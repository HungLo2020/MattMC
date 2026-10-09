//! Public GUI request and payload types and their validation.

use super::*;

#[derive(Clone, Debug)]
pub struct GuiSpriteRequest {
    pub stratum: u32,
    pub sprite_id: u32,
    pub selected_slot: i32,
    pub progress_fraction: f32,
    pub fill_direction: u32,
    pub color_argb: u32,
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
    pub gui_width: u32,
    pub gui_height: u32,
    /// Exact semantic projection, distinct from rounded layout/clip bounds.
    pub projection_extent: [f32; 2],
    pub sequence: u64,
}

#[derive(Clone, Debug, Default)]
pub struct GuiSubmitStats {
    /// Time spent lowering GUI semantics into backend-neutral GAL operations.
    pub frontend_nanos: u64,
    /// Subset of frontend time spent validating and preparing GUI mesh vertices.
    pub mesh_prepare_nanos: u64,
    /// Subset of frontend time spent resolving GUI mesh resources and recording operations.
    pub mesh_lower_nanos: u64,
    pub submission_id: u64,
    pub sprite_count: u64,
    pub affine_quad_count: u64,
    /// Coarse standard-3D GUI items that completed owned raster/composite
    /// command construction in this submission.
    pub mesh_item_count: u64,
    /// Semantic mesh layers consumed by the owned GUI-mesh raster path.
    pub mesh_batch_count: u64,
    /// Raster plus compose draws emitted for the mesh items.
    pub mesh_draw_count: u64,
    /// Entity-preview items selected for owned PIP raster/composition.
    pub entity_preview_item_count: u64,
    /// Entity-preview layers that emitted an owned raster draw.
    pub entity_preview_batch_count: u64,
    /// Entity-preview raster plus compose draws emitted for this submission.
    pub entity_preview_draw_count: u64,
    /// Bits 1..=8 identify the semantic material modes used by emitted
    /// entity-preview raster layers.
    pub entity_preview_material_mask: u64,
    pub entity_preview_vertex_count: u64,
    pub entity_preview_index_count: u64,
    pub sprite_batch_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub resource_creates: u64,
    pub command_lists: u64,
    pub command_ops: u64,
    /// Private Rust-owned offscreen raster targets used by standard-3D GUI
    /// items before they are composited into the requested GUI target. This
    /// never contains an acquired frame target or a Java-owned surface.
    pub(crate) owned_intermediate_targets: Vec<Handle>,
    /// Frame-local pass objects used only by a bounded diagnostic replay.
    /// They are not cached because their target retires with that capture.
    pub(crate) transient_diagnostic_passes: Vec<Handle>,
}

#[derive(Clone, Debug)]
pub struct GuiAssetPayload {
    pub sprite_id: u32,
    pub png_bytes: Vec<u8>,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum GuiRawImageFormat {
    Alpha8,
    Rgba8,
}

impl GuiRawImageFormat {
    pub(super) fn texture_format(self) -> TextureFormat {
        match self {
            Self::Alpha8 => TextureFormat::R8Unorm,
            Self::Rgba8 => TextureFormat::Rgba8Unorm,
        }
    }

    pub(super) fn bytes_per_pixel(self) -> usize {
        match self {
            Self::Alpha8 => 1,
            Self::Rgba8 => 4,
        }
    }

    pub(super) fn shader_mode(self) -> f32 {
        match self {
            Self::Alpha8 => 0.0,
            Self::Rgba8 => 1.0,
        }
    }
}

/// CPU input encoding is distinct from the resident/GPU texture format.
/// Indexed map colors are expanded by the frontend before resource admission.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum GuiRawImageSourceFormat { Alpha8, Rgba8, MapColor8 }
impl GuiRawImageSourceFormat {
    pub(crate) fn bytes_per_pixel(self) -> usize {
        match self { Self::Alpha8 | Self::MapColor8 => 1, Self::Rgba8 => 4 }
    }
    pub(crate) fn resident_format(self) -> GuiRawImageFormat {
        match self { Self::Alpha8 => GuiRawImageFormat::Alpha8, Self::Rgba8 | Self::MapColor8 => GuiRawImageFormat::Rgba8 }
    }
}

/// CPU-owned image data. The FFI boundary will carry this as one bounded asset
/// update; it deliberately contains no atlas, renderer, or backend objects.
#[derive(Clone, Debug)]
pub struct GuiRawImageAssetPayload {
    pub asset_id: u64,
    pub format: GuiRawImageSourceFormat,
    pub width: u32,
    pub height: u32,
    pub pixels: Vec<u8>,
    pub sampling: Option<(SamplerFilter, SamplerAddressMode)>,
}

/// One immutable item-local layer: no screen placement or scheduler identity.
#[derive(Clone, Debug)]
pub struct GuiItemRasterLayer {
    pub asset_id: u64,
    pub color_argb: u32,
    pub material: crate::render::guirender::items::material::GuiAffineMaterial,
    pub geometry: crate::render::guirender::items::raster::GuiItemRasterGeometry,
    pub uv: [f32; 4],
    pub model_transform: crate::render::guirender::items::raster::GuiItemModelTransform,
}

/// An affine textured GUI primitive. Four explicit corners from Minecraft font
/// layout reduce to an origin plus two axes without losing italic shear.
#[derive(Clone, Debug)]
pub struct GuiAffineQuadRequest {
    /// Standard 16x16 item-cell raster scale; zero is an ordinary screen quad.
    pub item_raster_scale: u32,
    pub item_raster_layers: Vec<GuiItemRasterLayer>,
    pub item_raster_geometry: crate::render::guirender::items::raster::GuiItemRasterGeometry,
    pub material: crate::render::guirender::items::material::GuiAffineMaterial,
    pub stratum: u32,
    pub asset_id: u64,
    pub x0: f32,
    pub y0: f32,
    pub x1: f32,
    pub y1: f32,
    pub x3: f32,
    pub y3: f32,
    pub z: f32,
    pub u0: f32,
    pub v0: f32,
    pub u1: f32,
    pub v1: f32,
    pub color_argb: u32,
    pub gui_width: u32,
    pub gui_height: u32,
    pub projection_extent: [f32; 2],
    pub sequence: u64,
    pub clip_mode: u32,
    pub clip_left: i32,
    pub clip_top: i32,
    pub clip_width: i32,
    pub clip_height: i32,
}

/// Typed immutable tiled command. Children retain their parent's scheduler
/// identity; only Rust lowers the repetition into explicit draw instances.
/// Private until Java/FFI transport and whole-frame admission are connected.
#[derive(Clone, Debug)]
pub(crate) struct GuiTiledQuadRequest {
    pub geometry: crate::render::guirender::tiling::GuiTileGeometry,
    pub stratum: u32,
    pub asset_id: u64,
    pub z: f32,
    pub color_argb: u32,
    pub gui_extent: [u32; 2],
    pub projection_extent: [f32; 2],
    pub sequence: u64,
    pub clip: Option<[i32; 4]>,
}

impl GuiTiledQuadRequest {
    pub(crate) fn validate(&self) -> GalResult<()> {
        validate_gui_projection(self.gui_extent, self.projection_extent)?;
        if self.asset_id == 0
            || self.sequence == 0
            || self.stratum == 0
            || !self.z.is_finite()
            || self
                .gui_extent
                .iter()
                .any(|v| *v == 0 || *v > GUI_MAX_VIEWPORT_AXIS as u32)
        {
            return Err(GalError::invalid_argument(
                "invalid semantic tiled GUI identity or extent",
            ));
        }
        if let Some([left, top, width, height]) = self.clip {
            if left < 0
                || top < 0
                || width < 0
                || height < 0
                || i64::from(left) + i64::from(width) > i64::from(self.gui_extent[0])
                || i64::from(top) + i64::from(height) > i64::from(self.gui_extent[1])
            {
                return Err(GalError::invalid_argument(
                    "tiled GUI clip must be frame-local",
                ));
            }
        }
        Ok(())
    }
}

pub(crate) fn preflight_tiled_affine_count(
    requests: &[GuiTiledQuadRequest],
    ordinary_affine_count: usize,
) -> GalResult<usize> {
    let mut count = ordinary_affine_count;
    if count > GUI_MAX_EXPANDED_AFFINE_QUADS {
        return Err(GalError::invalid_argument(
            "GUI frame affine expansion exceeds bounded limit",
        ));
    }
    for request in requests {
        request.validate()?;
        count = count
            .checked_add(crate::render::guirender::tiling::tile_segment_count(request.geometry)?)
            .filter(|n| *n <= GUI_MAX_EXPANDED_AFFINE_QUADS)
            .ok_or_else(|| {
                GalError::invalid_argument("GUI frame affine expansion exceeds bounded limit")
            })?;
    }
    Ok(count)
}

pub(super) fn lower_tiled_request(request: GuiTiledQuadRequest) -> GalResult<Vec<GuiAffineQuadRequest>> {
    request.validate()?;
    let [clip_left, clip_top, clip_width, clip_height] = request.clip.unwrap_or([0; 4]);
    crate::render::guirender::tiling::lower_tiles(request.geometry)?
        .into_iter()
        .map(|quad| {
            let child = GuiAffineQuadRequest {
                item_raster_layers: vec![],
                item_raster_scale: 0,
                item_raster_geometry: Default::default(),
                material: crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
                stratum: request.stratum,
                asset_id: request.asset_id,
                x0: quad.origin[0],
                y0: quad.origin[1],
                x1: quad.origin[0] + quad.axis_u[0],
                y1: quad.origin[1] + quad.axis_u[1],
                x3: quad.origin[0] + quad.axis_v[0],
                y3: quad.origin[1] + quad.axis_v[1],
                z: request.z,
                u0: quad.uv[0],
                v0: quad.uv[1],
                u1: quad.uv[2],
                v1: quad.uv[3],
                color_argb: request.color_argb,
                gui_width: request.gui_extent[0],
                gui_height: request.gui_extent[1],
                projection_extent: request.projection_extent,
                sequence: request.sequence,
                clip_mode: u32::from(request.clip.is_some()),
                clip_left,
                clip_top,
                clip_width,
                clip_height,
            };
            validate_affine_quad(&child)?;
            Ok(child)
        })
        .collect()
}

pub(crate) fn validate_gui_projection(layout: [u32; 2], projection: [f32; 2]) -> GalResult<()> {
    if projection
        .iter()
        .zip(layout)
        .any(|(&value, bound)| !value.is_finite() || value <= 0.0 || value.ceil() != bound as f32)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI projection must be finite, positive, and ceil to the explicit layout extent",
        ));
    }
    Ok(())
}

pub(crate) fn validate_affine_quad(request: &GuiAffineQuadRequest) -> GalResult<()> {
    if request.item_raster_layers.len() > crate::render::guirender::items::raster::MAX_ITEM_LAYERS
        || (!request.item_raster_layers.is_empty() && request.item_raster_scale == 0)
    {
        return Err(GalError::invalid_argument(
            "invalid bounded item layer request",
        ));
    }
    if request.item_raster_scale != 0 {
        request.item_raster_geometry.validate()?;
        crate::render::guirender::items::raster::item_uv_identity([request.u0, request.v0, request.u1, request.v1])?;
    }
    if request.item_raster_scale > 256
        || (request.item_raster_scale != 0
            && (matches!(
                request.material,
                crate::render::guirender::items::material::GuiAffineMaterial::Unlit
            ) || request.z != 0.0))
    {
        return Err(GalError::invalid_argument(
            "invalid full-item raster semantics",
        ));
    }
    validate_gui_projection(
        [request.gui_width, request.gui_height],
        request.projection_extent,
    )?;
    const GUI_UV_OVERLAP_LIMIT: f32 = 1.0 / 16.0;
    if request.asset_id == 0 || request.gui_width == 0 || request.gui_height == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI affine quad requires a non-zero asset and viewport",
        ));
    }
    let values = [
        request.x0, request.y0, request.x1, request.y1, request.x3, request.y3, request.z,
        request.u0, request.v0, request.u1, request.v1,
    ];
    if values.into_iter().any(|value| !value.is_finite()) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI affine quad coordinates must be finite",
        ));
    }
    if request.u0 < -GUI_UV_OVERLAP_LIMIT
        || request.v0 < -GUI_UV_OVERLAP_LIMIT
        || request.u1 > 1.0 + GUI_UV_OVERLAP_LIMIT
        || request.v1 > 1.0 + GUI_UV_OVERLAP_LIMIT
        || request.u1 < request.u0
        || request.v1 < request.v0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI affine quad UV range must stay inside the semantic image",
        ));
    }
    match request.clip_mode {
        0 if request.clip_left == 0
            && request.clip_top == 0
            && request.clip_width == 0
            && request.clip_height == 0 => {}
        1 if request.clip_left >= 0
            && request.clip_top >= 0
            && request.clip_width >= 0
            && request.clip_height >= 0
            && request.clip_left.saturating_add(request.clip_width) <= request.gui_width as i32
            && request.clip_top.saturating_add(request.clip_height)
                <= request.gui_height as i32 => {}
        _ => {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI affine quad clip must be disabled or a bounded frame-local rectangle",
            ));
        }
    }
    Ok(())
}
