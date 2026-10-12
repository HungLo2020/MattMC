//! Threaded section-key AVL index with live forward-node cursors.
//! Adapted from fastutil 8.5.12 LongAVLTreeSet (Apache-2.0),
//! Copyright (C) 2002-2023 Sebastiano Vigna.
//! https://www.apache.org/licenses/LICENSE-2.0
//!
//! Thread links are arena indices, not owning Arc cycles. Retired nodes remain
//! reachable through active cursor heads and are swept after mutations/releases.
#[derive(Clone, Copy)]
struct Node {
    key: i64,
    left: usize,
    right: usize,
    pred: bool,
    succ: bool,
    balance: i8,
}
impl Default for Node {
    fn default() -> Self {
        Self {
            key: 0,
            left: 0,
            right: 0,
            pred: true,
            succ: true,
            balance: 0,
        }
    }
}

pub(super) struct SectionTree {
    nodes: Vec<Node>,
    free: Vec<usize>,
    retired: Vec<usize>,
    root: usize,
    first: usize,
    last: usize,
    pub(super) count: i32,
}
impl Default for SectionTree {
    fn default() -> Self {
        Self {
            nodes: vec![Node::default()],
            free: Vec::new(),
            retired: Vec::new(),
            root: 0,
            first: 0,
            last: 0,
            count: 0,
        }
    }
}
impl SectionTree {
    fn allocate(&mut self, key: i64) -> usize {
        let id = self.free.pop().unwrap_or_else(|| {
            self.nodes.push(Node::default());
            self.nodes.len() - 1
        });
        self.nodes[id] = Node {
            key,
            ..Node::default()
        };
        id
    }
    fn left(&self, p: usize) -> usize {
        if self.nodes[p].pred {
            0
        } else {
            self.nodes[p].left
        }
    }
    fn right(&self, p: usize) -> usize {
        if self.nodes[p].succ {
            0
        } else {
            self.nodes[p].right
        }
    }
    fn predecessor(&mut self, p: usize, link: usize) {
        self.nodes[p].pred = true;
        self.nodes[p].left = link;
    }
    fn successor(&mut self, p: usize, link: usize) {
        self.nodes[p].succ = true;
        self.nodes[p].right = link;
    }
    fn child_left(&mut self, p: usize, link: usize) {
        self.nodes[p].pred = false;
        self.nodes[p].left = link;
    }
    fn child_right(&mut self, p: usize, link: usize) {
        self.nodes[p].succ = false;
        self.nodes[p].right = link;
    }
    pub(super) fn key(&self, p: usize) -> i64 {
        self.nodes[p].key
    }
    pub(super) fn next(&self, p: usize) -> usize {
        let mut next = self.nodes[p].right;
        if !self.nodes[p].succ {
            while !self.nodes[next].pred {
                next = self.nodes[next].left;
            }
        }
        next
    }
    fn previous(&self, p: usize) -> usize {
        let mut previous = self.nodes[p].left;
        if !self.nodes[p].pred {
            while !self.nodes[previous].succ {
                previous = self.nodes[previous].right;
            }
        }
        previous
    }
    pub(super) fn first_in(&self, from: i64, to: i64) -> usize {
        let mut p = self.root;
        let mut found = 0;
        while p != 0 {
            found = p;
            if from == self.nodes[p].key {
                break;
            }
            p = if from < self.nodes[p].key {
                self.left(p)
            } else {
                self.right(p)
            };
        }
        if found != 0 && self.nodes[found].key < from {
            found = self.next(found);
        }
        if found != 0 && self.nodes[found].key >= to {
            0
        } else {
            found
        }
    }
    fn replace_child(&mut self, parent: usize, right: bool, replacement: usize) {
        if parent == 0 {
            self.root = replacement;
        } else if right {
            self.nodes[parent].right = replacement;
        } else {
            self.nodes[parent].left = replacement;
        }
    }
    pub(super) fn add(&mut self, key: i64) -> bool {
        if self.root == 0 {
            let id = self.allocate(key);
            self.root = id;
            self.first = id;
            self.last = id;
            self.count += 1;
            return true;
        }
        let mut p = self.root;
        let mut q = 0;
        let mut y = self.root;
        let mut z = 0;
        let mut directions = [false; 48];
        let mut i = 0;
        let e;
        loop {
            if key == self.nodes[p].key {
                return false;
            }
            if self.nodes[p].balance != 0 {
                i = 0;
                z = q;
                y = p;
            }
            let right = key > self.nodes[p].key;
            directions[i] = right;
            i += 1;
            if right {
                if self.nodes[p].succ {
                    e = self.allocate(key);
                    self.count += 1;
                    if self.nodes[p].right == 0 {
                        self.last = e;
                    }
                    self.nodes[e].left = p;
                    self.nodes[e].right = self.nodes[p].right;
                    self.child_right(p, e);
                    break;
                }
                q = p;
                p = self.nodes[p].right;
            } else {
                if self.nodes[p].pred {
                    e = self.allocate(key);
                    self.count += 1;
                    if self.nodes[p].left == 0 {
                        self.first = e;
                    }
                    self.nodes[e].right = p;
                    self.nodes[e].left = self.nodes[p].left;
                    self.child_left(p, e);
                    break;
                }
                q = p;
                p = self.nodes[p].left;
            }
        }
        p = y;
        i = 0;
        while p != e {
            if directions[i] {
                self.nodes[p].balance += 1;
            } else {
                self.nodes[p].balance -= 1;
            }
            p = if directions[i] {
                self.nodes[p].right
            } else {
                self.nodes[p].left
            };
            i += 1;
        }
        let w;
        if self.nodes[y].balance == -2 {
            let x = self.nodes[y].left;
            if self.nodes[x].balance == -1 {
                w = x;
                if self.nodes[x].succ {
                    self.nodes[x].succ = false;
                    self.predecessor(y, x);
                } else {
                    self.nodes[y].left = self.nodes[x].right;
                }
                self.nodes[x].right = y;
                self.nodes[x].balance = 0;
                self.nodes[y].balance = 0;
            } else {
                assert_eq!(self.nodes[x].balance, 1);
                w = self.nodes[x].right;
                self.nodes[x].right = self.nodes[w].left;
                self.nodes[w].left = x;
                self.nodes[y].left = self.nodes[w].right;
                self.nodes[w].right = y;
                match self.nodes[w].balance {
                    -1 => {
                        self.nodes[x].balance = 0;
                        self.nodes[y].balance = 1;
                    }
                    0 => {
                        self.nodes[x].balance = 0;
                        self.nodes[y].balance = 0;
                    }
                    _ => {
                        self.nodes[x].balance = -1;
                        self.nodes[y].balance = 0;
                    }
                }
                self.nodes[w].balance = 0;
                if self.nodes[w].pred {
                    self.successor(x, w);
                    self.nodes[w].pred = false;
                }
                if self.nodes[w].succ {
                    self.predecessor(y, w);
                    self.nodes[w].succ = false;
                }
            }
        } else if self.nodes[y].balance == 2 {
            let x = self.nodes[y].right;
            if self.nodes[x].balance == 1 {
                w = x;
                if self.nodes[x].pred {
                    self.nodes[x].pred = false;
                    self.successor(y, x);
                } else {
                    self.nodes[y].right = self.nodes[x].left;
                }
                self.nodes[x].left = y;
                self.nodes[x].balance = 0;
                self.nodes[y].balance = 0;
            } else {
                assert_eq!(self.nodes[x].balance, -1);
                w = self.nodes[x].left;
                self.nodes[x].left = self.nodes[w].right;
                self.nodes[w].right = x;
                self.nodes[y].right = self.nodes[w].left;
                self.nodes[w].left = y;
                match self.nodes[w].balance {
                    1 => {
                        self.nodes[x].balance = 0;
                        self.nodes[y].balance = -1;
                    }
                    0 => {
                        self.nodes[x].balance = 0;
                        self.nodes[y].balance = 0;
                    }
                    _ => {
                        self.nodes[x].balance = 1;
                        self.nodes[y].balance = 0;
                    }
                }
                self.nodes[w].balance = 0;
                if self.nodes[w].pred {
                    self.successor(y, w);
                    self.nodes[w].pred = false;
                }
                if self.nodes[w].succ {
                    self.predecessor(x, w);
                    self.nodes[w].succ = false;
                }
            }
        } else {
            return true;
        }
        self.replace_child(z, z != 0 && self.nodes[z].left != y, w);
        true
    }
    fn parent(&self, e: usize) -> usize {
        if e == self.root {
            return 0;
        }
        let mut x = e;
        let mut y = e;
        loop {
            if self.nodes[y].succ {
                let mut p = self.nodes[y].right;
                if p == 0 || self.nodes[p].left != e {
                    while !self.nodes[x].pred {
                        x = self.nodes[x].left;
                    }
                    p = self.nodes[x].left;
                }
                return p;
            } else if self.nodes[x].pred {
                let mut p = self.nodes[x].left;
                if p == 0 || self.nodes[p].right != e {
                    while !self.nodes[y].succ {
                        y = self.nodes[y].right;
                    }
                    p = self.nodes[y].right;
                }
                return p;
            }
            x = self.nodes[x].left;
            y = self.nodes[y].right;
        }
    }
    pub(super) fn remove(&mut self, key: i64) -> bool {
        if self.root == 0 {
            return false;
        }
        let mut p = self.root;
        let mut q = 0;
        let mut dir = false;
        loop {
            if key == self.nodes[p].key {
                break;
            }
            dir = key > self.nodes[p].key;
            q = p;
            p = if dir { self.right(p) } else { self.left(p) };
            if p == 0 {
                return false;
            }
        }
        if self.nodes[p].left == 0 {
            self.first = self.next(p);
        }
        if self.nodes[p].right == 0 {
            self.last = self.previous(p);
        }
        if self.nodes[p].succ {
            if self.nodes[p].pred {
                if q != 0 {
                    if dir {
                        self.successor(q, self.nodes[p].right);
                    } else {
                        self.predecessor(q, self.nodes[p].left);
                    }
                } else {
                    self.root = if dir {
                        self.nodes[p].right
                    } else {
                        self.nodes[p].left
                    };
                }
            } else {
                let prev = self.previous(p);
                self.nodes[prev].right = self.nodes[p].right;
                self.replace_child(q, dir, self.nodes[p].left);
            }
        } else {
            let mut r = self.nodes[p].right;
            if self.nodes[r].pred {
                self.nodes[r].left = self.nodes[p].left;
                self.nodes[r].pred = self.nodes[p].pred;
                if !self.nodes[r].pred {
                    let prev = self.previous(r);
                    self.nodes[prev].right = r;
                }
                self.replace_child(q, dir, r);
                self.nodes[r].balance = self.nodes[p].balance;
                q = r;
                dir = true;
            } else {
                let s;
                loop {
                    let candidate = self.nodes[r].left;
                    if self.nodes[candidate].pred {
                        s = candidate;
                        break;
                    }
                    r = candidate;
                }
                if self.nodes[s].succ {
                    self.predecessor(r, s);
                } else {
                    self.nodes[r].left = self.nodes[s].right;
                }
                self.nodes[s].left = self.nodes[p].left;
                if !self.nodes[p].pred {
                    let prev = self.previous(p);
                    self.nodes[prev].right = s;
                    self.nodes[s].pred = false;
                }
                self.nodes[s].right = self.nodes[p].right;
                self.nodes[s].succ = false;
                self.replace_child(q, dir, s);
                self.nodes[s].balance = self.nodes[p].balance;
                q = r;
                dir = false;
            }
        }
        while q != 0 {
            let y = q;
            q = self.parent(y);
            if !dir {
                dir = q != 0 && self.nodes[q].left != y;
                self.nodes[y].balance += 1;
                if self.nodes[y].balance == 1 {
                    break;
                }
                if self.nodes[y].balance == 2 {
                    let x = self.nodes[y].right;
                    assert_ne!(x, 0);
                    if self.nodes[x].balance == -1 {
                        let w = self.nodes[x].left;
                        self.nodes[x].left = self.nodes[w].right;
                        self.nodes[w].right = x;
                        self.nodes[y].right = self.nodes[w].left;
                        self.nodes[w].left = y;
                        match self.nodes[w].balance {
                            1 => {
                                self.nodes[x].balance = 0;
                                self.nodes[y].balance = -1;
                            }
                            0 => {
                                self.nodes[x].balance = 0;
                                self.nodes[y].balance = 0;
                            }
                            _ => {
                                self.nodes[x].balance = 1;
                                self.nodes[y].balance = 0;
                            }
                        }
                        self.nodes[w].balance = 0;
                        if self.nodes[w].pred {
                            self.successor(y, w);
                            self.nodes[w].pred = false;
                        }
                        if self.nodes[w].succ {
                            self.predecessor(x, w);
                            self.nodes[w].succ = false;
                        }
                        self.replace_child(q, dir, w);
                    } else {
                        self.replace_child(q, dir, x);
                        if self.nodes[x].balance == 0 {
                            self.nodes[y].right = self.nodes[x].left;
                            self.nodes[x].left = y;
                            self.nodes[x].balance = -1;
                            self.nodes[y].balance = 1;
                            break;
                        }
                        assert_eq!(self.nodes[x].balance, 1);
                        if self.nodes[x].pred {
                            self.nodes[y].succ = true;
                            self.nodes[x].pred = false;
                        } else {
                            self.nodes[y].right = self.nodes[x].left;
                        }
                        self.nodes[x].left = y;
                        self.nodes[y].balance = 0;
                        self.nodes[x].balance = 0;
                    }
                }
            } else {
                dir = q != 0 && self.nodes[q].left != y;
                self.nodes[y].balance -= 1;
                if self.nodes[y].balance == -1 {
                    break;
                }
                if self.nodes[y].balance == -2 {
                    let x = self.nodes[y].left;
                    assert_ne!(x, 0);
                    if self.nodes[x].balance == 1 {
                        let w = self.nodes[x].right;
                        self.nodes[x].right = self.nodes[w].left;
                        self.nodes[w].left = x;
                        self.nodes[y].left = self.nodes[w].right;
                        self.nodes[w].right = y;
                        match self.nodes[w].balance {
                            -1 => {
                                self.nodes[x].balance = 0;
                                self.nodes[y].balance = 1;
                            }
                            0 => {
                                self.nodes[x].balance = 0;
                                self.nodes[y].balance = 0;
                            }
                            _ => {
                                self.nodes[x].balance = -1;
                                self.nodes[y].balance = 0;
                            }
                        }
                        self.nodes[w].balance = 0;
                        if self.nodes[w].pred {
                            self.successor(x, w);
                            self.nodes[w].pred = false;
                        }
                        if self.nodes[w].succ {
                            self.predecessor(y, w);
                            self.nodes[w].succ = false;
                        }
                        self.replace_child(q, dir, w);
                    } else {
                        self.replace_child(q, dir, x);
                        if self.nodes[x].balance == 0 {
                            self.nodes[y].left = self.nodes[x].right;
                            self.nodes[x].right = y;
                            self.nodes[x].balance = 1;
                            self.nodes[y].balance = -1;
                            break;
                        }
                        assert_eq!(self.nodes[x].balance, -1);
                        if self.nodes[x].succ {
                            self.nodes[y].pred = true;
                            self.nodes[x].succ = false;
                        } else {
                            self.nodes[y].left = self.nodes[x].right;
                        }
                        self.nodes[x].right = y;
                        self.nodes[y].balance = 0;
                        self.nodes[x].balance = 0;
                    }
                }
            }
        }
        self.count -= 1;
        self.retired.push(p);
        true
    }
    pub(super) fn needs_sweep(&self) -> bool {
        !self.retired.is_empty()
    }

    /// Only active forward heads can observe detached nodes. Follow both
    /// child/thread links to keep exactly their reachable mutation state.
    pub(super) fn sweep(&mut self, heads: &[usize]) {
        if heads.iter().all(|&id| id == 0) {
            self.free.append(&mut self.retired);
            return;
        }
        if self.retired.is_empty() {
            return;
        }
        let mut marked = vec![false; self.nodes.len()];
        let mut stack = Vec::with_capacity(heads.len() + 1);
        stack.extend_from_slice(heads);
        stack.push(self.root);
        while let Some(id) = stack.pop() {
            if id == 0 || marked[id] {
                continue;
            }
            marked[id] = true;
            stack.push(self.nodes[id].left);
            stack.push(self.nodes[id].right);
        }
        self.retired.retain(|&id| {
            if marked[id] {
                true
            } else {
                self.free.push(id);
                false
            }
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeSet;
    #[test]
    fn randomized_mutations_keep_threading_balance_and_order() {
        let mut tree = SectionTree::default();
        let mut expected = BTreeSet::new();
        let mut rng = 19u64;
        for _ in 0..20_000 {
            rng = rng.wrapping_mul(6364136223846793005).wrapping_add(1);
            let key = ((rng >> 24) % 257) as i64 - 128;
            if rng & 1 == 0 {
                assert_eq!(tree.add(key), expected.insert(key));
            } else {
                assert_eq!(tree.remove(key), expected.remove(&key));
                tree.sweep(&[]);
            }
            assert_eq!(tree.count as usize, expected.len());
            let mut actual = Vec::new();
            let mut p = tree.first;
            while p != 0 {
                actual.push(tree.key(p));
                p = tree.next(p);
                assert!(actual.len() <= expected.len());
            }
            assert_eq!(actual, expected.iter().copied().collect::<Vec<_>>());
            fn height(tree: &SectionTree, p: usize) -> i32 {
                if p == 0 {
                    return 0;
                }
                let left = height(tree, tree.left(p));
                let right = height(tree, tree.right(p));
                assert!((right - left).abs() <= 1);
                assert_eq!(right - left, i32::from(tree.nodes[p].balance));
                left.max(right) + 1
            }
            height(&tree, tree.root);
        }
        assert!(
            tree.nodes.len() < 300,
            "retired nodes reuse bounded arena slots"
        );
    }
    #[test]
    fn removed_prefetched_node_survives_until_cursor_advances() {
        let mut tree = SectionTree::default();
        for key in [2, 1, 3] {
            tree.add(key);
        }
        let head = tree.first_in(1, 4);
        assert_eq!(tree.key(head), 1);
        tree.remove(1);
        tree.sweep(&[head]);
        assert!(tree.retired.contains(&head));
        assert_eq!(tree.key(head), 1);
        let next = tree.next(head);
        assert_eq!(tree.key(next), 2);
        tree.sweep(&[next]);
        assert!(!tree.retired.contains(&head));
    }
}
