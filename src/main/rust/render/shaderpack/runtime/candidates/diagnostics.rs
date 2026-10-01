//! Admission diagnostics for each source-program family.

use super::*;

impl ShaderPackRuntimeExecutor {
    pub(crate) fn has_candidate_material_contract(&self) -> bool {
        matches!(
            self.source_candidate,
            TerrainSourceCandidateState::Discovered { .. }
        )
    }

    /// Bounded provenance for the generic textured-material source writer.
    /// The lowered pair is retained only after its scoped source has passed
    /// backend-neutral lowering; the world frontend later binds its named
    /// targets and appends the writer to the combined source transaction.
    pub(crate) fn candidate_textured_material_source_diagnostic(
        &self,
    ) -> (&'static str, Option<&str>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                textured_material_contract,
                textured_material_contract_error,
                textured_material_lowered_pair,
                textured_material_source_resource_binding_error,
                textured_material_source_resource_bindings,
                ..
            } => {
                if textured_material_lowered_pair.is_some()
                    && textured_material_source_resource_bindings.is_some()
                {
                    ("prepared", None)
                } else if textured_material_lowered_pair.is_some() {
                    (
                        "resource-rejected",
                        textured_material_source_resource_binding_error.as_deref(),
                    )
                } else if textured_material_contract.is_some() {
                    (
                        "lowering-rejected",
                        textured_material_contract_error.as_deref(),
                    )
                } else {
                    ("unavailable", textured_material_contract_error.as_deref())
                }
            }
            TerrainSourceCandidateState::Unavailable => ("unavailable", None),
            TerrainSourceCandidateState::Disabled { .. } => ("disabled", None),
            TerrainSourceCandidateState::Rejected { reason, .. } => ("rejected", Some(reason)),
        }
    }

    /// Bounded provenance for the Rust-owned entity writer. This reports only
    /// source-program/resource readiness; whole-frame route selection remains
    /// the frontend's exact-frame decision.
    pub(crate) fn candidate_entity_source_diagnostic(&self) -> (&'static str, Option<&str>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                entity_contract: Some(_),
                entity_lowered_pair: Some(_),
                entity_source_resource_bindings: Some(_),
                ..
            } => ("prepared", None),
            TerrainSourceCandidateState::Discovered {
                entity_contract: Some(_),
                entity_lowered_pair: Some(_),
                entity_source_resource_binding_error,
                ..
            } => (
                "resource-rejected",
                entity_source_resource_binding_error.as_deref(),
            ),
            TerrainSourceCandidateState::Discovered {
                entity_contract: Some(_),
                entity_contract_error,
                ..
            } => ("lowering-rejected", entity_contract_error.as_deref()),
            TerrainSourceCandidateState::Discovered {
                entity_contract_error,
                ..
            } => ("unavailable", entity_contract_error.as_deref()),
            TerrainSourceCandidateState::Unavailable => ("unavailable", None),
            TerrainSourceCandidateState::Disabled { .. } => ("disabled", None),
            TerrainSourceCandidateState::Rejected { reason, .. } => ("rejected", Some(reason)),
        }
    }

    /// Bounded provenance for the distinct first-person hand stage. Contract
    /// preparation and route selection remain separate, but the hand writer
    /// is now a Rust-owned executable source family when its copied projection
    /// and depth-clear semantics are present.
    pub(crate) fn candidate_hand_source_diagnostic(&self) -> (&'static str, Option<&str>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                hand_contract: Some(_),
                hand_lowered_pair: Some(_),
                hand_source_resource_bindings: Some(_),
                ..
            } => ("prepared", None),
            TerrainSourceCandidateState::Discovered {
                hand_contract: Some(_),
                hand_lowered_pair: Some(_),
                hand_source_resource_binding_error,
                ..
            } => (
                "resource-rejected",
                hand_source_resource_binding_error.as_deref(),
            ),
            TerrainSourceCandidateState::Discovered {
                hand_contract: Some(_),
                hand_contract_error,
                ..
            } => ("lowering-rejected", hand_contract_error.as_deref()),
            TerrainSourceCandidateState::Discovered {
                hand_contract_error,
                ..
            } => ("unavailable", hand_contract_error.as_deref()),
            TerrainSourceCandidateState::Unavailable => ("unavailable", None),
            TerrainSourceCandidateState::Disabled { .. } => ("disabled", None),
            TerrainSourceCandidateState::Rejected { reason, .. } => ("rejected", Some(reason)),
        }
    }

    /// Source-only diagnostic for the independent weather stage. It does not
    /// authorize a producer route, target, or presentation path.
    pub(crate) fn candidate_weather_source_diagnostic(&self) -> (&'static str, Option<&str>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                weather_contract,
                weather_contract_error,
                weather_lowered_pair,
                weather_source_resource_binding_error,
                weather_source_resource_bindings,
                ..
            } => {
                if weather_lowered_pair.is_some() && weather_source_resource_bindings.is_some() {
                    ("prepared", None)
                } else if weather_lowered_pair.is_some() {
                    (
                        "resource-rejected",
                        weather_source_resource_binding_error.as_deref(),
                    )
                } else if weather_contract.is_some() {
                    ("lowering-rejected", weather_contract_error.as_deref())
                } else {
                    ("unavailable", weather_contract_error.as_deref())
                }
            }
            TerrainSourceCandidateState::Unavailable => ("unavailable", None),
            TerrainSourceCandidateState::Disabled { .. } => ("disabled", None),
            TerrainSourceCandidateState::Rejected { reason, .. } => ("rejected", Some(reason)),
        }
    }

    /// Source-only preparation state for vanilla clouds. A `prepared` value
    /// means the selected source pair and semantic resource plan agree; it
    /// never authorizes cloud routing or a writer by itself.
    pub(crate) fn candidate_cloud_source_diagnostic(&self) -> (&'static str, Option<&str>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                cloud_contract:
                    Some(CloudPassContract {
                        face_disposition: CloudFaceDisposition::SuppressVanillaFaces,
                        ..
                    }),
                ..
            } => ("suppressed", None),
            TerrainSourceCandidateState::Discovered {
                cloud_lowered_pair,
                cloud_source_resource_binding_error,
                cloud_source_resource_bindings,
                ..
            } => {
                if cloud_lowered_pair.is_some() && cloud_source_resource_bindings.is_some() {
                    ("prepared", None)
                } else if cloud_lowered_pair.is_some() {
                    (
                        "resource-rejected",
                        cloud_source_resource_binding_error.as_deref(),
                    )
                } else {
                    match &self.source_candidate {
                        TerrainSourceCandidateState::Discovered {
                            cloud_contract: Some(_),
                            cloud_contract_error,
                            ..
                        } => ("lowering-rejected", cloud_contract_error.as_deref()),
                        TerrainSourceCandidateState::Discovered {
                            cloud_contract_error,
                            ..
                        } => ("unavailable", cloud_contract_error.as_deref()),
                        _ => unreachable!("matched discovered cloud candidate"),
                    }
                }
            }
            TerrainSourceCandidateState::Unavailable => ("unavailable", None),
            TerrainSourceCandidateState::Disabled { .. } => ("disabled", None),
            TerrainSourceCandidateState::Rejected { reason, .. } => ("rejected", Some(reason)),
        }
    }

    /// Bounded provenance for source-route admission diagnostics. This is
    /// intentionally semantic-only: it exposes neither shader objects nor
    /// backend state, and lets callers distinguish an absent candidate from a
    /// source contract that simply does not require the colored-light volume.
    pub(crate) fn source_candidate_admission_diagnostic(
        &self,
    ) -> (&'static str, Option<&str>, Option<bool>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable => ("unavailable", None, None),
            TerrainSourceCandidateState::Disabled { .. } => ("disabled", None, None),
            TerrainSourceCandidateState::Rejected { reason, .. } => {
                ("rejected", Some(reason.as_str()), None)
            }
            TerrainSourceCandidateState::Discovered {
                requires_colored_voxel_light,
                ..
            } => ("discovered", None, Some(*requires_colored_voxel_light)),
        }
    }

    /// Bounded source-only status for the distinct translucent terrain stage.
    /// This is intentionally separate from the normal terrain candidate: a
    /// discovered water/glass source never authorizes a lowerer, resource set,
    /// or draw until its own contract is complete.
    pub(crate) fn source_candidate_translucent_diagnostic(
        &self,
    ) -> (bool, Option<&str>, Option<usize>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                translucent_contract,
                translucent_contract_error,
                translucent_source_preprocess_error,
                translucent_source_lowering_error,
                translucent_source_resource_binding_error,
                ..
            } => (
                translucent_contract.is_some(),
                translucent_source_preprocess_error
                    .as_deref()
                    .or(translucent_source_lowering_error.as_deref())
                    .or(translucent_source_resource_binding_error.as_deref())
                    .or(translucent_contract_error.as_deref()),
                translucent_contract
                    .as_ref()
                    .map(|contract| contract.unsupported.len()),
            ),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => (false, None, None),
        }
    }

    /// Bounded semantic sampler/storage provenance for the distinct
    /// translucent source stage. This is capture-only evidence: the names
    /// are pack source identifiers paired with Rust-owned semantic roles,
    /// never backend bindings or native resources.
    pub(crate) fn source_candidate_translucent_resource_diagnostic(
        &self,
    ) -> (Option<u32>, Option<&str>, Vec<(String, String)>) {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                translucent_source_resource_binding_count,
                translucent_source_resource_binding_error,
                translucent_source_resource_bindings,
                ..
            } => {
                let bindings = translucent_source_resource_bindings
                    .as_ref()
                    .map(|plan| {
                        plan.bindings()
                            .iter()
                            .take(32)
                            .map(|binding| {
                                (
                                    binding.resource_name().to_string(),
                                    binding.role().diagnostic_name(),
                                )
                            })
                            .collect()
                    })
                    .unwrap_or_default();
                (
                    *translucent_source_resource_binding_count,
                    translucent_source_resource_binding_error.as_deref(),
                    bindings,
                )
            }
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => (None, None, Vec::new()),
        }
    }
}
