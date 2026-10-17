//! Live sky-source policy for contextual readers and layers. Actual VM objects
//! stay in the caller; Rust owns the section/cell cursor and the real work queue.
//! No borrow or queue guard survives an emitted VM effect.
use super::propagation::{self, seed, work::Queues};
use std::{
    ffi::c_void,
    panic::{catch_unwind, AssertUnwindSafe},
    sync::Arc,
};

#[derive(Clone, Copy)]
enum Stage {
    Section,
    Layer,
    Cell,
    Center,
    North,
    South,
    West,
    East,
    Write,
    AfterWrite,
    NextY,
    NextCell,
    SectionDone,
    NextSection,
    Error,
    Done,
}
struct Frame {
    queues: Arc<Queues>,
    chunk_x: i32,
    chunk_z: i32,
    bottom: i32,
    section: i32,
    p: i32,
    q: i32,
    r: i32,
    s: i32,
    t: i32,
    north: i32,
    south: i32,
    west: i32,
    east: i32,
    neighbors: i32,
    y: i32,
    below: bool,
    stage: Stage,
    action: i32,
    reply: [i64; 4],
}
impl Frame {
    fn effect(&mut self, action: i32, values: [i64; 4]) -> i32 {
        self.reply = values;
        action
    }
    fn source(&mut self, which: i32, x: i32, z: i32) -> i32 {
        self.effect(2, [i64::from(which), i64::from(x), i64::from(z), 0])
    }
    fn entry(&self) -> (i64, i64) {
        (
            propagation::block_pos(
                self.chunk_x.wrapping_shl(4).wrapping_add(self.s),
                self.y,
                self.chunk_z.wrapping_shl(4).wrapping_add(self.r),
            ),
            seed::sky_source(
                self.y == self.t,
                self.y < self.north,
                self.y < self.south,
                self.y < self.west,
                self.y < self.east,
            ) as i64,
        )
    }
    fn drive(&mut self) -> i32 {
        loop {
            match self.stage {
                Stage::Section => {
                    self.stage = if self.section >= self.bottom {
                        Stage::Layer
                    } else {
                        Stage::Done
                    };
                }
                Stage::Layer => {
                    return self.effect(
                        1,
                        [
                            propagation::section_pos(self.chunk_x, self.section, self.chunk_z),
                            0,
                            0,
                            0,
                        ],
                    )
                }
                Stage::Cell => {
                    self.stage = if self.r < 16 {
                        Stage::Center
                    } else {
                        Stage::SectionDone
                    };
                }
                Stage::Center => return self.source(0, self.s, self.r),
                Stage::North => {
                    return if self.r == 0 {
                        self.source(1, self.s, 15)
                    } else {
                        self.source(0, self.s, self.r - 1)
                    }
                }
                Stage::South => {
                    return if self.r == 15 {
                        self.source(2, self.s, 0)
                    } else {
                        self.source(0, self.s, self.r + 1)
                    }
                }
                Stage::West => {
                    return if self.s == 0 {
                        self.source(3, 15, self.r)
                    } else {
                        self.source(0, self.s - 1, self.r)
                    }
                }
                Stage::East => {
                    return if self.s == 15 {
                        self.source(4, 0, self.r)
                    } else {
                        self.source(0, self.s + 1, self.r)
                    }
                }
                Stage::Write => {
                    if self.y < self.p.max(self.t) {
                        self.stage = Stage::NextCell;
                        if self.t < self.p {
                            self.below = true;
                        }
                    } else {
                        return self.effect(
                            3,
                            [
                                i64::from(self.s),
                                i64::from(self.y & 15),
                                i64::from(self.r),
                                15,
                            ],
                        );
                    }
                }
                Stage::AfterWrite => {
                    if self.y == self.t || self.y < self.neighbors {
                        let (position, entry) = self.entry();
                        let status = self.queues.enqueue(1, position, entry);
                        if status != 0 {
                            self.stage = Stage::Error;
                            return self.effect(5, [i64::from(status), 0, 0, 0]);
                        }
                    }
                    self.stage = Stage::NextY;
                }
                Stage::NextY => {
                    self.y = self.y.wrapping_sub(1);
                    self.stage = Stage::Write;
                }
                Stage::NextCell => {
                    self.s += 1;
                    if self.s == 16 {
                        self.s = 0;
                        self.r += 1;
                    }
                    self.stage = Stage::Cell;
                }
                Stage::SectionDone => {
                    self.stage = if self.below {
                        Stage::NextSection
                    } else {
                        Stage::Done
                    };
                }
                Stage::NextSection => {
                    self.section = self.section.wrapping_sub(1);
                    self.stage = Stage::Section;
                }
                Stage::Error => return -27,
                Stage::Done => {
                    self.reply.fill(0);
                    return 0;
                }
            }
        }
    }
    fn step(&mut self, scalar: i64) -> i32 {
        if self.action <= 0 {
            return self.action;
        }
        self.stage = match self.stage {
            Stage::Layer => {
                if scalar == 0 {
                    Stage::NextSection
                } else {
                    self.p = self.section.wrapping_shl(4);
                    self.q = self.p.wrapping_add(15);
                    self.r = 0;
                    self.s = 0;
                    self.below = false;
                    Stage::Cell
                }
            }
            Stage::Center => {
                self.t = scalar as i32;
                if self.t <= self.q {
                    Stage::North
                } else {
                    Stage::NextCell
                }
            }
            Stage::North => {
                self.north = scalar as i32;
                Stage::South
            }
            Stage::South => {
                self.south = scalar as i32;
                Stage::West
            }
            Stage::West => {
                self.west = scalar as i32;
                Stage::East
            }
            Stage::East => {
                self.east = scalar as i32;
                self.neighbors = self.north.max(self.south).max(self.west.max(self.east));
                self.y = self.q;
                Stage::Write
            }
            Stage::Write => Stage::AfterWrite,
            _ => return -27,
        };
        self.drive()
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_live_sky_begin(
    queues: *const c_void,
    x: i32,
    z: i32,
    top: i32,
    bottom: i32,
) -> u64 {
    if queues.is_null() {
        return 0;
    }
    Arc::increment_strong_count(queues.cast::<Queues>());
    let mut frame = Box::new(Frame {
        queues: Arc::from_raw(queues.cast()),
        chunk_x: x,
        chunk_z: z,
        bottom,
        section: top.wrapping_sub(1),
        p: 0,
        q: 0,
        r: 0,
        s: 0,
        t: 0,
        north: 0,
        south: 0,
        west: 0,
        east: 0,
        neighbors: 0,
        y: 0,
        below: false,
        stage: Stage::Section,
        action: 0,
        reply: [0; 4],
    });
    frame.action = catch_unwind(AssertUnwindSafe(|| frame.drive())).unwrap_or(-99);
    Box::into_raw(frame) as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_live_sky_read(id: u64, output: *mut i64) -> i32 {
    if id == 0 || output.is_null() {
        return -27;
    }
    let frame = &*(id as *const Frame);
    std::ptr::copy_nonoverlapping(frame.reply.as_ptr(), output, 4);
    frame.action
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_live_sky_step(id: u64, scalar: i64, output: *mut i64) -> i32 {
    if id == 0 || output.is_null() {
        return -27;
    }
    let frame = &mut *(id as *mut Frame);
    frame.action = catch_unwind(AssertUnwindSafe(|| frame.step(scalar))).unwrap_or(-99);
    mattmc_light_live_sky_read(id, output)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_live_sky_end(id: u64) {
    if id != 0 {
        drop(Box::from_raw(id as *mut Frame));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn actual_queue_survives_facade_release_and_abandoned_vm_write_adds_no_source() {
        unsafe {
            let queue = Arc::new(Queues::default());
            let weak = Arc::downgrade(&queue);
            let frame = mattmc_light_live_sky_begin(Arc::as_ptr(&queue).cast(), 0, 0, 1, 0);
            let mut out = [0i64; 4];
            assert_eq!(mattmc_light_live_sky_read(frame, out.as_mut_ptr()), 1);
            assert_eq!(mattmc_light_live_sky_step(frame, 1, out.as_mut_ptr()), 2);
            // One real cell at Y15; every neighbor lies at Y16.
            assert_eq!(mattmc_light_live_sky_step(frame, 15, out.as_mut_ptr()), 2);
            for _ in 0..3 {
                assert_eq!(mattmc_light_live_sky_step(frame, 16, out.as_mut_ptr()), 2);
            }
            assert_eq!(mattmc_light_live_sky_step(frame, 16, out.as_mut_ptr()), 3);
            assert_eq!(out, [0, 15, 0, 15]);
            assert!(queue.data.lock().unwrap()[1].is_empty());
            // A genuine VM callback can enqueue while the frame is suspended.
            assert_eq!(queue.enqueue(1, 123, 456), 0);
            drop(queue);
            assert!(
                weak.upgrade().is_some(),
                "frame retains the actual shared queue allocation"
            );
            {
                let retained = weak.upgrade().unwrap();
                assert_eq!(
                    retained.data.lock().unwrap()[1]
                        .iter()
                        .copied()
                        .collect::<Vec<_>>(),
                    [123, 456]
                );
            }
            // Throwing VM set never ACKs, so no native source pair may appear.
            mattmc_light_live_sky_end(frame);
            assert!(
                weak.upgrade().is_none(),
                "abandoning the real invocation releases its last queue lease"
            );
        }
    }

    #[test]
    fn successful_vm_write_appends_after_callback_prefix_and_publishes_actual_count() {
        unsafe {
            let queue = Arc::new(Queues::default());
            let frame = mattmc_light_live_sky_begin(Arc::as_ptr(&queue).cast(), 0, 0, 1, 0);
            let mut out = [0i64; 4];
            assert_eq!(mattmc_light_live_sky_step(frame, 1, out.as_mut_ptr()), 2);
            assert_eq!(mattmc_light_live_sky_step(frame, 15, out.as_mut_ptr()), 2);
            for _ in 0..3 {
                assert_eq!(mattmc_light_live_sky_step(frame, 16, out.as_mut_ptr()), 2);
            }
            assert_eq!(mattmc_light_live_sky_step(frame, 16, out.as_mut_ptr()), 3);
            assert_eq!(queue.enqueue(1, 123, 456), 0);
            // ACK the real VM write. A new center read follows the emitted pair.
            assert_eq!(mattmc_light_live_sky_step(frame, 0, out.as_mut_ptr()), 2);
            let pending = queue.data.lock().unwrap()[1]
                .iter()
                .copied()
                .collect::<Vec<_>>();
            assert_eq!(pending.len(), 4);
            assert_eq!(&pending[..3], &[123, 456, 15]);
            assert_eq!(
                queue.counts[1].load(std::sync::atomic::Ordering::Acquire),
                4
            );
            mattmc_light_live_sky_end(frame);
            assert_eq!(
                queue.data.lock().unwrap()[1]
                    .iter()
                    .copied()
                    .collect::<Vec<_>>(),
                pending,
                "frame retirement cannot roll back committed callback or source entries"
            );
        }
    }
}
