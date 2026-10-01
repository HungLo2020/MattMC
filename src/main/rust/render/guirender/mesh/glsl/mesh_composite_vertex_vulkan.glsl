#version 450
layout(set = 0, binding = 0, std140) uniform GuiMeshComposite {
    vec4 pose_linear;
    vec4 pose_translation_viewport;
    vec4 bounds;
    vec4 uv_region;
    vec4 clip_rect;
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec2 v_pixel;
const vec2 corner[6] = vec2[6](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
    vec2(1.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 0.0)
);
void main() {
    vec2 local = mix(bounds.xy, bounds.zw, corner[gl_VertexIndex]);
    vec2 pixel = vec2(
        pose_linear.x * local.x + pose_linear.z * local.y + pose_translation_viewport.x,
        pose_linear.y * local.x + pose_linear.w * local.y + pose_translation_viewport.y
    );
    gl_Position = vec4(
        (pixel.x / pose_translation_viewport.z) * 2.0 - 1.0,
        1.0 - (pixel.y / pose_translation_viewport.w) * 2.0,
        0.0,
        1.0
    );
    v_pixel = pixel;
    // Keep the semantic Standard3dItemRenderer PIP orientation identical on
    // Vulkan and OpenGL. Backend coordinate conversion ends at rasterization;
    // the sampled owned image is not a generic GUI blit source.
    v_uv = uv_region.xy + corner[gl_VertexIndex] * uv_region.zw;
}
