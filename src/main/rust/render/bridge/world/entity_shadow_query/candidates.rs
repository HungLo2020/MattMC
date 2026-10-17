//! Bounded CPU candidate storage shared by capture and shadow admission.
//! The Java producer borrows writable slots until selection. Growth invalidates
//! those views; no renderer/GPU resource or Java object is retained here.
use crate::render::bridge::abi::FFI_MAX_BATCH_ITEMS;
use crate::render::worldrender::frame::entity_culling::{
    WorldEntityCullingInputs, ENTITY_CULL_LEASH_HOLDER,
};

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct Candidate {
    pub flags: u32,
    pub reserved: u32,
    pub bounds: [f64; 6],
    pub holder: [f64; 6],
}
impl Candidate {
    pub fn inputs(&self, camera: [f64; 3]) -> WorldEntityCullingInputs {
        WorldEntityCullingInputs {
            flags: self.flags,
            bounds: self.bounds,
            leash_holder_bounds: (self.flags & ENTITY_CULL_LEASH_HOLDER != 0)
                .then_some(self.holder),
            camera,
        }
    }
}

#[repr(C)]
pub struct Header {
    slots: *mut Candidate,
    admitted: *mut u8,
    capacity: u32,
    reserved: u32,
}
#[repr(C)]
pub struct Candidates {
    header: Header,
    pub slots: Vec<Candidate>,
    pub admitted: Vec<u8>,
}
impl Default for Candidates {
    fn default() -> Self {
        Self {
            header: Header {
                slots: std::ptr::null_mut(),
                admitted: std::ptr::null_mut(),
                capacity: 0,
                reserved: 0,
            },
            slots: Vec::new(),
            admitted: Vec::new(),
        }
    }
}
impl Candidates {
    fn refresh(&mut self) {
        self.header.slots = self.slots.as_mut_ptr();
        self.header.admitted = self.admitted.as_mut_ptr();
        self.header.capacity = self.slots.len().min(self.admitted.len()) as u32;
    }
    fn grow(&mut self, minimum: usize) -> bool {
        if minimum > FFI_MAX_BATCH_ITEMS {
            return false;
        }
        if minimum <= self.header.capacity as usize {
            return true;
        }
        let next = minimum.max(64).next_power_of_two().min(FFI_MAX_BATCH_ITEMS);
        // Refresh even if only the first allocation succeeded: old views may
        // already be invalid and the caller must re-read the header on failure.
        let ok = self.slots.try_reserve(next - self.slots.len()).is_ok()
            && self
                .admitted
                .try_reserve(next - self.admitted.len())
                .is_ok();
        if ok {
            self.slots.resize(next, Candidate::default());
            self.admitted.resize(next, 0);
        }
        self.refresh();
        ok
    }
}
#[no_mangle]
pub extern "C" fn mattmc_entity_shadow_candidates_create() -> *mut Candidates {
    Box::into_raw(Box::default())
}
/// # Safety
/// `owner` is a live exclusively borrowed candidate owner.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_shadow_candidates_grow(
    owner: *mut Candidates,
    minimum: u32,
) -> i32 {
    owner
        .as_mut()
        .map_or(0, |owner| i32::from(owner.grow(minimum as usize)))
}
/// # Safety
/// `owner` was created here and has no outstanding users or views.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_shadow_candidates_release(owner: *mut Candidates) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn growth_preserves_captures_and_is_bounded() {
        assert_eq!(std::mem::size_of::<Header>(), 24);
        assert_eq!(std::mem::size_of::<Candidate>(), 104);
        let mut owner = Candidates::default();
        assert!(owner.grow(1));
        owner.slots[0].flags = 17;
        owner.slots[0].bounds = [-1., -2., -3., 4., 5., 6.];
        owner.slots[0].holder = [1., 2., 3., 4., 5., 6.];
        let captured = owner.slots[0].inputs([7., 8., 9.]);
        assert!(owner.grow(65));
        assert_eq!(owner.header.capacity, 128);
        assert_eq!(owner.slots[0].inputs([7., 8., 9.]), captured);
        assert!(owner.grow(FFI_MAX_BATCH_ITEMS));
        assert!(!owner.grow(FFI_MAX_BATCH_ITEMS + 1));
        assert_eq!(owner.slots[0].inputs([7., 8., 9.]), captured);
        assert_eq!(owner.header.capacity as usize, FFI_MAX_BATCH_ITEMS);
        assert!(captured.validate().is_ok());
    }
}
