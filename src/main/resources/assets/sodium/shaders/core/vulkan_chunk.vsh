#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(std140) uniform SodiumChunkParams {
    vec2 TexCoordShrink;
};

layout(location = 0) in uvec2 a_Position;
layout(location = 1) in vec4 a_Color;
layout(location = 2) in uvec2 a_TexCoord;
layout(location = 3) in uvec4 a_LightAndData;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out vec2 lightCoord0;

layout(location = 5) flat out uint materialBits;

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
    vec3 position = decodePosition() + ModelOffset + decodeDrawTranslation(a_LightAndData[3]);
    gl_Position = ModelViewMat * vec4(position, 1.0);
    gl_Position.z = 0.5 * (gl_Position.z + gl_Position.w);

    sphericalVertexDistance = fog_spherical_distance(position);
    cylindricalVertexDistance = fog_cylindrical_distance(position);
    vertexColor = a_Color * ColorModulator;
    texCoord0 = decodeTexCoord(a_TexCoord) + decodeTexCoordBias(a_TexCoord) * TexCoordShrink;
    lightCoord0 = vec2(a_LightAndData.xy) / vec2(256.0);
    materialBits = a_LightAndData[2];
}