//! Build-local DH quad ownership and Frozen greedy topology. CPU facts only.
mod ffi;
#[cfg(test)]
mod tests;

/// Stable CPU projection; not a vertex/GPU ABI. Every byte is initialized.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) struct Quad {
    pub x: i16,
    pub y: i16,
    pub z: i16,
    pub width: i16,
    pub height: i16,
    pub direction: u8,
    pub material: u8,
    pub sky: u8,
    pub block: u8,
    pub error: u8,
    pub variant: u8,
    pub color: u32,
    pub identity: i32,
    pub position: i64,
}

#[derive(Clone, Copy)]
pub(super) struct Policy {
    pub exact: bool,
    pub overlap_errors: bool,
    pub curvature: bool,
}

impl Quad {
    fn key(self, east_west: bool) -> i64 {
        let (a, b, c) = match (self.direction, east_west) {
            (0 | 1, true) => (self.y, self.z, self.x),
            (0 | 1, false) => (self.y, self.x, self.z),
            (2 | 3, true) => (self.z, self.y, self.x),
            (2 | 3, false) => (self.z, self.x, self.y),
            (_, true) => (self.x, self.y, self.z),
            (_, false) => (self.x, self.z, self.y),
        };
        // Preserve Java's sign extension in each OR, including negative coordinates.
        (i64::from(a) << 48) | (i64::from(b) << 32) | (i64::from(c) << 16)
    }

    fn merge(&mut self, other: &mut Self, east_west: bool, policy: Policy) -> bool {
        if self.error != 0 || other.error != 0 || self.direction != other.direction {
            return false;
        }
        if policy.exact
            && (self.identity != other.identity
                || self.variant != other.variant
                || self.variant == 1 && self.position != other.position)
        {
            return false;
        }
        if east_west && self.y != other.y || !east_west && self.x != other.x {
            return false;
        }
        let coordinates = |q: &Self| match (q.direction, east_west) {
            (0 | 1, true) => (q.x, q.z),
            (0 | 1, false) => (q.z, q.y),
            (2 | 3, true) => (q.x, q.z),
            (2 | 3, false) => (q.y, q.z),
            (_, true) => (q.z, q.x),
            (_, false) => (q.y, q.z),
        };
        let (start, parallel) = coordinates(self);
        let (other_start, other_parallel) = coordinates(other);
        let (width, parallel_width, other_width, other_parallel_width) = if east_west {
            (self.width, self.height, other.width, other.height)
        } else {
            (self.height, self.width, other.height, other.width)
        };
        let shift = if policy.curvature { 4 } else { 11 };
        let max_width = 1 << shift;
        if i32::from(width) >= max_width
            // Arithmetic shifts are exact floor division for these power-of-two limits.
            || (i32::from(start) >> shift) != (i32::from(other_start) >> shift)
        {
            return false;
        }
        let end = i32::from(start) + i32::from(width);
        if end < i32::from(other_start) || parallel != other_parallel {
            return false;
        }
        if end > i32::from(other_start) {
            if i32::from(start) < i32::from(other_start) + i32::from(other_width)
                && policy.overlap_errors
            {
                self.error = 1;
                other.error = 1;
            }
            return false;
        }
        if parallel_width != other_parallel_width
            || self.color != other.color
            || self.material != other.material
            || self.sky != other.sky
            || self.block != other.block
        {
            return false;
        }
        if east_west {
            self.width = self.width.wrapping_add(other.width);
        } else {
            self.height = self.height.wrapping_add(other.height);
        }
        if self.identity != other.identity {
            self.identity = -1;
        }
        if self.variant != 1 || other.variant != 1 || self.position != other.position {
            self.variant = if self.variant == 0 || other.variant == 0 {
                0
            } else {
                2
            };
            self.position = 0;
        }
        true
    }
}

pub(super) struct Builder {
    pub lists: [[Vec<Quad>; 6]; 2],
    pub coverage: [i32; 6],
    pub premerges: i32,
    transparency: bool,
    // Reused across this build's merge passes, at most 65,536 primitive keys.
    sort_keys: Vec<i64>,
}

impl Builder {
    pub fn new(transparency: bool) -> Self {
        Self {
            lists: std::array::from_fn(|_| std::array::from_fn(|_| Vec::new())),
            coverage: [0; 6],
            premerges: 0,
            transparency,
            sort_keys: Vec::new(),
        }
    }

    pub fn add(&mut self, mut quad: Quad, premerge: bool, policy: Policy) -> bool {
        if quad.width <= 0 || quad.height <= 0 || quad.direction >= 6 {
            return false;
        }
        if quad.variant != 1 {
            quad.position = 0;
        }
        let transparent = self.transparency && (quad.color >> 24 != 255 || quad.material == 12);
        let category = if quad.identity > 0 {
            0
        } else if quad.identity == -1 {
            1
        } else {
            2
        };
        self.coverage[category] = self.coverage[category].wrapping_add(1);
        if !transparent {
            self.coverage[category + 3] = self.coverage[category + 3].wrapping_add(1);
        }
        let list = &mut self.lists[usize::from(transparent)][quad.direction as usize];
        if premerge {
            if let Some(last) = list.last_mut() {
                if last.merge(&mut quad, true, policy) || last.merge(&mut quad, false, policy) {
                    self.premerges = self.premerges.wrapping_add(1);
                    return true;
                }
            }
        }
        list.push(quad);
        true
    }

    pub fn merge(&mut self, policy: Policy) {
        for direction in 0..6 {
            for layer in 0..=usize::from(self.transparency) {
                Self::merge_list(
                    &mut self.lists[layer][direction],
                    &mut self.sort_keys,
                    true,
                    policy,
                );
            }
            if direction < 2 {
                for layer in 0..=usize::from(self.transparency) {
                    Self::merge_list(
                        &mut self.lists[layer][direction],
                        &mut self.sort_keys,
                        false,
                        policy,
                    );
                }
            }
        }
    }

    fn merge_list(list: &mut Vec<Quad>, keys: &mut Vec<i64>, east_west: bool, policy: Policy) {
        if list.len() <= 1 {
            return;
        }
        if list.len() <= 65_536 {
            // Low 16 key bits are zero. Original indices make ties unique/stable.
            keys.clear();
            if keys.capacity() < list.len() {
                keys.reserve_exact(list.len());
            }
            keys.extend(
                list.iter()
                    .enumerate()
                    .map(|(i, q)| q.key(east_west) | i as i64),
            );
            keys.sort_unstable();
            // Pull permutation cycles in place; keys double as the visited map.
            for first in 0..list.len() {
                let saved = list[first];
                let mut destination = first;
                loop {
                    let source = (keys[destination] & 0xffff) as usize;
                    keys[destination] = (keys[destination] & !0xffff) | destination as i64;
                    if source == first {
                        list[destination] = saved;
                        break;
                    }
                    list[destination] = list[source];
                    destination = source;
                }
            }
        } else {
            // Large compatibility inputs keep stable ties without index truncation.
            list.sort_by_key(|q| q.key(east_west));
        }
        let mut current = 0;
        for next in 1..list.len() {
            let mut candidate = list[next];
            if !list[current].merge(&mut candidate, east_west, policy) {
                current += 1;
                list[current] = candidate;
            }
        }
        list.truncate(current + 1);
    }
}
