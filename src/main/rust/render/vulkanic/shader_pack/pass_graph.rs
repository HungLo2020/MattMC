use crate::render::vulkanic::error::{GalError, GalResult};

use super::programs::ProgramIdentity;

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct PassIdentity(String);

impl PassIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct AttachmentIdentity(String);

impl AttachmentIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum AttachmentRole {
    ShadowDepth,
    GBufferAlbedo,
    GBufferNormal,
    GBufferMaterialLight,
    GBufferWorldPosition,
    DeferredLitColor,
    Composite0,
    Composite1,
    Depth,
    FinalColor,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LoadIntent {
    Load,
    Clear,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StoreIntent {
    Store,
    DontCare,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPassDesc {
    pub identity: PassIdentity,
    pub label: String,
    pub program: ProgramIdentity,
    pub colors: Vec<AttachmentRole>,
    pub depth: Option<AttachmentRole>,
    pub load: LoadIntent,
    pub store: StoreIntent,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PassGraph {
    passes: Vec<ShaderPassDesc>,
}

impl PassGraph {
    pub fn new(passes: Vec<ShaderPassDesc>) -> GalResult<Self> {
        if passes.is_empty() {
            return Err(GalError::invalid_argument("shader pass graph is empty"));
        }
        for pass in &passes {
            if pass.identity.as_str().trim().is_empty() {
                return Err(GalError::invalid_argument("shader pass identity is empty"));
            }
            if pass.label.trim().is_empty() {
                return Err(GalError::invalid_argument("shader pass label is empty"));
            }
            if pass.colors.is_empty() && pass.depth.is_none() {
                return Err(GalError::invalid_argument(
                    "shader pass must declare at least one color or depth attachment",
                ));
            }
            if pass.colors.contains(&AttachmentRole::Depth)
                || pass.colors.contains(&AttachmentRole::ShadowDepth)
            {
                return Err(GalError::invalid_argument(
                    "shader pass color attachment cannot be depth",
                ));
            }
            if matches!(
                pass.depth,
                Some(AttachmentRole::FinalColor)
                    | Some(AttachmentRole::GBufferAlbedo)
                    | Some(AttachmentRole::GBufferNormal)
                    | Some(AttachmentRole::GBufferMaterialLight)
                    | Some(AttachmentRole::GBufferWorldPosition)
                    | Some(AttachmentRole::DeferredLitColor)
                    | Some(AttachmentRole::Composite0)
                    | Some(AttachmentRole::Composite1)
            ) {
                return Err(GalError::invalid_argument(
                    "shader pass depth attachment must use a depth role",
                ));
            }
        }
        Ok(Self { passes })
    }

    pub fn passes(&self) -> &[ShaderPassDesc] {
        &self.passes
    }
}

pub fn builtin_terrain_material_pass_graph() -> GalResult<PassGraph> {
    PassGraph::new(vec![
        ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/shadow_depth"),
            label: "shadow depth".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/shadow_depth_v1"),
            colors: Vec::new(),
            depth: Some(AttachmentRole::ShadowDepth),
            load: LoadIntent::Clear,
            store: StoreIntent::Store,
        },
        ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/terrain_opaque"),
            label: "terrain-style opaque".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            colors: vec![
                AttachmentRole::GBufferAlbedo,
                AttachmentRole::GBufferNormal,
                AttachmentRole::GBufferMaterialLight,
                AttachmentRole::GBufferWorldPosition,
            ],
            depth: Some(AttachmentRole::Depth),
            load: LoadIntent::Clear,
            store: StoreIntent::Store,
        },
        ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/terrain_cutout"),
            label: "terrain-style cutout".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
            colors: vec![
                AttachmentRole::GBufferAlbedo,
                AttachmentRole::GBufferNormal,
                AttachmentRole::GBufferMaterialLight,
                AttachmentRole::GBufferWorldPosition,
            ],
            depth: Some(AttachmentRole::Depth),
            load: LoadIntent::Load,
            store: StoreIntent::Store,
        },
        ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/deferred_lighting"),
            label: "deferred lighting with shadow".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/deferred_lighting_v1"),
            colors: vec![AttachmentRole::DeferredLitColor],
            depth: None,
            load: LoadIntent::Clear,
            store: StoreIntent::Store,
        },
        ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/composite_0"),
            label: "composite 0 color grade".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/composite_color_grade_v1"),
            colors: vec![AttachmentRole::Composite0],
            depth: None,
            load: LoadIntent::Clear,
            store: StoreIntent::Store,
        },
        ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/composite_1"),
            label: "composite 1 depth fog".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/composite_depth_fog_v1"),
            colors: vec![AttachmentRole::Composite1],
            depth: None,
            load: LoadIntent::Clear,
            store: StoreIntent::Store,
        },
        ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/final_output"),
            label: "final output".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/final_copy_v1"),
            colors: vec![AttachmentRole::FinalColor],
            depth: None,
            load: LoadIntent::Load,
            store: StoreIntent::Store,
        },
    ])
}
