//! Best-effort console diagnostics shared by native subsystems.
//!
//! A launcher can close either output pipe during shutdown. Diagnostic I/O
//! must not panic across a C ABI or participate in submission/resource state.

use std::fmt::Arguments;
use std::io::Write;

pub(crate) fn stdout(arguments: Arguments<'_>) {
    let _ = writeln!(std::io::stdout().lock(), "{arguments}");
}

pub(crate) fn stderr(arguments: Arguments<'_>) {
    let _ = writeln!(std::io::stderr().lock(), "{arguments}");
}

#[cfg(all(test, unix))]
#[path = "console/tests.rs"]
mod tests;
