#version 330

#ifndef MAX_TEXTURE_LOD_BIAS
#error "MAX_TEXTURE_LOD_BIAS constant not specified"
#endif

#moj_import <minecraft:fog.glsl>
#moj_import <sodium:chunk_material.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

flat in uint materialBits;

out vec4 fragColor;

void main() {
    fragColor = apply_fog(
        vec4(1.0, 0.0, 0.0, 1.0),
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor
    );
}