#version 430 core
layout(binding = 1) uniform sampler2D Sampler0;
in vec2 v_uv;
in vec2 v_sprite_corner;
in vec2 v_pixel;
in vec4 v_color;
flat in vec4 v_uv_region;
flat in vec4 v_clip;
flat in float v_texture_mode;
flat in float v_clip_enabled;
out vec4 out_color;
void main() {
    if (v_clip_enabled > 0.5 && (v_pixel.x < v_clip.x || v_pixel.y < v_clip.y || v_pixel.x >= v_clip.z || v_pixel.y >= v_clip.w)) {
        discard;
    }
    // The explicit resource set owns filtering/addressing. Reconstructing a
    // texel with floor() bypasses that contract and changes minification ties.
    vec4 sampled = texture(Sampler0, v_uv);
#ifdef GUI_ITEM_CUTOUT
    if (sampled.a < 0.1) discard;
#endif
    vec4 color = (v_texture_mode < 0.5 ? vec4(1.0, 1.0, 1.0, sampled.r) : sampled) * v_color;
    if (color.a <= 0.0) {
        discard;
    }
#ifdef GUI_ITEM_RASTER
    if (color.a < 0.1) discard;
#endif
    out_color = color;
}
