#version 450
layout(set = 0, binding = 0) uniform texture2D DhColorTexture;
layout(set = 0, binding = 1) uniform sampler DhColorSampler;
layout(set = 0, binding = 2) uniform texture2D DhDepthTexture;
layout(set = 0, binding = 3) uniform sampler DhDepthSampler;
layout(set = 0, binding = 4) uniform texture2D VanillaColorTexture;
layout(set = 0, binding = 5) uniform sampler VanillaColorSampler;
layout(set = 0, binding = 6) uniform texture2D VanillaDepthTexture;
layout(set = 0, binding = 7) uniform sampler VanillaDepthSampler;
layout(set = 0, binding = 9) uniform texture2D DhSsaoTexture;
layout(set = 0, binding = 10) uniform sampler DhSsaoSampler;
layout(set = 0, binding = 8, std140) uniform DistantHorizonsDirectFog {
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

float dh_fog_curve(float value, float start, float length, float minimum, float range, float density, float falloff) {
    if (falloff < 0.5) {
        return minimum + range * clamp((value - start) / max(length, 0.0001), 0.0, 1.0);
    }
    float x = max((value - start) / max(length, 0.0001), 0.0) * density;
    float attenuation = falloff < 1.5 ? exp(-x) : exp(-x * x);
    return minimum + range - range * attenuation;
}

float dh_fog_factor(vec3 world_position, vec3 camera_position, float absolute_camera_y) {
    if (dh_fog_parameters[4].x < 0.5) return -1.0;
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
        float height_position = world_position.y;
        float direction = dh_fog_parameters[3].w;
        if (mod(direction, 2.0) < 0.5) {
            // The inverse combined matrix returns view-space coordinates, but
            // DH's height-base rule uses the absolute camera block Y. Keep
            // those coordinate spaces separate: camera_position remains the
            // view origin for horizontal/spherical distance, while the
            // copied camera uniform supplies the source height reference.
            height_position -= dh_fog_parameters[1].z - absolute_camera_y;
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

float dh_ssao_value(vec2 uv, float center_depth) {
    if (ssao_parameters0.x < 0.5) return 1.0;
    int radius = clamp(int(ssao_parameters1.w + 0.5), 0, 3);
    if (radius == 0) {
        return textureLod(sampler2D(DhSsaoTexture, DhSsaoSampler), uv, 0.0).r;
    }
    vec2 pixel_size = 1.0 / vec2(textureSize(sampler2D(DhSsaoTexture, DhSsaoSampler), 0));
    float sigma = 1.6;
    float accum = 0.0;
    float total = 0.0;
    for (int y = -3; y <= 3; y++) {
        if (abs(y) > radius) continue;
        for (int x = -3; x <= 3; x++) {
            if (abs(x) > radius) continue;
            vec2 sample_uv = uv + vec2(x, y) * pixel_size;
            float spatial = exp(-float(x * x + y * y) / (2.0 * sigma * sigma));
            float sample_depth = textureLod(sampler2D(DhDepthTexture, DhDepthSampler), sample_uv, 0.0).r;
            float depth_weight = exp(-abs(sample_depth - center_depth) * 48.0);
            float weight = spatial * depth_weight;
            accum += weight * textureLod(sampler2D(DhSsaoTexture, DhSsaoSampler), sample_uv, 0.0).r;
            total += weight;
        }
    }
    return total > 1.0e-4 ? accum / total : 1.0;
}

void main() {
    vec4 dh_color = texture(sampler2D(DhColorTexture, DhColorSampler), v_uv);
    float depth = texture(sampler2D(DhDepthTexture, DhDepthSampler), v_uv).r;
    // Frozen's DH fade shader uses an exact white color as its clear/unwritten
    // sentinel.  Keep that distinction after replacing the sentinel with the
    // vanilla snapshot: an uncovered sky pixel must not receive DH fog from
    // the reconstructed far-plane depth.
    bool dh_clear_color = all(equal(dh_color, vec4(1.0)));
    // The private target is cleared transparent; its depth clear value is a
    // backend detail and cannot be used as the coverage test.  Alpha is the
    // semantic write marker for this color attachment.
    bool dh_has_coverage = !dh_clear_color && dh_color.a > 0.0;
    // Frozen runs SSAO after opaque DH and before the transparent/fog stages.
    // The private color alpha is the copied pass coverage marker, so preserve
    // translucent/water layers while applying AO to opaque DH pixels.
    if (dh_has_coverage && dh_color.a >= 0.999 && ssao_parameters0.x >= 0.5) {
        dh_color.rgb *= dh_ssao_value(v_uv, depth);
    }
    // The audit-only depth-test compositor writes the private DH depth into
    // the normal Vulkan depth test. The pipeline leaves depth writes off, so
    // a nearer vanilla fragment remains authoritative for later passes. A
    // separate negative sentinel can invert the value for a capture-only
    // projection/depth-convention probe; the production fog block never uses
    // negative DH parameters.
    float comparison_depth = depth;
    if (dh_fog_parameters[4].z < -3.5 && dh_fog_parameters[4].z > -4.5) {
        comparison_depth = 1.0 - depth;
    }
    gl_FragDepth = comparison_depth;
    // Audit-only negative enable sentinel from the Rust-owned compositor
    // boundary. It never occurs in the copied DH fog contract.
    if (dh_fog_parameters[4].x < -0.5) {
        out_color = vec4(depth, depth, depth, 1.0);
        return;
    }
    // Audit-only sentinel: expose the private DH color attachment before
    // fullscreen fog/depth reconstruction. This separates raster coverage
    // from compositor UV or depth-coordinate errors and is never supplied by
    // the production fog contract.
    if (dh_fog_parameters[4].z < -2.5 && dh_fog_parameters[4].z > -3.5) {
        out_color = dh_color;
        return;
    }
    // Capture-only coverage probe for the ordinary no-fade route. The same
    // sentinel is also understood by the later vanilla-fade shader, but a
    // NONE fade policy never executes that pass. Resolve it here as a full
    // red/black mask so the retained frame proves which pixels came from the
    // private DH target even when vanilla terrain and DH fog are disabled.
    // The opaque alpha deliberately lets the subsequent sparse apply replace
    // every pixel for this diagnostic frame; production fog parameters never
    // contain this negative vertical-scale sentinel.
    if (dh_fog_parameters[4].w < -4.5 && dh_fog_parameters[4].w > -5.5) {
        out_color = vec4(dh_has_coverage ? 1.0 : 0.0, 0.0, 0.0, 1.0);
        return;
    }
    if (depth >= 1.0 && dh_color.a <= 0.0) discard;
    // The fullscreen vertex contract flips v_uv to address the Vulkan image
    // row origin. Depth reconstruction must use the original clip-space Y,
    // just as the deferred terrain fog path does, or the copied DH fog ramp
    // is evaluated at the wrong view-space height and distance. The source
    // FogShader receives the unflipped screen-quad coordinate, while this
    // Rust-owned sampler receives the Vulkan-oriented coordinate.
    vec2 reconstruction_uv = v_uv;
#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y
    reconstruction_uv.y = 1.0 - reconstruction_uv.y;
#endif
    vec4 clip = vec4(reconstruction_uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = inverse_combined_matrix * clip;
    // Frozen's FogShader divides by the signed inverse-MVP w. Preserve that
    // projective convention while still avoiding a zero divide for cleared or
    // malformed depth samples; using abs(w) would mirror points behind the
    // camera and change the copied fog distance contract.
    float world_w = abs(world.w) > 0.000001
        ? world.w
        : (world.w < 0.0 ? -0.000001 : 0.000001);
    world.xyz /= world_w;
    // Audit-only reconstruction sentinel. Encode the copied source view-space
    // position into RGB so a retained capture can distinguish an inverse
    // matrix/depth convention error from a fog-curve error. Production fog
    // never supplies a negative vertical-scale sentinel.
    if (dh_fog_parameters[4].w < -0.5 && dh_fog_parameters[4].w > -1.5) {
        vec3 encoded_position = clamp(world.xyz / 256.0 + vec3(0.5), 0.0, 1.0);
        out_color = vec4(encoded_position, 1.0);
        return;
    }
    // The low two bits carry Frozen's NONE/SINGLE/DOUBLE vanilla transition;
    // bit 2 carries the independent DH far-clip fade policy. Both use the
    // copied vanilla color snapshot, never the acquired frame target.
    float fade_policy = fade_parameters.z;
    float fade_mode = mod(fade_policy, 4.0);
    bool far_clip_fade = fade_policy >= 4.0;
    vec4 vanilla_color = vec4(0.0);
    if (fade_mode >= 0.5 || far_clip_fade) {
        vanilla_color = texture(sampler2D(VanillaColorTexture, VanillaColorSampler), v_uv);
    }
	// Capture-only source snapshots for separating transition input from fade
	// distance. Normal copied DH fog parameters never use negative vertical
	// scale values.
	if (dh_fog_parameters[4].w < -2.5 && dh_fog_parameters[4].w > -3.5) {
		out_color = vec4(vanilla_color.rgb, 1.0);
		return;
	}
    if (far_clip_fade) {
        // RenderUtil.getFarClipPlaneDistanceInBlocks() is
        // (lodChunkDistance * 16 + regionWidth) * 2. The copied fog block
        // carries the same lod block distance in lane [1].y.
        float far_clip_distance = (dh_fog_parameters[1].y + 64.0) * 2.0;
        float far_fade = smoothstep(
            far_clip_distance * 0.9,
            far_clip_distance * 0.5,
            length(world.xzy));
        // Frozen's DH fade shader mixes MC color with the private DH color
        // before DH fog is applied. Recompute that ordering in the single
        // Rust-owned compositor so the later fog operation sees the same
        // source color.
        if (!dh_has_coverage) {
            dh_color = vanilla_color;
        } else {
            dh_color = mix(vanilla_color, dh_color, far_fade);
        }
    }
    // Frozen does not invoke FogRenderer at all when enableDhFog is false.
    // In particular, its translucent-alpha floor belongs to that skipped fog
    // pass; applying the floor after dh_fog_factor reports "disabled" washes
    // water toward the fog colour even though the source renderer leaves the
    // private DH colour unchanged. Far-clip fade and SSAO are independent and
    // have already been applied above, so preserve their result here.
    if (dh_fog_parameters[4].x < 0.5) {
        out_color = dh_color;
        return;
    }
    // DH's inverse model-view-projection returns view-space coordinates. The
    // source fog shader measures from the view origin, while the copied frame
    // still carries the absolute camera for the height-fog base-height rule.
    // Do not translate this position into absolute world space.
    float dh_fog = dh_has_coverage
        ? dh_fog_factor(world.xyz, vec3(0.0), camera_position.y)
        : 0.0;
    float fog = dh_fog >= 0.0 ? dh_fog : 0.0;
    // Frozen's FogShader preserves partial DH coverage when it raises fog
    // alpha: FogApplyShader then blends that fog over the DH color before the
    // final replace pass. Match that semantic rule here so water and other
    // translucent LODs cannot become less fogged merely because their fog
    // curve is below their copied source alpha.
    if (dh_color.a > 0.0 && dh_color.a < 1.0) {
        fog = max(fog, dh_color.a);
    }
    // Audit-only sentinel: expose the copied DH fog factor as grayscale so a
    // capture can distinguish a saturated fog ramp from missing private
    // geometry or an incorrect color/depth attachment. The sentinel is
    // supplied only by the Rust-owned diagnostic boundary below.
	if (dh_fog_parameters[4].w < -1.5 && dh_fog_parameters[4].w > -2.5) {
        out_color = vec4(fog, fog, fog, 1.0);
        return;
    }
    // FogApplyShader source-alpha blends the fog texture over DH's color
    // attachment. Its separate alpha factors are ONE and ONE_MINUS_SRC_ALPHA,
    // so fog also raises partial/depth-only DH coverage. Preserve that result
    // in the resolved image consumed by apply and later vanilla-fade passes.
    float resolved_alpha = fog + dh_color.a * (1.0 - fog);
    out_color = vec4(mix(dh_color.rgb, fog_color_and_alpha.rgb, fog), resolved_alpha);
}
