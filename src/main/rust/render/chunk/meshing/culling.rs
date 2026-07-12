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

    (neighbor.flags & (STATE_FLAG_FULL_OCCLUSION | STATE_FLAG_SOLID_RENDER)) != 0
        || (neighbor.skip_group != 0
            && neighbor.skip_group == state.skip_group
            && (neighbor.flags & STATE_FLAG_FULL_OCCLUSION) != 0)
}
