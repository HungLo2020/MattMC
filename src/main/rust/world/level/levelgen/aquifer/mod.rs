//! Aquifer geometry and ordered material decisions. Java owns lazy fluid/noise
//! sources; a resumable decision requests only the sources its branch visits.
mod cell;
mod decision;
mod ffi;
pub(crate) mod locations;
mod fluid;
mod nearest;
#[cfg(test)]
mod tests;
