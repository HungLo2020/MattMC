//! Frozen VanillaCoreTransformer: unlit sky formats retain full lightmap inputs.
use super::*;
use crate::render::shaderpack::lowering::FullscreenSourceRasterPrimitive;

#[test]
fn sky_lightmap_legacy_coordinates_and_matrix_aliases_match_frozen_on_native_device() {
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("sky-lightmap-inputs").unwrap();
    let mut frame = sky_transform::captured_frame();
    frame.far_plane = Some(160.0);
    frame.fog_color = Some([0.7, 0.8, 1.0]);
    let vertex = r#"#version 130
out float valid;
void main() {
    int vertex = vulkanic_source_fullscreen_vertex_index();
    if (vertex >= 6) { valid = 0.0; gl_Position = vec4(0.0,0.0,2.0,1.0); return; }
    vec4 expected = vec4(240.0,240.0,0.0,1.0);
    vec4 light = vec4(0.96875,0.96875,0.03125,1.0);
    valid = all(equal(gl_MultiTexCoord1,expected)) &&
            all(equal(gl_MultiTexCoord2,expected)) &&
            all(equal(gl_TextureMatrix[1] * gl_MultiTexCoord1,light)) &&
            all(equal(gl_TextureMatrix[2] * gl_MultiTexCoord2,light)) &&
            all(equal(gl_TextureMatrix[0] * vec4(0.25,0.5,0.0,1.0),vec4(0.25,0.5,0.0,1.0)))
            ? 1.0 : 0.0;
    const vec2 positions[6] = vec2[6](vec2(-1,-1),vec2(-1,1),vec2(1,1),vec2(1,1),vec2(1,-1),vec2(-1,-1));
    gl_Position = vec4(positions[vertex],0.0,1.0);
}
"#;
    for raster in [
        FullscreenSourceRasterPrimitive::VanillaSkyDisc,
        FullscreenSourceRasterPrimitive::ShaderPackHorizon,
        FullscreenSourceRasterPrimitive::VanillaCelestialQuad,
    ] {
        let pixels = sky_transform::draw_inputs(
            &mut gal,
            raster,
            vertex,
            "gl_FragData[0] = vec4(valid);",
            &frame,
        );
        assert!(
            pixels.chunks_exact(4).all(|pixel| pixel == [255; 4]),
            "{raster:?}: legacy sky lightmap input differs from Frozen"
        );
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}
