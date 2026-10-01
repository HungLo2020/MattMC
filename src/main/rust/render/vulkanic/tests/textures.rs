//! Texture copies, row reversal, depth snapshots, 3D textures and mip generation.

use super::*;

#[test]
fn buffer_texture_copy_commands_validate_usage_ranges_and_hazards() {
    let mut gal = gal();
    let upload = gal
        .create_buffer(buffer("texture-upload", vec![BufferUsage::TransferSrc]))
        .unwrap();
    let readback = gal
        .create_buffer(buffer("texture-readback", vec![BufferUsage::TransferDst]))
        .unwrap();
    let texture_handle = gal
        .create_texture(texture(
            "copy-texture",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
        ))
        .unwrap();
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
    let region = BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row: 16,
        rows_per_image: 4,
        texture: texture_handle,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
    };

    let read_region = BufferImageCopyRegion {
        buffer: readback,
        ..region.clone()
    };
    gal.create_command_list(CommandListDesc {
        label: "buffer-texture-transfer-with-barrier".to_owned(),
        operations: vec![
            CommandOp::CopyBufferToTexture(region.clone()),
            CommandOp::Barrier(ResourceBarrier {
                resource: texture_handle,
                subresources: Some(full_range),
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::TransferSrc,
                src_queue: QueueClass::Transfer,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::CopyTextureToBuffer(read_region.clone()),
        ],
    })
    .unwrap();

    let no_barrier = gal
        .create_command_list(CommandListDesc {
            label: "buffer-texture-transfer-without-barrier".to_owned(),
            operations: vec![
                CommandOp::CopyBufferToTexture(region.clone()),
                CommandOp::CopyTextureToBuffer(read_region.clone()),
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "buffer-texture-hazard".to_owned(),
            command_lists: vec![no_barrier],
        }),
        super::StatusCode::InvalidArgument,
    );

    let transfer_src_only = gal
        .create_texture(texture(
            "transfer-src-only-texture",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferSrc],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-to-texture-without-dst-usage".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture: transfer_src_only,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-to-texture-bad-row-layout".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                bytes_per_row: 18,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-to-texture-outside-extent".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture_origin: TextureOrigin3d { x: 127, y: 0, z: 0 },
                ..region
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn texture_row_reversal_is_capability_checked_and_retains_copy_validation() {
    for supported in [false, true] {
        let mut caps = vulkan_capabilities();
        caps.features.texture_row_reversal = supported;
        let mut gal = gal_with_capabilities(caps);
        let source = gal
            .create_texture(texture(
                "row-source",
                TextureFormat::Rgba8Unorm,
                vec![TextureUsage::TransferSrc],
            ))
            .unwrap();
        let destination = gal
            .create_texture(texture(
                "row-destination",
                TextureFormat::Rgba8Unorm,
                vec![TextureUsage::TransferDst],
            ))
            .unwrap();
        let region = TextureImageCopyRegion {
            row_order: TextureRowOrder::Reverse,
            src_texture: source,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: destination,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: Extent3d {
                width: 2,
                height: 3,
                depth: 1,
            },
        };
        let list = |region| CommandListDesc {
            label: "reverse-rows".into(),
            operations: vec![CommandOp::CopyTexture(region)],
        };
        if supported {
            gal.create_command_list(list(region.clone())).unwrap();
            assert_code(
                gal.create_command_list(list(TextureImageCopyRegion {
                    dst_texture: source,
                    ..region.clone()
                })),
                StatusCode::InvalidArgument,
            );
            assert_code(
                gal.create_command_list(list(TextureImageCopyRegion {
                    dst_origin: TextureOrigin3d { x: 0, y: 127, z: 0 },
                    ..region
                })),
                StatusCode::InvalidArgument,
            );
        } else {
            assert_code(
                gal.create_command_list(list(region.clone())),
                StatusCode::UnsupportedFeature,
            );
            gal.create_command_list(list(TextureImageCopyRegion {
                row_order: TextureRowOrder::Preserve,
                ..region
            }))
            .unwrap();
        }
    }
}

#[test]
fn vulkan_texture_row_reversal_copies_exact_asymmetric_color_and_depth_rows() {
    use crate::render::vulkanic::backends::vulkan::VulkanBackend;
    // This is deliberately a real device test: inability to create the backend
    // must not be reported as a passing pixel comparison.
    let backend = VulkanBackend::new("explicit row reversal conformance").unwrap();
    let mut gal = VulkanicGal::new_with_backend(Box::new(backend));
    for format in [TextureFormat::Rgba8Unorm, TextureFormat::Depth32Float] {
        let extent = Extent3d {
            width: 2,
            height: 3,
            depth: 1,
        };
        let pixels: Vec<u8> = if format == TextureFormat::Depth32Float {
            [0.125f32, 0.25, 0.375, 0.5, 0.625, 0.875]
                .into_iter()
                .flat_map(f32::to_le_bytes)
                .collect()
        } else {
            (1u8..=24).collect()
        };
        let source = gal
            .create_texture(TextureDesc {
                label: "row-source".into(),
                dimension: TextureDimension::D2,
                format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
            })
            .unwrap();
        let destination = gal
            .create_texture(TextureDesc {
                label: "row-destination".into(),
                dimension: TextureDimension::D2,
                format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
            })
            .unwrap();
        let upload = gal
            .create_buffer(BufferDesc {
                label: "row-upload".into(),
                size: 24,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
            })
            .unwrap();
        let readback = gal
            .create_buffer(BufferDesc {
                label: "row-readback".into(),
                size: 24,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
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
        let copy = |buffer, texture| BufferImageCopyRegion {
            buffer,
            buffer_offset: 0,
            bytes_per_row: 8,
            rows_per_image: 3,
            texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        };
        let list = gal
            .create_command_list(CommandListDesc {
                label: "explicit-row-reversal".into(),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: upload,
                        offset: 0,
                        data: pixels.clone(),
                    },
                    barrier(
                        upload,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    ),
                    barrier(
                        source,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    ),
                    CommandOp::CopyBufferToTexture(copy(upload, source)),
                    barrier(
                        source,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    ),
                    barrier(
                        destination,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    ),
                    CommandOp::CopyTexture(TextureImageCopyRegion {
                        row_order: TextureRowOrder::Reverse,
                        src_texture: source,
                        src_mip: 0,
                        src_layer: 0,
                        src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        dst_texture: destination,
                        dst_mip: 0,
                        dst_layer: 0,
                        dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent,
                    }),
                    barrier(
                        destination,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    ),
                    CommandOp::CopyTextureToBuffer(copy(readback, destination)),
                    barrier(
                        readback,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    ),
                    CommandOp::HostReadBuffer {
                        buffer: readback,
                        offset: 0,
                        size: 24,
                    },
                ],
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "explicit-row-reversal".into(),
                command_lists: vec![list],
            })
            .unwrap();
        gal.retire_through_for_test(token.submission).unwrap();
        let reads = gal.completed_host_reads();
        let actual = &reads
            .iter()
            .rev()
            .find(|read| read.buffer == readback)
            .unwrap()
            .bytes;
        let expected: Vec<u8> = pixels.chunks_exact(8).rev().flatten().copied().collect();
        assert_eq!(
            &expected, actual,
            "row reversal must preserve exact texels for {format:?}"
        );
        for handle in [source, destination, upload, readback] {
            gal.destroy(handle).unwrap();
        }
    }
}

#[test]
fn texture_copy_validates_depth_snapshot_regions_and_transfer_hazards() {
    let mut gal = gal();
    let source = gal
        .create_texture(texture(
            "depth-copy-source",
            TextureFormat::Depth32Float,
            vec![TextureUsage::TransferSrc],
        ))
        .unwrap();
    let destination = gal
        .create_texture(texture(
            "depth-copy-destination",
            TextureFormat::Depth32Float,
            vec![TextureUsage::TransferDst],
        ))
        .unwrap();
    let region = TextureImageCopyRegion {
        row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        src_texture: source,
        src_mip: 0,
        src_layer: 0,
        src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        dst_texture: destination,
        dst_mip: 0,
        dst_layer: 0,
        dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
    };
    gal.create_command_list(CommandListDesc {
        label: "depth-snapshot-copy".to_owned(),
        operations: vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: source,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferSrc,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: destination,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferDst,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::CopyTexture(region.clone()),
        ],
    })
    .unwrap();

    let rgba_destination = gal
        .create_texture(texture(
            "depth-copy-rgba-destination",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferDst],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "depth-copy-format-mismatch".to_owned(),
            operations: vec![CommandOp::CopyTexture(TextureImageCopyRegion {
                dst_texture: rgba_destination,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "depth-copy-outside-extent".to_owned(),
            operations: vec![CommandOp::CopyTexture(TextureImageCopyRegion {
                dst_origin: TextureOrigin3d { x: 127, y: 0, z: 0 },
                ..region
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn d3_texture_validation_enforces_dimension_layers_mips_and_copy_boxes() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let upload = gal
        .create_buffer(buffer("d3-upload", vec![BufferUsage::TransferSrc]))
        .unwrap();
    let texture = gal
        .create_texture(TextureDesc {
            label: "d3-r8".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 2,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::TransferDst,
                TextureUsage::TransferSrc,
                TextureUsage::Sampled,
            ],
        })
        .unwrap();
    let region = BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row: 4,
        rows_per_image: 4,
        texture,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 2,
        },
    };
    gal.create_command_list(CommandListDesc {
        label: "d3-full-upload".to_owned(),
        operations: vec![CommandOp::CopyBufferToTexture(region.clone())],
    })
    .unwrap();

    assert_code(
        gal.create_texture_view(TextureViewDesc {
            label: "d3-invalid-layer-view".to_owned(),
            texture,
            format: TextureFormat::R8Uint,
            base_mip: 0,
            mip_count: 1,
            base_layer: 1,
            layer_count: 1,
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-invalid-array-layer-copy".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture_layer: 1,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-invalid-partial-row".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                bytes_per_row: 3,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-invalid-depth-box".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 1 },
                ..region
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn d3_texture_lifecycle_validates_mips_storage_transitions_and_retirement() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let texture = gal
        .create_texture(TextureDesc {
            label: "d3-rgba16-storage".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::Rgba16Float,
            extent: Extent3d {
                width: 8,
                height: 4,
                depth: 2,
            },
            mip_levels: 2,
            array_layers: 1,
            usages: vec![
                TextureUsage::Sampled,
                TextureUsage::Storage,
                TextureUsage::TransferDst,
            ],
        })
        .unwrap();
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 2,
        base_layer: 0,
        layer_count: 1,
    };
    let list = gal
        .create_command_list(CommandListDesc {
            label: "d3-storage-transition".to_owned(),
            operations: vec![
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(full_range),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::ShaderWrite,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(full_range),
                    before: TextureUsageState::ShaderWrite,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ],
        })
        .unwrap();
    let submission = gal
        .submit(SubmissionBatch {
            label: "d3-storage-transition-submit".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    gal.destroy(texture).unwrap();
    assert!(gal
        .retire_through_for_test(submission.submission)
        .unwrap()
        .contains(&texture));
    assert_code(
        gal.create_texture_view(TextureViewDesc {
            label: "stale-d3-view".to_owned(),
            texture,
            format: TextureFormat::Rgba16Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }),
        super::StatusCode::StaleHandle,
    );

    assert_code(
        gal.create_texture(TextureDesc {
            label: "d3-too-many-mips".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 4,
            },
            mip_levels: 4,
            array_layers: 1,
            usages: vec![TextureUsage::Sampled],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn mip_generation_requires_explicit_ranges_usages_and_non_integer_color_formats() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let texture = gal
        .create_texture(TextureDesc {
            label: "mip-generation-color".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 16,
                height: 8,
                depth: 1,
            },
            mip_levels: 5,
            array_layers: 1,
            usages: vec![
                TextureUsage::TransferSrc,
                TextureUsage::TransferDst,
                TextureUsage::Sampled,
            ],
        })
        .unwrap();
    let base_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
    let descendant_range = TextureSubresourceRange {
        base_mip: 1,
        mip_count: 4,
        base_layer: 0,
        layer_count: 1,
    };
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 5,
        base_layer: 0,
        layer_count: 1,
    };
    let list = gal
        .create_command_list(CommandListDesc {
            label: "mip-generation-valid".to_owned(),
            operations: vec![
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(base_range),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::TransferSrc,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(descendant_range),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::TransferDst,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::GenerateMipmaps {
                    texture,
                    subresources: full_range,
                },
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(full_range),
                    before: TextureUsageState::TransferSrc,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ],
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "mip-generation-valid-submit".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "mip-generation-one-level".to_owned(),
            operations: vec![CommandOp::GenerateMipmaps {
                texture,
                subresources: base_range,
            }],
        }),
        super::StatusCode::InvalidArgument,
    );

    let integer = gal
        .create_texture(TextureDesc {
            label: "mip-generation-integer".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 1,
            },
            mip_levels: 3,
            array_layers: 1,
            usages: vec![TextureUsage::TransferSrc, TextureUsage::TransferDst],
        })
        .unwrap();
    assert_unsupported(
        gal.create_command_list(CommandListDesc {
            label: "mip-generation-integer-command".to_owned(),
            operations: vec![CommandOp::GenerateMipmaps {
                texture: integer,
                subresources: TextureSubresourceRange {
                    base_mip: 0,
                    mip_count: 3,
                    base_layer: 0,
                    layer_count: 1,
                },
            }],
        }),
        "non-depth, non-integer",
    );
}

#[test]
fn rejected_d3_copy_does_not_poison_the_following_valid_submission() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let upload = gal
        .create_buffer(BufferDesc {
            label: "d3-rollback-upload".to_owned(),
            size: 32,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
        })
        .unwrap();
    let texture = gal
        .create_texture(TextureDesc {
            label: "d3-rollback-volume".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 2,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::TransferDst, TextureUsage::Sampled],
        })
        .unwrap();
    let region = BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row: 4,
        rows_per_image: 4,
        texture,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 2,
        },
    };
    let subresources = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-rejected-copy".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                bytes_per_row: 3,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );

    let list = gal
        .create_command_list(CommandListDesc {
            label: "d3-valid-copy-after-rejection".to_owned(),
            operations: vec![
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(subresources),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::TransferDst,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::CopyBufferToTexture(region),
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(subresources),
                    before: TextureUsageState::TransferDst,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ],
        })
        .unwrap();
    let submission = gal
        .submit(SubmissionBatch {
            label: "d3-valid-copy-after-rejection-submit".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    gal.destroy(texture).unwrap();
    gal.destroy(upload).unwrap();
    let retired = gal.retire_through_for_test(submission.submission).unwrap();
    assert!(retired.contains(&texture));
    assert!(retired.contains(&upload));
}
