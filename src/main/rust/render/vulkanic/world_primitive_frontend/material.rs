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
        WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT | WORLD_MATERIAL_MODE_TRANSLUCENT
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material mode {}", quad.material_mode),
        ));
    }
    if !matches!(
        quad.source_program,
        WORLD_MATERIAL_SOURCE_UNSPECIFIED
            | WORLD_MATERIAL_SOURCE_TEXTURED
            | WORLD_MATERIAL_SOURCE_WEATHER
            | WORLD_MATERIAL_SOURCE_CLOUDS
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world material source program {}",
                quad.source_program
            ),
        ));
    }
    if !matches!(
        quad.source_uv_space,
        WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE | WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world material source UV space {}",
                quad.source_uv_space
            ),
        ));
    }
    if !texture_supports_uv_space(quad.texture_id, quad.source_uv_space) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world material texture {} requires Minecraft atlas UV semantics",
                quad.texture_id
            ),
        ));
    }
    if !super::material_registry::material_matches_mode(quad.material_id, quad.material_mode) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world material id {} is incompatible with mode {}",
                quad.material_id, quad.material_mode
            ),
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
    if quad.viewport_width != frame.viewport_width || quad.viewport_height != frame.viewport_height
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world material viewport metadata must match the frame viewport",
        ));
    }
    for value in quad
        .vertices
        .iter()
        .flatten()
        .chain(quad.uvs.iter().flatten())
    {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world material quad metadata must be finite",
            ));
        }
    }
    Ok(())
}

pub(crate) fn is_known_material_id(material_id: u32) -> bool {
    super::material_registry::is_known_material_key(material_id)
}

pub(crate) fn is_known_texture_id(texture_id: u32) -> bool {
    canonical_texture_id(texture_id).is_some()
}

pub(crate) fn canonical_material_id(material_id: u32) -> Option<u32> {
    super::material_registry::canonical_material_key(material_id)
}

pub(crate) fn canonical_texture_id(texture_id: u32) -> Option<u32> {
    super::material_registry::canonical_texture_key(texture_id)
        .or_else(|| is_runtime_mesh_texture_id(texture_id).then_some(texture_id))
}

/// The copied Minecraft atlas is a Rust-owned runtime asset, not a bundled
/// standalone texture. It is admitted only with its original atlas UV space.
pub(crate) fn is_runtime_mesh_texture_id(texture_id: u32) -> bool {
    texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS
}

pub(crate) fn texture_supports_uv_space(texture_id: u32, uv_space: u32) -> bool {
    !is_runtime_mesh_texture_id(texture_id)
        || uv_space == WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
}

pub(crate) fn material_matches_mode(material_id: u32, mode: u32) -> bool {
    super::material_registry::material_matches_mode(material_id, mode)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn copied_terrain_atlas_is_a_runtime_texture_with_atlas_uvs_only() {
        assert_eq!(
            Some(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS),
            canonical_texture_id(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
        );
        assert!(is_known_texture_id(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS));
        assert!(texture_supports_uv_space(
            WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
            WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
        ));
        assert!(!texture_supports_uv_space(
            WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
            WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE
        ));
        assert!(!is_known_texture_id(0xdead_beef));
    }
}
