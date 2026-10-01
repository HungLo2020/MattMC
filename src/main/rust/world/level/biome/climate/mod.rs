//! Ordered climate R-tree lookup. Java constructs the tree; native memory holds
//! its immutable preorder snapshot. Leaf IDs are node indices, never registry IDs.
mod ffi;
mod search;
#[cfg(target_arch = "x86_64")]
mod simd;
#[cfg(test)]
mod tests;

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub(super) struct Node {
    min: [i64; 8],
    max: [i64; 8],
    next: i32,
    leaf: i32,
}
