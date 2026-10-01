#version 450
layout(set = 0, binding = 1) uniform texture2D Tex0;
layout(set = 0, binding = 2) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec2 v_sprite_corner;
layout(location = 2) in vec2 v_pixel;
layout(location = 3) in vec4 v_color;
layout(location = 4) flat in vec4 v_uv_region;
layout(location = 5) flat in vec4 v_clip;
layout(location = 6) flat in float v_texture_mode;
layout(location = 7) flat in float v_clip_enabled;
layout(location = 0) out vec4 out_color;
void main() {
    if (v_clip_enabled > 0.5 && (v_pixel.x < v_clip.x || v_pixel.y < v_clip.y || v_pixel.x >= v_clip.z || v_pixel.y >= v_clip.w)) {
        discard;
    }
    // The explicit resource set owns filtering/addressing. Reconstructing a
    // texel with floor() bypasses that contract and changes minification ties.
    vec4 sampled = texture(sampler2D(Tex0, Samp0), v_uv);
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
