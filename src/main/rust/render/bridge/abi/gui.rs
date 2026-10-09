//! GUI frame, quad, mesh, asset and atlas-reference records.

use super::*;

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiGuiSpriteRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub sprite_id: u32,
    pub selected_slot: i32,
    pub progress_fraction: f32,
    pub fill_direction: u32,
    pub color_argb: u32,
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
    pub gui_width: i32,
    pub gui_height: i32,
    pub sequence: u64,
}

/// Generic GUI image quad. Java supplies affine screen-space geometry and
/// semantic image identity only; Rust owns its texture resource and batching.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiGuiAffineQuadRequest {
    pub byte_size: u32,
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
    pub gui_width: i32,
    pub gui_height: i32,
    pub sequence: u64,
    pub clip_mode: u32,
    pub clip_left: i32,
    pub clip_top: i32,
    pub clip_width: i32,
    pub clip_height: i32,
    pub material_mode: u32,
    pub item_raster_scale: u32,
    pub item_raster_corners: [f32; 6],
    pub item_raster_layers: FfiSlice<FfiGuiItemRasterLayer>,
}

/// ABI38: immutable item-local layers nested under one GUI presentation.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct FfiGuiItemRasterLayer {
    pub byte_size: u32,
    pub material_mode: u32,
    pub asset_id: u64,
    pub color_argb: u32,
    pub corners: [f32; 6],
    pub uv: [f32; 4],
    pub model_transform: [f32; 16],
}

/// ABI v29 typed tiled-GUI semantics. The producer remains diagnostic-only
/// until real paired captures establish admission; no native GPU handles cross.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiGuiTiledQuadRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub asset_id: u64,
    pub bounds: [i32; 4],
    pub tile_extent: [u32; 2],
    pub uv: [f32; 4],
    pub pose: [f32; 6],
    pub z: f32,
    pub color_argb: u32,
    pub sequence: u64,
    pub clip_mode: u32,
    pub clip: [i32; 4],
}

/// Fixed copied vertex for the private Rust-owned GUI mesh family. This is a
/// semantic vertex record: no Java renderer object, atlas object, or native
/// resource identity crosses this boundary.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiGuiMeshVertex {
    pub position: [f32; 3],
    pub atlas_uv: [f32; 2],
    pub local_uv: [f32; 2],
    pub color_argb: u32,
    pub normal_packed: u32,
    pub source_face: u32,
    pub source_foil_type: u32,
}

/// One coarse material-homogeneous GUI item mesh layer. Geometry payloads are
/// copied through nested bounded slices; the Rust GUI mesh frontend owns
/// offscreen targets, material resources, batching, and execution.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiGuiMeshBatchRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub layer_index: u32,
    pub material_mode: u32,
    pub lighting_mode: u32,
    pub asset_id: u64,
    pub sequence: u64,
    pub alpha_cutoff: f32,
    pub reserved0: u32,
    pub model_transform: [f32; 16],
    pub gui_pose: [f32; 6],
    pub left: i32,
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
    pub gui_width: i32,
    pub gui_height: i32,
    pub render_width: i32,
    pub render_height: i32,
    pub guard_pixels: u32,
    pub clip_mode: u32,
    pub clip_left: i32,
    pub clip_top: i32,
    pub clip_width: i32,
    pub clip_height: i32,
    pub vertices: FfiSlice<FfiGuiMeshVertex>,
    pub indices: FfiSlice<u32>,
    pub item_foil_mode: u32,
    pub item_foil_clock_millis: u64,
    pub item_foil_speed: f64,
    pub item_foil_strength: f32,
    pub item_raster_scale: u32,
    pub decal_foil_mode: u32,
    pub decal_model_pose: [f32; 16],
    pub decal_normal_pose: [f32; 9],
    pub block_item_scale: u32,
    pub block_model_bounds: [f64; 6],
    pub block_item_layout: u32,
    pub item_cache_identity: u64,
    pub item_cache_mode: u32,
    /// Immutable CPU transform owner. Mode 0 uses the inline matrix, 1 right, 2 left.
    pub native_item_transform: u64,
    pub native_item_transform_mode: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiGuiFrameSubmitRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub frame_id: u64,
    pub frame_target: FfiHandle,
    pub gui_width: i32,
    pub gui_height: i32,
    pub sprites: FfiSlice<FfiGuiSpriteRequest>,
    pub affine_quads: FfiSlice<FfiGuiAffineQuadRequest>,
    pub negotiated_feature_bits: u64,
    /// Appended semantic GUI item meshes. Each item may contain several
    /// ordered layers sharing one scheduler sequence.
    pub mesh_batches: FfiSlice<FfiGuiMeshBatchRequest>,
    pub gui_projection_width: f32,
    pub gui_projection_height: f32,
    pub tiled_quads: FfiSlice<FfiGuiTiledQuadRequest>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGuiFrameSubmitResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub submission_id: u64,
    pub sprite_count: u64,
    pub sprite_batch_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub resource_creates: u64,
    pub command_lists: u64,
    pub command_ops: u64,
    pub metrics: FfiMetricsSnapshot,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGuiAssetPayload {
    pub byte_size: u32,
    pub sprite_id: u32,
    pub png_bytes: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGuiAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiGuiAssetPayload>,
    pub negotiated_feature_bits: u64,
}

/// Versioned raw GUI image transport. It is intentionally separate from the
/// legacy PNG sprite registry: font atlases are CPU-source pixel data, not
/// renderer textures or atlas handles.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGuiRawImageAssetPayload {
    pub byte_size: u32,
    pub format: u32,
    pub asset_id: u64,
    pub width: i32,
    pub height: i32,
    pub pixels: FfiBytes,
    pub sampling_filter: u32,
    pub sampling_address: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGuiRawImageUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiGuiRawImageAssetPayload>,
    pub negotiated_feature_bits: u64,
    /// Nonempty: complete live identity set, with pixels supplied only for updates.
    /// Empty: legacy full replacement (including an explicit empty reset).
    pub retained_asset_ids: FfiSlice<u64>,
}

/// Immutable semantic atlas identity and region, never pixels or GPU handles.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGuiAtlasReference {
    pub byte_size: u32,
    pub texture_id: u32,
    pub asset_id: u64,
    pub atlas_generation: u64,
    pub atlas_width: u32,
    pub atlas_height: u32,
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGuiAtlasReferenceUpdate {
    pub header: FfiHeader,
    pub revision: u64,
    pub references: FfiSlice<FfiGuiAtlasReference>,
    pub negotiated_feature_bits: u64,
}

impl Default for FfiGuiFrameSubmitResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            submission_id: 0,
            sprite_count: 0,
            sprite_batch_count: 0,
            cache_hits: 0,
            cache_misses: 0,
            resource_creates: 0,
            command_lists: 0,
            command_ops: 0,
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}
