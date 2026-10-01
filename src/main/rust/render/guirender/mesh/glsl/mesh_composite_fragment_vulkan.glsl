#version 450
layout(set = 0, binding = 1) uniform texture2D GuiMeshColor;
layout(set = 0, binding = 2) uniform sampler GuiMeshColorSampler;
layout(set = 0, binding = 0, std140) uniform GuiMeshComposite { vec4 pose_linear; vec4 pose_translation_viewport; vec4 bounds; vec4 uv_region; vec4 clip_rect; };
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec2 v_pixel;
layout(location = 0) out vec4 out_color;
void main() {
    if (v_pixel.x < clip_rect.x || v_pixel.y < clip_rect.y || v_pixel.x >= clip_rect.z || v_pixel.y >= clip_rect.w) discard;
    vec4 color = texture(sampler2D(GuiMeshColor, GuiMeshColorSampler), v_uv);
    if (color.a <= 0.0) discard;
    out_color = color;
}
