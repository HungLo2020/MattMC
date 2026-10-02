//! Submission ordering, completion, usage tracking, batch limits and metrics.

use super::*;

#[test]
fn submission_usage_tracks_accepted_work_and_retained_command_copies() {
    let mut gal = gal();
    let usage = SubmissionUsage::default();
    let batch = SubmissionBatch {
        label: "tracked commands".into(),
        command_lists: vec![gal
            .create_command_list(CommandListDesc {
                label: "tracked list".into(),
                operations: vec![CommandOp::TrackSubmission(usage.clone())],
            })
            .unwrap()],
    };
    assert!(usage.has_pending_commands());
    // A failed backend attempt consumes an ID but must not publish accepted use.
    gal.mock_backend_mut().unwrap().fail_next_submit = true;
    assert!(gal.submit(batch.clone()).is_err());
    assert_eq!(usage.last_submission(), SubmissionId(0));
    assert!(
        usage.has_pending_commands(),
        "the retained retry is still pending"
    );
    let first = gal.submit(batch.clone()).unwrap();
    assert_eq!(usage.last_submission(), first.submission);
    gal.retire_through(first.submission).unwrap();
    assert!(
        usage.has_pending_commands(),
        "completed use does not cancel a retained command copy"
    );
    let second = gal.submit(batch).unwrap();
    assert!(second.submission > first.submission);
    assert_eq!(usage.last_submission(), second.submission);
    assert!(!usage.has_pending_commands());
    assert_eq!(
        gal.poll_completed(),
        first.submission,
        "new accepted work remains in flight"
    );
    gal.retire_through(second.submission).unwrap();
    assert_eq!(gal.poll_completed(), usage.last_submission());

    let cancelled = CommandOp::TrackSubmission(usage.clone());
    drop(cancelled);
    assert!(!usage.has_pending_commands());
    assert_eq!(
        usage.last_submission(),
        second.submission,
        "cancellation preserves earlier accepted use"
    );
}

#[test]
fn completion_wait_rejects_unsubmitted_ids_without_fabricating_progress() {
    let mut gal = gal();
    assert!(gal.retire_through(SubmissionId(1)).is_err());
    assert_eq!(gal.poll_completed(), SubmissionId(0));
    assert!(gal.mock_backend().unwrap().retire_requests.is_empty());
    let batch = || SubmissionBatch {
        label: "completion-failure".into(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "empty-but-valid-command-list".into(),
            operations: vec![],
        })],
    };
    gal.mock_backend_mut().unwrap().fail_next_submit = true;
    assert!(gal.submit(batch()).is_err());
    assert_eq!(gal.next_submission_id(), SubmissionId(2));
    assert_eq!(
        gal.latest_submission_id(),
        SubmissionId(0),
        "a failed attempt is not an accepted receipt"
    );
    assert!(gal.retire_through(SubmissionId(1)).is_err());
    assert!(gal.mock_backend().unwrap().retire_requests.is_empty());
    let token = gal.submit(batch()).unwrap();
    assert_eq!(token.submission, SubmissionId(2));
    assert_eq!(gal.latest_submission_id(), token.submission);
    gal.retire_through(token.submission).unwrap();
    assert_eq!(gal.poll_completed(), token.submission);
}

#[test]
fn large_supported_batches_validate_with_backend_limits() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let src = gal
        .create_buffer(BufferDesc {
            label: "large-src".to_owned(),
            size: 4096,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
        })
        .unwrap();
    let dst = gal
        .create_buffer(BufferDesc {
            label: "large-dst".to_owned(),
            size: 4096,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })
        .unwrap();
    let mut lists = Vec::new();
    for index in 0..8 {
        lists.push(
            gal.create_command_list(CommandListDesc {
                label: format!("large-list-{index}"),
                operations: vec![
                    CommandOp::CopyBuffer { src, dst, size: 16 },
                    CommandOp::Barrier(ResourceBarrier {
                        resource: dst,
                        subresources: None,
                        before: TextureUsageState::TransferDst,
                        after: TextureUsageState::TransferDst,
                        src_queue: QueueClass::Graphics,
                        dst_queue: QueueClass::Graphics,
                    }),
                ],
            })
            .unwrap(),
        );
    }
    let token = gal
        .submit(SubmissionBatch {
            label: "large-submit".to_owned(),
            command_lists: lists,
        })
        .unwrap();
    assert_eq!(SubmissionId(1), token.submission);
    assert_eq!(1, gal.metrics().submissions);
}

#[test]
fn command_order_submission_ids_and_deferred_retirement_are_deterministic() {
    let mut gal = gal();
    let src = gal
        .create_buffer(buffer("src", vec![BufferUsage::TransferSrc]))
        .unwrap();
    let dst = gal
        .create_buffer(buffer("dst", vec![BufferUsage::TransferDst]))
        .unwrap();
    let ops = vec![CommandOp::CopyBuffer {
        src,
        dst,
        size: 256,
    }];
    let mut caller_ops = ops.clone();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "copy-list".to_owned(),
            operations: caller_ops.clone(),
        })
        .unwrap();
    caller_ops.clear();
    assert_eq!(list.operations, ops);

    let token = gal
        .submit(SubmissionBatch {
            label: "copy-batch".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    assert_eq!(token.submission.0, 1);
    assert_eq!(gal.mock_backend().unwrap().encoded_batches, 1);
    assert_eq!(
        gal.mock_backend().unwrap().submissions,
        vec![token.submission]
    );
    assert_eq!(
        gal.mock_backend()
            .unwrap()
            .submitted_labels
            .front()
            .unwrap(),
        "copy-batch"
    );

    gal.destroy(src).unwrap();
    assert_eq!(gal.metrics().deferred_retires, 1);
    assert_eq!(gal.metrics().resource_destroys, 0);

    gal.mock_backend_mut()
        .unwrap()
        .complete_through(token.submission);
    assert_eq!(gal.retire_completed().unwrap(), vec![src]);
    assert_eq!(gal.metrics().resource_destroys, 1);
}
