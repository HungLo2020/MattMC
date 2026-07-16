//! Native section scan facade.
//!
//! The compact production path is intentionally separate from the legacy record
//! scanner. Production consumes a `NativeSectionRecordSource` once, dispatches
//! model, fluid, and light-block producers for all render passes, and keeps pass
//! routing local to Rust-owned builders. The legacy module remains for benchmark,
//! replay, and ABI coverage of the historical 316-byte records.

use super::*;

mod compact;
mod dispatch;
mod legacy;
mod routing;

use dispatch::*;
use routing::*;

pub(super) use compact::section_builders_append_compact_native_section_all_passes_encoded;
pub(super) use legacy::section_builder_append_native_section_records_encoded;

#[cfg(test)]
mod tests;
