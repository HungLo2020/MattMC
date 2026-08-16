use super::*;
use crate::render::vulkanic::shader_pack::assets::{ShaderPackAssetFile, ShaderPackAssetUpdate};
use crate::render::vulkanic::shader_pack::source::{ShaderPackSourceUpdate, ShaderSourceFile};

pub(crate) unsafe fn decode_shader_pack_source_update(
    request: *const FfiShaderPackSourceUpdateRequest,
) -> GalResult<ShaderPackSourceUpdate> {
    let request = read_struct(request, "shader-pack source update request")?;
    validate_header::<FfiShaderPackSourceUpdateRequest>(request.header)?;
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader-pack source generation must be non-zero",
        ));
    }
    let pack_name_bytes = read_bounded_bytes(
        request.pack_name_utf8,
        false,
        FFI_MAX_LABEL_BYTES,
        "shader-pack source pack name",
    )?;
    let pack_name = decode_utf8(&pack_name_bytes, "shader-pack source pack name")?;
    // An empty, explicitly named generation clears a previously selected pack
    // without leaving stale source eligible for a later runtime admission.
    let raw_files = read_limited_slice(request.files, true, "shader-pack source files")?;
    if raw_files.len() > FFI_MAX_SHADER_PACK_SOURCE_FILES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "shader-pack source file count {} exceeds {}",
                raw_files.len(),
                FFI_MAX_SHADER_PACK_SOURCE_FILES
            ),
        ));
    }
    let mut total_bytes = 0usize;
    let mut files = Vec::with_capacity(raw_files.len());
    for file in raw_files {
        validate_item_size::<FfiShaderPackSourceFile>(file.byte_size, "shader-pack source file")?;
        if file.reserved0 != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "shader-pack source file reserved field must be zero",
            ));
        }
        let path_bytes = read_bounded_bytes(
            file.path_utf8,
            false,
            FFI_MAX_LABEL_BYTES,
            "shader-pack source path",
        )?;
        let path = decode_utf8(&path_bytes, "shader-pack source path")?;
        let contents_bytes = read_bounded_bytes(
            file.contents_utf8,
            true,
            FFI_MAX_SHADER_PACK_SOURCE_FILE_BYTES,
            "shader-pack source contents",
        )?;
        let contents = decode_utf8(&contents_bytes, "shader-pack source contents")?;
        total_bytes = total_bytes.checked_add(contents.len()).ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "shader-pack source aggregate byte count overflows",
            )
        })?;
        if total_bytes > FFI_MAX_SHADER_PACK_SOURCE_TOTAL_BYTES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "shader-pack source aggregate bytes exceed {}",
                    FFI_MAX_SHADER_PACK_SOURCE_TOTAL_BYTES
                ),
            ));
        }
        files.push(ShaderSourceFile::new(path, contents));
    }
    Ok(ShaderPackSourceUpdate {
        pack_name,
        generation: request.generation,
        files,
    })
}

/// Copies one complete binary shader-pack asset generation. This transport is
/// intentionally distinct from source text: callers cannot smuggle compiled
/// programs, renderer objects, or backend handles through a byte payload.
pub(crate) unsafe fn decode_shader_pack_asset_update(
    request: *const FfiShaderPackAssetUpdateRequest,
) -> GalResult<ShaderPackAssetUpdate> {
    let request = read_struct(request, "shader-pack asset update request")?;
    validate_header::<FfiShaderPackAssetUpdateRequest>(request.header)?;
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "shader-pack asset generation must be non-zero",
        ));
    }
    let pack_name_bytes = read_bounded_bytes(
        request.pack_name_utf8,
        false,
        FFI_MAX_LABEL_BYTES,
        "shader-pack asset pack name",
    )?;
    let pack_name = decode_utf8(&pack_name_bytes, "shader-pack asset pack name")?;
    let raw_files = read_limited_slice(request.files, true, "shader-pack asset files")?;
    if raw_files.len() > FFI_MAX_SHADER_PACK_ASSET_FILES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "shader-pack asset file count {} exceeds {}",
                raw_files.len(),
                FFI_MAX_SHADER_PACK_ASSET_FILES
            ),
        ));
    }
    let mut total_bytes = 0usize;
    let mut files = Vec::with_capacity(raw_files.len());
    for file in raw_files {
        validate_item_size::<FfiShaderPackAssetFile>(file.byte_size, "shader-pack asset file")?;
        if file.reserved0 != 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "shader-pack asset file reserved field must be zero",
            ));
        }
        let path_bytes = read_bounded_bytes(
            file.path_utf8,
            false,
            FFI_MAX_LABEL_BYTES,
            "shader-pack asset path",
        )?;
        let path = decode_utf8(&path_bytes, "shader-pack asset path")?;
        let contents = read_bounded_bytes(
            file.contents,
            true,
            FFI_MAX_SHADER_PACK_ASSET_FILE_BYTES,
            "shader-pack asset contents",
        )?;
        total_bytes = total_bytes.checked_add(contents.len()).ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "shader-pack asset aggregate byte count overflows",
            )
        })?;
        if total_bytes > FFI_MAX_SHADER_PACK_ASSET_TOTAL_BYTES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "shader-pack asset aggregate bytes exceed {}",
                    FFI_MAX_SHADER_PACK_ASSET_TOTAL_BYTES
                ),
            ));
        }
        files.push(ShaderPackAssetFile::new(path, contents));
    }
    Ok(ShaderPackAssetUpdate {
        pack_name,
        generation: request.generation,
        files,
    })
}

fn decode_utf8(bytes: &[u8], label: &str) -> GalResult<String> {
    std::str::from_utf8(bytes)
        .map(str::to_owned)
        .map_err(|_| GalError::ffi(StatusCode::InvalidArgument, format!("{label} is not UTF-8")))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_shader_pack_update_sources(
    context_id: u64,
    request: *const FfiShaderPackSourceUpdateRequest,
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
            input_bytes_for_shader_pack_source_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_shader_pack_source_update(request).and_then(|update| {
            context
                .world_primitive_frontend
                .apply_shader_pack_source_update(update)?;
            context
                .world_primitive_frontend
                .retire_source_final_outputs_for_shader_reload(&mut context.gal);
            Ok(())
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
pub unsafe extern "C" fn mattmc_vulkanic_gal_shader_pack_update_assets(
    context_id: u64,
    request: *const FfiShaderPackAssetUpdateRequest,
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
            input_bytes_for_shader_pack_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_shader_pack_asset_update(request).and_then(|update| {
            context
                .world_primitive_frontend
                .apply_shader_pack_asset_update(update)
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
