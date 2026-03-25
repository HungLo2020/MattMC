#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

out vec3 texCoord0;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vec2 clipPos = uv * 2.0 - 1.0;
    gl_Position = vec4(clipPos, 1.0, 1.0);
    vec3 viewDirection = vec3(clipPos.x / ProjMat[0][0], clipPos.y / ProjMat[1][1], -1.0);
    texCoord0 = transpose(mat3(ModelViewMat)) * viewDirection;
}
