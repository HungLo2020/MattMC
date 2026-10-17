use super::*;
use crate::render::chunk::terrain_selection::section_key;

/// An orthographic frustum so wide that every test box passes: each plane is
/// `axis * 1e-6 + 1`, i.e. `|axis| <= 1e6` after normalisation.
fn open_frustum() -> Frustum {
    let mut m = [0.0f32; 16];
    m[0] = 1e-6;
    m[5] = 1e-6;
    m[10] = 1e-6;
    m[15] = 1.0;
    Frustum::from_matrix(m)
}

/// An orthographic frustum accepting `|x|, |y|, |z| <= half` (camera space).
fn box_frustum(half: f32) -> Frustum {
    let mut m = [0.0f32; 16];
    m[0] = 1.0 / half;
    m[5] = 1.0 / half;
    m[10] = 1.0 / half;
    m[15] = 1.0;
    Frustum::from_matrix(m)
}

fn graph_with_columns(columns: impl IntoIterator<Item = (i32, i32)>, min_y: i32, max_y: i32, info: SectionInfo) -> SectionGraph {
    let mut graph = SectionGraph::new(min_y, max_y);
    let columns = columns.into_iter().collect::<Vec<_>>();
    for &(x, z) in &columns {
        graph.add_column(x, z);
    }
    for &(x, z) in &columns {
        for y in min_y..=max_y {
            graph.set_info([x, y, z], Some(info));
        }
    }
    graph
}

fn visit_order(graph: &mut SectionGraph, viewport: &Viewport, distance: f32, occlusion: bool) -> Vec<[i32; 3]> {
    let mut order = Vec::new();
    graph.find_visible(viewport, distance, occlusion, |section| order.push(section.position));
    order
}

#[test]
fn frustum_matches_joml_plane_arithmetic() {
    let frustum = box_frustum(32.0);
    assert!(frustum.test_aab([-10.0; 3], [10.0; 3]));
    assert!(frustum.test_aab([31.0, -1.0, -1.0], [40.0, 1.0, 1.0]));
    assert!(!frustum.test_aab([32.5, -1.0, -1.0], [40.0, 1.0, 1.0]));
    // The boundary itself is inside (>= -w).
    assert!(frustum.test_aab([32.0, 0.0, 0.0], [33.0, 1.0, 1.0]));
}

#[test]
fn camera_transform_truncates_and_reduces_fraction_precision() {
    let transform = CameraTransform::new([150.5, -20.25, -3000.7]);
    assert_eq!(transform.int, [150, -20, -3000]);
    let expected = |full: f64| {
        let full = full as f32;
        let modifier = 128.0f32.copysign(full);
        (full + modifier) - modifier
    };
    assert_eq!(transform.frac, [expected(0.5), expected(-0.25), expected(-3000.7 - -3000.0)]);
    let viewport = Viewport::new(open_frustum(), [-0.5, 15.9, 16.0]);
    assert_eq!(viewport.section, [-1, 0, 1]);
}

#[test]
fn open_world_visits_in_manhattan_waves_with_fixed_direction_order() {
    let columns = (-1..=1).flat_map(|x| (-1..=1).map(move |z| (x, z)));
    let mut graph = graph_with_columns(columns, 0, 2, SectionInfo::EMPTY);
    let viewport = Viewport::new(open_frustum(), [8.0, 24.0, 8.0]);
    let order = visit_order(&mut graph, &viewport, 1000.0, true);
    // Root, then wave 1 in down, up, north, south, west, east order.
    assert_eq!(
        &order[..7],
        &[[0, 1, 0], [0, 0, 0], [0, 2, 0], [0, 1, -1], [0, 1, 1], [-1, 1, 0], [1, 1, 0]]
    );
    assert_eq!(order.len(), 27);
    let manhattan = |p: &[i32; 3]| p[0].abs() + (p[1] - 1).abs() + p[2].abs();
    assert!(order.windows(2).all(|pair| manhattan(&pair[0]) <= manhattan(&pair[1])));
}

#[test]
fn opaque_sections_stop_traversal_but_are_still_visited() {
    let columns = (-3..=3).map(|x| (x, 0));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo::EMPTY);
    // A wall with no internal connections at x = 2.
    graph.set_info([2, 0, 0], Some(SectionInfo { flags: 1, visibility: 0 }));
    let viewport = Viewport::new(open_frustum(), [8.0, 8.0, 8.0]);
    let order = visit_order(&mut graph, &viewport, 1000.0, true);
    assert!(order.contains(&[2, 0, 0]));
    assert!(!order.contains(&[3, 0, 0]));
    assert!(order.contains(&[-3, 0, 0]));
    // Without occlusion culling the wall is transparent.
    assert!(visit_order(&mut graph, &viewport, 1000.0, false).contains(&[3, 0, 0]));
}

#[test]
fn unbuilt_sections_are_visited_but_never_propagate() {
    let columns = (0..=3).map(|x| (x, 0));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo::EMPTY);
    graph.set_info([1, 0, 0], None);
    let viewport = Viewport::new(open_frustum(), [8.0, 8.0, 8.0]);
    let mut visited = Vec::new();
    graph.find_visible(&viewport, 1000.0, true, |section| visited.push((section.position, section.info)));
    assert!(visited.contains(&([1, 0, 0], None)));
    assert!(!visited.iter().any(|(position, _)| *position == [2, 0, 0]));
}

#[test]
fn distance_and_frustum_cull_like_sodium() {
    let columns = (-8..=8).map(|x| (x, 0));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo::EMPTY);
    let viewport = Viewport::new(open_frustum(), [8.0, 8.0, 8.0]);
    // Section x=3 has its nearest grown-box point at 3*16-1-8 = 39 blocks.
    let order = visit_order(&mut graph, &viewport, 40.0, true);
    assert!(order.contains(&[3, 0, 0]));
    assert!(!order.contains(&[4, 0, 0]));
    let order = visit_order(&mut graph, &viewport, 39.0, true);
    assert!(!order.contains(&[3, 0, 0]));
    // A box frustum of 40 blocks: section x=3's box (centre 56, size 9.125)
    // starts at 46.875 - 8 = 38.875 relative, inside; x=4 is not.
    let viewport = Viewport::new(box_frustum(40.0), [8.0, 8.0, 8.0]);
    let order = visit_order(&mut graph, &viewport, 1000.0, true);
    assert!(order.contains(&[3, 0, 0]));
    assert!(!order.contains(&[4, 0, 0]));
}

#[test]
fn nearby_pass_adds_unreached_neighbours_once() {
    let columns = (-1..=1).flat_map(|x| (-1..=1).map(move |z| (x, z)));
    let mut graph = graph_with_columns(columns, 0, 2, SectionInfo { flags: 1, visibility: 0 });
    let viewport = Viewport::new(open_frustum(), [8.0, 24.0, 8.0]);
    let order = visit_order(&mut graph, &viewport, 1000.0, true);
    // The opaque root reaches nothing; the nearby pass visits all 26
    // neighbours in dx, dy, dz loop order, after the root.
    assert_eq!(order.len(), 27);
    assert_eq!(order[0], [0, 1, 0]);
    assert_eq!(order[1], [-1, 0, -1]);
    assert_eq!(order[26], [1, 2, 1]);
}

#[test]
fn outside_world_height_seeds_the_boundary_in_diamond_spiral_order() {
    let columns = (-2..=2).flat_map(|x| (-2..=2).map(move |z| (x, z)));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo { flags: 1, visibility: 0 });
    // Camera above the level: the top layer (y = 0) is seeded, radius 1.
    let viewport = Viewport::new(open_frustum(), [8.0, 100.0, 8.0]);
    let order = visit_order(&mut graph, &viewport, 16.0, true);
    let seeds = [[0, 0], [0, -1], [-1, 0], [0, 1], [1, 0], [-1, -1], [-1, 1], [1, 1], [1, -1]];
    // Only seeds within the 16-block distance survive their dequeue test.
    let expected = seeds
        .iter()
        .map(|[x, z]| [*x, 0, *z])
        .filter(|position| is_within_render_distance(&viewport.transform, *position, 16.0))
        .collect::<Vec<_>>();
    assert_eq!(order, expected);
}

#[test]
fn removed_columns_unlink_their_neighbours() {
    let columns = (-1..=1).map(|x| (x, 0));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo::EMPTY);
    graph.remove_column(1, 0);
    let viewport = Viewport::new(open_frustum(), [8.0, 8.0, 8.0]);
    assert_eq!(visit_order(&mut graph, &viewport, 1000.0, true), vec![[0, 0, 0], [-1, 0, 0]]);
    graph.add_column(1, 0);
    graph.set_info([1, 0, 0], Some(SectionInfo::EMPTY));
    assert_eq!(visit_order(&mut graph, &viewport, 1000.0, true), vec![[0, 0, 0], [-1, 0, 0], [1, 0, 0]]);
}

#[test]
fn unbuilt_camera_section_switches_to_the_frustum_only_tree() {
    let columns = (-4..=4).map(|x| (x, 0));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo { flags: 1, visibility: 0 });
    // Fully opaque sections would stop the occlusion traversal at the root.
    let viewport = Viewport::new(open_frustum(), [8.0, 8.0, 8.0]);
    let occlusion = visit_order(&mut graph, &viewport, 1000.0, true);
    assert_eq!(occlusion.len(), 3, "root plus its two nearby neighbours");
    // An unbuilt camera section selects the tree: every member section in
    // the frustum, no occlusion and no distance limit.
    graph.set_info([0, 0, 0], None);
    let mut tree = Vec::new();
    graph.select_camera_sections(&viewport, 16.0, true, |section| tree.push(section.position));
    assert_eq!(tree.len(), 9);
    // Front to back from the camera within the tree.
    let position = |x: i32| tree.iter().position(|p| *p == [x, 0, 0]).unwrap();
    assert!(position(1) < position(3));
    assert!(position(-1) < position(-4));
}

#[test]
fn tree_membership_follows_sodium_renderable_sections() {
    let columns = (0..=2).map(|x| (x, 0));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo { flags: 1, visibility: 0 });
    graph.set_info([0, 0, 0], None);
    // Built sections that render nothing leave the tree; empty ones too.
    graph.set_info([1, 0, 0], Some(SectionInfo { flags: 0, visibility: 0 }));
    graph.set_info([2, 0, 0], Some(SectionInfo::EMPTY));
    let viewport = Viewport::new(open_frustum(), [8.0, 8.0, 8.0]);
    let mut tree = Vec::new();
    graph.select_camera_sections(&viewport, 1000.0, true, |section| tree.push(section.position));
    assert_eq!(tree, vec![[0, 0, 0]]);
    graph.remove_column(0, 0);
    let mut tree = Vec::new();
    graph.traverse_tree(&viewport, |section| tree.push(section.position));
    assert!(tree.is_empty());
}

#[test]
fn tree_traversal_tests_leaves_with_the_unpadded_section_box() {
    let columns = (0..=5).map(|x| (x, 0));
    let mut graph = graph_with_columns(columns, 0, 0, SectionInfo { flags: 1, visibility: 0 });
    // Camera space box of 40 blocks around x = 8: section x=2 (centre 40,
    // relative 32, leaf half-size 8) reaches 24..40, inside; x=3 (relative
    // 48) starts at 40 > 40 - 0 only touching, x=4 is outside.
    let viewport = Viewport::new(box_frustum(40.0), [8.0, 8.0, 8.0]);
    let mut tree = Vec::new();
    graph.traverse_tree(&viewport, |section| tree.push(section.position));
    assert!(tree.contains(&[2, 0, 0]));
    assert!(tree.contains(&[3, 0, 0]));
    assert!(!tree.contains(&[4, 0, 0]));
}

#[test]
fn intersect_aab_distinguishes_inside_intersect_and_outside() {
    let frustum = box_frustum(32.0);
    assert_eq!(frustum.intersect_aab([-1.0; 3], [1.0; 3]), BoxIntersection::Inside);
    assert_eq!(frustum.intersect_aab([30.0, -1.0, -1.0], [34.0, 1.0, 1.0]), BoxIntersection::Intersect);
    assert_eq!(frustum.intersect_aab([33.0, -1.0, -1.0], [34.0, 1.0, 1.0]), BoxIntersection::Outside);
}

fn record(graph: &mut SectionGraph, positions: &[[i32; 3]]) {
    let visits = positions
        .iter()
        .map(|&position| {
            let slot = graph.slot(position).unwrap();
            graph.visited(slot)
        })
        .collect::<Vec<_>>();
    graph.record_visits(&visits);
}

#[test]
fn ready_columns_build_air_sections_empty_and_request_the_rest() {
    let mut graph = SectionGraph::new(0, 3);
    // Sections 1 and 3 hold blocks; 0 and 2 are air.
    assert!(graph.add_ready_column(0, 0, 0b1010));
    assert!(!graph.add_ready_column(0, 0, 0b1111));
    assert!(graph.section_ready([0, 0, 0]));
    assert!(!graph.section_ready([0, 1, 0]));
    assert_eq!(2, graph.needs_build_count());
    record(&mut graph, &[[0, 3, 0], [0, 0, 0], [0, 1, 0]]);
    assert_eq!(vec![section_key([0, 3, 0]), section_key([0, 1, 0])], graph.source.build_requests);

    // In-flight sections are not requested again.
    graph.build_started([0, 3, 0]);
    record(&mut graph, &[[0, 3, 0], [0, 1, 0]]);
    assert_eq!(vec![section_key([0, 1, 0])], graph.source.build_requests);
    assert_eq!(BuildCompletion::Current, graph.finish_build([0, 3, 0]));
    graph.accept_build([0, 3, 0], SectionInfo { flags: 1, visibility: 0 }, false, &[]);
    assert!(graph.section_ready([0, 3, 0]));
    assert_eq!(1, graph.needs_build_count());
    assert_eq!(0, graph.in_flight_count());
}

#[test]
fn edits_are_urgent_and_make_in_flight_builds_stale() {
    let mut graph = SectionGraph::new(0, 1);
    graph.add_ready_column(0, 0, 0b11);
    graph.accept_build([0, 0, 0], SectionInfo { flags: 1, visibility: 0 }, false, &[]);
    graph.build_started([0, 1, 0]);
    // Outside a ready column or the level height nothing is marked.
    assert!(!graph.schedule_rebuild([5, 0, 5]));
    assert!(!graph.schedule_rebuild([0, 2, 0]));
    assert!(graph.schedule_rebuild([0, 1, 0]));
    assert!(graph.schedule_rebuild([0, 0, 0]));
    assert_eq!(BuildCompletion::Stale, graph.finish_build([0, 1, 0]));
    // The rebuild of the built section (an edit) is requested first.
    record(&mut graph, &[[0, 1, 0], [0, 0, 0]]);
    assert_eq!(vec![section_key([0, 0, 0]), section_key([0, 1, 0])], graph.source.build_requests);
    assert!(!graph.section_ready([0, 0, 0]));

    // Unloading a column makes its in-flight builds stale and forgets it.
    graph.build_started([0, 0, 0]);
    assert!(graph.remove_ready_column(0, 0));
    assert!(!graph.remove_ready_column(0, 0));
    assert_eq!(BuildCompletion::Stale, graph.finish_build([0, 0, 0]));
    assert_eq!(0, graph.needs_build_count());
}

#[test]
fn reload_rebuilds_geometry_and_stales_or_cancels_in_flight_builds() {
    let mut graph = SectionGraph::new(0, 1);
    graph.add_ready_column(0, 0, 0b11);
    graph.accept_build([0, 0, 0], SectionInfo { flags: 1, visibility: 0 }, false, &[]);
    graph.build_started([0, 1, 0]);
    graph.reload_resources(false);
    assert_eq!(2, graph.needs_build_count());
    assert_eq!(BuildCompletion::Stale, graph.finish_build([0, 1, 0]));

    graph.build_started([0, 1, 0]);
    graph.reload_resources(true);
    assert_eq!(0, graph.in_flight_count());
}

#[test]
fn block_entity_sections_follow_visits_and_first_build_order() {
    let mut graph = SectionGraph::new(0, 0);
    for x in 0..3 {
        graph.add_ready_column(x, 0, 1);
    }
    let culled = SectionInfo { flags: 1 | source::FLAG_BLOCK_ENTITIES, visibility: 0 };
    graph.accept_build([2, 0, 0], culled, true, &[]);
    graph.accept_build([0, 0, 0], culled, false, &[]);
    graph.accept_build([1, 0, 0], SectionInfo { flags: 1, visibility: 0 }, true, &[]);
    record(&mut graph, &[[1, 0, 0], [0, 0, 0], [2, 0, 0]]);
    assert_eq!(vec![section_key([0, 0, 0]), section_key([2, 0, 0])], graph.source.block_entity_sections);
    assert_eq!(&[section_key([2, 0, 0]), section_key([1, 0, 0])], graph.global_block_entity_sections());
    // A rebuild keeps its place; losing and regaining globals moves it last.
    graph.accept_build([2, 0, 0], culled, true, &[]);
    assert_eq!(&[section_key([2, 0, 0]), section_key([1, 0, 0])], graph.global_block_entity_sections());
    graph.accept_build([2, 0, 0], culled, false, &[]);
    graph.accept_build([2, 0, 0], culled, true, &[]);
    assert_eq!(&[section_key([1, 0, 0]), section_key([2, 0, 0])], graph.global_block_entity_sections());
    graph.remove_ready_column(1, 0);
    assert_eq!(&[section_key([2, 0, 0])], graph.global_block_entity_sections());
}

#[test]
fn box_visibility_tests_only_the_latest_search() {
    let mut graph = SectionGraph::new(0, 1);
    assert!(!graph.box_visible([0, 0, 0], [0, 0, 0]));
    graph.add_ready_column(0, 0, 0);
    graph.add_ready_column(1, 0, 0);
    record(&mut graph, &[[0, 1, 0]]);
    assert!(graph.box_visible([0, 0, 0], [0, 1, 0]));
    assert!(!graph.box_visible([0, 0, 0], [1, 0, 0]));
    assert!(!graph.box_visible([5, 0, 5], [6, 1, 6]));
    record(&mut graph, &[[1, 0, 0]]);
    assert!(!graph.box_visible([0, 1, 0], [0, 1, 0]));
    assert!(graph.box_visible([0, 0, 0], [1, 0, 0]));
}
