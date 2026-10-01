#version 430 core
layout(std430, binding = 0) readonly buffer GuiMeshVertices { vec4 vertex_words[]; };
layout(std140, binding = 1) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
out vec2 v_uv;
out vec4 v_color;
out vec3 v_normal;
void main() {
    int base = gl_VertexID * 3;
    vec4 position_u = vertex_words[base];
    vec4 uv_color_rg = vertex_words[base + 1];
    vec4 color_ba_normal = vertex_words[base + 2];
    float top_left_y = 1.0 - (position_u.y / raster_extent.y) * 2.0;
    // Standard3dItemRenderer's owned orthographic projection is
    // setOrtho(0, width, height, 0, -1000, 1000, false): model-space Z is
    // negated into OpenGL clip depth. Keep that convention in the OpenGL
    // lowering so the nearest item face wins its private depth test.
    gl_Position = vec4((position_u.x / raster_extent.x) * 2.0 - 1.0, top_left_y, -position_u.z / 1000.0, 1.0);
    v_uv = vec2(position_u.w, uv_color_rg.x);
    v_color = vec4(uv_color_rg.y, uv_color_rg.z, uv_color_rg.w, color_ba_normal.x);
    v_normal = color_ba_normal.yzw;
}
