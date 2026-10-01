//! Frame surface lifecycle: configuration bounds, acquire, resize, present and cancel.

use super::*;

#[test]
fn frame_lifecycle_requires_presentation_capability() {
    let mut gal = gal();
    assert_unsupported(
        gal.configure_frame_surface(frame_surface("headless")),
        "presentation support",
    );
    assert_unsupported(
        gal.acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(1),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        }),
        "presentation support",
    );
}

#[test]
fn frame_surface_rejects_excessive_in_flight_slots() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    let mut surface = frame_surface("bounded-slots");
    surface.max_frames_in_flight = crate::render::vulkanic::gal::MAX_FRAMES_IN_FLIGHT + 1;
    assert_code(
        gal.configure_frame_surface(surface),
        StatusCode::InvalidArgument,
    );
}

#[test]
fn frame_surface_rejects_oversized_or_non_2d_extent() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    let mut oversized = frame_surface("oversized-surface");
    oversized.extent.width = crate::render::vulkanic::gal::MAX_FRAME_SURFACE_AXIS + 1;
    assert_code(
        gal.configure_frame_surface(oversized),
        StatusCode::InvalidArgument,
    );

    let mut three_dimensional = frame_surface("3d-surface");
    three_dimensional.extent.depth = 2;
    assert_code(
        gal.configure_frame_surface(three_dimensional),
        StatusCode::InvalidArgument,
    );
}

#[test]
fn frame_lifecycle_preserves_correlation_and_submission_ids() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("window"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(77),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    assert_eq!(acquired.status, FrameAcquireStatus::Ready);
    assert_eq!(acquired.correlation_id, FrameCorrelationId(77));
    assert_eq!(
        acquired.render_target,
        FrameRenderTargetId(acquired.frame.0)
    );
    let presented = gal
        .present_frame(PresentFrameDesc {
            frame: acquired.frame,
            correlation_id: acquired.correlation_id,
            wait_for: SubmissionId(9),
        })
        .unwrap();
    assert_eq!(presented.status, FramePresentStatus::Presented);
    assert_eq!(presented.completed_submission, SubmissionId(9));
    assert_eq!(gal.poll_completed(), SubmissionId(9));
}

#[test]
fn frame_lifecycle_models_resize_and_minimized_windows() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("resize"))
        .unwrap();
    let resize = gal
        .resize_frame_surface(FrameResizeDesc {
            correlation_id: FrameCorrelationId(2),
            extent: Extent3d {
                width: 256,
                height: 144,
                depth: 1,
            },
        })
        .unwrap();
    assert_eq!(resize.status, FrameAcquireStatus::Resized);
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(3),
            expected_extent: resize.extent,
        })
        .unwrap();
    assert_eq!(acquired.extent, resize.extent);
    let minimized = gal
        .resize_frame_surface(FrameResizeDesc {
            correlation_id: FrameCorrelationId(4),
            extent: Extent3d {
                width: 0,
                height: 0,
                depth: 1,
            },
        })
        .unwrap();
    assert_eq!(minimized.status, FrameAcquireStatus::Minimized);
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(5),
            expected_extent: minimized.extent,
        })
        .unwrap();
    assert_eq!(acquired.status, FrameAcquireStatus::Minimized);
}

#[test]
fn frame_lifecycle_rejects_present_without_acquire() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("bad-present"))
        .unwrap();
    assert_code(
        gal.present_frame(PresentFrameDesc {
            frame: FrameId(99),
            correlation_id: FrameCorrelationId(6),
            wait_for: SubmissionId(1),
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn frame_lifecycle_cancel_releases_an_acquired_frame() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("cancel"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(8),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    gal.cancel_frame(acquired.frame).unwrap();
    assert_code(
        gal.present_frame(PresentFrameDesc {
            frame: acquired.frame,
            correlation_id: acquired.correlation_id,
            wait_for: SubmissionId(1),
        }),
        super::StatusCode::InvalidArgument,
    );
}
