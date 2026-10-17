//! Scalar and borrowed CPU output boundaries; never hold a lock across callbacks.
use super::{
    policy::*,
    state::{water, Rain},
};
use std::ffi::c_void;
#[no_mangle]
pub extern "C" fn mattmc_fog_rain_create() -> *mut c_void {
    Box::into_raw(Box::new(Rain::default())).cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_fog_rain_release(owner: *mut c_void) {
    if !owner.is_null() {
        drop(Box::from_raw(owner.cast::<Rain>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_fog_rain_update(
    owner: *const c_void,
    rain: f32,
    sky_light: i32,
    precipitation: i32,
    ticks: f32,
) -> f32 {
    let Some(owner) = owner.cast::<Rain>().as_ref() else {
        return f32::NAN;
    };
    owner.update(rain, sky_light, precipitation, ticks)
}
#[no_mangle]
pub extern "C" fn mattmc_fog_water_color(now: i64, biome: i32) -> i32 {
    water().lock().unwrap().color(now, biome)
}
#[no_mangle]
pub extern "C" fn mattmc_fog_water_reset() {
    water().lock().unwrap().changed = -1;
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_fog_ranges(
    kind: i32,
    distance: f32,
    parameter: f32,
    cloud: f32,
    flags: i32,
    out: *mut [f32; 6],
) {
    if let Some(out) = out.as_mut() {
        ranges(kind, distance, parameter, cloud, flags, out);
    }
}
#[no_mangle]
pub extern "C" fn mattmc_fog_void_darkness(onset: f32, min_y: i32, camera_y: f64) -> f32 {
    clamp((onset + min_y as f32 - camera_y as f32) / onset, 0.0, 1.0)
}
#[no_mangle]
pub extern "C" fn mattmc_fog_effect_darkness(
    kind: i32,
    original: f32,
    parameter: f32,
    ending: i32,
) -> f32 {
    if kind == 0 {
        if ending != 0 {
            max(parameter / 20.0, original)
        } else {
            1.0
        }
    } else {
        max(parameter, original)
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_fog_finish_color(
    base: i32,
    kind: i32,
    darkness: f32,
    world: f32,
    vision: f32,
    out: *mut [f32; 4],
) {
    if let Some(out) = out.as_mut() {
        *out = finish_color(base, kind, darkness, world, vision);
    }
}
#[no_mangle]
pub extern "C" fn mattmc_fog_air_color(
    red: f32,
    green: f32,
    blue: f32,
    sunrise: i32,
    sun_weight: f32,
    sky: i32,
    distance: i32,
    rain: f32,
    thunder: f32,
) -> i32 {
    air(
        [red, green, blue],
        sunrise,
        sun_weight,
        sky,
        distance,
        rain,
        thunder,
    )
}
