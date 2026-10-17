//! Blended producer metadata, actual Perlin child custody and snapshot readers.
use super::perlin_construction::Graph as Perlin;
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
struct Graph {
    children: [Option<Arc<Perlin>>; 3],
    params: [f64; 5],
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
    header: [u64; 10],
    graph: Option<Graph>,
    phase: u8,
    ready: bool,
}
impl Builder {
    fn new(xz: f64, y: f64, xzf: f64, yf: f64, smear: f64) -> Self {
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Self {
            header: [0; 10],
            graph: Some(Graph {
                children: [None, None, None],
                params: [684.412 * xz, 684.412 * y, xzf, yf, smear],
                max: 0.,
            }),
            phase: 0,
            ready: false,
        }
    }
    fn effect(&mut self, phase: u8, command: u64, slot: u64) -> i32 {
        self.phase = phase;
        self.header = [0; 10];
        self.header[0] = command;
        self.header[1] = slot;
        if command == 1 {
            self.header[2] = if slot == 2 {
                -7i32 as u64
            } else {
                -15i32 as u64
            };
        }
        1
    }
    fn bind(&mut self, child: Arc<Perlin>, slot: i32) -> i32 {
        if !(0..3).contains(&slot) || self.phase != slot as u8 + 1 {
            return -1;
        }
        let target = &mut self.graph.as_mut().unwrap().children[slot as usize];
        if target.is_some() {
            return -1;
        }
        *target = Some(child);
        0
    }
    fn next(&mut self, response: u64) -> i32 {
        if self.ready {
            return 0;
        }
        match self.phase {
            0 => self.effect(1, 1, 0),
            1..=3 => {
                let slot = (self.phase - 1) as usize;
                if self.graph.as_ref().unwrap().children[slot].is_none() {
                    return -1;
                }
                if slot < 2 {
                    self.effect(self.phase + 1, 1, (slot + 1) as u64)
                } else {
                    self.effect(4, 2, 0);
                    self.header[2] = self.graph.as_ref().unwrap().params[1].to_bits();
                    1
                }
            }
            4 => {
                let graph = self.graph.as_mut().unwrap();
                graph.max = f64::from_bits(response);
                self.header = [0; 10];
                for i in 0..3 {
                    self.header[i] = graph.children[i].as_ref().unwrap().leaves.len() as u64;
                }
                for i in 0..5 {
                    self.header[3 + i] = graph.params[i].to_bits();
                }
                self.header[8] = graph.max.to_bits();
                self.ready = true;
                0
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
    pub unsafe extern "C" fn mattmc_noise_blended_builder(
        xz: f64,
        y: f64,
        xzf: f64,
        yf: f64,
        smear: f64,
    ) -> u64 {
        Box::into_raw(Box::new(Builder::new(xz, y, xzf, yf, smear))).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_blended_next(id: u64, response: u64) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize)).next(response)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_blended_bind(id: u64, child: u64, slot: i32) -> i32 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .bind(super::super::perlin_construction::ffi::retain(child), slot)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_blended_take(id: u64) -> u64 {
        (&mut *std::ptr::with_exposed_provenance_mut::<Builder>(id as usize))
            .take()
            .map_or(0, |g| Arc::into_raw(g).expose_provenance() as u64)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_blended_builder_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
                Builder,
            >(id as usize)));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_blended_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Graph>(
                id as usize,
            )));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_blended_reader(id: u64) -> u64 {
        let graph = &*std::ptr::with_exposed_provenance::<Graph>(id as usize);
        let children = &graph.children;
        Box::into_raw(Box::new(super::super::perlin_construction::Reader::triple(
            children[0].as_ref().unwrap().clone(),
            children[1].as_ref().unwrap().clone(),
            children[2].as_ref().unwrap().clone(),
            1,
        )))
        .expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_noise_blended_statistics(out: *mut u64) {
        for (i, a) in [&LIVE, &CREATED, &RETIRED].into_iter().enumerate() {
            out.add(i).write(a.load(Ordering::Relaxed));
        }
    }
}
#[cfg(test)]
mod tests {
    use super::super::construction::tests::SERIAL;
    use super::*;
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
                let h = *(std::ptr::with_exposed_provenance::<[u64; 4]>(id as usize));
                match h[0] {
                    1 | 3 => {}
                    2 => response = 1.0f64.to_bits(),
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
    #[test]
    fn actual_three_children_remain_owned_after_individual_producers_retire() {
        let _lock = SERIAL.lock().unwrap();
        let before = LIVE.load(Ordering::Relaxed);
        let mut b = Builder::new(0.25, 0.125, 80., 160., 4.);
        let mut weak = Vec::new();
        for (slot, count) in [16, 16, 8].into_iter().enumerate() {
            assert_eq!(b.next(0), 1);
            assert_eq!(&b.header[..2], &[1, slot as u64]);
            let graph = child(count);
            weak.push(Arc::downgrade(&graph));
            assert_eq!(b.bind(graph, slot as i32), 0);
        }
        assert_eq!(b.next(0), 1);
        assert_eq!(&b.header[..2], &[2, 0]);
        assert_eq!(f64::from_bits(b.header[2]), 684.412 * 0.125);
        assert_eq!(b.next(19.25f64.to_bits()), 0);
        assert_eq!(&b.header[..3], &[16, 16, 8]);
        assert_eq!(f64::from_bits(b.header[3]), 684.412 * 0.25);
        assert_eq!(f64::from_bits(b.header[8]), 19.25);
        let graph = b.take().unwrap();
        drop(b);
        assert!(weak.iter().all(|w| w.upgrade().is_some()));
        assert_eq!(LIVE.load(Ordering::Relaxed), before + 1);
        drop(graph);
        assert!(weak.iter().all(|w| w.upgrade().is_none()));
        assert_eq!(LIVE.load(Ordering::Relaxed), before);
    }
    #[test]
    fn genuine_later_child_failure_retires_already_bound_children() {
        let _lock = SERIAL.lock().unwrap();
        let before = LIVE.load(Ordering::Relaxed);
        let weak;
        {
            let mut b = Builder::new(1., 1., 1., 1., 1.);
            b.next(0);
            let graph = child(16);
            weak = Arc::downgrade(&graph);
            assert_eq!(b.bind(graph, 0), 0);
            b.next(0);
            assert!(weak.upgrade().is_some());
        }
        assert!(weak.upgrade().is_none());
        assert_eq!(LIVE.load(Ordering::Relaxed), before);
    }
    #[test]
    fn transferred_reader_writes_all_three_actual_children_after_parent_retirement() {
        let _lock = SERIAL.lock().unwrap();
        unsafe {
            let mut b = Builder::new(0.25, 0.125, 80., 160., 4.);
            let mut weak = Vec::new();
            for (slot, count) in [16, 16, 8].into_iter().enumerate() {
                b.next(0);
                let child = child(count);
                weak.push(Arc::downgrade(&child));
                b.bind(child, slot as i32);
            }
            b.next(0);
            b.next(2.0f64.to_bits());
            let graph = b.take().unwrap();
            drop(b);
            let raw = Arc::into_raw(graph).expose_provenance() as u64;
            let reader = ffi::mattmc_noise_blended_reader(raw);
            ffi::mattmc_noise_blended_release(raw);
            assert!(weak.iter().all(|w| w.upgrade().is_some()));
            use super::super::{perlin_construction::ffi as p, state::Octave};
            let mut response = 0;
            let mut written = Vec::new();
            loop {
                let status = p::mattmc_noise_perlin_reader_next(reader, response);
                if status == 0 {
                    break;
                }
                assert_eq!(status, 1);
                let h = *(std::ptr::with_exposed_provenance::<[u64; 4]>(reader as usize));
                response = 0;
                match h[0] {
                    2 => response = (h[1] as f64 + 0.5).to_bits(),
                    9 => {
                        written.push(h[1]);
                        let mut out = std::mem::MaybeUninit::<Octave>::uninit();
                        p::mattmc_noise_perlin_reader_write(reader, out.as_mut_ptr());
                        let actual = out.assume_init();
                        let local = if h[1] < 16 {
                            h[1]
                        } else if h[1] < 32 {
                            h[1] - 16
                        } else {
                            h[1] - 32
                        };
                        assert_eq!(actual.amplitude, local as f64 + 0.5);
                        assert_eq!(actual.present, 1);
                    }
                    op => panic!("unexpected triple reader effect {op}"),
                }
            }
            assert_eq!(written, (0u64..40).collect::<Vec<_>>());
            p::mattmc_noise_perlin_reader_release(reader);
            assert!(weak.iter().all(|w| w.upgrade().is_none()));
        }
    }
}
