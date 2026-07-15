//! Analyzer records and sort-type classification.
//!
//! This module ingests retained translucent quad records, computes Java-parity
//! metadata, and chooses the static or dynamic sorting strategy without owning
//! raw FFI handles.

use super::*;

pub fn verify() -> i32 {
    if std::mem::size_of::<TranslucentQuadRecord>() == 56
        && std::mem::size_of::<NativeFullQuadVertex>() == 32
        && std::mem::size_of::<NativeFullQuadBuffer>() == 152
        && std::mem::size_of::<NativeFullQuadState>() == 128
    {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
}

pub(super) fn analyze(records: &[TranslucentQuadRecord], sort_mode: i32) -> Result<Analyzer, i32> {
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

pub(super) fn build_quad_info(record: &TranslucentQuadRecord) -> QuadInfo {
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

pub(super) fn build_topo_quad_info(record: &TranslucentTopoQuadRecord) -> Result<QuadInfo, i32> {
    let facing = record.facing;
    if !(0..FACING_COUNT as i32).contains(&facing) {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let packed_normal = if is_aligned(facing) {
        packed_aligned_normal(facing)
    } else {
        record.packed_normal
    };
    let extents = record.extents;
    let center = compute_extent_center(&extents);
    let (topo_facing, quantized_dot_product) =
        compute_topo_facing_and_quantized_dot(facing, packed_normal, center, &extents);

    Ok(QuadInfo {
        positions: record.positions,
        center,
        facing,
        topo_facing,
        packed_normal,
        extents,
        accurate_dot_product: record.accurate_dot_product,
        quantized_dot_product,
    })
}

pub(super) fn build_topo_quad_infos(
    records: &[TranslucentTopoQuadRecord],
) -> Result<Vec<QuadInfo>, i32> {
    records.iter().map(build_topo_quad_info).collect()
}

pub(super) unsafe fn record_from_native_quad(
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

pub(super) fn record_is_invalid(record: &TranslucentQuadRecord) -> bool {
    compute_same_vertex_map(&record.positions).count_ones() > 1
}
