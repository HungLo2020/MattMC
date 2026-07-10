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

#[derive(Clone, Copy)]
struct RawPlane {
    normal: [f32; 3],
    distance: f32,
}

pub(crate) struct NativeGeometryPlanes {
    planes: Vec<RawPlane>,
}

impl NativeGeometryPlanes {
    pub(crate) fn new() -> Self {
        Self { planes: Vec::new() }
    }

    fn plane_count(&self) -> usize {
        self.planes.len()
    }

    pub(crate) fn add_aligned_plane(&mut self, direction: i32, distance: f32) -> Result<(), i32> {
        if !distance.is_finite() {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let normal = aligned_normal(direction)?;
        self.planes.push(RawPlane { normal, distance });
        Ok(())
    }

    fn add_double_sided_aligned_plane(&mut self, axis: i32, distance: f32) -> Result<(), i32> {
        if !(0..3).contains(&axis) || !distance.is_finite() {
            return Err(ERR_INVALID_ARGUMENT);
        }

        self.add_aligned_plane(axis, distance)?;
        self.add_aligned_plane(axis + 3, -distance)
    }

    pub(crate) fn add_unaligned_plane(
        &mut self,
        normal: [f32; 3],
        distance: f32,
    ) -> Result<(), i32> {
        if normal.iter().any(|value| !value.is_finite()) || !distance.is_finite() {
            return Err(ERR_INVALID_ARGUMENT);
        }

        self.planes.push(RawPlane {
            normal: clean_normal(normal),
            distance,
        });
        Ok(())
    }

    fn add_double_sided_unaligned_plane(
        &mut self,
        normal: [f32; 3],
        distance: f32,
    ) -> Result<(), i32> {
        self.add_unaligned_plane(normal, distance)?;
        self.add_unaligned_plane([-normal[0], -normal[1], -normal[2]], -distance)
    }
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

struct PendingGroupAccumulator {
    normal: [f32; 3],
    distances: Vec<f32>,
}

impl PendingGroupAccumulator {
    fn into_group(mut self, section_min: [f64; 3]) -> PendingGfniGroup {
        self.distances
            .sort_unstable_by(|left, right| left.total_cmp(right));
        self.distances
            .dedup_by(|left, right| left.to_bits() == right.to_bits());

        let base_distance = float_double_dot(self.normal, section_min);
        let range_start = self.distances[0] as f64 + base_distance;
        let range_end = self.distances[self.distances.len() - 1] as f64 + base_distance;
        let rel_distance_hash = relative_distance_hash(&self.distances);

        PendingGfniGroup {
            normal: self.normal,
            base_distance,
            range_start,
            range_end,
            rel_distance_hash,
            relative_distances: self.distances,
        }
    }
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

fn clean_normal(normal: [f32; 3]) -> [f32; 3] {
    [
        if normal[0] == 0.0 { 0.0 } else { normal[0] },
        if normal[1] == 0.0 { 0.0 } else { normal[1] },
        if normal[2] == 0.0 { 0.0 } else { normal[2] },
    ]
}

fn aligned_normal(direction: i32) -> Result<[f32; 3], i32> {
    match direction {
        0 => Ok([1.0, 0.0, 0.0]),
        1 => Ok([0.0, 1.0, 0.0]),
        2 => Ok([0.0, 0.0, 1.0]),
        3 => Ok([-1.0, 0.0, 0.0]),
        4 => Ok([0.0, -1.0, 0.0]),
        5 => Ok([0.0, 0.0, -1.0]),
        _ => Err(ERR_INVALID_ARGUMENT),
    }
}

fn float_double_dot(normal: [f32; 3], value: [f64; 3]) -> f64 {
    (normal[0] as f64).mul_add(
        value[0],
        (normal[1] as f64).mul_add(value[1], normal[2] as f64 * value[2]),
    )
}

fn relative_distance_hash(distances: &[f32]) -> i64 {
    let mut hash = 0i64;
    for &distance in distances {
        let distance_bits = (distance as f64).to_bits() as i64;
        hash ^= hash.wrapping_mul(31).wrapping_add(distance_bits);
    }
    hash
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

fn build_pending_groups(
    collector: &NativeGeometryPlanes,
    section_min: [f64; 3],
) -> Result<Vec<PendingGfniGroup>, i32> {
    if collector.planes.is_empty() {
        return Ok(Vec::new());
    }

    let mut accumulators: HashMap<NormalKey, PendingGroupAccumulator> = HashMap::new();

    for plane in &collector.planes {
        if plane.normal.iter().any(|value| !value.is_finite()) || !plane.distance.is_finite() {
            return Err(ERR_INVALID_ARGUMENT);
        }

        let key = NormalKey::new(plane.normal[0], plane.normal[1], plane.normal[2]);
        accumulators
            .entry(key)
            .or_insert_with(|| PendingGroupAccumulator {
                normal: plane.normal,
                distances: Vec::new(),
            })
            .distances
            .push(plane.distance);
    }

    let mut groups = Vec::with_capacity(accumulators.len());
    for accumulator in accumulators.into_values() {
        groups.push(accumulator.into_group(section_min));
    }

    Ok(groups)
}

fn handle_mut<'a>(handle: u64) -> Result<&'a mut NativeGfniTriggers, i32> {
    if handle == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(unsafe { &mut *(handle as *mut NativeGfniTriggers) })
}

fn geometry_planes_ref<'a>(handle: u64) -> Result<&'a NativeGeometryPlanes, i32> {
    if handle == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(unsafe { &*(handle as *const NativeGeometryPlanes) })
}

fn geometry_planes_mut<'a>(handle: u64) -> Result<&'a mut NativeGeometryPlanes, i32> {
    if handle == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(unsafe { &mut *(handle as *mut NativeGeometryPlanes) })
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
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeGeometryPlanes::new())) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    drop(Box::from_raw(handle as *mut NativeGeometryPlanes));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_count(
    handle: u64,
    output_count: *mut i32,
) -> i32 {
    if output_count.is_null() {
        return ERR_NULL_POINTER;
    }
    let collector = match geometry_planes_ref(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    if collector.plane_count() > i32::MAX as usize {
        return ERR_INVALID_ARGUMENT;
    }

    *output_count = collector.plane_count() as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_add_aligned(
    handle: u64,
    direction: i32,
    distance: f32,
) -> i32 {
    let collector = match geometry_planes_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };

    match collector.add_aligned_plane(direction, distance) {
        Ok(()) => OK,
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_add_double_sided_aligned(
    handle: u64,
    axis: i32,
    distance: f32,
) -> i32 {
    let collector = match geometry_planes_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };

    match collector.add_double_sided_aligned_plane(axis, distance) {
        Ok(()) => OK,
        Err(status) => status,
    }
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_add_unaligned(
    handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
) -> i32 {
    let collector = match geometry_planes_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };

    match collector.add_unaligned_plane([normal_x, normal_y, normal_z], distance) {
        Ok(()) => OK,
        Err(status) => status,
    }
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C" fn mattmc_sodium_geometry_planes_add_double_sided_unaligned(
    handle: u64,
    normal_x: f32,
    normal_y: f32,
    normal_z: f32,
    distance: f32,
) -> i32 {
    let collector = match geometry_planes_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };

    match collector.add_double_sided_unaligned_plane([normal_x, normal_y, normal_z], distance) {
        Ok(()) => OK,
        Err(status) => status,
    }
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
    section_min_x: i32,
    section_min_y: i32,
    section_min_z: i32,
    geometry_planes_handle: u64,
) -> i32 {
    let triggers = match handle_mut(handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let collector = match geometry_planes_ref(geometry_planes_handle) {
        Ok(value) => value,
        Err(status) => return status,
    };
    let groups = match build_pending_groups(
        collector,
        [
            section_min_x as f64,
            section_min_y as f64,
            section_min_z as f64,
        ],
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

    #[test]
    fn raw_plane_records_are_grouped_and_prepared_like_normal_planes() {
        let mut collector = NativeGeometryPlanes::new();
        collector.add_aligned_plane(0, 4.0).unwrap();
        collector.add_aligned_plane(0, 2.0).unwrap();
        collector.add_aligned_plane(0, 4.0).unwrap();
        collector.add_aligned_plane(1, 3.0).unwrap();

        let groups = build_pending_groups(&collector, [16.0, 32.0, 48.0]).unwrap();

        assert_eq!(groups.len(), 2);
        let x_group = groups
            .iter()
            .find(|group| group.normal == [1.0, 0.0, 0.0])
            .unwrap();
        assert_eq!(x_group.base_distance, 16.0);
        assert_eq!(x_group.range_start, 18.0);
        assert_eq!(x_group.range_end, 20.0);
        assert_eq!(x_group.relative_distances, vec![2.0, 4.0]);

        let y_group = groups
            .iter()
            .find(|group| group.normal == [0.0, 1.0, 0.0])
            .unwrap();
        assert_eq!(y_group.base_distance, 32.0);
        assert_eq!(y_group.range_start, 35.0);
        assert_eq!(y_group.range_end, 35.0);
        assert_eq!(y_group.relative_distances, vec![3.0]);
    }

    #[test]
    fn raw_plane_reader_rejects_invalid_records() {
        let mut collector = NativeGeometryPlanes::new();

        assert!(matches!(
            collector.add_unaligned_plane([1.0, 0.0, f32::NAN], 4.0),
            Err(ERR_INVALID_ARGUMENT)
        ));
    }

    #[test]
    fn geometry_plane_collector_cleans_zeroes_and_adds_double_sided_planes() {
        let mut collector = NativeGeometryPlanes::new();
        collector
            .add_double_sided_unaligned_plane([-0.0, 1.0, 0.0], 7.0)
            .unwrap();

        assert_eq!(collector.plane_count(), 2);
        assert_eq!(collector.planes[0].normal, [0.0, 1.0, 0.0]);
        assert_eq!(collector.planes[0].distance, 7.0);
        assert_eq!(collector.planes[1].normal, [0.0, -1.0, 0.0]);
        assert_eq!(collector.planes[1].distance, -7.0);
    }
}
