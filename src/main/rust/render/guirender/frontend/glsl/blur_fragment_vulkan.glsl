#version 450
layout(set = 0, binding = 0) uniform texture2D Source;
layout(set = 0, binding = 1) uniform sampler SourceSampler;
layout(set = 0, binding = 2, std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec2 texel = 1.0 / vec2(textureSize(sampler2D(Source, SourceSampler), 0));
    // Source and destination have the same explicit framebuffer extent.
    // Fragment coordinates preserve copied-image orientation under the GAL's
    // negative-height Vulkan viewport; interpolated clip-space UVs do not.
    vec2 source_uv = gl_FragCoord.xy * texel;
    vec2 sample_step = texel * BlurDir;
    float actual_radius = max(round(Radius), 0.0);
    vec4 blurred = vec4(0.0);
    for (float a = -actual_radius + 0.5; a <= actual_radius; a += 2.0) {
        blurred += texture(sampler2D(Source, SourceSampler), source_uv + sample_step * a);
    }
    blurred += texture(sampler2D(Source, SourceSampler), source_uv + sample_step * actual_radius) * 0.5;
    out_color = blurred / (actual_radius + 0.5);
}
