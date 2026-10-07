//! GUI mesh batch decoding and item-layer compaction.

use super::*;

/// Stable `GuiMeshBatchRecord.materialMode` ABI value for title panoramas.
pub(crate) const GUI_MESH_MATERIAL_PANORAMA: u32 = 5;

/// Decodes the private coarse GUI mesh family. Callers may use this only once
/// the owned GUI mesh pass is available; defining the transport does not arm a
/// route or expose a backend capability.
pub(crate) unsafe fn decode_gui_mesh_batches(
    raw: FfiSlice<FfiGuiMeshBatchRequest>,
    gui_width: i32,
    gui_height: i32,
) -> GalResult<Vec<GuiMeshBatchRequest>> {
    let batches = read_slice(raw, true, "GUI mesh batch requests")?;
    if batches.len() > GUI_MESH_MAX_BATCHES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh batch count {} exceeds max {}",
                batches.len(),
                GUI_MESH_MAX_BATCHES
            ),
        ));
    }
    // Inspect every nested slice descriptor before copying any geometry. The
    // per-batch limits alone would otherwise allow their product to exceed a
    // safe frame-sized allocation.
    let mut payload_bytes = 0_u64;
    for batch in batches {
        let vertices = read_slice(batch.vertices, true, "GUI mesh vertices")?;
        let indices = read_slice(batch.indices, true, "GUI mesh indices")?;
        if vertices.len() > GUI_MESH_MAX_VERTICES || indices.len() > GUI_MESH_MAX_INDICES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh payload exceeds its bounded vertex or index capacity",
            ));
        }
        payload_bytes = payload_bytes
            .saturating_add(
                (vertices.len() as u64)
                    .saturating_mul(std::mem::size_of::<FfiGuiMeshVertex>() as u64),
            )
            .saturating_add(
                (indices.len() as u64).saturating_mul(std::mem::size_of::<u32>() as u64),
            );
        if payload_bytes > GUI_MESH_MAX_FRAME_PAYLOAD_BYTES {
            return Err(GalError::ffi(
                StatusCode::LengthOverflow,
                format!(
                    "GUI mesh frame payload {} exceeds bounded limit {}",
                    payload_bytes, GUI_MESH_MAX_FRAME_PAYLOAD_BYTES
                ),
            ));
        }
    }
    let mut owned = Vec::with_capacity(batches.len());
    for batch in batches {
        validate_item_size::<FfiGuiMeshBatchRequest>(batch.byte_size, "GUI mesh batch")?;
        if batch.reserved0 != 0 || batch.gui_width != gui_width || batch.gui_height != gui_height {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh batch reserved fields and frame GUI extent must match",
            ));
        }
        let material_mode = match batch.material_mode {
            1 => GuiMeshMaterialMode::Opaque,
            2 => GuiMeshMaterialMode::Cutout,
            3 => GuiMeshMaterialMode::Translucent,
            4 => GuiMeshMaterialMode::Glint,
            GUI_MESH_MATERIAL_PANORAMA => GuiMeshMaterialMode::Panorama,
            6 => GuiMeshMaterialMode::ModelOverlay,
            7 => GuiMeshMaterialMode::EntityCutoutNoCull,
            8 => GuiMeshMaterialMode::EntityTranslucentNoCull,
            9 => GuiMeshMaterialMode::EntityDecalCutoutNoCull,
            other => {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown GUI mesh material mode {other}"),
                ));
            }
        };
        let lighting_mode = match batch.lighting_mode {
            1 => GuiMeshLightingMode::Flat,
            2 => GuiMeshLightingMode::Block,
            3 => GuiMeshLightingMode::InventoryBlock,
            4 => GuiMeshLightingMode::FrontModel,
            5 => GuiMeshLightingMode::EntityPreview,
            6 => GuiMeshLightingMode::OversizedItem,
            7 => GuiMeshLightingMode::OversizedItemFlat,
            other => {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown GUI mesh lighting mode {other}"),
                ));
            }
        };
        let vertices = read_slice(batch.vertices, true, "GUI mesh vertices")?;
        let indices = read_slice(batch.indices, true, "GUI mesh indices")?;
        if vertices.len() > GUI_MESH_MAX_VERTICES || indices.len() > GUI_MESH_MAX_INDICES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh payload exceeds its bounded vertex or index capacity",
            ));
        }
        let vertices = vertices
            .iter()
            .map(|vertex| GuiMeshVertex {
                position: vertex.position,
                atlas_uv: vertex.atlas_uv,
                local_uv: vertex.local_uv,
                color_argb: vertex.color_argb,
                normal_packed: vertex.normal_packed,
                source_face: vertex.source_face,
                source_foil_type: vertex.source_foil_type,
            })
            .collect();
        let request = GuiMeshBatchRequest {
            item_cache: crate::render::guirender::mesh::GuiItemCache::decode(
                batch.item_cache_identity,
                batch.item_cache_mode,
            )?,
            block_item_raster: crate::render::guirender::mesh::GuiBlockItemRaster::decode(
                batch.block_item_scale,
                batch.block_model_bounds,
                batch.block_item_layout,
            )?,
            decal_foil: crate::render::guirender::mesh::GuiDecalFoilProjection::decode(
                batch.decal_foil_mode,
                batch.decal_model_pose,
                batch.decal_normal_pose,
            )?,
            item_raster_scale: batch.item_raster_scale,
            item_lighting: None,
            item_foil: crate::render::shared::item_foil::StandardItemFoil::decode(
                batch.item_foil_mode,
                batch.item_foil_clock_millis,
                batch.item_foil_speed,
                batch.item_foil_strength,
            )?,
            stratum: batch.stratum,
            layer_index: batch.layer_index,
            sequence: batch.sequence,
            asset_id: batch.asset_id,
            material_mode,
            lighting_mode,
            alpha_cutoff: batch.alpha_cutoff,
            model_transform: batch.model_transform,
            gui_pose: batch.gui_pose,
            bounds: [batch.left, batch.top, batch.right, batch.bottom],
            gui_extent: [
                u32::try_from(batch.gui_width).map_err(|_| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "GUI mesh width must be positive",
                    )
                })?,
                u32::try_from(batch.gui_height).map_err(|_| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "GUI mesh height must be positive",
                    )
                })?,
            ],
            projection_extent: [gui_width as f32, gui_height as f32],
            render_extent: [
                u32::try_from(batch.render_width).map_err(|_| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "GUI mesh render width must be positive",
                    )
                })?,
                u32::try_from(batch.render_height).map_err(|_| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "GUI mesh render height must be positive",
                    )
                })?,
            ],
            guard_pixels: batch.guard_pixels,
            clip_mode: batch.clip_mode,
            clip_left: batch.clip_left,
            clip_top: batch.clip_top,
            clip_width: batch.clip_width,
            clip_height: batch.clip_height,
            vertices,
            indices: indices.to_vec(),
        };
        owned.push(request);
    }
    compact_gui_mesh_item_layers(&mut owned);
    // Validates every batch, then the item layer structure.
    validate_gui_mesh_batches(&owned)?;
    Ok(owned)
}

/// An item whose producer skipped a layer that emitted no quads (seen while
/// the client reloads a dimension) still has a well-defined layer order.
/// Renumber each item's unique layer indices densely from zero in that order
/// rather than rejecting the whole frame; duplicates remain an error.
pub(crate) fn compact_gui_mesh_item_layers(batches: &mut [crate::render::guirender::mesh::GuiMeshBatchRequest]) {
    let mut layers = std::collections::BTreeMap::<(u32, u64), std::collections::BTreeSet<u32>>::new();
    for batch in batches.iter() {
        layers
            .entry((batch.stratum, batch.sequence))
            .or_default()
            .insert(batch.layer_index);
    }
    for batch in batches.iter_mut() {
        if let Some(group) = layers.get(&(batch.stratum, batch.sequence)) {
            if let Some(rank) = group.iter().position(|layer| *layer == batch.layer_index) {
                batch.layer_index = rank as u32;
            }
        }
    }
}
