//! Ordinary FFI calls: native CPU owners, never GPU resources. Callers pin an
//! owner through prepare/color and through frame decoding (including async join).
use super::{Group, Inputs};
use std::sync::Mutex;

const HISTORY: usize = 16;
pub(crate) struct Owner(Mutex<State>);
struct State {
    group: Group,
    epoch: u64,
    poses: [(u64, [f32; 3]); HISTORY],
}
#[repr(C)]
pub struct ResultHeader {
    pub(crate) epoch: u64,
    active: i32,
    pub(crate) origin: [f32; 3],
}

/// # Safety
/// `address` is a live CPU owner returned by create, pinned until this read
/// completes. No pointer is retained by the renderer; only copied coordinates.
pub(crate) unsafe fn origin(address: u64, epoch: u64) -> Option<[f64; 3]> {
    if address == 0 || address % std::mem::align_of::<Owner>() as u64 != 0 || epoch == 0 {
        return None;
    }
    let owner = &*(address as *const Owner);
    let state = owner.0.lock().unwrap_or_else(|p| p.into_inner());
    let pose = state.poses[epoch as usize % HISTORY];
    (pose.0 == epoch).then(|| pose.1.map(f64::from))
}

#[no_mangle]
pub extern "C" fn mattmc_dh_cloud_create(width: i32, x: i32, z: i32, now: i64) -> *mut Owner {
    let Some(group) = Group::new(width, [x, z], now) else {
        return std::ptr::null_mut();
    };
    Box::into_raw(Box::new(Owner(Mutex::new(State {
        group,
        epoch: 0,
        poses: [(0, [0.0; 3]); HISTORY],
    }))))
}

/// # Safety
/// Unique allocation from create; no outstanding calls or frame decoders.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_cloud_release(owner: *mut Owner) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}

/// # Safety
/// Owner remains live, and `output` is writable/aligned for one 24-byte header.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_cloud_prepare(
    owner: *const Owner,
    now: i64,
    speed: f32,
    x: f64,
    y: f64,
    z: f64,
    lx: f32,
    ly: f32,
    lz: f32,
    radius: i32,
    height: i32,
    output: *mut ResultHeader,
) -> i32 {
    if owner.is_null() || output.is_null() {
        return -1;
    }
    let mut state = (*owner).0.lock().unwrap_or_else(|p| p.into_inner());
    let prepared = state.group.prepare(Inputs {
        enabled: true,
        now_millis: now,
        speed,
        camera: [x, y, z],
        look: [lx, ly, lz],
        radius_chunks: radius,
        max_height: height,
    });
    let origin = prepared.origin.unwrap();
    state.epoch = state.epoch.wrapping_add(1).max(1);
    let epoch = state.epoch;
    state.poses[epoch as usize % HISTORY] = (epoch, origin);
    output.write(ResultHeader {
        epoch,
        active: i32::from(prepared.active),
        origin,
    });
    0
}

/// # Safety
/// A live pinned owner; called only after the admitted Java color query.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_cloud_color(owner: *const Owner, color: u32) -> i32 {
    if owner.is_null() {
        return -1;
    }
    i32::from(
        (*owner)
            .0
            .lock()
            .unwrap_or_else(|p| p.into_inner())
            .group
            .color_changed(color),
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn pose_generations_are_bounded_and_read_the_requested_frame() {
        unsafe {
            let owner = mattmc_dh_cloud_create(2048, 0, 0, 0);
            let mut out = ResultHeader {
                epoch: 0,
                active: 0,
                origin: [0.0; 3],
            };
            for now in 1..=16 {
                assert_eq!(
                    0,
                    mattmc_dh_cloud_prepare(
                        owner,
                        now * 1000,
                        6.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        1.0,
                        128,
                        320,
                        &mut out
                    )
                );
            }
            assert_eq!(Some([1018.0, 520.0, 1024.0]), origin(owner as u64, 1));
            mattmc_dh_cloud_prepare(
                owner, 17000, 6.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 128, 320, &mut out,
            );
            assert_eq!(None, origin(owner as u64, 1));
            assert_eq!(Some([922.0, 520.0, 1024.0]), origin(owner as u64, 17));
            assert_eq!(None, origin(0, 1));
            mattmc_dh_cloud_release(owner);
        }
        assert_eq!(24, std::mem::size_of::<ResultHeader>());
    }
}
