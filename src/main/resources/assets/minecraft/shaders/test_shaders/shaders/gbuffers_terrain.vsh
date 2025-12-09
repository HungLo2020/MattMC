#version 330 core

// Test shader pack - basic terrain vertex shader
// This is a minimal shader for testing the shader pack discovery system

in vec3 vaPosition;
in vec2 vaUV0;
in vec4 vaColor;

out vec2 texCoord;
out vec4 vertexColor;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(vaPosition, 1.0);
    texCoord = vaUV0;
    vertexColor = vaColor;
}
