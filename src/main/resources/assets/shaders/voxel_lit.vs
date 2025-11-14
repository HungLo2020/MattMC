#version 120

// Outputs to fragment shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData; // (skyLight, blockLight, ao)

void main() {
	// Transform vertex position
	gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
	
	// Pass through texture coordinates and color
	vTexCoord = gl_MultiTexCoord0.xy;
	vColor = gl_Color;
	
	// Pass through light data from secondary texture coordinate
	vLightData = gl_MultiTexCoord1.xyz;
}
