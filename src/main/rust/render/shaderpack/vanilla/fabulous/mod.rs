//! Rust-owned external attachments for the bundled Fabulous transparency graph.
//!
//! This module only owns explicit GAL resources.  It does not select a route,
//! borrow Java/Iris targets, or submit a second frame.  The frame coordinator
//! must still route semantic draws and lower the validated post-effect before
//! these resources can make transparency available.

mod attachments;
mod pipelines;
mod copies;
mod barriers;

pub(crate) use self::attachments::*;
pub(crate) use self::pipelines::*;

use std::collections::BTreeSet;

use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureImageCopyRegion, TextureOrigin3d, TextureRowOrder, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    AccessFlags, BlendMode, BufferDesc, BufferUsage, CombinedTextureSamplerDesc, CullMode,
    Extent3d, FrontFace, GraphicsPipelineDesc, MemoryDomain, PipelineLayoutDesc,
    PipelineStageFlags, PrimitiveTopology, ResourceBinding, ResourceBindingDesc,
    ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc,
    SamplerFilter, ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension,
    TextureFormat, TextureUsage, TextureViewDesc,
};
use crate::render::vulkanic::resources::{RenderPassDesc, RenderTargetDesc};
use crate::render::shaderpack::vanilla::post_effect::executor::{
    FabulousExternalTargetInventory, VanillaPostEffectExternalTargetBinding,
};

impl FabulousAttachmentSet {

}

#[cfg(test)]
mod tests;
