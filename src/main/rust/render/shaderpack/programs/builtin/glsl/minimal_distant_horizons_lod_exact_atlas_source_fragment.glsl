#version 450
layout(set = 1, binding = 0) uniform texture2D TerrainAtlasColor;
layout(set = 1, binding = 1) uniform sampler TerrainAtlasSampler;
layout(set = 1, binding = 2) uniform texture2D LightmapTexture;
layout(set = 1, binding = 3) uniform sampler LightmapSampler;
layout(location = 0) in vec2 v_tile_uv;
layout(location = 1) flat in vec4 v_atlas_rect;
layout(location = 2) in vec4 v_color;
layout(location = 3) flat in uvec2 v_light;
layout(location = 4) flat in uint v_normal;
layout(location = 5) in vec3 v_world_position;
layout(location = 6) in vec3 v_source_position;
layout(location = 7) flat in uint v_material_flags;
layout(location = 9) in vec3 v_light_color;
layout(location = 0) out vec4 out_source_primary;

float dh_fragment_fade() {
    if ((flags_and_noise.w & 8u) != 0u) return 1.0;
    float distance_to_camera = distance(v_world_position,
        column_origin_and_world_y.xyz - model_offset_and_reserved.xyz);
    float dh_clip = clip_micro_noise_earth.x;
    return dh_clip > 0.0 ? smoothstep(dh_clip, dh_clip * 1.5, distance_to_camera) : 1.0;
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
    vec2 atlas_extent = vec2(textureSize(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), 0));
    vec2 texel = vec2(0.5) / atlas_extent;
    vec2 sprite_min = v_atlas_rect.xy + texel;
    vec2 sprite_max = v_atlas_rect.zw - texel;
    vec2 atlas_uv = mix(sprite_min, max(sprite_min, sprite_max), fract(v_tile_uv));
    // Capture-only probe: force the sprite sample to the copied base mip so
    // sparse atlas mip rows cannot masquerade as a material or alpha failure.
    vec4 atlas_color = (flags_and_noise.w & 256u) != 0u
        ? textureLod(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), atlas_uv, 0.0)
        : texture(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), atlas_uv);
    vec3 semantic_tint = (v_material_flags & 1u) != 0u ? v_color.rgb : vec3(1.0);
    if ((flags_and_noise.w & 128u) != 0u) {
        if (atlas_color.a * v_color.a * dh_fade < 0.1) discard;
        out_source_primary = vec4(atlas_color.rgb, atlas_color.a * v_color.a * dh_fade);
        return;
    }
    vec3 noisy_material = dh_apply_noise(atlas_color.rgb * semantic_tint * v_light_color, atlas_color.a * v_color.a * dh_fade, distance(v_world_position, column_origin_and_world_y.xyz - model_offset_and_reserved.xyz), v_source_position);
    vec4 color = vec4(noisy_material, atlas_color.a * v_color.a * dh_fade);
    if (color.a < 0.1) discard;
    out_source_primary = color;
}
