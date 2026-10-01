#version 430 core
layout(binding = 2) uniform sampler2D Sampler0;
layout(std140, binding = 1) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
in vec2 v_uv;
in vec4 v_color;
in vec3 v_normal;
out vec4 out_color;
void main() {
    vec4 color = texture(Sampler0, v_uv) * v_color;
    if (color.a <= raster_extent.z) discard;
    if (raster_extent.w > 0.5) {
        vec3 normal = raster_extent.w > 1.5 ? v_normal : normalize(v_normal);
        if (raster_extent.w > 2.5 && !gl_FrontFacing) normal = -normal;
        vec2 light = max(vec2(0.0), vec2(dot(light0.xyz, normal), dot(light1.xyz, normal)));
        color.rgb *= min(1.0, (light.x + light.y) * 0.6 + 0.4);
    }
    out_color = color;
}
