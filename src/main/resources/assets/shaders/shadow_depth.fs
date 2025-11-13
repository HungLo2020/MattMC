#version 120

// Simple fragment shader for shadow map rendering
// We don't output any color, just let the depth buffer write

void main() {
    // Depth is automatically written, no fragment color needed
    // But we need to output something for OpenGL to accept this shader
    gl_FragColor = vec4(1.0);
}
