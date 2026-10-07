//! Semantic GUI mesh requests and their prepared, backend-neutral draws.

use super::*;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum GuiMeshMaterialMode {
    Opaque,
    Cutout,
    Translucent,
    Glint,
    /// Fullscreen panorama image sampling. This is deliberately separate
    /// from opaque item geometry: vanilla's panorama pipeline has no culling
    /// or depth attachment interaction.
    Panorama,
    /// Vanilla entity_no_outline layers: two-sided alpha, no depth writes,
    /// and front/back directional lighting. Not ordinary translucent items.
    ModelOverlay,
    /// Vanilla entity/armor cutout geometry: alpha-tested and two-sided.
    EntityCutoutNoCull,
    /// Vanilla player/entity translucent geometry: alpha-tested, blended, and two-sided.
    EntityTranslucentNoCull,
    /// Vanilla armor decal cutout: two-sided alpha test with Equal depth and depth writes.
    EntityDecalCutoutNoCull,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum GuiMeshLightingMode {
    Flat,
    Block,
    /// Ordinary inventory models, with normals in the Y-down GUI space.
    /// Distinct from upright picture-in-picture model lighting.
    InventoryBlock,
    /// Vanilla gui_light=front models: ITEMS_FLAT directional lights, not unlit.
    FrontModel,
    /// Ordinary entity preview lighting (Lighting.ENTITY_IN_UI).
    EntityPreview,
    /// Oversized special-renderer items (Frozen OversizedItemRenderer):
    /// entity materials lit by ITEMS_3D in its (f, -f, f) PIP normal space,
    /// the same light space as ordinary inventory models.
    OversizedItem,
    /// Oversized items whose model does not use block light: ITEMS_FLAT.
    OversizedItemFlat,
}

impl GuiMeshLightingMode {
    /// Entity-material item layers: entity preview or oversized item lights.
    pub fn is_entity_material_lighting(self) -> bool {
        matches!(
            self,
            Self::EntityPreview | Self::OversizedItem | Self::OversizedItemFlat
        )
    }
}

/// One copied model vertex with the stable vanilla signed-i8 normal encoding.
/// Native item layouts carry original model-space normals for Rust to transform;
/// explicit legacy mesh requests already carry item-lighting-space normals and
/// must not receive a second normal transform.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiMeshVertex {
    pub position: [f32; 3],
    pub atlas_uv: [f32; 2],
    pub local_uv: [f32; 2],
    pub color_argb: u32,
    pub normal_packed: u32,
    /// Original baked face: 0 absent, 1..6 down/up/north/south/west/east.
    pub source_face: u32,
    /// Original layer foil type: 0 none, 1 standard. Not a lighting policy.
    pub source_foil_type: u32,
}

/// Immutable semantic poses for sheeted GUI foil. These are the unscaled model
/// and normal poses used to emit vertices, never inverse/decal texture matrices.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiDecalFoilProjection {
    pub native_item_layout: bool,
    pub model_pose: [f32; 16],
    pub normal_pose: [f32; 9],
}

impl GuiDecalFoilProjection {
    pub(crate) fn decode(
        mode: u32,
        model_pose: [f32; 16],
        normal_pose: [f32; 9],
    ) -> GalResult<Option<Self>> {
        match mode {
            0 if model_pose
                .iter()
                .chain(normal_pose.iter())
                .all(|v| v.to_bits() == 0) =>
            {
                Ok(None)
            }
            1 => {
                let value = Self {
                    native_item_layout: false,
                    model_pose,
                    normal_pose,
                };
                value.prepare(None)?;
                Ok(Some(value))
            }
            2 if model_pose
                .iter()
                .chain(normal_pose.iter())
                .all(|v| v.to_bits() == 0) =>
            {
                Ok(Some(Self {
                    native_item_layout: true,
                    model_pose,
                    normal_pose,
                }))
            }
            _ => Err(GalError::invalid_argument(
                "invalid or noncanonical GUI decal foil projection",
            )),
        }
    }

    pub(super) fn prepare(
        self,
        native_model: Option<[f32; 16]>,
    ) -> GalResult<crate::render::shared::special_item_foil::SpecialFoilProjection> {
        if self.native_item_layout {
            if self
                .model_pose
                .iter()
                .chain(self.normal_pose.iter())
                .any(|v| v.to_bits() != 0)
            {
                return Err(GalError::invalid_argument(
                    "native item decal layout cannot carry caller raster poses",
                ));
            }
            return crate::render::shared::special_item_foil::SpecialFoilProjection::from_native_gui_model(
                native_model.ok_or_else(|| {
                    GalError::invalid_argument(
                        "native decal layout requires native item raster semantics",
                    )
                })?,
            );
        }
        if native_model.is_some() {
            return Err(GalError::invalid_argument(
                "emitted-space decal poses cannot be used as model-space item poses",
            ));
        }
        crate::render::shared::special_item_foil::SpecialFoilProjection::new(
            self.model_pose,
            self.normal_pose,
            crate::render::shared::special_item_foil::FoilDisplayContext::Gui,
        )
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct GuiItemCache {
    pub identity: u64,
    pub animated: bool,
}

impl GuiItemCache {
    pub fn decode(identity: u64, mode: u32) -> GalResult<Option<Self>> {
        match (identity, mode) {
            (0, 0) => Ok(None),
            (identity, 1 | 2) if identity != 0 => Ok(Some(Self {
                identity,
                animated: mode == 2,
            })),
            _ => Err(GalError::invalid_argument(
                "invalid semantic GUI item cache identity or mode",
            )),
        }
    }
}

/// A material-homogeneous indexed mesh for one GUI item layer. `asset_id`
/// refers to a Rust-owned raw image asset, never a Minecraft atlas object.
#[derive(Clone, Debug, PartialEq)]
pub struct GuiMeshBatchRequest {
    pub item_cache: Option<GuiItemCache>,
    /// Private native contract: copied model-space bounds, original normals,
    /// and GUI scale. No caller-selected offscreen extent/guard/raster matrix.
    pub block_item_raster: Option<GuiBlockItemRaster>,
    /// Zero for explicit meshes; positive for a native 16-unit flat item cell.
    pub item_raster_scale: u32,
    /// Resolved solely from the Rust-owned frame lightmap, never transported.
    pub item_lighting: Option<GuiFlatItemLighting>,
    /// When present, vertices contain original atlas UVs and Rust prepares
    /// standard item foil. Absent for ordinary or explicitly prepared meshes.
    pub item_foil: Option<GuiItemFoil>,
    /// Optional sheeted coordinate generation before native glint animation.
    /// Requires item foil; flat GUI callsites request the native-owned item layout.
    pub decal_foil: Option<GuiDecalFoilProjection>,
    pub stratum: u32,
    /// Ordering within one item PIP raster. Every layer of a GUI item shares
    /// its scheduler sequence and composes only after the final layer.
    pub layer_index: u32,
    pub sequence: u64,
    pub asset_id: u64,
    pub material_mode: GuiMeshMaterialMode,
    pub lighting_mode: GuiMeshLightingMode,
    pub alpha_cutoff: f32,
    /// Vanilla-resolved item-layer transform copied before FFI.
    pub model_transform: [f32; 16],
    /// GUI affine pose expressed as m00, m01, m10, m11, m20, m21.
    pub gui_pose: [f32; 6],
    /// Logical GUI placement bounds: left, top, right, bottom.
    pub bounds: [i32; 4],
    pub gui_extent: [u32; 2],
    pub projection_extent: [f32; 2],
    /// Rust-owned offscreen raster extent, including native guard padding.
    /// This is deliberately distinct from the final GUI viewport.
    pub render_extent: [u32; 2],
    /// Copied PIP guard band. Composition excludes it from the visible GUI
    /// rectangle while rasterization retains it for filtered edge safety.
    pub guard_pixels: u32,
    pub clip_mode: u32,
    pub clip_left: i32,
    pub clip_top: i32,
    pub clip_width: i32,
    pub clip_height: i32,
    pub vertices: Vec<GuiMeshVertex>,
    pub indices: Vec<u32>,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiBlockItemRaster {
    pub model_min: [f64; 3],
    pub model_max: [f64; 3],
    pub gui_scale: u32,
    pub oversized_gui: bool,
}

impl GuiBlockItemRaster {
    pub(crate) fn decode(gui_scale: u32, bounds: [f64; 6], mode: u32) -> GalResult<Option<Self>> {
        if gui_scale == 0 {
            if mode != 0 || bounds.iter().any(|v| v.to_bits() != 0) {
                return Err(GalError::invalid_argument(
                    "absent block layout requires canonical zero bounds",
                ));
            }
            return Ok(None);
        }
        let model_min = [bounds[0], bounds[1], bounds[2]];
        let model_max = [bounds[3], bounds[4], bounds[5]];
        if !matches!(mode, 1 | 2) {
            return Err(GalError::invalid_argument("invalid block item layout mode"));
        }
        crate::render::guirender::items::layout::GuiItemRasterLayout::block_bounds(model_min, model_max, gui_scale)?;
        Ok(Some(Self {
            model_min,
            model_max,
            gui_scale,
            oversized_gui: mode == 2,
        }))
    }

    pub(super) fn oversized_layout(
        self,
        bounds: [i32; 4],
    ) -> GalResult<Option<(crate::render::guirender::items::layout::GuiItemRasterLayout, [i32; 4])>> {
        if !self.oversized_gui {
            return Ok(None);
        }
        if bounds[2] as i64 - bounds[0] as i64 != 16 || bounds[3] as i64 - bounds[1] as i64 != 16 {
            return Err(GalError::invalid_argument(
                "native oversized item requires original logical item box",
            ));
        }
        crate::render::guirender::items::layout::GuiItemRasterLayout::oversized_gui(
            self.model_min,
            self.model_max,
            self.gui_scale,
            [bounds[0], bounds[1]],
        )
    }
}

/// Backend-neutral, owned vertex data produced after the Java-owned source
/// snapshot is validated. The future GUI mesh pass may choose its private
/// buffer layout, but it must consume these copied semantics rather than a
/// Java model or PIP object.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiMeshPreparedVertex {
    pub position: [f32; 3],
    pub local_uv: [f32; 2],
    pub color: [f32; 4],
    pub normal: [f32; 3],
}

#[derive(Clone, Debug, PartialEq)]
pub struct GuiMeshPreparedDraw {
    pub item_cache: Option<GuiItemCache>,
    pub stratum: u32,
    pub layer_index: u32,
    pub sequence: u64,
    pub asset_id: u64,
    pub material_mode: GuiMeshMaterialMode,
    /// The copied item transform can contain a reflection (vanilla's GUI PIP
    /// pose does). Keep the resulting winding explicit so back-face culling
    /// remains correct instead of exposing the model interior.
    pub front_face: crate::render::vulkanic::resources::FrontFace,
    pub lighting_mode: GuiMeshLightingMode,
    pub alpha_cutoff: f32,
    pub gui_pose: [f32; 6],
    pub bounds: [i32; 4],
    pub gui_extent: [u32; 2],
    pub projection_extent: [f32; 2],
    pub render_extent: [u32; 2],
    pub guard_pixels: u32,
    pub clip_mode: u32,
    pub clip_left: i32,
    pub clip_top: i32,
    pub clip_width: i32,
    pub clip_height: i32,
    pub vertices: Vec<GuiMeshPreparedVertex>,
    pub indices: Vec<u32>,
    /// Affine texture transform applied to `local_uv` in the vertex shader
    /// (`[column0, column1, translation]`). Standard item foil animates only
    /// this per-draw uniform, so its geometry stays resident across frames.
    pub uv_transform: [[f32; 2]; 3],
}

/// Identity `GuiMeshPreparedDraw::uv_transform`.
pub const GUI_MESH_IDENTITY_UV_TRANSFORM: [[f32; 2]; 3] = [[1.0, 0.0], [0.0, 1.0], [0.0, 0.0]];

/// Stable process-local identity for copied GUI geometry. Transform, clip,
/// and layer fields are intentionally excluded: those remain per-draw
/// uniforms, while this key permits immutable vertex/index residency across
/// frames without retaining Java objects or native pointers.
pub fn geometry_fingerprint(draw: &GuiMeshPreparedDraw) -> u64 {
    // GUI item caches fingerprint every mesh draw each frame. Pack the same
    // fields as before into one buffer and hash it with XXH3 instead of
    // streaming every component through SipHash.
    thread_local! {
        static BYTES: std::cell::RefCell<Vec<u8>> = const { std::cell::RefCell::new(Vec::new()) };
    }
    BYTES.with(|bytes| {
        let mut bytes = bytes.borrow_mut();
        bytes.clear();
        bytes.reserve(16 + draw.vertices.len() * 48 + std::mem::size_of_val(draw.indices.as_slice()));
        bytes.extend_from_slice(&(draw.vertices.len() as u64).to_le_bytes());
        for vertex in &draw.vertices {
            // One fixed-size block per vertex keeps the copy branch-free.
            let mut block = [0u8; 48];
            let values = vertex
                .position
                .iter()
                .chain(&vertex.local_uv)
                .chain(&vertex.color)
                .chain(&vertex.normal);
            for (chunk, value) in block.chunks_exact_mut(4).zip(values) {
                chunk.copy_from_slice(&value.to_bits().to_le_bytes());
            }
            bytes.extend_from_slice(&block);
        }
        bytes.extend_from_slice(&(draw.indices.len() as u64).to_le_bytes());
        for index in &draw.indices {
            bytes.extend_from_slice(&index.to_le_bytes());
        }
        xxhash_rust::xxh3::xxh3_64(&bytes)
    })
}

/// One non-overlapping range in a persistent GUI-mesh stream. A command list
/// may rasterize several quads that use the same texture; every draw must
/// retain its own bytes until GPU execution instead of overwriting offset zero
/// before the submission reaches the backend.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct GuiMeshStreamRange {
    pub vertex_offset: u64,
    pub index_offset: u64,
}

impl GuiMeshBatchRequest {
    pub(super) fn requires_item_lightmap(&self) -> bool {
        (self.item_raster_scale != 0 || self.lighting_mode == GuiMeshLightingMode::InventoryBlock)
            && self.material_mode != GuiMeshMaterialMode::Glint
    }

    pub fn resolve_item_lighting(
        &mut self,
        frame: Option<crate::render::shaderpack::vanilla::lightmap::VanillaLightmapFrame>,
    ) -> GalResult<()> {
        if self.requires_item_lightmap() {
            self.item_lighting = Some(GuiFlatItemLighting::prepare(frame.ok_or_else(|| {
                GalError::invalid_argument("GUI item mesh requires explicit frame lightmap inputs")
            })?)?);
        }
        Ok(())
    }
}
