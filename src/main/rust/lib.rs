// MattMC Rust Native Library
// Main entry point that re-exports all FFI modules
//
// This library provides high-performance implementations of Java utility classes
// using Rust's zero-cost abstractions and memory safety guarantees.
//
// The library is structured as follows:
// - One Rust module per Java class
// - Mirror Java package structure where practical
// - All modules compile into a single native library (.so/.dylib/.dll)
// - Single JAR distribution with embedded native library
//
// Author: James Seibel and contributors
// Version: 0.2.0 (Modular structure)

// Utility module containing mathematical and bitwise operations
pub mod util;

// SQL modules
pub mod sql;

// Re-export all utility functions so they remain accessible via the C ABI
// This maintains backward compatibility while organizing code into modules
pub use util::math_util::*;
pub use util::bit_shift_util::*;
pub use util::color_util::*;
pub use util::bool_util::*;
pub use sql::dto::util::varint_util::*;
