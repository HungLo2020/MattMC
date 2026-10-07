#version 430 core
layout(std430, binding = 0) readonly buffer GuiMeshVertices { vec4 vertex_words[]; };
layout(std140, binding = 1) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; vec4 uv_transform_u; vec4 uv_transform_v; };
out vec3 v_ray;
void main() {
    int base = gl_VertexID * 3;
    vec4 position_u = vertex_words[base];
    vec4 uv_color_rg = vertex_words[base + 1];
    float top_left_y = 1.0 - (position_u.y / raster_extent.y) * 2.0;
    gl_Position = vec4((position_u.x / raster_extent.x) * 2.0 - 1.0, top_left_y, 0.0, 1.0);
    v_ray = vec3(position_u.z, position_u.w, uv_color_rg.x);
}
