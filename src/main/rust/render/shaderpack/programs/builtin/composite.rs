//! Built-in composite, deferred-lighting, final-copy and shadow-depth programs.

use super::*;

pub fn minimal_g_buffer_composite_program() -> CompositeProgram {
    minimal_deferred_lighting_program()
}

pub fn minimal_deferred_lighting_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/deferred_lighting_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-deferred-lighting.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-deferred-lighting.fragment".to_string(),
            source: MINIMAL_DEFERRED_LIGHTING_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_composite_color_grade_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/composite_color_grade_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-composite-color-grade.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-composite-color-grade.fragment".to_string(),
            source: MINIMAL_COMPOSITE_COLOR_GRADE_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_composite_depth_fog_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/composite_depth_fog_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-composite-depth-fog.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-composite-depth-fog.fragment".to_string(),
            source: MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_final_copy_program() -> CompositeProgram {
    CompositeProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/final_copy_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-final-copy.vertex".to_string(),
            source: MINIMAL_FULLSCREEN_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-final-copy.fragment".to_string(),
            source: MINIMAL_FINAL_COPY_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
    }
}

pub fn minimal_shadow_depth_program() -> TerrainMaterialProgram {
    TerrainMaterialProgram {
        identity: ProgramIdentity::new("vulkanic:builtin/shadow_depth_v1"),
        vertex: ShaderStageSource {
            stage: ShaderStageKind::Vertex,
            label: "minimal-shadow-depth.vertex".to_string(),
            source: MINIMAL_SHADOW_DEPTH_VERTEX.to_string(),
            entry_point: "main".to_string(),
        },
        fragment: ShaderStageSource {
            stage: ShaderStageKind::Fragment,
            label: "minimal-shadow-depth.fragment".to_string(),
            source: MINIMAL_SHADOW_DEPTH_FRAGMENT.to_string(),
            entry_point: "main".to_string(),
        },
        required_resources: Vec::new(),
    }
}
