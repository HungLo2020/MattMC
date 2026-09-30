//! Packed immutable density program ABI.
#[repr(C)]
pub(crate) struct Header {
    pub(crate) count: u32,
    pub(crate) inputs: u32,
    pub(crate) cells: u32,
    pub(crate) reserved: u32,
}
#[repr(C)]
pub(crate) struct Node {
    pub(crate) op: u32,
    pub(crate) a: u32,
    pub(crate) b: u32,
    pub(crate) c: u32,
    pub(crate) offset: u64,
    pub(crate) bytes: u64,
    pub(crate) p: f64,
    pub(crate) q: f64,
    pub(crate) r: f64,
    pub(crate) reserved: f64,
}
