#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
flat out vec4 vertexColor;

void main() {
    vec3 viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewPos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(viewPos);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPos);
    vertexColor = Color * ColorModulator * texelFetch(Sampler2, UV2 / 16, 0);
}
