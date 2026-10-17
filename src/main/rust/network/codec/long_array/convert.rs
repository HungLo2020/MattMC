#[cfg(target_arch = "x86_64")]
static AVX2: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

pub(super) fn initialize() -> i32 {
    #[cfg(target_arch = "x86_64")]
    {
        let enabled = std::is_x86_feature_detected!("avx2");
        AVX2.store(enabled, std::sync::atomic::Ordering::Relaxed);
        enabled as i32
    }
    #[cfg(not(target_arch = "x86_64"))]
    { 0 }
}

pub(super) fn encode(values: &[u64], bytes: &mut [u8]) {
    #[cfg(target_arch = "x86_64")]
    if AVX2.load(std::sync::atomic::Ordering::Relaxed) {
        unsafe { super::simd::swap(values.as_ptr().cast(), bytes.as_mut_ptr(), values.len()); }
        return;
    }
    encode_scalar(values, bytes);
}

pub(super) fn decode(bytes: &[u8], values: &mut [u64]) {
    #[cfg(target_arch = "x86_64")]
    if AVX2.load(std::sync::atomic::Ordering::Relaxed) {
        unsafe { super::simd::swap(bytes.as_ptr(), values.as_mut_ptr().cast(), values.len()); }
        return;
    }
    decode_scalar(bytes, values);
}

pub(super) fn encode_scalar(values: &[u64], bytes: &mut [u8]) {
    for (value, dest) in values.iter().zip(bytes.chunks_exact_mut(8)) {
        dest.copy_from_slice(&value.to_be_bytes());
    }
}

pub(super) fn decode_scalar(bytes: &[u8], values: &mut [u64]) {
    for (source, value) in bytes.chunks_exact(8).zip(values) {
        *value = u64::from_be_bytes(source.try_into().unwrap());
    }
}
