//! First-person (held item and hand) mesh instances and frame.

use super::*;

/// Decodes the append-only first-person instance stream. It intentionally
/// reuses the stable indexed-mesh record instead of creating a hand-specific
/// ABI schema, while rejecting camera-space strata before any frontend route
/// can consume the data.
pub(super) unsafe fn decode_world_first_person_mesh_instances(
    request: FfiSlice<FfiWorldMeshInstanceRecord>,
    first_person: &WorldFirstPersonFrame,
) -> GalResult<Vec<WorldMeshInstanceRequest>> {
    let raw_instances = read_slice(request, true, "world first-person mesh instances")?;
    if raw_instances.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world first-person mesh instance count {} exceeds max {}",
                raw_instances.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    if !raw_instances.is_empty() && !first_person.enabled {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world first-person mesh instances require an enabled first-person frame",
        ));
    }
    if usize::try_from(first_person.main_hand_instance_count)
        .ok()
        .is_none_or(|count| count > raw_instances.len())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world first-person main-hand instance count {} exceeds {} copied instances",
                first_person.main_hand_instance_count,
                raw_instances.len()
            ),
        ));
    }
    let mut instances = Vec::with_capacity(raw_instances.len());
    for instance in raw_instances {
        validate_item_size::<FfiWorldMeshInstanceRecord>(
            instance.byte_size,
            "world first-person mesh instance",
        )?;
        validate_mesh_instance_semantic_identity(instance, "world first-person mesh instance")?;
        if instance.terrain_placement_mode != 0 {
            return Err(GalError::invalid_argument(
                "first-person mesh instances cannot use terrain placement",
            ));
        }
        if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world first-person mesh instances cannot be outline-only",
            ));
        }
        if instance.block_entity_id != -1 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world first-person mesh instances cannot carry block-entity identity",
            ));
        }
        if instance.entity_id != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world first-person mesh instances cannot carry shader-pack entity identity",
            ));
        }
        if instance.stratum != WORLD_STRATUM_ENTITY_MESH {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world first-person mesh instances must use the generic entity-mesh semantic stratum",
            ));
        }
        if instance.depth_policy != WORLD_DEPTH_POLICY_DISABLED
            && instance.depth_policy != WORLD_DEPTH_POLICY_TEST_WRITE
            && instance.depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
            && !(instance.depth_policy == WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE
                && instance.stratum == WORLD_STRATUM_ENTITY_MESH)
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world first-person mesh depth policy {}",
                    instance.depth_policy
                ),
            ));
        }
        if instance.cull_policy != WORLD_CULL_NONE
            && instance.cull_policy != WORLD_CULL_BACK
            && instance.cull_policy != WORLD_CULL_FRONT
        {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world first-person mesh cull policy {}",
                    instance.cull_policy
                ),
            ));
        }
        if instance.winding != WORLD_WINDING_CCW && instance.winding != WORLD_WINDING_CW {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world first-person mesh winding {}",
                    instance.winding
                ),
            ));
        }
        let viewport_width = decode_world_viewport_axis(
            instance.viewport_width,
            "world first-person mesh viewport width",
        )?;
        let viewport_height = decode_world_viewport_axis(
            instance.viewport_height,
            "world first-person mesh viewport height",
        )?;
        instances.push(WorldMeshInstanceRequest {
            model_submission_order: decode_model_submission_order(instance)?,
            item_foil: decode_world_item_foil(instance)?,
            decal_foil: decode_world_decal_foil(instance, true)?,
            stratum: instance.stratum,
            mesh_key: instance.mesh_key,
            mesh_generation: instance.mesh_generation,
            mesh_section_index: instance.mesh_section_index,
            terrain_visible_facing_mask: 0x7f,
            depth_policy: instance.depth_policy,
            cull_policy: instance.cull_policy,
            winding: instance.winding,
            color_argb: instance.color_argb,
            entity_id: instance.entity_id,
            entity_color_argb: instance.entity_color_argb,
            packed_light: instance.packed_light,
            transform: instance.transform,
            outline_color_argb: instance.outline_color_argb,
            flags: instance.flags,
            block_entity_id: instance.block_entity_id,
            viewport_width,
            viewport_height,
        });
    }
    Ok(instances)
}

pub(super) fn decode_world_first_person_frame(
    request: FfiWorldFirstPersonFrame,
) -> GalResult<WorldFirstPersonFrame> {
    validate_item_size::<FfiWorldFirstPersonFrame>(request.byte_size, "world first-person frame")?;
    if request.enabled > 1 || request.clear_depth_before > 1 || request.translucent_hand_mask > 3 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world first-person frame has invalid flags",
        ));
    }
    if request.enabled == 0 {
        if request.clear_depth_before != 0
            || request.translucent_hand_mask != 0
            || request.projection_matrix.iter().any(|value| *value != 0.0)
            || request.model_view_matrix.iter().any(|value| *value != 0.0)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled world first-person frame must be zeroed",
            ));
        }
    } else {
        if request.clear_depth_before == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "enabled world first-person frame must explicitly clear its depth domain",
            ));
        }
        if request
            .projection_matrix
            .iter()
            .any(|value| !value.is_finite())
            || request.projection_matrix.iter().all(|value| *value == 0.0)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "enabled world first-person frame requires a finite non-zero projection matrix",
            ));
        }
        if request
            .model_view_matrix
            .iter()
            .any(|value| !value.is_finite())
            || request.model_view_matrix.iter().all(|value| *value == 0.0)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "enabled world first-person frame requires a finite non-zero model-view matrix",
            ));
        }
    }
    Ok(WorldFirstPersonFrame {
        enabled: request.enabled != 0,
        clear_depth_before: request.clear_depth_before != 0,
        main_hand_instance_count: request.main_hand_instance_count,
        projection_matrix: request.projection_matrix,
        model_view_matrix: request.model_view_matrix,
        translucent_hand_mask: request.translucent_hand_mask as u8,
    })
}

#[cfg(test)]
mod first_person_decode_tests {
    use crate::render::bridge::world::*;

    fn frame(enabled: bool, translucent_hand_mask: u32) -> FfiWorldFirstPersonFrame {
        let identity = [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ];
        FfiWorldFirstPersonFrame {
            byte_size: std::mem::size_of::<FfiWorldFirstPersonFrame>() as u32,
            enabled: u32::from(enabled),
            clear_depth_before: u32::from(enabled),
            main_hand_instance_count: 0,
            projection_matrix: if enabled { identity } else { [0.0; 16] },
            model_view_matrix: if enabled { identity } else { [0.0; 16] },
            translucent_hand_mask,
        }
    }

    #[test]
    fn translucent_hand_mask_names_only_enabled_hands() {
        let decoded = decode_world_first_person_frame(frame(true, 2)).unwrap();
        assert_eq!(2, decoded.translucent_hand_mask);
        assert!(decode_world_first_person_frame(frame(true, 4)).is_err());
        assert!(decode_world_first_person_frame(frame(false, 1)).is_err());
        assert_eq!(0, decode_world_first_person_frame(frame(false, 0)).unwrap().translucent_hand_mask);
    }
}
