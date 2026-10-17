//! Ordered climate R-tree lookup. Java constructs the tree; native memory holds
//! its immutable preorder snapshot. Leaf IDs are node indices, never registry IDs.
mod ffi;
mod search;
#[cfg(target_arch = "x86_64")]
mod simd;
#[cfg(test)]
mod tests;

/// Ordered lookups from `previous` (a leaf ID or -1), as `mattmc_climate_batch`
/// runs them; stops after a point with no leaf. Returns the points completed.
pub(crate) fn search_batch(nodes: &[Node], points: &[[i64; 8]], out: &mut [i32], previous: i32) -> usize {
    search::batch(nodes, points, out, previous)
}

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub(crate) struct Node {
    min: [i64; 8],
    max: [i64; 8],
    next: i32,
    leaf: i32,
}

impl Node {
    pub(crate) fn is_leaf(&self) -> bool {
        self.leaf == 1
    }
}
