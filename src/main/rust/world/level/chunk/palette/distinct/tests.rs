use super::ffi::{mattmc_palette_distinct, mattmc_palette_distinct_biomes};

fn compare(words: &[u64], bits: usize, size: usize, seen: &mut [u64]) {
    let mut expected = Vec::<u32>::new();
    for i in 0..size {
        let id = if bits == 0 { 0 } else {
            let per = 64 / bits;
            ((words[i / per] >> ((i % per) * bits)) & ((1u64 << bits) - 1)) as u32
        };
        if !expected.contains(&id) { expected.push(id); }
    }
    let mut output = vec![u32::MAX; size];
    let count = unsafe { mattmc_palette_distinct(words.as_ptr(), words.len() as i32,
        bits as i32, size as i32, seen.as_mut_ptr(), seen.len() as i32,
        output.as_mut_ptr(), output.len() as i32) };
    assert_eq!(count as usize, expected.len());
    assert_eq!(&output[..count as usize], &expected);
    assert!(output[count as usize..].iter().all(|&v| v == u32::MAX));
    assert!(seen.iter().all(|&v| v == 0));
    if size == 64 {
        output.fill(u32::MAX);
        let small = unsafe { mattmc_palette_distinct_biomes(words.as_ptr(), words.len() as i32,
            bits as i32, 64, seen.as_mut_ptr(), seen.len() as i32, output.as_mut_ptr(), 64) };
        assert_eq!(small, count); assert_eq!(&output[..count as usize], &expected);
        assert!(seen.iter().all(|&v| v == 0));
    }
}

#[test]
fn exhaustive_first_occurrence_order_and_workspace_reuse() {
    let mut seen = [0u64; 1024];
    for word in 0..65536 { compare(&[word], 2, 8, &mut seen); }
    eprintln!("DISTINCT_RUST_PARITY exhaustive=65536 ordered=true");
}

#[test]
fn every_width_boundary_padding_and_early_exit() {
    let mut seen = [0u64; 1024];
    let mut state = 1977u64;
    for bits in 0..=16 {
        for size in [1, 2, 3, 63, 64, 65, 4095, 4096] {
            let len = if bits == 0 { 0 } else { (size + 64 / bits - 1) / (64 / bits) };
            compare(&vec![0; len], bits, size, &mut seen);
            compare(&vec![u64::MAX; len], bits, size, &mut seen);
            for _ in 0..8 {
                let words: Vec<u64> = (0..len).map(|_| {
                    state ^= state << 13; state ^= state >> 7; state ^= state << 17; state
                }).collect();
                compare(&words, bits, size, &mut seen);
            }
        }
    }
}

#[test]
fn invalid_metadata_leaves_all_buffers_untouched() {
    let input = [17u64; 1024];
    let mut seen = [37u64; 1024];
    let mut out = [91u32; 4096];
    for (bits,size,len,work_len,out_len) in [(17,64,22,1024,64),(-1,64,2,1024,64),
        (4,0,0,1024,0),(4,4097,257,1024,4097),(4,64,3,1024,64),
        (4,64,4,1023,64),(4,64,4,1024,63),(0,64,1,1024,64)] {
        unsafe { assert_eq!(mattmc_palette_distinct(input.as_ptr(),len,bits,size,
            seen.as_mut_ptr(),work_len,out.as_mut_ptr(),out_len),-1); }
    }
    unsafe {
        assert_eq!(mattmc_palette_distinct_biomes(input.as_ptr(),4,4,4096,seen.as_mut_ptr(),1024,out.as_mut_ptr(),4096),-1);
        assert_eq!(mattmc_palette_distinct(std::ptr::null(),4,4,64,seen.as_mut_ptr(),1024,out.as_mut_ptr(),64),-1);
        assert_eq!(mattmc_palette_distinct(input.as_ptr(),4,4,64,std::ptr::null_mut(),1024,out.as_mut_ptr(),64),-1);
        assert_eq!(mattmc_palette_distinct(input.as_ptr(),4,4,64,seen.as_mut_ptr(),1024,std::ptr::null_mut(),64),-1);
        assert_eq!(mattmc_palette_distinct((input.as_ptr() as *const u8).add(1) as *const u64,4,4,64,seen.as_mut_ptr(),1024,out.as_mut_ptr(),64),-1);
    }
    assert_eq!(input,[17;1024]); assert_eq!(seen,[37;1024]); assert_eq!(out,[91;4096]);
}
