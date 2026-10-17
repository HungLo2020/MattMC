//! C exports of static-terrain publication (`worldrender/terrain/publication.rs`)
//! for `RustTerrainPublication`, and the one place that applies published rows
//! to the camera section graph (`chunk::SectionGraph`).

use crate::render::chunk::section_graph::SectionGraph;
use crate::render::chunk::terrain_selection::SectionMeshes;
use crate::render::worldrender::terrain::publication::{
    publication, section_coordinates, Changes, Collision, Layer, Row, SLOTS,
};

const OK: i32 = 0;
const COLLISION: i32 = 1;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;

/// Flags bit 1: the translucent layer asks for camera-sorted quads.
const FLAG_CAMERA_SORTED: i64 = 1;

fn layer(section: i64, slot: i64, key: i64, generation: i64, flags: i64) -> Option<Layer> {
    let slot = usize::try_from(slot).ok().filter(|slot| *slot < SLOTS)?;
    (key != 0 && flags & !FLAG_CAMERA_SORTED == 0).then_some(Layer {
        section,
        slot,
        key: key as u64,
        generation: generation as u64,
        camera_sorted: flags & FLAG_CAMERA_SORTED != 0,
    })
}

/// Writes [key, section, slot] of the layer holding a colliding key.
unsafe fn write_collision(out: *mut i64, collision: Collision) -> i32 {
    if !out.is_null() {
        std::slice::from_raw_parts_mut(out, 3).copy_from_slice(&[collision.key as i64, collision.section, collision.slot as i64]);
    }
    COLLISION
}

/// Publishes a section layer's mesh. Returns 0, or 1 when another section
/// layer already publishes `key` (then `collision` receives [key, section,
/// slot] and nothing changes).
///
/// # Safety
/// `collision` is null or addresses 3 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_publish_layer(
    section: i64,
    slot: i32,
    key: i64,
    generation: i64,
    flags: i32,
    collision: *mut i64,
) -> i32 {
    let Some(layer) = layer(section, i64::from(slot), key, generation, i64::from(flags)) else {
        return ERR_INVALID_ARGUMENT;
    };
    match publication().publish(layer) {
        Ok(()) => OK,
        Err(found) => write_collision(collision, found),
    }
}

#[no_mangle]
pub extern "C" fn mattmc_terrain_remove_layer(section: i64, slot: i32) -> i32 {
    match usize::try_from(slot).ok().filter(|slot| *slot < SLOTS) {
        Some(slot) => {
            publication().remove(section, slot);
            OK
        }
        None => ERR_INVALID_ARGUMENT,
    }
}

/// Replaces every row with `count` layers of (section, slot, key,
/// generation, flags). Returns 0, or 1 on a duplicate key (then `collision`
/// receives the first holder and nothing changes).
///
/// # Safety
/// `layers` addresses `count` × 5 longs; `collision` is null or addresses 3.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_publication_replace(layers: *const i64, count: i32, collision: *mut i64) -> i32 {
    if count > 0 && layers.is_null() {
        return ERR_NULL_POINTER;
    }
    let raw = if count <= 0 { &[][..] } else { std::slice::from_raw_parts(layers, count as usize * 5) };
    let Some(layers) = raw.chunks_exact(5).map(|l| layer(l[0], l[1], l[2], l[3], l[4])).collect::<Option<Vec<_>>>() else {
        return ERR_INVALID_ARGUMENT;
    };
    match publication().replace(&layers) {
        Ok(()) => OK,
        Err(found) => write_collision(collision, found),
    }
}

#[no_mangle]
pub extern "C" fn mattmc_terrain_publication_clear() {
    publication().clear();
}

fn meshes(row: Row) -> SectionMeshes {
    SectionMeshes { keys: row.keys, generations: row.generations, translucent_camera_sorted: row.translucent_camera_sorted }
}

/// Applies the rows changed since the last sync to `graph`; after a reset,
/// or with `republish` for a new graph, every row. Returns the number of
/// rows applied.
///
/// # Safety
/// `graph` is a live section graph.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_publication_sync_graph(graph: *mut SectionGraph, republish: i32) -> i32 {
    let Some(graph) = graph.as_mut() else {
        return ERR_NULL_POINTER;
    };
    // The publication lock covers the whole apply, so a reset and its rows
    // reach the graph together.
    let mut publication = publication();
    match publication.take_changes(republish != 0) {
        Changes::Reset(rows) => {
            graph.clear_meshes();
            for &(section, row) in &rows {
                graph.set_meshes(section_coordinates(section), Some(meshes(row)));
            }
            rows.len() as i32
        }
        Changes::Rows(rows) => {
            for &(section, row) in &rows {
                graph.set_meshes(section_coordinates(section), row.map(meshes));
            }
            rows.len() as i32
        }
    }
}

/// Writes each of `count` sections' row as [solid key, generation, cutout
/// key, generation, translucent key, generation] (zeros for no layer).
///
/// # Safety
/// `sections` addresses `count` longs, `out` `count` × 6.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_publication_rows(sections: *const i64, count: i32, out: *mut i64) -> i32 {
    if count <= 0 {
        return OK;
    }
    if sections.is_null() || out.is_null() {
        return ERR_NULL_POINTER;
    }
    let sections = std::slice::from_raw_parts(sections, count as usize);
    let out = std::slice::from_raw_parts_mut(out, count as usize * 6);
    let publication = publication();
    for (section, slots) in sections.iter().zip(out.chunks_exact_mut(6)) {
        let row = publication.row(*section);
        for slot in 0..SLOTS {
            slots[slot * 2] = row.keys[slot] as i64;
            slots[slot * 2 + 1] = row.generations[slot] as i64;
        }
    }
    OK
}

#[cfg(test)]
mod tests {
    use super::*;

    fn section(x: i64, y: i64, z: i64) -> i64 {
        ((x & 0x3F_FFFF) << 42) | (y & 0xF_FFFF) | ((z & 0x3F_FFFF) << 20)
    }

    fn graph_row(graph: &SectionGraph, position: [i32; 3]) -> Option<(Vec<u64>, Vec<u64>, bool)> {
        graph
            .published_meshes(position)
            .map(|meshes| (meshes.keys.to_vec(), meshes.generations.to_vec(), meshes.translucent_camera_sorted))
    }

    /// The only test that uses the process-wide publication.
    #[test]
    fn published_rows_reach_the_section_graph_through_the_exports() {
        unsafe {
            mattmc_terrain_publication_clear();
            let mut graph = SectionGraph::new(-4, 19);
            let a = section(-3, 4, 9);
            let mut collision = [0i64; 3];
            assert_eq!(mattmc_terrain_publish_layer(a, 0, 101, 1, 0, collision.as_mut_ptr()), OK);
            assert_eq!(mattmc_terrain_publish_layer(a, 2, 103, 1, 1, collision.as_mut_ptr()), OK);
            assert_eq!(mattmc_terrain_publish_layer(section(0, 0, 0), 1, 101, 2, 0, collision.as_mut_ptr()), COLLISION);
            assert_eq!(collision, [101, a, 0]);
            assert_eq!(mattmc_terrain_publish_layer(a, 3, 104, 1, 0, collision.as_mut_ptr()), ERR_INVALID_ARGUMENT);
            // A cleared publication resets the graph (first sync).
            assert_eq!(mattmc_terrain_publication_sync_graph(&mut graph, 0), 1);
            assert_eq!(graph_row(&graph, [-3, 4, 9]), Some((vec![101, 0, 103], vec![1, 0, 1], true)));
            // Later syncs carry only changes; removing the last layer clears the section.
            assert_eq!(mattmc_terrain_remove_layer(a, 2), OK);
            assert_eq!(mattmc_terrain_publication_sync_graph(&mut graph, 0), 1);
            assert_eq!(graph_row(&graph, [-3, 4, 9]), Some((vec![101, 0, 0], vec![1, 0, 0], false)));
            assert_eq!(mattmc_terrain_remove_layer(a, 0), OK);
            mattmc_terrain_publication_sync_graph(&mut graph, 0);
            assert_eq!(graph_row(&graph, [-3, 4, 9]), None);
            // A new graph republishes every row.
            let b = section(5, -2, 1);
            assert_eq!(mattmc_terrain_publish_layer(b, 1, 201, 7, 0, collision.as_mut_ptr()), OK);
            mattmc_terrain_publication_sync_graph(&mut graph, 0);
            let mut fresh = SectionGraph::new(-4, 19);
            assert_eq!(mattmc_terrain_publication_sync_graph(&mut fresh, 1), 1);
            assert_eq!(graph_row(&fresh, [5, -2, 1]), Some((vec![0, 201, 0], vec![0, 7, 0], false)));
            // Rows are read back in one call.
            let mut rows = [0i64; 12];
            assert_eq!(mattmc_terrain_publication_rows([b, a].as_ptr(), 2, rows.as_mut_ptr()), OK);
            assert_eq!(rows, [0, 0, 201, 7, 0, 0, 0, 0, 0, 0, 0, 0]);
            // A reload replace swaps every row and resets the graph.
            let layers = [a, 0, 301, 9, 0, a, 2, 302, 9, 1];
            assert_eq!(mattmc_terrain_publication_replace(layers.as_ptr(), 2, collision.as_mut_ptr()), OK);
            mattmc_terrain_publication_sync_graph(&mut graph, 0);
            assert_eq!(graph_row(&graph, [5, -2, 1]), None);
            assert_eq!(graph_row(&graph, [-3, 4, 9]), Some((vec![301, 0, 302], vec![9, 0, 9], true)));
            mattmc_terrain_publication_clear();
            assert_eq!(mattmc_terrain_publication_sync_graph(std::ptr::null_mut(), 0), ERR_NULL_POINTER);
        }
    }
}
