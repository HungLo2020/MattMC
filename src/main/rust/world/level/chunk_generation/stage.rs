//! Native stage admission and engine-completion control. Actual virtual holder,
//! step, engine and future calls remain effects; no borrowed output crosses one.
use super::TaskEffects;
use std::sync::atomic::{AtomicU64, Ordering};
static ADMISSIONS: AtomicU64 = AtomicU64::new(0);
static ENGINES: AtomicU64 = AtomicU64::new(0);
static COMPLETIONS: AtomicU64 = AtomicU64::new(0);
static FATALS: AtomicU64 = AtomicU64::new(0);
/// Stage effect attempts, not allocations, elapsed cost or total process work.
pub(super) fn statistics() -> [u64; 4] {
    [&ADMISSIONS, &ENGINES, &COMPLETIONS, &FATALS].map(|n| n.load(Ordering::Relaxed))
}

pub(super) async fn admit(effects: &mut impl TaskEffects) -> Result<(), ()> {
    ADMISSIONS.fetch_add(1, Ordering::Relaxed);
    effects.call(80, 0, 0, 0, 0).await?;
    if effects.call(1, 0, 0, 0, 0).await? != 0 {
        effects.call(8, 0, 0, 0, 0).await?;
        return Ok(());
    }
    // Each original target accessor remains a fresh VM observation. A custom
    // target/step can reenter or return a different resource on its next call.
    effects.call(80, 0, 0, 0, 0).await?;
    let empty = effects.call(81, 0, 0, 0, 0).await? != 0;
    effects.call(if empty { 82 } else { 83 }, 0, 0, 0, 0).await?;
    let previous = effects.call(84, 0, 0, 0, 0).await?;
    let admitted = if previous == 1 { true }
        else if previous == 2 || effects.call(85, 0, 0, 0, 0).await? != 0 {
            effects.call(86, 0, 0, 0, 0).await?;
            return Err(());
        } else { false };
    if admitted {
        // The native started-status commit precedes both the engine call and
        // actual handle registration, including either synchronous failure.
        ENGINES.fetch_add(1, Ordering::Relaxed);
        effects.call(94, 0, 0, 0, 0).await?;
        effects.call(87, 0, 0, 0, 0).await?;
        effects.call(88, 0, 0, 0, 0).await?;
    } else {
        effects.call(80, 0, 0, 0, 0).await?;
        super::readiness::get_or_create(effects).await?;
        effects.call(95, 0, 0, 0, 0).await?;
    }
    Ok(())
}

pub(super) async fn completed(effects: &mut impl TaskEffects) -> Result<(), ()> {
    COMPLETIONS.fetch_add(1, Ordering::Relaxed);
    if effects.call(90, 0, 0, 0, 0).await? != 0 {
        // Fatal reporting belongs only to the engine's supplied failure. A
        // throwing completion/publication is not reclassified as an engine error.
        FATALS.fetch_add(1, Ordering::Relaxed);
        effects.call(91, 0, 0, 0, 0).await?;
    } else {
        effects.call(80, 0, 0, 0, 0).await?;
        effects.call(92, 0, 0, 0, 0).await?;
        super::readiness::complete_future(effects).await?;
    }
    // Keep the work wrapper distinct from the status-slot publication wrapper,
    // including a custom handle callback providing both chunk and failure.
    effects.call(93, 0, 0, 0, 0).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{collections::VecDeque, future::Future, task::{Context, Poll, Waker}};
    struct Effects { expected: VecDeque<(i32, Result<i64, ()>)>, calls: Vec<i32> }
    impl Effects {
        fn new(script: &[(i32, i64)]) -> Self {
            Self { expected: script.iter().map(|&(op, response)| (op, Ok(response))).collect(), calls: Vec::new() }
        }
    }
    impl TaskEffects for Effects {
        async fn call(&mut self, op: i32, _a: i32, _b: i32, _c: i32, _token: u64) -> Result<i64, ()> {
            let (expected, response) = self.expected.pop_front().expect("unexpected stage effect");
            assert_eq!(op, expected); self.calls.push(op); response
        }
    }
    fn ready<F: Future>(future: F) -> F::Output {
        let mut future = std::pin::pin!(future);
        match future.as_mut().poll(&mut Context::from_waker(Waker::noop())) {
            Poll::Ready(result) => result, Poll::Pending => panic!("test leaves must complete immediately"),
        }
    }
    #[test] fn disallowed_admission_never_bumps_or_dispatches() {
        let mut e = Effects::new(&[(80,0),(1,1),(8,0)]);
        assert_eq!(ready(admit(&mut e)), Ok(())); assert!(e.expected.is_empty());
    }
    #[test] fn admitted_stage_dispatches_before_actual_handle_registration() {
        let mut e = Effects::new(&[(80,0),(1,0),(80,0),(81,1),(82,0),(84,1),(94,0),(87,0),(88,0)]);
        assert_eq!(ready(admit(&mut e)), Ok(())); assert!(e.expected.is_empty());
    }
    #[test] fn existing_stage_rereads_target_then_composes_live_future_request() {
        let mut e = Effects::new(&[(80,0),(1,0),(80,0),(81,0),(83,0),(84,3),(85,0),
            (80,0),(1,0),(2,1),(3,1),(6,0),(95,0)]);
        assert_eq!(ready(admit(&mut e)), Ok(())); assert!(e.expected.is_empty());
    }
    #[test] fn supplied_engine_failure_reports_before_work_wrapper_without_slot_publication() {
        let mut e = Effects::new(&[(90,1),(91,0),(93,0)]);
        assert_eq!(ready(completed(&mut e)), Ok(())); assert!(e.expected.is_empty());
    }
    #[test] fn success_composes_native_slot_completion_before_distinct_work_wrapper() {
        let mut e = Effects::new(&[(90,0),(80,0),(92,0),(2,0),(3,0),(12,0),(5,0),(93,0)]);
        assert_eq!(ready(completed(&mut e)), Ok(())); assert!(e.expected.is_empty());
    }
    #[test] fn throwing_engine_keeps_the_admission_prefix_without_register_or_fatal() {
        let mut e = Effects::new(&[(80,0),(1,0),(80,0),(81,1),(82,0),(84,1),(94,0),(87,0)]);
        e.expected.back_mut().unwrap().1 = Err(());
        assert_eq!(ready(admit(&mut e)), Err(())); assert!(e.expected.is_empty());
    }
}
