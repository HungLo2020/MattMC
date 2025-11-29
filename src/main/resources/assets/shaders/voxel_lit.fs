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

// Minecraft-style brightness lookup
// In Minecraft, light level 15 = 1.0, level 0 = ambient light (about 0.05)
// The formula is: level/15 with ambient light interpolation
// Minecraft also applies a slight curve to make middle values brighter
float getLightBrightness(float lightLevel) {
	// Normalize to 0-1 range
	float normalized = clamp(lightLevel / 15.0, 0.0, 1.0);
	
	// Apply a subtle curve to boost mid-range values (like Minecraft's notGamma)
	// This makes light level 7-10 brighter than a linear curve
	float curved = 1.0 - (1.0 - normalized) * (1.0 - normalized) * (1.0 - normalized) * (1.0 - normalized);
	
	// Mix between curved and linear for a balanced response
	return mix(normalized, curved, 0.5);
}

// Get directional shade based on face normal (Minecraft style)
// In Minecraft, this affects the BASE color of vertices, creating visible face edges
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
	
	// For angled faces, interpolate based on normal components
	float upShade = max(0.0, normal.y);
	float downShade = max(0.0, -normal.y) * 0.5;
	float sideShade = (absNormal.x * 0.6 + absNormal.z * 0.8);
	
	return upShade + downShade + sideShade * (1.0 - absNormal.y);
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
	
	// Calculate sky light brightness (with day/night multiplier)
	float skyBrightness = getLightBrightness(skyLight) * uSkyDarkness;
	
	// Calculate block light brightness for each RGB channel
	// This preserves your colored block lights!
	vec3 blockBrightness = vec3(
		getLightBrightness(blockLightRGB.r),
		getLightBrightness(blockLightRGB.g),
		getLightBrightness(blockLightRGB.b)
	);
	
	// Combine sky light (white) with colored block light using MAX blend
	// This is how Minecraft combines them - take the brighter of the two
	// But we use a soft max to allow colored lights to tint even in daylight
	vec3 skyColor = vec3(skyBrightness);
	vec3 combinedLight = max(skyColor, blockBrightness);
	
	// Also add a portion of the block light color on top for color blending
	// This lets colored lights show their color even when sky light is present
	vec3 blockColorBoost = blockBrightness * 0.3;
	vec3 finalLightColor = combinedLight + blockColorBoost;
	
	// Clamp to prevent over-brightening
	finalLightColor = min(finalLightColor, vec3(1.0));
	
	// Apply ambient occlusion (subtle darkening in corners)
	// vAO is 0.0-1.0 where 1.0 = no occlusion, 0.0 = full occlusion
	// Use a gentle strength so it's not too harsh
	float aoStrength = 0.4;
	float aoFactor = mix(1.0, vAO, aoStrength);
	finalLightColor *= aoFactor;
	
	// Apply directional face shading (Minecraft-style block face visibility)
	// This is separate from lighting - it's about making block faces distinguishable
	float directionalShade = getDirectionalShade(vNormal);
	
	// Apply directional shade more gently - blend with 1.0 instead of pure multiply
	// This prevents faces from becoming too dark
	float shadeStrength = 0.5; // 0 = no shading, 1 = full Minecraft shading
	float appliedShade = mix(1.0, directionalShade, shadeStrength);
	finalLightColor *= appliedShade;
	
	// Ensure minimum ambient brightness (never pitch black)
	// 0.1 is a good balance - dark but not unplayable
	finalLightColor = max(finalLightColor, vec3(0.1));
	
	// Apply optional emissive boost
	finalLightColor *= uEmissiveBoost;
	
	// Apply lighting to final color, preserving texture alpha for transparency
	gl_FragColor = vColor * texColor * vec4(finalLightColor, 1.0);
}
