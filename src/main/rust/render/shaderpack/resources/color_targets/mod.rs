//! Source-derived declarations for named shader-pack color targets.
//!
//! Shader packs express these through legacy `colortexN` declarations. Those
//! names are parsed only at this source boundary, then resolved through the
//! pack's semantic resource table. The result intentionally contains neither
//! attachment indices nor backend handles.

mod declarations;
mod scoped;
mod targets;
mod attachments;
mod resource_sets;
mod frame_plan;
mod clears;

pub use self::declarations::*;
pub(crate) use self::targets::*;
pub(crate) use self::attachments::*;
pub(crate) use self::resource_sets::*;
pub(crate) use self::frame_plan::*;
pub(crate) use self::clears::*;

use std::collections::BTreeMap;

use xxhash_rust::xxh32::xxh32;

use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureImageCopyRegion, TextureOrigin3d, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult, StatusCode};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    CombinedTextureSamplerDesc, Extent3d, QueueClass, RenderPassDesc, RenderTargetDesc,
    SamplerAddressMode, SamplerDesc, SamplerFilter, TextureDesc, TextureDimension, TextureFormat,
    TextureSubresourceRange, TextureUsage, TextureViewDesc,
};

use crate::render::shaderpack::lowering::{
    FullscreenSourceFragmentOutput, TerrainSourceOpaqueResourceBindingPlan,
    TerrainSourceOpaqueResourceKind,
};
use crate::render::shaderpack::programs::LoweredFullscreenSourceProgram;
use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::shaderpack::contracts::terrain::TerrainPassOutput;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailability,
    TerrainSourceResourceAvailabilitySet, TerrainSourceResourceBindings, TerrainSourceResourceRole,
};

#[cfg(test)]
mod tests;
