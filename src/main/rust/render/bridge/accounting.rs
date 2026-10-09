//! Header/table-size estimates only: never follow nested foreign pointers.
//! Checked memory readers account for actual bytes within the request budget.

use crate::render::bridge::*;

pub(crate) fn output_bytes_for_resource_results(capacity: u64) -> u64 {
    capacity.saturating_mul(size_of::<FfiCreateResultEntry>() as u64)
}

pub(crate) fn input_bytes_for_resource_batch(batch: &FfiResourceBatch) -> u64 {
    (size_of::<FfiResourceBatch>() as u64)
        .saturating_add(
            batch
                .buffers
                .count
                .saturating_mul(size_of::<FfiBufferDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .textures
                .count
                .saturating_mul(size_of::<FfiTextureDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .texture_views
                .count
                .saturating_mul(size_of::<FfiTextureViewDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .samplers
                .count
                .saturating_mul(size_of::<FfiSamplerDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .shaders
                .count
                .saturating_mul(size_of::<FfiShaderModuleDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .resource_layouts
                .count
                .saturating_mul(size_of::<FfiResourceLayoutDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .resource_layout_bindings
                .count
                .saturating_mul(size_of::<FfiResourceBindingDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .resource_sets
                .count
                .saturating_mul(size_of::<FfiResourceSetDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .resource_set_bindings
                .count
                .saturating_mul(size_of::<FfiResourceBindingAbi>() as u64),
        )
        .saturating_add(
            batch
                .pipeline_layouts
                .count
                .saturating_mul(size_of::<FfiPipelineLayoutDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .graphics_pipelines
                .count
                .saturating_mul(size_of::<FfiGraphicsPipelineDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .compute_pipelines
                .count
                .saturating_mul(size_of::<FfiComputePipelineDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .render_targets
                .count
                .saturating_mul(size_of::<FfiRenderTargetDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .render_passes
                .count
                .saturating_mul(size_of::<FfiRenderPassDescAbi>() as u64),
        )
        .saturating_add(
            batch
                .buffer_updates
                .count
                .saturating_mul(size_of::<FfiBufferUpdateAbi>() as u64),
        )
        .saturating_add(
            batch
                .texture_updates
                .count
                .saturating_mul(size_of::<FfiTextureUpdateAbi>() as u64),
        )
        .saturating_add(
            batch
                .destroys
                .count
                .saturating_mul(size_of::<FfiDestroyDescAbi>() as u64),
        )
}

pub(crate) fn input_bytes_for_submission(batch: &FfiSubmissionBatchAbi) -> u64 {
    (size_of::<FfiSubmissionBatchAbi>() as u64)
        .saturating_add(
            batch
                .command_lists
                .count
                .saturating_mul(size_of::<FfiCommandListAbi>() as u64),
        )
        .saturating_add(
            batch
                .operations
                .count
                .saturating_mul(size_of::<FfiCommandOpAbi>() as u64),
        )
        .saturating_add(
            batch
                .pass_attachments
                .count
                .saturating_mul(size_of::<FfiPassAttachmentAbi>() as u64),
        )
        .saturating_add(
            batch
                .copy_regions
                .count
                .saturating_mul(size_of::<FfiBufferImageCopyAbi>() as u64),
        )
        .saturating_add(
            batch
                .barriers
                .count
                .saturating_mul(size_of::<FfiResourceBarrierAbi>() as u64),
        )
}

pub(crate) fn input_bytes_for_gui_frame(request: &FfiGuiFrameSubmitRequest) -> u64 {
    (size_of::<FfiGuiFrameSubmitRequest>() as u64)
        .saturating_add(
            request
                .tiled_quads
                .count
                .saturating_mul(size_of::<FfiGuiTiledQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .sprites
                .count
                .saturating_mul(size_of::<FfiGuiSpriteRequest>() as u64),
        )
        .saturating_add(
            request
                .affine_quads
                .count
                .saturating_mul(size_of::<FfiGuiAffineQuadRequest>() as u64),
        )
}

pub(crate) fn input_bytes_for_whole_frame(request: &FfiWholeFrameSubmitRequest) -> u64 {
    (size_of::<FfiWholeFrameSubmitRequest>() as u64)
        .saturating_add(
            request
                .world_particle_quads
                .count
                .saturating_mul(size_of::<FfiWorldParticleQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .world_experience_orbs
                .count
                .saturating_mul(size_of::<FfiWorldExperienceOrbInstanceRecord>() as u64),
        )
        .saturating_add(
            request
                .gui_tiled_quads
                .count
                .saturating_mul(size_of::<FfiGuiTiledQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .world_segments
                .count
                .saturating_mul(size_of::<FfiWorldLineSegmentRequest>() as u64),
        )
        .saturating_add(
            request
                .world_crack_quads
                .count
                .saturating_mul(size_of::<FfiWorldCrackQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .world_border_quads
                .count
                .saturating_mul(size_of::<FfiWorldBorderQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .world_material_quads
                .count
                .saturating_mul(size_of::<FfiWorldMaterialQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .world_material_table
                .count
                .saturating_mul(size_of::<FfiWorldMaterialTableRecord>() as u64),
        )
        .saturating_add(
            request
                .world_material_compact_quads
                .count
                .saturating_mul(size_of::<FfiWorldMaterialCompactQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .world_mesh_instances
                .count
                .saturating_mul(size_of::<FfiWorldMeshInstanceRecord>() as u64),
        )
        .saturating_add(
            request
                .world_lod_instances
                .count
                .saturating_add(request.world_lod_frame_count)
                .saturating_mul(size_of::<FfiWorldLodColumnInstanceRecord>() as u64),
        )
        .saturating_add(
            request
                .gui_sprites
                .count
                .saturating_mul(size_of::<FfiGuiSpriteRequest>() as u64),
        )
        .saturating_add(
            request
                .gui_affine_quads
                .count
                .saturating_mul(size_of::<FfiGuiAffineQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .world_text_quads
                .count
                .saturating_mul(size_of::<FfiWorldTextQuadRequest>() as u64),
        )
        .saturating_add(
            request
                .gui_mesh_batches
                .count
                .saturating_mul(size_of::<FfiGuiMeshBatchRequest>() as u64),
        )
        .saturating_add(
            request
                .world_first_person_mesh_instances
                .count
                .saturating_mul(size_of::<FfiWorldMeshInstanceRecord>() as u64),
        )
        .saturating_add(
            request
                .world_distant_horizons_generic_boxes
                .count
                .saturating_mul(size_of::<FfiWorldDistantHorizonsGenericBoxRecord>() as u64),
        )
        .saturating_add(
            request
                .world_static_terrain_shadow_casters
                .count
                .saturating_mul(size_of::<FfiStaticTerrainShadowCaster>() as u64),
        )
        .saturating_add(
            request
                .world_static_terrain_sections
                .count
                .saturating_mul(size_of::<FfiStaticTerrainSection>() as u64),
        )
        .saturating_add(
            request
                .world_model_rig_poses
                .count
                .saturating_mul(size_of::<FfiModelRigPose>() as u64),
        )
        .saturating_add(
            request
                .world_dh_generic_group_instances
                .count
                .saturating_mul(size_of::<FfiDhGenericGroupInstance>() as u64),
        )
        .saturating_add(request.post_effect_id.len)
}

pub(crate) fn input_bytes_for_gui_asset_update(request: &FfiGuiAssetUpdateRequest) -> u64 {
    let payload_headers = request
        .assets
        .count
        .saturating_mul(size_of::<FfiGuiAssetPayload>() as u64);
    let payload_bytes = 0u64;
    (size_of::<FfiGuiAssetUpdateRequest>() as u64)
        .saturating_add(payload_headers)
        .saturating_add(payload_bytes)
}

pub(crate) fn input_bytes_for_gui_raw_image_update(request: &FfiGuiRawImageUpdateRequest) -> u64 {
    let payload_headers = request
        .assets
        .count
        .saturating_mul(size_of::<FfiGuiRawImageAssetPayload>() as u64);
    let payload_bytes = 0u64;
    (size_of::<FfiGuiRawImageUpdateRequest>() as u64)
        .saturating_add(payload_headers)
        .saturating_add(payload_bytes)
        .saturating_add(request.retained_asset_ids.count.saturating_mul(size_of::<u64>() as u64))
}

pub(crate) fn input_bytes_for_world_text_image_update(
    request: &FfiWorldTextImageUpdateRequest,
) -> u64 {
    let payload_headers = request
        .assets
        .count
        .saturating_mul(size_of::<FfiWorldTextImageAssetPayload>() as u64);
    let payload_bytes = 0u64;
    (size_of::<FfiWorldTextImageUpdateRequest>() as u64)
        .saturating_add(payload_headers)
        .saturating_add(payload_bytes)
}

pub(crate) fn input_bytes_for_world_border_asset_update(
    request: &FfiWorldBorderAssetUpdateRequest,
) -> u64 {
    (size_of::<FfiWorldBorderAssetUpdateRequest>() as u64).saturating_add(request.png_bytes.len)
}

pub(crate) fn input_bytes_for_world_crack_asset_update(
    request: &FfiWorldCrackAssetUpdateRequest,
) -> u64 {
    let payload_headers = request
        .assets
        .count
        .saturating_mul(size_of::<FfiWorldCrackAssetPayload>() as u64);
    let payload_bytes = 0u64;
    (size_of::<FfiWorldCrackAssetUpdateRequest>() as u64)
        .saturating_add(payload_headers)
        .saturating_add(payload_bytes)
}

pub(crate) fn input_bytes_for_world_material_asset_update(
    request: &FfiWorldMaterialAssetUpdateRequest,
) -> u64 {
    let payload_headers = request
        .assets
        .count
        .saturating_mul(size_of::<FfiWorldMaterialAssetPayload>() as u64);
    let payload_bytes =
        0u64;
    (size_of::<FfiWorldMaterialAssetUpdateRequest>() as u64)
        .saturating_add(payload_headers)
        .saturating_add(payload_bytes)
}

pub(crate) fn input_bytes_for_world_mesh_asset_update(
    request: &FfiWorldMeshAssetUpdateRequest,
) -> u64 {
    let mesh_headers = request
        .meshes
        .count
        .saturating_mul(size_of::<FfiWorldMeshAssetRecord>() as u64);
    let texture_headers = request
        .textures
        .count
        .saturating_mul(size_of::<FfiWorldMeshTextureAssetPayload>() as u64);
    let sorted_index_headers = request
        .sorted_indices
        .count
        .saturating_mul(size_of::<FfiWorldMeshSortedIndexRecord>() as u64);
    let retirement_headers = request
        .retirements
        .count
        .saturating_mul(size_of::<FfiWorldMeshAssetRetirementRecord>() as u64);
    let sorted_index_payload_bytes = 0u64;
    (size_of::<FfiWorldMeshAssetUpdateRequest>() as u64)
        .saturating_add(mesh_headers)
        .saturating_add(
            request
                .experience_orbs
                .count
                .saturating_mul(size_of::<FfiWorldExperienceOrbAssetRecord>() as u64),
        )
        .saturating_add(texture_headers)
        .saturating_add(sorted_index_headers)
        .saturating_add(retirement_headers)
        .saturating_add(sorted_index_payload_bytes)
}

pub(crate) fn input_bytes_for_world_lod_asset_update(request: &FfiWorldLodAssetUpdateRequest) -> u64 {
    (size_of::<FfiWorldLodAssetUpdateRequest>() as u64)
        .saturating_add(request.assets.count.saturating_mul(size_of::<FfiWorldLodColumnAssetRecord>() as u64))
        .saturating_add(request.retirements.count.saturating_mul(size_of::<FfiWorldLodColumnRetirementRecord>() as u64))
}

pub(crate) fn input_bytes_for_shader_pack_source_update(
    request: &FfiShaderPackSourceUpdateRequest,
) -> u64 {
    let file_headers = request
        .files
        .count
        .saturating_mul(size_of::<FfiShaderPackSourceFile>() as u64);
    let file_bytes = 0u64;
    (size_of::<FfiShaderPackSourceUpdateRequest>() as u64)
        .saturating_add(request.pack_name_utf8.len)
        .saturating_add(file_headers)
        .saturating_add(file_bytes)
}

pub(crate) fn input_bytes_for_shader_pack_asset_update(
    request: &FfiShaderPackAssetUpdateRequest,
) -> u64 {
    let file_headers = request
        .files
        .count
        .saturating_mul(size_of::<FfiShaderPackAssetFile>() as u64);
    let file_bytes = 0u64;
    (size_of::<FfiShaderPackAssetUpdateRequest>() as u64)
        .saturating_add(request.pack_name_utf8.len)
        .saturating_add(file_headers)
        .saturating_add(file_bytes)
}
