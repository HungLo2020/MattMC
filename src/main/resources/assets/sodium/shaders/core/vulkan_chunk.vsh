#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(std140) uniform SodiumChunkParams {
    vec2 TexCoordShrink;
};

layout(std140) uniform SodiumChunkRegion {
    vec3 RegionOffset;
};

uniform sampler2D Sampler2;

in uvec2 a_Position;
in vec4 a_Color;
in uvec2 a_TexCoord;
in uvec4 a_LightAndData;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

flat out uint materialBits;

const uint POSITION_BITS = 20u;
const uint POSITION_MAX_COORD = 1u << POSITION_BITS;
const uint TEXTURE_BITS = 15u;
const uint TEXTURE_VALUE_MASK = (1u << TEXTURE_BITS) - 1u;

const float VERTEX_SCALE = 32.0 / float(POSITION_MAX_COORD);
const float VERTEX_OFFSET = -8.0;

uvec3 deinterleaveU20x3(uvec2 data) {
    uvec3 hi = (uvec3(data.x) >> uvec3(0u, 10u, 20u)) & uvec3(0x3FFu);
    uvec3 lo = (uvec3(data.y) >> uvec3(0u, 10u, 20u)) & uvec3(0x3FFu);
    return (hi << 10u) | lo;
}

vec2 decodeTexCoord(uvec2 packed) {
    return vec2(packed & TEXTURE_VALUE_MASK) / float(1u << TEXTURE_BITS);
}

vec2 decodeTexCoordBias(uvec2 packed) {
    return mix(vec2(-1.0), vec2(1.0), bvec2(packed >> TEXTURE_BITS));
}

uvec3 decodeRelativeChunkCoord(uint drawId) {
    return uvec3(drawId) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 decodePosition() {
    return vec3(deinterleaveU20x3(a_Position)) * VERTEX_SCALE + VERTEX_OFFSET;
}

vec3 decodeDrawTranslation(uint drawId) {
    return vec3(decodeRelativeChunkCoord(drawId)) * vec3(16.0);
}

void main() {
    vec3 position = decodePosition() + RegionOffset + decodeDrawTranslation(a_LightAndData[3]);
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(position);
    cylindricalVertexDistance = fog_cylindrical_distance(position);
    vertexColor = a_Color * texture(Sampler2, vec2(a_LightAndData.xy) / vec2(256.0)) * ColorModulator;
    texCoord0 = decodeTexCoord(a_TexCoord) + decodeTexCoordBias(a_TexCoord) * TexCoordShrink;
    materialBits = a_LightAndData[2];
}