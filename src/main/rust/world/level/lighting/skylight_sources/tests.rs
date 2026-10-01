use super::{ffi::mattmc_skylight_sources_section, scan};
#[test]
fn packed_scan_matches_voxel_edges() {
    for bits in [0usize, 4, 5, 6, 7, 8, 9, 12, 15, 16, 32] {
        let per = if bits == 0 { 1 } else { 64 / bits };
        let mut words = vec![0u64; if bits == 0 { 0 } else { (4096 + per - 1) / per }];
        let mut ids = vec![0usize; 4096];
        let mut seed = 991u64;
        for i in 0..4096 {
            seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
            ids[i] = if bits == 0 {
                0
            } else {
                (seed >> 32) as usize % 8
            };
            if bits != 0 {
                words[i / per] |= (ids[i] as u64) << ((i % per) * bits);
            }
        }
        let flags = [0, 1, 2, 65536, 65538, 4, 131072, 131076];
        let edges = [0, 0, 0, 0, 0, 1, 0, 1, 0];
        for hb in [5usize, 6, 9, 13, 32] {
            let stride = (256 + 64 / hb - 1) / (64 / hb);
            let h = [bits as i32, hb as i32, stride as i32, 0, -1, 16, 3, 8];
            let mut pending = [1u8; 256];
            let mut above = [0u32; 256];
            let mut maps = vec![u64::MAX; stride];
            assert_eq!(
                scan::section(
                    &words,
                    &flags,
                    &edges,
                    &h,
                    &mut pending,
                    &mut above,
                    &mut maps
                ),
                Ok(0)
            );
            for c in 0..256 {
                let mut upper = 0;
                let mut expected = 0;
                for y in (0..16).rev() {
                    let s = flags[ids[c + y * 256]];
                    let up = ((s >> 1) & 32767) as usize;
                    if s & 1 != 0 || edges[upper * 3 + up] != 0 {
                        expected = y + 2;
                        break;
                    }
                    upper = (s >> 16) as usize;
                }
                assert_eq!(
                    (maps[c / (64 / hb)] >> ((c % (64 / hb)) * hb)) & ((1u64 << hb) - 1),
                    expected as u64
                );
            }
            let used = 256 % (64 / hb);
            if used != 0 {
                assert_eq!(maps[stride - 1] >> (used * hb), u64::MAX >> (used * hb));
            }
        }
    }
}
#[test]
fn ffi_checks_bounds_and_cross_section_faces() {
    let mut frame = vec![0u64; (1312 + 256 * 8) / 8];
    let mut words = [0u64; 256];
    words[..16].fill(0x1111111111111111);
    let flags = [0u32, 65536];
    let edges = [0u8, 0, 1, 0];
    unsafe {
        let h = std::slice::from_raw_parts_mut(frame.as_mut_ptr() as *mut i32, 8);
        h.copy_from_slice(&[4, 6, 26, 16, -1, 32, 2, 2]);
        let p = std::slice::from_raw_parts_mut((frame.as_mut_ptr() as *mut u8).add(32), 256);
        p.fill(1);
        let call = |f: &mut Vec<u64>, wlen| {
            mattmc_skylight_sources_section(
                words.as_ptr(),
                wlen,
                flags.as_ptr(),
                2,
                edges.as_ptr(),
                4,
                f.as_mut_ptr() as *mut u8,
                (f.len() * 8) as i32,
            )
        };
        assert_eq!(call(&mut frame, 1), -1);
        assert_eq!(call(&mut frame, 256), 256); // upper section carries DOWN face 1
        let h = std::slice::from_raw_parts_mut(frame.as_mut_ptr() as *mut i32, 8);
        h[3] = 0;
        assert_eq!(call(&mut frame, 256), 0); // cross-section edge 1->0 blocks
        assert_eq!(frame[1312 / 8] & 63, 17); // upper block Y=16
        let h = std::slice::from_raw_parts_mut(frame.as_mut_ptr() as *mut i32, 8);
        h[0] = 33;
        assert_eq!(call(&mut frame, 0), -1);
        assert_eq!(
            mattmc_skylight_sources_section(
                std::ptr::null(),
                0,
                flags.as_ptr(),
                2,
                edges.as_ptr(),
                4,
                frame.as_mut_ptr() as *mut u8,
                (frame.len() * 8) as i32
            ),
            -1
        );
    }
}

#[test]
fn empty_clear_preserves_every_padding_bit() {
    for bits in 1usize..=32 {
        let per = 64 / bits;
        let stride = (256 + per - 1) / per;
        let mut words = vec![0xafa5bffe9917abcd; stride];
        let mut expected = words.clone();
        for c in 0..256 {
            let shift = c % per * bits;
            expected[c / per] &= !(((1u64 << bits) - 1) << shift);
        }
        let call = unsafe {
            super::ffi::mattmc_skylight_sources_empty(
                words.as_mut_ptr(),
                stride as i32,
                bits as i32,
            )
        };
        assert_eq!(call, 0);
        assert_eq!(words, expected);
        let before = words.clone();
        assert_eq!(
            unsafe {
                super::ffi::mattmc_skylight_sources_empty(
                    words.as_mut_ptr(),
                    stride as i32 + 1,
                    bits as i32,
                )
            },
            -1
        );
        assert_eq!(before, words);
    }
    assert_eq!(
        unsafe { super::ffi::mattmc_skylight_sources_empty(std::ptr::null_mut(), 1, 9) },
        -1
    );
}
