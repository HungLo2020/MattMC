#version 450
layout(set = 0, binding = 2) uniform texture2D GuiMeshTexture;
layout(set = 0, binding = 3) uniform sampler GuiMeshSampler;
layout(set = 0, binding = 1, std140) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) in vec3 v_normal;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(GuiMeshTexture, GuiMeshSampler), v_uv) * v_color;
    if (color.a <= raster_extent.z) discard;
    if (raster_extent.w > 0.5) {
        vec3 normal = raster_extent.w > 1.5 ? v_normal : normalize(v_normal);
        if (raster_extent.w > 2.5 && !gl_FrontFacing) normal = -normal;
        vec2 light = max(vec2(0.0), vec2(dot(light0.xyz, normal), dot(light1.xyz, normal)));
        color.rgb *= min(1.0, (light.x + light.y) * 0.6 + 0.4);
    }
    out_color = color;
}
