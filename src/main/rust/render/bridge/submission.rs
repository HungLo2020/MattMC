//! Raw command submission: decoding command lists and ops, and the submit,
//! completion-query, retirement and readback entry points.

use crate::render::bridge::*;

pub unsafe fn decode_submission_batch(
    batch: *const FfiSubmissionBatchAbi,
    capabilities: BackendCapabilities,
) -> GalResult<SubmissionBatch> {
    let batch = read_struct(batch, "submission batch")?;
    validate_header::<FfiSubmissionBatchAbi>(batch.header)?;
    reject_unknown_feature_bits(batch.negotiated_feature_bits)?;
    require_negotiated_features(batch.negotiated_feature_bits, capabilities)?;
    let lists = read_limited_slice(batch.command_lists, false, "command lists")?;
    let ops = read_limited_slice(batch.operations, false, "command operations")?;
    let attachments = read_limited_slice(batch.pass_attachments, true, "pass attachments")?;
    let copy_regions = read_limited_slice(batch.copy_regions, true, "copy regions")?;
    let barriers = read_limited_slice(batch.barriers, true, "barriers")?;
    let input_items = lists
        .len()
        .checked_add(ops.len())
        .and_then(|count| count.checked_add(attachments.len()))
        .and_then(|count| count.checked_add(copy_regions.len()))
        .and_then(|count| count.checked_add(barriers.len()))
        .ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "submission input table count overflows",
            )
        })?;
    if input_items > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            "submission input table count exceeds ABI maximum",
        ));
    }
    if lists.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "submission requires at least one command list",
        ));
    }
    let mut command_lists = Vec::with_capacity(lists.len());
    let mut total_ops = 0usize;
    for list in lists {
        validate_item_size::<FfiCommandListAbi>(list.byte_size, "command list")?;
        let op_items = range_slice(ops, list.operations, "command list operations")?;
        if op_items.is_empty() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "command list must contain at least one operation",
            ));
        }
        total_ops = total_ops.checked_add(op_items.len()).ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "command operation count overflow",
            )
        })?;
        if op_items.len() > capabilities.limits.max_commands_per_list as usize {
            return Err(GalError::unsupported_feature(format!(
                "command list operation count {} exceeds backend '{}' limit {}",
                op_items.len(),
                capabilities.name,
                capabilities.limits.max_commands_per_list
            )));
        }
        let mut operations = Vec::with_capacity(op_items.len());
        for op in op_items {
            validate_item_size::<FfiCommandOpAbi>(op.byte_size, "command operation")?;
            operations.push(decode_command_op(
                op,
                attachments,
                copy_regions,
                barriers,
                capabilities,
            )?);
        }
        command_lists.push(CommandList::from(CommandListDesc {
            label: read_label(list.label, "command list label")?,
            operations,
        }));
    }
    if lists.len() > capabilities.limits.max_command_lists_per_submission as usize {
        return Err(GalError::unsupported_feature(format!(
            "submission command list count {} exceeds backend '{}' limit {}",
            lists.len(),
            capabilities.name,
            capabilities.limits.max_command_lists_per_submission
        )));
    }
    if total_ops > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            "submission operation count exceeds ABI maximum",
        ));
    }
    Ok(SubmissionBatch {
        label: read_label(batch.label, "submission label")?,
        command_lists,
    })
}

pub unsafe fn validate_completion_query(
    request: *const FfiCompletionQueryRequest,
) -> GalResult<FfiCompletionQueryRequest> {
    let request = read_struct(request, "completion query")?;
    validate_header::<FfiCompletionQueryRequest>(request.header)?;
    if request.submission_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "completion query requires a non-zero submission id",
        ));
    }
    Ok(request)
}

pub fn completion_result_for(
    requested: SubmissionId,
    completed: SubmissionId,
) -> FfiCompletionResult {
    FfiCompletionResult {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiCompletionResult>() as u32,
        },
        status: StatusCode::Ok as i32,
        error_domain: 0,
        requested_submission_id: requested.0,
        completed_submission_id: completed.0,
        is_complete: u32::from(completed >= requested),
    }
}

pub unsafe fn decode_retirement_batch(
    batch: *const FfiRetirementBatch,
) -> GalResult<(SubmissionId, Vec<Handle>)> {
    let batch = read_struct(batch, "retirement batch")?;
    validate_header::<FfiRetirementBatch>(batch.header)?;
    let handles = read_limited_slice(batch.handles, true, "retirement handles")?;
    let mut owned = Vec::with_capacity(handles.len());
    for handle in handles {
        owned.push(require_any_handle(*handle, "retirement handle")?);
    }
    Ok((SubmissionId(batch.completed_submission_id), owned))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_submit_batch(
    context_id: u64,
    batch: *const FfiSubmissionBatchAbi,
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
        let input_bytes = if batch.is_null() {
            0
        } else {
            read_struct(batch, "input accounting").as_ref().map(input_bytes_for_submission).unwrap_or(0)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = match decode_submission_batch(batch, context.gal.capabilities())
            .and_then(|batch| context.gal.submit(batch))
        {
            Ok(token) => {
                let mut status = status_ok(context);
                status.submission_id = token.submission.0;
                write_status_out(status_out, status);
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        };
        result
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_completion_query(
    context_id: u64,
    request: *const FfiCompletionQueryRequest,
    out: *mut FfiCompletionResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(size_of::<FfiCompletionQueryRequest>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiCompletionResult>() as u64);
        let result = match validate_completion_query(request) {
            Ok(request) => {
                let requested = SubmissionId(request.submission_id);
                let latest = context.gal.latest_submission_id();
                if requested > latest {
                    let error = GalError::submission(
                        StatusCode::InvalidArgument,
                        format!(
                            "completion query requested submission {} but latest submitted is {}",
                            requested.0, latest.0
                        ),
                    );
                    set_last_error(context, &error);
                    let _ = write_out(
                        out,
                        FfiCompletionResult {
                            header: FfiHeader {
                                version: FFI_ABI_VERSION,
                                byte_size: size_of::<FfiCompletionResult>() as u32,
                            },
                            status: error.code as i32,
                            error_domain: error.domain as u32,
                            requested_submission_id: requested.0,
                            completed_submission_id: context.gal.poll_completed().0,
                            is_complete: 0,
                        },
                        "completion result",
                    );
                    return error.code as i32;
                }
                let completed = context.gal.poll_completed();
                let result = completion_result_for(requested, completed);
                let _ = write_out(out, result, "completion result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiCompletionResult {
                        header: FfiHeader {
                            version: FFI_ABI_VERSION,
                            byte_size: size_of::<FfiCompletionResult>() as u32,
                        },
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        requested_submission_id: 0,
                        completed_submission_id: 0,
                        is_complete: 0,
                    },
                    "completion result",
                );
                error.code as i32
            }
        };
        result
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_retire(
    context_id: u64,
    batch: *const FfiRetirementBatch,
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
            .saturating_add(size_of::<FfiRetirementBatch>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = match decode_retirement_batch(batch)
            .and_then(|(id, _handles)| context.gal.retire_through(id))
        {
            Ok(retired) => {
                let mut status = status_ok(context);
                status.metrics.retired_resources = retired.len() as u64;
                write_status_out(status_out, status);
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        };
        result
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_readback(
    context_id: u64,
    request: *const FfiReadbackRequest,
    out_bytes: *mut u8,
    out_capacity: u64,
    out: *mut FfiReadbackResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context
            .ffi_input_bytes
            .saturating_add(size_of::<FfiReadbackRequest>() as u64);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiReadbackResult>() as u64)
            .saturating_add(out_capacity);
        let result = (|| -> GalResult<FfiReadbackResult> {
            let request = read_struct(request, "readback request")?;
            validate_header::<FfiReadbackRequest>(request.header)?;
            let buffer = require_handle(request.buffer, HandleKind::Buffer, "readback buffer")?;
            context
                .gal
                .retire_through(SubmissionId(request.submission_id))?;
            let reads = context.gal.completed_host_reads();
            let Some(read) = reads.iter().rev().find(|read| {
                read.submission == SubmissionId(request.submission_id)
                    && read.buffer == buffer
                    && read.offset == request.offset
            }) else {
                return Err(GalError::submission(
                    StatusCode::InvalidArgument,
                    "requested readback was not produced by this submission",
                ));
            };
            let requested_size = usize::try_from(request.size).map_err(|_| {
                GalError::ffi(
                    StatusCode::LengthOverflow,
                    "readback size does not fit usize",
                )
            })?;
            let bytes = &read.bytes[..read.bytes.len().min(requested_size)];
            if out_capacity < bytes.len() as u64 {
                return Err(GalError::ffi(
                    StatusCode::LengthOverflow,
                    format!(
                        "readback output capacity {out_capacity} is less than required {}",
                        bytes.len()
                    ),
                ));
            }
            if !bytes.is_empty() {
                if out_bytes.is_null() {
                    return Err(GalError::ffi(
                        StatusCode::NullPointer,
                        "readback output pointer is null",
                    ));
                }
                ptr::copy_nonoverlapping(bytes.as_ptr(), out_bytes, bytes.len());
            }
            let mut result = FfiReadbackResult {
                submission_id: request.submission_id,
                required_bytes: bytes.len() as u64,
                written_bytes: bytes.len() as u64,
                metrics: context_metrics(context),
                ..FfiReadbackResult::default()
            };
            result.metrics.ffi_output_bytes = result
                .metrics
                .ffi_output_bytes
                .saturating_add(bytes.len() as u64);
            Ok(result)
        })();
        let status = match result {
            Ok(result) => {
                let _ = write_out(out, result, "readback result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let mut result = FfiReadbackResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    metrics: context_metrics(context),
                    ..FfiReadbackResult::default()
                };
                if let Ok(request) = read_struct(request, "readback request") {
                    result.submission_id = request.submission_id;
                    result.required_bytes = request.size;
                }
                let _ = write_out(out, result, "readback result");
                error.code as i32
            }
        };
        status
    })
}

pub(crate) fn decode_command_op(
    op: &FfiCommandOpAbi,
    attachments: &[FfiPassAttachmentAbi],
    copy_regions: &[FfiBufferImageCopyAbi],
    barriers: &[FfiResourceBarrierAbi],
    capabilities: BackendCapabilities,
) -> GalResult<CommandOp> {
    match op.op_kind {
        1 => {
            require_feature(capabilities, BackendFeature::Graphics, "render pass")?;
            let colors = range_slice(attachments, op.colors, "begin pass color attachments")?
                .iter()
                .map(decode_color_pass_attachment)
                .collect::<GalResult<Vec<_>>>()?;
            let depth_items =
                range_slice(attachments, op.depth_stencil, "begin pass depth attachment")?;
            if depth_items.len() > 1 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "begin pass may specify at most one depth attachment",
                ));
            }
            check_attachment_count(colors.len(), !depth_items.is_empty(), capabilities)?;
            Ok(CommandOp::BeginPass {
                pass: require_handle(op.primary, HandleKind::RenderPass, "begin pass pass")?,
                target: require_handle_any(
                    op.secondary,
                    &[HandleKind::RenderTarget, HandleKind::FrameTarget],
                    "begin pass target",
                )?,
                colors,
                depth_stencil: depth_items
                    .first()
                    .map(decode_texture_view_pass_attachment)
                    .transpose()?,
            })
        }
        2 => Ok(CommandOp::BindGraphicsPipeline(require_handle(
            op.primary,
            HandleKind::GraphicsPipeline,
            "graphics pipeline",
        )?)),
        3 => {
            require_feature(capabilities, BackendFeature::Compute, "compute pipeline")?;
            Ok(CommandOp::BindComputePipeline(require_handle(
                op.primary,
                HandleKind::ComputePipeline,
                "compute pipeline",
            )?))
        }
        4 => Ok(CommandOp::BindResourceSet {
            pipeline_layout: require_handle(
                op.primary,
                HandleKind::PipelineLayout,
                "resource set pipeline layout",
            )?,
            set_index: op.set_index,
            set: require_handle(op.secondary, HandleKind::ResourceSet, "resource set")?,
            dynamic_offsets: Vec::new(),
        }),
        5 => Ok(CommandOp::SetVertexBuffer {
            slot: op.slot,
            buffer: require_handle(op.primary, HandleKind::Buffer, "vertex buffer")?,
            offset: op.offset,
        }),
        6 => Ok(CommandOp::SetIndexBuffer {
            buffer: require_handle(op.primary, HandleKind::Buffer, "index buffer")?,
            offset: op.offset,
            index_type: ffi_index_type(op.slot)?,
        }),
        7 => Ok(CommandOp::Draw {
            vertices: op.count0,
            instances: op.count1,
        }),
        8 => Ok(CommandOp::DrawIndexed {
            indices: op.count0,
            instances: op.count1,
        }),
        9 => {
            require_feature(capabilities, BackendFeature::IndirectDraw, "indirect draw")?;
            Ok(CommandOp::DrawIndirect {
                buffer: require_handle(op.primary, HandleKind::Buffer, "indirect draw buffer")?,
                offset: op.offset,
                draw_count: op.count0,
            })
        }
        20 => {
            require_feature(
                capabilities,
                BackendFeature::IndirectDraw,
                "indexed indirect draw",
            )?;
            Ok(CommandOp::DrawIndexedIndirect {
                buffer: require_handle(
                    op.primary,
                    HandleKind::Buffer,
                    "indexed indirect draw buffer",
                )?,
                offset: op.offset,
                draw_count: op.count0,
            })
        }
        10 => Ok(CommandOp::Dispatch {
            groups_x: op.count0,
            groups_y: op.count1,
            groups_z: op.count2,
        }),
        11 => {
            require_feature(
                capabilities,
                BackendFeature::IndirectDispatch,
                "indirect dispatch",
            )?;
            Ok(CommandOp::DispatchIndirect {
                buffer: require_handle(op.primary, HandleKind::Buffer, "indirect dispatch buffer")?,
                offset: op.offset,
            })
        }
        12 => Ok(CommandOp::CopyBuffer {
            src: require_handle(op.primary, HandleKind::Buffer, "copy buffer src")?,
            dst: require_handle(op.secondary, HandleKind::Buffer, "copy buffer dst")?,
            size: op.size,
        }),
        13 => {
            require_feature(
                capabilities,
                BackendFeature::TextureSubresourceCopies,
                "buffer-to-texture copy",
            )?;
            Ok(CommandOp::CopyBufferToTexture(decode_copy_region(
                single_range_item(
                    copy_regions,
                    op.copy_region,
                    "buffer-to-texture copy region",
                )?,
            )?))
        }
        14 => {
            require_feature(
                capabilities,
                BackendFeature::TextureSubresourceCopies,
                "texture-to-buffer copy",
            )?;
            Ok(CommandOp::CopyTextureToBuffer(decode_copy_region(
                single_range_item(
                    copy_regions,
                    op.copy_region,
                    "texture-to-buffer copy region",
                )?,
            )?))
        }
        15 => {
            require_feature(
                capabilities,
                BackendFeature::HostBufferAccess,
                "host write buffer",
            )?;
            Ok(CommandOp::HostWriteBuffer {
                buffer: require_handle(op.primary, HandleKind::Buffer, "host write buffer")?,
                offset: op.offset,
                data: unsafe {
                    read_bounded_bytes(
                        op.inline_bytes,
                        false,
                        FFI_MAX_INLINE_BYTES,
                        "host write data",
                    )?
                },
            })
        }
        16 => {
            require_feature(
                capabilities,
                BackendFeature::HostBufferAccess,
                "host read buffer",
            )?;
            Ok(CommandOp::HostReadBuffer {
                buffer: require_handle(op.primary, HandleKind::Buffer, "host read buffer")?,
                offset: op.offset,
                size: op.size,
            })
        }
        17 => Err(GalError::unsupported_feature(
            "present commands are outside submission batches; use ABI v2 frame present",
        )),
        18 => Ok(CommandOp::Barrier(decode_barrier(single_range_item(
            barriers, op.barrier, "barrier",
        )?)?)),
        19 => Ok(CommandOp::EndPass),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown command op kind {}", op.op_kind),
        )),
    }
}

pub(crate) fn single_range_item<'a, T>(
    items: &'a [T],
    range: FfiRange,
    label: &str,
) -> GalResult<&'a T> {
    let slice = range_slice(items, range, label)?;
    if slice.len() != 1 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} range must contain exactly one item"),
        ));
    }
    Ok(&slice[0])
}

pub(crate) fn decode_color_pass_attachment(
    item: &FfiPassAttachmentAbi,
) -> GalResult<PassAttachment> {
    validate_item_size::<FfiPassAttachmentAbi>(item.byte_size, "pass attachment")?;
    Ok(PassAttachment {
        view: require_handle_any(
            item.view,
            &[HandleKind::TextureView, HandleKind::FrameTarget],
            "color pass attachment view",
        )?,
        load_op: load_op(item.load_op)?,
        store_op: store_op(item.store_op)?,
        clear_color: if bool_flag(item.has_clear_color, "attachment clear color presence")? {
            Some(item.clear_color.into())
        } else {
            None
        },
    })
}

pub(crate) fn decode_texture_view_pass_attachment(
    item: &FfiPassAttachmentAbi,
) -> GalResult<PassAttachment> {
    validate_item_size::<FfiPassAttachmentAbi>(item.byte_size, "pass attachment")?;
    Ok(PassAttachment {
        view: require_handle(item.view, HandleKind::TextureView, "pass attachment view")?,
        load_op: load_op(item.load_op)?,
        store_op: store_op(item.store_op)?,
        clear_color: if bool_flag(item.has_clear_color, "attachment clear color presence")? {
            Some(item.clear_color.into())
        } else {
            None
        },
    })
}

pub(crate) fn decode_copy_region(item: &FfiBufferImageCopyAbi) -> GalResult<BufferImageCopyRegion> {
    validate_item_size::<FfiBufferImageCopyAbi>(item.byte_size, "buffer image copy")?;
    Ok(BufferImageCopyRegion {
        buffer: require_handle(item.buffer, HandleKind::Buffer, "copy buffer")?,
        buffer_offset: item.buffer_offset,
        bytes_per_row: item.bytes_per_row,
        rows_per_image: item.rows_per_image,
        texture: require_handle(item.texture, HandleKind::Texture, "copy texture")?,
        texture_mip: item.texture_mip,
        texture_layer: item.texture_layer,
        texture_origin: item.texture_origin.into(),
        extent: item.extent.into(),
    })
}

pub(crate) fn decode_barrier(item: &FfiResourceBarrierAbi) -> GalResult<ResourceBarrier> {
    validate_item_size::<FfiResourceBarrierAbi>(item.byte_size, "resource barrier")?;
    if item.stage_bits != 0 || item.access_bits != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "resource barrier stage/access bits are deprecated; use semantic before/after usage states",
        ));
    }
    Ok(ResourceBarrier {
        resource: require_any_handle(item.resource, "barrier resource")?,
        subresources: if bool_flag(item.has_subresources, "barrier subresource presence")? {
            Some(item.subresources.into())
        } else {
            None
        },
        before: texture_usage_state(item.before)?,
        after: texture_usage_state(item.after)?,
        src_queue: queue_class(item.src_queue)?,
        dst_queue: queue_class(item.dst_queue)?,
    })
}

pub(crate) fn unsupported_feature_from_message(message: &str) -> u32 {
    for (feature, needle) in [
        (BackendFeature::Compute, "compute"),
        (BackendFeature::StorageTextures, "storage texture"),
        (BackendFeature::IndirectDraw, "indirect draw"),
        (BackendFeature::IndirectDispatch, "indirect dispatch"),
        (BackendFeature::MultipleColorAttachments, "multiple color"),
        (BackendFeature::DepthOnlyPass, "depth-only"),
        (BackendFeature::BlendedPass, "blend"),
        (BackendFeature::TextureMipLevels, "mip"),
        (BackendFeature::TextureArrayLayers, "layer"),
        (BackendFeature::Presentation, "presentation"),
    ] {
        if message.contains(needle) {
            return feature as u32;
        }
    }
    0
}

#[allow(dead_code)]
pub(crate) fn _abi_status_domain_value(domain: ErrorDomain) -> u32 {
    domain as u32
}
