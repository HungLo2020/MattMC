//! Bounded source lowering from legacy terrain fragment syntax to explicit
//! shader-pack semantics.
//!
//! This is intentionally a source transform, not an Iris compatibility layer:
//! it owns text copied from the selected pack and emits GLSL 450 with named
//! terrain outputs. Remaining compatibility syntax is reported by dialect
//! preflight and keeps execution unavailable until a complete lowering exists.

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};

use super::dialect::{analyze_glsl_text, GlslDialectReport};
use super::preprocess::PreprocessedShaderSource;
use super::terrain_source_resources::{TerrainSourceResourceBindings, TerrainSourceResourceRole};

/// Named terrain outputs recovered from the audited `DRAWBUFFERS:06` source
/// contract. The identifiers are semantic; only a later pass description maps
/// them to a concrete attachment set.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainFragmentOutput {
    LitColor,
    MaterialAuxiliary,
    ViewSpaceNormal,
}

impl TerrainFragmentOutput {
    fn legacy_index(self) -> u32 {
        match self {
            Self::LitColor => 0,
            Self::MaterialAuxiliary => 1,
            Self::ViewSpaceNormal => 2,
        }
    }

    fn semantic_name(self) -> &'static str {
        match self {
            Self::LitColor => "out_terrain_lit_color",
            Self::MaterialAuxiliary => "out_terrain_material_auxiliary",
            Self::ViewSpaceNormal => "out_terrain_view_space_normal",
        }
    }
}

/// Named outputs from a source-derived shadow fragment. These are distinct
/// from terrain G-buffer outputs: a later shadow pass maps them to owned
/// shadow attachments rather than reusing a terrain attachment by index.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShadowFragmentOutput {
    ShadowColor,
    LightShaftColor,
}

impl ShadowFragmentOutput {
    fn legacy_index(self) -> u32 {
        match self {
            Self::ShadowColor => 0,
            Self::LightShaftColor => 1,
        }
    }

    fn semantic_name(self) -> &'static str {
        match self {
            Self::ShadowColor => "out_shadow_color",
            Self::LightShaftColor => "out_shadow_light_shaft_color",
        }
    }
}

/// Owned intermediate source from one lowering step. It does not indicate
/// executable readiness and has no backend-specific binding information.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainFragmentSource {
    entry_path: String,
    source: String,
    outputs: Vec<TerrainFragmentOutput>,
    remaining_dialect: GlslDialectReport,
}

/// Owned shadow fragment source after the bounded output lowering step. It
/// has no pipeline, attachment, or backend binding; those require a later
/// complete source shadow-pass contract.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredShadowFragmentSource {
    entry_path: String,
    source: String,
    outputs: Vec<ShadowFragmentOutput>,
    remaining_dialect: GlslDialectReport,
}

/// Owned GLSL 450 vertex source after legacy terrain names have been mapped to
/// the future explicit source-vertex stream. The stream binding is deliberately
/// private preparation: no current render route allocates or binds it.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainVertexSource {
    entry_path: String,
    source: String,
    remaining_dialect: GlslDialectReport,
}

/// Deterministic scalar/vector/matrix uniform layout shared by the two source
/// stages. Samplers and images remain distinct named resources for a later
/// lowering step.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceUniformContract {
    declarations: Vec<String>,
    fields: Vec<TerrainSourceUniformField>,
    std140_size: u32,
}

impl TerrainSourceUniformContract {
    pub fn declarations(&self) -> &[String] {
        &self.declarations
    }

    /// Deterministic std140 layout for the source-declared scalar/vector/
    /// matrix uniforms. The layout is semantic source preparation only; no
    /// Java or backend state participates in its construction.
    pub fn fields(&self) -> &[TerrainSourceUniformField] {
        &self.fields
    }

    pub fn std140_size(&self) -> u32 {
        self.std140_size
    }
}

/// Bounded GLSL value categories supported in the selected terrain source
/// scalar block. Opaque uniforms are represented separately by the semantic
/// resource binding plan and never appear here.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainSourceUniformType {
    Float,
    Int,
    Uint,
    Bool,
    Vec2,
    Vec3,
    Vec4,
    IVec2,
    IVec3,
    IVec4,
    UVec2,
    UVec3,
    UVec4,
    Mat2,
    Mat3,
    Mat4,
}

impl TerrainSourceUniformType {
    fn from_glsl(type_name: &str) -> GalResult<Self> {
        match type_name {
            "float" => Ok(Self::Float),
            "int" => Ok(Self::Int),
            "uint" => Ok(Self::Uint),
            "bool" => Ok(Self::Bool),
            "vec2" => Ok(Self::Vec2),
            "vec3" => Ok(Self::Vec3),
            "vec4" => Ok(Self::Vec4),
            "ivec2" => Ok(Self::IVec2),
            "ivec3" => Ok(Self::IVec3),
            "ivec4" => Ok(Self::IVec4),
            "uvec2" => Ok(Self::UVec2),
            "uvec3" => Ok(Self::UVec3),
            "uvec4" => Ok(Self::UVec4),
            "mat2" => Ok(Self::Mat2),
            "mat3" => Ok(Self::Mat3),
            "mat4" => Ok(Self::Mat4),
            _ => Err(GalError::unsupported_feature(format!(
                "terrain source scalar uniform type '{type_name}' has no std140 semantic layout"
            ))),
        }
    }

    fn std140_alignment(self) -> u32 {
        match self {
            Self::Float | Self::Int | Self::Uint | Self::Bool => 4,
            Self::Vec2 | Self::IVec2 | Self::UVec2 => 8,
            Self::Vec3
            | Self::Vec4
            | Self::IVec3
            | Self::IVec4
            | Self::UVec3
            | Self::UVec4
            | Self::Mat2
            | Self::Mat3
            | Self::Mat4 => 16,
        }
    }

    fn std140_size(self) -> u32 {
        match self {
            Self::Float | Self::Int | Self::Uint | Self::Bool => 4,
            Self::Vec2 | Self::IVec2 | Self::UVec2 => 8,
            Self::Vec3 | Self::Vec4 | Self::IVec3 | Self::IVec4 | Self::UVec3 | Self::UVec4 => 16,
            Self::Mat2 => 32,
            Self::Mat3 => 48,
            Self::Mat4 => 64,
        }
    }
}

/// One source scalar uniform after deterministic std140 layout. Array values
/// carry their required 16-byte rounded stride; scalar values use a zero
/// stride to avoid pretending they are arrays.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceUniformField {
    name: String,
    ty: TerrainSourceUniformType,
    array_length: u32,
    offset: u32,
    size: u32,
    array_stride: u32,
}

impl TerrainSourceUniformField {
    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn ty(&self) -> TerrainSourceUniformType {
        self.ty
    }

    pub fn array_length(&self) -> u32 {
        self.array_length
    }

    pub fn offset(&self) -> u32 {
        self.offset
    }

    pub fn size(&self) -> u32 {
        self.size
    }

    pub fn array_stride(&self) -> u32 {
        self.array_stride
    }
}

/// One source-derived vertex-to-fragment field. Locations are assigned from a
/// stable semantic sort, never from Java/Iris attribute state.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceVaryingField {
    name: String,
    type_name: String,
    interpolation: String,
    location: u32,
}

impl TerrainSourceVaryingField {
    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn type_name(&self) -> &str {
        &self.type_name
    }

    pub fn interpolation(&self) -> &str {
        &self.interpolation
    }

    pub fn location(&self) -> u32 {
        self.location
    }
}

/// Deterministic interface between the selected terrain source stages. This
/// covers only simple scalar/vector fields today; arrays, matrices, and other
/// multi-location interfaces are rejected instead of guessing a layout.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceVaryingContract {
    fields: Vec<TerrainSourceVaryingField>,
}

/// Source-level opaque resource category. This is intentionally not a Vulkan
/// descriptor or OpenGL binding; a later runtime maps these semantic fields to
/// explicit GAL resources.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainSourceOpaqueResourceKind {
    /// GLSL sampler declarations combine the image view and sampler state.
    /// The eventual runtime maps this to one semantic GAL pair binding.
    CombinedTextureSampler,
    StorageImage,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOpaqueResource {
    name: String,
    type_name: String,
    qualifiers: String,
    kind: TerrainSourceOpaqueResourceKind,
    binding: u32,
    active: bool,
}

impl TerrainSourceOpaqueResource {
    pub fn name(&self) -> &str {
        &self.name
    }
    pub fn type_name(&self) -> &str {
        &self.type_name
    }
    pub fn qualifiers(&self) -> &str {
        &self.qualifiers
    }
    pub fn kind(&self) -> TerrainSourceOpaqueResourceKind {
        self.kind
    }

    pub fn binding(&self) -> u32 {
        self.binding
    }

    /// Whether the already-expanded vertex or fragment stage actually
    /// references this declaration. Declarations remain in the lowered
    /// source with deterministic bindings, but only active resources belong
    /// to the executable semantic resource contract.
    pub fn active(&self) -> bool {
        self.active
    }
}

/// One source resource paired with a pack-declared portable role. The binding
/// remains a lowering-local ordering value; backends will later receive only
/// a resource layout assembled from these semantic roles.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOpaqueResourceBinding {
    resource_name: String,
    role: TerrainSourceResourceRole,
    kind: TerrainSourceOpaqueResourceKind,
    qualifiers: String,
    binding: u32,
}

impl TerrainSourceOpaqueResourceBinding {
    pub fn resource_name(&self) -> &str {
        &self.resource_name
    }

    pub fn role(&self) -> TerrainSourceResourceRole {
        self.role.clone()
    }

    pub fn kind(&self) -> TerrainSourceOpaqueResourceKind {
        self.kind
    }

    pub fn qualifiers(&self) -> &str {
        &self.qualifiers
    }

    pub fn binding(&self) -> u32 {
        self.binding
    }
}

/// Complete portable binding plan for all opaque resources in a lowered
/// terrain pair. It deliberately cannot be constructed from source names
/// alone: every resource must have a pack-declared semantic role.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOpaqueResourceBindingPlan {
    bindings: Vec<TerrainSourceOpaqueResourceBinding>,
}

impl TerrainSourceOpaqueResourceBindingPlan {
    pub fn bindings(&self) -> &[TerrainSourceOpaqueResourceBinding] {
        &self.bindings
    }

    /// Resolves only an active, lowered source resource name. Raw pack
    /// declarations may include inactive samplers and must not allocate a
    /// runtime resource by themselves.
    pub fn role_for(&self, resource_name: &str) -> Option<TerrainSourceResourceRole> {
        self.bindings
            .iter()
            .find(|binding| binding.resource_name == resource_name)
            .map(TerrainSourceOpaqueResourceBinding::role)
    }
}

/// Paired source resource table. Bindings are deterministic source-lowering
/// identities only, not native handles or runtime admission evidence.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOpaqueResourceContract {
    resources: Vec<TerrainSourceOpaqueResource>,
}

impl TerrainSourceOpaqueResourceContract {
    pub fn resources(&self) -> &[TerrainSourceOpaqueResource] {
        &self.resources
    }

    fn resource_for(&self, name: &str) -> Option<&TerrainSourceOpaqueResource> {
        self.resources.iter().find(|resource| resource.name == name)
    }

    pub fn active_resources(&self) -> impl Iterator<Item = &TerrainSourceOpaqueResource> {
        self.resources.iter().filter(|resource| resource.active)
    }

    /// Converts pack-declared source names into a closed semantic contract.
    /// Storage images remain distinct from sampled resources so later source
    /// program preparation can require an owned texture view rather than
    /// silently treating a writable image as a sampler.
    pub fn bind_semantic_roles(
        &self,
        declarations: &TerrainSourceResourceBindings,
    ) -> GalResult<TerrainSourceOpaqueResourceBindingPlan> {
        let mut bindings = Vec::with_capacity(self.resources.len());
        let mut missing_roles = Vec::new();
        for resource in self.active_resources() {
            let Some(role) = declarations.role_for(&resource.name) else {
                missing_roles.push(resource.name.as_str());
                continue;
            };
            let expected_type = match resource.kind {
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler => {
                    role.expected_sampler_type()
                }
                TerrainSourceOpaqueResourceKind::StorageImage => role
                    .expected_storage_image_type()
                    .ok_or_else(|| {
                        GalError::unsupported_feature(format!(
                            "terrain source role '{}' cannot provide storage-image resource '{}'",
                            role.semantic_name(),
                            resource.name
                        ))
                    })?,
            };
            if resource.type_name != expected_type {
                return Err(GalError::invalid_argument(format!(
                    "terrain material source resource '{}' declares '{}' but role {:?} requires '{}'",
                    resource.name,
                    resource.type_name,
                    role,
                    expected_type
                )));
            }
            bindings.push(TerrainSourceOpaqueResourceBinding {
                resource_name: resource.name.clone(),
                role: role.clone(),
                kind: resource.kind,
                qualifiers: resource.qualifiers.clone(),
                binding: resource.binding,
            });
        }
        if !missing_roles.is_empty() {
            const MAX_MISSING_ROLE_DIAGNOSTICS: usize = 12;
            let omitted = missing_roles
                .len()
                .saturating_sub(MAX_MISSING_ROLE_DIAGNOSTICS);
            let listed = missing_roles
                .iter()
                .take(MAX_MISSING_ROLE_DIAGNOSTICS)
                .copied()
                .collect::<Vec<_>>()
                .join(", ");
            let suffix = (omitted != 0).then(|| format!(" (+{omitted} more)"));
            return Err(GalError::unsupported_feature(format!(
                "terrain material source resources have no declared semantic roles: {listed}{}",
                suffix.unwrap_or_default()
            )));
        }
        // The semantic declaration file is pack-wide: its entries may belong
        // to a paired shadow, composite, or disabled preprocessing branch.
        // This source pair owns only the roles it actively consumes. An
        // unrelated declaration therefore cannot allocate or bind a resource
        // through this plan, while an active declaration still requires an
        // exact semantic role above.
        Ok(TerrainSourceOpaqueResourceBindingPlan { bindings })
    }
}

impl TerrainSourceVaryingContract {
    pub fn fields(&self) -> &[TerrainSourceVaryingField] {
        &self.fields
    }

    fn location_for(&self, name: &str) -> Option<u32> {
        self.fields
            .iter()
            .find(|field| field.name == name)
            .map(|field| field.location)
    }
}

/// Coherently lowered source pair. It is private source preparation only and
/// does not create resources or admit a selected-source render route.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredTerrainSourcePair {
    vertex: LoweredTerrainVertexSource,
    fragment: LoweredTerrainFragmentSource,
    uniform_contract: TerrainSourceUniformContract,
    varying_contract: TerrainSourceVaryingContract,
    opaque_resource_contract: TerrainSourceOpaqueResourceContract,
}

/// Coherently lowered source pair for a Rust-owned shadow-material pass.
///
/// This remains source preparation only. In particular, it neither allocates
/// a shadow-color attachment nor makes a selected shader-pack route
/// executable. Keeping it distinct from [`LoweredTerrainSourcePair`] prevents
/// a source shadow output from being mislabeled as a terrain G-buffer output.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LoweredShadowSourcePair {
    vertex: LoweredTerrainVertexSource,
    fragment: LoweredShadowFragmentSource,
    uniform_contract: TerrainSourceUniformContract,
    varying_contract: TerrainSourceVaryingContract,
    opaque_resource_contract: TerrainSourceOpaqueResourceContract,
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

impl LoweredTerrainVertexSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredTerrainFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[TerrainFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

impl LoweredShadowFragmentSource {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn outputs(&self) -> &[ShadowFragmentOutput] {
        &self.outputs
    }

    pub fn remaining_dialect(&self) -> &GlslDialectReport {
        &self.remaining_dialect
    }
}

/// Lowers only the two legacy fragment constructs whose mapping is fully
/// source-derived today: `texture2D` and `gl_FragData[n]`. This does not
/// attempt to guess vertex interfaces, fixed transforms, varying locations,
/// samplers, or backend descriptor bindings.
pub fn lower_terrain_fragment_surface(
    source: &PreprocessedShaderSource,
) -> GalResult<LoweredTerrainFragmentSource> {
    let uniform_contract = derive_terrain_source_uniform_contract(source, source)?;
    let varying_contract = derive_terrain_source_varying_contract(source, source)?;
    let opaque_resource_contract = derive_terrain_source_opaque_resource_contract(source, source)?;
    lower_terrain_fragment_surface_with_contracts(
        source,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
    )
}

/// Lowers a legacy shadow fragment into named shadow-pass outputs. This is
/// deliberately fragment-only preparation: execution still requires a
/// separately lowered shadow vertex stage and a complete Rust-owned shadow
/// attachment/resource contract.
pub fn lower_shadow_fragment_surface(
    source: &PreprocessedShaderSource,
) -> GalResult<LoweredShadowFragmentSource> {
    let uniform_contract = derive_terrain_source_uniform_contract(source, source)?;
    let opaque_resource_contract = derive_terrain_source_opaque_resource_contract(source, source)?;
    lower_shadow_fragment_surface_with_contracts(
        source,
        &uniform_contract,
        None,
        &opaque_resource_contract,
    )
}

fn lower_shadow_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: Option<&TerrainSourceVaryingContract>,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredShadowFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    // A shadow fragment may declare inputs that only its paired vertex stage
    // produces. Fragment-only preparation deliberately leaves those locations
    // untouched; complete pair lowering validates and assigns them.
    if let Some(varying_contract) = varying_contract {
        lowered = replace_identifier(&lowered, "varying", "in");
        lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    }
    lowered = apply_opaque_resource_bindings(&lowered, &opaque_resource_contract)?;
    let mut outputs = Vec::new();
    for output in [
        ShadowFragmentOutput::ShadowColor,
        ShadowFragmentOutput::LightShaftColor,
    ] {
        let (rewritten, occurrences) =
            replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
        lowered = rewritten;
        if occurrences > 0 {
            outputs.push(output);
        }
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "shadow fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if !outputs.contains(&ShadowFragmentOutput::ShadowColor) {
        return Err(GalError::invalid_argument(format!(
            "shadow fragment '{}' has no shadow color output to lower",
            source.entry_path()
        )));
    }
    let declarations = outputs
        .iter()
        .map(|output| {
            format!(
                "layout(location = {}) out vec4 {};\n",
                output.legacy_index(),
                output.semantic_name()
            )
        })
        .collect::<String>();
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredShadowFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

/// Lowers both stages with one exact scalar uniform layout. This is still a
/// preparation artifact, but it prevents a future program from silently using
/// mismatched stage-local UBO layouts.
pub fn lower_terrain_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredTerrainSourcePair> {
    let uniform_contract = derive_terrain_source_uniform_contract(vertex, fragment)?;
    let varying_contract = derive_terrain_source_varying_contract(vertex, fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(vertex, fragment)?;
    Ok(LoweredTerrainSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Terrain,
        )?,
        fragment: lower_terrain_fragment_surface_with_contracts(
            fragment,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers the exact scoped shadow-source pair into explicit source semantics.
/// Shadow transforms deliberately remain distinct from G-buffer transforms,
/// and shadow outputs remain distinct from normal terrain outputs. This is
/// not an executable pass: a future Rust-owned shadow-color attachment and
/// full named resource plan are still required before source execution can be
/// admitted.
pub fn lower_shadow_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredShadowSourcePair> {
    let uniform_contract = derive_source_uniform_contract(
        vertex,
        fragment,
        SourceTransformSemantics::Shadow,
    )?;
    let varying_contract = derive_terrain_source_varying_contract(vertex, fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(vertex, fragment)?;
    Ok(LoweredShadowSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Shadow,
        )?,
        fragment: lower_shadow_fragment_surface_with_contracts(
            fragment,
            &uniform_contract,
            Some(&varying_contract),
            &opaque_resource_contract,
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

fn lower_terrain_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredTerrainFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let mut outputs = Vec::new();
    for output in [
        TerrainFragmentOutput::LitColor,
        TerrainFragmentOutput::MaterialAuxiliary,
        TerrainFragmentOutput::ViewSpaceNormal,
    ] {
        let (rewritten, occurrences) =
            replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
        lowered = rewritten;
        if occurrences > 0 {
            outputs.push(output);
        }
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "terrain fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if outputs.is_empty() {
        return Err(GalError::invalid_argument(format!(
            "terrain fragment '{}' has no named terrain output to lower",
            source.entry_path()
        )));
    }
    let declarations = outputs
        .iter()
        .map(|output| {
            format!(
                "layout(location = {}) out vec4 {};\n",
                output.legacy_index(),
                output.semantic_name()
            )
        })
        .collect::<String>();
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredTerrainFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

const FRAGMENT_SEMANTIC_PREAMBLE: &str = r#"#define vulkanic_source_shadow2D(source_texture, source_coordinates) vec4(texture(source_texture, source_coordinates))
"#;

/// Lowers the audited terrain vertex compatibility names into an explicit
/// indexed source stream. This is not a generic GLSL compatibility shim: a
/// source declaring any unrecognized legacy attribute is rejected rather than
/// assigned an invented location or default value.
pub fn lower_terrain_vertex_surface(
    source: &PreprocessedShaderSource,
) -> GalResult<LoweredTerrainVertexSource> {
    let uniform_contract = derive_terrain_source_uniform_contract(source, source)?;
    let varying_contract = derive_terrain_source_varying_contract(source, source)?;
    let opaque_resource_contract = derive_terrain_source_opaque_resource_contract(source, source)?;
    lower_source_vertex_surface_with_contracts(
        source,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
        SourceTransformSemantics::Terrain,
    )
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum SourceTransformSemantics {
    Terrain,
    Shadow,
}

impl SourceTransformSemantics {
    fn model_view_uniform(self) -> &'static str {
        match self {
            Self::Terrain => "gbufferModelView",
            Self::Shadow => "shadowModelView",
        }
    }

    fn projection_uniform(self) -> &'static str {
        match self {
            Self::Terrain => "gbufferProjection",
            Self::Shadow => "shadowProjection",
        }
    }
}

fn lower_source_vertex_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
    transforms: SourceTransformSemantics,
) -> GalResult<LoweredTerrainVertexSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = remove_known_legacy_attributes(&lowered)?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    lowered = replace_identifier(&lowered, "varying", "out");
    for (legacy, explicit) in [
        ("gl_TextureMatrix", "vulkanic_source_texture_matrix"),
        ("gl_MultiTexCoord0", "vulkanic_source_atlas_uv"),
        ("gl_MultiTexCoord1", "vulkanic_source_lightmap_uv"),
        ("gl_Color", "vulkanic_source_vertex_color"),
        ("gl_NormalMatrix", "vulkanic_source_normal_matrix"),
        ("gl_Normal", "vulkanic_source_normal"),
        ("gl_ModelViewMatrix", "vulkanic_source_model_view"),
        ("gl_ProjectionMatrix", "gbufferProjection"),
        ("gl_Vertex", "vulkanic_source_position"),
        ("mc_Entity", "vulkanic_source_entity"),
        ("mc_midTexCoord", "vulkanic_source_mid_tex_coord"),
        ("at_tangent", "vulkanic_source_tangent"),
        ("at_midBlock", "vulkanic_source_mid_block"),
        ("ftransform", "vulkanic_source_ftransform"),
        ("texture2D", "texture"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = apply_varying_locations(&lowered, VaryingStorage::Out, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, &vertex_semantic_preamble(transforms))?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredTerrainVertexSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        remaining_dialect,
    })
}

fn vertex_semantic_preamble(transforms: SourceTransformSemantics) -> String {
    VERTEX_SEMANTIC_PREAMBLE_TEMPLATE
        .replace("{model_view}", transforms.model_view_uniform())
        .replace("{projection}", transforms.projection_uniform())
}

const VERTEX_SEMANTIC_PREAMBLE_TEMPLATE: &str = r#"struct VulkanicSourceTerrainVertex {
    vec4 position;
    vec4 color;
    vec4 normal_light;
    vec4 atlas_uv_lightmap;
    vec4 entity;
    vec4 mid_tex_coord;
    vec4 tangent;
    vec4 mid_block;
};
layout(set = 0, binding = 0, std430) readonly buffer VulkanicSourceTerrainVertices {
    VulkanicSourceTerrainVertex vulkanic_source_vertices[];
};
layout(set = 0, binding = 1, std140) uniform VulkanicSourceTerrainLegacyTransforms {
    mat4 vulkanic_source_texture_matrix[2];
};
struct VulkanicSourceTerrainInstance {
    mat4 model_transform;
};
layout(set = 0, binding = 3, std430) readonly buffer VulkanicSourceTerrainInstances {
    VulkanicSourceTerrainInstance vulkanic_source_instances[];
};
#define vulkanic_source_vertex vulkanic_source_vertices[gl_VertexIndex]
#define vulkanic_source_instance vulkanic_source_instances[gl_InstanceIndex]
#define vulkanic_source_model_transform vulkanic_source_instance.model_transform
#define vulkanic_source_model_view ({model_view} * vulkanic_source_model_transform)
#define vulkanic_source_normal_matrix transpose(inverse(mat3(vulkanic_source_model_view)))
#define vulkanic_source_position vulkanic_source_vertex.position
#define vulkanic_source_vertex_color vulkanic_source_vertex.color
#define vulkanic_source_normal vulkanic_source_vertex.normal_light.xyz
#define vulkanic_source_atlas_uv vec4(vulkanic_source_vertex.atlas_uv_lightmap.xy, 0.0, 1.0)
#define vulkanic_source_lightmap_uv vec4(vulkanic_source_vertex.atlas_uv_lightmap.zw, 0.0, 1.0)
#define vulkanic_source_entity vulkanic_source_vertex.entity
#define vulkanic_source_mid_tex_coord vulkanic_source_vertex.mid_tex_coord
#define vulkanic_source_tangent vulkanic_source_vertex.tangent
#define vulkanic_source_mid_block vulkanic_source_vertex.mid_block.xyz
#define vulkanic_source_ftransform() ({projection} * vulkanic_source_model_view * vulkanic_source_position)
"#;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum VaryingStorage {
    In,
    Out,
}

impl VaryingStorage {
    fn keyword(self) -> &'static str {
        match self {
            Self::In => "in",
            Self::Out => "out",
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct SourceVaryingDeclaration {
    name: String,
    type_name: String,
    interpolation: String,
}

/// Derives the exact simple field interface used by a vertex/fragment pair.
/// Missing or incompatible fragment inputs fail source preparation rather than
/// relying on compiler auto-assignment or an implicit legacy link convention.
pub fn derive_terrain_source_varying_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<TerrainSourceVaryingContract> {
    let mut vertex_outputs = BTreeMap::new();
    for field in collect_stage_varyings(vertex.expanded_source(), VaryingStorage::Out)? {
        insert_stage_varying(&mut vertex_outputs, field, "vertex output")?;
    }
    let mut fragment_inputs = BTreeMap::new();
    for field in collect_stage_varyings(fragment.expanded_source(), VaryingStorage::In)? {
        insert_stage_varying(&mut fragment_inputs, field, "fragment input")?;
    }

    for (name, fragment_field) in &fragment_inputs {
        let Some(vertex_field) = vertex_outputs.get(name) else {
            return Err(GalError::invalid_argument(format!(
                "terrain fragment input '{name}' has no matching vertex output"
            )));
        };
        if vertex_field.type_name != fragment_field.type_name
            || vertex_field.interpolation != fragment_field.interpolation
        {
            return Err(GalError::invalid_argument(format!(
                "terrain varying '{name}' differs between vertex output ('{} {}') and fragment input ('{} {}')",
                vertex_field.interpolation,
                vertex_field.type_name,
                fragment_field.interpolation,
                fragment_field.type_name,
            )));
        }
    }

    Ok(TerrainSourceVaryingContract {
        fields: vertex_outputs
            .into_values()
            .enumerate()
            .map(|(location, field)| TerrainSourceVaryingField {
                name: field.name,
                type_name: field.type_name,
                interpolation: field.interpolation,
                location: location as u32,
            })
            .collect(),
    })
}

fn insert_stage_varying(
    fields: &mut BTreeMap<String, SourceVaryingDeclaration>,
    field: SourceVaryingDeclaration,
    stage: &str,
) -> GalResult<()> {
    match fields.get(&field.name) {
        Some(existing) if existing != &field => Err(GalError::invalid_argument(format!(
            "terrain {stage} '{}' has incompatible repeated declarations",
            field.name
        ))),
        Some(_) => Ok(()),
        None => {
            fields.insert(field.name.clone(), field);
            Ok(())
        }
    }
}

fn collect_stage_varyings(
    source: &str,
    storage: VaryingStorage,
) -> GalResult<Vec<SourceVaryingDeclaration>> {
    let mut fields = Vec::new();
    for line in source.lines() {
        let Some(declarations) = parse_varying_declaration(line.trim(), storage)? else {
            continue;
        };
        fields.extend(declarations);
    }
    Ok(fields)
}

fn parse_varying_declaration(
    line: &str,
    storage: VaryingStorage,
) -> GalResult<Option<Vec<SourceVaryingDeclaration>>> {
    if !line.ends_with(';') || line.contains('(') || line.starts_with("layout") {
        return Ok(None);
    }
    let words = line
        .trim_end_matches(';')
        .split_whitespace()
        .collect::<Vec<_>>();
    let Some(storage_index) = words.iter().position(|word| *word == storage.keyword()) else {
        return Ok(None);
    };
    if storage_index > 1 || words.len() < storage_index + 3 {
        return Ok(None);
    }
    let interpolation = words[..storage_index].join(" ");
    if !interpolation.is_empty()
        && !matches!(interpolation.as_str(), "flat" | "smooth" | "noperspective")
    {
        return Err(GalError::unsupported_feature(format!(
            "terrain {} varying uses unsupported interpolation qualifier '{interpolation}'",
            storage.keyword()
        )));
    }
    let type_name = words[storage_index + 1];
    let names = words[storage_index + 2..].join(" ");
    if names.contains('[') || names.contains(']') {
        return Err(GalError::unsupported_feature(
            "terrain varying arrays need an explicit multi-location contract",
        ));
    }
    let mut declarations = Vec::new();
    for name in names.split(',').map(str::trim) {
        if name.is_empty() || !valid_identifier(name) {
            return Err(GalError::invalid_argument(format!(
                "terrain {} varying has invalid field name '{name}'",
                storage.keyword()
            )));
        }
        declarations.push(SourceVaryingDeclaration {
            name: name.to_string(),
            type_name: type_name.to_string(),
            interpolation: interpolation.clone(),
        });
    }
    Ok(Some(declarations))
}

fn apply_varying_locations(
    source: &str,
    storage: VaryingStorage,
    contract: &TerrainSourceVaryingContract,
) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    for line in source.lines() {
        let Some(fields) = parse_varying_declaration(line.trim(), storage)? else {
            output.push_str(line);
            output.push('\n');
            continue;
        };
        if contract.location_for(&fields[0].name).is_none() {
            output.push_str(line);
            output.push('\n');
            continue;
        }
        for field in fields {
            let Some(location) = contract.location_for(&field.name) else {
                return Err(GalError::invalid_argument(format!(
                    "terrain {} varying '{}' is absent from the paired contract",
                    storage.keyword(),
                    field.name
                )));
            };
            if field.interpolation.is_empty() {
                output.push_str(&format!(
                    "layout(location = {location}) {} {} {};\n",
                    storage.keyword(),
                    field.type_name,
                    field.name
                ));
            } else {
                output.push_str(&format!(
                    "layout(location = {location}) {} {} {} {};\n",
                    field.interpolation,
                    storage.keyword(),
                    field.type_name,
                    field.name
                ));
            }
        }
    }
    Ok(output)
}

fn valid_identifier(name: &str) -> bool {
    name.bytes()
        .next()
        .is_some_and(|byte| byte == b'_' || byte.is_ascii_alphabetic())
        && name.bytes().all(is_identifier_byte)
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct SourceOpaqueResourceDeclaration {
    name: String,
    type_name: String,
    qualifiers: String,
    kind: TerrainSourceOpaqueResourceKind,
    active: bool,
}

/// Derives one deterministic table for source-declared samplers/images across
/// both stages. Explicitly unsupported declaration forms fail rather than
/// falling back to compiler-assigned bindings.
pub fn derive_terrain_source_opaque_resource_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<TerrainSourceOpaqueResourceContract> {
    let mut resources = BTreeMap::<String, SourceOpaqueResourceDeclaration>::new();
    for source in [vertex, fragment] {
        for resource in collect_opaque_resources(source.expanded_source())? {
            if let Some(existing) = resources.get(&resource.name) {
                if existing.type_name != resource.type_name
                    || existing.qualifiers != resource.qualifiers
                    || existing.kind != resource.kind
                {
                    return Err(GalError::invalid_argument(format!(
                        "terrain opaque resource '{}' has incompatible paired declarations",
                        resource.name
                    )));
                }
                let active = existing.active || resource.active;
                resources
                    .get_mut(&resource.name)
                    .expect("opaque resource entry disappeared during paired lowering")
                    .active = active;
            } else {
                resources.insert(resource.name.clone(), resource);
            }
        }
    }
    Ok(TerrainSourceOpaqueResourceContract {
        resources: resources
            .into_values()
            .enumerate()
            .map(|(binding, resource)| TerrainSourceOpaqueResource {
                name: resource.name,
                type_name: resource.type_name,
                qualifiers: resource.qualifiers,
                kind: resource.kind,
                binding: binding as u32,
                active: resource.active,
            })
            .collect(),
    })
}

fn collect_opaque_resources(source: &str) -> GalResult<Vec<SourceOpaqueResourceDeclaration>> {
    let source_without_declarations = strip_opaque_resource_declarations(source)?;
    let referenced = glsl_identifiers(&source_without_declarations);
    let mut resources = Vec::new();
    for line in source.lines() {
        let Some(mut resource) = parse_opaque_resource_declaration(line.trim())? else {
            continue;
        };
        resource.active = referenced.contains(&resource.name);
        resources.push(resource);
    }
    Ok(resources)
}

/// Removes opaque declarations before the bounded reference scan so a global
/// shader header cannot make a sampler appear required merely by declaring
/// it. Active preprocessor definitions remain in the source and therefore
/// count as uses conservatively, exactly as scalar-uniform collection does.
fn strip_opaque_resource_declarations(source: &str) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    for line in source.lines() {
        if parse_opaque_resource_declaration(line.trim())?.is_some() {
            continue;
        }
        output.push_str(line);
        output.push('\n');
    }
    Ok(output)
}

fn parse_opaque_resource_declaration(
    line: &str,
) -> GalResult<Option<SourceOpaqueResourceDeclaration>> {
    if !line.ends_with(';') || line.contains('(') {
        return Ok(None);
    }
    if line.starts_with("layout") {
        if line.contains("sampler") || line.contains("image") {
            return Err(GalError::unsupported_feature(
                "terrain opaque resources with pre-existing layouts need an explicit source layout contract",
            ));
        }
        return Ok(None);
    }
    let words = line
        .trim_end_matches(';')
        .split_whitespace()
        .collect::<Vec<_>>();
    let Some(uniform_index) = words.iter().position(|word| *word == "uniform") else {
        return Ok(None);
    };
    if !words[..uniform_index].iter().all(|qualifier| {
        matches!(
            *qualifier,
            "readonly" | "writeonly" | "coherent" | "volatile" | "restrict"
        )
    }) {
        return Ok(None);
    }
    if words.len() != uniform_index + 3 || line.contains(',') || line.contains('[') {
        return Ok(None);
    }
    let type_name = words[uniform_index + 1];
    let kind = if type_name.contains("sampler") {
        TerrainSourceOpaqueResourceKind::CombinedTextureSampler
    } else if type_name.contains("image") {
        TerrainSourceOpaqueResourceKind::StorageImage
    } else {
        return Ok(None);
    };
    let name = words[uniform_index + 2];
    if !valid_identifier(name) {
        return Err(GalError::invalid_argument(format!(
            "terrain opaque resource has invalid name '{name}'"
        )));
    }
    let qualifiers = words[..uniform_index].join(" ");
    if !qualifiers.is_empty()
        && !qualifiers.split_whitespace().all(|qualifier| {
            matches!(
                qualifier,
                "readonly" | "writeonly" | "coherent" | "volatile" | "restrict"
            )
        })
    {
        return Err(GalError::unsupported_feature(format!(
            "terrain opaque resource '{name}' has unsupported qualifiers '{qualifiers}'"
        )));
    }
    Ok(Some(SourceOpaqueResourceDeclaration {
        name: name.to_string(),
        type_name: type_name.to_string(),
        qualifiers,
        kind,
        active: false,
    }))
}

fn apply_opaque_resource_bindings(
    source: &str,
    contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    for line in source.lines() {
        let Some(declaration) = parse_opaque_resource_declaration(line.trim())? else {
            output.push_str(line);
            output.push('\n');
            continue;
        };
        let Some(resource) = contract.resource_for(&declaration.name) else {
            return Err(GalError::invalid_argument(format!(
                "terrain opaque resource '{}' is absent from the paired contract",
                declaration.name
            )));
        };
        if declaration.qualifiers.is_empty() {
            output.push_str(&format!(
                "layout(set = 1, binding = {}) uniform {} {};\n",
                resource.binding, resource.type_name, resource.name
            ));
        } else {
            output.push_str(&format!(
                "layout(set = 1, binding = {}) {} uniform {} {};\n",
                resource.binding, resource.qualifiers, resource.type_name, resource.name
            ));
        }
    }
    Ok(output)
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct SourceUniformDeclaration {
    name: String,
    declaration: String,
}

/// Derives one ordered layout from both stages. Equal names must have exactly
/// equal declarations: accepting a type or array mismatch would make the
/// eventual UBO ABI ambiguous.
pub fn derive_terrain_source_uniform_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<TerrainSourceUniformContract> {
    derive_source_uniform_contract(vertex, fragment, SourceTransformSemantics::Terrain)
}

fn derive_source_uniform_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    transforms: SourceTransformSemantics,
) -> GalResult<TerrainSourceUniformContract> {
    let mut declarations = BTreeMap::new();
    for source in [vertex, fragment] {
        for uniform in collect_nonopaque_uniforms(source.expanded_source(), transforms)? {
            match declarations.get(&uniform.name) {
                Some(existing) if existing != &uniform.declaration => {
                    return Err(GalError::invalid_argument(format!(
                        "terrain source uniform '{}' has incompatible paired declarations: '{}' versus '{}'",
                        uniform.name, existing, uniform.declaration
                    )));
                }
                Some(_) => {}
                None => {
                    declarations.insert(uniform.name, uniform.declaration);
                }
            }
        }
        for (name, declaration) in
            required_legacy_transform_uniforms(source.expanded_source(), transforms)
        {
            match declarations.get(name) {
                Some(existing) if existing != declaration => {
                    return Err(GalError::invalid_argument(format!(
                        "terrain source legacy transform '{}' must be declared as '{}' rather than '{}'",
                        name, declaration, existing
                    )));
                }
                Some(_) => {}
                None => {
                    declarations.insert(name.to_string(), declaration.to_string());
                }
            }
        }
    }
    let declarations = declarations.into_values().collect::<Vec<_>>();
    let (fields, std140_size) = terrain_source_std140_layout(&declarations)?;
    Ok(TerrainSourceUniformContract {
        declarations,
        fields,
        std140_size,
    })
}

const MAX_TERRAIN_SOURCE_UNIFORM_FIELDS: usize = 512;
const MAX_TERRAIN_SOURCE_UNIFORM_BYTES: u32 = 64 * 1024;

fn terrain_source_std140_layout(
    declarations: &[String],
) -> GalResult<(Vec<TerrainSourceUniformField>, u32)> {
    if declarations.len() > MAX_TERRAIN_SOURCE_UNIFORM_FIELDS {
        return Err(GalError::invalid_argument(format!(
            "terrain source declares {} scalar uniforms, exceeding {}",
            declarations.len(),
            MAX_TERRAIN_SOURCE_UNIFORM_FIELDS
        )));
    }
    let mut fields = Vec::with_capacity(declarations.len());
    let mut offset = 0_u32;
    for declaration in declarations {
        let (type_name, name, array_length) =
            parse_terrain_source_uniform_declaration(declaration)?;
        let ty = TerrainSourceUniformType::from_glsl(type_name)?;
        let alignment = if array_length > 1 {
            16
        } else {
            ty.std140_alignment()
        };
        offset = align_up_std140(offset, alignment)?;
        let element_size = ty.std140_size();
        let array_stride = if array_length > 1 {
            align_up_std140(element_size, 16)?
        } else {
            0
        };
        let size = if array_length > 1 {
            array_stride.checked_mul(array_length).ok_or_else(|| {
                GalError::invalid_argument("terrain source uniform array size overflows u32")
            })?
        } else {
            element_size
        };
        let end = offset.checked_add(size).ok_or_else(|| {
            GalError::invalid_argument("terrain source std140 layout size overflows u32")
        })?;
        if end > MAX_TERRAIN_SOURCE_UNIFORM_BYTES {
            return Err(GalError::invalid_argument(format!(
                "terrain source std140 scalar block exceeds {} bytes",
                MAX_TERRAIN_SOURCE_UNIFORM_BYTES
            )));
        }
        fields.push(TerrainSourceUniformField {
            name: name.to_string(),
            ty,
            array_length,
            offset,
            size,
            array_stride,
        });
        offset = end;
    }
    Ok((fields, align_up_std140(offset, 16)?))
}

fn parse_terrain_source_uniform_declaration(declaration: &str) -> GalResult<(&str, &str, u32)> {
    let declaration = declaration.trim().trim_end_matches(';');
    let mut parts = declaration.split_whitespace();
    let type_name = parts
        .next()
        .ok_or_else(|| GalError::invalid_argument("terrain uniform type is missing"))?;
    let name = parts
        .next()
        .ok_or_else(|| GalError::invalid_argument("terrain uniform name is missing"))?;
    if parts.next().is_some() {
        return Err(GalError::invalid_argument(
            "terrain uniform declaration has unsupported qualifiers or tokens",
        ));
    }
    let (name, array_length) = match name.split_once('[') {
        Some((name, suffix)) => {
            let count = suffix.strip_suffix(']').ok_or_else(|| {
                GalError::invalid_argument("terrain uniform array declaration is malformed")
            })?;
            let count = count.parse::<u32>().map_err(|_| {
                GalError::invalid_argument("terrain uniform array length is not a u32")
            })?;
            if count == 0 {
                return Err(GalError::invalid_argument(
                    "terrain uniform array length must be non-zero",
                ));
            }
            (name, count)
        }
        None => (name, 1),
    };
    if !valid_identifier(name) {
        return Err(GalError::invalid_argument(format!(
            "terrain uniform has invalid name '{name}'"
        )));
    }
    Ok((type_name, name, array_length))
}

fn align_up_std140(value: u32, alignment: u32) -> GalResult<u32> {
    debug_assert!(alignment.is_power_of_two());
    value
        .checked_add(alignment - 1)
        .map(|value| value & !(alignment - 1))
        .ok_or_else(|| GalError::invalid_argument("terrain source std140 alignment overflows u32"))
}

/// Removes scalar/vector/matrix uniforms after their paired declaration has
/// been captured. Samplers/images remain source-declared named resources for
/// a later binding contract.
fn strip_nonopaque_uniforms(source: &str) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    for line in source.lines() {
        let trimmed = line.trim();
        let Some(declaration) = trimmed.strip_prefix("uniform ") else {
            output.push_str(line);
            output.push('\n');
            continue;
        };
        let type_name = declaration
            .split_whitespace()
            .next()
            .ok_or_else(|| GalError::invalid_argument("malformed terrain uniform declaration"))?;
        if is_opaque_uniform_type(type_name) {
            output.push_str(line);
            output.push('\n');
            continue;
        }
        validate_nonopaque_uniform_declaration(declaration)?;
    }
    Ok(output)
}

fn collect_nonopaque_uniforms(
    source: &str,
    transforms: SourceTransformSemantics,
) -> GalResult<Vec<SourceUniformDeclaration>> {
    // Expanded packs commonly include a broad global uniform header. Only a
    // source-stage reference belongs in this program's explicit ABI; merely
    // declaring a value in an inactive terrain path must not create a fake
    // semantic input requirement. Strip scalar declarations before scanning
    // so a declaration cannot count as its own use.
    let source_without_scalar_uniforms = strip_nonopaque_uniforms(source)?;
    let mut referenced = glsl_identifiers(&source_without_scalar_uniforms);
    // Vertex lowering replaces these legacy built-ins with explicit source
    // uniforms after the contract has been derived. Preserve their declared
    // semantic matrices when the original source requires the replacement.
    for (name, _) in required_legacy_transform_uniforms(source, transforms) {
        referenced.insert(name.to_string());
    }
    let mut uniforms = Vec::new();
    for line in source.lines() {
        let trimmed = line.trim();
        let Some(declaration) = trimmed.strip_prefix("uniform ") else {
            continue;
        };
        let type_name = declaration
            .split_whitespace()
            .next()
            .ok_or_else(|| GalError::invalid_argument("malformed terrain uniform declaration"))?;
        if is_opaque_uniform_type(type_name) {
            continue;
        }
        let declaration = validate_nonopaque_uniform_declaration(declaration)?;
        let name = uniform_name(&declaration)?;
        if referenced.contains(&name) {
            uniforms.push(SourceUniformDeclaration { name, declaration });
        }
    }
    Ok(uniforms)
}

/// Legacy matrix built-ins are transformed into these named source semantics.
/// They are added to the uniform ABI even when the original GLSL relied on
/// built-ins and never declared them explicitly.
fn required_legacy_transform_uniforms(
    source: &str,
    transforms: SourceTransformSemantics,
) -> Vec<(&'static str, &'static str)> {
    let referenced = glsl_identifiers(source);
    let mut requirements = Vec::with_capacity(2);
    if referenced.contains("gl_ModelViewMatrix")
        || referenced.contains("gl_NormalMatrix")
        || referenced.contains("ftransform")
    {
        requirements.push((
            transforms.model_view_uniform(),
            match transforms {
                SourceTransformSemantics::Terrain => "mat4 gbufferModelView;",
                SourceTransformSemantics::Shadow => "mat4 shadowModelView;",
            },
        ));
    }
    if referenced.contains("gl_ProjectionMatrix") || referenced.contains("ftransform") {
        requirements.push((
            transforms.projection_uniform(),
            match transforms {
                SourceTransformSemantics::Terrain => "mat4 gbufferProjection;",
                SourceTransformSemantics::Shadow => "mat4 shadowProjection;",
            },
        ));
    }
    requirements
}

/// Returns identifier tokens outside GLSL line/block comments. GLSL has no
/// string literals, so this bounded lexical scan is sufficient for deciding
/// whether a scalar declaration is referenced by the already-expanded source.
/// It deliberately treats identifiers in active preprocessor definitions as
/// references, which is conservative and avoids dropping macro-fed inputs.
fn glsl_identifiers(source: &str) -> BTreeSet<String> {
    let bytes = source.as_bytes();
    let mut identifiers = BTreeSet::new();
    let mut index = 0_usize;
    while index < bytes.len() {
        if bytes[index] == b'/' && index + 1 < bytes.len() {
            match bytes[index + 1] {
                b'/' => {
                    index += 2;
                    while index < bytes.len() && bytes[index] != b'\n' {
                        index += 1;
                    }
                    continue;
                }
                b'*' => {
                    index += 2;
                    while index + 1 < bytes.len()
                        && !(bytes[index] == b'*' && bytes[index + 1] == b'/')
                    {
                        index += 1;
                    }
                    index = (index + 2).min(bytes.len());
                    continue;
                }
                _ => {}
            }
        }
        if bytes[index] == b'_' || bytes[index].is_ascii_alphabetic() {
            let start = index;
            index += 1;
            while index < bytes.len()
                && (bytes[index] == b'_' || bytes[index].is_ascii_alphanumeric())
            {
                index += 1;
            }
            identifiers.insert(source[start..index].to_string());
            continue;
        }
        index += 1;
    }
    identifiers
}

fn validate_nonopaque_uniform_declaration(declaration: &str) -> GalResult<String> {
    let declaration = declaration.trim();
    if !declaration.ends_with(';') || declaration.contains('{') || declaration.contains(',') {
        return Err(GalError::unsupported_feature(
            "terrain scalar uniform declarations must be one named value ending in a semicolon",
        ));
    }
    Ok(declaration.to_string())
}

fn uniform_name(declaration: &str) -> GalResult<String> {
    let name = declaration
        .trim_end_matches(';')
        .split_whitespace()
        .last()
        .ok_or_else(|| GalError::invalid_argument("malformed terrain uniform declaration"))?
        .split('[')
        .next()
        .unwrap_or_default();
    if !valid_identifier(name) {
        return Err(GalError::invalid_argument(format!(
            "terrain uniform has invalid name '{name}'"
        )));
    }
    Ok(name.to_string())
}

fn is_opaque_uniform_type(type_name: &str) -> bool {
    type_name.contains("sampler")
        || type_name.contains("image")
        || type_name.starts_with("atomic_uint")
}

fn uniform_block(contract: &TerrainSourceUniformContract) -> String {
    if contract.declarations.is_empty() {
        return String::new();
    }
    let mut block = String::from(
        "layout(set = 0, binding = 2, std140) uniform VulkanicSourceTerrainUniforms {\n",
    );
    for declaration in &contract.declarations {
        block.push_str("    ");
        block.push_str(declaration);
        block.push('\n');
    }
    block.push_str("};\n");
    block
}

fn remove_known_legacy_attributes(source: &str) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    for line in source.lines() {
        let trimmed = line.trim();
        if let Some(declaration) = trimmed.strip_prefix("attribute ") {
            let name = declaration
                .trim_end_matches(';')
                .split_whitespace()
                .last()
                .ok_or_else(|| GalError::invalid_argument("malformed legacy terrain attribute"))?;
            let name = name.trim_end_matches(|character| character == ';' || character == ']');
            if !matches!(
                name,
                "mc_Entity" | "mc_midTexCoord" | "at_tangent" | "at_midBlock"
            ) {
                return Err(GalError::unsupported_feature(format!(
                    "terrain vertex declares unsupported legacy attribute '{name}'"
                )));
            }
            continue;
        }
        output.push_str(line);
        output.push('\n');
    }
    Ok(output)
}

fn upgrade_version(source: &str) -> GalResult<String> {
    let lines = source.lines().collect::<Vec<_>>();
    if lines.is_empty() {
        return Err(GalError::invalid_argument("shader source is empty"));
    }
    // The owned preprocessor may inject semantic `#define`s ahead of the
    // source's root version. GLSL requires version first, so move exactly one
    // root directive to the prologue without dropping the configured defines.
    let Some(version_line) = lines
        .iter()
        .position(|line| line.trim_start().starts_with("#version"))
    else {
        return Err(GalError::invalid_argument(
            "shader source has no GLSL #version directive to lower",
        ));
    };
    let mut output = String::from("#version 450\n");
    for (index, line) in lines.into_iter().enumerate() {
        if index == version_line {
            continue;
        }
        output.push_str(line);
        output.push('\n');
    }
    Ok(output)
}

fn insert_after_version(source: &str, declarations: &str) -> GalResult<String> {
    let Some(newline) = source.find('\n') else {
        return Err(GalError::invalid_argument(
            "lowered shader version line has no body",
        ));
    };
    let mut output = String::with_capacity(source.len() + declarations.len());
    output.push_str(&source[..newline + 1]);
    output.push_str(declarations);
    output.push_str(&source[newline + 1..]);
    Ok(output)
}

fn replace_identifier(source: &str, from: &str, to: &str) -> String {
    let mut output = String::with_capacity(source.len());
    let bytes = source.as_bytes();
    let mut cursor = 0;
    while cursor < bytes.len() {
        let Some(relative) = source[cursor..].find(from) else {
            output.push_str(&source[cursor..]);
            break;
        };
        let start = cursor + relative;
        let end = start + from.len();
        let before = start == 0 || !is_identifier_byte(bytes[start - 1]);
        let after = end == bytes.len() || !is_identifier_byte(bytes[end]);
        output.push_str(&source[cursor..start]);
        if before && after {
            output.push_str(to);
        } else {
            output.push_str(from);
        }
        cursor = end;
    }
    output
}

fn replace_fragment_output(
    source: &str,
    index: u32,
    replacement: &str,
) -> GalResult<(String, u32)> {
    let needle = "gl_FragData";
    let bytes = source.as_bytes();
    let mut output = String::with_capacity(source.len());
    let mut cursor = 0;
    let mut occurrences = 0;
    while cursor < bytes.len() {
        let Some(relative) = source[cursor..].find(needle) else {
            output.push_str(&source[cursor..]);
            break;
        };
        let start = cursor + relative;
        let end = start + needle.len();
        output.push_str(&source[cursor..start]);
        if (start > 0 && is_identifier_byte(bytes[start - 1]))
            || (end < bytes.len() && is_identifier_byte(bytes[end]))
        {
            output.push_str(needle);
            cursor = end;
            continue;
        }
        let mut offset = end;
        skip_space(bytes, &mut offset);
        if bytes.get(offset) != Some(&b'[') {
            output.push_str(needle);
            cursor = end;
            continue;
        }
        offset += 1;
        skip_space(bytes, &mut offset);
        let digits_start = offset;
        while bytes.get(offset).is_some_and(u8::is_ascii_digit) {
            offset += 1;
        }
        let Some(found_index) = source[digits_start..offset].parse::<u32>().ok() else {
            return Err(GalError::unsupported_feature(
                "gl_FragData index is not a literal",
            ));
        };
        skip_space(bytes, &mut offset);
        if bytes.get(offset) != Some(&b']') {
            return Err(GalError::unsupported_feature(
                "malformed gl_FragData output index",
            ));
        }
        offset += 1;
        if found_index == index {
            output.push_str(replacement);
            occurrences += 1;
        } else {
            output.push_str(&source[start..offset]);
        }
        cursor = offset;
    }
    Ok((output, occurrences))
}

fn contains_fragment_output(source: &str) -> GalResult<bool> {
    let (_, occurrences) = replace_fragment_output(source, u32::MAX, "")?;
    Ok(occurrences > 0 || source.contains("gl_FragData"))
}

fn skip_space(bytes: &[u8], offset: &mut usize) {
    while bytes.get(*offset).is_some_and(u8::is_ascii_whitespace) {
        *offset += 1;
    }
}

fn is_identifier_byte(byte: u8) -> bool {
    byte == b'_' || byte.is_ascii_alphanumeric()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::preprocess::{preprocess_artifact, PreprocessInput};
    use crate::render::vulkanic::shader_pack::source::{ShaderPackSource, ShaderSourceFile};

    fn artifact(source: &str) -> PreprocessedShaderSource {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new("terrain.fsh", source)],
        )
        .unwrap();
        preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap()
    }

    #[test]
    fn lowers_legacy_fragment_outputs_to_named_semantics_without_touching_varyings() {
        let lowered = lower_terrain_fragment_surface(&artifact(
            "#version 130\nvarying vec2 uv; uniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, uv); gl_FragData [ 1 ] = vec4(1.0); }",
        ))
        .unwrap();
        assert!(lowered.source().starts_with("#version 450\n"));
        assert!(lowered.source().contains("out_terrain_lit_color"));
        assert!(lowered.source().contains("out_terrain_material_auxiliary"));
        assert!(lowered.source().contains("texture(tex, uv)"));
        assert!(!lowered.source().contains("gl_FragData"));
        assert!(!lowered
            .remaining_dialect()
            .gaps()
            .contains(&super::super::dialect::GlslDialectGap::PreVulkanGlslVersion));
        assert!(lowered
            .remaining_dialect()
            .gaps()
            .contains(&super::super::dialect::GlslDialectGap::CompatibilityVertexAttributes));
    }

    #[test]
    fn rejects_outputs_outside_the_named_terrain_contract() {
        let error = lower_terrain_fragment_surface(&artifact(
            "#version 130\nvoid main() { gl_FragData[4] = vec4(1.0); }",
        ))
        .unwrap_err();
        assert!(error.to_string().contains("unsupported gl_FragData index"));
    }

    #[test]
    fn lowers_known_legacy_vertex_semantics_without_assigning_java_locations() {
        let lowered = lower_terrain_vertex_surface(&artifact(
            "#version 130\nattribute vec4 mc_Entity;\nattribute vec4 mc_midTexCoord;\nattribute vec4 at_tangent;\nattribute vec3 at_midBlock;\nvarying vec2 uv;\nvoid main() { uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; vec3 n = gl_NormalMatrix * gl_Normal + at_midBlock / 64.0; float id = mc_Entity.x + mc_midTexCoord.x + at_tangent.w; gl_Position = ftransform() + gl_ModelViewMatrix * gl_Vertex + gl_ProjectionMatrix * gl_Color + vec4(n, id); }",
        ))
        .unwrap();
        assert!(lowered.source().contains("VulkanicSourceTerrainVertex"));
        assert!(lowered.source().contains("VulkanicSourceTerrainInstances"));
        assert!(lowered.source().contains("vulkanic_source_mid_tex_coord"));
        assert!(lowered.source().contains("vulkanic_source_mid_block"));
        assert!(lowered.source().contains("vulkanic_source_model_view"));
        assert!(lowered.source().contains("out vec2 uv"));
        assert!(!lowered.source().contains("attribute vec4"));
        assert!(!lowered.source().contains("gl_MultiTexCoord0"));
        assert!(!lowered.source().contains("gl_ModelViewMatrix"));
        assert!(lowered.remaining_dialect().gaps().is_empty());
    }

    #[test]
    fn lowers_shadow_color_outputs_without_relabeling_them_as_terrain_gbuffer_data() {
        let lowered = lower_shadow_fragment_surface(&artifact(
            "#version 130\nuniform sampler2D tex;\nvoid main() { vec4 color = texture2D(tex, vec2(0.25)); gl_FragData[0] = color; gl_FragData[1] = vec4(color.rgb * 0.25, 1.0); }",
        ))
        .unwrap();
        assert_eq!(
            vec![
                ShadowFragmentOutput::ShadowColor,
                ShadowFragmentOutput::LightShaftColor,
            ],
            lowered.outputs()
        );
        assert!(lowered.source().contains("out_shadow_color"));
        assert!(lowered.source().contains("out_shadow_light_shaft_color"));
        assert!(!lowered.source().contains("out_terrain_lit_color"));
        assert!(!lowered.source().contains("gl_FragData"));
    }

    #[test]
    fn lowers_the_selected_scoped_shadow_fragment_without_a_terrain_output_alias() {
        let source =
            crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test(
            );
        let stages = crate::render::vulkanic::shader_pack::terrain_contract::shadow_source_stages_for_scope(
            &source,
            crate::render::vulkanic::shader_pack::terrain_contract::TerrainProgramScope::Overworld,
        )
        .unwrap();
        let artifacts =
            crate::render::vulkanic::shader_pack::preprocess::preprocess_terrain_sources(
                &source, &stages,
            )
            .unwrap();
        let lowered = lower_shadow_fragment_surface(&artifacts.fragment).unwrap();
        assert!(lowered
            .outputs()
            .contains(&ShadowFragmentOutput::ShadowColor));
        assert!(lowered.source().contains("out_shadow_color"));
        assert!(!lowered.source().contains("out_terrain_lit_color"));
    }

    #[test]
    fn shadow_pair_uses_shadow_transforms_and_keeps_shadow_outputs_distinct() {
        let pack = ShaderPackSource::new(
            "shadow-test",
            1,
            vec![
                ShaderSourceFile::new(
                    "shadow.vsh",
                    "#version 130\nout vec2 tex_coord;\nvoid main() { tex_coord = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
                ),
                ShaderSourceFile::new(
                    "shadow.fsh",
                    "#version 130\nin vec2 tex_coord;\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, tex_coord); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "shadow.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "shadow.fsh",
            defines: &[],
        })
        .unwrap();

        let lowered = lower_shadow_source_pair(&vertex, &fragment).unwrap();

        assert!(lowered.vertex().source().contains("shadowModelView"));
        assert!(lowered.vertex().source().contains("shadowProjection"));
        assert!(!lowered.vertex().source().contains("gbufferModelView"));
        assert!(lowered.fragment().source().contains("layout(location = 0) in vec2 tex_coord"));
        assert!(lowered.fragment().source().contains("out_shadow_color"));
        assert!(!lowered.fragment().source().contains("out_terrain_lit_color"));
        lowered.require_backend_neutral_lowering().unwrap();
    }

    #[test]
    fn selected_scoped_shadow_pair_preserves_shadow_transform_and_output_semantics() {
        let source =
            crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test(
            );
        let stages = crate::render::vulkanic::shader_pack::terrain_contract::shadow_source_stages_for_scope(
            &source,
            crate::render::vulkanic::shader_pack::terrain_contract::TerrainProgramScope::Overworld,
        )
        .unwrap();
        let artifacts =
            crate::render::vulkanic::shader_pack::preprocess::preprocess_terrain_sources(
                &source, &stages,
            )
            .unwrap();

        let lowered = lower_shadow_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();

        assert!(lowered.vertex().source().contains("shadowModelView"));
        assert!(lowered.vertex().source().contains("shadowProjection"));
        assert!(!lowered.vertex().source().contains("#define vulkanic_source_model_view (gbufferModelView"));
        assert!(lowered
            .fragment()
            .outputs()
            .contains(&ShadowFragmentOutput::ShadowColor));
        assert!(!lowered.fragment().source().contains("out_terrain_lit_color"));
        lowered.require_backend_neutral_lowering().unwrap();
    }

    #[test]
    fn rejects_unknown_legacy_vertex_attributes() {
        let error = lower_terrain_vertex_surface(&artifact(
            "#version 130\nattribute vec4 unmodeled_input;\nvoid main() {}",
        ))
        .unwrap_err();
        assert!(error.to_string().contains("unmodeled_input"));
    }

    #[test]
    fn pair_lowering_uses_one_deterministic_uniform_block_for_both_stages() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform float time;\nuniform mat4 view;\nvoid main() { gl_Position = gl_Vertex + vec4(time + view[0][0]); }",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform float time;\nuniform vec3 fog_color;\nvoid main() { gl_FragData[0] = vec4(time + fog_color.x); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        assert_eq!(
            lowered.uniform_contract().declarations(),
            ["vec3 fog_color;", "float time;", "mat4 view;"]
        );
        assert_eq!(96, lowered.uniform_contract().std140_size());
        assert_eq!(
            vec![
                ("fog_color", TerrainSourceUniformType::Vec3, 0, 16, 0),
                ("time", TerrainSourceUniformType::Float, 16, 4, 0),
                ("view", TerrainSourceUniformType::Mat4, 32, 64, 0),
            ],
            lowered
                .uniform_contract()
                .fields()
                .iter()
                .map(|field| (
                    field.name(),
                    field.ty(),
                    field.offset(),
                    field.size(),
                    field.array_stride(),
                ))
                .collect::<Vec<_>>()
        );
        let expected = uniform_block(lowered.uniform_contract());
        assert!(lowered.vertex().source().contains(&expected));
        assert!(lowered.fragment().source().contains(&expected));
    }

    #[test]
    fn paired_uniform_type_mismatch_is_rejected_before_lowering() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform float shared;\nvoid main() { gl_Position = vec4(shared); }",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform vec2 shared;\nvoid main() { gl_FragData[0] = vec4(shared, 0.0, 1.0); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let error = lower_terrain_source_pair(&vertex, &fragment).unwrap_err();
        assert!(error
            .to_string()
            .contains("incompatible paired declarations"));
    }

    #[test]
    fn scalar_uniform_layout_rejects_unknown_types_and_lays_out_arrays() {
        let array = artifact(
            "#version 130\nuniform vec2 weights[3];\nvoid main() { gl_Position = vec4(weights[0], 0.0, 1.0); }",
        );
        let layout = derive_terrain_source_uniform_contract(&array, &array).unwrap();
        assert_eq!(48, layout.std140_size());
        assert_eq!(1, layout.fields().len());
        let field = &layout.fields()[0];
        assert_eq!("weights", field.name());
        assert_eq!(TerrainSourceUniformType::Vec2, field.ty());
        assert_eq!(3, field.array_length());
        assert_eq!(0, field.offset());
        assert_eq!(48, field.size());
        assert_eq!(16, field.array_stride());

        let unsupported = artifact(
            "#version 130\nuniform double invalid;\nvoid main() { gl_Position = vec4(float(invalid)); }",
        );
        let error = derive_terrain_source_uniform_contract(&unsupported, &unsupported)
            .unwrap_err()
            .to_string();
        assert!(error.contains("double"));
        assert!(error.contains("std140 semantic layout"));
    }

    #[test]
    fn uniform_contract_omits_declaration_only_values_but_keeps_lowered_legacy_matrices() {
        let source = artifact(
            "#version 130\nuniform float unused;\nuniform float active;\nuniform mat4 gbufferModelView;\nuniform mat4 gbufferProjection;\n// unused must not count as a source reference\nvoid main() { gl_Position = ftransform() + vec4(active); }",
        );
        let contract = derive_terrain_source_uniform_contract(&source, &source).unwrap();
        assert_eq!(
            [
                "float active;",
                "mat4 gbufferModelView;",
                "mat4 gbufferProjection;",
            ],
            contract.declarations()
        );
        assert_eq!(144, contract.std140_size());

        let identifiers = glsl_identifiers("// ignored\nactive /* ignored too */ gbufferModelView");
        assert!(identifiers.contains("active"));
        assert!(identifiers.contains("gbufferModelView"));
        assert!(!identifiers.contains("ignored"));
    }

    #[test]
    fn legacy_transform_lowering_rejects_conflicting_semantic_matrix_declarations() {
        let source = artifact(
            "#version 130\nuniform float gbufferModelView;\nvoid main() { gl_Position = ftransform(); }",
        );
        let error = derive_terrain_source_uniform_contract(&source, &source)
            .unwrap_err()
            .to_string();
        assert!(error.contains("gbufferModelView"));
        assert!(error.contains("legacy transform"));
    }

    #[test]
    fn pair_lowering_assigns_matching_varyings_stable_locations() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nflat out int material;\nout vec2 atlas_uv;\nout vec3 normal;\nvoid main() { gl_Position = gl_Vertex; }",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nflat in int material;\nin vec2 atlas_uv;\nin vec3 normal;\nvoid main() { gl_FragData[0] = vec4(float(material) + atlas_uv.x + normal.x); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        assert_eq!(
            lowered
                .varying_contract()
                .fields()
                .iter()
                .map(|field| (field.name(), field.location()))
                .collect::<Vec<_>>(),
            vec![("atlas_uv", 0), ("material", 1), ("normal", 2)]
        );
        assert!(lowered
            .vertex()
            .source()
            .contains("layout(location = 1) flat out int material;"));
        assert!(lowered
            .fragment()
            .source()
            .contains("layout(location = 1) flat in int material;"));
    }

    #[test]
    fn paired_varying_mismatch_is_rejected_before_lowering() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nout vec3 normal;\nvoid main() {}",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nin vec2 normal;\nvoid main() { gl_FragData[0] = vec4(1.0); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let error = lower_terrain_source_pair(&vertex, &fragment).unwrap_err();
        assert!(error.to_string().contains("differs between vertex output"));
    }

    #[test]
    fn pair_lowering_assigns_stable_bindings_to_opaque_resources() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform sampler2D atlas;\nuniform usampler3D occupancy;\nvoid main() {}",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform sampler2D atlas;\nwriteonly uniform image3D output_volume;\nvoid main() { gl_FragData[0] = texture2D(atlas, vec2(0.0)); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        assert_eq!(
            lowered
                .opaque_resource_contract()
                .resources()
                .iter()
                .map(|resource| (resource.name(), resource.binding()))
                .collect::<Vec<_>>(),
            vec![("atlas", 0), ("occupancy", 1), ("output_volume", 2)]
        );
        assert!(lowered
            .vertex()
            .source()
            .contains("layout(set = 1, binding = 0) uniform sampler2D atlas;"));
        assert!(lowered
            .fragment()
            .source()
            .contains("layout(set = 1, binding = 2) writeonly uniform image3D output_volume;"));
    }

    #[test]
    fn paired_opaque_resource_type_mismatch_is_rejected_before_lowering() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform sampler2D shared;\nvoid main() {}",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform usampler2D shared;\nvoid main() { gl_FragData[0] = vec4(1.0); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let error = lower_terrain_source_pair(&vertex, &fragment).unwrap_err();
        assert!(error.to_string().contains("opaque resource 'shared'"));
    }

    #[test]
    fn opaque_resources_require_exact_pack_declared_semantic_roles() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform sampler2D tex;\nuniform usampler3D voxel_sampler;\nuniform sampler2D unused_global;\nvoid main() {}",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform sampler2D tex;\nuniform usampler3D voxel_sampler;\nuniform sampler2D unused_global;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
                ),
                ShaderSourceFile::new(
                    super::super::terrain_source_resources::TERRAIN_RESOURCE_BINDINGS_PATH,
                    "tex=material_atlas\nunused_global=noise\n",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
        let plan = lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap();
        assert_eq!(
            vec![("tex", TerrainSourceResourceRole::MaterialAtlas, 0)],
            plan.bindings()
                .iter()
                .map(|binding| (binding.resource_name(), binding.role(), binding.binding()))
                .collect::<Vec<_>>()
        );
    }

    #[test]
    fn opaque_resource_plan_ignores_unreferenced_global_sampler_declarations() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform sampler2D tex;\nuniform sampler2D unused_global;\nvoid main() {}",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform sampler2D tex;\nuniform sampler2D unused_global;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
                ),
                ShaderSourceFile::new(
                    super::super::terrain_source_resources::TERRAIN_RESOURCE_BINDINGS_PATH,
                    "tex=material_atlas\n",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        let resources = lowered.opaque_resource_contract().resources();
        assert_eq!(
            vec![("tex", true), ("unused_global", false)],
            resources
                .iter()
                .map(|resource| (resource.name(), resource.active()))
                .collect::<Vec<_>>()
        );
        let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
        let plan = lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap();
        assert_eq!(1, plan.bindings().len());
        assert_eq!(
            vec!["tex"],
            plan.bindings()
                .iter()
                .map(|binding| binding.resource_name())
                .collect::<Vec<_>>()
        );
        assert!(lowered
            .fragment()
            .source()
            .contains("layout(set = 1, binding = 1) uniform sampler2D unused_global;"));
    }

    #[test]
    fn opaque_resources_reject_missing_or_unused_semantic_roles() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform sampler2D tex;\nvoid main() {}",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
        assert!(lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap_err()
            .to_string()
            .contains("have no declared semantic roles: tex"));
    }

    #[test]
    fn opaque_resources_ignore_pack_wide_declarations_owned_by_other_source_stages() {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new(
                    "terrain.vsh",
                    "#version 130\nuniform sampler2D tex;\nvoid main() {}",
                ),
                ShaderSourceFile::new(
                    "terrain.fsh",
                    "#version 130\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
                ),
                ShaderSourceFile::new(
                    super::super::terrain_source_resources::TERRAIN_RESOURCE_BINDINGS_PATH,
                    "tex=material_atlas\nabsent=noise\n",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "terrain.fsh",
            defines: &[],
        })
        .unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
        let plan = lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap();
        assert_eq!(
            vec!["tex"],
            plan.bindings()
                .iter()
                .map(|binding| binding.resource_name())
                .collect::<Vec<_>>()
        );
    }
}
