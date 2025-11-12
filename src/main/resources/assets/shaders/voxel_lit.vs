#version 120

// Outputs to fragment shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData;
varying vec3 vNormal;
varying vec3 vWorldPos;
varying float vFogFactor;
varying float vViewDepth;

// Uniforms
uniform vec3 uCameraPos;

void main() {
    // Transform vertex position
    vec4 worldPos = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * worldPos;
    
    // Pass through data to fragment shader
    vTexCoord = gl_MultiTexCoord0.xy;
    vColor = gl_Color;
    vLightData = gl_MultiTexCoord1.xyz;  // Light data from secondary tex coord
    vWorldPos = worldPos.xyz;
    
    // Calculate normal from the vertex normal attribute
    vNormal = normalize(gl_NormalMatrix * gl_Normal);
    
    // Calculate fog factor (exponential squared fog)
    float distance = length(worldPos.xyz - uCameraPos);
    const float fogDensity = 0.0035; // Reduced density for more distant fog
    float fogAmount = 1.0 - exp(-pow(distance * fogDensity, 2.0));
    vFogFactor = clamp(fogAmount, 0.0, 1.0);
    
    // Calculate view space depth for cascade selection
    vViewDepth = distance;
}
