//! Java-compatible block-model and fluid lighting.
//!
//! The facade chooses flat or smooth lighting for a source quad, while the
//! submodules keep snapshot sampling, AO face construction, interpolation, and
//! packed light-word parity helpers separate. Lighting coordinates are relative
//! to the center block in the 3x3x3 neighborhood carried by
//! `NativeSectionBlockRecord`; each axis may sample only `-1..=1`.

use super::*;

mod blend;
mod cache;
mod face;
mod flat;
mod parity;
mod sampling;
mod smooth;
mod types;

use flat::flat_lighting;
pub(super) use parity::{get_emissive_lightmap, max_brightness};
#[cfg(test)]
pub(super) use sampling::neighborhood_index;
pub(super) use sampling::{dir_step, neighborhood_state_id};
use smooth::smooth_lighting;
pub(super) use types::NativeQuadLight;

pub(super) fn native_quad_lighting(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    state: NativeMeshingState,
) -> NativeQuadLight {
    native_quad_lighting_for_vertex_format(block, quad, state, false)
}

/// Computes model lighting for a concrete terrain vertex contract.
///
/// Extended terrain formats carry AO independently in alpha. Their shader path
/// applies directional face shade after vertex decoding, so baking that factor
/// here would apply it twice. Legacy compact formats retain the historical
/// baked-color convention.
pub(super) fn native_quad_lighting_for_vertex_format(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    state: NativeMeshingState,
    separate_ao: bool,
) -> NativeQuadLight {
    let light_face = if (0..6).contains(&quad.light_face) {
        quad.light_face
    } else if (0..6).contains(&quad.cull_face) {
        quad.cull_face
    } else {
        1
    };
    let use_smooth = quad.has_ao != 0;
    let apply_directional_shade = quad.shade != 0 && !separate_ao;
    if use_smooth {
        smooth_lighting(block, quad, state, light_face, apply_directional_shade)
    } else {
        flat_lighting(block, quad, state, light_face, apply_directional_shade)
    }
}

#[cfg(test)]
mod tests;
