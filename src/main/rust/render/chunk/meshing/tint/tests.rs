use super::*;

#[test]
fn argb_to_abgr_preserves_alpha_and_swaps_red_blue() {
    assert_eq!(0xff0000ffu32 as i32, argb_to_abgr(0xffff0000u32 as i32));
    assert_eq!(0xff00ff00u32 as i32, argb_to_abgr(0xff00ff00u32 as i32));
    assert_eq!(0xffff0000u32 as i32, argb_to_abgr(0xff0000ffu32 as i32));
    assert_eq!(0xffffffffu32 as i32, argb_to_abgr(0xffffffffu32 as i32));
    assert_eq!(0x80332211u32 as i32, argb_to_abgr(0x80112233u32 as i32));
    assert_eq!(0xff6f9935u32 as i32, argb_to_abgr(0xff35996fu32 as i32));
    assert_eq!(0xffe4763fu32 as i32, argb_to_abgr(0xff3f76e4u32 as i32));
}

#[test]
fn native_vertex_tint_matches_frozen_fixed_point_biome_blend() {
    let mut block = NativeSectionBlockRecord::default();
    block.tint = 0xff00_0000u32 as i32;
    block.flags = 1 << 1;
    // Snapshot lattice offsets are -1..2. Populate the Java 2x2 sample
    // square selected by a unit-block vertex (base offset 0 -> index 1).
    block.tint_lattice[1][1][1] = 0xff40_4000u32 as i32;
    block.tint_lattice[1][1][2] = 0xff80_4000u32 as i32;
    block.tint_lattice[1][2][1] = 0xff40_8000u32 as i32;
    block.tint_lattice[1][2][2] = 0xff80_8000u32 as i32;
    block.tint_lattice[1][0][0] = 0xff00_0000u32 as i32;
    block.tint_lattice[1][0][1] = 0xff40_0000u32 as i32;
    block.tint_lattice[1][1][0] = 0xff00_4000u32 as i32;
    let state = NativeMeshingState {
        tint_type: TINT_GRASS,
        ..NativeMeshingState::default()
    };
    assert_eq!(
        0xff40_4000u32 as i32,
        native_vertex_tint_color(&block, state, 0.5, 0.5, 0.5)
    );
    assert_eq!(
        0xff20_2000u32 as i32,
        native_vertex_tint_color(&block, state, 0.0, 0.5, 0.0)
    );
}

#[test]
fn copied_per_block_dry_foliage_tint_is_not_replaced_by_neighbour_blending() {
    let dry = 0xff98_6034u32 as i32;
    let block = NativeSectionBlockRecord {
        tint: dry,
        flags: 1 << 1,
        tint_lattice: [[[0xff38_9824u32 as i32; 4]; 4]; 4],
        ..NativeSectionBlockRecord::default()
    };
    let state = NativeMeshingState {
        tint_type: TINT_CONSTANT,
        ..NativeMeshingState::default()
    };
    for x in [-0.5, 0.0, 0.5, 1.0, 1.5] {
        for y in [0.0, 0.125, 1.0] {
            for z in [-0.5, 0.0, 0.5, 1.0, 1.5] {
                assert_eq!(native_vertex_tint_color(&block, state, x, y, z), dry);
                assert_eq!(
                    multiply_argb(-1, native_vertex_tint_color(&block, state, x, y, z)),
                    dry
                );
            }
        }
    }
}

#[test]
fn native_color_multiplication_matches_frozen_sodium_color_mixer() {
    // Mirrors Sodium's ColorMixer.mulComponentWise: (component product +
    // 0xff) >>> 8.  Division by 255 is observably different at this boundary.
    assert_eq!(
        0x8031_1d05u32 as i32,
        multiply_argb(0x8040_8020u32 as i32, 0xffc4_3a28u32 as i32)
    );
}

#[test]
fn owned_world_fields_match_literal_blends_for_extended_vertices_and_distinct_resolvers() {
    use crate::world::level::biome::color_fields::*;
    let active = [0u16, 15, 4080, 4095];
    let mut kinds = vec![0u8; 4096];
    let mut literals = vec![-1i32; 4096 * 64];
    for (i, &block) in active.iter().enumerate() {
        kinds[block as usize] = i as u8 + 1;
    }
    for i in 0..64 {
        literals[4095 * 64 + i] = 0xff000000u32 as i32 | (i as i32 * 0x010203);
    }
    let mut lease = [0u64; 4];
    assert_eq!(0, unsafe {
        mattmc_world_section_colors_plan(
            -17,
            80,
            -33,
            active.as_ptr(),
            4,
            kinds.as_ptr(),
            literals.as_ptr(),
            lease.as_mut_ptr(),
        )
    });
    unsafe {
        let queries = std::slice::from_raw_parts(lease[1] as *const [i32; 4], lease[3] as usize);
        let values = std::slice::from_raw_parts_mut(lease[2] as *mut i32, lease[3] as usize);
        for (value, query) in values.iter_mut().zip(queries) {
            *value = 0xff000000u32 as i32
                | (((query[0] * 31 + query[1]) & 255) << 16)
                | ((query[2] & 255) << 8)
                | (query[3] & 255);
        }
    }
    assert_eq!(0, mattmc_world_section_colors_finish(lease[0]));
    let fields = resolve(lease[0]).unwrap();
    assert_eq!(0, mattmc_world_section_colors_release(lease[0]));
    let state = NativeMeshingState {
        tint_type: TINT_GRASS,
        ..Default::default()
    };
    for &index in &active {
        let mut block = NativeSectionBlockRecord {
            local_x: (index & 15) as i32,
            local_y: (index >> 8) as i32,
            local_z: ((index >> 4) & 15) as i32,
            tint: 0xff000000u32 as i32,
            flags: 2,
            ..Default::default()
        };
        for y in 0..4 {
            for z in 0..4 {
                for x in 0..4 {
                    block.tint_lattice[y][z][x] = fields.sample(index as usize, x, y, z);
                }
            }
        }
        for x in [-1.0, -0.5, 0.0, 0.125, 0.5, 1.0, 1.5, 2.5] {
            for y in [-1.0, -0.5, 0.0, 0.125, 0.5, 1.0, 1.5] {
                for z in [-1.0, -0.5, 0.0, 0.125, 0.5, 1.0, 1.5] {
                    assert_eq!(
                        native_vertex_tint_color(&block, state, x, y, z),
                        native_vertex_tint_color_from_fields(&block, state, x, y, z, Some(&fields))
                    );
                }
            }
        }
    }
}

#[test]
fn tint_vertices_below_the_sample_domain_reject_without_integer_overflow() {
    let block = NativeSectionBlockRecord {
        tint: 0xff000000u32 as i32,
        flags: 2,
        ..Default::default()
    };
    let state = NativeMeshingState {
        tint_type: TINT_GRASS,
        ..Default::default()
    };
    for (x, y, z) in [(-1.0, 0.5, 0.5), (0.5, -1.0, 0.5), (0.5, 0.5, -1.0)] {
        assert_eq!(-1, native_vertex_tint_color(&block, state, x, y, z));
    }
}
