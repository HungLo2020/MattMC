//! Frozen frame 353, event 888: enclosing horizon before event 917's disc.
//! Capture SHA ed9f235f7b34179e66ac24421dbf1c1fbb2831c7718ce0bb2ebe6edfb7ac6057.
use super::*;
use crate::render::shaderpack::lowering::FullscreenSourceRasterPrimitive;

#[test]
fn horizon_legacy_transform_retains_captured_wall_vertices_and_radius_cap_on_native_device() {
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("horizon-wall-inputs").unwrap();
    let mut frame = sky_transform::captured_frame();
    frame.fog_color = Some([0.7019608, 0.8156863, 1.0]);
    // First wall from captured event 888, then Frozen's same construction
    // with its documented 256-block cap. Isolate that wall in screen space
    // so later horizon faces cannot hide a wrong vertex.
    for (far, adjacent, opposite) in [(160.0, 147.820724, 61.229347), (512.0, 236.51315, 97.96696)] {
        frame.far_plane = Some(far);
        let vertex = format!(r#"#version 130
out float valid;
void main() {{
    int vertex = vulkanic_source_fullscreen_vertex_index();
    if (vertex >= 6) {{ valid=0.0; gl_Position=vec4(-2.0,-2.0,0.0,1.0); return; }}
    const vec3 expected[6] = vec3[6](
        vec3({adjacent},-16.0,-{opposite}), vec3({adjacent},16.0,-{opposite}),
        vec3({opposite},16.0,-{adjacent}), vec3({opposite},16.0,-{adjacent}),
        vec3({opposite},-16.0,-{adjacent}), vec3({adjacent},-16.0,-{opposite}));
    valid=all(lessThan(abs(gl_Vertex-vec4(expected[vertex],1.0)),vec4(0.0001))) ? 1.0 : 0.0;
    const vec2 positions[6]=vec2[6](vec2(-1,-1),vec2(-1,1),vec2(1,1),vec2(1,1),vec2(1,-1),vec2(-1,-1));
    gl_Position=vec4(positions[vertex],0.0,1.0);
}}
"#);
        let pixels = sky_transform::draw_inputs(&mut gal,
            FullscreenSourceRasterPrimitive::ShaderPackHorizon, &vertex,
            "gl_FragData[0]=vec4(valid);", &frame);
        assert!(pixels.chunks_exact(4).all(|pixel| pixel == [255; 4]), "captured wall/cap differs at far={far}");
    }
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn horizon_legacy_transform_covers_below_disc_with_captured_fog_color_on_native_device() {
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("horizon-inputs").unwrap();
    let vertex = r#"#version 130
out float valid;
void main() {
    valid = all(lessThan(abs(gl_Color-vec4(0.7019608,0.8156863,1.0,1.0)),vec4(0.00001))) ? 1.0 : 0.0;
    gl_Position = ftransform();
}
"#;
    let mut frame = sky_transform::captured_frame();
    frame.far_plane = Some(160.0);
    frame.fog_color = Some([0.7019608, 0.8156863, 1.0]);
    for far in [160.0, 512.0] {
        frame.far_plane = Some(far);
        let pixels = sky_transform::draw_inputs(&mut gal,
            FullscreenSourceRasterPrimitive::ShaderPackHorizon, vertex,
            "gl_FragData[0] = vec4(valid);", &frame);
        assert!(pixels.chunks_exact(4).all(|pixel| pixel == [255; 4]),
            "enclosing horizon left a hole or changed fog color at render distance {far}");
    }
    // A disc alone leaves the horizon region at the clear color. This
    // control demonstrates why a correct top-disc transform cannot close
    // the missing-geometry defect.
    let disc = sky_transform::draw_inputs(&mut gal,
        FullscreenSourceRasterPrimitive::VanillaSkyDisc,
        "#version 130\nout float valid;\nvoid main(){ valid=1.0; gl_Position=ftransform(); }",
        "gl_FragData[0] = vec4(valid);", &frame);
    assert!(disc.chunks_exact(4).any(|pixel| pixel[..3] == [0; 3]));
    assert!(disc.chunks_exact(4).any(|pixel| pixel == [255; 4]));
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}
