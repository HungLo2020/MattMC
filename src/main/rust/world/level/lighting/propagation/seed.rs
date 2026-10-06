//! One section of `SkyLightEngine.propagateLightSources`: full skylight from
//! the top of the section down to each column's lowest source, and the
//! increase entries the original enqueues, in its order.
use super::{block_pos, with_level, LAYER};

/// Per column (z * 16 + x): the lowest source Y, then the lowest source Y of
/// the north, south, west and east neighbours (from neighbouring chunks at the edges).
pub(crate) const COLUMNS: usize = 5 * 256;

/// `increaseSkySourceInDirections(down, north, south, west, east)`.
fn sky_source(down: bool, north: bool, south: bool, west: bool, east: bool) -> u64 {
    let mut entry = with_level(0, 15);
    for (on, direction) in [(down, 0), (north, 2), (south, 3), (west, 4), (east, 5)] {
        if on {
            entry |= 1 << (direction + 4);
        }
    }
    entry
}

/// Writes the section whose lowest block is `bottom` and pushes its entries.
/// Returns (any level written, a source lies below this section).
pub(crate) fn sky_section(layer: &mut [u8; LAYER], columns: &[i32], bottom: i32, min_x: i32, min_z: i32,
    entries: &mut impl FnMut(i64, u64)) -> (bool, bool) {
    let top = bottom + 15;
    let (mut wrote, mut below) = (false, false);
    for r in 0..16 {
        for s in 0..16 {
            let column = r * 16 + s;
            let lowest = columns[column];
            if lowest > top {
                continue;
            }
            let (north, south, west, east) = (columns[256 + column], columns[512 + column], columns[768 + column], columns[1024 + column]);
            let neighbours = north.max(south).max(west.max(east));
            let mut y = top;
            while y >= bottom.max(lowest) {
                let i = ((y & 15) << 8 | (r as i32) << 4 | s as i32) as usize;
                let shift = (i & 1) * 4;
                layer[i >> 1] = layer[i >> 1] & !(15 << shift) | 15 << shift;
                wrote = true;
                if y == lowest || y < neighbours {
                    entries(block_pos(min_x + s as i32, y, min_z + r as i32),
                        sky_source(y == lowest, y < north, y < south, y < west, y < east));
                }
                y -= 1;
            }
            if lowest < bottom {
                below = true;
            }
        }
    }
    (wrote, below)
}
