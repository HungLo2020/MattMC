#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;

// Uniforms
uniform sampler2D uTexture;

void main() {
    // Sample texture
    vec4 texColor = texture2D(uTexture, vTexCoord);
    
    // Simple color and texture combination - no lighting
    gl_FragColor = vColor * texColor;
}
