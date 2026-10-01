#version 450
layout(set = 0, binding = 0) uniform texture2D InTexture;
layout(set = 0, binding = 1) uniform texture2D BlurTexture;
layout(set = 0, binding = 2) uniform sampler SourceSampler;
layout(set = 0, binding = 3, std140) uniform SpiderConfig {
    vec2 InScale;
    vec2 InOffset;
    float InRotation;
    vec4 Scissor;
    vec4 Vignette;
};
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec2 v_scaled_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 scaled = texture(sampler2D(InTexture, SourceSampler), clamp(v_scaled_uv, 0.0, 1.0));
    vec4 blurred = texture(sampler2D(BlurTexture, SourceSampler), v_uv);
    vec4 result = scaled;
    if (v_scaled_uv.x < Scissor.x || v_scaled_uv.y < Scissor.y || v_scaled_uv.x > Scissor.z || v_scaled_uv.y > Scissor.w) result = blurred;
    if (v_scaled_uv.x < Vignette.x) result = mix(blurred, result, clamp((Scissor.x - v_scaled_uv.x) / (Scissor.x - Vignette.x), 0.0, 1.0));
    if (v_scaled_uv.y < Vignette.y) result = mix(blurred, result, clamp((Scissor.y - v_scaled_uv.y) / (Scissor.y - Vignette.y), 0.0, 1.0));
    if (v_scaled_uv.x > Vignette.z) result = mix(blurred, result, clamp((Scissor.z - v_scaled_uv.x) / (Scissor.z - Vignette.z), 0.0, 1.0));
    if (v_scaled_uv.y > Vignette.w) result = mix(blurred, result, clamp((Scissor.w - v_scaled_uv.y) / (Scissor.w - Vignette.w), 0.0, 1.0));
    out_color = vec4(result.rgb, 1.0);
}
