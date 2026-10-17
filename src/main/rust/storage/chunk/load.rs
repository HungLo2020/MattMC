//! Loading: the `sections` list of a saved chunk's tape decoded for
//! `SerializableChunkData.parse` (palette states as vocabulary labels,
//! biome palette names, storage words, light layers), and the rest of the
//! root tape for Java's tag reader. Input Java's codecs would not read
//! exactly this way (any non-canonical palette entry, odd types or
//! lengths) is declined; Java then parses the whole tag itself.
use super::{Vocabulary, TAG_BYTE, TAG_BYTE_ARRAY, TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY, TAG_STRING};
use std::collections::HashMap;

const HEADER: usize = 8;
const RECORD: usize = 24;

#[derive(Clone, Copy)]
struct Record {
    tag: u8,
    element: u8,
    start: usize,
    name: (usize, usize),
    count: usize,
    scalar: i64,
}

struct Walk<'a> {
    tape: &'a [u8],
    at: usize,
}

impl<'a> Walk<'a> {
    fn record(&mut self) -> Option<Record> {
        let start = self.at;
        let b = self.tape.get(start..start + RECORD)?;
        let units = u32::from_le_bytes(b[4..8].try_into().ok()?) as usize;
        let count = u32::from_le_bytes(b[8..12].try_into().ok()?) as usize;
        let scalar = i64::from_le_bytes(b[16..24].try_into().ok()?);
        let name = (start + RECORD, start + RECORD + units.checked_mul(2)?);
        self.tape.get(name.0..name.1)?;
        self.at = name.1;
        Some(Record { tag: b[0], element: b[1], start, name, count, scalar })
    }

    fn name(&self, r: &Record) -> &'a [u8] {
        &self.tape[r.name.0..r.name.1]
    }

    fn take(&mut self, n: usize) -> Option<&'a [u8]> {
        let v = self.tape.get(self.at..self.at.checked_add(n)?)?;
        self.at += n;
        Some(v)
    }

    /// Skips a record's payload (after its header and name).
    fn skip(&mut self, r: &Record, depth: u32) -> Option<()> {
        if depth > 512 {
            return None;
        }
        match r.tag {
            1..=6 => {}
            TAG_BYTE_ARRAY => drop(self.take(r.count)?),
            TAG_STRING => drop(self.take(r.count.checked_mul(2)?)?),
            TAG_LIST | TAG_COMPOUND => {
                for _ in 0..r.count {
                    let child = self.record()?;
                    self.skip(&child, depth + 1)?;
                }
            }
            11 => drop(self.take(r.count.checked_mul(4)?)?),
            TAG_LONG_ARRAY => drop(self.take(r.count.checked_mul(8)?)?),
            _ => return None,
        }
        Some(())
    }
}

fn utf16(s: &str) -> Vec<u8> {
    s.encode_utf16().flat_map(|u| u.to_le_bytes()).collect()
}

/// Labels by each block state's list-element tape, built on first load.
pub(crate) struct Lookup {
    states: HashMap<Vec<u8>, u32>,
}

impl Lookup {
    pub(crate) fn new(vocabulary: &Vocabulary) -> Self {
        let mut states = HashMap::new();
        for label in 0..vocabulary.offsets.len() - 1 {
            let (a, b) = (vocabulary.offsets[label] as usize, vocabulary.offsets[label + 1] as usize);
            states.entry(vocabulary.fragments[a..b].to_vec()).or_insert(label as u32);
        }
        Self { states }
    }
}

/// One chunk's decode: `ints` [section count, then per section: y, flags (1
/// block states, 2 biomes, 4 block light, 8 sky light, 16 within the
/// height), then with block states: palette size, word count, labels; with
/// biomes: palette size, word count, name indices], each container's
/// `longs`, 2048 `bytes` per light layer, and the root tape without
/// `sections`.
#[derive(Default)]
pub(crate) struct Decoded {
    pub ints: Vec<i32>,
    pub longs: Vec<i64>,
    pub bytes: Vec<u8>,
    pub root: Vec<u8>,
}

struct Keys {
    sections: Vec<u8>,
    y: Vec<u8>,
    block_states: Vec<u8>,
    biomes: Vec<u8>,
    block_light: Vec<u8>,
    sky_light: Vec<u8>,
    palette: Vec<u8>,
    data: Vec<u8>,
}

/// Decodes `tape` for a level whose sections span `min_section..=max_section`;
/// `biomes` are the biome registry's names (UTF-16LE) by index. None declines.
pub(crate) fn decode(tape: &[u8], lookup: &Lookup, biomes: &HashMap<Vec<u8>, i32>, min_section: i32, max_section: i32) -> Option<Decoded> {
    let keys = Keys {
        sections: utf16("sections"),
        y: utf16("Y"),
        block_states: utf16("block_states"),
        biomes: utf16("biomes"),
        block_light: utf16("BlockLight"),
        sky_light: utf16("SkyLight"),
        palette: utf16("palette"),
        data: utf16("data"),
    };
    let mut walk = Walk { tape, at: HEADER };
    tape.get(..HEADER)?;
    let root = walk.record()?;
    if root.tag != TAG_COMPOUND || root.name.0 != root.name.1 {
        return None;
    }
    let mut out = Decoded::default();
    let mut found = None;
    let children_start = walk.at;
    for _ in 0..root.count {
        let child = walk.record()?;
        let payload = walk.at;
        if walk.name(&child) == keys.sections.as_slice() {
            if found.is_some() || child.tag != TAG_LIST {
                return None;
            }
            found = Some((child.start, child));
            walk.at = payload;
            sections(&mut walk, &child, &keys, lookup, biomes, min_section, max_section, &mut out)?;
        } else {
            walk.skip(&child, 1)?;
        }
    }
    if walk.at != tape.len() {
        return None;
    }
    let (start, child) = found?;
    let end = {
        let mut skip = Walk { tape, at: child.name.1 };
        skip.skip(&child, 1)?;
        skip.at
    };
    // The root without its sections entry: one child fewer.
    out.root.extend_from_slice(&tape[..children_start]);
    let count_at = root.start + 8;
    out.root[count_at..count_at + 4].copy_from_slice(&((root.count - 1) as u32).to_le_bytes());
    out.root.extend_from_slice(&tape[children_start..start]);
    out.root.extend_from_slice(&tape[end..]);
    Some(out)
}

#[allow(clippy::too_many_arguments)]
fn sections(walk: &mut Walk, list: &Record, keys: &Keys, lookup: &Lookup, biomes: &HashMap<Vec<u8>, i32>, min: i32, max: i32,
    out: &mut Decoded) -> Option<()> {
    if list.count > 0 && list.element != TAG_COMPOUND {
        return None;
    }
    out.ints.push(list.count as i32);
    for _ in 0..list.count {
        let section = walk.record()?;
        if section.tag != TAG_COMPOUND {
            return None;
        }
        // Entries by key; unknown keys are ignored, as parse ignores them.
        let (mut y, mut states, mut biome, mut block_light, mut sky_light) = (None, None, None, None, None);
        for _ in 0..section.count {
            let entry = walk.record()?;
            let name = walk.name(&entry);
            let payload = walk.at;
            walk.skip(&entry, 2)?;
            let slot = if name == keys.y.as_slice() {
                if entry.tag != TAG_BYTE {
                    return None;
                }
                y = Some(entry.scalar as i8 as i32);
                continue;
            } else if name == keys.block_states.as_slice() {
                &mut states
            } else if name == keys.biomes.as_slice() {
                &mut biome
            } else if name == keys.block_light.as_slice() {
                &mut block_light
            } else if name == keys.sky_light.as_slice() {
                &mut sky_light
            } else {
                continue;
            };
            *slot = Some((entry, payload));
        }
        let y = y.unwrap_or(0);
        let inside = y >= min && y <= max;
        let flags = states.is_some() as i32 | (biome.is_some() as i32) << 1 | (block_light.is_some() as i32) << 2
            | (sky_light.is_some() as i32) << 3 | (inside as i32) << 4;
        out.ints.push(y);
        out.ints.push(flags);
        if inside {
            if let Some((entry, payload)) = states {
                container(walk.tape, &entry, payload, keys, out, |w, e| {
                    if e.tag != TAG_COMPOUND {
                        return None;
                    }
                    let start = e.start;
                    w.skip(e, 4)?;
                    lookup.states.get(&w.tape[start..w.at]).map(|&l| l as i32)
                }, TAG_COMPOUND)?;
            }
            if let Some((entry, payload)) = biome {
                container(walk.tape, &entry, payload, keys, out, |w, e| {
                    if e.tag != TAG_STRING {
                        return None;
                    }
                    let name = w.take(e.count * 2)?;
                    biomes.get(name).copied()
                }, TAG_STRING)?;
            }
        }
        for light in [block_light, sky_light].into_iter().flatten() {
            let (entry, payload) = light;
            if entry.tag != TAG_BYTE_ARRAY || entry.count != 2048 {
                return None;
            }
            out.bytes.extend_from_slice(walk.tape.get(payload..payload + 2048)?);
        }
    }
    Some(())
}

/// A container compound `{palette, data?}`: palette entries through `entry`,
/// then the words. Extra keys are ignored, as the record codec ignores them.
fn container(tape: &[u8], record: &Record, payload: usize, keys: &Keys, out: &mut Decoded,
    entry: impl Fn(&mut Walk, &Record) -> Option<i32>, element: u8) -> Option<()> {
    if record.tag != TAG_COMPOUND {
        return None;
    }
    let mut walk = Walk { tape, at: payload };
    let (mut palette, mut data) = (None, None);
    for _ in 0..record.count {
        let child = walk.record()?;
        let at = walk.at;
        walk.skip(&child, 3)?;
        let name = &tape[child.name.0..child.name.1];
        if name == keys.palette.as_slice() {
            palette = Some((child, at));
        } else if name == keys.data.as_slice() {
            data = Some((child, at));
        }
    }
    let (list, at) = palette?;
    if list.tag != TAG_LIST || list.count == 0 || list.element != element {
        return None;
    }
    out.ints.push(list.count as i32);
    let words = match data {
        Some((d, _)) if d.tag != TAG_LONG_ARRAY => return None,
        Some((d, _)) => d.count,
        None => 0,
    };
    out.ints.push(if data.is_some() { words as i32 } else { -1 });
    let mut items = Walk { tape, at };
    for _ in 0..list.count {
        let e = items.record()?;
        out.ints.push(entry(&mut items, &e)?);
    }
    if let Some((_, at)) = data {
        let raw = tape.get(at..at + words * 8)?;
        out.longs.extend(raw.chunks_exact(8).map(|c| i64::from_le_bytes(c.try_into().unwrap())));
    }
    Some(())
}
