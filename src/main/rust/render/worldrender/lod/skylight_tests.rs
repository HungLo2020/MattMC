//! Frozen OpenGL's reduced DH colors retain their original skylight coordinate.
use super::*;
use crate::render::scene::lod::WORLD_LOD_VERTEX_LAYOUT_V1;
use crate::render::vulkanic::commands::BufferImageCopyRegion;
use crate::render::vulkanic::{CommandList, CommandListDesc, SubmissionBatch};

#[test]
fn ordinary_dh_native_skylight_preserves_dark_and_bright_source_rows() {
    // Frozen standard.vert maps sky 0 and 15 to their respective lightmap
    // texel centers. The Java Vulkan-only fold must not enter this route.
    // Distinct row colors make that semantic error visible in actual pixels.
    for (pass, layer, material, contract) in [
        (
            WorldLodPassClass::Opaque,
            WORLD_LOD_LAYER_OPAQUE,
            4,
            WorldLodMaterialContract::OPAQUE,
        ),
        (
            WorldLodPassClass::TransparentUp,
            WORLD_LOD_LAYER_TRANSPARENT_WATER_UP,
            12,
            WorldLodMaterialContract::TRANSPARENT_UP,
        ),
    ] {
        for sky in [0, 15] {
            let mut gal =
                crate::render::vulkanic::test_support::vulkan_gal("DH source skylight").unwrap();
            let extent = Extent3d {
                width: 16,
                height: 16,
                depth: 1,
            };
            let target = WorldLodDirectCompositionResources::create(
                &mut gal,
                extent,
                TextureFormat::Rgba8Unorm,
                RasterYDirection::Up,
            )
            .unwrap();
            let lightmap = gal
                .create_texture(TextureDesc {
                    label: "dh-skylight.lightmap".into(),
                    dimension: TextureDimension::D2,
                    format: TextureFormat::Rgba8Unorm,
                    extent,
                    mip_levels: 1,
                    array_layers: 1,
                    usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
                })
                .unwrap();
            let view = gal
                .create_texture_view(TextureViewDesc {
                    label: "dh-skylight.lightmap-view".into(),
                    texture: lightmap,
                    format: TextureFormat::Rgba8Unorm,
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                })
                .unwrap();
            let sampler = gal
                .create_sampler(SamplerDesc {
                    label: "dh-skylight.sampler".into(),
                    min_filter: SamplerFilter::Nearest,
                    mag_filter: SamplerFilter::Nearest,
                    mip_filter: SamplerFilter::Nearest,
                    address_u: SamplerAddressMode::ClampToEdge,
                    address_v: SamplerAddressMode::ClampToEdge,
                    address_w: SamplerAddressMode::ClampToEdge,
                    comparison: None,
                })
                .unwrap();
            let upload = gal
                .create_buffer(BufferDesc {
                    label: "dh-skylight.upload".into(),
                    size: 1024,
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
                })
                .unwrap();
            let pixels = (0..16)
                .flat_map(|row| {
                    let color = if row < 8 {
                        [255, 0, 0, 255]
                    } else {
                        [0, 255, 0, 255]
                    };
                    (0..16).flat_map(move |_| color)
                })
                .collect();
            let region = BufferImageCopyRegion {
                buffer: upload,
                buffer_offset: 0,
                bytes_per_row: 64,
                rows_per_image: 16,
                texture: lightmap,
                texture_mip: 0,
                texture_layer: 0,
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent,
            };
            let mut ops = vec![
                CommandOp::HostWriteBuffer {
                    buffer: upload,
                    offset: 0,
                    data: pixels,
                },
                CommandOp::Barrier(buffer_barrier(
                    upload,
                    TextureUsageState::TransferDst,
                    TextureUsageState::TransferSrc,
                )),
                CommandOp::Barrier(texture_barrier(
                    lightmap,
                    TextureUsageState::Undefined,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::CopyBufferToTexture(region.clone()),
                CommandOp::Barrier(texture_barrier(
                    lightmap,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
            ];
            let asset = WorldLodColumnAsset {
                column_key: 7,
                column_generation: 3,
                vertex_layout_version: WORLD_LOD_VERTEX_LAYOUT_V1,
                origin: [0, 0, 0],
                segments: vec![WorldLodSegment {
                    layer,
                    vertices: [[0, 0, 0], [2, 0, 0], [2, 2, 0], [0, 2, 0]]
                        .map(|position| WorldLodVertex {
                            local_position: position,
                            packed_light_and_micro_offset: sky,
                            color_rgba: [255; 4],
                            material_id: material,
                            normal_index: 2,
                        })
                        .to_vec(),
                }],
            };
            let assets = BTreeMap::from([(
                7,
                pack_world_lod_gpu_column_asset_from_compact(&asset).unwrap(),
            )]);
            let visible = [WorldLodColumnInstanceRequest {
                column_key: 7,
                column_generation: 3,
                layer,
                segment_index: 0,
                order: 0,
            }];
            let mut residency = WorldLodGpuResidency::default();
            residency
                .stage_visible_uploads(&mut gal, &assets, &visible, &mut ops)
                .unwrap();
            let draw = residency.resolve_visible_draws(&assets, &visible).unwrap()[0];
            let frame = WorldLodRenderFrame {
                enabled: true,
                micro_offset: 0.01,
                combined_matrix: [
                    1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., -1., -1., 0., 1.,
                ],
                ..WorldLodRenderFrame::default()
            };
            let uniforms = WorldLodDrawUniform::from_semantics(&frame, draw).unwrap();
            let mut owner = WorldLodPassResources::new_forward(pass);
            owner.begin_frame();
            let prepared = owner
                .stage_draw(
                    &mut gal,
                    draw,
                    uniforms,
                    contract,
                    VanillaLightmapBinding {
                        world_generation: 17,
                        lightmap_generation: 9,
                        texture_view: view,
                        sampler,
                    },
                    &mut ops,
                )
                .unwrap();
            owner.flush_packed_uniforms(&mut ops);
            ops.extend([
                CommandOp::Barrier(texture_barrier(
                    target.color_texture,
                    TextureUsageState::Undefined,
                    TextureUsageState::ColorAttachment,
                )),
                CommandOp::Barrier(texture_barrier(
                    target.depth_texture,
                    TextureUsageState::Undefined,
                    TextureUsageState::DepthStencilAttachment,
                )),
                CommandOp::BeginPass {
                    pass: target.pass,
                    target: target.target,
                    colors: vec![PassAttachment {
                        view: target.color_view,
                        load_op: AttachmentLoadOp::Clear,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.,
                            g: 0.,
                            b: 0.,
                            a: 1.,
                        }),
                    }],
                    depth_stencil: Some(PassAttachment {
                        view: target.depth_view,
                        load_op: AttachmentLoadOp::Clear,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }),
                },
                CommandOp::BindGraphicsPipeline(prepared.offscreen_pipeline.unwrap()),
                CommandOp::BindResourceSet {
                    set_index: 0,
                    set: prepared.geometry_resource_set,
                    pipeline_layout: prepared.pipeline_layout,
                    dynamic_offsets: prepared.uniform_dynamic_offset.into_iter().collect(),
                },
                CommandOp::BindResourceSet {
                    set_index: 1,
                    set: prepared.lightmap_resource_set,
                    dynamic_offsets: vec![],
                    pipeline_layout: prepared.pipeline_layout,
                },
                CommandOp::SetIndexBuffer {
                    buffer: prepared.index_buffer,
                    offset: prepared.index_offset,
                    index_type: prepared.index_type,
                },
                CommandOp::DrawIndexed {
                    indices: prepared.index_count,
                    instances: 1,
                },
                CommandOp::EndPass,
            ]);
            let readback = gal
                .create_buffer(BufferDesc {
                    label: "dh-skylight.readback".into(),
                    size: 1024,
                    memory: MemoryDomain::Readback,
                    usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
                })
                .unwrap();
            ops.extend([
                CommandOp::Barrier(texture_barrier(
                    target.color_texture,
                    TextureUsageState::ColorAttachment,
                    TextureUsageState::TransferSrc,
                )),
                CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
                    buffer: readback,
                    texture: target.color_texture,
                    ..region
                }),
                CommandOp::Barrier(buffer_barrier(
                    readback,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
                CommandOp::HostReadBuffer {
                    buffer: readback,
                    offset: 0,
                    size: 1024,
                },
            ]);
            let token = gal
                .submit(SubmissionBatch {
                    label: "dh-skylight.native".into(),
                    command_lists: vec![CommandList::from(CommandListDesc {
                        label: "dh-skylight.native".into(),
                        operations: ops,
                    })],
                })
                .unwrap();
            gal.retire_through_for_test(token.submission).unwrap();
            let reads = gal.completed_host_reads();
            let data = &reads.iter().find(|r| r.buffer == readback).unwrap().bytes;
            let expected = if sky == 0 {
                [255, 0, 0, 255]
            } else {
                [0, 255, 0, 255]
            };
            assert_eq!(
                &data[(8 * 16 + 8) * 4..(8 * 16 + 8) * 4 + 4],
                &expected,
                "Frozen OpenGL skylight {sky}, {pass:?} must preserve its source row"
            );
            owner.destroy(&mut gal);
            residency.discard_submission(&mut gal);
            target.destroy(&mut gal);
            for handle in [readback, upload, view, sampler, lightmap] {
                gal.destroy(handle).unwrap();
            }
        }
    }
}
