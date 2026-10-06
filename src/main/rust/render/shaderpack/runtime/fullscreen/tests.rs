use crate::render::shaderpack::runtime::fullscreen::*;
mod legacy_transform;
mod sky_transform;
mod sky_lightmap;
mod horizon_transform;
mod stage_colors;
mod coordinate_domains;
use crate::render::vulkanic::test_support::{presentation_capabilities, vulkan_capabilities};

use crate::render::vulkanic::commands::{
    CommandList, CommandListDesc, ResourceBarrier, SubmissionBatch,
};
use crate::render::vulkanic::frame::FrameRenderTargetId;
use crate::render::vulkanic::resources::{
    Extent3d, FrameTargetDesc, TextureDesc, TextureDimension, TextureFormat, TextureUsage,
    TextureViewDesc,
};
use crate::render::shaderpack::lowering::lower_fullscreen_source_pair;
use crate::render::shaderpack::source::preprocess::preprocess_source_stage_pair;
use crate::render::shaderpack::programs::prepare_lowered_fullscreen_source_program;
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
use crate::render::shaderpack::contracts::terrain::{
    TerrainSourceStage, TerrainSourceStages,
};
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailabilitySet,
    TerrainSourceResourceBindings, TERRAIN_RESOURCE_BINDINGS_PATH,
};

const SETTINGS: &str = concat!(
    "const int colortex0Format = RGBA8;\n",
    "const int colortex1Format = RGBA8;\n",
    "const int colortex2Format = RGBA8;\n",
    "const int colortex3Format = RGBA8;\n",
    "const int colortex4Format = RGBA8;\n",
    "const int colortex5Format = RGBA8;\n",
    "const int colortex6Format = RGBA8;\n",
    "const int colortex7Format = RGBA8;\n",
);
const BINDINGS: &str = concat!(
    "colortex0=shader_pack_color:primary\n",
    "colortex1=shader_pack_color:previous_depth\n",
    "colortex2=shader_pack_color:temporal_aa\n",
    "colortex3=shader_pack_color:translucent_final\n",
    "colortex4=shader_pack_color:volumetric_factor\n",
    "colortex5=shader_pack_color:normal_scene\n",
    "colortex6=shader_pack_color:material_auxiliary\n",
    "colortex7=shader_pack_color:temporal_reflection\n",
);

fn source(fragment: &str) -> ShaderPackSource {
    source_with_settings(fragment, SETTINGS)
}

fn source_with_settings(fragment: &str, settings: &str) -> ShaderPackSource {
    let fragment = if fragment.contains("DRAWBUFFERS:") {
        fragment.to_string()
    } else {
        fragment.replacen("#version 130\n", "#version 130\n/* DRAWBUFFERS:0 */\n", 1)
    };
    ShaderPackSource::new(
        "fullscreen-plan",
        9,
        vec![
            ShaderSourceFile::new("lib/pipelineSettings.glsl", settings),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, BINDINGS),
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new("world0/deferred1.fsh", fragment),
        ],
    )
    .unwrap()
}

fn program(source: &ShaderPackSource) -> LoweredFullscreenSourceProgram {
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
    let opaque = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&bindings)
        .unwrap();
    prepare_lowered_fullscreen_source_program(
        source.name(),
        source.generation(),
        "world0/deferred1.fsh",
        &lowered,
        &opaque,
    )
    .unwrap()
}

fn staged(
    source: &ShaderPackSource,
    gal: &mut VulkanicGal,
    feedback: bool,
) -> (
    ShaderPackColorTargetManifest,
    ShaderPackColorTargets,
    crate::render::shaderpack::resources::color_targets::ShaderPackColorTargetCache,
) {
    let bindings = TerrainSourceResourceBindings::from_source(source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(source, &bindings).unwrap();
    let identity = crate::render::shaderpack::resources::color_targets::ShaderPackColorTargetIdentity::new(
        13,
        source.generation(),
        Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        },
        feedback.then(|| "primary".to_string()).into_iter(),
        std::iter::empty::<String>(),
    )
    .unwrap();
    let mut cache = crate::render::shaderpack::resources::color_targets::ShaderPackColorTargetCache::default();
    let targets = cache.stage(gal, identity, &manifest).unwrap();
    (manifest, targets, cache)
}

/// Fullscreen staging requires an exact snapshot even when this focused
/// fixture has no external semantic resources. That distinguishes an
/// intentionally empty source contract from a caller that forgot to
/// provide its generation-bound resource snapshot.
fn empty_source_resource_snapshot(source: &ShaderPackSource) -> TerrainSourceOwnedResourceSet {
    TerrainSourceOwnedResourceSet::new(
        TerrainSourceResourceAvailabilitySet::new(source.generation(), 13, []).unwrap(),
        [],
    )
    .unwrap()
}

fn source_main_depth(gal: &mut VulkanicGal) -> (Handle, Handle) {
    let texture = gal
        .create_texture(TextureDesc {
            label: "source-final-output.depth".to_string(),
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
            label: "source-final-output.depth-view".to_string(),
            texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    (texture, view)
}

#[test]
fn rejects_feedback_execution_before_a_confirmed_source_history_frame() {
    let source = source(
        "#version 130\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, true);
    let plan = FullscreenSourceExecutionPlan::stage(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(empty_source_resource_snapshot(&source)),
        Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        },
    )
    .unwrap();
    assert_eq!(program.identity.as_str(), plan.prepared.program_identity);
    assert_eq!(1, plan.prepared.inputs.len());
    assert_eq!(1, plan.prepared.outputs.len());
    let texture_transforms = program
        .pack_texture_transforms(
            &crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
        )
        .unwrap();
    let mut operations = Vec::new();
    let mut color_frame = cache.begin_frame(&targets).unwrap();
    let error = plan
        .append_draw_with_color_frame(
            &program,
            &mut color_frame,
            FullscreenSourcePassFrame {
                texture_transforms,
                scalar_uniforms: Vec::new(),
                texture_transform_before: TextureUsageState::Undefined,
                scalar_uniform_before: None,
                clear_values: ShaderPackColorClearValues {
                    fog_color: crate::render::vulkanic::commands::ClearColor {
                        r: 0.0,
                        g: 0.0,
                        b: 0.0,
                        a: 1.0,
                    },
                },
                // The scheduler owns these states; this caller value is
                // deliberately ignored by the route-facing recorder.
                color_attachment_before: Vec::new(),
                clear_targets_this_pass: None,
            },
            &mut operations,
        )
        .unwrap_err();
    assert!(error.to_string().contains("feedback history"));
    assert!(operations.is_empty());
    plan.destroy(&mut gal);
    cache.destroy(&mut gal);
}

#[test]
fn final_output_plan_copies_only_the_final_stage_named_output_to_a_frame_target() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let (_manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "source-final-output.frame".to_string(),
            frame_id: 17,
            render_target: FrameRenderTargetId(17),
            extent: Extent3d {
                width: 16,
                height: 16,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let (depth_texture, depth_view) = source_main_depth(&mut gal);
    let plan = SourceFinalOutputPlan::stage(
        &mut gal,
        &program,
        &targets,
        frame_target,
        frame_target,
        depth_view,
        1,
    )
    .unwrap();
    assert_eq!(
        &TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        plan.source_role()
    );
    assert_eq!(FrameRenderTargetId(17), plan.identity().render_target);
    assert_eq!(
        [
            targets.identity.extent.width,
            targets.identity.extent.height,
            targets.identity.extent.depth,
        ],
        plan.identity().extent
    );
    assert_eq!(TextureFormat::Rgba8Unorm, plan.identity().color_format);
    let source_target = targets.target("primary").unwrap();
    let overlay_target = plan.overlay_target();
    assert_eq!(TextureFormat::Rgba8Unorm, plan.overlay_color_format());
    assert_ne!(frame_target, plan.overlay_color_attachment());
    assert_eq!(depth_view, plan.overlay_depth_attachment());
    // A final source pass samples main depth before the newly staged overlay
    // borrows it. Only the overlay color is new; depth retains that read.
    let source_render_target = gal.create_render_target(RenderTargetDesc {
        label: "depth-consumer.target".into(),
        color_views: vec![source_target.current_view],
        depth_stencil_view: None,
        extent: targets.identity.extent,
    }).unwrap();
    let depth_consumer = SourceColorCopyPlan::stage(
        &mut gal, "depth-consumer", depth_view, source_render_target,
        source_target.current_view, TextureFormat::Rgba8Unorm, None,
    ).unwrap();
    let mut operations = vec![CommandOp::Barrier(ResourceBarrier {
        resource: source_target.current_texture,
        subresources: None,
        before: TextureUsageState::Undefined,
        after: TextureUsageState::ColorAttachment,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    })];
    depth_consumer.append_draw(&mut operations);
    operations.push(CommandOp::Barrier(ResourceBarrier {
        resource: source_target.current_texture,
        subresources: None,
        before: TextureUsageState::ColorAttachment,
        after: TextureUsageState::ShaderRead,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }));
    plan.append_source_copy_from_state(&mut operations, TextureUsageState::Undefined);
    // Model a world overlay pass between the source copy and final
    // presentation. The color target must remain an attachment until the
    // last overlay writer has completed.
    operations.push(CommandOp::BeginPass {
        pass: plan.overlay_pass(),
        target: overlay_target,
        colors: vec![PassAttachment {
            view: plan.overlay_color_attachment(),
            load_op: AttachmentLoadOp::Load,
            store_op: AttachmentStoreOp::Store,
            clear_color: None,
        }],
        depth_stencil: Some(PassAttachment {
            view: depth_view,
            load_op: AttachmentLoadOp::Load,
            store_op: AttachmentStoreOp::Store,
            clear_color: None,
        }),
    });
    operations.push(CommandOp::EndPass);
    plan.append_present_copy(&mut operations);
    let presented_capture = plan
        .stage_presented_capture(&mut gal, "source-final-output.presented-capture")
        .unwrap();
    presented_capture.append_draw(&mut operations);
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { target, depth_stencil: Some(depth), .. }
            if *target == overlay_target && depth.view == depth_view
    )));
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { target, colors, .. }
            if *target == frame_target && colors[0].view == frame_target
    )));
    let present_begin = operations
        .iter()
        .position(|operation| {
            matches!(
                operation,
                CommandOp::BeginPass { target, .. } if *target == frame_target
            )
        })
        .unwrap();
    let capture_begin = operations
        .iter()
        .position(|operation| {
            matches!(
                operation,
                CommandOp::BeginPass { target, .. }
                    if *target == presented_capture.target
            )
        })
        .unwrap();
    assert!(
        present_begin < capture_begin,
        "the diagnostic mirror must copy after the acquired-target present copy"
    );
    let overlay_begin = operations
        .iter()
        .position(|operation| {
            matches!(
                operation,
                CommandOp::BeginPass { pass, target, .. }
                    if *pass == plan.overlay_pass() && *target == overlay_target
            )
        })
        .unwrap();
    let overlay_end = overlay_begin + 1;
    assert!(matches!(operations[overlay_end], CommandOp::EndPass));
    let present_transition = operations
        .iter()
        .position(|operation| {
            matches!(
                operation,
                CommandOp::Barrier(barrier)
                    if barrier.resource == plan.overlay.color_texture()
                        && barrier.before == TextureUsageState::ColorAttachment
                        && barrier.after == TextureUsageState::ShaderRead
            )
        })
        .unwrap();
    assert!(
        overlay_end < present_transition,
        "the overlay target must become shader-readable only after its final attachment writer"
    );
    gal.submit(SubmissionBatch {
        label: "source-final-output".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "source-final-output.commands".to_string(),
            operations,
        })],
    })
    .expect("the source final copy must be an ordinary explicit GAL submission");
    depth_consumer.destroy(&mut gal);
    gal.destroy(source_render_target).unwrap();
    presented_capture.destroy(&mut gal);
    plan.destroy(&mut gal);
    gal.destroy(depth_view).unwrap();
    gal.destroy(depth_texture).unwrap();
    cache.destroy(&mut gal);
}

#[test]
fn source_overlay_target_owns_color_and_keeps_the_source_depth_dependency_explicit() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let extent = Extent3d {
        width: 16,
        height: 16,
        depth: 1,
    };
    let depth_texture = gal
        .create_texture(TextureDesc {
            label: "source-overlay.depth".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::DepthStencilAttachment, TextureUsage::Sampled],
        })
        .unwrap();
    let depth_view = gal
        .create_texture_view(TextureViewDesc {
            label: "source-overlay.depth-view".to_string(),
            texture: depth_texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let overlay = SourceOverlayTarget::stage(
        &mut gal,
        "source-overlay",
        extent,
        TextureFormat::Rgba16Float,
        depth_view,
    )
    .unwrap();
    assert_eq!(extent, overlay.extent());
    assert_eq!(TextureFormat::Rgba16Float, overlay.color_format());
    assert_eq!(depth_view, overlay.depth_view());
    assert_eq!(
        overlay.color_view(),
        gal.pass_target_color_attachment(overlay.target()).unwrap()
    );
    assert_eq!(
        Some((depth_texture, depth_view)),
        gal.pass_target_depth_attachment(overlay.target()).unwrap()
    );
    assert!(gal.destroy(depth_view).is_err());

    overlay.destroy(&mut gal);
    gal.destroy(depth_view).unwrap();
    gal.destroy(depth_texture).unwrap();
}

#[test]
fn final_output_cache_reuses_a_semantic_swapchain_slot_and_discards_only_unsubmitted_staging() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let (_manifest, targets, mut source_targets) = staged(&source, &mut gal, false);
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "source-final-output.cached-frame".to_string(),
            frame_id: 19,
            render_target: FrameRenderTargetId(3),
            extent: Extent3d {
                width: 16,
                height: 16,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let (depth_texture, depth_view) = source_main_depth(&mut gal);
    let mut final_cache = SourceFinalOutputCache::default();
    let first = final_cache
        .reserve(
            &mut gal,
            &program,
            &targets,
            frame_target,
            frame_target,
            depth_view,
            1,
        )
        .unwrap();
    assert!(first.newly_staged);
    assert_eq!(FrameRenderTargetId(3), first.identity().render_target);
    let creates_after_first = gal.metrics().resource_creates;
    let second = final_cache
        .reserve(
            &mut gal,
            &program,
            &targets,
            frame_target,
            frame_target,
            depth_view,
            1,
        )
        .unwrap();
    assert!(!second.newly_staged);
    assert_eq!(first.identity(), second.identity());
    assert_eq!(creates_after_first, gal.metrics().resource_creates);
    assert_eq!(1, final_cache.len());

    let after_graph_rebuild = final_cache
        .reserve(
            &mut gal,
            &program,
            &targets,
            frame_target,
            frame_target,
            depth_view,
            2,
        )
        .unwrap();
    assert!(after_graph_rebuild.newly_staged);
    assert_ne!(first.identity(), after_graph_rebuild.identity());
    assert_eq!(2, final_cache.len());
    final_cache.discard(after_graph_rebuild, &mut gal);
    assert_eq!(1, final_cache.len());

    final_cache.confirm(&first).unwrap();
    final_cache.discard(second, &mut gal);
    assert_eq!(1, final_cache.len());

    // Repeated graph rebuilds for one acquired slot must not retain a
    // plan per generation forever.  Confirm each replacement to model
    // ordinary submitted frames; GAL retirement keeps any in-flight
    // native use safe while the semantic cache remains bounded.
    for graph_generation in 3..=16 {
        let replacement = final_cache
            .reserve(
                &mut gal,
                &program,
                &targets,
                frame_target,
                frame_target,
                depth_view,
                graph_generation,
            )
            .unwrap();
        assert!(replacement.newly_staged);
        final_cache.confirm(&replacement).unwrap();
    }
    assert!(
        final_cache.len() <= SourceFinalOutputCache::MAX_PLANS_PER_FRAME_TARGET,
        "repeated graph rebuilds must retain only a bounded frame-slot history"
    );

    let other_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "source-final-output.cached-frame-other-slot".to_string(),
            frame_id: 20,
            render_target: FrameRenderTargetId(4),
            extent: Extent3d {
                width: 16,
                height: 16,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let frame_slot_history = final_cache.len();
    let unsubmitted = final_cache
        .reserve(
            &mut gal,
            &program,
            &targets,
            other_target,
            other_target,
            depth_view,
            1,
        )
        .unwrap();
    assert!(unsubmitted.newly_staged);
    assert_eq!(
        frame_slot_history + 1,
        final_cache.len(),
        "a distinct acquired slot may add one independent final-output plan"
    );
    final_cache.discard(unsubmitted, &mut gal);
    assert_eq!(frame_slot_history, final_cache.len());

    final_cache.retire_frame_targets(&mut gal, &[frame_target]);
    assert_eq!(
        0,
        final_cache.len(),
        "swapchain-target retirement must release the cached pass before the target",
    );
    gal.destroy(frame_target)
        .expect("the cached final pass must not keep a retired frame target alive");
    gal.destroy(other_target).unwrap();
    final_cache.destroy(&mut gal);
    gal.destroy(depth_view).unwrap();
    gal.destroy(depth_texture).unwrap();
    source_targets.destroy(&mut gal);
}

#[test]
fn final_output_plan_rejects_a_named_color_texture_as_a_presentation_target() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (_manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let color_texture = targets.target("primary").unwrap().current_texture;
    let creates_before = gal.metrics().resource_creates;
    let error = SourceFinalOutputPlan::stage(
        &mut gal,
        &program,
        &targets,
        color_texture,
        color_texture,
        color_texture,
        1,
    )
    .unwrap_err();
    assert!(error
        .to_string()
        .contains("requires an acquired GAL frame target"));
    assert_eq!(
        creates_before,
        gal.metrics().resource_creates,
        "an invalid final target must reject before staging any copy resources"
    );
    cache.destroy(&mut gal);
}

#[test]
fn final_output_plan_rejects_an_incompatible_acquired_target_extent_before_staging() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let (_manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "source-final-output.wrong-extent".to_string(),
            frame_id: 18,
            render_target: FrameRenderTargetId(18),
            extent: Extent3d {
                width: 8,
                height: 8,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let creates_before = gal.metrics().resource_creates;
    let error = SourceFinalOutputPlan::stage(
        &mut gal,
        &program,
        &targets,
        frame_target,
        frame_target,
        frame_target,
        1,
    )
    .unwrap_err();
    assert!(error
        .to_string()
        .contains("does not match acquired frame target extent"));
    assert_eq!(creates_before, gal.metrics().resource_creates);
    gal.destroy(frame_target).unwrap();
    cache.destroy(&mut gal);
}

#[test]
fn source_final_copy_shaders_compile_at_the_vulkan_boundary() {
    let backend = match crate::render::vulkanic::test_support::vulkan_gal("MattMC source final-copy Vulkan conformance") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("Vulkan")
                    || text.contains("vulkan")
                    || text.contains("physical device"),
                "unexpected Vulkan source final-copy setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    for module in source_final_copy_shader_modules(
        crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions,
        "source-final-copy.vulkan",
    ) {
        gal.create_shader_module(module).unwrap_or_else(|error| {
            panic!("Rust-owned source final-copy shader must lower through Vulkan: {error}")
        });
    }
}

#[test]
fn source_final_copy_shaders_compile_at_the_opengl_boundary() {
    let backend = match crate::render::vulkanic::test_support::opengl_gal("MattMC source final-copy OpenGL conformance") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                "unexpected OpenGL source final-copy setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    for module in source_final_copy_shader_modules(
        crate::render::vulkanic::test_support::opengl_capabilities().shader_conventions,
        "source-final-copy.opengl",
    ) {
        gal.create_shader_module(module).unwrap_or_else(|error| {
            panic!("Rust-owned source final-copy shader must lower through OpenGL: {error}")
        });
    }
}

#[test]
fn nonclearing_source_output_records_dont_care_on_first_write() {
    let settings = concat!(
        "const int colortex0Format = RGBA8;\n",
        "const bool colortex0Clear = false;\n",
    );
    let source = source_with_settings(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
        settings,
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let plan = FullscreenSourceExecutionPlan::stage(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(empty_source_resource_snapshot(&source)),
        Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        },
    )
    .unwrap();
    let mut operations = Vec::new();
    let mut color_frame = cache.begin_frame(&targets).unwrap();
    plan.append_draw_with_color_frame(
        &program,
        &mut color_frame,
        FullscreenSourcePassFrame {
            texture_transforms: program
                .pack_texture_transforms(
                    &crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                )
                .unwrap(),
            scalar_uniforms: Vec::new(),
            texture_transform_before: TextureUsageState::Undefined,
            scalar_uniform_before: None,
            clear_values: ShaderPackColorClearValues {
                fog_color: crate::render::vulkanic::commands::ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                },
            },
            color_attachment_before: Vec::new(),
            clear_targets_this_pass: None,
        },
        &mut operations,
    )
    .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, .. }
            if colors[0].load_op == AttachmentLoadOp::DontCare
    )));
    gal.submit(SubmissionBatch {
        label: "fullscreen-source-nonclearing-first-write".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "fullscreen-source-nonclearing-first-write.commands".to_string(),
            operations,
        })],
    })
    .expect("a non-clearing source output may be first-written without invented history");
    cache
        .confirm_frame_submission(&mut gal, color_frame)
        .expect("the successful source submission must seed only confirmed color history");
    plan.destroy(&mut gal);
    cache.destroy(&mut gal);
}

#[test]
fn clearing_source_output_clears_again_after_a_prior_frame() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let plan = FullscreenSourceExecutionPlan::stage(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(empty_source_resource_snapshot(&source)),
        Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        },
    )
    .unwrap();
    let mut operations = Vec::new();
    plan.append_draw(
        &program,
        FullscreenSourcePassFrame {
            texture_transforms: program
                .pack_texture_transforms(
                    &crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                )
                .unwrap(),
            scalar_uniforms: Vec::new(),
            texture_transform_before: TextureUsageState::ShaderRead,
            scalar_uniform_before: None,
            clear_values: ShaderPackColorClearValues {
                fog_color: crate::render::vulkanic::commands::ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                },
            },
            color_attachment_before: vec![TextureUsageState::ShaderRead],
            clear_targets_this_pass: None,
        },
        &mut operations,
    )
    .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, .. }
            if colors[0].load_op == AttachmentLoadOp::Clear
    )));
    plan.destroy(&mut gal);
    cache.destroy(&mut gal);
}

#[test]
fn clearing_primary_source_output_uses_the_semantic_fog_clear() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, _cache) = staged(&source, &mut gal, false);
    let plan = FullscreenSourceExecutionPlan::stage(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(empty_source_resource_snapshot(&source)),
        Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        },
    )
    .unwrap();
    let mut operations = Vec::new();
    let fog_color = crate::render::vulkanic::commands::ClearColor {
        r: 0.25,
        g: 0.5,
        b: 0.75,
        a: 1.0,
    };
    plan.append_draw(
        &program,
        FullscreenSourcePassFrame {
            texture_transforms: program
                .pack_texture_transforms(
                    &crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                )
                .unwrap(),
            scalar_uniforms: Vec::new(),
            texture_transform_before: TextureUsageState::Undefined,
            scalar_uniform_before: None,
            clear_values: ShaderPackColorClearValues { fog_color },
            color_attachment_before: vec![TextureUsageState::Undefined],
            clear_targets_this_pass: None,
        },
        &mut operations,
    )
    .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass { colors, .. }
            if colors[0].load_op == AttachmentLoadOp::Clear
                && colors[0].clear_color == Some(fog_color)
    )));
    plan.destroy(&mut gal);
}

#[test]
fn source_sky_disc_records_its_owned_twenty_four_vertex_geometry() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let mut program = program(&source);
    program.raster_primitive =
        crate::render::shaderpack::lowering::FullscreenSourceRasterPrimitive::VanillaSkyDisc;
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, _) = staged(&source, &mut gal, false);
    let plan = FullscreenSourceExecutionPlan::stage(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(empty_source_resource_snapshot(&source)),
        Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        },
    )
    .unwrap();
    let mut operations = Vec::new();
    plan.append_draw(
        &program,
        FullscreenSourcePassFrame {
            texture_transforms: program
                .pack_texture_transforms(&crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain())
                .unwrap(),
            scalar_uniforms: Vec::new(),
            texture_transform_before: TextureUsageState::Undefined,
            scalar_uniform_before: None,
            clear_values: ShaderPackColorClearValues {
                fog_color: crate::render::vulkanic::commands::ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                },
            },
            color_attachment_before: vec![TextureUsageState::Undefined],
            clear_targets_this_pass: None,
        },
        &mut operations,
    )
    .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::Draw {
            vertices: 24,
            instances: 1
        }
    )));
    plan.destroy(&mut gal);
}

#[test]
fn source_celestial_quad_records_owned_sky_box_capacity_geometry() {
    let source = source(
        "#version 130\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
    );
    let mut program = program(&source);
    program.raster_primitive =
        crate::render::shaderpack::lowering::FullscreenSourceRasterPrimitive::VanillaCelestialQuad;
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, _) = staged(&source, &mut gal, false);
    let plan = FullscreenSourceExecutionPlan::stage(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(empty_source_resource_snapshot(&source)),
        Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        },
    )
    .unwrap();
    let mut operations = Vec::new();
    plan.append_draw(
        &program,
        FullscreenSourcePassFrame {
            texture_transforms: program
                .pack_texture_transforms(&crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain())
                .unwrap(),
            scalar_uniforms: Vec::new(),
            texture_transform_before: TextureUsageState::Undefined,
            scalar_uniform_before: None,
            clear_values: ShaderPackColorClearValues {
                fog_color: crate::render::vulkanic::commands::ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                },
            },
            color_attachment_before: vec![TextureUsageState::Undefined],
            clear_targets_this_pass: None,
        },
        &mut operations,
    )
    .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::Draw {
            vertices: 36,
            instances: 1
        }
    )));
    plan.destroy(&mut gal);
}

#[test]
fn rejects_a_declared_feedback_pass_when_the_staged_target_has_no_previous_image() {
    let source = source(
        "#version 130\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let error = PreparedFullscreenSourcePass::prepare(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::empty::<TerrainSourceOwnedResourceSet>(),
    )
    .unwrap_err();
    assert!(error.to_string().contains("no previous feedback view"));
    cache.destroy(&mut gal);
}

#[test]
fn rejects_sparse_source_output_locations_before_staging() {
    let source = source(
        "#version 130\n/* DRAWBUFFERS:01234 */\nin vec2 uv;\nvoid main() { gl_FragData[4] = vec4(uv, 0.0, 1.0); }",
    );
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
    let artifacts = preprocess_source_stage_pair(&source, &stages).unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let error = lower_fullscreen_source_pair(&artifacts.vertex, &artifacts.fragment, &bindings)
        .unwrap_err();
    assert!(error.to_string().contains("sparse gl_FragData locations"));
}

#[test]
fn stage_cache_reuses_frame_invariant_objects_for_one_live_plan_at_a_time() {
    let source = source(
        "#version 130\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
    );
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, mut targets_cache) = staged(&source, &mut gal, true);
    let pipelines = FullscreenPipelineCache::default();
    let extent = Extent3d { width: 16, height: 16, depth: 1 };
    let mut stage = |gal: &mut VulkanicGal| {
        FullscreenSourceExecutionPlan::stage_cached(
            gal,
            &program,
            &manifest,
            &targets,
            std::iter::once(empty_source_resource_snapshot(&source)),
            extent,
            Some((&pipelines, (1, 1))),
        )
        .unwrap()
    };

    let first = stage(&mut gal);
    // A second plan while the first is live must not share its uniforms.
    let concurrent = stage(&mut gal);
    assert_ne!(first.compiled.target, concurrent.compiled.target);
    assert_ne!(first.bound.texture_transform_buffer, concurrent.bound.texture_transform_buffer);
    let (target, uniforms, source_set, pack_set) = (
        first.compiled.target,
        first.bound.texture_transform_buffer,
        first.bound.source_data_set,
        first.bound.pack_resources_set,
    );
    first.destroy(&mut gal);
    concurrent.destroy(&mut gal);

    let next = stage(&mut gal);
    assert!(next.stage_lease.is_some());
    assert_eq!(target, next.compiled.target);
    assert_eq!(uniforms, next.bound.texture_transform_buffer);
    assert_eq!(source_set, next.bound.source_data_set);
    assert_ne!(pack_set, next.bound.pack_resources_set);
    let next_pack_set = next.bound.pack_resources_set;

    // Released while leased: destroyed only once the plan returns it.
    pipelines.release_stages(&mut gal);
    assert_eq!(1, pipelines.retired_stages.borrow().len());
    next.destroy(&mut gal);
    assert!(gal.destroy(next_pack_set).is_err());
    pipelines.retire_stages(&mut gal, []);
    assert!(pipelines.retired_stages.borrow().is_empty());
    assert!(gal.destroy(target).is_err());
    assert!(gal.destroy(source_set).is_err());

    pipelines.destroy(&mut gal);
    targets_cache.destroy(&mut gal);
}
