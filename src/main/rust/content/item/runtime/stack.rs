//! Retained stack identity/count and its component overlay. Java scalar views
//! are scoped to this Arc's lifetime and use aligned atomic/volatile accesses.
use super::patch::Overlay;
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicI32, AtomicBool, Ordering};

#[repr(C)]
pub struct Scalars {
    pub item: u64,
    pub count: AtomicI32,
    pub pop_time: AtomicI32,
}
pub struct Admission {
    pub prototype:u64,
    pub defaults:Option<Arc<super::slots::BoundaryOwner<super::patch::defaults::Store>>>,
    pub overlay_pristine:Arc<AtomicBool>,
    pub backing_pristine:Arc<AtomicBool>,
}
impl Admission {
    pub fn valid(&self)->bool {self.overlay_pristine.load(Ordering::Acquire) && self.backing_pristine.load(Ordering::Acquire)}
}
/// Retained aliases keep their actual allocation. Unique components may reside
/// inside the stack allocation; compatibility aliases then lease that real owner.
pub enum Components {
    Shared(Arc<Mutex<Overlay>>),
    Inline(Mutex<Overlay>),
    Parent(Arc<Stack>),
    Unpublished,
}
impl std::ops::Deref for Components {
    type Target=Mutex<Overlay>;
    fn deref(&self)->&Self::Target {match self {
        Self::Shared(value)=>value.as_ref(),Self::Inline(value)=>value,
        Self::Parent(owner)=>&owner.components,Self::Unpublished=>unreachable!("unpublished component move"),
    }}
}
impl Components {
    pub fn from_stack(owner:Arc<Stack>)->Self {match &owner.components {
        Self::Shared(value)=>Self::Shared(value.clone()),Self::Parent(root)=>Self::Parent(root.clone()),
        Self::Inline(_)=>Self::Parent(owner),Self::Unpublished=>unreachable!("unpublished component move"),
    }}
    fn inline_if_unique(&mut self) {
        if !matches!(self,Self::Shared(_)){return;}
        let Self::Shared(value)=std::mem::replace(self,Self::Unpublished) else{unreachable!()};
        *self=match Arc::try_unwrap(value){Ok(value)=>Self::Inline(value),Err(value)=>Self::Shared(value)};
    }
}

#[repr(C)]
pub struct Stack {
    pub scalars: Scalars,
    pub components: Components,
    pub empty_identity: bool,
    pub admission: Admission,
}
impl Stack {
    pub fn new(item:u64,count:i32,components:Arc<Mutex<Overlay>>)->Self {Self::with_components(item,count,Components::Shared(components))}
    pub fn from_overlay(item:u64,count:i32,components:Overlay)->Self {Self::with_components(item,count,Components::Inline(Mutex::new(components)))}
    pub fn with_components(item:u64,count:i32,components:Components)->Self {
        let admission={let owner=components.lock().unwrap();Admission {prototype:owner.prototype,defaults:owner.defaults.clone(),overlay_pristine:owner.pristine.clone(),backing_pristine:owner.backing_marker()}};
        Self { scalars: Scalars { item, count: AtomicI32::new(count), pop_time: AtomicI32::new(0) }, components, empty_identity: false, admission }
    }
    /// Only before publishing this Stack, under the owner registry guard. No
    /// component borrow is held; a concurrent real retained Arc prevents moving.
    pub fn inline_unique_components(&mut self){self.components.inline_if_unique();}
    pub fn address(&self) -> usize { &self.scalars as *const Scalars as usize }
}

#[cfg(test)] mod tests {
    use super::*;
    use std::sync::atomic::Ordering;
    #[test] fn scalar_layout_count_bits_and_overlay_identity_are_stable() {
        let overlay = Arc::new(Mutex::new(Overlay::new(55)));
        let stack = Arc::new(Stack::new(77, i32::MIN, overlay.clone()));
        assert_eq!(std::mem::offset_of!(Scalars, item), 0); assert_eq!(std::mem::offset_of!(Scalars, count), 8);
        assert_eq!(std::mem::size_of::<Scalars>(), 16);
        let address = stack.address(); let retained = stack.clone(); drop(stack);
        assert_eq!(retained.address(), address); assert!(std::ptr::eq(&*retained.components, &*overlay));
        assert_eq!(retained.scalars.count.load(Ordering::SeqCst), i32::MIN);
        retained.scalars.count.store(i32::MAX, Ordering::SeqCst);
        assert_eq!(retained.scalars.count.load(Ordering::SeqCst).wrapping_add(1), i32::MIN);
    }
}
