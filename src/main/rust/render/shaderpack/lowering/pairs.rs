//! Lowered vertex/fragment source pairs for each program stage.

use super::*;

/// Coherently lowered source pair. It is private source preparation only and
/// does not create resources or admit a selected-source render route.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredTerrainFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered source pair for the selected pack's distinct
/// translucent terrain stage. Keeping it separate from normal terrain makes
/// its depth/history/blend contract an explicit later requirement.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTranslucentTerrainSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredTranslucentTerrainFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered generic textured-material source pair. It deliberately
/// reuses the owned source vertex stream and transforms, while retaining its
/// distinct output schema so it cannot be mistaken for terrain or water.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTexturedMaterialSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredTexturedMaterialFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered ordinary entity source pair. It shares the owned
/// indexed semantic mesh stream with terrain, but retains its entity-specific
/// transform and output contract so an entity draw cannot be relabelled as a
/// terrain or generic textured-material draw.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredEntitySourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredTerrainFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered first-person hand/item source pair. It shares the
/// owned indexed semantic mesh stream with ordinary entities, but its copied
/// hand projection and isolated depth domain are deliberately a distinct
/// semantic contract. A caller therefore cannot relabel a hand draw as a
/// world entity merely because both source programs use legacy gbuffer
/// matrix names.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredHandSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredTerrainFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered weather-source pair. The compact world-material stream
/// is reused, while weather retains its own named output semantics.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredWeatherSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredWeatherFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered source pair for vanilla clouds. It shares the owned
/// camera-relative material stream but no terrain/weather target contract.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredCloudSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredCloudFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered source pair for a Rust-owned shadow-material pass.
///
/// This remains source preparation only. In particular, it neither allocates
/// a shadow-color attachment nor makes a selected shader-pack route
/// executable. Keeping it distinct from [`LoweredTerrainSourcePair`] prevents
/// a source shadow output from being mislabeled as a terrain G-buffer output.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredShadowSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredShadowFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
    /// Source storage-image roles whose writes are produced by a confirmed
    /// Rust semantic runtime instead of the lowered shadow program.
    pub(super) owned_storage_roles: Vec<TerrainSourceResourceRole>,
}

/// Coherently lowered source pair for the distinct Distant Horizons opaque
/// terrain stage. Its vertex transforms use the source-declared `dh*`
/// matrices rather than borrowing normal terrain or Iris state.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredDistantHorizonsSourcePair {
    pub(super) vertex: LoweredTerrainVertexSource,
    pub(super) fragment: LoweredDistantHorizonsFragmentSource,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered source pair for a source-defined fullscreen stage such
/// as a deferred or composite DH-depth consumer. This remains preparation
/// only: it has no target, pipeline, resource set, command, or route effect.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredFullscreenSourcePair {
    pub(super) vertex: LoweredFullscreenSourceVertex,
    pub(super) fragment: LoweredFullscreenSourceFragment,
    pub(super) uniform_contract: TerrainSourceUniformContract,
    pub(super) varying_contract: TerrainSourceVaryingContract,
    pub(super) opaque_resource_contract: TerrainSourceOpaqueResourceContract,
    pub(super) raster_primitive: FullscreenSourceRasterPrimitive,
}

/// Bounded provenance from successful paired lowering. The expanded source is
/// intentionally not retained by discovery; these counts make lowering state
/// observable without becoming a runtime program or resource plan.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainSourceLoweringSummary {
    pub scalar_uniform_count: u32,
    pub varying_count: u32,
    /// All source-declared opaque resources retained with deterministic
    /// lowering bindings, including declaration-only global-header entries.
    pub opaque_resource_count: u32,
    /// Opaque resources referenced by at least one expanded terrain stage and
    /// therefore requiring a semantic runtime binding before admission.
    pub active_opaque_resource_count: u32,
}

impl LoweredTerrainSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredTerrainFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn summary(&self) -> TerrainSourceLoweringSummary {
        TerrainSourceLoweringSummary {
            scalar_uniform_count: self.uniform_contract.declarations.len() as u32,
            varying_count: self.varying_contract.fields.len() as u32,
            opaque_resource_count: self.opaque_resource_contract.resources.len() as u32,
            active_opaque_resource_count: self.opaque_resource_contract.active_resources().count()
                as u32,
        }
    }

    /// Confirms that both owned stages have completed the bounded legacy
    /// dialect lowering required before any backend compiler may see them.
    /// This does not create a program or a resource layout.
    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    /// Rejects a semantic role plan produced for a different lowered source
    /// pair. Names and deterministic lowering bindings must agree exactly;
    /// backend resource identity is intentionally outside this check.
    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        let expected = self
            .opaque_resource_contract
            .active_resources()
            .collect::<Vec<_>>();
        if expected.len() != bindings.bindings().len() {
            return Err(GalError::invalid_argument(format!(
                "terrain source resource plan has {} bindings but lowered pair requires {}",
                bindings.bindings().len(),
                expected.len()
            )));
        }
        for (resource, binding) in expected.iter().zip(bindings.bindings()) {
            if resource.name() != binding.resource_name()
                || resource.kind() != binding.kind()
                || resource.qualifiers() != binding.qualifiers()
                || resource.binding() != binding.binding()
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain source resource plan does not match lowered resource '{}' at binding {}",
                    resource.name(),
                    resource.binding()
                )));
            }
        }
        Ok(())
    }
}

impl LoweredTexturedMaterialSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredTexturedMaterialFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        require_matching_opaque_resource_bindings(
            "textured material source",
            &self.opaque_resource_contract,
            bindings,
        )
    }
}

impl LoweredEntitySourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredTerrainFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        require_matching_opaque_resource_bindings(
            "entity source",
            &self.opaque_resource_contract,
            bindings,
        )
    }
}

impl LoweredHandSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredTerrainFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        require_matching_opaque_resource_bindings(
            "hand source",
            &self.opaque_resource_contract,
            bindings,
        )
    }
}

impl LoweredWeatherSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredWeatherFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        require_matching_opaque_resource_bindings(
            "weather source",
            &self.opaque_resource_contract,
            bindings,
        )
    }
}

impl LoweredCloudSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredCloudFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        require_matching_opaque_resource_bindings(
            "cloud source",
            &self.opaque_resource_contract,
            bindings,
        )
    }
}

impl LoweredTranslucentTerrainSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredTranslucentTerrainFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        let expected = self
            .opaque_resource_contract
            .active_resources()
            .collect::<Vec<_>>();
        if expected.len() != bindings.bindings().len() {
            return Err(GalError::invalid_argument(format!(
                "translucent terrain source resource plan has {} bindings but lowered pair requires {}",
                bindings.bindings().len(),
                expected.len()
            )));
        }
        for (resource, binding) in expected.iter().zip(bindings.bindings()) {
            if resource.name() != binding.resource_name()
                || resource.kind() != binding.kind()
                || resource.qualifiers() != binding.qualifiers()
                || resource.binding() != binding.binding()
            {
                return Err(GalError::invalid_argument(format!(
                    "translucent terrain source resource plan does not match lowered resource '{}' at binding {}",
                    resource.name(),
                    resource.binding()
                )));
            }
        }
        Ok(())
    }
}

pub(super) fn require_matching_opaque_resource_bindings(
    source_label: &str,
    contract: &TerrainSourceOpaqueResourceContract,
    bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<()> {
    let expected = contract.active_resources().collect::<Vec<_>>();
    if expected.len() != bindings.bindings().len() {
        return Err(GalError::invalid_argument(format!(
            "{source_label} resource plan has {} bindings but lowered pair requires {}",
            bindings.bindings().len(),
            expected.len()
        )));
    }
    for (resource, binding) in expected.iter().zip(bindings.bindings()) {
        if resource.name() != binding.resource_name()
            || resource.kind() != binding.kind()
            || resource.qualifiers() != binding.qualifiers()
            || resource.binding() != binding.binding()
        {
            return Err(GalError::invalid_argument(format!(
                "{source_label} resource plan does not match lowered resource '{}' at binding {}",
                resource.name(),
                resource.binding()
            )));
        }
    }
    Ok(())
}

impl LoweredShadowSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredShadowFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    /// Semantic roles whose legacy shadow writes were explicitly replaced by
    /// an owned Rust producer. These still participate in source-resource
    /// completeness even though the rewritten GLSL no longer declares them.
    pub fn owned_storage_roles(&self) -> &[TerrainSourceResourceRole] {
        &self.owned_storage_roles
    }

    /// Confirms that both source stages have completed the bounded legacy
    /// dialect lowering needed before any future backend compiler may see
    /// them. It still does not create a program or admit source execution.
    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    /// Rejects a semantic role plan produced for a different lowered shadow
    /// pair. The comparison is source-name and deterministic binding based;
    /// native resource identity remains outside the source contract.
    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        let expected = self
            .opaque_resource_contract
            .active_resources()
            .collect::<Vec<_>>();
        if expected.len() != bindings.bindings().len() {
            return Err(GalError::invalid_argument(format!(
                "shadow source resource plan has {} bindings but lowered pair requires {}",
                bindings.bindings().len(),
                expected.len()
            )));
        }
        for (resource, binding) in expected.iter().zip(bindings.bindings()) {
            if resource.name() != binding.resource_name()
                || resource.kind() != binding.kind()
                || resource.binding() != binding.binding()
            {
                return Err(GalError::invalid_argument(format!(
                    "shadow source resource plan does not match lowered resource '{}' at binding {}",
                    resource.name(),
                    resource.binding()
                )));
            }
        }
        Ok(())
    }
}

impl LoweredDistantHorizonsSourcePair {
    pub fn vertex(&self) -> &LoweredTerrainVertexSource {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredDistantHorizonsFragmentSource {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    /// DH lowering is source preparation only. This explicit check keeps a
    /// future runtime from compiling a compatibility-dialect source merely
    /// because it has the right output name.
    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    /// Rejects a semantic role plan produced for another lowered source pair.
    /// The comparison is only source-name/type/qualifier/binding metadata;
    /// native resource identity stays wholly outside this source contract.
    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        let expected = self
            .opaque_resource_contract
            .active_resources()
            .collect::<Vec<_>>();
        if expected.len() != bindings.bindings().len() {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons source resource plan has {} bindings but lowered pair requires {}",
                bindings.bindings().len(),
                expected.len()
            )));
        }
        for (resource, binding) in expected.iter().zip(bindings.bindings()) {
            if resource.name() != binding.resource_name()
                || resource.kind() != binding.kind()
                || resource.qualifiers() != binding.qualifiers()
                || resource.binding() != binding.binding()
            {
                return Err(GalError::invalid_argument(format!(
                    "Distant Horizons source resource plan does not match lowered resource '{}' at binding {}",
                    resource.name(),
                    resource.binding()
                )));
            }
        }
        Ok(())
    }
}

impl LoweredFullscreenSourcePair {
    pub fn raster_primitive(&self) -> FullscreenSourceRasterPrimitive {
        self.raster_primitive
    }

    pub fn vertex(&self) -> &LoweredFullscreenSourceVertex {
        &self.vertex
    }

    pub fn fragment(&self) -> &LoweredFullscreenSourceFragment {
        &self.fragment
    }

    pub fn uniform_contract(&self) -> &TerrainSourceUniformContract {
        &self.uniform_contract
    }

    pub fn varying_contract(&self) -> &TerrainSourceVaryingContract {
        &self.varying_contract
    }

    pub fn opaque_resource_contract(&self) -> &TerrainSourceOpaqueResourceContract {
        &self.opaque_resource_contract
    }

    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        self.vertex
            .remaining_dialect()
            .require_backend_neutral_lowering()?;
        self.fragment
            .remaining_dialect()
            .require_backend_neutral_lowering()
    }

    pub fn require_matching_opaque_resource_bindings(
        &self,
        bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        require_matching_opaque_resource_bindings(
            "fullscreen source",
            &self.opaque_resource_contract,
            bindings,
        )
    }
}
