pub mod diagnostics;
pub mod manifest;
pub mod pass_graph;
pub mod preprocess;
pub mod programs;
pub mod resources;
pub mod source;
pub mod uniforms;

#[cfg(test)]
mod tests {
    use super::manifest::ShaderPackConfig;
    use super::manifest::ShaderPackManifest;
    use super::pass_graph::{
        builtin_terrain_material_pass_graph, AttachmentRole, LoadIntent, PassGraph, PassIdentity,
        ShaderPassDesc, StoreIntent,
    };
    use super::preprocess::{preprocess, PreprocessInput};
    use super::programs::{
        minimal_composite_color_grade_program, minimal_composite_depth_fog_program,
        minimal_deferred_lighting_program, minimal_final_copy_program,
        minimal_shadow_depth_program, minimal_terrain_cutout_program,
        minimal_terrain_solid_program, ProgramIdentity, ShaderStageKind,
    };
    use super::resources::{
        ShaderPackResourceManifest, ShaderPackResources, ShaderPackRuntimePlan,
    };
    use super::source::{ShaderPackSource, ShaderSourceFile};

    #[test]
    fn minimal_terrain_solid_program_is_rust_owned_and_backend_neutral() {
        let program = minimal_terrain_solid_program();
        assert_eq!(
            ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            program.identity
        );
        assert_eq!(ShaderStageKind::Vertex, program.vertex.stage);
        assert_eq!(ShaderStageKind::Fragment, program.fragment.stage);
        assert!(program.vertex.source.contains("WorldMeshVertices"));
        assert!(program.fragment.source.contains("sampler2D"));
        assert!(!program.vertex.source.contains("IrisRenderSystem"));
        assert!(!program.fragment.source.contains("IrisRenderSystem"));

        let cutout = minimal_terrain_cutout_program();
        assert_eq!(
            ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
            cutout.identity
        );
        assert_eq!(program.vertex.source, cutout.vertex.source);
        assert!(cutout.fragment.source.contains("discard"));
    }

    #[test]
    fn preprocessor_expands_includes_and_defines_without_java_state() {
        let source = ShaderPackSource::new(
            "test-pack",
            7,
            vec![
                ShaderSourceFile::new("common.glsl", "vec3 shade(vec3 v) { return v; }\n"),
                ShaderSourceFile::new("terrain.vsh", "#include \"common.glsl\"\nvoid main() {}\n"),
            ],
        )
        .unwrap();
        let output = preprocess(PreprocessInput {
            source: &source,
            entry: "terrain.vsh",
            defines: &[("VULKANIC_TEST", "1")],
        })
        .unwrap();
        assert!(output.contains("#define VULKANIC_TEST 1"));
        assert!(output.contains("vec3 shade"));
    }

    #[test]
    fn pass_graph_names_explicit_attachments_and_ordering() {
        let graph = PassGraph::new(vec![ShaderPassDesc {
            identity: PassIdentity::new("vulkanic:pass/terrain-solid-test"),
            label: "terrain-solid".to_string(),
            program: ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            colors: vec![AttachmentRole::GBufferAlbedo],
            depth: Some(AttachmentRole::Depth),
            load: LoadIntent::Load,
            store: StoreIntent::Store,
        }])
        .unwrap();
        assert_eq!(1, graph.passes().len());
        assert_eq!("terrain-solid", graph.passes()[0].label);
    }

    #[test]
    fn builtin_material_pass_graph_declares_g_buffer_and_final_output() {
        let graph = builtin_terrain_material_pass_graph().unwrap();
        assert_eq!(7, graph.passes().len());
        assert_eq!(
            "vulkanic:pass/shadow_depth",
            graph.passes()[0].identity.as_str()
        );
        assert_eq!(LoadIntent::Clear, graph.passes()[0].load);
        assert_eq!(Some(AttachmentRole::ShadowDepth), graph.passes()[0].depth);
        assert_eq!(
            ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            graph.passes()[1].program
        );
        assert_eq!(LoadIntent::Clear, graph.passes()[1].load);
        assert_eq!(
            "vulkanic:pass/terrain_opaque",
            graph.passes()[1].identity.as_str()
        );
        assert_eq!(
            "vulkanic:pass/terrain_cutout",
            graph.passes()[2].identity.as_str()
        );
        assert_eq!(LoadIntent::Load, graph.passes()[2].load);
        assert_eq!(
            vec![
                AttachmentRole::GBufferAlbedo,
                AttachmentRole::GBufferNormal,
                AttachmentRole::GBufferMaterialLight,
                AttachmentRole::GBufferWorldPosition,
            ],
            graph.passes()[1].colors
        );
        assert_eq!(
            "vulkanic:pass/deferred_lighting",
            graph.passes()[3].identity.as_str()
        );
        assert_eq!(
            vec![AttachmentRole::DeferredLitColor],
            graph.passes()[3].colors
        );
        assert_eq!(
            "vulkanic:pass/composite_0",
            graph.passes()[4].identity.as_str()
        );
        assert_eq!(vec![AttachmentRole::Composite0], graph.passes()[4].colors);
        assert_eq!(
            "vulkanic:pass/composite_1",
            graph.passes()[5].identity.as_str()
        );
        assert_eq!(vec![AttachmentRole::Composite1], graph.passes()[5].colors);
        assert_eq!(
            "vulkanic:pass/final_output",
            graph.passes()[6].identity.as_str()
        );
        let deferred = minimal_deferred_lighting_program();
        assert!(deferred.fragment.source.contains("AlbedoTex"));
        assert!(deferred.fragment.source.contains("NormalTex"));
        assert!(deferred.fragment.source.contains("ShadowDepthTex"));
        let color_grade = minimal_composite_color_grade_program();
        assert!(color_grade.fragment.source.contains("color_grade_params"));
        let fog = minimal_composite_depth_fog_program();
        assert!(fog.fragment.source.contains("WorldPositionTex"));
        let final_copy = minimal_final_copy_program();
        assert!(final_copy.fragment.source.contains("Tex0"));
        let shadow = minimal_shadow_depth_program();
        assert_eq!(
            ProgramIdentity::new("vulkanic:builtin/shadow_depth_v1"),
            shadow.identity
        );
        assert!(shadow.fragment.source.contains("discard"));
    }

    #[test]
    fn shader_pack_resource_manifest_is_versioned_and_backend_neutral() {
        let resources = ShaderPackResourceManifest::terrain_material_v1(12).unwrap();
        assert_eq!(12, resources.generation.0);
        assert!(resources
            .attachments
            .iter()
            .any(|attachment| attachment.as_str() == "vulkanic:attachment/shadow_depth"));
        assert!(resources
            .attachments
            .iter()
            .any(|attachment| attachment.as_str() == "vulkanic:attachment/g_buffer_albedo"));
        assert!(resources
            .attachments
            .iter()
            .any(|attachment| attachment.as_str() == "vulkanic:attachment/g_buffer_normal"));
        assert!(resources.attachments.iter().any(|attachment| {
            attachment.as_str() == "vulkanic:attachment/g_buffer_material_light"
        }));
        assert!(resources.attachments.iter().any(|attachment| {
            attachment.as_str() == "vulkanic:attachment/g_buffer_world_position"
        }));
        assert!(resources
            .attachments
            .iter()
            .any(|attachment| attachment.as_str() == "vulkanic:attachment/deferred_lit_color"));
        assert!(resources
            .attachments
            .iter()
            .any(|attachment| attachment.as_str() == "vulkanic:attachment/composite_0"));
        assert!(resources
            .attachments
            .iter()
            .any(|attachment| attachment.as_str() == "vulkanic:attachment/composite_1"));
        assert!(resources
            .materials
            .iter()
            .any(|material| material.as_str() == "minecraft:material/cutout_textured"));
        assert!(resources
            .samplers
            .iter()
            .any(|sampler| sampler.as_str() == "vulkanic:sampler/nearest_clamp"));
    }

    #[test]
    fn shader_pack_config_declares_ordered_multipass_chain() {
        let config = ShaderPackConfig::internal_shadow_composite_fixture(4).unwrap();
        let graph = config.pass_graph().unwrap();
        let passes = graph
            .passes()
            .iter()
            .map(|pass| pass.identity.as_str())
            .collect::<Vec<_>>();
        assert_eq!(
            passes,
            vec![
                "vulkanic:pass/shadow_depth",
                "vulkanic:pass/terrain_opaque",
                "vulkanic:pass/terrain_cutout",
                "vulkanic:pass/deferred_lighting",
                "vulkanic:pass/composite_0",
                "vulkanic:pass/composite_1",
                "vulkanic:pass/final_output",
            ]
        );
        assert!(config
            .passes
            .iter()
            .any(|pass| pass.reads.contains(&AttachmentRole::ShadowDepth)
                && pass.colors.contains(&AttachmentRole::DeferredLitColor)));
    }

    #[test]
    fn runtime_plan_owns_composite_chain_program_and_resource_selection() {
        let plan = ShaderPackRuntimePlan::terrain_material_multipass_v1(8).unwrap();
        assert_eq!(8, plan.generation);
        assert_eq!(7, plan.graph.passes().len());
        assert_eq!(
            "vulkanic:builtin/deferred_lighting_v1",
            plan.programs.deferred_lighting.identity.as_str()
        );
        assert_eq!(
            "vulkanic:builtin/composite_color_grade_v1",
            plan.programs.composite_0.identity.as_str()
        );
        assert_eq!(
            "vulkanic:builtin/composite_depth_fog_v1",
            plan.programs.composite_1.identity.as_str()
        );
        assert!(plan
            .declared_attachment_roles()
            .contains(&AttachmentRole::Composite1));
        assert!(plan
            .resources
            .programs
            .iter()
            .any(|program| { program.as_str() == plan.programs.final_output.identity.as_str() }));
    }

    #[test]
    fn shader_pack_config_rejects_reads_before_writes_and_duplicate_writers() {
        let mut read_before_write = ShaderPackConfig::internal_shadow_composite_fixture(5).unwrap();
        read_before_write.passes.swap(3, 4);
        let error = read_before_write.validate().unwrap_err();
        assert!(error.to_string().contains("before"));

        let mut duplicate_writer = ShaderPackConfig::internal_shadow_composite_fixture(6).unwrap();
        duplicate_writer.passes[4].colors = vec![AttachmentRole::DeferredLitColor];
        let error = duplicate_writer.validate().unwrap_err();
        assert!(error.to_string().contains("multiple"));
    }

    #[test]
    fn resource_update_rolls_back_malformed_generation() {
        let mut resources = ShaderPackResources::default();
        resources
            .apply_generation(
                1,
                ShaderPackManifest::new(
                    "valid-pack",
                    1,
                    vec![ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1")],
                )
                .unwrap(),
            )
            .unwrap();
        let active = resources.active_generation();
        let stale_manifest = ShaderPackManifest::new(
            "bad-pack",
            2,
            vec![ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1")],
        )
        .unwrap();
        assert!(resources.apply_generation(1, stale_manifest).is_err());
        assert_eq!(active, resources.active_generation());
    }

    #[test]
    fn malformed_resource_generation_rolls_back_to_last_valid_manifest() {
        let mut resources = ShaderPackResources::default();
        let manifest = ShaderPackManifest::new(
            "valid-pack",
            4,
            vec![
                ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
                ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
            ],
        )
        .unwrap();
        resources
            .apply_resource_generation(
                4,
                manifest,
                ShaderPackResourceManifest::terrain_material_v1(4).unwrap(),
            )
            .unwrap();
        let bad_manifest = ShaderPackManifest::new(
            "bad-pack",
            5,
            vec![ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1")],
        )
        .unwrap();
        assert!(resources
            .apply_resource_generation(
                5,
                bad_manifest,
                ShaderPackResourceManifest::terrain_material_v1(6).unwrap(),
            )
            .is_err());
        assert_eq!(Some(4), resources.active_generation());
        assert_eq!("valid-pack", resources.active().unwrap().manifest.name());
    }
}
