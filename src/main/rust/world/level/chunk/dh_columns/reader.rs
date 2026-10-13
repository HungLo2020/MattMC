//! Scoped conversion reader: one Rust lease at a time, without materializing
//! Java list identities for primary columns. No borrow survives an ABI call.
use super::{mutable::{Column, Lease}, source::Source};

pub(crate) struct Reader { column: Option<Lease> }

impl Reader {
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

#[no_mangle]
pub extern "C" fn mattmc_dh_reader_create() -> *mut Reader {
    Box::into_raw(Box::new(Reader { column: None }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_reader_release(reader: *mut Reader) {
    if !reader.is_null() { drop(Box::from_raw(reader)); }
}
/// -1: retain compatibility access; 0: missing; 1: selected (including empty).
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_reader_select(reader: *mut Reader, source: *const Source, index: i32) -> i32 {
    let (Some(reader), Some(source)) = (reader.as_mut(), source.as_ref()) else { return -1; };
    if !(0..4096).contains(&index) { return -1; }
    reader.column = None;
    let index = index as usize;
    if !source.present(index) { return 0; }
    let owner = source.owner(index);
    if !owner.is_null() {
        reader.column = Some(Lease::retain(owner));
        return 1;
    }
    if source.column(index).is_some() { 1 } else { -1 }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_reader_size(reader: *const Reader) -> i32 {
    reader.as_ref().map_or(0, Reader::size)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_reader_get(reader: *const Reader, index: i32, out: *mut u64) -> i32 {
    if index < 0 || out.is_null() || out as usize % 8 != 0 { return 0; }
    let Some(value) = reader.as_ref().and_then(|reader| reader.point(index)) else { return 0; };
    *out = value;
    1
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
            let reader = mattmc_dh_reader_create();
            assert_eq!(mattmc_dh_reader_select(reader, source, 7), 1);
            let column = (*source).owner(7);
            Column::points_mut(column).push(33);
            assert_eq!(mattmc_dh_reader_size(reader), 3);
            let mut value = 0;
            assert_eq!(mattmc_dh_reader_get(reader, 2, &mut value), 1);
            assert_eq!(value, 33);
            // Replacing/closing the source cannot invalidate a selected lease.
            (*source).adopt(7, Box::new(Column::new(vec![44])));
            mattmc_dh_source_release(source);
            assert_eq!(mattmc_dh_reader_get(reader, 0, &mut value), 1);
            assert_eq!(value, 11);
            assert_eq!(mattmc_dh_reader_get(reader, 3, &mut value), 0);
            mattmc_dh_reader_release(reader);
        }
    }
    #[test]
    fn selection_distinguishes_missing_custom_empty_and_invalid_logical_sizes() {
        unsafe {
            let source = mattmc_dh_source_create();
            let reader = mattmc_dh_reader_create();
            assert_eq!(mattmc_dh_reader_select(reader, source, 0), 1);
            assert_eq!(mattmc_dh_reader_size(reader), 0);
            assert_eq!(mattmc_dh_source_set(source, 1, 0, std::ptr::null_mut()), 1);
            assert_eq!(mattmc_dh_reader_select(reader, source, 1), 0);
            assert_eq!(mattmc_dh_source_set(source, 2, 1, std::ptr::null_mut()), 1);
            assert_eq!(mattmc_dh_reader_select(reader, source, 2), -1);
            let column = Box::new(Column::new(vec![])); column.negative_size(-3);
            (*source).adopt(3, column);
            assert_eq!(mattmc_dh_reader_select(reader, source, 3), 1);
            assert_eq!(mattmc_dh_reader_size(reader), -3);
            let mut value = 99;
            assert_eq!(mattmc_dh_reader_get(reader, 0, &mut value), 0);
            assert_eq!(value, 99);
            mattmc_dh_reader_release(reader); mattmc_dh_source_release(source);
        }
    }
}
