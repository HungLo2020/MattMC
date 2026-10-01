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
    if (color.a < 0.5) {
        out_color = color;
        return;
    }
    float exposure = color_grade_params.x;
    vec3 lift = vec3(color_grade_params.y);
    vec3 graded = pow(max(color.rgb * exposure + lift, vec3(0.0)), vec3(color_grade_params.z));
    out_color = vec4(graded, color.a);
}
