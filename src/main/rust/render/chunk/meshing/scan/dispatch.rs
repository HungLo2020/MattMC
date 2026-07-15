//! Producer dispatch decisions for scanned section records.
//!
//! Dispatch derives producer eligibility from the registered native meshing state
//! plus per-record flags. Compact production and legacy scanning share this so
//! fluid suppression, model emission, and light-block routing stay consistent.

use super::*;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::chunk::meshing) struct ScanDispatch {
    pub(in crate::render::chunk::meshing) has_light_block: bool,
    pub(in crate::render::chunk::meshing) has_model: bool,
    pub(in crate::render::chunk::meshing) has_fluid: bool,
}

/// Classifies producer ownership before record materialization.
///
/// Compact and legacy scans both dispatch in light-block, model, then fluid
/// order. Snapshot fluid suppression only hides the fluid producer; model and
/// light-block producers must still run for the state.
#[inline(always)]
pub(in crate::render::chunk::meshing) fn scan_dispatch(
    flags: i32,
    record_flags: i32,
) -> ScanDispatch {
    ScanDispatch {
        has_light_block: (flags & STATE_FLAG_LIGHT_BLOCK) != 0,
        has_model: (flags & STATE_FLAG_MODEL) != 0,
        has_fluid: (flags & STATE_FLAG_FLUID) != 0
            && (record_flags & NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID) == 0,
    }
}
