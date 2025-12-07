#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DarkModeConfig {
    float Intensity;        // 0.0 = off, 1.0 = full dark mode
    float HueShift;         // Slight hue adjustment
    float Brightness;       // Overall brightness adjustment
    float Contrast;         // Contrast adjustment
};

out vec4 fragColor;

// RGB to HSV conversion
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// HSV to RGB conversion
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 color = texture(InSampler, texCoord);
    
    if (Intensity <= 0.0) {
        fragColor = color;
        return;
    }
    
    // Convert to HSV for easier manipulation
    vec3 hsv = rgb2hsv(color.rgb);
    float hue = hsv.x;
    float sat = hsv.y;
    float val = hsv.z;
    
    // Determine if this is a UI element (high saturation/value) or game content
    float isUI = step(0.5, max(sat, val));
    
    // Dark mode transformations
    if (isUI > 0.5) {
        // UI elements: invert brightness, preserve hue
        val = 1.0 - val;
        val = val * Brightness;
        
        // Boost saturation slightly for better visibility
        sat = min(1.0, sat * 1.2);
        
        // Apply hue shift
        hue = mod(hue + HueShift, 1.0);
    } else {
        // Game content: slight darkening only
        val = val * 0.8;
    }
    
    // Convert back to RGB
    vec3 darkRGB = hsv2rgb(vec3(hue, sat, val));
    
    // Mix based on intensity
    vec3 finalRGB = mix(color.rgb, darkRGB, Intensity);
    
    // Apply contrast adjustment
    finalRGB = (finalRGB - 0.5) * Contrast + 0.5;
    
    fragColor = vec4(finalRGB, color.a);
}
