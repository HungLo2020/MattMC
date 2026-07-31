use crate::render::vulkanic::error::{GalError, GalResult};

use super::manifest::ShaderPackManifest;
use super::pass_graph::{AttachmentIdentity, PassIdentity};
use super::programs::ProgramIdentity;

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
                ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
                ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
                ProgramIdentity::new("vulkanic:builtin/final_copy_v1"),
            ],
            passes: vec![
                PassIdentity::new("vulkanic:pass/terrain_opaque"),
                PassIdentity::new("vulkanic:pass/terrain_cutout"),
                PassIdentity::new("vulkanic:pass/final_composite_copy"),
            ],
            attachments: vec![
                AttachmentIdentity::new("vulkanic:attachment/world_material_color"),
                AttachmentIdentity::new("vulkanic:attachment/world_material_depth"),
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
