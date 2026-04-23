#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ModelOffset;
    vec3 viewPos = (ModelViewMat * vec4(pos, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewPos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(viewPos);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPos);
    vertexColor = Color;
    texCoord0 = UV0;
}
