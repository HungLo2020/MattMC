//! Secondary spatial index of the residency owner's physical slots. Stock
//! coordinates are immutable; unknown/custom bindings require live callbacks.
use std::collections::BTreeMap;

#[derive(Clone, Copy)]
enum Binding {
    Vacant,
    Custom,
    Stock(i32, i32),
}

pub(super) struct RangeIndex {
    bindings: Box<[Binding]>,
    ordered: BTreeMap<(i32, i32), usize>, // z, x: original row-major order
    custom: usize,
}
impl RangeIndex {
    pub(super) fn new(length: usize) -> Self {
        Self {
            bindings: vec![Binding::Vacant; length].into_boxed_slice(),
            ordered: BTreeMap::new(),
            custom: 0,
        }
    }
    pub(super) fn publish(&mut self, index: usize, present: bool, position: Option<(i32, i32)>) {
        match self.bindings[index] {
            Binding::Vacant => {}
            Binding::Custom => self.custom -= 1,
            Binding::Stock(x, z) => {
                self.ordered.remove(&(z, x));
            }
        }
        self.bindings[index] = if !present {
            Binding::Vacant
        } else if let Some((x, z)) = position {
            self.ordered.insert((z, x), index);
            Binding::Stock(x, z)
        } else {
            self.custom += 1;
            Binding::Custom
        };
    }
    /// None means a custom live binding exists: do not skip its getters.
    /// The caller reads a fresh page after any mutation or center change.
    pub(super) fn page(
        &self,
        policy: &super::Residency,
        bounds: [i32; 4],
        cursor: (i32, i32),
        output: &mut [i64],
    ) -> Option<usize> {
        if self.custom != 0 {
            return None;
        }
        let [min_x, max_x, min_z, max_z] = bounds;
        let mut count = 0;
        for (&(z, x), &index) in self.ordered.range((cursor.1, cursor.0)..=(max_z, i32::MAX)) {
            if x < min_x || x > max_x || z < min_z || !policy.in_range(x, z) {
                continue;
            }
            // A stock producer may still publish into an arbitrary physical
            // slot. Frozen lookup accepts it only at its own ring index.
            if policy.index(x, z) != index || policy.slots[index] == 0 {
                continue;
            }
            if count == output.len() / 3 {
                break;
            }
            output[count * 3..count * 3 + 3].copy_from_slice(&[index as i64, x as i64, z as i64]);
            count += 1;
        }
        Some(count)
    }
}
