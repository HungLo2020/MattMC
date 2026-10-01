use super::pack;
#[test]
fn compaction_and_encoding_match_first_identity_use() {
    for bits in [0usize, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 16, 32] {
        let p = if bits == 0 { 1 } else { 64 / bits };
        let mut words = vec![u64::MAX; if bits == 0 { 0 } else { (4096 + p - 1) / p }];
        let n = if bits == 0 { 1 } else { 17.min(1usize << bits) };
        let labels = (0..n).map(|i| (i / 2) as u32).collect::<Vec<_>>();
        let mut expected = vec![];
        let mut source = vec![];
        let mut seed = 1977u64;
        for i in 0..4096 {
            seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
            let id = if bits == 0 { 0 } else { seed as usize % n };
            source.push(id);
            let label = labels[id];
            let at = match expected.iter().position(|(k, _)| *k == label) {
                Some(at) => at,
                None => {
                    expected.push((label, id));
                    expected.len() - 1
                }
            };
            if bits != 0 {
                let shift = i % p * bits;
                let field = ((1u64 << bits) - 1) << shift;
                words[i / p] = (words[i / p] & !field) | ((id as u64) << shift);
            }
            source[i] = at;
        }
        let mut lookup = vec![0; n];
        let mut out = vec![0; 8192];
        let count = pack::compact(&words, bits, &labels, &mut lookup, &mut out).unwrap();
        assert_eq!(count, expected.len());
        assert_eq!(
            &out[..count],
            expected
                .iter()
                .map(|(_, id)| *id as u32)
                .collect::<Vec<_>>()
        );
        assert_eq!(
            &out[4096..],
            source.iter().map(|n| *n as u32).collect::<Vec<_>>()
        );
        for target in [1usize, 4, 5, 7, 9, 12, 32] {
            let per = 64 / target;
            let mut encoded = vec![u64::MAX; (4096 + per - 1) / per];
            pack::encode(&out[4096..], target, &mut encoded, None).unwrap();
            let mut java = vec![0u64; encoded.len()];
            for i in 0..4096 {
                java[i / per] |=
                    (source[i] as u64 & ((1u64 << target) - 1)) << ((i % per) * target);
            }
            assert_eq!(encoded, java);
        }
    }
}
#[test]
fn ffi_rejects_bad_metadata_ids_and_labels() {
    use super::ffi::*;
    let mut words = vec![0u64; 256];
    let labels = [0u32, 1];
    let mut lookup = [0i32; 2];
    let mut out = vec![0u32; 8192];
    unsafe {
        assert_eq!(
            mattmc_palette_compact(
                words.as_ptr(),
                256,
                4,
                labels.as_ptr(),
                2,
                lookup.as_mut_ptr(),
                2,
                out.as_mut_ptr(),
                8192
            ),
            1
        );
        words[0] = 15;
        assert_eq!(
            mattmc_palette_compact(
                words.as_ptr(),
                256,
                4,
                labels.as_ptr(),
                2,
                lookup.as_mut_ptr(),
                2,
                out.as_mut_ptr(),
                8192
            ),
            -2
        );
        assert_eq!(
            mattmc_palette_compact(
                words.as_ptr(),
                255,
                4,
                labels.as_ptr(),
                2,
                lookup.as_mut_ptr(),
                2,
                out.as_mut_ptr(),
                8192
            ),
            -1
        );
        assert_eq!(
            mattmc_palette_compact(
                std::ptr::null(),
                0,
                0,
                labels.as_ptr(),
                2,
                lookup.as_mut_ptr(),
                2,
                out.as_mut_ptr(),
                8192
            ),
            -1
        );
        assert_eq!(
            mattmc_palette_encode(
                out.as_ptr(),
                4095,
                4,
                out.as_ptr(),
                0,
                words.as_mut_ptr(),
                256
            ),
            -1
        );
        assert_eq!(
            mattmc_palette_encode(
                out.as_ptr(),
                4096,
                0,
                out.as_ptr(),
                0,
                words.as_mut_ptr(),
                256
            ),
            -1
        );
    }
    assert!(pack::compact(&[], 0, &[2], &mut lookup, &mut out).is_err());
}

#[test]
fn alias_remapping_matches_dense_encoding_and_rejects_bad_indices() {
    let ids = (0..4096).map(|i| (i % 3) as u32).collect::<Vec<_>>();
    let remap = [0, 1, 0];
    let mut actual = vec![u64::MAX; 256];
    pack::encode(&ids, 4, &mut actual, Some(&remap)).unwrap();
    let dense = ids.iter().map(|i| remap[*i as usize]).collect::<Vec<_>>();
    let mut expected = vec![0; 256];
    pack::encode(&dense, 4, &mut expected, None).unwrap();
    assert_eq!(actual, expected);
    assert!(pack::encode(&ids, 4, &mut actual, Some(&remap[..1])).is_err());
}
