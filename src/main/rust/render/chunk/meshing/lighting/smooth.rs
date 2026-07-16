use super::blend::{blend_ao_face, blend_inset_ao_face_cached};
use super::cache::AoFaceCache;
use super::face::ao_face_data;
use super::parity::{
    ambient_shade, calculate_corner_brightness, get_emissive_lightmap, get_lightmap,
    java_float_equal, unpack_ao, unpack_em, unpack_fc,
};
use super::sampling::{corner_weights, face_depth, map_ao_corners};
use super::{
    NativeMeshingState, NativeQuadLight, NativeSectionBlockRecord, StaticModelQuadRecord,
    MODEL_QUAD_FLAG_ALIGNED, MODEL_QUAD_FLAG_PARALLEL, MODEL_QUAD_FLAG_PARTIAL,
};

/// Computes Java's smooth-light path for full, partial, and inset faces.
///
/// Full aligned faces use the offset AO face directly. Partial and non-parallel
/// quads blend per vertex using clamped source coordinates, while non-parallel
/// endpoints snap to the same direct/offset faces Java selects. Inset faces
/// interpolate between direct and offset AO faces using `face_depth`.
pub(in crate::render::chunk::meshing) fn smooth_lighting(
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
                blend_inset_ao_face_cached(
                    &mut face_cache,
                    block,
                    light_face,
                    depth,
                    1.0 - depth,
                    weights,
                )
            }
        } else if java_float_equal(depth, 0.0) {
            blend_ao_face(face_cache.get(block, light_face, true), weights)
        } else if java_float_equal(depth, 1.0) {
            blend_ao_face(face_cache.get(block, light_face, false), weights)
        } else {
            blend_inset_ao_face_cached(
                &mut face_cache,
                block,
                light_face,
                depth,
                1.0 - depth,
                weights,
            )
        };
        out.ao[i] = ao * ambient_shade(light_face, shade);
        out.lm[i] = lm;
    }
    out
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
