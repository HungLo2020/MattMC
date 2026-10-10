//! Server chunk-task membership and scheduling. Callable references stay at
//! the temporary Java boundary; this owner stores opaque CPU tokens only.
use std::collections::HashMap;
use std::hash::{BuildHasherDefault, Hasher};
mod ffi;
#[cfg(test)]
mod tests;
const NONE: u32 = u32::MAX;
#[derive(Default)]
struct PositionHash(u64);
impl Hasher for PositionHash {
    fn finish(&self) -> u64 {
        let mixed = self.0.wrapping_mul(0x9e3779b97f4a7c15);
        mixed ^ (mixed >> 32)
    }
    fn write(&mut self, _: &[u8]) {
        unreachable!("only packed u64 chunk positions")
    }
    fn write_u64(&mut self, value: u64) {
        self.0 = value;
    }
}
#[derive(Clone, Copy)]
struct TaskList {
    first: u32,
    last: u32,
    count: usize,
}
struct Task {
    token: u64,
    next: u32,
}
struct Tasks {
    slots: Vec<Task>,
    free: u32,
}
impl Tasks {
    fn next_id(&self) -> u64 {
        if self.free == NONE {
            self.slots.len() as u64 + 1
        } else {
            u64::from(self.free) + 1
        }
    }
    fn new() -> Self {
        Self {
            slots: Vec::new(),
            free: NONE,
        }
    }
    fn insert(&mut self, token: u64) -> TaskList {
        let index = if self.free != NONE {
            let index = self.free;
            self.free = self.slots[index as usize].next;
            self.slots[index as usize] = Task { token, next: NONE };
            index
        } else {
            assert!(self.slots.len() < NONE as usize);
            let index = self.slots.len() as u32;
            self.slots.push(Task { token, next: NONE });
            index
        };
        TaskList {
            first: index,
            last: index,
            count: 1,
        }
    }
    fn append(&mut self, destination: &mut TaskList, source: TaskList) {
        self.slots[destination.last as usize].next = source.first;
        destination.last = source.last;
        destination.count += source.count;
    }
    fn retire(&mut self, list: TaskList, output: &mut Vec<u64>) {
        output.reserve(list.count);
        let mut index = list.first;
        while index != NONE {
            let task = &mut self.slots[index as usize];
            output.push(task.token);
            let next = task.next;
            task.next = self.free;
            self.free = index;
            index = next;
        }
    }
}
struct Chunk {
    pos: u64,
    tasks: TaskList,
    previous: u32,
    next: u32,
}
struct Chunks {
    slots: Vec<Chunk>,
    free: u32,
}
impl Chunks {
    fn new() -> Self {
        Self {
            slots: Vec::new(),
            free: NONE,
        }
    }
    fn insert(&mut self, pos: u64, tasks: TaskList, previous: u32) -> u32 {
        let chunk = Chunk {
            pos,
            tasks,
            previous,
            next: NONE,
        };
        if self.free != NONE {
            let index = self.free;
            self.free = self.slots[index as usize].next;
            self.slots[index as usize] = chunk;
            index
        } else {
            assert!(self.slots.len() < NONE as usize);
            let index = self.slots.len() as u32;
            self.slots.push(chunk);
            index
        }
    }
}
struct Level {
    entries: HashMap<u64, u32, BuildHasherDefault<PositionHash>>,
    first: u32,
    last: u32,
}
impl Default for Level {
    fn default() -> Self {
        Self {
            entries: HashMap::default(),
            first: NONE,
            last: NONE,
        }
    }
}
impl Level {
    #[inline(always)]
    fn append(&mut self, pos: u64, source: TaskList, tasks: &mut Tasks, chunks: &mut Chunks) {
        use std::collections::hash_map::Entry;
        match self.entries.entry(pos) {
            Entry::Occupied(entry) => {
                tasks.append(&mut chunks.slots[*entry.get() as usize].tasks, source)
            }
            Entry::Vacant(entry) => {
                let index = chunks.insert(pos, source, self.last);
                if self.last != NONE {
                    chunks.slots[self.last as usize].next = index;
                } else {
                    self.first = index;
                }
                self.last = index;
                entry.insert(index);
            }
        }
    }
    #[inline(always)]
    fn remove(&mut self, pos: u64, chunks: &mut Chunks) -> Option<TaskList> {
        let index = self.entries.remove(&pos)?;
        let chunk = &chunks.slots[index as usize];
        let (previous, next, tasks) = (chunk.previous, chunk.next, chunk.tasks);
        if previous != NONE {
            chunks.slots[previous as usize].next = next;
        } else {
            self.first = next;
        }
        if next != NONE {
            chunks.slots[next as usize].previous = previous;
        } else {
            self.last = previous;
        }
        chunks.slots[index as usize].next = chunks.free;
        chunks.free = index;
        Some(tasks)
    }
}
pub(super) struct Queue {
    levels: Box<[Level]>,
    tasks: Tasks,
    chunks: Chunks,
    top: usize,
    pending: usize,
    // One completed boundary operation, borrowed until the next mutation.
    output: Vec<u64>,
    output_pos: u64,
}
impl Queue {
    fn new(count: usize) -> Option<Self> {
        if !(1..=4096).contains(&count) {
            return None;
        }
        Some(Self {
            levels: (0..count).map(|_| Level::default()).collect(),
            tasks: Tasks::new(),
            chunks: Chunks::new(),
            top: count,
            pending: 0,
            output: Vec::new(),
            output_pos: 0,
        })
    }
    fn advance(&mut self) {
        while self.top < self.levels.len() && self.levels[self.top].entries.is_empty() {
            self.top += 1;
        }
    }
    fn submit(&mut self, pos: u64, level: i32, token: u64) -> i32 {
        if level < 0 || level as usize >= self.levels.len() {
            return 2;
        }
        if token == 0 {
            return 4;
        }
        let list = self.tasks.insert(token);
        self.levels[level as usize].append(pos, list, &mut self.tasks, &mut self.chunks);
        self.pending += 1;
        self.top = self.top.min(level as usize);
        0
    }
    fn resort(&mut self, pos: u64, old: i32, new: i32) -> i32 {
        if old >= self.levels.len() as i32 {
            return 0;
        }
        if old < 0 {
            return 2;
        }
        let tasks = self.levels[old as usize].remove(pos, &mut self.chunks);
        if self.top == old as usize {
            self.advance();
        }
        let Some(tasks) = tasks else {
            return 0;
        };
        if new < 0 || new as usize >= self.levels.len() {
            // Original source removal precedes its destination indexing failure.
            self.pending -= tasks.count;
            self.tasks.retire(tasks, &mut self.output);
            return 3;
        }
        self.levels[new as usize].append(pos, tasks, &mut self.tasks, &mut self.chunks);
        self.top = self.top.min(new as usize);
        0
    }
    fn release(&mut self, pos: u64, clear: bool) {
        if clear {
            for level in &mut self.levels {
                if let Some(tasks) = level.remove(pos, &mut self.chunks) {
                    self.pending -= tasks.count;
                    self.tasks.retire(tasks, &mut self.output);
                }
            }
        }
        self.advance();
    }
    fn pop(&mut self) -> i32 {
        if self.top == self.levels.len() {
            return 1;
        }
        let level = &mut self.levels[self.top];
        self.output_pos = self.chunks.slots[level.first as usize].pos;
        let tasks = level.remove(self.output_pos, &mut self.chunks).unwrap();
        self.pending -= tasks.count;
        self.tasks.retire(tasks, &mut self.output);
        self.advance();
        0
    }
    fn change(&mut self, action: i32, pos: u64, a: i32, b: u64) -> i32 {
        self.output.clear();
        self.output_pos = 0;
        match action {
            0 => self.submit(pos, a, b),
            1 => self.resort(pos, a, b as i32),
            2 => {
                self.release(pos, a != 0);
                0
            }
            3 => self.pop(),
            _ => 4,
        }
    }
}
