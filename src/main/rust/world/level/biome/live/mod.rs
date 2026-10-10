//! Loaded-world biome owners feed retained sky and fog fields directly.
use crate::world::level::chunk::biomes::{Generation, Owner};
use std::{collections::HashMap, sync::Arc};
mod ffi;
mod sampling;
const CELLS: usize = 216;
#[derive(Clone, Copy)]
pub enum BiomeColor {
    Sky = 0,
    Fog = 1,
}
#[derive(Clone)]
enum Chunk {
    Native(Arc<[Arc<Owner>]>),
    Compatibility,
}
struct Source {
    owner: Arc<Owner>,
    generation: Arc<Generation>,
    revision: u64,
    values: [u32; 64],
}
struct Sample {
    position: [u64; 3],
    color: [f64; 3],
}
struct Window {
    base: [i32; 3],
    revision: u64,
    colors: [[u32; CELLS]; 2],
    sources: Vec<Source>,
    samples: [Option<Sample>; 2],
}
pub struct LiveBiomeFields {
    epoch: u64,
    revision: u64,
    min_y: i32,
    max_y: i32,
    capacity: usize,
    range: Option<(i32, i32, i32)>,
    colors: [Arc<[u32]>; 2],
    fallback: [u32; 2],
    chunks: HashMap<(i32, i32), Chunk>,
    window: Option<Window>,
}
impl LiveBiomeFields {
    pub fn new(
        epoch: u64,
        min_y: i32,
        height: usize,
        colors: [Arc<[u32]>; 2],
        fallback_id: usize,
        capacity: usize,
    ) -> Option<Self> {
        if epoch == 0
            || min_y & 3 != 0
            || height == 0
            || height % 4 != 0
            || height > 4096
            || colors[0].is_empty()
            || colors[0].len() > 65535
            || colors[0].len() != colors[1].len()
            || capacity == 0
            || capacity > 65536
        {
            return None;
        }
        let fallback = [*colors[0].get(fallback_id)?, *colors[1].get(fallback_id)?];
        let max_y = min_y.checked_add(height as i32 - 1)?;
        Some(Self {
            epoch,
            revision: 1,
            min_y,
            max_y,
            capacity,
            range: None,
            colors,
            fallback,
            chunks: HashMap::new(),
            window: None,
        })
    }
    pub fn set_range(&mut self, epoch: u64, center: [i32; 2], radius: i32) -> bool {
        if epoch != self.epoch || radius < 0 || radius > 127 || self.revision == u64::MAX {
            return false;
        }
        let next = Some((center[0], center[1], radius));
        if self.range != next {
            self.range = next;
            self.revision += 1;
            self.window = None;
        }
        true
    }
    fn in_range(&self, x: i32, z: i32) -> bool {
        self.range.is_none_or(|(cx, cz, r)| {
            // Match Frozen Storage.inRange exactly, including MIN_VALUE abs.
            x.wrapping_sub(cx).wrapping_abs() <= r && z.wrapping_sub(cz).wrapping_abs() <= r
        })
    }
    fn admissible(&self, epoch: u64, position: (i32, i32)) -> bool {
        epoch == self.epoch
            && (self.chunks.contains_key(&position) || self.chunks.len() < self.capacity)
            && self.revision < u64::MAX
    }
    pub fn replace(&mut self, epoch: u64, position: (i32, i32), owners: Arc<[Arc<Owner>]>) -> bool {
        if !self.admissible(epoch, position)
            || owners.len() != (self.max_y - self.min_y + 1) as usize / 4
            || owners
                .iter()
                .any(|owner| !owner.is_valid() || owner.limit as usize != self.colors[0].len())
        {
            return false;
        }
        self.chunks.insert(position, Chunk::Native(owners));
        self.revision += 1;
        self.window = None;
        true
    }
    pub fn compatibility(&mut self, epoch: u64, position: (i32, i32)) -> bool {
        if !self.admissible(epoch, position) {
            return false;
        }
        self.chunks.insert(position, Chunk::Compatibility);
        self.revision += 1;
        self.window = None;
        true
    }
    pub fn remove(&mut self, epoch: u64, position: (i32, i32)) -> bool {
        if epoch != self.epoch {
            return false;
        }
        if !self.chunks.contains_key(&position) {
            return true;
        }
        if self.revision == u64::MAX {
            return false;
        }
        self.chunks.remove(&position);
        self.revision += 1;
        self.window = None;
        true
    }
    /// Terminal bridge disablement releases all residency/cache pins immediately.
    pub fn clear(&mut self, epoch: u64) -> bool {
        if epoch != self.epoch {
            return false;
        }
        self.window = None;
        self.chunks.clear();
        self.revision = self.revision.saturating_add(1);
        true
    }
    fn capture(&self, base: [i32; 3]) -> Option<Window> {
        let mut sources: Vec<Source> = Vec::with_capacity(27);
        let mut indices = HashMap::with_capacity(27);
        let mut colors = [[0u32; CELLS]; 2];
        for i in 0..CELLS {
            let x = base[0] - 2 + (i % 6) as i32;
            let y = base[1] - 2 + (i / 6 % 6) as i32;
            let z = base[2] - 2 + (i / 36) as i32;
            if !self.in_range(x >> 2, z >> 2) {
                colors[0][i] = self.fallback[0];
                colors[1][i] = self.fallback[1];
                continue;
            }
            let Some(chunk) = self.chunks.get(&(x >> 2, z >> 2)) else {
                colors[0][i] = self.fallback[0];
                colors[1][i] = self.fallback[1];
                continue;
            };
            let Chunk::Native(owners) = chunk else {
                return None;
            };
            let y = y.clamp(self.min_y, self.max_y) - self.min_y;
            let owner = &owners[y as usize / 4];
            if !owner.is_valid() {
                return None;
            }
            let key = Arc::as_ptr(owner) as usize;
            let source_index = if let Some(&index) = indices.get(&key) {
                index
            } else {
                let (generation, values, revision) = owner.snapshot();
                let index = sources.len();
                sources.push(Source {
                    owner: Arc::clone(owner),
                    generation,
                    revision,
                    values,
                });
                indices.insert(key, index);
                index
            };
            let local = ((y as usize & 3) << 4) | ((z as usize & 3) << 2) | (x as usize & 3);
            let id = sources[source_index].values[local] as usize;
            for field in 0..2 {
                colors[field][i] = *self.colors[field].get(id)?;
            }
        }
        // Another single-palette alias can change a generation during capture.
        if sources
            .iter()
            .any(|s| !s.owner.matches(&s.generation, s.revision))
        {
            return None;
        }
        Some(Window {
            base,
            revision: self.revision,
            colors,
            sources,
            samples: [None, None],
        })
    }
    pub fn query(&mut self, epoch: u64, position: [f64; 3]) -> Option<[f64; 3]> {
        self.query_color(epoch, position, BiomeColor::Sky)
    }
    pub fn query_color(
        &mut self,
        epoch: u64,
        position: [f64; 3],
        field: BiomeColor,
    ) -> Option<[f64; 3]> {
        if epoch != self.epoch
            || position
                .iter()
                .any(|p| !p.is_finite() || p.abs() > 7_500_000.0)
        {
            return None;
        }
        let base = position.map(|p| p.floor() as i32);
        let valid = self.window.as_ref().is_some_and(|w| {
            w.base == base
                && w.revision == self.revision
                && w.sources
                    .iter()
                    .all(|s| s.owner.matches(&s.generation, s.revision))
        });
        if !valid {
            self.window = Some(self.capture(base)?);
        }
        // Reuse only after checking every retained source's identity/revision.
        // Java hook, weather and brightness callbacks still run on every call.
        let window = self.window.as_mut()?;
        let key = position.map(f64::to_bits);
        let index = field as usize;
        if let Some(sample) = &window.samples[index] {
            if sample.position == key {
                return Some(sample.color);
            }
        }
        let color = sampling::sample(position, &window.colors[index]);
        window.samples[index] = Some(Sample {
            position: key,
            color,
        });
        Some(color)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn recorded_frozen_colors_cross_live_palette_index_and_both_consumers() {
        use std::io::{Cursor, Read};
        let mut raw = Vec::new();
        flate2::read::GzDecoder::new(include_bytes!("frozen-sky-sampler.bin.gz").as_slice())
            .read_to_end(&mut raw)
            .unwrap();
        fn u32_at(input: &mut Cursor<Vec<u8>>) -> u32 {
            let mut bytes = [0; 4];
            input.read_exact(&mut bytes).unwrap();
            u32::from_be_bytes(bytes)
        }
        fn u64_at(input: &mut Cursor<Vec<u8>>) -> u64 {
            let mut bytes = [0; 8];
            input.read_exact(&mut bytes).unwrap();
            u64::from_be_bytes(bytes)
        }
        let mut input = Cursor::new(raw);
        assert_eq!(u32_at(&mut input), 0x534b5931);
        assert_eq!(u32_at(&mut input), 256);
        for _ in 0..256 {
            let position: [f64; 3] = std::array::from_fn(|_| f64::from_bits(u64_at(&mut input)));
            let colors: Arc<[u32]> = (0..CELLS).map(|_| u32_at(&mut input)).collect();
            let expected: [u64; 3] = std::array::from_fn(|_| u64_at(&mut input));
            let base = position.map(|v| v.floor() as i32);
            let min_y = (base[1] - 2) & !3;
            let sky: Arc<[u32]> = vec![0xa1234567; CELLS].into();
            let mut world = LiveBiomeFields::new(7, min_y, 12, [sky, colors], 0, 16).unwrap();
            let mut chunks: HashMap<(i32, i32), [[u32; 64]; 3]> = HashMap::new();
            for id in 0..CELLS {
                let x = base[0] - 2 + (id % 6) as i32;
                let y = base[1] - 2 + (id / 6 % 6) as i32;
                let z = base[2] - 2 + (id / 36) as i32;
                let sections = chunks.entry((x >> 2, z >> 2)).or_insert([[0; 64]; 3]);
                sections[((y - min_y) >> 2) as usize]
                    [((y & 3) * 16 + (z & 3) * 4 + (x & 3)) as usize] = id as u32;
            }
            for (position, sections) in chunks {
                let owners: Arc<[Arc<Owner>]> = sections
                    .iter()
                    .map(|values| {
                        let words: Vec<u64> = values
                            .chunks(8)
                            .map(|row| {
                                row.iter()
                                    .enumerate()
                                    .fold(0u64, |word, (i, &id)| word | u64::from(id) << (i * 8))
                            })
                            .collect();
                        Arc::new(Owner::load(8, 8, &[], &words, CELLS as u32, 8).unwrap())
                    })
                    .collect::<Vec<_>>()
                    .into();
                assert!(world.replace(7, position, owners));
            }
            assert_eq!(
                world
                    .query_color(7, position, BiomeColor::Fog)
                    .unwrap()
                    .map(f64::to_bits),
                expected
            );
            let captured = world.window.as_ref().unwrap().colors.as_ptr();
            assert_eq!(
                world.query(7, position).unwrap(),
                [0x23 as f64 / 255., 0x45 as f64 / 255., 0x67 as f64 / 255.]
            );
            assert_eq!(world.window.as_ref().unwrap().colors.as_ptr(), captured);
            // Alternating consumers must retain their own exact result; neither
            // may return the other field's cached color.
            for _ in 0..4 {
                assert_eq!(
                    world
                        .query_color(7, position, BiomeColor::Fog)
                        .unwrap()
                        .map(f64::to_bits),
                    expected
                );
                assert_eq!(
                    world.query(7, position).unwrap(),
                    [0x23 as f64 / 255., 0x45 as f64 / 255., 0x67 as f64 / 255.]
                );
            }
        }
        assert_eq!(input.position() as usize, input.get_ref().len());
    }
    #[test]
    fn world_consumer_reads_shared_biome_mutations_without_a_second_java_grid() {
        let colors: Arc<[u32]> = (0..512u32)
            .map(|i| i.wrapping_mul(0x010101) & 0xffffff)
            .collect();
        let mut world =
            LiveBiomeFields::new(7, -4, 8, [Arc::clone(&colors), colors], 0, 4).unwrap();
        let owner = Arc::new(Owner::load(0, 0, &[1], &[], 512, 9).unwrap());
        let shared = owner.copy();
        for position in [(0, 0), (0, 1), (1, 0), (1, 1)] {
            assert!(world.replace(
                7,
                position,
                vec![Arc::clone(&owner), Arc::clone(&owner)].into()
            ));
        }
        let position = [2.5, 0.5, 2.5];
        let first = world.query(7, position).unwrap();
        assert_eq!(first, [1.0 / 255.0; 3]);
        let pointer = world.window.as_ref().unwrap().colors.as_ptr();
        for _ in 0..10_000 {
            assert_eq!(world.query(7, position), Some(first));
        }
        assert_eq!(world.window.as_ref().unwrap().colors.as_ptr(), pointer);
        assert_eq!(world.window.as_ref().unwrap().sources.len(), 1);
        assert!(shared.read_single(3));
        assert_eq!(world.query(7, position), Some([3.0 / 255.0; 3]));
        assert_eq!(owner.write(0, 7).unwrap(), (3, true));
        assert_ne!(world.query(7, position).unwrap(), [3.0 / 255.0; 3]);
        assert!(world.compatibility(7, (0, 0)));
        assert!(world.query(7, position).is_none());
        assert!(world.replace(
            7,
            (0, 0),
            vec![Arc::clone(&owner), Arc::clone(&owner)].into()
        ));
        assert!(world.query(7, position).is_some());
        for position in [(0, 0), (0, 1), (1, 0), (1, 1)] {
            assert!(world.remove(7, position));
        }
        assert_eq!(world.query(7, [2.5, 0.5, 2.5]), Some([0.0; 3]));
        assert_eq!(Arc::strong_count(&owner), 1);
    }
    #[test]
    fn view_center_updates_hide_retained_out_of_range_chunks_without_repacking() {
        let colors: Arc<[u32]> = (0..512u32)
            .map(|i| i.wrapping_mul(0x010101) & 0xffffff)
            .collect();
        let mut world =
            LiveBiomeFields::new(7, -4, 8, [Arc::clone(&colors), colors], 0, 4).unwrap();
        let owner = Arc::new(Owner::load(0, 0, &[5], &[], 512, 9).unwrap());
        assert!(world.replace(
            7,
            (0, 0),
            vec![Arc::clone(&owner), Arc::clone(&owner)].into()
        ));
        let position = [1.5, 0.5, 1.5];
        let before = world.query(7, position).unwrap();
        assert_ne!(before, [0.; 3]);
        assert!(world.set_range(7, [10, 10], 3));
        assert_eq!(world.query(7, position), Some([0.; 3]));
        assert_eq!(world.chunks.len(), 1);
        assert!(world.set_range(7, [0, 0], 3));
        assert_eq!(world.query(7, position), Some(before));
        assert!(!world.set_range(8, [0, 0], 3));
        assert!(!world.set_range(7, [0, 0], 128));
        assert!(world.set_range(7, [i32::MAX, i32::MIN], 3));
        assert_eq!(world.query(7, position), Some([0.; 3]));
    }
    #[test]
    fn actual_frozen_client_storage_range_matches_all_integer_edges() {
        use std::io::Read;
        let mut raw = Vec::new();
        flate2::read::GzDecoder::new(include_bytes!("frozen-view-range.bin.gz").as_slice())
            .read_to_end(&mut raw)
            .unwrap();
        let mut at = 0;
        let read = |at: &mut usize| {
            let value = i32::from_be_bytes(raw[*at..*at + 4].try_into().unwrap());
            *at += 4;
            value
        };
        assert_eq!(read(&mut at), 0x56525731);
        assert_eq!(read(&mut at), 12288);
        let mut world = LiveBiomeFields::new(
            7,
            0,
            4,
            [(0..512u32).collect(), (0..512u32).collect()],
            0,
            1,
        )
        .unwrap();
        for _ in 0..12288 {
            let radius = read(&mut at);
            let center = [read(&mut at), read(&mut at)];
            let query = [read(&mut at), read(&mut at)];
            let expected = raw[at] != 0;
            at += 1;
            assert!(world.set_range(7, center, radius));
            assert_eq!(
                world.in_range(query[0], query[1]),
                expected,
                "center={center:?} query={query:?} radius={radius}"
            );
        }
        assert_eq!(at, raw.len());
    }
    #[test]
    fn terminal_clear_releases_residency_and_cached_generation_pins() {
        let colors: Arc<[u32]> = (0..512u32).collect();
        let mut world = LiveBiomeFields::new(9, 0, 4, [Arc::clone(&colors), colors], 0, 1).unwrap();
        let owner = Arc::new(Owner::load(0, 0, &[7], &[], 512, 9).unwrap());
        assert!(world.replace(9, (0, 0), vec![Arc::clone(&owner)].into()));
        assert!(world.query(9, [1.5; 3]).is_some());
        assert!(Arc::strong_count(&owner) > 1);
        assert!(!world.compatibility(9, (1, 0))); // Residency admission exhausted.
        assert!(!world.clear(8));
        assert!(Arc::strong_count(&owner) > 1);
        assert!(world.clear(9));
        assert_eq!(Arc::strong_count(&owner), 1);
        assert!(world.chunks.is_empty());
        assert!(world.window.is_none());
        assert_eq!(world.query(9, [1.5; 3]), Some([0.; 3]));
    }
    #[test]
    fn epoch_dimension_height_registry_and_residency_admission_are_bounded() {
        let colors: Arc<[u32]> = (0..512u32)
            .map(|i| i.wrapping_mul(0x010101) & 0xffffff)
            .collect();
        let mut world =
            LiveBiomeFields::new(9, -4, 8, [Arc::clone(&colors), colors], 0, 1).unwrap();
        let bottom = Arc::new(Owner::load(0, 0, &[4], &[], 512, 9).unwrap());
        let top = Arc::new(Owner::load(0, 0, &[7], &[], 512, 9).unwrap());
        assert!(!world.replace(
            8,
            (0, 0),
            vec![Arc::clone(&bottom), Arc::clone(&top)].into()
        ));
        assert!(world.replace(9, (0, 0), vec![bottom, top].into()));
        assert!(!world.compatibility(9, (1, 0)));
        assert!(world.query(8, [0.0; 3]).is_none());
        let low = world.query(9, [0.0, -100.0, 0.0]).unwrap();
        let high = world.query(9, [0.0, 100.0, 0.0]).unwrap();
        assert_ne!(low, high);
        assert!(world.query(9, [f64::INFINITY, 0., 0.]).is_none());
        assert!(world.remove(9, (0, 0)));
        assert_eq!(world.query(9, [0.0, 0., 0.]), Some([0.0; 3]));
    }
}
