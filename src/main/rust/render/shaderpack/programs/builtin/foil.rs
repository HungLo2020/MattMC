//! Built-in item and world-decal foil (glint) programs.

use super::*;

/// Private standard model foil program. Requires an explicit per-instance
/// foil buffer at set0/binding4 and original UVs. The explicit frontend owns
/// transport, binding and lifetime validation. Never accepts decal foil.
pub const STANDARD_ITEM_FOIL_PROGRAM_ID: &str = "vulkanic:builtin/direct_standard_item_foil_v1";

pub fn minimal_direct_standard_item_foil_program() -> TerrainMaterialProgram {
    let mut program = minimal_direct_terrain_cutout_program();
    program.identity = ProgramIdentity::new(STANDARD_ITEM_FOIL_PROGRAM_ID);
    program.vertex.label = "direct-standard-item-foil.vertex".to_string();
    program.vertex.source = MINIMAL_TERRAIN_MATERIAL_VERTEX.replacen(
        "#version 450\n",
        "#version 450\n#define VULKANIC_STANDARD_ITEM_FOIL 1\n",
        1,
    );
    program.fragment.label = "direct-standard-item-foil.fragment".to_string();
    program.fragment.source = STANDARD_ITEM_FOIL_FRAGMENT.to_string();
    program
}

pub const WORLD_DECAL_FOIL_PROGRAM_ID: &str = "vulkanic:builtin/direct_world_decal_foil_v1";

pub fn minimal_direct_world_decal_foil_program() -> TerrainMaterialProgram {
    let mut program = minimal_direct_standard_item_foil_program();
    program.identity = ProgramIdentity::new(WORLD_DECAL_FOIL_PROGRAM_ID);
    program.vertex.label = "direct-world-decal-foil.vertex".to_string();
    program.vertex.source = program.vertex.source.replace(
        "    ItemFoilInstance foil_instances[];",
        "    uint foil_words[];",
    );
    let helpers = r#"
vec4 decal_foil_vec4(uint offset) {
    return uintBitsToFloat(uvec4(foil_words[offset], foil_words[offset+1u],
        foil_words[offset+2u], foil_words[offset+3u]));
}
ItemFoilInstance load_decal_foil(uint instance_index) {
    uint start = instance_index * 12u;
    return ItemFoilInstance(decal_foil_vec4(start), decal_foil_vec4(start+4u), decal_foil_vec4(start+8u));
}
vec2 world_decal_uv(uint instance_index, uint vertex_index) {
    uint start = foil_words[instance_index * 12u + 9u] + vertex_index * 2u;
    return uintBitsToFloat(uvec2(foil_words[start], foil_words[start+1u]));
}
"#;
    program.vertex.source = program.vertex.source.replace("void main() {", &format!("{helpers}\nvoid main() {{"))
        .replace("foil_instances[gl_InstanceIndex]", "load_decal_foil(uint(gl_InstanceIndex))")
        .replace("vec3 original_uv = vec3(vertex.position_uv.w, vertex.color_uv.w, 1.0);",
            "vec3 original_uv = vec3(world_decal_uv(uint(gl_InstanceIndex), uint(gl_VertexIndex)), 1.0);");
    program
}
