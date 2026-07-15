use super::*;

fn vertex_record(facing: i32, z: f32) -> TranslucentQuadRecord {
    TranslucentQuadRecord {
        positions: [0.0, 0.0, z, 1.0, 0.0, z, 1.0, 1.0, z, 0.0, 1.0, z],
        facing,
        packed_normal: packed_aligned_normal(facing),
    }
}

fn full_quad_xy() -> NativeFullQuadBuffer {
    let mut buffer = NativeFullQuadBuffer::default();
    buffer.vertices[0] = NativeFullQuadVertex {
        x: 0.0,
        y: 0.0,
        z: 0.0,
        color: -1,
        ao: 1.0,
        light: 0x00f000f0,
        ..NativeFullQuadVertex::default()
    };
    buffer.vertices[1] = NativeFullQuadVertex {
        x: 1.0,
        y: 0.0,
        z: 0.0,
        color: -1,
        ao: 1.0,
        light: 0x00f000f0,
        ..NativeFullQuadVertex::default()
    };
    buffer.vertices[2] = NativeFullQuadVertex {
        x: 1.0,
        y: 1.0,
        z: 0.0,
        color: -1,
        ao: 1.0,
        light: 0x00f000f0,
        ..NativeFullQuadVertex::default()
    };
    buffer.vertices[3] = NativeFullQuadVertex {
        x: 0.0,
        y: 1.0,
        z: 0.0,
        color: -1,
        ao: 1.0,
        light: 0x00f000f0,
        ..NativeFullQuadVertex::default()
    };
    buffer.material_bits = 1;
    buffer
}

#[test]
fn record_layout_matches_java_stride() {
    assert_eq!(56, std::mem::size_of::<TranslucentQuadRecord>());
}

#[test]
fn topo_record_layout_matches_java_stride() {
    assert_eq!(84, std::mem::size_of::<TranslucentTopoQuadRecord>());
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
fn topo_graph_sort_topo_records_applies_active_remap() {
    let records = [
        topo_record(FACING_POS_Z, 1.0),
        topo_record(FACING_POS_Z, 0.0),
    ];
    let active_to_real_index = [42, 7];
    let order = topo_graph_sort_topo_records(&records, Some(&active_to_real_index), false)
        .unwrap()
        .unwrap();

    assert_eq!(vec![7, 42], order);
}

#[test]
fn bsp_double_leaf_possible_accepts_opposite_aligned_faces() {
    let quad_a = build_topo_quad_info(&topo_record(FACING_POS_Z, 1.0)).unwrap();
    let quad_b = build_topo_quad_info(&topo_record(FACING_NEG_Z, 0.0)).unwrap();

    assert!(bsp_double_leaf_possible(&quad_a, &quad_b, false));
}

#[test]
fn full_quad_bsp_split_tracks_generated_quad_ownership_and_updates() {
    let mut source = Box::new(
        create_full_quad(
            &full_quad_xy(),
            FACING_POS_Z,
            packed_aligned_normal(FACING_POS_Z),
        )
        .unwrap(),
    );
    let source_handle = (&mut *source) as *mut NativeFullTQuad as u64;
    let mut planes = NativeGeometryPlanes::new();
    let mut workspace = BspFullQuadWorkspace {
        tree: create_bsp_tree(),
        geometry_planes: &mut planes,
        quads: vec![source.info.clone()],
        handles: vec![source_handle],
        owned_split_quads: Vec::new(),
        updated_quad_handles: Vec::new(),
        mesh_quad_count: 1,
        index_quad_count: 1,
        max_quad_count: 4,
        quantize_trigger_normals: false,
    };
    let mut splitting_group = Vec::new();
    let mut outside = Vec::new();
    let mut inside = Vec::new();

    split_full_candidate(
        &mut workspace,
        &mut splitting_group,
        0,
        [1.0, 0.0, 0.0],
        0.5,
        &mut outside,
        &mut inside,
    )
    .unwrap();

    assert!(splitting_group.is_empty());
    assert_eq!(vec![0], inside);
    assert_eq!(vec![1], outside);
    assert_eq!(2, workspace.mesh_quad_count);
    assert_eq!(2, workspace.index_quad_count);
    assert_eq!(1, workspace.owned_split_quads.len());
    assert_eq!(2, workspace.updated_quad_handles.len());
    assert_eq!(0, source.write_to_index);
    assert!(source.has_updated_vertices);
    assert_eq!(1, workspace.owned_split_quads[0].write_to_index);
    assert!(workspace.owned_split_quads[0].has_updated_vertices);
}

#[test]
fn native_full_quad_clamps_light_before_split_interpolation() {
    let mut source = full_quad_xy();
    source.vertices[0].light = 0;
    source.vertices[1].light = 0x0010_0010;
    let quad =
        create_full_quad(&source, FACING_POS_Z, packed_aligned_normal(FACING_POS_Z)).unwrap();

    assert_eq!(0x0008_0008, quad.quad.vertices[0].light);
    assert_eq!(0x0010_0010, quad.quad.vertices[1].light);

    let interpolated = interpolate_full_quad_attributes(
        0.5,
        (1.0, 0.0, 0.0),
        quad.quad.vertices[0],
        quad.quad.vertices[1],
    )
    .unwrap();

    assert_eq!(0x000c_000c, interpolated.light);
}

#[test]
fn topo_quad_store_updates_and_queries_by_index() {
    let records = [
        topo_record(FACING_POS_Z, 1.0),
        topo_record(FACING_POS_Z, 0.0),
    ];
    let mut store = create_topo_quad_store(&records).unwrap();

    assert!(!bsp_double_leaf_possible(
        store.quads[0].as_ref().unwrap(),
        store.quads[1].as_ref().unwrap(),
        false
    ));

    assert_eq!(
        OK,
        topo_quad_store_set(&mut store, 1, &topo_record(FACING_NEG_Z, 0.0))
    );
    assert!(bsp_double_leaf_possible(
        store.quads[0].as_ref().unwrap(),
        store.quads[1].as_ref().unwrap(),
        false
    ));

    assert_eq!(OK, topo_quad_store_remove(&mut store, 1));
    assert!(store.quads[1].is_none());
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

fn topo_record(facing: i32, z: f32) -> TranslucentTopoQuadRecord {
    let record = vertex_record(facing, z);
    let quad = build_quad_info(&record);
    TranslucentTopoQuadRecord {
        positions: quad.positions,
        extents: quad.extents,
        accurate_dot_product: quad.accurate_dot_product,
        facing: quad.facing,
        packed_normal: quad.packed_normal,
    }
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
fn bsp_tree_writes_multi_leaf_order() {
    let mut tree = create_bsp_tree();
    let root = add_bsp_node(&mut tree, BspNode::LeafMulti { quads: vec![1, 0] }).unwrap();
    tree.root = root;
    tree.index_quad_count = 2;

    let mut output = vec![0; 12];
    assert_eq!(
        OK,
        write_bsp_tree_index_buffer(&tree, &mut output, 0.0, 0.0, 0.0)
    );
    assert_eq!(vec![4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0], output);
}

#[test]
fn bsp_tree_traverses_fixed_double_in_native_order() {
    let mut tree = create_bsp_tree();
    let first = add_bsp_node(
        &mut tree,
        BspNode::LeafDouble {
            quad_a: 0,
            quad_b: 1,
        },
    )
    .unwrap();
    let second = add_bsp_node(&mut tree, BspNode::LeafSingle { quad: 2 }).unwrap();
    let root = add_bsp_node(
        &mut tree,
        BspNode::FixedDouble {
            remap: BspRemap::none(),
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
        vec![0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4, 8, 9, 10, 10, 11, 8],
        output
    );
}

#[test]
fn bsp_tree_traverses_multi_partition_by_camera_interval() {
    let mut tree = create_bsp_tree();
    let first = add_bsp_node(&mut tree, BspNode::LeafSingle { quad: 0 }).unwrap();
    let middle = add_bsp_node(&mut tree, BspNode::LeafSingle { quad: 1 }).unwrap();
    let last = add_bsp_node(&mut tree, BspNode::LeafSingle { quad: 2 }).unwrap();
    let root = add_bsp_node(
        &mut tree,
        BspNode::MultiPartition {
            remap: BspRemap::none(),
            normal: [1.0, 0.0, 0.0],
            plane_distances: vec![0.5, 1.5],
            partitions: vec![first, middle, last],
            on_plane_quads: vec![vec![3], vec![4]],
        },
    )
    .unwrap();
    tree.root = root;
    tree.index_quad_count = 5;

    let mut output = vec![0; 30];
    assert_eq!(
        OK,
        write_bsp_tree_index_buffer(&tree, &mut output, 0.0, 0.0, 0.0)
    );
    assert_eq!(
        vec![
            8, 9, 10, 10, 11, 8, 16, 17, 18, 18, 19, 16, 4, 5, 6, 6, 7, 4, 12, 13, 14, 14, 15, 12,
            0, 1, 2, 2, 3, 0,
        ],
        output
    );

    assert_eq!(
        OK,
        write_bsp_tree_index_buffer(&tree, &mut output, 2.0, 0.0, 0.0)
    );
    assert_eq!(
        vec![
            0, 1, 2, 2, 3, 0, 12, 13, 14, 14, 15, 12, 4, 5, 6, 6, 7, 4, 16, 17, 18, 18, 19, 16, 8,
            9, 10, 10, 11, 8,
        ],
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

#[test]
fn reusable_bsp_root_applies_fixed_offset_remap_natively() {
    let base_records = (0..32)
        .map(|index| vertex_record(FACING_POS_Z, index as f32))
        .collect::<Vec<_>>();
    let base_quads = base_records.iter().map(build_quad_info).collect::<Vec<_>>();
    let mut shifted_quads = Vec::with_capacity(base_quads.len() + 1);
    shifted_quads.push(base_quads[0].clone());
    shifted_quads.extend(base_quads.iter().cloned());
    let indexes = (1..=32).collect::<Vec<_>>();

    let mut old_tree = create_bsp_tree();
    let first = add_bsp_node(
        &mut old_tree,
        BspNode::LeafMulti {
            quads: (0..16).collect(),
        },
    )
    .unwrap();
    let second = add_bsp_node(
        &mut old_tree,
        BspNode::LeafMulti {
            quads: (16..32).collect(),
        },
    )
    .unwrap();
    let root = add_bsp_node(
        &mut old_tree,
        BspNode::FixedDouble {
            remap: BspRemap::none(),
            first,
            second,
        },
    )
    .unwrap();
    old_tree.root = root;
    old_tree.index_quad_count = 32;
    let old_root = Box::new(NativeBspReusableRoot {
        tree: old_tree,
        geometry_planes: NativeGeometryPlanes::new(),
        reuse_data: prepare_bsp_reuse_data(&base_quads, &(0..32).collect::<Vec<_>>()).unwrap(),
    });
    let old_root_handle = Box::into_raw(old_root) as u64;
    let mut geometry_planes = NativeGeometryPlanes::new();

    let output = try_reuse_bsp_root(
        &shifted_quads,
        &indexes,
        old_root_handle,
        &mut geometry_planes,
    )
    .unwrap()
    .unwrap();
    let mut index_buffer = vec![0; 32 * 6];

    assert_eq!(
        OK,
        write_bsp_tree_index_buffer(&output.tree, &mut index_buffer, 0.0, 0.0, 0.0)
    );
    assert_eq!(&[4, 5, 6, 6, 7, 4], &index_buffer[0..6]);
    assert_eq!(&[128, 129, 130, 130, 131, 128], &index_buffer[186..192]);

    unsafe {
        drop(Box::from_raw(old_root_handle as *mut NativeBspReusableRoot));
    }
}
