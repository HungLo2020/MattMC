#version 460

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;

layout(push_constant) uniform PushConstants {
    mat4 mvp;
} push_constants;

layout(location = 0) out vec3 frag_color;

void main() {
    gl_Position = push_constants.mvp * vec4(position, 1.0);
    frag_color = color;
}
