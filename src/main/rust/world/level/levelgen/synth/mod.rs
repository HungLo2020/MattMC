//! Noise synthesis families. Seed construction remains in Java.
#[cfg(target_arch = "x86_64")]
mod avx2_helpers;
mod blended;
mod common;
mod dispatch;
#[cfg(target_arch = "x86_64")]
mod dispatch_avx2;
mod ffi;
mod improved;
mod normal;
mod perlin;
mod perlin_simplex;
mod simplex;
mod state;
mod validation;
pub(crate) use dispatch::{noise_batch, noise_eval, sample_simplex2};
pub(crate) use state::State;
pub(crate) use validation::noise_validate;
#[cfg(test)]
mod tests;
