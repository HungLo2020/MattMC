use crate::render::vulkanic::error::{GalError, GalResult};

use super::manifest::{ShaderPackConfig, ShaderPackManifest};
use super::pass_graph::{AttachmentIdentity, AttachmentRole, PassGraph, PassIdentity};
use super::programs::{
    minimal_composite_color_grade_program, minimal_composite_depth_fog_program,
    minimal_deferred_lighting_program, minimal_final_copy_program, minimal_shadow_depth_program,
    minimal_terrain_cutout_program, minimal_terrain_solid_program, CompositeProgram,
    ProgramIdentity, TerrainMaterialProgram,
};

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct MaterialIdentity(String);

impl MaterialIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct SamplerIdentity(String);

impl SamplerIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ResourceGeneration(pub u64);

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackResourceManifest {
    pub generation: ResourceGeneration,
    pub programs: Vec<ProgramIdentity>,
    pub passes: Vec<PassIdentity>,
    pub attachments: Vec<AttachmentIdentity>,
    pub materials: Vec<MaterialIdentity>,
    pub samplers: Vec<SamplerIdentity>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct ShaderPackProgramSet {
    pub shadow_depth: TerrainMaterialProgram,
    pub terrain_opaque: TerrainMaterialProgram,
    pub terrain_cutout: TerrainMaterialProgram,
    pub deferred_lighting: CompositeProgram,
    pub composite_0: CompositeProgram,
    pub composite_1: CompositeProgram,
    pub final_output: CompositeProgram,
}

impl ShaderPackProgramSet {
    pub fn terrain_material_multipass_v1() -> Self {
        Self {
            shadow_depth: minimal_shadow_depth_program(),
            terrain_opaque: minimal_terrain_solid_program(),
            terrain_cutout: minimal_terrain_cutout_program(),
            deferred_lighting: minimal_deferred_lighting_program(),
            composite_0: minimal_composite_color_grade_program(),
            composite_1: minimal_composite_depth_fog_program(),
            final_output: minimal_final_copy_program(),
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct ShaderPackRuntimePlan {
    pub generation: u64,
    pub config: ShaderPackConfig,
    pub graph: PassGraph,
    pub resources: ShaderPackResourceManifest,
    pub programs: ShaderPackProgramSet,
}

impl ShaderPackRuntimePlan {
    pub fn terrain_material_multipass_v1(generation: u64) -> GalResult<Self> {
        let config = ShaderPackConfig::internal_shadow_composite_fixture(generation)?;
        let graph = config.pass_graph()?;
        Ok(Self {
            generation,
            config,
            graph,
            resources: ShaderPackResourceManifest::terrain_material_v1(generation)?,
            programs: ShaderPackProgramSet::terrain_material_multipass_v1(),
        })
    }

    pub fn declared_attachment_roles(&self) -> Vec<AttachmentRole> {
        self.config
            .attachments
            .iter()
            .map(|attachment| attachment.role)
            .collect()
    }
}

impl ShaderPackResourceManifest {
    pub fn terrain_material_v1(generation: u64) -> GalResult<Self> {
        if generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack resource generation must be non-zero",
            ));
        }
        Ok(Self {
            generation: ResourceGeneration(generation),
            programs: vec![
                ProgramIdentity::new("vulkanic:builtin/shadow_depth_v1"),
                ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
                ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
                ProgramIdentity::new("vulkanic:builtin/deferred_lighting_v1"),
                ProgramIdentity::new("vulkanic:builtin/composite_color_grade_v1"),
                ProgramIdentity::new("vulkanic:builtin/composite_depth_fog_v1"),
                ProgramIdentity::new("vulkanic:builtin/final_copy_v1"),
            ],
            passes: vec![
                PassIdentity::new("vulkanic:pass/shadow_depth"),
                PassIdentity::new("vulkanic:pass/terrain_opaque"),
                PassIdentity::new("vulkanic:pass/terrain_cutout"),
                PassIdentity::new("vulkanic:pass/deferred_lighting"),
                PassIdentity::new("vulkanic:pass/composite_0"),
                PassIdentity::new("vulkanic:pass/composite_1"),
                PassIdentity::new("vulkanic:pass/final_output"),
            ],
            attachments: vec![
                AttachmentIdentity::new("vulkanic:attachment/shadow_depth"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_albedo"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_normal"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_material_light"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_world_position"),
                AttachmentIdentity::new("vulkanic:attachment/g_buffer_depth"),
                AttachmentIdentity::new("vulkanic:attachment/deferred_lit_color"),
                AttachmentIdentity::new("vulkanic:attachment/composite_0"),
                AttachmentIdentity::new("vulkanic:attachment/composite_1"),
                AttachmentIdentity::new("vulkanic:attachment/final_color"),
            ],
            materials: vec![
                MaterialIdentity::new("minecraft:material/opaque_textured"),
                MaterialIdentity::new("minecraft:material/cutout_textured"),
            ],
            samplers: vec![SamplerIdentity::new("vulkanic:sampler/nearest_clamp")],
        })
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ActiveShaderPack {
    pub generation: u64,
    pub manifest: ShaderPackManifest,
    pub resources: ShaderPackResourceManifest,
}

#[derive(Default)]
pub struct ShaderPackResources {
    active: Option<ActiveShaderPack>,
    failed_generations: Vec<u64>,
}

impl ShaderPackResources {
    pub fn apply_generation(
        &mut self,
        generation: u64,
        manifest: ShaderPackManifest,
    ) -> GalResult<()> {
        self.apply_resource_generation(
            generation,
            manifest,
            ShaderPackResourceManifest::terrain_material_v1(generation)?,
        )
    }

    pub fn apply_resource_generation(
        &mut self,
        generation: u64,
        manifest: ShaderPackManifest,
        resources: ShaderPackResourceManifest,
    ) -> GalResult<()> {
        if generation == 0 || generation != manifest.generation() {
            self.failed_generations.push(generation);
            return Err(GalError::invalid_argument(
                "shader-pack generation must match manifest generation",
            ));
        }
        if generation != resources.generation.0 {
            self.failed_generations.push(generation);
            return Err(GalError::invalid_argument(
                "shader-pack generation must match resource generation",
            ));
        }
        if self
            .active
            .as_ref()
            .map(|active| generation <= active.generation)
            .unwrap_or(false)
        {
            self.failed_generations.push(generation);
            return Err(GalError::invalid_argument("stale shader-pack generation"));
        }
        self.active = Some(ActiveShaderPack {
            generation,
            manifest,
            resources,
        });
        Ok(())
    }

    pub fn active_generation(&self) -> Option<u64> {
        self.active.as_ref().map(|active| active.generation)
    }

    pub fn active(&self) -> Option<&ActiveShaderPack> {
        self.active.as_ref()
    }

    pub fn failed_generations(&self) -> &[u64] {
        &self.failed_generations
    }
}
