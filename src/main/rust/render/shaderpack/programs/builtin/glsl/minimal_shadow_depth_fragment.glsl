#version 450
layout(set = 0, binding = 2) uniform texture2D Tex0;
layout(set = 0, binding = 3) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 6) flat in vec4 v_animation_region;
layout(location = 7) flat in vec4 v_animation_next_region;
// The shadow target carries explicit color attachments as part of the
// backend-neutral pass contract. Keep those writes explicit even though the
// depth result is the semantic output consumed by lighting.
layout(location = 0) out vec4 out_shadow_color;
layout(location = 1) out vec4 out_light_shaft;
void main() {
    vec2 sample_uv = v_animation_region.xy + v_uv * v_animation_region.zw;
    vec4 color = texture(sampler2D(Tex0, Samp0), sample_uv);
    if (v_material.y > 0.5) {
        vec2 next_uv = v_animation_next_region.xy + v_uv * v_animation_next_region.zw;
        vec4 next_color = texture(sampler2D(Tex0, Samp0), next_uv);
        color = mix(color, next_color, clamp(v_material.z, 0.0, 1.0));
    }
    color *= v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
        discard;
    }
    out_shadow_color = vec4(0.0);
    out_light_shaft = vec4(0.0);
    gl_FragDepth = gl_FragCoord.z;
}
