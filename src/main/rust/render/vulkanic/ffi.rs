use std::collections::BTreeMap;
use std::cell::RefCell;
use std::mem::{align_of, size_of, MaybeUninit};
use std::ptr;
use std::slice;

use super::backends::{create_backend, BackendKind};
use super::commands::{
    AttachmentLoadOp, AttachmentStoreOp, BufferImageCopyRegion, ClearColor, CommandList,
    CommandListDesc, CommandOp, PassAttachment, ResourceBarrier, SubmissionBatch, TextureOrigin3d,
    TextureUsageState,
};
use super::error::{ErrorDomain, GalError, GalResult, StatusCode};
use super::handles::{Handle, HandleKind};
use super::metrics::Metrics;
use super::resources::{
    AccessFlags, BackendCapabilities, BackendFeature, BackendLimits, BlendMode, BufferDesc,
    BufferUsage, ColorFormat, CompareOp, ComputePipelineDesc, CullMode, Extent3d,
    GraphicsPipelineDesc, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology,
    QueueClass, RenderPassDesc, RenderTargetDesc, ResourceBinding, ResourceBindingDesc,
    ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc,
    SamplerFilter, ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension,
    TextureFormat, TextureSubresourceRange, TextureUsage, TextureViewDesc,
};
use super::sync::SubmissionId;
use super::gal::VulkanicGal;

pub const FFI_ABI_VERSION: u32 = 1;
pub const FFI_ABI_NAME: &str = "MattMC VulkanicGAL Java-Rust batch ABI";
pub const FFI_MAX_LABEL_BYTES: usize = 1024;
pub const FFI_MAX_SHADER_BYTES: usize = 16 * 1024 * 1024;
pub const FFI_MAX_INLINE_BYTES: usize = 64 * 1024 * 1024;
pub const FFI_MAX_BATCH_ITEMS: usize = 65_536;
pub const FFI_INITIAL_PRESENTATION_SUPPORTED: bool = false;

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FfiBackendKind {
    Vulkan = 1,
    OpenGl = 2,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiStructLayout {
    pub header: FfiHeader,
    pub struct_id: u32,
    pub byte_size: u32,
    pub alignment: u32,
    pub field_count: u32,
    pub field_offsets: [u32; 32],
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

pub fn validate_header<T>(header: FfiHeader) -> GalResult<()> {
    if header.version != FFI_ABI_VERSION {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("unsupported VulkanicGAL FFI version {}", header.version),
        ));
    }
    if header.byte_size as usize != size_of::<T>() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "FFI byte size mismatch: got {}, expected {}",
                header.byte_size,
                size_of::<T>()
            ),
        ));
    }
    Ok(())
}

pub unsafe fn read_bytes<'a>(bytes: FfiBytes, nullable: bool, label: &str) -> GalResult<&'a [u8]> {
    if bytes.len == 0 {
        if !nullable && bytes.ptr.is_null() {
            return Err(GalError::ffi(
                StatusCode::NullPointer,
                format!("{label} pointer is null"),
            ));
        }
        return Ok(&[]);
    }
    if bytes.ptr.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    let len = usize::try_from(bytes.len).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} length does not fit usize"),
        )
    })?;
    Ok(slice::from_raw_parts(bytes.ptr, len))
}

pub unsafe fn read_slice<'a, T>(
    slice_desc: FfiSlice<T>,
    nullable: bool,
    label: &str,
) -> GalResult<&'a [T]> {
    if slice_desc.count == 0 {
        if !nullable && slice_desc.ptr.is_null() {
            return Err(GalError::ffi(
                StatusCode::NullPointer,
                format!("{label} pointer is null"),
            ));
        }
        return Ok(&[]);
    }
    if slice_desc.ptr.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    if (slice_desc.ptr as usize) % align_of::<T>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            format!("{label} pointer is not aligned to {}", align_of::<T>()),
        ));
    }
    let count = usize::try_from(slice_desc.count).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} count does not fit usize"),
        )
    })?;
    count.checked_mul(size_of::<T>()).ok_or_else(|| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} byte length overflows usize"),
        )
    })?;
    Ok(slice::from_raw_parts(slice_desc.ptr, count))
}

pub unsafe fn validate_buffer_create_request(
    request: *const FfiBufferCreateRequest,
) -> GalResult<FfiBufferCreateRequest> {
    if request.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            "buffer request pointer is null",
        ));
    }
    if (request as usize) % align_of::<FfiBufferCreateRequest>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            "buffer request pointer is misaligned",
        ));
    }
    let request = *request;
    validate_header::<FfiBufferCreateRequest>(request.header)?;
    read_bytes(request.label, true, "buffer label")?;
    FfiMemoryDomain::validate(request.memory_domain)?;
    if request.size == 0 || request.usage_bits == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "buffer size and usage bits must be non-zero",
        ));
    }
    Ok(request)
}

pub unsafe fn validate_submission_request(
    request: *const FfiSubmissionRequest,
) -> GalResult<FfiSubmissionRequest> {
    if request.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            "submission request pointer is null",
        ));
    }
    if (request as usize) % align_of::<FfiSubmissionRequest>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            "submission request pointer is misaligned",
        ));
    }
    let request = *request;
    validate_header::<FfiSubmissionRequest>(request.header)?;
    read_bytes(request.label, true, "submission label")?;
    let lists = read_slice(request.command_lists, false, "submission command lists")?;
    if lists.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "submission requires at least one command list",
        ));
    }
    for list in lists {
        validate_header::<FfiCommandListRequest>(list.header)?;
        read_bytes(list.label, true, "command list label")?;
        read_bytes(list.encoded_ops, false, "encoded command operations")?;
        for use_decl in read_slice(list.resource_uses, true, "resource uses")? {
            if Handle::from(use_decl.resource).is_null() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "resource use contains null handle",
                ));
            }
            if use_decl.stage_bits == 0 || use_decl.access_bits == 0 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "resource use requires stage and access bits",
                ));
            }
        }
    }
    Ok(request)
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

#[derive(Clone, Debug, PartialEq)]
pub struct FfiOwnedCreate<T> {
    pub request_id: u64,
    pub desc: T,
}

#[derive(Clone, Debug, PartialEq)]
pub struct FfiOwnedBufferUpdate {
    pub buffer: Handle,
    pub offset: u64,
    pub data: Vec<u8>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct FfiOwnedTextureUpdate {
    pub texture: Handle,
    pub mip_level: u32,
    pub array_layer: u32,
    pub origin: TextureOrigin3d,
    pub extent: Extent3d,
    pub bytes_per_row: u32,
    pub rows_per_image: u32,
    pub data: Vec<u8>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct FfiOwnedResourceBatch {
    pub buffers: Vec<FfiOwnedCreate<BufferDesc>>,
    pub textures: Vec<FfiOwnedCreate<TextureDesc>>,
    pub texture_views: Vec<FfiOwnedCreate<TextureViewDesc>>,
    pub samplers: Vec<FfiOwnedCreate<SamplerDesc>>,
    pub shaders: Vec<FfiOwnedCreate<ShaderModuleDesc>>,
    pub resource_layouts: Vec<FfiOwnedCreate<ResourceLayoutDesc>>,
    pub resource_sets: Vec<FfiOwnedCreate<ResourceSetDesc>>,
    pub pipeline_layouts: Vec<FfiOwnedCreate<PipelineLayoutDesc>>,
    pub graphics_pipelines: Vec<FfiOwnedCreate<GraphicsPipelineDesc>>,
    pub compute_pipelines: Vec<FfiOwnedCreate<ComputePipelineDesc>>,
    pub render_targets: Vec<FfiOwnedCreate<RenderTargetDesc>>,
    pub render_passes: Vec<FfiOwnedCreate<RenderPassDesc>>,
    pub buffer_updates: Vec<FfiOwnedBufferUpdate>,
    pub texture_updates: Vec<FfiOwnedTextureUpdate>,
    pub destroys: Vec<(Handle, HandleKind)>,
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

pub fn status_result_from_error(error: &GalError) -> FfiStatusResult {
    FfiStatusResult {
        status: error.code as i32,
        error_domain: error.domain as u32,
        unsupported_feature: unsupported_feature_from_message(error.message.as_str()),
        ..FfiStatusResult::default()
    }
}

pub fn capability_feature_bits(capabilities: BackendCapabilities) -> u64 {
    let features = capabilities.features;
    let mut bits = 0;
    set_feature(&mut bits, FfiFeatureBits::GRAPHICS, features.graphics);
    set_feature(&mut bits, FfiFeatureBits::COMPUTE, features.compute);
    set_feature(
        &mut bits,
        FfiFeatureBits::DESCRIPTOR_ARRAYS,
        features.descriptor_arrays,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::OPTIONAL_BINDINGS,
        features.optional_bindings,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::DYNAMIC_BUFFER_OFFSETS,
        features.dynamic_buffer_offsets,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::UNIFORM_BUFFERS,
        features.uniform_buffers,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::STORAGE_BUFFERS,
        features.storage_buffers,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::STORAGE_TEXTURES,
        features.storage_textures,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::INDIRECT_DRAW,
        features.indirect_draw,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::INDIRECT_DISPATCH,
        features.indirect_dispatch,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::MULTIPLE_COLOR_ATTACHMENTS,
        features.multiple_color_attachments,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::DEPTH_ONLY_PASS,
        features.depth_only_pass,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::BLENDED_PASS,
        features.blended_pass,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES,
        features.texture_subresource_copies,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::TEXTURE_MIP_LEVELS,
        features.texture_mip_levels,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::TEXTURE_ARRAY_LAYERS,
        features.texture_array_layers,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::HOST_BUFFER_ACCESS,
        features.host_buffer_access,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::PRESENTATION,
        features.presentation,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::RENDERDOC_CAPTURE,
        features.renderdoc_capture,
    );
    set_feature(&mut bits, FfiFeatureBits::TRACY_ZONES, features.tracy_zones);
    bits
}

pub unsafe fn answer_capability_query(
    request: *const FfiCapabilityQueryRequest,
    capabilities: BackendCapabilities,
) -> GalResult<FfiCapabilityResult> {
    let request = read_struct(request, "capability query")?;
    validate_header::<FfiCapabilityQueryRequest>(request.header)?;
    reject_unknown_feature_bits(request.requested_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.requested_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported feature bits 0x{:x}",
            request.requested_feature_bits & !supported
        )));
    }
    Ok(FfiCapabilityResult {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiCapabilityResult>() as u32,
        },
        status: StatusCode::Ok as i32,
        error_domain: 0,
        supported_feature_bits: supported,
        negotiated_feature_bits: request.requested_feature_bits,
        limits: FfiBackendLimits::from(capabilities.limits),
        initial_presentation_supported: u32::from(FFI_INITIAL_PRESENTATION_SUPPORTED),
    })
}

pub unsafe fn decode_resource_batch(
    batch: *const FfiResourceBatch,
    capabilities: BackendCapabilities,
) -> GalResult<FfiOwnedResourceBatch> {
    let batch = read_struct(batch, "resource batch")?;
    validate_header::<FfiResourceBatch>(batch.header)?;
    reject_unknown_feature_bits(batch.negotiated_feature_bits)?;
    require_negotiated_features(batch.negotiated_feature_bits, capabilities)?;

    let buffers = read_limited_slice(batch.buffers, true, "buffer creates")?;
    let textures = read_limited_slice(batch.textures, true, "texture creates")?;
    let texture_views = read_limited_slice(batch.texture_views, true, "texture view creates")?;
    let samplers = read_limited_slice(batch.samplers, true, "sampler creates")?;
    let shaders = read_limited_slice(batch.shaders, true, "shader creates")?;
    let resource_layouts =
        read_limited_slice(batch.resource_layouts, true, "resource layout creates")?;
    let resource_layout_bindings = read_limited_slice(
        batch.resource_layout_bindings,
        true,
        "resource layout binding table",
    )?;
    let resource_sets = read_limited_slice(batch.resource_sets, true, "resource set creates")?;
    let resource_set_bindings = read_limited_slice(
        batch.resource_set_bindings,
        true,
        "resource set binding table",
    )?;
    let dynamic_offsets = read_limited_slice(batch.dynamic_offsets, true, "dynamic offset table")?;
    let pipeline_layouts =
        read_limited_slice(batch.pipeline_layouts, true, "pipeline layout creates")?;
    let pipeline_layout_resource_layouts = read_limited_slice(
        batch.pipeline_layout_resource_layouts,
        true,
        "pipeline layout resource layout table",
    )?;
    let graphics_pipelines =
        read_limited_slice(batch.graphics_pipelines, true, "graphics pipeline creates")?;
    let compute_pipelines =
        read_limited_slice(batch.compute_pipelines, true, "compute pipeline creates")?;
    let render_targets = read_limited_slice(batch.render_targets, true, "render target creates")?;
    let render_target_color_views = read_limited_slice(
        batch.render_target_color_views,
        true,
        "render target color view table",
    )?;
    let render_passes = read_limited_slice(batch.render_passes, true, "render pass creates")?;
    let render_pass_color_formats = read_limited_slice(
        batch.render_pass_color_formats,
        true,
        "render pass color format table",
    )?;
    let buffer_updates = read_limited_slice(batch.buffer_updates, true, "buffer updates")?;
    let texture_updates = read_limited_slice(batch.texture_updates, true, "texture updates")?;
    let destroys = read_limited_slice(batch.destroys, true, "destroys")?;
    let total_items = buffers.len()
        + textures.len()
        + texture_views.len()
        + samplers.len()
        + shaders.len()
        + resource_layouts.len()
        + resource_sets.len()
        + pipeline_layouts.len()
        + graphics_pipelines.len()
        + compute_pipelines.len()
        + render_targets.len()
        + render_passes.len()
        + buffer_updates.len()
        + texture_updates.len()
        + destroys.len();
    if total_items > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            "resource batch item count exceeds ABI maximum",
        ));
    }

    let mut owned = FfiOwnedResourceBatch {
        negotiated_feature_bits: batch.negotiated_feature_bits,
        ..FfiOwnedResourceBatch::default()
    };
    for item in buffers {
        validate_item_size::<FfiBufferDescAbi>(item.byte_size, "buffer create")?;
        let desc = BufferDesc {
            label: read_label(item.label, "buffer label")?,
            size: item.size,
            memory: memory_domain(item.memory_domain)?,
            usages: buffer_usage_bits(item.usage_bits)?,
        };
        check_buffer_capabilities(&desc, capabilities)?;
        owned.buffers.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc,
        });
    }
    for item in textures {
        validate_item_size::<FfiTextureDescAbi>(item.byte_size, "texture create")?;
        let desc = TextureDesc {
            label: read_label(item.label, "texture label")?,
            dimension: texture_dimension(item.dimension)?,
            format: texture_format(item.format)?,
            extent: item.extent.into(),
            mip_levels: item.mip_levels,
            array_layers: item.array_layers,
            usages: texture_usage_bits(item.usage_bits)?,
        };
        check_texture_capabilities(&desc, capabilities)?;
        owned.textures.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc,
        });
    }
    for item in texture_views {
        validate_item_size::<FfiTextureViewDescAbi>(item.byte_size, "texture view create")?;
        owned.texture_views.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: TextureViewDesc {
                label: read_label(item.label, "texture view label")?,
                texture: require_handle(item.texture, HandleKind::Texture, "texture view texture")?,
                format: texture_format(item.format)?,
                base_mip: item.base_mip,
                mip_count: item.mip_count,
                base_layer: item.base_layer,
                layer_count: item.layer_count,
            },
        });
    }
    for item in samplers {
        validate_item_size::<FfiSamplerDescAbi>(item.byte_size, "sampler create")?;
        owned.samplers.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: SamplerDesc {
                label: read_label(item.label, "sampler label")?,
                min_filter: sampler_filter(item.min_filter)?,
                mag_filter: sampler_filter(item.mag_filter)?,
                mip_filter: sampler_filter(item.mip_filter)?,
                address_u: sampler_address(item.address_u)?,
                address_v: sampler_address(item.address_v)?,
                address_w: sampler_address(item.address_w)?,
            },
        });
    }
    for item in shaders {
        validate_item_size::<FfiShaderModuleDescAbi>(item.byte_size, "shader create")?;
        let code = read_bounded_bytes(item.code, false, FFI_MAX_SHADER_BYTES, "shader code")?;
        owned.shaders.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: ShaderModuleDesc {
                label: read_label(item.label, "shader label")?,
                stage: shader_stage(item.stage)?,
                code_format: shader_code_format(item.code_format)?,
                code,
                entry_point: read_label(item.entry_point, "shader entry point")?,
            },
        });
    }
    for item in resource_layouts {
        validate_item_size::<FfiResourceLayoutDescAbi>(item.byte_size, "resource layout create")?;
        let binding_items =
            range_slice(resource_layout_bindings, item.bindings, "layout bindings")?;
        let mut bindings = Vec::with_capacity(binding_items.len());
        for binding in binding_items {
            validate_item_size::<FfiResourceBindingDescAbi>(
                binding.byte_size,
                "resource layout binding",
            )?;
            bindings.push(ResourceBindingDesc {
                binding: binding.binding,
                kind: resource_binding_kind(binding.kind)?,
                stages: stage_flags(binding.stage_bits)?,
                array_count: binding.array_count,
                optional: bool_flag(binding.optional, "resource layout optional binding")?,
                dynamic_offset_count: binding.dynamic_offset_count,
            });
        }
        owned.resource_layouts.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: ResourceLayoutDesc {
                label: read_label(item.label, "resource layout label")?,
                bindings,
            },
        });
    }
    for item in resource_sets {
        validate_item_size::<FfiResourceSetDescAbi>(item.byte_size, "resource set create")?;
        let binding_items = range_slice(
            resource_set_bindings,
            item.bindings,
            "resource set bindings",
        )?;
        let mut bindings = Vec::with_capacity(binding_items.len());
        for binding in binding_items {
            validate_item_size::<FfiResourceBindingAbi>(binding.byte_size, "resource set binding")?;
            let dynamic_offsets =
                range_slice(dynamic_offsets, binding.dynamic_offsets, "dynamic offsets")?.to_vec();
            bindings.push(ResourceBinding {
                binding: binding.binding,
                array_index: binding.array_index,
                resource: require_any_handle(binding.resource, "resource set binding resource")?,
                kind: resource_binding_kind(binding.kind)?,
                access: access_flags(binding.access_bits)?,
                dynamic_offsets,
            });
        }
        owned.resource_sets.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: ResourceSetDesc {
                label: read_label(item.label, "resource set label")?,
                layout: require_handle(
                    item.layout,
                    HandleKind::ResourceLayout,
                    "resource set layout",
                )?,
                bindings,
            },
        });
    }
    for item in pipeline_layouts {
        validate_item_size::<FfiPipelineLayoutDescAbi>(item.byte_size, "pipeline layout create")?;
        let layouts = range_slice(
            pipeline_layout_resource_layouts,
            item.resource_layouts,
            "pipeline layout resource layouts",
        )?
        .iter()
        .map(|handle| require_handle(*handle, HandleKind::ResourceLayout, "pipeline layout set"))
        .collect::<GalResult<Vec<_>>>()?;
        owned.pipeline_layouts.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: PipelineLayoutDesc {
                label: read_label(item.label, "pipeline layout label")?,
                resource_layouts: layouts,
            },
        });
    }
    for item in graphics_pipelines {
        validate_item_size::<FfiGraphicsPipelineDescAbi>(
            item.byte_size,
            "graphics pipeline create",
        )?;
        let color_formats = range_slice(
            render_pass_color_formats,
            item.color_formats,
            "graphics pipeline color formats",
        )?
        .iter()
        .map(|format| texture_format(*format))
        .collect::<GalResult<Vec<_>>>()?;
        let desc = GraphicsPipelineDesc {
            label: read_label(item.label, "graphics pipeline label")?,
            layout: require_handle(
                item.layout,
                HandleKind::PipelineLayout,
                "graphics pipeline layout",
            )?,
            vertex_shader: require_handle(
                item.vertex_shader,
                HandleKind::ShaderModule,
                "graphics pipeline vertex shader",
            )?,
            fragment_shader: require_handle(
                item.fragment_shader,
                HandleKind::ShaderModule,
                "graphics pipeline fragment shader",
            )?,
            topology: primitive_topology(item.topology)?,
            cull_mode: cull_mode(item.cull_mode)?,
            blend: blend_mode(item.blend)?,
            depth_compare: optional_compare_op(item.depth_compare)?,
            color_formats,
            depth_format: optional_texture_format(item.depth_format)?,
        };
        check_graphics_pipeline_capabilities(&desc, capabilities)?;
        owned.graphics_pipelines.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc,
        });
    }
    for item in compute_pipelines {
        validate_item_size::<FfiComputePipelineDescAbi>(item.byte_size, "compute pipeline create")?;
        require_feature(capabilities, BackendFeature::Compute, "compute pipeline")?;
        owned.compute_pipelines.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: ComputePipelineDesc {
                label: read_label(item.label, "compute pipeline label")?,
                layout: require_handle(
                    item.layout,
                    HandleKind::PipelineLayout,
                    "compute pipeline layout",
                )?,
                shader: require_handle(
                    item.shader,
                    HandleKind::ShaderModule,
                    "compute pipeline shader",
                )?,
            },
        });
    }
    for item in render_targets {
        validate_item_size::<FfiRenderTargetDescAbi>(item.byte_size, "render target create")?;
        let colors = range_slice(
            render_target_color_views,
            item.color_views,
            "render target color views",
        )?
        .iter()
        .map(|handle| require_handle(*handle, HandleKind::TextureView, "render target color view"))
        .collect::<GalResult<Vec<_>>>()?;
        check_attachment_count(colors.len(), item.depth_stencil_view.raw != 0, capabilities)?;
        owned.render_targets.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: RenderTargetDesc {
                label: read_label(item.label, "render target label")?,
                color_views: colors,
                depth_stencil_view: optional_handle(
                    item.depth_stencil_view,
                    HandleKind::TextureView,
                    "render target depth view",
                )?,
                extent: item.extent.into(),
            },
        });
    }
    for item in render_passes {
        validate_item_size::<FfiRenderPassDescAbi>(item.byte_size, "render pass create")?;
        let color_formats = range_slice(
            render_pass_color_formats,
            item.color_formats,
            "render pass color formats",
        )?
        .iter()
        .map(|format| texture_format(*format))
        .collect::<GalResult<Vec<ColorFormat>>>()?;
        check_attachment_count(color_formats.len(), item.depth_format != 0, capabilities)?;
        owned.render_passes.push(FfiOwnedCreate {
            request_id: item.request_id,
            desc: RenderPassDesc {
                label: read_label(item.label, "render pass label")?,
                target: require_handle(
                    item.target,
                    HandleKind::RenderTarget,
                    "render pass target",
                )?,
                color_formats,
                depth_format: optional_texture_format(item.depth_format)?,
            },
        });
    }
    for item in buffer_updates {
        validate_item_size::<FfiBufferUpdateAbi>(item.byte_size, "buffer update")?;
        owned.buffer_updates.push(FfiOwnedBufferUpdate {
            buffer: require_handle(item.buffer, HandleKind::Buffer, "buffer update target")?,
            offset: item.offset,
            data: read_bounded_bytes(item.data, false, FFI_MAX_INLINE_BYTES, "buffer update data")?,
        });
    }
    for item in texture_updates {
        validate_item_size::<FfiTextureUpdateAbi>(item.byte_size, "texture update")?;
        owned.texture_updates.push(FfiOwnedTextureUpdate {
            texture: require_handle(item.texture, HandleKind::Texture, "texture update target")?,
            mip_level: item.mip_level,
            array_layer: item.array_layer,
            origin: item.origin.into(),
            extent: item.extent.into(),
            bytes_per_row: item.bytes_per_row,
            rows_per_image: item.rows_per_image,
            data: read_bounded_bytes(
                item.data,
                false,
                FFI_MAX_INLINE_BYTES,
                "texture update data",
            )?,
        });
    }
    for item in destroys {
        validate_item_size::<FfiDestroyDescAbi>(item.byte_size, "destroy")?;
        let kind = handle_kind(item.expected_kind)?;
        owned
            .destroys
            .push((require_handle(item.handle, kind, "destroy handle")?, kind));
    }
    Ok(owned)
}

pub unsafe fn decode_submission_batch(
    batch: *const FfiSubmissionBatchAbi,
    capabilities: BackendCapabilities,
) -> GalResult<SubmissionBatch> {
    let batch = read_struct(batch, "submission batch")?;
    validate_header::<FfiSubmissionBatchAbi>(batch.header)?;
    reject_unknown_feature_bits(batch.negotiated_feature_bits)?;
    require_negotiated_features(batch.negotiated_feature_bits, capabilities)?;
    let lists = read_limited_slice(batch.command_lists, false, "command lists")?;
    let ops = read_limited_slice(batch.operations, false, "command operations")?;
    let attachments = read_limited_slice(batch.pass_attachments, true, "pass attachments")?;
    let copy_regions = read_limited_slice(batch.copy_regions, true, "copy regions")?;
    let barriers = read_limited_slice(batch.barriers, true, "barriers")?;
    if lists.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "submission requires at least one command list",
        ));
    }
    let mut command_lists = Vec::with_capacity(lists.len());
    let mut total_ops = 0usize;
    for list in lists {
        validate_item_size::<FfiCommandListAbi>(list.byte_size, "command list")?;
        let op_items = range_slice(ops, list.operations, "command list operations")?;
        if op_items.is_empty() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "command list must contain at least one operation",
            ));
        }
        total_ops = total_ops.checked_add(op_items.len()).ok_or_else(|| {
            GalError::ffi(
                StatusCode::LengthOverflow,
                "command operation count overflow",
            )
        })?;
        if op_items.len() > capabilities.limits.max_commands_per_list as usize {
            return Err(GalError::unsupported_feature(format!(
                "command list operation count {} exceeds backend '{}' limit {}",
                op_items.len(),
                capabilities.name,
                capabilities.limits.max_commands_per_list
            )));
        }
        let mut operations = Vec::with_capacity(op_items.len());
        for op in op_items {
            validate_item_size::<FfiCommandOpAbi>(op.byte_size, "command operation")?;
            operations.push(decode_command_op(
                op,
                attachments,
                copy_regions,
                barriers,
                capabilities,
            )?);
        }
        command_lists.push(CommandList::from(CommandListDesc {
            label: read_label(list.label, "command list label")?,
            operations,
        }));
    }
    if lists.len() > capabilities.limits.max_command_lists_per_submission as usize {
        return Err(GalError::unsupported_feature(format!(
            "submission command list count {} exceeds backend '{}' limit {}",
            lists.len(),
            capabilities.name,
            capabilities.limits.max_command_lists_per_submission
        )));
    }
    if total_ops > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            "submission operation count exceeds ABI maximum",
        ));
    }
    Ok(SubmissionBatch {
        label: read_label(batch.label, "submission label")?,
        command_lists,
    })
}

pub unsafe fn validate_completion_query(
    request: *const FfiCompletionQueryRequest,
) -> GalResult<FfiCompletionQueryRequest> {
    let request = read_struct(request, "completion query")?;
    validate_header::<FfiCompletionQueryRequest>(request.header)?;
    if request.submission_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "completion query requires a non-zero submission id",
        ));
    }
    Ok(request)
}

pub fn completion_result_for(
    requested: SubmissionId,
    completed: SubmissionId,
) -> FfiCompletionResult {
    FfiCompletionResult {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiCompletionResult>() as u32,
        },
        status: StatusCode::Ok as i32,
        error_domain: 0,
        requested_submission_id: requested.0,
        completed_submission_id: completed.0,
        is_complete: u32::from(completed >= requested),
    }
}

pub unsafe fn decode_retirement_batch(
    batch: *const FfiRetirementBatch,
) -> GalResult<(SubmissionId, Vec<Handle>)> {
    let batch = read_struct(batch, "retirement batch")?;
    validate_header::<FfiRetirementBatch>(batch.header)?;
    let handles = read_limited_slice(batch.handles, true, "retirement handles")?;
    let mut owned = Vec::with_capacity(handles.len());
    for handle in handles {
        owned.push(require_any_handle(*handle, "retirement handle")?);
    }
    Ok((SubmissionId(batch.completed_submission_id), owned))
}

pub fn serialize_resource_batch_canonical(batch: &FfiOwnedResourceBatch) -> Vec<u8> {
    let mut out = Vec::new();
    push_u32(&mut out, FFI_ABI_VERSION);
    push_u64(&mut out, batch.negotiated_feature_bits);
    push_u64(&mut out, batch.buffers.len() as u64);
    for item in &batch.buffers {
        push_create_prefix(&mut out, item.request_id, &item.desc.label);
        push_u64(&mut out, item.desc.size);
        push_u32(&mut out, item.desc.memory as u32);
        push_u64(&mut out, buffer_usage_bits_from_desc(&item.desc.usages));
    }
    push_u64(&mut out, batch.textures.len() as u64);
    for item in &batch.textures {
        push_create_prefix(&mut out, item.request_id, &item.desc.label);
        push_u32(&mut out, item.desc.dimension as u32);
        push_u32(&mut out, item.desc.format as u32);
        push_extent(&mut out, item.desc.extent);
        push_u32(&mut out, item.desc.mip_levels);
        push_u32(&mut out, item.desc.array_layers);
        push_u64(&mut out, texture_usage_bits_from_desc(&item.desc.usages));
    }
    push_u64(&mut out, batch.shaders.len() as u64);
    for item in &batch.shaders {
        push_create_prefix(&mut out, item.request_id, &item.desc.label);
        push_u32(&mut out, item.desc.stage as u32);
        push_u32(&mut out, item.desc.code_format as u32);
        push_bytes(&mut out, &item.desc.code);
        push_str(&mut out, &item.desc.entry_point);
    }
    push_u64(&mut out, batch.destroys.len() as u64);
    for (handle, kind) in &batch.destroys {
        push_u64(&mut out, handle.raw());
        push_u32(&mut out, *kind as u32);
    }
    out
}

pub fn serialize_submission_batch_canonical(batch: &SubmissionBatch) -> Vec<u8> {
    let mut out = Vec::new();
    push_u32(&mut out, FFI_ABI_VERSION);
    push_str(&mut out, &batch.label);
    push_u64(&mut out, batch.command_lists.len() as u64);
    for list in &batch.command_lists {
        push_str(&mut out, &list.label);
        push_u64(&mut out, list.operations.len() as u64);
        for op in &list.operations {
            serialize_command_op(&mut out, op);
        }
    }
    out
}

struct BridgeContext {
    gal: VulkanicGal,
    ffi_calls: u64,
    ffi_input_bytes: u64,
    ffi_output_bytes: u64,
    last_error: String,
}

#[derive(Default)]
struct BridgeRegistry {
    next_context_id: u64,
    contexts: BTreeMap<u64, BridgeContext>,
    last_error: String,
}

thread_local! {
    static BRIDGE_REGISTRY: RefCell<BridgeRegistry> = RefCell::new(BridgeRegistry {
        next_context_id: 1,
        contexts: BTreeMap::new(),
        last_error: String::new(),
    });
}

fn with_registry_mut<T>(f: impl FnOnce(&mut BridgeRegistry) -> T) -> T {
    BRIDGE_REGISTRY.with(|registry| f(&mut registry.borrow_mut()))
}

fn with_registry<T>(f: impl FnOnce(&BridgeRegistry) -> T) -> T {
    BRIDGE_REGISTRY.with(|registry| f(&registry.borrow()))
}

fn backend_kind(raw: u32) -> GalResult<BackendKind> {
    match raw {
        1 => Ok(BackendKind::Vulkan),
        2 => Ok(BackendKind::OpenGl),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown Rust VulkanicGAL backend kind {raw}"),
        )),
    }
}

fn context_metrics(context: &BridgeContext) -> FfiMetricsSnapshot {
    let mut metrics = FfiMetricsSnapshot::from(context.gal.metrics());
    metrics.ffi_calls = context.ffi_calls;
    metrics.ffi_input_bytes = context.ffi_input_bytes;
    metrics.ffi_output_bytes = context.ffi_output_bytes;
    metrics
}

fn status_ok(context: &BridgeContext) -> FfiStatusResult {
    FfiStatusResult {
        metrics: context_metrics(context),
        ..FfiStatusResult::default()
    }
}

fn status_error(context: Option<&BridgeContext>, error: &GalError) -> FfiStatusResult {
    let mut status = status_result_from_error(error);
    if let Some(context) = context {
        status.metrics = context_metrics(context);
    }
    status
}

fn set_last_error(context: &mut BridgeContext, error: &GalError) {
    context.last_error = error.to_string();
}

fn output_bytes_for_resource_results(capacity: u64) -> u64 {
    capacity.saturating_mul(size_of::<FfiCreateResultEntry>() as u64)
}

fn input_bytes_for_resource_batch(batch: &FfiResourceBatch) -> u64 {
    (size_of::<FfiResourceBatch>() as u64)
        .saturating_add(batch.buffers.count.saturating_mul(size_of::<FfiBufferDescAbi>() as u64))
        .saturating_add(batch.textures.count.saturating_mul(size_of::<FfiTextureDescAbi>() as u64))
        .saturating_add(batch.texture_views.count.saturating_mul(size_of::<FfiTextureViewDescAbi>() as u64))
        .saturating_add(batch.samplers.count.saturating_mul(size_of::<FfiSamplerDescAbi>() as u64))
        .saturating_add(batch.shaders.count.saturating_mul(size_of::<FfiShaderModuleDescAbi>() as u64))
        .saturating_add(batch.resource_layouts.count.saturating_mul(size_of::<FfiResourceLayoutDescAbi>() as u64))
        .saturating_add(batch.resource_layout_bindings.count.saturating_mul(size_of::<FfiResourceBindingDescAbi>() as u64))
        .saturating_add(batch.resource_sets.count.saturating_mul(size_of::<FfiResourceSetDescAbi>() as u64))
        .saturating_add(batch.resource_set_bindings.count.saturating_mul(size_of::<FfiResourceBindingAbi>() as u64))
        .saturating_add(batch.pipeline_layouts.count.saturating_mul(size_of::<FfiPipelineLayoutDescAbi>() as u64))
        .saturating_add(batch.graphics_pipelines.count.saturating_mul(size_of::<FfiGraphicsPipelineDescAbi>() as u64))
        .saturating_add(batch.compute_pipelines.count.saturating_mul(size_of::<FfiComputePipelineDescAbi>() as u64))
        .saturating_add(batch.render_targets.count.saturating_mul(size_of::<FfiRenderTargetDescAbi>() as u64))
        .saturating_add(batch.render_passes.count.saturating_mul(size_of::<FfiRenderPassDescAbi>() as u64))
        .saturating_add(batch.buffer_updates.count.saturating_mul(size_of::<FfiBufferUpdateAbi>() as u64))
        .saturating_add(batch.texture_updates.count.saturating_mul(size_of::<FfiTextureUpdateAbi>() as u64))
        .saturating_add(batch.destroys.count.saturating_mul(size_of::<FfiDestroyDescAbi>() as u64))
}

fn input_bytes_for_submission(batch: &FfiSubmissionBatchAbi) -> u64 {
    (size_of::<FfiSubmissionBatchAbi>() as u64)
        .saturating_add(batch.command_lists.count.saturating_mul(size_of::<FfiCommandListAbi>() as u64))
        .saturating_add(batch.operations.count.saturating_mul(size_of::<FfiCommandOpAbi>() as u64))
        .saturating_add(batch.pass_attachments.count.saturating_mul(size_of::<FfiPassAttachmentAbi>() as u64))
        .saturating_add(batch.copy_regions.count.saturating_mul(size_of::<FfiBufferImageCopyAbi>() as u64))
        .saturating_add(batch.barriers.count.saturating_mul(size_of::<FfiResourceBarrierAbi>() as u64))
}

unsafe fn write_out<T>(out: *mut T, value: T, label: &str) -> GalResult<()> {
    if out.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    if (out as usize) % align_of::<T>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            format!("{label} pointer is not aligned to {}", align_of::<T>()),
        ));
    }
    ptr::write(out, value);
    Ok(())
}

unsafe fn write_status_out(out: *mut FfiStatusResult, value: FfiStatusResult) {
    if !out.is_null() && (out as usize) % align_of::<FfiStatusResult>() == 0 {
        ptr::write(out, value);
    }
}

unsafe fn write_context_out(out: *mut FfiContextResult, value: FfiContextResult) {
    if !out.is_null() && (out as usize) % align_of::<FfiContextResult>() == 0 {
        ptr::write(out, value);
    }
}

fn create_result_capacity_required(batch: &FfiOwnedResourceBatch) -> usize {
    batch.buffers.len()
        + batch.textures.len()
        + batch.texture_views.len()
        + batch.samplers.len()
        + batch.shaders.len()
        + batch.resource_layouts.len()
        + batch.resource_sets.len()
        + batch.pipeline_layouts.len()
        + batch.graphics_pipelines.len()
        + batch.compute_pipelines.len()
        + batch.render_targets.len()
        + batch.render_passes.len()
}

fn execute_create<T>(
    results: &mut Vec<FfiCreateResultEntry>,
    item: &FfiOwnedCreate<T>,
    create: impl FnOnce() -> GalResult<Handle>,
) -> GalResult<()> {
    match create() {
        Ok(handle) => {
            results.push(FfiCreateResultEntry {
                request_id: item.request_id,
                handle: handle.into(),
                status: StatusCode::Ok as i32,
                error_domain: 0,
            });
            Ok(())
        }
        Err(error) => {
            results.push(FfiCreateResultEntry {
                request_id: item.request_id,
                handle: FfiHandle::default(),
                status: error.code as i32,
                error_domain: error.domain as u32,
            });
            Err(error)
        }
    }
}

fn execute_resource_batch(
    context: &mut BridgeContext,
    batch: FfiOwnedResourceBatch,
) -> GalResult<Vec<FfiCreateResultEntry>> {
    if !batch.texture_updates.is_empty() {
        return Err(GalError::unsupported_feature(
            "texture update resource batches are not part of the initial bridge path; use command uploads/copies",
        ));
    }
    let mut results = Vec::with_capacity(create_result_capacity_required(&batch));
    for item in &batch.buffers {
        execute_create(&mut results, item, || context.gal.create_buffer(item.desc.clone()))?;
    }
    for item in &batch.textures {
        execute_create(&mut results, item, || context.gal.create_texture(item.desc.clone()))?;
    }
    for item in &batch.texture_views {
        execute_create(&mut results, item, || {
            context.gal.create_texture_view(item.desc.clone())
        })?;
    }
    for item in &batch.samplers {
        execute_create(&mut results, item, || context.gal.create_sampler(item.desc.clone()))?;
    }
    for item in &batch.shaders {
        execute_create(&mut results, item, || {
            context.gal.create_shader_module(item.desc.clone())
        })?;
    }
    for item in &batch.resource_layouts {
        execute_create(&mut results, item, || {
            context.gal.create_resource_layout(item.desc.clone())
        })?;
    }
    for item in &batch.resource_sets {
        execute_create(&mut results, item, || {
            context.gal.create_resource_set(item.desc.clone())
        })?;
    }
    for item in &batch.pipeline_layouts {
        execute_create(&mut results, item, || {
            context.gal.create_pipeline_layout(item.desc.clone())
        })?;
    }
    for item in &batch.graphics_pipelines {
        execute_create(&mut results, item, || {
            context.gal.create_graphics_pipeline(item.desc.clone())
        })?;
    }
    for item in &batch.compute_pipelines {
        execute_create(&mut results, item, || {
            context.gal.create_compute_pipeline(item.desc.clone())
        })?;
    }
    for item in &batch.render_targets {
        execute_create(&mut results, item, || {
            context.gal.create_render_target(item.desc.clone())
        })?;
    }
    for item in &batch.render_passes {
        execute_create(&mut results, item, || {
            context.gal.create_render_pass(item.desc.clone())
        })?;
    }
    if !batch.buffer_updates.is_empty() {
        let operations = batch
            .buffer_updates
            .into_iter()
            .map(|update| CommandOp::HostWriteBuffer {
                buffer: update.buffer,
                offset: update.offset,
                data: update.data,
            })
            .collect();
        let list = context.gal.create_command_list(CommandListDesc {
            label: "ffi.resource-buffer-updates".to_string(),
            operations,
        })?;
        let _ = context.gal.submit(SubmissionBatch {
            label: "ffi.resource-buffer-update-submit".to_string(),
            command_lists: vec![list],
        })?;
        context.gal.retire_completed()?;
    }
    for (handle, _kind) in batch.destroys {
        context.gal.destroy(handle)?;
    }
    Ok(results)
}

macro_rules! offset_of {
    ($ty:ty, $field:tt) => {{
        let uninit = MaybeUninit::<$ty>::uninit();
        let base = uninit.as_ptr();
        unsafe { ptr::addr_of!((*base).$field) as usize - base as usize }
    }};
}

fn layout_for_struct(struct_id: u32) -> GalResult<FfiStructLayout> {
    macro_rules! layout {
        ($id:expr, $ty:ty, [$($field:tt),* $(,)?]) => {{
            let mut offsets = [0_u32; 32];
            let fields = [$(offset_of!($ty, $field) as u32),*];
            offsets[..fields.len()].copy_from_slice(&fields);
            FfiStructLayout {
                header: FfiHeader {
                    version: FFI_ABI_VERSION,
                    byte_size: size_of::<FfiStructLayout>() as u32,
                },
                struct_id: $id,
                byte_size: size_of::<$ty>() as u32,
                alignment: align_of::<$ty>() as u32,
                field_count: fields.len() as u32,
                field_offsets: offsets,
            }
        }};
    }
    Ok(match struct_id {
        1 => layout!(1, FfiHeader, [version, byte_size]),
        2 => layout!(2, FfiBytes, [ptr, len]),
        3 => layout!(3, FfiHandle, [raw]),
        4 => layout!(4, FfiContextCreateRequest, [header, backend_kind, tracy_enabled, label]),
        5 => layout!(5, FfiContextResult, [header, status, error_domain, context_id, supported_feature_bits, limits, metrics]),
        6 => layout!(6, FfiCapabilityQueryRequest, [header, requested_feature_bits, reserved0]),
        7 => layout!(7, FfiCapabilityResult, [header, status, error_domain, supported_feature_bits, negotiated_feature_bits, limits, initial_presentation_supported]),
        8 => layout!(8, FfiStatusResult, [header, status, error_domain, unsupported_feature, primary_handle, submission_id, required_bytes, metrics]),
        9 => layout!(9, FfiCreateResultEntry, [request_id, handle, status, error_domain]),
        10 => layout!(10, FfiBufferDescAbi, [byte_size, request_id, label, size, memory_domain, usage_bits]),
        11 => layout!(11, FfiTextureDescAbi, [byte_size, request_id, label, dimension, format, extent, mip_levels, array_layers, usage_bits]),
        12 => layout!(12, FfiTextureViewDescAbi, [byte_size, request_id, label, texture, format, base_mip, mip_count, base_layer, layer_count]),
        13 => layout!(13, FfiSamplerDescAbi, [byte_size, request_id, label, min_filter, mag_filter, mip_filter, address_u, address_v, address_w]),
        14 => layout!(14, FfiShaderModuleDescAbi, [byte_size, request_id, label, stage, code_format, code, entry_point]),
        15 => layout!(15, FfiResourceBindingDescAbi, [byte_size, binding, kind, stage_bits, array_count, optional, dynamic_offset_count]),
        16 => layout!(16, FfiResourceLayoutDescAbi, [byte_size, request_id, label, bindings]),
        17 => layout!(17, FfiResourceBindingAbi, [byte_size, binding, array_index, resource, kind, access_bits, dynamic_offsets]),
        18 => layout!(18, FfiResourceSetDescAbi, [byte_size, request_id, label, layout, bindings]),
        19 => layout!(19, FfiPipelineLayoutDescAbi, [byte_size, request_id, label, resource_layouts]),
        20 => layout!(20, FfiGraphicsPipelineDescAbi, [byte_size, request_id, label, layout, vertex_shader, fragment_shader, topology, cull_mode, blend, depth_compare, color_formats, depth_format]),
        21 => layout!(21, FfiRenderTargetDescAbi, [byte_size, request_id, label, color_views, depth_stencil_view, extent]),
        22 => layout!(22, FfiRenderPassDescAbi, [byte_size, request_id, label, target, color_formats, depth_format]),
        23 => layout!(23, FfiResourceBatch, [header, buffers, textures, texture_views, samplers, shaders, resource_layouts, resource_layout_bindings, resource_sets, resource_set_bindings, dynamic_offsets, pipeline_layouts, pipeline_layout_resource_layouts, graphics_pipelines, compute_pipelines, render_targets, render_target_color_views, render_passes, render_pass_color_formats, buffer_updates, texture_updates, destroys, negotiated_feature_bits]),
        24 => layout!(24, FfiPassAttachmentAbi, [byte_size, view, load_op, store_op, has_clear_color, clear_color]),
        25 => layout!(25, FfiBufferImageCopyAbi, [byte_size, buffer, buffer_offset, bytes_per_row, rows_per_image, texture, texture_mip, texture_layer, texture_origin, extent]),
        26 => layout!(26, FfiResourceBarrierAbi, [byte_size, resource, has_subresources, subresources, before, after, stage_bits, access_bits, src_queue, dst_queue]),
        27 => layout!(27, FfiCommandOpAbi, [byte_size, op_kind, primary, secondary, tertiary, set_index, slot, offset, size, count0, count1, count2, colors, depth_stencil, copy_region, barrier, inline_bytes, subresources]),
        28 => layout!(28, FfiCommandListAbi, [byte_size, label, operations]),
        29 => layout!(29, FfiSubmissionBatchAbi, [header, label, command_lists, operations, pass_attachments, copy_regions, barriers, negotiated_feature_bits]),
        30 => layout!(30, FfiCompletionQueryRequest, [header, submission_id]),
        31 => layout!(31, FfiCompletionResult, [header, status, error_domain, requested_submission_id, completed_submission_id, is_complete]),
        32 => layout!(32, FfiRetirementBatch, [header, completed_submission_id, handles]),
        33 => layout!(33, FfiReadbackRequest, [header, submission_id, buffer, offset, size]),
        34 => layout!(34, FfiReadbackResult, [header, status, error_domain, submission_id, required_bytes, written_bytes, metrics]),
        _ => {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown ABI struct id {struct_id}"),
            ))
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_abi_struct_layout(
    struct_id: u32,
    out: *mut FfiStructLayout,
) -> i32 {
    match layout_for_struct(struct_id).and_then(|layout| write_out(out, layout, "layout result")) {
        Ok(()) => StatusCode::Ok as i32,
        Err(error) => error.code as i32,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_context_create(
    request: *const FfiContextCreateRequest,
    out: *mut FfiContextResult,
) -> i32 {
    let result = (|| -> GalResult<FfiContextResult> {
        let request = read_struct(request, "context create request")?;
        validate_header::<FfiContextCreateRequest>(request.header)?;
        let kind = backend_kind(request.backend_kind)?;
        let label = read_label(request.label, "context label")?;
        let backend = create_backend(kind, &label)?;
        let gal = VulkanicGal::new_with_backend(backend, bool_flag(request.tracy_enabled, "tracy enabled")?);
        let capabilities = gal.capabilities();
        let context_id = with_registry_mut(|registry| -> GalResult<u64> {
            let context_id = registry.next_context_id;
            registry.next_context_id = registry.next_context_id.checked_add(1).ok_or_else(|| {
                GalError::ffi(StatusCode::GenerationExhausted, "context id space exhausted")
            })?;
            registry.contexts.insert(
                context_id,
                BridgeContext {
                    gal,
                    ffi_calls: 1,
                    ffi_input_bytes: size_of::<FfiContextCreateRequest>() as u64,
                    ffi_output_bytes: size_of::<FfiContextResult>() as u64,
                    last_error: String::new(),
                },
            );
            Ok(context_id)
        })?;
        Ok(FfiContextResult {
            context_id,
            supported_feature_bits: capability_feature_bits(capabilities),
            limits: capabilities.limits.into(),
            metrics: FfiMetricsSnapshot {
                ffi_calls: 1,
                ffi_input_bytes: size_of::<FfiContextCreateRequest>() as u64,
                ffi_output_bytes: size_of::<FfiContextResult>() as u64,
                ..FfiMetricsSnapshot::default()
            },
            ..FfiContextResult::default()
        })
    })();
    match result {
        Ok(value) => {
            write_context_out(out, value);
            StatusCode::Ok as i32
        }
        Err(error) => {
            with_registry_mut(|registry| {
                registry.last_error = error.to_string();
            });
            write_context_out(
                out,
                FfiContextResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    ..FfiContextResult::default()
                },
            );
            error.code as i32
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_context_destroy(
    context_id: u64,
    out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.remove(&context_id) else {
            let error = GalError::ffi(StatusCode::StaleHandle, format!("unknown context id {context_id}"));
            write_status_out(out, status_result_from_error(&error));
            return error.code as i32;
        };
        let mut status = status_ok(&context);
        status.metrics.ffi_calls = status.metrics.ffi_calls.saturating_add(1);
        status.metrics.ffi_output_bytes = status.metrics.ffi_output_bytes.saturating_add(size_of::<FfiStatusResult>() as u64);
        write_status_out(out, status);
        StatusCode::Ok as i32
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_capabilities(
    context_id: u64,
    request: *const FfiCapabilityQueryRequest,
    out: *mut FfiCapabilityResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
    context.ffi_calls += 1;
    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(size_of::<FfiCapabilityQueryRequest>() as u64);
    context.ffi_output_bytes = context.ffi_output_bytes.saturating_add(size_of::<FfiCapabilityResult>() as u64);
    match answer_capability_query(request, context.gal.capabilities()) {
        Ok(mut result) => {
            result.status = StatusCode::Ok as i32;
            let _ = write_out(out, result, "capability result");
            StatusCode::Ok as i32
        }
        Err(error) => {
            set_last_error(context, &error);
            let _ = write_out(
                out,
                FfiCapabilityResult {
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    supported_feature_bits: capability_feature_bits(context.gal.capabilities()),
                    limits: context.gal.capabilities().limits.into(),
                    ..FfiCapabilityResult {
                        header: FfiHeader {
                            version: FFI_ABI_VERSION,
                            byte_size: size_of::<FfiCapabilityResult>() as u32,
                        },
                        status: error.code as i32,
                        error_domain: error.domain as u32,
                        supported_feature_bits: 0,
                        negotiated_feature_bits: 0,
                        limits: FfiBackendLimits::default(),
                        initial_presentation_supported: 0,
                    }
                },
                "capability result",
            );
            error.code as i32
        }
    }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_resource_batch(
    context_id: u64,
    batch: *const FfiResourceBatch,
    results_out: *mut FfiCreateResultEntry,
    results_capacity: u64,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(StatusCode::StaleHandle, format!("unknown context id {context_id}"));
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
    let input_bytes = if batch.is_null() { 0 } else { input_bytes_for_resource_batch(&*batch) };
    context.ffi_calls += 1;
    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
    context.ffi_output_bytes = context
        .ffi_output_bytes
        .saturating_add(size_of::<FfiStatusResult>() as u64)
        .saturating_add(output_bytes_for_resource_results(results_capacity));
    let result = match decode_resource_batch(batch, context.gal.capabilities()).and_then(|owned| {
        let required = create_result_capacity_required(&owned);
        if required > 0 && results_out.is_null() {
            return Err(GalError::ffi(StatusCode::NullPointer, "create results pointer is null"));
        }
        if usize::try_from(results_capacity).unwrap_or(usize::MAX) < required {
            return Err(GalError::ffi(StatusCode::LengthOverflow, format!("create result capacity {results_capacity} is less than required {required}")));
        }
        execute_resource_batch(context, owned)
    }) {
        Ok(results) => {
            for (index, result) in results.iter().copied().enumerate() {
                ptr::write(results_out.add(index), result);
            }
            write_status_out(status_out, status_ok(context));
            StatusCode::Ok as i32
        }
        Err(error) => {
            set_last_error(context, &error);
            write_status_out(status_out, status_error(Some(context), &error));
            error.code as i32
        }
    };
        result
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_submit_batch(
    context_id: u64,
    batch: *const FfiSubmissionBatchAbi,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(StatusCode::StaleHandle, format!("unknown context id {context_id}"));
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
    let input_bytes = if batch.is_null() { 0 } else { input_bytes_for_submission(&*batch) };
    context.ffi_calls += 1;
    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
    context.ffi_output_bytes = context.ffi_output_bytes.saturating_add(size_of::<FfiStatusResult>() as u64);
    let result = match decode_submission_batch(batch, context.gal.capabilities()).and_then(|batch| context.gal.submit(batch)) {
        Ok(token) => {
            let mut status = status_ok(context);
            status.submission_id = token.submission.0;
            write_status_out(status_out, status);
            StatusCode::Ok as i32
        }
        Err(error) => {
            set_last_error(context, &error);
            write_status_out(status_out, status_error(Some(context), &error));
            error.code as i32
        }
    };
        result
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_completion_query(
    context_id: u64,
    request: *const FfiCompletionQueryRequest,
    out: *mut FfiCompletionResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
    context.ffi_calls += 1;
    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(size_of::<FfiCompletionQueryRequest>() as u64);
    context.ffi_output_bytes = context.ffi_output_bytes.saturating_add(size_of::<FfiCompletionResult>() as u64);
    let result = match validate_completion_query(request) {
        Ok(request) => {
            let completed = context.gal.retire_completed().map(|_| context.gal.completed_host_reads()).map(|_| {
                let reads = context.gal.completed_host_reads();
                reads.iter().map(|read| read.submission).max().unwrap_or(SubmissionId(0))
            });
            let polled = completed.unwrap_or(SubmissionId(0));
            let result = completion_result_for(SubmissionId(request.submission_id), std::cmp::max(polled, SubmissionId(request.submission_id)));
            let _ = write_out(out, result, "completion result");
            StatusCode::Ok as i32
        }
        Err(error) => {
            set_last_error(context, &error);
            let _ = write_out(
                out,
                FfiCompletionResult {
                    header: FfiHeader {
                        version: FFI_ABI_VERSION,
                        byte_size: size_of::<FfiCompletionResult>() as u32,
                    },
                    status: error.code as i32,
                    error_domain: error.domain as u32,
                    requested_submission_id: 0,
                    completed_submission_id: 0,
                    is_complete: 0,
                },
                "completion result",
            );
            error.code as i32
        }
    };
        result
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_retire(
    context_id: u64,
    batch: *const FfiRetirementBatch,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(StatusCode::StaleHandle, format!("unknown context id {context_id}"));
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
    context.ffi_calls += 1;
    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(size_of::<FfiRetirementBatch>() as u64);
    context.ffi_output_bytes = context.ffi_output_bytes.saturating_add(size_of::<FfiStatusResult>() as u64);
    let result = match decode_retirement_batch(batch).and_then(|(id, _handles)| context.gal.retire_through(id)) {
        Ok(retired) => {
            let mut status = status_ok(context);
            status.metrics.retired_resources = retired.len() as u64;
            write_status_out(status_out, status);
            StatusCode::Ok as i32
        }
        Err(error) => {
            set_last_error(context, &error);
            write_status_out(status_out, status_error(Some(context), &error));
            error.code as i32
        }
    };
        result
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_readback(
    context_id: u64,
    request: *const FfiReadbackRequest,
    out_bytes: *mut u8,
    out_capacity: u64,
    out: *mut FfiReadbackResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            return StatusCode::StaleHandle as i32;
        };
    context.ffi_calls += 1;
    context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(size_of::<FfiReadbackRequest>() as u64);
    context.ffi_output_bytes = context
        .ffi_output_bytes
        .saturating_add(size_of::<FfiReadbackResult>() as u64)
        .saturating_add(out_capacity);
    let result = (|| -> GalResult<FfiReadbackResult> {
        let request = read_struct(request, "readback request")?;
        validate_header::<FfiReadbackRequest>(request.header)?;
        let buffer = require_handle(request.buffer, HandleKind::Buffer, "readback buffer")?;
        context.gal.retire_through(SubmissionId(request.submission_id))?;
        let reads = context.gal.completed_host_reads();
        let Some(read) = reads.iter().rev().find(|read| {
            read.submission == SubmissionId(request.submission_id)
                && read.buffer == buffer
                && read.offset == request.offset
        }) else {
            return Err(GalError::submission(StatusCode::InvalidArgument, "requested readback was not produced by this submission"));
        };
        let requested_size = usize::try_from(request.size).map_err(|_| {
            GalError::ffi(StatusCode::LengthOverflow, "readback size does not fit usize")
        })?;
        let bytes = &read.bytes[..read.bytes.len().min(requested_size)];
        if out_capacity < bytes.len() as u64 {
            return Err(GalError::ffi(StatusCode::LengthOverflow, format!("readback output capacity {out_capacity} is less than required {}", bytes.len())));
        }
        if !bytes.is_empty() {
            if out_bytes.is_null() {
                return Err(GalError::ffi(StatusCode::NullPointer, "readback output pointer is null"));
            }
            ptr::copy_nonoverlapping(bytes.as_ptr(), out_bytes, bytes.len());
        }
        let mut result = FfiReadbackResult {
            submission_id: request.submission_id,
            required_bytes: bytes.len() as u64,
            written_bytes: bytes.len() as u64,
            metrics: context_metrics(context),
            ..FfiReadbackResult::default()
        };
        result.metrics.ffi_output_bytes = result.metrics.ffi_output_bytes.saturating_add(bytes.len() as u64);
        Ok(result)
    })();
    let status = match result {
        Ok(result) => {
            let _ = write_out(out, result, "readback result");
            StatusCode::Ok as i32
        }
        Err(error) => {
            set_last_error(context, &error);
            let mut result = FfiReadbackResult {
                status: error.code as i32,
                error_domain: error.domain as u32,
                metrics: context_metrics(context),
                ..FfiReadbackResult::default()
            };
            if let Ok(request) = read_struct(request, "readback request") {
                result.submission_id = request.submission_id;
                result.required_bytes = request.size;
            }
            let _ = write_out(out, result, "readback result");
            error.code as i32
        }
    };
        status
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_last_error(
    context_id: u64,
    out_bytes: *mut u8,
    out_capacity: u64,
) -> u64 {
    with_registry(|registry| {
        let Some(context) = registry.contexts.get(&context_id) else {
            let bytes = registry.last_error.as_bytes();
            let copy_len = bytes.len().min(usize::try_from(out_capacity).unwrap_or(0));
            if copy_len > 0 && !out_bytes.is_null() {
                ptr::copy_nonoverlapping(bytes.as_ptr(), out_bytes, copy_len);
            }
            return bytes.len() as u64;
        };
        let bytes = context.last_error.as_bytes();
        let copy_len = bytes.len().min(usize::try_from(out_capacity).unwrap_or(0));
        if copy_len > 0 && !out_bytes.is_null() {
            ptr::copy_nonoverlapping(bytes.as_ptr(), out_bytes, copy_len);
        }
        bytes.len() as u64
    })
}

fn set_feature(bits: &mut u64, feature: u64, enabled: bool) {
    if enabled {
        *bits |= feature;
    }
}

fn feature_from_bit(bit: u64) -> Option<BackendFeature> {
    match bit {
        FfiFeatureBits::GRAPHICS => Some(BackendFeature::Graphics),
        FfiFeatureBits::COMPUTE => Some(BackendFeature::Compute),
        FfiFeatureBits::DESCRIPTOR_ARRAYS => Some(BackendFeature::DescriptorArrays),
        FfiFeatureBits::OPTIONAL_BINDINGS => Some(BackendFeature::OptionalBindings),
        FfiFeatureBits::DYNAMIC_BUFFER_OFFSETS => Some(BackendFeature::DynamicBufferOffsets),
        FfiFeatureBits::UNIFORM_BUFFERS => Some(BackendFeature::UniformBuffers),
        FfiFeatureBits::STORAGE_BUFFERS => Some(BackendFeature::StorageBuffers),
        FfiFeatureBits::STORAGE_TEXTURES => Some(BackendFeature::StorageTextures),
        FfiFeatureBits::INDIRECT_DRAW => Some(BackendFeature::IndirectDraw),
        FfiFeatureBits::INDIRECT_DISPATCH => Some(BackendFeature::IndirectDispatch),
        FfiFeatureBits::MULTIPLE_COLOR_ATTACHMENTS => {
            Some(BackendFeature::MultipleColorAttachments)
        }
        FfiFeatureBits::DEPTH_ONLY_PASS => Some(BackendFeature::DepthOnlyPass),
        FfiFeatureBits::BLENDED_PASS => Some(BackendFeature::BlendedPass),
        FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES => {
            Some(BackendFeature::TextureSubresourceCopies)
        }
        FfiFeatureBits::TEXTURE_MIP_LEVELS => Some(BackendFeature::TextureMipLevels),
        FfiFeatureBits::TEXTURE_ARRAY_LAYERS => Some(BackendFeature::TextureArrayLayers),
        FfiFeatureBits::HOST_BUFFER_ACCESS => Some(BackendFeature::HostBufferAccess),
        FfiFeatureBits::PRESENTATION => Some(BackendFeature::Presentation),
        FfiFeatureBits::RENDERDOC_CAPTURE => Some(BackendFeature::RenderDocCapture),
        FfiFeatureBits::TRACY_ZONES => Some(BackendFeature::TracyZones),
        _ => None,
    }
}

fn reject_unknown_feature_bits(bits: u64) -> GalResult<()> {
    if bits & !FfiFeatureBits::ALL_KNOWN != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown negotiated feature bits 0x{:x}",
                bits & !FfiFeatureBits::ALL_KNOWN
            ),
        ));
    }
    Ok(())
}

fn require_negotiated_features(bits: u64, capabilities: BackendCapabilities) -> GalResult<()> {
    for index in 0..64 {
        let bit = 1_u64 << index;
        if bits & bit == 0 {
            continue;
        }
        let Some(feature) = feature_from_bit(bit) else {
            continue;
        };
        if !capabilities.supports(feature) {
            return Err(GalError::unsupported_feature(format!(
                "negotiated feature {:?} is unsupported by backend '{}'",
                feature, capabilities.name
            )));
        }
    }
    if bits & FfiFeatureBits::PRESENTATION != 0 && !FFI_INITIAL_PRESENTATION_SUPPORTED {
        return Err(GalError::unsupported_feature(
            "presentation/windowing is excluded from ABI version 1",
        ));
    }
    Ok(())
}

unsafe fn read_struct<T: Copy>(ptr: *const T, label: &str) -> GalResult<T> {
    if ptr.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    if (ptr as usize) % align_of::<T>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            format!("{label} pointer is not aligned to {}", align_of::<T>()),
        ));
    }
    Ok(*ptr)
}

unsafe fn read_limited_slice<'a, T>(
    slice_desc: FfiSlice<T>,
    nullable: bool,
    label: &str,
) -> GalResult<&'a [T]> {
    let items = read_slice(slice_desc, nullable, label)?;
    if items.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} count exceeds ABI maximum"),
        ));
    }
    Ok(items)
}

fn validate_item_size<T>(byte_size: u32, label: &str) -> GalResult<()> {
    if byte_size as usize != size_of::<T>() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "{label} byte size mismatch: got {byte_size}, expected {}",
                size_of::<T>()
            ),
        ));
    }
    Ok(())
}

unsafe fn read_bounded_bytes(
    bytes: FfiBytes,
    nullable: bool,
    max_bytes: usize,
    label: &str,
) -> GalResult<Vec<u8>> {
    let bytes = read_bytes(bytes, nullable, label)?;
    if bytes.len() > max_bytes {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} length exceeds ABI maximum"),
        ));
    }
    Ok(bytes.to_vec())
}

unsafe fn read_label(label: FfiBytes, label_name: &str) -> GalResult<String> {
    let bytes = read_bounded_bytes(label, true, FFI_MAX_LABEL_BYTES, label_name)?;
    String::from_utf8(bytes).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label_name} must be UTF-8"),
        )
    })
}

fn range_slice<'a, T>(items: &'a [T], range: FfiRange, label: &str) -> GalResult<&'a [T]> {
    let start = usize::try_from(range.offset).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} offset does not fit usize"),
        )
    })?;
    let count = usize::try_from(range.count).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} count does not fit usize"),
        )
    })?;
    let end = start.checked_add(count).ok_or_else(|| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} range overflows"),
        )
    })?;
    if end > items.len() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} range is outside its table"),
        ));
    }
    Ok(&items[start..end])
}

fn bool_flag(raw: u32, label: &str) -> GalResult<bool> {
    match raw {
        0 => Ok(false),
        1 => Ok(true),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("{label} must be 0 or 1"),
        )),
    }
}

fn require_any_handle(handle: FfiHandle, label: &str) -> GalResult<Handle> {
    let handle = Handle::from(handle);
    if handle.is_null() || handle.kind().is_none() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} is null or has unknown kind"),
        ));
    }
    Ok(handle)
}

fn require_handle(handle: FfiHandle, kind: HandleKind, label: &str) -> GalResult<Handle> {
    let handle = Handle::from(handle);
    if handle.is_null() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} handle is null"),
        ));
    }
    handle.require_kind(kind)?;
    Ok(handle)
}

fn optional_handle(handle: FfiHandle, kind: HandleKind, label: &str) -> GalResult<Option<Handle>> {
    if handle.raw == 0 {
        return Ok(None);
    }
    Ok(Some(require_handle(handle, kind, label)?))
}

fn handle_kind(raw: u32) -> GalResult<HandleKind> {
    HandleKind::from_raw(u8::try_from(raw).unwrap_or(0)).ok_or_else(|| {
        GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown handle kind {raw}"),
        )
    })
}

fn memory_domain(raw: u32) -> GalResult<MemoryDomain> {
    match raw {
        1 => Ok(MemoryDomain::DeviceLocal),
        2 => Ok(MemoryDomain::Upload),
        3 => Ok(MemoryDomain::Readback),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown memory domain {raw}"),
        )),
    }
}

fn texture_dimension(raw: u32) -> GalResult<TextureDimension> {
    match raw {
        1 => Ok(TextureDimension::D1),
        2 => Ok(TextureDimension::D2),
        3 => Ok(TextureDimension::D3),
        4 => Ok(TextureDimension::Cube),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown texture dimension {raw}"),
        )),
    }
}

fn texture_format(raw: u32) -> GalResult<TextureFormat> {
    match raw {
        1 => Ok(TextureFormat::Rgba8Unorm),
        2 => Ok(TextureFormat::Bgra8Unorm),
        3 => Ok(TextureFormat::Rgba16Float),
        4 => Ok(TextureFormat::Depth24Stencil8),
        5 => Ok(TextureFormat::Depth32Float),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown texture format {raw}"),
        )),
    }
}

fn optional_texture_format(raw: u32) -> GalResult<Option<TextureFormat>> {
    if raw == 0 {
        Ok(None)
    } else {
        texture_format(raw).map(Some)
    }
}

fn sampler_filter(raw: u32) -> GalResult<SamplerFilter> {
    match raw {
        1 => Ok(SamplerFilter::Nearest),
        2 => Ok(SamplerFilter::Linear),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown sampler filter {raw}"),
        )),
    }
}

fn sampler_address(raw: u32) -> GalResult<SamplerAddressMode> {
    match raw {
        1 => Ok(SamplerAddressMode::ClampToEdge),
        2 => Ok(SamplerAddressMode::Repeat),
        3 => Ok(SamplerAddressMode::MirroredRepeat),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown sampler address mode {raw}"),
        )),
    }
}

fn shader_stage(raw: u32) -> GalResult<ShaderStage> {
    match raw {
        1 => Ok(ShaderStage::Vertex),
        2 => Ok(ShaderStage::Fragment),
        3 => Ok(ShaderStage::Compute),
        4 => Ok(ShaderStage::Geometry),
        5 => Ok(ShaderStage::TessControl),
        6 => Ok(ShaderStage::TessEvaluation),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown shader stage {raw}"),
        )),
    }
}

fn shader_code_format(raw: u32) -> GalResult<ShaderCodeFormat> {
    match raw {
        1 => Ok(ShaderCodeFormat::Spirv),
        2 => Ok(ShaderCodeFormat::BackendPortableIr),
        3 => Ok(ShaderCodeFormat::Glsl),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown shader code format {raw}"),
        )),
    }
}

fn resource_binding_kind(raw: u32) -> GalResult<ResourceBindingKind> {
    match raw {
        1 => Ok(ResourceBindingKind::UniformBuffer),
        2 => Ok(ResourceBindingKind::StorageBuffer),
        3 => Ok(ResourceBindingKind::SampledTexture),
        4 => Ok(ResourceBindingKind::StorageTexture),
        5 => Ok(ResourceBindingKind::Sampler),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown resource binding kind {raw}"),
        )),
    }
}

fn primitive_topology(raw: u32) -> GalResult<PrimitiveTopology> {
    match raw {
        1 => Ok(PrimitiveTopology::Points),
        2 => Ok(PrimitiveTopology::Lines),
        3 => Ok(PrimitiveTopology::Triangles),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown primitive topology {raw}"),
        )),
    }
}

fn cull_mode(raw: u32) -> GalResult<CullMode> {
    match raw {
        1 => Ok(CullMode::None),
        2 => Ok(CullMode::Front),
        3 => Ok(CullMode::Back),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown cull mode {raw}"),
        )),
    }
}

fn blend_mode(raw: u32) -> GalResult<BlendMode> {
    match raw {
        1 => Ok(BlendMode::Disabled),
        2 => Ok(BlendMode::Alpha),
        3 => Ok(BlendMode::Additive),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown blend mode {raw}"),
        )),
    }
}

fn compare_op(raw: u32) -> GalResult<CompareOp> {
    match raw {
        1 => Ok(CompareOp::Always),
        2 => Ok(CompareOp::Less),
        3 => Ok(CompareOp::LessOrEqual),
        4 => Ok(CompareOp::Equal),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown compare op {raw}"),
        )),
    }
}

fn optional_compare_op(raw: u32) -> GalResult<Option<CompareOp>> {
    if raw == 0 {
        Ok(None)
    } else {
        compare_op(raw).map(Some)
    }
}

fn load_op(raw: u32) -> GalResult<AttachmentLoadOp> {
    match raw {
        1 => Ok(AttachmentLoadOp::Load),
        2 => Ok(AttachmentLoadOp::Clear),
        3 => Ok(AttachmentLoadOp::DontCare),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown attachment load op {raw}"),
        )),
    }
}

fn store_op(raw: u32) -> GalResult<AttachmentStoreOp> {
    match raw {
        1 => Ok(AttachmentStoreOp::Store),
        2 => Ok(AttachmentStoreOp::DontCare),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown attachment store op {raw}"),
        )),
    }
}

fn texture_usage_state(raw: u32) -> GalResult<TextureUsageState> {
    match raw {
        1 => Ok(TextureUsageState::Undefined),
        2 => Ok(TextureUsageState::ShaderRead),
        3 => Ok(TextureUsageState::ShaderWrite),
        4 => Ok(TextureUsageState::ColorAttachment),
        5 => Ok(TextureUsageState::DepthStencilAttachment),
        6 => Ok(TextureUsageState::TransferSrc),
        7 => Ok(TextureUsageState::TransferDst),
        8 => Ok(TextureUsageState::Present),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown texture usage state {raw}"),
        )),
    }
}

fn queue_class(raw: u32) -> GalResult<QueueClass> {
    match raw {
        1 => Ok(QueueClass::Graphics),
        2 => Ok(QueueClass::Compute),
        3 => Ok(QueueClass::Transfer),
        4 => Ok(QueueClass::Present),
        5 => Ok(QueueClass::External),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown queue class {raw}"),
        )),
    }
}

fn stage_flags(bits: u32) -> GalResult<PipelineStageFlags> {
    let known = PipelineStageFlags::DRAW.0
        | PipelineStageFlags::COMPUTE.0
        | PipelineStageFlags::TRANSFER.0
        | PipelineStageFlags::PRESENT.0;
    if bits & !known != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown pipeline stage bits 0x{:x}", bits & !known),
        ));
    }
    Ok(PipelineStageFlags(bits))
}

fn access_flags(bits: u32) -> GalResult<AccessFlags> {
    let known = AccessFlags::READ.0
        | AccessFlags::WRITE.0
        | AccessFlags::COLOR_ATTACHMENT.0
        | AccessFlags::DEPTH_STENCIL.0
        | AccessFlags::TRANSFER.0;
    if bits & !known != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown access bits 0x{:x}", bits & !known),
        ));
    }
    Ok(AccessFlags(bits))
}

fn buffer_usage_bits(bits: u64) -> GalResult<Vec<BufferUsage>> {
    let table = [
        (1_u64 << 0, BufferUsage::Vertex),
        (1_u64 << 1, BufferUsage::Index),
        (1_u64 << 2, BufferUsage::Uniform),
        (1_u64 << 3, BufferUsage::Storage),
        (1_u64 << 4, BufferUsage::TransferSrc),
        (1_u64 << 5, BufferUsage::TransferDst),
        (1_u64 << 6, BufferUsage::Indirect),
        (1_u64 << 7, BufferUsage::HostRead),
        (1_u64 << 8, BufferUsage::HostWrite),
    ];
    usage_bits(bits, &table, "buffer usage")
}

fn texture_usage_bits(bits: u64) -> GalResult<Vec<TextureUsage>> {
    let table = [
        (1_u64 << 0, TextureUsage::Sampled),
        (1_u64 << 1, TextureUsage::Storage),
        (1_u64 << 2, TextureUsage::ColorAttachment),
        (1_u64 << 3, TextureUsage::DepthStencilAttachment),
        (1_u64 << 4, TextureUsage::TransferSrc),
        (1_u64 << 5, TextureUsage::TransferDst),
        (1_u64 << 6, TextureUsage::Present),
        (1_u64 << 7, TextureUsage::HostRead),
        (1_u64 << 8, TextureUsage::HostWrite),
    ];
    usage_bits(bits, &table, "texture usage")
}

fn usage_bits<T: Copy>(bits: u64, table: &[(u64, T)], label: &str) -> GalResult<Vec<T>> {
    let known = table.iter().fold(0_u64, |acc, (bit, _)| acc | *bit);
    if bits == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} bits must be non-zero"),
        ));
    }
    if bits & !known != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown {label} bits 0x{:x}", bits & !known),
        ));
    }
    Ok(table
        .iter()
        .filter_map(|(bit, value)| if bits & *bit != 0 { Some(*value) } else { None })
        .collect())
}

fn check_buffer_capabilities(
    desc: &BufferDesc,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    if desc.size > capabilities.limits.max_buffer_size {
        return Err(GalError::unsupported_feature(
            "buffer size exceeds backend limit",
        ));
    }
    if desc.usages.contains(&BufferUsage::Storage) {
        require_feature(
            capabilities,
            BackendFeature::StorageBuffers,
            "storage buffer",
        )?;
    }
    if desc.usages.contains(&BufferUsage::Indirect) {
        require_any_feature(
            capabilities,
            &[
                BackendFeature::IndirectDraw,
                BackendFeature::IndirectDispatch,
            ],
            "indirect buffer",
        )?;
    }
    if desc.usages.contains(&BufferUsage::HostRead) || desc.usages.contains(&BufferUsage::HostWrite)
    {
        require_feature(
            capabilities,
            BackendFeature::HostBufferAccess,
            "host buffer access",
        )?;
    }
    Ok(())
}

fn check_texture_capabilities(
    desc: &TextureDesc,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    if desc.extent.width > capabilities.limits.max_texture_extent_2d
        || desc.extent.height > capabilities.limits.max_texture_extent_2d
    {
        return Err(GalError::unsupported_feature(
            "texture extent exceeds backend limit",
        ));
    }
    if desc.mip_levels > capabilities.limits.max_texture_mip_levels {
        return Err(GalError::unsupported_feature(
            "texture mip count exceeds backend limit",
        ));
    }
    if desc.array_layers > capabilities.limits.max_texture_array_layers {
        return Err(GalError::unsupported_feature(
            "texture layer count exceeds backend limit",
        ));
    }
    if desc.mip_levels > 1 {
        require_feature(
            capabilities,
            BackendFeature::TextureMipLevels,
            "texture mip levels",
        )?;
    }
    if desc.array_layers > 1 {
        require_feature(
            capabilities,
            BackendFeature::TextureArrayLayers,
            "texture array layers",
        )?;
    }
    if desc.usages.contains(&TextureUsage::Storage) {
        require_feature(
            capabilities,
            BackendFeature::StorageTextures,
            "storage texture",
        )?;
    }
    if desc.usages.contains(&TextureUsage::Present) {
        require_feature(
            capabilities,
            BackendFeature::Presentation,
            "presentation texture",
        )?;
        return Err(GalError::unsupported_feature(
            "presentation/windowing is excluded from ABI version 1",
        ));
    }
    Ok(())
}

fn check_graphics_pipeline_capabilities(
    desc: &GraphicsPipelineDesc,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    require_feature(capabilities, BackendFeature::Graphics, "graphics pipeline")?;
    check_attachment_count(
        desc.color_formats.len(),
        desc.depth_format.is_some(),
        capabilities,
    )?;
    if desc.blend != BlendMode::Disabled {
        require_feature(capabilities, BackendFeature::BlendedPass, "blended pass")?;
    }
    Ok(())
}

fn check_attachment_count(
    color_count: usize,
    has_depth: bool,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    if color_count > capabilities.limits.max_color_attachments as usize {
        return Err(GalError::unsupported_feature(
            "color attachment count exceeds backend limit",
        ));
    }
    if color_count > 1 {
        require_feature(
            capabilities,
            BackendFeature::MultipleColorAttachments,
            "multiple color attachments",
        )?;
    }
    if color_count == 0 && has_depth {
        require_feature(
            capabilities,
            BackendFeature::DepthOnlyPass,
            "depth-only pass",
        )?;
    }
    Ok(())
}

fn require_feature(
    capabilities: BackendCapabilities,
    feature: BackendFeature,
    label: &str,
) -> GalResult<()> {
    if capabilities.supports(feature) {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "backend '{}' does not support {label}",
            capabilities.name
        )))
    }
}

fn require_any_feature(
    capabilities: BackendCapabilities,
    features: &[BackendFeature],
    label: &str,
) -> GalResult<()> {
    if features
        .iter()
        .any(|feature| capabilities.supports(*feature))
    {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "backend '{}' does not support {label}",
            capabilities.name
        )))
    }
}

fn decode_command_op(
    op: &FfiCommandOpAbi,
    attachments: &[FfiPassAttachmentAbi],
    copy_regions: &[FfiBufferImageCopyAbi],
    barriers: &[FfiResourceBarrierAbi],
    capabilities: BackendCapabilities,
) -> GalResult<CommandOp> {
    match op.op_kind {
        1 => {
            require_feature(capabilities, BackendFeature::Graphics, "render pass")?;
            let colors = range_slice(attachments, op.colors, "begin pass color attachments")?
                .iter()
                .map(decode_pass_attachment)
                .collect::<GalResult<Vec<_>>>()?;
            let depth_items =
                range_slice(attachments, op.depth_stencil, "begin pass depth attachment")?;
            if depth_items.len() > 1 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "begin pass may specify at most one depth attachment",
                ));
            }
            check_attachment_count(colors.len(), !depth_items.is_empty(), capabilities)?;
            Ok(CommandOp::BeginPass {
                pass: require_handle(op.primary, HandleKind::RenderPass, "begin pass pass")?,
                target: require_handle(
                    op.secondary,
                    HandleKind::RenderTarget,
                    "begin pass target",
                )?,
                colors,
                depth_stencil: depth_items
                    .first()
                    .map(decode_pass_attachment)
                    .transpose()?,
            })
        }
        2 => Ok(CommandOp::BindGraphicsPipeline(require_handle(
            op.primary,
            HandleKind::GraphicsPipeline,
            "graphics pipeline",
        )?)),
        3 => {
            require_feature(capabilities, BackendFeature::Compute, "compute pipeline")?;
            Ok(CommandOp::BindComputePipeline(require_handle(
                op.primary,
                HandleKind::ComputePipeline,
                "compute pipeline",
            )?))
        }
        4 => Ok(CommandOp::BindResourceSet {
            pipeline_layout: require_handle(
                op.primary,
                HandleKind::PipelineLayout,
                "resource set pipeline layout",
            )?,
            set_index: op.set_index,
            set: require_handle(op.secondary, HandleKind::ResourceSet, "resource set")?,
        }),
        5 => Ok(CommandOp::SetVertexBuffer {
            slot: op.slot,
            buffer: require_handle(op.primary, HandleKind::Buffer, "vertex buffer")?,
            offset: op.offset,
        }),
        6 => Ok(CommandOp::SetIndexBuffer {
            buffer: require_handle(op.primary, HandleKind::Buffer, "index buffer")?,
            offset: op.offset,
        }),
        7 => Ok(CommandOp::Draw {
            vertices: op.count0,
            instances: op.count1,
        }),
        8 => Ok(CommandOp::DrawIndexed {
            indices: op.count0,
            instances: op.count1,
        }),
        9 => {
            require_feature(capabilities, BackendFeature::IndirectDraw, "indirect draw")?;
            Ok(CommandOp::DrawIndirect {
                buffer: require_handle(op.primary, HandleKind::Buffer, "indirect draw buffer")?,
                offset: op.offset,
                draw_count: op.count0,
            })
        }
        10 => Ok(CommandOp::Dispatch {
            groups_x: op.count0,
            groups_y: op.count1,
            groups_z: op.count2,
        }),
        11 => {
            require_feature(
                capabilities,
                BackendFeature::IndirectDispatch,
                "indirect dispatch",
            )?;
            Ok(CommandOp::DispatchIndirect {
                buffer: require_handle(op.primary, HandleKind::Buffer, "indirect dispatch buffer")?,
                offset: op.offset,
            })
        }
        12 => Ok(CommandOp::CopyBuffer {
            src: require_handle(op.primary, HandleKind::Buffer, "copy buffer src")?,
            dst: require_handle(op.secondary, HandleKind::Buffer, "copy buffer dst")?,
            size: op.size,
        }),
        13 => {
            require_feature(
                capabilities,
                BackendFeature::TextureSubresourceCopies,
                "buffer-to-texture copy",
            )?;
            Ok(CommandOp::CopyBufferToTexture(decode_copy_region(
                single_range_item(
                    copy_regions,
                    op.copy_region,
                    "buffer-to-texture copy region",
                )?,
            )?))
        }
        14 => {
            require_feature(
                capabilities,
                BackendFeature::TextureSubresourceCopies,
                "texture-to-buffer copy",
            )?;
            Ok(CommandOp::CopyTextureToBuffer(decode_copy_region(
                single_range_item(
                    copy_regions,
                    op.copy_region,
                    "texture-to-buffer copy region",
                )?,
            )?))
        }
        15 => {
            require_feature(
                capabilities,
                BackendFeature::HostBufferAccess,
                "host write buffer",
            )?;
            Ok(CommandOp::HostWriteBuffer {
                buffer: require_handle(op.primary, HandleKind::Buffer, "host write buffer")?,
                offset: op.offset,
                data: unsafe {
                    read_bounded_bytes(
                        op.inline_bytes,
                        false,
                        FFI_MAX_INLINE_BYTES,
                        "host write data",
                    )?
                },
            })
        }
        16 => {
            require_feature(
                capabilities,
                BackendFeature::HostBufferAccess,
                "host read buffer",
            )?;
            Ok(CommandOp::HostReadBuffer {
                buffer: require_handle(op.primary, HandleKind::Buffer, "host read buffer")?,
                offset: op.offset,
                size: op.size,
            })
        }
        17 => Err(GalError::unsupported_feature(
            "presentation/windowing is excluded from ABI version 1",
        )),
        18 => Ok(CommandOp::Barrier(decode_barrier(single_range_item(
            barriers, op.barrier, "barrier",
        )?)?)),
        19 => Ok(CommandOp::EndPass),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown command op kind {}", op.op_kind),
        )),
    }
}

fn single_range_item<'a, T>(items: &'a [T], range: FfiRange, label: &str) -> GalResult<&'a T> {
    let slice = range_slice(items, range, label)?;
    if slice.len() != 1 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} range must contain exactly one item"),
        ));
    }
    Ok(&slice[0])
}

fn decode_pass_attachment(item: &FfiPassAttachmentAbi) -> GalResult<PassAttachment> {
    validate_item_size::<FfiPassAttachmentAbi>(item.byte_size, "pass attachment")?;
    Ok(PassAttachment {
        view: require_handle(item.view, HandleKind::TextureView, "pass attachment view")?,
        load_op: load_op(item.load_op)?,
        store_op: store_op(item.store_op)?,
        clear_color: if bool_flag(item.has_clear_color, "attachment clear color presence")? {
            Some(item.clear_color.into())
        } else {
            None
        },
    })
}

fn decode_copy_region(item: &FfiBufferImageCopyAbi) -> GalResult<BufferImageCopyRegion> {
    validate_item_size::<FfiBufferImageCopyAbi>(item.byte_size, "buffer image copy")?;
    Ok(BufferImageCopyRegion {
        buffer: require_handle(item.buffer, HandleKind::Buffer, "copy buffer")?,
        buffer_offset: item.buffer_offset,
        bytes_per_row: item.bytes_per_row,
        rows_per_image: item.rows_per_image,
        texture: require_handle(item.texture, HandleKind::Texture, "copy texture")?,
        texture_mip: item.texture_mip,
        texture_layer: item.texture_layer,
        texture_origin: item.texture_origin.into(),
        extent: item.extent.into(),
    })
}

fn decode_barrier(item: &FfiResourceBarrierAbi) -> GalResult<ResourceBarrier> {
    validate_item_size::<FfiResourceBarrierAbi>(item.byte_size, "resource barrier")?;
    Ok(ResourceBarrier {
        resource: require_any_handle(item.resource, "barrier resource")?,
        subresources: if bool_flag(item.has_subresources, "barrier subresource presence")? {
            Some(item.subresources.into())
        } else {
            None
        },
        before: texture_usage_state(item.before)?,
        after: texture_usage_state(item.after)?,
        stages: stage_flags(item.stage_bits)?,
        access: access_flags(item.access_bits)?,
        src_queue: queue_class(item.src_queue)?,
        dst_queue: queue_class(item.dst_queue)?,
    })
}

fn unsupported_feature_from_message(message: &str) -> u32 {
    for (feature, needle) in [
        (BackendFeature::Compute, "compute"),
        (BackendFeature::StorageTextures, "storage texture"),
        (BackendFeature::IndirectDraw, "indirect draw"),
        (BackendFeature::IndirectDispatch, "indirect dispatch"),
        (BackendFeature::MultipleColorAttachments, "multiple color"),
        (BackendFeature::DepthOnlyPass, "depth-only"),
        (BackendFeature::BlendedPass, "blend"),
        (BackendFeature::TextureMipLevels, "mip"),
        (BackendFeature::TextureArrayLayers, "layer"),
        (BackendFeature::Presentation, "presentation"),
    ] {
        if message.contains(needle) {
            return feature as u32;
        }
    }
    0
}

fn buffer_usage_bits_from_desc(usages: &[BufferUsage]) -> u64 {
    usages.iter().fold(0_u64, |bits, usage| {
        bits | match usage {
            BufferUsage::Vertex => 1 << 0,
            BufferUsage::Index => 1 << 1,
            BufferUsage::Uniform => 1 << 2,
            BufferUsage::Storage => 1 << 3,
            BufferUsage::TransferSrc => 1 << 4,
            BufferUsage::TransferDst => 1 << 5,
            BufferUsage::Indirect => 1 << 6,
            BufferUsage::HostRead => 1 << 7,
            BufferUsage::HostWrite => 1 << 8,
        }
    })
}

fn texture_usage_bits_from_desc(usages: &[TextureUsage]) -> u64 {
    usages.iter().fold(0_u64, |bits, usage| {
        bits | match usage {
            TextureUsage::Sampled => 1 << 0,
            TextureUsage::Storage => 1 << 1,
            TextureUsage::ColorAttachment => 1 << 2,
            TextureUsage::DepthStencilAttachment => 1 << 3,
            TextureUsage::TransferSrc => 1 << 4,
            TextureUsage::TransferDst => 1 << 5,
            TextureUsage::Present => 1 << 6,
            TextureUsage::HostRead => 1 << 7,
            TextureUsage::HostWrite => 1 << 8,
        }
    })
}

fn serialize_command_op(out: &mut Vec<u8>, op: &CommandOp) {
    match op {
        CommandOp::BeginPass {
            pass,
            target,
            colors,
            depth_stencil,
        } => {
            push_u32(out, FfiCommandOpKind::BeginPass as u32);
            push_u64(out, pass.raw());
            push_u64(out, target.raw());
            push_u64(out, colors.len() as u64);
            for color in colors {
                serialize_attachment(out, color);
            }
            push_u32(out, u32::from(depth_stencil.is_some()));
            if let Some(depth) = depth_stencil {
                serialize_attachment(out, depth);
            }
        }
        CommandOp::BindGraphicsPipeline(handle) => {
            push_u32(out, FfiCommandOpKind::BindGraphicsPipeline as u32);
            push_u64(out, handle.raw());
        }
        CommandOp::BindComputePipeline(handle) => {
            push_u32(out, FfiCommandOpKind::BindComputePipeline as u32);
            push_u64(out, handle.raw());
        }
        CommandOp::BindResourceSet {
            pipeline_layout,
            set_index,
            set,
        } => {
            push_u32(out, FfiCommandOpKind::BindResourceSet as u32);
            push_u64(out, pipeline_layout.raw());
            push_u32(out, *set_index);
            push_u64(out, set.raw());
        }
        CommandOp::SetVertexBuffer {
            slot,
            buffer,
            offset,
        } => {
            push_u32(out, FfiCommandOpKind::SetVertexBuffer as u32);
            push_u32(out, *slot);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
        }
        CommandOp::SetIndexBuffer { buffer, offset } => {
            push_u32(out, FfiCommandOpKind::SetIndexBuffer as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
        }
        CommandOp::Draw {
            vertices,
            instances,
        } => {
            push_u32(out, FfiCommandOpKind::Draw as u32);
            push_u32(out, *vertices);
            push_u32(out, *instances);
        }
        CommandOp::DrawIndexed { indices, instances } => {
            push_u32(out, FfiCommandOpKind::DrawIndexed as u32);
            push_u32(out, *indices);
            push_u32(out, *instances);
        }
        CommandOp::DrawIndirect {
            buffer,
            offset,
            draw_count,
        } => {
            push_u32(out, FfiCommandOpKind::DrawIndirect as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_u32(out, *draw_count);
        }
        CommandOp::Dispatch {
            groups_x,
            groups_y,
            groups_z,
        } => {
            push_u32(out, FfiCommandOpKind::Dispatch as u32);
            push_u32(out, *groups_x);
            push_u32(out, *groups_y);
            push_u32(out, *groups_z);
        }
        CommandOp::DispatchIndirect { buffer, offset } => {
            push_u32(out, FfiCommandOpKind::DispatchIndirect as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
        }
        CommandOp::CopyBuffer { src, dst, size } => {
            push_u32(out, FfiCommandOpKind::CopyBuffer as u32);
            push_u64(out, src.raw());
            push_u64(out, dst.raw());
            push_u64(out, *size);
        }
        CommandOp::CopyBufferToTexture(region) => {
            push_u32(out, FfiCommandOpKind::CopyBufferToTexture as u32);
            serialize_copy_region(out, region);
        }
        CommandOp::CopyTextureToBuffer(region) => {
            push_u32(out, FfiCommandOpKind::CopyTextureToBuffer as u32);
            serialize_copy_region(out, region);
        }
        CommandOp::HostWriteBuffer {
            buffer,
            offset,
            data,
        } => {
            push_u32(out, FfiCommandOpKind::HostWriteBuffer as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_bytes(out, data);
        }
        CommandOp::HostReadBuffer {
            buffer,
            offset,
            size,
        } => {
            push_u32(out, FfiCommandOpKind::HostReadBuffer as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_u64(out, *size);
        }
        CommandOp::Present {
            texture,
            subresources,
        } => {
            push_u32(out, FfiCommandOpKind::Present as u32);
            push_u64(out, texture.raw());
            serialize_subresources(out, *subresources);
        }
        CommandOp::Barrier(barrier) => {
            push_u32(out, FfiCommandOpKind::Barrier as u32);
            push_u64(out, barrier.resource.raw());
            push_u32(out, u32::from(barrier.subresources.is_some()));
            if let Some(range) = barrier.subresources {
                serialize_subresources(out, range);
            }
            push_u32(out, barrier.before as u32);
            push_u32(out, barrier.after as u32);
            push_u32(out, barrier.stages.0);
            push_u32(out, barrier.access.0);
            push_u32(out, barrier.src_queue as u32);
            push_u32(out, barrier.dst_queue as u32);
        }
        CommandOp::EndPass => push_u32(out, FfiCommandOpKind::EndPass as u32),
    }
}

fn serialize_attachment(out: &mut Vec<u8>, attachment: &PassAttachment) {
    push_u64(out, attachment.view.raw());
    push_u32(out, attachment.load_op as u32);
    push_u32(out, attachment.store_op as u32);
    push_u32(out, u32::from(attachment.clear_color.is_some()));
    if let Some(color) = attachment.clear_color {
        push_f32(out, color.r);
        push_f32(out, color.g);
        push_f32(out, color.b);
        push_f32(out, color.a);
    }
}

fn serialize_copy_region(out: &mut Vec<u8>, region: &BufferImageCopyRegion) {
    push_u64(out, region.buffer.raw());
    push_u64(out, region.buffer_offset);
    push_u32(out, region.bytes_per_row);
    push_u32(out, region.rows_per_image);
    push_u64(out, region.texture.raw());
    push_u32(out, region.texture_mip);
    push_u32(out, region.texture_layer);
    push_u32(out, region.texture_origin.x);
    push_u32(out, region.texture_origin.y);
    push_u32(out, region.texture_origin.z);
    push_extent(out, region.extent);
}

fn serialize_subresources(out: &mut Vec<u8>, range: TextureSubresourceRange) {
    push_u32(out, range.base_mip);
    push_u32(out, range.mip_count);
    push_u32(out, range.base_layer);
    push_u32(out, range.layer_count);
}

fn push_create_prefix(out: &mut Vec<u8>, request_id: u64, label: &str) {
    push_u64(out, request_id);
    push_str(out, label);
}

fn push_extent(out: &mut Vec<u8>, extent: Extent3d) {
    push_u32(out, extent.width);
    push_u32(out, extent.height);
    push_u32(out, extent.depth);
}

fn push_str(out: &mut Vec<u8>, value: &str) {
    push_bytes(out, value.as_bytes());
}

fn push_bytes(out: &mut Vec<u8>, bytes: &[u8]) {
    push_u64(out, bytes.len() as u64);
    out.extend_from_slice(bytes);
}

fn push_u64(out: &mut Vec<u8>, value: u64) {
    out.extend_from_slice(&value.to_le_bytes());
}

fn push_u32(out: &mut Vec<u8>, value: u32) {
    out.extend_from_slice(&value.to_le_bytes());
}

fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_le_bytes());
}

#[allow(dead_code)]
fn _abi_status_domain_value(domain: ErrorDomain) -> u32 {
    domain as u32
}
