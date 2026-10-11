//! Saved palette policy produces primary live owners directly. The CPU spans
//! are borrowed for one call; no decoded Java storage or heap pointer is retained.
use std::borrow::Cow;
use std::sync::Arc;

#[derive(Clone, Copy)]
enum Kind {
    Blocks,
    Biomes,
}

struct Storage<'a> {
    bits: usize,
    requested: usize,
    palette: &'a [i32],
    words: Cow<'a, [u64]>,
}

fn prepare<'a>(
    kind: Kind,
    palette: &'a [i32],
    words: &'a [u64],
    limit: u32,
    global_bits: usize,
) -> Option<Storage<'a>> {
    let (cells, max_local, min_global) = match kind {
        Kind::Blocks => (4096usize, 8, 9),
        Kind::Biomes => (64usize, 3, 4),
    };
    if palette.is_empty()
        || palette.len() > 65536
        || limit == 0
        || limit > 65535
        || !(min_global..=16).contains(&global_bits)
        || limit as usize > 1 << global_bits
        || palette.iter().any(|&id| id < 0 || id as u32 >= limit)
    {
        return None;
    }
    let source_bits = usize::BITS as usize - (palette.len() - 1).leading_zeros() as usize;
    let requested = match kind {
        Kind::Blocks if (1..=4).contains(&source_bits) => 4,
        _ => source_bits,
    };
    let expected = if requested == 0 {
        0
    } else {
        cells.div_ceil(64 / requested)
    };
    if words.len() != expected {
        return None;
    }
    if requested <= max_local {
        // Raw local padding, palette order and unused slots remain unchanged.
        return Some(Storage {
            bits: requested,
            requested,
            palette,
            words: Cow::Borrowed(words),
        });
    }
    let source_per = 64 / requested;
    let source_mask = (1u64 << requested) - 1;
    let target_per = 64 / global_bits;
    let mut output = vec![0u64; cells.div_ceil(target_per)];
    for index in 0..cells {
        let local = ((words[index / source_per] >> (index % source_per * requested)) & source_mask)
            as usize;
        let value = *palette.get(local)? as u64;
        output[index / target_per] |= value << (index % target_per * global_bits);
    }
    Some(Storage {
        bits: global_bits,
        requested,
        palette: &[],
        words: Cow::Owned(output),
    })
}

/// # Safety
/// Input spans are aligned and live for this synchronous call, with the stated
/// lengths. They may be Java critical heap borrows. Outputs own independent data.
unsafe fn inputs<'a>(
    words: *const u64,
    length: i32,
    palette: *const i32,
    count: i32,
) -> Option<(&'a [u64], &'a [i32])> {
    if !(0..=1024).contains(&length)
        || !(1..=65536).contains(&count)
        || (length != 0 && (words.is_null() || words as usize % 8 != 0))
        || palette.is_null()
        || palette as usize % 4 != 0
    {
        return None;
    }
    let words = if length == 0 {
        &[]
    } else {
        std::slice::from_raw_parts(words, length as usize)
    };
    Some((words, std::slice::from_raw_parts(palette, count as usize)))
}

/// # Safety
/// See inputs. Returns one owned Arc, released with mattmc_live_section_release.
#[no_mangle]
pub unsafe extern "C" fn mattmc_saved_blocks_load(
    words: *const u64,
    length: i32,
    palette: *const i32,
    count: i32,
    limit: i32,
    global_bits: i32,
) -> *const super::super::live::Owner {
    let Some((words, palette)) = inputs(words, length, palette, count) else {
        return std::ptr::null();
    };
    load_blocks(palette, words, limit as u32, global_bits as usize)
        .map_or(std::ptr::null(), |owner| Arc::into_raw(Arc::new(owner)))
}

pub(crate) fn load_blocks(
    palette: &[i32],
    words: &[u64],
    limit: u32,
    global_bits: usize,
) -> Option<super::super::live::Owner> {
    let storage = prepare(Kind::Blocks, palette, words, limit, global_bits)?;
    super::super::live::Owner::load(
        storage.bits,
        storage.requested,
        storage.palette,
        &storage.words,
        limit,
        global_bits,
    )
}

/// # Safety
/// See inputs. Returns one owned Arc, released with mattmc_live_biome_release.
#[no_mangle]
pub unsafe extern "C" fn mattmc_saved_biomes_load(
    words: *const u64,
    length: i32,
    palette: *const i32,
    count: i32,
    limit: i32,
    global_bits: i32,
) -> *const super::super::biomes::Owner {
    let Some((words, palette)) = inputs(words, length, palette, count) else {
        return std::ptr::null();
    };
    load_biomes(palette, words, limit as u32, global_bits as usize)
        .map_or(std::ptr::null(), |owner| Arc::into_raw(Arc::new(owner)))
}

pub(crate) fn load_biomes(
    palette: &[i32],
    words: &[u64],
    limit: u32,
    global_bits: usize,
) -> Option<super::super::biomes::Owner> {
    let storage = prepare(Kind::Biomes, palette, words, limit, global_bits)?;
    let palette: Vec<u32> = storage.palette.iter().map(|&id| id as u32).collect();
    super::super::biomes::Owner::load(
        storage.bits,
        storage.requested,
        &palette,
        &storage.words,
        limit,
        global_bits,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn local_saved_padding_and_unused_entries_are_preserved() {
        for (kind, cells, counts) in [
            (
                Kind::Blocks,
                4096usize,
                vec![1, 2, 16, 17, 32, 33, 128, 129, 256],
            ),
            (Kind::Biomes, 64, vec![1, 2, 3, 4, 5, 8]),
        ] {
            for count in counts {
                let palette: Vec<i32> = (0..count as i32).collect();
                let bits = if count == 1 {
                    0
                } else {
                    usize::BITS as usize - (count - 1usize).leading_zeros() as usize
                };
                let bits = if matches!(kind, Kind::Blocks) && bits != 0 {
                    bits.max(4)
                } else {
                    bits
                };
                let words = if bits == 0 {
                    vec![]
                } else {
                    vec![0; cells.div_ceil(64 / bits)]
                };
                let mut words = words;
                if bits != 0 {
                    let last = cells % (64 / bits);
                    if last != 0 {
                        *words.last_mut().unwrap() = u64::MAX << (last * bits);
                    }
                }
                let storage = prepare(kind, &palette, &words, 32768, 15).unwrap();
                assert_eq!(storage.bits, bits);
                assert_eq!(storage.palette, palette);
                assert_eq!(storage.words.as_ref(), words);
                assert!(matches!(storage.words, Cow::Borrowed(_)));
            }
        }
    }
    #[test]
    fn global_aliases_map_directly_and_padding_is_zero() {
        for (kind, cells, count) in [
            (Kind::Blocks, 4096usize, 257usize),
            (Kind::Biomes, 64, 9),
            (Kind::Biomes, 64, 513),
        ] {
            let palette: Vec<i32> = (0..count).map(|i| (i % 511) as i32).collect();
            let bits = usize::BITS as usize - (count - 1).leading_zeros() as usize;
            let mut words = vec![u64::MAX; cells.div_ceil(64 / bits)];
            let mask = (1u64 << bits) - 1;
            for i in 0..cells {
                let shift = i % (64 / bits) * bits;
                words[i / (64 / bits)] =
                    (words[i / (64 / bits)] & !(mask << shift)) | (((i % count) as u64) << shift);
            }
            let storage = prepare(kind, &palette, &words, 512, 9).unwrap();
            for i in 0..cells {
                assert_eq!(
                    (storage.words[i / 7] >> (i % 7 * 9)) & 511,
                    (i % count % 511) as u64
                );
            }
            assert!(storage.words.iter().all(|word| word >> 63 == 0));
            assert_eq!(storage.requested, bits);
            assert!(storage.palette.is_empty());
            words[0] = (words[0] & !mask) | mask;
            assert!(prepare(kind, &palette, &words, 512, 9).is_none());
        }
    }
    #[test]
    fn malformed_imports_decline_without_an_owner() {
        unsafe {
            let palette = [0, 1];
            let words = [0u64; 256];
            assert!(
                mattmc_saved_blocks_load(words.as_ptr(), 255, palette.as_ptr(), 2, 512, 9)
                    .is_null()
            );
            assert!(
                mattmc_saved_blocks_load(words.as_ptr(), 256, palette.as_ptr(), 2, -1, 9).is_null()
            );
            assert!(
                mattmc_saved_biomes_load(words.as_ptr(), 1025, palette.as_ptr(), 2, 512, 9)
                    .is_null()
            );
            let owner = mattmc_saved_blocks_load(std::ptr::null(), 0, palette.as_ptr(), 1, 512, 9);
            assert!(!owner.is_null());
            drop(Arc::from_raw(owner));
        }
    }
}
