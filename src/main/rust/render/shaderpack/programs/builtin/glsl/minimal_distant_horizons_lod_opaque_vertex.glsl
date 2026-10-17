#version 450
layout(set = 1, binding = 0) uniform texture2D LightmapTexture;
layout(set = 1, binding = 1) uniform sampler LightmapSampler;
struct DistantHorizonsLodVertex {
    uvec4 data;
};
layout(set = 0, binding = 0, std430) readonly buffer DistantHorizonsLodVertices {
    DistantHorizonsLodVertex vertices[];
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
layout(location = 0) out vec4 v_color;
layout(location = 1) flat out uvec2 v_light;
layout(location = 2) flat out uint v_material;
layout(location = 3) flat out uint v_normal;
layout(location = 4) out vec3 v_world_position;
layout(location = 5) out vec3 v_source_position;
layout(location = 7) out vec3 v_light_color;
layout(location = 8) out vec3 v_unlit_color;

vec3 dh_normal(uint normal) {
    if (normal == 0u) return vec3(0.0, -1.0, 0.0);
    if (normal == 1u) return vec3(0.0, 1.0, 0.0);
    if (normal == 2u) return vec3(0.0, 0.0, -1.0);
    if (normal == 3u) return vec3(0.0, 0.0, 1.0);
    if (normal == 4u) return vec3(-1.0, 0.0, 0.0);
    return vec3(1.0, 0.0, 0.0);
}

void main() {
    DistantHorizonsLodVertex vertex = vertices[gl_VertexIndex + int(model_offset_and_reserved.w)];
    int local_x = int(vertex.data.x & 0xffffu);
    int local_y = int(vertex.data.x >> 16u);
    int local_z = int(vertex.data.y & 0xffffu);
    if (local_x >= 32768) local_x -= 65536;
    if (local_y >= 32768) local_y -= 65536;
    if (local_z >= 32768) local_z -= 65536;
    uint micro = (vertex.data.y >> 16u) & 0xffu;
    float micro_x = (micro & 2u) != 0u ? -clip_micro_noise_earth.y
        : ((micro & 1u) != 0u ? clip_micro_noise_earth.y : 0.0);
    float micro_z = (micro & 32u) != 0u ? -clip_micro_noise_earth.y
        : ((micro & 16u) != 0u ? clip_micro_noise_earth.y : 0.0);
    vec3 base_local = vec3(local_x, local_y, local_z);
    vec3 local = base_local + vec3(micro_x, 0.0, micro_z);
    // Frozen's standard.vert keeps vertexWorldPos at the raw packed position;
    // the micro offset affects clip placement only.
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
    // Capture-only probe for the legacy DH Vulkan backend convention. The
    // private Rust-owned route normally relies on its explicit negative
    // viewport and leaves Y untouched; this sentinel isolates a possible
    // source-side clip inversion without changing source/shader-pack paths.
    if ((flags_and_noise.w & 1u) != 0u) clip.y = -clip.y;
    gl_Position = clip;
    v_color = vec4(
        float(vertex.data.z & 0xffu),
        float((vertex.data.z >> 8u) & 0xffu),
        float((vertex.data.z >> 16u) & 0xffu),
        float((vertex.data.z >> 24u) & 0xffu)
    ) / 255.0;
    // Frozen's DH standard.vert samples the lightmap per vertex and
    // multiplies that result into vertexColor before raster interpolation.
    // Keep the light result smooth instead of sampling one flat provoking
    // vertex in the fragment stage.
    float light_sky = float(vertex.data.w & 0x0fu);
    float light_sky_uv = (light_sky + 0.5) / 16.0;
    // Frozen OpenGL samples the original sky coordinate. Its Java Vulkan-only
    // fold is not part of this semantic contract: folding dark rows upward
    // incorrectly brightens submerged terrain and covered side faces.
    vec2 light_uv = vec2(
        // The compact Rust-owned stream stores one byte per channel:
        // sky, block, material, normal. Do not decode the block channel as
        // the unused high nibble of the sky byte.
        (float((vertex.data.w >> 8u) & 0x0fu) + 0.5) / 16.0,
        light_sky_uv
    );
    v_unlit_color = v_color.rgb;
    v_light_color = texture(sampler2D(LightmapTexture, LightmapSampler), light_uv).rgb;
    v_color.rgb *= v_light_color;
    v_light = uvec2(
        vertex.data.w & 0xffu,
        (vertex.data.w >> 8u) & 0xffu
    );
    v_material = (vertex.data.w >> 16u) & 0xffu;
    v_normal = (vertex.data.w >> 24u) & 0xffu;
    v_world_position = world;
    // Frozen's noise shader receives the raw column-local position (`vPos`),
    // not an absolute world position reconstructed by subtracting a large
    // column origin in the fragment stage. Preserve that source precision
    // and derivative domain explicitly; absolute world coordinates remain
    // available separately for fog, fade, and depth semantics.
    v_source_position = base_local;
}
