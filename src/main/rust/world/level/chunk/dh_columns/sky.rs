//! Data-source sky light (`DhLightingEngine.bakeDataSourceSkyLight`) over the
//! authoritative primary table, in place and in Frozen's exact visit order.
//!
//! Java wrote every lit point through a list `set` (a downcall each) and built
//! a Java reader for every column it touched. Here the whole bake is one call;
//! Java only refreshes the views of columns that already have escaped readers.
use super::materials::Materials;
use super::mutable::Column;
use super::source::Source;
use std::slice;

const WIDTH: usize = 64;
/// `FullDataPointUtil.ID_MASK` is `Integer.MAX_VALUE`, not the low 32 bits.
#[inline] fn id(point: u64) -> usize { (point & 0x7fff_ffff) as usize }
#[inline] fn height(point: u64) -> i32 { ((point >> 32) & 4095) as i32 }
#[inline] fn bottom(point: u64) -> i32 { ((point >> 44) & 4095) as i32 }
#[inline] fn sky(point: u64) -> i32 { ((point >> 56) & 15) as i32 }
/// `FullDataPointUtil.setSkyLight`: clears the lane, then ORs the unmasked,
/// sign-extended value, exactly as Java's `(long) skyLight << 56`.
#[inline] fn with_sky(point: u64, light: i32) -> u64 { (point & !(15u64 << 56)) | ((light as i64) << 56) as u64 }

/// One column's mutable points. Lengths never change during the bake.
#[derive(Clone, Copy)]
struct Span { points: *mut u64, len: usize }
struct Bake<'a> { columns: &'a [Span; WIDTH * WIDTH], air: &'a [bool] }

impl Bake<'_> {
    #[inline] unsafe fn get(&self, column: usize, index: usize) -> u64 { *self.columns[column].points.add(index) }
    #[inline] unsafe fn set(&self, column: usize, index: usize, point: u64) { *self.columns[column].points.add(index) = point; }

    /// `recursivelyLightAdjacentDataPoints`: depth is bounded by the light level (at most 15).
    unsafe fn propagate(&self, x: usize, z: usize, point: u64) {
        let light = sky(point);
        if light <= 1 { return; }
        let min = bottom(point);
        let max = height(point) + min;
        for (dx, dz) in [(-1i32, 0i32), (1, 0), (0, -1), (0, 1)] {
            let (ax, az) = (x as i32 + dx, z as i32 + dz);
            if ax < 0 || ax >= WIDTH as i32 || az < 0 || az >= WIDTH as i32 { continue; }
            let (ax, az) = (ax as usize, az as usize);
            let column = ax * WIDTH + az;
            // Missing and present-empty columns both offer nothing to light.
            let size = self.columns[column].len;
            for index in 0..size {
                let adjacent = self.get(column, index);
                let adjacent_min = bottom(adjacent);
                let adjacent_max = height(adjacent) + adjacent_min;
                if adjacent_min >= max { continue; }
                if adjacent_max <= min { break; }
                if !self.air[id(adjacent)] { continue; }
                if light - 1 > sky(adjacent) {
                    let lit = with_sky(adjacent, light - 1);
                    self.set(column, index, lit);
                    self.propagate(ax, az, lit);
                }
            }
        }
    }

    unsafe fn run(&self, opacity: &[u8], max_sky: i32) {
        // Pass 1: z outer, x inner, top-down. Values are re-read after every
        // propagation, which may already have raised later points.
        for z in 0..WIDTH { for x in 0..WIDTH {
            let column = x * WIDTH + z;
            let size = self.columns[column].len;
            for index in 0..size {
                let point = self.get(column, index);
                if !self.air[id(point)] { continue; }
                let light = if index == 0 { max_sky } else {
                    let above = self.get(column, index - 1);
                    if !self.air[id(above)] { continue; }
                    sky(above)
                };
                let point = with_sky(point, light);
                self.set(column, index, point);
                self.propagate(x, z, point);
            }
        }}
        // Pass 2: column index order. Opaque points copy the light above them,
        // less the absorption of a partially translucent point above.
        for column in 0..WIDTH * WIDTH {
            let size = self.columns[column].len;
            for index in 0..size {
                let point = self.get(column, index);
                if index == 0 { self.set(column, index, with_sky(point, max_sky)); continue; }
                if self.air[id(point)] { continue; }
                let above = self.get(column, index - 1);
                let above_light = sky(above);
                if self.air[id(above)] {
                    self.set(column, index, with_sky(point, above_light));
                } else {
                    let absorption = opacity[id(above)] as i32 * height(above);
                    if absorption < above_light { self.set(column, index, with_sky(point, above_light - absorption)); }
                }
            }
        }
    }
}

/// Bake data-source sky light into `source` with `policy`'s opacity facts
/// for exactly `materials` mapping entries.
/// `output` receives `[count, (index, points, len) * count]` for columns
/// with escaped Java readers, as `mattmc_dh_source_cull_readers` does.
/// Returns 1 when baked, 0 when declined before any change (aliased or
/// unsupported columns, or a material outside the policy, which Java's
/// wrapper lookup would reject), or -1 when detaching a column failed to
/// allocate (no points were changed; detached readers are still reported).
///
/// # Safety
/// `source` and `policy` are live and exclusive for this call; `output` holds
/// `1 + 4096 * 3` aligned words.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_bake_sky(source: *mut Source, policy: *const Materials, materials: i32, max_sky: i32, output: *mut usize) -> i32 {
    let (Some(source), Some(policy)) = (source.as_mut(), policy.as_ref()) else { return 0; };
    // Java's mapping must hold exactly the policy's materials.
    if materials < 0 || policy.0.len() != materials as usize { return 0; }
    if output.is_null() || output as usize % align_of::<usize>() != 0 { return 0; }
    if source.identities().windows(2).any(|p| p[0] == p[1]) { return 0; }
    for i in 0..WIDTH * WIDTH {
        let Some(points) = source.column(i) else { return 0; };
        if points.iter().any(|&p| id(p) >= policy.0.len()) { return 0; }
    }
    let output = slice::from_raw_parts_mut(output, 1 + WIDTH * WIDTH * 3);
    output[0] = 0;
    // Java's second pass writes index 0 of every non-empty column, so every
    // non-empty column detaches. Detach them all before the first write.
    let mut columns = [Span { points: std::ptr::null_mut(), len: 0 }; WIDTH * WIDTH];
    let mut failed = false;
    for i in 0..WIDTH * WIDTH {
        if source.column(i).unwrap().is_empty() { continue; }
        let owner: *mut Column = source.owner(i);
        if !Column::make_owned(owner) { failed = true; break; }
        let points = Column::points_mut(owner);
        columns[i] = Span { points: points.as_mut_ptr(), len: points.len() };
        if source.has_reader(i) {
            let row = 1 + output[0] * 3;
            output[row..row + 3].copy_from_slice(&[i, points.as_ptr() as usize, points.len()]);
            output[0] += 1;
        }
    }
    if failed { return -1; }
    let air: Vec<bool> = policy.0.iter().map(|fact| fact.opacity == 0).collect();
    let opacity: Vec<u8> = policy.0.iter().map(|fact| fact.opacity).collect();
    Bake { columns: &columns, air: &air }.run(&opacity, max_sky);
    1
}

#[cfg(test)]
mod tests {
    use super::*;

    fn point(id: u32, height: u64, bottom: u64, sky: u64) -> u64 { id as u64 | height << 32 | bottom << 44 | sky << 56 }

    #[test]
    fn sky_setter_matches_java_sign_extension_and_masking() {
        let p = point(5, 3, 7, 9) | 0xf << 60;
        assert_eq!(sky(with_sky(p, 15)), 15);
        assert_eq!(with_sky(p, 2) >> 60, 0xf, "block light is untouched");
        assert_eq!(with_sky(p, -1), p | !0u64 << 56, "negative light sign-extends like Java");
        assert_eq!(id(0xffff_ffff), 0x7fff_ffff);
    }

    #[test]
    fn bake_lights_air_propagates_sideways_and_absorbs_through_translucency() {
        // ids: 0 air (opacity 0), 1 stone (16), 2 water (1).
        let air = [true, false, false];
        let opacity = [0u8, 16, 1];
        let mut columns_data: Vec<Vec<u64>> = vec![Vec::new(); WIDTH * WIDTH];
        // Column (0,0): air over water (height 3) over stone.
        columns_data[0] = vec![point(0, 10, 20, 0), point(2, 3, 17, 0), point(1, 17, 0, 0)];
        // Column (1,0): a roofed cave: stone roof, air below it overlapping (0,0)'s air.
        columns_data[WIDTH] = vec![point(1, 5, 25, 0), point(0, 5, 20, 0), point(1, 20, 0, 0)];
        let mut spans = [Span { points: std::ptr::null_mut(), len: 0 }; WIDTH * WIDTH];
        for (i, c) in columns_data.iter_mut().enumerate() { spans[i] = Span { points: c.as_mut_ptr(), len: c.len() }; }
        unsafe { Bake { columns: &spans, air: &air }.run(&opacity, 15); }
        let a = &columns_data[0];
        assert_eq!(sky(a[0]), 15, "top air is fully lit");
        assert_eq!(sky(a[1]), 15, "opaque-to-sky water copies the transparent air above it");
        assert_eq!(sky(a[2]), 12, "stone under water: 15 - opacity(1) * height(3)");
        let b = &columns_data[WIDTH];
        assert_eq!(sky(b[0]), 15, "pass 2 lights the top point");
        assert_eq!(sky(b[1]), 14, "cave air lit sideways from the open column");
        assert_eq!(sky(b[2]), 14, "stone floor copies the transparent air above it");
    }
}
