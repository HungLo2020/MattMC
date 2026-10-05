//! `NoiseBasedAquifer.location`/`cellLocations` with native positional
//! randomness: the random aquifer centre of each grid cell, packed as Java's
//! `BlockPos.asLong`. Entries already present are reused, as the Java cache is.
use crate::world::level::levelgen::random::Positional;

/// `BlockPos.asLong`: 26-bit x at 38, 26-bit z at 12, 12-bit y at 0.
pub(crate) fn block_pos_long(x: i32, y: i32, z: i32) -> i64 {
    ((x as i64 & 0x3ff_ffff) << 38) | (y as i64 & 0xfff) | ((z as i64 & 0x3ff_ffff) << 12)
}

/// Fills every missing location `cellLocations(x, y, z, width, height)` visits.
/// `shape` is [minGridX, minGridY, minGridZ, sizeX, sizeZ]; returns false
/// (leaving earlier entries filled) when a visited cell lies outside the grid.
pub(crate) fn fill_cell_locations(
    grid: &mut [i64],
    shape: &[i32],
    random: Positional,
    x: i32,
    y: i32,
    z: i32,
    width: i32,
    height: i32,
) -> bool {
    let grid_x = |value: i32| value >> 4;
    let grid_y = |value: i32| value.div_euclid(12);
    for gx in grid_x(x.wrapping_sub(5))..=grid_x(x.wrapping_add(width).wrapping_sub(6)).wrapping_add(1) {
        for gy in grid_y(y.wrapping_add(1)).wrapping_sub(1)..=grid_y(y.wrapping_add(height)).wrapping_add(1) {
            for gz in grid_x(z.wrapping_sub(5))..=grid_x(z.wrapping_add(width).wrapping_sub(6)).wrapping_add(1) {
                let rx = gx as i64 - shape[0] as i64;
                let ry = gy as i64 - shape[1] as i64;
                let rz = gz as i64 - shape[2] as i64;
                let index = (ry * shape[4] as i64 + rz) * shape[3] as i64 + rx;
                if index < 0 || index >= grid.len() as i64 {
                    return false;
                }
                let slot = &mut grid[index as usize];
                if *slot == i64::MAX {
                    // location(): nextInt(10), nextInt(9), nextInt(10) in that order.
                    let mut draw = random.at(gx, gy, gz);
                    let cx = (gx << 4).wrapping_add(draw.next_int_bound(10));
                    let cy = gy.wrapping_mul(12).wrapping_add(draw.next_int_bound(9));
                    let cz = (gz << 4).wrapping_add(draw.next_int_bound(10));
                    *slot = block_pos_long(cx, cy, cz);
                }
            }
        }
    }
    true
}
