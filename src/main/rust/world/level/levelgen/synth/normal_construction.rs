//! Normal producer metadata and actual ownership of both Perlin children.
use super::perlin_construction::{Graph as Perlin, Reader};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
struct Graph {
    first: Option<Arc<Perlin>>,
    second: Option<Arc<Perlin>>,
    factor: f64,
    max: f64,
}
impl Drop for Graph {
    fn drop(&mut self) {
        LIVE.fetch_sub(1, Ordering::Relaxed);
        RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
#[repr(C)]
struct Builder {
    header: [u64; 8],
    graph: Option<Graph>,
    modern: bool,
    phase: u8,
    index: i32,
    minimum: i32,
    maximum: i32,
    ready: bool,
}
impl Builder {
    fn new(modern: bool) -> Self {
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Self {
            header: [0; 8],
            graph: Some(Graph {
                first: None,
                second: None,
                factor: 0.,
                max: 0.,
            }),
            modern,
            phase: 0,
            index: 0,
            minimum: i32::MAX,
            maximum: i32::MIN,
            ready: false,
        }
    }
    fn effect(&mut self, phase: u8, command: u64, arg: u64) -> i32 {
        self.phase = phase;
        self.header = [command, arg, self.modern as u64, 0, 0, 0, 0, 0];
        1
    }
    fn bind(&mut self, graph: Arc<Perlin>, slot: i32) -> i32 {
        let parent = self.graph.as_mut().unwrap();
        match (slot, self.phase) {
            (0, 1) if parent.first.is_none() => {
                parent.first = Some(graph);
                0
            }
            (1, 2) if parent.second.is_none() => {
                parent.second = Some(graph);
                0
            }
            _ => -1,
        }
    }
    fn next(&mut self, response: u64) -> i32 {
        if self.ready {
            return 0;
        }
        match self.phase {
            0 => self.effect(1, 1, 0),
            1 => {
                if self.graph.as_ref().unwrap().first.is_some() {
                    self.effect(2, 1, 1)
                } else {
                    -1
                }
            }
            2 => {
                if self.graph.as_ref().unwrap().second.is_some() {
                    self.effect(3, 2, 0)
                } else {
                    -1
                }
            }
            3 => self.effect(4, 3, 0),
            4 => {
                if response != 0 {
                    self.effect(5, 4, 0)
                } else {
                    self.finish()
                }
            }
            5 => {
                self.index = response as i32;
                self.effect(6, 5, 0)
            }
            6 => {
                if f64::from_bits(response) != 0. {
                    self.minimum = self.minimum.min(self.index);
                    self.maximum = self.maximum.max(self.index);
                }
                self.effect(4, 3, 0)
            }
            _ => -1,
        }
    }
    fn finish(&mut self) -> i32 {
        let graph = self.graph.as_mut().unwrap();
        let first = graph.first.as_ref().unwrap();
        let second = graph.second.as_ref().unwrap();
        let span = self.maximum.wrapping_sub(self.minimum);
        let deviation = 0.1 * (1.0 + 1.0 / (span.wrapping_add(1) as f64));
        graph.factor = 0.16666666666666666 / deviation;
        graph.max = (first.max + second.max) * graph.factor;
        self.header = [
            first.leaves.len() as u64,
            second.leaves.len() as u64,
            first.input.to_bits(),
            first.value.to_bits(),
            second.input.to_bits(),
            second.value.to_bits(),
            graph.factor.to_bits(),
            graph.max.to_bits(),
        ];
        self.ready = true;
        0
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
    pub unsafe extern "C" fn mattmc_noise_normal_builder(modern: i32) -> u64 {
        Box::into_raw(Box::new(Builder::new(modern != 0))).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_normal_next(id: u64, response: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize)).next(response)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_normal_bind(id: u64, child: u64, slot: i32) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .bind(super::super::perlin_construction::ffi::retain(child), slot)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_normal_take(id: u64) -> u64 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .take()
            .map_or(0, |graph| Arc::into_raw(graph).expose_provenance() as u64)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_normal_builder_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
                Builder,
            >(id as usize)));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_normal_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Graph>(
                id as usize,
            )));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_normal_reader(id: u64, mode: i32) -> u64 {
        let graph = &*std::ptr::with_exposed_provenance::<Graph>(id as usize);
        Box::into_raw(Box::new(Reader::pair(
            graph.first.as_ref().unwrap().clone(),
            graph.second.as_ref().unwrap().clone(),
            mode,
        )))
        .expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_normal_statistics(out: *mut u64) {
        for (i, a) in [&LIVE, &CREATED, &RETIRED].iter().enumerate() {
            out.add(i).write(a.load(Ordering::Relaxed));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::super::construction::tests::SERIAL;
    use super::*;
    unsafe fn header(id: u64) -> [u64; 4] {
        *(std::ptr::with_exposed_provenance::<[u64; 4]>(id as usize))
    }
    fn child(count: i32) -> Arc<Perlin> {
        unsafe {
            use super::super::{construction::ffi as s, perlin_construction::ffi as p};
            let id = p::mattmc_noise_perlin_builder(-1, count, 1);
            let mut response = 0;
            loop {
                let status = p::mattmc_noise_perlin_next(id, response);
                if status == 0 {
                    break;
                }
                assert_eq!(status, 1);
                response = 0;
                match header(id)[0] {
                    1 | 3 => {}
                    2 => response = 1.0_f64.to_bits(),
                    4 => {
                        let seed = s::mattmc_noise_seed_builder(0);
                        s::mattmc_noise_seed_next(seed, 0);
                        for _ in 0..3 {
                            s::mattmc_noise_seed_next(seed, 0);
                        }
                        for _ in 0..256 {
                            s::mattmc_noise_seed_next(seed, 0);
                        }
                        let leaf = s::mattmc_noise_seed_take(seed);
                        assert_ne!(leaf, 0);
                        s::mattmc_noise_seed_builder_release(seed);
                        assert_eq!(p::mattmc_noise_perlin_bind(id, leaf), 0);
                        s::mattmc_noise_seed_release(leaf);
                    }
                    op => panic!("unexpected actual child effect {op}"),
                }
            }
            let raw = p::mattmc_noise_perlin_take(id);
            p::mattmc_noise_perlin_builder_release(id);
            let graph = p::retain(raw);
            p::mattmc_noise_perlin_release(raw);
            graph
        }
    }
    fn complete() -> Arc<Graph> {
        let mut b = Builder::new(true);
        b.next(0);
        b.bind(child(2), 0);
        b.next(0);
        b.bind(child(3), 1);
        b.next(0);
        assert_eq!(b.header[0], 2);
        b.next(0);
        for index in 0..3 {
            b.next(1);
            assert_eq!(b.header[0], 4);
            b.next(index);
            assert_eq!(b.header[0], 5);
            b.next(1.0_f64.to_bits());
        }
        assert_eq!(b.next(0), 0);
        assert_eq!(b.header[0], 2);
        assert_eq!(b.header[1], 3);
        assert_eq!(f64::from_bits(b.header[6]), 1.25);
        assert_eq!(f64::from_bits(b.header[7]), 5.0);
        b.take().unwrap()
    }
    #[test]
    fn paired_reader_observes_tail_with_correct_snapshot_offset() {
        let _lock = SERIAL.lock().unwrap();
        unsafe {
            let graph = complete();
            let root = Arc::into_raw(graph).expose_provenance() as u64;
            let reader = ffi::mattmc_noise_normal_reader(root, 2);
            ffi::mattmc_noise_normal_release(root);
            use super::super::perlin_construction::ffi as p;
            let mut response = 0;
            let mut expected = Vec::new();
            loop {
                let status = p::mattmc_noise_perlin_reader_next(reader, response);
                if status == 0 {
                    break;
                }
                assert_eq!(status, 1);
                let h = header(reader);
                match h[0] {
                    2 => {
                        response = if expected.len() == 4 {
                            2.0_f64.to_bits()
                        } else {
                            1.0_f64.to_bits()
                        };
                    }
                    10 => {
                        expected.push(h[1]);
                        response = 1.0_f64.to_bits();
                    }
                    op => panic!("unexpected reader effect {op}"),
                }
            }
            assert_eq!(expected, vec![0, 1, 2, 3, 4]);
            assert_eq!(header(reader)[0], 0, "second child tail was not compared");
            p::mattmc_noise_perlin_reader_release(reader);
        }
    }
    #[test]
    fn reader_keeps_both_actual_children_after_parent_metadata_retires() {
        let _lock = SERIAL.lock().unwrap();
        unsafe {
            let graph = complete();
            let first = Arc::downgrade(graph.first.as_ref().unwrap());
            let second = Arc::downgrade(graph.second.as_ref().unwrap());
            let root = Arc::into_raw(graph).expose_provenance() as u64;
            let reader = ffi::mattmc_noise_normal_reader(root, 2);
            ffi::mattmc_noise_normal_release(root);
            assert!(first.upgrade().is_some());
            assert!(second.upgrade().is_some());
            super::super::perlin_construction::ffi::mattmc_noise_perlin_reader_release(reader);
            assert!(first.upgrade().is_none());
            assert!(second.upgrade().is_none());
        }
    }
    #[test]
    fn iterator_failure_drops_both_bound_actual_graphs() {
        let _lock = SERIAL.lock().unwrap();
        let first = child(2);
        let second = child(3);
        let a = Arc::downgrade(&first);
        let b = Arc::downgrade(&second);
        {
            let mut parent = Builder::new(true);
            parent.next(0);
            parent.bind(first, 0);
            parent.next(0);
            parent.bind(second, 1);
            parent.next(0);
            assert_eq!(parent.header[0], 2);
            assert!(a.upgrade().is_some());
            assert!(b.upgrade().is_some());
        }
        assert!(a.upgrade().is_none());
        assert!(b.upgrade().is_none());
    }
}
