//! Frame entry points: surface configuration, acquire, resize, present,
//! cancel, shutdown and frame-target capture.

use crate::render::bridge::*;

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_frame_configure(
    context_id: u64,
    request: *const FfiFrameSurfaceConfigRequest,
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
        context.ffi_calls += 1;
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(size_of::<FfiFrameSurfaceConfigRequest>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = (|| -> GalResult<()> {
            let request = read_struct(request, "frame configure request")?;
            validate_header::<FfiFrameSurfaceConfigRequest>(request.header)?;
            let desc = FrameSurfaceDesc {
                label: read_label(request.label, "frame surface label")?,
                extent: request.extent.into(),
                color_format: texture_format(request.color_format)?,
                present_mode: present_mode(request.present_mode)?,
                max_frames_in_flight: request.max_frames_in_flight,
            };
            context.gui_frontend.clear_frame_pass(&mut context.gal);
            context
                .world_primitive_frontend
                .clear_frame_pass(&mut context.gal);
            destroy_all_frame_targets(context)?;
            context.gal.configure_frame_surface(desc)
        })();
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
pub unsafe extern "C" fn mattmc_vulkanic_gal_frame_acquire(
    context_id: u64,
    request: *const FfiFrameAcquireRequest,
    out: *mut FfiFrameAcquireResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(size_of::<FfiFrameAcquireRequest>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiFrameAcquireResult>() as u64);
        let result = (|| -> GalResult<FfiFrameAcquireResult> {
            let request = read_struct(request, "frame acquire request")?;
            validate_header::<FfiFrameAcquireRequest>(request.header)?;
            let acquired = context.gal.acquire_frame(FrameAcquireDesc {
                correlation_id: FrameCorrelationId(request.correlation_id),
                expected_extent: request.expected_extent.into(),
            })?;
            let frame_target = if matches!(
                acquired.status,
                FrameAcquireStatus::Minimized | FrameAcquireStatus::Resized
            ) {
                Handle::NULL
            } else if let Some(cached) = context.frame_targets.get(&acquired.render_target) {
                cached.handle
            } else {
                let handle = context.gal.create_frame_target(FrameTargetDesc {
                    label: format!("ffi.frame-target.{}", acquired.frame.0),
                    frame_id: acquired.frame.0,
                    render_target: acquired.render_target,
                    extent: acquired.extent,
                    color_format: acquired.color_format,
                })?;
                context
                    .frame_targets
                    .insert(acquired.render_target, CachedFrameTarget { handle });
                handle
            };
            Ok(FfiFrameAcquireResult {
                status: StatusCode::Ok as i32,
                error_domain: 0,
                frame_id: acquired.frame.0,
                correlation_id: acquired.correlation_id.0,
                acquire_status: acquire_status_raw(acquired.status),
                frame_target: FfiHandle::from(frame_target),
                frame_target_identity: acquired.render_target.0,
                extent: acquired.extent.into(),
                color_format: acquired.color_format as u32,
                metrics: context_metrics(context),
                ..FfiFrameAcquireResult::default()
            })
        })();
        match result {
            Ok(value) => {
                let _ = write_out(out, value, "frame acquire result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiFrameAcquireResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        metrics: context_metrics(context),
                        ..FfiFrameAcquireResult::default()
                    },
                    "frame acquire result",
                );
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_frame_resize(
    context_id: u64,
    request: *const FfiFrameResizeRequest,
    out: *mut FfiFrameResizeResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(size_of::<FfiFrameResizeRequest>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiFrameResizeResult>() as u64);
        let result = (|| -> GalResult<FfiFrameResizeResult> {
            let request = read_struct(request, "frame resize request")?;
            validate_header::<FfiFrameResizeRequest>(request.header)?;
            context.gui_frontend.clear_frame_pass(&mut context.gal);
            context
                .world_primitive_frontend
                .clear_frame_pass(&mut context.gal);
            destroy_all_frame_targets(context)?;
            let resized = context.gal.resize_frame_surface(FrameResizeDesc {
                correlation_id: FrameCorrelationId(request.correlation_id),
                extent: request.extent.into(),
            })?;
            Ok(FfiFrameResizeResult {
                status: StatusCode::Ok as i32,
                resize_status: acquire_status_raw(resized.status),
                extent: resized.extent.into(),
                ..FfiFrameResizeResult::default()
            })
        })();
        match result {
            Ok(value) => {
                let _ = write_out(out, value, "frame resize result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiFrameResizeResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        ..FfiFrameResizeResult::default()
                    },
                    "frame resize result",
                );
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_frame_present(
    context_id: u64,
    request: *const FfiFramePresentRequest,
    out: *mut FfiFramePresentResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(size_of::<FfiFramePresentRequest>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiFramePresentResult>() as u64);
        let result = (|| -> GalResult<FfiFramePresentResult> {
            let request = read_struct(request, "frame present request")?;
            validate_header::<FfiFramePresentRequest>(request.header)?;
            present_frame_and_retire(context, request.frame_id, request.correlation_id, request.wait_submission_id)
        })();
        match result {
            Ok(value) => {
                let _ = write_out(out, value, "frame present result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiFramePresentResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        ..FfiFramePresentResult::default()
                    },
                    "frame present result",
                );
                error.code as i32
            }
        }
    })
}

/// Presents an acquired frame after `wait_submission_id` and polls completed
/// timeline work; shared by the synchronous and pipelined present paths.
pub(crate) fn present_frame_and_retire(
    context: &mut BridgeContext,
    frame_id: u64,
    correlation_id: u64,
    wait_submission_id: u64,
) -> GalResult<FfiFramePresentResult> {
    let presented = context.gal.present_frame(PresentFrameDesc {
        frame: VulkanicFrameId(frame_id),
        correlation_id: FrameCorrelationId(correlation_id),
        wait_for: SubmissionId(wait_submission_id),
    })?;
    // Vulkan presentation waits on the queue's render-finished
    // semaphore. Poll completed timeline work here so resource
    // retirement remains bounded without reintroducing a CPU wait for
    // the frame that was just queued for presentation.
    context.gal.retire_completed()?;
    if std::env::var_os("MATTMC_TRACE_SUBMISSIONS").is_some() {
        crate::core::console::stdout(format_args!(
            "vulkan.submission.present-retire frame={} waited={} retired_through={}",
            presented.frame.0, wait_submission_id, presented.completed_submission.0,
        ));
    }
    Ok(FfiFramePresentResult {
        status: StatusCode::Ok as i32,
        frame_id: presented.frame.0,
        correlation_id: presented.correlation_id.0,
        present_status: present_status_raw(presented.status),
        completed_submission_id: presented.completed_submission.0,
        frame_target_identity: presented.render_target.0,
        ..FfiFramePresentResult::default()
    })
}

/// Releases an acquired frame after a failed transaction; shared by the
/// cancel entry point and a failed pipelined frame.
pub(crate) fn cancel_acquired_frame(context: &mut BridgeContext, frame_id: u64) -> GalResult<()> {
    context
        .gal
        .cancel_frame(crate::render::vulkanic::frame::FrameId(frame_id))?;
    context
        .gui_frontend
        .discard_prepared_post_effects(&mut context.gal);
    // The swapchain recreation waits for device quiescence, so all
    // cached frame-target wrappers are now safe to retire as well.
    destroy_all_frame_targets(context)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_frame_cancel(
    context_id: u64,
    request: *const FfiFrameCancelRequest,
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
        context.ffi_calls += 1;
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(size_of::<FfiFrameCancelRequest>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = (|| -> GalResult<()> {
            let request = read_struct(request, "frame cancel request")?;
            validate_header::<FfiFrameCancelRequest>(request.header)?;
            cancel_acquired_frame(context, request.frame_id)
        })();
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
pub unsafe extern "C" fn mattmc_vulkanic_gal_frame_shutdown(
    context_id: u64,
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
        context.ffi_calls += 1;
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        if let Err(error) = context.gui_frontend.reset(&mut context.gal) {
            set_last_error(context, &error);
            write_status_out(status_out, status_error(Some(context), &error));
            return error.code as i32;
        }
        context.world_primitive_frontend.reset(&mut context.gal);
        if let Err(error) = destroy_all_frame_targets(context) {
            set_last_error(context, &error);
            write_status_out(status_out, status_error(Some(context), &error));
            return error.code as i32;
        }
        match context.gal.shutdown_frame_surface() {
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

/// Copies one completed frame target (the image about to be presented, GUI
/// included) into host memory for a user screenshot. This is a rare,
/// explicitly requested readback: it records its own copy after the frame's
/// submission on the same queue and waits for that copy only. Java receives
/// plain RGBA8 bytes (bottom-up rows follow the frame target's orientation)
/// and never touches a backend image.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_frame_capture(
    context_id: u64,
    frame_target: u64,
    out_bytes: *mut u8,
    out_capacity: u64,
    out_meta: *mut u64,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
        context.ffi_calls += 1;
        let result = capture_frame_target(&mut context.gal, Handle::from_raw(frame_target));
        match result {
            Ok((width, height, bytes)) => {
                if out_meta.is_null() {
                    return StatusCode::InvalidArgument as i32;
                }
                unsafe {
                    *out_meta = u64::from(width);
                    *out_meta.add(1) = u64::from(height);
                    *out_meta.add(2) = bytes.len() as u64;
                }
                if out_bytes.is_null() || (bytes.len() as u64) > out_capacity {
                    // The caller learns the required size from out_meta.
                    return StatusCode::InvalidArgument as i32;
                }
                unsafe {
                    std::ptr::copy_nonoverlapping(bytes.as_ptr(), out_bytes, bytes.len());
                }
                context.ffi_output_bytes =
                    context.ffi_output_bytes.saturating_add(bytes.len() as u64);
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                error.code as i32
            }
        }
    })
}

fn capture_frame_target(gal: &mut VulkanicGal, target: Handle) -> GalResult<(u32, u32, Vec<u8>)> {
    let desc = gal.frame_target_desc(target)?;
    let (width, height) = (desc.extent.width, desc.extent.height);
    let swizzle_bgra = match desc.color_format {
        TextureFormat::Rgba8Unorm => false,
        TextureFormat::Bgra8Unorm => true,
        other => {
            return Err(GalError::unsupported_feature(format!(
                "frame capture does not support frame-target format {other:?}"
            )))
        }
    };
    let byte_count = u64::from(width) * u64::from(height) * 4;
    let extent = Extent3d { width, height, depth: 1 };
    let texture = gal.create_texture(TextureDesc {
        label: "frame-capture.texture".into(),
        dimension: TextureDimension::D2,
        format: desc.color_format,
        extent,
        mip_levels: 1,
        array_layers: 1,
        usages: vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
    })?;
    let readback = match gal.create_buffer(BufferDesc {
        label: "frame-capture.readback".into(),
        size: byte_count,
        memory: MemoryDomain::Readback,
        usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
    }) {
        Ok(buffer) => buffer,
        Err(error) => {
            let _ = gal.destroy(texture);
            return Err(error);
        }
    };
    let barrier = |resource, before, after| {
        CommandOp::Barrier(ResourceBarrier {
            resource,
            subresources: None,
            before,
            after,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Graphics,
        })
    };
    let operations = vec![
        barrier(texture, TextureUsageState::Undefined, TextureUsageState::TransferDst),
        CommandOp::CopyFrameTargetToTexture { src: target, dst: texture, extent },
        barrier(texture, TextureUsageState::TransferDst, TextureUsageState::TransferSrc),
        CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: width * 4,
            rows_per_image: height,
            texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }),
        barrier(readback, TextureUsageState::TransferDst, TextureUsageState::TransferSrc),
        CommandOp::HostReadBuffer { buffer: readback, offset: 0, size: byte_count },
    ];
    let submitted = gal.submit(SubmissionBatch {
        label: "frame-capture.submit".into(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "frame-capture.commands".into(),
            operations,
        })],
    });
    let result = submitted.and_then(|token| {
        gal.retire_through(token.submission)?;
        gal.completed_host_reads()
            .into_iter()
            .find(|read| read.buffer == readback && read.submission == token.submission)
            .ok_or_else(|| GalError::backend("frame capture readback did not complete"))
            .map(|read| read.bytes)
    });
    let _ = gal.destroy(readback);
    let _ = gal.destroy(texture);
    let mut bytes = result?;
    if bytes.len() as u64 != byte_count {
        return Err(GalError::backend("frame capture readback has an unexpected size"));
    }
    if swizzle_bgra {
        for pixel in bytes.chunks_exact_mut(4) {
            pixel.swap(0, 2);
        }
    }
    Ok((width, height, bytes))
}
