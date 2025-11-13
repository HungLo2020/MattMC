#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData;  // (skyLight 0-15, blockLight 0-15, ao 0-3)
varying vec3 vNormal;
varying vec3 vWorldPos;
varying float vFogFactor;
varying vec4 vShadowCoord0; // Near cascade
varying vec4 vShadowCoord1; // Mid cascade
varying vec4 vShadowCoord2; // Far cascade

// Uniforms
uniform sampler2D uTexture;
uniform sampler2D uShadowMap0; // Near cascade shadow map
uniform sampler2D uShadowMap1; // Mid cascade shadow map
uniform sampler2D uShadowMap2; // Far cascade shadow map
uniform vec3 uSunDir;        // Normalized sun direction vector
uniform vec3 uSunColor;      // Sun color
uniform vec3 uAmbientSky;    // Sky ambient color
uniform vec3 uAmbientBlock;  // Block light ambient color
uniform float uGamma;        // Gamma value (typically 2.2)
uniform vec3 uFogColor;      // Fog color
uniform float uSkyBrightness; // Sky brightness multiplier (0.0-1.0) for day/night cycle
uniform int uShadowsEnabled;  // Whether shadows are enabled
uniform vec3 uCameraPos;     // Camera position for cascade selection
uniform float uCascadeSplit0; // Near cascade distance (16 blocks)
uniform float uCascadeSplit1; // Mid cascade distance (48 blocks)

/**
 * Sample a shadow map and perform depth comparison.
 * Returns 1.0 for lit, 0.0 for shadowed.
 */
float sampleShadowMap(sampler2D shadowMap, vec4 shadowCoord, vec2 texelSize) {
    // Perspective divide
    vec3 projCoords = shadowCoord.xyz / shadowCoord.w;
    
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
    
    // PCF (Percentage Closer Filtering) with 3x3 samples
    float shadow = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 offset = vec2(x, y) * texelSize;
            float pcfDepth = texture2D(shadowMap, projCoords.xy + offset).r;
            shadow += currentDepth - bias > pcfDepth ? 0.0 : 1.0;
        }
    }
    return shadow / 9.0;
}

/**
 * Calculate shadow factor using Cascaded Shadow Maps (CSM).
 * Returns 1.0 for fully lit, 0.0 for fully shadowed.
 */
float calculateShadow() {
    if (uShadowsEnabled == 0) {
        return 1.0; // No shadows
    }
    
    // Calculate distance from camera to fragment
    float distanceFromCamera = length(vWorldPos - uCameraPos);
    
    // Shadow map texel size (assuming 2048x2048 shadow maps)
    vec2 texelSize = vec2(1.0 / 2048.0);
    
    // Select cascade based on distance from camera
    float shadow;
    if (distanceFromCamera < uCascadeSplit0) {
        // Near cascade (0-16 blocks): highest quality, tight frustum
        shadow = sampleShadowMap(uShadowMap0, vShadowCoord0, texelSize);
    } else if (distanceFromCamera < uCascadeSplit1) {
        // Mid cascade (16-48 blocks): medium quality
        shadow = sampleShadowMap(uShadowMap1, vShadowCoord1, texelSize);
    } else {
        // Far cascade (48-128 blocks): lowest quality, wide coverage
        shadow = sampleShadowMap(uShadowMap2, vShadowCoord2, texelSize);
    }
    
    return shadow;
}

void main() {
    // Sample texture
    vec4 texColor = texture2D(uTexture, vTexCoord);
    
    // Get albedo (no color space conversion needed)
    vec3 albedo = vColor.rgb * texColor.rgb;
    
    // Extract light data
    float skyLight = vLightData.x;       // Binary: 0 (no sky access) or 15 (can see sky)
    float blockLight = vLightData.y;     // 0-15 from light-emitting blocks
    float ao = vLightData.z;             // 0-3 ambient occlusion
    
    // Skylight is now binary: either has sky access (15) or not (0)
    // Use it only to determine if surface can receive sun lighting, not for shading intensity
    bool hasSkylightAccess = skyLight > 7.0; // Binary check (skylight should be 0 or 15)
    
    // Convert block light from 0-15 range to 0-1 range
    float blockLightNorm = blockLight / 15.0;
    
    // Apply ambient occlusion factor
    // AO values: 0 = no occlusion, 1 = slight, 2 = medium, 3 = heavy
    float aoFactor = 1.0;
    if (ao >= 3.0) aoFactor = 0.45;
    else if (ao >= 2.0) aoFactor = 0.6;
    else if (ao >= 1.0) aoFactor = 0.8;
    
    // Base ambient sky lighting - always present for surfaces with sky access
    // This represents ambient light scattered from the sky dome
    vec3 skyAmbient = hasSkylightAccess ? (uAmbientSky * uSkyBrightness * 0.4) : vec3(0.0);
    
    // Block light ambient (from torches, etc.)
    vec3 blockAmbient = uAmbientBlock * blockLightNorm;
    
    // Calculate directional sun lighting (only for surfaces with sky access)
    vec3 sunLighting = vec3(0.0);
    if (hasSkylightAccess && uSkyBrightness > 0.1) {
        // Calculate Lambert diffuse from sun (N·L)
        float NdotL = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
        
        // Calculate shadow factor from shadow maps based on sun position
        float shadowFactor = calculateShadow();
        
        // Sun provides strong directional lighting when not shadowed
        // Shadow map determines actual shadows based on sun angle
        sunLighting = uSunColor * NdotL * uSkyBrightness * shadowFactor;
    }
    
    // Combine sky lighting (ambient + directional sun with real-time shadows)
    vec3 skyLighting = skyAmbient + sunLighting;
    
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
