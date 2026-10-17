//! Rust-owned procedural coverage, sky geometry and compatibility inputs.

use super::*;

pub(super) fn fullscreen_vertex_semantic_preamble(
    raster_primitive: FullscreenSourceRasterPrimitive,
) -> String {
    let geometry = match raster_primitive {
        FullscreenSourceRasterPrimitive::FullscreenTriangle => {
            r#"
vec2 vulkanic_source_fullscreen_position() {
    const vec2 positions[3] = vec2[3](
        vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0)
    );
    return positions[vulkanic_source_fullscreen_vertex_index()];
}
vec4 vulkanic_source_fullscreen_vertex() {
    // Unit-quad local coordinates are independent of the sampler UV's
    // backend row conversion. The procedural triangle extends that same
    // affine domain beyond the viewport to cover it without a diagonal seam.
    return vec4(vulkanic_source_fullscreen_position() * 0.5 + 0.5, 0.0, 1.0);
}
vec2 vulkanic_source_fullscreen_uv_coordinates() {
    const vec2 coordinates[3] = vec2[3](
        vec2(0.0, 0.0), vec2(2.0, 0.0), vec2(0.0, 2.0)
    );
    vec2 coordinate = coordinates[vulkanic_source_fullscreen_vertex_index()];
    // Legacy source `texture2D` calls use Minecraft's OpenGL texture origin.
    // Vulkan images are sampled with the opposite vertical convention, while
    // integer texelFetch keeps its native image addressing. Preserve the
    // source sampler contract here, at the owned fullscreen vertex boundary.
    #ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
        coordinate.y = 1.0 - coordinate.y;
    #endif
    return coordinate;
}
#define vulkanic_source_fullscreen_transform() vec4(vulkanic_source_fullscreen_position(), 0.0, 1.0)
"#.to_string()
        }
        FullscreenSourceRasterPrimitive::VanillaSkyDisc => {
            r#"
// Expanded eight-wedge form of Minecraft's top SkyRenderer disc. The source
// program's legacy ftransform() sees the same local-space sky geometry rather
// than an approximation at synthetic fullscreen depth.
vec4 vulkanic_source_fullscreen_sky_position() {
    const vec3 outer[9] = vec3[9](
        vec3(-512.0, 16.0, 0.0),
        vec3(-362.03867, 16.0, -362.03867),
        vec3(0.0, 16.0, -512.0),
        vec3(362.03867, 16.0, -362.03867),
        vec3(512.0, 16.0, 0.0),
        vec3(362.03867, 16.0, 362.03867),
        vec3(0.0, 16.0, 512.0),
        vec3(-362.03867, 16.0, 362.03867),
        vec3(-512.0, 16.0, 0.0)
    );
    int vertex = vulkanic_source_fullscreen_vertex_index();
    int wedge = vertex / 3;
    int corner = vertex - wedge * 3;
    return corner == 0
        ? vec4(0.0, 16.0, 0.0, 1.0)
        : vec4(outer[wedge + corner - 1], 1.0);
}
vec2 vulkanic_source_fullscreen_uv_coordinates() { return vec2(0.0); }
#define vulkanic_source_fullscreen_transform() (gbufferProjection * gbufferModelView * vulkanic_source_fullscreen_sky_position())
"#.to_string()
        }
        FullscreenSourceRasterPrimitive::VanillaCelestialQuad => {
            CELESTIAL_VERTEX_GEOMETRY.to_string()
        }
        FullscreenSourceRasterPrimitive::ShaderPackHorizon => HORIZON_VERTEX_GEOMETRY.to_string(),
    };
    let vertex_color = match raster_primitive {
        FullscreenSourceRasterPrimitive::ShaderPackHorizon => {
            "#define vulkanic_source_fullscreen_vertex_color vec4(fogColor, 1.0)"
        }
        // SkyRenderer builds its top disc with the extracted vanilla sky
        // color. The selected source's sky vertex uses this for its legacy
        // `gl_Color` semantic (including star discrimination), so it cannot
        // be replaced with the generic procedural white value.
        FullscreenSourceRasterPrimitive::VanillaSkyDisc => {
            "#define vulkanic_source_fullscreen_vertex_color vec4(skyColor, 1.0)"
        }
        // The End sky box uses SkyRenderer's constant 0xFF282828 vertex color.
        FullscreenSourceRasterPrimitive::VanillaCelestialQuad => {
            "#define vulkanic_source_fullscreen_vertex_color (vulkanic_source_celestial_is_moon == 2 ? vec4(vec3(40.0 / 255.0), 1.0) : vec4(1.0, 1.0, 1.0, vulkanic_source_celestial_alpha))"
        }
        FullscreenSourceRasterPrimitive::FullscreenTriangle => {
            "const vec4 vulkanic_source_fullscreen_vertex_color = vec4(1.0);"
        }
    };
    r#"// Source stages use Rust-owned procedural geometry. This avoids
// inheriting a Java/Iris vertex stream and stays compatible with the OpenGL
// backend's intentionally storage-buffer-only mesh path.
layout(set = 0, binding = 0, std140) uniform VulkanicSourceFullscreenFrame {
    mat4 vulkanic_source_fullscreen_texture_matrix[2];
};
int vulkanic_source_fullscreen_vertex_index() {
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    return gl_VertexIndex;
#else
    return gl_VertexID;
#endif
}
VULKANIC_SOURCE_FULLSCREEN_GEOMETRY
#define vulkanic_source_fullscreen_uv vec4(vulkanic_source_fullscreen_uv_coordinates(), 0.0, 1.0)
#define vulkanic_source_fullscreen_secondary_uv vec4(vulkanic_source_fullscreen_uv_coordinates(), 0.0, 1.0)
VULKANIC_SOURCE_FULLSCREEN_VERTEX_COLOR
"#
    .replace("VULKANIC_SOURCE_FULLSCREEN_GEOMETRY", &geometry)
    .replace("VULKANIC_SOURCE_FULLSCREEN_VERTEX_COLOR", vertex_color)
}
