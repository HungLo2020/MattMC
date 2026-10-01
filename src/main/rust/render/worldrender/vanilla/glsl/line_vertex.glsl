#version 450
struct LineSegment {
    vec4 start;
    vec4 end;
    vec4 color;
};
layout(set = 0, binding = 0, std140) uniform WorldLineBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport;
    LineSegment segments[512];
};
layout(location = 0) flat out vec4 v_color;
void main() {
    int segment = gl_VertexIndex / 6;
    int corner = gl_VertexIndex - segment * 6;
    int endpoint = (corner == 0 || corner == 3 || corner == 5) ? 0 : 1;
    float side = (corner == 0 || corner == 1 || corner == 5) ? -1.0 : 1.0;
    vec3 start = segments[segment].start.xyz * (1.0 - (1.0 / 256.0));
    vec3 end = segments[segment].end.xyz * (1.0 - (1.0 / 256.0));
    vec4 start_clip = projection * view * vec4(start, 1.0);
    vec4 end_clip = projection * view * vec4(end, 1.0);
    vec2 start_ndc = start_clip.xy / start_clip.w;
    vec2 end_ndc = end_clip.xy / end_clip.w;
    vec2 screen_delta = (end_ndc - start_ndc) * viewport.xy;
    float length_px = max(length(screen_delta), 0.0001);
    vec2 normal = vec2(-screen_delta.y, screen_delta.x) / length_px;
    float width_px = max(segments[segment].start.w, 1.0);
    vec2 offset_ndc = normal * (width_px / viewport.xy) * side;
    vec4 clip = endpoint == 0 ? start_clip : end_clip;
    clip.xy += offset_ndc * clip.w;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_color = segments[segment].color;
}
