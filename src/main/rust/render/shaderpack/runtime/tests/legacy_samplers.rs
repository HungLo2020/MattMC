use super::*;

#[test]
fn legacy_sampler_single_shadow_output_retains_declared_output_and_compiles() {
    let original = complete_bundled_pack_source_for_test();
    let mut files = original.files();
    files.retain(|file| file.path != "world0/shadow.fsh");
    files.push(ShaderSourceFile::new("world0/shadow.fsh",
        "#version 130\n#define FRAGMENT_SHADER\n#define OVERWORLD\n#define SHADOW\n#include \"/lib/common.glsl\"\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0] = vec4(0.25); }\n"));
    let source = ShaderPackSource::new(original.name(), original.generation(), files).unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let program = executor.prepared_lowered_shadow_source_program().unwrap().unwrap();
    assert!(program.fragment.source.contains("out_shadow_color"));
    assert!(!program.fragment.source.contains("out_shadow_light_shaft_color"));
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("single-shadow-output").unwrap();
    for descriptor in program.shader_module_descriptors(gal.capabilities().shader_conventions) {
        let module = gal.create_shader_module(descriptor).unwrap();
        gal.destroy(module).unwrap();
    }
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn legacy_sampler_late_raw_secondary_writer_rebuilds_cache_and_requires_distinct_snapshot() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let mut gal = gal();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let mut images = Vec::new();
    for name in ["all-casters", "opaque-casters"] {
        let texture = gal
            .create_texture(TextureDesc {
                label: name.to_owned(),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent: Extent3d {
                    width: 16,
                    height: 16,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::DepthStencilAttachment, TextureUsage::Sampled],
            })
            .unwrap();
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: name.to_owned(),
                texture,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .unwrap();
        images.push((texture, view));
    }
    let input = TerrainSourceShadowDepthInput {
        shader_pack_generation: source.generation(),
        world_generation: 4,
        shader_graph_generation: 9,
        shadow_depth_view: images[0].1,
        shadow_depth_secondary_view: Handle::NULL,
    };
    let first = executor
        .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
        .unwrap()
        .unwrap();
    let primary_before = first.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary);
    assert!(
        executor.note_writer_required_roles([TerrainSourceResourceRole::ShadowDepthRawSecondary])
    );
    let before = gal.metrics().resource_creates - gal.metrics().resource_destroys;
    assert!(executor
        .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
        .unwrap_err()
        .to_string()
        .contains("distinct opaque-only snapshot"));
    assert_eq!(
        before,
        gal.metrics().resource_creates - gal.metrics().resource_destroys
    );
    let input = TerrainSourceShadowDepthInput {
        shadow_depth_secondary_view: images[1].1,
        ..input
    };
    let replacement = executor
        .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
        .unwrap()
        .unwrap();
    assert_ne!(
        primary_before,
        replacement.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary)
    );
    let primary_raw = replacement
        .combined_sampler_for(TerrainSourceResourceRole::ShadowDepthRaw)
        .unwrap();
    let secondary_raw = replacement
        .combined_sampler_for(TerrainSourceResourceRole::ShadowDepthRawSecondary)
        .unwrap();
    assert_ne!(primary_raw, secondary_raw);
    assert_eq!(
        TerrainSourceResourceRole::ShadowDepthRawSecondary.expected_sampled_resource_shape(),
        replacement
            .availability()
            .resource_for(TerrainSourceResourceRole::ShadowDepthRawSecondary)
            .unwrap()
            .shape
    );
    let cached = executor
        .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
        .unwrap()
        .unwrap();
    assert_eq!(
        Some(secondary_raw),
        cached.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthRawSecondary)
    );
    executor
        .clear_candidate_source_shadow_depth_resources(&mut gal)
        .unwrap();
    for (texture, view) in images.into_iter().rev() {
        gal.destroy(view).unwrap();
        gal.destroy(texture).unwrap();
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}
