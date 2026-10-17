//! Immutable density spline programs. Every intermediate is Java-width float.
mod coordinates;
pub(crate) mod evaluate;
mod ffi;
pub(crate) mod program;
#[cfg(test)]
mod tests;
