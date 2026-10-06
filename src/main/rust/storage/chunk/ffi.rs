//! Java bridge for chunk section serialization; see `NativeChunkSections`.
use super::{Biomes, Container, Encoder, Section, Tape, Vocabulary};
use std::cell::RefCell;

thread_local! {
    static ENCODER: RefCell<(Encoder, Vec<u8>)> = RefCell::new((Encoder::new(), Vec::new()));
}

/// The block-state vocabulary: canonical label per state id, each label's
/// list-element tape in `fragments` (label `i` spans `offsets[i]..offsets[i + 1]`)
/// and `bitsInStorage` per block palette size. Returns 0 on invalid input.
/// Never freed.
/// # Safety
/// Each pointer addresses its stated count of values for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_vocabulary(labels: *const u32, label_len: i32, fragments: *const u8, fragment_len: i32,
    offsets: *const u32, offset_len: i32, bits: *const u8, bits_len: i32) -> u64 {
    if labels.is_null() || fragments.is_null() || offsets.is_null() || bits.is_null() || label_len <= 0 || fragment_len < 0
        || offset_len < 2 || bits_len < 2
    {
        return 0;
    }
    let offsets = std::slice::from_raw_parts(offsets, offset_len as usize).to_vec();
    let labels = std::slice::from_raw_parts(labels, label_len as usize).to_vec();
    if offsets.windows(2).any(|w| w[0] > w[1]) || *offsets.last().unwrap() as i32 != fragment_len
        || labels.iter().any(|&l| l as usize + 1 >= offsets.len())
    {
        return 0;
    }
    let vocabulary = Vocabulary {
        labels,
        fragments: std::slice::from_raw_parts(fragments, fragment_len as usize).to_vec(),
        offsets,
        bits: std::slice::from_raw_parts(bits, bits_len as usize).to_vec(),
    };
    Box::into_raw(Box::new(vocabulary)) as u64
}

struct Reader<'a> {
    ints: &'a [i32],
    at: usize,
}

impl<'a> Reader<'a> {
    fn next(&mut self) -> Option<i32> {
        let v = *self.ints.get(self.at)?;
        self.at += 1;
        Some(v)
    }

    fn take(&mut self, n: usize) -> Option<&'a [i32]> {
        let v = self.ints.get(self.at..self.at.checked_add(n)?)?;
        self.at += n;
        Some(v)
    }
}

fn as_u32(ints: &[i32]) -> &[u32] {
    // Same size and alignment; labels are non-negative by validation below.
    unsafe { std::slice::from_raw_parts(ints.as_ptr() as *const u32, ints.len()) }
}

/// Encodes one chunk's `sections` list. `ints`: [section count, biome name
/// count, each name's UTF-16 length, then per section: y, flags (1 states and
/// biomes, 2 block light, 4 sky light), and with states: palette kind (0
/// labels follow, 1 global state ids), block bits, palette size, word count,
/// canonical labels; biome bits, palette size, word count, labels (biome
/// name indices)]. `longs`:
/// each container's words in order; `bytes`: 2048 bytes per light layer;
/// `chars`: the biome names. Writes the tape to `out` and its length to
/// `out_len`. Returns 0, 1 when `out` is too small (`out_len` is the size
/// needed), -1 for invalid input, -2 for input Java must encode.
/// # Safety
/// Each pointer addresses its stated count of values; `vocabulary` is live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_encode(vocabulary: u64, ints: *const i32, int_len: i32, longs: *const i64, long_len: i32,
    bytes: *const u8, byte_len: i32, chars: *const u16, char_len: i32, biome_bits: *const u8, biome_bits_len: i32, out: *mut u8,
    out_cap: i64, out_len: *mut i64) -> i32 {
    if vocabulary == 0 || ints.is_null() || out_len.is_null() || int_len < 2 || long_len < 0 || byte_len < 0 || char_len < 0
        || biome_bits.is_null() || biome_bits_len < 2 || (out.is_null() && out_cap > 0)
    {
        return -1;
    }
    let vocabulary = &*(vocabulary as *const Vocabulary);
    let slice = |p: *const u8, n: i32| if n == 0 { &[][..] } else { std::slice::from_raw_parts(p, n as usize) };
    let ints = std::slice::from_raw_parts(ints, int_len as usize);
    let longs: &[u64] = if long_len == 0 { &[] } else { std::slice::from_raw_parts(longs as *const u64, long_len as usize) };
    let bytes = slice(bytes, byte_len);
    let chars: &[u16] = if char_len == 0 { &[] } else { std::slice::from_raw_parts(chars, char_len as usize) };
    let biome_bits = slice(biome_bits, biome_bits_len);
    let Some((sections, biomes)) = parse(ints, longs, bytes, chars, vocabulary) else { return -1 };
    let biomes = Biomes { names: biomes, bits: biome_bits };
    ENCODER.with(|cell| {
        let (encoder, buffer) = &mut *cell.borrow_mut();
        let mut tape = Tape { out: std::mem::take(buffer) };
        tape.out.clear();
        let result = encoder.encode(vocabulary, &biomes, &sections, &mut tape);
        let status = match result {
            Err(_) => -2,
            Ok(()) => {
                *out_len = tape.out.len() as i64;
                if tape.out.len() as i64 > out_cap {
                    1
                } else {
                    std::ptr::copy_nonoverlapping(tape.out.as_ptr(), out, tape.out.len());
                    0
                }
            }
        };
        *buffer = tape.out;
        status
    })
}

fn parse<'a>(ints: &'a [i32], longs: &'a [u64], bytes: &'a [u8], chars: &'a [u16], vocabulary: &'a Vocabulary)
    -> Option<(Vec<Section<'a>>, Vec<&'a [u16]>)> {
    let mut r = Reader { ints, at: 0 };
    let count = usize::try_from(r.next()?).ok()?;
    let names = usize::try_from(r.next()?).ok()?;
    let mut biome_names = Vec::with_capacity(names);
    let mut char_at = 0usize;
    for _ in 0..names {
        let n = usize::try_from(r.next()?).ok()?;
        biome_names.push(chars.get(char_at..char_at + n)?);
        char_at += n;
    }
    let (mut long_at, mut byte_at) = (0usize, 0usize);
    let mut container = |r: &mut Reader<'a>, global: Option<&'a [u32]>| -> Option<Container<'a>> {
        let bits = usize::try_from(r.next()?).ok()?;
        let size = usize::try_from(r.next()?).ok()?;
        let words = usize::try_from(r.next()?).ok()?;
        let labels = match global {
            Some(labels) => labels,
            None => {
                let labels = r.take(size)?;
                if labels.iter().any(|&l| l < 0) {
                    return None;
                }
                as_u32(labels)
            }
        };
        let w = longs.get(long_at..long_at + words)?;
        long_at += words;
        Some(Container { bits, words: w, labels })
    };
    let mut sections = Vec::with_capacity(count);
    for _ in 0..count {
        let y = r.next()?;
        let flags = r.next()?;
        let states = if flags & 1 != 0 {
            let kind = r.next()?;
            let states = container(&mut r, if kind == 1 { Some(&vocabulary.labels) } else if kind == 0 { None } else { return None })?;
            let biomes = container(&mut r, None)?;
            Some((states, biomes))
        } else {
            None
        };
        let mut light = |on: bool| -> Option<Option<&'a [u8]>> {
            if !on {
                return Some(None);
            }
            let layer = bytes.get(byte_at..byte_at + 2048)?;
            byte_at += 2048;
            Some(Some(layer))
        };
        let block_light = light(flags & 2 != 0)?;
        let sky_light = light(flags & 4 != 0)?;
        sections.push(Section { y, states, block_light, sky_light });
    }
    (r.at == ints.len() && long_at == longs.len() && byte_at == bytes.len() && char_at == chars.len()).then_some((sections, biome_names))
}
