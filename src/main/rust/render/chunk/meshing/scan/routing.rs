//! Render-pass routing helpers shared by compact and legacy scans.
//!
//! Native section builders expose three runtime render passes: solid, cutout,
//! and translucent. Negative or out-of-range pass ids are treated as unroutable
//! here so callers can preserve their own fallback or legacy all-pass behavior.

pub(in crate::render::chunk::meshing) fn native_pass_index(pass_id: i32) -> Option<usize> {
    match pass_id {
        0..=2 => Some(pass_id as usize),
        _ => None,
    }
}
