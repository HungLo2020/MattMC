#version 450
layout(set = 0, binding = 0, std430) readonly buffer GuiMeshVertices { vec4 vertex_words[]; };
layout(set = 0, binding = 1, std140) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
layout(location = 0) out vec3 v_ray;
void main() {
    int base = gl_VertexIndex * 3;
    vec4 position_u = vertex_words[base];
    vec4 uv_color_rg = vertex_words[base + 1];
    float top_left_y = 1.0 - (position_u.y / raster_extent.y) * 2.0;
    gl_Position = vec4((position_u.x / raster_extent.x) * 2.0 - 1.0, top_left_y, 0.5, 1.0);
    v_ray = vec3(position_u.z, position_u.w, uv_color_rg.x);
}
