use super::parity::{
    ambient_shade, get_emissive_lightmap, pack_light, unpack_bl, unpack_em, unpack_fc, unpack_lu,
    unpack_sl,
};
use super::sampling::{dir_step, light_word};
use super::{
    NativeMeshingState, NativeQuadLight, NativeSectionBlockRecord, StaticModelQuadRecord,
    LIGHT_FULL_BRIGHT, MODEL_QUAD_FLAG_ALIGNED, MODEL_QUAD_FLAG_PARALLEL,
};

/// Computes Java's flat-light path.
///
/// Aligned faces sample the neighboring light word in the light-face direction.
/// Non-aligned faces sample the origin, while parallel faces on full cubes keep
/// Java's special neighboring-face behavior.
pub(in crate::render::chunk::meshing) fn flat_lighting(
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
