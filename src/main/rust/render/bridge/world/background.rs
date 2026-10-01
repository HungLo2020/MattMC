//! The world background request and viewport bounds.

use super::*;

pub(super) fn decode_world_viewport_axis(value: i32, label: &str) -> GalResult<u32> {
    if value <= 0 || value > GUI_MAX_VIEWPORT_AXIS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "{label} must be within bounded positive range 1..={GUI_MAX_VIEWPORT_AXIS}, got {value}"
            ),
        ));
    }
    Ok(value as u32)
}

pub(crate) fn decode_world_background_request(
    request: FfiWorldBackgroundRequest,
    frame_viewport_width: i32,
    frame_viewport_height: i32,
) -> GalResult<WorldBackgroundRequest> {
    validate_item_size::<FfiWorldBackgroundRequest>(request.byte_size, "world background")?;
    let enabled = bool_flag(request.enabled, "world background enabled")?;
    if !enabled {
        return Ok(WorldBackgroundRequest::default());
    }
    if !matches!(
        request.sky_type,
        WORLD_BACKGROUND_SKY_OVERWORLD
            | WORLD_BACKGROUND_SKY_NETHER
            | WORLD_BACKGROUND_SKY_END
            | WORLD_BACKGROUND_SKY_CUSTOM
    ) {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world background sky type {}", request.sky_type),
        ));
    }
    if request.load_intent != WORLD_BACKGROUND_LOAD_CLEAR {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world background load intent {}",
                request.load_intent
            ),
        ));
    }
    if request.store_intent != WORLD_BACKGROUND_STORE_STORE {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown world background store intent {}",
                request.store_intent
            ),
        ));
    }
    let viewport_width = u32::try_from(request.viewport_width).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world background viewport width must be non-negative, got {}",
                request.viewport_width
            ),
        )
    })?;
    let viewport_height = u32::try_from(request.viewport_height).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world background viewport height must be non-negative, got {}",
                request.viewport_height
            ),
        )
    })?;
    if request.viewport_width != frame_viewport_width
        || request.viewport_height != frame_viewport_height
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world background viewport metadata must match whole-frame viewport",
        ));
    }
    if request.sky_reserved0 != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world background sky reserved field must be zero",
        ));
    }
    Ok(WorldBackgroundRequest {
        enabled,
        sky_type: request.sky_type,
        color_argb: request.color_argb,
        load_intent: request.load_intent,
        store_intent: request.store_intent,
        viewport_width,
        viewport_height,
        sky: crate::render::worldrender::WorldSkyRequest {
            visible: bool_flag(request.sky_visible, "world sky visible")?,
            sunrise_or_sunset: bool_flag(
                request.sky_sunrise_or_sunset,
                "world sky sunrise or sunset",
            )?,
            dark_disc: bool_flag(request.sky_dark_disc, "world sky dark disc")?,
            sun_angle: request.sky_sun_angle,
            time_of_day: request.sky_time_of_day,
            rain_brightness: request.sky_rain_brightness,
            star_brightness: request.sky_star_brightness,
            sunrise_and_sunset_color_argb: request.sky_sunrise_and_sunset_color_argb,
            moon_phase: request.sky_moon_phase,
            end_flash_intensity: request.sky_end_flash_intensity,
            end_flash_x_angle: request.sky_end_flash_x_angle,
            end_flash_y_angle: request.sky_end_flash_y_angle,
            sky_color_argb: request.sky_color_argb,
        },
    })
}
