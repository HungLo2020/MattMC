//! Topological visibility ordering for translucent quads.
//!
//! Topology sorting reasons about half-space visibility, aligned faces, and
//! active index remaps. It is used by both static analyzer output and dynamic
//! runtime sorting before BSP fallback is required.

use super::*;

pub(super) fn sorted_quads_by_facing(
    records: &[TranslucentQuadRecord],
) -> Result<Vec<QuadInfo>, i32> {
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

pub(super) fn static_topo_sort(
    records: &[TranslucentQuadRecord],
    fail_on_intersection: bool,
) -> Result<Option<Vec<i32>>, i32> {
    let quads = sorted_quads_by_facing(records)?;
    Ok(topo_graph_sort(&quads, fail_on_intersection))
}

pub(super) fn topo_graph_sort_topo_records(
    records: &[TranslucentTopoQuadRecord],
    active_to_real_index: Option<&[i32]>,
    fail_on_intersection: bool,
) -> Result<Option<Vec<i32>>, i32> {
    if let Some(indexes) = active_to_real_index {
        if indexes.len() < records.len() {
            return Err(ERR_INVALID_ARGUMENT);
        }
    }

    let quads = build_topo_quad_infos(records)?;
    let Some(order) = topo_graph_sort(&quads, fail_on_intersection) else {
        return Ok(None);
    };

    if let Some(indexes) = active_to_real_index {
        let mut remapped = Vec::with_capacity(order.len());
        for index in order {
            if index < 0 {
                return Err(ERR_INVALID_ARGUMENT);
            }
            let index = index as usize;
            if index >= indexes.len() {
                return Err(ERR_INVALID_ARGUMENT);
            }
            remapped.push(indexes[index]);
        }
        Ok(Some(remapped))
    } else {
        Ok(Some(order))
    }
}

pub(super) fn bsp_double_leaf_possible(
    quad_a: &QuadInfo,
    quad_b: &QuadInfo,
    fail_on_intersection: bool,
) -> bool {
    let facing_a = quad_a.facing;
    let facing_b = quad_b.facing;

    if !is_aligned(facing_a) || !is_aligned(facing_b) {
        return normals_are_opposite(quad_a.packed_normal, quad_b.packed_normal)
            || quad_a.packed_normal == quad_b.packed_normal
                && quad_a.accurate_dot_product == quad_b.accurate_dot_product;
    }

    if quad_a.extents[facing_a as usize] == quad_b.extents[facing_b as usize] {
        return true;
    }

    if facing_a == opposite_facing(facing_b) {
        return true;
    }

    !orthogonal_quad_visible_through(quad_a, quad_b, fail_on_intersection)
        && !orthogonal_quad_visible_through(quad_b, quad_a, fail_on_intersection)
}

pub(super) fn create_topo_quad_store(
    records: &[TranslucentTopoQuadRecord],
) -> Result<NativeTopoQuadStore, i32> {
    let mut quads = Vec::with_capacity(records.len());
    for record in records {
        quads.push(Some(build_topo_quad_info(record)?));
    }
    Ok(NativeTopoQuadStore { quads })
}

pub(super) fn topo_quad_store_set(
    store: &mut NativeTopoQuadStore,
    index: i32,
    record: &TranslucentTopoQuadRecord,
) -> i32 {
    if index < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let quad = match build_topo_quad_info(record) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let index = index as usize;
    if index == store.quads.len() {
        store.quads.push(Some(quad));
    } else if index < store.quads.len() {
        store.quads[index] = Some(quad);
    } else {
        return ERR_INVALID_ARGUMENT;
    }

    OK
}

pub(super) fn topo_quad_store_remove(store: &mut NativeTopoQuadStore, index: i32) -> i32 {
    if index < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let index = index as usize;
    if index >= store.quads.len() {
        return ERR_INVALID_ARGUMENT;
    }

    store.quads[index] = None;
    OK
}

pub(super) fn topo_graph_sort(quads: &[QuadInfo], fail_on_intersection: bool) -> Option<Vec<i32>> {
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

pub(super) fn next_set_bit(bits: &[bool], start: usize) -> Option<usize> {
    bits.iter()
        .enumerate()
        .skip(start)
        .find_map(|(index, value)| (*value).then_some(index))
}

pub(super) fn quad_visible_through(
    quad: &QuadInfo,
    other: &QuadInfo,
    intersections_visible: bool,
) -> bool {
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

pub(super) fn quad_visible_through_intersections_visible(
    quad: &QuadInfo,
    other: &QuadInfo,
) -> bool {
    quad_visible_through(quad, other, true)
}

pub(super) fn orthogonal_quad_visible_through(
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

pub(super) fn accurate_normal(quad: &QuadInfo) -> (f32, f32, f32) {
    if is_aligned(quad.facing) {
        accurate_aligned_normal(quad.facing)
    } else {
        unpack_normal(quad.packed_normal)
    }
}

pub(super) fn dynamic_accurate_normal(quad: &QuadInfo) -> (f32, f32, f32) {
    if is_aligned(quad.topo_facing) {
        accurate_aligned_normal(quad.topo_facing)
    } else {
        unpack_normal(quad.packed_normal)
    }
}

pub(super) fn accurate_aligned_normal(facing: i32) -> (f32, f32, f32) {
    let normal = ALIGNED_NORMALS[facing as usize];
    (
        normal.0 as f32 / NORMAL_COMPONENT_RANGE,
        normal.1 as f32 / NORMAL_COMPONENT_RANGE,
        normal.2 as f32 / NORMAL_COMPONENT_RANGE,
    )
}

pub(super) fn point_outside_half_space(
    plane_distance: f32,
    plane_normal: (f32, f32, f32),
    x: f32,
    y: f32,
    z: f32,
) -> bool {
    dot(plane_normal, x, y, z) > plane_distance
}

pub(super) fn point_inside_half_space_epsilon(
    plane_distance: f32,
    plane_normal: (f32, f32, f32),
    x: f32,
    y: f32,
    z: f32,
) -> bool {
    dot(plane_normal, x, y, z) + HALF_SPACE_EPSILON < plane_distance
}

pub(super) fn point_outside_half_space_epsilon(
    plane_distance: f32,
    plane_normal: (f32, f32, f32),
    x: f32,
    y: f32,
    z: f32,
) -> bool {
    dot(plane_normal, x, y, z) - HALF_SPACE_EPSILON > plane_distance
}

pub(super) fn dot(normal: (f32, f32, f32), x: f32, y: f32, z: f32) -> f32 {
    normal.0 * x + normal.1 * y + normal.2 * z
}

pub(super) fn normalize3(x: f32, y: f32, z: f32) -> (f32, f32, f32) {
    let value = x * x + y * y + z * z;
    let coefficient = if value == 0.0 {
        1.0
    } else {
        1.0 / value.sqrt()
    };
    (x * coefficient, y * coefficient, z * coefficient)
}

pub(super) fn extents_intersect(a: &[f32; 6], b: &[f32; 6]) -> bool {
    for axis in 0..3 {
        let opposite = axis + 3;
        if a[axis] <= b[opposite] || b[axis] <= a[opposite] {
            return false;
        }
    }

    true
}

pub(super) fn opposite_facing(facing: i32) -> i32 {
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
