//! Private explicit layout for GUI item rasterization before GUI composition.
//! The caller supplies semantic item identities/order; no Java atlas positions
//! or GPU objects are accepted. The intermediate row order is explicit;
//! backend framebuffer conventions are not reconstructed here.
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    Extent3d, RenderPassDesc, RenderTargetDesc, TextureDesc, TextureDimension, TextureFormat,
    TextureUsage, TextureViewDesc,
};
use std::collections::{BTreeMap, BTreeSet};

const MAX_ITEMS: u32 = 4096;
pub(crate) const MAX_ITEM_LAYERS: usize = 64;

/// Immutable resolved model transform, column-major, in model coordinates.
/// This flat family retains a common Z plane and front-facing normal. General
/// 3D, perspective, and reflected-face transforms require the mesh family.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiItemModelTransform(pub [f32; 16]);

impl Default for GuiItemModelTransform {
    fn default() -> Self {
        Self([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -0.5, -0.5, -0.5, 1.0,
        ])
    }
}

impl GuiItemModelTransform {
    pub fn validate(self) -> GalResult<()> {
        let m = self.0;
        let determinant = m[0] * m[5] - m[1] * m[4];
        // Resolved quaternion rotations carry a few float rounding bits in
        // their otherwise unchanged Z scale. Require exact centered-Z
        // structure, with a machine-precision bound on that scale residue.
        // Do not snap the matrix or relax any XY geometry/pixel comparison.
        let centered_z = (m[10] - 1.0).abs() <= 4.0 * f32::EPSILON && m[14] == -0.5 * m[10];
        if m.iter().any(|v| !v.is_finite() || v.abs() > 16.0)
            || [2, 3, 6, 7, 8, 9, 11].into_iter().any(|i| m[i] != 0.0)
            || !centered_z
            || m[15] != 1.0
            || !determinant.is_finite()
            || determinant <= 0.000001
        {
            return Err(GalError::invalid_argument(
                "unsupported flat item model transform",
            ));
        }
        Ok(())
    }

    pub fn lower(self, geometry: GuiItemRasterGeometry) -> GalResult<GuiItemRasterGeometry> {
        self.validate()?;
        geometry.validate()?;
        if self == Self::default() {
            return Ok(geometry);
        }
        let m = self.0;
        let mut corners = geometry.corners;
        for point in corners.chunks_exact_mut(2) {
            let x = point[0] / 16.0;
            let y = 1.0 - point[1] / 16.0;
            point[0] = (m[0] * x + m[4] * y + m[12] + 0.5) * 16.0;
            point[1] = (0.5 - (m[1] * x + m[5] * y + m[13])) * 16.0;
        }
        let transformed = GuiItemRasterGeometry { corners };
        transformed.validate()?;
        Ok(transformed)
    }
}

pub(crate) fn item_uv_identity(uv: [f32; 4]) -> GalResult<[u32; 4]> {
    if uv
        .iter()
        .any(|v| !v.is_finite() || !(0.0..=1.0).contains(v))
        || uv[0] >= uv[2]
        || uv[1] >= uv[3]
    {
        return Err(GalError::invalid_argument(
            "invalid item-local UV rectangle",
        ));
    }
    Ok(uv.map(|v| if v == 0.0 { 0 } else { v.to_bits() }))
}

/// Authored item-local geometry, independent of the final screen transform.
/// The fourth affine corner is implied. This bounded slice is contained in
/// the canonical 16-unit item cell; other shapes remain unadmitted.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiItemRasterGeometry {
    pub corners: [f32; 6],
}

impl Default for GuiItemRasterGeometry {
    fn default() -> Self {
        Self {
            corners: [0.0, 0.0, 16.0, 0.0, 0.0, 16.0],
        }
    }
}

impl GuiItemRasterGeometry {
    pub fn validate(self) -> GalResult<()> {
        let [x0, y0, x1, y1, x3, y3] = self.corners;
        let fourth = [x1 + x3 - x0, y1 + y3 - y0];
        let area = (x1 - x0) * (y3 - y0) - (y1 - y0) * (x3 - x0);
        if self
            .corners
            .into_iter()
            .chain(fourth)
            .any(|v| !v.is_finite() || !(0.0..=16.0).contains(&v))
            || !area.is_finite()
            || area.abs() <= 0.000001
        {
            return Err(GalError::invalid_argument(
                "invalid bounded item-local raster geometry",
            ));
        }
        Ok(())
    }

    pub fn identity(self) -> GalResult<[u32; 6]> {
        self.validate()?;
        Ok(self.corners.map(|v| if v == 0.0 { 0 } else { v.to_bits() }))
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum GuiItemRasterRows {
    TopDown,
    BottomUp,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct GuiItemRasterIdentity {
    pub asset_id: u64,
    pub atlas_generation: u64,
    pub texture_id: u32,
    pub region: [u32; 4],
    pub color_argb: u32,
    pub lighting_rgb: [u32; 3],
    pub cutout: bool,
    pub geometry: [u32; 6],
    pub uv: [u32; 4],
}

/// Semantic slot history survives texture/GUI asset reloads. A replacement
/// model incarnation gets a new slot; GUI-scale changes and atlas exhaustion
/// rebuild the layout. No obsolete GPU image or Java model object is retained.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub(crate) struct GuiItemRasterSlots {
    scale: u32,
    layout: Option<GuiItemRasterLayout>,
    entries: BTreeMap<Vec<GuiItemRasterIdentity>, u32>,
}

impl GuiItemRasterSlots {
    pub fn prepare(
        &mut self,
        scale: u32,
        identities: &[GuiItemRasterIdentity],
        max_side: u32,
    ) -> GalResult<Vec<GuiItemRasterPlacement>> {
        self.prepare_groups(
            scale,
            &identities.iter().map(|id| vec![*id]).collect::<Vec<_>>(),
            max_side,
        )
    }

    /// Layer order and every layer's resource incarnation are part of item
    /// identity. Never assign independently composited slots to child layers.
    pub fn prepare_groups(
        &mut self,
        scale: u32,
        identities: &[Vec<GuiItemRasterIdentity>],
        max_side: u32,
    ) -> GalResult<Vec<GuiItemRasterPlacement>> {
        if identities.is_empty()
            || identities.len() > MAX_ITEMS as usize
            || identities.iter().any(|group| {
                group.is_empty()
                    || group.len() > MAX_ITEM_LAYERS
                    || group.iter().any(|id| {
                        id.asset_id == 0
                            || id.atlas_generation == 0
                            || id.texture_id == 0
                            || id.region[2] == 0
                            || id.region[3] == 0
                    })
            })
        {
            return Err(GalError::invalid_argument(
                "invalid bounded item raster identities",
            ));
        }
        let unique: BTreeSet<_> = identities.iter().cloned().collect();
        let added = unique
            .iter()
            .filter(|id| !self.entries.contains_key(*id))
            .count();
        let total = self.entries.len() + added;
        let rebuild = self.scale != scale
            || self.layout.map_or(true, |layout| {
                let columns = layout.side / layout.cell;
                total as u64 >= u64::from(columns) * u64::from(columns) || layout.side > max_side
            });
        let mut next = self.clone();
        if rebuild {
            next.layout = Some(GuiItemRasterLayout::new(
                scale,
                unique.len() as u32,
                max_side,
            )?);
            next.entries.clear();
            next.scale = scale;
        } else if total > MAX_ITEMS as usize {
            return Err(GalError::invalid_argument(
                "item raster identity residency bound exceeded",
            ));
        }
        for id in identities {
            let index = next.entries.len() as u32;
            next.entries.entry(id.clone()).or_insert(index);
        }
        let layout = next.layout.as_mut().expect("validated raster layout");
        layout.items = next.entries.len() as u32;
        let placements = identities
            .iter()
            .map(|id| layout.placement_with_rows(next.entries[id], GuiItemRasterRows::BottomUp))
            .collect::<GalResult<Vec<_>>>()?;
        *self = next;
        Ok(placements)
    }
}

/// One bounded Rust-owned intermediate. Its views never leave GAL, and it
/// does not acquire a surface or submit/present independently of the frame.
pub(crate) struct GuiItemRasterTarget {
    pub color: Handle,
    pub view: Handle,
    pub target: Handle,
    pub pass: Handle,
    pub extent: Extent3d,
}

impl GuiItemRasterTarget {
    pub fn create(gal: &mut VulkanicGal, extent: Extent3d) -> GalResult<Self> {
        if extent.width == 0
            || extent.height == 0
            || extent.depth != 1
            || extent.width > 4096
            || extent.height > 4096
        {
            return Err(GalError::invalid_argument(
                "invalid bounded item raster target extent",
            ));
        }
        let mut created = Vec::new();
        let result = (|| {
            let color = gal.create_texture(TextureDesc {
                label: "gui.item-raster.color".into(),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(color);
            let view = gal.create_texture_view(TextureViewDesc {
                label: "gui.item-raster.view".into(),
                texture: color,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(view);
            let target = gal.create_render_target(RenderTargetDesc {
                label: "gui.item-raster.target".into(),
                color_views: vec![view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(target);
            let pass = gal.create_render_pass(RenderPassDesc {
                label: "gui.item-raster.pass".into(),
                target,
                color_formats: vec![TextureFormat::Rgba8Unorm],
                depth_format: None,
            })?;
            created.push(pass);
            Ok(Self {
                color,
                view,
                target,
                pass,
                extent,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    /// Call only after dependent compositor bindings have been retired.
    /// GAL owns completion-aware destruction of the underlying objects.
    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        let mut error = None;
        for handle in [self.pass, self.target, self.view, self.color] {
            if let Err(cause) = gal.destroy(handle) {
                if error.is_none() {
                    error = Some(cause);
                }
            }
        }
        match error {
            Some(error) => Err(error),
            None => Ok(()),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct GuiItemRasterLayout {
    side: u32,
    cell: u32,
    items: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct GuiItemRasterPlacement {
    pub target_extent: [u32; 2],
    pub rect: [u32; 4],
    pub rows: GuiItemRasterRows,
}

impl GuiItemRasterLayout {
    /// Initial allocation policy of vanilla's item raster atlas, expressed
    /// in Rust. Cached identity lifetime/growth is deliberately not admitted
    /// by this stateless layout; callers must invalidate that cache explicitly.
    pub fn new(gui_scale: u32, items: u32, max_texture_side: u32) -> GalResult<Self> {
        if gui_scale == 0 || items == 0 || items > MAX_ITEMS || max_texture_side < 512 {
            return Err(GalError::invalid_argument("invalid GUI item raster layout"));
        }
        let cell = gui_scale
            .checked_mul(16)
            .ok_or_else(|| GalError::invalid_argument("GUI item raster cell overflow"))?;
        let padded = items + items / 2;
        let mut square_side = 1u32;
        while square_side * square_side < padded {
            square_side += 1;
        }
        let desired = square_side
            .checked_mul(cell)
            .and_then(u32::checked_next_power_of_two)
            .ok_or_else(|| GalError::invalid_argument("GUI item raster extent overflow"))?;
        let side = desired.max(512).min(max_texture_side);
        let columns = side / cell;
        if columns == 0 || u64::from(columns) * u64::from(columns) < u64::from(items) {
            return Err(GalError::invalid_argument(
                "GUI item raster target cannot fit the complete frame",
            ));
        }
        Ok(Self { side, cell, items })
    }

    pub fn placement(self, index: u32) -> GalResult<GuiItemRasterPlacement> {
        self.placement_with_rows(index, GuiItemRasterRows::TopDown)
    }

    /// Item-local row orientation is part of the explicit raster transform,
    /// not a backend query. Vanilla item cells use BottomUp: nearest sampling
    /// at exact texel boundaries is sensitive to interpolation direction.
    pub fn placement_with_rows(
        self,
        index: u32,
        rows: GuiItemRasterRows,
    ) -> GalResult<GuiItemRasterPlacement> {
        if index >= self.items {
            return Err(GalError::invalid_argument(
                "GUI item raster index out of bounds",
            ));
        }
        let columns = self.side / self.cell;
        let y = (index / columns) * self.cell;
        let y = match rows {
            GuiItemRasterRows::TopDown => y,
            GuiItemRasterRows::BottomUp => self.side - y - self.cell,
        };
        Ok(GuiItemRasterPlacement {
            target_extent: [self.side; 2],
            rows,
            rect: [(index % columns) * self.cell, y, self.cell, self.cell],
        })
    }
}

impl GuiItemRasterPlacement {
    pub(crate) fn validate(self) -> GalResult<()> {
        let [x, y, width, height] = self.rect;
        if width == 0
            || height == 0
            || self.target_extent.iter().any(|v| *v > i32::MAX as u32)
            || x.checked_add(width)
                .map_or(true, |end| end > self.target_extent[0])
            || y.checked_add(height)
                .map_or(true, |end| end > self.target_extent[1])
        {
            return Err(GalError::invalid_argument(
                "invalid GUI item raster placement",
            ));
        }
        Ok(())
    }
    /// Lower item-local geometry, not final screen coordinates, into the
    /// explicitly allocated raster target. Screen clipping and composition
    /// remain a separate command; rasterization clips only to this item cell.
    pub fn lower_quad(
        self,
        local: &crate::render::guirender::frontend::GuiAffineQuadRequest,
    ) -> GalResult<crate::render::guirender::frontend::GuiAffineQuadRequest> {
        let [x, y, width, height] = self.rect;
        self.validate()?;
        let p0 = self.raster_point([local.x0, local.y0])?;
        let p1 = self.raster_point([local.x1, local.y1])?;
        let p3 = self.raster_point([local.x3, local.y3])?;
        let mut raster = local.clone();
        [raster.x0, raster.y0] = p0;
        [raster.x1, raster.y1] = p1;
        [raster.x3, raster.y3] = p3;
        [raster.gui_width, raster.gui_height] = self.target_extent;
        raster.projection_extent = self.target_extent.map(|v| v as f32);
        raster.clip_mode = 1;
        raster.clip_left = x as i32;
        raster.clip_top = y as i32;
        raster.clip_width = width as i32;
        raster.clip_height = height as i32;
        Ok(raster)
    }

    /// Transform a semantic local GUI point (16 logical units per standard
    /// item) into this offscreen pass. No rounding, clamping or texel bias.
    pub fn raster_point(self, point: [f32; 2]) -> GalResult<[f32; 2]> {
        if point.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(
                "non-finite GUI item raster point",
            ));
        }
        self.validate()?;
        let y = match self.rows {
            GuiItemRasterRows::TopDown => {
                self.rect[1] as f32 + point[1] * (self.rect[3] as f32 / 16.0)
            }
            GuiItemRasterRows::BottomUp => {
                (self.rect[1] + self.rect[3]) as f32 - point[1] * (self.rect[3] as f32 / 16.0)
            }
        };
        let result = [
            self.rect[0] as f32 + point[0] * (self.rect[2] as f32 / 16.0),
            y,
        ];
        if result.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument("GUI item raster point overflow"));
        }
        Ok(result)
    }

    /// Backend-neutral sampled subresource coordinates for final composition.
    /// The inverse of the authored intermediate transform, on either backend.
    pub fn composite_uv(self) -> [f32; 4] {
        let low = self.rect[1] as f32 / self.target_extent[1] as f32;
        let high = (self.rect[1] + self.rect[3]) as f32 / self.target_extent[1] as f32;
        let [v0, v1] = match self.rows {
            GuiItemRasterRows::TopDown => [low, high],
            GuiItemRasterRows::BottomUp => [high, low],
        };
        [
            self.rect[0] as f32 / self.target_extent[0] as f32,
            v0,
            (self.rect[0] + self.rect[2]) as f32 / self.target_extent[0] as f32,
            v1,
        ]
    }
}

#[cfg(test)]
mod tests;
