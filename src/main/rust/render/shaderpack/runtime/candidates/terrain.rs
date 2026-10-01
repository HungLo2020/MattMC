//! Terrain source candidate state, observation and material-id resolution.

use super::*;

/// Owned-source discovery is deliberately separate from the executable plan.
/// A discovered contract proves only that Rust has parsed semantic pack input;
/// it cannot route any draw through an incomplete selected-source pipeline.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum TerrainSourceCandidateState {
    Unavailable,
    Disabled {
        generation: u64,
        pack_name: String,
    },
    Discovered {
        generation: u64,
        pack_name: String,
        requires_colored_voxel_light: bool,
        contract: TerrainPassContract,
        /// The scoped `gbuffers_textured` contract is retained independently
        /// from terrain. It is the future Rust-owned writer for generic
        /// material quads; discovery never authorizes the existing final
        /// overlay path to stand in for shader-pack participation.
        textured_material_contract: Option<TexturedMaterialPassContract>,
        textured_material_contract_error: Option<String>,
        textured_material_lowered_pair: Option<LoweredTexturedMaterialSourcePair>,
        /// `gbuffers_textured` owns a pass-local sampler/image contract. It
        /// must never borrow normal terrain bindings merely because the two
        /// source stages share a pack generation.
        textured_material_source_resource_binding_count: Option<u32>,
        textured_material_source_resource_binding_error: Option<String>,
        textured_material_source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Entity source discovery is intentionally independent from generic
        /// material staging. The lowered contract is consumed by the
        /// Rust-owned entity mesh stream and pass writer, which validate every
        /// requested semantic input and optional vertex attribute at frame
        /// admission time.
        entity_contract: Option<EntityPassContract>,
        entity_contract_error: Option<String>,
        entity_lowered_pair: Option<LoweredEntitySourcePair>,
        entity_source_resource_binding_count: Option<u32>,
        entity_source_resource_binding_error: Option<String>,
        entity_source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// First-person source discovery is deliberately separate from both
        /// world entities and the generic textured-material stage. Its own
        /// projection and cleared-depth domain are supplied by the dedicated
        /// Rust-owned hand writer before frame admission.
        hand_contract: Option<HandPassContract>,
        hand_contract_error: Option<String>,
        hand_lowered_pair: Option<LoweredHandSourcePair>,
        hand_source_resource_binding_count: Option<u32>,
        hand_source_resource_binding_error: Option<String>,
        hand_source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Weather is a separate selected-source stage. It reuses compact
        /// semantic material vertices but retains its own source output and
        /// resource requirements; discovery alone cannot enable a route.
        weather_contract: Option<WeatherPassContract>,
        weather_contract_error: Option<String>,
        weather_lowered_pair: Option<LoweredWeatherSourcePair>,
        weather_source_resource_binding_count: Option<u32>,
        weather_source_resource_binding_error: Option<String>,
        weather_source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Cloud source preparation is admitted only when the owned cloud
        /// writer declares compatible targets and frame resources.
        cloud_contract: Option<CloudPassContract>,
        cloud_contract_error: Option<String>,
        cloud_lowered_pair: Option<LoweredCloudSourcePair>,
        cloud_source_resource_binding_count: Option<u32>,
        cloud_source_resource_binding_error: Option<String>,
        cloud_source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// The pack's translucent terrain stage is discovered independently
        /// from normal terrain. It has a separate source contract and cannot
        /// be silently treated as an opaque/cutout variant during admission.
        translucent_contract: Option<TerrainPassContract>,
        translucent_contract_error: Option<String>,
        translucent_source_summary: Option<PreprocessedTerrainSourceSummary>,
        translucent_source_preprocess_error: Option<String>,
        translucent_source_lowering_error: Option<String>,
        translucent_lowered_pair: Option<LoweredTranslucentTerrainSourcePair>,
        /// The translucent source has its own pass-local sampler contract.
        /// Retaining it separately prevents `gbuffers_water` from inheriting
        /// normal-terrain resources with incompatible semantics.
        translucent_source_resource_binding_count: Option<u32>,
        translucent_source_resource_binding_error: Option<String>,
        translucent_source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Both terrain stages are source-expanded and fingerprinted before a
        /// source candidate can progress beyond discovery. This is provenance
        /// only; it is not a compiled program or render-route decision.
        source_summary: Option<PreprocessedTerrainSourceSummary>,
        source_preprocess_error: Option<String>,
        /// The semantically scoped shadow-stage pair is expanded separately
        /// from normal terrain and consumed by the Rust-owned shadow-color
        /// pass when its explicit target/resources are available.
        source_shadow_summary: Option<PreprocessedTerrainSourceSummary>,
        source_shadow_preprocess_error: Option<String>,
        /// Bounded diagnostic from lowering the scoped shadow fragment's
        /// named outputs. It cannot create a program or a shadow attachment.
        source_shadow_output_count: Option<u32>,
        source_shadow_lowering_error: Option<String>,
        /// The exact lowered shadow pair retained with the source generation.
        /// Execution still requires the pass to declare compatible attachments
        /// and a complete source shadow resource contract.
        source_lowered_shadow_pair: Option<LoweredShadowSourcePair>,
        /// Active resource roles for the separately lowered shadow source
        /// pair. These remain distinct from the normal terrain plan because
        /// binding numbers are pass-local, while shared pack assets may be
        /// prepared from their semantic union.
        source_shadow_resource_binding_count: Option<u32>,
        source_shadow_resource_binding_error: Option<String>,
        source_shadow_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Source-derived, backend-neutral vertex requirements observed after
        /// preprocessing the paired vertex stage. This is a strict future
        /// lowering prerequisite, never a Java/Iris vertex layout.
        source_vertex_interface: Option<TerrainVertexInterface>,
        /// Compact proof that both source stages reached the private lowering
        /// contract. It is diagnostics only and never admits execution.
        source_lowering_summary: Option<TerrainSourceLoweringSummary>,
        source_lowering_error: Option<String>,
        /// Bounded source-derived summary of scalar inputs which are (or are
        /// not) backed by named Rust gameplay semantics. It is diagnostic
        /// provenance only and cannot admit a source execution route.
        source_uniform_requirement_summary: Option<TerrainSourceUniformRequirementSummary>,
        source_uniform_requirement_error: Option<String>,
        /// The validated lowered source pair is retained privately so later
        /// preparation uses exactly the source that discovery inspected.
        /// Retaining it does not compile, bind, or execute a program.
        source_lowered_pair: Option<LoweredTerrainSourcePair>,
        /// Every lowered opaque resource needs a pack-declared semantic role
        /// before it can be assembled into a Rust-owned resource layout.
        /// This is still discovery provenance, never a GAL resource set.
        source_resource_binding_count: Option<u32>,
        source_resource_binding_error: Option<String>,
        /// Stable source-name to semantic-role mapping retained for a future
        /// Rust-owned resource-set builder. It carries no native handles,
        /// texture units, or backend descriptors.
        source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Source-derived terrain PNG declarations. They are retained only as
        /// semantic pack paths until the runtime validates matching Rust-owned
        /// binary assets and creates explicit GAL resources.
        source_asset_binding_count: Option<u32>,
        source_asset_binding_error: Option<String>,
        source_asset_bindings: Option<TerrainShaderPackAssetBindings>,
        /// Source-derived named color declarations shared by any later
        /// fullscreen consumers. The manifest is semantic pack metadata;
        /// target allocation remains private and cannot select this route.
        source_color_target_count: Option<u32>,
        source_color_target_error: Option<String>,
        source_color_target_gal_schema_error: Option<String>,
        source_color_targets: Option<ShaderPackColorTargetManifest>,
        /// Optional source-defined world-sky initializer. It is deliberately
        /// separate from the deferred/composite chain because it writes the
        /// named primary color before terrain rather than consuming terrain
        /// output after it.
        pre_terrain_sky_preparation: Option<FullscreenSourceStagePreparation>,
        /// Optional source-defined vanilla celestial writer. It remains
        /// separate from the sky disc because it consumes an owned local
        /// texture and emits real quad geometry for the sun/moon path.
        pre_terrain_celestial_preparation: Option<FullscreenSourceStagePreparation>,
        /// The source pack's complete scoped fullscreen chain. It belongs to
        /// the normal world contract rather than Distant Horizons: vanilla
        /// terrain can require the same deferred/composite/final stages even
        /// when no DH geometry is visible. Retaining this independently keeps
        /// eventual shared target allocation from silently deriving feedback
        /// history from only the current DH depth-consumer subset.
        post_terrain_preparation: Vec<FullscreenSourceStagePreparation>,
        post_terrain_preparation_error: Option<String>,
        voxel_materials: Option<VoxelMaterialMap>,
        voxel_emission: Option<VoxelEmissionTable>,
    },
    Rejected {
        generation: u64,
        pack_name: String,
        reason: String,
    },
}

impl ShaderPackRuntimeExecutor {
    pub(crate) fn observe_source_candidate(&mut self, source: &ShaderPackSource) {
        self.observe_source_candidate_for_scope(source, TerrainProgramScope::Default);
    }

    pub(crate) fn source_candidate_matches(
        &self,
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) -> bool {
        if self.source_candidate_scope != Some(scope) {
            return false;
        }
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable => false,
            TerrainSourceCandidateState::Disabled {
                generation,
                pack_name,
            }
            | TerrainSourceCandidateState::Rejected {
                generation,
                pack_name,
                ..
            }
            | TerrainSourceCandidateState::Discovered {
                generation,
                pack_name,
                ..
            } => *generation == source.generation() && pack_name == source.name(),
        }
    }

    /// Re-discovers only semantic source metadata for the supplied world
    /// scope. It is not a route-selection method and cannot bind or execute
    /// an Iris/OpenGL program.
    pub(crate) fn observe_source_candidate_for_scope(
        &mut self,
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) {
        if self.source_candidate_matches(source, scope) {
            return;
        }
        if source.is_empty() {
            self.source_candidate_epoch += 1;
            self.source_candidate = TerrainSourceCandidateState::Disabled {
                generation: source.generation(),
                pack_name: source.name().to_string(),
            };
            self.source_candidate_scope = Some(scope);
            return;
        }
        let candidate = match ShaderPackRuntimePlan::discover_terrain_contract_from_source_for_scope(
            source.generation(),
            source,
            scope,
        ) {
            Ok(contract) => {
                let (
                    textured_material_contract,
                    textured_material_contract_error,
                    textured_material_lowered_pair,
                    textured_material_source_resource_binding_count,
                    textured_material_source_resource_binding_error,
                    textured_material_source_resource_bindings,
                ) = match derive_textured_material_contract(source, scope) {
                    Ok(material_contract) => {
                        match lower_textured_material_source_pair(source, &material_contract) {
                            Ok(lowered_pair) => {
                                let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                    let bindings =
                                        TerrainSourceResourceBindings::from_source(source)?;
                                    lowered_pair
                                        .opaque_resource_contract()
                                        .bind_semantic_roles(&bindings)
                                })(
                                );
                                match resource_bindings {
                                    Ok(bindings) => {
                                        let count = bindings.bindings().len() as u32;
                                        (
                                            Some(material_contract),
                                            None,
                                            Some(lowered_pair),
                                            Some(count),
                                            None,
                                            Some(bindings),
                                        )
                                    }
                                    Err(error) => (
                                        Some(material_contract),
                                        None,
                                        Some(lowered_pair),
                                        None,
                                        Some(error.to_string()),
                                        None,
                                    ),
                                }
                            }
                            Err(error) => (
                                Some(material_contract),
                                Some(error.to_string()),
                                None,
                                None,
                                None,
                                None,
                            ),
                        }
                    }
                    Err(error) => (None, Some(error.to_string()), None, None, None, None),
                };
                let (
                    entity_contract,
                    entity_contract_error,
                    entity_lowered_pair,
                    entity_source_resource_binding_count,
                    entity_source_resource_binding_error,
                    entity_source_resource_bindings,
                ) = match derive_entity_contract(source, scope) {
                    Ok(entity_contract) => match lower_entity_source_pair(source, &entity_contract)
                    {
                        Ok(lowered_pair) => {
                            let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                let bindings = TerrainSourceResourceBindings::from_source(source)?;
                                bind_entity_source_resources(&lowered_pair, &bindings)
                            })();
                            match resource_bindings {
                                Ok(bindings) => {
                                    let count = bindings.bindings().len() as u32;
                                    (
                                        Some(entity_contract),
                                        None,
                                        Some(lowered_pair),
                                        Some(count),
                                        None,
                                        Some(bindings),
                                    )
                                }
                                Err(error) => (
                                    Some(entity_contract),
                                    None,
                                    Some(lowered_pair),
                                    None,
                                    Some(error.to_string()),
                                    None,
                                ),
                            }
                        }
                        Err(error) => (
                            Some(entity_contract),
                            Some(error.to_string()),
                            None,
                            None,
                            None,
                            None,
                        ),
                    },
                    Err(error) => (None, Some(error.to_string()), None, None, None, None),
                };
                let (
                    hand_contract,
                    hand_contract_error,
                    hand_lowered_pair,
                    hand_source_resource_binding_count,
                    hand_source_resource_binding_error,
                    hand_source_resource_bindings,
                ) = match derive_hand_contract(source, scope) {
                    Ok(hand_contract) => match lower_hand_source_pair(source, &hand_contract) {
                        Ok(lowered_pair) => {
                            let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                let bindings = TerrainSourceResourceBindings::from_source(source)?;
                                bind_hand_source_resources(&lowered_pair, &bindings)
                            })();
                            match resource_bindings {
                                Ok(bindings) => {
                                    let count = bindings.bindings().len() as u32;
                                    (
                                        Some(hand_contract),
                                        None,
                                        Some(lowered_pair),
                                        Some(count),
                                        None,
                                        Some(bindings),
                                    )
                                }
                                Err(error) => (
                                    Some(hand_contract),
                                    None,
                                    Some(lowered_pair),
                                    None,
                                    Some(error.to_string()),
                                    None,
                                ),
                            }
                        }
                        Err(error) => (
                            Some(hand_contract),
                            Some(error.to_string()),
                            None,
                            None,
                            None,
                            None,
                        ),
                    },
                    Err(error) => (None, Some(error.to_string()), None, None, None, None),
                };
                let (translucent_contract, translucent_contract_error) =
                    match derive_complementary_translucent_terrain_contract_for_scope(source, scope)
                    {
                        Ok(contract) => (Some(contract), None),
                        Err(error) => (None, Some(error.to_string())),
                    };
                let (
                    weather_contract,
                    weather_contract_error,
                    weather_lowered_pair,
                    weather_source_resource_binding_count,
                    weather_source_resource_binding_error,
                    weather_source_resource_bindings,
                ) = match derive_weather_pass_contract(source, scope) {
                    Ok(weather_contract) => {
                        match lower_weather_source_pair(source, &weather_contract) {
                            Ok(lowered_pair) => {
                                let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                    let bindings =
                                        TerrainSourceResourceBindings::from_source(source)?;
                                    lowered_pair
                                        .opaque_resource_contract()
                                        .bind_semantic_roles(&bindings)
                                })(
                                );
                                match resource_bindings {
                                    Ok(bindings) => {
                                        let count = bindings.bindings().len() as u32;
                                        (
                                            Some(weather_contract),
                                            None,
                                            Some(lowered_pair),
                                            Some(count),
                                            None,
                                            Some(bindings),
                                        )
                                    }
                                    Err(error) => (
                                        Some(weather_contract),
                                        None,
                                        Some(lowered_pair),
                                        None,
                                        Some(error.to_string()),
                                        None,
                                    ),
                                }
                            }
                            Err(error) => (
                                Some(weather_contract),
                                Some(error.to_string()),
                                None,
                                None,
                                None,
                                None,
                            ),
                        }
                    }
                    Err(error) => (None, Some(error.to_string()), None, None, None, None),
                };
                let (
                    cloud_contract,
                    cloud_contract_error,
                    cloud_lowered_pair,
                    cloud_source_resource_binding_count,
                    cloud_source_resource_binding_error,
                    cloud_source_resource_bindings,
                ) = match derive_cloud_pass_contract(source, scope) {
                    Ok(cloud_contract)
                        if cloud_contract.face_disposition
                            == CloudFaceDisposition::SuppressVanillaFaces =>
                    {
                        (Some(cloud_contract), None, None, None, None, None)
                    }
                    Ok(cloud_contract) => match lower_cloud_source_pair(source, &cloud_contract) {
                        Ok(lowered_pair) => {
                            let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                let bindings = TerrainSourceResourceBindings::from_source(source)?;
                                lowered_pair
                                    .opaque_resource_contract()
                                    .bind_semantic_roles(&bindings)
                            })();
                            match resource_bindings {
                                Ok(bindings) => {
                                    let count = bindings.bindings().len() as u32;
                                    (
                                        Some(cloud_contract),
                                        None,
                                        Some(lowered_pair),
                                        Some(count),
                                        None,
                                        Some(bindings),
                                    )
                                }
                                Err(error) => (
                                    Some(cloud_contract),
                                    None,
                                    Some(lowered_pair),
                                    None,
                                    Some(error.to_string()),
                                    None,
                                ),
                            }
                        }
                        Err(error) => (
                            Some(cloud_contract),
                            Some(error.to_string()),
                            None,
                            None,
                            None,
                            None,
                        ),
                    },
                    Err(error) => (None, Some(error.to_string()), None, None, None, None),
                };
                let (
                    translucent_source_summary,
                    translucent_source_preprocess_error,
                    translucent_source_lowering_error,
                    translucent_lowered_pair,
                    translucent_source_resource_binding_count,
                    translucent_source_resource_binding_error,
                    translucent_source_resource_bindings,
                ) = match translucent_contract.as_ref() {
                    Some(contract) => match contract
                        .source_stages()
                        .and_then(|stages| preprocess_terrain_sources(source, &stages))
                    {
                        Ok(artifacts) => match lower_translucent_terrain_source_pair(
                            &artifacts.vertex,
                            &artifacts.fragment,
                        ) {
                            Ok(lowered) => {
                                let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                    let bindings =
                                        TerrainSourceResourceBindings::from_source(source)?;
                                    let plan = lowered
                                        .opaque_resource_contract()
                                        .bind_semantic_roles(&bindings)?;
                                    bindings.require_contract_roles(contract)?;
                                    Ok(plan)
                                })(
                                );
                                (
                                    Some(artifacts.summary()),
                                    None,
                                    None,
                                    Some(lowered),
                                    resource_bindings
                                        .as_ref()
                                        .ok()
                                        .map(|plan| plan.bindings().len() as u32),
                                    resource_bindings
                                        .as_ref()
                                        .err()
                                        .map(|error| error.to_string()),
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
                            ),
                        },
                        Err(error) => (None, Some(error.to_string()), None, None, None, None, None),
                    },
                    None => (
                        None,
                        None,
                        translucent_contract_error.clone(),
                        None,
                        None,
                        None,
                        None,
                    ),
                };
                let (
                    source_summary,
                    source_preprocess_error,
                    source_vertex_interface,
                    source_lowering_summary,
                    source_lowering_error,
                    source_lowered_pair,
                    source_uniform_requirement_summary,
                    source_uniform_requirement_error,
                    source_resource_binding_count,
                    source_resource_binding_error,
                    source_resource_bindings,
                ) = match contract
                    .source_stages()
                    .and_then(|stages| preprocess_terrain_sources(source, &stages))
                {
                    Ok(artifacts) => {
                        match lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment) {
                            Ok(lowered) => {
                                let uniform_requirements =
                                    TerrainSourceUniformRequirements::from_contract(
                                        lowered.uniform_contract(),
                                    );
                                let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                    let bindings =
                                        TerrainSourceResourceBindings::from_source(source)?;
                                    let plan = lowered
                                        .opaque_resource_contract()
                                        .bind_semantic_roles(&bindings)?;
                                    bindings.require_contract_roles(&contract)?;
                                    Ok(plan)
                                })(
                                );
                                (
                                    Some(artifacts.summary()),
                                    None,
                                    Some(analyze_terrain_vertex_interface(&artifacts.vertex)),
                                    Some(lowered.summary()),
                                    None,
                                    Some(lowered),
                                    uniform_requirements
                                        .as_ref()
                                        .ok()
                                        .map(TerrainSourceUniformRequirements::summary),
                                    uniform_requirements
                                        .as_ref()
                                        .err()
                                        .map(|error| error.to_string()),
                                    resource_bindings
                                        .as_ref()
                                        .ok()
                                        .map(|plan| plan.bindings().len() as u32),
                                    resource_bindings
                                        .as_ref()
                                        .err()
                                        .map(|error| error.to_string()),
                                    resource_bindings.ok(),
                                )
                            }
                            Err(error) => (
                                Some(artifacts.summary()),
                                None,
                                Some(analyze_terrain_vertex_interface(&artifacts.vertex)),
                                None,
                                Some(error.to_string()),
                                None,
                                None,
                                None,
                                None,
                                None,
                                None,
                            ),
                        }
                    }
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
                        None,
                        None,
                    ),
                };
                let requires_colored_voxel_light = contract
                    .required_resources
                    .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume);
                let (
                    source_shadow_summary,
                    source_shadow_preprocess_error,
                    source_shadow_output_count,
                    source_shadow_lowering_error,
                    source_lowered_shadow_pair,
                    source_shadow_resource_binding_count,
                    source_shadow_resource_binding_error,
                    source_shadow_resource_bindings,
                ) = match shadow_source_stages_for_scope(source, scope)
                    .and_then(|stages| preprocess_terrain_sources(source, &stages))
                {
                    Ok(artifacts) => {
                        let storage_roles = TerrainSourceResourceBindings::from_source(source);
                        match storage_roles.and_then(|storage_roles| {
                            lower_shadow_source_pair_with_owned_storage(
                                &artifacts.vertex,
                                &artifacts.fragment,
                                &storage_roles,
                            )
                        }) {
                            Ok(lowered) => {
                                let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                    let declarations =
                                        TerrainSourceResourceBindings::from_source(source)?;
                                    lowered
                                        .opaque_resource_contract()
                                        .bind_semantic_roles(&declarations)
                                })(
                                );
                                (
                                    Some(artifacts.summary()),
                                    None,
                                    Some(lowered.fragment().outputs().len() as u32),
                                    None,
                                    Some(lowered),
                                    resource_bindings
                                        .as_ref()
                                        .ok()
                                        .map(|plan| plan.bindings().len() as u32),
                                    resource_bindings
                                        .as_ref()
                                        .err()
                                        .map(|error| error.to_string()),
                                    resource_bindings.ok(),
                                )
                            }
                            Err(error) => (
                                Some(artifacts.summary()),
                                None,
                                None,
                                Some(error.to_string()),
                                None,
                                None,
                                None,
                                None,
                            ),
                        }
                    }
                    Err(error) => (
                        None,
                        Some(error.to_string()),
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                    ),
                };
                let source_asset_bindings = TerrainShaderPackAssetBindings::from_source(source);
                let source_asset_binding_count = source_asset_bindings
                    .as_ref()
                    .ok()
                    .map(|bindings| bindings.samplers().count() as u32);
                let source_asset_binding_error = source_asset_bindings
                    .as_ref()
                    .err()
                    .map(ToString::to_string);
                let source_color_targets: GalResult<_> = (|| -> GalResult<_> {
                    let bindings = TerrainSourceResourceBindings::from_source(source)?;
                    ShaderPackColorTargetManifest::from_source(source, &bindings)
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
                let (post_terrain_preparation, post_terrain_preparation_error) =
                    match derive_fullscreen_source_chain(source, scope) {
                        Ok(stages) => (
                            stages
                                .iter()
                                .map(|stage| {
                                    prepare_fullscreen_source_stage(
                                        source,
                                        stage,
                                        FullscreenSourceMode::NormalWorld,
                                    )
                                })
                                .collect(),
                            None,
                        ),
                        Err(error) => (Vec::new(), Some(error.to_string())),
                    };
                let pre_terrain_sky_preparation = match derive_sky_source_stage(source, scope) {
                    Ok(Some(stage)) => Some(prepare_fullscreen_source_stage(
                        source,
                        &stage,
                        FullscreenSourceMode::NormalWorld,
                    )),
                    Ok(None) => None,
                    Err(error) => Some(FullscreenSourceStagePreparation {
                        stage_path: format!("{scope:?}/gbuffers_skybasic.fsh"),
                        kind: FullscreenSourceStageKind::Sky,
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
                    }),
                };
                let pre_terrain_celestial_preparation =
                    match derive_sky_textured_source_stage(source, scope) {
                        Ok(Some(stage)) => Some(prepare_fullscreen_source_stage(
                            source,
                            &stage,
                            FullscreenSourceMode::NormalWorld,
                        )),
                        Ok(None) => None,
                        Err(error) => Some(FullscreenSourceStagePreparation {
                            stage_path: format!("{scope:?}/gbuffers_skytextured.fsh"),
                            kind: FullscreenSourceStageKind::SkyTextured,
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
                        }),
                    };
                let (voxel_materials, voxel_emission) = if requires_colored_voxel_light {
                    match (
                        VoxelMaterialMap::derive(source, &contract),
                        VoxelEmissionTable::derive(source, &contract),
                    ) {
                        (Ok(materials), Ok(emission)) => (Some(materials), Some(emission)),
                        (Err(error), _) | (_, Err(error)) => {
                            self.source_candidate_epoch += 1;
                            self.source_candidate = TerrainSourceCandidateState::Rejected {
                                generation: source.generation(),
                                pack_name: source.name().to_string(),
                                reason: format!(
                                    "selected source colored voxel-light semantics are unsupported: {error}"
                                ),
                            };
                            return;
                        }
                    }
                } else {
                    (None, None)
                };
                TerrainSourceCandidateState::Discovered {
                    generation: source.generation(),
                    pack_name: source.name().to_string(),
                    requires_colored_voxel_light,
                    contract,
                    textured_material_contract,
                    textured_material_contract_error,
                    textured_material_lowered_pair,
                    textured_material_source_resource_binding_count,
                    textured_material_source_resource_binding_error,
                    textured_material_source_resource_bindings,
                    entity_contract,
                    entity_contract_error,
                    entity_lowered_pair,
                    entity_source_resource_binding_count,
                    entity_source_resource_binding_error,
                    entity_source_resource_bindings,
                    hand_contract,
                    hand_contract_error,
                    hand_lowered_pair,
                    hand_source_resource_binding_count,
                    hand_source_resource_binding_error,
                    hand_source_resource_bindings,
                    weather_contract,
                    weather_contract_error,
                    weather_lowered_pair,
                    weather_source_resource_binding_count,
                    weather_source_resource_binding_error,
                    weather_source_resource_bindings,
                    cloud_contract,
                    cloud_contract_error,
                    cloud_lowered_pair,
                    cloud_source_resource_binding_count,
                    cloud_source_resource_binding_error,
                    cloud_source_resource_bindings,
                    translucent_contract,
                    translucent_contract_error,
                    translucent_source_summary,
                    translucent_source_preprocess_error,
                    translucent_source_lowering_error,
                    translucent_lowered_pair,
                    translucent_source_resource_binding_count,
                    translucent_source_resource_binding_error,
                    translucent_source_resource_bindings,
                    source_summary,
                    source_preprocess_error,
                    source_shadow_summary,
                    source_shadow_preprocess_error,
                    source_shadow_output_count,
                    source_shadow_lowering_error,
                    source_lowered_shadow_pair,
                    source_shadow_resource_binding_count,
                    source_shadow_resource_binding_error,
                    source_shadow_resource_bindings,
                    source_vertex_interface,
                    source_lowering_summary,
                    source_lowering_error,
                    source_lowered_pair,
                    source_uniform_requirement_summary,
                    source_uniform_requirement_error,
                    source_resource_binding_count,
                    source_resource_binding_error,
                    source_resource_bindings,
                    source_asset_binding_count,
                    source_asset_binding_error,
                    source_asset_bindings: source_asset_bindings.ok(),
                    source_color_target_count,
                    source_color_target_error,
                    source_color_target_gal_schema_error,
                    source_color_targets: source_color_targets.ok(),
                    pre_terrain_sky_preparation,
                    pre_terrain_celestial_preparation,
                    post_terrain_preparation,
                    post_terrain_preparation_error,
                    voxel_materials,
                    voxel_emission,
                }
            }
            Err(error) => TerrainSourceCandidateState::Rejected {
                generation: source.generation(),
                pack_name: source.name().to_string(),
                reason: error.to_string(),
            },
        };
        self.source_candidate_epoch += 1;
        self.source_candidate = candidate;
        self.source_candidate_scope = Some(scope);
    }

    pub(crate) fn source_candidate(&self) -> &TerrainSourceCandidateState {
        &self.source_candidate
    }

    /// Returns only the source-derived mapping from canonical Minecraft raw
    /// block-state IDs to pack material IDs. This is immutable semantic data,
    /// not an Iris material map, shader binding, or backend object.
    pub(crate) fn candidate_runtime_block_state_material_ids(&self) -> Option<&BTreeMap<i32, i32>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { contract, .. } => {
                contract.runtime_block_state_material_ids.as_ref()
            }
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => None,
        }
    }

    /// Resolves a copied producer semantic identity through the active source
    /// contract. This keeps pack rule matching in the shader-pack runtime
    /// rather than making a world producer infer material policy from a
    /// texture, atlas region, or reduced material category.
    pub(crate) fn candidate_material_id_for_block_state_identity(
        &self,
        identity: &str,
    ) -> Option<GalResult<i32>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { contract, .. } => {
                Some(contract.material_id_for_block_state_identity(identity))
            }
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => None,
        }
    }
}
