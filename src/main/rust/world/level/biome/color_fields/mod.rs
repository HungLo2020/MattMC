//! Immutable section color inputs, shared by world coordinate rather than block.
//!
//! Built-in biome resolvers are pure over a captured world slice. Literal custom
//! provider rows are kept independently: their invocation order is observable.
use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};

const BLOCKS: usize = 4096;
const EDGE: usize = 19; // section coordinates -1..=17
const CELLS: usize = EDGE * EDGE * EDGE;
const ABSENT: u16 = u16::MAX;
const MAX_OWNERS: usize = 128;

pub(crate) struct SectionColorFields {
    pub(crate) origin: [i32; 3],
    kinds: Box<[u8]>,
    active: Box<[u16]>,
    indices: [Vec<u16>; 3],
    colors: Vec<i32>,
    literals: Vec<[i32; 64]>,
    literal_indices: Box<[u16]>,
}

impl SectionColorFields {
    pub(crate) fn sample(&self, block: usize, x: usize, y: usize, z: usize) -> i32 {
        match self.kinds[block] {
            0 => -1,
            4 => self.literals[self.literal_indices[block] as usize][(y * 4 + z) * 4 + x],
            kind => {
                let cell =
                    (((block >> 8) + y) * EDGE + ((block >> 4) & 15) + z) * EDGE + (block & 15) + x;
                self.colors[self.indices[kind as usize - 1][cell] as usize]
            }
        }
    }

    pub(crate) fn matches_active(&self, active: &[u16]) -> bool {
        &*self.active == active
    }
}

struct Pending {
    fields: SectionColorFields,
    queries: Vec<[i32; 4]>, // resolver kind, world x/y/z
}

enum Owner {
    Pending(Pending),
    Ready(Arc<SectionColorFields>),
}
#[derive(Default)]
struct Owners {
    next: u64,
    entries: HashMap<u64, Owner>,
}
fn owners() -> &'static Mutex<Owners> {
    static OWNERS: OnceLock<Mutex<Owners>> = OnceLock::new();
    OWNERS.get_or_init(|| Mutex::new(Owners::default()))
}

fn plan(
    origin: [i32; 3],
    active: &[u16],
    kinds: &[u8],
    literals: Option<&[i32]>,
) -> Result<Pending, i32> {
    if kinds.len() != BLOCKS || active.len() > BLOCKS {
        return Err(-2);
    }
    // Reject overflows before exposing any requests to the bridge.
    for coordinate in origin {
        if coordinate.checked_sub(1).is_none() || coordinate.checked_add(17).is_none() {
            return Err(-2);
        }
    }
    let mut fields = SectionColorFields {
        origin,
        kinds: vec![0; BLOCKS].into_boxed_slice(),
        active: active.into(),
        indices: std::array::from_fn(|_| Vec::new()),
        colors: Vec::new(),
        literals: Vec::new(),
        literal_indices: vec![ABSENT; BLOCKS].into_boxed_slice(),
    };
    let mut queries = Vec::new();
    let mut seen = [false; BLOCKS];
    for &block in active {
        let block = block as usize;
        if block >= BLOCKS || seen[block] || kinds[block] > 4 {
            return Err(-2);
        }
        seen[block] = true;
        let kind = kinds[block];
        fields.kinds[block] = kind;
        if kind == 0 {
            continue;
        }
        if kind == 4 {
            let rows = literals.ok_or(-1)?;
            let row = rows.get(block * 64..block * 64 + 64).ok_or(-2)?;
            fields.literal_indices[block] = fields.literals.len() as u16;
            fields.literals.push(row.try_into().unwrap());
            continue;
        }
        let indices = &mut fields.indices[kind as usize - 1];
        if indices.is_empty() {
            indices.resize(CELLS, ABSENT);
        }
        let bx = block & 15;
        let bz = (block >> 4) & 15;
        let by = block >> 8;
        for y in by..by + 4 {
            for z in bz..bz + 4 {
                for x in bx..bx + 4 {
                    let cell = (y * EDGE + z) * EDGE + x;
                    if indices[cell] == ABSENT {
                        indices[cell] = fields.colors.len() as u16;
                        fields.colors.push(0); // unfilled color; completion rejects it
                        queries.push([
                            kind as i32,
                            origin[0] + x as i32 - 1,
                            origin[1] + y as i32 - 1,
                            origin[2] + z as i32 - 1,
                        ]);
                    }
                }
            }
        }
    }
    Ok(Pending { fields, queries })
}

pub(crate) fn resolve(id: u64) -> Result<Arc<SectionColorFields>, i32> {
    match owners().lock().map_err(|_| -2)?.entries.get(&id) {
        Some(Owner::Ready(fields)) => Ok(Arc::clone(fields)),
        _ => Err(-2),
    }
}

/// Exclusive construction lease. Output is [identity, query address, color
/// address, query count]. Caller fills colors, finishes, and never writes again.
/// All pointed-to CPU input arrays must remain valid throughout this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_world_section_colors_plan(
    min_x: i32,
    min_y: i32,
    min_z: i32,
    active: *const u16,
    count: i32,
    kinds: *const u8,
    literals: *const i32,
    output: *mut u64,
) -> i32 {
    if active.is_null() || kinds.is_null() || output.is_null() {
        return -1;
    }
    if !(0..=BLOCKS as i32).contains(&count) {
        return -2;
    }
    let mut pool = match owners().lock() {
        Ok(pool) => pool,
        Err(_) => return -2,
    };
    if pool.entries.len() >= MAX_OWNERS {
        return -3;
    }
    let mut pending = match plan(
        [min_x, min_y, min_z],
        std::slice::from_raw_parts(active, count as usize),
        std::slice::from_raw_parts(kinds, BLOCKS),
        if literals.is_null() {
            None
        } else {
            Some(std::slice::from_raw_parts(literals, BLOCKS * 64))
        },
    ) {
        Ok(pending) => pending,
        Err(status) => return status,
    };
    let id = match pool.next.checked_add(1) {
        Some(id) if id <= i64::MAX as u64 => id,
        _ => return -3,
    };
    pool.next = id;
    let result = [
        id,
        pending.queries.as_ptr() as u64,
        pending.fields.colors.as_mut_ptr() as u64,
        pending.queries.len() as u64,
    ];
    pool.entries.insert(id, Owner::Pending(pending));
    std::ptr::copy_nonoverlapping(result.as_ptr(), output, 4);
    0
}

#[no_mangle]
pub extern "C" fn mattmc_world_section_colors_finish(id: u64) -> i32 {
    let mut pool = match owners().lock() {
        Ok(pool) => pool,
        Err(_) => return -2,
    };
    let Some(Owner::Pending(pending)) = pool.entries.get(&id) else {
        return -2;
    };
    if pending
        .fields
        .colors
        .iter()
        .any(|color| (*color as u32 >> 24) != 255)
    {
        return -2;
    }
    let Some(Owner::Pending(pending)) = pool.entries.remove(&id) else {
        return -2;
    };
    pool.entries
        .insert(id, Owner::Ready(Arc::new(pending.fields)));
    0
}

#[no_mangle]
pub extern "C" fn mattmc_world_section_colors_release(id: u64) -> i32 {
    match owners().lock() {
        Ok(mut pool) => {
            if pool.entries.remove(&id).is_some() {
                0
            } else {
                -2
            }
        }
        Err(_) => -2,
    }
}

#[cfg(test)]
mod tests;
