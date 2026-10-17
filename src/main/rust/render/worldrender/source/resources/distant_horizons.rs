//! Distant Horizons source targets and the candidate distant-depth snapshot.

use super::*;

/// Exact identity for one staged Distant Horizons source-target pair. The
/// token is intentionally smaller than the target payload: source color and
/// depth handles remain private to their owners, while confirmation can only
/// occur for the gameplay frame and semantic generation that staged them.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct DistantHorizonsSourceTargetSubmission {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) identity: lod::WorldLodSourceTargetIdentity,
}

/// Private handoff for a future DH source-pass executor. It carries only
/// Rust-owned GAL target wrappers and the exact confirmation token; it is not
/// an FFI record, backend object, or route-selection result.
#[derive(Clone, Debug)]
pub(crate) struct StagedDistantHorizonsSourceTargets {
    pub(in crate::render::worldrender) submission: DistantHorizonsSourceTargetSubmission,
    pub(in crate::render::worldrender) color_targets: ShaderPackColorTargets,
    pub(in crate::render::worldrender) depth_targets: lod::WorldLodSourceTargets,
}

impl WorldPrimitiveFrontend {
    /// Stages the two independently owned target families required by a
    /// source-derived Distant Horizons pass: the pack's named color targets
    /// and DH's distinct depth stream. This is intentionally preparation
    /// only. The caller must pair it with an eventual complete source-pass
    /// transaction and explicitly confirm or discard both families together.
    ///
    /// Keeping this entrypoint private and unselected prevents the existing
    /// minimal DH material pass from accidentally writing pack-color targets,
    /// while giving the later source executor a generation-coherent boundary
    /// with no Java, Iris, OpenGL, or native target state.
    pub(crate) fn stage_distant_horizons_source_targets(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        world_generation: u64,
        extent: Extent3d,
    ) -> GalResult<Option<StagedDistantHorizonsSourceTargets>> {
        let Some(runtime) = self.shader_runtime.as_ref() else {
            return Ok(None);
        };
        if runtime
            .prepared_lowered_distant_horizons_source_program()?
            .is_none()
        {
            return Ok(None);
        }
        let color_targets = match self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "Distant Horizons source targets require an initialized shader runtime",
                )
            })?
            .stage_source_color_targets(gal, world_generation, extent)
        {
            Ok(Some(targets)) => targets,
            Ok(None) => {
                return Err(GalError::backend(
                    "prepared Distant Horizons source program has no staged shader-pack color target manifest",
                ));
            }
            Err(error) => return Err(error),
        };
        let staged = self.stage_distant_horizons_source_targets_for_colors(
            gal,
            frame_id,
            world_generation,
            extent,
            color_targets,
        );
        if staged.is_err() {
            self.discard_source_color_submission(gal);
        }
        staged
    }

    /// Stages DH's distinct depth family against a caller-owned named color
    /// generation. A combined source frame uses this entrypoint after normal
    /// terrain has already staged its pack colors, ensuring both producers
    /// see one exact world/pack/extent identity instead of independently
    /// resolving whichever color targets happen to be current.
    pub(crate) fn stage_distant_horizons_source_targets_for_colors(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        world_generation: u64,
        extent: Extent3d,
        color_targets: ShaderPackColorTargets,
    ) -> GalResult<Option<StagedDistantHorizonsSourceTargets>> {
        if frame_id == 0 {
            return Err(GalError::invalid_argument(
                "Distant Horizons source targets require a non-zero gameplay frame id",
            ));
        }
        if self.pending_distant_horizons_source_targets.is_some() {
            return Err(GalError::backend(
                "Distant Horizons source targets are already awaiting one combined submission",
            ));
        }
        let shader_pack_generation = match self.shader_runtime.as_ref() {
            Some(runtime) => match runtime.prepared_lowered_distant_horizons_source_program()? {
                Some(program) => program.shader_pack_generation,
                None => return Ok(None),
            },
            None => return Ok(None),
        };
        let identity = lod::WorldLodSourceTargetIdentity {
            world_generation,
            shader_pack_generation,
            extent,
        };
        if color_targets.identity.world_generation != identity.world_generation
            || color_targets.identity.shader_pack_generation != identity.shader_pack_generation
            || color_targets.identity.extent != identity.extent
        {
            return Err(GalError::invalid_argument(
                "Distant Horizons source color target identity does not match its frame/world/program generation",
            ));
        }
        match self.lod_source_targets.stage(gal, identity) {
            Ok(depth_targets) => {
                let submission = DistantHorizonsSourceTargetSubmission { frame_id, identity };
                self.pending_distant_horizons_source_targets = Some(submission);
                Ok(Some(StagedDistantHorizonsSourceTargets {
                    submission,
                    color_targets,
                    depth_targets,
                }))
            }
            Err(error) => {
                self.discard_source_color_submission(gal);
                Err(error)
            }
        }
    }

    /// Commits the exact paired DH source-target generation after its one
    /// combined submission succeeds. Neither owner becomes active if a later
    /// source program, consumer, or submission step fails.
    pub(crate) fn confirm_distant_horizons_source_targets(
        &mut self,
        gal: &mut VulkanicGal,
        submission: DistantHorizonsSourceTargetSubmission,
    ) -> GalResult<()> {
        if self.pending_distant_horizons_source_targets != Some(submission) {
            return Err(GalError::backend(
                "Distant Horizons source target confirmation does not match the pending frame/generation",
            ));
        }
        self.lod_source_targets.confirm_submission(gal);
        if let Some(runtime) = self.shader_runtime.as_mut() {
            runtime.confirm_source_color_resources_submission(gal)?;
            runtime.confirm_source_color_targets_submission(gal);
        }
        self.pending_distant_horizons_source_targets = None;
        Ok(())
    }

    /// Rolls back any unsubmitted DH source targets. The active generation is
    /// retained, so malformed reloads or incomplete consumer plans cannot
    /// leak a mixed color/depth target pair into a later frame.
    pub(crate) fn discard_distant_horizons_source_targets(&mut self, gal: &mut VulkanicGal) {
        self.lod_source_targets.discard_submission(gal);
        self.discard_source_color_submission(gal);
        self.pending_distant_horizons_source_targets = None;
    }

    /// Stages and records the distinct far opaque-depth stream required by a
    /// discovered source contract. The normal Rust world graph remains the
    /// presenter during this preparation frame. If no DH geometry is visible,
    /// the stream is explicitly depth-cleared and copied to its named
    /// before-translucency snapshot; it is never substituted with near depth.
    pub(crate) fn append_candidate_source_distant_depth_for_admission(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        if !self.runtime_source_preparation_requested() || frame.voxel_volume.world_generation == 0
        {
            self.candidate_source_distant_depth_admission =
                Some("not-requested-or-world-unavailable".to_string());
            return Ok(false);
        }
        let (requires_depth, shader_pack_generation) = self
            .shader_runtime
            .as_ref()
            .map(|runtime| {
                (
                    runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::DistantHorizonsOpaqueDepth,
                    ) || runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency,
                    ),
                    runtime.expected_shader_pack_generation_for_resources(),
                )
            })
            .unwrap_or((false, 0));
        if !requires_depth {
            self.candidate_source_distant_depth_admission = Some("not-required".to_string());
            return Ok(false);
        }
        if self.pending_candidate_source_distant_depth.is_some() {
            self.candidate_source_distant_depth_admission = Some("already-pending".to_string());
            return Err(GalError::backend(
                "candidate Distant Horizons depth is already awaiting one normal-frame submission",
            ));
        }
        let identity = lod::WorldLodSourceTargetIdentity {
            world_generation: frame.voxel_volume.world_generation,
            shader_pack_generation,
            extent: Extent3d {
                width: frame.viewport_width,
                height: frame.viewport_height,
                depth: 1,
            },
        };
        // Candidate preparation is deliberately opportunistic. A normal Rust
        // gameplay frame may legitimately have cleared its optional source
        // snapshot during a reload or an unselected-plan transition. In that
        // case do not stage a private target with nothing exact-frame to
        // correlate it to; the normal graph continues and a later coherent
        // frame may prepare the source contract.
        if !self
            .candidate_source_resource_snapshot
            .as_ref()
            .is_some_and(|snapshot| {
                snapshot.world_generation == identity.world_generation
                    && snapshot.shader_pack_generation == identity.shader_pack_generation
                    && snapshot.frame_id == frame.frame_id
            })
        {
            let observed = self
                .candidate_source_resource_snapshot
                .as_ref()
                .map(|snapshot| {
                    format!(
                        "world={} pack={} frame={}",
                        snapshot.world_generation,
                        snapshot.shader_pack_generation,
                        snapshot.frame_id
                    )
                })
                .unwrap_or_else(|| "unavailable".to_string());
            self.candidate_source_distant_depth_admission = Some(format!(
                "snapshot-not-exact expected-world={} expected-pack={} expected-frame={} observed-{observed}",
                identity.world_generation, identity.shader_pack_generation, frame.frame_id,
            ));
            return Ok(false);
        }
        let (targets, prior_usage, created) = self
            .lod_source_targets
            .stage_for_empty_depth_snapshot(gal, identity)?;
        let result = (|| -> GalResult<()> {
            targets.append_empty_opaque_depth_snapshot(prior_usage, operations)?;
            let resources = targets.semantic_resources()?;
            let snapshot = self
                .candidate_source_resource_snapshot
                .as_mut()
                .expect("candidate source snapshot was checked before staging DH depth");
            if snapshot.world_generation != identity.world_generation
                || snapshot.shader_pack_generation != identity.shader_pack_generation
                || snapshot.frame_id != frame.frame_id
            {
                return Err(GalError::invalid_argument(
                    "candidate Distant Horizons depth generation does not match its exact source resource snapshot",
                ));
            }
            let unique_resources =
                resources.excluding_roles_already_owned_by(&snapshot.resources)?;
            if unique_resources.len() != 0 {
                snapshot.resources =
                    TerrainSourceOwnedResourceSet::merge([&snapshot.resources, &unique_resources])?;
            }
            self.candidate_source_resource_role_count = snapshot.resources.len();
            let missing_roles = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime remains installed while refreshing candidate DH depth admission")
                .candidate_source_missing_resource_roles_for_frame(
                    Some(&snapshot.resources),
                    source_frame_includes_distant_horizons(frame),
                );
            self.set_candidate_source_missing_resource_roles(missing_roles);
            Ok(())
        })();
        if let Err(error) = result {
            self.candidate_source_distant_depth_admission =
                Some(format!("staging-failed: {error}"));
            if created {
                self.lod_source_targets.discard_submission(gal);
            }
            return Err(error);
        }
        if created {
            self.pending_candidate_source_distant_depth =
                Some(DistantHorizonsSourceTargetSubmission {
                    frame_id: frame.frame_id,
                    identity,
                });
        }
        self.candidate_source_distant_depth_admission = Some(if created {
            "staged-empty-depth-snapshot".to_string()
        } else {
            "reused-empty-depth-snapshot".to_string()
        });
        Ok(true)
    }

    pub(crate) fn confirm_candidate_source_distant_depth(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
    ) -> GalResult<()> {
        let Some(submission) = self.pending_candidate_source_distant_depth else {
            return Ok(());
        };
        if submission.frame_id != frame_id {
            return Err(GalError::backend(
                "candidate Distant Horizons depth confirmation does not match the submitted gameplay frame",
            ));
        }
        self.lod_source_targets.confirm_submission(gal);
        self.pending_candidate_source_distant_depth = None;
        Ok(())
    }

    pub(crate) fn discard_candidate_source_distant_depth(&mut self, gal: &mut VulkanicGal) {
        if self.pending_candidate_source_distant_depth.take().is_some() {
            self.lod_source_targets.discard_submission(gal);
        }
    }

    /// Carries the confirmed empty-or-populated far-depth snapshot into the
    /// next exact selected-source frame. A pack-wide fullscreen consumer may
    /// sample this depth even when the current frame has no DH geometry, but
    /// it may never borrow a mismatched target or infer a near-depth alias.
    /// Whether the selected pack's required DH depth snapshot exists for this
    /// exact world, pack generation, and extent (see the merge below).
    pub(crate) fn source_distant_depth_ready(&self, frame: &WorldPrimitiveFrame) -> GalResult<()> {
        let Some(runtime) = self.shader_runtime.as_ref() else {
            return Ok(());
        };
        if !(runtime.candidate_source_requires_resource(
            TerrainSourceResourceRole::DistantHorizonsOpaqueDepth,
        ) || runtime.candidate_source_requires_resource(
            TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency,
        )) {
            return Ok(());
        }
        let identity = lod::WorldLodSourceTargetIdentity {
            world_generation: frame.voxel_volume.world_generation,
            shader_pack_generation: runtime.expected_shader_pack_generation_for_resources(),
            extent: Extent3d {
                width: frame.viewport_width,
                height: frame.viewport_height,
                depth: 1,
            },
        };
        match self.lod_source_targets.active_semantic_resources(identity)? {
            Some(_) => Ok(()),
            None => Err(GalError::invalid_argument(
                "no confirmed Distant Horizons depth snapshot for this world, pack, and extent yet",
            )),
        }
    }

    pub(crate) fn merge_active_candidate_source_distant_depth(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<()> {
        let Some(runtime) = self.shader_runtime.as_ref() else {
            return Ok(());
        };
        let requires_depth = runtime.candidate_source_requires_resource(
            TerrainSourceResourceRole::DistantHorizonsOpaqueDepth,
        ) || runtime.candidate_source_requires_resource(
            TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency,
        );
        if !requires_depth {
            return Ok(());
        }
        let identity = lod::WorldLodSourceTargetIdentity {
            world_generation: frame.voxel_volume.world_generation,
            shader_pack_generation: runtime.expected_shader_pack_generation_for_resources(),
            extent: Extent3d {
                width: frame.viewport_width,
                height: frame.viewport_height,
                depth: 1,
            },
        };
        let resources = self
            .lod_source_targets
            .active_semantic_resources(identity)?
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "selected source frame requires a confirmed Distant Horizons depth snapshot for its exact world, pack, and extent",
                )
            })?;
        let snapshot = self
            .candidate_source_resource_snapshot
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "selected source frame has no exact resource snapshot for confirmed Distant Horizons depth",
                )
            })?;
        if snapshot.world_generation != identity.world_generation
            || snapshot.shader_pack_generation != identity.shader_pack_generation
            || snapshot.frame_id != frame.frame_id
        {
            return Err(GalError::invalid_argument(
                "selected source frame's resource snapshot does not match confirmed Distant Horizons depth",
            ));
        }
        // The normal preparation frame may already have installed the exact
        // active DH depth pair. Re-adding that same semantic resource is
        // harmless, but a different generation or GAL binding is never an
        // alias: reject it rather than letting a stale far-depth snapshot
        // reach a source program.
        let unique_resources = resources.excluding_roles_already_owned_by(&snapshot.resources)?;
        if unique_resources.len() != 0 {
            snapshot.resources =
                TerrainSourceOwnedResourceSet::merge([&snapshot.resources, &unique_resources])?;
        }
        self.candidate_source_resource_role_count = snapshot.resources.len();
        Ok(())
    }

    /// Builds the exact semantic input set for one source-derived Distant
    /// Horizons stage. DH's opaque pass owns a distinct depth stream, so its
    /// later source consumers cannot reuse the near-terrain resource table or
    /// infer a raw target from backend state. The caller supplies the staged
    /// DH target family from the same pack/world/frame transaction; merging
    /// rejects any generation mismatch before a pipeline or resource set is
    /// created.
    pub(crate) fn candidate_source_resources_for_distant_horizons_program(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredDistantHorizonsSourceProgram,
        color_targets: &ShaderPackColorTargets,
        targets: &lod::WorldLodSourceTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if targets.identity.world_generation != world_generation
            || targets.identity.shader_pack_generation != program.shader_pack_generation
            || color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
        {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons source target identity world{} pack{} does not match requested world{} program pack{}",
                targets.identity.world_generation,
                targets.identity.shader_pack_generation,
                world_generation,
                program.shader_pack_generation,
            )));
        }
        let base = self
            .candidate_source_resource_snapshot_for_frame(
                program.shader_pack_generation,
                world_generation,
                frame_id,
            )?
            .resources
            .clone();
        let color_resources = self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "Distant Horizons source colors require a shader runtime",
                )
            })?
            .stage_distant_horizons_source_color_resources(gal, program, color_targets)?;
        let merged = base.with_stage_color_resources(&color_resources)?;
        let depth_resources = targets.semantic_resources()?;
        let unique_depth_resources = depth_resources.excluding_roles_already_owned_by(&merged)?;
        let merged = if unique_depth_resources.len() == 0 {
            merged
        } else {
            TerrainSourceOwnedResourceSet::merge([&merged, &unique_depth_resources])?
        };
        program.require_semantic_resources(merged.availability())?;
        Ok(merged)
    }
}
