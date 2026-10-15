//! `JigsawStructure.findGenerationPoint` and `JigsawPlacement.addPieces`:
//! the start piece, its height and the breadth-first jigsaw assembly over a
//! structure's pools and templates, which Java exports once per structure.
//! Free space is a box minus placed boxes (the voxel shapes Java builds are
//! integer boxes, so `joinIsNotEmpty(free, box.deflate(0.25), ONLY_SECOND)`
//! is "every cell of the box is free"). Java builds the piece objects from the
//! words this writes.
use crate::world::level::levelgen::random::Legacy;
use std::collections::HashMap;

/// `Direction.get3DDataValue()` order.
const DOWN: u8 = 0;
const UP: u8 = 1;
const STEP: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

fn opposite(d: u8) -> u8 {
    d ^ 1
}

/// `Rotation.rotate(direction)` by rotation ordinal (none, clockwise 90, 180, counterclockwise 90).
fn rotate(d: u8, rotation: u8) -> u8 {
    if d <= UP {
        return d;
    }
    // North, east, south, west.
    const RING: [u8; 4] = [2, 5, 3, 4];
    let at = RING.iter().position(|&r| r == d).unwrap_or(0);
    RING[(at + rotation as usize) % 4]
}

/// `StructureTemplate.transform(pos, NONE, rotation, ZERO)`.
fn transform([i, j, k]: [i32; 3], rotation: u8) -> [i32; 3] {
    match rotation {
        1 => [-k, j, i],
        2 => [-i, j, -k],
        3 => [k, j, -i],
        _ => [i, j, k],
    }
}

/// `BoundingBox`, inclusive.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Box3 {
    pub min: [i32; 3],
    pub max: [i32; 3],
}

impl Box3 {
    fn from_corners(a: [i32; 3], b: [i32; 3]) -> Self {
        Box3 { min: [a[0].min(b[0]), a[1].min(b[1]), a[2].min(b[2])], max: [a[0].max(b[0]), a[1].max(b[1]), a[2].max(b[2])] }
    }
    fn moved(self, d: [i32; 3]) -> Self {
        Box3 { min: [self.min[0] + d[0], self.min[1] + d[1], self.min[2] + d[2]], max: [self.max[0] + d[0], self.max[1] + d[1], self.max[2] + d[2]] }
    }
    fn inside(&self, p: [i32; 3]) -> bool {
        (0..3).all(|a| p[a] >= self.min[a] && p[a] <= self.max[a])
    }
    fn y_span(&self) -> i32 {
        self.max[1] - self.min[1] + 1
    }
    fn contains(&self, other: &Box3) -> bool {
        (0..3).all(|a| other.min[a] >= self.min[a] && other.max[a] <= self.max[a])
    }
    fn overlaps(&self, other: &Box3) -> bool {
        (0..3).all(|a| other.min[a] <= self.max[a] && other.max[a] >= self.min[a])
    }
    fn encapsulate(&mut self, other: &Box3) {
        for a in 0..3 {
            self.min[a] = self.min[a].min(other.min[a]);
            self.max[a] = self.max[a].max(other.max[a]);
        }
    }
}

/// Free space: a box minus the boxes placed in it (each in whole cells).
#[derive(Clone, Debug)]
struct Space {
    base: Box3,
    removed: Vec<Box3>,
}

impl Space {
    fn fits(&self, b: &Box3) -> bool {
        self.base.contains(b) && !self.removed.iter().any(|r| r.overlaps(b))
    }
}

#[derive(Clone, Debug)]
struct Jigsaw {
    pos: [i32; 3],
    front: u8,
    top: u8,
    rollable: bool,
    name: u32,
    pool: u32,
    target: u32,
    placement: i32,
    selection: i32,
}

struct Template {
    size: [i32; 3],
    palettes: Vec<Vec<Jigsaw>>,
}

#[derive(Clone, Debug)]
enum Kind {
    Empty,
    Single(usize),
    Feature,
    List(Vec<usize>),
}

struct Element {
    kind: Kind,
    rigid: bool,
}

struct Pool {
    /// The weighted list expanded: each element repeated by its weight.
    templates: Vec<usize>,
    fallback: Option<usize>,
    empty_key: bool,
    max_size: i32,
}

enum Alias {
    Direct(u32, u32),
    /// Alias, then (target, weight) pairs.
    Random(u32, Vec<(u32, i32)>),
    /// Groups of bindings with weights.
    Group(Vec<(Vec<Alias>, i32)>),
}

/// One structure's pools and templates, by Java's interned key and name IDs.
pub(crate) struct Data {
    templates: Vec<Template>,
    elements: Vec<Element>,
    pools: Vec<Pool>,
    pool_by_key: HashMap<u32, usize>,
    aliases: Vec<Alias>,
    /// `FeaturePoolElement`'s default jigsaw: name `minecraft:bottom`, target `minecraft:empty`, pool `minecraft:empty`.
    bottom: u32,
    empty_id: u32,
    empty_pool: u32,
}

struct Reader<'a> {
    words: &'a [i32],
    at: usize,
}

impl Reader<'_> {
    fn next(&mut self) -> Option<i32> {
        let w = *self.words.get(self.at)?;
        self.at += 1;
        Some(w)
    }
    fn count(&mut self) -> Option<usize> {
        usize::try_from(self.next()?).ok().filter(|&n| n < 1 << 22)
    }
}

fn read_aliases(r: &mut Reader, depth: u32) -> Option<Vec<Alias>> {
    let n = r.count()?;
    let mut out = Vec::with_capacity(n);
    for _ in 0..n {
        out.push(match r.next()? {
            0 => Alias::Direct(r.next()? as u32, r.next()? as u32),
            1 => {
                let alias = r.next()? as u32;
                let targets = r.count()?;
                let mut list = Vec::with_capacity(targets);
                for _ in 0..targets {
                    list.push((r.next()? as u32, r.next()?));
                }
                Alias::Random(alias, list)
            }
            2 if depth < 4 => {
                let groups = r.count()?;
                let mut list = Vec::with_capacity(groups);
                for _ in 0..groups {
                    let bindings = read_aliases(r, depth + 1)?;
                    list.push((bindings, r.next()?));
                }
                Alias::Group(list)
            }
            _ => return None,
        });
    }
    Some(out)
}

impl Data {
    /// Words: `[bottom, empty id, empty pool, templates, per template: size (3),
    /// palettes, per palette: jigsaws, per jigsaw: x, y, z, front, top, rollable,
    /// name, pool, target, placement, selection; elements, per element: kind (0
    /// empty, 1 single, 2 feature, 3 list), rigid, then a template index or list
    /// count and element indices; pools, per pool: key, empty key, fallback (-1),
    /// count, element indices; aliases]`. Pools and elements reference only
    /// earlier-declared indices or valid ones (checked).
    pub(crate) fn decode(words: &[i32]) -> Option<Self> {
        let mut r = Reader { words, at: 0 };
        let (bottom, empty_id, empty_pool) = (r.next()? as u32, r.next()? as u32, r.next()? as u32);
        let templates_n = r.count()?;
        let mut templates = Vec::with_capacity(templates_n);
        for _ in 0..templates_n {
            let size = [r.next()?, r.next()?, r.next()?];
            let palettes_n = r.count()?;
            let mut palettes = Vec::with_capacity(palettes_n);
            for _ in 0..palettes_n {
                let n = r.count()?;
                let mut list = Vec::with_capacity(n);
                for _ in 0..n {
                    let pos = [r.next()?, r.next()?, r.next()?];
                    let (front, top) = (u8::try_from(r.next()?).ok().filter(|&d| d < 6)?, u8::try_from(r.next()?).ok().filter(|&d| d < 6)?);
                    list.push(Jigsaw {
                        pos,
                        front,
                        top,
                        rollable: r.next()? != 0,
                        name: r.next()? as u32,
                        pool: r.next()? as u32,
                        target: r.next()? as u32,
                        placement: r.next()?,
                        selection: r.next()?,
                    });
                }
                palettes.push(list);
            }
            templates.push(Template { size, palettes });
        }
        let elements_n = r.count()?;
        let mut elements = Vec::with_capacity(elements_n);
        for _ in 0..elements_n {
            let kind = r.next()?;
            let rigid = r.next()? != 0;
            let kind = match kind {
                0 => Kind::Empty,
                1 => Kind::Single(r.count().filter(|&t| t < templates.len())?),
                2 => Kind::Feature,
                3 => {
                    let n = r.count()?;
                    let mut list = Vec::with_capacity(n);
                    for _ in 0..n {
                        list.push(r.count().filter(|&e| e < elements_n)?);
                    }
                    if list.is_empty() {
                        return None;
                    }
                    Kind::List(list)
                }
                _ => return None,
            };
            elements.push(Element { kind, rigid });
        }
        let pools_n = r.count()?;
        let mut pools = Vec::with_capacity(pools_n);
        let mut pool_by_key = HashMap::with_capacity(pools_n);
        for index in 0..pools_n {
            let key = r.next()? as u32;
            let empty_key = r.next()? != 0;
            let fallback = match r.next()? {
                -1 => None,
                f => Some(usize::try_from(f).ok().filter(|&f| f < pools_n)?),
            };
            let n = r.count()?;
            let mut list = Vec::with_capacity(n);
            for _ in 0..n {
                list.push(r.count().filter(|&e| e < elements_n)?);
            }
            pool_by_key.insert(key, index);
            pools.push(Pool { templates: list, fallback, empty_key, max_size: 0 });
        }
        let aliases = read_aliases(&mut r, 0)?;
        if r.at != words.len() {
            return None;
        }
        let mut data = Data { templates, elements, pools, pool_by_key, aliases, bottom, empty_id, empty_pool };
        // getMaxSize: the tallest non-empty element's box at the origin, unrotated.
        for p in 0..data.pools.len() {
            let mut max = 0;
            for &e in &data.pools[p].templates {
                if !matches!(data.elements[e].kind, Kind::Empty) {
                    max = max.max(data.bounding_box(e, [0, 0, 0], 0)?.y_span());
                }
            }
            data.pools[p].max_size = max;
        }
        Some(data)
    }

    /// `StructurePoolElement.getBoundingBox(manager, pos, rotation)`.
    fn bounding_box(&self, element: usize, pos: [i32; 3], rotation: u8) -> Option<Box3> {
        match &self.elements[element].kind {
            Kind::Single(t) => {
                let size = self.templates[*t].size;
                let a = transform([0, 0, 0], rotation);
                let b = transform([size[0] - 1, size[1] - 1, size[2] - 1], rotation);
                Some(Box3::from_corners(a, b).moved(pos))
            }
            Kind::Feature => Some(Box3 { min: pos, max: pos }),
            Kind::List(list) => {
                let mut out: Option<Box3> = None;
                for &e in list {
                    let b = self.bounding_box(e, pos, rotation)?;
                    match &mut out {
                        Some(o) => o.encapsulate(&b),
                        None => out = Some(b),
                    }
                }
                out
            }
            Kind::Empty => None,
        }
    }

    /// `getShuffledJigsawBlocks(manager, pos, rotation, random)`.
    fn shuffled_jigsaws(&self, element: usize, pos: [i32; 3], rotation: u8, random: &mut Legacy) -> Vec<Jigsaw> {
        match &self.elements[element].kind {
            Kind::Single(t) => {
                let template = &self.templates[*t];
                if template.palettes.is_empty() {
                    return Vec::new();
                }
                // getRandomPalette(palettes, pos): a fresh source seeded by the position.
                let palette = if template.palettes.len() == 1 {
                    0
                } else {
                    Legacy::new(crate::world::level::levelgen::random::position_seed(pos[0], pos[1], pos[2])).next_int_bound(template.palettes.len() as i32)
                        as usize
                };
                let mut list: Vec<Jigsaw> = template.palettes[palette]
                    .iter()
                    .map(|j| {
                        let p = transform(j.pos, rotation);
                        Jigsaw { pos: [p[0] + pos[0], p[1] + pos[1], p[2] + pos[2]], front: rotate(j.front, rotation), top: rotate(j.top, rotation), ..j.clone() }
                    })
                    .collect();
                shuffle(&mut list, random);
                // sortBySelectionPriority: stable, highest first.
                list.sort_by(|a, b| b.selection.cmp(&a.selection));
                list
            }
            Kind::Feature => vec![Jigsaw {
                pos,
                front: DOWN,
                top: 3,
                rollable: true,
                name: self.bottom,
                pool: self.empty_pool,
                target: self.empty_id,
                placement: 0,
                selection: 0,
            }],
            Kind::List(list) => self.shuffled_jigsaws(list[0], pos, rotation, random),
            Kind::Empty => Vec::new(),
        }
    }
}

/// `Util.shuffle(list, random)`.
fn shuffle<T>(list: &mut [T], random: &mut Legacy) {
    for j in (2..=list.len()).rev() {
        let k = random.next_int_bound(j as i32) as usize;
        list.swap(j - 1, k);
    }
}

/// `WeightedList.getRandom(random)`.
fn weighted<T: Copy>(list: &[(T, i32)], random: &mut Legacy) -> Option<T> {
    let total: i32 = list.iter().map(|e| e.1).sum();
    if total <= 0 {
        return None;
    }
    let mut i = random.next_int_bound(total);
    for &(value, weight) in list {
        i -= weight;
        if i < 0 {
            return Some(value);
        }
    }
    None
}

fn resolve(bindings: &[Alias], random: &mut Legacy, out: &mut Vec<(u32, u32)>) {
    for binding in bindings {
        match binding {
            Alias::Direct(a, t) => out.push((*a, *t)),
            Alias::Random(a, targets) => {
                if let Some(t) = weighted(targets, random) {
                    out.push((*a, t));
                }
            }
            Alias::Group(groups) => {
                let total: i32 = groups.iter().map(|g| g.1).sum();
                if total > 0 {
                    let mut i = random.next_int_bound(total);
                    for (bindings, weight) in groups {
                        i -= weight;
                        if i < 0 {
                            resolve(bindings, random, out);
                            break;
                        }
                    }
                }
            }
        }
    }
}

/// A placed piece: its element, position, ground level delta, rotation, box
/// and junctions `[x, ground y, z, delta y, rigid destination]`.
pub(crate) struct Piece {
    element: usize,
    pos: [i32; 3],
    ground: i32,
    rotation: u8,
    bbox: Box3,
    junctions: Vec<[i32; 5]>,
}

/// The structure's settings and the call's context.
pub(crate) struct Settings {
    pub start_pool: u32,
    /// The start jigsaw's name ID, or None.
    pub start_name: Option<u32>,
    pub max_depth: i32,
    pub expansion_hack: bool,
    /// Project the start to this heightmap (a height function index), or None.
    pub project: bool,
    pub max_horizontal: i32,
    pub max_vertical: i32,
    /// The padding is not `DimensionPadding.ZERO` (Java compares by identity).
    pub padding_check: bool,
    pub pad_bottom: i32,
    pub pad_top: i32,
    pub min_y: i32,
    pub max_y: i32,
    pub seed: i64,
}

pub(crate) enum Outcome {
    /// No stub: Java's `Optional.empty()`.
    None,
    /// The stub's position, and the pieces when its biome is valid.
    Stub([i32; 3], Vec<Piece>),
}

/// Heights and the biome check, from the column source and the climate tree.
pub(crate) trait World {
    /// `getFirstFreeHeight(x, z, WORLD_SURFACE_WG)`.
    fn surface(&mut self, x: i32, z: i32) -> Result<i32, ()>;
    /// `getFirstFreeHeight` on the start's projection heightmap.
    fn projection(&mut self, x: i32, z: i32) -> Result<i32, ()>;
    /// `isValidBiome` at the stub's position.
    fn valid_biome(&mut self, pos: [i32; 3]) -> Result<bool, ()>;
}

struct Placer<'d> {
    data: &'d Data,
    max_depth: i32,
    expansion_hack: bool,
    aliases: HashMap<u32, u32>,
    pieces: Vec<Piece>,
    spaces: Vec<Space>,
    /// `SequencedPriorityIterator`: per priority, a FIFO of (piece, space, depth).
    queue: std::collections::BTreeMap<i32, std::collections::VecDeque<(usize, usize, i32)>>,
}

impl Placer<'_> {
    fn lookup(&self, key: u32) -> u32 {
        *self.aliases.get(&key).unwrap_or(&key)
    }

    fn pool(&self, key: u32) -> Option<usize> {
        self.data.pool_by_key.get(&self.lookup(key)).copied()
    }

    fn try_children(&mut self, piece: usize, space: usize, depth: i32, random: &mut Legacy, world: &mut dyn World) -> Result<(), ()> {
        let data = self.data;
        let (element, pos, rotation, bbox) = {
            let p = &self.pieces[piece];
            (p.element, p.pos, p.rotation, p.bbox)
        };
        let rigid = data.elements[element].rigid;
        let mut inner: Option<usize> = None;
        let j = bbox.min[1];
        'jigsaws: for jigsaw in data.shuffled_jigsaws(element, pos, rotation, random) {
            let direction = jigsaw.front;
            let block2 = jigsaw.pos;
            let s = STEP[direction as usize];
            let block3 = [block2[0] + s[0], block2[1] + s[1], block2[2] + s[2]];
            let k = block2[1] - j;
            let mut l = i32::MIN;
            let Some(pool) = self.pool(jigsaw.pool) else { continue };
            let p = &data.pools[pool];
            if p.templates.is_empty() && !p.empty_key {
                continue;
            }
            // Every pool has a fallback holder; the export includes them all.
            let Some(fallback) = p.fallback else { return Err(()) };
            let f = &data.pools[fallback];
            if f.templates.is_empty() && !f.empty_key {
                continue;
            }
            let target_space = if bbox.inside(block3) {
                *inner.get_or_insert_with(|| {
                    self.spaces.push(Space { base: bbox, removed: Vec::new() });
                    self.spaces.len() - 1
                })
            } else {
                space
            };
            let mut list = Vec::new();
            if depth != self.max_depth {
                let mut templates = p.templates.clone();
                shuffle(&mut templates, random);
                list.extend(templates);
            }
            let mut templates = f.templates.clone();
            shuffle(&mut templates, random);
            list.extend(templates);
            let priority = jigsaw.placement;
            for element2 in list {
                if matches!(data.elements[element2].kind, Kind::Empty) {
                    break;
                }
                let mut rotations = [0u8, 1, 2, 3];
                shuffle(&mut rotations, random);
                for rotation2 in rotations {
                    let list2 = data.shuffled_jigsaws(element2, [0, 0, 0], rotation2, random);
                    let box2 = data.bounding_box(element2, [0, 0, 0], rotation2).ok_or(())?;
                    let n = if self.expansion_hack && box2.y_span() <= 16 {
                        list2
                            .iter()
                            .map(|j2| {
                                let s2 = STEP[j2.front as usize];
                                if !box2.inside([j2.pos[0] + s2[0], j2.pos[1] + s2[1], j2.pos[2] + s2[2]]) {
                                    return 0;
                                }
                                match self.pool(j2.pool) {
                                    Some(p2) => {
                                        let fb = data.pools[p2].fallback.map_or(0, |f| data.pools[f].max_size);
                                        data.pools[p2].max_size.max(fb)
                                    }
                                    None => 0,
                                }
                            })
                            .max()
                            .unwrap_or(0)
                    } else {
                        0
                    };
                    for jigsaw2 in &list2 {
                        // JigsawBlock.canAttach.
                        if !(direction == opposite(jigsaw2.front) && (jigsaw.rollable || jigsaw.top == jigsaw2.top) && jigsaw.target == jigsaw2.name) {
                            continue;
                        }
                        let block4 = jigsaw2.pos;
                        let block5 = [block3[0] - block4[0], block3[1] - block4[1], block3[2] - block4[2]];
                        let box3 = data.bounding_box(element2, block5, rotation2).ok_or(())?;
                        let o = box3.min[1];
                        let rigid2 = data.elements[element2].rigid;
                        let pp = block4[1];
                        let q = k - pp + STEP[direction as usize][1];
                        let r = if rigid && rigid2 {
                            j + q
                        } else {
                            if l == i32::MIN {
                                l = world.surface(block2[0], block2[2])?;
                            }
                            l - pp
                        };
                        let shift = r - o;
                        let mut box4 = box3.moved([0, shift, 0]);
                        let block6 = [block5[0], block5[1] + shift, block5[2]];
                        if n > 0 {
                            let t = (n + 1).max(box4.max[1] - box4.min[1]);
                            let corner = [box4.min[0], box4.min[1] + t, box4.min[2]];
                            box4.encapsulate(&Box3 { min: corner, max: corner });
                        }
                        if self.spaces[target_space].fits(&box4) {
                            self.spaces[target_space].removed.push(box4);
                            let t2 = self.pieces[piece].ground;
                            let u = if rigid2 { t2 - q } else { 1 };
                            let v = if rigid {
                                j + k
                            } else if rigid2 {
                                r + pp
                            } else {
                                if l == i32::MIN {
                                    l = world.surface(block2[0], block2[2])?;
                                }
                                l + q / 2
                            };
                            self.pieces[piece].junctions.push([block3[0], v - k + t2, block3[2], q, i32::from(rigid2)]);
                            self.pieces.push(Piece {
                                element: element2,
                                pos: block6,
                                ground: u,
                                rotation: rotation2,
                                bbox: box4,
                                junctions: vec![[block2[0], v - pp + u, block2[2], -q, i32::from(rigid)]],
                            });
                            if depth + 1 <= self.max_depth {
                                let child = self.pieces.len() - 1;
                                self.queue.entry(priority).or_default().push_back((child, target_space, depth + 1));
                            }
                            continue 'jigsaws;
                        }
                    }
                }
            }
        }
        Ok(())
    }

    fn next(&mut self) -> Option<(usize, usize, i32)> {
        let mut entry = self.queue.last_entry()?;
        let item = entry.get_mut().pop_front();
        if entry.get().is_empty() {
            entry.remove();
        }
        item
    }
}

/// `findGenerationPoint` then, for a valid biome, the pieces. `start_y` is the
/// sampled start height; `random` the generation context's random.
pub(crate) fn plan(data: &Data, s: &Settings, chunk: [i32; 2], start_y: i32, random: &mut Legacy, world: &mut dyn World) -> Result<Outcome, ()> {
    let block = [chunk[0] * 16, start_y, chunk[1] * 16];
    // PoolAliasLookup.create(aliases, blockPos, seed).
    let mut resolved = Vec::new();
    if !data.aliases.is_empty() {
        let mut alias_random = super::positional_legacy(s.seed, block[0], block[1], block[2]);
        resolve(&data.aliases, &mut alias_random, &mut resolved);
    }
    let aliases: HashMap<u32, u32> = resolved.into_iter().collect();
    let lookup = |key: u32| *aliases.get(&key).unwrap_or(&key);
    let rotation = random.next_int_bound(4) as u8;
    let Some(&start_pool) = data.pool_by_key.get(&lookup(s.start_pool)).or_else(|| data.pool_by_key.get(&s.start_pool)) else {
        return Err(());
    };
    let templates = &data.pools[start_pool].templates;
    if templates.is_empty() {
        return Ok(Outcome::None);
    }
    let element = templates[random.next_int_bound(templates.len() as i32) as usize];
    if matches!(data.elements[element].kind, Kind::Empty) {
        return Ok(Outcome::None);
    }
    let block2 = match s.start_name {
        Some(name) => match data.shuffled_jigsaws(element, block, rotation, random).into_iter().find(|j| j.name == name) {
            Some(j) => j.pos,
            None => return Ok(Outcome::None),
        },
        None => block,
    };
    let offset = [block2[0] - block[0], block2[1] - block[1], block2[2] - block[2]];
    let block3 = [block[0] - offset[0], block[1] - offset[1], block[2] - offset[2]];
    let bbox = data.bounding_box(element, block3, rotation).ok_or(())?;
    let (j, k) = ((bbox.max[0] + bbox.min[0]) / 2, (bbox.max[2] + bbox.min[2]) / 2);
    let l = if s.project { block[1] + world.projection(j, k)? } else { block3[1] };
    let m = bbox.min[1] + 1;
    let bbox = bbox.moved([0, l - m, 0]);
    let pos = [block3[0], block3[1] + (l - m), block3[2]];
    if s.padding_check && (bbox.min[1] < s.min_y + s.pad_bottom || bbox.max[1] > s.max_y - s.pad_top) {
        return Ok(Outcome::None);
    }
    let n = l + offset[1];
    let stub = [j, n, k];
    if !world.valid_biome(stub)? {
        return Ok(Outcome::Stub(stub, Vec::new()));
    }
    let mut placer = Placer {
        data,
        max_depth: s.max_depth,
        expansion_hack: s.expansion_hack,
        aliases,
        pieces: vec![Piece { element, pos, ground: 1, rotation, bbox, junctions: Vec::new() }],
        spaces: Vec::new(),
        queue: Default::default(),
    };
    if s.max_depth > 0 {
        let outer = Box3 {
            min: [j - s.max_horizontal, (n - s.max_vertical).max(s.min_y + s.pad_bottom), k - s.max_horizontal],
            max: [j + s.max_horizontal, (n + s.max_vertical + 1).min(s.max_y + 1 - s.pad_top) - 1, k + s.max_horizontal],
        };
        placer.spaces.push(Space { base: outer, removed: vec![bbox] });
        placer.try_children(0, 0, 0, random, world)?;
        while let Some((piece, space, depth)) = placer.next() {
            placer.try_children(piece, space, depth, random, world)?;
        }
    }
    Ok(Outcome::Stub(stub, placer.pieces))
}

/// Pieces as words: count, then per piece `[element, x, y, z, ground,
/// rotation, box (6), junctions, junction (5) each]`.
pub(crate) fn write(pieces: &[Piece], out: &mut Vec<i64>) {
    out.push(pieces.len() as i64);
    for p in pieces {
        out.extend([p.element as i64, p.pos[0] as i64, p.pos[1] as i64, p.pos[2] as i64, p.ground as i64, p.rotation as i64]);
        out.extend(p.bbox.min.iter().chain(p.bbox.max.iter()).map(|&v| v as i64));
        out.push(p.junctions.len() as i64);
        for j in &p.junctions {
            out.extend(j.iter().map(|&v| v as i64));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rotation_matches_java() {
        // North (2) clockwise is east (5); up stays.
        assert_eq!(rotate(2, 1), 5);
        assert_eq!(rotate(5, 1), 3);
        assert_eq!(rotate(3, 3), 5);
        assert_eq!(rotate(UP, 2), UP);
        assert_eq!(transform([1, 2, 3], 1), [-3, 2, 1]);
        assert_eq!(transform([1, 2, 3], 3), [3, 2, -1]);
    }

    #[test]
    fn space_rejects_overlaps_and_escapes() {
        let space = Space { base: Box3 { min: [0, 0, 0], max: [9, 9, 9] }, removed: vec![Box3 { min: [0, 0, 0], max: [2, 2, 2] }] };
        assert!(space.fits(&Box3 { min: [3, 0, 0], max: [5, 2, 2] }));
        assert!(!space.fits(&Box3 { min: [2, 0, 0], max: [5, 2, 2] }));
        assert!(!space.fits(&Box3 { min: [8, 0, 0], max: [10, 2, 2] }));
    }

    #[test]
    fn priority_queue_is_fifo_within_priority() {
        let data = Data { templates: vec![], elements: vec![], pools: vec![], pool_by_key: HashMap::new(), aliases: vec![], bottom: 0, empty_id: 0, empty_pool: 0 };
        let mut placer = Placer { data: &data, max_depth: 1, expansion_hack: false, aliases: HashMap::new(), pieces: vec![], spaces: vec![], queue: Default::default() };
        placer.queue.entry(0).or_default().push_back((1, 0, 1));
        placer.queue.entry(5).or_default().push_back((2, 0, 1));
        placer.queue.entry(0).or_default().push_back((3, 0, 1));
        assert_eq!(placer.next().map(|e| e.0), Some(2));
        assert_eq!(placer.next().map(|e| e.0), Some(1));
        assert_eq!(placer.next().map(|e| e.0), Some(3));
        assert_eq!(placer.next(), None);
    }
}
