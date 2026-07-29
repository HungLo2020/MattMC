use super::*;

pub(super) fn validate_quad(
    quad: &WorldMaterialQuadRequest,
    frame: &WorldPrimitiveFrame,
) -> GalResult<()> {
    if quad.stratum != WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("unsupported world material stratum {}", quad.stratum),
        ));
    }
    if !is_known_material_id(quad.material_id) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material id {}", quad.material_id),
        ));
    }
    if !is_known_texture_id(quad.texture_id) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material texture id {}", quad.texture_id),
        ));
    }
    if !matches!(
        quad.material_mode,
        WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material mode {}", quad.material_mode),
        ));
    }
    if quad.material_id == WORLD_MATERIAL_ID_OPAQUE_TEXTURED
        && quad.material_mode != WORLD_MATERIAL_MODE_OPAQUE
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "opaque world material id must use opaque material mode",
        ));
    }
    if matches!(
        quad.material_id,
        WORLD_MATERIAL_ID_CUTOUT_TEXTURED | WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT
    ) && quad.material_mode != WORLD_MATERIAL_MODE_CUTOUT
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "cutout world material id must use cutout material mode",
        ));
    }
    if quad.depth_policy > WORLD_DEPTH_POLICY_TEST_NO_WRITE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material depth policy {}", quad.depth_policy),
        ));
    }
    if !matches!(
        quad.cull_policy,
        WORLD_CULL_NONE | WORLD_CULL_BACK | WORLD_CULL_FRONT
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material cull policy {}", quad.cull_policy),
        ));
    }
    if quad.topology != WORLD_TOPOLOGY_TRIANGLES {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material topology {}", quad.topology),
        ));
    }
    if quad.winding != WORLD_WINDING_CCW && quad.winding != WORLD_WINDING_CW {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material winding {}", quad.winding),
        ));
    }
    if quad.viewport_width != frame.viewport_width || quad.viewport_height != frame.viewport_height {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world material viewport metadata must match the frame viewport",
        ));
    }
    for value in quad.vertices.iter().flatten().chain(quad.uvs.iter().flatten()) {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world material quad metadata must be finite",
            ));
        }
    }
    Ok(())
}

pub(super) fn is_known_material_id(material_id: u32) -> bool {
    matches!(
        material_id,
        WORLD_MATERIAL_ID_OPAQUE_TEXTURED
            | WORLD_MATERIAL_ID_CUTOUT_TEXTURED
            | WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT
    )
}

pub(super) fn is_known_texture_id(texture_id: u32) -> bool {
    texture_id == WORLD_MATERIAL_TEXTURE_STONE
        || texture_id == WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER
        || (WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_00
            ..=WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_15)
            .contains(&texture_id)
}
