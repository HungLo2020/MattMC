//! Translucent quad geometry and full-quad mutation helpers.
//!
//! Full quads retain all vertex attributes needed for BSP splitting. The
//! helpers here update geometric metadata after mutation while preserving the
//! Java ABI record layout used by replay fixtures and native bindings.

use super::*;

pub(super) fn compute_same_vertex_map(positions: &[f32; 12]) -> i32 {
    let mut last = (positions[9], positions[10], positions[11]);
    let mut same_vertex_map = 0i32;

    for index in 0..4usize {
        let base = index * 3;
        let current = (positions[base], positions[base + 1], positions[base + 2]);

        if (current.0 - last.0).abs() < VERTEX_EPSILON
            && (current.1 - last.1).abs() < VERTEX_EPSILON
            && (current.2 - last.2).abs() < VERTEX_EPSILON
        {
            same_vertex_map |= 1 << index;
        }

        last = current;
    }

    same_vertex_map
}

pub(super) fn compute_extents_and_center(
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

pub(super) fn compute_extent_center(extents: &[f32; 6]) -> (f32, f32, f32) {
    (
        (extents[0] + extents[3]) * 0.5,
        (extents[1] + extents[4]) * 0.5,
        (extents[2] + extents[5]) * 0.5,
    )
}

pub(super) fn full_quad_record(
    quad: &NativeFullQuadBuffer,
    facing: i32,
    packed_normal: i32,
) -> TranslucentQuadRecord {
    let mut positions = [0.0; 12];
    for vertex in 0..4usize {
        let output = vertex * 3;
        positions[output] = quad.vertices[vertex].x;
        positions[output + 1] = quad.vertices[vertex].y;
        positions[output + 2] = quad.vertices[vertex].z;
    }

    TranslucentQuadRecord {
        positions,
        facing,
        packed_normal,
    }
}

pub(super) fn create_full_quad(
    source: &NativeFullQuadBuffer,
    facing: i32,
    packed_normal: i32,
) -> Result<NativeFullTQuad, i32> {
    if !(0..FACING_COUNT as i32).contains(&facing) {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let record = full_quad_record(source, facing, packed_normal);
    if record_is_invalid(&record) {
        return Err(SORT_FAILED);
    }

    let info = build_quad_info(&record);
    let mut quad = *source;
    clamp_full_quad_lights_for_split_interpolation(&mut quad);

    Ok(NativeFullTQuad {
        quad,
        info,
        same_vertex_map: compute_same_vertex_map(&record.positions),
        normal_is_very_accurate: false,
        accurate_normal: [0.0; 3],
        has_updated_vertices: false,
        write_to_index: -1,
    })
}

pub(super) fn clamp_full_quad_lights_for_split_interpolation(quad: &mut NativeFullQuadBuffer) {
    for vertex in &mut quad.vertices {
        vertex.light = clamp_split_light(vertex.light);
    }
}

pub(super) fn clamp_split_light(light: i32) -> i32 {
    let block = (((light as u32) & 0xff) as i32).clamp(8, 248);
    let sky = ((((light as u32) >> 16) & 0xff) as i32).clamp(8, 248);
    (sky << 16) | block
}

pub(super) fn write_full_quad_state(
    quad: &NativeFullTQuad,
    output: *mut NativeFullQuadState,
) -> i32 {
    if output.is_null() {
        return ERR_NULL_POINTER;
    }

    unsafe {
        *output = NativeFullQuadState {
            positions: quad.info.positions,
            extents: quad.info.extents,
            center: [quad.info.center.0, quad.info.center.1, quad.info.center.2],
            accurate_normal: quad.accurate_normal,
            accurate_dot_product: quad.info.accurate_dot_product,
            quantized_dot_product: quad.info.quantized_dot_product,
            facing: quad.info.facing,
            packed_normal: quad.info.packed_normal,
            same_vertex_map: quad.same_vertex_map,
            normal_is_very_accurate: i32::from(quad.normal_is_very_accurate),
            has_updated_vertices: i32::from(quad.has_updated_vertices),
            write_to_index: quad.write_to_index,
        };
    }

    OK
}

pub(super) fn full_quad_refresh_after_vertex_modification(quad: &mut NativeFullTQuad) {
    let old_accurate_dot = quad.info.accurate_dot_product;
    let old_quantized_dot = quad.info.quantized_dot_product;
    let record = full_quad_record(&quad.quad, quad.info.facing, quad.info.packed_normal);
    let mut info = build_quad_info(&record);

    // Splitting preserves the original plane. Java only refreshed extents,
    // center, and cached vertex positions after a split vertex mutation.
    info.accurate_dot_product = old_accurate_dot;
    info.quantized_dot_product = old_quantized_dot;

    quad.same_vertex_map = compute_same_vertex_map(&record.positions);
    quad.info = info;
}

pub(super) fn full_quad_very_accurate_normal(quad: &mut NativeFullTQuad) -> [f32; 3] {
    if is_aligned(quad.info.facing) {
        let normal = accurate_aligned_normal(quad.info.facing);
        return [normal.0, normal.1, normal.2];
    }

    if !quad.normal_is_very_accurate {
        let v = &quad.quad.vertices;
        let dx0 = v[2].x - v[0].x;
        let dy0 = v[2].y - v[0].y;
        let dz0 = v[2].z - v[0].z;
        let dx1 = v[3].x - v[1].x;
        let dy1 = v[3].y - v[1].y;
        let dz1 = v[3].z - v[1].z;

        let (x, y, z) = normalize3(
            dy0 * dz1 - dz0 * dy1,
            dz0 * dx1 - dx0 * dz1,
            dx0 * dy1 - dy0 * dx1,
        );
        quad.accurate_normal = [x, y, z];
        quad.info.accurate_dot_product = dot(
            (x, y, z),
            quad.info.center.0,
            quad.info.center.1,
            quad.info.center.2,
        );
        quad.normal_is_very_accurate = true;
    }

    quad.accurate_normal
}

pub(super) fn full_quad_classify(
    quad: &NativeFullTQuad,
    plane: (f32, f32, f32),
    distance: f32,
) -> (i32, i32) {
    let mut inside_map = 0;
    let mut on_plane_map = 0;

    for index in 0..4usize {
        let vertex = quad.quad.vertices[index];
        let delta = dot(plane, vertex.x, vertex.y, vertex.z) - distance;
        if delta.abs() < VERTEX_EPSILON {
            on_plane_map |= 1 << index;
        } else if delta < 0.0 {
            inside_map |= 1 << index;
        }
    }

    (inside_map, on_plane_map)
}

pub(super) fn copy_full_quad_vertex(from: NativeFullQuadVertex, target: &mut NativeFullQuadVertex) {
    *target = from;
}

pub(super) fn copy_full_quad_vertex_to_indexes(
    quad: &mut NativeFullQuadBuffer,
    from: usize,
    targets: &[usize],
) {
    let value = quad.vertices[from];
    for target in targets {
        copy_full_quad_vertex(value, &mut quad.vertices[*target]);
    }
}

pub(super) fn mix_color(start: i32, end: i32, weight: f32) -> i32 {
    let weight = ((weight * 255.0) as i32 & 0xff) as i64;
    let inverse = 255 - weight;
    let start = start as u32 as u64;
    let end = end as u32 as u64;

    let hi = ((start & 0x00ff00ff) * weight as u64) + ((end & 0x00ff00ff) * inverse as u64);
    let lo = ((start & 0xff00ff00) * weight as u64) + ((end & 0xff00ff00) * inverse as u64);
    ((((hi + 0x00ff00ff) >> 8) & 0x00ff00ff) | (((lo + 0xff00ff00) >> 8) & 0xff00ff00)) as u32
        as i32
}

pub(super) fn lerp(weight: f32, start: f32, end: f32) -> f32 {
    start + weight * (end - start)
}

pub(super) fn interpolate_full_quad_attributes(
    split_distance: f32,
    split_plane: (f32, f32, f32),
    inside: NativeFullQuadVertex,
    outside: NativeFullQuadVertex,
) -> Result<NativeFullQuadVertex, i32> {
    let inside_to_outside_x = outside.x - inside.x;
    let inside_to_outside_y = outside.y - inside.y;
    let inside_to_outside_z = outside.z - inside.z;

    if inside_to_outside_x.abs() < VERTEX_EPSILON
        && inside_to_outside_y.abs() < VERTEX_EPSILON
        && inside_to_outside_z.abs() < VERTEX_EPSILON
    {
        return Ok(inside);
    }

    let split_plane_edge_dot = dot(
        split_plane,
        inside_to_outside_x,
        inside_to_outside_y,
        inside_to_outside_z,
    );
    if split_plane_edge_dot == 0.0 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let outside_amount =
        (split_distance - dot(split_plane, inside.x, inside.y, inside.z)) / split_plane_edge_dot;
    if outside_amount >= 1.0 {
        return Ok(outside);
    }
    if outside_amount <= 0.0 {
        return Ok(inside);
    }

    let block_light = lerp(
        outside_amount,
        (inside.light & 0xff) as f32,
        (outside.light & 0xff) as f32,
    ) as i32;
    let sky_light = lerp(
        outside_amount,
        (inside.light >> 16) as f32,
        (outside.light >> 16) as f32,
    ) as i32;

    Ok(NativeFullQuadVertex {
        x: inside.x + inside_to_outside_x * outside_amount,
        y: inside.y + inside_to_outside_y * outside_amount,
        z: inside.z + inside_to_outside_z * outside_amount,
        color: mix_color(inside.color, outside.color, outside_amount),
        ao: lerp(outside_amount, inside.ao, outside.ao),
        u: lerp(outside_amount, inside.u, outside.u),
        v: lerp(outside_amount, inside.v, outside.v),
        light: ((sky_light & 0xff) << 16) | (block_light & 0xff),
    })
}

pub(super) fn full_quad_split_even(
    vertex_inside_map: i32,
    inside_quad: &mut NativeFullTQuad,
    outside_quad: &mut NativeFullTQuad,
    split_plane: (f32, f32, f32),
    split_distance: f32,
) -> Result<(), i32> {
    for index_a in 0..4usize {
        let index_b = (index_a + 1) & 0b11;
        let inside_a = (vertex_inside_map & (1 << index_a)) != 0;
        let inside_b = (vertex_inside_map & (1 << index_b)) != 0;
        if inside_a == inside_b {
            continue;
        }

        let (inside_index, outside_index) = if inside_a {
            (index_a, index_b)
        } else {
            (index_b, index_a)
        };
        let interpolated = interpolate_full_quad_attributes(
            split_distance,
            split_plane,
            inside_quad.quad.vertices[inside_index],
            outside_quad.quad.vertices[outside_index],
        )?;
        inside_quad.quad.vertices[outside_index] = interpolated;
        outside_quad.quad.vertices[inside_index] = interpolated;
    }

    full_quad_refresh_after_vertex_modification(inside_quad);
    full_quad_refresh_after_vertex_modification(outside_quad);
    Ok(())
}

pub(super) fn full_quad_split_odd(
    corner_index: usize,
    corner_quad: &mut NativeFullTQuad,
    cut_quad: &mut NativeFullTQuad,
    bulk_quad: &mut NativeFullTQuad,
    split_plane: (f32, f32, f32),
    split_distance: f32,
) -> Result<(), i32> {
    if corner_index >= 4 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let prev_index = (corner_index + 3) & 0b11;
    let next_index = (corner_index + 1) & 0b11;
    let opposite_index = (corner_index + 2) & 0b11;
    let corner_vertex = corner_quad.quad.vertices[corner_index];

    let next = interpolate_full_quad_attributes(
        split_distance,
        split_plane,
        corner_vertex,
        bulk_quad.quad.vertices[next_index],
    )?;
    corner_quad.quad.vertices[next_index] = next;
    cut_quad.quad.vertices[next_index] = next;
    bulk_quad.quad.vertices[corner_index] = next;

    let prev = interpolate_full_quad_attributes(
        split_distance,
        split_plane,
        corner_vertex,
        bulk_quad.quad.vertices[prev_index],
    )?;
    corner_quad.quad.vertices[prev_index] = prev;
    corner_quad.quad.vertices[opposite_index] = prev;
    cut_quad.quad.vertices[corner_index] = prev;

    copy_full_quad_vertex_to_indexes(&mut cut_quad.quad, prev_index, &[opposite_index]);

    full_quad_refresh_after_vertex_modification(corner_quad);
    full_quad_refresh_after_vertex_modification(cut_quad);
    full_quad_refresh_after_vertex_modification(bulk_quad);
    Ok(())
}

pub(super) fn full_quad_split_triangle_corner(
    corner_index: usize,
    corner_quad: &mut NativeFullTQuad,
    bulk_quad: &mut NativeFullTQuad,
    split_plane: (f32, f32, f32),
    split_distance: f32,
) -> Result<(), i32> {
    if corner_index >= 4 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let prev_index = (corner_index + 3) & 0b11;
    let next_index = (corner_index + 1) & 0b11;
    let opposite_index = (corner_index + 2) & 0b11;
    let corner_vertex = corner_quad.quad.vertices[corner_index];

    let next = interpolate_full_quad_attributes(
        split_distance,
        split_plane,
        corner_vertex,
        bulk_quad.quad.vertices[next_index],
    )?;
    corner_quad.quad.vertices[next_index] = next;
    corner_quad.quad.vertices[opposite_index] = next;
    bulk_quad.quad.vertices[corner_index] = next;

    copy_full_quad_vertex_to_indexes(&mut bulk_quad.quad, prev_index, &[opposite_index]);

    let prev = interpolate_full_quad_attributes(
        split_distance,
        split_plane,
        corner_vertex,
        bulk_quad.quad.vertices[prev_index],
    )?;
    corner_quad.quad.vertices[prev_index] = prev;
    bulk_quad.quad.vertices[prev_index] = prev;

    full_quad_refresh_after_vertex_modification(corner_quad);
    full_quad_refresh_after_vertex_modification(bulk_quad);
    Ok(())
}

pub(super) fn full_quad_split_triangle_vertex(
    inside_index: usize,
    outside_index: usize,
    duplicate_index: i32,
    duplicate_is_inside: bool,
    inside_quad: &mut NativeFullTQuad,
    outside_quad: &mut NativeFullTQuad,
    split_plane: (f32, f32, f32),
    split_distance: f32,
) -> Result<(), i32> {
    if inside_index >= 4 || outside_index >= 4 || duplicate_index >= 4 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let interpolated = interpolate_full_quad_attributes(
        split_distance,
        split_plane,
        inside_quad.quad.vertices[inside_index],
        outside_quad.quad.vertices[outside_index],
    )?;
    inside_quad.quad.vertices[outside_index] = interpolated;
    outside_quad.quad.vertices[inside_index] = interpolated;

    if duplicate_index >= 0 {
        let duplicate_index = duplicate_index as usize;
        if duplicate_is_inside {
            outside_quad.quad.vertices[duplicate_index] = interpolated;
        } else {
            inside_quad.quad.vertices[duplicate_index] = interpolated;
        }
    }

    full_quad_refresh_after_vertex_modification(inside_quad);
    full_quad_refresh_after_vertex_modification(outside_quad);
    Ok(())
}

pub(super) fn compute_topo_facing_and_quantized_dot(
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

pub(super) fn quantize_normal(normal: (f32, f32, f32)) -> (f32, f32, f32) {
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

pub(super) fn aligned_facing_from_normal(normal: (f32, f32, f32)) -> Option<i32> {
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

pub(super) fn float_equal(a: f32, b: f32) -> bool {
    (a - b).abs() < VERTEX_EPSILON
}

pub(super) fn shrink_extents(extents: &mut [f32; 6], facing: i32) {
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

pub(super) fn flatten_by_facing(quads_by_facing: &[Vec<QuadInfo>; FACING_COUNT]) -> Vec<QuadInfo> {
    let total = quads_by_facing.iter().map(Vec::len).sum();
    let mut quads = Vec::with_capacity(total);
    for quads_for_facing in quads_by_facing {
        quads.extend(quads_for_facing.iter().cloned());
    }
    quads
}

#[allow(clippy::too_many_arguments)]
pub(super) fn sort_type_heuristic(
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

pub(super) fn filter_sort_type(sort_type: i32, sort_mode: i32) -> i32 {
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

pub(super) fn get_plane_count(
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

pub(super) fn compute_quad_hash(quads: &[QuadInfo]) -> i32 {
    let mut quad_hash = 0i32;
    for (index, quad) in quads.iter().enumerate() {
        quad_hash = java_i32_add(
            java_i32_mul(quad_hash, 31),
            java_i32_add(compute_single_quad_hash(quad), (index as i32) * 3),
        );
    }
    quad_hash
}

pub(super) fn compute_single_quad_hash(quad: &QuadInfo) -> i32 {
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

pub(super) fn java_float_array_hash(values: &[f32; 6]) -> i32 {
    let mut result = 1i32;
    for value in values {
        result = java_i32_add(java_i32_mul(31, result), value.to_bits() as i32);
    }
    result
}

pub(super) fn java_i32_add(a: i32, b: i32) -> i32 {
    a.wrapping_add(b)
}

pub(super) fn java_i32_mul(a: i32, b: i32) -> i32 {
    a.wrapping_mul(b)
}

pub(super) fn float_to_comparable_int(value: f32) -> i32 {
    let bits = value.to_bits() as i32;
    bits ^ ((bits >> 31) & 0x7fff_ffff)
}

pub(super) fn bitmap_is_opposing_aligned(bitmap: i32) -> bool {
    bitmap == ((1 << FACING_POS_X) | (1 << FACING_NEG_X))
        || bitmap == ((1 << FACING_POS_Y) | (1 << FACING_NEG_Y))
        || bitmap == ((1 << FACING_POS_Z) | (1 << FACING_NEG_Z))
}

pub(super) fn is_aligned(facing: i32) -> bool {
    facing != FACING_UNASSIGNED
}

pub(super) fn facing_sign(facing: i32) -> i32 {
    match facing {
        FACING_POS_X | FACING_POS_Y | FACING_POS_Z => 1,
        FACING_NEG_X | FACING_NEG_Y | FACING_NEG_Z => -1,
        _ => 0,
    }
}

pub(super) fn packed_aligned_normal(facing: i32) -> i32 {
    let normal = ALIGNED_NORMALS[facing as usize];
    pack_normal(normal.0, normal.1, normal.2)
}

pub(super) fn pack_normal(x: i8, y: i8, z: i8) -> i32 {
    ((z as u8 as i32) << 16) | ((y as u8 as i32) << 8) | (x as u8 as i32)
}

pub(super) fn unpack_normal(normal: i32) -> (f32, f32, f32) {
    (
        ((normal & 0xff) as u8 as i8) as f32 / NORMAL_COMPONENT_RANGE,
        (((normal >> 8) & 0xff) as u8 as i8) as f32 / NORMAL_COMPONENT_RANGE,
        (((normal >> 16) & 0xff) as u8 as i8) as f32 / NORMAL_COMPONENT_RANGE,
    )
}

pub(super) fn normals_are_opposite(a: i32, b: i32) -> bool {
    ((a & 0xff) as u8 as i8) == -((b & 0xff) as u8 as i8)
        && (((a >> 8) & 0xff) as u8 as i8) == -(((b >> 8) & 0xff) as u8 as i8)
        && (((a >> 16) & 0xff) as u8 as i8) == -(((b >> 16) & 0xff) as u8 as i8)
}
