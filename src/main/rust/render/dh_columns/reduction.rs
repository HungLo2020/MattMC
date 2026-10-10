//! Complete ordered reduction policy. No GPU resources or Java objects enter it.
use super::sort;
use std::cell::RefCell;
const NONE: usize = usize::MAX;
pub(super) const LIMIT: usize = 4096;
fn min(p: u64) -> i32 {
    ((p >> 8) & 4095) as i32
}
fn max(p: u64) -> i32 {
    ((p >> 20) & 4095) as i32
}
fn alpha(p: u64) -> u64 {
    (p >> 56) & 15
}
fn visible(p: u64) -> bool {
    alpha(p) != 0
}
fn water(p: u64) -> bool {
    p != 0 && p >> 60 == 12
}
fn with_min(p: u64, y: i32) -> u64 {
    (p & !(4095 << 8)) | (((y as u64) & 4095) << 8)
}
fn occludes(p: u64) -> bool {
    visible(p) && matches!(p >> 60, 2 | 3 | 4 | 5 | 6 | 7 | 9)
}
#[derive(Clone, Copy)]
struct Node {
    data: u64,
    material: i32,
    lower: usize,
    higher: usize,
    smaller: usize,
    bigger: usize,
}
impl Node {
    fn new(data: u64, material: i32) -> Self {
        Self {
            data,
            material,
            lower: NONE,
            higher: NONE,
            smaller: NONE,
            bigger: NONE,
        }
    }
    fn size(&self) -> i32 {
        max(self.data) - min(self.data)
    }
}
struct Reducer {
    nodes: Vec<Node>,
    order: Vec<usize>,
    low: usize,
    high: usize,
    small: usize,
    big: usize,
    count: usize,
    semantic: bool,
}
impl Reducer {
    fn new(input: &[u64], materials: Option<&[i32]>, previous: Option<Self>) -> Result<Self, ()> {
        let (mut nodes, mut order) =
            previous.map_or_else(|| (Vec::new(), Vec::new()), |old| (old.nodes, old.order));
        nodes.clear();
        order.clear();
        // Exact growth bounds retained capacity, including odd-sized inputs.
        nodes.reserve_exact(input.len() * 2);
        order.reserve_exact(input.len() * 2);
        for (i, &data) in input.iter().enumerate() {
            if visible(data) && min(data) < max(data) {
                nodes.push(Node::new(data, materials.map_or(0, |m| m[i])));
            }
        }
        order.extend(0..nodes.len());
        let mut r = Self {
            order,
            count: nodes.len(),
            nodes,
            low: NONE,
            high: NONE,
            small: NONE,
            big: NONE,
            semantic: materials.is_some(),
        };
        if r.nodes.is_empty() {
            return Ok(r);
        }
        sort::sort(&mut r.order, |i| min(r.nodes[i].data));
        for j in 1..r.order.len() {
            let (l, h) = (r.order[j - 1], r.order[j]);
            let (bottom, top) = (max(r.nodes[l].data), min(r.nodes[h].data));
            if bottom > top {
                return Err(());
            }
            if bottom == top {
                r.nodes[l].higher = h;
                r.nodes[h].lower = l;
            } else {
                let air = r.nodes.len();
                let data = (r.nodes[h].data & ((15 << 60) | 255))
                    | ((top as u64) << 20)
                    | ((bottom as u64) << 8);
                r.nodes.push(Node::new(data, 0));
                r.nodes[air].lower = l;
                r.nodes[air].higher = h;
                r.nodes[l].higher = air;
                r.nodes[h].lower = air;
            }
        }
        r.low = r.order[0];
        r.high = *r.order.last().unwrap();
        r.order.extend(r.order.len()..r.nodes.len());
        r.sort_sizes();
        Ok(r)
    }
    fn sort_sizes(&mut self) {
        sort::sort(&mut self.order, |i| self.nodes[i].size());
        self.small = self.order.first().copied().unwrap_or(NONE);
        self.big = self.order.last().copied().unwrap_or(NONE);
        for (j, &i) in self.order.iter().enumerate() {
            self.nodes[i].smaller = if j == 0 { NONE } else { self.order[j - 1] };
            self.nodes[i].bigger = self.order.get(j + 1).copied().unwrap_or(NONE);
        }
    }
    fn relink(&mut self) {
        self.order.clear();
        let mut i = self.low;
        while i != NONE {
            if self.nodes[i].data != 0 {
                self.order.push(i);
            }
            i = self.nodes[i].higher;
        }
        self.sort_sizes();
    }
    fn remove(&mut self, i: usize) {
        let n = self.nodes[i];
        if n.lower != NONE {
            self.nodes[n.lower].higher = n.higher;
        } else {
            self.low = n.higher;
        }
        if n.higher != NONE {
            self.nodes[n.higher].lower = n.lower;
        } else {
            self.high = n.lower;
        }
        if n.smaller != NONE {
            self.nodes[n.smaller].bigger = n.bigger;
        } else {
            self.small = n.bigger;
        }
        if n.bigger != NONE {
            self.nodes[n.bigger].smaller = n.smaller;
        } else {
            self.big = n.smaller;
        }
        if visible(n.data) {
            self.count -= 1;
        }
        self.nodes[i] = Node::new(0, 0);
    }
    fn merge_material(&mut self, survivor: usize, removed: usize) {
        if self.semantic
            && visible(self.nodes[removed].data)
            && self.nodes[survivor].material != self.nodes[removed].material
        {
            self.nodes[survivor].material = -1;
        }
    }
    fn extend(&mut self, i: usize, y: i32) {
        self.nodes[i].data = with_min(self.nodes[i].data, y);
    }
    fn resort(&mut self, i: usize) {
        let mut bigger = self.nodes[i].bigger;
        if bigger == NONE || self.nodes[i].size() <= self.nodes[bigger].size() {
            return;
        }
        let small = self.nodes[i].smaller;
        if small != NONE {
            self.nodes[small].bigger = bigger;
        } else {
            self.small = bigger;
        }
        self.nodes[bigger].smaller = small;
        loop {
            bigger = self.nodes[bigger].bigger;
            if bigger == NONE || self.nodes[i].size() <= self.nodes[bigger].size() {
                break;
            }
        }
        let previous = if bigger == NONE {
            self.big
        } else {
            self.nodes[bigger].smaller
        };
        self.nodes[i].smaller = previous;
        self.nodes[i].bigger = bigger;
        if bigger != NONE {
            self.nodes[bigger].smaller = i;
        } else {
            self.big = i;
        }
        if previous != NONE {
            self.nodes[previous].bigger = i;
        } else {
            self.small = i;
        }
    }
    fn mergeable(&self, a: usize, b: usize) -> bool {
        let (a, b) = (self.nodes[a].data, self.nodes[b].data);
        alpha(a) == alpha(b) && (water(a) == water(b) || !visible(if water(a) { b } else { a }))
    }
    fn step(&mut self, current: usize, fast: bool) -> usize {
        let n = self.nodes[current];
        let mut next = if fast { n.smaller } else { n.bigger };
        let h = n.higher != NONE && self.mergeable(n.higher, current);
        let l = n.lower != NONE && self.mergeable(n.lower, current);
        let (survivor, removed) =
            if h && (!l || self.nodes[n.higher].size() <= self.nodes[n.lower].size()) {
                (n.higher, current)
            } else if l {
                (current, n.lower)
            } else {
                return next;
            };
        if next == removed {
            next = self.nodes[next].smaller;
        }
        self.merge_material(survivor, removed);
        self.extend(survivor, min(self.nodes[removed].data));
        if !fast {
            self.resort(survivor);
        }
        self.remove(removed);
        if fast {
            next
        } else {
            self.small
        }
    }
    fn lower_node(&self, size: i32) -> usize {
        let mut i = self.small;
        while i != NONE {
            if self.nodes[i].size() >= size {
                return self.nodes[i].smaller;
            }
            i = self.nodes[i].bigger;
        }
        self.big
    }
    fn done(&self, target: i32) -> bool {
        self.count as i32 <= target
    }
    fn reduce(&mut self, target: i32) {
        let mut surface = None;
        let mut i = self.low;
        while i != NONE {
            let n = self.nodes[i];
            if visible(n.data) && water(n.data) && surface.is_none_or(|(p, _)| max(n.data) > max(p))
            {
                surface = Some((n.data, n.material));
            }
            i = n.higher;
        }
        self.reduce_steps(target);
        if let Some((data, material)) = surface {
            let mut i = self.low;
            let mut covered = NONE;
            let mut below = NONE;
            while i != NONE {
                let n = self.nodes[i];
                if visible(n.data) {
                    if water(n.data) && max(n.data) >= max(data) {
                        return;
                    }
                    if covered == NONE && min(n.data) <= max(data) && max(n.data) >= max(data) {
                        covered = i;
                    }
                    if max(n.data) < max(data) {
                        below = i;
                    }
                }
                i = n.higher;
            }
            let target = if covered == NONE { below } else { covered };
            if target != NONE {
                self.nodes[target].data = data;
                self.nodes[target].material = material;
            }
        }
    }
    fn reduce_steps(&mut self, target: i32) {
        for special in 1..=2 {
            let mut i = self.lower_node(special + 1);
            while i != NONE {
                if self.done(target) {
                    self.relink();
                    return;
                }
                i = self.step(i, true);
            }
            self.relink();
        }
        let mut i = self.small;
        while i != NONE {
            if self.done(target) {
                return;
            }
            i = self.step(i, false);
        }
        let mut i = self.small;
        while i != NONE {
            if self.done(target) {
                return;
            }
            let n = self.nodes[i];
            if n.lower != NONE
                && n.higher != NONE
                && alpha(self.nodes[n.lower].data) == alpha(self.nodes[n.higher].data)
            {
                self.merge_material(n.higher, n.lower);
                self.merge_material(n.higher, i);
                self.extend(n.higher, min(self.nodes[n.lower].data));
                self.resort(n.higher);
                self.remove(n.lower);
                self.remove(i);
                i = self.small;
            } else {
                i = n.bigger;
            }
        }
        let mut low = self.low;
        while low != NONE {
            if self.done(target) {
                return;
            }
            let low_y = min(self.nodes[low].data);
            let mut high = self.nodes[low].higher;
            loop {
                if high == NONE {
                    self.low = NONE;
                    self.high = NONE;
                    self.small = NONE;
                    self.big = NONE;
                    self.count = 0;
                    return;
                }
                if visible(self.nodes[high].data) {
                    self.merge_material(high, low);
                    self.extend(high, low_y);
                    self.resort(high);
                    self.remove(low);
                    low = self.low;
                    break;
                }
                self.remove(low);
                low = high;
                high = self.nodes[high].higher;
            }
        }
    }
    fn output(&self, size: usize, out: &mut Vec<(u64, i32)>) -> Result<(), ()> {
        out.clear();
        out.reserve_exact(size);
        let mut i = self.high;
        while i != NONE {
            let n = self.nodes[i];
            if visible(n.data) {
                if out.len() == size {
                    return Err(());
                }
                out.push((n.data, n.material));
            }
            i = n.lower;
        }
        if out.is_empty() && size == 0 {
            return Err(());
        }
        out.resize(size, (0, 0));
        Ok(())
    }
}
fn one(input: &[u64]) -> u64 {
    let Some(&first) = input.iter().find(|&&p| visible(p)) else {
        return 0;
    };
    let (mut highest, mut lowest, mut top_water) =
        (first, first, if water(first) { first } else { 0 });
    for &p in input.iter().skip_while(|&&p| !visible(p)) {
        if !visible(p) {
            continue;
        }
        if max(p) > max(highest) {
            highest = p;
        } else if min(p) < min(lowest) {
            lowest = p;
        }
        if water(p) && (top_water == 0 || max(p) > max(top_water)) {
            top_water = p;
        }
    }
    let selected = if top_water != 0
        && !input
            .iter()
            .any(|&p| occludes(p) && max(p) > max(top_water))
    {
        top_water
    } else {
        highest
    };
    with_min(selected, min(lowest))
}
#[derive(Default)]
struct Scratch {
    reducer: Option<Reducer>,
    output: Vec<(u64, i32)>,
}
thread_local! {static SCRATCH:RefCell<Scratch>=RefCell::new(Scratch::default());}
/// Scratch belongs to one native worker thread. It holds bounded numeric values
/// only, retains no source pointer/world/Java object, and drops with the thread.
pub(super) fn with_reduced<T>(
    input: &[u64],
    materials: Option<&[i32]>,
    target: i32,
    size: usize,
    consume: impl FnOnce(&[(u64, i32)]) -> T,
) -> Result<T, ()> {
    if input.len() > LIMIT || size > LIMIT || materials.is_some_and(|m| m.len() < input.len()) {
        return Err(());
    }
    SCRATCH.with(|scratch| {
        let mut scratch = scratch.borrow_mut();
        if materials.is_none() && target <= 1 {
            if target == 1 && size == 0 {
                return Err(());
            }
            let point = if target == 1 { one(input) } else { 0 };
            scratch.output.clear();
            scratch.output.reserve_exact(size);
            scratch.output.resize(size, (0, 0));
            if target == 1 {
                scratch.output[0].0 = point;
            }
        } else {
            let mut reducer = Reducer::new(input, materials, scratch.reducer.take())?;
            reducer.reduce(target);
            let result = reducer.output(size, &mut scratch.output);
            scratch.reducer = Some(reducer);
            result?;
        }
        Ok(consume(&scratch.output))
    })
}
#[cfg(test)]
pub(super) fn reduce(
    input: &[u64],
    materials: Option<&[i32]>,
    target: i32,
    size: usize,
) -> Result<Vec<(u64, i32)>, ()> {
    with_reduced(input, materials, target, size, |out| out.to_vec())
}
#[cfg(test)]
pub(super) fn scratch_capacities() -> [usize; 3] {
    SCRATCH.with(|s| {
        let s = s.borrow();
        [
            s.reducer.as_ref().map_or(0, |r| r.nodes.capacity()),
            s.reducer.as_ref().map_or(0, |r| r.order.capacity()),
            s.output.capacity(),
        ]
    })
}
