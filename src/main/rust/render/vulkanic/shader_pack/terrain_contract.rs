//! Source-derived, backend-neutral contract for a normal terrain material pass.
//!
//! This module deliberately describes inputs and outputs, rather than Iris
//! attachment slots, shader objects, or API state. Backends lower the contract
//! through the ordinary shader-pack runtime.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::{ShaderPackSource, ShaderSourceFile, RUNTIME_ENVIRONMENT_PATH};
use super::voxel_light_volume::{
    VoxelLightVolumeReadiness, VoxelLightVolumeRequirements, VoxelLightVolumeUpdatePolicy,
};

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainMaterialClass {
    Opaque,
    Cutout,
}

/// Semantic world scope for source-pack terrain program selection. This is a
/// pack-source identity only: it carries no Iris program, framebuffer, or GL
/// state. Runtime admission must still provide the matching explicit world
/// environment before any selected source can execute.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainProgramScope {
    Default,
    Overworld,
    Nether,
    End,
}

impl TerrainProgramScope {
    fn entry_candidates(self) -> &'static [&'static str] {
        match self {
            // The internal fixture uses the explicit program namespace. Real
            // packs conventionally expose the default fragment at root.
            Self::Default => &["program/gbuffers_terrain.glsl", "gbuffers_terrain.fsh"],
            // A pack's root terrain program is its explicit universal source,
            // not an accidental fallback to a different dimension. The
            // internal program path exists only for the Rust source fixture.
            Self::Overworld => &[
                "world0/gbuffers_terrain.fsh",
                "program/gbuffers_terrain.glsl",
                "gbuffers_terrain.fsh",
            ],
            Self::Nether => &[
                "world-1/gbuffers_terrain.fsh",
                "program/gbuffers_terrain.glsl",
                "gbuffers_terrain.fsh",
            ],
            Self::End => &[
                "world1/gbuffers_terrain.fsh",
                "program/gbuffers_terrain.glsl",
                "gbuffers_terrain.fsh",
            ],
        }
    }

    fn shadow_entry_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Default => &["shadow.fsh", "program/shadow.glsl"],
            Self::Overworld => &["world0/shadow.fsh", "shadow.fsh", "program/shadow.glsl"],
            Self::Nether => &["world-1/shadow.fsh", "shadow.fsh", "program/shadow.glsl"],
            Self::End => &["world1/shadow.fsh", "shadow.fsh", "program/shadow.glsl"],
        }
    }
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
    ColoredVoxelLightVolume,
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
    ColoredVoxelLighting,
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

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassRequiredResource {
    ColoredVoxelLightVolume,
}

/// One source stage selected by semantic pack/world metadata. The stage carries
/// only source path and preprocessor choices; it is not a compiler object,
/// backend binding, or Iris program reference.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceStage {
    pub path: String,
    pub defines: BTreeMap<String, String>,
}

/// Complete normal-terrain source pairing. A later executable source lowering
/// must resolve and validate both artifacts; discovering a fragment alone can
/// never establish shader-pack route readiness.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceStages {
    pub vertex: TerrainSourceStage,
    pub fragment: TerrainSourceStage,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainPassContract {
    pub pack_name: String,
    pub generation: u64,
    /// Backward-compatible fragment source identity for existing diagnostics.
    pub program_path: String,
    pub material_classes: BTreeSet<TerrainMaterialClass>,
    pub inputs: BTreeSet<TerrainPassInput>,
    pub outputs: BTreeSet<TerrainPassOutput>,
    pub property_defines: BTreeMap<String, String>,
    pub material_ids: BTreeMap<i32, Vec<String>>,
    pub operations: Vec<TerrainPassOperation>,
    pub required_resources: BTreeSet<TerrainPassRequiredResource>,
    pub voxel_light_volume_requirements: Option<VoxelLightVolumeRequirements>,
    pub unsupported: BTreeSet<UnsupportedTerrainFeature>,
}

impl TerrainPassContract {
    /// Reconstructs the complete normal-terrain source pairing from the
    /// canonical selected fragment identity. A future source route must use
    /// both returned stages before it may claim executable readiness.
    pub fn source_stages(&self) -> GalResult<TerrainSourceStages> {
        terrain_source_stages(&self.program_path)
    }

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

    /// Admission for executable selected-source terrain. Feature discovery is
    /// separate from resource readiness: a pack may require a semantic volume
    /// while all of its shader branches are otherwise supported.
    pub fn require_selected_subset_with_resources(
        &self,
        voxel_light_volume: Option<&VoxelLightVolumeReadiness>,
        frame_counter: u64,
    ) -> GalResult<()> {
        self.require_selected_subset()?;
        if self
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
        {
            let volume = voxel_light_volume.ok_or_else(|| {
                GalError::invalid_argument("missing required colored voxel-light volume generation")
            })?;
            volume.binding_for_frame(frame_counter)?;
            volume
                .descriptor()
                .requirements
                .update_policy
                .require_selected_source_support()?;
        }
        Ok(())
    }
}

pub fn derive_complementary_terrain_contract(
    source: &ShaderPackSource,
) -> GalResult<TerrainPassContract> {
    derive_complementary_terrain_contract_for_scope(source, TerrainProgramScope::Default)
}

/// Discovers a terrain fragment source for an explicitly supplied semantic
/// world scope. A missing scoped entry may use only the pack's explicit
/// universal terrain source; callers must never infer a program from an
/// unrelated active Iris pass.
pub fn derive_complementary_terrain_contract_for_scope(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TerrainPassContract> {
    let terrain_path = scope
        .entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing terrain fragment source for {scope:?}; tried {}",
                scope.entry_candidates().join(", ")
            ))
        })?;
    // Contract discovery intentionally examines source semantics before any
    // backend compilation. The ordinary preprocessor is still responsible for
    // producing executable source once the selected profile is supported.
    let terrain = expand_contract_includes(source, terrain_path)?;
    require_expression(&terrain, "DoLighting(")?;
    require_expression(&terrain, "vec4 color = texture2D(tex, texCoord)")?;
    require_expression(&terrain, "if (color.a <= 0.00001) discard")?;
    require_expression(&terrain, "color.rgb *= glColor.rgb")?;
    require_expression(&terrain, "gl_FragData[0] = color")?;
    require_expression(
        &terrain,
        "gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0)",
    )?;
    require_expression(&terrain, "DRAWBUFFERS:06")?;

    let common = source.get("lib/common.glsl").ok_or_else(|| {
        GalError::invalid_argument("missing lib/common.glsl for terrain contract")
    })?;
    let properties = source.get("shaders.properties").ok_or_else(|| {
        GalError::invalid_argument("missing shaders.properties for terrain contract")
    })?;
    let block_properties = source.get("block.properties").ok_or_else(|| {
        GalError::invalid_argument("missing block.properties for terrain contract")
    })?;
    let runtime_options = source.runtime_semantic_defines()?;
    let property_defines = parse_defines(common, properties, &runtime_options);
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
    let mut required_resources = BTreeSet::new();
    let voxel_light_volume_requirements =
        if let Some(colored_lighting) = colored_voxel_lighting_setting(&property_defines) {
            let shadow_compute = source.get("program/shadowcomp.glsl").ok_or_else(|| {
                GalError::invalid_argument(
                    "colored voxel-light terrain requires program/shadowcomp.glsl update semantics",
                )
            })?;
            let mut requirements = VoxelLightVolumeRequirements::complementary(colored_lighting)?;
            requirements.update_policy =
                VoxelLightVolumeUpdatePolicy::from_shadow_compute_source(shadow_compute)?;
            require_voxel_image(
                properties,
                "voxel_img",
                "voxel_sampler red_integer r8ui unsigned_int true false",
                requirements.extent,
            )?;
            require_voxel_image(
                properties,
                "floodfill_img",
                "floodfill_sampler rgba rgba16f half_float false false",
                requirements.extent,
            )?;
            require_voxel_image(
                properties,
                "floodfill_img_copy",
                "floodfill_sampler_copy rgba rgba16f half_float false false",
                requirements.extent,
            )?;
            inputs.insert(TerrainPassInput::ColoredVoxelLightVolume);
            required_resources.insert(TerrainPassRequiredResource::ColoredVoxelLightVolume);
            Some(requirements)
        } else {
            None
        };
    let mut operations = vec![
        TerrainPassOperation::AtlasSample,
        TerrainPassOperation::AlphaDiscard,
        TerrainPassOperation::TintMultiply,
        TerrainPassOperation::TerrainLighting,
        TerrainPassOperation::LitColorOutput,
        TerrainPassOperation::MaterialAuxiliaryOutput,
    ];
    if required_resources.contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume) {
        operations.insert(4, TerrainPassOperation::ColoredVoxelLighting);
    }
    if outputs.contains(&TerrainPassOutput::ViewSpaceNormal) {
        operations.push(TerrainPassOperation::ViewSpaceNormalOutput);
    }
    Ok(TerrainPassContract {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        program_path: terrain_path.to_string(),
        material_classes: BTreeSet::from([
            TerrainMaterialClass::Opaque,
            TerrainMaterialClass::Cutout,
        ]),
        inputs,
        outputs,
        property_defines: property_defines.clone(),
        material_ids,
        operations,
        required_resources,
        voxel_light_volume_requirements,
        unsupported: unsupported_features(&terrain, &property_defines),
    })
}

/// Resolves the paired shadow-source stages for one semantic world scope.
/// This is source provenance only: it neither compiles the source nor permits
/// a route to execute it. A future Rust-owned shadow-color pass must consume
/// these exact artifacts instead of inferring an Iris program or phase.
pub fn shadow_source_stages_for_scope(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TerrainSourceStages> {
    let entry = scope
        .shadow_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing shadow source for {scope:?}; tried {}",
                scope.shadow_entry_candidates().join(", ")
            ))
        })?;
    terrain_source_stages(entry)
}

/// Expands only the include graph needed to inspect immutable pack source
/// semantics. This is intentionally not the executable preprocessor: it does
/// not select conditional branches, inject environment defines, or retain a
/// compiler artifact. Scoped shader-pack entries are commonly wrappers around
/// shared program sources, so reading the wrapper alone cannot establish a
/// terrain contract.
fn expand_contract_includes(source: &ShaderPackSource, entry: &str) -> GalResult<String> {
    let mut stack = BTreeSet::new();
    let mut output = String::new();
    append_contract_source(source, entry, &mut stack, &mut output)?;
    if output.len() > ShaderPackSource::MAX_TOTAL_BYTES {
        return Err(GalError::invalid_argument(
            "terrain contract source expansion exceeds shader-pack byte limit",
        ));
    }
    Ok(output)
}

fn append_contract_source(
    source: &ShaderPackSource,
    path: &str,
    stack: &mut BTreeSet<String>,
    output: &mut String,
) -> GalResult<()> {
    let canonical = canonical_contract_path(path)?;
    if !stack.insert(canonical.clone()) {
        return Err(GalError::invalid_argument(format!(
            "cyclic terrain contract include {canonical}"
        )));
    }
    let contents = source.get(&canonical).ok_or_else(|| {
        GalError::invalid_argument(format!("missing terrain contract source {canonical}"))
    })?;
    for line in contents.lines() {
        if let Some(include) = contract_include(line) {
            let include_path = resolve_contract_include(&canonical, include)?;
            if source.get(&include_path).is_some() {
                append_contract_source(source, &include_path, stack, output)?;
            } else {
                // Contract discovery is a bounded semantic audit, not source
                // compilation. Curated source fixtures may omit unrelated
                // nested libraries; the executable preprocessor remains the
                // strict gate that rejects a selected source generation with
                // any missing active dependency.
                output.push_str(line);
                output.push('\n');
            }
        } else {
            output.push_str(line);
            output.push('\n');
        }
    }
    stack.remove(&canonical);
    Ok(())
}

fn contract_include(line: &str) -> Option<&str> {
    let rest = line.trim().strip_prefix("#include")?.trim();
    rest.strip_prefix('"')
        .and_then(|include| include.strip_suffix('"'))
        .or_else(|| {
            rest.strip_prefix('<')
                .and_then(|include| include.strip_suffix('>'))
        })
}

fn resolve_contract_include(parent: &str, include: &str) -> GalResult<String> {
    if include.starts_with('/') {
        return canonical_contract_path(include);
    }
    let parent = Path::new(parent);
    canonical_contract_path(
        &parent
            .parent()
            .unwrap_or_else(|| Path::new(""))
            .join(include)
            .to_string_lossy()
            .replace('\\', "/"),
    )
}

fn canonical_contract_path(path: &str) -> GalResult<String> {
    let normalized = path.trim_start_matches('/').replace('\\', "/");
    let mut components = Vec::new();
    for component in normalized.split('/') {
        match component {
            "" | "." => {}
            ".." => {
                if components.pop().is_none() {
                    return Err(GalError::invalid_argument(
                        "terrain contract include escapes shader-pack root",
                    ));
                }
            }
            component => components.push(component),
        }
    }
    if components.is_empty() {
        return Err(GalError::invalid_argument(
            "terrain contract include path is empty",
        ));
    }
    Ok(components.join("/"))
}

fn terrain_source_stages(fragment_path: &str) -> GalResult<TerrainSourceStages> {
    if let Some(vertex_path) = fragment_path.strip_suffix(".fsh") {
        return Ok(TerrainSourceStages {
            vertex: TerrainSourceStage {
                path: format!("{vertex_path}.vsh"),
                defines: BTreeMap::new(),
            },
            fragment: TerrainSourceStage {
                path: fragment_path.to_string(),
                defines: BTreeMap::new(),
            },
        });
    }
    if fragment_path.ends_with(".glsl") {
        return Ok(TerrainSourceStages {
            vertex: TerrainSourceStage {
                path: fragment_path.to_string(),
                defines: BTreeMap::from([("VERTEX_SHADER".to_string(), "1".to_string())]),
            },
            fragment: TerrainSourceStage {
                path: fragment_path.to_string(),
                defines: BTreeMap::from([("FRAGMENT_SHADER".to_string(), "1".to_string())]),
            },
        });
    }
    Err(GalError::invalid_argument(format!(
        "terrain fragment source {fragment_path} has no semantic vertex-stage pairing"
    )))
}

pub fn bundled_complementary_hung_loified_source(generation: u64) -> GalResult<ShaderPackSource> {
    ShaderPackSource::new(
        "ComplementaryHungLoIfied",
        generation,
        vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/program/gbuffers_terrain.glsl")),
            ShaderSourceFile::new("lib/common.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/common.glsl")),
            ShaderSourceFile::new("lib/uniforms.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/uniforms.glsl")),
            ShaderSourceFile::new("lib/util/commonFunctions.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/commonFunctions.glsl")),
            ShaderSourceFile::new("lib/lighting/mainLighting.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/lighting/mainLighting.glsl")),
            ShaderSourceFile::new("lib/misc/voxelization.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/misc/voxelization.glsl")),
            ShaderSourceFile::new("lib/colors/blocklightColors.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/colors/blocklightColors.glsl")),
            ShaderSourceFile::new("program/shadowcomp.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/program/shadowcomp.glsl")),
            ShaderSourceFile::new("lib/util/spaceConversion.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/spaceConversion.glsl")),
            ShaderSourceFile::new("lib/util/dither.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/dither.glsl")),
            ShaderSourceFile::new("shaders.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/shaders.properties")),
            ShaderSourceFile::new("block.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/block.properties")),
			ShaderSourceFile::new("item.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/item.properties")),
            ShaderSourceFile::new(
                RUNTIME_ENVIRONMENT_PATH,
                concat!(
                    "IRIS_VERSION=12000\n",
                    "MC_VERSION=12105\n",
                    "IS_IRIS=1\n",
                    "IRIS_FEATURE_CUSTOM_IMAGES=1\n",
                    "IRIS_FEATURE_SSBO=1\n",
                    "MC_OS_LINUX=1\n",
                    "MC_MIPMAP_LEVEL=4\n",
                ),
            ),
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

fn require_voxel_image(
    properties: &str,
    identity: &str,
    format: &str,
    extent: super::voxel_light_volume::VoxelLightVolumeExtent,
) -> GalResult<()> {
    let expected = format!(
        "image.{identity} = {format} {} {} {}",
        extent.width, extent.height, extent.depth
    );
    require_expression(properties, &expected)
}

fn parse_defines(
    common: &str,
    properties: &str,
    runtime_options: &BTreeMap<String, String>,
) -> BTreeMap<String, String> {
    let mut defines = BTreeMap::new();
    for line in common.lines() {
        let line = line.trim();
        let Some(rest) = line.strip_prefix("#define ") else {
            continue;
        };
        let mut values = rest.split_whitespace();
        let Some(key) = values.next() else { continue };
        if let Some(value) = values.next() {
            defines.insert(key.to_string(), value.to_string());
        }
    }
    for line in properties.lines() {
        let line = line.trim();
        let Some(rest) = line.strip_prefix("profile.MATTMC=") else {
            continue;
        };
        for entry in rest.split_whitespace() {
            let (key, value) = entry.split_once('=').unwrap_or((entry, "1"));
            defines.insert(key.to_string(), value.to_string());
        }
    }
    for (key, value) in runtime_options {
        defines.insert(key.clone(), value.clone());
    }
    defines
}

fn parse_block_properties(source: &str) -> BTreeMap<i32, Vec<String>> {
    let mut ids = BTreeMap::new();
    for line in source.lines() {
        let line = line.trim();
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let Some(id) = key
            .trim()
            .strip_prefix("block.")
            .and_then(|id| id.parse::<i32>().ok())
        else {
            continue;
        };
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

fn unsupported_features(
    source: &str,
    defines: &BTreeMap<String, String>,
) -> BTreeSet<UnsupportedTerrainFeature> {
    let mut unsupported = BTreeSet::new();
    let enabled = |key: &str| defines.get(key).map(String::as_str).unwrap_or("0") != "0";
    if source.contains("#ifdef POM") && enabled("POM") {
        unsupported.insert(UnsupportedTerrainFeature::ParallaxOcclusionMapping);
    }
    if source.contains("GENERATED_NORMALS") && enabled("GENERATED_NORMALS") {
        unsupported.insert(UnsupportedTerrainFeature::GeneratedNormals);
    }
    if source.contains("CUSTOM_PBR") && enabled("CUSTOM_PBR") {
        unsupported.insert(UnsupportedTerrainFeature::CustomPbr);
    }
    if enabled("ANISOTROPIC_FILTER") {
        unsupported.insert(UnsupportedTerrainFeature::AnisotropicFiltering);
    }
    if enabled("RAIN_PUDDLES") {
        unsupported.insert(UnsupportedTerrainFeature::RainPuddles);
    }
    unsupported
}

fn colored_voxel_lighting_setting(defines: &BTreeMap<String, String>) -> Option<u32> {
    // `common.glsl` defines both conditional branches textually. Source
    // discovery must therefore use the selected user/profile value rather
    // than the last textual `COLORED_LIGHTING_INTERNAL` branch.
    let value = defines
        .get("COLORED_LIGHTING")
        .or_else(|| defines.get("COLORED_LIGHTING_INTERNAL"))
        .map(String::as_str)
        .unwrap_or("0");
    value.parse::<u32>().ok().filter(|value| *value != 0)
}

#[cfg(test)]
mod tests {
    use super::super::source::RUNTIME_OPTIONS_PATH;
    use super::*;

    fn source() -> ShaderPackSource {
        ShaderPackSource::new("test", 1, vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "#include \"/lib/common.glsl\"\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
            ShaderSourceFile::new("lib/common.glsl", "#define SHADOW_QUALITY 2\n"),
            ShaderSourceFile::new("shaders.properties", "profile.MATTMC=SHADOW_QUALITY=2\n"),
            ShaderSourceFile::new("block.properties", "block.10009=minecraft:oak_leaves\n"),
        ]).unwrap()
    }

    fn terrain_fragment_source() -> &'static str {
        "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"
    }

    #[test]
    fn source_derived_contract_names_complementary_outputs_not_draw_buffers() {
        let contract = derive_complementary_terrain_contract(&source()).unwrap();
        assert!(contract
            .outputs
            .contains(&TerrainPassOutput::LitTerrainColor));
        assert!(contract
            .outputs
            .contains(&TerrainPassOutput::MaterialAuxiliary));
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
        assert_eq!(
            Some(&vec!["minecraft:oak_leaves".to_string()]),
            contract.material_ids.get(&10009)
        );
        assert!(!contract.inputs.contains(&TerrainPassInput::ShadowMap));
    }

    #[test]
    fn terrain_entry_discovery_uses_explicit_world_scope_not_an_active_renderer_pass() {
        let source = ShaderPackSource::new(
            "standard-layout",
            1,
            vec![
                ShaderSourceFile::new("gbuffers_terrain.fsh", terrain_fragment_source()),
                ShaderSourceFile::new("world0/gbuffers_terrain.fsh", terrain_fragment_source()),
                ShaderSourceFile::new("world-1/gbuffers_terrain.fsh", terrain_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();

        assert_eq!(
            "gbuffers_terrain.fsh",
            derive_complementary_terrain_contract(&source)
                .unwrap()
                .program_path
        );
        assert_eq!(
            "world0/gbuffers_terrain.fsh",
            derive_complementary_terrain_contract_for_scope(
                &source,
                TerrainProgramScope::Overworld
            )
            .unwrap()
            .program_path
        );
        assert_eq!(
            "world-1/gbuffers_terrain.fsh",
            derive_complementary_terrain_contract_for_scope(&source, TerrainProgramScope::Nether)
                .unwrap()
                .program_path
        );
        assert_eq!(
            "gbuffers_terrain.fsh",
            derive_complementary_terrain_contract_for_scope(&source, TerrainProgramScope::End)
                .unwrap()
                .program_path
        );
        let missing = ShaderPackSource::new(
            "missing-terrain",
            1,
            vec![
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let error =
            derive_complementary_terrain_contract_for_scope(&missing, TerrainProgramScope::End)
                .unwrap_err();
        assert!(format!("{error}").contains("missing terrain fragment source for End"));
    }

    #[test]
    fn terrain_contract_pairs_scoped_stage_files_and_shared_stage_defines() {
        let scoped = terrain_source_stages("world0/gbuffers_terrain.fsh").unwrap();
        assert_eq!("world0/gbuffers_terrain.vsh", scoped.vertex.path);
        assert!(scoped.vertex.defines.is_empty());
        assert_eq!("world0/gbuffers_terrain.fsh", scoped.fragment.path);

        let shared = terrain_source_stages("program/gbuffers_terrain.glsl").unwrap();
        assert_eq!("1", shared.vertex.defines["VERTEX_SHADER"]);
        assert_eq!("1", shared.fragment.defines["FRAGMENT_SHADER"]);
        assert!(terrain_source_stages("program/gbuffers_terrain.vert").is_err());
    }

    #[test]
    fn shadow_source_stages_are_scoped_and_never_infer_another_dimension() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new("world0/shadow.vsh", "#version 130\nvoid main() {}"),
                ShaderSourceFile::new("world0/shadow.fsh", "#version 130\nvoid main() {}"),
                ShaderSourceFile::new("world-1/shadow.vsh", "#version 130\nvoid main() {}"),
                ShaderSourceFile::new("world-1/shadow.fsh", "#version 130\nvoid main() {}"),
            ],
        )
        .unwrap();

        let overworld =
            shadow_source_stages_for_scope(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!("world0/shadow.vsh", overworld.vertex.path);
        assert_eq!("world0/shadow.fsh", overworld.fragment.path);

        let nether = shadow_source_stages_for_scope(&source, TerrainProgramScope::Nether).unwrap();
        assert_eq!("world-1/shadow.vsh", nether.vertex.path);
        assert_eq!("world-1/shadow.fsh", nether.fragment.path);

        let error = shadow_source_stages_for_scope(&source, TerrainProgramScope::End).unwrap_err();
        assert!(error.to_string().contains("missing shadow source for End"));
        assert!(error.to_string().contains("world1/shadow.fsh"));
    }

    #[test]
    fn missing_normal_terrain_output_is_rejected() {
        let source = ShaderPackSource::new(
            "bad",
            1,
            vec![
                ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void main() {}"),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        assert!(derive_complementary_terrain_contract(&source).is_err());
    }

    #[test]
    fn selected_profile_requires_a_complete_semantic_voxel_light_volume() {
        let source = ShaderPackSource::new("unsupported", 1, vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
            ShaderSourceFile::new("lib/common.glsl", "#define COLORED_LIGHTING 128\n"),
            ShaderSourceFile::new(
                "program/shadowcomp.glsl",
                "void main() { vec3 posOffset = floor(previousCameraPosition) - floor(cameraPosition); }",
            ),
            ShaderSourceFile::new("shaders.properties", "profile.MATTMC=COLORED_LIGHTING=128\nimage.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 128\nimage.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128\nimage.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128\n"),
            ShaderSourceFile::new("block.properties", ""),
        ]).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        assert!(contract.require_selected_subset().is_ok());
        assert!(contract
            .require_selected_subset_with_resources(None, 0)
            .is_err());
    }

    #[test]
    fn runtime_option_snapshot_overrides_pack_profile_defines() {
        let source = ShaderPackSource::new(
            "configured",
            1,
            vec![
                ShaderSourceFile::new("program/gbuffers_terrain.glsl", terrain_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", "#define COLORED_LIGHTING 0\n"),
                ShaderSourceFile::new(
                    "program/shadowcomp.glsl",
                    "void main() { vec3 posOffset = floor(previousCameraPosition) - floor(cameraPosition); }",
                ),
                ShaderSourceFile::new(
                    "shaders.properties",
                    "profile.MATTMC=COLORED_LIGHTING=0\nimage.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 128\nimage.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128\nimage.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128\n",
                ),
                ShaderSourceFile::new("block.properties", ""),
                ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "COLORED_LIGHTING=128\n"),
            ],
        )
        .unwrap();

        let contract = derive_complementary_terrain_contract(&source).unwrap();
        assert_eq!(
            Some(&"128".to_string()),
            contract.property_defines.get("COLORED_LIGHTING")
        );
        assert!(contract
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume));
    }

    #[test]
    fn runtime_option_snapshot_rejects_non_identifier_keys() {
        let mut files = source().files();
        files.push(ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "BAD-OPTION=1\n",
        ));
        let source = ShaderPackSource::new("bad-runtime-option", 2, files).unwrap();
        let error = derive_complementary_terrain_contract(&source).unwrap_err();
        assert!(format!("{error}").contains("not a preprocessor identifier"));
    }

    #[test]
    fn runtime_option_snapshot_rejects_non_scalar_values() {
        let mut files = source().files();
        files.push(ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "VALID_OPTION=not a token\n",
        ));
        let source = ShaderPackSource::new("bad-runtime-option", 2, files).unwrap();
        let error = derive_complementary_terrain_contract(&source).unwrap_err();
        assert!(format!("{error}").contains("not one preprocessor token"));
    }

    #[test]
    fn runtime_option_snapshot_rejects_empty_value() {
        let mut files = source().files();
        files.push(ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "VALID_OPTION=\n",
        ));
        let source = ShaderPackSource::new("empty-runtime-option", 2, files).unwrap();
        let error = derive_complementary_terrain_contract(&source).unwrap_err();
        assert!(format!("{error}").contains("not one preprocessor token"));
    }

    #[test]
    fn bundled_terrain_source_contract_does_not_reject_expanded_includes() {
        let contract = derive_complementary_terrain_contract(
            &bundled_complementary_hung_loified_source(9).unwrap(),
        )
        .unwrap();
        assert!(contract.require_selected_subset().is_ok());
    }

    #[test]
    fn bundled_complementary_volume_requirement_is_source_derived() {
        let contract = derive_complementary_terrain_contract(
            &bundled_complementary_hung_loified_source(3).unwrap(),
        )
        .unwrap();
        let requirements = contract.voxel_light_volume_requirements.unwrap();
        assert_eq!(256, requirements.extent.width);
        assert_eq!(128, requirements.extent.height);
        assert_eq!(256, requirements.extent.depth);
        assert_eq!(
            super::super::voxel_light_volume::VoxelLightVolumeFormat::OccupancyR8Uint,
            requirements.occupancy_format
        );
        assert_eq!(
            super::super::voxel_light_volume::VoxelLightVolumeFormat::LightingRgba16Float,
            requirements.lighting_format
        );
        assert!(requirements.update_policy.temporal_reprojection);
        assert!(requirements.update_policy.alternate_x_half_rate);
        assert!(requirements.update_policy.preserve_behind_view);
    }
}
