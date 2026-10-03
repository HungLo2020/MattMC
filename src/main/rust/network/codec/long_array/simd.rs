//! On x86 little-endian hosts, the wire conversion reverses each eight-byte
//! lane. One byte shuffle converts four independent longs; scalar tail is exact.
use std::arch::x86_64::*;

/// # Safety
/// CPU supports AVX2. Both disjoint buffers cover count*8 bytes for this call.
#[target_feature(enable = "avx2")]
pub(super) unsafe fn swap(input: *const u8, output: *mut u8, count: usize) {
    let permutation = _mm256_setr_epi8(
        7,6,5,4,3,2,1,0,15,14,13,12,11,10,9,8,
        7,6,5,4,3,2,1,0,15,14,13,12,11,10,9,8);
    let mut index = 0;
    while index + 4 <= count {
        let data = _mm256_loadu_si256(input.add(index * 8).cast());
        _mm256_storeu_si256(output.add(index * 8).cast(), _mm256_shuffle_epi8(data, permutation));
        index += 4;
    }
    while index < count {
        let value = input.add(index * 8).cast::<u64>().read_unaligned();
        output.add(index * 8).cast::<u64>().write_unaligned(value.swap_bytes());
        index += 1;
    }
}
