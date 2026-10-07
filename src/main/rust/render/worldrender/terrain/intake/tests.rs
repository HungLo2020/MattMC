use super::*;

const STRIDE: usize = 20;

fn params(separate_ao: bool, faults: u32) -> FfiCompactTerrainDecodeParams {
    FfiCompactTerrainDecodeParams {
        vertex_stride: STRIDE as u32,
        separate_ao: u32::from(separate_ao),
        atlas_width: 1024,
        atlas_height: 512,
        mid_block_offset: 0,
        fault_bits: faults,
        metadata_stride: 10,
        reserved: 0,
    }
}

/// Compact position word pair for local coordinates in -8..24.
fn position(local: [f32; 3]) -> (u32, u32) {
    let (mut hi, mut lo) = (0u32, 0u32);
    for (axis, value) in local.into_iter().enumerate() {
        let quantized = ((value + 8.0) / 32.0 * (1 << 20) as f32) as u32;
        hi |= ((quantized >> 10) & 0x3ff) << (axis * 10);
        lo |= (quantized & 0x3ff) << (axis * 10);
    }
    (hi, lo)
}

fn quad(corners: [[f32; 3]; 4], color: u32, texture: u32, light_material: u32) -> Vec<u8> {
    let mut bytes = Vec::new();
    for corner in corners {
        let (hi, lo) = position(corner);
        for word in [hi, lo, color, texture, light_material] {
            bytes.extend_from_slice(&word.to_ne_bytes());
        }
    }
    bytes
}

fn metadata(block_id: i32, local: [i32; 3], material_type: i32, emission: i32) -> Vec<i32> {
    vec![0, 0, block_id, local[0], local[1], local[2], material_type, 0, 0, emission]
}

const TOP: [[f32; 3]; 4] = [[0.0, 1.0, 0.0], [0.0, 1.0, 1.0], [1.0, 1.0, 1.0], [1.0, 1.0, 0.0]];

fn decode(buffer: &[u8], params: FfiCompactTerrainDecodeParams, segments: &[i32], meta: &[i32]) -> (Vec<FfiWorldMeshVertex>, FfiCompactTerrainDecodeStats) {
    let mut out = Vec::new();
    let mut stats = FfiCompactTerrainDecodeStats::default();
    decode_compact_terrain_vertices(buffer, &params, segments, meta, &mut out, &mut stats).unwrap();
    (out, stats)
}

#[test]
fn compact_color_light_and_material_follow_the_java_contract() {
    // Sodium ABGR -> semantic ARGB, with or without separate AO in the alpha byte.
    assert_eq!(0x8010_2040, decode_color(0x8040_2010, false, false, false));
    assert_eq!(0x8010_2040, decode_color(0x8040_2010, true, false, false));
    assert_eq!(0xffff_ffff, decode_color(0xffff_ffff, true, false, false));
    for block in 0..256u32 {
        for sky in [0u32, 1, 127, 255] {
            let compact = 0xabcd_0000 | block | (sky << 8);
            assert_eq!(block | (sky << 16), decode_light(compact, false));
            assert_eq!(sky | (block << 16), decode_light(compact, true));
        }
    }
    let buffer = quad(TOP, 0x8040_2010, 0, 0x0005_0000);
    let (vertices, _) = decode(&buffer, params(true, 0), &[4, 1], &metadata(7, [0, 0, 0], 1, 0));
    assert_eq!(0x105, vertices[0].terrain_material_bits);
    let (vertices, _) = decode(&buffer, params(false, 0), &[4, 1], &metadata(7, [0, 0, 0], 1, 0));
    assert_eq!(0x05, vertices[0].terrain_material_bits);
    assert_eq!(7, vertices[0].shader_block_id);
    assert_eq!(1, vertices[0].shader_material_type);
}

#[test]
fn copied_atlas_texture_keeps_the_sub_texel_direction() {
    let shrink = (1.0 / 32768.0f32) - (1.0 / (1024.0f32 * 256.0));
    let low = 16_384u32;
    let high = low | 0x8000;
    assert!((decode_texture_for_copied_atlas(low, 1024) - (0.5 - shrink)).abs() < 1e-7);
    assert!((decode_texture_for_copied_atlas(high, 1024) - (0.5 + shrink)).abs() < 1e-7);
}

#[test]
fn segments_carry_facing_normals_positions_and_semantic_mid_blocks() {
    let buffer = quad(TOP, 0xff80_8080, 0, 0);
    let (vertices, stats) = decode(&buffer, params(false, 0), &[4, 1], &metadata(3, [0, 0, 0], 0, 15));
    assert_eq!(4, vertices.len());
    assert!(vertices.iter().all(|vertex| vertex.normal_packed == pack_normal(0.0, 1.0, 0.0)));
    assert_eq!([0.0, 1.0, 0.0], [vertices[0].x, vertices[0].y, vertices[0].z]);
    // Mid-block offset from the block centre (0.5, 0.5, 0.5), 1/64 units, plus emission.
    assert_eq!(32 | ((-32i32 as u32 & 0xff) << 8) | (32 << 16) | (15 << 24), vertices[0].mid_block_packed);
    assert_eq!([0.0, 1.0, 0.0], stats.min_position);
    assert_eq!([1.0, 1.0, 1.0], stats.max_position);
    assert_eq!(1, stats.ao_contract_valid);
    // An unknown facing computes the normal from the first triangle.
    let (vertices, _) = decode(&buffer, params(false, 0), &[4, 6], &metadata(3, [0, 0, 0], 0, 0));
    assert_eq!(pack_normal(0.0, 1.0, 0.0), vertices[0].normal_packed);
}

#[test]
fn fault_injections_change_their_contracts() {
    let buffer = quad(TOP, 0x40ff_ffff, 0, 0x0000_0201);
    let meta = metadata(3, [0, 0, 0], 0, 0);
    let (vertices, _) = decode(&buffer, params(false, FAULT_INVERTED_NORMAL), &[4, 1], &meta);
    assert_eq!(pack_normal(0.0, -1.0, 0.0), vertices[0].normal_packed);
    let (vertices, _) = decode(&buffer, params(false, FAULT_SWAPPED_BLOCK_SKY_LIGHT), &[4, 1], &meta);
    assert_eq!(2 | (1 << 16), vertices[0].light);
    let (vertices, _) = decode(&buffer, params(false, FAULT_WRONG_TOP_FACE_SHADE), &[4, 1], &meta);
    assert_eq!(0x4080_8080, vertices[0].color_argb);
    let (vertices, stats) = decode(&buffer, params(true, FAULT_INVERTED_AO), &[4, 1], &meta);
    assert_eq!(0xbf, vertices[0].color_argb >> 24);
    assert_eq!(0, stats.ao_contract_valid);
    assert_eq!(4, stats.separate_ao_vertices);
}

#[test]
fn malformed_meshes_are_rejected() {
    let buffer = quad(TOP, 0, 0, 0);
    let mut out = Vec::new();
    let mut stats = FfiCompactTerrainDecodeStats::default();
    let meta = metadata(3, [0, 0, 0], 0, 0);
    for (segments, meta) in [(&[3, 1][..], &meta[..]), (&[8, 1][..], &meta[..]), (&[4][..], &meta[..]), (&[4, 1][..], &meta[..9])] {
        assert!(decode_compact_terrain_vertices(&buffer, &params(false, 0), segments, meta, &mut out, &mut stats).is_err());
    }
    let bad = metadata(-1, [0, 0, 0], 0, 0);
    assert!(decode_compact_terrain_vertices(&buffer, &params(false, 0), &[4, 1], &bad, &mut out, &mut stats).is_err());
}
