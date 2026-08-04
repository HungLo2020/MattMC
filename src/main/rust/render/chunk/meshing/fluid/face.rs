//! Semantic fluid face and native quad construction.
//!
//! Face helpers convert top, bottom, side, overlay, and backface semantics into
//! stable native vertices. Lighting and packed normals are applied here so record
//! conversion and direct compact emission share the same ABI layout assumptions.

use super::*;

#[cfg(test)]
pub(in crate::render::chunk::meshing) fn fluid_semantic_face(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    facing: usize,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> (FluidFaceRecord, usize) {
    let mut record = fluid_semantic_record(
        state,
        block,
        facing,
        flip,
        face_kind,
        y_offset,
        heights,
        side_coords,
        uvs,
        color,
        ao,
        light,
    );
    let quad = fluid_face_record_to_quad(record)
        .expect("native fluid semantic face generated an invalid fluid face record");
    record.packed_normal = norm_i8_pack_from_quad(&quad);
    (record, facing)
}

pub(in crate::render::chunk::meshing) fn fluid_semantic_native_face(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    facing: usize,
    flip: bool,
    light_face: i32,
    light_flags: i32,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> NativeFluidFace {
    let mut vertices = fluid_semantic_vertices(
        state,
        block,
        false,
        face_kind,
        y_offset,
        heights,
        side_coords,
        uvs,
        color,
        ao,
        light,
    );
    apply_fluid_lighting(&mut vertices, block, state, light_face, light_flags, ao);
    let mut packed_normal = packed_fluid_normal(facing, &vertices);
    if flip {
        vertices = [vertices[0], vertices[3], vertices[2], vertices[1]];
        packed_normal = flip_packed_normal(packed_normal);
    }
    NativeFluidFace {
        vertices,
        block_emission: state.block_emission.clamp(0, 255) as u8,
        render_type: 1,
        ignore_mid_block: 0,
        block_id: choose_block_id(block.fluid_block_id, state.fluid_block_id),
        local_x: block.absolute_x,
        local_y: block.absolute_y,
        local_z: block.absolute_z,
        material_bits: state.fluid_material_bits,
        packed_normal,
        facing,
        fluid_type: state.fluid_type,
        face_kind,
    }
}

pub(in crate::render::chunk::meshing) fn apply_fluid_lighting(
    vertices: &mut [QuadVertex; 4],
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    light_face: i32,
    flags: i32,
    face_brightness: f32,
) {
    let mut light_quad = StaticModelQuadRecord {
        flags,
        light_face,
        cull_face: -1,
        normal_face: -1,
        has_ao: if state.fluid_type == FLUID_WATER {
            1
        } else {
            0
        },
        shade: 0,
        ..StaticModelQuadRecord::default()
    };

    for (index, vertex) in vertices.iter().enumerate() {
        light_quad.vertices[index] = StaticModelVertexRecord {
            x: vertex.x - block.local_x as f32,
            y: vertex.y - block.local_y as f32,
            z: vertex.z - block.local_z as f32,
            color: vertex.color,
            u: vertex.u,
            v: vertex.v,
            light: vertex.light,
        };
    }

    let light = native_quad_lighting(block, &light_quad, state);
    for (index, vertex) in vertices.iter_mut().enumerate() {
        vertex.ao = light.ao[index] * face_brightness;
        vertex.light = light.lm[index];
    }
}

pub(in crate::render::chunk::meshing) fn fluid_semantic_record_diag(
    block: &NativeSectionBlockRecord,
    phase: &str,
    state: NativeMeshingState,
    facing: usize,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
    packed_normal: i32,
) {
    if !native_fluid_diag_enabled() {
        return;
    }
    let mut record = fluid_semantic_record(
        state,
        block,
        facing,
        flip,
        face_kind,
        y_offset,
        heights,
        side_coords,
        uvs,
        color,
        ao,
        light,
    );
    record.packed_normal = packed_normal;
    fluid_record_diag(block, phase, &record, facing);
}

pub(in crate::render::chunk::meshing) fn fluid_semantic_record(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    facing: usize,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> FluidFaceRecord {
    let _ = facing;
    FluidFaceRecord {
        packed_normal: 0,
        material_bits: state.fluid_material_bits,
        block_emission: state.block_emission,
        render_type: 1,
        ignore_mid_block: 0,
        block_id: choose_block_id(block.fluid_block_id, state.fluid_block_id),
        local_x: block.absolute_x,
        local_y: block.absolute_y,
        local_z: block.absolute_z,
        face_kind,
        flip: if flip { 1 } else { 0 },
        origin_x: block.local_x as f32,
        origin_y: block.local_y as f32,
        origin_z: block.local_z as f32,
        y_offset,
        heights,
        side_coords,
        uvs: [
            uvs[0].0, uvs[0].1, uvs[1].0, uvs[1].1, uvs[2].0, uvs[2].1, uvs[3].0, uvs[3].1,
        ],
        colors: [color; 4],
        aos: [ao; 4],
        lights: [light; 4],
        primitive_kind: if state.fluid_type == FLUID_WATER {
            TERRAIN_PRIMITIVE_BUILTIN_WATER
        } else {
            TERRAIN_PRIMITIVE_UNSUPPORTED_FLUID
        },
    }
}

pub(in crate::render::chunk::meshing) fn fluid_semantic_vertices(
    _state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> [QuadVertex; 4] {
    let origin_x = block.local_x as f32;
    let origin_y = block.local_y as f32;
    let origin_z = block.local_z as f32;
    let mut vertices = match face_kind {
        FLUID_FACE_TOP_NE_SW => [
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[3],
                origin_z,
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + heights[0],
                origin_z,
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + heights[1],
                origin_z + 1.0,
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[2],
                origin_z + 1.0,
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        FLUID_FACE_TOP_NW_SE => [
            fluid_native_vertex(
                origin_x,
                origin_y + heights[0],
                origin_z,
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + heights[1],
                origin_z + 1.0,
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[2],
                origin_z + 1.0,
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[3],
                origin_z,
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        FLUID_FACE_BOTTOM => [
            fluid_native_vertex(
                origin_x,
                origin_y + y_offset,
                origin_z + 1.0,
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + y_offset,
                origin_z,
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + y_offset,
                origin_z,
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + y_offset,
                origin_z + 1.0,
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        FLUID_FACE_SIDE => [
            fluid_native_vertex(
                origin_x + side_coords[2],
                origin_y + heights[1],
                origin_z + side_coords[3],
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + side_coords[2],
                origin_y + y_offset,
                origin_z + side_coords[3],
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + side_coords[0],
                origin_y + y_offset,
                origin_z + side_coords[1],
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + side_coords[0],
                origin_y + heights[0],
                origin_z + side_coords[1],
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        _ => [QuadVertex::default(); 4],
    };

    if flip {
        vertices = [vertices[0], vertices[3], vertices[2], vertices[1]];
    }

    vertices
}

pub(in crate::render::chunk::meshing) fn packed_fluid_normal(
    facing: usize,
    vertices: &[QuadVertex; 4],
) -> i32 {
    match facing {
        MODEL_QUAD_FACING_POS_X => 0x0000007f,
        MODEL_QUAD_FACING_POS_Y => 0x00007f00,
        MODEL_QUAD_FACING_POS_Z => 0x007f0000,
        MODEL_QUAD_FACING_NEG_X => 0x00000081,
        MODEL_QUAD_FACING_NEG_Y => 0x00008100,
        MODEL_QUAD_FACING_NEG_Z => 0x00810000,
        _ => norm_i8_pack_from_vertices(vertices),
    }
}

pub(in crate::render::chunk::meshing) fn flipped_fluid_back_face(
    front: NativeFluidFace,
    facing: usize,
) -> NativeFluidFace {
    let mut back = front;
    back.vertices = [
        front.vertices[0],
        front.vertices[3],
        front.vertices[2],
        front.vertices[1],
    ];
    let packed_normal = if facing < MODEL_QUAD_FACING_COUNT - 1 {
        flip_packed_normal(packed_fluid_normal(facing, &front.vertices))
    } else {
        flip_packed_normal(front.packed_normal)
    };
    back.packed_normal = packed_normal;
    back.facing = facing;
    back
}

pub(in crate::render::chunk::meshing) fn flip_packed_normal(normal: i32) -> i32 {
    let x = normal as u32 & 0xff;
    let y = (normal as u32 >> 8) & 0xff;
    let z = (normal as u32 >> 16) & 0xff;
    let flipped = ((!x).wrapping_add(1) & 0xff)
        | (((!y).wrapping_add(1) & 0xff) << 8)
        | (((!z).wrapping_add(1) & 0xff) << 16);
    flipped as i32
}

pub(in crate::render::chunk::meshing) fn fluid_native_vertex(
    x: f32,
    y: f32,
    z: f32,
    vertex: usize,
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> QuadVertex {
    QuadVertex {
        x,
        y,
        z,
        color,
        ao,
        u: uvs[vertex].0,
        v: uvs[vertex].1,
        light,
    }
}
