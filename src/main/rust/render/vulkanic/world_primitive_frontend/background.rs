use super::*;

pub(super) fn validate_background(frame: &WorldPrimitiveFrame) -> GalResult<()> {
    if !frame.background.enabled {
        return Ok(());
    }
    if !matches!(
        frame.background.sky_type,
        WORLD_BACKGROUND_SKY_OVERWORLD
            | WORLD_BACKGROUND_SKY_NETHER
            | WORLD_BACKGROUND_SKY_END
            | WORLD_BACKGROUND_SKY_CUSTOM
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world background sky type {}",
                frame.background.sky_type
            ),
        ));
    }
    if frame.background.load_intent != WORLD_BACKGROUND_LOAD_CLEAR {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world background load intent {}",
                frame.background.load_intent
            ),
        ));
    }
    if frame.background.store_intent != WORLD_BACKGROUND_STORE_STORE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world background store intent {}",
                frame.background.store_intent
            ),
        ));
    }
    if frame.background.viewport_width != frame.viewport_width
        || frame.background.viewport_height != frame.viewport_height
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world background viewport metadata must match the frame viewport",
        ));
    }
    Ok(())
}
