use super::{decode::decode, ffi::mattmc_palette_unpack_decode};

fn input(bits: usize, ids: &[u32]) -> Vec<u64> {
    let per = 64 / bits;
    let mut words = vec![u64::MAX; (4096 + per - 1) / per];
    let mask = (1u64 << bits) - 1;
    for (i, &id) in ids.iter().enumerate() {
        let shift = i % per * bits;
        words[i / per] = (words[i / per] & !(mask << shift)) | ((id as u64) << shift);
    }
    words
}

#[test]
fn all_widths_counts_patterns_first_use_and_encode() {
    let mut lookup = vec![0; 65536];
    let mut out = vec![0; 8192];
    let mut cases = 0;
    for bits in 9..=16 {
        for count in [(1usize << (bits - 1)) + 1, 1usize << bits] {
            for pattern in 0..4 {
                let ids: Vec<u32> = (0..4096)
                    .map(|i| match pattern {
                        0 => (i * 1977 + 71) % count,
                        1 => i % count,
                        2 => (i / 127) % count,
                        _ => count - 1,
                    } as u32)
                    .collect();
                let words = input(bits, &ids);
                let n = decode(&words, bits, count, &mut lookup, &mut out).unwrap();
                let mut expected = Vec::new();
                for (i, &id) in ids.iter().enumerate() {
                    let ordinal = match expected.iter().position(|&v| v == id) {
                        Some(n) => n,
                        None => {
                            expected.push(id);
                            expected.len() - 1
                        }
                    };
                    assert_eq!(out[4096 + i], ordinal as u32);
                }
                assert_eq!(&out[..n], expected.as_slice());
                assert!(lookup.iter().all(|&v| v == 0));
                let ffi_n = unsafe {
                    mattmc_palette_unpack_decode(
                        words.as_ptr(),
                        words.len() as i32,
                        bits as i32,
                        count as i32,
                        lookup.as_mut_ptr(),
                        65536,
                        out.as_mut_ptr(),
                        8192,
                    )
                };
                assert_eq!(ffi_n, n as i32);
                for target in 1..=16 {
                    let map: Vec<u32> = expected.iter().map(|id| id.wrapping_mul(37)).collect();
                    let per = 64 / target;
                    let mut packed = vec![u64::MAX; (4096 + per - 1) / per];
                    super::super::pack::encode(&out[4096..], target, &mut packed, Some(&map))
                        .unwrap();
                    let mask = (1u64 << target) - 1;
                    for i in 0..4096 {
                        assert_eq!(
                            (packed[i / per] >> (i % per * target)) & mask,
                            (ids[i].wrapping_mul(37) as u64) & mask
                        );
                    }
                    for (w, &value) in packed.iter().enumerate() {
                        let used_bits = per.min(4096 - w * per) * target;
                        if used_bits < 64 {
                            assert_eq!(value >> used_bits, 0);
                        }
                    }
                }
                cases += 1;
            }
        }
    }
    assert_eq!(cases, 64);
}

#[test]
fn invalid_ids_reset_workspace_and_metadata_rejects_without_writes() {
    let mut ids = vec![0; 4096];
    for i in 0..4096 {
        ids[i] = (i % 257) as u32;
    }
    let mut lookup = vec![0; 65536];
    let mut out = vec![77; 8192];
    for at in [0, 1, 127, 4095] {
        let saved = ids[at];
        ids[at] = 511;
        let words = input(9, &ids);
        assert_eq!(
            unsafe {
                mattmc_palette_unpack_decode(
                    words.as_ptr(),
                    words.len() as i32,
                    9,
                    257,
                    lookup.as_mut_ptr(),
                    65536,
                    out.as_mut_ptr(),
                    8192,
                )
            },
            -2
        );
        assert!(lookup.iter().all(|&v| v == 0));
        ids[at] = saved;
        let words = input(9, &ids);
        assert!(decode(&words, 9, 257, &mut lookup, &mut out).is_ok());
    }
    let words = input(9, &ids);
    let saved = out.clone();
    for (bits, count, len, out_len) in [
        (8, 257, words.len() as i32, 8192),
        (9, 513, words.len() as i32, 8192),
        (9, 257, 0, 8192),
        (9, 257, words.len() as i32, 8191),
    ] {
        assert_eq!(
            unsafe {
                mattmc_palette_unpack_decode(
                    words.as_ptr(),
                    len,
                    bits,
                    count,
                    lookup.as_mut_ptr(),
                    65536,
                    out.as_mut_ptr(),
                    out_len,
                )
            },
            -1
        );
        assert_eq!(out, saved);
    }
    assert_eq!(
        unsafe {
            mattmc_palette_unpack_decode(
                std::ptr::null(),
                0,
                9,
                257,
                lookup.as_mut_ptr(),
                65536,
                out.as_mut_ptr(),
                8192,
            )
        },
        -1
    );
}
