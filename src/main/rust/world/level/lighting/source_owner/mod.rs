//! Owned source storage, producers and incremental policy for world lighting.
//! Reuses the actual production canonical scan; no VM pointers or callbacks retained.
use super::skylight_sources::scan;
use std::sync::{Arc, Mutex};

#[derive(Debug, PartialEq, Eq)]
pub enum Error {
    Bits(i32),
    Index(i32),
    Value(i32),
    WordCount,
    Allocation,
    Poisoned,
    Scan,
    Incomplete,
}
struct Store {
    min: i32,
    bits: usize,
    words: Vec<u64>,
}
#[derive(Clone)]
pub struct Source(Arc<Mutex<Store>>);
impl Source {
    pub fn from_bounds(low: i32, high: i32) -> Result<Self, Error> {
        let min = low.wrapping_sub(1);
        let span = high.wrapping_add(1).wrapping_sub(min).wrapping_add(1);
        // Original Mth.ceillog2: signed overflow maps non-power negative spans to zero.
        let bits = if span > 0 {
            32 - (span - 1).leading_zeros() as i32
        } else if span == i32::MIN {
            31
        } else {
            0
        };
        Self::new(min, bits)
    }

    pub fn new(min: i32, bits: i32) -> Result<Self, Error> {
        if !(1..=32).contains(&bits) {
            return Err(Error::Bits(bits));
        }
        let per = 64 / bits as usize;
        let count = 256usize.div_ceil(per);
        let mut words = Vec::new();
        words
            .try_reserve_exact(count)
            .map_err(|_| Error::Allocation)?;
        words.resize(count, 0);
        Ok(Self(Arc::new(Mutex::new(Store {
            min,
            bits: bits as usize,
            words,
        }))))
    }
    pub fn words(&self) -> Result<Vec<u64>, Error> {
        let store = self.0.lock().map_err(|_| Error::Poisoned)?;
        let mut copy = Vec::new();
        copy.try_reserve_exact(store.words.len())
            .map_err(|_| Error::Allocation)?;
        copy.extend_from_slice(&store.words);
        Ok(copy)
    }
    /// Actual packed-word replacement; the borrowed buffer is never retained.
    pub fn replace(&self, words: &[u64]) -> Result<(), Error> {
        let mut store = self.0.lock().map_err(|_| Error::Poisoned)?;
        if words.len() != store.words.len() {
            return Err(Error::WordCount);
        }
        store.words.copy_from_slice(words);
        Ok(())
    }
    pub fn get(&self, index: i32) -> Result<i32, Error> {
        let store = self.0.lock().map_err(|_| Error::Poisoned)?;
        let encoded = store.get(index)?;
        let y = encoded.wrapping_add(store.min);
        Ok(if y == store.min { i32::MIN } else { y })
    }
    pub fn highest(&self) -> Result<i32, Error> {
        let store = self.0.lock().map_err(|_| Error::Poisoned)?;
        let mut max = i32::MIN;
        for index in 0..256 {
            max = max.max(store.get(index)?);
        }
        let y = max.wrapping_add(store.min);
        Ok(if y == store.min { i32::MIN } else { y })
    }
    pub fn set(&self, index: i32, y: i32) -> Result<(), Error> {
        let mut store = self.0.lock().map_err(|_| Error::Poisoned)?;
        if !(0..256).contains(&index) {
            return Err(Error::Index(index));
        }
        let value = y.wrapping_sub(store.min);
        let mask = (1u64 << store.bits) - 1;
        if value < 0 || value as u64 > mask {
            return Err(Error::Value(value));
        }
        let bits = store.bits;
        scan::set_height(&mut store.words, index as usize, bits, value as u64);
        Ok(())
    }
    pub fn clear(&self) -> Result<(), Error> {
        let mut store = self.0.lock().map_err(|_| Error::Poisoned)?;
        let bits = store.bits;
        scan::clear(&mut store.words, bits);
        Ok(())
    }
    pub fn canonical_fill(&self) -> Result<CanonicalFill, Error> {
        let store = self.0.lock().map_err(|_| Error::Poisoned)?;
        let mut working = Vec::new();
        working
            .try_reserve_exact(store.words.len())
            .map_err(|_| Error::Allocation)?;
        working.extend_from_slice(&store.words);
        Ok(CanonicalFill {
            source: self.clone(),
            min: store.min,
            bits: store.bits,
            working,
            pending: [1; 256],
            above: [0; 256],
            failed: false,
        })
    }
}
impl Store {
    fn get(&self, index: i32) -> Result<i32, Error> {
        if !(0..256).contains(&index) {
            return Err(Error::Index(index));
        }
        let per = 64 / self.bits;
        let index = index as usize;
        Ok(
            ((self.words[index / per] >> ((index % per) * self.bits)) & ((1u64 << self.bits) - 1))
                as i32,
        )
    }
}
/// Actual pending producer owns source lifetime and its unpublished partial state.
/// Failed/abandoned canonical scans never publish a partial fallback result.
pub struct CanonicalFill {
    source: Source,
    min: i32,
    bits: usize,
    working: Vec<u64>,
    pending: [u8; 256],
    above: [u32; 256],
    failed: bool,
}
impl CanonicalFill {
    pub fn section(
        &mut self,
        words: &[u64],
        flags: &[u32],
        edges: &[u8],
        palette_bits: i32,
        base: i32,
        faces: usize,
    ) -> Result<i32, Error> {
        if self.failed {
            return Err(Error::Scan);
        }
        let header = [
            palette_bits,
            self.bits as i32,
            self.working.len() as i32,
            base,
            self.min,
            0,
            faces as i32,
            flags.len() as i32,
        ];
        match scan::section(
            words,
            flags,
            edges,
            &header,
            &mut self.pending,
            &mut self.above,
            &mut self.working,
        ) {
            Ok(remaining) => Ok(remaining),
            Err(()) => {
                self.failed = true;
                Err(Error::Scan)
            }
        }
    }
    /// Reads the actual section payload under its existing owner lock. No
    /// section pointer, lock, or borrow survives this call or publication.
    fn live_section(
        &mut self,
        reader: crate::world::level::chunk::live::StateReader<'_>,
        descriptors: &[u32],
        edges: &[u8],
        base: i32,
        faces: usize,
    ) -> Result<i32, Error> {
        if self.failed { return Err(Error::Scan); }
        let header = [0, self.bits as i32, self.working.len() as i32,
            base, self.min, 0, faces as i32, 0];
        let result = scan::section_states(false, |index| {
            descriptors.get(reader.get(index) as usize).copied().ok_or(())
        }, edges, &header, &mut self.pending, &mut self.above, &mut self.working);
        result.map_err(|()| { self.failed = true; Error::Scan })
    }
    pub fn finish(self) -> Result<(), Error> {
        if self.failed {
            return Err(Error::Scan);
        }
        if self.pending.iter().any(|&v| v != 0) {
            return Err(Error::Incomplete);
        }
        self.source.replace(&self.working)
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn direct_live_section_matches_packed_scan_in_every_owner_format() {
        use crate::world::level::chunk::live::Owner;
        let descriptors = [0, 1, 2, 65536, 65538, 4, 131072, 131076];
        let edges = [0, 0, 0, 0, 0, 1, 0, 1, 0];
        for bits in [0usize, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16] {
            let global = bits >= 9;
            let palette: Vec<i32> = if global { vec![] } else if bits == 0 { vec![3] }
                else { (0..8).rev().collect() };
            let mut words = vec![0u64; if bits == 0 { 0 } else { 4096usize.div_ceil(64 / bits) }];
            if bits != 0 {
                let per = 64 / bits;
                for index in 0..4096 {
                    let value = (index * 71 + index / 256 * 13) % 8;
                    words[index / per] |= (value as u64) << (index % per * bits);
                }
            }
            let owner = Owner::load(bits, bits, &palette, &words, 512,
                if global { bits } else { 9 }).unwrap();
            let packed_flags: Vec<u32> = if global { descriptors.to_vec() } else {
                palette.iter().map(|&id| descriptors[id as usize]).collect()
            };
            let actual = Source::new(-1, 6).unwrap();
            let reference = Source::new(-1, 6).unwrap();
            actual.replace(&[u64::MAX; 26]).unwrap();
            reference.replace(&[u64::MAX; 26]).unwrap();
            let mut live = actual.canonical_fill().unwrap();
            let mut packed = reference.canonical_fill().unwrap();
            for base in [16, 0] {
                let remaining = owner.with_state_reader(|reader|
                    live.live_section(reader, &descriptors, &edges, base, 3));
                assert_eq!(remaining, packed.section(&words, &packed_flags, &edges, bits as i32, base, 3));
                assert_eq!(live.working, packed.working);
                assert_eq!(live.pending, packed.pending);
                assert_eq!(live.above, packed.above);
            }
            live.finish().unwrap(); packed.finish().unwrap();
            assert_eq!(actual.words(), reference.words());
            // The reader guard must be gone; mutation cannot deadlock here.
            assert!(owner.write(0, 4).is_some());
            assert_eq!(actual.words(), reference.words());
        }
    }
    #[test]
    fn direct_scan_failure_keeps_source_unpublished_and_releases_section_guard() {
        use crate::world::level::chunk::live::Owner;
        let source = Source::new(-1, 6).unwrap();
        source.set(0, 5).unwrap();
        let before = source.words().unwrap();
        let mut words = vec![0u64; 256];
        words[3840 / 16] = 1 << 4; // Last Y, column1 refers to missing descriptor1.
        let owner = Owner::load(4, 4, &[0, 1], &words, 512, 9).unwrap();
        let mut fill = source.canonical_fill().unwrap();
        assert_eq!(owner.with_state_reader(|reader|
            fill.live_section(reader, &[1], &[0], 16, 1)), Err(Error::Scan));
        assert_ne!(fill.working, before);
        assert_eq!(source.words().unwrap(), before);
        assert!(owner.write(1, 0).is_some());
        assert_eq!(fill.finish(), Err(Error::Scan));
        assert_eq!(source.words().unwrap(), before);
    }
    #[test]
    fn direct_scan_pending_result_retains_source_but_no_section_reference() {
        use crate::world::level::chunk::live::Owner;
        let source = Source::new(-1, 6).unwrap();
        let weak = Arc::downgrade(&source.0);
        let mut fill = source.canonical_fill().unwrap();
        let owner = Owner::load(0, 0, &[0], &[], 512, 9).unwrap();
        drop(source);
        assert_eq!(owner.with_state_reader(|reader|
            fill.live_section(reader, &[1], &[0], 16, 1)), Ok(0));
        // Actual read is complete; the pending source can publish after the
        // section's native owner is destroyed, with no dangling borrowed state.
        drop(owner);
        assert!(weak.upgrade().is_some());
        assert!(fill.working.iter().any(|&word| word != 0));
        fill.finish().unwrap();
        assert!(weak.upgrade().is_none());
    }
    #[test]
    fn escaped_words_preserve_padding_and_signed_java_consumption() {
        let source = Source::new(-65, 5).unwrap();
        let raw = vec![u64::MAX; 22];
        source.replace(&raw).unwrap();
        assert_eq!(source.get(255), Ok(-34));
        source.clear().unwrap();
        let words = source.words().unwrap();
        assert_eq!(words[..21], [0xf000000000000000u64; 21]);
        assert_eq!(words[21], !((1u64 << 20) - 1));
        assert_eq!(source.get(255), Ok(i32::MIN));
        assert_eq!(source.highest(), Ok(i32::MIN));
        let signed = Source::new(-65, 32).unwrap();
        signed.replace(&[u64::MAX; 128]).unwrap();
        assert_eq!(signed.get(0), Ok(-66));
        assert_eq!(signed.highest(), Ok(-66));
    }
    #[test]
    fn invalid_write_preserves_actual_payload_and_index_precedes_value() {
        let source = Source::new(-65, 5).unwrap();
        source.set(17, -60).unwrap();
        let before = source.words().unwrap();
        assert_eq!(source.set(-1, i32::MIN), Err(Error::Index(-1)));
        assert_eq!(
            source.set(17, i32::MIN),
            Err(Error::Value(i32::MIN.wrapping_add(65)))
        );
        assert_eq!(source.words().unwrap(), before);
        assert_eq!(source.get(17), Ok(-60));
    }
    #[test]
    fn actual_canonical_producer_publishes_only_after_complete_scan() {
        let source = Source::new(-1, 6).unwrap();
        source.set(0, 5).unwrap();
        let before = source.words().unwrap();
        let mut abandoned = source.canonical_fill().unwrap();
        assert_eq!(abandoned.section(&[], &[0], &[0], -1, 16, 1), Ok(256));
        assert_eq!(source.words().unwrap(), before);
        assert_eq!(abandoned.finish(), Err(Error::Incomplete));
        let mut fill = source.canonical_fill().unwrap();
        assert_eq!(fill.section(&[], &[1], &[0], 0, 16, 1), Ok(0));
        assert_eq!(source.words().unwrap(), before);
        fill.finish().unwrap();
        for i in 0..256 {
            assert_eq!(source.get(i), Ok(32));
        }
        assert_eq!(source.highest(), Ok(32));
    }
    #[test]
    fn actual_scan_failure_after_partial_work_cannot_commit_or_overwrite_source() {
        let source = Source::new(-1, 6).unwrap();
        source.set(0, 5).unwrap();
        let before = source.words().unwrap();
        // A two-entry palette uses id1 in column1 but publishes a missing descriptor.
        // Column0 writes the real working buffer before the actual kernel rejects id1.
        let mut fill = source.canonical_fill().unwrap();
        let words = [2u64; 64];
        assert_eq!(fill.section(&words, &[1], &[0], 1, 16, 1), Err(Error::Scan));
        assert_ne!(fill.working, before);
        assert_eq!(source.words().unwrap(), before);
        assert_eq!(fill.finish(), Err(Error::Scan));
        assert_eq!(source.words().unwrap(), before);
    }
    #[test]
    fn pending_producer_owns_actual_store_until_drop_and_snapshot_survives_retirement() {
        let source = Source::new(-1, 6).unwrap();
        source.set(0, 5).unwrap();
        let snapshot = source.words().unwrap();
        let weak = Arc::downgrade(&source.0);
        let mut producer = source.canonical_fill().unwrap();
        drop(source);
        assert!(weak.upgrade().is_some());
        assert_eq!(producer.section(&[], &[1], &[0], 0, 16, 1), Ok(0));
        producer.finish().unwrap();
        assert!(weak.upgrade().is_none());
        assert_eq!(snapshot[0] & 63, 6);
    }
    #[test]
    fn real_retained_owner_is_detected_by_weak_lifetime_observation() {
        let source = Source::new(-1, 6).unwrap();
        let weak = Arc::downgrade(&source.0);
        let retained = source.clone();
        drop(source);
        assert!(weak.upgrade().is_some());
        drop(retained);
        assert!(weak.upgrade().is_none());
    }
}

#[path = "source_update.rs"]
pub mod source_update;

#[path = "source_ffi.rs"]
mod source_ffi;

#[path = "source_fill.rs"]
pub mod source_fill;

#[path="source_canonical.rs"]
mod source_canonical;

#[path="source_weak.rs"]
mod source_weak;

#[path="source_seed.rs"]
mod source_seed;
