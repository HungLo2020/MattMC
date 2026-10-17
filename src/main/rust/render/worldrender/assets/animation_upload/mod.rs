//! Explicit patch uploads for already sampled Rust-owned atlas resources.

#[cfg(test)]
mod tests;
use crate::render::worldrender::*;
use crate::render::shared::sprite_interpolation::PreparedAtlasTick;

#[derive(Default)]
pub(in crate::render::worldrender) struct UploadQueue {
    pending: std::collections::VecDeque<(SubmissionId, Handle, u64)>,
}

#[derive(Debug, PartialEq, Eq)]
pub(in crate::render::worldrender) enum UploadAttempt {
    Accepted(Option<SubmissionId>),
    PendingCompletion,
}

impl UploadQueue {
    /// Reclaim only the oldest owned lease, never device-idle or unrelated work.
    pub(in crate::render::worldrender) fn wait_for_oldest(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        let submission = self.pending.front().map(|entry| entry.0).ok_or_else(|| {
            GalError::invalid_argument("animation backpressure has no owned completion")
        })?;
        gal.retire_through(submission)?;
        self.reap(gal)
    }

    pub(in crate::render::worldrender) fn release(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        while let Some((_, handle, _)) = self.pending.front().copied() {
            gal.destroy(handle)?;
            self.pending.pop_front();
        }
        Ok(())
    }

    pub(in crate::render::worldrender) fn reap(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        let completed = gal.poll_completed();
        gal.retire_completed()?;
        while let Some((submission, handle, _)) = self.pending.front().copied() {
            if submission > completed {
                break;
            }
            gal.destroy(handle)?;
            self.pending.pop_front();
        }
        Ok(())
    }

    /// Candidate ownership stays with the caller on rejection or backpressure.
    /// Success consumes it only after an actual submit, never a deferred append.
    pub(in crate::render::worldrender) fn submit(
        &mut self,
        gal: &mut VulkanicGal,
        resources: &MeshTextureResources,
        animation: &mut crate::render::shared::sprite_interpolation::OwnedAtlasAnimationUpdate,
        retained: &mut WorldMaterialTextureAsset,
        candidate: &mut Option<PreparedAtlasTick>,
    ) -> GalResult<UploadAttempt> {
        self.reap(gal)?;
        let prepared = candidate
            .as_ref()
            .ok_or_else(|| GalError::invalid_argument("missing animation candidate"))?;
        animation.validate_commit(prepared)?;
        if retained.animation_generation != animation.generation
            || retained.width != resources.width
            || retained.height != resources.height
            || retained.mip_rgba.len() + 1 != resources.mip_levels as usize
            || resources.mip_levels == 0
            || resources.mip_levels > texture_mip_level_count(resources.width, resources.height)
        {
            return Err(GalError::invalid_argument(
                "animation retained atlas incarnation mismatch",
            ));
        }
        if retained.coordinate_origin != WorldMeshTextureCoordinateOrigin::Vulkanic {
            return Err(GalError::unsupported_feature(
                "animation uploads require canonical terrain atlas rows",
            ));
        }
        for (mip, pixels) in std::iter::once(&retained.rgba)
            .chain(&retained.mip_rgba)
            .enumerate()
        {
            let expected = u64::from((retained.width >> mip).max(1))
                .checked_mul(u64::from((retained.height >> mip).max(1)))
                .and_then(|pixels| pixels.checked_mul(4))
                .ok_or_else(|| GalError::invalid_argument("retained atlas size overflow"))?;
            if pixels.len() as u64 != expected {
                return Err(GalError::invalid_argument(
                    "retained atlas pixel extent mismatch",
                ));
            }
        }
        let bytes = prepared
            .patches()
            .iter()
            .flat_map(|patch| &patch.mip_pixels)
            .try_fold(0u64, |total, pixels| total.checked_add(pixels.len() as u64))
            .ok_or_else(|| GalError::invalid_argument("animation lease size overflow"))?;
        if bytes == 0 {
            animation.commit_tick(candidate.take().expect("validated candidate"))?;
            return Ok(UploadAttempt::Accepted(None));
        }
        if bytes > 96 * 1024 * 1024 {
            return Err(GalError::invalid_argument(
                "animation lease exceeds byte bound",
            ));
        }
        let resident = self.pending.iter().map(|entry| entry.2).sum::<u64>();
        if self.pending.len() >= 3 || resident + bytes > 96 * 1024 * 1024 {
            return Ok(UploadAttempt::PendingCompletion);
        }
        let upload = gal.create_buffer(BufferDesc {
            label: "terrain-animation.upload-lease".into(),
            size: bytes,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::HostWrite,
                BufferUsage::TransferSrc,
                BufferUsage::TransferDst,
            ],
        })?;
        let result = operations(resources, upload, bytes, prepared).and_then(|operations| {
            gal.submit(SubmissionBatch {
                label: "terrain-animation.upload".into(),
                command_lists: vec![CommandList::from(CommandListDesc {
                    label: "terrain-animation.upload.commands".into(),
                    operations,
                })],
            })
        });
        let token = match result {
            Ok(token) => token,
            Err(error) => {
                // No accepted submission owns this fresh lease. GAL handles any
                // backend failure retirement; the clock candidate is unchanged.
                let _ = gal.retire(upload);
                return Err(error);
            }
        };
        self.pending.push_back((token.submission, upload, bytes));
        // The accepted upload and retained CPU incarnation must agree. These
        // copies cannot fail: all extents were validated before submission and
        // neither destination nor candidate can be mutated through the GAL call.
        retained.equipment_capture_png.take();
        for patch in prepared.patches() {
            for (mip, source) in patch.mip_pixels.iter().enumerate() {
                let stride = (retained.width >> mip).max(1) as usize * 4;
                let row_bytes = (patch.region.width >> mip) as usize * 4;
                let start = (patch.region.y >> mip) as usize * stride
                    + (patch.region.x >> mip) as usize * 4;
                let target = if mip == 0 {
                    &mut retained.rgba
                } else {
                    &mut retained.mip_rgba[mip - 1]
                };
                for row in 0..(patch.region.height >> mip) as usize {
                    target[start + row * stride..start + row * stride + row_bytes]
                        .copy_from_slice(&source[row * row_bytes..(row + 1) * row_bytes]);
                }
            }
        }
        animation.commit_tick(candidate.take().expect("validated candidate"))?;
        Ok(UploadAttempt::Accepted(Some(token.submission)))
    }
}

/// `upload` must be a fresh or completion-retired upload-buffer lease. This
/// lowering never reuses the atlas's initial upload buffer implicitly and does
/// not commit clocks: submission acceptance is the caller's responsibility.
pub(in crate::render::worldrender) fn operations(
    resources: &MeshTextureResources,
    upload: Handle,
    capacity: u64,
    prepared: &PreparedAtlasTick,
) -> GalResult<Vec<CommandOp>> {
    if resources.width == 0
        || resources.height == 0
        || resources.mip_levels == 0
        || resources.mip_levels > texture_mip_level_count(resources.width, resources.height)
    {
        return Err(GalError::invalid_argument(
            "invalid animation upload target",
        ));
    }
    let mut total = 0u64;
    for patch in prepared.patches() {
        if patch.mip_pixels.len() != resources.mip_levels as usize {
            return Err(GalError::invalid_argument(
                "animation upload mip count mismatch",
            ));
        }
        for (level, pixels) in patch.mip_pixels.iter().enumerate() {
            let w = patch.region.width >> level;
            let h = patch.region.height >> level;
            let expected = u64::from(w) * u64::from(h) * 4;
            total = total
                .checked_add(expected)
                .ok_or_else(|| GalError::invalid_argument("animation upload size overflow"))?;
            if w == 0
                || h == 0
                || pixels.len() as u64 != expected
                || u64::from(patch.region.x >> level) + u64::from(w)
                    > u64::from((resources.width >> level).max(1))
                || u64::from(patch.region.y >> level) + u64::from(h)
                    > u64::from((resources.height >> level).max(1))
                || total > capacity
                || total > 96 * 1024 * 1024
            {
                return Err(GalError::invalid_argument(
                    "invalid or over-budget animation upload patch",
                ));
            }
        }
    }
    if total == 0 {
        return Ok(Vec::new());
    }
    let mut bytes = Vec::with_capacity(total as usize);
    let mut copies = Vec::new();
    let mut written_mips = BTreeSet::new();
    for patch in prepared.patches() {
        for (level, pixels) in patch.mip_pixels.iter().enumerate() {
            let w = patch.region.width >> level;
            let h = patch.region.height >> level;
            // GAL tracks texture hazards per subresource, not pixel rectangle.
            // Distinct sprite rectangles still require an explicit ordered
            // transfer-write dependency when they target the same mip.
            if !written_mips.insert(level) {
                copies.push(CommandOp::Barrier(texture_subresource_barrier(
                    resources.texture,
                    TextureSubresourceRange {
                        base_mip: level as u32,
                        mip_count: 1,
                        base_layer: 0,
                        layer_count: 1,
                    },
                    TextureUsageState::TransferDst,
                    TextureUsageState::TransferDst,
                )));
            }
            copies.push(CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                buffer: upload,
                buffer_offset: bytes.len() as u64,
                bytes_per_row: w * 4,
                rows_per_image: h,
                texture: resources.texture,
                texture_mip: level as u32,
                texture_layer: 0,
                texture_origin: TextureOrigin3d {
                    x: patch.region.x >> level,
                    y: patch.region.y >> level,
                    z: 0,
                },
                extent: Extent3d {
                    width: w,
                    height: h,
                    depth: 1,
                },
            }));
            bytes.extend_from_slice(pixels);
        }
    }
    let mips = TextureSubresourceRange {
        base_mip: 0,
        mip_count: resources.mip_levels,
        base_layer: 0,
        layer_count: 1,
    };
    let mut result = vec![
        CommandOp::HostWriteBuffer {
            buffer: upload,
            offset: 0,
            data: bytes,
        },
        CommandOp::Barrier(buffer_barrier(
            upload,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        )),
        CommandOp::Barrier(texture_subresource_barrier(
            resources.texture,
            mips,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )),
    ];
    result.extend(copies);
    result.push(CommandOp::Barrier(texture_subresource_barrier(
        resources.texture,
        mips,
        TextureUsageState::TransferDst,
        TextureUsageState::ShaderRead,
    )));
    Ok(result)
}

