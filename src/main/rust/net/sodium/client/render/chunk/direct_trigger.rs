use std::cmp::Ordering;
use std::collections::{BTreeMap, HashMap};
use std::slice;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

const EARLY_TRIGGER_FACTOR: f64 = 0.9;
const TRIGGER_ANGLE: f64 = std::f64::consts::PI / 18.0;
const EARLY_TRIGGER_ANGLE_COS: f64 = 0.9876883405951378;
const SECTION_CENTER_DIST_SQUARED: f64 = 3.0 * 8.0 * 8.0 + 1.0;
const SECTION_CENTER_DIST: f64 = 13.892443989449804;
const TRIGGER_DISTANCE: f64 = 1.0;
const EARLY_TRIGGER_DISTANCE_SQUARED: f64 =
    TRIGGER_DISTANCE * EARLY_TRIGGER_FACTOR * TRIGGER_DISTANCE * EARLY_TRIGGER_FACTOR;

#[derive(Clone, Copy, Debug, PartialEq)]
struct OrderedF64(f64);

impl Eq for OrderedF64 {}

impl PartialOrd for OrderedF64 {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for OrderedF64 {
    fn cmp(&self, other: &Self) -> Ordering {
        self.0.total_cmp(&other.0)
    }
}

#[derive(Clone, Debug)]
struct DirectTriggerEntry {
    section_pos: i64,
    section_center: [f64; 3],
    trigger_camera_pos: [f64; 3],
    trigger_key: OrderedF64,
}

struct NativeDirectTriggers {
    accumulated_distance: f64,
    entries: HashMap<i64, DirectTriggerEntry>,
    keys: BTreeMap<OrderedF64, Vec<i64>>,
}

impl NativeDirectTriggers {
    fn new() -> Self {
        Self {
            accumulated_distance: 0.0,
            entries: HashMap::new(),
            keys: BTreeMap::new(),
        }
    }

    fn direct_trigger_count(&self) -> usize {
        self.entries.len()
    }

    fn process_triggers(&mut self, start: [f64; 3], end: [f64; 3]) -> Result<Vec<i64>, i32> {
        self.accumulated_distance += distance(start, end);
        if !self.accumulated_distance.is_finite() {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let boundary = OrderedF64(self.accumulated_distance);
        let due_keys: Vec<OrderedF64> = self.keys.range(..boundary).map(|(key, _)| *key).collect();
        let mut triggered = Vec::new();

        for key in due_keys {
            let Some(sections) = self.keys.remove(&key) else {
                continue;
            };

            for section_pos in sections {
                let Some(mut entry) = self.entries.remove(&section_pos) else {
                    continue;
                };

                if self.process_single_trigger(&mut entry, end)? {
                    triggered.push(entry.section_pos);
                }
                self.insert_entry(entry)?;
            }
        }

        Ok(triggered)
    }

    fn integrate_section(
        &mut self,
        section_pos: i64,
        section_x: i32,
        section_y: i32,
        section_z: i32,
        start: [f64; 3],
        end: [f64; 3],
    ) -> Result<bool, i32> {
        self.remove_section(section_pos);

        let mut entry = DirectTriggerEntry {
            section_pos,
            section_center: section_center(section_x, section_y, section_z),
            trigger_camera_pos: start,
            trigger_key: OrderedF64(f64::INFINITY),
        };

        let triggered = if start != end {
            self.process_single_trigger(&mut entry, end)?
        } else {
            let key = self.initial_trigger_key(&entry, start)?;
            entry.trigger_key = OrderedF64(key);
            false
        };

        self.insert_entry(entry)?;
        Ok(triggered)
    }

    fn remove_section(&mut self, section_pos: i64) {
        let Some(entry) = self.entries.remove(&section_pos) else {
            return;
        };

        let mut remove_key = false;
        if let Some(sections) = self.keys.get_mut(&entry.trigger_key) {
            sections.retain(|existing| *existing != section_pos);
            remove_key = sections.is_empty();
        }
        if remove_key {
            self.keys.remove(&entry.trigger_key);
        }
    }

    fn process_single_trigger(
        &self,
        entry: &mut DirectTriggerEntry,
        camera: [f64; 3],
    ) -> Result<bool, i32> {
        let (triggered, key) = if is_angle_triggering(entry, camera) {
            let mut remaining_angle = TRIGGER_ANGLE;
            let angle_cos = center_relative_angle_cos(entry, entry.trigger_camera_pos, camera);

            let triggered = if angle_cos <= EARLY_TRIGGER_ANGLE_COS {
                entry.trigger_camera_pos = camera;
                true
            } else {
                remaining_angle -= angle_cos.acos();
                false
            };

            (
                triggered,
                self.direct_angle_trigger_key(entry, remaining_angle)?,
            )
        } else {
            let mut remaining_distance = TRIGGER_DISTANCE;
            let distance_squared = distance_squared(entry.trigger_camera_pos, camera);

            let triggered = if distance_squared >= EARLY_TRIGGER_DISTANCE_SQUARED {
                entry.trigger_camera_pos = camera;
                true
            } else {
                remaining_distance -= distance_squared.sqrt();
                false
            };

            (
                triggered,
                self.direct_distance_trigger_key(remaining_distance)?,
            )
        };

        entry.trigger_key = OrderedF64(key);
        Ok(triggered)
    }

    fn initial_trigger_key(
        &self,
        entry: &DirectTriggerEntry,
        camera: [f64; 3],
    ) -> Result<f64, i32> {
        if is_angle_triggering(entry, camera) {
            self.direct_angle_trigger_key(entry, TRIGGER_ANGLE)
        } else {
            self.direct_distance_trigger_key(TRIGGER_DISTANCE)
        }
    }

    fn direct_angle_trigger_key(
        &self,
        entry: &DirectTriggerEntry,
        remaining_angle: f64,
    ) -> Result<f64, i32> {
        let trigger_camera_section_center_distance =
            distance(entry.section_center, entry.trigger_camera_pos);
        let center_min_distance =
            remaining_angle.tan() * (trigger_camera_section_center_distance - SECTION_CENTER_DIST);
        checked_key(self.accumulated_distance + center_min_distance)
    }

    fn direct_distance_trigger_key(&self, remaining_distance: f64) -> Result<f64, i32> {
        checked_key(self.accumulated_distance + remaining_distance)
    }

    fn insert_entry(&mut self, entry: DirectTriggerEntry) -> Result<(), i32> {
        checked_key(entry.trigger_key.0)?;

        let section_pos = entry.section_pos;
        let key = entry.trigger_key;
        self.keys.entry(key).or_default().push(section_pos);
        self.entries.insert(section_pos, entry);
        Ok(())
    }
}

fn checked_key(key: f64) -> Result<f64, i32> {
    if key.is_finite() {
        Ok(key)
    } else {
        Err(ERR_INVALID_ARGUMENT)
    }
}

fn section_center(section_x: i32, section_y: i32, section_z: i32) -> [f64; 3] {
    [
        section_x as f64 * 16.0 + 8.0,
        section_y as f64 * 16.0 + 8.0,
        section_z as f64 * 16.0 + 8.0,
    ]
}

fn is_angle_triggering(entry: &DirectTriggerEntry, camera: [f64; 3]) -> bool {
    distance_squared(entry.section_center, camera) > SECTION_CENTER_DIST_SQUARED
}

fn center_relative_angle_cos(entry: &DirectTriggerEntry, a: [f64; 3], b: [f64; 3]) -> f64 {
    angle_cos(
        entry.section_center[0] - a[0],
        entry.section_center[1] - a[1],
        entry.section_center[2] - a[2],
        entry.section_center[0] - b[0],
        entry.section_center[1] - b[1],
        entry.section_center[2] - b[2],
    )
}

fn angle_cos(ax: f64, ay: f64, az: f64, bx: f64, by: f64, bz: f64) -> f64 {
    let length_a = ax.mul_add(ax, ay.mul_add(ay, az * az)).sqrt();
    let length_b = bx.mul_add(bx, by.mul_add(by, bz * bz)).sqrt();
    let dot = ax.mul_add(bx, ay.mul_add(by, az * bz));
    dot / (length_a * length_b)
}

fn distance(a: [f64; 3], b: [f64; 3]) -> f64 {
    distance_squared(a, b).sqrt()
}

fn distance_squared(a: [f64; 3], b: [f64; 3]) -> f64 {
    let dx = a[0] - b[0];
    let dy = a[1] - b[1];
    let dz = a[2] - b[2];
    dx.mul_add(dx, dy.mul_add(dy, dz * dz))
}

fn handle_mut<'a>(handle: u64) -> Result<&'a mut NativeDirectTriggers, i32> {
    if handle == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(unsafe { &mut *(handle as *mut NativeDirectTriggers) })
}

fn movement(
    start_x: f64,
    start_y: f64,
    start_z: f64,
    end_x: f64,
    end_y: f64,
    end_z: f64,
) -> [f64; 6] {
    [start_x, start_y, start_z, end_x, end_y, end_z]
}

fn validate_movement(values: [f64; 6]) -> Result<([f64; 3], [f64; 3]), i32> {
    if values.iter().any(|value| !value.is_finite()) {
        return Err(ERR_INVALID_ARGUMENT);
    }

    Ok((
        [values[0], values[1], values[2]],
        [values[3], values[4], values[5]],
    ))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeDirectTriggers::new())) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    drop(Box::from_raw(handle as *mut NativeDirectTriggers));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_count(
    handle: u64,
    output_count: *mut i32,
) -> i32 {
    if output_count.is_null() {
        return ERR_NULL_POINTER;
    }

    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let count = triggers.direct_trigger_count();
    if count > i32::MAX as usize {
        return ERR_INVALID_ARGUMENT;
    }

    *output_count = count as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_remove(
    handle: u64,
    section_pos: i64,
) -> i32 {
    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    triggers.remove_section(section_pos);
    OK
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_integrate(
    handle: u64,
    section_pos: i64,
    section_x: i32,
    section_y: i32,
    section_z: i32,
    start_x: f64,
    start_y: f64,
    start_z: f64,
    end_x: f64,
    end_y: f64,
    end_z: f64,
    output_triggered: *mut i32,
) -> i32 {
    if output_triggered.is_null() {
        return ERR_NULL_POINTER;
    }

    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let (start, end) =
        match validate_movement(movement(start_x, start_y, start_z, end_x, end_y, end_z)) {
            Ok(value) => value,
            Err(status) => return status,
        };
    let triggered = match triggers.integrate_section(
        section_pos,
        section_x,
        section_y,
        section_z,
        start,
        end,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *output_triggered = if triggered { 1 } else { 0 };
    OK
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_process(
    handle: u64,
    start_x: f64,
    start_y: f64,
    start_z: f64,
    end_x: f64,
    end_y: f64,
    end_z: f64,
    output_sections: *mut i64,
    output_capacity: i32,
    output_count: *mut i32,
) -> i32 {
    if output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_count.is_null() || (output_capacity > 0 && output_sections.is_null()) {
        return ERR_NULL_POINTER;
    }

    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let (start, end) =
        match validate_movement(movement(start_x, start_y, start_z, end_x, end_y, end_z)) {
            Ok(value) => value,
            Err(status) => return status,
        };
    let triggered = match triggers.process_triggers(start, end) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if triggered.len() > output_capacity as usize || triggered.len() > i32::MAX as usize {
        return ERR_CAPACITY;
    }

    if !triggered.is_empty() {
        let output = slice::from_raw_parts_mut(output_sections, output_capacity as usize);
        output[..triggered.len()].copy_from_slice(&triggered);
    }
    *output_count = triggered.len() as i32;
    OK
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn direct_distance_trigger_fires_after_threshold_and_reinserts() {
        let mut triggers = NativeDirectTriggers::new();
        let section = 42;
        triggers
            .integrate_section(section, 0, 0, 0, [8.0, 8.0, 8.0], [8.0, 8.0, 8.0])
            .unwrap();

        assert!(triggers
            .process_triggers([8.0, 8.0, 8.0], [8.95, 8.0, 8.0])
            .unwrap()
            .is_empty());
        assert_eq!(
            triggers
                .process_triggers([8.95, 8.0, 8.0], [9.05, 8.0, 8.0])
                .unwrap(),
            vec![section]
        );
        assert_eq!(triggers.direct_trigger_count(), 1);
    }

    #[test]
    fn removed_sections_do_not_trigger() {
        let mut triggers = NativeDirectTriggers::new();
        let section = 42;
        triggers
            .integrate_section(section, 0, 0, 0, [8.0, 8.0, 8.0], [8.0, 8.0, 8.0])
            .unwrap();
        triggers.remove_section(section);

        assert!(triggers
            .process_triggers([8.0, 8.0, 8.0], [18.0, 8.0, 8.0])
            .unwrap()
            .is_empty());
        assert_eq!(triggers.direct_trigger_count(), 0);
    }

    #[test]
    fn catchup_integration_can_trigger_immediately() {
        let mut triggers = NativeDirectTriggers::new();
        assert!(triggers
            .integrate_section(42, 0, 0, 0, [8.0, 8.0, 8.0], [9.0, 8.0, 8.0])
            .unwrap());
        assert_eq!(triggers.direct_trigger_count(), 1);
    }
}
