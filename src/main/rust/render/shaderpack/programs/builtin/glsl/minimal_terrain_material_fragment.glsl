#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 3) in vec3 v_normal;
layout(location = 4) in vec2 v_light;
layout(location = 5) in vec3 v_world_position;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
layout(location = 8) flat in vec4 v_overlay_color;
layout(location = 9) in vec2 v_fog_distances;
layout(location = 10) flat in vec4 v_fog_color_and_environmental_start;
layout(location = 11) flat in vec4 v_fog_ranges;
layout(location = 12) flat in uint v_terrain_material_bits;
// These locations are implementation details. The shader-pack contract names
// the values terrain_lit_color, terrain_view_space_normal, and
// terrain_material_auxiliary.
layout(location = 0) out vec4 out_terrain_lit_color;
layout(location = 1) out vec4 out_terrain_view_space_normal;
layout(location = 2) out vec4 out_terrain_material_auxiliary;
layout(location = 3) out vec4 out_world_position;
// Frozen Java OpenGL reported GL_MAX_TEXTURE_LOD_BIAS = 15 for the paired
// baseline device. Sodium applies its negative counterpart only to materials
// whose compact material byte disables mipmaps. Preserve derivative-selected
// sampling; `textureLod(..., 0.0)` would incorrectly force the base mip.
const float FROZEN_MAX_TEXTURE_LOD_BIAS = 15.0;
// Keep the copied Frozen fog behavior intact even for disabled or degenerate
// ranges. In particular, `end <= start` is a hard transition, not a disabled
// fog range; only its explicit sentinel disables fog.
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
    // Keep the deferred Fabulous material path on the same compact Sodium
    // material contract as the direct path.  These bits are semantic mesh
    // data, not a backend sampler workaround: they control whether a sprite
    // may use its mip chain and which alpha/cull rule its fragment receives.
    bool use_mipmaps = (v_terrain_material_bits & 1u) != 0u;
    vec4 color = use_mipmaps
        ? texture(sampler2D(Tex0, Samp0), sample_uv)
        : texture(sampler2D(Tex0, Samp0), sample_uv, -FROZEN_MAX_TEXTURE_LOD_BIAS);
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        vec4 next_color = texture(sampler2D(Tex0, Samp0), next_uv);
        color = mix(color, next_color, clamp(v_material.z, 0.0, 1.0));
    }
    color *= v_color;
    if (v_overlay_color.a > 0.0) {
        color.rgb = mix(color.rgb, v_overlay_color.rgb, v_overlay_color.a);
    }
    uint alpha_cutoff_class = (v_terrain_material_bits >> 1u) & 3u;
    // Copied model geometry has no Sodium material bits. Its declared
    // material carries the cutoff independently in the native instance.
    bool model_cutout = (uint(v_material.w) & 32u) != 0u;
    float alpha_cutoff = model_cutout ? v_material.x
        : float[4](0.0, 0.1, 0.1, 1.0)[alpha_cutoff_class];
#ifdef VULKANIC_TERRAIN_FRAGMENT_DISCARD
    // Match Sodium: pass culling is explicit pipeline state, never a
    // material-dependent fragment-side back-face discard.
    if (color.a < max(v_material.x, alpha_cutoff)) {
        discard;
    }
#endif
    float environmental_fog = frozen_linear_fog_value(
        v_fog_distances.x,
        v_fog_color_and_environmental_start.w,
        v_fog_ranges.x
    );
    float render_distance_fog = frozen_linear_fog_value(
        v_fog_distances.y,
        v_fog_ranges.y,
        v_fog_ranges.z
    );
    vec3 n = normalize(v_normal) * 0.5 + 0.5;
    out_terrain_lit_color = color;
    // Keep Fog's copied per-vertex distances semantic through deferred work.
    // The normal alpha is otherwise unused by this admitted deferred path.
    out_terrain_view_space_normal = vec4(n, max(environmental_fog, render_distance_fog));
    // This deferred route only receives opaque/cutout terrain after its
    // material-specific discard. Sodium's compact vertex alpha may be baked
    // AO, so it is not an existence bit.  Carry explicit coverage separately
    // instead of allowing dark AO vertices to disappear in deferred lighting.
    out_terrain_material_auxiliary = vec4(v_material.x, v_light.x, v_light.y, 1.0);
    out_world_position = vec4(v_world_position, 1.0);
}
