//! Immutable collision grids retain empty coordinate intervals: epsilon merging
//! depends on them even when the occupied volume is identical to merged boxes.
use super::installed;
use std::sync::OnceLock;

#[derive(Debug, PartialEq)]
pub(crate) struct WorldGrid {
    pub(crate) axes: [Vec<f64>; 3],
    pub(crate) boxes: Vec<[u64; 3]>,
    pub(crate) bounds: [f64; 6],
}
impl WorldGrid {
    fn new(axes: [Vec<f64>; 3], boxes: &[[f64; 6]]) -> Option<Self> {
        if axes.iter().any(|a| a.is_empty() || a.len() > 65 || a.iter().any(|v| !v.is_finite())
            || a.windows(2).any(|w| w[0] >= w[1])) { return None; }
        let mut masks = Vec::with_capacity(boxes.len());
        let mut bounds = [f64::INFINITY, f64::INFINITY, f64::INFINITY,
            f64::NEG_INFINITY, f64::NEG_INFINITY, f64::NEG_INFINITY];
        for b in boxes {
            let mut mask = [0; 3];
            for a in 0..3 {
                let lo = axes[a].iter().position(|&v| v == b[a])?;
                let hi = axes[a].iter().position(|&v| v == b[a+3])?;
                if lo >= hi { return None; }
                mask[a] = (u64::MAX >> (64-(hi-lo))) << lo;
                bounds[a] = bounds[a].min(b[a]); bounds[a+3] = bounds[a+3].max(b[a+3]);
            }
            masks.push(mask);
        }
        Some(Self { axes, boxes: masks, bounds })
    }
}
#[derive(PartialEq)]
pub(super) struct WorldCatalog {
    pub(super) flags: Vec<u8>,
    pub(super) states: Vec<i32>,
    pub(super) grids: Vec<WorldGrid>,
}
pub(super) static WORLD: OnceLock<WorldCatalog> = OnceLock::new();

/// One immutable semantic installation. All spans must be live, aligned and
/// readable for their specified lengths; no input pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_collision_world_install(flags: *const u8, states: *const i32, count: i32,
    rows: *const i32, row_count: i32, coords: *const f64, coord_count: i32) -> i32 {
    let Some(catalog) = installed() else { return -1; };
    if flags.is_null() || states.is_null() || rows.is_null() || coords.is_null()
        || states as usize % 4 != 0 || rows as usize % 4 != 0 || coords as usize % 8 != 0
        || count < 0 || count as usize != catalog.states.len()
        || !(0..=7168).contains(&row_count) || row_count % 7 != 0
        || !(0..=199680).contains(&coord_count) { return -1; }
    let flags = std::slice::from_raw_parts(flags, count as usize).to_vec();
    let states = std::slice::from_raw_parts(states, count as usize).to_vec();
    let rows = std::slice::from_raw_parts(rows, row_count as usize);
    let coords = std::slice::from_raw_parts(coords, coord_count as usize);
    let mut grids = Vec::with_capacity(rows.len()/7);
    for row in rows.chunks_exact(7) {
        let Some(boxes) = catalog.shapes.get(row[0] as usize) else { return -1; };
        let mut axes = std::array::from_fn(|_| Vec::new());
        for a in 0..3 {
            let start = row[1+a*2]; let size = row[2+a*2];
            if start < 0 || !(1..=65).contains(&size) || start as usize + size as usize > coords.len() { return -1; }
            axes[a] = coords[start as usize..start as usize+size as usize].to_vec();
        }
        let Some(grid) = WorldGrid::new(axes, boxes) else { return -1; };
        grids.push(grid);
    }
    if flags.iter().zip(&states).enumerate().any(|(i, (&flag, &grid))| flag & !31 != 0
        || if flag & 1 == 0 { grid != -1 } else {
            grid < 0 || grid as usize >= grids.len() || rows[grid as usize*7] != catalog.states[i]
        }) { return -1; }
    match WORLD.set(WorldCatalog { flags, states, grids }) {
        Ok(()) => 1,
        Err(value) => if WORLD.get() == Some(&value) { 1 } else { -2 },
    }
}
