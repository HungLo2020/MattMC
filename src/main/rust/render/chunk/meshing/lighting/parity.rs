use super::LIGHT_FULL_BRIGHT;

pub(in crate::render::chunk::meshing) fn java_float_equal(a: f32, b: f32) -> bool {
    (a - b).abs() < 1.0e-5
}

/// Java's directional shade multipliers for model quads.
pub(in crate::render::chunk::meshing) fn ambient_shade(dir: i32, shade: bool) -> f32 {
    if !shade {
        return 1.0;
    }
    match dir {
        0 => 0.5,
        1 => 1.0,
        2 | 3 => 0.8,
        4 | 5 => 0.6,
        _ => 1.0,
    }
}

pub(in crate::render::chunk::meshing) fn get_lightmap(word: i32) -> i32 {
    pack_light(
        std::cmp::max(unpack_bl(word), unpack_lu(word)),
        unpack_sl(word),
    )
}

pub(in crate::render::chunk::meshing) fn get_emissive_lightmap(word: i32) -> i32 {
    if unpack_em(word) {
        LIGHT_FULL_BRIGHT
    } else {
        get_lightmap(word)
    }
}

pub(in crate::render::chunk::meshing) fn pack_light(block: i32, sky: i32) -> i32 {
    ((sky & 0xF) << 20) | ((block & 0xF) << 4)
}

pub(in crate::render::chunk::meshing) fn unpack_block_light(light: i32) -> i32 {
    light & 0xff
}

pub(in crate::render::chunk::meshing) fn unpack_sky_light(light: i32) -> i32 {
    (light >> 16) & 0xff
}

pub(in crate::render::chunk::meshing) fn unpack_bl(word: i32) -> i32 {
    word & 0xF
}

pub(in crate::render::chunk::meshing) fn unpack_sl(word: i32) -> i32 {
    (word >> 4) & 0xF
}

pub(in crate::render::chunk::meshing) fn unpack_lu(word: i32) -> i32 {
    (word >> 8) & 0xF
}

pub(in crate::render::chunk::meshing) fn unpack_ao(word: i32) -> f32 {
    (((word >> 12) & 0xFFFF) as f32) * (1.0 / 4096.0)
}

pub(in crate::render::chunk::meshing) fn unpack_em(word: i32) -> bool {
    ((word >> 28) & 1) != 0
}

pub(in crate::render::chunk::meshing) fn unpack_op(word: i32) -> bool {
    ((word >> 29) & 1) != 0
}

pub(in crate::render::chunk::meshing) fn unpack_fo(word: i32) -> bool {
    ((word >> 30) & 1) != 0
}

pub(in crate::render::chunk::meshing) fn unpack_fc(word: i32) -> bool {
    ((word as u32 >> 31) & 1) != 0
}

/// Java corner brightness with non-zero fill and emissive full-bright override.
pub(in crate::render::chunk::meshing) fn calculate_corner_brightness(
    mut a: i32,
    mut b: i32,
    mut c: i32,
    mut d: i32,
    aem: bool,
    bem: bool,
    cem: bool,
    dem: bool,
) -> i32 {
    if a == 0 || b == 0 || c == 0 || d == 0 {
        let min = min_non_zero(min_non_zero(a, b), min_non_zero(c, d));
        a = a.max(min);
        b = b.max(min);
        c = c.max(min);
        d = d.max(min);
    }
    if aem {
        a = LIGHT_FULL_BRIGHT;
    }
    if bem {
        b = LIGHT_FULL_BRIGHT;
    }
    if cem {
        c = LIGHT_FULL_BRIGHT;
    }
    if dem {
        d = LIGHT_FULL_BRIGHT;
    }
    ((a + b + c + d) >> 2) & 0x00ff_00ff
}

pub(in crate::render::chunk::meshing) fn min_non_zero(a: i32, b: i32) -> i32 {
    if a == 0 {
        b
    } else if b == 0 {
        a
    } else {
        a.min(b)
    }
}

/// Component-wise Java source-light merge used when model vertices carry light.
#[inline(always)]
pub(in crate::render::chunk::meshing) fn max_brightness(a: i32, b: i32) -> i32 {
    let a = a as u32;
    let b = b as u32;
    ((a & 0x0000_ffff).max(b & 0x0000_ffff) | (a & 0xffff_0000).max(b & 0xffff_0000)) as i32
}
