#version 450
#ifdef VULKANIC_GAL_PARTICLE_LIGHTMAP
layout(set = 1, binding = 0) uniform texture2D LightmapTex;
layout(set = 1, binding = 1) uniform sampler LightmapSamp;
#endif
struct GenericBox {
    vec4 min_corner;
    vec4 max_corner;
    uvec4 colors_0_3;
    uvec4 colors_4_5_light;
};
layout(set = 0, binding = 0, std430) readonly buffer WorldDhGenericBoxes {
    mat4 view;
    mat4 projection;
    vec4 viewport_cutout;
    GenericBox boxes[4096];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
layout(location = 3) out float v_camera_distance;
layout(location = 4) out vec2 v_lightmap_uv;
const vec2 corner[4] = vec2[4](vec2(0.0,0.0),vec2(1.0,0.0),vec2(1.0,1.0),vec2(0.0,1.0));
void main() {
    uint face = uint(gl_InstanceIndex) % 6u;
    GenericBox box = boxes[uint(gl_InstanceIndex) / 6u];
    vec3 a = box.min_corner.xyz;
    vec3 b = box.max_corner.xyz;
    vec3 p0; vec3 p1; vec3 p2; vec3 p3;
    if (face == 0u) { p0=vec3(b.x,b.y,a.z); p1=vec3(b.x,a.y,a.z); p2=vec3(a.x,a.y,a.z); p3=vec3(a.x,b.y,a.z); }
    else if (face == 1u) { p0=vec3(b.x,a.y,b.z); p1=vec3(b.x,b.y,b.z); p2=vec3(a.x,b.y,b.z); p3=vec3(a.x,a.y,b.z); }
    else if (face == 2u) { p0=vec3(a.x,b.y,a.z); p1=vec3(a.x,a.y,a.z); p2=vec3(a.x,a.y,b.z); p3=vec3(a.x,b.y,b.z); }
    else if (face == 3u) { p0=vec3(b.x,b.y,a.z); p1=vec3(b.x,b.y,b.z); p2=vec3(b.x,a.y,b.z); p3=vec3(b.x,a.y,a.z); }
    else if (face == 4u) { p0=vec3(a.x,a.y,a.z); p1=vec3(b.x,a.y,a.z); p2=vec3(b.x,a.y,b.z); p3=vec3(a.x,a.y,b.z); }
    else { p0=vec3(a.x,b.y,b.z); p1=vec3(b.x,b.y,b.z); p2=vec3(b.x,b.y,a.z); p3=vec3(a.x,b.y,a.z); }
    vec2 c = corner[gl_VertexIndex];
    vec3 position = mix(mix(p0,p1,c.x),mix(p3,p2,c.x),c.y);
    vec4 clip = projection * view * vec4(position, 1.0);
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_uv = c;
    uint color = face < 4u ? box.colors_0_3[face] : box.colors_4_5_light[face - 4u];
    v_color = vec4(float((color >> 16u) & 255u),float((color >> 8u) & 255u),float(color & 255u),float(color >> 24u)) / 255.0;
    uint packed_light = box.colors_4_5_light.z;
    v_lightmap_uv = vec2(float(packed_light & 255u),float((packed_light >> 16u) & 255u));
#ifdef VULKANIC_GAL_PARTICLE_LIGHTMAP
    v_color *= texelFetch(sampler2D(LightmapTex, LightmapSamp), ivec2(v_lightmap_uv) / 16, 0);
#endif
    v_material = vec4(viewport_cutout.z,viewport_cutout.w,0.0,0.0);
    v_camera_distance = length((view * vec4(position,1.0)).xyz);
}
