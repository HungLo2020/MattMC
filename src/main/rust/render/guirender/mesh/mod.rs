//! Rust-owned 3D GUI meshes: item models, block-item rasters and the panorama.
//!
//! Batches copied from vanilla item meshes are validated (`validation`) and
//! prepared (`prepare`) into one backend-neutral `model`; Java renderer
//! objects and backend state never cross this boundary. Prepared draws are
//! rendered by per-draw passes (`pass`) over shared programs (`program`),
//! directly into the GUI frame or into generation-scoped offscreen targets
//! (`offscreen`) that composite resources (`composite`) blit back.
//!
//! - `limits`: batch, payload, target and uniform bounds.
//! - `raster_state`: winding, culling and model-transform math of a draw.
//! - `uniforms`: bindings, barriers and uniform and index packing.
//! - `shaders`: GLSL sources (`glsl/`).

mod limits;
mod shaders;
mod model;
mod validation;
mod prepare;
mod program;
mod pass;
mod raster_state;
mod composite;
mod uniforms;
mod offscreen;

pub use self::limits::*;
use self::shaders::*;
/// GLSL sources the backend conformance tests compile directly.
#[cfg(test)]
pub(crate) use self::shaders::{
    opengl_panorama_shader_sources_for_backend_test, vulkan_panorama_shader_sources_for_backend_test,
    vulkan_shader_sources_for_backend_test,
};
pub use self::model::*;
pub use self::validation::*;
pub use self::prepare::*;
pub use self::program::*;
pub use self::pass::*;
use self::raster_state::*;
pub use self::composite::*;
use self::uniforms::*;
pub use self::offscreen::*;

use ch::Hasher;
use core::hash as ch;
use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult, StatusCode};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::guirender::frontend::GUI_MAX_VIEWPORT_AXIS;
pub use crate::render::guirender::items::material::GuiFlatItemLighting;
use crate::render::vulkanic::handles::Handle;
pub use crate::render::shared::item_foil::StandardItemFoil as GuiItemFoil;
use crate::render::vulkanic::resources::{
    AccessFlags, BlendMode, GlslDialect, BufferDesc, BufferUsage, ColorFormat, CompareOp, Extent3d,
    GraphicsPipelineDesc, IndexType, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags,
    PrimitiveTopology, QueueClass, RenderPassDesc, RenderTargetDesc, ResourceBinding,
    ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureUsage, TextureViewDesc,
};
use crate::render::vulkanic::CullMode;

#[cfg(test)]
mod tests;
