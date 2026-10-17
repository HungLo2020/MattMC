//! Ordered collision traversal over retained primary chunk sections.
//! Rust resolves static geometry; Java delivers contextual shape callbacks
//! and collision results, then the next call reads live inputs again.
use crate::content::block::{self, collision, StateId};
use super::chunk::inputs::{Chunk, Sections};
mod geometry;

#[derive(Clone, Copy)]
struct Cursor { origin: [i32; 3], size: [i32; 3], start: i32, end: i32 }
impl Cursor {
    fn new(values: &[i32]) -> Option<Self> {
        let origin = [values[0], values[1], values[2]];
        let size = [values[3], values[4], values[5]];
        if size.iter().any(|&v| v <= 0 || v > 128) { return None; }
        let end = size[0].checked_mul(size[1])?.checked_mul(size[2])?;
        if end > 32768 || !(0..end).contains(&values[6]) { return None; }
        for a in 0..3 { origin[a].checked_add(size[a])?; }
        Some(Self { origin, size, start: values[6], end })
    }
    fn point(self, index: i32) -> ([i32; 3], usize) {
        let local = [index % self.size[0], index / self.size[0] % self.size[1], index / self.size[0] / self.size[1]];
        let edge = (0..3).filter(|&a| local[a] == 0 || local[a] == self.size[a]-1).count();
        (std::array::from_fn(|a| self.origin[a] + local[a]), edge)
    }
}

fn scan(cursor: Cursor, mut read: impl FnMut([i32; 3], usize) -> Option<bool>) -> (i32, Option<[i32; 3]>) {
    let (mut point, _) = cursor.point(cursor.start);
    let chunk = [point[0] >> 4, point[2] >> 4];
    let end: [i32; 3] = std::array::from_fn(|a| cursor.origin[a] + cursor.size[a]);
    let mut next = cursor.start;
    let mut last = None;
    while next < cursor.end && next - cursor.start < 4096 {
        let edge = (0..3).filter(|&a| point[a] == cursor.origin[a] || point[a] == end[a]-1).count();
        if edge != 3 {
            // The Java chunk getter remains ordered, including null results.
            if [point[0] >> 4, point[2] >> 4] != chunk { break; }
            if read(point, edge) != Some(true) { break; }
            last = Some(point);
        }
        next += 1;
        point[0] += 1;
        if point[0] == end[0] {
            point[0] = cursor.origin[0]; point[1] += 1;
            if point[1] == end[1] { point[1] = cursor.origin[1]; point[2] += 1; }
        }
    }
    (next - cursor.start, last)
}

/// Return (excluded prefix << 32) | (candidate StateID + 1); low bit 16 marks
/// a resolved static hit. A zero candidate resumes traversal. MAX requests rebind.
/// Eleven i32s: origin XYZ, dimensions XYZ, current linear index, then three output
/// coordinates and a validity flag for the last visited non-corner. Six f64s are
/// the original query bounds. Spans/owner remain live; nothing is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_collision_scan(chunk: *const Chunk, cursor: *mut i32, bounds: *const f64) -> u64 {
    let Some(chunk) = chunk.as_ref() else { return 0; };
    if cursor.is_null() || bounds.is_null() || cursor as usize % 4 != 0 || bounds as usize % 8 != 0 { return 0; }
    let output = std::slice::from_raw_parts_mut(cursor, 11);
    let Some(cursor) = Cursor::new(output) else { return 0; };
    let bounds: [f64; 6] = std::slice::from_raw_parts(bounds, 6).try_into().unwrap();
    if bounds.iter().any(|v| !v.is_finite()) { return 0; }
    let (Some(registry), Some(catalog)) = (block::installed(), collision::installed()) else { return 0; };
    let Some(air) = registry.air().map(|id| registry.block(id).default_state()) else { return 0; };
    let sections = Sections::Retained(chunk);
    let mut reader: Option<(usize, super::chunk::live::WorldReader<'_>)> = None;
    let query = geometry::query_bounds(bounds);
    let mut candidate = None;
    let mut retired = false;
    let (skipped, last) = scan(cursor, |point, edge| {
        let row = (point[1] as i64 - chunk.min_y as i64) >> 4;
        if reader.as_ref().is_some_and(|(previous, _)| row < 0 || *previous != row as usize) { reader = None; }
        let state = if row < 0 || row as usize >= sections.len() || sections.counter(row as usize)?.packed() as u16 == 0 { air }
        else {
            if reader.is_none() {
                let Some(borrowed) = sections.block(row as usize).world_reader() else { retired = true; return None; };
                reader = Some((row as usize, borrowed));
            }
            let Some(id) = reader.as_ref().unwrap().1.get(((point[1]&15)*256 + (point[2]&15)*16 + (point[0]&15)) as usize) else {
                retired = true; return None;
            };
            if id as usize >= registry.state_count() { return None; }
            StateId(id as u16)
        };
        // These predicates precede context shape lookup in the original caller.
        // Even contextual providers need no callback when a border excludes them.
        if edge != 0 && catalog.excludes_world_border(state, edge) { return Some(true); }
        match catalog.world_geometry(state) {
            Some(shape) => {
                let hit = geometry::intersects(shape, point, edge, bounds, query.as_ref());
                candidate = hit.then_some((state, true));
                Some(!hit)
            }
            None => { candidate = Some((state, false)); None }
        }
    });
    if retired && skipped == 0 { return u64::MAX; }
    output[10] = i32::from(last.is_some());
    if let Some(last) = last { output[7..10].copy_from_slice(&last); }
    (skipped as u64) << 32 | candidate.map_or(0, |(state, hit)| (state.0 as u64 + 1) | if hit { 1 << 16 } else { 0 })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn traversal_stops_before_callbacks_and_chunk_changes() {
        let c = Cursor::new(&[14,0,0,4,3,3,1]).unwrap();
        let mut seen = Vec::new();
        assert_eq!(scan(c,|p,e|{seen.push((p,e));Some(true)}),(1,Some([15,0,0])));
        assert_eq!(seen, vec![([15,0,0],2)]);
        let c = Cursor::new(&[0,0,0,3,3,3,0]).unwrap();
        let mut calls=0;
        let skipped=scan(c,|_,_|{calls+=1;if calls==3 {None} else {Some(true)}});
        assert_eq!(skipped,(4,Some([0,1,0]))); // two corners and two callback-free cells
        assert_eq!(calls,3);
    }
    #[test]
    fn unsupported_and_wrapping_cursor_extents_decline() {
        assert!(Cursor::new(&[i32::MAX,0,0,2,2,2,0]).is_none());
        assert!(Cursor::new(&[0,0,0,0,2,2,0]).is_none());
        assert!(Cursor::new(&[0,0,0,128,128,128,0]).is_none());
    }
}
