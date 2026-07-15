use super::parity::{
    calculate_corner_brightness, get_lightmap, unpack_ao, unpack_em, unpack_fo, unpack_op,
};
use super::sampling::{add_dir, ao_neighbor_faces, corner_word, dir_step, light_word};
use super::NativeSectionBlockRecord;

#[derive(Clone, Copy)]
pub(in crate::render::chunk::meshing) struct AoFace {
    pub(in crate::render::chunk::meshing) lm: [i32; 4],
    pub(in crate::render::chunk::meshing) ao: [f32; 4],
}

/// Builds the Java AO face for one block face.
///
/// `offset=true` samples the adjacent face in `direction`; full-opaque adjacent
/// samples inherit origin light for parity. Edge-occluded corners fall back to
/// their edge sample, matching Java's corner occlusion rule.
pub(in crate::render::chunk::meshing) fn ao_face_data(
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
