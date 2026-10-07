//! Entity-outline targets, post-effect pipelines and mask GPU resources.

use super::*;

impl WorldPrimitiveFrontend {
    pub(crate) fn ensure_entity_outline_target_resources(
        &mut self,
        gal: &mut VulkanicGal,
        width: u32,
        height: u32,
        color_format: TextureFormat,
    ) -> GalResult<&features::outline::EntityOutlineTargetResources> {
        self.ensure_entity_outline_target_resources_with_depth(
            gal,
            width,
            height,
            color_format,
            None,
        )
    }

    pub(crate) fn ensure_entity_outline_target_resources_with_depth(
        &mut self,
        gal: &mut VulkanicGal,
        width: u32,
        height: u32,
        color_format: TextureFormat,
        mask_depth_view: Option<Handle>,
    ) -> GalResult<&features::outline::EntityOutlineTargetResources> {
        if self
            .entity_outline_targets
            .as_ref()
            .is_some_and(|resources| {
                resources.width == width
                    && resources.height == height
                    && resources.color_format == color_format
                    && resources.mask_depth_view == mask_depth_view
            })
        {
            return Ok(self.entity_outline_targets.as_ref().unwrap());
        }
        if let Some(previous) = self.entity_outline_targets.take() {
            self.entity_outline_targets_initialized = false;
            self.pending_entity_outline_targets_written = false;
            self.destroy_entity_outline_mask_gpu_resources(gal);
            if let Some(sets) = self.entity_outline_post_effect_sets.take() {
                for handle in sets.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
            for handle in previous.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let resources = features::outline::create_entity_outline_target_resources_with_depth(
            gal,
            width,
            height,
            color_format,
            mask_depth_view,
        )?;
        self.entity_outline_targets = Some(resources);
        Ok(self.entity_outline_targets.as_ref().unwrap())
    }

    pub(crate) fn destroy_entity_outline_mask_gpu_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(resources) = self.entity_outline_mask_gpu.take() {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    pub(crate) fn ensure_entity_outline_post_effect_pipelines(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: TextureFormat,
    ) -> GalResult<&features::outline::EntityOutlinePostEffectPipelines> {
        if self
            .entity_outline_post_effect_pipelines
            .as_ref()
            .is_some_and(|pipelines| pipelines.color_format == color_format)
        {
            return Ok(self.entity_outline_post_effect_pipelines.as_ref().unwrap());
        }
        if let Some(previous) = self.entity_outline_post_effect_pipelines.take() {
            for handle in previous.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let pipelines = features::outline::create_entity_outline_post_effect_pipelines(gal, color_format)?;
        self.entity_outline_post_effect_pipelines = Some(pipelines);
        Ok(self.entity_outline_post_effect_pipelines.as_ref().unwrap())
    }

    pub(crate) fn ensure_entity_outline_post_effect_resource_sets(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: TextureFormat,
    ) -> GalResult<&features::outline::EntityOutlinePostEffectResourceSets> {
        if self
            .entity_outline_post_effect_sets
            .as_ref()
            .is_some_and(|sets| sets.color_format == color_format)
        {
            return Ok(self.entity_outline_post_effect_sets.as_ref().unwrap());
        }
        if let Some(previous) = self.entity_outline_post_effect_sets.take() {
            for handle in previous.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let targets = self.entity_outline_targets.as_ref().ok_or_else(|| {
            GalError::backend("entity outline targets are required before post-effect sets")
        })?;
        let pipelines = self
            .entity_outline_post_effect_pipelines
            .as_ref()
            .ok_or_else(|| {
                GalError::backend("entity outline pipelines are required before post-effect sets")
            })?;
        let sets =
            features::outline::create_entity_outline_post_effect_resource_sets(gal, targets, pipelines)?;
        self.entity_outline_post_effect_sets = Some(sets);
        Ok(self.entity_outline_post_effect_sets.as_ref().unwrap())
    }

    /// Creates the explicit GAL resources needed by the outline mask writer
    /// for a resolved semantic frame. This prepares only private resources and
    /// upload bytes; the caller must still record barriers, writes, and draws
    /// in the enclosing frame transaction before the route can execute.
    pub(crate) fn prepare_entity_outline_mask_gpu_resources(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
    ) -> GalResult<Option<&features::outline::EntityOutlineMaskGpuResources>> {
        let Some(draws) = features::outline::resolve_entity_outline_mask_draws(self, frame)? else {
            return Ok(None);
        };
        let (instance_bytes, instance_offsets) =
            features::outline::pack_entity_outline_instance_stream(frame, &draws)?;
        self.ensure_entity_outline_post_effect_pipelines(gal, color_format)?;
        if self.entity_outline_targets.is_some() {
            self.ensure_entity_outline_post_effect_resource_sets(gal, color_format)?;
        }
        if instance_bytes.is_empty() {
            return Err(GalError::invalid_argument(
                "entity outline mask stream must contain a camera header",
            ));
        }
        if let Some(previous) = self.entity_outline_mask_gpu.take() {
            for handle in previous.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let mut created = Vec::new();
        let result = (|| -> GalResult<features::outline::EntityOutlineMaskGpuResources> {
            let instance_buffer = gal.create_buffer(BufferDesc {
                label: format!("minecraft.entity-outline.instances.{}", frame.frame_id),
                size: instance_bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Storage, BufferUsage::HostWrite],
            })?;
            created.push(instance_buffer);
            let mut gpu_draws = Vec::with_capacity(draws.len());
            for (draw_index, draw) in draws.iter().enumerate() {
                let (vertex_bytes, index_bytes, texture_id) = {
                    let asset = self.mesh_assets.get(&draw.mesh_key).ok_or_else(|| {
                        GalError::backend("entity outline mesh asset vanished during preparation")
                    })?;
                    let section = asset
                        .sections
                        .get(draw.section_index as usize)
                        .ok_or_else(|| GalError::backend("entity outline section vanished"))?;
                    (
                        asset.vertex_bytes.clone(),
                        asset.index_bytes.clone(),
                        section.texture_id,
                    )
                };
                let geometry_key = MeshGeometryResourceKey {
                    mesh_key: draw.mesh_key,
                    mesh_generation: draw.mesh_generation,
                    vertex_abi: MeshVertexAbi::Rich80,
                };
                self.ensure_mesh_geometry_resources(
                    gal,
                    geometry_key,
                    vertex_bytes,
                    index_bytes,
                    false,
                )?;
                let (vertex_buffer, vertex_offset, vertex_range, index_buffer, index_base) = self
                    .mesh_geometry_resources
                    .get(&geometry_key)
                    .map(|geometry| {
                        (
                            geometry.vertex_buffer,
                            geometry.vertex_offset,
                            geometry.vertex_range,
                            geometry.index_buffer,
                            geometry.index_offset,
                        )
                    })
                    .ok_or_else(|| GalError::backend("entity outline geometry is missing"))?;
                self.ensure_mesh_texture_resources(gal, texture_id, "entity-outline")?;
                let (texture_view, sampler) = self
                    .mesh_texture_resources
                    .get(&texture_id)
                    .map(|texture| (texture.view, texture.sampler))
                    .ok_or_else(|| GalError::backend("entity outline texture is missing"))?;
                let pipeline_key = self.ensure_entity_outline_pipeline_resources(
                    gal,
                    color_format,
                    draw.winding,
                    draw.depth_policy,
                    draw.cull_policy,
                    raster_y_direction,
                )?;
                let (pipeline_handle, pipeline_layout, resource_layout) = self
                    .mesh_pipeline_resources
                    .get(&pipeline_key)
                    .map(|pipeline| {
                        (
                            pipeline.pipeline,
                            pipeline.pipeline_layout,
                            pipeline.resource_layout,
                        )
                    })
                    .ok_or_else(|| GalError::backend("entity outline pipeline is missing"))?;
                // Outline draws use a compact per-draw stream rather than
                // the global fixed-size terrain range. Rebind that same
                // explicit layout with a range bounded to this draw's
                // dynamic offset so GAL validation can prove the slice is
                // inside the newly allocated buffer.
                let resource_set = create_entity_outline_resource_set(
                    gal,
                    &format!("minecraft.entity-outline.draw-{draw_index}"),
                    resource_layout,
                    vertex_buffer,
                    vertex_range,
                    instance_buffer,
                    instance_bytes.len() as u64 - instance_offsets[draw_index],
                    texture_view,
                    sampler,
                )?;
                created.push(resource_set);
                gpu_draws.push(features::outline::EntityOutlineMaskGpuDraw {
                    mesh_key: draw.mesh_key,
                    mesh_generation: draw.mesh_generation,
                    section_index: draw.section_index,
                    index_buffer,
                    index_offset: index_base
                        .checked_add(u64::from(draw.index_offset))
                        .ok_or_else(|| {
                            GalError::invalid_argument("outline index offset overflow")
                        })?,
                    vertex_offset,
                    index_count: draw.index_count,
                    index_type: draw.index_type,
                    pipeline: pipeline_handle,
                    pipeline_layout,
                    resource_set,
                    dynamic_offset: instance_offsets[draw_index],
                    instance_count: draw.instances.len() as u32,
                });
            }
            Ok(features::outline::EntityOutlineMaskGpuResources {
                instance_buffer,
                instance_bytes,
                draws: gpu_draws,
            })
        })();
        let resources = match result {
            Ok(resources) => resources,
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.retire(handle);
                }
                return Err(error);
            }
        };
        self.entity_outline_mask_gpu = Some(resources);
        Ok(self.entity_outline_mask_gpu.as_ref())
    }

    /// Prepare the private solid-color pipeline used by the entity-outline
    /// mask. This only allocates explicit GAL resources; admission of outline
    /// draws remains guarded by the semantic validator until mask bindings and
    /// instance packing are complete.
    pub(crate) fn ensure_entity_outline_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: ColorFormat,
        winding: u32,
        depth_policy: u32,
        cull_policy: u32,
        raster_y_direction: RasterYDirection,
    ) -> GalResult<MeshPipelineResourceKey> {
        let program = minimal_entity_outline_program();
        let key = MeshPipelineResourceKey {
            vertex_abi: MeshVertexAbi::Rich80,
            raster_y_direction,
            g_buffer: false,
            material_mode: WORLD_MATERIAL_MODE_OPAQUE,
            winding,
            depth_policy,
            cull_policy,
            color_format,
            shader_program_identity: program.identity.clone(),
            shader_resource_layout: None,
        };
        self.ensure_mesh_pipeline_resources_for_program(gal, key.clone(), &program)?;
        Ok(key)
    }
}
