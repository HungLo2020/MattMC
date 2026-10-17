//! Pipelined whole-frame execution.
//!
//! A pipelined whole-frame submit copies the request out of Java memory on the
//! calling thread, then executes the frame and presents it on a per-context
//! worker while Java collects the next frame. Exclusivity is by handoff, not
//! locking: every bridge entry point first joins all pending work
//! (`with_registry`, `with_registry_mut`), so the worker and the calling
//! thread never use a context at the same time. Java reads the deferred
//! submit and present results with an explicit join.

use crate::render::bridge::*;
use std::sync::mpsc;
use std::sync::{Arc, Mutex};

/// Results of one pipelined frame, in the records the synchronous entry
/// points would have returned.
#[derive(Clone, Copy, Default)]
pub(crate) struct PipelinedFrameOutcome {
    pub(crate) submit_status: i32,
    pub(crate) submit: FfiWholeFrameSubmitResult,
    /// `None` when the submit failed and nothing was presented.
    pub(crate) present: Option<(i32, FfiFramePresentResult)>,
}

/// A frame job and the context it runs on.
struct Job(Box<dyn FnOnce()>);

// SAFETY: a job is sent only while its context is handed to the worker:
// the dispatching entry point returns without touching the context again,
// and every later entry point joins the job before using any context. The
// job's captured values are owned copies of the decoded request.
unsafe impl Send for Job {}

/// A raw context pointer that crosses to the worker under the handoff rule.
pub(crate) struct ContextHandoff(pub(crate) *mut BridgeContext);

// SAFETY: see `Job`; the boxed context's address is stable while registered.
unsafe impl Send for ContextHandoff {}

/// Results of one queued frame: the swapchain acquire its job performed,
/// then (when an image was acquired) its submit and present.
#[derive(Clone, Copy, Default)]
pub(crate) struct QueuedFrameOutcome {
    pub(crate) acquire_status: i32,
    pub(crate) acquire: FfiFrameAcquireResult,
    pub(crate) frame: PipelinedFrameOutcome,
}

/// State queued jobs report to the Java thread.
#[derive(Default)]
pub(crate) struct QueueState {
    /// Outcomes of queued frames, oldest first.
    pub(crate) frames: std::collections::VecDeque<QueuedFrameOutcome>,
    /// The first failure of a queued non-frame job (asset update, animation
    /// tick); reported by the next queued-frame join, which fails closed.
    pub(crate) error: Option<(i32, String)>,
}

pub(crate) struct FramePipeline {
    jobs: Option<mpsc::Sender<Job>>,
    done: mpsc::Receiver<()>,
    /// Jobs sent and not yet observed finished.
    pending: std::cell::Cell<usize>,
    worker: Option<std::thread::JoinHandle<()>>,
    outcome: Arc<Mutex<Option<PipelinedFrameOutcome>>>,
    queue: Arc<Mutex<QueueState>>,
    /// The context's backend capabilities, captured while the context was
    /// joined; queued entry points decode against them without touching the
    /// context the worker owns.
    capabilities: BackendCapabilities,
    /// The registered (boxed, address-stable) context, captured while joined,
    /// so queuing never forms a reference to a context a job may be using.
    context: *mut BridgeContext,
}

impl FramePipeline {
    pub(crate) fn new(context: &mut BridgeContext) -> GalResult<Self> {
        let mut pipeline = Self::new_detached(context.gal.capabilities())?;
        pipeline.context = context as *mut BridgeContext;
        Ok(pipeline)
    }

    fn new_detached(capabilities: BackendCapabilities) -> GalResult<Self> {
        let context = std::ptr::null_mut();
        let (jobs, job_receiver) = mpsc::channel::<Job>();
        let (done_sender, done) = mpsc::channel();
        let worker = std::thread::Builder::new()
            .name("mattmc-frame-worker".into())
            .spawn(move || {
                prefer_fastest_cores();
                while let Ok(Job(job)) = job_receiver.recv() {
                    // A panic is converted into a failed outcome by the job's
                    // own guard; never leave the joining thread waiting.
                    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(job));
                    if done_sender.send(()).is_err() {
                        break;
                    }
                }
            })
            .map_err(|error| GalError::backend(format!("frame worker could not start: {error}")))?;
        Ok(Self {
            jobs: Some(jobs),
            done,
            pending: std::cell::Cell::new(0),
            worker: Some(worker),
            outcome: Arc::new(Mutex::new(None)),
            queue: Arc::new(Mutex::new(QueueState::default())),
            capabilities,
            context,
        })
    }

    pub(crate) fn capabilities(&self) -> &BackendCapabilities {
        &self.capabilities
    }

    /// The context, for a queued job to use when it runs.
    pub(crate) fn context_handoff(&self) -> ContextHandoff {
        ContextHandoff(self.context)
    }

    /// Waits for every sent job. The context is the caller's again afterwards.
    pub(crate) fn join(&self) {
        while self.pending.get() > 0 {
            let _ = self.done.recv();
            self.pending.set(self.pending.get() - 1);
        }
    }

    /// Queues a job behind the jobs already sent, without waiting for them.
    /// The context stays the worker's until a bridge entry point joins.
    pub(crate) fn enqueue(
        &self,
        job: impl FnOnce(&Mutex<QueueState>) + 'static,
    ) -> GalResult<()> {
        let queue = Arc::clone(&self.queue);
        let jobs = self
            .jobs
            .as_ref()
            .ok_or_else(|| GalError::backend("frame worker has stopped"))?;
        jobs.send(Job(Box::new(move || job(&queue))))
            .map_err(|_| GalError::backend("frame worker has stopped"))?;
        self.pending.set(self.pending.get() + 1);
        Ok(())
    }

    /// The oldest queued frame's outcome, waiting only for the jobs up to
    /// it. `None` when no queued frame is outstanding.
    pub(crate) fn next_queued_frame(&self) -> Option<QueuedFrameOutcome> {
        loop {
            if let Some(outcome) = self.queue.lock().ok().and_then(|mut queue| queue.frames.pop_front()) {
                return Some(outcome);
            }
            if self.pending.get() == 0 {
                return None;
            }
            let _ = self.done.recv();
            self.pending.set(self.pending.get() - 1);
        }
    }

    /// Takes the first failure of a queued non-frame job, if any.
    pub(crate) fn take_queued_error(&self) -> Option<(i32, String)> {
        self.queue.lock().ok().and_then(|mut queue| queue.error.take())
    }

    /// Hands one frame to the worker. The caller must not use the context again
    /// until a bridge entry point has joined this job.
    pub(crate) fn dispatch(
        &self,
        job: impl FnOnce(&Mutex<Option<PipelinedFrameOutcome>>) + 'static,
    ) -> GalResult<()> {
        self.join();
        let outcome = Arc::clone(&self.outcome);
        let jobs = self
            .jobs
            .as_ref()
            .ok_or_else(|| GalError::backend("frame worker has stopped"))?;
        jobs.send(Job(Box::new(move || job(&outcome))))
            .map_err(|_| GalError::backend("frame worker has stopped"))?;
        self.pending.set(1);
        Ok(())
    }

    pub(crate) fn is_pending(&self) -> bool {
        self.pending.get() > 0
    }

    /// The last completed frame's results, once.
    pub(crate) fn take_outcome(&self) -> Option<PipelinedFrameOutcome> {
        self.join();
        self.outcome.lock().ok().and_then(|mut outcome| outcome.take())
    }
}

/// Keeps the first failure of a queued non-frame job for the next join.
pub(crate) fn record_queued_error(queue: &Mutex<QueueState>, error: &GalError) {
    if let Ok(mut queue) = queue.lock() {
        queue.error.get_or_insert((error.code as i32, error.message.clone()));
    }
}

/// The worker runs a whole frame per job, on the critical path. On hybrid
/// CPUs the scheduler may place it on an efficiency core, which measurably
/// lengthens every frame; keep it on the highest-frequency CPUs it is allowed
/// to use. Changes nothing where all allowed CPUs share one maximum frequency.
fn prefer_fastest_cores() {
    #[cfg(target_os = "linux")]
    {
        // SAFETY: plain libc calls on a zeroed, owned cpu_set_t for this thread.
        unsafe {
            let mut allowed: libc::cpu_set_t = std::mem::zeroed();
            if libc::sched_getaffinity(0, std::mem::size_of::<libc::cpu_set_t>(), &mut allowed) != 0 {
                return;
            }
            let max_frequency = |cpu: usize| {
                std::fs::read_to_string(format!("/sys/devices/system/cpu/cpu{cpu}/cpufreq/cpuinfo_max_freq"))
                    .ok()
                    .and_then(|text| text.trim().parse::<u64>().ok())
            };
            let cpus = (0..libc::CPU_SETSIZE as usize)
                .filter(|&cpu| libc::CPU_ISSET(cpu, &allowed))
                .map(|cpu| (cpu, max_frequency(cpu)))
                .collect::<Vec<_>>();
            let Some(fastest) = cpus.iter().filter_map(|(_, frequency)| *frequency).max() else {
                return;
            };
            if cpus.iter().all(|(_, frequency)| *frequency == Some(fastest)) {
                return;
            }
            let mut preferred: libc::cpu_set_t = std::mem::zeroed();
            for (cpu, frequency) in &cpus {
                if *frequency == Some(fastest) {
                    libc::CPU_SET(*cpu, &mut preferred);
                }
            }
            let _ = libc::sched_setaffinity(0, std::mem::size_of::<libc::cpu_set_t>(), &preferred);
        }
    }
}

#[cfg(test)]
impl FramePipeline {
    fn for_test() -> Self {
        let mut pipeline = Self::new_detached(crate::render::vulkanic::test_support::mock_gal().capabilities()).unwrap();
        pipeline.context = std::ptr::null_mut();
        pipeline
    }
}

impl Drop for FramePipeline {
    fn drop(&mut self) {
        self.join();
        drop(self.jobs.take());
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU32, Ordering};

    #[test]
    fn join_waits_for_the_frame_and_outcomes_are_taken_once() {
        let pipeline = FramePipeline::for_test();
        let progress = Arc::new(AtomicU32::new(0));
        let observed = Arc::clone(&progress);
        pipeline
            .dispatch(move |outcome| {
                std::thread::sleep(std::time::Duration::from_millis(20));
                observed.store(1, Ordering::SeqCst);
                *outcome.lock().unwrap() = Some(PipelinedFrameOutcome {
                    submit_status: 7,
                    ..PipelinedFrameOutcome::default()
                });
            })
            .unwrap();
        assert!(pipeline.is_pending());
        pipeline.join();
        assert!(!pipeline.is_pending());
        assert_eq!(progress.load(Ordering::SeqCst), 1);
        assert_eq!(pipeline.take_outcome().map(|outcome| outcome.submit_status), Some(7));
        assert!(pipeline.take_outcome().is_none());
    }

    #[test]
    fn queued_jobs_run_in_order_and_frames_join_one_at_a_time() {
        let pipeline = FramePipeline::for_test();
        let order = Arc::new(Mutex::new(Vec::new()));
        for job in 0..3 {
            let order = Arc::clone(&order);
            pipeline
                .enqueue(move |queue| {
                    order.lock().unwrap().push(job);
                    if job == 1 {
                        record_queued_error(queue, &GalError::invalid_argument("asset update failed"));
                    } else {
                        queue.lock().unwrap().frames.push_back(QueuedFrameOutcome {
                            acquire_status: job,
                            ..QueuedFrameOutcome::default()
                        });
                    }
                })
                .unwrap();
        }
        assert_eq!(pipeline.next_queued_frame().map(|frame| frame.acquire_status), Some(0));
        assert_eq!(pipeline.next_queued_frame().map(|frame| frame.acquire_status), Some(2));
        assert!(pipeline.take_queued_error().is_some_and(|(_, message)| message.contains("asset update")));
        assert!(pipeline.take_queued_error().is_none());
        assert!(pipeline.next_queued_frame().is_none());
        assert!(!pipeline.is_pending());
        assert_eq!(*order.lock().unwrap(), vec![0, 1, 2]);
    }

    #[test]
    fn dispatch_joins_the_previous_frame_first_and_jobs_run_in_order() {
        let pipeline = FramePipeline::for_test();
        let order = Arc::new(Mutex::new(Vec::new()));
        for frame in 0..3 {
            let order = Arc::clone(&order);
            pipeline
                .dispatch(move |outcome| {
                    order.lock().unwrap().push(frame);
                    *outcome.lock().unwrap() = Some(PipelinedFrameOutcome {
                        submit_status: frame,
                        ..PipelinedFrameOutcome::default()
                    });
                })
                .unwrap();
        }
        assert_eq!(pipeline.take_outcome().map(|outcome| outcome.submit_status), Some(2));
        assert_eq!(*order.lock().unwrap(), vec![0, 1, 2]);
        // A panicking frame still releases the joining thread.
        pipeline.dispatch(|_| panic!("frame job failed")).unwrap();
        pipeline.join();
        assert!(pipeline.take_outcome().is_none());
    }
}
