//! World entry points and decoding: whole-frame and world-primitive submits
//! and world asset updates, handed to the world renderer.

mod exports;
mod whole_frame;
mod meshes;
mod first_person;
mod lod;
mod dh_boxes;
mod dh_generic_groups;
mod environment;
mod assets;
mod background;
mod mesh_assets;
mod entity_shadow_query;
mod model_rigs;
mod terrain_intake;
mod terrain_publication;
mod terrain_residency;

pub(crate) use self::whole_frame::*;
pub(crate) use self::meshes::*;
use self::first_person::*;
pub(crate) use self::lod::*;
use self::dh_boxes::*;
pub(crate) use self::environment::*;
pub(crate) use self::assets::*;
pub(crate) use self::background::*;
#[cfg(test)]
pub(crate) use self::mesh_assets::*;

use crate::render::bridge::*;
use crate::render::shaderpack::vanilla::lightmap::{VanillaLightmapFrame, VanillaLightmapInputs};
use crate::render::worldrender::frame::material_quads as world_material_semantics;
use crate::render::worldrender::features::world_text::{
    WorldTextImageAsset, WorldTextImageFormat, WorldTextQuadRequest, MAX_WORLD_TEXT_IMAGES,
    MAX_WORLD_TEXT_IMAGE_BYTES_TOTAL, WORLD_TEXT_DEPTH_NORMAL, WORLD_TEXT_DEPTH_POLYGON_OFFSET,
    WORLD_TEXT_DEPTH_SEE_THROUGH,
};
use crate::render::worldrender::WorldDistantHorizonsGenericBoxRequest;
use crate::render::worldrender::WorldFeatureCoverageFrame;
use crate::render::worldrender::WorldFirstPersonFrame;
use crate::render::worldrender::WorldLodRenderFrame;
use crate::render::worldrender::WorldShaderEnvironmentFrame;
use crate::render::worldrender::WorldVoxelVolumeFrame;
use crate::render::worldrender::WORLD_LOD_MAX_COLUMNS;
use crate::render::worldrender::WORLD_LOD_MAX_NORMAL_INDEX;
use crate::render::worldrender::WORLD_LOD_MAX_SEGMENTS_PER_COLUMN;
use crate::render::worldrender::WORLD_LOD_MAX_VERTICES_PER_SEGMENT;
use crate::render::worldrender::WORLD_LOD_MAX_VISIBLE_SEGMENTS;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_CLOUDS;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_ENTITY_MODEL;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_PARTICLES;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_TEXTURED;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_UNSPECIFIED;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_WEATHER;
use crate::render::scene::mesh::WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS;
use crate::render::scene::mesh::WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY;
use crate::render::scene::mesh::WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY;
use crate::render::scene::mesh::WORLD_MESH_SECTION_ALL;
use crate::render::scene::strata::WORLD_STRATUM_DH_GENERIC;
#[cfg(test)]
use crate::render::scene::material::WORLD_MATERIAL_ID_OPAQUE_TEXTURED;
#[cfg(test)]
use crate::render::scene::material::WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED;
#[cfg(test)]
use crate::render::scene::textures::WORLD_MATERIAL_TEXTURE_GENERATED_WHITE;
#[cfg(test)]
use crate::render::scene::strata::WORLD_STRATUM_DH_GENERIC_SSAO;
use std::collections::BTreeSet;

