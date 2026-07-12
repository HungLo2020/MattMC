use super::*;

pub(super) fn native_section_culls_quad(
    record: &NativeSectionBlockRecord,
    quad_record: StaticModelQuadRecord,
    states: &HashMap<i32, NativeMeshingState>,
) -> bool {
    if quad_record.cull_face < 0 || quad_record.cull_face >= 6 {
        return false;
    }

    let neighbor_id = record.neighbor_state_ids[quad_record.cull_face as usize];
    let Some(neighbor) = states.get(&neighbor_id) else {
        return false;
    };

    (neighbor.flags & (STATE_FLAG_FULL_OCCLUSION | STATE_FLAG_SOLID_RENDER)) != 0
        || (neighbor.skip_group != 0
            && neighbor.skip_group
                == states
                    .get(&record.state_id)
                    .map(|state| state.skip_group)
                    .unwrap_or(0)
            && (neighbor.flags & STATE_FLAG_FULL_OCCLUSION) != 0)
}
