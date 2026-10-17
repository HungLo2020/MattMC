//! Shader environment, voxel volume and feature coverage frames.

use super::*;

pub(super) fn decode_world_feature_coverage(
    request: FfiWorldFeatureCoverage,
) -> GalResult<WorldFeatureCoverageFrame> {
    validate_item_size::<FfiWorldFeatureCoverage>(
        request.byte_size,
        "world feature coverage frame",
    )?;
    Ok(WorldFeatureCoverageFrame {
        model_submits: request.model_submits,
        model_part_submits: request.model_part_submits,
        block_model_submits: request.block_model_submits,
        ordinary_block_submits: request.ordinary_block_submits,
        item_submits: request.item_submits,
        custom_geometry_submits: request.custom_geometry_submits,
        shadow_submits: request.shadow_submits,
        flame_submits: request.flame_submits,
        name_tag_submits: request.name_tag_submits,
        text_submits: request.text_submits,
        hitbox_submits: request.hitbox_submits,
        leash_submits: request.leash_submits,
        particle_group_submits: request.particle_group_submits,
    })
}

pub(super) fn decode_world_shader_environment_frame(
    request: FfiWorldShaderEnvironmentFrame,
) -> GalResult<WorldShaderEnvironmentFrame> {
    validate_item_size::<FfiWorldShaderEnvironmentFrame>(
        request.byte_size,
        "world shader environment frame",
    )?;
    let enabled = bool_flag(request.enabled, "world shader environment frame enabled")?;
    let biome_resource_location = unsafe {
        read_label(
            request.biome_resource_location_utf8,
            "world shader environment biome resource location",
        )
    }?;
    let main_hand_item_model_resource_location = unsafe {
        read_label(
            request.main_hand_item_model_resource_location_utf8,
            "world shader environment main-hand item-model resource location",
        )
    }?;
    let off_hand_item_model_resource_location = unsafe {
        read_label(
            request.off_hand_item_model_resource_location_utf8,
            "world shader environment off-hand item-model resource location",
        )
    }?;
    if request.lightmap_reserved != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment lightmap reserved field must be zero",
        ));
    }
    let vanilla_lightmap = if bool_flag(
        request.lightmap_enabled,
        "world shader environment lightmap enabled",
    )? {
        Some(VanillaLightmapFrame {
            generation: request.lightmap_generation,
            inputs: VanillaLightmapInputs {
                ambient_light_factor: request.lightmap_ambient_light_factor,
                sky_factor: request.lightmap_sky_factor,
                block_factor: request.lightmap_block_factor,
                night_vision_factor: request.lightmap_night_vision_factor,
                darkness_scale: request.lightmap_darkness_scale,
                darken_world_factor: request.lightmap_darken_world_factor,
                brightness_factor: request.lightmap_brightness_factor,
                sky_light_color: [
                    request.lightmap_sky_light_r,
                    request.lightmap_sky_light_g,
                    request.lightmap_sky_light_b,
                ],
                ambient_color: [
                    request.lightmap_ambient_r,
                    request.lightmap_ambient_g,
                    request.lightmap_ambient_b,
                ],
            },
        })
    } else {
        if request.lightmap_generation != 0
            || [
                request.lightmap_ambient_light_factor,
                request.lightmap_sky_factor,
                request.lightmap_block_factor,
                request.lightmap_night_vision_factor,
                request.lightmap_darkness_scale,
                request.lightmap_darken_world_factor,
                request.lightmap_brightness_factor,
                request.lightmap_sky_light_r,
                request.lightmap_sky_light_g,
                request.lightmap_sky_light_b,
                request.lightmap_ambient_r,
                request.lightmap_ambient_g,
                request.lightmap_ambient_b,
            ]
            .into_iter()
            .any(|value| value != 0.0)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled world shader environment lightmap must be zeroed",
            ));
        }
        None
    };
    if let Some(lightmap) = vanilla_lightmap {
        lightmap.validate().map_err(|error| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!("world shader environment lightmap is invalid: {error}"),
            )
        })?;
    }
    let environment = WorldShaderEnvironmentFrame {
        enabled,
        world_generation: request.world_generation,
        world_time: request.world_time,
        frame_time_seconds: request.frame_time_seconds,
        frame_counter: request.frame_counter,
        frame_time_counter: request.frame_time_counter,
        world_day: request.world_day,
        moon_phase: request.moon_phase,
        time_of_day: request.time_of_day,
        rain_strength: request.rain_strength,
        thunder_strength: request.thunder_strength,
        sky_darken: request.sky_darken,
        eye_submersion: request.eye_submersion,
        screen_brightness: request.screen_brightness,
        far_plane: request.far_plane,
        distant_horizons_render_distance: request.distant_horizons_render_distance,
        configured_shadow_distance_chunks: request.configured_shadow_distance_chunks,
        relative_eye_position: [
            request.relative_eye_x,
            request.relative_eye_y,
            request.relative_eye_z,
        ],
        sky_color: [
            request.sky_color_r,
            request.sky_color_g,
            request.sky_color_b,
        ],
        darkness_light_factor: request.darkness_light_factor,
        blindness: request.blindness,
        darkness_factor: request.darkness_factor,
        eye_brightness: [request.eye_brightness_block, request.eye_brightness_sky],
        night_vision: request.night_vision,
        fog_color: [
            request.fog_color_r,
            request.fog_color_g,
            request.fog_color_b,
        ],
        fog_parameter_color: [
            request.fog_parameter_color_r,
            request.fog_parameter_color_g,
            request.fog_parameter_color_b,
            request.fog_parameter_color_a,
        ],
        fog_environmental_start: request.fog_environmental_start,
        fog_environmental_end: request.fog_environmental_end,
        fog_render_distance_start: request.fog_render_distance_start,
        fog_render_distance_end: request.fog_render_distance_end,
        fog_sky_end: request.fog_sky_end,
        fog_clouds_end: request.fog_clouds_end,
        biome_precipitation: request.biome_precipitation,
        biome_resource_location,
        main_hand_item_model_resource_location,
        off_hand_item_model_resource_location,
        main_hand_item_light_emission: request.main_hand_item_light_emission,
        off_hand_item_light_emission: request.off_hand_item_light_emission,
        vanilla_lightmap,
    };
    if !enabled {
        if environment != WorldShaderEnvironmentFrame::default() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled world shader environment frame must be zeroed",
            ));
        }
        return Ok(environment);
    }
    if environment.world_generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled world shader environment frame requires a world generation",
        ));
    }
    for (label, value) in [
        ("time of day", environment.time_of_day),
        ("rain strength", environment.rain_strength),
        ("thunder strength", environment.thunder_strength),
        ("sky darken", environment.sky_darken),
    ] {
        if !value.is_finite() || !(0.0..=1.0).contains(&value) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("world shader environment {label} must be finite and within [0, 1]"),
            ));
        }
    }
    if !environment.darkness_light_factor.is_finite() || environment.darkness_light_factor < 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment darkness light factor must be finite and non-negative",
        ));
    }
    if environment.distant_horizons_render_distance < 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment Distant Horizons render distance must be non-negative",
        ));
    }
    for (label, value) in [
        ("blindness", environment.blindness),
        ("darkness factor", environment.darkness_factor),
    ] {
        if !value.is_finite() || !(0.0..=1.0).contains(&value) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("world shader environment {label} must be finite and within [0, 1]"),
            ));
        }
    }
    if environment
        .eye_brightness
        .iter()
        .any(|value| !(0..=240).contains(value) || value % 16 != 0)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment eye brightness must contain packed vanilla light values in [0, 240]",
        ));
    }
    if !environment.night_vision.is_finite() || !(0.0..=1.0).contains(&environment.night_vision) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment night vision must be finite and within [0, 1]",
        ));
    }
    if environment
        .fog_color
        .iter()
        .any(|value| !value.is_finite() || !(0.0..=1.0).contains(value))
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment fog color must be finite and within [0, 1]",
        ));
    }
    for (label, value) in [
        ("fog parameter red", environment.fog_parameter_color[0]),
        ("fog parameter green", environment.fog_parameter_color[1]),
        ("fog parameter blue", environment.fog_parameter_color[2]),
        ("fog parameter alpha", environment.fog_parameter_color[3]),
        (
            "fog environmental start",
            environment.fog_environmental_start,
        ),
        ("fog environmental end", environment.fog_environmental_end),
        (
            "fog render-distance start",
            environment.fog_render_distance_start,
        ),
        (
            "fog render-distance end",
            environment.fog_render_distance_end,
        ),
        ("fog sky end", environment.fog_sky_end),
    ] {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("world shader environment {label} must be finite"),
            ));
        }
    }
    if !(0..720_720).contains(&environment.frame_counter) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment frame counter must be within [0, 720720)",
        ));
    }
    if !(0.0..3600.0).contains(&environment.frame_time_counter) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment frame time counter must be within [0, 3600)",
        ));
    }
    if !environment.frame_time_seconds.is_finite()
        || !(0.0..3600.0).contains(&environment.frame_time_seconds)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment frame time must be finite and within [0, 3600)",
        ));
    }
    if !(0..=7).contains(&environment.moon_phase) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment moon phase must be within [0, 7]",
        ));
    }
    if !(0..=3).contains(&environment.eye_submersion) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment eye submersion must be within [0, 3]",
        ));
    }
    if !(0..=2).contains(&environment.biome_precipitation) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment biome precipitation must be 0 (none), 1 (rain), or 2 (snow)",
        ));
    }
    if !is_canonical_resource_location(&environment.biome_resource_location) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment biome resource location must be canonical namespace:path text",
        ));
    }
    for (label, value) in [
        (
            "main-hand item-model resource location",
            &environment.main_hand_item_model_resource_location,
        ),
        (
            "off-hand item-model resource location",
            &environment.off_hand_item_model_resource_location,
        ),
    ] {
        if !value.is_empty() && !is_canonical_resource_location(value) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world shader environment {label} must be empty or canonical namespace:path text"
                ),
            ));
        }
    }
    if !environment.screen_brightness.is_finite() || environment.screen_brightness < 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment screen brightness must be finite and non-negative",
        ));
    }
    if !environment.far_plane.is_finite() || environment.far_plane <= 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment far plane must be finite and positive",
        ));
    }
    if environment
        .relative_eye_position
        .iter()
        .any(|value| !value.is_finite())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment relative eye position must be finite",
        ));
    }
    if environment
        .sky_color
        .iter()
        .any(|value| !value.is_finite() || !(0.0..=1.0).contains(value))
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world shader environment sky color must be finite and within [0, 1]",
        ));
    }
    for (label, emission) in [
        (
            "main-hand item light emission",
            environment.main_hand_item_light_emission,
        ),
        (
            "off-hand item light emission",
            environment.off_hand_item_light_emission,
        ),
    ] {
        if !(0..=15).contains(&emission) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("world shader environment {label} must be within [0, 15]"),
            ));
        }
    }
    Ok(environment)
}

pub(crate) use crate::render::shaderpack::properties::item_ids::is_canonical_resource_location;

pub(super) fn decode_world_voxel_volume_frame(
    request: FfiWorldVoxelVolumeFrame,
) -> GalResult<WorldVoxelVolumeFrame> {
    validate_item_size::<FfiWorldVoxelVolumeFrame>(request.byte_size, "world voxel-volume frame")?;
    let enabled = bool_flag(request.enabled, "world voxel-volume frame enabled")?;
    if !enabled {
        if request.world_generation != 0
            || request.resource_generation != 0
            || request.camera_x != 0.0
            || request.camera_y != 0.0
            || request.camera_z != 0.0
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "disabled world voxel-volume frame must be zeroed",
            ));
        }
        return Ok(WorldVoxelVolumeFrame::default());
    }
    if request.world_generation == 0 || request.resource_generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "enabled world voxel-volume frame requires non-zero world and resource generations",
        ));
    }
    let camera_world_position = [request.camera_x, request.camera_y, request.camera_z];
    if camera_world_position.iter().any(|value| !value.is_finite()) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world voxel-volume camera position must be finite",
        ));
    }
    Ok(WorldVoxelVolumeFrame {
        enabled,
        world_generation: request.world_generation,
        resource_generation: request.resource_generation,
        camera_world_position,
    })
}
