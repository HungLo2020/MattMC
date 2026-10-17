//! Primary world random-position sequence and callback-bounded random ticking.
//! Selection reads live section storage directly. No reader survives a Java
//! behavior callback; the next selection observes mutations and palette growth.
use super::chunk::{counters, inputs::{Chunk, Sections}, live};
use std::sync::{Arc, atomic::{AtomicI32, Ordering}};

pub struct TickChunk { chunk: Arc<Chunk>, leases: Box<[live::WorldLease]> }
#[no_mangle]
pub unsafe extern "C" fn mattmc_random_ticks_bind(chunk: *const Chunk) -> *mut TickChunk {
    if chunk.is_null() { return std::ptr::null_mut(); }
    let sections = Sections::Retained(&*chunk);
    let Some(leases) = (0..sections.len()).map(|i| sections.block(i).world_lease()).collect::<Option<Vec<_>>>()
        else { return std::ptr::null_mut(); };
    Arc::increment_strong_count(chunk);
    Box::into_raw(Box::new(TickChunk { chunk: Arc::from_raw(chunk), leases: leases.into_boxed_slice() }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_random_ticks_unbind(chunk: *mut TickChunk) {
    if !chunk.is_null() { drop(Box::from_raw(chunk)); }
}

pub struct Sequence(AtomicI32);
impl Sequence {
    fn new(seed: i32) -> Self { Self(AtomicI32::new(seed)) }
    fn next(&self) -> i32 {
        // World mutation remains thread-confined, like Level's original field.
        // Atomic storage keeps accidental readers defined without imposing a
        // compare/exchange loop on every simulation draw.
        let next = self.0.load(Ordering::Relaxed).wrapping_mul(3).wrapping_add(1_013_904_223);
        self.0.store(next, Ordering::Relaxed);
        next >> 2
    }
}

/// consumed attempts, shifted sequence bits, selected StateID (-1 for a
/// compatibility read). A call may skip only states with no behavior callback.
fn select(sequence: &Sequence, blocks: &live::Owner, attempts: i32,
    needs_callback: impl Fn(u32) -> bool, output: &mut [i32; 3]) -> i32 {
    *output = [0, 0, -1];
    let Some(reader) = blocks.world_reader() else { return -1; };
    for _ in 0..attempts.clamp(0, 4096) {
        let bits = sequence.next();
        output[0] += 1;
        output[1] = bits;
        let index = ((bits & 15) | ((bits >> 8 & 15) << 4) | ((bits >> 16 & 15) << 8)) as usize;
        let Some(state) = reader.get(index) else { return 1; };
        if needs_callback(state) {
            output[2] = state as i32;
            return 1;
        }
    }
    0
}

#[no_mangle]
pub extern "C" fn mattmc_random_ticks_create(seed: i32) -> *mut Sequence {
    Box::into_raw(Box::new(Sequence::new(seed)))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_random_ticks_release(owner: *mut Sequence) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_random_ticks_next(owner: *const Sequence) -> i32 {
    (*owner).next()
}
/// Owners and three writable i32s are pinned by the caller for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_random_ticks_select(owner: *const Sequence,
    blocks: *const live::Owner, attempts: i32, output: *mut [i32; 3]) -> i32 {
    if owner.is_null() || blocks.is_null() || output.is_null() || attempts <= 0 { return -1; }
    let policies = counters::installed_policies();
    select(&*owner, &*blocks, attempts, |state| {
        policies.and_then(|p| p.get(state as usize)).and_then(|p| *p)
            .is_none_or(|p| p.random_tick_callbacks())
    }, &mut *output)
}

/// Cursor: section index, attempts already consumed (-1 before eligibility),
/// shifted position bits, StateID. No callback or lock survives this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_random_ticks_chunk(owner: *const Sequence,
    chunk: *const TickChunk, speed: i32, cursor: *mut [i32; 4]) -> i64 {
    if owner.is_null() || chunk.is_null() || cursor.is_null() || speed <= 0 { return -1; }
    let c = &mut *cursor;
    let chunk = &*chunk;
    let sections = Sections::Retained(&chunk.chunk);
    if c[0] < 0 || c[0] as usize > sections.len() || c[1] < -1 || c[1] > speed { return -1; }
    let policies = counters::installed_policies();
    let mut budget = 4096;
    while (c[0] as usize) < sections.len() {
        let index = c[0] as usize;
        if c[1] == -1 {
            let counts = sections.counter(index).unwrap().packed();
            if (counts >> 16) as i16 <= 0 && (counts >> 32) as i16 <= 0 {
                c[0] += 1; continue;
            }
            c[1] = 0;
        }
        if c[1] == speed { c[0] += 1; c[1] = -1; continue; }
        if budget == 0 { return 0; }
        let blocks = sections.block(index);
        let lease = &chunk.leases[index];
        if !lease.valid(blocks) { return -1; }
        while c[1] < speed && budget > 0 {
            let bits = (*owner).next(); c[1] += 1; budget -= 1; c[2] = bits; c[3] = -1;
            let cell = ((bits & 15) | ((bits >> 8 & 15) << 4) | ((bits >> 16 & 15) << 8)) as usize;
            let Some(state) = lease.get(blocks, cell) else { return tick_result(c); };
            if policies.and_then(|p| p.get(state as usize)).and_then(|p| *p)
                .is_none_or(|p| p.random_tick_callbacks()) {
                c[3] = state as i32; return tick_result(c);
            }
        }
    }
    0
}

// One scalar result describes the callback. The native cursor stays in its
// scoped buffer across callbacks; nested queries borrow a different buffer.
fn tick_result(c: &[i32; 4]) -> i64 {
    (1i64 << 62) | ((i64::from(c[3]) + 1) << 32) | (i64::from(c[0]) << 24) | i64::from(c[2] & 0x00ff_ffff)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn section() -> live::Owner { live::Owner::load(0, 0, &[0], &[], 65535, 16).unwrap() }
    #[test]
    fn signed_sequence_matches_java_wrapping_recurrence() {
        for seed in [0, 1, -1, i32::MIN, i32::MAX, 0x12345678] {
            let owner = Sequence::new(seed); let mut expected = seed;
            for _ in 0..10000 {
                expected = expected.wrapping_mul(3).wrapping_add(1_013_904_223);
                assert_eq!(owner.next(), expected >> 2);
            }
        }
    }
    #[test]
    fn skipped_attempts_are_bounded_and_share_scalar_sequence() {
        let owner = Sequence::new(71); let reference = Sequence::new(71);
        let blocks = section(); let mut output = [0; 3];
        assert_eq!(select(&owner, &blocks, 5000, |_| false, &mut output), 0);
        assert_eq!(output[0], 4096);
        for _ in 0..4096 { reference.next(); }
        assert_eq!(owner.next(), reference.next());
    }
    #[test]
    fn mutation_and_retirement_are_observed_between_callbacks() {
        let owner = Sequence::new(71); let blocks = section(); let mut output = [0; 3];
        assert_eq!(select(&owner, &blocks, 3, |_| true, &mut output), 1);
        assert_eq!(output[0], 1); assert_eq!(output[2], 0);
        for i in 0..4096 { blocks.write(i, 37).unwrap(); }
        assert_eq!(select(&owner, &blocks, 2, |_| true, &mut output), 1);
        assert_eq!(output[2], 37);
        let before = owner.0.load(Ordering::Relaxed);
        blocks.invalidate();
        assert_eq!(select(&owner, &blocks, 1, |_| true, &mut output), -1);
        assert_eq!(output[0], 0); assert_eq!(owner.0.load(Ordering::Relaxed), before);
    }
}
