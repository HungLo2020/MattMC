//! Sample both actual owned shadow images; no extra production texture usages.
use super::*;
use crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResourceSet;
use crate::render::shaderpack::resources::color_targets::{
    ShaderPackColorTargetCache, ShaderPackColorTargetIdentity, ShaderPackColorTargetManifest,
};
use crate::render::shaderpack::runtime::fullscreen::{
    FullscreenSourceExecutionPlan, FullscreenSourcePassFrame,
};
use crate::render::vulkanic::commands::{BufferImageCopyRegion, TextureOrigin3d};
use crate::render::vulkanic::resources::CombinedTextureSamplerDesc;

#[test]
fn empty_source_preparation_native_readers_see_clear_depth_in_both_shadow_images() {
    // The source-only fixture has no GPU resources before recording.
    let (mut mock, mut frontend, mock_target, scene) = empty_source_frame();
    mock.destroy(mock_target).unwrap();
    drop(mock);
    let mut gal =
        crate::render::vulkanic::test_support::vulkan_gal("empty source shadow readers").unwrap();
    let owner = passes::oriented_target::OrientedWorldTarget::create(
        &mut gal,
        "empty-shadow-test",
        passes::oriented_target::WorldTargetDesc {
            extent: Extent3d {
                width: 128,
                height: 128,
                depth: 1,
            },
            color_format: ColorFormat::Rgba8Unorm,
            raster_y_direction: RasterYDirection::Up,
        },
    )
    .unwrap();
    let (mut ops, _) = frontend
        .append_frame_ops_inner_with_source_preparation(
            &mut gal,
            1,
            owner.target,
            scene,
            true,
            RasterYDirection::Up,
            true,
        )
        .unwrap();
    ops.insert(
        0,
        CommandOp::Barrier(texture_barrier(
            owner.color_texture,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )),
    );
    let graph = frontend.g_buffer_resources.as_ref().unwrap();
    let roles = [
        TerrainSourceResourceRole::ShadowDepthRaw,
        TerrainSourceResourceRole::ShadowDepthRawSecondary,
    ];
    let mut samplers = Vec::new();
    for view in [graph.shadow_depth_view, graph.shadow_depth_opaque_view] {
        samplers.push(
            gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: "empty-shadow-reader".into(),
                texture_view: view,
                sampler: graph.sampler,
            })
            .unwrap(),
        );
    }
    let inputs = TerrainSourceOwnedResourceSet::new(
        TerrainSourceResourceAvailabilitySet::new(
            1,
            1,
            roles.iter().map(|role| TerrainSourceResourceAvailability {
                role: role.clone(),
                shape: role.expected_sampled_resource_shape(),
                resource_generation: graph.generation,
            }),
        )
        .unwrap(),
        roles
            .iter()
            .zip(&samplers)
            .map(|(role, sampler)| TerrainSourceOwnedResource {
                role: role.clone(),
                combined_sampler: *sampler,
            }),
    )
    .unwrap();
    let source = ShaderPackSource::new("empty-shadow-reader", 1, vec![
        ShaderSourceFile::new("lib/pipelineSettings.glsl", "const int colortex0Format = RGBA8;\n"),
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH,
            concat!("colortex0=shader_pack_color:primary\n",
                "colortex1=shader_pack_color:previous_depth\ncolortex2=shader_pack_color:temporal_aa\n",
                "colortex3=shader_pack_color:translucent_final\ncolortex4=shader_pack_color:volumetric_factor\n",
                "colortex5=shader_pack_color:normal_scene\ncolortex6=shader_pack_color:material_auxiliary\n",
                "colortex7=shader_pack_color:temporal_reflection\n",
                "shadowtex0=shadow_depth_primary\nshadowtex1=shadow_depth_secondary\n")),
        ShaderSourceFile::new("world0/deferred.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/deferred.fsh", "#version 130\nuniform sampler2D shadowtex0;\nuniform sampler2D shadowtex1;\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0]=vec4(texture2D(shadowtex0,vec2(0.5)).r,texture2D(shadowtex1,vec2(0.5)).r,0.0,1.0); }\n"),
    ]).unwrap();
    let stages = crate::render::shaderpack::contracts::terrain::TerrainSourceStages {
        vertex: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "world0/deferred.vsh".into(),
            defines: Default::default(),
        },
        fragment: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "world0/deferred.fsh".into(),
            defines: Default::default(),
        },
    };
    let artifacts = crate::render::shaderpack::source::preprocess::preprocess_source_stage_pair(
        &source, &stages,
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let lowered = crate::render::shaderpack::lowering::lower_fullscreen_source_pair(
        &artifacts.vertex,
        &artifacts.fragment,
        &bindings,
    )
    .unwrap();
    let opaque = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&bindings)
        .unwrap();
    let program = crate::render::shaderpack::programs::prepare_lowered_fullscreen_source_program(
        source.name(),
        1,
        "world0/deferred.fsh",
        &lowered,
        &opaque,
    )
    .unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    let extent = Extent3d {
        width: 16,
        height: 16,
        depth: 1,
    };
    let identity = ShaderPackColorTargetIdentity::new(1, 1, extent, [], []).unwrap();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache.stage(&mut gal, identity, &manifest).unwrap();
    let plan = FullscreenSourceExecutionPlan::stage(
        &mut gal,
        &program,
        &manifest,
        &targets,
        [inputs],
        extent,
    )
    .unwrap();
    plan.append_draw(
        &program,
        FullscreenSourcePassFrame {
            texture_transforms: program
                .pack_texture_transforms(
                    &TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                )
                .unwrap(),
            scalar_uniforms: program
                .pack_scalar_uniforms(&TerrainSourceUniformFrame::default())
                .unwrap(),
            texture_transform_before: TextureUsageState::Undefined,
            scalar_uniform_before: program
                .execution_interface
                .scalar_uniforms
                .map(|_| TextureUsageState::Undefined),
            clear_values: ShaderPackColorClearValues {
                fog_color: ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                },
            },
            color_attachment_before: vec![TextureUsageState::Undefined],
            clear_targets_this_pass: None,
        },
        &mut ops,
    )
    .unwrap();
    let output = plan.outputs()[0].texture;
    let readback = gal
        .create_buffer(BufferDesc {
            label: "empty-shadow-pixels".into(),
            size: 16 * 16 * 4,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })
        .unwrap();
    ops.extend([
        CommandOp::Barrier(texture_barrier(
            output,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferSrc,
        )),
        CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: 16 * 4,
            rows_per_image: 16,
            texture: output,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }),
        CommandOp::Barrier(buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )),
        CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: 16 * 16 * 4,
        },
    ]);
    let token = gal
        .submit(SubmissionBatch {
            label: "empty-shadow-readback".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "empty-shadow-readback".into(),
                operations: ops,
            })],
        })
        .unwrap();
    gal.retire_through_for_test(token.submission).unwrap();
    let reads = gal.completed_host_reads();
    let pixels = &reads
        .iter()
        .find(|read| read.buffer == readback)
        .unwrap()
        .bytes;
    assert!(
        pixels
            .chunks_exact(4)
            .all(|pixel| pixel == [255, 255, 0, 255]),
        "every pixel must sample depth 1 from both initialized shadow images: {:?}",
        &pixels[..16]
    );
    gal.destroy(readback).unwrap();
    plan.destroy(&mut gal);
    cache.destroy(&mut gal);
    for sampler in samplers {
        gal.destroy(sampler).unwrap();
    }
    frontend.reset(&mut gal);
    for handle in owner.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}
