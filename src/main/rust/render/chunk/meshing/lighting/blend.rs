use super::cache::AoFaceCache;
#[cfg(test)]
use super::face::ao_face_data;
use super::face::AoFace;
use super::parity::{unpack_block_light, unpack_sky_light};
use super::NativeSectionBlockRecord;

/// Bilinearly blends one AO face.
pub(in crate::render::chunk::meshing) fn blend_ao_face(
    face: AoFace,
    weights: [f32; 4],
) -> (f32, i32) {
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
pub(in crate::render::chunk::meshing) fn blend_inset_ao_face(
    block: &NativeSectionBlockRecord,
    light_face: i32,
    n1d: f32,
    n2d: f32,
    weights: [f32; 4],
) -> (f32, i32) {
    let n1 = ao_face_data(block, light_face, false);
    let n2 = ao_face_data(block, light_face, true);
    blend_inset_faces(n1, n2, n1d, n2d, weights)
}

pub(in crate::render::chunk::meshing) fn blend_inset_ao_face_cached(
    cache: &mut AoFaceCache,
    block: &NativeSectionBlockRecord,
    light_face: i32,
    n1d: f32,
    n2d: f32,
    weights: [f32; 4],
) -> (f32, i32) {
    let n1 = cache.get(block, light_face, false);
    let n2 = cache.get(block, light_face, true);
    blend_inset_faces(n1, n2, n1d, n2d, weights)
}

fn blend_inset_faces(n1: AoFace, n2: AoFace, n1d: f32, n2d: f32, weights: [f32; 4]) -> (f32, i32) {
    let ao = weighted_sum(n1.ao, weights) * n1d + weighted_sum(n2.ao, weights) * n2d;
    let sl = weighted_sum(n1.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_sky_light(lm) as f32), weights) * n2d;
    let bl = weighted_sum(n1.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n1d
        + weighted_sum(n2.lm.map(|lm| unpack_block_light(lm) as f32), weights) * n2d;
    (ao, (((sl as i32) & 0xff) << 16) | ((bl as i32) & 0xff))
}

pub(in crate::render::chunk::meshing) fn weighted_sum(values: [f32; 4], weights: [f32; 4]) -> f32 {
    values[0] * weights[0]
        + values[1] * weights[1]
        + values[2] * weights[2]
        + values[3] * weights[3]
}
