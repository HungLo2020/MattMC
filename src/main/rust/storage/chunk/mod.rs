//! Chunk section serialization: the `sections` list of
//! `SerializableChunkData.write()` (block states and biomes packed as
//! `PalettedContainer`'s codec does, block and sky light, `Y`), written
//! directly as NBT tape records in Java's `CompoundTag` key order. Java
//! supplies raw container storage and registry vocabulary; see
//! `NativeChunkSections`.
pub(crate) mod ffi;
pub(crate) mod load;
#[cfg(test)]
mod tests;

const TAG_BYTE: u8 = 1;
const TAG_BYTE_ARRAY: u8 = 7;
const TAG_STRING: u8 = 8;
const TAG_LIST: u8 = 9;
const TAG_COMPOUND: u8 = 10;
const TAG_LONG_ARRAY: u8 = 12;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Error {
    /// Input Java must encode itself.
    Unsupported,
}

/// `String.hashCode` over UTF-16 units.
fn java_hash(units: &[u16]) -> i32 {
    units.iter().fold(0i32, |h, &u| h.wrapping_mul(31).wrapping_add(u as i32))
}

/// Iteration order of a default-capacity `java.util.HashMap` holding `keys`
/// inserted in the given order (at most 12 keys: no resize, no tree bins):
/// by bucket, then insertion order within a bucket.
pub(crate) fn hash_map_order(keys: &[&[u16]]) -> Vec<usize> {
    assert!(keys.len() <= 12);
    let bucket = |k: &[u16]| {
        let h = java_hash(k);
        ((h ^ ((h as u32) >> 16) as i32) & 15) as usize
    };
    let mut order: Vec<usize> = (0..keys.len()).collect();
    order.sort_by_key(|&i| (bucket(keys[i]), i));
    order
}

fn units(s: &str) -> Vec<u16> {
    s.encode_utf16().collect()
}

/// The tape writer of `NativeNbt.TapeWriter`: 24-byte records, UTF-16 names.
pub(crate) struct Tape {
    pub out: Vec<u8>,
}

impl Tape {
    fn record(&mut self, tag: u8, element: u8, name: &[u16], count: usize, scalar: i64) {
        self.out.extend_from_slice(&[tag, element, 0, 0]);
        self.out.extend_from_slice(&(name.len() as i32).to_le_bytes());
        self.out.extend_from_slice(&(count as i32).to_le_bytes());
        self.out.extend_from_slice(&0i32.to_le_bytes());
        self.out.extend_from_slice(&scalar.to_le_bytes());
        for unit in name {
            self.out.extend_from_slice(&unit.to_le_bytes());
        }
    }
}

/// One container's storage as Java holds it: `bits` per entry in
/// `SimpleBitStorage` words (0 for `ZeroBitStorage`), and the label of each
/// palette id (equal labels are the same value).
pub(crate) struct Container<'a> {
    pub bits: usize,
    pub words: &'a [u64],
    pub labels: &'a [u32],
}

/// `PalettedContainer.pack`: distinct values in first-occurrence order and
/// each entry's index among them. Returns the palette ids of the values.
fn compact(container: &Container, entries: usize, lookup: &mut Vec<i32>, order: &mut Vec<u32>, indices: &mut Vec<u32>) -> Result<(), Error> {
    order.clear();
    indices.clear();
    let bits = container.bits;
    if bits == 0 {
        // ZeroBitStorage reads id 0 everywhere.
        container.labels.first().ok_or(Error::Unsupported)?;
        order.push(0);
        indices.resize(entries, 0);
        return Ok(());
    }
    if bits > 32 {
        return Err(Error::Unsupported);
    }
    let per = 64 / bits;
    if container.words.len() != entries.div_ceil(per) {
        return Err(Error::Unsupported);
    }
    let mask = if bits == 64 { u64::MAX } else { (1u64 << bits) - 1 };
    for &label in container.labels {
        if lookup.len() <= label as usize {
            lookup.resize(label as usize + 1, -1);
        }
    }
    // Every slot set here is cleared again on every exit: the lookup is reused
    // by later containers and encodes on this thread.
    let mut touched = Vec::new();
    let mut result = Ok(());
    for at in 0..entries {
        let id = (container.words[at / per] >> (at % per * bits) & mask) as usize;
        let Some(&label) = container.labels.get(id) else {
            result = Err(Error::Unsupported);
            break;
        };
        let slot = &mut lookup[label as usize];
        if *slot == -1 {
            *slot = order.len() as i32;
            order.push(id as u32);
            touched.push(label as usize);
        }
        indices.push(*slot as u32);
    }
    for label in touched {
        lookup[label] = -1;
    }
    result
}

/// `SimpleBitStorage(bits, entries, indices).getRaw()`.
fn pack_words(indices: &[u32], bits: usize, out: &mut Vec<i64>) {
    let per = 64 / bits;
    out.clear();
    for values in indices.chunks(per) {
        let mut word = 0u64;
        for (i, &value) in values.iter().enumerate() {
            word |= (value as u64) << (i * bits);
        }
        out.push(word as i64);
    }
}

/// Immutable block-state vocabulary: each state id's canonical label and the
/// tape of its `BlockState.CODEC` compound as a list element, plus
/// `bitsInStorage` for every block palette size.
pub(crate) struct Vocabulary {
    pub labels: Vec<u32>,
    pub fragments: Vec<u8>,
    pub offsets: Vec<u32>,
    pub bits: Vec<u8>,
    /// Labels by fragment, for loading; built on first use.
    pub lookup: std::sync::OnceLock<load::Lookup>,
}

/// A section as `SectionData` holds it.
pub(crate) struct Section<'a> {
    pub y: i32,
    pub states: Option<(Container<'a>, Container<'a>)>,
    pub block_light: Option<&'a [u8]>,
    pub sky_light: Option<&'a [u8]>,
}

/// What a chunk's biome containers need: each biome's name, and
/// `bitsInStorage` for every biome palette size.
pub(crate) struct Biomes<'a> {
    pub names: Vec<&'a [u16]>,
    pub bits: &'a [u8],
}

pub(crate) struct Encoder {
    lookup: Vec<i32>,
    order: Vec<u32>,
    indices: Vec<u32>,
    words: Vec<i64>,
    keys: Keys,
}

struct Keys {
    sections: Vec<u16>,
    block_states: Vec<u16>,
    biomes: Vec<u16>,
    block_light: Vec<u16>,
    sky_light: Vec<u16>,
    y: Vec<u16>,
    palette: Vec<u16>,
    data: Vec<u16>,
}

impl Encoder {
    pub(crate) fn new() -> Self {
        Self {
            lookup: Vec::new(),
            order: Vec::new(),
            indices: Vec::new(),
            words: Vec::new(),
            keys: Keys {
                sections: units("sections"),
                block_states: units("block_states"),
                biomes: units("biomes"),
                block_light: units("BlockLight"),
                sky_light: units("SkyLight"),
                y: units("Y"),
                palette: units("palette"),
                data: units("data"),
            },
        }
    }

    /// The tape of `writeTag("sections", sections)`.
    pub(crate) fn encode(&mut self, vocabulary: &Vocabulary, biomes: &Biomes, sections: &[Section], tape: &mut Tape) -> Result<(), Error> {
        let present: Vec<&Section> = sections
            .iter()
            .filter(|s| s.states.is_some() || s.block_light.is_some() || s.sky_light.is_some())
            .collect();
        // ListTag.identifyRawElementType: 0 when empty.
        tape.record(TAG_LIST, if present.is_empty() { 0 } else { TAG_COMPOUND }, &self.keys.sections, present.len(), 0);
        for section in present {
            self.section(vocabulary, biomes, section, tape)?;
        }
        Ok(())
    }

    fn section(&mut self, vocabulary: &Vocabulary, biomes: &Biomes, section: &Section, tape: &mut Tape) -> Result<(), Error> {
        // Insertion order of SerializableChunkData.write: block_states, biomes, BlockLight, SkyLight, Y.
        let mut keys: Vec<(&[u16], u8)> = Vec::with_capacity(5);
        if section.states.is_some() {
            keys.push((&self.keys.block_states, 0));
            keys.push((&self.keys.biomes, 1));
        }
        if section.block_light.is_some() {
            keys.push((&self.keys.block_light, 2));
        }
        if section.sky_light.is_some() {
            keys.push((&self.keys.sky_light, 3));
        }
        keys.push((&self.keys.y, 4));
        let names: Vec<&[u16]> = keys.iter().map(|k| k.0).collect();
        let order = hash_map_order(&names);
        let kinds: Vec<u8> = order.iter().map(|&i| keys[i].1).collect();
        tape.record(TAG_COMPOUND, 0, &[], kinds.len(), 0);
        for kind in kinds {
            match kind {
                0 => {
                    let (states, _) = section.states.as_ref().unwrap();
                    let name = self.keys.block_states.clone();
                    self.container(&name, states, 4096, tape, |_, tape, id| {
                        let label = states.labels[id as usize] as usize;
                        let (start, end) = (*vocabulary.offsets.get(label).ok_or(Error::Unsupported)? as usize,
                            *vocabulary.offsets.get(label + 1).ok_or(Error::Unsupported)? as usize);
                        tape.out.extend_from_slice(vocabulary.fragments.get(start..end).ok_or(Error::Unsupported)?);
                        Ok(())
                    }, TAG_COMPOUND, &vocabulary.bits)?;
                }
                1 => {
                    let (_, container) = section.states.as_ref().unwrap();
                    let name = self.keys.biomes.clone();
                    self.container(&name, container, 64, tape, |_, tape, id| {
                        let name = *biomes.names.get(container.labels[id as usize] as usize).ok_or(Error::Unsupported)?;
                        tape.record(TAG_STRING, 0, &[], name.len(), 0);
                        for unit in name {
                            tape.out.extend_from_slice(&unit.to_le_bytes());
                        }
                        Ok(())
                    }, TAG_STRING, biomes.bits)?;
                }
                2 | 3 => {
                    let (name, bytes) = if kind == 2 {
                        (&self.keys.block_light, section.block_light.unwrap())
                    } else {
                        (&self.keys.sky_light, section.sky_light.unwrap())
                    };
                    tape.record(TAG_BYTE_ARRAY, 0, name, bytes.len(), 0);
                    tape.out.extend_from_slice(bytes);
                }
                _ => tape.record(TAG_BYTE, 0, &self.keys.y, 0, section.y as i8 as i64),
            }
        }
        Ok(())
    }

    /// `compoundTag.store(name, codec, container)`: {palette, data?} in HashMap order.
    #[allow(clippy::too_many_arguments)]
    fn container(&mut self, name: &[u16], container: &Container, entries: usize, tape: &mut Tape,
        mut entry: impl FnMut(&mut Self, &mut Tape, u32) -> Result<(), Error>, element: u8, bits_table: &[u8]) -> Result<(), Error> {
        let (mut lookup, mut order, mut indices) =
            (std::mem::take(&mut self.lookup), std::mem::take(&mut self.order), std::mem::take(&mut self.indices));
        let result = compact(container, entries, &mut lookup, &mut order, &mut indices);
        self.lookup = lookup;
        result?;
        let bits = *bits_table.get(order.len()).ok_or(Error::Unsupported)? as usize;
        if bits > 32 {
            return Err(Error::Unsupported);
        }
        let with_data = bits != 0;
        if with_data {
            let mut words = std::mem::take(&mut self.words);
            pack_words(&indices, bits, &mut words);
            self.words = words;
        }
        let palette_key = self.keys.palette.clone();
        let data_key = self.keys.data.clone();
        let keys: Vec<&[u16]> = if with_data { vec![&palette_key, &data_key] } else { vec![&palette_key] };
        let key_order = hash_map_order(&keys);
        tape.record(TAG_COMPOUND, 0, name, keys.len(), 0);
        let mut failed = Ok(());
        for k in key_order {
            if k == 0 {
                tape.record(TAG_LIST, element, &palette_key, order.len(), 0);
                for &id in &order {
                    if let Err(e) = entry(self, tape, id) {
                        failed = Err(e);
                        break;
                    }
                }
            } else {
                tape.record(TAG_LONG_ARRAY, 0, &data_key, self.words.len(), 0);
                for word in &self.words {
                    tape.out.extend_from_slice(&word.to_le_bytes());
                }
            }
        }
        self.order = order;
        self.indices = indices;
        failed
    }
}
