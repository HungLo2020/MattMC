#version 330 core

#include "/lib/common.glsl"

out vec4 fragColor;

void main() {
    vec3 lighting = calculateLighting(vec3(0.0, 1.0, 0.0));
    fragColor = vec4(lighting, 1.0);
}
