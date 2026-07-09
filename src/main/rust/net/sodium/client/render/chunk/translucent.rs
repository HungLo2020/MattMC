use std::slice;

use super::index;

const OK: i32 = 0;
const SORT_FAILED: i32 = 1;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

const FACING_COUNT: usize = 7;
const FACING_DIRECTIONS: usize = 6;
const FACING_POS_X: i32 = 0;
const FACING_POS_Y: i32 = 1;
const FACING_POS_Z: i32 = 2;
const FACING_NEG_X: i32 = 3;
const FACING_NEG_Y: i32 = 4;
const FACING_NEG_Z: i32 = 5;
const FACING_UNASSIGNED: i32 = 6;

const SORT_MODE_NONE: i32 = 0;
const SORT_MODE_STATIC: i32 = 1;

const SORT_TYPE_NONE: i32 = 2;
const SORT_TYPE_STATIC_NORMAL_RELATIVE: i32 = 3;
const SORT_TYPE_STATIC_TOPO: i32 = 4;
const SORT_TYPE_DYNAMIC: i32 = 5;

const VERTEX_EPSILON: f32 = 0.00001;
const QUANTIZE_EPSILON: f32 = 1.0 / 256.0;
const HALF_SPACE_EPSILON: f32 = 0.001;
const NORMAL_COMPONENT_RANGE: f32 = 127.0;
const NATIVE_QUAD_STRIDE: u64 = 152;

const ALIGNED_NORMALS: [(i8, i8, i8); FACING_DIRECTIONS] = [
    (127, 0, 0),
    (0, 127, 0),
    (0, 0, 127),
    (-127, 0, 0),
    (0, -127, 0),
    (0, 0, -127),
];
const STATIC_TOPO_SORT_ATTEMPT_LIMITS: [i32; 6] = [-1, -1, 250, 100, 50, 30];

#[repr(C)]
#[derive(Clone, Copy)]
pub struct TranslucentQuadRecord {
    positions: [f32; 12],
    facing: i32,
    packed_normal: i32,
}

#[derive(Clone)]
struct QuadInfo {
    positions: [f32; 12],
    center: (f32, f32, f32),
    facing: i32,
    topo_facing: i32,
    packed_normal: i32,
    extents: [f32; 6],
    accurate_dot_product: f32,
    quantized_dot_product: f32,
}

#[derive(Clone)]
struct Analyzer {
    mesh_facing_counts: [i32; FACING_COUNT],
    sort_type: i32,
    quad_hash: i32,
    aligned_facing_bitmap: i32,
    is_double_unaligned: bool,
    static_keys: Vec<i32>,
}

struct NativeTranslucentSectionGeometry {
    quads: Vec<QuadInfo>,
    aligned_separator_distances: [Vec<f32>; FACING_DIRECTIONS],
}

enum NativeTranslucentSortDataKind {
    StaticIndexData(Vec<i32>),
    DynamicTopo(NativeTranslucentSectionGeometry),
}

struct NativeTranslucentSortData {
    quad_count: usize,
    kind: NativeTranslucentSortDataKind,
}

struct NativeTranslucentAnalyzer {
    records: Vec<TranslucentQuadRecord>,
}

#[derive(Clone)]
enum BspRemapKind {
    None,
    FixedOffset(i32),
    IndexMap(Vec<i32>),
}

#[derive(Clone)]
struct BspRemap {
    index_count: usize,
    kind: BspRemapKind,
}

impl BspRemap {
    fn none() -> Self {
        Self {
            index_count: 0,
            kind: BspRemapKind::None,
        }
    }

    fn is_active(&self) -> bool {
        !matches!(self.kind, BspRemapKind::None)
    }
}

enum BspNode {
    LeafSingle {
        quad: i32,
    },
    LeafDouble {
        quad_a: i32,
        quad_b: i32,
    },
    LeafMulti {
        quads: Vec<i32>,
    },
    FixedDouble {
        remap: BspRemap,
        first: i32,
        second: i32,
    },
    Binary {
        remap: BspRemap,
        normal: [f32; 3],
        distance: f32,
        inside: i32,
        outside: i32,
        on_plane: Vec<i32>,
    },
    MultiPartition {
        remap: BspRemap,
        normal: [f32; 3],
        plane_distances: Vec<f32>,
        partitions: Vec<i32>,
        on_plane_quads: Vec<Vec<i32>>,
    },
}

struct NativeBspTree {
    nodes: Vec<BspNode>,
    root: i32,
    index_quad_count: usize,
}

struct BspActiveRemap {
    node_index: usize,
    remaining: usize,
}

struct BspTraversalState {
    quad_indexes: Vec<i32>,
    active_remap: Option<BspActiveRemap>,
}

pub fn verify() -> i32 {
    if std::mem::size_of::<TranslucentQuadRecord>() == 56 {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
}

fn analyze(records: &[TranslucentQuadRecord], sort_mode: i32) -> Result<Analyzer, i32> {
    if sort_mode < SORT_MODE_NONE || sort_mode > 2 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let mut quads_by_facing: [Vec<QuadInfo>; FACING_COUNT] = std::array::from_fn(|_| Vec::new());
    let mut has_unaligned = false;
    let mut untracked_unaligned_normal_count = 0;
    let mut aligned_facing_bitmap = 0i32;
    let mut extents = [
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
        f32::INFINITY,
        f32::INFINITY,
        f32::INFINITY,
    ];
    let mut aligned_extents_multiple = false;
    let mut aligned_extremes = [
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
        f32::INFINITY,
        f32::INFINITY,
        f32::INFINITY,
    ];
    let mut unaligned_a_normal = -1;
    let mut unaligned_a_distance1 = f32::NAN;
    let mut unaligned_a_distance2 = f32::NAN;
    let mut unaligned_b_normal = -1;
    let mut unaligned_b_distance1 = f32::NAN;
    let mut unaligned_b_distance2 = f32::NAN;

    for record in records {
        if record.facing < 0 || record.facing >= FACING_COUNT as i32 {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let quad = build_quad_info(record);
        let facing_index = quad.facing as usize;

        if is_aligned(quad.facing) {
            if !has_unaligned {
                for axis in 0..3 {
                    extents[axis] = extents[axis].max(quad.extents[axis]);
                }
                for axis in 3..6 {
                    extents[axis] = extents[axis].min(quad.extents[axis]);
                }
            }

            let distance = quad.accurate_dot_product;
            let existing_extreme = aligned_extremes[facing_index];
            if !aligned_extents_multiple
                && !existing_extreme.is_infinite()
                && existing_extreme != distance
            {
                aligned_extents_multiple = true;
            }

            if facing_sign(quad.facing) > 0 {
                aligned_extremes[facing_index] = aligned_extremes[facing_index].max(distance);
            } else {
                aligned_extremes[facing_index] = aligned_extremes[facing_index].min(distance);
            }
        } else {
            has_unaligned = true;
            let distance = quad.accurate_dot_product;

            if quad.packed_normal == unaligned_a_normal {
                if unaligned_a_distance1.is_nan() {
                    unaligned_a_distance1 = distance;
                } else {
                    unaligned_a_distance2 = distance;
                }
            } else if quad.packed_normal == unaligned_b_normal {
                if unaligned_b_distance1.is_nan() {
                    unaligned_b_distance1 = distance;
                } else {
                    unaligned_b_distance2 = distance;
                }
            } else if unaligned_a_normal == -1 {
                unaligned_a_normal = quad.packed_normal;
                unaligned_a_distance1 = distance;
            } else if unaligned_b_normal == -1 {
                unaligned_b_normal = quad.packed_normal;
                unaligned_b_distance1 = distance;
            } else {
                untracked_unaligned_normal_count += 1;
            }
        }

        quads_by_facing[facing_index].push(quad);
        if facing_index < FACING_DIRECTIONS {
            aligned_facing_bitmap |= 1 << facing_index;
        }
    }

    let mut mesh_facing_counts = [0i32; FACING_COUNT];
    for facing in 0..FACING_COUNT {
        mesh_facing_counts[facing] = quads_by_facing[facing].len() as i32;
    }

    let sorted_quads = flatten_by_facing(&quads_by_facing);
    let sort_type = filter_sort_type(
        sort_type_heuristic(
            &sorted_quads,
            sort_mode,
            has_unaligned,
            untracked_unaligned_normal_count,
            aligned_facing_bitmap,
            extents,
            aligned_extents_multiple,
            aligned_extremes,
            unaligned_a_normal,
            unaligned_a_distance1,
            unaligned_a_distance2,
            unaligned_b_normal,
            unaligned_b_distance1,
            unaligned_b_distance2,
        ),
        sort_mode,
    );
    let quad_hash = compute_quad_hash(&sorted_quads);
    let static_keys = if sort_type == SORT_TYPE_STATIC_NORMAL_RELATIVE {
        sorted_quads
            .iter()
            .map(|quad| float_to_comparable_int(quad.accurate_dot_product))
            .collect()
    } else {
        Vec::new()
    };

    Ok(Analyzer {
        mesh_facing_counts,
        sort_type,
        quad_hash,
        aligned_facing_bitmap,
        is_double_unaligned: aligned_facing_bitmap == 0,
        static_keys,
    })
}

fn build_quad_info(record: &TranslucentQuadRecord) -> QuadInfo {
    let facing = record.facing;
    let packed_normal = if is_aligned(facing) {
        packed_aligned_normal(facing)
    } else {
        record.packed_normal
    };
    let (mut extents, explicit_center) = compute_extents_and_center(record, facing);
    shrink_extents(&mut extents, facing);
    let center = explicit_center.unwrap_or_else(|| compute_extent_center(&extents));

    let accurate_dot_product = if is_aligned(facing) {
        extents[facing as usize] * facing_sign(facing) as f32
    } else {
        let normal = unpack_normal(packed_normal);
        center.0 * normal.0 + center.1 * normal.1 + center.2 * normal.2
    };
    let (topo_facing, quantized_dot_product) =
        compute_topo_facing_and_quantized_dot(facing, packed_normal, center, &extents);

    QuadInfo {
        positions: record.positions,
        center,
        facing,
        topo_facing,
        packed_normal,
        extents,
        accurate_dot_product,
        quantized_dot_product,
    }
}

unsafe fn record_from_native_quad(
    native_quad_address: u64,
    facing: i32,
    packed_normal: i32,
) -> TranslucentQuadRecord {
    let mut positions = [0.0; 12];
    for vertex in 0..4usize {
        let base = native_quad_address + (vertex * 32) as u64;
        let output = vertex * 3;
        positions[output] = *(base as *const f32);
        positions[output + 1] = *((base + 4) as *const f32);
        positions[output + 2] = *((base + 8) as *const f32);
    }

    TranslucentQuadRecord {
        positions,
        facing,
        packed_normal,
    }
}

fn record_is_invalid(record: &TranslucentQuadRecord) -> bool {
    let mut last = (
        record.positions[9],
        record.positions[10],
        record.positions[11],
    );
    let mut same_vertex_map = 0i32;

    for index in 0..4usize {
        let base = index * 3;
        let current = (
            record.positions[base],
            record.positions[base + 1],
            record.positions[base + 2],
        );

        if (current.0 - last.0).abs() < VERTEX_EPSILON
            && (current.1 - last.1).abs() < VERTEX_EPSILON
            && (current.2 - last.2).abs() < VERTEX_EPSILON
        {
            same_vertex_map |= 1 << index;
        }

        last = current;
    }

    same_vertex_map.count_ones() > 1
}

fn compute_extents_and_center(
    record: &TranslucentQuadRecord,
    facing: i32,
) -> ([f32; 6], Option<(f32, f32, f32)>) {
    let mut x_sum = 0.0;
    let mut y_sum = 0.0;
    let mut z_sum = 0.0;
    let mut last_x = record.positions[9];
    let mut last_y = record.positions[10];
    let mut last_z = record.positions[11];
    let mut same_vertex_map = 0i32;
    let mut extents = [
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
        f32::INFINITY,
        f32::INFINITY,
        f32::INFINITY,
    ];

    for vertex in 0..4 {
        let base = vertex * 3;
        let x = record.positions[base];
        let y = record.positions[base + 1];
        let z = record.positions[base + 2];

        extents[0] = extents[0].max(x);
        extents[1] = extents[1].max(y);
        extents[2] = extents[2].max(z);
        extents[3] = extents[3].min(x);
        extents[4] = extents[4].min(y);
        extents[5] = extents[5].min(z);

        if (x - last_x).abs() >= VERTEX_EPSILON
            || (y - last_y).abs() >= VERTEX_EPSILON
            || (z - last_z).abs() >= VERTEX_EPSILON
        {
            x_sum += x;
            y_sum += y;
            z_sum += z;
        } else {
            same_vertex_map |= 1 << vertex;
        }

        if vertex != 3 {
            last_x = x;
            last_y = y;
            last_z = z;
        }
    }

    let unique_vertices = 4 - same_vertex_map.count_ones() as i32;
    let center = if (!is_aligned(facing) || unique_vertices != 4) && unique_vertices >= 3 {
        let inv = 1.0 / unique_vertices as f32;
        Some((x_sum * inv, y_sum * inv, z_sum * inv))
    } else {
        None
    };

    (extents, center)
}

fn compute_extent_center(extents: &[f32; 6]) -> (f32, f32, f32) {
    (
        (extents[0] + extents[3]) * 0.5,
        (extents[1] + extents[4]) * 0.5,
        (extents[2] + extents[5]) * 0.5,
    )
}

fn compute_topo_facing_and_quantized_dot(
    facing: i32,
    packed_normal: i32,
    center: (f32, f32, f32),
    extents: &[f32; 6],
) -> (i32, f32) {
    if is_aligned(facing) {
        return (
            facing,
            extents[facing as usize] * facing_sign(facing) as f32,
        );
    }

    let quantized_normal = quantize_normal(unpack_normal(packed_normal));
    let topo_facing = aligned_facing_from_normal(quantized_normal).unwrap_or(FACING_UNASSIGNED);
    let quantized_dot_product = if is_aligned(topo_facing) {
        extents[topo_facing as usize] * facing_sign(topo_facing) as f32
    } else {
        dot(quantized_normal, center.0, center.1, center.2)
    };

    (topo_facing, quantized_dot_product)
}

fn quantize_normal(normal: (f32, f32, f32)) -> (f32, f32, f32) {
    let inf_norm = normal.0.abs().max(normal.1.abs()).max(normal.2.abs());
    let mut x = normal.0;
    let mut y = normal.1;
    let mut z = normal.2;
    if inf_norm != 0.0 && inf_norm != 1.0 {
        x /= inf_norm;
        y /= inf_norm;
        z /= inf_norm;
    }

    normalize3(
        (x * 4.0) as i32 as f32,
        (y * 4.0) as i32 as f32,
        (z * 4.0) as i32 as f32,
    )
}

fn aligned_facing_from_normal(normal: (f32, f32, f32)) -> Option<i32> {
    for facing in 0..FACING_DIRECTIONS {
        let aligned = accurate_aligned_normal(facing as i32);
        if float_equal(normal.0, aligned.0)
            && float_equal(normal.1, aligned.1)
            && float_equal(normal.2, aligned.2)
        {
            return Some(facing as i32);
        }
    }

    None
}

fn float_equal(a: f32, b: f32) -> bool {
    (a - b).abs() < VERTEX_EPSILON
}

fn shrink_extents(extents: &mut [f32; 6], facing: i32) {
    if facing != FACING_POS_X && facing != FACING_NEG_X {
        extents[0] -= QUANTIZE_EPSILON;
        extents[3] += QUANTIZE_EPSILON;
        if extents[3] > extents[0] {
            extents[3] = extents[0];
        }
    }
    if facing != FACING_POS_Y && facing != FACING_NEG_Y {
        extents[1] -= QUANTIZE_EPSILON;
        extents[4] += QUANTIZE_EPSILON;
        if extents[4] > extents[1] {
            extents[4] = extents[1];
        }
    }
    if facing != FACING_POS_Z && facing != FACING_NEG_Z {
        extents[2] -= QUANTIZE_EPSILON;
        extents[5] += QUANTIZE_EPSILON;
        if extents[5] > extents[2] {
            extents[5] = extents[2];
        }
    }
}

fn flatten_by_facing(quads_by_facing: &[Vec<QuadInfo>; FACING_COUNT]) -> Vec<QuadInfo> {
    let total = quads_by_facing.iter().map(Vec::len).sum();
    let mut quads = Vec::with_capacity(total);
    for quads_for_facing in quads_by_facing {
        quads.extend(quads_for_facing.iter().cloned());
    }
    quads
}

#[allow(clippy::too_many_arguments)]
fn sort_type_heuristic(
    quads: &[QuadInfo],
    sort_mode: i32,
    has_unaligned: bool,
    untracked_unaligned_normal_count: i32,
    aligned_facing_bitmap: i32,
    extents: [f32; 6],
    aligned_extents_multiple: bool,
    aligned_extremes: [f32; 6],
    unaligned_a_normal: i32,
    unaligned_a_distance1: f32,
    unaligned_a_distance2: f32,
    unaligned_b_normal: i32,
    unaligned_b_distance1: f32,
    unaligned_b_distance2: f32,
) -> i32 {
    if quads.len() <= 1 || sort_mode == SORT_MODE_NONE {
        return SORT_TYPE_NONE;
    }

    let aligned_normal_count = aligned_facing_bitmap.count_ones() as i32;
    let plane_count = get_plane_count(
        aligned_normal_count,
        aligned_extents_multiple,
        unaligned_a_distance1,
        unaligned_a_distance2,
        unaligned_b_distance1,
        unaligned_b_distance2,
    );
    let mut unaligned_normal_count = untracked_unaligned_normal_count;
    if unaligned_a_normal != -1 {
        unaligned_normal_count += 1;
    }
    if unaligned_b_normal != -1 {
        unaligned_normal_count += 1;
    }
    let normal_count = aligned_normal_count + unaligned_normal_count;

    if plane_count <= 1 {
        return SORT_TYPE_NONE;
    }

    if !has_unaligned {
        let opposing_aligned_normals = bitmap_is_opposing_aligned(aligned_facing_bitmap);
        if plane_count == 2 && opposing_aligned_normals {
            return SORT_TYPE_NONE;
        }

        if !aligned_extents_multiple {
            let mut passes_bounding_box_test = true;
            for direction in 0..FACING_DIRECTIONS {
                let extreme = aligned_extremes[direction];
                if extreme.is_infinite() {
                    continue;
                }

                let sign = if direction < 3 { 1.0 } else { -1.0 };
                if sign * extreme != extents[direction] {
                    passes_bounding_box_test = false;
                    break;
                }
            }
            if passes_bounding_box_test {
                return SORT_TYPE_NONE;
            }
        }

        if opposing_aligned_normals || aligned_normal_count == 1 {
            return SORT_TYPE_STATIC_NORMAL_RELATIVE;
        }
    } else if aligned_normal_count == 0 {
        if unaligned_normal_count == 1
            || (unaligned_normal_count == 2
                && normals_are_opposite(unaligned_a_normal, unaligned_b_normal))
        {
            return SORT_TYPE_STATIC_NORMAL_RELATIVE;
        }
    } else if plane_count == 2 {
        let aligned_direction = aligned_facing_bitmap.trailing_zeros() as i32;
        if normals_are_opposite(unaligned_a_normal, packed_aligned_normal(aligned_direction)) {
            return SORT_TYPE_STATIC_NORMAL_RELATIVE;
        }
    }

    let attempt_limit_index =
        normal_count.clamp(2, STATIC_TOPO_SORT_ATTEMPT_LIMITS.len() as i32 - 1);
    if quads.len() as i32 <= STATIC_TOPO_SORT_ATTEMPT_LIMITS[attempt_limit_index as usize] {
        SORT_TYPE_STATIC_TOPO
    } else {
        SORT_TYPE_DYNAMIC
    }
}

fn filter_sort_type(sort_type: i32, sort_mode: i32) -> i32 {
    if sort_mode == SORT_MODE_NONE {
        SORT_TYPE_NONE
    } else if sort_mode == SORT_MODE_STATIC
        && sort_type != SORT_TYPE_STATIC_NORMAL_RELATIVE
        && sort_type != SORT_TYPE_STATIC_TOPO
    {
        SORT_TYPE_NONE
    } else {
        sort_type
    }
}

fn get_plane_count(
    aligned_normal_count: i32,
    aligned_extents_multiple: bool,
    unaligned_a_distance1: f32,
    unaligned_a_distance2: f32,
    unaligned_b_distance1: f32,
    unaligned_b_distance2: f32,
) -> i32 {
    let aligned_plane_count = if aligned_extents_multiple {
        100
    } else {
        aligned_normal_count
    };

    aligned_plane_count
        + (!unaligned_a_distance1.is_nan()) as i32
        + (!unaligned_a_distance2.is_nan()) as i32
        + (!unaligned_b_distance1.is_nan()) as i32
        + (!unaligned_b_distance2.is_nan()) as i32
}

fn compute_quad_hash(quads: &[QuadInfo]) -> i32 {
    let mut quad_hash = 0i32;
    for (index, quad) in quads.iter().enumerate() {
        quad_hash = java_i32_add(
            java_i32_mul(quad_hash, 31),
            java_i32_add(compute_single_quad_hash(quad), (index as i32) * 3),
        );
    }
    quad_hash
}

fn compute_single_quad_hash(quad: &QuadInfo) -> i32 {
    let mut result = 1i32;
    result = java_i32_add(
        java_i32_mul(31, result),
        java_float_array_hash(&quad.extents),
    );
    let normal_or_facing = if is_aligned(quad.facing) {
        packed_aligned_normal(quad.facing)
    } else {
        quad.packed_normal
    };
    result = java_i32_add(java_i32_mul(31, result), normal_or_facing);
    result = java_i32_add(
        java_i32_mul(31, result),
        quad.quantized_dot_product.to_bits() as i32,
    );
    result
}

fn java_float_array_hash(values: &[f32; 6]) -> i32 {
    let mut result = 1i32;
    for value in values {
        result = java_i32_add(java_i32_mul(31, result), value.to_bits() as i32);
    }
    result
}

fn java_i32_add(a: i32, b: i32) -> i32 {
    a.wrapping_add(b)
}

fn java_i32_mul(a: i32, b: i32) -> i32 {
    a.wrapping_mul(b)
}

fn float_to_comparable_int(value: f32) -> i32 {
    let bits = value.to_bits() as i32;
    bits ^ ((bits >> 31) & 0x7fff_ffff)
}

fn bitmap_is_opposing_aligned(bitmap: i32) -> bool {
    bitmap == ((1 << FACING_POS_X) | (1 << FACING_NEG_X))
        || bitmap == ((1 << FACING_POS_Y) | (1 << FACING_NEG_Y))
        || bitmap == ((1 << FACING_POS_Z) | (1 << FACING_NEG_Z))
}

fn is_aligned(facing: i32) -> bool {
    facing != FACING_UNASSIGNED
}

fn facing_sign(facing: i32) -> i32 {
    match facing {
        FACING_POS_X | FACING_POS_Y | FACING_POS_Z => 1,
        FACING_NEG_X | FACING_NEG_Y | FACING_NEG_Z => -1,
        _ => 0,
    }
}

fn packed_aligned_normal(facing: i32) -> i32 {
    let normal = ALIGNED_NORMALS[facing as usize];
    pack_normal(normal.0, normal.1, normal.2)
}

fn pack_normal(x: i8, y: i8, z: i8) -> i32 {
    ((z as u8 as i32) << 16) | ((y as u8 as i32) << 8) | (x as u8 as i32)
}

fn unpack_normal(normal: i32) -> (f32, f32, f32) {
    (
        ((normal & 0xff) as u8 as i8) as f32 / NORMAL_COMPONENT_RANGE,
        (((normal >> 8) & 0xff) as u8 as i8) as f32 / NORMAL_COMPONENT_RANGE,
        (((normal >> 16) & 0xff) as u8 as i8) as f32 / NORMAL_COMPONENT_RANGE,
    )
}

fn normals_are_opposite(a: i32, b: i32) -> bool {
    ((a & 0xff) as u8 as i8) == -((b & 0xff) as u8 as i8)
        && (((a >> 8) & 0xff) as u8 as i8) == -(((b >> 8) & 0xff) as u8 as i8)
        && (((a >> 16) & 0xff) as u8 as i8) == -(((b >> 16) & 0xff) as u8 as i8)
}

fn sorted_quads_by_facing(records: &[TranslucentQuadRecord]) -> Result<Vec<QuadInfo>, i32> {
    let mut quads_by_facing: [Vec<QuadInfo>; FACING_COUNT] = std::array::from_fn(|_| Vec::new());

    for record in records {
        if record.facing < 0 || record.facing >= FACING_COUNT as i32 {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let quad = build_quad_info(record);
        quads_by_facing[quad.facing as usize].push(quad);
    }

    Ok(flatten_by_facing(&quads_by_facing))
}

fn static_topo_sort(
    records: &[TranslucentQuadRecord],
    fail_on_intersection: bool,
) -> Result<Option<Vec<i32>>, i32> {
    let quads = sorted_quads_by_facing(records)?;
    Ok(topo_graph_sort(&quads, fail_on_intersection))
}

fn create_section_geometry(
    records: &[TranslucentQuadRecord],
) -> Result<NativeTranslucentSectionGeometry, i32> {
    let quads = sorted_quads_by_facing(records)?;
    let aligned_separator_distances = build_aligned_separator_distances(&quads);
    Ok(NativeTranslucentSectionGeometry {
        quads,
        aligned_separator_distances,
    })
}

fn sorted_index_data_from_order(quad_count: usize, quad_indexes: &[i32]) -> Result<Vec<i32>, i32> {
    if quad_indexes.len() > quad_count {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let output_len = quad_count.checked_mul(6).ok_or(ERR_INVALID_ARGUMENT)?;
    let mut output = vec![0i32; output_len];
    let status = index::write_sorted_quad_index_buffer(&mut output, quad_indexes);
    if status != OK {
        return Err(status);
    }

    Ok(output)
}

fn static_normal_relative_index_data(
    mesh_facing_counts: &[i32],
    sort_keys: &[i32],
    quad_count: usize,
    is_double_unaligned: bool,
) -> Result<Vec<i32>, i32> {
    if mesh_facing_counts.len() != FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if sort_keys.len() < quad_count {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let output_len = quad_count.checked_mul(6).ok_or(ERR_INVALID_ARGUMENT)?;
    let mut output = vec![0i32; output_len];

    if quad_count == 0 {
        return Ok(output);
    }
    if quad_count == 1 {
        let status = index::write_sorted_quad_index_buffer(&mut output, &[0]);
        return if status == OK {
            Ok(output)
        } else {
            Err(status)
        };
    }

    if is_double_unaligned {
        let status =
            index::write_key_sorted_quad_index_buffer(&mut output, &sort_keys[..quad_count]);
        return if status == OK {
            Ok(output)
        } else {
            Err(status)
        };
    }

    let mut key_offset = 0usize;
    let mut output_offset = 0usize;
    for &quad_count_for_facing in mesh_facing_counts {
        if quad_count_for_facing < 0 {
            continue;
        }

        let count = usize::try_from(quad_count_for_facing).map_err(|_| ERR_INVALID_ARGUMENT)?;
        if count == 0 {
            continue;
        }
        if key_offset
            .checked_add(count)
            .filter(|end| *end <= sort_keys.len())
            .is_none()
        {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let index_offset = output_offset.checked_mul(6).ok_or(ERR_INVALID_ARGUMENT)?;
        let output_slice = &mut output[index_offset..];
        let status = if count == 1 {
            index::write_sorted_quad_index_buffer(output_slice, &[0])
        } else {
            index::write_key_sorted_quad_index_buffer(
                output_slice,
                &sort_keys[key_offset..key_offset + count],
            )
        };
        if status != OK {
            return Err(status);
        }

        key_offset += count;
        output_offset += count;
    }

    if output_offset != quad_count {
        return Err(ERR_INVALID_ARGUMENT);
    }

    Ok(output)
}

fn create_static_topo_sort_data(
    records: &[TranslucentQuadRecord],
    fail_on_intersection: bool,
) -> Result<Option<NativeTranslucentSortData>, i32> {
    let Some(quad_indexes) = static_topo_sort(records, fail_on_intersection)? else {
        return Ok(None);
    };
    let quad_count = records.len();
    let index_data = sorted_index_data_from_order(quad_count, &quad_indexes)?;
    Ok(Some(NativeTranslucentSortData {
        quad_count,
        kind: NativeTranslucentSortDataKind::StaticIndexData(index_data),
    }))
}

fn create_static_normal_relative_sort_data(
    mesh_facing_counts: &[i32],
    sort_keys: &[i32],
    quad_count: usize,
    is_double_unaligned: bool,
) -> Result<NativeTranslucentSortData, i32> {
    let index_data = static_normal_relative_index_data(
        mesh_facing_counts,
        sort_keys,
        quad_count,
        is_double_unaligned,
    )?;
    Ok(NativeTranslucentSortData {
        quad_count,
        kind: NativeTranslucentSortDataKind::StaticIndexData(index_data),
    })
}

fn create_dynamic_topo_sort_data(
    records: &[TranslucentQuadRecord],
) -> Result<NativeTranslucentSortData, i32> {
    let geometry = create_section_geometry(records)?;
    Ok(NativeTranslucentSortData {
        quad_count: records.len(),
        kind: NativeTranslucentSortDataKind::DynamicTopo(geometry),
    })
}

fn write_static_sort_data(sort_data: &NativeTranslucentSortData, output: &mut [i32]) -> i32 {
    let NativeTranslucentSortDataKind::StaticIndexData(index_data) = &sort_data.kind else {
        return ERR_INVALID_ARGUMENT;
    };
    let expected_index_count = match sort_data.quad_count.checked_mul(6) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if index_data.len() != expected_index_count {
        return ERR_INVALID_ARGUMENT;
    }
    if output.len() < index_data.len() {
        return ERR_CAPACITY;
    }

    output[..index_data.len()].copy_from_slice(index_data);
    OK
}

fn write_dynamic_sort_data(
    sort_data: &NativeTranslucentSortData,
    output: &mut [i32],
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
    initial: bool,
    is_direct_trigger: bool,
    state: DynamicSortState,
) -> Result<DynamicSortState, i32> {
    let NativeTranslucentSortDataKind::DynamicTopo(geometry) = &sort_data.kind else {
        return Err(ERR_INVALID_ARGUMENT);
    };
    write_dynamic_sort_index_buffer(
        geometry,
        output,
        camera_x,
        camera_y,
        camera_z,
        initial,
        is_direct_trigger,
        state,
    )
}

fn create_bsp_tree() -> NativeBspTree {
    NativeBspTree {
        nodes: Vec::new(),
        root: -1,
        index_quad_count: 0,
    }
}

fn add_bsp_node(tree: &mut NativeBspTree, node: BspNode) -> Result<i32, i32> {
    let index = i32::try_from(tree.nodes.len()).map_err(|_| ERR_CAPACITY)?;
    tree.nodes.push(node);
    Ok(index)
}

fn validate_bsp_node_index(tree: &NativeBspTree, node_index: i32) -> Result<Option<usize>, i32> {
    if node_index < 0 {
        return Ok(None);
    }

    let index = node_index as usize;
    if index >= tree.nodes.len() {
        return Err(ERR_INVALID_ARGUMENT);
    }
    Ok(Some(index))
}

fn create_bsp_remap(
    kind: i32,
    index_count: i32,
    fixed_offset: i32,
    index_map: &[i32],
) -> Result<BspRemap, i32> {
    if index_count < 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    match kind {
        0 => {
            if index_count != 0 || !index_map.is_empty() {
                return Err(ERR_INVALID_ARGUMENT);
            }
            Ok(BspRemap::none())
        }
        1 => {
            if index_count == 0 || !index_map.is_empty() {
                return Err(ERR_INVALID_ARGUMENT);
            }
            Ok(BspRemap {
                index_count: index_count as usize,
                kind: BspRemapKind::FixedOffset(fixed_offset),
            })
        }
        2 => {
            if index_count == 0 || index_map.is_empty() {
                return Err(ERR_INVALID_ARGUMENT);
            }
            Ok(BspRemap {
                index_count: index_count as usize,
                kind: BspRemapKind::IndexMap(index_map.to_vec()),
            })
        }
        _ => Err(ERR_INVALID_ARGUMENT),
    }
}

fn write_bsp_tree_index_buffer(
    tree: &NativeBspTree,
    output: &mut [i32],
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
) -> i32 {
    let Some(root_index) = (match validate_bsp_node_index(tree, tree.root) {
        Ok(value) => value,
        Err(status) => return status,
    }) else {
        return ERR_INVALID_ARGUMENT;
    };

    let mut state = BspTraversalState {
        quad_indexes: Vec::with_capacity(tree.index_quad_count),
        active_remap: None,
    };

    if let Err(status) =
        collect_bsp_node(tree, root_index, [camera_x, camera_y, camera_z], &mut state)
    {
        return status;
    }
    if state.active_remap.is_some() || state.quad_indexes.len() != tree.index_quad_count {
        return ERR_INVALID_ARGUMENT;
    }

    index::write_sorted_quad_index_buffer(output, &state.quad_indexes)
}

fn collect_optional_bsp_node(
    tree: &NativeBspTree,
    node_index: i32,
    camera: [f32; 3],
    state: &mut BspTraversalState,
) -> Result<(), i32> {
    if let Some(index) = validate_bsp_node_index(tree, node_index)? {
        collect_bsp_node(tree, index, camera, state)?;
    }
    Ok(())
}

fn collect_bsp_node(
    tree: &NativeBspTree,
    node_index: usize,
    camera: [f32; 3],
    state: &mut BspTraversalState,
) -> Result<(), i32> {
    let node = tree.nodes.get(node_index).ok_or(ERR_INVALID_ARGUMENT)?;
    match node {
        BspNode::LeafSingle { quad } => write_bsp_index(tree, state, *quad),
        BspNode::LeafDouble { quad_a, quad_b } => {
            write_bsp_index(tree, state, *quad_a)?;
            write_bsp_index(tree, state, *quad_b)
        }
        BspNode::LeafMulti { quads } => write_bsp_indexes(tree, state, quads),
        BspNode::FixedDouble {
            remap,
            first,
            second,
        } => {
            start_bsp_remap(state, node_index, remap)?;
            collect_optional_bsp_node(tree, *first, camera, state)?;
            collect_optional_bsp_node(tree, *second, camera, state)
        }
        BspNode::Binary {
            remap,
            normal,
            distance,
            inside,
            outside,
            on_plane,
        } => {
            start_bsp_remap(state, node_index, remap)?;
            let camera_inside = dot3(*normal, camera) < *distance;
            if camera_inside {
                collect_optional_bsp_node(tree, *outside, camera, state)?;
            } else {
                collect_optional_bsp_node(tree, *inside, camera, state)?;
            }
            write_bsp_indexes(tree, state, on_plane)?;
            if camera_inside {
                collect_optional_bsp_node(tree, *inside, camera, state)
            } else {
                collect_optional_bsp_node(tree, *outside, camera, state)
            }
        }
        BspNode::MultiPartition {
            remap,
            normal,
            plane_distances,
            partitions,
            on_plane_quads,
        } => {
            start_bsp_remap(state, node_index, remap)?;
            let camera_distance = dot3(*normal, camera);

            for i in 0..plane_distances.len() {
                if camera_distance <= plane_distances[i] {
                    let is_on_plane = camera_distance == plane_distances[i];
                    if is_on_plane {
                        collect_optional_bsp_node(tree, partitions[i], camera, state)?;
                    }

                    for j in ((i + 1)..=plane_distances.len()).rev() {
                        collect_optional_bsp_node(tree, partitions[j], camera, state)?;
                        write_bsp_indexes(tree, state, &on_plane_quads[j - 1])?;
                    }

                    if !is_on_plane {
                        collect_optional_bsp_node(tree, partitions[i], camera, state)?;
                    }
                    return Ok(());
                }

                collect_optional_bsp_node(tree, partitions[i], camera, state)?;
                write_bsp_indexes(tree, state, &on_plane_quads[i])?;
            }

            collect_optional_bsp_node(tree, partitions[plane_distances.len()], camera, state)
        }
    }
}

fn start_bsp_remap(
    state: &mut BspTraversalState,
    node_index: usize,
    remap: &BspRemap,
) -> Result<(), i32> {
    if !remap.is_active() {
        return Ok(());
    }
    if state.active_remap.is_some() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    state.active_remap = Some(BspActiveRemap {
        node_index,
        remaining: remap.index_count,
    });
    Ok(())
}

fn write_bsp_indexes(
    tree: &NativeBspTree,
    state: &mut BspTraversalState,
    indexes: &[i32],
) -> Result<(), i32> {
    for index in indexes {
        write_bsp_index(tree, state, *index)?;
    }
    Ok(())
}

fn write_bsp_index(
    tree: &NativeBspTree,
    state: &mut BspTraversalState,
    index: i32,
) -> Result<(), i32> {
    if index < 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let output_index = if let Some(active) = &mut state.active_remap {
        let remap_node = tree
            .nodes
            .get(active.node_index)
            .ok_or(ERR_INVALID_ARGUMENT)?;
        let remapped = match node_remap(remap_node) {
            Some(BspRemap {
                kind: BspRemapKind::FixedOffset(offset),
                ..
            }) => index.checked_add(*offset).ok_or(ERR_INVALID_ARGUMENT)?,
            Some(BspRemap {
                kind: BspRemapKind::IndexMap(map),
                ..
            }) => {
                let map_index = usize::try_from(index).map_err(|_| ERR_INVALID_ARGUMENT)?;
                *map.get(map_index).ok_or(ERR_INVALID_ARGUMENT)?
            }
            _ => return Err(ERR_INVALID_ARGUMENT),
        };

        if remapped < 0 {
            return Err(ERR_INVALID_ARGUMENT);
        }
        if active.remaining == 0 {
            return Err(ERR_INVALID_ARGUMENT);
        }
        active.remaining -= 1;
        if active.remaining == 0 {
            state.active_remap = None;
        }
        remapped
    } else {
        index
    };

    state.quad_indexes.push(output_index);
    Ok(())
}

fn node_remap(node: &BspNode) -> Option<&BspRemap> {
    match node {
        BspNode::FixedDouble { remap, .. }
        | BspNode::Binary { remap, .. }
        | BspNode::MultiPartition { remap, .. } => Some(remap),
        _ => None,
    }
}

fn dot3(a: [f32; 3], b: [f32; 3]) -> f32 {
    a[0].mul_add(b[0], a[1].mul_add(b[1], a[2] * b[2]))
}

unsafe fn ffi_i32_slice<'a>(ptr: *const i32, len: i32) -> Result<&'a [i32], i32> {
    if len < 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if len == 0 {
        return Ok(&[]);
    }
    if ptr.is_null() {
        return Err(ERR_NULL_POINTER);
    }
    Ok(slice::from_raw_parts(ptr, len as usize))
}

unsafe fn ffi_f32_slice<'a>(ptr: *const f32, len: i32) -> Result<&'a [f32], i32> {
    if len < 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if len == 0 {
        return Ok(&[]);
    }
    if ptr.is_null() {
        return Err(ERR_NULL_POINTER);
    }
    Ok(slice::from_raw_parts(ptr, len as usize))
}

fn write_distance_sorted_index_buffer(
    geometry: &NativeTranslucentSectionGeometry,
    output: &mut [i32],
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
) -> i32 {
    let keys: Vec<i32> = geometry
        .quads
        .iter()
        .map(|quad| {
            let dx = quad.center.0 - camera_x;
            let dy = quad.center.1 - camera_y;
            let dz = quad.center.2 - camera_z;
            let distance_squared = dx.mul_add(dx, dy.mul_add(dy, dz * dz));
            !(distance_squared.to_bits() as i32)
        })
        .collect();

    index::write_key_sorted_quad_index_buffer(output, &keys)
}

fn build_aligned_separator_distances(quads: &[QuadInfo]) -> [Vec<f32>; FACING_DIRECTIONS] {
    let mut distances: [Vec<f32>; FACING_DIRECTIONS] = std::array::from_fn(|_| Vec::new());

    for quad in quads {
        if is_aligned(quad.topo_facing) {
            distances[quad.topo_facing as usize].push(quad.quantized_dot_product);
        }
    }

    for distances_for_normal in &mut distances {
        distances_for_normal.sort_by(f32::total_cmp);
        distances_for_normal.dedup_by(|a, b| a.to_bits() == b.to_bits());
    }

    distances
}

struct DynamicSortState {
    gfni_trigger: bool,
    direct_trigger: bool,
    consecutive_topo_sort_failures: i32,
}

fn write_dynamic_sort_index_buffer(
    geometry: &NativeTranslucentSectionGeometry,
    output: &mut [i32],
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
    initial: bool,
    is_direct_trigger: bool,
    mut state: DynamicSortState,
) -> Result<DynamicSortState, i32> {
    if state.gfni_trigger && !is_direct_trigger {
        let topo_start = if initial {
            None
        } else {
            Some(std::time::Instant::now())
        };
        let topo_result = dynamic_topo_graph_sort(geometry, (camera_x, camera_y, camera_z), false);
        let sort_time_ns = topo_start
            .map(|start| start.elapsed().as_nanos().min(i32::MAX as u128) as i32)
            .unwrap_or(0);

        match topo_result {
            Some(order)
                if !topo_sort_timed_out(
                    initial,
                    sort_time_ns,
                    state.consecutive_topo_sort_failures,
                ) =>
            {
                let status = index::write_sorted_quad_index_buffer(output, &order);
                if status != OK {
                    return Err(status);
                }
                state.direct_trigger = false;
                state.consecutive_topo_sort_failures = 0;
            }
            Some(_) => {
                state.direct_trigger = true;
                state.gfni_trigger = false;
            }
            None => {
                state.consecutive_topo_sort_failures += 1;
                state.direct_trigger = true;
                if state.consecutive_topo_sort_failures >= topo_attempts_for_time(sort_time_ns) {
                    state.gfni_trigger = false;
                }
            }
        }
    }

    if state.direct_trigger {
        let status =
            write_distance_sorted_index_buffer(geometry, output, camera_x, camera_y, camera_z);
        if status != OK {
            return Err(status);
        }
    }

    Ok(state)
}

fn topo_sort_timed_out(initial: bool, sort_time_ns: i32, consecutive_failures: i32) -> bool {
    if initial {
        return false;
    }

    let limit = if consecutive_failures > 0 {
        750_000
    } else {
        1_000_000
    };
    sort_time_ns > limit
}

fn topo_attempts_for_time(ns: i32) -> i32 {
    if ns <= 250_000 {
        5
    } else {
        2
    }
}

fn dynamic_topo_graph_sort(
    geometry: &NativeTranslucentSectionGeometry,
    camera: (f32, f32, f32),
    fail_on_intersection: bool,
) -> Option<Vec<i32>> {
    let mut order = Vec::with_capacity(geometry.quads.len());
    let mut active_to_real_index = Vec::with_capacity(geometry.quads.len());
    let mut active_quads = Vec::with_capacity(geometry.quads.len());

    for (index, quad) in geometry.quads.iter().enumerate() {
        if point_outside_half_space(
            quad.accurate_dot_product,
            dynamic_accurate_normal(quad),
            camera.0,
            camera.1,
            camera.2,
        ) {
            active_to_real_index.push(index as i32);
            active_quads.push(quad);
        } else {
            order.push(index as i32);
        }
    }

    dynamic_topo_graph_sort_active(
        &mut order,
        &active_quads,
        Some(&active_to_real_index),
        Some(geometry),
        Some(camera),
        fail_on_intersection,
    )
}

fn dynamic_topo_graph_sort_active(
    output: &mut Vec<i32>,
    quads: &[&QuadInfo],
    active_to_real_index: Option<&[i32]>,
    geometry: Option<&NativeTranslucentSectionGeometry>,
    camera: Option<(f32, f32, f32)>,
    fail_on_intersection: bool,
) -> Option<Vec<i32>> {
    let quad_count = quads.len();

    if quad_count == 0 {
        return Some(std::mem::take(output));
    }
    if quad_count == 1 {
        output.push(active_to_real_index.map_or(0, |indexes| indexes[0]));
        return Some(std::mem::take(output));
    }
    if quad_count == 2 {
        let mut a = 0usize;
        let mut b = 1usize;
        if dynamic_quad_visible_through(quads[a], quads[b], None, None, fail_on_intersection) {
            if fail_on_intersection
                && dynamic_quad_visible_through(quads[b], quads[a], None, None, true)
            {
                return None;
            }

            a = 1;
            b = 0;
        }
        output.push(active_to_real_index.map_or(a as i32, |indexes| indexes[a]));
        output.push(active_to_real_index.map_or(b as i32, |indexes| indexes[b]));
        return Some(std::mem::take(output));
    }

    let mut unvisited = vec![true; quad_count];
    let mut visited_count = 0usize;
    let mut on_stack = vec![false; quad_count];
    let mut stack = vec![0usize; quad_count];
    let mut next_edge = vec![0usize; quad_count];

    while visited_count < quad_count {
        let mut stack_pos = 0usize;
        let root = next_set_bit(&unvisited, 0)?;
        stack[stack_pos] = root;
        on_stack[root] = true;
        next_edge[stack_pos] = 0;

        loop {
            let current_quad_index = stack[stack_pos];
            let mut next_edge_test = next_set_bit(&unvisited, next_edge[stack_pos]);
            if let Some(mut next_index) = next_edge_test {
                if current_quad_index != next_index
                    && dynamic_quad_visible_through(
                        quads[current_quad_index],
                        quads[next_index],
                        geometry,
                        camera,
                        fail_on_intersection,
                    )
                {
                    if on_stack[next_index] {
                        return None;
                    }

                    next_edge[stack_pos] = next_index + 1;
                    stack_pos += 1;
                    stack[stack_pos] = next_index;
                    on_stack[next_index] = true;
                    next_edge[stack_pos] = 0;
                    continue;
                }

                next_index += 1;
                if next_index < quad_count {
                    next_edge[stack_pos] = next_index;
                    continue;
                }
                next_edge_test = None;
            }

            if next_edge_test.is_none() {
                on_stack[current_quad_index] = false;
                visited_count += 1;
                unvisited[current_quad_index] = false;
                output.push(
                    active_to_real_index.map_or(current_quad_index as i32, |indexes| {
                        indexes[current_quad_index]
                    }),
                );

                if stack_pos == 0 {
                    break;
                }
                stack_pos -= 1;
            }
        }
    }

    Some(std::mem::take(output))
}

fn dynamic_quad_visible_through(
    quad: &QuadInfo,
    other: &QuadInfo,
    geometry: Option<&NativeTranslucentSectionGeometry>,
    camera: Option<(f32, f32, f32)>,
    intersections_visible: bool,
) -> bool {
    let result = if is_aligned(quad.topo_facing) && is_aligned(other.topo_facing) {
        if opposite_facing(quad.topo_facing) == other.topo_facing {
            false
        } else if quad.topo_facing == other.topo_facing {
            let sign = facing_sign(quad.topo_facing) as f32;
            let direction = quad.topo_facing as usize;
            sign * quad.extents[direction] > sign * other.extents[direction]
        } else {
            dynamic_orthogonal_quad_visible_through(quad, other, intersections_visible)
        }
    } else {
        let quad_normal = dynamic_accurate_normal(quad);
        let mut other_inside_quad = false;
        for vertex in 0..4 {
            let base = vertex * 3;
            if point_inside_half_space_epsilon(
                quad.accurate_dot_product,
                quad_normal,
                other.positions[base],
                other.positions[base + 1],
                other.positions[base + 2],
            ) {
                other_inside_quad = true;
                break;
            }
        }

        if !other_inside_quad {
            false
        } else {
            let other_normal = dynamic_accurate_normal(other);
            let mut quad_not_fully_inside_other = false;
            for vertex in 0..4 {
                let base = vertex * 3;
                if point_outside_half_space_epsilon(
                    other.accurate_dot_product,
                    other_normal,
                    quad.positions[base],
                    quad.positions[base + 1],
                    quad.positions[base + 2],
                ) {
                    quad_not_fully_inside_other = true;
                    break;
                }
            }
            quad_not_fully_inside_other
        }
    };

    if result {
        if let (Some(geometry), Some(camera)) = (geometry, camera) {
            return dynamic_visibility_with_separator(quad, other, geometry, camera);
        }
    }

    result
}

fn dynamic_orthogonal_quad_visible_through(
    quad: &QuadInfo,
    other: &QuadInfo,
    intersections_visible: bool,
) -> bool {
    let a_direction = quad.topo_facing as usize;
    let a_opposite = opposite_facing(quad.topo_facing) as usize;
    let b_direction = other.topo_facing as usize;
    let a_sign = facing_sign(quad.topo_facing) as f32;
    let b_sign = facing_sign(other.topo_facing) as f32;

    let b_into_a_descent = a_sign * quad.extents[a_direction] - a_sign * other.extents[a_opposite];
    let a_outside_b_ascent =
        b_sign * quad.extents[b_direction] - b_sign * other.extents[b_direction];
    let visible = b_into_a_descent > 0.0 && a_outside_b_ascent > 0.0;

    if visible && extents_intersect(&quad.extents, &other.extents) {
        if intersections_visible {
            return true;
        }
        return b_into_a_descent + a_outside_b_ascent > 1.0;
    }

    visible
}

fn dynamic_visibility_with_separator(
    quad: &QuadInfo,
    other: &QuadInfo,
    geometry: &NativeTranslucentSectionGeometry,
    camera: (f32, f32, f32),
) -> bool {
    for direction in 0..FACING_DIRECTIONS {
        let opposite_direction = opposite_facing(direction as i32) as usize;
        let sign = facing_sign(direction as i32) as f32;
        let mut separator_range_start = sign * other.extents[direction];
        let separator_range_end = sign * quad.extents[opposite_direction];
        if separator_range_start > separator_range_end {
            continue;
        }

        let normal = accurate_aligned_normal(direction as i32);
        let camera_distance = dot(normal, camera.0, camera.1, camera.2);
        if camera_distance > separator_range_end {
            continue;
        }

        separator_range_start = camera_distance;
        if query_range(
            &geometry.aligned_separator_distances[direction],
            separator_range_start,
            separator_range_end,
        ) {
            return false;
        }
    }

    true
}

fn query_range(sorted_distances: &[f32], start: f32, end: f32) -> bool {
    if sorted_distances.is_empty() {
        return false;
    }

    match sorted_distances.binary_search_by(|distance| distance.total_cmp(&start)) {
        Ok(_) => true,
        Err(insertion_point) => {
            insertion_point < sorted_distances.len() && sorted_distances[insertion_point] <= end
        }
    }
}

fn topo_graph_sort(quads: &[QuadInfo], fail_on_intersection: bool) -> Option<Vec<i32>> {
    let quad_count = quads.len();
    let mut output = Vec::with_capacity(quad_count);

    if quad_count == 0 {
        return Some(output);
    }
    if quad_count == 1 {
        output.push(0);
        return Some(output);
    }
    if quad_count == 2 {
        let mut a = 0usize;
        let mut b = 1usize;
        if quad_visible_through(&quads[a], &quads[b], fail_on_intersection) {
            if fail_on_intersection
                && quad_visible_through_intersections_visible(&quads[b], &quads[a])
            {
                return None;
            }

            a = 1;
            b = 0;
        }
        output.push(a as i32);
        output.push(b as i32);
        return Some(output);
    }

    let mut unvisited = vec![true; quad_count];
    let mut visited_count = 0usize;
    let mut on_stack = vec![false; quad_count];
    let mut stack = vec![0usize; quad_count];
    let mut next_edge = vec![0usize; quad_count];

    while visited_count < quad_count {
        let mut stack_pos = 0usize;
        let root = next_set_bit(&unvisited, 0)?;
        stack[stack_pos] = root;
        on_stack[root] = true;
        next_edge[stack_pos] = 0;

        loop {
            let current_quad_index = stack[stack_pos];
            let mut next_edge_test = next_set_bit(&unvisited, next_edge[stack_pos]);
            if let Some(mut next_index) = next_edge_test {
                if current_quad_index != next_index
                    && quad_visible_through(
                        &quads[current_quad_index],
                        &quads[next_index],
                        fail_on_intersection,
                    )
                {
                    if on_stack[next_index] {
                        return None;
                    }

                    next_edge[stack_pos] = next_index + 1;
                    stack_pos += 1;
                    stack[stack_pos] = next_index;
                    on_stack[next_index] = true;
                    next_edge[stack_pos] = 0;
                    continue;
                }

                next_index += 1;
                if next_index < quad_count {
                    next_edge[stack_pos] = next_index;
                    continue;
                }
                next_edge_test = None;
            }

            if next_edge_test.is_none() {
                on_stack[current_quad_index] = false;
                visited_count += 1;
                unvisited[current_quad_index] = false;
                output.push(current_quad_index as i32);

                if stack_pos == 0 {
                    break;
                }
                stack_pos -= 1;
            }
        }
    }

    Some(output)
}

fn next_set_bit(bits: &[bool], start: usize) -> Option<usize> {
    bits.iter()
        .enumerate()
        .skip(start)
        .find_map(|(index, value)| (*value).then_some(index))
}

fn quad_visible_through(quad: &QuadInfo, other: &QuadInfo, intersections_visible: bool) -> bool {
    if std::ptr::eq(quad, other) {
        return false;
    }

    if is_aligned(quad.facing) && is_aligned(other.facing) {
        if opposite_facing(quad.facing) == other.facing {
            return false;
        }

        if quad.facing == other.facing {
            let sign = facing_sign(quad.facing) as f32;
            let direction = quad.facing as usize;
            return sign * quad.extents[direction] > sign * other.extents[direction];
        }

        return orthogonal_quad_visible_through(quad, other, intersections_visible);
    }

    let quad_normal = accurate_normal(quad);
    let mut other_inside_quad = false;
    for vertex in 0..4 {
        let base = vertex * 3;
        if point_inside_half_space_epsilon(
            quad.accurate_dot_product,
            quad_normal,
            other.positions[base],
            other.positions[base + 1],
            other.positions[base + 2],
        ) {
            other_inside_quad = true;
            break;
        }
    }

    if !other_inside_quad {
        return false;
    }

    let other_normal = accurate_normal(other);
    for vertex in 0..4 {
        let base = vertex * 3;
        if point_outside_half_space_epsilon(
            other.accurate_dot_product,
            other_normal,
            quad.positions[base],
            quad.positions[base + 1],
            quad.positions[base + 2],
        ) {
            return true;
        }
    }

    false
}

fn quad_visible_through_intersections_visible(quad: &QuadInfo, other: &QuadInfo) -> bool {
    quad_visible_through(quad, other, true)
}

fn orthogonal_quad_visible_through(
    quad: &QuadInfo,
    other: &QuadInfo,
    intersections_visible: bool,
) -> bool {
    let a_direction = quad.facing as usize;
    let a_opposite = opposite_facing(quad.facing) as usize;
    let b_direction = other.facing as usize;
    let a_sign = facing_sign(quad.facing) as f32;
    let b_sign = facing_sign(other.facing) as f32;

    let b_into_a_descent = a_sign * quad.extents[a_direction] - a_sign * other.extents[a_opposite];
    let a_outside_b_ascent =
        b_sign * quad.extents[b_direction] - b_sign * other.extents[b_direction];
    let visible = b_into_a_descent > 0.0 && a_outside_b_ascent > 0.0;

    if visible && extents_intersect(&quad.extents, &other.extents) {
        if intersections_visible {
            return true;
        }
        return b_into_a_descent + a_outside_b_ascent > 1.0;
    }

    visible
}

fn accurate_normal(quad: &QuadInfo) -> (f32, f32, f32) {
    if is_aligned(quad.facing) {
        accurate_aligned_normal(quad.facing)
    } else {
        unpack_normal(quad.packed_normal)
    }
}

fn dynamic_accurate_normal(quad: &QuadInfo) -> (f32, f32, f32) {
    if is_aligned(quad.topo_facing) {
        accurate_aligned_normal(quad.topo_facing)
    } else {
        unpack_normal(quad.packed_normal)
    }
}

fn accurate_aligned_normal(facing: i32) -> (f32, f32, f32) {
    let normal = ALIGNED_NORMALS[facing as usize];
    (
        normal.0 as f32 / NORMAL_COMPONENT_RANGE,
        normal.1 as f32 / NORMAL_COMPONENT_RANGE,
        normal.2 as f32 / NORMAL_COMPONENT_RANGE,
    )
}

fn point_outside_half_space(
    plane_distance: f32,
    plane_normal: (f32, f32, f32),
    x: f32,
    y: f32,
    z: f32,
) -> bool {
    dot(plane_normal, x, y, z) > plane_distance
}

fn point_inside_half_space_epsilon(
    plane_distance: f32,
    plane_normal: (f32, f32, f32),
    x: f32,
    y: f32,
    z: f32,
) -> bool {
    dot(plane_normal, x, y, z) + HALF_SPACE_EPSILON < plane_distance
}

fn point_outside_half_space_epsilon(
    plane_distance: f32,
    plane_normal: (f32, f32, f32),
    x: f32,
    y: f32,
    z: f32,
) -> bool {
    dot(plane_normal, x, y, z) - HALF_SPACE_EPSILON > plane_distance
}

fn dot(normal: (f32, f32, f32), x: f32, y: f32, z: f32) -> f32 {
    normal.0 * x + normal.1 * y + normal.2 * z
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

fn extents_intersect(a: &[f32; 6], b: &[f32; 6]) -> bool {
    for axis in 0..3 {
        let opposite = axis + 3;
        if a[axis] <= b[opposite] || b[axis] <= a[opposite] {
            return false;
        }
    }

    true
}

fn opposite_facing(facing: i32) -> i32 {
    match facing {
        FACING_POS_X => FACING_NEG_X,
        FACING_POS_Y => FACING_NEG_Y,
        FACING_POS_Z => FACING_NEG_Z,
        FACING_NEG_X => FACING_POS_X,
        FACING_NEG_Y => FACING_POS_Y,
        FACING_NEG_Z => FACING_POS_Z,
        _ => FACING_UNASSIGNED,
    }
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_translucent_analyzer_verify() -> i32 {
    verify()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeTranslucentAnalyzer {
        records: Vec::new(),
    })) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeTranslucentAnalyzer));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_append_record(
    handle: u64,
    record: *const TranslucentQuadRecord,
) -> i32 {
    if handle == 0 || record.is_null() {
        return ERR_NULL_POINTER;
    }

    let analyzer = &mut *(handle as *mut NativeTranslucentAnalyzer);
    let record = *record;
    if record_is_invalid(&record) {
        return SORT_FAILED;
    }

    analyzer.records.push(record);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_append_native_quad(
    handle: u64,
    native_quad_address: u64,
    facing: i32,
    packed_normal: i32,
) -> i32 {
    if handle == 0 || native_quad_address == 0 {
        return ERR_NULL_POINTER;
    }
    if !(0..FACING_COUNT as i32).contains(&facing) {
        return ERR_INVALID_ARGUMENT;
    }

    let analyzer = &mut *(handle as *mut NativeTranslucentAnalyzer);
    let record = record_from_native_quad(native_quad_address, facing, packed_normal);
    if record_is_invalid(&record) {
        return SORT_FAILED;
    }

    analyzer.records.push(record);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_append_native_quad_batch(
    handle: u64,
    native_quad_address: u64,
    quad_count: i32,
    facing: i32,
    packed_normals: *const i32,
    validity_output_address: u64,
    output_valid_count: *mut i32,
) -> i32 {
    append_native_quad_batch_to_analyzer(
        handle,
        native_quad_address,
        quad_count,
        facing,
        packed_normals,
        validity_output_address,
        output_valid_count,
    )
}

pub unsafe fn append_native_quad_batch_to_analyzer(
    handle: u64,
    native_quad_address: u64,
    quad_count: i32,
    facing: i32,
    packed_normals: *const i32,
    validity_output_address: u64,
    output_valid_count: *mut i32,
) -> i32 {
    if handle == 0 || output_valid_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if quad_count < 0 || !(0..FACING_COUNT as i32).contains(&facing) {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count == 0 {
        *output_valid_count = 0;
        return OK;
    }
    if native_quad_address == 0 || packed_normals.is_null() || validity_output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let quad_count = quad_count as usize;
    let analyzer = &mut *(handle as *mut NativeTranslucentAnalyzer);
    let packed_normals = slice::from_raw_parts(packed_normals, quad_count);
    let validity_output = slice::from_raw_parts_mut(validity_output_address as *mut u8, quad_count);
    let mut valid_count = 0i32;

    for index in 0..quad_count {
        let quad_address = native_quad_address + (index as u64 * NATIVE_QUAD_STRIDE);
        let record = record_from_native_quad(quad_address, facing, packed_normals[index]);

        if record_is_invalid(&record) {
            validity_output[index] = 0;
            continue;
        }

        validity_output[index] = 1;
        analyzer.records.push(record);
        valid_count += 1;
    }

    *output_valid_count = valid_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_record_count(
    handle: u64,
    output_count: *mut i32,
) -> i32 {
    if handle == 0 || output_count.is_null() {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    *output_count = analyzer.records.len() as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_write_records_by_facing(
    handle: u64,
    output_records: *mut TranslucentQuadRecord,
    output_records_len: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    if output_records_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    if output_records_len as usize != analyzer.records.len() {
        return ERR_CAPACITY;
    }
    if analyzer.records.is_empty() {
        return OK;
    }
    if !analyzer.records.is_empty() && output_records.is_null() {
        return ERR_NULL_POINTER;
    }

    let output_records = slice::from_raw_parts_mut(output_records, output_records_len as usize);
    let mut output_index = 0usize;
    for facing in 0..FACING_COUNT as i32 {
        for record in &analyzer.records {
            if record.facing == facing {
                output_records[output_index] = *record;
                output_index += 1;
            }
        }
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_analyze(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    sort_mode: i32,
    metrics: *mut i32,
    metrics_len: i32,
    mesh_facing_counts: *mut i32,
    mesh_facing_counts_len: i32,
    static_keys: *mut i32,
    static_keys_len: i32,
) -> i32 {
    if record_count < 0
        || metrics_len < 5
        || mesh_facing_counts_len != FACING_COUNT as i32
        || static_keys_len < 0
    {
        return ERR_INVALID_ARGUMENT;
    }
    if metrics.is_null() || mesh_facing_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if record_count > 0 && records.is_null() {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let analyzer = match analyze(records, sort_mode) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if analyzer.static_keys.len() > static_keys_len as usize {
        return ERR_CAPACITY;
    }
    if !analyzer.static_keys.is_empty() && static_keys.is_null() {
        return ERR_NULL_POINTER;
    }

    let metrics = slice::from_raw_parts_mut(metrics, metrics_len as usize);
    metrics[0] = analyzer.sort_type;
    metrics[1] = analyzer.quad_hash;
    metrics[2] = analyzer.aligned_facing_bitmap;
    metrics[3] = if analyzer.is_double_unaligned { 1 } else { 0 };
    metrics[4] = analyzer.static_keys.len() as i32;

    let mesh_facing_counts =
        slice::from_raw_parts_mut(mesh_facing_counts, mesh_facing_counts_len as usize);
    mesh_facing_counts.copy_from_slice(&analyzer.mesh_facing_counts);

    if !analyzer.static_keys.is_empty() {
        let static_keys = slice::from_raw_parts_mut(static_keys, static_keys_len as usize);
        static_keys[..analyzer.static_keys.len()].copy_from_slice(&analyzer.static_keys);
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_analyzer_analyze_handle(
    handle: u64,
    sort_mode: i32,
    metrics: *mut i32,
    metrics_len: i32,
    mesh_facing_counts: *mut i32,
    mesh_facing_counts_len: i32,
    static_keys: *mut i32,
    static_keys_len: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_analyzer_analyze(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        sort_mode,
        metrics,
        metrics_len,
        mesh_facing_counts,
        mesh_facing_counts_len,
        static_keys,
        static_keys_len,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_static_topo_sort(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    fail_on_intersection: i32,
    output_indices: *mut i32,
    output_indices_len: i32,
) -> i32 {
    if record_count < 0 || output_indices_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if record_count > 0 && (records.is_null() || output_indices.is_null()) {
        return ERR_NULL_POINTER;
    }
    if output_indices_len < record_count {
        return ERR_CAPACITY;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let order = match static_topo_sort(records, fail_on_intersection != 0) {
        Ok(Some(value)) => value,
        Ok(None) => return SORT_FAILED,
        Err(status) => return status,
    };

    if !order.is_empty() {
        let output_indices = slice::from_raw_parts_mut(output_indices, output_indices_len as usize);
        output_indices[..order.len()].copy_from_slice(&order);
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_static_topo_sort_handle(
    handle: u64,
    fail_on_intersection: i32,
    output_indices: *mut i32,
    output_indices_len: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_static_topo_sort(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        fail_on_intersection,
        output_indices,
        output_indices_len,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_section_geometry_create(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    output_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (record_count > 0 && records.is_null()) {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let geometry = match create_section_geometry(records) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(geometry)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_section_geometry_create_from_analyzer(
    analyzer_handle: u64,
    output_handle: *mut u64,
) -> i32 {
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(analyzer_handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_section_geometry_create(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        output_handle,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_section_geometry_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(
        handle as *mut NativeTranslucentSectionGeometry,
    ));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_topo_create(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    fail_on_intersection: i32,
    output_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (record_count > 0 && records.is_null()) {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let sort_data = match create_static_topo_sort_data(records, fail_on_intersection != 0) {
        Ok(Some(value)) => value,
        Ok(None) => {
            *output_handle = 0;
            return SORT_FAILED;
        }
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(sort_data)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_topo_create_from_analyzer(
    analyzer_handle: u64,
    fail_on_intersection: i32,
    output_handle: *mut u64,
) -> i32 {
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(analyzer_handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_sort_data_static_topo_create(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        fail_on_intersection,
        output_handle,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_snr_create(
    mesh_facing_counts: *const i32,
    mesh_facing_count_len: i32,
    sort_keys: *const i32,
    sort_key_len: i32,
    quad_count: i32,
    is_double_unaligned: i32,
    output_handle: *mut u64,
) -> i32 {
    if mesh_facing_count_len < 0 || sort_key_len < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null()
        || mesh_facing_counts.is_null()
        || (sort_key_len > 0 && sort_keys.is_null())
    {
        return ERR_NULL_POINTER;
    }

    let mesh_facing_counts =
        slice::from_raw_parts(mesh_facing_counts, mesh_facing_count_len as usize);
    let sort_keys = if sort_key_len == 0 {
        &[]
    } else {
        slice::from_raw_parts(sort_keys, sort_key_len as usize)
    };
    let sort_data = match create_static_normal_relative_sort_data(
        mesh_facing_counts,
        sort_keys,
        quad_count as usize,
        is_double_unaligned != 0,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(sort_data)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_dynamic_topo_create(
    records: *const TranslucentQuadRecord,
    record_count: i32,
    output_handle: *mut u64,
) -> i32 {
    if record_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || (record_count > 0 && records.is_null()) {
        return ERR_NULL_POINTER;
    }

    let records = if record_count == 0 {
        &[]
    } else {
        slice::from_raw_parts(records, record_count as usize)
    };
    let sort_data = match create_dynamic_topo_sort_data(records) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_handle = Box::into_raw(Box::new(sort_data)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_dynamic_topo_create_from_analyzer(
    analyzer_handle: u64,
    output_handle: *mut u64,
) -> i32 {
    if analyzer_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let analyzer = &*(analyzer_handle as *const NativeTranslucentAnalyzer);
    mattmc_sodium_translucent_sort_data_dynamic_topo_create(
        analyzer.records.as_ptr(),
        analyzer.records.len() as i32,
        output_handle,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeTranslucentSortData));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_static_write(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let sort_data = &*(handle as *const NativeTranslucentSortData);
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    write_static_sort_data(sort_data, output)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_sort_data_dynamic_write(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
    initial: i32,
    is_direct_trigger: i32,
    gfni_trigger: i32,
    direct_trigger: i32,
    consecutive_topo_sort_failures: i32,
    output_state: *mut i32,
    output_state_len: i32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_state_len < 3 || consecutive_topo_sort_failures < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 || output_state.is_null() {
        return ERR_NULL_POINTER;
    }

    let sort_data = &*(handle as *const NativeTranslucentSortData);
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    let state = DynamicSortState {
        gfni_trigger: gfni_trigger != 0,
        direct_trigger: direct_trigger != 0,
        consecutive_topo_sort_failures,
    };
    let state = match write_dynamic_sort_data(
        sort_data,
        output,
        camera_x,
        camera_y,
        camera_z,
        initial != 0,
        is_direct_trigger != 0,
        state,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let output_state = slice::from_raw_parts_mut(output_state, output_state_len as usize);
    output_state[0] = if state.gfni_trigger { 1 } else { 0 };
    output_state[1] = if state.direct_trigger { 1 } else { 0 };
    output_state[2] = state.consecutive_topo_sort_failures;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(create_bsp_tree())) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeBspTree));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_set_root(
    handle: u64,
    root_node: i32,
    index_quad_count: i32,
) -> i32 {
    if index_quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let tree = &mut *(handle as *mut NativeBspTree);
    if let Err(status) = validate_bsp_node_index(tree, root_node) {
        return status;
    }
    tree.root = root_node;
    tree.index_quad_count = index_quad_count as usize;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_add_leaf_single(
    handle: u64,
    quad: i32,
    output_node: *mut i32,
) -> i32 {
    if quad < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_node.is_null() {
        return ERR_NULL_POINTER;
    }

    let tree = &mut *(handle as *mut NativeBspTree);
    match add_bsp_node(tree, BspNode::LeafSingle { quad }) {
        Ok(index) => {
            *output_node = index;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_add_leaf_double(
    handle: u64,
    quad_a: i32,
    quad_b: i32,
    output_node: *mut i32,
) -> i32 {
    if quad_a < 0 || quad_b < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_node.is_null() {
        return ERR_NULL_POINTER;
    }

    let tree = &mut *(handle as *mut NativeBspTree);
    match add_bsp_node(tree, BspNode::LeafDouble { quad_a, quad_b }) {
        Ok(index) => {
            *output_node = index;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_add_leaf_multi(
    handle: u64,
    indexes: *const i32,
    index_count: i32,
    output_node: *mut i32,
) -> i32 {
    if index_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_node.is_null() {
        return ERR_NULL_POINTER;
    }

    let quads = match ffi_i32_slice(indexes, index_count) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if quads.iter().any(|index| *index < 0) {
        return ERR_INVALID_ARGUMENT;
    }

    let tree = &mut *(handle as *mut NativeBspTree);
    match add_bsp_node(
        tree,
        BspNode::LeafMulti {
            quads: quads.to_vec(),
        },
    ) {
        Ok(index) => {
            *output_node = index;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_add_fixed_double(
    handle: u64,
    remap_kind: i32,
    remap_index_count: i32,
    fixed_offset: i32,
    index_map: *const i32,
    index_map_len: i32,
    first: i32,
    second: i32,
    output_node: *mut i32,
) -> i32 {
    if index_map_len < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_node.is_null() {
        return ERR_NULL_POINTER;
    }

    let tree = &mut *(handle as *mut NativeBspTree);
    if let Err(status) = validate_bsp_node_index(tree, first) {
        return status;
    }
    if let Err(status) = validate_bsp_node_index(tree, second) {
        return status;
    }

    let index_map = match ffi_i32_slice(index_map, index_map_len) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let remap = match create_bsp_remap(remap_kind, remap_index_count, fixed_offset, index_map) {
        Ok(value) => value,
        Err(status) => return status,
    };
    match add_bsp_node(
        tree,
        BspNode::FixedDouble {
            remap,
            first,
            second,
        },
    ) {
        Ok(index) => {
            *output_node = index;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_add_binary(
    handle: u64,
    remap_kind: i32,
    remap_index_count: i32,
    fixed_offset: i32,
    index_map: *const i32,
    index_map_len: i32,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
    inside: i32,
    outside: i32,
    on_plane: *const i32,
    on_plane_len: i32,
    output_node: *mut i32,
) -> i32 {
    if index_map_len < 0 || on_plane_len < 0 || !distance.is_finite() {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_node.is_null() {
        return ERR_NULL_POINTER;
    }

    let tree = &mut *(handle as *mut NativeBspTree);
    if let Err(status) = validate_bsp_node_index(tree, inside) {
        return status;
    }
    if let Err(status) = validate_bsp_node_index(tree, outside) {
        return status;
    }

    let index_map = match ffi_i32_slice(index_map, index_map_len) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let remap = match create_bsp_remap(remap_kind, remap_index_count, fixed_offset, index_map) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let on_plane = match ffi_i32_slice(on_plane, on_plane_len) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if on_plane.iter().any(|index| *index < 0) {
        return ERR_INVALID_ARGUMENT;
    }

    match add_bsp_node(
        tree,
        BspNode::Binary {
            remap,
            normal: [normal_x, normal_y, normal_z],
            distance,
            inside,
            outside,
            on_plane: on_plane.to_vec(),
        },
    ) {
        Ok(index) => {
            *output_node = index;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_add_multi_partition(
    handle: u64,
    remap_kind: i32,
    remap_index_count: i32,
    fixed_offset: i32,
    index_map: *const i32,
    index_map_len: i32,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    plane_distances: *const f32,
    plane_distance_count: i32,
    partitions: *const i32,
    partition_count: i32,
    on_plane_indexes: *const i32,
    on_plane_index_count: i32,
    on_plane_counts: *const i32,
    on_plane_count_count: i32,
    output_node: *mut i32,
) -> i32 {
    if index_map_len < 0
        || plane_distance_count < 0
        || partition_count < 0
        || on_plane_index_count < 0
        || on_plane_count_count < 0
    {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_node.is_null() {
        return ERR_NULL_POINTER;
    }
    if partition_count != plane_distance_count + 1 || on_plane_count_count != plane_distance_count {
        return ERR_INVALID_ARGUMENT;
    }

    let tree = &mut *(handle as *mut NativeBspTree);
    let partition_indexes = match ffi_i32_slice(partitions, partition_count) {
        Ok(value) => value,
        Err(status) => return status,
    };
    for partition in partition_indexes {
        if let Err(status) = validate_bsp_node_index(tree, *partition) {
            return status;
        }
    }

    let index_map = match ffi_i32_slice(index_map, index_map_len) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let remap = match create_bsp_remap(remap_kind, remap_index_count, fixed_offset, index_map) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let distances = match ffi_f32_slice(plane_distances, plane_distance_count) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if distances.iter().any(|distance| !distance.is_finite()) {
        return ERR_INVALID_ARGUMENT;
    }
    let on_plane_flat = match ffi_i32_slice(on_plane_indexes, on_plane_index_count) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if on_plane_flat.iter().any(|index| *index < 0) {
        return ERR_INVALID_ARGUMENT;
    }
    let counts = match ffi_i32_slice(on_plane_counts, on_plane_count_count) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let mut on_plane_quads = Vec::with_capacity(counts.len());
    let mut offset = 0usize;
    for count in counts {
        if *count < 0 {
            return ERR_INVALID_ARGUMENT;
        }
        let next_offset = match offset.checked_add(*count as usize) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        if next_offset > on_plane_flat.len() {
            return ERR_INVALID_ARGUMENT;
        }
        on_plane_quads.push(on_plane_flat[offset..next_offset].to_vec());
        offset = next_offset;
    }
    if offset != on_plane_flat.len() {
        return ERR_INVALID_ARGUMENT;
    }

    match add_bsp_node(
        tree,
        BspNode::MultiPartition {
            remap,
            normal: [normal_x, normal_y, normal_z],
            plane_distances: distances.to_vec(),
            partitions: partition_indexes.to_vec(),
            on_plane_quads,
        },
    ) {
        Ok(index) => {
            *output_node = index;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_bsp_tree_write_index_buffer(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let tree = &*(handle as *const NativeBspTree);
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    write_bsp_tree_index_buffer(tree, output, camera_x, camera_y, camera_z)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_section_geometry_distance_sort_write(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let geometry = &*(handle as *const NativeTranslucentSectionGeometry);
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    write_distance_sorted_index_buffer(geometry, output, camera_x, camera_y, camera_z)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_translucent_section_geometry_dynamic_sort_write(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    camera_x: f32,
    camera_y: f32,
    camera_z: f32,
    initial: i32,
    is_direct_trigger: i32,
    gfni_trigger: i32,
    direct_trigger: i32,
    consecutive_topo_sort_failures: i32,
    output_state: *mut i32,
    output_state_len: i32,
) -> i32 {
    if output_capacity < 0 || output_capacity % std::mem::size_of::<i32>() as i32 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_state_len < 3 || consecutive_topo_sort_failures < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address == 0 || output_state.is_null() {
        return ERR_NULL_POINTER;
    }

    let geometry = &*(handle as *const NativeTranslucentSectionGeometry);
    let output = slice::from_raw_parts_mut(
        output_address as *mut i32,
        output_capacity as usize / std::mem::size_of::<i32>(),
    );
    let state = DynamicSortState {
        gfni_trigger: gfni_trigger != 0,
        direct_trigger: direct_trigger != 0,
        consecutive_topo_sort_failures,
    };
    let state = match write_dynamic_sort_index_buffer(
        geometry,
        output,
        camera_x,
        camera_y,
        camera_z,
        initial != 0,
        is_direct_trigger != 0,
        state,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let output_state = slice::from_raw_parts_mut(output_state, output_state_len as usize);
    output_state[0] = if state.gfni_trigger { 1 } else { 0 };
    output_state[1] = if state.direct_trigger { 1 } else { 0 };
    output_state[2] = state.consecutive_topo_sort_failures;
    OK
}

#[cfg(test)]
mod tests {
    use super::*;

    fn vertex_record(facing: i32, z: f32) -> TranslucentQuadRecord {
        TranslucentQuadRecord {
            positions: [0.0, 0.0, z, 1.0, 0.0, z, 1.0, 1.0, z, 0.0, 1.0, z],
            facing,
            packed_normal: packed_aligned_normal(facing),
        }
    }

    #[test]
    fn record_layout_matches_java_stride() {
        assert_eq!(56, std::mem::size_of::<TranslucentQuadRecord>());
    }

    #[test]
    fn opposing_faces_need_no_sort() {
        let records = [
            vertex_record(FACING_POS_Z, 1.0),
            vertex_record(FACING_NEG_Z, 0.0),
        ];
        let analysis = analyze(&records, 2).unwrap();

        assert_eq!(SORT_TYPE_NONE, analysis.sort_type);
        assert_eq!(1, analysis.mesh_facing_counts[FACING_POS_Z as usize]);
        assert_eq!(1, analysis.mesh_facing_counts[FACING_NEG_Z as usize]);
    }

    #[test]
    fn same_direction_planes_use_static_normal_relative_keys() {
        let records = [
            vertex_record(FACING_POS_Z, 0.0),
            vertex_record(FACING_POS_Z, 1.0),
        ];
        let analysis = analyze(&records, 2).unwrap();

        assert_eq!(SORT_TYPE_STATIC_NORMAL_RELATIVE, analysis.sort_type);
        assert_eq!(2, analysis.static_keys.len());
        assert!(analysis.static_keys[0] < analysis.static_keys[1]);
    }

    #[test]
    fn static_topo_sort_orders_visible_parallel_planes_back_to_front() {
        let records = [
            vertex_record(FACING_POS_Z, 1.0),
            vertex_record(FACING_POS_Z, 0.0),
        ];
        let order = static_topo_sort(&records, false).unwrap().unwrap();

        assert_eq!(vec![1, 0], order);
    }

    #[test]
    fn distance_sort_orders_far_quads_before_near_quads() {
        let records = [
            vertex_record(FACING_POS_Z, 1.0),
            vertex_record(FACING_POS_Z, 4.0),
        ];
        let geometry = create_section_geometry(&records).unwrap();
        let mut output = vec![0; 12];

        assert_eq!(
            OK,
            write_distance_sorted_index_buffer(&geometry, &mut output, 0.0, 0.0, 0.0)
        );
        assert_eq!(vec![4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0], output);
    }

    #[test]
    fn bsp_tree_traverses_binary_partition_by_camera_side() {
        let mut tree = create_bsp_tree();
        let inside = add_bsp_node(&mut tree, BspNode::LeafSingle { quad: 0 }).unwrap();
        let outside = add_bsp_node(&mut tree, BspNode::LeafSingle { quad: 1 }).unwrap();
        let root = add_bsp_node(
            &mut tree,
            BspNode::Binary {
                remap: BspRemap::none(),
                normal: [1.0, 0.0, 0.0],
                distance: 0.5,
                inside,
                outside,
                on_plane: vec![2],
            },
        )
        .unwrap();
        tree.root = root;
        tree.index_quad_count = 3;

        let mut output = vec![0; 18];
        assert_eq!(
            OK,
            write_bsp_tree_index_buffer(&tree, &mut output, 0.0, 0.0, 0.0)
        );
        assert_eq!(
            vec![4, 5, 6, 6, 7, 4, 8, 9, 10, 10, 11, 8, 0, 1, 2, 2, 3, 0],
            output
        );

        assert_eq!(
            OK,
            write_bsp_tree_index_buffer(&tree, &mut output, 1.0, 0.0, 0.0)
        );
        assert_eq!(
            vec![0, 1, 2, 2, 3, 0, 8, 9, 10, 10, 11, 8, 4, 5, 6, 6, 7, 4],
            output
        );
    }

    #[test]
    fn bsp_tree_applies_fixed_offset_remap_for_reused_subtree() {
        let mut tree = create_bsp_tree();
        let first = add_bsp_node(&mut tree, BspNode::LeafMulti { quads: vec![0, 1] }).unwrap();
        let second = add_bsp_node(&mut tree, BspNode::LeafSingle { quad: 2 }).unwrap();
        let root = add_bsp_node(
            &mut tree,
            BspNode::FixedDouble {
                remap: BspRemap {
                    index_count: 3,
                    kind: BspRemapKind::FixedOffset(3),
                },
                first,
                second,
            },
        )
        .unwrap();
        tree.root = root;
        tree.index_quad_count = 3;

        let mut output = vec![0; 18];
        assert_eq!(
            OK,
            write_bsp_tree_index_buffer(&tree, &mut output, 0.0, 0.0, 0.0)
        );
        assert_eq!(
            vec![12, 13, 14, 14, 15, 12, 16, 17, 18, 18, 19, 16, 20, 21, 22, 22, 23, 20],
            output
        );
    }
}
