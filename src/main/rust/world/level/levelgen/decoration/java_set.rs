//! `java.util.HashSet<BlockPos>` with Java's iteration order: buckets in index
//! order, each bucket in its `next` order, resizing at three quarters of the
//! table with Java's order-preserving split. Tree code and vegetation patches
//! iterate these sets, so their order is observable.
//!
//! A bucket of nine keys in a table of at least 64 becomes a red-black tree
//! bin, as `HashMap.treeifyBin` does: iteration still follows `next`, but new
//! keys link in after their tree parent and the root moves to the bin's front.
//! The tree is emulated exactly while hashes differ; Java breaks equal-hash
//! ties with `System.identityHashCode`, which no emulation can reproduce.

/// `Vec3i.hashCode`: `(y + z * 31) * 31 + x`.
pub(crate) fn block_pos_hash([x, y, z]: [i32; 3]) -> i32 {
    y.wrapping_add(z.wrapping_mul(31)).wrapping_mul(31).wrapping_add(x)
}

/// `HashMap.hash`: the hash with its high half folded in (a signed int).
fn spread(hash: i32) -> i32 {
    hash ^ ((hash as u32) >> 16) as i32
}

const TREEIFY_THRESHOLD: usize = 8;
const UNTREEIFY_THRESHOLD: usize = 6;
const MIN_TREEIFY_CAPACITY: usize = 64;

#[derive(Clone, Copy)]
struct Node {
    pos: [i32; 3],
    hash: i32,
    parent: Option<usize>,
    left: Option<usize>,
    right: Option<usize>,
    red: bool,
}

/// A tree bin: its nodes, the `next` order (indices into `nodes`) and the root.
#[derive(Clone)]
struct Tree {
    nodes: Vec<Node>,
    order: Vec<usize>,
    root: usize,
}

#[derive(Clone)]
enum Bin {
    Chain(Vec<[i32; 3]>),
    Tree(Tree),
}

impl Bin {
    fn len(&self) -> usize {
        match self {
            Bin::Chain(chain) => chain.len(),
            Bin::Tree(tree) => tree.order.len(),
        }
    }

    fn contains(&self, pos: [i32; 3]) -> bool {
        match self {
            Bin::Chain(chain) => chain.contains(&pos),
            Bin::Tree(tree) => tree.order.iter().any(|&n| tree.nodes[n].pos == pos),
        }
    }

    fn keys(&self) -> Vec<[i32; 3]> {
        match self {
            Bin::Chain(chain) => chain.clone(),
            Bin::Tree(tree) => tree.order.iter().map(|&n| tree.nodes[n].pos).collect(),
        }
    }
}

impl Tree {
    /// `TreeNode.treeify` over keys in `next` order, then `moveRootToFront`.
    fn treeify(keys: &[[i32; 3]]) -> Self {
        let nodes: Vec<Node> = keys
            .iter()
            .map(|&pos| Node { pos, hash: spread(block_pos_hash(pos)), parent: None, left: None, right: None, red: false })
            .collect();
        let mut tree = Tree { order: (0..nodes.len()).collect(), nodes, root: 0 };
        let mut root: Option<usize> = None;
        for x in 0..tree.nodes.len() {
            let Some(r) = root else {
                tree.nodes[x].red = false;
                root = Some(x);
                continue;
            };
            let h = tree.nodes[x].hash;
            let mut p = r;
            loop {
                let ph = tree.nodes[p].hash;
                // Equal hashes fall to `tieBreakOrder` (identity hashes): left.
                let dir = if ph > h { -1 } else if ph < h { 1 } else { -1 };
                let xp = p;
                let next = if dir <= 0 { tree.nodes[p].left } else { tree.nodes[p].right };
                match next {
                    Some(n) => p = n,
                    None => {
                        tree.nodes[x].parent = Some(xp);
                        if dir <= 0 {
                            tree.nodes[xp].left = Some(x);
                        } else {
                            tree.nodes[xp].right = Some(x);
                        }
                        root = Some(tree.balance_insertion(r, x));
                        break;
                    }
                }
            }
        }
        tree.root = root.unwrap_or(0);
        tree.move_root_to_front();
        tree
    }

    /// `putTreeVal` for a key known to be absent.
    fn put(&mut self, pos: [i32; 3]) {
        let h = spread(block_pos_hash(pos));
        let x = self.nodes.len();
        self.nodes.push(Node { pos, hash: h, parent: None, left: None, right: None, red: false });
        let mut p = self.root;
        loop {
            let ph = self.nodes[p].hash;
            let dir = if ph > h { -1 } else if ph < h { 1 } else { -1 };
            let xp = p;
            let next = if dir <= 0 { self.nodes[p].left } else { self.nodes[p].right };
            match next {
                Some(n) => p = n,
                None => {
                    if dir <= 0 {
                        self.nodes[xp].left = Some(x);
                    } else {
                        self.nodes[xp].right = Some(x);
                    }
                    self.nodes[x].parent = Some(xp);
                    // `xp.next = x`: the new key follows its parent.
                    let at = self.order.iter().position(|&n| n == xp).expect("parent is in the bin");
                    self.order.insert(at + 1, x);
                    self.root = self.balance_insertion(self.root, x);
                    self.move_root_to_front();
                    return;
                }
            }
        }
    }

    fn move_root_to_front(&mut self) {
        if self.order.first() != Some(&self.root) {
            let at = self.order.iter().position(|&n| n == self.root).expect("root is in the bin");
            self.order.remove(at);
            self.order.insert(0, self.root);
        }
    }

    fn rotate_left(&mut self, mut root: usize, p: usize) -> usize {
        let Some(r) = self.nodes[p].right else { return root };
        let rl = self.nodes[r].left;
        self.nodes[p].right = rl;
        if let Some(rl) = rl {
            self.nodes[rl].parent = Some(p);
        }
        let pp = self.nodes[p].parent;
        self.nodes[r].parent = pp;
        match pp {
            None => {
                root = r;
                self.nodes[r].red = false;
            }
            Some(pp) if self.nodes[pp].left == Some(p) => self.nodes[pp].left = Some(r),
            Some(pp) => self.nodes[pp].right = Some(r),
        }
        self.nodes[r].left = Some(p);
        self.nodes[p].parent = Some(r);
        root
    }

    fn rotate_right(&mut self, mut root: usize, p: usize) -> usize {
        let Some(l) = self.nodes[p].left else { return root };
        let lr = self.nodes[l].right;
        self.nodes[p].left = lr;
        if let Some(lr) = lr {
            self.nodes[lr].parent = Some(p);
        }
        let pp = self.nodes[p].parent;
        self.nodes[l].parent = pp;
        match pp {
            None => {
                root = l;
                self.nodes[l].red = false;
            }
            Some(pp) if self.nodes[pp].right == Some(p) => self.nodes[pp].right = Some(l),
            Some(pp) => self.nodes[pp].left = Some(l),
        }
        self.nodes[l].right = Some(p);
        self.nodes[p].parent = Some(l);
        root
    }

    /// `TreeNode.balanceInsertion`.
    fn balance_insertion(&mut self, mut root: usize, mut x: usize) -> usize {
        self.nodes[x].red = true;
        loop {
            let Some(mut xp) = self.nodes[x].parent else {
                self.nodes[x].red = false;
                return x;
            };
            if !self.nodes[xp].red {
                return root;
            }
            let Some(mut xpp) = self.nodes[xp].parent else {
                return root;
            };
            let xppl = self.nodes[xpp].left;
            if Some(xp) == xppl {
                let xppr = self.nodes[xpp].right;
                if let Some(xppr) = xppr.filter(|&n| self.nodes[n].red) {
                    self.nodes[xppr].red = false;
                    self.nodes[xp].red = false;
                    self.nodes[xpp].red = true;
                    x = xpp;
                } else {
                    let mut grand = Some(xpp);
                    if self.nodes[xp].right == Some(x) {
                        x = xp;
                        root = self.rotate_left(root, x);
                        let parent = self.nodes[x].parent;
                        grand = parent.and_then(|p| self.nodes[p].parent);
                        match parent {
                            Some(p) => xp = p,
                            None => return root,
                        }
                    }
                    self.nodes[xp].red = false;
                    if let Some(g) = grand {
                        xpp = g;
                        self.nodes[xpp].red = true;
                        root = self.rotate_right(root, xpp);
                    }
                }
            } else {
                if let Some(xppl) = xppl.filter(|&n| self.nodes[n].red) {
                    self.nodes[xppl].red = false;
                    self.nodes[xp].red = false;
                    self.nodes[xpp].red = true;
                    x = xpp;
                } else {
                    let mut grand = Some(xpp);
                    if self.nodes[xp].left == Some(x) {
                        x = xp;
                        root = self.rotate_right(root, x);
                        let parent = self.nodes[x].parent;
                        grand = parent.and_then(|p| self.nodes[p].parent);
                        match parent {
                            Some(p) => xp = p,
                            None => return root,
                        }
                    }
                    self.nodes[xp].red = false;
                    if let Some(g) = grand {
                        xpp = g;
                        self.nodes[xpp].red = true;
                        root = self.rotate_left(root, xpp);
                    }
                }
            }
        }
    }
}

#[derive(Clone)]
pub(crate) struct JavaHashSet {
    buckets: Vec<Bin>,
    len: usize,
    /// Whether any bin was ever a tree (its order outlives a later split).
    treeified: bool,
}

impl Default for JavaHashSet {
    fn default() -> Self {
        Self::new()
    }
}

impl JavaHashSet {
    /// `new HashSet<>()` / `Sets.newHashSet()`: a 16-bucket table on first insertion.
    pub(crate) fn new() -> Self {
        Self { buckets: Vec::new(), len: 0, treeified: false }
    }

    /// `new HashSet<>(collection)`: capacity `max(size / .75 + 1, 16)`, then `addAll` in order.
    pub(crate) fn from_ordered(items: &[[i32; 3]]) -> Self {
        let wanted = ((items.len() as f32 / 0.75f32) as usize + 1).max(16);
        let mut set = Self { buckets: vec![Bin::Chain(Vec::new()); wanted.next_power_of_two()], len: 0, treeified: false };
        for &item in items {
            set.insert(item);
        }
        set
    }

    pub(crate) fn len(&self) -> usize {
        self.len
    }

    pub(crate) fn is_empty(&self) -> bool {
        self.len == 0
    }

    fn index(&self, pos: [i32; 3]) -> usize {
        spread(block_pos_hash(pos)) as u32 as usize & (self.buckets.len() - 1)
    }

    pub(crate) fn contains(&self, pos: [i32; 3]) -> bool {
        !self.buckets.is_empty() && self.buckets[self.index(pos)].contains(pos)
    }

    /// `add`: false when already present (its position is kept).
    pub(crate) fn insert(&mut self, pos: [i32; 3]) -> bool {
        if self.buckets.is_empty() {
            self.buckets = vec![Bin::Chain(Vec::new()); 16];
        }
        let index = self.index(pos);
        if self.buckets[index].contains(pos) {
            return false;
        }
        let capacity = self.buckets.len();
        let treeify = match &mut self.buckets[index] {
            Bin::Tree(tree) => {
                tree.put(pos);
                None
            }
            Bin::Chain(chain) => {
                chain.push(pos);
                // `putVal` calls `treeifyBin` once a chain reaches nine keys.
                (chain.len() > TREEIFY_THRESHOLD).then(|| chain.clone())
            }
        };
        if let Some(keys) = treeify {
            if capacity < MIN_TREEIFY_CAPACITY {
                self.resize();
            } else {
                self.buckets[index] = Bin::Tree(Tree::treeify(&keys));
                self.treeified = true;
            }
        }
        self.len += 1;
        if self.len > self.buckets.len() * 3 / 4 {
            self.resize();
        }
        true
    }

    /// `HashMap.resize`: each bin splits into its low and high halves in
    /// `next` order; a tree half of six or fewer keys becomes a chain, and a
    /// larger one is rebuilt only when the other half is not empty.
    fn resize(&mut self) {
        let old = std::mem::take(&mut self.buckets);
        let old_capacity = old.len();
        let capacity = old_capacity * 2;
        self.buckets = vec![Bin::Chain(Vec::new()); capacity];
        for (index, bin) in old.into_iter().enumerate() {
            let keys = bin.keys();
            let (lo, hi): (Vec<_>, Vec<_>) =
                keys.iter().partition(|&&pos| spread(block_pos_hash(pos)) as u32 as usize & old_capacity == 0);
            match bin {
                Bin::Chain(_) => {
                    self.buckets[index] = Bin::Chain(lo);
                    self.buckets[index + old_capacity] = Bin::Chain(hi);
                }
                Bin::Tree(tree) => {
                    let whole = lo.is_empty() || hi.is_empty();
                    for (half, at) in [(lo, index), (hi, index + old_capacity)] {
                        if half.is_empty() {
                            continue;
                        }
                        self.buckets[at] = if half.len() <= UNTREEIFY_THRESHOLD {
                            Bin::Chain(half)
                        } else if whole {
                            Bin::Tree(tree.clone())
                        } else {
                            Bin::Tree(Tree::treeify(&half))
                        };
                    }
                }
            }
        }
    }

    /// Removal keeps the remaining keys in `next` order. A tree bin is
    /// rebuilt from that order, which approximates `removeTreeNode`; no
    /// production caller removes keys.
    pub(crate) fn remove(&mut self, pos: [i32; 3]) -> bool {
        if self.buckets.is_empty() {
            return false;
        }
        let index = self.index(pos);
        let mut keys = self.buckets[index].keys();
        let Some(at) = keys.iter().position(|&p| p == pos) else {
            return false;
        };
        keys.remove(at);
        self.buckets[index] = match &self.buckets[index] {
            Bin::Tree(_) if keys.len() > UNTREEIFY_THRESHOLD => Bin::Tree(Tree::treeify(&keys)),
            _ => Bin::Chain(keys),
        };
        self.len -= 1;
        true
    }

    /// The first key in iteration order.
    pub(crate) fn first(&self) -> Option<[i32; 3]> {
        self.iter().next()
    }

    pub(crate) fn iter(&self) -> impl Iterator<Item = [i32; 3]> + '_ {
        self.buckets.iter().flat_map(|bin| -> Box<dyn Iterator<Item = [i32; 3]> + '_> {
            match bin {
                Bin::Chain(chain) => Box::new(chain.iter().copied()),
                Bin::Tree(tree) => Box::new(tree.order.iter().map(|&n| tree.nodes[n].pos)),
            }
        })
    }

    pub(crate) fn to_vec(&self) -> Vec<[i32; 3]> {
        self.iter().collect()
    }

    #[cfg(test)]
    pub(crate) fn ever_treeified(&self) -> bool {
        self.treeified
    }
}
