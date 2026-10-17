//! Owned celestial local geometry, UVs and model transforms.

// Matrix inputs also exist in fragment shaders. Keep these independent of
// vertex indexing and preserve the local vertex/model-view distinction.
pub(super) const CELESTIAL_MODEL_TRANSFORM: &str = r#"
vec3 vulkanic_source_rotate_x(vec3 value, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec3(value.x, c * value.y - s * value.z, s * value.y + c * value.z);
}
vec3 vulkanic_source_rotate_y(vec3 value, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec3(c * value.x + s * value.z, value.y, -s * value.x + c * value.z);
}
vec3 vulkanic_source_rotate_z(vec3 value, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec3(c * value.x - s * value.y, s * value.x + c * value.y, value.z);
}
vec3 vulkanic_source_celestial_rotate(vec3 value) {
    value = vulkanic_source_rotate_z(value, radians(vulkanic_source_celestial_sun_path_rotation));
    // SkyRenderer rotates with the vanilla sky-clock fraction, not Iris's
    // sunAngle uniform (which shifts that clock by a quarter turn).
    value = vulkanic_source_rotate_x(value, vulkanic_source_celestial_time_of_day * 6.28318530718);
    return vulkanic_source_rotate_y(value, -1.57079632679);
}
mat4 vulkanic_source_celestial_model_view() {
    // End face rotations are baked into its CPU vertex mesh in Frozen.
    if (vulkanic_source_celestial_is_moon == 2) return gbufferModelView;
    bool moon = vulkanic_source_celestial_is_moon == 1;
    float size = moon ? 20.0 : 30.0;
    float height = moon ? -100.0 : 100.0;
    mat4 model = mat4(
        vec4(vulkanic_source_celestial_rotate(vec3(size, 0.0, 0.0)), 0.0),
        vec4(vulkanic_source_celestial_rotate(vec3(0.0, 1.0, 0.0)), 0.0),
        vec4(vulkanic_source_celestial_rotate(vec3(0.0, 0.0, size)), 0.0),
        vec4(vulkanic_source_celestial_rotate(vec3(0.0, height, 0.0)), 1.0)
    );
    return gbufferModelView * model;
}
"#;

pub(super) const CELESTIAL_VERTEX_GEOMETRY: &str = r#"
// SkyRenderer's indexed local quad. Scaling and translation belong to the
// model-view matrix; source reads of gl_Vertex must still see unit positions.
int vulkanic_source_celestial_corner() {
    const int triangle_corners[6] = int[6](0, 1, 2, 0, 2, 3);
    return triangle_corners[vulkanic_source_fullscreen_vertex_index() % 6];
}
int vulkanic_source_celestial_face() {
    return vulkanic_source_fullscreen_vertex_index() / 6;
}
bool vulkanic_source_celestial_end_sky() {
    return vulkanic_source_celestial_is_moon == 2;
}
vec4 vulkanic_source_fullscreen_celestial_position() {
    int face = vulkanic_source_celestial_face();
    if (vulkanic_source_celestial_end_sky()) {
        // SkyRenderer.buildEndSky: one +/-100 face per rotation.
        const vec3 box_corners[4] = vec3[4](
            vec3(-100.0, -100.0, -100.0), vec3(-100.0, -100.0, 100.0),
            vec3(100.0, -100.0, 100.0), vec3(100.0, -100.0, -100.0)
        );
        vec3 corner = box_corners[vulkanic_source_celestial_corner()];
        if (face == 1) corner = vulkanic_source_rotate_x(corner, 1.57079632679);
        else if (face == 2) corner = vulkanic_source_rotate_x(corner, -1.57079632679);
        else if (face == 3) corner = vulkanic_source_rotate_x(corner, 3.14159265359);
        else if (face == 4) corner = vulkanic_source_rotate_z(corner, 1.57079632679);
        else if (face == 5) corner = vulkanic_source_rotate_z(corner, -1.57079632679);
        return vec4(corner, 1.0);
    }
    if (face != 0) {
        // Sun and moon draw one quad; collapse the remaining faces.
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    const vec2 sun_corners[4] = vec2[4](
        vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0)
    );
    const vec2 moon_corners[4] = vec2[4](
        vec2(-1.0, 1.0), vec2(1.0, 1.0), vec2(1.0, -1.0), vec2(-1.0, -1.0)
    );
    bool moon = vulkanic_source_celestial_is_moon == 1;
    vec2 corner = moon ? moon_corners[vulkanic_source_celestial_corner()]
                       : sun_corners[vulkanic_source_celestial_corner()];
    return vec4(corner.x, 0.0, corner.y, 1.0);
}
vec2 vulkanic_source_fullscreen_uv_coordinates() {
    int corner = vulkanic_source_celestial_corner();
    bool moon = vulkanic_source_celestial_is_moon == 1;
    vec2 coordinate;
    if (vulkanic_source_celestial_end_sky()) {
        const vec2 end_uv[4] = vec2[4](
            vec2(0.0, 0.0), vec2(0.0, 16.0), vec2(16.0, 16.0), vec2(16.0, 0.0)
        );
        coordinate = end_uv[corner];
    } else if (moon) {
        int phase = clamp(moonPhase, 0, 7);
        float u0 = float(phase % 4) * 0.25;
        float v0 = float(phase / 4) * 0.5;
        const vec2 moon_uv[4] = vec2[4](
            vec2(1.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 0.0), vec2(1.0, 0.0)
        );
        coordinate = vec2(u0, v0) + moon_uv[corner] * vec2(0.25, 0.5);
    } else {
        const vec2 sun_uv[4] = vec2[4](
            vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
        );
        coordinate = sun_uv[corner];
    }
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    coordinate.y = 1.0 - coordinate.y;
#endif
    return coordinate;
}
#define vulkanic_source_fullscreen_transform() (gbufferProjection * vulkanic_source_celestial_model_view() * vulkanic_source_fullscreen_celestial_position())
"#;
