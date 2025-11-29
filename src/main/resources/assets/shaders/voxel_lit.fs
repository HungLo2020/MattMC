#version 130

// Inputs from vertex shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec4 vLightData; // (skyLight, blockLightR, blockLightG, blockLightB)
varying float vAO; // Ambient occlusion (0.0-1.0, where 1.0 = no occlusion)
varying vec3 vNormal; // Face normal for directional shading

// Uniforms
uniform sampler2D uTexture;
uniform float uLightGamma; // Gamma exponent for light curve (default 1.4)
uniform float uEmissiveBoost; // Brightness boost for emissive textures (default 1.0)
uniform float uSkyDarkness; // Sky light multiplier for day/night (0.0-1.0, default 1.0)

// Simple light level to brightness conversion
// Light level 15 = full brightness (1.0), level 0 = minimum ambient
float getLightBrightness(float lightLevel) {
	return clamp(lightLevel / 15.0, 0.0, 1.0);
}

// Get directional shade based on face normal (Minecraft style)
// UP = 1.0, DOWN = 0.5, NORTH/SOUTH = 0.8, WEST/EAST = 0.6
const float AXIS_DOMINANT_THRESHOLD = 0.9;

float getDirectionalShade(vec3 normal) {
	vec3 absNormal = abs(normal);
	
	if (absNormal.y > AXIS_DOMINANT_THRESHOLD) {
		return normal.y > 0.0 ? 1.0 : 0.5;
	} else if (absNormal.z > AXIS_DOMINANT_THRESHOLD) {
		return 0.8;
	} else if (absNormal.x > AXIS_DOMINANT_THRESHOLD) {
		return 0.6;
	}
	
	// For angled faces, interpolate
	float upShade = max(0.0, normal.y);
	float downShade = max(0.0, -normal.y) * 0.5;
	float sideShade = (absNormal.x * 0.6 + absNormal.z * 0.8);
	
	return upShade + downShade + sideShade * (1.0 - absNormal.y);
}

void main() {
	// Sample texture
	vec4 texColor = texture2D(uTexture, vTexCoord);
	
	// Discard fully transparent pixels
	if (texColor.a < 0.01) {
		discard;
	}
	
	// Extract light components (0-15 range)
	float skyLight = vLightData.x;
	vec3 blockLightRGB = vLightData.yzw;
	
	// Calculate sky light brightness
	// Use uSkyDarkness if it's set (> 0), otherwise assume full daylight (1.0)
	float skyDarknessFactor = uSkyDarkness > 0.001 ? uSkyDarkness : 1.0;
	float skyBrightness = getLightBrightness(skyLight) * skyDarknessFactor;
	
	// Calculate block light brightness for each RGB channel (preserves colored lights!)
	vec3 blockBrightness = vec3(
		getLightBrightness(blockLightRGB.r),
		getLightBrightness(blockLightRGB.g),
		getLightBrightness(blockLightRGB.b)
	);
	
	// Combine sky light (white) with colored block light
	// Take the MAX of sky and block light, then add block color boost
	vec3 skyColor = vec3(skyBrightness);
	vec3 combinedLight = max(skyColor, blockBrightness);
	
	// Add block light color on top so colored lights show even in daylight
	vec3 blockColorBoost = blockBrightness * 0.5;
	vec3 finalLightColor = combinedLight + blockColorBoost;
	
	// Clamp to prevent over-brightening
	finalLightColor = min(finalLightColor, vec3(1.0));
	
	// Apply ambient occlusion very gently (20% strength)
	float aoFactor = mix(1.0, vAO, 0.2);
	finalLightColor *= aoFactor;
	
	// Apply directional face shading gently (30% strength)
	float directionalShade = getDirectionalShade(vNormal);
	float appliedShade = mix(1.0, directionalShade, 0.3);
	finalLightColor *= appliedShade;
	
	// Ensure minimum ambient brightness
	finalLightColor = max(finalLightColor, vec3(0.15));
	
	// Apply optional emissive boost
	finalLightColor *= uEmissiveBoost;
	
	// Apply lighting to final color
	gl_FragColor = vColor * texColor * vec4(finalLightColor, 1.0);
}
