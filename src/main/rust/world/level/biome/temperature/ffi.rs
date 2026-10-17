//! Private biome service entry points. Only FFI borrows caller-owned noise data.
use super::*;
pub(in crate::world::level::biome) unsafe fn retain(id: u64) -> Arc<Cache> {
    let p = std::ptr::with_exposed_provenance::<Cache>(id as usize);
    Arc::increment_strong_count(p);
    Arc::from_raw(p)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_create() -> u64 {
    Cache::create().map_or(0, |c| Arc::into_raw(c).expose_provenance() as u64)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_release(id: u64) {
    if id != 0 {
        drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Cache>(
            id as usize,
        )));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_lookup(id: u64, key: i64, mode: i32) -> i32 {
    retain(id).lookup(key, mode)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_frame(
    id: u64,
    key: i64,
    sea: i32,
    mode: i32,
    modifier: i32,
) -> u64 {
    Box::into_raw(Box::new(Frame::new(
        Some(retain(id)),
        key,
        sea,
        mode,
        modifier,
        0.,
    )))
    .expose_provenance() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_modifier(modifier: i32, base: u32) -> u64 {
    Box::into_raw(Box::new(Frame::new(
        None,
        0,
        0,
        2,
        modifier,
        f32::from_bits(base),
    )))
    .expose_provenance() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_next(id: u64, response: u64, failed: i32) -> i32 {
    (&mut *std::ptr::with_exposed_provenance_mut::<Frame>(id as usize)).next(response, failed != 0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_noise(
    id: u64,
    state: *const crate::world::level::levelgen::synth::State,
    bytes: u64,
) -> i32 {
    // Caller supplies the sealed immutable NativeNoiseState, validated by its
    // synthesis owner before publication. Bound the borrowed header/trailing data
    // here; do not repeat full permutation validation for every temperature miss.
    if state.is_null() || (state as usize) % 8 != 0 || bytes < 80 {
        return -1;
    }
    let s = &*state;
    if s.kind != 6 || s.n2 != 0 || s.n3 != 0 || bytes != 80 + 1320 * u64::from(s.n1) {
        return -1;
    }
    let f = &mut *std::ptr::with_exposed_provenance_mut::<Frame>(id as usize);
    if f.header[0] != 5 {
        return -2;
    }
    let value = crate::world::level::levelgen::synth::noise_eval(
        state,
        f64::from_bits(f.header[2]),
        f64::from_bits(f.header[3]),
        0.,
        0.,
        0.,
        0,
    );
    f.noise_reply = Some(value.to_bits());
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_frame_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(
            std::ptr::with_exposed_provenance_mut::<Frame>(id as usize),
        ));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_statistics(out: *mut u64) {
    for (i, n) in statistics().into_iter().enumerate() {
        out.add(i).write(n);
    }
}

/// Verification only: pairs are copied in real FIFO order. No borrowed output
/// survives this call. A zero capacity requests the current entry count.
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_snapshot(
    id: u64,
    out: *mut u64,
    capacity: u64,
) -> u64 {
    let cache = retain(id);
    let store = cache.store.lock().unwrap_or_else(|p| p.into_inner());
    if capacity >= store.order.len() as u64 && !out.is_null() {
        for (i, key) in store.order.iter().enumerate() {
            out.add(2 * i).write(*key as u64);
            out.add(2 * i + 1)
                .write(u64::from(store.values[key].to_bits()));
        }
    }
    store.order.len() as u64
}
/// Verification only: validate and allocate a replacement before changing the
/// actual cache. This does not invoke temperature policy or count real commits.
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_temperature_restore(
    id: u64,
    data: *const u64,
    count: u64,
) -> i32 {
    if count > CAPACITY as u64 || (count != 0 && data.is_null()) {
        return -1;
    }
    let mut next = Store::default();
    if next.values.try_reserve(CAPACITY).is_err() || next.order.try_reserve(CAPACITY).is_err() {
        return -2;
    }
    for i in 0..count as usize {
        let key = data.add(2 * i).read() as i64;
        let value = f32::from_bits(data.add(2 * i + 1).read() as u32);
        if next.values.insert(key, value).is_some() {
            return -1;
        }
        next.order.push_back(key);
    }
    *retain(id).store.lock().unwrap_or_else(|p| p.into_inner()) = next;
    0
}
