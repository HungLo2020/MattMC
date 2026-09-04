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
    let state = NativeMeshingState { tint_type: TINT_GRASS, ..NativeMeshingState::default() };
    assert_eq!(0xff40_4000u32 as i32, native_vertex_tint_color(&block, state, 0.5, 0.5, 0.5));
    assert_eq!(0xff20_2000u32 as i32, native_vertex_tint_color(&block, state, 0.0, 0.5, 0.0));
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
