//! Source textured-material frames and staged material primitives.

use super::*;

/// Immutable, caller-independent source payload for one generic
/// `gbuffers_textured` writer. This is deliberately CPU-only: the later
/// named-target pass owns stream allocation, layouts, and command recording.
/// Keeping the material stream separate from terrain prevents a textured
/// producer from inheriting terrain-only vertices or instance transforms.
#[derive(Clone, Debug)]
pub(crate) struct PreparedTexturedMaterialSourceFrame {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) primitives: Vec<TexturedMaterialSourcePrimitive>,
    pub(in crate::render::worldrender) vertex_stream: Vec<u8>,
    pub(in crate::render::worldrender) legacy_texture_transforms: Vec<u8>,
    pub(in crate::render::worldrender) scalar_uniforms: Vec<u8>,
}

impl WorldPrimitiveFrontend {
    /// Prepares one source-derived frame payload from the same semantic mesh
    /// range selected by ordinary Rust batching. The original index width is
    /// used only to resolve section identity; the prepared source draw always
    /// binds its own explicit u32 index stream.
    /// Builds one complete material-source payload from the semantic frame.
    /// This remains unavailable to route selection until a named-target writer
    /// consumes all returned buffers in the same combined source submission.
    pub(crate) fn prepare_textured_material_source_frame(
        &self,
        program: &LoweredTexturedMaterialSourceProgram,
        frame: &WorldPrimitiveFrame,
        texture_transforms: &TerrainSourceTextureTransforms,
        uniform_frame: &TerrainSourceUniformFrame,
    ) -> GalResult<PreparedTexturedMaterialSourceFrame> {
        if frame.material_quads.is_empty() {
            return Err(GalError::invalid_argument(
                "textured material source frame requires at least one semantic material quad",
            ));
        }
        self.prepare_textured_material_source_frame_for_indices(
            program,
            frame,
            0..frame.material_quads.len(),
            texture_transforms,
            uniform_frame,
        )
    }

    pub(crate) fn prepare_textured_material_source_frame_for_indices(
        &self,
        program: &LoweredTexturedMaterialSourceProgram,
        frame: &WorldPrimitiveFrame,
        indices: impl IntoIterator<Item = usize>,
        texture_transforms: &TerrainSourceTextureTransforms,
        uniform_frame: &TerrainSourceUniformFrame,
    ) -> GalResult<PreparedTexturedMaterialSourceFrame> {
        let primitives = stage_source_material_primitives_for_indices(frame, indices)?;
        if primitives.is_empty() {
            return Err(GalError::invalid_argument(
                "textured material source frame requires at least one selected semantic material quad",
            ));
        }
        let vertex_stream = program.pack_material_primitives(&primitives)?;
        let expected_bytes = primitives
            .len()
            .checked_mul(4)
            .and_then(|vertices| {
                vertices.checked_mul(program.execution_interface.vertex_stride as usize)
            })
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "textured material source vertex stream length overflows",
                )
            })?;
        if vertex_stream.len() != expected_bytes {
            return Err(GalError::backend(
                "textured material source vertex stream does not match its prepared program ABI",
            ));
        }
        Ok(PreparedTexturedMaterialSourceFrame {
            frame_id: frame.frame_id,
            primitives,
            vertex_stream,
            legacy_texture_transforms: program
                .pack_legacy_texture_transforms(texture_transforms)?,
            scalar_uniforms: program.pack_scalar_uniforms(uniform_frame)?,
        })
    }

    /// Moves local-material uploads requested by source fullscreen consumers
    /// (for example source-defined celestial writers) into the already opened
    /// exact-frame transaction. These consumers do not own a separate submit;
    /// their copied texture payloads must precede their draws in the same
    /// source submission just like entity and hand material uploads.
    pub(crate) fn absorb_pending_source_material_texture_uploads(&mut self, frame_id: u64) -> GalResult<()> {
        let pending = self
            .pending_source_material_texture_uploads
            .remove(&frame_id)
            .unwrap_or_default();
        if pending.is_empty() {
            return Ok(());
        }
        let transaction = self
            .pending_source_terrain_frame_transactions
            .get_mut(&frame_id)
            .ok_or_else(|| {
                GalError::backend(
                    "source local-material upload has no exact-frame source transaction",
                )
            })?;
        for upload in pending {
            transaction
                .source_material_texture_ids
                .insert(upload.texture_id);
            transaction.operations.extend(upload.operations);
        }
        Ok(())
    }

    pub(crate) fn discard_unsubmitted_source_material_textures(
        &mut self,
        gal: &mut VulkanicGal,
        texture_ids: &BTreeSet<u32>,
    ) {
        if texture_ids.is_empty() {
            return;
        }
        let pack_keys = self
            .lowered_textured_material_source_pack_resources
            .keys()
            .filter(|key| {
                key.local_texture
                    .is_some_and(|(texture_id, _)| texture_ids.contains(&texture_id))
            })
            .cloned()
            .collect::<Vec<_>>();
        self.destroy_lowered_textured_material_source_pack_resources_for_keys(gal, pack_keys);
        let entity_pack_keys = self
            .lowered_entity_source_pack_resources
            .keys()
            .filter(|key| texture_ids.contains(&key.local_texture.0))
            .cloned()
            .collect::<Vec<_>>();
        self.destroy_lowered_entity_source_pack_resources_for_keys(gal, entity_pack_keys);
        let binding_keys = self
            .lowered_textured_material_source_local_texture_resources
            .keys()
            .filter(|key| texture_ids.contains(&key.texture_id))
            .cloned()
            .collect::<Vec<_>>();
        for key in binding_keys {
            if let Some(resources) = self
                .lowered_textured_material_source_local_texture_resources
                .remove(&key)
            {
                let _ = gal.retire(resources.combined_sampler);
            }
        }
        for texture_id in texture_ids {
            self.source_material_texture_resident.remove(texture_id);
            self.source_material_texture_upload_confirmed
                .remove(texture_id);
            self.source_material_texture_staged.remove(texture_id);
            self.source_material_texture_upload_operations
                .remove(texture_id);
            if let Some(resources) = self.source_material_texture_resources.remove(texture_id) {
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
    }
}

pub(crate) fn stage_source_material_primitives(
    frame: &WorldPrimitiveFrame,
) -> GalResult<Vec<TexturedMaterialSourcePrimitive>> {
    stage_source_material_primitives_for_indices(frame, 0..frame.material_quads.len())
}

pub(crate) fn stage_source_material_primitives_for_indices(
    frame: &WorldPrimitiveFrame,
    indices: impl IntoIterator<Item = usize>,
) -> GalResult<Vec<TexturedMaterialSourcePrimitive>> {
    let mut staged = Vec::new();
    for index in indices {
        let quad = frame.material_quads.get(index).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "source material primitive selection references missing quad {index}"
            ))
        })?;
        let winding = match quad.winding {
            WORLD_WINDING_CCW => TexturedMaterialWinding::CounterClockwise,
            WORLD_WINDING_CW => TexturedMaterialWinding::Clockwise,
            value => {
                return Err(GalError::invalid_argument(format!(
                    "world material quad {index} has unknown source winding {value}"
                )));
            }
        };
        let texture_coordinates = match quad.source_uv_space {
            WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE => {
                TexturedMaterialTextureCoordinates::LocalTexture
            }
            WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS => {
                TexturedMaterialTextureCoordinates::MinecraftBlockAtlas
            }
            value => {
                return Err(GalError::invalid_argument(format!(
                    "world material quad {index} has unknown source UV space {value}"
                )));
            }
        };
        match quad.source_program {
            WORLD_MATERIAL_SOURCE_TEXTURED
            | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
            | WORLD_MATERIAL_SOURCE_PARTICLES
            | WORLD_MATERIAL_SOURCE_WEATHER
            | WORLD_MATERIAL_SOURCE_CLOUDS => {
                staged.push(stage_textured_material_primitive_with_vertex_modulation(
                    layered_material_vertices(quad),
                    quad.uvs,
                    quad.vertex_color_argb,
                    quad.vertex_packed_light,
                    texture_coordinates,
                    winding,
                )?)
            }
            WORLD_MATERIAL_SOURCE_UNSPECIFIED => {
                return Err(GalError::unsupported_feature(format!(
                    "world material quad {index} is not classified for a source-derived material program"
                )));
            }
            value => {
                return Err(GalError::invalid_argument(format!(
                    "world material quad {index} has unknown source program {value}"
                )));
            }
        }
    }
    Ok(staged)
}
