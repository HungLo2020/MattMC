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

    pub fn enqueue_computed(
        &mut self,
        value: u64,
        current: i32,
        computed: i32,
    ) -> Result<(), Error> {
        self.enqueue(value, self.priority(current, computed))
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
