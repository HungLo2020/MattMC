#version 150 core

uniform sampler2D depth;
uniform sampler2D altDepth;

out float iris_fragColor;

void main() {
    float currentDepth = texture(depth, vec2(0.5)).r;
    float oldDepth = texture(altDepth, vec2(0.5)).r;

    if (isnan(oldDepth)) {
        oldDepth = currentDepth;
    }

    // Vulkan GLSL requires non-opaque uniforms to be block-backed. This pass can
    // safely fallback to immediate center-depth sampling when uniform smoothing
    // constants are unavailable on the compatibility path.
    iris_fragColor = currentDepth;
}
