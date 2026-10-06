//! Lowered entity and first-person hand programs and draws.

use super::*;

impl WorldPrimitiveFrontend {
    /// Per-generation cache of the entity-stream shadow caster program.
    pub(crate) fn entity_shadow_program(
        &mut self,
        shader_pack_generation: u64,
    ) -> GalResult<LoweredEntitySourceProgram> {
        if let Some((generation, program)) = self.entity_shadow_program_cache.as_ref() {
            if *generation == shader_pack_generation {
                return Ok(program.clone());
            }
        }
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::invalid_argument("entity shadow program requires an active shader pack")
        })?;
        if source.generation() != shader_pack_generation {
            return Err(GalError::invalid_argument(
                "entity shadow program generation does not match the active shader pack",
            ));
        }
        let program = crate::render::shaderpack::contracts::entity::prepare_entity_shadow_source_program(
            source,
            TerrainProgramScope::Overworld,
        )?;
        self.entity_shadow_program_cache = Some((shader_pack_generation, program.clone()));
        Ok(program)
    }
}

impl WorldPrimitiveFrontend {
    #[cfg(test)]
    pub(crate) fn ensure_lowered_source_entity_geometry_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredEntitySourceProgram,
        prepared: &PreparedSourceEntityFrame,
    ) -> GalResult<LoweredSourceTerrainDataKey> {
        self.ensure_lowered_local_source_geometry_resources(gal, program, prepared.mesh.as_ref())
    }

    #[cfg(test)]
    pub(crate) fn ensure_lowered_source_entity_data_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredEntitySourceProgram,
        prepared: &PreparedSourceEntityFrame,
    ) -> GalResult<(
        LoweredSourceTerrainDataKey,
        LoweredSourceTerrainFrameDataKey,
        SourceTerrainFrameStreamAllocation,
    )> {
        self.ensure_lowered_local_source_data_resources(
            gal,
            program,
            prepared.frame_id,
            prepared.mesh.as_ref(),
            &prepared.legacy_texture_transforms,
            &prepared.scalar_uniforms,
            &prepared.instance_transforms,
        )
    }

    #[cfg(test)]
    pub(crate) fn ensure_lowered_entity_source_pack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredEntitySourceProgram,
        resources: &TerrainSourceOwnedResourceSet,
        local_texture: (u32, u64),
    ) -> GalResult<LoweredEntitySourcePackKey> {
        self.ensure_lowered_local_source_pack_resources(gal, program, resources, local_texture)
    }

    /// Entity-specific semantic adapter for the shared owned local-textured
    /// draw preparation. Entity IDs and colors are packed before this boundary;
    /// the resource and pipeline path itself is intentionally shared with hands.
    pub(crate) fn prepare_lowered_source_entity_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredEntitySourceProgram,
        prepared: &PreparedSourceEntityFrame,
        base_resources: &TerrainSourceOwnedResourceSet,
        color_formats: Vec<TextureFormat>,
    ) -> GalResult<EntitySourceDraw> {
        self.prepare_lowered_local_source_draw(
            gal,
            program,
            prepared.frame_id,
            &prepared.mesh,
            prepared.section_index,
            prepared.section_count,
            prepared.texture_id,
            prepared.material_mode,
            prepared.depth_policy,
            prepared.cull_policy,
            prepared.winding,
            TextureFormat::Depth32Float,
            &prepared.legacy_texture_transforms,
            &prepared.scalar_uniforms,
            &prepared.instance_transforms,
            base_resources,
            color_formats,
            None,
        )
    }

    /// Hand-specific semantic adapter for the shared owned local-textured
    /// draw preparation. The hand boundary is preserved by the caller's pass
    /// order and fresh depth target, never by a producer-specific GAL command.
    pub(crate) fn prepare_lowered_source_hand_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredHandSourceProgram,
        prepared: &PreparedSourceHandFrame,
        base_resources: &TerrainSourceOwnedResourceSet,
        color_formats: Vec<TextureFormat>,
        depth_format: TextureFormat,
    ) -> GalResult<EntitySourceDraw> {
        self.prepare_lowered_local_source_draw(
            gal,
            program,
            prepared.frame_id,
            &prepared.mesh,
            prepared.section_index,
            1,
            prepared.texture_id,
            prepared.material_mode,
            prepared.depth_policy,
            prepared.cull_policy,
            prepared.winding,
            depth_format,
            &prepared.legacy_texture_transforms,
            &prepared.scalar_uniforms,
            &prepared.instance_transforms,
            base_resources,
            color_formats,
            None,
        )
    }
}
