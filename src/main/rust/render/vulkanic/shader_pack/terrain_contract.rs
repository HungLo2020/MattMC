//! Source-derived, backend-neutral contract for a normal terrain material pass.
//!
//! This module deliberately describes inputs and outputs, rather than Iris
//! attachment slots, shader objects, or API state. Backends lower the contract
//! through the ordinary shader-pack runtime.

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::{ShaderPackSource, ShaderSourceFile};

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainMaterialClass {
    Opaque,
    Cutout,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassInput {
    AtlasColor,
    AtlasUv,
    Tint,
    AmbientOcclusion,
    PackedBlockLight,
    PackedSkyLight,
    GeometricNormal,
    MaterialIdentity,
    WorldPosition,
    Camera,
    DirectionalLight,
    Environment,
    ShadowMap,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassOutput {
    LitTerrainColor,
    MaterialAuxiliary,
    ViewSpaceNormal,
}

/// Ordered normal-terrain expressions recovered from the selected program.
/// These are shader semantics, never API attachment slots or native state.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassOperation {
    AtlasSample,
    AlphaDiscard,
    TintMultiply,
    TerrainLighting,
    LitColorOutput,
    MaterialAuxiliaryOutput,
    ViewSpaceNormalOutput,
}

impl TerrainPassOutput {
    pub fn semantic_name(self) -> &'static str {
        match self {
            Self::LitTerrainColor => "terrain_lit_color",
            Self::MaterialAuxiliary => "terrain_material_auxiliary",
            Self::ViewSpaceNormal => "terrain_view_space_normal",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum UnsupportedTerrainFeature {
    ParallaxOcclusionMapping,
    GeneratedNormals,
    CustomPbr,
    AnisotropicFiltering,
    ColoredVoxelLighting,
    RainPuddles,
    MaterialReflections,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainPassContract {
    pub pack_name: String,
    pub generation: u64,
    pub program_path: String,
    pub material_classes: BTreeSet<TerrainMaterialClass>,
    pub inputs: BTreeSet<TerrainPassInput>,
    pub outputs: BTreeSet<TerrainPassOutput>,
    pub property_defines: BTreeMap<String, String>,
    pub material_ids: BTreeMap<i32, Vec<String>>,
    pub operations: Vec<TerrainPassOperation>,
    pub unsupported: BTreeSet<UnsupportedTerrainFeature>,
}

impl TerrainPassContract {
    pub fn supports_selected_subset(&self) -> bool {
        self.unsupported.is_empty()
    }

    pub fn require_selected_subset(&self) -> GalResult<()> {
        if self.supports_selected_subset() {
            return Ok(());
        }
        Err(GalError::unsupported_feature(format!(
            "terrain shader-pass contract contains unsupported features: {}",
            self.unsupported
                .iter()
                .map(|feature| format!("{feature:?}"))
                .collect::<Vec<_>>()
                .join(", ")
        )))
    }
}

pub fn derive_complementary_terrain_contract(source: &ShaderPackSource) -> GalResult<TerrainPassContract> {
    let terrain_path = "program/gbuffers_terrain.glsl";
    // Contract discovery intentionally examines source semantics before any
    // backend compilation. The ordinary preprocessor is still responsible for
    // producing executable source once the selected profile is supported.
    let terrain = source
        .get(terrain_path)
        .ok_or_else(|| GalError::invalid_argument("missing program/gbuffers_terrain.glsl"))?;
    require_expression(&terrain, "DoLighting(")?;
    require_expression(&terrain, "vec4 color = texture2D(tex, texCoord)")?;
    require_expression(&terrain, "if (color.a <= 0.00001) discard")?;
    require_expression(&terrain, "color.rgb *= glColor.rgb")?;
    require_expression(&terrain, "gl_FragData[0] = color")?;
    require_expression(&terrain, "gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0)")?;
    require_expression(&terrain, "DRAWBUFFERS:06")?;

    let common = source
        .get("lib/common.glsl")
        .ok_or_else(|| GalError::invalid_argument("missing lib/common.glsl for terrain contract"))?;
    let properties = source
        .get("shaders.properties")
        .ok_or_else(|| GalError::invalid_argument("missing shaders.properties for terrain contract"))?;
    let block_properties = source
        .get("block.properties")
        .ok_or_else(|| GalError::invalid_argument("missing block.properties for terrain contract"))?;
    let property_defines = parse_defines(common, properties);
    let material_ids = parse_block_properties(block_properties);
    let mut outputs = BTreeSet::from([
        TerrainPassOutput::LitTerrainColor,
        TerrainPassOutput::MaterialAuxiliary,
    ]);
    if terrain.contains("gl_FragData[2] = vec4(mat3(gbufferModelViewInverse) * normalM, 1.0)") {
        outputs.insert(TerrainPassOutput::ViewSpaceNormal);
    }
    let mut inputs = BTreeSet::from([
        TerrainPassInput::AtlasColor,
        TerrainPassInput::AtlasUv,
        TerrainPassInput::Tint,
        TerrainPassInput::AmbientOcclusion,
        TerrainPassInput::PackedBlockLight,
        TerrainPassInput::PackedSkyLight,
        TerrainPassInput::GeometricNormal,
        TerrainPassInput::MaterialIdentity,
        TerrainPassInput::WorldPosition,
        TerrainPassInput::Camera,
        TerrainPassInput::DirectionalLight,
        TerrainPassInput::Environment,
    ]);
    if terrain.contains("shadow") || terrain.contains("Shadow") {
        inputs.insert(TerrainPassInput::ShadowMap);
    }
    let mut operations = vec![
        TerrainPassOperation::AtlasSample,
        TerrainPassOperation::AlphaDiscard,
        TerrainPassOperation::TintMultiply,
        TerrainPassOperation::TerrainLighting,
        TerrainPassOperation::LitColorOutput,
        TerrainPassOperation::MaterialAuxiliaryOutput,
    ];
    if outputs.contains(&TerrainPassOutput::ViewSpaceNormal) {
        operations.push(TerrainPassOperation::ViewSpaceNormalOutput);
    }
    Ok(TerrainPassContract {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        program_path: terrain_path.to_string(),
        material_classes: BTreeSet::from([TerrainMaterialClass::Opaque, TerrainMaterialClass::Cutout]),
        inputs,
        outputs,
        property_defines: property_defines.clone(),
        material_ids,
        operations,
        unsupported: unsupported_features(&terrain, &property_defines),
    })
}

pub fn bundled_complementary_hung_loified_source(generation: u64) -> GalResult<ShaderPackSource> {
    ShaderPackSource::new(
        "ComplementaryHungLoIfied",
        generation,
        vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/program/gbuffers_terrain.glsl")),
            ShaderSourceFile::new("lib/common.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/common.glsl")),
            ShaderSourceFile::new("lib/lighting/mainLighting.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/lighting/mainLighting.glsl")),
            ShaderSourceFile::new("lib/util/spaceConversion.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/spaceConversion.glsl")),
            ShaderSourceFile::new("lib/util/dither.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/dither.glsl")),
            ShaderSourceFile::new("shaders.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/shaders.properties")),
            ShaderSourceFile::new("block.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/block.properties")),
        ],
    )
}

fn require_expression(source: &str, expression: &str) -> GalResult<()> {
    if source.contains(expression) {
        Ok(())
    } else {
        Err(GalError::invalid_argument(format!(
            "terrain source is missing required normal-pass expression {expression}"
        )))
    }
}

fn parse_defines(common: &str, properties: &str) -> BTreeMap<String, String> {
    let mut defines = BTreeMap::new();
    for line in common.lines() {
        let line = line.trim();
        let Some(rest) = line.strip_prefix("#define ") else { continue };
        let mut values = rest.split_whitespace();
        let Some(key) = values.next() else { continue };
        if let Some(value) = values.next() {
            defines.insert(key.to_string(), value.to_string());
        }
    }
    for line in properties.lines() {
        let line = line.trim();
        let Some(rest) = line.strip_prefix("profile.MATTMC=") else { continue };
        for entry in rest.split_whitespace() {
            let (key, value) = entry.split_once('=').unwrap_or((entry, "1"));
            defines.insert(key.to_string(), value.to_string());
        }
    }
    defines
}

fn parse_block_properties(source: &str) -> BTreeMap<i32, Vec<String>> {
    let mut ids = BTreeMap::new();
    for line in source.lines() {
        let line = line.trim();
        let Some((key, value)) = line.split_once('=') else { continue };
        let Some(id) = key.trim().strip_prefix("block.").and_then(|id| id.parse::<i32>().ok()) else { continue };
        let values = value
            .split_whitespace()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(ToOwned::to_owned)
            .collect::<Vec<_>>();
        if !values.is_empty() {
            ids.insert(id, values);
        }
    }
    ids
}

fn unsupported_features(source: &str, defines: &BTreeMap<String, String>) -> BTreeSet<UnsupportedTerrainFeature> {
    let mut unsupported = BTreeSet::new();
    let enabled = |key: &str| defines.get(key).map(String::as_str).unwrap_or("0") != "0";
    if source.contains("#ifdef POM") && enabled("POM") { unsupported.insert(UnsupportedTerrainFeature::ParallaxOcclusionMapping); }
    if source.contains("GENERATED_NORMALS") && enabled("GENERATED_NORMALS") { unsupported.insert(UnsupportedTerrainFeature::GeneratedNormals); }
    if source.contains("CUSTOM_PBR") && enabled("CUSTOM_PBR") { unsupported.insert(UnsupportedTerrainFeature::CustomPbr); }
    if enabled("ANISOTROPIC_FILTER") { unsupported.insert(UnsupportedTerrainFeature::AnisotropicFiltering); }
    if enabled("COLORED_LIGHTING_INTERNAL") || enabled("COLORED_LIGHTING") { unsupported.insert(UnsupportedTerrainFeature::ColoredVoxelLighting); }
    if enabled("RAIN_PUDDLES") { unsupported.insert(UnsupportedTerrainFeature::RainPuddles); }
    unsupported
}

#[cfg(test)]
mod tests {
    use super::*;

    fn source() -> ShaderPackSource {
        ShaderPackSource::new("test", 1, vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "#include \"/lib/common.glsl\"\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
            ShaderSourceFile::new("lib/common.glsl", "#define SHADOW_QUALITY 2\n"),
            ShaderSourceFile::new("shaders.properties", "profile.MATTMC=SHADOW_QUALITY=2\n"),
            ShaderSourceFile::new("block.properties", "block.10009=minecraft:oak_leaves\n"),
        ]).unwrap()
    }

    #[test]
    fn source_derived_contract_names_complementary_outputs_not_draw_buffers() {
        let contract = derive_complementary_terrain_contract(&source()).unwrap();
        assert!(contract.outputs.contains(&TerrainPassOutput::LitTerrainColor));
        assert!(contract.outputs.contains(&TerrainPassOutput::MaterialAuxiliary));
        assert_eq!(
            vec![
                TerrainPassOperation::AtlasSample,
                TerrainPassOperation::AlphaDiscard,
                TerrainPassOperation::TintMultiply,
                TerrainPassOperation::TerrainLighting,
                TerrainPassOperation::LitColorOutput,
                TerrainPassOperation::MaterialAuxiliaryOutput,
            ],
            contract.operations
        );
        assert_eq!(Some(&vec!["minecraft:oak_leaves".to_string()]), contract.material_ids.get(&10009));
        assert!(!contract.inputs.contains(&TerrainPassInput::ShadowMap));
    }

    #[test]
    fn missing_normal_terrain_output_is_rejected() {
        let source = ShaderPackSource::new("bad", 1, vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void main() {}"),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ]).unwrap();
        assert!(derive_complementary_terrain_contract(&source).is_err());
    }

    #[test]
    fn selected_profile_reports_unsupported_branches_instead_of_substituting_lighting() {
        let source = ShaderPackSource::new("unsupported", 1, vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
            ShaderSourceFile::new("lib/common.glsl", "#define COLORED_LIGHTING 1\n"),
            ShaderSourceFile::new("shaders.properties", "profile.MATTMC=COLORED_LIGHTING=1\n"),
            ShaderSourceFile::new("block.properties", ""),
        ]).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        assert!(contract.require_selected_subset().is_err());
    }
}
