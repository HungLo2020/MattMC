use std::slice;
use std::sync::{Mutex, OnceLock};
use std::time::Instant;

use super::{index, translucent};

mod assembly;
mod builder;
mod cache;
mod culling;
mod ffi;
mod fluid;
mod format;
mod lighting;
mod model;
mod packing;
mod quad;
mod scan;
mod tint;
mod updates;

pub use ffi::*;
pub(crate) use updates::updated_quads_create_from_handles;

use assembly::*;
use builder::*;
use cache::*;
use culling::*;
use fluid::{
    emit_native_section_fluid_faces, native_fluid_diag_enabled,
    section_builder_append_fluid_face_records_encoded,
};
#[cfg(test)]
use fluid::{flowing_top_trig_for_test, fluid_face_record_to_quad, fluid_semantic_face};
use format::*;
use lighting::*;
use model::*;
use packing::*;
use quad::*;
use scan::*;
use tint::*;
use updates::*;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

const MODEL_QUAD_FACING_COUNT: usize = 7;
const MODEL_QUAD_FACING_POS_X: usize = 0;
const MODEL_QUAD_FACING_POS_Y: usize = 1;
const MODEL_QUAD_FACING_POS_Z: usize = 2;
const MODEL_QUAD_FACING_NEG_X: usize = 3;
const MODEL_QUAD_FACING_NEG_Y: usize = 4;
const MODEL_QUAD_FACING_NEG_Z: usize = 5;
const MODEL_QUAD_FACING_UNASSIGNED: usize = 6;
const FLUID_ALIGNED_EQUALS_EPSILON: f32 = 0.011;
const FLUID_FACE_TOP_NE_SW: i32 = 0;
const FLUID_FACE_TOP_NW_SE: i32 = 1;
const FLUID_FACE_BOTTOM: i32 = 2;
const FLUID_FACE_SIDE: i32 = 3;
const NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID: i32 = 1;
const POSITION_MAX_VALUE: f32 = (1 << 20) as f32;
const TEXTURE_MAX_VALUE: f32 = (1 << 15) as f32;
const MODEL_ORIGIN: f32 = 8.0;
const MODEL_RANGE: f32 = 32.0;
const INDEX_MODE_NONE: i32 = 0;
const INDEX_MODE_SHARED: i32 = 1;
const INDEX_MODE_SORTED_QUADS: i32 = 2;
const INDEX_MODE_KEY_SORTED: i32 = 3;
const PENDING_BATCH_QUAD_CAPACITY: usize = 256;
const STATIC_MODEL_EMPTY_RECORD_ID: i32 = -1;
const STATIC_MODEL_LIGHT_BLOCK_RECORD_ID: i32 = -2;
const STATE_FLAG_AIR: i32 = 1;
const STATE_FLAG_MODEL: i32 = 1 << 1;
const STATE_FLAG_FLUID: i32 = 1 << 2;
const STATE_FLAG_SOLID_RENDER: i32 = 1 << 3;
const STATE_FLAG_FULL_OCCLUSION: i32 = 1 << 4;
const STATE_FLAG_LIGHT_BLOCK: i32 = 1 << 5;
const STATE_FLAG_CAN_OCCLUDE: i32 = 1 << 7;
const STATE_FLAG_BLOCKS_MOTION: i32 = 1 << 8;
const MODEL_QUAD_FLAG_PARTIAL: i32 = 1;
const MODEL_QUAD_FLAG_PARALLEL: i32 = 1 << 1;
const MODEL_QUAD_FLAG_ALIGNED: i32 = 1 << 2;
const TINT_NONE: i32 = 0;
const TINT_GRASS: i32 = 1;
const TINT_FOLIAGE: i32 = 2;
const TINT_WATER: i32 = 3;
const TINT_REDSTONE: i32 = 4;
const TINT_CONSTANT: i32 = 5;
const TINT_STEM: i32 = 6;
const TINT_DOUBLE_PLANT_GRASS: i32 = 7;
const TINT_SPRUCE: i32 = 8;
const TINT_BIRCH: i32 = 9;
const TINT_FORCE_GRASS: i32 = 10;
const FLUID_WATER: i32 = 1;
const FLUID_LAVA: i32 = 2;
const FLUID_SPRITE_WATER_STILL: i32 = 1;
const FLUID_SPRITE_WATER_FLOW: i32 = 1 << 1;
const FLUID_SPRITE_WATER_OVERLAY: i32 = 1 << 2;
const FLUID_SPRITE_LAVA_STILL: i32 = 1 << 8;
const FLUID_SPRITE_LAVA_FLOW: i32 = 1 << 9;
const OFFSET_NONE: i32 = 0;
const OFFSET_XZ: i32 = 1;
const OFFSET_XYZ: i32 = 2;
const LIGHT_FULL_BRIGHT: i32 = 0x00f0_00f0;
const SELECTOR_DIRECT: i32 = 0;
const SELECTOR_WEIGHTED: i32 = 1;
const SELECTOR_GROUP: i32 = 2;

const COMPACT_VALUE_STRIDE: i32 = 0;
const COMPACT_VALUE_POSITION_OFFSET: i32 = 1;
const COMPACT_VALUE_COLOR_OFFSET: i32 = 2;
const COMPACT_VALUE_TEXTURE_OFFSET: i32 = 3;
const COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET: i32 = 4;
const COMPACT_VALUE_BLOCK_ID_OFFSET: i32 = 5;
const COMPACT_VALUE_NORMAL_OFFSET: i32 = 6;
const COMPACT_VALUE_TANGENT_OFFSET: i32 = 7;
const COMPACT_VALUE_MID_UV_OFFSET: i32 = 8;
const COMPACT_VALUE_MID_BLOCK_OFFSET: i32 = 9;
const COMPACT_VALUE_POSITION_MAX_VALUE: i32 = 10;
const COMPACT_VALUE_TEXTURE_MAX_VALUE: i32 = 11;

const COMPACT_VERTEX_STRIDE: i32 = 20;
const COMPACT_POSITION_OFFSET: i32 = 0;
const COMPACT_COLOR_OFFSET: i32 = 8;
const COMPACT_TEXTURE_OFFSET: i32 = 12;
const COMPACT_LIGHT_MATERIAL_INDEX_OFFSET: i32 = 16;
const COMPACT_NATIVE_BLOCK_ID_OFFSET: i32 = 0;
const COMPACT_NATIVE_NORMAL_OFFSET: i32 = 0;
const COMPACT_NATIVE_TANGENT_OFFSET: i32 = 0;
const COMPACT_NATIVE_MID_UV_OFFSET: i32 = 0;
const COMPACT_NATIVE_MID_BLOCK_OFFSET: i32 = 0;

const PROFILE_STAGE_COUNT: usize = 56;
const PROFILE_COUNT_COUNT: usize = 24;
const PROFILE_EXPORT_LONGS: usize = PROFILE_STAGE_COUNT + PROFILE_COUNT_COUNT;
const PROFILE_SECTION_SCAN: usize = 0;
const PROFILE_MODEL_LOOKUP_EMIT: usize = 1;
const PROFILE_FLUID_VIS_HEIGHT: usize = 2;
const PROFILE_FLUID_GEOM_UV: usize = 3;
#[allow(dead_code)]
const PROFILE_LIGHT_AO_TINT: usize = 4;
const PROFILE_MATERIAL_PASS: usize = 5;
const PROFILE_QUAD_STAGING: usize = 6;
const PROFILE_TRANSLUCENT_INGEST: usize = 7;
#[allow(dead_code)]
const PROFILE_TRANSLUCENT_METADATA: usize = 8;
#[allow(dead_code)]
const PROFILE_SORTING: usize = 9;
const PROFILE_VERTEX_PACKING: usize = 10;
#[allow(dead_code)]
const PROFILE_INDEX_EMISSION: usize = 11;
const PROFILE_FINAL_ASSEMBLY: usize = 12;
const PROFILE_STATIC_STATE_SELECTOR_LOOKUP: usize = 13;
const PROFILE_STATIC_WEIGHTED_MULTIPART_RESOLUTION: usize = 14;
const PROFILE_STATIC_CACHED_MODEL_LOOKUP: usize = 15;
const PROFILE_STATIC_CULLING: usize = 16;
const PROFILE_STATIC_QUAD_ITERATION: usize = 17;
const PROFILE_STATIC_LIGHTING_AO: usize = 18;
const PROFILE_STATIC_TINT: usize = 19;
const PROFILE_STATIC_POSITION_OFFSET_TRANSFORM: usize = 20;
const PROFILE_STATIC_SPRITE_MATERIAL_PASS: usize = 21;
const PROFILE_STATIC_NATIVE_QUAD_CREATION: usize = 22;
const PROFILE_STATIC_STAGING: usize = 23;
const PROFILE_FLUID_TOP_FACE_CONSTRUCTION: usize = 24;
const PROFILE_FLUID_SIDE_FACE_CONSTRUCTION: usize = 25;
const PROFILE_FLUID_BOTTOM_FACE_CONSTRUCTION: usize = 26;
const PROFILE_FLUID_CORNER_HEIGHT_USE: usize = 27;
const PROFILE_FLUID_STILL_FLOWING_UV: usize = 28;
const PROFILE_FLUID_OVERLAY_SELECTION: usize = 29;
const PROFILE_FLUID_LIGHTING_TINT: usize = 30;
const PROFILE_FLUID_NORMAL_BACKFACE: usize = 31;
const PROFILE_FLUID_MATERIAL_SPRITE_ROUTING: usize = 32;
const PROFILE_FLUID_NATIVE_QUAD_APPEND: usize = 33;
const PROFILE_SCAN_ACTIVE_RECORD_ITERATION: usize = 34;
const PROFILE_SCAN_RECORD_DECODING: usize = 35;
const PROFILE_SCAN_DISPATCH: usize = 36;
const PROFILE_SCAN_CACHE_LOOKUP: usize = 37;
const PROFILE_SCAN_CULLING: usize = 38;
const PROFILE_SCAN_LIGHTING_AO: usize = 39;
const PROFILE_SCAN_TINTING: usize = 40;
const PROFILE_SCAN_MODEL_EMISSION: usize = 41;
const PROFILE_SCAN_FLUID_EMISSION: usize = 42;
#[allow(dead_code)]
const PROFILE_SCAN_PASS_MATERIAL_ROUTING: usize = 43;
const PROFILE_SCAN_QUAD_APPEND: usize = 44;
const PROFILE_STAGING_QUAD_APPEND: usize = 45;
const PROFILE_STAGING_PENDING_WRITE: usize = 46;
const PROFILE_STAGING_FLUSH: usize = 47;
const PROFILE_STAGING_VERTEX_ENCODING: usize = 48;
#[allow(dead_code)]
const PROFILE_STAGING_INDEX_WRITE: usize = 49;
#[allow(dead_code)]
const PROFILE_STAGING_FINAL_BUFFER_ASSEMBLY: usize = 50;
#[allow(dead_code)]
const PROFILE_TEMPLATE_LOOKUP: usize = 51;
const PROFILE_TEMPLATE_INSTANCE_PATCH: usize = 52;
const PROFILE_TEMPLATE_DIRECT_VERTEX_ENCODING: usize = 53;
#[allow(dead_code)]
const PROFILE_TEMPLATE_RETAINED_TRANSLUCENT_METADATA: usize = 54;
#[allow(dead_code)]
const PROFILE_TEMPLATE_FINAL_ASSEMBLY_COPY: usize = 55;
const PROFILE_COUNT_SCANNED_BLOCKS: usize = 0;
const PROFILE_COUNT_NATIVE_MODEL_BLOCKS: usize = 1;
const PROFILE_COUNT_NATIVE_MODEL_QUADS: usize = 2;
const PROFILE_COUNT_FLUID_BLOCKS: usize = 3;
const PROFILE_COUNT_FLUID_FACES: usize = 4;
const PROFILE_COUNT_TRANSLUCENT_QUADS: usize = 5;
#[allow(dead_code)]
const PROFILE_COUNT_SORTED_QUADS: usize = 6;
const PROFILE_COUNT_EMITTED_QUADS: usize = 7;
const PROFILE_COUNT_DIRECT_TEMPLATE_QUADS: usize = 8;
const PROFILE_COUNT_GENERIC_NATIVE_QUADS: usize = 9;
const PROFILE_COUNT_DIRECT_TEMPLATE_BYTES_WRITTEN: usize = 10;
const PROFILE_COUNT_GENERIC_NATIVE_BYTES_RETAINED: usize = 11;
const PROFILE_COUNT_SELECTOR_RESOLUTIONS: usize = 12;
const PROFILE_COUNT_SELECTOR_CACHE_HITS: usize = 13;
const PROFILE_COUNT_SELECTOR_CACHE_MISSES: usize = 14;
const PROFILE_COUNT_MULTIPART_CHILDREN_TESTED: usize = 15;
const PROFILE_COUNT_MULTIPART_CHILDREN_SELECTED: usize = 16;
const PROFILE_COUNT_WEIGHTED_ENTRIES_VISITED: usize = 17;
const PROFILE_COUNT_MODEL_CACHE_HITS: usize = 18;
const PROFILE_COUNT_MODEL_CACHE_MISSES: usize = 19;
const PROFILE_COUNT_TEMP_VECTOR_CLEARS: usize = 20;
const PROFILE_COUNT_TRANSLUCENT_RETAINED_BYTES: usize = 21;
const PROFILE_COUNT_TRANSLUCENT_ANALYZER_ENTRIES: usize = 22;
const PROFILE_COUNT_TRANSLUCENT_VALIDITY_BYTES: usize = 23;

static STATIC_MODEL_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();
static FLUID_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();
static SCAN_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();
static STAGING_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();
static ENABLE_DIRECT_STATIC_TEMPLATES: OnceLock<bool> = OnceLock::new();

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct QuadVertex {
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct NativeQuad {
    vertices: [QuadVertex; 4],
    block_emission: u8,
    render_type: u8,
    ignore_mid_block: u8,
    _padding: u8,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct FlatQuadRecord {
    quad: NativeQuad,
    packed_normal: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct LightBlockRecord {
    material_bits: i32,
    block_emission: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct FluidFaceRecord {
    packed_normal: i32,
    material_bits: i32,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    face_kind: i32,
    flip: i32,
    origin_x: f32,
    origin_y: f32,
    origin_z: f32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [f32; 8],
    colors: [i32; 4],
    aos: [f32; 4],
    lights: [i32; 4],
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct StaticModelVertexRecord {
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    u: f32,
    v: f32,
    light: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct StaticModelQuadRecord {
    vertices: [StaticModelVertexRecord; 4],
    material_bits: i32,
    cull_face: i32,
    normal_face: i32,
    packed_normal: i32,
    block_emission: i32,
    render_type: i32,
    shade: i32,
    flags: i32,
    light_face: i32,
    tint_index: i32,
    has_ao: i32,
    pass_id: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct StaticModelBlockRecord {
    model_id: i32,
    material_bits: i32,
    block_emission: i32,
    render_type: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    cull_mask: i32,
    _padding: i32,
    offset_x: f32,
    offset_y: f32,
    offset_z: f32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct NativeSectionBlockRecord {
    state_id: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    seed_lo: i32,
    seed_hi: i32,
    neighbor_state_ids: [i32; 6],
    light_words: [i32; 27],
    neighborhood_state_ids: [i32; 27],
    tint: i32,
    fluid_tint: i32,
    fluid_flow_x: f32,
    fluid_flow_z: f32,
    absolute_x: i32,
    absolute_y: i32,
    absolute_z: i32,
    legacy_offset_x: f32,
    legacy_offset_y: f32,
    legacy_offset_z: f32,
    fluid_block_id: i32,
    flags: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct NativeModelSelectorEntry {
    target_id: i32,
    weight: i32,
}

#[derive(Clone, Debug, Default)]
struct NativeModelSelector {
    kind: i32,
    entries: Vec<NativeModelSelectorEntry>,
    total_weight: i32,
}

#[derive(Clone, Copy, Debug)]
struct FluidSprite {
    u0: f32,
    u1: f32,
    v0: f32,
    v1: f32,
    shrink: f32,
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
struct NativeMeshingState {
    selector_id: i32,
    flags: i32,
    material_bits: i32,
    pass_id: i32,
    block_emission: i32,
    #[allow(dead_code)]
    render_type: i32,
    block_id: i32,
    fluid_material_bits: i32,
    fluid_pass_id: i32,
    fluid_block_id: i32,
    skip_group: i32,
    fluid_type: i32,
    fluid_own_height: f32,
    fluid_falling: i32,
    offset_type: i32,
    max_horizontal_offset: f32,
    max_vertical_offset: f32,
    tint_type: i32,
    fluid_still: FluidSprite,
    fluid_flow: FluidSprite,
    fluid_overlay: FluidSprite,
    fluid_overlay_valid: i32,
}

struct NativeQuadBuffer {
    quads: Vec<NativeQuad>,
    encoded: Vec<u8>,
    encoded_format: Option<NativeFormat>,
}

struct NativePendingQuadBuffer {
    quads: Vec<NativeQuad>,
    flat_quad_records: Vec<FlatQuadRecord>,
    light_block_records: Vec<LightBlockRecord>,
    fluid_face_records: Vec<FluidFaceRecord>,
    static_model_block_records: Vec<StaticModelBlockRecord>,
    static_template_blocks: Vec<*const NativeSectionBlockRecord>,
    static_template_states: Vec<NativeMeshingState>,
    static_template_quads: Vec<*const StaticModelQuadRecord>,
    packed_normals: Vec<i32>,
    validity: Vec<u8>,
}

struct NativeSectionMeshBuilder {
    buffers: [NativeQuadBuffer; MODEL_QUAD_FACING_COUNT],
    pending: [NativePendingQuadBuffer; MODEL_QUAD_FACING_COUNT],
    counts: [usize; MODEL_QUAD_FACING_COUNT],
    profile: NativeMeshingProfile,
    section_pass_cache_address: u64,
    section_pass_cache_count: usize,
    section_pass_cache_mask: u32,
    section_pass_cache_valid: bool,
    fluid_sprite_mask: i32,
}

#[derive(Clone, Copy, Debug)]
struct NativeMeshingProfile {
    stage_nanos: [u64; PROFILE_STAGE_COUNT],
    counts: [u64; PROFILE_COUNT_COUNT],
}

impl Default for NativeMeshingProfile {
    fn default() -> Self {
        Self {
            stage_nanos: [0; PROFILE_STAGE_COUNT],
            counts: [0; PROFILE_COUNT_COUNT],
        }
    }
}

impl NativeMeshingProfile {
    #[inline(always)]
    fn reset(&mut self) {
        self.stage_nanos.fill(0);
        self.counts.fill(0);
    }

    #[inline(always)]
    fn add_stage(&mut self, stage: usize, started_at: Instant) {
        self.stage_nanos[stage] = self.stage_nanos[stage]
            .saturating_add(started_at.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64);
    }

    #[inline(always)]
    fn add_optional_stage(&mut self, stage: usize, started_at: Option<Instant>) {
        if let Some(started_at) = started_at {
            self.add_stage(stage, started_at);
        }
    }

    #[inline(always)]
    fn add_count(&mut self, counter: usize, value: usize) {
        self.counts[counter] = self.counts[counter].saturating_add(value as u64);
    }
}

#[inline(always)]
fn static_model_substage_profile_enabled() -> bool {
    *STATIC_MODEL_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_STATIC_MODEL_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

fn direct_static_templates_enabled() -> bool {
    *ENABLE_DIRECT_STATIC_TEMPLATES.get_or_init(|| {
        std::env::var("MATTMC_NATIVE_MESHING_ENABLE_DIRECT_STATIC_TEMPLATES")
            .map(|value| {
                value.eq_ignore_ascii_case("true")
                    || value.eq_ignore_ascii_case("yes")
                    || value == "1"
            })
            .unwrap_or(false)
    })
}

#[inline(always)]
fn fluid_substage_profile_enabled() -> bool {
    *FLUID_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_FLUID_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

#[inline(always)]
fn scan_substage_profile_enabled() -> bool {
    *SCAN_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_SCAN_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

#[inline(always)]
fn staging_substage_profile_enabled() -> bool {
    *STAGING_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_STAGING_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

#[inline(always)]
fn profile_start(enabled: bool) -> Option<Instant> {
    enabled.then(Instant::now)
}

struct NativeUpdatedQuads {
    quads: Vec<u64>,
    mesh_quad_count: i32,
    index_quad_count: i32,
}

#[cfg(test)]
mod tests;
