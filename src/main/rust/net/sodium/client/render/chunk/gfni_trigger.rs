use std::cmp::Ordering;
use std::collections::{HashMap, HashSet};
use std::slice;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

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

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
struct NormalKey {
    x: u32,
    y: u32,
    z: u32,
}

impl NormalKey {
    fn new(x: f32, y: f32, z: f32) -> Self {
        Self {
            x: canonical_float_bits(x),
            y: canonical_float_bits(y),
            z: canonical_float_bits(z),
        }
    }
}

#[derive(Clone)]
struct GfniGroup {
    section_pos: i64,
    base_distance: f64,
    range_start: f64,
    range_end: f64,
    rel_distance_hash: i64,
    relative_distances: Vec<f32>,
}

struct NormalList {
    normal: [f32; 3],
    groups_by_section: HashMap<i64, GfniGroup>,
    sorted_by_start: Vec<i64>,
    sorted_by_end: Vec<i64>,
    dirty: bool,
}

struct NativeGfniTriggers {
    normal_lists: HashMap<NormalKey, NormalList>,
    section_normals: HashMap<i64, Vec<NormalKey>>,
    group_count: usize,
}

impl NativeGfniTriggers {
    fn new() -> Self {
        Self {
            normal_lists: HashMap::new(),
            section_normals: HashMap::new(),
            group_count: 0,
        }
    }

    fn normal_count(&self) -> usize {
        self.normal_lists.len()
    }

    fn group_count(&self) -> usize {
        self.group_count
    }

    fn remove_section(&mut self, section_pos: i64) {
        let Some(normal_keys) = self.section_normals.remove(&section_pos) else {
            return;
        };

        for key in normal_keys {
            let mut remove_normal = false;
            if let Some(normal_list) = self.normal_lists.get_mut(&key) {
                if normal_list.groups_by_section.remove(&section_pos).is_some() {
                    self.group_count -= 1;
                    normal_list.dirty = true;
                }
                remove_normal = normal_list.groups_by_section.is_empty();
            }

            if remove_normal {
                self.normal_lists.remove(&key);
            }
        }
    }

    fn integrate_section(&mut self, section_pos: i64, groups: Vec<PendingGfniGroup>) {
        self.remove_section(section_pos);

        if groups.is_empty() {
            return;
        }

        let mut section_normals = Vec::with_capacity(groups.len());
        for pending in groups {
            let key = NormalKey::new(pending.normal[0], pending.normal[1], pending.normal[2]);
            let normal_list = self.normal_lists.entry(key).or_insert_with(|| NormalList {
                normal: pending.normal,
                groups_by_section: HashMap::new(),
                sorted_by_start: Vec::new(),
                sorted_by_end: Vec::new(),
                dirty: true,
            });

            if normal_list
                .groups_by_section
                .insert(section_pos, pending.into_group(section_pos))
                .is_none()
            {
                self.group_count += 1;
            }
            normal_list.dirty = true;
            section_normals.push(key);
        }

        self.section_normals.insert(section_pos, section_normals);
    }

    fn process_triggers(&mut self, start: [f64; 3], end: [f64; 3]) -> TriggerResult {
        let mut sections = Vec::new();
        let mut normals = HashSet::new();

        for (key, normal_list) in self.normal_lists.iter_mut() {
            normal_list.process_movement(start, end, &mut sections, &mut normals, *key);
        }

        TriggerResult {
            sections,
            unique_normal_count: normals.len(),
        }
    }

    fn process_catchup(&mut self, section_pos: i64, start: [f64; 3], end: [f64; 3]) -> Vec<i64> {
        let Some(normal_keys) = self.section_normals.get(&section_pos).cloned() else {
            return Vec::new();
        };

        let mut sections = Vec::new();
        for key in normal_keys {
            if let Some(normal_list) = self.normal_lists.get_mut(&key) {
                normal_list.process_catchup(section_pos, start, end, &mut sections);
            }
        }
        sections
    }
}

impl NormalList {
    fn process_movement(
        &mut self,
        movement_start: [f64; 3],
        movement_end: [f64; 3],
        output_sections: &mut Vec<i64>,
        output_normals: &mut HashSet<NormalKey>,
        normal_key: NormalKey,
    ) {
        let start = float_double_dot(self.normal, movement_start);
        let end = float_double_dot(self.normal, movement_end);
        if start >= end {
            return;
        }

        self.rebuild_indexes_if_needed();
        let start_candidates =
            upper_bound_start(&self.sorted_by_start, &self.groups_by_section, end);
        let end_candidates = self.sorted_by_end.len()
            - upper_bound_end(&self.sorted_by_end, &self.groups_by_section, start);

        if start_candidates <= end_candidates {
            for section_pos in self.sorted_by_start.iter().take(start_candidates) {
                let group = &self.groups_by_section[section_pos];
                self.trigger_group_if_needed(
                    group,
                    start,
                    end,
                    output_sections,
                    output_normals,
                    normal_key,
                );
            }
        } else {
            let start_index = upper_bound_end(&self.sorted_by_end, &self.groups_by_section, start);
            for section_pos in self.sorted_by_end.iter().skip(start_index) {
                let group = &self.groups_by_section[section_pos];
                self.trigger_group_if_needed(
                    group,
                    start,
                    end,
                    output_sections,
                    output_normals,
                    normal_key,
                );
            }
        }
    }

    fn process_catchup(
        &mut self,
        section_pos: i64,
        movement_start: [f64; 3],
        movement_end: [f64; 3],
        output_sections: &mut Vec<i64>,
    ) {
        let start = float_double_dot(self.normal, movement_start);
        let end = float_double_dot(self.normal, movement_end);
        if start >= end {
            return;
        }

        if let Some(group) = self.groups_by_section.get(&section_pos) {
            if group.plane_triggered(start, end) {
                output_sections.push(group.section_pos);
            }
        }
    }

    fn trigger_group_if_needed(
        &self,
        group: &GfniGroup,
        start: f64,
        end: f64,
        output_sections: &mut Vec<i64>,
        output_normals: &mut HashSet<NormalKey>,
        normal_key: NormalKey,
    ) {
        if group.plane_triggered(start, end) {
            output_sections.push(group.section_pos);
            output_normals.insert(normal_key);
        }
    }

    fn rebuild_indexes_if_needed(&mut self) {
        if !self.dirty {
            return;
        }

        self.sorted_by_start = self.groups_by_section.keys().copied().collect();
        self.sorted_by_start.sort_unstable_by(|left, right| {
            compare_groups_by_start(
                &self.groups_by_section[left],
                &self.groups_by_section[right],
            )
        });

        self.sorted_by_end = self.groups_by_section.keys().copied().collect();
        self.sorted_by_end.sort_unstable_by(|left, right| {
            compare_groups_by_end(
                &self.groups_by_section[left],
                &self.groups_by_section[right],
            )
        });

        self.dirty = false;
    }
}

impl GfniGroup {
    fn plane_triggered(&self, start: f64, end: f64) -> bool {
        start < self.range_end
            && end > self.range_start
            && query_range(
                &self.relative_distances,
                (start - self.base_distance) as f32,
                (end - self.base_distance) as f32,
            )
    }
}

struct PendingGfniGroup {
    normal: [f32; 3],
    base_distance: f64,
    range_start: f64,
    range_end: f64,
    rel_distance_hash: i64,
    relative_distances: Vec<f32>,
}

impl PendingGfniGroup {
    fn into_group(self, section_pos: i64) -> GfniGroup {
        GfniGroup {
            section_pos,
            base_distance: self.base_distance,
            range_start: self.range_start,
            range_end: self.range_end,
            rel_distance_hash: self.rel_distance_hash,
            relative_distances: self.relative_distances,
        }
    }
}

struct TriggerResult {
    sections: Vec<i64>,
    unique_normal_count: usize,
}

fn compare_groups_by_start(left: &GfniGroup, right: &GfniGroup) -> Ordering {
    OrderedF64(left.range_start)
        .cmp(&OrderedF64(right.range_start))
        .then_with(|| OrderedF64(left.range_end).cmp(&OrderedF64(right.range_end)))
        .then_with(|| left.section_pos.cmp(&right.section_pos))
        .then_with(|| left.rel_distance_hash.cmp(&right.rel_distance_hash))
}

fn compare_groups_by_end(left: &GfniGroup, right: &GfniGroup) -> Ordering {
    OrderedF64(left.range_end)
        .cmp(&OrderedF64(right.range_end))
        .then_with(|| OrderedF64(left.range_start).cmp(&OrderedF64(right.range_start)))
        .then_with(|| left.section_pos.cmp(&right.section_pos))
        .then_with(|| left.rel_distance_hash.cmp(&right.rel_distance_hash))
}

fn upper_bound_start(sections: &[i64], groups: &HashMap<i64, GfniGroup>, query_end: f64) -> usize {
    sections.partition_point(|section_pos| groups[section_pos].range_start < query_end)
}

fn upper_bound_end(sections: &[i64], groups: &HashMap<i64, GfniGroup>, query_start: f64) -> usize {
    sections.partition_point(|section_pos| groups[section_pos].range_end <= query_start)
}

fn query_range(sorted_distances: &[f32], start: f32, end: f32) -> bool {
    match sorted_distances.binary_search_by(|value| value.total_cmp(&start)) {
        Ok(_) => true,
        Err(insertion_point) => {
            insertion_point < sorted_distances.len() && sorted_distances[insertion_point] <= end
        }
    }
}

fn canonical_float_bits(value: f32) -> u32 {
    if value == 0.0 {
        0.0f32.to_bits()
    } else {
        value.to_bits()
    }
}

fn float_double_dot(normal: [f32; 3], value: [f64; 3]) -> f64 {
    (normal[0] as f64).mul_add(
        value[0],
        (normal[1] as f64).mul_add(value[1], normal[2] as f64 * value[2]),
    )
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

fn read_pending_groups(
    normal_components: *const f32,
    base_distances: *const f64,
    ranges: *const f64,
    hashes: *const i64,
    distance_offsets: *const i32,
    distance_counts: *const i32,
    distances: *const f32,
    group_count: i32,
    distance_count: i32,
) -> Result<Vec<PendingGfniGroup>, i32> {
    if group_count < 0 || distance_count < 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if group_count == 0 {
        return Ok(Vec::new());
    }
    if normal_components.is_null()
        || base_distances.is_null()
        || ranges.is_null()
        || hashes.is_null()
        || distance_offsets.is_null()
        || distance_counts.is_null()
        || distances.is_null()
    {
        return Err(ERR_NULL_POINTER);
    }

    let group_count = group_count as usize;
    let distance_count = distance_count as usize;
    let normal_components = unsafe { slice::from_raw_parts(normal_components, group_count * 3) };
    let base_distances = unsafe { slice::from_raw_parts(base_distances, group_count) };
    let ranges = unsafe { slice::from_raw_parts(ranges, group_count * 2) };
    let hashes = unsafe { slice::from_raw_parts(hashes, group_count) };
    let distance_offsets = unsafe { slice::from_raw_parts(distance_offsets, group_count) };
    let distance_counts = unsafe { slice::from_raw_parts(distance_counts, group_count) };
    let distances = unsafe { slice::from_raw_parts(distances, distance_count) };

    let mut groups = Vec::with_capacity(group_count);
    for index in 0..group_count {
        let offset = distance_offsets[index];
        let count = distance_counts[index];
        if offset < 0 || count <= 0 {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let offset = offset as usize;
        let count = count as usize;
        let end = offset.checked_add(count).ok_or(ERR_INVALID_ARGUMENT)?;
        if end > distances.len() {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let normal = [
            normal_components[index * 3],
            normal_components[index * 3 + 1],
            normal_components[index * 3 + 2],
        ];
        if normal.iter().any(|value| !value.is_finite())
            || !base_distances[index].is_finite()
            || !ranges[index * 2].is_finite()
            || !ranges[index * 2 + 1].is_finite()
            || distances[offset..end]
                .iter()
                .any(|value| !value.is_finite())
        {
            return Err(ERR_INVALID_ARGUMENT);
        }

        groups.push(PendingGfniGroup {
            normal,
            base_distance: base_distances[index],
            range_start: ranges[index * 2],
            range_end: ranges[index * 2 + 1],
            rel_distance_hash: hashes[index],
            relative_distances: distances[offset..end].to_vec(),
        });
    }

    Ok(groups)
}

fn handle_mut<'a>(handle: u64) -> Result<&'a mut NativeGfniTriggers, i32> {
    if handle == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(unsafe { &mut *(handle as *mut NativeGfniTriggers) })
}

fn write_trigger_output(
    result: TriggerResult,
    output_sections: *mut i64,
    output_capacity: i32,
    output_state: *mut i32,
    output_state_len: i32,
) -> i32 {
    if output_capacity < 0 || output_state_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_state.is_null() || (output_capacity > 0 && output_sections.is_null()) {
        return ERR_NULL_POINTER;
    }
    if result.sections.len() > output_capacity as usize || result.sections.len() > i32::MAX as usize
    {
        return ERR_CAPACITY;
    }
    if result.unique_normal_count > i32::MAX as usize {
        return ERR_INVALID_ARGUMENT;
    }

    unsafe {
        if !result.sections.is_empty() {
            let output = slice::from_raw_parts_mut(output_sections, output_capacity as usize);
            output[..result.sections.len()].copy_from_slice(&result.sections);
        }

        let output_state = slice::from_raw_parts_mut(output_state, output_state_len as usize);
        output_state[0] = result.sections.len() as i32;
        output_state[1] = result.unique_normal_count as i32;
    }

    OK
}

fn write_count_output(
    triggers: &NativeGfniTriggers,
    output_counts: *mut i32,
    output_count_len: i32,
) -> i32 {
    if output_count_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if triggers.normal_count() > i32::MAX as usize || triggers.group_count() > i32::MAX as usize {
        return ERR_INVALID_ARGUMENT;
    }

    unsafe {
        let output_counts = slice::from_raw_parts_mut(output_counts, output_count_len as usize);
        output_counts[0] = triggers.normal_count() as i32;
        output_counts[1] = triggers.group_count() as i32;
    }

    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_gfni_triggers_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeGfniTriggers::new())) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_gfni_triggers_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    drop(Box::from_raw(handle as *mut NativeGfniTriggers));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_gfni_triggers_counts(
    handle: u64,
    output_counts: *mut i32,
    output_count_len: i32,
) -> i32 {
    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    write_count_output(triggers, output_counts, output_count_len)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_gfni_triggers_remove(handle: u64, section_pos: i64) -> i32 {
    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    triggers.remove_section(section_pos);
    OK
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn mattmc_sodium_gfni_triggers_integrate(
    handle: u64,
    section_pos: i64,
    normal_components: *const f32,
    base_distances: *const f64,
    ranges: *const f64,
    hashes: *const i64,
    distance_offsets: *const i32,
    distance_counts: *const i32,
    distances: *const f32,
    group_count: i32,
    distance_count: i32,
) -> i32 {
    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let groups = match read_pending_groups(
        normal_components,
        base_distances,
        ranges,
        hashes,
        distance_offsets,
        distance_counts,
        distances,
        group_count,
        distance_count,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    triggers.integrate_section(section_pos, groups);
    OK
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn mattmc_sodium_gfni_triggers_process(
    handle: u64,
    start_x: f64,
    start_y: f64,
    start_z: f64,
    end_x: f64,
    end_y: f64,
    end_z: f64,
    output_sections: *mut i64,
    output_capacity: i32,
    output_state: *mut i32,
    output_state_len: i32,
) -> i32 {
    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let (start, end) = match validate_movement([start_x, start_y, start_z, end_x, end_y, end_z]) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let result = triggers.process_triggers(start, end);
    write_trigger_output(
        result,
        output_sections,
        output_capacity,
        output_state,
        output_state_len,
    )
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn mattmc_sodium_gfni_triggers_catchup(
    handle: u64,
    section_pos: i64,
    start_x: f64,
    start_y: f64,
    start_z: f64,
    end_x: f64,
    end_y: f64,
    end_z: f64,
    output_sections: *mut i64,
    output_capacity: i32,
    output_state: *mut i32,
    output_state_len: i32,
) -> i32 {
    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let (start, end) = match validate_movement([start_x, start_y, start_z, end_x, end_y, end_z]) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let sections = triggers.process_catchup(section_pos, start, end);
    write_trigger_output(
        TriggerResult {
            sections,
            unique_normal_count: 0,
        },
        output_sections,
        output_capacity,
        output_state,
        output_state_len,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn movement_crossing_plane_triggers_section() {
        let mut triggers = NativeGfniTriggers::new();
        triggers.integrate_section(
            42,
            vec![PendingGfniGroup {
                normal: [1.0, 0.0, 0.0],
                base_distance: 0.0,
                range_start: 4.0,
                range_end: 4.0,
                rel_distance_hash: 4.0f64.to_bits() as i64,
                relative_distances: vec![4.0],
            }],
        );

        let result = triggers.process_triggers([0.0, 0.0, 0.0], [5.0, 0.0, 0.0]);
        assert_eq!(result.sections, vec![42]);
        assert_eq!(result.unique_normal_count, 1);
    }

    #[test]
    fn reverse_movement_does_not_trigger() {
        let mut triggers = NativeGfniTriggers::new();
        triggers.integrate_section(
            42,
            vec![PendingGfniGroup {
                normal: [1.0, 0.0, 0.0],
                base_distance: 0.0,
                range_start: 4.0,
                range_end: 4.0,
                rel_distance_hash: 4.0f64.to_bits() as i64,
                relative_distances: vec![4.0],
            }],
        );

        assert!(triggers
            .process_triggers([5.0, 0.0, 0.0], [0.0, 0.0, 0.0])
            .sections
            .is_empty());
    }

    #[test]
    fn removing_section_removes_all_normal_groups() {
        let mut triggers = NativeGfniTriggers::new();
        triggers.integrate_section(
            42,
            vec![
                PendingGfniGroup {
                    normal: [1.0, 0.0, 0.0],
                    base_distance: 0.0,
                    range_start: 4.0,
                    range_end: 4.0,
                    rel_distance_hash: 1,
                    relative_distances: vec![4.0],
                },
                PendingGfniGroup {
                    normal: [0.0, 1.0, 0.0],
                    base_distance: 0.0,
                    range_start: 2.0,
                    range_end: 2.0,
                    rel_distance_hash: 2,
                    relative_distances: vec![2.0],
                },
            ],
        );

        triggers.remove_section(42);
        assert_eq!(triggers.normal_count(), 0);
        assert_eq!(triggers.group_count(), 0);
    }

    #[test]
    fn catchup_checks_only_integrated_section() {
        let mut triggers = NativeGfniTriggers::new();
        triggers.integrate_section(
            42,
            vec![PendingGfniGroup {
                normal: [1.0, 0.0, 0.0],
                base_distance: 0.0,
                range_start: 4.0,
                range_end: 4.0,
                rel_distance_hash: 1,
                relative_distances: vec![4.0],
            }],
        );
        triggers.integrate_section(
            43,
            vec![PendingGfniGroup {
                normal: [1.0, 0.0, 0.0],
                base_distance: 0.0,
                range_start: 4.0,
                range_end: 4.0,
                rel_distance_hash: 1,
                relative_distances: vec![4.0],
            }],
        );

        assert_eq!(
            triggers.process_catchup(42, [0.0, 0.0, 0.0], [5.0, 0.0, 0.0]),
            vec![42]
        );
    }
}
