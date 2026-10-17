#version 450
#ifdef VULKANIC_GAL_PARTICLE_LIGHTMAP
layout(set = 1, binding = 0) uniform texture2D LightmapTex;
layout(set = 1, binding = 1) uniform sampler LightmapSamp;
#endif
struct MaterialQuad {
    vec4 p0;
    vec4 p1;
    vec4 p2;
    vec4 p3;
    vec4 uv0_uv1;
    vec4 uv2_uv3;
    vec4 color0;
    vec4 color1;
    vec4 color2;
    vec4 color3;
    vec4 light0_light1;
    vec4 light2_light3;
};
layout(set = 0, binding = 0, std430) readonly buffer WorldMaterialBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport_cutout;
#ifdef VULKANIC_GAL_SKY_FOG
    vec4 sky_fog_color;
#endif
#ifdef VULKANIC_GAL_MAP_TEXT
    vec4 map_fog_color;
    vec4 map_fog_ranges;
#endif
    MaterialQuad quads[4096];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
layout(location = 3) out float v_camera_distance;
layout(location = 4) out vec2 v_lightmap_uv;
#if defined(VULKANIC_GAL_SKY_FOG) || defined(VULKANIC_GAL_MAP_TEXT)
layout(location = 5) out float v_cylindrical_distance;
#endif
const vec2 corner[4] = vec2[4](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
);
void main() {
    MaterialQuad quad = quads[gl_InstanceIndex];
    int vertex = gl_VertexIndex;
    vec2 c = corner[vertex];
    vec3 top = mix(quad.p0.xyz, quad.p1.xyz, c.x);
    vec3 bottom = mix(quad.p3.xyz, quad.p2.xyz, c.x);
    vec3 position = mix(top, bottom, c.y);
#ifdef VULKANIC_GAL_SKY_FOG
    // Frozen computes fog distances from the local bottom fan (Y=-16),
    // before renderDarkDisc translates its ModelView by Y=12.
    vec3 local_sky_position = position;
    position.y += 12.0;
#endif
    vec2 uv_top = mix(quad.uv0_uv1.xy, quad.uv0_uv1.zw, c.x);
    vec2 uv_bottom = mix(quad.uv2_uv3.zw, quad.uv2_uv3.xy, c.x);
    // Sky and clouds are camera-relative; ordinary materials retain the
    // copied world view. Each family's local draw transform remains separate.
    mat4 material_view = view;
#ifdef VULKANIC_GAL_SKY_FOG
    material_view[3].xyz = vec3(0.0);
#else
    if (viewport_cutout.w > 0.0) {
        material_view[3].xyz = vec3(0.0);
    }
#endif
    vec4 camera_position = material_view * vec4(position, 1.0);
    vec4 clip = projection * camera_position;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_uv = mix(uv_top, uv_bottom, c.y);
#ifdef VULKANIC_GAL_MAP_UV_TOP_LEFT
    // Maps copy Minecraft UVs. The shared image upload converts top-left
    // rows, so normalize the consumer once; canonical assets need no change.
    v_uv.y = 1.0 - v_uv.y;
#endif
    vec4 color_top = mix(quad.color0, quad.color1, c.x);
    vec4 color_bottom = mix(quad.color3, quad.color2, c.x);
    v_color = mix(color_top, color_bottom, c.y);
    vec2 light_top = mix(quad.light0_light1.xy, quad.light0_light1.zw, c.x);
    vec2 light_bottom = mix(quad.light2_light3.xy, quad.light2_light3.zw, c.x);
    v_lightmap_uv = mix(light_top, light_bottom, c.y);
#ifdef VULKANIC_GAL_PARTICLE_LIGHTMAP
    // Frozen's particle/weather vertex program fetches UV2 here, before the
    // rasterizer interpolates vertexColor across a rain quad. Sampling this
    // in the fragment stage is observably different at light boundaries.
    v_color *= texelFetch(sampler2D(LightmapTex, LightmapSamp), ivec2(v_lightmap_uv) / 16, 0);
#endif
    // `viewport_cutout.w` is a cloud-only semantic range. Zero means this
    // ordinary material is not a vanilla cloud face. The distance is taken
    // after the copied camera transform, matching Frozen's
    // `fog_spherical_distance` input rather than a world-origin distance.
    v_material = vec4(viewport_cutout.z, viewport_cutout.w, 0.0, 0.0);
#ifdef VULKANIC_GAL_SKY_FOG
    v_camera_distance = length(local_sky_position);
    v_cylindrical_distance = max(length(local_sky_position.xz), abs(local_sky_position.y));
#else
    v_camera_distance = length(camera_position.xyz);
#ifdef VULKANIC_GAL_MAP_TEXT
    v_cylindrical_distance = max(length(camera_position.xz), abs(camera_position.y));
#endif
#endif
}
