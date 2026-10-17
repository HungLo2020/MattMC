#version 450
layout(set = 1, binding = 2) uniform texture2D LightmapTexture;
layout(set = 1, binding = 3) uniform sampler LightmapSampler;
struct DistantHorizonsLodExactAtlasVertex {
    float local_x;
    float local_y;
    float local_z;
    float micro_x;
    float micro_y;
    float micro_z;
    float tile_u;
    float tile_v;
    float atlas_u0;
    float atlas_v0;
    float atlas_u1;
    float atlas_v1;
    uint color_rgba;
    uint light_normal_pad;
};
layout(set = 0, binding = 0, std430) readonly buffer DistantHorizonsLodExactAtlasVertices {
    DistantHorizonsLodExactAtlasVertex vertices[];
};
layout(set = 0, binding = 1, std140) uniform DistantHorizonsLodFrame {
    mat4 combined_matrix;
    vec4 column_origin_and_world_y;
    vec4 model_offset_and_reserved;
    vec4 clip_micro_noise_earth;
    uvec4 flags_and_noise;
    vec4 fog_color_and_alpha;
    vec4 fog_ranges;
    vec4 dh_fog_parameters[5];
};
layout(location = 0) out vec2 v_tile_uv;
layout(location = 1) flat out vec4 v_atlas_rect;
layout(location = 2) out vec4 v_color;
layout(location = 3) flat out uvec2 v_light;
layout(location = 4) flat out uint v_normal;
layout(location = 5) out vec3 v_world_position;
layout(location = 6) out vec3 v_source_position;
layout(location = 7) flat out uint v_material_flags;
layout(location = 9) out vec3 v_light_color;

void main() {
    DistantHorizonsLodExactAtlasVertex vertex = vertices[gl_VertexIndex];
    // Keep the exact-atlas stream on the same DH micro-offset contract as
    // the reduced-color stream. DH's standard vertex shader applies the
    // packed offset to horizontal X/Z only; the Y bits remain serialized
    // source data but do not move the rasterized vertex.
    vec3 base_local = vec3(vertex.local_x, vertex.local_y, vertex.local_z);
    vec3 local = base_local + vec3(vertex.micro_x, 0.0, vertex.micro_z);
    // Match Frozen's raw vertexWorldPos; micro offsets affect clip placement,
    // not the distance/fog/noise coordinate passed to the fragment stage.
    vec3 world = base_local + column_origin_and_world_y.xyz;
    vec4 clip = combined_matrix * vec4(
        local + model_offset_and_reserved.xyz,
        1.0
    );
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    if ((flags_and_noise.w & 2u) == 0u) {
        clip.z = clip.z * 0.5 + clip.w * 0.5;
    }
#endif
    if ((flags_and_noise.w & 1u) != 0u) clip.y = -clip.y;
    gl_Position = clip;
    v_tile_uv = vec2(vertex.tile_u, vertex.tile_v);
    v_atlas_rect = vec4(vertex.atlas_u0, vertex.atlas_v0, vertex.atlas_u1, vertex.atlas_v1);
    v_color = vec4(
        float(vertex.color_rgba & 0xffu),
        float((vertex.color_rgba >> 8u) & 0xffu),
        float((vertex.color_rgba >> 16u) & 0xffu),
        float((vertex.color_rgba >> 24u) & 0xffu)
    ) / 255.0;
    // Exact-atlas DH keeps the copied vertex tint separate from the
    // lightmap result because the fragment stage uses that tint to modulate
    // the resolved sprite. The lightmap is still sampled per vertex, as in
    // Frozen's standard.vert.
    float light_sky = float(vertex.light_normal_pad & 0x0fu);
    float light_sky_uv = (light_sky + 0.5) / 16.0;
    // Keep the original Frozen OpenGL sky coordinate, consistently with the
    // reduced-color fallback. A Java Vulkan-only brightness fold would make
    // shaded or submerged atlas replacements brighter than their source.
    vec2 light_uv = vec2(
        // Exact-atlas vertices use the same byte-separated sky/block ABI as
        // the reduced stream.
        (float((vertex.light_normal_pad >> 8u) & 0x0fu) + 0.5) / 16.0,
        light_sky_uv
    );
    v_light_color = texture(sampler2D(LightmapTexture, LightmapSampler), light_uv).rgb;
    v_light = uvec2(
        vertex.light_normal_pad & 0xffu,
        (vertex.light_normal_pad >> 8u) & 0xffu
    );
    v_normal = (vertex.light_normal_pad >> 16u) & 0xffu;
    v_material_flags = (vertex.light_normal_pad >> 24u) & 0xffu;
    v_world_position = world;
    v_source_position = base_local;
}
