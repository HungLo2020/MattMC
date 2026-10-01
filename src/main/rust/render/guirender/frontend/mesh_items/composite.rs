//! Composite batches that blit cached mesh item rasters into the GUI frame.

use super::*;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::guirender::frontend) struct GuiMeshCompositeKey {
    pub(in crate::render::guirender::frontend) item_identity: u64,
    pub(in crate::render::guirender::frontend) width: u32,
    pub(in crate::render::guirender::frontend) height: u32,
    pub(in crate::render::guirender::frontend) color_format: ColorFormat,
    pub(in crate::render::guirender::frontend) depth_format: Option<TextureFormat>,
}

pub(in crate::render::guirender::frontend) struct PendingGuiMeshComposite<'a> {
    pub(in crate::render::guirender::frontend) key: GuiMeshCompositeKey,
    pub(in crate::render::guirender::frontend) source: crate::render::guirender::mesh::GuiMeshOffscreenTarget,
    pub(in crate::render::guirender::frontend) source_usage: TextureUsageState,
    pub(in crate::render::guirender::frontend) draw: &'a GuiMeshPreparedDraw,
    pub(in crate::render::guirender::frontend) uniform_offset: u64,
}

impl GuiFrontend {
    pub(in crate::render::guirender::frontend) fn append_mesh_composite_batch(
        &self,
        frame_pass: Handle,
        render_target: Handle,
        color_attachment: Handle,
        depth_attachment: Option<Handle>,
        pending: &[PendingGuiMeshComposite<'_>],
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        let Some(first) = pending.first() else {
            return Ok(());
        };
        // All composite keys in one ordered mesh item family share the final
        // target format and therefore one immutable compositor pipeline. Each
        // resource set still keeps its own source view and dynamic uniform
        // buffer range, so source/material ownership stays explicit.
        let first_resources = self
            .mesh_composites
            .get(&first.key)
            .ok_or_else(|| GalError::backend("GUI mesh compositor vanished before batch draw"))?;
        for composite in pending {
            let resources = self.mesh_composites.get(&composite.key).ok_or_else(|| {
                GalError::backend("GUI mesh compositor vanished before batch draw")
            })?;
            if resources.pipeline != first_resources.pipeline
                || resources.pipeline_layout != first_resources.pipeline_layout
            {
                return Err(GalError::backend(
                    "GUI mesh composite batch contains incompatible compositor pipelines",
                ));
            }
            if composite.draw.render_extent
                != [
                    composite.source.extent.width,
                    composite.source.extent.height,
                ]
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh composite source extent does not match its prepared draw",
                ));
            }
            resources.append_composite_upload(
                composite.source.color,
                composite.source_usage,
                crate::render::guirender::mesh::composite_uniform_bytes(composite.draw),
                composite.uniform_offset,
                operations,
            )?;
        }
        operations.push(CommandOp::BeginPass {
            pass: frame_pass,
            target: render_target,
            colors: vec![PassAttachment {
                view: color_attachment,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: depth_attachment.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(first_resources.pipeline));
        for composite in pending {
            let resources = self.mesh_composites.get(&composite.key).ok_or_else(|| {
                GalError::backend("GUI mesh compositor vanished before batch draw")
            })?;
            resources.append_composite_draw(composite.uniform_offset, operations);
        }
        operations.push(CommandOp::EndPass);
        Ok(())
    }
}

pub(in crate::render::guirender::frontend) fn pack_gui_composite_uniform_uploads(
    operations: &mut Vec<CommandOp>,
    composite_uniform_buffers: &BTreeSet<Handle>,
) {
    #[derive(Clone)]
    struct Upload {
        start: usize,
        buffer: Handle,
        offset: u64,
        data: Vec<u8>,
        before: CommandOp,
        after: CommandOp,
    }

    let mut by_buffer: BTreeMap<Handle, Vec<Upload>> = BTreeMap::new();
    for start in 0..operations.len().saturating_sub(2) {
        let (
            CommandOp::Barrier(ResourceBarrier {
                resource: before_buffer,
                before: TextureUsageState::ShaderRead,
                after: TextureUsageState::TransferDst,
                ..
            }),
            CommandOp::HostWriteBuffer {
                buffer,
                offset,
                data,
            },
            CommandOp::Barrier(ResourceBarrier {
                resource: after_buffer,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ShaderRead,
                ..
            }),
        ) = (
            &operations[start],
            &operations[start + 1],
            &operations[start + 2],
        )
        else {
            continue;
        };
        if before_buffer != buffer
            || after_buffer != buffer
            || !composite_uniform_buffers.contains(buffer)
        {
            continue;
        }
        by_buffer.entry(*buffer).or_default().push(Upload {
            start,
            buffer: *buffer,
            offset: *offset,
            data: data.clone(),
            before: operations[start].clone(),
            after: operations[start + 2].clone(),
        });
    }

    let mut replacements = BTreeMap::<usize, Vec<CommandOp>>::new();
    let mut skipped = BTreeSet::new();
    for uploads in by_buffer.into_values().filter(|uploads| uploads.len() > 1) {
        let mut ranges = uploads
            .iter()
            .map(|upload| {
                upload
                    .offset
                    .checked_add(upload.data.len() as u64)
                    .map(|end| (upload.offset, end))
            })
            .collect::<Option<Vec<_>>>();
        let Some(ref mut ranges) = ranges else {
            continue;
        };
        ranges.sort_unstable();
        if ranges.windows(2).any(|pair| pair[0].1 > pair[1].0) {
            continue;
        }
        let minimum = ranges[0].0;
        let maximum = ranges.last().unwrap().1;
        let Ok(packed_len) = usize::try_from(maximum - minimum) else {
            continue;
        };
        let mut packed = vec![0; packed_len];
        for upload in &uploads {
            let Ok(relative) = usize::try_from(upload.offset - minimum) else {
                continue;
            };
            packed[relative..relative + upload.data.len()].copy_from_slice(&upload.data);
            skipped.extend(upload.start..upload.start + 3);
        }
        let first = &uploads[0];
        replacements.insert(
            first.start,
            vec![
                first.before.clone(),
                CommandOp::HostWriteBuffer {
                    buffer: first.buffer,
                    offset: minimum,
                    data: packed,
                },
                first.after.clone(),
            ],
        );
    }
    if replacements.is_empty() {
        return;
    }
    let original = std::mem::take(operations);
    for (index, operation) in original.into_iter().enumerate() {
        if let Some(replacement) = replacements.remove(&index) {
            operations.extend(replacement);
        }
        if !skipped.contains(&index) {
            operations.push(operation);
        }
    }
}
