use super::{convert, ffi};

fn values(count: usize) -> Vec<u64> {
    let mut state = 1977_u64;
    (0..count).map(|i| {
        state = state.wrapping_mul(6364136223846793005).wrapping_add(1);
        match i % 8 { 0 => 0, 1 => u64::MAX, 2 => 1 << 63, 3 => i as u64, _ => state }
    }).collect()
}

#[test]
fn scalar_and_simd_match_literal_big_endian_for_all_tail_sizes() {
    for count in (1..=256).chain([511,512,513,8191,8192]) {
        let input = values(count);
        let expected: Vec<u8> = input.iter().flat_map(|v| v.to_be_bytes()).collect();
        for offset in 0..32 {
            let mut output = vec![123; offset + count*8 + 32];
            convert::encode_scalar(&input, &mut output[offset..offset+count*8]);
            assert_eq!(&output[offset..offset+count*8], expected);
            let mut decoded = vec![123;count];
            convert::decode_scalar(&output[offset..offset+count*8], &mut decoded);
            assert_eq!(decoded,input);
            convert::initialize();
            output.fill(123);
            convert::encode(&input,&mut output[offset..offset+count*8]);
            assert_eq!(&output[offset..offset+count*8],expected);
            assert!(output[..offset].iter().chain(&output[offset+count*8..]).all(|b| *b==123));
            convert::decode(&output[offset..offset+count*8],&mut decoded);
            assert_eq!(decoded,input);
        }
    }
}

#[test]
fn abi_roundtrips_aligned_longs_and_unaligned_bytes() {
    for count in [1,3,4,5,127,128,129,8191,8192] {
        let input=values(count);let mut bytes=vec![123;count*8+3];let mut output=vec![123;count+2];
        unsafe {
            assert_eq!(ffi::mattmc_long_array_encode(input.as_ptr(),count as i32,bytes.as_mut_ptr().add(1),count as i32*8),0);
            assert_eq!(ffi::mattmc_long_array_decode(bytes.as_ptr().add(1),count as i32*8,output.as_mut_ptr().add(1),count as i32),0);
        }
        assert_eq!(bytes[0],123);assert_eq!(bytes[count*8+1],123);
        assert_eq!(&output[1..count+1],input);assert_eq!(output[0],123);assert_eq!(output[count+1],123);
    }
}

#[test]
fn invalid_lengths_alignment_and_nulls_do_not_write() {
    let input=values(8193);let mut bytes=vec![123;8193*8];let mut output=vec![123;8193];
    for count in [i32::MIN,-1,0,8193,i32::MAX] { unsafe {
        assert_eq!(ffi::mattmc_long_array_encode(input.as_ptr(),count,bytes.as_mut_ptr(),0),-1);
        assert_eq!(ffi::mattmc_long_array_decode(bytes.as_ptr(),0,output.as_mut_ptr(),count),-1);
    }}
    unsafe {
        assert_eq!(ffi::mattmc_long_array_encode(input.as_ptr(),128,bytes.as_mut_ptr(),1023),-1);
        assert_eq!(ffi::mattmc_long_array_decode(bytes.as_ptr(),1025,output.as_mut_ptr(),128),-1);
        assert_eq!(ffi::mattmc_long_array_encode(std::ptr::null(),128,bytes.as_mut_ptr(),1024),-1);
        assert_eq!(ffi::mattmc_long_array_decode(std::ptr::null(),1024,output.as_mut_ptr(),128),-1);
        assert_eq!(ffi::mattmc_long_array_encode(input.as_ptr().cast::<u8>().add(1).cast(),128,bytes.as_mut_ptr(),1024),-1);
        assert_eq!(ffi::mattmc_long_array_decode(bytes.as_ptr(),1024,output.as_mut_ptr().cast::<u8>().add(1).cast(),128),-1);
    }
    assert!(bytes.iter().all(|v|*v==123));assert!(output.iter().all(|v|*v==123));
}
