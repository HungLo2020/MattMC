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
uniform vec3 uSunColor;      // Sun color
uniform vec3 uAmbientSky;    // Sky ambient color
uniform vec3 uAmbientBlock;  // Block light ambient color
uniform float uGamma;        // Gamma value (typically 2.2)
uniform vec3 uFogColor;      // Fog color

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
    
    // Calculate ambient lighting
    vec3 skyAmbient = uAmbientSky * skyLightNorm;
    vec3 blockAmbient = uAmbientBlock * blockLightNorm;
    vec3 ambient = max(skyAmbient, blockAmbient);
    
    // Calculate Lambert diffuse from sun (N·L)
    float NdotL = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
    vec3 sunDiffuse = uSunColor * NdotL * skyLightNorm;
    
    // Combine all lighting
    vec3 lighting = ambient + sunDiffuse;
    
    // Apply ambient occlusion
    lighting *= aoFactor;
    
    // Apply lighting to albedo
    vec3 litColor = albedo * lighting;
    
    // Add tiny emissive boost for emissive blocks (helps with bloom later)
    // Only add emissive when blockLight is very high (indicating light source)
    // This makes torches and other light-emitting blocks glow slightly
    float emissiveStrength = smoothstep(12.0, 15.0, blockLight);
    vec3 emissive = albedo * emissiveStrength * 0.15;
    litColor += emissive;
    
    // Apply fog
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);
    
    // Output final color (no gamma correction)
    gl_FragColor = vec4(finalColor, vColor.a * texColor.a);
}
