use super::*;

pub(super) fn validate_quad(
    quad: &WorldCrackQuadRequest,
    frame: &WorldPrimitiveFrame,
) -> GalResult<()> {
    if quad.stratum != WORLD_STRATUM_BLOCK_BREAKING_CRACK {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("unsupported world crack stratum {}", quad.stratum),
        ));
    }
    if quad.stage >= CRACK_STAGE_COUNT {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown block-breaking crack animation stage {}", quad.stage),
        ));
    }
    if quad.depth_policy > WORLD_DEPTH_POLICY_TEST_WRITE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world crack depth policy {}", quad.depth_policy),
        ));
    }
    if quad.viewport_width != frame.viewport_width || quad.viewport_height != frame.viewport_height {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world crack viewport metadata must match the frame viewport",
        ));
    }
    for value in quad.vertices.iter().flatten() {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world crack quad coordinates must be finite",
            ));
        }
    }
    Ok(())
}
