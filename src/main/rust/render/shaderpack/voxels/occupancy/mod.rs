//! Rust-owned semantic occupancy generation for shader-pack voxel volumes.
//!
//! This is deliberately a private preparation stage. It consumes copied mesh
//! semantics and emits typed volume updates; it has no Java renderer objects,
//! Iris state, backend handles, or selected-source admission side effects.

mod sampling;
mod snapshots;
mod runtime;
mod colored_light;
mod flood_fill;
mod puddles;
mod regions;

pub use self::sampling::*;
pub use self::snapshots::*;
pub use self::runtime::*;
pub use self::colored_light::*;
pub use self::flood_fill::*;
pub(crate) use self::puddles::*;
use self::regions::*;

use std::collections::BTreeMap;
use std::sync::Arc;

use crate::render::vulkanic::commands::{
    BufferImageCopyRegion, CommandOp, ResourceBarrier, TextureOrigin3d, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    AccessFlags, BufferDesc, BufferUsage, CombinedTextureSamplerDesc, ComputePipelineDesc,
    Extent3d, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, QueueClass, ResourceBinding,
    ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    SamplerAddressMode, SamplerDesc, SamplerFilter, ShaderCodeFormat, ShaderModuleDesc,
    ShaderStage, TextureDesc, TextureDimension, TextureFormat, TextureUsage, TextureViewDesc,
};
use crate::render::scene::voxel_source::TerrainVoxelSourceMesh;
use crate::render::scene::voxel_source::TerrainVoxelSourceVertex;
use crate::render::scene::mesh::WorldMeshAsset;
use crate::render::scene::mesh::WorldMeshVertex;
use crate::render::scene::mesh::WORLD_MESH_VERTEX_LAYOUT_V3;
use crate::render::scene::strata::WORLD_STRATUM_TERRAIN;

use crate::render::shaderpack::programs::shader_stage_code;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet, TerrainSourceOwnedStorageResource,
    TerrainSourceResourceAvailability, TerrainSourceResourceAvailabilitySet,
    TerrainSourceResourceRole, TerrainSourceSampledResourceShape,
};
use crate::render::shaderpack::voxels::emission_table::{VoxelEmissionTable, VOXEL_TINT_COUNT};
use crate::render::shaderpack::voxels::light_volume::{
    flood_fill_output_field_for_frame, flood_fill_source_field_for_frame, VoxelLightVolumeCache,
    VoxelLightVolumeDescriptor, VoxelLightVolumeFrameMapping, VoxelLightVolumeKind,
    VoxelLightVolumeMapping, VoxelLightVolumeReadiness, VoxelLightVolumeRegion,
    VoxelLightVolumeShaderMapping, VoxelLightVolumeTemporalMapping, VoxelLightVolumeUpdate,
    VoxelLightVolumeViewDirection,
};
use crate::render::shaderpack::voxels::material_map::VoxelMaterialMap;

#[cfg(test)]
mod tests;
