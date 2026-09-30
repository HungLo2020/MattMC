//! Java-owned immutable permutation and octave ABI.
/// ABI v1: native endian, 8-byte alignment, followed by n1+n2+n3 Octaves.
#[repr(C)]
pub struct State {
    pub(crate) kind: u32,
    pub(crate) n1: u32,
    pub(crate) n2: u32,
    pub(crate) n3: u32,
    pub(crate) params: [f64; 8],
}
#[repr(C)]
#[derive(Clone)]
pub struct Octave {
    pub(crate) x: f64,
    pub(crate) y: f64,
    pub(crate) z: f64,
    pub(crate) amplitude: f64,
    pub(crate) present: u64,
    pub(crate) p: [u8; 256],
    pub(crate) p32: [i32; 256],
}

impl State {
    // SAFETY: the bridge allocates/validates the complete immutable trailing array.
    pub(crate) unsafe fn octaves(&self) -> &[Octave] {
        unsafe {
            std::slice::from_raw_parts(
                (self as *const Self).add(1).cast(),
                (self.n1 + self.n2 + self.n3) as usize,
            )
        }
    }
}

impl Octave {
    #[inline(always)]
    pub(crate) fn p(&self, i: i32) -> i32 {
        self.p[(i & 255) as usize] as i32
    }
    #[inline(always)]
    pub(crate) fn p12(&self, i: i32) -> i32 {
        // Packed by Java alongside the permutation; avoids division in Simplex.
        (self.p32[(i & 255) as usize] >> 8) & 15
    }
}
