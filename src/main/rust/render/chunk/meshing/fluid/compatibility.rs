//! Compatibility conversion from legacy fluid face records to native quads.
//!
//! These helpers keep benchmark and replay-only record paths alive while ordinary
//! production fluid meshing emits through semantic native faces and compact
//! encoding. The record layout is ABI-stable and shared with Java tests.

use super::*;

pub(in crate::render::chunk::meshing) fn fluid_face_record_to_quad(
    record: FluidFaceRecord,
) -> Result<NativeQuad, i32> {
    let mut vertices = match record.face_kind {
        // Top face, diagonal from north-east to south-west.
        0 => [
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[3],
                record.origin_z,
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[0],
                record.origin_z,
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[1],
                record.origin_z + 1.0,
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[2],
                record.origin_z + 1.0,
                3,
                record,
            ),
        ],
        // Top face, diagonal from north-west to south-east.
        1 => [
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[0],
                record.origin_z,
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[1],
                record.origin_z + 1.0,
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[2],
                record.origin_z + 1.0,
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[3],
                record.origin_z,
                3,
                record,
            ),
        ],
        // Bottom face.
        2 => [
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.y_offset,
                record.origin_z + 1.0,
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.y_offset,
                record.origin_z,
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.y_offset,
                record.origin_z,
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.y_offset,
                record.origin_z + 1.0,
                3,
                record,
            ),
        ],
        // Horizontal side face. side_coords = x1,z1,x2,z2 and heights = c1,c2,...
        3 => [
            fluid_vertex(
                record.origin_x + record.side_coords[2],
                record.origin_y + record.heights[1],
                record.origin_z + record.side_coords[3],
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[2],
                record.origin_y + record.y_offset,
                record.origin_z + record.side_coords[3],
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[0],
                record.origin_y + record.y_offset,
                record.origin_z + record.side_coords[1],
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[0],
                record.origin_y + record.heights[0],
                record.origin_z + record.side_coords[1],
                3,
                record,
            ),
        ],
        _ => return Err(ERR_INVALID_ARGUMENT),
    };

    if record.flip != 0 {
        vertices = [vertices[0], vertices[3], vertices[2], vertices[1]];
    }

    Ok(NativeQuad {
        vertices,
        block_emission: record.block_emission.clamp(0, 255) as u8,
        render_type: record.render_type.clamp(0, 255) as u8,
        ignore_mid_block: if record.ignore_mid_block != 0 { 1 } else { 0 },
        _padding: 0,
        block_id: record.block_id,
        local_x: record.local_x,
        local_y: record.local_y,
        local_z: record.local_z,
        material_bits: record.material_bits,
    })
}

pub(in crate::render::chunk::meshing) fn fluid_vertex(
    x: f32,
    y: f32,
    z: f32,
    vertex: usize,
    record: FluidFaceRecord,
) -> QuadVertex {
    QuadVertex {
        x,
        y,
        z,
        color: record.colors[vertex],
        ao: record.aos[vertex],
        u: record.uvs[vertex * 2],
        v: record.uvs[vertex * 2 + 1],
        light: record.lights[vertex],
    }
}
