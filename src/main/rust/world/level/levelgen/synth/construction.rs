//! Native leaf seed construction. RNG effects use the original VM source;
//! no source, borrowed pointer or callback is stored in a builder.
use super::state::Octave;
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
static FRAMES: AtomicU64 = AtomicU64::new(0);
static FRAME_CREATED: AtomicU64 = AtomicU64::new(0);
static FRAME_RETIRED: AtomicU64 = AtomicU64::new(0);
static LEAVES: AtomicU64 = AtomicU64::new(0);
static LEAF_CREATED: AtomicU64 = AtomicU64::new(0);
static LEAF_RETIRED: AtomicU64 = AtomicU64::new(0);
#[repr(C)]
pub(super) struct Leaf {
    offsets: [f64; 3],
    permutation: [u8; 256],
}
impl Drop for Leaf {
    fn drop(&mut self) {
        LEAVES.fetch_sub(1, Ordering::Relaxed);
        LEAF_RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
#[repr(C)]
struct Builder {
    header: [u64; 4],
    offsets: [f64; 3],
    scratch: [u8; 512],
    capacity: i32,
    step: usize,
    finished: bool,
    taken: bool,
}
impl Builder {
    fn new(simplex: bool) -> Self {
        FRAMES.fetch_add(1, Ordering::Relaxed);
        FRAME_CREATED.fetch_add(1, Ordering::Relaxed);
        Self {
            header: [0; 4],
            offsets: [0.; 3],
            scratch: [0; 512],
            capacity: if simplex { 512 } else { 256 },
            step: 0,
            finished: false,
            taken: false,
        }
    }
    fn next(&mut self, response: u64) -> i32 {
        if self.finished {
            return 0;
        }
        if self.step > 0 && self.step <= 3 {
            self.offsets[self.step - 1] = f64::from_bits(response) * 256.;
        }
        if self.step == 3 {
            for i in 0..256 {
                self.scratch[i] = i as u8;
            }
        }
        if self.step > 3 {
            let i = self.step - 4;
            let index = (i as i32).wrapping_add(response as i32);
            if index < 0 || index >= self.capacity {
                self.header = [index as u64, self.capacity as u64, 0, 0];
                self.finished = true;
                return -1;
            }
            self.scratch.swap(i, index as usize);
        }
        if self.step == 259 {
            self.finished = true;
            self.step = 260;
            return 0;
        }
        self.header = if self.step < 3 {
            [1, 0, 0, 0]
        } else {
            [2, (259 - self.step) as u64, 0, 0]
        };
        self.step += 1;
        1
    }
    fn take(&mut self) -> Option<Arc<Leaf>> {
        if !self.finished || self.step != 260 || self.taken {
            return None;
        }
        self.taken = true;
        let mut permutation = [0; 256];
        permutation.copy_from_slice(&self.scratch[..256]);
        LEAVES.fetch_add(1, Ordering::Relaxed);
        LEAF_CREATED.fetch_add(1, Ordering::Relaxed);
        Some(Arc::new(Leaf {
            offsets: self.offsets,
            permutation,
        }))
    }
}
impl Drop for Builder {
    fn drop(&mut self) {
        FRAMES.fetch_sub(1, Ordering::Relaxed);
        FRAME_RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
impl Leaf {
    pub(super) fn octave(&self, amplitude: f64) -> Octave {
        let mut p32 = [0; 256];
        for (i, value) in self.permutation.iter().enumerate() {
            let p = *value as i32;
            p32[i] = p | ((p % 12) << 8);
        }
        Octave {
            x: self.offsets[0],
            y: self.offsets[1],
            z: self.offsets[2],
            amplitude,
            present: 1,
            p: self.permutation,
            p32,
        }
    }
}
pub(super) mod ffi {
    use super::*;
    pub(in crate::world::level::levelgen::synth) unsafe fn retain(id: u64) -> Arc<Leaf> {
        let pointer = std::ptr::with_exposed_provenance::<Leaf>(id as usize);
        Arc::increment_strong_count(pointer);
        Arc::from_raw(pointer)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_seed_builder(simplex: i32) -> u64 {
        Box::into_raw(Box::new(Builder::new(simplex != 0))).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_seed_next(id: u64, response: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize)).next(response)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_seed_take(id: u64) -> u64 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .take()
            .map_or(0, |leaf| Arc::into_raw(leaf).expose_provenance() as u64)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_seed_builder_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
                Builder,
            >(id as usize)));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_seed_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Leaf>(
                id as usize,
            )));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_seed_octave(id: u64, out: *mut Octave, amplitude: f64) {
        let leaf = &*std::ptr::with_exposed_provenance::<Leaf>(id as usize);
        out.write(leaf.octave(amplitude));
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_seed_statistics(out: *mut u64) {
        for (i, value) in [
            &FRAMES,
            &FRAME_CREATED,
            &FRAME_RETIRED,
            &LEAVES,
            &LEAF_CREATED,
            &LEAF_RETIRED,
        ]
        .iter()
        .enumerate()
        {
            out.add(i).write(value.load(Ordering::Relaxed));
        }
    }
}

#[cfg(test)]
pub(super) mod tests {
    use super::*;
    use std::sync::Mutex;
    pub(in crate::world::level::levelgen::synth) static SERIAL: Mutex<()> = Mutex::new(());
    fn complete(simplex: bool) -> Arc<Leaf> {
        let mut b = Builder::new(simplex);
        assert_eq!(b.next(0), 1);
        for value in [0.25_f64, 0.5, 0.75] {
            assert_eq!(b.header[0], 1);
            assert_eq!(b.next(value.to_bits()), 1);
        }
        for bound in (1..=256).rev() {
            assert_eq!(b.header[..2], [2, bound]);
            assert_eq!(b.next(0), if bound == 1 { 0 } else { 1 });
        }
        let leaf = b.take().expect("completed leaf");
        assert!(b.take().is_none());
        leaf
    }
    #[test]
    fn real_builder_and_leaf_retire_once_after_transfer() {
        let _lock = SERIAL.lock().unwrap();
        let frames = FRAMES.load(Ordering::Relaxed);
        let leaves = LEAVES.load(Ordering::Relaxed);
        let leaf = complete(false);
        assert_eq!(FRAMES.load(Ordering::Relaxed), frames);
        assert_eq!(LEAVES.load(Ordering::Relaxed), leaves + 1);
        assert_eq!(leaf.offsets, [64., 128., 192.]);
        let octave = leaf.octave(0.5);
        drop(leaf);
        assert_eq!(LEAVES.load(Ordering::Relaxed), leaves);
        assert_eq!(octave.amplitude, 0.5);
        assert_eq!(octave.p[255], 255);
        assert_eq!(octave.p32[255], 255 | ((255 % 12) << 8));
    }
    #[test]
    fn actual_family_capacity_preserves_upper_half_alias() {
        let _lock = SERIAL.lock().unwrap();
        for simplex in [false, true] {
            let mut b = Builder::new(simplex);
            b.next(0);
            for _ in 0..3 {
                b.next(0);
            }
            assert_eq!(b.next(256), if simplex { 1 } else { -1 });
            if simplex {
                assert_eq!(b.scratch[0], 0);
                assert_eq!(b.scratch[256], 0);
            } else {
                assert_eq!(b.header[..2], [256, 256]);
                assert!(b.take().is_none());
            }
        }
    }
    #[test]
    fn last_failed_swap_cannot_publish_a_leaf() {
        let _lock = SERIAL.lock().unwrap();
        let mut b = Builder::new(false);
        b.next(0);
        for _ in 0..3 {
            b.next(0);
        }
        for _ in 0..255 {
            b.next(0);
        }
        assert_eq!(b.next(1), -1);
        assert_eq!(b.header[..2], [256, 256]);
        assert!(b.take().is_none());
    }
    #[test]
    fn unfinished_failure_drops_actual_scratch_without_leaf() {
        let _lock = SERIAL.lock().unwrap();
        let frames = FRAMES.load(Ordering::Relaxed);
        let created = LEAF_CREATED.load(Ordering::Relaxed);
        {
            let mut b = Builder::new(true);
            b.next(0);
            b.next(f64::NAN.to_bits());
            assert!(b.take().is_none());
        }
        assert_eq!(FRAMES.load(Ordering::Relaxed), frames);
        assert_eq!(LEAF_CREATED.load(Ordering::Relaxed), created);
    }
    #[test]
    fn actual_swap_is_retained_in_independently_copied_octave() {
        let _lock = SERIAL.lock().unwrap();
        let mut b = Builder::new(false);
        b.next(0);
        for _ in 0..3 {
            b.next(0);
        }
        b.next(1);
        for _ in 0..255 {
            b.next(0);
        }
        let leaf = b.take().unwrap();
        let octave = leaf.octave(1.0);
        drop(leaf);
        assert_eq!(octave.p[0], 1);
        assert_eq!(octave.p[1], 0);
        assert_eq!(octave.p32[0], 1 | (1 << 8));
    }
}
