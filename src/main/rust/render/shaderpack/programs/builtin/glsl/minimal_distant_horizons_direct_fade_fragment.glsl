#version 450
layout(set = 0, binding = 0) uniform texture2D DhResolvedColorTexture;
layout(set = 0, binding = 1) uniform sampler DhResolvedColorSampler;
layout(set = 0, binding = 2) uniform texture2D DhDepthTexture;
layout(set = 0, binding = 3) uniform sampler DhDepthSampler;
layout(set = 0, binding = 4) uniform texture2D VanillaColorTexture;
layout(set = 0, binding = 5) uniform sampler VanillaColorSampler;
layout(set = 0, binding = 6) uniform texture2D VanillaDepthTexture;
layout(set = 0, binding = 7) uniform sampler VanillaDepthSampler;
layout(set = 0, binding = 8, std140) uniform DistantHorizonsDirectFade {
    mat4 combined_matrix;
    mat4 inverse_combined_matrix;
    mat4 inverse_vanilla_matrix;
    vec4 camera_position;
    vec4 fog_color_and_alpha;
    vec4 fog_ranges;
    vec4 dh_fog_parameters[5];
    vec4 fade_parameters;
    vec4 ssao_parameters0;
    vec4 ssao_parameters1;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;

vec3 reconstruct_position(mat4 inverse_matrix, vec2 uv, float depth) {
    vec2 reconstruction_uv = uv;
#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y
    reconstruction_uv.y = 1.0 - reconstruction_uv.y;
#endif
    vec4 clip = vec4(reconstruction_uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 position = inverse_matrix * clip;
    float position_w = abs(position.w) > 0.000001
        ? position.w : (position.w < 0.0 ? -0.000001 : 0.000001);
    return position.xyz / position_w;
}

void main() {
    vec4 combined_color = texture(sampler2D(VanillaColorTexture, VanillaColorSampler), v_uv);
    vec4 dh_color = texture(sampler2D(DhResolvedColorTexture, DhResolvedColorSampler), v_uv);
    float dh_depth = texture(sampler2D(DhDepthTexture, DhDepthSampler), v_uv).r;
    // The resolved attachment carries the semantic coverage marker in alpha.
    // A private depth write alone is not visible DH color: treating it as
    // coverage lets the fog-colored clear value replace valid vanilla terrain
    // at the fade boundary.
    bool dh_has_coverage = dh_color.a > 0.0;

    // Audit-only source snapshot. Production fog never supplies this negative
    // vertical-scale sentinel.
    if (dh_fog_parameters[4].w < -2.5 && dh_fog_parameters[4].w > -3.5) {
        out_color = vec4(combined_color.rgb, 1.0);
        return;
    }

    // Capture-only coverage probe: red is resolved DH coverage, green is
    // vanilla depth coverage at this fade boundary, and blue preserves the
    // sampled vanilla depth. Production fog parameters never use this
    // negative vertical-scale sentinel.
    if (dh_fog_parameters[4].w < -4.5 && dh_fog_parameters[4].w > -5.5) {
        float probe_vanilla_depth = texture(
            sampler2D(VanillaDepthTexture, VanillaDepthSampler), v_uv
        ).r;
        out_color = vec4(
            dh_has_coverage ? 1.0 : 0.0,
            probe_vanilla_depth < 1.0 ? 1.0 : 0.0,
            probe_vanilla_depth,
            1.0
        );
        return;
    }

    vec3 dh_position = reconstruct_position(inverse_combined_matrix, v_uv, dh_depth);
    // Capture-only classification for the high-altitude cloud guard. Red is
    // the guard predicate, green is resolved DH coverage, and blue is vanilla
    // depth coverage at this exact fade boundary. Production fog semantics
    // never contain this negative vertical-scale sentinel.
    if (dh_fog_parameters[4].w < -5.5 && dh_fog_parameters[4].w > -6.5) {
        float probe_vanilla_depth = texture(
            sampler2D(VanillaDepthTexture, VanillaDepthSampler), v_uv
        ).r;
        out_color = vec4(
            dh_position.y > fade_parameters.w ? 1.0 : 0.0,
            dh_has_coverage ? 1.0 : 0.0,
            probe_vanilla_depth < 1.0 ? 1.0 : 0.0,
            1.0
        );
        return;
    }
    // Frozen's LOD-only debug mode invokes this shader at both vanilla fade
    // boundaries and immediately replaces the combined Minecraft image with
    // DH's private color texture. The CPU contract reserves fade mode 3 for
    // that source behavior; normal NONE/SINGLE/DOUBLE modes are 0/1/2.
    if (fade_parameters.z > 2.5 && fade_parameters.z < 3.5) {
        out_color = dh_color;
        return;
    }
    // Frozen only substitutes Minecraft color for an unwritten DH pixel in
    // the ordinary fade modes. LOD-only returns the private DH target exactly,
    // including its fog-colored, zero-alpha clear pixels.
    if (!dh_has_coverage) dh_color = combined_color;
    if (dh_position.y > fade_parameters.w) {
        out_color = vec4(combined_color.rgb, 0.0);
        return;
    }
    float vanilla_depth = texture(sampler2D(VanillaDepthTexture, VanillaDepthSampler), v_uv).r;
    if (vanilla_depth >= 1.0) {
        out_color = vec4(combined_color.rgb, 0.0);
        return;
    }
    vec3 vanilla_position = reconstruct_position(inverse_vanilla_matrix, v_uv, vanilla_depth);
    float vanilla_distance = length(vanilla_position.xzy);
    float fade = smoothstep(fade_parameters.x, fade_parameters.y, vanilla_distance);
    if (dh_fog_parameters[4].w < -3.5) {
        out_color = vec4(fade, fade, fade, 1.0);
        return;
    }
    out_color = mix(combined_color, dh_color, fade);
    out_color.a = 1.0;
}
