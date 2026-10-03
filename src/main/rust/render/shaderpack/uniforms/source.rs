//! Semantic requirements for scalar uniforms declared by a lowered terrain
//! source pair.
//!
//! This is deliberately a catalog, not a name-to-bytes escape hatch. A
//! selected source can only become executable after every declaration is
//! supplied by one named gameplay semantic with the exact source type.

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::lowering::{
    TerrainSourceUniformContract, TerrainSourceUniformField, TerrainSourceUniformType,
};

mod expressions;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainSourceUniformSemantic {
    FrameCounter,
    /// Semantic world render-category selected by the Rust pass scheduler.
    /// This is not an Iris render-stage object or callback; source programs
    /// use its integer value to admit terrain voxelization work by layer.
    RenderStage,
    FrameModuloEight,
    WorldTime,
    WorldDay,
    MoonPhase,
    FrameTimeSeconds,
    FrameTimeCounter,
    /// Source-declared temporal smoothing of `frameTime`, owned by Rust from
    /// copied frame duration rather than an Iris custom-uniform object.
    FrameTimeSmooth,
    AspectRatio,
    Blindness,
    DarknessFactor,
    MaxBlindnessDarkness,
    SunAngle,
    SunPosition,
    MoonPosition,
    ShadowLightPosition,
    UpPosition,
    /// Rust-owned celestial primitive selector. `0` is the sun and `1` is
    /// the moon; the selected source program still receives its pack-defined
    /// render-stage integer separately.
    CelestialIsMoon,
    /// Copied vanilla celestial vertex alpha. This is per owned sky draw,
    /// not a Java dynamic-transform or backend blend state.
    CelestialAlpha,
    /// Parsed shader-pack sun-path rotation used by the owned celestial
    /// transform. The value is source configuration, never Iris state.
    CelestialSunPathRotation,
    /// Vanilla sky-clock fraction, independent of Iris's shifted sunAngle.
    CelestialTimeOfDay,
    RainStrength,
    RainFactor,
    ThunderStrength,
    SkyDarken,
    CameraWorldPosition,
    /// Integer block-space camera origin used by source programs for stable
    /// world-coordinate reconstruction. Rust derives this from the same
    /// copied semantic camera position as the floating and fractional forms.
    CameraWorldPositionInt,
    CameraWorldPositionFract,
    PreviousCameraWorldPosition,
    /// Magnitude of current-minus-previous semantic camera position.
    CameraVelocity,
    ViewMatrix,
    ViewMatrixInverse,
    ProjectionMatrix,
    ProjectionMatrixInverse,
    PreviousViewMatrix,
    PreviousProjectionMatrix,
    ShadowModelView,
    ShadowModelViewInverse,
    ShadowProjection,
    ShadowProjectionInverse,
    DistantModelView,
    DistantProjection,
    DistantProjectionInverse,
    ViewportWidth,
    ViewportHeight,
    NearPlane,
    EyeSubmersion,
    ScreenBrightness,
    DarknessLightFactor,
    NightVision,
    EyeBrightness,
    EyeBrightnessSmooth,
    EyeBrightnessM,
    EyeBrightnessM2,
    FogColor,
    /// Exact RGBA and environmental range used by legacy `gl_Fog`. These are
    /// distinct from a pack's `fogColor` uniform, which may have different
    /// source-defined composition semantics.
    LegacyFogColor,
    LegacyFogEnvironmentalStart,
    LegacyFogEnvironmentalEnd,
    BiomeDry,
    BiomeSnowy,
    BiomeNetherWastes,
    BiomeCrimsonForest,
    BiomeWarpedForest,
    BiomeBasaltDeltas,
    BiomeSoulValley,
    BiomePaleGarden,
    BiomeRainy,
    /// Source-defined surface wetness. Rust receives a semantic gameplay
    /// value; packs retain ownership of how it affects shading.
    Wetness,
    SkyColor,
    MaterialAtlasSize,
    FarPlane,
    /// Distant Horizons' configured block render distance. Iris exposes this
    /// whenever the pack enables its shared `DISTANT_HORIZONS` source branch,
    /// including near-terrain programs. Rust receives copied gameplay
    /// configuration rather than an Iris uniform value.
    DistantHorizonsRenderDistance,
    RelativeEyePosition,
    /// The currently rendered entity identity. Static terrain explicitly uses
    /// the documented non-entity sentinel; future entity passes must provide
    /// their own resolved semantic value.
    EntityId,
    /// Per-draw entity color override. This is a copied gameplay color owned
    /// by the Rust source-pass contract; it is not a Java/Iris uniform value
    /// or a backend binding.
    EntityColor,
    /// Pack-defined identity for the current item-render pass. Non-item
    /// passes use the explicit `-1` sentinel rather than inheriting a value
    /// from any legacy renderer state.
    CurrentRenderedItemId,
    /// The currently rendered block-entity identity. Static terrain explicitly
    /// uses the documented non-block-entity sentinel.
    BlockEntityId,
    HeldItemIdMain,
    HeldItemIdOffHand,
    HeldBlockLightMain,
    HeldBlockLightOffHand,
    /// Linked node in this requirement catalog's immutable custom program.
    /// The type is checked against the active property and GLSL declaration.
    CustomExpression { index: u16, ty: TerrainSourceUniformType },
}

impl TerrainSourceUniformSemantic {
    fn for_name(name: &str) -> Option<Self> {
        match name {
            "frameCounter" => Some(Self::FrameCounter),
            "renderStage" => Some(Self::RenderStage),
            "framemod8" => Some(Self::FrameModuloEight),
            "worldTime" => Some(Self::WorldTime),
            "worldDay" => Some(Self::WorldDay),
            "moonPhase" => Some(Self::MoonPhase),
            "frameTime" => Some(Self::FrameTimeSeconds),
            "frameTimeCounter" => Some(Self::FrameTimeCounter),
            "frameTimeSmooth" => Some(Self::FrameTimeSmooth),
            "aspectRatio" => Some(Self::AspectRatio),
            "blindness" => Some(Self::Blindness),
            "darknessFactor" => Some(Self::DarknessFactor),
            "maxBlindnessDarkness" => Some(Self::MaxBlindnessDarkness),
            "sunAngle" => Some(Self::SunAngle),
            "sunPosition" => Some(Self::SunPosition),
            "moonPosition" => Some(Self::MoonPosition),
            "shadowLightPosition" => Some(Self::ShadowLightPosition),
            "upPosition" => Some(Self::UpPosition),
            "vulkanic_source_celestial_is_moon" => Some(Self::CelestialIsMoon),
            "vulkanic_source_celestial_alpha" => Some(Self::CelestialAlpha),
            "vulkanic_source_celestial_sun_path_rotation" => Some(Self::CelestialSunPathRotation),
            "vulkanic_source_celestial_time_of_day" => Some(Self::CelestialTimeOfDay),
            "rainStrength" => Some(Self::RainStrength),
            "rainFactor" => Some(Self::RainFactor),
            "thunderStrength" => Some(Self::ThunderStrength),
            "skyDarken" => Some(Self::SkyDarken),
            "cameraPosition" => Some(Self::CameraWorldPosition),
            "cameraPositionInt" => Some(Self::CameraWorldPositionInt),
            "cameraPositionFract" => Some(Self::CameraWorldPositionFract),
            "previousCameraPosition" => Some(Self::PreviousCameraWorldPosition),
            "velocity" => Some(Self::CameraVelocity),
            "gbufferModelView" => Some(Self::ViewMatrix),
            "gbufferModelViewInverse" => Some(Self::ViewMatrixInverse),
            "gbufferProjection" => Some(Self::ProjectionMatrix),
            "gbufferProjectionInverse" => Some(Self::ProjectionMatrixInverse),
            "gbufferPreviousModelView" => Some(Self::PreviousViewMatrix),
            "gbufferPreviousProjection" => Some(Self::PreviousProjectionMatrix),
            "shadowModelView" => Some(Self::ShadowModelView),
            "shadowModelViewInverse" => Some(Self::ShadowModelViewInverse),
            "shadowProjection" => Some(Self::ShadowProjection),
            "shadowProjectionInverse" => Some(Self::ShadowProjectionInverse),
            "dhModelView" => Some(Self::DistantModelView),
            "dhProjection" => Some(Self::DistantProjection),
            "dhProjectionInverse" => Some(Self::DistantProjectionInverse),
            "viewWidth" => Some(Self::ViewportWidth),
            "viewHeight" => Some(Self::ViewportHeight),
            "near" => Some(Self::NearPlane),
            "isEyeInWater" => Some(Self::EyeSubmersion),
            "screenBrightness" => Some(Self::ScreenBrightness),
            "darknessLightFactor" => Some(Self::DarknessLightFactor),
            "nightVision" => Some(Self::NightVision),
            "eyeBrightness" => Some(Self::EyeBrightness),
            "eyeBrightnessSmooth" => Some(Self::EyeBrightnessSmooth),
            "eyeBrightnessM" => Some(Self::EyeBrightnessM),
            "eyeBrightnessM2" => Some(Self::EyeBrightnessM2),
            "fogColor" => Some(Self::FogColor),
            "vulkanic_source_fog_parameter_color" => Some(Self::LegacyFogColor),
            "vulkanic_source_fog_environmental_start" => Some(Self::LegacyFogEnvironmentalStart),
            "vulkanic_source_fog_environmental_end" => Some(Self::LegacyFogEnvironmentalEnd),
            "inDry" => Some(Self::BiomeDry),
            "inSnowy" => Some(Self::BiomeSnowy),
            "inNetherWastes" => Some(Self::BiomeNetherWastes),
            "inCrimsonForest" => Some(Self::BiomeCrimsonForest),
            "inWarpedForest" => Some(Self::BiomeWarpedForest),
            "inBasaltDeltas" => Some(Self::BiomeBasaltDeltas),
            "inSoulValley" => Some(Self::BiomeSoulValley),
            "inPaleGarden" => Some(Self::BiomePaleGarden),
            "inRainy" => Some(Self::BiomeRainy),
            "wetness" => Some(Self::Wetness),
            "skyColor" => Some(Self::SkyColor),
            "atlasSize" => Some(Self::MaterialAtlasSize),
            "far" => Some(Self::FarPlane),
            "dhRenderDistance" => Some(Self::DistantHorizonsRenderDistance),
            "relativeEyePosition" => Some(Self::RelativeEyePosition),
            "entityId" => Some(Self::EntityId),
            "entityColor" => Some(Self::EntityColor),
            "currentRenderedItemId" => Some(Self::CurrentRenderedItemId),
            "blockEntityId" => Some(Self::BlockEntityId),
            "heldItemId" => Some(Self::HeldItemIdMain),
            "heldItemId2" => Some(Self::HeldItemIdOffHand),
            "heldBlockLightValue" => Some(Self::HeldBlockLightMain),
            "heldBlockLightValue2" => Some(Self::HeldBlockLightOffHand),
            _ => None,
        }
    }

    fn expected_type(self) -> TerrainSourceUniformType {
        match self {
            Self::CustomExpression { ty, .. } => ty,
            Self::FrameCounter
            | Self::RenderStage
            | Self::WorldTime
            | Self::WorldDay
            | Self::MoonPhase
            | Self::CelestialIsMoon
            | Self::EntityId
            | Self::CurrentRenderedItemId
            | Self::BlockEntityId
            | Self::HeldItemIdMain
            | Self::HeldItemIdOffHand
            | Self::HeldBlockLightMain
            | Self::HeldBlockLightOffHand => TerrainSourceUniformType::Int,
            Self::EntityColor => TerrainSourceUniformType::Vec4,
            Self::FrameModuloEight
            | Self::FrameTimeSeconds
            | Self::FrameTimeCounter
            | Self::FrameTimeSmooth
            | Self::AspectRatio
            | Self::Blindness
            | Self::DarknessFactor
            | Self::MaxBlindnessDarkness
            | Self::SunAngle
            | Self::CelestialAlpha
            | Self::CelestialSunPathRotation
            | Self::CelestialTimeOfDay
            | Self::RainStrength
            | Self::RainFactor
            | Self::ThunderStrength
            | Self::SkyDarken => TerrainSourceUniformType::Float,
            Self::CameraWorldPosition
            | Self::CameraWorldPositionFract
            | Self::PreviousCameraWorldPosition => TerrainSourceUniformType::Vec3,
            Self::CameraWorldPositionInt => TerrainSourceUniformType::IVec3,
            Self::CameraVelocity => TerrainSourceUniformType::Float,
            Self::ViewMatrix
            | Self::ViewMatrixInverse
            | Self::ProjectionMatrix
            | Self::ProjectionMatrixInverse
            | Self::PreviousViewMatrix
            | Self::PreviousProjectionMatrix
            | Self::ShadowModelView
            | Self::ShadowModelViewInverse
            | Self::ShadowProjection
            | Self::ShadowProjectionInverse
            | Self::DistantModelView
            | Self::DistantProjection
            | Self::DistantProjectionInverse => TerrainSourceUniformType::Mat4,
            Self::ViewportWidth
            | Self::ViewportHeight
            | Self::NearPlane
            | Self::ScreenBrightness
            | Self::DarknessLightFactor
            | Self::NightVision
            | Self::EyeBrightnessM
            | Self::EyeBrightnessM2 => TerrainSourceUniformType::Float,
            Self::BiomeDry
            | Self::BiomeSnowy
            | Self::BiomeNetherWastes
            | Self::BiomeCrimsonForest
            | Self::BiomeWarpedForest
            | Self::BiomeBasaltDeltas
            | Self::BiomeSoulValley => TerrainSourceUniformType::Float,
            Self::BiomePaleGarden | Self::BiomeRainy | Self::Wetness => {
                TerrainSourceUniformType::Float
            }
            Self::EyeSubmersion => TerrainSourceUniformType::Int,
            Self::FogColor | Self::SkyColor => TerrainSourceUniformType::Vec3,
            Self::LegacyFogColor => TerrainSourceUniformType::Vec4,
            Self::LegacyFogEnvironmentalStart | Self::LegacyFogEnvironmentalEnd => {
                TerrainSourceUniformType::Float
            }
            Self::MaterialAtlasSize => TerrainSourceUniformType::IVec2,
            Self::EyeBrightness | Self::EyeBrightnessSmooth => TerrainSourceUniformType::IVec2,
            Self::SunPosition | Self::MoonPosition | Self::ShadowLightPosition | Self::UpPosition => TerrainSourceUniformType::Vec3,
            Self::FarPlane => TerrainSourceUniformType::Float,
            Self::DistantHorizonsRenderDistance => TerrainSourceUniformType::Int,
            Self::RelativeEyePosition => TerrainSourceUniformType::Vec3,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceUniformRequirement {
    pub field: TerrainSourceUniformField,
    pub semantic: Option<TerrainSourceUniformSemantic>,
}

/// Immutable source-derived catalog of scalar uniform requirements. Unknown
/// declarations are retained explicitly and keep selected-source execution
/// unavailable instead of silently receiving a default value.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceUniformRequirements {
    fields: Vec<TerrainSourceUniformRequirement>,
    custom_program: expressions::Program,
}

/// Bounded candidate-diagnostic summary. Names are source semantic names,
/// never backend locations or Java renderer objects.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceUniformRequirementSummary {
    pub field_count: u32,
    pub resolved_field_count: u32,
    pub unresolved_field_names: Vec<String>,
}

impl TerrainSourceUniformRequirements {
    pub fn from_contract(contract: &TerrainSourceUniformContract) -> GalResult<Self> {
        let custom_roots = contract.fields().iter().filter(|field|
            TerrainSourceUniformSemantic::for_name(field.name()).is_none()
                && contract.custom_uniforms().get(field.name()).is_some_and(|definition| definition.uniform)
        ).map(|field| (field.name(), field.ty())).collect::<Vec<_>>();
        let custom_program = expressions::Program::compile(contract.custom_uniforms(), &custom_roots)?;
        let mut fields = Vec::with_capacity(contract.fields().len());
        for field in contract.fields() {
            let semantic = TerrainSourceUniformSemantic::for_name(field.name())
                .or_else(|| custom_program.root(field.name()).map(|index|
                    TerrainSourceUniformSemantic::CustomExpression { index, ty: field.ty() }
                ));
            if let Some(semantic) = semantic {
                if field.array_length() != 1 || field.ty() != semantic.expected_type() {
                    return Err(GalError::invalid_argument(format!(
                        "terrain source uniform '{}' is incompatible with semantic {:?}",
                        field.name(),
                        semantic
                    )));
                }
            }
            fields.push(TerrainSourceUniformRequirement {
                field: field.clone(),
                semantic,
            });
        }
        Ok(Self { fields, custom_program })
    }

    pub fn fields(&self) -> &[TerrainSourceUniformRequirement] {
        &self.fields
    }

    pub fn unresolved_fields(&self) -> impl Iterator<Item = &TerrainSourceUniformField> {
        self.fields
            .iter()
            .filter(|requirement| requirement.semantic.is_none())
            .map(|requirement| &requirement.field)
    }

    pub fn is_fully_semantic(&self) -> bool {
        self.unresolved_fields().next().is_none()
    }

    pub fn require_fully_semantic(&self) -> GalResult<()> {
        let unresolved = self
            .unresolved_fields()
            .take(8)
            .map(|field| field.name())
            .collect::<Vec<_>>();
        if unresolved.is_empty() {
            return Ok(());
        }
        Err(GalError::unsupported_feature(format!(
            "selected terrain source has unresolved semantic scalar uniforms: {}",
            unresolved.join(", ")
        )))
    }

    pub fn std140_size(&self) -> GalResult<u32> {
        let end = self.fields.iter().try_fold(0_u32, |end, requirement| {
            let field_end = requirement
                .field
                .offset()
                .checked_add(requirement.field.size())
                .ok_or_else(|| {
                    GalError::invalid_argument("terrain source scalar field range overflows u32")
                })?;
            Ok::<_, GalError>(end.max(field_end))
        })?;
        end.checked_add(15).map(|value| value & !15).ok_or_else(|| {
            GalError::invalid_argument("terrain source scalar block size overflows u32")
        })
    }

    pub fn summary(&self) -> TerrainSourceUniformRequirementSummary {
        const MAX_UNRESOLVED_DIAGNOSTICS: usize = 16;
        let unresolved = self.unresolved_fields().collect::<Vec<_>>();
        let unresolved_field_names = unresolved
            .iter()
            .take(MAX_UNRESOLVED_DIAGNOSTICS)
            .map(|field| field.name().to_string())
            .collect::<Vec<_>>();
        TerrainSourceUniformRequirementSummary {
            field_count: self.fields.len() as u32,
            resolved_field_count: self.fields.len() as u32 - unresolved.len() as u32,
            unresolved_field_names,
        }
    }
}

/// Explicit game-frame values that can currently satisfy a bounded subset of
/// source-declared terrain uniforms. These are values, not Java locations,
/// Iris objects, or backend upload descriptions. Future callsites may extend
/// this semantic record without changing source ABI layout rules.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct TerrainSourceUniformFrame {
    pub frame_counter: Option<i32>,
    /// Pack-defined world render category for the current Rust-owned pass.
    /// The runtime must provide it explicitly instead of borrowing Iris state.
    pub render_stage: Option<i32>,
    pub frame_modulo_eight: Option<f32>,
    pub world_time: Option<i32>,
    pub world_day: Option<i32>,
    pub moon_phase: Option<i32>,
    pub frame_time_seconds: Option<f32>,
    pub frame_time_counter: Option<f32>,
    pub frame_time_smooth: Option<f32>,
    pub aspect_ratio: Option<f32>,
    pub blindness: Option<f32>,
    pub darkness_factor: Option<f32>,
    pub max_blindness_darkness: Option<f32>,
    pub sun_angle: Option<f32>,
    pub sun_position: Option<[f32; 3]>,
    pub moon_position: Option<[f32; 3]>,
    pub shadow_light_position: Option<[f32; 3]>,
    pub up_position: Option<[f32; 3]>,
    pub celestial_is_moon: Option<i32>,
    pub celestial_alpha: Option<f32>,
    pub celestial_sun_path_rotation: Option<f32>,
    pub celestial_time_of_day: Option<f32>,
    pub rain_strength: Option<f32>,
    pub rain_factor: Option<f32>,
    pub thunder_strength: Option<f32>,
    pub sky_darken: Option<f32>,
    pub camera_world_position: Option<[f32; 3]>,
    pub camera_world_position_int: Option<[i32; 3]>,
    pub camera_world_position_fract: Option<[f32; 3]>,
    pub previous_camera_world_position: Option<[f32; 3]>,
    pub camera_velocity: Option<f32>,
    /// Source matrix convention is retained exactly as copied semantic data.
    /// No row/column or API coordinate conversion occurs in this packer.
    pub view_matrix: Option<[f32; 16]>,
    pub view_matrix_inverse: Option<[f32; 16]>,
    pub projection_matrix: Option<[f32; 16]>,
    pub projection_matrix_inverse: Option<[f32; 16]>,
    pub previous_view_matrix: Option<[f32; 16]>,
    pub previous_projection_matrix: Option<[f32; 16]>,
    pub shadow_model_view: Option<[f32; 16]>,
    pub shadow_model_view_inverse: Option<[f32; 16]>,
    pub shadow_projection: Option<[f32; 16]>,
    pub shadow_projection_inverse: Option<[f32; 16]>,
    /// Distant Horizons' source-declared view transform. This is copied
    /// semantic frame data, not a borrowed DH or OpenGL matrix object.
    pub distant_model_view: Option<[f32; 16]>,
    pub distant_projection: Option<[f32; 16]>,
    pub distant_projection_inverse: Option<[f32; 16]>,
    pub viewport_width: Option<f32>,
    pub viewport_height: Option<f32>,
    pub near_plane: Option<f32>,
    pub eye_submersion: Option<i32>,
    pub screen_brightness: Option<f32>,
    pub darkness_light_factor: Option<f32>,
    pub night_vision: Option<f32>,
    pub eye_brightness: Option<[i32; 2]>,
    pub eye_brightness_smooth: Option<[i32; 2]>,
    pub eye_brightness_m: Option<f32>,
    pub eye_brightness_m2: Option<f32>,
    pub fog_color: Option<[f32; 3]>,
    /// Exact copied RGBA fog parameter record and environmental range used by
    /// legacy `gl_Fog`, never a renderer-owned compatibility object.
    pub legacy_fog_parameter_color: Option<[f32; 4]>,
    pub legacy_fog_environmental_start: Option<f32>,
    pub legacy_fog_environmental_end: Option<f32>,
    /// Raw vanilla precipitation at the camera block. This stays raw so the
    /// selected source's smoothing policy is owned by Rust.
    pub biome_precipitation: Option<i32>,
    /// Canonical camera-biome identity copied from gameplay. Shader-pack biome
    /// maps and smoothing remain Rust-owned source semantics.
    pub biome_resource_location: Option<String>,
    pub biome_dry: Option<f32>,
    pub biome_snowy: Option<f32>,
    pub biome_nether_wastes: Option<f32>,
    pub biome_crimson_forest: Option<f32>,
    pub biome_warped_forest: Option<f32>,
    pub biome_basalt_deltas: Option<f32>,
    pub biome_soul_valley: Option<f32>,
    pub biome_pale_garden: Option<f32>,
    pub biome_rainy: Option<f32>,
    pub wetness: Option<f32>,
    pub sky_color: Option<[f32; 3]>,
    /// Dimensions of the Rust-owned terrain atlas selected by the source
    /// resource contract. This is resource metadata, not a Java texture ID
    /// or backend handle.
    pub material_atlas_size: Option<[i32; 2]>,
    pub far_plane: Option<f32>,
    pub distant_horizons_render_distance: Option<i32>,
    pub relative_eye_position: Option<[f32; 3]>,
    /// Explicit current-pass entity identity. `-1` means no entity is being
    /// rendered, matching the shader-pack semantic default rather than an
    /// inherited Iris uniform value.
    pub entity_id: Option<i32>,
    /// Explicit current-pass entity color in normalized RGBA order. An entity
    /// source writer must set this per compatible draw group rather than
    /// relying on any Java/Iris-managed state.
    pub entity_color: Option<[f32; 4]>,
    /// Explicit current rendered-item identity. `-1` is the source-standard
    /// non-item sentinel used by entity and terrain passes.
    pub current_rendered_item_id: Option<i32>,
    /// Explicit current-pass block-entity identity. `-1` means no block entity
    /// is being rendered.
    pub block_entity_id: Option<i32>,
    /// Item IDs are pack-owned integers resolved by Rust from copied vanilla
    /// item-model identities.
    pub held_item_id_main: Option<i32>,
    pub held_item_id_off_hand: Option<i32>,
    /// Pack-owned held-light semantics derived from copied vanilla item light
    /// emission and the active source generation's `oldHandLight` policy.
    pub held_block_light_main: Option<i32>,
    pub held_block_light_off_hand: Option<i32>,
}

impl TerrainSourceUniformFrame {
    /// Produces the exact std140 scalar block required by one source-derived
    /// requirement catalog. Missing semantic values fail explicitly; unknown
    /// source declarations cannot be filled by a generic byte payload.
    pub fn pack_std140(
        &self,
        requirements: &TerrainSourceUniformRequirements,
    ) -> GalResult<Vec<u8>> {
        requirements.require_fully_semantic()?;
        let mut bytes = vec![0_u8; requirements.std140_size()? as usize];
        let custom_values = requirements.custom_program.evaluate(self)?;
        for requirement in requirements.fields() {
            let semantic = requirement.semantic.expect("fully semantic requirement");
            let offset = requirement.field.offset() as usize;
            match semantic {
                TerrainSourceUniformSemantic::SunPosition | TerrainSourceUniformSemantic::MoonPosition | TerrainSourceUniformSemantic::ShadowLightPosition | TerrainSourceUniformSemantic::UpPosition => {
                    let value = match semantic {
                        TerrainSourceUniformSemantic::SunPosition => self.sun_position,
                        TerrainSourceUniformSemantic::MoonPosition => self.moon_position,
                        TerrainSourceUniformSemantic::ShadowLightPosition => self.shadow_light_position,
                        _ => self.up_position,
                    };
                    let value = self.required_vec3(value, "celestial view-space position")?;
                    for (component, value) in value.into_iter().enumerate() { write_f32(&mut bytes, offset + component*4, value)?; }
                }
                TerrainSourceUniformSemantic::EyeBrightnessSmooth => {
                    let values = self.required_smoothed_eye_brightness()?;
                    for (component, value) in values.into_iter().enumerate() { write_i32(&mut bytes, offset + component*4, value)?; }
                }
                TerrainSourceUniformSemantic::CustomExpression { index, .. } => {
                    use expressions::Value;
                    match custom_values[index as usize].expect("evaluated custom root") {
                        Value::Float(value) => write_f32(&mut bytes, offset, value)?,
                        Value::Int(value) => write_i32(&mut bytes, offset, value)?,
                        Value::Bool(value) => write_i32(&mut bytes, offset, i32::from(value))?,
                        Value::Vector(values, count) => {
                            for component in 0..count as usize {
                                write_f32(&mut bytes, offset + component * 4, values[component])?;
                            }
                        }
                    }
                }
                TerrainSourceUniformSemantic::FrameCounter => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.frame_counter, "frame counter")?,
                    )?;
                }
                TerrainSourceUniformSemantic::RenderStage => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.render_stage, "world render stage")?,
                    )?;
                }
                TerrainSourceUniformSemantic::FrameModuloEight => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.frame_modulo_eight, "frame modulo eight")?,
                    )?;
                }
                TerrainSourceUniformSemantic::WorldTime => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.world_time, "world time")?,
                    )?;
                }
                TerrainSourceUniformSemantic::WorldDay => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.world_day, "world day")?,
                    )?;
                }
                TerrainSourceUniformSemantic::MoonPhase => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.moon_phase, "moon phase")?,
                    )?;
                }
                TerrainSourceUniformSemantic::FrameTimeSeconds => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.frame_time_seconds, "frame time seconds")?,
                    )?;
                }
                TerrainSourceUniformSemantic::FrameTimeCounter => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.frame_time_counter, "frame time counter")?,
                    )?;
                }
                TerrainSourceUniformSemantic::FrameTimeSmooth => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.frame_time_smooth, "smoothed frame time")?,
                    )?;
                }
                TerrainSourceUniformSemantic::AspectRatio => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.aspect_ratio, "aspect ratio")?,
                    )?;
                }
                TerrainSourceUniformSemantic::Blindness => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.blindness, "blindness")?,
                    )?;
                }
                TerrainSourceUniformSemantic::DarknessFactor => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.darkness_factor, "darkness factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::MaxBlindnessDarkness => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(
                            self.max_blindness_darkness,
                            "maximum blindness/darkness factor",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::SunAngle => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.sun_angle, "sun angle")?,
                    )?;
                }
                TerrainSourceUniformSemantic::CelestialIsMoon => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.celestial_is_moon, "celestial moon selector")?,
                    )?;
                }
                TerrainSourceUniformSemantic::CelestialAlpha => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.celestial_alpha, "celestial alpha")?,
                    )?;
                }
                TerrainSourceUniformSemantic::CelestialSunPathRotation => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(
                            self.celestial_sun_path_rotation,
                            "celestial sun-path rotation",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::CelestialTimeOfDay => {
                    write_f32(&mut bytes, offset,
                        self.required_f32(self.celestial_time_of_day, "celestial time of day")?)?;
                }
                TerrainSourceUniformSemantic::RainStrength => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.rain_strength, "rain strength")?,
                    )?;
                }
                TerrainSourceUniformSemantic::RainFactor => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.rain_factor, "rain factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ThunderStrength => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.thunder_strength, "thunder strength")?,
                    )?;
                }
                TerrainSourceUniformSemantic::SkyDarken => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.sky_darken, "sky darken")?,
                    )?;
                }
                TerrainSourceUniformSemantic::CameraWorldPosition => {
                    let value =
                        self.required_vec3(self.camera_world_position, "camera world position")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::CameraWorldPositionInt => {
                    let value = self.required_ivec3(
                        self.camera_world_position_int,
                        "integer camera world position",
                    )?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_i32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::CameraWorldPositionFract => {
                    let value = self.required_vec3(
                        self.camera_world_position_fract,
                        "camera world position fraction",
                    )?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::PreviousCameraWorldPosition => {
                    let value = self.required_vec3(
                        self.previous_camera_world_position,
                        "previous camera world position",
                    )?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::CameraVelocity => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.camera_velocity, "camera velocity")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ViewMatrix => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.view_matrix, "view matrix")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ViewMatrixInverse => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.view_matrix_inverse, "view matrix inverse")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ProjectionMatrix => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.projection_matrix, "projection matrix")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ProjectionMatrixInverse => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(
                            self.projection_matrix_inverse,
                            "projection matrix inverse",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::PreviousViewMatrix => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.previous_view_matrix, "previous view matrix")?,
                    )?;
                }
                TerrainSourceUniformSemantic::PreviousProjectionMatrix => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(
                            self.previous_projection_matrix,
                            "previous projection matrix",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::ShadowModelView => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.shadow_model_view, "shadow model view")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ShadowModelViewInverse => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(
                            self.shadow_model_view_inverse,
                            "shadow model view inverse",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::ShadowProjection => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.shadow_projection, "shadow projection")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ShadowProjectionInverse => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(
                            self.shadow_projection_inverse,
                            "shadow projection inverse",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::DistantModelView => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.distant_model_view, "Distant Horizons model view")?,
                    )?;
                }
                TerrainSourceUniformSemantic::DistantProjection => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(self.distant_projection, "Distant Horizons projection")?,
                    )?;
                }
                TerrainSourceUniformSemantic::DistantProjectionInverse => {
                    write_mat4(
                        &mut bytes,
                        offset,
                        self.required_mat4(
                            self.distant_projection_inverse,
                            "Distant Horizons projection inverse",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::ViewportWidth => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.viewport_width, "viewport width")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ViewportHeight => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.viewport_height, "viewport height")?,
                    )?;
                }
                TerrainSourceUniformSemantic::NearPlane => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.near_plane, "near plane")?,
                    )?;
                }
                TerrainSourceUniformSemantic::EyeSubmersion => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.eye_submersion, "eye submersion")?,
                    )?;
                }
                TerrainSourceUniformSemantic::ScreenBrightness => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.screen_brightness, "screen brightness")?,
                    )?;
                }
                TerrainSourceUniformSemantic::DarknessLightFactor => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.darkness_light_factor, "darkness light factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::NightVision => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.night_vision, "night vision")?,
                    )?;
                }
                TerrainSourceUniformSemantic::EyeBrightness => {
                    let value = self.required_eye_brightness()?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_i32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::EyeBrightnessM => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.eye_brightness_m, "smoothed eye brightness")?,
                    )?;
                }
                TerrainSourceUniformSemantic::EyeBrightnessM2 => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.eye_brightness_m2, "binary eye brightness")?,
                    )?;
                }
                TerrainSourceUniformSemantic::FogColor => {
                    let value = self.required_vec3(self.fog_color, "fog color")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::LegacyFogColor => {
                    let value = self.required_vec4(
                        self.legacy_fog_parameter_color,
                        "legacy fog parameter color",
                    )?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::LegacyFogEnvironmentalStart => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(
                            self.legacy_fog_environmental_start,
                            "legacy fog environmental start",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::LegacyFogEnvironmentalEnd => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(
                            self.legacy_fog_environmental_end,
                            "legacy fog environmental end",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeDry => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_dry, "biome dry factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeSnowy => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_snowy, "biome snowy factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeNetherWastes => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_nether_wastes, "Nether Wastes biome factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeCrimsonForest => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(
                            self.biome_crimson_forest,
                            "Crimson Forest biome factor",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeWarpedForest => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_warped_forest, "Warped Forest biome factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeBasaltDeltas => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_basalt_deltas, "Basalt Deltas biome factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeSoulValley => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_soul_valley, "Soul Sand Valley biome factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomePaleGarden => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_pale_garden, "Pale Garden biome factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::BiomeRainy => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.biome_rainy, "rainy-biome factor")?,
                    )?;
                }
                TerrainSourceUniformSemantic::Wetness => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.wetness, "surface wetness")?,
                    )?;
                }
                TerrainSourceUniformSemantic::SkyColor => {
                    let value = self.required_vec3(self.sky_color, "sky color")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::MaterialAtlasSize => {
                    let value =
                        self.required_ivec2(self.material_atlas_size, "material atlas size")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_i32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::FarPlane => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.far_plane, "far plane")?,
                    )?;
                }
                TerrainSourceUniformSemantic::DistantHorizonsRenderDistance => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(
                            self.distant_horizons_render_distance,
                            "Distant Horizons render distance",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::RelativeEyePosition => {
                    let value =
                        self.required_vec3(self.relative_eye_position, "relative eye position")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::EntityId => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.entity_id, "current rendered entity id")?,
                    )?;
                }
                TerrainSourceUniformSemantic::EntityColor => {
                    let value =
                        self.required_vec4(self.entity_color, "current rendered entity color")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
                }
                TerrainSourceUniformSemantic::CurrentRenderedItemId => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(
                            self.current_rendered_item_id,
                            "current rendered item id",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::BlockEntityId => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(
                            self.block_entity_id,
                            "current rendered block-entity id",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::HeldItemIdMain => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.held_item_id_main, "main-hand held item id")?,
                    )?;
                }
                TerrainSourceUniformSemantic::HeldItemIdOffHand => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.held_item_id_off_hand, "off-hand held item id")?,
                    )?;
                }
                TerrainSourceUniformSemantic::HeldBlockLightMain => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(
                            self.held_block_light_main,
                            "main-hand held block light",
                        )?,
                    )?;
                }
                TerrainSourceUniformSemantic::HeldBlockLightOffHand => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(
                            self.held_block_light_off_hand,
                            "off-hand held block light",
                        )?,
                    )?;
                }
            }
        }
        Ok(bytes)
    }

    fn required_i32(&self, value: Option<i32>, label: &str) -> GalResult<i32> {
        value.ok_or_else(|| {
            GalError::invalid_argument(format!("terrain source uniform requires {label}"))
        })
    }

    fn required_eye_brightness(&self) -> GalResult<[i32; 2]> {
        let value = self
            .eye_brightness
            .ok_or_else(|| GalError::invalid_argument("terrain source requires eye brightness"))?;
        if value
            .iter()
            .any(|component| !(0..=240).contains(component) || component % 16 != 0)
        {
            return Err(GalError::invalid_argument(format!(
                "terrain source eye brightness must contain packed vanilla light values in [0, 240]"
            )));
        }
        Ok(value)
    }

    fn required_smoothed_eye_brightness(&self) -> GalResult<[i32; 2]> {
        let value = self.eye_brightness_smooth.ok_or_else(|| GalError::invalid_argument("terrain source requires smoothed eye brightness"))?;
        if value.iter().any(|component| !(0..=240).contains(component)) {
            return Err(GalError::invalid_argument("smoothed eye brightness must contain light values in [0, 240]"));
        }
        Ok(value)
    }

    fn required_ivec2(&self, value: Option<[i32; 2]>, label: &str) -> GalResult<[i32; 2]> {
        let value = value.ok_or_else(|| {
            GalError::invalid_argument(format!("terrain source requires {label}"))
        })?;
        if value.iter().any(|component| *component <= 0) {
            return Err(GalError::invalid_argument(format!(
                "terrain source {label} must contain positive values"
            )));
        }
        Ok(value)
    }

    fn required_f32(&self, value: Option<f32>, label: &str) -> GalResult<f32> {
        let value = value.ok_or_else(|| {
            GalError::invalid_argument(format!("terrain source uniform requires {label}"))
        })?;
        if !value.is_finite() {
            return Err(GalError::invalid_argument(format!(
                "terrain source uniform {label} must be finite"
            )));
        }
        Ok(value)
    }

    fn required_vec3(&self, value: Option<[f32; 3]>, label: &str) -> GalResult<[f32; 3]> {
        let value = value.ok_or_else(|| {
            GalError::invalid_argument(format!("terrain source uniform requires {label}"))
        })?;
        if value.iter().any(|component| !component.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "terrain source uniform {label} must be finite"
            )));
        }
        Ok(value)
    }

    fn required_ivec3(&self, value: Option<[i32; 3]>, label: &str) -> GalResult<[i32; 3]> {
        value.ok_or_else(|| {
            GalError::invalid_argument(format!(
                "source uniform frame is missing required {label} semantic"
            ))
        })
    }

    fn required_vec4(&self, value: Option<[f32; 4]>, label: &str) -> GalResult<[f32; 4]> {
        let value = value.ok_or_else(|| {
            GalError::invalid_argument(format!("terrain source uniform requires {label}"))
        })?;
        if value.iter().any(|component| !component.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "terrain source uniform {label} must be finite"
            )));
        }
        Ok(value)
    }

    fn required_mat4(&self, value: Option<[f32; 16]>, label: &str) -> GalResult<[f32; 16]> {
        let value = value.ok_or_else(|| {
            GalError::invalid_argument(format!("terrain source uniform requires {label}"))
        })?;
        if value.iter().any(|component| !component.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "terrain source uniform {label} must be finite"
            )));
        }
        Ok(value)
    }
}

fn write_i32(bytes: &mut [u8], offset: usize, value: i32) -> GalResult<()> {
    write_bytes(bytes, offset, &value.to_le_bytes())
}

fn write_f32(bytes: &mut [u8], offset: usize, value: f32) -> GalResult<()> {
    write_bytes(bytes, offset, &value.to_le_bytes())
}

fn write_mat4(bytes: &mut [u8], offset: usize, value: [f32; 16]) -> GalResult<()> {
    for (component, value) in value.into_iter().enumerate() {
        write_f32(bytes, offset + component * 4, value)?;
    }
    Ok(())
}

fn write_bytes(bytes: &mut [u8], offset: usize, value: &[u8]) -> GalResult<()> {
    let end = offset.checked_add(value.len()).ok_or_else(|| {
        GalError::invalid_argument("terrain source scalar write range overflows usize")
    })?;
    let destination = bytes.get_mut(offset..end).ok_or_else(|| {
        GalError::invalid_argument("terrain source scalar write exceeds std140 block")
    })?;
    destination.copy_from_slice(value);
    Ok(())
}

#[cfg(test)]
mod tests;
