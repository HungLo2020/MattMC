//! Cached DH biome tint neighborhoods over existing CPU column views.
//! Resolver callbacks stay outside native locks; mutable UTF16 keys retain their
//! original Java hash and first-inserted identity, including serialization edits.
use super::id_map::Map;
use std::{
    collections::HashMap,
    hash::{BuildHasherDefault, Hasher},
    slice,
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc, Mutex, OnceLock,
    },
};
#[cfg(test)]
mod tests;
pub(super) struct Identity {
    hash: i32,
    units: Mutex<Box<[u16]>>,
}
pub struct Biome(pub(super) Arc<Identity>);
struct Color {
    identity: Arc<Identity>,
    value: Box<AtomicU64>,
}
// These keys are already Frozen's fixed wrapper hash, not arbitrary UTF16
// strings. Mix that integer once; equality/collision ordering stays in Colors.
#[derive(Default)]
struct TintHash(u64);
impl Hasher for TintHash {
    fn finish(&self) -> u64 {
        let mixed = self.0.wrapping_mul(0x9e3779b97f4a7c15);
        mixed ^ (mixed >> 32)
    }
    fn write(&mut self, _: &[u8]) {
        unreachable!("only fixed i32 biome hashes")
    }
    fn write_i32(&mut self, value: i32) {
        self.0 = value as u32 as u64;
    }
}
#[derive(Default)]
struct Colors(HashMap<i32, Vec<Color>, BuildHasherDefault<TintHash>>);
impl Colors {
    fn same(a: &Arc<Identity>, b: &Arc<Identity>) -> bool {
        if Arc::ptr_eq(a, b) {
            return true;
        }
        let (first, second) = if Arc::as_ptr(a) < Arc::as_ptr(b) {
            (a, b)
        } else {
            (b, a)
        };
        let first = first.units.lock().unwrap_or_else(|e| e.into_inner());
        let second = second.units.lock().unwrap_or_else(|e| e.into_inner());
        *first == *second
    }
    fn cell(&self, key: &Arc<Identity>) -> Option<&AtomicU64> {
        self.0
            .get(&key.hash)?
            .iter()
            .find(|entry| Self::same(&entry.identity, key))
            .map(|entry| entry.value.as_ref())
    }
    fn get(&self, key: &Arc<Identity>) -> Option<i32> {
        self.cell(key)
            .map(|value| value.load(Ordering::Acquire) as i32)
    }
    fn put(&mut self, key: &Arc<Identity>, color: i32) {
        let word = (1u64 << 32) | color as u32 as u64;
        let entries = self.0.entry(key.hash).or_default();
        if let Some(entry) = entries
            .iter_mut()
            .find(|entry| Self::same(&entry.identity, key))
        {
            entry.value.store(word, Ordering::Release);
        } else {
            entries.push(Color {
                identity: key.clone(),
                value: Box::new(AtomicU64::new(word)),
            });
            EPOCH.fetch_add(1, Ordering::Release);
        }
    }
}
type Registry = Mutex<HashMap<Box<[u16]>, Arc<Mutex<Colors>>>>;
static CACHES: OnceLock<Registry> = OnceLock::new();
static EPOCH: AtomicU64 = AtomicU64::new(1);
pub struct Cache(Arc<Mutex<Colors>>);
unsafe fn text<'a>(p: *const u16, n: i32) -> Option<&'a [u16]> {
    if !(0..=65536).contains(&n) || n != 0 && (p.is_null() || p as usize % 2 != 0) {
        return None;
    }
    Some(if n == 0 {
        &[]
    } else {
        slice::from_raw_parts(p, n as usize)
    })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_create(p: *const u16, n: i32) -> *mut Cache {
    let Some(key) = text(p, n) else {
        return std::ptr::null_mut();
    };
    let mut all = CACHES
        .get_or_init(Mutex::default)
        .lock()
        .unwrap_or_else(|e| e.into_inner());
    let colors = all
        .entry(key.into())
        .or_insert_with(|| Arc::new(Mutex::default()))
        .clone();
    Box::into_raw(Box::new(Cache(colors)))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_release(p: *mut Cache) {
    if !p.is_null() {
        drop(Box::from_raw(p));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_biome_v2(p: *const u16, n: i32, hash: i32) -> *mut Biome {
    text(p, n).map_or(std::ptr::null_mut(), |s| {
        Box::into_raw(Box::new(Biome(Arc::new(Identity {
            hash,
            units: Mutex::new(s.into()),
        }))))
    })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_biome_update(
    owner: *const Biome,
    p: *const u16,
    n: i32,
) -> i32 {
    let (Some(owner), Some(units)) = (owner.as_ref(), text(p, n)) else {
        return 0;
    };
    *owner.0.units.lock().unwrap_or_else(|e| e.into_inner()) = units.into();
    EPOCH.fetch_add(1, Ordering::Release);
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_biome_release(p: *mut Biome) {
    if !p.is_null() {
        drop(Box::from_raw(p));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_get(cache: *const Cache, biome: *const Biome) -> u64 {
    let (Some(cache), Some(biome)) = (cache.as_ref(), biome.as_ref()) else {
        return 0;
    };
    cache
        .0
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .get(&biome.0)
        .map_or(0, |v| (1u64 << 32) | v as u32 as u64)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_put(
    cache: *const Cache,
    biome: *const Biome,
    color: i32,
) -> i32 {
    let (Some(cache), Some(biome)) = (cache.as_ref(), biome.as_ref()) else {
        return 0;
    };
    cache
        .0
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .put(&biome.0, color);
    1
}
/// Frozen's relative-position formula deliberately differs from floor division
/// at negative positions. Keep its +max(1,2^detail-1), truncation and 63+remainder.
fn relative(value: i32, detail: u32) -> usize {
    let width = 1i32 << detail;
    let input = value.wrapping_add(if value < 0 { (width - 1).max(1) } else { 0 });
    let index = input / width % 64;
    (if value < 0 { 63 + index } else { index }) as usize
}
fn column(pos: u64, x: i32, z: i32) -> Option<usize> {
    let detail = (pos & 127) as u32;
    if !(6..=8).contains(&detail) {
        return None;
    }
    let sx = (((pos >> 8) as i32) << 4) >> 4;
    let sz = (((pos >> 36) as i32) << 4) >> 4;
    let min_x = sx.wrapping_mul(1 << detail);
    let min_z = sz.wrapping_mul(1 << detail);
    let max_x = min_x.wrapping_add((1 << detail) - 1);
    let max_z = min_z.wrapping_add((1 << detail) - 1);
    // contains() checks the minimum corner of the requested detail6 section.
    let requested_x = (x.div_euclid(64)).wrapping_mul(64);
    let requested_z = (z.div_euclid(64)).wrapping_mul(64);
    if requested_x < min_x || requested_x > max_x || requested_z < min_z || requested_z > max_z {
        return None;
    }
    Some(relative(x, detail - 6) * 64 + relative(z, detail - 6))
}
/// Outputs one column index per ordered sample; -1 means outside the source.
/// Return -1 declines, zero means Frozen's unsmoothed path, 1..49 is the plan.
pub(super) unsafe fn mattmc_dh_tint_plan(
    pos: u64,
    x: i32,
    z: i32,
    radius: i32,
    out: *mut i32,
) -> i32 {
    let detail = (pos & 127) as u32;
    if !(6..=8).contains(&detail)
        || !(0..=3).contains(&radius)
        || out.is_null()
        || out as usize % 4 != 0
    {
        return -1;
    }
    if radius == 0 || (1 << (detail - 6)) > radius {
        return 0;
    }
    let (Some(xmin), Some(xmax), Some(zmin), Some(zmax)) = (
        x.checked_sub(radius),
        x.checked_add(radius + 1),
        z.checked_sub(radius),
        z.checked_add(radius + 1),
    ) else {
        return -1;
    };
    let mut n = 0;
    for px in xmin..xmax {
        for pz in zmin..zmax {
            out.add(n)
                .write(column(pos, px, pz).map_or(-1, |i| i as i32));
            n += 1;
        }
    }
    n as i32
}
/// Input pointer/count per plan sample, borrowed through this call only.
/// Output color/x/z. Decline writes nothing. Cache miss returns INVALID_COLOR
/// at Frozen's exact mutation prefix; no resolver callback runs under the lock.
unsafe fn blend(
    cache: *const Cache,
    biome: *const Biome,
    map: *const Map,
    pos: u64,
    x: i32,
    y: i32,
    z: i32,
    min_y: i32,
    radius: i32,
    raw: *const usize,
    out: *mut i32,
) -> i32 {
    let (Some(cache), Some(center), Some(map)) = (cache.as_ref(), biome.as_ref(), map.as_ref())
    else {
        return 0;
    };
    if raw.is_null() || raw as usize % 8 != 0 || out.is_null() || out as usize % 4 != 0 {
        return 0;
    }
    let mut plan = [0; 49];
    let count = mattmc_dh_tint_plan(pos, x, z, radius, plan.as_mut_ptr());
    if count <= 0 {
        return 0;
    }
    let raw = slice::from_raw_parts(raw, count as usize * 2);
    let mut columns: [&[u64]; 49] = [&[]; 49];
    // Validate all spans before borrowing. Malformed material IDs decline only
    // if reached; an earlier miss must retain its original prefix.
    for (i, pair) in raw.chunks_exact(2).enumerate() {
        if pair[1] > 4096 || pair[1] != 0 && (pair[0] == 0 || pair[0] % 8 != 0) {
            return 0;
        }
        columns[i] = if pair[1] == 0 {
            &[]
        } else {
            slice::from_raw_parts(pair[0] as *const u64, pair[1])
        };
    }
    blend_columns(cache, center, map, x, y, z, min_y, radius,
        &plan[..count as usize], &columns[..count as usize], out)
}
// Both transport routes share the exact Frozen traversal and mutation prefix.
unsafe fn blend_columns(cache: &Cache, center: &Biome, map: &Map,
    x: i32, y: i32, z: i32, min_y: i32, radius: i32,
    plan: &[i32], columns: &[&[u64]], out: *mut i32) -> i32 {
    let colors = cache.0.lock().unwrap_or_else(|e| e.into_inner());
    let mut sum = [0i32; 3];
    let mut found = 0;
    let mut final_x = x;
    let mut final_z = z;
    let relative_y = y.wrapping_sub(min_y);
    let width = (radius * 2 + 1) as usize;
    for i in 0..plan.len() {
        final_x = x - radius + (i / width) as i32;
        final_z = z - radius + (i % width) as i32;
        if plan[i] < 0 {
            continue;
        }
        let Some(&point) = columns[i].iter().find(|&&p| {
            let bottom = ((p >> 44) & 4095) as i32;
            let height = ((p >> 32) & 4095) as i32;
            p != 0 && bottom <= relative_y && relative_y < bottom + height
        }) else {
            continue;
        };
        let Some(identity) = map.tint_biome((point & 0x7fff_ffff) as usize) else {
            return 0;
        };
        let color = colors.get(identity).unwrap_or(i32::MIN);
        if color == i32::MIN {
            out.write(color);
            out.add(1).write(final_x);
            out.add(2).write(final_z);
            return 1;
        }
        sum[0] += (color as u32 >> 16 & 255) as i32;
        sum[1] += (color as u32 >> 8 & 255) as i32;
        sum[2] += (color as u32 & 255) as i32;
        found += 1;
    }
    let color = if found == 0 {
        colors.get(&center.0).unwrap_or(i32::MIN)
    } else {
        (0xff00_0000u32
            | ((sum[0] / found) as u32) << 16
            | ((sum[1] / found) as u32) << 8
            | (sum[2] / found) as u32) as i32
    };
    out.write(color);
    out.add(1).write(final_x);
    out.add(2).write(final_z);
    1
}

/// A biome's published tint for this block, if any (the unsmoothed lookup).
pub(crate) fn cached(cache: &Cache, biome: &Biome) -> Option<i32> {
    cache.0.lock().unwrap_or_else(|e| e.into_inner()).get(&biome.0)
}

/// The smoothed tint at a point, blended over the full-data source's columns.
/// None when the inputs are outside the native route (the caller asks Java);
/// a missing neighbor color yields `i32::MIN`, Frozen's invalid sentinel.
pub(crate) unsafe fn source_blend(cache: &Cache, center: &Biome, map: &Map, source: &super::source::Source,
    pos: u64, x: i32, y: i32, z: i32, min_y: i32, radius: i32) -> Option<i32> {
    let mut plan = [0; 49];
    let count = mattmc_dh_tint_plan(pos, x, z, radius, plan.as_mut_ptr());
    if count <= 0 { return None; }
    let mut columns = [&[][..]; 49];
    for (i, &index) in plan[..count as usize].iter().enumerate() {
        if index >= 0 {
            let points = source.column(index as usize)?;
            if points.len() > 4096 { return None; }
            columns[i] = points;
        }
    }
    let mut out = [0i32; 3];
    (blend_columns(cache, center, map, x, y, z, min_y, radius,
        &plan[..count as usize], &columns[..count as usize], out.as_mut_ptr()) == 1).then_some(out[0])
}

/// Borrow a stable primary color cell, until owner destruction. Published cache
/// owners retain their process-lifetime registry lease. Mutation changes the
/// generation; numeric publication updates the cell without moving it.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_bind(
    cache: *const Cache,
    biome: *const Biome,
) -> *const AtomicU64 {
    let (Some(cache), Some(biome)) = (cache.as_ref(), biome.as_ref()) else {
        return std::ptr::null();
    };
    cache
        .0
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .cell(&biome.0)
        .map_or(std::ptr::null(), |value| value as *const _)
}
#[no_mangle]
pub extern "C" fn mattmc_dh_tint_epoch() -> *const AtomicU64 {
    &EPOCH
}

/// One point's tint blended over a full-data source's columns, for tests of
/// the conversion's native route. Output color/x/z; 0 declines.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_tint_source(
    cache: *const Cache, center: *const Biome, map: *const Map,
    source: *const super::source::Source, pos: u64,
    x: i32, y: i32, z: i32, min_y: i32, radius: i32, out: *mut i32,
) -> i32 {
    let (Some(cache), Some(center), Some(map), Some(source)) =
        (cache.as_ref(), center.as_ref(), map.as_ref(), source.as_ref()) else { return 0; };
    if out.is_null() || out as usize % 4 != 0 { return 0; }
    let mut plan = [0; 49];
    let count = mattmc_dh_tint_plan(pos, x, z, radius, plan.as_mut_ptr());
    if count <= 0 { return 0; }
    let mut columns = [&[][..]; 49];
    for (i, &index) in plan[..count as usize].iter().enumerate() {
        if index >= 0 {
            let Some(points) = source.column(index as usize) else { return 0; };
            if points.len() > 4096 { return 0; }
            columns[i] = points;
        }
    }
    blend_columns(cache, center, map, x, y, z, min_y, radius,
        &plan[..count as usize], &columns[..count as usize], out)
}
