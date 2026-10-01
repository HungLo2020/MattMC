#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
layout(location = 8) flat in vec4 v_overlay_color;
layout(location = 9) in vec2 v_fog_distances;
layout(location = 10) flat in vec4 v_fog_color_and_environmental_start;
layout(location = 11) flat in vec4 v_fog_ranges;
layout(location = 12) flat in uint v_terrain_material_bits;
layout(location = 15) in vec4 v_back_color;
layout(location = 0) out vec4 out_color;
// Frozen Java OpenGL reported GL_MAX_TEXTURE_LOD_BIAS = 15 for the paired
// baseline device. Keep Sodium's derivative-selected negative-bias sample;
// forcing level zero is not equivalent for distant terrain.
const float FROZEN_MAX_TEXTURE_LOD_BIAS = 15.0;
const float FROZEN_FOG_DISABLED_SENTINEL_THRESHOLD = 1.0e12;
bool frozen_fog_range_disabled(float start, float end) {
    return max(abs(start), abs(end)) >= FROZEN_FOG_DISABLED_SENTINEL_THRESHOLD;
}
bool frozen_fog_distance_invalid(float distance) {
    return distance != distance || abs(distance) >= FROZEN_FOG_DISABLED_SENTINEL_THRESHOLD;
}
float frozen_linear_fog_value(float distance, float start, float end) {
    if (frozen_fog_distance_invalid(distance) || frozen_fog_range_disabled(start, end)) {
        return 0.0;
    }
    if (end <= start) {
        return distance > start ? 1.0 : 0.0;
    }
    if (distance <= start) {
        return 0.0;
    }
    if (distance >= end) {
        return 1.0;
    }
    return clamp((distance - start) / (end - start), 0.0, 1.0);
}
void main() {
    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;
    // Frozen Sodium's compact material byte controls both this exact choice
    // and alpha cutoff. A non-mipped material asks for a negative LOD bias
    // while retaining derivative-selected mip sampling.
#ifdef VULKANIC_MODEL_TRANSLUCENT_CUTOUT
    vec4 color = texture(sampler2D(Tex0, Samp0), sample_uv);
#else
    bool use_mipmaps = (v_terrain_material_bits & 1u) != 0u;
    vec4 color = use_mipmaps
        ? texture(sampler2D(Tex0, Samp0), sample_uv)
        : texture(sampler2D(Tex0, Samp0), sample_uv, -FROZEN_MAX_TEXTURE_LOD_BIAS);
#endif
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        vec4 next_color = texture(sampler2D(Tex0, Samp0), next_uv);
        color = mix(color, next_color, clamp(v_material.z, 0.0, 1.0));
    }
    bool per_face_lighting = (uint(v_material.w) & 64u) != 0u;
    color *= per_face_lighting && !gl_FrontFacing ? v_back_color : v_color;
    if (v_overlay_color.a > 0.0) {
        color.rgb = mix(color.rgb, v_overlay_color.rgb, v_overlay_color.a);
    }
#ifdef VULKANIC_MODEL_TRANSLUCENT_CUTOUT
    if (color.a < 0.1) discard;
#endif
    uint alpha_cutoff_class = (v_terrain_material_bits >> 1u) & 3u;
    // Copied model geometry has no Sodium material bits. Its declared
    // material carries the cutoff independently in the native instance.
    bool model_cutout = (uint(v_material.w) & 32u) != 0u;
    float alpha_cutoff = model_cutout ? v_material.x
        : float[4](0.0, 0.1, 0.1, 1.0)[alpha_cutoff_class];
#ifdef VULKANIC_TERRAIN_FRAGMENT_DISCARD
    // Frozen Sodium's `block_layer_*.fsh` applies only the compact material
    // alpha cutoff here. Cull state belongs to the explicit pass pipeline;
    // a fragment-side back-face discard would remove valid cutout geometry
    // (for example foliage planes) that the baseline still draws.
    if (alpha_cutoff > 0.0 && color.a < alpha_cutoff) {
        discard;
    }
#endif
    float fog_value = max(
        frozen_linear_fog_value(v_fog_distances.x, v_fog_color_and_environmental_start.w, v_fog_ranges.x),
        frozen_linear_fog_value(v_fog_distances.y, v_fog_ranges.y, v_fog_ranges.z)
    );
    float fog_alpha = clamp(fog_value * v_fog_ranges.w, 0.0, 1.0);
    color.rgb = mix(color.rgb, v_fog_color_and_environmental_start.rgb, fog_alpha);
    out_color = color;
}
