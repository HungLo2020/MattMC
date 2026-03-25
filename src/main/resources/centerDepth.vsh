#version 150 core

in vec3 iris_Position;

void main() {
    // This pass renders a fullscreen sample quad. Use a fixed NDC transform
    // to avoid backend-specific uniform-block requirements in Vulkan GLSL.
    vec2 ndc = iris_Position.xy * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);
}
