use super::*;

pub(super) fn validate_segment(
    segment: &WorldLineSegmentRequest,
    frame: &WorldPrimitiveFrame,
) -> GalResult<()> {
    if segment.stratum != WORLD_STRATUM_BLOCK_OUTLINE {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("unsupported world primitive stratum {}", segment.stratum),
        ));
    }
    if segment.style > 2 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world primitive style {}", segment.style),
        ));
    }
    if segment.depth_policy > WORLD_DEPTH_POLICY_TEST_NO_WRITE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world primitive depth policy {}",
                segment.depth_policy
            ),
        ));
    }
    if segment.line_width <= 0.0 || !segment.line_width.is_finite() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive line width must be finite and positive",
        ));
    }
    if segment.viewport_width != frame.viewport_width || segment.viewport_height != frame.viewport_height
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive segment viewport metadata must match the frame viewport",
        ));
    }
    for value in segment.start.iter().chain(segment.end.iter()) {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world primitive segment coordinates must be finite",
            ));
        }
    }
    Ok(())
}
