//! Pack-declared semantic bindings for lowered terrain source resources.
//!
//! A shader source name is not a backend binding. Explicit transported
//! manifests or the standard Iris source protocol resolve names into stable
//! semantic roles. Pass-specific custom textures require selected-stage
//! preprocessing; alias discovery creates no native resources.

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};

use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::shaderpack::contracts::terrain::{TerrainPassContract, TerrainPassInput, TerrainPassRequiredResource};

pub const TERRAIN_RESOURCE_BINDINGS_PATH: &str = "mattmc/terrain-resource-bindings.properties";

mod legacy;

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
    /// Raw depth from the opaque-only shadow snapshot.
    ShadowDepthRawSecondary,
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
            "shadow_depth_raw_secondary" => Ok(Self::ShadowDepthRawSecondary),
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
            | Self::ShadowDepthRawSecondary
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
            (Self::ShadowDepthPrimary, "sampler2D") => Ok(Self::ShadowDepthRaw),
            (Self::ShadowDepthSecondary, "sampler2D") => Ok(Self::ShadowDepthRawSecondary),
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
            | Self::ShadowDepthRawSecondary
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
            Self::ShadowDepthRawSecondary => "shadow_depth_raw_secondary",
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
            | Self::ShadowDepthRawSecondary
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
///
/// Immutable after construction and shared: per-draw material preparation
/// clones and compares the same frame set many times, so clones share one
/// allocation and equality first checks identity.
#[derive(Clone, Debug)]
pub struct TerrainSourceOwnedResourceSet(std::sync::Arc<OwnedResourceSetFields>);

#[derive(Debug, Eq, PartialEq)]
struct OwnedResourceSetFields {
    availability: TerrainSourceResourceAvailabilitySet,
    samplers: BTreeMap<TerrainSourceResourceRole, Handle>,
    storage_views: BTreeMap<TerrainSourceResourceRole, Handle>,
}

impl PartialEq for TerrainSourceOwnedResourceSet {
    fn eq(&self, other: &Self) -> bool {
        std::sync::Arc::ptr_eq(&self.0, &other.0) || *self.0 == *other.0
    }
}

impl Eq for TerrainSourceOwnedResourceSet {}

impl TerrainSourceOwnedResourceSet {
    /// Whether both name the same snapshot object. Memos keyed by a held
    /// snapshot use this; equal content in another object simply misses.
    pub(crate) fn same_snapshot(&self, other: &Self) -> bool {
        std::sync::Arc::ptr_eq(&self.0, &other.0)
    }

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
        Ok(Self(std::sync::Arc::new(OwnedResourceSetFields {
            availability,
            samplers,
            storage_views,
        })))
    }

    pub fn availability(&self) -> &TerrainSourceResourceAvailabilitySet {
        &self.0.availability
    }

    pub fn combined_sampler_for(&self, role: TerrainSourceResourceRole) -> Option<Handle> {
        self.0.samplers.get(&role).copied()
    }

    pub fn storage_texture_for(&self, role: TerrainSourceResourceRole) -> Option<Handle> {
        self.0.storage_views.get(&role).copied()
    }

    pub fn len(&self) -> usize {
        self.0.samplers.len() + self.0.storage_views.len()
    }

    /// Stable semantic cache identity for one owned source-resource table.
    /// It intentionally contains only role and generation facts, never a GAL
    /// handle, descriptor, texture unit, or backend-native resource identity.
    /// A frontend may use this to retain a compatible source resource set
    /// across frames while making pack/world replacement invalidate it.
    pub fn generation_signature(&self) -> Vec<(TerrainSourceResourceRole, u64)> {
        self.0.availability
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
        let Some(existing) = self.0.availability.resource_for(role.clone()) else {
            return Err(GalError::invalid_argument(format!(
                "terrain source override references unavailable role '{}'",
                role.semantic_name()
            )));
        };
        if self.0.storage_views.contains_key(&role) || !self.0.samplers.contains_key(&role) {
            return Err(GalError::invalid_argument(format!(
                "terrain source override requires sampled role '{}'",
                role.semantic_name()
            )));
        }
        let availability = self
            .0.availability
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
            .0.samplers
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
            .0.storage_views
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
                self.0.availability.shader_pack_generation(),
                self.0.availability.world_generation(),
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
            .0.availability
            .resources()
            .filter(|resource| !exclusions.contains(&resource.role))
            .collect::<Vec<_>>();
        let resources = self
            .0.samplers
            .iter()
            .filter(|(role, _)| !exclusions.contains(*role))
            .map(|(role, &combined_sampler)| TerrainSourceOwnedResource {
                role: role.clone(),
                combined_sampler,
            })
            .collect::<Vec<_>>();
        let storage_resources = self
            .0.storage_views
            .iter()
            .filter(|(role, _)| !exclusions.contains(*role))
            .map(|(role, &texture_view)| TerrainSourceOwnedStorageResource {
                role: role.clone(),
                texture_view,
            })
            .collect::<Vec<_>>();
        Self::with_storage_resources(
            TerrainSourceResourceAvailabilitySet::new(
                self.0.availability.shader_pack_generation(),
                self.0.availability.world_generation(),
                availability,
            )?,
            resources,
            storage_resources,
        )
    }

    /// Selects a writer's locally owned color samplers over color bindings
    /// carried by an admission snapshot. Program-local descriptor ordinals
    /// can produce different combined handles for the same target image.
    /// Every other resource role stays unchanged and strict merging remains
    /// the rule for independently owned non-color subsets.
    pub(crate) fn with_stage_color_resources(
        &self,
        colors: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<Self> {
        if self.0.availability.shader_pack_generation() != colors.0.availability.shader_pack_generation()
            || self.0.availability.world_generation() != colors.0.availability.world_generation()
        {
            return Err(GalError::invalid_argument(
                "stage color bindings must match the snapshot's world and shader-pack generations",
            ));
        }
        let roles = colors.0.availability.resources().map(|resource| resource.role).collect::<Vec<_>>();
        if roles.iter().any(|role| !matches!(role, TerrainSourceResourceRole::ShaderPackColor(_)))
            || !colors.0.storage_views.is_empty()
        {
            return Err(GalError::invalid_argument(
                "stage color replacement accepts only owned color sampler roles",
            ));
        }
        let base = self.excluding_roles(roles)?;
        Self::merge([&base, colors])
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
        if self.0.availability.shader_pack_generation()
            != existing.0.availability.shader_pack_generation()
            || self.0.availability.world_generation() != existing.0.availability.world_generation()
        {
            return Err(GalError::invalid_argument(
                "cannot compare terrain source resources from different shader-pack or world generations",
            ));
        }
        let mut exclusions = BTreeSet::new();
        for resource in self.0.availability.resources() {
            let Some(previous) = existing.0.availability.resource_for(resource.role.clone()) else {
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
            let source_generation = set.0.availability.shader_pack_generation();
            let world_generation = set.0.availability.world_generation();
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
            availability.extend(set.0.availability.resources());
            resources.extend(set.0.samplers.iter().map(|(role, combined_sampler)| {
                TerrainSourceOwnedResource {
                    role: role.clone(),
                    combined_sampler: *combined_sampler,
                }
            }));
            storage_resources.extend(set.0.storage_views.iter().map(|(role, texture_view)| {
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
    // Protocol outputs remain color identities even when a phase overrides
    // the corresponding sampler spelling with a custom PNG.
    color_outputs: BTreeMap<u32, TerrainSourceResourceRole>,
}

impl TerrainSourceResourceBindings {
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let Some(contents) = source.get(TERRAIN_RESOURCE_BINDINGS_PATH) else {
            return Self::from_legacy_source(source);
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
        Ok(Self { bindings, color_outputs: BTreeMap::new() })
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
        if let Some(role) = self.color_outputs.get(&slot) {
            return Ok(role.clone());
        }
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
mod tests;
