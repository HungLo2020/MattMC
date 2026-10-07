//! C export of the terrain mesh intake (`worldrender/terrain/intake.rs`):
//! decodes a section layer's compact vertices into the world-mesh vertex ABI.

use crate::render::bridge::abi::FfiWorldMeshVertex;
use crate::render::scene::mesh::WorldMeshVertex;
use crate::render::worldrender::terrain::intake::{
    decode_compact_terrain_vertices, FfiCompactTerrainDecodeParams, FfiCompactTerrainDecodeStats,
};

fn abi_vertex(vertex: &WorldMeshVertex) -> FfiWorldMeshVertex {
    FfiWorldMeshVertex {
        byte_size: std::mem::size_of::<FfiWorldMeshVertex>() as u32,
        color_argb: vertex.color_argb,
        normal_packed: vertex.normal_packed,
        light: vertex.light,
        x: vertex.position[0],
        y: vertex.position[1],
        z: vertex.position[2],
        u: vertex.uv[0],
        v: vertex.uv[1],
        atlas_u: vertex.shader_atlas_uv[0],
        atlas_v: vertex.shader_atlas_uv[1],
        shader_block_id: vertex.shader_block_id,
        shader_material_type: vertex.shader_material_type,
        terrain_material_bits: vertex.terrain_material_bits,
        mid_block_packed: vertex.mid_block_packed,
    }
}

/// Decodes into `out` (`capacity` vertices) and `stats`. Returns the vertex
/// count, or -2 for invalid input (including insufficient capacity).
///
/// # Safety
/// Each pointer addresses its stated element count; `params` and `stats`
/// one record each.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_decode_compact_vertices(
    buffer: *const u8,
    buffer_len: u64,
    params: *const FfiCompactTerrainDecodeParams,
    segments: *const i32,
    segment_ints: u32,
    metadata: *const i32,
    metadata_ints: u32,
    out: *mut FfiWorldMeshVertex,
    capacity: u32,
    stats: *mut FfiCompactTerrainDecodeStats,
) -> i32 {
    if buffer.is_null() || params.is_null() || stats.is_null() || (capacity != 0 && out.is_null())
        || (segment_ints != 0 && segments.is_null()) || (metadata_ints != 0 && metadata.is_null())
    {
        return -2;
    }
    let slice = |pointer: *const i32, count: u32| {
        if count == 0 { &[][..] } else { std::slice::from_raw_parts(pointer, count as usize) }
    };
    let buffer = std::slice::from_raw_parts(buffer, buffer_len as usize);
    let params = std::ptr::read_unaligned(params);
    thread_local! {
        static SCRATCH: std::cell::RefCell<Vec<WorldMeshVertex>> = const { std::cell::RefCell::new(Vec::new()) };
    }
    SCRATCH.with(|scratch| {
        let mut vertices = scratch.borrow_mut();
        let mut decoded_stats = FfiCompactTerrainDecodeStats::default();
        match decode_compact_terrain_vertices(
            buffer,
            &params,
            slice(segments, segment_ints),
            slice(metadata, metadata_ints),
            &mut vertices,
            &mut decoded_stats,
        ) {
            Ok(count) if count <= capacity as usize => {
                for (index, vertex) in vertices.iter().enumerate() {
                    out.add(index).write(abi_vertex(vertex));
                }
                std::ptr::write_unaligned(stats, decoded_stats);
                count as i32
            }
            _ => -2,
        }
    })
}
