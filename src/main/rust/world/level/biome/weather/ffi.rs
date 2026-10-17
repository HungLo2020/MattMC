//! Private caller-owned weather frames. All pointer construction stays here.
use super::*;
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_weather_frame(mode: i32, sea: i32, edge: i32) -> u64 {
    Box::into_raw(Box::new(Frame::new(mode, sea, edge != 0))).expose_provenance() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_weather_bind(id: u64, cache: u64, modifier: i32) -> i32 {
    let f = &mut *std::ptr::with_exposed_provenance_mut::<Frame>(id as usize);
    f.bind(
        crate::world::level::biome::temperature::ffi::retain(cache),
        modifier,
    )
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_weather_next(id: u64, response: u64, failed: i32) -> i32 {
    (&mut *std::ptr::with_exposed_provenance_mut::<Frame>(id as usize)).next(response, failed != 0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_weather_noise(
    id: u64,
    state: *const crate::world::level::levelgen::synth::State,
    bytes: u64,
) -> i32 {
    let f = &mut *std::ptr::with_exposed_provenance_mut::<Frame>(id as usize);
    let Some(child) = f.child.as_mut() else {
        return -2;
    };
    crate::world::level::biome::temperature::ffi::mattmc_biome_temperature_noise(
        std::ptr::from_mut(child).expose_provenance() as u64,
        state,
        bytes,
    )
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_weather_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(
            std::ptr::with_exposed_provenance_mut::<Frame>(id as usize),
        ));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_weather_statistics(out: *mut u64) {
    for (i, n) in statistics().into_iter().enumerate() {
        out.add(i).write(n);
    }
}
