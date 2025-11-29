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

// Minecraft-style brightness curve: f / (4.0 - 3.0 * f)
// This creates a non-linear response that's brighter in the middle range
float minecraftBrightness(float lightLevel) {
	float f = clamp(lightLevel / 15.0, 0.0, 1.0);
	return f / (4.0 - 3.0 * f);
}

// Map light value (0-15) to brightness (0.0-1.0) using Minecraft's curve
float lightToBrightness(float lightValue) {
	return minecraftBrightness(lightValue);
}

// Map RGB light values to color using Minecraft's brightness curve
// Preserves the RGB ratios while applying the non-linear response
vec3 lightToColor(vec3 lightRGB) {
	// Get the maximum component to determine overall intensity
	float maxLight = max(max(lightRGB.r, lightRGB.g), lightRGB.b);
	if (maxLight < 0.001) {
		return vec3(0.0);
	}
	
	// Apply Minecraft's brightness curve to the max intensity
	float brightnessFactor = minecraftBrightness(maxLight);
	
	// Scale the RGB values proportionally to preserve color ratios
	return (lightRGB / maxLight) * brightnessFactor;
}

// Get directional shade based on face normal (Minecraft style)
// UP = 1.0, DOWN = 0.5, NORTH/SOUTH = 0.8, WEST/EAST = 0.6
float getDirectionalShade(vec3 normal) {
	// Determine which axis the normal is primarily aligned with
	vec3 absNormal = abs(normal);
	
	if (absNormal.y > 0.9) {
		// Y-axis dominant: up or down face
		return normal.y > 0.0 ? 1.0 : 0.5;
	} else if (absNormal.z > 0.9) {
		// Z-axis dominant: north or south face
		return 0.8;
	} else if (absNormal.x > 0.9) {
		// X-axis dominant: west or east face
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
	
	// Apply Minecraft's brightness curve to sky light
	// Also apply sky darkness for day/night cycle
	float skyBrightness = lightToBrightness(skyLight) * uSkyDarkness;
	
	// Apply Minecraft's brightness curve to block light (preserving RGB)
	vec3 blockLightColor = lightToColor(blockLightRGB);
	
	// Combine sky light (white) with colored block light
	vec3 skyColor = vec3(skyBrightness);
	
	// Additive blending of sky and block light for smooth color mixing
	vec3 finalLightColor = skyColor + blockLightColor;
	
	// Clamp to prevent over-brightening
	finalLightColor = min(finalLightColor, vec3(1.0));
	
	// Apply ambient occlusion (vAO is 0.0-1.0 where 1.0 = no occlusion)
	// Mix with a strength factor so AO isn't too harsh
	float aoStrength = 0.6;
	float aoFactor = mix(1.0, vAO, aoStrength);
	finalLightColor *= aoFactor;
	
	// Apply directional face shading (Minecraft-style)
	float directionalShade = getDirectionalShade(vNormal);
	finalLightColor *= directionalShade;
	
	// Ensure minimum brightness (much darker than before for cave atmosphere)
	// Minecraft uses very dark caves, so 0.04 is more appropriate than 0.25
	finalLightColor = max(finalLightColor, vec3(0.04));
	
	// Apply optional emissive boost
	finalLightColor *= uEmissiveBoost;
	
	// Apply lighting to final color, preserving texture alpha for transparency
	gl_FragColor = vColor * texColor * vec4(finalLightColor, 1.0);
}
