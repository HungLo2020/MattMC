//! World mesh instance decoding: transforms, semantic identity, foil and terrain facing.

use super::*;

pub(super) fn decode_model_submission_order(instance: &FfiWorldMeshInstanceRecord) -> GalResult<Option<i32>> {
    match (
        instance.model_submission_order_mode,
        instance.model_submission_order,
    ) {
        (0, 0) => Ok(None),
        (1, order)
            if instance.terrain_placement_mode == 0
                && instance.stratum == WORLD_STRATUM_ENTITY_MESH =>
        {
            Ok(Some(order))
        }
        _ => Err(GalError::invalid_argument(
            "invalid model submission order declaration",
        )),
    }
}

pub(super) fn decode_world_item_foil(
    instance: &FfiWorldMeshInstanceRecord,
) -> GalResult<Option<crate::render::shared::item_foil::StandardItemFoil>> {
    let foil = crate::render::shared::item_foil::StandardItemFoil::decode(
        instance.item_foil_mode,
        instance.item_foil_clock_millis,
        instance.item_foil_speed,
        instance.item_foil_strength,
    )?;
    if foil.is_some()
        && (instance.stratum != WORLD_STRATUM_ENTITY_MESH
            || instance.terrain_placement_mode != 0
            || instance.flags != 0
            || instance.block_entity_id != -1)
    {
        return Err(GalError::invalid_argument(
            "standard foil requires an ordinary entity mesh instance",
        ));
    }
    Ok(foil)
}

pub(super) fn decode_world_decal_foil(
    instance: &FfiWorldMeshInstanceRecord,
    first_person: bool,
) -> GalResult<Option<crate::render::worldrender::features::decal_foil::WorldDecalFoilProjection>> {
    let decal = crate::render::worldrender::features::decal_foil::WorldDecalFoilProjection::decode(
        instance.decal_foil_mode,
        instance.decal_normal_mode,
        instance.decal_model_pose,
        instance.decal_normal_pose,
    )?;
    if let Some(value) = decal {
        let foil = decode_world_item_foil(instance)?;
        if !matches!(foil, Some(f) if f.kind == crate::render::shared::item_foil::StandardFoilKind::Item)
            || value.first_person != first_person
            || value.model_pose != instance.transform
        {
            return Err(GalError::invalid_argument(
                "world decal requires item foil, matching draw pose and display context",
            ));
        }
    }
    Ok(decal)
}

pub(super) fn is_world_mesh_stratum(stratum: u32) -> bool {
    matches!(
        stratum,
        WORLD_STRATUM_TERRAIN
            | WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY
            | WORLD_STRATUM_MOVING_MESH
            | WORLD_STRATUM_ENTITY_MESH
            | crate::render::scene::strata::WORLD_STRATUM_ENTITY_SHADOW_CASTER
    )
}

pub(super) fn validate_mesh_instance_semantic_identity(
    instance: &FfiWorldMeshInstanceRecord,
    label: &str,
) -> GalResult<()> {
    decode_mesh_instance_transform(instance)?;
    if instance.depth_policy == WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE
        && (instance.stratum != WORLD_STRATUM_ENTITY_MESH || instance.item_foil_mode != 0)
    {
        return Err(GalError::invalid_argument(
            "equal-depth writes require a non-foil entity mesh",
        ));
    }
    if instance.mesh_key == 0 || instance.mesh_generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} key and generation must be non-zero"),
        ));
    }
    if instance.packed_light > 0x00ff_00ff {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} contains invalid packed vanilla UV2 light"),
        ));
    }
    crate::render::shared::view_layering::validate_flags(
        if instance.stratum == WORLD_STRATUM_TERRAIN {
            instance.flags & !WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY
        } else {
            instance.flags
        },
        instance.stratum == WORLD_STRATUM_ENTITY_MESH,
        instance.item_foil_mode == 0
            && instance.block_entity_id == -1
            && instance.terrain_placement_mode == 0,
    )?;
    if instance.flags
        & !(WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY
            | WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS
            | WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY
            | crate::render::shared::view_layering::FLAGS)
        != 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} contains unknown semantic flags"),
        ));
    }
    if instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0
        && (instance.stratum != WORLD_STRATUM_TERRAIN
            || instance.mesh_section_index != WORLD_MESH_SECTION_ALL
            || !matches!(
                instance.depth_policy,
                WORLD_DEPTH_POLICY_TEST_WRITE | WORLD_DEPTH_POLICY_TEST_NO_WRITE
            ))
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} camera-sorted quads require complete translucent terrain"),
        ));
    }
    if instance.stratum == WORLD_STRATUM_TERRAIN
        && instance.flags & WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY != 0
        && instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} shadow-only instances require unsorted static terrain"),
        ));
    }
    if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0
        && (instance.stratum != WORLD_STRATUM_ENTITY_MESH || instance.outline_color_argb == 0)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} outline-only flag requires entity stratum and outline color"),
        ));
    }
    if instance.block_entity_id < -1 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} block entity id must be >= -1"),
        ));
    }
    Ok(())
}

pub(super) fn decode_mesh_instance_transform(instance: &FfiWorldMeshInstanceRecord) -> GalResult<[f32; 16]> {
    match instance.terrain_placement_mode {
        0 if instance.terrain_origin == [0; 3] && instance.terrain_camera == [0.0; 3] => {
            Ok(instance.transform)
        }
        1 if instance.stratum == WORLD_STRATUM_TERRAIN
            && instance.mesh_section_index == WORLD_MESH_SECTION_ALL
            && instance.entity_id == 0
            && instance.block_entity_id == -1
            && instance.transform
                == [
                    1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
                ] =>
        {
            crate::render::worldrender::terrain::placement::TerrainSectionPlacement {
                origin: instance.terrain_origin,
                camera: instance.terrain_camera,
            }
            .lower()
        }
        _ => Err(GalError::invalid_argument(
            "incoherent semantic terrain placement",
        )),
    }
}

pub(crate) fn terrain_visible_facing_mask(instance: &FfiWorldMeshInstanceRecord) -> u8 {
    if instance.terrain_placement_mode != 1 {
        return 0x7f;
    }
    let camera = instance.terrain_camera.map(|axis| axis.floor() as i64);
    let origin = instance.terrain_origin.map(i64::from);
    let mut mask = 1u8 << 6;
    for axis in 0..3 {
        if camera[axis] > origin[axis] - 3 {
            mask |= 1 << axis;
        }
        if camera[axis] < origin[axis] + 19 {
            mask |= 1 << (axis + 3);
        }
    }
    mask
}
