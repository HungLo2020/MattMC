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

/// `Heightmap.Types` copied per chunk, in this order, with their ordinals.
pub(crate) const OCEAN_FLOOR_WG: usize = 1;
const TYPE_ORDINALS: [u32; HEIGHTMAP_TYPES] = [0, 2];

/// Output words: status detail, then far-write log entries (x, y, z), then
/// one flag per chunk section whose block palette grew, then the heightmaps
/// Rust primed (a bit set like `H_HEIGHTMAPS_PRESENT`, then raw words).
pub(crate) const OUT_DETAIL_X: usize = 0;
pub(crate) const OUT_DETAIL_Z: usize = 1;
pub(crate) const OUT_FAR_COUNT: usize = 2;
pub(crate) const OUT_FAR_LOG: usize = 3;
pub(crate) const FAR_LOG_ENTRIES: usize = 16;
pub(crate) const OUT_RESIZED: usize = OUT_FAR_LOG + FAR_LOG_ENTRIES * 3;

/// A read or write that Java would have answered with a crash or exception.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Failure {
    /// `WorldGenRegion.getChunk` outside its cache: Java reproduces the crash report.
    UnavailableChunk { x: i32, z: i32 },
    /// The binding cannot serve this read (unprimed heightmap, missing owner).
    Unbound,
    /// A write the counter policies decline; Java's path would materialize storage.
    Declined,
}

pub(crate) type Result<T> = std::result::Result<T, Failure>;

pub(crate) struct Region<'a> {
    words: &'a [i64],
    out: &'a mut [i64],
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
}

impl<'a> Region<'a> {
    pub(crate) fn words_for(sections: usize, heightmap_words: usize) -> usize {
        HEADER + CHUNKS * sections * SECTION_WORDS + CHUNKS * HEIGHTMAP_TYPES * heightmap_words
    }

    pub(crate) fn out_words_for(sections: usize, heightmap_words: usize) -> usize {
        OUT_RESIZED + CHUNKS * sections + 1 + CHUNKS * HEIGHTMAP_TYPES * heightmap_words
    }

    fn out_primed_flags(&self) -> usize {
        OUT_RESIZED + CHUNKS * self.sections
    }

    /// Validates the header and lengths; owners are trusted as live for the call.
    pub(crate) fn bind(words: &'a [i64], out: &'a mut [i64]) -> Option<Self> {
        if words.len() < HEADER || words[H_VERSION] != VERSION {
            return None;
        }
        let sections = usize::try_from(words[H_SECTIONS]).ok().filter(|s| (1..=4096).contains(s))?;
        let heightmap_bits = usize::try_from(words[H_HEIGHTMAP_BITS]).ok().filter(|b| (1..=32).contains(b))?;
        let heightmap_words = usize::try_from(words[H_HEIGHTMAP_WORDS]).ok()?;
        if heightmap_words != 256usize.div_ceil(64 / heightmap_bits)
            || words.len() != Self::words_for(sections, heightmap_words)
            || out.len() != Self::out_words_for(sections, heightmap_words)
        {
            return None;
        }
        let policies = counters::installed_policies()?;
        let registry = crate::content::block::installed()?;
        let air = registry.block(registry.air()?).default_state().0 as u32;
        out.fill(0);
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
            words,
            out,
        })
    }

    /// Index into the bound neighbourhood, or the crash Java's region raises.
    fn chunk(&self, chunk_x: i32, chunk_z: i32) -> Result<usize> {
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

    fn section_words(&self, chunk: usize, section: usize) -> &[i64] {
        let at = HEADER + (chunk * self.sections + section) * SECTION_WORDS;
        &self.words[at..at + SECTION_WORDS]
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
        let owner = self.blocks(chunk, section)?;
        Ok(owner.with_state_reader(|reader| reader.get(Self::block_index(x, y, z))))
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
        let chunk = self.chunk(x >> 4, z >> 4)?;
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
        let masks = crate::world::level::levelgen::heightmap::installed_masks().ok_or(Failure::Unbound)?;
        let bit = 1u32 << TYPE_ORDINALS[kind];
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
        let base = self.out_primed_flags() + 1 + (chunk * HEIGHTMAP_TYPES + kind) * self.heightmap_words;
        let per = 64 / self.heightmap_bits;
        self.out[base..base + self.heightmap_words].fill(0);
        for (index, height) in heights.iter().enumerate() {
            let value = (height.unwrap_or(self.min_y) - self.min_y) as u64;
            let cell = index / per;
            self.out[base + cell] |= (value << ((index - cell * per) * self.heightmap_bits)) as i64;
        }
        Ok(())
    }

    /// `WorldGenRegion.getBiome`: fiddled zoom, then `ChunkAccess.getNoiseBiome`'s clamp.
    pub(crate) fn biome(&self, x: i32, y: i32, z: i32) -> Result<u32> {
        let [qx, qy, qz] = fiddled_distance::quart(self.zoom_seed, [x, y, z]);
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
