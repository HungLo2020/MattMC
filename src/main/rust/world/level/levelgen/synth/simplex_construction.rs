//! Native Perlin-Simplex octave policy, leaf custody and immutable snapshots.
use super::construction::Leaf;
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
struct Graph {
    leaves: Vec<Option<Arc<Leaf>>>,
    input: f64,
    value: f64,
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
    initial: Option<Arc<Leaf>>,
    pending: Option<Arc<Leaf>>,
    phase: u8,
    shift: i32,
    last: i32,
    count: i32,
    index: i32,
    ready: bool,
    terminal: bool,
}
// Effects: empty1,first2,last3,leaf4,contains5,consume6,derivedSource7,throw8.
impl Builder {
    fn new() -> Self {
        Self {
            header: [0; 4],
            graph: None,
            initial: None,
            pending: None,
            phase: 0,
            shift: 0,
            last: 0,
            count: 0,
            index: 0,
            ready: false,
            terminal: false,
        }
    }
    fn effect(&mut self, phase: u8, command: u64, arg: i32, source: u64) -> i32 {
        self.phase = phase;
        self.header = [command, arg as u64, source, 0];
        1
    }
    fn bind(&mut self, leaf: Arc<Leaf>) -> i32 {
        if self.header[0] != 4 || self.pending.is_some() {
            return -1;
        }
        self.pending = Some(leaf);
        0
    }
    fn lower(&mut self) -> i32 {
        if self.index < self.count {
            if self.index >= 0 {
                self.effect(9, 5, self.last.wrapping_sub(self.index), 0)
            } else {
                self.effect(11, 6, 262, 0)
            }
        } else if self.last > 0 {
            // The original positive-octave path forces the initial child's
            // lazy validated snapshot here. Reuse that same native validator
            // before deriving its seed, without delaying an invalid-leaf error.
            #[repr(C)]
            struct InitialSnapshot {
                state: super::state::State,
                octave: super::state::Octave,
            }
            let snapshot = InitialSnapshot {
                state: super::state::State {
                    kind: 5,
                    n1: 1,
                    n2: 0,
                    n3: 0,
                    params: [0.; 8],
                },
                octave: self.initial.as_ref().unwrap().octave(1.),
            };
            let status = unsafe {
                super::validation::noise_validate(
                    &snapshot.state,
                    std::mem::size_of::<InitialSnapshot>() as u64,
                )
            };
            if status != 0 {
                self.terminal = true;
                return self.effect(99, 12, status, 0);
            }
            let initial = &snapshot.octave;
            let seed = (initial.simplex3(initial.x, initial.y, initial.z)
                * f64::from(9.223372e18f32)) as i64;
            self.effect(12, 7, 0, 0);
            self.header[1] = seed as u64;
            1
        } else {
            self.finish()
        }
    }
    fn upper(&mut self) -> i32 {
        if self.index >= 0 {
            if self.index < self.count {
                self.effect(13, 5, self.last.wrapping_sub(self.index), 1)
            } else {
                self.effect(15, 6, 262, 1)
            }
        } else {
            self.finish()
        }
    }
    fn finish(&mut self) -> i32 {
        let graph = self.graph.as_mut().unwrap();
        graph.input = super::perlin_construction::power_of_two(self.last);
        graph.value = 1.0 / (super::perlin_construction::power_of_two(self.count) - 1.0);
        self.header = [
            self.count as u64,
            graph.input.to_bits(),
            graph.value.to_bits(),
            0,
        ];
        self.initial = None;
        self.ready = true;
        0
    }
    fn next(&mut self, response: u64) -> i32 {
        if self.ready || self.terminal {
            return 0;
        }
        match self.phase {
            0 => self.effect(1, 1, 0, 0),
            1 => {
                if response != 0 {
                    self.terminal = true;
                    self.effect(99, 8, 1, 0)
                } else {
                    self.effect(2, 2, 0, 0)
                }
            }
            2 => {
                self.shift = (response as i32).wrapping_neg();
                self.effect(3, 3, 0, 0)
            }
            3 => {
                self.last = response as i32;
                self.count = self.shift.wrapping_add(self.last).wrapping_add(1);
                if self.count < 1 {
                    self.terminal = true;
                    self.effect(99, 8, 2, 0)
                } else {
                    self.effect(4, 4, 0, 0)
                }
            }
            4 => {
                let Some(leaf) = self.pending.take() else {
                    return -1;
                };
                self.initial = Some(leaf);
                self.effect(16, 9, self.count, 0)
            }
            16 => {
                LIVE.fetch_add(1, Ordering::Relaxed);
                CREATED.fetch_add(1, Ordering::Relaxed);
                self.graph = Some(Graph {
                    leaves: vec![None; self.count as usize],
                    input: 0.,
                    value: 0.,
                });
                if self.last >= 0 && self.last < self.count {
                    self.effect(5, 5, 0, 0)
                } else {
                    self.index = self.last.wrapping_add(1);
                    self.lower()
                }
            }
            5 => {
                if response != 0 {
                    self.graph.as_mut().unwrap().leaves[self.last as usize] = self.initial.clone();
                    self.effect(17, 10, self.last, 0)
                } else {
                    self.index = self.last.wrapping_add(1);
                    self.lower()
                }
            }
            17 => {
                self.index = self.last.wrapping_add(1);
                self.lower()
            }
            9 => {
                if response != 0 {
                    self.effect(10, 4, 0, 0)
                } else {
                    self.effect(11, 6, 262, 0)
                }
            }
            10 => {
                let Some(leaf) = self.pending.take() else {
                    return -1;
                };
                self.graph.as_mut().unwrap().leaves[self.index as usize] = Some(leaf);
                self.effect(18, 10, self.index, 0)
            }
            18 => {
                self.index = self.index.wrapping_add(1);
                self.lower()
            }
            11 => {
                self.index = self.index.wrapping_add(1);
                self.lower()
            }
            12 => {
                self.index = self.last.wrapping_sub(1);
                self.upper()
            }
            13 => {
                if response != 0 {
                    self.effect(14, 4, 0, 1)
                } else {
                    self.effect(15, 6, 262, 1)
                }
            }
            14 => {
                let Some(leaf) = self.pending.take() else {
                    return -1;
                };
                self.graph.as_mut().unwrap().leaves[self.index as usize] = Some(leaf);
                self.effect(19, 10, self.index, 1)
            }
            19 => {
                self.index = self.index.wrapping_sub(1);
                self.upper()
            }
            15 => {
                self.index = self.index.wrapping_sub(1);
                self.upper()
            }
            _ => -1,
        }
    }
    fn take(&mut self) -> Option<Arc<Graph>> {
        if self.ready {
            self.graph.take().map(Arc::new)
        } else {
            None
        }
    }
}
mod ffi {
    use super::*;
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_builder() -> u64 {
        Box::into_raw(Box::new(Builder::new())).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_next(id: u64, response: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize)).next(response)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_bind(id: u64, leaf: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .bind(super::super::construction::ffi::retain(leaf))
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_take(id: u64) -> u64 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .take()
            .map_or(0, |g| Arc::into_raw(g).expose_provenance() as u64)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_builder_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
                Builder,
            >(id as usize)));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Graph>(
                id as usize,
            )));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_write(
        id: u64,
        out: *mut super::super::state::Octave,
    ) {
        let graph = &*std::ptr::with_exposed_provenance::<Graph>(id as usize);
        for (i, leaf) in graph.leaves.iter().enumerate() {
            let octave = leaf
                .as_ref()
                .map_or_else(|| std::mem::zeroed(), |leaf| leaf.octave(1.));
            out.add(i).write(octave);
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_simplex_octaves_statistics(out: *mut u64) {
        for (i, a) in [&LIVE, &CREATED, &RETIRED].into_iter().enumerate() {
            out.add(i).write(a.load(Ordering::Relaxed));
        }
    }
}
#[cfg(test)]
mod tests {
    use super::super::construction::tests::SERIAL;
    use super::*;
    fn leaf() -> Arc<Leaf> {
        unsafe {
            use super::super::construction::ffi as s;
            let b = s::mattmc_noise_seed_builder(1);
            s::mattmc_noise_seed_next(b, 0);
            for _ in 0..3 {
                s::mattmc_noise_seed_next(b, 0);
            }
            for _ in 0..256 {
                s::mattmc_noise_seed_next(b, 0);
            }
            let raw = s::mattmc_noise_seed_take(b);
            s::mattmc_noise_seed_builder_release(b);
            let leaf = s::retain(raw);
            s::mattmc_noise_seed_release(raw);
            leaf
        }
    }
    fn complete(octaves: &[i32]) -> (Arc<Graph>, Vec<[u64; 4]>) {
        let mut b = Builder::new();
        let mut response = 0;
        let mut trace = Vec::new();
        loop {
            let status = b.next(response);
            if status == 0 {
                break;
            }
            assert_eq!(status, 1);
            let h = b.header;
            trace.push(h);
            response = 0;
            match h[0] {
                1 => response = octaves.is_empty() as u64,
                2 => response = *octaves.first().unwrap() as u64,
                3 => response = *octaves.last().unwrap() as u64,
                4 => assert_eq!(b.bind(leaf()), 0),
                5 => response = octaves.contains(&(h[1] as i32)) as u64,
                6 | 7 | 9 | 10 => {}
                op => panic!("unexpected Simplex producer effect {op}"),
            }
        }
        (b.take().unwrap(), trace)
    }
    #[test]
    fn negative_holes_retain_actual_leaf_graphs_and_original_membership_order() {
        let _lock = SERIAL.lock().unwrap();
        let (g, t) = complete(&[-3, -1, 0]);
        assert_eq!(
            g.leaves.iter().map(Option::is_some).collect::<Vec<_>>(),
            [true, true, false, true]
        );
        assert_eq!(g.input, 1.);
        assert_eq!(g.value, 1. / 15.);
        assert_eq!(
            t.iter()
                .filter(|h| h[0] == 5)
                .map(|h| h[1] as i32)
                .collect::<Vec<_>>(),
            [0, -1, -2, -3]
        );
        assert_eq!(t.iter().filter(|h| h[0] == 4).count(), 3);
        assert_eq!(t.iter().filter(|h| h[0] == 6).count(), 1);
        assert!(!t.iter().any(|h| h[0] == 7));
    }
    #[test]
    fn positive_levels_switch_to_derived_source_at_original_prefix() {
        let _lock = SERIAL.lock().unwrap();
        let (g, t) = complete(&[-2, 0, 2]);
        assert_eq!(
            g.leaves.iter().map(Option::is_some).collect::<Vec<_>>(),
            [true, false, true, false, true]
        );
        assert_eq!(g.input, 4.);
        assert_eq!(g.value, 1. / 31.);
        let split = t.iter().position(|h| h[0] == 7).unwrap();
        assert_eq!(t[..split].iter().filter(|h| h[0] == 4).count(), 2);
        assert!(t[..split]
            .iter()
            .filter(|h| h[0] == 4 || h[0] == 6)
            .all(|h| h[2] == 0));
        assert!(t[split + 1..]
            .iter()
            .filter(|h| h[0] == 4 || h[0] == 6)
            .all(|h| h[2] == 1));
        assert_eq!(t[split][1], 0);
    }
    #[test]
    fn failed_later_leaf_drops_the_actual_initial_and_stored_data() {
        let _lock = SERIAL.lock().unwrap();
        let before = LIVE.load(Ordering::Relaxed);
        let weak;
        {
            let mut b = Builder::new();
            b.next(0);
            b.next(0);
            b.next((-3i32) as u64);
            b.next(0);
            let data = leaf();
            weak = Arc::downgrade(&data);
            b.bind(data);
            b.next(0);
            assert_eq!(b.header[0], 9);
            b.next(0);
            b.next(1);
            assert!(weak.upgrade().is_some());
        }
        assert!(weak.upgrade().is_none());
        assert_eq!(LIVE.load(Ordering::Relaxed), before);
    }
    #[test]
    fn empty_and_overflow_reject_before_actual_leaf_effect() {
        let _lock = SERIAL.lock().unwrap();
        let mut b = Builder::new();
        b.next(0);
        b.next(1);
        assert_eq!(b.header[..2], [8, 1]);
        assert!(b.graph.is_none());
        let mut b = Builder::new();
        b.next(0);
        b.next(0);
        b.next(i32::MIN as u64);
        b.next(i32::MAX as u64);
        assert_eq!(b.header[..2], [8, 2]);
        assert!(b.graph.is_none());
    }
    #[test]
    fn completed_graph_writes_real_native_leaf_permutations_and_snapshot_outlives_owner() {
        let _lock = SERIAL.lock().unwrap();
        let before = LIVE.load(Ordering::Relaxed);
        let (graph, _) = complete(&[-3, -1, 0]);
        let weak = graph
            .leaves
            .iter()
            .filter_map(|leaf| leaf.as_ref().map(Arc::downgrade))
            .collect::<Vec<_>>();
        let raw = Arc::into_raw(graph).expose_provenance() as u64;
        let mut out = Vec::<std::mem::MaybeUninit<super::super::state::Octave>>::with_capacity(4);
        out.resize_with(4, std::mem::MaybeUninit::uninit);
        unsafe {
            ffi::mattmc_noise_simplex_octaves_write(raw, out.as_mut_ptr().cast());
            ffi::mattmc_noise_simplex_octaves_release(raw);
            assert!(weak.iter().all(|leaf| leaf.upgrade().is_none()));
            assert_eq!(LIVE.load(Ordering::Relaxed), before);
            for (index, data) in out.into_iter().enumerate() {
                let octave = data.assume_init();
                if index == 2 {
                    assert_eq!(octave.present, 0);
                    assert_eq!(octave.amplitude, 0.);
                } else {
                    assert_eq!(octave.present, 1);
                    assert_eq!(octave.amplitude, 1.);
                    assert_eq!((octave.p[0], octave.p[31], octave.p[255]), (0, 31, 255));
                }
            }
        }
    }
    #[test]
    fn constructor_frequency_reuses_exact_subnormal_policy() {
        let _lock = SERIAL.lock().unwrap();
        let (graph, _) = complete(&[-1024]);
        assert_eq!(graph.input.to_bits(), 1125899906842624);
        assert_eq!(graph.value, 1.);
    }
    #[test]
    fn vm_array_failure_precedes_native_vector_allocation_and_releases_initial_leaf() {
        let _lock = SERIAL.lock().unwrap();
        let before = LIVE.load(Ordering::Relaxed);
        let weak;
        {
            let mut b = Builder::new();
            b.next(0);
            b.next(0);
            b.next(0);
            b.next((i32::MAX - 1) as u64);
            assert_eq!(b.header[0], 4);
            let initial = leaf();
            weak = Arc::downgrade(&initial);
            b.bind(initial);
            b.next(0);
            assert_eq!(b.header[..2], [9, i32::MAX as u64]);
            assert!(b.graph.is_none());
            assert!(weak.upgrade().is_some());
        }
        assert!(weak.upgrade().is_none());
        assert_eq!(LIVE.load(Ordering::Relaxed), before);
    }
    #[test]
    fn positive_octaves_keep_the_existing_lazy_initial_validation_boundary() {
        let _lock = SERIAL.lock().unwrap();
        unsafe {
            use super::super::construction::ffi as f;
            let raw = f::mattmc_noise_seed_builder(1);
            f::mattmc_noise_seed_next(raw, 0);
            for _ in 0..3 {
                f::mattmc_noise_seed_next(raw, f64::NAN.to_bits());
            }
            for _ in 0..256 {
                f::mattmc_noise_seed_next(raw, 0);
            }
            let id = f::mattmc_noise_seed_take(raw);
            f::mattmc_noise_seed_builder_release(raw);
            let data = f::retain(id);
            f::mattmc_noise_seed_release(id);
            let weak = Arc::downgrade(&data);
            {
                let mut b = Builder::new();
                b.next(0);
                b.next(0);
                b.next(0);
                b.next(1);
                b.bind(data);
                b.next(0);
                b.next(0);
                b.next(1);
                assert_eq!(b.header[0], 10);
                b.next(0);
                assert_eq!(b.header[..2], [12, (-5i32) as u64]);
                assert!(b.take().is_none());
            }
            assert!(weak.upgrade().is_none());
        }
    }
}
