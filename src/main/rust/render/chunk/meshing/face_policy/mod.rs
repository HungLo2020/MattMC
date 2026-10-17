//! Face policy over retained canonical world state IDs and immutable geometry.
//! Java supplies cached shapes and callback implementation identities once;
//! block/occlusion/fluid facts come from the existing native block registry.
mod ffi;
pub(super) use ffi::{draw, state_admitted};
mod geometry;
#[cfg(test)]
mod tests;

pub(super) const MAX_SHAPES: usize = 1024;
pub(super) const PAD: usize = 18;
pub(super) const CELLS: usize = PAD * PAD * PAD;

#[derive(Clone, Debug, PartialEq)]
pub(super) struct Shape {
    full_identity: bool,
    empty: bool,
    boxes: Vec<[f64; 6]>,
}
#[derive(Clone, Copy, PartialEq)]
pub(super) struct State {
    // Block, HalfTransparent, PowderSnow, IronBars, MangroveRoots,
    // Liquid, Leaves, or an unrecognized implementation. This is source
    // identity, never a precomputed per-block culling answer.
    implementation: u8,
    canonical_shape: bool,
    faces: [u16; 6],
}
#[derive(Clone, Copy)]
pub(super) struct Facts {
    block: u16,
    can_occlude: bool,
    fluid_family: Option<u8>,
    mangrove_roots: bool,
}
#[derive(PartialEq)]
pub(super) struct Policy {
    states: Vec<State>,
    shapes: Vec<Shape>,
    exposed: Vec<bool>,
}
impl Policy {
    fn new(states: Vec<State>, shapes: Vec<Shape>) -> Result<Self, ()> {
        if states.is_empty()
            || states.len() >= u16::MAX as usize
            || shapes.is_empty()
            || shapes.len() > MAX_SHAPES
            || shapes.iter().map(|s| s.boxes.len()).sum::<usize>() > 8192
            || shapes.iter().any(|s| !geometry::valid(s))
            || states.iter().any(|s| {
                s.implementation > 7 || s.faces.iter().any(|&id| id as usize >= shapes.len())
            })
        {
            return Err(());
        }
        let mut exposed = Vec::with_capacity(shapes.len() * shapes.len());
        for a in &shapes {
            for b in &shapes {
                exposed.push(geometry::exposed(a, b)?);
            }
        }
        Ok(Self {
            states,
            shapes,
            exposed,
        })
    }

    fn draw(
        &self,
        own: usize,
        neighbor: usize,
        direction: usize,
        pure_platform: bool,
        facts: &impl Fn(usize) -> Option<Facts>,
    ) -> Option<bool> {
        let a = self.states.get(own)?;
        let b = self.states.get(neighbor)?;
        if direction >= 6 || !b.canonical_shape {
            return None;
        }
        let adjacent = &self.shapes[b.faces[direction ^ 1] as usize];
        // Preserve Frozen callback ordering: full identity is tested first.
        if adjacent.full_identity {
            return Some(false);
        }
        let af = facts(own)?;
        let bf = facts(neighbor)?;
        let skip = match a.implementation {
            0 => false,
            1 | 2 => af.block == bf.block,
            4 => bf.mangrove_roots && direction < 2,
            5 => {
                let (Some(a), Some(b)) = (af.fluid_family, bf.fluid_family) else {
                    return None;
                };
                if a == 0 {
                    return None;
                }
                a == b
            }
            // Reloadable bars tags, registered leaf hooks and unknown methods
            // retain their original Java callbacks, including empty neighbors.
            _ => return None,
        };
        if skip {
            return Some(false);
        }
        if !pure_platform || !a.canonical_shape {
            return None;
        }
        if adjacent.empty || !bf.can_occlude {
            return Some(true);
        }
        let own_shape = a.faces[direction] as usize;
        if self.shapes[own_shape].empty {
            return Some(true);
        }
        Some(self.exposed[own_shape * self.shapes.len() + b.faces[direction ^ 1] as usize])
    }

    fn native_state(&self, id: usize, facts: &impl Fn(usize) -> Option<Facts>) -> bool {
        let Some(state) = self.states.get(id) else {
            return false;
        };
        if !state.canonical_shape {
            return false;
        }
        let Some(facts) = facts(id) else {
            return false;
        };
        match state.implementation {
            0 | 1 | 2 | 4 => true,
            5 => matches!(facts.fluid_family, Some(1 | 2)),
            _ => false,
        }
    }
    fn admit_grid(&self, ids: &[i32]) -> Result<bool, ()> {
        if ids.len() != CELLS
            || ids
                .iter()
                .any(|&id| id < 0 || id as usize >= self.states.len())
        {
            return Err(());
        }
        Ok(ids
            .iter()
            .all(|&id| self.states[id as usize].canonical_shape))
    }
}
