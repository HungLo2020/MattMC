#version 120

// Outputs to fragment shader
varying vec2 vTexCoord;
varying vec4 vColor;
varying vec3 vLightData;
varying vec3 vNormal;
varying vec3 vWorldPos;
varying float vFogFactor;
varying vec4 vShadowCoord0; // Near cascade
varying vec4 vShadowCoord1; // Mid cascade
varying vec4 vShadowCoord2; // Far cascade

// Uniforms
uniform vec3 uCameraPos;
uniform mat4 uShadowMatrix0; // Near cascade matrix
uniform mat4 uShadowMatrix1; // Mid cascade matrix
uniform mat4 uShadowMatrix2; // Far cascade matrix
uniform int uShadowsEnabled;

void main() {
    // Get vertex in world space (gl_Vertex is already in world space due to chunk translation)
    vec4 worldPos = gl_Vertex;
    
    // Transform to camera space for rendering
    vec4 viewPos = gl_ModelViewMatrix * worldPos;
    gl_Position = gl_ProjectionMatrix * viewPos;
    
    // Pass through data to fragment shader
    vTexCoord = gl_MultiTexCoord0.xy;
    vColor = gl_Color;
    vLightData = gl_MultiTexCoord1.xyz;  // Light data from secondary tex coord
    vWorldPos = worldPos.xyz;
    
    // Pass normal in world space (don't transform by ModelView matrix)
    // This ensures lighting is camera-independent
    vNormal = normalize(gl_Normal);
    
    // Calculate fog factor (exponential squared fog)
    float distance = length(viewPos.xyz - uCameraPos);
    const float fogDensity = 0.0035; // Reduced density for more distant fog
    float fogAmount = 1.0 - exp(-pow(distance * fogDensity, 2.0));
    vFogFactor = clamp(fogAmount, 0.0, 1.0);
    
    // Calculate shadow coordinates for all cascades if shadows are enabled
    if (uShadowsEnabled == 1) {
        vShadowCoord0 = uShadowMatrix0 * worldPos;
        vShadowCoord1 = uShadowMatrix1 * worldPos;
        vShadowCoord2 = uShadowMatrix2 * worldPos;
    }
}
