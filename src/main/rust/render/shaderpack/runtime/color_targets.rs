//! Pack color-target staging and per-frame source color transactions.

use super::*;

pub(crate) const TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES: u64 =
    16 * 4 + 4 * 4 + 4 * 4 + 16 * 4 + 4 * 4 + 4 * 4;

pub(super) fn shader_pack_color_name_from_role(role: &str) -> GalResult<String> {
    role.strip_prefix("shader_pack_color:")
        .map(str::to_string)
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "fullscreen resource role '{role}' is not a named shader-pack color target"
            ))
        })
}

/// One source-generation color transaction for one combined Rust submission.
///
/// This owns only source-color lifecycle semantics: bootstrap clears, explicit
/// mip prerequisites, feedback copies, and submit-confirmed history. World
/// frontends supply semantic terrain or DH draws separately, so neither route
/// can reinterpret attachment state, borrow Iris targets, or advance history
/// after a rejected submission.
pub(crate) struct ShaderPackSourceColorFrameTransaction {
    pub(super) targets: ShaderPackColorTargets,
    pub(super) frame: ShaderPackColorFramePlan,
    pub(super) bootstrap: Option<ShaderPackColorBootstrapPlan>,
    pub(super) finalized: bool,
}

impl ShaderPackSourceColorFrameTransaction {
    pub(super) fn begin(
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        frame: ShaderPackColorFramePlan,
        clear_values: ShaderPackColorBootstrapClearValues,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<Self> {
        let bootstrap = if frame.requires_initial_clear()? {
            Some(frame.stage_full_clear(gal, targets, clear_values)?)
        } else {
            None
        };
        let mut transaction = Self {
            targets: targets.clone(),
            frame,
            bootstrap,
            finalized: false,
        };
        if let Some(bootstrap) = transaction.bootstrap.as_ref() {
            transaction.frame.append_full_clear(bootstrap, operations)?;
        }
        let mipmapped_roles = transaction
            .targets
            .identity
            .mipmapped_target_names
            .iter()
            .cloned()
            .map(TerrainSourceResourceRole::ShaderPackColor)
            .collect::<Vec<_>>();
        transaction
            .frame
            .append_mipmaps(&transaction.targets, &mipmapped_roles, operations)?;
        let feedback_mipmapped_roles = mipmapped_roles
            .iter()
            .filter(|role| transaction.is_feedback_role(role))
            .cloned()
            .collect::<Vec<_>>();
        transaction.frame.append_feedback_mipmaps(
            &transaction.targets,
            &feedback_mipmapped_roles,
            operations,
        )?;
        Ok(transaction)
    }

    /// Records Rust-owned terrain/DH source outputs whose draw was staged by
    /// a dedicated semantic frontend. The scheduler validates only named
    /// color ownership and never accepts arbitrary native attachment handles.
    pub(crate) fn record_external_outputs(
        &mut self,
        outputs: &[TerrainSourceResourceRole],
    ) -> GalResult<()> {
        self.require_open()?;
        self.frame.record_external_outputs(outputs)
    }

    /// Appends one lowered fullscreen source consumer after establishing its
    /// exact current/feedback mip prerequisites from the source contract.
    pub(crate) fn append_fullscreen_consumer(
        &mut self,
        plan: &FullscreenSourceExecutionPlan,
        program: &LoweredFullscreenSourceProgram,
        frame: FullscreenSourcePassFrame,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.require_open()?;
        let current_mip_roles = program
            .mipmap_requirements
            .iter()
            .filter(|requirement| {
                !program.feedback_requirements.iter().any(|feedback| {
                    feedback.role == requirement.role
                        && feedback.sampled_binding == requirement.sampled_binding
                })
            })
            .map(|requirement| requirement.role.clone())
            .collect::<Vec<_>>();
        self.frame
            .append_mipmaps(&self.targets, &current_mip_roles, operations)?;
        let feedback_roles = program
            .feedback_requirements
            .iter()
            .map(|requirement| requirement.role.clone())
            .collect::<Vec<_>>();
        let copied_feedback_roles = self.frame.append_same_frame_feedback_snapshots(
            &self.targets,
            &feedback_roles,
            operations,
        )?;
        let copied_feedback_mip_roles = copied_feedback_roles
            .into_iter()
            .filter(|role| {
                program.mipmap_requirements.iter().any(|requirement| {
                    requirement.role == *role
                        && program.feedback_requirements.iter().any(|feedback| {
                            feedback.role == requirement.role
                                && feedback.sampled_binding == requirement.sampled_binding
                        })
                })
            })
            .collect::<Vec<_>>();
        self.frame.append_feedback_mipmaps(
            &self.targets,
            &copied_feedback_mip_roles,
            operations,
        )?;
        plan.append_draw_with_color_frame(program, &mut self.frame, frame, operations)
    }

    /// Finishes the source side of the combined submission. Confirmation is
    /// intentionally separate and consumes this transaction only after GAL
    /// accepts the same command list.
    pub(crate) fn finish(&mut self, operations: &mut Vec<CommandOp>) -> GalResult<()> {
        self.require_open()?;
        self.frame
            .append_feedback_copies(&self.targets, operations)?;
        self.finalized = true;
        Ok(())
    }

    pub(crate) fn confirm(
        self,
        runtime: &mut ShaderPackRuntimeExecutor,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if !self.finalized {
            self.discard(runtime, gal);
            return Err(GalError::invalid_argument(
                "shader-pack source color transaction must finish before submission confirmation",
            ));
        }
        let result = runtime.confirm_source_color_transaction_submission(gal, self.frame);
        if let Some(bootstrap) = self.bootstrap {
            bootstrap.destroy(gal);
        }
        if result.is_err() {
            runtime.discard_source_color_targets_submission(gal);
        }
        result
    }

    pub(crate) fn discard(self, runtime: &mut ShaderPackRuntimeExecutor, gal: &mut VulkanicGal) {
        if let Some(bootstrap) = self.bootstrap {
            bootstrap.destroy(gal);
        }
        runtime.discard_source_color_targets_submission(gal);
    }

    pub(super) fn require_open(&self) -> GalResult<()> {
        if self.finalized {
            return Err(GalError::invalid_argument(
                "shader-pack source color transaction is already finalized",
            ));
        }
        Ok(())
    }

    pub(super) fn is_feedback_role(&self, role: &TerrainSourceResourceRole) -> bool {
        role.shader_pack_color_name().is_some_and(|name| {
            self.targets
                .identity
                .feedback_target_names
                .binary_search_by(|candidate| candidate.as_str().cmp(name))
                .is_ok()
        })
    }
}

impl ShaderPackRuntimeExecutor {
    /// Returns the one pack-wide named color manifest shared by ordinary
    /// terrain and any retained Distant Horizons fullscreen consumers. Both
    /// discovery paths parse the same source generation, so disagreement is
    /// a hard source-contract error rather than an opportunity to allocate
    /// separate, silently divergent target sets.
    pub(super) fn source_color_target_manifest(&self) -> GalResult<Option<&ShaderPackColorTargetManifest>> {
        let terrain = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                source_color_targets,
                ..
            } => source_color_targets.as_ref(),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => None,
        };
        let distant_horizons = match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Discovered {
                source_color_targets,
                ..
            } => source_color_targets.as_ref(),
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => None,
        };
        match (terrain, distant_horizons) {
            (Some(terrain), Some(distant_horizons)) if terrain != distant_horizons => {
                Err(GalError::invalid_argument(
                    "ordinary terrain and Distant Horizons source discovery disagree on the shader-pack color target manifest",
                ))
            }
            (Some(manifest), _) | (_, Some(manifest)) => Ok(Some(manifest)),
            (None, None) => Ok(None),
        }
    }

    /// Resolves the feedback and mip history required by every scoped
    /// fullscreen stage in the normal world source contract. This is kept
    /// separate from the existing DH depth-consumer preparation because that
    /// subset is not a valid proxy for the complete vanilla/DH composite
    /// chain. A missing or partially lowered stage keeps complete target
    /// staging unavailable rather than allocating a target generation with
    /// insufficient history images.
    pub(super) fn complete_source_color_target_requirements(&self) -> GalResult<(Vec<String>, Vec<String>)> {
        let (preparation, preparation_error) = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                post_terrain_preparation,
                post_terrain_preparation_error,
                ..
            } => (
                post_terrain_preparation.as_slice(),
                post_terrain_preparation_error.as_deref(),
            ),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok((Vec::new(), Vec::new())),
        };
        if let Some(error) = preparation_error {
            return Err(GalError::unsupported_feature(format!(
                "complete shader-pack fullscreen chain cannot stage named color targets: {error}"
            )));
        }
        if preparation.is_empty() {
            return Err(GalError::unsupported_feature(
                "complete shader-pack fullscreen chain has no retained stages",
            ));
        }
        let mut feedback_names = Vec::new();
        let mut mipmapped_names = Vec::new();
        for stage in preparation {
            if let Some(error) = stage
                .source_preprocess_error
                .as_deref()
                .or(stage.source_lowering_error.as_deref())
                .or(stage.source_program_preparation_error.as_deref())
                .or(stage.source_resource_binding_error.as_deref())
            {
                return Err(GalError::unsupported_feature(format!(
                    "complete shader-pack fullscreen stage '{}' is not prepared: {error}",
                    stage.stage_path
                )));
            }
            if stage.source_program.is_none() {
                return Err(GalError::unsupported_feature(format!(
                    "complete shader-pack fullscreen stage '{}' has no retained lowered program",
                    stage.stage_path
                )));
            }
            feedback_names.extend(
                stage
                    .source_feedback_roles
                    .iter()
                    .map(|role| shader_pack_color_name_from_role(role))
                    .collect::<GalResult<Vec<_>>>()?,
            );
            mipmapped_names.extend(
                stage
                    .source_mipmap_roles
                    .iter()
                    .map(|role| shader_pack_color_name_from_role(role))
                    .collect::<GalResult<Vec<_>>>()?,
            );
        }
        Ok((feedback_names, mipmapped_names))
    }

    /// Stages exact Rust-owned named pack-color images shared by ordinary
    /// terrain and Distant Horizons source consumers. This is source-resource
    /// preparation only: it creates no pipeline, pass, descriptor set, draw,
    /// route, or presenter, and callers must confirm/discard it with their
    /// future combined submission outcome.
    pub(crate) fn stage_source_color_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        extent: crate::render::vulkanic::resources::Extent3d,
    ) -> GalResult<Option<ShaderPackColorTargets>> {
        let manifest = match self.source_color_target_manifest()? {
            Some(manifest) => manifest.clone(),
            None => return Ok(None),
        };
        let generation = manifest.generation();
        let (feedback_names, mipmapped_names) = match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Discovered {
                depth_consumer_preparation,
                ..
            } => {
                let feedback_names = depth_consumer_preparation
                    .iter()
                    .flat_map(|consumer| consumer.source_feedback_roles.iter())
                    .map(|role| {
                        role.strip_prefix("shader_pack_color:")
                            .map(str::to_string)
                            .ok_or_else(|| {
                                GalError::invalid_argument(format!(
                                    "fullscreen feedback role '{role}' is not a named shader-pack color target"
                                ))
                            })
                    })
                    .collect::<GalResult<Vec<_>>>()?;
                let mipmapped_names = depth_consumer_preparation
                    .iter()
                    .flat_map(|consumer| consumer.source_mipmap_roles.iter())
                    .map(|role| {
                        role.strip_prefix("shader_pack_color:")
                            .map(str::to_string)
                            .ok_or_else(|| {
                                GalError::invalid_argument(format!(
                                    "fullscreen mipmap role '{role}' is not a named shader-pack color target"
                                ))
                            })
                    })
                    .collect::<GalResult<Vec<_>>>()?;
                (feedback_names, mipmapped_names)
            }
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => (Vec::new(), Vec::new()),
        };
        let identity = ShaderPackColorTargetIdentity::new(
            world_generation,
            generation,
            extent,
            feedback_names,
            mipmapped_names,
        )?;
        self.source_color_targets
            .stage(gal, identity, &manifest)
            .map(Some)
    }

    /// Stages the one complete named-color target generation required by the
    /// full scoped source chain. This remains private preparation: it does
    /// not record a fullscreen draw, select a route, or present a frame.
    /// Future normal vanilla terrain and DH execution must use this entry
    /// point together, rather than deriving feedback/mips from whichever
    /// geometry family happened to be visible first.
    pub(crate) fn stage_complete_source_color_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        extent: crate::render::vulkanic::resources::Extent3d,
    ) -> GalResult<Option<ShaderPackColorTargets>> {
        let manifest = match self.source_color_target_manifest()? {
            Some(manifest) => manifest.clone(),
            None => return Ok(None),
        };
        let (feedback_names, mipmapped_names) = self.complete_source_color_target_requirements()?;
        let identity = ShaderPackColorTargetIdentity::new(
            world_generation,
            manifest.generation(),
            extent,
            feedback_names,
            mipmapped_names,
        )?;
        self.source_color_targets
            .stage(gal, identity, &manifest)
            .map(Some)
    }

    /// Resolves one lowered normal-terrain program's named outputs through
    /// the selected pack manifest and staged Rust-owned target generation.
    /// The result is preparation data for the world frontend, which owns the
    /// explicit depth target, render pass, source transaction, and combined
    /// submission. Keeping that split prevents either ordinary terrain or DH
    /// from treating legacy source slots as backend attachments.
    pub(crate) fn resolve_terrain_source_color_outputs(
        &self,
        program: &LoweredTerrainSourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<Vec<TerrainSourceColorAttachment>> {
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::invalid_argument(
                "lowered terrain source program has no selected shader-pack color target manifest",
            )
        })?;
        if program.shader_pack_generation != manifest.generation()
            || program.shader_pack_generation != targets.identity.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "lowered terrain source program, color manifest, and staged targets must share one shader-pack generation",
            ));
        }
        let output_color_slots = program.terrain_output_color_slots().ok_or_else(|| {
            GalError::invalid_argument(
                "shadow source programs cannot resolve normal-terrain shader-pack color outputs",
            )
        })?;
        resolve_terrain_source_color_attachments(output_color_slots, manifest, targets)
    }

    /// Resolves the distinct `gbuffers_textured` source writer through the
    /// same Rust-owned named-color generation as terrain. The program keeps
    /// its own output schema and sampler plan; this helper deliberately does
    /// not treat generic material as a terrain mesh or reuse terrain bindings.
    pub(crate) fn resolve_textured_material_source_color_outputs(
        &self,
        program: &LoweredTexturedMaterialSourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<Vec<TerrainSourceColorAttachment>> {
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::invalid_argument(
                "lowered textured material source program has no selected shader-pack color target manifest",
            )
        })?;
        if program.shader_pack_generation != manifest.generation()
            || program.shader_pack_generation != targets.identity.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "textured material source program, color manifest, and staged targets must share one shader-pack generation",
            ));
        }
        resolve_terrain_source_color_attachments(
            program.named_output_color_slots(),
            manifest,
            targets,
        )
    }

    /// Resolves the distinct `gbuffers_entities` writer through the same
    /// Rust-owned named-color generation as terrain. Entity source programs
    /// retain their local-material contract and can never select legacy
    /// draw-buffer slots or Java/Iris targets directly.
    pub(crate) fn resolve_entity_source_color_outputs(
        &self,
        program: &LoweredEntitySourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<Vec<TerrainSourceColorAttachment>> {
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::invalid_argument(
                "lowered entity source program has no selected shader-pack color target manifest",
            )
        })?;
        if program.shader_pack_generation != manifest.generation()
            || program.shader_pack_generation != targets.identity.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "entity source program, color manifest, and staged targets must share one shader-pack generation",
            ));
        }
        resolve_terrain_source_color_attachments(
            program.named_output_color_slots(),
            manifest,
            targets,
        )
    }

    /// Resolves the separately lowered `gbuffers_hand` source through the
    /// exact Rust-owned named-color generation. The caller must still supply
    /// its explicit first-person depth boundary; this helper cannot reuse a
    /// Java/Iris target or select a hand route.
    pub(crate) fn resolve_hand_source_color_outputs(
        &self,
        program: &LoweredHandSourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<Vec<TerrainSourceColorAttachment>> {
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::invalid_argument(
                "lowered hand source program has no selected shader-pack color target manifest",
            )
        })?;
        if program.shader_pack_generation != manifest.generation()
            || program.shader_pack_generation != targets.identity.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "hand source program, color manifest, and staged targets must share one shader-pack generation",
            ));
        }
        resolve_terrain_source_color_attachments(
            program.named_output_color_slots(),
            manifest,
            targets,
        )
    }

    /// Stages the named shader-pack color sampler subset for one ordinary
    /// terrain program. Normal terrain has no feedback or mip policy of its
    /// own; those concerns are explicit only on lowered fullscreen stages.
    /// The returned table is semantic and caller-owned only as a clone of the
    /// cache's handles, suitable for merging into the exact frame snapshot.
    pub(crate) fn stage_terrain_source_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.source_color_resources.stage(
            gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            targets,
        )
    }

    /// Stages only the named color sampler subset required by the prepared
    /// textured-material source program. The cache owns the generated GAL
    /// sets and retires them with the source generation; this returns no Java
    /// or backend handle and cannot select a rendering route.
    pub(crate) fn stage_textured_material_source_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.source_color_resources.stage(
            gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            targets,
        )
    }

    /// Stages only the named color sampler subset declared by the distinct
    /// entity source contract. The caller adds its local material texture
    /// separately; this cache never aliases it to a terrain atlas resource.
    pub(crate) fn stage_terrain_source_color_resources_for_entity(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredEntitySourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.source_color_resources.stage(
            gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            targets,
        )
    }

    /// Stages only the named shader-pack samplers declared by the hand
    /// source. A later first-person writer adds its Rust-owned material
    /// texture and stream separately, so this cannot inherit entity or Java
    /// renderer resources.
    pub(crate) fn stage_terrain_source_color_resources_for_hand(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredHandSourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.source_color_resources.stage(
            gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            targets,
        )
    }

    /// Equivalent named-color resource preparation for the distinct Distant
    /// Horizons source ABI. DH stays semantically separate at geometry/depth
    /// level but samples the same Rust-owned pack color generation.
    pub(crate) fn stage_distant_horizons_source_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.source_color_resources.stage(
            gal,
            program.shader_pack_generation,
            &program.opaque_resource_bindings,
            &ShaderPackColorSamplingPlan::default(),
            targets,
        )
    }

    pub(crate) fn confirm_source_color_targets_submission(&mut self, gal: &mut VulkanicGal) {
        self.source_color_targets.confirm_submission(gal);
    }

    /// Promotes program-local named-color sampler tables only after the same
    /// combined source submission succeeds. Call this before target promotion
    /// so wrappers referencing an old target generation retire before those
    /// target images/views are retired.
    pub(crate) fn confirm_source_color_resources_submission(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        self.source_color_resources.confirm_submission(gal)
    }

    /// Begins a private semantic color schedule for an exact staged source
    /// target generation. Both ordinary terrain and Distant Horizons use this
    /// same owner; it neither selects a route nor borrows an Iris target.
    pub(crate) fn begin_source_color_frame(
        &self,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<ShaderPackColorFramePlan> {
        self.source_color_targets.begin_frame(targets)
    }

    /// Starts the reusable named-color portion of one combined source frame.
    /// It is intentionally route-neutral: ordinary terrain and Distant
    /// Horizons may both append their own semantic draws, but neither owns
    /// color-history validity or native render-target state.
    pub(crate) fn begin_source_color_transaction(
        &self,
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        clear_values: ShaderPackColorBootstrapClearValues,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<ShaderPackSourceColorFrameTransaction> {
        ShaderPackSourceColorFrameTransaction::begin(
            gal,
            targets,
            self.begin_source_color_frame(targets)?,
            clear_values,
            operations,
        )
    }

    /// Advances named color/history validity only after the combined source
    /// submission is accepted. A failed frame must call the existing discard
    /// path and cannot become feedback input for either terrain family.
    pub(crate) fn confirm_source_color_frame_submission(
        &mut self,
        gal: &mut VulkanicGal,
        frame: ShaderPackColorFramePlan,
    ) -> GalResult<()> {
        self.source_color_targets
            .confirm_frame_submission(gal, frame)
    }

    /// Confirms both halves of one accepted source-color submission. Frame
    /// history becomes visible first; only then may an initial staged target
    /// generation become the active reusable generation. A failed history
    /// confirmation leaves target staging discardable rather than retaining a
    /// generation whose semantic contents were never accepted.
    pub(super) fn confirm_source_color_transaction_submission(
        &mut self,
        gal: &mut VulkanicGal,
        frame: ShaderPackColorFramePlan,
    ) -> GalResult<()> {
        self.confirm_source_color_frame_submission(gal, frame)?;
        // Retire sampler wrappers before source-color target replacement can
        // retire the images/views they reference.
        self.confirm_source_color_resources_submission(gal)?;
        self.confirm_source_color_targets_submission(gal);
        Ok(())
    }

    pub(crate) fn discard_source_color_targets_submission(&mut self, gal: &mut VulkanicGal) {
        self.source_color_resources.discard_submission(gal);
        self.source_color_targets.discard_submission(gal);
    }
}
