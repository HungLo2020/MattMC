//! Semantic requirements for scalar uniforms declared by a lowered terrain
//! source pair.
//!
//! This is deliberately a catalog, not a name-to-bytes escape hatch. A
//! selected source can only become executable after every declaration is
//! supplied by one named gameplay semantic with the exact source type.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::lowering::{
    TerrainSourceUniformContract, TerrainSourceUniformField, TerrainSourceUniformType,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainSourceUniformSemantic {
    FrameCounter,
    FrameModuloEight,
    WorldTime,
    WorldDay,
    MoonPhase,
    FrameTimeSeconds,
    FrameTimeCounter,
    SunAngle,
    RainStrength,
    RainFactor,
    ThunderStrength,
    SkyDarken,
    CameraWorldPosition,
    CameraWorldPositionFract,
    ViewMatrix,
    ViewMatrixInverse,
    ProjectionMatrix,
    ProjectionMatrixInverse,
    ViewportWidth,
    ViewportHeight,
    EyeSubmersion,
    ScreenBrightness,
    DarknessLightFactor,
    NightVision,
    FogColor,
    BiomeDry,
    BiomeSnowy,
    BiomeNetherWastes,
    BiomeCrimsonForest,
    BiomeWarpedForest,
    BiomeBasaltDeltas,
    BiomeSoulValley,
    SkyColor,
    MaterialAtlasSize,
    FarPlane,
    RelativeEyePosition,
    HeldItemIdMain,
    HeldItemIdOffHand,
    HeldBlockLightMain,
    HeldBlockLightOffHand,
}

impl TerrainSourceUniformSemantic {
    fn for_name(name: &str) -> Option<Self> {
        match name {
            "frameCounter" => Some(Self::FrameCounter),
            "framemod8" => Some(Self::FrameModuloEight),
            "worldTime" => Some(Self::WorldTime),
            "worldDay" => Some(Self::WorldDay),
            "moonPhase" => Some(Self::MoonPhase),
            "frameTime" => Some(Self::FrameTimeSeconds),
            "frameTimeCounter" => Some(Self::FrameTimeCounter),
            "sunAngle" => Some(Self::SunAngle),
            "rainStrength" => Some(Self::RainStrength),
            "rainFactor" => Some(Self::RainFactor),
            "thunderStrength" => Some(Self::ThunderStrength),
            "skyDarken" => Some(Self::SkyDarken),
            "cameraPosition" => Some(Self::CameraWorldPosition),
            "cameraPositionFract" => Some(Self::CameraWorldPositionFract),
            "gbufferModelView" => Some(Self::ViewMatrix),
            "gbufferModelViewInverse" => Some(Self::ViewMatrixInverse),
            "gbufferProjection" => Some(Self::ProjectionMatrix),
            "gbufferProjectionInverse" => Some(Self::ProjectionMatrixInverse),
            "viewWidth" => Some(Self::ViewportWidth),
            "viewHeight" => Some(Self::ViewportHeight),
            "isEyeInWater" => Some(Self::EyeSubmersion),
            "screenBrightness" => Some(Self::ScreenBrightness),
            "darknessLightFactor" => Some(Self::DarknessLightFactor),
            "nightVision" => Some(Self::NightVision),
            "fogColor" => Some(Self::FogColor),
            "inDry" => Some(Self::BiomeDry),
            "inSnowy" => Some(Self::BiomeSnowy),
            "inNetherWastes" => Some(Self::BiomeNetherWastes),
            "inCrimsonForest" => Some(Self::BiomeCrimsonForest),
            "inWarpedForest" => Some(Self::BiomeWarpedForest),
            "inBasaltDeltas" => Some(Self::BiomeBasaltDeltas),
            "inSoulValley" => Some(Self::BiomeSoulValley),
            "skyColor" => Some(Self::SkyColor),
            "atlasSize" => Some(Self::MaterialAtlasSize),
            "far" => Some(Self::FarPlane),
            "relativeEyePosition" => Some(Self::RelativeEyePosition),
            "heldItemId" => Some(Self::HeldItemIdMain),
            "heldItemId2" => Some(Self::HeldItemIdOffHand),
            "heldBlockLightValue" => Some(Self::HeldBlockLightMain),
            "heldBlockLightValue2" => Some(Self::HeldBlockLightOffHand),
            _ => None,
        }
    }

    fn expected_type(self) -> TerrainSourceUniformType {
        match self {
            Self::FrameCounter
            | Self::WorldTime
            | Self::WorldDay
            | Self::MoonPhase
            | Self::HeldItemIdMain
            | Self::HeldItemIdOffHand
            | Self::HeldBlockLightMain
            | Self::HeldBlockLightOffHand => TerrainSourceUniformType::Int,
            Self::FrameModuloEight
            | Self::FrameTimeSeconds
            | Self::FrameTimeCounter
            | Self::SunAngle
            | Self::RainStrength
            | Self::RainFactor
            | Self::ThunderStrength
            | Self::SkyDarken => TerrainSourceUniformType::Float,
            Self::CameraWorldPosition | Self::CameraWorldPositionFract => {
                TerrainSourceUniformType::Vec3
            }
            Self::ViewMatrix
            | Self::ViewMatrixInverse
            | Self::ProjectionMatrix
            | Self::ProjectionMatrixInverse => TerrainSourceUniformType::Mat4,
            Self::ViewportWidth
            | Self::ViewportHeight
            | Self::ScreenBrightness
            | Self::DarknessLightFactor
            | Self::NightVision => TerrainSourceUniformType::Float,
            Self::BiomeDry
            | Self::BiomeSnowy
            | Self::BiomeNetherWastes
            | Self::BiomeCrimsonForest
            | Self::BiomeWarpedForest
            | Self::BiomeBasaltDeltas
            | Self::BiomeSoulValley => TerrainSourceUniformType::Float,
            Self::EyeSubmersion => TerrainSourceUniformType::Int,
            Self::FogColor | Self::SkyColor => TerrainSourceUniformType::Vec3,
            Self::MaterialAtlasSize => TerrainSourceUniformType::IVec2,
            Self::FarPlane => TerrainSourceUniformType::Float,
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
        let mut fields = Vec::with_capacity(contract.fields().len());
        for field in contract.fields() {
            let semantic = TerrainSourceUniformSemantic::for_name(field.name());
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
        Ok(Self { fields })
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
    pub frame_modulo_eight: Option<f32>,
    pub world_time: Option<i32>,
    pub world_day: Option<i32>,
    pub moon_phase: Option<i32>,
    pub frame_time_seconds: Option<f32>,
    pub frame_time_counter: Option<f32>,
    pub sun_angle: Option<f32>,
    pub rain_strength: Option<f32>,
    pub rain_factor: Option<f32>,
    pub thunder_strength: Option<f32>,
    pub sky_darken: Option<f32>,
    pub camera_world_position: Option<[f32; 3]>,
    pub camera_world_position_fract: Option<[f32; 3]>,
    /// Source matrix convention is retained exactly as copied semantic data.
    /// No row/column or API coordinate conversion occurs in this packer.
    pub view_matrix: Option<[f32; 16]>,
    pub view_matrix_inverse: Option<[f32; 16]>,
    pub projection_matrix: Option<[f32; 16]>,
    pub projection_matrix_inverse: Option<[f32; 16]>,
    pub viewport_width: Option<f32>,
    pub viewport_height: Option<f32>,
    pub eye_submersion: Option<i32>,
    pub screen_brightness: Option<f32>,
    pub darkness_light_factor: Option<f32>,
    pub night_vision: Option<f32>,
    pub fog_color: Option<[f32; 3]>,
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
    pub sky_color: Option<[f32; 3]>,
    /// Dimensions of the Rust-owned terrain atlas selected by the source
    /// resource contract. This is resource metadata, not a Java texture ID
    /// or backend handle.
    pub material_atlas_size: Option<[i32; 2]>,
    pub far_plane: Option<f32>,
    pub relative_eye_position: Option<[f32; 3]>,
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
        for requirement in requirements.fields() {
            let semantic = requirement.semantic.expect("fully semantic requirement");
            let offset = requirement.field.offset() as usize;
            match semantic {
                TerrainSourceUniformSemantic::FrameCounter => {
                    write_i32(
                        &mut bytes,
                        offset,
                        self.required_i32(self.frame_counter, "frame counter")?,
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
                TerrainSourceUniformSemantic::SunAngle => {
                    write_f32(
                        &mut bytes,
                        offset,
                        self.required_f32(self.sun_angle, "sun angle")?,
                    )?;
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
                TerrainSourceUniformSemantic::CameraWorldPositionFract => {
                    let value = self.required_vec3(
                        self.camera_world_position_fract,
                        "camera world position fraction",
                    )?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
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
                TerrainSourceUniformSemantic::FogColor => {
                    let value = self.required_vec3(self.fog_color, "fog color")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
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
                TerrainSourceUniformSemantic::RelativeEyePosition => {
                    let value =
                        self.required_vec3(self.relative_eye_position, "relative eye position")?;
                    for (component, value) in value.into_iter().enumerate() {
                        write_f32(&mut bytes, offset + component * 4, value)?;
                    }
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

    fn required_ivec2(&self, value: Option<[i32; 2]>, label: &str) -> GalResult<[i32; 2]> {
        let value = value.ok_or_else(|| {
            GalError::invalid_argument(format!("terrain source requires {label}"))
        })?;
        if value.iter().any(|component| *component <= 0) {
            return Err(GalError::invalid_argument(format!(
                "terrain source {label} must contain positive finite values"
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
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::lowering::lower_terrain_source_pair;
    use crate::render::vulkanic::shader_pack::preprocess::preprocess_terrain_sources;
    use crate::render::vulkanic::shader_pack::source::{ShaderPackSource, ShaderSourceFile};
    use crate::render::vulkanic::shader_pack::terrain_contract::derive_complementary_terrain_contract;

    fn source_requirements(
        vertex_uniform: &str,
        vertex_use: &str,
        fragment_uniform: &str,
        fragment_use: &str,
    ) -> GalResult<TerrainSourceUniformRequirements> {
        let source = ShaderPackSource::new(
            "uniform-requirements",
            1,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.vsh",
                    format!(
                        "#version 130\n{vertex_uniform}\nuniform sampler2D tex;\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nvoid main() {{ {vertex_use} texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }}"
                    ),
                ),
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    format!(
                        "#version 130\n{fragment_uniform}\nuniform sampler2D tex;\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nvoid DoLighting() {{}}\n/* DRAWBUFFERS:06 */\nvoid main() {{ {fragment_use} vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }}"
                    ),
                ),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
                ShaderSourceFile::new(
                    "mattmc/terrain-resource-bindings.properties",
                    "tex=material_atlas\n",
                ),
            ],
        )?;
        let contract = derive_complementary_terrain_contract(&source)?;
        let artifacts = preprocess_terrain_sources(&source, &contract.source_stages()?)?;
        let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment)?;
        TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())
    }

    #[test]
    fn maps_known_source_uniforms_without_an_untyped_payload() {
        let requirements = source_requirements(
            "uniform mat4 gbufferModelView;\nuniform int worldTime;",
            "float source_world_time = float(worldTime);",
            "uniform float rainStrength;",
            "float source_rain_strength = rainStrength;",
        )
        .unwrap();
        assert!(requirements.is_fully_semantic());
        assert_eq!(4, requirements.fields().len());
        assert_eq!(
            Some(TerrainSourceUniformSemantic::ViewMatrix),
            requirements.fields()[0].semantic
        );
        assert_eq!(
            Some(TerrainSourceUniformSemantic::RainStrength),
            requirements.fields()[2].semantic
        );
        assert_eq!(
            Some(TerrainSourceUniformSemantic::WorldTime),
            requirements.fields()[3].semantic
        );
        assert_eq!(
            Some(TerrainSourceUniformSemantic::ProjectionMatrix),
            requirements.fields()[1].semantic
        );
    }

    #[test]
    fn maps_camera_environment_semantics_without_renderer_state() {
        let requirements = source_requirements(
            "uniform int isEyeInWater;\nuniform float screenBrightness;\nuniform float darknessLightFactor;\nuniform float nightVision;\nuniform float inDry;\nuniform float inSnowy;\nuniform float inNetherWastes;\nuniform float inCrimsonForest;\nuniform float inWarpedForest;\nuniform float inBasaltDeltas;\nuniform float inSoulValley;",
            "float source_submersion = float(isEyeInWater) + screenBrightness + darknessLightFactor + nightVision + inDry + inSnowy + inNetherWastes + inCrimsonForest + inWarpedForest + inBasaltDeltas + inSoulValley;",
            "uniform vec3 fogColor;\nuniform vec3 skyColor;",
            "vec3 source_fog = fogColor; vec3 source_sky = skyColor;",
        )
        .unwrap();
        assert!(requirements.is_fully_semantic());
        assert!(
            requirements
                .fields()
                .iter()
                .any(|requirement| requirement.semantic
                    == Some(TerrainSourceUniformSemantic::EyeSubmersion))
        );
        assert!(
            requirements
                .fields()
                .iter()
                .any(|requirement| requirement.semantic
                    == Some(TerrainSourceUniformSemantic::ScreenBrightness))
        );
        assert!(
            requirements
                .fields()
                .iter()
                .any(|requirement| requirement.semantic
                    == Some(TerrainSourceUniformSemantic::DarknessLightFactor))
        );
        assert!(
            requirements
                .fields()
                .iter()
                .any(|requirement| requirement.semantic
                    == Some(TerrainSourceUniformSemantic::NightVision))
        );
        assert!(requirements.fields().iter().any(
            |requirement| requirement.semantic == Some(TerrainSourceUniformSemantic::BiomeDry)
        ));
        assert!(
            requirements
                .fields()
                .iter()
                .any(|requirement| requirement.semantic
                    == Some(TerrainSourceUniformSemantic::BiomeNetherWastes))
        );
        assert!(
            requirements
                .fields()
                .iter()
                .any(|requirement| requirement.semantic
                    == Some(TerrainSourceUniformSemantic::BiomeSoulValley))
        );
        assert!(
            requirements
                .fields()
                .iter()
                .any(|requirement| requirement.semantic
                    == Some(TerrainSourceUniformSemantic::BiomeSnowy))
        );
        assert!(requirements.fields().iter().any(
            |requirement| requirement.semantic == Some(TerrainSourceUniformSemantic::FogColor)
        ));
        assert!(requirements.fields().iter().any(
            |requirement| requirement.semantic == Some(TerrainSourceUniformSemantic::SkyColor)
        ));
    }

    #[test]
    fn packs_rust_resolved_main_and_off_hand_item_ids() {
        let requirements = source_requirements(
            "uniform int heldItemId;\nuniform int heldItemId2;",
            "float source_held_items = float(heldItemId + heldItemId2);",
            "",
            "",
        )
        .unwrap();
        assert!(requirements.is_fully_semantic());
        let main_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::HeldItemIdMain))
            .expect("heldItemId must retain its semantic")
            .field
            .offset() as usize;
        let off_hand_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::HeldItemIdOffHand))
            .expect("heldItemId2 must retain its semantic")
            .field
            .offset() as usize;
        let frame = TerrainSourceUniformFrame {
            held_item_id_main: Some(45_032),
            held_item_id_off_hand: Some(-1),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        };
        let bytes = frame.pack_std140(&requirements).unwrap();
        assert_eq!(
            45_032_i32.to_le_bytes(),
            bytes[main_offset..main_offset + 4]
        );
        assert_eq!(
            (-1_i32).to_le_bytes(),
            bytes[off_hand_offset..off_hand_offset + 4]
        );
    }

    #[test]
    fn packs_pack_composed_main_and_off_hand_block_light() {
        let requirements = source_requirements(
            "uniform int heldBlockLightValue;\nuniform int heldBlockLightValue2;",
            "float source_held_light = float(heldBlockLightValue + heldBlockLightValue2);",
            "",
            "",
        )
        .unwrap();
        assert!(requirements.is_fully_semantic());
        let main_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::HeldBlockLightMain))
            .expect("heldBlockLightValue must retain its semantic")
            .field
            .offset() as usize;
        let off_hand_offset = requirements
            .fields()
            .iter()
            .find(|field| {
                field.semantic == Some(TerrainSourceUniformSemantic::HeldBlockLightOffHand)
            })
            .expect("heldBlockLightValue2 must retain its semantic")
            .field
            .offset() as usize;
        let bytes = TerrainSourceUniformFrame {
            held_block_light_main: Some(12),
            held_block_light_off_hand: Some(7),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();
        assert_eq!(12_i32.to_le_bytes(), bytes[main_offset..main_offset + 4]);
        assert_eq!(
            7_i32.to_le_bytes(),
            bytes[off_hand_offset..off_hand_offset + 4]
        );
    }

    #[test]
    fn unknown_or_mistyped_source_uniforms_remain_explicit_blockers() {
        let requirements = source_requirements(
            "uniform float packSpecificValue;",
            "float source_pack_specific = packSpecificValue;",
            "",
            "",
        )
        .unwrap();
        assert!(!requirements.is_fully_semantic());
        assert_eq!(
            vec!["packSpecificValue"],
            requirements
                .unresolved_fields()
                .map(|field| field.name())
                .collect::<Vec<_>>()
        );
        assert!(
            requirements
                .require_fully_semantic()
                .unwrap_err()
                .to_string()
                .contains("packSpecificValue")
        );
        assert_eq!(
            TerrainSourceUniformRequirementSummary {
                field_count: 3,
                resolved_field_count: 2,
                unresolved_field_names: vec!["packSpecificValue".to_string()],
            },
            requirements.summary()
        );
        assert!(
            source_requirements(
                "uniform float worldTime;",
                "float source_world_time = worldTime;",
                "",
                "",
            )
            .is_err()
        );
    }

    #[test]
    fn packs_recognized_semantics_at_the_source_derived_std140_offsets() {
        let requirements = source_requirements(
            "uniform mat4 gbufferModelView;\nuniform int worldTime;",
            "float source_world_time = float(worldTime);",
            "uniform float rainStrength;",
            "float source_rain_strength = rainStrength;",
        )
        .unwrap();
        let mut view = [0.0_f32; 16];
        view[0] = 2.0;
        view[5] = 3.0;
        view[10] = 4.0;
        view[15] = 1.0;
        let mut projection = [0.0_f32; 16];
        projection[0] = 5.0;
        projection[5] = 6.0;
        projection[10] = 7.0;
        projection[15] = 1.0;
        let bytes = TerrainSourceUniformFrame {
            view_matrix: Some(view),
            projection_matrix: Some(projection),
            rain_strength: Some(0.25),
            world_time: Some(1234),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();

        assert_eq!(144, bytes.len());
        assert_eq!(2.0_f32.to_le_bytes(), bytes[0..4]);
        assert_eq!(3.0_f32.to_le_bytes(), bytes[20..24]);
        assert_eq!(5.0_f32.to_le_bytes(), bytes[64..68]);
        assert_eq!(6.0_f32.to_le_bytes(), bytes[84..88]);
        assert_eq!(0.25_f32.to_le_bytes(), bytes[128..132]);
        assert_eq!(1234_i32.to_le_bytes(), bytes[132..136]);
    }

    #[test]
    fn packs_rust_owned_material_atlas_extent_as_ivec2() {
        let requirements = source_requirements(
            "uniform ivec2 atlasSize;",
            "ivec2 source_atlas_size = atlasSize;",
            "",
            "",
        )
        .unwrap();
        assert_eq!(
            Some(TerrainSourceUniformSemantic::MaterialAtlasSize),
            requirements.fields()[0].semantic
        );
        let bytes = TerrainSourceUniformFrame {
            material_atlas_size: Some([1024, 512]),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();
        let atlas = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::MaterialAtlasSize))
            .unwrap()
            .field
            .offset() as usize;
        assert_eq!(1024_i32.to_le_bytes(), bytes[atlas..atlas + 4]);
        assert_eq!(512_i32.to_le_bytes(), bytes[atlas + 4..atlas + 8]);
        let required_transforms = TerrainSourceUniformFrame {
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        };
        assert!(
            required_transforms
                .clone()
                .pack_std140(&requirements)
                .unwrap_err()
                .to_string()
                .contains("material atlas size")
        );
        assert!(
            TerrainSourceUniformFrame {
                material_atlas_size: Some([0, 512]),
                ..required_transforms
            }
            .pack_std140(&requirements)
            .is_err()
        );
    }

    #[test]
    fn packs_vanilla_night_vision_as_a_named_source_semantic() {
        let requirements = source_requirements(
            "uniform float nightVision;",
            "float source_night_vision = nightVision;",
            "",
            "",
        )
        .unwrap();
        let night_vision_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::NightVision))
            .expect("nightVision must retain its named semantic")
            .field
            .offset() as usize;
        let bytes = TerrainSourceUniformFrame {
            night_vision: Some(0.875),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();
        assert_eq!(
            0.875_f32.to_le_bytes(),
            bytes[night_vision_offset..night_vision_offset + 4]
        );
        assert!(
            TerrainSourceUniformFrame {
                view_matrix: Some([
                    1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
                ]),
                projection_matrix: Some([
                    1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
                ]),
                ..TerrainSourceUniformFrame::default()
            }
            .pack_std140(&requirements)
            .unwrap_err()
            .to_string()
            .contains("night vision")
        );
    }

    #[test]
    fn packs_rust_owned_temporal_rain_factor_as_a_named_source_semantic() {
        let requirements = source_requirements(
            "uniform float rainFactor;",
            "float source_rain_factor = rainFactor;",
            "",
            "",
        )
        .unwrap();
        let rain_factor_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::RainFactor))
            .expect("rainFactor must retain its named semantic")
            .field
            .offset() as usize;
        let bytes = TerrainSourceUniformFrame {
            rain_factor: Some(0.625),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();
        assert_eq!(
            0.625_f32.to_le_bytes(),
            bytes[rain_factor_offset..rain_factor_offset + 4]
        );
        assert!(
            TerrainSourceUniformFrame {
                view_matrix: Some([
                    1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
                ]),
                projection_matrix: Some([
                    1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
                ]),
                ..TerrainSourceUniformFrame::default()
            }
            .pack_std140(&requirements)
            .unwrap_err()
            .to_string()
            .contains("rain factor")
        );
    }

    #[test]
    fn packs_vanilla_fog_color_as_a_named_source_semantic() {
        let requirements = source_requirements(
            "uniform vec3 fogColor;",
            "vec3 source_fog_color = fogColor;",
            "",
            "",
        )
        .unwrap();
        let fog_color_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::FogColor))
            .expect("fogColor must retain its named semantic")
            .field
            .offset() as usize;
        let bytes = TerrainSourceUniformFrame {
            fog_color: Some([0.25, 0.5, 0.75]),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();
        assert_eq!(
            0.25_f32.to_le_bytes(),
            bytes[fog_color_offset..fog_color_offset + 4]
        );
        assert_eq!(
            0.5_f32.to_le_bytes(),
            bytes[fog_color_offset + 4..fog_color_offset + 8]
        );
        assert_eq!(
            0.75_f32.to_le_bytes(),
            bytes[fog_color_offset + 8..fog_color_offset + 12]
        );
    }

    #[test]
    fn packs_source_smoothed_biome_climate_as_named_semantics() {
        let requirements = source_requirements(
            "uniform float inDry;\nuniform float inSnowy;",
            "float climate = inDry + inSnowy;",
            "",
            "",
        )
        .unwrap();
        assert!(requirements.is_fully_semantic());
        let dry_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::BiomeDry))
            .expect("inDry must retain its named semantic")
            .field
            .offset() as usize;
        let snowy_offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::BiomeSnowy))
            .expect("inSnowy must retain its named semantic")
            .field
            .offset() as usize;
        let bytes = TerrainSourceUniformFrame {
            biome_dry: Some(0.25),
            biome_snowy: Some(0.75),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();
        assert_eq!(0.25_f32.to_le_bytes(), bytes[dry_offset..dry_offset + 4]);
        assert_eq!(
            0.75_f32.to_le_bytes(),
            bytes[snowy_offset..snowy_offset + 4]
        );
    }

    #[test]
    fn packs_source_smoothed_nether_biomes_as_named_semantics() {
        let requirements = source_requirements(
			"uniform float inNetherWastes;\nuniform float inCrimsonForest;\nuniform float inWarpedForest;\nuniform float inBasaltDeltas;\nuniform float inSoulValley;",
			"float nether = inNetherWastes + inCrimsonForest + inWarpedForest + inBasaltDeltas + inSoulValley;",
			"",
			"",
		)
		.unwrap();
        assert!(requirements.is_fully_semantic());
        let bytes = TerrainSourceUniformFrame {
            biome_nether_wastes: Some(0.1),
            biome_crimson_forest: Some(0.2),
            biome_warped_forest: Some(0.3),
            biome_basalt_deltas: Some(0.4),
            biome_soul_valley: Some(0.5),
            view_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            projection_matrix: Some([
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ]),
            ..TerrainSourceUniformFrame::default()
        }
        .pack_std140(&requirements)
        .unwrap();
        for (semantic, expected) in [
            (TerrainSourceUniformSemantic::BiomeNetherWastes, 0.1_f32),
            (TerrainSourceUniformSemantic::BiomeCrimsonForest, 0.2_f32),
            (TerrainSourceUniformSemantic::BiomeWarpedForest, 0.3_f32),
            (TerrainSourceUniformSemantic::BiomeBasaltDeltas, 0.4_f32),
            (TerrainSourceUniformSemantic::BiomeSoulValley, 0.5_f32),
        ] {
            let offset = requirements
                .fields()
                .iter()
                .find(|field| field.semantic == Some(semantic))
                .expect("named Nether semantic must retain its source field")
                .field
                .offset() as usize;
            assert_eq!(expected.to_le_bytes(), bytes[offset..offset + 4]);
        }
    }

    #[test]
    fn packing_rejects_missing_or_non_finite_semantic_values() {
        let requirements = source_requirements(
            "",
            "",
            "uniform float rainStrength;",
            "float source_rain_strength = rainStrength;",
        )
        .unwrap();
        let transforms = TerrainSourceUniformFrame {
            view_matrix: Some([1.0; 16]),
            projection_matrix: Some([1.0; 16]),
            ..TerrainSourceUniformFrame::default()
        };
        assert!(
            transforms
                .pack_std140(&requirements)
                .unwrap_err()
                .to_string()
                .contains("rain strength")
        );
        assert!(
            TerrainSourceUniformFrame {
                view_matrix: Some([1.0; 16]),
                projection_matrix: Some([1.0; 16]),
                rain_strength: Some(f32::NAN),
                ..TerrainSourceUniformFrame::default()
            }
            .pack_std140(&requirements)
            .is_err()
        );
    }
}
