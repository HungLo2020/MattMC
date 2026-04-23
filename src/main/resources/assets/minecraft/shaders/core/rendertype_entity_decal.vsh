#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;


out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ModelOffset;
    vec3 viewPos = (ModelViewMat * vec4(pos, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewPos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(viewPos);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPos);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
