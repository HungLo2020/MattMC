use crate::render::shaderpack::contracts::material::*;
use crate::render::shaderpack::source::ShaderSourceFile;

fn source(fragment_draw_buffers: &str) -> ShaderPackSource {
    ShaderPackSource::new(
        "textured-fixture",
        7,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_textured.vsh",
                "#version 130\n#define VERTEX_SHADER\nvoid main() { vec4 p = gl_Vertex; vec2 lm = GetLightMapCoordinates(); vec3 n = gl_Normal; vec4 c = gl_Color; gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * p; }",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_textured.fsh",
                format!(
                    "#version 130\n#define FRAGMENT_SHADER\nvoid DoLighting() {{}}\nvoid main() {{ vec4 color = texture2D(tex, texCoord); color *= glColor; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; gl_FragData[2] = color; /* DRAWBUFFERS:{fragment_draw_buffers} */ }}"
                ),
            ),
        ],
    )
    .unwrap()
}

#[test]
fn discovers_explicit_textured_material_semantics_without_renderer_state() {
    let contract =
        derive_textured_material_contract(&source("063"), TerrainProgramScope::Overworld)
            .unwrap();
    assert_eq!("world0/gbuffers_textured.fsh", contract.program_path);
    assert_eq!(vec![0, 6, 3], contract.output_color_slots);
    assert!(contract
        .inputs
        .contains(&TexturedMaterialSourceInput::PackedLight));
    assert!(contract
        .inputs
        .contains(&TexturedMaterialSourceInput::ViewSpaceNormal));
    assert_eq!(3, contract.outputs.len());
    assert_eq!(Some(0.1_f32.to_bits()), contract.alpha_cutoff_bits);
}

#[test]
fn textured_material_alpha_test_honors_pack_override_and_off() {
    for (property, expected) in [
        ("GREATER 0.25", Some(0.25_f32.to_bits())),
        ("off", None),
        ("false", None),
    ] {
        let base = source("063");
        let mut files = base.files();
        files.push(ShaderSourceFile::new(
            "shaders.properties", format!("alphaTest.gbuffers_textured={property}\n"),
        ));
        let source = ShaderPackSource::new(base.name(), base.generation(), files).unwrap();
        let contract = derive_textured_material_contract(&source, TerrainProgramScope::Overworld)
            .unwrap();
        assert_eq!(expected, contract.alpha_cutoff_bits, "{property}");
    }
}

#[test]
fn textured_material_alpha_test_rejects_unresolved_or_invalid_properties() {
    for property in [
        "alphaTest.gbuffers_textured=GREATER NaN",
        "alphaTest.gbuffers_textured=GREATER 1.1",
        "alphaTest.gbuffers_textured=LESS 0.1",
        "#ifdef CUTOUT\nalphaTest.gbuffers_textured=off\n#endif",
        "alphaTest.gbuffers_textured=off\nalphaTest.gbuffers_textured=GREATER 0.1",
    ] {
        let base = source("063");
        let mut files = base.files();
        files.push(ShaderSourceFile::new("shaders.properties", property));
        let source = ShaderPackSource::new(base.name(), base.generation(), files).unwrap();
        assert!(derive_textured_material_contract(&source, TerrainProgramScope::Overworld).is_err(),
            "unsupported alpha semantics must not silently use the default: {property}");
    }
}

#[test]
fn rejects_incompatible_textured_material_output_schema() {
    let error =
        derive_textured_material_contract(&source("06"), TerrainProgramScope::Overworld)
            .unwrap_err();
    assert!(error.to_string().contains("DRAWBUFFERS schema"));
}

#[test]
fn lowers_a_bounded_textured_material_pair_without_terrain_only_attributes() {
    let source = source("063");
    let contract =
        derive_textured_material_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let lowered = lower_textured_material_source_pair(&source, &contract).unwrap();

    lowered.require_backend_neutral_lowering().unwrap();
    assert!(lowered
        .vertex()
        .source()
        .contains("VulkanicSourceTexturedMaterialVertex"));
    assert!(lowered.vertex().source().contains(
        "#define vulkanic_source_entity vec4(0.0, 0.0, 0.0, 1.0)"
    ));
    assert!(!lowered.vertex().source().contains("vulkanic_source_vertex.entity"));
    assert!(lowered
        .fragment()
        .source()
        .contains("out_textured_material_lit_color"));
    assert!(lowered
        .fragment()
        .source()
        .contains("out_textured_material_translucency_auxiliary"));
    assert!(!lowered
        .fragment()
        .source()
        .contains("out_terrain_view_space_normal"));
}

#[test]
fn lowers_ftransform_only_textured_material_vertex_semantics() {
    let source = ShaderPackSource::new(
        "textured-ftransform",
        8,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_textured.vsh",
                "#version 130\nvoid main() { vec2 lm = GetLightMapCoordinates(); vec3 n = gl_Normal; vec4 c = gl_Color; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_textured.fsh",
                "#version 130\nvoid DoLighting() {}\nvoid main() { vec4 color = texture2D(tex, texCoord); color *= glColor; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; gl_FragData[2] = color; /* DRAWBUFFERS:063 */ }",
            ),
        ],
    )
    .unwrap();
    let contract =
        derive_textured_material_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let lowered = lower_textured_material_source_pair(&source, &contract).unwrap();

    lowered.require_backend_neutral_lowering().unwrap();
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_ftransform"));
}

#[test]
fn rejects_textured_material_source_with_terrain_only_attributes() {
    let source = ShaderPackSource::new(
        "textured-terrain-attribute-fixture",
        9,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_textured.vsh",
                "#version 130\n#define VERTEX_SHADER\nvoid main() { vec4 p = gl_Vertex + vec4(mc_midTexCoord.x); vec2 lm = GetLightMapCoordinates(); vec3 n = gl_Normal; vec4 c = gl_Color; gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * p; }",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_textured.fsh",
                "#version 130\n#define FRAGMENT_SHADER\nvoid DoLighting() {}\nvoid main() { vec4 color = texture2D(tex, texCoord); color *= glColor; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; gl_FragData[2] = color; /* DRAWBUFFERS:063 */ }",
            ),
        ],
    )
    .unwrap();
    let contract =
        derive_textured_material_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let error = lower_textured_material_source_pair(&source, &contract).unwrap_err();
    assert!(error
        .to_string()
        .contains("terrain-only attribute 'mc_midTexCoord'"));
}

#[test]
fn stages_camera_relative_material_vertices_with_an_outward_normal() {
    let primitive = stage_textured_material_primitive(
        [
            [-1.0, -1.0, 2.0],
            [1.0, -1.0, 2.0],
            [1.0, 1.0, 2.0],
            [-1.0, 1.0, 2.0],
        ],
        [[0.2, 0.3], [0.8, 0.3], [0.8, 0.9], [0.2, 0.9]],
        0x7f12_3456,
        0x00f0_00b0,
        TexturedMaterialTextureCoordinates::MinecraftBlockAtlas,
        TexturedMaterialWinding::CounterClockwise,
    )
    .unwrap();

    assert_eq!(
        TexturedMaterialPositionSpace::CameraRelative,
        primitive.position_space
    );
    assert_eq!(
        TexturedMaterialTextureCoordinates::MinecraftBlockAtlas,
        primitive.texture_coordinates
    );
    assert_eq!([0.0, 0.0, 1.0], primitive.vertices[0].geometric_normal);
    assert_eq!([0.8, 0.9], primitive.vertices[2].texture_uv);
    assert_eq!(0x7f12_3456, primitive.vertices[3].source_color_argb);
    assert_eq!(0x00f0_00b0, primitive.vertices[1].packed_light);
}

#[test]
fn vertex_modulated_material_stream_preserves_endpoint_color_and_light() {
    let primitive = stage_textured_material_primitive_with_vertex_modulation(
        [
            [0.0, 0.0, 0.0],
            [1.0, 0.0, 0.0],
            [1.0, 1.0, 0.0],
            [0.0, 1.0, 0.0],
        ],
        [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
        [0xff80_6040, 0xff80_6040, 0xff38_2c20, 0xff38_2c20],
        [0x00f0_00d0, 0x00f0_00d0, 0x00b0_0070, 0x00b0_0070],
        TexturedMaterialTextureCoordinates::LocalTexture,
        TexturedMaterialWinding::CounterClockwise,
    )
    .unwrap();

    assert_eq!(0xff80_6040, primitive.vertices[0].source_color_argb);
    assert_eq!(0xff38_2c20, primitive.vertices[3].source_color_argb);
    assert_eq!(0x00f0_00d0, primitive.vertices[1].packed_light);
    assert_eq!(0x00b0_0070, primitive.vertices[2].packed_light);
}

#[test]
fn packs_compact_textured_material_stream_with_raw_uv2_light() {
    let primitive = stage_textured_material_primitive(
        [
            [0.0, 0.0, 0.0],
            [1.0, 0.0, 0.0],
            [1.0, 1.0, 0.0],
            [0.0, 1.0, 0.0],
        ],
        [[0.25, 0.5], [0.75, 0.5], [0.75, 1.0], [0.25, 1.0]],
        0x8040_80ff,
        0x00d0_00a0,
        TexturedMaterialTextureCoordinates::MinecraftBlockAtlas,
        TexturedMaterialWinding::CounterClockwise,
    )
    .unwrap();
    let packed = pack_textured_material_source_primitives(&[primitive]).unwrap();
    assert_eq!(4 * TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES, packed.len());
    let lanes = packed
        .chunks_exact(std::mem::size_of::<f32>())
        .map(|lane| f32::from_ne_bytes(lane.try_into().unwrap()))
        .collect::<Vec<_>>();
    assert_eq!([0.0, 0.0, 0.0, 1.0], lanes[0..4]);
    assert_eq!(
        [64.0 / 255.0, 128.0 / 255.0, 1.0, 128.0 / 255.0],
        lanes[4..8]
    );
    assert_eq!([0.0, 0.0, 1.0, 0.0], lanes[8..12]);
    assert_eq!([0.25, 0.5, 160.0, 208.0], lanes[12..16]);
}

#[test]
fn atlas_source_stream_rejects_standalone_texture_coordinates() {
    let primitive = stage_textured_material_primitive(
        [
            [-1.0, -1.0, 2.0],
            [1.0, -1.0, 2.0],
            [1.0, 1.0, 2.0],
            [-1.0, 1.0, 2.0],
        ],
        [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
        0xffff_ffff,
        0,
        TexturedMaterialTextureCoordinates::LocalTexture,
        TexturedMaterialWinding::CounterClockwise,
    )
    .unwrap();

    let error = pack_textured_material_atlas_primitives(&[primitive]).unwrap_err();
    assert!(error
        .to_string()
        .contains("requires Minecraft block-atlas UVs"));
}

#[test]
fn source_material_winding_flips_the_geometric_normal_without_reordering_semantics() {
    let primitive = stage_textured_material_primitive(
        [
            [-1.0, -1.0, 2.0],
            [1.0, -1.0, 2.0],
            [1.0, 1.0, 2.0],
            [-1.0, 1.0, 2.0],
        ],
        [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
        0xffff_ffff,
        0,
        TexturedMaterialTextureCoordinates::LocalTexture,
        TexturedMaterialWinding::Clockwise,
    )
    .unwrap();

    assert_eq!(
        [-1.0, -1.0, 2.0],
        primitive.vertices[0].camera_relative_position
    );
    assert_eq!([0.0, 0.0], primitive.vertices[0].texture_uv);
    assert_eq!([0.0, 0.0, -1.0], primitive.vertices[0].geometric_normal);
}

#[test]
fn clockwise_source_stream_reverses_storage_for_owned_triangle_expansion() {
    let primitive = stage_textured_material_primitive(
        [
            [0.0, 0.0, 0.0],
            [1.0, 0.0, 0.0],
            [1.0, 1.0, 0.0],
            [0.0, 1.0, 0.0],
        ],
        [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
        0xffff_ffff,
        0,
        TexturedMaterialTextureCoordinates::LocalTexture,
        TexturedMaterialWinding::Clockwise,
    )
    .unwrap();
    let packed = pack_textured_material_source_primitives(&[primitive]).unwrap();
    let positions = packed
        .chunks_exact(TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES)
        .map(|vertex| {
            [
                f32::from_ne_bytes(vertex[0..4].try_into().unwrap()),
                f32::from_ne_bytes(vertex[4..8].try_into().unwrap()),
                f32::from_ne_bytes(vertex[8..12].try_into().unwrap()),
            ]
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![
            [0.0, 1.0, 0.0],
            [1.0, 1.0, 0.0],
            [1.0, 0.0, 0.0],
            [0.0, 0.0, 0.0]
        ],
        positions
    );
}

#[test]
fn stages_small_or_zero_area_quads_and_rejects_non_finite_data() {
    let stage = |positions| {
        stage_textured_material_primitive(
            positions,
            [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            0,
            0,
            TexturedMaterialTextureCoordinates::LocalTexture,
            TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap()
        .vertices[0]
            .geometric_normal
    };
    // Zero area rasterizes nothing; staging must not drop the frame.
    assert_eq!([0.0, 0.0, 1.0], stage([[0.0, 0.0, 0.0]; 4]));
    // Sub-millimetre first-person item faces keep their true normal.
    let tiny = 1.0e-4;
    let normal = stage([[0.0, 0.0, 0.0], [tiny, 0.0, 0.0], [tiny, 0.0, tiny], [0.0, 0.0, tiny]]);
    assert!((normal[1] + 1.0).abs() < 1.0e-6, "{normal:?}");
    // A triangle authored as a quad (vertex 1 == vertex 0) uses the
    // opposite corner.
    let normal = stage([[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [1.0, 1.0, 0.0], [0.0, 1.0, 0.0]]);
    assert!((normal[2] - 1.0).abs() < 1.0e-6, "{normal:?}");

    let non_finite = stage_textured_material_primitive(
        [
            [f32::NAN, 0.0, 0.0],
            [1.0, 0.0, 0.0],
            [1.0, 1.0, 0.0],
            [0.0, 1.0, 0.0],
        ],
        [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
        0,
        0,
        TexturedMaterialTextureCoordinates::LocalTexture,
        TexturedMaterialWinding::CounterClockwise,
    )
    .unwrap_err();
    assert!(non_finite.to_string().contains("non-finite"));
}
