//! Sort-data creation and index-buffer emission.
//!
//! Static sort data stores prepared index order. Dynamic sort data retains
//! section geometry and writes camera-relative index buffers without changing
//! the stable Java-facing sort-data handles.

use super::*;

pub(super) fn create_section_geometry(
    records: &[TranslucentQuadRecord],
) -> Result<NativeTranslucentSectionGeometry, i32> {
    let quads = sorted_quads_by_facing(records)?;
    let aligned_separator_distances = build_aligned_separator_distances(&quads);
    Ok(NativeTranslucentSectionGeometry {
        quads,
        aligned_separator_distances,
    })
}

pub(super) fn sorted_index_data_from_order(
    quad_count: usize,
    quad_indexes: &[i32],
) -> Result<Vec<i32>, i32> {
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

pub(super) fn static_normal_relative_index_data(
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

pub(super) fn create_static_topo_sort_data(
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

pub(super) fn create_static_order_sort_data(
    quad_count: usize,
    quad_indexes: &[i32],
) -> Result<NativeTranslucentSortData, i32> {
    let index_data = sorted_index_data_from_order(quad_count, quad_indexes)?;
    Ok(NativeTranslucentSortData {
        quad_count,
        kind: NativeTranslucentSortDataKind::StaticIndexData(index_data),
    })
}

pub(super) fn create_static_normal_relative_sort_data(
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

pub(super) fn create_dynamic_topo_sort_data(
    records: &[TranslucentQuadRecord],
) -> Result<NativeTranslucentSortData, i32> {
    let geometry = create_section_geometry(records)?;
    Ok(NativeTranslucentSortData {
        quad_count: records.len(),
        kind: NativeTranslucentSortDataKind::DynamicTopo(geometry),
    })
}

pub(super) fn create_geometry_planes_from_records(
    records: &[TranslucentQuadRecord],
) -> Result<NativeGeometryPlanes, i32> {
    let mut collector = NativeGeometryPlanes::new();

    for record in records {
        if record.facing < 0 || record.facing >= FACING_COUNT as i32 {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let quad = build_quad_info(record);
        if is_aligned(quad.topo_facing) {
            collector.add_aligned_plane(quad.topo_facing, quad.quantized_dot_product)?;
        } else {
            let normal = quantize_normal(unpack_normal(quad.packed_normal));
            collector
                .add_unaligned_plane([normal.0, normal.1, normal.2], quad.quantized_dot_product)?;
        }
    }

    Ok(collector)
}

pub(super) fn write_static_sort_data(
    sort_data: &NativeTranslucentSortData,
    output: &mut [i32],
) -> i32 {
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

pub(super) fn write_dynamic_sort_data(
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

pub(super) fn write_distance_sorted_index_buffer(
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

pub(super) fn build_aligned_separator_distances(
    quads: &[QuadInfo],
) -> [Vec<f32>; FACING_DIRECTIONS] {
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

pub(super) struct DynamicSortState {
    pub(super) gfni_trigger: bool,
    pub(super) direct_trigger: bool,
    pub(super) consecutive_topo_sort_failures: i32,
}

pub(super) fn write_dynamic_sort_index_buffer(
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

pub(super) fn topo_sort_timed_out(
    initial: bool,
    sort_time_ns: i32,
    consecutive_failures: i32,
) -> bool {
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

pub(super) fn topo_attempts_for_time(ns: i32) -> i32 {
    if ns <= 250_000 {
        5
    } else {
        2
    }
}

pub(super) fn dynamic_topo_graph_sort(
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

pub(super) fn dynamic_topo_graph_sort_active(
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

pub(super) fn dynamic_quad_visible_through(
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

pub(super) fn dynamic_orthogonal_quad_visible_through(
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

pub(super) fn dynamic_visibility_with_separator(
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

pub(super) fn query_range(sorted_distances: &[f32], start: f32, end: f32) -> bool {
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
