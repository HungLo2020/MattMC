#version 450
layout(set = 0, binding = 0) uniform texture2D Source;
layout(set = 0, binding = 1) uniform sampler SourceSampler;
layout(set = 0, binding = 2, std140) uniform BitsConfig { float Resolution; float MosaicSize; };
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec2 size = vec2(textureSize(sampler2D(Source, SourceSampler), 0));
    vec2 mosaic = size / max(MosaicSize, 1.0);
    vec2 fract_pixel = fract(v_uv * mosaic) / mosaic;
    vec4 base = texture(sampler2D(Source, SourceSampler), v_uv - fract_pixel);
    vec3 quantized = base.rgb - fract(base.rgb * Resolution) / Resolution;
    float luma = dot(quantized, vec3(0.3, 0.59, 0.11));
    base.rgb = luma + (quantized - luma) * 1.5;
    out_color = vec4(base.rgb, 1.0);
}
