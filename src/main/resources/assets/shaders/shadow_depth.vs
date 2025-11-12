#version 120

// Simple vertex shader for shadow depth rendering
// Only transforms vertices to light space, no lighting calculations needed

void main() {
    // Transform vertex to light clip space
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
}
