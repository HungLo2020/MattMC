//! Translucent/depth capture copies, frame-target handoffs and optical hand copies.

use super::*;

impl FabulousAttachmentSet {
    /// Copies the isolated normal-deferred translucent capture into the
    /// Rust-owned Fabulous translucent attachment.  The source is a semantic
    /// GAL texture; no native image or Java/Iris target is accepted.  This is
    /// deliberately color-only until the terrain depth handoff has an
    /// explicit depth-transfer contract.
    pub(crate) fn append_translucent_capture_copy(
        &self,
        ops: &mut Vec<CommandOp>,
        source_texture: Handle,
        extent: Extent3d,
        source_before: TextureUsageState,
        destination_before: TextureUsageState,
    ) -> GalResult<()> {
        self.append_translucent_capture_copy_oriented(
            ops,
            source_texture,
            extent,
            source_before,
            destination_before,
            TextureRowOrder::Preserve,
        )
    }

    pub(super) fn append_translucent_capture_copy_oriented(
        &self,
        ops: &mut Vec<CommandOp>,
        source_texture: Handle,
        extent: Extent3d,
        source_before: TextureUsageState,
        destination_before: TextureUsageState,
        row_order: TextureRowOrder,
    ) -> GalResult<()> {
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "Fabulous translucent capture copy requires a non-zero 2D extent",
            ));
        }
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: source_texture,
            subresources: None,
            before: source_before,
            after: TextureUsageState::TransferSrc,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
        }));
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: self.translucent.color_texture,
            subresources: None,
            before: destination_before,
            after: TextureUsageState::TransferDst,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
        }));
        ops.push(CommandOp::CopyTexture(TextureImageCopyRegion {
            row_order,
            src_texture: source_texture,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: self.translucent.color_texture,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }));
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: source_texture,
            subresources: None,
            before: TextureUsageState::TransferSrc,
            after: TextureUsageState::ShaderRead,
            src_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: self.translucent.color_texture,
            subresources: None,
            before: TextureUsageState::TransferDst,
            after: TextureUsageState::ShaderRead,
            src_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        Ok(())
    }

    /// Copies the normal graph's acquired frame image into the private
    /// Fabulous main sampler before the bundled transparency pass. The frame
    /// target remains opaque to the frontend; only the semantic GAL copy
    /// operation crosses this boundary.
    pub(crate) fn append_frame_target_to_main_copy(
        &self,
        ops: &mut Vec<CommandOp>,
        frame_target: Handle,
        extent: Extent3d,
        destination_before: TextureUsageState,
    ) -> GalResult<()> {
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "Fabulous main capture copy requires a non-zero 2D extent",
            ));
        }
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: self.main.color_texture,
            subresources: None,
            before: destination_before,
            after: TextureUsageState::TransferDst,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
        }));
        ops.push(CommandOp::CopyFrameTargetToTexture {
            src: frame_target,
            dst: self.main.color_texture,
            extent,
        });
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: self.main.color_texture,
            subresources: None,
            before: TextureUsageState::TransferDst,
            after: TextureUsageState::ShaderRead,
            src_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        Ok(())
    }

    /// Copies a Rust-owned depth domain into a Fabulous attachment.  The
    /// bundled transparency shader samples every declared depth role, so the
    /// terrain handoff must preserve the exact G-buffer depth rather than
    /// inventing a second or borrowed depth source.
    pub(crate) fn append_depth_capture_copy(
        &self,
        ops: &mut Vec<CommandOp>,
        source_texture: Handle,
        destination: FabulousTargetRole,
        extent: Extent3d,
        source_before: TextureUsageState,
        destination_before: TextureUsageState,
    ) -> GalResult<()> {
        self.append_depth_capture_copy_oriented(
            ops,
            source_texture,
            destination,
            extent,
            source_before,
            destination_before,
            TextureRowOrder::Preserve,
        )
    }

    pub(super) fn append_depth_capture_copy_oriented(
        &self,
        ops: &mut Vec<CommandOp>,
        source_texture: Handle,
        destination: FabulousTargetRole,
        extent: Extent3d,
        source_before: TextureUsageState,
        destination_before: TextureUsageState,
        row_order: TextureRowOrder,
    ) -> GalResult<()> {
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "Fabulous depth capture copy requires a non-zero 2D extent",
            ));
        }
        let attachment = match destination {
            FabulousTargetRole::Main => &self.main,
            FabulousTargetRole::Translucent => &self.translucent,
            FabulousTargetRole::ItemEntity => &self.item_entity,
            FabulousTargetRole::Particles => &self.particles,
            FabulousTargetRole::Clouds => &self.clouds,
            FabulousTargetRole::Weather => &self.weather,
        };
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: source_texture,
            subresources: None,
            before: source_before,
            after: TextureUsageState::TransferSrc,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
        }));
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: attachment.depth_texture,
            subresources: None,
            before: destination_before,
            after: TextureUsageState::TransferDst,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
        }));
        ops.push(CommandOp::CopyTexture(TextureImageCopyRegion {
            row_order,
            src_texture: source_texture,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: attachment.depth_texture,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }));
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: source_texture,
            subresources: None,
            before: TextureUsageState::TransferSrc,
            after: TextureUsageState::ShaderRead,
            src_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: attachment.depth_texture,
            subresources: None,
            before: TextureUsageState::TransferDst,
            after: TextureUsageState::ShaderRead,
            src_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        Ok(())
    }

    /// Clears an external family that has no semantic producer in the
    /// current terrain handoff. This makes every declared color/depth sampler
    /// valid without borrowing Java-side attachments.
    pub(crate) fn append_empty_attachment_clear(
        &self,
        ops: &mut Vec<CommandOp>,
        role: FabulousTargetRole,
        color_before: TextureUsageState,
        depth_before: TextureUsageState,
    ) {
        let attachment = match role {
            FabulousTargetRole::Main => &self.main,
            FabulousTargetRole::Translucent => &self.translucent,
            FabulousTargetRole::ItemEntity => &self.item_entity,
            FabulousTargetRole::Particles => &self.particles,
            FabulousTargetRole::Clouds => &self.clouds,
            FabulousTargetRole::Weather => &self.weather,
        };
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: attachment.color_texture,
            subresources: None,
            before: color_before,
            after: TextureUsageState::ColorAttachment,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: attachment.depth_texture,
            subresources: None,
            before: depth_before,
            after: TextureUsageState::DepthStencilAttachment,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        ops.push(CommandOp::BeginPass {
            pass: attachment.render_pass,
            target: attachment.render_target,
            colors: vec![PassAttachment {
                view: attachment.color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 0.0,
                }),
            }],
            depth_stencil: Some(PassAttachment {
                view: attachment.depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        ops.push(CommandOp::EndPass);
        ops.extend([
            CommandOp::Barrier(ResourceBarrier {
                resource: attachment.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: attachment.depth_texture,
                subresources: None,
                before: TextureUsageState::DepthStencilAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
        ]);
    }

    /// Lowers a complete terrain-only transparency handoff into one command
    /// stream. This helper is private preparation: callers still decide
    /// whether the semantic frame is eligible and must submit the returned
    /// operations together with GUI/text composition.
    pub(crate) fn append_terrain_handoff_to_frame_target(
        &self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        frame_target: Handle,
        extent: Extent3d,
        deferred_frame_color_source: Handle,
        deferred_translucent_source: Handle,
        deferred_depth_source: Handle,
        deferred_translucent_depth_source: Handle,
    ) -> GalResult<Handle> {
        self.append_terrain_handoff_to_frame_target_with_external_ops(
            gal,
            ops,
            frame_target,
            extent,
            deferred_frame_color_source,
            deferred_translucent_source,
            deferred_depth_source,
            deferred_translucent_depth_source,
            &[],
            [false; 4],
            false,
            true,
        )
    }

    pub(crate) fn append_terrain_handoff_to_frame_target_with_external_ops(
        &self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        frame_target: Handle,
        extent: Extent3d,
        deferred_frame_color_source: Handle,
        deferred_translucent_source: Handle,
        deferred_depth_source: Handle,
        deferred_translucent_depth_source: Handle,
        external_operations: &[CommandOp],
        external_roles_written: [bool; 4],
        attachments_initialized: bool,
        deferred_translucent_initialized: bool,
    ) -> GalResult<Handle> {
        self.append_terrain_handoff_to_frame_target_oriented(
            gal,
            ops,
            frame_target,
            extent,
            deferred_frame_color_source,
            deferred_translucent_source,
            deferred_depth_source,
            deferred_translucent_depth_source,
            external_operations,
            external_roles_written,
            attachments_initialized,
            deferred_translucent_initialized,
            TextureRowOrder::Preserve,
        )
    }

    /// Acquired-frame color and external roles are canonical. Deferred color
    /// and both deferred depth inputs carry the declared copy row order; they
    /// are normalized together before the transparency graph samples them.
    pub(crate) fn append_terrain_handoff_to_frame_target_oriented(
        &self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        frame_target: Handle,
        extent: Extent3d,
        deferred_frame_color_source: Handle,
        deferred_translucent_source: Handle,
        deferred_depth_source: Handle,
        deferred_translucent_depth_source: Handle,
        external_operations: &[CommandOp],
        external_roles_written: [bool; 4],
        attachments_initialized: bool,
        deferred_translucent_initialized: bool,
        deferred_row_order: TextureRowOrder,
    ) -> GalResult<Handle> {
        // A newly allocated attachment has no prior Vulkan layout.  Once a
        // submitted handoff has completed this explicit state becomes
        // shader-readable and is retained by the attachment set.  Carry this
        // lifecycle fact through the GAL command stream rather than claiming
        // an undefined image was previously sampled.
        let persistent_before = if attachments_initialized {
            TextureUsageState::ShaderRead
        } else {
            TextureUsageState::Undefined
        };
        self.append_frame_target_to_main_copy(
            ops,
            deferred_frame_color_source,
            extent,
            persistent_before,
        )?;
        if deferred_translucent_initialized {
            self.append_translucent_capture_copy_oriented(
                ops,
                deferred_translucent_source,
                extent,
                TextureUsageState::ShaderRead,
                persistent_before,
                deferred_row_order,
            )?;
        } else {
            // The graph always samples the declared translucent role. When
            // this semantic frame has no translucent terrain draw, initialize
            // just its color input as transparent; retain the separately
            // copied depth input for Fabulous's depth-aware composition.
            let attachment = &self.translucent;
            ops.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.color_texture,
                subresources: None,
                before: persistent_before,
                after: TextureUsageState::ColorAttachment,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }));
            ops.push(CommandOp::BeginPass {
                pass: attachment.render_pass,
                target: attachment.render_target,
                colors: vec![PassAttachment {
                    view: attachment.color_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(ClearColor {
                        r: 0.0,
                        g: 0.0,
                        b: 0.0,
                        a: 0.0,
                    }),
                }],
                depth_stencil: None,
            });
            ops.push(CommandOp::EndPass);
            ops.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }));
        }
        self.append_depth_capture_copy_oriented(
            ops,
            deferred_depth_source,
            FabulousTargetRole::Main,
            extent,
            TextureUsageState::ShaderRead,
            // Fabulous depth attachments persist across frames and the
            // preceding graph leaves them shader-readable. Treating them as
            // Undefined causes an invalid old-layout transition on reuse.
            persistent_before,
            deferred_row_order,
        )?;
        self.append_depth_capture_copy_oriented(
            ops,
            deferred_translucent_depth_source,
            FabulousTargetRole::Translucent,
            extent,
            TextureUsageState::ShaderRead,
            persistent_before,
            deferred_row_order,
        )?;
        for (role_index, role) in [
            FabulousTargetRole::ItemEntity,
            FabulousTargetRole::Particles,
            FabulousTargetRole::Clouds,
            FabulousTargetRole::Weather,
        ]
        .into_iter()
        .enumerate()
        {
            // Item/entity work was already populated by the world graph.
            // The remaining families draw below using Load, so initialize
            // them even when a producer is present. Skipping their clear
            // samples undefined images on allocation and preserves old-frame
            // pixels thereafter. Undefined explicitly discards that history.
            if role_index == 0 && external_roles_written[role_index] {
                continue;
            }
            self.append_empty_attachment_clear(
                ops,
                role,
                TextureUsageState::Undefined,
                TextureUsageState::Undefined,
            );
        }
        ops.extend(external_operations.iter().cloned());
        let translucent_written_by_external_ops = external_operations.iter().any(|operation| {
            matches!(operation, CommandOp::BeginPass { target, .. } if *target == self.translucent.render_target)
        });
        ops.extend(self.external_shader_read_barriers_with_role_states(
            TextureUsageState::ShaderRead,
            TextureUsageState::ShaderRead,
            if translucent_written_by_external_ops {
                TextureUsageState::ColorAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if translucent_written_by_external_ops {
                TextureUsageState::DepthStencilAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[0] {
                TextureUsageState::ColorAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[0] {
                TextureUsageState::DepthStencilAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[1] {
                TextureUsageState::ColorAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[1] {
                TextureUsageState::DepthStencilAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[2] {
                TextureUsageState::ColorAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[2] {
                TextureUsageState::DepthStencilAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[3] {
                TextureUsageState::ColorAttachment
            } else {
                TextureUsageState::ShaderRead
            },
            if external_roles_written[3] {
                TextureUsageState::DepthStencilAttachment
            } else {
                TextureUsageState::ShaderRead
            },
        ));
        ops.push(self.final_target_color_attachment_barrier());
        let executor = crate::render::shaderpack::vanilla::post_effect::executor::bundled_transparency_executor()?;
        // The handoff above either writes each optional external role through
        // semantic material operations or explicitly clears it. Record that
        // receipt before lowering the graph so allocation/type validation can
        // never masquerade as frame population.
        let populated_roles = BTreeSet::from([
            "minecraft:main".to_owned(),
            "minecraft:translucent".to_owned(),
            "minecraft:item_entity".to_owned(),
            "minecraft:particles".to_owned(),
            "minecraft:clouds".to_owned(),
            "minecraft:weather".to_owned(),
        ]);
        self.external_inventory()
            .validate_populated_for_plan(executor.plan(), &populated_roles)?;
        let (bindings, presentation_pass) =
            self.transparency_pass_bindings_to_frame_target(gal, frame_target)?;
        let mut post_ops = match executor.lower(&bindings) {
            Ok(operations) => operations,
            Err(error) => {
                let _ = gal.destroy(presentation_pass);
                return Err(error);
            }
        };
        let first_end = post_ops
            .iter()
            .position(|operation| matches!(operation, CommandOp::EndPass))
            .ok_or_else(|| {
                GalError::backend("terrain Fabulous handoff omitted first pass terminator")
            })?;
        // The first pass writes the private `final` target; the vanilla
        // terminal blit samples it and performs the required format conversion
        // into the one acquired presentation target.  Keep that dependency
        // explicit rather than replacing it with a raw copy, which is invalid
        // for the RGBA intermediate/BGRA presentation combination.
        post_ops.insert(
            first_end + 1,
            self.between_transparency_pass_barriers()
                .into_iter()
                .next()
                .expect("Fabulous graph has one intermediate transition"),
        );
        let blit_uniform_buffer = self
            .bindings
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous descriptor bindings are not initialized"))?
            .blit_uniform_buffer;
        ops.extend([
            CommandOp::Barrier(ResourceBarrier {
                resource: blit_uniform_buffer,
                subresources: None,
                before: TextureUsageState::ShaderRead,
                after: TextureUsageState::TransferDst,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
            }),
            CommandOp::HostWriteBuffer {
                buffer: blit_uniform_buffer,
                offset: 0,
                data: vec![0, 0, 128, 63, 0, 0, 128, 63, 0, 0, 128, 63, 0, 0, 128, 63],
            },
            CommandOp::Barrier(ResourceBarrier {
                resource: blit_uniform_buffer,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ShaderRead,
                src_queue: crate::render::vulkanic::resources::QueueClass::Transfer,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
        ]);
        ops.extend(post_ops);
        Ok(presentation_pass)
    }

    /// Copies the already-rendered main color into the private optical hand
    /// target.  The operation is deliberately expressed as GAL barriers and
    /// a texture copy; no framebuffer, image, or native handle crosses the
    /// semantic boundary.
    pub(crate) fn optical_hand_copy_from_main(&self, extent: Extent3d) -> Vec<CommandOp> {
        vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::TransferSrc,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferDst,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
            CommandOp::CopyTexture(crate::render::vulkanic::commands::TextureImageCopyRegion {
                row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                src_texture: self.main.color_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: crate::render::vulkanic::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: self.optical_hand.color_texture,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: crate::render::vulkanic::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ColorAttachment,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::ColorAttachment,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
        ]
    }

    /// Copies the optical result back over the Rust-owned main color. The
    /// caller records this only after the optical hand pass has ended.
    pub(crate) fn optical_hand_copy_to_main(&self, extent: Extent3d) -> Vec<CommandOp> {
        vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::TransferSrc,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::TransferDst,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
            CommandOp::CopyTexture(crate::render::vulkanic::commands::TextureImageCopyRegion {
                row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                src_texture: self.optical_hand.color_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: crate::render::vulkanic::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: self.main.color_texture,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: crate::render::vulkanic::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ColorAttachment,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::ColorAttachment,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }),
        ]
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        if let Some(pipelines) = self.pipelines {
            for handle in pipelines.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        if let Some(bindings) = self.bindings {
            for handle in bindings.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        for handle in self.final_target.handles_in_destroy_order() {
            let _ = gal.destroy(handle);
        }
        for resource in [
            self.optical_hand,
            self.weather,
            self.clouds,
            self.particles,
            self.item_entity,
            self.translucent,
            self.main,
        ] {
            for handle in resource.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
    }
}
