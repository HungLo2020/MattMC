use super::*;

fn vertex(u: f32, v: f32) -> WorldMeshVertex {
    WorldMeshVertex {
        position: [0.0, 1.0, 0.0],
        uv: [u, v],
        shader_atlas_uv: [u, v],
        shader_block_id: 7,
        shader_material_type: 0,
        terrain_material_bits: 0,
        mid_block_packed: 0xff00_0000,
        color_argb: 0xffff_ffff,
        normal_packed: 0x007f_00,
        light: 0x00f0_00f0,
    }
}

fn quads(count: usize) -> Vec<WorldMeshVertex> {
    (0..count * 4).map(|index| vertex(0.1 + index as f32 * 0.001, 0.2)).collect()
}

fn params(layer: u32) -> LayerAssemblyParams {
    LayerAssemblyParams { section_pos: 0x1234_5678, layer_ordinal: layer, atlas_generation: 3, water_block_atlas: true, water: None }
}

fn metadata(kinds: &[i32]) -> Vec<i32> {
    kinds.iter().flat_map(|kind| [*kind, 0, 0, 0, 0, 0, 0, 0, 0, 0]).collect()
}

fn quad_bytes(primitive: u32) -> Vec<u8> {
    let base = primitive * 4;
    [base, base + 1, base + 2, base + 2, base + 3, base].iter().flat_map(|index| index.to_le_bytes()).collect()
}

#[test]
fn opaque_layers_get_one_u16_range_per_segment() {
    let mut vertices = quads(3);
    let assembled = assemble_layer(&mut vertices, &[8, 1, 4, 2], &metadata(&[0, 0, 0]), &[], &params(LAYER_SOLID)).unwrap();
    assert_eq!(INDEX_TYPE_U16, assembled.index_type);
    let indices: Vec<u16> = assembled.index_bytes.chunks_exact(2).map(|pair| u16::from_le_bytes([pair[0], pair[1]])).collect();
    assert_eq!(&indices[..12], &[0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4]);
    assert_eq!(&indices[12..], &[8, 9, 10, 10, 11, 8]);
    assert_eq!(2, assembled.sections.len());
    assert_eq!((0, 12, 1), (assembled.sections[0].index_offset, assembled.sections[0].index_count, assembled.sections[0].source_facing));
    assert_eq!((24, 6, 2), (assembled.sections[1].index_offset, assembled.sections[1].index_count, assembled.sections[1].source_facing));
    assert_eq!(WORLD_MATERIAL_ID_OPAQUE_TEXTURED, assembled.sections[0].material_id);
    assert_eq!((11, 1, 0, 1), (assembled.max_index, assembled.positive_y_sections, assembled.negative_y_sections, assembled.horizontal_sections));
    let mut cutout_vertices = quads(3);
    let cutout = assemble_layer(&mut cutout_vertices, &[8, 1, 4, 2], &metadata(&[0, 0, 0]), &[], &params(1)).unwrap();
    assert_eq!(WORLD_MATERIAL_ID_CUTOUT_TEXTURED, cutout.sections[0].material_id);
    assert_eq!(WORLD_MATERIAL_MODE_CUTOUT, cutout.sections[0].material_mode);
}

#[test]
fn generation_is_content_identity_and_key_is_atlas_scoped() {
    let assemble = |vertices: &mut Vec<WorldMeshVertex>| {
        assemble_layer(vertices, &[8, 1], &metadata(&[0, 0]), &[], &params(LAYER_SOLID)).unwrap()
    };
    let first = assemble(&mut quads(2));
    assert_eq!(first.mesh_generation, assemble(&mut quads(2)).mesh_generation);
    let mut changed = quads(2);
    changed[5].color_argb = 0xff00_ff00;
    assert_ne!(first.mesh_generation, assemble(&mut changed).mesh_generation);
    let mut moved = quads(2);
    moved[1].mid_block_packed = 1;
    assert_ne!(first.mesh_generation, assemble(&mut moved).mesh_generation);
    assert_ne!(mesh_key(0x1234_5678, 0, 3), mesh_key(0x1234_5678, 0, 4));
    assert_eq!(first.mesh_key, mesh_key(0x1234_5678, 0, 3));
}

#[test]
fn translucent_layers_keep_sorter_order_and_split_ranges_by_material() {
    let mut vertices = quads(3);
    // Sorter order 2, 0, 1; primitive 0 is an unsupported fluid.
    let sorted: Vec<u8> = [2, 0, 1].iter().flat_map(|primitive| quad_bytes(*primitive)).collect();
    let assembled = assemble_layer(
        &mut vertices,
        &[12, 6],
        &metadata(&[PRIMITIVE_KIND_UNSUPPORTED_FLUID, PRIMITIVE_KIND_GENERIC_FLUID, PRIMITIVE_KIND_UNKNOWN]),
        &sorted,
        &params(LAYER_TRANSLUCENT),
    )
    .unwrap();
    assert_eq!(INDEX_TYPE_U32, assembled.index_type);
    let mut expected = quad_bytes(2);
    expected.extend(quad_bytes(1));
    assert_eq!(expected, assembled.index_bytes);
    // The omitted primitive closes the first range.
    assert_eq!(2, assembled.sections.len());
    assert_eq!((0, 6), (assembled.sections[0].index_offset, assembled.sections[0].index_count));
    assert_eq!((24, 6), (assembled.sections[1].index_offset, assembled.sections[1].index_count));
    assert_eq!(FACING_UNASSIGNED, assembled.sections[0].source_facing);
    let accounting = assembled.translucent.unwrap();
    assert_eq!((3, 2, 0, 1, 2, 1), (
        accounting.source_primitives,
        accounting.non_fluid_primitives,
        accounting.water_primitives,
        accounting.unsupported_primitives,
        accounting.retained_primitives,
        accounting.omitted_primitives,
    ));
    assert_eq!((18, 12, 6), (accounting.source_indices, accounting.retained_indices, accounting.omitted_indices));
    assert_eq!(sorted_index_hash(&sorted), accounting.source_hash);
    assert_eq!(3, accounting.samples.len());
    assert!(!accounting.samples[1].retained);
}

#[test]
fn facing_local_sorter_indices_are_rebased_onto_the_layer() {
    let mut vertices = quads(3);
    // Two facing segments (2 quads, 1 quad), each numbered from zero.
    let mut sorted = quad_bytes(1);
    sorted.extend(quad_bytes(0));
    sorted.extend(quad_bytes(0));
    let assembled = assemble_layer(&mut vertices, &[8, 1, 4, 2], &metadata(&[0, 0, 0]), &sorted, &params(LAYER_TRANSLUCENT)).unwrap();
    let mut expected = quad_bytes(1);
    expected.extend(quad_bytes(0));
    expected.extend(quad_bytes(2));
    assert_eq!(expected, assembled.index_bytes);
}

#[test]
fn water_quads_take_their_sprite_and_material_type() {
    let sprites = [
        FfiWaterSprite { texture_id: WORLD_MATERIAL_TEXTURE_WATER_STILL, u0: 0.0, u1: 0.25, v0: 0.0, v1: 0.25 },
        FfiWaterSprite { texture_id: WORLD_MATERIAL_TEXTURE_WATER_FLOW, u0: 0.5, u1: 1.0, v0: 0.0, v1: 0.5 },
        FfiWaterSprite { texture_id: WORLD_MATERIAL_TEXTURE_WATER_OVERLAY, u0: 0.25, u1: 0.5, v0: 0.0, v1: 0.25 },
    ];
    let mut vertices: Vec<WorldMeshVertex> =
        [(0.1, 0.1); 4].into_iter().chain([(0.75, 0.25); 4]).map(|(u, v)| vertex(u, v)).collect();
    let layer = LayerAssemblyParams { water_block_atlas: false, water: Some(sprites), ..params(LAYER_TRANSLUCENT) };
    let kinds = metadata(&[PRIMITIVE_KIND_BUILTIN_WATER, PRIMITIVE_KIND_BUILTIN_WATER]);
    let assembled = assemble_layer(&mut vertices, &[8, 1], &kinds, &[], &layer).unwrap();
    assert_eq!(1, vertices[0].shader_material_type);
    assert_eq!(2, vertices[4].shader_material_type);
    assert!((vertices[0].uv[0] - 0.4).abs() < 1e-6, "still sprite local u");
    assert!((vertices[4].uv[0] - 0.5).abs() < 1e-6, "flow sprite local u");
    assert_eq!(vec![WORLD_MATERIAL_TEXTURE_WATER_STILL, WORLD_MATERIAL_TEXTURE_WATER_FLOW],
        assembled.sections.iter().map(|section| section.texture_id).collect::<Vec<_>>());
    let accounting = assembled.translucent.unwrap();
    assert_eq!((1, 1, 1), (accounting.water_still, accounting.water_flow, accounting.water_texture_switches));
    // The block-atlas binding keeps atlas UVs and the atlas texture.
    let mut atlas_vertices: Vec<WorldMeshVertex> = [(0.1, 0.1); 4].into_iter().map(|(u, v)| vertex(u, v)).collect();
    let atlas = assemble_layer(&mut atlas_vertices, &[4, 1], &metadata(&[PRIMITIVE_KIND_BUILTIN_WATER]), &[],
        &LayerAssemblyParams { water_block_atlas: true, ..layer }).unwrap();
    assert_eq!(0.1, atlas_vertices[0].uv[0]);
    assert_eq!(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS, atlas.sections[0].texture_id);
    assert_eq!(WORLD_MATERIAL_ID_WATER_TRANSLUCENT, atlas.sections[0].material_id);
}

#[test]
fn a_fully_omitted_translucent_layer_is_empty_and_malformed_orders_are_rejected() {
    let mut vertices = quads(1);
    let empty = assemble_layer(&mut vertices, &[4, 1], &metadata(&[PRIMITIVE_KIND_UNSUPPORTED_FLUID]), &[], &params(LAYER_TRANSLUCENT)).unwrap();
    assert!(empty.is_empty());
    let mut duplicate = quad_bytes(0);
    duplicate.extend(quad_bytes(0));
    let mut vertices = quads(2);
    assert!(assemble_layer(&mut vertices, &[8, 1], &metadata(&[0, 0]), &duplicate, &params(LAYER_TRANSLUCENT)).is_err());
    let mut vertices = quads(1);
    assert!(assemble_layer(&mut vertices, &[4, 1], &metadata(&[9]), &[], &params(LAYER_TRANSLUCENT)).is_err());
}

// Former Java translucent contract cases (RustGalTerrainRendererLightingContractTest).

const TEST_SPRITES: [FfiWaterSprite; 3] = [
    FfiWaterSprite { texture_id: WORLD_MATERIAL_TEXTURE_WATER_STILL, u0: 0.0, u1: 0.25, v0: 0.0, v1: 0.25 },
    FfiWaterSprite { texture_id: WORLD_MATERIAL_TEXTURE_WATER_FLOW, u0: 0.25, u1: 0.5, v0: 0.0, v1: 0.25 },
    FfiWaterSprite { texture_id: WORLD_MATERIAL_TEXTURE_WATER_OVERLAY, u0: 0.5, u1: 0.75, v0: 0.0, v1: 0.25 },
];

/// Primitive 0 in the still sprite, 2 in the flow sprite, others outside water.
fn contract_vertices(primitives: usize) -> Vec<WorldMeshVertex> {
    let mut vertices = Vec::new();
    for primitive in 0..primitives {
        let base_u = match primitive {
            0 => 0.05,
            2 => 0.30,
            _ => 0.80,
        };
        for (u, v) in [(base_u, 0.05), (base_u + 0.05, 0.05), (base_u + 0.05, 0.10), (base_u, 0.10)] {
            vertices.push(WorldMeshVertex { color_argb: 0xffff_ffff, mid_block_packed: 0, ..vertex(u, v) });
        }
    }
    vertices
}

fn sorted_quads(primitives: &[u32]) -> Vec<u8> {
    primitives.iter().flat_map(|primitive| quad_bytes(*primitive)).collect()
}

fn contract_params(block_atlas: bool) -> LayerAssemblyParams {
    LayerAssemblyParams { water_block_atlas: block_atlas, water: Some(TEST_SPRITES), ..params(LAYER_TRANSLUCENT) }
}

fn ordered(kinds: &[i32], order: &[u32], vertices: &mut Vec<WorldMeshVertex>, block_atlas: bool) -> Result<AssembledLayer, String> {
    let segments = [vertices.len() as i32, 6];
    assemble_layer(vertices, &segments, &metadata(kinds), &sorted_quads(order), &contract_params(block_atlas))
}

#[test]
fn separate_sheet_water_and_glass_keep_sorted_order_without_grouping() {
    let mut vertices = contract_vertices(3);
    let mesh = ordered(
        &[PRIMITIVE_KIND_BUILTIN_WATER, PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT, PRIMITIVE_KIND_BUILTIN_WATER],
        &[0, 1, 2], &mut vertices, false).unwrap();
    let accounting = mesh.translucent.clone().unwrap();
    assert_eq!(72, mesh.index_bytes.len());
    assert_eq!((3, 1, 2, 0, 3, 0, 18, 18), (
        accounting.source_primitives, accounting.non_fluid_primitives, accounting.water_primitives,
        accounting.unsupported_primitives, accounting.retained_primitives, accounting.omitted_primitives,
        accounting.source_indices, accounting.retained_indices));
    assert_eq!(3, mesh.sections.len(), "water/glass/water sorted order must not be grouped by material");
    assert_eq!(2, accounting.material_switches);
    let ranges: Vec<_> = mesh.sections.iter()
        .map(|section| (section.material_id, section.texture_id, section.index_offset, section.index_count))
        .collect();
    assert_eq!(vec![
        (WORLD_MATERIAL_ID_WATER_TRANSLUCENT, WORLD_MATERIAL_TEXTURE_WATER_STILL, 0, 6),
        (WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED, WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS, 24, 6),
        (WORLD_MATERIAL_ID_WATER_TRANSLUCENT, WORLD_MATERIAL_TEXTURE_WATER_FLOW, 48, 6),
    ], ranges);
}

#[test]
fn atlas_water_keeps_sorted_geometry_and_uvs_and_sets_only_material_type() {
    let mut vertices = contract_vertices(3);
    let originals = vertices.clone();
    let mesh = ordered(
        &[PRIMITIVE_KIND_BUILTIN_WATER, PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT, PRIMITIVE_KIND_BUILTIN_WATER],
        &[2, 1, 0], &mut vertices, true).unwrap();
    assert_eq!(sorted_quads(&[2, 1, 0]), mesh.index_bytes);
    assert_eq!(3, mesh.sections.len());
    let accounting = mesh.translucent.clone().unwrap();
    assert_eq!((1, 1, 0), (accounting.water_still, accounting.water_flow, accounting.omitted_primitives));
    for (index, (before, after)) in originals.iter().zip(&vertices).enumerate() {
        let material_type = if index < 4 { 1 } else if index >= 8 { 2 } else { before.shader_material_type };
        assert_eq!(before.uv, after.uv);
        assert_eq!(material_type, after.shader_material_type);
    }
    for (index, section) in mesh.sections.iter().enumerate() {
        assert_eq!(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS, section.texture_id);
        let material = if index == 1 { WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED } else { WORLD_MATERIAL_ID_WATER_TRANSLUCENT };
        assert_eq!((material, index as u32 * 24, 6), (section.material_id, section.index_offset, section.index_count));
        assert_eq!((WORLD_CULL_BACK, WORLD_WINDING_CCW, WORLD_MATERIAL_MODE_TRANSLUCENT),
            (section.cull_policy, section.winding, section.material_mode));
    }
}

#[test]
fn adjacent_atlas_water_sprites_share_one_range_and_keep_their_identity() {
    let mut vertices = contract_vertices(3);
    for (offset, (u, v)) in [(0.55, 0.05), (0.60, 0.05), (0.60, 0.10), (0.55, 0.10)].into_iter().enumerate() {
        vertices[4 + offset].uv = [u, v];
    }
    let mesh = ordered(&[PRIMITIVE_KIND_BUILTIN_WATER; 3], &[2, 0, 1], &mut vertices, true).unwrap();
    assert_eq!(sorted_quads(&[2, 0, 1]), mesh.index_bytes);
    assert_eq!(1, mesh.sections.len());
    assert_eq!(18, mesh.sections[0].index_count);
    let accounting = mesh.translucent.unwrap();
    assert_eq!((1, 1, 1, 0), (accounting.water_still, accounting.water_flow, accounting.water_overlay,
        accounting.water_texture_switches));
    assert_eq!(3, vertices[4].shader_material_type);
    assert_eq!(0.55, vertices[4].uv[0]);
}

#[test]
fn generic_fluid_stays_in_the_sorted_payload_with_atlas_semantics() {
    let mut vertices = contract_vertices(3);
    let mesh = ordered(
        &[PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT, PRIMITIVE_KIND_GENERIC_FLUID, PRIMITIVE_KIND_BUILTIN_WATER],
        &[0, 1, 2], &mut vertices, false).unwrap();
    assert_eq!(72, mesh.index_bytes.len(), "a generic fluid must remain in the sorted payload");
    let accounting = mesh.translucent.clone().unwrap();
    assert_eq!((2, 1, 0, 3, 0, 0), (accounting.non_fluid_primitives, accounting.water_primitives,
        accounting.unsupported_primitives, accounting.retained_primitives, accounting.omitted_primitives,
        accounting.omitted_indices));
    assert_eq!(2, mesh.sections.len(), "adjacent generic-fluid and ordinary translucent ranges coalesce");
    assert_eq!((WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED, 0, 12),
        (mesh.sections[0].material_id, mesh.sections[0].index_offset, mesh.sections[0].index_count));
    assert_eq!((WORLD_MATERIAL_ID_WATER_TRANSLUCENT, WORLD_MATERIAL_TEXTURE_WATER_FLOW, 48),
        (mesh.sections[1].material_id, mesh.sections[1].texture_id, mesh.sections[1].index_offset));
}

#[test]
fn malformed_sort_references_are_rejected() {
    let kinds = [PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT, PRIMITIVE_KIND_BUILTIN_WATER];
    assert!(ordered(&kinds, &[0, 0], &mut contract_vertices(2), false).is_err());
    assert!(ordered(&kinds, &[0], &mut contract_vertices(2), false).is_err());
    let mut interleaved = sorted_quads(&[0, 1]);
    interleaved[4] = 7;
    let mut vertices = contract_vertices(2);
    assert!(assemble_layer(&mut vertices, &[8, 6], &metadata(&kinds), &interleaved, &contract_params(false)).is_err());
}

#[test]
fn facing_local_orders_normalize_and_global_orders_pass_through() {
    assert_eq!(sorted_quads(&[0, 1]), normalize_sorted_indices(&sorted_quads(&[0, 0]), 8, &[1, 1]).unwrap());
    assert_eq!(sorted_quads(&[1, 0]), normalize_sorted_indices(&sorted_quads(&[1, 0]), 8, &[1, 1]).unwrap(),
        "already-global sorter payloads pass through unchanged");
    assert!(normalize_sorted_indices(&sorted_quads(&[0, 0]), 8, &[2]).is_err());
}

#[test]
fn all_unsupported_payload_is_an_empty_filtered_range_and_unknown_flat_quads_are_glass() {
    let mut vertices = contract_vertices(1);
    let empty = ordered(&[PRIMITIVE_KIND_UNSUPPORTED_FLUID], &[0], &mut vertices, false).unwrap();
    let accounting = empty.translucent.clone().unwrap();
    assert_eq!((1, 1, 0, 1, 0, 6), (accounting.source_primitives, accounting.unsupported_primitives,
        accounting.retained_primitives, accounting.omitted_primitives, accounting.retained_indices,
        accounting.omitted_indices));
    assert!(empty.sections.is_empty());
    let mut vertices = contract_vertices(1);
    let flat = ordered(&[PRIMITIVE_KIND_UNKNOWN], &[0], &mut vertices, false).unwrap();
    let accounting = flat.translucent.clone().unwrap();
    assert_eq!((1, 1, 0), (accounting.non_fluid_primitives, accounting.retained_primitives, accounting.omitted_primitives));
    assert_eq!(WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED, flat.sections[0].material_id);
}
