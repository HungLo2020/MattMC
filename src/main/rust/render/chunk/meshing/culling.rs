use super::*;

#[inline(always)]
pub(super) fn native_section_culls_quad(
    record: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    quad_record: StaticModelQuadRecord,
    states: &[Option<NativeMeshingState>],
) -> bool {
    if quad_record.cull_face < 0 || quad_record.cull_face >= 6 {
        return false;
    }

    let neighbor_id = record.neighbor_state_ids[quad_record.cull_face as usize];
    let Some(neighbor) = state_by_id(states, neighbor_id) else {
        return false;
    };

    state_culls_model_face(quad_record.cull_face, state, neighbor)
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
    if !(0..6).contains(&face) || neighbor.skip_group == 0 || neighbor.skip_group != state.skip_group {
        return false;
    }

    ((state.skip_mask >> face) & 1) != 0
        && ((neighbor.skip_mask >> opposite_face(face)) & 1) != 0
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
}
