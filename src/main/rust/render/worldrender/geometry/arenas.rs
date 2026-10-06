//! Geometry arenas and pages, instance/indirect/sorted-index streams, frame streams, multidraw and the upload queue.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) const SOURCE_TERRAIN_FRAME_STREAM_SLOT_COUNT: usize = 3;

pub(in crate::render::worldrender) const SOURCE_TERRAIN_FRAME_STREAM_MIN_BYTES: u64 = 64 * 1024;

/// Hard cap for one completion-gated lowered-source frame stream.  A source
/// frame may contain several writers, but it must never be allowed to turn a
/// reusable slot into an unbounded native allocation.  The slot count above
/// therefore bounds aggregate residency to three times this value.
pub const LOWERED_SOURCE_FRAME_STREAM_MAX_BYTES: u64 = 256 * 1024 * 1024;

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(in crate::render::worldrender) struct MeshPageResourceSetKey {
    pub(in crate::render::worldrender) resource_layout: Handle,
    pub(in crate::render::worldrender) vertex_buffer: Handle,
    pub(in crate::render::worldrender) instance_buffer: Handle,
    pub(in crate::render::worldrender) texture_view: Handle,
    pub(in crate::render::worldrender) sampler: Handle,
    pub(in crate::render::worldrender) observation_buffer: Option<Handle>,
}

pub(in crate::render::worldrender) struct MeshGeometryResources {
    pub(in crate::render::worldrender) vertex_buffer: Handle,
    pub(in crate::render::worldrender) vertex_offset: u64,
    pub(in crate::render::worldrender) vertex_range: u64,
    pub(in crate::render::worldrender) vertex_stride: usize,
    pub(in crate::render::worldrender) index_buffer: Handle,
    pub(in crate::render::worldrender) index_offset: u64,
    pub(in crate::render::worldrender) index_range: u64,
}

pub(in crate::render::worldrender) struct MeshGeometryArenaPage {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) staging_buffer: Option<Handle>,
    pub(in crate::render::worldrender) upload_initialized: bool,
    pub(in crate::render::worldrender) capacity: u64,
    pub(in crate::render::worldrender) free_ranges: Vec<(u64, u64)>,
}

#[derive(Default)]
pub(in crate::render::worldrender) struct MeshGeometryArena {
    pub(in crate::render::worldrender) vertex_pages: Vec<MeshGeometryArenaPage>,
    pub(in crate::render::worldrender) index_pages: Vec<MeshGeometryArenaPage>,
    pub(in crate::render::worldrender) pending_releases: Vec<(SubmissionId, MeshGeometryResources)>,
}

pub(in crate::render::worldrender) struct SourceMeshResources {
    pub(in crate::render::worldrender) geometry_key: MeshGeometryResourceKey,
    pub(in crate::render::worldrender) vertex_offset: u64,
    pub(in crate::render::worldrender) vertex_stride: usize,
    pub(in crate::render::worldrender) index_buffer: Handle,
    pub(in crate::render::worldrender) index_offset: u64,
    pub(in crate::render::worldrender) index_type: IndexType,
    pub(in crate::render::worldrender) pipeline_layout: Handle,
    pub(in crate::render::worldrender) pipeline: Handle,
    pub(in crate::render::worldrender) shadow_pipeline: Option<Handle>,
    pub(in crate::render::worldrender) resource_set: Handle,
}

/// Source terrain geometry shares large device pages instead of owning two
/// buffers per section mesh. 128 MiB is Vulkan's guaranteed minimum
/// `maxStorageBufferRange`, so a whole page is always bindable as one
/// vertex stream; indices are rebased to the page's vertex origin at upload.
pub(in crate::render::worldrender) const SOURCE_TERRAIN_GEOMETRY_PAGE_BYTES: u64 = 128 * 1024 * 1024;

/// Page ranges keep 256-byte alignment (a multiple of the 64-byte source
/// vertex and of the u32 index size).
pub(in crate::render::worldrender) const SOURCE_TERRAIN_GEOMETRY_ALIGNMENT: u64 = 256;

/// Multi-draw terrain binds the instance stream at offset zero and addresses
/// records with `firstInstance`. Record offsets require the 80-byte stride;
/// only descriptor offsets (including uniforms and direct instances) need
/// 256-byte alignment.
pub(in crate::render::worldrender) const SOURCE_TERRAIN_MULTIDRAW_INSTANCE_ALIGNMENT: u64 =
    TERRAIN_SOURCE_INSTANCE_BYTES as u64;

// Retain the existing conservative, descriptor-aligned per-batch capacity
// budget. Packing changes the staged/uploaded bytes, not slot reservations;
// concatenated reservations still cover alignment of subsequent uniforms.
const SOURCE_TERRAIN_MULTIDRAW_RESERVATION_PADDING: u64 = 1280;

/// Whole-slot instance bindings stay within Vulkan's guaranteed storage range.
pub(in crate::render::worldrender) const SOURCE_TERRAIN_MULTIDRAW_INSTANCE_RANGE_MAX: u64 = 128 * 1024 * 1024;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct SourceGeometryRange {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) offset: u64,
    pub(in crate::render::worldrender) bytes: u64,
}

pub(in crate::render::worldrender) struct SourceGeometryPage {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) capacity: u64,
    pub(in crate::render::worldrender) free_ranges: Vec<(u64, u64)>,
}

/// Completion-gated suballocator for source terrain geometry pages. A range
/// is reused only after every submission that could read it has completed.
#[derive(Default)]
pub(in crate::render::worldrender) struct SourceTerrainGeometryPages {
    pub(in crate::render::worldrender) vertex_pages: Vec<SourceGeometryPage>,
    pub(in crate::render::worldrender) index_pages: Vec<SourceGeometryPage>,
    pub(in crate::render::worldrender) pending_releases: Vec<(SubmissionId, SourceGeometryRange)>,
}

impl SourceTerrainGeometryPages {
    pub(in crate::render::worldrender) fn allocate(
        gal: &mut VulkanicGal,
        pages: &mut Vec<SourceGeometryPage>,
        bytes: u64,
        usage: BufferUsage,
        kind: &str,
        device_local: bool,
    ) -> GalResult<SourceGeometryRange> {
        let bytes = align_up_u64(bytes.max(1), SOURCE_TERRAIN_GEOMETRY_ALIGNMENT)?;
        for page in pages.iter_mut() {
            if let Some(index) = page.free_ranges.iter().position(|(_, length)| *length >= bytes) {
                let (offset, length) = page.free_ranges[index];
                if length == bytes {
                    page.free_ranges.remove(index);
                } else {
                    page.free_ranges[index] = (offset + bytes, length - bytes);
                }
                return Ok(SourceGeometryRange { buffer: page.buffer, offset, bytes });
            }
        }
        let capacity = SOURCE_TERRAIN_GEOMETRY_PAGE_BYTES.max(bytes);
        let buffer = gal.create_buffer(BufferDesc {
            label: format!("source-terrain-geometry-page.{kind}.{}", pages.len()),
            size: capacity,
            memory: if device_local {
                MemoryDomain::DeviceLocal
            } else {
                MemoryDomain::Upload
            },
            usages: vec![
                usage,
                if device_local {
                    BufferUsage::TransferDst
                } else {
                    BufferUsage::HostWrite
                },
            ],
        })?;
        let mut free_ranges = Vec::new();
        if capacity > bytes {
            free_ranges.push((bytes, capacity - bytes));
        }
        pages.push(SourceGeometryPage { buffer, capacity, free_ranges });
        Ok(SourceGeometryRange { buffer, offset: 0, bytes })
    }

    pub(in crate::render::worldrender) fn allocate_vertex(
        &mut self,
        gal: &mut VulkanicGal,
        bytes: u64,
        device_local: bool,
    ) -> GalResult<SourceGeometryRange> {
        Self::allocate(gal, &mut self.vertex_pages, bytes, BufferUsage::Storage, "vertices", device_local)
    }

    pub(in crate::render::worldrender) fn allocate_index(
        &mut self,
        gal: &mut VulkanicGal,
        bytes: u64,
        device_local: bool,
    ) -> GalResult<SourceGeometryRange> {
        Self::allocate(gal, &mut self.index_pages, bytes, BufferUsage::Index, "indices", device_local)
    }

    pub(in crate::render::worldrender) fn release_now(&mut self, range: SourceGeometryRange) {
        let Some(page) = self
            .vertex_pages
            .iter_mut()
            .chain(self.index_pages.iter_mut())
            .find(|page| page.buffer == range.buffer)
        else {
            return;
        };
        let position = page
            .free_ranges
            .iter()
            .position(|(offset, _)| *offset > range.offset)
            .unwrap_or(page.free_ranges.len());
        page.free_ranges.insert(position, (range.offset, range.bytes));
        // Coalesce with the following and preceding free neighbours.
        if position + 1 < page.free_ranges.len() {
            let (offset, length) = page.free_ranges[position];
            let (next_offset, next_length) = page.free_ranges[position + 1];
            if offset + length == next_offset {
                page.free_ranges[position].1 = length + next_length;
                page.free_ranges.remove(position + 1);
            }
        }
        if position > 0 {
            let (previous_offset, previous_length) = page.free_ranges[position - 1];
            let (offset, length) = page.free_ranges[position];
            if previous_offset + previous_length == offset {
                page.free_ranges[position - 1].1 = previous_length + length;
                page.free_ranges.remove(position);
            }
        }
    }

    pub(in crate::render::worldrender) fn release_after(&mut self, submission: SubmissionId, range: SourceGeometryRange) {
        self.pending_releases.push((submission, range));
    }

    /// Returns completed ranges to their pages and removes pages that became
    /// entirely free. The caller destroys the returned page buffers after the
    /// sets that bind them.
    pub(in crate::render::worldrender) fn reclaim(&mut self, gal: &mut VulkanicGal) -> Vec<Handle> {
        if self.pending_releases.is_empty() {
            return Vec::new();
        }
        let completed = gal.poll_completed();
        let mut retained = Vec::new();
        for (submission, range) in std::mem::take(&mut self.pending_releases) {
            if submission <= completed {
                self.release_now(range);
            } else {
                retained.push((submission, range));
            }
        }
        self.pending_releases = retained;
        let mut emptied = Vec::new();
        for pages in [&mut self.vertex_pages, &mut self.index_pages] {
            pages.retain(|page| {
                let empty =
                    page.free_ranges.len() == 1 && page.free_ranges[0] == (0, page.capacity);
                if empty {
                    emptied.push(page.buffer);
                }
                !empty
            });
        }
        emptied
    }

    pub(in crate::render::worldrender) fn destroy_all(&mut self, gal: &mut VulkanicGal) {
        for page in self.vertex_pages.drain(..).chain(self.index_pages.drain(..)) {
            let _ = gal.destroy(page.buffer);
        }
        self.pending_releases.clear();
    }
}

#[derive(Clone, Copy, Debug)]
pub(in crate::render::worldrender) struct MeshInstanceStreamSlot {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) capacity: u64,
}

#[derive(Clone, Copy, Debug)]
pub(in crate::render::worldrender) struct MeshInstanceStreamBinding {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) capacity: u64,
    pub(in crate::render::worldrender) grew: bool,
}

#[derive(Clone, Copy, Debug)]
pub(in crate::render::worldrender) struct MeshIndirectStreamSlot {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) capacity: u64,
    pub(in crate::render::worldrender) initialized: bool,
}

#[derive(Clone, Copy, Debug)]
pub(in crate::render::worldrender) struct SourceTerrainFrameStreamSlot {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) capacity: u64,
    pub(in crate::render::worldrender) frame_id: Option<u64>,
    pub(in crate::render::worldrender) epoch: u64,
    pub(in crate::render::worldrender) cursor: u64,
    pub(in crate::render::worldrender) submission: Option<SubmissionId>,
    /// Indexed-indirect commands of multi-drawn terrain for this slot's frame.
    pub(in crate::render::worldrender) indirect_buffer: Option<Handle>,
    pub(in crate::render::worldrender) indirect_capacity: u64,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct SourceTerrainFrameStreamAllocation {
    pub(in crate::render::worldrender) buffer: Handle,
    pub(in crate::render::worldrender) epoch: u64,
    pub(in crate::render::worldrender) legacy_transform_offset: u64,
    pub(in crate::render::worldrender) scalar_uniform_offset: Option<u64>,
    pub(in crate::render::worldrender) instance_offset: u64,
}

impl MeshGeometryArena {
    pub(in crate::render::worldrender) fn vertex_page_capacity(&self, buffer: Handle) -> Option<u64> {
        self.vertex_pages
            .iter()
            .find(|page| page.buffer == buffer)
            .map(|page| page.capacity)
    }

    pub(in crate::render::worldrender) fn reclaim_completed(&mut self, gal: &mut VulkanicGal) {
        let completed = gal.poll_completed();
        let mut retained = Vec::new();
        for (submission, resources) in std::mem::take(&mut self.pending_releases) {
            if submission <= completed {
                self.release_vertex(
                    resources.vertex_buffer,
                    resources.vertex_offset,
                    resources.vertex_range,
                );
                self.release_index(
                    resources.index_buffer,
                    resources.index_offset,
                    resources.index_range,
                );
            } else {
                retained.push((submission, resources));
            }
        }
        self.pending_releases = retained;
        // A completed generation can leave an entire 16 MiB arena page empty.
        // Return such pages to the GAL immediately; keeping an empty page
        // resident defeats the completion-gated retirement contract and makes
        // streamed terrain growth accumulate native upload memory forever.
        self.trim_empty_pages(gal);
    }

    pub(in crate::render::worldrender) fn defer_release(&mut self, submission: SubmissionId, resources: MeshGeometryResources) {
        self.pending_releases.push((submission, resources));
    }

    pub(in crate::render::worldrender) fn allocate_vertex(
        &mut self,
        gal: &mut VulkanicGal,
        label: &str,
        bytes: u64,
    ) -> GalResult<(Handle, u64)> {
        let device_local = gal.capabilities().supports(BackendFeature::DeviceLocalMemory);
        Self::allocate_in_pages(
            gal,
            &mut self.vertex_pages,
            label,
            bytes,
            BufferUsage::Storage,
            "vertices",
            device_local,
        )
    }

    pub(in crate::render::worldrender) fn allocate_index(
        &mut self,
        gal: &mut VulkanicGal,
        label: &str,
        bytes: u64,
    ) -> GalResult<(Handle, u64)> {
        Self::allocate_in_pages(
            gal,
            &mut self.index_pages,
            label,
            bytes,
            BufferUsage::Index,
            "indices",
            false,
        )
    }

    pub(in crate::render::worldrender) fn allocate_in_pages(
        gal: &mut VulkanicGal,
        pages: &mut Vec<MeshGeometryArenaPage>,
        label: &str,
        bytes: u64,
        usage: BufferUsage,
        kind: &str,
        device_local: bool,
    ) -> GalResult<(Handle, u64)> {
        let bytes = align_up_multiple_u64(bytes, WORLD_MESH_GEOMETRY_ALIGNMENT)?;
        if bytes == 0 {
            return Err(GalError::invalid_argument(
                "world mesh geometry range cannot be empty",
            ));
        }
        if let Some((page, range_index)) = pages.iter_mut().find_map(|page| {
            page.free_ranges
                .iter()
                .position(|(_, length)| *length >= bytes)
                .map(|index| (page, index))
        }) {
            let (offset, length) = page.free_ranges[range_index];
            if length == bytes {
                page.free_ranges.remove(range_index);
            } else {
                page.free_ranges[range_index] = (offset + bytes, length - bytes);
            }
            return Ok((page.buffer, offset));
        }
        let capacity = WORLD_MESH_GEOMETRY_PAGE_BYTES.max(bytes);
        let buffer = gal.create_buffer(BufferDesc {
            label: format!("world-mesh.geometry-arena.{kind}.{}", pages.len()),
            size: capacity,
            memory: if device_local {
                MemoryDomain::DeviceLocal
            } else {
                MemoryDomain::Upload
            },
            usages: if device_local {
                vec![usage, BufferUsage::TransferDst]
            } else {
                vec![usage, BufferUsage::HostWrite]
            },
        })?;
        let staging_buffer = if device_local {
            match gal.create_buffer(BufferDesc {
                label: format!("world-mesh.geometry-arena.{kind}.{}.staging", pages.len()),
                size: capacity,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            }) {
                Ok(staging) => Some(staging),
                Err(error) => {
                    let _ = gal.destroy(buffer);
                    return Err(error);
                }
            }
        } else {
            None
        };
        let mut page = MeshGeometryArenaPage {
            buffer,
            staging_buffer,
            upload_initialized: false,
            capacity,
            free_ranges: Vec::new(),
        };
        if capacity > bytes {
            page.free_ranges.push((bytes, capacity - bytes));
        }
        pages.push(page);
        let _ = label;
        Ok((buffer, 0))
    }

    pub(in crate::render::worldrender) fn vertex_upload_page(&self, buffer: Handle) -> Option<(Handle, bool)> {
        self.vertex_pages
            .iter()
            .find(|page| page.buffer == buffer)
            .and_then(|page| page.staging_buffer.map(|staging| (staging, page.upload_initialized)))
    }

    pub(in crate::render::worldrender) fn mark_vertex_uploaded(&mut self, buffer: Handle) {
        if let Some(page) = self.vertex_pages.iter_mut().find(|page| page.buffer == buffer) {
            page.upload_initialized = true;
        }
    }

    pub(in crate::render::worldrender) fn release_vertex(&mut self, buffer: Handle, offset: u64, bytes: u64) {
        Self::release_in_pages(&mut self.vertex_pages, buffer, offset, bytes);
    }

    pub(in crate::render::worldrender) fn release_index(&mut self, buffer: Handle, offset: u64, bytes: u64) {
        Self::release_in_pages(&mut self.index_pages, buffer, offset, bytes);
    }

    pub(in crate::render::worldrender) fn release_in_pages(
        pages: &mut [MeshGeometryArenaPage],
        buffer: Handle,
        offset: u64,
        bytes: u64,
    ) {
        let Some(page) = pages.iter_mut().find(|page| page.buffer == buffer) else {
            return;
        };
        let bytes = align_up_multiple_u64(bytes, WORLD_MESH_GEOMETRY_ALIGNMENT)
            .expect("geometry range was validated on allocation");
        debug_assert!(offset
            .checked_add(bytes)
            .is_some_and(|end| end <= page.capacity));
        page.free_ranges.push((offset, bytes));
        page.free_ranges.sort_unstable_by_key(|(start, _)| *start);
        let mut merged = Vec::with_capacity(page.free_ranges.len());
        for (start, length) in page.free_ranges.drain(..) {
            if let Some((previous_start, previous_length)) = merged.last_mut() {
                if *previous_start + *previous_length == start {
                    *previous_length += length;
                    continue;
                }
            }
            merged.push((start, length));
        }
        page.free_ranges = merged;
    }

    pub(in crate::render::worldrender) fn trim_empty_pages(&mut self, gal: &mut VulkanicGal) {
        fn trim(pages: &mut Vec<MeshGeometryArenaPage>, gal: &mut VulkanicGal) {
            let mut retained = Vec::with_capacity(pages.len());
            for page in pages.drain(..) {
                let empty =
                    page.free_ranges.len() == 1 && page.free_ranges[0] == (0, page.capacity);
                if empty {
                    // The range was released only after its protecting
                    // submission completed. GAL still owns the final
                    // completion check and may defer destruction if another
                    // explicit dependency references the page.
                    let _ = gal.destroy(page.buffer);
                    if let Some(staging) = page.staging_buffer {
                        let _ = gal.destroy(staging);
                    }
                } else {
                    retained.push(page);
                }
            }
            *pages = retained;
        }

        trim(&mut self.vertex_pages, gal);
        trim(&mut self.index_pages, gal);
    }

    pub(in crate::render::worldrender) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.pending_releases.clear();
        for page in self
            .vertex_pages
            .drain(..)
            .chain(self.index_pages.drain(..))
        {
            let _ = gal.destroy(page.buffer);
            if let Some(staging) = page.staging_buffer {
                let _ = gal.destroy(staging);
            }
        }
    }
}

impl WorldPrimitiveFrontend {
    pub(in crate::render::worldrender) fn submit_or_queue_world_upload(
        &mut self,
        gal: &mut VulkanicGal,
        label: &str,
        operations: Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.defer_world_uploads {
            self.pending_world_upload_ops.extend(operations);
            return Ok(());
        }
        gal.submit(SubmissionBatch {
            label: label.to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: format!("{label}.commands"),
                operations,
            })],
        })?;
        Ok(())
    }

    pub(in crate::render::worldrender) fn flush_pending_world_uploads(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        let operations = self.pending_world_upload_ops.clone();
        if operations.is_empty() {
            return Ok(());
        }
        gal.submit(SubmissionBatch {
            label: "world-resource-uploads.batch".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "world-resource-uploads.batch.commands".to_string(),
                operations,
            })],
        })?;
        self.pending_world_upload_ops.clear();
        Ok(())
    }

    pub(in crate::render::worldrender) fn ensure_mesh_geometry_resources(
        &mut self,
        gal: &mut VulkanicGal,
        key: MeshGeometryResourceKey,
        vertex_bytes: Vec<u8>,
        index_bytes: Vec<u8>,
        capture_layered_geometry: bool,
    ) -> GalResult<()> {
        if self.mesh_geometry_resources.contains_key(&key) {
            return Ok(());
        }
        if self.mesh_geometry_resources.len() >= WORLD_MESH_GEOMETRY_RESIDENCY {
            return Err(GalError::backend(format!(
                "world mesh geometry residency exceeds bounded limit {WORLD_MESH_GEOMETRY_RESIDENCY}"
            )));
        }
        let label = format!(
            "world-mesh-geometry-{}-gen{}",
            key.mesh_key, key.mesh_generation
        );
        // Page reclamation belongs to the explicit post-submission retirement
        // boundary. Destroying page-wide descriptor sets here invalidates sets
        // already recorded for an earlier batch while this same frame is still
        // being assembled, allowing their handle slots to be reused before
        // GAL validates the completed command list.
        let result = (|| -> GalResult<MeshGeometryResources> {
            let vertex_range = vertex_bytes.len() as u64;
            let index_range = index_bytes.len() as u64;
            let (vertex_buffer, vertex_offset) =
                self.mesh_geometry_arena
                    .allocate_vertex(gal, &label, vertex_range)?;
            let (index_buffer, index_offset) =
                match self
                    .mesh_geometry_arena
                    .allocate_index(gal, &label, index_range)
                {
                    Ok(allocation) => allocation,
                    Err(error) => {
                        self.mesh_geometry_arena.release_vertex(
                            vertex_buffer,
                            vertex_offset,
                            vertex_range,
                        );
                        return Err(error);
                    }
                };
            let resources = MeshGeometryResources {
                vertex_buffer,
                vertex_offset,
                vertex_range,
                vertex_stride: key.vertex_abi.stride(),
                index_buffer,
                index_offset,
                index_range,
            };
            if (capture_layered_geometry && layered_geometry_capture_configured())
                || (crate::core::environment::var_os("MATTMC_GRAPHICS_AUDIT").is_some() && self
                    .mesh_assets
                    .get(&key.mesh_key)
                    .is_some_and(|asset| asset.entity_identity == "minecraft:wolf"))
            {
                // Diagnostic exhaustion must not alter resource creation or rendering.
                // Missing watches cause capture verification to reject the evidence.
                let _ = gal.watch_buffer_upload_for_capture(
                    vertex_buffer,
                    vertex_offset,
                    vertex_range as usize,
                );
                let _ = gal.watch_buffer_upload_for_capture(
                    index_buffer,
                    index_offset,
                    index_range as usize,
                );
            }
            if let Err(error) =
                self.upload_mesh_geometry_resources(gal, &resources, vertex_bytes, index_bytes)
            {
                gal.unwatch_buffer_upload_for_capture(
                    vertex_buffer,
                    vertex_offset,
                    vertex_range as usize,
                );
                gal.unwatch_buffer_upload_for_capture(
                    index_buffer,
                    index_offset,
                    index_range as usize,
                );
                // The arena owns ranges independently of the resource map.  An
                // upload failure must therefore return both allocations
                // immediately: neither range has been submitted for GPU use.
                self.mesh_geometry_arena.release_vertex(
                    resources.vertex_buffer,
                    resources.vertex_offset,
                    resources.vertex_range,
                );
                self.mesh_geometry_arena.release_index(
                    resources.index_buffer,
                    resources.index_offset,
                    resources.index_range,
                );
                return Err(error);
            }
            Ok(resources)
        })();
        self.mesh_geometry_resources.insert(key, result?);
        Ok(())
    }

    pub(in crate::render::worldrender) fn ensure_mesh_instance_stream(
        &mut self,
        gal: &mut VulkanicGal,
        required_bytes: u64,
    ) -> GalResult<MeshInstanceStreamBinding> {
        let required_capacity = required_bytes
            .checked_add(WORLD_MESH_INSTANCE_STREAM_BINDING_RANGE_BYTES)
            .ok_or_else(|| GalError::invalid_argument("world mesh stream capacity overflow"))?
            .max(WORLD_MESH_INSTANCE_BUFFER_BYTES);
        if let Some(slot) = self
            .mesh_instance_stream_slots
            .iter()
            .rev()
            .copied()
            .find(|slot| slot.capacity >= required_capacity)
        {
            return Ok(MeshInstanceStreamBinding {
                buffer: slot.buffer,
                capacity: slot.capacity,
                grew: false,
            });
        }
        // Growing rebinds every mesh resource set to the new buffer. Growing to
        // the exact requirement made streaming terrain regrow every few frames,
        // each time rebuilding hundreds of resource sets (~30 ms frames). Grow
        // geometrically so a session regrows only logarithmically often.
        let previous_capacity = self
            .mesh_instance_stream_slots
            .last()
            .map_or(0, |slot| slot.capacity);
        let capacity = align_up_u64(
            required_capacity.max(previous_capacity.saturating_mul(2)),
            WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64,
        )?;
        let buffer = gal.create_buffer(BufferDesc {
            label: "world-mesh.shared-instance-stream".to_string(),
            size: capacity,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::Storage,
                BufferUsage::TransferDst,
                BufferUsage::HostWrite,
            ],
        })?;
        // A larger stream supersedes every smaller stream.  Resource sets are
        // rebound by the caller immediately after this returns, while GAL
        // destruction remains submission-aware for any in-flight frame that
        // still references an older buffer.  Retaining every historical
        // capacity made repeated terrain visibility growth accumulate native
        // allocations even though only the newest stream could be selected.
        let previous_slots = std::mem::take(&mut self.mesh_instance_stream_slots);
        self.deferred_mesh_stream_buffer_destroys
            .extend(previous_slots.into_iter().map(|slot| slot.buffer));
        self.mesh_instance_stream_slots
            .push(MeshInstanceStreamSlot { buffer, capacity });
        Ok(MeshInstanceStreamBinding {
            buffer,
            capacity,
            grew: true,
        })
    }

    /// The instance stream is a persistent, generation-bounded allocation. Its
    /// native buffer is part of every mesh resource-set binding, so growing it
    /// retires old stream buffers through GAL and lets the existing immutable
    /// geometry, material, and pipeline caches rebuild against the replacement.
    pub(in crate::render::worldrender) fn invalidate_mesh_instance_stream_bindings(&mut self, _gal: &mut VulkanicGal) {
        for (_, set) in std::mem::take(&mut self.mesh_page_resource_sets) {
            self.deferred_mesh_resource_destroys.push(set);
        }
        for (_, resources) in std::mem::take(&mut self.mesh_resources) {
            self.deferred_mesh_resource_destroys
                .push(resources.resource_set);
        }
        for (_, resources) in std::mem::take(&mut self.source_mesh_resources) {
            self.deferred_mesh_resource_destroys
                .push(resources.resource_set);
        }
    }

    pub(in crate::render::worldrender) fn destroy_mesh_page_resource_sets(&mut self, gal: &mut VulkanicGal) {
        for resources in self.mesh_resources.values_mut() {
            resources.page_resource_set = None;
        }
        for (_, set) in std::mem::take(&mut self.mesh_page_resource_sets) {
            let _ = gal.destroy(set);
        }
    }

    pub(in crate::render::worldrender) fn ensure_mesh_page_resource_set(
        &mut self,
        gal: &mut VulkanicGal,
        mesh_key: MeshResourceKey,
        stream: MeshInstanceStreamBinding,
        lightmap_layout: Handle,
    ) -> GalResult<Handle> {
        let mesh = self
            .mesh_resources
            .get(&mesh_key)
            .ok_or_else(|| GalError::backend("world mesh resources missing before page binding"))?;
        if let Some(set) = mesh.page_resource_set {
            return Ok(set);
        }
        let vertex_buffer = mesh.vertex_buffer;
        let mut pipeline_key = mesh_pipeline_key(mesh_key)?;
        pipeline_key.shader_resource_layout = Some(lightmap_layout);
        let pipeline = self
            .mesh_pipeline_resources
            .get(&pipeline_key)
            .ok_or_else(|| GalError::backend("world mesh pipeline missing before page binding"))?;
        if pipeline.vertex_observation.is_some() {
            return Err(GalError::unsupported_feature(
                "page-wide indexed draws are disabled while vertex observation is active",
            ));
        }
        let texture = self
            .mesh_texture_resources
            .get(&mesh_key.texture_id)
            .ok_or_else(|| GalError::backend("world mesh texture missing before page binding"))?;
        let key = MeshPageResourceSetKey {
            resource_layout: pipeline.resource_layout,
            vertex_buffer,
            instance_buffer: stream.buffer,
            texture_view: texture.view,
            sampler: texture.sampler,
            observation_buffer: None,
        };
        if let Some(set) = self.mesh_page_resource_sets.get(&key) {
            self.mesh_resources
                .get_mut(&mesh_key)
                .expect("world mesh resource still exists")
                .page_resource_set = Some(*set);
            return Ok(*set);
        }
        let vertex_range = self
            .mesh_geometry_arena
            .vertex_page_capacity(vertex_buffer)
            .ok_or_else(|| GalError::backend("world mesh vertex page vanished before binding"))?;
        let set = create_mesh_resource_set(
            gal,
            "world-mesh.page",
            key.resource_layout,
            vertex_buffer,
            vertex_range,
            stream.buffer,
            stream.capacity,
            key.texture_view,
            key.sampler,
            false,
            None,
        )?;
        self.mesh_page_resource_sets.insert(key, set);
        self.mesh_resources
            .get_mut(&mesh_key)
            .expect("world mesh resource still exists")
            .page_resource_set = Some(set);
        Ok(set)
    }

    pub(in crate::render::worldrender) fn ensure_mesh_indirect_stream(
        &mut self,
        gal: &mut VulkanicGal,
        required_bytes: u64,
    ) -> GalResult<MeshIndirectStreamSlot> {
        if let Some(slot) = self.mesh_indirect_stream {
            if slot.capacity >= required_bytes {
                return Ok(slot);
            }
        }
        let capacity = align_up_u64(required_bytes.max(4_096), 256)?;
        let buffer = gal.create_buffer(BufferDesc {
            label: "world-mesh.indexed-indirect-stream".to_string(),
            size: capacity,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::Indirect,
                BufferUsage::HostWrite,
                BufferUsage::TransferDst,
            ],
        })?;
        if let Some(previous) = self.mesh_indirect_stream.replace(MeshIndirectStreamSlot {
            buffer,
            capacity,
            initialized: false,
        }) {
            self.deferred_mesh_stream_buffer_destroys
                .push(previous.buffer);
        }
        Ok(self
            .mesh_indirect_stream
            .expect("installed indirect stream"))
    }

    pub(in crate::render::worldrender) fn ensure_mesh_sorted_index_stream(
        &mut self,
        gal: &mut VulkanicGal,
        required_bytes: u64,
    ) -> GalResult<MeshIndirectStreamSlot> {
        if let Some(slot) = self.mesh_sorted_index_stream {
            if slot.capacity >= required_bytes {
                return Ok(slot);
            }
        }
        let capacity = align_up_u64(required_bytes.max(4_096), 256)?;
        let buffer = gal.create_buffer(BufferDesc {
            label: "world-mesh.camera-sorted-index-stream".to_string(),
            size: capacity,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::Index,
                BufferUsage::HostWrite,
                BufferUsage::TransferDst,
            ],
        })?;
        if let Some(previous) = self
            .mesh_sorted_index_stream
            .replace(MeshIndirectStreamSlot {
                buffer,
                capacity,
                initialized: false,
            })
        {
            self.deferred_mesh_stream_buffer_destroys
                .push(previous.buffer);
        }
        Ok(self
            .mesh_sorted_index_stream
            .expect("installed sorted index stream"))
    }

    /// Reserves one bounded source-program frame-data range. The allocation is
    /// keyed by the semantic render frame and may be reused only after the
    /// caller marks its submission and GAL reports that submission complete.
    /// This is private scaffolding until lowered source execution is admitted.
    pub(in crate::render::worldrender) fn allocate_source_terrain_frame_stream(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        legacy_bytes: u64,
        scalar_bytes: u64,
        instance_bytes: u64,
    ) -> GalResult<SourceTerrainFrameStreamAllocation> {
        self.allocate_source_terrain_frame_stream_sharing_uniforms(
            gal,
            frame_id,
            legacy_bytes,
            scalar_bytes,
            instance_bytes,
            None,
        )
    }

    /// `shared_uniforms` names this frame's already-staged (legacy, scalar)
    /// offsets for identical uniform bytes; only instance data is allocated.
    pub(in crate::render::worldrender) fn allocate_source_terrain_frame_stream_sharing_uniforms(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        legacy_bytes: u64,
        scalar_bytes: u64,
        instance_bytes: u64,
        shared_uniforms: Option<(u64, Option<u64>)>,
    ) -> GalResult<SourceTerrainFrameStreamAllocation> {
        self.allocate_source_terrain_frame_stream_aligned(
            gal,
            frame_id,
            legacy_bytes,
            scalar_bytes,
            instance_bytes,
            shared_uniforms,
            WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64,
        )
    }

    /// As above, with the instance block starting at a multiple of
    /// `instance_alignment` from the start of the slot.
    #[allow(clippy::too_many_arguments)]
    pub(in crate::render::worldrender) fn allocate_source_terrain_frame_stream_aligned(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        legacy_bytes: u64,
        scalar_bytes: u64,
        instance_bytes: u64,
        shared_uniforms: Option<(u64, Option<u64>)>,
        instance_alignment: u64,
    ) -> GalResult<SourceTerrainFrameStreamAllocation> {
        // A uniform-only allocation (no instance block) stages a pass's
        // shared blocks; an instance-only one reuses already staged blocks.
        if legacy_bytes == 0 || (instance_bytes == 0 && shared_uniforms.is_some()) {
            return Err(GalError::invalid_argument(
                "source terrain frame stream requires non-empty legacy and instance payloads",
            ));
        }
        if shared_uniforms.is_some_and(|(_, scalar)| scalar.is_some() != (scalar_bytes != 0)) {
            return Err(GalError::invalid_argument(
                "shared source uniforms do not match the requested scalar layout",
            ));
        }
        let align = WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64;
        if instance_alignment == 0 {
            return Err(GalError::invalid_argument(
                "source terrain instance alignment must be non-zero",
            ));
        }
        // Compute absolute offsets independently: a record-aligned batch base
        // is not necessarily aligned for its uniform descriptors. Shared
        // uniforms need no new descriptor range, only densely packed records.
        let layout = |cursor: u64| -> GalResult<(u64, Option<u64>, u64, u64)> {
            let (legacy, scalar, instance_base) = match shared_uniforms {
                Some((legacy, scalar)) => (legacy, scalar, cursor),
                None => {
                    let legacy = align_up_u64(cursor, align)?;
                    let legacy_end = legacy.checked_add(legacy_bytes).ok_or_else(|| {
                        GalError::invalid_argument("source terrain legacy uniform range overflow")
                    })?;
                    let scalar = if scalar_bytes != 0 {
                        Some(align_up_u64(legacy_end, align)?)
                    } else {
                        None
                    };
                    let end = scalar
                        .unwrap_or(legacy_end)
                        .checked_add(scalar_bytes)
                        .ok_or_else(|| {
                            GalError::invalid_argument("source terrain scalar uniform range overflow")
                        })?;
                    (legacy, scalar, end)
                }
            };
            let instance = align_up_multiple_u64(instance_base, instance_alignment)?;
            let end = instance.checked_add(instance_bytes).ok_or_else(|| {
                GalError::invalid_argument("source terrain frame stream range overflow")
            })?;
            Ok((legacy, scalar, instance, end))
        };
        let required = layout(0)?.3;
        let required_capacity =
            align_up_u64(required, align)?.max(SOURCE_TERRAIN_FRAME_STREAM_MIN_BYTES);
        if required_capacity > LOWERED_SOURCE_FRAME_STREAM_MAX_BYTES {
            return Err(GalError::unsupported_feature(format!(
                "lowered source frame stream exceeds bounded slot capacity (requested={} limit={})",
                required_capacity, LOWERED_SOURCE_FRAME_STREAM_MAX_BYTES
            )));
        }
        let existing = self
            .source_terrain_frame_stream_slots
            .iter()
            .position(|slot| slot.frame_id == Some(frame_id));
        let slot_index = if let Some(index) = existing {
            index
        } else if let Some(index) = self
            .source_terrain_frame_stream_slots
            .iter()
            .position(|slot| slot.frame_id.is_none())
        {
            index
        } else if let Some(index) = {
            let completed = gal.poll_completed();
            self.source_terrain_frame_stream_slots.iter().position(|slot| {
                slot.submission
                    .is_some_and(|submission| submission <= completed)
            })
        } {
            index
        } else if self.source_terrain_frame_stream_slots.len()
            < SOURCE_TERRAIN_FRAME_STREAM_SLOT_COUNT
        {
            let buffer = gal.create_buffer(BufferDesc {
                label: format!(
                    "source-terrain-frame-stream-slot{}",
                    self.source_terrain_frame_stream_slots.len()
                ),
                size: required_capacity,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::Storage,
                    BufferUsage::HostWrite,
                ],
            })?;
            self.source_terrain_frame_stream_slots
                .push(SourceTerrainFrameStreamSlot {
                    buffer,
                    capacity: required_capacity,
                    frame_id: None,
                    epoch: 0,
                    cursor: 0,
                    submission: None,
                    indirect_buffer: None,
                    indirect_capacity: 0,
                });
            self.source_terrain_frame_stream_slots.len() - 1
        } else if let Some((index, oldest)) = self
            .source_terrain_frame_stream_slots
            .iter()
            .enumerate()
            .filter_map(|(index, slot)| slot.submission.map(|submission| (index, submission)))
            .min_by_key(|(_, submission)| *submission)
        {
            // Every slot belongs to an in-flight frame (a slow GPU frame, e.g.
            // while chunks stream in after a teleport). Bounded, explicit
            // backpressure: wait for exactly the oldest slot's submission
            // instead of failing this frame back to the vanilla route.
            gal.retire_through(oldest)?;
            index
        } else {
            return Err(GalError::unsupported_feature(
                "source terrain frame stream has no completed slot available",
            ));
        };
        let previous_frame_id = self.source_terrain_frame_stream_slots[slot_index].frame_id;
        let same_frame = previous_frame_id == Some(frame_id);
        if shared_uniforms.is_some() && !same_frame {
            return Err(GalError::invalid_argument(
                "shared source uniforms must come from the same frame stream slot",
            ));
        }
        if !same_frame {
            if let Some(previous_frame_id) = previous_frame_id {
                if self
                    .pending_source_terrain_frame_transactions
                    .contains_key(&previous_frame_id)
                {
                    return Err(GalError::invalid_argument(
                        "source terrain frame stream cannot recycle a frame whose upload transaction was not consumed",
                    ));
                }
            }
        }
        if same_frame
            && self.source_terrain_frame_stream_slots[slot_index]
                .submission
                .is_some()
        {
            return Err(GalError::invalid_argument(
                "source terrain frame stream cannot allocate after the frame was submitted",
            ));
        }
        if !same_frame
            && required_capacity > self.source_terrain_frame_stream_slots[slot_index].capacity
        {
            // Grow geometrically. Replacing a slot destroys every resource set
            // bound to its buffer; growing to the exact size made a streaming
            // world rebuild ~1,500 sets per frame as its payload crept upward.
            let grown_capacity = align_up_u64(
                required_capacity
                    .max(self.source_terrain_frame_stream_slots[slot_index].capacity.saturating_mul(2)),
                align,
            )?
            .min(LOWERED_SOURCE_FRAME_STREAM_MAX_BYTES)
            .max(required_capacity);
            let old = self.source_terrain_frame_stream_slots[slot_index].buffer;
            let buffer = gal.create_buffer(BufferDesc {
                label: format!("source-terrain-frame-stream-slot{slot_index}"),
                size: grown_capacity,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::Storage,
                    BufferUsage::HostWrite,
                ],
            })?;
            self.destroy_lowered_source_terrain_frame_data_resources_for_stream_buffer(gal, old);
            let _ = gal.destroy(old);
            let slot = &mut self.source_terrain_frame_stream_slots[slot_index];
            slot.buffer = buffer;
            slot.capacity = grown_capacity;
        }
        let slot = &mut self.source_terrain_frame_stream_slots[slot_index];
        if !same_frame {
            slot.frame_id = Some(frame_id);
            slot.epoch = slot.epoch.checked_add(1).ok_or_else(|| {
                GalError::invalid_argument("source terrain frame stream slot epoch overflow")
            })?;
            slot.cursor = 0;
            slot.submission = None;
        }
        let (legacy_transform_offset, scalar_uniform_offset, instance_offset, allocation_end) =
            layout(slot.cursor)?;
        if allocation_end > slot.capacity {
            return Err(GalError::invalid_argument(format!(
                "source terrain frame stream allocation exceeds its bounded slot capacity (frame={frame_id} cursor={} end={allocation_end} capacity={})",
                slot.cursor, slot.capacity,
            )));
        }
        slot.cursor = allocation_end;
        Ok(SourceTerrainFrameStreamAllocation {
            buffer: slot.buffer,
            epoch: slot.epoch,
            legacy_transform_offset,
            scalar_uniform_offset,
            instance_offset,
        })
    }

    /// Reserves the exact bounded dynamic payload for one source frame before
    /// per-batch preparation starts. The individual allocations still carry
    /// their real offsets and remain tied to one completion-gated slot; this
    /// only prevents a busy terrain frame from inheriting the first batch's
    /// small capacity.
    pub(in crate::render::worldrender) fn reserve_source_terrain_frame_stream_capacity(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        payload_bytes: u64,
    ) -> GalResult<()> {
        if payload_bytes == 0 {
            return Ok(());
        }
        let _ = self.allocate_source_terrain_frame_stream(gal, frame_id, 1, 0, payload_bytes)?;
        let slot = self
            .source_terrain_frame_stream_slots
            .iter_mut()
            .find(|slot| slot.frame_id == Some(frame_id))
            .ok_or_else(|| {
                GalError::backend("source terrain frame stream reservation lost its allocated slot")
            })?;
        if slot.submission.is_some() {
            return Err(GalError::invalid_argument(
                "source terrain frame stream cannot reserve after the frame was submitted",
            ));
        }
        slot.cursor = 0;
        Ok(())
    }


    pub(in crate::render::worldrender) fn validated_source_terrain_frame_stream_payload_bytes(
        interface: ValidatedSourceInterface<'_>,
        instance_count: u64,
    ) -> GalResult<u64> {
        // Keep the conservative reservation independent of record packing.
        Self::validated_source_frame_stream_payload_bytes(interface, instance_count)?
            .checked_add(SOURCE_TERRAIN_MULTIDRAW_RESERVATION_PADDING)
            .ok_or_else(|| GalError::invalid_argument("source terrain frame stream payload overflows"))
    }

    /// Ensures this frame's stream slot owns an indirect buffer for at least
    /// `command_count` multi-draw commands and arms multi-draw for the frame.
    /// Without this reservation every draw keeps its direct binding path.
    pub(in crate::render::worldrender) fn reserve_source_terrain_multidraw_commands(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        command_count: u64,
    ) -> GalResult<()> {
        self.source_terrain_multidraw_frame = None;
        if command_count == 0
            || !source_terrain_multidraw_enabled()
            || !gal.capabilities().supports(BackendFeature::IndirectDraw)
        {
            return Ok(());
        }
        let required = command_count
            .checked_mul(WORLD_MESH_INDEXED_INDIRECT_COMMAND_BYTES)
            .ok_or_else(|| GalError::invalid_argument("source multi-draw reservation overflows"))?;
        let slot = self
            .source_terrain_frame_stream_slots
            .iter_mut()
            .find(|slot| slot.frame_id == Some(frame_id))
            .ok_or_else(|| {
                GalError::backend("source multi-draw reservation requires a reserved stream slot")
            })?;
        if slot.indirect_buffer.is_none() || slot.indirect_capacity < required {
            let capacity = align_up_u64(required.max(slot.indirect_capacity.saturating_mul(2)).max(16 * 1024), 256)?;
            let buffer = gal.create_buffer(BufferDesc {
                label: "source-terrain-frame-stream.indirect".to_string(),
                size: capacity,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Indirect,
                    BufferUsage::HostWrite,
                    BufferUsage::TransferDst,
                ],
            })?;
            // The slot is only reused after its previous submission completed;
            // the GAL additionally defers destruction of in-flight buffers.
            if let Some(previous) = slot.indirect_buffer.replace(buffer) {
                let _ = gal.destroy(previous);
            }
            slot.indirect_capacity = capacity;
        }
        self.source_terrain_multidraw_frame = Some(frame_id);
        Ok(())
    }

    /// Stages one indexed-indirect command in the frame's slot buffer.
    pub(in crate::render::worldrender) fn append_source_terrain_multidraw_command(
        &mut self,
        frame_id: u64,
        command: PageIndexedDrawCommand,
    ) -> GalResult<TerrainIndexedIndirect> {
        let (buffer, capacity) = self
            .source_terrain_frame_stream_slots
            .iter()
            .find(|slot| slot.frame_id == Some(frame_id))
            .and_then(|slot| slot.indirect_buffer.map(|buffer| (buffer, slot.indirect_capacity)))
            .ok_or_else(|| GalError::backend("source multi-draw command has no reserved buffer"))?;
        let transaction = self
            .pending_source_terrain_frame_transactions
            .get_mut(&frame_id)
            .ok_or_else(|| GalError::backend("source multi-draw command has no frame transaction"))?;
        let offset = transaction.indirect_staging.len() as u64;
        if offset + WORLD_MESH_INDEXED_INDIRECT_COMMAND_BYTES > capacity {
            return Err(GalError::backend("source multi-draw commands exceed their reservation"));
        }
        transaction.indirect_buffer = Some(buffer);
        command.append_bytes(&mut transaction.indirect_staging);
        Ok(TerrainIndexedIndirect { buffer, offset, draw_count: 1 })
    }

    /// Stages a run of consecutive indexed-indirect commands: one draw.
    pub(in crate::render::worldrender) fn append_source_terrain_multidraw_commands(
        &mut self,
        frame_id: u64,
        commands: &[PageIndexedDrawCommand],
    ) -> GalResult<TerrainIndexedIndirect> {
        let draw_count = u32::try_from(commands.len())
            .ok()
            .filter(|count| *count != 0)
            .ok_or_else(|| GalError::invalid_argument("source multi-draw run must hold 1..=u32::MAX commands"))?;
        let (buffer, capacity) = self
            .source_terrain_frame_stream_slots
            .iter()
            .find(|slot| slot.frame_id == Some(frame_id))
            .and_then(|slot| slot.indirect_buffer.map(|buffer| (buffer, slot.indirect_capacity)))
            .ok_or_else(|| GalError::backend("source multi-draw command has no reserved buffer"))?;
        let transaction = self
            .pending_source_terrain_frame_transactions
            .get_mut(&frame_id)
            .ok_or_else(|| GalError::backend("source multi-draw command has no frame transaction"))?;
        let offset = transaction.indirect_staging.len() as u64;
        if offset + u64::from(draw_count) * WORLD_MESH_INDEXED_INDIRECT_COMMAND_BYTES > capacity {
            return Err(GalError::backend("source multi-draw commands exceed their reservation"));
        }
        transaction.indirect_buffer = Some(buffer);
        transaction
            .indirect_staging
            .reserve(commands.len() * WORLD_MESH_INDEXED_INDIRECT_COMMAND_BYTES as usize);
        for command in commands {
            command.append_bytes(&mut transaction.indirect_staging);
        }
        Ok(TerrainIndexedIndirect { buffer, offset, draw_count })
    }





    pub(in crate::render::worldrender) fn validated_source_frame_stream_payload_bytes(
        interface: ValidatedSourceInterface<'_>,
        instance_count: u64,
    ) -> GalResult<u64> {
        let interface = interface.0;
        if instance_count == 0 {
            return Err(GalError::invalid_argument(
                "source terrain frame stream payload requires at least one instance",
            ));
        }
        let align = WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64;
        let legacy_bytes = u64::from(interface.legacy_transform_bytes);
        let scalar_bytes = u64::from(interface.scalar_uniform_bytes);
        let instance_bytes = instance_count
            .checked_mul(u64::from(interface.instance_stride))
            .ok_or_else(|| {
                GalError::invalid_argument("source terrain instance payload overflows")
            })?;
        let scalar_offset = (scalar_bytes != 0).then_some(align_up_u64(legacy_bytes, align)?);
        let instance_base = scalar_offset
            .unwrap_or_else(|| align_up_u64(legacy_bytes, align).expect("validated alignment"));
        let instance_offset = align_up_u64(
            instance_base.checked_add(scalar_bytes).ok_or_else(|| {
                GalError::invalid_argument("source terrain frame stream scalar range overflows")
            })?,
            align,
        )?;
        align_up_u64(
            instance_offset.checked_add(instance_bytes).ok_or_else(|| {
                GalError::invalid_argument("source terrain frame stream payload range overflows")
            })?,
            align,
        )
    }

    pub(in crate::render::worldrender) fn mark_source_terrain_frame_stream_submitted(
        &mut self,
        frame_id: u64,
        stream_buffer: Handle,
        stream_epoch: u64,
        submission: SubmissionId,
    ) -> GalResult<()> {
        if self
            .pending_source_terrain_frame_transactions
            .contains_key(&frame_id)
        {
            return Err(GalError::invalid_argument(
                "source terrain frame stream cannot mark a pending upload transaction submitted",
            ));
        }
        let slot = self
            .source_terrain_frame_stream_slots
            .iter_mut()
            .find(|slot| slot.frame_id == Some(frame_id))
            .ok_or_else(|| {
                GalError::invalid_argument("source terrain frame stream frame was not allocated")
            })?;
        if slot.buffer != stream_buffer || slot.epoch != stream_epoch {
            return Err(GalError::invalid_argument(
                "source terrain frame submission token does not match the allocated stream slot",
            ));
        }
        if slot.submission.is_some() {
            return Err(GalError::invalid_argument(
                "source terrain frame stream frame was already marked submitted",
            ));
        }
        slot.submission = Some(submission);
        Ok(())
    }

    pub(in crate::render::worldrender) fn upload_mesh_geometry_resources(
        &mut self,
        gal: &mut VulkanicGal,
        resources: &MeshGeometryResources,
        vertex_bytes: Vec<u8>,
        index_bytes: Vec<u8>,
    ) -> GalResult<()> {
        let staged_vertex_page = self
            .mesh_geometry_arena
            .vertex_upload_page(resources.vertex_buffer);
        let mut ops = Vec::with_capacity(if staged_vertex_page.is_some() { 8 } else { 4 });
        if let Some((staging, initialized)) = staged_vertex_page {
            if initialized {
                ops.push(CommandOp::Barrier(buffer_barrier(
                    staging,
                    TextureUsageState::TransferSrc,
                    TextureUsageState::TransferDst,
                )));
            }
            ops.push(CommandOp::HostWriteBuffer {
                buffer: staging,
                offset: resources.vertex_offset,
                data: vertex_bytes,
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                staging,
                TextureUsageState::TransferDst,
                TextureUsageState::TransferSrc,
            )));
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.vertex_buffer,
                if initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::CopyBufferRegion {
                src: staging,
                src_offset: resources.vertex_offset,
                dst: resources.vertex_buffer,
                dst_offset: resources.vertex_offset,
                size: resources.vertex_range,
            });
        } else {
            ops.push(CommandOp::HostWriteBuffer {
                buffer: resources.vertex_buffer,
                offset: resources.vertex_offset,
                data: vertex_bytes,
            });
        }
        ops.push(CommandOp::Barrier(buffer_barrier(
            resources.vertex_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::HostWriteBuffer {
            buffer: resources.index_buffer,
            offset: resources.index_offset,
            data: index_bytes,
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            resources.index_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::IndexRead,
        )));
        self.submit_or_queue_world_upload(gal, "world-mesh.upload", ops)?;
        if staged_vertex_page.is_some() {
            self.mesh_geometry_arena
                .mark_vertex_uploaded(resources.vertex_buffer);
        }
        Ok(())
    }

    pub(in crate::render::worldrender) fn apply_mesh_sorted_index_update(
        &mut self,
        gal: &mut VulkanicGal,
        update: WorldMeshSortedIndexUpdate,
    ) -> GalResult<()> {
        let asset = self.mesh_assets.get_mut(&update.mesh_key).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "world mesh sorted index update references unknown mesh key {}",
                update.mesh_key
            ))
        })?;
        if asset.mesh_generation != update.mesh_generation {
            return Err(GalError::invalid_argument(format!(
                "world mesh sorted index update generation {} does not match mesh {} generation {}",
                update.mesh_generation, update.mesh_key, asset.mesh_generation
            )));
        }
        if update.index_generation <= asset.index_generation {
            return Err(GalError::invalid_argument(format!(
                "stale world mesh sorted index generation {} for mesh {}; current is {}",
                update.index_generation, update.mesh_key, asset.index_generation
            )));
        }
        validate_mesh_sorted_index_payload(asset, &update)?;
        *asset.translucent_order.get_mut() = None;
        let old_index_len = asset.index_bytes.len();
        asset.index_generation = update.index_generation;
        asset.index_type = update.index_type;
        asset.index_bytes = update.index_bytes.clone();
        // Source geometry is expanded from the index order (one source quad
        // per original quad), so a resorted mesh must be re-expanded; keeping
        // the old buffers would draw a stale translucent order.
        let mut stale_source_geometry = self
            .lowered_source_terrain_geometry_resources
            .keys()
            .filter(|key| key.mesh_key == update.mesh_key)
            .cloned()
            .collect::<Vec<_>>();
        stale_source_geometry.sort_unstable();
        self.destroy_lowered_source_terrain_resources_for_keys(gal, stale_source_geometry);
        let matching_keys = self
            .mesh_resources
            .keys()
            .copied()
            .filter(|key| key.mesh_key == update.mesh_key)
            .collect::<Vec<_>>();
        if old_index_len != update.index_bytes.len() {
            self.destroy_mesh_resources_for_keys(gal, matching_keys);
            return Ok(());
        }
        // GPU updates were accepted as one transaction before publishing the asset generation.
        Ok(())
    }

    pub(in crate::render::worldrender) fn destroy_mesh_instance_streams(&mut self, gal: &mut VulkanicGal) {
        self.destroy_mesh_page_resource_sets(gal);
        let slots = std::mem::take(&mut self.mesh_instance_stream_slots);
        for slot in slots.into_iter().rev() {
            let _ = gal.destroy(slot.buffer);
        }
        if let Some(slot) = self.mesh_indirect_stream.take() {
            let _ = gal.destroy(slot.buffer);
        }
        if let Some(slot) = self.mesh_sorted_index_stream.take() {
            let _ = gal.destroy(slot.buffer);
        }
    }

    pub(in crate::render::worldrender) fn destroy_source_terrain_frame_streams(&mut self, gal: &mut VulkanicGal) {
        let slots = std::mem::take(&mut self.source_terrain_frame_stream_slots);
        for slot in slots.into_iter().rev() {
            self.destroy_lowered_textured_material_source_frame_data_resources_for_stream_buffer(
                gal,
                slot.buffer,
            );
            self.destroy_lowered_source_terrain_frame_data_resources_for_stream_buffer(
                gal,
                slot.buffer,
            );
            let _ = gal.destroy(slot.buffer);
        }
    }
}

impl WorldPrimitiveFrontend {
    /// Bounded identity-keyed reuse of one semantic batch selection. The key
    /// owns the complete ordered instance identity (mesh generations, flags,
    /// policies), and mesh/texture updates clear or prune the cache, so a hit
    /// is exactly the plan `mesh_batches_selected` would rebuild. Selections
    /// containing camera-sorted instances are never cached (their index order
    /// follows the camera).
    pub(in crate::render::worldrender) fn cached_mesh_batch_plan(
        &mut self,
        frame: &WorldPrimitiveFrame,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
        g_buffer: bool,
        selection: MeshBatchSelection,
        identities: &[MeshBatchInstanceKey],
        terrain_only: bool,
    ) -> GalResult<Arc<Vec<MeshBatch>>> {
        self.cached_mesh_batch_plan_selected(
            frame,
            color_format,
            raster_y_direction,
            g_buffer,
            selection,
            identities,
            terrain_only,
            None,
        )
    }

    pub(in crate::render::worldrender) fn cached_mesh_batch_plan_selected(
        &mut self,
        frame: &WorldPrimitiveFrame,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
        g_buffer: bool,
        selection: MeshBatchSelection,
        identities: &[MeshBatchInstanceKey],
        terrain_only: bool,
        indices: Option<&[usize]>,
    ) -> GalResult<Arc<Vec<MeshBatch>>> {
        if let Some(indices) = indices {
            if indices.len() != identities.len()
                || indices
                    .last()
                    .is_some_and(|index| *index >= frame.mesh_instances.len())
                || indices.windows(2).any(|pair| pair[0] >= pair[1])
            {
                return Err(GalError::invalid_argument(
                    "selected batch key requires ordered, in-bounds instance positions",
                ));
            }
        }
        let build = |frontend: &Self| match indices {
            Some(indices) => mesh_batches_filtered_indices(
                frame,
                frontend,
                color_format,
                raster_y_direction,
                g_buffer,
                selection,
                terrain_only,
                indices,
            ),
            None => mesh_batches_filtered(
                frame,
                frontend,
                color_format,
                raster_y_direction,
                g_buffer,
                selection,
                terrain_only,
            ),
        };
        let depends_on_camera = |instance: &WorldMeshInstanceRequest| {
            instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0
                && selection.includes(instance)
        };
        let camera_dependent = match indices {
            Some(indices) => indices
                .iter()
                .any(|&index| depends_on_camera(&frame.mesh_instances[index])),
            None => frame.mesh_instances.iter().any(depends_on_camera),
        };
        // Culled repetitions affect first-seen ordering and translucent batch
        // boundaries. Cache their complete topology, then filter a copy; the
        // selected-only identity cannot describe that ordering.
        // When the indices are every instance the plan admits, nothing is
        // culled and the selected plan is exactly the filtered full plan.
        // A culled instance can only change the selected plan by sharing a
        // mesh with a selected one (first-seen order, translucent adjacency).
        let repeated_mesh = indices.is_some_and(|indices| {
            !mesh_batch_indices_cover_selection(frame, selection, terrain_only, indices)
                && mesh_batch_selection_repeats_selected_mesh(frame, selection, terrain_only, indices)
        });
        if camera_dependent {
            return Ok(Arc::new(build(self)?));
        }
        if repeated_mesh {
            let full_identities = frame.mesh_instances.iter().map(|instance| {
                if terrain_only {
                    terrain_batch_instance_key(instance)
                } else {
                    mesh_batch_instance_key(instance)
                }
            }).collect::<Vec<_>>();
            let full_batches = self.cached_mesh_batch_plan(
                frame, color_format, raster_y_direction, g_buffer, selection,
                &full_identities, terrain_only,
            )?;
            let mut batches = full_batches.as_ref().clone();
            let indices = indices.expect("repeated selected plan has instance indices");
            for batch in &mut batches {
                batch.indices.retain(|index| indices.binary_search(index).is_ok());
            }
            batches.retain(|batch| !batch.indices.is_empty());
            return Ok(Arc::new(batches));
        }
        if let Some(entry) = self.mesh_batch_plan_cache.iter().find(|entry| {
            entry.key.color_format == color_format
                && entry.key.raster_y_direction == raster_y_direction
                && entry.key.g_buffer == g_buffer
                && !entry.key.allow_optical
                && entry.key.selection == selection
                && entry.key.terrain_only == terrain_only
                && entry.key.instances == identities
                && entry.key.instance_indices.as_deref() == indices
        }) {
            return Ok(Arc::clone(&entry.batches));
        }
        let batches = Arc::new(build(self)?);
        const MAX_MESH_BATCH_PLAN_CACHE: usize = 4;
        if self.mesh_batch_plan_cache.len() >= MAX_MESH_BATCH_PLAN_CACHE {
            self.mesh_batch_plan_cache.remove(0);
        }
        self.mesh_batch_plan_cache.push(MeshBatchPlanCacheEntry {
            key: MeshBatchPlanKey {
                color_format,
                raster_y_direction,
                g_buffer,
                allow_optical: false,
                selection,
                terrain_only,
                instances: identities.to_vec(),
                instance_indices: indices.map(<[usize]>::to_vec),
            },
            batches: Arc::clone(&batches),
        });
        Ok(batches)
    }


}

/// Layered (view-layering) geometry upload proof exists only for whole-frame
/// attachment captures. Watching every layered mesh in ordinary play filled the
/// GAL's diagnostic upload history, and hazard analysis scanned it on every
/// buffer write. The process-level option is snapshotted at launch.
fn layered_geometry_capture_configured() -> bool {
    cfg!(test)
        || crate::core::environment::var_os("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR").is_some()
}


/// An execution interface that passed `validate`. Sizing many batches of one
/// program takes this instead of re-validating the immutable interface (its
/// scalar field table) once per batch.
#[derive(Clone, Copy)]
pub(in crate::render::worldrender) struct ValidatedSourceInterface<'a>(
    &'a crate::render::shaderpack::programs::TerrainSourceExecutionInterface,
);

impl<'a> ValidatedSourceInterface<'a> {
    pub(in crate::render::worldrender) fn new(
        interface: &'a crate::render::shaderpack::programs::TerrainSourceExecutionInterface,
    ) -> GalResult<Self> {
        interface.validate()?;
        Ok(Self(interface))
    }
}

/// Validated interfaces by address for one sizing pass over many batches.
#[derive(Default)]
pub(in crate::render::worldrender) struct ValidatedSourceInterfaces<'a>(
    Vec<ValidatedSourceInterface<'a>>,
);

impl<'a> ValidatedSourceInterfaces<'a> {
    pub(in crate::render::worldrender) fn get(
        &mut self,
        interface: &'a crate::render::shaderpack::programs::TerrainSourceExecutionInterface,
    ) -> GalResult<ValidatedSourceInterface<'a>> {
        if let Some(validated) = self.0.iter().find(|validated| std::ptr::eq(validated.0, interface)) {
            return Ok(*validated);
        }
        let validated = ValidatedSourceInterface::new(interface)?;
        self.0.push(validated);
        Ok(validated)
    }
}
