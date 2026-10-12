//! Backend-independent DH render-column reduction over semantic CPU values.
mod ffi;
mod reduction;
mod sort;
pub(crate) mod source;
#[cfg(test)]
mod tests;
