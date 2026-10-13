//! Camera immersion consumes primary world cells; no Java point/block arrays.
//! Near-plane arithmetic and fluid height comparisons retain Frozen's ordering.
use crate::content::{block::{BlockRegistry, StateId}, fluid::{self, Family}};

pub(crate) const NONE: i32 = 5;
pub(crate) const WATER: i32 = 1;
pub(crate) const LAVA: i32 = 0;
pub(crate) const POWDER_SNOW: i32 = 2;

fn cell(point: [f64; 3]) -> Option<[i32; 3]> {
    if point.iter().any(|p| !p.is_finite() || *p < i32::MIN as f64 || *p >= i32::MAX as f64) { return None; }
    Some(point.map(|v| v.floor() as i32))
}

/// None declines an unsupported live source. No partial result is published.
pub(crate) fn query(
    registry: &BlockRegistry, position: [f64; 3], axes: &[[f64; 3]; 3],
    half_height: f64, aspect: f64, tags: u32, near_plane: bool,
    mut read: impl FnMut([i32; 3]) -> Option<StateId>,
) -> Option<i32> {
    let fluids = fluid::registry();
    let mut sample = |point: [f64; 3], water: bool| -> Option<i32> {
        let pos = cell(point)?;
        let state = read(pos)?;
        if state.0 as usize >= registry.state_count() { return None; }
        let traits = fluids.state(registry.fluid_state(state))?;
        let tagged = tags & (1 << (traits.fluid.0 as u32 + if water { 0 } else { 5 })) != 0;
        if tagged {
            let family = fluids.definition(traits.fluid)?.family;
            let height = if family == Family::Empty { 0.0 } else {
                let above = read([pos[0], pos[1].wrapping_add(1), pos[2]])?;
                if above.0 as usize >= registry.state_count() { return None; }
                let above_fluid = fluids.state(registry.fluid_state(above))?;
                if fluids.definition(above_fluid.fluid)?.family == family { 1.0 } else { traits.own_height }
            };
            // Java adds int blockY to float height BEFORE promotion to double.
            let surface = (pos[1] as f32 + height) as f64;
            if if water { point[1] < surface } else { point[1] <= surface } {
                return Some(if water { WATER } else { LAVA });
            }
        } else if !water && registry.block(registry.block_of(state)).name() == "minecraft:powder_snow" {
            return Some(POWDER_SNOW);
        }
        Some(NONE)
    };
    if !near_plane { return sample(position, true); }
    let forward = axes[0].map(|v| v * 0.05_f32 as f64);
    let left = axes[1].map(|v| v * (half_height * aspect));
    let up = axes[2].map(|v| v * half_height);
    for corner in 0..5 {
        let point = std::array::from_fn(|axis| {
            let offset = match corner {
                0 => forward[axis],
                1 => (forward[axis] + up[axis]) + left[axis],
                2 => (forward[axis] + up[axis]) - left[axis],
                3 => (forward[axis] - up[axis]) + left[axis],
                _ => (forward[axis] - up[axis]) - left[axis],
            };
            position[axis] + offset
        });
        let result = sample(point, false)?;
        if result != NONE { return Some(result); }
    }
    Some(NONE)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::content::{block::{Builder, StateFacts}, fluid::FluidStateId};
    fn registry() -> BlockRegistry {
        let mut builder = Builder::new();
        for (name, fluid) in [("air", 0), ("water", 18), ("lava", 36), ("powder_snow", 0)] {
            builder.block(&format!("minecraft:{name}"), &[], 0,
                vec![StateFacts { fluid_state: FluidStateId(fluid), ..StateFacts::default() }]).unwrap();
        }
        builder.finish(1, vec![0]).unwrap()
    }
    const TAGS: u32 = 6 | (24 << 5);
    const AXES: [[f64; 3]; 3] = [[0., 0., 1.], [1., 0., 0.], [0., 1., 0.]];
    #[test]
    fn water_and_lava_preserve_distinct_surface_inequalities_and_above_reads() {
        let r = registry();
        let height = (8.0_f32 / 9.0) as f64;
        let mut reads = Vec::new();
        assert_eq!(query(&r, [0.5, height, 0.5], &AXES, 0., 1., TAGS, false, |p| {
            reads.push(p); Some(StateId(if p[1] == 0 { 1 } else { 0 }))
        }), Some(NONE));
        assert_eq!(reads, [[0, 0, 0], [0, 1, 0]]);
        assert_eq!(query(&r, [0.5, height, 0.5], &AXES, 0., 1., TAGS, true,
            |p| Some(StateId(if p[1] == 0 { 2 } else { 0 }))), Some(LAVA));
        assert_eq!(query(&r, [0.5, 0.99, 0.5], &AXES, 0., 1., TAGS, false,
            |_| Some(StateId(1))), Some(WATER));
    }
    #[test]
    fn corner_order_and_unsupported_reads_short_circuit() {
        let r = registry(); let mut reads = Vec::new();
        assert_eq!(query(&r, [0.95, 0.95, 0.95], &AXES, 0.1, 1., TAGS, true, |p| {
            reads.push(p); Some(StateId(if p[0] == 1 { 3 } else { 0 }))
        }), Some(POWDER_SNOW));
        assert_eq!(reads, [[0, 0, 1], [1, 1, 1]]);
        let mut reads = 0;
        assert_eq!(query(&r, [0.5; 3], &AXES, 0., 1., TAGS, true, |_| { reads += 1; None }), None);
        assert_eq!(reads, 1);
        assert_eq!(query(&r, [f64::NAN; 3], &AXES, 0., 1., TAGS, false, |_| panic!()), None);
    }
    #[test]
    fn dynamic_tags_and_float_height_rounding_are_semantic_inputs() {
        let r = registry();
        assert_eq!(query(&r, [0.5; 3], &AXES, 0., 1., 0, false, |_| Some(StateId(1))), Some(NONE));
        // Tagging empty as lava uses empty height zero, without an above lookup.
        let mut count = 0;
        assert_eq!(query(&r, [0.5, 0., 0.5], &AXES, 0., 1., 1 << 5, true, |_| {
            count += 1; Some(StateId(0))
        }), Some(LAVA));
        assert_eq!(count, 1);
        assert_eq!(query(&r, [0.5, 16_777_216.5, 0.5], &AXES, 0., 1., TAGS, false,
            |p| Some(StateId(if p[1] == 16_777_216 { 1 } else { 0 }))), Some(NONE));
    }
}
