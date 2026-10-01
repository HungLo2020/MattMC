//! Resource batch records: descriptor ABIs, updates and creation results.

use super::*;

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
