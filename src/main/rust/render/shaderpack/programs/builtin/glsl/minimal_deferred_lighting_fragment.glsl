#version 450
layout(set = 0, binding = 0) uniform texture2D AlbedoTex;
layout(set = 0, binding = 1) uniform texture2D NormalTex;
layout(set = 0, binding = 2) uniform texture2D MaterialLightTex;
layout(set = 0, binding = 3) uniform texture2D WorldPositionTex;
layout(set = 0, binding = 4) uniform texture2D ShadowDepthTex;
layout(set = 0, binding = 5) uniform sampler Samp0;
layout(set = 0, binding = 6, std430) readonly buffer ShaderCompositeUniforms {
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 color_grade_params;
    mat4 projection_inverse;
    vec4 fog_color_and_environmental_start;
    vec4 fog_ranges;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 albedo = texture(sampler2D(AlbedoTex, Samp0), v_uv);
    vec4 material_light = texture(sampler2D(MaterialLightTex, Samp0), v_uv);
    if (material_light.a < 0.5) {
        out_color = vec4(albedo.rgb, 0.0);
        return;
    }
    vec4 packed_world = texture(sampler2D(WorldPositionTex, Samp0), v_uv);
    float shadow_range = max(shadow_params.w, 1.0);
    vec3 world_position = (packed_world.xyz * 2.0 - 1.0) * shadow_range;
    vec4 light_clip = light_view_projection * vec4(world_position, 1.0);
    vec3 light_ndc = light_clip.xyz / max(abs(light_clip.w), 0.0001);
    vec2 shadow_uv = light_ndc.xy * 0.5 + 0.5;
    float shadow_factor = 1.0;
    if (shadow_params.x > 0.5
            && shadow_uv.x >= 0.0 && shadow_uv.x <= 1.0
            && shadow_uv.y >= 0.0 && shadow_uv.y <= 1.0) {
        float shadow_depth = texture(sampler2D(ShadowDepthTex, Samp0), shadow_uv).r;
        float compare_depth = light_ndc.z * 0.5 + 0.5;
        float bias = shadow_params.y;
        shadow_factor = compare_depth - bias > shadow_depth ? shadow_params.z : 1.0;
    }
    // Terrain material passes write terrain_lit_color, not raw base color.
    // Their vertex color has already applied Minecraft's per-face shade and
    // packed block/sky-light factor. Relighting it here darkens horizontal
    // faces a second time and makes correct atlas regions look like unrelated
    // dark materials. Keep this pass limited to the explicit shadow dependency
    // until a source-derived deferred-lighting contract replaces it.
    out_color = vec4(albedo.rgb * shadow_factor, albedo.a);
}
