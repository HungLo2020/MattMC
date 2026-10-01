#version 450
layout(set = 0, binding = 0, std140) uniform WorldSkyDisc {
    mat4 view;
    mat4 projection;
    vec4 color;
    vec4 fog_color;
    vec4 sky_end_padding;
};
layout(location = 0) in vec4 v_color;
layout(location = 1) in float v_spherical_distance;
layout(location = 2) in float v_cylindrical_distance;
layout(location = 0) out vec4 out_color;
void main() {
    float sky_end = sky_end_padding.x;
    float spherical_fog = clamp(v_spherical_distance / max(sky_end, 0.0001), 0.0, 1.0);
    float cylindrical_fog = clamp(v_cylindrical_distance / max(sky_end, 0.0001), 0.0, 1.0);
    float fog = max(spherical_fog, cylindrical_fog);
    out_color = vec4(mix(v_color.rgb, fog_color.rgb, fog * fog_color.a), 1.0);
}
