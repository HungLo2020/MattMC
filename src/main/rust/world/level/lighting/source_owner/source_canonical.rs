//! Isolated native canonical producer: owns its real source Arc and unpublished work.
//! Every buffer is synchronously borrowed. No VM object/address is retained.
use super::{CanonicalFill, Source};
use crate::world::level::lighting::skylight_sources::{installed_tables, CUSTOM};
struct Bulk {
    fill: Option<CanonicalFill>,
    section: i32,
    min_y: i32,
    any_non_air: bool,
}
impl Bulk {
    fn valid_source(source: &Source, min_y: i32, height: i32, sections: i32, highest: i32) -> bool {
        if min_y % 16 != 0 || height % 16 != 0 || !(16..=4096).contains(&height)
            || (min_y as i64).abs() > 1_000_000 || sections != height / 16
            || highest < -1 || highest >= sections { return false; }
        let Ok(store) = source.0.lock() else { return false; };
        let required_bits = (32 - ((height + 2) as u32).leading_zeros()) as usize;
        store.min == min_y - 1 && store.bits == required_bits
    }
    fn new(source: &Source, min_y: i32, highest: i32) -> Option<Self> {
        installed_tables()?;
        Some(Self { fill: Some(source.canonical_fill().ok()?), section: highest,
            min_y, any_non_air: false })
    }
    fn request(&self, frame: &mut [i64; 16]) { frame[0] = 1; frame[1] = self.section as i64; }
    fn feed(&mut self, words: &[u64], ids: Option<&[u32]>, bits: i32,
        count: usize, frame: &mut [i64; 16]) -> Result<(), ()> {
        let tables = installed_tables().ok_or(())?;
        if !(-1..=32).contains(&bits) || count == 0 { return Err(()); }
        let expected_words = if bits <= 0 { 0 } else { 4096usize.div_ceil(64 / bits as usize) };
        if words.len() != expected_words { return Err(()); }
        let mut local = [0u32; 256];
        let flags = if bits == -1 { &local[..1] } else if let Some(ids) = ids {
            if ids.len() != count || count > 256 { return Err(()); }
            for (destination, &id) in local.iter_mut().zip(ids) {
                *destination = *tables.descriptors.get(id as usize).ok_or(())?;
                if *destination == CUSTOM { return Err(()); }
            }
            &local[..count]
        } else {
            if tables.custom || count != tables.descriptors.len() { return Err(()); }
            &tables.descriptors
        };
        self.any_non_air |= bits != -1;
        let fill = self.fill.as_mut().ok_or(())?;
        let remaining = fill.section(words, flags, &tables.edges, bits,
            self.min_y + self.section * 16, tables.faces).map_err(|_| ())?;
        self.complete_section(remaining, frame)
    }
    fn feed_live(&mut self, owner: &crate::world::level::chunk::live::Owner,
        frame: &mut [i64; 16]) -> Result<(), ()> {
        let tables = installed_tables().ok_or(())?;
        // Preserve the old resource admission: all local palette entries must
        // be supported, and any custom global registry state declines globally.
        let remaining = owner.with_state_reader(|reader| {
            let admitted = if reader.global_palette() { !tables.custom } else {
                reader.palette_states().all(|id| tables.descriptors.get(id as usize)
                    .is_some_and(|&descriptor| descriptor != CUSTOM))
            };
            if !admitted { return Err(()); }
            self.fill.as_mut().ok_or(())?.live_section(reader,
                &tables.descriptors, &tables.edges, self.min_y + self.section * 16,
                tables.faces).map_err(|_| ())
        })?;
        self.any_non_air = true;
        self.complete_section(remaining, frame)
    }
    fn complete_section(&mut self, remaining: i32, frame: &mut [i64; 16]) -> Result<(), ()> {
        if remaining != 0 {
            if self.section == 0 { return Err(()); }
            self.section -= 1; self.request(frame); return Ok(());
        }
        // Only the last visited column's final positions escape a pure canonical fill.
        // An all-air chunk never enters the original per-column loop.
        let fill = self.fill.take().ok_or(())?;
        let per = 64 / fill.bits;
        let encoded = ((fill.working[255 / per] >> ((255 % per) * fill.bits))
            & ((1u64 << fill.bits) - 1)) as i32;
        let last_y = if encoded == 0 { self.min_y } else { encoded + fill.min };
        fill.finish().map_err(|_| ())?;
        frame[0] = 0; frame[1] = i64::from(self.any_non_air); frame[2] = last_y as i64;
        Ok(())
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_bulk_new(id: u64, min_y: i32, height: i32,
    sections: i32, highest: i32, frame: *mut [i64; 16]) -> u64 {
    if frame.is_null() { return 0; }
    (*frame)[0] = -1; // Explicit DECLINE, distinct from successful empty DONE.
    if id == 0 { return 0; }
    let source = &*(id as usize as *const Source);
    if !Bulk::valid_source(source, min_y, height, sections, highest) { return 0; }
    if highest == -1 {
        if source.clear().is_err() { return 0; }
        (*frame)[0] = 0; (*frame)[1] = 0;
        return 0; // No cursor, working payload, section scratch or position effects.
    }
    let Some(bulk) = Bulk::new(source, min_y, highest) else { return 0; };
    bulk.request(&mut *frame);
    Box::into_raw(Box::new(bulk)) as usize as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_bulk_section(id: u64, words: *const u64,
    word_count: i32, ids: *const u32, count: i32, bits: i32, frame: *mut [i64; 16]) -> i32 {
    if id == 0 || words.is_null() || word_count < 0 || count < 1 || frame.is_null()
        || words as usize % 8 != 0 || !ids.is_null() && ids as usize % 4 != 0 {
        return -1;
    }
    let words = std::slice::from_raw_parts(words, word_count as usize);
    let ids = if ids.is_null() { None } else {
        if count > 256 { return -1; }
        Some(std::slice::from_raw_parts(ids, count as usize))
    };
    (&mut *(id as usize as *mut Bulk)).feed(words, ids, bits, count as usize, &mut *frame)
        .map_or(-1, |_| 0)
}
/// The exact air-counter shortcut needs no packed-word or palette buffer.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_bulk_air_section(id: u64,
    frame: *mut [i64; 16]) -> i32 {
    if id == 0 || frame.is_null() || frame as usize % 8 != 0 { return -1; }
    (&mut *(id as usize as *mut Bulk)).feed(&[], Some(&[0]), -1, 1, &mut *frame)
        .map_or(-1, |_| 0)
}
/// Both the bulk cursor and exact section owner are pinned synchronously by
/// the VM resource bridge. No retained section borrow or VM address is stored.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_bulk_live_section(id: u64,
    owner: *const crate::world::level::chunk::live::Owner,
    frame: *mut [i64; 16]) -> i32 {
    if id == 0 || owner.is_null() || frame.is_null()
        || owner as usize % std::mem::align_of::<crate::world::level::chunk::live::Owner>() != 0
        || frame as usize % 8 != 0 { return -1; }
    (&mut *(id as usize as *mut Bulk)).feed_live(&*owner, &mut *frame).map_or(-1, |_| 0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_bulk_release(id: u64) {
    if id != 0 { drop(Box::from_raw(id as usize as *mut Bulk)); }
}

#[cfg(test)]
mod highest_tests {
    use super::*;
    #[test]
    fn actual_empty_done_clears_store_without_cursor_and_rejects_malformed_facts() {
        let source = Source::from_bounds(0,64).unwrap();
        source.set(0,33).unwrap();
        let id = &source as *const Source as usize as u64;
        let mut frame = [99;16];
        unsafe {
            assert_eq!(mattmc_sky_source_bulk_new(id,0,64,4,-2,&mut frame),0);
            assert_eq!(frame[0],-1); assert_eq!(source.get(0).unwrap(),33);
            assert_eq!(mattmc_sky_source_bulk_new(id,0,64,4,4,&mut frame),0);
            assert_eq!(frame[0],-1); assert_eq!(source.get(0).unwrap(),33);
            assert_eq!(mattmc_sky_source_bulk_new(id,0,64,4,-1,&mut frame),0);
        }
        assert_eq!((frame[0],frame[1]),(0,0));
        assert_eq!((frame[2],frame[15]),(99,99));
        for index in 0..256 { assert_eq!(source.get(index).unwrap(),i32::MIN); }
    }
}
