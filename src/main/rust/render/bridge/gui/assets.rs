//! GUI sprite asset and raw image updates: decoding and entry points.

use super::*;

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
    if assets.len() > GUI_MAX_RAW_IMAGES {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!(
                "GUI asset payload count {} exceeds bounded limit {GUI_MAX_RAW_IMAGES}",
                assets.len()
            ),
        ));
    }
    let mut seen = BTreeMap::new();
    let mut owned = Vec::with_capacity(assets.len());
    let mut png_bytes_total = 0usize;
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
        png_bytes_total = png_bytes_total
            .checked_add(asset.png_bytes.len as usize)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::LengthOverflow,
                    "GUI asset PNG byte count overflow",
                )
            })?;
        if png_bytes_total > GUI_MAX_RAW_IMAGE_BYTES_TOTAL {
            return Err(GalError::ffi(
                StatusCode::LengthOverflow,
                format!(
                    "GUI asset PNG bytes {} exceed bounded total {GUI_MAX_RAW_IMAGE_BYTES_TOTAL}",
                    png_bytes_total
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

pub(crate) unsafe fn decode_gui_raw_image_update(
    request: *const FfiGuiRawImageUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Vec<GuiRawImageAssetPayload>)> {
    let request = read_struct(request, "raw GUI image update request")?;
    validate_header::<FfiGuiRawImageUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported raw GUI image feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "raw GUI image generation must be non-zero",
        ));
    }
    let assets = read_limited_slice(request.assets, true, "raw GUI image payloads")?;
    if assets.len() > GUI_MAX_RAW_IMAGES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "raw GUI image payload count {} exceeds bounded limit {GUI_MAX_RAW_IMAGES}",
                assets.len()
            ),
        ));
    }
    let mut seen = BTreeMap::new();
    let mut owned = Vec::with_capacity(assets.len());
    let mut total_pixels = 0usize;
    for asset in assets {
        validate_item_size::<FfiGuiRawImageAssetPayload>(asset.byte_size, "raw GUI image payload")?;
        if asset.asset_id == 0 || seen.insert(asset.asset_id, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "raw GUI image asset ids must be unique and non-zero",
            ));
        }
        let format = match asset.format {
            1 => GuiRawImageSourceFormat::Alpha8,
            2 => GuiRawImageSourceFormat::Rgba8,
            3 => GuiRawImageSourceFormat::MapColor8,
            other => {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown raw GUI image format {other}"),
                ));
            }
        };
        let width = u32::try_from(asset.width).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                "raw GUI image width must be positive",
            )
        })?;
        let height = u32::try_from(asset.height).map_err(|_| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                "raw GUI image height must be positive",
            )
        })?;
        let pixel_count = usize::try_from(width)
            .ok()
            .and_then(|width| {
                usize::try_from(height)
                    .ok()
                    .and_then(|height| width.checked_mul(height))
            })
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::LengthOverflow,
                    "raw GUI image pixel count overflows",
                )
            })?;
        if pixel_count == 0 || pixel_count > GUI_MAX_RAW_IMAGE_PIXELS {
            return Err(GalError::ffi(
                StatusCode::LengthOverflow,
                format!(
                    "raw GUI image {} has {} pixels; maximum is {GUI_MAX_RAW_IMAGE_PIXELS}",
                    asset.asset_id, pixel_count
                ),
            ));
        }
        let bytes_per_pixel = format.bytes_per_pixel();
        let expected_bytes = pixel_count.checked_mul(bytes_per_pixel).ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "raw GUI image byte count overflows",
            )
        })?;
        let incoming_bytes = usize::try_from(asset.pixels.len).map_err(|_| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "raw GUI image byte length exceeds usize",
            )
        })?;
        if incoming_bytes != expected_bytes {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "raw GUI image {} has {} bytes; expected {expected_bytes}",
                    asset.asset_id, incoming_bytes
                ),
            ));
        }
        // Bound decoded residency too; indexed images expand in the frontend.
        let resident_bytes = pixel_count.checked_mul(match format.resident_format() {
            GuiRawImageFormat::Alpha8 => 1usize,
            GuiRawImageFormat::Rgba8 => 4usize,
        }).ok_or_else(|| GalError::ffi(StatusCode::LengthOverflow, "raw GUI resident image size overflows"))?;
        total_pixels = total_pixels.checked_add(resident_bytes).ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "raw GUI image aggregate byte count overflows",
            )
        })?;
        if total_pixels > GUI_MAX_RAW_IMAGE_BYTES_TOTAL {
            return Err(GalError::ffi(
                StatusCode::LengthOverflow,
                format!(
                    "raw GUI image aggregate bytes {total_pixels} exceed bounded limit {GUI_MAX_RAW_IMAGE_BYTES_TOTAL}"
                ),
            ));
        }
        let pixels = read_bounded_bytes(
            asset.pixels,
            true,
            FFI_MAX_GUI_ASSET_BYTES,
            "raw GUI image pixels",
        )?;
        owned.push(GuiRawImageAssetPayload {
            sampling: match (asset.sampling_filter, asset.sampling_address) {
                (0, 0) => None,
                (filter @ 1..=2, address @ 1..=2) => Some((
                    if filter == 1 {
                        crate::render::vulkanic::resources::SamplerFilter::Nearest
                    } else {
                        crate::render::vulkanic::resources::SamplerFilter::Linear
                    },
                    if address == 1 {
                        crate::render::vulkanic::resources::SamplerAddressMode::Repeat
                    } else {
                        crate::render::vulkanic::resources::SamplerAddressMode::ClampToEdge
                    },
                )),
                _ => {
                    return Err(GalError::invalid_argument(
                        "invalid explicit raw GUI image sampling",
                    ))
                }
            },
            asset_id: asset.asset_id,
            format,
            width,
            height,
            pixels,
        });
    }
    Ok((request.generation, owned))
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
            read_struct(request, "input accounting").as_ref().map(input_bytes_for_gui_asset_update).unwrap_or(0)
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

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_gui_update_raw_images(
    context_id: u64,
    request: *const FfiGuiRawImageUpdateRequest,
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
            read_struct(request, "input accounting").as_ref().map(input_bytes_for_gui_raw_image_update).unwrap_or(0)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_gui_raw_image_update(request, context.gal.capabilities()).and_then(
            |(generation, assets)| {
                let request = read_struct(request, "raw GUI retained identity manifest")?;
                context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(
                    assets.iter().map(|asset| asset.pixels.len() as u64).sum::<u64>());
                let retained = read_limited_slice(request.retained_asset_ids, true, "raw GUI retained identities")?;
                if retained.len() > GUI_MAX_RAW_IMAGES {
                    return Err(GalError::invalid_argument("raw GUI retained identity bound exceeded"));
                }
                context
                    .gui_frontend
                    .apply_raw_image_patch(&mut context.gal, generation, assets, retained.to_vec())
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
