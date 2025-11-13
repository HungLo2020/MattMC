#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vNormal;
varying vec3 vWorldPos;
varying float vFogFactor;

// Uniforms
uniform sampler2D uTexture;
uniform vec3 uSunDir;        // Normalized sun direction vector
uniform vec3 uSunColor;      // Sun color
uniform vec3 uAmbientSky;    // Sky ambient color
uniform vec3 uAmbientBlock;  // Block light ambient color
uniform float uGamma;        // Gamma value (typically 2.2)
uniform vec3 uFogColor;      // Fog color
uniform float uSkyBrightness; // Sky brightness multiplier (0.0-1.0) for day/night cycle

void main() {
    // Sample texture
    vec4 texColor = texture2D(uTexture, vTexCoord);
    
    // Get albedo (no color space conversion needed)
    vec3 albedo = vColor.rgb * texColor.rgb;
    
    // Calculate Lambert diffuse from sun (N·L)
    float NdotL = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
    
    // Apply sun diffuse lighting
    vec3 sunDiffuse = uSunColor * NdotL * uSkyBrightness;
    
    // Combine sky lighting (ambient + sun)
    vec3 skyLighting = uAmbientSky * uSkyBrightness + sunDiffuse;
    
    // Block light adds on top of sky lighting
    vec3 blockLighting = uAmbientBlock;
    
    // Combine lighting: use max for base, then add block light contribution
    vec3 lighting = max(skyLighting, blockLighting);
    
    // Add extra block light contribution to make torches more visible
    lighting += blockLighting * 0.8;
    
    // Apply lighting to albedo
    vec3 litColor = albedo * lighting;
    
    // Apply fog
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);
    
    // Output final color (no gamma correction)
    gl_FragColor = vec4(finalColor, vColor.a * texColor.a);
}
