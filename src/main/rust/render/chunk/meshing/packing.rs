use super::*;

pub(super) unsafe fn encode(
    input_address: u64,
    vertex_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if vertex_count < 0 || output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if vertex_count == 0 {
        return OK;
    }
    if input_address == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let vertex_count = match usize::try_from(vertex_count) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if vertex_count % 4 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let quad_count = vertex_count / 4;
    let output_len = match vertex_count.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let input = slice::from_raw_parts(input_address as *const NativeQuad, quad_count);
    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);

    for (quad_index, quad) in input.iter().enumerate() {
        let start = quad_index * 4 * format.vertex_stride;
        let end = start + 4 * format.vertex_stride;
        encode_quad(quad, &mut output[start..end], format);
    }

    OK
}

pub(super) unsafe fn encode_scattered(
    input_address: u64,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if update_count < 0 || output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if update_count == 0 {
        return OK;
    }
    if input_address == 0 || output_vertex_offsets.is_null() || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let update_count = match usize::try_from(update_count) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_capacity);
    let input = slice::from_raw_parts(input_address as *const NativeQuad, update_count);
    let output_vertex_offsets = slice::from_raw_parts(output_vertex_offsets, update_count);
    let quad_byte_len = match 4usize.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };

    for (quad, vertex_offset) in input.iter().zip(output_vertex_offsets.iter()) {
        let vertex_offset = match usize::try_from(*vertex_offset) {
            Ok(value) => value,
            Err(_) => return ERR_INVALID_ARGUMENT,
        };
        if vertex_offset % 4 != 0 {
            return ERR_INVALID_ARGUMENT;
        }

        let byte_offset = match vertex_offset.checked_mul(format.vertex_stride) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let byte_end = match byte_offset.checked_add(quad_byte_len) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let Some(output_slice) = output.get_mut(byte_offset..byte_end) else {
            return ERR_CAPACITY;
        };

        encode_quad(quad, output_slice, format);
    }

    OK
}

pub(super) fn encode_quad(quad: &NativeQuad, output: &mut [u8], format: NativeFormat) {
    if is_compact_fast_format(format) {
        encode_quad_compact(quad, output, format.section_index);
        return;
    }

    let vertices = &quad.vertices;
    let tex_centroid_u = vertices.iter().map(|vertex| vertex.u).sum::<f32>() * 0.25;
    let tex_centroid_v = vertices.iter().map(|vertex| vertex.v).sum::<f32>() * 0.25;
    let mid_uv = encode_old_uv(tex_centroid_u, tex_centroid_v);
    let (normal, tangent, packed_normal, packed_tangent) = if format.normal_offset != 0 {
        let normal = compute_face_normal(vertices);
        let tangent = compute_tangent_for_quad(normal, vertices);
        let normal_oct = octahedron_encode(normal);
        let tangent_oct = tangent_encode(tangent);
        (
            normal,
            tangent,
            pack_norm_i8(normal_oct.0, normal_oct.1, tangent_oct.0, tangent_oct.1),
            pack_norm_i8(tangent.0, tangent.1, tangent.2, tangent.3),
        )
    } else {
        (
            (0.0, 1.0, 0.0),
            (0.0, 1.0, 0.0, 1.0),
            default_normal(),
            pack_norm_i8(0.0, 1.0, 0.0, 1.0),
        )
    };
    let _ = (normal, tangent);

    for (index, vertex) in vertices.iter().enumerate() {
        let ptr = &mut output[index * format.vertex_stride..(index + 1) * format.vertex_stride];
        let x = quantize_position(vertex.x);
        let y = quantize_position(vertex.y);
        let z = quantize_position(vertex.z);
        let u = encode_texture(tex_centroid_u, vertex.u);
        let v = encode_texture(tex_centroid_v, vertex.v);
        let light = encode_light(vertex.light);

        put_i32(ptr, 0, pack_position_hi(x, y, z));
        put_i32(ptr, 4, pack_position_lo(x, y, z));
        put_i32(
            ptr,
            8,
            encode_color(vertex.color, vertex.ao, format.separate_ao),
        );
        put_i32(ptr, 12, pack_texture(u, v));
        put_i32(
            ptr,
            16,
            pack_light_and_data(light, quad.material_bits, format.section_index),
        );

        if format.block_id_offset != 0 {
            put_i32(ptr, format.block_id_offset, pack_block_id(quad));
        }
        if format.mid_block_offset != 0 {
            let mid_block = if quad.ignore_mid_block != 0 {
                0
            } else {
                compute_mid_block(vertex, quad)
            };
            put_i32(ptr, format.mid_block_offset, mid_block);
            ptr[format.mid_block_offset + 3] = quad.block_emission;
        }
        if format.mid_uv_offset != 0 {
            put_i32(ptr, format.mid_uv_offset, mid_uv);
        }
        if format.normal_offset != 0 {
            put_i32(ptr, format.normal_offset, packed_normal);
        }
        if format.tangent_offset != 0 {
            put_i32(ptr, format.tangent_offset, packed_tangent);
        }
    }
}

#[inline(always)]
pub(super) fn is_compact_fast_format(format: NativeFormat) -> bool {
    format.vertex_stride == COMPACT_VERTEX_STRIDE as usize
        && format.block_id_offset == 0
        && format.normal_offset == 0
        && format.tangent_offset == 0
        && format.mid_uv_offset == 0
        && format.mid_block_offset == 0
        && !format.separate_ao
}

#[inline(always)]
fn encode_quad_compact(quad: &NativeQuad, output: &mut [u8], section_index: i32) {
    encode_compact_quad_vertices(
        &quad.vertices,
        quad.material_bits,
        section_index,
        output,
    );
}

#[inline(always)]
pub(super) fn encode_compact_quad_vertices(
    vertices: &[QuadVertex; 4],
    material_bits: i32,
    section_index: i32,
    output: &mut [u8],
) {
    let tex_centroid_u = (vertices[0].u + vertices[1].u + vertices[2].u + vertices[3].u) * 0.25;
    let tex_centroid_v = (vertices[0].v + vertices[1].v + vertices[2].v + vertices[3].v) * 0.25;
    let material_section = ((material_bits & 0xff) << 16) | ((section_index & 0xff) << 24);

    encode_compact_vertex(
        vertices[0],
        &mut output[0..COMPACT_VERTEX_STRIDE as usize],
        tex_centroid_u,
        tex_centroid_v,
        material_section,
    );
    encode_compact_vertex(
        vertices[1],
        &mut output[COMPACT_VERTEX_STRIDE as usize..(COMPACT_VERTEX_STRIDE * 2) as usize],
        tex_centroid_u,
        tex_centroid_v,
        material_section,
    );
    encode_compact_vertex(
        vertices[2],
        &mut output[(COMPACT_VERTEX_STRIDE * 2) as usize..(COMPACT_VERTEX_STRIDE * 3) as usize],
        tex_centroid_u,
        tex_centroid_v,
        material_section,
    );
    encode_compact_vertex(
        vertices[3],
        &mut output[(COMPACT_VERTEX_STRIDE * 3) as usize..(COMPACT_VERTEX_STRIDE * 4) as usize],
        tex_centroid_u,
        tex_centroid_v,
        material_section,
    );
}

#[inline(always)]
fn encode_compact_vertex(
    vertex: QuadVertex,
    output: &mut [u8],
    tex_centroid_u: f32,
    tex_centroid_v: f32,
    material_section: i32,
) {
    encode_compact_vertex_values(
        vertex.x,
        vertex.y,
        vertex.z,
        vertex.color,
        vertex.ao,
        vertex.u,
        vertex.v,
        vertex.light,
        output,
        tex_centroid_u,
        tex_centroid_v,
        material_section,
    );
}

#[inline(always)]
#[allow(clippy::too_many_arguments)]
pub(super) fn encode_compact_vertex_values(
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
    output: &mut [u8],
    tex_centroid_u: f32,
    tex_centroid_v: f32,
    material_section: i32,
) {
    let x = quantize_position(x);
    let y = quantize_position(y);
    let z = quantize_position(z);
    let u = encode_texture(tex_centroid_u, u);
    let v = encode_texture(tex_centroid_v, v);
    let light = encode_light(light);

    put_i32(output, 0, pack_position_hi(x, y, z));
    put_i32(output, 4, pack_position_lo(x, y, z));
    put_i32(output, 8, color_mul_rgb(color, ao));
    put_i32(output, 12, pack_texture(u, v));
    put_i32(output, 16, (light & 0xffff) | material_section);
}

pub(super) fn put_i32(output: &mut [u8], offset: usize, value: i32) {
    unsafe {
        std::ptr::write_unaligned(output.as_mut_ptr().add(offset).cast::<i32>(), value);
    }
}

pub(super) fn pack_position_hi(x: i32, y: i32, z: i32) -> i32 {
    (((x >> 10) & 0x3ff) << 0) | (((y >> 10) & 0x3ff) << 10) | (((z >> 10) & 0x3ff) << 20)
}

pub(super) fn pack_position_lo(x: i32, y: i32, z: i32) -> i32 {
    ((x & 0x3ff) << 0) | ((y & 0x3ff) << 10) | ((z & 0x3ff) << 20)
}

pub(super) fn quantize_position(position: f32) -> i32 {
    ((normalize_position(position) * POSITION_MAX_VALUE) as i32) & 0x0f_ffff
}

pub(super) fn normalize_position(value: f32) -> f32 {
    (MODEL_ORIGIN + value) / MODEL_RANGE
}

pub(super) fn encode_texture(center: f32, value: f32) -> i32 {
    let bias = if value < center { 1 } else { -1 };
    let quantized = java_round(value * TEXTURE_MAX_VALUE) + bias;
    (quantized & 0x7fff) | (sign(bias) << 15)
}

pub(super) fn encode_old_uv(u: f32, v: f32) -> i32 {
    ((java_round(u * TEXTURE_MAX_VALUE) & 0xffff) << 0)
        | ((java_round(v * TEXTURE_MAX_VALUE) & 0xffff) << 16)
}

pub(super) fn java_round(value: f32) -> i32 {
    (value + 0.5).floor() as i32
}

pub(super) fn sign(value: i32) -> i32 {
    ((value as u32) >> 31) as i32
}

pub(super) fn pack_texture(u: i32, v: i32) -> i32 {
    ((u & 0xffff) << 0) | ((v & 0xffff) << 16)
}

pub(super) fn encode_light(light: i32) -> i32 {
    let sky = clamp_i32(((light as u32 >> 16) & 0xff) as i32, 8, 248);
    let block = clamp_i32(((light as u32 >> 0) & 0xff) as i32, 8, 248);
    (block << 0) | (sky << 8)
}

pub(super) fn pack_light_and_data(light: i32, material: i32, section: i32) -> i32 {
    ((light & 0xffff) << 0) | ((material & 0xff) << 16) | ((section & 0xff) << 24)
}

pub(super) fn clamp_i32(value: i32, min: i32, max: i32) -> i32 {
    value.max(min).min(max)
}

pub(super) fn normalized_float_to_byte(value: f32) -> i32 {
    ((value * 255.0) as i32) & 0xff
}

pub(super) fn color_mul(color: i32, factor: i32) -> i32 {
    let color = color as u32 as u64;
    let factor = factor as u64;
    let hi = (color & 0x00ff00ff) * factor;
    let lo = (color & 0xff00ff00) * factor;
    let result = (((hi + 0x00ff00ff) >> 8) & 0x00ff00ff) | (((lo + 0xff00ff00) >> 8) & 0xff00ff00);
    result as u32 as i32
}

pub(super) fn color_mul_rgb(color: i32, factor: f32) -> i32 {
    let alpha_mask = 0xff000000u32 as i32;
    let factor = normalized_float_to_byte(factor);
    (color_mul(color, factor) & !alpha_mask) | (color & alpha_mask)
}

pub(super) fn with_alpha(color: i32, alpha: f32) -> i32 {
    let alpha = normalized_float_to_byte(alpha);
    (alpha << 24) | (color & !(0xff << 24))
}

pub(super) fn encode_color(color: i32, ao: f32, separate_ao: bool) -> i32 {
    if separate_ao {
        with_alpha(color, ao)
    } else {
        color_mul_rgb(color, ao)
    }
}

pub(super) fn compute_mid_block(vertex: &QuadVertex, quad: &NativeQuad) -> i32 {
    pack_mid_block(
        quad.local_x as f32 + 0.5 - vertex.x,
        quad.local_y as f32 + 0.5 - vertex.y,
        quad.local_z as f32 + 0.5 - vertex.z,
    )
}

pub(super) fn pack_mid_block(x: f32, y: f32, z: f32) -> i32 {
    (((x * 64.0) as i32) & 0xff)
        | ((((y * 64.0) as i32) & 0xff) << 8)
        | ((((z * 64.0) as i32) & 0xff) << 16)
}

pub(super) fn pack_block_id(quad: &NativeQuad) -> i32 {
    quad.block_id.wrapping_add(1).wrapping_shl(1) | ((quad.render_type as i32) & 1)
}

pub(super) fn compute_face_normal(vertices: &[QuadVertex; 4]) -> (f32, f32, f32) {
    let dx0 = vertices[2].x - vertices[0].x;
    let dy0 = vertices[2].y - vertices[0].y;
    let dz0 = vertices[2].z - vertices[0].z;
    let dx1 = vertices[3].x - vertices[1].x;
    let dy1 = vertices[3].y - vertices[1].y;
    let dz1 = vertices[3].z - vertices[1].z;
    normalize3(
        dy0 * dz1 - dz0 * dy1,
        dz0 * dx1 - dx0 * dz1,
        dx0 * dy1 - dy0 * dx1,
    )
}

pub(super) fn norm_i8_pack_from_quad(quad: &NativeQuad) -> i32 {
    norm_i8_pack_from_vertices(&quad.vertices)
}

pub(super) fn norm_i8_pack_from_vertices(vertices: &[QuadVertex; 4]) -> i32 {
    let normal = compute_face_normal(vertices);
    (((normal.0.clamp(-1.0, 1.0) * 127.0) as i32) & 0xff)
        | ((((normal.1.clamp(-1.0, 1.0) * 127.0) as i32) & 0xff) << 8)
        | ((((normal.2.clamp(-1.0, 1.0) * 127.0) as i32) & 0xff) << 16)
}

pub(super) fn compute_tangent_for_quad(
    normal: (f32, f32, f32),
    vertices: &[QuadVertex; 4],
) -> (f32, f32, f32, f32) {
    match compute_tangent(normal, vertices[0], vertices[1], vertices[2]) {
        Some(value) => value,
        None => compute_tangent(normal, vertices[2], vertices[3], vertices[0])
            .unwrap_or((0.0, 1.0, 0.0, 1.0)),
    }
}

pub(super) fn compute_tangent(
    normal: (f32, f32, f32),
    v0: QuadVertex,
    v1: QuadVertex,
    v2: QuadVertex,
) -> Option<(f32, f32, f32, f32)> {
    let edge1x = v1.x - v0.x;
    let edge1y = v1.y - v0.y;
    let edge1z = v1.z - v0.z;
    let edge2x = v2.x - v0.x;
    let edge2y = v2.y - v0.y;
    let edge2z = v2.z - v0.z;

    let delta_u1 = v1.u - v0.u;
    let delta_v1 = v1.v - v0.v;
    let delta_u2 = v2.u - v0.u;
    let delta_v2 = v2.v - v0.v;
    let fdenom = delta_u1 * delta_v2 - delta_u2 * delta_v1;
    let f = if fdenom == 0.0 { 1.0 } else { 1.0 / fdenom };

    let tangent = normalize3(
        f * (delta_v2 * edge1x - delta_v1 * edge2x),
        f * (delta_v2 * edge1y - delta_v1 * edge2y),
        f * (delta_v2 * edge1z - delta_v1 * edge2z),
    );
    if tangent.0 == 0.0 && tangent.1 == 0.0 && tangent.2 == 0.0 {
        return None;
    }

    let bitangent = normalize3(
        f * (-delta_u2 * edge1x + delta_u1 * edge2x),
        f * (-delta_u2 * edge1y + delta_u1 * edge2y),
        f * (-delta_u2 * edge1z + delta_u1 * edge2z),
    );

    let predicted_bitangent = (
        tangent.1 * normal.2 - tangent.2 * normal.1,
        tangent.2 * normal.0 - tangent.0 * normal.2,
        tangent.0 * normal.1 - tangent.1 * normal.0,
    );
    let dot = bitangent.0 * predicted_bitangent.0
        + bitangent.1 * predicted_bitangent.1
        + bitangent.2 * predicted_bitangent.2;
    let w = if dot < 0.0 { -1.0 } else { 1.0 };

    Some((tangent.0, tangent.1, tangent.2, w))
}

pub(super) fn normalize3(x: f32, y: f32, z: f32) -> (f32, f32, f32) {
    let value = x * x + y * y + z * z;
    let coefficient = if value == 0.0 {
        1.0
    } else {
        1.0 / value.sqrt()
    };
    (x * coefficient, y * coefficient, z * coefficient)
}

pub(super) fn octahedron_encode(vector: (f32, f32, f32)) -> (f32, f32) {
    let inv_l1 = 1.0 / (vector.0.abs() + vector.1.abs() + vector.2.abs());
    let nx = vector.0 * inv_l1;
    let ny = vector.1 * inv_l1;
    let nz = vector.2 * inv_l1;

    if nz >= 0.0 {
        (nx, ny)
    } else {
        (
            (1.0 - ny.abs()) * if nx >= 0.0 { 1.0 } else { -1.0 },
            (1.0 - nx.abs()) * if ny >= 0.0 { 1.0 } else { -1.0 },
        )
    }
}

pub(super) fn tangent_encode(tangent: (f32, f32, f32, f32)) -> (f32, f32) {
    let mut encoded = octahedron_encode((tangent.0, tangent.1, tangent.2));
    let y_sign = if encoded.1 >= 0.0 {
        64.0 / 127.0
    } else {
        -64.0 / 127.0
    };
    encoded.1 *= 63.0 / 127.0;
    if tangent.3 < 0.0 {
        encoded.1 += y_sign;
    }
    encoded
}

pub(super) fn default_normal() -> i32 {
    let normal = octahedron_encode((0.0, 1.0, 0.0));
    let tangent = tangent_encode((0.0, 1.0, 0.0, 1.0));
    pack_norm_i8(normal.0, normal.1, tangent.0, tangent.1)
}

pub(super) fn pack_norm_i8(x: f32, y: f32, z: f32, w: f32) -> i32 {
    (((x * 127.0) as i32) & 0xff)
        | ((((y * 127.0) as i32) & 0xff) << 8)
        | ((((z * 127.0) as i32) & 0xff) << 16)
        | ((((w * 127.0) as i32) & 0xff) << 24)
}
