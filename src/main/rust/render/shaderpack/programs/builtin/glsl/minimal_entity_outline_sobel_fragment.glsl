#version 450
layout(set = 0, binding = 0) uniform texture2D InTexture;
layout(set = 0, binding = 1) uniform sampler InSampler;
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec2 one_texel = 1.0 / vec2(textureSize(sampler2D(InTexture, InSampler), 0));
    vec4 center = texture(sampler2D(InTexture, InSampler), v_uv);
    vec4 left = texture(sampler2D(InTexture, InSampler), v_uv - vec2(one_texel.x, 0.0));
    vec4 right = texture(sampler2D(InTexture, InSampler), v_uv + vec2(one_texel.x, 0.0));
    vec4 up = texture(sampler2D(InTexture, InSampler), v_uv - vec2(0.0, one_texel.y));
    vec4 down = texture(sampler2D(InTexture, InSampler), v_uv + vec2(0.0, one_texel.y));
    float total = clamp(abs(center.a - left.a) + abs(center.a - right.a) + abs(center.a - up.a) + abs(center.a - down.a), 0.0, 1.0);
    vec3 color = center.rgb * center.a + left.rgb * left.a + right.rgb * right.a + up.rgb * up.a + down.rgb * down.a;
    out_color = vec4(color * 0.2, total);
}
