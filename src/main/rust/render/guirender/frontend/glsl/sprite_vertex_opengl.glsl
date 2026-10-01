#version 430 core
struct PackedGuiQuad {
    vec4 origin_axis_u;
    vec4 axis_v_mode;
    vec4 viewport;
    vec4 clip;
    vec4 uv_region;
    vec4 color;
};
layout(std140, binding = 0) uniform GuiSpriteBatch {
    PackedGuiQuad sprites[256];
};
out vec2 v_uv;
out vec2 v_sprite_corner;
out vec2 v_pixel;
out vec4 v_color;
flat out vec4 v_uv_region;
flat out vec4 v_clip;
flat out float v_texture_mode;
flat out float v_clip_enabled;
const vec2 corner[6] = vec2[6](
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0),
    vec2(0.0, 0.0)
);
void main() {
    int vertex = gl_VertexID;
    PackedGuiQuad sprite = sprites[gl_InstanceID];
    vec2 pixel = sprite.origin_axis_u.xy + corner[vertex].x * sprite.origin_axis_u.zw + corner[vertex].y * sprite.axis_v_mode.xy;
    // Match the explicit orthographic matrix coefficients. Dividing each
    // vertex first changes rounding at minified texture sample boundaries.
    float top_left_y = pixel.y * (-2.0 / sprite.viewport.y) + 1.0;
    float ndc_y = mix(top_left_y, -top_left_y, sprite.viewport.w);
    vec2 ndc = vec2(pixel.x * (2.0 / sprite.viewport.x) - 1.0, ndc_y);
    gl_Position = vec4(ndc, sprite.axis_v_mode.w, 1.0);
    v_uv_region = sprite.uv_region;
    v_clip = sprite.clip;
    v_clip_enabled = sprite.viewport.z;
    v_texture_mode = sprite.axis_v_mode.z;
    v_sprite_corner = corner[vertex];
    v_pixel = pixel;
    v_uv = vec2(
        sprite.uv_region.x + corner[vertex].x * sprite.uv_region.z,
        sprite.uv_region.y + corner[vertex].y * sprite.uv_region.w
    );
    v_color = sprite.color;
}
