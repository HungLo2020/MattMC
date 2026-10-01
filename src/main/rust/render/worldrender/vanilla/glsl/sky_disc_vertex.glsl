#version 450
layout(set = 0, binding = 0, std140) uniform WorldSkyDisc {
    mat4 view;
    mat4 projection;
    vec4 color;
    vec4 fog_color;
    vec4 sky_end_padding;
};
const vec3 FAN[10] = vec3[](
    vec3(0.0, 16.0, 0.0),
    vec3(-512.0, 16.0, 0.0),
    vec3(-362.03867, 16.0, -362.03867),
    vec3(0.0, 16.0, -512.0),
    vec3(362.03867, 16.0, -362.03867),
    vec3(512.0, 16.0, 0.0),
    vec3(362.03867, 16.0, 362.03867),
    vec3(0.0, 16.0, 512.0),
    vec3(-362.03867, 16.0, 362.03867),
    vec3(-512.0, 16.0, 0.0)
);
layout(location = 0) out vec4 v_color;
// Frozen's core/sky.vsh leaves these as ordinary smooth varyings.  In
// particular, clipping this camera-relative fan retains GLSL's normal
// perspective-correct interpolation; do not substitute a backend-specific
// window-linear rule here.
layout(location = 1) out float v_spherical_distance;
layout(location = 2) out float v_cylindrical_distance;
void main() {
    vec3 position = FAN[gl_VertexIndex];
    // SkyRenderer builds the disc around the camera and uses Minecraft's
    // rotational ModelViewMat for it.  The semantic world view also carries
    // the camera translation for terrain, which must not move this
    // camera-relative primitive through its own fan.
    mat4 sky_view = view;
    sky_view[3].xyz = vec3(0.0);
    // GameRenderer supplies Minecraft's ordinary OpenGL clip-space
    // Matrix4f.perspective projection. VulkanicGAL owns the backend boundary,
    // so Vulkan lowers its -1..1 clip depth to 0..1 here; Java does not supply
    // a Vulkan-specific matrix or a native handle.
    vec4 clip = projection * sky_view * vec4(position, 1.0);
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    clip.z = clip.z * 0.5 + clip.w * 0.5;
#endif
    gl_Position = clip;
    v_color = color;
    v_spherical_distance = length(position);
    v_cylindrical_distance = max(length(position.xz), abs(position.y));
}
