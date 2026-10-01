//! Backend-neutral preparation for Rust-owned source-derived fullscreen passes.
//!
//! This module closes the semantic gap between separately owned source color
//! targets and other owned inputs (depth, shadow, material, and pack assets),
//! then compiles/binds an explicit fullscreen pass. Command recording remains
//! separate so route selection and frame ownership cannot hide here.

mod final_output;
mod pipelines;
mod passes;
mod execution;

pub(crate) use self::final_output::*;
pub(crate) use self::pipelines::*;
pub(crate) use self::passes::*;
pub(crate) use self::execution::*;

use std::collections::BTreeMap;

use crate::render::vulkanic::resources::ShaderConventions;

use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandOp, PassAttachment, ResourceBarrier,
    TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    AccessFlags, BlendMode, BufferDesc, BufferUsage, CombinedTextureSamplerDesc, CullMode,
    GraphicsPipelineDesc, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology,
    QueueClass, RenderPassDesc, RenderTargetDesc, ResourceBinding, ResourceBindingDesc,
    ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc,
    SamplerFilter, ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension,
    TextureFormat, TextureUsage, TextureViewDesc,
};

use crate::render::shaderpack::lowering::{FullscreenSourceRasterPrimitive, TerrainSourceOpaqueResourceKind};
use crate::render::shaderpack::programs::{shader_stage_code, LoweredFullscreenSourceProgram};
use crate::render::shaderpack::resources::color_targets::{
    prepare_fullscreen_source_color_resources, resolve_fullscreen_source_color_attachments,
    source_color_clear_color, FullscreenSourceColorAttachment, ShaderPackColorBootstrapClearValues,
    ShaderPackColorFramePlan, ShaderPackColorTargetManifest, ShaderPackColorTargets,
    ShaderPackSourceColorResources,
};
use crate::render::shaderpack::resources::bindings::{TerrainSourceOwnedResourceSet, TerrainSourceResourceRole};

#[cfg(test)]
mod tests;
