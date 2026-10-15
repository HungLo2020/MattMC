//! A decoration call's `WorldGenRegion`: the center chunk and its neighbours
//! within the step's dependency radius, read and written through their live
//! Rust section owners. Java fills the binding words before each call and
//! keeps every owner reachable until the call returns.
use crate::world::level::biome::fiddled_distance;
use crate::content::block::{BlockRegistry, StateFlags, StateId};
use crate::world::level::chunk::{biomes, counters, live};

/// Binding header words, then `SECTION_WORDS` per chunk section (row-major over
/// the 3x3 neighbourhood, `dz` outer), then `HEIGHTMAP_TYPES` raw heightmaps per chunk.
pub(crate) const VERSION: i64 = 1;
pub(crate) const HEADER: usize = 16;
pub(crate) const SECTION_WORDS: usize = 3;
pub(crate) const HEIGHTMAP_TYPES: usize = 2;
pub(crate) const CHUNKS: usize = 9;
const H_VERSION: usize = 0;
const H_CENTER_X: usize = 1;
const H_CENTER_Z: usize = 2;
const H_MIN_Y: usize = 3;
const H_SECTIONS: usize = 4;
const H_WRITE_RADIUS: usize = 5;
const H_MIN_GEN_Y: usize = 6;
const H_GEN_DEPTH: usize = 7;
const H_ZOOM_SEED: usize = 8;
const H_HEIGHTMAP_BITS: usize = 9;
const H_HEIGHTMAP_WORDS: usize = 10;
/// Bit `chunk * HEIGHTMAP_TYPES + type` when that heightmap is primed and copied.
const H_HEIGHTMAPS_PRESENT: usize = 11;
const H_CHUNKS_PRESENT: usize = 12;
/// Nonzero when Rust may prime an unprimed heightmap as `Heightmap.primeHeightmaps` would.
const H_LAZY_PRIMING: usize = 13;
/// Bit per chunk whose persisted status is at least INITIALIZE_LIGHT.
const H_LIGHT_CHUNKS: usize = 14;
/// `ChunkSkyLightSources` storage bits.
const H_SKY_BITS: usize = 15;
/// Final heightmap types per chunk (`ChunkStatus.FINAL_HEIGHTMAPS`).
pub(crate) const FINAL_TYPES: usize = 4;

/// `Heightmap.Types` copied per chunk, in this order, with their ordinals.
pub(crate) const OCEAN_FLOOR_WG: usize = 1;
const TYPE_ORDINALS: [u32; HEIGHTMAP_TYPES] = [0, 2];

/// Output words: status detail, then far-write log entries (x, y, z), then
/// one flag per chunk section whose block palette grew, then the WG heightmaps
/// Rust primed (a bit set like `H_HEIGHTMAPS_PRESENT`, then raw words), then
/// the final heightmaps and sky-light sources: Java fills them with present
/// bits before the call, Rust edits them in place and sets dirty bits.
pub(crate) const OUT_DETAIL_X: usize = 0;
pub(crate) const OUT_DETAIL_Z: usize = 1;
pub(crate) const OUT_FAR_COUNT: usize = 2;
pub(crate) const OUT_FAR_LOG: usize = 3;
pub(crate) const FAR_LOG_ENTRIES: usize = 16;
pub(crate) const OUT_RESIZED: usize = OUT_FAR_LOG + FAR_LOG_ENTRIES * 3;

/// Failure injection for verification (`-Dmattmc.worldgen.decorationTest=fail:N`):
/// the Nth native block write from now fails mid-placement. Negative: off.
static FAIL_COUNTDOWN: std::sync::atomic::AtomicI64 = std::sync::atomic::AtomicI64::new(-1);

pub(crate) fn inject_failure_after(writes: i64) {
    FAIL_COUNTDOWN.store(writes, std::sync::atomic::Ordering::SeqCst);
}

/// Counts one native write; the Nth fails once with a binding failure.
pub(crate) fn injected_failure() -> Result<()> {
    use std::sync::atomic::Ordering;
    if FAIL_COUNTDOWN.load(Ordering::Relaxed) < 0 {
        return Ok(());
    }
    if FAIL_COUNTDOWN.fetch_sub(1, Ordering::SeqCst) == 1 {
        return Err(Failure::Declined);
    }
    Ok(())
}

/// A read or write that Java would have answered with a crash or exception.
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub(crate) enum Failure {
    /// `WorldGenRegion.getChunk` outside its cache: Java reproduces the crash report.
    UnavailableChunk { x: i32, z: i32 },
    /// The binding cannot serve this read (unprimed heightmap, missing owner).
    Unbound,
    /// A Java sync upcall failed; Java holds the exception.
    Callback,
    /// A write the counter policies decline; Java's path would materialize storage.
    Declined,
}

pub(crate) type Result<T> = std::result::Result<T, Failure>;

/// `int upcall(int op, long* args, int count)`: a non-negative result, or
/// negative on failure (Java keeps the exception). Before it runs,
/// Java applies everything Rust holds (views, heightmaps, sky sources, primed
/// maps, far writes, events); afterwards it refreshes the binding words.
pub(crate) type Callback = unsafe extern "C" fn(i32, *mut i64, i32) -> i32;

/// Sync upcall operations.
pub(crate) const OP_UPDATE_SHAPE_AT_EDGE: i32 = 1;
/// `level.getBlockEntity(pos, BlockEntityType.BEEHIVE).isPresent()`: 1 or 0.
pub(crate) const OP_BEEHIVE_PRESENT: i32 = 2;
/// `storeBee(Occupant.create(ticks))` for each given tick count, in order.
pub(crate) const OP_STORE_BEES: i32 = 3;
/// `ConfiguredFeature.place` in Java: `[index, x, y, z, seedLo, seedHi]`; Java
/// writes the random's state back into the last two words.
pub(crate) const OP_PLACE_JAVA: i32 = 4;
/// The program's Java predicate `[index, x, y, z]` at that position: 1 or 0.
pub(crate) const OP_TEST_PREDICATE: i32 = 5;
/// A program's Java step `[index, x, y, z, seedLo, seedHi, slot]` drawing from
/// the random; Java writes its state back into words 4 and 5.
pub(crate) const OP_JAVA_STEP: i32 = 8;
/// `Math.log` of `[bits]`, written back as bits; a pure upcall.
pub(crate) const OP_MATH_LOG: i32 = 9;
/// Reads outside the bound 3×3, which `WorldGenRegion` serves from its wider
/// cache: `getBlockState` `[x, y, z]`, `getHeight` `[ordinal, x, z]` and
/// `getNoiseBiome` `[qx, qy, qz]` (as a region biome index), written to word 0.
/// Pure: Rust never writes outside the 3×3, so Java's view there is current.
pub(crate) const OP_FAR_STATE: i32 = 10;
/// `Math.pow` of `[a bits, b bits]`, written to word 0 as bits; a pure upcall.
pub(crate) const OP_MATH_POW: i32 = 13;
/// `Math.sin` and `Math.cos` of `[bits]` off the transcribed stub's main path; pure upcalls.
pub(crate) const OP_MATH_SIN: i32 = 14;
pub(crate) const OP_MATH_COS: i32 = 15;
/// `WorldGenRegion.getBrightness(LightLayer.BLOCK, [x, y, z])` to word 0; pure:
/// Java's own setBlock only queues light work, so pending events never change it.
pub(crate) const OP_BLOCK_LIGHT: i32 = 16;
/// `getBlockState(pos).isFaceSturdy(level, pos, direction)` for `[x, y, z, direction ordinal]`; pure.
pub(crate) const OP_STURDY: i32 = 17;
/// `Block.canSupportCenter(level, pos, direction)` for `[x, y, z, direction ordinal]`; pure.
pub(crate) const OP_CENTER_SUPPORT: i32 = 18;
/// `getBiome(pos).is(tag)` for `[x, y, z, tag index]`; pure.
pub(crate) const OP_BIOME_TAG: i32 = 19;
/// `Block.isFaceFull(getBlockState(pos).getCollisionShape(level, pos), direction)` for
/// `[x, y, z, direction ordinal]`; pure.
pub(crate) const OP_COLLISION_FACE: i32 = 20;
/// The FEATURES transaction's first-touch capture for `[chunk index]`: Java
/// snapshots that chunk (and arms its native sections) before Rust's first
/// write to it. Pure: it reads Java state only.
pub(crate) const OP_TOUCH: i32 = 21;
/// `Biome.warmEnoughToRain(pos, level.getSeaLevel())` for `[x, y, z, region biome
/// index]` to word 0. A synchronous, **cache-mutating** callback, not a pure read:
/// it updates Java's per-thread temperature cache (keyed by position alone), so
/// every call must happen exactly where and when Java's feature makes it. Never
/// memoize, reorder or skip it.
pub(crate) const OP_WARM: i32 = 22;
pub(crate) const OP_FAR_HEIGHT: i32 = 11;
pub(crate) const OP_FAR_BIOME: i32 = 12;

/// Binding or output words shared with Java. Java rewrites them during a sync
/// upcall, so Rust holds raw pointers and only ever creates short-lived
/// references inside one access, never across a call into Java.
pub(crate) struct Shared {
    pointer: *mut i64,
    length: usize,
}

impl Shared {
    /// # Safety
    /// `pointer` is valid for `length` words for the region's lifetime.
    pub(crate) unsafe fn new(pointer: *mut i64, length: usize) -> Self {
        Self { pointer, length }
    }

    pub(crate) fn len(&self) -> usize {
        self.length
    }

    pub(crate) fn as_ptr(&self) -> *mut i64 {
        self.pointer
    }

    pub(crate) fn fill(&mut self, range: std::ops::Range<usize>, value: i64) {
        assert!(range.start <= range.end && range.end <= self.length);
        for i in range {
            // SAFETY: bounds checked above.
            unsafe { *self.pointer.add(i) = value };
        }
    }
}

impl std::ops::Index<usize> for Shared {
    type Output = i64;
    fn index(&self, index: usize) -> &i64 {
        assert!(index < self.length);
        // SAFETY: bounds checked; the reference ends with the expression.
        unsafe { &*self.pointer.add(index) }
    }
}

impl std::ops::IndexMut<usize> for Shared {
    fn index_mut(&mut self, index: usize) -> &mut i64 {
        assert!(index < self.length);
        // SAFETY: bounds checked; the reference ends with the expression.
        unsafe { &mut *self.pointer.add(index) }
    }
}

pub(crate) struct Region<'a> {
    words: Shared,
    out: Shared,
    lifetime: std::marker::PhantomData<&'a mut [i64]>,
    pub(crate) center_x: i32,
    pub(crate) center_z: i32,
    pub(crate) min_y: i32,
    pub(crate) sections: usize,
    write_radius: i32,
    pub(crate) min_gen_y: i32,
    pub(crate) gen_depth: i32,
    zoom_seed: i64,
    heightmap_bits: usize,
    heightmap_words: usize,
    policies: &'static [Option<counters::Policy>],
    registry: &'static BlockRegistry,
    air: u32,
    void_air: u32,
    sky_bits: usize,
    sky_words: usize,
    /// Java-owned effects in call order: `[kind, x, y, z, argument]`.
    pub(crate) events: Vec<[i64; 5]>,
    /// Java's sync upcall, for steps that still run in Java mid-placement.
    pub(crate) callback: Option<Callback>,
    /// The running program's slot in the decoration plan (-1 outside the loop),
    /// so Java finds that program's Java leaves and predicates.
    pub(crate) slot: i64,
    /// A failure raised where no `Result` reaches (an int provider's `Math.log`
    /// upcall); the next region access returns it.
    deferred: std::cell::Cell<Option<Failure>>,
    /// Chunks Java has captured for the step's transaction during this call.
    touched: std::cell::Cell<u16>,
    /// Lock-free reads of each section's current generation, renewed after
    /// palette growth replaces it (as Java's views are).
    leases: std::cell::RefCell<Vec<Option<live::WorldLease>>>,
}

impl<'a> Region<'a> {
    pub(crate) fn words_for(sections: usize, heightmap_words: usize) -> usize {
        HEADER + CHUNKS * sections * SECTION_WORDS + CHUNKS * HEIGHTMAP_TYPES * heightmap_words
    }

    pub(crate) fn out_words_for(sections: usize, heightmap_words: usize, sky_words: usize) -> usize {
        OUT_RESIZED + CHUNKS * sections + 1 + CHUNKS * HEIGHTMAP_TYPES * heightmap_words
            + 2 + CHUNKS * FINAL_TYPES * heightmap_words + 2 + CHUNKS * sky_words
    }

    fn out_final(&self) -> usize {
        self.out_primed_flags() + 1 + CHUNKS * HEIGHTMAP_TYPES * self.heightmap_words
    }

    fn out_sky(&self) -> usize {
        self.out_final() + 2 + CHUNKS * FINAL_TYPES * self.heightmap_words
    }

    fn out_primed_flags(&self) -> usize {
        OUT_RESIZED + CHUNKS * self.sections
    }

    /// Validates the header and lengths; owners are trusted as live for the call.
    pub(crate) fn bind(words: Shared, mut out: Shared) -> Option<Self> {
        if words.len() < HEADER || words[H_VERSION] != VERSION {
            return None;
        }
        let sections = usize::try_from(words[H_SECTIONS]).ok().filter(|s| (1..=4096).contains(s))?;
        let heightmap_bits = usize::try_from(words[H_HEIGHTMAP_BITS]).ok().filter(|b| (1..=32).contains(b))?;
        let heightmap_words = usize::try_from(words[H_HEIGHTMAP_WORDS]).ok()?;
        let sky_bits = usize::try_from(words[H_SKY_BITS]).ok().filter(|b| (1..=32).contains(b))?;
        let sky_words = 256usize.div_ceil(64 / sky_bits);
        if heightmap_words != 256usize.div_ceil(64 / heightmap_bits)
            || words.len() != Self::words_for(sections, heightmap_words)
            || out.len() != Self::out_words_for(sections, heightmap_words, sky_words)
        {
            return None;
        }
        let policies = counters::installed_policies()?;
        let registry = crate::content::block::installed()?;
        let air = registry.block(registry.air()?).default_state().0 as u32;
        let void_air = registry.block(registry.by_name("minecraft:void_air")?).default_state().0 as u32;
        // Java fills the final heightmaps and sky sources; only the rest starts clear.
        let keep = OUT_RESIZED + CHUNKS * sections + 1 + CHUNKS * HEIGHTMAP_TYPES * heightmap_words;
        out.fill(0..keep, 0);
        Some(Self {
            center_x: words[H_CENTER_X] as i32,
            center_z: words[H_CENTER_Z] as i32,
            min_y: words[H_MIN_Y] as i32,
            sections,
            write_radius: words[H_WRITE_RADIUS] as i32,
            min_gen_y: words[H_MIN_GEN_Y] as i32,
            gen_depth: words[H_GEN_DEPTH] as i32,
            zoom_seed: words[H_ZOOM_SEED],
            heightmap_bits,
            heightmap_words,
            policies,
            registry,
            air,
            void_air,
            sky_bits,
            sky_words,
            events: Vec::new(),
            callback: None,
            slot: -1,
            deferred: std::cell::Cell::new(None),
            touched: std::cell::Cell::new(0),
            leases: std::cell::RefCell::new((0..CHUNKS * sections).map(|_| None).collect()),
            words,
            out,
            lifetime: std::marker::PhantomData,
        })
    }

    /// Index into the bound neighbourhood, or the crash Java's region raises.
    pub(crate) fn chunk_index(&self, chunk_x: i32, chunk_z: i32) -> Result<usize> {
        self.chunk(chunk_x, chunk_z)
    }

    fn chunk(&self, chunk_x: i32, chunk_z: i32) -> Result<usize> {
        self.check_deferred()?;
        let dx = chunk_x.wrapping_sub(self.center_x);
        let dz = chunk_z.wrapping_sub(self.center_z);
        if !(-1..=1).contains(&dx) || !(-1..=1).contains(&dz) {
            return Err(Failure::UnavailableChunk { x: chunk_x, z: chunk_z });
        }
        let index = ((dz + 1) * 3 + dx + 1) as usize;
        if self.words[H_CHUNKS_PRESENT] & (1 << index) == 0 {
            return Err(Failure::UnavailableChunk { x: chunk_x, z: chunk_z });
        }
        Ok(index)
    }

    pub(crate) fn fail_detail(&mut self, failure: &Failure) {
        if let Failure::UnavailableChunk { x, z } = failure {
            self.out[OUT_DETAIL_X] = *x as i64;
            self.out[OUT_DETAIL_Z] = *z as i64;
        }
    }

    pub(crate) fn section_word(&self, chunk: usize, section: usize, word: usize) -> i64 {
        self.section_words(chunk, section)[word]
    }

    pub(crate) fn chunks_present(&self) -> i64 {
        self.words[H_CHUNKS_PRESENT]
    }

    pub(crate) fn registry(&self) -> &'static BlockRegistry {
        self.registry
    }

    pub(crate) fn void_air(&self) -> u32 {
        self.void_air
    }

    /// `Blocks.AIR.defaultBlockState()`.
    pub(crate) fn air_block_state(&self) -> u32 {
        self.air
    }

    pub(crate) fn light_active(&self, chunk: usize) -> bool {
        self.words[H_LIGHT_CHUNKS] & (1 << chunk) != 0
    }

    /// The section index of an in-height Y.
    pub(crate) fn section_of(&self, y: i32) -> usize {
        self.section_index(y).expect("caller checked the build height")
    }

    pub(crate) fn event(&mut self, kind: i64, x: i32, y: i32, z: i32, argument: i64) {
        self.events.push([kind, x as i64, y as i64, z as i64, argument]);
    }

    /// `ProtoChunk.getBlockState` for a chunk of the region: void air outside the build height.
    pub(crate) fn chunk_block_state(&self, chunk: usize, x: i32, y: i32, z: i32) -> Result<u32> {
        let Some(section) = self.section_index(y) else {
            return Ok(self.void_air);
        };
        self.read_section(chunk, section, Self::block_index(x, y, z))
    }

    /// `WorldGenRegion.getBlockState`: the chunk's state, void air outside the build height.
    pub(crate) fn level_block_state(&self, x: i32, y: i32, z: i32) -> Result<u32> {
        match self.chunk(x >> 4, z >> 4) {
            Ok(chunk) => self.chunk_block_state(chunk, x, y, z),
            Err(Failure::UnavailableChunk { .. }) if self.callback.is_some() => self.far(OP_FAR_STATE, [x as i64, y as i64, z as i64]).map(|s| s as u32),
            Err(failure) => Err(failure),
        }
    }

    /// Whether chunk (x, z) is one of the bound nine.
    pub(crate) fn binds_chunk(&self, chunk_x: i32, chunk_z: i32) -> bool {
        (-1..=1).contains(&chunk_x.wrapping_sub(self.center_x)) && (-1..=1).contains(&chunk_z.wrapping_sub(self.center_z))
    }

    /// A read Java answers from `WorldGenRegion` outside the bound 3×3.
    /// Block light at a position, from Java.
    pub(crate) fn block_light(&self, x: i32, y: i32, z: i32) -> Result<i64> {
        self.far(OP_BLOCK_LIGHT, [x as i64, y as i64, z as i64])
    }

    /// Java's `warmEnoughToRain` of the region biome `biome` at a position
    /// ([`OP_WARM`]): one synchronous callback per call, which mutates Java's cache.
    pub(crate) fn warm_enough_to_rain(&self, biome: u32, x: i32, y: i32, z: i32) -> Result<bool> {
        self.check_deferred()?;
        let mut args = [x as i64, y as i64, z as i64, biome as i64];
        self.pure_call(OP_WARM, &mut args)?;
        Ok(args[0] != 0)
    }

    fn far(&self, op: i32, mut args: [i64; 3]) -> Result<i64> {
        self.check_deferred()?;
        self.pure_call(op, &mut args)?;
        Ok(args[0])
    }

    fn final_bit(chunk: usize, kind: usize) -> usize {
        chunk * FINAL_TYPES + kind
    }

    pub(crate) fn final_present(&self, chunk: usize, kind: usize) -> bool {
        (self.out[self.out_final()] >> Self::final_bit(chunk, kind)) & 1 != 0
    }

    pub(crate) fn mark_final_present(&mut self, chunk: usize, kind: usize) {
        let at = self.out_final();
        self.out[at] |= 1 << Self::final_bit(chunk, kind);
        self.out[at + 1] |= 1 << Self::final_bit(chunk, kind);
    }

    fn packed_get(words: &Shared, base: usize, bits: usize, index: usize) -> i64 {
        let per = 64 / bits;
        let cell = index / per;
        ((words[base + cell] as u64 >> ((index - cell * per) * bits)) & ((1u64 << bits) - 1)) as i64
    }

    fn packed_set(words: &mut Shared, base: usize, bits: usize, index: usize, value: u64) {
        let per = 64 / bits;
        let cell = index / per;
        let shift = (index - cell * per) * bits;
        let mask = ((1u64 << bits) - 1) << shift;
        words[base + cell] = ((words[base + cell] as u64 & !mask) | ((value << shift) & mask)) as i64;
    }

    fn final_base(&self, chunk: usize, kind: usize) -> usize {
        self.out_final() + 2 + Self::final_bit(chunk, kind) * self.heightmap_words
    }

    /// `Heightmap.getFirstAvailable` of a final heightmap.
    pub(crate) fn final_height(&self, chunk: usize, kind: usize, column: usize) -> i32 {
        Self::packed_get(&self.out, self.final_base(chunk, kind), self.heightmap_bits, column) as i32 + self.min_y
    }

    pub(crate) fn set_final_height(&mut self, chunk: usize, kind: usize, column: usize, height: i32) {
        let base = self.final_base(chunk, kind);
        Self::packed_set(&mut self.out, base, self.heightmap_bits, column, (height - self.min_y) as u64);
        let at = self.out_final() + 1;
        self.out[at] |= 1 << Self::final_bit(chunk, kind);
    }

    pub(crate) fn sky_present(&self, chunk: usize) -> bool {
        (self.out[self.out_sky()] >> chunk) & 1 != 0
    }

    /// `WorldGenRegion.getHeight(type, x, z)` for a final heightmap kind,
    /// priming it from current blocks when the chunk has none yet.
    pub(crate) fn final_height_at(&mut self, kind: usize, x: i32, z: i32) -> Result<i32> {
        let chunk = self.chunk(x >> 4, z >> 4)?;
        if !self.final_present(chunk, kind) {
            self.prime_final_kind(chunk, kind)?;
        }
        Ok(self.final_height(chunk, kind, ((x & 15) + (z & 15) * 16) as usize))
    }

    fn prime_final_kind(&mut self, chunk: usize, kind: usize) -> Result<()> {
        let bit = 1u32 << crate::world::level::levelgen::decoration::write::FINAL_ORDINALS[kind];
        let heights = self.prime_heights(chunk, bit)?;
        for (column, height) in heights.iter().enumerate() {
            self.set_final_height(chunk, kind, column, height.unwrap_or(self.min_y));
        }
        self.mark_final_present(chunk, kind);
        Ok(())
    }

    /// Runs one Java step mid-placement: Rust's state goes to Java first and
    /// Java's comes back into the binding words. No references into the
    /// shared words live across the call.
    /// Records a failure for the next region access.
    pub(crate) fn defer(&self, failure: Failure) {
        if self.deferred.get().is_none() {
            self.deferred.set(Some(failure));
        }
    }

    pub(crate) fn check_deferred(&self) -> Result<()> {
        match self.deferred.take() {
            Some(failure) => {
                self.deferred.set(Some(failure));
                Err(failure)
            }
            None => Ok(()),
        }
    }

    /// A Java call that touches no world state (`Math.log`): nothing is synced either way.
    /// Java's first-touch capture of `chunk`, once per call, before its first write.
    fn touch(&self, chunk: usize) -> Result<()> {
        let bit = 1u16 << chunk;
        if self.touched.get() & bit == 0 {
            self.pure_call(OP_TOUCH, &mut [chunk as i64])?;
            self.touched.set(self.touched.get() | bit);
        }
        Ok(())
    }

    pub(crate) fn pure_call(&self, op: i32, args: &mut [i64]) -> Result<i32> {
        let callback = self.callback.ok_or(Failure::Unbound)?;
        // SAFETY: Java reads and writes only `args` for pure operations.
        let status = unsafe { callback(op, args.as_mut_ptr(), args.len() as i32) };
        if status < 0 {
            return Err(Failure::Callback);
        }
        Ok(status)
    }

    pub(crate) fn sync_call(&mut self, op: i32, args: &mut [i64]) -> Result<i32> {
        self.check_deferred()?;
        let callback = self.callback.ok_or(Failure::Unbound)?;
        super::ffi::publish_events(std::mem::take(&mut self.events));
        // SAFETY: Java's upcall reads (and for some operations writes) `args`
        // during the call and edits only the binding words.
        let status = unsafe { callback(op, args.as_mut_ptr(), args.len() as i32) };
        if status < 0 {
            return Err(Failure::Callback);
        }
        Ok(status)
    }

    /// `StructureTemplate.updateShapeAtEdge(level, flags, shape, min)` through Java:
    /// arbitrary blocks' `updateShape` stays Java's until block behaviour moves.
    /// `shape` bits index `(x * ySpan + y) * zSpan + z`.
    pub(crate) fn update_shape_at_edge(&mut self, flags: i32, min: [i32; 3], span: [i32; 3], shape: &[u64]) -> Result<()> {
        let mut args = vec![flags as i64, min[0] as i64, min[1] as i64, min[2] as i64, span[0] as i64, span[1] as i64, span[2] as i64];
        args.extend(shape.iter().map(|&w| w as i64));
        self.sync_call(OP_UPDATE_SHAPE_AT_EDGE, &mut args).map(|_| ())
    }

    /// `WorldGenRegion.getHeight(type, x, z)` for any `Heightmap.Types` ordinal.
    pub(crate) fn height_by_type(&mut self, ordinal: u32, x: i32, z: i32) -> Result<i32> {
        if !self.binds_chunk(x >> 4, z >> 4) && self.callback.is_some() {
            return self.far(OP_FAR_HEIGHT, [ordinal as i64, x as i64, z as i64]).map(|h| h as i32);
        }
        match ordinal {
            0 => self.height(0, x, z),
            2 => self.height(OCEAN_FLOOR_WG, x, z),
            _ => {
                let kind = crate::world::level::levelgen::decoration::write::FINAL_ORDINALS
                    .iter().position(|&o| o == ordinal).ok_or(Failure::Unbound)?;
                self.final_height_at(kind, x, z)
            }
        }
    }

    /// `ChunkSkyLightSources.minY`: one below the level's minimum.
    pub(crate) fn sky_min_y(&self) -> i32 {
        self.min_y - 1
    }

    pub(crate) fn sky_source(&self, chunk: usize, column: usize) -> i32 {
        let base = self.out_sky() + 2 + chunk * self.sky_words;
        Self::packed_get(&self.out, base, self.sky_bits, column) as i32 + self.sky_min_y()
    }

    pub(crate) fn set_sky_source(&mut self, chunk: usize, column: usize, y: i32) {
        let base = self.out_sky() + 2 + chunk * self.sky_words;
        let value = (y - self.sky_min_y()) as u64;
        Self::packed_set(&mut self.out, base, self.sky_bits, column, value);
        let at = self.out_sky() + 1;
        self.out[at] |= 1 << chunk;
    }

    fn section_words(&self, chunk: usize, section: usize) -> [i64; SECTION_WORDS] {
        let at = HEADER + (chunk * self.sections + section) * SECTION_WORDS;
        [self.words[at], self.words[at + 1], self.words[at + 2]]
    }

    /// `LevelHeightAccessor.isOutsideBuildHeight`.
    pub(crate) fn outside_build_height(&self, y: i32) -> bool {
        let section = (y >> 4) - (self.min_y >> 4);
        section < 0 || section >= self.sections as i32
    }

    fn section_index(&self, y: i32) -> Option<usize> {
        let section = (y >> 4) - (self.min_y >> 4);
        (section >= 0 && (section as usize) < self.sections).then_some(section as usize)
    }

    fn blocks(&self, chunk: usize, section: usize) -> Result<&live::Owner> {
        let pointer = self.section_words(chunk, section)[0] as *const live::Owner;
        // SAFETY: Java keeps each bound section's owner alive for the call.
        unsafe { pointer.as_ref() }.ok_or(Failure::Unbound)
    }

    fn block_index(x: i32, y: i32, z: i32) -> usize {
        (((y & 15) << 8) | ((z & 15) << 4) | (x & 15)) as usize
    }

    /// `BulkSectionAccess.getBlockState`: air outside the section range.
    pub(crate) fn block_state(&self, x: i32, y: i32, z: i32) -> Result<u32> {
        let chunk = self.chunk(x >> 4, z >> 4)?;
        let Some(section) = self.section_index(y) else {
            return Ok(self.air());
        };
        self.read_section(chunk, section, Self::block_index(x, y, z))
    }

    fn read_section(&self, chunk: usize, section: usize, index: usize) -> Result<u32> {
        let owner = self.blocks(chunk, section)?;
        let mut leases = self.leases.borrow_mut();
        let slot = &mut leases[chunk * self.sections + section];
        if let Some(value) = slot.as_ref().and_then(|lease| lease.get(owner, index)) {
            return Ok(value);
        }
        *slot = owner.world_lease();
        match slot.as_ref().and_then(|lease| lease.get(owner, index)) {
            Some(value) => Ok(value),
            // A non-authoritative owner still answers under its lock, as before.
            None => Ok(owner.with_state_reader(|reader| reader.get(index))),
        }
    }

    fn air(&self) -> u32 {
        self.air
    }

    /// `BlockState.isAir()` at a position read as `block_state` reads it.
    pub(crate) fn is_air(&self, x: i32, y: i32, z: i32) -> Result<bool> {
        let state = self.block_state(x, y, z)?;
        if state as usize >= self.registry.state_count() {
            return Err(Failure::Unbound);
        }
        Ok(self.registry.flags(StateId(state as u16)).contains(StateFlags::AIR))
    }

    /// `LevelChunkSection.setBlockState(x, y, z, state, false)` with native counters.
    /// Returns the previous state; no heightmap, light or block-entity side effects.
    pub(crate) fn set_section_state(&mut self, x: i32, y: i32, z: i32, state: u32) -> Result<u32> {
        injected_failure()?;
        let chunk = self.chunk(x >> 4, z >> 4)?;
        self.touch(chunk)?;
        let section = self.section_index(y).ok_or(Failure::Unbound)?;
        let words = self.section_words(chunk, section);
        // SAFETY: Java keeps each bound section's owners alive for the call.
        let (blocks, counts) = unsafe {
            ((words[0] as *const live::Owner).as_ref(), (words[1] as *const counters::Owner).as_ref())
        };
        let (Some(blocks), Some(counts)) = (blocks, counts) else {
            return Err(Failure::Unbound);
        };
        let (old, resized) = blocks
            .write_counts(counts, Self::block_index(x, y, z), state, self.policies)
            .ok_or(Failure::Declined)?;
        if resized {
            self.out[OUT_RESIZED + chunk * self.sections + section] = 1;
        }
        Ok(old)
    }

    /// `WorldGenRegion.ensureCanWrite` for a region whose center is not upgrading;
    /// far writes are logged for Java, which reports them exactly as its region does.
    pub(crate) fn ensure_can_write(&mut self, x: i32, y: i32, z: i32) -> bool {
        let dx = ((x >> 4).wrapping_sub(self.center_x)).wrapping_abs();
        let dz = ((z >> 4).wrapping_sub(self.center_z)).wrapping_abs();
        if dx <= self.write_radius && dz <= self.write_radius {
            return true;
        }
        let count = self.out[OUT_FAR_COUNT] as usize;
        if count < FAR_LOG_ENTRIES {
            let at = OUT_FAR_LOG + count * 3;
            self.out[at] = x as i64;
            self.out[at + 1] = y as i64;
            self.out[at + 2] = z as i64;
        }
        self.out[OUT_FAR_COUNT] = (count + 1) as i64;
        false
    }

    /// `WorldGenRegion.getHeight(type, x, z)`: the chunk heightmap's first free Y.
    /// An unprimed heightmap is primed here, at its first read, as `ChunkAccess.getHeight`
    /// primes it; Java installs the result after the call.
    pub(crate) fn height(&mut self, kind: usize, x: i32, z: i32) -> Result<i32> {
        let chunk = self.chunk(x >> 4, z >> 4)?;
        let bit = chunk * HEIGHTMAP_TYPES + kind;
        let index = ((x & 15) + (z & 15) * 16) as usize;
        let per = 64 / self.heightmap_bits;
        let cell = index / per;
        let shift = (index - cell * per) * self.heightmap_bits;
        let raw = if (self.words[H_HEIGHTMAPS_PRESENT] >> bit) & 1 != 0 {
            let base = HEADER + CHUNKS * self.sections * SECTION_WORDS + bit * self.heightmap_words;
            self.words[base + cell] as u64
        } else {
            let flags = self.out_primed_flags();
            if (self.out[flags] >> bit) & 1 == 0 {
                if self.words[H_LAZY_PRIMING] == 0 {
                    return Err(Failure::Unbound);
                }
                self.prime(chunk, kind)?;
                self.out[flags] |= 1 << bit;
            }
            self.out[flags + 1 + bit * self.heightmap_words + cell] as u64
        };
        let value = (raw >> shift) & ((1u64 << self.heightmap_bits) - 1);
        Ok(value as i32 + self.min_y)
    }

    /// `Heightmap.primeHeightmaps(chunk, {type})` over current blocks: from the
    /// top of the highest non-empty section down, the first non-AIR-block state
    /// the type accepts. Columns without one keep the minimum height.
    fn prime(&mut self, chunk: usize, kind: usize) -> Result<()> {
        let heights = self.prime_heights(chunk, 1u32 << TYPE_ORDINALS[kind])?;
        let base = self.out_primed_flags() + 1 + (chunk * HEIGHTMAP_TYPES + kind) * self.heightmap_words;
        self.out.fill(base..base + self.heightmap_words, 0);
        for (index, height) in heights.iter().enumerate() {
            Self::packed_set(&mut self.out, base, self.heightmap_bits, index, (height.unwrap_or(self.min_y) - self.min_y) as u64);
        }
        Ok(())
    }

    /// Each column's primed height for the heightmap type whose mask `bit` selects:
    /// from the top of the highest non-empty section down, the first non-AIR-block
    /// state the type accepts; `None` keeps the minimum height.
    pub(crate) fn prime_heights(&self, chunk: usize, bit: u32) -> Result<[Option<i32>; 256]> {
        let masks = crate::world::level::levelgen::heightmap::installed_masks().ok_or(Failure::Unbound)?;
        let mut heights = [None::<i32>; 256];
        let mut top = None;
        for section in (0..self.sections).rev() {
            let counts = self.section_words(chunk, section)[1] as *const counters::Owner;
            // SAFETY: Java keeps each bound section's owners alive for the call.
            let counts = unsafe { counts.as_ref() }.ok_or(Failure::Unbound)?;
            if counts.values()[0] != 0 {
                top = Some(section);
                break;
            }
        }
        // `getHighestSectionPosition() + 16`: section 0 when every section is empty.
        let top = top.unwrap_or(0);
        let mut remaining = 256;
        for section in (0..=top).rev() {
            let owner = self.blocks(chunk, section)?;
            let custom = owner.with_state_reader(|reader| {
                for y in (0..16).rev() {
                    for column in 0..256 {
                        if heights[column].is_some() {
                            continue;
                        }
                        let state = reader.get((y << 8) | column);
                        let mask = *masks.masks.get(state as usize).unwrap_or(&crate::world::level::levelgen::heightmap::CUSTOM);
                        if mask == crate::world::level::levelgen::heightmap::CUSTOM {
                            return true;
                        }
                        if mask & bit != 0 {
                            heights[column] = Some(self.min_y + section as i32 * 16 + y as i32 + 1);
                            remaining -= 1;
                        }
                    }
                }
                false
            });
            if custom {
                return Err(Failure::Unbound);
            }
            if remaining == 0 {
                break;
            }
        }
        Ok(heights)
    }

    /// The distinct biome values stored in the bound 3x3's sections (the set
    /// `applyBiomeDecoration` gathers), ascending.
    pub(crate) fn present_biomes(&self) -> Result<Vec<u32>> {
        let mut seen: Vec<u32> = Vec::new();
        for chunk in 0..CHUNKS {
            for section in 0..self.sections {
                let pointer = self.section_words(chunk, section)[2] as *const biomes::Owner;
                // SAFETY: Java keeps each bound section's owner alive for the call.
                let owner = unsafe { pointer.as_ref() }.ok_or(Failure::Unbound)?;
                let mut last = u32::MAX;
                let valid = owner.for_each_present(|value| {
                    if value != last {
                        last = value;
                        if let Err(at) = seen.binary_search(&value) {
                            seen.insert(at, value);
                        }
                    }
                });
                if !valid {
                    return Err(Failure::Unbound);
                }
            }
        }
        Ok(seen)
    }

    /// `WorldGenRegion.getBiome`: fiddled zoom, then `ChunkAccess.getNoiseBiome`'s clamp.
    pub(crate) fn biome(&self, x: i32, y: i32, z: i32) -> Result<u32> {
        let [qx, qy, qz] = fiddled_distance::quart(self.zoom_seed, [x, y, z]);
        if !self.binds_chunk(qx >> 2, qz >> 2) && self.callback.is_some() {
            return self.far(OP_FAR_BIOME, [qx as i64, qy as i64, qz as i64]).map(|b| b as u32);
        }
        let chunk = self.chunk(qx >> 2, qz >> 2)?;
        let min_quart = self.min_y >> 2;
        let max_quart = min_quart + ((self.sections as i32 * 16) >> 2) - 1;
        let clamped = qy.clamp(min_quart, max_quart);
        let section = self.section_index(clamped << 2).ok_or(Failure::Unbound)?;
        let pointer = self.section_words(chunk, section)[2] as *const biomes::Owner;
        // SAFETY: Java keeps each bound section's owner alive for the call.
        let owner = unsafe { pointer.as_ref() }.ok_or(Failure::Unbound)?;
        let index = (((clamped & 3) << 4) | ((qz & 3) << 2) | (qx & 3)) as usize;
        owner.value(index).ok_or(Failure::Unbound)
    }
}
