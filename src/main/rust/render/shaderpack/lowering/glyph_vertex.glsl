// The world-glyph stream owns the same four CPU-semantic vertices as compact
// material quads. Derive the glyph's face metadata from those vertices, rather
// than importing Iris's GLYPH layout or inventing terrain attributes.
VulkanicSourceTexturedMaterialVertex vulkanic_glyph_corner(int corner) {
    return vulkanic_source_textured_vertices[(gl_VertexIndex / 6) * 4 + corner];
}
vec4 vulkanic_glyph_norm_i8(vec4 value) {
    return vec4(ivec4(clamp(value, -1.0, 1.0) * 127.0)) / 127.0;
}
vec3 vulkanic_glyph_face_normal() {
    return normalize(cross(
        vulkanic_glyph_corner(2).position.xyz - vulkanic_glyph_corner(0).position.xyz,
        vulkanic_glyph_corner(3).position.xyz - vulkanic_glyph_corner(1).position.xyz));
}
vec4 vulkanic_glyph_mid_uv() {
    vec2 uv = vec2(0.0);
    for (int i = 0; i < 4; ++i) uv += vulkanic_glyph_corner(i).texture_uv_lightmap.xy;
    return vec4(uv * 0.25, 0.0, 1.0);
}
vec4 vulkanic_glyph_tangent() {
    vec3 edge1 = vulkanic_glyph_corner(1).position.xyz - vulkanic_glyph_corner(0).position.xyz;
    vec3 edge2 = vulkanic_glyph_corner(2).position.xyz - vulkanic_glyph_corner(0).position.xyz;
    vec2 uv1 = vulkanic_glyph_corner(1).texture_uv_lightmap.xy - vulkanic_glyph_corner(0).texture_uv_lightmap.xy;
    vec2 uv2 = vulkanic_glyph_corner(2).texture_uv_lightmap.xy - vulkanic_glyph_corner(0).texture_uv_lightmap.xy;
    float determinant = uv1.x * uv2.y - uv2.x * uv1.y;
    float scale = determinant == 0.0 ? 1.0 : 1.0 / determinant;
    vec3 tangent = normalize(scale * (uv2.y * edge1 - uv1.y * edge2));
    vec3 bitangent = normalize(scale * (-uv2.x * edge1 + uv1.x * edge2));
    float handedness = dot(bitangent, cross(tangent, vulkanic_glyph_face_normal())) < 0.0 ? -1.0 : 1.0;
    return vulkanic_glyph_norm_i8(vec4(tangent, handedness));
}
#undef vulkanic_source_normal
#define vulkanic_source_normal vulkanic_glyph_norm_i8(vec4(vulkanic_glyph_face_normal(), 0.0)).xyz
#define vulkanic_source_mid_tex_coord vulkanic_glyph_mid_uv()
#define vulkanic_source_tangent vulkanic_glyph_tangent()
