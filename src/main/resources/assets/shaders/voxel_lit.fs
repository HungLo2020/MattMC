#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData;  // (skyLight 0-15, blockLight 0-15, ao 0-3)
varying vec3 vNormal;
varying vec3 vWorldPos;
varying float vFogFactor;

// Uniforms
uniform sampler2D uTexture;
uniform vec3 uSunDir;        // Normalized sun direction vector
uniform vec3 uSunColor;      // Sun color (linear space)
uniform vec3 uAmbientSky;    // Sky ambient color (linear space)
uniform vec3 uAmbientBlock;  // Block light ambient color (linear space)
uniform float uGamma;        // Gamma value (typically 2.2)
uniform vec3 uFogColor;      // Fog color (linear space)

// Convert sRGB to linear space
vec3 srgbToLinear(vec3 srgb) {
    return pow(srgb, vec3(2.2));
}

void main() {
    // Sample texture (in sRGB space)
    vec4 texColor = texture2D(uTexture, vTexCoord);
    
    // Convert texture and vertex colors from sRGB to linear space
    vec3 albedo = srgbToLinear(vColor.rgb) * srgbToLinear(texColor.rgb);
    
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
    
    // Calculate ambient lighting (scaled down to prevent over-brightening)
    vec3 skyAmbient = uAmbientSky * skyLightNorm;
    vec3 blockAmbient = uAmbientBlock * blockLightNorm;
    vec3 ambient = max(skyAmbient, blockAmbient); // Take maximum of the two
    
    // Calculate Lambert diffuse from sun (N·L)
    float NdotL = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
    vec3 sunDiffuse = uSunColor * NdotL * skyLightNorm * 0.5; // Scale down sun contribution
    
    // Combine all lighting (do this in linear space)
    // Total should not exceed 1.0 to prevent over-saturation
    vec3 lighting = ambient + sunDiffuse;
    
    // Apply ambient occlusion
    lighting *= aoFactor;
    
    // Apply lighting to albedo (still in linear space)
    vec3 litColor = albedo * lighting;
    
    // Apply fog (in linear space)
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);
    
    // Apply gamma correction at the end (linear -> sRGB)
    finalColor = pow(finalColor, vec3(1.0 / uGamma));
    
    // Output final color
    gl_FragColor = vec4(finalColor, vColor.a * texColor.a);
}
