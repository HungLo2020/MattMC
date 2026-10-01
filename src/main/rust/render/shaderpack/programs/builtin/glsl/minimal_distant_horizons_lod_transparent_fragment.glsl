#version 450
layout(location = 0) in vec4 v_color;
layout(location = 1) flat in uvec2 v_light;
layout(location = 2) flat in uint v_material;
layout(location = 3) flat in uint v_normal;
layout(location = 4) in vec3 v_world_position;
layout(location = 5) in vec3 v_source_position;
layout(location = 7) in vec3 v_light_color;
layout(location = 8) in vec3 v_unlit_color;
layout(set = 0, binding = 1, std140) uniform DistantHorizonsLodFrame {
    mat4 combined_matrix;
    vec4 column_origin_and_world_y;
    vec4 model_offset_and_reserved;
    vec4 clip_micro_noise_earth;
    uvec4 flags_and_noise;
    vec4 fog_color_and_alpha;
    vec4 fog_ranges;
    vec4 dh_fog_parameters[5];
};
layout(location = 0) out vec4 out_color;

vec3 dh_normal(uint normal) {
    if (normal == 0u) return vec3(0.0, -1.0, 0.0);
    if (normal == 1u) return vec3(0.0, 1.0, 0.0);
    if (normal == 2u) return vec3(0.0, 0.0, -1.0);
    if (normal == 3u) return vec3(0.0, 0.0, 1.0);
    if (normal == 4u) return vec3(-1.0, 0.0, 0.0);
    return vec3(1.0, 0.0, 0.0);
}

float dh_fragment_fade() {
    float distance_to_camera = distance(v_world_position,
        column_origin_and_world_y.xyz - model_offset_and_reserved.xyz);
    float dh_clip = clip_micro_noise_earth.x;
    return dh_clip > 0.0 ? smoothstep(dh_clip, dh_clip * 1.5, distance_to_camera) : 1.0;
}

float dh_fog_curve(float value, float start, float length, float minimum, float range, float density, float falloff) {
    if (falloff < 0.5) {
        return minimum + range * clamp((value - start) / max(length, 0.0001), 0.0, 1.0);
    }
    float x = max((value - start) / max(length, 0.0001), 0.0) * density;
    float attenuation = falloff < 1.5 ? exp(-x) : exp(-x * x);
    return minimum + range - range * attenuation;
}

float dh_fog_factor(vec3 world_position) {
    // DH far/height fog is normalized to the configured LOD draw distance.
    // A disabled DH fog block deliberately falls back to copied vanilla fog.
    if (dh_fog_parameters[4].x < 0.5) return -1.0;
    vec3 camera_position = column_origin_and_world_y.xyz - model_offset_and_reserved.xyz;
    float lod_distance = max(dh_fog_parameters[1].y, 1.0);
    float horizontal_distance = length((world_position - camera_position).xz);
    float spherical_distance = distance(world_position, camera_position);
    float active_distance = dh_fog_parameters[4].z > 0.5 ? spherical_distance : horizontal_distance;
    float far = dh_fog_curve(
        active_distance, dh_fog_parameters[0].x * lod_distance,
        (dh_fog_parameters[0].y - dh_fog_parameters[0].x) * lod_distance,
        dh_fog_parameters[0].z, dh_fog_parameters[0].w - dh_fog_parameters[0].z,
        dh_fog_parameters[1].x, dh_fog_parameters[3].x);
    float height = 0.0;
    if (dh_fog_parameters[4].y > 0.5) {
        float camera_y = camera_position.y;
        float height_position = world_position.y;
        float direction = dh_fog_parameters[3].w;
        if (mod(direction, 2.0) < 0.5) {
            height_position -= dh_fog_parameters[1].z - camera_y;
        }
        bool applies_up = mod(floor(direction / 2.0), 2.0) > 0.5;
        bool applies_down = mod(floor(direction / 4.0), 2.0) > 0.5;
        float vertical_distance = applies_up && applies_down
            ? abs(height_position)
            : (applies_down ? -height_position : height_position);
        float vertical_scale = dh_fog_parameters[4].w > 0.0
            ? dh_fog_parameters[4].w : (1.0 / 384.0);
        vertical_distance *= vertical_scale;
        height = dh_fog_curve(
            vertical_distance, dh_fog_parameters[1].w, dh_fog_parameters[2].x - dh_fog_parameters[1].w,
            dh_fog_parameters[2].y, dh_fog_parameters[2].z - dh_fog_parameters[2].y,
            dh_fog_parameters[2].w, dh_fog_parameters[3].y);
    }
    int mode = int(dh_fog_parameters[3].z + 0.5);
    if (mode == 0 || mode == 1) return clamp(far, 0.0, 1.0);
    if (mode == 2) return clamp(max(far, height), 0.0, 1.0);
    if (mode == 3) return clamp(far + height, 0.0, 1.0);
    if (mode == 4) return clamp(far * height, 0.0, 1.0);
    if (mode == 5) return clamp(1.0 - (1.0 - far) * (1.0 - height), 0.0, 1.0);
    if (mode == 6) return clamp(far + max(far, height), 0.0, 1.0);
    if (mode == 7) return clamp(far + far * height, 0.0, 1.0);
    if (mode == 8) return clamp(far + 1.0 - (1.0 - far) * (1.0 - height), 0.0, 1.0);
    if (mode == 9) return clamp(far * 0.5 + height * 0.5, 0.0, 1.0);
    return clamp(far, 0.0, 1.0);
}


float dh_rand(float co) { return fract(sin(co * 91.3458) * 47453.5453); }
float dh_rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
float dh_rand(vec3 co) { return dh_rand(co.xy + dh_rand(co.z)); }

vec3 dh_apply_noise(vec3 color, float alpha, float view_distance, vec3 local_position) {
    if ((flags_and_noise.x & 4u) == 0u || clip_micro_noise_earth.z <= 0.0) return color;
    float steps = float(max(flags_and_noise.y, 1u));
    // Frozen's flat_shaded.frag derives this normal from the interpolated raw
    // position, rather than the packed face-normal metadata. Keep the raw
    // position contract so the quantized noise coordinate matches source DH.
    vec3 vertex_normal = normalize(cross(dFdy(v_source_position), dFdx(v_source_position)));
    vec3 fixed_position = local_position + vertex_normal * 0.001;
    vec3 quantized = floor(fixed_position * steps) / steps;
    float random_value = dh_rand(quantized);
    float noise_amplification = clip_micro_noise_earth.z * 0.01;
    float luminance = (color.r + color.g + color.b) / 3.0;
    noise_amplification = (1.0 - pow(luminance * 2.0 - 1.0, 2.0)) * noise_amplification;
    noise_amplification *= alpha;
    random_value = random_value * 2.0 * noise_amplification - noise_amplification;
    vec3 noisy = clamp(color + (1.0 - color) * random_value, 0.0, 1.0);
    if (flags_and_noise.z != 0u) {
        float distance_factor = min(view_distance / float(flags_and_noise.z), 1.0);
        noisy = mix(noisy, color, distance_factor);
    }
    return noisy;
}

void main() {
    float dh_fade = dh_fragment_fade();
    // The matching vertex shader applied the copied lightmap before
    // interpolation, preserving Frozen DH's standard.vert contract.
    vec3 material_color = v_color.rgb;
    float distance_to_camera = distance(v_world_position,
        column_origin_and_world_y.xyz - model_offset_and_reserved.xyz);
    // Apply copied DH fog in the forward material. Frozen's vanilla DH path
    // has no vanilla-fog fallback; the dedicated DH fog pass is disabled
    // together with this semantic block.
    float dh_fog = dh_fog_factor(v_world_position);
    float fog = dh_fog >= 0.0 ? dh_fog : 0.0;
    // Frozen's flat_shaded.frag uses the fade only for coverage discard;
    // it preserves the source vertex alpha for translucent composition.
    if ((flags_and_noise.x & 2u) != 0u) {
        int dither_x = int(mod(gl_FragCoord.x, 4.0));
        int dither_y = (flags_and_noise.w & 512u) != 0u
            ? int(mod(gl_FragCoord.y, 4.0))
            : int(mod(-gl_FragCoord.y, 4.0));
        int dither_index = dither_y * 4 + dither_x;
        float dither_threshold = (float[16](0.0, 8.0, 2.0, 10.0,
            12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0,
            15.0, 7.0, 13.0, 5.0)[dither_index] / 16.0) + 0.001;
        if (dh_fade <= dither_threshold) discard;
    } else if (dh_fade <= 0.0) {
        discard;
    }
    // Graphics-audit-only material probes. They intentionally run after the
    // source fade/discard test so coverage, culling, depth, and blend state
    // remain unchanged while color provenance is isolated. Normal frames
    // leave this private flag lane clear.
    if ((flags_and_noise.w & 32u) != 0u && v_material == 12u) {
        out_color = vec4(0.0, 1.0, 0.0, v_color.a);
        return;
    }
    if ((flags_and_noise.w & 64u) != 0u) {
        out_color = vec4(v_light_color, v_color.a);
        return;
    }
    if ((flags_and_noise.w & 16u) != 0u) {
        out_color = vec4(v_unlit_color, v_color.a);
        return;
    }
    vec3 noisy_material = dh_apply_noise(material_color, v_color.a, distance_to_camera, v_source_position);
    vec3 shaded = mix(noisy_material, fog_color_and_alpha.rgb, fog);
    out_color = vec4(shaded, v_color.a);
}
