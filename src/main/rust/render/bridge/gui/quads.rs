//! Affine and tiled GUI quad decoding.

use super::*;

pub(super) const GUI_MAX_AFFINE_QUADS: usize = crate::render::guirender::frontend::GUI_MAX_EXPANDED_AFFINE_QUADS;

/// Copies ABI v29 tiled semantics with aggregate preflight before geometry
/// expansion. Frame integration checks parent sequences across GUI families
/// before partitioning at blur boundaries. Producer admission remains private.
pub(crate) unsafe fn decode_gui_tiled_quads(
    raw: FfiSlice<FfiGuiTiledQuadRequest>,
    gui_extent: [u32; 2],
    projection_extent: [f32; 2],
    ordinary_affine_count: usize,
) -> GalResult<Vec<crate::render::guirender::frontend::GuiTiledQuadRequest>> {
    use crate::render::guirender::frontend::{preflight_tiled_affine_count, GuiTiledQuadRequest};
    if raw.count > GUI_MAX_AFFINE_QUADS as u64 {
        return Err(GalError::invalid_argument(
            "tiled GUI request count exceeds bounded limit",
        ));
    }
    let items = read_slice(raw, true, "GUI tiled quad requests")?;
    let mut count = preflight_tiled_affine_count(&[], ordinary_affine_count)?;
    let mut owned = Vec::with_capacity(items.len());
    let mut sequences = std::collections::BTreeSet::new();
    for item in items {
        validate_item_size::<FfiGuiTiledQuadRequest>(item.byte_size, "GUI tiled quad")?;
        let clip = match item.clip_mode {
            0 if item.clip == [0; 4] => None,
            1 => Some(item.clip),
            _ => return Err(GalError::invalid_argument("invalid tiled GUI clip mode")),
        };
        let request = GuiTiledQuadRequest {
            geometry: crate::render::guirender::tiling::GuiTileGeometry {
                bounds: item.bounds,
                tile_extent: item.tile_extent,
                uv: item.uv,
                pose: item.pose,
            },
            stratum: item.stratum,
            asset_id: item.asset_id,
            z: item.z,
            color_argb: item.color_argb,
            gui_extent,
            projection_extent,
            sequence: item.sequence,
            clip,
        };
        count = preflight_tiled_affine_count(std::slice::from_ref(&request), count)?;
        if !sequences.insert(request.sequence) {
            return Err(GalError::invalid_argument(
                "duplicate tiled GUI parent sequence",
            ));
        }
        owned.push(request);
    }
    Ok(owned)
}

pub(super) fn decode_gui_affine_quads(
    raw: FfiSlice<FfiGuiAffineQuadRequest>,
    gui_width: i32,
    gui_height: i32,
) -> GalResult<Vec<GuiAffineQuadRequest>> {
    let quads = unsafe { read_slice(raw, true, "GUI affine quad requests") }?;
    if quads.len() > GUI_MAX_AFFINE_QUADS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI affine quad count {} exceeds max {}",
                quads.len(),
                GUI_MAX_AFFINE_QUADS
            ),
        ));
    }
    let mut owned = Vec::with_capacity(quads.len());
    let mut total_layers = 0_usize;
    for quad in quads {
        validate_item_size::<FfiGuiAffineQuadRequest>(quad.byte_size, "GUI affine quad")?;
        if quad.item_raster_layers.count > crate::render::guirender::items::raster::MAX_ITEM_LAYERS as u64
            || (quad.item_raster_layers.count != 0 && quad.item_raster_scale == 0)
        {
            return Err(GalError::invalid_argument(
                "invalid bounded GUI item layers",
            ));
        }
        total_layers += quad.item_raster_layers.count as usize;
        if total_layers > crate::render::guirender::frontend::GUI_MAX_RAW_IMAGES {
            return Err(GalError::invalid_argument(
                "GUI item layer stream exceeds frame bound",
            ));
        }
        let layers = unsafe { read_slice(quad.item_raster_layers, true, "GUI item layers") }?;
        let mut item_raster_layers = Vec::with_capacity(layers.len());
        for layer in layers {
            validate_item_size::<FfiGuiItemRasterLayer>(layer.byte_size, "GUI item layer")?;
            if layer.asset_id == 0 || !matches!(layer.material_mode, 1 | 2) {
                return Err(GalError::invalid_argument(
                    "invalid GUI item layer resource/material",
                ));
            }
            let geometry = crate::render::guirender::items::raster::GuiItemRasterGeometry {
                corners: layer.corners,
            };
            geometry.identity()?;
            crate::render::guirender::items::raster::item_uv_identity(layer.uv)?;
            let model_transform =
                crate::render::guirender::items::raster::GuiItemModelTransform(layer.model_transform);
            model_transform.validate()?;
            item_raster_layers.push(crate::render::guirender::frontend::GuiItemRasterLayer {
                asset_id: layer.asset_id,
                color_argb: layer.color_argb,
                geometry,
                uv: layer.uv,
                model_transform,
                material: crate::render::guirender::items::material::GuiAffineMaterial::decode(
                    layer.material_mode,
                )?,
            });
        }
        if quad.asset_id == 0 || quad.gui_width != gui_width || quad.gui_height != gui_height {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI affine quad asset and viewport must match its frame",
            ));
        }
        let to_u32 = |value: i32, field: &str| -> GalResult<u32> {
            u32::try_from(value).map_err(|_| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!("GUI affine quad {field} must be non-negative, got {value}"),
                )
            })
        };
        let request = GuiAffineQuadRequest {
            item_raster_layers,
            item_raster_scale: quad.item_raster_scale,
            item_raster_geometry: crate::render::guirender::items::raster::GuiItemRasterGeometry {
                corners: quad.item_raster_corners,
            },
            material: crate::render::guirender::items::material::GuiAffineMaterial::decode(
                quad.material_mode,
            )?,
            stratum: quad.stratum,
            asset_id: quad.asset_id,
            x0: quad.x0,
            y0: quad.y0,
            x1: quad.x1,
            y1: quad.y1,
            x3: quad.x3,
            y3: quad.y3,
            z: quad.z,
            u0: quad.u0,
            v0: quad.v0,
            u1: quad.u1,
            v1: quad.v1,
            color_argb: quad.color_argb,
            gui_width: to_u32(quad.gui_width, "gui_width")?,
            gui_height: to_u32(quad.gui_height, "gui_height")?,
            projection_extent: [gui_width as f32, gui_height as f32],
            sequence: quad.sequence,
            clip_mode: quad.clip_mode,
            clip_left: quad.clip_left,
            clip_top: quad.clip_top,
            clip_width: quad.clip_width,
            clip_height: quad.clip_height,
        };
        crate::render::guirender::frontend::validate_affine_quad(&request)?;
        owned.push(request);
    }
    Ok(owned)
}
