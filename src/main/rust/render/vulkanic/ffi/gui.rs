use super::*;

pub(crate) unsafe fn decode_gui_frame_submit(
    request: *const FfiGuiFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Handle, Vec<GuiSpriteRequest>)> {
    let request = read_struct(request, "GUI frame submit request")?;
    validate_header::<FfiGuiFrameSubmitRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported GUI feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 || request.frame_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI frame submit requires non-zero generation and frame id",
        ));
    }
    if request.gui_width <= 0 || request.gui_height <= 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI frame submit requires positive GUI dimensions, got {}x{}",
                request.gui_width, request.gui_height
            ),
        ));
    }
    let frame_target = Handle::from(request.frame_target);
    if frame_target.is_null() || frame_target.kind() != Some(HandleKind::FrameTarget) {
        return Err(GalError::ffi(
            StatusCode::WrongHandleType,
            "GUI frame submit requires a frame-target handle",
        ));
    }
    let sprites = read_slice(request.sprites, true, "GUI sprite requests")?;
    if sprites.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI frame submit sprite count {} exceeds max {}",
                sprites.len(),
                FFI_MAX_BATCH_ITEMS
            ),
        ));
    }
    let mut owned = Vec::with_capacity(sprites.len());
    for sprite in sprites {
        if sprite.byte_size as usize != size_of::<FfiGuiSpriteRequest>() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "GUI sprite byte size mismatch: got {}, expected {}",
                    sprite.byte_size,
                    size_of::<FfiGuiSpriteRequest>()
                ),
            ));
        }
        if sprite.gui_width != request.gui_width || sprite.gui_height != request.gui_height {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI sprite dimensions must match frame GUI dimensions",
            ));
        }
        let to_u32 = |value: i32, field: &str| -> GalResult<u32> {
            u32::try_from(value).map_err(|_| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!("GUI sprite {field} must be non-negative, got {value}"),
                )
            })
        };
        owned.push(GuiSpriteRequest {
            stratum: sprite.stratum,
            sprite_id: sprite.sprite_id,
            selected_slot: sprite.selected_slot,
            progress_fraction: sprite.progress_fraction,
            fill_direction: sprite.fill_direction,
            color_argb: sprite.color_argb,
            x: sprite.x,
            y: sprite.y,
            width: to_u32(sprite.width, "width")?,
            height: to_u32(sprite.height, "height")?,
            gui_width: to_u32(sprite.gui_width, "gui_width")?,
            gui_height: to_u32(sprite.gui_height, "gui_height")?,
        });
    }
    Ok((request.generation, frame_target, owned))
}

pub(crate) unsafe fn decode_gui_asset_update(
    request: *const FfiGuiAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Vec<GuiAssetPayload>)> {
    let request = read_struct(request, "GUI asset update request")?;
    validate_header::<FfiGuiAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported GUI asset feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI asset generation must be non-zero",
        ));
    }
    let assets = read_limited_slice(request.assets, true, "GUI asset payloads")?;
    let mut seen = BTreeMap::new();
    let mut owned = Vec::with_capacity(assets.len());
    for asset in assets {
        validate_item_size::<FfiGuiAssetPayload>(asset.byte_size, "GUI asset payload")?;
        if seen.insert(asset.sprite_id, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "duplicate GUI asset payload for sprite id {}",
                    asset.sprite_id
                ),
            ));
        }
        let png_bytes = read_bounded_bytes(
            asset.png_bytes,
            true,
            FFI_MAX_GUI_ASSET_BYTES,
            "GUI asset PNG bytes",
        )?;
        owned.push(GuiAssetPayload {
            sprite_id: asset.sprite_id,
            png_bytes,
        });
    }
    Ok((request.generation, owned))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_gui_submit_frame(
    context_id: u64,
    request: *const FfiGuiFrameSubmitRequest,
    out: *mut FfiGuiFrameSubmitResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            let _ = write_out(
                out,
                FfiGuiFrameSubmitResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    ..FfiGuiFrameSubmitResult::default()
                },
                "GUI frame submit result",
            );
            return error.code as i32;
        };
        let input_bytes = if request.is_null() {
            0
        } else {
            input_bytes_for_gui_frame(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiGuiFrameSubmitResult>() as u64);
        let result = decode_gui_frame_submit(request, context.gal.capabilities()).and_then(
            |(generation, frame_target, sprites)| {
                let stats = context.gui_frontend.submit_frame(
                    &mut context.gal,
                    generation,
                    frame_target,
                    sprites,
                )?;
                destroy_stale_frame_targets(context)?;
                Ok(stats)
            },
        );
        match result {
            Ok(stats) => {
                let value = gui_frame_result_ok(context, stats);
                let _ = write_out(out, value, "GUI frame submit result");
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                let _ = write_out(
                    out,
                    FfiGuiFrameSubmitResult {
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        metrics: context_metrics(context),
                        ..FfiGuiFrameSubmitResult::default()
                    },
                    "GUI frame submit result",
                );
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_gui_update_assets(
    context_id: u64,
    request: *const FfiGuiAssetUpdateRequest,
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
            input_bytes_for_gui_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_gui_asset_update(request, context.gal.capabilities()).and_then(
            |(generation, assets)| {
                context
                    .gui_frontend
                    .apply_asset_update(&mut context.gal, generation, assets)
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
