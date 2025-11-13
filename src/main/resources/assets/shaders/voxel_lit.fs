#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vNormal;
varying vec3 vWorldPos;
varying float vFogFactor;
varying vec4 vShadowCoord;

// Uniforms
uniform sampler2D uTexture;
uniform sampler2D uShadowMap;
uniform vec3 uSunDir;        // Normalized sun direction vector
uniform vec3 uSunColor;      // Sun color
uniform vec3 uAmbientSky;    // Sky ambient color
uniform vec3 uAmbientBlock;  // Block light ambient color
uniform float uGamma;        // Gamma value (typically 2.2)
uniform vec3 uFogColor;      // Fog color
uniform float uSkyBrightness; // Sky brightness multiplier (0.0-1.0) for day/night cycle
uniform int uShadowsEnabled;  // Whether shadows are enabled

/**
 * Sample the shadow map to determine if this fragment is in shadow.
 * Returns 1.0 for lit, 0.0 for shadowed.
 */
float calculateShadow() {
    if (uShadowsEnabled == 0) {
        return 1.0; // No shadows
    }
    
    // Perspective divide
    vec3 projCoords = vShadowCoord.xyz / vShadowCoord.w;
    
    // Transform from [-1,1] to [0,1] range
    projCoords = projCoords * 0.5 + 0.5;
    
    // If outside shadow map bounds, assume lit
    if (projCoords.x < 0.0 || projCoords.x > 1.0 || 
        projCoords.y < 0.0 || projCoords.y > 1.0 ||
        projCoords.z > 1.0) {
        return 1.0;
    }
    
    float currentDepth = projCoords.z;
    
    // Adaptive bias to prevent shadow acne
    float bias = max(0.005 * (1.0 - dot(vNormal, uSunDir)), 0.001);
    
    // Simple shadow test
    float closestDepth = texture2D(uShadowMap, projCoords.xy).r;
    float shadow = currentDepth - bias > closestDepth ? 0.0 : 1.0;
    
    return shadow;
}

void main() {
    // Sample texture
    vec4 texColor = texture2D(uTexture, vTexCoord);
    
    // Get albedo (no color space conversion needed)
    vec3 albedo = vColor.rgb * texColor.rgb;
    
    // Calculate Lambert diffuse from sun (N·L)
    float NdotL = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
    
    // Calculate shadow factor
    float shadowFactor = calculateShadow();
    
    // Apply shadow to sun diffuse lighting
    vec3 sunDiffuse = uSunColor * NdotL * uSkyBrightness * shadowFactor;
    
    // Combine sky lighting (ambient + shadowed sun)
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
