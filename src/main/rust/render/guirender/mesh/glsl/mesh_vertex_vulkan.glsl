#version 450
layout(set = 0, binding = 0, std430) readonly buffer GuiMeshVertices { vec4 vertex_words[]; };
layout(set = 0, binding = 1, std140) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) out vec3 v_normal;
void main() {
    int base = gl_VertexIndex * 3;
    vec4 position_u = vertex_words[base];
    vec4 uv_color_rg = vertex_words[base + 1];
    vec4 color_ba_normal = vertex_words[base + 2];
    float top_left_y = 1.0 - (position_u.y / raster_extent.y) * 2.0;
    // The copied vanilla PIP projection above is authored in OpenGL's
    // [-1, 1] clip-depth convention. Vulkan consumes [0, 1], so convert it
    // after preserving the same negative model-space Z scale.
    float vanilla_pip_clip_depth = -position_u.z / 1000.0;
    gl_Position = vec4((position_u.x / raster_extent.x) * 2.0 - 1.0, top_left_y, vanilla_pip_clip_depth * 0.5 + 0.5, 1.0);
    v_uv = vec2(position_u.w, uv_color_rg.x);
    v_color = vec4(uv_color_rg.y, uv_color_rg.z, uv_color_rg.w, color_ba_normal.x);
    v_normal = color_ba_normal.yzw;
}
