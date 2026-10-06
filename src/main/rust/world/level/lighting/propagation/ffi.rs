//! Java bridge: immutable block-state tables, one engine per Java light
//! engine, and a pass with a section callback. See `NativeLightPropagation`.
use super::{Blocks, Engine, Error, Source, Tables, Type, LAYER};

/// Java's section callback: (mode, section, buffer). Mode 0 reports the
/// stored layer (0 absent, 1 stored); mode 1 the block states (0). Negative
/// values report failures.
pub(crate) type SectionCallback = unsafe extern "C" fn(i32, i64, *mut u8) -> i32;

// Callback buffer: header ints, layer bytes, palette types, packed words, full types.
const HEADER: usize = 0;
const LAYER_AT: usize = 64;
const PALETTE_AT: usize = LAYER_AT + LAYER;
const PALETTE_MAX: usize = 256;
const WORDS_AT: usize = PALETTE_AT + PALETTE_MAX * 2;
const WORDS_MAX: usize = 1024;
const FULL_AT: usize = WORDS_AT + WORDS_MAX * 8;
pub(crate) const BUFFER: usize = FULL_AT + 4096 * 2;

/// An engine and the buffer its callback fills.
pub(crate) struct Handle {
    engine: Engine,
    buffer: Vec<u64>,
}

struct Callback<'a> {
    call: SectionCallback,
    buffer: &'a mut [u64],
}

impl Callback<'_> {
    fn bytes(&mut self) -> *mut u8 {
        self.buffer.as_mut_ptr() as *mut u8
    }

    fn header(&self, i: usize) -> i32 {
        unsafe { *(self.buffer.as_ptr() as *const i32).add(HEADER / 4 + i) }
    }
}

impl Source for Callback<'_> {
    fn layer(&mut self, section: i64, layer: &mut [u8; LAYER]) -> Result<Option<bool>, Error> {
        let status = unsafe { (self.call)(0, section, self.bytes()) };
        match status {
            0 => Ok(None),
            1 => {
                let default = self.header(1);
                if default == -1 {
                    let bytes = unsafe { std::slice::from_raw_parts((self.buffer.as_ptr() as *const u8).add(LAYER_AT), LAYER) };
                    layer.copy_from_slice(bytes);
                } else if (0..16).contains(&default) {
                    layer.fill((default * 17) as u8);
                } else {
                    return Err(Error::Unsupported);
                }
                Ok(Some(self.header(0) != 0))
            }
            -1 => Err(Error::Unsupported),
            _ => Err(Error::Callback),
        }
    }

    fn blocks(&mut self, section: i64) -> Result<Blocks<'_>, Error> {
        let status = unsafe { (self.call)(1, section, self.bytes()) };
        match status {
            0 => {}
            -1 => return Err(Error::Unsupported),
            _ => return Err(Error::Callback),
        }
        let (kind, a, b) = (self.header(0), self.header(1), self.header(2));
        let base = self.buffer.as_ptr() as *const u8;
        match kind {
            0 if (0..=u16::MAX as i32).contains(&a) => Ok(Blocks::Uniform(a as u16)),
            1 | 2 if (1..=32).contains(&a) => {
                let words = 4096usize.div_ceil(64 / a as usize);
                if words > WORDS_MAX || (kind == 1 && !(1..=PALETTE_MAX as i32).contains(&b)) {
                    return Err(Error::Unsupported);
                }
                unsafe {
                    let palette = (kind == 1).then(|| std::slice::from_raw_parts(base.add(PALETTE_AT) as *const u16, b as usize));
                    Ok(Blocks::Packed { bits: a as u32, palette, words: std::slice::from_raw_parts(base.add(WORDS_AT) as *const u64, words) })
                }
            }
            3 => Ok(Blocks::Full(unsafe { std::slice::from_raw_parts(base.add(FULL_AT) as *const u16, 4096) })),
            _ => Err(Error::Unsupported),
        }
    }
}

/// Builds the immutable tables: `state_types` per state id (u16::MAX for
/// states Rust must not see), 9 values per type (opacity, emission, empty,
/// six face IDs), the `faces`² occlusion matrix and AIR's type. Returns 0
/// on invalid input. The tables are never freed.
/// # Safety
/// Each pointer addresses its stated count of values for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tables_create(state_types: *const u16, state_count: i32, types: *const u16, type_count: i32,
    occludes: *const u8, faces: i32, air: i32) -> u64 {
    if state_types.is_null() || types.is_null() || occludes.is_null() || state_count <= 0 || type_count <= 0
        || !(1..=4096).contains(&faces) || !(0..type_count).contains(&air) || type_count >= u16::MAX as i32
    {
        return 0;
    }
    let raw = std::slice::from_raw_parts(types, type_count as usize * 9);
    let mut parsed = Vec::with_capacity(type_count as usize);
    for t in raw.chunks_exact(9) {
        if !(1..=15).contains(&t[0]) || t[1] > 15 || t[2] > 1 || t[3..].iter().any(|&f| f as i32 >= faces) {
            return 0;
        }
        parsed.push(Type { opacity: t[0] as u8, emission: t[1] as u8, empty: t[2] == 1, faces: [t[3], t[4], t[5], t[6], t[7], t[8]] });
    }
    let state_types = std::slice::from_raw_parts(state_types, state_count as usize).to_vec();
    if state_types.iter().any(|&t| t != u16::MAX && t as i32 >= type_count) {
        return 0;
    }
    let faces = faces as usize;
    let tables = Tables {
        state_types,
        types: parsed,
        faces,
        occludes: std::slice::from_raw_parts(occludes, faces * faces).to_vec(),
        air: air as u16,
    };
    Box::into_raw(Box::new(tables)) as u64
}

#[no_mangle]
pub extern "C" fn mattmc_light_engine_create(sky: i32) -> u64 {
    Box::into_raw(Box::new(Handle { engine: Engine::new(sky != 0), buffer: vec![0; BUFFER / 8] })) as u64
}

/// # Safety
/// `handle` came from `mattmc_light_engine_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_engine_release(handle: u64) {
    if handle != 0 {
        drop(Box::from_raw(handle as *mut Handle));
    }
}

/// One propagation pass over the queues (`decrease_len`/`increase_len`
/// longs of position, entry pairs). Writes [processed, written sections,
/// affected sections] to `out`. Returns 0, -1 for invalid input, -2 for
/// input Java must run itself, -3 when the callback failed.
/// # Safety
/// `handle`/`tables` are live and used by no other call; the queue pointers
/// address their lengths; `out` addresses 3 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_run(handle: u64, tables: u64, decreases: *const i64, decrease_len: i32, increases: *const i64,
    increase_len: i32, lowest: i32, callback: Option<SectionCallback>, out: *mut i64) -> i32 {
    let Some(call) = callback else { return -1 };
    if handle == 0 || tables == 0 || out.is_null() || decrease_len < 0 || increase_len < 0 || decrease_len % 2 != 0 || increase_len % 2 != 0
        || (decrease_len > 0 && decreases.is_null()) || (increase_len > 0 && increases.is_null())
    {
        return -1;
    }
    let handle = &mut *(handle as *mut Handle);
    let tables = &*(tables as *const Tables);
    let slice = |p: *const i64, n: i32| if n == 0 { &[][..] } else { std::slice::from_raw_parts(p, n as usize) };
    let mut source = Callback { call, buffer: &mut handle.buffer };
    match handle.engine.run(tables, &mut source, slice(decreases, decrease_len), slice(increases, increase_len), lowest) {
        Ok(outcome) => {
            *out = outcome.processed;
            *out.add(1) = handle.engine.written_count() as i64;
            *out.add(2) = handle.engine.affected().len() as i64;
            0
        }
        Err(Error::Unsupported) => -2,
        Err(Error::Callback) => -3,
    }
}

/// Copies the last pass's written sections (keys and 2048-byte layers) and
/// affected sections. Returns 0, or -1 when the counts differ.
/// # Safety
/// `handle` is live; the pointers address `written` longs, `written` × 2048
/// bytes and `affected` longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_results(handle: u64, keys: *mut i64, layers: *mut u8, written: i32, affected: *mut i64, affected_len: i32) -> i32 {
    if handle == 0 {
        return -1;
    }
    let engine = &(*(handle as *const Handle)).engine;
    if engine.written_count() != written as usize || engine.affected().len() != affected_len as usize {
        return -1;
    }
    for (i, (key, layer)) in engine.written().enumerate() {
        *keys.add(i) = key;
        std::ptr::copy_nonoverlapping(layer.as_ptr(), layers.add(i * LAYER), LAYER);
    }
    if affected_len > 0 {
        std::ptr::copy_nonoverlapping(engine.affected().as_ptr(), affected, affected_len as usize);
    }
    0
}

/// One section of `SkyLightEngine.propagateLightSources` (see `seed.rs`):
/// `layer` is the section's 2048 bytes, filled with `default` first when it
/// is 0..15 (a lazy layer). Writes entry pairs to `entries` (room for 8192
/// longs) and [pairs, wrote, source below] to `out`. Bounded work with no
/// allocations or callbacks, for critical heap access. Returns 0 or -1.
/// # Safety
/// The pointers address 2048 bytes, 1280 ints, 8192 longs and 3 ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_sky_section(layer: *mut u8, default: i32, columns: *const i32, bottom: i32, min_x: i32, min_z: i32,
    entries: *mut i64, out: *mut i32) -> i32 {
    if layer.is_null() || columns.is_null() || entries.is_null() || out.is_null() || default > 15 || bottom % 16 != 0 {
        return -1;
    }
    let layer = &mut *(layer as *mut [u8; LAYER]);
    if default >= 0 {
        layer.fill((default * 17) as u8);
    }
    let columns = std::slice::from_raw_parts(columns, super::seed::COLUMNS);
    let entries = std::slice::from_raw_parts_mut(entries, 8192);
    let mut pairs = 0;
    let (wrote, below) = super::seed::sky_section(layer, columns, bottom, min_x, min_z, &mut |pos, entry| {
        entries[pairs * 2] = pos;
        entries[pairs * 2 + 1] = entry as i64;
        pairs += 1;
    });
    *out = pairs as i32;
    *out.add(1) = wrote as i32;
    *out.add(2) = below as i32;
    0
}
