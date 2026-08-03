use super::*;

pub const FFI_ABI_V1_VERSION: u32 = 1;
pub const FFI_ABI_VERSION: u32 = 2;
pub const FFI_INITIAL_PRESENTATION_SUPPORTED: bool = false;
pub const FFI_ABI_NAME: &str = "MattMC VulkanicGAL Java-Rust batch ABI";
pub const FFI_MAX_LABEL_BYTES: usize = 1024;
pub const FFI_MAX_SHADER_BYTES: usize = 16 * 1024 * 1024;
pub const FFI_MAX_INLINE_BYTES: usize = 64 * 1024 * 1024;
pub const FFI_MAX_GUI_ASSET_BYTES: usize = 4 * 1024 * 1024;
pub const FFI_MAX_WORLD_BORDER_ASSET_BYTES: usize = 2 * 1024 * 1024;
pub const FFI_MAX_WORLD_CRACK_ASSET_BYTES: usize = 4 * 1024 * 1024;
pub const FFI_MAX_WORLD_MATERIAL_ASSET_BYTES: usize = 4 * 1024 * 1024;
pub const FFI_MAX_WORLD_MESH_TEXTURE_ASSET_BYTES: usize = 4 * 1024 * 1024;
pub const FFI_MAX_WORLD_MESH_INDEX_BYTES: usize = 1024 * 1024;
pub const FFI_MAX_BATCH_ITEMS: usize = 65_536;

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FfiBackendKind {
    Vulkan = 1,
    OpenGl = 2,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiStructLayout {
    pub header: FfiHeader,
    pub struct_id: u32,
    pub byte_size: u32,
    pub alignment: u32,
    pub field_count: u32,
    pub field_offsets: [u32; 64],
}

impl Default for FfiStructLayout {
    fn default() -> Self {
        Self {
            header: FfiHeader::default(),
            struct_id: 0,
            byte_size: 0,
            alignment: 0,
            field_count: 0,
            field_offsets: [0; 64],
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiContextCreateRequest {
    pub header: FfiHeader,
    pub backend_kind: u32,
    pub tracy_enabled: u32,
    pub label: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiContextResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub context_id: u64,
    pub supported_feature_bits: u64,
    pub limits: FfiBackendLimits,
    pub metrics: FfiMetricsSnapshot,
}

impl Default for FfiContextResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            context_id: 0,
            supported_feature_bits: 0,
            limits: FfiBackendLimits::default(),
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiBorrowedOpenGlContextCreateRequest {
    pub header: FfiHeader,
    pub stable_window_id: u64,
    pub tracy_enabled: u32,
    pub reserved0: u32,
    pub label: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWindowedVulkanContextCreateRequest {
    pub header: FfiHeader,
    pub platform: u32,
    pub tracy_enabled: u32,
    pub stable_window_id: u64,
    pub native_display: u64,
    pub native_window: u64,
    pub label: FfiBytes,
    pub surface_label: FfiBytes,
    pub extent: FfiExtent3d,
    pub color_format: u32,
    pub present_mode: u32,
    pub max_frames_in_flight: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameSurfaceConfigRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub extent: FfiExtent3d,
    pub color_format: u32,
    pub present_mode: u32,
    pub max_frames_in_flight: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameAcquireRequest {
    pub header: FfiHeader,
    pub correlation_id: u64,
    pub expected_extent: FfiExtent3d,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameAcquireResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub acquire_status: u32,
    pub frame_target: FfiHandle,
    pub frame_target_identity: u64,
    pub extent: FfiExtent3d,
    pub color_format: u32,
    pub metrics: FfiMetricsSnapshot,
}

impl Default for FfiFrameAcquireResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            frame_id: 0,
            correlation_id: 0,
            acquire_status: 0,
            frame_target: FfiHandle::default(),
            frame_target_identity: 0,
            extent: FfiExtent3d::default(),
            color_format: 0,
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameResizeRequest {
    pub header: FfiHeader,
    pub correlation_id: u64,
    pub extent: FfiExtent3d,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameResizeResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub resize_status: u32,
    pub extent: FfiExtent3d,
}

impl Default for FfiFrameResizeResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            resize_status: 0,
            extent: FfiExtent3d::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFramePresentRequest {
    pub header: FfiHeader,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub wait_submission_id: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFramePresentResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub present_status: u32,
    pub completed_submission_id: u64,
    pub frame_target_identity: u64,
}

impl Default for FfiFramePresentResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            frame_id: 0,
            correlation_id: 0,
            present_status: 0,
            completed_submission_id: 0,
            frame_target_identity: 0,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiReadbackRequest {
    pub header: FfiHeader,
    pub submission_id: u64,
    pub buffer: FfiHandle,
    pub offset: u64,
    pub size: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiReadbackResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub submission_id: u64,
    pub required_bytes: u64,
    pub written_bytes: u64,
    pub metrics: FfiMetricsSnapshot,
}

impl Default for FfiReadbackResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            submission_id: 0,
            required_bytes: 0,
            written_bytes: 0,
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiHeader {
    pub version: u32,
    pub byte_size: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiBytes {
    pub ptr: *const u8,
    pub len: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiSlice<T> {
    pub ptr: *const T,
    pub count: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiHandle {
    pub raw: u64,
}

impl From<Handle> for FfiHandle {
    fn from(handle: Handle) -> Self {
        Self { raw: handle.raw() }
    }
}

impl From<FfiHandle> for Handle {
    fn from(handle: FfiHandle) -> Self {
        Handle::from_raw(handle.raw)
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub handle: FfiHandle,
    pub submission_id: u64,
    pub required_bytes: u64,
}

impl Default for FfiResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            handle: FfiHandle::default(),
            submission_id: 0,
            required_bytes: 0,
        }
    }
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FfiMemoryDomain {
    DeviceLocal = 1,
    Upload = 2,
    Readback = 3,
}

impl FfiMemoryDomain {
    pub fn validate(raw: u32) -> GalResult<Self> {
        match raw {
            1 => Ok(Self::DeviceLocal),
            2 => Ok(Self::Upload),
            3 => Ok(Self::Readback),
            _ => Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown memory domain {raw}"),
            )),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiBufferCreateRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub size: u64,
    pub memory_domain: u32,
    pub usage_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceUse {
    pub resource: FfiHandle,
    pub stage_bits: u32,
    pub access_bits: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiCommandListRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub encoded_ops: FfiBytes,
    pub resource_uses: FfiSlice<FfiResourceUse>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiSubmissionRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub command_lists: FfiSlice<FfiCommandListRequest>,
}

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
    pub negotiated_feature_bits: u64,
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

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldBorderAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub texture_id: u32,
    pub reserved0: u32,
    pub png_bytes: FfiBytes,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldCrackAssetPayload {
    pub byte_size: u32,
    pub stage: u32,
    pub png_bytes: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldCrackAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiWorldCrackAssetPayload>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMaterialAssetPayload {
    pub byte_size: u32,
    pub texture_id: u32,
    pub png_bytes: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMaterialAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub assets: FfiSlice<FfiWorldMaterialAssetPayload>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldLineSegmentRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub style: u32,
    pub depth_policy: u32,
    pub color_argb: u32,
    pub line_width: f32,
    pub start_x: f32,
    pub start_y: f32,
    pub start_z: f32,
    pub end_x: f32,
    pub end_y: f32,
    pub end_z: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldCrackQuadRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub stage: u32,
    pub depth_policy: u32,
    pub blend_policy: u32,
    pub cull_policy: u32,
    pub color_argb: u32,
    pub reserved0: u32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldBorderQuadRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub texture_id: u32,
    pub depth_policy: u32,
    pub blend_policy: u32,
    pub cull_policy: u32,
    pub color_argb: u32,
    pub reserved0: u32,
    pub border_size: f32,
    pub distance_to_border: f32,
    pub scroll_u: f32,
    pub scroll_v: f32,
    pub uv_u: f32,
    pub uv_v: f32,
    pub uv_width: f32,
    pub uv_height: f32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMaterialQuadRequest {
    pub byte_size: u32,
    pub stratum: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub topology: u32,
    pub color_argb: u32,
    pub reserved0: u32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub uv0_u: f32,
    pub uv0_v: f32,
    pub uv1_u: f32,
    pub uv1_v: f32,
    pub uv2_u: f32,
    pub uv2_v: f32,
    pub uv3_u: f32,
    pub uv3_v: f32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMaterialTableRecord {
    pub byte_size: u32,
    pub stratum: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub topology: u32,
    pub winding: u32,
    pub reserved0: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMaterialCompactQuadRequest {
    pub byte_size: u32,
    pub material_index: u32,
    pub color_argb: u32,
    pub reserved0: u32,
    pub p0_x: f32,
    pub p0_y: f32,
    pub p0_z: f32,
    pub p1_x: f32,
    pub p1_y: f32,
    pub p1_z: f32,
    pub p2_x: f32,
    pub p2_y: f32,
    pub p2_z: f32,
    pub p3_x: f32,
    pub p3_y: f32,
    pub p3_z: f32,
    pub uv0_u: f32,
    pub uv0_v: f32,
    pub uv1_u: f32,
    pub uv1_v: f32,
    pub uv2_u: f32,
    pub uv2_v: f32,
    pub uv3_u: f32,
    pub uv3_v: f32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshVertex {
    pub byte_size: u32,
    pub color_argb: u32,
    pub normal_packed: u32,
    pub light: u32,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub u: f32,
    pub v: f32,
    pub atlas_u: f32,
    pub atlas_v: f32,
    pub shader_block_id: i32,
    pub shader_material_type: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshSectionRecord {
    pub byte_size: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub cull_policy: u32,
    pub winding: u32,
    pub index_offset: u32,
    pub index_count: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshAssetRecord {
    pub byte_size: u32,
    pub vertex_layout_version: u32,
    pub index_type: u32,
    pub reserved0: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub vertices: FfiSlice<FfiWorldMeshVertex>,
    pub index_bytes: FfiBytes,
    pub sections: FfiSlice<FfiWorldMeshSectionRecord>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldMeshTextureAssetPayload {
    pub byte_size: u32,
    pub texture_id: u32,
    pub png_bytes: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub meshes: FfiSlice<FfiWorldMeshAssetRecord>,
    pub textures: FfiSlice<FfiWorldMeshTextureAssetPayload>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWorldMeshInstanceRecord {
    pub byte_size: u32,
    pub stratum: u32,
    pub mesh_section_index: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub winding: u32,
    pub color_argb: u32,
    pub viewport_width: i32,
    pub viewport_height: i32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub transform: [f32; 16],
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWorldBackgroundRequest {
    pub byte_size: u32,
    pub enabled: u32,
    pub sky_type: u32,
    pub load_intent: u32,
    pub store_intent: u32,
    pub color_argb: u32,
    pub viewport_width: i32,
    pub viewport_height: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiWholeFrameSubmitRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub frame_target: FfiHandle,
    pub gui_width: i32,
    pub gui_height: i32,
    pub viewport_width: i32,
    pub viewport_height: i32,
    pub view_matrix: [f32; 16],
    pub projection_matrix: [f32; 16],
    pub world_background: FfiWorldBackgroundRequest,
    pub world_segments: FfiSlice<FfiWorldLineSegmentRequest>,
    pub world_crack_quads: FfiSlice<FfiWorldCrackQuadRequest>,
    pub world_border_quads: FfiSlice<FfiWorldBorderQuadRequest>,
    pub world_material_quads: FfiSlice<FfiWorldMaterialQuadRequest>,
    pub world_material_table: FfiSlice<FfiWorldMaterialTableRecord>,
    pub world_material_compact_quads: FfiSlice<FfiWorldMaterialCompactQuadRequest>,
    pub world_mesh_instances: FfiSlice<FfiWorldMeshInstanceRecord>,
    pub gui_sprites: FfiSlice<FfiGuiSpriteRequest>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiWholeFrameSubmitResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub submission_id: u64,
    pub world_segment_count: u64,
    pub world_vertex_count: u64,
    pub world_batch_count: u64,
    pub world_draw_count: u64,
    pub world_crack_quad_count: u64,
    pub world_crack_batch_count: u64,
    pub world_crack_draw_count: u64,
    pub world_border_quad_count: u64,
    pub world_border_batch_count: u64,
    pub world_border_draw_count: u64,
    pub world_material_quad_count: u64,
    pub world_material_batch_count: u64,
    pub world_material_draw_count: u64,
    pub world_mesh_instance_count: u64,
    pub world_mesh_batch_count: u64,
    pub world_mesh_draw_count: u64,
    pub world_background_clear_count: u64,
    pub world_background_diagnostic_fallback_count: u64,
    pub world_background_sky_type: u64,
    pub world_background_color_argb: u64,
    pub depth_attachment_creates: u64,
    pub depth_attachment_reuses: u64,
    pub depth_attachment_retires: u64,
    pub outline_cache_hits: u64,
    pub outline_cache_misses: u64,
    pub crack_cache_hits: u64,
    pub crack_cache_misses: u64,
    pub border_cache_hits: u64,
    pub border_cache_misses: u64,
    pub material_cache_hits: u64,
    pub material_cache_misses: u64,
    pub mesh_cache_hits: u64,
    pub mesh_cache_misses: u64,
    pub sprite_count: u64,
    pub sprite_batch_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub resource_creates: u64,
    pub command_lists: u64,
    pub command_ops: u64,
    pub metrics: FfiMetricsSnapshot,
    pub profile: FfiWholeFrameProfileSnapshot,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiWholeFrameProfileSnapshot {
    pub ffi_decode_nanos: u64,
    pub gui_frontend_nanos: u64,
    pub world_frontend_total_nanos: u64,
    pub world_validate_frame_nanos: u64,
    pub world_batching_nanos: u64,
    pub world_resource_prepare_nanos: u64,
    pub world_prepare_target_query_nanos: u64,
    pub world_prepare_render_resources_nanos: u64,
    pub world_prepare_depth_attachment_nanos: u64,
    pub world_prepare_g_buffer_resources_nanos: u64,
    pub world_prepare_g_buffer_cache_check_nanos: u64,
    pub world_prepare_g_buffer_destroy_nanos: u64,
    pub world_prepare_g_buffer_plan_nanos: u64,
    pub world_prepare_g_buffer_create_nanos: u64,
    pub world_prepare_frame_pass_nanos: u64,
    pub world_mesh_section_expand_group_nanos: u64,
    pub shader_plan_lookup_nanos: u64,
    pub gal_command_generation_nanos: u64,
    pub gal_submit_total_nanos: u64,
    pub gal_validate_ops_nanos: u64,
    pub gal_validate_handles_nanos: u64,
    pub gal_hazard_analysis_nanos: u64,
    pub backend_encode_nanos: u64,
    pub backend_submit_nanos: u64,
    pub backend_retire_nanos: u64,
    pub vulkan_command_buffer_alloc_nanos: u64,
    pub vulkan_command_buffer_begin_nanos: u64,
    pub vulkan_command_recording_nanos: u64,
    pub vulkan_command_buffer_end_nanos: u64,
    pub vulkan_queue_submit_nanos: u64,
    pub vulkan_timeline_poll_nanos: u64,
    pub vulkan_timeline_wait_nanos: u64,
    pub vulkan_device_wait_idle_nanos: u64,
    pub vulkan_command_buffers_allocated: u64,
    pub vulkan_command_buffers_freed: u64,
    pub vulkan_wait_count: u64,
    pub vulkan_device_wait_idle_count: u64,
    pub resource_creates_delta: u64,
    pub resource_destroys_delta: u64,
    pub host_write_ops: u64,
    pub host_write_bytes: u64,
    pub barrier_ops: u64,
    pub pass_count: u64,
    pub draw_ops: u64,
    pub draw_indexed_ops: u64,
    pub pipeline_binds: u64,
    pub resource_set_binds: u64,
    pub gpu_timestamp_status: u64,
    pub gpu_shadow_depth_nanos: u64,
    pub gpu_terrain_opaque_nanos: u64,
    pub gpu_terrain_cutout_nanos: u64,
    pub gpu_deferred_lighting_nanos: u64,
    pub gpu_composite0_nanos: u64,
    pub gpu_composite1_nanos: u64,
    pub gpu_final_output_nanos: u64,
    pub gpu_frame_total_nanos: u64,
    pub g_buffer_persistent_cache_hits: u64,
    pub g_buffer_persistent_cache_misses: u64,
    pub g_buffer_final_binding_cache_hits: u64,
    pub g_buffer_final_binding_cache_misses: u64,
    pub g_buffer_attachment_creates: u64,
    pub g_buffer_pipeline_creates: u64,
    pub g_buffer_shader_module_creates: u64,
    pub g_buffer_descriptor_creates: u64,
    pub g_buffer_render_target_creates: u64,
    pub g_buffer_resources_retired: u64,
    pub world_prepare_g_buffer_persistent_key_nanos: u64,
    pub world_prepare_g_buffer_persistent_lookup_nanos: u64,
    pub world_prepare_g_buffer_final_key_nanos: u64,
    pub world_prepare_g_buffer_final_lookup_nanos: u64,
    pub world_prepare_g_buffer_final_create_nanos: u64,
    pub world_prepare_frame_target_attachment_query_nanos: u64,
    pub world_prepare_mesh_material_asset_nanos: u64,
    pub world_prepare_metrics_accounting_nanos: u64,
    pub g_buffer_final_pass_creates: u64,
    pub vulkan_acquire_nanos: u64,
    pub vulkan_present_nanos: u64,
    pub vulkan_present_wait_nanos: u64,
    pub vulkan_present_mode: u64,
    pub vulkan_requested_present_mode: u64,
    pub vulkan_supported_present_modes: u64,
    pub vulkan_present_mode_fallback_reason: u64,
    pub vulkan_acquired_image_index: u64,
    pub vulkan_swapchain_generation: u64,
    pub vulkan_swapchain_image_count: u64,
    pub vulkan_surface_min_image_count: u64,
    pub vulkan_surface_max_image_count: u64,
    pub vulkan_configured_frames_in_flight: u64,
    pub vulkan_images_in_flight: u64,
    pub vulkan_available_frame_slots: u64,
    pub gal_hazard_read_events: u64,
    pub gal_hazard_write_events: u64,
    pub gal_hazard_candidates_examined: u64,
    pub gal_hazard_conflicts: u64,
    pub gal_hazard_barriers_applied: u64,
    pub gal_hazard_active_read_entries: u64,
    pub gal_hazard_active_write_entries: u64,
    pub gal_command_ops_before_normalize: u64,
    pub gal_command_ops_after_normalize: u64,
    pub gal_redundant_pipeline_binds_removed: u64,
    pub gal_redundant_resource_set_binds_removed: u64,
    pub gal_redundant_vertex_buffer_binds_removed: u64,
    pub gal_redundant_index_buffer_binds_removed: u64,
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

impl Default for FfiWholeFrameSubmitResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            submission_id: 0,
            world_segment_count: 0,
            world_vertex_count: 0,
            world_batch_count: 0,
            world_draw_count: 0,
            world_crack_quad_count: 0,
            world_crack_batch_count: 0,
            world_crack_draw_count: 0,
            world_border_quad_count: 0,
            world_border_batch_count: 0,
            world_border_draw_count: 0,
            world_material_quad_count: 0,
            world_material_batch_count: 0,
            world_material_draw_count: 0,
            world_mesh_instance_count: 0,
            world_mesh_batch_count: 0,
            world_mesh_draw_count: 0,
            world_background_clear_count: 0,
            world_background_diagnostic_fallback_count: 0,
            world_background_sky_type: 0,
            world_background_color_argb: 0,
            depth_attachment_creates: 0,
            depth_attachment_reuses: 0,
            depth_attachment_retires: 0,
            outline_cache_hits: 0,
            outline_cache_misses: 0,
            crack_cache_hits: 0,
            crack_cache_misses: 0,
            border_cache_hits: 0,
            border_cache_misses: 0,
            material_cache_hits: 0,
            material_cache_misses: 0,
            mesh_cache_hits: 0,
            mesh_cache_misses: 0,
            sprite_count: 0,
            sprite_batch_count: 0,
            cache_hits: 0,
            cache_misses: 0,
            resource_creates: 0,
            command_lists: 0,
            command_ops: 0,
            metrics: FfiMetricsSnapshot::default(),
            profile: FfiWholeFrameProfileSnapshot::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiRange {
    pub offset: u64,
    pub count: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiExtent3d {
    pub width: u32,
    pub height: u32,
    pub depth: u32,
}

impl From<FfiExtent3d> for Extent3d {
    fn from(extent: FfiExtent3d) -> Self {
        Self {
            width: extent.width,
            height: extent.height,
            depth: extent.depth,
        }
    }
}

impl From<Extent3d> for FfiExtent3d {
    fn from(extent: Extent3d) -> Self {
        Self {
            width: extent.width,
            height: extent.height,
            depth: extent.depth,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiTextureOrigin3d {
    pub x: u32,
    pub y: u32,
    pub z: u32,
}

impl From<FfiTextureOrigin3d> for TextureOrigin3d {
    fn from(origin: FfiTextureOrigin3d) -> Self {
        Self {
            x: origin.x,
            y: origin.y,
            z: origin.z,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiTextureSubresourceRange {
    pub base_mip: u32,
    pub mip_count: u32,
    pub base_layer: u32,
    pub layer_count: u32,
}

impl From<FfiTextureSubresourceRange> for TextureSubresourceRange {
    fn from(range: FfiTextureSubresourceRange) -> Self {
        Self {
            base_mip: range.base_mip,
            mip_count: range.mip_count,
            base_layer: range.base_layer,
            layer_count: range.layer_count,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiFeatureBits {
    pub bits: u64,
}

impl FfiFeatureBits {
    pub const GRAPHICS: u64 = 1 << 0;
    pub const COMPUTE: u64 = 1 << 1;
    pub const DESCRIPTOR_ARRAYS: u64 = 1 << 2;
    pub const OPTIONAL_BINDINGS: u64 = 1 << 3;
    pub const DYNAMIC_BUFFER_OFFSETS: u64 = 1 << 4;
    pub const UNIFORM_BUFFERS: u64 = 1 << 5;
    pub const STORAGE_BUFFERS: u64 = 1 << 6;
    pub const STORAGE_TEXTURES: u64 = 1 << 7;
    pub const INDIRECT_DRAW: u64 = 1 << 8;
    pub const INDIRECT_DISPATCH: u64 = 1 << 9;
    pub const MULTIPLE_COLOR_ATTACHMENTS: u64 = 1 << 10;
    pub const DEPTH_ONLY_PASS: u64 = 1 << 11;
    pub const BLENDED_PASS: u64 = 1 << 12;
    pub const TEXTURE_SUBRESOURCE_COPIES: u64 = 1 << 13;
    pub const TEXTURE_MIP_LEVELS: u64 = 1 << 14;
    pub const TEXTURE_ARRAY_LAYERS: u64 = 1 << 15;
    pub const HOST_BUFFER_ACCESS: u64 = 1 << 16;
    pub const PRESENTATION: u64 = 1 << 17;
    pub const RENDERDOC_CAPTURE: u64 = 1 << 18;
    pub const TRACY_ZONES: u64 = 1 << 19;
    pub const ALL_KNOWN: u64 = (1 << 20) - 1;
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiBackendLimits {
    pub max_buffer_size: u64,
    pub max_texture_extent_2d: u32,
    pub max_texture_mip_levels: u32,
    pub max_texture_array_layers: u32,
    pub max_resource_layout_bindings: u32,
    pub max_binding_array_count: u32,
    pub max_color_attachments: u32,
    pub max_dynamic_offsets_per_binding: u32,
    pub max_command_lists_per_submission: u32,
    pub max_commands_per_list: u32,
    pub max_draw_count: u32,
    pub max_dispatch_groups_per_axis: u32,
    pub max_label_bytes: u32,
    pub max_shader_bytes: u32,
    pub max_inline_bytes: u64,
    pub max_batch_items: u32,
}

impl From<BackendLimits> for FfiBackendLimits {
    fn from(limits: BackendLimits) -> Self {
        Self {
            max_buffer_size: limits.max_buffer_size,
            max_texture_extent_2d: limits.max_texture_extent_2d,
            max_texture_mip_levels: limits.max_texture_mip_levels,
            max_texture_array_layers: limits.max_texture_array_layers,
            max_resource_layout_bindings: limits.max_resource_layout_bindings,
            max_binding_array_count: limits.max_binding_array_count,
            max_color_attachments: limits.max_color_attachments,
            max_dynamic_offsets_per_binding: limits.max_dynamic_offsets_per_binding,
            max_command_lists_per_submission: limits.max_command_lists_per_submission,
            max_commands_per_list: limits.max_commands_per_list,
            max_draw_count: limits.max_draw_count,
            max_dispatch_groups_per_axis: limits.max_dispatch_groups_per_axis,
            max_label_bytes: FFI_MAX_LABEL_BYTES as u32,
            max_shader_bytes: FFI_MAX_SHADER_BYTES as u32,
            max_inline_bytes: FFI_MAX_INLINE_BYTES as u64,
            max_batch_items: FFI_MAX_BATCH_ITEMS as u32,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiCapabilityQueryRequest {
    pub header: FfiHeader,
    pub requested_feature_bits: u64,
    pub reserved0: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiCapabilityResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub supported_feature_bits: u64,
    pub negotiated_feature_bits: u64,
    pub limits: FfiBackendLimits,
    pub initial_presentation_supported: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiMetricsSnapshot {
    pub resource_creates: u64,
    pub resource_destroys: u64,
    pub submissions: u64,
    pub command_lists: u64,
    pub command_ops: u64,
    pub backend_submissions: u64,
    pub backend_waits: u64,
    pub retired_resources: u64,
    pub ffi_calls: u64,
    pub ffi_input_bytes: u64,
    pub ffi_output_bytes: u64,
}

impl From<&Metrics> for FfiMetricsSnapshot {
    fn from(metrics: &Metrics) -> Self {
        Self {
            resource_creates: metrics.resource_creates,
            resource_destroys: metrics.resource_destroys,
            submissions: metrics.submissions,
            command_lists: 0,
            command_ops: 0,
            backend_submissions: 0,
            backend_waits: 0,
            retired_resources: metrics.deferred_retires,
            ffi_calls: 0,
            ffi_input_bytes: 0,
            ffi_output_bytes: 0,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiStatusResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub unsupported_feature: u32,
    pub primary_handle: FfiHandle,
    pub submission_id: u64,
    pub required_bytes: u64,
    pub metrics: FfiMetricsSnapshot,
}

impl Default for FfiStatusResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            unsupported_feature: 0,
            primary_handle: FfiHandle::default(),
            submission_id: 0,
            required_bytes: 0,
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiCreateResultEntry {
    pub request_id: u64,
    pub handle: FfiHandle,
    pub status: i32,
    pub error_domain: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiBufferDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub size: u64,
    pub memory_domain: u32,
    pub usage_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiTextureDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub dimension: u32,
    pub format: u32,
    pub extent: FfiExtent3d,
    pub mip_levels: u32,
    pub array_layers: u32,
    pub usage_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiTextureViewDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub texture: FfiHandle,
    pub format: u32,
    pub base_mip: u32,
    pub mip_count: u32,
    pub base_layer: u32,
    pub layer_count: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiSamplerDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub min_filter: u32,
    pub mag_filter: u32,
    pub mip_filter: u32,
    pub address_u: u32,
    pub address_v: u32,
    pub address_w: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiShaderModuleDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub stage: u32,
    pub code_format: u32,
    pub code: FfiBytes,
    pub entry_point: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceBindingDescAbi {
    pub byte_size: u32,
    pub binding: u32,
    pub kind: u32,
    pub stage_bits: u32,
    pub array_count: u32,
    pub optional: u32,
    pub dynamic_offset_count: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceLayoutDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub bindings: FfiRange,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceBindingAbi {
    pub byte_size: u32,
    pub binding: u32,
    pub array_index: u32,
    pub resource: FfiHandle,
    pub kind: u32,
    pub access_bits: u32,
    pub dynamic_offsets: FfiRange,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceSetDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub layout: FfiHandle,
    pub bindings: FfiRange,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiPipelineLayoutDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub resource_layouts: FfiRange,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiGraphicsPipelineDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub layout: FfiHandle,
    pub vertex_shader: FfiHandle,
    pub fragment_shader: FfiHandle,
    pub topology: u32,
    pub cull_mode: u32,
    pub blend: u32,
    pub depth_compare: u32,
    pub color_formats: FfiRange,
    pub depth_format: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiComputePipelineDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub layout: FfiHandle,
    pub shader: FfiHandle,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiRenderTargetDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub color_views: FfiRange,
    pub depth_stencil_view: FfiHandle,
    pub extent: FfiExtent3d,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiRenderPassDescAbi {
    pub byte_size: u32,
    pub request_id: u64,
    pub label: FfiBytes,
    pub target: FfiHandle,
    pub color_formats: FfiRange,
    pub depth_format: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiDestroyDescAbi {
    pub byte_size: u32,
    pub handle: FfiHandle,
    pub expected_kind: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiBufferUpdateAbi {
    pub byte_size: u32,
    pub buffer: FfiHandle,
    pub offset: u64,
    pub data: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiTextureUpdateAbi {
    pub byte_size: u32,
    pub texture: FfiHandle,
    pub mip_level: u32,
    pub array_layer: u32,
    pub origin: FfiTextureOrigin3d,
    pub extent: FfiExtent3d,
    pub bytes_per_row: u32,
    pub rows_per_image: u32,
    pub data: FfiBytes,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceBatch {
    pub header: FfiHeader,
    pub buffers: FfiSlice<FfiBufferDescAbi>,
    pub textures: FfiSlice<FfiTextureDescAbi>,
    pub texture_views: FfiSlice<FfiTextureViewDescAbi>,
    pub samplers: FfiSlice<FfiSamplerDescAbi>,
    pub shaders: FfiSlice<FfiShaderModuleDescAbi>,
    pub resource_layouts: FfiSlice<FfiResourceLayoutDescAbi>,
    pub resource_layout_bindings: FfiSlice<FfiResourceBindingDescAbi>,
    pub resource_sets: FfiSlice<FfiResourceSetDescAbi>,
    pub resource_set_bindings: FfiSlice<FfiResourceBindingAbi>,
    pub dynamic_offsets: FfiSlice<u64>,
    pub pipeline_layouts: FfiSlice<FfiPipelineLayoutDescAbi>,
    pub pipeline_layout_resource_layouts: FfiSlice<FfiHandle>,
    pub graphics_pipelines: FfiSlice<FfiGraphicsPipelineDescAbi>,
    pub compute_pipelines: FfiSlice<FfiComputePipelineDescAbi>,
    pub render_targets: FfiSlice<FfiRenderTargetDescAbi>,
    pub render_target_color_views: FfiSlice<FfiHandle>,
    pub render_passes: FfiSlice<FfiRenderPassDescAbi>,
    pub render_pass_color_formats: FfiSlice<u32>,
    pub buffer_updates: FfiSlice<FfiBufferUpdateAbi>,
    pub texture_updates: FfiSlice<FfiTextureUpdateAbi>,
    pub destroys: FfiSlice<FfiDestroyDescAbi>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiClearColor {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

impl From<FfiClearColor> for ClearColor {
    fn from(color: FfiClearColor) -> Self {
        Self {
            r: color.r,
            g: color.g,
            b: color.b,
            a: color.a,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiPassAttachmentAbi {
    pub byte_size: u32,
    pub view: FfiHandle,
    pub load_op: u32,
    pub store_op: u32,
    pub has_clear_color: u32,
    pub clear_color: FfiClearColor,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiBufferImageCopyAbi {
    pub byte_size: u32,
    pub buffer: FfiHandle,
    pub buffer_offset: u64,
    pub bytes_per_row: u32,
    pub rows_per_image: u32,
    pub texture: FfiHandle,
    pub texture_mip: u32,
    pub texture_layer: u32,
    pub texture_origin: FfiTextureOrigin3d,
    pub extent: FfiExtent3d,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceBarrierAbi {
    pub byte_size: u32,
    pub resource: FfiHandle,
    pub has_subresources: u32,
    pub subresources: FfiTextureSubresourceRange,
    pub before: u32,
    pub after: u32,
    pub stage_bits: u32,
    pub access_bits: u32,
    pub src_queue: u32,
    pub dst_queue: u32,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FfiCommandOpKind {
    BeginPass = 1,
    BindGraphicsPipeline = 2,
    BindComputePipeline = 3,
    BindResourceSet = 4,
    SetVertexBuffer = 5,
    SetIndexBuffer = 6,
    Draw = 7,
    DrawIndexed = 8,
    DrawIndirect = 9,
    Dispatch = 10,
    DispatchIndirect = 11,
    CopyBuffer = 12,
    CopyBufferToTexture = 13,
    CopyTextureToBuffer = 14,
    HostWriteBuffer = 15,
    HostReadBuffer = 16,
    Present = 17,
    Barrier = 18,
    EndPass = 19,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiCommandOpAbi {
    pub byte_size: u32,
    pub op_kind: u32,
    pub primary: FfiHandle,
    pub secondary: FfiHandle,
    pub tertiary: FfiHandle,
    pub set_index: u32,
    pub slot: u32,
    pub offset: u64,
    pub size: u64,
    pub count0: u32,
    pub count1: u32,
    pub count2: u32,
    pub colors: FfiRange,
    pub depth_stencil: FfiRange,
    pub copy_region: FfiRange,
    pub barrier: FfiRange,
    pub inline_bytes: FfiBytes,
    pub subresources: FfiTextureSubresourceRange,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiCommandListAbi {
    pub byte_size: u32,
    pub label: FfiBytes,
    pub operations: FfiRange,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiSubmissionBatchAbi {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub command_lists: FfiSlice<FfiCommandListAbi>,
    pub operations: FfiSlice<FfiCommandOpAbi>,
    pub pass_attachments: FfiSlice<FfiPassAttachmentAbi>,
    pub copy_regions: FfiSlice<FfiBufferImageCopyAbi>,
    pub barriers: FfiSlice<FfiResourceBarrierAbi>,
    pub negotiated_feature_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiCompletionQueryRequest {
    pub header: FfiHeader,
    pub submission_id: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiCompletionResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub requested_submission_id: u64,
    pub completed_submission_id: u64,
    pub is_complete: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiRetirementBatch {
    pub header: FfiHeader,
    pub completed_submission_id: u64,
    pub handles: FfiSlice<FfiHandle>,
}
