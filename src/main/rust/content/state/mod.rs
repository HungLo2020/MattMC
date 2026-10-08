//! Ordered Cartesian state domains and their immutable transition graphs.
//! Definitions supply property domains in name order; the last varies fastest.
//! This owner is shared by native block definitions and temporary Java views.
pub(crate) mod ffi;

pub const MAX_STATES: usize = u16::MAX as usize;
const MAX_GRAPH_ENTRIES: usize = 16 * 1024 * 1024;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct StateSlot {
    pub count: u16,
    pub stride: u16,
}

impl StateSlot {
    pub fn value(self, state: u16) -> u16 {
        state / self.stride % self.count
    }

    pub fn with_value(self, state: u16, value: u16) -> Option<u16> {
        if value >= self.count {
            return None;
        }
        Some(state - self.value(state) * self.stride + value * self.stride)
    }
}

#[derive(Debug)]
pub struct StateLayout {
    slots: Vec<StateSlot>,
    states: usize,
}

impl StateLayout {
    pub fn new(counts: &[u16]) -> Result<Self, &'static str> {
        let mut slots = Vec::with_capacity(counts.len());
        let mut states = 1usize;
        for &count in counts.iter().rev() {
            if count == 0 {
                return Err("empty property domain");
            }
            slots.push(StateSlot {
                count,
                stride: states as u16,
            });
            states = states
                .checked_mul(count as usize)
                .filter(|&n| n <= MAX_STATES)
                .ok_or("state count")?;
        }
        slots.reverse();
        Ok(Self { slots, states })
    }

    pub fn slots(&self) -> &[StateSlot] {
        &self.slots
    }
    pub fn state_count(&self) -> usize {
        self.states
    }
}

/// Native-owned rows. Java temporarily projects IDs onto existing state objects;
/// it does not choose enumeration order or search for transition targets.
pub struct StateGraph {
    layout: StateLayout,
    values: Vec<i32>,
    targets: Vec<i32>,
    offsets: Vec<i32>,
    width: usize,
}

impl StateGraph {
    pub fn new(counts: &[u16]) -> Result<Self, &'static str> {
        let layout = StateLayout::new(counts)?;
        let width: usize = counts.iter().map(|&n| n as usize).sum();
        let target_len = layout
            .states
            .checked_mul(width)
            .filter(|&n| n <= MAX_GRAPH_ENTRIES)
            .ok_or("transition graph size")?;
        let value_len = layout
            .states
            .checked_mul(counts.len())
            .filter(|&n| n <= MAX_GRAPH_ENTRIES)
            .ok_or("state values size")?;
        let mut values = Vec::new();
        let mut targets = Vec::new();
        values
            .try_reserve_exact(value_len)
            .map_err(|_| "state values allocation")?;
        targets
            .try_reserve_exact(target_len)
            .map_err(|_| "transition graph allocation")?;
        let mut offsets = Vec::with_capacity(counts.len());
        let mut offset = 0usize;
        for &count in counts {
            offsets.push(offset as i32);
            offset += count as usize;
        }
        for state in 0..layout.states {
            for slot in &layout.slots {
                values.push(slot.value(state as u16) as i32);
                for value in 0..slot.count {
                    targets
                        .push(slot.with_value(state as u16, value).expect("domain value") as i32);
                }
            }
        }
        Ok(Self {
            layout,
            values,
            targets,
            offsets,
            width,
        })
    }

    fn header(&self) -> [i32; 5] {
        [
            self.layout.states as i32,
            self.layout.slots.len() as i32,
            self.width as i32,
            self.values.len() as i32,
            self.targets.len() as i32,
        ]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn graph_matches_cartesian_enumeration_and_independent_value_replacement() {
        let counts = [2, 3, 4];
        let graph = StateGraph::new(&counts).unwrap();
        let mut rows = Vec::new();
        for a in 0..2 {
            for b in 0..3 {
                for c in 0..4 {
                    rows.push([a, b, c]);
                }
            }
        }
        assert_eq!(graph.header(), [24, 3, 9, 72, 216]);
        for (state, row) in rows.iter().enumerate() {
            assert_eq!(&graph.values[state * 3..state * 3 + 3], row);
            for property in 0..3 {
                for value in 0..counts[property] {
                    let mut changed = *row;
                    changed[property] = value as i32;
                    let expected = rows.iter().position(|r| *r == changed).unwrap();
                    let index =
                        state * graph.width + graph.offsets[property] as usize + value as usize;
                    assert_eq!(graph.targets[index], expected as i32);
                }
            }
        }
    }

    #[test]
    fn no_properties_has_one_state_and_singleton_domains_stay_ordered() {
        let empty = StateGraph::new(&[]).unwrap();
        assert_eq!(empty.header(), [1, 0, 0, 0, 0]);
        let graph = StateGraph::new(&[1, 2, 1]).unwrap();
        assert_eq!(graph.values, [0, 0, 0, 0, 1, 0]);
        assert_eq!(graph.targets, [0, 0, 1, 0, 1, 0, 1, 1]);
    }

    #[test]
    fn reserved_id_and_materialized_graph_bounds_reject_oversized_domains() {
        assert!(StateLayout::new(&[0]).is_err());
        assert!(StateLayout::new(&[256, 256]).is_err());
        assert_eq!(
            StateLayout::new(&[u16::MAX]).unwrap().state_count(),
            MAX_STATES
        );
        assert!(StateGraph::new(&[u16::MAX]).is_err());
        assert_eq!(
            StateLayout::new(&[2]).unwrap().slots()[0].with_value(0, 2),
            None
        );
    }
}
