//! Java bridge: the light tables derived from the block registry, one engine
//! per Java light engine, and a pass with a section callback. See
//! `NativeLightPropagation`.
use super::{Blocks, Engine, Error, Source, Tables, LAYER};
use crate::world::level::lighting::layers::{Layer, ffi::{Projection, projection}};

/// Java's section callback: (mode, section, buffer). Mode 0 reports the
/// stored layer (0 absent, 1 stored); mode 1 the block states (0). Negative
/// values report failures.
pub(crate) type SectionCallback = unsafe extern "C" fn(i32, i64, *mut u8) -> i32;

// Callback buffer: header ints, layer bytes, palette state ids, packed words,
// full state ids. Rust maps the state ids to light types in place.
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
    written_indices: Vec<usize>,
}

struct Callback<'a> {
    call: SectionCallback,
    buffer: &'a mut [u64],
    tables: &'a Tables,
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
            2 => {
                // The callback pins the live CPU owner until this native read
                // finishes; the pass then owns its independent byte snapshot.
                let pointer = self.buffer[1] as *const Layer;
                if pointer.is_null() || pointer as usize % std::mem::align_of::<Layer>() != 0 {
                    return Err(Error::Callback);
                }
                let view = unsafe { &*pointer }.view();
                if !view.copy_bytes_into(layer) {
                    let default = view.default_value();
                    if !(0..16).contains(&default) { return Err(Error::Unsupported); }
                    layer.fill((default * 17) as u8);
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
        let tables = self.tables;
        let base = self.buffer.as_mut_ptr() as *mut u8;
        let to_types = |at: usize, n: usize| unsafe {
            for id in std::slice::from_raw_parts_mut(base.add(at) as *mut u16, n) {
                *id = tables.state_type(*id);
            }
        };
        match kind {
            0 if (0..=u16::MAX as i32).contains(&a) => Ok(Blocks::Uniform(tables.state_type(a as u16))),
            1 | 2 if (1..=32).contains(&a) => {
                let words = 4096usize.div_ceil(64 / a as usize);
                if words > WORDS_MAX || (kind == 1 && !(1..=PALETTE_MAX as i32).contains(&b)) {
                    return Err(Error::Unsupported);
                }
                if kind == 1 {
                    to_types(PALETTE_AT, b as usize);
                }
                unsafe {
                    let palette = (kind == 1).then(|| std::slice::from_raw_parts(base.add(PALETTE_AT) as *const u16, b as usize));
                    Ok(Blocks::Packed { bits: a as u32, palette, words: std::slice::from_raw_parts(base.add(WORDS_AT) as *const u64, words) })
                }
            }
            3 => {
                to_types(FULL_AT, 4096);
                Ok(Blocks::Full(unsafe { std::slice::from_raw_parts(base.add(FULL_AT) as *const u16, 4096) }))
            }
            _ => Err(Error::Unsupported),
        }
    }
}

/// The light tables of the installed block registry: a state's type is
/// (`max(1, getLightBlock())`, emission, `isEmptyShape`, six light occlusion
/// faces), numbered in state order. Returns 0 when the registry is not
/// installed or its types do not fit. The tables are never freed.
#[no_mangle]
pub extern "C" fn mattmc_light_tables_create() -> u64 {
    super::installed_tables().map_or(0, |tables| tables as *const Tables as u64)
}

#[no_mangle]
pub extern "C" fn mattmc_light_engine_create(sky: i32) -> u64 {
    Box::into_raw(Box::new(Handle { engine: Engine::new(sky != 0), buffer: vec![0; BUFFER / 8], written_indices: Vec::new() })) as u64
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
    let mut source = Callback { call, buffer: &mut handle.buffer, tables };
    handle.written_indices.clear();
    match handle.engine.run(tables, &mut source, slice(decreases, decrease_len), slice(increases, increase_len), lowest) {
        Ok(outcome) => {
            handle.written_indices.extend(handle.engine.sections.iter().enumerate()
                .filter_map(|(index, section)| section.written.then_some(index)));
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

/// Export only ordered section identities; owned results remain in Rust.
/// # Safety
/// The live, excluded handle owns the last successful pass. Output spans are
/// writable for their lengths and disjoint from the engine and one another.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_result_keys(handle: u64, keys: *mut i64,
    written: i32, affected: *mut i64, affected_len: i32) -> i32 {
    if handle == 0 || written < 0 || affected_len < 0
        || (written > 0 && keys.is_null()) || (affected_len > 0 && affected.is_null()) { return -1; }
    let handle = &*(handle as *const Handle);
    if handle.written_indices.len() != written as usize || handle.engine.affected().len() != affected_len as usize { return -1; }
    for (i, index) in handle.written_indices.iter().enumerate() {
        *keys.add(i) = handle.engine.sections[*index].key;
    }
    if affected_len > 0 {
        std::ptr::copy_nonoverlapping(handle.engine.affected().as_ptr(), affected, affected_len as usize);
    }
    0
}

fn written_layer(handle: &Handle, index: i32) -> Option<&[u8; LAYER]> {
    if index < 0 { return None; }
    let section = handle.engine.sections.get(*handle.written_indices.get(index as usize)?)?;
    handle.engine.layers.get(section.layer)
}

/// Preserve the target layer's materialization/default and map COW semantics.
/// # Safety
/// Handle and target are live and caller-excluded; view addresses one writable
/// pointer. The caller retains the returned CPU lease until releasing it once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_result_install(handle: u64, index: i32,
    target: *const Layer, view: *mut *mut Projection) -> i32 {
    if handle == 0 || target.is_null() || view.is_null() { return -1; }
    let Some(bytes) = written_layer(&*(handle as *const Handle), index) else { return -1; };
    let target = &*target;
    let changed = target.install_bytes(*bytes);
    *view = if changed { projection(target) } else { std::ptr::null_mut() };
    0
}

/// Compatibility export for mutable Java arrays/subclass targets only.
/// # Safety
/// The handle is live/excluded and output spans exactly LAYER writable bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_result_copy(handle: u64, index: i32,
    output: *mut u8, length: i32) -> i32 {
    if handle == 0 || output.is_null() || length != LAYER as i32 { return -1; }
    let Some(bytes) = written_layer(&*(handle as *const Handle), index) else { return -1; };
    std::ptr::copy_nonoverlapping(bytes.as_ptr(), output, LAYER);
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

/// Verification: copies the installed tables' type of each state when `out`
/// holds enough values. Returns the state count, or -1 without tables.
/// # Safety
/// A non-null `out` addresses `out_len` values.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_state_types(out: *mut u16, out_len: i32) -> i32 {
    let Some(tables) = super::installed_tables() else { return -1 };
    if !out.is_null() && out_len as usize >= tables.state_types.len() {
        std::slice::from_raw_parts_mut(out, tables.state_types.len()).copy_from_slice(&tables.state_types);
    }
    tables.state_types.len() as i32
}

/// Seed a retained native layer without any Java light-byte projection.
/// # Safety
/// Owner is live/excluded; columns, entries, out and view cover 1280 i32s,
/// 8192 i64s, 3 i32s and one pointer, respectively, all valid and disjoint.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_sky_owned(layer: *const Layer, columns: *const i32,
    bottom: i32, min_x: i32, min_z: i32, entries: *mut i64, out: *mut i32,
    projection_out: *mut *mut Projection) -> i32 {
    if layer.is_null() || columns.is_null() || entries.is_null() || out.is_null()
        || projection_out.is_null() || bottom % 16 != 0 { return -1; }
    let layer = &*layer;
    let view = layer.view();
    let mut bytes = [0u8; LAYER];
    if !view.copy_bytes_into(&mut bytes) {
        if !(0..16).contains(&view.default_value()) { return -1; }
        bytes.fill((view.default_value() * 17) as u8);
    }
    let columns = std::slice::from_raw_parts(columns, super::seed::COLUMNS);
    let entries = std::slice::from_raw_parts_mut(entries, 8192);
    let mut pairs = 0;
    let (wrote, below) = super::seed::sky_section(&mut bytes, columns, bottom, min_x, min_z, &mut |pos, entry| {
        entries[pairs * 2] = pos; entries[pairs * 2 + 1] = entry as i64; pairs += 1;
    });
    let changed = wrote && layer.install_bytes(bytes);
    *projection_out = if changed { projection(layer) } else { std::ptr::null_mut() };
    *out = pairs as i32; *out.add(1) = wrote as i32; *out.add(2) = below as i32;
    0
}
