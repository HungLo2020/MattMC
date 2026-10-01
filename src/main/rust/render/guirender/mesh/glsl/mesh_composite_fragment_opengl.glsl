#version 430 core
layout(binding = 1) uniform sampler2D Sampler0;
layout(std140, binding = 0) uniform GuiMeshComposite { vec4 pose_linear; vec4 pose_translation_viewport; vec4 bounds; vec4 uv_region; vec4 clip_rect; };
in vec2 v_uv;
in vec2 v_pixel;
out vec4 out_color;
void main() {
    if (v_pixel.x < clip_rect.x || v_pixel.y < clip_rect.y || v_pixel.x >= clip_rect.z || v_pixel.y >= clip_rect.w) discard;
    vec4 color = texture(Sampler0, v_uv);
    if (color.a <= 0.0) discard;
    out_color = color;
}
