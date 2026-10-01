//! Program model shared by built-in and pack programs: identities, stage sources, material programs and shader-convention adaptation.

use super::*;

/// Dimension tag for program identities. Every lowered-program cache (layouts,
/// pipelines, pack resources) keys on identity, and a pack's Nether/End
/// programs share their overworld program's kind name; without the tag a
/// dimension change reused the overworld layout and pipelines.
pub(super) fn scope_identity_tag(scope: IdentityScope) -> &'static str {
    match scope {
        IdentityScope::Default | IdentityScope::Overworld => "",
        IdentityScope::Nether => "_nether",
        IdentityScope::End => "_end",
    }
}

/// The shadow constructor has no dimension scope; tag non-overworld variants
/// by their actual lowered source and bindings so per-dimension shadow
/// programs never share a cached layout or pipeline.
pub(super) fn shadow_program_identity_tag(
    lowered: &LoweredShadowSourcePair,
    bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> String {
    let tag = program_path_identity_tag(lowered.fragment().entry_path());
    // Deterministic FNV-1a: the identity keys process-lifetime caches.
    let mut value: u64 = 0xcbf2_9ce4_8422_2325;
    for byte in lowered
        .fragment()
        .source()
        .bytes()
        .chain(format!("{bindings:?}").into_bytes())
    {
        value = (value ^ u64::from(byte)).wrapping_mul(0x0000_0100_0000_01b3);
    }
    format!("{tag}_{value:016x}")
}

pub(super) fn program_path_identity_tag(path: &str) -> &'static str {
    if path.starts_with("world-1/") {
        "_nether"
    } else if path.starts_with("world1/") {
        "_end"
    } else {
        ""
    }
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
/// Shared so per-draw cache keys clone it without allocating.
pub struct ProgramIdentity(pub(super) std::sync::Arc<str>);

impl ProgramIdentity {
    pub fn new(value: impl Into<String>) -> Self {
        Self(std::sync::Arc::from(value.into()))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShaderStageKind {
    Vertex,
    Fragment,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderStageSource {
    pub stage: ShaderStageKind,
    pub label: String,
    pub source: String,
    pub entry_point: String,
}

impl ShaderStageSource {
    /// Converts owned shader-pack text into an explicit GAL shader-module
    /// description. This selects only the portable coordinate convention
    /// required by the target API; backend compilation and native objects
    /// remain private to their respective backends.
    pub fn shader_module_descriptor(&self, conventions: ShaderConventions) -> ShaderModuleDesc {
        let stage = match self.stage {
            ShaderStageKind::Vertex => ShaderStage::Vertex,
            ShaderStageKind::Fragment => ShaderStage::Fragment,
        };
        ShaderModuleDesc {
            label: self.label.clone(),
            stage,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code(conventions, &self.source),
            entry_point: self.entry_point.clone(),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainMaterialProgram {
    pub identity: ProgramIdentity,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
    /// Backend-neutral semantic resources required in addition to the mesh
    /// material set. Pipeline construction turns these into ordinary GAL
    /// resource layouts; program descriptions never contain backend handles.
    pub required_resources: Vec<TerrainProgramResource>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainProgramResource {
    ColoredVoxelLightVolume,
}

impl TerrainMaterialProgram {
    /// Converts a Rust-owned built-in terrain program into explicit GAL
    /// shader modules. This is shared by ordinary mesh materials and DH LOD
    /// materials so neither frontend has to reproduce backend dialect
    /// selection or module descriptions.
    pub fn shader_module_descriptors(&self, conventions: ShaderConventions) -> [ShaderModuleDesc; 2] {
        [
            self.vertex.shader_module_descriptor(conventions),
            self.fragment.shader_module_descriptor(conventions),
        ]
    }

    pub fn requires(&self, resource: TerrainProgramResource) -> bool {
        self.required_resources.contains(&resource)
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CompositeProgram {
    pub identity: ProgramIdentity,
    pub vertex: ShaderStageSource,
    pub fragment: ShaderStageSource,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainMaterialProgramKind {
    Opaque,
    Cutout,
    Translucent,
}

impl TerrainMaterialProgramKind {
    pub(super) fn identity(self) -> ProgramIdentity {
        match self {
            Self::Opaque => ProgramIdentity::new("vulkanic:builtin/terrain_opaque_v1"),
            Self::Cutout => ProgramIdentity::new("vulkanic:builtin/terrain_cutout_v1"),
            Self::Translucent => ProgramIdentity::new("vulkanic:builtin/terrain_translucent_v1"),
        }
    }

    pub(super) fn label_suffix(self) -> &'static str {
        match self {
            Self::Opaque => "opaque",
            Self::Cutout => "cutout",
            Self::Translucent => "translucent",
        }
    }
}

/// Adapts shared GLSL text to the backend's native conventions by defining
/// the `VULKANIC_GAL_*` convention macros the shader may test.
pub fn shader_stage_code(conventions: ShaderConventions, source: &str) -> Vec<u8> {
    let mut defines = String::new();
    if conventions.zero_to_one_clip_depth {
        defines.push_str("#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1\n");
    }
    // When a backend's framebuffer rows and sampled-image coordinates differ
    // from the pass graph's top-left image convention, every fullscreen
    // transfer compensates exactly once; otherwise forward work inserted
    // after a transfer acquires a different vertical parity from G-buffer
    // content.
    if conventions.flip_fullscreen_uv_y {
        defines.push_str("#define VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y 1\n");
    }
    if defines.is_empty() {
        return source.as_bytes().to_vec();
    }
    source
        .replacen("#version 450\n", &format!("#version 450\n{defines}"), 1)
        .into_bytes()
}

#[cfg(test)]
pub(super) mod shader_stage_code_tests {
    use crate::render::shaderpack::programs::*;

    #[test]
    fn vulkan_fullscreen_transfers_correct_framebuffer_to_texture_row_origin() {
        let source = "#version 450\n#ifdef VULKANIC_GAL_FULLSCREEN_UV_TOP_ORIGIN\n#endif\n";
        let lowered = String::from_utf8(shader_stage_code(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions, source))
            .expect("Vulkan shader source must remain UTF-8");

        assert!(lowered.contains("#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1"));
        assert!(lowered.contains("#define VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y 1"));
        assert!(!lowered.contains("#define VULKANIC_GAL_FULLSCREEN_UV_TOP_ORIGIN 1"));
    }

    #[test]
    fn vulkan_fullscreen_transfers_correct_the_framebuffer_to_texture_row_origin() {
        let source = "#version 450\n#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y\n#endif\n";
        let lowered = String::from_utf8(shader_stage_code(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions, source))
            .expect("Vulkan shader source must remain UTF-8");

        assert!(lowered.contains("#define VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y 1"));
    }
}
