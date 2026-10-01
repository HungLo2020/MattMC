use super::{ffi, scan};

#[test]
fn packed_scan_matches_independent_voxel_reference() {
    let mut seed = 713587123u64;
    for bits in [0usize, 1, 4, 5, 6, 7, 8, 9, 14, 16, 32] {
        for selected in 1..64 {
            let mut flags = vec![
                0u32;
                if bits == 0 {
                    1
                } else {
                    256.min(1 << bits.min(8))
                }
            ];
            for (i, value) in flags.iter_mut().enumerate() {
                *value = (i as u32 * 13) & 63;
            }
            let mut ids = vec![0usize; 4096];
            let per_word = if bits == 0 { 1 } else { 64 / bits };
            let mut words = vec![
                0u64;
                if bits == 0 {
                    0
                } else {
                    (4096 + per_word - 1) / per_word
                }
            ];
            for (i, id) in ids.iter_mut().enumerate() {
                seed ^= seed << 13;
                seed ^= seed >> 7;
                seed ^= seed << 17;
                *id = seed as usize % flags.len();
                if bits != 0 {
                    words[i / per_word] |= (*id as u64) << ((i % per_word) * bits);
                }
            }
            let height_bits = 9;
            let stride = 37;
            let header = [
                bits as i32,
                height_bits,
                flags.len() as i32,
                stride,
                -64,
                -64,
                320,
                selected,
            ];
            let mut pending = [selected as u8; 256];
            let mut maps = vec![0xdeadbeefcafef00d; 6 * stride as usize];
            let mut expected = maps.clone();
            let mut expected_pending = pending;
            for z in 0..16 {
                for x in 0..16 {
                    for map in 0..6 {
                        if selected & (1 << map) == 0 {
                            continue;
                        }
                        for y in (0..16).rev() {
                            if flags[ids[x + z * 16 + y * 256]] & (1 << map) == 0 {
                                continue;
                            }
                            let col = x + z * 16;
                            let word = map * stride as usize + col / 7;
                            let shift = col % 7 * 9;
                            expected[word] =
                                (expected[word] & !(511 << shift)) | (((y + 1) as u64) << shift);
                            expected_pending[col] &= !(1 << map);
                            break;
                        }
                    }
                }
            }
            scan::section(&words, bits, &flags, &header, &mut pending, &mut maps).unwrap();
            assert_eq!(maps, expected);
            assert_eq!(pending, expected_pending);
        }
    }
}

#[test]
fn validates_lengths_and_bad_ids_without_out_of_bounds_access() {
    let mut frame = [0u64; 1600];
    let h = [4i32, 9, 1, 37, -64, -64, 320, 63];
    let mut words = [0u64; 256];
    let flags = [63u32];
    unsafe {
        std::ptr::copy_nonoverlapping(h.as_ptr() as *const u8, frame.as_mut_ptr() as *mut u8, 32);
        std::ptr::write_bytes((frame.as_mut_ptr() as *mut u8).add(32), 63, 256);
        assert_eq!(
            ffi::mattmc_heightmap_section(
                words.as_ptr(),
                255,
                flags.as_ptr(),
                1,
                frame.as_mut_ptr() as *mut u8,
                12800
            ),
            -1
        );
        assert_eq!(
            ffi::mattmc_heightmap_section(
                words.as_ptr(),
                256,
                flags.as_ptr(),
                1,
                frame.as_mut_ptr() as *mut u8,
                288
            ),
            -1
        );
        words[240] = 15;
        assert_eq!(
            ffi::mattmc_heightmap_section(
                words.as_ptr(),
                256,
                flags.as_ptr(),
                1,
                frame.as_mut_ptr() as *mut u8,
                12800
            ),
            -2
        );
        assert_eq!(
            ffi::mattmc_heightmap_section(
                std::ptr::null(),
                0,
                flags.as_ptr(),
                1,
                frame.as_mut_ptr() as *mut u8,
                12800
            ),
            -1
        );
    }
}
