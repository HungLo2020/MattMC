//! Packing sprites and affine quads into uniform-backed draw batches.

use super::*;

impl GuiFrontend {
    pub(in crate::render::guirender::frontend) fn pack_sprite(
        &mut self,
        request: &GuiSpriteRequest,
        def: &SpriteDef,
        pre_present_y_flip: bool,
    ) -> GalResult<PackedGuiQuad> {
        let atlas = self.atlas_for(def.group)?;
        let region = atlas.regions.get(&def.id).ok_or_else(|| {
            GalError::backend(format!("sprite '{}' missing from atlas", def.name))
        })?;
        let source_width = request.width.min(def.width);
        let source_height = request.height.min(def.height);
        Ok(PackedGuiQuad {
            origin: [request.x as f32, request.y as f32],
            axis_u: [request.width as f32, 0.0],
            axis_v: [0.0, request.height as f32],
            viewport: request.projection_extent,
            clip: [0.0; 4],
            clip_enabled: false,
            pre_present_y_flip,
            // GUI atlases and raw GUI images use the same semantic convention:
            // byte row zero and V=0 are the top edge. Backends upload the bytes
            // unchanged, and the shader samples the explicit UV/sampler so no
            // API-specific texture-origin conversion leaks into this frontend.
            uv: [
                region.x as f32 / atlas.width as f32,
                region.y as f32 / atlas.height as f32,
                source_width as f32 / atlas.width as f32,
                source_height as f32 / atlas.height as f32,
            ],
            color: argb_to_rgba(request.color_argb),
            texture_mode: GuiRawImageFormat::Rgba8.shader_mode(),
            z: 0.0,
        })
    }
}

pub(in crate::render::guirender::frontend) fn append_gui_quad(
    batches: &mut Vec<GuiBatch>,
    stratum: u32,
    group: TextureGroup,
    quad: PackedGuiQuad,
) {
    if let Some(batch) = batches.last_mut().filter(|batch| {
        batch.stratum == stratum
            && batch.group == group
            && batch.quads.len() < GUI_MAX_PACKED_SPRITES
    }) {
        batch.quads.push(quad);
    } else {
        batches.push(GuiBatch {
            stratum,
            group,
            quads: vec![quad],
        });
    }
}

pub(in crate::render::guirender::frontend) fn append_gui_batches_ops(
    frontend: &GuiFrontend,
    frame_pass: Handle,
    render_target: Handle,
    color_attachment: Handle,
    depth_attachment: Option<Handle>,
    color_format: ColorFormat,
    depth_format: Option<TextureFormat>,
    batches: &[GuiBatch],
    ops: &mut Vec<CommandOp>,
) -> GalResult<()> {
    let mut cursor = 0;
    while cursor < batches.len() {
        // Each GUI texture binding owns one offset-zero uniform buffer. Keep a
        // buffer to at most one draw in a pass: otherwise a later host write
        // would replace the data consumed by an earlier draw. Distinct
        // bindings can safely share a pass because all uploads happen before
        // rendering starts and their pipelines use the same target formats.
        let group_start = cursor;
        let mut uniform_buffers = BTreeSet::new();
        while cursor < batches.len() {
            let batch = &batches[cursor];
            let resources = frontend
                .resources
                .get(&ResourceKey::new(batch.group, color_format, depth_format))
                .ok_or_else(|| GalError::backend("GUI resources vanished before ordered submit"))?;
            if !uniform_buffers.insert(resources.uniform_buffer) {
                break;
            }
            cursor += 1;
        }
        let grouped = &batches[group_start..cursor];
        for batch in grouped {
            let resources = frontend
                .resources
                .get(&ResourceKey::new(batch.group, color_format, depth_format))
                .ok_or_else(|| GalError::backend("GUI resources vanished before ordered submit"))?;
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::HostWriteBuffer {
                buffer: resources.uniform_buffer,
                offset: 0,
                data: packed_uniform_bytes(batch)?,
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }
        ops.push(CommandOp::BeginPass {
            pass: frame_pass,
            target: render_target,
            colors: vec![loaded_frame_color_attachment(color_attachment)],
            depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
        });
        for batch in grouped {
            let resources = frontend
                .resources
                .get(&ResourceKey::new(batch.group, color_format, depth_format))
                .ok_or_else(|| GalError::backend("GUI resources vanished before ordered submit"))?;
            ops.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: resources.pipeline_layout,
                set_index: 0,
                set: resources.resource_set,
                dynamic_offsets: Vec::new(),
            });
            ops.push(CommandOp::SetIndexBuffer {
                buffer: resources.index_buffer,
                offset: 0,
                index_type: crate::render::vulkanic::resources::IndexType::U32,
            });
            ops.push(CommandOp::DrawIndexed {
                indices: 6,
                instances: batch.quads.len() as u32,
            });
        }
        ops.push(CommandOp::EndPass);
    }
    Ok(())
}

pub(in crate::render::guirender::frontend) fn packed_uniform_bytes(batch: &GuiBatch) -> GalResult<Vec<u8>> {
    if batch.quads.is_empty() || batch.quads.len() > GUI_MAX_PACKED_SPRITES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "packed GUI quad count must be in 1..{}: {}",
                GUI_MAX_PACKED_SPRITES,
                batch.quads.len()
            ),
        ));
    }
    let mut out = Vec::with_capacity(batch.quads.len() * GUI_UNIFORM_BYTES);
    for quad in &batch.quads {
        for value in quad.origin.into_iter().chain(quad.axis_u) {
            push_f32(&mut out, value);
        }
        for value in quad.axis_v.into_iter().chain([quad.texture_mode, quad.z]) {
            push_f32(&mut out, value);
        }
        for value in quad.viewport.into_iter().chain([
            if quad.clip_enabled { 1.0 } else { 0.0 },
            if quad.pre_present_y_flip { 1.0 } else { 0.0 },
        ]) {
            push_f32(&mut out, value);
        }
        for value in quad.clip {
            push_f32(&mut out, value);
        }
        for value in quad.uv {
            push_f32(&mut out, value);
        }
        for value in quad.color {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

pub(in crate::render::guirender::frontend) fn argb_to_rgba(color_argb: u32) -> [f32; 4] {
    [
        ((color_argb >> 16) & 0xff) as f32 / 255.0,
        ((color_argb >> 8) & 0xff) as f32 / 255.0,
        (color_argb & 0xff) as f32 / 255.0,
        ((color_argb >> 24) & 0xff) as f32 / 255.0,
    ]
}
