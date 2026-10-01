//! Context creation, capability negotiation and metrics records.

use super::*;

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FfiBackendKind {
    Vulkan = 1,
    OpenGl = 2,
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
