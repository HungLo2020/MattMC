#version 450
layout(set = 0, binding = 0) uniform texture2D InTexture;
layout(set = 0, binding = 1) uniform sampler InSampler;
layout(set = 0, binding = 2, std140) uniform BlitConfig { vec4 ColorModulate; };
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    out_color = texture(sampler2D(InTexture, InSampler), v_uv) * ColorModulate;
}
