use std::cell::RefCell;
use std::cmp::Ordering;
use std::collections::{BTreeMap, HashMap};
use std::slice;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;
const LAST_ERROR_CAP: usize = 4096;

const EARLY_TRIGGER_FACTOR: f64 = 0.9;
const TRIGGER_ANGLE: f64 = std::f64::consts::PI / 18.0;
const EARLY_TRIGGER_ANGLE_COS: f64 = 0.9876883405951378;
const SECTION_CENTER_DIST_SQUARED: f64 = 3.0 * 8.0 * 8.0 + 1.0;
const SECTION_CENTER_DIST: f64 = 13.892443989449804;
const TRIGGER_DISTANCE: f64 = 1.0;
const EARLY_TRIGGER_DISTANCE_SQUARED: f64 =
    TRIGGER_DISTANCE * EARLY_TRIGGER_FACTOR * TRIGGER_DISTANCE * EARLY_TRIGGER_FACTOR;

thread_local! {
    static LAST_ERROR: RefCell<String> = const { RefCell::new(String::new()) };
}

fn clear_last_error() {
    LAST_ERROR.with(|error| error.borrow_mut().clear());
}

fn record_error(status: i32, variant: &'static str, context: String) -> i32 {
    LAST_ERROR.with(|error| {
        let mut error = error.borrow_mut();
        error.clear();
        error.push_str(status_name(status));
        error.push_str(" variant=");
        error.push_str(variant);
        if !context.is_empty() {
            error.push(' ');
            error.push_str(&context);
        }
        if error.len() > LAST_ERROR_CAP {
            error.truncate(LAST_ERROR_CAP);
        }
    });
    status
}

fn status_name(status: i32) -> &'static str {
    match status {
        OK => "OK",
        ERR_NULL_POINTER => "ERR_NULL_POINTER",
        ERR_INVALID_ARGUMENT => "ERR_INVALID_ARGUMENT",
        ERR_CAPACITY => "ERR_CAPACITY",
        _ => "ERR_UNKNOWN",
    }
}

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

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct DirectTriggerStats {
    pub process_calls: u64,
    pub integrate_calls: u64,
    pub catchup_integrations: u64,
    pub angle_path_integrations: u64,
    pub distance_path_integrations: u64,
    pub invalid_angle_input_fallbacks: u64,
    pub total_movement_distance: f64,
    pub max_movement_distance: f64,
}

struct NativeDirectTriggers {
    accumulated_distance: f64,
    entries: HashMap<i64, DirectTriggerEntry>,
    keys: BTreeMap<OrderedF64, Vec<i64>>,
    stats: DirectTriggerStats,
}

impl NativeDirectTriggers {
    fn new() -> Self {
        Self {
            accumulated_distance: 0.0,
            entries: HashMap::new(),
            keys: BTreeMap::new(),
            stats: DirectTriggerStats::default(),
        }
    }

    fn direct_trigger_count(&self) -> usize {
        self.entries.len()
    }

    fn process_triggers(&mut self, start: [f64; 3], end: [f64; 3]) -> Result<Vec<i64>, i32> {
        self.stats.process_calls = self.stats.process_calls.saturating_add(1);
        self.record_movement(start, end);
        self.accumulated_distance += distance(start, end);
        if !self.accumulated_distance.is_finite() {
            return Err(record_error(
                ERR_INVALID_ARGUMENT,
                "InvalidArgument::NonFiniteAccumulatedDistance",
                format!(
                    "operation=process start={start:?} end={end:?} accumulated_distance={}",
                    self.accumulated_distance
                ),
            ));
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
        self.stats.integrate_calls = self.stats.integrate_calls.saturating_add(1);
        if start != end {
            self.stats.catchup_integrations = self.stats.catchup_integrations.saturating_add(1);
        }
        self.record_movement(start, end);
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
        &mut self,
        entry: &mut DirectTriggerEntry,
        camera: [f64; 3],
    ) -> Result<bool, i32> {
        let start_supports_angle = is_angle_triggering(entry, entry.trigger_camera_pos);
        let end_supports_angle = is_angle_triggering(entry, camera);
        let (triggered, key) = if start_supports_angle && end_supports_angle {
            self.stats.angle_path_integrations =
                self.stats.angle_path_integrations.saturating_add(1);
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
            self.stats.distance_path_integrations =
                self.stats.distance_path_integrations.saturating_add(1);
            if start_supports_angle != end_supports_angle {
                self.stats.invalid_angle_input_fallbacks =
                    self.stats.invalid_angle_input_fallbacks.saturating_add(1);
            }
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

    fn record_movement(&mut self, start: [f64; 3], end: [f64; 3]) {
        let movement = distance(start, end);
        if movement.is_finite() {
            self.stats.total_movement_distance += movement;
            if movement > self.stats.max_movement_distance {
                self.stats.max_movement_distance = movement;
            }
        }
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
        checked_key(
            self.accumulated_distance + center_min_distance,
            "direct_angle_trigger_key",
        )
    }

    fn direct_distance_trigger_key(&self, remaining_distance: f64) -> Result<f64, i32> {
        checked_key(
            self.accumulated_distance + remaining_distance,
            "direct_distance_trigger_key",
        )
    }

    fn insert_entry(&mut self, entry: DirectTriggerEntry) -> Result<(), i32> {
        checked_key(entry.trigger_key.0, "insert_entry")?;

        let section_pos = entry.section_pos;
        let key = entry.trigger_key;
        self.keys.entry(key).or_default().push(section_pos);
        self.entries.insert(section_pos, entry);
        Ok(())
    }
}

fn checked_key(key: f64, context: &'static str) -> Result<f64, i32> {
    if key.is_finite() {
        Ok(key)
    } else {
        Err(record_error(
            ERR_INVALID_ARGUMENT,
            "InvalidArgument::NonFiniteTriggerKey",
            format!("operation={context} key={key}"),
        ))
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
    let denominator = length_a * length_b;
    if denominator == 0.0 {
        return 1.0;
    }
    let dot = ax.mul_add(bx, ay.mul_add(by, az * bz));
    (dot / denominator).clamp(-1.0, 1.0)
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
        return Err(record_error(
            ERR_NULL_POINTER,
            "NullPointer::DirectTriggerHandle",
            "handle=0".to_string(),
        ));
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
        return Err(record_error(
            ERR_INVALID_ARGUMENT,
            "InvalidArgument::NonFiniteMovement",
            format!(
                "start=({},{},{}) end=({},{},{})",
                values[0], values[1], values[2], values[3], values[4], values[5]
            ),
        ));
    }

    Ok((
        [values[0], values[1], values[2]],
        [values[3], values[4], values[5]],
    ))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_create(output_handle: *mut u64) -> i32 {
    clear_last_error();
    if output_handle.is_null() {
        return record_error(
            ERR_NULL_POINTER,
            "NullPointer::OutputHandle",
            "operation=create".to_string(),
        );
    }

    *output_handle = Box::into_raw(Box::new(NativeDirectTriggers::new())) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_destroy(handle: u64) -> i32 {
    clear_last_error();
    if handle == 0 {
        return record_error(
            ERR_NULL_POINTER,
            "NullPointer::DirectTriggerHandle",
            "operation=destroy handle=0".to_string(),
        );
    }

    drop(Box::from_raw(handle as *mut NativeDirectTriggers));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_count(
    handle: u64,
    output_count: *mut i32,
) -> i32 {
    clear_last_error();
    if output_count.is_null() {
        return record_error(
            ERR_NULL_POINTER,
            "NullPointer::OutputCount",
            "operation=count".to_string(),
        );
    }

    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let count = triggers.direct_trigger_count();
    if count > i32::MAX as usize {
        return record_error(
            ERR_INVALID_ARGUMENT,
            "InvalidArgument::CountOverflow",
            format!("operation=count count={count}"),
        );
    }

    *output_count = count as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_stats(
    handle: u64,
    output_stats: *mut DirectTriggerStats,
) -> i32 {
    clear_last_error();
    if output_stats.is_null() {
        return record_error(
            ERR_NULL_POINTER,
            "NullPointer::OutputStats",
            "operation=stats".to_string(),
        );
    }

    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    *output_stats = triggers.stats;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_remove(
    handle: u64,
    section_pos: i64,
) -> i32 {
    clear_last_error();
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
    clear_last_error();
    if output_triggered.is_null() {
        return record_error(
            ERR_NULL_POINTER,
            "NullPointer::OutputTriggered",
            format!(
                "operation=integrate handle={handle} section_pos={section_pos} section=({section_x},{section_y},{section_z})"
            ),
        );
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
    clear_last_error();
    if output_capacity < 0 {
        return record_error(
            ERR_INVALID_ARGUMENT,
            "InvalidArgument::NegativeOutputCapacity",
            format!("operation=process output_capacity={output_capacity}"),
        );
    }
    if output_count.is_null() || (output_capacity > 0 && output_sections.is_null()) {
        return record_error(
            ERR_NULL_POINTER,
            "NullPointer::ProcessOutput",
            format!(
                "operation=process output_sections_null={} output_count_null={} output_capacity={output_capacity}",
                output_sections.is_null(),
                output_count.is_null()
            ),
        );
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
        return record_error(
            ERR_CAPACITY,
            "Capacity::TriggeredSections",
            format!(
                "operation=process triggered={} output_capacity={output_capacity}",
                triggered.len()
            ),
        );
    }

    if !triggered.is_empty() {
        let output = slice::from_raw_parts_mut(output_sections, output_capacity as usize);
        output[..triggered.len()].copy_from_slice(&triggered);
    }
    *output_count = triggered.len() as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_direct_triggers_last_error(
    output_bytes: *mut u8,
    output_capacity: usize,
    output_length: *mut usize,
) -> i32 {
    if output_length.is_null() {
        return ERR_NULL_POINTER;
    }

    let message = LAST_ERROR.with(|error| error.borrow().clone());
    let bytes = message.as_bytes();
    *output_length = bytes.len();

    if output_capacity > 0 && output_bytes.is_null() {
        return ERR_NULL_POINTER;
    }

    let written = bytes.len().min(output_capacity);
    if written > 0 {
        let output = slice::from_raw_parts_mut(output_bytes, output_capacity);
        output[..written].copy_from_slice(&bytes[..written]);
    }

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

    #[test]
    fn catchup_integration_from_section_center_does_not_generate_invalid_key() {
        let mut triggers = NativeDirectTriggers::new();
        assert!(triggers
            .integrate_section(42, 0, 0, 0, [8.0, 8.0, 8.0], [24.0, 8.0, 8.0])
            .unwrap());
        assert_eq!(triggers.direct_trigger_count(), 1);
        assert_eq!(triggers.stats.integrate_calls, 1);
        assert_eq!(triggers.stats.catchup_integrations, 1);
        assert_eq!(triggers.stats.invalid_angle_input_fallbacks, 1);
        assert_eq!(triggers.stats.distance_path_integrations, 1);
        assert!(triggers.stats.max_movement_distance >= 16.0);
    }

    #[test]
    fn angle_cosine_is_clamped_for_trigger_key_stability() {
        assert_eq!(angle_cos(1.0, 0.0, 0.0, 2.0, 0.0, 0.0), 1.0);
        assert_eq!(angle_cos(1.0, 0.0, 0.0, -2.0, 0.0, 0.0), -1.0);
        assert_eq!(angle_cos(0.0, 0.0, 0.0, 2.0, 0.0, 0.0), 1.0);
    }

    #[test]
    fn invalid_movement_records_precise_last_error_variant() {
        let mut handle = 0_u64;
        assert_eq!(
            unsafe { mattmc_sodium_direct_triggers_create(&mut handle) },
            OK
        );

        let mut triggered = 0_i32;
        let status = unsafe {
            mattmc_sodium_direct_triggers_integrate(
                handle,
                42,
                0,
                0,
                0,
                f64::NAN,
                8.0,
                8.0,
                24.0,
                8.0,
                8.0,
                &mut triggered,
            )
        };
        assert_eq!(status, ERR_INVALID_ARGUMENT);

        let mut buffer = [0_u8; 256];
        let mut length = 0_usize;
        assert_eq!(
            unsafe {
                mattmc_sodium_direct_triggers_last_error(
                    buffer.as_mut_ptr(),
                    buffer.len(),
                    &mut length,
                )
            },
            OK
        );
        let message = std::str::from_utf8(&buffer[..length]).unwrap();
        assert!(message.contains("ERR_INVALID_ARGUMENT"));
        assert!(message.contains("InvalidArgument::NonFiniteMovement"));

        assert_eq!(unsafe { mattmc_sodium_direct_triggers_destroy(handle) }, OK);
    }
}
