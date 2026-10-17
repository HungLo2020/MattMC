use super::*;
use crate::world::level::levelgen::noise_fill::section::pack;

// 0 air, 1 stone, 2 cave air.
static FLAGS: [u8; 3] = [FLAG_AIR | FLAG_AIR_BLOCK, FLAG_BLOCKS_MOTION, FLAG_AIR];

#[test]
fn retained_capture_owns_snapshot_and_preserves_aliased_section_local_counts() {
    use crate::world::level::chunk::{counters, inputs, live};
    use std::sync::Arc;
    let block = Arc::new(live::Owner::load(0, 0, &[1], &[], 3, 15).unwrap());
    let low = Arc::new(counters::Owner::new(0));
    let high = Arc::new(counters::Owner::new(4096));
    let p = Arc::as_ptr(&block) as usize;
    let pairs = [p, Arc::as_ptr(&low) as usize, p, Arc::as_ptr(&high) as usize];
    let owner = unsafe { inputs::mattmc_chunk_inputs_create(pairs.as_ptr(), 2, -16) };
    assert!(!owner.is_null());
    let heights = pack(&[32; 256], ceil_log2(33));
    let stage = ffi::capture_retained(unsafe { &*owner }, 15, &FLAGS, &heights, &heights).unwrap_or_else(|e| panic!("capture status {e}"));
    assert_eq!(stage.get(0, -16, 0), -2); // Shared blocks do not imply shared counters.
    assert_eq!(stage.get(0, 0, 0), 1);
    block.write(0, 2).unwrap();
    high.set([0, 0, 0]);
    assert_eq!(stage.get(0, 0, 0), 1);
    let next = ffi::capture_retained(unsafe { &*owner }, 15, &FLAGS, &heights, &heights).unwrap_or_else(|e| panic!("capture status {e}"));
    assert_eq!(next.get(0, 0, 0), -2);
    assert_eq!(ffi::capture_retained(unsafe { &*owner }, 14, &FLAGS, &heights, &heights).err(), Some(u64::MAX));
    assert_eq!(ffi::capture_retained(unsafe { &*owner }, 15, &FLAGS, &[], &heights).err(), Some(0));
    unsafe { inputs::mattmc_chunk_inputs_release(owner); }
    drop(block); drop(low); drop(high);
    assert_eq!(stage.get(0, 0, 0), 1); // Captures retain no input-owner lifetime.
}

#[test]
fn heightmap_scan_reads_only_air_sections_as_air() {
    let heights = pack(&[16; 256], ceil_log2(49));
    let sections = vec![
        (0, 0, vec![1], vec![], [4096, 0, 0]),
        (0, 0, vec![2], vec![], [0, 0, 0]),
        (0, 0, vec![0], vec![], [0, 0, 0]),
    ];
    let mut chunk = ProtoStorage::new(0, 48, sections, 15, &FLAGS, &heights, &heights).unwrap();
    chunk.set_block_state(3, 40, 5, 1).unwrap();
    assert_eq!(chunk.height(3, 5), 40);
    assert_eq!(chunk.get(3, 20, 5), -2, "a cave-air section reads as ProtoChunk's AIR");
    // Removing the only block scans down through two only-air sections.
    chunk.set_block_state(3, 40, 5, 0).unwrap();
    assert_eq!(chunk.height(3, 5), 15);
    assert_eq!(chunk.heightmap_raw(true), chunk.heightmap_raw(false));
}

// 0 air, 1 stone, 2 cave air, 3 a randomly ticking block, 4 water.
static TICKS: [u8; 5] = [FLAG_AIR | FLAG_AIR_BLOCK, FLAG_BLOCKS_MOTION, FLAG_AIR, FLAG_BLOCKS_MOTION | FLAG_RANDOM_TICKS, FLAG_FLUID];

#[test]
fn writes_keep_proto_chunk_counters_and_air_shortcut() {
    let heights = pack(&[16; 256], ceil_log2(33));
    let sections = vec![(0, 0, vec![3], vec![], [4096, 4096, 0]), (0, 0, vec![2], vec![], [0, 0, 0])];
    let mut chunk = ProtoStorage::new(0, 32, sections, 15, &TICKS, &heights, &heights).unwrap();
    // AIR into an only-air section is skipped: the cave-air section stays untouched.
    chunk.set_block_state(1, 20, 1, 0).unwrap();
    assert!(chunk.section(1).is_none());
    // Replacing a ticking block decrements the ticking count; water is non-empty and a fluid.
    chunk.set_block_state(1, 3, 1, 1).unwrap();
    chunk.set_block_state(2, 3, 1, 4).unwrap();
    let (_, palette, _, counts) = chunk.section(0).unwrap();
    assert_eq!(palette, &[3, 1, 4]);
    assert_eq!(counts, [4096, 4094, 1]);
    // Marks are the stage's: storage writes add none.
    assert!(chunk.post_process().is_empty());
}
