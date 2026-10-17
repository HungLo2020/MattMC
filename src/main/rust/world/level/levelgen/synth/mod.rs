//! Noise synthesis and native leaf seed construction.
#[cfg(target_arch = "x86_64")]
mod avx2_helpers;
mod blended;
mod blended_construction;
mod common;
mod construction;
mod dispatch;
#[cfg(target_arch = "x86_64")]
mod dispatch_avx2;
mod ffi;
mod improved;
mod normal;
mod normal_construction;
mod perlin;
mod perlin_construction;
mod perlin_simplex;
mod simplex;
mod simplex_construction;
mod state;
mod validation;
pub(crate) use dispatch::{noise_batch, noise_eval, sample_simplex2};
pub(crate) use state::State;
pub(crate) use validation::noise_validate;
#[cfg(test)]
mod tests;
