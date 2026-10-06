//! Density arithmetic, immutable programs and cell evaluation.
pub(crate) mod beardifier;
pub(crate) mod cell;
pub(crate) mod end_islands;
pub(crate) mod evaluator;
mod ffi;
pub(crate) mod math;
mod operations;
mod program;
pub(crate) mod spline;
#[cfg(test)]
mod tests;
mod unary;
pub(crate) mod validation;
