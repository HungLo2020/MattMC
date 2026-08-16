//! Pack-declared semantic bindings for lowered terrain source resources.
//!
//! A shader source name is not a backend binding. This module converts an
//! explicitly transported pack declaration into stable semantic roles; it
//! intentionally contains neither native handles nor attachment indices.

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};

use super::source::ShaderPackSource;
use super::terrain_contract::{TerrainPassContract, TerrainPassInput, TerrainPassRequiredResource};

pub const TERRAIN_RESOURCE_BINDINGS_PATH: &str = "mattmc/terrain-resource-bindings.properties";

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainSourceResourceRole {
    /// A draw-local material texture resolved from a Rust-owned semantic
    /// asset. It remains distinct from the Minecraft block atlas even when a
    /// legacy source program spells both samplers `tex`.
    MaterialTexture,
    MaterialAtlas,
    MaterialNormalMap,
    MaterialSpecularMap,
    Lightmap,
    /// First compare-sampled shadow depth input in a source terrain pass.
    ShadowDepthPrimary,
    /// Second compare-sampled shadow depth input. Shader packs may use a
    /// distinct projection/filter history, so it cannot alias the primary
    /// role merely because both have the same sampler type.
    ShadowDepthSecondary,
    /// Raw shadow-depth data used by source paths that explicitly reconstruct
    /// or combine shadow depth rather than issuing a compare sample.
    ShadowDepthRaw,
    ShadowColor,
    /// A distinct shadow color target sampled by source stages that retain
    /// multiple shadow attachments. It cannot alias the primary merely
    /// because both are ordinary 2D color textures.
    ShadowColorSecondary,
    Noise,
    GBufferAlbedo,
    GBufferNormal,
    GBufferMaterialLight,
    GBufferWorldPosition,
    MainDepth,
    /// Main-scene depth copied before translucent work. Source packs use this
    /// as a semantic snapshot, not as an interchangeable live depth view.
    MainDepthBeforeTranslucency,
    /// Earlier main-scene depth history retained by packs that declare a
    /// second snapshot. It stays separate from the current and pre-
    /// translucent depth streams.
    MainDepthPrevious,
    /// A named shader-pack color resource. The semantic name is supplied by
    /// the pack manifest (for example `primary` or `material_auxiliary`),
    /// never an OpenGL attachment index or a backend image handle.
    ShaderPackColor(String),
    /// The depth written by the source-derived opaque Distant Horizons pass.
    /// It is raw sampled depth, not a shadow map and not interchangeable with
    /// the near-terrain depth attachment.
    DistantHorizonsOpaqueDepth,
    /// The Distant Horizons depth snapshot taken before translucent work. A
    /// selected source may consume both variants, so they remain distinct
    /// semantic resources even when a particular frame gives them equal data.
    DistantHorizonsDepthBeforeTranslucency,
    ColoredVoxelOccupancy,
    ColoredVoxelLightCurrent,
    ColoredVoxelLightPrevious,
    /// Rain-puddle occupancy written by the source-derived shadow voxelizer.
    /// It is an owned unsigned 2D storage image, never an Iris custom image.
    PuddleOccupancy,
    /// A shader-pack-owned 2D image declared by a normalized pack-relative
    /// asset path. The path is semantic pack data, not a texture unit or
    /// backend handle, and lets several selected-source custom images coexist.
    PackTexture(String),
}

impl TerrainSourceResourceRole {
    fn parse(value: &str) -> GalResult<Self> {
        if let Some(path) = value.strip_prefix("pack_texture:") {
            return Ok(Self::PackTexture(normalized_pack_texture_path(path)?));
        }
        if let Some(name) = value.strip_prefix("shader_pack_color:") {
            return Ok(Self::ShaderPackColor(normalized_shader_pack_color_name(
                name,
            )?));
        }
        match value {
            "material_texture" => Ok(Self::MaterialTexture),
            "material_atlas" => Ok(Self::MaterialAtlas),
            "material_normal_map" => Ok(Self::MaterialNormalMap),
            "material_specular_map" => Ok(Self::MaterialSpecularMap),
            "lightmap" => Ok(Self::Lightmap),
            // Keep the early diagnostic spelling readable while mapping it to
            // the unambiguous semantic role used by new source declarations.
            "shadow_depth" | "shadow_depth_compare" | "shadow_depth_primary" => {
                Ok(Self::ShadowDepthPrimary)
            }
            "shadow_depth_secondary" => Ok(Self::ShadowDepthSecondary),
            "shadow_depth_raw" => Ok(Self::ShadowDepthRaw),
            "shadow_color" => Ok(Self::ShadowColor),
            "shadow_color_secondary" => Ok(Self::ShadowColorSecondary),
            "noise" => Ok(Self::Noise),
            "g_buffer_albedo" => Ok(Self::GBufferAlbedo),
            "g_buffer_normal" => Ok(Self::GBufferNormal),
            "g_buffer_material_light" => Ok(Self::GBufferMaterialLight),
            "g_buffer_world_position" => Ok(Self::GBufferWorldPosition),
            "main_depth" => Ok(Self::MainDepth),
            "main_depth_before_translucency" => Ok(Self::MainDepthBeforeTranslucency),
            "main_depth_previous" => Ok(Self::MainDepthPrevious),
            "distant_horizons_opaque_depth" => Ok(Self::DistantHorizonsOpaqueDepth),
            "distant_horizons_depth_before_translucency" => {
                Ok(Self::DistantHorizonsDepthBeforeTranslucency)
            }
            "colored_voxel_occupancy" => Ok(Self::ColoredVoxelOccupancy),
            "colored_voxel_light_current" => Ok(Self::ColoredVoxelLightCurrent),
            "colored_voxel_light_previous" => Ok(Self::ColoredVoxelLightPrevious),
            "puddle_occupancy" => Ok(Self::PuddleOccupancy),
            _ => Err(GalError::unsupported_feature(format!(
                "unknown semantic terrain source resource role '{value}'"
            ))),
        }
    }

    pub fn expected_sampler_type(&self) -> &'static str {
        match self {
            Self::MaterialTexture
            | Self::MaterialAtlas
            | Self::MaterialNormalMap
            | Self::MaterialSpecularMap
            | Self::Lightmap
            | Self::Noise
            | Self::ShadowDepthRaw
            | Self::ShadowColor
            | Self::ShadowColorSecondary
            | Self::GBufferAlbedo
            | Self::GBufferNormal
            | Self::GBufferMaterialLight
            | Self::GBufferWorldPosition
            | Self::MainDepth
            | Self::MainDepthBeforeTranslucency
            | Self::MainDepthPrevious
            | Self::ShaderPackColor(_)
            | Self::DistantHorizonsOpaqueDepth
            | Self::DistantHorizonsDepthBeforeTranslucency => "sampler2D",
            Self::ShadowDepthPrimary | Self::ShadowDepthSecondary => "sampler2DShadow",
            Self::ColoredVoxelOccupancy => "usampler3D",
            Self::ColoredVoxelLightCurrent | Self::ColoredVoxelLightPrevious => "sampler3D",
            Self::PuddleOccupancy => "usampler2D",
            Self::PackTexture(_) => "sampler2D",
        }
    }

    /// Resolves a pack-declared physical shadow-depth resource to the exact
    /// source-stage sampling contract. `shadow_depth` identifies the owned
    /// depth image, while each source declaration decides whether it performs
    /// comparison sampling or reads raw depth. This keeps that distinction in
    /// semantic source lowering instead of tying it to an Iris texture unit.
    pub fn resolve_sampled_declaration(&self, type_name: &str) -> GalResult<Self> {
        match (self, type_name) {
            (Self::ShadowDepthPrimary | Self::ShadowDepthSecondary, "sampler2D") => {
                Ok(Self::ShadowDepthRaw)
            }
            _ if self.expected_sampler_type() == type_name => Ok(self.clone()),
            _ => Err(GalError::invalid_argument(format!(
                "semantic terrain source resource '{}' requires '{}' but source declares '{type_name}'",
                self.semantic_name(),
                self.expected_sampler_type(),
            ))),
        }
    }

    /// Returns the exact GLSL image declaration accepted for a source storage
    /// binding. Most semantic roles are sample-only; storage access is an
    /// explicit source contract rather than an implicit sampler conversion.
    pub fn expected_storage_image_type(&self) -> Option<&'static str> {
        match self {
            Self::ColoredVoxelOccupancy => Some("uimage3D"),
            Self::PuddleOccupancy => Some("uimage2D"),
            Self::MaterialTexture
            | Self::MaterialAtlas
            | Self::MaterialNormalMap
            | Self::MaterialSpecularMap
            | Self::Lightmap
            | Self::ShadowDepthPrimary
            | Self::ShadowDepthSecondary
            | Self::ShadowDepthRaw
            | Self::ShadowColor
            | Self::ShadowColorSecondary
            | Self::Noise
            | Self::GBufferAlbedo
            | Self::GBufferNormal
            | Self::GBufferMaterialLight
            | Self::GBufferWorldPosition
            | Self::MainDepth
            | Self::MainDepthBeforeTranslucency
            | Self::MainDepthPrevious
            | Self::ShaderPackColor(_)
            | Self::DistantHorizonsOpaqueDepth
            | Self::DistantHorizonsDepthBeforeTranslucency
            | Self::ColoredVoxelLightCurrent
            | Self::ColoredVoxelLightPrevious
            | Self::PackTexture(_) => None,
        }
    }

    pub fn semantic_name(&self) -> &str {
        match self {
            Self::MaterialTexture => "material_texture",
            Self::MaterialAtlas => "material_atlas",
            Self::MaterialNormalMap => "material_normal_map",
            Self::MaterialSpecularMap => "material_specular_map",
            Self::Lightmap => "lightmap",
            Self::ShadowDepthPrimary => "shadow_depth_primary",
            Self::ShadowDepthSecondary => "shadow_depth_secondary",
            Self::ShadowDepthRaw => "shadow_depth_raw",
            Self::ShadowColor => "shadow_color",
            Self::ShadowColorSecondary => "shadow_color_secondary",
            Self::Noise => "noise",
            Self::GBufferAlbedo => "g_buffer_albedo",
            Self::GBufferNormal => "g_buffer_normal",
            Self::GBufferMaterialLight => "g_buffer_material_light",
            Self::GBufferWorldPosition => "g_buffer_world_position",
            Self::MainDepth => "main_depth",
            Self::MainDepthBeforeTranslucency => "main_depth_before_translucency",
            Self::MainDepthPrevious => "main_depth_previous",
            Self::ShaderPackColor(_) => "shader_pack_color",
            Self::DistantHorizonsOpaqueDepth => "distant_horizons_opaque_depth",
            Self::DistantHorizonsDepthBeforeTranslucency => {
                "distant_horizons_depth_before_translucency"
            }
            Self::ColoredVoxelOccupancy => "colored_voxel_occupancy",
            Self::ColoredVoxelLightCurrent => "colored_voxel_light_current",
            Self::ColoredVoxelLightPrevious => "colored_voxel_light_previous",
            Self::PuddleOccupancy => "puddle_occupancy",
            Self::PackTexture(_) => "pack_texture",
        }
    }

    /// Stable diagnostic spelling for a concrete semantic resource. Unlike
    /// [`Self::semantic_name`], this retains a named shader-pack color or
    /// pack-texture identity so feedback requirements can be correlated
    /// without exposing an attachment number or native resource.
    pub fn diagnostic_name(&self) -> String {
        match self {
            Self::ShaderPackColor(name) => format!("shader_pack_color:{name}"),
            Self::PackTexture(path) => format!("pack_texture:{path}"),
            _ => self.semantic_name().to_string(),
        }
    }

    pub fn pack_texture_path(&self) -> Option<&str> {
        match self {
            Self::PackTexture(path) => Some(path),
            _ => None,
        }
    }

    pub fn shader_pack_color_name(&self) -> Option<&str> {
        match self {
            Self::ShaderPackColor(name) => Some(name),
            _ => None,
        }
    }
}

/// Backend-neutral sampled-resource shape expected by one semantic terrain
/// source role. This is deliberately narrower than a texture description:
/// GAL owns format/usage validation and backends own native view creation.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainSourceSampledResourceShape {
    Texture2d,
    /// An integer-valued 2D texture. This stays distinct from ordinary color
    /// textures so a source `usampler2D` cannot accidentally bind normalized
    /// material data.
    UnsignedTexture2d,
    DepthCompareTexture2d,
    UnsignedTexture3d,
    FloatTexture3d,
}

impl TerrainSourceResourceRole {
    pub fn expected_sampled_resource_shape(&self) -> TerrainSourceSampledResourceShape {
        match self {
            Self::ShadowDepthPrimary | Self::ShadowDepthSecondary => {
                TerrainSourceSampledResourceShape::DepthCompareTexture2d
            }
            Self::ColoredVoxelOccupancy => TerrainSourceSampledResourceShape::UnsignedTexture3d,
            Self::PuddleOccupancy => TerrainSourceSampledResourceShape::UnsignedTexture2d,
            Self::ColoredVoxelLightCurrent | Self::ColoredVoxelLightPrevious => {
                TerrainSourceSampledResourceShape::FloatTexture3d
            }
            Self::MaterialTexture
            | Self::MaterialAtlas
            | Self::MaterialNormalMap
            | Self::MaterialSpecularMap
            | Self::Lightmap
            | Self::ShadowDepthRaw
            | Self::ShadowColor
            | Self::ShadowColorSecondary
            | Self::Noise
            | Self::GBufferAlbedo
            | Self::GBufferNormal
            | Self::GBufferMaterialLight
            | Self::GBufferWorldPosition
            | Self::MainDepth
            | Self::MainDepthBeforeTranslucency
            | Self::MainDepthPrevious
            | Self::ShaderPackColor(_)
            | Self::DistantHorizonsOpaqueDepth
            | Self::DistantHorizonsDepthBeforeTranslucency
            | Self::PackTexture(_) => TerrainSourceSampledResourceShape::Texture2d,
        }
    }
}

fn normalized_pack_texture_path(path: &str) -> GalResult<String> {
    let path = path.trim();
    if path.is_empty()
        || path.starts_with('/')
        || path
            .split('/')
            .any(|component| component.is_empty() || component == "." || component == "..")
    {
        return Err(GalError::invalid_argument(
            "shader-pack texture role requires a normalized relative asset path",
        ));
    }
    Ok(path.to_string())
}

fn normalized_shader_pack_color_name(name: &str) -> GalResult<String> {
    let name = name.trim();
    let legacy_attachment_name = name.strip_prefix("colortex").is_some_and(|suffix| {
        !suffix.is_empty() && suffix.bytes().all(|byte| byte.is_ascii_digit())
    });
    if name.is_empty()
        || legacy_attachment_name
        || !name
            .bytes()
            .all(|byte| byte == b'_' || byte.is_ascii_lowercase() || byte.is_ascii_digit())
        || !matches!(name.as_bytes().first(), Some(byte) if *byte == b'_' || byte.is_ascii_lowercase())
    {
        return Err(GalError::invalid_argument(
            "shader-pack color role requires a lowercase semantic identifier",
        ));
    }
    Ok(name.to_string())
}

/// One Rust-owned resource made available to a selected source program. The
/// identity is semantic and generation-bound; native handles live only in the
/// shader runtime and are attached after this contract is validated.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceResourceAvailability {
    pub role: TerrainSourceResourceRole,
    pub shape: TerrainSourceSampledResourceShape,
    pub resource_generation: u64,
}

/// Closed source-resource availability record for one shader-pack/world
/// generation. It cannot carry Java renderer objects, GL texture units, or
/// Vulkan descriptors.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceResourceAvailabilitySet {
    shader_pack_generation: u64,
    world_generation: u64,
    resources: BTreeMap<TerrainSourceResourceRole, TerrainSourceResourceAvailability>,
}

impl TerrainSourceResourceAvailabilitySet {
    pub fn new(
        shader_pack_generation: u64,
        world_generation: u64,
        resources: impl IntoIterator<Item = TerrainSourceResourceAvailability>,
    ) -> GalResult<Self> {
        let mut by_role = BTreeMap::new();
        for resource in resources {
            if resource.resource_generation == 0 {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource '{}' has no owned generation",
                    resource.role.semantic_name()
                )));
            }
            let expected = resource.role.expected_sampled_resource_shape();
            if resource.shape != expected {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource '{}' has shape {:?}, but its semantic role requires {:?}",
                    resource.role.semantic_name(),
                    resource.shape,
                    expected
                )));
            }
            let role = resource.role.clone();
            if by_role.insert(role.clone(), resource).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource role '{}' is available more than once",
                    role.semantic_name()
                )));
            }
        }
        Ok(Self {
            shader_pack_generation,
            world_generation,
            resources: by_role,
        })
    }

    pub fn shader_pack_generation(&self) -> u64 {
        self.shader_pack_generation
    }

    pub fn world_generation(&self) -> u64 {
        self.world_generation
    }

    pub fn resource_for(
        &self,
        role: TerrainSourceResourceRole,
    ) -> Option<TerrainSourceResourceAvailability> {
        self.resources.get(&role).cloned()
    }

    pub fn resources(&self) -> impl Iterator<Item = TerrainSourceResourceAvailability> + '_ {
        self.resources.values().cloned()
    }
}

/// One Rust-owned combined sampler prepared for a semantic source role. The
/// handle is a validated GAL identity, never a Java/GL/Vulkan handle.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOwnedResource {
    pub role: TerrainSourceResourceRole,
    pub combined_sampler: Handle,
}

/// One Rust-owned writable texture view prepared for a semantic source role.
/// This remains a validated GAL view handle, never a Java/GL/Vulkan handle.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOwnedStorageResource {
    pub role: TerrainSourceResourceRole,
    pub texture_view: Handle,
}

/// Generation-coherent owned sampler table. It is deliberately separate from
/// source parsing and from backend lowering: a later runtime can create a GAL
/// resource set from this table only after it has owned every referenced
/// texture, view, sampler, and lifetime.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOwnedResourceSet {
    availability: TerrainSourceResourceAvailabilitySet,
    samplers: BTreeMap<TerrainSourceResourceRole, Handle>,
    storage_views: BTreeMap<TerrainSourceResourceRole, Handle>,
}

impl TerrainSourceOwnedResourceSet {
    pub fn new(
        availability: TerrainSourceResourceAvailabilitySet,
        resources: impl IntoIterator<Item = TerrainSourceOwnedResource>,
    ) -> GalResult<Self> {
        Self::with_storage_resources(availability, resources, [])
    }

    /// Builds one generation-coherent owned source resource table. Samplers
    /// and writable views are kept in distinct maps so a source image can
    /// never be silently downgraded into a sampled binding.
    pub fn with_storage_resources(
        availability: TerrainSourceResourceAvailabilitySet,
        resources: impl IntoIterator<Item = TerrainSourceOwnedResource>,
        storage_resources: impl IntoIterator<Item = TerrainSourceOwnedStorageResource>,
    ) -> GalResult<Self> {
        let mut samplers = BTreeMap::new();
        for resource in resources {
            if availability.resource_for(resource.role.clone()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "terrain source sampler role '{}' is not available for this generation",
                    resource.role.semantic_name()
                )));
            }
            if resource.combined_sampler.kind() != Some(HandleKind::CombinedTextureSampler) {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource '{}' is not a combined texture sampler",
                    resource.role.semantic_name()
                )));
            }
            let role = resource.role.clone();
            if samplers
                .insert(role.clone(), resource.combined_sampler)
                .is_some()
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain source sampler role '{}' is owned more than once",
                    role.semantic_name()
                )));
            }
        }
        let mut storage_views = BTreeMap::new();
        for resource in storage_resources {
            if availability.resource_for(resource.role.clone()).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "terrain source storage role '{}' is not available for this generation",
                    resource.role.semantic_name()
                )));
            }
            if resource.texture_view.kind() != Some(HandleKind::TextureView) {
                return Err(GalError::invalid_argument(format!(
                    "terrain source storage resource '{}' is not a texture view",
                    resource.role.semantic_name()
                )));
            }
            let role = resource.role.clone();
            if storage_views
                .insert(role.clone(), resource.texture_view)
                .is_some()
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain source storage role '{}' is owned more than once",
                    role.semantic_name()
                )));
            }
        }
        for available in availability.resources() {
            if !samplers.contains_key(&available.role)
                && !storage_views.contains_key(&available.role)
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource '{}' has no owned sampler or storage view",
                    available.role.semantic_name()
                )));
            }
        }
        Ok(Self {
            availability,
            samplers,
            storage_views,
        })
    }

    pub fn availability(&self) -> &TerrainSourceResourceAvailabilitySet {
        &self.availability
    }

    pub fn combined_sampler_for(&self, role: TerrainSourceResourceRole) -> Option<Handle> {
        self.samplers.get(&role).copied()
    }

    pub fn storage_texture_for(&self, role: TerrainSourceResourceRole) -> Option<Handle> {
        self.storage_views.get(&role).copied()
    }

    pub fn len(&self) -> usize {
        self.samplers.len() + self.storage_views.len()
    }

    /// Stable semantic cache identity for one owned source-resource table.
    /// It intentionally contains only role and generation facts, never a GAL
    /// handle, descriptor, texture unit, or backend-native resource identity.
    /// A frontend may use this to retain a compatible source resource set
    /// across frames while making pack/world replacement invalidate it.
    pub fn generation_signature(&self) -> Vec<(TerrainSourceResourceRole, u64)> {
        self.availability
            .resources()
            .map(|resource| (resource.role, resource.resource_generation))
            .collect()
    }

    /// Rebinds one already-declared sampled role for a distinct semantic
    /// material draw. The caller supplies a stable resource generation rather
    /// than a GAL/native identity, so frontend cache keys remain meaningful
    /// across backend implementations while still invalidating when the
    /// material asset changes.
    pub fn with_combined_sampler_override(
        &self,
        role: TerrainSourceResourceRole,
        combined_sampler: Handle,
        resource_generation: u64,
    ) -> GalResult<Self> {
        if combined_sampler.kind() != Some(HandleKind::CombinedTextureSampler) {
            return Err(GalError::invalid_argument(format!(
                "terrain source override for '{}' is not a combined texture sampler",
                role.semantic_name()
            )));
        }
        if resource_generation == 0 {
            return Err(GalError::invalid_argument(format!(
                "terrain source override for '{}' has zero resource generation",
                role.semantic_name()
            )));
        }
        let Some(existing) = self.availability.resource_for(role.clone()) else {
            return Err(GalError::invalid_argument(format!(
                "terrain source override references unavailable role '{}'",
                role.semantic_name()
            )));
        };
        if self.storage_views.contains_key(&role) || !self.samplers.contains_key(&role) {
            return Err(GalError::invalid_argument(format!(
                "terrain source override requires sampled role '{}'",
                role.semantic_name()
            )));
        }
        let availability = self
            .availability
            .resources()
            .map(|available| TerrainSourceResourceAvailability {
                role: available.role.clone(),
                shape: available.shape,
                resource_generation: if available.role == role {
                    resource_generation
                } else {
                    available.resource_generation
                },
            })
            .collect::<Vec<_>>();
        debug_assert!(existing.resource_generation > 0);
        let resources = self
            .samplers
            .iter()
            .map(|(available_role, sampler)| TerrainSourceOwnedResource {
                role: available_role.clone(),
                combined_sampler: if *available_role == role {
                    combined_sampler
                } else {
                    *sampler
                },
            })
            .collect::<Vec<_>>();
        let storage_resources = self
            .storage_views
            .iter()
            .map(
                |(available_role, texture_view)| TerrainSourceOwnedStorageResource {
                    role: available_role.clone(),
                    texture_view: *texture_view,
                },
            )
            .collect::<Vec<_>>();
        Self::with_storage_resources(
            TerrainSourceResourceAvailabilitySet::new(
                self.availability.shader_pack_generation(),
                self.availability.world_generation(),
                availability,
            )?,
            resources,
            storage_resources,
        )
    }

    /// Returns the semantic subset whose roles are not already owned by an
    /// earlier stage in the same source-frame transaction. This is not a
    /// conflict-resolution policy: callers must explicitly name the earlier
    /// roles, and `merge` still rejects every accidental duplicate.
    pub fn excluding_roles(
        &self,
        exclusions: impl IntoIterator<Item = TerrainSourceResourceRole>,
    ) -> GalResult<Self> {
        let exclusions = exclusions.into_iter().collect::<BTreeSet<_>>();
        let availability = self
            .availability
            .resources()
            .filter(|resource| !exclusions.contains(&resource.role))
            .collect::<Vec<_>>();
        let resources = self
            .samplers
            .iter()
            .filter(|(role, _)| !exclusions.contains(*role))
            .map(|(role, &combined_sampler)| TerrainSourceOwnedResource {
                role: role.clone(),
                combined_sampler,
            })
            .collect::<Vec<_>>();
        let storage_resources = self
            .storage_views
            .iter()
            .filter(|(role, _)| !exclusions.contains(*role))
            .map(|(role, &texture_view)| TerrainSourceOwnedStorageResource {
                role: role.clone(),
                texture_view,
            })
            .collect::<Vec<_>>();
        Self::with_storage_resources(
            TerrainSourceResourceAvailabilitySet::new(
                self.availability.shader_pack_generation(),
                self.availability.world_generation(),
                availability,
            )?,
            resources,
            storage_resources,
        )
    }

    /// Removes roles already supplied by an earlier stage of the same source
    /// transaction, but only when their semantic generation and owned GAL
    /// bindings are exactly equal. This lets a confirmed snapshot be carried
    /// into a later plan without treating a different current/stale resource
    /// as a harmless duplicate.
    pub fn excluding_roles_already_owned_by(
        &self,
        existing: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<Self> {
        if self.availability.shader_pack_generation()
            != existing.availability.shader_pack_generation()
            || self.availability.world_generation() != existing.availability.world_generation()
        {
            return Err(GalError::invalid_argument(
                "cannot compare terrain source resources from different shader-pack or world generations",
            ));
        }
        let mut exclusions = BTreeSet::new();
        for resource in self.availability.resources() {
            let Some(previous) = existing.availability.resource_for(resource.role.clone()) else {
                continue;
            };
            if previous != resource
                || existing.combined_sampler_for(resource.role.clone())
                    != self.combined_sampler_for(resource.role.clone())
                || existing.storage_texture_for(resource.role.clone())
                    != self.storage_texture_for(resource.role.clone())
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource role '{}' conflicts with an earlier source-stage binding",
                    resource.role.diagnostic_name(),
                )));
            }
            exclusions.insert(resource.role);
        }
        self.excluding_roles(exclusions)
    }

    /// Merges independently prepared semantic resource subsets for one exact
    /// shader-pack/world generation. This keeps pack-owned PNGs, Minecraft
    /// material assets, runtime attachments, and volume resources separate at
    /// creation time while making duplicate or mixed-generation roles fail
    /// before a source program can receive a GAL resource set.
    pub fn merge<'a>(
        sets: impl IntoIterator<Item = &'a TerrainSourceOwnedResourceSet>,
    ) -> GalResult<Self> {
        let mut expected_generation = None;
        let mut expected_world_generation = None;
        let mut availability = Vec::new();
        let mut resources = Vec::new();
        let mut storage_resources = Vec::new();
        for set in sets {
            let source_generation = set.availability.shader_pack_generation();
            let world_generation = set.availability.world_generation();
            if let Some(expected) = expected_generation {
                if source_generation != expected {
                    return Err(GalError::invalid_argument(format!(
                        "cannot merge terrain source resources from shader-pack generations {expected} and {source_generation}"
                    )));
                }
            } else {
                expected_generation = Some(source_generation);
            }
            if let Some(expected) = expected_world_generation {
                if world_generation != expected {
                    return Err(GalError::invalid_argument(format!(
                        "cannot merge terrain source resources from world generations {expected} and {world_generation}"
                    )));
                }
            } else {
                expected_world_generation = Some(world_generation);
            }
            availability.extend(set.availability.resources());
            resources.extend(set.samplers.iter().map(|(role, combined_sampler)| {
                TerrainSourceOwnedResource {
                    role: role.clone(),
                    combined_sampler: *combined_sampler,
                }
            }));
            storage_resources.extend(set.storage_views.iter().map(|(role, texture_view)| {
                TerrainSourceOwnedStorageResource {
                    role: role.clone(),
                    texture_view: *texture_view,
                }
            }));
        }
        let shader_pack_generation = expected_generation.ok_or_else(|| {
            GalError::invalid_argument("cannot merge an empty terrain source resource set")
        })?;
        let world_generation =
            expected_world_generation.expect("set with shader generation has world generation");
        Self::with_storage_resources(
            TerrainSourceResourceAvailabilitySet::new(
                shader_pack_generation,
                world_generation,
                availability,
            )?,
            resources,
            storage_resources,
        )
    }
}

/// One fully owned source-generation declaration. It is optional while source
/// discovery is diagnostic-only, but any executable source route must bind all
/// its opaque resources through this table.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct TerrainSourceResourceBindings {
    bindings: BTreeMap<String, TerrainSourceResourceRole>,
}

impl TerrainSourceResourceBindings {
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let Some(contents) = source.get(TERRAIN_RESOURCE_BINDINGS_PATH) else {
            return Ok(Self::default());
        };
        let mut bindings = BTreeMap::new();
        for (index, raw_line) in contents.lines().enumerate() {
            let line = raw_line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let Some((name, role)) = line.split_once('=') else {
                return Err(GalError::invalid_argument(format!(
                    "terrain resource binding line {} is missing '='",
                    index + 1
                )));
            };
            let name = name.trim();
            if !valid_identifier(name) {
                return Err(GalError::invalid_argument(format!(
                    "terrain resource binding '{}' is not a shader identifier",
                    name
                )));
            }
            let role = TerrainSourceResourceRole::parse(role.trim())?;
            // Multiple pack source identifiers may be aliases for one
            // Rust-owned resource (for example legacy `gaux*` and
            // `colortex*` sampler names). The resource table remains unique
            // by semantic role; this declaration table maps every source
            // identifier independently and still rejects duplicate names.
            if bindings.insert(name.to_string(), role).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "terrain resource binding '{}' is declared more than once",
                    name
                )));
            }
        }
        Ok(Self { bindings })
    }

    pub fn role_for(&self, name: &str) -> Option<TerrainSourceResourceRole> {
        self.bindings.get(name).cloned()
    }

    /// Resolves one legacy shader-pack color output slot to its declared
    /// semantic color role. The slot is source syntax only; callers retain the
    /// returned named role and must never expose the numeric slot as a GAL or
    /// frontend attachment identity.
    pub fn shader_pack_color_output_for_slot(
        &self,
        slot: u32,
    ) -> GalResult<TerrainSourceResourceRole> {
        let name = format!("colortex{slot}");
        match self.role_for(&name) {
            Some(role @ TerrainSourceResourceRole::ShaderPackColor(_)) => Ok(role),
            Some(role) => Err(GalError::invalid_argument(format!(
                "shader-pack color output slot {slot} is declared as non-color role '{}'",
                role.semantic_name()
            ))),
            None => Err(GalError::unsupported_feature(format!(
                "shader-pack source output slot {slot} has no declared semantic color role"
            ))),
        }
    }

    pub fn names(&self) -> impl Iterator<Item = &str> {
        self.bindings.keys().map(String::as_str)
    }

    /// Checks the resource roles required by the selected source contract,
    /// separately from lowerer validation that every declared sampler is
    /// actually consumed. This keeps source semantics explicit without
    /// manufacturing a native descriptor layout during discovery.
    pub fn require_contract_roles(&self, contract: &TerrainPassContract) -> GalResult<()> {
        let mut required = BTreeSet::new();
        if contract.inputs.contains(&TerrainPassInput::AtlasColor) {
            required.insert(TerrainSourceResourceRole::MaterialAtlas);
        }
        if contract.inputs.contains(&TerrainPassInput::ShadowMap) {
            required.insert(TerrainSourceResourceRole::ShadowDepthPrimary);
        }
        if contract
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
        {
            // Occupancy is an owned generation input for the flood-fill
            // runtime. The terrain program may never sample it directly, so
            // it cannot be required in this source-stage sampler manifest.
            required.extend([
                TerrainSourceResourceRole::ColoredVoxelLightCurrent,
                TerrainSourceResourceRole::ColoredVoxelLightPrevious,
            ]);
        }
        for role in required {
            if !self.bindings.values().any(|declared| declared == &role) {
                return Err(GalError::invalid_argument(format!(
                    "terrain source contract requires semantic resource role '{}'",
                    role.semantic_name()
                )));
            }
        }
        Ok(())
    }
}

fn valid_identifier(name: &str) -> bool {
    let mut bytes = name.bytes();
    matches!(bytes.next(), Some(byte) if byte == b'_' || byte.is_ascii_alphabetic())
        && bytes.all(|byte| byte == b'_' || byte.is_ascii_alphanumeric())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::handles::{Handle, HandleKind};
    use crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;
    use crate::render::vulkanic::shader_pack::terrain_contract::{
        TerrainMaterialClass, TerrainPassOperation, TerrainPassOutput, TerrainSourcePassKind,
        UnsupportedTerrainFeature,
    };

    fn contract(
        inputs: BTreeSet<TerrainPassInput>,
        required_resources: BTreeSet<TerrainPassRequiredResource>,
    ) -> TerrainPassContract {
        TerrainPassContract {
            pass_kind: TerrainSourcePassKind::OpaqueCutout,
            pack_name: "test".to_string(),
            generation: 1,
            program_path: "gbuffers_terrain.fsh".to_string(),
            material_classes: BTreeSet::from([TerrainMaterialClass::Opaque]),
            inputs,
            outputs: BTreeSet::from([TerrainPassOutput::LitTerrainColor]),
            output_color_slots: BTreeMap::from([(TerrainPassOutput::LitTerrainColor, 0)]),
            property_defines: BTreeMap::new(),
            material_ids: BTreeMap::new(),
            runtime_block_state_material_ids: None,
            operations: vec![TerrainPassOperation::AtlasSample],
            required_resources,
            voxel_light_volume_requirements: None,
            translucent_raster_state: None,
            unsupported: BTreeSet::<UnsupportedTerrainFeature>::new(),
        }
    }

    #[test]
    fn parses_bounded_semantic_resource_roles_without_native_state() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nvoxel_sampler=colored_voxel_occupancy\nfloodfill_sampler=colored_voxel_light_current\n",
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        assert_eq!(
            Some(TerrainSourceResourceRole::MaterialAtlas),
            bindings.role_for("tex")
        );
        assert_eq!(
            "usampler3D",
            bindings
                .role_for("voxel_sampler")
                .unwrap()
                .expected_sampler_type()
        );
    }

    #[test]
    fn bundled_pack_declares_voxel_sampler_as_owned_occupancy_volume() {
        let source = complete_bundled_pack_source_for_test();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();

        assert_eq!(
            Some(TerrainSourceResourceRole::ColoredVoxelOccupancy),
            bindings.role_for("voxel_sampler")
        );
        assert_eq!(
            Some(TerrainSourceResourceRole::PuddleOccupancy),
            bindings.role_for("puddle_sampler")
        );
        assert_eq!(
            Some(TerrainSourceResourceRole::PuddleOccupancy),
            bindings.role_for("puddle_img")
        );
    }

    #[test]
    fn puddle_occupancy_is_an_unsigned_2d_sampler_and_storage_contract() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "puddle_sampler=puddle_occupancy\npuddle_img=puddle_occupancy\n",
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let role = bindings.role_for("puddle_sampler").unwrap();

        assert_eq!(TerrainSourceResourceRole::PuddleOccupancy, role);
        assert_eq!("puddle_occupancy", role.semantic_name());
        assert_eq!("usampler2D", role.expected_sampler_type());
        assert_eq!(Some("uimage2D"), role.expected_storage_image_type());
        assert_eq!(
            TerrainSourceSampledResourceShape::UnsignedTexture2d,
            role.expected_sampled_resource_shape()
        );
        assert_eq!(Some(role.clone()), bindings.role_for("puddle_img"));
    }

    #[test]
    fn pack_texture_roles_keep_normalized_asset_identity() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "cloud_noise=pack_texture:lib/textures/cloud-water.png\n",
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        assert_eq!(
            Some(TerrainSourceResourceRole::PackTexture(
                "lib/textures/cloud-water.png".to_string()
            )),
            bindings.role_for("cloud_noise")
        );
        assert_eq!(
            Some("lib/textures/cloud-water.png"),
            bindings
                .role_for("cloud_noise")
                .as_ref()
                .and_then(TerrainSourceResourceRole::pack_texture_path)
        );

        let malformed = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "cloud_noise=pack_texture:../escape.png\n",
            )],
        )
        .unwrap();
        assert!(TerrainSourceResourceBindings::from_source(&malformed)
            .unwrap_err()
            .to_string()
            .contains("normalized relative"));
    }

    #[test]
    fn distinguishes_raw_compare_and_color_shadow_inputs() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                concat!(
                    "shadow_compare=shadow_depth_primary\n",
                    "shadow_compare_secondary=shadow_depth_secondary\n",
                    "shadow_raw=shadow_depth_raw\n",
                    "shadow_color=shadow_color\n",
                    "normals=material_normal_map\n",
                    "specular=material_specular_map\n",
                ),
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        assert_eq!(
            Some(TerrainSourceResourceRole::ShadowDepthPrimary),
            bindings.role_for("shadow_compare")
        );
        assert_eq!(
            "sampler2DShadow",
            bindings
                .role_for("shadow_compare")
                .unwrap()
                .expected_sampler_type()
        );
        assert_eq!(
            "sampler2D",
            bindings
                .role_for("shadow_raw")
                .unwrap()
                .expected_sampler_type()
        );
        assert_eq!(
            "sampler2DShadow",
            bindings
                .role_for("shadow_compare_secondary")
                .unwrap()
                .expected_sampler_type()
        );
        assert_eq!(
            "material_normal_map",
            bindings.role_for("normals").unwrap().semantic_name()
        );
        assert_eq!(
            "material_specular_map",
            bindings.role_for("specular").unwrap().semantic_name()
        );
    }

    #[test]
    fn keeps_distant_depth_streams_distinct_from_shadow_depth() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                concat!(
                    "dh_opaque=distant_horizons_opaque_depth\n",
                    "dh_pre_translucent=distant_horizons_depth_before_translucency\n",
                ),
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        for name in ["dh_opaque", "dh_pre_translucent"] {
            let role = bindings.role_for(name).unwrap();
            assert_eq!("sampler2D", role.expected_sampler_type());
            assert_eq!(
                TerrainSourceSampledResourceShape::Texture2d,
                role.expected_sampled_resource_shape()
            );
            assert_ne!(TerrainSourceResourceRole::ShadowDepthRaw, role);
        }
    }

    #[test]
    fn shader_pack_color_roles_are_named_semantics_not_attachment_indices() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "color=shader_pack_color:primary\n",
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let role = bindings.role_for("color").unwrap();
        assert_eq!(Some("primary"), role.shader_pack_color_name());
        assert_eq!("shader_pack_color:primary", role.diagnostic_name());
        assert_eq!("sampler2D", role.expected_sampler_type());
        assert_eq!(
            TerrainSourceSampledResourceShape::Texture2d,
            role.expected_sampled_resource_shape()
        );
        assert!(ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "color=shader_pack_color:colortex0\n",
            )],
        )
        .and_then(|source| TerrainSourceResourceBindings::from_source(&source))
        .is_err());
    }

    #[test]
    fn permits_semantic_resource_aliases_but_rejects_duplicate_names_or_unknown_roles() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nother=material_atlas\n",
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        assert_eq!(
            Some(TerrainSourceResourceRole::MaterialAtlas),
            bindings.role_for("tex")
        );
        assert_eq!(
            Some(TerrainSourceResourceRole::MaterialAtlas),
            bindings.role_for("other")
        );
        let duplicate_name = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\ntex=material_normal_map\n",
            )],
        )
        .unwrap();
        assert!(TerrainSourceResourceBindings::from_source(&duplicate_name).is_err());
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=bogus\n",
            )],
        )
        .unwrap();
        assert!(TerrainSourceResourceBindings::from_source(&source).is_err());
    }

    #[test]
    fn availability_set_requires_each_role_to_use_its_semantic_sample_shape() {
        let resources = TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::MaterialAtlas,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: 3,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ColoredVoxelLightCurrent,
                    shape: TerrainSourceSampledResourceShape::FloatTexture3d,
                    resource_generation: 4,
                },
            ],
        )
        .unwrap();
        assert_eq!(7, resources.shader_pack_generation());
        assert_eq!(12, resources.world_generation());
        assert_eq!(
            Some(TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::MaterialAtlas,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 3,
            }),
            resources.resource_for(TerrainSourceResourceRole::MaterialAtlas)
        );
        assert!(TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 4,
            }],
        )
        .unwrap_err()
        .to_string()
        .contains("requires UnsignedTexture3d"));

        assert!(TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::PuddleOccupancy,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 4,
            }],
        )
        .unwrap_err()
        .to_string()
        .contains("requires UnsignedTexture2d"));
        assert!(TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::Noise,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: 1,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::Noise,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: 2,
                },
            ],
        )
        .unwrap_err()
        .to_string()
        .contains("available more than once"));
        assert!(TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::MaterialAtlas,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 0,
            }],
        )
        .unwrap_err()
        .to_string()
        .contains("has no owned generation"));
    }

    #[test]
    fn contract_requirements_reject_missing_semantic_roles_before_binding() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                    "tex=material_atlas\nshadowtex0=shadow_depth\nfloodfill=colored_voxel_light_current\n",
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let contract = contract(
            BTreeSet::from([TerrainPassInput::AtlasColor, TerrainPassInput::ShadowMap]),
            BTreeSet::from([TerrainPassRequiredResource::ColoredVoxelLightVolume]),
        );

        assert!(bindings
            .require_contract_roles(&contract)
            .unwrap_err()
            .to_string()
            .contains("colored_voxel_light_previous"));
    }

    #[test]
    fn contract_requirements_accept_complete_semantic_roles() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nshadowtex0=shadow_depth\nvoxel=colored_voxel_occupancy\nfloodfill=colored_voxel_light_current\nfloodfill_copy=colored_voxel_light_previous\n",
            )],
        )
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let contract = contract(
            BTreeSet::from([TerrainPassInput::AtlasColor, TerrainPassInput::ShadowMap]),
            BTreeSet::from([TerrainPassRequiredResource::ColoredVoxelLightVolume]),
        );

        bindings.require_contract_roles(&contract).unwrap();
    }

    #[test]
    fn owned_resource_set_requires_exact_available_combined_samplers() {
        let availability = TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::MaterialAtlas,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 3,
            }],
        )
        .unwrap();
        let sampler = Handle::new(HandleKind::CombinedTextureSampler, 4, 1).unwrap();
        let owned = TerrainSourceOwnedResourceSet::new(
            availability.clone(),
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::MaterialAtlas,
                combined_sampler: sampler,
            }],
        )
        .unwrap();
        assert_eq!(
            Some(sampler),
            owned.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
        );

        assert!(TerrainSourceOwnedResourceSet::new(availability.clone(), []).is_err());
        assert!(TerrainSourceOwnedResourceSet::new(
            availability.clone(),
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::MaterialAtlas,
                combined_sampler: Handle::new(HandleKind::Sampler, 4, 1).unwrap(),
            }],
        )
        .is_err());
        assert!(TerrainSourceOwnedResourceSet::new(
            availability,
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::Noise,
                combined_sampler: sampler,
            }],
        )
        .is_err());
    }

    #[test]
    fn owned_resource_generation_signature_is_semantic_and_stably_ordered() {
        let availability = TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::Noise,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: 9,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::MaterialAtlas,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: 3,
                },
            ],
        )
        .unwrap();
        let resources = TerrainSourceOwnedResourceSet::new(
            availability,
            [
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::Noise,
                    combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 4, 1)
                        .unwrap(),
                },
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::MaterialAtlas,
                    combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 5, 1)
                        .unwrap(),
                },
            ],
        )
        .unwrap();

        assert_eq!(
            vec![
                (TerrainSourceResourceRole::MaterialAtlas, 3),
                (TerrainSourceResourceRole::Noise, 9),
            ],
            resources.generation_signature()
        );
    }

    #[test]
    fn sampled_resource_override_replaces_only_the_named_semantic_role() {
        let availability = TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::MaterialAtlas,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: 3,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::Noise,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: 9,
                },
            ],
        )
        .unwrap();
        let atlas = Handle::new(HandleKind::CombinedTextureSampler, 4, 1).unwrap();
        let noise = Handle::new(HandleKind::CombinedTextureSampler, 5, 1).unwrap();
        let local = Handle::new(HandleKind::CombinedTextureSampler, 6, 1).unwrap();
        let resources = TerrainSourceOwnedResourceSet::new(
            availability,
            [
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::MaterialAtlas,
                    combined_sampler: atlas,
                },
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::Noise,
                    combined_sampler: noise,
                },
            ],
        )
        .unwrap();
        let overridden = resources
            .with_combined_sampler_override(TerrainSourceResourceRole::MaterialAtlas, local, 17)
            .unwrap();
        assert_eq!(
            Some(local),
            overridden.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
        );
        assert_eq!(
            Some(noise),
            overridden.combined_sampler_for(TerrainSourceResourceRole::Noise)
        );
        assert_eq!(
            vec![
                (TerrainSourceResourceRole::MaterialAtlas, 17),
                (TerrainSourceResourceRole::Noise, 9),
            ],
            overridden.generation_signature()
        );
        assert!(resources
            .with_combined_sampler_override(
                TerrainSourceResourceRole::MaterialAtlas,
                Handle::new(HandleKind::Sampler, 6, 1).unwrap(),
                17,
            )
            .is_err());
    }

    #[test]
    fn owned_resource_set_keeps_storage_views_distinct_from_samplers() {
        let availability = TerrainSourceResourceAvailabilitySet::new(
            7,
            12,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
                shape: TerrainSourceSampledResourceShape::UnsignedTexture3d,
                resource_generation: 3,
            }],
        )
        .unwrap();
        let view = Handle::new(HandleKind::TextureView, 4, 1).unwrap();
        let resources = TerrainSourceOwnedResourceSet::with_storage_resources(
            availability.clone(),
            [],
            [TerrainSourceOwnedStorageResource {
                role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
                texture_view: view,
            }],
        )
        .unwrap();
        assert_eq!(
            Some(view),
            resources.storage_texture_for(TerrainSourceResourceRole::ColoredVoxelOccupancy)
        );
        assert_eq!(
            None,
            resources.combined_sampler_for(TerrainSourceResourceRole::ColoredVoxelOccupancy)
        );
        assert_eq!(1, resources.len());

        assert!(TerrainSourceOwnedResourceSet::with_storage_resources(
            availability,
            [],
            [TerrainSourceOwnedStorageResource {
                role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
                texture_view: Handle::new(HandleKind::Sampler, 4, 1).unwrap(),
            }],
        )
        .is_err());
    }

    #[test]
    fn semantic_resource_subsets_merge_only_with_exact_generations_and_roles() {
        let availability = |role: TerrainSourceResourceRole, generation, world_generation| {
            TerrainSourceResourceAvailabilitySet::new(
                generation,
                world_generation,
                [TerrainSourceResourceAvailability {
                    role: role.clone(),
                    shape: role.expected_sampled_resource_shape(),
                    resource_generation: 3,
                }],
            )
            .unwrap()
        };
        let noise = TerrainSourceOwnedResourceSet::new(
            availability(TerrainSourceResourceRole::Noise, 7, 12),
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::Noise,
                combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 1, 1).unwrap(),
            }],
        )
        .unwrap();
        let atlas = TerrainSourceOwnedResourceSet::new(
            availability(TerrainSourceResourceRole::MaterialAtlas, 7, 12),
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::MaterialAtlas,
                combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 2, 1).unwrap(),
            }],
        )
        .unwrap();
        let merged = TerrainSourceOwnedResourceSet::merge([&noise, &atlas]).unwrap();
        assert_eq!(
            Some(Handle::new(HandleKind::CombinedTextureSampler, 1, 1).unwrap()),
            merged.combined_sampler_for(TerrainSourceResourceRole::Noise)
        );
        assert_eq!(
            Some(Handle::new(HandleKind::CombinedTextureSampler, 2, 1).unwrap()),
            merged.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
        );
        let stale = TerrainSourceOwnedResourceSet::new(
            availability(TerrainSourceResourceRole::ShadowColor, 8, 12),
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::ShadowColor,
                combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 3, 1).unwrap(),
            }],
        )
        .unwrap();
        assert!(TerrainSourceOwnedResourceSet::merge([&noise, &stale]).is_err());
        assert!(TerrainSourceOwnedResourceSet::merge([&noise, &noise]).is_err());

        let exact_duplicate = noise.excluding_roles_already_owned_by(&noise).unwrap();
        assert_eq!(0, exact_duplicate.len());

        let conflicting_noise = TerrainSourceOwnedResourceSet::new(
            availability(TerrainSourceResourceRole::Noise, 7, 12),
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::Noise,
                combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 9, 1).unwrap(),
            }],
        )
        .unwrap();
        assert!(conflicting_noise
            .excluding_roles_already_owned_by(&noise)
            .unwrap_err()
            .to_string()
            .contains("conflicts with an earlier source-stage binding"));
    }
}
