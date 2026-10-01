use super::{ffi::mattmc_palette_histogram, scan::*};

#[test]
fn counts_padding_order_and_workspace_reuse() {
    let mut work = vec![0; WORK];
    let mut out = vec![0; 4096];
    for bits in 0..=16 {
        let limit = 1usize << bits;
        let per = if bits == 0 { 1 } else { 64 / bits };
        let mut words = vec![u64::MAX; if bits == 0 { 0 } else { (4096 + per - 1) / per }];
        let mut expected = std::collections::BTreeMap::<u32, u32>::new();
        for i in 0..4096 {
            let id = ((i * 1977) % limit.min(4096)) as u32;
            *expected.entry(id).or_default() += 1;
            if bits != 0 {
                let shift = i % per * bits;
                let field = ((1u64 << bits) - 1) << shift;
                words[i / per] = (words[i / per] & !field) | ((id as u64) << shift);
            }
        }
        let size = scan(&words, bits, &mut work, &mut out);
        let actual = out[..size]
            .iter()
            .map(|v| (*v as u32, (*v >> 32) as u32))
            .collect::<std::collections::BTreeMap<_, _>>();
        assert_eq!(actual, expected);
        assert_eq!(size, expected.len());
        assert!(work[..DENSE].iter().all(|v| *v == 0));
        if actual.contains_key(&0) {
            assert_eq!(out[0] as u32, 0);
        }
        let first = out[..size].to_vec();
        assert_eq!(scan(&words, bits, &mut work, &mut out), size);
        assert_eq!(first, out[..size]);
    }
}

#[test]
fn ffi_checks_metadata_before_access() {
    let words = vec![0u64; 256];
    let mut work = vec![0u32; WORK];
    let mut out = vec![0u64; 4096];
    unsafe {
        assert_eq!(
            mattmc_palette_histogram(
                words.as_ptr(),
                256,
                4,
                work.as_mut_ptr(),
                WORK as i32,
                out.as_mut_ptr(),
                4096
            ),
            1
        );
        assert_eq!(out[0], 4096u64 << 32);
        for (len, bits, work_len, out_len) in [
            (255, 4, WORK as i32, 4096),
            (256, 17, WORK as i32, 4096),
            (256, 4, 0, 4096),
            (256, 4, WORK as i32, 0),
        ] {
            assert_eq!(
                mattmc_palette_histogram(
                    words.as_ptr(),
                    len,
                    bits,
                    work.as_mut_ptr(),
                    work_len,
                    out.as_mut_ptr(),
                    out_len
                ),
                -1
            );
        }
        assert_eq!(
            mattmc_palette_histogram(
                std::ptr::null(),
                256,
                4,
                work.as_mut_ptr(),
                WORK as i32,
                out.as_mut_ptr(),
                4096
            ),
            -1
        );
    }
    assert!(work[..DENSE].iter().all(|v| *v == 0));
}
