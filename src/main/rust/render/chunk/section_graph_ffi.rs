//! C ABI for the terrain section graph.
//!
//! The graph is a standalone handle owned by Java's terrain source and used
//! only from the render thread. It is deliberately not part of a VulkanicGAL
//! bridge context: querying visibility must never join a pipelined frame.

use super::section_graph::{BuildCompletion, Frustum, SectionGraph, SectionInfo, Viewport};
use super::terrain_selection::{SectionMeshes, SelectedLayer, TerrainSelectionParams};
use crate::render::bridge::abi::{FfiStaticTerrainSection, FfiStaticTerrainShadowCaster};

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

/// One section's build information. `built == 0` clears it (unbuilt).
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiSectionGraphInfo {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub built: u32,
    pub flags: u32,
    pub reserved: u32,
    pub visibility: u64,
}

/// One visited section, in visit order. `built == 0` until its first build.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct FfiSectionGraphVisit {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub built: u32,
    pub flags: u32,
}

/// Creates a graph for a level with the given inclusive section-Y range.
#[no_mangle]
pub extern "C" fn mattmc_sodium_section_graph_create(min_section_y: i32, max_section_y: i32) -> *mut SectionGraph {
    if min_section_y > max_section_y {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(SectionGraph::new(min_section_y, max_section_y)))
}

/// # Safety
/// `graph` must come from `mattmc_sodium_section_graph_create` and is not
/// used afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_destroy(graph: *mut SectionGraph) {
    if !graph.is_null() {
        drop(Box::from_raw(graph));
    }
}

/// One section's accepted build. `flags` are `RenderSectionFlags`;
/// `global_block_entities` is nonzero when it has off-screen block entities.
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiSectionBuild {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub flags: u32,
    pub visibility: u64,
    pub global_block_entities: u32,
    pub sprite_count: u32,
    /// `sprite_count` animated sprite ids (null only for 0).
    pub sprites: *const u32,
}

/// The latest camera search's bookkeeping outputs; views are valid until the
/// graph's next search or mutation.
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiSectionGraphFrame {
    /// Section keys to build, block edits first, otherwise in visit order.
    pub build_requests: *const i64,
    pub build_request_count: u64,
    /// Visited built sections with culled block entities, in visit order.
    pub block_entity_sections: *const i64,
    pub block_entity_section_count: u64,
    /// Built sections with off-screen block entities, in first-build order.
    pub global_block_entity_sections: *const i64,
    pub global_block_entity_section_count: u64,
    pub needs_build: u64,
    pub in_flight: u64,
    pub visit_count: u64,
}

/// A column became ready (`non_air_mask` bit `y - min_section_y` set for
/// sections that need a build). Returns 1 when added, 0 when already ready.
///
/// # Safety
/// `graph` comes from `mattmc_sodium_section_graph_create`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_add_column(graph: *mut SectionGraph, x: i32, z: i32, non_air_mask: u64) -> i32 {
    match graph.as_mut() {
        Some(graph) => i32::from(graph.add_ready_column(x, z, non_air_mask)),
        None => ERR_NULL_POINTER,
    }
}

/// A column stopped being ready. Returns 1 when removed, 0 when not ready.
///
/// # Safety
/// As [`mattmc_sodium_section_graph_add_column`].
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_remove_column(graph: *mut SectionGraph, x: i32, z: i32) -> i32 {
    match graph.as_mut() {
        Some(graph) => i32::from(graph.remove_ready_column(x, z)),
        None => ERR_NULL_POINTER,
    }
}

/// Section operations: 0 schedule a rebuild (returns 1 when marked), 1 a
/// build was handed to a worker, 2 a build finished (returns 1 when stale),
/// 3 query readiness (returns 1 when ready).
///
/// # Safety
/// As [`mattmc_sodium_section_graph_add_column`].
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_section_op(graph: *mut SectionGraph, op: u32, x: i32, y: i32, z: i32) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    let position = [x, y, z];
    match op {
        0 => i32::from(graph.schedule_rebuild(position)),
        1 => {
            graph.build_started(position);
            OK
        }
        2 => i32::from(graph.finish_build(position) == BuildCompletion::Stale),
        3 => i32::from(graph.section_ready(position)),
        _ => ERR_INVALID_ARGUMENT,
    }
}

/// Accepts a section's newest build (an empty one included).
///
/// # Safety
/// `build` addresses one record whose `sprites` addresses `sprite_count` ids.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_accept_build(graph: *mut SectionGraph, build: *const FfiSectionBuild) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    if build.is_null() {
        return ERR_NULL_POINTER;
    }
    let build = std::ptr::read_unaligned(build);
    if build.flags > 0xFF || (build.sprite_count != 0 && build.sprites.is_null()) {
        return ERR_INVALID_ARGUMENT;
    }
    let sprites = if build.sprite_count == 0 {
        &[][..]
    } else {
        std::slice::from_raw_parts(build.sprites, build.sprite_count as usize)
    };
    graph.accept_build(
        [build.x, build.y, build.z],
        SectionInfo { flags: build.flags as u8, visibility: build.visibility },
        build.global_block_entities != 0,
        sprites,
    );
    OK
}

/// Resource reload; `cancel_in_flight` when the worker pool was replaced.
///
/// # Safety
/// As [`mattmc_sodium_section_graph_add_column`].
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_reload(graph: *mut SectionGraph, cancel_in_flight: u32) -> i32 {
    match graph.as_mut() {
        Some(graph) => {
            graph.reload_resources(cancel_in_flight != 0);
            OK
        }
        None => ERR_NULL_POINTER,
    }
}

/// Whether any section of the inclusive section-coordinate box was visited
/// by the latest camera search (1) or not (0).
///
/// # Safety
/// As [`mattmc_sodium_section_graph_add_column`].
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_box_visible(
    graph: *const SectionGraph,
    min_x: i32,
    min_y: i32,
    min_z: i32,
    max_x: i32,
    max_y: i32,
    max_z: i32,
) -> i32 {
    match graph.as_ref() {
        Some(graph) => i32::from(graph.box_visible([min_x, min_y, min_z], [max_x, max_y, max_z])),
        None => ERR_NULL_POINTER,
    }
}

/// Current build counts without a search: `needs_build << 32 | in_flight`.
///
/// # Safety
/// As [`mattmc_sodium_section_graph_add_column`].
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_build_counts(graph: *const SectionGraph) -> u64 {
    match graph.as_ref() {
        Some(graph) => ((graph.needs_build_count() as u64) << 32) | graph.in_flight_count() as u64,
        None => 0,
    }
}

/// Frozen's camera-pass selection. `matrix` is the column-major combined
/// projection * model-view matrix of the culling frustum and `camera` its
/// position. Records the search's bookkeeping into `frame`. Visited sections
/// are copied to `out` only when `capacity` is nonzero; when it is too small
/// it returns `ERR_CAPACITY` (the search is still recorded).
///
/// # Safety
/// `camera` addresses 3 doubles, `matrix` 16 floats, `out` `capacity`
/// records and `frame` one record.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_select(
    graph: *mut SectionGraph,
    camera: *const f64,
    matrix: *const f32,
    search_distance: f32,
    use_occlusion_culling: u32,
    out: *mut FfiSectionGraphVisit,
    capacity: u32,
    frame: *mut FfiSectionGraphFrame,
) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    if camera.is_null() || matrix.is_null() || frame.is_null() || (capacity != 0 && out.is_null()) {
        return ERR_NULL_POINTER;
    }
    let camera: [f64; 3] = std::ptr::read_unaligned(camera.cast());
    let matrix: [f32; 16] = std::ptr::read_unaligned(matrix.cast());
    let viewport = Viewport::new(Frustum::from_matrix(matrix), camera);
    let mut visits = std::mem::take(&mut graph.last_visits);
    visits.clear();
    graph.select_camera_sections(&viewport, search_distance, use_occlusion_culling != 0, |section| visits.push(section));
    graph.record_visits(&visits);
    let out = if capacity == 0 { &mut [][..] } else { std::slice::from_raw_parts_mut(out, capacity as usize) };
    for (slot, section) in out.iter_mut().zip(&visits) {
        *slot = FfiSectionGraphVisit {
            x: section.position[0],
            y: section.position[1],
            z: section.position[2],
            built: u32::from(section.info.is_some()),
            flags: section.info.map_or(0, |info| u32::from(info.flags)),
        };
    }
    let visit_count = visits.len();
    graph.last_visits = visits;
    let source = &graph.source;
    let globals = graph.global_block_entity_sections();
    std::ptr::write_unaligned(
        frame,
        FfiSectionGraphFrame {
            build_requests: source.build_requests.as_ptr(),
            build_request_count: source.build_requests.len() as u64,
            block_entity_sections: source.block_entity_sections.as_ptr(),
            block_entity_section_count: source.block_entity_sections.len() as u64,
            global_block_entity_sections: globals.as_ptr(),
            global_block_entity_section_count: globals.len() as u64,
            needs_build: graph.needs_build_count() as u64,
            in_flight: graph.in_flight_count() as u64,
            visit_count: visit_count as u64,
        },
    );
    if capacity != 0 && visit_count > out.len() {
        ERR_CAPACITY
    } else {
        OK
    }
}

/// One section's published layer meshes (solid, cutout, translucent); zero
/// keys are absent layers and all-zero keys clear the section.
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiSectionMeshes {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    /// Bit 0: the translucent layer is camera-sorted.
    pub flags: u32,
    pub keys: [u64; 3],
    pub generations: [u64; 3],
}

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiTerrainSelectionParams {
    pub camera: [f64; 3],
    pub depth_policies: [u32; 3],
    pub layer_ordinals: [u32; 3],
    pub shadow_candidates: u32,
    pub max_shadow_candidates: u32,
    pub receipts: u32,
    pub reserved: u32,
}

/// Views of the graph-owned selection; valid until the graph's next
/// selection, mesh update or destruction.
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiTerrainSelectionView {
    pub sections: *const FfiStaticTerrainSection,
    pub section_layers: *const SelectedLayer,
    pub section_count: u64,
    pub casters: *const FfiStaticTerrainShadowCaster,
    pub caster_count: u64,
    /// Animated sprite ids, each once.
    pub animated: *const u32,
    pub animated_count: u64,
    pub layer_probes: u64,
    pub layer_submissions: u64,
    pub fingerprint: u64,
}

/// Applies published mesh rows; `clear` first drops every row.
///
/// # Safety
/// `records` addresses `count` records (null only for 0).
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_set_meshes(
    graph: *mut SectionGraph,
    clear: u32,
    records: *const FfiSectionMeshes,
    count: u32,
) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    if count != 0 && records.is_null() {
        return ERR_NULL_POINTER;
    }
    if clear != 0 {
        graph.clear_meshes();
    }
    let records = if count == 0 { &[][..] } else { std::slice::from_raw_parts(records, count as usize) };
    for record in records {
        if record.flags & !1 != 0 {
            return ERR_INVALID_ARGUMENT;
        }
        graph.set_meshes(
            [record.x, record.y, record.z],
            Some(SectionMeshes {
                keys: record.keys,
                generations: record.generations,
                translucent_camera_sorted: record.flags & 1 != 0,
            }),
        );
    }
    OK
}

/// Builds the frame's static-terrain selection from the latest camera
/// selection (`terrain_selection`) and returns views of it.
///
/// # Safety
/// `params` and `out` address one record each.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_select_terrain(
    graph: *mut SectionGraph,
    params: *const FfiTerrainSelectionParams,
    out: *mut FfiTerrainSelectionView,
) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    if params.is_null() || out.is_null() {
        return ERR_NULL_POINTER;
    }
    let params = std::ptr::read_unaligned(params);
    if params.camera.iter().any(|value| !value.is_finite()) {
        return ERR_INVALID_ARGUMENT;
    }
    let params = TerrainSelectionParams {
        camera: params.camera,
        depth_policies: params.depth_policies,
        layer_ordinals: params.layer_ordinals,
        shadow_candidates: params.shadow_candidates != 0,
        max_shadow_candidates: params.max_shadow_candidates as usize,
        receipts: params.receipts != 0,
    };
    let visits = std::mem::take(&mut graph.last_visits);
    let mut terrain = std::mem::take(&mut graph.terrain);
    graph.select_terrain(&visits, &params, &mut terrain);
    graph.last_visits = visits;
    graph.terrain = terrain;
    let terrain = &graph.terrain;
    std::ptr::write_unaligned(
        out,
        FfiTerrainSelectionView {
            sections: terrain.sections.as_ptr(),
            section_layers: terrain.section_layers.as_ptr(),
            section_count: terrain.sections.len() as u64,
            casters: terrain.casters.as_ptr(),
            caster_count: terrain.casters.len() as u64,
            animated: terrain.animated.as_ptr(),
            animated_count: terrain.animated.len() as u64,
            layer_probes: terrain.receipts.layer_probes,
            layer_submissions: terrain.receipts.layer_submissions,
            fingerprint: terrain.receipts.fingerprint,
        },
    );
    OK
}
