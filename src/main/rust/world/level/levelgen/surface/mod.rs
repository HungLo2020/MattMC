//! Resumable surface evaluation. Java owns ordered writes and external requests.
pub(crate) mod chunk;
mod pass;
mod evaluator;
mod ffi;
mod frame;
mod program;
pub(crate) mod scalar;
#[cfg(test)]
mod tests;
