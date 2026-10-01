use crate::render::shaderpack::resources::color_targets::*;
    use crate::render::vulkanic::commands::{CommandList, CommandListDesc, SubmissionBatch};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::shaderpack::lowering::lower_fullscreen_source_pair;
use crate::render::shaderpack::source::preprocess::preprocess_source_stage_pair;
use crate::render::shaderpack::programs::prepare_lowered_fullscreen_source_program;
use crate::render::shaderpack::source::ShaderSourceFile;
use crate::render::shaderpack::contracts::terrain::{
    TerrainSourceStage, TerrainSourceStages,
};
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceResourceBindings, TERRAIN_RESOURCE_BINDINGS_PATH,
};

fn bindings() -> &'static str {
    concat!(
        "colortex0=shader_pack_color:primary\n",
        "colortex1=shader_pack_color:previous_depth\n",
        "colortex2=shader_pack_color:temporal_aa\n",
        "colortex3=shader_pack_color:translucent_final\n",
        "colortex4=shader_pack_color:volumetric_factor\n",
        "colortex5=shader_pack_color:normal_scene\n",
        "colortex6=shader_pack_color:material_auxiliary\n",
        "colortex7=shader_pack_color:temporal_reflection\n",
    )
}

fn settings() -> &'static str {
    concat!(
        "/* exact source declarations */\n",
        "const int colortex0Format = R11F_G11F_B10F;\n",
        "const int colortex1Format = R32F;\n",
        "const int colortex2Format = RGB16F;\n",
        "const int colortex3Format = RGBA8;\n",
        "const int colortex4Format = R8;\n",
        "const int colortex5Format = RGBA8_SNORM;\n",
        "const int colortex6Format = RGBA8;\n",
        "const int colortex7Format = RGBA16F;\n",
        "const bool colortex0Clear = true;\n",
        "const bool colortex1Clear = false;\n",
        "const bool colortex2Clear = false;\n",
        "const bool colortex3Clear = true;\n",
        "const bool colortex4Clear = false;\n",
        "const bool colortex5Clear = false;\n",
        "const bool colortex6Clear = true;\n",
        "const bool colortex7Clear = false;\n",
    )
}

fn source(settings: &str) -> ShaderPackSource {
    ShaderPackSource::new(
        "source-targets",
        7,
        vec![
            ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, settings),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, bindings()),
        ],
    )
    .unwrap()
}

fn gal() -> VulkanicGal {
    crate::render::vulkanic::test_support::mock_gal()
}

fn fullscreen_program(source: &ShaderPackSource) -> LoweredFullscreenSourceProgram {
    let stages = TerrainSourceStages {
        vertex: TerrainSourceStage {
            path: "world0/deferred1.vsh".to_string(),
            defines: Default::default(),
        },
        fragment: TerrainSourceStage {
            path: "world0/deferred1.fsh".to_string(),
            defines: Default::default(),
        },
    };
    let artifacts = preprocess_source_stage_pair(source, &stages).unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(source).unwrap();
    let lowered =
        lower_fullscreen_source_pair(&artifacts.vertex, &artifacts.fragment, &bindings)
            .unwrap();
    let opaque_bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&bindings)
        .unwrap();
    prepare_lowered_fullscreen_source_program(
        source.name(),
        source.generation(),
        "world0/deferred1.fsh",
        &lowered,
        &opaque_bindings,
    )
    .unwrap()
}

#[test]
fn manifest_keeps_named_target_semantics_and_exact_source_formats() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let primary = manifest.target("primary").unwrap();
    assert_eq!(0, primary.source_slot());
    assert_eq!(ShaderPackColorFormat::R11fG11fB10f, primary.format);
    assert!(primary.clear_each_frame);
    assert_eq!(
        TextureFormat::R11fG11fB10f,
        primary.gal_schema_color_format()
    );
    assert_eq!(
        TextureFormat::Rgba16Float,
        manifest
            .target("temporal_reflection")
            .unwrap()
            .gal_schema_color_format()
    );
    assert_eq!(8, manifest.targets().count());
    assert_eq!(
        "material_auxiliary",
        manifest.target_for_source_slot(6).unwrap().name()
    );
    assert!(manifest.require_gal_schema_formats().is_ok());
}

#[test]
fn manifest_uses_portable_protocol_defaults_for_missing_properties() {
    let incomplete = settings().replace("const bool colortex6Clear = true;\n", "");
    let incomplete_source = source(&incomplete);
    let bindings = TerrainSourceResourceBindings::from_source(&incomplete_source).unwrap();
    let manifest =
        ShaderPackColorTargetManifest::from_source(&incomplete_source, &bindings).unwrap();
    assert!(
        manifest
            .target("material_auxiliary")
            .unwrap()
            .clear_each_frame
    );

    let no_format = settings().replace("const int colortex6Format = RGBA8;\n", "");
    let no_format_source = source(&no_format);
    let bindings = TerrainSourceResourceBindings::from_source(&no_format_source).unwrap();
    assert_eq!(
        ShaderPackColorFormat::Rgba8,
        ShaderPackColorTargetManifest::from_source(&no_format_source, &bindings)
            .unwrap()
            .target("material_auxiliary")
            .unwrap()
            .format
    );
}

#[test]
fn manifest_preserves_literal_source_clear_colors_without_backend_identity() {
    let with_clear_color = format!(
        "{}const vec4 colortex4ClearColor = vec4(0.125, 0.25f, 0.5, 1.0);\n",
        settings()
    );
    let source = source(&with_clear_color);
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    assert_eq!(
        Some([
            0.125f32.to_bits(),
            0.25f32.to_bits(),
            0.5f32.to_bits(),
            1.0f32.to_bits(),
        ]),
        manifest
            .target("volumetric_factor")
            .unwrap()
            .clear_color_bits
    );

    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        std::iter::empty::<String>(),
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let mut frame = cache.begin_frame(&targets).unwrap();
    let bootstrap = frame
        .stage_full_clear(
            &mut gal,
            &targets,
            ShaderPackColorBootstrapClearValues {
                fog_color: ClearColor {
                    r: 0.7,
                    g: 0.6,
                    b: 0.5,
                    a: 1.0,
                },
            },
        )
        .unwrap();
    let mut operations = Vec::new();
    frame
        .append_full_clear(&bootstrap, &mut operations)
        .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, .. }
            if colors.first().and_then(|attachment| attachment.clear_color)
                == Some(ClearColor { r: 0.125, g: 0.25, b: 0.5, a: 1.0 })
    )));
    bootstrap.destroy(&mut gal);
    cache.destroy(&mut gal);
}

#[test]
fn manifest_rejects_nonliteral_or_duplicate_source_clear_colors() {
    let nonliteral = format!(
        "{}const vec4 colortex4ClearColor = vec4(fogColor.rgb, 1.0);\n",
        settings()
    );
    let nonliteral_source = source(&nonliteral);
    let bindings = TerrainSourceResourceBindings::from_source(&nonliteral_source).unwrap();
    assert!(
        ShaderPackColorTargetManifest::from_source(&nonliteral_source, &bindings)
            .unwrap_err()
            .to_string()
            .contains("shader-pack color clear value")
    );

    let duplicate = format!(
        "{}const vec4 colortex4ClearColor = vec4(0.0);\nconst vec4 colortex4ClearColor = vec4(1.0);\n",
        settings()
    );
    let duplicate_source = source(&duplicate);
    let bindings = TerrainSourceResourceBindings::from_source(&duplicate_source).unwrap();
    assert!(
        ShaderPackColorTargetManifest::from_source(&duplicate_source, &bindings)
            .unwrap_err()
            .to_string()
            .contains("clear color more than once")
    );
}

#[test]
fn manifest_rejects_duplicated_or_unknown_format_declarations() {
    let duplicate = format!("{}const int colortex0Format = RGBA8;\n", settings());
    let duplicate_source = source(&duplicate);
    let bindings = TerrainSourceResourceBindings::from_source(&duplicate_source).unwrap();
    assert!(
        ShaderPackColorTargetManifest::from_source(&duplicate_source, &bindings)
            .unwrap_err()
            .to_string()
            .contains("format more than once")
    );

    let unknown = settings().replace("RGBA16F", "RGB10_A2");
    let unknown_source = source(&unknown);
    let bindings = TerrainSourceResourceBindings::from_source(&unknown_source).unwrap();
    assert!(
        ShaderPackColorTargetManifest::from_source(&unknown_source, &bindings)
            .unwrap_err()
            .to_string()
            .contains("unsupported shader-pack color target format")
    );
}

#[test]
fn private_targets_use_exact_formats_and_feedback_pairs_without_route_selection() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();

    let targets = cache.stage(&mut gal, identity.clone(), &manifest).unwrap();
    assert_eq!(18, gal.metrics().resource_creates);
    let primary = targets.target("primary").unwrap();
    assert_eq!(TextureFormat::R11fG11fB10f, primary.format);
    assert!(primary.previous_texture.is_some());
    assert!(primary.previous_view.is_some());
    let material_auxiliary = targets.target("material_auxiliary").unwrap();
    assert_eq!(TextureFormat::Rgba8Unorm, material_auxiliary.format);
    assert!(material_auxiliary.previous_texture.is_none());
    assert!(material_auxiliary.previous_view.is_none());
    assert_eq!(8, targets.targets().count());

    cache.confirm_submission(&mut gal);
    let reused = cache.stage(&mut gal, identity, &manifest).unwrap();
    assert_eq!(primary, reused.target("primary").unwrap());
    assert_eq!(18, gal.metrics().resource_creates);

    cache.destroy(&mut gal);
    assert_eq!(18, gal.metrics().resource_destroys);
}

#[test]
fn color_frame_history_advances_only_after_confirmation_and_copies_feedback() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let primary = targets.target("primary").unwrap();
    let attachment = FullscreenSourceColorAttachment {
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        texture: primary.current_texture,
        view: primary.current_view,
        format: primary.format,
        clear_each_frame: primary.clear_each_frame,
        clear_color_bits: primary.clear_color_bits,
    };

    let mut first = cache.begin_frame(&targets).unwrap();
    assert_eq!(
        vec![TextureUsageState::Undefined],
        first
            .attachment_states(std::slice::from_ref(&attachment))
            .unwrap()
    );
    assert!(first.require_sample(&attachment.role, true).is_err());
    assert_eq!(
        vec![true],
        first
            .attachment_clear_mask(std::slice::from_ref(&attachment))
            .unwrap()
    );
    first
        .record_pass(
            std::slice::from_ref(&attachment),
            std::slice::from_ref(&attachment),
            &[true],
        )
        .unwrap();
    assert_eq!(
        vec![false],
        first
            .attachment_clear_mask(std::slice::from_ref(&attachment))
            .unwrap(),
        "a later source pass must load sky or terrain written earlier this frame"
    );
    let mut operations = Vec::new();
    first
        .append_feedback_copies(&targets, &mut operations)
        .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::CopyTexture(copy)
            if copy.src_texture == primary.current_texture
                && copy.dst_texture == primary.previous_texture.unwrap()
    )));

    // This is the only state transition that a successful combined
    // submission may make visible to the next source frame.
    cache
        .confirm_frame_submission(&mut gal, first)
        .expect("a matching staged generation must accept confirmed history");
    let second = cache.begin_frame(&targets).unwrap();
    assert_eq!(
        vec![TextureUsageState::ShaderRead],
        second
            .attachment_states(std::slice::from_ref(&attachment))
            .unwrap()
    );
    second
        .require_sample(&attachment.role, true)
        .expect("only the confirmed feedback copy may seed the next frame");

    // An abandoned frame never modifies the confirmed state.
    let abandoned = cache.begin_frame(&targets).unwrap();
    drop(abandoned);
    let third = cache.begin_frame(&targets).unwrap();
    third
        .require_sample(&attachment.role, true)
        .expect("discarding a plan must leave the prior confirmed history intact");
    cache.destroy(&mut gal);
}

#[test]
fn same_frame_feedback_snapshots_an_earlier_writer_before_self_feedback() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let primary = targets.target("primary").unwrap();
    let role = TerrainSourceResourceRole::ShaderPackColor("primary".to_string());
    let mut frame = cache.begin_frame(&targets).unwrap();
    frame
        .record_external_outputs(std::slice::from_ref(&role))
        .unwrap();

    let mut operations = Vec::new();
    assert_eq!(
        vec![role.clone()],
        frame
            .append_same_frame_feedback_snapshots(
                &targets,
                std::slice::from_ref(&role),
                &mut operations,
            )
            .unwrap()
    );
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::CopyTexture(copy)
            if copy.src_texture == primary.current_texture
                && copy.dst_texture == primary.previous_texture.unwrap()
    )));
    frame
        .require_sample(&role, true)
        .expect("the copied same-frame image must be available to the self-feedback sampler");

    let mut no_writer = cache.begin_frame(&targets).unwrap();
    let no_writer_ops = no_writer
        .append_same_frame_feedback_snapshots(
            &targets,
            std::slice::from_ref(&role),
            &mut operations,
        )
        .unwrap();
    assert!(
        no_writer_ops.is_empty(),
        "an unwritten role must retain prior-frame history rather than overwrite it"
    );
    cache.destroy(&mut gal);
}

#[test]
fn full_clear_bootstraps_current_and_feedback_images_before_source_execution() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let mut frame = cache.begin_frame(&targets).unwrap();
    let bootstrap = frame
        .stage_full_clear(
            &mut gal,
            &targets,
            ShaderPackColorBootstrapClearValues {
                fog_color: ClearColor {
                    r: 0.2,
                    g: 0.3,
                    b: 0.4,
                    a: 0.0,
                },
            },
        )
        .unwrap();
    let mut operations = Vec::new();
    frame
        .append_full_clear(&bootstrap, &mut operations)
        .unwrap();
    let clears = operations
        .iter()
        .filter_map(|operation| match operation {
            CommandOp::BeginPass { colors, .. } => colors.first(),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        9,
        clears.len(),
        "eight current targets plus primary feedback"
    );
    assert!(clears.iter().all(|attachment| {
        attachment.load_op == AttachmentLoadOp::Clear
            && attachment.store_op == AttachmentStoreOp::Store
    }));
    assert!(clears.iter().any(|attachment| {
        attachment.clear_color
            == Some(ClearColor {
                r: 0.2,
                g: 0.3,
                b: 0.4,
                a: 1.0,
            })
    }));
    assert!(clears.iter().any(|attachment| {
        attachment.clear_color
            == Some(ClearColor {
                r: 1.0,
                g: 1.0,
                b: 1.0,
                a: 1.0,
            })
    }));
    gal.submit(SubmissionBatch {
        label: "shader-pack-color-bootstrap".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "shader-pack-color-bootstrap.commands".to_string(),
            operations,
        })],
    })
    .expect("the source bootstrap clear must be a valid backend-neutral submission");
    bootstrap.destroy(&mut gal);
    cache
        .confirm_frame_submission(&mut gal, frame)
        .expect("only the submitted full clear may seed source color history");
    let next = cache.begin_frame(&targets).unwrap();
    next.require_sample(
        &TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        true,
    )
    .expect("the feedback side must be initialized by the submitted full clear");
    cache.destroy(&mut gal);
}

#[test]
fn source_color_bootstrap_is_required_only_for_a_fresh_generation() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        std::iter::empty::<String>(),
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();

    let mut first = cache.begin_frame(&targets).unwrap();
    assert!(first.requires_initial_clear().unwrap());
    let bootstrap = first
        .stage_full_clear(
            &mut gal,
            &targets,
            ShaderPackColorBootstrapClearValues {
                fog_color: ClearColor {
                    r: 0.1,
                    g: 0.2,
                    b: 0.3,
                    a: 1.0,
                },
            },
        )
        .unwrap();
    let mut operations = Vec::new();
    first
        .append_full_clear(&bootstrap, &mut operations)
        .unwrap();
    bootstrap.destroy(&mut gal);
    cache.confirm_frame_submission(&mut gal, first).unwrap();

    let second = cache.begin_frame(&targets).unwrap();
    assert!(!second.requires_initial_clear().unwrap());
    assert!(second
        .stage_full_clear(
            &mut gal,
            &targets,
            ShaderPackColorBootstrapClearValues {
                fog_color: ClearColor {
                    r: 0.1,
                    g: 0.2,
                    b: 0.3,
                    a: 1.0,
                },
            },
        )
        .unwrap_err()
        .to_string()
        .contains("only valid for an uninitialized target generation"));
    cache.destroy(&mut gal);
}

#[test]
fn mipmapped_source_sampling_requires_and_records_explicit_generation() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        std::iter::empty::<String>(),
        ["primary".to_string()],
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let mut frame = cache.begin_frame(&targets).unwrap();
    let bootstrap = frame
        .stage_full_clear(
            &mut gal,
            &targets,
            ShaderPackColorBootstrapClearValues {
                fog_color: ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                },
            },
        )
        .unwrap();
    let primary = TerrainSourceResourceRole::ShaderPackColor("primary".to_string());
    let mut operations = Vec::new();
    frame
        .append_full_clear(&bootstrap, &mut operations)
        .unwrap();
    assert!(frame
        .require_sample_with_mips(&primary, false, true)
        .is_err());
    frame
        .append_mipmaps(&targets, std::slice::from_ref(&primary), &mut operations)
        .unwrap();
    frame
        .require_sample_with_mips(&primary, false, true)
        .expect("mipmapped sampling must become valid only after GAL mip generation");
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::GenerateMipmaps { texture, subresources }
            if *texture == targets.target("primary").unwrap().current_texture
                && subresources.mip_count > 1
    )));
    gal.submit(SubmissionBatch {
        label: "shader-pack-color-mips".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "shader-pack-color-mips.commands".to_string(),
            operations,
        })],
    })
    .expect("the explicit source mip transaction must validate");
    bootstrap.destroy(&mut gal);
    cache.confirm_frame_submission(&mut gal, frame).unwrap();
    cache.destroy(&mut gal);
}

#[test]
fn private_targets_allocate_source_requested_mip_chains_without_claiming_execution() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        ["primary".to_string(), "material_auxiliary".to_string()],
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();

    let targets = cache.stage(&mut gal, identity.clone(), &manifest).unwrap();
    assert_eq!(9, targets.target("primary").unwrap().mip_levels);
    assert_eq!(9, targets.target("material_auxiliary").unwrap().mip_levels);
    assert_eq!(1, targets.target("previous_depth").unwrap().mip_levels);

    cache.confirm_submission(&mut gal);
    let without_mips = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        identity.extent,
        ["primary".to_string()],
        std::iter::empty::<String>(),
    )
    .unwrap();
    let replacement = cache.stage(&mut gal, without_mips, &manifest).unwrap();
    assert_eq!(1, replacement.target("primary").unwrap().mip_levels);
    cache.discard_submission(&mut gal);
    cache.destroy(&mut gal);
}

#[test]
fn fullscreen_color_resources_bind_feedback_and_mips_from_source_semantics() {
    let source = ShaderPackSource::new(
        "fullscreen-source-targets",
        7,
        vec![
            ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, settings()),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, bindings()),
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nuniform sampler2D colortex0;\n/* const bool colortex0MipmapEnabled = true; */\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
        ],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        ["primary".to_string()],
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let program = fullscreen_program(&source);
    let resources = prepare_fullscreen_source_color_resources(&mut gal, &program, &targets)
        .expect("source-declared feedback/mips must prepare owned color resources");
    let primary = targets.target("primary").unwrap();
    assert_eq!(9, primary.mip_levels);
    assert_eq!(
        primary.previous_view,
        resources.sampled_view_for(TerrainSourceResourceRole::ShaderPackColor(
            "primary".to_string()
        ))
    );
    assert!(resources
        .resources()
        .combined_sampler_for(TerrainSourceResourceRole::ShaderPackColor(
            "primary".to_string()
        ))
        .is_some());
    let outputs = resolve_fullscreen_source_color_attachments(&program, &manifest, &targets)
        .expect("the lowered output must resolve through the same semantic color target");
    assert_eq!(1, outputs.len());
    assert_eq!(0, outputs[0].source_slot);
    assert_eq!(
        TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        outputs[0].role
    );
    assert_eq!(primary.current_attachment_view, outputs[0].view);
    let all_slots = source_color_attachments_by_slot(&manifest, &targets)
        .expect("all manifest color slots must resolve without backend defaults");
    assert_eq!(8, all_slots.len());
    assert_eq!(0, all_slots[0].source_slot);
    assert_eq!(6, all_slots[6].source_slot);
    assert_eq!(
        TerrainSourceResourceRole::ShaderPackColor("material_auxiliary".to_string()),
        all_slots[6].role
    );
    resources.destroy(&mut gal);
    cache.destroy(&mut gal);
}

#[test]
fn generic_source_color_resources_bind_current_targets_without_fullscreen_policy() {
    let source = ShaderPackSource::new(
        "generic-source-targets",
        7,
        vec![
            ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, settings()),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, bindings()),
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nuniform sampler2D colortex0;\n/* const bool colortex0MipmapEnabled = true; */\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
        ],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        ["primary".to_string()],
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let program = fullscreen_program(&source);

    let resources = prepare_source_color_resources(
        &mut gal,
        program.shader_pack_generation,
        &program.opaque_resource_bindings,
        &ShaderPackColorSamplingPlan::default(),
        &targets,
    )
    .expect("terrain/DH-style source sampling must allocate current named targets");
    let primary = targets.target("primary").unwrap();
    // The target owns a mip chain for another program, but this binding
    // did not request mip sampling: like Iris's non-mip minification
    // filter, it must observe mip 0 only, never a derivative-chosen level.
    assert_eq!(9, primary.mip_levels);
    assert_ne!(primary.current_view, primary.current_attachment_view);
    assert_eq!(
        Some(primary.current_attachment_view),
        resources.sampled_view_for(TerrainSourceResourceRole::ShaderPackColor(
            "primary".to_string()
        ))
    );
    resources.destroy(&mut gal);
    cache.destroy(&mut gal);
}

#[test]
fn generic_source_color_policy_rejects_unbound_sampler_identity() {
    let source = ShaderPackSource::new(
        "generic-source-policy-rejection",
        7,
        vec![
            ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, settings()),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, bindings()),
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
        ],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        std::iter::empty::<String>(),
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let program = fullscreen_program(&source);
    let invalid_policy = ShaderPackColorSamplingPlan {
        bindings: BTreeMap::from([(
            (
                TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
                99,
            ),
            SourceColorBinding {
                feedback: true,
                mipmapped: false,
            },
        )]),
    };
    assert!(prepare_source_color_resources(
        &mut gal,
        program.shader_pack_generation,
        &program.opaque_resource_bindings,
        &invalid_policy,
        &targets,
    )
    .unwrap_err()
    .to_string()
    .contains("references absent combined sampler"));
    cache.destroy(&mut gal);
}

#[test]
fn source_color_sampler_cache_follows_target_confirmation_and_discard() {
    let source = ShaderPackSource::new(
        "source-color-cache-transaction",
        7,
        vec![
            ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, settings()),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, bindings()),
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
        ],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let program = fullscreen_program(&source);
    let mut gal = gal();
    let mut target_cache = ShaderPackColorTargetCache::default();
    let mut resource_cache = ShaderPackSourceColorResourceCache::default();

    let first_identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        extent,
        std::iter::empty::<String>(),
        std::iter::empty::<String>(),
    )
    .unwrap();
    let first_targets = target_cache
        .stage(&mut gal, first_identity.clone(), &manifest)
        .unwrap();
    resource_cache
        .stage(
            &mut gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            &first_targets,
        )
        .unwrap();
    assert_eq!(0, resource_cache.active_len());
    assert_eq!(1, resource_cache.pending_len());
    resource_cache.discard_submission(&mut gal);
    target_cache.discard_submission(&mut gal);
    assert_eq!(0, resource_cache.active_len());
    assert_eq!(0, resource_cache.pending_len());

    let first_targets = target_cache
        .stage(&mut gal, first_identity, &manifest)
        .unwrap();
    resource_cache
        .stage(
            &mut gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            &first_targets,
        )
        .unwrap();
    resource_cache.confirm_submission(&mut gal).unwrap();
    target_cache.confirm_submission(&mut gal);
    assert_eq!(1, resource_cache.active_len());

    let replacement_targets = target_cache
        .stage(
            &mut gal,
            ShaderPackColorTargetIdentity::new(
                42,
                source.generation(),
                extent,
                std::iter::empty::<String>(),
                std::iter::empty::<String>(),
            )
            .unwrap(),
            &manifest,
        )
        .unwrap();
    resource_cache
        .stage(
            &mut gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            &replacement_targets,
        )
        .unwrap();
    resource_cache.confirm_submission(&mut gal).unwrap();
    target_cache.confirm_submission(&mut gal);
    assert_eq!(1, resource_cache.active_len());
    assert_eq!(0, resource_cache.pending_len());

    resource_cache.destroy(&mut gal);
    target_cache.destroy(&mut gal);
}

#[test]
fn terrain_outputs_resolve_named_pack_targets_without_attachment_indices() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        std::iter::empty::<String>(),
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();

    let attachments = resolve_terrain_source_color_attachments(
        &[
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 6),
        ],
        &manifest,
        &targets,
    )
    .expect("named terrain outputs must resolve through staged pack targets");
    assert_eq!(2, attachments.len());
    assert_eq!(TerrainPassOutput::LitTerrainColor, attachments[0].output);
    assert_eq!(0, attachments[0].source_slot);
    assert_eq!(
        TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        attachments[0].role
    );
    assert_eq!(
        targets.target("primary").unwrap().current_attachment_view,
        attachments[0].view
    );
    assert_eq!(TerrainPassOutput::MaterialAuxiliary, attachments[1].output);
    assert_eq!(6, attachments[1].source_slot);
    assert_eq!(
        TerrainSourceResourceRole::ShaderPackColor("material_auxiliary".to_string()),
        attachments[1].role
    );
    assert_eq!(
        targets
            .target("material_auxiliary")
            .unwrap()
            .current_attachment_view,
        attachments[1].view
    );

    let duplicate = resolve_terrain_source_color_attachments(
        &[
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 0),
        ],
        &manifest,
        &targets,
    )
    .unwrap_err();
    assert!(duplicate
        .to_string()
        .contains("more than one semantic output"));
    cache.destroy(&mut gal);
}

#[test]
fn fullscreen_output_resolution_rejects_a_manifest_program_semantic_mismatch() {
    let source = ShaderPackSource::new(
        "fullscreen-source-target-output-mismatch",
        7,
        vec![
            ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, settings()),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, bindings()),
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
        ],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let mut manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        std::iter::empty::<String>(),
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    manifest.targets.get_mut("primary").unwrap().role =
        TerrainSourceResourceRole::ShaderPackColor("previous_depth".to_string());
    let error = resolve_fullscreen_source_color_attachments(
        &fullscreen_program(&source),
        &manifest,
        &targets,
    )
    .unwrap_err();
    assert!(error.to_string().contains("maps to semantic color"));
    cache.destroy(&mut gal);
}

#[test]
fn fullscreen_color_resources_reject_mip_sampling_without_a_staged_chain() {
    let source = ShaderPackSource::new(
        "fullscreen-source-targets-no-mips",
        7,
        vec![
            ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, settings()),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, bindings()),
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nuniform sampler2D colortex0;\n/* const bool colortex0MipmapEnabled = true; */\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
        ],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["primary".to_string()],
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let error = prepare_fullscreen_source_color_resources(
        &mut gal,
        &fullscreen_program(&source),
        &targets,
    )
    .unwrap_err();
    assert!(error.to_string().contains("has no staged mip chain"));
    cache.destroy(&mut gal);
}

#[test]
fn feedback_target_must_be_declared_before_any_private_resource_is_created() {
    let source = source(settings());
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let identity = ShaderPackColorTargetIdentity::new(
        41,
        source.generation(),
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        ["absent_target".to_string()],
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();

    assert!(cache.stage(&mut gal, identity, &manifest).is_err());
    assert_eq!(0, gal.metrics().resource_creates);
}
