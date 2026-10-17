use super::*;

fn fixture() -> (
    VulkanicGal,
    WorldPrimitiveFrontend,
    LoweredTerrainSourceProgram,
    TerrainSourceUniformFrame,
) {
    let source = ShaderPackSource::new(
        "packed-source-uniforms",
        1,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nuniform mat4 gbufferModelView;\nuniform mat4 gbufferProjection;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let artifacts =
        preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
    let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program = prepare_lowered_terrain_source_program(
        &contract,
        &lowered,
        &bindings,
        TerrainMaterialProgramKind::Opaque,
    )
    .unwrap();

    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.generation = 1;
    let mut mesh = mesh_asset(0x7a1e, 1, IndexType::U16);
    mesh.vertex_layout_version = WORLD_MESH_VERTEX_LAYOUT_V3;
    for vertex in &mut mesh.vertices {
        vertex.shader_material_type = 0;
        vertex.normal_packed = pack_normal_i8([0.0, 0.0, 1.0]);
    }
    frontend
        .apply_world_mesh_asset_update(&mut gal, 1, vec![mesh], Vec::new())
        .unwrap();
    (
        gal,
        frontend,
        program,
        frame(Vec::new()).source_uniform_frame().unwrap(),
    )
}

#[test]
fn packed_source_uniforms_match_semantic_bytes_and_keep_instances_independent() {
    let (mut gal, mut frontend, program, uniforms) = fixture();
    let transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
    let packed = frontend
        .prepare_source_terrain_uniforms(&program, 91, &transforms, &uniforms)
        .unwrap();
    let first_instances = [(matrix4_identity(), u32::MAX)];
    let mut moved = matrix4_identity();
    moved[12] = 3.0;
    let second_instances = [(moved, 0x80402010)];
    let semantic = frontend
        .prepare_source_terrain_frame_for_mesh_range(
            &program,
            91,
            0x7a1e,
            1,
            0,
            6,
            &first_instances,
            &transforms,
            &uniforms,
        )
        .unwrap();
    let first = frontend
        .prepare_source_terrain_frame_for_mesh_range_using_uniforms(
            &program,
            91,
            0x7a1e,
            1,
            0,
            6,
            &first_instances,
            SourceTerrainFrameUniforms::Packed(&packed),
        )
        .unwrap();
    let second = frontend
        .prepare_source_terrain_frame_for_mesh_range_using_uniforms(
            &program,
            91,
            0x7a1e,
            1,
            0,
            6,
            &second_instances,
            SourceTerrainFrameUniforms::Packed(&packed),
        )
        .unwrap();
    assert_eq!(
        first.legacy_texture_transforms,
        semantic.legacy_texture_transforms
    );
    assert_eq!(first.scalar_uniforms, semantic.scalar_uniforms);
    assert_eq!(first.instance_transforms, semantic.instance_transforms);
    assert_eq!(first.section_indices, semantic.section_indices);
    assert!(Arc::ptr_eq(
        &first.legacy_texture_transforms,
        &second.legacy_texture_transforms
    ));
    assert!(Arc::ptr_eq(&first.scalar_uniforms, &second.scalar_uniforms));
    assert_ne!(first.instance_transforms, second.instance_transforms);
    frontend.destroy_resources(&mut gal);
}

#[test]
fn packed_source_uniforms_reject_another_frame_or_program_even_with_the_same_identity() {
    let (mut gal, mut frontend, program, uniforms) = fixture();
    let transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
    let packed = frontend
        .prepare_source_terrain_uniforms(&program, 91, &transforms, &uniforms)
        .unwrap();
    let cloned_program = program.clone();
    for (selected, frame_id) in [(&program, 92), (&cloned_program, 91)] {
        let error = frontend
            .prepare_source_terrain_frame_for_mesh_range_using_uniforms(
                selected,
                frame_id,
                0x7a1e,
                1,
                0,
                6,
                &[(matrix4_identity(), u32::MAX)],
                SourceTerrainFrameUniforms::Packed(&packed),
            )
            .unwrap_err();
        assert!(
            error.message.contains("another program or frame"),
            "{error}"
        );
    }
    frontend.destroy_resources(&mut gal);
}

#[test]
fn packed_source_uniforms_snapshot_changed_matrices_without_mutating_earlier_draws() {
    let (mut gal, mut frontend, program, mut uniforms) = fixture();
    let mut transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
    let original = frontend
        .prepare_source_terrain_uniforms(&program, 91, &transforms, &uniforms)
        .unwrap();
    let instances = [(matrix4_identity(), u32::MAX)];
    let before = frontend
        .prepare_source_terrain_frame_for_mesh_range_using_uniforms(
            &program,
            91,
            0x7a1e,
            1,
            0,
            6,
            &instances,
            SourceTerrainFrameUniforms::Packed(&original),
        )
        .unwrap();
    let before_scalar = before.scalar_uniforms.to_vec();
    let before_legacy = before.legacy_texture_transforms.to_vec();
    uniforms.view_matrix.as_mut().unwrap()[12] = 7.0;
    transforms.atlas_texture_matrix[12] = 0.25;
    let changed = frontend
        .prepare_source_terrain_uniforms(&program, 91, &transforms, &uniforms)
        .unwrap();
    let after = frontend
        .prepare_source_terrain_frame_for_mesh_range_using_uniforms(
            &program,
            91,
            0x7a1e,
            1,
            0,
            6,
            &instances,
            SourceTerrainFrameUniforms::Packed(&changed),
        )
        .unwrap();
    let semantic = frontend
        .prepare_source_terrain_frame_for_mesh_range(
            &program,
            91,
            0x7a1e,
            1,
            0,
            6,
            &instances,
            &transforms,
            &uniforms,
        )
        .unwrap();
    assert_eq!(after.scalar_uniforms, semantic.scalar_uniforms);
    assert_eq!(
        after.legacy_texture_transforms,
        semantic.legacy_texture_transforms
    );
    assert_ne!(after.scalar_uniforms.as_ref(), before_scalar.as_slice());
    assert_ne!(
        after.legacy_texture_transforms.as_ref(),
        before_legacy.as_slice()
    );
    assert_eq!(before.scalar_uniforms.as_ref(), before_scalar.as_slice());
    assert_eq!(
        before.legacy_texture_transforms.as_ref(),
        before_legacy.as_slice()
    );
    frontend.destroy_resources(&mut gal);
}

#[test]
fn packed_source_uniforms_require_semantic_values_and_finite_instance_transforms() {
    let (mut gal, mut frontend, program, mut uniforms) = fixture();
    let transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
    let original = uniforms.view_matrix.take();
    assert!(frontend
        .prepare_source_terrain_uniforms(&program, 91, &transforms, &uniforms)
        .is_err());
    uniforms.view_matrix = original;
    let packed = frontend
        .prepare_source_terrain_uniforms(&program, 91, &transforms, &uniforms)
        .unwrap();
    let mut invalid = matrix4_identity();
    invalid[12] = f32::NAN;
    let error = frontend
        .prepare_source_terrain_frame_for_mesh_range_using_uniforms(
            &program,
            91,
            0x7a1e,
            1,
            0,
            6,
            &[(invalid, u32::MAX)],
            SourceTerrainFrameUniforms::Packed(&packed),
        )
        .unwrap_err();
    assert!(error.message.contains("non-finite"), "{error}");
    frontend.destroy_resources(&mut gal);
}
