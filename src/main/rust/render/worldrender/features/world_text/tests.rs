use crate::render::worldrender::features::world_text::*;
use crate::render::vulkanic::test_support::{MockBackend, vulkan_capabilities};
use crate::render::vulkanic::resources::{FrameTargetDesc, RenderPassDesc};
use crate::render::vulkanic::{CommandList, CommandListDesc, SubmissionBatch};

fn asset() -> WorldTextImageAsset {
    WorldTextImageAsset {
        asset_id: 7,
        atlas_generation: 3,
        atlas_revision: 4,
        format: WorldTextImageFormat::Alpha8,
        width: 2,
        height: 2,
        pixels: vec![0, 64, 128, 255],
    }
}

fn quad(depth_policy: u32) -> WorldTextQuadRequest {
    WorldTextQuadRequest {
        asset_id: 7,
        atlas_generation: 3,
        atlas_revision: 4,
        colored: false,
        depth_policy,
        packed_light: 0,
        block_entity_id: -1,
        distance_to_camera_sq: 4.0,
        model_view_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        positions: [
            [0.0, 0.0, 0.0],
            [0.0, 8.0, 0.0],
            [8.0, 8.0, 0.0],
            [8.0, 0.0, 0.0],
        ],
        uvs: [[0.0, 0.0], [0.0, 1.0], [1.0, 1.0], [1.0, 0.0]],
        color_argb: 0xffff_ffff,
    }
}

#[test]
fn rejects_stale_or_malformed_image_generations() {
    let mut frontend = WorldTextFrontend::default();
    frontend.apply_image_update(1, vec![asset()]).unwrap();
    assert!(frontend.apply_image_update(1, vec![asset()]).is_err());
    let mut malformed = asset();
    malformed.pixels.pop();
    assert!(frontend.apply_image_update(2, vec![malformed]).is_err());
    assert_eq!(1, frontend.asset_generation);
}

#[test]
fn rejects_excessive_world_text_image_count_before_copying_assets() {
    let mut frontend = WorldTextFrontend::default();
    let assets = (0..=MAX_WORLD_TEXT_IMAGES)
        .map(|index| WorldTextImageAsset {
            asset_id: index as u64 + 1,
            ..asset()
        })
        .collect();
    let error = frontend.apply_image_update(1, assets).unwrap_err();
    assert!(error.to_string().contains("image asset count"));
    assert_eq!(0, frontend.asset_generation);
}

#[test]
fn rejects_excessive_world_text_image_bytes_before_installing_generation() {
    let mut frontend = WorldTextFrontend::default();
    let bytes_per_asset = 1024 * 1024;
    let assets = (0..65)
        .map(|index| WorldTextImageAsset {
            asset_id: index as u64 + 1,
            width: 1024,
            height: 1024,
            pixels: vec![0; bytes_per_asset],
            ..asset()
        })
        .collect();
    let error = frontend.apply_image_update(1, assets).unwrap_err();
    assert!(error.to_string().contains("image assets use"));
    assert_eq!(0, frontend.asset_generation);
}

#[test]
fn vertex_shader_preserves_semantic_left_to_right_glyph_uvs() {
    let shader = std::str::from_utf8(WORLD_TEXT_VERTEX_SHADER).unwrap();
    assert!(shader.contains("vec2 uv_top = mix(quad.uv01.xy, quad.uv23.zw, corner.x);"));
    assert!(shader.contains("vec2 uv_bottom = mix(quad.uv01.zw, quad.uv23.xy, corner.x);"));
    assert!(!shader.contains("mix(quad.uv23.zw, quad.uv01.xy, corner.x)"));
}

#[test]
fn preserves_depth_and_submission_order_when_batching() {
    let mut frontend = WorldTextFrontend::default();
    frontend.apply_image_update(1, vec![asset()]).unwrap();
    let frame = WorldTextFrame {
        quads: vec![
            quad(WORLD_TEXT_DEPTH_SEE_THROUGH),
            quad(WORLD_TEXT_DEPTH_SEE_THROUGH),
            quad(WORLD_TEXT_DEPTH_NORMAL),
            quad(WORLD_TEXT_DEPTH_POLYGON_OFFSET),
        ],
    };
    let batches = frontend.prepare_frame(&frame).unwrap();
    assert_eq!(3, batches.len());
    assert_eq!(2, batches[0].count);
    assert_eq!(WORLD_TEXT_DEPTH_NORMAL, batches[1].depth_policy);
    assert_eq!(WORLD_TEXT_DEPTH_POLYGON_OFFSET, batches[2].depth_policy);
}

#[test]
fn preserves_packed_light_boundaries_when_batching() {
    let mut frontend = WorldTextFrontend::default();
    frontend.apply_image_update(1, vec![asset()]).unwrap();
    let mut lit = quad(WORLD_TEXT_DEPTH_NORMAL);
    lit.packed_light = 0x00f0_00f0;
    let mut dim = lit.clone();
    dim.packed_light = 0x0010_0010;
    let batches = frontend
        .prepare_frame(&WorldTextFrame {
            quads: vec![lit, dim],
        })
        .unwrap();
    assert_eq!(2, batches.len());
    assert_eq!(0x00f0_00f0, batches[0].packed_light);
    assert_eq!(0x0010_0010, batches[1].packed_light);
}

#[test]
fn preserves_block_entity_boundaries_when_batching() {
    let mut frontend = WorldTextFrontend::default();
    frontend.apply_image_update(1, vec![asset()]).unwrap();
    let mut first = quad(WORLD_TEXT_DEPTH_NORMAL);
    first.block_entity_id = 11;
    let mut second = first.clone();
    second.block_entity_id = 12;
    let batches = frontend
        .prepare_frame(&WorldTextFrame {
            quads: vec![first, second],
        })
        .unwrap();
    assert_eq!(2, batches.len());
    assert_eq!(11, batches[0].block_entity_id);
    assert_eq!(12, batches[1].block_entity_id);
}

#[test]
fn rejects_stale_atlas_references_before_draw_planning() {
    let mut frontend = WorldTextFrontend::default();
    frontend.apply_image_update(1, vec![asset()]).unwrap();
    let mut stale = quad(WORLD_TEXT_DEPTH_NORMAL);
    stale.atlas_revision = 5;
    assert!(frontend
        .prepare_frame(&WorldTextFrame { quads: vec![stale] })
        .is_err());
}

#[test]
fn packs_the_global_view_matrix_between_projection_and_text_quads() {
    let mut view = [0.0; 16];
    view[0] = 1.0;
    view[5] = 1.0;
    view[10] = 1.0;
    view[12] = 7.0;
    view[15] = 1.0;
    let mut projection = [0.0; 16];
    projection[0] = 2.0;
    projection[5] = 3.0;
    projection[10] = 4.0;
    projection[15] = 1.0;
    let packed = packed_uniforms(
        view,
        projection,
        WorldTextImageFormat::Alpha8,
        &[quad(WORLD_TEXT_DEPTH_NORMAL)],
    )
    .unwrap();
    assert_eq!(
        WORLD_TEXT_HEADER_BYTES + WORLD_TEXT_QUAD_BYTES,
        packed.len()
    );
    let f32_at =
        |offset: usize| f32::from_le_bytes(packed[offset..offset + 4].try_into().unwrap());
    assert_eq!(2.0, f32_at(0));
    assert_eq!(7.0, f32_at(64 + 12 * 4));
    assert_eq!(0.0, f32_at(128));
}

#[test]
fn samples_the_copied_font_region_with_the_readable_local_u_orientation() {
    let shader = std::str::from_utf8(WORLD_TEXT_VERTEX_SHADER).unwrap();
    assert!(shader.contains("mix(quad.uv01.xy, quad.uv23.zw, corner.x)"));
    assert!(shader.contains("mix(quad.uv01.zw, quad.uv23.xy, corner.x)"));
}

#[test]
fn classifies_projected_glyph_bounds_without_backend_state() {
    let identity = [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ];
    let mut visible = quad(WORLD_TEXT_DEPTH_SEE_THROUGH);
    visible.positions = [
        [-0.5, 0.5, 0.0],
        [-0.5, -0.5, 0.0],
        [0.5, -0.5, 0.0],
        [0.5, 0.5, 0.0],
    ];
    assert_eq!(
        Some([-0.5, -0.5, 0.5, 0.5]),
        quad_ndc_bounds(identity, identity, &visible),
    );
    assert!(ndc_bounds_intersect_viewport(
        quad_ndc_bounds(identity, identity, &visible).unwrap()
    ));

    visible.model_view_matrix[12] = 3.0;
    assert!(!ndc_bounds_intersect_viewport(
        quad_ndc_bounds(identity, identity, &visible).unwrap()
    ));
}

#[test]
fn name_tag_projection_includes_the_copied_global_view() {
    let identity = [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ];
    let mut global_view = identity;
    global_view[12] = 8.0;
    let mut glyph = quad(WORLD_TEXT_DEPTH_NORMAL);
    glyph.positions = [
        [-0.5, 0.5, 0.0],
        [-0.5, -0.5, 0.0],
        [0.5, -0.5, 0.0],
        [0.5, 0.5, 0.0],
    ];
    assert_ne!(
        quad_ndc_bounds(identity, identity, &glyph),
        quad_ndc_bounds(global_view, identity, &glyph),
        "the semantic name-tag pose remains relative to the copied frame view",
    );
}

#[test]
fn atlas_upload_residency_commits_only_on_submission_confirmation() {
    let key = WorldTextResourceKey {
        raster_y_direction: RasterYDirection::Up,
        asset_id: 7,
        atlas_generation: 2,
        atlas_revision: 3,
        color_format: ColorFormat::Bgra8Unorm,
    };
    let pending = WorldTextResources {
        upload_buffer: Handle::NULL,
        uniform_buffer: Handle::NULL,
        texture: Handle::NULL,
        sampler: Handle::NULL,
        vertex_shader: Handle::NULL,
        fragment_shader: Handle::NULL,
        texture_view: Handle::NULL,
        resource_layout: Handle::NULL,
        resource_set: Handle::NULL,
        pipeline_layout: Handle::NULL,
        pipeline_depth_disabled: Handle::NULL,
        pipeline_depth_test_no_write: Handle::NULL,
        pipeline_depth_test_polygon_offset: Handle::NULL,
        uploaded: false,
        upload_pending: true,
    };
    let mut frontend = WorldTextFrontend::default();
    frontend.resources.insert(key, pending);
    frontend.pending_upload_keys.insert(key);

    frontend.cancel_submission();
    let resources = frontend.resources.get(&key).unwrap();
    assert!(!resources.uploaded);
    assert!(!resources.upload_pending);

    frontend.resources.get_mut(&key).unwrap().upload_pending = true;
    frontend.pending_upload_keys.insert(key);
    frontend.confirm_submission();
    let resources = frontend.resources.get(&key).unwrap();
    assert!(resources.uploaded);
    assert!(!resources.upload_pending);

    // A second semantic producer may append an empty text slice after the
    // first producer has staged an upload. That append must not reopen the
    // transaction and clear the pending marker before confirmation.
    assert!(frontend.resources.get(&key).unwrap().uploaded);
    frontend.resources.get_mut(&key).unwrap().uploaded = false;
    frontend.resources.get_mut(&key).unwrap().upload_pending = true;
    frontend.upload_submission_active = true;
    let mut gal = crate::render::worldrender::tests::gal();
    let mut ops = Vec::new();
    frontend
        .append_frame_ops(
            &mut gal,
            Handle::NULL,
            Handle::NULL,
            Handle::NULL,
            Handle::NULL,
            Handle::NULL,
            TextureUsageState::Undefined,
            ColorFormat::Bgra8Unorm,
            RasterYDirection::Up,
            [0.0; 16],
            [0.0; 16],
            &[],
            &mut ops,
            false,
        )
        .unwrap();
    let resources = frontend.resources.get(&key).unwrap();
    assert!(resources.upload_pending);
    assert!(!resources.uploaded);
}

#[test]
fn begin_submission_discards_previous_producer_markers() {
    let key = WorldTextResourceKey {
        raster_y_direction: RasterYDirection::Up,
        asset_id: 7,
        atlas_generation: 2,
        atlas_revision: 3,
        color_format: ColorFormat::Bgra8Unorm,
    };
    let pending = WorldTextResources {
        upload_buffer: Handle::NULL,
        uniform_buffer: Handle::NULL,
        texture: Handle::NULL,
        sampler: Handle::NULL,
        vertex_shader: Handle::NULL,
        fragment_shader: Handle::NULL,
        texture_view: Handle::NULL,
        resource_layout: Handle::NULL,
        resource_set: Handle::NULL,
        pipeline_layout: Handle::NULL,
        pipeline_depth_disabled: Handle::NULL,
        pipeline_depth_test_no_write: Handle::NULL,
        pipeline_depth_test_polygon_offset: Handle::NULL,
        uploaded: false,
        upload_pending: true,
    };
    let mut frontend = WorldTextFrontend::default();
    frontend.resources.insert(key, pending);
    frontend.pending_upload_keys.insert(key);
    frontend.upload_submission_active = true;

    frontend.begin_submission();

    let resources = frontend.resources.get(&key).unwrap();
    assert!(!resources.uploaded);
    assert!(!resources.upload_pending);
    assert!(frontend.upload_submission_active);
    assert!(frontend.pending_upload_keys.is_empty());
}

#[test]
fn appends_owned_alpha_text_draws_with_explicit_depth_modes() {
    let mut capabilities = vulkan_capabilities();
    capabilities.features.presentation = true;
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(capabilities);
    let target = gal
        .create_frame_target(FrameTargetDesc {
            label: "world-text-test-target".to_string(),
            frame_id: 1,
            render_target: crate::render::vulkanic::frame::FrameRenderTargetId(1),
            extent: Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            color_format: ColorFormat::Bgra8Unorm,
        })
        .unwrap();
    let depth_texture = gal
        .create_texture(TextureDesc {
            label: "world-text-test-depth".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent: Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::DepthStencilAttachment],
        })
        .unwrap();
    let depth_view = gal
        .create_texture_view(TextureViewDesc {
            label: "world-text-test-depth-view".to_string(),
            texture: depth_texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let pass = gal
        .create_render_pass(RenderPassDesc {
            label: "world-text-test-pass".to_string(),
            target,
            color_formats: vec![ColorFormat::Bgra8Unorm],
            depth_format: Some(TextureFormat::Depth32Float),
        })
        .unwrap();
    let mut frontend = WorldTextFrontend::default();
    frontend.apply_image_update(1, vec![asset()]).unwrap();
    let mut ops = Vec::new();
    frontend
        .append_frame_ops(
            &mut gal,
            target,
            pass,
            target,
            depth_texture,
            depth_view,
            TextureUsageState::DepthStencilAttachment,
            ColorFormat::Bgra8Unorm,
            RasterYDirection::Up,
            [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            &[
                quad(WORLD_TEXT_DEPTH_SEE_THROUGH),
                quad(WORLD_TEXT_DEPTH_NORMAL),
                quad(WORLD_TEXT_DEPTH_POLYGON_OFFSET),
            ],
            &mut ops,
            true,
        )
        .unwrap();

    assert_eq!(1, frontend.resources.len());
    assert_eq!(
        3,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::Draw {
                    vertices: 6,
                    instances: 1
                }
            ))
            .count()
    );
    assert_eq!(
        3,
        ops.iter()
            .filter(|op| matches!(op, CommandOp::BindGraphicsPipeline(_)))
            .count()
    );
    // `append_frame_ops` only stages residency; even the legacy
    // `mark_uploaded` argument must not commit before the GAL accepts the
    // complete enclosing submission.
    assert!(!frontend.resources.values().next().unwrap().uploaded);
    frontend.confirm_submission();
    let resources = frontend.resources.values().next().unwrap();
    assert!(
        resources.uploaded,
        "admitted text execution must commit atlas upload state"
    );
    let bound_pipelines = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::BindGraphicsPipeline(handle) => Some(*handle),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![
            resources.pipeline_depth_disabled,
            resources.pipeline_depth_test_no_write,
            resources.pipeline_depth_test_polygon_offset,
        ],
        bound_pipelines
    );
    assert!(
        !ops.iter().any(|op| matches!(
            op,
            CommandOp::Barrier(barrier)
                if barrier.resource == depth_texture
                    && barrier.before == TextureUsageState::ShaderRead
                    && barrier.after == TextureUsageState::DepthStencilAttachment
        )),
        "direct world text retains the already-attached depth state"
    );
    let up_pipelines = bound_pipelines;
    let mut down_ops = Vec::new();
    frontend
        .append_frame_ops(
            &mut gal,
            target,
            pass,
            target,
            depth_texture,
            depth_view,
            TextureUsageState::DepthStencilAttachment,
            ColorFormat::Bgra8Unorm,
            RasterYDirection::Down,
            crate::render::worldrender::matrix4_identity(),
            crate::render::worldrender::matrix4_identity(),
            &[
                quad(WORLD_TEXT_DEPTH_SEE_THROUGH),
                quad(WORLD_TEXT_DEPTH_NORMAL),
                quad(WORLD_TEXT_DEPTH_POLYGON_OFFSET),
            ],
            &mut down_ops,
            false,
        )
        .unwrap();
    assert_eq!(
        frontend.resources.len(),
        2,
        "the same atlas must not alias opposite target orientations"
    );
    let down_pipelines = down_ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::BindGraphicsPipeline(handle) => Some(*handle),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(down_pipelines.len(), 3);
    for (direction, pipelines) in [
        (RasterYDirection::Up, up_pipelines),
        (RasterYDirection::Down, down_pipelines),
    ] {
        for pipeline in pipelines {
            assert_eq!(
                gal.graphics_pipeline_descriptor_for_test(pipeline)
                    .unwrap()
                    .raster_y_direction,
                direction
            );
        }
    }
    frontend.reset(&mut gal);
    for handle in [pass, depth_view, depth_texture, target] {
        gal.destroy(handle).unwrap();
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn transitions_shader_read_depth_before_depth_aware_text() {
    let mut capabilities = vulkan_capabilities();
    capabilities.features.presentation = true;
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(capabilities);
    let target = gal
        .create_frame_target(FrameTargetDesc {
            label: "world-text-transition-target".to_string(),
            frame_id: 1,
            render_target: crate::render::vulkanic::frame::FrameRenderTargetId(1),
            extent: Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            color_format: ColorFormat::Bgra8Unorm,
        })
        .unwrap();
    let depth_texture = gal
        .create_texture(TextureDesc {
            label: "world-text-transition-depth".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent: Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::DepthStencilAttachment, TextureUsage::Sampled],
        })
        .unwrap();
    let depth_view = gal
        .create_texture_view(TextureViewDesc {
            label: "world-text-transition-depth-view".to_string(),
            texture: depth_texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let pass = gal
        .create_render_pass(RenderPassDesc {
            label: "world-text-transition-pass".to_string(),
            target,
            color_formats: vec![ColorFormat::Bgra8Unorm],
            depth_format: Some(TextureFormat::Depth32Float),
        })
        .unwrap();
    let mut frontend = WorldTextFrontend::default();
    frontend.apply_image_update(1, vec![asset()]).unwrap();
    let mut ops = Vec::new();
    frontend
        .append_frame_ops(
            &mut gal,
            target,
            pass,
            target,
            depth_texture,
            depth_view,
            TextureUsageState::ShaderRead,
            ColorFormat::Bgra8Unorm,
            RasterYDirection::Up,
            [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            &[quad(WORLD_TEXT_DEPTH_NORMAL)],
            &mut ops,
            true,
        )
        .unwrap();

    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::Barrier(barrier)
            if barrier.resource == depth_texture
                && barrier.before == TextureUsageState::ShaderRead
                && barrier.after == TextureUsageState::DepthStencilAttachment
    )));

    gal.submit(SubmissionBatch {
        label: "world-text-transition-submission".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-text-transition-commands".to_string(),
            operations: {
                let mut operations = vec![CommandOp::Barrier(texture_barrier(
                    depth_texture,
                    TextureUsageState::Undefined,
                    TextureUsageState::ShaderRead,
                ))];
                operations.extend(ops);
                operations
            },
        })],
    })
    .expect("the explicit depth transition resolves the sampled-to-attachment hazard");
}
