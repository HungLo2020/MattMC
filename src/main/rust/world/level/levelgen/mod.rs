//! Seed-compatible world generation. Native ABI adapters are kept separate
//! from noise kernels, density evaluation and resumable surface rules.
pub(crate) mod density;
pub(crate) mod math;
pub(crate) mod surface;
pub(crate) mod synth;
