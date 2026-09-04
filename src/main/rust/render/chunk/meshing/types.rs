//! Meshing ABI layouts and Rust-owned state containers.
//!
//! #[repr(C)] records in this module describe the stable Java/Rust boundary. Internal builder
//! structs own native vectors and handles, while legacy benchmark-only record shapes remain here
//! so production and replay compatibility stay visibly separate.

use super::constants::MODEL_QUAD_FACING_COUNT;
use super::format::NativeFormat;
use super::profile::NativeMeshingProfile;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct QuadVertex {
    pub(super) x: f32,
    pub(super) y: f32,
    pub(super) z: f32,
    pub(super) color: i32,
    pub(super) ao: f32,
    pub(super) u: f32,
    pub(super) v: f32,
    pub(super) light: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct NativeQuad {
    pub(super) vertices: [QuadVertex; 4],
    pub(super) block_emission: u8,
    pub(super) render_type: u8,
    pub(super) ignore_mid_block: u8,
    pub(super) _padding: u8,
    pub(super) block_id: i32,
    pub(super) local_x: i32,
    pub(super) local_y: i32,
    pub(super) local_z: i32,
    pub(super) material_bits: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct FlatQuadRecord {
    pub(super) quad: NativeQuad,
    pub(super) packed_normal: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct LightBlockRecord {
    pub(super) material_bits: i32,
    pub(super) block_emission: i32,
    pub(super) block_id: i32,
    pub(super) local_x: i32,
    pub(super) local_y: i32,
    pub(super) local_z: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct FluidFaceRecord {
    pub(super) packed_normal: i32,
    pub(super) material_bits: i32,
    pub(super) block_emission: i32,
    pub(super) render_type: i32,
    pub(super) ignore_mid_block: i32,
    pub(super) block_id: i32,
    pub(super) local_x: i32,
    pub(super) local_y: i32,
    pub(super) local_z: i32,
    pub(super) face_kind: i32,
    pub(super) flip: i32,
    pub(super) origin_x: f32,
    pub(super) origin_y: f32,
    pub(super) origin_z: f32,
    pub(super) y_offset: f32,
    pub(super) heights: [f32; 4],
    pub(super) side_coords: [f32; 4],
    pub(super) uvs: [f32; 8],
    pub(super) colors: [i32; 4],
    pub(super) aos: [f32; 4],
    pub(super) lights: [i32; 4],
    pub(super) primitive_kind: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct StaticModelVertexRecord {
    pub(super) x: f32,
    pub(super) y: f32,
    pub(super) z: f32,
    pub(super) color: i32,
    pub(super) u: f32,
    pub(super) v: f32,
    pub(super) light: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct StaticModelQuadRecord {
    pub(super) vertices: [StaticModelVertexRecord; 4],
    pub(super) material_bits: i32,
    pub(super) cull_face: i32,
    pub(super) normal_face: i32,
    pub(super) packed_normal: i32,
    pub(super) block_emission: i32,
    pub(super) render_type: i32,
    pub(super) shade: i32,
    pub(super) flags: i32,
    pub(super) light_face: i32,
    pub(super) tint_index: i32,
    pub(super) has_ao: i32,
    pub(super) pass_id: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct StaticModelBlockRecord {
    pub(super) model_id: i32,
    pub(super) material_bits: i32,
    pub(super) block_emission: i32,
    pub(super) render_type: i32,
    pub(super) block_id: i32,
    pub(super) local_x: i32,
    pub(super) local_y: i32,
    pub(super) local_z: i32,
    pub(super) cull_mask: i32,
    pub(super) _padding: i32,
    pub(super) offset_x: f32,
    pub(super) offset_y: f32,
    pub(super) offset_z: f32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct NativeSectionBlockRecord {
    pub(super) state_id: i32,
    pub(super) block_id: i32,
    pub(super) local_x: i32,
    pub(super) local_y: i32,
    pub(super) local_z: i32,
    pub(super) seed_lo: i32,
    pub(super) seed_hi: i32,
    pub(super) neighbor_state_ids: [i32; 6],
    pub(super) light_words: [i32; 27],
    pub(super) neighborhood_state_ids: [i32; 27],
    // Immutable biome/color-provider samples for direct static-model tinting.
    // Kept separate from the 3x3 state neighborhood used by lighting.
    pub(super) tint_lattice: [[[i32; 4]; 4]; 4],
    pub(super) tint: i32,
    pub(super) fluid_tint: i32,
    pub(super) fluid_flow_x: f32,
    pub(super) fluid_flow_z: f32,
    pub(super) absolute_x: i32,
    pub(super) absolute_y: i32,
    pub(super) absolute_z: i32,
    pub(super) legacy_offset_x: f32,
    pub(super) legacy_offset_y: f32,
    pub(super) legacy_offset_z: f32,
    pub(super) fluid_block_id: i32,
    pub(super) flags: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct CompactSectionSnapshotHeader {
    pub(super) version: i32,
    pub(super) active_count: i32,
    pub(super) min_x: i32,
    pub(super) min_y: i32,
    pub(super) min_z: i32,
    pub(super) _padding: i32,
    pub(super) active_indices_address: u64,
    pub(super) padded_state_ids_address: u64,
    pub(super) padded_light_words_address: u64,
    pub(super) block_ids_address: u64,
    pub(super) seed_los_address: u64,
    pub(super) seed_his_address: u64,
    pub(super) tints_address: u64,
    pub(super) fluid_tints_address: u64,
    pub(super) fluid_flow_x_address: u64,
    pub(super) fluid_flow_z_address: u64,
    pub(super) fluid_block_ids_address: u64,
    pub(super) flags_address: u64,
    pub(super) tint_lattices_address: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct NativeModelSelectorEntry {
    pub(super) target_id: i32,
    pub(super) weight: i32,
}

#[derive(Clone, Debug, Default)]
pub(super) struct NativeModelSelector {
    pub(super) kind: i32,
    pub(super) entries: Vec<NativeModelSelectorEntry>,
    pub(super) total_weight: i32,
}

#[derive(Clone, Copy, Debug)]
pub(super) struct FluidSprite {
    pub(super) u0: f32,
    pub(super) u1: f32,
    pub(super) v0: f32,
    pub(super) v1: f32,
    pub(super) shrink: f32,
}

impl Default for FluidSprite {
    fn default() -> Self {
        Self {
            u0: 0.0,
            u1: 1.0,
            v0: 0.0,
            v1: 1.0,
            shrink: 0.0,
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub(super) struct NativeMeshingState {
    pub(super) selector_id: i32,
    pub(super) flags: i32,
    pub(super) material_bits: i32,
    pub(super) pass_id: i32,
    pub(super) block_emission: i32,
    #[allow(dead_code)]
    pub(super) render_type: i32,
    pub(super) block_id: i32,
    pub(super) fluid_material_bits: i32,
    pub(super) fluid_pass_id: i32,
    pub(super) fluid_block_id: i32,
    pub(super) skip_group: i32,
    pub(super) skip_mask: i32,
    pub(super) fluid_type: i32,
    pub(super) fluid_own_height: f32,
    pub(super) fluid_falling: i32,
    pub(super) offset_type: i32,
    pub(super) max_horizontal_offset: f32,
    pub(super) max_vertical_offset: f32,
    pub(super) tint_type: i32,
    pub(super) fluid_still: FluidSprite,
    pub(super) fluid_flow: FluidSprite,
    pub(super) fluid_overlay: FluidSprite,
    pub(super) fluid_overlay_valid: i32,
}

pub(super) struct NativeQuadBuffer {
    pub(super) quads: Vec<NativeQuad>,
    pub(super) encoded: Vec<u8>,
    pub(super) encoded_format: Option<NativeFormat>,
    pub(super) primitive_metadata: Vec<NativeTerrainPrimitiveMetadata>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct NativeTerrainPrimitiveMetadata {
    pub(super) primitive_kind: i32,
    pub(super) material_bits: i32,
    pub(super) block_id: i32,
    pub(super) local_x: i32,
    pub(super) local_y: i32,
    pub(super) local_z: i32,
    pub(super) render_type: i32,
    pub(super) face_kind: i32,
    pub(super) facing: i32,
    pub(super) reserved0: i32,
}

pub(super) struct NativePendingQuadBuffer {
    pub(super) quads: Vec<NativeQuad>,
    pub(super) primitive_kinds: Vec<i32>,
    pub(super) flat_quad_records: Vec<FlatQuadRecord>,
    pub(super) light_block_records: Vec<LightBlockRecord>,
    pub(super) fluid_face_records: Vec<FluidFaceRecord>,
    pub(super) static_model_block_records: Vec<StaticModelBlockRecord>,
    pub(super) packed_normals: Vec<i32>,
    pub(super) validity: Vec<u8>,
}

pub(super) struct NativeSectionMeshBuilder {
    pub(super) buffers: [NativeQuadBuffer; MODEL_QUAD_FACING_COUNT],
    pub(super) pending: [NativePendingQuadBuffer; MODEL_QUAD_FACING_COUNT],
    pub(super) counts: [usize; MODEL_QUAD_FACING_COUNT],
    pub(super) profile: NativeMeshingProfile,
    pub(super) section_pass_cache_address: u64,
    pub(super) section_pass_cache_count: usize,
    pub(super) section_pass_cache_mask: u32,
    pub(super) section_pass_cache_valid: bool,
    pub(super) fluid_sprite_mask: i32,
}

pub(super) struct NativeUpdatedQuads {
    pub(super) quads: Vec<u64>,
    pub(super) mesh_quad_count: i32,
    pub(super) index_quad_count: i32,
}
