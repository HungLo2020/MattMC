//! One native allocation for a chunk's fresh sections.
//!
//! `ChunkAccess.replaceMissingSections` gives each missing section a
//! single-value block owner, a single-value biome owner and zeroed counters.
//! Created one by one, each is a separate Java owner with its own cleanup
//! registration; DH world generation creates hundreds of chunks a second.
//! A slab creates them together and Java holds one owner for all of them.
//! Each item stays an ordinary `Arc` owner, so consumers that retain one
//! (chunk inputs, residency, generation stages) keep working unchanged and
//! outlive the slab safely.
use super::{biomes, counters, live};
use std::sync::Arc;

pub(crate) struct Slab {
    // Held only to keep each item's Arc alive for Java's borrowed pointers.
    #[allow(dead_code)] blocks: Box<[Arc<live::Owner>]>,
    #[allow(dead_code)] biomes: Box<[Arc<biomes::Owner>]>,
    #[allow(dead_code)] counters: Box<[Arc<counters::Owner>]>,
}

/// Fresh sections: block owners holding only `block_state`, biome owners
/// holding only `biome`, zeroed counters. The width/limit arguments are what
/// `mattmc_live_section_create` / `mattmc_live_biome_create` receive for a
/// fresh single-value container. `output` receives `3 * count` borrowed
/// pointers: every block owner, then every biome owner, then every counter.
/// Returns null for invalid input or allocation failure.
///
/// # Safety
/// `output` holds `3 * count` aligned words. The pointers stay valid until
/// `mattmc_chunk_slab_release`, and retaining one needs its own strong count.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_slab_create(
    count: i32,
    block_state: i32, block_requested: i32, block_limit: i32, block_global_bits: i32,
    biome: i32, biome_requested: i32, biome_limit: i32, biome_global_bits: i32,
    output: *mut usize,
) -> *mut Slab {
    if !(1..=4096).contains(&count) || output.is_null() || output as usize % align_of::<usize>() != 0
        || block_state < 0 || biome < 0 || !(0..=32).contains(&block_requested) || !(0..=32).contains(&biome_requested) {
        return std::ptr::null_mut();
    }
    let count = count as usize;
    let mut blocks = Vec::new();
    let mut biome_owners = Vec::new();
    let mut counter_owners = Vec::new();
    if blocks.try_reserve_exact(count).is_err() || biome_owners.try_reserve_exact(count).is_err()
        || counter_owners.try_reserve_exact(count).is_err() {
        return std::ptr::null_mut();
    }
    for _ in 0..count {
        let Some(block) = live::Owner::load(0, block_requested as usize, &[block_state], &[],
            block_limit as u32, block_global_bits as usize) else { return std::ptr::null_mut(); };
        let Some(owner) = biomes::Owner::load(0, biome_requested as usize, &[biome as u32], &[],
            biome_limit as u32, biome_global_bits as usize) else { return std::ptr::null_mut(); };
        blocks.push(Arc::new(block));
        biome_owners.push(Arc::new(owner));
        counter_owners.push(Arc::new(counters::Owner::new(0)));
    }
    let output = std::slice::from_raw_parts_mut(output, 3 * count);
    for i in 0..count {
        output[i] = Arc::as_ptr(&blocks[i]) as usize;
        output[count + i] = Arc::as_ptr(&biome_owners[i]) as usize;
        output[2 * count + i] = Arc::as_ptr(&counter_owners[i]) as usize;
    }
    Box::into_raw(Box::new(Slab {
        blocks: blocks.into_boxed_slice(),
        biomes: biome_owners.into_boxed_slice(),
        counters: counter_owners.into_boxed_slice(),
    }))
}

/// Drop the slab's own reference to every item.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_slab_release(slab: *mut Slab) {
    if !slab.is_null() { drop(Box::from_raw(slab)); }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn slab_items_match_fresh_owners_and_outlive_the_slab() {
        let mut output = [0usize; 6];
        let slab = unsafe { mattmc_chunk_slab_create(2, 0, 0, 30000, 15, 3, 0, 64, 6, output.as_mut_ptr()) };
        assert!(!slab.is_null());
        let (block, biome) = (output[1] as *const live::Owner, output[3] as *const biomes::Owner);
        assert_eq!(unsafe { &*(output[5] as *const counters::Owner) }.packed(), 0);
        assert_eq!(unsafe { &*biome }.snapshot().1[63], 3, "single-value biome");
        // A consumer retains items, as chunk inputs do, then the slab goes away.
        let block = unsafe { Arc::increment_strong_count(block); Arc::from_raw(block) };
        let biome = unsafe { Arc::increment_strong_count(biome); Arc::from_raw(biome) };
        unsafe { mattmc_chunk_slab_release(slab) };
        assert_eq!(block.capture()[123], 0, "single-value air");
        assert_eq!(block.write(5, 7), Some((0, true)), "writes grow like a created owner");
        assert_eq!(block.capture()[5], 7);
        assert_eq!(biome.write(1, 4).map(|r| r.1), Some(true));
        assert!(unsafe { mattmc_chunk_slab_create(0, 0, 0, 1, 1, 0, 0, 1, 1, output.as_mut_ptr()) }.is_null());
    }
}
