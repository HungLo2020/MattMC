use crate::render::worldrender::features::outline::*;
use crate::render::vulkanic::handles::{Handle, HandleKind};

fn frame_with_instances(instances: Vec<WorldMeshInstanceRequest>) -> WorldPrimitiveFrame {
    WorldPrimitiveFrame {
        engine_globals: None,
        frame_id: 1,
        correlation_id: 1,
        viewport_width: 128,
        viewport_height: 128,
        view_matrix: [0.0; 16],
        projection_matrix: [0.0; 16],
        voxel_volume: WorldVoxelVolumeFrame::default(),
        shader_environment: WorldShaderEnvironmentFrame::default(),
        feature_coverage: WorldFeatureCoverageFrame::default(),
        first_person: WorldFirstPersonFrame::default(),
        first_person_mesh_instances: Vec::new(),
        background: WorldBackgroundRequest::default(),
        segments: Vec::new(),
        crack_quads: Vec::new(),
        border_quads: Vec::new(),
        material_quads: Vec::new(),
        dh_generic_boxes: Vec::new(),
        mesh_instances: instances,
        text_quads: Vec::new(),
        lod_instances: Vec::new(),
        lod_render_frame: WorldLodRenderFrame::default(),
    }
}

#[test]
fn entity_outline_plan_copies_only_outlined_entity_instances() {
    let plain = test_mesh_instance(1);
    let mut outlined = test_mesh_instance(2);
    outlined.outline_color_argb = 0xff_12_34_56;
    let plan = plan_entity_outline_mask(&frame_with_instances(vec![plain, outlined]))
        .unwrap()
        .unwrap();
    assert_eq!(128, plan.viewport_width);
    assert_eq!(1, plan.instances.len());
    assert_eq!(2, plan.instances[0].mesh_key);
    assert_eq!(0xff_12_34_56, plan.instances[0].color_argb);
}

#[test]
fn entity_outline_plan_retains_outline_only_instances_for_mask_execution() {
    let mut outline_only = test_mesh_instance(7);
    outline_only.flags = 1;
    outline_only.outline_color_argb = 0xff_33_66_cc;

    let plan = plan_entity_outline_mask(&frame_with_instances(vec![outline_only]))
        .unwrap()
        .expect("outline-only semantic work must remain available to the mask planner");

    assert_eq!(1, plan.instances.len());
    assert_eq!(7, plan.instances[0].mesh_key);
    assert_eq!(0xff_33_66_cc, plan.instances[0].color_argb);
}

#[test]
fn entity_outline_plan_rejects_non_entity_strata() {
    let mut instance = test_mesh_instance(3);
    instance.stratum = WORLD_STRATUM_TERRAIN;
    instance.outline_color_argb = 0xff_ff_ff_ff;
    let error = plan_entity_outline_mask(&frame_with_instances(vec![instance])).unwrap_err();
    assert!(error.to_string().contains("entity mesh stratum"));
}

#[test]
fn entity_outline_instances_pack_color_and_preserve_mesh_instance_abi() {
    let mut instance = test_mesh_instance(9);
    instance.outline_color_argb = 0xff_12_34_56;
    let plan = plan_entity_outline_mask(&frame_with_instances(vec![instance]))
        .unwrap()
        .unwrap();
    let bytes = pack_entity_outline_instances(&plan.instances, false).unwrap();
    assert_eq!(crate::render::worldrender::WORLD_MESH_INSTANCE_BYTES, bytes.len());
    let color = &bytes[16 * 4..20 * 4];
    assert_eq!(
        f32::from_le_bytes(color[0..4].try_into().unwrap()),
        0x12 as f32 / 255.0
    );
    assert_eq!(
        f32::from_le_bytes(color[3 * 4..4 * 4].try_into().unwrap()),
        1.0
    );
    assert_eq!(plan.instances[0].depth_policy, WORLD_DEPTH_POLICY_DISABLED);
    let atlas_bytes = pack_entity_outline_instances(&plan.instances, true).unwrap();
    assert_eq!(
        f32::from_le_bytes(atlas_bytes[92..96].try_into().unwrap()),
        1.0
    );
}

#[test]
fn entity_outline_post_effect_plan_binds_mask_to_the_bundled_four_pass_chain() {
    let mut instance = test_mesh_instance(4);
    instance.outline_color_argb = 0xff_ff_00_00;
    let plan = prepare_entity_outline_post_effect(&frame_with_instances(vec![instance]))
        .unwrap()
        .unwrap();
    assert_eq!(1, plan.mask.instances.len());
    assert_eq!(4, plan.effect.ordered_passes.len());
    assert_eq!(4, plan.shader_sources.len());
    assert!(plan.shader_sources.iter().all(|source| {
        !source.vertex_shader.is_empty() && !source.fragment_shader.is_empty()
    }));
    assert_eq!("minecraft:entity_outline", plan.effect.effect_name);
    assert_eq!(
        "minecraft:entity_outline",
        plan.effect.ordered_passes[0].inputs[0].target
    );
    assert_eq!(
        "minecraft:entity_outline",
        plan.effect.ordered_passes[3].output
    );
}

#[test]
fn entity_outline_geometry_resolution_expands_all_sections_from_owned_asset() {
    let mut instance = test_mesh_instance(19);
    instance.mesh_section_index = WORLD_MESH_SECTION_ALL;
    instance.outline_color_argb = 0xff_ff_00_00;
    let frame = frame_with_instances(vec![instance]);
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.mesh_assets.insert(
        19,
        crate::render::worldrender::features::outline::MeshAssetStore {
            translucent_order: Default::default(),
            section_ranges_cache: Default::default(),
            texture_animation_signature_cache: Default::default(),
            mesh_generation: 1,
            index_generation: 1,
            vertex_layout_version: 0,
            vertex_bytes: Vec::new(),
            decal_normals: None,
            source_input: None,
            entity_identity: String::new(),
            terrain_voxel_vertices: None,
            terrain_voxel_indices: None,
            terrain_voxel_translucent_indices: None,
            terrain_voxel_model_bounds: None,
            index_bytes: Vec::new(),
            index_type: IndexType::U16,
            sections: vec![
                WorldMeshSection {
                    material_id: 1,
                    texture_id: 1,
                    material_mode: WORLD_MATERIAL_MODE_OPAQUE,
                    cull_policy: WORLD_CULL_BACK,
                    winding: WORLD_WINDING_CCW,
                    index_offset: 0,
                    index_count: 6,
                    source_facing: 6,
                },
                WorldMeshSection {
                    material_id: 2,
                    texture_id: 2,
                    material_mode: WORLD_MATERIAL_MODE_CUTOUT,
                    cull_policy: WORLD_CULL_NONE,
                    winding: WORLD_WINDING_CW,
                    index_offset: 12,
                    index_count: 6,
                    source_facing: 6,
                },
            ],
        },
    );
    let draws = resolve_entity_outline_mask_draws(&frontend, &frame)
        .unwrap()
        .unwrap();
    assert_eq!(2, draws.len());
    assert_eq!(
        vec![0, 1],
        draws
            .iter()
            .map(|draw| draw.section_index)
            .collect::<Vec<_>>()
    );
    assert!(draws.iter().all(|draw| draw.instances.len() == 1));
    let (stream, offsets) = pack_entity_outline_instance_stream(&frame, &draws).unwrap();
    assert_eq!(vec![0, 512], offsets);
    assert_eq!(
        512 + crate::render::worldrender::features::outline::WORLD_MESH_BATCH_HEADER_BYTES + crate::render::worldrender::features::outline::WORLD_MESH_INSTANCE_BYTES,
        stream.len()
    );
}

#[test]
fn entity_outline_lowering_requires_private_bindings_for_every_pass() {
    let mut instance = test_mesh_instance(5);
    instance.outline_color_argb = 0xff_00_ff_00;
    let plan = prepare_entity_outline_post_effect(&frame_with_instances(vec![instance]))
        .unwrap()
        .unwrap();
    let handle = |kind: HandleKind, index: u32| Handle::new(kind, index, 1).unwrap();
    let bindings = plan
        .effect
        .ordered_passes
        .iter()
        .enumerate()
        .map(|(index, pass)| VanillaPostEffectPassBinding {
            render_pass: handle(HandleKind::RenderPass, index as u32 + 1),
            render_target: handle(HandleKind::RenderTarget, index as u32 + 1),
            color_attachment: handle(HandleKind::TextureView, index as u32 + 1),
            depth_attachment: None,
            pipeline: handle(HandleKind::GraphicsPipeline, index as u32 + 1),
            pipeline_layout: handle(HandleKind::PipelineLayout, index as u32 + 1),
            resource_set: handle(HandleKind::ResourceSet, index as u32 + 1),
            inputs: pass
                .inputs
                .iter()
                .map(|input| VanillaPostEffectInputBinding {
                    texture_view: handle(HandleKind::TextureView, index as u32 + 10),
                    sampler: handle(HandleKind::Sampler, index as u32 + 10),
                    bilinear: input.bilinear,
                    use_depth_buffer: input.use_depth_buffer,
                })
                .collect(),
            uniform_values: pass.uniform_values.clone(),
        })
        .collect::<Vec<_>>();
    let operations = lower_entity_outline_post_effect(&plan, &bindings).unwrap();
    assert_eq!(20, operations.len());
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::Draw {
            vertices: 3,
            instances: 1
        }
    )));
}

#[test]
fn entity_outline_target_resources_reject_zero_extent_before_allocation() {
    let mut gal = crate::render::worldrender::tests::gal();
    let error =
        create_entity_outline_target_resources(&mut gal, 0, 128, TextureFormat::Rgba8Unorm)
            .unwrap_err();
    assert!(error.to_string().contains("non-zero extent"));
}

#[test]
fn entity_outline_mask_target_binds_explicit_depth_view_without_owning_it() {
    let mut gal = crate::render::worldrender::tests::gal();
    let depth = gal
        .create_texture(TextureDesc {
            label: "outline-test-depth".to_string(),
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
            label: "outline-test-depth-view".to_string(),
            texture: depth,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let resources = create_entity_outline_target_resources_with_depth(
        &mut gal,
        64,
        64,
        TextureFormat::Rgba8Unorm,
        Some(depth_view),
    )
    .unwrap();
    assert_eq!(Some(depth_view), resources.mask_depth_view);
    assert!(resources
        .handles_in_destroy_order()
        .iter()
        .all(|handle| { *handle != depth && *handle != depth_view }));
    for handle in resources.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    gal.destroy(depth_view).unwrap();
    gal.destroy(depth).unwrap();
}

#[test]
fn entity_outline_mask_lowering_emits_depth_tested_indexed_pass_and_restores_state() {
    let handle = |kind: HandleKind, index: u32| Handle::new(kind, index, 1).unwrap();
    let targets = EntityOutlineTargetResources {
        width: 64,
        height: 64,
        color_format: TextureFormat::Rgba8Unorm,
        mask_depth_view: Some(handle(HandleKind::TextureView, 20)),
        sampler: handle(HandleKind::Sampler, 13),
        mask_texture: handle(HandleKind::Texture, 1),
        mask_view: handle(HandleKind::TextureView, 2),
        mask_target: handle(HandleKind::RenderTarget, 3),
        mask_pass: handle(HandleKind::RenderPass, 4),
        swap_texture: handle(HandleKind::Texture, 5),
        swap_view: handle(HandleKind::TextureView, 6),
        swap_target: handle(HandleKind::RenderTarget, 7),
        swap_pass: handle(HandleKind::RenderPass, 8),
        outline_texture: handle(HandleKind::Texture, 9),
        outline_view: handle(HandleKind::TextureView, 10),
        outline_target: handle(HandleKind::RenderTarget, 11),
        outline_pass: handle(HandleKind::RenderPass, 12),
    };
    let gpu = EntityOutlineMaskGpuResources {
        instance_buffer: handle(HandleKind::Buffer, 30),
        instance_bytes: vec![0; 336],
        draws: vec![EntityOutlineMaskGpuDraw {
            mesh_key: 1,
            mesh_generation: 1,
            section_index: 0,
            index_buffer: handle(HandleKind::Buffer, 31),
            index_offset: 0,
            vertex_offset: 0,
            index_count: 6,
            index_type: IndexType::U16,
            pipeline: handle(HandleKind::GraphicsPipeline, 32),
            pipeline_layout: handle(HandleKind::PipelineLayout, 33),
            resource_set: handle(HandleKind::ResourceSet, 34),
            dynamic_offset: 0,
            instance_count: 1,
        }],
    };
    let depth_view = targets.mask_depth_view.unwrap();
    let operations = lower_entity_outline_mask_pass(
        &targets,
        &gpu,
        depth_view,
        TextureUsageState::ShaderRead,
        TextureUsageState::ShaderRead,
        TextureUsageState::ShaderRead,
    )
    .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::BeginPass {
            depth_stencil: Some(PassAttachment { view, .. }), ..
        } if *view == depth_view
    )));
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::DrawIndexed {
            indices: 6,
            instances: 1
        }
    )));
    assert!(
        matches!(operations.last(), Some(CommandOp::Barrier(barrier))
        if barrier.resource == depth_view
            && barrier.after == TextureUsageState::ShaderRead)
    );
}

#[test]
fn entity_outline_target_resources_are_cached_and_reset_owned() {
    let mut gal = crate::render::worldrender::tests::gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let first = frontend
        .ensure_entity_outline_target_resources(&mut gal, 64, 64, TextureFormat::Rgba8Unorm)
        .unwrap()
        .clone();
    let second = frontend
        .ensure_entity_outline_target_resources(&mut gal, 64, 64, TextureFormat::Rgba8Unorm)
        .unwrap()
        .clone();
    assert_eq!(first, second);
    frontend.reset(&mut gal);
    assert!(frontend.entity_outline_targets.is_none());
}

#[test]
fn entity_outline_mask_transitions_are_explicit_and_persistent_state_aware() {
    let handle = |kind: HandleKind, index: u32| Handle::new(kind, index, 1).unwrap();
    let resources = EntityOutlineTargetResources {
        width: 64,
        height: 64,
        color_format: TextureFormat::Rgba8Unorm,
        mask_depth_view: None,
        sampler: handle(HandleKind::Sampler, 13),
        mask_texture: handle(HandleKind::Texture, 1),
        mask_view: handle(HandleKind::TextureView, 2),
        mask_target: handle(HandleKind::RenderTarget, 3),
        mask_pass: handle(HandleKind::RenderPass, 4),
        swap_texture: handle(HandleKind::Texture, 5),
        swap_view: handle(HandleKind::TextureView, 6),
        swap_target: handle(HandleKind::RenderTarget, 7),
        swap_pass: handle(HandleKind::RenderPass, 8),
        outline_texture: handle(HandleKind::Texture, 9),
        outline_view: handle(HandleKind::TextureView, 10),
        outline_target: handle(HandleKind::RenderTarget, 11),
        outline_pass: handle(HandleKind::RenderPass, 12),
    };
    let operations =
        entity_outline_mask_transition_ops(&resources, TextureUsageState::ShaderRead);
    assert!(matches!(operations[0], CommandOp::Barrier(ref barrier)
        if barrier.resource == resources.mask_texture
            && barrier.before == TextureUsageState::ShaderRead
            && barrier.after == TextureUsageState::ColorAttachment));
    assert!(matches!(operations[1], CommandOp::Barrier(ref barrier)
        if barrier.before == TextureUsageState::ColorAttachment
            && barrier.after == TextureUsageState::ShaderRead));
}

#[test]
fn entity_outline_pipeline_uses_dedicated_solid_color_program() {
    let mut gal = crate::render::worldrender::tests::gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let key = frontend
        .ensure_entity_outline_pipeline_resources(
            &mut gal,
            ColorFormat::Rgba8Unorm,
            WORLD_WINDING_CCW,
            WORLD_DEPTH_POLICY_TEST_WRITE,
            WORLD_CULL_BACK,
            RasterYDirection::Up,
        )
        .unwrap();
    assert_eq!(
        "vulkanic:builtin/entity_outline_mask_v1",
        key.shader_program_identity.as_str()
    );
    let resources = frontend.mesh_pipeline_resources.get(&key).unwrap();
    assert!(resources.shadow_pipeline.is_none());
    frontend.reset(&mut gal);
    assert!(frontend.mesh_pipeline_resources.is_empty());
}

#[test]
fn entity_outline_post_effect_pipelines_are_rust_owned_and_destroyable() {
    let mut gal = crate::render::worldrender::tests::gal();
    let resources =
        create_entity_outline_post_effect_pipelines(&mut gal, TextureFormat::Rgba8Unorm)
            .unwrap();
    assert_ne!(Handle::NULL, resources.sobel_pipeline);
    assert_ne!(Handle::NULL, resources.blur_pipeline);
    assert_ne!(Handle::NULL, resources.blit_pipeline);
    for handle in resources.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
}

#[test]
fn entity_outline_post_effect_resource_sets_bind_owned_targets_and_uniforms() {
    let mut gal = crate::render::worldrender::tests::gal();
    let targets =
        create_entity_outline_target_resources(&mut gal, 64, 64, TextureFormat::Rgba8Unorm)
            .unwrap();
    let pipelines =
        create_entity_outline_post_effect_pipelines(&mut gal, TextureFormat::Rgba8Unorm)
            .unwrap();
    let sets = create_entity_outline_post_effect_resource_sets(&mut gal, &targets, &pipelines)
        .unwrap();
    assert_eq!(4, sets.resource_sets.len());
    assert_eq!(3, sets.uniform_buffers.len());
    for handle in sets.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    for handle in pipelines.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    for handle in targets.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
}

#[test]
fn entity_outline_frontend_caches_and_retires_post_effect_sets() {
    let mut gal = crate::render::worldrender::tests::gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend
        .ensure_entity_outline_target_resources(&mut gal, 64, 64, TextureFormat::Rgba8Unorm)
        .unwrap();
    frontend
        .ensure_entity_outline_post_effect_pipelines(&mut gal, TextureFormat::Rgba8Unorm)
        .unwrap();
    let first = frontend
        .ensure_entity_outline_post_effect_resource_sets(&mut gal, TextureFormat::Rgba8Unorm)
        .unwrap()
        .clone();
    let second = frontend
        .ensure_entity_outline_post_effect_resource_sets(&mut gal, TextureFormat::Rgba8Unorm)
        .unwrap()
        .clone();
    assert_eq!(first, second);
    frontend.reset(&mut gal);
    assert!(frontend.entity_outline_post_effect_sets.is_none());
}

#[test]
fn entity_outline_filter_bindings_preserve_the_four_owned_intermediate_passes() {
    let mut gal = crate::render::worldrender::tests::gal();
    let targets =
        create_entity_outline_target_resources(&mut gal, 64, 64, TextureFormat::Rgba8Unorm)
            .unwrap();
    let pipelines =
        create_entity_outline_post_effect_pipelines(&mut gal, TextureFormat::Rgba8Unorm)
            .unwrap();
    let sets = create_entity_outline_post_effect_resource_sets(&mut gal, &targets, &pipelines)
        .unwrap();
    let mut instance = test_mesh_instance(44);
    instance.outline_color_argb = 0xff_ff_ff_ff;
    let plan = prepare_entity_outline_post_effect(&frame_with_instances(vec![instance]))
        .unwrap()
        .unwrap();
    let bindings =
        bind_entity_outline_post_effect_passes(&plan, &targets, &pipelines, &sets).unwrap();
    assert_eq!(4, bindings.len());
    assert_eq!(targets.swap_target, bindings[0].render_target);
    assert_eq!(targets.outline_target, bindings[1].render_target);
    assert_eq!(targets.swap_target, bindings[2].render_target);
    assert_eq!(targets.outline_target, bindings[3].render_target);
    for handle in sets.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    for handle in pipelines.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    for handle in targets.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
}

#[test]
fn entity_outline_post_effect_lowering_interleaves_uniforms_targets_and_draws() {
    let mut gal = crate::render::worldrender::tests::gal();
    let targets =
        create_entity_outline_target_resources(&mut gal, 64, 64, TextureFormat::Rgba8Unorm)
            .unwrap();
    let pipelines =
        create_entity_outline_post_effect_pipelines(&mut gal, TextureFormat::Rgba8Unorm)
            .unwrap();
    let sets = create_entity_outline_post_effect_resource_sets(&mut gal, &targets, &pipelines)
        .unwrap();
    let mut instance = test_mesh_instance(45);
    instance.outline_color_argb = 0xff_ff_ff_ff;
    let plan = prepare_entity_outline_post_effect(&frame_with_instances(vec![instance]))
        .unwrap()
        .unwrap();
    let operations = lower_entity_outline_post_effect_with_resources(
        &plan,
        &targets,
        &pipelines,
        &sets,
        Handle::new(HandleKind::RenderPass, 90, 1).unwrap(),
        Handle::new(HandleKind::RenderTarget, 91, 1).unwrap(),
        Handle::new(HandleKind::TextureView, 92, 1).unwrap(),
        pipelines.composite_depthless_pipeline,
        TextureUsageState::ShaderRead,
        TextureUsageState::Undefined,
        TextureUsageState::Undefined,
    )
    .unwrap();
    assert_eq!(
        3,
        operations
            .iter()
            .filter(|operation| matches!(operation, CommandOp::HostWriteBuffer { .. }))
            .count()
    );
    assert_eq!(
        5,
        operations
            .iter()
            .filter(|operation| matches!(
                operation,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    assert!(operations.iter().any(|op| matches!(op,
        CommandOp::BeginPass { target, colors, .. }
            if *target == Handle::new(HandleKind::RenderTarget, 91, 1).unwrap()
                && colors[0].load_op == AttachmentLoadOp::Load)));
    assert_eq!(
        gal.graphics_pipeline_descriptor_for_test(pipelines.composite_depthless_pipeline)
            .unwrap()
            .blend,
        BlendMode::AlphaPreserveAlpha
    );
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::Barrier(barrier)
            if barrier.resource == targets.swap_texture
                && barrier.after == TextureUsageState::ColorAttachment
    )));
    for handle in sets.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    for handle in pipelines.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    for handle in targets.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
}

#[test]
fn entity_outline_mask_gpu_preparation_owns_stream_and_resource_sets() {
    let mut gal = crate::render::worldrender::tests::gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let asset = crate::render::worldrender::tests::mesh_asset(23, 1, IndexType::U16);
    frontend.mesh_assets.insert(
        23,
        crate::render::worldrender::MeshAssetStore {
            decal_normals: None,
            translucent_order: Default::default(),
            section_ranges_cache: Default::default(),
            texture_animation_signature_cache: Default::default(),
            mesh_generation: asset.mesh_generation,
            index_generation: 1,
            vertex_layout_version: asset.vertex_layout_version,
            vertex_bytes: crate::render::worldrender::packed_mesh_vertices(&asset.vertices),
            source_input: None,
            entity_identity: String::new(),
            terrain_voxel_vertices: None,
            terrain_voxel_indices: None,
            terrain_voxel_translucent_indices: None,
            terrain_voxel_model_bounds: None,
            index_bytes: asset.index_bytes.clone(),
            index_type: asset.index_type,
            sections: asset.sections.clone(),
        },
    );
    let mut instance = test_mesh_instance(23);
    instance.outline_color_argb = 0xff_00_80_ff;
    let frame = frame_with_instances(vec![instance]);
    let resources = frontend
        .prepare_entity_outline_mask_gpu_resources(
            &mut gal,
            &frame,
            ColorFormat::Rgba8Unorm,
            RasterYDirection::Up,
        )
        .unwrap()
        .unwrap()
        .clone();
    assert_eq!(1, resources.draws.len());
    assert_eq!(1, resources.draws[0].instance_count);
    assert!(resources.instance_bytes.len() >= crate::render::worldrender::features::outline::WORLD_MESH_BATCH_HEADER_BYTES);
    assert_ne!(Handle::NULL, resources.instance_buffer);
    assert_ne!(Handle::NULL, resources.draws[0].resource_set);
    frontend.reset(&mut gal);
    assert!(frontend.entity_outline_mask_gpu.is_none());
}

fn test_mesh_instance(mesh_key: u64) -> WorldMeshInstanceRequest {
    WorldMeshInstanceRequest {
        entity_culling: None,
        model_submission_order: None,
        item_foil: None,
        decal_foil: None,
        stratum: WORLD_STRATUM_ENTITY_MESH,
        mesh_key,
        mesh_generation: 1,
        mesh_section_index: WORLD_MESH_SECTION_ALL,
        terrain_visible_facing_mask: 0x7f,
        depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
        cull_policy: WORLD_CULL_BACK,
        winding: WORLD_WINDING_CCW,
        color_argb: 0xffff_ffff,
        entity_id: 0,
        entity_color_argb: 0,
        packed_light: 0,
        outline_color_argb: 0,
        flags: 0,
        block_entity_id: -1,
        transform: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        viewport_width: 128,
        viewport_height: 128,
    }
}

#[cfg(target_os = "linux")]
#[test]
fn entity_outline_native_composition_preserves_background_alpha_and_orientation() {
    for direction in [RasterYDirection::Up, RasterYDirection::Down] {
        for texture_case in 0..3 {
            let pixels = native_outline_pixels(direction, texture_case);
            let pixel = |x: usize, y: usize| &pixels[(y * 128 + x) * 4..(y * 128 + x + 1) * 4];
            assert_eq!(pixel(8, 8), [32, 64, 128, 64], "background {direction:?}");
            assert_eq!(
                pixel(64, 100),
                [32, 64, 128, 64],
                "no reflected outline {direction:?}"
            );
            assert!(
                pixels.chunks_exact(4).all(|p| p[3] == 64),
                "destination alpha {direction:?}"
            );
            if texture_case == 1 {
                assert!(
                    pixels.chunks_exact(4).all(|p| p == [32, 64, 128, 64]),
                    "fully transparent texture must have no silhouette {direction:?}"
                );
            } else {
                assert!(
                    pixel(47, 36)[1] > 64,
                    "outline edge {direction:?}: {:?}",
                    pixel(47, 36)
                );
                // Frozen's box blur averages RGB, but SUMS alpha. At this
                // straight edge RGB is 0.4 green and alpha saturates to one
                // after the vertical pass: no background may bleed through.
                assert_eq!(
                    pixel(47, 36),
                    [0, 102, 0, 64],
                    "vanilla blur opacity {direction:?}"
                );
                if texture_case == 2 {
                    assert!(
                        pixel(64, 36)[1] > 64,
                        "alpha silhouette edge {direction:?}: {:?}",
                        pixel(64, 36)
                    );
                    assert_eq!(
                        pixel(80, 36),
                        [32, 64, 128, 64],
                        "transparent half {direction:?}"
                    );
                } else {
                    assert_eq!(
                        pixel(64, 36),
                        [32, 64, 128, 64],
                        "hollow interior {direction:?}"
                    );
                }
            }
        }
    }
}

#[cfg(target_os = "linux")]
fn native_outline_pixels(direction: RasterYDirection, texture_case: u8) -> Vec<u8> {
    use crate::render::worldrender::passes::oriented_target::{
        OrientedWorldTarget, WorldAttachmentStates, WorldTargetDesc,
    };
            let mut gal = crate::render::vulkanic::test_support::vulkan_gal("outline composition").unwrap();
    let extent = Extent3d {
        width: 128,
        height: 128,
        depth: 1,
    };
    let owner = OrientedWorldTarget::create(
        &mut gal,
        "outline.world",
        WorldTargetDesc {
            extent,
            color_format: TextureFormat::Rgba8Unorm,
            raster_y_direction: direction,
        },
    )
    .unwrap();
    let canonical = OrientedWorldTarget::create(
        &mut gal,
        "outline.canonical",
        WorldTargetDesc {
            extent,
            color_format: TextureFormat::Rgba8Unorm,
            raster_y_direction: RasterYDirection::Up,
        },
    )
    .unwrap();
    let mut frontend = WorldPrimitiveFrontend::default();
    let mut mesh = crate::render::worldrender::tests::mesh_asset(9891, 1, IndexType::U32);
    for vertex in &mut mesh.vertices {
        vertex.position = [
            vertex.position[0] * 0.5,
            vertex.position[1] * 0.5 + 0.4,
            0.0,
        ];
    }
    let mut padding = crate::render::worldrender::tests::mesh_asset(9890, 1, IndexType::U32);
    padding.index_bytes.fill(0);
    let mut png_bytes = Vec::new();
    {
        let mut encoder = png::Encoder::new(&mut png_bytes, 2, 1);
        encoder.set_color(png::ColorType::Rgba);
        encoder.set_depth(png::BitDepth::Eight);
        let mut writer = encoder.write_header().unwrap();
        writer
            .write_image_data(&[
                255,
                255,
                255,
                if texture_case == 1 { 0 } else { 255 },
                255,
                255,
                255,
                if texture_case == 0 { 255 } else { 0 },
            ])
            .unwrap();
    }
    let texture = WorldMeshTextureAssetPayload {
        texture_id: WORLD_MATERIAL_TEXTURE_STONE,
        png_bytes,
        mip_png_bytes: Vec::new(),
        frame_width: 0,
        frame_height: 0,
        frame_count: 1,
        frame_ticks: 1,
        animation_flags: 0,
        frame_row_size: 0,
        interpolation_policy: 0,
        animation_frames: Vec::new(),
        coordinate_origin: 0,
        sampling: None,
        requested_mip_levels: 1,
    };
    frontend
        .apply_world_mesh_asset_update(&mut gal, 1, vec![padding, mesh], vec![texture])
        .unwrap();
    let padding = &frontend.mesh_assets[&9890];
    let vertex_bytes = padding.vertex_bytes.clone();
    let index_bytes = padding.index_bytes.clone();
    frontend
        .ensure_mesh_geometry_resources(
            &mut gal,
            MeshGeometryResourceKey {
                mesh_key: 9890,
                mesh_generation: 1,
                vertex_abi: MeshVertexAbi::Rich80,
            },
            vertex_bytes,
            index_bytes,
            false,
        )
        .unwrap();
    let mut instance = test_mesh_instance(9891);
    instance.flags = WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY;
    instance.outline_color_argb = 0xff00ff00;
    let mut frame = frame_with_instances(vec![instance]);
    frame.view_matrix = matrix4_identity();
    frame.projection_matrix = matrix4_identity();
    frame.background.enabled = true;
    frame.background.sky_type = WORLD_BACKGROUND_SKY_NETHER;
    frame.background.load_intent = WORLD_BACKGROUND_LOAD_CLEAR;
    frame.background.store_intent = WORLD_BACKGROUND_STORE_STORE;
    frame.background.color_argb = 0x40204080;
    frame.background.viewport_width = 128;
    frame.background.viewport_height = 128;
    let (mut ops, _) = frontend
        .append_frame_ops_inner(&mut gal, 1, owner.target, frame, true, direction)
        .unwrap();
    let draw = &frontend.entity_outline_mask_gpu.as_ref().unwrap().draws[0];
    assert!(
        draw.vertex_offset > 0 && draw.index_offset > 0,
        "fixture must exercise both shared-geometry offsets"
    );
    let descriptor = gal
        .graphics_pipeline_descriptor_for_test(draw.pipeline)
        .unwrap();
    assert_eq!(descriptor.depth_compare, None);
    assert!(!descriptor.depth_write);
    ops.insert(
        0,
        CommandOp::Barrier(texture_barrier(
            owner.color_texture,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )),
    );
    ops.extend(
        owner
            .transfer_to(
                &canonical,
                WorldAttachmentStates::ATTACHMENTS,
                WorldAttachmentStates::UNDEFINED,
                WorldAttachmentStates::ATTACHMENTS,
            )
            .unwrap(),
    );
    let readback = gal
        .create_buffer(BufferDesc {
            label: "outline.readback".into(),
            size: 128 * 128 * 8,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })
        .unwrap();
    ops.extend([
        CommandOp::Barrier(texture_barrier(
            canonical.color_texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::TransferSrc,
        )),
        CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: 128 * 4,
            rows_per_image: 128,
            texture: canonical.color_texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }),
        CommandOp::Barrier(texture_barrier(
            canonical.depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::TransferSrc,
        )),
        CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 128 * 128 * 4,
            bytes_per_row: 128 * 4,
            rows_per_image: 128,
            texture: canonical.depth_texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }),
        CommandOp::Barrier(buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )),
        CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: 128 * 128 * 8,
        },
    ]);
    let list = gal
        .create_command_list(CommandListDesc {
            label: "outline.commands".into(),
            operations: ops,
        })
        .unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "outline.submit".into(),
            command_lists: vec![list],
        })
        .unwrap();
    gal.retire_through(token.submission).unwrap();
    let pixels = gal
        .completed_host_reads()
        .iter()
        .find(|read| read.buffer == readback)
        .unwrap()
        .bytes
        .clone();
    assert!(
        pixels[128 * 128 * 4..]
            .chunks_exact(4)
            .all(|p| f32::from_ne_bytes(p.try_into().unwrap()) == 1.0),
        "outline mask and composition must not modify world depth"
    );
    gal.destroy(readback).unwrap();
    frontend.reset(&mut gal);
    for resource in [owner, canonical] {
        for handle in resource.handles_in_destroy_order() {
            gal.destroy(handle).unwrap();
        }
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
    pixels[..128 * 128 * 4].to_vec()
}
