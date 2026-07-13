use super::*;

#[derive(Clone, Copy, Debug)]
pub(super) struct NativeQuadLight {
    pub(super) ao: [f32; 4],
    pub(super) lm: [i32; 4],
}

pub(super) fn native_quad_lighting(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    state: NativeMeshingState,
) -> NativeQuadLight {
    let light_face = if (0..6).contains(&quad.light_face) {
        quad.light_face
    } else if (0..6).contains(&quad.cull_face) {
        quad.cull_face
    } else {
        1
    };
    let use_smooth = quad.has_ao != 0;
    if use_smooth {
        smooth_lighting(block, quad, state, light_face, quad.shade != 0)
    } else {
        flat_lighting(block, quad, state, light_face, quad.shade != 0)
    }
}

pub(super) fn flat_lighting(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    _state: NativeMeshingState,
    light_face: i32,
    shade: bool,
) -> NativeQuadLight {
    let origin_full_cube = unpack_fc(block.light_words[13]);
    let sample_dir = if (0..6).contains(&quad.cull_face) {
        quad.cull_face
    } else if (quad.flags & MODEL_QUAD_FLAG_ALIGNED) != 0
        || ((quad.flags & MODEL_QUAD_FLAG_PARALLEL) != 0 && origin_full_cube)
    {
        light_face
    } else {
        -1
    };
    let word = if sample_dir >= 0 {
        light_word(block, dir_step(sample_dir))
    } else {
        block.light_words[13]
    };
    let lm = if unpack_em(block.light_words[13]) && sample_dir >= 0 {
        LIGHT_FULL_BRIGHT
    } else if sample_dir >= 0 {
        let origin = block.light_words[13];
        let adj = word;
        pack_light(
            std::cmp::max(unpack_bl(adj), unpack_lu(origin)),
            unpack_sl(adj),
        )
    } else {
        get_emissive_lightmap(word)
    };
    NativeQuadLight {
        ao: [ambient_shade(light_face, shade); 4],
        lm: [lm; 4],
    }
}

pub(super) fn smooth_lighting(
    block: &NativeSectionBlockRecord,
    quad: &StaticModelQuadRecord,
    _state: NativeMeshingState,
    light_face: i32,
    shade: bool,
) -> NativeQuadLight {
    if let Some(light) = uniform_smooth_lighting(block, light_face, shade) {
        return light;
    }

    let parallel = (quad.flags & MODEL_QUAD_FLAG_PARALLEL) != 0;
    let aligned = (quad.flags & MODEL_QUAD_FLAG_ALIGNED) != 0
        || (parallel && unpack_fc(block.light_words[13]));
    let partial = (quad.flags & MODEL_QUAD_FLAG_PARTIAL) != 0;

    if aligned && !partial {
        let face = ao_face_data(block, light_face, true);
        let (lm, mut ao) = map_ao_corners(light_face, face.lm, face.ao);
        for value in &mut ao {
            *value *= ambient_shade(light_face, shade);
        }
        return NativeQuadLight { ao, lm };
    }

    let mut out = NativeQuadLight {
        ao: [1.0; 4],
        lm: [get_emissive_lightmap(block.light_words[13]); 4],
    };
    let mut face_cache = AoFaceCache::default();
    for i in 0..4 {
        let source = quad.vertices[i];
        let weights = corner_weights(
            light_face,
            source.x.clamp(0.0, 1.0),
            source.y.clamp(0.0, 1.0),
            source.z.clamp(0.0, 1.0),
        );
        let depth = face_depth(light_face, source.x, source.y, source.z);

        let (ao, lm) = if aligned {
            blend_ao_face(face_cache.get(block, light_face, true), weights)
        } else if parallel {
            if java_float_equal(depth, 1.0) {
                blend_ao_face(face_cache.get(block, light_face, false), weights)
            } else {
                blend_inset_ao_face_cached(&mut face_cache, block, light_face, depth, 1.0 - depth, weights)
            }
        } else if java_float_equal(depth, 0.0) {
            blend_ao_face(face_cache.get(block, light_face, true), weights)
        } else if java_float_equal(depth, 1.0) {
            blend_ao_face(face_cache.get(block, light_face, false), weights)
        } else {
            blend_inset_ao_face_cached(&mut face_cache, block, light_face, depth, 1.0 - depth, weights)
        };
        out.ao[i] = ao * ambient_shade(light_face, shade);
        out.lm[i] = lm;
    }
    out
}

#[derive(Default)]
struct AoFaceCache {
    faces: [Option<AoFace>; 12],
}

impl AoFaceCache {
    #[inline(always)]
    fn get(&mut self, block: &NativeSectionBlockRecord, direction: i32, offset: bool) -> AoFace {
        let index = ao_face_cache_index(direction, offset);
        if let Some(face) = self.faces[index] {
            return face;
        }
        let face = ao_face_data(block, direction, offset);
        self.faces[index] = Some(face);
        face
    }
}

#[inline(always)]
fn ao_face_cache_index(direction: i32, offset: bool) -> usize {
    let direction = direction.clamp(0, 5) as usize;
    (direction << 1) | usize::from(offset)
}

#[inline]
fn uniform_smooth_lighting(
    block: &NativeSectionBlockRecord,
    light_face: i32,
    shade: bool,
) -> Option<NativeQuadLight> {
    let word = block.light_words[13];
    if !block.light_words.iter().all(|sample| *sample == word) {
        return None;
    }

    let lightmap = get_lightmap(word);
    let emissive = unpack_em(word);
    let lm = calculate_corner_brightness(
        lightmap, lightmap, lightmap, lightmap, emissive, emissive, emissive, emissive,
    );
    let ao = unpack_ao(word) * ambient_shade(light_face, shade);
    Some(NativeQuadLight {
        ao: [ao; 4],
        lm: [lm; 4],
    })
}

#[derive(Clone, Copy)]
pub(super) struct AoFace {
    pub(super) lm: [i32; 4],
    pub(super) ao: [f32; 4],
}

pub(super) fn ao_face_data(
    block: &NativeSectionBlockRecord,
    direction: i32,
    offset: bool,
) -> AoFace {
    let (dx, dy, dz) = if offset {
        dir_step(direction)
    } else {
        (0, 0, 0)
    };
    let adj = light_word(block, (dx, dy, dz));
    let origin = block.light_words[13];
    let calm = if offset && unpack_fo(adj) {
        get_lightmap(origin)
    } else {
        get_lightmap(adj)
    };
    let caem = if offset && unpack_fo(adj) {
        unpack_em(origin)
    } else {
        unpack_em(adj)
    };
    let caao = unpack_ao(adj);
    let faces = ao_neighbor_faces(direction);

    let e0 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[0])));
    let e1 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[1])));
    let e2 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[2])));
    let e3 = light_word(block, add_dir((dx, dy, dz), dir_step(faces[3])));
    let e = [e0, e1, e2, e3];
    let elm = e.map(get_lightmap);
    let eao = e.map(unpack_ao);
    let eop = e.map(unpack_op);
    let eem = e.map(unpack_em);

    let c0 = corner_word(
        block,
        (dx, dy, dz),
        faces[0],
        faces[2],
        eop[2] && eop[0],
        e[0],
    );
    let c1 = corner_word(
        block,
        (dx, dy, dz),
        faces[0],
        faces[3],
        eop[3] && eop[0],
        e[0],
    );
    let c2 = corner_word(
        block,
        (dx, dy, dz),
        faces[1],
        faces[2],
        eop[2] && eop[1],
        e[1],
    );
    let c3 = corner_word(
        block,
        (dx, dy, dz),
        faces[1],
        faces[3],
        eop[3] && eop[1],
        e[1],
    );
    let c = [c1, c0, c2, c3];

    AoFace {
        ao: [
            (eao[3] + eao[0] + unpack_ao(c[0]) + caao) * 0.25,
            (eao[2] + eao[0] + unpack_ao(c[1]) + caao) * 0.25,
            (eao[2] + eao[1] + unpack_ao(c[2]) + caao) * 0.25,
            (eao[3] + eao[1] + unpack_ao(c[3]) + caao) * 0.25,
        ],
        lm: [
            calculate_corner_brightness(
                elm[3],
                elm[0],
                get_lightmap(c[0]),
                calm,
                eem[3],
                eem[0],
                unpack_em(c[0]),
                caem,
            ),
            calculate_corner_brightness(
                elm[2],
                elm[0],
                get_lightmap(c[1]),
                calm,
                eem[2],
                eem[0],
                unpack_em(c[1]),
                caem,
            ),
            calculate_corner_brightness(
                elm[2],
                elm[1],
                get_lightmap(c[2]),
                calm,
                eem[2],
                eem[1],
                unpack_em(c[2]),
                caem,
            ),
            calculate_corner_brightness(
                elm[3],
                elm[1],
                get_lightmap(c[3]),
                calm,
                eem[3],
                eem[1],
                unpack_em(c[3]),
                caem,
            ),
        ],
    }
}

pub(super) fn neighborhood_state_id(
    block: &NativeSectionBlockRecord,
    dx: i32,
    dy: i32,
    dz: i32,
) -> Option<i32> {
    if !(-1..=1).contains(&dx) || !(-1..=1).contains(&dy) || !(-1..=1).contains(&dz) {
        return None;
    }
    Some(block.neighborhood_state_ids[neighborhood_index(dx, dy, dz)])
}

pub(super) fn neighborhood_index(dx: i32, dy: i32, dz: i32) -> usize {
    ((dy + 1) as usize * 9) + ((dz + 1) as usize * 3) + (dx + 1) as usize
}

pub(super) fn light_word(block: &NativeSectionBlockRecord, delta: (i32, i32, i32)) -> i32 {
    if !(-1..=1).contains(&delta.0) || !(-1..=1).contains(&delta.1) || !(-1..=1).contains(&delta.2)
    {
        block.light_words[13]
    } else {
        block.light_words[neighborhood_index(delta.0, delta.1, delta.2)]
    }
}

pub(super) fn corner_word(
    block: &NativeSectionBlockRecord,
    base: (i32, i32, i32),
    a: i32,
    b: i32,
    edge_occluded: bool,
    fallback: i32,
) -> i32 {
    if edge_occluded {
        fallback
    } else {
        light_word(block, add_dir(add_dir(base, dir_step(a)), dir_step(b)))
    }
}

pub(super) fn add_dir(a: (i32, i32, i32), b: (i32, i32, i32)) -> (i32, i32, i32) {
    (a.0 + b.0, a.1 + b.1, a.2 + b.2)
}

pub(super) fn dir_step(dir: i32) -> (i32, i32, i32) {
    match dir {
        0 => (0, -1, 0),
        1 => (0, 1, 0),
        2 => (0, 0, -1),
        3 => (0, 0, 1),
        4 => (-1, 0, 0),
        5 => (1, 0, 0),
        _ => (0, 0, 0),
    }
}

pub(super) fn ao_neighbor_faces(dir: i32) -> [i32; 4] {
    match dir {
        0 => [4, 5, 2, 3],
        1 => [5, 4, 2, 3],
        2 => [1, 0, 5, 4],
        3 => [4, 5, 0, 1],
        4 => [1, 0, 2, 3],
        5 => [0, 1, 2, 3],
        _ => [5, 4, 2, 3],
    }
}

pub(super) fn map_ao_corners(dir: i32, lm0: [i32; 4], ao0: [f32; 4]) -> ([i32; 4], [f32; 4]) {
    match dir {
        1 => (
            [lm0[2], lm0[3], lm0[0], lm0[1]],
            [ao0[2], ao0[3], ao0[0], ao0[1]],
        ),
        2 | 4 => (
            [lm0[1], lm0[2], lm0[3], lm0[0]],
            [ao0[1], ao0[2], ao0[3], ao0[0]],
        ),
        5 => (
            [lm0[3], lm0[0], lm0[1], lm0[2]],
            [ao0[3], ao0[0], ao0[1], ao0[2]],
        ),
        _ => (lm0, ao0),
    }
}

pub(super) fn corner_weights(dir: i32, x: f32, y: f32, z: f32) -> [f32; 4] {
    let (u, v) = match dir {
        0 => (z.clamp(0.0, 1.0), (1.0 - x).clamp(0.0, 1.0)),
        1 => (z.clamp(0.0, 1.0), x.clamp(0.0, 1.0)),
        2 => ((1.0 - x).clamp(0.0, 1.0), y.clamp(0.0, 1.0)),
        3 => (y.clamp(0.0, 1.0), (1.0 - x).clamp(0.0, 1.0)),
        4 => (z.clamp(0.0, 1.0), y.clamp(0.0, 1.0)),
        5 => (z.clamp(0.0, 1.0), (1.0 - y).clamp(0.0, 1.0)),
        _ => (0.5, 0.5),
    };
    [v * u, v * (1.0 - u), (1.0 - v) * (1.0 - u), (1.0 - v) * u]
}

pub(super) fn face_depth(dir: i32, x: f32, y: f32, z: f32) -> f32 {
    match dir {
        0 => y.clamp(0.0, 1.0),
        1 => 1.0 - y.clamp(0.0, 1.0),
        2 => z.clamp(0.0, 1.0),
        3 => 1.0 - z.clamp(0.0, 1.0),
        4 => x.clamp(0.0, 1.0),
        5 => 1.0 - x.clamp(0.0, 1.0),
        _ => 0.0,
    }
}

pub(super) fn blend_ao_face(face: AoFace, weights: [f32; 4]) -> (f32, i32) {
    let ao = face.ao[0] * weights[0]
        + face.ao[1] * weights[1]
        + face.ao[2] * weights[2]
        + face.ao[3] * weights[3];
    let sky = unpack_sky_light(face.lm[0]) as f32 * weights[0]
        + unpack_sky_light(face.lm[1]) as f32 * weights[1]
        + unpack_sky_light(face.lm[2]) as f32 * weights[2]
        + unpack_sky_light(face.lm[3]) as f32 * weights[3];
    let block = unpack_block_light(face.lm[0]) as f32 * weights[0]
        + unpack_block_light(face.lm[1]) as f32 * weights[1]
        + unpack_block_light(face.lm[2]) as f32 * weights[2]
        + unpack_block_light(face.lm[3]) as f32 * weights[3];
    (ao, (((sky as i32) & 0xff) << 16) | ((block as i32) & 0xff))
}

#[cfg(test)]
pub(super) fn blend_inset_ao_face(
    block: &NativeSectionBlockRecord,
    light_face: i32,
    n1d: f32,
    n2d: f32,
    weights: [f32; 4],
) -> (f32, i32) {
    let n1 = ao_face_data(block, light_face, false);
    let n2 = ao_face_data(block, light_face, true);
    let ao = weighted_sum(n1.ao, weights) * n1d + weighted_sum(n2.ao, weights) * n2d;
    let sl = weighted_sum(n1.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n2d;
    let bl = weighted_sum(n1.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n2d;
    (ao, (((sl as i32) & 0xff) << 16) | ((bl as i32) & 0xff))
}

fn blend_inset_ao_face_cached(
    cache: &mut AoFaceCache,
    block: &NativeSectionBlockRecord,
    light_face: i32,
    n1d: f32,
    n2d: f32,
    weights: [f32; 4],
) -> (f32, i32) {
    let n1 = cache.get(block, light_face, false);
    let n2 = cache.get(block, light_face, true);
    let ao = weighted_sum(n1.ao, weights) * n1d + weighted_sum(n2.ao, weights) * n2d;
    let sl = weighted_sum(n1.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n2d;
    let bl = weighted_sum(n1.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n2d;
    (ao, (((sl as i32) & 0xff) << 16) | ((bl as i32) & 0xff))
}

pub(super) fn weighted_sum(values: [f32; 4], weights: [f32; 4]) -> f32 {
    values[0] * weights[0]
        + values[1] * weights[1]
        + values[2] * weights[2]
        + values[3] * weights[3]
}

pub(super) fn java_float_equal(a: f32, b: f32) -> bool {
    (a - b).abs() < 1.0e-5
}

pub(super) fn ambient_shade(dir: i32, shade: bool) -> f32 {
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

pub(super) fn get_lightmap(word: i32) -> i32 {
    pack_light(
        std::cmp::max(unpack_bl(word), unpack_lu(word)),
        unpack_sl(word),
    )
}

pub(super) fn get_emissive_lightmap(word: i32) -> i32 {
    if unpack_em(word) {
        LIGHT_FULL_BRIGHT
    } else {
        get_lightmap(word)
    }
}

pub(super) fn pack_light(block: i32, sky: i32) -> i32 {
    ((sky & 0xF) << 20) | ((block & 0xF) << 4)
}

pub(super) fn unpack_block_light(light: i32) -> i32 {
    light & 0xff
}

pub(super) fn unpack_sky_light(light: i32) -> i32 {
    (light >> 16) & 0xff
}

pub(super) fn unpack_bl(word: i32) -> i32 {
    word & 0xF
}
pub(super) fn unpack_sl(word: i32) -> i32 {
    (word >> 4) & 0xF
}
pub(super) fn unpack_lu(word: i32) -> i32 {
    (word >> 8) & 0xF
}
pub(super) fn unpack_ao(word: i32) -> f32 {
    (((word >> 12) & 0xFFFF) as f32) * (1.0 / 4096.0)
}
pub(super) fn unpack_em(word: i32) -> bool {
    ((word >> 28) & 1) != 0
}
pub(super) fn unpack_op(word: i32) -> bool {
    ((word >> 29) & 1) != 0
}
pub(super) fn unpack_fo(word: i32) -> bool {
    ((word >> 30) & 1) != 0
}
pub(super) fn unpack_fc(word: i32) -> bool {
    ((word as u32 >> 31) & 1) != 0
}

pub(super) fn calculate_corner_brightness(
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

pub(super) fn min_non_zero(a: i32, b: i32) -> i32 {
    if a == 0 {
        b
    } else if b == 0 {
        a
    } else {
        a.min(b)
    }
}
