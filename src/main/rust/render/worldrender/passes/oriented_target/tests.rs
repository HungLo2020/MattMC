use crate::render::worldrender::passes::oriented_target::*;
use crate::render::vulkanic::test_support::MockBackend;

fn desc(direction: RasterYDirection) -> WorldTargetDesc {
    WorldTargetDesc {
        extent: Extent3d {
            width: 8,
            height: 8,
            depth: 1,
        },
        color_format: TextureFormat::Rgba8Unorm,
        raster_y_direction: direction,
    }
}

#[test]
fn world_target_allocation_failure_rolls_back_every_partial_owner() {
    for failure in 0..6 {
        let mut backend = MockBackend::default();
        backend.fail_create_after = Some(failure);
        let mut gal = crate::render::vulkanic::test_support::gal_with_mock(backend);
        assert!(
            OrientedWorldTarget::create(&mut gal, "failure", desc(RasterYDirection::Down))
                .is_err()
        );
        assert_eq!(gal.metrics().resource_creates, failure as u64);
        assert_eq!(
            gal.metrics().resource_creates,
            gal.metrics().resource_destroys
        );
        assert!(gal.mock_backend().unwrap().live.is_empty());
        let retry =
            OrientedWorldTarget::create(&mut gal, "retry", desc(RasterYDirection::Down))
                .unwrap();
        for handle in retry.handles_in_destroy_order() {
            gal.destroy(handle).unwrap();
        }
        assert_eq!(
            gal.metrics().resource_creates,
            gal.metrics().resource_destroys
        );
    }
}

#[test]
fn world_target_transfer_declares_both_orientations_and_attachment_states() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    for source_direction in [RasterYDirection::Up, RasterYDirection::Down] {
        for destination_direction in [RasterYDirection::Up, RasterYDirection::Down] {
            let source =
                OrientedWorldTarget::create(&mut gal, "source", desc(source_direction))
                    .unwrap();
            let destination = OrientedWorldTarget::create(
                &mut gal,
                "destination",
                desc(destination_direction),
            )
            .unwrap();
            let operations = source
                .transfer_to(
                    &destination,
                    WorldAttachmentStates::ATTACHMENTS,
                    WorldAttachmentStates::UNDEFINED,
                    WorldAttachmentStates::ATTACHMENTS,
                )
                .unwrap();
            assert_eq!(operations.len(), 8);
            for (offset, src, dst, state) in [
                (
                    0,
                    source.color_texture,
                    destination.color_texture,
                    TextureUsageState::ColorAttachment,
                ),
                (
                    4,
                    source.depth_texture,
                    destination.depth_texture,
                    TextureUsageState::DepthStencilAttachment,
                ),
            ] {
                let CommandOp::Barrier(before) = &operations[offset] else {
                    panic!("source barrier missing")
                };
                assert_eq!(
                    (before.resource, before.before, before.after),
                    (src, state, TextureUsageState::TransferSrc)
                );
                let CommandOp::CopyTexture(copy) = &operations[offset + 2] else {
                    panic!("explicit copy missing")
                };
                assert_eq!((copy.src_texture, copy.dst_texture), (src, dst));
                assert_eq!(
                    copy.row_order,
                    if source_direction == destination_direction {
                        TextureRowOrder::Preserve
                    } else {
                        TextureRowOrder::Reverse
                    }
                );
                let CommandOp::Barrier(after) = &operations[offset + 3] else {
                    panic!("destination barrier missing")
                };
                assert_eq!(
                    (after.resource, after.before, after.after),
                    (dst, TextureUsageState::TransferDst, state)
                );
            }
            assert!(source
                .transfer_to(
                    &source,
                    WorldAttachmentStates::ATTACHMENTS,
                    WorldAttachmentStates::UNDEFINED,
                    WorldAttachmentStates::ATTACHMENTS
                )
                .is_err());
            for target in [destination, source] {
                for handle in target.handles_in_destroy_order() {
                    gal.destroy(handle).unwrap();
                }
            }
        }
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn borrowed_world_image_pairs_reject_aliasing_before_recording_copies() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let source =
        OrientedWorldTarget::create(&mut gal, "source", desc(RasterYDirection::Down)).unwrap();
    let destination =
        OrientedWorldTarget::create(&mut gal, "destination", desc(RasterYDirection::Up))
            .unwrap();
    for (color_texture, depth_texture) in [
        (source.color_texture, destination.depth_texture),
        (destination.color_texture, source.depth_texture),
        (source.depth_texture, destination.depth_texture),
        (destination.color_texture, source.color_texture),
        (destination.depth_texture, destination.depth_texture),
        (Handle::NULL, destination.depth_texture),
    ] {
        let alias = WorldAttachmentImages {
            color_texture,
            depth_texture,
            ..destination.images()
        };
        assert!(source
            .images()
            .transfer_to(
                alias,
                WorldAttachmentStates::ATTACHMENTS,
                WorldAttachmentStates::UNDEFINED,
                WorldAttachmentStates::ATTACHMENTS
            )
            .is_err());
    }
    for owner in [destination, source] {
        for handle in owner.handles_in_destroy_order() {
            gal.destroy(handle).unwrap();
        }
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn world_target_resize_and_format_changes_require_distinct_compatible_owners() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let original_desc = desc(RasterYDirection::Down);
    let original = OrientedWorldTarget::create(&mut gal, "original", original_desc).unwrap();
    for replacement_desc in [
        WorldTargetDesc {
            extent: Extent3d {
                width: 16,
                height: 8,
                depth: 1,
            },
            ..original_desc
        },
        WorldTargetDesc {
            color_format: TextureFormat::Rgba16Float,
            ..original_desc
        },
    ] {
        let replacement =
            OrientedWorldTarget::create(&mut gal, "replacement", replacement_desc).unwrap();
        assert!(original
            .transfer_to(
                &replacement,
                WorldAttachmentStates::ATTACHMENTS,
                WorldAttachmentStates::UNDEFINED,
                WorldAttachmentStates::ATTACHMENTS
            )
            .is_err());
        assert_eq!(
            gal.pass_target_extent(original.target).unwrap(),
            original_desc.extent
        );
        assert_eq!(
            gal.pass_target_color_format(original.target).unwrap(),
            original_desc.color_format
        );
        for handle in replacement.handles_in_destroy_order() {
            gal.destroy(handle).unwrap();
        }
    }
    for handle in original.handles_in_destroy_order() {
        gal.destroy(handle).unwrap();
    }
    let creates = gal.metrics().resource_creates;
    for invalid_desc in [
        WorldTargetDesc {
            extent: Extent3d {
                width: 0,
                height: 8,
                depth: 1,
            },
            ..original_desc
        },
        WorldTargetDesc {
            extent: Extent3d {
                width: 8,
                height: 8,
                depth: 2,
            },
            ..original_desc
        },
        WorldTargetDesc {
            color_format: TextureFormat::Depth32Float,
            ..original_desc
        },
    ] {
        assert!(OrientedWorldTarget::create(&mut gal, "invalid", invalid_desc).is_err());
        assert_eq!(gal.metrics().resource_creates, creates);
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
    assert!(gal.mock_backend().unwrap().live.is_empty());
}
