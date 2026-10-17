//! Bounded immutable CPU handoff from visibility selection to frame decode.
//! Decoding clones ownership before queueing; renderer/GAL resource lifetimes
//! remain with their existing owners. No native memory address crosses Java.
use super::{Failure, Frame, Ledger, Result, ROUTE_SELECTED_FLAG};
use crate::render::scene::lod::WorldLodInstances;
use std::sync::atomic::{AtomicU64, Ordering};

const MAX_RETAINED_FRAMES: usize = 3;
static NEXT_FRAME_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Debug)]
pub(crate) struct RetainedVisibleFrame {
    pub id: u64,
    pub lifecycle: i64,
    pub route_frame: i64,
    pub frame: Frame,
    pub instances: WorldLodInstances,
    /// Opaque, non-water transparent, water. Computed with the selected set.
    pub counts: [u32; 3],
}

impl Ledger {
    pub(crate) fn consume_retained_frame(&mut self) -> Result<RetainedVisibleFrame> {
        let selected = self.route.selected && self.frame.enabled && self.frame.flags & ROUTE_SELECTED_FLAG != 0;
        let id = if selected && !self.pending_segments.is_empty() {
            NEXT_FRAME_ID.fetch_update(Ordering::Relaxed, Ordering::Relaxed,
                |id| id.checked_add(1).filter(|&next| next <= i64::MAX as u64))
                .map_err(|_| Failure::FrameReferenceBounds)?
        } else { 0 };
        let (instances, frame) = self.consume_frame();
        if self.last_consumed_counts.iter().sum::<u32>() as usize != instances.len() {
            return Err(Failure::SelectUnsupportedSegments);
        }
        let snapshot = RetainedVisibleFrame { id, lifecycle: self.lifecycle(), route_frame: self.route.frame,
            frame, instances, counts: self.last_consumed_counts };
        if id != 0 {
            self.consumed_frames.push_back(snapshot.clone());
            while self.consumed_frames.len() > MAX_RETAINED_FRAMES { self.consumed_frames.pop_front(); }
        }
        Ok(snapshot)
    }

    /// Exact frame identity, lifecycle, count and selected decision must all
    /// agree. The returned owner stays immutable after eviction or reset.
    pub(crate) fn resolve_retained_frame(&self, id: u64, lifecycle: i64, count: u64,
        enabled: bool, flags: u32) -> Option<WorldLodInstances> {
        if id == 0 || lifecycle != self.lifecycle() || !enabled || flags & ROUTE_SELECTED_FLAG as u32 == 0 {
            return None;
        }
        self.consumed_frames.iter().find(|frame| frame.id == id && frame.lifecycle == lifecycle
            && frame.frame.enabled == enabled && frame.frame.flags as u32 == flags
            && frame.instances.len() as u64 == count).map(|frame| frame.instances.clone())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn selected(ledger: &mut Ledger, generation: i64) -> RetainedVisibleFrame {
        ledger.begin_frame(Some(0), "", "finite", "");
        ledger.append_segments(i64::MIN + 9, generation, 1, &[2, 0]).unwrap();
        ledger.append_segments(9, generation, 3, &[7]).unwrap();
        ledger.append_segments(9, generation, 4, &[1]).unwrap();
        ledger.select_route(false).unwrap();
        ledger.consume_retained_frame().unwrap()
    }

    fn resolve(ledger: &Ledger, frame: &RetainedVisibleFrame) -> Option<WorldLodInstances> {
        ledger.resolve_retained_frame(frame.id, frame.lifecycle, frame.instances.len() as u64,
            frame.frame.enabled, frame.frame.flags as u32)
    }

    #[test]
    fn selected_native_frames_preserve_order_counts_signed_keys_and_generations() {
        let mut ledger = Ledger::new();
        let first = selected(&mut ledger, 11);
        assert_eq!(first.counts, [2, 1, 1]);
        assert_eq!(first.instances[0].column_key as i64, i64::MIN + 9);
        assert_eq!(first.instances.iter().map(|i| (i.layer, i.segment_index, i.order)).collect::<Vec<_>>(),
            vec![(1, 2, 0), (1, 0, 1), (3, 7, 2), (4, 1, 3)]);
        let same = selected(&mut ledger, 11);
        assert!(first.instances.shares_storage(&same.instances));
        assert_ne!(first.id, same.id);
        assert_eq!(ledger.receipts.last_visible_set_change_route_frame, first.route_frame);
        let changed = selected(&mut ledger, 12);
        assert!(!first.instances.shares_storage(&changed.instances));
        assert_eq!(first.instances[0].column_generation, 11);
        assert_eq!(changed.instances[0].column_generation, 12);
        assert_eq!(ledger.receipts.last_visible_set_change_route_frame, changed.route_frame);
    }

    #[test]
    fn native_reference_rejects_wrong_identity_lifecycle_count_and_decision() {
        let mut ledger = Ledger::new();
        let frame = selected(&mut ledger, 1);
        let args = (frame.id, frame.lifecycle, frame.instances.len() as u64, true, frame.frame.flags as u32);
        assert!(resolve(&ledger, &frame).unwrap().shares_storage(&frame.instances));
        assert!(ledger.resolve_retained_frame(0, args.1, args.2, args.3, args.4).is_none());
        assert!(ledger.resolve_retained_frame(u64::MAX, args.1, args.2, args.3, args.4).is_none());
        assert!(ledger.resolve_retained_frame(args.0, args.1 + 1, args.2, args.3, args.4).is_none());
        assert!(ledger.resolve_retained_frame(args.0, args.1, args.2 + 1, args.3, args.4).is_none());
        assert!(ledger.resolve_retained_frame(args.0, args.1, args.2, false, args.4).is_none());
        assert!(ledger.resolve_retained_frame(args.0, args.1, args.2, true, 0).is_none());
        assert!(ledger.resolve_retained_frame(args.0, args.1, args.2, true, args.4 | 1).is_none());
    }

    #[test]
    fn decoded_queued_owner_survives_ring_eviction_reset_and_pass_clear() {
        let mut ledger = Ledger::new();
        let first = selected(&mut ledger, 1);
        let queued = resolve(&ledger, &first).unwrap();
        let mut derived = queued.clone();
        derived.clear();
        assert!(derived.is_empty());
        assert_eq!(queued.len(), 4);
        let second = selected(&mut ledger, 2);
        let third = selected(&mut ledger, 3);
        let fourth = selected(&mut ledger, 4);
        assert_eq!(ledger.consumed_frames.len(), MAX_RETAINED_FRAMES);
        assert!(resolve(&ledger, &first).is_none());
        for frame in [&second, &third, &fourth] { assert!(resolve(&ledger, frame).is_some()); }
        ledger.clear("world-unload", &mut Vec::new());
        assert!(resolve(&ledger, &fourth).is_none());
        assert!(ledger.consumed_frames.is_empty());
        assert_eq!(queued[0].column_generation, 1);
        let after_reset = selected(&mut ledger, 1);
        assert!(after_reset.id > fourth.id);
        ledger.reset_for_test(&mut Vec::new());
        let restarted = selected(&mut ledger, 1);
        assert!(restarted.id > after_reset.id);
        assert!(resolve(&ledger, &after_reset).is_none());
    }

    #[test]
    fn unselected_and_empty_frames_publish_no_reference_and_reset_last_work() {
        let mut ledger = Ledger::new();
        selected(&mut ledger, 1);
        ledger.begin_test_frame(true);
        ledger.append_segments(9, 1, 1, &[0]).unwrap();
        let rejected = ledger.consume_retained_frame().unwrap();
        assert_eq!(rejected.id, 0);
        assert!(rejected.instances.is_empty());
        assert_eq!(rejected.counts, [0; 3]);
        assert!(ledger.last_consumed().is_empty());
        ledger.begin_test_frame(true);
        ledger.select_route(false).unwrap();
        let empty = ledger.consume_retained_frame().unwrap();
        assert_eq!(empty.id, 0);
        assert!(empty.frame.enabled);
        assert_eq!(empty.counts, [0; 3]);
    }

    #[test]
    fn malformed_late_segments_reject_retention_without_poisoning_the_ledger() {
        let mut ledger = Ledger::new();
        ledger.begin_frame(Some(0), "", "finite", "");
        ledger.append_segments(9, 1, 1, &[0]).unwrap();
        ledger.select_route(false).unwrap();
        // Compatibility appenders may add work after route selection. That
        // earlier validation cannot establish the new segment's layer.
        ledger.append_segments(9, 1, 99, &[1]).unwrap();
        assert!(matches!(ledger.consume_retained_frame(), Err(Failure::SelectUnsupportedSegments)));
        assert!(ledger.consumed_frames.is_empty());
        let valid = selected(&mut ledger, 2);
        assert_eq!(valid.counts, [2, 1, 1]);
        assert!(resolve(&ledger, &valid).is_some());
    }

    #[test]
    fn negative_segment_indexes_are_rejected_before_selection() {
        let mut ledger = Ledger::new();
        ledger.begin_test_frame(true);
        assert_eq!(ledger.append_segments(9, 1, 1, &[0, -1]), Err(Failure::VisibleSegmentCapture));
        assert!(ledger.pending_segments().is_empty());
    }
}
