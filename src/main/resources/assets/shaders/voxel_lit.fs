#version 120
#extension GL_EXT_texture_array : enable

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData;  // (skyLight 0-15, blockLight 0-15, ao 0-3)
varying vec3 vNormal;
varying vec3 vWorldPos;
varying float vFogFactor;
varying float vViewDepth;

// Uniforms
uniform sampler2D uTexture;
uniform sampler2DArrayShadow uShadowMap;  // Shadow map texture array
uniform vec3 uSunDir;        // Normalized sun direction vector
uniform vec3 uSunColor;      // Sun color
uniform vec3 uAmbientSky;    // Sky ambient color
uniform vec3 uAmbientBlock;  // Block light ambient color
uniform float uGamma;        // Gamma value (typically 2.2)
uniform vec3 uFogColor;      // Fog color
uniform float uSkyBrightness; // Sky brightness multiplier (0.0-1.0) for day/night cycle

// Shadow cascade uniforms
uniform mat4 uLightViewProj[4];  // Light view-projection matrices for each cascade
uniform vec4 uCascadeSplits;     // Cascade split distances (near/far pairs)
uniform int uNumCascades;        // Number of active cascades
uniform bool uShadowsEnabled;    // Whether shadows are enabled

/**
 * Sample shadow map with PCF (Percentage Closer Filtering).
 * Uses a 3x3 kernel for smooth shadow edges.
 */
float sampleShadowMapPCF(vec3 shadowCoord, int cascadeIndex) {
    float shadow = 0.0;
    vec2 texelSize = vec2(1.0 / 1024.0); // Adjust based on shadow map resolution
    
    // 3x3 PCF kernel
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 offset = vec2(x, y) * texelSize;
            vec4 shadowSample = vec4(shadowCoord.xy + offset, float(cascadeIndex), shadowCoord.z);
            shadow += shadow2DArray(uShadowMap, shadowSample).r;
        }
    }
    
    return shadow / 9.0; // Average of 9 samples
}

/**
 * Calculate shadow factor for the current fragment.
 * Selects appropriate cascade based on view depth and blends between cascades.
 */
float calculateShadow() {
    if (!uShadowsEnabled || uNumCascades == 0) {
        return 1.0; // No shadows
    }
    
    // Select cascade based on view depth
    int cascadeIndex = -1;
    for (int i = 0; i < uNumCascades; i++) {
        if (i == 0 && vViewDepth < uCascadeSplits[0]) {
            cascadeIndex = 0;
            break;
        } else if (i > 0 && vViewDepth >= uCascadeSplits[i-1] && vViewDepth < uCascadeSplits[i]) {
            cascadeIndex = i;
            break;
        }
    }
    
    // If no cascade found, return full light (no shadow)
    if (cascadeIndex < 0) {
        return 1.0;
    }
    
    // Transform world position to light clip space
    vec4 lightClipPos = uLightViewProj[cascadeIndex] * vec4(vWorldPos, 1.0);
    
    // Perspective divide and transform to [0,1] range
    vec3 shadowCoord = lightClipPos.xyz / lightClipPos.w;
    shadowCoord = shadowCoord * 0.5 + 0.5;
    
    // Check if fragment is within shadow map bounds
    if (shadowCoord.x < 0.0 || shadowCoord.x > 1.0 ||
        shadowCoord.y < 0.0 || shadowCoord.y > 1.0 ||
        shadowCoord.z < 0.0 || shadowCoord.z > 1.0) {
        return 1.0; // Outside shadow map, assume lit
    }
    
    // Apply depth bias to reduce shadow acne
    float bias = 0.005;
    shadowCoord.z -= bias;
    
    // Sample shadow map with PCF
    float shadowFactor = sampleShadowMapPCF(shadowCoord, cascadeIndex);
    
    // Blend with next cascade near split boundaries to reduce popping
    float blendRange = 5.0; // Distance over which to blend cascades
    if (cascadeIndex < uNumCascades - 1) {
        float nextSplit = uCascadeSplits[cascadeIndex];
        float blendFactor = clamp((nextSplit - vViewDepth) / blendRange, 0.0, 1.0);
        
        if (blendFactor < 1.0) {
            // Sample next cascade
            vec4 nextLightClipPos = uLightViewProj[cascadeIndex + 1] * vec4(vWorldPos, 1.0);
            vec3 nextShadowCoord = nextLightClipPos.xyz / nextLightClipPos.w;
            nextShadowCoord = nextShadowCoord * 0.5 + 0.5;
            nextShadowCoord.z -= bias;
            
            float nextShadowFactor = sampleShadowMapPCF(nextShadowCoord, cascadeIndex + 1);
            shadowFactor = mix(nextShadowFactor, shadowFactor, blendFactor);
        }
    }
    
    return shadowFactor;
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
    
    // Calculate shadow factor
    float shadowFactor = calculateShadow();
    
    // Apply shadow to sun diffuse lighting only (not ambient)
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
