//! Exact port of `DynamicGraphMinFixedPoint` + `ChunkTracker` for trackers
//! whose levels live in one byte map (`FixedPlayerDistanceChunkTracker`,
//! `SimulationChunkTracker`). Integer arithmetic, clamping, byte storage and
//! queue scheduling follow the Java sources statement by statement; see
//! `RUST-PLAYER-DISTANCE.md` and `RUST-SIMULATION-DISTANCE.md` for contracts.
use super::position_map::{AllocationError, PositionMap};
use crate::world::level::lighting::priority_queue::queue::{Error as QueueError, Queue};

/// `ChunkPos.INVALID_CHUNK_POS`, the graph's source sentinel.
pub(crate) const INVALID_CHUNK_POS: i64 = as_long(1_875_066, 1_875_066);
const NO_COMPUTED_LEVEL: i32 = 255;

/// `ChunkPos.asLong`.
pub(crate) const fn as_long(x: i32, z: i32) -> i64 {
    ((x as u32 as u64) | ((z as u32 as u64) << 32)) as i64
}

/// `SectionPos` packing: x 22 bits at 42, z 22 bits at 20, y 20 bits at 0.
pub(crate) const fn section_long(x: i32, y: i32, z: i32) -> i64 {
    ((x as i64 & 0x3f_ffff) << 42) | (y as i64 & 0xf_ffff) | ((z as i64 & 0x3f_ffff) << 20)
}

fn section_x(position: i64) -> i32 {
    (position >> 42) as i32
}

fn section_y(position: i64) -> i32 {
    ((position << 44) >> 44) as i32
}

fn section_z(position: i64) -> i32 {
    ((position << 22) >> 42) as i32
}

/// Neighbourhood and source sentinel of the graph's Java tracker family.
#[derive(Clone, Copy)]
pub(crate) enum Topology {
    /// `ChunkTracker`: 8 chunk neighbours, source `INVALID_CHUNK_POS`.
    Chunk,
    /// `SectionTracker`: 26 section neighbours, source `Long.MAX_VALUE`.
    Section,
}

impl Topology {
    fn source(self) -> i64 {
        match self {
            Topology::Chunk => INVALID_CHUNK_POS,
            Topology::Section => i64::MAX,
        }
    }

    /// Every offset position in the original loop order, the centre included
    /// (callers skip or substitute it exactly as the Java loops do).
    fn neighbourhood(self, position: i64, out: &mut [i64; 27]) -> usize {
        let mut count = 0;
        match self {
            Topology::Chunk => {
                let (x, z) = (position as i32, (position >> 32) as i32);
                for dx in -1..=1 {
                    for dz in -1..=1 {
                        out[count] = as_long(x.wrapping_add(dx), z.wrapping_add(dz));
                        count += 1;
                    }
                }
            }
            Topology::Section => {
                let (x, y, z) = (section_x(position), section_y(position), section_z(position));
                for dx in -1..=1 {
                    for dy in -1..=1 {
                        for dz in -1..=1 {
                            out[count] = section_long(x.wrapping_add(dx), y.wrapping_add(dy), z.wrapping_add(dz));
                            count += 1;
                        }
                    }
                }
            }
        }
        count
    }
}

/// `Mth.clamp(int, int, int)`.
fn clamp(value: i32, min: i32, max: i32) -> i32 {
    if value < min {
        min
    } else {
        value.min(max)
    }
}

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    Allocation,
    /// Impossible for clamped priorities; reported rather than masked.
    Queue,
}

impl From<AllocationError> for Error {
    fn from(_: AllocationError) -> Self {
        Error::Allocation
    }
}

impl From<QueueError> for Error {
    fn from(error: QueueError) -> Self {
        match error {
            QueueError::Allocation => Error::Allocation,
            QueueError::Index(_) | QueueError::Empty => Error::Queue,
        }
    }
}

/// A tracker's `getLevelFromSource`.
pub(crate) trait SourceLevels {
    fn level_from_source(&self, position: i64) -> i32;
}

/// Chunks holding at least one player; every field reads this one source.
pub(crate) struct Players {
    present: PositionMap<u8>,
}

impl Players {
    pub fn new() -> Result<Self, Error> {
        Ok(Self {
            present: PositionMap::with_expected(16)?,
        })
    }

    pub fn insert(&mut self, position: i64) -> Result<(), Error> {
        Ok(self.present.insert(position, 1)?)
    }

    pub fn remove(&mut self, position: i64) {
        self.present.remove(position);
    }

}

impl SourceLevels for Players {
    /// `FixedPlayerDistanceChunkTracker.getLevelFromSource`.
    fn level_from_source(&self, position: i64) -> i32 {
        if self.present.contains(position) {
            0
        } else {
            i32::MAX
        }
    }
}

pub(crate) struct DistanceField {
    topology: Topology,
    level_count: i32,
    /// `setLevel` removes levels above this and stores the rest.
    max_stored: i32,
    /// `chunks.defaultReturnValue`, before byte narrowing.
    absent_level: i32,
    queue: Queue,
    /// Java `Long2ByteMap computedLevels`, read as `get(..) & 255`.
    computed: PositionMap<u8>,
    /// Java `Long2ByteMap chunks`, read as a signed byte.
    levels: PositionMap<u8>,
    /// Every `setLevel(pos, level)` in call order, for Java's published view.
    changes: Vec<(i64, i32)>,
    /// Loading only: `ChunkPos.MAX_COORDINATE_VALUE`. Java's `setLevel` then
    /// creates a `ChunkHolder`, which throws beyond this chessboard distance.
    holder_bound: Option<i32>,
}

impl DistanceField {
    /// `FixedPlayerDistanceChunkTracker(i)`: `super(i + 2, 16, 256)`.
    /// The Java adapter validates the distance before construction.
    pub fn fixed_player(max_distance: i32) -> Result<Self, Error> {
        Self::new(Topology::Chunk, max_distance + 2, max_distance, max_distance + 2)
    }

    /// `SimulationChunkTracker`: `super(34, 16, 256)`, levels of 33 and above
    /// removed, absent chunks at 33.
    pub fn simulation() -> Result<Self, Error> {
        Self::new(Topology::Chunk, 34, 32, 33)
    }

    /// `LoadingChunkTracker`: `super(MAX_LEVEL + 1, 16, 256)` with
    /// `MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1`; levels above `ChunkLevel.MAX_LEVEL`
    /// mean "no holder" and read back as `MAX_LEVEL`, exactly as the holder map does.
    pub fn loading(chunk_max_level: i32, max_coordinate: i32) -> Result<Self, Error> {
        let mut field = Self::new(Topology::Chunk, chunk_max_level + 2, chunk_max_level, chunk_max_level + 1)?;
        field.holder_bound = Some(max_coordinate);
        Ok(field)
    }

    /// `PoiManager.DistanceTracker`: `super(7, 16, 256)`, levels above 6
    /// removed, absent sections at 7.
    pub fn poi() -> Result<Self, Error> {
        Self::new(Topology::Section, 7, 6, 7)
    }

    fn new(topology: Topology, level_count: i32, max_stored: i32, absent_level: i32) -> Result<Self, Error> {
        Ok(Self {
            topology,
            level_count,
            max_stored,
            absent_level,
            queue: Queue::new(level_count, 16)?,
            computed: PositionMap::with_expected(256)?,
            levels: PositionMap::with_expected(256)?,
            changes: Vec::new(),
            holder_bound: None,
        })
    }

    /// Whether Java's `setLevel(pos, level)` throws creating an out-of-bounds
    /// holder: a loaded level at a chunk whose `ChunkPos.getChessboardDistance(ZERO)`
    /// exceeds the bound. Such chunks never hold a holder, so it would be created.
    fn holder_creation_fails(&self, position: i64, level: i32) -> bool {
        self.holder_bound.is_some_and(|bound| {
            let distance = (position as i32).wrapping_abs().max(((position >> 32) as i32).wrapping_abs());
            level <= self.max_stored && distance > bound
        })
    }

    pub fn has_work(&self) -> bool {
        self.queue.first < self.level_count
    }

    pub fn changes(&self) -> &[(i64, i32)] {
        &self.changes
    }

    pub fn clear_changes(&mut self) {
        self.changes.clear();
    }

    /// `chunks.get(l)` with its byte default.
    pub fn level(&self, position: i64) -> i32 {
        match self.levels.get(position) {
            Some(value) => value as i8 as i32,
            None => self.absent_level as i8 as i32,
        }
    }

    fn computed_level(&self, position: i64) -> i32 {
        self.computed.get(position).map_or(NO_COMPUTED_LEVEL, i32::from)
    }

    /// The tracker's `setLevel`, recording the call.
    fn set_level(&mut self, position: i64, level: i32) -> Result<(), Error> {
        self.changes
            .try_reserve(1)
            .map_err(|_| Error::Allocation)?;
        if level > self.max_stored {
            self.levels.remove(position);
        } else {
            self.levels.insert(position, level as i8 as u8)?;
        }
        self.changes.push((position, level));
        Ok(())
    }

    /// `ChunkTracker.computeLevelFromNeighbor`.
    fn level_from_neighbor(&self, source: &impl SourceLevels, from: i64, to: i64, level: i32) -> i32 {
        if from == self.topology.source() {
            source.level_from_source(to)
        } else {
            level.wrapping_add(1)
        }
    }

    /// `ChunkTracker`/`SectionTracker.update`: `checkEdge(source, pos, level, decrease)`.
    pub fn update(
        &mut self,
        source: &impl SourceLevels,
        position: i64,
        level: i32,
        decrease: bool,
    ) -> Result<(), Error> {
        let current = self.level(position);
        let computed = self.computed_level(position);
        self.check_edge(source, self.topology.source(), position, level, current, computed, decrease)
    }

    fn check_edge(
        &mut self,
        source: &impl SourceLevels,
        from: i64,
        to: i64,
        level: i32,
        current: i32,
        computed: i32,
        decrease: bool,
    ) -> Result<(), Error> {
        if to == self.topology.source() {
            return Ok(());
        }
        let top = self.level_count - 1;
        let level = clamp(level, 0, top);
        let current = clamp(current, 0, top);
        let absent = computed == NO_COMPUTED_LEVEL;
        let computed = if absent { current } else { computed };
        let next = if decrease {
            computed.min(level)
        } else {
            clamp(self.computed_from_neighbors(source, to, from, level), 0, top)
        };
        if current != next {
            let previous = if absent { NO_COMPUTED_LEVEL } else { computed };
            self.queue.reschedule(to as u64, current, previous, next)?;
            self.computed.insert(to, next as u8)?;
        } else if !absent {
            self.queue.cancel_computed(to as u64, current, computed)?;
            self.computed.remove(to);
        }
        Ok(())
    }

    /// `DynamicGraphMinFixedPoint.checkNeighbor`.
    fn check_neighbor(
        &mut self,
        source: &impl SourceLevels,
        from: i64,
        to: i64,
        level: i32,
        decrease: bool,
    ) -> Result<(), Error> {
        let top = self.level_count - 1;
        let computed = self.computed_level(to);
        let candidate = clamp(self.level_from_neighbor(source, from, to, level), 0, top);
        if decrease {
            let current = self.level(to);
            self.check_edge(source, from, to, candidate, current, computed, decrease)
        } else {
            let absent = computed == NO_COMPUTED_LEVEL;
            let pending = if absent {
                clamp(self.level(to), 0, top)
            } else {
                computed
            };
            if candidate == pending {
                let current = if absent { pending } else { self.level(to) };
                self.check_edge(source, from, to, top, current, computed, decrease)
            } else {
                Ok(())
            }
        }
    }

    /// `ChunkTracker`/`SectionTracker.getComputedLevel`.
    fn computed_from_neighbors(&self, source: &impl SourceLevels, position: i64, excluded: i64, level: i32) -> i32 {
        let mut best = level;
        let mut neighbourhood = [0i64; 27];
        let count = self.topology.neighbourhood(position, &mut neighbourhood);
        for mut neighbor in neighbourhood[..count].iter().copied() {
            if neighbor == position {
                neighbor = self.topology.source();
            }
            if neighbor != excluded {
                let candidate = self.level_from_neighbor(source, neighbor, position, self.level(neighbor));
                if best > candidate {
                    best = candidate;
                }
                if best == 0 {
                    return best;
                }
            }
        }
        best
    }

    /// `ChunkTracker`/`SectionTracker.checkNeighborsAfterUpdate`.
    fn check_neighbors_after_update(
        &mut self,
        source: &impl SourceLevels,
        position: i64,
        level: i32,
        decrease: bool,
    ) -> Result<(), Error> {
        if !decrease || level < self.level_count - 2 {
            let mut neighbourhood = [0i64; 27];
            let count = self.topology.neighbourhood(position, &mut neighbourhood);
            for neighbor in neighbourhood[..count].iter().copied() {
                if neighbor != position {
                    self.check_neighbor(source, position, neighbor, level, decrease)?;
                }
            }
        }
        Ok(())
    }

    /// `DynamicGraphMinFixedPoint.runUpdates`; returns the remaining budget.
    pub fn run_updates(&mut self, source: &impl SourceLevels, mut budget: i32) -> Result<i32, Error> {
        let top = self.level_count - 1;
        while self.has_work() && budget > 0 {
            budget -= 1;
            let position = self.queue.pop()? as i64;
            let current = clamp(self.level(position), 0, top);
            let computed = self.computed.remove(position).map_or(NO_COMPUTED_LEVEL, i32::from);
            if computed < current {
                if self.holder_creation_fails(position, computed) {
                    // Stop exactly where Java's setLevel throws: node popped,
                    // pending level removed, level unchanged. Java's replay of
                    // this final call throws the original exception.
                    self.changes.try_reserve(1).map_err(|_| Error::Allocation)?;
                    self.changes.push((position, computed));
                    return Ok(budget);
                }
                self.set_level(position, computed)?;
                self.check_neighbors_after_update(source, position, computed, true)?;
            } else if computed > current {
                self.set_level(position, top)?;
                if computed != top {
                    self.queue.enqueue_computed(position as u64, top, computed)?;
                    self.computed.insert(position, computed as u8)?;
                }
                self.check_neighbors_after_update(source, position, current, false)?;
            }
        }
        Ok(budget)
    }
}
