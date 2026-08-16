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
    let sky = frame.background.sky;
    if !sky.visible {
        if sky.sunrise_or_sunset
            || sky.dark_disc
            || sky.sun_angle != 0.0
            || sky.time_of_day != 0.0
            || sky.rain_brightness != 0.0
            || sky.star_brightness != 0.0
            || sky.sunrise_and_sunset_color_argb != 0
            || sky.moon_phase != 0
            || sky.end_flash_intensity != 0.0
            || sky.end_flash_x_angle != 0.0
            || sky.end_flash_y_angle != 0.0
            || sky.sky_color_argb != 0
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled world sky semantics must be zeroed",
            ));
        }
        return Ok(());
    }
    if !frame.background.enabled {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "visible world sky requires an enabled world background",
        ));
    }
    if !(0..=7).contains(&sky.moon_phase) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world sky moon phase must be within [0, 7]",
        ));
    }
    for (label, value) in [
        ("sun angle", sky.sun_angle),
        ("time of day", sky.time_of_day),
        ("rain brightness", sky.rain_brightness),
        ("star brightness", sky.star_brightness),
        ("end flash intensity", sky.end_flash_intensity),
        ("end flash x angle", sky.end_flash_x_angle),
        ("end flash y angle", sky.end_flash_y_angle),
    ] {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("world sky {label} must be finite"),
            ));
        }
    }
    Ok(())
}
