use super::ordered_set::OrderedSet;

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    Index(i32),
    Empty,
    Allocation,
}

pub(crate) struct Queue {
    buckets: Vec<OrderedSet>,
    pub first: i32,
}

impl Queue {
    pub fn new(levels: i32, expected: i32) -> Result<Self, Error> {
        if levels < 0 || (levels > 0 && expected < 0) {
            return Err(Error::Allocation);
        }
        let mut buckets = Vec::new();
        buckets
            .try_reserve_exact(levels as usize)
            .map_err(|_| Error::Allocation)?;
        for _ in 0..levels {
            buckets.push(OrderedSet::new(expected as usize).map_err(|_| Error::Allocation)?);
        }
        Ok(Self {
            buckets,
            first: levels,
        })
    }

    fn bucket(&mut self, level: i32) -> Result<&mut OrderedSet, Error> {
        self.buckets
            .get_mut(level as usize)
            .ok_or(Error::Index(level))
    }

    pub fn bounded(&self) -> bool {
        self.buckets.len() <= 256
    }

    pub fn enqueue_without_growth(&mut self, value: u64, level: i32) -> Option<Result<(), Error>> {
        let bucket = match self.bucket(level) {
            Ok(bucket) => bucket,
            Err(error) => return Some(Err(error)),
        };
        if !bucket.insert_without_growth(value) {
            return None;
        }
        if self.first > level {
            self.first = level;
        }
        Some(Ok(()))
    }

    // Preserve the original upper-bound semantics, including state on a failed scan.
    fn advance(&mut self, bound: i32) -> Result<(), Error> {
        let previous = self.first;
        self.first = bound;
        for level in previous + 1..bound {
            if !self.bucket(level)?.is_empty() {
                self.first = level;
                break;
            }
        }
        Ok(())
    }

    pub fn enqueue(&mut self, value: u64, level: i32) -> Result<(), Error> {
        self.bucket(level)?
            .insert(value)
            .map_err(|_| Error::Allocation)?;
        if self.first > level {
            self.first = level;
        }
        Ok(())
    }

    pub fn dequeue(&mut self, value: u64, level: i32, bound: i32) -> Result<(), Error> {
        let bucket = self.bucket(level)?;
        bucket.remove(value).map_err(|_| Error::Allocation)?;
        if bucket.is_empty() && self.first == level {
            self.advance(bound)?;
        }
        Ok(())
    }

    pub fn dequeue_bounded(
        &mut self,
        value: u64,
        level: i32,
        bound: i32,
    ) -> Option<Result<(), Error>> {
        let bucket = match self.bucket(level) {
            Ok(bucket) => bucket,
            Err(error) => return Some(Err(error)),
        };
        if !bucket.remove_bounded(value) {
            return None;
        }
        if bucket.is_empty() && self.first == level {
            return Some(self.advance(bound));
        }
        Some(Ok(()))
    }

    // This is the graph's original priority policy, without coordinate/state
    // approximations. 255 is its NO_COMPUTED_LEVEL sentinel, not a queue level.
    pub fn priority(&self, current: i32, computed: i32) -> i32 {
        current.min(computed).min(self.buckets.len() as i32 - 1)
    }

    pub fn cancel_computed(
        &mut self,
        value: u64,
        current: i32,
        computed: i32,
    ) -> Result<(), Error> {
        self.dequeue(
            value,
            self.priority(current, computed),
            self.buckets.len() as i32,
        )
    }

    pub fn cancel_computed_bounded(
        &mut self,
        value: u64,
        current: i32,
        computed: i32,
    ) -> Option<Result<(), Error>> {
        self.dequeue_bounded(
            value,
            self.priority(current, computed),
            self.buckets.len() as i32,
        )
    }

    pub fn enqueue_computed(
        &mut self,
        value: u64,
        current: i32,
        computed: i32,
    ) -> Result<(), Error> {
        self.enqueue(value, self.priority(current, computed))
    }

    pub fn enqueue_computed_bounded(
        &mut self,
        value: u64,
        current: i32,
        computed: i32,
    ) -> Option<Result<(), Error>> {
        self.enqueue_without_growth(value, self.priority(current, computed))
    }

    pub fn reschedule(
        &mut self,
        value: u64,
        current: i32,
        previous: i32,
        next: i32,
    ) -> Result<(), Error> {
        let destination = self.priority(current, next);
        let old = self.priority(current, previous);
        if previous != 255 && old != destination {
            // Preserve removal/scan before insertion, including partial state if
            // either operation fails. Java publishes its computed map afterward.
            self.dequeue(value, old, destination)?;
        }
        self.enqueue(value, destination)
    }

    pub fn reschedule_bounded(
        &mut self,
        value: u64,
        current: i32,
        previous: i32,
        next: i32,
    ) -> Option<Result<(), Error>> {
        let destination = self.priority(current, next);
        let old = self.priority(current, previous);
        if previous == 255 || old == destination {
            return self.enqueue_without_growth(value, destination);
        }
        // Preflight both buckets before removing anything. A rejected bounded
        // operation must retry from the original state on the ordinary path.
        let old_bucket = match self.bucket(old) {
            Ok(bucket) => bucket,
            Err(error) => return Some(Err(error)),
        };
        let old_plan = old_bucket.prepare_remove(value)?;
        let destination_bucket = match self.bucket(destination) {
            Ok(bucket) => bucket,
            Err(_) => return None, // ordinary removal must precede this failure
        };
        let destination_plan = destination_bucket.prepare_insert(value)?;
        // Both plans are ready, the priorities are distinct, and no bucket has
        // changed. Commit uses the validated slots without probing again. There
        // is no retry path after the first mutation.
        let old_bucket = self.bucket(old).unwrap();
        old_bucket.commit_remove(old_plan);
        if old_bucket.is_empty() && self.first == old {
            if let Err(error) = self.advance(destination) {
                return Some(Err(error));
            }
        }
        self.bucket(destination)
            .unwrap()
            .commit_insert(value, destination_plan);
        if self.first > destination {
            self.first = destination;
        }
        Some(Ok(()))
    }

    pub fn pop_bounded(&mut self) -> Option<Result<u64, Error>> {
        let bucket = match self.bucket(self.first) {
            Ok(bucket) => bucket,
            Err(error) => return Some(Err(error)),
        };
        let value = match bucket.pop_bounded()? {
            Some(value) => value,
            None => return Some(Err(Error::Empty)),
        };
        if bucket.is_empty() {
            if let Err(error) = self.advance(self.buckets.len() as i32) {
                return Some(Err(error));
            }
        }
        Some(Ok(value))
    }

    pub fn pop(&mut self) -> Result<u64, Error> {
        let bucket = self.bucket(self.first)?;
        let value = bucket
            .pop()
            .map_err(|_| Error::Allocation)?
            .ok_or(Error::Empty)?;
        if bucket.is_empty() {
            self.advance(self.buckets.len() as i32)?;
        }
        Ok(value)
    }
}
