//! The world entry points Java calls: whole-frame and world-primitive submits and world asset updates.

use super::*;

/// Emits phase boundaries only for an explicitly requested diagnostic run
/// (`MATTMC_TRACE_WHOLE_FRAME`, read once); the message is formatted only then.
macro_rules! whole_frame_trace {
    ($($arg:tt)*) => {
        if whole_frame_trace_enabled() {
            crate::core::console::stderr(format_args!($($arg)*));
        }
    };
}


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
        let result = decode_whole_frame_request(context, request)
            .and_then(|decoded| execute_whole_frame(context, decoded));
        let (status, value) = whole_frame_status(context, result);
        let _ = write_out(out, value, "whole-frame submit result");
        status
    })
}

/// Pipelined `mattmc_vulkanic_gal_whole_frame_submit`: the context's frame
/// worker decodes, executes and presents the frame (see `bridge::pipeline`).
/// Java keeps the request memory alive and unmodified until it joins. `out`
/// reports only acceptance; the frame's submit and present results arrive
/// through `mattmc_vulkanic_gal_whole_frame_join`. A frame whose decode or
/// execution fails is cancelled on the worker, as Java would.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_whole_frame_submit_pipelined(
    context_id: u64,
    request: *const FfiWholeFrameSubmitRequest,
    present: *const FfiFramePresentRequest,
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
        let accepted = (|| -> GalResult<_> {
            let present = read_struct(present, "frame present request")?;
            validate_header::<FfiFramePresentRequest>(present.header)?;
            if request.is_null() {
                return Err(GalError::invalid_argument("pipelined whole-frame request is null"));
            }
            if !registry.pipelines.contains_key(&context_id) {
                registry.pipelines.insert(context_id, FramePipeline::new(context)?);
            }
            Ok(present)
        })();
        let request = RequestHandoff(request);
        let present = match accepted {
            Ok(accepted) => accepted,
            Err(error) => {
                let (status, value) = whole_frame_status(context, Err(error));
                let _ = write_out(out, value, "whole-frame submit result");
                return status;
            }
        };
        let handoff = ContextHandoff(&mut **context as *mut BridgeContext);
        let pipeline = registry.pipelines.get(&context_id).expect("pipeline inserted above");
        let dispatched = pipeline.dispatch(move |outcome| {
            let handoff = handoff;
            let request = request;
            // SAFETY: the context is handed to this job until a bridge entry
            // point joins it (see `bridge::pipeline`).
            let context = unsafe { &mut *handoff.0 };
            let _budget = crate::render::bridge::memory::RequestBudget::begin();
            // SAFETY: Java keeps the request memory alive and unmodified until
            // it joins this frame (`VulkanicGalBridge.pipelinedRequestArena`).
            let result = unsafe { decode_whole_frame_request(context, request.0) }
                .and_then(|decoded| execute_whole_frame(context, decoded));
            let (submit_status, submit) = whole_frame_status(context, result);
            let presented = if submit_status == StatusCode::Ok as i32 {
                let result = crate::render::bridge::frame::present_frame_and_retire(
                    context,
                    present.frame_id,
                    present.correlation_id,
                    submit.submission_id,
                );
                Some(match result {
                    Ok(presented) => (StatusCode::Ok as i32, presented),
                    Err(error) => {
                        set_last_error(context, &error);
                        (
                            error.code as i32,
                            FfiFramePresentResult {
                                status: error.code as i32,
                                error_domain: error.domain as u32,
                                ..FfiFramePresentResult::default()
                            },
                        )
                    }
                })
            } else {
                let _ = crate::render::bridge::frame::cancel_acquired_frame(context, present.frame_id);
                None
            };
            if let Ok(mut slot) = outcome.lock() {
                *slot = Some(PipelinedFrameOutcome {
                    submit_status,
                    submit,
                    present: presented,
                });
            }
        });
        match dispatched {
            Ok(()) => {
                let _ = write_out(
                    out,
                    FfiWholeFrameSubmitResult {
                        status: StatusCode::Ok as i32,
                        ..FfiWholeFrameSubmitResult::default()
                    },
                    "whole-frame submit result",
                );
                StatusCode::Ok as i32
            }
            Err(error) => {
                let (status, value) = whole_frame_status(context, Err(error));
                let _ = write_out(out, value, "whole-frame submit result");
                status
            }
        }
    })
}

/// Waits for the context's pipelined frame and returns its submit and present
/// results. `has_outcome` is zero when no pipelined frame completed since the
/// last join; `present_status` is the present call's status, or the submit's
/// status when the frame failed and was cancelled instead.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_whole_frame_join(
    context_id: u64,
    submit_out: *mut FfiWholeFrameSubmitResult,
    present_out: *mut FfiFramePresentResult,
    has_outcome: *mut u32,
) -> i32 {
    with_registry_mut(|registry| {
        if !registry.contexts.contains_key(&context_id) {
            return StatusCode::StaleHandle as i32;
        }
        let outcome = registry
            .pipelines
            .get(&context_id)
            .and_then(FramePipeline::take_outcome);
        let Some(outcome) = outcome else {
            let _ = write_out(has_outcome, 0u32, "pipelined frame presence");
            return StatusCode::Ok as i32;
        };
        let _ = write_out(has_outcome, 1u32, "pipelined frame presence");
        let _ = write_out(submit_out, outcome.submit, "whole-frame submit result");
        let present = outcome.present.map_or(
            FfiFramePresentResult {
                status: outcome.submit_status,
                ..FfiFramePresentResult::default()
            },
            |(_, present)| present,
        );
        let _ = write_out(present_out, present, "frame present result");
        StatusCode::Ok as i32
    })
}

/// Queues a whole frame behind the jobs already queued, without joining
/// them. Its job acquires the presentable image itself, executes the request
/// with that image's frame id and target, and presents it; Java keeps the
/// request memory alive until `mattmc_vulkanic_gal_whole_frame_join_queued`
/// returns this frame. A frame whose acquire yields no image (minimized or
/// resized surface) only records the acquire.
///
/// # Safety
/// `request` must stay valid and unmodified until this frame is joined.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_whole_frame_submit_queued(
    context_id: u64,
    request: *const FfiWholeFrameSubmitRequest,
    correlation_id: u64,
    width: u32,
    height: u32,
) -> i32 {
    let queued = with_queue(context_id, |pipeline| {
        if request.is_null() || width == 0 || height == 0 {
            return Err(GalError::invalid_argument("queued whole-frame request is incomplete"));
        }
        // Decode now, on the calling thread: Java's memory is not read after
        // this call returns. The image is acquired by the job, so the copy
        // names a placeholder target and frame until then.
        let mut copy = read_struct(request, "queued whole-frame request")?;
        copy.frame_target = FfiHandle::from(Handle::new(HandleKind::FrameTarget, 0, 1)?);
        copy.frame_id = correlation_id;
        copy.correlation_id = correlation_id;
        let decoded = decode_whole_frame_payload(&copy, pipeline.capabilities().clone())?;
        let handoff = pipeline.context_handoff();
        pipeline.enqueue(move |queue| {
            let handoff = handoff;
            // SAFETY: queued jobs run on the worker, which owns the context
            // until a bridge entry point joins (see `bridge::pipeline`).
            let context = unsafe { &mut *handoff.0 };
            let outcome = run_queued_frame(context, decoded, correlation_id, width, height);
            if let Ok(mut queue) = queue.lock() {
                queue.frames.push_back(outcome);
            }
        })
    });
    match queued {
        Ok(()) => StatusCode::Ok as i32,
        Err(error) => error.code as i32,
    }
}

/// Acquire, execute and present one queued frame on the worker.
fn run_queued_frame(
    context: &mut BridgeContext,
    mut decoded: DecodedWholeFrame,
    correlation_id: u64,
    width: u32,
    height: u32,
) -> QueuedFrameOutcome {
    let _budget = crate::render::bridge::memory::RequestBudget::begin();
    let extent = crate::render::vulkanic::resources::Extent3d { width, height, depth: 1 };
    let acquire = match crate::render::bridge::frame::acquire_frame_target(context, correlation_id, extent) {
        Ok(acquire) => acquire,
        Err(error) => {
            set_last_error(context, &error);
            return QueuedFrameOutcome {
                acquire_status: error.code as i32,
                acquire: FfiFrameAcquireResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    correlation_id,
                    ..FfiFrameAcquireResult::default()
                },
                frame: PipelinedFrameOutcome::default(),
            };
        }
    };
    let mut outcome = QueuedFrameOutcome {
        acquire_status: StatusCode::Ok as i32,
        acquire,
        frame: PipelinedFrameOutcome::default(),
    };
    if Handle::from(acquire.frame_target).is_null() {
        return outcome;
    }
    charge_whole_frame_decode(context, decoded.input_bytes);
    decoded.frame_target = Handle::from(acquire.frame_target);
    decoded.world_frame.frame_id = acquire.frame_id;
    let result = execute_whole_frame(context, decoded);
    let (submit_status, submit) = whole_frame_status(context, result);
    let present = if submit_status == StatusCode::Ok as i32 {
        let result = crate::render::bridge::frame::present_frame_and_retire(
            context,
            acquire.frame_id,
            correlation_id,
            submit.submission_id,
        );
        Some(match result {
            Ok(presented) => (StatusCode::Ok as i32, presented),
            Err(error) => {
                set_last_error(context, &error);
                (
                    error.code as i32,
                    FfiFramePresentResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        ..FfiFramePresentResult::default()
                    },
                )
            }
        })
    } else {
        let _ = crate::render::bridge::frame::cancel_acquired_frame(context, acquire.frame_id);
        None
    };
    outcome.frame = PipelinedFrameOutcome { submit_status, submit, present };
    outcome
}

/// Waits for the oldest queued frame (and the jobs queued before it) and
/// returns its acquire, submit and present results. `has_outcome` is zero
/// when no queued frame is outstanding. A queued asset update or animation
/// tick that failed before it is reported here as this call's status.
///
/// # Safety
/// The out pointers must be writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_whole_frame_join_queued(
    context_id: u64,
    acquire_out: *mut FfiFrameAcquireResult,
    submit_out: *mut FfiWholeFrameSubmitResult,
    present_out: *mut FfiFramePresentResult,
    has_outcome: *mut u32,
) -> i32 {
    let joined = with_queue(context_id, |pipeline| {
        let outcome = pipeline.next_queued_frame();
        Ok((outcome, pipeline.take_queued_error()))
    });
    let (outcome, error) = match joined {
        Ok(joined) => joined,
        Err(error) => return error.code as i32,
    };
    let Some(outcome) = outcome else {
        let _ = write_out(has_outcome, 0u32, "queued frame presence");
        return error.map_or(StatusCode::Ok as i32, |(status, _)| status);
    };
    let _ = write_out(has_outcome, 1u32, "queued frame presence");
    let _ = write_out(acquire_out, outcome.acquire, "frame acquire result");
    let _ = write_out(submit_out, outcome.frame.submit, "whole-frame submit result");
    let present = outcome.frame.present.map_or(
        FfiFramePresentResult {
            status: outcome.frame.submit_status,
            ..FfiFramePresentResult::default()
        },
        |(_, present)| present,
    );
    let _ = write_out(present_out, present, "frame present result");
    error.map_or(StatusCode::Ok as i32, |(status, _)| status)
}

/// A Java request pointer read by the frame worker before Java joins it.
struct RequestHandoff(*const FfiWholeFrameSubmitRequest);

// SAFETY: Java owns the memory and leaves it untouched until the join.
unsafe impl Send for RequestHandoff {}

/// One whole-frame request after it has been copied out of Java memory.
/// Nothing here borrows the request, so execution may outlive the call.
pub(super) struct DecodedWholeFrame {
    generation: u64,
    frame_target: Handle,
    world_frame: WorldPrimitiveFrame,
    gui_sprites: Vec<GuiSpriteRequest>,
    gui_affine_quads: Vec<GuiAffineQuadRequest>,
    gui_mesh_batches: Vec<GuiMeshBatchRequest>,
    gui_blur_before_stratum: i32,
    gui_blur_radius: i32,
    post_effect_id: Vec<u8>,
    gui_tiled_quads: Vec<GuiTiledQuadRequest>,
    ffi_decode_nanos: u64,
    /// Request bytes read, charged to the context that executes the frame.
    input_bytes: u64,
}

/// Charges and copies one whole-frame request. Runs on the calling thread
/// while the request memory is still valid.
pub(super) unsafe fn decode_whole_frame_request(
    context: &mut BridgeContext,
    request: *const FfiWholeFrameSubmitRequest,
) -> GalResult<DecodedWholeFrame> {
    let decoded = decode_whole_frame_payload(request, context.gal.capabilities());
    charge_whole_frame_decode(context, decoded.as_ref().map_or(0, |decoded| decoded.input_bytes));
    decoded
}

/// Records one decoded request's FFI traffic on its context.
fn charge_whole_frame_decode(context: &mut BridgeContext, input_bytes: u64) {
    context.ffi_calls += 1;
    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
    context.ffi_output_bytes = context
        .ffi_output_bytes
        .saturating_add(size_of::<FfiWholeFrameSubmitResult>() as u64);
}

/// Copies one whole-frame request out of Java memory without touching a
/// context, so a queued frame can be decoded on the calling thread.
unsafe fn decode_whole_frame_payload(
    request: *const FfiWholeFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<DecodedWholeFrame> {
    let mut input_bytes = read_whole_frame_request(request)
        .as_ref()
        .map(input_bytes_for_whole_frame)
        .unwrap_or(0);
    let decode_started = std::time::Instant::now();
    let (
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
    ) = decode_whole_frame_submit_with_tiled_gui(request, capabilities)?;
    if whole_frame_trace_enabled() {
        // Observe decoded native item meshes, not Java producer
        // counts. A group is the real scheduler item identity.
        let (groups, layers, nonidentity, distinct) =
            crate::render::guirender::mesh::flat_item_mesh_decode_counts(&gui_mesh_batches);
        if layers != 0 {
            whole_frame_trace!("whole-frame.gui-item-mesh-layers groups={} layers={}", groups, layers);
            whole_frame_trace!("whole-frame.gui-item-mesh-transforms layers={} nonidentity={} distinct={}", layers, nonidentity, distinct);
        }
    }
    let item_layer_count = gui_affine_quads.iter().map(|quad|quad.item_raster_layers.len()).sum::<usize>();
    input_bytes = input_bytes.saturating_add(
        item_layer_count as u64 * size_of::<FfiGuiItemRasterLayer>() as u64);
    if item_layer_count != 0 {
        whole_frame_trace!("whole-frame.gui-item-layers groups={} layers={}",
            gui_affine_quads.iter().filter(|quad|!quad.item_raster_layers.is_empty()).count(),item_layer_count);
        if whole_frame_trace_enabled() {
            let matrices=gui_affine_quads.iter().flat_map(|quad| &quad.item_raster_layers)
                .map(|layer| layer.model_transform);
            let mut distinct=BTreeSet::new();
            let mut nonidentity=0;
            for matrix in matrices {
                nonidentity+=usize::from(matrix!=Default::default());
                distinct.insert(matrix.0.map(|v| if v==0.0 {0} else {v.to_bits()}));
            }
            whole_frame_trace!("whole-frame.gui-item-transforms layers={} nonidentity={} distinct={}",
                item_layer_count,nonidentity,distinct.len());
        }
    }
    Ok(DecodedWholeFrame {
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
        ffi_decode_nanos: crate::render::vulkanic::metrics::elapsed_nanos_u64(decode_started),
        input_bytes,
    })
}

/// Executes one decoded whole frame: world and GUI frontends, GAL submission
/// and stale-target retirement. Touches only the context.
pub(super) fn execute_whole_frame(
    context: &mut BridgeContext,
    decoded: DecodedWholeFrame,
) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)> {
    let DecodedWholeFrame {
        generation,
        frame_target,
        mut world_frame,
        gui_sprites,
        gui_affine_quads,
        gui_mesh_batches,
        gui_blur_before_stratum,
        gui_blur_radius,
        post_effect_id,
        gui_tiled_quads,
        ffi_decode_nanos,
        input_bytes: _,
    } = decoded;
    let world_frame_id = world_frame.frame_id;
    context
        .world_primitive_frontend
        .validate_post_effect_request_with_globals(
            &post_effect_id,
            world_frame.engine_globals,
            context.gal.capabilities().shader_conventions,
        )?;
    whole_frame_trace!(
        "whole-frame.frontend.begin generation={} frame={} decode_nanos={}",
        generation, world_frame_id, ffi_decode_nanos
    );
    let frontend_started = std::time::Instant::now();
    // The armed shader route draws resident terrain from its scene straight
    // from the compact entries; every other route needs instances.
    let keep_compact = context.world_primitive_frontend.scene_terrain_available(&context.gal);
    context
        .world_primitive_frontend
        .admit_static_terrain(&mut world_frame, keep_compact)?;
    // Capture short menu transitions without sampling past the
    // requested frame. Bound diagnostics across the process.
    static TILED_RECEIPTS: std::sync::atomic::AtomicUsize =
        std::sync::atomic::AtomicUsize::new(0);
    let tiled_receipt = if !gui_tiled_quads.is_empty()
        && gui_tile_trace_enabled()
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
    whole_frame_trace!(
        "whole-frame.frontend.end generation={} frame={} elapsed_nanos={}",
        generation,
        world_frame_id,
        frontend_elapsed_nanos
    );
    let (mut world_stats, gui_stats) = frontend_result?;
    whole_frame_trace!(
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
    );
    whole_frame_trace!(
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
    );
    whole_frame_trace!(
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
    );
    whole_frame_trace!(
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
    );
    if let Some((parents, children)) = tiled_receipt {
        crate::core::console::stderr(format_args!("whole-frame.gui-tiles.submitted frame={} parents={} children={}",
            world_frame_id, parents, children));
    }
    world_stats.profile.ffi_decode_nanos = ffi_decode_nanos;
    world_stats.profile.whole_frame_native_total_nanos = frontend_elapsed_nanos;
    world_stats.profile.gui_frontend_nanos = gui_stats.frontend_nanos;
    world_stats.profile.gui_mesh_prepare_nanos = gui_stats.mesh_prepare_nanos;
    world_stats.profile.gui_mesh_lower_nanos = gui_stats.mesh_lower_nanos;
    whole_frame_trace!(
        "whole-frame.stale-targets.begin generation={} frame={}",
        generation, world_frame_id
    );
    destroy_stale_frame_targets(context)?;
    whole_frame_trace!(
        "whole-frame.stale-targets.end generation={} frame={}",
        generation, world_frame_id
    );
    world_stats.command_lists = 1;
    Ok((world_stats, gui_stats))
}

/// The status and result record Java receives for one whole-frame execution.
pub(super) fn whole_frame_status(
    context: &mut BridgeContext,
    result: GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)>,
) -> (i32, FfiWholeFrameSubmitResult) {
    match result {
        Ok((world_stats, gui_stats)) => (
            StatusCode::Ok as i32,
            whole_frame_result_ok(context, world_stats, gui_stats),
        ),
        Err(error) => {
            set_last_error(context, &error);
            (
                error.code as i32,
                FfiWholeFrameSubmitResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    metrics: context_metrics(context),
                    ..FfiWholeFrameSubmitResult::default()
                },
            )
        }
    }
}

/// Whether `MATTMC_TRACE_WHOLE_FRAME` requested phase tracing. The ABI stays
/// one call; tracing distinguishes semantic/GAL work from stale-target
/// retirement without changing rendering behavior.
fn whole_frame_trace_enabled() -> bool {
    static ENABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *ENABLED.get_or_init(|| std::env::var_os("MATTMC_TRACE_WHOLE_FRAME").is_some())
}

fn gui_tile_trace_enabled() -> bool {
    static ENABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *ENABLED.get_or_init(|| std::env::var_os("MATTMC_TRACE_GUI_TILES").is_some())
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
            |(generation, frame_target, mut world_frame)| {
                context
                    .world_primitive_frontend
                    .admit_static_terrain(&mut world_frame, false)?;
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
            read_struct(request, "input accounting").as_ref().map(input_bytes_for_world_border_asset_update).unwrap_or(0)
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
                read_struct(request, "input accounting").as_ref().map(input_bytes_for_world_text_image_update).unwrap_or(0)
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
            read_struct(request, "input accounting").as_ref().map(input_bytes_for_world_crack_asset_update).unwrap_or(0)
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
            read_struct(request, "input accounting").as_ref().map(input_bytes_for_world_lod_asset_update).unwrap_or(0)
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
                whole_frame_trace!(
                    "whole-frame.dh-assets.native generation={generation} columns={asset_count} retirements={retirement_count} provenance={provenance_count} decode_nanos={decode_nanos} apply_nanos={}",
                    crate::render::vulkanic::metrics::elapsed_nanos_u64(apply_started),
                );
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
