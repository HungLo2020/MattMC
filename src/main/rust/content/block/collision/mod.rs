//! Immutable intrinsic collision geometry. Contextual/dynamic shapes remain
//! outside this registry; consumers must decline them before publication.
use super::{BlockRegistry, StateFlags, StateId};
use std::sync::OnceLock;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct DhFacts {
    pub(crate) solid: bool,
    pub(crate) opacity: u8,
}
#[derive(Debug, PartialEq)]
pub(crate) struct Catalog {
    states: Vec<i32>,
    shapes: Vec<Vec<[f64; 6]>>,
    dh: Vec<Option<DhFacts>>,
}
impl Catalog {
    pub(crate) fn new(
        states: Vec<i32>,
        shapes: Vec<Vec<[f64; 6]>>,
        facts: impl Fn(usize) -> (StateFlags, u8),
    ) -> Option<Self> {
        if states.is_empty()
            || states.len() >= u16::MAX as usize
            || shapes.len() > 1024
            || shapes.iter().map(Vec::len).sum::<usize>() > 8192
            || shapes.iter().any(|s| {
                s.len() > 256
                    || s.iter().any(|b| {
                        b.iter().any(|v| !v.is_finite()) || (0..3).any(|a| b[a] >= b[a + 3])
                    })
            })
            || states.iter().any(|&s| s < -1 || s >= shapes.len() as i32)
        {
            return None;
        }
        let dh = states
            .iter()
            .enumerate()
            .map(|(id, &shape)| {
                let (flags, light) = facts(id);
                if shape < 0 || flags.contains(StateFlags::CUSTOM) {
                    return None;
                }
                let air = flags.contains(StateFlags::AIR);
                let occludes = flags.contains(StateFlags::CAN_OCCLUDE);
                Some(DhFacts {
                    solid: !air && !shapes[shape as usize].is_empty(),
                    opacity: if air {
                        0
                    } else if flags.contains(StateFlags::HAS_FLUID) && !occludes {
                        1
                    } else if light == 0 && !occludes {
                        0
                    } else {
                        16
                    },
                })
            })
            .collect();
        Some(Self { states, shapes, dh })
    }
    pub(crate) fn from_registry(
        states: Vec<i32>,
        shapes: Vec<Vec<[f64; 6]>>,
        registry: &BlockRegistry,
    ) -> Option<Self> {
        if states.len() != registry.state_count() {
            return None;
        }
        Self::new(states, shapes, |id| {
            (
                registry.flags(StateId(id as u16)),
                registry.light_block(StateId(id as u16)),
            )
        })
    }
    pub(crate) fn dh(&self, state: u32) -> Option<DhFacts> {
        self.dh.get(state as usize).copied().flatten()
    }
}
static CATALOG: OnceLock<Catalog> = OnceLock::new();
pub(crate) fn installed() -> Option<&'static Catalog> {
    CATALOG.get()
}

/// Install cached intrinsic boxes once. All input spans are aligned, live and
/// immutable for this call. Rows contain [first box, box count]. -1 is unsupported.
#[no_mangle]
pub unsafe extern "C" fn mattmc_collision_install(
    states: *const i32,
    count: i32,
    rows: *const i32,
    row_length: i32,
    boxes: *const f64,
    box_length: i32,
) -> i32 {
    if states.is_null()
        || rows.is_null()
        || boxes.is_null()
        || states as usize % 4 != 0
        || rows as usize % 4 != 0
        || boxes as usize % 8 != 0
        || !(1..65535).contains(&count)
        || !(0..=2048).contains(&row_length)
        || row_length % 2 != 0
        || !(0..=49152).contains(&box_length)
        || box_length % 6 != 0
    {
        return -1;
    }
    let Some(registry) = super::installed() else {
        return -1;
    };
    let rows = std::slice::from_raw_parts(rows, row_length as usize);
    let boxes = std::slice::from_raw_parts(boxes, box_length as usize);
    let mut shapes = Vec::with_capacity(rows.len() / 2);
    for row in rows.chunks_exact(2) {
        if row[0] < 0
            || !(0..=256).contains(&row[1])
            || (row[0] as usize + row[1] as usize) > boxes.len() / 6
        {
            return -1;
        }
        shapes.push(
            boxes[row[0] as usize * 6..(row[0] + row[1]) as usize * 6]
                .chunks_exact(6)
                .map(|b| b.try_into().unwrap())
                .collect(),
        );
    }
    let Some(catalog) = Catalog::from_registry(
        std::slice::from_raw_parts(states, count as usize).to_vec(),
        shapes,
        registry,
    ) else {
        return -1;
    };
    match CATALOG.set(catalog) {
        Ok(()) => 1,
        Err(value) => {
            if CATALOG.get() == Some(&value) {
                1
            } else {
                -2
            }
        }
    }
}
#[cfg(test)]
mod tests;
