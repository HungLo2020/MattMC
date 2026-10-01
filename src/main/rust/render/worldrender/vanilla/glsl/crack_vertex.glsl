#version 450
struct CrackQuad {
    vec4 p0;
    vec4 p1;
    vec4 p2;
    vec4 p3;
    vec4 uv_region;
    vec4 color;
};
layout(set = 0, binding = 0, std140) uniform CrackQuadBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport;
    CrackQuad quads[512];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
const vec2 corner[6] = vec2[6](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0),
    vec2(0.0, 0.0)
);
void main() {
    int vertex = gl_VertexIndex;
    CrackQuad quad = quads[gl_InstanceIndex];
    vec2 c = corner[vertex];
    vec3 top = mix(quad.p0.xyz, quad.p1.xyz, c.x);
    vec3 bottom = mix(quad.p3.xyz, quad.p2.xyz, c.x);
    vec3 position = mix(top, bottom, c.y);
    vec4 clip = projection * view * vec4(position, 1.0);
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_uv = vec2(
        quad.uv_region.x + c.x * quad.uv_region.z,
        quad.uv_region.y + c.y * quad.uv_region.w
    );
    v_color = quad.color;
}
