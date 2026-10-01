//! Built-in Distant Horizons LOD programs and their resource layouts.

use super::*;

/// First Rust-owned material program for Distant Horizons' opaque CPU LOD
/// stream. It is intentionally separate from Minecraft terrain: DH carries
/// pre-resolved vertex color and packed light, not atlas UVs or sprite
/// identities. The eventual frontend supplies its four explicit bindings as
/// Rust-owned GAL resources; this description contains no Java/DH GPU state.
pub fn minimal_distant_horizons_lod_opaque_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/distant_horizons_lod_opaque_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-distant-horizons-lod-opaque.vertex".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-distant-horizons-lod-opaque.fragment".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// Forward-color variant used when vanilla Rust Vulkan presents DH without a
/// shader-pack G-buffer. It keeps the same copied semantic inputs and depth
/// policy while writing the single acquired color attachment.
pub fn minimal_distant_horizons_lod_forward_opaque_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/distant_horizons_lod_forward_opaque_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-distant-horizons-lod-forward-opaque.vertex".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-distant-horizons-lod-forward-opaque.fragment".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// Rust-owned alpha-blended material program for Distant Horizons' non-water
/// transparent CPU LOD streams. It shares the copied DH vertex and semantic
/// lightmap contract with the opaque program, but writes one composited color
/// attachment and deliberately does not participate in the G-buffer or shadow
/// pass. Water is intentionally resolved by the separate Rust water-surface
/// pass, whose depth/cull/blend policy is not conflated with this generic lane.
pub fn minimal_distant_horizons_lod_transparent_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/distant_horizons_lod_transparent_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-distant-horizons-lod-transparent.vertex".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-distant-horizons-lod-transparent.fragment".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// Exact-atlas opaque DH material program. This is deliberately distinct from
/// the reduced color stream: it is admitted only for immutable column
/// segments whose copied face provenance resolves every quad to the owned
/// Minecraft terrain atlas.
pub fn minimal_distant_horizons_lod_exact_atlas_opaque_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new(
            "vulkanic:builtin/distant_horizons_lod_exact_atlas_opaque_v2",
        ),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-distant-horizons-lod-exact-atlas-opaque.vertex".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-distant-horizons-lod-exact-atlas-opaque.fragment".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// Forward-color exact-atlas DH material program used by the vanilla Rust
/// Vulkan one-target presentation graph. It shares the immutable atlas
/// geometry and descriptor layout with the deferred variant while emitting
/// only the target's color attachment.
pub fn minimal_distant_horizons_lod_exact_atlas_forward_opaque_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new(
            "vulkanic:builtin/distant_horizons_lod_exact_atlas_forward_opaque_v1",
        ),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-distant-horizons-lod-exact-atlas-forward-opaque.vertex".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-distant-horizons-lod-exact-atlas-forward-opaque.fragment".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// Exact-atlas DH writer for a source-derived frame. The selected DH program
/// writes one named primary color target; this private Rust writer therefore
/// retains that exact output schema while resolving immutable face ranges to
/// an owned Minecraft atlas. It does not reinterpret DH as an ordinary
/// terrain G-buffer writer.
pub fn minimal_distant_horizons_lod_exact_atlas_source_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new(
            "vulkanic:builtin/distant_horizons_lod_exact_atlas_source_v1",
        ),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-distant-horizons-lod-exact-atlas-source.vertex".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-distant-horizons-lod-exact-atlas-source.fragment".to_string(),
            source: MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_SOURCE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// The complete two-set binding contract for the private DH opaque program.
/// Set zero changes with a visible LOD segment; set one changes only with a
/// complete Rust-owned vanilla-lightmap generation. The descriptions are
/// backend-neutral and can be consumed unchanged by the Rust Vulkan and
/// OpenGL implementations.
pub fn distant_horizons_lod_opaque_resource_layouts(label: &str) -> [ResourceLayoutDesc; 2] {
    [
        ResourceLayoutDesc {
            label: format!("{label}.geometry-and-frame"),
            bindings: vec![
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::StorageBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::UniformBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
            ],
        },
        ResourceLayoutDesc {
            label: format!("{label}.lightmap"),
            bindings: vec![
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::SampledTexture,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::Sampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
            ],
        },
    ]
}

/// Three-set contract for a DH segment with fully resolved per-face Minecraft
/// atlas provenance. The atlas and lightmap are both Rust-owned semantic
/// resources; neither is a borrowed Java/OpenGL binding.
pub fn distant_horizons_lod_exact_atlas_resource_layouts(label: &str) -> [ResourceLayoutDesc; 2] {
    let [geometry_and_frame, _lightmap] = distant_horizons_lod_opaque_resource_layouts(label);
    [
        geometry_and_frame,
        ResourceLayoutDesc {
            label: format!("{label}.terrain-atlas-and-lightmap"),
            bindings: vec![
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::SampledTexture,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::Sampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 2,
                    kind: ResourceBindingKind::SampledTexture,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 3,
                    kind: ResourceBindingKind::Sampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
            ],
        },
    ]
}

/// Additional set used only by the source-derived exact-atlas DH adapter.
/// The selected pack's declared resources remain in its ordinary set one;
/// this set supplies the copied Minecraft atlas that turns resolved tile UVs
/// into the source program's semantic vertex color.
pub fn distant_horizons_exact_atlas_source_resource_layout(label: &str) -> ResourceLayoutDesc {
    ResourceLayoutDesc {
        label: format!("{label}.exact-atlas"),
        bindings: vec![
            ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::SampledTexture,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 1,
                kind: ResourceBindingKind::Sampler,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
        ],
    }
}
