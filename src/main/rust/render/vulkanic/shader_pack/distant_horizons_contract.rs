//! Source-derived semantic contract for a shader-pack's opaque Distant
//! Horizons terrain stage.
//!
//! DH terrain is intentionally distinct from ordinary terrain: selected packs
//! write their declared shared pack color target while retaining distinct
//! distant depth for later source-declared consumers. This module records only
//! that semantic contract. It does not inspect Iris,
//! compile a program, create attachments, or select a rendering route.

use std::collections::BTreeSet;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::fullscreen_contract::{derive_fullscreen_source_chain, FullscreenSourceStage};
use super::source::ShaderPackSource;
use super::terrain_contract::{
    derive_complementary_terrain_contract_for_scope, expand_contract_includes,
    source_alpha_over_blend_property, terrain_source_stages, TerrainPassRequiredResource,
    TerrainProgramScope, TerrainSourceStages, TerrainTranslucentBlend,
};
use super::{
    pass_graph::{
        distant_horizons_opaque_pass_graph, distant_horizons_translucent_pass_graph,
        AttachmentRole, PassGraph,
    },
    programs::ProgramIdentity,
};

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum DistantHorizonsPassInput {
    VertexColor,
    PackedBlockAndSkyLight,
    MaterialCategory,
    FaceNormal,
    ColumnLocalPosition,
    ColumnOrigin,
    WorldYOffset,
    DistantProjection,
    DistantProjectionInverse,
    Lightmap,
    Camera,
    DirectionalLight,
    Environment,
    Dither,
    Noise,
    /// The main-scene depth snapshot sampled by DH translucent source stages.
    /// This is intentionally distinct from the DH depth chain and from the
    /// live near-terrain depth target.
    MainDepthBeforeTranslucency,
    ColoredVoxelLightVolume,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum DistantHorizonsPassOutput {
    ShaderPackPrimaryColor,
    DistantDepth,
}

/// The source-derived material phase for one DH program. The phase is part of
/// the contract before any target, pipeline, or route is constructed, so a
/// later executor cannot accidentally submit a `dh_water` range as opaque.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum DistantHorizonsPassKind {
    Opaque,
    Translucent,
}

/// An explicit prerequisite for selecting this source. The dependency is
/// semantic rather than an attachment number: the later pass graph chooses
/// the actual target formats and transitions.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum DistantHorizonsPassDependency {
    DistantDepthConsumers,
}

/// A pack-relative fragment stage that samples one or both Distant Horizons
/// depth variants. This is a semantic source identity, never an Iris program,
/// attachment number, or backend resource.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DistantHorizonsDepthConsumer {
    pub stage_path: String,
    /// The explicit vertex/fragment source pair which owns this later
    /// consumer. It is source identity only; source execution needs a
    /// dedicated fullscreen/pass executor and remains unavailable until then.
    pub source_stages: TerrainSourceStages,
    pub reads_opaque_depth: bool,
    pub reads_depth_before_translucency: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DistantHorizonsPassContract {
    pub pass_kind: DistantHorizonsPassKind,
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub program_path: String,
    pub source_stages: TerrainSourceStages,
    pub inputs: BTreeSet<DistantHorizonsPassInput>,
    pub outputs: BTreeSet<DistantHorizonsPassOutput>,
    /// A source-declared alpha blend semantic for the translucent phase. It
    /// remains absent for opaque DH and contains no API blend factors.
    pub translucent_blend: Option<TerrainTranslucentBlend>,
    pub required_resources: BTreeSet<TerrainPassRequiredResource>,
    pub dependencies: BTreeSet<DistantHorizonsPassDependency>,
    /// Complete scoped post-terrain chain. Depth consumers below remain the
    /// narrower executable subset until every stage's resources and final
    /// target are Rust-owned.
    pub post_terrain_stages: Vec<FullscreenSourceStage>,
    pub distant_depth_consumers: Vec<DistantHorizonsDepthConsumer>,
}

impl DistantHorizonsPassContract {
    /// The contract may be discovered and validated independently, but it
    /// cannot be selected until every later selected-source pass that samples
    /// DH depth declares the corresponding semantic dependency.
    pub fn requires_distant_depth_consumers(&self) -> bool {
        self.dependencies
            .contains(&DistantHorizonsPassDependency::DistantDepthConsumers)
    }

    /// Creates the semantic source pass ordering. The returned graph does not
    /// select a route or create attachments: that belongs to the Rust
    /// shader-pack runtime after every discovered source-declared consumer is
    /// admitted.
    pub fn pass_graph(&self, terrain_program: ProgramIdentity) -> GalResult<PassGraph> {
        match self.pass_kind {
            DistantHorizonsPassKind::Opaque => distant_horizons_opaque_pass_graph(terrain_program),
            DistantHorizonsPassKind::Translucent => {
                distant_horizons_translucent_pass_graph(terrain_program)
            }
        }
    }

    /// Named output roles are deliberately separate from `DRAWBUFFERS`
    /// numbers. A backend can assign formats and attachment locations later
    /// without changing the source-derived contract.
    pub fn output_roles(&self) -> GalResult<Vec<AttachmentRole>> {
        match self.pass_kind {
            DistantHorizonsPassKind::Opaque => Ok(vec![
                AttachmentRole::ShaderPackPrimaryColor,
                AttachmentRole::DistantDepth,
            ]),
            DistantHorizonsPassKind::Translucent => {
                Ok(vec![AttachmentRole::ShaderPackPrimaryColor])
            }
        }
    }
}

pub fn derive_distant_horizons_opaque_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<DistantHorizonsPassContract> {
    let program_path = scope
        .distant_horizons_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing Distant Horizons terrain fragment source for {scope:?}; tried {}",
                scope.distant_horizons_entry_candidates().join(", ")
            ))
        })?;
    let source_stages = terrain_source_stages(program_path)?;
    let fragment = expand_contract_includes(source, &source_stages.fragment.path)?;
    let vertex = expand_contract_includes(source, &source_stages.vertex.path)?;
    require(&vertex, "dhMaterialId")?;
    require(&vertex, "GetLightMapCoordinates()")?;
    require(&vertex, "gl_NormalMatrix * gl_Normal")?;
    require(&vertex, "gl_ModelViewMatrix * gl_Vertex")?;
    require(&fragment, "vec4 color = vec4(glColor.rgb, 1.0)")?;
    require(&fragment, "mat4 gbufferProjection = dhProjection")?;
    require(
        &fragment,
        "mat4 gbufferProjectionInverse = dhProjectionInverse",
    )?;
    require(&fragment, "DoLighting(")?;
    require(&fragment, "/* DRAWBUFFERS:0 */")?;
    require(&fragment, "gl_FragData[0] = color")?;

    // Property/resource requirements are shared pack semantics, but the DH
    // vertex and output contract remains separate from normal terrain.
    let normal_terrain = derive_complementary_terrain_contract_for_scope(source, scope)?;
    let mut inputs = BTreeSet::from([
        DistantHorizonsPassInput::VertexColor,
        DistantHorizonsPassInput::PackedBlockAndSkyLight,
        DistantHorizonsPassInput::MaterialCategory,
        DistantHorizonsPassInput::FaceNormal,
        DistantHorizonsPassInput::ColumnLocalPosition,
        DistantHorizonsPassInput::ColumnOrigin,
        DistantHorizonsPassInput::WorldYOffset,
        DistantHorizonsPassInput::DistantProjection,
        DistantHorizonsPassInput::DistantProjectionInverse,
        DistantHorizonsPassInput::Lightmap,
        DistantHorizonsPassInput::Camera,
        DistantHorizonsPassInput::DirectionalLight,
        DistantHorizonsPassInput::Environment,
        DistantHorizonsPassInput::Dither,
        DistantHorizonsPassInput::Noise,
    ]);
    if normal_terrain
        .required_resources
        .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
    {
        inputs.insert(DistantHorizonsPassInput::ColoredVoxelLightVolume);
    }
    let distant_depth_consumers = distant_depth_consumers(source, scope)?;
    let post_terrain_stages = derive_fullscreen_source_chain(source, scope)?;
    let mut dependencies = BTreeSet::new();
    if !distant_depth_consumers.is_empty() {
        dependencies.insert(DistantHorizonsPassDependency::DistantDepthConsumers);
    }
    Ok(DistantHorizonsPassContract {
        pass_kind: DistantHorizonsPassKind::Opaque,
        pack_name: source.name().to_string(),
        generation: source.generation(),
        scope,
        program_path: program_path.to_string(),
        source_stages,
        inputs,
        outputs: BTreeSet::from([
            DistantHorizonsPassOutput::ShaderPackPrimaryColor,
            DistantHorizonsPassOutput::DistantDepth,
        ]),
        translucent_blend: None,
        required_resources: normal_terrain.required_resources,
        dependencies,
        post_terrain_stages,
        distant_depth_consumers,
    })
}

/// Recovers the separate Distant Horizons translucent/material-water source
/// contract. This discovery deliberately does not make the program routable:
/// `dh_water` reads a main-depth history and needs an owned late material pass
/// rather than the opaque DH target. Keeping it as an explicit contract lets
/// the frontend reject the range truthfully until that pass exists.
pub fn derive_distant_horizons_translucent_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<DistantHorizonsPassContract> {
    let program_path = scope
        .distant_horizons_translucent_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing Distant Horizons translucent fragment source for {scope:?}; tried {}",
                scope
                    .distant_horizons_translucent_entry_candidates()
                    .join(", ")
            ))
        })?;
    let source_stages = terrain_source_stages(program_path)?;
    let fragment = expand_contract_includes(source, &source_stages.fragment.path)?;
    let vertex = expand_contract_includes(source, &source_stages.vertex.path)?;
    require(&vertex, "dhMaterialId")?;
    require(&vertex, "GetLightMapCoordinates()")?;
    require(&vertex, "gl_NormalMatrix * gl_Normal")?;
    require(&vertex, "gl_ModelViewMatrix * gl_Vertex")?;
    require(&fragment, "mat4 gbufferProjection = dhProjection")?;
    require(
        &fragment,
        "mat4 gbufferProjectionInverse = dhProjectionInverse",
    )?;
    require(&fragment, "texture2D(depthtex1, screenPos.xy)")?;
    require(&fragment, "DoLighting(")?;
    require(&fragment, "DoFog(")?;
    require(&fragment, "/* DRAWBUFFERS:0 */")?;
    require(&fragment, "gl_FragData[0] = color")?;
    let blend = source_alpha_over_blend_property(source, "blend.dh_water")?.ok_or_else(|| {
        GalError::unsupported_feature(
            "selected Distant Horizons translucent source must declare blend.dh_water",
        )
    })?;

    let normal_terrain = derive_complementary_terrain_contract_for_scope(source, scope)?;
    let mut inputs = BTreeSet::from([
        DistantHorizonsPassInput::VertexColor,
        DistantHorizonsPassInput::PackedBlockAndSkyLight,
        DistantHorizonsPassInput::MaterialCategory,
        DistantHorizonsPassInput::FaceNormal,
        DistantHorizonsPassInput::ColumnLocalPosition,
        DistantHorizonsPassInput::ColumnOrigin,
        DistantHorizonsPassInput::WorldYOffset,
        DistantHorizonsPassInput::DistantProjection,
        DistantHorizonsPassInput::DistantProjectionInverse,
        DistantHorizonsPassInput::Lightmap,
        DistantHorizonsPassInput::Camera,
        DistantHorizonsPassInput::DirectionalLight,
        DistantHorizonsPassInput::Environment,
        DistantHorizonsPassInput::Dither,
        DistantHorizonsPassInput::Noise,
        DistantHorizonsPassInput::MainDepthBeforeTranslucency,
    ]);
    if normal_terrain
        .required_resources
        .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
    {
        inputs.insert(DistantHorizonsPassInput::ColoredVoxelLightVolume);
    }
    Ok(DistantHorizonsPassContract {
        pass_kind: DistantHorizonsPassKind::Translucent,
        pack_name: source.name().to_string(),
        generation: source.generation(),
        scope,
        program_path: program_path.to_string(),
        source_stages,
        inputs,
        outputs: BTreeSet::from([DistantHorizonsPassOutput::ShaderPackPrimaryColor]),
        translucent_blend: Some(blend),
        required_resources: normal_terrain.required_resources,
        dependencies: BTreeSet::new(),
        post_terrain_stages: Vec::new(),
        distant_depth_consumers: Vec::new(),
    })
}

/// DH depth is not a separate color-composite source. It is an independent
/// semantic input only when a later executable pack stage actually samples it.
/// Uniform declarations and shared include bodies do not make consumers: this
/// scan expands concrete world fragment entries and records their exact paths.
/// It is intentionally conservative about conditional branches; a later
/// source executor must admit every recorded consumer before selecting DH.
fn distant_depth_consumers(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<Vec<DistantHorizonsDepthConsumer>> {
    let mut consumers = Vec::new();
    for file in source.files() {
        if !is_distant_depth_consumer_stage(&file.path)
            || !belongs_to_scope(&file.path, scope)
            || file.path.contains("dh_terrain")
            || file.path.contains("dh_water")
        {
            continue;
        }
        let expanded = expand_contract_includes(source, &file.path)?;
        let reads_opaque_depth = samples_distant_depth(&expanded, "dhDepthTex");
        let reads_depth_before_translucency = samples_distant_depth(&expanded, "dhDepthTex1");
        if reads_opaque_depth || reads_depth_before_translucency {
            consumers.push(DistantHorizonsDepthConsumer {
                source_stages: terrain_source_stages(&file.path)?,
                stage_path: file.path,
                reads_opaque_depth,
                reads_depth_before_translucency,
            });
        }
    }
    Ok(consumers)
}

/// Source packs can carry every dimension's world programs at once. A DH
/// depth stream is dimension-local, so only later stages in the selected
/// program scope may become dependencies of that contract. Pulling a Nether
/// or End consumer into an Overworld plan makes the plan impossible to admit
/// even though the active source pair is otherwise complete.
fn belongs_to_scope(path: &str, scope: TerrainProgramScope) -> bool {
    match scope {
        TerrainProgramScope::Default => !path.starts_with("world"),
        TerrainProgramScope::Overworld => path.starts_with("world0/"),
        TerrainProgramScope::Nether => path.starts_with("world-1/"),
        TerrainProgramScope::End => path.starts_with("world1/"),
    }
}

fn is_distant_depth_consumer_stage(path: &str) -> bool {
    if !path.ends_with(".fsh")
        || !(path.starts_with("world0/")
            || path.starts_with("world1/")
            || path.starts_with("world-1/")
            || path.starts_with("world-2/"))
    {
        return false;
    }
    let Some(file_name) = path.rsplit('/').next() else {
        return false;
    };
    // DH contributes after the ordinary geometry programs. A geometry-stage
    // reference (for example `gbuffers_entities`) is a future producer
    // migration dependency, not a post-DH composition dependency. Retaining
    // it here would make the DH pass falsely require unrelated Java/Iris
    // vertex semantics before the source-owned fullscreen chain can run.
    file_name.starts_with("deferred")
        || file_name.starts_with("composite")
        || file_name.starts_with("final")
}

fn samples_distant_depth(source: &str, sampler: &str) -> bool {
    ["texelFetch(", "texture(", "texture2D("]
        .iter()
        .any(|sample| source.contains(&format!("{sample}{sampler}")))
}

fn require(source: &str, expression: &str) -> GalResult<()> {
    if source.contains(expression) {
        Ok(())
    } else {
        Err(GalError::invalid_argument(format!(
            "Distant Horizons terrain source is missing required expression {expression}"
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test;
    use crate::render::vulkanic::shader_pack::source::{ShaderSourceFile, RUNTIME_OPTIONS_PATH};

    fn source_with_runtime_options(distant_horizons: bool) -> ShaderPackSource {
        let source = complete_bundled_pack_source_for_test();
        ShaderPackSource::new(
            "ComplementaryHungLoIfied-dh-water-runtime-options",
            92,
            source
                .files()
                .into_iter()
                .chain(std::iter::once(ShaderSourceFile::new(
                    RUNTIME_OPTIONS_PATH,
                    format!(
                        "{}SHADOW_QUALITY=-1\nFXAA_DEFINE=-1\nCOLORED_LIGHTING=0\nENTITY_SHADOWS_DEFINE=-1\nPLAYER_SHADOW=-1\nRAIN_PUDDLES=0\n",
                        if distant_horizons {
                            "DISTANT_HORIZONS=1\n"
                        } else {
                            ""
                        },
                    ),
                )))
                .collect(),
        )
        .unwrap()
    }

    fn source_with_distant_horizons_enabled() -> ShaderPackSource {
        source_with_runtime_options(true)
    }

    #[test]
    fn bundled_complementary_dh_contract_keeps_shared_color_and_distant_depth_explicit() {
        let source = complete_bundled_pack_source_for_test();
        let contract =
            derive_distant_horizons_opaque_contract(&source, TerrainProgramScope::Overworld)
                .unwrap();
        assert_eq!("world0/dh_terrain.fsh", contract.program_path);
        assert_eq!("world0/dh_terrain.vsh", contract.source_stages.vertex.path);
        assert!(contract
            .inputs
            .contains(&DistantHorizonsPassInput::Lightmap));
        assert!(contract
            .inputs
            .contains(&DistantHorizonsPassInput::MaterialCategory));
        assert!(contract
            .outputs
            .contains(&DistantHorizonsPassOutput::ShaderPackPrimaryColor));
        assert!(contract
            .outputs
            .contains(&DistantHorizonsPassOutput::DistantDepth));
        assert!(contract.requires_distant_depth_consumers());
        assert_eq!(
            "world0/deferred1.fsh",
            contract.post_terrain_stages[0].stage_path
        );
        assert_eq!(
            "world0/final.fsh",
            contract.post_terrain_stages.last().unwrap().stage_path
        );
        assert!(contract.distant_depth_consumers.iter().any(|consumer| {
            consumer.stage_path == "world0/deferred1.fsh" && consumer.reads_opaque_depth
        }));
        assert!(contract.distant_depth_consumers.iter().any(|consumer| {
            consumer.stage_path == "world0/composite.fsh"
                && consumer.reads_opaque_depth
                && consumer.reads_depth_before_translucency
        }));
        assert!(contract
            .distant_depth_consumers
            .iter()
            .all(|consumer| consumer.stage_path.starts_with("world0/")));
        assert!(contract
            .distant_depth_consumers
            .iter()
            .all(|consumer| !consumer.stage_path.contains("gbuffers_entities")));
    }

    #[test]
    fn dh_contract_never_falls_back_to_ordinary_terrain_source() {
        let source = complete_bundled_pack_source_for_test();
        let missing = ShaderPackSource::new(
            "ComplementaryHungLoIfied-no-dh",
            8,
            source
                .files()
                .into_iter()
                .filter(|file| !file.path.contains("dh_terrain"))
                .collect(),
        )
        .unwrap();
        let error =
            derive_distant_horizons_opaque_contract(&missing, TerrainProgramScope::Overworld)
                .unwrap_err();
        assert!(error
            .to_string()
            .contains("missing Distant Horizons terrain fragment source"));
    }

    #[test]
    fn dh_contract_discovers_depth_consumers_from_sampling_not_uniform_declarations() {
        let source = complete_bundled_pack_source_for_test();
        let without_sampling = ShaderPackSource::new(
            "ComplementaryHungLoIfied-no-dh-depth-consumers",
            8,
            source
                .files()
                .into_iter()
                .map(|mut file| {
                    if !file.path.contains("dh_terrain") && !file.path.contains("dh_water") {
                        file.contents = file.contents.replace("dhDepthTex", "notDhDepthTex");
                    }
                    file
                })
                .collect(),
        )
        .unwrap();
        let contract = derive_distant_horizons_opaque_contract(
            &without_sampling,
            TerrainProgramScope::Overworld,
        )
        .unwrap();
        assert!(!contract.requires_distant_depth_consumers());
        assert!(contract.distant_depth_consumers.is_empty());
        assert_eq!(
            1,
            contract
                .pass_graph(ProgramIdentity::new("pack:world0/dh_terrain"))
                .unwrap()
                .passes()
                .len()
        );
    }

    #[test]
    fn dh_contract_writes_shared_color_and_retains_distant_depth() {
        let source = complete_bundled_pack_source_for_test();
        let contract =
            derive_distant_horizons_opaque_contract(&source, TerrainProgramScope::Overworld)
                .unwrap();
        assert_eq!(
            vec![
                AttachmentRole::ShaderPackPrimaryColor,
                AttachmentRole::DistantDepth
            ],
            contract.output_roles().unwrap()
        );
        let graph = contract
            .pass_graph(ProgramIdentity::new("pack:world0/dh_terrain"))
            .unwrap();
        assert_eq!(1, graph.passes().len());
        assert_eq!(
            "vulkanic:pass/distant_horizons_opaque",
            graph.passes()[0].identity.as_str()
        );
        assert_eq!(
            vec![AttachmentRole::ShaderPackPrimaryColor],
            graph.passes()[0].colors
        );
        assert_eq!(Some(AttachmentRole::DistantDepth), graph.passes()[0].depth);
    }

    #[test]
    fn dh_water_contract_keeps_dh_translucency_separate_from_near_terrain() {
        let source = source_with_distant_horizons_enabled();
        let contract =
            derive_distant_horizons_translucent_contract(&source, TerrainProgramScope::Overworld)
                .unwrap();

        assert_eq!(DistantHorizonsPassKind::Translucent, contract.pass_kind);
        assert_eq!("world0/dh_water.fsh", contract.program_path);
        assert_eq!("world0/dh_water.vsh", contract.source_stages.vertex.path);
        assert_eq!(
            Some(TerrainTranslucentBlend::SourceAlphaOver),
            contract.translucent_blend
        );
        assert!(contract
            .inputs
            .contains(&DistantHorizonsPassInput::MainDepthBeforeTranslucency));
        assert_eq!(
            vec![AttachmentRole::ShaderPackPrimaryColor],
            contract.output_roles().unwrap()
        );
        assert!(!contract
            .outputs
            .contains(&DistantHorizonsPassOutput::DistantDepth));
        let graph = contract
            .pass_graph(ProgramIdentity::new("pack:world0/dh_water"))
            .unwrap();
        assert_eq!(1, graph.passes().len());
        assert_eq!(
            "vulkanic:pass/distant_horizons_translucent",
            graph.passes()[0].identity.as_str()
        );
        assert_eq!(
            vec![AttachmentRole::ShaderPackPrimaryColor],
            graph.passes()[0].colors
        );
        assert_eq!(Some(AttachmentRole::DistantDepth), graph.passes()[0].depth);
    }

    #[test]
    fn dh_water_contract_requires_the_selected_source_blend_property() {
        let source = source_with_runtime_options(false);
        let error =
            derive_distant_horizons_translucent_contract(&source, TerrainProgramScope::Overworld)
                .unwrap_err();
        assert!(error.to_string().contains("blend.dh_water"));
    }

    #[test]
    fn dh_water_contract_never_falls_back_to_dh_terrain() {
        let source = source_with_distant_horizons_enabled();
        let missing = ShaderPackSource::new(
            "ComplementaryHungLoIfied-no-dh-water",
            93,
            source
                .files()
                .into_iter()
                .filter(|file| !file.path.contains("dh_water"))
                .collect(),
        )
        .unwrap();
        let error =
            derive_distant_horizons_translucent_contract(&missing, TerrainProgramScope::Overworld)
                .unwrap_err();
        assert!(error
            .to_string()
            .contains("missing Distant Horizons translucent fragment source"));
    }
}
