#version 450
layout(set = 0, binding = 1) uniform texture2D Tex0;
layout(set = 0, binding = 2) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 3) in float v_camera_distance;
layout(location = 0) out vec4 out_color;
#ifdef VULKANIC_GAL_MAP_TEXT
layout(location = 5) in float v_cylindrical_distance;
layout(set = 0, binding = 0, std430) readonly buffer MapFog {
    mat4 view;
    mat4 projection;
    vec4 viewport_cutout;
    vec4 map_fog_color;
    vec4 map_fog_ranges;
};
float map_linear_fog(float distance, float start, float end) {
    if (distance != distance || abs(distance) >= 1.0e12 || max(abs(start), abs(end)) >= 1.0e12) return 0.0;
    if (end <= start) return distance > start ? 1.0 : 0.0;
    return clamp((distance - start) / (end - start), 0.0, 1.0);
}
#endif
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv) * v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
    // Vanilla's `rendertype_clouds.fsh` fades the already-modulated cloud
    // alpha by linear spherical fog from zero to `FogCloudsEnd`. This stays
    // a cloud-family semantic; weather and generic translucent materials do
    // not inherit it merely because they use the same private quad stream.
    if (v_material.y > 0.0) {
        color.a *= 1.0 - clamp(v_camera_distance / v_material.y, 0.0, 1.0);
    }
#ifdef VULKANIC_GAL_MAP_TEXT
    float fog = max(map_linear_fog(v_camera_distance, map_fog_ranges.x, map_fog_ranges.y),
        map_linear_fog(v_cylindrical_distance, map_fog_ranges.z, map_fog_ranges.w));
    color.rgb = mix(color.rgb, map_fog_color.rgb, clamp(fog * map_fog_color.a, 0.0, 1.0));
#endif
    out_color = color;
}
