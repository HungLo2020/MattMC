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
layout(location = 0) out vec4 out_albedo;
layout(location = 1) out vec4 out_normal;
layout(location = 2) out vec4 out_material_light;
layout(location = 3) out vec4 out_world_position;
void main() {
    // Exact Frozen core/sky.fsh call: apply_fog(ColorModulator, spherical,
    // cylindrical, 0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor).
    float sky_end = sky_end_padding.x;
    float spherical_fog = clamp(v_spherical_distance / max(sky_end, 0.0001), 0.0, 1.0);
    float cylindrical_fog = clamp(v_cylindrical_distance / max(sky_end, 0.0001), 0.0, 1.0);
    float fog = max(spherical_fog, cylindrical_fog);
    out_albedo = vec4(mix(v_color.rgb, fog_color.rgb, fog * fog_color.a), 1.0);
    // Deferred lighting treats material alpha below 0.5 as background and
    // forwards albedo unchanged. Normal alpha is the later terrain-fog
    // factor, however: this sky writer has already applied Frozen's sky-fog
    // equation above, so it must explicitly opt out of that separate terrain
    // fog composite instead of letting it replace the fan with clear fog.
    out_normal = vec4(0.5, 0.5, 1.0, 0.0);
    out_material_light = vec4(0.0, 1.0, 1.0, 0.0);
    out_world_position = vec4(0.5, 0.5, 0.5, 0.0);
}
