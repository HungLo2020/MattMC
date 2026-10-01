#version 450
layout(set = 0, binding = 2) uniform texture2D OutlineTexture;
layout(set = 0, binding = 3) uniform sampler OutlineSampler;
layout(location = 0) in vec4 v_outline_color;
layout(location = 1) in vec2 v_outline_uv;
layout(location = 0) out vec4 out_outline_color;
void main() {
    if (texture(sampler2D(OutlineTexture, OutlineSampler), v_outline_uv).a == 0.0) discard;
    out_outline_color = v_outline_color;
}
