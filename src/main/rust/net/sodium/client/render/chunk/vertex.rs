use std::collections::HashMap;
use std::slice;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Mutex, OnceLock};

use super::{index, translucent};

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
static FLUID_DIAG_COUNT: AtomicUsize = AtomicUsize::new(0);
static FLUID_FLUSH_DIAG_COUNT: AtomicUsize = AtomicUsize::new(0);
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
    _padding: i32,
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
    packed_normals: Vec<i32>,
    validity: Vec<u8>,
}

struct NativeSectionMeshBuilder {
    buffers: [NativeQuadBuffer; MODEL_QUAD_FACING_COUNT],
    pending: [NativePendingQuadBuffer; MODEL_QUAD_FACING_COUNT],
    counts: [usize; MODEL_QUAD_FACING_COUNT],
}

struct NativeUpdatedQuads {
    quads: Vec<u64>,
    mesh_quad_count: i32,
    index_quad_count: i32,
}

static STATIC_MODEL_CACHE: OnceLock<Mutex<HashMap<i32, Vec<StaticModelQuadRecord>>>> = OnceLock::new();
static NATIVE_MODEL_SELECTORS: OnceLock<Mutex<HashMap<i32, NativeModelSelector>>> = OnceLock::new();
static NATIVE_MESHING_STATES: OnceLock<Mutex<HashMap<i32, NativeMeshingState>>> = OnceLock::new();

fn static_model_cache() -> &'static Mutex<HashMap<i32, Vec<StaticModelQuadRecord>>> {
    STATIC_MODEL_CACHE.get_or_init(|| Mutex::new(HashMap::new()))
}

fn native_model_selectors() -> &'static Mutex<HashMap<i32, NativeModelSelector>> {
    NATIVE_MODEL_SELECTORS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn native_meshing_states() -> &'static Mutex<HashMap<i32, NativeMeshingState>> {
    NATIVE_MESHING_STATES.get_or_init(|| Mutex::new(HashMap::new()))
}

pub(crate) fn updated_quads_create_from_handles(
    quads: Vec<u64>,
    mesh_quad_count: i32,
    index_quad_count: i32,
) -> u64 {
    Box::into_raw(Box::new(NativeUpdatedQuads {
        quads,
        mesh_quad_count,
        index_quad_count,
    })) as u64
}

#[derive(Clone, Copy, PartialEq, Eq)]
struct NativeFormat {
    vertex_stride: usize,
    block_id_offset: usize,
    normal_offset: usize,
    tangent_offset: usize,
    mid_uv_offset: usize,
    mid_block_offset: usize,
    section_index: i32,
    separate_ao: bool,
}

impl NativeFormat {
    fn from_abi(
        quad_stride: i32,
        vertex_stride: i32,
        block_id_offset: i32,
        normal_offset: i32,
        tangent_offset: i32,
        mid_uv_offset: i32,
        mid_block_offset: i32,
        section_index: i32,
        separate_ao: i32,
    ) -> Result<Self, i32> {
        let quad_stride = usize::try_from(quad_stride).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let vertex_stride = usize::try_from(vertex_stride).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let block_id_offset = usize::try_from(block_id_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let normal_offset = usize::try_from(normal_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let tangent_offset = usize::try_from(tangent_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let mid_uv_offset = usize::try_from(mid_uv_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let mid_block_offset =
            usize::try_from(mid_block_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;

        if quad_stride != std::mem::size_of::<NativeQuad>() || vertex_stride < 20 {
            return Err(ERR_INVALID_ARGUMENT);
        }

        for offset in [
            block_id_offset,
            normal_offset,
            tangent_offset,
            mid_uv_offset,
            mid_block_offset,
        ] {
            if offset != 0 && offset + 4 > vertex_stride {
                return Err(ERR_INVALID_ARGUMENT);
            }
        }

        Ok(Self {
            vertex_stride,
            block_id_offset,
            normal_offset,
            tangent_offset,
            mid_uv_offset,
            mid_block_offset,
            section_index,
            separate_ao: separate_ao != 0,
        })
    }
}

pub fn verify() -> i32 {
    if std::mem::size_of::<QuadVertex>() == 32
        && std::mem::size_of::<NativeQuad>() == 152
        && std::mem::size_of::<FlatQuadRecord>() == 156
        && std::mem::size_of::<LightBlockRecord>() == 24
        && std::mem::size_of::<FluidFaceRecord>() == 172
        && std::mem::size_of::<StaticModelVertexRecord>() == 28
        && std::mem::size_of::<StaticModelQuadRecord>() == 160
        && std::mem::size_of::<StaticModelBlockRecord>() == 52
        && std::mem::size_of::<NativeSectionBlockRecord>() == 316
        && std::mem::size_of::<NativeModelSelectorEntry>() == 8
    {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
}

fn compact_format_value(value: i32) -> i32 {
    match value {
        COMPACT_VALUE_STRIDE => COMPACT_VERTEX_STRIDE,
        COMPACT_VALUE_POSITION_OFFSET => COMPACT_POSITION_OFFSET,
        COMPACT_VALUE_COLOR_OFFSET => COMPACT_COLOR_OFFSET,
        COMPACT_VALUE_TEXTURE_OFFSET => COMPACT_TEXTURE_OFFSET,
        COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET => COMPACT_LIGHT_MATERIAL_INDEX_OFFSET,
        COMPACT_VALUE_BLOCK_ID_OFFSET => COMPACT_NATIVE_BLOCK_ID_OFFSET,
        COMPACT_VALUE_NORMAL_OFFSET => COMPACT_NATIVE_NORMAL_OFFSET,
        COMPACT_VALUE_TANGENT_OFFSET => COMPACT_NATIVE_TANGENT_OFFSET,
        COMPACT_VALUE_MID_UV_OFFSET => COMPACT_NATIVE_MID_UV_OFFSET,
        COMPACT_VALUE_MID_BLOCK_OFFSET => COMPACT_NATIVE_MID_BLOCK_OFFSET,
        COMPACT_VALUE_POSITION_MAX_VALUE => POSITION_MAX_VALUE as i32,
        COMPACT_VALUE_TEXTURE_MAX_VALUE => TEXTURE_MAX_VALUE as i32,
        _ => ERR_INVALID_ARGUMENT,
    }
}

unsafe fn native_quad_mut(address: u64) -> Result<&'static mut NativeQuad, i32> {
    if address == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(&mut *(address as *mut NativeQuad))
}

unsafe fn native_quad(address: u64) -> Result<&'static NativeQuad, i32> {
    if address == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(&*(address as *const NativeQuad))
}

unsafe fn write_native_quad_metadata(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
) -> i32 {
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    quad.block_emission = block_emission as u8;
    quad.render_type = render_type as u8;
    quad.ignore_mid_block = if ignore_mid_block != 0 { 1 } else { 0 };
    quad._padding = 0;
    quad.block_id = block_id;
    quad.local_x = local_x;
    quad.local_y = local_y;
    quad.local_z = local_z;
    quad.material_bits = material_bits;
    OK
}

unsafe fn write_native_quad_vertex(
    quad_address: u64,
    vertex_index: i32,
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
) -> i32 {
    let vertex_index = match usize::try_from(vertex_index) {
        Ok(value) if value < 4 => value,
        _ => return ERR_INVALID_ARGUMENT,
    };
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    quad.vertices[vertex_index] = QuadVertex {
        x,
        y,
        z,
        color,
        ao,
        u,
        v,
        light,
    };
    OK
}

unsafe fn write_native_quad(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
    x0: f32,
    y0: f32,
    z0: f32,
    color0: i32,
    ao0: f32,
    u0: f32,
    v0: f32,
    light0: i32,
    x1: f32,
    y1: f32,
    z1: f32,
    color1: i32,
    ao1: f32,
    u1: f32,
    v1: f32,
    light1: i32,
    x2: f32,
    y2: f32,
    z2: f32,
    color2: i32,
    ao2: f32,
    u2: f32,
    v2: f32,
    light2: i32,
    x3: f32,
    y3: f32,
    z3: f32,
    color3: i32,
    ao3: f32,
    u3: f32,
    v3: f32,
    light3: i32,
) -> i32 {
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *quad = NativeQuad {
        vertices: [
            QuadVertex {
                x: x0,
                y: y0,
                z: z0,
                color: color0,
                ao: ao0,
                u: u0,
                v: v0,
                light: light0,
            },
            QuadVertex {
                x: x1,
                y: y1,
                z: z1,
                color: color1,
                ao: ao1,
                u: u1,
                v: v1,
                light: light1,
            },
            QuadVertex {
                x: x2,
                y: y2,
                z: z2,
                color: color2,
                ao: ao2,
                u: u2,
                v: v2,
                light: light2,
            },
            QuadVertex {
                x: x3,
                y: y3,
                z: z3,
                color: color3,
                ao: ao3,
                u: u3,
                v: v3,
                light: light3,
            },
        ],
        block_emission: block_emission as u8,
        render_type: render_type as u8,
        ignore_mid_block: if ignore_mid_block != 0 { 1 } else { 0 },
        _padding: 0,
        block_id,
        local_x,
        local_y,
        local_z,
        material_bits,
    };
    OK
}

unsafe fn native_quad_position(quad_address: u64, vertex_index: i32, component: i32) -> f32 {
    let Ok(vertex_index) = usize::try_from(vertex_index) else {
        return 0.0;
    };
    if vertex_index >= 4 {
        return 0.0;
    }

    let Ok(quad) = native_quad(quad_address) else {
        return 0.0;
    };
    let vertex = quad.vertices[vertex_index];

    match component {
        0 => vertex.x,
        1 => vertex.y,
        2 => vertex.z,
        _ => 0.0,
    }
}

unsafe fn encode(
    input_address: u64,
    vertex_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if vertex_count < 0 || output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if vertex_count == 0 {
        return OK;
    }
    if input_address == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let vertex_count = match usize::try_from(vertex_count) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if vertex_count % 4 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let quad_count = vertex_count / 4;
    let output_len = match vertex_count.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let input = slice::from_raw_parts(input_address as *const NativeQuad, quad_count);
    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);

    for (quad_index, quad) in input.iter().enumerate() {
        let start = quad_index * 4 * format.vertex_stride;
        let end = start + 4 * format.vertex_stride;
        encode_quad(quad, &mut output[start..end], format);
    }

    OK
}

unsafe fn encode_scattered(
    input_address: u64,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if update_count < 0 || output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if update_count == 0 {
        return OK;
    }
    if input_address == 0 || output_vertex_offsets.is_null() || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let update_count = match usize::try_from(update_count) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_capacity);
    let input = slice::from_raw_parts(input_address as *const NativeQuad, update_count);
    let output_vertex_offsets = slice::from_raw_parts(output_vertex_offsets, update_count);
    let quad_byte_len = match 4usize.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };

    for (quad, vertex_offset) in input.iter().zip(output_vertex_offsets.iter()) {
        let vertex_offset = match usize::try_from(*vertex_offset) {
            Ok(value) => value,
            Err(_) => return ERR_INVALID_ARGUMENT,
        };
        if vertex_offset % 4 != 0 {
            return ERR_INVALID_ARGUMENT;
        }

        let byte_offset = match vertex_offset.checked_mul(format.vertex_stride) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let byte_end = match byte_offset.checked_add(quad_byte_len) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let Some(output_slice) = output.get_mut(byte_offset..byte_end) else {
            return ERR_CAPACITY;
        };

        encode_quad(quad, output_slice, format);
    }

    OK
}

unsafe fn assemble(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
) -> i32 {
    if input_addresses.is_null() || input_vertex_counts.is_null() || vertex_segments.is_null() {
        return ERR_NULL_POINTER;
    }
    if input_count != MODEL_QUAD_FACING_COUNT as i32
        || vertex_segments_len != (MODEL_QUAD_FACING_COUNT * 2) as i32
        || output_capacity < 0
    {
        return ERR_INVALID_ARGUMENT;
    }

    let input_addresses = slice::from_raw_parts(input_addresses, MODEL_QUAD_FACING_COUNT);
    let input_vertex_counts = slice::from_raw_parts(input_vertex_counts, MODEL_QUAD_FACING_COUNT);
    let vertex_segments = slice::from_raw_parts_mut(vertex_segments, MODEL_QUAD_FACING_COUNT * 2);
    vertex_segments.fill(0);

    let total_vertices = match input_vertex_counts.iter().try_fold(0usize, |acc, count| {
        let count = usize::try_from(*count).ok()?;
        if count % 4 != 0 {
            return None;
        }
        acc.checked_add(count)
    }) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };

    if total_vertices == 0 {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_len = match total_vertices.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);
    let mut output_vertex_offset = 0usize;

    if slice_reordering != 0 {
        let mut segment_index = 0usize;
        if let Err(status) = append_segment(
            MODEL_QUAD_FACING_UNASSIGNED,
            input_addresses,
            input_vertex_counts,
            output,
            &mut output_vertex_offset,
            vertex_segments,
            &mut segment_index,
            format,
        ) {
            return status;
        }

        for step in 0..2 {
            for facing in 0..MODEL_QUAD_FACING_COUNT {
                if facing == MODEL_QUAD_FACING_UNASSIGNED
                    || ((visible_slices >> facing) & 1) == step
                {
                    continue;
                }
                if let Err(status) = append_segment(
                    facing,
                    input_addresses,
                    input_vertex_counts,
                    output,
                    &mut output_vertex_offset,
                    vertex_segments,
                    &mut segment_index,
                    format,
                ) {
                    return status;
                }
            }
        }
    } else {
        if force_unassigned != 0 {
            let segment_index = MODEL_QUAD_FACING_UNASSIGNED << 1;
            vertex_segments[segment_index] = total_vertices as i32;
            vertex_segments[segment_index + 1] = MODEL_QUAD_FACING_UNASSIGNED as i32;
        }

        for facing in 0..MODEL_QUAD_FACING_COUNT {
            let vertex_count = match usize::try_from(input_vertex_counts[facing]) {
                Ok(value) => value,
                Err(_) => return ERR_INVALID_ARGUMENT,
            };
            if vertex_count == 0 {
                continue;
            }

            if force_unassigned == 0 {
                let segment_index = facing << 1;
                vertex_segments[segment_index] = vertex_count as i32;
                vertex_segments[segment_index + 1] = facing as i32;
            }

            if let Err(status) = encode_segment(
                input_addresses[facing],
                vertex_count,
                output,
                &mut output_vertex_offset,
                format,
            ) {
                return status;
            }
        }
    }

    OK
}

unsafe fn assemble_output(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
    index_output_address: u64,
    index_output_capacity: i32,
    index_mode: i32,
    index_stride: i32,
    index_values: *const i32,
    index_value_count: i32,
) -> i32 {
    let status = assemble(
        input_addresses,
        input_vertex_counts,
        input_count,
        output_address,
        output_capacity,
        vertex_segments,
        vertex_segments_len,
        format,
        visible_slices,
        force_unassigned,
        slice_reordering,
    );
    if status != OK || index_mode == INDEX_MODE_NONE {
        return status;
    }
    if index_output_capacity < 0 || index_output_address == 0 {
        return if index_output_capacity < 0 {
            ERR_INVALID_ARGUMENT
        } else {
            ERR_NULL_POINTER
        };
    }

    match index_mode {
        INDEX_MODE_SHARED => {
            let total_vertices = match total_vertex_count(input_vertex_counts, input_count) {
                Ok(value) => value,
                Err(status) => return status,
            };
            if total_vertices % 4 != 0 {
                return ERR_INVALID_ARGUMENT;
            }

            let output = slice::from_raw_parts_mut(
                index_output_address as *mut u8,
                index_output_capacity as usize,
            );
            index::write_shared_quad_index_buffer(output, index_stride, (total_vertices / 4) as i32)
        }
        INDEX_MODE_SORTED_QUADS | INDEX_MODE_KEY_SORTED => {
            if index_value_count < 0 {
                return ERR_INVALID_ARGUMENT;
            }
            if index_value_count == 0 {
                return OK;
            }
            if index_values.is_null() {
                return ERR_NULL_POINTER;
            }

            let index_capacity = (index_output_capacity as usize) / std::mem::size_of::<i32>();
            let output =
                slice::from_raw_parts_mut(index_output_address as *mut i32, index_capacity);
            let values = slice::from_raw_parts(index_values, index_value_count as usize);

            if index_mode == INDEX_MODE_SORTED_QUADS {
                index::write_sorted_quad_index_buffer(output, values)
            } else {
                index::write_key_sorted_quad_index_buffer(output, values)
            }
        }
        _ => ERR_INVALID_ARGUMENT,
    }
}

unsafe fn assemble_section_builder(
    builder: &NativeSectionMeshBuilder,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
) -> i32 {
    if vertex_segments.is_null() || output_capacity < 0 {
        return if vertex_segments.is_null() {
            ERR_NULL_POINTER
        } else {
            ERR_INVALID_ARGUMENT
        };
    }
    if vertex_segments_len != (MODEL_QUAD_FACING_COUNT * 2) as i32 {
        return ERR_INVALID_ARGUMENT;
    }

    let vertex_segments = slice::from_raw_parts_mut(vertex_segments, MODEL_QUAD_FACING_COUNT * 2);
    vertex_segments.fill(0);

    let total_vertices = match builder
        .counts
        .iter()
        .try_fold(0usize, |acc, count| acc.checked_add(count.checked_mul(4)?))
    {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if total_vertices == 0 {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_len = match total_vertices.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);
    let mut output_vertex_offset = 0usize;

    if slice_reordering != 0 {
        let mut segment_index = 0usize;
        if let Err(status) = append_builder_segment(
            builder,
            MODEL_QUAD_FACING_UNASSIGNED,
            output,
            &mut output_vertex_offset,
            vertex_segments,
            &mut segment_index,
            format,
        ) {
            return status;
        }

        for step in 0..2 {
            for facing in 0..MODEL_QUAD_FACING_COUNT {
                if facing == MODEL_QUAD_FACING_UNASSIGNED
                    || ((visible_slices >> facing) & 1) == step
                {
                    continue;
                }
                if let Err(status) = append_builder_segment(
                    builder,
                    facing,
                    output,
                    &mut output_vertex_offset,
                    vertex_segments,
                    &mut segment_index,
                    format,
                ) {
                    return status;
                }
            }
        }
    } else {
        if force_unassigned != 0 {
            let segment_index = MODEL_QUAD_FACING_UNASSIGNED << 1;
            vertex_segments[segment_index] = total_vertices as i32;
            vertex_segments[segment_index + 1] = MODEL_QUAD_FACING_UNASSIGNED as i32;
        }

        for facing in 0..MODEL_QUAD_FACING_COUNT {
            let vertex_count = builder.counts[facing] * 4;
            if vertex_count == 0 {
                continue;
            }

            if force_unassigned == 0 {
                let segment_index = facing << 1;
                vertex_segments[segment_index] = vertex_count as i32;
                vertex_segments[segment_index + 1] = facing as i32;
            }

            if let Err(status) = encode_builder_segment(
                &builder.buffers[facing],
                vertex_count,
                output,
                &mut output_vertex_offset,
                format,
            ) {
                return status;
            }
        }
    }

    OK
}

fn append_builder_segment(
    builder: &NativeSectionMeshBuilder,
    facing: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    vertex_segments: &mut [i32],
    segment_index: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    let vertex_count = builder.counts[facing]
        .checked_mul(4)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    vertex_segments[*segment_index] = vertex_count as i32;
    vertex_segments[*segment_index + 1] = facing as i32;
    *segment_index += 2;

    if vertex_count != 0 {
        encode_builder_segment(
            &builder.buffers[facing],
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
    }

    Ok(())
}

fn encode_builder_segment(
    buffer: &NativeQuadBuffer,
    vertex_count: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    if vertex_count % 4 != 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let byte_offset = output_vertex_offset
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let byte_len = vertex_count
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let output_slice = output
        .get_mut(byte_offset..byte_offset + byte_len)
        .ok_or(ERR_CAPACITY)?;

    if buffer.encoded_format == Some(format) && buffer.encoded.len() >= byte_len {
        output_slice.copy_from_slice(&buffer.encoded[..byte_len]);
    } else {
        if buffer.quads.len() < vertex_count / 4 {
            return Err(ERR_INVALID_ARGUMENT);
        }
        let input_address = buffer.quads.as_ptr() as u64;
        encode_segment(
            input_address,
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
        return Ok(());
    }

    *output_vertex_offset += vertex_count;
    Ok(())
}

unsafe fn total_vertex_count(
    input_vertex_counts: *const i32,
    input_count: i32,
) -> Result<usize, i32> {
    if input_vertex_counts.is_null() {
        return Err(ERR_NULL_POINTER);
    }
    if input_count != MODEL_QUAD_FACING_COUNT as i32 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let input_vertex_counts = slice::from_raw_parts(input_vertex_counts, MODEL_QUAD_FACING_COUNT);
    input_vertex_counts
        .iter()
        .try_fold(0usize, |acc, count| {
            let count = usize::try_from(*count).ok()?;
            if count % 4 != 0 {
                return None;
            }
            acc.checked_add(count)
        })
        .ok_or(ERR_INVALID_ARGUMENT)
}

fn append_segment(
    facing: usize,
    input_addresses: &[u64],
    input_vertex_counts: &[i32],
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    vertex_segments: &mut [i32],
    segment_index: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    let vertex_count =
        usize::try_from(input_vertex_counts[facing]).map_err(|_| ERR_INVALID_ARGUMENT)?;
    vertex_segments[*segment_index] = vertex_count as i32;
    vertex_segments[*segment_index + 1] = facing as i32;
    *segment_index += 2;

    if vertex_count != 0 {
        encode_segment(
            input_addresses[facing],
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
    }

    Ok(())
}

fn encode_segment(
    input_address: u64,
    vertex_count: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    if input_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if vertex_count % 4 != 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }
    let quad_count = vertex_count / 4;

    let byte_offset = output_vertex_offset
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let byte_len = vertex_count
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let output_slice = output
        .get_mut(byte_offset..byte_offset + byte_len)
        .ok_or(ERR_CAPACITY)?;
    let input = unsafe { slice::from_raw_parts(input_address as *const NativeQuad, quad_count) };

    for (quad_index, quad) in input.iter().enumerate() {
        let start = quad_index * 4 * format.vertex_stride;
        let end = start + 4 * format.vertex_stride;
        encode_quad(quad, &mut output_slice[start..end], format);
    }

    *output_vertex_offset += vertex_count;
    Ok(())
}

fn create_section_mesh_builder(capacity: usize) -> NativeSectionMeshBuilder {
    NativeSectionMeshBuilder {
        buffers: std::array::from_fn(|_| NativeQuadBuffer {
            quads: vec![NativeQuad::default(); capacity],
            encoded: Vec::new(),
            encoded_format: None,
        }),
        pending: std::array::from_fn(|_| NativePendingQuadBuffer {
            quads: vec![NativeQuad::default(); PENDING_BATCH_QUAD_CAPACITY],
            flat_quad_records: vec![FlatQuadRecord::default(); PENDING_BATCH_QUAD_CAPACITY],
            light_block_records: vec![LightBlockRecord::default(); PENDING_BATCH_QUAD_CAPACITY],
            fluid_face_records: vec![FluidFaceRecord::default(); PENDING_BATCH_QUAD_CAPACITY],
            static_model_block_records: vec![StaticModelBlockRecord::default(); PENDING_BATCH_QUAD_CAPACITY],
            packed_normals: vec![0; PENDING_BATCH_QUAD_CAPACITY],
            validity: vec![0; PENDING_BATCH_QUAD_CAPACITY],
        }),
        counts: [0; MODEL_QUAD_FACING_COUNT],
    }
}

fn section_builder_prepare_quad(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
) -> Result<u64, i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let index = builder.counts[facing];
    if builder.buffers[facing].quads.len() <= index {
        let next_capacity = (builder.buffers[facing].quads.len().max(1) * 2).max(index + 1);
        builder.buffers[facing]
            .quads
            .resize(next_capacity, NativeQuad::default());
    }

    Ok(unsafe { builder.buffers[facing].quads.as_mut_ptr().add(index) as u64 })
}

unsafe fn section_builder_append_batch(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    batch_address: u64,
    quad_count: usize,
    validity: Option<&[u8]>,
) -> Result<i32, i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if quad_count == 0 {
        return Ok(0);
    }
    if batch_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if let Some(validity) = validity {
        if validity.len() < quad_count {
            return Err(ERR_INVALID_ARGUMENT);
        }
    }

    let input = slice::from_raw_parts(batch_address as *const NativeQuad, quad_count);
    let valid_count = validity
        .map(|mask| {
            mask.iter()
                .take(quad_count)
                .filter(|&&value| value != 0)
                .count()
        })
        .unwrap_or(quad_count);
    let start = builder.counts[facing];
    let required_len = start.checked_add(valid_count).ok_or(ERR_CAPACITY)?;

    if builder.buffers[facing].quads.len() < required_len {
        builder.buffers[facing]
            .quads
            .resize(required_len, NativeQuad::default());
    }

    let output = &mut builder.buffers[facing].quads[start..required_len];
    let mut output_index = 0usize;

    for index in 0..quad_count {
        let is_valid = match validity {
            Some(mask) => mask[index] != 0,
            None => true,
        };

        if is_valid {
            output[output_index] = input[index];
            output_index += 1;
        }
    }

    builder.counts[facing] = required_len;
    Ok(valid_count as i32)
}

unsafe fn section_builder_append_batch_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    batch_address: u64,
    quad_count: usize,
    validity: Option<&[u8]>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if quad_count == 0 {
        return Ok(0);
    }
    if batch_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if let Some(validity) = validity {
        if validity.len() < quad_count {
            return Err(ERR_INVALID_ARGUMENT);
        }
    }

    let input = slice::from_raw_parts(batch_address as *const NativeQuad, quad_count);
    let valid_count = validity
        .map(|mask| {
            mask.iter()
                .take(quad_count)
                .filter(|&&value| value != 0)
                .count()
        })
        .unwrap_or(quad_count);

    let start = builder.counts[facing];
    let required_len = start.checked_add(valid_count).ok_or(ERR_CAPACITY)?;
    let buffer = &mut builder.buffers[facing];

    if store_raw_quads && buffer.quads.len() < required_len {
        buffer.quads.resize(required_len, NativeQuad::default());
    }

    let encoded_quad_len = 4usize
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;

    if !buffer.encoded.is_empty() && buffer.encoded_format != Some(format) {
        buffer.encoded.clear();
        buffer.encoded_format = None;
    }
    if buffer.encoded_format.is_none() {
        buffer.encoded_format = Some(format);
    }

    let required_encoded_len = required_len
        .checked_mul(encoded_quad_len)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    if buffer.encoded.len() < required_encoded_len {
        buffer.encoded.resize(required_encoded_len, 0);
    }

    let mut output_index = 0usize;

    for index in 0..quad_count {
        let is_valid = match validity {
            Some(mask) => mask[index] != 0,
            None => true,
        };

        if is_valid {
            let quad = input[index];
            if store_raw_quads {
                buffer.quads[start + output_index] = quad;
            }
            let encoded_start = (start + output_index) * encoded_quad_len;
            let encoded_end = encoded_start + encoded_quad_len;
            encode_quad(
                &quad,
                &mut buffer.encoded[encoded_start..encoded_end],
                format,
            );
            output_index += 1;
        }
    }

    builder.counts[facing] = required_len;
    Ok(valid_count as i32)
}

unsafe fn section_builder_append_flat_quad_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    analyzer: Option<(u64, i32)>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<(i32, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if record_count == 0 {
        return Ok((0, 0));
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<FlatQuadRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const FlatQuadRecord, record_count);
    let mut processed = 0usize;
    let mut total_valid = 0i32;
    let mut total_committed = 0i32;

    while processed < record_count {
        let chunk_count = (record_count - processed).min(PENDING_BATCH_QUAD_CAPACITY);
        {
            let pending = &mut builder.pending[facing];
            for index in 0..chunk_count {
                let record = records[processed + index];
                pending.quads[index] = record.quad;
                pending.packed_normals[index] = record.packed_normal;
            }
        }

        let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
        let mut chunk_valid = chunk_count as i32;
        let validity = if let Some((analyzer_handle, translucent_facing)) = analyzer {
            let status = translucent::append_native_quad_batch_to_analyzer(
                analyzer_handle,
                builder.pending[facing].quads.as_ptr() as u64,
                chunk_count as i32,
                translucent_facing,
                builder.pending[facing].packed_normals.as_ptr(),
                validity_address,
                &mut chunk_valid,
            );
            if status != OK {
                return Err(status);
            }
            Some(slice::from_raw_parts(validity_address as *const u8, chunk_count))
        } else {
            None
        };

        let chunk_committed = section_builder_append_batch_encoded(
            builder,
            facing,
            builder.pending[facing].quads.as_ptr() as u64,
            chunk_count,
            validity,
            format,
            store_raw_quads,
        )?;

        total_valid = total_valid.checked_add(chunk_valid).ok_or(ERR_CAPACITY)?;
        total_committed = total_committed
            .checked_add(chunk_committed)
            .ok_or(ERR_CAPACITY)?;
        processed += chunk_count;
    }

    Ok((total_valid, total_committed))
}

fn native_fluid_flush_diag(
    facing: usize,
    has_analyzer: bool,
    chunk_count: usize,
    valid_count: i32,
    committed_count: i32,
    records: &[FluidFaceRecord],
) {
    if std::env::var_os("MATTMC_NATIVE_FLUID_DIAG").is_none() {
        return;
    }
    let Some(record) = records
        .iter()
        .find(|record| record.render_type == 1 && record.face_kind == FLUID_FACE_TOP_NE_SW)
    else {
        return;
    };
    let index = FLUID_FLUSH_DIAG_COUNT.fetch_add(1, Ordering::Relaxed);
    if index >= 80 {
        return;
    }
    eprintln!(
        "MATTMC_NATIVE_FLUID_DIAG #{index} flush facing={} analyzer={} quads={} valid={} committed={} first_pos={},{},{} first_face={} first_flip={} first_light=0x{:08x} first_color=0x{:08x}",
        facing,
        has_analyzer,
        chunk_count,
        valid_count,
        committed_count,
        record.local_x,
        record.local_y,
        record.local_z,
        record.face_kind,
        record.flip,
        record.lights[0],
        record.colors[0] as u32,
    );
}

unsafe fn section_builder_append_light_block_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if record_count == 0 {
        return Ok(0);
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<LightBlockRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const LightBlockRecord, record_count);
    let mut processed = 0usize;
    let mut total_committed = 0i32;

    while processed < record_count {
        let chunk_count = (record_count - processed).min(PENDING_BATCH_QUAD_CAPACITY);
        {
            let pending = &mut builder.pending[facing];
            for index in 0..chunk_count {
                pending.quads[index] = light_block_record_to_quad(records[processed + index]);
            }
        }

        let chunk_committed = section_builder_append_batch_encoded(
            builder,
            facing,
            builder.pending[facing].quads.as_ptr() as u64,
            chunk_count,
            None,
            format,
            store_raw_quads,
        )?;

        total_committed = total_committed
            .checked_add(chunk_committed)
            .ok_or(ERR_CAPACITY)?;
        processed += chunk_count;
    }

    Ok(total_committed)
}

unsafe fn section_builder_append_fluid_face_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    analyzer: Option<(u64, i32)>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<(i32, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if record_count == 0 {
        return Ok((0, 0));
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<FluidFaceRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const FluidFaceRecord, record_count);
    let mut processed = 0usize;
    let mut total_valid = 0i32;
    let mut total_committed = 0i32;

    while processed < record_count {
        let chunk_count = (record_count - processed).min(PENDING_BATCH_QUAD_CAPACITY);
        {
            let pending = &mut builder.pending[facing];
            for index in 0..chunk_count {
                let record = records[processed + index];
                pending.quads[index] = fluid_face_record_to_quad(record)?;
                pending.packed_normals[index] = record.packed_normal;
            }
        }

        let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
        let mut chunk_valid = chunk_count as i32;
        let validity = if let Some((analyzer_handle, translucent_facing)) = analyzer {
            let status = translucent::append_native_quad_batch_to_analyzer(
                analyzer_handle,
                builder.pending[facing].quads.as_ptr() as u64,
                chunk_count as i32,
                translucent_facing,
                builder.pending[facing].packed_normals.as_ptr(),
                validity_address,
                &mut chunk_valid,
            );
            if status != OK {
                return Err(status);
            }
            Some(slice::from_raw_parts(validity_address as *const u8, chunk_count))
        } else {
            None
        };

        let chunk_committed = section_builder_append_batch_encoded(
            builder,
            facing,
            builder.pending[facing].quads.as_ptr() as u64,
            chunk_count,
            validity,
            format,
            store_raw_quads,
        )?;

        native_fluid_flush_diag(
            facing,
            analyzer.is_some(),
            chunk_count,
            chunk_valid,
            chunk_committed,
            &records[processed..processed + chunk_count],
        );

        total_valid = total_valid.checked_add(chunk_valid).ok_or(ERR_CAPACITY)?;
        total_committed = total_committed
            .checked_add(chunk_committed)
            .ok_or(ERR_CAPACITY)?;
        processed += chunk_count;
    }

    Ok((total_valid, total_committed))
}

unsafe fn section_builder_append_static_model_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    if record_count == 0 {
        return Ok(0);
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<StaticModelBlockRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const StaticModelBlockRecord, record_count);
    let cache_guard = static_model_cache()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let mut total_committed = 0i32;
    let mut pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];

    for record in records {
        if record.model_id == STATIC_MODEL_EMPTY_RECORD_ID {
            continue;
        }
        if record.model_id == STATIC_MODEL_LIGHT_BLOCK_RECORD_ID {
            let facing = MODEL_QUAD_FACING_UNASSIGNED;
            let slot = pending_counts[facing];
            {
                let pending = &mut builder.pending[facing];
                pending.quads[slot] = light_block_record_to_quad(LightBlockRecord {
                    material_bits: record.material_bits,
                    block_emission: record.block_emission,
                    block_id: record.block_id,
                    local_x: record.local_x,
                    local_y: record.local_y,
                    local_z: record.local_z,
                });
            }
            pending_counts[facing] += 1;

            if pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
                flush_static_model_pending_face(
                    builder,
                    facing,
                    &mut pending_counts,
                    None,
                    format,
                    store_raw_quads,
                    &mut total_committed,
                )?;
            }
            continue;
        }

        let Some(model) = cache_guard.get(&record.model_id) else {
            continue;
        };

        for quad_record in model {
            if quad_record.cull_face >= 0 && ((record.cull_mask >> quad_record.cull_face) & 1) != 0 {
                continue;
            }

            let facing = match usize::try_from(quad_record.normal_face) {
                Ok(value) if value < MODEL_QUAD_FACING_COUNT => value,
                _ => MODEL_QUAD_FACING_UNASSIGNED,
            };
            let quad = static_model_quad_to_native(*record, *quad_record);
            let slot = pending_counts[facing];
            {
                let pending = &mut builder.pending[facing];
                pending.quads[slot] = quad;
            }
            pending_counts[facing] += 1;

            if pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
                flush_static_model_pending_face(
                    builder,
                    facing,
                    &mut pending_counts,
                    None,
                    format,
                    store_raw_quads,
                    &mut total_committed,
                )?;
            }
        }
    }

    for facing in 0..MODEL_QUAD_FACING_COUNT {
        flush_static_model_pending_face(
            builder,
            facing,
            &mut pending_counts,
            None,
            format,
            store_raw_quads,
            &mut total_committed,
        )?;
    }

    Ok(total_committed)
}

unsafe fn flush_static_model_pending_face(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    total_committed: &mut i32,
) -> Result<(), i32> {
    let count = pending_counts[facing];
    if count == 0 {
        return Ok(());
    }

    let pending_address = builder.pending[facing].quads.as_ptr() as u64;
    let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
    let mut valid_count = count as i32;
    let validity = if let Some(analyzer_handle) = analyzer {
        let status = translucent::append_native_quad_batch_to_analyzer(
            analyzer_handle,
            pending_address,
            count as i32,
            facing as i32,
            builder.pending[facing].packed_normals.as_ptr(),
            validity_address,
            &mut valid_count,
        );
        if status != OK {
            return Err(status);
        }
        Some(slice::from_raw_parts(validity_address as *const u8, count))
    } else {
        None
    };
    let committed = section_builder_append_batch_encoded(
        builder,
        facing,
        pending_address,
        count,
        validity,
        format,
        store_raw_quads,
    )?;
    *total_committed = total_committed.checked_add(committed).ok_or(ERR_CAPACITY)?;
    pending_counts[facing] = 0;
    Ok(())
}

fn light_block_record_to_quad(record: LightBlockRecord) -> NativeQuad {
    let emission = record.block_emission.clamp(0, 255);
    let x = record.local_x as f32 + 0.25;
    let y = record.local_y as f32 + 0.25;
    let z = record.local_z as f32 + 0.25;
    let light = (emission << 4) | (emission << 20);
    let vertex = QuadVertex {
        x,
        y,
        z,
        color: 0,
        ao: 1.0,
        u: 0.0,
        v: 0.0,
        light,
    };

    NativeQuad {
        vertices: [vertex; 4],
        block_emission: emission as u8,
        render_type: 0,
        ignore_mid_block: 1,
        _padding: 0,
        block_id: record.block_id,
        local_x: record.local_x,
        local_y: record.local_y,
        local_z: record.local_z,
        material_bits: record.material_bits,
    }
}

fn static_model_quad_to_native(
    block: StaticModelBlockRecord,
    quad_record: StaticModelQuadRecord,
) -> NativeQuad {
    let material_bits = if block.material_bits != 0 {
        block.material_bits
    } else {
        quad_record.material_bits
    };
    let block_emission = if block.block_emission != 0 {
        block.block_emission
    } else {
        quad_record.block_emission
    };
    let render_type = if block.render_type != 0 {
        block.render_type
    } else {
        quad_record.render_type
    };
    let mut vertices = [QuadVertex::default(); 4];

    for (index, vertex) in vertices.iter_mut().enumerate() {
        let source = quad_record.vertices[index];
        *vertex = QuadVertex {
            x: block.local_x as f32 + block.offset_x + source.x,
            y: block.local_y as f32 + block.offset_y + source.y,
            z: block.local_z as f32 + block.offset_z + source.z,
            color: argb_to_abgr(source.color),
            ao: if quad_record.shade != 0 { 1.0 } else { 1.0 },
            u: source.u,
            v: source.v,
            light: source.light,
        };
    }

    NativeQuad {
        vertices,
        block_emission: block_emission.clamp(0, 255) as u8,
        render_type: render_type.clamp(0, 255) as u8,
        ignore_mid_block: 0,
        _padding: 0,
        block_id: block.block_id,
        local_x: block.local_x,
        local_y: block.local_y,
        local_z: block.local_z,
        material_bits,
    }
}

unsafe fn section_builder_append_native_section_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    pass_id: i32,
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    if record_count == 0 {
        return Ok(0);
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<NativeSectionBlockRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const NativeSectionBlockRecord, record_count);
    let states_guard = native_meshing_states()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let selectors_guard = native_model_selectors()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let models_guard = static_model_cache()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let mut total_committed = 0i32;
    let mut pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];
    let mut fluid_pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];
    let mut model_ids = Vec::with_capacity(8);

    for record in records {
        let Some(state) = states_guard.get(&record.state_id).copied() else {
            continue;
        };
        if (state.flags & STATE_FLAG_AIR) != 0 {
            continue;
        }

        if (state.flags & STATE_FLAG_LIGHT_BLOCK) != 0 && pass_id == 1 {
            push_native_section_quad(
                builder,
                light_block_record_to_quad(LightBlockRecord {
                    material_bits: state.material_bits,
                    block_emission: state.block_emission,
                    block_id: choose_block_id(record.block_id, state.block_id),
                    local_x: record.local_x,
                    local_y: record.local_y,
                    local_z: record.local_z,
                }),
                0,
                MODEL_QUAD_FACING_UNASSIGNED,
                &mut pending_counts,
                analyzer,
                format,
                store_raw_quads,
                &mut total_committed,
            )?;
        }

        if (state.flags & STATE_FLAG_MODEL) != 0 && state.pass_id == pass_id {
            model_ids.clear();
            resolve_selector_model_ids(
                state.selector_id,
                record_seed(*record),
                &selectors_guard,
                &mut model_ids,
            )?;

            for model_id in &model_ids {
                let Some(model) = models_guard.get(model_id) else {
                    continue;
                };

                for quad_record in model {
                    if native_section_culls_quad(record, *quad_record, &states_guard) {
                        continue;
                    }

                    let facing = match usize::try_from(quad_record.normal_face) {
                        Ok(value) if value < MODEL_QUAD_FACING_COUNT => value,
                        _ => MODEL_QUAD_FACING_UNASSIGNED,
                    };
                    let quad = static_model_quad_to_native_section(*record, state, *quad_record);
                    push_native_section_quad(
                        builder,
                        quad,
                        quad_record.packed_normal,
                        facing,
                        &mut pending_counts,
                        analyzer,
                        format,
                        store_raw_quads,
                        &mut total_committed,
                    )?;
                }
            }
        }

        if (state.flags & STATE_FLAG_FLUID) != 0
            && (record.flags & NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID) == 0
            && state.fluid_pass_id == pass_id
        {
            for (fluid_record, facing) in native_section_fluid_faces(record, state, &states_guard) {
                push_native_section_fluid_face(
                    builder,
                    fluid_record,
                    facing,
                    &mut pending_counts,
                    &mut fluid_pending_counts,
                    analyzer,
                    format,
                    store_raw_quads,
                    &mut total_committed,
                )?;
            }
        }
    }

    for facing in 0..MODEL_QUAD_FACING_COUNT {
        flush_static_model_pending_face(
            builder,
            facing,
            &mut pending_counts,
            analyzer,
            format,
            store_raw_quads,
            &mut total_committed,
        )?;
        flush_native_section_pending_fluid_face(
            builder,
            facing,
            &mut fluid_pending_counts,
            analyzer,
            format,
            store_raw_quads,
            &mut total_committed,
        )?;
    }

    Ok(total_committed)
}

unsafe fn push_native_section_quad(
    builder: &mut NativeSectionMeshBuilder,
    quad: NativeQuad,
    packed_normal: i32,
    facing: usize,
    pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    total_committed: &mut i32,
) -> Result<(), i32> {
    let slot = pending_counts[facing];
    builder.pending[facing].quads[slot] = quad;
    builder.pending[facing].packed_normals[slot] = packed_normal;
    pending_counts[facing] += 1;

    if pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
        flush_static_model_pending_face(
            builder,
            facing,
            pending_counts,
            analyzer,
            format,
            store_raw_quads,
            total_committed,
        )?;
    }

    Ok(())
}

unsafe fn push_native_section_fluid_face(
    builder: &mut NativeSectionMeshBuilder,
    record: FluidFaceRecord,
    facing: usize,
    static_pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    fluid_pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    total_committed: &mut i32,
) -> Result<(), i32> {
    if static_pending_counts[facing] != 0 {
        flush_static_model_pending_face(
            builder,
            facing,
            static_pending_counts,
            analyzer,
            format,
            store_raw_quads,
            total_committed,
        )?;
    }

    let slot = fluid_pending_counts[facing];
    builder.pending[facing].fluid_face_records[slot] = record;
    fluid_pending_counts[facing] += 1;

    if fluid_pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
        flush_native_section_pending_fluid_face(
            builder,
            facing,
            fluid_pending_counts,
            analyzer,
            format,
            store_raw_quads,
            total_committed,
        )?;
    }

    Ok(())
}

unsafe fn flush_native_section_pending_fluid_face(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    fluid_pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    total_committed: &mut i32,
) -> Result<(), i32> {
    let count = fluid_pending_counts[facing];
    if count == 0 {
        return Ok(());
    }

    let analyzer = analyzer.map(|handle| (handle, facing as i32));
    let (valid, committed) = section_builder_append_fluid_face_records_encoded(
        builder,
        facing,
        builder.pending[facing].fluid_face_records.as_ptr() as u64,
        count,
        std::mem::size_of::<FluidFaceRecord>(),
        analyzer,
        format,
        store_raw_quads,
    )?;
    if valid != committed {
        return Err(ERR_INVALID_ARGUMENT);
    }
    *total_committed = total_committed.checked_add(committed).ok_or(ERR_CAPACITY)?;
    fluid_pending_counts[facing] = 0;
    Ok(())
}

fn resolve_selector_model_ids(
    selector_id: i32,
    seed: u64,
    selectors: &HashMap<i32, NativeModelSelector>,
    output: &mut Vec<i32>,
) -> Result<(), i32> {
    let Some(selector) = selectors.get(&selector_id) else {
        return Ok(());
    };

    match selector.kind {
        SELECTOR_DIRECT => {
            if let Some(entry) = selector.entries.first() {
                output.push(entry.target_id);
            }
        }
        SELECTOR_WEIGHTED => {
            if selector.total_weight <= 0 {
                return Ok(());
            }
            let mut choice = legacy_next_int(seed, selector.total_weight);
            for entry in &selector.entries {
                choice -= entry.weight;
                if choice < 0 {
                    resolve_selector_model_ids(entry.target_id, seed, selectors, output)?;
                    break;
                }
            }
        }
        SELECTOR_GROUP => {
            let child_seed = legacy_next_long(seed);
            for entry in &selector.entries {
                resolve_selector_model_ids(entry.target_id, child_seed, selectors, output)?;
            }
        }
        _ => return Err(ERR_INVALID_ARGUMENT),
    }

    Ok(())
}

fn legacy_next_int(seed: u64, bound: i32) -> i32 {
    if bound <= 0 {
        return 0;
    }

    let mut state = legacy_set_seed(seed);
    if (bound & (bound - 1)) == 0 {
        return (((bound as i64) * (legacy_next(&mut state, 31) as i64)) >> 31) as i32;
    }

    loop {
        let value = legacy_next(&mut state, 31) as i32;
        let result = value % bound;
        if value - result + (bound - 1) >= 0 {
            return result;
        }
    }
}

fn legacy_next_long(seed: u64) -> u64 {
    let mut state = legacy_set_seed(seed);
    let high = legacy_next(&mut state, 32) as u64;
    let low = legacy_next(&mut state, 32) as u64;
    (high << 32).wrapping_add(low)
}

fn legacy_set_seed(seed: u64) -> u64 {
    (seed ^ 25_214_903_917) & 281_474_976_710_655
}

fn legacy_next(state: &mut u64, bits: u32) -> u32 {
    *state = state
        .wrapping_mul(25_214_903_917)
        .wrapping_add(11)
        & 281_474_976_710_655;
    (*state >> (48 - bits)) as u32
}

fn record_seed(record: NativeSectionBlockRecord) -> u64 {
    ((record.seed_hi as u32 as u64) << 32) | (record.seed_lo as u32 as u64)
}

fn native_section_culls_quad(
    record: &NativeSectionBlockRecord,
    quad_record: StaticModelQuadRecord,
    states: &HashMap<i32, NativeMeshingState>,
) -> bool {
    if quad_record.cull_face < 0 || quad_record.cull_face >= 6 {
        return false;
    }

    let neighbor_id = record.neighbor_state_ids[quad_record.cull_face as usize];
    let Some(neighbor) = states.get(&neighbor_id) else {
        return false;
    };

    (neighbor.flags & (STATE_FLAG_FULL_OCCLUSION | STATE_FLAG_SOLID_RENDER)) != 0
        || (neighbor.skip_group != 0 && neighbor.skip_group == states
            .get(&record.state_id)
            .map(|state| state.skip_group)
            .unwrap_or(0)
            && (neighbor.flags & STATE_FLAG_FULL_OCCLUSION) != 0)
}

fn static_model_quad_to_native_section(
    block: NativeSectionBlockRecord,
    state: NativeMeshingState,
    quad_record: StaticModelQuadRecord,
) -> NativeQuad {
    let mut vertices = [QuadVertex::default(); 4];
    let offset = native_model_offset(block, state);
    let light = native_quad_lighting(&block, &quad_record, state);
    let tint = native_tint_color(&block, state, false);

    for (index, vertex) in vertices.iter_mut().enumerate() {
        let source = quad_record.vertices[index];
        let mut color = source.color;
        if quad_record.tint_index != -1
            || state.tint_type == TINT_GRASS
            || state.tint_type == TINT_FOLIAGE
            || state.tint_type == TINT_FORCE_GRASS
            || state.tint_type == TINT_DOUBLE_PLANT_GRASS
            || state.tint_type == TINT_CONSTANT
            || state.tint_type == TINT_SPRUCE
            || state.tint_type == TINT_BIRCH
        {
            color = multiply_argb(color, tint);
        }
        *vertex = QuadVertex {
            x: block.local_x as f32 + offset.0 + source.x,
            y: block.local_y as f32 + offset.1 + source.y,
            z: block.local_z as f32 + offset.2 + source.z,
            color: argb_to_abgr(color),
            ao: light.ao[index],
            u: source.u,
            v: source.v,
            light: if source.light > 0 { source.light } else { light.lm[index] },
        };
    }

    NativeQuad {
        vertices,
        block_emission: state.block_emission.clamp(0, 255) as u8,
        render_type: 0,
        ignore_mid_block: 0,
        _padding: 0,
        block_id: choose_block_id(block.block_id, state.block_id),
        local_x: block.absolute_x,
        local_y: block.absolute_y,
        local_z: block.absolute_z,
        material_bits: if state.material_bits != 0 {
            state.material_bits
        } else {
            quad_record.material_bits
        },
    }
}

#[derive(Clone, Copy, Debug)]
struct NativeQuadLight {
    ao: [f32; 4],
    lm: [i32; 4],
}

fn native_quad_lighting(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    state: NativeMeshingState,
) -> NativeQuadLight {
    let light_face = if (0..6).contains(&quad.light_face) {
        quad.light_face
    } else if (0..6).contains(&quad.cull_face) {
        quad.cull_face
    } else {
        1
    };
    let use_smooth = quad.has_ao != 0;
    if use_smooth {
        smooth_lighting(block, quad, state, light_face, quad.shade != 0)
    } else {
        flat_lighting(block, quad, state, light_face, quad.shade != 0)
    }
}

fn flat_lighting(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    state: NativeMeshingState,
    light_face: i32,
    shade: bool,
) -> NativeQuadLight {
    let sample_dir = if (0..6).contains(&quad.cull_face) {
        quad.cull_face
    } else if (quad.flags & MODEL_QUAD_FLAG_ALIGNED) != 0
        || ((quad.flags & MODEL_QUAD_FLAG_PARALLEL) != 0
            && (state.flags & STATE_FLAG_FULL_OCCLUSION) != 0)
    {
        light_face
    } else {
        -1
    };
    let word = if sample_dir >= 0 {
        light_word(block, dir_step(sample_dir))
    } else {
        block.light_words[13]
    };
    let lm = if unpack_em(block.light_words[13]) && sample_dir >= 0 {
        LIGHT_FULL_BRIGHT
    } else if sample_dir >= 0 {
        let origin = block.light_words[13];
        let adj = word;
        pack_light(std::cmp::max(unpack_bl(adj), unpack_lu(origin)), unpack_sl(adj))
    } else {
        get_emissive_lightmap(word)
    };
    NativeQuadLight {
        ao: [ambient_shade(light_face, shade); 4],
        lm: [lm; 4],
    }
}

fn smooth_lighting(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    state: NativeMeshingState,
    light_face: i32,
    shade: bool,
) -> NativeQuadLight {
    let parallel = (quad.flags & MODEL_QUAD_FLAG_PARALLEL) != 0;
    let aligned = (quad.flags & MODEL_QUAD_FLAG_ALIGNED) != 0
        || (parallel && (state.flags & STATE_FLAG_FULL_OCCLUSION) != 0);
    let partial = (quad.flags & MODEL_QUAD_FLAG_PARTIAL) != 0;

    if aligned && !partial {
        let face = ao_face_data(block, light_face, true);
        let (lm, mut ao) = map_ao_corners(light_face, face.lm, face.ao);
        for value in &mut ao {
            *value *= ambient_shade(light_face, shade);
        }
        return NativeQuadLight { ao, lm };
    }

    let mut out = NativeQuadLight {
        ao: [1.0; 4],
        lm: [get_emissive_lightmap(block.light_words[13]); 4],
    };
    for i in 0..4 {
        let source = quad.vertices[i];
        let weights = corner_weights(light_face, source.x.clamp(0.0, 1.0), source.y.clamp(0.0, 1.0), source.z.clamp(0.0, 1.0));
        let depth = face_depth(light_face, source.x, source.y, source.z);

        let (ao, lm) = if aligned {
            blend_ao_face(ao_face_data(block, light_face, true), weights)
        } else if parallel {
            if java_float_equal(depth, 1.0) {
                blend_ao_face(ao_face_data(block, light_face, false), weights)
            } else {
                blend_inset_ao_face(block, light_face, depth, 1.0 - depth, weights)
            }
        } else if java_float_equal(depth, 0.0) {
            blend_ao_face(ao_face_data(block, light_face, true), weights)
        } else if java_float_equal(depth, 1.0) {
            blend_ao_face(ao_face_data(block, light_face, false), weights)
        } else {
            blend_inset_ao_face(block, light_face, depth, 1.0 - depth, weights)
        };
        out.ao[i] = ao * ambient_shade(light_face, shade);
        out.lm[i] = lm;
    }
    out
}

#[derive(Clone, Copy)]
struct AoFace {
    lm: [i32; 4],
    ao: [f32; 4],
}

fn ao_face_data(block: &NativeSectionBlockRecord, direction: i32, offset: bool) -> AoFace {
    let (dx, dy, dz) = if offset { dir_step(direction) } else { (0, 0, 0) };
    let adj = light_word(block, (dx, dy, dz));
    let origin = block.light_words[13];
    let calm = if offset && unpack_fo(adj) { get_lightmap(origin) } else { get_lightmap(adj) };
    let caem = if offset && unpack_fo(adj) { unpack_em(origin) } else { unpack_em(adj) };
    let caao = unpack_ao(adj);
    let faces = ao_neighbor_faces(direction);

    let e0 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[0])));
    let e1 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[1])));
    let e2 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[2])));
    let e3 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[3])));
    let e = [e0, e1, e2, e3];
    let elm = e.map(get_lightmap);
    let eao = e.map(unpack_ao);
    let eop = e.map(unpack_op);
    let eem = e.map(unpack_em);

    let c0 = corner_word(block, (dx, dy, dz), faces[0], faces[2], eop[2] && eop[0], e[0]);
    let c1 = corner_word(block, (dx, dy, dz), faces[0], faces[3], eop[3] && eop[0], e[0]);
    let c2 = corner_word(block, (dx, dy, dz), faces[1], faces[2], eop[2] && eop[1], e[1]);
    let c3 = corner_word(block, (dx, dy, dz), faces[1], faces[3], eop[3] && eop[1], e[1]);
    let c = [c1, c0, c2, c3];

    AoFace {
        ao: [
            (eao[3] + eao[0] + unpack_ao(c[0]) + caao) * 0.25,
            (eao[2] + eao[0] + unpack_ao(c[1]) + caao) * 0.25,
            (eao[2] + eao[1] + unpack_ao(c[2]) + caao) * 0.25,
            (eao[3] + eao[1] + unpack_ao(c[3]) + caao) * 0.25,
        ],
        lm: [
            calculate_corner_brightness(elm[3], elm[0], get_lightmap(c[0]), calm, eem[3], eem[0], unpack_em(c[0]), caem),
            calculate_corner_brightness(elm[2], elm[0], get_lightmap(c[1]), calm, eem[2], eem[0], unpack_em(c[1]), caem),
            calculate_corner_brightness(elm[2], elm[1], get_lightmap(c[2]), calm, eem[2], eem[1], unpack_em(c[2]), caem),
            calculate_corner_brightness(elm[3], elm[1], get_lightmap(c[3]), calm, eem[3], eem[1], unpack_em(c[3]), caem),
        ],
    }
}

fn native_section_fluid_faces(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &HashMap<i32, NativeMeshingState>,
) -> Vec<(FluidFaceRecord, usize)> {
    if state.fluid_type != FLUID_WATER && state.fluid_type != FLUID_LAVA {
        return Vec::new();
    }
    let mut out = Vec::with_capacity(8);
    let h = fluid_height(block, states, 0, 0, 0, 1);
    let heights = if h >= 1.0 {
        [1.0; 4]
    } else {
        let hn = fluid_height(block, states, 0, 0, -1, 2);
        let hs = fluid_height(block, states, 0, 0, 1, 3);
        let hw = fluid_height(block, states, -1, 0, 0, 4);
        let he = fluid_height(block, states, 1, 0, 0, 5);
        [
            fluid_corner_height(block, states, h, hn, hw, -1, 0, -1),
            fluid_corner_height(block, states, h, hs, hw, -1, 0, 1),
            fluid_corner_height(block, states, h, hs, he, 1, 0, 1),
            fluid_corner_height(block, states, h, hn, he, 1, 0, -1),
        ]
    };
    let cull_up = fluid_side_occluded(block, state, states, 1);
    let cull_down = fluid_side_occluded(block, state, states, 0) || !fluid_side_exposed(block, states, 0, 0, -1, 0.8888889);
    let color = argb_to_abgr(native_tint_color(block, state, true));
    let light = get_emissive_lightmap(block.light_words[13]);
    let y_offset = if cull_down { 0.0 } else { 0.001 };
    let top_exposed = fluid_side_exposed(block, states, 0, 1, 0, heights.iter().copied().fold(1.0, f32::min));
    fluid_diag(block, state, "top-check", cull_up, top_exposed, heights, color, light, None);

    let mut render_heights = heights;
    if !cull_up && top_exposed {
        for height in &mut render_heights {
            *height -= 0.001;
        }
        let top = if block.fluid_flow_x == 0.0 && block.fluid_flow_z == 0.0 && state.fluid_falling == 0 {
            let sprite = state.fluid_still;
            [
                (sprite_u(sprite, 0.0), sprite_v(sprite, 0.0)),
                (sprite_u(sprite, 0.0), sprite_v(sprite, 1.0)),
                (sprite_u(sprite, 1.0), sprite_v(sprite, 1.0)),
                (sprite_u(sprite, 1.0), sprite_v(sprite, 0.0)),
            ]
        } else {
            let sprite = state.fluid_flow;
            let dir = block.fluid_flow_z.atan2(block.fluid_flow_x) - std::f32::consts::FRAC_PI_2;
            let sin = dir.sin() * 0.25;
            let cos = dir.cos() * 0.25;
            [
                (sprite_u(sprite, 0.5 + (-cos - sin)), sprite_v(sprite, 0.5 + -cos + sin)),
                (sprite_u(sprite, 0.5 + -cos + sin), sprite_v(sprite, 0.5 + cos + sin)),
                (sprite_u(sprite, 0.5 + cos + sin), sprite_v(sprite, 0.5 + (cos - sin))),
                (sprite_u(sprite, 0.5 + (cos - sin)), sprite_v(sprite, 0.5 + (-cos - sin))),
            ]
        };
        let top = shrink_fluid_uvs(top, state.fluid_still.shrink);
        let top_facing = if fluid_top_aligned(render_heights) {
            MODEL_QUAD_FACING_POS_Y
        } else {
            MODEL_QUAD_FACING_UNASSIGNED
        };
        let top_face_kind = if fluid_top_crease_ne_sw(render_heights) {
            FLUID_FACE_TOP_NE_SW
        } else {
            FLUID_FACE_TOP_NW_SE
        };
        let top_record_uvs = if top_face_kind == FLUID_FACE_TOP_NE_SW {
            [top[3], top[0], top[1], top[2]]
        } else {
            top
        };
        let top_face = fluid_semantic_face(
            state,
            block,
            top_facing,
            false,
            top_face_kind,
            0.0,
            render_heights,
            [0.0; 4],
            top_record_uvs,
            color,
            1.0,
            light,
        );
        fluid_record_diag(block, "top-record", &top_face.0, top_face.1);
        out.push(top_face);
        fluid_diag(block, state, "top-emitted", cull_up, top_exposed, render_heights, color, light, Some(top_facing));
        if fluid_backward_up_face(block, state, states) {
            let backward_facing = if top_facing == MODEL_QUAD_FACING_POS_Y {
                MODEL_QUAD_FACING_NEG_Y
            } else {
                MODEL_QUAD_FACING_UNASSIGNED
            };
            let backward_face = fluid_semantic_face(
                state,
                block,
                backward_facing,
                true,
                top_face_kind,
                0.0,
                render_heights,
                [0.0; 4],
                top_record_uvs,
                color,
                1.0,
                light,
            );
            fluid_record_diag(block, "top-back-record", &backward_face.0, backward_face.1);
            out.push(backward_face);
        }
    }

    if !cull_down {
        let sprite = state.fluid_still;
        out.push(fluid_semantic_face(
            state,
            block,
            MODEL_QUAD_FACING_NEG_Y,
            false,
            FLUID_FACE_BOTTOM,
            y_offset,
            [0.0; 4],
            [0.0; 4],
            [
                (sprite.u0, sprite.v1),
                (sprite.u0, sprite.v0),
                (sprite.u1, sprite.v0),
                (sprite.u1, sprite.v1),
            ],
            color,
            1.0,
            light,
        ));
    }

    let sides = [
        (2, MODEL_QUAD_FACING_NEG_Z, MODEL_QUAD_FACING_POS_Z, 0.8, render_heights[0], render_heights[3], 0.0, 0.001, 1.0, 0.001),
        (3, MODEL_QUAD_FACING_POS_Z, MODEL_QUAD_FACING_NEG_Z, 0.8, render_heights[2], render_heights[1], 1.0, 0.999, 0.0, 0.999),
        (4, MODEL_QUAD_FACING_NEG_X, MODEL_QUAD_FACING_POS_X, 0.6, render_heights[1], render_heights[0], 0.001, 1.0, 0.001, 0.0),
        (5, MODEL_QUAD_FACING_POS_X, MODEL_QUAD_FACING_NEG_X, 0.6, render_heights[3], render_heights[2], 0.999, 0.0, 0.999, 1.0),
    ];
    for (dir, facing, opposite_facing, shade, h1, h2, x1, z1, x2, z2) in sides {
        if !fluid_side_occluded(block, state, states, dir) && fluid_side_exposed(block, states, dir_step(dir).0, dir_step(dir).1, dir_step(dir).2, h1.max(h2)) {
            let is_overlay = fluid_side_uses_overlay(block, state, states, dir);
            let sprite = if is_overlay {
                state.fluid_overlay
            } else {
                state.fluid_flow
            };
            let u1 = sprite_u(sprite, 0.0);
            let u2 = sprite_u(sprite, 0.5);
            let v1 = sprite_v(sprite, (1.0 - h1) * 0.5);
            let v2 = sprite_v(sprite, (1.0 - h2) * 0.5);
            let v3 = sprite_v(sprite, 0.5);
            out.push(fluid_semantic_face(
                state,
                block,
                facing,
                false,
                FLUID_FACE_SIDE,
                y_offset,
                [h1, h2, 0.0, 0.0],
                [x1, z1, x2, z2],
                [(u2, v2), (u2, v3), (u1, v3), (u1, v1)],
                color,
                shade,
                light,
            ));
            if !is_overlay {
                out.push(fluid_semantic_face(
                    state,
                    block,
                    opposite_facing,
                    true,
                    FLUID_FACE_SIDE,
                    y_offset,
                    [h1, h2, 0.0, 0.0],
                    [x1, z1, x2, z2],
                    [(u2, v2), (u2, v3), (u1, v3), (u1, v1)],
                    color,
                    shade,
                    light,
                ));
            }
        }
    }
    out
}

fn fluid_diag(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    phase: &str,
    cull_up: bool,
    top_exposed: bool,
    heights: [f32; 4],
    color: i32,
    light: i32,
    facing: Option<usize>,
) {
    if std::env::var_os("MATTMC_NATIVE_FLUID_DIAG").is_none() {
        return;
    }
    if state.fluid_type != FLUID_WATER {
        return;
    }
    if !(0..=160).contains(&block.absolute_x)
        || !(60..=72).contains(&block.absolute_y)
        || !(360..=660).contains(&block.absolute_z)
    {
        return;
    }
    if phase == "top-check" && cull_up {
        return;
    }
    let index = FLUID_DIAG_COUNT.fetch_add(1, Ordering::Relaxed);
    if index >= 240 {
        return;
    }
    let color_u = color as u32;
    eprintln!(
        "MATTMC_NATIVE_FLUID_DIAG #{index} {phase} pos={},{},{} state={} fluid_block_id={} state_fluid_block_id={} pass={} material={} cull_up={} top_exposed={} heights={:.4},{:.4},{:.4},{:.4} color=0x{color_u:08x} alpha={} light=0x{:08x} facing={:?} sprite_still=({:.5},{:.5},{:.5},{:.5}) flow=({:.4},{:.4})",
        block.absolute_x,
        block.absolute_y,
        block.absolute_z,
        block.state_id,
        block.fluid_block_id,
        state.fluid_block_id,
        state.fluid_pass_id,
        state.fluid_material_bits,
        cull_up,
        top_exposed,
        heights[0],
        heights[1],
        heights[2],
        heights[3],
        (color_u >> 24) & 0xff,
        light,
        facing,
        state.fluid_still.u0,
        state.fluid_still.u1,
        state.fluid_still.v0,
        state.fluid_still.v1,
        block.fluid_flow_x,
        block.fluid_flow_z,
    );
}

fn fluid_record_diag(block: &NativeSectionBlockRecord, phase: &str, record: &FluidFaceRecord, facing: usize) {
    if std::env::var_os("MATTMC_NATIVE_FLUID_DIAG").is_none() {
        return;
    }
    if !(0..=160).contains(&block.absolute_x)
        || !(60..=72).contains(&block.absolute_y)
        || !(360..=660).contains(&block.absolute_z)
    {
        return;
    }
    let index = FLUID_DIAG_COUNT.fetch_add(1, Ordering::Relaxed);
    if index >= 240 {
        return;
    }
    let color = record.colors[0] as u32;
    eprintln!(
        "MATTMC_NATIVE_FLUID_DIAG #{index} {phase} pos={},{},{} facing={} flip={} face={} origin={:.1},{:.1},{:.1} heights={:.4},{:.4},{:.4},{:.4} uv0={:.5},{:.5} uv1={:.5},{:.5} uv2={:.5},{:.5} uv3={:.5},{:.5} color0=0x{color:08x} ao0={:.4} light0=0x{:08x} normal=0x{:08x} material={} pass={}",
        block.absolute_x,
        block.absolute_y,
        block.absolute_z,
        facing,
        record.flip,
        record.face_kind,
        record.origin_x,
        record.origin_y,
        record.origin_z,
        record.heights[0],
        record.heights[1],
        record.heights[2],
        record.heights[3],
        record.uvs[0],
        record.uvs[1],
        record.uvs[2],
        record.uvs[3],
        record.uvs[4],
        record.uvs[5],
        record.uvs[6],
        record.uvs[7],
        record.aos[0],
        record.lights[0],
        record.packed_normal,
        record.material_bits,
        record.render_type,
    );
}

fn fluid_top_aligned(heights: [f32; 4]) -> bool {
    fluid_aligned_equals(heights[3], heights[0])
        && fluid_aligned_equals(heights[0], heights[2])
        && fluid_aligned_equals(heights[2], heights[1])
        && fluid_aligned_equals(heights[1], heights[3])
}

fn fluid_aligned_equals(a: f32, b: f32) -> bool {
    (a - b).abs() <= FLUID_ALIGNED_EQUALS_EPSILON
}

fn fluid_top_crease_ne_sw(heights: [f32; 4]) -> bool {
    fluid_top_aligned(heights)
        || heights[3] > heights[0] && heights[3] > heights[2]
        || heights[3] < heights[0] && heights[3] < heights[2]
        || heights[1] > heights[0] && heights[1] > heights[2]
        || heights[1] < heights[0] && heights[1] < heights[2]
}

fn fluid_semantic_face(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    mut facing: usize,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> (FluidFaceRecord, usize) {
    if std::env::var_os("MATTMC_NATIVE_FLUID_FORCE_UNASSIGNED").is_some() {
        facing = MODEL_QUAD_FACING_UNASSIGNED;
    }

    let mut record = FluidFaceRecord {
        packed_normal: 0,
        material_bits: state.fluid_material_bits,
        block_emission: state.block_emission,
        render_type: 1,
        ignore_mid_block: 0,
        block_id: choose_block_id(block.fluid_block_id, state.fluid_block_id),
        local_x: block.absolute_x,
        local_y: block.absolute_y,
        local_z: block.absolute_z,
        face_kind,
        flip: if flip { 1 } else { 0 },
        origin_x: block.local_x as f32,
        origin_y: block.local_y as f32,
        origin_z: block.local_z as f32,
        y_offset,
        heights,
        side_coords,
        uvs: [
            uvs[0].0, uvs[0].1, uvs[1].0, uvs[1].1, uvs[2].0, uvs[2].1, uvs[3].0, uvs[3].1,
        ],
        colors: [color; 4],
        aos: [ao; 4],
        lights: [light; 4],
    };
    if let Ok(quad) = fluid_face_record_to_quad(record) {
        record.packed_normal = norm_i8_pack_from_quad(&quad);
    }
    (record, facing)
}

fn fluid_side_uses_overlay(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &HashMap<i32, NativeMeshingState>,
    direction: i32,
) -> bool {
    if state.fluid_type != FLUID_WATER || state.fluid_overlay_valid == 0 {
        return false;
    }
    let (dx, dy, dz) = dir_step(direction);
    let Some(neighbor_id) = neighborhood_state_id(block, dx, dy, dz) else {
        return false;
    };
    let Some(neighbor) = states.get(&neighbor_id) else {
        return false;
    };
    (neighbor.flags & STATE_FLAG_AIR) == 0 && (neighbor.flags & STATE_FLAG_CAN_OCCLUDE) == 0
}

fn sprite_u(sprite: FluidSprite, value: f32) -> f32 {
    sprite.u0 + (sprite.u1 - sprite.u0) * value
}

fn sprite_v(sprite: FluidSprite, value: f32) -> f32 {
    sprite.v0 + (sprite.v1 - sprite.v0) * value
}

fn shrink_fluid_uvs(mut uvs: [(f32, f32); 4], shrink: f32) -> [(f32, f32); 4] {
    if shrink == 0.0 {
        return uvs;
    }
    let avg_u = (uvs[0].0 + uvs[1].0 + uvs[2].0 + uvs[3].0) * 0.25;
    let avg_v = (uvs[0].1 + uvs[1].1 + uvs[2].1 + uvs[3].1) * 0.25;
    for uv in &mut uvs {
        uv.0 += (avg_u - uv.0) * shrink;
        uv.1 += (avg_v - uv.1) * shrink;
    }
    uvs
}

fn choose_block_id(record_block_id: i32, state_block_id: i32) -> i32 {
    if record_block_id >= 0 {
        record_block_id
    } else {
        state_block_id
    }
}

fn native_model_offset(block: NativeSectionBlockRecord, state: NativeMeshingState) -> (f32, f32, f32) {
    if block.legacy_offset_x != 0.0 || block.legacy_offset_y != 0.0 || block.legacy_offset_z != 0.0 {
        return (block.legacy_offset_x, block.legacy_offset_y, block.legacy_offset_z);
    }
    if state.offset_type == OFFSET_NONE {
        return (0.0, 0.0, 0.0);
    }
    let seed = mth_seed(block.absolute_x, 0, block.absolute_z);
    let max_h = state.max_horizontal_offset;
    let x = ((((seed & 15) as f32) / 15.0 - 0.5) * 0.5).clamp(-max_h, max_h);
    let z = ((((seed >> 8 & 15) as f32) / 15.0 - 0.5) * 0.5).clamp(-max_h, max_h);
    if state.offset_type == OFFSET_XYZ {
        let y = (((seed >> 4 & 15) as f32) / 15.0 - 1.0) * state.max_vertical_offset;
        (x, y, z)
    } else if state.offset_type == OFFSET_XZ {
        (x, 0.0, z)
    } else {
        (0.0, 0.0, 0.0)
    }
}

fn mth_seed(x: i32, y: i32, z: i32) -> i64 {
    let mut value = (x as i64)
        .wrapping_mul(3_129_871)
        ^ (z as i64).wrapping_mul(116_129_781)
        ^ (y as i64);
    value = value
        .wrapping_mul(value)
        .wrapping_mul(42_317_861)
        .wrapping_add(value.wrapping_mul(11));
    value >> 16
}

fn native_tint_color(block: &NativeSectionBlockRecord, state: NativeMeshingState, fluid: bool) -> i32 {
    if fluid {
        if state.fluid_type == FLUID_WATER && block.fluid_tint != -1 {
            return block.fluid_tint;
        }
        return -1;
    }
    match state.tint_type {
        TINT_SPRUCE => return 0xff619961u32 as i32,
        TINT_BIRCH => return 0xff80a755u32 as i32,
        _ => {}
    }

    if block.tint != -1 {
        return block.tint;
    }
    match state.tint_type {
        TINT_NONE => -1,
        TINT_REDSTONE | TINT_CONSTANT | TINT_STEM => block.tint,
        TINT_WATER => block.tint,
        _ => block.tint,
    }
}

fn multiply_argb(color: i32, tint: i32) -> i32 {
    if tint == -1 {
        return color;
    }
    let ca = (color as u32 >> 24) & 0xff;
    let cr = (color as u32 >> 16) & 0xff;
    let cg = (color as u32 >> 8) & 0xff;
    let cb = color as u32 & 0xff;
    let tr = (tint as u32 >> 16) & 0xff;
    let tg = (tint as u32 >> 8) & 0xff;
    let tb = tint as u32 & 0xff;
    ((ca << 24) | ((cr * tr / 255) << 16) | ((cg * tg / 255) << 8) | (cb * tb / 255)) as i32
}

fn fluid_height(
    block: &NativeSectionBlockRecord,
    states: &HashMap<i32, NativeMeshingState>,
    dx: i32,
    dy: i32,
    dz: i32,
    fallback_neighbor_index: usize,
) -> f32 {
    let state_id = neighborhood_state_id(block, dx, dy, dz)
        .unwrap_or_else(|| block.neighbor_state_ids[fallback_neighbor_index]);
    let Some(sample) = states.get(&state_id).copied() else {
        return 0.0;
    };
    let Some(center) = states.get(&block.state_id).copied() else {
        return 0.0;
    };
    if sample.fluid_type == center.fluid_type && sample.fluid_type != 0 {
        if neighborhood_state_id(block, dx, dy + 1, dz)
            .and_then(|above_id| states.get(&above_id))
            .map(|above| above.fluid_type == center.fluid_type)
            .unwrap_or(false)
        {
            1.0
        } else {
            sample.fluid_own_height
        }
    } else if (sample.flags & STATE_FLAG_BLOCKS_MOTION) == 0 {
        0.0
    } else {
        -1.0
    }
}

fn fluid_corner_height(
    block: &NativeSectionBlockRecord,
    states: &HashMap<i32, NativeMeshingState>,
    center: f32,
    hx: f32,
    hz: f32,
    dx: i32,
    dy: i32,
    dz: i32,
) -> f32 {
    if hx >= 1.0 || hz >= 1.0 {
        return 1.0;
    }
    let mut total = 0.0;
    let mut samples = 0.0;
    if hx > 0.0 || hz > 0.0 {
        let diagonal = fluid_height(block, states, dx, dy, dz, 0);
        if diagonal >= 1.0 {
            return 1.0;
        }
        modify_fluid_height(&mut total, &mut samples, diagonal);
    }
    modify_fluid_height(&mut total, &mut samples, center);
    modify_fluid_height(&mut total, &mut samples, hx);
    modify_fluid_height(&mut total, &mut samples, hz);
    if samples == 0.0 { 0.0 } else { total / samples }
}

fn modify_fluid_height(total: &mut f32, samples: &mut f32, height: f32) {
    if height >= 0.8 {
        *total += height * 10.0;
        *samples += 10.0;
    } else if height >= 0.0 {
        *total += height;
        *samples += 1.0;
    }
}

fn fluid_side_occluded(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &HashMap<i32, NativeMeshingState>,
    dir: i32,
) -> bool {
    let neighbor_id = block.neighbor_state_ids[dir as usize];
    let Some(neighbor) = states.get(&neighbor_id) else {
        return false;
    };
    neighbor.fluid_type == state.fluid_type
        || ((neighbor.flags & STATE_FLAG_FULL_OCCLUSION) != 0 && dir != 1)
}

fn fluid_side_exposed(
    block: &NativeSectionBlockRecord,
    states: &HashMap<i32, NativeMeshingState>,
    dx: i32,
    dy: i32,
    dz: i32,
    _height: f32,
) -> bool {
    neighborhood_state_id(block, dx, dy, dz)
        .and_then(|id| states.get(&id))
        .map(|state| (state.flags & STATE_FLAG_CAN_OCCLUDE) == 0 || (state.flags & STATE_FLAG_FULL_OCCLUSION) == 0)
        .unwrap_or(true)
}

fn fluid_backward_up_face(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &HashMap<i32, NativeMeshingState>,
) -> bool {
    for dz in -1..=1 {
        for dx in -1..=1 {
            let Some(id) = neighborhood_state_id(block, dx, 1, dz) else {
                return true;
            };
            let Some(sample) = states.get(&id) else {
                return true;
            };
            if sample.fluid_type != state.fluid_type && (sample.flags & STATE_FLAG_SOLID_RENDER) == 0 {
                return true;
            }
        }
    }
    false
}

fn neighborhood_state_id(block: &NativeSectionBlockRecord, dx: i32, dy: i32, dz: i32) -> Option<i32> {
    if !(-1..=1).contains(&dx) || !(-1..=1).contains(&dy) || !(-1..=1).contains(&dz) {
        return None;
    }
    Some(block.neighborhood_state_ids[neighborhood_index(dx, dy, dz)])
}

fn neighborhood_index(dx: i32, dy: i32, dz: i32) -> usize {
    ((dy + 1) as usize * 9) + ((dz + 1) as usize * 3) + (dx + 1) as usize
}

fn light_word(block: &NativeSectionBlockRecord, delta: (i32, i32, i32)) -> i32 {
    if !(-1..=1).contains(&delta.0) || !(-1..=1).contains(&delta.1) || !(-1..=1).contains(&delta.2) {
        block.light_words[13]
    } else {
        block.light_words[neighborhood_index(delta.0, delta.1, delta.2)]
    }
}

fn corner_word(
    block: &NativeSectionBlockRecord,
    base: (i32, i32, i32),
    a: i32,
    b: i32,
    edge_occluded: bool,
    fallback: i32,
) -> i32 {
    if edge_occluded {
        fallback
    } else {
        light_word(block, add_dir(add_dir(base, dir_step(a)), dir_step(b)))
    }
}

fn add_dir(a: (i32, i32, i32), b: (i32, i32, i32)) -> (i32, i32, i32) {
    (a.0 + b.0, a.1 + b.1, a.2 + b.2)
}

fn dir_step(dir: i32) -> (i32, i32, i32) {
    match dir {
        0 => (0, -1, 0),
        1 => (0, 1, 0),
        2 => (0, 0, -1),
        3 => (0, 0, 1),
        4 => (-1, 0, 0),
        5 => (1, 0, 0),
        _ => (0, 0, 0),
    }
}

fn ao_neighbor_faces(dir: i32) -> [i32; 4] {
    match dir {
        0 => [4, 5, 2, 3],
        1 => [5, 4, 2, 3],
        2 => [1, 0, 5, 4],
        3 => [4, 5, 0, 1],
        4 => [1, 0, 2, 3],
        5 => [0, 1, 2, 3],
        _ => [5, 4, 2, 3],
    }
}

fn map_ao_corners(dir: i32, lm0: [i32; 4], ao0: [f32; 4]) -> ([i32; 4], [f32; 4]) {
    match dir {
        1 => ([lm0[2], lm0[3], lm0[0], lm0[1]], [ao0[2], ao0[3], ao0[0], ao0[1]]),
        2 | 4 => ([lm0[1], lm0[2], lm0[3], lm0[0]], [ao0[1], ao0[2], ao0[3], ao0[0]]),
        5 => ([lm0[3], lm0[0], lm0[1], lm0[2]], [ao0[3], ao0[0], ao0[1], ao0[2]]),
        _ => (lm0, ao0),
    }
}

fn corner_weights(dir: i32, x: f32, y: f32, z: f32) -> [f32; 4] {
    let (u, v) = match dir {
        0 => (z.clamp(0.0, 1.0), (1.0 - x).clamp(0.0, 1.0)),
        1 => (z.clamp(0.0, 1.0), x.clamp(0.0, 1.0)),
        2 => ((1.0 - x).clamp(0.0, 1.0), y.clamp(0.0, 1.0)),
        3 => (y.clamp(0.0, 1.0), (1.0 - x).clamp(0.0, 1.0)),
        4 => (z.clamp(0.0, 1.0), y.clamp(0.0, 1.0)),
        5 => (z.clamp(0.0, 1.0), (1.0 - y).clamp(0.0, 1.0)),
        _ => (0.5, 0.5),
    };
    [v * u, v * (1.0 - u), (1.0 - v) * (1.0 - u), (1.0 - v) * u]
}

fn face_depth(dir: i32, x: f32, y: f32, z: f32) -> f32 {
    match dir {
        0 => y.clamp(0.0, 1.0),
        1 => 1.0 - y.clamp(0.0, 1.0),
        2 => z.clamp(0.0, 1.0),
        3 => 1.0 - z.clamp(0.0, 1.0),
        4 => x.clamp(0.0, 1.0),
        5 => 1.0 - x.clamp(0.0, 1.0),
        _ => 0.0,
    }
}

fn blend_ao_face(face: AoFace, weights: [f32; 4]) -> (f32, i32) {
    let ao = face.ao[0] * weights[0]
        + face.ao[1] * weights[1]
        + face.ao[2] * weights[2]
        + face.ao[3] * weights[3];
    let sky = unpack_sky_light(face.lm[0]) as f32 * weights[0]
        + unpack_sky_light(face.lm[1]) as f32 * weights[1]
        + unpack_sky_light(face.lm[2]) as f32 * weights[2]
        + unpack_sky_light(face.lm[3]) as f32 * weights[3];
    let block = unpack_block_light(face.lm[0]) as f32 * weights[0]
        + unpack_block_light(face.lm[1]) as f32 * weights[1]
        + unpack_block_light(face.lm[2]) as f32 * weights[2]
        + unpack_block_light(face.lm[3]) as f32 * weights[3];
    (ao, (((sky as i32) & 0xff) << 16) | ((block as i32) & 0xff))
}

fn blend_inset_ao_face(
    block: &NativeSectionBlockRecord,
    light_face: i32,
    n1d: f32,
    n2d: f32,
    weights: [f32; 4],
) -> (f32, i32) {
    let n1 = ao_face_data(block, light_face, false);
    let n2 = ao_face_data(block, light_face, true);
    let ao = weighted_sum(n1.ao, weights) * n1d + weighted_sum(n2.ao, weights) * n2d;
    let sl = weighted_sum(n1.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n2d;
    let bl = weighted_sum(n1.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n2d;
    (ao, (((sl as i32) & 0xff) << 16) | ((bl as i32) & 0xff))
}

fn weighted_sum(values: [f32; 4], weights: [f32; 4]) -> f32 {
    values[0] * weights[0]
        + values[1] * weights[1]
        + values[2] * weights[2]
        + values[3] * weights[3]
}

fn java_float_equal(a: f32, b: f32) -> bool {
    (a - b).abs() < 1.0e-5
}

fn ambient_shade(dir: i32, shade: bool) -> f32 {
    if !shade {
        return 1.0;
    }
    match dir {
        0 => 0.5,
        1 => 1.0,
        2 | 3 => 0.8,
        4 | 5 => 0.6,
        _ => 1.0,
    }
}

fn get_lightmap(word: i32) -> i32 {
    pack_light(std::cmp::max(unpack_bl(word), unpack_lu(word)), unpack_sl(word))
}

fn get_emissive_lightmap(word: i32) -> i32 {
    if unpack_em(word) {
        LIGHT_FULL_BRIGHT
    } else {
        get_lightmap(word)
    }
}

fn pack_light(block: i32, sky: i32) -> i32 {
    ((sky & 0xF) << 20) | ((block & 0xF) << 4)
}

fn unpack_block_light(light: i32) -> i32 {
    light & 0xff
}

fn unpack_sky_light(light: i32) -> i32 {
    (light >> 16) & 0xff
}

fn unpack_bl(word: i32) -> i32 { word & 0xF }
fn unpack_sl(word: i32) -> i32 { (word >> 4) & 0xF }
fn unpack_lu(word: i32) -> i32 { (word >> 8) & 0xF }
fn unpack_ao(word: i32) -> f32 { (((word >> 12) & 0xFFFF) as f32) * (1.0 / 4096.0) }
fn unpack_em(word: i32) -> bool { ((word >> 28) & 1) != 0 }
fn unpack_op(word: i32) -> bool { ((word >> 29) & 1) != 0 }
fn unpack_fo(word: i32) -> bool { ((word >> 30) & 1) != 0 }

fn calculate_corner_brightness(
    mut a: i32,
    mut b: i32,
    mut c: i32,
    mut d: i32,
    aem: bool,
    bem: bool,
    cem: bool,
    dem: bool,
) -> i32 {
    if a == 0 || b == 0 || c == 0 || d == 0 {
        let min = min_non_zero(min_non_zero(a, b), min_non_zero(c, d));
        a = a.max(min);
        b = b.max(min);
        c = c.max(min);
        d = d.max(min);
    }
    if aem { a = LIGHT_FULL_BRIGHT; }
    if bem { b = LIGHT_FULL_BRIGHT; }
    if cem { c = LIGHT_FULL_BRIGHT; }
    if dem { d = LIGHT_FULL_BRIGHT; }
    ((a + b + c + d) >> 2) & 0x00ff_00ff
}

fn min_non_zero(a: i32, b: i32) -> i32 {
    if a == 0 { b } else if b == 0 { a } else { a.min(b) }
}

fn argb_to_abgr(color: i32) -> i32 {
    let color = color as u32;
    let alpha_green = color & 0xff00_ff00;
    let red = (color & 0x00ff_0000) >> 16;
    let blue = (color & 0x0000_00ff) << 16;
    (alpha_green | red | blue) as i32
}

fn fluid_face_record_to_quad(record: FluidFaceRecord) -> Result<NativeQuad, i32> {
    let mut vertices = match record.face_kind {
        // Top face, diagonal from north-east to south-west.
        0 => [
            fluid_vertex(record.origin_x + 1.0, record.origin_y + record.heights[3], record.origin_z, 0, record),
            fluid_vertex(record.origin_x, record.origin_y + record.heights[0], record.origin_z, 1, record),
            fluid_vertex(record.origin_x, record.origin_y + record.heights[1], record.origin_z + 1.0, 2, record),
            fluid_vertex(record.origin_x + 1.0, record.origin_y + record.heights[2], record.origin_z + 1.0, 3, record),
        ],
        // Top face, diagonal from north-west to south-east.
        1 => [
            fluid_vertex(record.origin_x, record.origin_y + record.heights[0], record.origin_z, 0, record),
            fluid_vertex(record.origin_x, record.origin_y + record.heights[1], record.origin_z + 1.0, 1, record),
            fluid_vertex(record.origin_x + 1.0, record.origin_y + record.heights[2], record.origin_z + 1.0, 2, record),
            fluid_vertex(record.origin_x + 1.0, record.origin_y + record.heights[3], record.origin_z, 3, record),
        ],
        // Bottom face.
        2 => [
            fluid_vertex(record.origin_x, record.origin_y + record.y_offset, record.origin_z + 1.0, 0, record),
            fluid_vertex(record.origin_x, record.origin_y + record.y_offset, record.origin_z, 1, record),
            fluid_vertex(record.origin_x + 1.0, record.origin_y + record.y_offset, record.origin_z, 2, record),
            fluid_vertex(record.origin_x + 1.0, record.origin_y + record.y_offset, record.origin_z + 1.0, 3, record),
        ],
        // Horizontal side face. side_coords = x1,z1,x2,z2 and heights = c1,c2,...
        3 => [
            fluid_vertex(
                record.origin_x + record.side_coords[2],
                record.origin_y + record.heights[1],
                record.origin_z + record.side_coords[3],
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[2],
                record.origin_y + record.y_offset,
                record.origin_z + record.side_coords[3],
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[0],
                record.origin_y + record.y_offset,
                record.origin_z + record.side_coords[1],
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[0],
                record.origin_y + record.heights[0],
                record.origin_z + record.side_coords[1],
                3,
                record,
            ),
        ],
        _ => return Err(ERR_INVALID_ARGUMENT),
    };

    if record.flip != 0 {
        vertices = [vertices[0], vertices[3], vertices[2], vertices[1]];
    }

    Ok(NativeQuad {
        vertices,
        block_emission: record.block_emission.clamp(0, 255) as u8,
        render_type: record.render_type.clamp(0, 255) as u8,
        ignore_mid_block: if record.ignore_mid_block != 0 { 1 } else { 0 },
        _padding: 0,
        block_id: record.block_id,
        local_x: record.local_x,
        local_y: record.local_y,
        local_z: record.local_z,
        material_bits: record.material_bits,
    })
}

fn fluid_vertex(x: f32, y: f32, z: f32, vertex: usize, record: FluidFaceRecord) -> QuadVertex {
    QuadVertex {
        x,
        y,
        z,
        color: record.colors[vertex],
        ao: record.aos[vertex],
        u: record.uvs[vertex * 2],
        v: record.uvs[vertex * 2 + 1],
        light: record.lights[vertex],
    }
}

fn section_builder_staging_addresses(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
) -> Result<(u64, u64, u64, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let pending = &mut builder.pending[facing];
    Ok((
        pending.quads.as_mut_ptr() as u64,
        pending.packed_normals.as_mut_ptr() as u64,
        pending.validity.as_mut_ptr() as u64,
        pending.quads.len() as i32,
    ))
}

fn section_builder_record_staging_addresses(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
) -> Result<(u64, u64, u64, u64, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let pending = &mut builder.pending[facing];
    Ok((
        pending.flat_quad_records.as_mut_ptr() as u64,
        pending.light_block_records.as_mut_ptr() as u64,
        pending.fluid_face_records.as_mut_ptr() as u64,
        pending.static_model_block_records.as_mut_ptr() as u64,
        pending.flat_quad_records.len() as i32,
    ))
}

unsafe fn section_builder_encode_scattered_unassigned(
    builder: &NativeSectionMeshBuilder,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if update_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if update_count == 0 {
        return OK;
    }
    if output_vertex_offsets.is_null() || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let input_address = builder.buffers[MODEL_QUAD_FACING_UNASSIGNED].quads.as_ptr() as u64;
    encode_scattered(
        input_address,
        output_vertex_offsets,
        update_count,
        output_address,
        output_capacity,
        format,
    )
}

unsafe fn updated_quads_apply(
    updated_quads: &NativeUpdatedQuads,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
    material_bits: i32,
) -> i32 {
    if output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if updated_quads.quads.is_empty() {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let mut update_builder = create_section_mesh_builder(updated_quads.quads.len());
    let mut output_vertex_offsets = Vec::with_capacity(updated_quads.quads.len());

    for &quad_handle in &updated_quads.quads {
        let mut write_to_index = -1;
        let status = translucent::native_full_quad_write_to_index(quad_handle, &mut write_to_index);
        if status != OK {
            return status;
        }
        if write_to_index < 0 {
            continue;
        }

        let quad_address =
            match section_builder_prepare_quad(&mut update_builder, MODEL_QUAD_FACING_UNASSIGNED) {
                Ok(value) => value,
                Err(status) => return status,
            };
        let status = translucent::native_full_quad_write_to_native_buffer(
            quad_handle,
            quad_address,
            material_bits,
        );
        if status != OK {
            return status;
        }
        update_builder.counts[MODEL_QUAD_FACING_UNASSIGNED] += 1;

        let Some(vertex_offset) = write_to_index.checked_mul(4) else {
            return ERR_INVALID_ARGUMENT;
        };
        output_vertex_offsets.push(vertex_offset);
    }

    section_builder_encode_scattered_unassigned(
        &update_builder,
        output_vertex_offsets.as_ptr(),
        output_vertex_offsets.len() as i32,
        output_address,
        output_capacity,
        format,
    )
}

fn encode_quad(quad: &NativeQuad, output: &mut [u8], format: NativeFormat) {
    let vertices = &quad.vertices;
    let tex_centroid_u = vertices.iter().map(|vertex| vertex.u).sum::<f32>() * 0.25;
    let tex_centroid_v = vertices.iter().map(|vertex| vertex.v).sum::<f32>() * 0.25;
    let mid_uv = encode_old_uv(tex_centroid_u, tex_centroid_v);
    let (normal, tangent, packed_normal, packed_tangent) = if format.normal_offset != 0 {
        let normal = compute_face_normal(vertices);
        let tangent = compute_tangent_for_quad(normal, vertices);
        let normal_oct = octahedron_encode(normal);
        let tangent_oct = tangent_encode(tangent);
        (
            normal,
            tangent,
            pack_norm_i8(normal_oct.0, normal_oct.1, tangent_oct.0, tangent_oct.1),
            pack_norm_i8(tangent.0, tangent.1, tangent.2, tangent.3),
        )
    } else {
        (
            (0.0, 1.0, 0.0),
            (0.0, 1.0, 0.0, 1.0),
            default_normal(),
            pack_norm_i8(0.0, 1.0, 0.0, 1.0),
        )
    };
    let _ = (normal, tangent);

    for (index, vertex) in vertices.iter().enumerate() {
        let ptr = &mut output[index * format.vertex_stride..(index + 1) * format.vertex_stride];
        let x = quantize_position(vertex.x);
        let y = quantize_position(vertex.y);
        let z = quantize_position(vertex.z);
        let u = encode_texture(tex_centroid_u, vertex.u);
        let v = encode_texture(tex_centroid_v, vertex.v);
        let light = encode_light(vertex.light);

        put_i32(ptr, 0, pack_position_hi(x, y, z));
        put_i32(ptr, 4, pack_position_lo(x, y, z));
        put_i32(
            ptr,
            8,
            encode_color(vertex.color, vertex.ao, format.separate_ao),
        );
        put_i32(ptr, 12, pack_texture(u, v));
        put_i32(
            ptr,
            16,
            pack_light_and_data(light, quad.material_bits, format.section_index),
        );

        if format.block_id_offset != 0 {
            put_i32(ptr, format.block_id_offset, pack_block_id(quad));
        }
        if format.mid_block_offset != 0 {
            let mid_block = if quad.ignore_mid_block != 0 {
                0
            } else {
                compute_mid_block(vertex, quad)
            };
            put_i32(ptr, format.mid_block_offset, mid_block);
            ptr[format.mid_block_offset + 3] = quad.block_emission;
        }
        if format.mid_uv_offset != 0 {
            put_i32(ptr, format.mid_uv_offset, mid_uv);
        }
        if format.normal_offset != 0 {
            put_i32(ptr, format.normal_offset, packed_normal);
        }
        if format.tangent_offset != 0 {
            put_i32(ptr, format.tangent_offset, packed_tangent);
        }
    }
}

fn put_i32(output: &mut [u8], offset: usize, value: i32) {
    output[offset..offset + 4].copy_from_slice(&value.to_ne_bytes());
}

fn pack_position_hi(x: i32, y: i32, z: i32) -> i32 {
    (((x >> 10) & 0x3ff) << 0) | (((y >> 10) & 0x3ff) << 10) | (((z >> 10) & 0x3ff) << 20)
}

fn pack_position_lo(x: i32, y: i32, z: i32) -> i32 {
    ((x & 0x3ff) << 0) | ((y & 0x3ff) << 10) | ((z & 0x3ff) << 20)
}

fn quantize_position(position: f32) -> i32 {
    ((normalize_position(position) * POSITION_MAX_VALUE) as i32) & 0x0f_ffff
}

fn normalize_position(value: f32) -> f32 {
    (MODEL_ORIGIN + value) / MODEL_RANGE
}

fn encode_texture(center: f32, value: f32) -> i32 {
    let bias = if value < center { 1 } else { -1 };
    let quantized = java_round(value * TEXTURE_MAX_VALUE) + bias;
    (quantized & 0x7fff) | (sign(bias) << 15)
}

fn encode_old_uv(u: f32, v: f32) -> i32 {
    ((java_round(u * TEXTURE_MAX_VALUE) & 0xffff) << 0)
        | ((java_round(v * TEXTURE_MAX_VALUE) & 0xffff) << 16)
}

fn java_round(value: f32) -> i32 {
    (value + 0.5).floor() as i32
}

fn sign(value: i32) -> i32 {
    ((value as u32) >> 31) as i32
}

fn pack_texture(u: i32, v: i32) -> i32 {
    ((u & 0xffff) << 0) | ((v & 0xffff) << 16)
}

fn encode_light(light: i32) -> i32 {
    let sky = clamp_i32(((light as u32 >> 16) & 0xff) as i32, 8, 248);
    let block = clamp_i32(((light as u32 >> 0) & 0xff) as i32, 8, 248);
    (block << 0) | (sky << 8)
}

fn pack_light_and_data(light: i32, material: i32, section: i32) -> i32 {
    ((light & 0xffff) << 0) | ((material & 0xff) << 16) | ((section & 0xff) << 24)
}

fn clamp_i32(value: i32, min: i32, max: i32) -> i32 {
    value.max(min).min(max)
}

fn normalized_float_to_byte(value: f32) -> i32 {
    ((value * 255.0) as i32) & 0xff
}

fn color_mul(color: i32, factor: i32) -> i32 {
    let color = color as u32 as u64;
    let factor = factor as u64;
    let hi = (color & 0x00ff00ff) * factor;
    let lo = (color & 0xff00ff00) * factor;
    let result = (((hi + 0x00ff00ff) >> 8) & 0x00ff00ff) | (((lo + 0xff00ff00) >> 8) & 0xff00ff00);
    result as u32 as i32
}

fn color_mul_rgb(color: i32, factor: f32) -> i32 {
    let alpha_mask = 0xff000000u32 as i32;
    let factor = normalized_float_to_byte(factor);
    (color_mul(color, factor) & !alpha_mask) | (color & alpha_mask)
}

fn with_alpha(color: i32, alpha: f32) -> i32 {
    let alpha = normalized_float_to_byte(alpha);
    (alpha << 24) | (color & !(0xff << 24))
}

fn encode_color(color: i32, ao: f32, separate_ao: bool) -> i32 {
    if separate_ao {
        with_alpha(color, ao)
    } else {
        color_mul_rgb(color, ao)
    }
}

fn compute_mid_block(vertex: &QuadVertex, quad: &NativeQuad) -> i32 {
    pack_mid_block(
        quad.local_x as f32 + 0.5 - vertex.x,
        quad.local_y as f32 + 0.5 - vertex.y,
        quad.local_z as f32 + 0.5 - vertex.z,
    )
}

fn pack_mid_block(x: f32, y: f32, z: f32) -> i32 {
    (((x * 64.0) as i32) & 0xff)
        | ((((y * 64.0) as i32) & 0xff) << 8)
        | ((((z * 64.0) as i32) & 0xff) << 16)
}

fn pack_block_id(quad: &NativeQuad) -> i32 {
    quad.block_id
        .wrapping_add(1)
        .wrapping_shl(1)
        | ((quad.render_type as i32) & 1)
}

fn compute_face_normal(vertices: &[QuadVertex; 4]) -> (f32, f32, f32) {
    let dx0 = vertices[2].x - vertices[0].x;
    let dy0 = vertices[2].y - vertices[0].y;
    let dz0 = vertices[2].z - vertices[0].z;
    let dx1 = vertices[3].x - vertices[1].x;
    let dy1 = vertices[3].y - vertices[1].y;
    let dz1 = vertices[3].z - vertices[1].z;
    normalize3(
        dy0 * dz1 - dz0 * dy1,
        dz0 * dx1 - dx0 * dz1,
        dx0 * dy1 - dy0 * dx1,
    )
}

fn norm_i8_pack_from_quad(quad: &NativeQuad) -> i32 {
    let normal = compute_face_normal(&quad.vertices);
    (((normal.0.clamp(-1.0, 1.0) * 127.0) as i32) & 0xff)
        | ((((normal.1.clamp(-1.0, 1.0) * 127.0) as i32) & 0xff) << 8)
        | ((((normal.2.clamp(-1.0, 1.0) * 127.0) as i32) & 0xff) << 16)
}

fn compute_tangent_for_quad(
    normal: (f32, f32, f32),
    vertices: &[QuadVertex; 4],
) -> (f32, f32, f32, f32) {
    match compute_tangent(normal, vertices[0], vertices[1], vertices[2]) {
        Some(value) => value,
        None => compute_tangent(normal, vertices[2], vertices[3], vertices[0])
            .unwrap_or((0.0, 1.0, 0.0, 1.0)),
    }
}

fn compute_tangent(
    normal: (f32, f32, f32),
    v0: QuadVertex,
    v1: QuadVertex,
    v2: QuadVertex,
) -> Option<(f32, f32, f32, f32)> {
    let edge1x = v1.x - v0.x;
    let edge1y = v1.y - v0.y;
    let edge1z = v1.z - v0.z;
    let edge2x = v2.x - v0.x;
    let edge2y = v2.y - v0.y;
    let edge2z = v2.z - v0.z;

    let delta_u1 = v1.u - v0.u;
    let delta_v1 = v1.v - v0.v;
    let delta_u2 = v2.u - v0.u;
    let delta_v2 = v2.v - v0.v;
    let fdenom = delta_u1 * delta_v2 - delta_u2 * delta_v1;
    let f = if fdenom == 0.0 { 1.0 } else { 1.0 / fdenom };

    let tangent = normalize3(
        f * (delta_v2 * edge1x - delta_v1 * edge2x),
        f * (delta_v2 * edge1y - delta_v1 * edge2y),
        f * (delta_v2 * edge1z - delta_v1 * edge2z),
    );
    if tangent.0 == 0.0 && tangent.1 == 0.0 && tangent.2 == 0.0 {
        return None;
    }

    let bitangent = normalize3(
        f * (-delta_u2 * edge1x + delta_u1 * edge2x),
        f * (-delta_u2 * edge1y + delta_u1 * edge2y),
        f * (-delta_u2 * edge1z + delta_u1 * edge2z),
    );

    let predicted_bitangent = (
        tangent.1 * normal.2 - tangent.2 * normal.1,
        tangent.2 * normal.0 - tangent.0 * normal.2,
        tangent.0 * normal.1 - tangent.1 * normal.0,
    );
    let dot = bitangent.0 * predicted_bitangent.0
        + bitangent.1 * predicted_bitangent.1
        + bitangent.2 * predicted_bitangent.2;
    let w = if dot < 0.0 { -1.0 } else { 1.0 };

    Some((tangent.0, tangent.1, tangent.2, w))
}

fn normalize3(x: f32, y: f32, z: f32) -> (f32, f32, f32) {
    let value = x * x + y * y + z * z;
    let coefficient = if value == 0.0 {
        1.0
    } else {
        1.0 / value.sqrt()
    };
    (x * coefficient, y * coefficient, z * coefficient)
}

fn octahedron_encode(vector: (f32, f32, f32)) -> (f32, f32) {
    let inv_l1 = 1.0 / (vector.0.abs() + vector.1.abs() + vector.2.abs());
    let nx = vector.0 * inv_l1;
    let ny = vector.1 * inv_l1;
    let nz = vector.2 * inv_l1;

    if nz >= 0.0 {
        (nx, ny)
    } else {
        (
            (1.0 - ny.abs()) * if nx >= 0.0 { 1.0 } else { -1.0 },
            (1.0 - nx.abs()) * if ny >= 0.0 { 1.0 } else { -1.0 },
        )
    }
}

fn tangent_encode(tangent: (f32, f32, f32, f32)) -> (f32, f32) {
    let mut encoded = octahedron_encode((tangent.0, tangent.1, tangent.2));
    let y_sign = if encoded.1 >= 0.0 {
        64.0 / 127.0
    } else {
        -64.0 / 127.0
    };
    encoded.1 *= 63.0 / 127.0;
    if tangent.3 < 0.0 {
        encoded.1 += y_sign;
    }
    encoded
}

fn default_normal() -> i32 {
    let normal = octahedron_encode((0.0, 1.0, 0.0));
    let tangent = tangent_encode((0.0, 1.0, 0.0, 1.0));
    pack_norm_i8(normal.0, normal.1, tangent.0, tangent.1)
}

fn pack_norm_i8(x: f32, y: f32, z: f32, w: f32) -> i32 {
    (((x * 127.0) as i32) & 0xff)
        | ((((y * 127.0) as i32) & 0xff) << 8)
        | ((((z * 127.0) as i32) & 0xff) << 16)
        | ((((w * 127.0) as i32) & 0xff) << 24)
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_chunk_mesh_verify() -> i32 {
    verify()
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_chunk_compact_format_value(value: i32) -> i32 {
    compact_format_value(value)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_write_metadata(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
) -> i32 {
    write_native_quad_metadata(
        quad_address,
        block_emission,
        render_type,
        ignore_mid_block,
        block_id,
        local_x,
        local_y,
        local_z,
        material_bits,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_write_vertex(
    quad_address: u64,
    vertex_index: i32,
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
) -> i32 {
    write_native_quad_vertex(quad_address, vertex_index, x, y, z, color, ao, u, v, light)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_write(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
    x0: f32,
    y0: f32,
    z0: f32,
    color0: i32,
    ao0: f32,
    u0: f32,
    v0: f32,
    light0: i32,
    x1: f32,
    y1: f32,
    z1: f32,
    color1: i32,
    ao1: f32,
    u1: f32,
    v1: f32,
    light1: i32,
    x2: f32,
    y2: f32,
    z2: f32,
    color2: i32,
    ao2: f32,
    u2: f32,
    v2: f32,
    light2: i32,
    x3: f32,
    y3: f32,
    z3: f32,
    color3: i32,
    ao3: f32,
    u3: f32,
    v3: f32,
    light3: i32,
) -> i32 {
    write_native_quad(
        quad_address,
        block_emission,
        render_type,
        ignore_mid_block,
        block_id,
        local_x,
        local_y,
        local_z,
        material_bits,
        x0,
        y0,
        z0,
        color0,
        ao0,
        u0,
        v0,
        light0,
        x1,
        y1,
        z1,
        color1,
        ao1,
        u1,
        v1,
        light1,
        x2,
        y2,
        z2,
        color2,
        ao2,
        u2,
        v2,
        light2,
        x3,
        y3,
        z3,
        color3,
        ao3,
        u3,
        v3,
        light3,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_position(
    quad_address: u64,
    vertex_index: i32,
    component: i32,
) -> f32 {
    native_quad_position(quad_address, vertex_index, component)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_mesh_encode(
    input_address: u64,
    vertex_count: i32,
    output_address: u64,
    output_capacity: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
) -> i32 {
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    encode(
        input_address,
        vertex_count,
        output_address,
        output_capacity,
        format,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_mesh_scattered_encode(
    input_address: u64,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
) -> i32 {
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    encode_scattered(
        input_address,
        output_vertex_offsets,
        update_count,
        output_address,
        output_capacity,
        format,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_mesh_output_assemble(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
    separate_ao: i32,
    index_output_address: u64,
    index_output_capacity: i32,
    index_mode: i32,
    index_stride: i32,
    index_values: *const i32,
    index_value_count: i32,
) -> i32 {
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    assemble_output(
        input_addresses,
        input_vertex_counts,
        input_count,
        output_address,
        output_capacity,
        vertex_segments,
        vertex_segments_len,
        format,
        visible_slices,
        force_unassigned,
        slice_reordering,
        index_output_address,
        index_output_capacity,
        index_mode,
        index_stride,
        index_values,
        index_value_count,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_create(
    initial_quad_capacity: i32,
    output_handle: *mut u64,
) -> i32 {
    if initial_quad_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(initial_quad_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let builder = create_section_mesh_builder(capacity);

    *output_handle = Box::into_raw(Box::new(builder)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeSectionMeshBuilder));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_start(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    builder.counts.fill(0);
    for buffer in &mut builder.buffers {
        buffer.encoded.clear();
        buffer.encoded_format = None;
    }
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_prepare_quad(
    handle: u64,
    facing: i32,
    output_address: *mut u64,
) -> i32 {
    if handle == 0 || output_address.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_prepare_quad(builder, facing as usize) {
        Ok(address) => {
            *output_address = address;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_commit_quad(
    handle: u64,
    facing: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || facing as usize >= MODEL_QUAD_FACING_COUNT {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    let facing = facing as usize;
    if builder.counts[facing] >= builder.buffers[facing].quads.len() {
        return ERR_INVALID_ARGUMENT;
    }
    builder.counts[facing] += 1;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_batch(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_batch(
        builder,
        facing as usize,
        batch_address,
        quad_count as usize,
        None,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_batch_filtered(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    validity_address: u64,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count > 0 && validity_address == 0 {
        return ERR_NULL_POINTER;
    }

    let validity = if quad_count == 0 {
        &[][..]
    } else {
        slice::from_raw_parts(validity_address as *const u8, quad_count as usize)
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_batch(
        builder,
        facing as usize,
        batch_address,
        quad_count as usize,
        Some(validity),
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_batch_encoded(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_batch_encoded(
        builder,
        facing as usize,
        batch_address,
        quad_count as usize,
        None,
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_flat_quad_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_flat_quad_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
        None,
        format,
        store_raw_quads != 0,
    ) {
        Ok((_, committed_count)) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_light_block_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_light_block_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_fluid_face_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_fluid_face_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
        None,
        format,
        store_raw_quads != 0,
    ) {
        Ok((_, committed_count)) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_static_model_cache_clear() -> i32 {
    let Ok(mut cache) = static_model_cache().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    cache.clear();
    drop(cache);
    let Ok(mut selectors) = native_model_selectors().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    selectors.clear();
    drop(selectors);
    let Ok(mut states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    states.clear();
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_static_model_cache_register(
    model_id: i32,
    quad_address: u64,
    quad_count: i32,
    quad_stride: i32,
) -> i32 {
    if model_id < 0 || quad_count < 0 || quad_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count > 0 && quad_address == 0 {
        return ERR_NULL_POINTER;
    }
    let quad_stride = match usize::try_from(quad_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if quad_stride != std::mem::size_of::<StaticModelQuadRecord>() {
        return ERR_INVALID_ARGUMENT;
    }

    let quads = if quad_count == 0 {
        Vec::new()
    } else {
        slice::from_raw_parts(quad_address as *const StaticModelQuadRecord, quad_count as usize)
            .to_vec()
    };

    let Ok(mut cache) = static_model_cache().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    cache.insert(model_id, quads);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_model_selector_register(
    selector_id: i32,
    kind: i32,
    entry_address: u64,
    entry_count: i32,
    entry_stride: i32,
) -> i32 {
    if selector_id < 0 || entry_count < 0 || entry_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if entry_count > 0 && entry_address == 0 {
        return ERR_NULL_POINTER;
    }
    let entry_stride = match usize::try_from(entry_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if entry_stride != std::mem::size_of::<NativeModelSelectorEntry>() {
        return ERR_INVALID_ARGUMENT;
    }

    let entries = if entry_count == 0 {
        Vec::new()
    } else {
        slice::from_raw_parts(entry_address as *const NativeModelSelectorEntry, entry_count as usize)
            .to_vec()
    };
    let total_weight = entries
        .iter()
        .filter(|entry| entry.weight > 0)
        .map(|entry| entry.weight)
        .sum();

    let Ok(mut selectors) = native_model_selectors().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    selectors.insert(selector_id, NativeModelSelector {
        kind,
        entries,
        total_weight,
    });
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_meshing_state_register(
    state_id: i32,
    selector_id: i32,
    flags: i32,
    material_bits: i32,
    pass_id: i32,
    block_emission: i32,
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
    fluid_still_u0: f32,
    fluid_still_u1: f32,
    fluid_still_v0: f32,
    fluid_still_v1: f32,
    fluid_still_shrink: f32,
    fluid_flow_u0: f32,
    fluid_flow_u1: f32,
    fluid_flow_v0: f32,
    fluid_flow_v1: f32,
    fluid_flow_shrink: f32,
    fluid_overlay_u0: f32,
    fluid_overlay_u1: f32,
    fluid_overlay_v0: f32,
    fluid_overlay_v1: f32,
    fluid_overlay_shrink: f32,
    fluid_overlay_valid: i32,
) -> i32 {
    if state_id < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let Ok(mut states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    states.insert(state_id, NativeMeshingState {
        selector_id,
        flags,
        material_bits,
        pass_id,
        block_emission,
        render_type,
        block_id,
        fluid_material_bits,
        fluid_pass_id,
        fluid_block_id,
        skip_group,
        fluid_type,
        fluid_own_height,
        fluid_falling,
        offset_type,
        max_horizontal_offset,
        max_vertical_offset,
        tint_type,
        fluid_still: FluidSprite {
            u0: fluid_still_u0,
            u1: fluid_still_u1,
            v0: fluid_still_v0,
            v1: fluid_still_v1,
            shrink: fluid_still_shrink,
        },
        fluid_flow: FluidSprite {
            u0: fluid_flow_u0,
            u1: fluid_flow_u1,
            v0: fluid_flow_v0,
            v1: fluid_flow_v1,
            shrink: fluid_flow_shrink,
        },
        fluid_overlay: FluidSprite {
            u0: fluid_overlay_u0,
            u1: fluid_overlay_u1,
            v0: fluid_overlay_v0,
            v1: fluid_overlay_v1,
            shrink: fluid_overlay_shrink,
        },
        fluid_overlay_valid,
    });
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_static_model_batch_encoded(
    handle: u64,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if record_count < 0 || record_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_static_model_records_encoded(
        builder,
        record_address,
        record_count as usize,
        record_stride,
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_native_section_encoded(
    handle: u64,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    pass_id: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    analyzer_handle: u64,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if record_count < 0 || record_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_native_section_records_encoded(
        builder,
        record_address,
        record_count as usize,
        record_stride,
        pass_id,
        if analyzer_handle == 0 { None } else { Some(analyzer_handle) },
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_batch_encoded(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    packed_normals_address: u64,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 || packed_normals_address == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    let facing = facing as usize;
    if facing >= MODEL_QUAD_FACING_COUNT || quad_count as usize > PENDING_BATCH_QUAD_CAPACITY {
        return ERR_INVALID_ARGUMENT;
    }

    let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
    let mut valid_count = 0i32;
    let status = translucent::append_native_quad_batch_to_analyzer(
        analyzer_handle,
        batch_address,
        quad_count,
        translucent_facing,
        packed_normals_address as *const i32,
        validity_address,
        &mut valid_count,
    );
    if status != OK {
        return status;
    }

    match section_builder_append_batch_encoded(
        builder,
        facing,
        batch_address,
        quad_count as usize,
        Some(slice::from_raw_parts(
            validity_address as *const u8,
            quad_count as usize,
        )),
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_flat_quad_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    record_stride: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if record_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_flat_quad_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
        Some((analyzer_handle, translucent_facing)),
        format,
        store_raw_quads != 0,
    ) {
        Ok((valid_count, committed_count)) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_fluid_face_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    record_stride: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if record_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_fluid_face_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
        Some((analyzer_handle, translucent_facing)),
        format,
        store_raw_quads != 0,
    ) {
        Ok((valid_count, committed_count)) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_batch(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    packed_normals_address: u64,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 || packed_normals_address == 0 {
        return ERR_NULL_POINTER;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    let facing = facing as usize;
    if facing >= MODEL_QUAD_FACING_COUNT || quad_count as usize > PENDING_BATCH_QUAD_CAPACITY {
        return ERR_INVALID_ARGUMENT;
    }

    let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
    let mut valid_count = 0i32;
    let status = translucent::append_native_quad_batch_to_analyzer(
        analyzer_handle,
        batch_address,
        quad_count,
        translucent_facing,
        packed_normals_address as *const i32,
        validity_address,
        &mut valid_count,
    );
    if status != OK {
        return status;
    }

    match section_builder_append_batch(
        builder,
        facing,
        batch_address,
        quad_count as usize,
        Some(slice::from_raw_parts(
            validity_address as *const u8,
            quad_count as usize,
        )),
    ) {
        Ok(committed_count) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_staging_addresses(
    handle: u64,
    facing: i32,
    output_quad_address: *mut u64,
    output_packed_normals_address: *mut u64,
    output_validity_address: *mut u64,
    output_capacity: *mut i32,
) -> i32 {
    if handle == 0
        || output_quad_address.is_null()
        || output_packed_normals_address.is_null()
        || output_validity_address.is_null()
        || output_capacity.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if facing < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_staging_addresses(builder, facing as usize) {
        Ok((quad_address, packed_normals_address, validity_address, capacity)) => {
            *output_quad_address = quad_address;
            *output_packed_normals_address = packed_normals_address;
            *output_validity_address = validity_address;
            *output_capacity = capacity;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_record_staging_addresses(
    handle: u64,
    facing: i32,
    output_flat_quad_record_address: *mut u64,
    output_light_block_record_address: *mut u64,
    output_fluid_face_record_address: *mut u64,
    output_static_model_block_record_address: *mut u64,
    output_capacity: *mut i32,
) -> i32 {
    if handle == 0
        || output_flat_quad_record_address.is_null()
        || output_light_block_record_address.is_null()
        || output_fluid_face_record_address.is_null()
        || output_static_model_block_record_address.is_null()
        || output_capacity.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if facing < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_record_staging_addresses(builder, facing as usize) {
        Ok((flat_quad_record_address, light_block_record_address, fluid_face_record_address,
            static_model_block_record_address, capacity)) => {
            *output_flat_quad_record_address = flat_quad_record_address;
            *output_light_block_record_address = light_block_record_address;
            *output_fluid_face_record_address = fluid_face_record_address;
            *output_static_model_block_record_address = static_model_block_record_address;
            *output_capacity = capacity;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_facing_address(
    handle: u64,
    facing: i32,
    output_address: *mut u64,
) -> i32 {
    if handle == 0 || output_address.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || facing as usize >= MODEL_QUAD_FACING_COUNT {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    let facing = facing as usize;
    *output_address = if builder.counts[facing] == 0 {
        0
    } else {
        builder.buffers[facing].quads.as_ptr() as u64
    };
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_facing_vertex_count(
    handle: u64,
    facing: i32,
    output_count: *mut i32,
) -> i32 {
    if handle == 0 || output_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || facing as usize >= MODEL_QUAD_FACING_COUNT {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    *output_count = (builder.counts[facing as usize] * 4) as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_total_vertex_count(
    handle: u64,
    output_count: *mut i32,
) -> i32 {
    if handle == 0 || output_count.is_null() {
        return ERR_NULL_POINTER;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    let Some(total_count) = builder
        .counts
        .iter()
        .try_fold(0usize, |acc, count| acc.checked_add(count * 4))
        .and_then(|value| i32::try_from(value).ok())
    else {
        return ERR_CAPACITY;
    };

    *output_count = total_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_assemble(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
    separate_ao: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    assemble_section_builder(
        builder,
        output_address,
        output_capacity,
        vertex_segments,
        vertex_segments_len,
        format,
        visible_slices,
        force_unassigned,
        slice_reordering,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_encode_scattered_unassigned(
    handle: u64,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    section_builder_encode_scattered_unassigned(
        &*(handle as *const NativeSectionMeshBuilder),
        output_vertex_offsets,
        update_count,
        output_address,
        output_capacity,
        format,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeUpdatedQuads {
        quads: Vec::new(),
        mesh_quad_count: 0,
        index_quad_count: 0,
    })) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeUpdatedQuads));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_add(handle: u64, quad_handle: u64) -> i32 {
    if handle == 0 || quad_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let updated_quads = &mut *(handle as *mut NativeUpdatedQuads);
    updated_quads.quads.push(quad_handle);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_set_counts(
    handle: u64,
    mesh_quad_count: i32,
    index_quad_count: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    if mesh_quad_count < 0 || index_quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let updated_quads = &mut *(handle as *mut NativeUpdatedQuads);
    updated_quads.mesh_quad_count = mesh_quad_count;
    updated_quads.index_quad_count = index_quad_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_counts(
    handle: u64,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }

    let updated_quads = &*(handle as *const NativeUpdatedQuads);
    *output_counts = updated_quads.mesh_quad_count;
    *output_counts.add(1) = updated_quads.index_quad_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_apply(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    material_bits: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let updated_quads = &*(handle as *const NativeUpdatedQuads);
    updated_quads_apply(
        updated_quads,
        output_address,
        output_capacity,
        format,
        material_bits,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_create(
    capacity: i32,
    output_handle: *mut u64,
    output_address: *mut u64,
) -> i32 {
    if capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || output_address.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let mut buffer = Box::new(NativeQuadBuffer {
        quads: vec![NativeQuad::default(); capacity],
        encoded: Vec::new(),
        encoded_format: None,
    });

    *output_address = if buffer.quads.is_empty() {
        0
    } else {
        buffer.quads.as_mut_ptr() as u64
    };
    *output_handle = Box::into_raw(buffer) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_ensure_capacity(
    handle: u64,
    capacity: i32,
    output_address: *mut u64,
) -> i32 {
    if capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let buffer = &mut *(handle as *mut NativeQuadBuffer);
    if buffer.quads.len() < capacity {
        buffer.quads.resize(capacity, NativeQuad::default());
    }

    *output_address = if buffer.quads.is_empty() {
        0
    } else {
        buffer.quads.as_mut_ptr() as u64
    };
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeQuadBuffer));
    OK
}

#[cfg(test)]
mod tests {
    use super::*;

    fn vertex(x: f32, y: f32, z: f32, u: f32, v: f32) -> QuadVertex {
        QuadVertex {
            x,
            y,
            z,
            color: 0xff804020u32 as i32,
            ao: 0.5,
            u,
            v,
            light: 0x00f000f0,
        }
    }

    fn quad() -> NativeQuad {
        NativeQuad {
            vertices: [
                vertex(0.0, 0.0, 0.0, 0.0, 0.0),
                vertex(1.0, 0.0, 0.0, 1.0, 0.0),
                vertex(1.0, 1.0, 0.0, 1.0, 1.0),
                vertex(0.0, 1.0, 0.0, 0.0, 1.0),
            ],
            block_emission: 7,
            render_type: 1,
            ignore_mid_block: 0,
            _padding: 0,
            block_id: 41,
            local_x: 1,
            local_y: 2,
            local_z: 3,
            material_bits: 5,
        }
    }

    #[test]
    fn native_quad_layout_matches_java_stride() {
        assert_eq!(32, std::mem::size_of::<QuadVertex>());
        assert_eq!(152, std::mem::size_of::<NativeQuad>());
        assert_eq!(156, std::mem::size_of::<FlatQuadRecord>());
        assert_eq!(24, std::mem::size_of::<LightBlockRecord>());
        assert_eq!(172, std::mem::size_of::<FluidFaceRecord>());
    }

    #[test]
    fn argb_to_abgr_preserves_alpha_and_swaps_red_blue() {
        assert_eq!(0xff0000ffu32 as i32, argb_to_abgr(0xffff0000u32 as i32));
        assert_eq!(0xff00ff00u32 as i32, argb_to_abgr(0xff00ff00u32 as i32));
        assert_eq!(0xffff0000u32 as i32, argb_to_abgr(0xff0000ffu32 as i32));
        assert_eq!(0xffffffffu32 as i32, argb_to_abgr(0xffffffffu32 as i32));
        assert_eq!(0x80332211u32 as i32, argb_to_abgr(0x80112233u32 as i32));
        assert_eq!(0xff6f9935u32 as i32, argb_to_abgr(0xff35996fu32 as i32));
        assert_eq!(0xffe4763fu32 as i32, argb_to_abgr(0xff3f76e4u32 as i32));
    }

    #[test]
    fn compact_color_encoding_keeps_material_alpha_when_ao_is_not_separate() {
        assert_eq!(
            0x80112233u32 as i32,
            encode_color(0x80112233u32 as i32, 1.0, false)
        );
        assert_eq!(
            0x7f112233u32 as i32,
            encode_color(0x80112233u32 as i32, 0.5, true)
        );
    }

    #[test]
    fn fluid_face_record_expands_semantic_side_face_to_quad() {
        let mut record = FluidFaceRecord {
            packed_normal: 0,
            material_bits: 5,
            block_emission: 7,
            render_type: 1,
            ignore_mid_block: 0,
            block_id: 41,
            local_x: 4,
            local_y: 5,
            local_z: 6,
            face_kind: 3,
            flip: 0,
            origin_x: 10.0,
            origin_y: 20.0,
            origin_z: 30.0,
            y_offset: 0.001,
            heights: [0.75, 0.5, 0.0, 0.0],
            side_coords: [0.0, 1.0, 1.0, 1.0],
            uvs: [0.0, 0.2, 0.5, 0.6, 1.0, 0.6, 1.0, 0.1],
            colors: [1, 2, 3, 4],
            aos: [0.1, 0.2, 0.3, 0.4],
            lights: [11, 12, 13, 14],
        };

        let quad = fluid_face_record_to_quad(record).unwrap();
        assert_eq!(11.0, quad.vertices[0].x);
        assert_eq!(20.5, quad.vertices[0].y);
        assert_eq!(31.0, quad.vertices[0].z);
        assert_eq!(10.0, quad.vertices[3].x);
        assert_eq!(20.75, quad.vertices[3].y);
        assert_eq!(31.0, quad.vertices[3].z);
        assert_eq!(5, quad.material_bits);
        assert_eq!(41, quad.block_id);

        record.flip = 1;
        let flipped = fluid_face_record_to_quad(record).unwrap();
        assert_eq!(quad.vertices[0].x, flipped.vertices[0].x);
        assert_eq!(quad.vertices[3].x, flipped.vertices[1].x);
        assert_eq!(quad.vertices[2].x, flipped.vertices[2].x);
        assert_eq!(quad.vertices[1].x, flipped.vertices[3].x);
    }

    #[test]
    fn compact_format_metadata_is_rust_owned() {
        assert_eq!(
            20,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_STRIDE)
        );
        assert_eq!(
            0,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_POSITION_OFFSET)
        );
        assert_eq!(
            8,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_COLOR_OFFSET)
        );
        assert_eq!(
            12,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_TEXTURE_OFFSET)
        );
        assert_eq!(
            16,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET)
        );
        assert_eq!(
            0,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_BLOCK_ID_OFFSET)
        );
        assert_eq!(
            1 << 20,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_POSITION_MAX_VALUE)
        );
        assert_eq!(
            1 << 15,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_TEXTURE_MAX_VALUE)
        );
    }

    #[test]
    fn native_quad_write_helpers_populate_rust_owned_layout() {
        let mut quad = NativeQuad::default();
        let address = &mut quad as *mut NativeQuad as u64;

        unsafe {
            assert_eq!(
                OK,
                mattmc_sodium_chunk_native_quad_write_metadata(address, 13, 2, 1, 99, 4, 5, 6, 7)
            );
            assert_eq!(
                OK,
                mattmc_sodium_chunk_native_quad_write_vertex(
                    address, 2, 1.25, 2.5, 3.75, 0x11223344, 0.875, 0.125, 0.625, 0x00f000f0,
                )
            );
        }

        assert_eq!(13, quad.block_emission);
        assert_eq!(2, quad.render_type);
        assert_eq!(1, quad.ignore_mid_block);
        assert_eq!(0, quad._padding);
        assert_eq!(99, quad.block_id);
        assert_eq!(4, quad.local_x);
        assert_eq!(5, quad.local_y);
        assert_eq!(6, quad.local_z);
        assert_eq!(7, quad.material_bits);
        assert_eq!(1.25, quad.vertices[2].x);
        assert_eq!(2.5, unsafe {
            mattmc_sodium_chunk_native_quad_position(address, 2, 1)
        });
        assert_eq!(3.75, unsafe {
            mattmc_sodium_chunk_native_quad_position(address, 2, 2)
        });
        assert_eq!(0x11223344, quad.vertices[2].color);
        assert_eq!(0.875, quad.vertices[2].ao);
        assert_eq!(0.125, quad.vertices[2].u);
        assert_eq!(0.625, quad.vertices[2].v);
        assert_eq!(0x00f000f0, quad.vertices[2].light);
    }

    #[test]
    fn compact_encoder_writes_expected_base_words() {
        let input = [quad()];
        let mut output = vec![0u8; 4 * 20];
        let format = NativeFormat {
            vertex_stride: 20,
            block_id_offset: 0,
            normal_offset: 0,
            tangent_offset: 0,
            mid_uv_offset: 0,
            mid_block_offset: 0,
            section_index: 3,
            separate_ao: false,
        };

        unsafe {
            assert_eq!(
                OK,
                encode(
                    input.as_ptr() as u64,
                    4,
                    output.as_mut_ptr() as u64,
                    output.len() as i32,
                    format,
                )
            );
        }

        assert_ne!([0u8; 20], output[0..20]);
        assert_eq!(
            pack_light_and_data(0xf0f0, 5, 3).to_ne_bytes(),
            output[16..20]
        );
    }

    #[test]
    fn block_id_pack_matches_java_wrapping_int_arithmetic() {
        let mut input = quad();
        input.block_id = i32::MAX;
        input.render_type = 1;

        assert_eq!(1, pack_block_id(&input));

        input.block_id = -1;
        input.render_type = 0;
        assert_eq!(0, pack_block_id(&input));
    }

    #[test]
    fn scattered_encoder_writes_requested_quad_slots_only() {
        let mut first = quad();
        first.material_bits = 5;
        let mut second = quad();
        second.material_bits = 9;
        second.vertices[0].x = 4.0;
        let input = [first, second];
        let offsets = [8, 0];
        let mut output = vec![0u8; 4 * 3 * 20];
        let format = NativeFormat {
            vertex_stride: 20,
            block_id_offset: 0,
            normal_offset: 0,
            tangent_offset: 0,
            mid_uv_offset: 0,
            mid_block_offset: 0,
            section_index: 7,
            separate_ao: false,
        };

        unsafe {
            assert_eq!(
                OK,
                encode_scattered(
                    input.as_ptr() as u64,
                    offsets.as_ptr(),
                    input.len() as i32,
                    output.as_mut_ptr() as u64,
                    output.len() as i32,
                    format,
                )
            );
        }

        assert_eq!(
            pack_light_and_data(0xf0f0, 9, 7).to_ne_bytes(),
            output[16..20]
        );
        assert_eq!([0u8; 20], output[4 * 20..5 * 20]);
        assert_eq!(
            pack_light_and_data(0xf0f0, 5, 7).to_ne_bytes(),
            output[(8 * 20 + 16)..(8 * 20 + 20)]
        );
    }

    #[test]
    fn smooth_lighting_uses_java_aligned_partial_offset_face() {
        let block = lighting_block_record();
        let quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED | MODEL_QUAD_FLAG_PARTIAL, 1, 0.25, 0.5, 0.75);
        let light = smooth_lighting(&block, &quad, lighting_state_record(0), 1, true);
        let weights = corner_weights(1, 0.25, 0.5, 0.75);
        let expected = blend_ao_face(ao_face_data(&block, 1, true), weights);
        let wrong_direct = blend_ao_face(ao_face_data(&block, 1, false), weights);

        assert_close(expected.0 * ambient_shade(1, true), light.ao[0]);
        assert_eq!(expected.1, light.lm[0]);
        assert_ne!(wrong_direct.1, light.lm[0]);
    }

    #[test]
    fn smooth_lighting_uses_java_parallel_inset_depth_weights() {
        let block = lighting_block_record();
        let quad = lighting_quad(MODEL_QUAD_FLAG_PARALLEL | MODEL_QUAD_FLAG_PARTIAL, 1, 0.4, 0.25, 0.6);
        let light = smooth_lighting(&block, &quad, lighting_state_record(0), 1, true);
        let weights = corner_weights(1, 0.4, 0.25, 0.6);
        let depth = face_depth(1, 0.4, 0.25, 0.6);
        let expected = blend_inset_ao_face(&block, 1, depth, 1.0 - depth, weights);

        assert_close(expected.0 * ambient_shade(1, true), light.ao[0]);
        assert_eq!(expected.1, light.lm[0]);
    }

    #[test]
    fn smooth_lighting_snaps_non_parallel_endpoints_like_java() {
        let block = lighting_block_record();
        let top_vertex = lighting_quad(0, 1, 0.5, 1.0, 0.5);
        let bottom_vertex = lighting_quad(0, 1, 0.5, 0.0, 0.5);
        let top_light = smooth_lighting(&block, &top_vertex, lighting_state_record(0), 1, true);
        let bottom_light = smooth_lighting(&block, &bottom_vertex, lighting_state_record(0), 1, true);
        let weights_top = corner_weights(1, 0.5, 1.0, 0.5);
        let weights_bottom = corner_weights(1, 0.5, 0.0, 0.5);
        let expected_top = blend_ao_face(ao_face_data(&block, 1, true), weights_top);
        let expected_bottom = blend_ao_face(ao_face_data(&block, 1, false), weights_bottom);

        assert_eq!(expected_top.1, top_light.lm[0]);
        assert_eq!(expected_bottom.1, bottom_light.lm[0]);
        assert_ne!(top_light.lm[0], bottom_light.lm[0]);
    }

    #[test]
    fn smooth_lighting_treats_parallel_full_cube_as_java_aligned_full_face() {
        let block = lighting_block_record();
        let quad = lighting_quad(MODEL_QUAD_FLAG_PARALLEL, 1, 0.4, 0.25, 0.6);
        let light = smooth_lighting(&block, &quad, lighting_state_record(STATE_FLAG_FULL_OCCLUSION), 1, true);
        let face = ao_face_data(&block, 1, true);
        let (expected_lm, mut expected_ao) = map_ao_corners(1, face.lm, face.ao);
        for value in &mut expected_ao {
            *value *= ambient_shade(1, true);
        }

        assert_eq!(expected_lm, light.lm);
        for i in 0..4 {
            assert_close(expected_ao[i], light.ao[i]);
        }
    }

    #[test]
    fn static_model_native_quads_use_block_iris_render_type() {
        let mut block = lighting_block_record();
        block.local_x = 1;
        block.local_y = 2;
        block.local_z = 3;
        block.absolute_x = 145;
        block.absolute_y = 66;
        block.absolute_z = 531;
        let mut state = lighting_state_record(0);
        state.render_type = 2;
        let quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);

        let native = static_model_quad_to_native_section(block, state, quad);

        assert_eq!(0, native.render_type);
        assert_eq!(145, native.local_x);
        assert_eq!(66, native.local_y);
        assert_eq!(531, native.local_z);
    }

    #[test]
    fn static_model_zero_source_light_uses_computed_lighting() {
        let block = lighting_block_record();
        let mut quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
        for vertex in &mut quad.vertices {
            vertex.light = 0;
        }
        let expected = native_quad_lighting(&block, &quad, lighting_state_record(STATE_FLAG_FULL_OCCLUSION));

        let native = static_model_quad_to_native_section(
            block,
            lighting_state_record(STATE_FLAG_FULL_OCCLUSION),
            quad,
        );

        assert_eq!(expected.lm[0], native.vertices[0].light);
        assert_ne!(0, native.vertices[0].light);
    }

    #[test]
    fn static_model_force_grass_tint_applies_without_quad_tint_index() {
        let mut block = lighting_block_record();
        block.tint = 0xff35_996fu32 as i32;
        let mut state = lighting_state_record(0);
        state.tint_type = TINT_FORCE_GRASS;
        let mut quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
        quad.tint_index = -1;
        quad.vertices[0].color = 0xffff_ffffu32 as i32;

        let native = static_model_quad_to_native_section(block, state, quad);

        assert_eq!(0xff6f_9935u32 as i32, native.vertices[0].color);
    }

    #[test]
    fn native_fluid_uses_fluid_shader_block_id_not_container_block_id() {
        let mut block = lighting_block_record();
        block.block_id = 1234;
        block.fluid_block_id = 5678;
        block.fluid_tint = 0xff3f_76e4u32 as i32;
        let mut state = lighting_state_record(0);
        state.fluid_type = FLUID_WATER;
        state.fluid_block_id = 9012;
        state.fluid_material_bits = 9;
        state.fluid_still = FluidSprite {
            u0: 0.0,
            u1: 1.0,
            v0: 0.0,
            v1: 1.0,
            shrink: 0.0,
        };

        let (record, _) = fluid_semantic_face(
            state,
            &block,
            MODEL_QUAD_FACING_POS_Y,
            false,
            FLUID_FACE_TOP_NW_SE,
            0.0,
            [1.0; 4],
            [0.0; 4],
            [(0.0, 0.0), (0.0, 1.0), (1.0, 1.0), (1.0, 0.0)],
            argb_to_abgr(block.fluid_tint),
            1.0,
            LIGHT_FULL_BRIGHT,
        );
        let quad = fluid_face_record_to_quad(record).unwrap();

        assert_eq!(5678, quad.block_id);
        assert_eq!(1, quad.render_type);
    }

    fn lighting_quad(flags: i32, light_face: i32, x: f32, y: f32, z: f32) -> StaticModelQuadRecord {
        StaticModelQuadRecord {
            vertices: [StaticModelVertexRecord {
                x,
                y,
                z,
                color: -1,
                u: 0.0,
                v: 0.0,
                light: -1,
            }; 4],
            material_bits: 5,
            cull_face: -1,
            normal_face: MODEL_QUAD_FACING_UNASSIGNED as i32,
            packed_normal: 0,
            block_emission: 0,
            render_type: 0,
            shade: 1,
            flags,
            light_face,
            tint_index: -1,
            has_ao: 1,
            _padding: 0,
        }
    }

    fn lighting_block_record() -> NativeSectionBlockRecord {
        let mut record = NativeSectionBlockRecord::default();
        record.fluid_block_id = -1;
        for dz in -1..=1 {
            for dy in -1..=1 {
                for dx in -1..=1 {
                    let index = neighborhood_index(dx, dy, dz);
                    let ao = 2048 + (index as i32 * 53);
                    let block = ((index as i32 * 3) + 1) & 0xf;
                    let sky = ((index as i32 * 5) + 2) & 0xf;
                    let luminance = ((index as i32 * 7) + 3) & 0xf;
                    record.light_words[index] = pack_light_word(block, sky, luminance, ao, false, false, false, false);
                }
            }
        }
        record
    }

    fn lighting_state_record(flags: i32) -> NativeMeshingState {
        let mut state = NativeMeshingState::default();
        state.flags = flags;
        state
    }

    fn pack_light_word(block: i32, sky: i32, luminance: i32, ao: i32, em: bool, op: bool, fo: bool, fc: bool) -> i32 {
        (block & 0xf)
            | ((sky & 0xf) << 4)
            | ((luminance & 0xf) << 8)
            | ((ao & 0xffff) << 12)
            | ((em as i32) << 28)
            | ((op as i32) << 29)
            | ((fo as i32) << 30)
            | ((fc as i32) << 31)
    }

    fn assert_close(expected: f32, actual: f32) {
        assert!((expected - actual).abs() < 0.00001, "expected {expected}, got {actual}");
    }

    #[test]
    fn native_quad_buffer_create_and_grow_returns_writable_memory() {
        unsafe {
            let mut handle = 0u64;
            let mut address = 0u64;
            assert_eq!(
                OK,
                mattmc_sodium_chunk_quad_buffer_create(1, &mut handle, &mut address)
            );
            assert_ne!(0, handle);
            assert_ne!(0, address);

            *(address as *mut NativeQuad) = quad();

            assert_eq!(
                OK,
                mattmc_sodium_chunk_quad_buffer_ensure_capacity(handle, 4, &mut address)
            );
            assert_ne!(0, address);
            *(address as *mut NativeQuad).add(3) = quad();

            assert_eq!(OK, mattmc_sodium_chunk_quad_buffer_destroy(handle));
        }
    }
}
