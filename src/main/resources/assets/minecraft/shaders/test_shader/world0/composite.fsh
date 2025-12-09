#version 150

// Test composite shader for overworld (world0)

uniform sampler2D colortex0;

in vec2 texcoord;

out vec4 FragColor;

void main() {
    vec4 color = texture(colortex0, texcoord);
    FragColor = color;
}
