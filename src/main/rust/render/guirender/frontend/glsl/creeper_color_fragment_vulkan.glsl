#version 450
layout(set = 0, binding = 0) uniform texture2D Source;
layout(set = 0, binding = 1) uniform sampler SourceSampler;
layout(set = 0, binding = 2, std140) uniform ColorConfig { vec3 RedMatrix; vec3 GreenMatrix; vec3 BlueMatrix; };
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec3 input_color = texture(sampler2D(Source, SourceSampler), v_uv).rgb;
    vec3 color = vec3(dot(input_color, RedMatrix), dot(input_color, GreenMatrix), dot(input_color, BlueMatrix));
    float luma = dot(color, vec3(0.3, 0.59, 0.11));
    color = (color - luma) * 1.8 + luma;
    out_color = vec4(color, 1.0);
}
