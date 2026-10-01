//! GUI frame submits: decoding, request-sequence validation and the entry point.

use super::*;

#[cfg(test)]
pub(crate) unsafe fn decode_gui_frame_submit(
    request: *const FfiGuiFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(u64, Handle, Vec<GuiSpriteRequest>)> {
    let (generation, frame_target, sprites, _) =
        decode_gui_frame_submit_with_affine(request, capabilities)?;
    Ok((generation, frame_target, sprites))
}

#[cfg(test)]
pub(crate) unsafe fn decode_gui_frame_submit_with_affine(
    request: *const FfiGuiFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Handle,
    Vec<GuiSpriteRequest>,
    Vec<GuiAffineQuadRequest>,
)> {
    let (generation, frame_target, sprites, affine_quads, mesh_batches) =
        decode_gui_frame_submit_with_mesh(request, capabilities)?;
    if !mesh_batches.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batches require the mesh-aware frame submit path",
        ));
    }
    Ok((generation, frame_target, sprites, affine_quads))
}

#[cfg(test)]
pub(crate) unsafe fn decode_gui_frame_submit_with_mesh(
    request: *const FfiGuiFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Handle,
    Vec<GuiSpriteRequest>,
    Vec<GuiAffineQuadRequest>,
    Vec<GuiMeshBatchRequest>,
)> {
    let (generation, target, sprites, affine, meshes, tiles) =
        decode_gui_frame_submit_with_tiles(request, capabilities)?;
    if !tiles.is_empty() {
        return Err(GalError::invalid_argument(
            "tiled GUI requires the typed frame submit path",
        ));
    }
    Ok((generation, target, sprites, affine, meshes))
}

pub(crate) unsafe fn decode_gui_frame_submit_with_tiles(
    request: *const FfiGuiFrameSubmitRequest,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Handle,
    Vec<GuiSpriteRequest>,
    Vec<GuiAffineQuadRequest>,
    Vec<GuiMeshBatchRequest>,
    Vec<GuiTiledQuadRequest>,
)> {
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
    if request.gui_width <= 0
        || request.gui_height <= 0
        || request.gui_width > GUI_MAX_VIEWPORT_AXIS
        || request.gui_height > GUI_MAX_VIEWPORT_AXIS
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI frame submit dimensions {}x{} exceed bounded positive axis {}",
                request.gui_width, request.gui_height, GUI_MAX_VIEWPORT_AXIS
            ),
        ));
    }
    let frame_target = Handle::from(request.frame_target);
    let projection_extent = [request.gui_projection_width, request.gui_projection_height];
    crate::render::guirender::frontend::validate_gui_projection(
        [request.gui_width as u32, request.gui_height as u32],
        projection_extent,
    )?;
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
            projection_extent,
            sequence: sprite.sequence,
        });
    }
    let mut affine_quads =
        decode_gui_affine_quads(request.affine_quads, request.gui_width, request.gui_height)?;
    let mut mesh_batches =
        decode_gui_mesh_batches(request.mesh_batches, request.gui_width, request.gui_height)?;
    for quad in &mut affine_quads {
        quad.projection_extent = projection_extent;
    }
    for mesh in &mut mesh_batches {
        mesh.projection_extent = projection_extent;
    }
    let tiled_quads = decode_gui_tiled_quads(
        request.tiled_quads,
        [request.gui_width as u32, request.gui_height as u32],
        projection_extent,
        affine_quads.len(),
    )?;
    crate::render::guirender::frontend::validate_gui_frame_sequences(
        &owned,
        &affine_quads,
        &mesh_batches,
        &tiled_quads,
    )?;
    Ok((
        request.generation,
        frame_target,
        owned,
        affine_quads,
        mesh_batches,
        tiled_quads,
    ))
}

#[cfg(test)]
pub(super) fn validate_gui_request_sequences(
    sprites: &[GuiSpriteRequest],
    affine_quads: &[GuiAffineQuadRequest],
    mesh_batches: &[GuiMeshBatchRequest],
) -> GalResult<()> {
    let mut sequences = Vec::with_capacity(sprites.len() + affine_quads.len());
    sequences.extend(sprites.iter().map(|request| request.sequence));
    sequences.extend(affine_quads.iter().map(|request| request.sequence));
    // Layers intentionally share their item's sequence. The mesh frontend
    // validates contiguous layer indices and treats them as one ordered item.
    let mesh_sequences = mesh_batches
        .iter()
        .map(|request| request.sequence)
        .collect::<std::collections::BTreeSet<_>>();
    sequences.extend(mesh_sequences);
    if sequences.iter().any(|sequence| *sequence == 0) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI requests require non-zero scheduler sequences",
        ));
    }
    sequences.sort_unstable();
    if sequences.windows(2).any(|pair| pair[0] == pair[1]) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI request scheduler sequences must be unique within one frame",
        ));
    }
    Ok(())
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
        let result = decode_gui_frame_submit_with_tiles(request, context.gal.capabilities())
            .and_then(
                |(generation, frame_target, sprites, affine_quads, mesh_batches, tiled_quads)| {
                    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(
                        affine_quads
                            .iter()
                            .map(|quad| {
                                quad.item_raster_layers.len() as u64
                                    * size_of::<FfiGuiItemRasterLayer>() as u64
                            })
                            .sum::<u64>(),
                    );
                    let stats = context.gui_frontend.submit_frame_with_owned_atlases(
                        &mut context.gal,
                        Some(&mut context.world_primitive_frontend),
                        generation,
                        frame_target,
                        sprites,
                        affine_quads,
                        mesh_batches,
                        tiled_quads,
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
