#version 450
const vec2 positions[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
layout(location = 0) out vec2 v_uv;
void main() {
    vec2 position = positions[gl_VertexIndex];
    gl_Position = vec4(position, 0.0, 1.0);
    v_uv = position * 0.5 + 0.5;
#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y
    v_uv.y = 1.0 - v_uv.y;
#endif
}
