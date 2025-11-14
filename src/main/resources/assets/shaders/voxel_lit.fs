#version 120

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData; // (skyLight, blockLight, ao)

// Uniforms
uniform sampler2D uTexture;
uniform float uLightGamma; // Gamma exponent for light curve (default 1.4)
uniform float uEmissiveBoost; // Brightness boost for emissive textures (default 1.0)

// Map light value (0-15) to brightness (0.0-1.0) with gamma curve
float lightToBrightness(float lightValue, float gamma) {
	// Normalize to 0.0-1.0
	float normalized = clamp(lightValue / 15.0, 0.0, 1.0);
	
	// Apply gamma curve
	return pow(normalized, gamma);
}

void main() {
	// Sample texture
	vec4 texColor = texture2D(uTexture, vTexCoord);
	
	// Extract light components (0-15 range)
	float skyLight = vLightData.x;
	float blockLight = vLightData.y;
	float ao = vLightData.z; // Not used yet, reserved for ambient occlusion
	
	// Convert to brightness with gamma curve
	float skyBrightness = lightToBrightness(skyLight, uLightGamma);
	float blockBrightness = lightToBrightness(blockLight, uLightGamma);
	
	// Combine sky and block light (take maximum)
	float finalBrightness = max(skyBrightness, blockBrightness);
	
	// Ensure minimum brightness (never completely dark)
	// Increased from 0.05 to 0.20 to make shadows less intense
	finalBrightness = max(finalBrightness, 0.20);
	
	// Apply optional emissive boost
	// (could be controlled per-texture in future)
	finalBrightness *= uEmissiveBoost;
	
	// Apply lighting to final color
	gl_FragColor = vColor * texColor * vec4(vec3(finalBrightness), 1.0);
}
