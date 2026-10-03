//! Distant Horizons source candidate states and observation.

use super::*;

/// Discovery and lowering provenance for the distinct Distant Horizons
/// source stage. This deliberately does not share the near-terrain candidate:
/// DH has its own copied column stream, transform contract, output targets,
/// and eventual composite dependency. Retaining it here establishes
/// generation-coherent source ownership without selecting a live DH route.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum DistantHorizonsSourceCandidateState {
    Unavailable,
    Disabled {
        generation: u64,
        pack_name: String,
    },
    Discovered {
        generation: u64,
        pack_name: String,
        contract: DistantHorizonsPassContract,
        /// Discovery for the pack's separate DH translucent entry. It is
        /// retained only to give range admission an exact reason; this state
        /// never lowers, binds, or routes `dh_water` until its owned late
        /// target and full resource contract are implemented.
        translucent_contract: DistantHorizonsTranslucentSourceCandidate,
        /// Prepared provenance for the separately selected DH translucent
        /// pair. Retaining it does not create a pipeline or route; it only
        /// guarantees a future late-pass owner consumes the exact source that
        /// discovery validated.
        translucent_source_summary: Option<PreprocessedTerrainSourceSummary>,
        translucent_source_preprocess_error: Option<String>,
        translucent_source_lowering_error: Option<String>,
        translucent_source_lowered_pair: Option<LoweredDistantHorizonsSourcePair>,
        translucent_source_resource_binding_error: Option<String>,
        translucent_source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        source_summary: Option<PreprocessedTerrainSourceSummary>,
        source_preprocess_error: Option<String>,
        source_lowering_error: Option<String>,
        source_lowered_pair: Option<LoweredDistantHorizonsSourcePair>,
        source_uniform_requirement_summary: Option<TerrainSourceUniformRequirementSummary>,
        source_uniform_requirement_error: Option<String>,
        source_resource_binding_count: Option<u32>,
        source_resource_binding_error: Option<String>,
        source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Source-derived format, clear, and mip declarations for named pack
        /// colors. These remain runtime preparation data: no target is
        /// allocated and no source route is selected here.
        source_color_target_count: Option<u32>,
        source_color_target_error: Option<String>,
        /// A generic GAL-format diagnostic. Native backend/device support is
        /// checked later while staging Rust-owned target resources; Rust must
        /// never replace an unavailable source format with a nearby format.
        source_color_target_gal_schema_error: Option<String>,
        source_color_targets: Option<ShaderPackColorTargetManifest>,
        /// Every later source stage which actually samples the DH depth
        /// stream. These are paired preprocessor artifacts only: they do not
        /// compile through the terrain-mesh lowerer and cannot make DH live.
        /// Retaining them prevents a future executor from silently skipping a
        /// source-declared depth consumer.
        depth_consumer_preparation: Vec<DistantHorizonsDepthConsumerPreparation>,
        /// The complete post-terrain chain expanded with the same explicit
        /// Distant Horizons source mode as the DH writer. This is separate
        /// from the normal-world chain because a pack may branch on
        /// `DISTANT_HORIZONS` while consuming the same named color targets.
        pre_terrain_preparation: Vec<FullscreenSourceStagePreparation>,
        pre_terrain_preparation_error: Option<String>,
        post_terrain_preparation: Vec<FullscreenSourceStagePreparation>,
        post_terrain_preparation_error: Option<String>,
    },
    Rejected {
        generation: u64,
        pack_name: String,
        reason: String,
    },
}

/// Bounded discovery provenance for `dh_water`. The opaque DH route must stay
/// independently usable when a pack omits this stage, while a visible water
/// range must never be silently treated as opaque if the stage is malformed
/// or still lacks an owned executor.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum DistantHorizonsTranslucentSourceCandidate {
    Unavailable,
    Discovered(DistantHorizonsPassContract),
    Rejected(String),
}

/// Bounded source provenance for one later shader-pack stage that samples DH
/// depth. The source pair stays independent of a frontend or backend so the
/// eventual fullscreen executor can lower it through explicit named resources
/// rather than borrowing Iris pass state.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct DistantHorizonsDepthConsumerPreparation {
    pub stage_path: String,
    pub reads_opaque_depth: bool,
    pub reads_depth_before_translucency: bool,
    pub source_summary: Option<PreprocessedTerrainSourceSummary>,
    pub source_preprocess_error: Option<String>,
    /// Fullscreen lowering must complete before this consumer can be compiled
    /// into an owned pass. Discovery retains its specific failure rather than
    /// pretending that preprocessing alone establishes executable readiness.
    pub source_lowering_error: Option<String>,
    /// Manifest-derived semantic color targets written by this stage. Raw
    /// `DRAWBUFFERS` locations are intentionally not retained here.
    pub source_output_roles: Vec<String>,
    /// Source program preparation additionally requires a fully semantic
    /// scalar-uniform contract. Its result still has no pipeline, targets, or
    /// route effect, but exposes the first concrete missing runtime input.
    pub source_program_preparation_error: Option<String>,
    pub source_program_identity: Option<String>,
    /// Owned source-derived program retained only after preprocessing,
    /// semantic resource binding, and scalar-uniform validation all succeed.
    /// This prevents a future executor from re-parsing another pack generation
    /// or silently skipping a source-declared DH depth consumer.
    pub source_program: Option<LoweredFullscreenSourceProgram>,
    /// Source-declared output/input aliases that require a distinct
    /// previous/current attachment pair before this consumer can execute.
    pub source_feedback_roles: Vec<String>,
    /// Source-local `colortexNMipmapEnabled` directives resolved through
    /// active semantic sampler bindings. Allocation is diagnostic preparation
    /// only until a later owned fullscreen pass generates the requested mips.
    pub source_mipmap_roles: Vec<String>,
    /// Full source-declared sampler/image plan for this consumer. It remains
    /// diagnostic preparation until a generic fullscreen executor owns every
    /// named semantic resource and output attachment.
    pub source_resource_binding_count: Option<u32>,
    pub source_resource_binding_error: Option<String>,
}

impl ShaderPackRuntimeExecutor {
    pub(crate) fn distant_horizons_source_candidate_matches(
        &self,
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) -> bool {
        if self.distant_horizons_source_candidate_scope != Some(scope) {
            return false;
        }
        match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Unavailable => false,
            DistantHorizonsSourceCandidateState::Disabled {
                generation,
                pack_name,
            }
            | DistantHorizonsSourceCandidateState::Rejected {
                generation,
                pack_name,
                ..
            }
            | DistantHorizonsSourceCandidateState::Discovered {
                generation,
                pack_name,
                ..
            } => *generation == source.generation() && pack_name == source.name(),
        }
    }

    /// Discovers the pack's distinct Distant Horizons pair without borrowing
    /// DH or Iris runtime state. This is separate from normal terrain source
    /// discovery and is intentionally preparation-only: no targets, pipeline,
    /// resource set, command, or route decision is created here.
    pub(crate) fn observe_distant_horizons_source_candidate_for_scope(
        &mut self,
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) {
        if self.distant_horizons_source_candidate_matches(source, scope) {
            return;
        }
        self.distant_horizons_source_candidate_epoch += 1;
        if source.is_empty() {
            self.distant_horizons_source_candidate =
                DistantHorizonsSourceCandidateState::Disabled {
                    generation: source.generation(),
                    pack_name: source.name().to_string(),
                };
            self.distant_horizons_source_candidate_scope = Some(scope);
            return;
        }

        self.distant_horizons_source_candidate = match derive_distant_horizons_opaque_contract(
            source, scope,
        ) {
            Ok(contract) => {
                let translucent_contract = if scope
                    .distant_horizons_translucent_entry_candidates()
                    .iter()
                    .all(|path| source.get(path).is_none())
                {
                    DistantHorizonsTranslucentSourceCandidate::Unavailable
                } else {
                    match derive_distant_horizons_translucent_contract(source, scope) {
                        Ok(contract) => {
                            DistantHorizonsTranslucentSourceCandidate::Discovered(contract)
                        }
                        Err(error) => {
                            DistantHorizonsTranslucentSourceCandidate::Rejected(error.to_string())
                        }
                    }
                };
                let (
                    translucent_source_summary,
                    translucent_source_preprocess_error,
                    translucent_source_lowering_error,
                    translucent_source_lowered_pair,
                    translucent_source_resource_binding_error,
                    translucent_source_resource_bindings,
                ) = match &translucent_contract {
                    DistantHorizonsTranslucentSourceCandidate::Discovered(contract) => {
                        match preprocess_distant_horizons_sources(source, &contract.source_stages) {
                            Ok(artifacts) => {
                                match lower_distant_horizons_source_pair(
                                    &artifacts.vertex,
                                    &artifacts.fragment,
                                ) {
                                    Ok(lowered) => {
                                        let resource_bindings: GalResult<_> =
                                            (|| -> GalResult<_> {
                                                let bindings =
                                                    TerrainSourceResourceBindings::from_source(
                                                        source,
                                                    )?;
                                                lowered
                                                    .opaque_resource_contract()
                                                    .bind_semantic_roles(&bindings)
                                            })();
                                        (
                                            Some(artifacts.summary()),
                                            None,
                                            None,
                                            Some(lowered),
                                            resource_bindings
                                                .as_ref()
                                                .err()
                                                .map(ToString::to_string),
                                            resource_bindings.ok(),
                                        )
                                    }
                                    Err(error) => (
                                        Some(artifacts.summary()),
                                        None,
                                        Some(error.to_string()),
                                        None,
                                        None,
                                        None,
                                    ),
                                }
                            }
                            Err(error) => (None, Some(error.to_string()), None, None, None, None),
                        }
                    }
                    DistantHorizonsTranslucentSourceCandidate::Unavailable
                    | DistantHorizonsTranslucentSourceCandidate::Rejected(_) => {
                        (None, None, None, None, None, None)
                    }
                };
                let (
                    source_summary,
                    source_preprocess_error,
                    source_lowering_error,
                    source_lowered_pair,
                    source_uniform_requirement_summary,
                    source_uniform_requirement_error,
                    source_resource_binding_count,
                    source_resource_binding_error,
                    source_resource_bindings,
                ) = match preprocess_distant_horizons_sources(source, &contract.source_stages) {
                    Ok(artifacts) => match lower_distant_horizons_source_pair(
                        &artifacts.vertex,
                        &artifacts.fragment,
                    ) {
                        Ok(lowered) => {
                            let uniform_requirements =
                                TerrainSourceUniformRequirements::from_contract(
                                    lowered.uniform_contract(),
                                );
                            let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                let bindings = TerrainSourceResourceBindings::from_source(source)?;
                                lowered
                                    .opaque_resource_contract()
                                    .bind_semantic_roles(&bindings)
                            })();
                            (
                                Some(artifacts.summary()),
                                None,
                                None,
                                Some(lowered),
                                uniform_requirements
                                    .as_ref()
                                    .ok()
                                    .map(TerrainSourceUniformRequirements::summary),
                                uniform_requirements.as_ref().err().map(ToString::to_string),
                                resource_bindings
                                    .as_ref()
                                    .ok()
                                    .map(|plan| plan.bindings().len() as u32),
                                resource_bindings.as_ref().err().map(ToString::to_string),
                                resource_bindings.ok(),
                            )
                        }
                        Err(error) => (
                            Some(artifacts.summary()),
                            None,
                            Some(error.to_string()),
                            None,
                            None,
                            None,
                            None,
                            None,
                            None,
                        ),
                    },
                    Err(error) => (
                        None,
                        Some(error.to_string()),
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                    ),
                };
                let source_color_targets: GalResult<_> = (|| -> GalResult<_> {
                    let bindings = TerrainSourceResourceBindings::from_source(source)?;
                    ShaderPackColorTargetManifest::from_source_for_scope(source, &bindings, scope)
                })();
                let source_color_target_count = source_color_targets
                    .as_ref()
                    .ok()
                    .map(|targets| targets.targets().count() as u32);
                let source_color_target_error =
                    source_color_targets.as_ref().err().map(ToString::to_string);
                let source_color_target_gal_schema_error = source_color_targets
                    .as_ref()
                    .ok()
                    .and_then(|targets| targets.require_gal_schema_formats().err())
                    .map(|error| error.to_string());
                let depth_consumer_preparation = contract
                        .distant_depth_consumers
                        .iter()
                        .map(|consumer| {
                            match preprocess_distant_horizons_fullscreen_stage_pair(
                                source,
                                &consumer.source_stages,
                            ) {
                                Ok(artifacts) => {
                                    let lowering: GalResult<_> = (|| -> GalResult<_> {
                                        let declarations =
                                            TerrainSourceResourceBindings::from_source(source)?;
                                        lower_fullscreen_source_pair(
                                            &artifacts.vertex,
                                            &artifacts.fragment,
                                            &declarations,
                                        )
                                    })();
                                    let resource_bindings = lowering.as_ref().ok().map(|lowered| {
                                        TerrainSourceResourceBindings::from_source(source).and_then(
                                            |declarations| {
                                                lowered
                                                    .opaque_resource_contract()
                                                    .bind_semantic_roles(&declarations)
                                            },
                                        )
                                    });
                                    let program_preparation = match (
                                        lowering.as_ref(),
                                        resource_bindings.as_ref(),
                                    ) {
                                        (Ok(lowered), Some(Ok(bindings))) => {
                                            prepare_lowered_fullscreen_source_program(
                                                source.name(),
                                                source.generation(),
                                                &consumer.stage_path,
                                                lowered,
                                                bindings,
                                            )
                                        }
                                        (Err(error), _) => Err(GalError::unsupported_feature(
                                            format!(
                                                "fullscreen source lowering for '{}' failed: {error}",
                                                consumer.stage_path
                                            ),
                                        )),
                                        (_, Some(Err(error))) => Err(GalError::unsupported_feature(
                                            format!(
                                                "fullscreen source semantic resource binding for '{}' failed: {error}",
                                                consumer.stage_path
                                            ),
                                        )),
                                        (_, None) => Err(GalError::unsupported_feature(
                                            format!(
                                                "fullscreen source semantic resource binding for '{}' was not prepared",
                                                consumer.stage_path
                                            ),
                                        )),
                                    };
                                    DistantHorizonsDepthConsumerPreparation {
                                        stage_path: consumer.stage_path.clone(),
                                        reads_opaque_depth: consumer.reads_opaque_depth,
                                        reads_depth_before_translucency: consumer
                                            .reads_depth_before_translucency,
                                        source_summary: Some(artifacts.summary()),
                                        source_preprocess_error: None,
                                        source_lowering_error: lowering
                                            .as_ref()
                                            .err()
                                            .map(ToString::to_string),
                                        source_output_roles: lowering
                                            .as_ref()
                                            .ok()
                                            .map(|lowered| {
                                                lowered
                                                    .fragment()
                                                    .outputs()
                                                    .iter()
                                                    .map(|output| {
                                                        output
                                                            .role()
                                                            .semantic_name()
                                                            .to_string()
                                                    })
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
                                                    .map(|requirement| {
                                                        requirement.role.diagnostic_name()
                                                    })
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
                                                    .map(|requirement| {
                                                        requirement.role.diagnostic_name()
                                                    })
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
                                            .map(|error| error.to_string()),
                                    }
                                }
                                Err(error) => DistantHorizonsDepthConsumerPreparation {
                                    stage_path: consumer.stage_path.clone(),
                                    reads_opaque_depth: consumer.reads_opaque_depth,
                                    reads_depth_before_translucency: consumer
                                        .reads_depth_before_translucency,
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
                        })
                        .collect();
                let (pre_terrain_preparation, pre_terrain_preparation_error) =
                    match derive_pre_terrain_fullscreen_source_chain(source, scope) {
                        Ok(stages) => (stages.iter().map(|stage|
                            prepare_fullscreen_source_stage(source, stage, FullscreenSourceMode::DistantHorizons)
                        ).collect(), None),
                        Err(error) => (Vec::new(), Some(error.to_string())),
                    };
                let (post_terrain_preparation, post_terrain_preparation_error) =
                    match derive_fullscreen_source_chain(source, scope) {
                        Ok(stages) => (
                            stages
                                .iter()
                                .map(|stage| {
                                    prepare_fullscreen_source_stage(
                                        source,
                                        stage,
                                        FullscreenSourceMode::DistantHorizons,
                                    )
                                })
                                .collect(),
                            None,
                        ),
                        Err(error) => (Vec::new(), Some(error.to_string())),
                    };
                DistantHorizonsSourceCandidateState::Discovered {
                    generation: source.generation(),
                    pack_name: source.name().to_string(),
                    contract,
                    translucent_contract,
                    translucent_source_summary,
                    translucent_source_preprocess_error,
                    translucent_source_lowering_error,
                    translucent_source_lowered_pair,
                    translucent_source_resource_binding_error,
                    translucent_source_resource_bindings,
                    source_summary,
                    source_preprocess_error,
                    source_lowering_error,
                    source_lowered_pair,
                    source_uniform_requirement_summary,
                    source_uniform_requirement_error,
                    source_resource_binding_count,
                    source_resource_binding_error,
                    source_resource_bindings,
                    source_color_target_count,
                    source_color_target_error,
                    source_color_target_gal_schema_error,
                    source_color_targets: source_color_targets.ok(),
                    depth_consumer_preparation,
                    pre_terrain_preparation,
                    pre_terrain_preparation_error,
                    post_terrain_preparation,
                    post_terrain_preparation_error,
                }
            }
            Err(error) => DistantHorizonsSourceCandidateState::Rejected {
                generation: source.generation(),
                pack_name: source.name().to_string(),
                reason: error.to_string(),
            },
        };
        self.distant_horizons_source_candidate_scope = Some(scope);
    }

    pub(crate) fn distant_horizons_source_candidate(&self) -> &DistantHorizonsSourceCandidateState {
        &self.distant_horizons_source_candidate
    }

    /// Returns only the separately discovered DH translucent source state.
    /// Callers may use it to explain why a semantically translucent LOD range
    /// was not admitted, but cannot use it as a route or backend capability.
    pub(crate) fn distant_horizons_translucent_source_candidate(
        &self,
    ) -> Option<&DistantHorizonsTranslucentSourceCandidate> {
        match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Discovered {
                translucent_contract,
                ..
            } => Some(translucent_contract),
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => None,
        }
    }
}
