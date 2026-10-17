use super::*;
use std::io::Read;
struct Reader(Vec<u8>, usize);
impl Reader {
    fn bytes(&mut self, n: usize) -> &[u8] {
        let start = self.1;
        self.1 += n;
        &self.0[start..self.1]
    }
    fn int(&mut self) -> i32 {
        i32::from_be_bytes(self.bytes(4).try_into().unwrap())
    }
    fn long(&mut self) -> u64 {
        u64::from_be_bytes(self.bytes(8).try_into().unwrap())
    }
    fn boolean(&mut self) -> bool {
        self.bytes(1)[0] != 0
    }
    fn text(&mut self) -> String {
        let n = u16::from_be_bytes(self.bytes(2).try_into().unwrap()) as usize;
        String::from_utf8(self.bytes(n).to_vec()).unwrap()
    }
}
#[test]
fn actual_frozen_histories_match_order_failure_prefix_and_membership() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!("../../../../../test/resources/world/chunk/frozen-chunk-tasks.bin.gz")
            .as_slice(),
    )
    .read_to_end(&mut bytes)
    .unwrap();
    let mut r = Reader(bytes, 0);
    assert_eq!(r.int(), 0x43545131);
    assert_eq!(r.int(), 5);
    for _ in 0..5 {
        assert!(!r.text().is_empty());
        assert_eq!(r.text().len(), 64);
    }
    assert!(r.text().contains("Actual Frozen server chunk-task queue"));
    let levels = r.int() as usize;
    assert_eq!(r.int(), 8);
    for seed in 0..8 {
        let mut q = Queue::new(levels).unwrap();
        for step in 0..r.int() {
            let action = r.int();
            let pos = r.long();
            let a = r.int();
            let b = r.int();
            let token = r.int() as u64;
            let exception = r.text();
            let work = r.boolean();
            let text = r.text();
            let pending = r.int() as usize;
            let result = r.boolean();
            let expected = if result {
                let pos = r.long();
                let values: Vec<_> = (0..r.int()).map(|_| r.int() as u64 + 1).collect();
                Some((pos, values))
            } else {
                None
            };
            let status = q.change(
                action,
                pos,
                a,
                if action == 0 { token + 1 } else { b as u64 },
            );
            assert_eq!(
                status == 2 || status == 3,
                !exception.is_empty(),
                "seed{seed} step{step}"
            );
            if !exception.is_empty() {
                assert_eq!(exception, "java.lang.ArrayIndexOutOfBoundsException");
            }
            assert_eq!(q.top < levels, work, "seed{seed} step{step}");
            assert_eq!(format!("frozen {}...", q.top), text);
            assert_eq!(q.pending, pending, "seed{seed} step{step}");
            if let Some((pos, tasks)) = expected {
                assert_eq!(status, 0);
                assert_eq!(q.output_pos, pos);
                assert_eq!(q.output, tasks, "seed{seed} step{step}");
            } else if action == 3 {
                assert_eq!(status, 1);
            }
            assert_eq!(
                q.pending,
                q.levels
                    .iter()
                    .flat_map(|l| l.entries.values())
                    .map(|&index| q.chunks.slots[index as usize].tasks.count)
                    .sum::<usize>()
            );
            for level in &q.levels {
                let mut cursor = level.first;
                let mut previous = NONE;
                let mut count = 0;
                while cursor != NONE {
                    let entry = &q.chunks.slots[cursor as usize];
                    assert_eq!(level.entries[&entry.pos], cursor);
                    assert_eq!(entry.previous, previous);
                    assert!(entry.tasks.count > 0);
                    previous = cursor;
                    cursor = entry.next;
                    count += 1;
                    assert!(count <= level.entries.len());
                }
                assert_eq!(previous, level.last);
                assert_eq!(count, level.entries.len());
            }
        }
    }
    assert_eq!(r.1, r.0.len());
}
// Validate completed scalar projections independently of the Java cache on every
// boundary operation; the primary atomic header remains the diagnostic owner.
unsafe fn native_change(owner: u64, action: i32, pos: u64, a: i32, b: u64, result: u64) -> i32 {
    use std::sync::atomic::{AtomicU64, Ordering};
    let word = ffi::mattmc_chunk_tasks_change_v4(owner, action, pos, a, b, result);
    let header = ffi::mattmc_chunk_tasks_header(owner as *const ffi::Owner).cast::<u8>();
    let pending = (&*header.add(8).cast::<AtomicU64>()).load(Ordering::Acquire);
    let next_id = (&*header.add(16).cast::<AtomicU64>()).load(Ordering::Acquire);
    assert_eq!(word >> 32, next_id);
    let levels = header.add(4).cast::<i32>().read();
    assert_eq!(word & 0x100 != 0, levels <= 64 && pending < 4096);
    (word & 0xff) as i32
}
#[test]
fn independent_owners_release_all_primary_membership_and_preserve_batch_views() {
    unsafe {
        let a = ffi::mattmc_chunk_tasks_create(4);
        let b = ffi::mattmc_chunk_tasks_create(4);
        assert!(!a.is_null() && !b.is_null());
        let mut metadata = [0u64; 3];
        let ptr = metadata.as_mut_ptr();
        assert_eq!(native_change(a as u64, 0, 0, 0, 1, ptr as u64), 0);
        assert_eq!(native_change(a as u64, 0, 0, 1, 2, ptr as u64), 0);
        assert_eq!(native_change(b as u64, 0, 0, 0, 1, ptr as u64), 0);
        assert_eq!(native_change(a as u64, 2, 0, 1, 0, ptr as u64), 0);
        assert_eq!(metadata[1], 2);
        assert_eq!(
            std::slice::from_raw_parts(metadata[0] as *const u64, 2),
            [1, 2]
        );
        assert_eq!(native_change(a as u64, 3, 0, 0, 0, ptr as u64), 1);
        assert_eq!(native_change(b as u64, 3, 0, 0, 0, ptr as u64), 0);
        assert_eq!(
            std::slice::from_raw_parts(metadata[0] as *const u64, 1),
            [1]
        );
        assert_eq!(native_change(a as u64, 0, 0, 4, 1, ptr as u64), 2);
        assert_eq!(native_change(a as u64, 0, 0, 0, 0, ptr as u64), 4);
        ffi::mattmc_chunk_tasks_destroy(a);
        ffi::mattmc_chunk_tasks_destroy(b);
        assert!(ffi::mattmc_chunk_tasks_create(0).is_null());
        assert!(ffi::mattmc_chunk_tasks_create(4097).is_null());
    }
}
#[test]
fn task_slab_capacity_tracks_peak_pending_not_history() {
    let mut q = Queue::new(4).unwrap();
    for round in 0..80 {
        for i in 0..2048 {
            assert_eq!(q.change(0, i % 16, (i % 4) as i32, i + 1), 0);
        }
        assert_eq!(q.tasks.slots.len(), 2048, "round {round}");
        assert_eq!(q.chunks.slots.len(), 16, "round {round}");
        for i in 0..8 {
            q.change(2, i, 1, 0);
        }
        while q.top < 4 {
            q.change(3, 0, 0, 0);
        }
        assert_eq!(q.pending, 0);
        assert_eq!(q.tasks.slots.len(), 2048);
        let mut index = q.tasks.free;
        let mut free = 0;
        while index != NONE {
            index = q.tasks.slots[index as usize].next;
            free += 1;
            assert!(free <= 2048);
        }
        assert_eq!(free, 2048);
        let mut index = q.chunks.free;
        let mut free = 0;
        while index != NONE {
            index = q.chunks.slots[index as usize].next;
            free += 1;
            assert!(free <= 16);
        }
        assert_eq!(free, 16);
    }
}

#[test]
fn boundary_call_mode_follows_primary_pending_and_priority_limits() {
    unsafe {
        for levels in [4, 65] {
            let owner = ffi::mattmc_chunk_tasks_create(levels);
            assert!(!owner.is_null());
            let mut metadata = [0u64; 3];
            let result = metadata.as_mut_ptr() as u64;
            for i in 0..4097 {
                assert_eq!(
                    native_change(
                        owner as u64,
                        0,
                        i % 64,
                        (i % levels as u64) as i32,
                        i + 1,
                        result
                    ),
                    0
                );
            }
            // Each call's next-row/call-mode projection is checked against the
            // independent atomic header, across growth and return below4096.
            let mut popped = 0;
            while native_change(owner as u64, 3, 0, 0, 0, result) == 0 {
                popped += metadata[1];
            }
            assert_eq!(popped, 4097);
            ffi::mattmc_chunk_tasks_destroy(owner);
        }
    }
}
