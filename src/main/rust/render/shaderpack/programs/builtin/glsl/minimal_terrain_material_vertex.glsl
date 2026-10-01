#version 450
layout(set = 1, binding = 0) uniform texture2D LightmapTexture;
layout(set = 1, binding = 1) uniform sampler LightmapSampler;
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
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
layout(location = 3) out vec3 v_normal;
layout(location = 4) out vec2 v_light;
layout(location = 5) out vec3 v_world_position;
layout(location = 6) flat out vec4 v_animation_region;
layout(location = 7) flat out vec4 v_animation_next_region;
layout(location = 8) flat out vec4 v_overlay_color;
// Frozen's terrain shader interpolates distances and resolves the non-linear
// clamped fog function in the fragment shader.  Never interpolate the final
// fog factor: that changes the ramp across large (especially translucent)
// terrain triangles.
layout(location = 9) out vec2 v_fog_distances;
layout(location = 10) flat out vec4 v_fog_color_and_environmental_start;
layout(location = 11) flat out vec4 v_fog_ranges;
layout(location = 12) flat out uint v_terrain_material_bits;
// 13 is foil strength; 14 is the optional clip diagnostic.
layout(location = 15) out vec4 v_back_color;
#ifdef VULKANIC_STANDARD_ITEM_FOIL
// Two affine rows (xy basis, z translation) and RGB strength. Padding is
// explicit; no reuse of terrain animation fields or packed vertex alpha.
struct ItemFoilInstance { vec4 uv_row0; vec4 uv_row1; vec4 parameters; };
layout(set = 0, binding = 4, std430) readonly buffer ItemFoilInstances {
    ItemFoilInstance foil_instances[];
};
layout(location = 13) flat out float v_foil_strength;
#endif
void main() {
    MeshVertex vertex = vertices[gl_VertexIndex];
    MeshInstance instance = instances[gl_InstanceIndex];
    vec4 world = instance.model * vec4(vertex.position_uv.xyz, 1.0);
    vec4 clip = projection * view * world;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    // A copied world-mesh vertex carries both its local sprite coordinate and
    // its resolved terrain-atlas coordinate.  The submitted semantic section
    // declares which coordinate space its owned texture uses; do not make a
    // backend infer that from a native texture object.  This keeps standalone
    // water sprites local while atlas-backed terrain (including glass) samples
    // its resolved atlas region.
    // Independent semantic flags, encoded exactly in the Rust-owned ABI lane.
    uint material_semantics = uint(instance.material.w);
    v_uv = (material_semantics & 1u) != 0u
        ? vertex.shader_data.xy
        : vec2(vertex.position_uv.w, vertex.color_uv.w);
    v_uv = v_uv * instance.texture_transform.xy
        + vec2(instance.texture_transform.z,
            (uint(instance.material.w) & 1024u) != 0u ? 0.0 : instance.texture_transform.w);
    // Frozen OpenGL Sodium preserves UV2 byte coordinates (including smooth
    // lighting's fractional levels) and divides by 256 in chunk_vertex.glsl.
    // The copied vertex lane is byte/240; rescale without nibble truncation.
    // LightTexture is linear,
    // so this deliberately samples at `level / 16`, including the boundary
    // interpolation between neighbouring lightmap texels. Do not replace it
    // with texel-centre coordinates: that is a different lighting contract.
    // The resulting lit vertex color is then interpolated across the triangle.
    vec2 light_coordinates = vertex.extra_data.xy;
#ifdef VULKANIC_STANDARD_ITEM_FOIL
    ItemFoilInstance foil = foil_instances[gl_InstanceIndex];
    vec3 original_uv = vec3(vertex.position_uv.w, vertex.color_uv.w, 1.0);
    v_uv = vec2(dot(foil.uv_row0.xyz, original_uv), dot(foil.uv_row1.xyz, original_uv));
    v_foil_strength = foil.parameters.x;
    // Frozen glint consumes ColorModulator, not baked tint or lightmap.
    v_color = instance.color;
    v_back_color = v_color;
#else
    if ((material_semantics & 1024u) != 0u) {
        uint packed_instance_light = floatBitsToUint(instance.texture_transform.w);
        light_coordinates = vec2(
            float(packed_instance_light & 0xffu) / 240.0,
            float((packed_instance_light >> 16u) & 0xffu) / 240.0);
    }
    vec2 light_uv = clamp(light_coordinates, vec2(0.0), vec2(255.0 / 240.0)) * (15.0 / 16.0);
    // Frozen's standalone baked blocks use core/terrain.vsh, not Sodium's
    // chunk shader. Its half-texel offset samples the lightmap texel centres.
    if ((material_semantics & 8u) != 0u) {
        light_uv += vec2(0.5 / 16.0);
    }
    vec4 light_color = (material_semantics & (128u | 512u)) != 0u
        ? vec4(1.0)
        : texture(sampler2D(LightmapTexture, LightmapSampler), light_uv);
    if ((material_semantics & 16u) != 0u) {
        ivec2 light_texel = ivec2(round(clamp(light_coordinates, vec2(0.0), vec2(1.0)) * 15.0));
        light_color = texelFetch(sampler2D(LightmapTexture, LightmapSampler), light_texel, 0);
    }
    v_color = vec4(vertex.color_uv.rgb, vertex.normal_light.w) * instance.color
        * light_color;
    v_back_color = v_color;
    if ((material_semantics & 2u) != 0u && (material_semantics & (128u | 256u)) == 0u) {
        // Frozen's entity.vsh applies minecraft_mix_light before the copied
        // UV2 lightmap. ModelPart normals are semantic mesh data, so Rust
        // owns this calculation rather than borrowing Java's Lighting UBO.
        const vec3 LIGHT0_DIRECTION = normalize(vec3(0.2, 1.0, -0.7));
        const vec3 LIGHT1_DIRECTION = normalize(vec3(-0.2, 1.0, 0.7));
        const vec3 NETHER_LIGHT1_DIRECTION = normalize(vec3(-0.2, -1.0, 0.7));
        // Mesh normals are model-local, just like positions. Frozen's baked
        // encoder transforms them by the pose normal matrix before lighting;
        // the separate frame view matrix is not part of that pose operation.
        vec3 normal = normalize(transpose(inverse(mat3(instance.model)))
            * vec3(vertex.normal_light.yz, vertex.extra_data.z));
        bool quantize_normal = (material_semantics & 64u) != 0u;
#ifdef VULKANIC_MODEL_TRANSLUCENT_CUTOUT
        quantize_normal = true;
#endif
        // Frozen's entity and baked encoders pack the final transformed normal.
        // Quantize once and do not renormalize the resulting vertex attribute.
        if (quantize_normal) {
            normal = trunc(clamp(normal, vec3(-1.0), vec3(1.0)) * 127.0) / 127.0;
        }
        vec2 light = max(vec2(0.0), vec2(
            dot(LIGHT0_DIRECTION, normal),
            dot((material_semantics & 4u) != 0u ? NETHER_LIGHT1_DIRECTION : LIGHT1_DIRECTION, normal)
        ));
        float diffuse = min(1.0, (light.x + light.y) * 0.6 + 0.4);
        vec2 back_light = max(vec2(0.0), vec2(
            dot(LIGHT0_DIRECTION, -normal),
            dot((material_semantics & 4u) != 0u ? NETHER_LIGHT1_DIRECTION : LIGHT1_DIRECTION, -normal)
        ));
        v_back_color.rgb *= min(1.0, (back_light.x + back_light.y) * 0.6 + 0.4);
        v_color.rgb *= diffuse;
    }
#endif
    v_material = instance.material;
    v_animation_region = instance.animation_region;
    v_animation_next_region = instance.animation_next_region;
    v_overlay_color = instance.overlay_color;
    v_normal = normalize(vec3(vertex.normal_light.yz, vertex.extra_data.z));
    v_light = clamp(light_coordinates, vec2(0.0), vec2(1.0));
    v_terrain_material_bits = uint(clamp(vertex.extra_data.w, 0.0, 255.0));
    // Frozen's terrain vertex shader resolves both fog distances from
    // `Position + ModelOffset` before applying ModelViewMat.  `world` is this
    // explicit camera-relative semantic position.  Euclidean distance would
    // survive a view rotation, but Sodium's cylindrical distance would not;
    // using `view * world` here makes camera pitch alter the fog ramp.
    vec3 fog_position = world.xyz;
    v_fog_distances = vec2(
        length(fog_position),
        max(length(fog_position.xz), abs(fog_position.y))
    );
    v_fog_color_and_environmental_start = fog_color_and_environmental_start;
    v_fog_ranges = fog_ranges;
    float shadow_range = max(shadow_params.w, 1.0);
    v_world_position = world.xyz / shadow_range * 0.5 + 0.5;
}
