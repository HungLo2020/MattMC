use super::*;

pub(super) unsafe fn native_quad_mut(address: u64) -> Result<&'static mut NativeQuad, i32> {
    if address == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(&mut *(address as *mut NativeQuad))
}

pub(super) unsafe fn native_quad(address: u64) -> Result<&'static NativeQuad, i32> {
    if address == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(&*(address as *const NativeQuad))
}

pub(super) unsafe fn write_native_quad_metadata(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
) -> i32 {
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    quad.block_emission = block_emission as u8;
    quad.render_type = render_type as u8;
    quad.ignore_mid_block = if ignore_mid_block != 0 { 1 } else { 0 };
    quad._padding = 0;
    quad.block_id = block_id;
    quad.local_x = local_x;
    quad.local_y = local_y;
    quad.local_z = local_z;
    quad.material_bits = material_bits;
    OK
}

pub(super) unsafe fn write_native_quad_vertex(
    quad_address: u64,
    vertex_index: i32,
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
) -> i32 {
    let vertex_index = match usize::try_from(vertex_index) {
        Ok(value) if value < 4 => value,
        _ => return ERR_INVALID_ARGUMENT,
    };
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    quad.vertices[vertex_index] = QuadVertex {
        x,
        y,
        z,
        color,
        ao,
        u,
        v,
        light,
    };
    OK
}

pub(super) unsafe fn write_native_quad(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
    x0: f32,
    y0: f32,
    z0: f32,
    color0: i32,
    ao0: f32,
    u0: f32,
    v0: f32,
    light0: i32,
    x1: f32,
    y1: f32,
    z1: f32,
    color1: i32,
    ao1: f32,
    u1: f32,
    v1: f32,
    light1: i32,
    x2: f32,
    y2: f32,
    z2: f32,
    color2: i32,
    ao2: f32,
    u2: f32,
    v2: f32,
    light2: i32,
    x3: f32,
    y3: f32,
    z3: f32,
    color3: i32,
    ao3: f32,
    u3: f32,
    v3: f32,
    light3: i32,
) -> i32 {
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *quad = NativeQuad {
        vertices: [
            QuadVertex {
                x: x0,
                y: y0,
                z: z0,
                color: color0,
                ao: ao0,
                u: u0,
                v: v0,
                light: light0,
            },
            QuadVertex {
                x: x1,
                y: y1,
                z: z1,
                color: color1,
                ao: ao1,
                u: u1,
                v: v1,
                light: light1,
            },
            QuadVertex {
                x: x2,
                y: y2,
                z: z2,
                color: color2,
                ao: ao2,
                u: u2,
                v: v2,
                light: light2,
            },
            QuadVertex {
                x: x3,
                y: y3,
                z: z3,
                color: color3,
                ao: ao3,
                u: u3,
                v: v3,
                light: light3,
            },
        ],
        block_emission: block_emission as u8,
        render_type: render_type as u8,
        ignore_mid_block: if ignore_mid_block != 0 { 1 } else { 0 },
        _padding: 0,
        block_id,
        local_x,
        local_y,
        local_z,
        material_bits,
    };
    OK
}

pub(super) unsafe fn native_quad_position(
    quad_address: u64,
    vertex_index: i32,
    component: i32,
) -> f32 {
    let Ok(vertex_index) = usize::try_from(vertex_index) else {
        return 0.0;
    };
    if vertex_index >= 4 {
        return 0.0;
    }

    let Ok(quad) = native_quad(quad_address) else {
        return 0.0;
    };
    let vertex = quad.vertices[vertex_index];

    match component {
        0 => vertex.x,
        1 => vertex.y,
        2 => vertex.z,
        _ => 0.0,
    }
}
