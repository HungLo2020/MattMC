//! Threaded lighting's live task membership and PRE/propagation/POST policy.
//! Every operation ends before a Java effect; invocation cursors are independent.
use std::collections::VecDeque;
mod ffi;
#[cfg(test)]
mod tests;

const BATCH: usize = 1000;
#[derive(Clone, Copy)]
struct Task {
    row: u64,
    post: bool,
}
#[derive(Clone, Copy)]
enum Phase {
    Pre,
    Post,
}
#[derive(Clone, Copy)]
struct Cursor {
    limit: usize,
    phase: Phase,
    position: usize,
    visited: usize,
    waiting: bool,
}
#[derive(Debug, PartialEq)]
enum Error {
    Cursor,
    RemoveEmpty,
    Row,
}
#[derive(Debug, PartialEq)]
enum Action {
    Done,
    Propagate,
    Callable(u64),
}
#[derive(Default)]
struct Queue {
    tasks: VecDeque<Task>,
    rows: u64,
    free_rows: Vec<u64>,
    cursors: Vec<Option<Cursor>>,
    free_cursors: Vec<usize>,
    retired: Vec<u64>,
}
impl Queue {
    fn next_row(&self) -> u64 {
        self.free_rows.last().copied().unwrap_or(self.rows + 1)
    }
    fn append(&mut self, row: u64, post: bool) -> Result<bool, Error> {
        if row != self.next_row() {
            return Err(Error::Row);
        }
        if self.free_rows.pop().is_none() {
            self.rows += 1;
        }
        self.tasks.push_back(Task { row, post });
        Ok(self.tasks.len() >= BATCH)
    }
    fn begin(&mut self) -> u64 {
        let cursor = Cursor {
            limit: self.tasks.len().min(BATCH),
            phase: Phase::Pre,
            position: 0,
            visited: 0,
            waiting: false,
        };
        if let Some(index) = self.free_cursors.pop() {
            self.cursors[index] = Some(cursor);
            index as u64 + 1
        } else {
            self.cursors.push(Some(cursor));
            self.cursors.len() as u64
        }
    }
    fn end(&mut self, id: u64) -> Result<(), Error> {
        let index = id.checked_sub(1).ok_or(Error::Cursor)? as usize;
        let cursor = self.cursors.get_mut(index).ok_or(Error::Cursor)?;
        if cursor.take().is_none() {
            return Err(Error::Cursor);
        }
        self.free_cursors.push(index);
        Ok(())
    }
    fn remove_front(&mut self) -> Result<(), Error> {
        // ObjectArrayList's post-pass iterator always removes index zero.
        // A nested pass may already have removed it: preserve that failure.
        let task = self.tasks.pop_front().ok_or(Error::RemoveEmpty)?;
        self.free_rows.push(task.row);
        self.retired.push(task.row);
        Ok(())
    }
    fn advance(&mut self, c: &mut Cursor) -> Result<Action, Error> {
        match c.phase {
            Phase::Pre => {
                if c.waiting {
                    c.visited += 1;
                    c.waiting = false;
                }
                while c.position < self.tasks.len() && c.visited < c.limit {
                    let task = self.tasks[c.position];
                    c.position += 1;
                    if !task.post {
                        c.waiting = true;
                        return Ok(Action::Callable(task.row));
                    }
                    c.visited += 1;
                }
                // The original iterator.back(j) resets its cursor to zero here,
                // including after nested mutations. Do not snapshot its tasks.
                c.phase = Phase::Post;
                c.position = 0;
                c.visited = 0;
                c.waiting = false;
                Ok(Action::Propagate)
            }
            Phase::Post => {
                if c.waiting {
                    self.remove_front()?;
                    c.visited += 1;
                    c.waiting = false;
                }
                while !self.tasks.is_empty() && c.visited < c.limit {
                    let task = self.tasks[0];
                    if task.post {
                        c.waiting = true;
                        return Ok(Action::Callable(task.row));
                    }
                    self.remove_front()?;
                    c.visited += 1;
                }
                Ok(Action::Done)
            }
        }
    }
    fn next(&mut self, id: u64) -> Result<Action, Error> {
        let index = id.checked_sub(1).ok_or(Error::Cursor)? as usize;
        let mut cursor = self
            .cursors
            .get_mut(index)
            .and_then(Option::take)
            .ok_or(Error::Cursor)?;
        let result = self.advance(&mut cursor);
        self.cursors[index] = Some(cursor);
        result
    }
    fn allocation_bytes(&self) -> usize {
        self.tasks.capacity() * std::mem::size_of::<Task>()
            + self.free_rows.capacity() * 8
            + self.cursors.capacity() * std::mem::size_of::<Option<Cursor>>()
            + self.free_cursors.capacity() * std::mem::size_of::<usize>()
            + self.retired.capacity() * 8
    }
}
