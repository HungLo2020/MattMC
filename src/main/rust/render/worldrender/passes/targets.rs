//! Depth attachments, frame passes, G-buffer resources and bindings, runtime targets and screen passes.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) struct DepthAttachmentResources {
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) view: Handle,
    pub(in crate::render::worldrender) extent: Extent3d,
}

pub(in crate::render::worldrender) struct GBufferResources {
    pub(in crate::render::worldrender) shadow_depth_texture: Handle,
    /// Iris `shadowtex1`: shadow depth copied after opaque/cutout casters and
    /// before translucent ones, so underwater receivers see water in
    /// shadowtex0 but not here.
    pub(in crate::render::worldrender) shadow_depth_opaque_texture: Handle,
    pub(in crate::render::worldrender) shadow_depth_opaque_view: Handle,
    pub(in crate::render::worldrender) shadow_color_texture: Handle,
    pub(in crate::render::worldrender) shadow_light_shaft_texture: Handle,
    pub(in crate::render::worldrender) albedo_texture: Handle,
    pub(in crate::render::worldrender) normal_texture: Handle,
    pub(in crate::render::worldrender) material_light_texture: Handle,
    pub(in crate::render::worldrender) world_position_texture: Handle,
    pub(in crate::render::worldrender) deferred_lit_texture: Handle,
    pub(in crate::render::worldrender) translucent_capture_texture: Handle,
    pub(in crate::render::worldrender) translucent_capture_depth_texture: Handle,
    pub(in crate::render::worldrender) composite0_texture: Handle,
    pub(in crate::render::worldrender) composite1_texture: Handle,
    pub(in crate::render::worldrender) depth_texture: Handle,
    pub(in crate::render::worldrender) main_depth_before_translucency_texture: Handle,
    pub(in crate::render::worldrender) main_depth_previous_texture: Handle,
    pub(in crate::render::worldrender) shadow_depth_view: Handle,
    pub(in crate::render::worldrender) shadow_color_view: Handle,
    pub(in crate::render::worldrender) shadow_light_shaft_view: Handle,
    pub(in crate::render::worldrender) albedo_view: Handle,
    pub(in crate::render::worldrender) normal_view: Handle,
    pub(in crate::render::worldrender) material_light_view: Handle,
    pub(in crate::render::worldrender) world_position_view: Handle,
    pub(in crate::render::worldrender) deferred_lit_view: Handle,
    pub(in crate::render::worldrender) translucent_capture_view: Handle,
    pub(in crate::render::worldrender) translucent_capture_depth_view: Handle,
    pub(in crate::render::worldrender) composite0_view: Handle,
    pub(in crate::render::worldrender) composite1_view: Handle,
    pub(in crate::render::worldrender) depth_view: Handle,
    pub(in crate::render::worldrender) main_depth_before_translucency_view: Handle,
    pub(in crate::render::worldrender) main_depth_previous_view: Handle,
    pub(in crate::render::worldrender) sampler: Handle,
    pub(in crate::render::worldrender) shadow_target: Handle,
    pub(in crate::render::worldrender) target: Handle,
    pub(in crate::render::worldrender) deferred_lit_target: Handle,
    pub(in crate::render::worldrender) translucent_target: Handle,
    pub(in crate::render::worldrender) translucent_capture_target: Handle,
    pub(in crate::render::worldrender) composite0_target: Handle,
    pub(in crate::render::worldrender) composite1_target: Handle,
    pub(in crate::render::worldrender) shadow_pass: Handle,
    pub(in crate::render::worldrender) g_buffer_pass: Handle,
    pub(in crate::render::worldrender) deferred_lighting_pass: Handle,
    pub(in crate::render::worldrender) translucent_pass: Handle,
    pub(in crate::render::worldrender) translucent_capture_pass: Handle,
    pub(in crate::render::worldrender) composite0_pass: Handle,
    pub(in crate::render::worldrender) composite1_pass: Handle,
    pub(in crate::render::worldrender) composite_uniform_buffer: Handle,
    pub(in crate::render::worldrender) screen_vertex_shader: Handle,
    pub(in crate::render::worldrender) deferred_lighting_fragment_shader: Handle,
    pub(in crate::render::worldrender) composite0_fragment_shader: Handle,
    pub(in crate::render::worldrender) composite1_fragment_shader: Handle,
    pub(in crate::render::worldrender) final_fragment_shader: Handle,
    pub(in crate::render::worldrender) screen_resource_layout: Handle,
    pub(in crate::render::worldrender) deferred_lighting_resource_set: Handle,
    pub(in crate::render::worldrender) composite0_resource_set: Handle,
    pub(in crate::render::worldrender) composite1_resource_set: Handle,
    pub(in crate::render::worldrender) screen_pipeline_layout: Handle,
    pub(in crate::render::worldrender) deferred_lighting_pipeline: Handle,
    pub(in crate::render::worldrender) composite0_pipeline: Handle,
    pub(in crate::render::worldrender) composite1_pipeline: Handle,
    pub(in crate::render::worldrender) final_resource_set: Handle,
    pub(in crate::render::worldrender) final_pipeline: Handle,
    pub(in crate::render::worldrender) extent: Extent3d,
    pub(in crate::render::worldrender) shadow_extent: Extent3d,
    pub(in crate::render::worldrender) frame_color_format: ColorFormat,
    pub(in crate::render::worldrender) final_depth_format: Option<TextureFormat>,
    pub(in crate::render::worldrender) generation: u64,
    /// The three deferred/composite screen targets end each successful graph
    /// submission in ShaderRead. This remains false until their first write,
    /// so the next frame emits the correct explicit old-layout transition.
    pub(in crate::render::worldrender) screen_targets_initialized: bool,
    pub(in crate::render::worldrender) translucent_capture_initialized: bool,
    pub(in crate::render::worldrender) shadow_targets_initialized: bool,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::worldrender) struct GBufferFinalBindingKey {
    pub(in crate::render::worldrender) frame_target: Handle,
    pub(in crate::render::worldrender) frame_color_format: ColorFormat,
    pub(in crate::render::worldrender) final_depth_view: Option<Handle>,
    pub(in crate::render::worldrender) graph_generation: u64,
}

pub(in crate::render::worldrender) struct GBufferFinalBindingResources {
    pub(in crate::render::worldrender) final_pass: Handle,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::worldrender) struct SourceTerrainColorAttachmentKey {
    pub(in crate::render::worldrender) output: crate::render::shaderpack::contracts::terrain::TerrainPassOutput,
    pub(in crate::render::worldrender) source_slot: u32,
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) view: Handle,
    pub(in crate::render::worldrender) format: TextureFormat,
    pub(in crate::render::worldrender) clear_each_frame: bool,
    pub(in crate::render::worldrender) clear_color_bits: Option<[u32; 4]>,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::worldrender) struct SourceTerrainColorPassTargetKey {
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) graph_generation: u64,
    pub(in crate::render::worldrender) extent: [u32; 3],
    pub(in crate::render::worldrender) color_attachments: Vec<SourceTerrainColorAttachmentKey>,
    pub(in crate::render::worldrender) phase: TerrainSourceColorPassPhase,
    pub(in crate::render::worldrender) depth_texture: Handle,
    pub(in crate::render::worldrender) depth_view: Handle,
}

pub(in crate::render::worldrender) struct SourceTerrainColorPassTargetResources {
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
}

/// Stable semantic identity for the private first-person depth attachment.
/// The hand pass loads the completed named world colors but must never reuse
/// world depth. Keeping this identity separate from the render-target wrapper
/// prevents a future hand writer from accidentally inheriting a Java/Iris or
/// terrain depth domain.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::worldrender) struct HandSourceDepthKey {
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) graph_generation: u64,
    pub(in crate::render::worldrender) extent: [u32; 3],
    pub(in crate::render::worldrender) format: TextureFormat,
}

pub(in crate::render::worldrender) struct HandSourceDepthResources {
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) view: Handle,
    pub(in crate::render::worldrender) sampled_sampler: Option<Handle>,
    pub(in crate::render::worldrender) combined_sampler: Option<Handle>,
}

impl HandSourceDepthResources {
    pub(in crate::render::worldrender) fn destroy(self, gal: &mut VulkanicGal) {
        if let Some(combined_sampler) = self.combined_sampler {
            let _ = gal.destroy(combined_sampler);
        }
        let _ = gal.destroy(self.view);
        let _ = gal.destroy(self.texture);
    }
}

/// Confirmation state for the two depth snapshots that source-derived passes
/// may eventually consume. It is deliberately frontend-owned: snapshots are
/// valid only after the one combined world/GUI submission that copied them is
/// accepted by GAL, never merely because graph operations were assembled.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(in crate::render::worldrender) struct GBufferDepthHistoryState {
    pub(in crate::render::worldrender) graph_generation: u64,
    pub(in crate::render::worldrender) before_translucency_valid: bool,
    pub(in crate::render::worldrender) previous_valid: bool,
    pub(in crate::render::worldrender) last_frame_id: Option<u64>,
    pub(in crate::render::worldrender) last_submission: Option<SubmissionId>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct GBufferDepthHistorySubmission {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) graph_generation: u64,
    pub(in crate::render::worldrender) prior_before_translucency_valid: bool,
}

impl DepthAttachmentResources {
    pub(in crate::render::worldrender) fn handles_in_destroy_order(&self) -> [Handle; 2] {
        [self.view, self.texture]
    }
}

impl GBufferResources {
    pub(in crate::render::worldrender) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        vec![
            self.final_pipeline,
            self.composite1_pipeline,
            self.composite0_pipeline,
            self.deferred_lighting_pipeline,
            self.screen_pipeline_layout,
            self.final_resource_set,
            self.composite1_resource_set,
            self.composite0_resource_set,
            self.deferred_lighting_resource_set,
            self.screen_resource_layout,
            self.final_fragment_shader,
            self.composite1_fragment_shader,
            self.composite0_fragment_shader,
            self.deferred_lighting_fragment_shader,
            self.screen_vertex_shader,
            self.composite_uniform_buffer,
            self.composite1_pass,
            self.composite0_pass,
            self.translucent_pass,
            self.translucent_capture_pass,
            self.deferred_lighting_pass,
            self.g_buffer_pass,
            self.shadow_pass,
            self.composite1_target,
            self.composite0_target,
            self.translucent_target,
            self.translucent_capture_target,
            self.deferred_lit_target,
            self.target,
            self.shadow_target,
            self.sampler,
            self.depth_view,
            self.main_depth_previous_view,
            self.main_depth_before_translucency_view,
            self.composite1_view,
            self.composite0_view,
            self.deferred_lit_view,
            self.translucent_capture_view,
            self.translucent_capture_depth_view,
            self.world_position_view,
            self.material_light_view,
            self.normal_view,
            self.albedo_view,
            self.shadow_depth_opaque_view,
            self.shadow_depth_view,
            self.shadow_light_shaft_view,
            self.shadow_color_view,
            self.depth_texture,
            self.main_depth_previous_texture,
            self.main_depth_before_translucency_texture,
            self.composite1_texture,
            self.composite0_texture,
            self.deferred_lit_texture,
            self.translucent_capture_texture,
            self.translucent_capture_depth_texture,
            self.world_position_texture,
            self.material_light_texture,
            self.normal_texture,
            self.albedo_texture,
            self.shadow_depth_opaque_texture,
            self.shadow_depth_texture,
            self.shadow_light_shaft_texture,
            self.shadow_color_texture,
        ]
    }
}

impl GBufferFinalBindingResources {
    pub(in crate::render::worldrender) fn handles_in_destroy_order(&self) -> [Handle; 1] {
        [self.final_pass]
    }
}

#[derive(Clone, Copy)]
pub(in crate::render::worldrender) struct CachedPass {
    pub(in crate::render::worldrender) frame_target: Handle,
    pub(in crate::render::worldrender) depth_view: Handle,
    pub(in crate::render::worldrender) pass: Handle,
}

#[derive(Clone, Copy)]
pub(in crate::render::worldrender) struct CachedColorOnlyPass {
    pub(in crate::render::worldrender) frame_target: Handle,
    pub(in crate::render::worldrender) pass: Handle,
}

impl WorldPrimitiveFrontend {
    pub fn clear_frame_pass(&mut self, gal: &mut VulkanicGal) {
        for pass in self.cached_passes.drain(..) {
            let _ = gal.destroy(pass.pass);
        }
        for pass in self.cached_color_only_passes.drain(..) {
            let _ = gal.destroy(pass.pass);
        }
        let retired = self.destroy_g_buffer_final_bindings(gal);
        self.pending_g_buffer_resources_retired = self
            .pending_g_buffer_resources_retired
            .saturating_add(retired);
    }

    pub fn clear_frame_passes_for_targets(&mut self, gal: &mut VulkanicGal, targets: &[Handle]) {
        self.source_final_output_cache
            .retire_frame_targets(gal, targets);
        self.cached_passes.retain(|pass| {
            if targets.contains(&pass.frame_target) {
                let _ = gal.destroy(pass.pass);
                false
            } else {
                true
            }
        });
        self.cached_color_only_passes.retain(|pass| {
            if targets.contains(&pass.frame_target) {
                let _ = gal.destroy(pass.pass);
                false
            } else {
                true
            }
        });
        let keys = self
            .g_buffer_final_bindings
            .keys()
            .copied()
            .filter(|key| targets.contains(&key.frame_target))
            .collect::<Vec<_>>();
        for key in keys {
            let retired = self.destroy_g_buffer_final_binding(gal, key);
            self.pending_g_buffer_resources_retired = self
                .pending_g_buffer_resources_retired
                .saturating_add(retired);
        }
    }

    pub(in crate::render::worldrender) fn ensure_depth_attachment(
        &mut self,
        gal: &mut VulkanicGal,
        pass_target: Handle,
        width: u32,
        height: u32,
    ) -> GalResult<(Handle, Handle, bool, u64)> {
        let extent = Extent3d {
            width,
            height,
            depth: 1,
        };
        if pass_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
            let (texture, view) = gal.frame_target_owned_depth_attachment(pass_target)?;
            let target_extent = gal.pass_target_extent(pass_target)?;
            if target_extent != extent {
                return Err(GalError::invalid_argument(
                    "frame-target depth extent does not match the acquired target",
                ));
            }
            // The acquired target owns this depth image for the lifetime of
            // the frame.  Do not allocate a second frontend-private depth
            // resource or silently substitute one.
            if let Some(depth) = self.depth_attachment.take() {
                for handle in depth.handles_in_destroy_order() {
                    let _ = gal.destroy(handle);
                }
                self.pending_depth_attachment_retires =
                    self.pending_depth_attachment_retires.saturating_add(1);
                self.clear_frame_pass(gal);
            }
            return Ok((texture, view, false, 0));
        }
        if let Some((texture, view)) = gal.pass_target_depth_attachment(pass_target)? {
            if let Some(depth) = self.depth_attachment.take() {
                for handle in depth.handles_in_destroy_order() {
                    let _ = gal.destroy(handle);
                }
                self.pending_depth_attachment_retires =
                    self.pending_depth_attachment_retires.saturating_add(1);
                self.clear_frame_pass(gal);
            }
            return Ok((texture, view, false, 0));
        }
        if let Some(depth) = self.depth_attachment.as_ref() {
            if depth.extent == extent {
                return Ok((depth.texture, depth.view, false, 0));
            }
        }
        let mut retired = 0;
        if let Some(depth) = self.depth_attachment.take() {
            for handle in depth.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
            retired = 1;
        }
        self.clear_frame_pass(gal);
        let label = format!("world-block-outline-depth-gen{}", self.generation);
        let texture = gal.create_texture(TextureDesc {
            label: format!("{label}.texture"),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::DepthStencilAttachment],
        })?;
        let view = match gal.create_texture_view(TextureViewDesc {
            label: format!("{label}.view"),
            texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }) {
            Ok(view) => view,
            Err(error) => {
                let _ = gal.destroy(texture);
                return Err(error);
            }
        };
        self.depth_attachment = Some(DepthAttachmentResources {
            texture,
            view,
            extent,
        });
        Ok((texture, view, true, retired))
    }

    pub(in crate::render::worldrender) fn frame_pass(
        &mut self,
        gal: &mut VulkanicGal,
        pass_target: Handle,
        depth_view: Handle,
    ) -> GalResult<Handle> {
        if let Some(cached) = self
            .cached_passes
            .iter()
            .find(|cached| cached.frame_target == pass_target && cached.depth_view == depth_view)
        {
            return Ok(cached.pass);
        }
        // Several targets can be recorded into one not-yet-submitted frame.
        // A target switch must not destroy a pass already referenced by it.
        // Frame release/resize retires exact targets; reset drains everything.
        if self.cached_passes.len() >= 32 {
            return Err(GalError::unsupported_feature(
                "world frame-pass residency exceeds 32 live targets",
            ));
        }
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.world.block-outline.pass".to_string(),
            target: pass_target,
            color_formats: vec![gal.pass_target_color_format(pass_target)?],
            depth_format: Some(TextureFormat::Depth32Float),
        })?;
        self.cached_passes.push(CachedPass {
            frame_target: pass_target,
            depth_view,
            pass,
        });
        Ok(pass)
    }

    pub(in crate::render::worldrender) fn color_only_frame_pass(
        &mut self,
        gal: &mut VulkanicGal,
        pass_target: Handle,
    ) -> GalResult<Handle> {
        if let Some(cached) = self
            .cached_color_only_passes
            .iter()
            .find(|cached| cached.frame_target == pass_target)
        {
            return Ok(cached.pass);
        }
        if self.cached_color_only_passes.len() >= 32 {
            return Err(GalError::unsupported_feature(
                "world color-only pass residency exceeds 32 live targets",
            ));
        }
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.world.block-outline.color-only-pass".to_string(),
            target: pass_target,
            color_formats: vec![gal.pass_target_color_format(pass_target)?],
            depth_format: None,
        })?;
        self.cached_color_only_passes.push(CachedColorOnlyPass {
            frame_target: pass_target,
            pass,
        });
        Ok(pass)
    }

    pub(in crate::render::worldrender) fn ensure_g_buffer_resources(
        &mut self,
        gal: &mut VulkanicGal,
        width: u32,
        height: u32,
        frame_color_format: ColorFormat,
        final_depth_format: Option<TextureFormat>,
        source_scope: Option<TerrainProgramScope>,
        profile: &mut WholeFrameProfile,
    ) -> GalResult<()> {
        let extent = Extent3d {
            width,
            height,
            depth: 1,
        };
        // Source shadow maps have their own pack-defined square resolution.
        // The vanilla graph retains its existing viewport-sized attachment.
        let shadow_extent = self
            .shader_pack_sources
            .active_shadow_policy_for_scope(source_scope.unwrap_or(TerrainProgramScope::Default))?
            .map(|policy| Extent3d {
                width: policy.resolution(),
                height: policy.resolution(),
                depth: 1,
            })
            .unwrap_or(extent);
        let persistent_key_started = std::time::Instant::now();
        let has_compatible_persistent_resources =
            self.g_buffer_resources.as_ref().is_some_and(|resources| {
                resources.extent == extent
                    && resources.shadow_extent == shadow_extent
                    && resources.frame_color_format == frame_color_format
                    && resources.final_depth_format == final_depth_format
                    && resources.generation == self.generation
            });
        profile.world_prepare_g_buffer_persistent_key_nanos = profile
            .world_prepare_g_buffer_persistent_key_nanos
            .saturating_add(elapsed_nanos_u64(persistent_key_started));
        let cache_check_started = std::time::Instant::now();
        if has_compatible_persistent_resources {
            profile.g_buffer_persistent_cache_hits =
                profile.g_buffer_persistent_cache_hits.saturating_add(1);
            profile.world_prepare_g_buffer_cache_check_nanos = profile
                .world_prepare_g_buffer_cache_check_nanos
                .saturating_add(elapsed_nanos_u64(cache_check_started));
            profile.world_prepare_g_buffer_persistent_lookup_nanos = profile
                .world_prepare_g_buffer_persistent_lookup_nanos
                .saturating_add(elapsed_nanos_u64(cache_check_started));
            return Ok(());
        }
        profile.g_buffer_persistent_cache_misses =
            profile.g_buffer_persistent_cache_misses.saturating_add(1);
        profile.world_prepare_g_buffer_cache_check_nanos = profile
            .world_prepare_g_buffer_cache_check_nanos
            .saturating_add(elapsed_nanos_u64(cache_check_started));
        profile.world_prepare_g_buffer_persistent_lookup_nanos = profile
            .world_prepare_g_buffer_persistent_lookup_nanos
            .saturating_add(elapsed_nanos_u64(cache_check_started));
        let destroy_started = std::time::Instant::now();
        let retired = self.destroy_g_buffer_resources(gal);
        profile.g_buffer_resources_retired =
            profile.g_buffer_resources_retired.saturating_add(retired);
        profile.world_prepare_g_buffer_destroy_nanos = profile
            .world_prepare_g_buffer_destroy_nanos
            .saturating_add(elapsed_nanos_u64(destroy_started));
        let label = format!("world-shader-g-buffer-gen{}", self.generation);
        let plan_started = std::time::Instant::now();
        self.ensure_shader_runtime(gal, self.generation)?;
        let shader_executor = self
            .shader_runtime
            .as_ref()
            .expect("shader runtime installed before G-buffer resource preparation");
        let shader_plan = shader_executor.plan();
        profile.world_prepare_g_buffer_plan_nanos = profile
            .world_prepare_g_buffer_plan_nanos
            .saturating_add(elapsed_nanos_u64(plan_started));
        let mut created = Vec::new();
        let create_started = std::time::Instant::now();
        let result = (|| -> GalResult<GBufferResources> {
            let shadow_depth_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.shadow-depth.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent: shadow_extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::DepthStencilAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(shadow_depth_texture);
            let shadow_depth_opaque_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.shadow-depth-opaque.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent: shadow_extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })?;
            created.push(shadow_depth_opaque_texture);
            let shadow_color_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.shadow-color"), shadow_extent)?;
            created.push(shadow_color_texture);
            let shadow_light_shaft_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.shadow-light-shaft"), shadow_extent)?;
            created.push(shadow_light_shaft_texture);
            let albedo_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.albedo"), extent)?;
            created.push(albedo_texture);
            let normal_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.normal"), extent)?;
            created.push(normal_texture);
            let material_light_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.material-light"), extent)?;
            created.push(material_light_texture);
            let world_position_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.world-position"), extent)?;
            created.push(world_position_texture);
            let deferred_lit_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.deferred-lit"), extent)?;
            created.push(deferred_lit_texture);
            let translucent_capture_texture = create_g_buffer_color_texture(
                gal,
                &format!("{label}.translucent-capture"),
                extent,
            )?;
            created.push(translucent_capture_texture);
            let composite0_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.composite-0"), extent)?;
            created.push(composite0_texture);
            let composite1_texture =
                create_g_buffer_color_texture(gal, &format!("{label}.composite-1"), extent)?;
            created.push(composite1_texture);
            let depth_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.depth.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                // TransferDst: the Iris-ordered solid hand publishes its merged
                // depth back into main depth before depthtex1 and deferred.
                usages: vec![
                    TextureUsage::DepthStencilAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            })?;
            created.push(depth_texture);
            let source_depth_snapshot = |suffix: &str| TextureDesc {
                label: format!("{label}.{suffix}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    // The snapshot may also be selected as the private
                    // source final-output depth domain; keep that attachment
                    // capability explicit rather than borrowing the live
                    // G-buffer depth image.
                    TextureUsage::DepthStencilAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            };
            let main_depth_before_translucency_texture =
                gal.create_texture(source_depth_snapshot("main-depth-before-translucency"))?;
            created.push(main_depth_before_translucency_texture);
            let translucent_capture_depth_texture =
                gal.create_texture(source_depth_snapshot("translucent-capture-depth"))?;
            created.push(translucent_capture_depth_texture);
            let main_depth_previous_texture =
                gal.create_texture(source_depth_snapshot("main-depth-previous"))?;
            created.push(main_depth_previous_texture);
            let shadow_depth_view = create_texture_view(
                gal,
                &format!("{label}.shadow-depth.view"),
                shadow_depth_texture,
                TextureFormat::Depth32Float,
            )?;
            created.push(shadow_depth_view);
            let shadow_depth_opaque_view = create_texture_view(
                gal,
                &format!("{label}.shadow-depth-opaque.view"),
                shadow_depth_opaque_texture,
                TextureFormat::Depth32Float,
            )?;
            created.push(shadow_depth_opaque_view);
            let shadow_color_view = create_texture_view(
                gal,
                &format!("{label}.shadow-color.view"),
                shadow_color_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(shadow_color_view);
            let shadow_light_shaft_view = create_texture_view(
                gal,
                &format!("{label}.shadow-light-shaft.view"),
                shadow_light_shaft_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(shadow_light_shaft_view);
            let albedo_view = create_texture_view(
                gal,
                &format!("{label}.albedo.view"),
                albedo_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(albedo_view);
            let normal_view = create_texture_view(
                gal,
                &format!("{label}.normal.view"),
                normal_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(normal_view);
            let material_light_view = create_texture_view(
                gal,
                &format!("{label}.material-light.view"),
                material_light_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(material_light_view);
            let world_position_view = create_texture_view(
                gal,
                &format!("{label}.world-position.view"),
                world_position_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(world_position_view);
            let deferred_lit_view = create_texture_view(
                gal,
                &format!("{label}.deferred-lit.view"),
                deferred_lit_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(deferred_lit_view);
            let translucent_capture_view = create_texture_view(
                gal,
                &format!("{label}.translucent-capture.view"),
                translucent_capture_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(translucent_capture_view);
            let translucent_capture_depth_view = create_texture_view(
                gal,
                &format!("{label}.translucent-capture-depth.view"),
                translucent_capture_depth_texture,
                TextureFormat::Depth32Float,
            )?;
            created.push(translucent_capture_depth_view);
            let composite0_view = create_texture_view(
                gal,
                &format!("{label}.composite-0.view"),
                composite0_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(composite0_view);
            let composite1_view = create_texture_view(
                gal,
                &format!("{label}.composite-1.view"),
                composite1_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )?;
            created.push(composite1_view);
            let depth_view = create_texture_view(
                gal,
                &format!("{label}.depth.view"),
                depth_texture,
                TextureFormat::Depth32Float,
            )?;
            created.push(depth_view);
            let main_depth_before_translucency_view = create_texture_view(
                gal,
                &format!("{label}.main-depth-before-translucency.view"),
                main_depth_before_translucency_texture,
                TextureFormat::Depth32Float,
            )?;
            created.push(main_depth_before_translucency_view);
            let main_depth_previous_view = create_texture_view(
                gal,
                &format!("{label}.main-depth-previous.view"),
                main_depth_previous_texture,
                TextureFormat::Depth32Float,
            )?;
            created.push(main_depth_previous_view);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            let shadow_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.shadow-target"),
                color_views: vec![shadow_color_view, shadow_light_shaft_view],
                depth_stencil_view: Some(shadow_depth_view),
                extent: shadow_extent,
            })?;
            created.push(shadow_target);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![
                    albedo_view,
                    normal_view,
                    material_light_view,
                    world_position_view,
                ],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(target);
            let deferred_lit_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.deferred-lit-target"),
                color_views: vec![deferred_lit_view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(deferred_lit_target);
            let translucent_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.translucent-target"),
                color_views: vec![deferred_lit_view],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(translucent_target);
            let translucent_capture_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.translucent-capture-target"),
                color_views: vec![translucent_capture_view],
                depth_stencil_view: Some(translucent_capture_depth_view),
                extent,
            })?;
            created.push(translucent_capture_target);
            let composite0_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.composite-0-target"),
                color_views: vec![composite0_view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(composite0_target);
            let composite1_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.composite-1-target"),
                color_views: vec![composite1_view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(composite1_target);
            let shadow_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.shadow-pass"),
                target: shadow_target,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT; 2],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(shadow_pass);
            let g_buffer_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.terrain-pass"),
                target,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT; 4],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(g_buffer_pass);
            let deferred_lighting_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.deferred-lighting-pass"),
                target: deferred_lit_target,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT],
                depth_format: None,
            })?;
            created.push(deferred_lighting_pass);
            let translucent_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.translucent-pass"),
                target: translucent_target,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(translucent_pass);
            let translucent_capture_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.translucent-capture-pass"),
                target: translucent_capture_target,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(translucent_capture_pass);
            let composite0_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.composite-0-pass"),
                target: composite0_target,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT],
                depth_format: None,
            })?;
            created.push(composite0_pass);
            let composite1_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.composite-1-pass"),
                target: composite1_target,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT],
                depth_format: None,
            })?;
            created.push(composite1_pass);
            let composite_uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.deferred-composite-uniforms"),
                size: WORLD_SHADER_COMPOSITE_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Storage,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(composite_uniform_buffer);
            let screen_vertex_program = &shader_plan.programs.deferred_lighting;
            let screen_vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.screen.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    &screen_vertex_program.vertex.source,
                ),
                entry_point: screen_vertex_program.vertex.entry_point.clone(),
            })?;
            created.push(screen_vertex_shader);
            let deferred_lighting_fragment_shader = create_shader_screen_fragment_shader(
                gal,
                &format!("{label}.deferred-lighting"),
                &shader_plan.programs.deferred_lighting,
            )?;
            created.push(deferred_lighting_fragment_shader);
            let composite0_fragment_shader = create_shader_screen_fragment_shader(
                gal,
                &format!("{label}.composite-0"),
                &shader_plan.programs.composite_0,
            )?;
            created.push(composite0_fragment_shader);
            let composite1_fragment_shader = create_shader_screen_fragment_shader(
                gal,
                &format!("{label}.composite-1"),
                &shader_plan.programs.composite_1,
            )?;
            created.push(composite1_fragment_shader);
            let final_fragment_shader = create_shader_screen_fragment_shader(
                gal,
                &format!("{label}.final-output"),
                &shader_plan.programs.final_output,
            )?;
            created.push(final_fragment_shader);
            let screen_resource_layout = create_shader_screen_resource_layout(gal, &label)?;
            created.push(screen_resource_layout);
            let deferred_lighting_resource_set = create_shader_screen_resource_set(
                gal,
                &format!("{label}.deferred-lighting"),
                screen_resource_layout,
                [
                    albedo_view,
                    normal_view,
                    material_light_view,
                    world_position_view,
                    shadow_depth_view,
                ],
                sampler,
                composite_uniform_buffer,
            )?;
            created.push(deferred_lighting_resource_set);
            let composite0_resource_set = create_shader_screen_resource_set(
                gal,
                &format!("{label}.composite-0"),
                screen_resource_layout,
                [
                    deferred_lit_view,
                    normal_view,
                    material_light_view,
                    world_position_view,
                    shadow_depth_view,
                ],
                sampler,
                composite_uniform_buffer,
            )?;
            created.push(composite0_resource_set);
            let composite1_resource_set = create_shader_screen_resource_set(
                gal,
                &format!("{label}.composite-1"),
                screen_resource_layout,
                [
                    composite0_view,
                    normal_view,
                    material_light_view,
                    world_position_view,
                    depth_view,
                ],
                sampler,
                composite_uniform_buffer,
            )?;
            created.push(composite1_resource_set);
            let final_resource_set = create_shader_screen_resource_set(
                gal,
                &format!("{label}.final-output"),
                screen_resource_layout,
                [
                    composite1_view,
                    normal_view,
                    material_light_view,
                    world_position_view,
                    depth_view,
                ],
                sampler,
                composite_uniform_buffer,
            )?;
            created.push(final_resource_set);
            let screen_pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.screen.pipeline-layout"),
                resource_layouts: vec![screen_resource_layout],
            })?;
            created.push(screen_pipeline_layout);
            let deferred_lighting_pipeline = create_shader_screen_pipeline(
                gal,
                &format!("{label}.deferred-lighting"),
                screen_pipeline_layout,
                screen_vertex_shader,
                deferred_lighting_fragment_shader,
                SHADER_G_BUFFER_COLOR_FORMAT,
                None,
            )?;
            created.push(deferred_lighting_pipeline);
            let composite0_pipeline = create_shader_screen_pipeline(
                gal,
                &format!("{label}.composite-0"),
                screen_pipeline_layout,
                screen_vertex_shader,
                composite0_fragment_shader,
                SHADER_G_BUFFER_COLOR_FORMAT,
                None,
            )?;
            created.push(composite0_pipeline);
            let composite1_pipeline = create_shader_screen_pipeline(
                gal,
                &format!("{label}.composite-1"),
                screen_pipeline_layout,
                screen_vertex_shader,
                composite1_fragment_shader,
                SHADER_G_BUFFER_COLOR_FORMAT,
                None,
            )?;
            created.push(composite1_pipeline);
            let final_pipeline = create_shader_screen_pipeline(
                gal,
                &format!("{label}.final-output"),
                screen_pipeline_layout,
                screen_vertex_shader,
                final_fragment_shader,
                frame_color_format,
                None,
            )?;
            created.push(final_pipeline);
            Ok(GBufferResources {
                shadow_depth_texture,
                shadow_depth_opaque_texture,
                shadow_depth_opaque_view,
                shadow_color_texture,
                shadow_light_shaft_texture,
                albedo_texture,
                normal_texture,
                material_light_texture,
                world_position_texture,
                deferred_lit_texture,
                translucent_capture_texture,
                translucent_capture_depth_texture,
                composite0_texture,
                composite1_texture,
                depth_texture,
                main_depth_before_translucency_texture,
                main_depth_previous_texture,
                shadow_depth_view,
                shadow_color_view,
                shadow_light_shaft_view,
                albedo_view,
                normal_view,
                material_light_view,
                world_position_view,
                deferred_lit_view,
                translucent_capture_view,
                translucent_capture_depth_view,
                composite0_view,
                composite1_view,
                depth_view,
                main_depth_before_translucency_view,
                main_depth_previous_view,
                sampler,
                shadow_target,
                target,
                deferred_lit_target,
                translucent_target,
                translucent_capture_target,
                composite0_target,
                composite1_target,
                shadow_pass,
                g_buffer_pass,
                deferred_lighting_pass,
                translucent_pass,
                translucent_capture_pass,
                composite0_pass,
                composite1_pass,
                composite_uniform_buffer,
                screen_vertex_shader,
                deferred_lighting_fragment_shader,
                composite0_fragment_shader,
                composite1_fragment_shader,
                final_fragment_shader,
                screen_resource_layout,
                deferred_lighting_resource_set,
                composite0_resource_set,
                composite1_resource_set,
                screen_pipeline_layout,
                deferred_lighting_pipeline,
                composite0_pipeline,
                composite1_pipeline,
                final_resource_set,
                final_pipeline,
                extent,
                shadow_extent,
                frame_color_format,
                final_depth_format,
                generation: self.generation,
                screen_targets_initialized: false,
                translucent_capture_initialized: false,
                shadow_targets_initialized: false,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.g_buffer_resources = Some(result?);
        profile.g_buffer_attachment_creates =
            profile.g_buffer_attachment_creates.saturating_add(11);
        profile.g_buffer_shader_module_creates =
            profile.g_buffer_shader_module_creates.saturating_add(5);
        profile.g_buffer_descriptor_creates = profile.g_buffer_descriptor_creates.saturating_add(6);
        profile.g_buffer_render_target_creates =
            profile.g_buffer_render_target_creates.saturating_add(6);
        profile.g_buffer_pipeline_creates = profile.g_buffer_pipeline_creates.saturating_add(4);
        profile.world_prepare_g_buffer_create_nanos = profile
            .world_prepare_g_buffer_create_nanos
            .saturating_add(elapsed_nanos_u64(create_started));
        Ok(())
    }

    pub(in crate::render::worldrender) fn ensure_g_buffer_final_binding(
        &mut self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
        frame_color_format: ColorFormat,
        final_depth_view: Option<Handle>,
        profile: &mut WholeFrameProfile,
    ) -> GalResult<GBufferFinalBindingKey> {
        let graph_generation = self
            .g_buffer_resources
            .as_ref()
            .ok_or_else(|| GalError::backend("G-buffer resources missing before final binding"))?
            .generation;
        let final_key_started = std::time::Instant::now();
        let key = GBufferFinalBindingKey {
            frame_target,
            frame_color_format,
            final_depth_view,
            graph_generation,
        };
        // A swapchain may expose a fresh semantic render-target identity on
        // every acquire even when its native image set is unchanged.  Final
        // output passes are target-specific, so retaining every historical
        // identity grows descriptor/render-pass state without bound and can
        // leave the Vulkan driver validating thousands of dead frame targets.
        // The previous frame has crossed the present/retirement boundary by
        // the time the next frame reaches this function; discard stale
        // bindings before looking up the current one.  GAL destruction still
        // uses its submission-aware deferred lifetime, so this does not
        // borrow native handles or weaken synchronization.
        self.retire_stale_g_buffer_final_bindings(gal, key);
        let resources = self
            .g_buffer_resources
            .as_ref()
            .ok_or_else(|| GalError::backend("G-buffer resources missing before final binding"))?;
        profile.world_prepare_g_buffer_final_key_nanos = profile
            .world_prepare_g_buffer_final_key_nanos
            .saturating_add(elapsed_nanos_u64(final_key_started));
        let final_lookup_started = std::time::Instant::now();
        if self.g_buffer_final_bindings.contains_key(&key) {
            profile.g_buffer_final_binding_cache_hits =
                profile.g_buffer_final_binding_cache_hits.saturating_add(1);
            profile.world_prepare_g_buffer_final_lookup_nanos = profile
                .world_prepare_g_buffer_final_lookup_nanos
                .saturating_add(elapsed_nanos_u64(final_lookup_started));
            return Ok(key);
        }
        profile.g_buffer_final_binding_cache_misses = profile
            .g_buffer_final_binding_cache_misses
            .saturating_add(1);
        profile.world_prepare_g_buffer_final_lookup_nanos = profile
            .world_prepare_g_buffer_final_lookup_nanos
            .saturating_add(elapsed_nanos_u64(final_lookup_started));
        let label = format!("world-shader-g-buffer-gen{}", resources.generation);
        let mut created = Vec::new();
        let final_create_started = std::time::Instant::now();
        let result = (|| -> GalResult<GBufferFinalBindingResources> {
            let final_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.final-output-pass"),
                target: frame_target,
                color_formats: vec![frame_color_format],
                // The final fullscreen shader samples the main depth texture
                // but performs no depth test. Do not declare a depth format
                // on this pass; attaching the same image would create an
                // illegal sampled/attachment feedback layout on Vulkan.
                depth_format: None,
            })?;
            created.push(final_pass);
            Ok(GBufferFinalBindingResources { final_pass })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.g_buffer_final_bindings.insert(key, result?);
        profile.g_buffer_final_pass_creates = profile.g_buffer_final_pass_creates.saturating_add(1);
        profile.world_prepare_g_buffer_final_create_nanos = profile
            .world_prepare_g_buffer_final_create_nanos
            .saturating_add(elapsed_nanos_u64(final_create_started));
        Ok(key)
    }

    pub(in crate::render::worldrender) fn retire_stale_g_buffer_final_bindings(
        &mut self,
        gal: &mut VulkanicGal,
        current: GBufferFinalBindingKey,
    ) {
        let stale = self
            .g_buffer_final_bindings
            .keys()
            .filter(|key| **key != current)
            .cloned()
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(binding) = self.g_buffer_final_bindings.remove(&key) {
                destroy_g_buffer_final_binding_handles(gal, binding);
                self.pending_g_buffer_resources_retired =
                    self.pending_g_buffer_resources_retired.saturating_add(1);
            }
        }
    }

    pub(in crate::render::worldrender) fn destroy_g_buffer_resources(&mut self, gal: &mut VulkanicGal) -> u64 {
        let mut retired = self.destroy_g_buffer_final_bindings(gal);
        // Source final-output plans now own intermediate color targets bound
        // to this graph's main depth view. They must retire before the graph
        // depth target, just like the normal source terrain target/pass pairs.
        self.source_final_output_cache.destroy(gal);
        // Normal source terrain target/pass pairs reference the current
        // Rust-owned graph depth view. Retire them before replacing that
        // graph generation, even though the named color images are owned by
        // the shader runtime and may survive independently.
        self.destroy_source_terrain_color_pass_targets(gal);
        self.g_buffer_depth_history = GBufferDepthHistoryState::default();
        self.discard_pending_g_buffer_depth_history_submission();
        // The private source wrapper owns comparison samplers that reference
        // this shadow-target generation, so retire all source shadow wrappers
        // before their backing target views.
        self.clear_candidate_source_shadow_depth_resources(gal);
        self.clear_candidate_source_shadow_color_resources(gal);
        self.clear_candidate_source_main_depth_resources(gal);
        self.clear_candidate_source_resource_snapshot();
        if let Some(resources) = self.g_buffer_resources.take() {
            retired = retired.saturating_add(1);
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        retired
    }

    pub(in crate::render::worldrender) fn destroy_g_buffer_final_bindings(&mut self, gal: &mut VulkanicGal) -> u64 {
        let bindings = std::mem::take(&mut self.g_buffer_final_bindings);
        let retired = bindings.len() as u64;
        for (_, binding) in bindings {
            destroy_g_buffer_final_binding_handles(gal, binding);
        }
        retired
    }

    pub(in crate::render::worldrender) fn destroy_g_buffer_final_binding(
        &mut self,
        gal: &mut VulkanicGal,
        key: GBufferFinalBindingKey,
    ) -> u64 {
        if let Some(binding) = self.g_buffer_final_bindings.remove(&key) {
            destroy_g_buffer_final_binding_handles(gal, binding);
            1
        } else {
            0
        }
    }
}

pub(in crate::render::worldrender) fn destroy_g_buffer_final_binding_handles(
    gal: &mut VulkanicGal,
    binding: GBufferFinalBindingResources,
) {
    for handle in binding.handles_in_destroy_order() {
        let _ = gal.destroy(handle);
    }
}

pub(in crate::render::worldrender) fn create_g_buffer_color_texture(
    gal: &mut VulkanicGal,
    label: &str,
    extent: Extent3d,
) -> GalResult<Handle> {
    // Shader-pack terrain lighting is HDR.  Complementary's terrain pass can
    // legitimately produce values above one before its deferred/composite
    // tone mapping; an 8-bit UNORM attachment clamps those values and turns
    // the entire daylight terrain into a pale white sheet.  Keep the graph's
    // intermediate color domain float so the Rust/Vulkan route has the same
    // precision contract as the Frozen OpenGL shader targets.  The acquired
    // presentation target remains its normal swapchain format.
    gal.create_texture(TextureDesc {
        label: format!("{label}.texture"),
        dimension: TextureDimension::D2,
        format: SHADER_G_BUFFER_COLOR_FORMAT,
        extent,
        mip_levels: 1,
        array_layers: 1,
        usages: vec![
            TextureUsage::ColorAttachment,
            TextureUsage::Sampled,
            TextureUsage::TransferSrc,
        ],
    })
}

pub(in crate::render::worldrender) const SHADER_G_BUFFER_COLOR_FORMAT: TextureFormat = TextureFormat::Rgba16Float;

pub(in crate::render::worldrender) fn create_shader_screen_resource_layout(gal: &mut VulkanicGal, label: &str) -> GalResult<Handle> {
    gal.create_resource_layout(ResourceLayoutDesc {
        label: format!("{label}.screen.resource-layout"),
        bindings: vec![
            ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::SampledTexture,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 1,
                kind: ResourceBindingKind::SampledTexture,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 2,
                kind: ResourceBindingKind::SampledTexture,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 3,
                kind: ResourceBindingKind::SampledTexture,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 4,
                kind: ResourceBindingKind::SampledTexture,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 5,
                kind: ResourceBindingKind::Sampler,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 6,
                kind: ResourceBindingKind::StorageBuffer,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
        ],
    })
}

pub(in crate::render::worldrender) fn create_shader_screen_resource_set(
    gal: &mut VulkanicGal,
    label: &str,
    layout: Handle,
    sampled_views: [Handle; 5],
    sampler: Handle,
    uniform_buffer: Handle,
) -> GalResult<Handle> {
    gal.create_resource_set(ResourceSetDesc {
        label: format!("{label}.resource-set"),
        layout,
        bindings: vec![
            sampled_binding(0, sampled_views[0]),
            sampled_binding(1, sampled_views[1]),
            sampled_binding(2, sampled_views[2]),
            sampled_binding(3, sampled_views[3]),
            sampled_binding(4, sampled_views[4]),
            sampler_binding(5, sampler),
            ResourceBinding {
                binding: 6,
                array_index: 0,
                resource: uniform_buffer,
                kind: ResourceBindingKind::StorageBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
        ],
    })
}

pub(in crate::render::worldrender) fn create_shader_screen_fragment_shader(
    gal: &mut VulkanicGal,
    label: &str,
    program: &CompositeProgram,
) -> GalResult<Handle> {
    gal.create_shader_module(ShaderModuleDesc {
        label: format!("{label}.fragment"),
        stage: ShaderStage::Fragment,
        code_format: ShaderCodeFormat::Glsl,
        code: shader_stage_code(gal.capabilities().shader_conventions, &program.fragment.source),
        entry_point: program.fragment.entry_point.clone(),
    })
}

pub(in crate::render::worldrender) fn create_shader_screen_pipeline(
    gal: &mut VulkanicGal,
    label: &str,
    layout: Handle,
    vertex_shader: Handle,
    fragment_shader: Handle,
    color_format: ColorFormat,
    depth_format: Option<TextureFormat>,
) -> GalResult<Handle> {
    gal.create_graphics_pipeline(GraphicsPipelineDesc {
        label: format!("{label}.pipeline"),
        layout,
        vertex_shader,
        fragment_shader,
        topology: PrimitiveTopology::Triangles,
        cull_mode: CullMode::None,
        front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
        provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
        raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
        blend: BlendMode::Disabled,
        depth_compare: None,
        depth_write: false,
        depth_bias: None,
        color_formats: vec![color_format],
        depth_format,
        stencil: None,
    })
}

pub(in crate::render::worldrender) fn terrain_material_pass_mode(material_mode: u32) -> GalResult<TerrainMaterialPassMode> {
    match material_mode {
        WORLD_MATERIAL_MODE_OPAQUE => Ok(TerrainMaterialPassMode::Opaque),
        WORLD_MATERIAL_MODE_CUTOUT => Ok(TerrainMaterialPassMode::Cutout),
        WORLD_MATERIAL_MODE_GLINT => Ok(TerrainMaterialPassMode::Cutout),
        WORLD_MATERIAL_MODE_TRANSLUCENT | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT => {
            Ok(TerrainMaterialPassMode::Translucent)
        }
        other => Err(GalError::invalid_argument(format!(
            "unsupported terrain material pass mode {other}"
        ))),
    }
}

/// Selects only explicit source-program raster semantics. The normal terrain
/// source retains its solid/cutout depth writer; the separate translucent
/// source must have carried an explicit alpha-blend declaration while it was
/// parsed from the selected pack. This function has no backend state and is
/// shared by the eventual Vulkan/OpenGL lowering path.
pub(in crate::render::worldrender) fn source_terrain_pipeline_raster_state(
    program: &LoweredTerrainSourceProgram,
    material_mode: u32,
) -> GalResult<(BlendMode, bool)> {
    if program.terrain_output_color_slots().is_none()
        && material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
    {
        // The selected shadow-water stage replaces shadow colors and writes
        // depth. Iris disables blending for this pass by default; its
        // translucency belongs to the shader's outputs, not framebuffer blend.
        return Ok((BlendMode::Disabled, true));
    }
    match material_mode {
        WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT => {
            if program.translucent_raster_state().is_some() {
                return Err(GalError::invalid_argument(
                    "translucent source program cannot prepare opaque or cutout terrain sections",
                ));
            }
            Ok((BlendMode::Disabled, true))
        }
        WORLD_MATERIAL_MODE_TRANSLUCENT => program
            .translucent_blend_mode()
            .map(|blend| {
                // The source translucent shader writes both the color and a
                // revealage/multiplier auxiliary output. Only the primary
                // color is alpha-composited; auxiliary outputs must replace
                // their clear value. Vulkan expresses that MRT distinction
                // explicitly while OpenGL retains its equivalent legacy path.
                let blend = if blend == BlendMode::Alpha {
                    BlendMode::AlphaFirstAttachmentOnly
                } else {
                    blend
                };
                // Sodium's translucent layer uses vanilla `RenderPipelines.
                // TRANSLUCENT`, which writes depth; Iris keeps that mask for
                // `gbuffers_water`. The main depth therefore carries glass and
                // water, so depthtex0 differs from the pre-translucent
                // depthtex1 and later writers (outlines) are occluded by it.
                (blend, true)
            })
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "translucent terrain source requires an explicit source alpha/blend raster contract",
                )
            }),
        other => Err(GalError::invalid_argument(format!(
            "unsupported source terrain material mode {other}"
        ))),
    }
}

pub(in crate::render::worldrender) fn source_terrain_translucent_phase(has_bootstrap_batches: bool) -> TerrainSourceColorPassPhase {
    if has_bootstrap_batches {
        TerrainSourceColorPassPhase::Translucent
    } else {
        TerrainSourceColorPassPhase::TranslucentFirst
    }
}

pub(in crate::render::worldrender) fn terrain_runtime_targets(
    resources: &GBufferResources,
    final_binding: &GBufferFinalBindingResources,
) -> TerrainRuntimeTargets {
    TerrainRuntimeTargets {
        shadow_depth_texture: resources.shadow_depth_texture,
        shadow_depth_opaque_texture: resources.shadow_depth_opaque_texture,
        shadow_extent: resources.shadow_extent,
        shadow_depth_view: resources.shadow_depth_view,
        shadow_color_texture: resources.shadow_color_texture,
        shadow_color_view: resources.shadow_color_view,
        shadow_light_shaft_texture: resources.shadow_light_shaft_texture,
        shadow_light_shaft_view: resources.shadow_light_shaft_view,
        shadow_target: resources.shadow_target,
        shadow_pass: resources.shadow_pass,
        albedo_texture: resources.albedo_texture,
        albedo_view: resources.albedo_view,
        normal_texture: resources.normal_texture,
        normal_view: resources.normal_view,
        material_light_texture: resources.material_light_texture,
        material_light_view: resources.material_light_view,
        world_position_texture: resources.world_position_texture,
        world_position_view: resources.world_position_view,
        depth_texture: resources.depth_texture,
        depth_view: resources.depth_view,
        depth_history: TerrainDepthHistoryTargets {
            main_depth_texture: resources.depth_texture,
            before_translucency_texture: resources.main_depth_before_translucency_texture,
            previous_texture: resources.main_depth_previous_texture,
        },
        target: resources.target,
        g_buffer_pass: resources.g_buffer_pass,
        deferred_lit_texture: resources.deferred_lit_texture,
        deferred_lit_view: resources.deferred_lit_view,
        deferred_lit_target: resources.deferred_lit_target,
        deferred_lighting_pass: resources.deferred_lighting_pass,
        deferred_lighting_pipeline: resources.deferred_lighting_pipeline,
        deferred_lighting_resource_set: resources.deferred_lighting_resource_set,
        translucent_target: resources.translucent_target,
        translucent_pass: resources.translucent_pass,
        translucent_capture: Some(TerrainTranslucentCaptureTargets {
            extent: resources.extent,
            color_texture: resources.translucent_capture_texture,
            color_view: resources.translucent_capture_view,
            depth_texture: resources.translucent_capture_depth_texture,
            depth_view: resources.translucent_capture_depth_view,
            target: resources.translucent_capture_target,
            pass: resources.translucent_capture_pass,
        }),
        translucent_capture_initialized: resources.translucent_capture_initialized,
        composite0_texture: resources.composite0_texture,
        composite0_view: resources.composite0_view,
        composite0_target: resources.composite0_target,
        composite0_pass: resources.composite0_pass,
        composite0_pipeline: resources.composite0_pipeline,
        composite0_resource_set: resources.composite0_resource_set,
        composite1_texture: resources.composite1_texture,
        composite1_view: resources.composite1_view,
        composite1_target: resources.composite1_target,
        composite1_pass: resources.composite1_pass,
        composite1_pipeline: resources.composite1_pipeline,
        composite1_resource_set: resources.composite1_resource_set,
        final_pass: final_binding.final_pass,
        final_pipeline: resources.final_pipeline,
        final_resource_set: resources.final_resource_set,
        screen_pipeline_layout: resources.screen_pipeline_layout,
        composite_uniform_buffer: resources.composite_uniform_buffer,
        shadow_targets_initialized: resources.shadow_targets_initialized,
    }
}

pub(in crate::render::worldrender) fn terrain_source_shadow_pass_targets(
    resources: &GBufferResources,
) -> TerrainSourceShadowPassTargets {
    TerrainSourceShadowPassTargets {
        shadow_depth_texture: resources.shadow_depth_texture,
        shadow_depth_opaque_texture: resources.shadow_depth_opaque_texture,
        shadow_extent: resources.shadow_extent,
        shadow_depth_view: resources.shadow_depth_view,
        shadow_color_texture: resources.shadow_color_texture,
        shadow_color_view: resources.shadow_color_view,
        shadow_light_shaft_texture: resources.shadow_light_shaft_texture,
        shadow_light_shaft_view: resources.shadow_light_shaft_view,
        shadow_target: resources.shadow_target,
        shadow_pass: resources.shadow_pass,
        initialized: resources.shadow_targets_initialized,
    }
}

pub(in crate::render::worldrender) fn shader_shadow_params(enabled: bool) -> [f32; 4] {
    // Keep the explicit orthographic shadow domain large enough to cover a
    // full visible section while retaining raster coverage at the bounded
    // shadow target resolution. The same range is consumed by the world
    // position packing and deferred comparison below.
    [if enabled { 1.0 } else { 0.0 }, 0.006, 0.42, 64.0]
}
