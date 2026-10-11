//! Primary position/bounds components. Java objects are compatibility projections;
//! rendering queries the retained components without a per-frame bounds array.
use std::sync::{
    atomic::{AtomicI32, AtomicU64, Ordering},
    Arc, Mutex,
};

#[repr(C)]
pub(crate) struct View {
    position: [AtomicU64; 3],
    bounds: [AtomicU64; 6],
    culling: [AtomicU64; 6],
    block: [AtomicI32; 3],
    chunk: [AtomicI32; 2],
    visible: AtomicI32,
    revision: AtomicU64,
}
impl View {
    fn new(position: [f64; 3], bounds: [f64; 6]) -> Self {
        let block = position.map(floor);
        let culling = inflated(bounds, position);
        Self {
            position: position.map(|v| AtomicU64::new(v.to_bits())),
            bounds: bounds.map(|v| AtomicU64::new(v.to_bits())),
            culling: culling.map(|v| AtomicU64::new(v.to_bits())),
            block: block.map(AtomicI32::new),
            chunk: [block[0] >> 4, block[2] >> 4].map(AtomicI32::new),
            visible: AtomicI32::new(0),
            revision: AtomicU64::new(0),
        }
    }
    fn position(&self) -> [f64; 3] {
        self.position
            .each_ref()
            .map(|v| f64::from_bits(v.load(Ordering::Relaxed)))
    }
    fn bounds(&self) -> [f64; 6] {
        self.bounds
            .each_ref()
            .map(|v| f64::from_bits(v.load(Ordering::Relaxed)))
    }
    fn project_culling(&self) {
        let mut changed = false;
        for (target, value) in self
            .culling
            .iter()
            .zip(inflated(self.bounds(), self.position()))
        {
            changed |= target.load(Ordering::Relaxed) != value.to_bits();
            target.store(value.to_bits(), Ordering::Release);
        }
        if changed {
            self.revision.fetch_add(1, Ordering::Release);
        }
    }
    /// Java's !=, saturating double->int conversion and wrapping floor edge cases.
    fn move_to(&self, position: [f64; 3]) -> i32 {
        if self.position().iter().zip(position).all(|(&a, b)| a == b) {
            return 0;
        }
        for (target, value) in self.position.iter().zip(position) {
            target.store(value.to_bits(), Ordering::Release);
        }
        let block = position.map(floor);
        let mut flags = 1;
        if self
            .block
            .iter()
            .zip(block)
            .any(|(a, b)| a.load(Ordering::Relaxed) != b)
        {
            flags |= 2;
            for (target, value) in self.block.iter().zip(block) {
                target.store(value, Ordering::Release);
            }
            let chunk = [block[0] >> 4, block[2] >> 4];
            if self
                .chunk
                .iter()
                .zip(chunk)
                .any(|(a, b)| a.load(Ordering::Relaxed) != b)
            {
                flags |= 4;
                for (target, value) in self.chunk.iter().zip(chunk) {
                    target.store(value, Ordering::Release);
                }
            }
        }
        self.project_culling();
        flags
    }
}
fn floor(v: f64) -> i32 {
    let i = v as i32;
    if v < i as f64 {
        i.wrapping_sub(1)
    } else {
        i
    }
}
fn minimum(a: f64, b: f64) -> f64 {
    if a.is_nan() || b.is_nan() {
        f64::NAN
    } else if a == 0.0 && b == 0.0 {
        f64::from_bits(a.to_bits() | b.to_bits())
    } else {
        a.min(b)
    }
}
fn maximum(a: f64, b: f64) -> f64 {
    if a.is_nan() || b.is_nan() {
        f64::NAN
    } else if a == 0.0 && b == 0.0 {
        f64::from_bits(a.to_bits() & b.to_bits())
    } else {
        a.max(b)
    }
}
fn normalized(mut v: [f64; 6]) -> [f64; 6] {
    for i in 0..3 {
        let a = v[i];
        let b = v[i + 3];
        v[i] = minimum(a, b);
        v[i + 3] = maximum(a, b);
    }
    v
}
fn inflated(bounds: [f64; 6], position: [f64; 3]) -> [f64; 6] {
    let mut v = bounds;
    for i in 0..3 {
        v[i] -= 0.5;
        v[i + 3] += 0.5;
    }
    v = normalized(v);
    let size = ((v[3] - v[0]) + (v[4] - v[1]) + (v[5] - v[2])) / 3.0;
    if v.iter().any(|v| v.is_nan()) || size == 0.0 {
        v = normalized([
            position[0] - 2.0,
            position[1] - 2.0,
            position[2] - 2.0,
            position[0] + 2.0,
            position[1] + 2.0,
            position[2] + 2.0,
        ]);
    }
    v
}

struct Slot {
    generation: u32,
    dense: Option<usize>,
}
struct Entry {
    id: u64,
    view: Arc<View>,
}
struct Store {
    slots: Vec<Slot>,
    free: Vec<usize>,
    dense: Vec<Entry>,
    stamp: u64,
}
impl Store {
    fn new() -> Self {
        Self {
            slots: Vec::new(),
            free: Vec::new(),
            dense: Vec::new(),
            stamp: 0,
        }
    }
    fn insert(&mut self, view: Arc<View>) -> Option<u64> {
        let slot = if let Some(slot) = self.free.pop() {
            slot
        } else {
            if self.slots.len() >= i32::MAX as usize {
                return None;
            }
            self.slots.push(Slot {
                generation: 1,
                dense: None,
            });
            self.slots.len() - 1
        };
        let id = ((self.slots[slot].generation as u64) << 32) | slot as u64;
        self.slots[slot].dense = Some(self.dense.len());
        self.dense.push(Entry { id, view });
        Some(id)
    }
    fn index(&self, id: u64) -> Option<usize> {
        let slot = self.slots.get(id as u32 as usize)?;
        if slot.generation != (id >> 32) as u32 {
            return None;
        }
        slot.dense
    }
    fn remove(&mut self, id: u64) -> bool {
        let Some(index) = self.index(id) else {
            return false;
        };
        self.dense.swap_remove(index);
        if let Some(entry) = self.dense.get(index) {
            self.slots[entry.id as u32 as usize].dense = Some(index);
        }
        let number = id as u32 as usize;
        let slot = &mut self.slots[number];
        slot.dense = None;
        // Exhausted generations retire a slot rather than alias an old ID.
        if slot.generation != u32::MAX {
            slot.generation += 1;
            self.free.push(number);
        }
        true
    }
}
#[repr(C)]
struct Header {
    revision: AtomicU64,
    stamp: AtomicU64,
    count: AtomicI32,
    reserved: i32,
    query_revision: AtomicU64,
}
pub(crate) struct World {
    header: Header,
    store: Mutex<Store>,
}
impl World {
    fn publish(&self, store: &Store) {
        self.header
            .count
            .store(store.dense.len() as i32, Ordering::Release);
        self.header.revision.fetch_add(1, Ordering::Release);
    }
}
struct Binding {
    world: Arc<World>,
    id: u64,
    view: Arc<View>,
}
pub(crate) struct Body {
    binding: Mutex<Binding>,
}
impl Drop for Body {
    fn drop(&mut self) {
        let binding = self.binding.get_mut().unwrap_or_else(|e| e.into_inner());
        let mut store = binding
            .world
            .store
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        if store.remove(binding.id) {
            binding.world.publish(&store);
        }
    }
}

#[no_mangle]
pub extern "C" fn mattmc_entity_geometry_world_create() -> *const World {
    Arc::into_raw(Arc::new(World {
        header: Header {
            revision: AtomicU64::new(0),
            stamp: AtomicU64::new(0),
            count: AtomicI32::new(0),
            reserved: 0,
            query_revision: AtomicU64::new(0),
        },
        store: Mutex::new(Store::new()),
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_world_release(world: *const World) {
    if !world.is_null() {
        drop(Arc::from_raw(world));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_world_view(
    world: *const World,
) -> *const std::ffi::c_void {
    world
        .as_ref()
        .map_or(std::ptr::null(), |w| &w.header as *const Header as *const _)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_create(
    world: *const World,
    x: f64,
    y: f64,
    z: f64,
    a: f64,
    b: f64,
    c: f64,
    d: f64,
    e: f64,
    f: f64,
) -> *mut Body {
    let Some(w) = world.as_ref() else {
        return std::ptr::null_mut();
    };
    let view = Arc::new(View::new([x, y, z], [a, b, c, d, e, f]));
    let mut store = w.store.lock().unwrap_or_else(|e| e.into_inner());
    let Some(id) = store.insert(view.clone()) else {
        return std::ptr::null_mut();
    };
    w.publish(&store);
    Arc::increment_strong_count(world);
    Box::into_raw(Box::new(Body {
        binding: Mutex::new(Binding {
            world: Arc::from_raw(world),
            id,
            view,
        }),
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_release(body: *mut Body) {
    if !body.is_null() {
        drop(Box::from_raw(body));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_view(body: *const Body) -> *const View {
    let Some(body) = body.as_ref() else {
        return std::ptr::null();
    };
    let binding = body.binding.lock().unwrap_or_else(|e| e.into_inner());
    Arc::as_ptr(&binding.view)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_change(
    body: *const Body,
    mode: i32,
    a: f64,
    b: f64,
    c: f64,
    d: f64,
    e: f64,
    f: f64,
) -> i32 {
    let Some(body) = body.as_ref() else {
        return -1;
    };
    let binding = body.binding.lock().unwrap_or_else(|e| e.into_inner());
    let store = binding
        .world
        .store
        .lock()
        .unwrap_or_else(|e| e.into_inner());
    if store.index(binding.id).is_none() {
        return -1;
    }
    let flags = match mode {
        0 => binding.view.move_to([a, b, c]),
        1 => {
            for (target, value) in binding.view.bounds.iter().zip([a, b, c, d, e, f]) {
                target.store(value.to_bits(), Ordering::Release);
            }
            binding.view.project_culling();
            1
        }
        _ => return -1,
    };
    if flags != 0 {
        binding.world.publish(&store);
    }
    flags
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_transfer(
    body: *const Body,
    world: *const World,
) -> i32 {
    let (Some(body), Some(w)) = (body.as_ref(), world.as_ref()) else {
        return -1;
    };
    let mut binding = body.binding.lock().unwrap_or_else(|e| e.into_inner());
    if std::ptr::eq(Arc::as_ptr(&binding.world), world) {
        return 0;
    }
    let mut next = w.store.lock().unwrap_or_else(|e| e.into_inner());
    let Some(id) = next.insert(binding.view.clone()) else {
        return -1;
    };
    w.publish(&next);
    drop(next);
    {
        let mut old = binding
            .world
            .store
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        if !old.remove(binding.id) {
            drop(old);
            let mut rollback = w.store.lock().unwrap_or_else(|e| e.into_inner());
            rollback.remove(id);
            w.publish(&rollback);
            return -1;
        }
        binding.world.publish(&old);
    }
    Arc::increment_strong_count(world);
    binding.world = Arc::from_raw(world);
    binding.id = id;
    1
}

/// JOML 1.10.5 plane extraction and positive-corner test, with the same f32
/// arithmetic, normalization and f64 camera subtraction before narrowing.
fn planes(matrix: &[f32; 16]) -> [[f32; 4]; 6] {
    std::array::from_fn(|plane| {
        let row = plane / 2;
        let positive = plane % 2 == 0;
        let mut v = std::array::from_fn(|column| {
            if positive {
                matrix[column * 4 + 3] + matrix[column * 4 + row]
            } else {
                matrix[column * 4 + 3] - matrix[column * 4 + row]
            }
        });
        let length = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        let inv = 1.0 / length.sqrt();
        for component in &mut v {
            *component *= inv;
        }
        v
    })
}
fn visible(view: &View, planes: &[[f32; 4]; 6], camera: [f64; 3]) -> bool {
    let v: [f32; 6] = std::array::from_fn(|i| {
        (f64::from_bits(view.culling[i].load(Ordering::Relaxed)) - camera[i % 3]) as f32
    });
    planes.iter().all(|p| {
        let x = v[if p[0] < 0.0 { 0 } else { 3 }];
        let y = v[if p[1] < 0.0 { 1 } else { 4 }];
        let z = v[if p[2] < 0.0 { 2 } else { 5 }];
        p[0] * x + p[1] * y + p[2] * z >= -p[3]
    })
}
/// One bounded camera input; no Java entity array or callback under the lock.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_geometry_query(
    world: *const World,
    matrix: *const f32,
    length: i32,
    x: f64,
    y: f64,
    z: f64,
) -> u64 {
    let Some(w) = world.as_ref() else {
        return 0;
    };
    if matrix.is_null() || matrix as usize % 4 != 0 || length != 16 {
        return 0;
    }
    let plane = planes(&*matrix.cast::<[f32; 16]>());
    let mut store = w.store.lock().unwrap_or_else(|e| e.into_inner());
    if store.stamp == u64::MAX {
        return 0;
    }
    store.stamp += 1;
    for entry in &store.dense {
        entry.view.visible.store(
            i32::from(visible(&entry.view, &plane, [x, y, z])),
            Ordering::Release,
        );
    }
    w.header
        .query_revision
        .store(w.header.revision.load(Ordering::Relaxed), Ordering::Release);
    w.header.stamp.store(store.stamp, Ordering::Release);
    store.stamp
}

#[cfg(test)]
mod tests;
