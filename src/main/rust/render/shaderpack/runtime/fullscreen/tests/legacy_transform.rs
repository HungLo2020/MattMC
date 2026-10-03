//! Native coverage and input-domain checks for Frozen's composite transform.

use super::*;
use crate::render::vulkanic::commands::{BufferImageCopyRegion, TextureOrigin3d};

#[test]
fn fullscreen_legacy_transform_preserves_unit_positions_and_sampler_uv_on_native_device() {
    let mut gal =
        crate::render::vulkanic::test_support::vulkan_gal("fullscreen-legacy-transform").unwrap();
    for transform in [
        "gl_ModelViewProjectionMatrix * gl_Vertex",
        "gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex",
        "ftransform()",
    ] {
        let base = source(concat!(
            "#version 130\nin vec2 local;\nin vec2 uv;\nin float valid;\n",
            "void main() { gl_FragData[0] = vec4(local, uv.y, valid); }\n",
        ));
        let files = base
            .files()
            .into_iter()
            .map(|mut file| {
                if file.path == "world0/deferred1.vsh" {
                    file.contents = format!(
                        concat!(
                    "#version 130\nout vec2 local;\nout vec2 uv;\nout float valid;\n",
                    "void main() {{ gl_Position = {}; local = gl_Vertex.xy;\n",
                    "uv = gl_MultiTexCoord0.xy;\n",
                    // Frozen's composite Z column is zero, rather than a 3D
                    // ortho projection. Normal and unused texture lanes have
                    // explicit compatibility values, not scene camera data.
                    "valid = all(equal(gl_ProjectionMatrix[2], vec4(0.0))) &&\n",
                    "all(equal(gl_NormalMatrix * gl_Normal, vec3(0.0, 0.0, 1.0))) &&\n",
                    "all(equal(gl_MultiTexCoord1, vec4(0.0, 0.0, 0.0, 1.0))) &&\n",
                    "all(equal(gl_MultiTexCoord7, vec4(0.0, 0.0, 0.0, 1.0))) &&\n",
                    "all(equal(gl_TextureMatrix[7] * gl_Color, vec4(1.0))) ? 1.0 : 0.0; }}\n",
                ),
                        transform
                    );
                }
                file
            })
            .collect();
        let source = ShaderPackSource::new(base.name(), base.generation(), files).unwrap();
        let program = program(&source);
        assert!(
            program.execution_interface.scalar_uniform_fields.is_empty(),
            "composite transforms must not invent world-camera uniforms"
        );
        let (manifest, targets, mut cache) = staged(&source, &mut gal, false);
        let extent = targets.identity.extent;
        let plan = FullscreenSourceExecutionPlan::stage(
            &mut gal,
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
                label: "fullscreen-transform.readback".into(),
                size: 16 * 16 * 4,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
        let mut operations = Vec::new();
        plan.append_draw(&program, FullscreenSourcePassFrame {
            texture_transforms: program.pack_texture_transforms(
                &crate::render::shaderpack::programs::TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
            ).unwrap(),
            scalar_uniforms: Vec::new(),
            texture_transform_before: TextureUsageState::Undefined,
            scalar_uniform_before: None,
            clear_values: ShaderPackColorClearValues {
                fog_color: crate::render::vulkanic::commands::ClearColor {
                    r: 0.0, g: 0.0, b: 0.0, a: 0.0,
                },
            },
            color_attachment_before: vec![TextureUsageState::Undefined],
            clear_targets_this_pass: None,
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
                label: "fullscreen-transform".into(),
                command_lists: vec![CommandList::from(CommandListDesc {
                    label: "fullscreen-transform.commands".into(),
                    operations,
                })],
            })
            .unwrap();
        gal.retire_through_for_test(token.submission).unwrap();
        let reads = gal.completed_host_reads();
        let pixels = &reads
            .iter()
            .rev()
            .find(|read| read.buffer == readback)
            .unwrap()
            .bytes;
        assert_eq!(16 * 16 * 4, pixels.len());
        for (index, pixel) in pixels.chunks_exact(4).enumerate() {
            let x = (index % 16) as f32 + 0.5;
            let y = (index / 16) as f32 + 0.5;
            // RasterYDirection::Up puts the source quad's upper edge in
            // readback row zero. Sampler UVs have the opposite row domain.
            let expected = [x / 16.0 * 255.0, (1.0 - y / 16.0) * 255.0, y / 16.0 * 255.0];
            for channel in 0..3 {
                assert!((pixel[channel] as f32 - expected[channel]).abs() <= 1.0,
                    "{transform}: pixel {index}, channel {channel}: {pixel:?}, expected {expected:?}");
            }
            assert_eq!(
                255, pixel[3],
                "{transform}: uncovered pixel or incorrect legacy input at {index}"
            );
        }
        gal.destroy(readback).unwrap();
        plan.destroy(&mut gal);
        cache.destroy(&mut gal);
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}
