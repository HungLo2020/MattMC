use super::*;

#[test]
fn all_widths_padding_aliases_and_owned_input() {
    for bits in 0..=16 {
        let n = if bits == 0 { 1 } else { 1 << bits.min(8) };
        let palette: Vec<i32> = (0..n).map(|i| (i % 37) as i32).collect();
        let mut words = if bits == 0 {
            vec![]
        } else {
            vec![0u64; ENTRIES.div_ceil(64 / bits)]
        };
        for i in 0..ENTRIES {
            if bits != 0 {
                words[i / (64 / bits)] |= ((i % n) as u64) << (i % (64 / bits) * bits);
            }
        }
        let capture = decode(&words, bits, &palette, 100).unwrap();
        words.fill(u64::MAX);
        for i in 0..ENTRIES {
            assert_eq!(capture[i], palette[i % n] as u16);
        }
    }
    let words = vec![u64::MAX; ENTRIES.div_ceil(64 / 15)];
    let global = decode(&words, 15, &[], 32768).unwrap();
    assert!(global.iter().all(|s| *s == 32767));
}

#[test]
fn bad_inputs_decline_without_owner() {
    assert!(decode(&[], 0, &[8], 8).is_none());
    assert!(decode(&[], 0, &[-1], 100).is_none());
    assert!(decode(&[], 0, &[], 100).is_none());
    assert!(decode(&[], 17, &[0], 100).is_none());
    assert!(decode(&[u64::MAX; 256], 4, &[1, 2], 100).is_none());
    assert!(decode(&[], 0, &[0], 65536).is_none());
    unsafe {
        assert!(
            ffi::mattmc_chunk_snapshot_create(std::ptr::null(), 0, 0, [1].as_ptr(), 1, 100)
                .is_null()
        );
        assert_eq!(
            ffi::mattmc_chunk_snapshot_padded(std::ptr::null(), std::ptr::null_mut(), 0, 0),
            -1
        );
    }
}

#[test]
fn neighbourhood_faces_edges_corners_and_missing_sections() {
    let sections: Vec<_> = (0..27).map(|s| Box::new([s as u16 + 1; ENTRIES])).collect();
    let refs = std::array::from_fn(|i| Some(sections[i].as_ref()));
    let mut output = vec![0; PAD * PAD * PAD];
    padded(refs, 0, &mut output);
    for y in 0..PAD {
        for z in 0..PAD {
            for x in 0..PAD {
                let s = ((y + 15) / 16 * 3 + (z + 15) / 16) * 3 + (x + 15) / 16;
                assert_eq!(output[(y * PAD + z) * PAD + x], s as i32 + 1);
            }
        }
    }
    padded([None; 27], 71, &mut output);
    assert!(output.iter().all(|s| *s == 71));
}

#[test]
fn exported_owner_is_readable_and_consumed_in_bulk() {
    unsafe {
        let words = [0u64; 256];
        let owner =
            ffi::mattmc_chunk_snapshot_create(words.as_ptr(), 256, 4, [31].as_ptr(), 1, 100);
        assert!(!owner.is_null());
        let mut sections = [std::ptr::null(); 27];
        sections[13] = owner;
        let mut output = vec![0; PAD * PAD * PAD];
        assert_eq!(
            ffi::mattmc_chunk_snapshot_padded(
                sections.as_ptr(),
                output.as_mut_ptr(),
                output.len() as i32,
                0
            ),
            0
        );
        assert_eq!(output[(9 * PAD + 9) * PAD + 9], 31);
        assert_eq!(output[0], 0);
        ffi::mattmc_chunk_snapshot_release(owner);
    }
}
