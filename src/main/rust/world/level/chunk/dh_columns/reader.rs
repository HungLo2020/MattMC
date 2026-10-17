//! Scoped conversion reader: one Rust lease at a time, without materializing
//! Java list identities for primary columns. No borrow survives an ABI call.
use super::{mutable::{Column, Lease}, source::Source};

pub(crate) struct Reader { column: Option<Lease> }

impl Reader {
    pub(crate) fn new() -> Self { Self { column: None } }
    /// -1: inconsistent slot; 0: missing; 1: selected (including empty).
    pub(crate) unsafe fn select(&mut self, source: &Source, index: usize) -> i32 {
        self.column = None;
        if !source.present(index) { return 0; }
        let owner = source.owner(index);
        if !owner.is_null() {
            self.column = Some(Lease::retain(owner));
            return 1;
        }
        if source.column(index).is_some() { 1 } else { -1 }
    }
    pub(crate) fn size(&self) -> i32 {
        self.column.as_ref().map_or(0, |lease| unsafe {
            (*(lease.identity() as *const Column)).logical_len() as i32
        })
    }
    pub(crate) fn point(&self, index: i32) -> Option<u64> {
        if index < 0 { return None; }
        let lease = self.column.as_ref()?;
        let column = unsafe { &*(lease.identity() as *const Column) };
        if column.invalid_size() { return None; }
        column.points().get(index as usize).copied()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::source::*;
    #[test]
    fn selection_retains_only_current_column_and_reads_its_live_extent() {
        unsafe {
            let source = mattmc_dh_source_create();
            (*source).adopt(7, Box::new(Column::new(vec![11, 22])));
            let mut reader = Reader::new();
            assert_eq!(reader.select(&*source, 7), 1);
            let column = (*source).owner(7);
            Column::points_mut(column).push(33);
            assert_eq!(reader.size(), 3);
            assert_eq!(reader.point(2), Some(33));
            // Replacing/closing the source cannot invalidate a selected lease.
            (*source).adopt(7, Box::new(Column::new(vec![44])));
            mattmc_dh_source_release(source);
            assert_eq!(reader.point(0), Some(11));
            assert_eq!(reader.point(3), None);
        }
    }
    #[test]
    fn selection_distinguishes_missing_custom_empty_and_invalid_logical_sizes() {
        unsafe {
            let source = mattmc_dh_source_create();
            let mut reader = Reader::new();
            assert_eq!(reader.select(&*source, 0), 1);
            assert_eq!(reader.size(), 0);
            assert_eq!(mattmc_dh_source_set(source, 1, 0, std::ptr::null_mut()), 1);
            assert_eq!(reader.select(&*source, 1), 0);
            let column = Box::new(Column::new(vec![])); column.negative_size(-3);
            (*source).adopt(3, column);
            assert_eq!(reader.select(&*source, 3), 1);
            assert_eq!(reader.size(), -3);
            assert_eq!(reader.point(0), None);
            mattmc_dh_source_release(source);
        }
    }
}
