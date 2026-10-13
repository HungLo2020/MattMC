//! Conversion-local material facts. No world or Java references are retained.
//! Zero means unresolved; admitted words always carry a registered state ID.
use std::sync::atomic::{AtomicU64, Ordering};
static COLOR_GENERATION: AtomicU64 = AtomicU64::new(0);
pub(crate) fn color_generation() -> u64 { COLOR_GENERATION.load(Ordering::Acquire) }
#[derive(Clone, Copy, Default)]
struct Entry { facts: u64, color: Option<u32> }
pub(crate) struct Materials { words: Box<[Entry]>, color_generation: u64 }
impl Materials {
    pub(crate) fn get(&self, id: i32) -> Option<u64> {
        let word = self.words.get(usize::try_from(id).ok()?)?.facts;
        (word != 0).then_some(word)
    }
    pub(crate) fn put(&mut self, id: i32, word: u64) {
        if let Some(slot) = usize::try_from(id).ok().and_then(|i| self.words.get_mut(i)) { slot.facts = word; }
    }
    fn refresh_colors(&mut self) {
        let generation = color_generation();
        if generation != self.color_generation {
            for entry in &mut self.words { entry.color = None; }
            self.color_generation = generation;
        }
    }
    pub(crate) fn color(&mut self, id: i32) -> Option<u32> {
        self.refresh_colors();
        self.words.get(usize::try_from(id).ok()?)?.color
    }
    pub(crate) fn put_color(&mut self, id: i32, color: u32, generation: u64) {
        self.refresh_colors();
        // A reload during a contextual query must not publish its old result
        // into the new generation. Unsupported/custom facts are never cached.
        if generation != self.color_generation { return; }
        if let Some(slot) = usize::try_from(id).ok().and_then(|i| self.words.get_mut(i)) {
            if slot.facts != 0 { slot.color = Some(color); }
        }
    }
}
#[no_mangle]
pub extern "C" fn mattmc_dh_conversion_materials_colors_changed() {
    COLOR_GENERATION.fetch_add(1, Ordering::AcqRel);
}
#[no_mangle]
pub extern "C" fn mattmc_dh_conversion_materials_create(count: i32) -> *mut Materials {
    if !(1..=1_048_576).contains(&count) { return std::ptr::null_mut(); }
    let mut words = Vec::new();
    if words.try_reserve_exact(count as usize).is_err() { return std::ptr::null_mut(); }
    words.resize(count as usize, Entry::default());
    Box::into_raw(Box::new(Materials { words: words.into_boxed_slice(), color_generation: color_generation() }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_conversion_materials_clear(owner: *mut Materials) {
    if let Some(owner) = owner.as_mut() { owner.words.fill(Entry::default()); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_conversion_materials_release(owner: *mut Materials) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn bounded_facts_distinguish_unresolved_and_invalidate_together() {
        unsafe {
            assert!(mattmc_dh_conversion_materials_create(-1).is_null());
            assert!(mattmc_dh_conversion_materials_create(1_048_577).is_null());
            let owner = mattmc_dh_conversion_materials_create(2);
            assert!(!owner.is_null());
            assert_eq!((*owner).get(0), None);
            (*owner).put(0, 1u64 << 32);
            (*owner).put(1, (2u64 << 32) | 3);
            (*owner).put(-1, 99); (*owner).put(2, 99);
            assert_eq!((*owner).get(0), Some(1u64 << 32));
            assert_eq!((*owner).get(1), Some((2u64 << 32) | 3));
            assert_eq!((*owner).get(2), None);
            let generation = color_generation();
            (*owner).put_color(0, 0xff123456, generation);
            assert_eq!((*owner).color(0), Some(0xff123456));
            mattmc_dh_conversion_materials_colors_changed();
            assert_eq!((*owner).color(0), None);
            (*owner).put_color(0, 0xffabcdef, generation);
            assert_eq!((*owner).color(0), None, "reload during callback must decline its stale color");
            assert_eq!((*owner).get(0), Some(1u64 << 32), "color invalidation retains intrinsic facts");
            mattmc_dh_conversion_materials_clear(owner);
            assert_eq!((*owner).get(0), None); assert_eq!((*owner).get(1), None);
            mattmc_dh_conversion_materials_release(owner);
        }
    }
}
