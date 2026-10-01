use super::{ffi::*, remap::*};

fn pack(values: &[u32], bits: usize) -> Vec<u64> {
    if bits == 0 {
        return vec![];
    }
    values
        .chunks(64 / bits)
        .map(|chunk| {
            chunk
                .iter()
                .enumerate()
                .fold(0, |word, (i, &id)| word | (id as u64) << (i * bits))
        })
        .collect()
}

#[test]
fn all_widths_first_use_order_aliases_and_padding() {
    let mut rng = 713u64;
    for bits in 0..=8 {
        for target in 1..=16 {
            for pattern in 0..4 {
                let count = 1 << bits;
                let values: Vec<u32> = (0..SIZE)
                    .map(|i| {
                        rng ^= rng << 13;
                        rng ^= rng >> 7;
                        rng ^= rng << 17;
                        (match pattern {
                            0 => 0,
                            1 => count - 1,
                            2 => i,
                            _ => rng as usize,
                        } % count) as u32
                    })
                    .collect();
                let mut words = pack(&values, bits);
                if bits != 0 {
                    let per = 64 / bits;
                    for (i, word) in words.iter_mut().enumerate() {
                        let fields = per.min(SIZE - i * per);
                        if fields * bits != 64 {
                            *word |= u64::MAX << (fields * bits);
                        }
                    }
                }
                let original = words.clone();
                let mut order = vec![u32::MAX; 256];
                let n = used(&words, bits, count, &mut order).unwrap();
                let mut expected = Vec::new();
                for &id in &values {
                    if !expected.contains(&id) {
                        expected.push(id);
                    }
                }
                assert_eq!(&order[..n], &expected);
                let map: Vec<u32> = (0..256)
                    .map(|i| ((i * 137 + 1) & ((1 << target) - 1)) as u32)
                    .collect();
                let expected = pack(
                    &values
                        .iter()
                        .map(|&id| map[id as usize])
                        .collect::<Vec<_>>(),
                    target,
                );
                let mut out = vec![u64::MAX; expected.len()];
                remap(&words, bits, &map, target, &mut out);
                assert_eq!(out, expected);
                assert_eq!(words, original);
            }
        }
    }
}

#[test]
fn ffi_rejects_metadata_and_bad_ids_without_publishing() {
    let words = pack(&vec![1; SIZE], 4);
    let mut order = [91u32; 256];
    unsafe {
        assert_eq!(
            mattmc_palette_resize_used(
                words.as_ptr(),
                words.len() as i32,
                4,
                1,
                order.as_mut_ptr(),
                256
            ),
            -2
        );
        assert_eq!(
            mattmc_palette_resize_used(words.as_ptr(), -1, 4, 16, order.as_mut_ptr(), 256),
            -1
        );
        assert_eq!(
            mattmc_palette_resize_used(
                words.as_ptr(),
                words.len() as i32,
                -1,
                16,
                order.as_mut_ptr(),
                256
            ),
            -1
        );
        assert_eq!(
            mattmc_palette_resize_used(std::ptr::null(), 0, 0, 1, order.as_mut_ptr(), 256),
            -1
        );
    }
    assert_eq!(order, [91; 256]);
    let mut out = vec![91u64; (SIZE + 11) / 12];
    let mut map = [0u32; 256];
    map[1] = 32;
    unsafe {
        assert_eq!(
            mattmc_palette_resize_remap(
                words.as_ptr(),
                words.len() as i32,
                4,
                map.as_ptr(),
                256,
                5,
                out.as_mut_ptr(),
                out.len() as i32
            ),
            -2
        );
        assert_eq!(
            mattmc_palette_resize_remap(
                words.as_ptr(),
                words.len() as i32,
                4,
                map.as_ptr(),
                255,
                5,
                out.as_mut_ptr(),
                out.len() as i32
            ),
            -1
        );
    }
    assert!(out.iter().all(|&x| x == 91));
}

#[test]
fn ffi_matches_safe_kernel_and_reuses_dirty_scratch() {
    for bits in 0..=8 {
        let values: Vec<u32> = (0..SIZE)
            .map(|i| ((i * 1977) & ((1 << bits) - 1)) as u32)
            .collect();
        let words = pack(&values, bits);
        let mut order = [u32::MAX; 256];
        let map: Vec<u32> = (0..256).map(|i| (i * 37) as u32).collect();
        let mut out = vec![u64::MAX; SIZE / 4];
        unsafe {
            assert_eq!(
                mattmc_palette_resize_used(
                    words.as_ptr(),
                    words.len() as i32,
                    bits as i32,
                    (1 << bits) as i32,
                    order.as_mut_ptr(),
                    256
                ),
                1 << bits
            );
            assert_eq!(
                mattmc_palette_resize_remap(
                    words.as_ptr(),
                    words.len() as i32,
                    bits as i32,
                    map.as_ptr(),
                    256,
                    15,
                    out.as_mut_ptr(),
                    out.len() as i32
                ),
                0
            );
        }
        assert_eq!(
            out,
            pack(
                &values.iter().map(|&i| map[i as usize]).collect::<Vec<_>>(),
                15
            )
        );
    }
}
