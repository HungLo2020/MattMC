//! Pack-declared semantic bindings for lowered terrain source resources.
//!
//! A shader source name is not a backend binding. This module converts an
//! explicitly transported pack declaration into stable semantic roles; it
//! intentionally contains neither native handles nor attachment indices.

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;
use super::terrain_contract::{TerrainPassContract, TerrainPassInput, TerrainPassRequiredResource};

pub const TERRAIN_RESOURCE_BINDINGS_PATH: &str = "mattmc/terrain-resource-bindings.properties";

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainSourceResourceRole {
    MaterialAtlas,
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
    Noise,
    GBufferAlbedo,
    GBufferNormal,
    GBufferMaterialLight,
    GBufferWorldPosition,
    MainDepth,
    ColoredVoxelOccupancy,
    ColoredVoxelLightCurrent,
    ColoredVoxelLightPrevious,
}

impl TerrainSourceResourceRole {
    fn parse(value: &str) -> GalResult<Self> {
        match value {
            "material_atlas" => Ok(Self::MaterialAtlas),
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
            "noise" => Ok(Self::Noise),
            "g_buffer_albedo" => Ok(Self::GBufferAlbedo),
            "g_buffer_normal" => Ok(Self::GBufferNormal),
            "g_buffer_material_light" => Ok(Self::GBufferMaterialLight),
            "g_buffer_world_position" => Ok(Self::GBufferWorldPosition),
            "main_depth" => Ok(Self::MainDepth),
            "colored_voxel_occupancy" => Ok(Self::ColoredVoxelOccupancy),
            "colored_voxel_light_current" => Ok(Self::ColoredVoxelLightCurrent),
            "colored_voxel_light_previous" => Ok(Self::ColoredVoxelLightPrevious),
            _ => Err(GalError::unsupported_feature(format!(
                "unknown semantic terrain source resource role '{value}'"
            ))),
        }
    }

    pub fn expected_sampler_type(self) -> &'static str {
        match self {
            Self::MaterialAtlas
            | Self::MaterialSpecularMap
            | Self::Lightmap
            | Self::Noise
            | Self::ShadowDepthRaw
            | Self::ShadowColor
            | Self::GBufferAlbedo
            | Self::GBufferNormal
            | Self::GBufferMaterialLight
            | Self::GBufferWorldPosition
            | Self::MainDepth => "sampler2D",
            Self::ShadowDepthPrimary | Self::ShadowDepthSecondary => "sampler2DShadow",
            Self::ColoredVoxelOccupancy => "usampler3D",
            Self::ColoredVoxelLightCurrent | Self::ColoredVoxelLightPrevious => "sampler3D",
        }
    }

    pub fn semantic_name(self) -> &'static str {
        match self {
            Self::MaterialAtlas => "material_atlas",
            Self::MaterialSpecularMap => "material_specular_map",
            Self::Lightmap => "lightmap",
            Self::ShadowDepthPrimary => "shadow_depth_primary",
            Self::ShadowDepthSecondary => "shadow_depth_secondary",
            Self::ShadowDepthRaw => "shadow_depth_raw",
            Self::ShadowColor => "shadow_color",
            Self::Noise => "noise",
            Self::GBufferAlbedo => "g_buffer_albedo",
            Self::GBufferNormal => "g_buffer_normal",
            Self::GBufferMaterialLight => "g_buffer_material_light",
            Self::GBufferWorldPosition => "g_buffer_world_position",
            Self::MainDepth => "main_depth",
            Self::ColoredVoxelOccupancy => "colored_voxel_occupancy",
            Self::ColoredVoxelLightCurrent => "colored_voxel_light_current",
            Self::ColoredVoxelLightPrevious => "colored_voxel_light_previous",
        }
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
        let mut roles = BTreeSet::new();
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
            if !roles.insert(role) {
                return Err(GalError::invalid_argument(format!(
                    "terrain resource role {:?} is declared more than once",
                    role
                )));
            }
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
        self.bindings.get(name).copied()
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
            if !self.bindings.values().any(|declared| *declared == role) {
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
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;
    use crate::render::vulkanic::shader_pack::terrain_contract::{
        TerrainMaterialClass, TerrainPassOperation, TerrainPassOutput, UnsupportedTerrainFeature,
    };

    fn contract(
        inputs: BTreeSet<TerrainPassInput>,
        required_resources: BTreeSet<TerrainPassRequiredResource>,
    ) -> TerrainPassContract {
        TerrainPassContract {
            pack_name: "test".to_string(),
            generation: 1,
            program_path: "gbuffers_terrain.fsh".to_string(),
            material_classes: BTreeSet::from([TerrainMaterialClass::Opaque]),
            inputs,
            outputs: BTreeSet::from([TerrainPassOutput::LitTerrainColor]),
            property_defines: BTreeMap::new(),
            material_ids: BTreeMap::new(),
            operations: vec![TerrainPassOperation::AtlasSample],
            required_resources,
            voxel_light_volume_requirements: None,
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
            "material_specular_map",
            bindings.role_for("specular").unwrap().semantic_name()
        );
    }

    #[test]
    fn rejects_duplicate_or_unknown_semantic_roles() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nother=material_atlas\n",
            )],
        )
        .unwrap();
        assert!(TerrainSourceResourceBindings::from_source(&source).is_err());
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
}
