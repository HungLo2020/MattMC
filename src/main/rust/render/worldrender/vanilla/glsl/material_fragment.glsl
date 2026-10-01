#version 450
layout(set = 0, binding = 1) uniform texture2D Tex0;
layout(set = 0, binding = 2) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 3) in float v_camera_distance;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv) * v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
    // Vanilla's `rendertype_clouds.fsh` fades the already-modulated cloud
    // alpha by linear spherical fog from zero to `FogCloudsEnd`. This stays
    // a cloud-family semantic; weather and generic translucent materials do
    // not inherit it merely because they use the same private quad stream.
    if (v_material.y > 0.0) {
        color.a *= 1.0 - clamp(v_camera_distance / v_material.y, 0.0, 1.0);
    }
    out_color = color;
}
