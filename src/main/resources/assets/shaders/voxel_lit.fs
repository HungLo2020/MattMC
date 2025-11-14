#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData; // (skyLight, blockLight, ao)

// Uniforms
uniform sampler2D uTexture;
uniform float uLightGamma; // Gamma exponent for light curve (default 1.4)
uniform float uEmissiveBoost; // Brightness boost for emissive textures (default 1.0)
uniform float uAOStrength; // AO strength multiplier (default 0.8, 0=off, 1=full)

// Map light value (0-15) to brightness (0.0-1.0) with gamma curve
float lightToBrightness(float lightValue, float gamma) {
	// Normalize to 0.0-1.0
	float normalized = clamp(lightValue / 15.0, 0.0, 1.0);
	
	// Apply gamma curve
	return pow(normalized, gamma);
}

// Map AO value (0-3) to brightness multiplier
float aoToBrightness(float aoValue, float strength) {
	// Normalize AO from 0-3 to 0.0-1.0
	float normalized = clamp(aoValue / 3.0, 0.0, 1.0);
	
	// Calculate darkening factor (0=no darkening, 1=full darkening)
	// AO 0 → 0.0 darkening (no AO)
	// AO 1 → 0.33 darkening (weak AO)
	// AO 2 → 0.66 darkening (medium AO)
	// AO 3 → 1.0 darkening (strong AO)
	
	// Apply strength multiplier
	float darkening = normalized * strength;
	
	// Convert to brightness (1.0 = no darkening, 0.0 = full darkening)
	return 1.0 - darkening;
}

void main() {
	// Sample texture
	vec4 texColor = texture2D(uTexture, vTexCoord);
	
	// Extract light components
	float skyLight = vLightData.x;    // 0-15 range
	float blockLight = vLightData.y;  // 0-15 range
	float ao = vLightData.z;          // 0-3 range
	
	// Convert to brightness with gamma curve
	float skyBrightness = lightToBrightness(skyLight, uLightGamma);
	float blockBrightness = lightToBrightness(blockLight, uLightGamma);
	
	// Combine sky and block light (take maximum)
	float finalBrightness = max(skyBrightness, blockBrightness);
	
	// Ensure minimum brightness (never completely dark)
	finalBrightness = max(finalBrightness, 0.05);
	
	// Apply optional emissive boost
	// (could be controlled per-texture in future)
	finalBrightness *= uEmissiveBoost;
	
	// Apply ambient occlusion
	float aoBrightness = aoToBrightness(ao, uAOStrength);
	finalBrightness *= aoBrightness;
	
	// Apply lighting to final color
	gl_FragColor = vColor * texColor * vec4(vec3(finalBrightness), 1.0);
}
