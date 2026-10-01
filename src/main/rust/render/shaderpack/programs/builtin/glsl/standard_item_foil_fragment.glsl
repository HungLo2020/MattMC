#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 9) in vec2 v_fog_distances;
layout(location = 10) flat in vec4 v_fog_color_and_environmental_start;
layout(location = 11) flat in vec4 v_fog_ranges;
layout(location = 13) flat in float v_foil_strength;
layout(location = 0) out vec4 out_color;
float foil_fog_value(float distance, float start, float end) {
    // Frozen OpenGL fog.glsl sentinel/degenerate-range behavior.
    if (distance != distance || abs(distance) >= 1.0e12
        || max(abs(start), abs(end)) >= 1.0e12) return 0.0;
    if (end <= start) return distance > start ? 1.0 : 0.0;
    if (distance <= start) return 0.0;
    if (distance >= end) return 1.0;
    return clamp((distance - start) / (end - start), 0.0, 1.0);
}
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv) * v_color;
    if (color.a < 0.1) discard;
    float fog = max(
        foil_fog_value(v_fog_distances.x, v_fog_color_and_environmental_start.w, v_fog_ranges.x),
        foil_fog_value(v_fog_distances.y, v_fog_ranges.y, v_fog_ranges.z));
    // Foil fades to black; ordinary fog-color mixing is incorrect. Neither
    // strength nor fog changes texture alpha or discard coverage.
    out_color = vec4(color.rgb * ((1.0 - fog) * v_foil_strength), color.a);
}
