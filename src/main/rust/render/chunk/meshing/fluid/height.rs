//! Fluid top alignment and corner-height rules.
//!
//! Heights follow the vanilla-style weighted corner sampling rules: matching
//! fluid above forces a full corner, high samples receive extra weight, solid
//! blocking samples become negative, and nonblocking empty samples contribute zero.

use super::*;

pub(in crate::render::chunk::meshing) fn fluid_top_aligned(heights: [f32; 4]) -> bool {
    fluid_aligned_equals(heights[3], heights[0])
        && fluid_aligned_equals(heights[0], heights[2])
        && fluid_aligned_equals(heights[2], heights[1])
        && fluid_aligned_equals(heights[1], heights[3])
}

pub(in crate::render::chunk::meshing) fn fluid_aligned_equals(a: f32, b: f32) -> bool {
    (a - b).abs() <= FLUID_ALIGNED_EQUALS_EPSILON
}

pub(in crate::render::chunk::meshing) fn fluid_top_crease_ne_sw(
    heights: [f32; 4],
    aligned: bool,
) -> bool {
    aligned
        || heights[3] > heights[0] && heights[3] > heights[2]
        || heights[3] < heights[0] && heights[3] < heights[2]
        || heights[1] > heights[0] && heights[1] > heights[2]
        || heights[1] < heights[0] && heights[1] < heights[2]
}

pub(in crate::render::chunk::meshing) fn fluid_height(
    block: &NativeSectionBlockRecord,
    center: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    dx: i32,
    dy: i32,
    dz: i32,
    fallback_neighbor_index: usize,
) -> f32 {
    let state_id = neighborhood_state_id(block, dx, dy, dz)
        .unwrap_or_else(|| block.neighbor_state_ids[fallback_neighbor_index]);
    let Some(sample) = state_by_id(states, state_id) else {
        return 0.0;
    };
    if sample.fluid_type == center.fluid_type && sample.fluid_type != 0 {
        if neighborhood_state_id(block, dx, dy + 1, dz)
            .and_then(|above_id| state_by_id(states, above_id))
            .map(|above| above.fluid_type == center.fluid_type)
            .unwrap_or(false)
        {
            1.0
        } else {
            sample.fluid_own_height
        }
    } else if (sample.flags & STATE_FLAG_BLOCKS_MOTION) == 0 {
        0.0
    } else {
        -1.0
    }
}

pub(in crate::render::chunk::meshing) fn fluid_corner_height(
    block: &NativeSectionBlockRecord,
    center_state: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    center: f32,
    hx: f32,
    hz: f32,
    dx: i32,
    dy: i32,
    dz: i32,
) -> f32 {
    if hx >= 1.0 || hz >= 1.0 {
        return 1.0;
    }
    let mut total = 0.0;
    let mut samples = 0.0;
    if hx > 0.0 || hz > 0.0 {
        let diagonal = fluid_height(block, center_state, states, dx, dy, dz, 0);
        if diagonal >= 1.0 {
            return 1.0;
        }
        modify_fluid_height(&mut total, &mut samples, diagonal);
    }
    modify_fluid_height(&mut total, &mut samples, center);
    modify_fluid_height(&mut total, &mut samples, hx);
    modify_fluid_height(&mut total, &mut samples, hz);
    if samples == 0.0 {
        0.0
    } else {
        total / samples
    }
}

pub(in crate::render::chunk::meshing) fn modify_fluid_height(
    total: &mut f32,
    samples: &mut f32,
    height: f32,
) {
    if height >= 0.8 {
        *total += height * 10.0;
        *samples += 10.0;
    } else if height >= 0.0 {
        *total += height;
        *samples += 1.0;
    }
}
