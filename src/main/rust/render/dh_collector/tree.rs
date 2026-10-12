//! Retained DH visibility topology. Java reports generation-tree mutations,
//! section enablement and container swaps; frames never import a tree or rows.
//! No Java address or GPU object is stored here.
use std::collections::HashMap;
use std::sync::Mutex;

#[derive(Clone, Default)]
struct Node {
    key: i64,
    children: [Option<usize>; 4],
    value: bool,
    enabled: bool,
    generation: i64,
    token: i64,
    epoch: i64,
}

pub(crate) struct Tree {
    nodes: Vec<Option<Node>>,
    free: Vec<usize>,
    indices: HashMap<i64, usize>,
    root: u32,
    leaf: u32,
    diameter: i32,
    pub(crate) center: [i32; 2],
    next_token: i64,
}

pub(crate) struct Owner(pub(crate) Mutex<Option<Tree>>);

fn level(key: i64) -> u32 {
    (key & 127) as u32
}
fn coords(key: i64) -> [i32; 2] {
    [
        (((key >> 8) & 0x0fff_ffff) as i32) << 4 >> 4,
        (((key >> 36) & 0x0fff_ffff) as i32) << 4 >> 4,
    ]
}
fn encode(d: u32, x: i32, z: i32) -> i64 {
    (d as i64 & 127) | ((x as i64 & 0x0fff_ffff) << 8) | ((z as i64 & 0x0fff_ffff) << 36)
}
fn parent(key: i64) -> i64 {
    let [x, z] = coords(key);
    encode(level(key) + 1, x >> 1, z >> 1)
}
fn child_index(key: i64) -> usize {
    let [x, z] = coords(key);
    ((x & 1) | ((z & 1) << 1)) as usize
}
fn bounds(key: i64) -> ([i32; 2], i32) {
    let [x, z] = coords(key);
    let d = level(key);
    ([x.wrapping_shl(d), z.wrapping_shl(d)], 1i32.wrapping_shl(d))
}

/// Exact normalized JOML planes, copied once per frame. Keep f32 arithmetic
/// and left-associated sums: Frozen's testAab does not use fused operations.
pub(crate) struct Frustum<'a> {
    pub(crate) planes: Option<&'a [f32; 24]>,
    pub(crate) min_y: f32,
    pub(crate) max_y: f32,
}
impl Frustum<'_> {
    fn intersects(&self, key: i64) -> bool {
        let Some(planes) = self.planes else {
            return true;
        };
        let ([x, z], width) = bounds(key);
        let min = [x as f32, self.min_y, z as f32];
        let max = [
            x.wrapping_add(width) as f32,
            self.max_y,
            z.wrapping_add(width) as f32,
        ];
        planes.chunks_exact(4).all(|p| {
            let coord = |i| if p[i] < 0.0 { min[i] } else { max[i] };
            p[0] * coord(0) + p[1] * coord(1) + p[2] * coord(2) >= -p[3]
        })
    }
}

#[derive(Default, Debug)]
pub(crate) struct Walk {
    pub(crate) candidates: Vec<(i64, i64)>,
    pub(crate) visited: i64,
    pub(crate) culled: i64,
    pub(crate) disabled: i64,
    pub(crate) missing: i64,
}
impl Tree {
    pub(crate) fn new(root: u32, leaf: u32, diameter: i32, center: [i32; 2]) -> Self {
        Self {
            nodes: Vec::new(),
            free: Vec::new(),
            indices: HashMap::new(),
            root,
            leaf,
            diameter,
            center,
            next_token: 0,
        }
    }
    pub(crate) fn add(&mut self, key: i64) {
        if self.indices.contains_key(&key) {
            return;
        }
        let d = level(key);
        if d < self.leaf || d > self.root {
            return;
        }
        let node = Some(Node {
            key,
            ..Node::default()
        });
        let i = if let Some(i) = self.free.pop() {
            self.nodes[i] = node;
            i
        } else {
            self.nodes.push(node);
            self.nodes.len() - 1
        };
        self.indices.insert(key, i);
        if d < self.root {
            let p = parent(key);
            self.add(p);
            let parent = self.indices[&p];
            self.nodes[parent].as_mut().unwrap().children[child_index(key)] = Some(i);
        }
    }
    /// Identity token fences section replacement and late worker updates.
    pub(crate) fn value(&mut self, key: i64, present: bool) -> i64 {
        self.add(key);
        let Some(&i) = self.indices.get(&key) else {
            return 0;
        };
        self.next_token = self
            .next_token
            .checked_add(1)
            .expect("DH visibility token exhausted");
        let node = self.nodes[i].as_mut().unwrap();
        node.value = present;
        node.enabled = false;
        node.generation = -1;
        node.token = self.next_token;
        node.token
    }
    pub(crate) fn state(
        &mut self,
        key: i64,
        token: i64,
        enabled: bool,
        generation: i64,
        epoch: i64,
    ) {
        if let Some(&i) = self.indices.get(&key) {
            let node = self.nodes[i].as_mut().unwrap();
            if node.value && node.token == token {
                node.enabled = enabled;
                node.generation = generation;
                node.epoch = epoch;
            }
        }
    }
    pub(crate) fn invalidate(&mut self, key: i64, token: i64, epoch: i64) {
        if let Some(&i) = self.indices.get(&key) {
            let node = self.nodes[i].as_mut().unwrap();
            if node.token == token && node.epoch == epoch {
                node.generation = -1;
            }
        }
    }
    pub(crate) fn remove(&mut self, key: i64, include_self: bool) {
        let Some(&i) = self.indices.get(&key) else {
            return;
        };
        let children = std::mem::take(&mut self.nodes[i].as_mut().unwrap().children);
        for child in children.into_iter().flatten() {
            let key = self.nodes[child].as_ref().unwrap().key;
            self.remove(key, true);
        }
        if include_self {
            if level(key) < self.root {
                if let Some(&p) = self.indices.get(&parent(key)) {
                    self.nodes[p].as_mut().unwrap().children[child_index(key)] = None;
                }
            }
            self.indices.remove(&key);
            self.nodes[i] = None;
            self.free.push(i);
        }
    }
    fn in_bounds(&self, key: i64) -> bool {
        // QuadTree.DoSquaresOverlap performs Java int additions before f32 conversion.
        let ([x, z], w) = bounds(key);
        let x = x << 4 >> 4;
        let z = z << 4 >> 4;
        let min = [
            self.center[0].wrapping_sub(self.diameter / 2),
            self.center[1].wrapping_sub(self.diameter / 2),
        ];
        (min[0] as f32) < x.wrapping_add(w) as f32
            && min[0].wrapping_add(self.diameter) as f32 > x as f32
            && (min[1] as f32) < z.wrapping_add(w) as f32
            && min[1].wrapping_add(self.diameter) as f32 > z as f32
    }
    pub(crate) fn walk(&self, frustum: &Frustum<'_>) -> Walk {
        let mut out = Walk::default();
        let cx = self.center[0] >> self.root;
        let cz = self.center[1] >> self.root;
        // MovableGridRingList's stable squared-distance order, x then z ties.
        // This nine-element order is independent of the center and topology.
        let roots = [(0, 0), (-1, 0), (0, -1), (0, 1), (1, 0),
                     (-1, -1), (-1, 1), (1, -1), (1, 1)];
        let mut frontier = Vec::new();
        let mut next = Vec::new();
        for (x, z) in roots {
            let key = encode(self.root, cx.wrapping_add(x), cz.wrapping_add(z));
            let Some(&root) = self.indices.get(&key) else {
                continue;
            };
            let reject = |i: usize, out: &mut Walk| {
                let n = self.nodes[i].as_ref().unwrap();
                if n.value && !frustum.intersects(n.key) {
                    out.culled += 1;
                    true
                } else {
                    false
                }
            };
            if reject(root, &mut out) || !self.in_bounds(key) {
                continue;
            }
            frontier.clear();
            frontier.push(root);
            let mut detail = self.root;
            while !frontier.is_empty() {
                next.clear();
                for &i in &frontier {
                    let node = self.nodes[i].as_ref().unwrap();
                    out.visited += 1;
                    if node.value {
                        if !node.enabled {
                            out.disabled += 1;
                        } else if node.generation < 0 {
                            out.missing += 1;
                        } else if node.generation > 0 {
                            out.candidates.push((node.key, node.generation));
                        }
                    }
                    // Every frontier node already passed reject on insertion.
                    // The topology lock and immutable frame planes make that
                    // result stable. Frozen's repeated accepted-parent test
                    // cannot increment its culling counter; children still
                    // receive their original test in the original order.
                    if detail > self.leaf {
                        for child in node.children.into_iter().flatten() {
                            if !reject(child, &mut out) {
                                next.push(child);
                            }
                        }
                    }
                }
                std::mem::swap(&mut frontier, &mut next);
                detail = detail.saturating_sub(1);
            }
        }
        out
    }
}

/// # Safety
/// Returned unique CPU owner is released once, after its scoped users finish.
#[no_mangle]
pub extern "C" fn mattmc_dh_visibility_create(
    root: i32,
    leaf: i32,
    diameter: i32,
    x: i32,
    z: i32,
) -> *mut Owner {
    if leaf < 0 || root < leaf || root > 30 || diameter <= 0 {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(Owner(Mutex::new(Some(Tree::new(
        root as u32,
        leaf as u32,
        diameter,
        [x, z],
    ))))))
}
/// # Safety
/// Live scoped owner. Events: add, value, remove children, remove subtree,
/// center change, close. Close drops bulk storage; late callbacks are ignored.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_visibility_event(
    owner: *const Owner,
    kind: i32,
    key: i64,
    a: i32,
    b: i32,
) -> i64 {
    let Some(owner) = owner.as_ref() else {
        return 0;
    };
    let mut data = owner.0.lock().unwrap();
    if kind == 5 {
        data.take();
        return 0;
    }
    let Some(tree) = data.as_mut() else { return 0 };
    match kind {
        0 => tree.add(key),
        1 => return tree.value(key, a != 0),
        2 => tree.remove(key, false),
        3 => tree.remove(key, true),
        4 => tree.center = [a, b],
        _ => {}
    }
    0
}
/// # Safety
/// Live scoped owner. No Java memory is retained; stale identity updates are ignored.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_visibility_state(
    owner: *const Owner,
    key: i64,
    token: i64,
    enabled: i32,
    generation: i64,
    epoch: i64,
) {
    if let Some(owner) = owner.as_ref() {
        if let Some(tree) = owner.0.lock().unwrap().as_mut() {
            tree.state(key, token, enabled != 0, generation, epoch);
        }
    }
}
/// # Safety
/// Live scoped owner. Closing an older container cannot invalidate a newer swap.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_visibility_invalidate(
    owner: *const Owner,
    key: i64,
    token: i64,
    epoch: i64,
) {
    if let Some(owner) = owner.as_ref() {
        if let Some(tree) = owner.0.lock().unwrap().as_mut() {
            tree.invalidate(key, token, epoch);
        }
    }
}
/// # Safety
/// Unique wrapper released once after all FFM scope users have finished.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_visibility_release(owner: *mut Owner) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn replacement_and_detachment_fence_late_workers() {
        let mut t = Tree::new(8, 6, 256, [0, 0]);
        let key = encode(6, 0, 0);
        let old = t.value(key, true);
        t.state(key, old, true, 7, 0);
        assert_eq!(
            t.walk(&Frustum {
                planes: None,
                min_y: 0.,
                max_y: 1.
            })
            .candidates,
            vec![(key, 7)]
        );
        let new = t.value(key, true);
        t.state(key, old, true, 8, 0);
        assert!(t
            .walk(&Frustum {
                planes: None,
                min_y: 0.,
                max_y: 1.
            })
            .candidates
            .is_empty());
        t.state(key, new, true, 9, 0);
        t.remove(parent(key), false);
        t.state(key, new, true, 10, 0);
        assert!(t
            .walk(&Frustum {
                planes: None,
                min_y: 0.,
                max_y: 1.
            })
            .candidates
            .is_empty());
        assert_eq!(t.indices.len(), 2);
    }
    #[test]
    fn null_parent_does_not_cull_descendants_and_empty_is_not_drawable() {
        let mut t = Tree::new(7, 6, 256, [0, 0]);
        let key = encode(6, 0, 0);
        let token = t.value(key, true);
        t.state(key, token, true, 0, 0);
        let f = Frustum {
            planes: None,
            min_y: 0.,
            max_y: 1.,
        };
        assert!(t.walk(&f).candidates.is_empty());
        t.state(key, token, true, 4, 0);
        assert_eq!(t.walk(&f).candidates, vec![(key, 4)]);
        let root = parent(key);
        t.value(root, true);
        let mut planes = [0.; 24];
        for p in planes.chunks_mut(4) {
            p[3] = -1.;
        }
        assert!(t
            .walk(&Frustum {
                planes: Some(&planes),
                min_y: 0.,
                max_y: 1.
            })
            .candidates
            .is_empty());
    }
    #[test]
    fn old_container_close_cannot_invalidate_a_reused_generation() {
        let mut t = Tree::new(8, 6, 256, [0, 0]);
        let key = encode(6, 0, 0);
        let token = t.value(key, true);
        t.state(key, token, true, 7, 1);
        t.state(key, token, true, 7, 2);
        t.invalidate(key, token, 1);
        let f = Frustum {
            planes: None,
            min_y: 0.,
            max_y: 1.,
        };
        assert_eq!(t.walk(&f).candidates, vec![(key, 7)]);
        t.invalidate(key, token, 2);
        assert!(t.walk(&f).candidates.is_empty());
    }
}
