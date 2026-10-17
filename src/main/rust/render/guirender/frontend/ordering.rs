//! Ordering of sprite, affine, tiled and mesh requests into one stratum-sorted stream.

use super::*;

pub(crate) fn validate_gui_frame_sequences(
    sprites: &[GuiSpriteRequest],
    affine: &[GuiAffineQuadRequest],
    meshes: &[GuiMeshBatchRequest],
    tiles: &[GuiTiledQuadRequest],
) -> GalResult<()> {
    let mesh_sequences = meshes
        .iter()
        .map(|request| request.sequence)
        .collect::<std::collections::BTreeSet<_>>();
    let mut seen = std::collections::BTreeSet::new();
    for sequence in sprites
        .iter()
        .map(|request| request.sequence)
        .chain(affine.iter().map(|request| request.sequence))
        .chain(tiles.iter().map(|request| request.sequence))
        .chain(mesh_sequences)
    {
        if sequence == 0 {
            return Err(GalError::invalid_argument(
                "GUI requests require non-zero scheduler sequences",
            ));
        }
        if !seen.insert(sequence) {
            return Err(GalError::invalid_argument(
                "GUI request scheduler sequences must be unique within one frame",
            ));
        }
    }
    Ok(())
}

#[derive(Debug)]
pub(super) enum GuiFrameRequest {
    Sprite(GuiSpriteRequest),
    Affine(GuiAffineQuadRequest),
    /// Consecutive affine quads sharing one semantic texture group.  Their
    /// instance order remains the scheduler order, but one explicit draw can
    /// carry all of them.
    AffineBatch(Vec<GuiAffineQuadRequest>),
    Mesh(GuiMeshItem),
}

#[derive(Debug)]
pub(super) struct GuiMeshItem {
    pub(super) stratum: u32,
    pub(super) sequence: u64,
    pub(super) layers: Vec<GuiMeshBatchRequest>,
}

impl GuiFrameRequest {
    pub(super) fn stratum(&self) -> u32 {
        match self {
            Self::Sprite(request) => request.stratum,
            Self::Affine(request) => request.stratum,
            Self::AffineBatch(requests) => requests[0].stratum,
            Self::Mesh(request) => request.stratum,
        }
    }

    pub(super) fn sequence(&self) -> u64 {
        match self {
            Self::Sprite(request) => request.sequence,
            Self::Affine(request) => request.sequence,
            Self::AffineBatch(requests) => requests[0].sequence,
            Self::Mesh(request) => request.sequence,
        }
    }
}

/// Semantic model overlays finish before the item's depth-equal foil pass.
/// Frozen's fixed entity-glint buffer is flushed after shield pattern buffers;
/// express that dependency here without asking Java to sort GPU passes.
/// Other item families keep their existing authored order.
pub(super) fn mesh_item_layer_execution_order(layers: &[GuiMeshPreparedDraw]) -> Vec<usize> {
    let mut order: Vec<_> = (0..layers.len()).collect();
    if layers
        .iter()
        .any(|layer| layer.material_mode == GuiMeshMaterialMode::ModelOverlay)
    {
        order.sort_by_key(|index| {
            (
                layers[*index].material_mode == GuiMeshMaterialMode::Glint,
                *index,
            )
        });
    }
    order
}

pub(super) fn validate_mesh_item_layers(layers: &[GuiMeshPreparedDraw]) -> GalResult<()> {
    let first = layers.first().ok_or_else(|| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh item must contain one or more layers",
        )
    })?;
    for (expected_layer, layer) in layers.iter().enumerate() {
        if layer.layer_index != expected_layer as u32
            || layer.item_cache != first.item_cache
            || layer.bounds != first.bounds
            || layer.gui_pose != first.gui_pose
            || layer.gui_extent != first.gui_extent
            || layer.render_extent != first.render_extent
            || layer.guard_pixels != first.guard_pixels
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh item layers must share target and composition semantics",
            ));
        }
    }
    Ok(())
}

pub(super) fn order_gui_requests(
    sprites: Vec<GuiSpriteRequest>,
    affine_quads: Vec<GuiAffineQuadRequest>,
) -> GalResult<Vec<GuiFrameRequest>> {
    order_gui_requests_with_mesh(sprites, affine_quads, Vec::new())
}

pub(super) fn order_gui_requests_with_mesh(
    sprites: Vec<GuiSpriteRequest>,
    affine_quads: Vec<GuiAffineQuadRequest>,
    mesh_batches: Vec<GuiMeshBatchRequest>,
) -> GalResult<Vec<GuiFrameRequest>> {
    order_gui_requests_with_tiles(sprites, affine_quads, mesh_batches, Vec::new())
}

pub(super) fn order_gui_requests_with_tiles(
    sprites: Vec<GuiSpriteRequest>,
    affine_quads: Vec<GuiAffineQuadRequest>,
    mesh_batches: Vec<GuiMeshBatchRequest>,
    tiled_quads: Vec<GuiTiledQuadRequest>,
) -> GalResult<Vec<GuiFrameRequest>> {
    preflight_tiled_affine_count(&tiled_quads, affine_quads.len())?;
    if mesh_batches.len() > GUI_MAX_MESH_BATCHES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh batch count {} exceeds bounded limit {GUI_MAX_MESH_BATCHES}",
                mesh_batches.len()
            ),
        ));
    }
    let mesh_items = group_gui_mesh_items(mesh_batches)?;
    let tiled_items = tiled_quads
        .into_iter()
        .map(|request| lower_tiled_request(request).map(GuiFrameRequest::AffineBatch))
        .collect::<GalResult<Vec<_>>>()?;
    let mut ordered = sprites
        .into_iter()
        .map(GuiFrameRequest::Sprite)
        .chain(affine_quads.into_iter().map(GuiFrameRequest::Affine))
        .chain(mesh_items.into_iter().map(GuiFrameRequest::Mesh))
        .chain(tiled_items)
        .collect::<Vec<_>>();
    ordered.sort_by_key(|request| (request.stratum(), request.sequence()));
    validate_ordered_gui_requests(&ordered)?;
    Ok(coalesce_ordered_affine_requests(ordered))
}

pub(super) fn coalesce_ordered_affine_requests(ordered: Vec<GuiFrameRequest>) -> Vec<GuiFrameRequest> {
    let mut result = Vec::with_capacity(ordered.len());
    for request in ordered {
        match request {
            GuiFrameRequest::Affine(request) => {
                let can_append = result.last().is_some_and(|previous| {
                    if let GuiFrameRequest::AffineBatch(batch) = previous {
                        batch.last().is_some_and(|last| {
                            last.stratum == request.stratum
                                && last.asset_id == request.asset_id
                                && last.item_raster_scale == request.item_raster_scale
                        })
                    } else {
                        false
                    }
                });
                if can_append {
                    if let Some(GuiFrameRequest::AffineBatch(batch)) = result.last_mut() {
                        batch.push(request);
                    }
                } else {
                    result.push(GuiFrameRequest::AffineBatch(vec![request]));
                }
            }
            other => result.push(other),
        }
    }
    result
}

pub(super) fn group_gui_mesh_items(mut batches: Vec<GuiMeshBatchRequest>) -> GalResult<Vec<GuiMeshItem>> {
    crate::render::guirender::mesh::validate_batches(&batches)?;
    batches.sort_by_key(|batch| (batch.stratum, batch.sequence, batch.layer_index));
    // Move each batch into its item; the requests (and their vertices) are owned.
    let mut items: Vec<GuiMeshItem> = Vec::new();
    for batch in batches {
        let key = (batch.stratum, batch.sequence);
        match items.last_mut() {
            Some(item) if (item.stratum, item.sequence) == key => item.layers.push(batch),
            _ => items.push(GuiMeshItem {
                stratum: key.0,
                sequence: key.1,
                layers: vec![batch],
            }),
        }
    }
    Ok(items)
}

pub(super) fn validate_ordered_gui_requests(requests: &[GuiFrameRequest]) -> GalResult<()> {
    let mut previous = None;
    for request in requests {
        if request.sequence() == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI request sequence must be non-zero",
            ));
        }
        let key = (request.stratum(), request.sequence());
        if previous.is_some_and(|previous| previous >= key) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI requests must have unique scheduler sequences in stratum order",
            ));
        }
        previous = Some(key);
    }
    Ok(())
}
