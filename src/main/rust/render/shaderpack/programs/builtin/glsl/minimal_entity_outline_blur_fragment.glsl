#version 450
layout(set = 0, binding = 0) uniform texture2D InTexture;
layout(set = 0, binding = 1) uniform sampler InSampler;
layout(set = 0, binding = 2, std140) uniform BlurConfig { vec2 BlurDir; float Radius; };
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec2 one_texel = 1.0 / vec2(textureSize(sampler2D(InTexture, InSampler), 0));
    vec2 step_value = one_texel * BlurDir;
    float radius = max(Radius, 0.0);
    vec4 blurred = vec4(0.0);
    for (float a = -radius + 0.5; a <= radius; a += 2.0) {
        blurred += texture(sampler2D(InTexture, InSampler), v_uv + step_value * a);
    }
    blurred += texture(sampler2D(InTexture, InSampler), v_uv + step_value * radius) * 0.5;
    // Vanilla entity_outline_box_blur averages color, not coverage. Alpha is
    // accumulated and clamped by the UNORM attachment on each filter pass.
    out_color = vec4((blurred / (radius + 0.5)).rgb, blurred.a);
}
