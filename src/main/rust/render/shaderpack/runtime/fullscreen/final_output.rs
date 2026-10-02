//! Final-output identity and reservation, overlay targets, and the final copy to the frame.

use super::*;

/// Stable, backend-neutral compatibility identity for one final source-copy
/// binding. `render_target` is the acquired frame slot identity supplied by
/// GAL, not a native image/view or a transient GAL handle.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) struct SourceFinalOutputIdentity {
    pub world_generation: u64,
    pub shader_pack_generation: u64,
    /// The source main-depth generation is part of the compatibility contract
    /// for the private overlay target. A swapchain slot can be reused across a
    /// resize or graph rebuild, but it may not retain a target that borrows an
    /// obsolete depth view.
    pub graph_generation: u64,
    pub source_role: TerrainSourceResourceRole,
    pub render_target: crate::render::vulkanic::frame::FrameRenderTargetId,
    pub extent: [u32; 3],
    pub color_format: crate::render::vulkanic::resources::TextureFormat,
}

/// A frame-local reservation into the persistent final-output cache. A newly
/// staged entry is removed on a later frame-recording failure; confirmed
/// entries remain owned by the cache until their source generation retires.
#[derive(Clone, Debug)]
pub(crate) struct SourceFinalOutputReservation {
    pub(super) identity: SourceFinalOutputIdentity,
    pub(super) newly_staged: bool,
}

impl SourceFinalOutputReservation {
    pub(crate) fn identity(&self) -> &SourceFinalOutputIdentity {
        &self.identity
    }

    pub(crate) fn newly_staged(&self) -> bool {
        self.newly_staged
    }
}

impl SourceFinalOutputIdentity {
    pub(super) const fn extent3d(&self) -> crate::render::vulkanic::resources::Extent3d {
        crate::render::vulkanic::resources::Extent3d {
            width: self.extent[0],
            height: self.extent[1],
            depth: self.extent[2],
        }
    }
}

/// Persistent final-copy bindings keyed only by semantic compatibility facts.
/// In particular, swapchain image rotation is represented by GAL's stable
/// `FrameRenderTargetId`, never a native image/view or transient GAL handle.
#[derive(Default)]
pub(crate) struct SourceFinalOutputCache {
    pub(super) plans: BTreeMap<SourceFinalOutputIdentity, SourceFinalOutputPlan>,
}

/// Private Rust-owned color target for source-frame world overlays. It pairs
/// an owned color image with an already-owned source depth view, so overlays
/// can retain depth testing without pretending an acquired swapchain image
/// has a depth attachment. The caller controls pass ordering and final
/// presentation; this type only owns the explicit target resources.
#[derive(Debug)]
pub(crate) struct SourceOverlayTarget {
    pub(super) target: Handle,
    pub(super) color_texture: Handle,
    pub(super) color_view: Handle,
    pub(super) depth_view: Handle,
    pub(super) extent: crate::render::vulkanic::resources::Extent3d,
    pub(super) color_format: TextureFormat,
}

impl SourceOverlayTarget {
    pub(crate) fn stage(
        gal: &mut VulkanicGal,
        label: &str,
        extent: crate::render::vulkanic::resources::Extent3d,
        color_format: TextureFormat,
        depth_view: Handle,
    ) -> GalResult<Self> {
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "source overlay target requires a non-zero two-dimensional extent",
            ));
        }
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let color_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.color"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                // The normal source path writes this target as a color attachment
                // and samples it for the final copy. Bounded source diagnostics may
                // additionally read it back after the source chain completes.
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(color_texture);
            let color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.color-view"),
                texture: color_texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(color_view);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![color_view],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(target);
            Ok(Self {
                target,
                color_texture,
                color_view,
                depth_view,
                extent,
                color_format,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(crate) const fn target(&self) -> Handle {
        self.target
    }

    pub(crate) const fn color_view(&self) -> Handle {
        self.color_view
    }

    pub(crate) const fn color_texture(&self) -> Handle {
        self.color_texture
    }

    pub(crate) const fn depth_view(&self) -> Handle {
        self.depth_view
    }

    pub(crate) const fn extent(&self) -> crate::render::vulkanic::resources::Extent3d {
        self.extent
    }

    pub(crate) const fn color_format(&self) -> TextureFormat {
        self.color_format
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [self.target, self.color_view, self.color_texture] {
            let _ = gal.destroy(handle);
        }
    }
}

/// One transient Rust-owned presentation node for a completed source frame.
/// The selected pack's `final` source stage first writes its declared named
/// color output; this node samples that semantic result and writes the
/// acquired backend-owned frame target. It contains only GAL handles and a
/// pack-level role, never a Java/Iris target, program, or native handle.
#[derive(Debug)]
pub(crate) struct SourceFinalOutputPlan {
    pub(super) identity: SourceFinalOutputIdentity,
    pub(super) source_role: TerrainSourceResourceRole,
    pub(super) frame_target: Handle,
    pub(super) frame_format: TextureFormat,
    pub(super) overlay: SourceOverlayTarget,
    pub(super) overlay_pass: Handle,
    pub(super) source_copy: SourceColorCopyPlan,
    pub(super) present_copy: SourceColorCopyPlan,
}

/// Frame-local diagnostic mirror of the final present copy. It deliberately
/// uses the identical owned source and copy program as the acquired-target
/// presentation path, so a capture can prove the displayed image without
/// reading back a swapchain image or exposing backend objects.
#[derive(Debug)]
pub(crate) struct SourceFinalPresentationCapture {
    pub(super) target: Handle,
    pub(super) color_texture: Handle,
    pub(super) color_view: Handle,
    pub(super) format: TextureFormat,
    pub(super) copy: SourceColorCopyPlan,
}

/// One explicit sampled-color copy. It is deliberately private to the
/// source-frame presenter: callers only compose semantic source stages and
/// never see the target's backend state or native identity.
#[derive(Debug)]
pub(super) struct SourceColorCopyPlan {
    pub(super) target: Handle,
    pub(super) color_attachment: Handle,
    pub(super) depth_attachment: Option<Handle>,
    pub(super) pass: Handle,
    pub(super) resource_layout: Handle,
    pub(super) pipeline_layout: Handle,
    pub(super) resource_set: Handle,
    pub(super) sampler: Handle,
    pub(super) combined_sampler: Handle,
    pub(super) vertex_shader: Handle,
    pub(super) fragment_shader: Handle,
    pub(super) pipeline: Handle,
}

pub(super) struct SourceFinalOutputStageInput {
    pub(super) identity: SourceFinalOutputIdentity,
    pub(super) source_role: TerrainSourceResourceRole,
    pub(super) source_view: Handle,
    pub(super) source_name: String,
    pub(super) frame_target: Handle,
    pub(super) color_attachment: Handle,
    pub(super) frame_format: crate::render::vulkanic::resources::TextureFormat,
    pub(super) source_format: crate::render::vulkanic::resources::TextureFormat,
    pub(super) main_depth_view: Handle,
}

impl SourceFinalOutputPlan {
    /// Stages the explicit last copy from the selected source final stage to
    /// one acquired Rust frame target. The source program must expose exactly
    /// one location-zero named color output; multi-output or nonzero final
    /// stages require a richer semantic presentation contract and are
    /// rejected instead of being guessed at.
    pub(crate) fn stage(
        gal: &mut VulkanicGal,
        final_program: &LoweredFullscreenSourceProgram,
        targets: &ShaderPackColorTargets,
        frame_target: Handle,
        color_attachment: Handle,
        main_depth_view: Handle,
        graph_generation: u64,
    ) -> GalResult<Self> {
        let input = Self::stage_input(
            gal,
            final_program,
            targets,
            frame_target,
            color_attachment,
            main_depth_view,
            graph_generation,
        )?;
        Self::stage_from_input(gal, input)
    }

    pub(super) fn stage_input(
        gal: &VulkanicGal,
        final_program: &LoweredFullscreenSourceProgram,
        targets: &ShaderPackColorTargets,
        frame_target: Handle,
        color_attachment: Handle,
        main_depth_view: Handle,
        graph_generation: u64,
    ) -> GalResult<SourceFinalOutputStageInput> {
        if frame_target.kind() != Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
            return Err(GalError::invalid_argument(
                "source final output requires an acquired GAL frame target",
            ));
        }
        if color_attachment != frame_target {
            return Err(GalError::invalid_argument(
                "source final output must use the acquired frame target as its color attachment",
            ));
        }
        let frame_target_desc = gal.frame_target_desc(frame_target)?;
        let outputs = &final_program.outputs;
        let [output] = outputs.as_slice() else {
            return Err(GalError::unsupported_feature(format!(
                "source final program '{}' must declare exactly one named color output",
                final_program.identity.as_str()
            )));
        };
        if output.source_location() != 0 {
            return Err(GalError::unsupported_feature(format!(
                "source final program '{}' writes location {}; only semantic final location zero is supported",
                final_program.identity.as_str(),
                output.source_location()
            )));
        }
        let source_role = output.role();
        let source_name = source_role
            .shader_pack_color_name()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source final program output is not a named shader-pack color role",
                )
            })?
            .to_string();
        let source = targets.target(&source_name).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "source final output '{}' has no staged semantic color target",
                source_name
            ))
        })?;
        // `SourceOverlayTarget::stage` validates the exact depth view against
        // the render target. The source runtime owns and already validates the
        // main-depth semantic resource; this presentation helper intentionally
        // does not need a general texture-view inspection API.
        if frame_target_desc.extent != targets.identity.extent {
            return Err(GalError::invalid_argument(format!(
                "source final output extent {:?} does not match acquired frame target extent {:?}",
                targets.identity.extent, frame_target_desc.extent
            )));
        }
        let frame_format = frame_target_desc.color_format;
        let identity = SourceFinalOutputIdentity {
            world_generation: targets.identity.world_generation,
            shader_pack_generation: targets.identity.shader_pack_generation,
            graph_generation,
            source_role: source_role.clone(),
            render_target: frame_target_desc.render_target,
            extent: [
                frame_target_desc.extent.width,
                frame_target_desc.extent.height,
                frame_target_desc.extent.depth,
            ],
            color_format: frame_format,
        };
        Ok(SourceFinalOutputStageInput {
            identity,
            source_role,
            source_view: source.current_view,
            source_name,
            frame_target,
            color_attachment,
            frame_format,
            source_format: source.format,
            main_depth_view,
        })
    }

    pub(super) fn stage_from_input(
        gal: &mut VulkanicGal,
        input: SourceFinalOutputStageInput,
    ) -> GalResult<Self> {
        let SourceFinalOutputStageInput {
            identity,
            source_role,
            source_view,
            source_name,
            frame_target,
            color_attachment,
            frame_format,
            source_format,
            main_depth_view,
        } = input;
        let label = format!(
            "source-final-output.world{}-pack{}.{}",
            identity.world_generation, identity.shader_pack_generation, source_name
        );
        let overlay = SourceOverlayTarget::stage(
            gal,
            &format!("{label}.overlay"),
            identity.extent3d(),
            source_format,
            main_depth_view,
        )?;
        let overlay_pass = match gal.create_render_pass(RenderPassDesc {
            label: format!("{label}.overlay.pass"),
            target: overlay.target(),
            color_formats: vec![source_format],
            depth_format: Some(TextureFormat::Depth32Float),
        }) {
            Ok(pass) => pass,
            Err(error) => {
                overlay.destroy(gal);
                return Err(error);
            }
        };
        let source_copy = match SourceColorCopyPlan::stage(
            gal,
            &format!("{label}.source-copy"),
            source_view,
            overlay.target(),
            overlay.color_view(),
            source_format,
            Some(overlay.depth_view()),
        ) {
            Ok(copy) => copy,
            Err(error) => {
                let _ = gal.destroy(overlay_pass);
                overlay.destroy(gal);
                return Err(error);
            }
        };
        let present_copy = match SourceColorCopyPlan::stage(
            gal,
            &format!("{label}.present-copy"),
            overlay.color_view(),
            frame_target,
            color_attachment,
            frame_format,
            None,
        ) {
            Ok(copy) => copy,
            Err(error) => {
                source_copy.destroy(gal);
                let _ = gal.destroy(overlay_pass);
                overlay.destroy(gal);
                return Err(error);
            }
        };
        Ok(Self {
            identity,
            source_role,
            frame_target,
            frame_format,
            overlay,
            overlay_pass,
            source_copy,
            present_copy,
        })
    }

    /// Appends the final source color copy after the source color transaction
    /// has made the selected output shader-readable. This neither submits nor
    /// presents; the owning frame coordinator remains solely responsible for
    /// the single Rust submission and present.
    pub(crate) fn append_source_copy(&self, operations: &mut Vec<CommandOp>) {
        // A newly staged overlay has no prior attachment writer. Establish
        // the explicit color-attachment layout before the first source copy;
        // the final present copy below returns it to sampled state.
        operations.push(CommandOp::Barrier(texture_barrier(
            self.overlay.color_texture(),
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )));
        // Source fullscreen consumers may have sampled the exact main depth
        // before the final color copy. Reattaching it to the private overlay
        // target is an explicit semantic transition; it is not an implicit
        // backend layout guess.
        operations.push(CommandOp::Barrier(texture_barrier(
            self.overlay.depth_view(),
            TextureUsageState::ShaderRead,
            TextureUsageState::DepthStencilAttachment,
        )));
        self.source_copy.append_draw(operations);
    }

    /// Same source copy with the actual prior semantic state of a cached
    /// frame-slot overlay color. Newly staged colors begin Undefined;
    /// confirmed colors return from the prior present copy in ShaderRead.
    /// The borrowed main depth was already consumed by this frame's source
    /// passes, independently of whether the overlay color is newly staged.
    pub(crate) fn append_source_copy_from_state(
        &self,
        operations: &mut Vec<CommandOp>,
        overlay_before: TextureUsageState,
    ) {
        operations.push(CommandOp::Barrier(texture_barrier(
            self.overlay.color_texture(),
            overlay_before,
            TextureUsageState::ColorAttachment,
        )));
        operations.push(CommandOp::Barrier(texture_barrier(
            self.overlay.depth_view(),
            TextureUsageState::ShaderRead,
            TextureUsageState::DepthStencilAttachment,
        )));
        self.source_copy.append_draw(operations);
    }

    /// Returns the private target after the selected source chain has copied
    /// its final named color into it. World overlays may be added between this
    /// copy and `append_present_copy`, retaining the source main depth without
    /// exposing backend state through a frontend or Java boundary.
    pub(crate) fn overlay_target(&self) -> Handle {
        self.overlay.target()
    }

    /// The acquired Rust-owned final target after the source color has been
    /// presented into it. Screen-space GUI is composed here so the source
    /// color's framebuffer-to-sampled-image row conversion cannot invert
    /// top-left GUI coordinates. This is still part of the one Rust command
    /// stream and does not expose a backend target identity outside the
    /// source-frame presenter.
    pub(crate) fn presentation_target(&self) -> Handle {
        self.frame_target
    }

    pub(crate) fn overlay_color_attachment(&self) -> Handle {
        self.overlay.color_view()
    }

    /// The private source result is readable only by the Rust source-frame
    /// owner. This exists for bounded selected-source diagnostics; callers
    /// still receive no backend target identity or presentation control.
    pub(crate) fn overlay_color_texture(&self) -> Handle {
        self.overlay.color_texture()
    }

    pub(crate) fn overlay_depth_attachment(&self) -> Handle {
        self.overlay.depth_view()
    }

    #[allow(dead_code)]
    pub(crate) fn overlay_pass(&self) -> Handle {
        self.overlay_pass
    }

    pub(crate) fn overlay_color_format(&self) -> TextureFormat {
        self.overlay.color_format()
    }

    pub(crate) fn overlay_extent(&self) -> crate::render::vulkanic::resources::Extent3d {
        self.overlay.extent()
    }

    pub(crate) fn append_present_copy(&self, operations: &mut Vec<CommandOp>) {
        // The source copy and any following world overlays write the same
        // private color attachment. Transition it exactly once after the last
        // attachment writer, immediately before the present copy samples it.
        operations.push(CommandOp::Barrier(texture_barrier(
            self.overlay.color_texture(),
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
        self.present_copy.append_draw(operations);
    }

    /// Stages a bounded diagnostic target that receives the same final copy as
    /// the acquired frame target. This has no presenter, no Java dependency,
    /// and exists only for a selected capture frame.
    pub(crate) fn stage_presented_capture(
        &self,
        gal: &mut VulkanicGal,
        label: &str,
    ) -> GalResult<SourceFinalPresentationCapture> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<SourceFinalPresentationCapture> {
            let color_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.color"),
                dimension: TextureDimension::D2,
                // This mirror represents the acquired presentation target,
                // not the source overlay. Its format must match the final
                // target so diagnostic GUI replay cannot hide conversion.
                format: self.frame_format,
                extent: self.overlay.extent(),
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::ColorAttachment, TextureUsage::TransferSrc],
            })?;
            created.push(color_texture);
            let color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.color-view"),
                texture: color_texture,
                format: self.frame_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(color_view);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![color_view],
                depth_stencil_view: None,
                extent: self.overlay.extent(),
            })?;
            created.push(target);
            let copy = SourceColorCopyPlan::stage(
                gal,
                &format!("{label}.copy"),
                self.overlay.color_view(),
                target,
                color_view,
                self.frame_format,
                None,
            )?;
            Ok(SourceFinalPresentationCapture {
                target,
                color_texture,
                color_view,
                format: self.frame_format,
                copy,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(crate) fn source_role(&self) -> &TerrainSourceResourceRole {
        &self.source_role
    }

    pub(crate) fn identity(&self) -> &SourceFinalOutputIdentity {
        &self.identity
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        self.present_copy.destroy(gal);
        self.source_copy.destroy(gal);
        let _ = gal.destroy(self.overlay_pass);
        self.overlay.destroy(gal);
    }
}

impl SourceFinalPresentationCapture {
    /// Frame-local Rust-owned target used only to retain a semantic replay of
    /// the final presentation image for one selected diagnostic frame.
    pub(crate) fn target(&self) -> Handle {
        self.target
    }

    pub(crate) fn color_attachment(&self) -> Handle {
        self.color_view
    }

    pub(crate) fn color_texture(&self) -> Handle {
        self.color_texture
    }

    pub(crate) fn format(&self) -> TextureFormat {
        self.format
    }

    pub(crate) fn append_draw(&self, operations: &mut Vec<CommandOp>) {
        // The mirror is frame-local and its first writer is the exact final
        // presentation copy. Establish the target attachment layout explicitly
        // before that draw; the subsequent GUI replay remains in the same
        // color-attachment state until the readback path transitions it.
        operations.push(CommandOp::Barrier(texture_barrier(
            self.color_texture,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )));
        self.copy.append_draw(operations);
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        self.copy.destroy(gal);
        for handle in [self.target, self.color_view, self.color_texture] {
            let _ = gal.destroy(handle);
        }
    }
}

impl SourceColorCopyPlan {
    pub(super) fn stage(
        gal: &mut VulkanicGal,
        label: &str,
        source_view: Handle,
        target: Handle,
        color_attachment: Handle,
        color_format: TextureFormat,
        depth_attachment: Option<Handle>,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
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
            let combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label}.combined-sampler"),
                    texture_view: source_view,
                    sampler,
                })?;
            created.push(combined_sampler);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                }],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: resource_layout,
                bindings: vec![ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: combined_sampler,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                }],
            })?;
            created.push(resource_set);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let [vertex_desc, fragment_desc] =
                source_final_copy_shader_modules(gal.capabilities().shader_conventions, label);
            let vertex_shader = gal.create_shader_module(vertex_desc)?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(fragment_desc)?;
            created.push(fragment_shader);
            let depth_format = depth_attachment.map(|_| TextureFormat::Depth32Float);
            let pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.pass"),
                target,
                color_formats: vec![color_format],
                depth_format,
            })?;
            created.push(pass);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
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
            })?;
            created.push(pipeline);
            Ok(Self {
                target,
                color_attachment,
                depth_attachment,
                pass,
                resource_layout,
                pipeline_layout,
                resource_set,
                sampler,
                combined_sampler,
                vertex_shader,
                fragment_shader,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(super) fn append_draw(&self, operations: &mut Vec<CommandOp>) {
        operations.push(CommandOp::BeginPass {
            pass: self.pass,
            target: self.target,
            colors: vec![PassAttachment {
                view: self.color_attachment,
                load_op: AttachmentLoadOp::DontCare,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: self.depth_attachment.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.pipeline,
            self.pass,
            self.fragment_shader,
            self.vertex_shader,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.combined_sampler,
            self.sampler,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}

impl SourceFinalOutputCache {
    /// Keep a small retirement-safe history for a reused frame slot.  A few
    /// generations may overlap while the backend drains in-flight work, but
    /// the cache must not grow with every graph rebuild or world generation.
    pub(super) const MAX_PLANS_PER_FRAME_TARGET: usize = 8;

    /// Ensures one persistent final-copy binding for a semantic source output
    /// and acquired swapchain image slot. Validation happens before cache
    /// lookup so an otherwise warm cache cannot conceal an incompatible frame
    /// target or source target generation.
    pub(crate) fn reserve(
        &mut self,
        gal: &mut VulkanicGal,
        final_program: &LoweredFullscreenSourceProgram,
        targets: &ShaderPackColorTargets,
        frame_target: Handle,
        color_attachment: Handle,
        main_depth_view: Handle,
        graph_generation: u64,
    ) -> GalResult<SourceFinalOutputReservation> {
        let input = SourceFinalOutputPlan::stage_input(
            gal,
            final_program,
            targets,
            frame_target,
            color_attachment,
            main_depth_view,
            graph_generation,
        )?;
        // A frame slot is reused across source/world graph generations.  The
        // old final-copy plan cannot be reused once any compatibility fact
        // changes, but retaining every generation until swapchain teardown
        // would make the cache grow with gameplay time.  Retire stale plans
        // for this exact acquired slot and semantic output now.  `destroy`
        // is GAL-owned and therefore defers native destruction until the last
        // submission that referenced the plan has completed; in-flight work
        // remains valid while the cache stays bounded by active frame slots.
        let mut stale_identities = self
            .plans
            .keys()
            .filter(|identity| {
                identity.render_target == input.identity.render_target
                    && identity.source_role == input.identity.source_role
                    && **identity != input.identity
            })
            .cloned()
            .collect::<Vec<_>>();
        if stale_identities.len() >= Self::MAX_PLANS_PER_FRAME_TARGET {
            // BTreeMap order is stable and the identity's generation fields
            // are monotonic in normal operation, so removing the oldest
            // entries gives the backend a bounded retirement window while
            // retaining the most recent plans for overlapping submissions.
            stale_identities.sort_by_key(|identity| {
                (
                    identity.world_generation,
                    identity.shader_pack_generation,
                    identity.graph_generation,
                )
            });
            let remove_count = stale_identities
                .len()
                .saturating_sub(Self::MAX_PLANS_PER_FRAME_TARGET - 1);
            stale_identities.truncate(remove_count);
        } else {
            stale_identities.clear();
        }
        for identity in stale_identities {
            if let Some(plan) = self.plans.remove(&identity) {
                plan.destroy(gal);
            }
        }
        if self.plans.contains_key(&input.identity) {
            return Ok(SourceFinalOutputReservation {
                identity: input.identity,
                newly_staged: false,
            });
        }
        let identity = input.identity.clone();
        let plan = SourceFinalOutputPlan::stage_from_input(gal, input)?;
        self.plans.insert(identity.clone(), plan);
        Ok(SourceFinalOutputReservation {
            identity,
            newly_staged: true,
        })
    }

    pub(crate) fn plan(
        &self,
        reservation: &SourceFinalOutputReservation,
    ) -> GalResult<&SourceFinalOutputPlan> {
        self.plans.get(&reservation.identity).ok_or_else(|| {
            GalError::backend(
                "source final-output reservation was retired before frame recording completed",
            )
        })
    }

    /// A successfully submitted entry remains warm. Keeping this explicit
    /// makes the transaction boundary visible beside the failure rollback.
    pub(crate) fn confirm(&self, reservation: &SourceFinalOutputReservation) -> GalResult<()> {
        self.plan(reservation).map(|_| ())
    }

    /// Removes only a binding first allocated by the unsubmitted frame. A
    /// warm binding is never retired by an unrelated record failure.
    pub(crate) fn discard(
        &mut self,
        reservation: SourceFinalOutputReservation,
        gal: &mut VulkanicGal,
    ) {
        if reservation.newly_staged {
            if let Some(plan) = self.plans.remove(&reservation.identity) {
                plan.destroy(gal);
            }
        }
    }

    /// Retires bindings that reference frame targets about to be recreated or
    /// destroyed. The public cache identity remains semantic, while this
    /// private owner performs the necessary GAL dependency cleanup before a
    /// backend can retire its acquired image slot.
    pub(crate) fn retire_frame_targets(&mut self, gal: &mut VulkanicGal, targets: &[Handle]) {
        let identities = self
            .plans
            .iter()
            .filter_map(|(identity, plan)| {
                targets
                    .contains(&plan.frame_target)
                    .then(|| identity.clone())
            })
            .collect::<Vec<_>>();
        for identity in identities {
            if let Some(plan) = self.plans.remove(&identity) {
                plan.destroy(gal);
            }
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        let plans = std::mem::take(&mut self.plans);
        for (_, plan) in plans {
            plan.destroy(gal);
        }
    }

    #[cfg(test)]
    pub(crate) fn len(&self) -> usize {
        self.plans.len()
    }
}

pub(super) const SOURCE_FINAL_COPY_VERTEX: &str = r#"#version 450
layout(location = 0) out vec2 source_uv;
void main() {
    const vec2 positions[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
    vec2 position = positions[gl_VertexIndex];
    source_uv = position * 0.5 + 0.5;
    gl_Position = vec4(position, 0.0, 1.0);
}
"#;

pub(super) const SOURCE_FINAL_COPY_FRAGMENT: &str = r#"#version 450
layout(set = 0, binding = 0) uniform sampler2D source_final_color;
layout(location = 0) in vec2 source_uv;
layout(location = 0) out vec4 final_color;
void main() {
    final_color = texture(source_final_color, source_uv);
}
"#;

pub(super) fn source_final_copy_shader_modules(
    conventions: ShaderConventions,
    label: &str,
) -> [ShaderModuleDesc; 2] {
    [
        ShaderModuleDesc {
            label: format!("{label}.vertex"),
            stage: ShaderStage::Vertex,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code(conventions, SOURCE_FINAL_COPY_VERTEX),
            entry_point: "main".to_string(),
        },
        ShaderModuleDesc {
            label: format!("{label}.fragment"),
            stage: ShaderStage::Fragment,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code(conventions, SOURCE_FINAL_COPY_FRAGMENT),
            entry_point: "main".to_string(),
        },
    ]
}
