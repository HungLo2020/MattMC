//! C ABI entry points for Java chunk meshing calls.
//!
//! Exported functions in this directory are grouped by native surface area and
//! kept as thin ABI wrappers: validate raw pointers and primitive arguments,
//! construct Rust-owned views or handles, then delegate meshing behavior to safe
//! internal modules. Symbol names and Java bindings must remain stable.
//!
//! # Safety
//!
//! Every unsafe export in this module is called from Java's foreign-function
//! bindings. Java must pass handles returned by the matching native constructor,
//! keep pointed-to memory alive and immutable for the duration of the call unless
//! the parameter is documented as output memory, and provide counts/strides that
//! describe the accessible allocation exactly. Rust validates null pointers,
//! negative counts, ABI format fields, and known handle-zero cases before
//! constructing slices, but it cannot prove that a non-null foreign pointer or
//! nonzero handle still points to live memory.

use super::*;

mod builder;
mod cache;
mod format;
mod section;
mod staging;
mod translucent;
mod updates;

pub use builder::*;
pub use cache::*;
pub use format::*;
pub use section::*;
pub use staging::*;
pub use translucent::*;
pub use updates::*;

#[cfg(test)]
mod tests;
