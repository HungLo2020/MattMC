//! Command list, submission, completion and retirement records.

use super::*;

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
    // Append-only: stable ABI values 1..19 are already public.
    DrawIndexedIndirect = 20,
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
