//! Lowered source programs, prepared once per pack generation and memoized.

use super::*;
use std::sync::Arc;

/// Prepared lowered programs are pure functions of the discovered source
/// candidate, but preparing one re-derives its contract and interface; the
/// frontend asks for them several times per frame. Immutable snapshots are
/// shared, so cache hits never copy source text or contracts; callers keep
/// their exact generation alive until their CPU preparation completes.
#[derive(Debug, Default)]
pub(super) struct PreparedSourceProgramMemos {
    pub(super) terrain: std::cell::RefCell<Vec<(u64, u8, Arc<LoweredTerrainSourceProgram>)>>,
    pub(super) entity: std::cell::RefCell<Option<(u64, Arc<LoweredEntitySourceProgram>)>>,
    pub(super) hand: std::cell::RefCell<Option<(u64, Arc<LoweredHandSourceProgram>)>>,
    pub(super) cloud: std::cell::RefCell<Option<(u64, Arc<LoweredCloudSourceProgram>)>>,
    pub(super) distant_horizons: std::cell::RefCell<Option<(u64, Arc<LoweredDistantHorizonsSourceProgram>)>>,
    pub(super) distant_horizons_translucent:
        std::cell::RefCell<Option<(u64, Arc<LoweredDistantHorizonsSourceProgram>)>>,
    pub(super) weather: std::cell::RefCell<Option<(u64, Arc<LoweredWeatherSourceProgram>)>>,
    pub(super) textured_material: std::cell::RefCell<Option<(u64, Arc<LoweredTexturedMaterialSourceProgram>)>>,
    /// Post-terrain fullscreen chains, keyed by (terrain candidate epoch,
    /// DH candidate epoch, DH chain). Shared so a frame never deep-copies
    /// every stage's lowered source text.
    pub(super) fullscreen: std::cell::RefCell<
        Option<((u64, u64, bool), Vec<std::sync::Arc<LoweredFullscreenSourceProgram>>)>,
    >,
    /// Individually shared fullscreen stages (pre-terrain, sky, celestial) by
    /// program address and candidate epochs; see `shared_fullscreen_program`.
    pub(super) shared_fullscreen: std::cell::RefCell<
        Vec<((usize, u64, u64), std::sync::Arc<LoweredFullscreenSourceProgram>)>,
    >,
}

pub(super) fn memoized_source_program<T>(
    cell: &std::cell::RefCell<Option<(u64, Arc<T>)>>,
    epoch: u64,
    build: impl FnOnce() -> GalResult<Option<T>>,
) -> GalResult<Option<Arc<T>>> {
    if let Some((built_epoch, program)) = cell.borrow().as_ref() {
        if *built_epoch == epoch {
            return Ok(Some(program.clone()));
        }
    }
    let built = build()?.map(Arc::new);
    *cell.borrow_mut() = built.as_ref().map(|program| (epoch, program.clone()));
    Ok(built)
}

impl ShaderPackRuntimeExecutor {
    /// Prepares exactly the DH source stages retained during discovery. The
    /// caller still needs a Rust-owned distant-depth target, a selected shared
    /// pack-color target, and every source-derived depth consumer before this
    /// artifact can ever be executed.
    pub(crate) fn prepared_lowered_distant_horizons_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredDistantHorizonsSourceProgram>>> {
        memoized_source_program(
            &self.prepared_program_memos.distant_horizons,
            self.distant_horizons_source_candidate_epoch,
            || self.prepared_lowered_distant_horizons_source_program_uncached(),
        )
    }

    pub(super) fn prepared_lowered_distant_horizons_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredDistantHorizonsSourceProgram>> {
        match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => Ok(None),
            DistantHorizonsSourceCandidateState::Discovered {
                contract,
                source_summary: Some(_),
                source_preprocess_error: None,
                source_lowering_error: None,
                source_lowered_pair: Some(lowered),
                source_uniform_requirement_summary: Some(summary),
                source_uniform_requirement_error: None,
                source_resource_binding_count: Some(_),
                source_resource_binding_error: None,
                source_resource_bindings: Some(bindings),
                ..
            } if summary.field_count == summary.resolved_field_count => {
                prepare_lowered_distant_horizons_source_program(contract, lowered, bindings)
                    .map(Some)
            }
            DistantHorizonsSourceCandidateState::Discovered {
                source_preprocess_error,
                source_lowering_error,
                source_uniform_requirement_summary,
                source_uniform_requirement_error,
                source_resource_binding_error,
                ..
            } => {
                let uniform_status = source_uniform_requirement_error.clone().or_else(|| {
                    source_uniform_requirement_summary
                        .as_ref()
                        .and_then(|summary| {
                            (summary.field_count != summary.resolved_field_count).then(|| {
                                format!(
                                    "unresolved DH scalar source uniforms: {}",
                                    summary.unresolved_field_names.join(", ")
                                )
                            })
                        })
                });
                Err(GalError::unsupported_feature(format!(
                    "Distant Horizons source has no complete paired-stage source contract: {}",
                    source_preprocess_error
                        .as_deref()
                        .or(source_lowering_error.as_deref())
                        .or(uniform_status.as_deref())
                        .or(source_resource_binding_error.as_deref())
                        .unwrap_or("missing paired lowering/resource provenance")
                )))
            }
        }
    }

    /// Returns the separately lowered `dh_water` program only when its own
    /// source pair and semantic resource bindings are complete. This is a
    /// preparation artifact: callers still need a dedicated late source pass
    /// with the owned depth-history resource required by the contract.
    pub(crate) fn prepared_lowered_distant_horizons_translucent_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredDistantHorizonsSourceProgram>>> {
        memoized_source_program(
            &self.prepared_program_memos.distant_horizons_translucent,
            self.distant_horizons_source_candidate_epoch,
            || self.prepared_lowered_distant_horizons_translucent_source_program_uncached(),
        )
    }

    pub(super) fn prepared_lowered_distant_horizons_translucent_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredDistantHorizonsSourceProgram>> {
        match &self.distant_horizons_source_candidate {
            DistantHorizonsSourceCandidateState::Unavailable
            | DistantHorizonsSourceCandidateState::Disabled { .. }
            | DistantHorizonsSourceCandidateState::Rejected { .. } => Ok(None),
            DistantHorizonsSourceCandidateState::Discovered {
                translucent_contract: DistantHorizonsTranslucentSourceCandidate::Unavailable,
                ..
            } => Ok(None),
            DistantHorizonsSourceCandidateState::Discovered {
                translucent_contract: DistantHorizonsTranslucentSourceCandidate::Rejected(reason),
                ..
            } => Err(GalError::unsupported_feature(format!(
                "Distant Horizons translucent source contract was rejected: {reason}"
            ))),
            DistantHorizonsSourceCandidateState::Discovered {
                translucent_contract: DistantHorizonsTranslucentSourceCandidate::Discovered(
                    contract,
                ),
                translucent_source_summary: Some(_),
                translucent_source_preprocess_error: None,
                translucent_source_lowering_error: None,
                translucent_source_lowered_pair: Some(lowered),
                translucent_source_resource_binding_error: None,
                translucent_source_resource_bindings: Some(bindings),
                ..
            } => prepare_lowered_distant_horizons_source_program(contract, lowered, bindings)
                .map(Some),
            DistantHorizonsSourceCandidateState::Discovered {
                translucent_source_preprocess_error,
                translucent_source_lowering_error,
                translucent_source_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "Distant Horizons translucent source has no complete paired-stage source contract: {}",
                translucent_source_preprocess_error
                    .as_deref()
                    .or(translucent_source_lowering_error.as_deref())
                    .or(translucent_source_resource_binding_error.as_deref())
                    .unwrap_or("missing retained lowering/resource provenance")
            ))),
        }
    }

    /// Prepares the independently discovered vanilla-cloud source from one
    /// exact pack generation. The prepared program is consumed by the
    /// Rust-owned cloud target writer during exact-frame assembly; preparation
    /// itself never mutates route admission or allocates backend resources.
    pub(crate) fn prepared_lowered_cloud_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredCloudSourceProgram>>> {
        memoized_source_program(&self.prepared_program_memos.cloud, self.source_candidate_epoch, || {
            self.prepared_lowered_cloud_source_program_uncached()
        })
    }

    pub(super) fn prepared_lowered_cloud_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredCloudSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                cloud_contract:
                    Some(CloudPassContract {
                        face_disposition: CloudFaceDisposition::SuppressVanillaFaces,
                        ..
                    }),
                ..
            } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                cloud_contract: Some(contract),
                cloud_contract_error: None,
                cloud_lowered_pair: Some(lowered),
                cloud_source_resource_binding_error: None,
                cloud_source_resource_bindings: Some(bindings),
                ..
            } => prepare_lowered_cloud_source_program(contract, lowered, bindings).map(Some),
            TerrainSourceCandidateState::Discovered {
                cloud_contract_error,
                cloud_source_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "selected cloud source has no complete paired-stage resource contract: {}",
                cloud_contract_error
                    .as_deref()
                    .or(cloud_source_resource_binding_error.as_deref())
                    .unwrap_or("missing contract, lowering, or semantic resource bindings")
            ))),
        }
    }

    /// Source-derived policy for vanilla cloud faces. A suppressed result is
    /// an admitted shader-pack behavior, not an unavailable renderer path.
    pub(crate) fn suppresses_vanilla_cloud_faces(&self) -> bool {
        matches!(
            &self.source_candidate,
            TerrainSourceCandidateState::Discovered {
                cloud_contract: Some(CloudPassContract {
                    face_disposition: CloudFaceDisposition::SuppressVanillaFaces,
                    ..
                }),
                ..
            }
        )
    }

    /// Prepares the selected weather source program from one coherent pack
    /// generation. This has no draw/route effect until a dedicated weather
    /// target writer is staged by the combined source-frame transaction.
    pub(crate) fn prepared_lowered_weather_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredWeatherSourceProgram>>> {
        memoized_source_program(&self.prepared_program_memos.weather, self.source_candidate_epoch, || {
            self.prepared_lowered_weather_source_program_uncached()
        })
    }

    pub(super) fn prepared_lowered_weather_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredWeatherSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                weather_contract: Some(contract),
                weather_contract_error: None,
                weather_lowered_pair: Some(lowered),
                weather_source_resource_binding_error: None,
                weather_source_resource_bindings: Some(bindings),
                ..
            } => prepare_lowered_weather_source_program(contract, lowered, bindings).map(Some),
            TerrainSourceCandidateState::Discovered {
                weather_contract_error,
                weather_source_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "selected weather source has no complete paired-stage resource contract: {}",
                weather_contract_error
                    .as_deref()
                    .or(weather_source_resource_binding_error.as_deref())
                    .unwrap_or("missing contract, lowering, or semantic resource bindings")
            ))),
        }
    }

    /// Prepares the independently discovered generic textured-material stage
    /// from one exact source generation. This is deliberately not route
    /// admission: a selected frame still needs a Rust-owned material stream,
    /// named target writer, and per-frame resource coherence before any draw
    /// can execute.
    pub(crate) fn prepared_lowered_textured_material_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredTexturedMaterialSourceProgram>>> {
        memoized_source_program(&self.prepared_program_memos.textured_material, self.source_candidate_epoch, || {
            self.prepared_lowered_textured_material_source_program_uncached()
        })
    }

    pub(super) fn prepared_lowered_textured_material_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredTexturedMaterialSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                textured_material_contract: Some(contract),
                textured_material_contract_error: None,
                textured_material_lowered_pair: Some(lowered),
                textured_material_source_resource_binding_error: None,
                textured_material_source_resource_bindings: Some(bindings),
                ..
            } => prepare_lowered_textured_material_source_program(contract, lowered, bindings)
                .map(Some),
            TerrainSourceCandidateState::Discovered {
                textured_material_contract_error,
                textured_material_source_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "selected textured material source has no complete paired-stage resource contract: {}",
                textured_material_contract_error
                    .as_deref()
                    .or(textured_material_source_resource_binding_error.as_deref())
                    .unwrap_or("missing contract, lowering, or semantic resource bindings")
            ))),
        }
    }

    /// Prepares the discovered ordinary-entity source only after its entity
    /// contract, lowered pair, and local-material semantic bindings agree.
    /// This is deliberately preparation-only: it cannot compile shaders,
    /// stage a Rust target, allocate a resource set, select a route, or issue
    /// a draw.
    pub(crate) fn prepared_lowered_entity_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredEntitySourceProgram>>> {
        memoized_source_program(&self.prepared_program_memos.entity, self.source_candidate_epoch, || {
            self.prepared_lowered_entity_source_program_uncached()
        })
    }

    pub(super) fn prepared_lowered_entity_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredEntitySourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                entity_contract: Some(contract),
                entity_contract_error: None,
                entity_lowered_pair: Some(lowered),
                entity_source_resource_binding_count: Some(_),
                entity_source_resource_binding_error: None,
                entity_source_resource_bindings: Some(bindings),
                ..
            } => prepare_lowered_entity_source_program(contract, lowered, bindings).map(Some),
            TerrainSourceCandidateState::Discovered {
                entity_contract_error,
                entity_source_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "selected entity source preparation is incomplete: {}; {}",
                entity_contract_error
                    .as_deref()
                    .unwrap_or("missing entity source contract or lowered pair"),
                entity_source_resource_binding_error
                    .as_deref()
                    .unwrap_or("missing entity source resource bindings")
            ))),
        }
    }

    /// Prepares the discovered first-person source only after its hand
    /// contract, lowered pair, and owned semantic resource bindings agree.
    /// It remains route-inactive until the dedicated hand writer supplies the
    /// copied first-person projection and depth-clear contract.
    pub(crate) fn prepared_lowered_hand_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredHandSourceProgram>>> {
        memoized_source_program(&self.prepared_program_memos.hand, self.source_candidate_epoch, || {
            self.prepared_lowered_hand_source_program_uncached()
        })
    }

    pub(super) fn prepared_lowered_hand_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredHandSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                hand_contract: Some(contract),
                hand_contract_error: None,
                hand_lowered_pair: Some(lowered),
                hand_source_resource_binding_count: Some(_),
                hand_source_resource_binding_error: None,
                hand_source_resource_bindings: Some(bindings),
                ..
            } => prepare_lowered_hand_source_program(contract, lowered, bindings).map(Some),
            TerrainSourceCandidateState::Discovered {
                hand_contract_error,
                hand_source_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "selected hand source preparation is incomplete: {}; {}",
                hand_contract_error
                    .as_deref()
                    .unwrap_or("missing hand source contract or lowered pair"),
                hand_source_resource_binding_error
                    .as_deref()
                    .unwrap_or("missing hand source resource bindings")
            ))),
        }
    }

    /// Prepares the exact paired source stages and semantic sampler plan
    /// retained during discovery. This cannot compile a backend program,
    /// allocate a resource layout, select a route, or issue a draw.
    pub(crate) fn prepared_lowered_terrain_source_program(
        &self,
        kind: TerrainMaterialProgramKind,
    ) -> GalResult<Option<Arc<LoweredTerrainSourceProgram>>> {
        let slot = match kind {
            TerrainMaterialProgramKind::Opaque => 0,
            TerrainMaterialProgramKind::Cutout => 1,
            TerrainMaterialProgramKind::Translucent => 2,
        };
        self.memoized_terrain_program(slot, || {
            self.prepared_lowered_terrain_source_program_uncached(kind)
        })
    }

    pub(super) fn memoized_terrain_program(
        &self,
        slot: u8,
        build: impl FnOnce() -> GalResult<Option<LoweredTerrainSourceProgram>>,
    ) -> GalResult<Option<Arc<LoweredTerrainSourceProgram>>> {
        let epoch = self.source_candidate_epoch;
        if let Some((_, _, program)) = self
            .prepared_program_memos
            .terrain
            .borrow()
            .iter()
            .find(|(built_epoch, built_slot, _)| *built_epoch == epoch && *built_slot == slot)
        {
            return Ok(Some(program.clone()));
        }
        let built = build()?.map(Arc::new);
        let mut memos = self.prepared_program_memos.terrain.borrow_mut();
        memos.retain(|(built_epoch, built_slot, _)| *built_epoch == epoch && *built_slot != slot);
        if let Some(program) = built.as_ref() {
            memos.push((epoch, slot, program.clone()));
        }
        Ok(built)
    }

    pub(super) fn prepared_lowered_terrain_source_program_uncached(
        &self,
        kind: TerrainMaterialProgramKind,
    ) -> GalResult<Option<LoweredTerrainSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                contract,
                source_summary: Some(_),
                source_preprocess_error: None,
                source_lowering_summary: Some(_),
                source_lowering_error: None,
                source_uniform_requirement_summary: Some(summary),
                source_uniform_requirement_error: None,
                source_lowered_pair: Some(lowered),
                source_resource_binding_count: Some(_),
                source_resource_binding_error: None,
                source_resource_bindings: Some(bindings),
                ..
            } if summary.field_count == summary.resolved_field_count => {
                prepare_lowered_terrain_source_program(contract, lowered, bindings, kind).map(Some)
            }
            TerrainSourceCandidateState::Discovered {
                contract,
                source_preprocess_error,
                source_lowering_error,
                source_uniform_requirement_summary,
                source_uniform_requirement_error,
                source_resource_binding_error,
                ..
            } => {
                let contract_status = contract
                    .require_selected_subset()
                    .err()
                    .map(|error| error.to_string())
                    .unwrap_or_else(|| "otherwise source-contract supported".to_string());
                let uniform_status = source_uniform_requirement_error.clone().or_else(|| {
                    source_uniform_requirement_summary
                        .as_ref()
                        .and_then(|summary| {
                            (summary.field_count != summary.resolved_field_count).then(|| {
                                format!(
                                    "unresolved scalar source uniforms: {}",
                                    summary.unresolved_field_names.join(", ")
                                )
                            })
                        })
                });
                Err(GalError::unsupported_feature(format!(
                    "selected terrain source has no complete paired-stage source contract: {}; source contract: {contract_status}",
                    source_preprocess_error
                        .as_deref()
                        .or(source_lowering_error.as_deref())
                        .or(uniform_status.as_deref())
                        .or(source_resource_binding_error.as_deref())
                        .unwrap_or("missing paired lowering/resource provenance")
                )))
            }
        }
    }

    /// Prepares the independently lowered translucent source stage. Target
    /// allocation and pass construction remain frame-scoped: the caller must
    /// provide the current color history, depth, and blend semantics before
    /// admitting the pass.
    pub(crate) fn prepared_lowered_translucent_terrain_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredTerrainSourceProgram>>> {
        self.memoized_terrain_program(3, || {
            self.prepared_lowered_translucent_terrain_source_program_uncached()
        })
    }

    pub(super) fn prepared_lowered_translucent_terrain_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredTerrainSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                translucent_contract: Some(contract),
                translucent_source_summary: Some(_),
                translucent_source_preprocess_error: None,
                translucent_source_lowering_error: None,
                translucent_lowered_pair: Some(lowered),
                translucent_source_resource_binding_count: Some(_),
                translucent_source_resource_binding_error: None,
                translucent_source_resource_bindings: Some(bindings),
                ..
            } => prepare_lowered_translucent_terrain_source_program(contract, lowered, bindings)
                .map(Some),
            TerrainSourceCandidateState::Discovered {
                translucent_contract_error,
                translucent_source_preprocess_error,
                translucent_source_lowering_error,
                translucent_source_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "selected terrain source has no complete translucent source contract: {}",
                translucent_source_preprocess_error
                    .as_deref()
                    .or(translucent_source_lowering_error.as_deref())
                    .or(translucent_source_resource_binding_error.as_deref())
                    .or(translucent_contract_error.as_deref())
                    .unwrap_or("missing translucent source lowering/resource provenance")
            ))),
        }
    }

    /// Prepares the exact scoped shadow stages retained during discovery.
    /// This still cannot select source execution: callers must separately
    /// provide a matching shadow attachment/output contract and resource set.
    pub(crate) fn prepared_lowered_shadow_source_program(
        &self,
    ) -> GalResult<Option<Arc<LoweredTerrainSourceProgram>>> {
        self.memoized_terrain_program(4, || {
            self.prepared_lowered_shadow_source_program_uncached()
        })
    }

    pub(super) fn prepared_lowered_shadow_source_program_uncached(
        &self,
    ) -> GalResult<Option<LoweredTerrainSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                generation,
                pack_name,
                source_shadow_summary: Some(_),
                source_shadow_preprocess_error: None,
                source_shadow_lowering_error: None,
                source_lowered_shadow_pair: Some(lowered),
                source_shadow_resource_binding_error: None,
                source_shadow_resource_bindings: Some(bindings),
                ..
            } => {
                let expected_outputs = [
                    ShadowFragmentOutput::ShadowColor,
                    ShadowFragmentOutput::LightShaftColor,
                ];
                if lowered.fragment().outputs() != expected_outputs
                    && lowered.fragment().outputs() != [ShadowFragmentOutput::ShadowColor]
                {
                    return Err(GalError::unsupported_feature(
                        "selected terrain source shadow output contract requires primary shadow color with optional secondary color",
                    ));
                }
                prepare_lowered_shadow_source_program(pack_name, *generation, lowered, bindings)
                    .map(Some)
            }
            TerrainSourceCandidateState::Discovered {
                source_shadow_preprocess_error,
                source_shadow_lowering_error,
                source_shadow_resource_binding_error,
                ..
            } => Err(GalError::unsupported_feature(format!(
                "selected terrain source has no complete scoped shadow program: {}",
                source_shadow_preprocess_error
                    .as_deref()
                    .or(source_shadow_lowering_error.as_deref())
                    .or(source_shadow_resource_binding_error.as_deref())
                    .unwrap_or("missing shadow lowering/resource provenance")
            ))),
        }
    }

    /// Builds the internal terrain fixture after source discovery. This
    /// exists only for focused tests of resource-generation coherence; it
    /// neither prepares nor executes the selected shader-pack source.
    pub(crate) fn candidate_fixture_terrain_program(
        &self,
        kind: TerrainMaterialProgramKind,
        frame_counter: u64,
    ) -> GalResult<Option<TerrainSourceProgramCandidate>> {
        let (contract, source_vertex_interface) = match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
            TerrainSourceCandidateState::Discovered {
                contract,
                source_summary: Some(_),
                source_preprocess_error: None,
                source_vertex_interface: Some(interface),
                source_lowering_summary: Some(_),
                source_lowering_error: None,
                source_uniform_requirement_summary: Some(summary),
                source_uniform_requirement_error: None,
                source_lowered_pair: Some(_),
                source_resource_binding_count: Some(_),
                source_resource_binding_error: None,
                source_resource_bindings: Some(_),
                ..
            } if summary.field_count == summary.resolved_field_count => (contract, interface),
            TerrainSourceCandidateState::Discovered {
                contract,
                source_preprocess_error,
                source_lowering_error,
                source_uniform_requirement_summary,
                source_uniform_requirement_error,
                source_resource_binding_error,
                ..
            } => {
                let contract_status = contract
                    .require_selected_subset()
                    .err()
                    .map(|error| error.to_string())
                    .unwrap_or_else(|| "otherwise source-contract supported".to_string());
                let uniform_status = source_uniform_requirement_error.clone().or_else(|| {
                    source_uniform_requirement_summary
                        .as_ref()
                        .and_then(|summary| {
                            (summary.field_count != summary.resolved_field_count).then(|| {
                                format!(
                                    "unresolved scalar source uniforms: {}",
                                    summary.unresolved_field_names.join(", ")
                                )
                            })
                        })
                });
                return Err(GalError::unsupported_feature(format!(
                    "selected terrain source has no complete paired-stage source contract: {}; source contract: {contract_status}",
                    source_preprocess_error
                        .as_deref()
                        .or(source_lowering_error.as_deref())
                        .or(uniform_status.as_deref())
                        .or(source_resource_binding_error.as_deref())
                        .unwrap_or("missing paired lowering/resource provenance")
                )));
            }
        };
        let binding = self.candidate_shader_binding(frame_counter)?;
        let readiness = if contract
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
        {
            Some(
                self.terrain_colored_light
                    .as_ref()
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "selected terrain source requires a complete owned colored voxel-light volume",
                        )
                    })?
                    .readiness()?,
            )
        } else {
            None
        };
        let program = complementary_terrain_subset_program_with_resources(
            contract,
            kind,
            readiness.as_ref(),
            frame_counter,
        )?;
        // Keep source admission honest if the source contract becomes
        // lowerable before the shared mesh ABI grows the required semantics.
        // The check remains after contract admission so today's explicit
        // `UnloweredTerrainSource` status is retained as the primary reason.
        source_vertex_interface.require_current_world_mesh_support()?;
        if program.requires(TerrainProgramResource::ColoredVoxelLightVolume) != binding.is_some() {
            return Err(GalError::invalid_argument(
                "selected terrain source program/resource binding requirements disagree",
            ));
        }
        Ok(Some(TerrainSourceProgramCandidate { program, binding }))
    }
}
