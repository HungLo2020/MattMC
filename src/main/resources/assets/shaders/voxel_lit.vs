#version 130

// Outputs to fragment shader
// Using centroid qualifier to prevent MSAA edge sampling artifacts.
// Centroid interpolation ensures sample points stay within the triangle primitive,
// preventing off-color banding caused by samples falling outside triangle boundaries.
centroid varying vec2 vTexCoord;
centroid varying vec4 vColor;
centroid varying vec4 vLightData; // (skyLight, blockLightR, blockLightG, blockLightB)
centroid varying float vAO; // Ambient occlusion

void main() {
	// Transform vertex position
	gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
	
	// Pass through texture coordinates and color
	vTexCoord = gl_MultiTexCoord0.xy;
	vColor = gl_Color;
	
	// Pass through light data from secondary texture coordinates
	vLightData = gl_MultiTexCoord1; // skyLight, blockLightR, blockLightG, blockLightB
	vAO = gl_MultiTexCoord2.x; // Ambient occlusion
}
