use super::*;

pub(super) fn validate_quad(
    quad: &WorldBorderQuadRequest,
    frame: &WorldPrimitiveFrame,
) -> GalResult<()> {
    if quad.stratum != WORLD_STRATUM_WORLD_BORDER {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("unsupported world border stratum {}", quad.stratum),
        ));
    }
    if quad.texture_id != WORLD_BORDER_TEXTURE_FORCEFIELD {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world border texture id {}", quad.texture_id),
        ));
    }
    if quad.blend_policy != WORLD_BORDER_BLEND_OVERLAY {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world border blend policy {}", quad.blend_policy),
        ));
    }
    if quad.cull_policy != WORLD_BORDER_CULL_NONE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world border cull policy {}", quad.cull_policy),
        ));
    }
    if quad.depth_policy > WORLD_DEPTH_POLICY_TEST_WRITE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world border depth policy {}", quad.depth_policy),
        ));
    }
    if quad.viewport_width != frame.viewport_width || quad.viewport_height != frame.viewport_height {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world border viewport metadata must match the frame viewport",
        ));
    }
    for value in quad
        .vertices
        .iter()
        .flatten()
        .chain(quad.uv_region.iter())
        .chain(quad.scroll.iter())
        .chain([quad.border_size, quad.distance_to_border].iter())
    {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world border quad metadata must be finite",
            ));
        }
    }
    if quad.border_size < 0.0 || quad.distance_to_border < 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world border size and distance must be non-negative",
        ));
    }
    Ok(())
}
