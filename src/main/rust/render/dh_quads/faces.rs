//! DH vertical visibility and material intervals, consumed directly by the quad owner.
//! Neighbor words are borrowed for this call; scratch and emitted faces belong to Rust.
use super::{Builder, Policy, Quad};

pub(super) const LIMIT: usize = 4096;

// One aligned word per interval, preserving Frozen's signed short/light fields.
fn segment(start: i16, end: i16, sky: i8) -> u64 {
    u64::from(start as u16) | (u64::from(end as u16) << 16) | (u64::from(sky as u8) << 32)
}
fn start(word: u64) -> i16 {
    word as i16
}
fn end(word: u64) -> i16 {
    (word >> 16) as i16
}
fn light(word: u64) -> i8 {
    (word >> 32) as i8
}

#[derive(Default)]
pub(super) struct Scratch {
    segments: Vec<u64>,
    next: Vec<u64>,
}

fn low(p: u64) -> i16 {
    ((p >> 8) & 4095) as i16
}
fn high(p: u64) -> i16 {
    ((p >> 20) & 4095) as i16
}
fn zero_height(p: u64) -> bool {
    (p >> 8) & 0xffffff == 0
}
fn sky(p: u64) -> i8 {
    (p & 15) as i8
}
fn transparent(p: u64, enabled: bool) -> bool {
    p != 0 && enabled && ((p >> 56) & 15 != 15 || p >> 60 == 12)
}

impl Scratch {
    fn apply(&mut self, range_start: i16, range_end: i16, new_light: i8) {
        // Splitting retains the original outer bounds, even for reversed input
        // ranges. An entirely external range cannot change any interval.
        if let (Some(&first), Some(&last)) = (self.segments.first(), self.segments.last()) {
            if range_end <= start(first) || range_start >= end(last) {
                return;
            }
        }
        self.next.clear();
        for &s in &self.segments {
            let (lo, hi, sky) = (start(s), end(s), light(s));
            if hi <= range_start || lo >= range_end {
                self.next.push(s);
                continue;
            }
            if lo < range_start {
                self.next.push(segment(lo, range_start, sky));
            }
            self.next.push(segment(
                lo.max(range_start),
                hi.min(range_end),
                new_light.min(sky),
            ));
            if hi > range_end {
                self.next.push(segment(range_end, hi, sky));
            }
        }
        // Same sequence as clear/addAll; swapping avoids copying every segment.
        std::mem::swap(&mut self.segments, &mut self.next);
    }
}

fn append(owner: &mut Builder, q: Quad, policy: Policy) -> i32 {
    if q.width == 0 || q.height == 0 {
        return -2;
    }
    if q.width < 0 || q.height < 0 {
        return -3;
    }
    i32::from(owner.add(q, true, policy))
}

/// Span triples: signed i32 start/end, signed identity plus variant, i64 position.
/// Ordered overlaps and gaps intentionally follow the current semantic extension.
pub(super) fn vertical(owner: &mut Builder, q: Quad, spans: &[u64], policy: Policy) -> i32 {
    if spans.is_empty() {
        return if q.height <= 0 {
            1
        } else {
            append(owner, q, policy)
        };
    }
    let top = i32::from(q.y) + i32::from(q.height);
    let mut cursor = i32::from(q.y);
    for span in spans.chunks_exact(3) {
        let start = cursor.max(span[0] as i32);
        let end = top.min((span[0] >> 32) as i32);
        if end <= start {
            continue;
        }
        if cursor < start {
            let status = append(
                owner,
                Quad {
                    y: cursor as i16,
                    height: (start - cursor) as i16,
                    ..q
                },
                policy,
            );
            if status != 1 {
                return status;
            }
        }
        let status = append(
            owner,
            Quad {
                y: start as i16,
                height: (end - start) as i16,
                identity: span[1] as i32,
                variant: (span[1] >> 32) as u8,
                position: span[2] as i64,
                ..q
            },
            policy,
        );
        if status != 1 {
            return status;
        }
        cursor = end;
    }
    if cursor < top {
        return append(
            owner,
            Quad {
                y: cursor as i16,
                height: (top - cursor) as i16,
                ..q
            },
            policy,
        );
    }
    1
}

pub(super) fn adjacent(
    owner: &mut Builder,
    q: Quad,
    points: &[u64],
    same_detail: bool,
    cave_y: i32,
    transparency: bool,
    spans: &[u64],
    policy: Policy,
) -> i32 {
    if points.is_empty() || zero_height(points[0]) {
        return vertical(owner, Quad { sky: 15, ..q }, spans, policy);
    }
    let y_max = q.y.wrapping_add(q.height);
    let input_transparent = transparency && (q.color >> 24 < 255 || q.material == 12);
    let mut scratch = std::mem::take(&mut owner.face_scratch);
    scratch.segments.clear();
    scratch.segments.push(segment(q.y, y_max, 15));
    for (i, &p) in points.iter().enumerate() {
        if p == 0 || zero_height(p) || y_max <= low(p) {
            continue;
        }
        let above = if i == 0 { 0 } else { points[i - 1] };
        let below = points.get(i + 1).copied().unwrap_or(0);
        let light = if below == 0 || !transparent(p, transparency) {
            let boundary = (q.x == 0 && q.direction == 4)
                || (q.z == 0 && q.direction == 2)
                || (q.x == 256 && q.direction == 5)
                || (q.z == 256 && q.direction == 3);
            if !same_detail && i32::from(high(p)) >= cave_y && boundary {
                sky(p)
            } else {
                -1
            }
        } else {
            sky(below)
        };
        scratch.apply(low(p), high(p), light);
        if high(p) < low(above) {
            scratch.apply(high(p), low(above), sky(p));
        }
    }
    let mut status = 1;
    for &s in &scratch.segments {
        let height = end(s).wrapping_sub(start(s));
        let sky = light(s);
        if sky < 0 || input_transparent && sky != 15 || height <= 0 {
            continue;
        }
        status = vertical(
            owner,
            Quad {
                y: start(s),
                height,
                sky: sky as u8,
                ..q
            },
            spans,
            policy,
        );
        if status != 1 {
            break;
        }
    }
    scratch.segments.clear();
    scratch.next.clear();
    owner.face_scratch = scratch;
    status
}

#[cfg(test)]
mod tests {
    use super::*;
    fn quad() -> Quad {
        Quad {
            x: 1,
            y: 0,
            z: 1,
            width: 4,
            height: 16,
            direction: 2,
            material: 2,
            sky: 15,
            block: 0,
            error: 0,
            variant: 1,
            color: 0xff336699,
            identity: 7,
            position: 9,
        }
    }
    fn policy() -> Policy {
        Policy {
            exact: true,
            overlap_errors: false,
            curvature: false,
        }
    }
    fn point(low: u64, high: u64, light: u64) -> u64 {
        0x2f00000000000000 | high << 20 | low << 8 | light
    }
    #[test]
    fn neighbor_visibility_light_and_boundary_exemption() {
        let points = [point(4, 12, 6), point(0, 2, 3)];
        let mut b = Builder::new(false);
        assert_eq!(
            adjacent(
                &mut b,
                quad(),
                &points,
                true,
                i32::MIN,
                false,
                &[],
                policy()
            ),
            1
        );
        assert_eq!(
            b.lists[0][2]
                .iter()
                .map(|q| (q.y, q.height, q.sky))
                .collect::<Vec<_>>(),
            vec![(2, 2, 3), (12, 4, 15)]
        );
        let mut boundary = Builder::new(false);
        assert_eq!(
            adjacent(
                &mut boundary,
                Quad { z: 0, ..quad() },
                &points,
                false,
                10,
                false,
                &[],
                policy()
            ),
            1
        );
        assert!(boundary.lists[0][2]
            .iter()
            .any(|q| q.y == 4 && q.height == 8 && q.sky == 6));
    }
    #[test]
    fn semantic_prefix_and_signed_width_errors() {
        let spans = [
            3u64 << 32,
            11u64 | 1u64 << 32,
            101,
            3u64 | 5u64 << 32,
            12u64 | 1u64 << 32,
            202,
        ];
        let mut b = Builder::new(false);
        assert_eq!(
            vertical(
                &mut b,
                Quad {
                    y: 1,
                    height: 5,
                    ..quad()
                },
                &spans,
                policy()
            ),
            1
        );
        assert_eq!(
            b.lists[0][2]
                .iter()
                .map(|q| (q.y, q.height, q.identity, q.position))
                .collect::<Vec<_>>(),
            vec![(1, 2, 11, 101), (3, 2, 12, 202), (5, 1, 7, 9)]
        );
        assert_eq!(
            vertical(&mut b, Quad { width: 0, ..quad() }, &[], policy()),
            -2
        );
        assert_eq!(
            vertical(
                &mut b,
                Quad {
                    width: -1,
                    ..quad()
                },
                &[],
                policy()
            ),
            -3
        );
    }
    #[test]
    fn reused_scratch_is_bounded_and_empty_after_emission() {
        let points: Vec<_> = (0..LIMIT)
            .map(|i| point((i % 2000) as u64, (i % 2000 + 1) as u64, 15))
            .collect();
        let mut b = Builder::new(false);
        assert_eq!(
            adjacent(
                &mut b,
                Quad {
                    height: 2001,
                    ..quad()
                },
                &points,
                true,
                0,
                false,
                &[],
                policy()
            ),
            1
        );
        let capacity = (
            b.face_scratch.segments.capacity(),
            b.face_scratch.next.capacity(),
        );
        assert!(capacity.0 <= 4 * LIMIT && capacity.1 <= 4 * LIMIT);
        assert!(b.face_scratch.segments.is_empty() && b.face_scratch.next.is_empty());
        assert_eq!(
            adjacent(
                &mut b,
                Quad {
                    height: 2001,
                    ..quad()
                },
                &points,
                true,
                0,
                false,
                &[],
                policy()
            ),
            1
        );
        assert_eq!(
            capacity,
            (
                b.face_scratch.segments.capacity(),
                b.face_scratch.next.capacity()
            )
        );
    }
}
