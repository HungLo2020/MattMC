use crate::render::shaderpack::runtime::*;
use crate::render::vulkanic::test_support::{presentation_capabilities, vulkan_capabilities};
use crate::render::vulkanic::commands::{CommandList, CommandListDesc, SubmissionBatch};
use crate::render::vulkanic::handles::HandleKind;
use crate::render::vulkanic::resources::{
    Extent3d, TextureDesc, TextureDimension, TextureFormat, TextureUsage, TextureViewDesc,
};
use crate::render::shaderpack::source::assets::{
    ShaderPackAssetFile, ShaderPackAssetUpdate,
};
use crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test;
use crate::render::shaderpack::source::ShaderSourceFile;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailabilitySet,
    TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
};
use std::path::PathBuf;

mod source_pack_probe;
mod pre_terrain;
mod legacy_samplers;
mod legacy_images;
mod prepared_programs;

fn gal() -> VulkanicGal {
    crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ))
}

#[test]
fn persistent_screen_targets_use_shader_read_after_first_frame() {
    assert_eq!(TextureUsageState::Undefined, screen_texture_before(false));
    assert_eq!(TextureUsageState::ShaderRead, screen_texture_before(true));
}

#[test]
fn fabulous_translucency_handoff_excludes_each_external_family_from_deferred_blending() {
    let draw = |stratum| TerrainMeshDraw {
        shadow: None,
        pipeline: Handle::NULL,
        offscreen_pipeline: None,
        pipeline_layout: Handle::NULL,
        resource_set: Handle::NULL,
        resource_set_dynamic_offsets: Vec::new().into(),
        shader_resource_set: None,
        index_buffer: Handle::NULL,
        index_offset: 0,
        index_type: IndexType::U32,
        index_count: 3,
        instance_count: 1,
        indexed_indirect: None,
        stratum,
        material_mode: TerrainMaterialPassMode::Translucent,
        shadow_participation: TerrainShadowParticipation::Unavailable,
    };
    let terrain = draw(WORLD_STRATUM_TERRAIN);
    let entity = draw(WORLD_STRATUM_ENTITY_MESH);

    assert!(ShaderPackRuntimeExecutor::translucent_draw_is_external(
        &terrain, false, true,
    ));
    assert!(ShaderPackRuntimeExecutor::translucent_draw_is_external(
        &entity, true, false,
    ));
    assert!(!ShaderPackRuntimeExecutor::translucent_draw_is_external(
        &terrain, false, false,
    ));
    assert!(!ShaderPackRuntimeExecutor::translucent_draw_is_external(
        &entity, false, true,
    ));
}

fn copied_png_assets_for(source: &ShaderPackSource) -> ShaderPackAssets {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../resources/shaders/ComplementaryHungLoIfied/shaders");
    let bindings = TerrainShaderPackAssetBindings::from_source(source).unwrap();
    let mut files = Vec::new();
    for (_, path) in bindings.samplers() {
        files.push(ShaderPackAssetFile::new(
            path,
            fs::read(root.join(path)).unwrap(),
        ));
        let sidecar = format!("{path}.mcmeta");
        if root.join(&sidecar).is_file() {
            files.push(ShaderPackAssetFile::new(
                &sidecar,
                fs::read(root.join(&sidecar)).unwrap(),
            ));
        }
    }
    ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        files,
    })
    .unwrap()
}
use crate::render::shaderpack::contracts::terrain::bundled_complementary_hung_loified_source;

#[test]
fn runtime_executor_owns_expected_gameplay_pass_order() {
    let executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
    let passes = executor
        .plan()
        .graph
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
            "vulkanic:pass/terrain_translucent",
            "vulkanic:pass/composite_0",
            "vulkanic:pass/composite_1",
            "vulkanic:pass/final_output",
        ]
    );
    assert!(executor.validate_terrain_material_graph().is_ok());
}

#[test]
fn source_shadow_pass_accepts_an_independent_caster_stream() {
    let executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
    let caster = TerrainShadowMeshDraw {
        shadow: TerrainShadowDraw {
            pipeline: Handle::NULL,
            pipeline_layout: Handle::NULL,
            resource_set: Handle::NULL,
            resource_set_dynamic_offsets: Vec::new().into(),
            shader_resource_set: None,
        },
        index_buffer: Handle::NULL,
        index_offset: 0,
        index_type: IndexType::U32,
        index_count: 3,
        instance_count: 1,
        indexed_indirect: None,
        material_mode: TerrainMaterialPassMode::Opaque,
    };
    let mut operations = Vec::new();
    executor
        .append_terrain_source_shadow_pass(
            &mut operations,
            TerrainSourceShadowPassTargets {
                shadow_depth_texture: Handle::NULL,
                shadow_depth_opaque_texture: Handle::NULL,
                shadow_extent: Extent3d { width: 0, height: 0, depth: 1 },
                shadow_depth_view: Handle::NULL,
                shadow_color_texture: Handle::NULL,
                shadow_color_view: Handle::NULL,
                shadow_light_shaft_texture: Handle::NULL,
                shadow_light_shaft_view: Handle::NULL,
                shadow_target: Handle::NULL,
                shadow_pass: Handle::NULL,
                initialized: false,
            },
            &[],
            &[caster],
            &[],
        )
        .unwrap();
    assert!(operations.iter().any(|op| matches!(
        op,
        CommandOp::DrawIndexed {
            indices: 3,
            instances: 1
        }
    )));
    let white = Some(ClearColor {
        r: 1.0,
        g: 1.0,
        b: 1.0,
        a: 1.0,
    });
    assert!(operations.iter().any(|op| matches!(
        op,
        CommandOp::BeginPass { colors, .. }
            if colors.len() == 2
                && colors.iter().all(|attachment| attachment.clear_color == white)
    )));
}

#[test]
fn runtime_owns_only_generation_coherent_vanilla_lightmap_bytes() {
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
    let frame = VanillaLightmapFrame {
        generation: 4,
        inputs: crate::render::shaderpack::vanilla::lightmap::VanillaLightmapInputs {
            ambient_light_factor: 0.0,
            sky_factor: 1.0,
            block_factor: 1.5,
            night_vision_factor: 0.0,
            darkness_scale: 0.0,
            darken_world_factor: 0.0,
            brightness_factor: 0.0,
            sky_light_color: [1.0; 3],
            ambient_color: [1.0; 3],
        },
    };
    assert_eq!(
        Some(VanillaLightmapCacheUpdate::Replaced),
        executor.observe_vanilla_lightmap(3, Some(frame)).unwrap()
    );
    assert_eq!(3, executor.vanilla_lightmap_cache().world_generation());
    assert_eq!(4, executor.vanilla_lightmap_cache().lightmap_generation());
    assert_eq!(
        16 * 16 * 4,
        executor.vanilla_lightmap_cache().rgba8().len(),
        "the runtime stores copied semantic image bytes, not a Java texture object"
    );
    assert_eq!(
        Some(VanillaLightmapCacheUpdate::Unchanged),
        executor.observe_vanilla_lightmap(3, Some(frame)).unwrap()
    );
    assert!(executor.observe_vanilla_lightmap(0, Some(frame)).is_err());
}

#[test]
fn dropped_lightmap_upload_is_rerecorded_and_never_promoted_unrecorded() {
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
    let frame = VanillaLightmapFrame {
        generation: 4,
        inputs: crate::render::shaderpack::vanilla::lightmap::VanillaLightmapInputs {
            ambient_light_factor: 0.0,
            sky_factor: 1.0,
            block_factor: 1.5,
            night_vision_factor: 0.0,
            darkness_scale: 0.0,
            darken_world_factor: 0.0,
            brightness_factor: 0.0,
            sky_light_color: [1.0; 3],
            ambient_color: [1.0; 3],
        },
    };
    executor.observe_vanilla_lightmap(3, Some(frame)).unwrap();
    let mut gal = gal();
    let copies = |ops: &[CommandOp]| {
        ops.iter()
            .filter(|op| matches!(op, CommandOp::CopyBufferToTexture(_)))
            .count()
    };

    // A provisional assembly stages, then drops its operations.
    let mut dropped = Vec::new();
    assert!(executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut dropped)
        .unwrap());
    assert_eq!(1, copies(&dropped));
    executor.forget_recorded_vanilla_lightmap_upload();
    // Nothing records it again: confirmation must not promote an image
    // that no submitted command list uploaded.
    executor
        .confirm_vanilla_lightmap_submission(&mut gal)
        .unwrap();
    assert!(!executor.has_pending_vanilla_lightmap_submission());
    assert!(executor.vanilla_lightmap_binding(false).is_none());
    executor.retire_replaced_vanilla_lightmaps(&mut gal).unwrap();

    // Same shape, but a later consumer re-records the upload once.
    let mut dropped = Vec::new();
    executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut dropped)
        .unwrap();
    executor.forget_recorded_vanilla_lightmap_upload();
    let pending = executor.vanilla_lightmap_binding(true).unwrap();
    let mut submitted = Vec::new();
    assert!(executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut submitted)
        .unwrap());
    assert_eq!(1, copies(&submitted));
    let mut same_submission = Vec::new();
    assert!(!executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut same_submission)
        .unwrap());
    assert!(same_submission.is_empty());
    executor
        .confirm_vanilla_lightmap_submission(&mut gal)
        .unwrap();
    assert_eq!(Some(pending), executor.vanilla_lightmap_binding(false));
}

#[test]
fn runtime_stages_and_confirms_lightmap_residency_only_with_submission_success() {
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
    let frame = VanillaLightmapFrame {
        generation: 4,
        inputs: crate::render::shaderpack::vanilla::lightmap::VanillaLightmapInputs {
            ambient_light_factor: 0.0,
            sky_factor: 1.0,
            block_factor: 1.5,
            night_vision_factor: 0.0,
            darkness_scale: 0.0,
            darken_world_factor: 0.0,
            brightness_factor: 0.0,
            sky_light_color: [1.0; 3],
            ambient_color: [1.0; 3],
        },
    };
    executor.observe_vanilla_lightmap(3, Some(frame)).unwrap();
    let mut gal = gal();
    let mut ops = Vec::new();
    assert!(executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut ops)
        .unwrap());
    assert!(executor.has_pending_vanilla_lightmap_submission());
    assert!(executor.vanilla_lightmap_residency.is_none());
    assert!(executor.vanilla_lightmap_binding(false).is_none());
    let pending_binding = executor.vanilla_lightmap_binding(true).unwrap();
    assert_eq!(3, pending_binding.world_generation);
    assert_eq!(4, pending_binding.lightmap_generation);
    assert!(!pending_binding.texture_view.is_null());
    assert!(!pending_binding.sampler.is_null());
    let mut same_submission_ops = Vec::new();
    assert!(
        !executor
            .stage_vanilla_lightmap_residency(&mut gal, &mut same_submission_ops)
            .unwrap(),
        "a second consumer in the same combined submission reuses the pending residency"
    );
    assert!(same_submission_ops.is_empty());
    gal.submit(SubmissionBatch {
        label: "test.runtime-lightmap".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "test.runtime-lightmap.commands".to_string(),
            operations: ops,
        })],
    })
    .unwrap();
    executor
        .confirm_vanilla_lightmap_submission(&mut gal)
        .unwrap();
    assert!(!executor.has_pending_vanilla_lightmap_submission());
    assert!(executor.vanilla_lightmap_residency.is_some());
    assert_eq!(
        Some(pending_binding),
        executor.vanilla_lightmap_binding(false),
        "confirmation preserves the same Rust-owned semantic lightmap binding"
    );

    let mut duplicate_ops = Vec::new();
    assert!(!executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut duplicate_ops)
        .unwrap());
    assert!(duplicate_ops.is_empty());

    executor
        .observe_vanilla_lightmap(
            3,
            Some(VanillaLightmapFrame {
                generation: 5,
                ..frame
            }),
        )
        .unwrap();
    let mut replacement_ops = Vec::new();
    assert!(executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut replacement_ops)
        .unwrap());
    executor.discard_vanilla_lightmap_submission(&mut gal);
    assert!(!executor.has_pending_vanilla_lightmap_submission());
    assert!(executor.vanilla_lightmap_residency.is_some());
}

#[test]
fn lightmap_consumer_sets_retire_before_a_replaced_residency_view() {
    use crate::render::vulkanic::resources::{
        PipelineStageFlags, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc,
    };

    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
    let frame = VanillaLightmapFrame {
        generation: 1,
        inputs: crate::render::shaderpack::vanilla::lightmap::VanillaLightmapInputs {
            ambient_light_factor: 0.0,
            sky_factor: 1.0,
            block_factor: 1.0,
            night_vision_factor: 0.0,
            darkness_scale: 0.0,
            darken_world_factor: 0.0,
            brightness_factor: 0.0,
            sky_light_color: [1.0; 3],
            ambient_color: [1.0; 3],
        },
    };
    executor.observe_vanilla_lightmap(1, Some(frame)).unwrap();
    let mut gal = gal();
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "test.lightmap.consumer.layout".to_owned(),
            bindings: vec![
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::SampledTexture,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::Sampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
            ],
        })
        .unwrap();
    let mut ops = Vec::new();
    executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut ops)
        .unwrap();
    let first = executor
        .vanilla_lightmap_resource_set(&mut gal, layout, true)
        .unwrap()
        .unwrap();
    assert_eq!(1, first.lightmap_generation);
    gal.submit(SubmissionBatch {
        label: "test.lightmap.consumer".to_owned(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "test.lightmap.consumer.commands".to_owned(),
            operations: ops,
        })],
    })
    .unwrap();
    executor
        .confirm_vanilla_lightmap_submission(&mut gal)
        .unwrap();
    executor
        .observe_vanilla_lightmap(
            1,
            Some(VanillaLightmapFrame {
                generation: 2,
                ..frame
            }),
        )
        .unwrap();
    let mut replacement_ops = Vec::new();
    executor
        .stage_vanilla_lightmap_residency(&mut gal, &mut replacement_ops)
        .unwrap();
    executor
        .vanilla_lightmap_resource_set(&mut gal, layout, true)
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "test.lightmap.consumer.replace".to_owned(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "test.lightmap.consumer.replace.commands".to_owned(),
            operations: replacement_ops,
        })],
    })
    .unwrap();
    executor
        .confirm_vanilla_lightmap_submission(&mut gal)
        .unwrap();
}

#[test]
fn owned_source_generation_is_discovered_without_admitting_source_execution() {
    let source = bundled_complementary_hung_loified_source(11).unwrap();
    let executor =
        ShaderPackRuntimeExecutor::terrain_material_fixture_from_source(&source).unwrap();
    let contract = executor.plan().terrain_contract.as_ref().unwrap();
    assert_eq!(source.generation(), contract.generation);
    assert_eq!(source.name(), contract.pack_name);
    assert_eq!(
        "vulkanic:builtin/terrain_opaque_v1",
        executor.plan().programs.terrain_opaque.identity.as_str()
    );
    assert!(executor
        .plan()
        .terrain_contract_diagnostic_json()
        .contains("\"selected_source_plan_prepared\":false"));
}

#[test]
fn owned_source_candidate_is_observed_without_replacing_the_fixture_plan() {
    let source = bundled_complementary_hung_loified_source(13).unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let fixture_program = executor.plan().programs.terrain_opaque.identity.clone();

    executor.observe_source_candidate(&source);

    let candidate = executor.source_candidate();
    assert!(
        matches!(
            candidate,
            TerrainSourceCandidateState::Discovered {
                generation: 13,
                requires_colored_voxel_light: true,
                ..
            }
        ),
        "{candidate:?}"
    );
    assert!(executor
        .candidate_shader_binding(0)
        .unwrap_err()
        .to_string()
        .contains("complete owned colored voxel-light volume"));
    assert!(matches!(
        candidate,
        TerrainSourceCandidateState::Discovered {
            source_shadow_summary: None,
            source_shadow_preprocess_error: Some(error),
            source_shadow_output_count: None,
            source_shadow_lowering_error: None,
            ..
        } if error.contains("missing shadow source for Default")
    ));
    assert_eq!(
        fixture_program,
        executor.plan().programs.terrain_opaque.identity
    );
    assert_eq!(
        Some("lib/textures/noise.png"),
        executor
            .source_asset_binding_plan()
            .and_then(|bindings| bindings.sampler_path("noisetex"))
    );
    let (translucent_discovered, translucent_reason, unsupported_count) =
        executor.source_candidate_translucent_diagnostic();
    assert!(!translucent_discovered);
    assert!(unsupported_count.is_none());
    assert!(translucent_reason
        .expect("the curated normal-only fixture must identify its missing translucent stage")
        .contains("missing translucent terrain fragment source"));
}

#[test]
fn material_texture_wrapper_rejects_a_non_material_semantic_role_before_handle_use() {
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let mut gal = gal();
    let error = executor
        .ensure_candidate_source_material_texture_resources(
            &mut gal,
            TerrainSourceMaterialTextureInput {
                role: TerrainSourceResourceRole::Lightmap,
                shader_pack_generation: 1,
                world_generation: 1,
                mesh_asset_generation: 1,
                texture_view: Handle::NULL,
                sampler: Handle::NULL,
            },
        )
        .unwrap_err();
    assert!(error.to_string().contains("unsupported semantic role"));
}

#[test]
fn active_normal_map_binding_owns_a_distinct_semantic_material_wrapper() {
    let source = ShaderPackSource::new(
        "normal-map-resource",
        29,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nuniform sampler2D normals;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); color.rgb += texture2D(normals, texCoord).rgb * 0.0; if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nnormals=material_normal_map\n",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let mut gal = gal();
    executor.observe_source_candidate(&source);
    assert!(executor
        .candidate_source_requires_resource(TerrainSourceResourceRole::MaterialNormalMap));

    let texture = gal
        .create_texture(TextureDesc {
            label: "normal-map-resource.texture".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
        })
        .unwrap();
    let view = gal
        .create_texture_view(TextureViewDesc {
            label: "normal-map-resource.view".to_string(),
            texture,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let sampler = gal
        .create_sampler(SamplerDesc {
            label: "normal-map-resource.sampler".to_string(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })
        .unwrap();
    let resources = executor
        .ensure_candidate_source_material_texture_resources(
            &mut gal,
            TerrainSourceMaterialTextureInput {
                role: TerrainSourceResourceRole::MaterialNormalMap,
                shader_pack_generation: source.generation(),
                world_generation: 3,
                mesh_asset_generation: 5,
                texture_view: view,
                sampler,
            },
        )
        .unwrap()
        .expect("the active normal-map source role must receive a wrapper");
    assert!(resources
        .combined_sampler_for(TerrainSourceResourceRole::MaterialNormalMap)
        .is_some());
    executor
        .clear_candidate_source_material_texture_resources(&mut gal)
        .unwrap();
    gal.destroy(sampler).unwrap();
    gal.destroy(view).unwrap();
    gal.destroy(texture).unwrap();
}

#[test]
fn complete_source_discovery_records_paired_lowering_provenance_without_admission() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    let contract = match executor.source_candidate() {
        TerrainSourceCandidateState::Discovered { contract, .. } => contract,
        candidate => panic!("expected a discovered puddle source candidate, got {candidate:?}"),
    };
    assert!(
        contract.require_selected_subset().is_ok(),
        "the owned puddle resource must replace only the obsolete feature gate"
    );

    assert_eq!(
        ("prepared", None),
        executor.candidate_textured_material_source_diagnostic(),
        "the bundled scoped gbuffers_textured pair must be retained before a future named-target writer may be implemented"
    );
    assert_eq!(
        ("prepared", None),
        executor.candidate_entity_source_diagnostic(),
        "gbuffers_entities readiness must reflect the owned entity stream and named writer without selecting a route by itself"
    );
    assert_eq!(
        ("prepared", None),
        executor.candidate_hand_source_diagnostic(),
        "gbuffers_hand must be source-lowered and resource-resolved without accidentally selecting the Java/Iris hand route"
    );
    let hand_program = executor
        .prepared_lowered_hand_source_program()
        .unwrap()
        .expect("the complete bundled hand contract must prepare without selecting a route");
    assert!(hand_program.identity.as_str().contains("hand_source_gen"));
    assert_eq!(
        vec![
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 6),
            (TerrainPassOutput::ViewSpaceNormal, 5),
        ],
        hand_program.named_output_color_slots(),
        "gbuffers_hand DRAWBUFFERS:06 must retain named source output meanings"
    );
    let mut gal = gal();
    let hand_targets = executor
        .stage_source_color_targets(
            &mut gal,
            73,
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap()
        .expect("the bundled hand contract requires Rust-owned named color targets");
    let hand_outputs = executor
        .resolve_hand_source_color_outputs(&hand_program, &hand_targets)
        .unwrap();
    let hand_color_resources = executor
        .stage_terrain_source_color_resources_for_hand(&mut gal, &hand_program, &hand_targets)
        .unwrap();
    assert!(
        hand_color_resources
            .combined_sampler_for(TerrainSourceResourceRole::MaterialTexture)
            .is_none(),
        "hand named-color staging must not manufacture a local material texture; the future writer owns that separate Rust resource"
    );
    assert_eq!(
        hand_program.named_output_color_slots(),
        hand_outputs
            .iter()
            .map(|attachment| (attachment.output, attachment.source_slot))
            .collect::<Vec<_>>(),
        "hand output slots must resolve through named Rust-owned targets rather than Iris attachments"
    );
    assert_eq!(
        AttachmentLoadOp::Load,
        TerrainSourceColorPassPhase::Hands.color_load_op(&hand_outputs[0]),
        "first-person output must compose over the completed world color"
    );
    assert_eq!(
        AttachmentLoadOp::Clear,
        TerrainSourceColorPassPhase::Hands.depth_load_op(),
        "first-person output must begin a fresh Rust-owned depth domain"
    );
    assert_eq!(
        TextureUsageState::Undefined,
        TerrainSourceColorPassPhase::Hands.depth_before(),
        "the private first-person depth image must transition explicitly from its initial state"
    );
    let entity_program = executor
        .prepared_lowered_entity_source_program()
        .unwrap()
        .expect("the complete bundled entity contract must prepare without admitting a draw");
    assert!(entity_program
        .identity
        .as_str()
        .contains("entity_source_gen"));
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialTexture),
        entity_program.opaque_resource_bindings.role_for("tex"),
        "the selected entity pass must retain its local-material sampler role rather than terrain atlas semantics"
    );
    assert_eq!(
        ("prepared", None),
        executor.candidate_weather_source_diagnostic(),
        "the scoped weather stage must remain independently prepared without enabling a draw"
    );
    let (cloud_state, cloud_reason) = executor.candidate_cloud_source_diagnostic();
    assert_eq!("suppressed", cloud_state);
    assert!(
        cloud_reason.is_none(),
        "the bundled inactive cloud branch must report its source-declared suppression without inventing a failed writer: {cloud_reason:?}"
    );
    let textured_program = executor
        .prepared_lowered_textured_material_source_program()
        .unwrap()
        .expect("the complete bundled textured contract must prepare without admitting a draw");
    assert_eq!(
        vec![
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 6),
            (TerrainPassOutput::TranslucencyAuxiliary, 3),
        ],
        textured_program.named_output_color_slots(),
        "gbuffers_textured DRAWBUFFERS:063 must retain its material and translucency meanings"
    );
    let weather_program = executor
        .prepared_lowered_weather_source_program()
        .unwrap()
        .expect("complete weather contract must prepare without admitting a draw");
    assert!(weather_program
        .identity
        .as_str()
        .contains("weather_source_gen"));
    assert_eq!(0, weather_program.lit_color_output_slot);
    assert_eq!(0.1, weather_program.alpha_discard_threshold());
    assert_eq!(
        crate::render::shaderpack::contracts::weather::WeatherBlend::SourceAlphaOver,
        weather_program.blend
    );

    assert!(
        matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                textured_material_contract: Some(material_contract),
                textured_material_contract_error: None,
                textured_material_lowered_pair: Some(_),
                textured_material_source_resource_binding_count: Some(count),
                textured_material_source_resource_binding_error: None,
                textured_material_source_resource_bindings: Some(_),
                entity_contract: Some(entity_contract),
                entity_contract_error: None,
                entity_lowered_pair: Some(_),
                entity_source_resource_binding_count: Some(entity_count),
                entity_source_resource_binding_error: None,
                entity_source_resource_bindings: Some(_),
                weather_contract: Some(weather_contract),
                weather_contract_error: None,
                weather_lowered_pair: Some(_),
                weather_source_resource_binding_count: Some(weather_count),
                weather_source_resource_binding_error: None,
                weather_source_resource_bindings: Some(_),
                source_summary: Some(_),
                source_preprocess_error: None,
                source_shadow_summary: Some(shadow_summary),
                source_shadow_preprocess_error: None,
                source_shadow_output_count: Some(2),
                source_shadow_lowering_error: None,
                source_lowering_summary: Some(summary),
                source_lowering_error: None,
                source_resource_binding_count: Some(8),
                source_resource_binding_error: None,
                ..
            } if summary.varying_count > 0
                && summary.opaque_resource_count > summary.active_opaque_resource_count
                && summary.active_opaque_resource_count > 0
                && *count > 0
                && *weather_count > 0
                && *entity_count > 0
                && material_contract.scope == TerrainProgramScope::Overworld
                && entity_contract.scope == TerrainProgramScope::Overworld
                && weather_contract.scope == TerrainProgramScope::Overworld
                && shadow_summary.vertex_entry == "world0/shadow.vsh"
                && shadow_summary.fragment_entry == "world0/shadow.fsh"
        ),
        "{:#?}",
        executor.source_candidate()
    );
    let entity_base_color_role = match executor.source_candidate() {
        TerrainSourceCandidateState::Discovered {
            entity_source_resource_bindings: Some(bindings),
            ..
        } => bindings.role_for("tex"),
        candidate => panic!("expected discovered entity binding plan, got {candidate:?}"),
    };
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialTexture),
        entity_base_color_role,
        "gbuffers_entities must retain its local material sampler semantics instead of inheriting terrain atlas ownership"
    );
    assert_eq!(
        executor.plan().programs.terrain_opaque.identity.as_str(),
        "vulkanic:builtin/terrain_opaque_v1"
    );
    let shadow = executor
        .prepared_lowered_shadow_source_program()
        .unwrap()
        .expect("complete scoped shadow source must remain available for a later owned pass");
    assert!(shadow.identity.as_str().contains("shadow_source_gen"));
    assert!(shadow.fragment.source.contains("out_shadow_color"));
    assert!(!shadow.fragment.source.contains("out_terrain_lit_color"));
    assert!(!shadow.vertex.source.contains("puddle_img"));
    assert!(!shadow.vertex.source.contains("imageStore"));
}

#[test]
fn selected_vanilla_cloud_style_prepares_without_admitting_a_cloud_route() {
    let source = complete_bundled_pack_source_for_test();
    let mut files = source.files();
    files.push(ShaderSourceFile::new(
        crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
        "CLOUD_STYLE_DEFINE=50\n",
    ));
    let source =
        ShaderPackSource::new("bundled-vanilla-cloud-style", source.generation(), files)
            .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    assert_eq!(
        ("prepared", None),
        executor.candidate_cloud_source_diagnostic(),
        "a selected vanilla cloud branch must have paired source lowering and semantic resource bindings before any writer is staged"
    );
    let cloud = executor
        .prepared_lowered_cloud_source_program()
        .unwrap()
        .expect("prepared cloud source must not require route admission");
    assert!(cloud.identity.as_str().contains("cloud_source_gen"));
    assert_eq!(
        vec![
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 6),
            (TerrainPassOutput::TranslucencyAuxiliary, 3),
        ],
        cloud.named_output_color_slots()
    );
}

#[test]
fn entity_source_color_outputs_resolve_through_named_targets() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let program = executor
        .prepared_lowered_entity_source_program()
        .unwrap()
        .expect("the complete source fixture must prepare the entity contract");
    let mut gal = gal();
    let targets = executor
        .stage_source_color_targets(
            &mut gal,
            73,
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap()
        .expect("selected source must stage Rust-owned named targets");
    let outputs = executor
        .resolve_entity_source_color_outputs(&program, &targets)
        .unwrap();
    assert_eq!(
        vec![
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 6),
            (TerrainPassOutput::ViewSpaceNormal, 5),
        ],
        outputs
            .iter()
            .map(|output| (output.output, output.source_slot))
            .collect::<Vec<_>>()
    );
    assert_eq!(
        targets.target("primary").unwrap().current_attachment_view,
        outputs[0].view
    );
    executor.discard_source_color_targets_submission(&mut gal);
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn active_puddle_shadow_writer_requires_the_owned_semantic_resource() {
    let complete = complete_bundled_pack_source_for_test();
    let mut files = complete.files();
    files.push(ShaderSourceFile::new(
        crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
        "RAIN_PUDDLES=1\nDETAIL_QUALITY=3\n",
    ));
    let source = ShaderPackSource::new("complete-puddles", 92, files).unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    assert!(
        matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                source_shadow_preprocess_error: None,
                source_shadow_lowering_error: None,
                source_lowered_shadow_pair: Some(shadow),
                ..
            } if shadow.owned_storage_roles() == [TerrainSourceResourceRole::PuddleOccupancy]
        ),
        "{:#?}",
        executor.source_candidate()
    );
    assert!(executor
        .candidate_source_requires_resource(TerrainSourceResourceRole::PuddleOccupancy,));
    let shadow = executor
        .prepared_lowered_shadow_source_program()
        .unwrap()
        .expect("the active puddle source shadow pair must remain privately prepared");
    assert!(!shadow.vertex.source.contains("puddle_img"));
    assert!(!shadow.vertex.source.contains("imageStore"));
}

#[test]
fn source_shadow_depth_resources_are_generation_bound_compare_samplers() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let mut gal = gal();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    let texture = gal
        .create_texture(TextureDesc {
            label: "source-shadow-depth-test.texture".to_string(),
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
            label: "source-shadow-depth-test.view".to_string(),
            texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let input = TerrainSourceShadowDepthInput {
        shader_pack_generation: source.generation(),
        world_generation: 4,
        shader_graph_generation: 9,
        shadow_depth_view: view,
        shadow_depth_secondary_view: Handle::NULL,
    };

    let first = executor
        .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
        .unwrap()
        .expect("source declares compare and post-terrain raw shadow samplers");
    let primary = first
        .combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary)
        .unwrap();
    let secondary = first
        .combined_sampler_for(TerrainSourceResourceRole::ShadowDepthSecondary)
        .unwrap();
    assert_ne!(primary, secondary);
    assert_eq!(3, first.len());
    let raw = first.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthRaw).unwrap();
    assert_ne!(primary, raw, "post-terrain raw depth cannot inherit a compare sampler");
    assert_eq!(
        Some(TerrainSourceResourceRole::ShadowDepthPrimary.expected_sampled_resource_shape()),
        first
            .availability()
            .resource_for(TerrainSourceResourceRole::ShadowDepthPrimary)
            .map(|resource| resource.shape)
    );

    let cached = executor
        .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
        .unwrap()
        .unwrap();
    assert_eq!(
        Some(primary),
        cached.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary)
    );

    let replaced = executor
        .ensure_candidate_source_shadow_depth_resources(
            &mut gal,
            TerrainSourceShadowDepthInput {
                shader_graph_generation: 10,
                ..input
            },
        )
        .unwrap()
        .unwrap();
    assert_ne!(
        Some(primary),
        replaced.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary)
    );
    executor
        .clear_candidate_source_shadow_depth_resources(&mut gal)
        .unwrap();
    gal.destroy(view).unwrap();
    gal.destroy(texture).unwrap();
}

#[test]
fn source_shadow_color_requires_an_owned_color_view_and_retires_generation_bound_wrappers() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let mut gal = gal();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    let texture = gal
        .create_texture(TextureDesc {
            label: "source-shadow-color-test.texture".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 16,
                height: 16,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::ColorAttachment,
                TextureUsage::Sampled,
                TextureUsage::TransferSrc,
            ],
        })
        .unwrap();
    let view = gal
        .create_texture_view(TextureViewDesc {
            label: "source-shadow-color-test.view".to_string(),
            texture,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let sampler = gal
        .create_sampler(SamplerDesc {
            label: "source-shadow-color-test.sampler".to_string(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })
        .unwrap();
    let input = TerrainSourceShadowColorInput {
        shader_pack_generation: source.generation(),
        world_generation: 4,
        shader_graph_generation: 9,
        shadow_color_view: view,
        shadow_color_secondary_view: view,
        sampler,
    };

    let first = executor
        .ensure_candidate_source_shadow_color_resources(&mut gal, input)
        .unwrap()
        .expect("source declares shadowcolor0 as a color resource");
    let combined = first
        .combined_sampler_for(TerrainSourceResourceRole::ShadowColor)
        .unwrap();
    assert_eq!(2, first.len());
    let secondary = first.combined_sampler_for(TerrainSourceResourceRole::ShadowColorSecondary).unwrap();
    assert_ne!(combined, secondary, "post-terrain shadow color retains its distinct role");
    assert_eq!(
        Some(TerrainSourceResourceRole::ShadowColor.expected_sampled_resource_shape()),
        first
            .availability()
            .resource_for(TerrainSourceResourceRole::ShadowColor)
            .map(|resource| resource.shape)
    );
    assert_eq!(
        Some(combined),
        executor
            .ensure_candidate_source_shadow_color_resources(&mut gal, input)
            .unwrap()
            .unwrap()
            .combined_sampler_for(TerrainSourceResourceRole::ShadowColor)
    );

    let replaced = executor
        .ensure_candidate_source_shadow_color_resources(
            &mut gal,
            TerrainSourceShadowColorInput {
                shader_graph_generation: 10,
                ..input
            },
        )
        .unwrap()
        .unwrap();
    assert_ne!(
        Some(combined),
        replaced.combined_sampler_for(TerrainSourceResourceRole::ShadowColor)
    );
    executor
        .clear_candidate_source_shadow_color_resources(&mut gal)
        .unwrap();
    gal.destroy(sampler).unwrap();
    gal.destroy(view).unwrap();
    gal.destroy(texture).unwrap();
}

#[test]
fn source_shadow_color_resource_set_preserves_two_declared_color_roles() {
    let primary_view = Handle::new(HandleKind::TextureView, 91, 1).unwrap();
    let secondary_view = Handle::new(HandleKind::TextureView, 92, 1).unwrap();
    let sampler = Handle::new(HandleKind::Sampler, 93, 1).unwrap();
    let primary_combined = Handle::new(HandleKind::CombinedTextureSampler, 94, 1).unwrap();
    let secondary_combined = Handle::new(HandleKind::CombinedTextureSampler, 95, 1).unwrap();
    let resources = TerrainSourceShadowColorResources {
        shader_pack_generation: 7,
        world_generation: 8,
        shader_graph_generation: 9,
        shadow_color_view: primary_view,
        shadow_color_secondary_view: secondary_view,
        sampler,
        combined_samplers: BTreeMap::from([
            (TerrainSourceResourceRole::ShadowColor, primary_combined),
            (
                TerrainSourceResourceRole::ShadowColorSecondary,
                secondary_combined,
            ),
        ]),
    };

    let set = resources.semantic_resource_set().unwrap();
    assert_eq!(2, set.len());
    assert_eq!(
        Some(primary_combined),
        set.combined_sampler_for(TerrainSourceResourceRole::ShadowColor)
    );
    assert_eq!(
        Some(secondary_combined),
        set.combined_sampler_for(TerrainSourceResourceRole::ShadowColorSecondary)
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::ShadowColorSecondary.expected_sampled_resource_shape()),
        set.availability()
            .resource_for(TerrainSourceResourceRole::ShadowColorSecondary)
            .map(|resource| resource.shape)
    );
}

#[test]
fn source_shadow_color_wrapper_binds_both_source_declared_roles() {
    let source = ShaderPackSource::new(
        "two-shadow-color-source",
        41,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nuniform sampler2D shadowcolor0;\nuniform sampler2D shadowcolor1;\nuniform sampler2DShadow shadowtex0;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); color.rgb += (texture2D(shadowcolor0, texCoord).rgb + texture2D(shadowcolor1, texCoord).rgb) * 0.0; if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nshadowcolor0=shadow_color\nshadowcolor1=shadow_color_secondary\nshadowtex0=shadow_depth_primary\n",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);
    assert!(
        executor.candidate_source_requires_resource(TerrainSourceResourceRole::ShadowColor),
        "source discovery did not retain the primary shadow-color binding: {:?}",
        executor.source_candidate()
    );
    assert!(
        executor.candidate_source_requires_resource(
            TerrainSourceResourceRole::ShadowColorSecondary
        ),
        "source discovery did not retain the secondary shadow-color binding: {:?}",
        executor.source_candidate()
    );

    let mut gal = gal();
    let create_view = |gal: &mut VulkanicGal, label: &str| {
        let texture = gal
            .create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: 4,
                    height: 4,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::ColorAttachment, TextureUsage::Sampled],
            })
            .unwrap();
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: format!("{label}.view"),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .unwrap();
        (texture, view)
    };
    let (primary_texture, primary_view) = create_view(&mut gal, "source-shadow-primary");
    let (secondary_texture, secondary_view) = create_view(&mut gal, "source-shadow-secondary");
    let sampler = gal
        .create_sampler(SamplerDesc {
            label: "source-shadow-color-pair.sampler".to_string(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })
        .unwrap();
    let set = executor
        .ensure_candidate_source_shadow_color_resources(
            &mut gal,
            TerrainSourceShadowColorInput {
                shader_pack_generation: source.generation(),
                world_generation: 4,
                shader_graph_generation: 9,
                shadow_color_view: primary_view,
                shadow_color_secondary_view: secondary_view,
                sampler,
            },
        )
        .unwrap()
        .expect("both declared semantic shadow color resources must be prepared");
    assert_eq!(2, set.len());
    assert_ne!(
        set.combined_sampler_for(TerrainSourceResourceRole::ShadowColor),
        set.combined_sampler_for(TerrainSourceResourceRole::ShadowColorSecondary),
    );

    executor
        .clear_candidate_source_shadow_color_resources(&mut gal)
        .unwrap();
    gal.destroy(sampler).unwrap();
    for (view, texture) in [
        (secondary_view, secondary_texture),
        (primary_view, primary_texture),
    ] {
        gal.destroy(view).unwrap();
        gal.destroy(texture).unwrap();
    }
}

#[test]
fn source_discovery_is_cached_by_pack_generation_and_world_scope() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let terrain = executor.source_candidate.clone();
    let distant_horizons = executor.distant_horizons_source_candidate.clone();
    assert!(executor.source_candidate_matches(&source, TerrainProgramScope::Overworld));
    assert!(executor
        .distant_horizons_source_candidate_matches(&source, TerrainProgramScope::Overworld));

    // A repeated render-frame observation must retain the already lowered
    // semantic candidates rather than re-expanding the configured pack.
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    assert_eq!(terrain, executor.source_candidate);
    assert_eq!(distant_horizons, executor.distant_horizons_source_candidate);

    // Scope remains part of the cache key: a dimension transition must
    // rediscover semantic source paths even with the same pack generation.
    assert!(!executor.source_candidate_matches(&source, TerrainProgramScope::Nether));
    assert!(!executor
        .distant_horizons_source_candidate_matches(&source, TerrainProgramScope::Nether));
}

#[test]
fn source_main_depth_exposes_only_confirmed_snapshot_roles() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    assert!(executor.candidate_source_requires_resource(TerrainSourceResourceRole::MainDepth));
    assert!(executor.candidate_source_requires_resource(
        TerrainSourceResourceRole::MainDepthBeforeTranslucency
    ));

    let mut gal = gal();
    let create_depth_view = |gal: &mut VulkanicGal, label: &str| {
        let texture = gal
            .create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent: Extent3d {
                    width: 16,
                    height: 16,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            })
            .unwrap();
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: format!("{label}.view"),
                texture,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .unwrap();
        (texture, view)
    };
    let (main_texture, main_view) = create_depth_view(&mut gal, "source-main-depth.main");
    let (before_texture, before_view) = create_depth_view(&mut gal, "source-main-depth.before");
    let (replacement_texture, replacement_view) =
        create_depth_view(&mut gal, "source-main-depth.replacement");
    let sampler = gal
        .create_sampler(SamplerDesc {
            label: "source-main-depth.sampler".to_string(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })
        .unwrap();
    let base_input = TerrainSourceMainDepthInput {
        shader_pack_generation: source.generation(),
        world_generation: 4,
        shader_graph_generation: 9,
        main_depth_view: main_view,
        before_translucency_view: None,
        previous_view: None,
        sampler,
    };
    let first = executor
        .ensure_candidate_source_main_depth_resources(&mut gal, base_input)
        .unwrap()
        .expect("source declares a current main depth role");
    assert!(first
        .combined_sampler_for(TerrainSourceResourceRole::MainDepth)
        .is_some());
    assert!(first
        .combined_sampler_for(TerrainSourceResourceRole::MainDepthBeforeTranslucency)
        .is_none());
    assert!(first
        .combined_sampler_for(TerrainSourceResourceRole::MainDepthPrevious)
        .is_none());

    let confirmed = executor
        .ensure_candidate_source_main_depth_resources(
            &mut gal,
            TerrainSourceMainDepthInput {
                before_translucency_view: Some(before_view),
                previous_view: None,
                ..base_input
            },
        )
        .unwrap()
        .expect("confirmed depth snapshots must become separate semantic roles");
    assert_eq!(2, confirmed.len());
    for role in [
        TerrainSourceResourceRole::MainDepth,
        TerrainSourceResourceRole::MainDepthBeforeTranslucency,
    ] {
        assert!(confirmed.combined_sampler_for(role).is_some());
    }

    let replacement_error = executor
        .ensure_candidate_source_main_depth_resources(
            &mut gal,
            TerrainSourceMainDepthInput {
                before_translucency_view: Some(replacement_view),
                previous_view: None,
                ..base_input
            },
        )
        .expect_err("an established temporal source-depth role must not retarget in place");
    assert!(replacement_error
        .to_string()
        .contains("cannot replace a view in place"));

    executor
        .clear_candidate_source_main_depth_resources(&mut gal)
        .unwrap();
    gal.destroy(sampler).unwrap();
    for (view, texture) in [
        (replacement_view, replacement_texture),
        (before_view, before_texture),
        (main_view, main_texture),
    ] {
        gal.destroy(view).unwrap();
        gal.destroy(texture).unwrap();
    }
}

#[test]
fn copied_source_assets_are_generation_coherent_private_runtime_preparation() {
    let source = complete_bundled_pack_source_for_test();
    let assets = copied_png_assets_for(&source);
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let mut gal = gal();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    assert!(executor
        .ensure_candidate_source_asset_resources(&mut gal, &assets)
        .unwrap());
    assert!(executor
        .candidate_source_asset_resource_count()
        .is_some_and(|count| count > 0));
    let shadow_asset_role = executor.source_resource_binding_plans().into_iter()
        .find_map(|plan| plan.role_for("gaux4")).expect("shadow source binds gaux4");
    assert!(
        executor
            .source_asset_resources
            .as_ref()
            .and_then(|resources| resources.combined_sampler_for(shadow_asset_role))
            .is_some(),
        "shadow-only gaux4 must be retained from the separately lowered shadow plan"
    );
    assert!(!executor
        .ensure_candidate_source_asset_resources(&mut gal, &assets)
        .unwrap());

    let mismatched = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "different-pack".to_string(),
        generation: source.generation(),
        files: Vec::new(),
    })
    .unwrap();
    assert!(executor
        .ensure_candidate_source_asset_resources(&mut gal, &mismatched)
        .unwrap_err()
        .to_string()
        .contains("do not match source candidate"));

    executor
        .clear_candidate_source_asset_resources(&mut gal)
        .unwrap();
    assert_eq!(None, executor.candidate_source_asset_resource_count());
    executor.destroy(&mut gal).unwrap();
    assert!(gal.metrics().resource_destroys > 0);
}

#[test]
fn complete_source_distinguishes_active_terrain_resources_from_global_declarations() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    let TerrainSourceCandidateState::Discovered {
        source_lowered_pair: Some(lowered),
        source_lowering_summary: Some(summary),
        source_uniform_requirement_summary: Some(uniform_summary),
        source_resource_binding_count: Some(8),
        source_resource_binding_error: None,
        ..
    } = executor.source_candidate()
    else {
        panic!("expected complete source to remain a diagnostic-only candidate");
    };
    assert_eq!(
        vec![
            "atlasSize",
            "cameraPosition",
            "cameraPositionFract",
            "darknessLightFactor",
            "far",
            "fogColor",
            "frameCounter",
            "frameTimeCounter",
            "framemod8",
            "gbufferModelView",
            "gbufferModelViewInverse",
            "gbufferProjection",
            "gbufferProjectionInverse",
            "heldBlockLightValue",
            "heldBlockLightValue2",
            "heldItemId",
            "heldItemId2",
            "inBasaltDeltas",
            "inCrimsonForest",
            "inDry",
            "inNetherWastes",
            "inSnowy",
            "inSoulValley",
            "inWarpedForest",
            "isEyeInWater",
            "moonPhase",
            "nightVision",
            "rainFactor",
            "relativeEyePosition",
            "screenBrightness",
            "shadowModelView",
            "shadowModelViewInverse",
            "shadowProjection",
            "shadowProjectionInverse",
            "skyColor",
            "sunAngle",
            "viewHeight",
            "viewWidth",
            "worldDay",
            "worldTime",
        ],
        lowered
            .uniform_contract()
            .fields()
            .iter()
            .map(|field| field.name())
            .collect::<Vec<_>>(),
        "the source-derived environment contract must not be silently narrowed"
    );
    assert_eq!(27, summary.opaque_resource_count);
    assert_eq!(8, summary.active_opaque_resource_count);
    assert_eq!(40, uniform_summary.field_count);
    assert_eq!(40, uniform_summary.resolved_field_count);
    assert!(uniform_summary.unresolved_field_names.is_empty());
    assert_eq!(
        vec![
            "floodfill_sampler",
            "floodfill_sampler_copy",
            "noisetex",
            "shadowcolor0",
            "shadowtex0",
            "shadowtex1",
            "specular",
            "tex",
        ],
        lowered
            .opaque_resource_contract()
            .active_resources()
            .map(|resource| resource.name())
            .collect::<Vec<_>>()
    );
    assert_eq!(
        8,
        executor
            .source_resource_binding_plan()
            .unwrap()
            .bindings()
            .len()
    );
    assert!(matches!(
        executor.source_candidate(),
        TerrainSourceCandidateState::Discovered {
            source_shadow_resource_binding_count: Some(count),
            source_shadow_resource_binding_error: None,
            source_shadow_resource_bindings: Some(_),
            ..
        } if *count > 0
    ));
    assert!(executor
        .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
        .unwrap()
        .is_some());
}

#[test]
fn declared_source_resources_progress_through_private_candidate_preparation() {
    let source = ShaderPackSource::new(
        "declared-resource-source",
        19,
        vec![
            ShaderSourceFile::new(
                "program/gbuffers_terrain.glsl",
                "#version 130\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\n",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define SHADOW_QUALITY 2\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);

    let candidate = executor.source_candidate();
    assert!(
        matches!(
            candidate,
            TerrainSourceCandidateState::Discovered {
                source_lowering_summary: Some(_),
                source_lowering_error: None,
                source_resource_binding_count: Some(1),
                source_resource_binding_error: None,
                ..
            }
        ),
        "{candidate:?}"
    );
    assert!(executor
        .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
        .unwrap()
        .is_some());
    let preparation_error = executor
        .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
        .unwrap_err()
        .to_string();
    assert!(preparation_error.contains("compatibility_fragment_outputs"));
    let bindings = executor.source_resource_binding_plan().unwrap().bindings();
    assert_eq!(1, bindings.len());
    assert_eq!("tex", bindings[0].resource_name());
    assert_eq!(TerrainSourceResourceRole::MaterialAtlas, bindings[0].role());
    assert_eq!(
        crate::render::shaderpack::lowering::TerrainSourceOpaqueResourceKind::CombinedTextureSampler,
        bindings[0].kind()
    );
    assert_eq!(0, bindings[0].binding());
    assert_eq!(
        "vulkanic:builtin/terrain_opaque_v1",
        executor.plan().programs.terrain_opaque.identity.as_str()
    );
}

#[test]
fn fully_lowered_source_is_retained_for_private_preparation_only() {
    let source = ShaderPackSource::new(
        "retained-lowered-source",
        31,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);

    assert!(matches!(
        executor.source_candidate(),
        TerrainSourceCandidateState::Discovered {
            source_uniform_requirement_summary: Some(summary),
            source_uniform_requirement_error: None,
            ..
        } if summary.field_count == 2
            && summary.resolved_field_count == 2
            && summary.unresolved_field_names.is_empty()
    ));

    let prepared = executor
        .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
        .unwrap()
        .expect("complete paired source must be retained privately");
    assert_eq!(
        "vulkanic:shader-pack/retained-lowered-source/terrain_opaque_source_gen31",
        prepared.identity.as_str()
    );
    assert!(prepared.vertex.source.contains("#version 450"));
    assert!(prepared.fragment.source.contains("#version 450"));
    assert_eq!(1, prepared.opaque_resource_bindings.bindings().len());
    assert_eq!(
        crate::render::shaderpack::lowering::TerrainSourceOpaqueResourceKind::CombinedTextureSampler,
        prepared.opaque_resource_bindings.bindings()[0].kind()
    );
    assert_eq!(
        "vulkanic:builtin/terrain_opaque_v1",
        executor.plan().programs.terrain_opaque.identity.as_str(),
        "source preparation must not replace the executable fixture plan"
    );
}

#[test]
fn scoped_complementary_candidate_uses_world_entry_pair_not_shared_include_body() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    assert!(matches!(
        executor.source_candidate(),
        TerrainSourceCandidateState::Discovered {
            source_summary: Some(summary),
            source_preprocess_error: None,
            source_lowering_summary: Some(_),
            source_lowering_error: None,
            source_shadow_summary: Some(shadow_summary),
            source_shadow_preprocess_error: None,
            source_shadow_lowering_error: None,
            ..
        } if summary.vertex_entry == "world0/gbuffers_terrain.vsh"
            && summary.fragment_entry == "world0/gbuffers_terrain.fsh"
            && shadow_summary.vertex_entry == "world0/shadow.vsh"
            && shadow_summary.fragment_entry == "world0/shadow.fsh"
    ));
    assert!(executor
        .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
        .unwrap()
        .is_some());
    assert!(executor
        .prepared_lowered_shadow_source_program()
        .unwrap()
        .is_some());
}

#[test]
fn scoped_distant_horizons_candidate_retains_its_own_source_pair_without_selecting_a_route() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let fixture_program = executor.plan().programs.terrain_opaque.identity.clone();

    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );

    assert!(
        matches!(
            executor.distant_horizons_source_candidate(),
            DistantHorizonsSourceCandidateState::Discovered {
                generation,
                source_summary: Some(summary),
                source_preprocess_error: None,
                source_lowering_error: None,
                source_uniform_requirement_summary: Some(uniforms),
                source_uniform_requirement_error: None,
                source_resource_binding_count: Some(_),
                source_resource_binding_error: None,
                source_color_target_count: Some(8),
                source_color_target_error: None,
                source_color_target_gal_schema_error: None,
                source_color_targets: Some(targets),
                depth_consumer_preparation,
                ..
            } if *generation == source.generation()
                && summary.vertex_entry == "world0/dh_terrain.vsh"
                && summary.fragment_entry == "world0/dh_terrain.fsh"
                && uniforms.field_count == uniforms.resolved_field_count
                && targets.target("primary").is_some()
                && depth_consumer_preparation.iter().any(|consumer| {
                    consumer.stage_path == "world0/deferred1.fsh"
                        && consumer.reads_opaque_depth
                        && consumer.source_preprocess_error.is_none()
                        && consumer.source_lowering_error.is_none()
                        && consumer.source_resource_binding_error.is_none()
                        && !consumer.source_output_roles.is_empty()
                        && consumer.source_program_preparation_error.is_none()
                        && consumer.source_program_identity.as_deref().is_some_and(|identity|
                            identity.contains("world0-deferred1-source-gen91")
                        )
                        && consumer.source_program.as_ref().is_some_and(|program|
                            program.identity.as_str().contains("world0-deferred1-source-gen91")
                        )
                        && consumer
                            .source_feedback_roles
                            .iter()
                            .any(|role| role == "shader_pack_color:primary")
                        && consumer.source_summary.as_ref().is_some_and(|summary| {
                            summary.vertex_entry == "world0/deferred1.vsh"
                                && summary.fragment_entry == "world0/deferred1.fsh"
                        })
                })
                && depth_consumer_preparation.iter().any(|consumer| {
                    consumer.stage_path == "world0/composite.fsh"
                        && consumer.reads_opaque_depth
                        && consumer.reads_depth_before_translucency
                        && consumer.source_preprocess_error.is_none()
                })
        ),
        "{:#?}",
        executor.distant_horizons_source_candidate()
    );

    let prepared = executor
        .prepared_lowered_distant_horizons_source_program()
        .unwrap()
        .expect("complete DH pair must remain available for Rust-owned target preparation");
    assert_eq!(
        "vulkanic:shader-pack/complementaryhungloified-complete-test/distant_horizons_opaque_source_gen91",
        prepared.identity.as_str()
    );
    assert_eq!(32, prepared.execution_interface.vertex_stride);
    assert_eq!(128, prepared.execution_interface.column_frame_bytes);
    assert!(prepared
        .vertex
        .source
        .contains("VulkanicDistantHorizonsVertices"));
    assert!(prepared
        .fragment
        .source
        .contains("out_distant_horizons_lit_color"));
    let depth_consumers = executor
        .prepared_lowered_distant_horizons_depth_consumers()
        .unwrap();
    assert_eq!(5, depth_consumers.len());
    assert!(depth_consumers.iter().any(|program| {
        program
            .identity
            .as_str()
            .contains("world0-deferred1-source-gen91")
    }));
    assert!(depth_consumers.iter().any(|program| {
        program
            .identity
            .as_str()
            .contains("world0-composite-source-gen91")
    }));
    let complete_chain = executor
        .prepared_lowered_post_terrain_fullscreen_programs()
        .expect("every scoped fullscreen stage must either retain a lowered program or expose its first precise preparation failure");
    assert_eq!(
        8,
        complete_chain.len(),
        "the bundled Overworld pack has one complete retained post-terrain source chain",
    );
    assert_eq!(
        "vulkanic:shader-pack/complementaryhungloified-complete-test/world0-final-source-gen91",
        complete_chain.last().unwrap().identity.as_str(),
    );
    assert_eq!(
        fixture_program,
        executor.plan().programs.terrain_opaque.identity,
        "DH preparation must not replace the active near-terrain fixture or select a DH route",
    );
}

#[test]
fn dh_water_discovery_never_reuses_the_opaque_dh_source_pair() {
    let complete = complete_bundled_pack_source_for_test();
    let source = ShaderPackSource::new(
        "complete-dh-without-water",
        92,
        complete
            .files()
            .into_iter()
            .filter(|file| !file.path.contains("dh_water"))
            .collect(),
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );

    assert!(matches!(
        executor.distant_horizons_source_candidate(),
        DistantHorizonsSourceCandidateState::Discovered {
            contract,
            translucent_contract: DistantHorizonsTranslucentSourceCandidate::Unavailable,
            ..
        } if contract.program_path == "world0/dh_terrain.fsh"
    ));
}

#[test]
fn dh_water_runtime_preparation_retains_a_distinct_lowered_program() {
    let complete = complete_bundled_pack_source_for_test();
    let source = ShaderPackSource::new(
        "complete-dh-water-runtime",
        92,
        complete
            .files()
            .into_iter()
            .chain(std::iter::once(ShaderSourceFile::new(
                crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
                "DISTANT_HORIZONS=1\nSHADOW_QUALITY=-1\nFXAA_DEFINE=-1\nCOLORED_LIGHTING=0\nENTITY_SHADOWS_DEFINE=-1\nPLAYER_SHADOW=-1\nRAIN_PUDDLES=0\n",
            )))
            .collect(),
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );

    let program = executor
        .prepared_lowered_distant_horizons_translucent_source_program()
        .unwrap()
        .expect("selected dh_water source must retain its prepared Rust program");
    assert_eq!(
        crate::render::shaderpack::contracts::distant_horizons::DistantHorizonsPassKind::Translucent,
        program.pass_kind
    );
    assert_eq!(
        Some(
            crate::render::shaderpack::contracts::terrain::TerrainTranslucentBlend::SourceAlphaOver
        ),
        program.translucent_blend
    );
    assert!(program
        .identity
        .as_str()
        .contains("distant_horizons_translucent_source_gen92"));
    assert!(program.fragment.source.contains("depthtex1"));
}

#[test]
fn distant_horizons_source_target_preparation_is_private_and_submission_confirmed() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let fixture_program = executor.plan().programs.terrain_opaque.identity.clone();
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let mut gal = gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };

    let targets = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("a discovered DH source candidate must stage named private targets");
    assert_eq!(8, targets.targets().count());
    let primary = targets.target("primary").unwrap();
    assert_eq!(TextureFormat::R11fG11fB10f, primary.format);
    assert!(primary.previous_view.is_some());
    assert_eq!(9, primary.mip_levels);
    assert_eq!(
        fixture_program,
        executor.plan().programs.terrain_opaque.identity,
        "staging source images must not select a source terrain/DH route",
    );
    assert!(!executor.has_pending_vanilla_lightmap_submission());

    executor.confirm_source_color_targets_submission(&mut gal);
    let reused = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .unwrap();
    assert_eq!(primary, reused.target("primary").unwrap());
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn complete_source_color_target_identity_uses_every_scoped_fullscreen_stage() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let expected = match executor.source_candidate() {
        TerrainSourceCandidateState::Discovered {
            post_terrain_preparation,
            post_terrain_preparation_error: None,
            ..
        } => post_terrain_preparation,
        candidate => panic!("expected complete normal source discovery, got {candidate:#?}"),
    };
    assert_eq!(8, expected.len());
    let mut feedback = expected
        .iter()
        .flat_map(|stage| stage.source_feedback_roles.iter())
        .map(|role| shader_pack_color_name_from_role(role).unwrap())
        .collect::<Vec<_>>();
    feedback.sort();
    feedback.dedup();
    let mut mipmapped = expected
        .iter()
        .flat_map(|stage| stage.source_mipmap_roles.iter())
        .map(|role| shader_pack_color_name_from_role(role).unwrap())
        .collect::<Vec<_>>();
    mipmapped.sort();
    mipmapped.dedup();

    let mut gal = gal();
    let targets = executor
        .stage_complete_source_color_targets(
            &mut gal,
            73,
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap()
        .expect("complete source discovery must stage one full-chain target generation");
    assert_eq!(feedback, targets.identity.feedback_target_names);
    assert_eq!(mipmapped, targets.identity.mipmapped_target_names);
    assert!(
        !targets.identity.feedback_target_names.is_empty(),
        "the complete chain must retain explicit feedback history rather than silently using the DH subset"
    );
    executor.discard_source_color_targets_submission(&mut gal);
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn bundled_overworld_sky_initializer_lowers_as_an_owned_source_stage() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    let sky = executor
        .prepared_lowered_pre_terrain_sky_program()
        .expect("a declared sky stage must either lower or expose a precise error")
        .expect("Complementary's overworld scope declares gbuffers_skybasic");
    assert_eq!("world0/gbuffers_skybasic.fsh", sky.source_stage_path);
    let horizon = executor.prepared_lowered_pre_terrain_horizon_program().unwrap().unwrap();
    assert_eq!(FullscreenSourceRasterPrimitive::ShaderPackHorizon, horizon.raster_primitive);
    assert_eq!(2_076, horizon.raster_primitive.vertex_count());
    assert_eq!(sky.source_stage_path, horizon.source_stage_path);
    assert_ne!(sky.identity, horizon.identity, "cached pipelines must retain distinct geometry and uniforms");
    assert_eq!(sky.outputs, horizon.outputs);
    assert_eq!(
        FullscreenSourceRasterPrimitive::VanillaSkyDisc,
        sky.raster_primitive,
        "the source sky must receive Frozen's disc geometry and depth field"
    );
    assert!(sky
        .vertex
        .source
        .contains("vulkanic_source_fullscreen_transform"));
    assert!(sky
        .vertex
        .source
        .contains("vulkanic_source_fullscreen_vertex_color"));
    assert!(sky
        .fragment
        .source
        .contains("out_vulkanic_source_color_primary"));
}

#[test]
fn bundled_overworld_celestial_stage_lowers_with_owned_quad_semantics() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    let celestial = executor
        .prepared_lowered_pre_terrain_celestial_program()
        .expect("a declared celestial stage must either lower or expose a precise error")
        .expect("Complementary's overworld scope declares gbuffers_skytextured");
    assert_eq!(
        "world0/gbuffers_skytextured.fsh",
        celestial.source_stage_path
    );
    assert_eq!(
        FullscreenSourceRasterPrimitive::VanillaCelestialQuad,
        celestial.raster_primitive
    );
    assert!(celestial
        .vertex
        .source
        .contains("vulkanic_source_fullscreen_celestial_position"));
    // One program serves sun, moon and the End sky box (selector 2).
    assert!(celestial.vertex.source.contains("vulkanic_source_celestial_end_sky()"));
    assert_eq!(36, celestial.raster_primitive.vertex_count());
    assert!(celestial
        .vertex
        .source
        .contains("uniform VulkanicSourceTerrainUniforms"));
    for declaration in [
        "float vulkanic_source_celestial_time_of_day;",
        "int moonPhase;",
        "int vulkanic_source_celestial_is_moon;",
        "float vulkanic_source_celestial_alpha;",
        "float vulkanic_source_celestial_sun_path_rotation;",
    ] {
        assert!(
            celestial.vertex.source.contains(declaration),
            "owned celestial vertex GLSL is missing {declaration}"
        );
    }
    for name in [
        "vulkanic_source_celestial_time_of_day",
        "vulkanic_source_celestial_is_moon",
        "vulkanic_source_celestial_alpha",
        "vulkanic_source_celestial_sun_path_rotation",
    ] {
        assert!(
            celestial
                .execution_interface
                .scalar_uniform_fields
                .iter()
                .any(|field| field.name() == name),
            "owned celestial program is missing injected semantic field {name}"
        );
        assert!(
            celestial.vertex.source.contains(name),
            "owned celestial vertex GLSL is missing injected semantic declaration {name}"
        );
    }
}

#[test]
fn complete_source_color_target_staging_rejects_an_incomplete_world_chain() {
    let complete = complete_bundled_pack_source_for_test();
    let source = ShaderPackSource::new(
        "complete-pack-without-world-final",
        complete.generation(),
        complete
            .files()
            .into_iter()
            .filter(|file| file.path != "world0/final.vsh" && file.path != "world0/final.fsh")
            .collect(),
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let mut gal = gal();
    let error = executor
        .stage_complete_source_color_targets(
            &mut gal,
            73,
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("complete shader-pack fullscreen chain"),
        "incomplete source discovery must not allocate partial full-chain targets: {error}"
    );
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn complete_fullscreen_staging_rejects_missing_semantic_inputs_without_route_selection() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let fixture_program = executor.plan().programs.terrain_opaque.identity.clone();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let mut gal = gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let targets = executor
        .stage_complete_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("complete source discovery must stage a private target generation");
    let error = executor
        .stage_complete_post_terrain_execution_plans(&mut gal, &targets, |_| &[], extent)
        .unwrap_err();
    assert!(
        error.to_string().contains("semantic")
            || error.to_string().contains("resource"),
        "the complete chain must reject a missing source input rather than selecting a partial route: {error}"
    );
    assert_eq!(
        fixture_program,
        executor.plan().programs.terrain_opaque.identity,
        "private fullscreen staging must not replace the active fixture program or select gameplay execution"
    );
    executor.discard_source_color_targets_submission(&mut gal);
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn normal_terrain_and_distant_horizons_resolve_the_same_named_color_generation() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let program = executor
        .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
        .unwrap()
        .expect("normal terrain source must prepare before named target resolution");
    let mut gal = gal();
    let targets = executor
        .stage_source_color_targets(
            &mut gal,
            73,
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap()
        .expect("shared source discovery must stage one named color generation");
    let attachments = executor
        .resolve_terrain_source_color_outputs(&program, &targets)
        .expect("normal terrain outputs must resolve through the shared target generation");
    assert_eq!(3, attachments.len());
    assert_eq!(
        "primary",
        attachments[0].role.shader_pack_color_name().unwrap()
    );
    assert_eq!(
        "material_auxiliary",
        attachments[1].role.shader_pack_color_name().unwrap()
    );
    assert_eq!(
        "normal_scene",
        attachments[2].role.shader_pack_color_name().unwrap()
    );
    assert_eq!(
        targets.target("primary").unwrap().current_attachment_view,
        attachments[0].view
    );
    assert_eq!(
        targets
            .target("material_auxiliary")
            .unwrap()
            .current_attachment_view,
        attachments[1].view
    );
    assert_eq!(
        targets
            .target("normal_scene")
            .unwrap()
            .current_attachment_view,
        attachments[2].view
    );
    executor.discard_source_color_targets_submission(&mut gal);
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn normal_terrain_source_pass_uses_named_color_targets_and_explicit_depth() {
    use crate::render::vulkanic::resources::{
        AccessFlags, ComputePipelineDesc, PipelineLayoutDesc, PipelineStageFlags,
        ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc,
        ResourceSetDesc, ShaderCodeFormat, ShaderModuleDesc, ShaderStage,
    };
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let program = executor
        .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
        .unwrap()
        .expect("normal terrain source must prepare before pass construction");
    let mut gal = gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let targets = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("selected source must stage named color targets");
    let color_attachments = executor
        .resolve_terrain_source_color_outputs(&program, &targets)
        .unwrap();
    let depth_texture = gal
        .create_texture(crate::render::vulkanic::resources::TextureDesc {
            label: "normal-terrain-source-depth".to_string(),
            dimension: crate::render::vulkanic::resources::TextureDimension::D2,
            format: crate::render::vulkanic::resources::TextureFormat::Depth32Float,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                crate::render::vulkanic::resources::TextureUsage::DepthStencilAttachment,
                crate::render::vulkanic::resources::TextureUsage::Sampled,
            ],
        })
        .unwrap();
    let depth_view = gal
        .create_texture_view(crate::render::vulkanic::resources::TextureViewDesc {
            label: "normal-terrain-source-depth.view".to_string(),
            texture: depth_texture,
            format: crate::render::vulkanic::resources::TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let target = gal
        .create_render_target(crate::render::vulkanic::resources::RenderTargetDesc {
            label: "normal-terrain-source-target".to_string(),
            color_views: color_attachments
                .iter()
                .map(|attachment| attachment.view)
                .collect(),
            depth_stencil_view: Some(depth_view),
            extent,
        })
        .unwrap();
    let pass = gal
        .create_render_pass(crate::render::vulkanic::resources::RenderPassDesc {
            label: "normal-terrain-source-pass".to_string(),
            target,
            color_formats: color_attachments
                .iter()
                .map(|attachment| attachment.format)
                .collect(),
            depth_format: Some(crate::render::vulkanic::resources::TextureFormat::Depth32Float),
        })
        .unwrap();
    let mut operations = Vec::new();
    let transaction = executor
        .begin_source_color_transaction(
            &mut gal,
            &targets,
            ShaderPackColorClearValues {
                fog_color: ClearColor {
                    r: 0.1,
                    g: 0.2,
                    b: 0.3,
                    a: 1.0,
                },
            },
            &mut operations,
        )
        .unwrap();
    // Model the sky's sampled main-depth access before terrain clears it.
    // Submit the recorded commands so hazard validation checks the dependency,
    // rather than only checking the shape of the generated barrier.
    let sampled_layout = gal.create_resource_layout(ResourceLayoutDesc {
        label: "sky-depth-layout".into(),
        bindings: vec![ResourceBindingDesc {
            binding: 0, kind: ResourceBindingKind::SampledTexture,
            stages: PipelineStageFlags::COMPUTE, array_count: 1,
            optional: false, dynamic_offset_count: 0,
        }],
    }).unwrap();
    let sampled_set = gal.create_resource_set(ResourceSetDesc {
        label: "sky-depth-set".into(), layout: sampled_layout,
        bindings: vec![ResourceBinding {
            binding: 0, array_index: 0, resource: depth_view,
            kind: ResourceBindingKind::SampledTexture, access: AccessFlags::READ,
            dynamic_offsets: Vec::new(), buffer_range: None,
        }],
    }).unwrap();
    let sampled_pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
        label: "sky-depth-pipeline-layout".into(), resource_layouts: vec![sampled_layout],
    }).unwrap();
    let sampled_shader = gal.create_shader_module(ShaderModuleDesc {
        label: "sky-depth-shader".into(), stage: ShaderStage::Compute,
        code_format: ShaderCodeFormat::Spirv, code: vec![3, 2, 35, 7],
        entry_point: "main".into(),
    }).unwrap();
    let sampled_pipeline = gal.create_compute_pipeline(ComputePipelineDesc {
        label: "sky-depth-pipeline".into(), layout: sampled_pipeline_layout,
        shader: sampled_shader,
    }).unwrap();
    operations.extend([
        CommandOp::BindComputePipeline(sampled_pipeline),
        CommandOp::BindResourceSet {
            pipeline_layout: sampled_pipeline_layout, set_index: 0,
            set: sampled_set, dynamic_offsets: Vec::new(),
        },
        CommandOp::Dispatch { groups_x: 1, groups_y: 1, groups_z: 1 },
    ]);
    let mut initialized_attachments = color_attachments.clone();
    transaction.resolve_terrain_color_clear_policy(&mut initialized_attachments).unwrap();
    let initializer_boundary = operations.len();
    executor
        .append_terrain_source_color_pass(
            &mut operations,
            &TerrainSourceColorPassTargets {
                phase: TerrainSourceColorPassPhase::BootstrapAfterInitialization,
                color_attachments: initialized_attachments,
                clear_values: ShaderPackColorClearValues {
                    fog_color: ClearColor {
                        r: 0.1,
                        g: 0.2,
                        b: 0.3,
                        a: 1.0,
                    },
                },
                depth_texture,
                depth_view,
                target,
                pass,
            },
            &[],
        )
        .unwrap();
    assert!(operations[initializer_boundary..].iter().any(|op| matches!(op,
        CommandOp::BeginPass { colors, depth_stencil: Some(depth), .. }
            if colors.iter().all(|color| color.load_op == AttachmentLoadOp::Load)
                && depth.load_op == AttachmentLoadOp::Clear)),
        "terrain must preserve every initialized color slot, including non-primary sky/prepare outputs");
    gal.submit(SubmissionBatch {
        label: "sky-before-terrain".into(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "sky-before-terrain".into(), operations: operations.clone(),
        })],
    }).expect("terrain must synchronize the sky's depth read before clearing depth");
    for handle in [sampled_pipeline, sampled_shader, sampled_set, sampled_pipeline_layout, sampled_layout] {
        gal.destroy(handle).unwrap();
    }
    executor
        .append_terrain_source_color_pass(
            &mut operations,
            &TerrainSourceColorPassTargets {
                phase: TerrainSourceColorPassPhase::Translucent,
                color_attachments: color_attachments.clone(),
                clear_values: ShaderPackColorClearValues {
                    fog_color: ClearColor {
                        r: 0.1,
                        g: 0.2,
                        b: 0.3,
                        a: 1.0,
                    },
                },
                depth_texture,
                depth_view,
                target,
                pass,
            },
            &[],
        )
        .unwrap();
    let material_begin = operations.len();
    executor
        .append_textured_material_source_color_pass(
            &mut operations,
            &TerrainSourceColorPassTargets {
                phase: TerrainSourceColorPassPhase::TexturedMaterial,
                color_attachments: color_attachments.clone(),
                clear_values: ShaderPackColorClearValues {
                    fog_color: ClearColor {
                        r: 0.1,
                        g: 0.2,
                        b: 0.3,
                        a: 1.0,
                    },
                },
                depth_texture,
                depth_view,
                target,
                pass,
            },
            &[],
        )
        .unwrap();
    let material_operations = &operations[material_begin..];
    assert!(material_operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, depth_stencil, .. }
            if colors.iter().all(|color| color.load_op == AttachmentLoadOp::Load)
                && depth_stencil.as_ref().is_some_and(|depth| depth.load_op == AttachmentLoadOp::Load)
    )));
    assert!(material_operations.iter().all(|operation| !matches!(
        operation,
        CommandOp::BeginPass { colors, .. }
            if colors.iter().any(|color| color.load_op == AttachmentLoadOp::Clear)
    )));
    let cloud_begin = operations.len();
    executor
        .append_cloud_source_color_pass(
            &mut operations,
            &TerrainSourceColorPassTargets {
                phase: TerrainSourceColorPassPhase::Clouds,
                color_attachments: color_attachments.clone(),
                clear_values: ShaderPackColorClearValues {
                    fog_color: ClearColor {
                        r: 0.1,
                        g: 0.2,
                        b: 0.3,
                        a: 1.0,
                    },
                },
                depth_texture,
                depth_view,
                target,
                pass,
            },
            &[],
        )
        .unwrap();
    let cloud_operations = &operations[cloud_begin..];
    assert!(cloud_operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, depth_stencil, .. }
            if colors.iter().all(|color| color.load_op == AttachmentLoadOp::Load)
                && depth_stencil.as_ref().is_some_and(|depth| depth.load_op == AttachmentLoadOp::Load)
    )));
    assert!(cloud_operations.iter().all(|operation| !matches!(
        operation,
        CommandOp::BeginPass { colors, .. }
            if colors.iter().any(|color| color.load_op == AttachmentLoadOp::Clear)
    )));
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { target: pass_target, .. } if *pass_target == target
    )));
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::Barrier(barrier)
            if barrier.resource == color_attachments[0].texture
                && barrier.before == TextureUsageState::ShaderRead
                && barrier.after == TextureUsageState::ColorAttachment
    )));
    assert!(
        operations.iter().any(|operation| matches!(
            operation,
            CommandOp::BeginPass {
                colors,
                depth_stencil: Some(depth),
                ..
            } if colors.iter().all(|attachment| attachment.load_op == AttachmentLoadOp::Load)
                && depth.load_op == AttachmentLoadOp::Load
        )),
        "the translucent writer must preserve opaque/cutout color and depth"
    );
    assert!(
        operations.iter().any(|operation| matches!(
            operation,
            CommandOp::Barrier(barrier)
                if barrier.resource == depth_texture
                    && barrier.before == TextureUsageState::ShaderRead
                    && barrier.after == TextureUsageState::DepthStencilAttachment
        )),
        "the translucent writer must explicitly reacquire depth after the normal terrain pass"
    );
    let entity_begin = operations.len();
    let entity_targets = TerrainSourceColorPassTargets {
        phase: TerrainSourceColorPassPhase::Entities,
        color_attachments: color_attachments.clone(),
        clear_values: ShaderPackColorClearValues {
            fog_color: ClearColor {
                r: 0.1,
                g: 0.2,
                b: 0.3,
                a: 1.0,
            },
        },
        depth_texture,
        depth_view,
        target,
        pass,
    };
    executor
        .append_entity_source_color_pass(&mut operations, &entity_targets, &[])
        .unwrap();
    let entity_operations = &operations[entity_begin..];
    assert!(entity_operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, depth_stencil, .. }
            if colors.iter().all(|color| color.load_op == AttachmentLoadOp::Load)
                && depth_stencil.as_ref().is_some_and(|depth| depth.load_op == AttachmentLoadOp::Load)
    )));
    assert!(entity_operations.iter().all(|operation| !matches!(
        operation,
        CommandOp::BeginPass { colors, .. }
            if colors.iter().any(|color| color.load_op == AttachmentLoadOp::Clear)
    )));
    let hand_targets = TerrainSourceColorPassTargets {
        phase: TerrainSourceColorPassPhase::Hands,
        ..entity_targets.clone()
    };
    let hand_begin = operations.len();
    executor
        .append_hand_source_color_pass(&mut operations, &hand_targets, &[], None)
        .unwrap();
    let hand_operations = &operations[hand_begin..];
    assert!(hand_operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, depth_stencil, .. }
            if colors.iter().all(|color| color.load_op == AttachmentLoadOp::Load)
                && depth_stencil.as_ref().is_some_and(|depth| depth.load_op == AttachmentLoadOp::Clear)
    )), "the hand writer must preserve completed world color but clear its own depth domain");
    assert!(
        hand_operations.iter().any(|operation| matches!(
            operation,
            CommandOp::Barrier(barrier)
                if barrier.resource == depth_texture
                    && barrier.before == TextureUsageState::Undefined
                    && barrier.after == TextureUsageState::DepthStencilAttachment
        )),
        "the hand writer must explicitly transition its fresh depth domain"
    );
    let mut copied_hand_operations = Vec::new();
    let world_depth = test_handle(HandleKind::Texture, 991);
    executor
        .append_hand_source_color_pass(
            &mut copied_hand_operations,
            &hand_targets,
            &[],
            Some((world_depth, Extent3d { width: 16, height: 16, depth: 1 })),
        )
        .unwrap();
    assert!(copied_hand_operations.iter().any(|operation| matches!(
        operation,
        CommandOp::CopyTexture(copy)
            if copy.src_texture == world_depth && copy.dst_texture == depth_texture
    )));
    assert!(copied_hand_operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { depth_stencil: Some(depth), .. }
            if depth.load_op == AttachmentLoadOp::Load
    )), "ordinary hands must retain copied world depth for depthtex0");
    assert!(executor
        .append_hand_source_color_pass(&mut Vec::new(), &entity_targets, &[], None)
        .is_err());
    let wrong_phase = TerrainSourceColorPassTargets {
        phase: TerrainSourceColorPassPhase::TexturedMaterial,
        ..entity_targets
    };
    assert!(executor
        .append_entity_source_color_pass(&mut Vec::new(), &wrong_phase, &[])
        .is_err());
    gal.destroy(pass).unwrap();
    gal.destroy(target).unwrap();
    gal.destroy(depth_view).unwrap();
    gal.destroy(depth_texture).unwrap();
    transaction.discard(&mut executor, &mut gal);
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn first_translucent_source_writer_initializes_attachments() {
    assert_eq!(
        TextureUsageState::Undefined,
        TerrainSourceColorPassPhase::TranslucentFirst.depth_before()
    );
    assert_eq!(
        AttachmentLoadOp::Clear,
        TerrainSourceColorPassPhase::TranslucentFirst.depth_load_op()
    );
}

#[test]
fn textured_material_source_resolves_its_own_named_color_outputs() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let program = executor
        .prepared_lowered_textured_material_source_program()
        .unwrap()
        .expect("complete selected source must retain the textured material program");
    let mut gal = gal();
    let targets = executor
        .stage_source_color_targets(
            &mut gal,
            73,
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap()
        .expect("selected source must stage named color targets");
    let resolved = executor
        .resolve_textured_material_source_color_outputs(&program, &targets)
        .unwrap();

    assert_eq!(3, resolved.len());
    assert_eq!(TerrainPassOutput::LitTerrainColor, resolved[0].output);
    assert_eq!(0, resolved[0].source_slot);
    assert_eq!(TerrainPassOutput::MaterialAuxiliary, resolved[1].output);
    assert_eq!(6, resolved[1].source_slot);
    assert_eq!(TerrainPassOutput::TranslucencyAuxiliary, resolved[2].output);
    assert_eq!(3, resolved[2].source_slot);

    executor.destroy(&mut gal).unwrap();
}

#[test]
fn source_color_transaction_bootstraps_once_then_reuses_confirmed_targets() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let mut gal = gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let targets = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("selected source must stage named color targets");
    let clear_values = ShaderPackColorClearValues {
        fog_color: ClearColor {
            r: 0.1,
            g: 0.2,
            b: 0.3,
            a: 1.0,
        },
    };
    let mut first_ops = Vec::new();
    let mut first = executor
        .begin_source_color_transaction(&mut gal, &targets, clear_values, &mut first_ops)
        .unwrap();
    assert!(first_ops
        .iter()
        .any(|operation| matches!(operation, CommandOp::BeginPass { .. })));
    first
        .record_external_outputs(&[TerrainSourceResourceRole::ShaderPackColor(
            "primary".to_string(),
        )])
        .unwrap();
    first.finish(&mut first_ops).unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "source-color-bootstrap-once".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "source-color-bootstrap-once.commands".to_string(),
                operations: first_ops,
            })],
        })
        .unwrap();
    first.confirm(&mut executor, &mut gal).unwrap();
    gal.retire_through_for_test(token.submission).unwrap();

    let mut second_ops = Vec::new();
    let second = executor
        .begin_source_color_transaction(&mut gal, &targets, clear_values, &mut second_ops)
        .unwrap();
    let expected_clears = targets.targets().filter(|(_, target)| target.clear_each_frame)
        .map(|(_, target)| 1 + usize::from(target.previous_view.is_some())).sum::<usize>();
    assert_eq!(expected_clears, second_ops.iter().filter(|op| matches!(op, CommandOp::BeginPass { .. })).count(),
        "warm source frames clear both sides only of clear-enabled targets");
    second.discard(&mut executor, &mut gal);
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn discarded_source_color_transaction_retires_only_pending_target_generation() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let mut gal = gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let first = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("ordinary source discovery must stage private named colors");
    let first_primary = first.target("primary").unwrap();
    let mut operations = Vec::new();
    executor
        .begin_source_color_transaction(
            &mut gal,
            &first,
            ShaderPackColorClearValues {
                fog_color: ClearColor {
                    r: 0.1,
                    g: 0.2,
                    b: 0.3,
                    a: 1.0,
                },
            },
            &mut operations,
        )
        .unwrap()
        .discard(&mut executor, &mut gal);
    assert!(
        !operations.is_empty(),
        "discarded source preparation must have owned real bootstrap work before it is retired"
    );
    let restaged = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("discarding an unsubmitted transaction must allow a clean restage");
    assert_ne!(
        first_primary.current_texture,
        restaged.target("primary").unwrap().current_texture,
        "unsubmitted source targets must never become the active generation"
    );
    executor.discard_source_color_targets_submission(&mut gal);
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn ordinary_and_distant_source_share_one_semantic_color_target_cache() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let mut gal = gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };

    let ordinary = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("ordinary source discovery must stage its named color targets");
    assert_eq!(8, ordinary.targets().count());
    assert!(ordinary.target("primary").is_some());
    executor.confirm_source_color_targets_submission(&mut gal);

    let shared = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .expect("DH source must reuse the matching pack-wide color targets");
    assert_eq!(ordinary.identity, shared.identity);
    assert_eq!(
        ordinary.target("primary"),
        shared.target("primary"),
        "ordinary terrain and DH must never allocate divergent primary targets"
    );
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn dh_fullscreen_pass_preparation_rejects_missing_external_semantics_before_execution() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let mut gal = gal();
    let targets = executor
        .stage_source_color_targets(
            &mut gal,
            73,
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap()
        .expect("a complete DH candidate must stage private named source targets");

    let error = executor
        .stage_distant_horizons_depth_consumer_execution_plans(
            &mut gal,
            &targets,
            &[TerrainSourceOwnedResourceSet::new(
                TerrainSourceResourceAvailabilitySet::new(source.generation(), 73, []).unwrap(),
                [],
            )
            .unwrap()],
            Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        )
        .unwrap_err();
    assert!(
        error.to_string().contains("unavailable for semantic role"),
        "{error}"
    );
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn vanilla_source_completeness_excludes_discovered_distant_horizons_roles() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );

    let vanilla_roles = executor.source_required_resource_roles_for_frame(false);
    let distant_roles = executor.source_required_resource_roles_for_frame(true);
    assert!(
        vanilla_roles.is_subset(&distant_roles),
        "admitting DH may add requirements but cannot drop ordinary terrain roles"
    );
    let normal_plans = executor.source_resource_binding_plans_for_frame(false);
    let all_plans = executor.source_resource_binding_plans_for_frame(true);
    assert!(
        normal_plans.len() <= all_plans.len(),
        "a vanilla-only frame must never acquire an additional source plan from discovered DH state"
    );
}

#[test]
fn dh_source_and_fullscreen_consumers_participate_in_shared_resource_completeness() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let mut expected_roles = executor
        .prepared_lowered_distant_horizons_source_program()
        .unwrap()
        .expect("bundled source exposes one DH draw program")
        .opaque_resource_bindings
        .bindings()
        .iter()
        .map(|binding| binding.role())
        .collect::<Vec<_>>();
    expected_roles.extend(
        executor
            .prepared_lowered_distant_horizons_depth_consumers()
            .unwrap()
            .into_iter()
            .flat_map(|program| {
                program
                    .opaque_resource_bindings
                    .bindings()
                    .iter()
                    .map(|binding| binding.role())
            }),
    );
    expected_roles.sort();
    expected_roles.dedup();
    assert!(!expected_roles.is_empty());
    for role in expected_roles {
        assert!(
            executor.candidate_source_requires_resource(role.clone()),
            "shared source resource completeness omitted {}",
            role.semantic_name(),
        );
    }
}

#[test]
fn rejected_distant_horizons_source_target_preparation_retires_only_pending_images() {
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let mut gal = gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let resource_creates_before = gal.metrics().resource_creates;
    let resource_destroys_before = gal.metrics().resource_destroys;

    let staged = executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .unwrap();
    let staged_resource_count = staged
        .targets()
        .map(|(_, target)| {
            4 + u64::from(target.current_attachment_view != target.current_view)
                + u64::from(target.previous_view.is_some()) * 4
                + u64::from(target.previous_attachment_view.is_some())
        })
        .sum::<u64>();
    assert_eq!(
        resource_creates_before + staged_resource_count,
        gal.metrics().resource_creates
    );
    executor.discard_source_color_targets_submission(&mut gal);
    assert_eq!(
        resource_destroys_before + staged_resource_count,
        gal.metrics().resource_destroys
    );

    executor
        .stage_source_color_targets(&mut gal, 73, extent)
        .unwrap()
        .unwrap();
    assert_eq!(
        resource_creates_before + staged_resource_count * 2,
        gal.metrics().resource_creates
    );
    executor.destroy(&mut gal).unwrap();
}

#[test]
fn unresolved_source_scalar_uniforms_block_all_candidate_preparation() {
    let source = ShaderPackSource::new(
        "unresolved-scalar-source",
        32,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                "#version 130\nuniform float packSpecificValue;\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform() + vec4(packSpecificValue); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);

    assert!(matches!(
        executor.source_candidate(),
        TerrainSourceCandidateState::Discovered {
            source_uniform_requirement_summary: Some(summary),
            source_uniform_requirement_error: None,
            ..
        } if summary.field_count == 3
            && summary.resolved_field_count == 2
            && summary.unresolved_field_names == vec!["packSpecificValue".to_string()]
    ));
    for error in [
        executor
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
            .unwrap_err()
            .to_string(),
        executor
            .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
            .unwrap_err()
            .to_string(),
    ] {
        assert!(
            error.contains("unresolved scalar source uniforms"),
            "{error}"
        );
        assert!(error.contains("packSpecificValue"), "{error}");
    }
}

#[test]
fn explicit_empty_source_is_observed_as_disabled_not_rejected() {
    let source = ShaderPackSource::new("disabled", 17, Vec::new()).unwrap();
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(3).unwrap();

    runtime.observe_source_candidate(&source);

    assert!(matches!(
        runtime.source_candidate(),
        TerrainSourceCandidateState::Disabled { generation: 17, pack_name }
            if pack_name == "disabled"
    ));
    assert_eq!(
        "vulkanic:builtin/terrain_opaque_v1",
        runtime.plan().programs.terrain_opaque.identity.as_str()
    );
    assert_eq!(None, runtime.candidate_shader_binding(0).unwrap());
}

#[test]
fn discovered_source_candidate_without_pack_version_uses_owned_target_version() {
    let source = ShaderPackSource::new(
        "bounded-source-candidate",
        23,
        vec![
            ShaderSourceFile::new(
                "program/gbuffers_terrain.glsl",
                "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);

    let program = executor
        .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
        .unwrap()
        .expect("the owned lowering supplies GLSL 450 when the pack leaves version to Iris");
    assert!(program.program.vertex.source.starts_with("#version 450\n"));
    assert!(program
        .program
        .fragment
        .source
        .starts_with("#version 450\n"));
    assert!(matches!(
        executor.source_candidate(),
        TerrainSourceCandidateState::Discovered {
            source_summary: Some(summary),
            source_preprocess_error: None,
            source_lowering_summary: Some(_),
            source_lowering_error: None,
            ..
        } if summary.vertex_entry == "program/gbuffers_terrain.glsl"
            && summary.fragment_entry == "program/gbuffers_terrain.glsl"
    ));
}

#[test]
fn fragment_only_source_candidate_cannot_prepare_a_selected_program() {
    let source = ShaderPackSource::new(
        "fragment-only",
        24,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);

    assert!(matches!(
        executor.source_candidate(),
        TerrainSourceCandidateState::Discovered {
            source_summary: None,
            source_preprocess_error: Some(error),
            ..
        } if error.contains("missing shader source gbuffers_terrain.vsh")
    ));
    assert!(executor
        .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
        .unwrap_err()
        .to_string()
        .contains("no complete paired-stage source contract"));
}

#[test]
fn colored_source_candidate_prepares_owned_volume_identity_from_semantics() {
    let source = bundled_complementary_hung_loified_source(29).unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);

    let preparation = executor
        .candidate_colored_light_preparation(41, 53, [-1.25, 64.5, 7.75])
        .unwrap()
        .expect("Complementary source should prepare ColoredVoxelLighting semantics");

    assert_eq!(29, preparation.descriptor.shader_pack_generation);
    assert_eq!(41, preparation.descriptor.world_generation);
    assert_eq!(53, preparation.descriptor.resource_generation);
    assert_eq!([-2, 64, 7], preparation.descriptor.mapping.camera_cell);
    assert_eq!(
        [0.75, 0.5, 0.75],
        preparation.descriptor.mapping.camera_fraction
    );
    assert_eq!(29, preparation.materials.shader_pack_generation());
    assert_eq!(29, preparation.emission.shader_pack_generation());
    assert!(executor
        .candidate_colored_light_preparation(0, 53, [0.0; 3])
        .is_err());
}

#[test]
fn prepared_colored_light_generation_installs_once_and_reuses_matching_descriptor() {
    let source = bundled_complementary_hung_loified_source(31).unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate(&source);
    let preparation = executor
        .candidate_colored_light_preparation(43, 59, [0.25, 64.5, 0.75])
        .unwrap()
        .unwrap();
    let mut gal = gal();

    assert!(executor
        .ensure_candidate_colored_light_runtime(&mut gal, preparation.clone())
        .unwrap());
    let compatible_descriptor = executor
        .candidate_colored_light_descriptor(43, 59, [0.75, 64.5, 0.25])
        .unwrap()
        .unwrap();
    assert!(executor.candidate_colored_light_runtime_compatible(&compatible_descriptor));
    assert!(!executor
        .ensure_candidate_colored_light_runtime(&mut gal, preparation)
        .unwrap());
    let fractional = executor
        .candidate_colored_light_preparation(43, 59, [0.75, 64.5, 0.25])
        .unwrap()
        .unwrap();
    assert!(!executor
        .ensure_candidate_colored_light_runtime(&mut gal, fractional)
        .unwrap());
    assert!(executor.has_private_terrain_occupancy());
    assert_eq!(
        31,
        executor
            .private_terrain_occupancy_descriptor()
            .unwrap()
            .shader_pack_generation
    );
}

#[test]
fn incomplete_owned_source_is_rejected_without_changing_fixture_execution() {
    let source = ShaderPackSource::new(
        "incomplete",
        14,
        vec![ShaderSourceFile::new(
            "program/gbuffers_terrain.glsl",
            "void main() {}",
        )],
    )
    .unwrap();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    let fixture_program = executor.plan().programs.terrain_opaque.identity.clone();

    executor.observe_source_candidate(&source);

    assert!(matches!(
        executor.source_candidate(),
        TerrainSourceCandidateState::Rejected {
            generation: 14,
            pack_name,
            ..
        } if pack_name == "incomplete"
    ));
    assert_eq!(
        fixture_program,
        executor.plan().programs.terrain_opaque.identity
    );
}

#[test]
fn composite_uniform_block_layout_is_stable() {
    let uniforms = TerrainCompositeUniforms {
        light_view_projection: [1.0; 16],
        shadow_params: [2.0; 4],
        color_grade_params: [3.0; 4],
        projection_inverse: [4.0; 16],
        fog_color_and_environmental_start: [5.0; 4],
        fog_ranges: [6.0; 4],
    };
    assert_eq!(
        TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES as usize,
        uniforms.pack().len()
    );
}

#[test]
fn indexed_draw_emission_skips_redundant_state_binds_inside_pass() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let other_pipeline = test_handle(HandleKind::GraphicsPipeline, 2);
    let layout = test_handle(HandleKind::PipelineLayout, 3);
    let set = test_handle(HandleKind::ResourceSet, 4);
    let other_set = test_handle(HandleKind::ResourceSet, 5);
    let index_buffer = test_handle(HandleKind::Buffer, 6);
    let mut ops = Vec::new();
    let mut state = IndexedDrawState::default();

    append_indexed_draw(
        &mut ops,
        &mut state,
        pipeline,
        layout,
        set,
        &[0],
        None,
        index_buffer,
        0,
        IndexType::U32,
        6,
        1,
        None,
    );
    append_indexed_draw(
        &mut ops,
        &mut state,
        pipeline,
        layout,
        set,
        &[0],
        None,
        index_buffer,
        0,
        IndexType::U32,
        6,
        2,
        None,
    );
    append_indexed_draw(
        &mut ops,
        &mut state,
        pipeline,
        layout,
        other_set,
        &[0],
        None,
        index_buffer,
        0,
        IndexType::U32,
        6,
        3,
        None,
    );
    append_indexed_draw(
        &mut ops,
        &mut state,
        pipeline,
        layout,
        other_set,
        &[0],
        None,
        index_buffer,
        12,
        IndexType::U32,
        6,
        4,
        None,
    );
    append_indexed_draw(
        &mut ops,
        &mut state,
        other_pipeline,
        layout,
        other_set,
        &[0],
        None,
        index_buffer,
        12,
        IndexType::U32,
        6,
        5,
        None,
    );

    let pipeline_binds = ops
        .iter()
        .filter(|op| matches!(op, CommandOp::BindGraphicsPipeline(_)))
        .count();
    let resource_set_binds = ops
        .iter()
        .filter(|op| matches!(op, CommandOp::BindResourceSet { .. }))
        .count();
    let index_binds = ops
        .iter()
        .filter(|op| matches!(op, CommandOp::SetIndexBuffer { .. }))
        .count();
    let draws = ops
        .iter()
        .filter(|op| matches!(op, CommandOp::DrawIndexed { .. }))
        .count();

    assert_eq!(2, pipeline_binds);
    assert_eq!(3, resource_set_binds);
    assert_eq!(2, index_binds);
    assert_eq!(5, draws);
}

#[test]
fn indexed_indirect_emission_coalesces_contiguous_compatible_records() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let layout = test_handle(HandleKind::PipelineLayout, 2);
    let set = test_handle(HandleKind::ResourceSet, 3);
    let index = test_handle(HandleKind::Buffer, 4);
    let indirect = test_handle(HandleKind::Buffer, 5);
    let mut ops = Vec::new();
    let mut state = IndexedDrawState::default();
    for offset in [0, 20, 40] {
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[0, 0],
            None,
            index,
            0,
            IndexType::U32,
            6,
            1,
            Some(TerrainIndexedIndirect {
                buffer: indirect,
                offset,
                draw_count: 1,
            }),
        );
    }
    assert!(matches!(
        ops.last(),
        Some(CommandOp::DrawIndexedIndirect { buffer, offset: 0, draw_count: 3 })
            if *buffer == indirect
    ));
    assert_eq!(
        1,
        ops.iter()
            .filter(|op| matches!(op, CommandOp::DrawIndexedIndirect { .. }))
            .count()
    );
}

#[test]
fn indexed_indirect_emission_splits_runs_at_backend_limit() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let layout = test_handle(HandleKind::PipelineLayout, 2);
    let set = test_handle(HandleKind::ResourceSet, 3);
    let index = test_handle(HandleKind::Buffer, 4);
    let indirect = test_handle(HandleKind::Buffer, 5);
    let mut ops = Vec::new();
    let mut state = IndexedDrawState::with_indirect_limit(2);
    for offset in [0, 20, 40, 60, 80] {
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[0, 0],
            None,
            index,
            0,
            IndexType::U32,
            6,
            1,
            Some(TerrainIndexedIndirect {
                buffer: indirect,
                offset,
                draw_count: 1,
            }),
        );
    }
    assert_eq!(
        vec![(0, 2), (40, 2), (80, 1)],
        ops.iter()
            .filter_map(|op| match op {
                CommandOp::DrawIndexedIndirect {
                    buffer,
                    offset,
                    draw_count,
                } if *buffer == indirect => Some((*offset, *draw_count)),
                _ => None,
            })
            .collect::<Vec<_>>()
    );
}

#[test]
fn indexed_draw_emission_distinguishes_static_and_dynamic_set_bindings() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let layout = test_handle(HandleKind::PipelineLayout, 2);
    let set = test_handle(HandleKind::ResourceSet, 3);
    let index_buffer = test_handle(HandleKind::Buffer, 4);
    let mut ops = Vec::new();
    let mut state = IndexedDrawState::default();

    append_indexed_draw(
        &mut ops,
        &mut state,
        pipeline,
        layout,
        set,
        &[],
        None,
        index_buffer,
        0,
        IndexType::U32,
        6,
        1,
        None,
    );
    append_indexed_draw(
        &mut ops,
        &mut state,
        pipeline,
        layout,
        set,
        &[32],
        None,
        index_buffer,
        0,
        IndexType::U32,
        6,
        1,
        None,
    );
    append_indexed_draw(
        &mut ops,
        &mut state,
        pipeline,
        layout,
        set,
        &[32],
        None,
        index_buffer,
        0,
        IndexType::U32,
        6,
        1,
        None,
    );

    let dynamic_offsets = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::BindResourceSet {
                set_index: 0,
                dynamic_offsets,
                ..
            } => Some(dynamic_offsets.as_slice()),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(vec![&[][..], &[32][..]], dynamic_offsets);
}

#[test]
fn indexed_draw_emission_binds_optional_shader_resources_by_semantic_set() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let layout = test_handle(HandleKind::PipelineLayout, 2);
    let mesh_set = test_handle(HandleKind::ResourceSet, 3);
    let shader_set = test_handle(HandleKind::ResourceSet, 4);
    let replacement_shader_set = test_handle(HandleKind::ResourceSet, 5);
    let index_buffer = test_handle(HandleKind::Buffer, 6);
    let shader_binding = TerrainShaderResourceSet {
        set_index: 1,
        set: shader_set,
    };
    let mut ops = Vec::new();
    let mut state = IndexedDrawState::default();
    for binding in [
        Some(shader_binding),
        Some(shader_binding),
        None,
        Some(TerrainShaderResourceSet {
            set_index: 1,
            set: replacement_shader_set,
        }),
    ] {
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            mesh_set,
            &[0],
            binding,
            index_buffer,
            0,
            IndexType::U32,
            6,
            1,
            None,
        );
    }
    let shader_binds = ops
        .iter()
        .filter(|op| matches!(op, CommandOp::BindResourceSet { set_index: 1, .. }))
        .count();
    assert_eq!(2, shader_binds);
    assert!(ops.iter().any(|op| {
        matches!(op, CommandOp::BindResourceSet { set_index: 1, set, .. } if *set == replacement_shader_set)
    }));
}

fn test_handle(kind: HandleKind, index: u32) -> Handle {
    Handle::new(kind, index, 1).unwrap()
}

#[test]
fn textured_material_direct_draw_expands_compact_quads_without_an_index_buffer() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 100);
    let layout = test_handle(HandleKind::PipelineLayout, 101);
    let source_set = test_handle(HandleKind::ResourceSet, 102);
    let pack_set = test_handle(HandleKind::ResourceSet, 103);
    let mut operations = Vec::new();
    let mut state = DirectDrawState::default();
    append_direct_draw(
        &mut operations,
        &mut state,
        pipeline,
        layout,
        source_set,
        &[64, 128, 256],
        Some(TerrainShaderResourceSet {
            set_index: 1,
            set: pack_set,
        }),
        12,
    );
    assert!(operations.iter().any(|operation| {
        matches!(
            operation,
            CommandOp::Draw {
                vertices: 12,
                instances: 1
            }
        )
    }));
    assert!(!operations.iter().any(|operation| matches!(
        operation,
        CommandOp::SetIndexBuffer { .. } | CommandOp::DrawIndexed { .. }
    )));
    assert!(operations.iter().any(|operation| {
        matches!(operation, CommandOp::BindResourceSet { set_index: 1, set, .. } if *set == pack_set)
    }));
}

#[test]
fn main_depth_history_first_frame_copies_only_current_depth() {
    let targets = TerrainDepthHistoryTargets {
        main_depth_texture: test_handle(HandleKind::Texture, 1),
        before_translucency_texture: test_handle(HandleKind::Texture, 2),
        previous_texture: test_handle(HandleKind::Texture, 3),
    };
    let mut ops = Vec::new();
    ShaderPackRuntimeExecutor::append_main_depth_history(
        &mut ops,
        targets,
        TerrainDepthHistoryPlan {
            prior_before_translucency_valid: false,
            prior_previous_valid: false,
            extent: Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        },
    )
    .unwrap();

    let copies = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::CopyTexture(copy) => Some(copy),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(1, copies.len());
    assert_eq!(targets.main_depth_texture, copies[0].src_texture);
    assert_eq!(targets.before_translucency_texture, copies[0].dst_texture);
    assert!(ops.iter().all(|op| !matches!(
        op,
        CommandOp::CopyTexture(copy) if copy.dst_texture == targets.previous_texture
    )));
}

#[test]
fn main_depth_history_rotates_confirmed_snapshot_before_replacement() {
    let targets = TerrainDepthHistoryTargets {
        main_depth_texture: test_handle(HandleKind::Texture, 1),
        before_translucency_texture: test_handle(HandleKind::Texture, 2),
        previous_texture: test_handle(HandleKind::Texture, 3),
    };
    let mut ops = Vec::new();
    ShaderPackRuntimeExecutor::append_main_depth_history(
        &mut ops,
        targets,
        TerrainDepthHistoryPlan {
            prior_before_translucency_valid: true,
            prior_previous_valid: true,
            extent: Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        },
    )
    .unwrap();

    let copies = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::CopyTexture(copy) => Some((copy.src_texture, copy.dst_texture)),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![
            (
                targets.before_translucency_texture,
                targets.previous_texture,
            ),
            (
                targets.main_depth_texture,
                targets.before_translucency_texture,
            ),
        ],
        copies
    );
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::Barrier(barrier)
            if barrier.resource == targets.previous_texture
                && barrier.before == TextureUsageState::ShaderRead
                && barrier.after == TextureUsageState::TransferDst
    )));
}

#[test]
fn main_depth_history_rejects_non_2d_extent() {
    let targets = TerrainDepthHistoryTargets {
        main_depth_texture: test_handle(HandleKind::Texture, 1),
        before_translucency_texture: test_handle(HandleKind::Texture, 2),
        previous_texture: test_handle(HandleKind::Texture, 3),
    };
    let error = ShaderPackRuntimeExecutor::append_main_depth_history(
        &mut Vec::new(),
        targets,
        TerrainDepthHistoryPlan {
            prior_before_translucency_valid: false,
            prior_previous_valid: false,
            extent: Extent3d {
                width: 1,
                height: 1,
                depth: 2,
            },
        },
    )
    .unwrap_err();
    assert!(error.to_string().contains("non-zero 2D extent"));
}

#[test]
fn indexed_indirect_runs_merge_and_split_like_single_commands() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let layout = test_handle(HandleKind::PipelineLayout, 2);
    let set = test_handle(HandleKind::ResourceSet, 3);
    let index = test_handle(HandleKind::Buffer, 4);
    let indirect = test_handle(HandleKind::Buffer, 5);
    let mut ops = Vec::new();
    let mut state = IndexedDrawState::with_indirect_limit(4);
    // Runs of 3 and 4 contiguous commands, then a gap.
    for (offset, draw_count) in [(0, 3), (60, 4), (200, 1)] {
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[0, 0],
            None,
            index,
            0,
            IndexType::U32,
            6,
            1,
            Some(TerrainIndexedIndirect { buffer: indirect, offset, draw_count }),
        );
    }
    assert_eq!(
        vec![(0, 4), (80, 3), (200, 1)],
        ops.iter()
            .filter_map(|op| match op {
                CommandOp::DrawIndexedIndirect { buffer, offset, draw_count } if *buffer == indirect => {
                    Some((*offset, *draw_count))
                }
                _ => None,
            })
            .collect::<Vec<_>>()
    );
}
