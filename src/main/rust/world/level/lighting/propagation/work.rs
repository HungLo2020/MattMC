//! Retained propagation inputs and a non-copying transaction journal.
use std::collections::VecDeque;
use std::sync::{
    atomic::{AtomicI32, Ordering},
    Arc, Mutex, Weak,
};

/// Actual retained level observation. Bits0/1 are block/sky pending; bits2/3
/// are complete idle-update eligibility. Engine/queue owners publish their bits.
#[repr(C)]
pub(in crate::world::level::lighting) struct LevelProjection {
    pub(in crate::world::level::lighting) flags: AtomicI32,
}
impl Default for LevelProjection {
    fn default()->Self{Self{flags:AtomicI32::new(12)}}
}
struct LevelFollower { owner:Weak<LevelProjection>, which:u32 }
#[derive(Default)]
pub(in crate::world::level::lighting) struct LevelFollowers { levels:Mutex<Vec<LevelFollower>> }
impl LevelFollowers {
    pub(in crate::world::level::lighting) fn bind_level(&self,projection:&CoordinatorProjection,owner:&Arc<LevelProjection>,which:u32){
        let mut levels=self.levels.lock().unwrap();
        levels.retain(|level|level.owner.strong_count()!=0);
        levels.push(LevelFollower{owner:Arc::downgrade(owner),which});
        Self::publish_locked(projection,&mut levels);
    }
    fn publish_locked(projection:&CoordinatorProjection,levels:&mut Vec<LevelFollower>){
        let pending=projection.pending.load(Ordering::Acquire)!=0;
        let idle=projection.idle_update.load(Ordering::Acquire)!=0;
        levels.retain(|level|{
            let Some(owner)=level.owner.upgrade()else{return false;};
            let mask=(1|4)<<level.which;
            let value=(i32::from(pending)|(i32::from(idle)<<2))<<level.which;
            let _=owner.flags.fetch_update(Ordering::AcqRel,Ordering::Acquire,|old|Some((old&!mask)|value));
            true
        });
    }
    fn publish(&self,projection:&CoordinatorProjection){Self::publish_locked(projection,&mut self.levels.lock().unwrap());}
}

/// The existing sixteen-byte borrowed engine projection remains unchanged.
#[repr(C)]
#[derive(Default)]
pub(in crate::world::level::lighting) struct CoordinatorProjection {
    pub(in crate::world::level::lighting) nodes: AtomicI32,
    pub(in crate::world::level::lighting) idle_update: AtomicI32,
    nonqueue_pending: AtomicI32,
    pub(in crate::world::level::lighting) pending: AtomicI32,
}
struct ProjectionBinding { projection:Weak<CoordinatorProjection>, levels:Arc<LevelFollowers> }

pub(in crate::world::level::lighting) struct Queues {
    pub(in crate::world::level::lighting) data: Mutex<[VecDeque<i64>; 2]>,
    pub(in crate::world::level::lighting) counts: [AtomicI32; 2],
    projections: Mutex<Vec<ProjectionBinding>>,
    projection_count: AtomicI32,
}
impl Default for Queues {
    fn default() -> Self {
        Self { data:Mutex::new(std::array::from_fn(|_|VecDeque::new())),
            counts:std::array::from_fn(|_|AtomicI32::new(0)),projections:Mutex::new(Vec::new()),projection_count:AtomicI32::new(0) }
    }
}
impl Queues {
    pub(in crate::world::level::lighting) fn bind_projection(&self, projection:&Arc<CoordinatorProjection>)->Arc<LevelFollowers>{
        let queues=self.data.lock().unwrap();let mut projections=self.projections.lock().unwrap();
        projections.retain(|p|p.projection.strong_count()!=0);
        let levels=Arc::new(LevelFollowers::default());
        projections.push(ProjectionBinding{projection:Arc::downgrade(projection),levels:Arc::clone(&levels)});
        self.projection_count.store(projections.len() as i32,Ordering::Release);
        projection.pending.store(i32::from(queues.iter().any(|q|!q.is_empty())),Ordering::Release);
        levels.publish(projection);levels
    }
    /// Lock order is State -> queue -> projections -> level observers. A
    /// producer's invalidation cannot be overwritten by a stale idle scan.
    pub(in crate::world::level::lighting) fn publish_coordinator(&self,projection:&CoordinatorProjection,nonqueue_pending:bool,idle:bool){
        let queues=self.data.lock().unwrap();let has_queue=queues.iter().any(|q|!q.is_empty());
        projection.nonqueue_pending.store(i32::from(nonqueue_pending),Ordering::Release);
        projection.pending.store(i32::from(nonqueue_pending||has_queue),Ordering::Release);
        projection.idle_update.store(i32::from(idle&&!has_queue),Ordering::Release);
        for binding in self.projections.lock().unwrap().iter(){
            if binding.projection.as_ptr()==projection as *const _{binding.levels.publish(projection);}
        }
    }
    pub(in crate::world::level::lighting) fn publish(&self,queues:&[VecDeque<i64>;2]){
        for(target,queue)in self.counts.iter().zip(queues){target.store(queue.len() as i32,Ordering::Release);}
        if self.projection_count.load(Ordering::Acquire)!=0{
            let has_queue=queues.iter().any(|q|!q.is_empty());let mut projections=self.projections.lock().unwrap();
            projections.retain(|binding|{
                let Some(projection)=binding.projection.upgrade()else{return false;};
                let pending=has_queue||projection.nonqueue_pending.load(Ordering::Acquire)!=0;
                projection.pending.store(i32::from(pending),Ordering::Release);
                if pending{projection.idle_update.store(0,Ordering::Release);}
                binding.levels.publish(&projection);true
            });
            self.projection_count.store(projections.len() as i32,Ordering::Release);
        }
    }
    pub(in crate::world::level::lighting) fn take(&self) -> [VecDeque<i64>; 2] {
        let mut queues = self.data.lock().unwrap();
        let inputs = std::mem::take(&mut *queues);
        self.publish(&queues);
        inputs
    }
    /// Callback additions precede restored original inputs, like Java's refill.
    pub(in crate::world::level::lighting) fn finish(&self, mut inputs: [VecDeque<i64>; 2], restore: bool) {
        let mut queues = self.data.lock().unwrap();
        for (pending, original) in queues.iter_mut().zip(&mut inputs) {
            if restore {
                pending.append(original);
            } else {
                original.clear();
                if pending.is_empty() && original.capacity() > pending.capacity() {
                    std::mem::swap(pending, original);
                }
            }
        }
        self.publish(&queues);
    }
}

/// Original inputs stay intact until admission succeeds. Only newly generated
/// propagation work is dequeued destructively; no input clone is needed.
#[derive(Default)]
pub(in crate::world::level::lighting) struct WorkQueue {
    initial: VecDeque<i64>,
    cursor: usize,
    generated: VecDeque<(i64, u64)>,
}
impl WorkQueue {
    pub(in crate::world::level::lighting) fn clear(&mut self) {
        self.initial.clear();
        self.cursor = 0;
        self.generated.clear();
    }
    pub(in crate::world::level::lighting) fn adopt(&mut self, initial: VecDeque<i64>) {
        self.initial = initial;
        self.cursor = 0;
    }
    pub(in crate::world::level::lighting) fn push_back(&mut self, value: (i64, u64)) {
        self.generated.push_back(value);
    }
    pub(in crate::world::level::lighting) fn pop_front(&mut self) -> Option<(i64, u64)> {
        if self.cursor < self.initial.len() {
            let pair = (
                self.initial[self.cursor],
                self.initial[self.cursor + 1] as u64,
            );
            self.cursor += 2;
            Some(pair)
        } else {
            self.generated.pop_front()
        }
    }
    pub(in crate::world::level::lighting) fn journal(&mut self) -> VecDeque<i64> {
        self.cursor = 0;
        std::mem::take(&mut self.initial)
    }
}

/// Independent queue ownership lets callbacks enqueue without aliasing the
/// excluded propagation engine or holding a native lock across an upcall.
pub(in crate::world::level::lighting) unsafe fn retain(pointer: *const Queues) -> Arc<Queues> {
    Arc::increment_strong_count(pointer);
    Arc::from_raw(pointer)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn original_inputs_precede_generated_work_and_survive_partial_consumption() {
        let mut queue = WorkQueue::default();
        queue.adopt(VecDeque::from([1, 11, 2, 22]));
        queue.push_back((3, 33));
        assert_eq!(queue.pop_front(), Some((1, 11)));
        assert_eq!(queue.journal(), VecDeque::from([1, 11, 2, 22]));
        queue.clear();
        queue.adopt(VecDeque::from([1, 11, 2, 22]));
        queue.push_back((3, 33));
        assert_eq!(queue.pop_front(), Some((1, 11)));
        assert_eq!(queue.pop_front(), Some((2, 22)));
        assert_eq!(queue.pop_front(), Some((3, 33)));
        assert_eq!(queue.pop_front(), None);
    }
    #[test]
    fn rejected_inputs_append_after_callback_additions_and_success_recycles_capacity() {
        let owner = Queues::default();
        owner.data.lock().unwrap()[0].extend([1, 11, 2, 22]);
        let inputs = owner.take();
        owner.data.lock().unwrap()[0].extend([3, 33]);
        owner.finish(inputs, true);
        assert_eq!(
            owner.data.lock().unwrap()[0],
            VecDeque::from([3, 33, 1, 11, 2, 22])
        );
        let inputs = owner.take();
        let capacity = inputs[0].capacity();
        owner.finish(inputs, false);
        assert_eq!(owner.counts[0].load(Ordering::Acquire), 0);
        assert!(owner.data.lock().unwrap()[0].capacity() >= capacity);
    }
}
