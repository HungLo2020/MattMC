#version 130

// Outputs to fragment shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec4 vLightData; // (skyLight, blockLightR, blockLightG, blockLightB)
varying float vAO; // Ambient occlusion
varying vec3 vNormal; // Face normal for directional shading

void main() {
	// Transform vertex position
	gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
	
	// Pass through texture coordinates and color
	vTexCoord = gl_MultiTexCoord0.xy;
	vColor = gl_Color;
	
	// Pass through light data from secondary texture coordinates
	vLightData = gl_MultiTexCoord1; // skyLight, blockLightR, blockLightG, blockLightB
	vAO = gl_MultiTexCoord2.x; // Ambient occlusion
	
	// Pass through normal from gl_Normal (set by MeshBuilder)
	vNormal = gl_Normal;
}
