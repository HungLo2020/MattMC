#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DarkModeConfig {
    float Intensity;
    float HueShift;
    float Brightness;
    float Contrast;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    
    // Simple darkening: reduce brightness by multiplying RGB values
    vec3 darkenedColor = color.rgb * Brightness;
    
    // Mix between original and darkened based on intensity
    vec3 finalColor = mix(color.rgb, darkenedColor, Intensity);
    
    fragColor = vec4(finalColor, color.a);
}
