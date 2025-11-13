#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vNormal;

// Uniforms
uniform sampler2D uTexture;
uniform vec3 uSunDir;
uniform float uSkyBrightness;

void main() {
    // Sample texture
    vec4 texColor = texture2D(uTexture, vTexCoord);
    
    // Get albedo (base color)
    vec3 albedo = vColor.rgb * texColor.rgb;
    
    // Basic Lambert lighting (N·L)
    float NdotL = max(dot(normalize(vNormal), uSunDir), 0.0);
    vec3 lighting = vec3(0.3) + vec3(0.7) * NdotL * uSkyBrightness;
    
    // Apply lighting to albedo
    vec3 color = albedo * lighting;
    gl_FragColor = vec4(color, texColor.a);
}
