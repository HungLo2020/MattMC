//! Native-owned published level views of distance fields: the simulation
//! tracker's `Long2ByteOpenHashMap chunks` and the natural-spawn counter's.
//! Each is an exact fastutil map port (table layout, growth and shrink, so
//! iteration order is the original's), filled from its graph's ordered
//! `setLevel` changes without a Java replay. A cursor is fastutil's
//! `MapIterator` over the live table: Java callbacks between steps may change
//! the view exactly as they could change the Java map. Every call holds the
//! view's lock only while it runs; a cursor keeps its view alive.
use std::sync::{Arc, Mutex, MutexGuard};

use super::ticket_storage::map::{Cursor, ListMap, NONE};
use super::ticket_storage::Fault;

pub(crate) struct LevelView {
    map: ListMap,
    /// `defaultReturnValue`: the level of an absent chunk.
    absent: i32,
    /// Levels at or above this are removed instead of stored.
    remove_from: i32,
}

impl LevelView {
    pub(crate) fn new(absent: i32, remove_from: i32) -> Result<Self, Fault> {
        Ok(Self { map: ListMap::with_expected(16)?, absent, remove_from })
    }

    /// The tracker's `setLevel(pos, level)`: `remove` above the range, else `put`.
    pub(crate) fn set_level(&mut self, key: i64, level: i32) -> Result<(), Fault> {
        if level >= self.remove_from {
            self.map.remove(key)?;
        } else {
            // `(byte) level`, read back as a byte.
            self.map.put(key, level as u8 as u32)?;
        }
        Ok(())
    }

    pub(crate) fn apply(&mut self, changes: &[(i64, i32)]) -> Result<(), Fault> {
        changes.iter().try_for_each(|&(key, level)| self.set_level(key, level))
    }

    fn level(&self, value: u32) -> i32 {
        if value == NONE {
            self.absent
        } else {
            value as u8 as i8 as i32
        }
    }

    /// `chunks.get(key)`.
    pub(crate) fn get(&self, key: i64) -> i32 {
        self.level(self.map.get(key))
    }

    pub(crate) fn len(&self) -> usize {
        self.map.len()
    }

    /// Entries in iteration order (tests).
    pub(crate) fn ordered(&self) -> Vec<(i64, i32)> {
        self.map.ordered().into_iter().map(|(k, v)| (k, self.level(v))).collect()
    }
}

pub(crate) struct SharedView(Mutex<LevelView>);

impl SharedView {
    pub(crate) fn new(view: LevelView) -> Self {
        Self(Mutex::new(view))
    }

    pub(crate) fn lock(&self) -> MutexGuard<'_, LevelView> {
        // Nothing panics while holding it, so a poisoned lock still holds whole state.
        self.0.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
    }
}

/// What a cursor step found.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Step {
    /// The entry's key and level.
    Entry(i64, i32),
    End,
    /// `key[pos]` past the table after the view shrank mid-walk: Java's
    /// `ArrayIndexOutOfBoundsException` (index, length).
    OutOfBounds(i32, usize),
    /// The walk ran below slot 0 (the view grew mid-walk) and read the
    /// iterator's never-created wrapped-key list: Java's `NullPointerException`.
    NullWrapped,
    /// It read past the wrapped-key list: `LongArrayList.getLong`'s
    /// `IndexOutOfBoundsException` (index, list size).
    WrappedIndex(i32, usize),
}

/// What an iterator `remove()` did.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Removal {
    Removed,
    /// No entry to remove: Java's `IllegalStateException`.
    IllegalState,
    /// The removed slot lies past a table that shrank under the walk.
    OutOfBounds(i32, usize),
    NullWrapped,
    WrappedIndex(i32, usize),
}

/// A `fastIterable` / `keySet().iterator()` walk; holds its view alive.
pub(crate) struct ViewCursor {
    view: Arc<SharedView>,
    cursor: Cursor,
}

impl ViewCursor {
    pub(crate) fn new(view: Arc<SharedView>) -> Self {
        let cursor = view.lock().map.cursor();
        Self { view, cursor }
    }

    /// The next entry whose level is at most `max_level`. Entries above it are
    /// passed exactly as the Java loop's filter passes them (nothing runs in between).
    pub(crate) fn next(&mut self, max_level: i32) -> Result<Step, Fault> {
        let view = self.view.lock();
        while self.cursor.has_next() {
            let index = match self.cursor.next_entry(&view.map) {
                Ok(index) => index,
                Err(Fault::ArrayIndex) => return Ok(Step::OutOfBounds(self.out_of_bounds_index(), view.map.table_len())),
                Err(Fault::NullPointer) => return Ok(Step::NullWrapped),
                Err(Fault::IndexOutOfBounds) => return Ok(Step::WrappedIndex(self.wrapped_index(), self.wrapped_len())),
                Err(error) => return Err(error),
            };
            let key = view.map.key_at(index)?;
            let level = view.level(view.map.value_at(index)?);
            if level <= max_level {
                return Ok(Step::Entry(key, level));
            }
        }
        Ok(Step::End)
    }

    /// The position `nextEntry` read past the table.
    fn out_of_bounds_index(&self) -> i32 {
        self.cursor.mark().position()
    }

    /// The wrapped-list index the walk read (`-pos - 1`).
    fn wrapped_index(&self) -> i32 {
        -self.cursor.mark().position() - 1
    }

    fn wrapped_len(&self) -> usize {
        self.cursor.wrapped().as_ref().map_or(0, Vec::len)
    }

    /// The iterator's `c` (entries left), including any step that threw.
    pub(crate) fn left(&self) -> usize {
        self.cursor.mark().left()
    }

    /// `remove()` of the entry last returned (fastutil `MapIterator.remove`):
    /// the zero key, a backward shift (which may move a key into the wrapped
    /// list), or `remove(key)` for a wrapped key.
    pub(crate) fn remove(&mut self) -> Result<Removal, Fault> {
        let mut view = self.view.lock();
        match self.cursor.remove(&mut view.map) {
            Ok(_) => Ok(Removal::Removed),
            Err(Fault::IllegalState) => Ok(Removal::IllegalState),
            // `shiftKeys(last)` writes `key[last]` first: past a shrunk table that
            // throws for `last`, before anything changes.
            Err(Fault::ArrayIndex) => Ok(Removal::OutOfBounds(self.cursor.mark().last(), view.map.table_len())),
            // A wrapped-path removal reads the wrapped list as `nextEntry` does.
            Err(Fault::NullPointer) => Ok(Removal::NullWrapped),
            Err(Fault::IndexOutOfBounds) => Ok(Removal::WrappedIndex(self.wrapped_index(), self.wrapped_len())),
            Err(error) => Err(error),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn shared(entries: &[(i64, i32)]) -> Arc<SharedView> {
        let mut view = LevelView::new(33, 33).unwrap();
        view.apply(entries).unwrap();
        Arc::new(SharedView::new(view))
    }

    fn walk(cursor: &mut ViewCursor, max: i32) -> Vec<Step> {
        let mut out = Vec::new();
        loop {
            match cursor.next(max).unwrap() {
                Step::End => return out,
                step @ (Step::OutOfBounds(..) | Step::NullWrapped) => {
                    out.push(step);
                    return out;
                }
                step => out.push(step),
            }
        }
    }

    #[test]
    fn levels_store_remove_and_default() {
        let view = shared(&[(5, 31), (0, 20), (7, 33), (5, 32), (9, 40)]);
        let view = view.lock();
        assert_eq!(view.get(5), 32);
        assert_eq!(view.get(0), 20);
        assert_eq!(view.get(7), 33);
        assert_eq!(view.get(9), 33);
        assert_eq!(view.len(), 2);
    }

    #[test]
    fn cursor_walks_in_table_order_zero_key_first_and_filters() {
        let entries: Vec<(i64, i32)> = (0..40).map(|i| (i * 1_000_003 - 20, 28 + (i % 6) as i32)).chain([(0, 30)]).collect();
        let view = shared(&entries);
        let order = view.lock().ordered();
        assert_eq!(order[0].0, 0);
        let all: Vec<Step> = order.iter().map(|&(k, l)| Step::Entry(k, l)).collect();
        assert_eq!(walk(&mut ViewCursor::new(view.clone()), i32::MAX), all);
        let ticking: Vec<Step> = order.iter().filter(|e| e.1 <= 31).map(|&(k, l)| Step::Entry(k, l)).collect();
        assert_eq!(walk(&mut ViewCursor::new(view), 31), ticking);
    }

    #[test]
    fn mutation_during_a_walk_reads_the_live_table() {
        let view = shared(&(1..=12).map(|i| (i, 30)).collect::<Vec<_>>());
        let mut cursor = ViewCursor::new(view.clone());
        let first = cursor.next(i32::MAX).unwrap();
        // Growth mid-walk: the cursor keeps its count and position over the new table.
        view.lock().apply(&(100..140).map(|i| (i, 30)).collect::<Vec<_>>()).unwrap();
        // The count is fixed at creation; walking the larger table from the old
        // top slot can run below slot 0 into the null wrapped list, as Java does.
        let rest = walk(&mut cursor, i32::MAX);
        assert!(rest.len() == 11 || rest.last() == Some(&Step::NullWrapped), "{first:?} {rest:?}");
        // Shrink mid-walk: a position past the halved table is Java's AIOOBE.
        let view = shared(&(1..=200).map(|i| (i * 7919, 30)).collect::<Vec<_>>());
        let mut cursor = ViewCursor::new(view.clone());
        cursor.next(i32::MAX).unwrap();
        view.lock().apply(&(2..=200).map(|i| (i * 7919, 33)).collect::<Vec<_>>()).unwrap();
        assert!(walk(&mut cursor, i32::MAX).iter().any(|step| matches!(step, Step::OutOfBounds(..))));
    }

    #[test]
    fn cursor_removal_follows_map_iterator_remove() {
        let view = shared(&[(0, 30), (1, 31), (2, 32)]);
        let mut cursor = ViewCursor::new(view.clone());
        // Nothing returned yet: IllegalStateException.
        assert_eq!(cursor.remove().unwrap(), Removal::IllegalState);
        let Step::Entry(first, _) = cursor.next(i32::MAX).unwrap() else { panic!() };
        assert_eq!(first, 0, "the zero key comes first");
        assert_eq!(cursor.remove().unwrap(), Removal::Removed);
        // Removing twice is IllegalStateException; the count is unchanged by removal.
        assert_eq!(cursor.remove().unwrap(), Removal::IllegalState);
        assert_eq!(cursor.left(), 2);
        assert_eq!(walk(&mut cursor, i32::MAX).len(), 2);
        assert_eq!(view.lock().len(), 2);
        assert_eq!(view.lock().get(0), 33);
    }

    #[test]
    fn a_cursor_keeps_its_view_alive() {
        let view = shared(&[(3, 30), (4, 31)]);
        let mut cursor = ViewCursor::new(view.clone());
        drop(view);
        assert_eq!(walk(&mut cursor, i32::MAX).len(), 2);
    }
}
