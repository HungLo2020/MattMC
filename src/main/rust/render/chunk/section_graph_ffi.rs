//! C ABI for the terrain section graph.
//!
//! The graph is a standalone handle owned by Java's terrain source and used
//! only from the render thread. It is deliberately not part of a VulkanicGAL
//! bridge context: querying visibility must never join a pipelined frame.

use super::section_graph::{Frustum, SectionGraph, SectionInfo, Viewport};
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

/// Applies column readiness changes (`[x, z]` pairs; removals first, as
/// `ChunkTracker.forEachEvent` delivers them) and section build information.
///
/// # Safety
/// Pointers must address the stated element counts (null only for 0).
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_update(
    graph: *mut SectionGraph,
    removed_columns: *const i32,
    removed_count: u32,
    added_columns: *const i32,
    added_count: u32,
    infos: *const FfiSectionGraphInfo,
    info_count: u32,
) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    let slice = |pointer: *const i32, count: u32| -> Option<&[i32]> {
        if count == 0 {
            Some(&[])
        } else if pointer.is_null() {
            None
        } else {
            Some(std::slice::from_raw_parts(pointer, count as usize * 2))
        }
    };
    let (Some(removed), Some(added)) = (slice(removed_columns, removed_count), slice(added_columns, added_count))
    else {
        return ERR_NULL_POINTER;
    };
    if info_count != 0 && infos.is_null() {
        return ERR_NULL_POINTER;
    }
    for column in removed.chunks_exact(2) {
        graph.remove_column(column[0], column[1]);
    }
    for column in added.chunks_exact(2) {
        graph.add_column(column[0], column[1]);
    }
    let infos = if info_count == 0 { &[][..] } else { std::slice::from_raw_parts(infos, info_count as usize) };
    for info in infos {
        if info.flags > 0xFF || info.reserved != 0 {
            return ERR_INVALID_ARGUMENT;
        }
        let value = (info.built != 0).then_some(SectionInfo { flags: info.flags as u8, visibility: info.visibility });
        graph.set_info([info.x, info.y, info.z], value);
    }
    OK
}

/// Frozen's camera-pass selection. `matrix` is the column-major combined
/// projection * model-view matrix of the culling frustum and `camera` its
/// position. Writes visited sections in visit order; when `capacity` is too
/// small it returns `ERR_CAPACITY` with the required count in `out_count`.
///
/// # Safety
/// `camera` addresses 3 doubles, `matrix` 16 floats, `out` `capacity`
/// records and `out_count` one u32.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_graph_select(
    graph: *mut SectionGraph,
    camera: *const f64,
    matrix: *const f32,
    search_distance: f32,
    use_occlusion_culling: u32,
    out: *mut FfiSectionGraphVisit,
    capacity: u32,
    out_count: *mut u32,
) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    if camera.is_null() || matrix.is_null() || out_count.is_null() || (capacity != 0 && out.is_null()) {
        return ERR_NULL_POINTER;
    }
    let camera: [f64; 3] = std::ptr::read_unaligned(camera.cast());
    let matrix: [f32; 16] = std::ptr::read_unaligned(matrix.cast());
    let viewport = Viewport::new(Frustum::from_matrix(matrix), camera);
    let out = if capacity == 0 { &mut [][..] } else { std::slice::from_raw_parts_mut(out, capacity as usize) };
    let mut count = 0usize;
    let mut visits = std::mem::take(&mut graph.last_visits);
    visits.clear();
    graph.select_camera_sections(&viewport, search_distance, use_occlusion_culling != 0, |section| {
        visits.push(section);
        if let Some(slot) = out.get_mut(count) {
            *slot = FfiSectionGraphVisit {
                x: section.position[0],
                y: section.position[1],
                z: section.position[2],
                built: u32::from(section.info.is_some()),
                flags: section.info.map_or(0, |info| u32::from(info.flags)),
            };
        }
        count += 1;
    });
    graph.last_visits = visits;
    *out_count = count as u32;
    if count > out.len() {
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
    /// `[x, y, z]` section positions.
    pub animated: *const i32,
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
            animated: terrain.animated.as_ptr().cast(),
            animated_count: terrain.animated.len() as u64,
            layer_probes: terrain.receipts.layer_probes,
            layer_submissions: terrain.receipts.layer_submissions,
            fingerprint: terrain.receipts.fingerprint,
        },
    );
    OK
}
