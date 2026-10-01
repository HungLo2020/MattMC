//! Per-frame color-target state, bootstrap clears and layout transitions.

use super::*;

/// Submit-confirmed semantic state for one private shader-pack color target.
/// This contains no image views or native identities: it answers only whether
/// the current and optional previous images have meaningful source-owned
/// contents for the target generation.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(super) struct ShaderPackColorFrameTargetState {
    pub(super) current_initialized: bool,
    pub(super) previous_initialized: bool,
    pub(super) mipmaps_initialized: bool,
    pub(super) previous_mipmaps_initialized: bool,
    /// Distinguishes an earlier writer in this exact source schedule from a
    /// value merely retained by a confirmed older frame. A self-feedback pass
    /// must snapshot the former before it overwrites the current image, while
    /// the latter remains valid temporal history.
    pub(super) current_written_this_frame: bool,
}

/// Semantic values needed for the source-pack's one-time full clear. Fog is
/// copied gameplay/environment data; the remaining defaults follow the
/// portable pack target protocol (slot one is depth history, all other
/// non-primary targets begin transparent black). No Iris framebuffer, target,
/// or render-state object participates in this contract.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct ShaderPackColorBootstrapClearValues {
    pub fog_color: ClearColor,
}

#[derive(Debug)]
pub(super) struct ShaderPackColorBootstrapTarget {
    pub(super) name: String,
    pub(super) previous: bool,
    pub(super) texture: Handle,
    pub(super) view: Handle,
    pub(super) render_target: Handle,
    pub(super) render_pass: Handle,
    pub(super) clear_color: ClearColor,
}

/// One bounded, Rust-owned initialization transaction for all current and
/// feedback sides of a named source color generation. The transient passes
/// exist only to perform explicit clears through GAL; target images retain
/// their usual generation lifetime in `ShaderPackColorTargetCache`.
#[derive(Debug)]
pub(crate) struct ShaderPackColorBootstrapPlan {
    pub(super) targets: Vec<ShaderPackColorBootstrapTarget>,
}

/// The last successfully submitted color-history state for one exact source
/// target identity. A replacement extent, world, or pack generation starts
/// uninitialized rather than inheriting images across an incompatible route.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct ShaderPackColorFrameState {
    pub(super) identity: ShaderPackColorTargetIdentity,
    pub(super) targets: BTreeMap<String, ShaderPackColorFrameTargetState>,
}

/// One in-progress source-frame color schedule. It is intentionally detached
/// from submission confirmation: a rejected command list cannot make a color
/// target or feedback image semantically valid.
#[derive(Clone, Debug)]
pub(crate) struct ShaderPackColorFramePlan {
    pub(super) identity: ShaderPackColorTargetIdentity,
    pub(super) targets: BTreeMap<String, ShaderPackColorFrameTargetState>,
}

impl ShaderPackColorFramePlan {
    pub(crate) fn begin(
        targets: &ShaderPackColorTargets,
        prior: Option<&ShaderPackColorFrameState>,
    ) -> GalResult<Self> {
        if let Some(prior) = prior {
            if prior.identity != targets.identity {
                return Err(GalError::invalid_argument(
                    "shader-pack color-frame state belongs to a different target generation",
                ));
            }
        }
        let mut frame_targets = BTreeMap::new();
        for (name, target) in targets.targets() {
            let prior_state = prior
                .and_then(|state| state.targets.get(name).copied())
                .unwrap_or_default();
            frame_targets.insert(
                name.to_string(),
                ShaderPackColorFrameTargetState {
                    current_initialized: prior_state.current_initialized,
                    previous_initialized: if target.previous_view.is_some() {
                        prior_state.previous_initialized
                    } else {
                        false
                    },
                    mipmaps_initialized: if target.mip_levels > 1 {
                        prior_state.mipmaps_initialized
                    } else {
                        false
                    },
                    previous_mipmaps_initialized: if target.previous_view.is_some()
                        && target.mip_levels > 1
                    {
                        prior_state.previous_mipmaps_initialized
                    } else {
                        false
                    },
                    current_written_this_frame: false,
                },
            );
        }
        Ok(Self {
            identity: targets.identity.clone(),
            targets: frame_targets,
        })
    }

    /// A source-generation bootstrap clears every current/feedback image
    /// exactly once. Later frames preserve the submit-confirmed target state
    /// and let their declared passes apply the per-target clear/load policy.
    /// A partially initialized generation is never safe to guess about.
    pub(crate) fn requires_initial_clear(&self) -> GalResult<bool> {
        let initialized = self
            .targets
            .values()
            .map(|state| state.current_initialized)
            .collect::<Vec<_>>();
        if initialized.iter().all(|initialized| !initialized) {
            if self.targets.values().any(|state| {
                state.previous_initialized
                    || state.mipmaps_initialized
                    || state.previous_mipmaps_initialized
            }) {
                return Err(GalError::invalid_argument(
                    "shader-pack color generation has inconsistent partial initialization",
                ));
            }
            return Ok(true);
        }
        if initialized.iter().all(|initialized| *initialized) {
            return Ok(false);
        }
        Err(GalError::invalid_argument(
            "shader-pack color generation has partially initialized current targets",
        ))
    }

    /// Creates the initial full-clear commands for a fresh source target
    /// generation. This is intentionally explicit rather than treating an
    /// allocation as initialized data. Callers record, submit, destroy the
    /// transient plan, then confirm this frame only when that submission
    /// succeeds.
    pub(crate) fn stage_full_clear(
        &self,
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        values: ShaderPackColorBootstrapClearValues,
    ) -> GalResult<ShaderPackColorBootstrapPlan> {
        self.require_targets(targets)?;
        if self.targets.values().any(|state| {
            state.current_initialized
                || state.previous_initialized
                || state.mipmaps_initialized
                || state.previous_mipmaps_initialized
        }) {
            return Err(GalError::invalid_argument(
                "shader-pack color full clear is only valid for an uninitialized target generation",
            ));
        }
        ShaderPackColorBootstrapPlan::stage(gal, targets, values)
    }

    /// Records the full clear and makes the initialized state visible only in
    /// this in-progress plan. `confirm_frame_submission` remains the sole
    /// way to expose it to another frame.
    pub(crate) fn append_full_clear(
        &mut self,
        bootstrap: &ShaderPackColorBootstrapPlan,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.targets.values().any(|state| {
            state.current_initialized
                || state.previous_initialized
                || state.mipmaps_initialized
                || state.previous_mipmaps_initialized
        }) {
            return Err(GalError::invalid_argument(
                "shader-pack color full clear cannot overwrite an already initialized frame plan",
            ));
        }
        bootstrap.append(operations);
        for state in self.targets.values_mut() {
            state.current_initialized = true;
            state.mipmaps_initialized = false;
            state.previous_mipmaps_initialized = false;
            state.current_written_this_frame = true;
            // A target without a previous image leaves this false. The
            // bootstrap plan contains a previous-side clear exactly for
            // declared feedback targets.
        }
        for target in &bootstrap.targets {
            if target.previous {
                self.targets
                    .get_mut(&target.name)
                    .expect("bootstrap target name derives from frame target")
                    .previous_initialized = true;
            }
        }
        Ok(())
    }

    /// Returns the prior states for a complete source render target in source
    /// slot order. The caller still owns pass load/store selection, while this
    /// plan guarantees the state originates from semantic frame history.
    pub(crate) fn attachment_states(
        &self,
        attachments: &[FullscreenSourceColorAttachment],
    ) -> GalResult<Vec<TextureUsageState>> {
        attachments
            .iter()
            .map(|attachment| {
                let name = attachment.role.shader_pack_color_name().ok_or_else(|| {
                    GalError::invalid_argument(
                        "shader-pack color frame attachment is not a named color role",
                    )
                })?;
                let state = self.targets.get(name).ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "shader-pack color frame has no target state for '{name}'"
                    ))
                })?;
                Ok(if state.current_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                })
            })
            .collect()
    }

    /// A pack clear declaration applies on the target's first use in this
    /// frame. Later source writers must load the color produced earlier in
    /// the same frame (notably skybasic before celestial and terrain).
    pub(crate) fn attachment_clear_mask(
        &self,
        attachments: &[FullscreenSourceColorAttachment],
    ) -> GalResult<Vec<bool>> {
        attachments
            .iter()
            .map(|attachment| {
                let name = attachment.role.shader_pack_color_name().ok_or_else(|| {
                    GalError::invalid_argument("shader-pack clear attachment is not a named color role")
                })?;
                let state = self.targets.get(name).ok_or_else(|| {
                    GalError::invalid_argument(format!("shader-pack color frame has no target state for '{name}'"))
                })?;
                Ok(attachment.clear_each_frame && !state.current_written_this_frame)
            })
            .collect()
    }

    /// A source program may sample the current image only after an earlier
    /// pass in this exact frame, or a confirmed previous frame, initialized it.
    /// Feedback samples have their own prior image and never alias current.
    pub(crate) fn require_sample(
        &self,
        role: &TerrainSourceResourceRole,
        feedback: bool,
    ) -> GalResult<()> {
        self.require_sample_with_mips(role, feedback, false)
    }

    /// Mipmapped sampling is a separate semantic dependency: allocating a
    /// chain does not initialize its descendants. A source frame must record
    /// an explicit `GenerateMipmaps` operation after the most recent write.
    pub(crate) fn require_sample_with_mips(
        &self,
        role: &TerrainSourceResourceRole,
        feedback: bool,
        mipmapped: bool,
    ) -> GalResult<()> {
        let name = role.shader_pack_color_name().ok_or_else(|| {
            GalError::invalid_argument(
                "shader-pack color sample is not a named semantic color role",
            )
        })?;
        let state = self.targets.get(name).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "shader-pack color frame has no target state for sampled '{name}'"
            ))
        })?;
        let initialized = if feedback {
            state.previous_initialized
        } else {
            state.current_initialized
        };
        if initialized {
            if mipmapped
                && !(if feedback {
                    state.previous_mipmaps_initialized
                } else {
                    state.mipmaps_initialized
                })
            {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack color target '{name}' is sampled with mips before an explicit source-owned mip generation"
                )));
            }
            return Ok(());
        }
        Err(GalError::invalid_argument(format!(
            "shader-pack color target '{name}' is sampled as {} before a confirmed source-owned initialization",
            if feedback { "feedback history" } else { "current-frame data" }
        )))
    }

    /// Records semantic availability after one pass. Every cleared attachment
    /// is initialized by its clear operation; every declared output is
    /// initialized by the fragment program. Other non-clearing attachments
    /// retain exactly their prior status.
    pub(crate) fn record_pass(
        &mut self,
        attachments: &[FullscreenSourceColorAttachment],
        outputs: &[FullscreenSourceColorAttachment],
        clear_mask: &[bool],
    ) -> GalResult<()> {
        if clear_mask.len() != attachments.len() {
            return Err(GalError::invalid_argument("shader-pack clear mask does not match pass attachments"));
        }
        for (attachment, clear) in attachments.iter().zip(clear_mask.iter().copied()) {
            if clear {
                let state = self.target_state_mut(&attachment.role)?;
                state.current_initialized = true;
                state.mipmaps_initialized = false;
                state.current_written_this_frame = true;
            }
        }
        for output in outputs {
            let state = self.target_state_mut(&output.role)?;
            state.current_initialized = true;
            state.mipmaps_initialized = false;
            state.current_written_this_frame = true;
        }
        Ok(())
    }

    /// Records outputs from an earlier non-fullscreen source pass such as
    /// terrain opaque/cutout. It has the same submit-confirmed ownership as
    /// fullscreen outputs and accepts only declared named color roles.
    pub(crate) fn record_external_outputs(
        &mut self,
        outputs: &[TerrainSourceResourceRole],
    ) -> GalResult<()> {
        for role in outputs {
            let state = self.target_state_mut(role)?;
            state.current_initialized = true;
            state.mipmaps_initialized = false;
            state.current_written_this_frame = true;
        }
        Ok(())
    }

    /// Makes same-frame writes available to a later self-feedback source
    /// stage. The source contract identifies feedback when a stage samples and
    /// writes one named color role; Vulkan and OpenGL cannot bind one image for
    /// both, so the scheduler snapshots only a value actually written earlier
    /// in this frame. Unwritten roles keep their confirmed previous-frame
    /// history for temporal stages.
    pub(crate) fn append_same_frame_feedback_snapshots(
        &mut self,
        targets: &ShaderPackColorTargets,
        roles: &[TerrainSourceResourceRole],
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<Vec<TerrainSourceResourceRole>> {
        self.require_targets(targets)?;
        let mut names = roles
            .iter()
            .map(|role| {
                role.shader_pack_color_name()
                    .map(str::to_string)
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "shader-pack feedback snapshot role is not a named color target",
                        )
                    })
            })
            .collect::<GalResult<Vec<_>>>()?;
        names.sort();
        names.dedup();

        let mut copied = Vec::new();
        for name in names {
            let target = targets.target(&name).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "shader-pack feedback snapshot target '{name}' is unavailable"
                ))
            })?;
            let previous_texture = target.previous_texture.ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "shader-pack source stage declared self-feedback for '{name}' without a staged feedback image"
                ))
            })?;
            let state = self.targets.get(&name).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "shader-pack color frame has no feedback state for '{name}'"
                ))
            })?;
            if !state.current_written_this_frame {
                continue;
            }
            operations.push(CommandOp::Barrier(texture_barrier(
                target.current_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferSrc,
            )));
            operations.push(CommandOp::Barrier(texture_barrier(
                previous_texture,
                if state.previous_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::CopyTexture(TextureImageCopyRegion {
                row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                src_texture: target.current_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: previous_texture,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: self.identity.extent,
            }));
            operations.push(CommandOp::Barrier(texture_barrier(
                previous_texture,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            operations.push(CommandOp::Barrier(texture_barrier(
                target.current_texture,
                TextureUsageState::TransferSrc,
                TextureUsageState::ShaderRead,
            )));
            let state = self.targets.get_mut(&name).expect("validated target state");
            state.previous_initialized = true;
            state.previous_mipmaps_initialized = false;
            copied.push(TerrainSourceResourceRole::ShaderPackColor(name));
        }
        Ok(copied)
    }

    /// Generates the complete descendant chain for source-declared mipmapped
    /// colors. It is deliberately a named semantic operation rather than an
    /// implicit sampler side effect, so Vulkan and OpenGL lower the same GAL
    /// transition and later passes can reject stale descendants precisely.
    pub(crate) fn append_mipmaps(
        &mut self,
        targets: &ShaderPackColorTargets,
        roles: &[TerrainSourceResourceRole],
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.append_mipmaps_for_side(targets, roles, false, operations)
    }

    /// Generates mip descendants for feedback images after bootstrap or a
    /// current-to-previous copy. Feedback and current sides never alias.
    pub(crate) fn append_feedback_mipmaps(
        &mut self,
        targets: &ShaderPackColorTargets,
        roles: &[TerrainSourceResourceRole],
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.append_mipmaps_for_side(targets, roles, true, operations)
    }

    pub(super) fn append_mipmaps_for_side(
        &mut self,
        targets: &ShaderPackColorTargets,
        roles: &[TerrainSourceResourceRole],
        previous: bool,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.require_targets(targets)?;
        let mut names = roles
            .iter()
            .map(|role| {
                role.shader_pack_color_name()
                    .map(str::to_string)
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "shader-pack mip generation role is not a named color target",
                        )
                    })
            })
            .collect::<GalResult<Vec<_>>>()?;
        names.sort();
        names.dedup();
        for name in names {
            let target = targets.target(&name).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "shader-pack mip generation target '{name}' is unavailable"
                ))
            })?;
            if target.mip_levels < 2 {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack color target '{name}' requested mip generation without a staged mip chain"
                )));
            }
            let state = self.targets.get(&name).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "shader-pack color frame has no target state for mip generation '{name}'"
                ))
            })?;
            if !(if previous {
                state.previous_initialized
            } else {
                state.current_initialized
            }) {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack color target '{name}' cannot generate {} mips before level zero is initialized",
                    if previous { "feedback" } else { "current" }
                )));
            }
            let texture = if previous {
                target.previous_texture.ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "shader-pack color target '{name}' has no feedback image for mip generation"
                    ))
                })?
            } else {
                target.current_texture
            };
            let mips_initialized = if previous {
                state.previous_mipmaps_initialized
            } else {
                state.mipmaps_initialized
            };
            let all_mips = TextureSubresourceRange {
                base_mip: 0,
                mip_count: target.mip_levels,
                base_layer: 0,
                layer_count: 1,
            };
            let base_mip = TextureSubresourceRange {
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            };
            let descendants = TextureSubresourceRange {
                base_mip: 1,
                mip_count: target.mip_levels - 1,
                base_layer: 0,
                layer_count: 1,
            };
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: texture,
                subresources: Some(base_mip),
                before: TextureUsageState::ShaderRead,
                after: TextureUsageState::TransferSrc,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: texture,
                subresources: Some(descendants),
                before: if mips_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                after: TextureUsageState::TransferDst,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
            operations.push(CommandOp::GenerateMipmaps {
                texture,
                subresources: all_mips,
            });
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: texture,
                subresources: Some(all_mips),
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::ShaderRead,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
            let state = self.targets.get_mut(&name).expect("validated target state");
            if previous {
                state.previous_mipmaps_initialized = true;
            } else {
                state.mipmaps_initialized = true;
            }
        }
        Ok(())
    }

    /// Appends the source-defined current-to-previous copies for feedback
    /// targets. The copy happens only after the complete source schedule has
    /// written a target, and callers must confirm this frame after the whole
    /// submission succeeds before its history can be sampled next frame.
    pub(crate) fn append_feedback_copies(
        &mut self,
        targets: &ShaderPackColorTargets,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.require_targets(targets)?;
        for (name, target) in targets.targets() {
            let Some(previous_texture) = target.previous_texture else {
                continue;
            };
            let state = self.targets.get(name).expect("validated target state");
            if !state.current_initialized {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack feedback target '{name}' was not initialized by the complete source frame"
                )));
            }
            operations.push(CommandOp::Barrier(texture_barrier(
                target.current_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferSrc,
            )));
            operations.push(CommandOp::Barrier(texture_barrier(
                previous_texture,
                if state.previous_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::CopyTexture(TextureImageCopyRegion {
                row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                src_texture: target.current_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: previous_texture,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: self.identity.extent,
            }));
            operations.push(CommandOp::Barrier(texture_barrier(
                previous_texture,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            operations.push(CommandOp::Barrier(texture_barrier(
                target.current_texture,
                TextureUsageState::TransferSrc,
                TextureUsageState::ShaderRead,
            )));
            self.targets
                .get_mut(name)
                .expect("validated target state")
                .previous_initialized = true;
            self.targets
                .get_mut(name)
                .expect("validated target state")
                .previous_mipmaps_initialized = false;
        }
        Ok(())
    }

    pub(crate) fn into_confirmed_state(self) -> ShaderPackColorFrameState {
        ShaderPackColorFrameState {
            identity: self.identity,
            targets: self.targets,
        }
    }

    pub(super) fn target_state_mut(
        &mut self,
        role: &TerrainSourceResourceRole,
    ) -> GalResult<&mut ShaderPackColorFrameTargetState> {
        let name = role.shader_pack_color_name().ok_or_else(|| {
            GalError::invalid_argument(
                "shader-pack color frame output is not a named semantic color role",
            )
        })?;
        self.targets.get_mut(name).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "shader-pack color frame has no target state for output '{name}'"
            ))
        })
    }

    pub(super) fn require_targets(&self, targets: &ShaderPackColorTargets) -> GalResult<()> {
        if self.identity != targets.identity {
            return Err(GalError::invalid_argument(
                "shader-pack color-frame plan belongs to a different target generation",
            ));
        }
        Ok(())
    }
}

impl ShaderPackColorBootstrapPlan {
    pub(super) fn stage(
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        values: ShaderPackColorBootstrapClearValues,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let mut bootstrap_targets = Vec::new();
            for (name, target) in targets.targets() {
                let clear_color = source_color_clear_color(
                    target.source_slot,
                    target.clear_color_bits,
                    values.fog_color,
                );
                let mut append_target =
                    |previous: bool, texture: Handle, view: Handle| -> GalResult<()> {
                        let side = if previous { "previous" } else { "current" };
                        let label = format!(
                            "shader-pack-color-bootstrap.world{}-pack{}.{}.{}",
                            targets.identity.world_generation,
                            targets.identity.shader_pack_generation,
                            name,
                            side,
                        );
                        let render_target = gal.create_render_target(RenderTargetDesc {
                            label: format!("{label}.target"),
                            color_views: vec![view],
                            depth_stencil_view: None,
                            extent: targets.identity.extent,
                        })?;
                        created.push(render_target);
                        let render_pass = gal.create_render_pass(RenderPassDesc {
                            label: format!("{label}.pass"),
                            target: render_target,
                            color_formats: vec![target.format],
                            depth_format: None,
                        })?;
                        created.push(render_pass);
                        bootstrap_targets.push(ShaderPackColorBootstrapTarget {
                            name: name.to_string(),
                            previous,
                            texture,
                            view,
                            render_target,
                            render_pass,
                            clear_color,
                        });
                        Ok(())
                    };
                append_target(
                    false,
                    target.current_texture,
                    target.current_attachment_view,
                )?;
                if let (Some(texture), Some(view)) = (target.previous_texture, target.previous_view)
                {
                    append_target(
                        true,
                        texture,
                        target.previous_attachment_view.unwrap_or(view),
                    )?;
                }
            }
            Ok(Self {
                targets: bootstrap_targets,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(super) fn append(&self, operations: &mut Vec<CommandOp>) {
        for target in &self.targets {
            operations.push(CommandOp::Barrier(texture_barrier(
                target.texture,
                TextureUsageState::Undefined,
                TextureUsageState::ColorAttachment,
            )));
            operations.push(CommandOp::BeginPass {
                pass: target.render_pass,
                target: target.render_target,
                colors: vec![PassAttachment {
                    view: target.view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(target.clear_color),
                }],
                depth_stencil: None,
            });
            operations.push(CommandOp::EndPass);
            operations.push(CommandOp::Barrier(texture_barrier(
                target.texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        for target in self.targets.into_iter().rev() {
            for handle in [target.render_pass, target.render_target] {
                let _ = gal.destroy(handle);
            }
        }
    }
}

pub(crate) fn source_color_clear_color(
    source_slot: u32,
    clear_color_bits: Option<[u32; 4]>,
    fog_color: ClearColor,
) -> ClearColor {
    // Capture-only target/readback isolation. This is intentionally scoped to
    // the named source primary target and cannot alter normal route selection
    // or material execution.
    if source_slot == 0
        && matches!(
            std::env::var("MATTMC_RUST_SELECTED_SOURCE_CLEAR_PROBE")
                .ok()
                .as_deref()
                .map(str::trim),
            Some("primary-red")
        )
    {
        return ClearColor {
            r: 1.0,
            g: 0.0,
            b: 0.0,
            a: 1.0,
        };
    }
    if let Some([r, g, b, a]) = clear_color_bits {
        return ClearColor {
            r: f32::from_bits(r),
            g: f32::from_bits(g),
            b: f32::from_bits(b),
            a: f32::from_bits(a),
        };
    }
    match source_slot {
        // The portable Iris/OptiFine target contract starts main color at the
        // semantic fog color and gives depth history a known far-depth value.
        0 => ClearColor {
            r: fog_color.r,
            g: fog_color.g,
            b: fog_color.b,
            a: 1.0,
        },
        1 => ClearColor {
            r: 1.0,
            g: 1.0,
            b: 1.0,
            a: 1.0,
        },
        _ => ClearColor {
            r: 0.0,
            g: 0.0,
            b: 0.0,
            a: 0.0,
        },
    }
}

pub(super) fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}
