use super::*;
use std::mem::offset_of;

pub(crate) fn layout_for_struct(struct_id: u32) -> GalResult<FfiStructLayout> {
    macro_rules! layout {
        ($id:expr, $ty:ty, [$($field:tt),* $(,)?]) => {{
            let mut offsets = [0_u32; 64];
            let fields = [$(offset_of!($ty, $field) as u32),*];
            offsets[..fields.len()].copy_from_slice(&fields);
            FfiStructLayout {
                header: FfiHeader {
                    version: FFI_ABI_VERSION,
                    byte_size: size_of::<FfiStructLayout>() as u32,
                },
                struct_id: $id,
                byte_size: size_of::<$ty>() as u32,
                alignment: align_of::<$ty>() as u32,
                field_count: fields.len() as u32,
                field_offsets: offsets,
            }
        }};
    }
    Ok(match struct_id {
        1 => layout!(1, FfiHeader, [version, byte_size]),
        2 => layout!(2, FfiBytes, [ptr, len]),
        3 => layout!(3, FfiHandle, [raw]),
        4 => layout!(
            4,
            FfiContextCreateRequest,
            [header, backend_kind, tracy_enabled, label]
        ),
        5 => layout!(
            5,
            FfiContextResult,
            [
                header,
                status,
                error_domain,
                context_id,
                supported_feature_bits,
                limits,
                metrics
            ]
        ),
        6 => layout!(
            6,
            FfiCapabilityQueryRequest,
            [header, requested_feature_bits, reserved0]
        ),
        7 => layout!(
            7,
            FfiCapabilityResult,
            [
                header,
                status,
                error_domain,
                supported_feature_bits,
                negotiated_feature_bits,
                limits,
                initial_presentation_supported
            ]
        ),
        8 => layout!(
            8,
            FfiStatusResult,
            [
                header,
                status,
                error_domain,
                unsupported_feature,
                primary_handle,
                submission_id,
                required_bytes,
                metrics
            ]
        ),
        9 => layout!(
            9,
            FfiCreateResultEntry,
            [request_id, handle, status, error_domain]
        ),
        10 => layout!(
            10,
            FfiBufferDescAbi,
            [
                byte_size,
                request_id,
                label,
                size,
                memory_domain,
                usage_bits
            ]
        ),
        11 => layout!(
            11,
            FfiTextureDescAbi,
            [
                byte_size,
                request_id,
                label,
                dimension,
                format,
                extent,
                mip_levels,
                array_layers,
                usage_bits
            ]
        ),
        12 => layout!(
            12,
            FfiTextureViewDescAbi,
            [
                byte_size,
                request_id,
                label,
                texture,
                format,
                base_mip,
                mip_count,
                base_layer,
                layer_count
            ]
        ),
        13 => layout!(
            13,
            FfiSamplerDescAbi,
            [
                byte_size, request_id, label, min_filter, mag_filter, mip_filter, address_u,
                address_v, address_w
            ]
        ),
        14 => layout!(
            14,
            FfiShaderModuleDescAbi,
            [
                byte_size,
                request_id,
                label,
                stage,
                code_format,
                code,
                entry_point
            ]
        ),
        15 => layout!(
            15,
            FfiResourceBindingDescAbi,
            [
                byte_size,
                binding,
                kind,
                stage_bits,
                array_count,
                optional,
                dynamic_offset_count
            ]
        ),
        16 => layout!(
            16,
            FfiResourceLayoutDescAbi,
            [byte_size, request_id, label, bindings]
        ),
        17 => layout!(
            17,
            FfiResourceBindingAbi,
            [
                byte_size,
                binding,
                array_index,
                resource,
                kind,
                access_bits,
                dynamic_offsets
            ]
        ),
        18 => layout!(
            18,
            FfiResourceSetDescAbi,
            [byte_size, request_id, label, layout, bindings]
        ),
        19 => layout!(
            19,
            FfiPipelineLayoutDescAbi,
            [byte_size, request_id, label, resource_layouts]
        ),
        20 => layout!(
            20,
            FfiGraphicsPipelineDescAbi,
            [
                byte_size,
                request_id,
                label,
                layout,
                vertex_shader,
                fragment_shader,
                topology,
                cull_mode,
                blend,
                depth_compare,
                color_formats,
                depth_format
            ]
        ),
        21 => layout!(
            21,
            FfiRenderTargetDescAbi,
            [
                byte_size,
                request_id,
                label,
                color_views,
                depth_stencil_view,
                extent
            ]
        ),
        22 => layout!(
            22,
            FfiRenderPassDescAbi,
            [
                byte_size,
                request_id,
                label,
                target,
                color_formats,
                depth_format
            ]
        ),
        23 => layout!(
            23,
            FfiResourceBatch,
            [
                header,
                buffers,
                textures,
                texture_views,
                samplers,
                shaders,
                resource_layouts,
                resource_layout_bindings,
                resource_sets,
                resource_set_bindings,
                dynamic_offsets,
                pipeline_layouts,
                pipeline_layout_resource_layouts,
                graphics_pipelines,
                compute_pipelines,
                render_targets,
                render_target_color_views,
                render_passes,
                render_pass_color_formats,
                buffer_updates,
                texture_updates,
                destroys,
                negotiated_feature_bits
            ]
        ),
        24 => layout!(
            24,
            FfiPassAttachmentAbi,
            [
                byte_size,
                view,
                load_op,
                store_op,
                has_clear_color,
                clear_color
            ]
        ),
        25 => layout!(
            25,
            FfiBufferImageCopyAbi,
            [
                byte_size,
                buffer,
                buffer_offset,
                bytes_per_row,
                rows_per_image,
                texture,
                texture_mip,
                texture_layer,
                texture_origin,
                extent
            ]
        ),
        26 => layout!(
            26,
            FfiResourceBarrierAbi,
            [
                byte_size,
                resource,
                has_subresources,
                subresources,
                before,
                after,
                stage_bits,
                access_bits,
                src_queue,
                dst_queue
            ]
        ),
        27 => layout!(
            27,
            FfiCommandOpAbi,
            [
                byte_size,
                op_kind,
                primary,
                secondary,
                tertiary,
                set_index,
                slot,
                offset,
                size,
                count0,
                count1,
                count2,
                colors,
                depth_stencil,
                copy_region,
                barrier,
                inline_bytes,
                subresources
            ]
        ),
        28 => layout!(28, FfiCommandListAbi, [byte_size, label, operations]),
        29 => layout!(
            29,
            FfiSubmissionBatchAbi,
            [
                header,
                label,
                command_lists,
                operations,
                pass_attachments,
                copy_regions,
                barriers,
                negotiated_feature_bits
            ]
        ),
        30 => layout!(30, FfiCompletionQueryRequest, [header, submission_id]),
        31 => layout!(
            31,
            FfiCompletionResult,
            [
                header,
                status,
                error_domain,
                requested_submission_id,
                completed_submission_id,
                is_complete
            ]
        ),
        32 => layout!(
            32,
            FfiRetirementBatch,
            [header, completed_submission_id, handles]
        ),
        33 => layout!(
            33,
            FfiReadbackRequest,
            [header, submission_id, buffer, offset, size]
        ),
        34 => layout!(
            34,
            FfiReadbackResult,
            [
                header,
                status,
                error_domain,
                submission_id,
                required_bytes,
                written_bytes,
                metrics
            ]
        ),
        35 => layout!(
            35,
            FfiBorrowedOpenGlContextCreateRequest,
            [header, stable_window_id, tracy_enabled, reserved0, label]
        ),
        36 => layout!(
            36,
            FfiFrameSurfaceConfigRequest,
            [
                header,
                label,
                extent,
                color_format,
                present_mode,
                max_frames_in_flight
            ]
        ),
        37 => layout!(
            37,
            FfiFrameAcquireRequest,
            [header, correlation_id, expected_extent]
        ),
        38 => layout!(
            38,
            FfiFrameAcquireResult,
            [
                header,
                status,
                error_domain,
                frame_id,
                correlation_id,
                acquire_status,
                frame_target,
                frame_target_identity,
                extent,
                color_format,
                metrics
            ]
        ),
        39 => layout!(39, FfiFrameResizeRequest, [header, correlation_id, extent]),
        40 => layout!(
            40,
            FfiFrameResizeResult,
            [header, status, error_domain, resize_status, extent]
        ),
        41 => layout!(
            41,
            FfiFramePresentRequest,
            [header, frame_id, correlation_id, wait_submission_id]
        ),
        42 => layout!(
            42,
            FfiFramePresentResult,
            [
                header,
                status,
                error_domain,
                frame_id,
                correlation_id,
                present_status,
                completed_submission_id,
                frame_target_identity
            ]
        ),
        43 => layout!(43, FfiDestroyDescAbi, [byte_size, handle, expected_kind]),
        44 => layout!(
            44,
            FfiGuiSpriteRequest,
            [
                byte_size,
                stratum,
                sprite_id,
                selected_slot,
                progress_fraction,
                fill_direction,
                color_argb,
                x,
                y,
                width,
                height,
                gui_width,
                gui_height
            ]
        ),
        45 => layout!(
            45,
            FfiGuiFrameSubmitRequest,
            [
                header,
                generation,
                frame_id,
                frame_target,
                gui_width,
                gui_height,
                sprites,
                negotiated_feature_bits
            ]
        ),
        46 => layout!(
            46,
            FfiGuiFrameSubmitResult,
            [
                header,
                status,
                error_domain,
                submission_id,
                sprite_count,
                sprite_batch_count,
                cache_hits,
                cache_misses,
                resource_creates,
                command_lists,
                command_ops,
                metrics
            ]
        ),
        47 => layout!(47, FfiGuiAssetPayload, [byte_size, sprite_id, png_bytes]),
        48 => layout!(
            48,
            FfiGuiAssetUpdateRequest,
            [header, generation, assets, negotiated_feature_bits]
        ),
        49 => layout!(
            49,
            FfiWindowedVulkanContextCreateRequest,
            [
                header,
                platform,
                tracy_enabled,
                stable_window_id,
                native_display,
                native_window,
                label,
                surface_label,
                extent,
                color_format,
                present_mode,
                max_frames_in_flight
            ]
        ),
        50 => layout!(
            50,
            FfiWorldLineSegmentRequest,
            [
                byte_size,
                stratum,
                style,
                depth_policy,
                color_argb,
                line_width,
                start_x,
                start_y,
                start_z,
                end_x,
                end_y,
                end_z,
                viewport_width,
                viewport_height
            ]
        ),
        51 => layout!(
            51,
            FfiWorldCrackQuadRequest,
            [
                byte_size,
                stratum,
                stage,
                depth_policy,
                blend_policy,
                cull_policy,
                color_argb,
                reserved0,
                p0_x,
                p0_y,
                p0_z,
                p1_x,
                p1_y,
                p1_z,
                p2_x,
                p2_y,
                p2_z,
                p3_x,
                p3_y,
                p3_z,
                viewport_width,
                viewport_height
            ]
        ),
        52 => layout!(
            52,
            FfiWorldBorderQuadRequest,
            [
                byte_size,
                stratum,
                texture_id,
                depth_policy,
                blend_policy,
                cull_policy,
                color_argb,
                reserved0,
                border_size,
                distance_to_border,
                scroll_u,
                scroll_v,
                uv_u,
                uv_v,
                uv_width,
                uv_height,
                p0_x,
                p0_y,
                p0_z,
                p1_x,
                p1_y,
                p1_z,
                p2_x,
                p2_y,
                p2_z,
                p3_x,
                p3_y,
                p3_z,
                viewport_width,
                viewport_height
            ]
        ),
        53 => layout!(
            53,
            FfiWholeFrameSubmitRequest,
            [
                header,
                generation,
                frame_id,
                correlation_id,
                frame_target,
                gui_width,
                gui_height,
                viewport_width,
                viewport_height,
                view_matrix,
                projection_matrix,
                world_background,
                world_segments,
                world_crack_quads,
                world_border_quads,
                world_material_quads,
                world_material_table,
                world_material_compact_quads,
                world_mesh_instances,
                gui_sprites,
                negotiated_feature_bits
            ]
        ),
        54 => layout!(
            54,
            FfiWholeFrameSubmitResult,
            [
                header,
                status,
                error_domain,
                submission_id,
                world_segment_count,
                world_vertex_count,
                world_batch_count,
                world_draw_count,
                world_crack_quad_count,
                world_crack_batch_count,
                world_crack_draw_count,
                world_border_quad_count,
                world_border_batch_count,
                world_border_draw_count,
                world_material_quad_count,
                world_material_batch_count,
                world_material_draw_count,
                world_mesh_instance_count,
                world_mesh_batch_count,
                world_mesh_draw_count,
                world_background_clear_count,
                world_background_diagnostic_fallback_count,
                world_background_sky_type,
                world_background_color_argb,
                depth_attachment_creates,
                depth_attachment_reuses,
                depth_attachment_retires,
                outline_cache_hits,
                outline_cache_misses,
                crack_cache_hits,
                crack_cache_misses,
                border_cache_hits,
                border_cache_misses,
                material_cache_hits,
                material_cache_misses,
                mesh_cache_hits,
                mesh_cache_misses,
                sprite_count,
                sprite_batch_count,
                cache_hits,
                cache_misses,
                resource_creates,
                command_lists,
                command_ops,
                metrics,
                profile
            ]
        ),
        55 => layout!(
            55,
            FfiWorldBorderAssetUpdateRequest,
            [
                header,
                generation,
                texture_id,
                reserved0,
                png_bytes,
                negotiated_feature_bits
            ]
        ),
        56 => layout!(
            56,
            FfiWorldBackgroundRequest,
            [
                byte_size,
                enabled,
                sky_type,
                load_intent,
                store_intent,
                color_argb,
                viewport_width,
                viewport_height
            ]
        ),
        57 => layout!(57, FfiWorldCrackAssetPayload, [byte_size, stage, png_bytes]),
        58 => layout!(
            58,
            FfiWorldCrackAssetUpdateRequest,
            [header, generation, assets, negotiated_feature_bits]
        ),
        59 => layout!(
            59,
            FfiWorldMaterialQuadRequest,
            [
                byte_size,
                stratum,
                material_id,
                texture_id,
                material_mode,
                depth_policy,
                cull_policy,
                topology,
                color_argb,
                reserved0,
                p0_x,
                p0_y,
                p0_z,
                p1_x,
                p1_y,
                p1_z,
                p2_x,
                p2_y,
                p2_z,
                p3_x,
                p3_y,
                p3_z,
                uv0_u,
                uv0_v,
                uv1_u,
                uv1_v,
                uv2_u,
                uv2_v,
                uv3_u,
                uv3_v,
                viewport_width,
                viewport_height
            ]
        ),
        60 => layout!(
            60,
            FfiWorldMaterialAssetPayload,
            [byte_size, texture_id, png_bytes]
        ),
        61 => layout!(
            61,
            FfiWorldMaterialAssetUpdateRequest,
            [header, generation, assets, negotiated_feature_bits]
        ),
        62 => layout!(
            62,
            FfiWorldMaterialTableRecord,
            [
                byte_size,
                stratum,
                material_id,
                texture_id,
                material_mode,
                depth_policy,
                cull_policy,
                topology,
                winding,
                reserved0
            ]
        ),
        63 => layout!(
            63,
            FfiWorldMaterialCompactQuadRequest,
            [
                byte_size,
                material_index,
                color_argb,
                reserved0,
                p0_x,
                p0_y,
                p0_z,
                p1_x,
                p1_y,
                p1_z,
                p2_x,
                p2_y,
                p2_z,
                p3_x,
                p3_y,
                p3_z,
                uv0_u,
                uv0_v,
                uv1_u,
                uv1_v,
                uv2_u,
                uv2_v,
                uv3_u,
                uv3_v
            ]
        ),
        64 => layout!(
            64,
            FfiWorldMeshVertex,
            [
                byte_size,
                color_argb,
                normal_packed,
                light,
                x,
                y,
                z,
                u,
                v,
                atlas_u,
                atlas_v,
                shader_block_id,
                shader_material_type
            ]
        ),
        65 => layout!(
            65,
            FfiWorldMeshSectionRecord,
            [
                byte_size,
                material_id,
                texture_id,
                material_mode,
                cull_policy,
                winding,
                index_offset,
                index_count
            ]
        ),
        66 => layout!(
            66,
            FfiWorldMeshAssetRecord,
            [
                byte_size,
                vertex_layout_version,
                index_type,
                reserved0,
                mesh_key,
                mesh_generation,
                vertices,
                index_bytes,
                sections
            ]
        ),
        67 => layout!(
            67,
            FfiWorldMeshTextureAssetPayload,
            [byte_size, texture_id, png_bytes]
        ),
        68 => layout!(
            68,
            FfiWorldMeshAssetUpdateRequest,
            [
                header,
                generation,
                meshes,
                textures,
                sorted_indices,
                negotiated_feature_bits
            ]
        ),
        69 => layout!(
            69,
            FfiWorldMeshInstanceRecord,
            [
                byte_size,
                stratum,
                mesh_section_index,
                depth_policy,
                cull_policy,
                winding,
                color_argb,
                viewport_width,
                viewport_height,
                mesh_key,
                mesh_generation,
                transform
            ]
        ),
        70 => layout!(
            70,
            FfiWorldMeshSortedIndexRecord,
            [
                byte_size,
                index_type,
                reserved0,
                mesh_key,
                mesh_generation,
                index_generation,
                index_bytes
            ]
        ),
        _ => {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown ABI struct id {struct_id}"),
            ))
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_abi_struct_layout(
    struct_id: u32,
    out: *mut FfiStructLayout,
) -> i32 {
    match layout_for_struct(struct_id).and_then(|layout| write_out(out, layout, "layout result")) {
        Ok(()) => StatusCode::Ok as i32,
        Err(error) => error.code as i32,
    }
}
