#version 130

// Inputs from vertex shader
// Using centroid qualifier to prevent MSAA edge sampling artifacts.
// Centroid interpolation ensures sample points stay within the triangle primitive,
// preventing off-color banding caused by samples falling outside triangle boundaries.
centroid varying vec2 vTexCoord;
centroid varying vec4 vColor;
centroid varying vec4 vLightData; // (skyLight, blockLightR, blockLightG, blockLightB)
centroid varying float vAO; // Ambient occlusion

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

// Map RGB light values to color with gamma curve
vec3 lightToColor(vec3 lightRGB, float gamma) {
	// Normalize to 0.0-1.0
	vec3 normalized = clamp(lightRGB / 15.0, 0.0, 1.0);
	
	// Apply gamma curve
	return pow(normalized, vec3(gamma));
}

void main() {
	// Sample texture
	vec4 texColor = texture2D(uTexture, vTexCoord);
	
	// Discard fully transparent pixels for proper alpha testing
	if (texColor.a < 0.01) {
		discard;
	}
	
	// Extract light components (0-15 range)
	float skyLight = vLightData.x;
	vec3 blockLightRGB = vLightData.yzw; // R, G, B
	float ao = vAO; // Ambient occlusion (0.0 to 1.0 range)
	
	// Convert to brightness/color with gamma curve
	float skyBrightness = lightToBrightness(skyLight, uLightGamma);
	vec3 blockLightColor = lightToColor(blockLightRGB, uLightGamma);
	
	// Combine sky light (white) with colored block light
	// Sky light is white, so we scale it uniformly
	vec3 skyColor = vec3(skyBrightness);
	
	// Additive blending of sky and block light for smooth color mixing
	// This creates natural color transitions when different light sources overlap
	vec3 finalLightColor = skyColor + blockLightColor;
	
	// Clamp to prevent over-brightening
	finalLightColor = min(finalLightColor, vec3(1.0));
	
	// Apply ambient occlusion - darkens corners and edges next to solid blocks
	// AO is in 0.0-1.0 range where 1.0 = full brightness, 0.0 = full occlusion
	finalLightColor *= ao;
	
	// Ensure minimum brightness (never completely dark)
	// Increased from 0.05 to 0.20, then to 0.25 to reduce interior corner darkness
	finalLightColor = max(finalLightColor, vec3(0.25));
	
	// Apply optional emissive boost
	finalLightColor *= uEmissiveBoost;
	
	// Apply lighting to final color, preserving texture alpha for transparency
	gl_FragColor = vColor * texColor * vec4(finalLightColor, 1.0);
}
