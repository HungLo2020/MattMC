#version 450
struct MeshVertex {
    vec4 position_uv;
    vec4 color_uv;
    vec4 normal_light;
    vec4 extra_data;
    vec4 shader_data;
};
layout(set = 0, binding = 0, std430) readonly buffer WorldMeshVertices {
    MeshVertex vertices[];
};
struct MeshInstance {
    mat4 model;
    vec4 color;
    vec4 material;
    vec4 animation_region;
    vec4 animation_next_region;
    vec4 overlay_color;
    vec4 texture_transform;
};
layout(set = 0, binding = 1, std430) readonly buffer WorldMeshInstances {
    mat4 view;
    mat4 projection;
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 fog_color_and_environmental_start;
    vec4 fog_ranges;
    MeshInstance instances[];
};
layout(location = 0) out vec4 v_outline_color;
layout(location = 1) out vec2 v_outline_uv;
void main() {
    MeshVertex vertex = vertices[gl_VertexIndex];
    MeshInstance instance = instances[gl_InstanceIndex];
    vec4 world = instance.model * vec4(vertex.position_uv.xyz, 1.0);
    vec4 clip = projection * view * world;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_outline_color = instance.color;
    v_outline_uv = (uint(instance.material.w) & 1u) != 0u
        ? vertex.shader_data.xy : vec2(vertex.position_uv.w, vertex.color_uv.w);
    v_outline_uv = v_outline_uv * instance.texture_transform.xy
        + vec2(instance.texture_transform.z,
            (uint(instance.material.w) & 1024u) != 0u ? 0.0 : instance.texture_transform.w);
}
