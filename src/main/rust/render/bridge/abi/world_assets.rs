//! World asset update records: borders, cracks, materials, meshes, text images and atlas animation.

use super::*;

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldBorderAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub texture_id: u32,
    pub reserved0: u32,
    pub png_bytes: FfiBytes,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldCrackAssetPayload {
    pub byte_size: u32,
    pub stage: u32,
    pub png_bytes: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldCrackAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiWorldCrackAssetPayload>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMaterialAssetPayload {
    pub byte_size: u32,
    pub texture_id: u32,
    pub png_bytes: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMaterialAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiWorldMaterialAssetPayload>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshVertex {
    pub byte_size: u32,
    pub color_argb: u32,
    pub normal_packed: u32,
    pub light: u32,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub u: f32,
    pub v: f32,
    pub atlas_u: f32,
    pub atlas_v: f32,
    pub shader_block_id: i32,
    pub shader_material_type: i32,
    /// Sodium's compact per-primitive material byte. Bit 0 selects mip
    /// sampling; bits 1..2 select the alpha-cutoff class.
    pub terrain_material_bits: u32,
    /// Copied signed-byte `at_midBlock` xyz plus the source emission byte.
    /// This is terrain-model semantics, not an Iris binding or GL state.
    pub mid_block_packed: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshSectionRecord {
    pub byte_size: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub cull_policy: u32,
    pub winding: u32,
    pub index_offset: u32,
    pub index_count: u32,
    pub source_facing: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshAssetRecord {
    pub byte_size: u32,
    pub vertex_layout_version: u32,
    pub index_type: u32,
    pub reserved0: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub vertices: FfiSlice<FfiWorldMeshVertex>,
    pub index_bytes: FfiBytes,
    pub sections: FfiSlice<FfiWorldMeshSectionRecord>,
    /// Canonical gameplay entity identity for entity-model assets. Empty means
    /// this immutable mesh is not an entity model. Rust resolves any selected
    /// source pack ID from `entity.properties`.
    pub entity_identity_utf8: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMeshAnimationFrameRecord {
    pub byte_size: u32,
    pub frame_index: u32,
    pub duration_ticks: u32,
    pub reserved0: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiSpriteAnimationMip {
    pub byte_size: u32,
    pub width: u32,
    pub height: u32,
    pub reserved0: u32,
    pub rgba: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiSpriteAnimationSource {
    pub byte_size: u32,
    pub sprite_id: u32,
    pub atlas_x: u32,
    pub atlas_y: u32,
    pub frame_width: u32,
    pub frame_height: u32,
    pub interpolate: u32,
    pub reserved0: u32,
    pub frames: FfiSlice<FfiWorldMeshAnimationFrameRecord>,
    pub mips: FfiSlice<FfiSpriteAnimationMip>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiAtlasAnimationAssetUpdate {
    pub header: FfiHeader,
    pub texture_id: u32,
    pub reserved0: u32,
    pub generation: u64,
    pub initial_tick: u64,
    pub sprites: FfiSlice<FfiSpriteAnimationSource>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMeshTextureAssetPayload {
    pub byte_size: u32,
    pub texture_id: u32,
    pub png_bytes: FfiBytes,
    pub frame_width: u32,
    pub frame_height: u32,
    pub frame_count: u32,
    pub frame_ticks: u32,
    pub animation_flags: u32,
    pub frame_row_size: u32,
    pub interpolation_policy: u32,
    pub reserved0: u32,
    pub animation_frames: FfiSlice<FfiWorldMeshAnimationFrameRecord>,
    pub mip_png_bytes: FfiSlice<FfiBytes>,
    /// Zero/zero retains the producer family's existing defaults. Otherwise
    /// filter is 1=nearest, 2=linear; address is 1=repeat, 2=clamp-to-edge.
    /// Partial or unknown descriptors are rejected before asset publication.
    pub sampling_filter: u32,
    pub sampling_address: u32,
    /// Zero retains the existing family contract; positive counts are exact.
    pub requested_mip_levels: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshSortedIndexRecord {
    pub byte_size: u32,
    pub index_type: u32,
    pub reserved0: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub index_generation: u64,
    pub index_bytes: FfiBytes,
}

/// Explicit retirement of one immutable world-mesh generation. This is a
/// backend-neutral resource-lifetime command; it is not a draw or a native
/// handle. Rust ignores a record whose generation no longer matches, so a
/// delayed eviction can never remove a newer replacement of the same key.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMeshAssetRetirementRecord {
    pub byte_size: u32,
    pub reserved0: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub meshes: FfiSlice<FfiWorldMeshAssetRecord>,
    pub textures: FfiSlice<FfiWorldMeshTextureAssetPayload>,
    pub sorted_indices: FfiSlice<FfiWorldMeshSortedIndexRecord>,
    pub negotiated_feature_bits: u64,
    pub retirements: FfiSlice<FfiWorldMeshAssetRetirementRecord>,
    pub experience_orbs: FfiSlice<FfiWorldExperienceOrbAssetRecord>,
}

/// Immutable gameplay appearance and CPU resource identity, not GPU handles
/// or caller-selected vertices, UVs, materials, or raster state.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldExperienceOrbAssetRecord {
    pub byte_size: u32,
    pub icon: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub red: u32,
    pub blue: u32,
    pub packed_light: u32,
    pub reserved0: u32,
}

/// One copied source font-atlas image. This is an immutable semantic payload,
/// not a Java texture, atlas object, or backend resource.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldTextImageAssetPayload {
    pub byte_size: u32,
    pub format: u32,
    pub width: u32,
    pub height: u32,
    pub asset_id: u64,
    pub atlas_generation: u64,
    pub atlas_revision: u64,
    pub pixels: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldTextImageUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiWorldTextImageAssetPayload>,
    pub negotiated_feature_bits: u64,
}
