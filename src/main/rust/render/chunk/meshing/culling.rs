use super::*;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::OnceLock;

const FACE_CULL_TRACE_LIMIT: usize = 4096;
static FACE_CULL_TRACE_SECTION: OnceLock<Option<i64>> = OnceLock::new();
static FACE_CULL_TRACE_EVENTS: AtomicUsize = AtomicUsize::new(0);

#[inline(always)]
pub(super) fn native_section_culls_quad(
    record: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    quad_record: StaticModelQuadRecord,
    states: &[Option<NativeMeshingState>],
) -> bool {
    if quad_record.cull_face < 0 || quad_record.cull_face >= 6 {
        trace_cull_decision(record, state, quad_record, None, false, "uncullable-face");
        return false;
    }

    if ((semantic_cull_mask(record.flags) >> quad_record.cull_face) & 1) != 0 {
        trace_cull_decision(record, state, quad_record, None, true, "semantic-shape-cull-mask");
        return true;
    }

    let neighbor_id = record.neighbor_state_ids[quad_record.cull_face as usize];
    let Some(neighbor) = state_by_id(states, neighbor_id) else {
        trace_cull_decision(record, state, quad_record, None, false, "neighbor-state-missing");
        return false;
    };

    let full_or_solid = (neighbor.flags & (STATE_FLAG_FULL_OCCLUSION | STATE_FLAG_SOLID_RENDER)) != 0;
    let same_skip_group = same_skip_group_culls_face(quad_record.cull_face, state, neighbor);
    let culled = full_or_solid || same_skip_group;
    let reason = if full_or_solid {
        "neighbor-full-or-solid"
    } else if same_skip_group {
        "same-skip-group"
    } else {
        "neighbor-does-not-cull"
    };
    trace_cull_decision(record, state, quad_record, Some(neighbor), culled, reason);
    culled
}

#[inline(always)]
fn semantic_cull_mask(flags: i32) -> i32 {
    (flags >> 8) & 0b11_1111
}

fn trace_cull_decision(
    record: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    quad: StaticModelQuadRecord,
    neighbor: Option<NativeMeshingState>,
    culled: bool,
    reason: &str,
) {
    let Some(target_section) = *FACE_CULL_TRACE_SECTION.get_or_init(|| {
        std::env::var("MATTMC_NATIVE_CULL_TRACE_SECTION")
            .ok()
            .and_then(|value| value.parse::<i64>().ok())
    }) else {
        return;
    };
    let section_key = section_key(record.absolute_x, record.absolute_y, record.absolute_z);
    if section_key != target_section || FACE_CULL_TRACE_EVENTS.fetch_add(1, Ordering::Relaxed) >= FACE_CULL_TRACE_LIMIT {
        return;
    }
    let (neighbor_state_id, neighbor_block_id, neighbor_flags, neighbor_skip_group, neighbor_skip_mask) =
        neighbor.map_or((-1, -1, 0, 0, 0), |value| {
            (
                record.neighbor_state_ids[quad.cull_face.clamp(0, 5) as usize],
                value.block_id,
                value.flags,
                value.skip_group,
                value.skip_mask,
            )
        });
    eprintln!(
        "MATTMC_NATIVE_CULL_TRACE sectionKey={section_key} pos={},{},{} localY={} stateId={} blockId={} face={} normalFace={} passId={} decision={} reason={} neighborStateId={} neighborBlockId={} stateFlags={} neighborFlags={} skipGroup={} skipMask={} neighborSkipGroup={} neighborSkipMask={}",
        record.absolute_x,
        record.absolute_y,
        record.absolute_z,
        record.absolute_y.rem_euclid(16),
        record.state_id,
        record.block_id,
        quad.cull_face,
        quad.normal_face,
        quad.pass_id,
        if culled { "cull" } else { "emit" },
        reason,
        neighbor_state_id,
        neighbor_block_id,
        state.flags,
        neighbor_flags,
        state.skip_group,
        state.skip_mask,
        neighbor_skip_group,
        neighbor_skip_mask,
    );
}

#[inline(always)]
fn section_key(x: i32, y: i32, z: i32) -> i64 {
    let section_x = i64::from(x).div_euclid(16);
    let section_y = i64::from(y).div_euclid(16);
    let section_z = i64::from(z).div_euclid(16);
    ((section_x & 0x3f_ffff) << 42) | ((section_z & 0x3f_ffff) << 20) | (section_y & 0x0f_ffff)
}

#[inline(always)]
pub(super) fn state_culls_model_face(
    face: i32,
    state: NativeMeshingState,
    neighbor: NativeMeshingState,
) -> bool {
    (neighbor.flags & (STATE_FLAG_FULL_OCCLUSION | STATE_FLAG_SOLID_RENDER)) != 0
        || same_skip_group_culls_face(face, state, neighbor)
}

#[inline(always)]
fn same_skip_group_culls_face(
    face: i32,
    state: NativeMeshingState,
    neighbor: NativeMeshingState,
) -> bool {
    if !(0..6).contains(&face)
        || neighbor.skip_group == 0
        || neighbor.skip_group != state.skip_group
    {
        return false;
    }

    ((state.skip_mask >> face) & 1) != 0 && ((neighbor.skip_mask >> opposite_face(face)) & 1) != 0
}

#[inline(always)]
fn opposite_face(face: i32) -> i32 {
    match face {
        0 => 1,
        1 => 0,
        2 => 3,
        3 => 2,
        4 => 5,
        5 => 4,
        _ => face,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn same_skip_group_culls_non_full_transparent_neighbor() {
        let all_faces_mask = 0b11_1111;
        let state = NativeMeshingState {
            skip_group: 7,
            skip_mask: all_faces_mask,
            ..NativeMeshingState::default()
        };
        let neighbor = NativeMeshingState {
            skip_group: 7,
            skip_mask: all_faces_mask,
            flags: 0,
            ..NativeMeshingState::default()
        };

        assert!(state_culls_model_face(0, state, neighbor));
        assert!(state_culls_model_face(5, state, neighbor));
    }

    #[test]
    fn different_or_missing_skip_group_does_not_cull_non_full_neighbor() {
        let state = NativeMeshingState {
            skip_group: 7,
            ..NativeMeshingState::default()
        };
        let different_neighbor = NativeMeshingState {
            skip_group: 8,
            flags: 0,
            ..NativeMeshingState::default()
        };
        let ungrouped_neighbor = NativeMeshingState {
            skip_group: 0,
            flags: 0,
            ..NativeMeshingState::default()
        };

        assert!(!state_culls_model_face(0, state, different_neighbor));
        assert!(!state_culls_model_face(0, state, ungrouped_neighbor));
    }

    #[test]
    fn same_skip_group_requires_both_direction_masks() {
        let state = NativeMeshingState {
            skip_group: 7,
            skip_mask: 1 << 5,
            ..NativeMeshingState::default()
        };
        let neighbor_with_opposite = NativeMeshingState {
            skip_group: 7,
            skip_mask: 1 << 4,
            ..NativeMeshingState::default()
        };
        let neighbor_without_opposite = NativeMeshingState {
            skip_group: 7,
            skip_mask: 1 << 5,
            ..NativeMeshingState::default()
        };

        assert!(state_culls_model_face(5, state, neighbor_with_opposite));
        assert!(!state_culls_model_face(5, state, neighbor_without_opposite));
    }

    #[test]
    fn full_occlusion_still_culls_without_skip_group() {
        let state = NativeMeshingState::default();
        let neighbor = NativeMeshingState {
            flags: STATE_FLAG_FULL_OCCLUSION,
            ..NativeMeshingState::default()
        };

        assert!(state_culls_model_face(0, state, neighbor));
    }

    #[test]
    fn semantic_snapshot_cull_mask_overrides_approximate_native_state_flags() {
        let record = NativeSectionBlockRecord {
            flags: 1 << 8,
            ..NativeSectionBlockRecord::default()
        };
        let quad = StaticModelQuadRecord {
            cull_face: 0,
            ..StaticModelQuadRecord::default()
        };

        assert!(native_section_culls_quad(
            &record,
            NativeMeshingState::default(),
            quad,
            &[]
        ));
    }
}
