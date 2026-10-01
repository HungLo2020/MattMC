//! Built-in entity outline and first-person optical stencil programs.

use super::*;

/// Rust-owned solid-color entity-outline mask program. It deliberately uses
/// the ordinary mesh/instance storage ABI so the eventual mask writer can
/// share copied geometry without importing a Java renderer or native handle.
pub fn minimal_entity_outline_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/entity_outline_mask_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-entity-outline.vertex".to_string(),
            source: MINIMAL_ENTITY_OUTLINE_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-entity-outline.fragment".to_string(),
            source: MINIMAL_ENTITY_OUTLINE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}

/// Rust-owned fragment-only optical mask program.  The vertex ABI remains the
/// copied world-mesh instance stream, while the fragment writes zero alpha so
/// the optical target preserves its copied color and changes only stencil.
pub fn minimal_optical_stencil_write_program() -> TerrainMaterialProgram {
    let mut program = minimal_direct_terrain_cutout_program();
    program.identity = ProgramIdentity::new("vulkanic:builtin/optical_stencil_write_v1");
    program.fragment = ShaderStageSource {
        stage: ShaderStageKind::Fragment,
        label: "minimal-optical-stencil-write.fragment".to_string(),
        source: MINIMAL_OPTICAL_STENCIL_WRITE_FRAGMENT.to_string(),
        entry_point: "main".to_string(),
    };
    program
}
