use super::NativeSectionBlockRecord;

/// Returns a state id from the 3x3x3 neighborhood.
///
/// Coordinates are relative to the center block. Out-of-range access is
/// rejected instead of clamped because fluid and culling callers use `None` to
/// preserve callback/fallback behavior.
pub(in crate::render::chunk::meshing) fn neighborhood_state_id(
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

pub(in crate::render::chunk::meshing) fn neighborhood_index(dx: i32, dy: i32, dz: i32) -> usize {
    ((dy + 1) as usize * 9) + ((dz + 1) as usize * 3) + (dx + 1) as usize
}

/// Reads a light word from the 3x3x3 neighborhood.
///
/// Java falls back to the origin sample when AO corner traversal reaches
/// outside the cached neighborhood, so this helper intentionally does the same.
pub(in crate::render::chunk::meshing) fn light_word(
    block: &NativeSectionBlockRecord,
    delta: (i32, i32, i32),
) -> i32 {
    if !(-1..=1).contains(&delta.0) || !(-1..=1).contains(&delta.1) || !(-1..=1).contains(&delta.2)
    {
        block.light_words[13]
    } else {
        block.light_words[neighborhood_index(delta.0, delta.1, delta.2)]
    }
}

pub(in crate::render::chunk::meshing) fn corner_word(
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

pub(in crate::render::chunk::meshing) fn add_dir(
    a: (i32, i32, i32),
    b: (i32, i32, i32),
) -> (i32, i32, i32) {
    (a.0 + b.0, a.1 + b.1, a.2 + b.2)
}

pub(in crate::render::chunk::meshing) fn dir_step(dir: i32) -> (i32, i32, i32) {
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

pub(in crate::render::chunk::meshing) fn ao_neighbor_faces(dir: i32) -> [i32; 4] {
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

pub(in crate::render::chunk::meshing) fn map_ao_corners(
    dir: i32,
    lm0: [i32; 4],
    ao0: [f32; 4],
) -> ([i32; 4], [f32; 4]) {
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

/// Bilinear weights for the face-local `(u, v)` coordinates Java derives from
/// vertex position and light face.
pub(in crate::render::chunk::meshing) fn corner_weights(
    dir: i32,
    x: f32,
    y: f32,
    z: f32,
) -> [f32; 4] {
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

/// Depth between the direct and offset AO faces.
pub(in crate::render::chunk::meshing) fn face_depth(dir: i32, x: f32, y: f32, z: f32) -> f32 {
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
