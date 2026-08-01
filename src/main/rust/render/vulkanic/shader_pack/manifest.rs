use crate::render::vulkanic::error::{GalError, GalResult};

use std::collections::BTreeSet;

use super::pass_graph::{
    AttachmentIdentity, AttachmentRole, LoadIntent, PassGraph, PassIdentity, ShaderPassDesc,
    StoreIntent,
};
use super::programs::ProgramIdentity;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackManifest {
    name: String,
    generation: u64,
    programs: Vec<ProgramIdentity>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShaderAttachmentFormat {
    Rgba8Unorm,
    Depth32Float,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShaderAttachmentExtent {
    Frame,
    ShadowMap,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderAttachmentDecl {
    pub identity: AttachmentIdentity,
    pub role: AttachmentRole,
    pub format: ShaderAttachmentFormat,
    pub extent: ShaderAttachmentExtent,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderProgramDecl {
    pub identity: ProgramIdentity,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderSamplerDecl {
    pub identity: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderUniformDecl {
    pub identity: String,
    pub byte_size: u64,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPassConfig {
    pub identity: PassIdentity,
    pub program: ProgramIdentity,
    pub enabled: bool,
    pub reads: Vec<AttachmentRole>,
    pub colors: Vec<AttachmentRole>,
    pub depth: Option<AttachmentRole>,
    pub load: LoadIntent,
    pub store: StoreIntent,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackConfig {
    pub schema_version: u32,
    pub generation: u64,
    pub attachments: Vec<ShaderAttachmentDecl>,
    pub programs: Vec<ShaderProgramDecl>,
    pub passes: Vec<ShaderPassConfig>,
    pub samplers: Vec<ShaderSamplerDecl>,
    pub uniforms: Vec<ShaderUniformDecl>,
}

impl ShaderPackConfig {
    pub const SCHEMA_VERSION: u32 = 1;

    pub fn internal_shadow_composite_fixture(generation: u64) -> GalResult<Self> {
        let config = Self {
            schema_version: Self::SCHEMA_VERSION,
            generation,
            attachments: vec![
                attachment(
                    "vulkanic:attachment/shadow_depth",
                    AttachmentRole::ShadowDepth,
                    ShaderAttachmentFormat::Depth32Float,
                    ShaderAttachmentExtent::ShadowMap,
                ),
                attachment(
                    "vulkanic:attachment/g_buffer_albedo",
                    AttachmentRole::GBufferAlbedo,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/g_buffer_normal",
                    AttachmentRole::GBufferNormal,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/g_buffer_material_light",
                    AttachmentRole::GBufferMaterialLight,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/g_buffer_world_position",
                    AttachmentRole::GBufferWorldPosition,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/g_buffer_depth",
                    AttachmentRole::Depth,
                    ShaderAttachmentFormat::Depth32Float,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/deferred_lit_color",
                    AttachmentRole::DeferredLitColor,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/composite_0",
                    AttachmentRole::Composite0,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/composite_1",
                    AttachmentRole::Composite1,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
                attachment(
                    "vulkanic:attachment/final_color",
                    AttachmentRole::FinalColor,
                    ShaderAttachmentFormat::Rgba8Unorm,
                    ShaderAttachmentExtent::Frame,
                ),
            ],
            programs: vec![
                program("vulkanic:builtin/shadow_depth_v1"),
                program("vulkanic:builtin/terrain_opaque_v1"),
                program("vulkanic:builtin/terrain_cutout_v1"),
                program("vulkanic:builtin/deferred_lighting_v1"),
                program("vulkanic:builtin/composite_color_grade_v1"),
                program("vulkanic:builtin/composite_depth_fog_v1"),
                program("vulkanic:builtin/final_copy_v1"),
            ],
            passes: vec![
                pass(
                    "vulkanic:pass/shadow_depth",
                    "vulkanic:builtin/shadow_depth_v1",
                    true,
                    vec![],
                    vec![],
                    Some(AttachmentRole::ShadowDepth),
                    LoadIntent::Clear,
                    StoreIntent::Store,
                ),
                pass(
                    "vulkanic:pass/terrain_opaque",
                    "vulkanic:builtin/terrain_opaque_v1",
                    true,
                    vec![],
                    vec![
                        AttachmentRole::GBufferAlbedo,
                        AttachmentRole::GBufferNormal,
                        AttachmentRole::GBufferMaterialLight,
                        AttachmentRole::GBufferWorldPosition,
                    ],
                    Some(AttachmentRole::Depth),
                    LoadIntent::Clear,
                    StoreIntent::Store,
                ),
                pass(
                    "vulkanic:pass/terrain_cutout",
                    "vulkanic:builtin/terrain_cutout_v1",
                    true,
                    vec![],
                    vec![
                        AttachmentRole::GBufferAlbedo,
                        AttachmentRole::GBufferNormal,
                        AttachmentRole::GBufferMaterialLight,
                        AttachmentRole::GBufferWorldPosition,
                    ],
                    Some(AttachmentRole::Depth),
                    LoadIntent::Load,
                    StoreIntent::Store,
                ),
                pass(
                    "vulkanic:pass/deferred_lighting",
                    "vulkanic:builtin/deferred_lighting_v1",
                    true,
                    vec![
                        AttachmentRole::GBufferAlbedo,
                        AttachmentRole::GBufferNormal,
                        AttachmentRole::GBufferMaterialLight,
                        AttachmentRole::GBufferWorldPosition,
                        AttachmentRole::Depth,
                        AttachmentRole::ShadowDepth,
                    ],
                    vec![AttachmentRole::DeferredLitColor],
                    None,
                    LoadIntent::Clear,
                    StoreIntent::Store,
                ),
                pass(
                    "vulkanic:pass/composite_0",
                    "vulkanic:builtin/composite_color_grade_v1",
                    true,
                    vec![AttachmentRole::DeferredLitColor],
                    vec![AttachmentRole::Composite0],
                    None,
                    LoadIntent::Clear,
                    StoreIntent::Store,
                ),
                pass(
                    "vulkanic:pass/composite_1",
                    "vulkanic:builtin/composite_depth_fog_v1",
                    true,
                    vec![
                        AttachmentRole::Composite0,
                        AttachmentRole::GBufferWorldPosition,
                        AttachmentRole::Depth,
                    ],
                    vec![AttachmentRole::Composite1],
                    None,
                    LoadIntent::Clear,
                    StoreIntent::Store,
                ),
                pass(
                    "vulkanic:pass/final_output",
                    "vulkanic:builtin/final_copy_v1",
                    true,
                    vec![AttachmentRole::Composite1],
                    vec![AttachmentRole::FinalColor],
                    None,
                    LoadIntent::Load,
                    StoreIntent::Store,
                ),
            ],
            samplers: vec![ShaderSamplerDecl {
                identity: "vulkanic:sampler/nearest_clamp".to_string(),
            }],
            uniforms: vec![ShaderUniformDecl {
                identity: "vulkanic:uniforms/deferred_composite_v1".to_string(),
                byte_size: 112,
            }],
        };
        config.validate()?;
        Ok(config)
    }

    pub fn validate(&self) -> GalResult<()> {
        if self.schema_version != Self::SCHEMA_VERSION {
            return Err(GalError::invalid_argument(
                "unsupported shader-pack config schema",
            ));
        }
        if self.generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack config generation must be non-zero",
            ));
        }
        let mut attachment_roles = BTreeSet::new();
        let mut attachment_ids = BTreeSet::new();
        for attachment in &self.attachments {
            if !attachment_ids.insert(attachment.identity.as_str().to_string()) {
                return Err(GalError::invalid_argument(
                    "duplicate shader attachment identity",
                ));
            }
            if !attachment_roles.insert(attachment.role) {
                return Err(GalError::invalid_argument(
                    "duplicate shader attachment role",
                ));
            }
        }
        let mut programs = BTreeSet::new();
        for program in &self.programs {
            if !programs.insert(program.identity.as_str().to_string()) {
                return Err(GalError::invalid_argument(
                    "duplicate shader program identity",
                ));
            }
        }
        let mut pass_ids = BTreeSet::new();
        let mut written = BTreeSet::new();
        for pass in self.passes.iter().filter(|pass| pass.enabled) {
            if !pass_ids.insert(pass.identity.as_str().to_string()) {
                return Err(GalError::invalid_argument("duplicate shader pass identity"));
            }
            if !programs.contains(pass.program.as_str()) {
                return Err(GalError::invalid_argument(
                    "shader pass references missing program",
                ));
            }
            for input in &pass.reads {
                if !attachment_roles.contains(input) {
                    return Err(GalError::invalid_argument(
                        "shader pass reads missing attachment",
                    ));
                }
                if !written.contains(input) {
                    return Err(GalError::invalid_argument(
                        "shader pass reads attachment before it is written",
                    ));
                }
            }
            for output in pass.colors.iter().copied().chain(pass.depth) {
                if !attachment_roles.contains(&output) {
                    return Err(GalError::invalid_argument(
                        "shader pass writes missing attachment",
                    ));
                }
                if !written.insert(output) {
                    if pass.load != LoadIntent::Load {
                        return Err(GalError::invalid_argument(
                            "shader attachment has multiple unsynchronized writers",
                        ));
                    }
                }
            }
        }
        PassGraph::new(
            self.passes
                .iter()
                .filter(|pass| pass.enabled)
                .map(|pass| ShaderPassDesc {
                    identity: pass.identity.clone(),
                    label: pass.identity.as_str().to_string(),
                    program: pass.program.clone(),
                    colors: pass.colors.clone(),
                    depth: pass.depth,
                    load: pass.load,
                    store: pass.store,
                })
                .collect(),
        )?;
        Ok(())
    }

    pub fn pass_graph(&self) -> GalResult<PassGraph> {
        self.validate()?;
        PassGraph::new(
            self.passes
                .iter()
                .filter(|pass| pass.enabled)
                .map(|pass| ShaderPassDesc {
                    identity: pass.identity.clone(),
                    label: pass.identity.as_str().to_string(),
                    program: pass.program.clone(),
                    colors: pass.colors.clone(),
                    depth: pass.depth,
                    load: pass.load,
                    store: pass.store,
                })
                .collect(),
        )
    }
}

fn attachment(
    identity: &str,
    role: AttachmentRole,
    format: ShaderAttachmentFormat,
    extent: ShaderAttachmentExtent,
) -> ShaderAttachmentDecl {
    ShaderAttachmentDecl {
        identity: AttachmentIdentity::new(identity),
        role,
        format,
        extent,
    }
}

fn program(identity: &str) -> ShaderProgramDecl {
    ShaderProgramDecl {
        identity: ProgramIdentity::new(identity),
    }
}

fn pass(
    identity: &str,
    program: &str,
    enabled: bool,
    reads: Vec<AttachmentRole>,
    colors: Vec<AttachmentRole>,
    depth: Option<AttachmentRole>,
    load: LoadIntent,
    store: StoreIntent,
) -> ShaderPassConfig {
    ShaderPassConfig {
        identity: PassIdentity::new(identity),
        program: ProgramIdentity::new(program),
        enabled,
        reads,
        colors,
        depth,
        load,
        store,
    }
}

impl ShaderPackManifest {
    pub fn new(
        name: impl Into<String>,
        generation: u64,
        programs: Vec<ProgramIdentity>,
    ) -> GalResult<Self> {
        let name = name.into();
        if name.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack manifest name is empty",
            ));
        }
        if generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack manifest generation must be non-zero",
            ));
        }
        if programs.is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack manifest must declare at least one program",
            ));
        }
        Ok(Self {
            name,
            generation,
            programs,
        })
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn programs(&self) -> &[ProgramIdentity] {
        &self.programs
    }
}
