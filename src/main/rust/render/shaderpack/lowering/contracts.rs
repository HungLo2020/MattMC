//! Uniform, varying and opaque-resource interface contracts of lowered sources.

use super::*;

/// Deterministic scalar/vector/matrix uniform layout shared by the two source
/// stages. Samplers and images remain distinct named resources for a later
/// lowering step.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceUniformContract {
    pub(super) declarations: Vec<String>,
    pub(super) fields: Vec<TerrainSourceUniformField>,
    pub(super) std140_size: u32,
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
    pub(super) fn from_glsl(type_name: &str) -> GalResult<Self> {
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

    pub(super) fn std140_alignment(self) -> u32 {
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

    pub(super) fn std140_size(self) -> u32 {
        match self {
            Self::Float | Self::Int | Self::Uint | Self::Bool => 4,
            Self::Vec2 | Self::IVec2 | Self::UVec2 => 8,
            // A standalone vec3 has 16-byte alignment but occupies three
            // components. std140 permits a following scalar to use the
            // fourth component; arrays still round their stride to 16 below.
            // Treating vec3 as a 16-byte payload shifts every later scalar
            // field from the first vec3 onward.
            Self::Vec3 | Self::IVec3 | Self::UVec3 => 12,
            Self::Vec4 | Self::IVec4 | Self::UVec4 => 16,
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
    pub(super) name: String,
    pub(super) ty: TerrainSourceUniformType,
    pub(super) array_length: u32,
    pub(super) offset: u32,
    pub(super) size: u32,
    pub(super) array_stride: u32,
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
    pub(super) name: String,
    pub(super) type_name: String,
    pub(super) interpolation: String,
    pub(super) location: u32,
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
    pub(super) fields: Vec<TerrainSourceVaryingField>,
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
    pub(super) name: String,
    pub(super) type_name: String,
    pub(super) qualifiers: String,
    pub(super) kind: TerrainSourceOpaqueResourceKind,
    pub(super) binding: u32,
    pub(super) active: bool,
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
    pub(super) resource_name: String,
    pub(super) role: TerrainSourceResourceRole,
    pub(super) kind: TerrainSourceOpaqueResourceKind,
    pub(super) qualifiers: String,
    pub(super) binding: u32,
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
    pub(super) bindings: Vec<TerrainSourceOpaqueResourceBinding>,
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

    /// Reclassifies one already-declared active sampler at a pass-specific
    /// semantic boundary. This is deliberately narrow: source names remain
    /// pack data, but a legacy pack-wide `tex=material_atlas` declaration
    /// cannot force an entity pass to borrow terrain atlas ownership.
    pub fn with_sampled_role_override(
        &self,
        resource_name: &str,
        expected_role: TerrainSourceResourceRole,
        replacement_role: TerrainSourceResourceRole,
    ) -> GalResult<Self> {
        if expected_role.expected_sampler_type() != replacement_role.expected_sampler_type()
            || expected_role.expected_sampled_resource_shape()
                != replacement_role.expected_sampled_resource_shape()
        {
            return Err(GalError::invalid_argument(format!(
                "source resource role override '{}' -> '{}' is not sampler-compatible",
                expected_role.semantic_name(),
                replacement_role.semantic_name()
            )));
        }
        let mut bindings = self.bindings.clone();
        let binding = bindings
            .iter_mut()
            .find(|binding| binding.resource_name == resource_name)
            .ok_or_else(|| {
                GalError::unsupported_feature(format!(
                    "source resource plan has no active sampler '{resource_name}' to override"
                ))
            })?;
        if binding.kind != TerrainSourceOpaqueResourceKind::CombinedTextureSampler
            || binding.role != expected_role
        {
            return Err(GalError::unsupported_feature(format!(
                "source resource '{}' is not the expected '{}' sampled role",
                resource_name,
                expected_role.semantic_name()
            )));
        }
        binding.role = replacement_role;
        Ok(Self { bindings })
    }
}

/// Paired source resource table. Bindings are deterministic source-lowering
/// identities only, not native handles or runtime admission evidence.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceOpaqueResourceContract {
    pub(super) resources: Vec<TerrainSourceOpaqueResource>,
}

impl TerrainSourceOpaqueResourceContract {
    pub fn resources(&self) -> &[TerrainSourceOpaqueResource] {
        &self.resources
    }

    pub(super) fn resource_for(&self, name: &str) -> Option<&TerrainSourceOpaqueResource> {
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
            let Some(declared_role) = declarations.role_for(&resource.name) else {
                missing_roles.push(resource.name.as_str());
                continue;
            };
            let role = match resource.kind {
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler => {
                    declared_role.resolve_sampled_declaration(&resource.type_name)?
                }
                TerrainSourceOpaqueResourceKind::StorageImage => declared_role,
            };
            let expected_type = match resource.kind {
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler => {
                    role.expected_sampler_type()
                }
                TerrainSourceOpaqueResourceKind::StorageImage => {
                    role.expected_storage_image_type().ok_or_else(|| {
                        GalError::unsupported_feature(format!(
                            "terrain source role '{}' cannot provide storage-image resource '{}'",
                            role.semantic_name(),
                            resource.name
                        ))
                    })?
                }
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

    pub(super) fn location_for(&self, name: &str) -> Option<u32> {
        self.fields
            .iter()
            .find(|field| field.name == name)
            .map(|field| field.location)
    }
}
