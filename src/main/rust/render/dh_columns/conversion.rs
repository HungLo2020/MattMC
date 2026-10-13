//! Ordered full-data to render-point policy. Contextual world/material queries
//! are synchronous callbacks; no Rust storage borrow survives a callback.
use std::panic::{catch_unwind, AssertUnwindSafe};
use crate::world::level::chunk::dh_columns::reader::Reader;
use crate::content::block::{StateFlags, StateId};

// These operations are mirrored by NativeDhRenderConversion. Values carry CPU
// facts only. A callback failure stops at the exact already-published prefix.
const SIZE: i32 = 0;
const POINT: i32 = 1;
const RESOLVE: i32 = 2;
const SOLID: i32 = 3;
const LIQUID: i32 = 4;
const OPACITY: i32 = 5;
const SNOW: i32 = 6;
const COLOR: i32 = 7;
const PREVIOUS_MATERIAL: i32 = 9;
const OUTPUT_GET: i32 = 10;
const OUTPUT_SET: i32 = 11;
const SEMANTIC_SET: i32 = 12;
const SEMANTIC_MERGE: i32 = 13;
const BLOCK_MATERIAL: i32 = 14;
const INVALID_POINT: i32 = 15;
const COLOR_SELECTED: i32 = 16;
const SEMANTIC_SET_UNAVAILABLE: i32 = 17;
const SEMANTIC_MERGE_UNAVAILABLE: i32 = 18;

struct Intrinsic {
    solid: bool,
    liquid: bool,
    opacity: u8,
    snow: bool,
    material: i32,
}
impl Intrinsic {
    fn resolve(word: u64) -> Option<Self> {
        let state = ((word >> 32) as u32).checked_sub(1)?;
        let geometry = crate::content::block::collision::installed()?.dh(state)?;
        let registry = crate::content::block::installed()?;
        if state as usize >= registry.state_count() { return None; }
        let flags = registry.flags(StateId(state as u16));
        Some(Self { solid: geometry.solid,
            liquid: !flags.contains(StateFlags::AIR) && flags.contains(StateFlags::HAS_FLUID),
            opacity: geometry.opacity, snow: word & 8 != 0,
            material: ((word >> 16) as u8 as i8) as i32 })
    }
}

fn with_min(point: u64, min: i32) -> u64 {
    (point & !(4095 << 8)) | (((min as u64) & 4095) << 8)
}

fn convert(mut call: impl FnMut(i32, i32, u64, u64) -> Result<u64, ()>, flags: i32, cave_y: i32) -> Result<(), ()> {
    let semantic = flags & 8 != 0;
    let mut color_next = -1i32;
    let (mut sky_next, mut block_next) = (-1i32, -1i32);
    let (mut last_color, mut last_bottom) = (0i32, -10_000i32);
    let (mut index, mut output) = (0i32, 0i32);
    while index < call(SIZE, 0, 0, 0)? as i32 {
        let point = call(POINT, index, 0, 0)?;
        let bottom = ((point >> 44) & 4095) as i32;
        let mut height = ((point >> 32) & 4095) as i32;
        let mut sky = ((point >> 56) & 15) as i32;
        let mut block = ((point >> 60) & 15) as i32;
        let facts = call(RESOLVE, (point & 0x7fff_ffff) as i32, bottom as u64, 0)?;
        if facts & 4 != 0 { index = index.wrapping_add(1); continue; }
        if facts & 2 != 0 && flags & 4 != 0 && sky == 0 && bottom + height < cave_y
            && output != 0 && index != 0 && index.wrapping_add(1) < call(SIZE, 0, 0, 0)? as i32 {
            let next = call(POINT, index.wrapping_add(1), 0, 0)?;
            if (next >> 56) & 15 == 0 && (last_color as u32 >> 24) == 255 {
                let previous = call(OUTPUT_GET, output - 1, 0, 0)?;
                call(OUTPUT_SET, output - 1, with_min(previous, bottom), 0)?;
            }
            index = index.wrapping_add(1); continue;
        }
        if facts & 1 != 0 { index = index.wrapping_add(1); continue; }
        let intrinsic = Intrinsic::resolve(facts);
        let mut avoided = if let Some(f) = &intrinsic {
            flags & 1 != 0 && !f.solid && !f.liquid && f.opacity != 16
        } else {
            flags & 1 != 0 && call(SOLID, 0, 0, 0)? == 0
                && call(LIQUID, 0, 0, 0)? == 0 && call(OPACITY, 0, 0, 0)? as i32 != 16
        };
        let snow = if let Some(f) = &intrinsic { f.snow } else { call(SNOW, 0, 0, 0)? != 0 };
        if snow {
            height -= 1;
            if height == 0 { avoided = true; }
        }
        if avoided {
            if flags & 2 != 0 {
                let color = call(COLOR, 0, 0, 0)? as i32;
                if (color as u32 >> 24) != 0 {
                    color_next = color | 0xff00_0000u32 as i32;
                    sky_next = sky; block_next = block;
                }
            }
            index = index.wrapping_add(1); continue;
        }
        let own_color = color_next == -1 || if let Some(f) = &intrinsic { f.liquid } else { call(LIQUID, 0, 0, 0)? != 0 };
        let (color, material) = if own_color {
            let resolved = call(COLOR, i32::from(semantic), 0, 0)?;
            (resolved as i32, (resolved >> 32) as i32)
        } else {
            sky = sky_next; block = block_next;
            (color_next, 0)
        };
        color_next = -1;
        if color == last_color && bottom + height == last_bottom && output > 0
            && (!semantic || call(PREVIOUS_MATERIAL, output - 1, 0, 0)? as i32 == material) {
            let previous = call(OUTPUT_GET, output - 1, 0, 0)?;
            call(OUTPUT_SET, output - 1, with_min(previous, bottom), 0)?;
            if semantic { call(SEMANTIC_MERGE, output - 1, 0, 0)?; }
        } else {
            let block_material = if let Some(f) = &intrinsic { f.material } else { call(BLOCK_MATERIAL, 0, 0, 0)? as i32 };
            let top = bottom + height;
            if flags & 16 != 0 && (top < 0 || top >= 6095 || bottom > top || !(0..256).contains(&block_material)) {
                call(INVALID_POINT, block_material, ((top as u32 as u64) << 32) | bottom as u32 as u64, 0)?;
            }
            let packed = (((block_material as u64) & 15) << 60)
                | (((color as u32 as u64) >> 28) << 56)
                | (((color as u32 as u64) & 0xff_ffff) << 32)
                | (((top as u64) & 4095) << 20) | (((bottom as u64) & 4095) << 8)
                | (((block as u64) & 15) << 4) | ((sky as u64) & 15);
            call(OUTPUT_SET, output, packed, 0)?;
            if semantic { call(SEMANTIC_SET, output, material as u64, 0)?; }
            output = output.wrapping_add(1);
        }
        last_bottom = bottom; last_color = color;
        index = index.wrapping_add(1);
    }
    if output == 0 { call(OUTPUT_SET, 0, 0, 0)?; }
    Ok(())
}

const COLUMN_BEGIN: i32 = 19;
const COLUMN_END: i32 = 20;

type Callback = unsafe extern "C" fn(i32, i32, u64, u64) -> u64;

/// One column through the conversion policy. Rust serves input, output and
/// cached material facts; everything else is a contextual Java query.
unsafe fn convert_column(flags: i32, cave_y: i32, java: &mut dyn FnMut(i32, i32, u64) -> Result<u64, ()>,
    reader: &Reader, output: *mut super::source::Source, window: u64,
    materials: *mut super::conversion_materials::Materials) -> Result<(), ()> {
    let (mut selected, mut needs_selection) = (0u64, false);
    convert(|mut op, index, mut value, _extra| {
        // Acquire and drop each borrow before a callback. The ABI scope pins
        // the reader, while its selected column may grow or change in Java.
        if op == SIZE { return Ok(reader.size() as u64); }
        if op == POINT {
            if let Some(point) = reader.point(index) { return Ok(point); }
            return Err(());
        }
        if op == OUTPUT_GET {
            return output.as_ref().and_then(|out| out.conversion_read(window, index)).ok_or(());
        } else if op == OUTPUT_SET {
            let out = output.as_mut().ok_or(())?;
            return if out.conversion_write(window, index, value) { Ok(0) } else { Err(()) };
        }
        if op == RESOLVE {
            selected = (value << 32) | index as u32 as u64;
            needs_selection = false;
            if let Some(word) = materials.as_ref().and_then(|m| m.get(index)) {
                needs_selection = true;
                return Ok(word);
            }
        } else if op == COLOR && flags & 8 == 0 {
            if let Some(color) = materials.as_mut().and_then(|m| m.color(selected as i32)) {
                return Ok(color as u64);
            }
        }
        if needs_selection {
            if op == COLOR {
                op = COLOR_SELECTED;
                value = selected;
                needs_selection = false;
            } else if op == SEMANTIC_SET {
                op = SEMANTIC_SET_UNAVAILABLE;
            } else if op == SEMANTIC_MERGE {
                op = SEMANTIC_MERGE_UNAVAILABLE;
            }
        }
        let color_generation = super::conversion_materials::color_generation();
        let result = java(op, index, value)?;
        if op == RESOLVE && Intrinsic::resolve(result).is_some() {
            if let Some(materials) = materials.as_mut() { materials.put(index, result); }
        }
        if matches!(op, COLOR | COLOR_SELECTED) && flags & 8 == 0 && result >> 63 != 0 {
            if let Some(materials) = materials.as_mut() {
                materials.put_color(selected as i32, result as u32, color_generation);
            }
            return Ok(result as u32 as u64);
        }
        Ok(result)
    }, flags, cave_y)
}

/// Converts every column of a full-data source into `output` (64 x 64 columns
/// of `height` points). Columns taller than `height` convert into `scratch`
/// and are reduced here; semantic builds instead hand that reduction and
/// their sidecars to Java at COLUMN_END. Java sees COLUMN_BEGIN before the
/// first contextual query of a column, and for every converted column in
/// semantic builds. Returns 1, 0 on a failed or invalid conversion, or -5 when
/// a tall column's points overlap (Java's IllegalArgumentException).
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_render_convert_source(flags: i32, cave_y: i32, callback: Option<Callback>,
    source: *const crate::world::level::chunk::dh_columns::source::Source,
    output: *mut super::source::Source, height: i32, scratch: *mut super::source::Source,
    materials: *mut super::conversion_materials::Materials) -> i32 {
    let (Some(callback), Some(source)) = (callback, source.as_ref()) else { return 0; };
    if output.is_null() || scratch.is_null() || height < 0
        || (height as usize).checked_mul(4096) != Some((*output).points().len()) {
        return 0;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let semantic = flags & 8 != 0;
        let height = height as usize;
        let mut reader = Reader::new();
        for column in 0..4096usize {
            match reader.select(source, column) {
                0 => continue,
                1 => {}
                _ => return 0,
            }
            let length = reader.size();
            if length == 0 { continue; }
            let expanded = length > 0 && length as usize > height;
            if expanded && length as usize > (*scratch).points().len() { return 0; }
            let mut java = Column { callback, column: column as i32, length: if expanded { length as u64 } else { 0 }, begun: false };
            if semantic && java.begin().is_err() { return 0; }
            let column_window = (height as u64) << 32 | (column * height) as u64;
            let (target, window) = if expanded {
                (*scratch).conversion_clear(length as usize);
                (scratch, (length as u32 as u64) << 32)
            } else {
                (output, column_window)
            };
            if convert_column(flags, cave_y, &mut |op, index, value| java.call(op, index, value),
                &reader, target, window, materials).is_err() { return 0; }
            if expanded && !semantic {
                let points = &(*scratch).points()[..length as usize];
                let written = super::reduction::with_reduced(points, None, height as i32, height, |result| {
                    for (i, &(point, _)) in result.iter().enumerate() {
                        (*output).conversion_write(column_window, i as i32, point);
                    }
                });
                if written.is_err() { return -5; }
            }
            if semantic && java.call(COLUMN_END, column as i32, if expanded { length as u64 } else { 0 }).is_err() { return 0; }
        }
        1
    })).unwrap_or(0)
}

/// Java's view of one column: announced once, before its first query.
struct Column { callback: Callback, column: i32, length: u64, begun: bool }
impl Column {
    unsafe fn raw(&self, op: i32, index: i32, value: u64) -> Result<u64, ()> {
        let mut failed = 0u8;
        let result = (self.callback)(op, index, value, (&mut failed as *mut u8) as usize as u64);
        if failed != 0 { Err(()) } else { Ok(result) }
    }
    unsafe fn begin(&mut self) -> Result<(), ()> {
        if !self.begun {
            self.begun = true;
            // The tall-column length, or 0 when the column converts in place.
            self.raw(COLUMN_BEGIN, self.column, self.length)?;
        }
        Ok(())
    }
    unsafe fn call(&mut self, op: i32, index: i32, value: u64) -> Result<u64, ()> {
        self.begin()?;
        self.raw(op, index, value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn full(id: u64, bottom: u64, height: u64, sky: u64, block: u64) -> u64 {
        id | (height << 32) | (bottom << 44) | (sky << 56) | (block << 60)
    }
    #[test]
    fn equal_color_merges_height_but_keeps_first_light_and_material() {
        let points = [full(0, 10, 2, 15, 1), full(1, 8, 2, 0, 7)];
        let mut output = [0u64; 2];
        let mut current = 0;
        let mut material_reads = 0;
        convert(|op, i, value, _| Ok(match op {
            SIZE => 2,
            POINT => points[i as usize],
            RESOLVE => { current = i; 0 }
            SNOW => 0,
            COLOR => 0xff223344,
            BLOCK_MATERIAL => { material_reads += 1; (2 + current) as u64 }
            OUTPUT_GET => output[i as usize],
            OUTPUT_SET => { output[i as usize] = value; 0 }
            _ => panic!("unexpected query {op}"),
        }), 0, 0).unwrap();
        assert_eq!(output, [0x2f22_3344_00c0_081f, 0]);
        assert_eq!(material_reads, 1);
    }
    #[test]
    fn cave_culling_extends_previous_opaque_surface_without_advancing_merge_state() {
        let points = [full(0, 20, 2, 0, 0), full(1, 10, 10, 0, 0), full(0, 0, 10, 0, 0)];
        let mut output = [0u64; 3];
        convert(|op, i, value, _| Ok(match op {
            SIZE => 3,
            POINT => points[i as usize],
            RESOLVE => if i == 1 { 3 } else { 0 },
            SNOW => 0,
            COLOR => 0xff223344,
            BLOCK_MATERIAL => 2,
            OUTPUT_GET => output[i as usize],
            OUTPUT_SET => { output[i as usize] = value; 0 }
            _ => panic!("unexpected query {op}"),
        }), 4, 128).unwrap();
        assert_eq!(output, [0x2f22_3344_0160_0a00, 0x2f22_3344_00a0_0000, 0]);
    }
    #[test]
    fn callback_failure_stops_before_later_publication() {
        let mut calls = Vec::new();
        let result = convert(|op, _, _, _| {
            calls.push(op);
            match op { SIZE => Ok(1), POINT => Ok(full(0, 0, 1, 0, 0)),
                RESOLVE | SNOW => Ok(0), COLOR => Err(()), _ => panic!("unexpected query {op}") }
        }, 0, 0);
        assert_eq!(result, Err(()));
        assert_eq!(calls, [SIZE, POINT, RESOLVE, SNOW, COLOR]);
    }
}
