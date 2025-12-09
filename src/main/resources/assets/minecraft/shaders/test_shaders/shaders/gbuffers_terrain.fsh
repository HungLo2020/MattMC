#version 330 core

// Test shader pack - basic terrain fragment shader
// This is a minimal shader for testing the shader pack discovery system

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

uniform sampler2D Sampler0;

void main() {
    vec4 texColor = texture(Sampler0, texCoord);
    fragColor = texColor * vertexColor;
}
