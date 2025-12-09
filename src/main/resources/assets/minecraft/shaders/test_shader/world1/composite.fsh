#version 150

// Test composite shader for end (world1)

uniform sampler2D colortex0;

in vec2 texcoord;

out vec4 FragColor;

void main() {
    vec4 color = texture(colortex0, texcoord);
    // Add purplish tint for end
    color.b *= 1.2;
    FragColor = color;
}
