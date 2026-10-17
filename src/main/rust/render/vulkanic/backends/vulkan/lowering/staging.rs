//! Reusable host-visible staging for buffer uploads recorded into a submission.
//!
//! Large or unaligned `HostWriteBuffer` data is copied through staging memory.
//! Creating and freeing one buffer and allocation per upload costs driver
//! calls every frame, so the lowerer keeps persistently mapped chunks and
//! sub-allocates them linearly while encoding. A submission owns its chunks
//! until its timeline value retires; only then may they be rewritten.

use std::ptr::NonNull;
use std::sync::Arc;

use ash::vk;

use super::super::device::VulkanContext;
use crate::render::vulkanic::error::{GalError, GalResult};

/// Default chunk size; larger single uploads get a dedicated chunk.
pub(super) const STAGING_CHUNK_BYTES: u64 = 4 * 1024 * 1024;
/// Idle chunks retained for reuse. Excess chunks are freed on retirement.
pub(super) const MAX_IDLE_STAGING_BYTES: u64 = 32 * 1024 * 1024;
const STAGING_ALIGNMENT: u64 = 16;

struct MappedBytes(NonNull<u8>);

// The mapping is owned by exactly one chunk, which is moved between the
// lowerer and its submissions; writes happen only while encoding.
unsafe impl Send for MappedBytes {}
unsafe impl Sync for MappedBytes {}

/// One persistently mapped `TRANSFER_SRC` buffer. Dropping it frees the
/// driver objects, so failed encodes and teardown cannot leak memory.
pub(super) struct StagingChunk {
    context: Arc<VulkanContext>,
    pub(super) buffer: vk::Buffer,
    memory: vk::DeviceMemory,
    mapped: MappedBytes,
    capacity: u64,
}

impl StagingChunk {
    fn new(context: Arc<VulkanContext>, capacity: u64) -> GalResult<Self> {
        let info = vk::BufferCreateInfo::default()
            .size(capacity)
            .usage(vk::BufferUsageFlags::TRANSFER_SRC)
            .sharing_mode(vk::SharingMode::EXCLUSIVE);
        let buffer = unsafe { context.device.create_buffer(&info, None) }
            .map_err(|e| GalError::backend(format!("staging buffer creation failed: {e:?}")))?;
        let requirements = unsafe { context.device.get_buffer_memory_requirements(buffer) };
        let memory = match context.allocate_memory(
            requirements,
            vk::MemoryPropertyFlags::HOST_VISIBLE | vk::MemoryPropertyFlags::HOST_COHERENT,
        ) {
            Ok(memory) => memory,
            Err(error) => {
                unsafe { context.device.destroy_buffer(buffer, None) };
                return Err(error);
            }
        };
        let release = |context: &VulkanContext| unsafe {
            context.device.destroy_buffer(buffer, None);
            context.device.free_memory(memory, None);
        };
        if let Err(e) = unsafe { context.device.bind_buffer_memory(buffer, memory, 0) } {
            release(&context);
            return Err(GalError::backend(format!("staging buffer binding failed: {e:?}")));
        }
        let mapped = match unsafe {
            context.device.map_memory(memory, 0, vk::WHOLE_SIZE, vk::MemoryMapFlags::empty())
        } {
            Ok(pointer) => NonNull::new(pointer.cast::<u8>()),
            Err(e) => {
                release(&context);
                return Err(GalError::backend(format!("staging memory mapping failed: {e:?}")));
            }
        };
        let Some(mapped) = mapped else {
            release(&context);
            return Err(GalError::backend("staging memory mapping returned null"));
        };
        Ok(Self { context, buffer, memory, mapped: MappedBytes(mapped), capacity })
    }

    pub(super) fn capacity(&self) -> u64 {
        self.capacity
    }
}

impl Drop for StagingChunk {
    fn drop(&mut self) {
        unsafe {
            // Freeing mapped memory implicitly unmaps it.
            self.context.device.destroy_buffer(self.buffer, None);
            self.context.device.free_memory(self.memory, None);
        }
    }
}

/// Linear allocator for one encode. `used` chunks belong to the submission;
/// `idle` chunks were lent by the lowerer and are returned after encoding.
#[derive(Default)]
pub(super) struct StagingCursor {
    pub(super) idle: Vec<StagingChunk>,
    pub(super) used: Vec<StagingChunk>,
    offset: u64,
    pub(super) chunks_created: u64,
}

impl StagingCursor {
    pub(super) fn with_idle(idle: Vec<StagingChunk>) -> Self {
        Self { idle, ..Self::default() }
    }

    /// Copies `bytes` into staging memory owned by this submission and
    /// returns the source buffer and offset for a transfer command.
    pub(super) fn stage(
        &mut self,
        context: &Arc<VulkanContext>,
        bytes: &[u8],
    ) -> GalResult<(vk::Buffer, u64)> {
        let len = bytes.len() as u64;
        let aligned = self.offset.next_multiple_of(STAGING_ALIGNMENT);
        let fits = self
            .used
            .last()
            .is_some_and(|chunk| aligned.checked_add(len).is_some_and(|end| end <= chunk.capacity));
        if !fits {
            // Best fit, so a rare oversized chunk is not spent on small uploads.
            let reuse = self
                .idle
                .iter()
                .enumerate()
                .filter(|(_, chunk)| chunk.capacity >= len)
                .min_by_key(|(_, chunk)| chunk.capacity)
                .map(|(index, _)| index);
            let chunk = match reuse {
                Some(index) => self.idle.swap_remove(index),
                None => {
                    self.chunks_created += 1;
                    StagingChunk::new(context.clone(), len.max(STAGING_CHUNK_BYTES))?
                }
            };
            self.used.push(chunk);
            self.offset = 0;
        }
        let offset = if fits { aligned } else { 0 };
        let chunk = self.used.last().expect("staging chunk selected");
        let start = usize::try_from(offset)
            .map_err(|_| GalError::backend("staging offset does not fit usize"))?;
        unsafe {
            std::ptr::copy_nonoverlapping(
                bytes.as_ptr(),
                chunk.mapped.0.as_ptr().add(start),
                bytes.len(),
            );
        }
        self.offset = offset + len;
        Ok((chunk.buffer, offset))
    }
}

/// Returns retired chunks to the idle list, freeing any beyond the bound.
/// Smaller chunks are kept first: default-size chunks serve every frame,
/// while an oversized one served a single large upload.
pub(super) fn recycle_chunks(idle: &mut Vec<StagingChunk>, retired: Vec<StagingChunk>) {
    idle.extend(retired);
    let mut retained = 0_u64;
    idle.sort_by_key(|chunk| chunk.capacity);
    idle.retain(|chunk| {
        retained = retained.saturating_add(chunk.capacity);
        retained <= MAX_IDLE_STAGING_BYTES
    });
}
