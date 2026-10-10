use super::*;
struct Reader<'a> {
    bytes: &'a [u8],
    at: usize,
}
impl Reader<'_> {
    fn u32(&mut self) -> u32 {
        let v = u32::from_be_bytes(self.bytes[self.at..self.at + 4].try_into().unwrap());
        self.at += 4;
        v
    }
    fn u64(&mut self) -> u64 {
        let v = u64::from_be_bytes(self.bytes[self.at..self.at + 8].try_into().unwrap());
        self.at += 8;
        v
    }
    fn boolean(&mut self) -> bool {
        let v = self.bytes[self.at];
        self.at += 1;
        assert!(v <= 1);
        v != 0
    }
    fn snapshot(&mut self) -> Expected {
        let bits = self.u32() as usize;
        let requested = self.u32() as usize;
        let global = self.boolean();
        let count = self.u32() as usize;
        let palette = (0..count).map(|_| self.u32()).collect();
        let count = self.u32() as usize;
        let words = (0..count).map(|_| self.u64()).collect();
        let values = std::array::from_fn(|_| self.u32());
        Expected {
            bits,
            requested,
            global,
            palette,
            words,
            values,
        }
    }
}
struct Expected {
    bits: usize,
    requested: usize,
    global: bool,
    palette: Vec<u32>,
    words: Vec<u64>,
    values: [u32; 64],
}
fn compare(owner: &Owner, e: &Expected) {
    let (g, values, _) = owner.snapshot();
    assert_eq!(
        (g.bits, g.requested, g.global),
        (e.bits, e.requested, e.global)
    );
    let count = g.count.load(Ordering::Acquire) as usize;
    let palette: Vec<_> = g
        .palette
        .iter()
        .take(count)
        .map(|v| v.load(Ordering::Acquire))
        .collect();
    let words: Vec<_> = g.words.iter().map(|v| v.load(Ordering::Acquire)).collect();
    assert_eq!(palette, e.palette);
    assert_eq!(words, e.words);
    assert_eq!(values, e.values);
}
#[test]
fn actual_frozen_imports_growth_padding_copies_and_single_aliases_match() {
    use std::io::Read;
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(include_bytes!("frozen-live-biomes.bin.gz").as_slice())
        .read_to_end(&mut bytes)
        .unwrap();
    let mut r = Reader {
        bytes: &bytes,
        at: 0,
    };
    assert_eq!(r.u32(), 0x42494d31);
    let scenarios = r.u32();
    assert_eq!(scenarios, 16);
    let mut operations = 0;
    for _ in 0..scenarios {
        let limit = r.u32();
        let global_bits = r.u32() as usize;
        let initial = r.snapshot();
        let mut owners: [Option<Owner>; 4] = std::array::from_fn(|_| None);
        owners[0] = Some(
            Owner::load(
                initial.bits,
                initial.requested,
                &initial.palette,
                &initial.words,
                limit,
                global_bits,
            )
            .unwrap(),
        );
        compare(owners[0].as_ref().unwrap(), &initial);
        let steps = r.u32();
        for _ in 0..steps {
            let kind = r.u32();
            let slot = r.u32() as usize;
            let index = r.u32() as usize;
            let value = r.u32();
            let old = r.u32();
            match kind {
                0 => {
                    let result = owners[slot].as_ref().unwrap().write(index, value).unwrap();
                    assert_eq!(result.0, old);
                }
                1 => {
                    let copy = owners[index].as_ref().unwrap().copy();
                    owners[slot] = Some(copy);
                }
                2 => assert!(owners[slot].as_ref().unwrap().read_single(value)),
                _ => panic!("Unknown oracle operation"),
            }
            let mask = r.u32();
            for (i, owner) in owners.iter().enumerate() {
                assert_eq!(owner.is_some(), mask & (1 << i) != 0);
                if let Some(owner) = owner {
                    compare(owner, &r.snapshot());
                }
            }
            operations += 1;
        }
    }
    assert_eq!(operations, 1600);
    assert_eq!(r.at, r.bytes.len());
}
#[test]
fn retained_native_world_cache_stamps_notice_shared_single_mutation_and_growth() {
    let owner = Owner::load(0, 0, &[1], &[], 512, 9).unwrap();
    let copy = owner.copy();
    let (old, values, revision) = owner.snapshot();
    assert_eq!(values, [1; 64]);
    assert!(copy.read_single(3));
    assert!(!owner.matches(&old, revision));
    let (shared, values, revision) = owner.snapshot();
    assert_eq!(values, [3; 64]);
    assert!(Arc::ptr_eq(&old, &shared));
    assert!(owner.matches(&shared, revision));
    assert_eq!(owner.write(5, 7).unwrap(), (3, true));
    assert!(!owner.matches(&shared, revision));
    assert_eq!(copy.lease().value(5), 3);
    assert_eq!(owner.lease().value(5), 7);
    let nonzero = owner.copy();
    assert!(!Arc::ptr_eq(&nonzero.lease(), &owner.lease()));
    assert_eq!(nonzero.write(5, 8).unwrap().0, 7);
    assert_eq!(owner.lease().value(5), 7);
}
#[test]
fn concurrent_single_alias_capture_never_mixes_values_or_accepts_stale_revision() {
    let owner = Arc::new(Owner::load(0, 0, &[1], &[], 512, 9).unwrap());
    let copy = owner.copy();
    let worker = std::thread::spawn(move || {
        for i in 0..100_000 {
            assert!(copy.read_single(i % 511));
        }
    });
    for _ in 0..10_000 {
        let (_, values, revision) = owner.snapshot();
        assert!(values.iter().all(|&v| v == values[0]));
        assert_eq!(revision & 1, 0);
    }
    worker.join().unwrap();
}
#[test]
fn invalid_input_and_mutations_do_not_partially_publish_a_generation() {
    assert!(Owner::load(3, 3, &[1, 1], &[0; 4], 512, 9).is_none());
    assert!(Owner::load(1, 1, &[1], &[u64::MAX], 512, 9).is_none());
    assert!(Owner::load(0, 0, &[1], &[], 8, 3).is_none());
    let owner = Owner::load(0, 0, &[1], &[], 512, 9).unwrap();
    let before = owner.snapshot();
    assert!(owner.write(64, 2).is_none());
    assert!(owner.write(0, 512).is_none());
    assert!(!owner.read_single(512));
    assert!(owner.matches(&before.0, before.2));
    assert_eq!(owner.snapshot().1, before.1);
}

#[test]
fn compatibility_transfer_invalidates_retained_world_sources_without_freeing_views() {
    let owner = Owner::load(0, 0, &[1], &[], 512, 9).unwrap();
    let (view, values, revision) = owner.snapshot();
    owner.invalidate();
    assert!(!owner.is_valid());
    assert!(!owner.matches(&view, revision));
    assert!(owner.write(0, 2).is_none());
    assert!(!owner.read_single(2));
    assert_eq!(view.snapshot().0, values);
    assert_eq!(view.value(0), 1);
}

#[test]
fn production_cpu_boundary_releases_owner_without_invalidating_retained_generation() {
    unsafe {
        let palette = [3u32];
        let words = [0u64; 16];
        let owner =
            ffi::mattmc_live_biome_create(words.as_ptr(), 0, 0, 0, palette.as_ptr(), 1, 512, 9);
        assert!(!owner.is_null());
        let mut header = [0u64; 6];
        assert_eq!(ffi::mattmc_live_biome_view(owner, header.as_mut_ptr()), 0);
        let view = header[5] as *const Generation;
        let copy = ffi::mattmc_live_biome_copy(owner);
        assert!(!copy.is_null());
        assert_eq!(ffi::mattmc_live_biome_read_single(copy, 7), 0);
        assert_eq!((*view).value(0), 7);
        assert_eq!(ffi::mattmc_live_biome_write(owner, 0, 9), (1i64 << 32) | 7);
        assert_eq!((*view).value(0), 7);
        let mut output_words = [0u64; 16];
        let mut output_palette = [0u32; 8];
        let mut output_header = [0u32; 4];
        assert_eq!(
            ffi::mattmc_live_biome_export(
                owner,
                output_words.as_mut_ptr(),
                output_palette.as_mut_ptr(),
                output_header.as_mut_ptr()
            ),
            1
        );
        assert_eq!(output_palette[..2], [7, 9]);
        ffi::mattmc_live_biome_invalidate(owner);
        assert_eq!(ffi::mattmc_live_biome_write(owner, 1, 4), -1);
        ffi::mattmc_live_biome_release(owner);
        ffi::mattmc_live_biome_release(copy);
        assert_eq!((*view).value(0), 7);
        ffi::mattmc_live_biome_view_release(view);
    }
}
