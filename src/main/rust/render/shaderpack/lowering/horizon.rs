//! Owned geometry matching Frozen Iris's HorizonRenderer. The radius comes
//! from copied effective render distance in blocks, not projection clip far.

pub(super) const HORIZON_VERTEX_GEOMETRY: &str = r#"
vec4 vulkanic_source_fullscreen_horizon_position() {
    const int corners[6] = int[6](0, 1, 2, 2, 3, 0);
    int vertex = vulkanic_source_fullscreen_vertex_index();
    int quad = vertex / 6;
    int corner = corners[vertex % 6];
    if (quad < 8) {
        float radius = min(far, 256.0);
        float adjacent = radius * 0.9238795;
        float opposite = radius * 0.38268343;
        if (quad >= 4) { adjacent = -adjacent; opposite = -opposite; }
        vec2 first;
        vec2 second;
        int face = quad % 4;
        if (face == 0) { first = vec2(adjacent, -opposite); second = vec2(opposite, -adjacent); }
        else if (face == 1) { first = vec2(adjacent, opposite); second = vec2(adjacent, -opposite); }
        else if (face == 2) { first = vec2(opposite, adjacent); second = vec2(adjacent, opposite); }
        else { first = vec2(-opposite, adjacent); second = vec2(opposite, adjacent); }
        vec2 xz = corner < 2 ? first : second;
        return vec4(xz.x, corner == 0 || corner == 3 ? -16.0 : 16.0, xz.y, 1.0);
    }
    // Frozen tiles both planes inclusively from -384 through +384 in steps
    // of 64: 13 by 13 quads, with opposite winding on the bottom.
    int plane_quad = (quad - 8) % 169;
    bool bottom = quad >= 177;
    float x = -384.0 + float(plane_quad / 13) * 64.0;
    float z = -384.0 + float(plane_quad % 13) * 64.0;
    const vec2 top_offsets[4] = vec2[4](vec2(64,0), vec2(64,64), vec2(0,64), vec2(0,0));
    const vec2 bottom_offsets[4] = vec2[4](vec2(64,0), vec2(0,0), vec2(0,64), vec2(64,64));
    vec2 offset = bottom ? bottom_offsets[corner] : top_offsets[corner];
    return vec4(x + offset.x, bottom ? -16.0 : 16.0, z + offset.y, 1.0);
}
vec2 vulkanic_source_fullscreen_uv_coordinates() { return vec2(0.0); }
#define vulkanic_source_fullscreen_transform() (gbufferProjection * gbufferModelView * vulkanic_source_fullscreen_horizon_position())
"#;
