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
    use super::manifest::ShaderPackManifest;
    use super::pass_graph::{
        builtin_terrain_material_pass_graph, AttachmentRole, LoadIntent, PassGraph, PassIdentity,
        ShaderPassDesc, StoreIntent,
    };
    use super::preprocess::{preprocess, PreprocessInput};
    use super::programs::{
        minimal_terrain_cutout_program, minimal_terrain_solid_program, ProgramIdentity,
        ShaderStageKind,
    };
    use super::resources::{ShaderPackResourceManifest, ShaderPackResources};
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
            color: AttachmentRole::GBufferColor(0),
            depth: Some(AttachmentRole::Depth),
            load: LoadIntent::Load,
            store: StoreIntent::Store,
        }])
        .unwrap();
        assert_eq!(1, graph.passes().len());
        assert_eq!("terrain-solid", graph.passes()[0].label);
    }

    #[test]
    fn builtin_material_pass_graph_declares_opaque_cutout_and_final_copy() {
        let graph = builtin_terrain_material_pass_graph().unwrap();
        assert_eq!(3, graph.passes().len());
        assert_eq!(
            "vulkanic:pass/terrain_opaque",
            graph.passes()[0].identity.as_str()
        );
        assert_eq!(
            ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            graph.passes()[0].program
        );
        assert_eq!(LoadIntent::Clear, graph.passes()[0].load);
        assert_eq!(
            "vulkanic:pass/terrain_cutout",
            graph.passes()[1].identity.as_str()
        );
        assert_eq!(LoadIntent::Load, graph.passes()[1].load);
        assert_eq!(AttachmentRole::FinalColor, graph.passes()[2].color);
    }

    #[test]
    fn shader_pack_resource_manifest_is_versioned_and_backend_neutral() {
        let resources = ShaderPackResourceManifest::terrain_material_v1(12).unwrap();
        assert_eq!(12, resources.generation.0);
        assert!(resources
            .attachments
            .iter()
            .any(|attachment| attachment.as_str() == "vulkanic:attachment/world_material_color"));
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
