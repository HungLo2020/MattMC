use super::super::id_map;
use super::*;
use std::io::Read;
struct Reader(Vec<u8>, usize);
impl Reader {
    fn bytes(&mut self, n: usize) -> &[u8] {
        let s = self.1;
        self.1 += n;
        &self.0[s..self.1]
    }
    fn int(&mut self) -> i32 {
        i32::from_be_bytes(self.bytes(4).try_into().unwrap())
    }
    fn long(&mut self) -> u64 {
        u64::from_be_bytes(self.bytes(8).try_into().unwrap())
    }
    fn text(&mut self) -> String {
        let n = u16::from_be_bytes(self.bytes(2).try_into().unwrap()) as usize;
        String::from_utf8(self.bytes(n).to_vec()).unwrap()
    }
}
#[test]
fn untouched_frozen_cached_neighborhoods_match_color_and_mutation_prefix() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!("../../../../../../../test/resources/world/chunk/frozen-dh-tint.bin.gz")
            .as_slice(),
    )
    .read_to_end(&mut bytes)
    .unwrap();
    let mut r = Reader(bytes, 0);
    assert_eq!(r.int(), 0x44485431);
    assert_eq!(r.int(), 7);
    for _ in 0..7 {
        assert!(!r.text().is_empty());
        assert_eq!(r.text().len(), 64);
    }
    assert!(r.text().contains("Actual Frozen"));
    assert_eq!(r.int(), 288);
    for case in 0..288 {
        let pos = r.long();
        let (x, y, z, min_y, radius, mask) = (r.int(), r.int(), r.int(), r.int(), r.int(), r.int());
        let mut colors = Colors::default();
        let mut biomes = Vec::new();
        let map = unsafe { id_map::mattmc_dh_id_map_create() };
        let state: Vec<u16> = "AIR".encode_utf16().collect();
        for i in 0..4 {
            let identity: Box<[u16]> = r.text().encode_utf16().collect();
            let color = r.int();
            unsafe {
                assert_eq!(
                    id_map::mattmc_dh_id_map_add(
                        map,
                        identity.as_ptr(),
                        identity.len() as i32,
                        state.as_ptr(),
                        3,
                        0,
                        0
                    ),
                    i
                );
            }
            let hash = identity
                .iter()
                .fold(0i32, |h, &c| h.wrapping_mul(31).wrapping_add(c as i32))
                .wrapping_add(31);
            let identity = Arc::new(Identity {
                hash,
                units: Mutex::new(identity),
            });
            unsafe {
                assert_eq!(
                    id_map::mattmc_dh_id_map_bind_tint(map, i, &Biome(identity.clone())),
                    1
                );
            }
            if mask & (1 << i) != 0 {
                colors.put(&identity, color);
            }
            biomes.push(identity);
        }
        let cache = Cache(Arc::new(Mutex::new(colors)));
        let center = Biome(biomes[0].clone());
        let mut columns = vec![Vec::<u64>::new(); 4096];
        for _ in 0..r.int() {
            let i = r.int() as usize;
            columns[i] = (0..r.int()).map(|_| r.long()).collect();
        }
        let expected = [r.int(), r.int(), r.int()];
        let mut plan = [0; 49];
        let mut out = [123; 3];
        unsafe {
            let count = mattmc_dh_tint_plan(pos, x, z, radius, plan.as_mut_ptr());
            assert!(count >= 0);
            if count == 0 {
                let color = mattmc_dh_tint_get(&cache, &center);
                let actual = if color >> 32 == 0 {
                    i32::MIN
                } else {
                    color as i32
                };
                assert_eq!([actual, x, z], expected, "case{case}");
            } else {
                let mut raw = [0usize; 98];
                for i in 0..count as usize {
                    if plan[i] >= 0 {
                        let c = &columns[plan[i] as usize];
                        raw[2 * i] = c.as_ptr() as usize;
                        raw[2 * i + 1] = c.len();
                    }
                }
                assert_eq!(
                    mattmc_dh_tint_blend(
                        &cache,
                        &center,
                        map,
                        pos,
                        x,
                        y,
                        z,
                        min_y,
                        radius,
                        raw.as_ptr(),
                        out.as_mut_ptr()
                    ),
                    1,
                    "case{case}"
                );
                assert_eq!(out, expected, "case{case}");
                out.fill(999);
                assert_eq!(
                    mattmc_dh_tint_blend_bounded(
                        &cache,
                        &center,
                        map,
                        pos,
                        x,
                        y,
                        z,
                        min_y,
                        radius,
                        raw.as_ptr(),
                        out.as_mut_ptr()
                    ),
                    1,
                    "bounded case{case}"
                );
                assert_eq!(out, expected, "bounded case{case}");
            }
            id_map::mattmc_dh_id_map_release(map);
        }
    }
    assert_eq!(r.1, r.0.len());
}
#[test]
fn cache_publication_shares_utf16_identity_and_distinguishes_missing_from_every_color() {
    let name: Vec<u16> = "test:tint_control_\u{1f30e}".encode_utf16().collect();
    let b: Vec<u16> = "test:biome".encode_utf16().collect();
    unsafe {
        let a = mattmc_dh_tint_create(name.as_ptr(), name.len() as i32);
        let c = mattmc_dh_tint_create(name.as_ptr(), name.len() as i32);
        let biome = mattmc_dh_tint_biome_v2(b.as_ptr(), b.len() as i32, 123);
        assert_eq!(mattmc_dh_tint_get(a, biome), 0);
        for value in [i32::MIN, -1, 0, i32::MAX] {
            assert_eq!(mattmc_dh_tint_put(a, biome, value), 1);
            assert_eq!(
                mattmc_dh_tint_get(c, biome),
                (1u64 << 32) | value as u32 as u64
            );
        }
        mattmc_dh_tint_release(a);
        mattmc_dh_tint_release(c);
        mattmc_dh_tint_biome_release(biome);
    }
}
#[test]
fn malformed_spans_and_unsupported_bounds_leave_result_untouched() {
    let cache = Cache(Arc::new(Mutex::default()));
    let biome = Biome(Arc::new(Identity {
        hash: 1,
        units: Mutex::new(vec![1].into_boxed_slice()),
    }));
    let mut plan = [17; 49];
    let mut out = [31; 3];
    unsafe {
        assert_eq!(
            mattmc_dh_tint_plan(6, i32::MAX, 0, 3, plan.as_mut_ptr()),
            -1
        );
        assert!(plan.iter().all(|&v| v == 17));
        let map = id_map::mattmc_dh_id_map_create();
        let mut raw = [0usize; 98];
        raw[1] = 4097;
        assert_eq!(
            mattmc_dh_tint_blend(
                &cache,
                &biome,
                map,
                6,
                30,
                0,
                30,
                0,
                3,
                raw.as_ptr(),
                out.as_mut_ptr()
            ),
            0
        );
        assert_eq!(out, [31; 3]);
        id_map::mattmc_dh_id_map_release(map);
    }
}

#[test]
fn actual_frozen_mutable_key_histories_keep_original_hash_and_first_binding() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!(
            "../../../../../../../test/resources/world/chunk/frozen-dh-tint-keys.bin.gz"
        )
        .as_slice(),
    )
    .read_to_end(&mut bytes)
    .unwrap();
    let mut r = Reader(bytes, 0);
    assert_eq!(r.int(), 0x44544b31);
    assert_eq!(r.int(), 32);
    for case in 0..32 {
        let a: Vec<u16> = r.text().encode_utf16().collect();
        let c: Vec<u16> = r.text().encode_utf16().collect();
        let hash = r.int();
        let cache = Cache(Arc::new(Mutex::default()));
        unsafe {
            let biomes = [
                mattmc_dh_tint_biome_v2(a.as_ptr(), a.len() as i32, hash),
                mattmc_dh_tint_biome_v2(a.as_ptr(), a.len() as i32, hash),
                mattmc_dh_tint_biome_v2(c.as_ptr(), c.len() as i32, hash),
            ];
            for step in 0..r.int() {
                let action = r.int();
                let b = biomes[r.int() as usize];
                let color = r.int();
                let expected = r.int();
                if action == 1 {
                    assert_eq!(mattmc_dh_tint_put(&cache, b, color), 1);
                }
                if action == 2 {
                    assert_eq!(
                        mattmc_dh_tint_biome_update(b, c.as_ptr(), c.len() as i32),
                        1
                    );
                }
                let got = mattmc_dh_tint_get(&cache, b);
                let got = if got >> 32 == 0 { i32::MIN } else { got as i32 };
                assert_eq!(got, expected, "case{case} step{step}");
            }
            for b in biomes {
                mattmc_dh_tint_biome_release(b);
            }
        }
    }
    assert_eq!(r.1, r.0.len());
}

#[test]
fn primary_color_cells_survive_growth_and_publish_membership_generation() {
    let cache = Cache(Arc::new(Mutex::default()));
    let name = [b'a' as u16];
    let changed = [b'b' as u16];
    unsafe {
        let biome = mattmc_dh_tint_biome_v2(name.as_ptr(), 1, 1);
        assert!(mattmc_dh_tint_bind(&cache, biome).is_null());
        let generation = (*mattmc_dh_tint_epoch()).load(Ordering::Acquire);
        assert_eq!(mattmc_dh_tint_put(&cache, biome, 123), 1);
        assert!((*mattmc_dh_tint_epoch()).load(Ordering::Acquire) > generation);
        let cell = mattmc_dh_tint_bind(&cache, biome);
        assert!(!cell.is_null());
        assert_eq!((*cell).load(Ordering::Acquire), (1u64 << 32) | 123);
        for i in 2..1026 {
            let units = [i as u16];
            let other = mattmc_dh_tint_biome_v2(units.as_ptr(), 1, i);
            assert_eq!(mattmc_dh_tint_put(&cache, other, i), 1);
            mattmc_dh_tint_biome_release(other);
        }
        assert_eq!(mattmc_dh_tint_bind(&cache, biome), cell);
        assert_eq!(mattmc_dh_tint_put(&cache, biome, 456), 1);
        assert_eq!((*cell).load(Ordering::Acquire), (1u64 << 32) | 456);
        let generation = (*mattmc_dh_tint_epoch()).load(Ordering::Acquire);
        assert_eq!(mattmc_dh_tint_biome_update(biome, changed.as_ptr(), 1), 1);
        assert!((*mattmc_dh_tint_epoch()).load(Ordering::Acquire) > generation);
        let old = mattmc_dh_tint_biome_v2(name.as_ptr(), 1, 1);
        assert!(mattmc_dh_tint_bind(&cache, old).is_null());
        assert_eq!(mattmc_dh_tint_bind(&cache, biome), cell);
        mattmc_dh_tint_biome_release(old);
        mattmc_dh_tint_biome_release(biome);
    }
}

#[test]
fn bounded_sampling_declines_long_work_and_contended_keys_without_writes() {
    let cache = Cache(Arc::new(Mutex::default()));
    let name = [b'a' as u16];
    let state = [b'A' as u16];
    unsafe {
        let biome = mattmc_dh_tint_biome_v2(name.as_ptr(), 1, 1);
        let map = id_map::mattmc_dh_id_map_create();
        assert_eq!(
            id_map::mattmc_dh_id_map_add(map, name.as_ptr(), 1, state.as_ptr(), 1, 0, 0),
            0
        );
        assert_eq!(id_map::mattmc_dh_id_map_bind_tint(map, 0, biome), 1);
        assert_eq!(mattmc_dh_tint_put(&cache, biome, 0xff123456u32 as i32), 1);
        let points = vec![1u64 << 32; 100];
        let mut raw = [0usize; 98];
        for i in 0..49 {
            raw[2 * i] = points.as_ptr() as usize;
            raw[2 * i + 1] = points.len();
        }
        let mut out = [313; 3];
        assert_eq!(
            mattmc_dh_tint_blend_bounded(
                &cache,
                biome,
                map,
                6,
                30,
                0,
                30,
                0,
                3,
                raw.as_ptr(),
                out.as_mut_ptr()
            ),
            0
        );
        assert_eq!(out, [313; 3]);
        assert_eq!(
            mattmc_dh_tint_blend(
                &cache,
                biome,
                map,
                6,
                30,
                0,
                30,
                0,
                3,
                raw.as_ptr(),
                out.as_mut_ptr()
            ),
            1
        );
        assert_eq!(out, [0xff123456u32 as i32, 33, 33]);
        for i in 0..49 {
            raw[2 * i + 1] = 1;
        }
        let guard = cache.0.lock().unwrap();
        out.fill(313);
        assert_eq!(
            mattmc_dh_tint_blend_bounded(
                &cache,
                biome,
                map,
                6,
                30,
                0,
                30,
                0,
                3,
                raw.as_ptr(),
                out.as_mut_ptr()
            ),
            0
        );
        assert_eq!(out, [313; 3]);
        drop(guard);
        let other = mattmc_dh_tint_biome_v2(name.as_ptr(), 1, 1);
        let guard = (&(*other).0).units.lock().unwrap();
        assert!(cache.0.lock().unwrap().bounded_get(&(*other).0).is_err());
        drop(guard);
        mattmc_dh_tint_biome_release(other);
        mattmc_dh_tint_biome_release(biome);
        id_map::mattmc_dh_id_map_release(map);
    }
}
