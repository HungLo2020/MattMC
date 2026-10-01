#version 430 core
layout(binding = 2) uniform sampler2D Sampler0;
in vec3 v_ray;
out vec4 out_color;
void main() {
    vec3 ray = normalize(v_ray);
    vec3 axis = abs(ray);
    float face;
    float u;
    float v;
    if (axis.x >= axis.y && axis.x >= axis.z) {
        float scale = 0.5 / axis.x;
        face = ray.x > 0.0 ? 0.0 : 1.0;
        u = (ray.x > 0.0 ? -ray.z : ray.z) * scale + 0.5;
        v = -ray.y * scale + 0.5;
    } else if (axis.y >= axis.z) {
        float scale = 0.5 / axis.y;
        face = ray.y > 0.0 ? 2.0 : 3.0;
        u = ray.x * scale + 0.5;
        v = (ray.y > 0.0 ? ray.z : -ray.z) * scale + 0.5;
    } else {
        float scale = 0.5 / axis.z;
        face = ray.z > 0.0 ? 4.0 : 5.0;
        u = (ray.z > 0.0 ? ray.x : -ray.x) * scale + 0.5;
        v = -ray.y * scale + 0.5;
    }
    // Match Frozen's panorama fragment shader exactly: its continuous sampler
    // is allowed to filter at the stacked-face edge.  Insetting this lookup by
    // half a texel changes the title image at every cube-face boundary.
    vec2 atlas_uv = vec2(clamp(u, 0.0, 1.0), (face + clamp(v, 0.0, 1.0)) / 6.0);
    out_color = texture(Sampler0, atlas_uv);
}
