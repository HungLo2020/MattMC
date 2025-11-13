#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData;  // (skyLight 0-15, blockLight 0-15, ao 0-3)
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
 * Calculate shadow factor using PCF (Percentage Closer Filtering).
 * Returns 1.0 for fully lit, 0.0 for fully shadowed.
 */
float calculateShadow() {
    if (uShadowsEnabled == 0) {
        return 1.0; // No shadows
    }
    
    // Perspective divide to get NDC coordinates
    vec3 projCoords = vShadowCoord.xyz / vShadowCoord.w;
    
    // Transform from [-1,1] to [0,1] range
    projCoords = projCoords * 0.5 + 0.5;
    
    // If outside shadow map bounds, assume lit
    if (projCoords.x < 0.0 || projCoords.x > 1.0 || 
        projCoords.y < 0.0 || projCoords.y > 1.0 ||
        projCoords.z > 1.0) {
        return 1.0;
    }
    
    // Get depth from shadow map
    float closestDepth = texture2D(uShadowMap, projCoords.xy).r;
    float currentDepth = projCoords.z;
    
    // Bias to prevent shadow acne
    float bias = max(0.005 * (1.0 - dot(vNormal, uSunDir)), 0.001);
    
    // Simple PCF (2x2 samples)
    float shadow = 0.0;
    vec2 texelSize = vec2(1.0 / 2048.0); // Assuming 2048x2048 shadow map
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 offset = vec2(x, y) * texelSize;
            float pcfDepth = texture2D(uShadowMap, projCoords.xy + offset).r;
            shadow += currentDepth - bias > pcfDepth ? 0.0 : 1.0;
        }
    }
    shadow /= 9.0; // Average of 9 samples
    
    return shadow;
}

void main() {
    // Sample texture
    vec4 texColor = texture2D(uTexture, vTexCoord);
    
    // Get albedo (no color space conversion needed)
    vec3 albedo = vColor.rgb * texColor.rgb;
    
    // Extract light data
    float skyLight = vLightData.x;
    float blockLight = vLightData.y;
    float ao = vLightData.z;
    
    // Convert light values from 0-15 range to 0-1 range
    float skyLightNorm = skyLight / 15.0;
    float blockLightNorm = blockLight / 15.0;
    
    // Apply ambient occlusion factor
    // AO values: 0 = no occlusion, 1 = slight, 2 = medium, 3 = heavy
    float aoFactor = 1.0;
    if (ao >= 3.0) aoFactor = 0.45;
    else if (ao >= 2.0) aoFactor = 0.6;
    else if (ao >= 1.0) aoFactor = 0.8;
    
    // Calculate ambient lighting from both sources
    // Apply sky brightness multiplier to dim ambient sky light at night
    vec3 skyAmbient = uAmbientSky * skyLightNorm * uSkyBrightness;
    vec3 blockAmbient = uAmbientBlock * blockLightNorm;
    
    // Calculate Lambert diffuse from sun (N·L)
    float NdotL = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
    
    // Calculate shadow factor (only if there's skylight)
    float shadowFactor = 1.0;
    if (skyLightNorm > 0.0) {
        shadowFactor = calculateShadow();
    }
    
    // Apply shadow to sun diffuse lighting
    vec3 sunDiffuse = uSunColor * NdotL * skyLightNorm * uSkyBrightness * shadowFactor;
    
    // Combine sky lighting (ambient + shadowed sun)
    vec3 skyLighting = skyAmbient + sunDiffuse;
    
    // Apply ambient occlusion to sky lighting
    skyLighting *= aoFactor;
    
    // Block light is less affected by AO and adds on top of sky lighting
    // Apply reduced AO to block light (50% influence)
    float blockAOFactor = mix(1.0, aoFactor, 0.5);
    vec3 blockLighting = blockAmbient * blockAOFactor;
    
    // Combine lighting: use max for base, then add block light contribution
    // This makes torches visible even in bright areas
    vec3 lighting = max(skyLighting, blockLighting);
    
    // Add extra block light contribution to make torches more visible
    // Increased to 0.8 (80%) to make torches clearly visible even when using placeholder models
    lighting += blockLighting * 0.8;
    
    // Apply lighting to albedo
    vec3 litColor = albedo * lighting;
    
    // Apply fog
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);
    
    // Output final color (no gamma correction)
    gl_FragColor = vec4(finalColor, vColor.a * texColor.a);
}
