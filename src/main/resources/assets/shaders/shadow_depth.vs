#version 120

// Simple vertex shader for shadow map rendering
// Only needs to output depth, no color or lighting

void main() {
    // Transform vertex to clip space
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
}
