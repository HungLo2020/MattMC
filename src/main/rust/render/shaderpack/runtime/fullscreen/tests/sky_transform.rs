//! Frozen frame 353, OpenGL events 917/958: local sky inputs and draw matrices.
//! Capture SHA ed9f235f7b34179e66ac24421dbf1c1fbb2831c7718ce0bb2ebe6edfb7ac6057.
//! Native readback is supplemental input evidence, not gameplay image parity.

use super::*;
use crate::render::shaderpack::lowering::{
    lower_fullscreen_source_pair_with_raster_primitive, FullscreenSourceRasterPrimitive,
};
use crate::render::shaderpack::uniforms::source::TerrainSourceUniformFrame;
use crate::render::vulkanic::commands::{BufferImageCopyRegion, TextureOrigin3d};

pub(super) fn captured_frame() -> TerrainSourceUniformFrame {
    // SDK ShaderVariable matrices are row-major; the semantic writer takes
    // column-major matrices. These values are the captured sky draw's camera,
    // not the translated/scaled celestial draw's model-view.
    TerrainSourceUniformFrame {
        view_matrix: Some([
            0.2588189,
            -0.16773126,
            0.95125127,
            0.0,
            7.450581e-9,
            0.9848077,
            0.17364815,
            0.0,
            -0.9659258,
            -0.044943415,
            0.25488687,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
        ]),
        projection_matrix: Some([
            0.8033332,
            0.0,
            0.0,
            0.0,
            0.0,
            1.428148,
            0.0,
            0.0,
            0.0,
            0.0,
            -1.0000489,
            -1.0,
            0.0,
            0.0,
            -0.100002445,
            0.0,
        ]),
        celestial_time_of_day: Some(0.0),
        sun_angle: Some(0.25),
        celestial_sun_path_rotation: Some(-25.0),
        celestial_is_moon: Some(0),
        celestial_alpha: Some(1.0),
        moon_phase: Some(0),
        sky_color: Some([0.4627451, 0.65882355, 1.0]),
        ..Default::default()
    }
}

#[test]
fn sky_legacy_transform_matches_frozen_celestial_local_inputs_and_clip_positions_on_native_device()
{
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("sky-transform").unwrap();
    for transform in [
        "gl_ModelViewProjectionMatrix * gl_Vertex",
        "gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex",
        "ftransform()",
    ] {
        let vertex = format!(
            r#"#version 130
out float valid;
void main() {{
    // Exact captured Sun clip coordinates include the pack's TAA jitter.
    const vec4 expected[4] = vec4[4](
        vec4(-5.4778562, 137.1107941, -50.4212799, -50.3188171),
        vec4(-47.6770782, 97.9503708, -59.8788872, -59.7759628),
        vec4(-60.1298561, 112.3627090, -2.8010223, -2.7008877),
        vec4(-17.9306297, 151.5231171, 6.6565862, 6.7562580)
    );
    vec4 clip = {transform};
    clip.xy += vec2(0.000390625, 0.00069444446) * clip.w;
    int corner = gl_Vertex.z < 0.0 ? (gl_Vertex.x < 0.0 ? 0 : 1)
                                  : (gl_Vertex.x < 0.0 ? 3 : 2);
    bool local = gl_Vertex.y == 0.0 && gl_Vertex.w == 1.0 &&
                 abs(gl_Vertex.x) == 1.0 && abs(gl_Vertex.z) == 1.0;
    valid = local && all(lessThan(abs(clip - expected[corner]), vec4(0.001)))
                  && all(equal(gl_Color, vec4(1.0))) ? 1.0 : 0.0;
    // The captured Sun is mostly outside the view. Rasterize its local unit
    // domain for an observable readback after checking the actual clip value.
    gl_Position = vec4(gl_Vertex.x, gl_Vertex.z, 0.0, 1.0);
}}
"#
        );
        let pixels = draw_inputs(
            &mut gal,
            FullscreenSourceRasterPrimitive::VanillaCelestialQuad,
            &vertex,
            r#"
            // Fragment compatibility matrices must refer to this draw too.
            bool matrix_valid = all(lessThan(abs(gl_ModelViewMatrix[3] -
                vec4(-40.8217888,87.3544998,26.5098515,1.0)),vec4(0.001))) &&
                all(lessThan(abs(gl_ModelViewMatrix[0] -
                vec4(-26.2627811,-13.7079086,4.7285719,0.0)),vec4(0.001)));
            gl_FragData[0] = vec4(valid * (matrix_valid ? 1.0 : 0.0));
            "#,
            &captured_frame(),
        );
        assert!(
            pixels.iter().all(|value| *value == 255),
            "{transform}: celestial local inputs/clock/matrices differ from Frozen"
        );
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn sky_legacy_transform_preserves_frozen_disc_positions_depth_and_color_on_native_device() {
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("sky-disc-transform").unwrap();
    let vertex = r#"#version 130
out float valid;
void main() {
    vec4 clip = gl_ModelViewProjectionMatrix * gl_Vertex;
    // Captured event 917 center and outer point (-512,16,0), before TAA.
    vec4 center = vec4(0.0, 22.5032196, -2.8785088, -2.7783704);
    vec4 outer = vec4(-106.453918, 145.150299, 484.185944, 484.262268);
    bool geometry = gl_Vertex.y == 16.0 && gl_Vertex.w == 1.0;
    if (gl_Vertex.x == 0.0 && gl_Vertex.z == 0.0)
        geometry = geometry && all(lessThan(abs(clip-center),vec4(0.001)));
    if (gl_Vertex.x == -512.0 && abs(gl_Vertex.z) < 0.001)
        geometry = geometry && all(lessThan(abs(clip-outer),vec4(0.001)));
    valid = geometry && all(lessThan(abs(gl_Color - vec4(0.4627451,0.65882355,1.0,1.0)),vec4(0.00001))) ? 1.0 : 0.0;
    gl_Position = vec4(gl_Vertex.xz / 512.0, 0.0, 1.0);
}
"#;
    let pixels = draw_inputs(
        &mut gal,
        FullscreenSourceRasterPrimitive::VanillaSkyDisc,
        vertex,
        "gl_FragData[0] = vec4(valid);",
        &captured_frame(),
    );
    // Interior is covered by the expanded fan; viewport corners are outside.
    for y in 4..12 {
        for x in 4..12 {
            assert_eq!(
                &[255; 4],
                &pixels[(y * 16 + x) * 4..(y * 16 + x + 1) * 4],
                "disc geometry/depth/color differs at {x},{y}"
            );
        }
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

pub(super) fn draw_inputs(
    gal: &mut VulkanicGal,
    raster: FullscreenSourceRasterPrimitive,
    vertex: &str,
    fragment_body: &str,
    frame: &TerrainSourceUniformFrame,
) -> Vec<u8> {
    let source = ShaderPackSource::new("sky-input-readback", 9, vec![
        ShaderSourceFile::new("lib/pipelineSettings.glsl", SETTINGS),
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, BINDINGS),
        ShaderSourceFile::new("world0/gbuffers_sky.vsh", vertex),
        ShaderSourceFile::new("world0/gbuffers_sky.fsh", format!(
            "#version 130\n/* DRAWBUFFERS:0 */\nin float valid;\nvoid main() {{ {fragment_body} }}\n")),
    ]).unwrap();
    let stages = TerrainSourceStages {
        vertex: TerrainSourceStage {
            path: "world0/gbuffers_sky.vsh".into(),
            defines: Default::default(),
        },
        fragment: TerrainSourceStage {
            path: "world0/gbuffers_sky.fsh".into(),
            defines: Default::default(),
        },
    };
    let artifacts = preprocess_source_stage_pair(&source, &stages).unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let lowered = lower_fullscreen_source_pair_with_raster_primitive(
        &artifacts.vertex,
        &artifacts.fragment,
        &bindings,
        raster,
    )
    .unwrap();
    let opaque = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&bindings)
        .unwrap();
    let program = prepare_lowered_fullscreen_source_program(
        source.name(),
        source.generation(),
        "world0/gbuffers_sky.fsh",
        &lowered,
        &opaque,
    )
    .unwrap();
    let (manifest, targets, mut cache) = staged(&source, gal, false);
    let extent = targets.identity.extent;
    let plan = FullscreenSourceExecutionPlan::stage(
        gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(empty_source_resource_snapshot(&source)),
        extent,
    )
    .unwrap();
    let texture = plan.outputs()[0].texture;
    let readback = gal
        .create_buffer(BufferDesc {
            label: "sky-input.readback".into(),
            size: 16 * 16 * 4,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })
        .unwrap();
    let mut operations = Vec::new();
    plan.append_draw(&program, FullscreenSourcePassFrame {
        texture_transforms: program.pack_texture_transforms(
            &crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain()).unwrap(),
        scalar_uniforms: program.pack_scalar_uniforms(frame).unwrap(),
        texture_transform_before: TextureUsageState::Undefined,
        scalar_uniform_before: Some(TextureUsageState::Undefined),
        clear_values: ShaderPackColorClearValues {
            fog_color: crate::render::vulkanic::commands::ClearColor { r: 0.0, g: 0.0, b: 0.0, a: 0.0 },
        },
        color_attachment_before: vec![TextureUsageState::Undefined], clear_targets_this_pass: None,
    }, &mut operations).unwrap();
    let barrier = |resource, before, after| {
        CommandOp::Barrier(ResourceBarrier {
            resource,
            subresources: None,
            before,
            after,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Graphics,
        })
    };
    operations.extend([
        barrier(
            texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferSrc,
        ),
        CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: 16 * 4,
            rows_per_image: 16,
            texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }),
        barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        ),
        CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: 16 * 16 * 4,
        },
    ]);
    let token = gal
        .submit(SubmissionBatch {
            label: "sky-input".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "sky-input.commands".into(),
                operations,
            })],
        })
        .unwrap();
    gal.retire_through_for_test(token.submission).unwrap();
    let pixels = gal
        .completed_host_reads()
        .iter()
        .rev()
        .find(|read| read.buffer == readback)
        .unwrap()
        .bytes
        .clone();
    gal.destroy(readback).unwrap();
    plan.destroy(gal);
    cache.destroy(gal);
    pixels
}
