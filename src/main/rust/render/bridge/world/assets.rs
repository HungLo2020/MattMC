//! World text image, border and crack asset updates.

use super::*;

pub(crate) unsafe fn decode_world_text_image_update(
    request: *const FfiWorldTextImageUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Vec<WorldTextImageAsset>)> {
    let request = read_struct(request, "world text image update request")?;
    validate_header::<FfiWorldTextImageUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    if request.negotiated_feature_bits & !capability_feature_bits(capabilities) != 0 {
        return Err(GalError::unsupported_feature(
            "world text image update requests unsupported backend features",
        ));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world text image update generation must be non-zero",
        ));
    }
    let raw_assets = read_limited_slice(request.assets, true, "world text image assets")?;
    if raw_assets.len() > MAX_WORLD_TEXT_IMAGES {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!(
                "world text image asset count {} exceeds bounded limit {MAX_WORLD_TEXT_IMAGES}",
                raw_assets.len()
            ),
        ));
    }
    let mut assets = Vec::with_capacity(raw_assets.len());
    let mut total_bytes = 0usize;
    for asset in raw_assets {
        validate_item_size::<FfiWorldTextImageAssetPayload>(
            asset.byte_size,
            "world text image asset",
        )?;
        let pixels = read_bounded_bytes(
            asset.pixels,
            true,
            4 * 1024 * 1024,
            "world text image pixels",
        )?;
        total_bytes = total_bytes.checked_add(pixels.len()).ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "world text image pixel byte count overflow",
            )
        })?;
        if total_bytes > MAX_WORLD_TEXT_IMAGE_BYTES_TOTAL {
            return Err(GalError::ffi(
                StatusCode::LengthOverflow,
                format!(
                    "world text image pixels {} exceed bounded total {MAX_WORLD_TEXT_IMAGE_BYTES_TOTAL}",
                    total_bytes
                ),
            ));
        }
        assets.push(WorldTextImageAsset {
            asset_id: asset.asset_id,
            atlas_generation: asset.atlas_generation,
            atlas_revision: asset.atlas_revision,
            format: WorldTextImageFormat::try_from(asset.format)?,
            width: asset.width,
            height: asset.height,
            pixels,
        });
    }
    Ok((request.generation, assets))
}

pub(crate) unsafe fn decode_world_border_asset_update(
    request: *const FfiWorldBorderAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, WorldBorderAssetPayload)> {
    let request = read_struct(request, "world-border asset update request")?;
    validate_header::<FfiWorldBorderAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported world-border asset feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world-border asset generation must be non-zero",
        ));
    }
    let png_bytes = read_bounded_bytes(
        request.png_bytes,
        true,
        FFI_MAX_WORLD_BORDER_ASSET_BYTES,
        "world-border texture PNG bytes",
    )?;
    Ok((
        request.generation,
        WorldBorderAssetPayload {
            texture_id: request.texture_id,
            png_bytes,
        },
    ))
}

pub(crate) unsafe fn decode_world_crack_asset_update(
    request: *const FfiWorldCrackAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Vec<WorldCrackAssetPayload>)> {
    let request = read_struct(request, "world crack asset update request")?;
    validate_header::<FfiWorldCrackAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported world crack asset feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world crack asset generation must be non-zero",
        ));
    }
    let raw_assets = read_limited_slice(request.assets, true, "world crack asset payloads")?;
    let mut seen = BTreeMap::new();
    let mut assets = Vec::with_capacity(raw_assets.len());
    for asset in raw_assets {
        validate_item_size::<FfiWorldCrackAssetPayload>(
            asset.byte_size,
            "world crack asset payload",
        )?;
        if asset.stage >= 10 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world crack stage {}", asset.stage),
            ));
        }
        if seen.insert(asset.stage, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "duplicate world crack asset payload for stage {}",
                    asset.stage
                ),
            ));
        }
        let png_bytes = read_bounded_bytes(
            asset.png_bytes,
            true,
            FFI_MAX_WORLD_CRACK_ASSET_BYTES,
            "world crack asset PNG bytes",
        )?;
        assets.push(WorldCrackAssetPayload {
            stage: asset.stage,
            png_bytes,
        });
    }
    Ok((request.generation, assets))
}
