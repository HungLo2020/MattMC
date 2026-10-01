//! Sky, celestial, post-terrain and DH fullscreen stage preparation and execution plans.

use super::*;

/// Bounded reusable preparation evidence for one selected shader-pack
/// fullscreen stage. It intentionally does not know whether a stage belongs
/// to normal terrain or Distant Horizons: both feed the same source-owned
/// named-color graph after their geometry passes. The data contains source
/// identities and lowered Rust programs only, never Iris state or backend
/// objects.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct FullscreenSourceStagePreparation {
    pub stage_path: String,
    pub kind: FullscreenSourceStageKind,
    pub source_summary: Option<PreprocessedTerrainSourceSummary>,
    pub source_preprocess_error: Option<String>,
    pub source_lowering_error: Option<String>,
    pub source_output_roles: Vec<String>,
    pub source_program_preparation_error: Option<String>,
    pub source_program_identity: Option<String>,
    pub source_program: Option<LoweredFullscreenSourceProgram>,
    pub source_feedback_roles: Vec<String>,
    pub source_mipmap_roles: Vec<String>,
    pub source_resource_binding_count: Option<u32>,
    pub source_resource_binding_error: Option<String>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum FullscreenSourceMode {
    NormalWorld,
    DistantHorizons,
}

pub(super) fn prepare_fullscreen_source_stage(
    source: &ShaderPackSource,
    stage: &FullscreenSourceStage,
    mode: FullscreenSourceMode,
) -> FullscreenSourceStagePreparation {
    let artifacts = match mode {
        FullscreenSourceMode::NormalWorld => {
            preprocess_source_stage_pair(source, &stage.source_stages)
        }
        FullscreenSourceMode::DistantHorizons => {
            preprocess_distant_horizons_fullscreen_stage_pair(source, &stage.source_stages)
        }
    };
    match artifacts {
        Ok(artifacts) => {
            let lowering: GalResult<_> = (|| -> GalResult<_> {
                let declarations = TerrainSourceResourceBindings::from_source(source)?;
                lower_fullscreen_source_pair_with_raster_primitive(
                    &artifacts.vertex,
                    &artifacts.fragment,
                    &declarations,
                    fullscreen_stage_raster_primitive(stage.kind),
                )
            })();
            let resource_bindings = lowering.as_ref().ok().map(|lowered| {
                TerrainSourceResourceBindings::from_source(source).and_then(|declarations| {
                    lowered
                        .opaque_resource_contract()
                        .bind_semantic_roles(&declarations)
                })
            });
            let program_preparation = match (lowering.as_ref(), resource_bindings.as_ref()) {
                (Ok(lowered), Some(Ok(bindings))) => prepare_lowered_fullscreen_source_program(
                    source.name(),
                    source.generation(),
                    &stage.stage_path,
                    lowered,
                    bindings,
                ),
                (Err(error), _) => Err(GalError::unsupported_feature(format!(
                    "fullscreen source lowering for '{}' failed: {error}",
                    stage.stage_path
                ))),
                (_, Some(Err(error))) => Err(GalError::unsupported_feature(format!(
                    "fullscreen source semantic resource binding for '{}' failed: {error}",
                    stage.stage_path
                ))),
                (_, None) => Err(GalError::unsupported_feature(format!(
                    "fullscreen source semantic resource binding for '{}' was not prepared",
                    stage.stage_path
                ))),
            };
            FullscreenSourceStagePreparation {
                stage_path: stage.stage_path.clone(),
                kind: stage.kind,
                source_summary: Some(artifacts.summary()),
                source_preprocess_error: None,
                source_lowering_error: lowering.as_ref().err().map(ToString::to_string),
                source_output_roles: lowering
                    .as_ref()
                    .ok()
                    .map(|lowered| {
                        lowered
                            .fragment()
                            .outputs()
                            .iter()
                            .map(|output| output.role().semantic_name().to_string())
                            .collect()
                    })
                    .unwrap_or_default(),
                source_program_preparation_error: program_preparation
                    .as_ref()
                    .err()
                    .map(ToString::to_string),
                source_program_identity: program_preparation
                    .as_ref()
                    .ok()
                    .map(|program| program.identity.as_str().to_string()),
                source_program: program_preparation.as_ref().ok().cloned(),
                source_feedback_roles: program_preparation
                    .as_ref()
                    .ok()
                    .map(|program| {
                        program
                            .feedback_requirements
                            .iter()
                            .map(|requirement| requirement.role.diagnostic_name())
                            .collect()
                    })
                    .unwrap_or_default(),
                source_mipmap_roles: program_preparation
                    .as_ref()
                    .ok()
                    .map(|program| {
                        program
                            .mipmap_requirements
                            .iter()
                            .map(|requirement| requirement.role.diagnostic_name())
                            .collect()
                    })
                    .unwrap_or_default(),
                source_resource_binding_count: resource_bindings
                    .as_ref()
                    .and_then(|result| result.as_ref().ok())
                    .map(|plan| plan.bindings().len() as u32),
                source_resource_binding_error: resource_bindings
                    .as_ref()
                    .and_then(|result| result.as_ref().err())
                    .map(ToString::to_string),
            }
        }
        Err(error) => FullscreenSourceStagePreparation {
            stage_path: stage.stage_path.clone(),
            kind: stage.kind,
            source_summary: None,
            source_preprocess_error: Some(error.to_string()),
            source_lowering_error: None,
            source_output_roles: Vec::new(),
            source_program_preparation_error: None,
            source_program_identity: None,
            source_program: None,
            source_feedback_roles: Vec::new(),
            source_mipmap_roles: Vec::new(),
            source_resource_binding_count: None,
            source_resource_binding_error: None,
        },
    }
}

pub(super) fn fullscreen_stage_raster_primitive(
    kind: FullscreenSourceStageKind,
) -> FullscreenSourceRasterPrimitive {
    match kind {
        // `gbuffers_skybasic` reconstructs its camera ray from the actual
        // sky-disc depth field. Keep that geometry source-owned instead of
        // approximating it with a fullscreen triangle.
        // The semantic source sky initializer is a background writer. A
        // fullscreen triangle avoids coupling its coverage to the vanilla
        // disc's camera-space radius while preserving the source fragment
        // shader's exact ray reconstruction and later terrain occlusion.
        FullscreenSourceStageKind::Sky => FullscreenSourceRasterPrimitive::FullscreenTriangle,
        FullscreenSourceStageKind::SkyTextured => {
            FullscreenSourceRasterPrimitive::VanillaCelestialQuad
        }
        FullscreenSourceStageKind::Deferred { .. }
        | FullscreenSourceStageKind::Composite { .. }
        | FullscreenSourceStageKind::Final => FullscreenSourceRasterPrimitive::FullscreenTriangle,
    }
}

impl ShaderPackRuntimeExecutor {
    /// Returns the complete owned source-derived fullscreen stages that
    /// consume the Distant Horizons depth stream. This remains preparation
    /// data only: callers still need to stage every named target, create the
    /// explicit fullscreen passes, and confirm one combined submission before
    /// a DH shader-pack route can be selected.
    ///
    /// A partial consumer list is never returned. A source-declared consumer
    /// without a fully lowered semantic program is an admission error, not an
    /// excuse to omit its depth dependency.
    pub(crate) fn prepared_lowered_distant_horizons_depth_consumers(
        &self,
    ) -> GalResult<Vec<&LoweredFullscreenSourceProgram>> {
        match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => Ok(Vec::new()),
            DistantHorizonsSourceCandidateState::Discovered {
                contract,
                depth_consumer_preparation,
                ..
            } => {
                if contract.distant_depth_consumers.len() != depth_consumer_preparation.len() {
                    return Err(GalError::backend(
                        "Distant Horizons depth-consumer preparation no longer matches its source contract",
                    ));
                }
                depth_consumer_preparation
                    .iter()
                    .zip(contract.distant_depth_consumers.iter())
                    .map(|(prepared, declared)| {
                        if prepared.stage_path != declared.stage_path {
                            return Err(GalError::backend(format!(
                                "Distant Horizons depth-consumer preparation '{}' does not match declared stage '{}'",
                                prepared.stage_path, declared.stage_path
                            )));
                        }
                        prepared.source_program.as_ref().ok_or_else(|| {
                            let reason = prepared
                                .source_preprocess_error
                                .as_deref()
                                .or(prepared.source_lowering_error.as_deref())
                                .or(prepared.source_program_preparation_error.as_deref())
                                .or(prepared.source_resource_binding_error.as_deref())
                                .unwrap_or("missing retained owned fullscreen program");
                            GalError::unsupported_feature(format!(
                                "Distant Horizons depth consumer '{}' is not fully prepared: {reason}",
                                prepared.stage_path
                            ))
                        })
                    })
                    .collect()
            }
        }
    }

    /// Returns the complete source-derived fullscreen chain compiled in
    /// Distant Horizons mode. This includes stages that do not sample the DH
    /// depth directly: later composite/final stages must preserve the same
    /// source configuration as the first deferred consumer.
    pub(crate) fn prepared_lowered_distant_horizons_post_terrain_fullscreen_programs(
        &self,
    ) -> GalResult<Vec<&LoweredFullscreenSourceProgram>> {
        match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => Ok(Vec::new()),
            DistantHorizonsSourceCandidateState::Discovered {
                post_terrain_preparation,
                post_terrain_preparation_error,
                ..
            } => {
                if let Some(error) = post_terrain_preparation_error {
                    return Err(GalError::unsupported_feature(format!(
                        "complete Distant Horizons shader-pack fullscreen chain could not be derived: {error}"
                    )));
                }
                post_terrain_preparation
                    .iter()
                    .map(|prepared| {
                        prepared.source_program.as_ref().ok_or_else(|| {
                            let reason = prepared
                                .source_preprocess_error
                                .as_deref()
                                .or(prepared.source_lowering_error.as_deref())
                                .or(prepared.source_program_preparation_error.as_deref())
                                .or(prepared.source_resource_binding_error.as_deref())
                                .unwrap_or("missing retained owned fullscreen program");
                            GalError::unsupported_feature(format!(
                                "Distant Horizons fullscreen source stage '{}' is not fully prepared: {reason}",
                                prepared.stage_path
                            ))
                        })
                    })
                    .collect()
            }
        }
    }

    /// Returns the optional source-derived sky initializer retained for the
    /// selected normal-world contract. This stage runs after named-color
    /// bootstrap and before terrain/DH writers, so it never borrows a Java
    /// sky buffer or pretends to be a deferred/composite consumer.
    pub(crate) fn prepared_lowered_pre_terrain_sky_program(
        &self,
    ) -> GalResult<Option<&LoweredFullscreenSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                pre_terrain_sky_preparation,
                ..
            } => match pre_terrain_sky_preparation {
                None => Ok(None),
                Some(prepared) => prepared.source_program.as_ref().map(Some).ok_or_else(|| {
                    let reason = prepared
                        .source_preprocess_error
                        .as_deref()
                        .or(prepared.source_lowering_error.as_deref())
                        .or(prepared.source_program_preparation_error.as_deref())
                        .or(prepared.source_resource_binding_error.as_deref())
                        .unwrap_or("missing retained owned sky initializer");
                    GalError::unsupported_feature(format!(
                        "source sky initializer '{}' is not fully prepared: {reason}",
                        prepared.stage_path
                    ))
                }),
            },
        }
    }

    /// Returns the optional source-defined celestial writer retained for the
    /// normal-world contract. Discovery/lowering is kept private until the
    /// frontend supplies a complete owned texture/resource execution plan.
    pub(crate) fn prepared_lowered_pre_terrain_celestial_program(
        &self,
    ) -> GalResult<Option<&LoweredFullscreenSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                pre_terrain_celestial_preparation,
                ..
            } => match pre_terrain_celestial_preparation {
                None => Ok(None),
                Some(prepared) => prepared.source_program.as_ref().map(Some).ok_or_else(|| {
                    let reason = prepared
                        .source_preprocess_error
                        .as_deref()
                        .or(prepared.source_lowering_error.as_deref())
                        .or(prepared.source_program_preparation_error.as_deref())
                        .or(prepared.source_resource_binding_error.as_deref())
                        .unwrap_or("missing retained owned celestial source writer");
                    GalError::unsupported_feature(format!(
                        "source celestial stage '{}' is not fully prepared: {reason}",
                        prepared.stage_path
                    ))
                }),
            },
        }
    }

    /// Stages the optional source sky initializer against the same named
    /// color generation that terrain and Distant Horizons will later write.
    /// A missing sky stage is a supported pack choice; a declared but
    /// unprepared stage is rejected by `prepared_lowered_pre_terrain_sky_program`.
    pub(crate) fn stage_pre_terrain_sky_execution_plan(
        &self,
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        external_inputs: &[TerrainSourceOwnedResourceSet],
        extent: Extent3d,
    ) -> GalResult<Option<FullscreenSourceExecutionPlan>> {
        let Some(program) = self.prepared_lowered_pre_terrain_sky_program()? else {
            return Ok(None);
        };
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::unsupported_feature(
                "source sky initializer requires a selected semantic color-target manifest",
            )
        })?;
        if manifest.generation() != targets.identity.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "source sky initializer and named color targets have different shader-pack generations",
            ));
        }
        FullscreenSourceExecutionPlan::stage_cached(
            gal,
            program,
            manifest,
            targets,
            external_inputs.iter().cloned(),
            extent,
            Some((&self.fullscreen_pipeline_cache, self.fullscreen_pipeline_epochs())),
        )
        .map(Some)
    }

    /// Stages the optional source-defined textured celestial writer against
    /// the same owned named-color generation as the sky initializer. The
    /// caller supplies one fully semantic resource snapshot per celestial
    /// draw, so this never inherits Java/Iris texture bindings.
    pub(crate) fn stage_pre_terrain_celestial_execution_plan(
        &self,
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        external_inputs: &[TerrainSourceOwnedResourceSet],
        extent: Extent3d,
    ) -> GalResult<Option<FullscreenSourceExecutionPlan>> {
        let Some(program) = self.prepared_lowered_pre_terrain_celestial_program()? else {
            return Ok(None);
        };
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::unsupported_feature(
                "source celestial writer requires a selected semantic color-target manifest",
            )
        })?;
        if manifest.generation() != targets.identity.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "source celestial writer and named color targets have different shader-pack generations",
            ));
        }
        FullscreenSourceExecutionPlan::stage_cached(
            gal,
            program,
            manifest,
            targets,
            external_inputs.iter().cloned(),
            extent,
            Some((&self.fullscreen_pipeline_cache, self.fullscreen_pipeline_epochs())),
        )
        .map(Some)
    }

    /// Returns the complete scoped fullscreen source chain retained during
    /// discovery. Unlike the depth-consumer accessor, this includes stages
    /// that do not sample Distant Horizons depth. It is deliberately strict:
    /// the first preprocessing, lowering, or semantic-resource failure keeps
    /// the entire eventual source route unavailable instead of allowing a
    /// partial composite chain to look complete.
    /// The post-terrain fullscreen chain (DH or ordinary) as shared owners,
    /// memoized for the candidate epochs that prepared it.
    pub(crate) fn shared_post_terrain_fullscreen_programs(
        &self,
        distant_horizons: bool,
    ) -> GalResult<Vec<std::sync::Arc<LoweredFullscreenSourceProgram>>> {
        let key = (
            self.source_candidate_epoch,
            self.distant_horizons_source_candidate_epoch,
            distant_horizons,
        );
        if let Some((built, programs)) = self.prepared_program_memos.fullscreen.borrow().as_ref() {
            if *built == key {
                return Ok(programs.clone());
            }
        }
        let programs = if distant_horizons {
            self.prepared_lowered_distant_horizons_post_terrain_fullscreen_programs()?
        } else {
            self.prepared_lowered_post_terrain_fullscreen_programs()?
        }
        .into_iter()
        .map(|program| std::sync::Arc::new(program.clone()))
        .collect::<Vec<_>>();
        *self.prepared_program_memos.fullscreen.borrow_mut() = Some((key, programs.clone()));
        Ok(programs)
    }

    pub(crate) fn prepared_lowered_post_terrain_fullscreen_programs(
        &self,
    ) -> GalResult<Vec<&LoweredFullscreenSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(Vec::new()),
            TerrainSourceCandidateState::Discovered {
                post_terrain_preparation,
                post_terrain_preparation_error,
                ..
            } => {
                if let Some(error) = post_terrain_preparation_error {
                    return Err(GalError::unsupported_feature(format!(
                        "complete shader-pack fullscreen chain could not be derived: {error}"
                    )));
                }
                post_terrain_preparation
                    .iter()
                    .map(|prepared| {
                        prepared.source_program.as_ref().ok_or_else(|| {
                            let reason = prepared
                                .source_preprocess_error
                                .as_deref()
                                .or(prepared.source_lowering_error.as_deref())
                                .or(prepared.source_program_preparation_error.as_deref())
                                .or(prepared.source_resource_binding_error.as_deref())
                                .unwrap_or("missing retained owned fullscreen program");
                            GalError::unsupported_feature(format!(
                                "fullscreen source stage '{}' is not fully prepared: {reason}",
                                prepared.stage_path
                            ))
                        })
                    })
                    .collect()
            }
        }
    }

    /// Stages complete GAL execution owners for the retained post-DH source
    /// consumers. This remains private preparation: the caller owns exact
    /// frame uniform payloads, resource-state sequencing, combined
    /// submission confirmation, and route selection. Staging rejects before
    /// any draw when even one semantic resource is absent.
    pub(crate) fn stage_distant_horizons_depth_consumer_execution_plans(
        &self,
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        external_inputs: &[TerrainSourceOwnedResourceSet],
        extent: crate::render::vulkanic::resources::Extent3d,
    ) -> GalResult<Vec<FullscreenSourceExecutionPlan>> {
        let manifest = match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Discovered {
                source_color_targets: Some(manifest),
                ..
            } => manifest,
            DistantHorizonsSourceCandidateState::Discovered { .. } => {
                return Err(GalError::unsupported_feature(
                    "Distant Horizons source consumers have no complete semantic color-target manifest",
                ));
            }
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => return Ok(Vec::new()),
        };
        let programs = self.prepared_lowered_distant_horizons_depth_consumers()?;
        let mut plans = Vec::with_capacity(programs.len());
        for program in programs {
            match FullscreenSourceExecutionPlan::stage_cached(
                gal,
                program,
                manifest,
                targets,
                external_inputs.iter().cloned(),
                extent,
                Some((&self.fullscreen_pipeline_cache, self.fullscreen_pipeline_epochs())),
            ) {
                Ok(plan) => plans.push(plan),
                Err(error) => {
                    for plan in plans.into_iter().rev() {
                        plan.destroy(gal);
                    }
                    return Err(error);
                }
            }
        }
        Ok(plans)
    }

    /// Stages the complete Distant Horizons-mode fullscreen chain against the
    /// shared named-color target generation. The target model stays common;
    /// only source-derived control flow differs from normal-world execution.
    pub(crate) fn stage_distant_horizons_complete_post_terrain_execution_plans<'a>(
        &self,
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        external_inputs_for_stage: impl Fn(&str) -> &'a [TerrainSourceOwnedResourceSet],
        extent: crate::render::vulkanic::resources::Extent3d,
    ) -> GalResult<Vec<FullscreenSourceExecutionPlan>> {
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::unsupported_feature(
                "complete Distant Horizons fullscreen source execution requires a selected semantic color-target manifest",
            )
        })?;
        if manifest.generation() != targets.identity.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "Distant Horizons fullscreen source programs and named color targets have different shader-pack generations",
            ));
        }
        let programs = self.prepared_lowered_distant_horizons_post_terrain_fullscreen_programs()?;
        if programs.is_empty() {
            return Err(GalError::unsupported_feature(
                "complete Distant Horizons fullscreen source execution requires at least one retained stage",
            ));
        }
        let mut plans = Vec::with_capacity(programs.len());
        for program in programs {
            match FullscreenSourceExecutionPlan::stage_cached(
                gal,
                program,
                manifest,
                targets,
                external_inputs_for_stage(&program.source_stage_path)
                    .iter()
                    .cloned(),
                extent,
                Some((&self.fullscreen_pipeline_cache, self.fullscreen_pipeline_epochs())),
            ) {
                Ok(plan) => plans.push(plan),
                Err(error) => {
                    for plan in plans.into_iter().rev() {
                        plan.destroy(gal);
                    }
                    return Err(error);
                }
            }
        }
        Ok(plans)
    }

    /// Stages every fullscreen pass in the complete scoped world chain using
    /// one already-complete named-color generation. This deliberately has no
    /// special knowledge of normal terrain or Distant Horizons: the caller
    /// supplies the union of generation-coherent semantic inputs accumulated
    /// by those frontends, and every stage receives the same explicit source
    /// target contract. The world frontend schedules all writers, this chain,
    /// and final output in one Rust-owned submission.
    /// `external_inputs_for_stage` receives each program's source stage path.
    /// Stages recorded at different frame boundaries can observe different
    /// semantic resources (for example, Iris deferred stages run before the
    /// hand writer and must see world depth rather than post-hand depth).
    pub(crate) fn stage_complete_post_terrain_execution_plans<'a>(
        &self,
        gal: &mut VulkanicGal,
        targets: &ShaderPackColorTargets,
        external_inputs_for_stage: impl Fn(&str) -> &'a [TerrainSourceOwnedResourceSet],
        extent: crate::render::vulkanic::resources::Extent3d,
    ) -> GalResult<Vec<FullscreenSourceExecutionPlan>> {
        let manifest = self.source_color_target_manifest()?.ok_or_else(|| {
            GalError::unsupported_feature(
                "complete fullscreen source execution requires a selected semantic color-target manifest",
            )
        })?;
        if manifest.generation() != targets.identity.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "complete fullscreen source programs and named color targets have different shader-pack generations",
            ));
        }
        let programs = self.prepared_lowered_post_terrain_fullscreen_programs()?;
        if programs.is_empty() {
            return Err(GalError::unsupported_feature(
                "complete fullscreen source execution requires at least one retained stage",
            ));
        }
        let mut plans = Vec::with_capacity(programs.len());
        for program in programs {
            match FullscreenSourceExecutionPlan::stage_cached(
                gal,
                program,
                manifest,
                targets,
                external_inputs_for_stage(&program.source_stage_path)
                    .iter()
                    .cloned(),
                extent,
                Some((&self.fullscreen_pipeline_cache, self.fullscreen_pipeline_epochs())),
            ) {
                Ok(plan) => plans.push(plan),
                Err(error) => {
                    for plan in plans.into_iter().rev() {
                        plan.destroy(gal);
                    }
                    return Err(error);
                }
            }
        }
        Ok(plans)
    }
}
