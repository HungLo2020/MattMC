use super::*;

pub(super) fn validate_frame_header(frame: &WorldPrimitiveFrame) -> GalResult<()> {
    if frame.frame_id == 0 || frame.correlation_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive frame and correlation ids must be non-zero",
        ));
    }
    if frame.viewport_width == 0 || frame.viewport_height == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive viewport must be non-empty",
        ));
    }
    if frame.segments.len() > WORLD_MAX_LINE_SEGMENTS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive line segment count {} exceeds maximum {}",
                frame.segments.len(),
                WORLD_MAX_LINE_SEGMENTS
            ),
        ));
    }
    if frame.crack_quads.len() > WORLD_MAX_CRACK_QUADS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world crack quad count {} exceeds maximum {}",
                frame.crack_quads.len(),
                WORLD_MAX_CRACK_QUADS
            ),
        ));
    }
    if frame.border_quads.len() > WORLD_MAX_BORDER_QUADS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world border quad count {} exceeds maximum {}",
                frame.border_quads.len(),
                WORLD_MAX_BORDER_QUADS
            ),
        ));
    }
    if frame.material_quads.len() > WORLD_MAX_MATERIAL_QUADS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world material quad count {} exceeds maximum {}",
                frame.material_quads.len(),
                WORLD_MAX_MATERIAL_QUADS
            ),
        ));
    }
    if frame.mesh_instances.len() > WORLD_MAX_MESH_INSTANCES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world mesh instance count {} exceeds maximum {}",
                frame.mesh_instances.len(),
                WORLD_MAX_MESH_INSTANCES
            ),
        ));
    }
    for value in frame
        .view_matrix
        .iter()
        .chain(frame.projection_matrix.iter())
    {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world primitive matrices must contain finite values",
            ));
        }
    }
    Ok(())
}
