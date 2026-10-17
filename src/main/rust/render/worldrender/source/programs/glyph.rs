//! Generation-bound world-glyph programs and canonical producer semantics.

use super::*;
use crate::render::shaderpack::contracts::glyph::{
    prepare_world_glyph_source_program, WorldGlyphSourceProgram,
};

impl WorldPrimitiveFrontend {
    pub(in crate::render::worldrender) fn world_glyph_source_program(
        &self,
        scope: TerrainProgramScope,
    ) -> GalResult<std::sync::Arc<WorldGlyphSourceProgram>> {
        let source = self
            .shader_pack_sources
            .active()
            .ok_or_else(|| GalError::invalid_argument("world glyph requires an active pack"))?;
        let key = (source.generation(), scope);
        if let Some((cached, program)) = self.world_glyph_source_program_cache.borrow().as_ref() {
            if *cached == key {
                return Ok(program.clone());
            }
        }
        let program = std::sync::Arc::new(prepare_world_glyph_source_program(source, scope)?);
        *self.world_glyph_source_program_cache.borrow_mut() = Some((key, program.clone()));
        Ok(program)
    }

    pub(in crate::render::worldrender) fn source_program_for_material_batch(
        &self,
        frame: &WorldPrimitiveFrame,
        batch: SourceTexturedMaterialBatch,
    ) -> GalResult<std::sync::Arc<LoweredTexturedMaterialSourceProgram>> {
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::unsupported_feature(
                "selected source frame has source-textured material work but no Rust shader runtime",
            )
        })?;
        if framed_map_identity(batch.material_id).is_some() {
            return Ok(self
                .world_glyph_source_program(
                    terrain_program_scope_for_sky_type(frame.background.sky_type)?.ok_or_else(
                        || GalError::invalid_argument("world glyph requires a world source scope"),
                    )?,
                )?
                .program
                .clone());
        }
        runtime
            .prepared_lowered_textured_material_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source frame has source-textured material work but no lowered gbuffers_textured writer",
                )
            })
    }

    pub(in crate::render::worldrender) fn apply_material_producer_source_uniforms(
        &self,
        frame: &WorldPrimitiveFrame,
        batch: SourceTexturedMaterialBatch,
        uniforms: &mut TerrainSourceUniformFrame,
    ) -> GalResult<()> {
        if let Some(identity) = framed_map_identity(batch.material_id) {
            let source = self.world_glyph_source_program(
                terrain_program_scope_for_sky_type(frame.background.sky_type)?.ok_or_else(
                    || GalError::invalid_argument("world glyph requires a world source scope"),
                )?,
            )?;
            uniforms.entity_id = Some(source.entity_id(identity)?);
            uniforms.entity_color = Some([0.0; 4]);
            uniforms.block_entity_id = Some(-1);
            uniforms.current_rendered_item_id = Some(-1);
        } else {
            uniforms.block_entity_id = Some(batch.block_entity_id);
        }
        Ok(())
    }
}

fn framed_map_identity(material_id: u32) -> Option<&'static str> {
    match material_id {
        WORLD_MATERIAL_ID_ITEM_FRAME_MAP => Some("minecraft:item_frame"),
        WORLD_MATERIAL_ID_GLOW_ITEM_FRAME_MAP => Some("minecraft:glow_item_frame"),
        _ => None,
    }
}
