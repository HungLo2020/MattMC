#version 330

#ifndef MAX_TEXTURE_LOD_BIAS
#error "MAX_TEXTURE_LOD_BIAS constant not specified"
#endif

#moj_import <minecraft:fog.glsl>
#moj_import <sodium:chunk_material.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;


layout(location = 5) flat in uint materialBits;

layout(location = 0) out vec4 fragColor;

void main() {
#ifdef VULKAN_FORCE_BASE_MIP
    vec4 color = textureLod(Sampler0, texCoord0, 0.0);
#else
    float lodBias = _material_use_mips(materialBits) ? 0.0 : float(-MAX_TEXTURE_LOD_BIAS);
    vec4 color = texture(Sampler0, texCoord0, lodBias);
#endif
    color *= vertexColor;

#ifdef USE_FRAGMENT_DISCARD
    if (color.a < _material_alpha_cutoff(materialBits)) {
        discard;
    }
#endif

    fragColor = apply_fog(
        color,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor
    );
}