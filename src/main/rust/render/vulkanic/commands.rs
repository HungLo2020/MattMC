use super::handles::Handle;
use super::resources::{Extent3d, IndexType, QueueClass, TextureSubresourceRange};
use super::sync::SubmissionId;
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};

/// CPU lifetime receipt for transient storage referenced by prepared commands.
/// The allocator retains one owner; each command copy retains another. GAL
/// records accepted use before releasing its command references. Discarding
/// commands releases reservations without pretending that work was submitted.
#[derive(Clone, Debug, Default)]
pub struct SubmissionUsage(Arc<AtomicU64>);

impl PartialEq for SubmissionUsage {
    fn eq(&self, other: &Self) -> bool {
        Arc::ptr_eq(&self.0, &other.0)
    }
}
impl Eq for SubmissionUsage {}

impl SubmissionUsage {
    pub(super) fn has_pending_commands(&self) -> bool {
        Arc::strong_count(&self.0) > 1
    }
    pub(super) fn last_submission(&self) -> SubmissionId {
        SubmissionId(self.0.load(Ordering::Acquire))
    }
    pub(super) fn accept(&self, id: SubmissionId) {
        self.0.fetch_max(id.0, Ordering::Release);
    }
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureUsageState {
    Undefined = 1,
    ShaderRead = 2,
    ShaderWrite = 3,
    ColorAttachment = 4,
    DepthStencilAttachment = 5,
    TransferSrc = 6,
    TransferDst = 7,
    Present = 8,
    IndexRead = 9,
    /// Read-only access through a storage-image descriptor. Unlike sampled
    /// reads this remains in Vulkan GENERAL layout.
    ShaderStorageRead = 10,
    /// Read by an indirect draw/dispatch command processor. This is distinct
    /// from shader and index input so explicit backends can publish host or
    /// transfer writes to the correct command-processing stage.
    IndirectRead = 11,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceBarrier {
    pub resource: Handle,
    pub subresources: Option<TextureSubresourceRange>,
    pub before: TextureUsageState,
    pub after: TextureUsageState,
    pub src_queue: QueueClass,
    pub dst_queue: QueueClass,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ClearColor {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum AttachmentLoadOp {
    Load = 1,
    Clear = 2,
    DontCare = 3,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum AttachmentStoreOp {
    Store = 1,
    DontCare = 2,
}

#[derive(Clone, Debug, PartialEq)]
pub struct PassAttachment {
    pub view: Handle,
    pub load_op: AttachmentLoadOp,
    pub store_op: AttachmentStoreOp,
    pub clear_color: Option<ClearColor>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TextureOrigin3d {
    pub x: u32,
    pub y: u32,
    pub z: u32,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BufferImageCopyRegion {
    pub buffer: Handle,
    pub buffer_offset: u64,
    pub bytes_per_row: u32,
    pub rows_per_image: u32,
    pub texture: Handle,
    pub texture_mip: u32,
    pub texture_layer: u32,
    pub texture_origin: TextureOrigin3d,
    pub extent: Extent3d,
}

/// Ordering of rows within the destination copy rectangle. This is data
/// transformation, not an implicit framebuffer or shader coordinate convention.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureRowOrder {
    Preserve,
    Reverse,
}

/// One explicit texture-to-texture copy. Source and destination subresources
/// are named independently so a frontend can retain immutable depth/history
/// snapshots without exposing an API-specific image copy primitive.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TextureImageCopyRegion {
    pub row_order: TextureRowOrder,
    pub src_texture: Handle,
    pub src_mip: u32,
    pub src_layer: u32,
    pub src_origin: TextureOrigin3d,
    pub dst_texture: Handle,
    pub dst_mip: u32,
    pub dst_layer: u32,
    pub dst_origin: TextureOrigin3d,
    pub extent: Extent3d,
}

#[derive(Clone, Debug, PartialEq)]
pub enum CommandOp {
    /// GAL consumes this CPU receipt; it is never sent to a GPU backend.
    TrackSubmission(SubmissionUsage),
    BeginPass {
        pass: Handle,
        target: Handle,
        colors: Vec<PassAttachment>,
        depth_stencil: Option<PassAttachment>,
    },
    BindGraphicsPipeline(Handle),
    BindComputePipeline(Handle),
    BindResourceSet {
        pipeline_layout: Handle,
        set_index: u32,
        set: Handle,
        dynamic_offsets: Vec<u64>,
    },
    SetVertexBuffer {
        slot: u32,
        buffer: Handle,
        offset: u64,
    },
    SetIndexBuffer {
        buffer: Handle,
        offset: u64,
        index_type: IndexType,
    },
    Draw {
        vertices: u32,
        instances: u32,
    },
    DrawIndexed {
        indices: u32,
        instances: u32,
    },
    DrawIndirect {
        buffer: Handle,
        offset: u64,
        draw_count: u32,
    },
    /// Draws indexed command records from a GAL-owned indirect buffer. Each
    /// record uses the backend-neutral five-lane indexed layout: index count,
    /// instance count, first index, signed vertex offset, and first instance.
    DrawIndexedIndirect {
        buffer: Handle,
        offset: u64,
        draw_count: u32,
    },
    Dispatch {
        groups_x: u32,
        groups_y: u32,
        groups_z: u32,
    },
    DispatchIndirect {
        buffer: Handle,
        offset: u64,
    },
    CopyBuffer {
        src: Handle,
        dst: Handle,
        size: u64,
    },
    CopyBufferToTexture(BufferImageCopyRegion),
    CopyTextureToBuffer(BufferImageCopyRegion),
    CopyTexture(TextureImageCopyRegion),
    /// Copies the acquired presentation image into a Rust-owned texture.
    ///
    /// Frame targets are intentionally opaque GAL resources; this operation
    /// is the only legal way for a frontend to make their pixels sampleable
    /// without exposing a backend image/view or native swapchain handle.
    /// The destination must be explicitly transitioned to `TransferDst` by a
    /// preceding GAL barrier in the same submission.
    CopyFrameTargetToTexture {
        src: Handle,
        dst: Handle,
        extent: Extent3d,
    },
    /// Copies a Rust-owned texture into the acquired presentation image.
    /// The frame target remains opaque; this is the only legal presentation
    /// write path for offscreen Rust post-processing.
    CopyTextureToFrameTarget {
        src: Handle,
        dst: Handle,
        extent: Extent3d,
    },
    /// Generates the descendant mip levels in one explicit texture range.
    /// The first level is the source; every following level is written by the
    /// operation. Backends choose their native implementation privately.
    GenerateMipmaps {
        texture: Handle,
        subresources: TextureSubresourceRange,
    },
    HostWriteBuffer {
        buffer: Handle,
        offset: u64,
        data: Vec<u8>,
    },
    HostReadBuffer {
        buffer: Handle,
        offset: u64,
        size: u64,
    },
    Present {
        texture: Handle,
        subresources: TextureSubresourceRange,
    },
    Barrier(ResourceBarrier),
    EndPass,
}

#[derive(Clone, Debug, PartialEq)]
pub struct CommandListDesc {
    pub label: String,
    pub operations: Vec<CommandOp>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct CommandList {
    pub label: String,
    pub operations: Vec<CommandOp>,
}

impl From<CommandListDesc> for CommandList {
    fn from(desc: CommandListDesc) -> Self {
        Self {
            label: desc.label,
            operations: desc.operations,
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct SubmissionBatch {
    pub label: String,
    pub command_lists: Vec<CommandList>,
}

#[derive(Clone, Debug, PartialEq)]
pub(super) struct ValidatedCommandList {
    pub(super) label: String,
    pub(super) operations: Vec<CommandOp>,
}

#[derive(Clone, Debug, PartialEq)]
pub(super) struct ValidatedSubmissionBatch {
    pub(super) label: String,
    pub(super) command_lists: Vec<ValidatedCommandList>,
}

impl From<SubmissionBatch> for ValidatedSubmissionBatch {
    fn from(batch: SubmissionBatch) -> Self {
        Self {
            label: batch.label,
            command_lists: batch
                .command_lists
                .into_iter()
                .map(|list| ValidatedCommandList {
                    label: list.label,
                    operations: list.operations,
                })
                .collect(),
        }
    }
}
