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

pub(crate) unsafe fn decode_world_mesh_asset_update(
    request: *const FfiWorldMeshAssetUpdateRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Vec<WorldMeshAsset>,
    Vec<WorldMeshTextureAssetPayload>,
    Vec<WorldMeshSortedIndexUpdate>,
)> {
    let request = read_struct(request, "world mesh asset update request")?;
    validate_header::<FfiWorldMeshAssetUpdateRequest>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.negotiated_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported world mesh asset feature bits 0x{:x}",
            request.negotiated_feature_bits & !supported
        )));
    }
    if request.generation == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh asset generation must be non-zero",
        ));
    }
    let raw_textures = read_limited_slice(request.textures, true, "world mesh texture payloads")?;
    let mut seen_textures = BTreeMap::new();
    let mut textures = Vec::with_capacity(raw_textures.len());
    for texture in raw_textures {
        validate_item_size::<FfiWorldMeshTextureAssetPayload>(
            texture.byte_size,
            "world mesh texture payload",
        )?;
        if texture.texture_id == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh texture id must be non-zero",
            ));
        }
        if seen_textures.insert(texture.texture_id, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("duplicate world mesh texture {}", texture.texture_id),
            ));
        }
        textures.push(WorldMeshTextureAssetPayload {
            texture_id: texture.texture_id,
            png_bytes: read_bounded_bytes(
                texture.png_bytes,
                true,
                FFI_MAX_WORLD_MESH_TEXTURE_ASSET_BYTES,
                "world mesh texture PNG bytes",
            )?,
        });
    }
    let raw_meshes = read_limited_slice(request.meshes, true, "world mesh assets")?;
    let mut seen_meshes = BTreeMap::new();
    let mut meshes = Vec::with_capacity(raw_meshes.len());
    for mesh in raw_meshes {
        validate_item_size::<FfiWorldMeshAssetRecord>(mesh.byte_size, "world mesh asset")?;
        if mesh.mesh_key == 0 || mesh.mesh_generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh asset key and generation must be non-zero",
            ));
        }
        if seen_meshes.insert(mesh.mesh_key, ()).is_some() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("duplicate world mesh asset {}", mesh.mesh_key),
            ));
        }
        let index_type = ffi_index_type(mesh.index_type)?;
        let raw_vertices = read_limited_slice(mesh.vertices, false, "world mesh vertices")?;
        let mut vertices = Vec::with_capacity(raw_vertices.len());
        for vertex in raw_vertices {
            validate_item_size::<FfiWorldMeshVertex>(vertex.byte_size, "world mesh vertex")?;
            vertices.push(WorldMeshVertex {
                position: [vertex.x, vertex.y, vertex.z],
                uv: [vertex.u, vertex.v],
                shader_atlas_uv: [vertex.atlas_u, vertex.atlas_v],
                shader_block_id: vertex.shader_block_id,
                shader_material_type: vertex.shader_material_type,
                color_argb: vertex.color_argb,
                normal_packed: vertex.normal_packed,
                light: vertex.light,
            });
        }
        let index_bytes = read_bounded_bytes(
            mesh.index_bytes,
            false,
            FFI_MAX_WORLD_MESH_INDEX_BYTES,
            "world mesh index bytes",
        )?;
        let raw_sections = read_limited_slice(mesh.sections, false, "world mesh sections")?;
        let mut sections = Vec::with_capacity(raw_sections.len());
        for section in raw_sections {
            validate_item_size::<FfiWorldMeshSectionRecord>(
                section.byte_size,
                "world mesh section",
            )?;
            let material_id = world_material_semantics::canonical_material_id(section.material_id)
                .ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::UnknownEnum,
                        format!("unknown world mesh material id {}", section.material_id),
                    )
                })?;
            sections.push(WorldMeshSection {
                material_id,
                texture_id: section.texture_id,
                material_mode: section.material_mode,
                cull_policy: section.cull_policy,
                winding: section.winding,
                index_offset: section.index_offset,
                index_count: section.index_count,
            });
        }
        meshes.push(WorldMeshAsset {
            mesh_key: mesh.mesh_key,
            mesh_generation: mesh.mesh_generation,
            vertex_layout_version: mesh.vertex_layout_version,
            index_type,
            vertices,
            index_bytes,
            sections,
        });
    }
    let raw_sorted_indices =
        read_limited_slice(request.sorted_indices, true, "world mesh sorted index updates")?;
    let mut sorted_indices = Vec::with_capacity(raw_sorted_indices.len());
    for update in raw_sorted_indices {
        validate_item_size::<FfiWorldMeshSortedIndexRecord>(
            update.byte_size,
            "world mesh sorted index update",
        )?;
        if update.mesh_key == 0 || update.mesh_generation == 0 || update.index_generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh sorted index update key and generations must be non-zero",
            ));
        }
        sorted_indices.push(WorldMeshSortedIndexUpdate {
            mesh_key: update.mesh_key,
            mesh_generation: update.mesh_generation,
            index_generation: update.index_generation,
            index_type: ffi_index_type(update.index_type)?,
            index_bytes: read_bounded_bytes(
                update.index_bytes,
                false,
                FFI_MAX_WORLD_MESH_INDEX_BYTES,
                "world mesh sorted index bytes",
            )?,
        });
    }
    Ok((request.generation, meshes, textures, sorted_indices))
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

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_world_mesh_update_assets(
    context_id: u64,
    request: *const FfiWorldMeshAssetUpdateRequest,
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
            input_bytes_for_world_mesh_asset_update(&*request)
        };
        context.ffi_calls += 1;
        context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_world_mesh_asset_update(request, context.gal.capabilities()).and_then(
            |(generation, meshes, textures, sorted_indices)| {
                context
                    .world_primitive_frontend
                    .apply_world_mesh_asset_update_with_sorted(
                        &mut context.gal,
                        generation,
                        meshes,
                        textures,
                        sorted_indices,
                    )
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
