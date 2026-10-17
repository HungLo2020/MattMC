//! Saved DH lighting import. Borrow NBT nibbles once; retain compact native fields.
//! Frozen consumes list order (not section Y), skips incomplete pairs, and carries
//! discovered skylight forward to later empty arrays. No emitter cache is created.
use super::{Field, Layer, SectionMeta};
use std::panic::{catch_unwind, AssertUnwindSafe};

struct Channel {
    offsets: Vec<i32>,
    bytes: Vec<u8>,
    meta: Vec<Option<SectionMeta>>,
}
impl Channel {
    fn new(count: usize) -> Self {
        Self {
            offsets: vec![-1; count],
            bytes: Vec::new(),
            meta: vec![None; count],
        }
    }
    fn insert(&mut self, index: usize, raw: &[u8], default: u8) {
        let mut counts = [0i16; 16];
        let constant = if raw.is_empty() {
            counts[default as usize] = 4096;
            default as i8
        } else {
            for &byte in raw {
                counts[(byte & 15) as usize] += 1;
                counts[(byte >> 4) as usize] += 1;
            }
            counts
                .iter()
                .position(|&n| n == 4096)
                .map_or(-1, |n| n as i8)
        };
        self.meta[index] = Some(SectionMeta { counts, constant });
        if constant >= 0 {
            self.offsets[index] = -1 - i32::from(constant);
        } else {
            self.offsets[index] = self.bytes.len() as i32;
            self.bytes.extend_from_slice(raw);
        }
    }
    fn finish(self) -> (Layer, Box<[Option<SectionMeta>]>) {
        (
            Layer {
                offsets: self.offsets.into_boxed_slice(),
                data: self.bytes.into_boxed_slice(),
            },
            self.meta.into_boxed_slice(),
        )
    }
}
struct Import {
    min_y: i32,
    rows: usize,
    next: usize,
    found_sky: bool,
    block: Channel,
    sky: Channel,
}
impl Import {
    fn new(min_y: i32, count: usize, rows: usize) -> Option<Self> {
        if !(1..=256).contains(&count)
            || rows > 4096
            || min_y % 16 != 0
            || min_y.unsigned_abs() > 1_000_000
        {
            return None;
        }
        Some(Self {
            min_y,
            rows,
            next: 0,
            found_sky: false,
            block: Channel::new(count),
            sky: Channel::new(count),
        })
    }
    fn add(&mut self, index: usize, block: Option<&[u8]>, sky: Option<&[u8]>) -> bool {
        if index != self.next
            || index >= self.rows
            || [block, sky]
                .into_iter()
                .flatten()
                .any(|b| !b.is_empty() && b.len() != 2048)
        {
            return false;
        }
        self.next += 1;
        let (Some(block), Some(sky)) = (block, sky) else {
            return true;
        };
        self.found_sky |= !sky.is_empty();
        if index < self.block.offsets.len() {
            self.block.insert(index, block, 0);
            self.sky
                .insert(index, sky, if self.found_sky { 15 } else { 0 });
        }
        true
    }
    fn finish(self) -> Option<Field> {
        if self.next != self.rows {
            return None;
        }
        let (block, block_meta) = self.block.finish();
        let (sky, sky_meta) = self.sky.finish();
        Some(Field {
            min_y: self.min_y,
            block,
            sky,
            block_meta,
            sky_meta,
            sources: None,
        })
    }
}
pub struct Builder(Option<Import>);
fn aligned<T>(p: *const T) -> bool {
    !p.is_null() && p as usize % std::mem::align_of::<T>() == 0
}

#[no_mangle]
pub extern "C" fn mattmc_dh_saved_light_create(min_y: i32, count: i32, rows: i32) -> *mut Builder {
    if count < 0 || rows < 0 {
        return std::ptr::null_mut();
    }
    Import::new(min_y, count as usize, rows as usize).map_or(std::ptr::null_mut(), |i| {
        Box::into_raw(Box::new(Builder(Some(i))))
    })
}
/// -1 is a missing tag, 0 an empty tag, 2048 an existing borrowed nibble span.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_saved_light_add(
    owner: *mut Builder,
    index: i32,
    block: *const u8,
    block_len: i32,
    sky: *const u8,
    sky_len: i32,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        if !aligned(owner)
            || index < 0
            || [(block, block_len), (sky, sky_len)]
                .into_iter()
                .any(|(p, n)| !matches!(n, -1 | 0 | 2048) || n > 0 && p.is_null())
        {
            return 0;
        }
        let Some(owner) = (*owner).0.as_mut() else {
            return 0;
        };
        let view = |p, n| {
            if n < 0 {
                None
            } else if n == 0 {
                Some(&[][..])
            } else {
                Some(std::slice::from_raw_parts(p, n as usize))
            }
        };
        i32::from(owner.add(index as usize, view(block, block_len), view(sky, sky_len)))
    }))
    .unwrap_or(0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_saved_light_finish(owner: *mut Builder) -> *mut Field {
    if !aligned(owner) {
        return std::ptr::null_mut();
    }
    (*owner)
        .0
        .take()
        .and_then(Import::finish)
        .map_or(std::ptr::null_mut(), |f| Box::into_raw(Box::new(f)))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_saved_light_release(owner: *mut Builder) {
    if aligned(owner) {
        drop(Box::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_light_has_storage(field: *const Field, sky: i32) -> i32 {
    if !aligned(field) || !matches!(sky, 0 | 1) {
        return -1;
    }
    i32::from(
        (if sky == 0 {
            &(*field).block_meta
        } else {
            &(*field).sky_meta
        })
        .iter()
        .any(Option::is_some),
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn borrowed_nibbles_produce_independent_fields_and_unknown_emitters() {
        let mut block = vec![0x76; 2048];
        let sky = vec![0xaa; 2048];
        unsafe {
            let builder = mattmc_dh_saved_light_create(-64, 4, 5);
            assert!(!builder.is_null());
            assert_eq!(
                mattmc_dh_saved_light_add(builder, 1, block.as_ptr(), 2048, sky.as_ptr(), 2048),
                0
            );
            assert_eq!(
                mattmc_dh_saved_light_add(builder, 0, std::ptr::null(), -1, sky.as_ptr(), 2048),
                1
            );
            assert_eq!(
                mattmc_dh_saved_light_add(builder, 1, block.as_ptr(), 2048, sky.as_ptr(), 2048),
                1
            );
            assert_eq!(
                mattmc_dh_saved_light_add(builder, 2, std::ptr::null(), 0, std::ptr::null(), 0),
                1
            );
            assert_eq!(
                mattmc_dh_saved_light_add(builder, 3, std::ptr::null(), -1, std::ptr::null(), 0),
                1
            );
            assert_eq!(
                mattmc_dh_saved_light_add(builder, 4, block.as_ptr(), 2048, sky.as_ptr(), 2048),
                1
            );
            let field = mattmc_dh_saved_light_finish(builder);
            assert!(!field.is_null());
            assert!(mattmc_dh_saved_light_finish(builder).is_null());
            mattmc_dh_saved_light_release(builder);
            block.fill(0);
            assert_eq!((*field).level(0, -48, 0, false), 6);
            assert_eq!((*field).level(1, -48, 0, false), 7);
            assert_eq!((*field).level(0, -32, 0, true), 15);
            assert_eq!((*field).level(0, -64, 0, true), 0);
            assert_eq!((*field).level(0, -16, 0, true), 0);
            assert_eq!((&(*field).block.data).len(), 2048);
            assert_eq!((*field).block_meta[1].as_ref().unwrap().counts[6], 2048);
            assert!((*field).sources.is_none());
            assert!(super::super::sources::mattmc_dh_light_field_sources(field).is_null());
            assert_eq!(mattmc_dh_light_has_storage(field, 0), 1);
            super::super::ffi::mattmc_dh_light_field_release(field);
        }
    }
    #[test]
    fn incomplete_and_malformed_imports_never_publish() {
        assert!(Import::new(1, 1, 0).is_none());
        assert!(Import::new(0, 257, 0).is_none());
        assert!(Import::new(0, 1, 4097).is_none());
        let mut importer = Import::new(0, 1, 1).unwrap();
        assert!(!importer.add(0, Some(&[1, 2]), Some(&[])));
        assert!(importer.finish().is_none());
        let empty = Import::new(0, 1, 0).unwrap().finish().unwrap();
        assert!(empty.block_meta[0].is_none());
        assert!(empty.sky_meta[0].is_none());
        assert!(empty.sources.is_none());
    }
}
