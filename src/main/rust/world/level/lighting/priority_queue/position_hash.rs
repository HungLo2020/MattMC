/// Fastutil's primitive long mixing family. Full-key equality decides membership;
/// hashing never determines FIFO order. No strings or general objects enter this set.
pub(super) fn mix(value: u64) -> u64 {
    let mixed = value.wrapping_mul(0x9e3779b97f4a7c15);
    let mixed = mixed ^ (mixed >> 32);
    mixed ^ (mixed >> 16)
}
