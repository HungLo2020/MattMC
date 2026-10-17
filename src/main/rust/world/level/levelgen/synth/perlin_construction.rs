//! Actual Perlin graph production and metadata. VM arrays are identity projections;
//! native graph ownership never depends on a VM permutation or borrowed pointer.
use super::{construction::Leaf, state::Octave};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static FACTORY_LIVE: AtomicU64 = AtomicU64::new(0);
static FACTORY_CREATED: AtomicU64 = AtomicU64::new(0);
static FACTORY_RETIRED: AtomicU64 = AtomicU64::new(0);
static READER_LIVE: AtomicU64 = AtomicU64::new(0);
static READER_CREATED: AtomicU64 = AtomicU64::new(0);
static READER_RETIRED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
// Java Math.pow(2.0, integer) preserves subnormals. powi may first
// overflow a positive intermediate before reciprocation, losing those bits.
pub(super) fn power_of_two(exponent: i32) -> f64 {
    match exponent {
        i32::MIN..=-1075 => 0.0,
        -1074..=-1023 => f64::from_bits(1_u64 << (exponent + 1074)),
        -1022..=1023 => f64::from_bits(((exponent + 1023) as u64) << 52),
        1024..=i32::MAX => f64::INFINITY,
    }
}
const FORK: u64 = 1;
const AMPLITUDE: u64 = 2;
const HASH: u64 = 3;
const LEAF: u64 = 4;
const PROJECT: u64 = 5;
const SKIP: u64 = 6;
const COUNT: u64 = 7;
const THROW: u64 = 8;
const WRITE: u64 = 9;
const EXPECTED: u64 = 10;
pub(super) struct Graph {
    pub(super) leaves: Vec<Option<Arc<Leaf>>>,
    pub(super) first: i32,
    pub(super) input: f64,
    pub(super) value: f64,
    pub(super) max: f64,
}
impl Drop for Graph {
    fn drop(&mut self) {
        LIVE.fetch_sub(1, Ordering::Relaxed);
        RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
#[repr(C)]
struct Builder {
    header: [u64; 4],
    graph: Option<Graph>,
    modern: bool,
    phase: u64,
    index: i32,
    j: i32,
    pending: Option<Arc<Leaf>>,
    ready: bool,
    factor: f64,
    sum: f64,
}
impl Builder {
    fn new(first: i32, count: i32, modern: bool) -> Self {
        // The caller has already performed the original typed VM array allocation.
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Self {
            header: [0; 4],
            graph: Some(Graph {
                leaves: vec![None; count as usize],
                first,
                input: 0.,
                value: 0.,
                max: 0.,
            }),
            modern,
            phase: 0,
            index: 0,
            j: first.wrapping_neg(),
            pending: None,
            ready: false,
            factor: 0.,
            sum: 0.,
        }
    }
    fn effect(&mut self, phase: u64, command: u64, arg: i32) -> i32 {
        self.phase = phase;
        self.header = [command, arg as u64, 0, 0];
        1
    }
    fn bind(&mut self, leaf: Arc<Leaf>) -> i32 {
        if self.header[0] != LEAF || self.pending.is_some() {
            return -1;
        }
        self.pending = Some(leaf);
        0
    }
    fn modern_next(&mut self) -> i32 {
        if self.index < self.graph.as_ref().unwrap().leaves.len() as i32 {
            self.effect(2, AMPLITUDE, self.index)
        } else {
            self.metadata()
        }
    }
    fn legacy_next(&mut self) -> i32 {
        if self.index >= 0 {
            if self.index < self.graph.as_ref().unwrap().leaves.len() as i32 {
                self.effect(12, AMPLITUDE, self.index)
            } else {
                self.effect(15, SKIP, 262)
            }
        } else {
            self.effect(16, COUNT, 0)
        }
    }
    fn metadata(&mut self) -> i32 {
        let graph = self.graph.as_mut().unwrap();
        let n = graph.leaves.len() as i32;
        graph.input = power_of_two(self.j.wrapping_neg());
        graph.value = power_of_two(n.wrapping_sub(1)) / (power_of_two(n) - 1.0);
        self.factor = graph.value;
        self.sum = 0.;
        self.index = 0;
        self.edge_next()
    }
    fn edge_next(&mut self) -> i32 {
        let graph = self.graph.as_mut().unwrap();
        while (self.index as usize) < graph.leaves.len() {
            if graph.leaves[self.index as usize].is_some() {
                return self.effect(20, AMPLITUDE, self.index);
            }
            self.factor /= 2.;
            self.index += 1;
        }
        graph.max = self.sum;
        self.header = [
            graph.input.to_bits(),
            graph.value.to_bits(),
            graph.max.to_bits(),
            0,
        ];
        self.ready = true;
        0
    }
    fn next(&mut self, response: u64) -> i32 {
        if self.ready {
            return 0;
        }
        match self.phase {
            0 => {
                if self.modern {
                    self.effect(1, FORK, 0)
                } else {
                    self.effect(10, LEAF, -1)
                }
            }
            1 => {
                self.index = 0;
                self.modern_next()
            }
            2 => {
                if f64::from_bits(response) != 0. {
                    let octave = self.graph.as_ref().unwrap().first.wrapping_add(self.index);
                    self.effect(3, HASH, octave)
                } else {
                    self.index += 1;
                    self.modern_next()
                }
            }
            3 => self.effect(4, LEAF, self.index),
            4 => {
                let Some(leaf) = self.pending.take() else {
                    return -1;
                };
                self.graph.as_mut().unwrap().leaves[self.index as usize] = Some(leaf);
                self.index += 1;
                self.modern_next()
            }
            10 => {
                if self.pending.is_none() {
                    return -1;
                }
                let n = self.graph.as_ref().unwrap().leaves.len() as i32;
                if self.j >= 0 && self.j < n {
                    self.effect(11, AMPLITUDE, self.j)
                } else {
                    self.pending = None;
                    self.index = self.j.wrapping_sub(1);
                    self.legacy_next()
                }
            }
            11 => {
                if f64::from_bits(response) != 0. {
                    self.graph.as_mut().unwrap().leaves[self.j as usize] = self.pending.take();
                    self.effect(14, PROJECT, self.j)
                } else {
                    self.pending = None;
                    self.index = self.j.wrapping_sub(1);
                    self.legacy_next()
                }
            }
            14 => {
                self.index = self.j.wrapping_sub(1);
                self.legacy_next()
            }
            12 => {
                if f64::from_bits(response) != 0. {
                    self.effect(13, LEAF, self.index)
                } else {
                    self.effect(15, SKIP, 262)
                }
            }
            13 => {
                let Some(leaf) = self.pending.take() else {
                    return -1;
                };
                self.graph.as_mut().unwrap().leaves[self.index as usize] = Some(leaf);
                self.index -= 1;
                self.legacy_next()
            }
            15 => {
                self.index -= 1;
                self.legacy_next()
            }
            16 => {
                let graph = self.graph.as_ref().unwrap();
                let actual = graph.leaves.iter().filter(|x| x.is_some()).count() as u64;
                if actual != response {
                    self.effect(99, THROW, 1)
                } else if self.j < (graph.leaves.len() as i32).wrapping_sub(1) {
                    self.effect(99, THROW, 2)
                } else {
                    self.metadata()
                }
            }
            20 => {
                self.sum += (f64::from_bits(response) * 2.0) * self.factor;
                self.factor /= 2.;
                self.index += 1;
                self.edge_next()
            }
            _ => -1,
        }
    }
    fn take(&mut self) -> Option<Arc<Graph>> {
        if !self.ready {
            return None;
        }
        self.graph.take().map(Arc::new)
    }
}
// Native graph frames request only original amplitude observations. Destinations
// are borrowed solely for an individual synchronous write in the FFI module.
#[repr(C)]
pub(super) struct Reader {
    header: [u64; 4],
    graph: Arc<Graph>,
    second: Option<Arc<Graph>>,
    offset: usize,
    index: usize,
    phase: u8,
    mode: i32,
    input: f64,
    factor: f64,
    sum: f64,
    amplitude: f64,
    terminal: bool,
}
impl Reader {
    fn new(graph: Arc<Graph>, mode: i32, input: f64) -> Self {
        READER_LIVE.fetch_add(1, Ordering::Relaxed);
        READER_CREATED.fetch_add(1, Ordering::Relaxed);
        let factor = graph.value;
        let input = if mode == 0 { input + 2.0 } else { input };
        Self {
            header: [0; 4],
            graph,
            second: None,
            offset: 0,
            index: 0,
            phase: 0,
            mode,
            input,
            factor,
            sum: 0.,
            amplitude: 0.,
            terminal: false,
        }
    }
    pub(super) fn pair(first: Arc<Graph>, second: Arc<Graph>, mode: i32) -> Self {
        let mut reader = Self::new(first, mode, 0.);
        reader.second = Some(second);
        reader
    }
    fn advance(&mut self) -> i32 {
        loop {
            while self.index < self.graph.leaves.len() {
                if self.graph.leaves[self.index].is_some() {
                    self.header = [AMPLITUDE, self.index as u64, 0, 0];
                    self.phase = 1;
                    return 1;
                }
                self.index += 1;
                self.factor /= 2.;
            }
            if let Some(second) = self.second.take() {
                self.offset += self.graph.leaves.len();
                self.graph = second;
                self.index = 0;
                self.factor = self.graph.value;
                continue;
            }
            self.header = [
                if self.mode == 2 {
                    1
                } else {
                    self.sum.to_bits()
                },
                0,
                0,
                0,
            ];
            self.terminal = true;
            return 0;
        }
    }
    fn next(&mut self, response: u64) -> i32 {
        if self.terminal {
            return 0;
        }
        if self.phase == 1 {
            let amplitude = f64::from_bits(response);
            if self.mode == 0 {
                self.sum += (amplitude * self.input) * self.factor;
            } else {
                self.amplitude = amplitude;
                self.header = [
                    if self.mode == 2 { EXPECTED } else { WRITE },
                    (self.offset + self.index) as u64,
                    amplitude.to_bits(),
                    0,
                ];
                self.phase = 2;
                return 1;
            }
            self.index += 1;
            self.factor /= 2.;
        } else if self.phase == 2 {
            if self.mode == 2 && self.amplitude.to_bits() != response {
                self.header = [0; 4];
                self.terminal = true;
                return 0;
            }
            self.index += 1;
        }
        self.advance()
    }
}
impl Drop for Reader {
    fn drop(&mut self) {
        READER_LIVE.fetch_sub(1, Ordering::Relaxed);
        READER_RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
// Octave normalization uses genuine stock set/iterator/list effects, with all
// index/span/error decisions here. No borrowed VM collection pointer is stored.
#[repr(C)]
struct Amplitudes {
    header: [u64; 4],
    phase: u8,
    shift: i32,
    first: i32,
    terminal: bool,
}
impl Amplitudes {
    fn new() -> Self {
        FACTORY_LIVE.fetch_add(1, Ordering::Relaxed);
        FACTORY_CREATED.fetch_add(1, Ordering::Relaxed);
        Self {
            header: [0; 4],
            phase: 0,
            shift: 0,
            first: 0,
            terminal: false,
        }
    }
    fn effect(&mut self, phase: u8, command: u64, arg: i32) -> i32 {
        self.phase = phase;
        self.header = [command, arg as u64, 0, 0];
        1
    }
    fn next(&mut self, response: u64) -> i32 {
        if self.terminal {
            return 0;
        }
        match self.phase {
            0 => self.effect(1, 20, 0), // genuine isEmpty
            1 => {
                if response != 0 {
                    self.effect(99, THROW, 3)
                } else {
                    self.effect(2, 21, 0)
                }
            }
            2 => {
                self.first = response as i32;
                self.shift = self.first.wrapping_neg();
                self.effect(3, 22, 0)
            }
            3 => {
                let count = self.shift.wrapping_add(response as i32).wrapping_add(1);
                if count < 1 {
                    self.effect(99, THROW, 4)
                } else {
                    self.effect(4, 23, count)
                }
            }
            4 => self.effect(5, 24, 0),     // genuine iterator
            5 | 8 => self.effect(6, 25, 0), // genuine hasNext
            6 => {
                if response == 0 {
                    self.header = [self.shift.wrapping_neg() as u64, 0, 0, 0];
                    self.terminal = true;
                    0
                } else {
                    self.effect(7, 26, 0)
                }
            }
            7 => {
                let index = (response as i32).wrapping_add(self.shift);
                self.phase = 8;
                self.header = [27, index as u64, 1.0_f64.to_bits(), 0];
                1
            }
            _ => -1,
        }
    }
}

impl Drop for Amplitudes {
    fn drop(&mut self) {
        FACTORY_LIVE.fetch_sub(1, Ordering::Relaxed);
        FACTORY_RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}

pub(super) mod ffi {
    use super::*;
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_amplitudes() -> u64 {
        Box::into_raw(Box::new(Amplitudes::new())).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_amplitudes_next(id: u64, response: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Amplitudes>(id as usize)).next(response)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_amplitudes_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
                Amplitudes,
            >(id as usize)));
        }
    }
    pub(in crate::world::level::levelgen::synth) unsafe fn retain(id: u64) -> Arc<Graph> {
        let p = std::ptr::with_exposed_provenance::<Graph>(id as usize);
        Arc::increment_strong_count(p);
        Arc::from_raw(p)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_builder(
        first: i32,
        count: i32,
        modern: i32,
    ) -> u64 {
        Box::into_raw(Box::new(Builder::new(first, count, modern != 0))).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_next(id: u64, response: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize)).next(response)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_bind(id: u64, leaf: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .bind(super::super::construction::ffi::retain(leaf))
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_take(id: u64) -> u64 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .take()
            .map_or(0, |g| Arc::into_raw(g).expose_provenance() as u64)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_builder_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
                Builder,
            >(id as usize)));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Graph>(
                id as usize,
            )));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_reader(id: u64, mode: i32, input: f64) -> u64 {
        Box::into_raw(Box::new(Reader::new(retain(id), mode, input))).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_reader_next(id: u64, response: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Reader>(id as usize)).next(response)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_reader_write(id: u64, out: *mut Octave) {
        let r = &*std::ptr::with_exposed_provenance::<Reader>(id as usize);
        out.write(
            r.graph.leaves[r.index]
                .as_ref()
                .unwrap()
                .octave(r.amplitude),
        );
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_reader_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(
                std::ptr::with_exposed_provenance_mut::<Reader>(id as usize),
            ));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_nonzero(value: f64) -> i32 {
        (value != 0.) as i32
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_perlin_statistics(out: *mut u64) {
        for (i, a) in [
            &LIVE,
            &CREATED,
            &RETIRED,
            &READER_LIVE,
            &READER_CREATED,
            &READER_RETIRED,
            &FACTORY_LIVE,
            &FACTORY_CREATED,
            &FACTORY_RETIRED,
        ]
        .iter()
        .enumerate()
        {
            out.add(i).write(a.load(Ordering::Relaxed));
        }
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn integer_binary_powers_preserve_java_subnormal_and_extreme_bits() {
        for (exponent, bits) in [
            (i32::MIN, 0),
            (-1075, 0),
            (-1074, 1),
            (-1073, 2),
            (-1024, 0x0004_0000_0000_0000),
            (-1023, 0x0008_0000_0000_0000),
            (-1022, 0x0010_0000_0000_0000),
            (0, 0x3ff0_0000_0000_0000),
            (1023, 0x7fe0_0000_0000_0000),
            (1024, 0x7ff0_0000_0000_0000),
            (i32::MAX, 0x7ff0_0000_0000_0000),
        ] {
            assert_eq!(
                super::power_of_two(exponent).to_bits(),
                bits,
                "binary exponent {exponent}"
            );
        }
    }

    use super::super::construction::tests::SERIAL;
    use super::*;
    fn seed() -> Arc<Leaf> {
        unsafe {
            let id = super::super::construction::ffi::mattmc_noise_seed_builder(0);
            assert_eq!(
                super::super::construction::ffi::mattmc_noise_seed_next(id, 0),
                1
            );
            for _ in 0..3 {
                super::super::construction::ffi::mattmc_noise_seed_next(id, 0);
            }
            for _ in 0..256 {
                super::super::construction::ffi::mattmc_noise_seed_next(id, 0);
            }
            let raw = super::super::construction::ffi::mattmc_noise_seed_take(id);
            assert_ne!(raw, 0);
            super::super::construction::ffi::mattmc_noise_seed_builder_release(id);
            let leaf = super::super::construction::ffi::retain(raw);
            super::super::construction::ffi::mattmc_noise_seed_release(raw);
            leaf
        }
    }
    fn graph() -> Arc<Graph> {
        let mut b = Builder::new(-2, 1, true);
        assert_eq!(b.next(0), 1);
        assert_eq!(b.header[0], FORK);
        b.next(0);
        assert_eq!(b.header[..2], [AMPLITUDE, 0]);
        b.next(1.0_f64.to_bits());
        assert_eq!(b.header[..2], [HASH, (-2_i64) as u64]);
        b.next(0);
        assert_eq!(b.header[..2], [LEAF, 0]);
        assert_eq!(b.bind(seed()), 0);
        b.next(0);
        assert_eq!(b.header[..2], [AMPLITUDE, 0]);
        assert_eq!(b.next(0.75_f64.to_bits()), 0);
        assert_eq!(
            b.header[..3],
            [0.25_f64.to_bits(), 1.0_f64.to_bits(), 1.5_f64.to_bits()]
        );
        b.take().unwrap()
    }
    #[test]
    fn reader_owns_actual_graph_and_leaf_after_root_release() {
        let _lock = SERIAL.lock().unwrap();
        let g = graph();
        let weak = Arc::downgrade(&g);
        let leaf = Arc::downgrade(g.leaves[0].as_ref().unwrap());
        let mut reader = Reader::new(g.clone(), 0, 1.);
        drop(g);
        assert!(weak.upgrade().is_some());
        assert!(leaf.upgrade().is_some());
        assert_eq!(reader.next(0), 1);
        assert_eq!(reader.header[..2], [AMPLITUDE, 0]);
        assert_eq!(reader.next(0.5_f64.to_bits()), 0);
        assert_eq!(f64::from_bits(reader.header[0]), 1.5);
        drop(reader);
        assert!(weak.upgrade().is_none());
        assert!(leaf.upgrade().is_none());
    }
    #[test]
    fn abandoned_constructor_releases_already_bound_child() {
        let _lock = SERIAL.lock().unwrap();
        let leaf = seed();
        let weak = Arc::downgrade(&leaf);
        {
            let mut b = Builder::new(0, 1, true);
            b.next(0);
            b.next(0);
            b.next(1.0_f64.to_bits());
            b.next(0);
            b.bind(leaf);
            b.next(0);
            assert!(weak.upgrade().is_some());
            assert!(!b.ready);
        }
        assert!(weak.upgrade().is_none());
    }
    #[test]
    fn mismatch_validation_precedes_positive_octave_validation() {
        let _lock = SERIAL.lock().unwrap();
        for (expected, count) in [(1, 2_u64), (2, 1)] {
            let mut b = Builder::new(0, 2, false);
            b.next(0);
            b.bind(seed());
            b.next(0);
            b.next(1.0_f64.to_bits());
            b.next(0);
            assert_eq!(b.header[0], COUNT);
            b.next(count);
            assert_eq!(b.header[..2], [THROW, expected]);
            assert!(b.take().is_none());
        }
    }
    #[test]
    fn write_and_snapshot_comparison_consume_current_observations_in_order() {
        let _lock = SERIAL.lock().unwrap();
        let g = graph();
        let mut writer = Reader::new(g.clone(), 1, 0.);
        writer.next(0);
        writer.next(0.375_f64.to_bits());
        assert_eq!(writer.header[..3], [WRITE, 0, 0.375_f64.to_bits()]);
        let mut destination = std::mem::MaybeUninit::<Octave>::uninit();
        let id = Box::into_raw(Box::new(writer)).expose_provenance() as u64;
        unsafe {
            ffi::mattmc_noise_perlin_reader_write(id, destination.as_mut_ptr());
            assert_eq!(destination.assume_init().amplitude, 0.375);
            ffi::mattmc_noise_perlin_reader_release(id);
        }
        let mut same = Reader::new(g.clone(), 2, 0.);
        same.next(0);
        same.next((-0.0_f64).to_bits());
        assert_eq!(same.header[0], EXPECTED);
        assert_eq!(same.next((-0.0_f64).to_bits()), 0);
        assert_eq!(same.header[0], 1);
        let mut changed = Reader::new(g, 2, 0.);
        changed.next(0);
        changed.next((-0.0_f64).to_bits());
        assert_eq!(changed.next(0.0_f64.to_bits()), 0);
        assert_eq!(changed.header[0], 0);
    }
    #[test]
    fn factory_empty_and_overflow_reject_before_array_allocation() {
        let _lock = SERIAL.lock().unwrap();
        let mut empty = Amplitudes::new();
        empty.next(0);
        assert_eq!(empty.header[0], 20);
        empty.next(1);
        assert_eq!(empty.header[..2], [THROW, 3]);
        let mut overflow = Amplitudes::new();
        overflow.next(0);
        overflow.next(0);
        assert_eq!(overflow.header[0], 21);
        overflow.next(i32::MIN as u64);
        assert_eq!(overflow.header[0], 22);
        overflow.next(i32::MAX as u64);
        assert_eq!(overflow.header[..2], [THROW, 4]);
    }
    #[test]
    fn factory_selection_retains_original_wrapping_indices() {
        let _lock = SERIAL.lock().unwrap();
        let mut f = Amplitudes::new();
        f.next(0);
        f.next(0);
        f.next((-3_i64) as u64);
        f.next(0);
        assert_eq!(f.header[..2], [23, 4]);
        f.next(0);
        assert_eq!(f.header[0], 24);
        f.next(0);
        assert_eq!(f.header[0], 25);
        f.next(1);
        assert_eq!(f.header[0], 26);
        f.next((-3_i64) as u64);
        assert_eq!(f.header[..3], [27, 0, 1.0_f64.to_bits()]);
        f.next(0);
        f.next(1);
        f.next(0);
        assert_eq!(f.header[..3], [27, 3, 1.0_f64.to_bits()]);
        f.next(0);
        assert_eq!(f.next(0), 0);
        assert_eq!(f.header[0], (-3_i64) as u64);
    }
}
