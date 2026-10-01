#version 450
layout(set = 0, binding = 2) uniform texture2D TerrainAtlasColor;
layout(set = 0, binding = 3) uniform sampler TerrainAtlasSampler;
#ifdef VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT
// Rust-owned semantic ColoredVoxelLighting resources. These use a second
// ordinary GAL resource set rather than any Iris or backend-private state.
// The matching layout is created by TerrainVoxelLightSamplingResources.
layout(set = 1, binding = 0) uniform utexture3D TerrainVoxelOccupancy;
layout(set = 1, binding = 1) uniform texture3D TerrainColoredVoxelLight;
layout(set = 1, binding = 2) uniform sampler TerrainVoxelLightSampler;
layout(set = 1, binding = 3, std140) uniform TerrainVoxelLightMapping {
    vec4 scene_to_volume_offset_and_normal_offset;
    vec4 scene_to_volume_scale;
    vec4 inverse_extent;
    ivec4 valid_world_min;
    ivec4 valid_world_max_exclusive;
    ivec4 camera_cell;
};
#endif
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 3) in vec3 v_normal;
layout(location = 4) in vec2 v_light;
layout(location = 5) in vec3 v_world_position;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
layout(location = 0) out vec4 out_terrain_lit_color;
layout(location = 1) out vec4 out_terrain_view_space_normal;
layout(location = 2) out vec4 out_terrain_material_auxiliary;
layout(location = 3) out vec4 out_world_position;

float complementary_block_light(float value) {
    float steep = pow(value * value, 4.0) * 3.8;
    float calm = value * 1.8;
    return pow(steep + calm, 2.25);
}

#ifdef VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT
vec3 terrain_voxel_coordinate(vec3 world_position, vec3 surface_normal) {
    vec3 shifted_world = world_position
        + surface_normal * scene_to_volume_offset_and_normal_offset.w;
    return (shifted_world - vec3(camera_cell.xyz)
        + scene_to_volume_offset_and_normal_offset.xyz)
        * scene_to_volume_scale.xyz
        * inverse_extent.xyz;
}
#endif

void main() {
    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;
    vec4 sampled_atlas_color = texture(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), sample_uv);
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        sampled_atlas_color = mix(
            sampled_atlas_color,
            texture(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), next_uv),
            clamp(v_material.z, 0.0, 1.0)
        );
    }
    // gbuffers_terrain: if (color.a <= 0.00001) discard;
    if (sampled_atlas_color.a <= 0.00001) discard;

    // gbuffers_terrain: color.rgb *= glColor.rgb;
    vec3 tint_result = sampled_atlas_color.rgb * v_color.rgb;
    float raw_ao = clamp(v_color.a, 0.0, 1.0);
    vec3 normal = normalize(v_normal);
    float directional_shade = 0.75 + 0.25 * max(normal.y, 0.0);
    float block_light = complementary_block_light(clamp(v_light.x, 0.0, 1.0));
    float sky_light = clamp(v_light.y, 0.0, 1.0);
    vec3 scene_lighting = vec3(0.18 + 0.82 * sky_light);
#ifdef VULKANIC_TERRAIN_COLORED_VOXEL_LIGHT
    vec3 voxel_coordinate = terrain_voxel_coordinate(v_world_position, normal);
    bool voxel_coordinate_in_bounds = all(greaterThanEqual(voxel_coordinate, vec3(0.0)))
        && all(lessThanEqual(voxel_coordinate, vec3(1.0)));
    // The occupancy lookup keeps the material field in the semantic contract
    // and makes a missing/incorrect integer D3 binding visible rather than
    // silently using only the flood-fill texture.
    uint voxel_occupancy = voxel_coordinate_in_bounds
        ? texture(usampler3D(TerrainVoxelOccupancy, TerrainVoxelLightSampler), voxel_coordinate).r
        : 0u;
    vec3 colored_voxel_light = voxel_coordinate_in_bounds && voxel_occupancy != 0u
        ? texture(sampler3D(TerrainColoredVoxelLight, TerrainVoxelLightSampler), voxel_coordinate).rgb
        : vec3(0.0);
#else
    vec3 colored_voxel_light = vec3(0.0);
#endif
    vec3 final_diffuse = sqrt(max(
        vec3(raw_ao * directional_shade * directional_shade) *
        (vec3(block_light) + scene_lighting * scene_lighting + colored_voxel_light),
        vec3(0.0)
    ));
    vec4 terrain_lit_color = vec4(tint_result * final_diffuse, sampled_atlas_color.a);
    // gbuffers_terrain writes this only when its reflection profile is on.
    // The output is still pass-local and does not require implementing the
    // later deferred reflection consumer.
    float sky_light_factor = pow(max(sky_light - 0.7, 0.0) * 3.33333, 2.0);

    out_terrain_lit_color = terrain_lit_color;
    out_terrain_material_auxiliary = vec4(0.0, 0.0, sky_light_factor, 1.0);
    out_terrain_view_space_normal = vec4(normal, 1.0);
    out_world_position = vec4(v_world_position, terrain_lit_color.a);
}
