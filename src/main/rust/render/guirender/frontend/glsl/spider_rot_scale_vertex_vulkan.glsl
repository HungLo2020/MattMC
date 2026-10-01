#version 450
const vec2 positions[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
layout(set = 0, binding = 3, std140) uniform SpiderConfig {
    vec2 InScale;
    vec2 InOffset;
    float InRotation;
    vec4 Scissor;
    vec4 Vignette;
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec2 v_scaled_uv;
void main() {
    vec2 position = positions[gl_VertexIndex];
    gl_Position = vec4(position, 0.0, 1.0);
    v_uv = position * 0.5 + 0.5;
    float radians_value = InRotation * 0.0174532925;
    float c = cos(radians_value);
    float s = sin(radians_value);
    vec2 rotated = vec2(v_uv.x * c - v_uv.y * s, v_uv.y * c + v_uv.x * s);
    v_scaled_uv = rotated * InScale + InOffset;
}
