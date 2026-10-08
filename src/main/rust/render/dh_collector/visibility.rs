//! The ledger's half of DH's per-frame render list (`RenderBufferHandler`
//! and `LodRenderer`): from the quadtree walk's candidate columns, one call
//! requests publication, records visibility, sorts near to far and admits the
//! visible segments, in the order Java made those calls one column at a time.
use super::{Counts, Failure, Ledger, Result, Visible};

/// `MAX_PENDING_VISIBLE_COLUMN_KEYS`: larger candidate lists are not recorded.
const MAX_RECORDED_CANDIDATES: usize = 16_384;

/// `DhSectionPos.getCenterBlockPosX/Z`.
pub(crate) fn section_center(pos: i64) -> [i32; 2] {
    let detail = (pos & 0x7F) as i32;
    let x = (((pos >> 8) & 0x0FFF_FFFF) as i32) << 4 >> 4;
    let z = (((pos >> 36) & 0x0FFF_FFFF) as i32) << 4 >> 4;
    // `BitShiftUtil.powerOfTwo`: Java's `1 << n` uses the low five bits of n.
    let power = |n: i32| 1i32.wrapping_shl(n as u32);
    let center = |value: i32| match detail {
        0 => value,
        1 => value.wrapping_mul(2),
        _ => value.wrapping_mul(power(detail)).wrapping_add(power(detail - 1)),
    };
    [center(x), center(z)]
}

/// `Pos2D.manhattanDist` with Java's `int` arithmetic.
fn manhattan(a: [i32; 2], b: [i32; 2]) -> i32 {
    a[0].wrapping_sub(b[0]).wrapping_abs().wrapping_add(a[1].wrapping_sub(b[1]).wrapping_abs())
}

/// One frame's render-list result.
#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct VisibleFrame {
    /// Candidate columns near to far.
    pub sorted: Vec<i64>,
    pub unpublished: i32,
    /// Publication requests that failed (Java logged and skipped each one),
    /// with the first failure.
    pub request_failures: i32,
    pub first_request_failure: Option<(i64, Failure)>,
    /// Admitted segments, when admission ran.
    pub counts: Counts,
}

impl Ledger {
    /// `buildRenderList`'s publication requests and visibility record, then
    /// (with `admit`) `recordVisibleMaterialColumn` for each column in order.
    /// `enabled` is the collector's `enabled()`; `center` the quadtree's
    /// center block. An admission failure ends the call, as Java's exception did.
    pub(crate) fn collect_visible_frame(&mut self, candidates: &[i64], center: [i32; 2], enabled: bool, admit: bool) -> Result<VisibleFrame> {
        let mut frame = VisibleFrame::default();
        for &key in candidates {
            if self.has_published_column(key) {
                continue;
            }
            if !enabled {
                frame.unpublished += 1;
                continue;
            }
            match self.request_publication(key) {
                Ok(true) => {}
                Ok(false) => frame.unpublished += 1,
                Err(failure) => {
                    frame.request_failures += 1;
                    frame.first_request_failure.get_or_insert((key, failure));
                }
            }
        }
        frame.sorted = candidates.to_vec();
        frame.sorted.sort_by(|&a, &b| {
            manhattan(section_center(a), center).wrapping_sub(manhattan(section_center(b), center)).cmp(&0)
        });
        if enabled && frame.sorted.len() <= MAX_RECORDED_CANDIDATES {
            self.record_visibility(frame.sorted.len() as i32, frame.unpublished, &frame.sorted);
        }
        if admit && enabled {
            for &key in &frame.sorted {
                if let Visible::Admit { generation, counts } = self.visible_column(key)? {
                    self.append_visible_column(key, generation, counts)?;
                    frame.counts.opaque += counts.opaque;
                    frame.counts.side += counts.side;
                    frame.counts.up += counts.up;
                    frame.counts.water += counts.water;
                }
            }
        }
        Ok(frame)
    }
}
