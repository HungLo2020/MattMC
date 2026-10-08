//! Retained DH generic box groups (clouds, beacons, API objects). DH marks a
//! group dirty only when its boxes change (`triggerBoxChange`, or a changed
//! box count); clouds otherwise just move their origin each frame. Java
//! registers a group's boxes here, in group coordinates, only when it
//! changes; each frame then carries one instance per active group (origin,
//! light, shading), and the frame decode expands them into the frame's
//! camera-relative generic boxes exactly as Java formerly did:
//! `(box + origin) - camera` in f64, then f32.
//!
//! An instance whose group or generation is unknown (a registration Java
//! sent but a dropped submission never delivered, or a registry reset) is
//! skipped and raises the resend flag; Java then re-registers every group.

use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use crate::render::bridge::abi::{FfiDhGenericGroupBox, FfiDhGenericGroupInstance};
use crate::render::vulkanic::{GalError, GalResult};
use crate::render::worldrender::WorldDistantHorizonsGenericBoxRequest;

/// Boxes across all retained groups (DH's own per-frame cap is 10,000).
pub const MAX_RETAINED_DH_GENERIC_BOXES: usize = 65_536;
const MAX_RETAINED_DH_GENERIC_GROUPS: usize = 4096;

#[derive(Debug)]
struct RetainedGroup {
    generation: u64,
    boxes: Arc<[FfiDhGenericGroupBox]>,
}

#[derive(Debug, Default)]
struct Registry {
    groups: HashMap<u64, RetainedGroup>,
    boxes: usize,
}

fn registry() -> &'static Mutex<Registry> {
    static REGISTRY: OnceLock<Mutex<Registry>> = OnceLock::new();
    REGISTRY.get_or_init(Default::default)
}

static RESEND_REQUESTED: AtomicBool = AtomicBool::new(false);

fn lock() -> std::sync::MutexGuard<'static, Registry> {
    registry().lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn set_group(id: u64, generation: u64, boxes: &[FfiDhGenericGroupBox]) -> GalResult<()> {
    if id == 0 || generation == 0 {
        return Err(GalError::invalid_argument("DH generic group id and generation must be non-zero"));
    }
    for item in boxes {
        if item.min.iter().chain(item.max.iter()).any(|value| !value.is_finite())
            || item.min.iter().zip(item.max.iter()).any(|(min, max)| min > max)
            || item.material > 0xff
        {
            return Err(GalError::invalid_argument("DH generic group box has invalid bounds or material"));
        }
    }
    let mut registry = lock();
    let previous = registry.groups.get(&id).map_or(0, |group| group.boxes.len());
    if !registry.groups.contains_key(&id) && registry.groups.len() >= MAX_RETAINED_DH_GENERIC_GROUPS {
        return Err(GalError::invalid_argument("DH generic group registry is full"));
    }
    let total = registry.boxes - previous + boxes.len();
    if total > MAX_RETAINED_DH_GENERIC_BOXES {
        return Err(GalError::invalid_argument("DH generic group box bound exceeded"));
    }
    registry.boxes = total;
    registry.groups.insert(id, RetainedGroup { generation, boxes: boxes.into() });
    Ok(())
}

fn release_group(id: u64) {
    let mut registry = lock();
    if let Some(group) = registry.groups.remove(&id) {
        registry.boxes -= group.boxes.len();
    }
}

fn clear_groups() {
    let mut registry = lock();
    registry.groups.clear();
    registry.boxes = 0;
}

/// Expands the frame's group instances (draw order) into camera-relative
/// generic boxes; the instance index is the box's render-group ordinal.
pub(super) fn expand_dh_generic_groups(
    instances: &[FfiDhGenericGroupInstance],
    camera: [f64; 3],
) -> GalResult<Vec<WorldDistantHorizonsGenericBoxRequest>> {
    if instances.is_empty() {
        return Ok(Vec::new());
    }
    if instances.len() > 0x1_0000 || camera.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument("DH generic group instances or camera are invalid"));
    }
    let registry = lock();
    let mut boxes = Vec::new();
    for (ordinal, instance) in instances.iter().enumerate() {
        if instance.flags & !1 != 0
            || instance.origin.iter().any(|value| !value.is_finite())
            || instance.shading.iter().any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument("DH generic group instance has invalid flags, origin or shading"));
        }
        let Some(group) = registry.groups.get(&instance.group_id).filter(|group| group.generation == instance.generation)
        else {
            RESEND_REQUESTED.store(true, Ordering::Release);
            continue;
        };
        let place = |local: [f64; 3]| [0, 1, 2].map(|axis| ((local[axis] + instance.origin[axis]) - camera[axis]) as f32);
        boxes.extend(group.boxes.iter().map(|item| WorldDistantHorizonsGenericBoxRequest {
            min: place(item.min),
            max: place(item.max),
            color_argb: item.color_argb,
            packed_light: instance.packed_light,
            shading: instance.shading,
            ssao_enabled: instance.flags & 1 != 0,
            material: item.material,
            group: ordinal as u32,
        }));
    }
    Ok(boxes)
}

/// Registers (or replaces) a group's boxes. Returns 0, or -2 when rejected.
///
/// # Safety
/// `boxes` addresses `count` records (it may be null when `count` is 0).
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_dh_generic_group_set(
    id: u64,
    generation: u64,
    boxes: *const FfiDhGenericGroupBox,
    count: u32,
) -> i32 {
    if count != 0 && boxes.is_null() {
        return -2;
    }
    let boxes = if count == 0 { &[][..] } else { std::slice::from_raw_parts(boxes, count as usize) };
    match set_group(id, generation, boxes) {
        Ok(()) => 0,
        Err(_) => -2,
    }
}

#[no_mangle]
pub extern "C" fn mattmc_vulkanic_dh_generic_group_release(id: u64) {
    release_group(id);
}

#[no_mangle]
pub extern "C" fn mattmc_vulkanic_dh_generic_groups_clear() {
    clear_groups();
}

/// Whether a frame referenced a group Rust does not hold (cleared on read):
/// Java then re-registers every group.
#[no_mangle]
pub extern "C" fn mattmc_vulkanic_dh_generic_groups_take_resend() -> i32 {
    RESEND_REQUESTED.swap(false, Ordering::AcqRel) as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    fn group_box(min: [f64; 3], max: [f64; 3]) -> FfiDhGenericGroupBox {
        FfiDhGenericGroupBox { min, max, color_argb: 0xffff_ffff, material: 3 }
    }

    fn instance(id: u64, generation: u64, origin: [f64; 3]) -> FfiDhGenericGroupInstance {
        FfiDhGenericGroupInstance {
            group_id: id,
            generation,
            origin,
            packed_light: 0x00f0_00f0,
            flags: 1,
            shading: [0.8, 0.8, 0.6, 0.6, 1.0, 0.5],
        }
    }

    #[test]
    fn retained_groups_expand_like_the_former_java_camera_relative_boxes() {
        let id = 0x9e00_0001;
        set_group(id, 1, &[group_box([0.0, 0.0, 0.0], [12.0, 4.0, 12.0]), group_box([12.0, 0.0, 0.0], [24.0, 4.0, 12.0])])
            .unwrap();
        let origin = [100_000.25, 384.0, -2_048.75];
        let camera = [99_990.125, 70.5, -2_000.0];
        let boxes = expand_dh_generic_groups(&[instance(id, 1, origin)], camera).unwrap();
        assert_eq!(2, boxes.len());
        // Java: (float)(box.minPos.x + origin.x - camPos.x).
        assert_eq!((12.0f64 + origin[0] - camera[0]) as f32, boxes[1].min[0]);
        assert_eq!((4.0f64 + origin[1] - camera[1]) as f32, boxes[0].max[1]);
        assert_eq!((0, true, 3), (boxes[1].group, boxes[1].ssao_enabled, boxes[1].material));
        release_group(id);
    }

    #[test]
    fn unknown_or_stale_groups_are_skipped_and_request_a_resend() {
        let id = 0x9e00_0002;
        set_group(id, 4, &[group_box([0.0; 3], [1.0; 3])]).unwrap();
        mattmc_vulkanic_dh_generic_groups_take_resend();
        let boxes = expand_dh_generic_groups(&[instance(id, 3, [0.0; 3]), instance(id, 4, [0.0; 3])], [0.0; 3]).unwrap();
        assert_eq!(1, boxes.len(), "the stale generation is skipped");
        assert_eq!(1, boxes[0].group, "ordinals follow the instance order");
        assert_eq!(1, mattmc_vulkanic_dh_generic_groups_take_resend());
        assert_eq!(0, mattmc_vulkanic_dh_generic_groups_take_resend());
        release_group(id);
        assert!(set_group(id, 1, &[group_box([1.0; 3], [0.0; 3])]).is_err(), "inverted bounds are rejected");
    }
}
