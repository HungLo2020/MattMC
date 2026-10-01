#version 450
layout(set = 0, binding = 0) uniform texture2D Source;
layout(set = 0, binding = 1) uniform sampler SourceSampler;
layout(set = 0, binding = 2, std140) uniform InvertConfig {
    float InverseAmount;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    // The copied image and destination share a framebuffer extent. Address
    // physical rows directly; clip-space UVs flip under a negative viewport.
    vec2 source_uv = gl_FragCoord.xy / vec2(textureSize(sampler2D(Source, SourceSampler), 0));
    vec4 source = texture(sampler2D(Source, SourceSampler), source_uv);
    vec4 inverted = vec4(1.0) - source;
    vec4 result = mix(source, inverted, clamp(InverseAmount, 0.0, 1.0));
    out_color = vec4(result.rgb, 1.0);
}
