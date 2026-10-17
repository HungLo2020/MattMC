#version 450
layout(set = 0, binding = 0, std430) readonly buffer WorldSkyMaterialBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport_cutout;
    vec4 sky_fog_color;
};
layout(location = 1) in vec4 v_color;
layout(location = 3) in float v_camera_distance;
layout(location = 5) in float v_cylindrical_distance;
layout(location = 0) out vec4 out_color;

float sky_linear_fog(float distance, float start, float end) {
    if (distance != distance || abs(distance) >= 1.0e12
            || max(abs(start), abs(end)) >= 1.0e12) {
        return 0.0;
    }
    if (end <= start) {
        return distance > start ? 1.0 : 0.0;
    }
    return clamp((distance - start) / (end - start), 0.0, 1.0);
}

void main() {
    // Frozen core/sky.fsh uses spherical 0..FogSkyEnd and cylindrical
    // FogSkyEnd..FogSkyEnd. Keep the smooth per-vertex distance inputs.
    float end = viewport_cutout.w;
    float fog = max(sky_linear_fog(v_camera_distance, 0.0, end),
                    sky_linear_fog(v_cylindrical_distance, end, end));
    float blend = clamp(fog * sky_fog_color.a, 0.0, 1.0);
    out_color = vec4(mix(v_color.rgb, sky_fog_color.rgb, blend), v_color.a);
}
