//! Chunk-range prerequisites. VM signals report actual completion; Rust owns
//! membership, the all-prerequisite barrier, error selection and ordered effects.
//! Never retain a borrowed state or lock while performing a VM effect.
use std::sync::Mutex;
use std::collections::VecDeque;
use std::sync::atomic::{AtomicU64, Ordering};
use super::{Arc, Holder, TaskDriver, TaskEffects, DriverExchange, ContinuationEffects};

/// Keep the actual task incarnation across dispatch and inline wake callbacks.
/// Cancellation does not bypass its outstanding prerequisite probes/releases.
pub(super) fn dispatch(owner: Option<Arc<super::Task>>, run: bool) -> TaskDriver {
    let exchange = Arc::new(DriverExchange::new());
    let mut effects = ContinuationEffects { exchange: exchange.clone() };
    let future = Box::pin(async move {
        let _owner = owner;
        if run {
            if effects.call(3, 0, 0, 0, 0).await? != 0 {
                effects.call(4, 0, 0, 0, 0).await?;
            }
        } else {
            effects.call(1, 0, 0, 0, 0).await?;
            effects.call(2, 0, 0, 0, 0).await?;
        }
        Ok(false)
    });
    TaskDriver::from_future(future, exchange)
}

pub(super) fn prepare_chunk(owner: Option<Arc<Holder>>, kind: i32, phase: i32) -> TaskDriver {
    let exchange = Arc::new(DriverExchange::new());
    let mut effects = ContinuationEffects { exchange: exchange.clone() };
    let future = Box::pin(async move {
        let _owner = owner;
        if !(0..=2).contains(&kind) { return Err(()); }
        match phase {
            0 => {
                let radius = if kind == 2 { 2 } else { 1 };
                effects.call(if kind == 0 { 13 } else { 12 }, radius, 0, 0, 0).await?;
                effects.call(if kind == 1 { 3 } else { 2 }, 0, 0, 0, 0).await?;
            }
            1 => {
                let size = effects.call(50, 0, 0, 0, 0).await? as i32;
                effects.call(51, size / 2, 0, 0, 0).await?;
                if kind == 1 {
                    effects.call(52, 0, 0, 0, 0).await?;
                    effects.call(53, 0, 0, 0, 0).await?;
                    let done = effects.call(54, 0, 0, 0, 0).await?;
                    effects.call(if done != 0 { 56 } else { 57 }, 0, 0, 0, 0).await?;
                }
            }
            2 => { effects.call(56, 0, 0, 0, 0).await?; }
            _ => return Err(()),
        }
        Ok(false)
    });
    TaskDriver::from_future(future, exchange)
}

async fn fail_pending(effects: &mut impl TaskEffects, slot: i32) -> Result<(), ()> {
    if effects.call(9, slot, 0, 0, 0).await? != 0
        && effects.call(10, slot, 0, 0, 0).await? == 0 {
        effects.call(11, 0, 0, 0, 0).await?;
        return Err(());
    }
    Ok(())
}

async fn get_or_create(effects: &mut impl TaskEffects) -> Result<(), ()> {
    if effects.call(1, 0, 0, 0, 0).await? != 0 {
        effects.call(8, 0, 0, 0, 0).await?;
        return Ok(());
    }
    let slot = effects.call(2, 0, 0, 0, 0).await? as i32;
    if effects.call(3, slot, 0, 0, 0).await? == 0 {
        effects.call(4, 0, 0, 0, 0).await?;
        if effects.call(5, slot, 0, 0, 0).await? == 0 {
            if effects.call(1, 0, 0, 0, 0).await? != 0 {
                fail_pending(effects, slot).await?;
                effects.call(8, 0, 0, 0, 0).await?;
            } else { effects.call(7, 0, 0, 0, 0).await?; }
            return Ok(());
        }
    }
    effects.call(6, 0, 0, 0, 0).await?;
    Ok(())
}

async fn reschedule(effects: &mut impl TaskEffects, present: bool) -> Result<(), ()> {
    effects.call(if present { 22 } else { 23 }, 0, 0, 0, 0).await?;
    if effects.call(24, 0, 0, 0, 0).await? != 0 {
        effects.call(26, 0, 0, 0, 0).await?;
    }
    Ok(())
}

async fn fail_range(effects: &mut impl TaskEffects, first: i32, last: i32) -> Result<(), ()> {
    let mut slot = first;
    while slot <= last {
        if effects.call(3, slot, 0, 0, 0).await? != 0 {
            effects.call(17, 0, 0, 0, 0).await?;
            fail_pending(effects, slot).await?;
        }
        slot = slot.wrapping_add(1);
    }
    Ok(())
}

/// Completion and downgrade decisions use actual VM method results. A future's
/// externally mutable completion is never inferred from native slot membership.
pub(super) fn holder(owner: Arc<Holder>, operation: i32) -> TaskDriver {
    let exchange = Arc::new(DriverExchange::new());
    let mut effects = ContinuationEffects { exchange: exchange.clone() };
    let future = Box::pin(async move {
        let owner = owner;
        match operation {
            0 => get_or_create(&mut effects).await?,
            1 => {
                let slot = effects.call(2, 0, 0, 0, 0).await? as i32;
                loop {
                    if effects.call(3, slot, 0, 0, 0).await? == 0 {
                        effects.call(12, 0, 0, 0, 0).await?;
                        if effects.call(5, slot, 0, 0, 0).await? == 0 { break; }
                    } else {
                        if effects.call(13, 0, 0, 0, 0).await? != 0 { break; }
                        if effects.call(14, 0, 0, 0, 0).await? != 0 {
                            effects.call(15, 0, 0, 0, 0).await?;
                            return Err(());
                        }
                        effects.call(16, 0, 0, 0, 0).await?;
                    }
                }
            }
            4 => {
                match effects.call(30, 0, 0, 0, 0).await? {
                    1 => { effects.call(8, 0, 0, 0, 0).await?; return Ok(false); }
                    2 => {
                        if effects.call(1, 0, 0, 0, 0).await? != 0 {
                            effects.call(8, 0, 0, 0, 0).await?;
                            return Ok(false);
                        }
                        get_or_create(&mut effects).await?;
                        effects.call(18, 0, 0, 0, 0).await?;
                    }
                    _ => {}
                }
                if effects.call(19, 0, 0, 0, 0).await? == 0 {
                    if effects.call(20, 0, 0, 0, 0).await? == 0
                        || effects.call(21, 0, 0, 0, 0).await? != 0 {
                        reschedule(&mut effects, true).await?;
                    }
                }
                effects.call(6, 0, 0, 0, 0).await?;
            }
            5 => {
                let had_allowed = effects.call(31, 0, 0, 0, 0).await? != 0;
                let allowed = effects.call(32, 0, 0, 0, 0).await? != 0;
                if had_allowed && (!allowed || effects.call(33, 0, 0, 0, 0).await? != 0) {
                    let first = if allowed { (effects.call(34, 0, 0, 0, 0).await? as i32).wrapping_add(1) } else { 0 };
                    let last = effects.call(35, 0, 0, 0, 0).await? as i32;
                    fail_range(&mut effects, first, last).await?;
                    if effects.call(20, 0, 0, 0, 0).await? != 0 {
                        let mut found = false;
                        if allowed {
                            // The original loop captures started once, while
                            // each parent/comparison uses its actual VM object.
                            let started = effects.call(36, 0, 0, 0, 0).await? != 0;
                            while !started || effects.call(37, 0, 0, 0, 0).await? != 0 {
                                let slot = effects.call(2, 0, 0, 0, 0).await? as i32;
                                if effects.call(3, slot, 0, 0, 0).await? != 0 { found = true; break; }
                                if effects.call(38, 0, 0, 0, 0).await? != 0 { break; }
                                effects.call(39, 0, 0, 0, 0).await?;
                            }
                        }
                        reschedule(&mut effects, found).await?;
                    }
                }
            }
            6 => {
                if owner.change(6, 0, 0) == 0 {
                    effects.call(40, 0, 0, 0, 0).await?;
                    effects.call(41, 0, 0, 0, 0).await?;
                    effects.call(42, 0, 0, 0, 0).await?;
                }
            }
            7 => {
                // Capture the actual fence before decrement and inline completion.
                effects.call(43, 0, 0, 0, 0).await?;
                let count = owner.change(7, 0, 0) as i32;
                if count == 0 { effects.call(44, 0, 0, 0, 0).await?; }
                if count < 0 { effects.call(45, count, 0, 0, 0).await?; }
            }
            _ => return Err(()),
        }
        Ok(false)
    });
    TaskDriver::from_future(future, exchange)
}

#[derive(Clone, Copy)]
enum Input { Leaf(usize), Node(usize) }
#[derive(Clone, Copy)]
enum Publication { Waiting, Queued, Claimed, Prepared(bool), Published(bool) }
#[derive(Clone, Copy)]
struct Node { left: Input, right: Input, publication: Publication, parent: usize }
#[derive(Clone, Copy)]
enum Pass { Join(usize), Allocate, Result(usize), Finished }
struct State {
    leaves: Vec<bool>,
    parents: Vec<usize>,
    nodes: Vec<Node>,
    ready: VecDeque<usize>,
    armed: bool,
    claimed: bool,
    fence: bool,
    pass: Pass,
}
static LIVE_RANGES: AtomicU64 = AtomicU64::new(0);
static CREATED_RANGES: AtomicU64 = AtomicU64::new(0);
static RETIRED_RANGES: AtomicU64 = AtomicU64::new(0);
static LIVE_BYTES: AtomicU64 = AtomicU64::new(0);
static PEAK_BYTES: AtomicU64 = AtomicU64::new(0);

pub(super) struct Range { state: Mutex<State>, bytes: u64 }

impl Drop for Range {
    fn drop(&mut self) {
        LIVE_RANGES.fetch_sub(1, Ordering::Relaxed);
        LIVE_BYTES.fetch_sub(self.bytes, Ordering::Relaxed);
        RETIRED_RANGES.fetch_add(1, Ordering::Relaxed);
    }
}

/// Requested owner/vector payload only: excludes Arc control blocks, allocator
/// overhead, VM resources and all other native/process allocations.
pub(super) fn statistics() -> [u64; 5] {
    [&LIVE_RANGES, &CREATED_RANGES, &RETIRED_RANGES, &LIVE_BYTES, &PEAK_BYTES]
        .map(|counter| counter.load(Ordering::Relaxed))
}

impl Range {
    pub(super) fn new(count: usize) -> Option<Self> {
        Self::new_role(count, false)
    }
    pub(super) fn new_role(count: usize, fence: bool) -> Option<Self> {
        let mut leaves = Vec::new();
        leaves.try_reserve_exact(count).ok()?;
        leaves.resize(count, false);
        let mut parents = Vec::new();
        parents.try_reserve_exact(count).ok()?;
        parents.resize(count, 0);
        let mut nodes = Vec::new();
        nodes.try_reserve_exact(count.max(1)).ok()?;
        let mut ready = VecDeque::new();
        ready.try_reserve_exact(count).ok()?;
        if count != 0 { Self::build(&mut nodes, &mut parents, 0, count - 1); }
        let bytes = (std::mem::size_of::<Self>() + leaves.capacity() * std::mem::size_of::<bool>()
            + nodes.capacity() * std::mem::size_of::<Node>()
            + (parents.capacity() + ready.capacity()) * std::mem::size_of::<usize>()) as u64;
        LIVE_RANGES.fetch_add(1, Ordering::Relaxed);
        CREATED_RANGES.fetch_add(1, Ordering::Relaxed);
        let live = LIVE_BYTES.fetch_add(bytes, Ordering::Relaxed) + bytes;
        PEAK_BYTES.fetch_max(live, Ordering::Relaxed);
        Some(Self { state: Mutex::new(State { leaves, parents, nodes, ready, armed: false,
            claimed: false, fence, pass: Pass::Join(0) }), bytes })
    }
    // The original allOf splits at mid and uses direct future inputs at the
    // edges. A direct future stays mutable until THIS node publishes; a
    // previously published child node keeps its already observed outcome.
    fn build(nodes: &mut Vec<Node>, parents: &mut [usize], lo: usize, hi: usize) -> usize {
        let mid = (lo + hi) >> 1;
        let left = if lo == mid { Input::Leaf(lo) } else { Input::Node(Self::build(nodes, parents, lo, mid)) };
        let right = if lo == hi { left } else if hi == mid + 1 { Input::Leaf(hi) }
            else { Input::Node(Self::build(nodes, parents, mid + 1, hi)) };
        let index = nodes.len();
        for input in [left, right] {
            match input {
                Input::Leaf(leaf) => parents[leaf] = index + 1,
                Input::Node(child) => nodes[child].parent = index + 1,
            }
        }
        nodes.push(Node { left, right, publication: Publication::Waiting, parent: 0 });
        index
    }
    fn terminal(state: &mut State) -> u64 {
        if !state.armed || state.claimed { return 0; }
        let failed = match state.nodes.last() {
            None => false,
            Some(Node { publication: Publication::Published(failed), .. }) => *failed,
            _ => return 0,
        };
        state.claimed = true;
        // Completion also selects the last-use boundary of actual VM inputs:
        // ranges still need joins; fences and failures can release before wake.
        if failed { 3u64 << 32 | state.nodes.len().saturating_sub(1) as u64 }
        else if state.fence { 2u64 << 32 }
        else { 1u64 << 32 }
    }
    pub(super) fn event(&self, index: Option<usize>) -> u64 {
        let mut state = self.state.lock().unwrap_or_else(|p| p.into_inner());
        if let Some(index) = index {
            let Some(leaf) = state.leaves.get_mut(index) else { return 0; };
            *leaf = true;
            let parent = state.parents[index];
            Self::enqueue(&mut state, parent);
        } else { state.armed = true; }
        Self::terminal(&mut state)
    }
    fn available(state: &State, input: Input) -> bool {
        match input {
            Input::Leaf(index) => state.leaves[index],
            Input::Node(index) => matches!(state.nodes[index].publication, Publication::Published(_)),
        }
    }
    fn enqueue(state: &mut State, parent: usize) {
        if parent == 0 { return; }
        let index = parent - 1;
        let node = state.nodes[index];
        if matches!(node.publication, Publication::Waiting)
            && Self::available(state, node.left) && Self::available(state, node.right) {
            state.nodes[index].publication = Publication::Queued;
            // Every node enters once; capacity was reserved before publication.
            state.ready.push_back(index);
        }
    }
    pub(super) fn claim(&self) -> u64 {
        let mut state = self.state.lock().unwrap_or_else(|p| p.into_inner());
        let Some(index) = state.ready.pop_front() else { return 0; };
        debug_assert!(matches!(state.nodes[index].publication, Publication::Queued));
        state.nodes[index].publication = Publication::Claimed;
        index as u64 + 1
    }
    pub(super) fn input(&self, index: usize, right: bool) -> i32 {
        let state = self.state.lock().unwrap_or_else(|p| p.into_inner());
        let Some(node) = state.nodes.get(index) else { return i32::MIN; };
        match if right { node.right } else { node.left } {
            Input::Leaf(index) => -(index as i32) - 1,
            Input::Node(index) => index as i32,
        }
    }
    /// Select the actual VM error resource before publishing this node. The
    /// caller binds it with heap release semantics, then commits without allocation.
    pub(super) fn prepare(&self, index: usize, left: bool, right: bool) -> i32 {
        let mut state = self.state.lock().unwrap_or_else(|p| p.into_inner());
        let Some(node) = state.nodes.get_mut(index) else { return -1; };
        if !matches!(node.publication, Publication::Claimed) { return -1; }
        node.publication = Publication::Prepared(left || right);
        if left { 1 } else if right { 2 } else { 0 }
    }
    pub(super) fn commit(&self, index: usize) -> u64 {
        let mut state = self.state.lock().unwrap_or_else(|p| p.into_inner());
        let Some(node) = state.nodes.get_mut(index) else { return 0; };
        let Publication::Prepared(failed) = node.publication else { return 0; };
        node.publication = Publication::Published(failed);
        let parent = node.parent;
        Self::enqueue(&mut state, parent);
        Self::terminal(&mut state)
    }
    pub(super) fn next(&self, present: bool) -> u64 {
        let mut state = self.state.lock().unwrap_or_else(|p| p.into_inner());
        let count = state.leaves.len();
        let (op, row, next) = match state.pass {
            Pass::Join(row) if row < count => (1, row, Pass::Join(row + 1)),
            Pass::Join(_) => (2, 0, Pass::Allocate),
            Pass::Allocate if count != 0 => (3, 0, Pass::Result(0)),
            Pass::Allocate => (4, 0, Pass::Finished),
            Pass::Result(_) if !present => (5, 0, Pass::Finished),
            Pass::Result(row) if row + 1 < count => (3, row + 1, Pass::Result(row + 1)),
            Pass::Result(_) => (4, 0, Pass::Finished),
            Pass::Finished => return 0,
        };
        state.pass = next;
        (op as u64) << 32 | row as u64
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn drain(range: &Range, leaves: &[bool], errors: &mut [bool]) -> u64 {
        let mut terminal = 0;
        loop {
            let claimed = range.claim();
            if claimed == 0 { return terminal; }
            let node = claimed as usize - 1;
            let failed = |input: i32| if input < 0 { leaves[(-input - 1) as usize] } else { errors[input as usize] };
            let left = failed(range.input(node, false));
            let right = failed(range.input(node, true));
            assert!(range.prepare(node, left, right) >= 0);
            errors[node] = left || right;
            terminal |= range.commit(node);
        }
    }
    #[test] fn direct_input_recovery_before_pair_publication_is_observed() {
        let range = Range::new(3).unwrap(); let mut errors = [false; 3];
        range.event(None); range.event(Some(0));
        assert_eq!(drain(&range, &[true, false, false], &mut errors), 0);
        range.event(Some(1));
        assert_eq!(drain(&range, &[false; 3], &mut errors), 0);
        range.event(Some(2));
        assert_eq!(drain(&range, &[false; 3], &mut errors), 1u64 << 32);
    }
    #[test] fn published_pair_failure_survives_later_leaf_recovery() {
        let range = Range::new(3).unwrap(); let mut errors = [false; 3];
        range.event(None); range.event(Some(0)); range.event(Some(1));
        assert_eq!(drain(&range, &[true, false, false], &mut errors), 0);
        range.event(Some(2));
        assert_eq!(drain(&range, &[false; 3], &mut errors), 3u64 << 32 | 1);
        assert_eq!(range.event(None), 0);
    }
    #[test] fn binding_preparation_does_not_publish_a_parent_or_finish() {
        let range = Range::new(2).unwrap();
        range.event(Some(0)); range.event(Some(1));
        assert_eq!(range.claim(), 1);
        assert_eq!(range.prepare(0, true, false), 1);
        assert_eq!(range.event(None), 0);
        assert_eq!(range.claim(), 0);
        assert_eq!(range.commit(0), 3u64 << 32);
        assert_eq!(range.commit(0), 0);
    }
    #[test] fn all_joins_precede_allocation_and_ordered_short_circuit() {
        let range = Range::new(3).unwrap();
        let command = |op: u64, row: u64| (op << 32) | row;
        assert_eq!(range.next(false), command(1, 0));
        assert_eq!(range.next(false), command(1, 1));
        assert_eq!(range.next(false), command(1, 2));
        assert_eq!(range.next(false), command(2, 0));
        assert_eq!(range.next(false), command(3, 0));
        assert_eq!(range.next(true), command(3, 1));
        assert_eq!(range.next(false), command(5, 0));
        assert_eq!(range.next(true), 0);
    }
    #[test] fn concurrent_ready_events_claim_and_publish_each_continuation_once() {
        let range = Arc::new(Range::new(32).unwrap());
        range.event(None);
        let threads: Vec<_> = (0..32).map(|index| {
            let range = range.clone();
            std::thread::spawn(move || {
                let mut finished = usize::from(range.event(Some(index)) != 0);
                loop {
                    let command = range.claim();
                    if command == 0 { break; }
                    let node = command as usize - 1;
                    assert_eq!(range.prepare(node, false, false), 0);
                    finished += usize::from(range.commit(node) != 0);
                }
                finished
            })
        }).collect();
        assert_eq!(threads.into_iter().map(|thread| thread.join().unwrap()).sum::<usize>(), 1);
        let state = range.state.lock().unwrap();
        assert_eq!(state.nodes.iter().filter(|node| matches!(node.publication, Publication::Published(false))).count(), 31);
        assert!(state.ready.is_empty());
    }

    #[test] fn fence_completion_selects_input_retirement_before_wakeup() {
        let range = Range::new_role(2, true).unwrap();
        range.event(None); range.event(Some(0)); range.event(Some(1));
        assert_eq!(range.claim(), 1);
        assert_eq!(range.prepare(0, false, false), 0);
        assert_eq!(range.commit(0), 2u64 << 32);
    }

}
