//! GPU regression for map text policy and its position after opaque model draws.
use super::*;

// Frozen's lightmap shader mixes full brightness with 0.75 by 0.04, so
// the fixture's maximum-light texel is 0.99, quantized to 252/255.
const LIT_WHITE: [u8; 4] = [252, 252, 252, 255];
fn lit(color: [u8; 4]) -> [u8; 4] {
    [
        ((color[0] as f32) * 252. / 255.).round() as u8,
        ((color[1] as f32) * 252. / 255.).round() as u8,
        ((color[2] as f32) * 252. / 255.).round() as u8,
        color[3],
    ]
}

fn texture(id: u32, rgba: [u8; 4]) -> WorldMeshTextureAssetPayload {
    let mut texture = shader_mesh_scene_textures(0).remove(0);
    texture.texture_id = id;
    let mut bytes = Vec::new();
    let mut encoder = png::Encoder::new(&mut bytes, 1, 1);
    encoder.set_color(png::ColorType::Rgba);
    encoder.set_depth(png::BitDepth::Eight);
    encoder
        .write_header()
        .unwrap()
        .write_image_data(&rgba)
        .unwrap();
    texture.png_bytes = bytes;
    texture.mip_png_bytes.clear();
    texture.requested_mip_levels = 1;
    texture
}

fn image(id: u32, depth: f32, policy: bool) -> WorldMaterialQuadRequest {
    let mut quad = material_quad(
        WORLD_MATERIAL_MODE_TRANSLUCENT,
        WORLD_DEPTH_POLICY_TEST_NO_WRITE,
    );
    quad.stratum = WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY;
    quad.material_id = if policy {
        WORLD_MATERIAL_ID_MAP_TEXT
    } else {
        WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED
    };
    quad.material_mode = if policy {
        WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
    } else {
        WORLD_MATERIAL_MODE_TRANSLUCENT
    };
    quad.source_program = WORLD_MATERIAL_SOURCE_TEXTURED;
    quad.cull_policy = WORLD_CULL_NONE;
    quad.texture_id = id;
    quad.vertices
        .iter_mut()
        .for_each(|position| position[2] = depth);
    quad.packed_light = 15728640;
    quad.vertex_packed_light = [15728640; 4];
    quad
}

fn render(
    quads: Vec<WorldMaterialQuadRequest>,
    textures: Vec<WorldMeshTextureAssetPayload>,
    fog: bool,
) -> [u8; 4] {
    let pixels = render_pixels(quads, textures, fog);
    pixels[(64 * 128 + 64) * 4..(64 * 128 + 64) * 4 + 4]
        .try_into()
        .unwrap()
}

fn render_pixels(
    quads: Vec<WorldMaterialQuadRequest>,
    textures: Vec<WorldMeshTextureAssetPayload>,
    fog: bool,
) -> Vec<u8> {
    render_pixels_with_backplate(quads, textures, fog, true)
}

fn render_pixels_with_backplate(
    quads: Vec<WorldMaterialQuadRequest>,
    textures: Vec<WorldMeshTextureAssetPayload>,
    fog: bool,
    backplate: bool,
) -> Vec<u8> {
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("map text ordering").unwrap();
    let mut frontend = WorldPrimitiveFrontend::default();
    let vertices = [
        [-0.5, -0.5, 0.1],
        [0.5, -0.5, 0.1],
        [0.5, 0.5, 0.1],
        [-0.5, 0.5, 0.1],
    ]
    .into_iter()
    .map(|p| shader_mesh_vertex(p, [0.5, 0.5], 0xffffffff, 0, [0., 0., 1.]))
    .collect();
    let mut plate = shader_mesh_quad_asset(
        1001,
        1,
        0xf000_1001,
        WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
        WORLD_MATERIAL_MODE_OPAQUE,
        vertices,
    );
    plate.sections[0].cull_policy = WORLD_CULL_NONE;
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            1,
            vec![plate],
            std::iter::once(texture(0xf000_1001, [255; 4]))
                .chain(textures)
                .collect(),
        )
        .unwrap();
    let mut scene = frame(Vec::new());
    scene.background = WorldBackgroundRequest::default();
    let mut plate = mesh_instance(1001, 1);
    plate.stratum = WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY;
    plate.cull_policy = WORLD_CULL_NONE;
    scene.mesh_instances = if backplate { vec![plate] } else { Vec::new() };
    scene.material_quads = quads;
    if fog {
        scene.shader_environment.enabled = true;
        scene.shader_environment.far_plane = 128.0;
        scene.shader_environment.fog_parameter_color = [0., 0., 1., 1.];
        scene.shader_environment.fog_environmental_start = 0.;
        scene.shader_environment.fog_environmental_end = 0.;
        scene.shader_environment.fog_render_distance_start = 1.0e12;
        scene.shader_environment.fog_render_distance_end = 1.0e12;
    }
    let rendered =
        render_material_scene(&mut gal, &mut frontend, 1, 128, 128, scene, "map-text").unwrap();
    frontend.reset(&mut gal);
    rendered.pixels
}

#[test]
fn vulkan_map_text_stages_its_lightmap_without_any_mesh_batches() {
    // Selected-source preparation strips mesh instances from its provisional
    // graph. Map lightmap admission must therefore depend on the map material
    // itself, rather than incidentally finding an opaque model in that graph.
    let color = crate::content::map_color::PACKED_RGBA[18];
    let pixels = render_pixels_with_backplate(
        vec![image(0xf000_1002, -0.1, true)],
        vec![texture(0xf000_1002, color)],
        false,
        false,
    );
    assert_eq!(&lit(color), &pixels[(64 * 128 + 64) * 4..(64 * 128 + 64) * 4 + 4]);
}

#[test]
fn vulkan_map_text_preserves_frozen_uvs_on_shared_top_left_images() {
    // NativeImage row zero addresses V=0 in Frozen's GL upload. The shared
    // Vulkan image flips top-left payloads; the map consumer must compensate
    // its UVs. A solid image cannot detect this reversal.
    let mut codes = vec![18; 128 * 128];
    codes[64 * 128..].fill(30);
    let mut map = texture(0xf000_1002, [0; 4]);
    map.png_bytes = crate::assets::map_image::encode_png(&codes).unwrap();
    for origin in [0, 1] {
        map.coordinate_origin = origin;
        let pixels = render_pixels(
            vec![image(0xf000_1002, -0.1, true)],
            vec![map.clone()],
            false,
        );
        for (y, code) in [(80, 18), (48, 30)] {
            let start = (y * 128 + 64) * 4;
            assert_eq!(
                &lit(crate::content::map_color::PACKED_RGBA[code]),
                &pixels[start..start + 4],
                "Frozen map UV row at image y={y} origin={origin}"
            );
        }
    }
}

#[test]
fn vulkan_map_text_survives_later_opaque_models_but_respects_their_depth() {
    let mut map = texture(0xf000_1002, [0; 4]);
    map.png_bytes = crate::assets::map_image::encode_png(&vec![30; 128 * 128]).unwrap();
    let control = render(
        vec![image(0xf000_1002, -0.1, false)],
        vec![map.clone()],
        false,
    );
    assert_eq!(
        LIT_WHITE, control,
        "old generic alpha path is erased by the later frame model"
    );
    let expected = lit(crate::content::map_color::PACKED_RGBA[30]);
    for material in [WORLD_MATERIAL_ID_MAP_TEXT, WORLD_MATERIAL_ID_ITEM_FRAME_MAP, WORLD_MATERIAL_ID_GLOW_ITEM_FRAME_MAP] {
        let mut quad = image(0xf000_1002, -0.1, true);
        quad.material_id = material;
        assert_eq!(expected, render(vec![quad], vec![map.clone()], false), "map producer {material:x}");
    }
    assert_eq!(
        LIT_WHITE,
        render(vec![image(0xf000_1002, 0.2, true)], vec![map], false),
        "a genuinely nearer model still occludes the map"
    );
    assert_eq!(
        LIT_WHITE,
        render(
            vec![image(0xf000_1002, -0.1, true)],
            vec![texture(0xf000_1002, [0; 4])],
            false
        ),
        "transparent map pixels leave the frame visible"
    );
}

#[test]
fn vulkan_map_decorations_preserve_frozen_alpha_cutoff_blending_and_fog() {
    for alpha in [25, 26, 127, 255] {
        let pixel = render(
            vec![
                image(0xf000_1002, -0.1, true),
                image(0xf000_1003, -0.2, true),
            ],
            vec![
                texture(0xf000_1002, [0, 178, 0, 255]),
                texture(0xf000_1003, [255, 0, 0, alpha]),
            ],
            false,
        );
        let coverage = if alpha < 26 { 0. } else { alpha as f32 / 255. };
        let expected = [
            (coverage * 252.).round() as u8,
            ((1. - coverage) * 176.).round() as u8,
            0,
        ];
        for channel in 0..3 {
            assert!(
                (pixel[channel] as i16 - expected[channel] as i16).abs() <= 1,
                "alpha={alpha} expected={expected:?} actual={pixel:?}"
            );
        }
    }
    assert_eq!(
        [0, 0, 255, 255],
        render(
            vec![image(0xf000_1002, -0.1, true)],
            vec![texture(0xf000_1002, [0, 178, 0, 255])],
            true
        ),
        "environmental fog applies to map text"
    );
}
