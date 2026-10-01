#version 450
layout(set = 0, binding = 0) uniform texture2D DhResolvedColorTexture;
layout(set = 0, binding = 1) uniform sampler DhResolvedColorSampler;
layout(set = 0, binding = 2) uniform texture2D DhDepthTexture;
layout(set = 0, binding = 3) uniform sampler DhDepthSampler;
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 dh_color = texture(sampler2D(DhResolvedColorTexture, DhResolvedColorSampler), v_uv);
    float dh_depth = texture(sampler2D(DhDepthTexture, DhDepthSampler), v_uv).r;
    if (dh_depth >= 1.0 && dh_color.a <= 0.0) discard;
    out_color = dh_color;
}
