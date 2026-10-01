//! Validation of GUI mesh batches and resolution of item raster bounds.

use super::*;

/// Diagnostic counts from decoded semantic meshes; no rendering state mutation.
pub(crate) fn flat_item_mesh_decode_counts(
    batches: &[GuiMeshBatchRequest],
) -> (usize, usize, usize, usize) {
    let mut groups = BTreeSet::new();
    let mut transforms = BTreeSet::new();
    let mut layers = 0;
    let mut nonidentity = 0;
    // Even an untransformed item includes the authored model-centering
    // translation; compare with that convention, not a raw identity matrix.
    let identity = crate::render::guirender::items::raster::GuiItemModelTransform::default().0;
    for batch in batches.iter().filter(|batch| batch.item_raster_scale != 0) {
        groups.insert((batch.stratum, batch.sequence));
        layers += 1;
        nonidentity += usize::from(batch.model_transform != identity);
        transforms.insert(
            batch
                .model_transform
                .map(|v| if v == 0.0 { 0 } else { v.to_bits() }),
        );
    }
    (groups.len(), layers, nonidentity, transforms.len())
}

pub fn validate_batches(batches: &[GuiMeshBatchRequest]) -> GalResult<()> {
    if batches.len() > GUI_MESH_MAX_BATCHES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh batch count {} exceeds maximum {}",
                batches.len(),
                GUI_MESH_MAX_BATCHES
            ),
        ));
    }
    let mut layer_groups = BTreeMap::<(u32, u64), BTreeSet<u32>>::new();
    for batch in batches {
        validate_batch(batch)?;
        if batch.sequence == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh batches require non-zero item frame sequences",
            ));
        }
        if !layer_groups
            .entry((batch.stratum, batch.sequence))
            .or_default()
            .insert(batch.layer_index)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh item layers require unique layer indices",
            ));
        }
    }
    for layers in layer_groups.values() {
        if layers
            .iter()
            .copied()
            .enumerate()
            .any(|(expected, actual)| actual != expected as u32)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh item layers must be contiguous from zero",
            ));
        }
    }
    Ok(())
}

/// Frozen's flat atlas cell uses translate(k/2,k/2,0), scale(k,-k,k).
/// Resolve that layout here, without a caller-provided PIP target or guard band.
pub(crate) fn resolved_item_raster(
    batch: &GuiMeshBatchRequest,
) -> GalResult<([u32; 2], [f32; 16], u32)> {
    if let Some(block) = batch.block_item_raster {
        if block
            .model_min
            .iter()
            .chain(block.model_max.iter())
            .any(|v| !v.is_finite())
            || (0..3).any(|axis| block.model_min[axis] > block.model_max[axis])
        {
            return Err(GalError::invalid_argument("invalid semantic item bounds"));
        }
        if batch.item_raster_scale != 0
            || batch.render_extent != [0, 0]
            || batch.guard_pixels != 0
            || batch.decal_foil.is_some()
            || !matches!(
                (batch.material_mode, batch.lighting_mode),
                (
                    GuiMeshMaterialMode::Opaque
                        | GuiMeshMaterialMode::Cutout
                        | GuiMeshMaterialMode::Translucent,
                    GuiMeshLightingMode::InventoryBlock
                ) | (GuiMeshMaterialMode::Glint, GuiMeshLightingMode::Flat)
            )
            || (batch.material_mode == GuiMeshMaterialMode::Glint && batch.item_foil.is_none())
        {
            return Err(GalError::invalid_argument(
                "conflicting native block item layout semantics",
            ));
        }
        let layout = match block.oversized_layout(batch.bounds)? {
            Some((layout, _)) => layout,
            None => crate::render::guirender::items::layout::GuiItemRasterLayout::inventory_block(block.gui_scale)?,
        };
        return Ok((
            layout.extent,
            layout.compose(batch.model_transform)?,
            layout.guard_pixels,
        ));
    }
    if batch.item_raster_scale == 0 {
        if batch.item_lighting.is_some() && !batch.requires_item_lightmap() {
            return Err(GalError::invalid_argument(
                "explicit mesh cannot carry flat item lighting",
            ));
        }
        return Ok((
            batch.render_extent,
            batch.model_transform,
            batch.guard_pixels,
        ));
    }
    if batch.render_extent != [0, 0]
        || batch.guard_pixels != 0
        || !matches!(
            batch.lighting_mode,
            GuiMeshLightingMode::Flat | GuiMeshLightingMode::FrontModel
        )
        || batch.material_mode == GuiMeshMaterialMode::Panorama
        || (batch.material_mode == GuiMeshMaterialMode::Glint && batch.item_foil.is_none())
    {
        return Err(GalError::invalid_argument(
            "flat item mesh has conflicting raster/material semantics",
        ));
    }
    let layout = crate::render::guirender::items::layout::GuiItemRasterLayout::flat(batch.item_raster_scale)?;
    // Indexed front-lit items include genuine 3D models (e.g. shields), not
    // just generated 2D item layers. Preserve their explicit affine transform;
    // the separate quad/sprite frontend retains its 2D transform contract.
    if batch
        .model_transform
        .iter()
        .any(|v| !v.is_finite() || v.abs() > 16.0)
        || [3, 7, 11].iter().any(|i| batch.model_transform[*i] != 0.0)
        || batch.model_transform[15] != 1.0
    {
        return Err(GalError::invalid_argument(
            "native item mesh requires a bounded affine model transform",
        ));
    }
    model_transform_determinant(batch.model_transform)?;
    Ok((
        layout.extent,
        layout.compose(batch.model_transform)?,
        layout.guard_pixels,
    ))
}

pub(crate) fn resolved_item_bounds(batch: &GuiMeshBatchRequest) -> GalResult<[i32; 4]> {
    Ok(
        match batch
            .block_item_raster
            .map(|block| block.oversized_layout(batch.bounds))
            .transpose()?
            .flatten()
        {
            Some((_, bounds)) => bounds,
            None => batch.bounds,
        },
    )
}

pub fn validate_batch(batch: &GuiMeshBatchRequest) -> GalResult<()> {
    if matches!(
        batch.material_mode,
        GuiMeshMaterialMode::EntityCutoutNoCull
            | GuiMeshMaterialMode::EntityTranslucentNoCull
            | GuiMeshMaterialMode::EntityDecalCutoutNoCull
    ) && (!batch.lighting_mode.is_entity_material_lighting()
        || (batch.alpha_cutoff - 0.1).abs() > f32::EPSILON
        || batch.item_raster_scale != 0
        || batch.item_foil.is_some())
    {
        return Err(GalError::invalid_argument(
            "entity no-cull material requires an entity preview with vanilla alpha cutoff",
        ));
    }
    if let Some(cache) = batch.item_cache {
        if cache.identity == 0
            || (batch.item_raster_scale == 0 && batch.block_item_raster.is_none())
            || (!cache.animated && batch.item_foil.is_some())
        {
            return Err(GalError::invalid_argument(
                "GUI item caching requires a native item raster and coherent animation semantics",
            ));
        }
    }
    if batch.material_mode == GuiMeshMaterialMode::ModelOverlay
        && (batch.item_raster_scale == 0
            || batch.lighting_mode != GuiMeshLightingMode::FrontModel
            || batch.alpha_cutoff != 0.0
            || batch.item_foil.is_some())
    {
        return Err(GalError::invalid_argument(
            "model overlay requires native front-lit base geometry without cutout or foil",
        ));
    }
    if batch.lighting_mode == GuiMeshLightingMode::FrontModel
        && (batch.item_raster_scale == 0
            || batch.material_mode == GuiMeshMaterialMode::Glint
            || batch.decal_foil.is_some()
            || batch.block_item_raster.is_some())
    {
        return Err(GalError::invalid_argument(
            "front model lighting requires a native base item mesh",
        ));
    }
    let (render_extent, model_transform, guard_pixels) = resolved_item_raster(batch)?;
    if let Some(decal) = batch.decal_foil {
        if batch.item_foil.is_none() {
            return Err(GalError::invalid_argument(
                "decal foil requires explicit item foil timing and strength",
            ));
        }
        decal.prepare((batch.item_raster_scale != 0).then_some(model_transform))?;
    }
    if let Some(foil) = batch.item_foil {
        foil.validate()?;
        if foil.kind == crate::render::shared::item_foil::StandardFoilKind::Armor {
            return Err(GalError::invalid_argument(
                "perspective armor foil is not a GUI entity-preview material",
            ));
        }
        if foil.kind == crate::render::shared::item_foil::StandardFoilKind::ArmorOrthographic
            && (batch.lighting_mode != GuiMeshLightingMode::EntityPreview
                || batch.item_raster_scale != 0
                || batch.block_item_raster.is_some()
                || batch.decal_foil.is_some())
        {
            return Err(GalError::invalid_argument(
                "orthographic armor foil requires explicit GUI entity-preview semantics",
            ));
        }
        if foil.kind == crate::render::shared::item_foil::StandardFoilKind::Entity
            && (batch.item_raster_scale == 0
                || batch.block_item_raster.is_some()
                || batch.decal_foil.is_some()
                || batch.lighting_mode != GuiMeshLightingMode::Flat)
        {
            return Err(GalError::invalid_argument(
                "entity foil requires native front-lit model item layout",
            ));
        }
        if batch.material_mode != GuiMeshMaterialMode::Glint {
            return Err(GalError::invalid_argument(
                "item foil requires the glint material",
            ));
        }
    }
    if batch.stratum == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a non-zero GUI stratum",
        ));
    }
    if batch.asset_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a non-zero semantic image asset id",
        ));
    }
    if batch.gui_extent[0] == 0 || batch.gui_extent[1] == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a positive GUI extent",
        ));
    }
    if batch.gui_extent[0] > GUI_MAX_VIEWPORT_AXIS as u32
        || batch.gui_extent[1] > GUI_MAX_VIEWPORT_AXIS as u32
    {
        return Err(GalError::unsupported_feature(format!(
            "GUI mesh logical extent {}x{} exceeds bounded axis {}",
            batch.gui_extent[0], batch.gui_extent[1], GUI_MAX_VIEWPORT_AXIS
        )));
    }
    if render_extent[0] == 0 || render_extent[1] == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a positive offscreen raster extent",
        ));
    }
    crate::render::guirender::frontend::validate_gui_projection(batch.gui_extent, batch.projection_extent)?;
    if render_extent[0] > GUI_MESH_MAX_OFFSCREEN_AXIS
        || render_extent[1] > GUI_MESH_MAX_OFFSCREEN_AXIS
    {
        return Err(GalError::unsupported_feature(format!(
            "GUI mesh offscreen extent {}x{} exceeds bounded axis {}",
            batch.render_extent[0], batch.render_extent[1], GUI_MESH_MAX_OFFSCREEN_AXIS
        )));
    }
    if batch.bounds[0] >= batch.bounds[2] || batch.bounds[1] >= batch.bounds[3] {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires ordered non-empty logical bounds",
        ));
    }
    if !batch.alpha_cutoff.is_finite() || !(0.0..=1.0).contains(&batch.alpha_cutoff) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh alpha cutoff must be finite and within [0, 1]",
        ));
    }
    if batch.material_mode == GuiMeshMaterialMode::Opaque && batch.alpha_cutoff != 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "opaque GUI mesh batches must use a zero alpha cutoff",
        ));
    }
    if batch.vertices.len() < 3 || batch.vertices.len() > GUI_MESH_MAX_VERTICES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh vertex count {} is outside 3..={}",
                batch.vertices.len(),
                GUI_MESH_MAX_VERTICES
            ),
        ));
    }
    if batch.indices.len() < 3
        || batch.indices.len() > GUI_MESH_MAX_INDICES
        || batch.indices.len() % 3 != 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh index count {} must be a bounded triangle list",
                batch.indices.len()
            ),
        ));
    }
    if !batch.model_transform.iter().all(|value| value.is_finite())
        || !batch.gui_pose.iter().all(|value| value.is_finite())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh transforms must be finite",
        ));
    }
    if guard_pixels.saturating_mul(2) >= render_extent[0]
        || guard_pixels.saturating_mul(2) >= render_extent[1]
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh guard band must leave a non-empty offscreen raster area",
        ));
    }
    match batch.clip_mode {
        0 if batch.clip_left == 0
            && batch.clip_top == 0
            && batch.clip_width == 0
            && batch.clip_height == 0 => {}
        1 if batch.clip_left >= 0
            && batch.clip_top >= 0
            && batch.clip_width >= 0
            && batch.clip_height >= 0
            && batch.clip_left <= batch.gui_extent[0] as i32
            && batch.clip_top <= batch.gui_extent[1] as i32
            && batch.clip_width <= batch.gui_extent[0] as i32 - batch.clip_left
            && batch.clip_height <= batch.gui_extent[1] as i32 - batch.clip_top => {}
        _ => {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh clip must be disabled or a bounded frame-local rectangle",
            ))
        }
    }
    for vertex in &batch.vertices {
        if vertex.source_face > 6
            || vertex.source_foil_type > 1
            || (vertex.source_foil_type != 0 && vertex.source_face == 0)
            || ((vertex.source_face != 0 || vertex.source_foil_type != 0)
                && batch.block_item_raster.is_none())
        {
            return Err(GalError::invalid_argument(
                "GUI baked face/foil requires valid native block semantics",
            ));
        }
        if !vertex.position.iter().all(|value| value.is_finite())
            || !vertex.atlas_uv.iter().all(|value| value.is_finite())
            || !vertex.local_uv.iter().all(|value| value.is_finite())
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh vertices must contain finite positions and atlas UVs",
            ));
        }
    }
    if batch
        .indices
        .iter()
        .any(|index| *index as usize >= batch.vertices.len())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh indices must reference a copied vertex in their batch",
        ));
    }
    Ok(())
}
