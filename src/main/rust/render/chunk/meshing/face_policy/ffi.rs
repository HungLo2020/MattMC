use super::{Facts, Policy, Shape, State, CELLS, MAX_SHAPES};
use crate::content::block::{self, BlockId, BlockRegistry, FluidKind, StateFlags, StateId};
use std::sync::OnceLock;

pub struct Owner {
    policy: Policy,
    blocks: &'static BlockRegistry,
    roots: Option<BlockId>,
    admission: Vec<u8>,
}
impl Owner {
    fn facts(&self, id: usize) -> Option<Facts> {
        if id >= self.blocks.state_count() {
            return None;
        }
        let id = StateId(id as u16);
        let flags = self.blocks.flags(id);
        if flags.contains(StateFlags::CUSTOM) {
            return None;
        }
        Some(Facts {
            block: self.blocks.block_of(id).0,
            can_occlude: flags.contains(StateFlags::CAN_OCCLUDE),
            fluid_family: match self.blocks.fluid(id) {
                FluidKind::None => Some(0),
                FluidKind::Water => Some(1),
                FluidKind::Lava => Some(2),
                FluidKind::Other => None,
            },
            mangrove_roots: Some(self.blocks.block_of(id)) == self.roots,
        })
    }
}

/// Imports immutable cached geometry and source implementation IDs once.
/// State rows: implementation, six face IDs, canonical-shape flag.
/// Shape rows: identity/empty flags, first box, box count. Each box is six f64s.
/// # Safety
/// Caller supplies aligned readable spans of the stated lengths. No input
/// pointer is retained. One immutable process-wide registry is installed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_face_policy_install(
    states: *const i32,
    state_len: i32,
    shapes: *const i32,
    shape_len: i32,
    boxes: *const f64,
    box_len: i32,
) -> i32 {
    let Some(blocks) = block::installed() else {
        return -1;
    };
    if states.is_null()
        || shapes.is_null()
        || states as usize % 4 != 0
        || shapes as usize % 4 != 0
        || state_len != blocks.state_count() as i32 * 8
        || shape_len <= 0
        || shape_len as usize > MAX_SHAPES * 3
        || shape_len % 3 != 0
        || !(0..=8192 * 6).contains(&box_len)
        || box_len % 6 != 0
        || (box_len > 0 && (boxes.is_null() || boxes as usize % 8 != 0))
    {
        return -1;
    }
    let state_rows = std::slice::from_raw_parts(states, state_len as usize);
    let shape_rows = std::slice::from_raw_parts(shapes, shape_len as usize);
    let box_rows = if box_len == 0 {
        &[][..]
    } else {
        std::slice::from_raw_parts(boxes, box_len as usize)
    };
    let mut retained_shapes = Vec::with_capacity(shape_rows.len() / 3);
    for row in shape_rows.chunks_exact(3) {
        if !(0..=3).contains(&row[0])
            || row[1] < 0
            || !(0..=256).contains(&row[2])
            || (row[1] as usize + row[2] as usize) > box_rows.len() / 6
        {
            return -1;
        }
        let start = row[1] as usize * 6;
        let end = start + row[2] as usize * 6;
        retained_shapes.push(Shape {
            full_identity: row[0] & 1 != 0,
            empty: row[0] & 2 != 0,
            boxes: box_rows[start..end]
                .chunks_exact(6)
                .map(|b| b.try_into().unwrap())
                .collect(),
        });
    }
    let mut retained_states = Vec::with_capacity(blocks.state_count());
    for row in state_rows.chunks_exact(8) {
        if !(0..=7).contains(&row[0])
            || !(0..=1).contains(&row[7])
            || row[1..7]
                .iter()
                .any(|&id| id < 0 || id as usize >= retained_shapes.len())
        {
            return -1;
        }
        retained_states.push(State {
            implementation: row[0] as u8,
            canonical_shape: row[7] != 0
                && !blocks
                    .flags(StateId(retained_states.len() as u16))
                    .contains(StateFlags::CUSTOM),
            faces: std::array::from_fn(|d| row[d + 1] as u16),
        });
    }
    let Ok(policy) = Policy::new(retained_states, retained_shapes) else {
        return -1;
    };
    let mut owner = Owner {
        policy,
        blocks,
        roots: blocks.by_name("minecraft:mangrove_roots"),
        admission: Vec::new(),
    };
    owner.admission = (0..owner.policy.states.len())
        .map(|id| u8::from(owner.policy.native_state(id, &|id| owner.facts(id))))
        .collect();
    match INSTALLED.set(owner) {
        Ok(()) => 1,
        Err(owner) => {
            if INSTALLED
                .get()
                .is_some_and(|old| old.policy == owner.policy)
            {
                1
            } else {
                -2
            }
        }
    }
}

static INSTALLED: OnceLock<Owner> = OnceLock::new();

/// Immutable CPU admission metadata; valid for the process lifetime.
/// # Safety
/// Count is an aligned writable i32 pointer.
#[no_mangle]
pub unsafe extern "C" fn mattmc_face_policy_admission(count: *mut i32) -> *const u8 {
    if count.is_null() || count as usize % 4 != 0 {
        return std::ptr::null();
    }
    let Some(owner) = INSTALLED.get() else {
        *count = 0;
        return std::ptr::null();
    };
    *count = owner.admission.len() as i32;
    owner.admission.as_ptr()
}

/// Validates a retained 18-cubed canonical world grid. No input is retained.
/// Returns1 for admission,0 for compatibility callbacks and negatives for bad spans/IDs.
/// # Safety
/// IDs are an aligned readable span of the stated length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_face_policy_admit_grid(ids: *const i32, count: i32) -> i32 {
    if ids.is_null() || ids as usize % 4 != 0 || count != CELLS as i32 {
        return -1;
    }
    let Some(owner) = INSTALLED.get() else {
        return 0;
    };
    match owner
        .policy
        .admit_grid(std::slice::from_raw_parts(ids, CELLS))
    {
        Ok(true) => 1,
        Ok(false) => 0,
        Err(()) => -2,
    }
}

pub(in crate::render::chunk::meshing) fn state_admitted(state: i32) -> bool {
    usize::try_from(state)
        .ok()
        .and_then(|id| INSTALLED.get()?.admission.get(id))
        .copied()
        == Some(1)
}
pub(in crate::render::chunk::meshing) fn draw(
    own: i32,
    neighbor: i32,
    direction: usize,
) -> Option<bool> {
    let owner = INSTALLED.get()?;
    if !state_admitted(own) {
        return None;
    }
    owner.policy.draw(
        usize::try_from(own).ok()?,
        usize::try_from(neighbor).ok()?,
        direction,
        true,
        &|id| owner.facts(id),
    )
}

/// Batched CPU query for parity verification; gameplay consumes policy internally.
/// Output codes:0 compatibility callback,1 culled,2 visible.
/// # Safety
/// Triples and output are aligned, valid, disjoint spans of the stated lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_face_policy_queries(
    triples: *const i32,
    len: i32,
    output: *mut u8,
    output_len: i32,
) -> i32 {
    if triples.is_null()
        || output.is_null()
        || triples as usize % 4 != 0
        || !(0..=3_000_000).contains(&len)
        || len % 3 != 0
        || output_len != len / 3
    {
        return -1;
    }
    let Some(owner) = INSTALLED.get() else {
        return -3;
    };
    let triples = std::slice::from_raw_parts(triples, len as usize);
    if triples.chunks_exact(3).any(|row| {
        row[0] < 0
            || row[1] < 0
            || row[0] as usize >= owner.policy.states.len()
            || row[1] as usize >= owner.policy.states.len()
            || !(0..6).contains(&row[2])
    }) {
        return -2;
    }
    let output = std::slice::from_raw_parts_mut(output, output_len as usize);
    for (row, target) in triples.chunks_exact(3).zip(output) {
        *target = match draw(row[0], row[1], row[2] as usize) {
            None => 0,
            Some(false) => 1,
            Some(true) => 2,
        };
    }
    0
}
