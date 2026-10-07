//! Java bridge for chunk section serialization; see `NativeChunkSections`.
use super::{Biomes, Container, Encoder, Section, Tape, Vocabulary};
use super::load::{decode, Decoded, Lookup};
use std::cell::RefCell;
use std::collections::HashMap;

thread_local! {
    static ENCODER: RefCell<(Encoder, Vec<u8>)> = RefCell::new((Encoder::new(), Vec::new()));
    static DECODED: RefCell<Option<Decoded>> = const { RefCell::new(None) };
}

/// The block-state vocabulary built from the installed block registry
/// (labels are state ids). Returns 0 when the registry is not installed or
/// Java must encode its states. Never freed.
#[no_mangle]
pub extern "C" fn mattmc_chunk_sections_vocabulary() -> u64 {
    super::vocabulary::installed().map_or(0, |v| v as *const Vocabulary as u64)
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

/// A biome registry's names for loading: `lengths[i]` UTF-16 units of name `i`
/// in `chars`. Returns 0 on invalid input. Released with
/// `mattmc_chunk_biome_names_release`.
/// # Safety
/// The pointers address their stated counts for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_biome_names_create(chars: *const u16, char_len: i32, lengths: *const i32, count: i32) -> u64 {
    if lengths.is_null() || count < 0 || char_len < 0 || (char_len > 0 && chars.is_null()) {
        return 0;
    }
    let chars: &[u16] = if char_len == 0 { &[] } else { std::slice::from_raw_parts(chars, char_len as usize) };
    let lengths = std::slice::from_raw_parts(lengths, count as usize);
    let mut names = HashMap::new();
    let mut at = 0usize;
    for (index, &length) in lengths.iter().enumerate() {
        let Ok(length) = usize::try_from(length) else { return 0 };
        let Some(name) = chars.get(at..at + length) else { return 0 };
        names.entry(name.iter().flat_map(|u| u.to_le_bytes()).collect::<Vec<u8>>()).or_insert(index as i32);
        at += length;
    }
    Box::into_raw(Box::new(names)) as u64
}

/// # Safety
/// `handle` came from `mattmc_chunk_biome_names_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_biome_names_release(handle: u64) {
    if handle != 0 {
        drop(Box::from_raw(handle as *mut HashMap<Vec<u8>, i32>));
    }
}

/// Decodes a saved chunk's tape (see `load.rs`) for sections
/// `min_section..=max_section`, keeping the result for
/// `mattmc_chunk_sections_take`. Writes [int count, long count, byte count,
/// root tape length] to `sizes`. Returns 0, -2 to decline (Java parses the
/// tag), -1 for invalid input.
/// # Safety
/// `vocabulary` and `biomes` are live handles; `tape` addresses `tape_len`
/// bytes and `sizes` 4 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_decode(vocabulary: u64, biomes: u64, tape: *const u8, tape_len: i32, min_section: i32,
    max_section: i32, sizes: *mut i64) -> i32 {
    if vocabulary == 0 || biomes == 0 || tape.is_null() || tape_len < 0 || sizes.is_null() {
        return -1;
    }
    let vocabulary = &*(vocabulary as *const Vocabulary);
    let biomes = &*(biomes as *const HashMap<Vec<u8>, i32>);
    let lookup = vocabulary.lookup.get_or_init(|| Lookup::new(vocabulary));
    let tape = std::slice::from_raw_parts(tape, tape_len as usize);
    let Some(decoded) = decode(tape, lookup, biomes, min_section, max_section) else {
        DECODED.with(|d| *d.borrow_mut() = None);
        return -2;
    };
    let sizes = std::slice::from_raw_parts_mut(sizes, 4);
    sizes.copy_from_slice(&[decoded.ints.len() as i64, decoded.longs.len() as i64, decoded.bytes.len() as i64, decoded.root.len() as i64]);
    DECODED.with(|d| *d.borrow_mut() = Some(decoded));
    0
}

/// Copies and clears this thread's last decode. Returns 0, or -1 when there is none.
/// # Safety
/// The pointers address the sizes `mattmc_chunk_sections_decode` reported.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_take(ints: *mut i32, longs: *mut i64, bytes: *mut u8, root: *mut u8) -> i32 {
    let Some(decoded) = DECODED.with(|d| d.borrow_mut().take()) else { return -1 };
    let copy = |src: *const u8, dst: *mut u8, n: usize| if n > 0 { std::ptr::copy_nonoverlapping(src, dst, n) };
    copy(decoded.ints.as_ptr() as *const u8, ints as *mut u8, decoded.ints.len() * 4);
    copy(decoded.longs.as_ptr() as *const u8, longs as *mut u8, decoded.longs.len() * 8);
    copy(decoded.bytes.as_ptr(), bytes, decoded.bytes.len());
    copy(decoded.root.as_ptr(), root, decoded.root.len());
    0
}

/// Verification: copies state `state`'s fragment from the installed
/// vocabulary when `out` holds enough bytes. Returns its length, or -1
/// without a vocabulary or for an unknown state.
/// # Safety
/// A non-null `out` addresses `out_len` bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_fragment(state: i32, out: *mut u8, out_len: i32) -> i32 {
    let Some(v) = super::vocabulary::installed() else { return -1 };
    let Some(&start) = usize::try_from(state).ok().and_then(|s| v.offsets.get(s)) else { return -1 };
    let Some(&end) = v.offsets.get(state as usize + 1) else { return -1 };
    let fragment = &v.fragments[start as usize..end as usize];
    if !out.is_null() && out_len as usize >= fragment.len() {
        std::slice::from_raw_parts_mut(out, fragment.len()).copy_from_slice(fragment);
    }
    fragment.len() as i32
}
