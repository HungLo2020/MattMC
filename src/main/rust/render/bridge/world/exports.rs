//! The world entry points Java calls: whole-frame and world-primitive submits and world asset updates.

use super::*;

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_whole_frame_submit(
    context_id: u64,
    request: *const FfiWholeFrameSubmitRequest,
    out: *mut FfiWholeFrameSubmitResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            let _ = write_out(
                out,
                FfiWholeFrameSubmitResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    ..FfiWholeFrameSubmitResult::default()
                },
                "whole-frame submit result",
            );
            return error.code as i32;
        };
        let input_bytes = read_whole_frame_request(request)
            .as_ref()
            .map(input_bytes_for_whole_frame)
            .unwrap_or(0);
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiWholeFrameSubmitResult>() as u64);
        let decode_started = std::time::Instant::now();
        let result = decode_whole_frame_submit_with_tiled_gui(request, context.gal.capabilities())
            .and_then(
                |(
                    generation,
                    frame_target,
                    world_frame,
                    gui_sprites,
                    gui_affine_quads,
                    gui_mesh_batches,
                    gui_blur_before_stratum,
                    gui_blur_radius,
                    post_effect_id,
                    gui_tiled_quads,
                )| {
                    let world_frame_id = world_frame.frame_id;
                    if std::env::var_os("MATTMC_TRACE_WHOLE_FRAME").is_some() {
                        // Observe decoded native item meshes, not Java producer
                        // counts. A group is the real scheduler item identity.
                        let (groups, layers, nonidentity, distinct) =
                            crate::render::guirender::mesh::flat_item_mesh_decode_counts(&gui_mesh_batches);
                        if layers != 0 {
                            whole_frame_trace(&format!("whole-frame.gui-item-mesh-layers groups={} layers={}", groups, layers));
                            whole_frame_trace(&format!("whole-frame.gui-item-mesh-transforms layers={} nonidentity={} distinct={}", layers, nonidentity, distinct));
                        }
                    }
                    let item_layer_count = gui_affine_quads.iter().map(|quad|quad.item_raster_layers.len()).sum::<usize>();
                    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(
                        item_layer_count as u64 * size_of::<FfiGuiItemRasterLayer>() as u64);
                    if item_layer_count != 0 {
                        whole_frame_trace(&format!("whole-frame.gui-item-layers groups={} layers={}",
                            gui_affine_quads.iter().filter(|quad|!quad.item_raster_layers.is_empty()).count(),item_layer_count));
                        if std::env::var_os("MATTMC_TRACE_WHOLE_FRAME").is_some() {
                            let matrices=gui_affine_quads.iter().flat_map(|quad| &quad.item_raster_layers)
                                .map(|layer| layer.model_transform);
                            let mut distinct=BTreeSet::new();
                            let mut nonidentity=0;
                            for matrix in matrices {
                                nonidentity+=usize::from(matrix!=Default::default());
                                distinct.insert(matrix.0.map(|v| if v==0.0 {0} else {v.to_bits()}));
                            }
                            whole_frame_trace(&format!("whole-frame.gui-item-transforms layers={} nonidentity={} distinct={}",
                                item_layer_count,nonidentity,distinct.len()));
                        }
                    }
                    let ffi_decode_nanos =
                        crate::render::vulkanic::metrics::elapsed_nanos_u64(decode_started);
                    context
                        .world_primitive_frontend
                        .validate_post_effect_request_with_globals(
                            &post_effect_id,
                            world_frame.engine_globals,
                            context.gal.capabilities().shader_conventions,
                        )?;
                    whole_frame_trace(&format!(
                        "whole-frame.frontend.begin generation={} frame={} decode_nanos={}",
                        generation, world_frame_id, ffi_decode_nanos
                    ));
                    let frontend_started = std::time::Instant::now();
                    // Capture short menu transitions without sampling past the
                    // requested frame. Bound diagnostics across the process.
                    static TILED_RECEIPTS: std::sync::atomic::AtomicUsize =
                        std::sync::atomic::AtomicUsize::new(0);
                    let tiled_receipt = if !gui_tiled_quads.is_empty()
                        && std::env::var_os("MATTMC_TRACE_GUI_TILES").is_some()
                        && TILED_RECEIPTS.fetch_update(
                            std::sync::atomic::Ordering::Relaxed,
                            std::sync::atomic::Ordering::Relaxed,
                            |count| (count < 512).then_some(count + 1),
                        ).is_ok()
                    {
                        Some((gui_tiled_quads.len(),
                            crate::render::guirender::frontend::preflight_tiled_affine_count(
                                &gui_tiled_quads, 0)?))
                    } else { None };
                    let frontend_result = context
                        .world_primitive_frontend
                        .submit_whole_frame_with_tiled_gui_frontend(
                            &mut context.gal,
                            generation,
                            frame_target,
                            world_frame,
                            &mut context.gui_frontend,
                            gui_sprites,
                            gui_affine_quads,
                            gui_mesh_batches,
                            post_effect_id,
                            gui_blur_before_stratum,
                            gui_blur_radius,
                            gui_tiled_quads,
                        );
                    // Preserve the frontend's direct world-graph timer and
                    // report this outer boundary separately. The outer call
                    // also contains GUI lowering, GAL submission, and
                    // post-submit ownership confirmation.
                    let frontend_elapsed_nanos =
                        crate::render::vulkanic::metrics::elapsed_nanos_u64(frontend_started);
                    whole_frame_trace(&format!(
                        "whole-frame.frontend.end generation={} frame={} elapsed_nanos={}",
                        generation,
                        world_frame_id,
                        frontend_elapsed_nanos
                    ));
                    let (mut world_stats, gui_stats) = frontend_result?;
                    whole_frame_trace(&format!(
                        "whole-frame.gal-profile frame={} validate_ops_nanos={} validate_handles_nanos={} hazard_nanos={} encode_nanos={} queue_nanos={} submit_total_nanos={} ops_before={} ops_after={} hazard_candidates={} hazard_reads={} hazard_writes={} set_binds_removed={}",
                        world_frame_id,
                        world_stats.profile.gal.gal_validate_ops_nanos,
                        world_stats.profile.gal.gal_validate_handles_nanos,
                        world_stats.profile.gal.gal_hazard_analysis_nanos,
                        world_stats.profile.gal.backend_encode_nanos,
                        world_stats.profile.gal.backend_submit_nanos,
                        world_stats.profile.gal.gal_submit_total_nanos,
                        world_stats.profile.gal.gal_command_ops_before_normalize,
                        world_stats.profile.gal.gal_command_ops_after_normalize,
                        world_stats.profile.gal.gal_hazard_candidates_examined,
                        world_stats.profile.gal.gal_hazard_read_events,
                        world_stats.profile.gal.gal_hazard_write_events,
                        world_stats.profile.gal.gal_redundant_resource_set_binds_removed,
                    ));
                    whole_frame_trace(&format!(
                        "whole-frame.graph-profile frame={} validate_nanos={} batching_nanos={} mesh_group_nanos={} resources_nanos={} mesh_assets_nanos={} mesh_resources_nanos={} command_nanos={} stream_pack_nanos={} draw_record_nanos={} mesh_batches={} mesh_instances={}",
                        world_frame_id,
                        world_stats.profile.world_validate_frame_nanos,
                        world_stats.profile.world_batching_nanos,
                        world_stats.profile.world_mesh_section_expand_group_nanos,
                        world_stats.profile.world_resource_prepare_nanos,
                        world_stats.profile.world_prepare_mesh_material_asset_nanos,
                        world_stats.profile.world_prepare_mesh_resource_nanos,
                        world_stats.profile.gal.gal_command_generation_nanos,
                        world_stats.profile.world_mesh_stream_payload_pack_nanos,
                        world_stats.profile.world_mesh_draw_record_nanos,
                        world_stats.profile.world_prepare_mesh_batch_count,
                        world_stats.mesh_instance_count,
                    ));
                    whole_frame_trace(&format!(
                        "whole-frame.draw-profile frame={} draw_indexed={} draw_direct={} page_batches={} page_runs={} dynamic_terrain={} dynamic_other={} translucent_terrain={} stream_bytes={} resource_creates={} gpu_nanos={} gpu_status={}",
                        world_frame_id,
                        world_stats.profile.gal.draw_indexed_ops,
                        world_stats.profile.gal.draw_ops,
                        world_stats.profile.world_mesh_page_indirect_batch_count,
                        world_stats.profile.world_mesh_page_indirect_run_count,
                        world_stats.profile.world_mesh_dynamic_terrain_batch_count,
                        world_stats.profile.world_mesh_dynamic_non_terrain_batch_count,
                        world_stats.profile.world_mesh_terrain_translucent_batch_count,
                        world_stats.profile.world_mesh_stream_payload_bytes,
                        world_stats.profile.gal.resource_creates_delta,
                        world_stats.profile.gal.gpu_frame_total_nanos,
                        world_stats.profile.gal.gpu_timestamp_status,
                    ));
                    whole_frame_trace(&format!(
                        "whole-frame.gpu-profile frame={} total_nanos={} dh_opaque_nanos={} terrain_opaque_nanos={} terrain_cutout_nanos={} shadow_nanos={} deferred_nanos={} composite0_nanos={} composite1_nanos={} final_nanos={}",
                        world_frame_id,
                        world_stats.profile.gal.gpu_frame_total_nanos,
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::DISTANT_HORIZONS_OPAQUE)],
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::TERRAIN_OPAQUE)],
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::TERRAIN_CUTOUT)],
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::SHADOW_DEPTH)],
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::DEFERRED_LIGHTING)],
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::COMPOSITE_0)],
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::COMPOSITE_1)],
                        world_stats.profile.gal.gpu_scope_nanos[usize::from(crate::render::worldrender::diagnostics::gpu_profile_scopes::FINAL_OUTPUT)],
                    ));
                    if let Some((parents, children)) = tiled_receipt {
                        eprintln!("whole-frame.gui-tiles.submitted frame={} parents={} children={}",
                            world_frame_id, parents, children);
                    }
                    world_stats.profile.ffi_decode_nanos = ffi_decode_nanos;
                    world_stats.profile.whole_frame_native_total_nanos = frontend_elapsed_nanos;
                    world_stats.profile.gui_frontend_nanos = gui_stats.frontend_nanos;
                    world_stats.profile.gui_mesh_prepare_nanos = gui_stats.mesh_prepare_nanos;
                    world_stats.profile.gui_mesh_lower_nanos = gui_stats.mesh_lower_nanos;
                    whole_frame_trace(&format!(
                        "whole-frame.stale-targets.begin generation={} frame={}",
                        generation, world_frame_id
                    ));
                    destroy_stale_frame_targets(context)?;
                    whole_frame_trace(&format!(
                        "whole-frame.stale-targets.end generation={} frame={}",
                        generation, world_frame_id
                    ));
                    world_stats.command_lists = 1;
                    Ok((world_stats, gui_stats))
                },
            );
        match result {
            Ok((world_stats, gui_stats)) => {
                let value = whole_frame_result_ok(context, world_stats, gui_stats);
                let _ = write_out(out, value, "whole-frame submit result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiWholeFrameSubmitResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        metrics: context_metrics(context),
                        ..FfiWholeFrameSubmitResult::default()
                    },
                    "whole-frame submit result",
                );
                error.code as i32
            }
        }
    })
}

/// Emits phase boundaries only for an explicitly requested diagnostic run.
/// The ABI deliberately remains one call; this distinguishes semantic/GAL
/// work from stale-target retirement without changing rendering behavior.
fn whole_frame_trace(message: &str) {
    if std::env::var_os("MATTMC_TRACE_WHOLE_FRAME").is_some() {
        eprintln!("{message}");
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_primitives_submit(
    context_id: u64,
    request: *const FfiWholeFrameSubmitRequest,
    out: *mut FfiWholeFrameSubmitResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            let _ = write_out(
                out,
                FfiWholeFrameSubmitResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    ..FfiWholeFrameSubmitResult::default()
                },
                "world primitive submit result",
            );
            return error.code as i32;
        };
        let input_bytes = read_whole_frame_request(request)
            .as_ref()
            .map(input_bytes_for_whole_frame)
            .unwrap_or(0);
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiWholeFrameSubmitResult>() as u64);
        let result = decode_world_primitive_submit(request, context.gal.capabilities()).and_then(
            |(generation, frame_target, world_frame)| {
                let world_stats = context.world_primitive_frontend.submit_partial_frame(
                    &mut context.gal,
                    generation,
                    frame_target,
                    world_frame,
                )?;
                destroy_stale_frame_targets(context)?;
                Ok(world_stats)
            },
        );
        match result {
            Ok(world_stats) => {
                let value = whole_frame_result_ok(context, world_stats, GuiSubmitStats::default());
                let _ = write_out(out, value, "world primitive submit result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiWholeFrameSubmitResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        metrics: context_metrics(context),
                        ..FfiWholeFrameSubmitResult::default()
                    },
                    "world primitive submit result",
                );
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_border_update_asset(
    context_id: u64,
    request: *const FfiWorldBorderAssetUpdateRequest,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_world_border_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_world_border_asset_update(request, context.gal.capabilities())
            .and_then(|(generation, payload)| {
                context
                    .world_primitive_frontend
                    .apply_world_border_asset_update(&mut context.gal, generation, payload)
            });
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_text_update_images(
    context_id: u64,
    request: *const FfiWorldTextImageUpdateRequest,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        context.ffi_calls = context.ffi_calls.saturating_add(1);
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(if request.is_null() {
                0
            } else {
                input_bytes_for_world_text_image_update(&*request)
            });
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_world_text_image_update(request, context.gal.capabilities()).and_then(
            |(generation, assets)| {
                context
                    .world_primitive_frontend
                    .apply_world_text_image_update(generation, assets)
            },
        );
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_crack_update_assets(
    context_id: u64,
    request: *const FfiWorldCrackAssetUpdateRequest,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_world_crack_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_world_crack_asset_update(request, context.gal.capabilities()).and_then(
            |(generation, payloads)| {
                context
                    .world_primitive_frontend
                    .apply_world_crack_asset_update(&mut context.gal, generation, payloads)
            },
        );
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_lod_update_assets(
    context_id: u64,
    request: *const FfiWorldLodAssetUpdateRequest,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_world_lod_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let decode_started = std::time::Instant::now();
        let result = decode_world_lod_asset_update(request, context.gal.capabilities()).and_then(
            |(generation, assets, retirements, material_provenance)| {
                let decode_nanos = crate::render::vulkanic::metrics::elapsed_nanos_u64(decode_started);
                let asset_count = assets.len();
                let retirement_count = retirements.len();
                let provenance_count = material_provenance.len();
                let apply_started = std::time::Instant::now();
                let result = context
                    .world_primitive_frontend
                    .apply_world_lod_column_asset_update_with_provenance(
                        &mut context.gal,
                        generation,
                        assets,
                        retirements,
                        material_provenance,
                    );
                whole_frame_trace(&format!(
                    "whole-frame.dh-assets.native generation={generation} columns={asset_count} retirements={retirement_count} provenance={provenance_count} decode_nanos={decode_nanos} apply_nanos={}",
                    crate::render::vulkanic::metrics::elapsed_nanos_u64(apply_started),
                ));
                result
            },
        );
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}
