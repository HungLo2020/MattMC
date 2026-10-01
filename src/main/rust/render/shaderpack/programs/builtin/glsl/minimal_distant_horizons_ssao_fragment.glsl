#version 450
layout(set = 0, binding = 0) uniform texture2D DhDepthTexture;
layout(set = 0, binding = 1) uniform sampler DhDepthSampler;
layout(set = 0, binding = 2, std140) uniform DistantHorizonsSsao {
    mat4 projection_matrix;
    mat4 inverse_projection_matrix;
    vec4 ssao_parameters0;
    vec4 ssao_parameters1;
};
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;

const float EPSILON = 1.0e-6;
const float GOLDEN_ANGLE = 2.39996323;
const float PI = 3.1415926538;
const float TAU = PI * 2.0;
const vec3 MAGIC = vec3(0.06711056, 0.00583715, 52.9829189);

vec3 unproject(vec4 value) {
    return value.xyz / max(abs(value.w), 1.0e-6) * (value.w < 0.0 ? -1.0 : 1.0);
}

vec3 view_position(vec2 uv, float depth) {
    vec2 reconstruction_uv = uv;
#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y
    reconstruction_uv.y = 1.0 - reconstruction_uv.y;
#endif
    vec4 value = inverse_projection_matrix * vec4(reconstruction_uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return unproject(value);
}

vec2 project_uv(vec3 position) {
    vec4 clip = projection_matrix * vec4(position, 1.0);
    vec2 uv = clip.xy / max(abs(clip.w), 1.0e-6) * 0.5 + 0.5;
#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y
    uv.y = 1.0 - uv.y;
#endif
    return clamp(uv, vec2(0.0), vec2(1.0));
}

float interleaved_gradient_noise(vec2 pixel) {
    float x = dot(pixel, MAGIC.xy);
    return fract(MAGIC.z * fract(x));
}

void main() {
    float enabled = ssao_parameters0.x;
    float depth = textureLod(sampler2D(DhDepthTexture, DhDepthSampler), v_uv, 0.0).r;
    if (enabled < 0.5 || depth >= 1.0 - EPSILON || ssao_parameters0.z <= 0.0) {
        out_color = vec4(1.0);
        return;
    }
    int sample_count = clamp(int(ssao_parameters0.y + 0.5), 1, 64);
    float radius = ssao_parameters0.z;
    float strength = max(ssao_parameters0.w, 0.0);
    float min_light = clamp(ssao_parameters1.x, 0.0, 1.0);
    float bias = max(ssao_parameters1.y, 0.0);
    float fade_distance = max(ssao_parameters1.z, 0.0);
    vec3 view_pos = view_position(v_uv, depth);
    float distance_from_camera = length(view_pos);
    if (fade_distance <= 0.0 || distance_from_camera >= fade_distance) {
        out_color = vec4(1.0);
        return;
    }
    vec3 view_normal = normalize(cross(dFdxFine(view_pos), dFdyFine(view_pos)));
    float phase = interleaved_gradient_noise(gl_FragCoord.xy) * TAU;
    float radius_step = radius / float(sample_count);
    float current_radius = radius_step;
    float occlusion = 0.0;
    int valid_samples = 0;
    for (int i = 0; i < 64; i++) {
        if (i >= sample_count) break;
        vec2 offset = vec2(sin(phase), cos(phase)) * current_radius;
        phase += GOLDEN_ANGLE;
        current_radius += radius_step;
        vec3 sample_view_pos = view_pos + vec3(offset, -0.1);
        vec2 sample_uv = project_uv(sample_view_pos);
        float sample_depth = textureLod(sampler2D(DhDepthTexture, DhDepthSampler), sample_uv, 0.0).r;
        if (sample_depth >= 1.0 - EPSILON) continue;
        sample_view_pos = view_position(sample_uv, sample_depth);
        vec3 difference = sample_view_pos - view_pos;
        float sample_distance = length(difference);
        if (sample_distance <= EPSILON) continue;
        vec3 sample_normal = difference / sample_distance;
        float sample_no_lighting = max(dot(view_normal, sample_normal) - bias, 0.0);
        float attenuation = 1.0 - clamp(sample_distance / radius, 0.0, 1.0);
        occlusion += sample_no_lighting * attenuation;
        valid_samples++;
    }
    occlusion /= max(float(valid_samples), 1.0);
    occlusion = smoothstep(0.0, max(strength, EPSILON), occlusion);
    occlusion *= (1.0 - min_light);
    occlusion *= clamp((fade_distance - distance_from_camera) / fade_distance, 0.0, 1.0);
    out_color = vec4(vec3(1.0 - occlusion), 1.0);
}
