//! Frame-start clearing must reset stale images without erasing retained history.
use super::*;
use crate::render::vulkanic::commands::{BufferImageCopyRegion, TextureRowOrder};
use crate::render::vulkanic::resources::{BufferDesc, BufferUsage, MemoryDomain};

fn fixture() -> (ShaderPackSource, ShaderPackColorTargetManifest) {
    let source = ShaderPackSource::new("frame-start-clears", 7, vec![
        ShaderSourceFile::new(PIPELINE_SETTINGS_PATH, concat!(
            "const int colortex0Format=RGBA8;\nconst int colortex1Format=RGBA8;\nconst int colortex2Format=RGBA8;\n",
            "const bool colortex0Clear=true;\nconst bool colortex1Clear=false;\n",
            "const vec4 colortex2ClearColor=vec4(0.0,1.0,0.0,1.0);\n")),
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH,
            bindings().replace("previous_depth", "history").replace("temporal_aa", "custom")),
    ]).unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let manifest = ShaderPackColorTargetManifest::from_source(&source, &bindings).unwrap();
    (source, manifest)
}

fn identity(source: &ShaderPackSource, world: u64, size: u32) -> ShaderPackColorTargetIdentity {
    ShaderPackColorTargetIdentity::new(
        world,
        source.generation(),
        Extent3d {
            width: size,
            height: size,
            depth: 1,
        },
        ["primary".into(), "history".into()],
        ["primary".into(), "history".into()],
    )
    .unwrap()
}

fn barrier(texture: Handle, before: TextureUsageState, after: TextureUsageState) -> CommandOp {
    CommandOp::Barrier(ResourceBarrier {
        resource: texture,
        subresources: Some(TextureSubresourceRange {
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }),
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    })
}

fn submit(gal: &mut VulkanicGal, operations: Vec<CommandOp>) {
    let token = gal
        .submit(SubmissionBatch {
            label: "frame-start-clear".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "frame-start-clear".into(),
                operations,
            })],
        })
        .unwrap();
    gal.retire_through_for_test(token.submission).unwrap();
}

#[test]
fn frame_start_clears_reset_both_sides_preserve_history_and_rebuild_mips_on_native_device() {
    let (source, manifest) = fixture();
    let mut gal =
        crate::render::vulkanic::test_support::vulkan_gal("frame-start-clear-pixels").unwrap();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache
        .stage(&mut gal, identity(&source, 1, 8), &manifest)
        .unwrap();
    let primary = targets.target("primary").unwrap();
    let history = targets.target("history").unwrap();
    let mut first = cache.begin_frame(&targets).unwrap();
    let mut operations = Vec::new();
    first
        .append_frame_start_clears(
            &targets,
            ShaderPackColorClearValues {
                fog_color: ClearColor {
                    r: 1.0,
                    g: 0.0,
                    b: 0.0,
                    a: 0.0,
                },
            },
            &mut operations,
        )
        .unwrap();
    // Seed retained history with actual red pixels, different from slot one's
    // default white. A warm clear of clear=false would now be observable.
    for destination in [history.current_texture, history.previous_texture.unwrap()] {
        operations.extend([
            barrier(
                primary.current_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferSrc,
            ),
            barrier(
                destination,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            ),
            CommandOp::CopyTexture(TextureImageCopyRegion {
                row_order: TextureRowOrder::Preserve,
                src_texture: primary.current_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: destination,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: targets.identity.extent,
            }),
            barrier(
                destination,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            ),
            barrier(
                primary.current_texture,
                TextureUsageState::TransferSrc,
                TextureUsageState::ShaderRead,
            ),
        ]);
    }
    let roles = [
        TerrainSourceResourceRole::ShaderPackColor("primary".into()),
        TerrainSourceResourceRole::ShaderPackColor("history".into()),
    ];
    first
        .append_mipmaps(&targets, &roles, &mut operations)
        .unwrap();
    first
        .append_feedback_mipmaps(&targets, &roles, &mut operations)
        .unwrap();
    submit(&mut gal, operations);
    cache.confirm_frame_submission(&mut gal, first).unwrap();

    let creates = gal.metrics().resource_creates;
    let mut warm = cache.begin_frame(&targets).unwrap();
    let mut operations = Vec::new();
    warm.append_frame_start_clears(
        &targets,
        ShaderPackColorClearValues {
            fog_color: ClearColor {
                r: 0.0,
                g: 0.0,
                b: 1.0,
                a: 0.0,
            },
        },
        &mut operations,
    )
    .unwrap();
    assert_eq!(
        creates,
        gal.metrics().resource_creates,
        "warm clears reuse cached passes"
    );
    let primary_mips_invalid = [false, true].map(|previous|
        warm.require_sample_with_mips(&roles[0], previous, true).is_err());
    for previous in [false, true] {
        warm.require_sample_with_mips(&roles[1], previous, true)
            .unwrap();
    }
    warm.append_mipmaps(&targets, &roles[..1], &mut operations)
        .unwrap();
    warm.append_feedback_mipmaps(&targets, &roles[..1], &mut operations)
        .unwrap();
    let custom = targets.target("custom").unwrap();
    let mut reads = Vec::new();
    for (name, texture, mip, expected) in [
        ("current", primary.current_texture, 0, [0, 0, 255, 255]),
        (
            "previous",
            primary.previous_texture.unwrap(),
            0,
            [0, 0, 255, 255],
        ),
        ("current-mip", primary.current_texture, 1, [0, 0, 255, 255]),
        (
            "previous-mip",
            primary.previous_texture.unwrap(),
            1,
            [0, 0, 255, 255],
        ),
        (
            "history-current",
            history.current_texture,
            0,
            [255, 0, 0, 255],
        ),
        (
            "history-previous",
            history.previous_texture.unwrap(),
            0,
            [255, 0, 0, 255],
        ),
        ("custom", custom.current_texture, 0, [0, 255, 0, 255]),
    ] {
        let size = 8 >> mip;
        let buffer = gal
            .create_buffer(BufferDesc {
                label: format!("{name}.readback"),
                size: u64::from(size * size * 4),
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
        let image_barrier = |before, after| {
            CommandOp::Barrier(ResourceBarrier {
                resource: texture,
                subresources: Some(TextureSubresourceRange {
                    base_mip: mip,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                }),
                before,
                after,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            })
        };
        operations.extend([
            image_barrier(
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferSrc,
            ),
            CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
                buffer,
                buffer_offset: 0,
                bytes_per_row: size * 4,
                rows_per_image: size,
                texture,
                texture_mip: mip,
                texture_layer: 0,
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: Extent3d {
                    width: size,
                    height: size,
                    depth: 1,
                },
            }),
            image_barrier(
                TextureUsageState::TransferSrc,
                TextureUsageState::ShaderRead,
            ),
            CommandOp::Barrier(ResourceBarrier {
                resource: buffer,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ShaderRead,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }),
            CommandOp::HostReadBuffer {
                buffer,
                offset: 0,
                size: u64::from(size * size * 4),
            },
        ]);
        reads.push((name, buffer, expected));
    }
    submit(&mut gal, operations);
    cache.confirm_frame_submission(&mut gal, warm).unwrap();
    let completed = gal.completed_host_reads();
    for (name, buffer, expected) in &reads {
        let bytes = &completed
            .iter()
            .find(|read| read.buffer == *buffer)
            .unwrap()
            .bytes;
        for pixel in bytes.chunks_exact(4) {
            assert_eq!(&expected[..], pixel, "{name}");
        }
    }
    for (_, buffer, _) in reads {
        gal.destroy(buffer).unwrap();
    }
    assert!(primary_mips_invalid.into_iter().all(|invalid| invalid));
    cache.destroy(&mut gal);
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn frame_start_clears_reuse_passes_and_preserve_confirmed_state_after_rejected_resize() {
    let (source, manifest) = fixture();
    let mut gal = gal();
    let mut cache = ShaderPackColorTargetCache::default();
    let targets = cache
        .stage(&mut gal, identity(&source, 1, 8), &manifest)
        .unwrap();
    let values = ShaderPackColorClearValues {
        fog_color: ClearColor {
            r: 0.2,
            g: 0.3,
            b: 0.4,
            a: 0.0,
        },
    };
    let creates = gal.metrics().resource_creates;
    for _ in 0..16 {
        let mut frame = cache.begin_frame(&targets).unwrap();
        let mut operations = Vec::new();
        frame
            .append_frame_start_clears(&targets, values, &mut operations)
            .unwrap();
        let count = operations.len();
        assert!(frame
            .append_frame_start_clears(&targets, values, &mut operations)
            .is_err());
        assert_eq!(
            count,
            operations.len(),
            "duplicate frame clears cannot append work"
        );
        submit(&mut gal, operations);
        cache.confirm_frame_submission(&mut gal, frame).unwrap();
    }
    assert_eq!(creates, gal.metrics().resource_creates);
    let confirmed = cache.confirmed_frame.clone();
    let replacement = cache
        .stage(&mut gal, identity(&source, 1, 16), &manifest)
        .unwrap();
    let mut frame = cache.begin_frame(&replacement).unwrap();
    assert!(frame.requires_initial_clear().unwrap());
    let mut operations = Vec::new();
    frame
        .append_frame_start_clears(&replacement, values, &mut operations)
        .unwrap();
    operations.push(CommandOp::EndPass); // Actual rejected GAL command list.
    assert!(gal
        .submit(SubmissionBatch {
            label: "rejected-color-resize".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "rejected-color-resize".into(),
                operations
            })]
        })
        .is_err());
    cache.discard_submission(&mut gal);
    assert_eq!(confirmed, cache.confirmed_frame);
    let restored = cache
        .stage(&mut gal, identity(&source, 1, 8), &manifest)
        .unwrap();
    assert_eq!(targets.target("primary"), restored.target("primary"));
    for (world, size) in [(1, 16), (2, 16)] {
        let replacement = cache
            .stage(&mut gal, identity(&source, world, size), &manifest)
            .unwrap();
        let mut frame = cache.begin_frame(&replacement).unwrap();
        assert!(frame.requires_initial_clear().unwrap());
        let mut operations = Vec::new();
        frame
            .append_frame_start_clears(&replacement, values, &mut operations)
            .unwrap();
        submit(&mut gal, operations);
        cache.confirm_frame_submission(&mut gal, frame).unwrap();
    }
    cache.destroy(&mut gal);
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}
