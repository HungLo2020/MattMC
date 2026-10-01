use crate::render::shaderpack::vanilla::fabulous::*;
    use crate::render::vulkanic::test_support::vulkan_capabilities;
use crate::render::vulkanic::frame::{FrameRenderTargetId, FrameSurfaceDesc, PresentMode};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::HandleKind;
use crate::render::vulkanic::resources::FrameTargetDesc;

#[test]
fn attachment_contract_is_explicitly_two_dimensional_and_bounded() {
    assert_eq!(FABULOUS_DEPTH_FORMAT, TextureFormat::Depth32Float);
    assert_eq!(
        Extent3d {
            width: 1,
            height: 1,
            depth: 1
        }
        .depth,
        1
    );
}

#[test]
fn material_source_families_map_to_distinct_fabulous_roles() {
    use crate::render::scene::material::WORLD_MATERIAL_SOURCE_CLOUDS;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_ENTITY_MODEL;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_PARTICLES;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_TEXTURED;
use crate::render::scene::material::WORLD_MATERIAL_SOURCE_WEATHER;
    assert_eq!(
        FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_TEXTURED),
        Some(FabulousTargetRole::Translucent)
    );
    assert_eq!(
        FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_ENTITY_MODEL),
        Some(FabulousTargetRole::Translucent)
    );
    assert_eq!(
        FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_PARTICLES),
        Some(FabulousTargetRole::Particles)
    );
    assert_eq!(
        FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_CLOUDS),
        Some(FabulousTargetRole::Clouds)
    );
    assert_eq!(
        FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_WEATHER),
        Some(FabulousTargetRole::Weather)
    );
    assert_eq!(
        FabulousTargetRole::for_material_source(0),
        Some(FabulousTargetRole::Translucent)
    );
}

#[test]
fn attachment_set_allocates_explicit_passes_and_validates_the_bundled_graph() {
    let mut capabilities = vulkan_capabilities();
    capabilities.features.presentation = true;
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(capabilities);
    let set = FabulousAttachmentSet::create(
        &mut gal,
        Extent3d {
            width: 64,
            height: 64,
            depth: 1,
        },
        TextureFormat::Rgba8Unorm,
    )
    .unwrap();
    assert_ne!(set.main.render_pass, Handle::NULL);
    assert_ne!(set.translucent.render_pass, set.particles.render_pass);
    assert_ne!(set.final_target.render_target, set.main.render_target);
    assert_eq!(set.bindings.as_ref().unwrap().combined_samplers.len(), 12);
    let executor =
        crate::render::shaderpack::vanilla::post_effect::executor::bundled_transparency_executor().unwrap();
    set.external_inventory()
        .validate_against(executor.plan())
        .unwrap();
    assert_eq!(13, set.pre_transparency_barriers().len());
    let mut capture_copy = Vec::new();
    set.append_translucent_capture_copy(
        &mut capture_copy,
        set.main.color_texture,
        Extent3d {
            width: 64,
            height: 64,
            depth: 1,
        },
        TextureUsageState::ShaderRead,
        TextureUsageState::Undefined,
    )
    .unwrap();
    assert!(capture_copy.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::CopyTexture(copy)
            if copy.src_texture == set.main.color_texture
                && copy.dst_texture == set.translucent.color_texture
    )));
    let stateful_barriers = set.external_shader_read_barriers_with_translucent_state(
        TextureUsageState::ShaderRead,
        TextureUsageState::Undefined,
    );
    assert!(!stateful_barriers.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::Barrier(barrier)
            if barrier.resource == set.translucent.color_texture
                && barrier.before == TextureUsageState::ShaderRead
    )));
    let mut main_copy = Vec::new();
    set.append_frame_target_to_main_copy(
        &mut main_copy,
        Handle::new(HandleKind::FrameTarget, 99, 1).unwrap(),
        Extent3d {
            width: 64,
            height: 64,
            depth: 1,
        },
        TextureUsageState::Undefined,
    )
    .unwrap();
    assert!(main_copy.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::CopyFrameTargetToTexture {
            src,
            dst,
            ..
        } if *src == Handle::new(HandleKind::FrameTarget, 99, 1).unwrap()
            && *dst == set.main.color_texture
    )));
    let mut depth_copy = Vec::new();
    set.append_depth_capture_copy(
        &mut depth_copy,
        set.main.depth_texture,
        FabulousTargetRole::Translucent,
        Extent3d {
            width: 64,
            height: 64,
            depth: 1,
        },
        TextureUsageState::ShaderRead,
        TextureUsageState::Undefined,
    )
    .unwrap();
    assert!(depth_copy.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::CopyTexture(copy)
            if copy.src_texture == set.main.depth_texture
                && copy.dst_texture == set.translucent.depth_texture
    )));
    assert_eq!(1, set.between_transparency_pass_barriers().len());
    let copy_in = set.optical_hand_copy_from_main(Extent3d {
        width: 64,
        height: 64,
        depth: 1,
    });
    assert_eq!(5, copy_in.len());
    assert!(copy_in.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::CopyTexture(copy)
            if copy.src_texture == set.main.color_texture
                && copy.dst_texture == set.optical_hand.color_texture
    )));
    let copy_out = set.optical_hand_copy_to_main(Extent3d {
        width: 64,
        height: 64,
        depth: 1,
    });
    assert_eq!(5, copy_out.len());
    assert!(copy_out.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::CopyTexture(copy)
            if copy.src_texture == set.optical_hand.color_texture
                && copy.dst_texture == set.main.color_texture
    )));
    let pass_bindings = set.transparency_pass_bindings().unwrap();
    let operations = executor.lower(&pass_bindings).unwrap();
    assert_eq!(
        2,
        operations
            .iter()
            .filter(|operation| matches!(
                operation,
                crate::render::vulkanic::commands::CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    gal.create_command_list(crate::render::vulkanic::commands::CommandListDesc {
        label: "fabulous-transparency-test".to_string(),
        operations,
    })
    .unwrap();
    gal.configure_frame_surface(FrameSurfaceDesc {
        label: "fabulous-frame-surface".to_string(),
        extent: Extent3d {
            width: 64,
            height: 64,
            depth: 1,
        },
        color_format: TextureFormat::Rgba8Unorm,
        present_mode: PresentMode::Fifo,
        max_frames_in_flight: 2,
    })
    .unwrap();
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "fabulous-frame-target".to_string(),
            frame_id: 1,
            render_target: FrameRenderTargetId(1),
            extent: Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let mut terrain_handoff = Vec::new();
    let presentation_pass = set
        .append_terrain_handoff_to_frame_target(
            &mut gal,
            &mut terrain_handoff,
            frame_target,
            Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            frame_target,
            set.main.color_texture,
            set.main.depth_texture,
            set.main.depth_texture,
        )
        .unwrap();
    assert!(terrain_handoff.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::CopyFrameTargetToTexture { .. }
    )));
    assert_ne!(Handle::NULL, presentation_pass);
    let (frame_bindings, present_pass) = set
        .transparency_pass_bindings_to_frame_target(&mut gal, frame_target)
        .unwrap();
    assert_eq!(frame_target, frame_bindings.last().unwrap().render_target);
    assert_eq!(
        frame_target,
        frame_bindings.last().unwrap().color_attachment
    );
    assert!(frame_bindings.last().unwrap().depth_attachment.is_none());
    let frame_operations = executor.lower(&frame_bindings).unwrap();
    gal.create_command_list(crate::render::vulkanic::commands::CommandListDesc {
        label: "fabulous-transparency-frame-target-test".to_string(),
        operations: frame_operations,
    })
    .unwrap();
    gal.destroy(presentation_pass).unwrap();
    gal.destroy(present_pass).unwrap();
    set.destroy(&mut gal);
}

#[test]
fn terrain_handoff_accepts_bgra_frame_with_rgba_translucent_capture() {
    let mut capabilities = vulkan_capabilities();
    capabilities.features.presentation = true;
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(capabilities);
    let set = FabulousAttachmentSet::create_with_translucent_format(
        &mut gal,
        Extent3d {
            width: 32,
            height: 32,
            depth: 1,
        },
        TextureFormat::Bgra8Unorm,
        TextureFormat::Rgba8Unorm,
    )
    .unwrap();
    assert_eq!(TextureFormat::Rgba8Unorm, set.translucent_color_format);
    gal.configure_frame_surface(FrameSurfaceDesc {
        label: "terrain-bgra-surface".to_string(),
        extent: Extent3d {
            width: 32,
            height: 32,
            depth: 1,
        },
        color_format: TextureFormat::Bgra8Unorm,
        present_mode: PresentMode::Fifo,
        max_frames_in_flight: 2,
    })
    .unwrap();
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "terrain-bgra-target".to_string(),
            frame_id: 7,
            render_target: FrameRenderTargetId(7),
            extent: Extent3d {
                width: 32,
                height: 32,
                depth: 1,
            },
            color_format: TextureFormat::Bgra8Unorm,
        })
        .unwrap();
    let capture_source = gal
        .create_texture(TextureDesc {
            label: "terrain-bgra-capture-source".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 32,
                height: 32,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::TransferSrc, TextureUsage::Sampled],
        })
        .unwrap();
    let capture_depth = gal
        .create_texture(TextureDesc {
            label: "terrain-bgra-depth-source".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent: Extent3d {
                width: 32,
                height: 32,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::TransferSrc, TextureUsage::Sampled],
        })
        .unwrap();
    let translucent_depth = gal
        .create_texture(TextureDesc {
            label: "terrain-independent-translucent-depth".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent: Extent3d {
                width: 32,
                height: 32,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::TransferSrc, TextureUsage::Sampled],
        })
        .unwrap();
    let mut operations = Vec::new();
    let presentation_pass = set
        .append_terrain_handoff_to_frame_target(
            &mut gal,
            &mut operations,
            frame_target,
            Extent3d {
                width: 32,
                height: 32,
                depth: 1,
            },
            frame_target,
            capture_source,
            capture_depth,
            translucent_depth,
        )
        .unwrap();
    let depth_copy_sources: Vec<_> = operations
        .iter()
        .filter_map(|operation| match operation {
            crate::render::vulkanic::commands::CommandOp::CopyTexture(copy)
                if copy.dst_texture == set.main.depth_texture
                    || copy.dst_texture == set.translucent.depth_texture =>
            {
                Some((copy.src_texture, copy.dst_texture))
            }
            _ => None,
        })
        .collect();
    assert_eq!(
        depth_copy_sources,
        vec![
            (capture_depth, set.main.depth_texture),
            (translucent_depth, set.translucent.depth_texture)
        ],
        "each Fabulous color layer must retain its own producer depth"
    );
    gal.create_command_list(crate::render::vulkanic::commands::CommandListDesc {
        label: "terrain-bgra-handoff-test".to_string(),
        operations,
    })
    .unwrap();
    assert_ne!(Handle::NULL, presentation_pass);
    gal.destroy(presentation_pass).unwrap();
    let mut no_translucent_operations = Vec::new();
    let no_translucent_presentation_pass = set
        .append_terrain_handoff_to_frame_target_with_external_ops(
            &mut gal,
            &mut no_translucent_operations,
            frame_target,
            Extent3d {
                width: 32,
                height: 32,
                depth: 1,
            },
            frame_target,
            capture_source,
            capture_depth,
            capture_depth,
            &[],
            [false; 4],
            false,
            false,
        )
        .unwrap();
    assert!(!no_translucent_operations.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::CopyTexture(copy)
            if copy.src_texture == capture_source
    )));
    assert!(no_translucent_operations.iter().any(|operation| matches!(
        operation,
        crate::render::vulkanic::commands::CommandOp::BeginPass { target, .. }
            if *target == set.translucent.render_target
    )));
    gal.destroy(no_translucent_presentation_pass).unwrap();
    for row_order in [TextureRowOrder::Preserve, TextureRowOrder::Reverse] {
        for has_translucent in [false, true] {
            let mut oriented = Vec::new();
            let pass = set
                .append_terrain_handoff_to_frame_target_oriented(
                    &mut gal,
                    &mut oriented,
                    frame_target,
                    Extent3d {
                        width: 32,
                        height: 32,
                        depth: 1,
                    },
                    frame_target,
                    capture_source,
                    capture_depth,
                    translucent_depth,
                    &[],
                    [false; 4],
                    false,
                    has_translucent,
                    row_order,
                )
                .unwrap();
            let copies: Vec<_> = oriented
                .iter()
                .filter_map(|op| match op {
                    CommandOp::CopyTexture(copy) => Some(copy),
                    _ => None,
                })
                .collect();
            assert_eq!(copies.len(), if has_translucent { 3 } else { 2 });
            assert!(copies.iter().all(|copy| copy.row_order == row_order),
                "deferred translucent color and both depths must share one normalization contract");
            assert!(
                oriented.iter().any(|op| matches!(op,
                CommandOp::CopyFrameTargetToTexture { src, dst, .. }
                    if *src == frame_target && *dst == set.main.color_texture)),
                "already-canonical acquired color must not be reversed again"
            );
            gal.create_command_list(crate::render::vulkanic::commands::CommandListDesc {
                label: "oriented-terrain-handoff".into(),
                operations: oriented,
            })
            .unwrap();
            gal.destroy(pass).unwrap();
        }
    }
    // External producers load their role images, unlike item/entity
    // work which the earlier world graph has already populated.
    for (role_index, attachment) in [(1, &set.particles), (2, &set.clouds), (3, &set.weather)] {
        let mut external = Vec::new();
        set.append_empty_attachment_clear(
            &mut external,
            [
                FabulousTargetRole::Particles,
                FabulousTargetRole::Clouds,
                FabulousTargetRole::Weather,
            ][role_index - 1],
            TextureUsageState::ShaderRead,
            TextureUsageState::ShaderRead,
        );
        // Model the real producer's Load pass, leaving attachments in
        // their writable layouts for the handoff's final transitions.
        external.truncate(4);
        if let CommandOp::BeginPass {
            colors,
            depth_stencil,
            ..
        } = &mut external[2]
        {
            colors[0].load_op = AttachmentLoadOp::Load;
            colors[0].clear_color = None;
            depth_stencil.as_mut().unwrap().load_op = AttachmentLoadOp::Load;
        } else {
            panic!("expected external attachment pass");
        }
        let mut written = [false; 4];
        written[role_index] = true;
        let mut ops = Vec::new();
        let pass = set
            .append_terrain_handoff_to_frame_target_with_external_ops(
                &mut gal,
                &mut ops,
                frame_target,
                Extent3d {
                    width: 32,
                    height: 32,
                    depth: 1,
                },
                frame_target,
                capture_source,
                capture_depth,
                translucent_depth,
                &external,
                written,
                false,
                false,
            )
            .unwrap();
        let loads: Vec<_> = ops
            .iter()
            .filter_map(|op| match op {
                CommandOp::BeginPass {
                    target,
                    colors,
                    depth_stencil,
                    ..
                } if *target == attachment.render_target => {
                    Some((colors[0].load_op, depth_stencil.as_ref().unwrap().load_op))
                }
                _ => None,
            })
            .collect();
        assert_eq!(
            loads,
            vec![
                (AttachmentLoadOp::Clear, AttachmentLoadOp::Clear),
                (AttachmentLoadOp::Load, AttachmentLoadOp::Load)
            ],
            "each produced external role must initialize color/depth before loading them"
        );
        gal.create_command_list(crate::render::vulkanic::commands::CommandListDesc {
            label: "initialized-external-handoff".into(),
            operations: ops,
        })
        .unwrap();
        gal.destroy(pass).unwrap();
    }
    gal.destroy(capture_source).unwrap();
    gal.destroy(capture_depth).unwrap();
    gal.destroy(translucent_depth).unwrap();
    set.destroy(&mut gal);
}
