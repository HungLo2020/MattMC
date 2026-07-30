use super::*;
use crate::render::vulkanic::world_primitive_frontend::material as world_material_semantics;

pub(crate) unsafe fn decode_world_material_asset_update(
    request: *const FfiWorldMaterialAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Vec<WorldMaterialAssetPayload>)> {
    let request = read_struct(request, "world material asset update request")?;
    validate_header::<FfiWorldMaterialAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported world material asset feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world material asset generation must be non-zero",
        ));
    }
    let raw_assets = read_limited_slice(request.assets, true, "world material asset payloads")?;
    let mut seen = BTreeMap::new();
    let mut assets = Vec::with_capacity(raw_assets.len());
    for asset in raw_assets {
        validate_item_size::<FfiWorldMaterialAssetPayload>(
            asset.byte_size,
            "world material asset payload",
        )?;
        let texture_id = world_material_semantics::canonical_texture_id(asset.texture_id)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown world material texture id {}", asset.texture_id),
                )
            })?;
        if seen.insert(texture_id, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "duplicate world material asset payload for texture {}",
                    texture_id
                ),
            ));
        }
        let png_bytes = read_bounded_bytes(
            asset.png_bytes,
            true,
            FFI_MAX_WORLD_MATERIAL_ASSET_BYTES,
            "world material asset PNG bytes",
        )?;
        assets.push(WorldMaterialAssetPayload {
            texture_id,
            png_bytes,
        });
    }
    Ok((request.generation, assets))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_material_update_assets(
    context_id: u64,
    request: *const FfiWorldMaterialAssetUpdateRequest,
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
            input_bytes_for_world_material_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_world_material_asset_update(request, context.gal.capabilities())
            .and_then(|(generation, payloads)| {
                context
                    .world_primitive_frontend
                    .apply_world_material_asset_update(&mut context.gal, generation, payloads)
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
