//! Commands: the ops a command list records (passes, binds, draws,
//! dispatches, copies, barriers, uploads), command lists, and the submission
//! batches handed to `VulkanicGal::submit`.

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
    /// Whether prepared commands still reference this storage.
    pub fn has_pending_commands(&self) -> bool {
        Arc::strong_count(&self.0) > 1
    }
    /// The latest submission that used this storage.
    pub fn last_submission(&self) -> SubmissionId {
        SubmissionId(self.0.load(Ordering::Acquire))
    }
    pub(super) fn accept(&self, id: SubmissionId) {
        self.0.fetch_max(id.0, Ordering::Release);
    }
    /// Marks this storage as used by `id` without submitting, for tests of
    /// renderer-side reclamation outside the GAL.
    #[cfg(test)]
    pub(crate) fn accept_for_test(&self, id: SubmissionId) {
        self.accept(id);
    }
}

/// How a resource is used before or after a barrier; values are part of
/// the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureUsageState {
    /// Contents are not needed (first use or discard).
    Undefined = 1,
    /// Sampled or read by shaders.
    ShaderRead = 2,
    /// Written by shaders through a storage binding.
    ShaderWrite = 3,
    /// Rendered to as a color attachment.
    ColorAttachment = 4,
    /// Rendered to as a depth-stencil attachment.
    DepthStencilAttachment = 5,
    /// Source of a copy.
    TransferSrc = 6,
    /// Destination of a copy or upload.
    TransferDst = 7,
    /// Handed to the presentation engine.
    Present = 8,
    /// Read as an index buffer.
    IndexRead = 9,
    /// Read-only access through a storage-image descriptor. Unlike sampled
    /// reads this remains in Vulkan GENERAL layout.
    ShaderStorageRead = 10,
    /// Read by an indirect draw/dispatch command processor. This is distinct
    /// from shader and index input so explicit backends can publish host or
    /// transfer writes to the correct command-processing stage.
    IndirectRead = 11,
}

/// Orders earlier accesses to a resource before later ones. Hazard
/// analysis rejects overlapping conflicting accesses that no barrier
/// separates.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceBarrier {
    /// The buffer, texture or texture view the barrier covers.
    pub resource: Handle,
    /// The mips and layers covered; `None` means the whole texture (or the
    /// view's range). Ignored for buffers, which are covered whole.
    pub subresources: Option<TextureSubresourceRange>,
    /// The usage of the accesses before the barrier.
    pub before: TextureUsageState,
    /// The usage of the accesses after the barrier.
    pub after: TextureUsageState,
    /// The queue class releasing the resource.
    pub src_queue: QueueClass,
    /// The queue class acquiring the resource.
    pub dst_queue: QueueClass,
}

/// An RGBA clear color.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ClearColor {
    /// Red.
    pub r: f32,
    /// Green.
    pub g: f32,
    /// Blue.
    pub b: f32,
    /// Alpha.
    pub a: f32,
}

/// What a pass does with an attachment's contents when it begins; values
/// are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum AttachmentLoadOp {
    /// Keep the previous contents.
    Load = 1,
    /// Clear to the attachment's clear color.
    Clear = 2,
    /// Contents are undefined.
    DontCare = 3,
}

/// What a pass does with an attachment's contents when it ends; values are
/// part of the C ABI. A later pass may only `Load` contents that were
/// stored.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum AttachmentStoreOp {
    /// Keep the rendered contents.
    Store = 1,
    /// Contents may be discarded.
    DontCare = 2,
}

/// One attachment of a pass.
#[derive(Clone, Debug, PartialEq)]
pub struct PassAttachment {
    /// The texture view (or frame target) rendered to.
    pub view: Handle,
    /// What happens to its contents when the pass begins.
    pub load_op: AttachmentLoadOp,
    /// What happens to its contents when the pass ends.
    pub store_op: AttachmentStoreOp,
    /// The clear color, for `AttachmentLoadOp::Clear`.
    pub clear_color: Option<ClearColor>,
}

/// A texel position in a texture.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TextureOrigin3d {
    /// Column.
    pub x: u32,
    /// Row.
    pub y: u32,
    /// Depth slice (0 for 2D textures).
    pub z: u32,
}

/// A copy between a buffer and one mip/layer region of a texture.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BufferImageCopyRegion {
    /// The buffer side of the copy.
    pub buffer: Handle,
    /// Byte offset of the first texel in the buffer.
    pub buffer_offset: u64,
    /// Bytes between the starts of consecutive rows in the buffer.
    pub bytes_per_row: u32,
    /// Rows between the starts of consecutive depth slices in the buffer.
    pub rows_per_image: u32,
    /// The texture side of the copy.
    pub texture: Handle,
    /// The texture mip level.
    pub texture_mip: u32,
    /// The texture array layer.
    pub texture_layer: u32,
    /// The first texel of the region in the texture.
    pub texture_origin: TextureOrigin3d,
    /// The region size in texels.
    pub extent: Extent3d,
}

/// Ordering of rows within the destination copy rectangle. This is data
/// transformation, not an implicit framebuffer or shader coordinate convention.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureRowOrder {
    /// Rows keep their order.
    Preserve,
    /// Rows are written bottom-up (requires the row-reversal capability).
    Reverse,
}

/// One explicit texture-to-texture copy. Source and destination subresources
/// are named independently so a frontend can retain immutable depth/history
/// snapshots without exposing an API-specific image copy primitive.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TextureImageCopyRegion {
    /// Whether destination rows are reversed.
    pub row_order: TextureRowOrder,
    /// The source texture.
    pub src_texture: Handle,
    /// The source mip level.
    pub src_mip: u32,
    /// The source array layer.
    pub src_layer: u32,
    /// The first source texel.
    pub src_origin: TextureOrigin3d,
    /// The destination texture.
    pub dst_texture: Handle,
    /// The destination mip level.
    pub dst_mip: u32,
    /// The destination array layer.
    pub dst_layer: u32,
    /// The first destination texel.
    pub dst_origin: TextureOrigin3d,
    /// The region size in texels.
    pub extent: Extent3d,
}

/// One recorded command. A command list is a sequence of these; the GAL
/// validates every op against handles, capabilities and pass state before
/// a backend sees it.
#[derive(Clone, Debug, PartialEq)]
pub enum CommandOp {
    /// GAL consumes this CPU receipt; it is never sent to a GPU backend.
    TrackSubmission(SubmissionUsage),
    /// Begins a render pass on a target.
    BeginPass {
        /// The render pass.
        pass: Handle,
        /// The render target or frame target rendered to.
        target: Handle,
        /// The color attachments, in order.
        colors: Vec<PassAttachment>,
        /// The depth-stencil attachment, if any.
        depth_stencil: Option<PassAttachment>,
    },
    /// Binds a graphics pipeline (inside a pass).
    BindGraphicsPipeline(Handle),
    /// Binds a compute pipeline (outside a pass).
    BindComputePipeline(Handle),
    /// Binds a resource set at an index of the bound pipeline's layout.
    BindResourceSet {
        /// The layout the set is bound against; must match the bound pipeline.
        pipeline_layout: Handle,
        /// The set index in that layout.
        set_index: u32,
        /// The resource set.
        set: Handle,
        /// Offsets for the set's dynamic buffer bindings, in binding order.
        dynamic_offsets: Vec<u64>,
    },
    /// Binds a vertex buffer to a slot.
    SetVertexBuffer {
        /// The vertex buffer slot.
        slot: u32,
        /// The buffer.
        buffer: Handle,
        /// Byte offset of the first vertex.
        offset: u64,
    },
    /// Binds the index buffer.
    SetIndexBuffer {
        /// The buffer.
        buffer: Handle,
        /// Byte offset of the first index.
        offset: u64,
        /// The index size.
        index_type: IndexType,
    },
    /// Draws non-indexed vertices.
    Draw {
        /// Vertices per instance.
        vertices: u32,
        /// Instances.
        instances: u32,
    },
    /// Draws indexed vertices from the bound index buffer.
    DrawIndexed {
        /// Indices per instance.
        indices: u32,
        /// Instances.
        instances: u32,
    },
    /// Draws non-indexed vertices from records in a buffer.
    DrawIndirect {
        /// The buffer holding the draw records.
        buffer: Handle,
        /// Byte offset of the first record (four-byte aligned).
        offset: u64,
        /// How many records to draw (non-zero).
        draw_count: u32,
    },
    /// Draws indexed command records from a GAL-owned indirect buffer. Each
    /// record uses the backend-neutral five-lane indexed layout: index count,
    /// instance count, first index, signed vertex offset, and first instance.
    DrawIndexedIndirect {
        /// The buffer holding the indexed draw records.
        buffer: Handle,
        /// Byte offset of the first record.
        offset: u64,
        /// How many records to draw.
        draw_count: u32,
    },
    /// Dispatches compute work groups.
    Dispatch {
        /// Work groups along x.
        groups_x: u32,
        /// Work groups along y.
        groups_y: u32,
        /// Work groups along z.
        groups_z: u32,
    },
    /// Dispatches compute work groups counted in a buffer.
    DispatchIndirect {
        /// The buffer holding the group counts.
        buffer: Handle,
        /// Byte offset of the counts.
        offset: u64,
    },
    /// Copies the first `size` bytes of one buffer to another.
    CopyBuffer {
        /// The source buffer.
        src: Handle,
        /// The destination buffer.
        dst: Handle,
        /// Bytes to copy.
        size: u64,
    },
    /// Copies a byte range between buffers.
    CopyBufferRegion {
        /// The source buffer.
        src: Handle,
        /// Source byte offset.
        src_offset: u64,
        /// The destination buffer.
        dst: Handle,
        /// Destination byte offset.
        dst_offset: u64,
        /// Bytes to copy.
        size: u64,
    },
    /// Uploads a buffer region into a texture.
    CopyBufferToTexture(BufferImageCopyRegion),
    /// Copies a texture region into a buffer.
    CopyTextureToBuffer(BufferImageCopyRegion),
    /// Copies between textures.
    CopyTexture(TextureImageCopyRegion),
    /// Copies the acquired presentation image into a Rust-owned texture.
    ///
    /// Frame targets are intentionally opaque GAL resources; this operation
    /// is the only legal way for a frontend to make their pixels sampleable
    /// without exposing a backend image/view or native swapchain handle.
    /// The destination must be explicitly transitioned to `TransferDst` by a
    /// preceding GAL barrier in the same submission.
    CopyFrameTargetToTexture {
        /// The frame target.
        src: Handle,
        /// The destination texture.
        dst: Handle,
        /// The region size: non-zero, depth 1, within both images.
        extent: Extent3d,
    },
    /// Copies a Rust-owned texture into the acquired presentation image.
    /// The frame target remains opaque; this is the only legal presentation
    /// write path for offscreen Rust post-processing.
    CopyTextureToFrameTarget {
        /// The source texture.
        src: Handle,
        /// The frame target.
        dst: Handle,
        /// The region size: non-zero, depth 1, within both images.
        extent: Extent3d,
    },
    /// Generates the descendant mip levels in one explicit texture range.
    /// The first level is the source; every following level is written by the
    /// operation. Backends choose their native implementation privately.
    GenerateMipmaps {
        /// The texture.
        texture: Handle,
        /// The mip range: its first level is the source.
        subresources: TextureSubresourceRange,
    },
    /// Writes host bytes into a buffer when the submission executes.
    HostWriteBuffer {
        /// The buffer.
        buffer: Handle,
        /// Destination byte offset.
        offset: u64,
        /// The bytes to write.
        data: Vec<u8>,
    },
    /// Reads buffer bytes back to the host; available from
    /// `VulkanicGal::completed_host_reads` once the submission completes.
    HostReadBuffer {
        /// The buffer.
        buffer: Handle,
        /// Source byte offset.
        offset: u64,
        /// Bytes to read.
        size: u64,
    },
    /// Marks a texture range as handed to presentation.
    Present {
        /// The texture presented.
        texture: Handle,
        /// The mips and layers presented.
        subresources: TextureSubresourceRange,
    },
    /// Orders accesses to a resource (see `ResourceBarrier`).
    Barrier(ResourceBarrier),
    /// Ends the current render pass.
    EndPass,
}

/// A labelled sequence of command ops to validate into a `CommandList`.
#[derive(Clone, Debug, PartialEq)]
pub struct CommandListDesc {
    /// A debug label, used in errors and traces.
    pub label: String,
    /// The ops, in execution order.
    pub operations: Vec<CommandOp>,
}

/// A validated sequence of command ops, ready to submit.
#[derive(Clone, Debug, PartialEq)]
pub struct CommandList {
    /// The debug label.
    pub label: String,
    /// The ops, in execution order.
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

/// The command lists submitted together; they execute in order.
#[derive(Clone, Debug, PartialEq)]
pub struct SubmissionBatch {
    /// A debug label, used in errors and traces.
    pub label: String,
    /// The command lists, in execution order.
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
