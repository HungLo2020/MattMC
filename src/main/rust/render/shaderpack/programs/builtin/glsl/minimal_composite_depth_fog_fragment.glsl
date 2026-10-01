#version 450
layout(set = 0, binding = 0) uniform texture2D Tex0;
layout(set = 0, binding = 1) uniform texture2D NormalTex;
layout(set = 0, binding = 4) uniform texture2D MainDepthTex;
layout(set = 0, binding = 5) uniform sampler Samp0;
layout(set = 0, binding = 6, std430) readonly buffer ShaderCompositeUniforms {
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 color_grade_params;
    mat4 projection_inverse;
    vec4 fog_color_and_environmental_start;
    vec4 fog_ranges;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv);
    float device_depth = texture(sampler2D(MainDepthTex, Samp0), v_uv).r;
    // `v_uv` deliberately addresses Vulkan attachments with the opposite row
    // origin from the fullscreen raster position.  It is therefore correct
    // for sampling, but not for rebuilding the game's view-space ray: that
    // reconstruction must use the unflipped clip-space Y coordinate.
    vec2 reconstruction_uv = v_uv;
#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y
    reconstruction_uv.y = 1.0 - reconstruction_uv.y;
#endif
    // Deferred work preserves Frozen's interpolated vertex fog factor in the
    // normal alpha channel. Do not reconstruct a different distance from
    // depth: perspective reconstruction changes the fog ramp over terrain
    // and translucent triangles.
    float fog_factor = texture(sampler2D(NormalTex, Samp0), v_uv).a;
    // `color.a` is not a fog-opacity channel. Sodium's compact terrain
    // vertices use it for separate ambient occlusion, and the deferred
    // G-buffer must preserve that semantic for lighting. Frozen resolves fog
    // with the copied fog-color alpha instead.
    float fog_alpha = clamp(fog_factor * fog_ranges.w, 0.0, 1.0);
    out_color = vec4(mix(color.rgb, fog_color_and_environmental_start.rgb, fog_alpha), color.a);
}
