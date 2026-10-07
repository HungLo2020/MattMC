use super::*;
use crate::render::chunk::section_graph::SectionInfo;

const GEOMETRY: u8 = 1;

fn params(shadow_candidates: bool, max_shadow_candidates: usize) -> TerrainSelectionParams {
    TerrainSelectionParams {
        camera: [8.0, 8.0, 8.0],
        depth_policies: [10, 11, 12],
        layer_ordinals: [0, 1, 3],
        shadow_candidates,
        max_shadow_candidates,
    }
}

fn graph(sections: &[([i32; 3], u8)]) -> SectionGraph {
    let mut graph = SectionGraph::new(0, 3);
    for x in -2..=4 {
        graph.add_column(x, 0);
    }
    for &(position, flags) in sections {
        graph.set_info(position, Some(SectionInfo { flags, visibility: 0 }));
    }
    graph
}

fn visit(graph: &SectionGraph, position: [i32; 3]) -> VisitedSection {
    let slot = graph.slot(position).unwrap();
    VisitedSection {
        slot,
        position,
        info: graph.section_flags(slot).map(|flags| SectionInfo { flags, visibility: 0 }),
    }
}

fn meshes(base: u64, translucent: bool) -> SectionMeshes {
    SectionMeshes {
        keys: [base, base + 1, if translucent { base + 2 } else { 0 }],
        generations: [7, 7, 7],
        translucent_camera_sorted: translucent,
    }
}

#[test]
fn camera_layers_follow_visit_order_then_translucent_back_to_front() {
    let near = [0, 0, 0];
    let far = [3, 0, 0];
    let unbuilt = [1, 0, 0];
    let empty = [2, 0, 0];
    let mut graph = graph(&[(near, GEOMETRY | FLAG_ANIMATED_SPRITES), (far, GEOMETRY), (empty, 0)]);
    graph.set_meshes(near, Some(meshes(100, true)));
    graph.set_meshes(far, Some(meshes(200, true)));
    graph.set_meshes(unbuilt, Some(meshes(300, false)));
    // Opaque layers keep visit order (not key order); unbuilt and empty
    // sections never draw.
    let visits = [far, unbuilt, near, empty].map(|position| visit(&graph, position));
    let mut out = TerrainSelection::default();
    graph.select_terrain(&visits, &params(false, 0), &mut out);

    let keys = out.sections.iter().map(|section| section.mesh_key).collect::<Vec<_>>();
    assert_eq!(vec![200, 201, 100, 101, 202, 102], keys);
    assert_eq!([48, 0, 0], out.sections[0].origin);
    assert_eq!([0, 0, 0], out.sections[2].origin);
    assert_eq!(vec![10, 11, 10, 11, 12, 12], out.sections.iter().map(|s| s.depth_policy).collect::<Vec<_>>());
    assert_eq!(CAMERA_SORTED_QUADS, out.sections[4].flags);
    assert_eq!(0, out.sections[0].flags);
    assert_eq!(vec![near], out.animated);
    assert_eq!(6, out.receipts.layer_submissions);
    assert_eq!(2 * 2 + 2, out.receipts.layer_probes);
    assert_eq!(section_key(near), out.section_layers[5].section_key);
    assert_eq!(2, out.section_layers[5].layer);
}

#[test]
fn shadow_candidates_are_other_geometry_sections_nearest_first_when_limited() {
    let visible = [0, 0, 0];
    let a = [1, 0, 0];
    let b = [4, 0, 0];
    let c = [-2, 0, 0];
    let mut graph = graph(&[(visible, GEOMETRY), (a, GEOMETRY | FLAG_ANIMATED_SPRITES), (b, GEOMETRY), (c, GEOMETRY)]);
    for (index, position) in [visible, a, b, c].into_iter().enumerate() {
        graph.set_meshes(position, Some(meshes(100 * (index as u64 + 1), false)));
    }
    let visits = [visit(&graph, visible)];
    let mut out = TerrainSelection::default();
    graph.select_terrain(&visits, &params(true, 16), &mut out);
    // Signed key order, as Java's radix sort: x is the top bits, so x = -2 sorts first.
    let keys = out.casters.iter().map(|caster| caster.mesh_key).collect::<Vec<_>>();
    assert_eq!(vec![400, 401, 200, 201, 300, 301], keys);
    assert_eq!(vec![a], out.animated);

    // Over the limit, the nearest centres survive: a (16 blocks), c (32).
    graph.select_terrain(&visits, &params(true, 2), &mut out);
    let keys = out.casters.iter().map(|caster| caster.mesh_key).collect::<Vec<_>>();
    assert_eq!(vec![400, 401, 200, 201], keys);
}

#[test]
fn duplicate_mesh_keys_draw_once_and_cleared_rows_disappear() {
    let first = [0, 0, 0];
    let second = [1, 0, 0];
    let mut graph = graph(&[(first, GEOMETRY), (second, GEOMETRY)]);
    graph.set_meshes(first, Some(meshes(100, false)));
    graph.set_meshes(second, Some(meshes(100, false)));
    let visits = [first, second].map(|position| visit(&graph, position));
    let mut out = TerrainSelection::default();
    graph.select_terrain(&visits, &params(false, 0), &mut out);
    assert_eq!(2, out.sections.len());

    graph.set_meshes(first, Some(SectionMeshes::default()));
    graph.set_meshes(second, None);
    graph.select_terrain(&visits, &params(false, 0), &mut out);
    assert!(out.sections.is_empty());
}
