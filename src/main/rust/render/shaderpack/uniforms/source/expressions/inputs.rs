//! Typed reads from the existing immutable source frame.

use super::*;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) struct Input {
    semantic: TerrainSourceUniformSemantic,
    component: Option<u8>,
    row: Option<u8>,
}

pub(super) fn component(name: &str) -> GalResult<u8> {
    match name {
        "x" | "r" | "s" | "0" => Ok(0),
        "y" | "g" | "t" | "1" => Ok(1),
        "z" | "b" | "p" | "2" => Ok(2),
        "w" | "a" | "q" | "3" => Ok(3),
        _ => Err(invalid(format!("invalid custom input component '{name}'"))),
    }
}

impl Input {
    pub(super) fn resolve(name: &str) -> GalResult<Option<(Self, Ty)>> {
        use TerrainSourceUniformType::*;
        let mut names = name.split('.');
        let base = names.next().unwrap();
        let Some(semantic) = TerrainSourceUniformSemantic::for_name(base) else {
            return Ok(None);
        };
        let selector = names.map(component).collect::<GalResult<Vec<_>>>()?;
        let source_type = semantic.expected_type();
        let ty = match source_type {
            Float | Int if selector.is_empty() => {
                if source_type == Float {
                    Ty::Float
                } else {
                    Ty::Int
                }
            }
            Vec2 | Vec3 | Vec4 | IVec2 | IVec3 => {
                let width = match source_type {
                    Vec2 | IVec2 => 2,
                    Vec3 | IVec3 => 3,
                    _ => 4,
                };
                let integer = matches!(source_type, IVec2 | IVec3);
                if selector.is_empty() && !integer {
                    Ty::Vector(width)
                } else if selector.len() == 1 && selector[0] < width {
                    if integer {
                        Ty::Int
                    } else {
                        Ty::Float
                    }
                } else {
                    return Err(invalid("custom vector input needs an in-range component"));
                }
            }
            Mat4 if selector.len() == 2 => Ty::Float,
            _ => {
                return Err(invalid(format!(
                    "custom input '{name}' requires an explicit supported type/component"
                )))
            }
        };
        Ok(Some((
            Self {
                semantic,
                component: selector.first().copied(),
                row: selector.get(1).copied(),
            },
            ty,
        )))
    }

    pub(super) fn read(self, frame: &TerrainSourceUniformFrame) -> GalResult<Value> {
        use TerrainSourceUniformSemantic::*;
        let missing = || {
            invalid(format!(
                "custom expression requires {:?} frame input",
                self.semantic
            ))
        };
        macro_rules! scalar {
            ($field:ident, $kind:ident) => {
                Value::$kind(frame.$field.ok_or_else(missing)?)
            };
        }
        let value = match self.semantic {
            FrameCounter => scalar!(frame_counter, Int),
            RenderStage => scalar!(render_stage, Int),
            WorldTime => scalar!(world_time, Int),
            WorldDay => scalar!(world_day, Int),
            MoonPhase => scalar!(moon_phase, Int),
            CelestialIsMoon => scalar!(celestial_is_moon, Int),
            EyeSubmersion => scalar!(eye_submersion, Int),
            EntityId => scalar!(entity_id, Int),
            CurrentRenderedItemId => scalar!(current_rendered_item_id, Int),
            BlockEntityId => scalar!(block_entity_id, Int),
            HeldItemIdMain => scalar!(held_item_id_main, Int),
            HeldItemIdOffHand => scalar!(held_item_id_off_hand, Int),
            HeldBlockLightMain => scalar!(held_block_light_main, Int),
            HeldBlockLightOffHand => scalar!(held_block_light_off_hand, Int),
            FrameModuloEight => scalar!(frame_modulo_eight, Float),
            FrameTimeSeconds => scalar!(frame_time_seconds, Float),
            FrameTimeCounter => scalar!(frame_time_counter, Float),
            FrameTimeSmooth => scalar!(frame_time_smooth, Float),
            AspectRatio => scalar!(aspect_ratio, Float),
            Blindness => scalar!(blindness, Float),
            DarknessFactor => scalar!(darkness_factor, Float),
            MaxBlindnessDarkness => scalar!(max_blindness_darkness, Float),
            SunAngle => scalar!(sun_angle, Float),
            CelestialAlpha => scalar!(celestial_alpha, Float),
            CelestialSunPathRotation => scalar!(celestial_sun_path_rotation, Float),
            CelestialTimeOfDay => scalar!(celestial_time_of_day, Float),
            RainStrength => scalar!(rain_strength, Float),
            RainFactor => scalar!(rain_factor, Float),
            ThunderStrength => scalar!(thunder_strength, Float),
            SkyDarken => scalar!(sky_darken, Float),
            CameraVelocity => scalar!(camera_velocity, Float),
            ViewportWidth => scalar!(viewport_width, Float),
            ViewportHeight => scalar!(viewport_height, Float),
            NearPlane => scalar!(near_plane, Float),
            ScreenBrightness => scalar!(screen_brightness, Float),
            DarknessLightFactor => scalar!(darkness_light_factor, Float),
            NightVision => scalar!(night_vision, Float),
            EyeBrightnessM => scalar!(eye_brightness_m, Float),
            EyeBrightnessM2 => scalar!(eye_brightness_m2, Float),
            LegacyFogEnvironmentalStart => scalar!(legacy_fog_environmental_start, Float),
            LegacyFogEnvironmentalEnd => scalar!(legacy_fog_environmental_end, Float),
            BiomeDry => scalar!(biome_dry, Float),
            BiomeSnowy => scalar!(biome_snowy, Float),
            BiomeNetherWastes => scalar!(biome_nether_wastes, Float),
            BiomeCrimsonForest => scalar!(biome_crimson_forest, Float),
            BiomeWarpedForest => scalar!(biome_warped_forest, Float),
            BiomeBasaltDeltas => scalar!(biome_basalt_deltas, Float),
            BiomeSoulValley => scalar!(biome_soul_valley, Float),
            BiomePaleGarden => scalar!(biome_pale_garden, Float),
            BiomeRainy => scalar!(biome_rainy, Float),
            Wetness => scalar!(wetness, Float),
            FarPlane => scalar!(far_plane, Float),
            DistantHorizonsRenderDistance => scalar!(distant_horizons_render_distance, Int),
            EyeBrightness => {
                Value::Int(frame.required_eye_brightness()?[self.component.unwrap() as usize])
            }
            EyeBrightnessSmooth => Value::Int(
                frame.required_smoothed_eye_brightness()?[self.component.unwrap() as usize],
            ),
            MaterialAtlasSize => Value::Int(
                frame.required_ivec2(frame.material_atlas_size, "atlas size")?
                    [self.component.unwrap() as usize],
            ),
            CameraWorldPositionInt => Value::Int(
                frame.camera_world_position_int.ok_or_else(missing)?
                    [self.component.unwrap() as usize],
            ),
            CameraWorldPosition
            | CameraWorldPositionFract
            | PreviousCameraWorldPosition
            | FogColor
            | SkyColor
            | RelativeEyePosition
            | SunPosition
            | MoonPosition
            | ShadowLightPosition
            | UpPosition => {
                let values = match self.semantic {
                    CameraWorldPosition => frame.camera_world_position,
                    CameraWorldPositionFract => frame.camera_world_position_fract,
                    PreviousCameraWorldPosition => frame.previous_camera_world_position,
                    FogColor => frame.fog_color,
                    SkyColor => frame.sky_color,
                    RelativeEyePosition => frame.relative_eye_position,
                    SunPosition => frame.sun_position,
                    MoonPosition => frame.moon_position,
                    ShadowLightPosition => frame.shadow_light_position,
                    UpPosition => frame.up_position,
                    _ => unreachable!(),
                }
                .ok_or_else(missing)?;
                vector(&values, self.component)?
            }
            EntityColor | LegacyFogColor => {
                let values = if self.semantic == EntityColor {
                    frame.entity_color
                } else {
                    frame.legacy_fog_parameter_color
                }
                .ok_or_else(missing)?;
                vector(&values, self.component)?
            }
            ViewMatrix
            | ViewMatrixInverse
            | ProjectionMatrix
            | ProjectionMatrixInverse
            | PreviousViewMatrix
            | PreviousProjectionMatrix
            | ShadowModelView
            | ShadowModelViewInverse
            | ShadowProjection
            | ShadowProjectionInverse
            | DistantModelView
            | DistantProjection
            | DistantProjectionInverse => {
                let values = match self.semantic {
                    ViewMatrix => frame.view_matrix,
                    ViewMatrixInverse => frame.view_matrix_inverse,
                    ProjectionMatrix => frame.projection_matrix,
                    ProjectionMatrixInverse => frame.projection_matrix_inverse,
                    PreviousViewMatrix => frame.previous_view_matrix,
                    PreviousProjectionMatrix => frame.previous_projection_matrix,
                    ShadowModelView => frame.shadow_model_view,
                    ShadowModelViewInverse => frame.shadow_model_view_inverse,
                    ShadowProjection => frame.shadow_projection,
                    ShadowProjectionInverse => frame.shadow_projection_inverse,
                    DistantModelView => frame.distant_model_view,
                    DistantProjection => frame.distant_projection,
                    DistantProjectionInverse => frame.distant_projection_inverse,
                    _ => unreachable!(),
                }
                .ok_or_else(missing)?;
                if values.iter().any(|value| !value.is_finite()) {
                    return Err(invalid("custom matrix input must be finite"));
                }
                Value::Float(
                    values[self.component.unwrap() as usize * 4 + self.row.unwrap() as usize],
                )
            }
            CustomExpression { .. } => {
                unreachable!("custom nodes are linked, not external frame inputs")
            }
        };
        value.finite()
    }
}

fn vector(values: &[f32], component: Option<u8>) -> GalResult<Value> {
    if values.iter().any(|value| !value.is_finite()) {
        return Err(invalid("custom vector input must be finite"));
    }
    if let Some(component) = component {
        return Ok(Value::Float(values[component as usize]));
    }
    let mut vector = [0.0; 4];
    vector[..values.len()].copy_from_slice(values);
    Ok(Value::Vector(vector, values.len() as u8))
}
