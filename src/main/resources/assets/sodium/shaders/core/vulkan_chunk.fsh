#version 330

#ifndef MAX_TEXTURE_LOD_BIAS
#error "MAX_TEXTURE_LOD_BIAS constant not specified"
#endif

#moj_import <minecraft:fog.glsl>
#moj_import <sodium:chunk_material.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec2 lightCoord0;

layout(location = 5) flat in uint materialBits;

layout(location = 0) out vec4 fragColor;

vec4 minecraft_sample_lightmap(sampler2D lightMap, vec2 uv) {
    return texture(lightMap, clamp(uv + vec2(0.5 / 16.0), vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main() {
    float lodBias = _material_use_mips(materialBits) ? 0.0 : float(-MAX_TEXTURE_LOD_BIAS);
    vec4 color = texture(Sampler0, texCoord0, lodBias) * vertexColor * minecraft_sample_lightmap(Sampler2, lightCoord0);
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