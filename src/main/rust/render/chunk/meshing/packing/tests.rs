use super::*;

fn vertex(x: f32, y: f32, z: f32, u: f32, v: f32) -> QuadVertex {
    QuadVertex {
        x,
        y,
        z,
        color: 0xff804020u32 as i32,
        ao: 0.5,
        u,
        v,
        light: 0x00f000f0,
    }
}

fn quad() -> NativeQuad {
    NativeQuad {
        vertices: [
            vertex(0.0, 0.0, 0.0, 0.0, 0.0),
            vertex(1.0, 0.0, 0.0, 1.0, 0.0),
            vertex(1.0, 1.0, 0.0, 1.0, 1.0),
            vertex(0.0, 1.0, 0.0, 0.0, 1.0),
        ],
        block_emission: 7,
        render_type: 1,
        ignore_mid_block: 0,
        _padding: 0,
        block_id: 41,
        local_x: 1,
        local_y: 2,
        local_z: 3,
        material_bits: 5,
    }
}

#[test]
fn compact_color_encoding_keeps_material_alpha_when_ao_is_not_separate() {
    assert_eq!(
        0x80112233u32 as i32,
        encode_color(0x80112233u32 as i32, 1.0, false)
    );
    assert_eq!(
        0x7f112233u32 as i32,
        encode_color(0x80112233u32 as i32, 0.5, true)
    );
}

#[test]
fn compact_encoder_writes_expected_base_words() {
    let input = [quad()];
    let mut output = vec![0u8; 4 * 20];
    let format = NativeFormat {
        vertex_stride: 20,
        block_id_offset: 0,
        normal_offset: 0,
        tangent_offset: 0,
        mid_uv_offset: 0,
        mid_block_offset: 0,
        section_index: 3,
        separate_ao: false,
    };

    unsafe {
        assert_eq!(
            OK,
            encode(
                input.as_ptr() as u64,
                4,
                output.as_mut_ptr() as u64,
                output.len() as i32,
                format,
            )
        );
    }

    assert_ne!([0u8; 20], output[0..20]);
    assert_eq!(
        pack_light_and_data(0xf0f0, 5, 3).to_ne_bytes(),
        output[16..20]
    );
}

#[test]
fn compact_encoder_preserves_top_origin_atlas_v_coordinates() {
    // This intentionally uses an asymmetric atlas row. A global `1.0 - v`
    // conversion still produces valid packed coordinates but samples an
    // unrelated sprite row from the copied Minecraft atlas.
    let mut input = quad();
    input.vertices[0].u = 0.453_125;
    input.vertices[1].u = 0.460_938;
    input.vertices[2].u = 0.460_938;
    input.vertices[3].u = 0.453_125;
    input.vertices[0].v = 0.710_938;
    input.vertices[1].v = 0.710_938;
    input.vertices[2].v = 0.718_750;
    input.vertices[3].v = 0.718_750;
    let expected_v = [
        input.vertices[0].v,
        input.vertices[1].v,
        input.vertices[2].v,
        input.vertices[3].v,
    ];
    let mut output = vec![0u8; 4 * COMPACT_VERTEX_STRIDE as usize];

    encode_compact_quad_vertices(&input.vertices, input.material_bits, 3, &mut output);

    for (vertex, expected_v) in expected_v.into_iter().enumerate() {
        let offset = vertex * COMPACT_VERTEX_STRIDE as usize + 12;
        let packed = i32::from_ne_bytes(output[offset..offset + 4].try_into().unwrap()) as u32;
        let decoded_v = ((packed >> 16) & 0x7fff) as f32 / TEXTURE_MAX_VALUE;
        assert!(
            (decoded_v - expected_v).abs() <= 2.0 / TEXTURE_MAX_VALUE,
            "vertex {vertex} V coordinate changed during compact packing: expected {expected_v}, got {decoded_v}"
        );
        assert!(
            (decoded_v - (1.0 - expected_v)).abs() > 0.2,
            "vertex {vertex} was vertically mirrored while packing the copied atlas"
        );
    }
}

#[test]
fn block_id_pack_matches_java_wrapping_int_arithmetic() {
    let mut input = quad();
    input.block_id = i32::MAX;
    input.render_type = 1;

    assert_eq!(1, pack_block_id(&input));

    input.block_id = -1;
    input.render_type = 0;
    assert_eq!(0, pack_block_id(&input));
}

#[test]
fn scattered_encoder_writes_requested_quad_slots_only() {
    let mut first = quad();
    first.material_bits = 5;
    let mut second = quad();
    second.material_bits = 9;
    second.vertices[0].x = 4.0;
    let input = [first, second];
    let offsets = [8, 0];
    let mut output = vec![0u8; 4 * 3 * 20];
    let format = NativeFormat {
        vertex_stride: 20,
        block_id_offset: 0,
        normal_offset: 0,
        tangent_offset: 0,
        mid_uv_offset: 0,
        mid_block_offset: 0,
        section_index: 7,
        separate_ao: false,
    };

    unsafe {
        assert_eq!(
            OK,
            encode_scattered(
                input.as_ptr() as u64,
                offsets.as_ptr(),
                input.len() as i32,
                output.as_mut_ptr() as u64,
                output.len() as i32,
                format,
            )
        );
    }

    assert_eq!(
        pack_light_and_data(0xf0f0, 9, 7).to_ne_bytes(),
        output[16..20]
    );
    assert_eq!([0u8; 20], output[4 * 20..5 * 20]);
    assert_eq!(
        pack_light_and_data(0xf0f0, 5, 7).to_ne_bytes(),
        output[(8 * 20 + 16)..(8 * 20 + 20)]
    );
}
