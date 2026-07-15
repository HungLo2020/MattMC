//! BSP construction, reuse, splitting, and traversal.
//!
//! BSP nodes preserve the Java-compatible index-buffer contract while allowing
//! native full quads to be split and retained by opaque handles. Reuse remaps
//! are explicit so dynamic sort buffers can keep stable semantic ordering.

use super::*;

pub(super) fn create_bsp_tree() -> NativeBspTree {
    NativeBspTree {
        nodes: Vec::new(),
        root: -1,
        index_quad_count: 0,
    }
}

pub(super) fn prepare_bsp_reuse_data(
    quads: &[QuadInfo],
    indexes: &[i32],
) -> Option<BspNodeReuseData> {
    if indexes.len() <= BSP_NODE_REUSE_THRESHOLD {
        return None;
    }

    let mut quad_extents = Vec::with_capacity(indexes.len());
    let mut max_index = -1;
    for &index in indexes {
        let quad = quad_at(quads, index).ok()?;
        quad_extents.push(quad.extents);
        max_index = max_index.max(index);
    }

    Some(BspNodeReuseData {
        quad_extents,
        indexes: indexes.to_vec(),
        index_count: indexes.len(),
        max_index,
    })
}

pub(super) fn prepare_bsp_reuse_remap(
    quads: &[QuadInfo],
    indexes: &[i32],
    reuse_data: &BspNodeReuseData,
) -> Option<BspRemap> {
    if reuse_data.quad_extents.len() != indexes.len() {
        return None;
    }

    for (position, &index) in indexes.iter().enumerate() {
        if quad_at(quads, index).ok()?.extents != reuse_data.quad_extents[position] {
            return None;
        }
    }

    let map_len = usize::try_from(reuse_data.max_index.checked_add(1)?).ok()?;
    let mut index_map = vec![0; map_len];
    let mut first_offset = 0;
    let mut fixed_offset = true;

    for (position, &old_index) in reuse_data.indexes.iter().enumerate() {
        let old_index_usize = usize::try_from(old_index).ok()?;
        let new_index = *indexes.get(position)?;
        *index_map.get_mut(old_index_usize)? = new_index;
        let offset = new_index.checked_sub(old_index)?;
        if position == 0 {
            first_offset = offset;
        } else if first_offset != offset {
            fixed_offset = false;
        }
    }

    if fixed_offset {
        Some(BspRemap {
            index_count: reuse_data.index_count,
            kind: BspRemapKind::FixedOffset(first_offset),
        })
    } else {
        Some(BspRemap {
            index_count: reuse_data.index_count,
            kind: BspRemapKind::IndexMap(index_map),
        })
    }
}

pub(super) fn set_bsp_root_remap(tree: &mut NativeBspTree, remap: BspRemap) -> Result<(), i32> {
    let root = validate_bsp_node_index(tree, tree.root)?.ok_or(ERR_INVALID_ARGUMENT)?;
    match tree.nodes.get_mut(root).ok_or(ERR_INVALID_ARGUMENT)? {
        BspNode::FixedDouble {
            remap: node_remap, ..
        }
        | BspNode::Binary {
            remap: node_remap, ..
        }
        | BspNode::MultiPartition {
            remap: node_remap, ..
        } => {
            *node_remap = remap;
            Ok(())
        }
        _ => {
            if remap.is_active() {
                Err(ERR_INVALID_ARGUMENT)
            } else {
                Ok(())
            }
        }
    }
}

pub(super) fn try_reuse_bsp_root(
    quads: &[QuadInfo],
    indexes: &[i32],
    old_root_handle: u64,
    geometry_planes: &mut NativeGeometryPlanes,
) -> Result<Option<BspBuildOutput>, i32> {
    if old_root_handle == 0 {
        return Ok(None);
    }

    let old_root = unsafe { &*(old_root_handle as *const NativeBspReusableRoot) };
    let Some(remap) = prepare_bsp_reuse_remap(quads, indexes, &old_root.reuse_data) else {
        return Ok(None);
    };

    let mut tree = old_root.tree.clone();
    set_bsp_root_remap(&mut tree, remap)?;
    *geometry_planes = old_root.geometry_planes.clone();
    let reusable_root = Box::new(NativeBspReusableRoot {
        tree: tree.clone(),
        geometry_planes: geometry_planes.clone(),
        reuse_data: BspNodeReuseData {
            quad_extents: old_root.reuse_data.quad_extents.clone(),
            indexes: old_root.reuse_data.indexes.clone(),
            index_count: old_root.reuse_data.index_count,
            max_index: old_root.reuse_data.max_index,
        },
    });

    Ok(Some(BspBuildOutput {
        tree,
        reusable_root: Some(reusable_root),
    }))
}

pub(super) fn create_bsp_reusable_root(
    tree: &NativeBspTree,
    geometry_planes: &NativeGeometryPlanes,
    quads: &[QuadInfo],
    indexes: &[i32],
    prepare_node_reuse: bool,
) -> Option<Box<NativeBspReusableRoot>> {
    if !prepare_node_reuse {
        return None;
    }

    let reuse_data = prepare_bsp_reuse_data(quads, indexes)?;
    Some(Box::new(NativeBspReusableRoot {
        tree: tree.clone(),
        geometry_planes: geometry_planes.clone(),
        reuse_data,
    }))
}

pub(super) fn create_bsp_build_result() -> NativeBspBuildResult {
    NativeBspBuildResult {
        geometry_planes: Some(Box::new(NativeGeometryPlanes::new())),
        tree: None,
        owned_split_quads: Vec::new(),
    }
}

pub(super) fn build_bsp_tree_from_topo_records_with_reuse(
    records: &[TranslucentTopoQuadRecord],
    geometry_planes: &mut NativeGeometryPlanes,
    old_root_handle: u64,
    prepare_node_reuse: bool,
) -> Result<BspBuildOutput, i32> {
    let quads = build_topo_quad_infos(records)?;
    let indexes = (0..quads.len())
        .map(|index| i32::try_from(index).map_err(|_| ERR_CAPACITY))
        .collect::<Result<Vec<_>, _>>()?;

    if let Some(output) = try_reuse_bsp_root(&quads, &indexes, old_root_handle, geometry_planes)? {
        return Ok(output);
    }

    let mut tree = create_bsp_tree();
    let root = build_bsp_node(&mut tree, geometry_planes, &quads, &indexes, -1)?;
    tree.root = root;
    tree.index_quad_count = quads.len();
    let reusable_root =
        create_bsp_reusable_root(&tree, geometry_planes, &quads, &indexes, prepare_node_reuse);

    Ok(BspBuildOutput {
        tree,
        reusable_root,
    })
}

pub(super) fn build_bsp_node(
    tree: &mut NativeBspTree,
    geometry_planes: &mut NativeGeometryPlanes,
    quads: &[QuadInfo],
    indexes: &[i32],
    depth: i32,
) -> Result<i32, i32> {
    if indexes.is_empty() {
        return Ok(-1);
    }
    if indexes.len() == 1 {
        return add_bsp_node(tree, BspNode::LeafSingle { quad: indexes[0] });
    }
    if indexes.len() == 2 {
        let quad_a = quad_at(quads, indexes[0])?;
        let quad_b = quad_at(quads, indexes[1])?;
        if bsp_double_leaf_possible(quad_a, quad_b, false) {
            return add_bsp_node(
                tree,
                BspNode::LeafDouble {
                    quad_a: indexes[0],
                    quad_b: indexes[1],
                },
            );
        }
    }

    build_partitioned_bsp_node(tree, geometry_planes, quads, indexes, depth + 1)
}

pub(super) fn build_partitioned_bsp_node(
    tree: &mut NativeBspTree,
    geometry_planes: &mut NativeGeometryPlanes,
    quads: &[QuadInfo],
    indexes: &[i32],
    depth: i32,
) -> Result<i32, i32> {
    for axis_count in 0..3 {
        let axis = ((axis_count + depth + 1) % 3) as usize;
        let opposite_direction = axis + 3;
        let mut aligned_facing_bitmap = 0i32;
        let mut only_interval_side = true;
        let mut points = Vec::with_capacity(indexes.len() * 2);

        for &quad_index in indexes {
            let quad = quad_at(quads, quad_index)?;
            let pos_extent = quad.extents[axis];
            let neg_extent = quad.extents[opposite_direction];
            if pos_extent == neg_extent {
                points.push(bsp_interval_point(pos_extent, quad_index, 1));
            } else {
                points.push(bsp_interval_point(pos_extent, quad_index, 0));
                points.push(bsp_interval_point(neg_extent, quad_index, 2));
                only_interval_side = false;
            }
            aligned_facing_bitmap |= 1 << quad.facing;
        }

        if aligned_facing_bitmap & (1 << FACING_UNASSIGNED) == 0 {
            let normal_count = aligned_facing_bitmap.count_ones();
            if normal_count == 1
                || (normal_count == 2 && bitmap_is_opposing_aligned(aligned_facing_bitmap))
            {
                return if only_interval_side {
                    build_bsp_snr_leaf_from_points(tree, quads, &mut points)
                } else {
                    build_bsp_snr_leaf_from_quads(tree, quads, indexes)
                };
            }
        }

        points.sort_by(|a, b| {
            a.distance_key
                .cmp(&b.distance_key)
                .then_with(|| a.point_type.cmp(&b.point_type))
                .then_with(|| a.quad_index.cmp(&b.quad_index))
        });

        let mut distance = f32::NAN;
        let mut before: Vec<i32> = Vec::new();
        let mut on: Vec<i32> = Vec::new();
        let mut has_before = false;
        let mut has_on = false;
        let mut thickness = 0i32;
        let mut partitions = Vec::new();

        for point in &points {
            match point.point_type {
                2 => {
                    if thickness == 0 && (has_before || has_on) {
                        partitions.push(BspBuildPartition {
                            distance,
                            before: take_if_present(&mut before, &mut has_before),
                            on: take_if_present(&mut on, &mut has_on),
                        });
                        distance = f32::NAN;
                    }

                    thickness += 1;
                    if has_on {
                        if distance.is_nan() {
                            return Err(ERR_INVALID_ARGUMENT);
                        }
                        partitions.push(BspBuildPartition {
                            distance,
                            before: take_if_present(&mut before, &mut has_before),
                            on: take_if_present(&mut on, &mut has_on),
                        });
                        distance = f32::NAN;
                    }
                    before.push(point.quad_index);
                    has_before = true;
                }
                0 => {
                    thickness -= 1;
                    if !has_on {
                        distance = point.distance;
                    }
                }
                1 => {
                    if thickness == 0 {
                        if !has_on {
                            on.clear();
                            has_on = true;
                            distance = point.distance;
                        } else if distance != point.distance {
                            partitions.push(BspBuildPartition {
                                distance,
                                before: take_if_present(&mut before, &mut has_before),
                                on: take_if_present(&mut on, &mut has_on),
                            });
                            distance = point.distance;
                            on.clear();
                            has_on = true;
                        }
                        on.push(point.quad_index);
                    } else {
                        before.push(point.quad_index);
                        has_before = true;
                    }
                }
                _ => return Err(ERR_INVALID_ARGUMENT),
            }
        }

        if has_before && before.len() == indexes.len() {
            continue;
        }

        let ends_with_plane = has_on;
        if has_before || has_on {
            partitions.push(BspBuildPartition {
                distance: if ends_with_plane { distance } else { f32::NAN },
                before: take_if_present(&mut before, &mut has_before),
                on: take_if_present(&mut on, &mut has_on),
            });
        }

        if partitions.len() <= 2 {
            let outside = if partitions.len() == 2 {
                Some(&partitions[1])
            } else {
                None
            };
            if outside.is_none() || !ends_with_plane {
                return build_bsp_binary_from_partitions(
                    tree,
                    geometry_planes,
                    quads,
                    depth,
                    &partitions[0],
                    outside,
                    axis as i32,
                );
            }
        }

        return build_bsp_multi_partition(
            tree,
            geometry_planes,
            quads,
            depth,
            &partitions,
            axis as i32,
            ends_with_plane,
        );
    }

    if let Some(node) =
        build_bsp_intersection_fallback(tree, geometry_planes, quads, indexes, depth)?
    {
        return Ok(node);
    }
    build_bsp_topo_multi_leaf(tree, quads, indexes, false)?.ok_or(SORT_FAILED)
}

pub(super) fn bsp_interval_point(
    distance: f32,
    quad_index: i32,
    point_type: i32,
) -> BspBuildIntervalPoint {
    BspBuildIntervalPoint {
        distance_key: float_to_comparable_int(distance),
        distance,
        quad_index,
        point_type,
    }
}

pub(super) fn take_if_present(values: &mut Vec<i32>, present: &mut bool) -> Vec<i32> {
    if *present {
        *present = false;
        std::mem::take(values)
    } else {
        Vec::new()
    }
}

pub(super) fn build_bsp_binary_from_partitions(
    tree: &mut NativeBspTree,
    geometry_planes: &mut NativeGeometryPlanes,
    quads: &[QuadInfo],
    depth: i32,
    inside: &BspBuildPartition,
    outside: Option<&BspBuildPartition>,
    axis: i32,
) -> Result<i32, i32> {
    geometry_planes.add_double_sided_aligned_plane(axis, inside.distance)?;

    let inside_node = if inside.before.is_empty() {
        -1
    } else {
        build_bsp_node(tree, geometry_planes, quads, &inside.before, depth)?
    };
    let outside_node = if let Some(outside) = outside {
        build_bsp_node(tree, geometry_planes, quads, &outside.before, depth)?
    } else {
        -1
    };
    let mut on_plane = inside.on.clone();
    on_plane.sort_unstable();

    let normal = accurate_aligned_normal(axis);
    add_bsp_node(
        tree,
        BspNode::Binary {
            remap: BspRemap::none(),
            normal: [normal.0, normal.1, normal.2],
            distance: inside.distance,
            inside: inside_node,
            outside: outside_node,
            on_plane,
        },
    )
}

pub(super) fn build_bsp_multi_partition(
    tree: &mut NativeBspTree,
    geometry_planes: &mut NativeGeometryPlanes,
    quads: &[QuadInfo],
    depth: i32,
    partitions: &[BspBuildPartition],
    axis: i32,
    ends_with_plane: bool,
) -> Result<i32, i32> {
    let plane_count = if ends_with_plane {
        partitions.len()
    } else {
        partitions.len().saturating_sub(1)
    };
    let mut plane_distances = Vec::with_capacity(plane_count);
    let mut partition_nodes = vec![-1; plane_count + 1];
    let mut on_plane_quads = Vec::with_capacity(plane_count);

    for (index, partition) in partitions.iter().enumerate() {
        if index < plane_count {
            if partition.distance.is_nan() {
                return Err(ERR_INVALID_ARGUMENT);
            }
            geometry_planes.add_double_sided_aligned_plane(axis, partition.distance)?;
            plane_distances.push(partition.distance);
            let mut on_plane = partition.on.clone();
            on_plane.sort_unstable();
            on_plane_quads.push(on_plane);
        }

        if !partition.before.is_empty() && index < partition_nodes.len() {
            partition_nodes[index] =
                build_bsp_node(tree, geometry_planes, quads, &partition.before, depth)?;
        }
    }

    let normal = accurate_aligned_normal(axis);
    add_bsp_node(
        tree,
        BspNode::MultiPartition {
            remap: BspRemap::none(),
            normal: [normal.0, normal.1, normal.2],
            plane_distances,
            partitions: partition_nodes,
            on_plane_quads,
        },
    )
}

pub(super) fn build_bsp_snr_leaf_from_quads(
    tree: &mut NativeBspTree,
    quads: &[QuadInfo],
    indexes: &[i32],
) -> Result<i32, i32> {
    let mut sorted = indexes.to_vec();
    sorted.sort_by_key(|index| {
        quad_at(quads, *index)
            .map(|quad| float_to_comparable_int(quad.accurate_dot_product))
            .unwrap_or(i32::MAX)
    });
    add_bsp_node(tree, BspNode::LeafMulti { quads: sorted })
}

pub(super) fn build_bsp_snr_leaf_from_points(
    tree: &mut NativeBspTree,
    quads: &[QuadInfo],
    points: &mut [BspBuildIntervalPoint],
) -> Result<i32, i32> {
    points.sort_by(|a, b| {
        a.distance_key
            .cmp(&b.distance_key)
            .then_with(|| a.point_type.cmp(&b.point_type))
            .then_with(|| a.quad_index.cmp(&b.quad_index))
    });

    let mut sorted = vec![0; points.len()];
    let mut forwards = 0usize;
    let mut backwards = sorted.len();
    for point in points {
        let quad = quad_at(quads, point.quad_index)?;
        if facing_sign(quad.facing) == 1 {
            sorted[forwards] = point.quad_index;
            forwards += 1;
        } else {
            backwards -= 1;
            sorted[backwards] = point.quad_index;
        }
    }
    add_bsp_node(tree, BspNode::LeafMulti { quads: sorted })
}

pub(super) fn build_bsp_topo_multi_leaf(
    tree: &mut NativeBspTree,
    quads: &[QuadInfo],
    indexes: &[i32],
    fail_on_intersection: bool,
) -> Result<Option<i32>, i32> {
    if indexes.len()
        > STATIC_TOPO_SORT_ATTEMPT_LIMITS[STATIC_TOPO_SORT_ATTEMPT_LIMITS.len() - 1] as usize
    {
        return Ok(None);
    }

    let active = indexes
        .iter()
        .map(|index| quad_at(quads, *index).cloned())
        .collect::<Result<Vec<_>, _>>()?;
    let Some(sorted_local) = topo_graph_sort(&active, fail_on_intersection) else {
        return Ok(None);
    };
    let mut sorted = Vec::with_capacity(sorted_local.len());
    for local in sorted_local {
        let local = usize::try_from(local).map_err(|_| ERR_INVALID_ARGUMENT)?;
        sorted.push(*indexes.get(local).ok_or(ERR_INVALID_ARGUMENT)?);
    }
    add_bsp_node(tree, BspNode::LeafMulti { quads: sorted }).map(Some)
}

pub(super) fn build_bsp_intersection_fallback(
    tree: &mut NativeBspTree,
    geometry_planes: &mut NativeGeometryPlanes,
    quads: &[QuadInfo],
    indexes: &[i32],
    depth: i32,
) -> Result<Option<i32>, i32> {
    let primary_threshold = (indexes.len() / 2).clamp(2, 4);
    let mut counts = vec![0usize; indexes.len()];
    let mut primary = vec![false; indexes.len()];

    for a in 0..indexes.len() {
        for b in (a + 1)..indexes.len() {
            if extents_intersect(
                &quad_at(quads, indexes[a])?.extents,
                &quad_at(quads, indexes[b])?.extents,
            ) {
                counts[a] += 1;
                counts[b] += 1;
                if counts[a] >= primary_threshold {
                    primary[a] = true;
                }
                if counts[b] >= primary_threshold {
                    primary[b] = true;
                }
            }
        }
    }

    let primary_count = primary.iter().filter(|value| **value).count();
    if primary_count == 0 {
        return Ok(None);
    }
    if primary_count == indexes.len() {
        let mut sorted = indexes.to_vec();
        sorted.sort_unstable();
        return add_bsp_node(tree, BspNode::LeafMulti { quads: sorted }).map(Some);
    }

    let mut non_primary = Vec::with_capacity(indexes.len() - primary_count);
    let mut primary_indexes = Vec::with_capacity(primary_count);
    for (local, &index) in indexes.iter().enumerate() {
        if primary[local] {
            primary_indexes.push(index);
        } else {
            non_primary.push(index);
        }
    }
    let first = build_bsp_node(tree, geometry_planes, quads, &non_primary, depth)?;
    let second = build_bsp_node(tree, geometry_planes, quads, &primary_indexes, depth)?;
    add_bsp_node(
        tree,
        BspNode::FixedDouble {
            remap: BspRemap::none(),
            first,
            second,
        },
    )
    .map(Some)
}

pub(super) fn quad_at(quads: &[QuadInfo], index: i32) -> Result<&QuadInfo, i32> {
    if index < 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }
    quads.get(index as usize).ok_or(ERR_INVALID_ARGUMENT)
}

pub(super) unsafe fn build_bsp_tree_from_full_quad_handles_with_reuse(
    handles: &[u64],
    geometry_planes: &mut NativeGeometryPlanes,
    max_quad_count: usize,
    quantize_trigger_normals: bool,
    old_root_handle: u64,
    prepare_node_reuse: bool,
) -> Result<BspFullQuadBuildOutput, i32> {
    let mut quads = Vec::with_capacity(handles.len());
    for &handle in handles {
        if handle == 0 {
            return Err(ERR_NULL_POINTER);
        }
        quads.push((&*(handle as *const NativeFullTQuad)).info.clone());
    }

    let indexes = (0..handles.len())
        .map(|index| i32::try_from(index).map_err(|_| ERR_CAPACITY))
        .collect::<Result<Vec<_>, _>>()?;

    if let Some(output) = try_reuse_bsp_root(&quads, &indexes, old_root_handle, geometry_planes)? {
        return Ok(BspFullQuadBuildOutput {
            tree: output.tree,
            reusable_root: output.reusable_root,
            owned_split_quads: Vec::new(),
            updated_quad_handles: Vec::new(),
            mesh_quad_count: handles.len(),
            index_quad_count: handles.len(),
        });
    }

    let mut workspace = BspFullQuadWorkspace {
        tree: create_bsp_tree(),
        geometry_planes,
        quads,
        handles: handles.to_vec(),
        owned_split_quads: Vec::new(),
        updated_quad_handles: Vec::new(),
        mesh_quad_count: handles.len(),
        index_quad_count: handles.len(),
        max_quad_count: max_quad_count.max(handles.len()),
        quantize_trigger_normals,
    };

    let root = build_full_bsp_node(&mut workspace, &indexes, -1)?;
    workspace.tree.root = root;
    workspace.tree.index_quad_count = workspace.index_quad_count;
    let reusable_root = create_bsp_reusable_root(
        &workspace.tree,
        workspace.geometry_planes,
        &workspace.quads,
        &indexes,
        prepare_node_reuse,
    );

    Ok(BspFullQuadBuildOutput {
        tree: workspace.tree,
        reusable_root,
        owned_split_quads: workspace.owned_split_quads,
        updated_quad_handles: workspace.updated_quad_handles,
        mesh_quad_count: workspace.mesh_quad_count,
        index_quad_count: workspace.index_quad_count,
    })
}

pub(super) fn build_full_bsp_node(
    workspace: &mut BspFullQuadWorkspace<'_>,
    indexes: &[i32],
    depth: i32,
) -> Result<i32, i32> {
    if indexes.is_empty() {
        return Ok(-1);
    }
    if indexes.len() == 1 {
        return add_bsp_node(
            &mut workspace.tree,
            BspNode::LeafSingle { quad: indexes[0] },
        );
    }
    if indexes.len() == 2 {
        let quad_a = workspace.quad(indexes[0])?;
        let quad_b = workspace.quad(indexes[1])?;
        if bsp_double_leaf_possible(quad_a, quad_b, workspace.can_split_quads()) {
            return add_bsp_node(
                &mut workspace.tree,
                BspNode::LeafDouble {
                    quad_a: indexes[0],
                    quad_b: indexes[1],
                },
            );
        }
    }

    build_full_partitioned_bsp_node(workspace, indexes, depth + 1)
}

pub(super) fn build_full_partitioned_bsp_node(
    workspace: &mut BspFullQuadWorkspace<'_>,
    indexes: &[i32],
    depth: i32,
) -> Result<i32, i32> {
    let mut best_splitting_group = Vec::new();

    for axis_count in 0..3 {
        let axis = ((axis_count + depth + 1) % 3) as usize;
        let opposite_direction = axis + 3;
        let mut aligned_facing_bitmap = 0i32;
        let mut only_interval_side = true;
        let mut points = Vec::with_capacity(indexes.len() * 2);

        for &quad_index in indexes {
            let quad = workspace.quad(quad_index)?;
            let pos_extent = quad.extents[axis];
            let neg_extent = quad.extents[opposite_direction];
            if pos_extent == neg_extent {
                points.push(bsp_interval_point(pos_extent, quad_index, 1));
            } else {
                points.push(bsp_interval_point(pos_extent, quad_index, 0));
                points.push(bsp_interval_point(neg_extent, quad_index, 2));
                only_interval_side = false;
            }
            aligned_facing_bitmap |= 1 << quad.facing;
        }

        if aligned_facing_bitmap & (1 << FACING_UNASSIGNED) == 0 {
            let normal_count = aligned_facing_bitmap.count_ones();
            if normal_count == 1
                || (normal_count == 2 && bitmap_is_opposing_aligned(aligned_facing_bitmap))
            {
                return if only_interval_side {
                    build_full_bsp_snr_leaf_from_points(workspace, &mut points)
                } else {
                    build_full_bsp_snr_leaf_from_quads(workspace, indexes)
                };
            }
        }

        points.sort_by(|a, b| {
            a.distance_key
                .cmp(&b.distance_key)
                .then_with(|| a.point_type.cmp(&b.point_type))
                .then_with(|| a.quad_index.cmp(&b.quad_index))
        });

        let mut distance = f32::NAN;
        let mut before = Vec::new();
        let mut on = Vec::new();
        let mut has_before = false;
        let mut has_on = false;
        let mut thickness = 0i32;
        let mut partitions = Vec::new();
        let mut splitting_group = Vec::new();
        let mut split_distance = f32::NAN;

        for point in &points {
            match point.point_type {
                2 => {
                    if thickness == 0 && (has_before || has_on) {
                        partitions.push(BspBuildPartition {
                            distance,
                            before: take_if_present(&mut before, &mut has_before),
                            on: take_if_present(&mut on, &mut has_on),
                        });
                        distance = f32::NAN;
                    }
                    thickness += 1;
                    if has_on {
                        if distance.is_nan() {
                            return Err(ERR_INVALID_ARGUMENT);
                        }
                        partitions.push(BspBuildPartition {
                            distance,
                            before: take_if_present(&mut before, &mut has_before),
                            on: take_if_present(&mut on, &mut has_on),
                        });
                        distance = f32::NAN;
                    }
                    before.push(point.quad_index);
                    has_before = true;
                }
                0 => {
                    thickness -= 1;
                    if !has_on {
                        distance = point.distance;
                    }
                }
                1 => {
                    if thickness == 0 {
                        if !has_on {
                            on.clear();
                            has_on = true;
                            distance = point.distance;
                        } else if distance != point.distance {
                            partitions.push(BspBuildPartition {
                                distance,
                                before: take_if_present(&mut before, &mut has_before),
                                on: take_if_present(&mut on, &mut has_on),
                            });
                            distance = point.distance;
                            on.clear();
                            has_on = true;
                        }
                        on.push(point.quad_index);
                    } else {
                        before.push(point.quad_index);
                        has_before = true;
                        if workspace.can_split_quads() {
                            if point.distance == split_distance || split_distance.is_nan() {
                                splitting_group.push(point.quad_index);
                            } else {
                                flush_best_splitting_group(
                                    &mut splitting_group,
                                    &mut best_splitting_group,
                                    axis,
                                );
                            }
                            split_distance = point.distance;
                        }
                    }
                }
                _ => return Err(ERR_INVALID_ARGUMENT),
            }
        }

        if workspace.can_split_quads() {
            flush_best_splitting_group(&mut splitting_group, &mut best_splitting_group, axis);
        }

        if has_before && before.len() == indexes.len() {
            continue;
        }

        let ends_with_plane = has_on;
        if has_before || has_on {
            partitions.push(BspBuildPartition {
                distance: if ends_with_plane { distance } else { f32::NAN },
                before: take_if_present(&mut before, &mut has_before),
                on: take_if_present(&mut on, &mut has_on),
            });
        }

        if partitions.len() <= 2 {
            let outside = if partitions.len() == 2 {
                Some(&partitions[1])
            } else {
                None
            };
            if outside.is_none() || !ends_with_plane {
                return build_full_bsp_binary_from_partitions(
                    workspace,
                    depth,
                    &partitions[0],
                    outside,
                    axis as i32,
                );
            }
        }

        return build_full_bsp_multi_partition(
            workspace,
            depth,
            &partitions,
            axis as i32,
            ends_with_plane,
        );
    }

    if workspace.can_split_quads() {
        if let Some(node) = build_full_bsp_topo_multi_leaf(workspace, indexes, true)? {
            return Ok(node);
        }
        return handle_full_unsortable_by_splitting(
            workspace,
            indexes,
            depth,
            best_splitting_group,
        );
    }

    if let Some(node) = build_full_bsp_intersection_fallback(workspace, indexes, depth)? {
        return Ok(node);
    }
    build_full_bsp_topo_multi_leaf(workspace, indexes, false)?.ok_or(SORT_FAILED)
}

pub(super) fn flush_best_splitting_group(current: &mut Vec<i32>, best: &mut Vec<i32>, axis: usize) {
    if current.len() > best.len() || (current.len() == best.len() && axis == 1) {
        best.clear();
        best.extend_from_slice(current);
    }
    current.clear();
}

pub(super) fn build_full_bsp_binary_from_partitions(
    workspace: &mut BspFullQuadWorkspace<'_>,
    depth: i32,
    inside: &BspBuildPartition,
    outside: Option<&BspBuildPartition>,
    axis: i32,
) -> Result<i32, i32> {
    workspace
        .geometry_planes
        .add_double_sided_aligned_plane(axis, inside.distance)?;
    let inside_node = if inside.before.is_empty() {
        -1
    } else {
        build_full_bsp_node(workspace, &inside.before, depth)?
    };
    let outside_node = if let Some(outside) = outside {
        build_full_bsp_node(workspace, &outside.before, depth)?
    } else {
        -1
    };
    let mut on_plane = inside.on.clone();
    on_plane.sort_unstable();
    let normal = accurate_aligned_normal(axis);
    add_bsp_node(
        &mut workspace.tree,
        BspNode::Binary {
            remap: BspRemap::none(),
            normal: [normal.0, normal.1, normal.2],
            distance: inside.distance,
            inside: inside_node,
            outside: outside_node,
            on_plane,
        },
    )
}

pub(super) fn build_full_bsp_binary_from_parts(
    workspace: &mut BspFullQuadWorkspace<'_>,
    depth: i32,
    inside: &[i32],
    outside: &[i32],
    on_plane: &[i32],
    axis: i32,
    normal: [f32; 3],
    distance: f32,
) -> Result<i32, i32> {
    if axis < 0 {
        workspace
            .geometry_planes
            .add_double_sided_unaligned_plane(normal, distance)?;
    } else {
        workspace
            .geometry_planes
            .add_double_sided_aligned_plane(axis, distance.abs())?;
    }

    let inside_node = if inside.is_empty() {
        -1
    } else {
        build_full_bsp_node(workspace, inside, depth)?
    };
    let outside_node = if outside.is_empty() {
        -1
    } else {
        build_full_bsp_node(workspace, outside, depth)?
    };
    let mut on_plane = on_plane.to_vec();
    on_plane.sort_unstable();

    add_bsp_node(
        &mut workspace.tree,
        BspNode::Binary {
            remap: BspRemap::none(),
            normal,
            distance,
            inside: inside_node,
            outside: outside_node,
            on_plane,
        },
    )
}

pub(super) fn build_full_bsp_multi_partition(
    workspace: &mut BspFullQuadWorkspace<'_>,
    depth: i32,
    partitions: &[BspBuildPartition],
    axis: i32,
    ends_with_plane: bool,
) -> Result<i32, i32> {
    let plane_count = if ends_with_plane {
        partitions.len()
    } else {
        partitions.len().saturating_sub(1)
    };
    let mut plane_distances = Vec::with_capacity(plane_count);
    let mut partition_nodes = vec![-1; plane_count + 1];
    let mut on_plane_quads = Vec::with_capacity(plane_count);

    for (index, partition) in partitions.iter().enumerate() {
        if index < plane_count {
            if partition.distance.is_nan() {
                return Err(ERR_INVALID_ARGUMENT);
            }
            workspace
                .geometry_planes
                .add_double_sided_aligned_plane(axis, partition.distance)?;
            plane_distances.push(partition.distance);
            let mut on_plane = partition.on.clone();
            on_plane.sort_unstable();
            on_plane_quads.push(on_plane);
        }
        if !partition.before.is_empty() && index < partition_nodes.len() {
            partition_nodes[index] = build_full_bsp_node(workspace, &partition.before, depth)?;
        }
    }

    let normal = accurate_aligned_normal(axis);
    add_bsp_node(
        &mut workspace.tree,
        BspNode::MultiPartition {
            remap: BspRemap::none(),
            normal: [normal.0, normal.1, normal.2],
            plane_distances,
            partitions: partition_nodes,
            on_plane_quads,
        },
    )
}

pub(super) fn build_full_bsp_snr_leaf_from_quads(
    workspace: &mut BspFullQuadWorkspace<'_>,
    indexes: &[i32],
) -> Result<i32, i32> {
    let mut sorted = indexes.to_vec();
    sorted.sort_by_key(|index| {
        workspace
            .quad(*index)
            .map(|quad| float_to_comparable_int(quad.accurate_dot_product))
            .unwrap_or(i32::MAX)
    });
    add_bsp_node(&mut workspace.tree, BspNode::LeafMulti { quads: sorted })
}

pub(super) fn build_full_bsp_snr_leaf_from_points(
    workspace: &mut BspFullQuadWorkspace<'_>,
    points: &mut [BspBuildIntervalPoint],
) -> Result<i32, i32> {
    points.sort_by(|a, b| {
        a.distance_key
            .cmp(&b.distance_key)
            .then_with(|| a.point_type.cmp(&b.point_type))
            .then_with(|| a.quad_index.cmp(&b.quad_index))
    });

    let mut sorted = vec![0; points.len()];
    let mut forwards = 0usize;
    let mut backwards = sorted.len();
    for point in points {
        let quad = workspace.quad(point.quad_index)?;
        if facing_sign(quad.facing) == 1 {
            sorted[forwards] = point.quad_index;
            forwards += 1;
        } else {
            backwards -= 1;
            sorted[backwards] = point.quad_index;
        }
    }
    add_bsp_node(&mut workspace.tree, BspNode::LeafMulti { quads: sorted })
}

pub(super) fn build_full_bsp_topo_multi_leaf(
    workspace: &mut BspFullQuadWorkspace<'_>,
    indexes: &[i32],
    fail_on_intersection: bool,
) -> Result<Option<i32>, i32> {
    if indexes.len()
        > STATIC_TOPO_SORT_ATTEMPT_LIMITS[STATIC_TOPO_SORT_ATTEMPT_LIMITS.len() - 1] as usize
    {
        return Ok(None);
    }
    let active = indexes
        .iter()
        .map(|index| workspace.quad(*index).cloned())
        .collect::<Result<Vec<_>, _>>()?;
    let Some(sorted_local) = topo_graph_sort(&active, fail_on_intersection) else {
        return Ok(None);
    };
    let mut sorted = Vec::with_capacity(sorted_local.len());
    for local in sorted_local {
        let local = usize::try_from(local).map_err(|_| ERR_INVALID_ARGUMENT)?;
        sorted.push(*indexes.get(local).ok_or(ERR_INVALID_ARGUMENT)?);
    }
    add_bsp_node(&mut workspace.tree, BspNode::LeafMulti { quads: sorted }).map(Some)
}

pub(super) fn build_full_bsp_intersection_fallback(
    workspace: &mut BspFullQuadWorkspace<'_>,
    indexes: &[i32],
    depth: i32,
) -> Result<Option<i32>, i32> {
    let primary_threshold = (indexes.len() / 2).clamp(2, 4);
    let mut counts = vec![0usize; indexes.len()];
    let mut primary = vec![false; indexes.len()];

    for a in 0..indexes.len() {
        for b in (a + 1)..indexes.len() {
            if extents_intersect(
                &workspace.quad(indexes[a])?.extents,
                &workspace.quad(indexes[b])?.extents,
            ) {
                counts[a] += 1;
                counts[b] += 1;
                if counts[a] >= primary_threshold {
                    primary[a] = true;
                }
                if counts[b] >= primary_threshold {
                    primary[b] = true;
                }
            }
        }
    }

    let primary_count = primary.iter().filter(|value| **value).count();
    if primary_count == 0 {
        return Ok(None);
    }
    if primary_count == indexes.len() {
        let mut sorted = indexes.to_vec();
        sorted.sort_unstable();
        return add_bsp_node(&mut workspace.tree, BspNode::LeafMulti { quads: sorted }).map(Some);
    }

    let mut non_primary = Vec::with_capacity(indexes.len() - primary_count);
    let mut primary_indexes = Vec::with_capacity(primary_count);
    for (local, &index) in indexes.iter().enumerate() {
        if primary[local] {
            primary_indexes.push(index);
        } else {
            non_primary.push(index);
        }
    }
    let first = build_full_bsp_node(workspace, &non_primary, depth)?;
    let second = build_full_bsp_node(workspace, &primary_indexes, depth)?;
    add_bsp_node(
        &mut workspace.tree,
        BspNode::FixedDouble {
            remap: BspRemap::none(),
            first,
            second,
        },
    )
    .map(Some)
}

pub(super) fn handle_full_unsortable_by_splitting(
    workspace: &mut BspFullQuadWorkspace<'_>,
    indexes: &[i32],
    depth: i32,
    mut splitting_group: Vec<i32>,
) -> Result<i32, i32> {
    if splitting_group.is_empty() {
        let representative = *indexes.first().ok_or(ERR_INVALID_ARGUMENT)?;
        splitting_group.push(representative);
    }
    let representative_index = splitting_group[0];
    let representative = unsafe { workspace.full_quad_mut(representative_index)? };
    let representative_facing = representative.info.facing;
    let split_plane = full_quad_very_accurate_normal(representative);
    let split_distance = representative.info.accurate_dot_product;
    let initial_splitting_group_size = splitting_group.len();

    let mut inside = Vec::new();
    let mut outside = Vec::new();

    for &candidate_index in indexes {
        if splitting_group[..initial_splitting_group_size].contains(&candidate_index) {
            continue;
        }

        let candidate = unsafe { workspace.full_quad_mut(candidate_index)? };
        let candidate_facing = candidate.info.facing;
        let same_split_plane = candidate_facing == representative_facing
            && candidate.info.accurate_dot_product == split_distance
            && (representative_facing != FACING_UNASSIGNED
                || full_quad_very_accurate_normal(candidate) == split_plane);
        if same_split_plane {
            splitting_group.push(candidate_index);
            continue;
        }

        split_full_candidate(
            workspace,
            &mut splitting_group,
            candidate_index,
            split_plane,
            split_distance,
            &mut outside,
            &mut inside,
        )?;
    }

    let (facing, normal, distance) = if workspace.quantize_trigger_normals {
        let representative = unsafe { workspace.full_quad_mut(representative_index)? };
        quantized_full_quad_plane(representative)
    } else {
        (representative_facing, split_plane, split_distance)
    };
    let axis = if is_aligned(facing) { facing % 3 } else { -1 };

    build_full_bsp_binary_from_parts(
        workspace,
        depth,
        &inside,
        &outside,
        &splitting_group,
        axis,
        normal,
        distance,
    )
}

pub(super) fn split_full_candidate(
    workspace: &mut BspFullQuadWorkspace<'_>,
    splitting_group: &mut Vec<i32>,
    candidate_index: i32,
    split_plane: [f32; 3],
    split_distance: f32,
    outside: &mut Vec<i32>,
    inside: &mut Vec<i32>,
) -> Result<(), i32> {
    let handle = workspace.handle(candidate_index)?;
    let source = unsafe { &*(handle as *const NativeFullTQuad) };
    let unique_vertex_map = (!source.same_vertex_map) & 0b1111;
    let unique_vertices = unique_vertex_map.count_ones();
    if unique_vertices < 3 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let (inside_map_unmasked, on_plane_map_unmasked) =
        full_quad_classify(source, tuple3(split_plane), split_distance);
    let mut inside_map = inside_map_unmasked & unique_vertex_map;
    let on_plane_map = on_plane_map_unmasked & unique_vertex_map;
    if on_plane_map == unique_vertex_map {
        splitting_group.push(candidate_index);
        return Ok(());
    }
    if inside_map == 0 {
        outside.push(candidate_index);
        return Ok(());
    }
    if (inside_map | on_plane_map) == unique_vertex_map {
        inside.push(candidate_index);
        return Ok(());
    }

    let on_plane_count = on_plane_map.count_ones();
    let mut inside_count = inside_map.count_ones();
    if !workspace.can_split_quads() {
        let outside_count = 4 - inside_count - on_plane_count;
        if on_plane_count >= inside_count && on_plane_count >= outside_count {
            splitting_group.push(candidate_index);
        } else if inside_count >= outside_count {
            inside.push(candidate_index);
        } else {
            outside.push(candidate_index);
        }
        return Ok(());
    }

    let mut outside_quad = Box::new(source.clone());
    let mut second_outside_quad: Option<Box<NativeFullTQuad>> = None;
    let mut second_inside_quad: Option<Box<NativeFullTQuad>> = None;

    if unique_vertices == 3 {
        let same_vertex_map = source.same_vertex_map;
        if on_plane_count == 1 {
            let mut duplicate_index = -1;
            let mut duplicate_is_inside = false;
            if (on_plane_map_unmasked & same_vertex_map) == 0 {
                duplicate_is_inside = (same_vertex_map & inside_map_unmasked) != 0;
                duplicate_index = same_vertex_map.trailing_zeros() as i32;
            }

            let inside_index = inside_map.trailing_zeros() as usize;
            let outside_index =
                (!(inside_map | on_plane_map) & unique_vertex_map).trailing_zeros() as usize;
            let inside_quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
            full_quad_split_triangle_vertex(
                inside_index,
                outside_index,
                duplicate_index,
                duplicate_is_inside,
                inside_quad,
                &mut outside_quad,
                tuple3(split_plane),
                split_distance,
            )?;
        } else if inside_map_unmasked.count_ones() == 2 {
            let inside_quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
            full_quad_split_even(
                inside_map_unmasked,
                inside_quad,
                &mut outside_quad,
                tuple3(split_plane),
                split_distance,
            )?;
        } else if inside_count == 1 {
            let corner_index = inside_map.trailing_zeros() as usize;
            let inside_quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
            full_quad_split_triangle_corner(
                corner_index,
                inside_quad,
                &mut outside_quad,
                tuple3(split_plane),
                split_distance,
            )?;
        } else {
            let corner_index = (!inside_map_unmasked).trailing_zeros() as usize;
            let inside_quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
            full_quad_split_triangle_corner(
                corner_index,
                &mut outside_quad,
                inside_quad,
                tuple3(split_plane),
                split_distance,
            )?;
        }
    } else {
        if on_plane_count == 2 {
            if on_plane_map == 0b0101 {
                inside_map |= 0b0001;
            } else {
                inside_map |= 0b0010;
            }
            inside_count = 2;
        } else if on_plane_count == 1 && inside_count == 1 {
            inside_map |= on_plane_map;
            inside_count = 2;
        }

        if inside_count == 2 {
            let inside_quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
            full_quad_split_even(
                inside_map,
                inside_quad,
                &mut outside_quad,
                tuple3(split_plane),
                split_distance,
            )?;
        } else if inside_count == 3 {
            let corner_index = (!inside_map & 0b1111).trailing_zeros() as usize;
            let mut extra_inside = Box::new(source.clone());
            let inside_quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
            full_quad_split_odd(
                corner_index,
                &mut outside_quad,
                &mut extra_inside,
                inside_quad,
                tuple3(split_plane),
                split_distance,
            )?;
            second_inside_quad = Some(extra_inside);
        } else {
            let corner_index = inside_map.trailing_zeros() as usize;
            let mut extra_outside = Box::new(source.clone());
            let inside_quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
            full_quad_split_odd(
                corner_index,
                inside_quad,
                &mut extra_outside,
                &mut outside_quad,
                tuple3(split_plane),
                split_distance,
            )?;
            second_outside_quad = Some(extra_outside);
        }
    }

    if let Some(index) = workspace.update_full_quad(candidate_index)? {
        inside.push(index);
    }
    if let Some(index) = workspace.push_full_quad(outside_quad)? {
        outside.push(index);
    }
    if let Some(quad) = second_inside_quad {
        if let Some(index) = workspace.push_full_quad(quad)? {
            inside.push(index);
        }
    }
    if let Some(quad) = second_outside_quad {
        if let Some(index) = workspace.push_full_quad(quad)? {
            outside.push(index);
        }
    }

    Ok(())
}

pub(super) fn quantized_full_quad_plane(quad: &NativeFullTQuad) -> (i32, [f32; 3], f32) {
    if is_aligned(quad.info.facing) {
        let normal = accurate_aligned_normal(quad.info.facing);
        return (
            quad.info.facing,
            [normal.0, normal.1, normal.2],
            quad.info.accurate_dot_product,
        );
    }

    let normal_tuple = quantize_normal(unpack_normal(quad.info.packed_normal));
    let facing = aligned_facing_from_normal(normal_tuple).unwrap_or(FACING_UNASSIGNED);
    let distance = if is_aligned(facing) {
        quad.info.extents[facing as usize] * facing_sign(facing) as f32
    } else {
        dot(
            normal_tuple,
            quad.info.center.0,
            quad.info.center.1,
            quad.info.center.2,
        )
    };
    (
        facing,
        [normal_tuple.0, normal_tuple.1, normal_tuple.2],
        distance,
    )
}

pub(super) fn tuple3(value: [f32; 3]) -> (f32, f32, f32) {
    (value[0], value[1], value[2])
}

impl BspFullQuadWorkspace<'_> {
    fn can_split_quads(&self) -> bool {
        self.index_quad_count < self.max_quad_count
    }

    fn quad(&self, index: i32) -> Result<&QuadInfo, i32> {
        quad_at(&self.quads, index)
    }

    fn handle(&self, index: i32) -> Result<u64, i32> {
        if index < 0 {
            return Err(ERR_INVALID_ARGUMENT);
        }
        self.handles
            .get(index as usize)
            .copied()
            .ok_or(ERR_INVALID_ARGUMENT)
    }

    unsafe fn full_quad_mut(&self, index: i32) -> Result<&mut NativeFullTQuad, i32> {
        let handle = self.handle(index)?;
        if handle == 0 {
            return Err(ERR_NULL_POINTER);
        }
        Ok(&mut *(handle as *mut NativeFullTQuad))
    }

    fn mark_updated(&mut self, handle: u64) -> Result<(), i32> {
        if handle == 0 {
            return Err(ERR_NULL_POINTER);
        }
        let quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
        if !quad.has_updated_vertices {
            quad.has_updated_vertices = true;
            self.updated_quad_handles.push(handle);
        }
        Ok(())
    }

    fn update_full_quad(&mut self, index: i32) -> Result<Option<i32>, i32> {
        let handle = self.handle(index)?;
        let quad = unsafe { &mut *(handle as *mut NativeFullTQuad) };
        if full_quad_is_invalid(quad) {
            quad.write_to_index = -1;
            self.mark_updated(handle)?;
            self.index_quad_count = self.index_quad_count.saturating_sub(1);
            return Ok(None);
        }

        quad.write_to_index = index;
        let index_usize = index as usize;
        self.quads[index_usize] = quad.info.clone();
        self.mark_updated(handle)?;
        Ok(Some(index))
    }

    fn push_full_quad(&mut self, mut quad: Box<NativeFullTQuad>) -> Result<Option<i32>, i32> {
        if full_quad_is_invalid(&quad) {
            return Ok(None);
        }

        let index = i32::try_from(self.handles.len()).map_err(|_| ERR_CAPACITY)?;
        quad.write_to_index = index;
        let handle = (&mut *quad) as *mut NativeFullTQuad as u64;
        self.quads.push(quad.info.clone());
        self.handles.push(handle);
        self.owned_split_quads.push(quad);
        self.mesh_quad_count += 1;
        self.index_quad_count += 1;
        self.mark_updated(handle)?;
        Ok(Some(index))
    }
}

pub(super) fn full_quad_is_invalid(quad: &NativeFullTQuad) -> bool {
    quad.same_vertex_map.count_ones() > 1
}

pub(super) fn add_bsp_node(tree: &mut NativeBspTree, node: BspNode) -> Result<i32, i32> {
    let index = i32::try_from(tree.nodes.len()).map_err(|_| ERR_CAPACITY)?;
    tree.nodes.push(node);
    Ok(index)
}

pub(super) fn validate_bsp_node_index(
    tree: &NativeBspTree,
    node_index: i32,
) -> Result<Option<usize>, i32> {
    if node_index < 0 {
        return Ok(None);
    }

    let index = node_index as usize;
    if index >= tree.nodes.len() {
        return Err(ERR_INVALID_ARGUMENT);
    }
    Ok(Some(index))
}

pub(super) fn write_bsp_tree_index_buffer(
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

pub(super) fn collect_optional_bsp_node(
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

pub(super) fn collect_bsp_node(
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

pub(super) fn start_bsp_remap(
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

pub(super) fn write_bsp_indexes(
    tree: &NativeBspTree,
    state: &mut BspTraversalState,
    indexes: &[i32],
) -> Result<(), i32> {
    for index in indexes {
        write_bsp_index(tree, state, *index)?;
    }
    Ok(())
}

pub(super) fn write_bsp_index(
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

pub(super) fn node_remap(node: &BspNode) -> Option<&BspRemap> {
    match node {
        BspNode::FixedDouble { remap, .. }
        | BspNode::Binary { remap, .. }
        | BspNode::MultiPartition { remap, .. } => Some(remap),
        _ => None,
    }
}

pub(super) fn dot3(a: [f32; 3], b: [f32; 3]) -> f32 {
    a[0].mul_add(b[0], a[1].mul_add(b[1], a[2] * b[2]))
}
