#version 450
layout(set = 0, binding = 0) uniform texture2D Source;
layout(set = 0, binding = 1) uniform sampler SourceSampler;
layout(set = 0, binding = 2, std140) uniform BlitConfig { vec4 ColorModulate; };
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() { out_color = texture(sampler2D(Source, SourceSampler), v_uv) * ColorModulate; }
