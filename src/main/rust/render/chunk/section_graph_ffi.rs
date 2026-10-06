//! C ABI for the terrain section graph.
//!
//! The graph is a standalone handle owned by Java's terrain source and used
//! only from the render thread. It is deliberately not part of a VulkanicGAL
//! bridge context: querying visibility must never join a pipelined frame.

use super::section_graph::{Frustum, SectionGraph, SectionInfo, Viewport};

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
    graph.select_camera_sections(&viewport, search_distance, use_occlusion_culling != 0, |section| {
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
    *out_count = count as u32;
    if count > out.len() {
        ERR_CAPACITY
    } else {
        OK
    }
}
