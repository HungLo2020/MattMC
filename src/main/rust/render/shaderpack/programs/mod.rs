//! Programs: the shared program model, programs lowered from a selected pack's
//! sources, and MattMC's built-in programs.

mod model;
mod lowered;
mod builtin;

pub use self::model::*;
pub use self::lowered::*;
pub use self::builtin::*;

use std::collections::BTreeSet;

use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::{
    AccessFlags, ShaderConventions, BlendMode, PipelineStageFlags, ResourceBinding, ResourceBindingDesc,
    ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc, ShaderCodeFormat, ShaderModuleDesc,
    ShaderStage,
};

use crate::render::shaderpack::contracts::cloud::{CloudBlend, CloudPassContract};
use crate::render::shaderpack::contracts::terrain::TerrainProgramScope as IdentityScope;

use crate::render::shaderpack::contracts::distant_horizons::{DistantHorizonsPassContract, DistantHorizonsPassKind};
use crate::render::shaderpack::contracts::entity::{EntityPassContract, EntitySourceDrawSemantics, EntitySourceOutput};
use crate::render::shaderpack::contracts::hand::{HandPassContract, HandSourceOutput};
use crate::render::shaderpack::lowering::{
    DistantHorizonsFragmentOutput, FullscreenSourceFragmentOutput, FullscreenSourceRasterPrimitive,
    LoweredCloudSourcePair, LoweredDistantHorizonsSourcePair, LoweredEntitySourcePair,
    LoweredFullscreenSourcePair, LoweredHandSourcePair, LoweredShadowSourcePair,
    LoweredTerrainSourcePair, LoweredTexturedMaterialSourcePair,
    LoweredTranslucentTerrainSourcePair, LoweredWeatherSourcePair,
    TerrainSourceOpaqueResourceBindingPlan, TerrainSourceOpaqueResourceKind,
    TerrainSourceUniformField,
};
use crate::render::shaderpack::contracts::material::{
    pack_textured_material_source_primitives, TexturedMaterialPassContract,
    TexturedMaterialSourcePrimitive, TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES,
};
use crate::render::shaderpack::uniforms::source::{TerrainSourceUniformFrame, TerrainSourceUniformRequirements};
use crate::render::shaderpack::contracts::terrain::{
    TerrainMaterialClass, TerrainPassContract, TerrainPassOutput, TerrainPassRequiredResource,
    TerrainTranslucentBlend, TerrainTranslucentRasterState,
};
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailabilitySet, TerrainSourceResourceRole,
};
use crate::render::shaderpack::contracts::weather::{WeatherBlend, WeatherPassContract};

#[cfg(test)]
mod tests;

use crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeReadiness;
