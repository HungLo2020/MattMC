#version 450
layout(set = 0, binding = 0) uniform texture2D Tex0;
layout(set = 0, binding = 5) uniform sampler Samp0;
layout(set = 0, binding = 6, std430) readonly buffer ShaderCompositeUniforms {
    mat4 light_view_projection;
    vec4 shadow_params;
    vec4 color_grade_params;
    mat4 projection_inverse;
    vec4 fog_color_and_environmental_start;
    vec4 fog_ranges;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv);
    out_color = vec4(color.rgb + vec3(shadow_params.x * 0.0), color.a);
}
