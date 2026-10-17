//! Stock chest slot membership. A row retains the actual stack, plus the stable
//! Java facade token used only to resolve its original identity/reachability.
use super::stack::Stack;
use std::sync::Arc;

/// Canonical owner exclusion, also admitted from Java before a bounded critical
/// call. The critical body only borrows; it never acquires or releases this gate.
#[repr(C)]
#[derive(Debug)]
pub struct BoundaryOwner<T> {
    gate: std::sync::atomic::AtomicU64,
    value: std::cell::UnsafeCell<T>,
}
unsafe impl<T: Send> Send for BoundaryOwner<T> {}
unsafe impl<T: Send> Sync for BoundaryOwner<T> {}
impl<T> BoundaryOwner<T> {
    pub fn new(value:T)->Self {Self{gate:std::sync::atomic::AtomicU64::new(0),value:std::cell::UnsafeCell::new(value)}}
    pub fn lock(&self)->Result<BoundaryGuard<'_,T>,std::convert::Infallible> {
        while self.gate.compare_exchange(0,1,std::sync::atomic::Ordering::Acquire,std::sync::atomic::Ordering::Relaxed).is_err() {std::thread::yield_now();}
        Ok(BoundaryGuard{owner:self,not_send:std::marker::PhantomData})
    }
    /// One attempt only. Bounded consumers fall back instead of waiting.
    pub fn try_lock(&self)->Option<BoundaryGuard<'_,T>> {
        self.gate.compare_exchange(0,1,std::sync::atomic::Ordering::Acquire,std::sync::atomic::Ordering::Relaxed).ok()
            .map(|_|BoundaryGuard{owner:self,not_send:std::marker::PhantomData})
    }
    /// Caller holds this exact gate and pins its actual Arc through the borrow.
    /// A pointer or a gate value alone is never an allocation lease.
    pub unsafe fn admitted(&self)->&T {&*self.value.get()}
    pub fn address(&self)->usize {self as *const Self as usize}
}
pub struct BoundaryGuard<'a,T> {owner:&'a BoundaryOwner<T>,not_send:std::marker::PhantomData<*mut ()>}
impl<T> std::ops::Deref for BoundaryGuard<'_,T> {type Target=T;fn deref(&self)->&T {unsafe{&*self.owner.value.get()}}}
impl<T> std::ops::DerefMut for BoundaryGuard<'_,T> {fn deref_mut(&mut self)->&mut T {unsafe{&mut *self.owner.value.get()}}}
impl<T> Drop for BoundaryGuard<'_,T> {fn drop(&mut self){self.owner.gate.store(0,std::sync::atomic::Ordering::Release);}}

#[derive(Clone)]
pub struct Entry { pub facade: i64, pub stack: Arc<Stack> }

/// Natively published contents that no Java facade has observed yet carry a
/// pending key instead of a facade token. Keys never collide with registry
/// handles (positive), 0 (null), -1 (absent), BAD (-2) or busy results.
pub const PENDING_BASE:i64=-(1i64<<40);
pub fn is_pending(token:i64)->bool{token<=PENDING_BASE}
static NEXT_PENDING:std::sync::atomic::AtomicI64=std::sync::atomic::AtomicI64::new(PENDING_BASE);
pub fn next_pending_key()->i64{NEXT_PENDING.fetch_sub(1,std::sync::atomic::Ordering::Relaxed)}
/// Witnesses (`mattmc_item_transfer_counter` 5..7): pending publications,
/// facades later materialized for them, and publications rolled back.
pub static PENDING_PUBLISHED:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
pub static PENDING_MATERIALIZED:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
pub static PENDING_ROLLED_BACK:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
/// Broadcast copies built from pending contents without materializing them (counter 8).
pub static PENDING_BROADCAST_COPIES:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
pub struct Slots { entries: Vec<Entry> }
impl Slots {
    pub fn fill(&mut self,empty:Entry) {self.entries.fill(empty);}
    pub fn entries_facade_index(&self,facade:i64)->i32 {self.entries.iter().position(|entry|entry.facade==facade).map_or(-1,|index|index as i32)}
    pub fn new(size: usize, empty: Entry) -> Self { Self { entries: vec![empty; size] } }
    pub fn get(&self, index: usize) -> Option<&Entry> { self.entries.get(index) }
    pub fn len(&self) -> usize { self.entries.len() }
    pub fn set(&mut self, index: usize, value: Entry) -> Option<Entry> {
        Some(std::mem::replace(self.entries.get_mut(index)?, value))
    }
}
#[cfg(test)] mod tests {
    use super::*;
    use super::super::patch::Overlay;
    use std::sync::Mutex;
    use std::sync::atomic::Ordering;
    #[test] fn two_slots_retain_one_stack_and_release_does_not_invalidate_external_owner() {
        let stack = Arc::new(Stack::new(9, 7, Arc::new(Mutex::new(Overlay::new(1)))));
        let empty = Entry { facade: 1, stack: Arc::new(Stack::new(0, 0, Arc::new(Mutex::new(Overlay::new(0))))) };
        let mut slots = Slots::new(27, empty.clone());
        slots.set(0, Entry { facade: 2, stack: stack.clone() }); slots.set(1, Entry { facade: 2, stack: stack.clone() });
        stack.scalars.count.store(11, Ordering::SeqCst);
        assert!(Arc::ptr_eq(&slots.get(0).unwrap().stack, &slots.get(1).unwrap().stack));
        assert_eq!(slots.get(1).unwrap().stack.scalars.count.load(Ordering::SeqCst), 11);
        slots.set(0, empty.clone()); assert_eq!(slots.get(1).unwrap().facade, 2);
        slots.set(1, empty); drop(slots);
        assert_eq!(stack.scalars.count.load(Ordering::SeqCst), 11);
    }
}

// Complete stock-chest scan. A result ends BEFORE entering the next Java effect.
// action:0 done,1 committed merge/dirty callback,2 stage empty insertion,3 Java continuation.
// phase:0 evaluate stackability,1 merge,2 empty. Index is the next original read point.
fn transfer_result(action:u64,index:usize,phase:u64,amount:i32)->i64 {
    (action | ((index as u64)<<3) | (phase<<9) | ((amount as u32 as u64)<<32)) as i64
}
/// Exact broadcast snapshot of contents whose only components are their
/// prototype's: item identity, prototype identity and raw count packed
/// losslessly (21 bits each; EMPTY's identity is 0), or -1 when the contents
/// are not eligible (components patched, or a field out of range). Equal
/// snapshots mean equal ItemStack.matches/remote-hash content.
pub fn broadcast_digest(stack:&Stack)->i64 {
    if stack.empty_identity {return 0;}
    const LIMIT:u64=1<<21;
    let item=stack.scalars.item;let count=stack.scalars.count.load(std::sync::atomic::Ordering::Relaxed);
    // Never-patched contents (admission markers) need no component lock.
    let prototype=if stack.admission.valid(){stack.admission.prototype}else{
        let Ok(overlay)=stack.components.try_lock() else{return -1};if overlay.patch_len()!=0{return -1;}overlay.prototype};
    if item==0||item>=LIMIT||prototype>=LIMIT||count<0||count as u64>=LIMIT {return -1;}
    ((item<<42)|(prototype<<21)|count as u64) as i64
}
/// Items whose ItemStack copy is pure (inherited asItem); registered by Java at component binding.
static PURE_COPY:std::sync::RwLock<Option<std::collections::HashSet<u64>>>=std::sync::RwLock::new(None);
pub fn mark_pure_copy(item:u64){PURE_COPY.write().unwrap().get_or_insert_with(Default::default).insert(item);}
pub fn pure_copy(item:u64)->bool{PURE_COPY.read().unwrap().as_ref().is_some_and(|items|items.contains(&item))}
/// Shulker box block items (ShulkerBoxBlockEntity.canPlaceItemThroughFace refuses them).
static SHULKER_ITEM:std::sync::RwLock<Option<std::collections::HashSet<u64>>>=std::sync::RwLock::new(None);
pub fn mark_shulker_item(item:u64){SHULKER_ITEM.write().unwrap().get_or_insert_with(Default::default).insert(item);}
pub fn shulker_item(item:u64)->bool{SHULKER_ITEM.read().unwrap().as_ref().is_some_and(|items|items.contains(&item))}
/// ItemStack.getMaxStackSize of unpatched contents: the prototype's max stack size.
pub fn plain_max(stack:&Stack)->Option<i32>{let defaults=stack.admission.defaults.as_ref()?;let store=defaults.lock().ok()?;max_size(&store)}
/// AbstractContainerMenu.getRedstoneSignalFromContainer over stock rows (container
/// max stack size 99), in the original float order; None when a non-empty stack
/// is not unpatched (its max stack size needs Java's component map).
pub fn redstone_signal(rows:&Slots,air:u64)->Option<i32> {redstone_signal_parts(&[rows],air)}
/// The same signal over concatenated containers (CompoundContainer: first then
/// second half, total size), in the original slot order.
pub fn redstone_signal_parts(parts:&[&Slots],air:u64)->Option<i32> {
    let size:usize=parts.iter().map(|rows|rows.len()).sum();let mut sum=0.0f32;
    for rows in parts { for index in 0..rows.len() {
        let stack=&rows.get(index)?.stack;let count=visible_count(stack,air);if count==0{continue;}
        if broadcast_digest(stack)<=0{return None;}
        sum+=count as f32/99.min(plain_max(stack)?) as f32;
    }}
    sum/=size as f32;
    Some((sum*14.0f32).floor() as i32+i32::from(sum>0.0))
}
/// Container.isEmpty (bit 1) and HopperBlockEntity.inventoryFull (bit 2: every
/// slot non-empty at exactly its max stack size) over stock rows; bit 4 when
/// fullness depends on a patched stack's component map (emptiness never does).
pub fn occupancy(rows:&Slots,air:u64)->Option<i64> {
    let (mut empty,mut full,mut unknown)=(true,true,false);
    for index in 0..rows.len() {
        let stack=&rows.get(index)?.stack;let count=visible_count(stack,air);
        if count==0{full=false;continue;}empty=false;
        if broadcast_digest(stack)<=0{unknown=true;continue;}
        match plain_max(stack){Some(max)=>if count!=max{full=false;},None=>unknown=true}
    }
    Some(i64::from(empty)|i64::from(full&&!unknown)<<1|i64::from(full&&unknown)<<2)
}
/// Bit i set when row i is non-empty (ItemStack.isEmpty false), for rows 0..63.
pub fn occupied_mask(rows:&Slots,air:u64)->Option<i64> {
    if rows.len()>63{return None;}
    let mut mask=0i64;for index in 0..rows.len(){if visible_count(&rows.get(index)?.stack,air)!=0{mask|=1<<index;}}Some(mask)
}
pub fn visible_count(stack:&Stack,air:u64)->i32 {
    if stack.empty_identity || stack.scalars.item==air {0} else {stack.scalars.count.load(std::sync::atomic::Ordering::Relaxed).max(0)}
}
fn pristine(stack:&Stack,bounded:bool)->Option<(u64,&Arc<super::slots::BoundaryOwner<super::patch::defaults::Store>>)> {
    if !stack.admission.valid() {
        if bounded {return None;}
        // Invalidated metadata never asserts membership. Preserve admission for
        // an actually empty canonical patch (including loaded/cleared stacks).
        if stack.components.lock().unwrap().patch_len()!=0{return None;}
    }
    Some((stack.admission.prototype,stack.admission.defaults.as_ref()?))
}
fn max_size(defaults:&super::patch::defaults::Store)->Option<i32> {
    // A malformed raw builder remains legal; its original Java cast must throw at its old read point.
    if (1..=3).any(|kind| !defaults.scalar_valid(kind)) {return None;}
    Some(defaults.scalar(1).unwrap_or(1))
}
pub static TRANSFER_STEPS:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
pub static TRANSFER_NATIVE_DEFAULTS:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
pub static TRANSFER_NATIVE_SLOTS:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
#[derive(Default)]
pub struct TransferStats { pub steps:u64, pub defaults:u64, pub slots:u64 }
impl Drop for TransferStats {
    fn drop(&mut self) {
        use std::sync::atomic::Ordering;
        TRANSFER_STEPS.fetch_add(self.steps,Ordering::Relaxed);
        TRANSFER_NATIVE_DEFAULTS.fetch_add(self.defaults,Ordering::Relaxed);
        TRANSFER_NATIVE_SLOTS.fetch_add(self.slots,Ordering::Relaxed);
    }
}
pub fn transfer_step(slots:&Slots,source:&Arc<Stack>,air:u64,phase:u64,index:usize)->i64 {
    transfer_step_counted(slots,source,air,phase,index,&mut TransferStats::default())
}
pub fn transfer_step_counted(slots:&Slots,source:&Stack,air:u64,phase:u64,index:usize,stats:&mut TransferStats)->i64 {
    transfer_step_inner(slots,source,air,phase,index,stats,None,false)
}
/// Actual default and slot owners have been excluded by the caller before entry.
/// This body is bounded, does no allocation/locking/lookup of owner handles/upcall.
pub unsafe fn transfer_step_admitted(slots:&Slots,source:&Stack,defaults:&BoundaryOwner<super::patch::defaults::Store>,air:u64,phase:u64,index:usize)->i64 {
    transfer_step_inner(slots,source,air,phase,index,&mut TransferStats::default(),Some((defaults,defaults.admitted())),false)
}
pub fn transfer_step_inventory(slots:&Slots,source:&Stack,air:u64,phase:u64,index:usize,stats:&mut TransferStats)->i64 {
    transfer_step_inner(slots,source,air,phase,index,stats,None,true)
}
/// Original reverse menu62..27 is inventory8..0 followed by35..9.
pub fn target_index(index:usize,inventory_reverse:bool)->usize {
    if !inventory_reverse {index}else if index<9 {8-index}else{44-index}
}
pub(super) fn transfer_payload_step(slots:&Slots,source:&Stack,air:u64,mut phase:u64,mut index:usize,stats:&mut TransferStats,inventory_reverse:bool,roles:&super::components::Roles)->i64 {
    use std::sync::atomic::Ordering;
    let limit=if inventory_reverse {36}else{27};
    if slots.entries.len()!=limit || index>limit || phase>2{return 7;}
    if visible_count(source,air)==0{return transfer_result(0,index,phase,0);}
    let Some(max)=super::components::max_size_with(source,roles) else{return transfer_result(3,index,phase,0)};
    stats.defaults+=1;
    if phase==0 {
        let Some(stackable)=super::components::stackable_with(source,roles) else{return transfer_result(3,index,phase,0)};
        phase=if stackable{1}else{2};
    }
    if phase==1 {
        while visible_count(source,air)!=0 && index<limit {
            let target=&slots.entries[target_index(index,inventory_reverse)].stack;stats.slots+=1;
            if visible_count(target,air)!=0 && target.scalars.item==source.scalars.item {
                let Some(equal)=inventory_same_with_roles(source,target,air,Some(roles)) else{return transfer_result(3,index,1,0)};
                if equal {
                    let source_count=visible_count(source,air);let target_count=visible_count(target,air);
                    let sum=target_count.wrapping_add(source_count);let cap=99.min(max);
                    if sum<=cap {
                        source.scalars.count.store(0,Ordering::Relaxed);target.scalars.count.store(sum,Ordering::Relaxed);
                        return transfer_result(1,index,1,0);
                    }else if target_count<cap {
                        source.scalars.count.store(source_count.wrapping_sub(cap.wrapping_sub(target_count)),Ordering::Relaxed);
                        target.scalars.count.store(cap,Ordering::Relaxed);return transfer_result(1,index,1,0);
                    }
                }
            }
            index+=1;
        }
        if visible_count(source,air)==0{return transfer_result(0,index,1,0);}
        phase=2;index=0;
    }
    while index<limit {
        stats.slots+=1;
        if visible_count(&slots.entries[target_index(index,inventory_reverse)].stack,air)==0 {
            return transfer_result(2,index,phase,visible_count(source,air).min(99.min(max)));
        }
        index+=1;
    }
    transfer_result(0,index,phase,0)
}
fn transfer_step_inner(slots:&Slots,source:&Stack,air:u64,mut phase:u64,mut index:usize,stats:&mut TransferStats,admitted:Option<(&BoundaryOwner<super::patch::defaults::Store>,&super::patch::defaults::Store)>,inventory_reverse:bool)->i64 {
    use std::sync::atomic::Ordering;
    stats.steps+=1;
    let limit=if inventory_reverse {36}else{27};
    if slots.entries.len()!=limit || index>limit || phase>2 {return 7;}
    if visible_count(source,air)==0 {return transfer_result(0,index,phase,0);}
    let Some((prototype,defaults))=pristine(source,admitted.is_some()) else{
        if admitted.is_none(){if let Some(roles)=super::components::roles(){return transfer_payload_step(slots,source,air,phase,index,stats,inventory_reverse,roles);}}
        return transfer_result(if admitted.is_some(){4}else{3},index,phase,0)
    };
    let max;
    {
        let locked;
        let d=if let Some((expected,value))=admitted {
            if !std::ptr::eq(defaults.as_ref(),expected) || !value.bounded_transfer() {return transfer_result(4,index,phase,0);}
            value
        } else {locked=defaults.lock().unwrap();&locked};
        let Some(value)=max_size(d) else{
            // End the default borrow before the full actual-payload reader.
            return transfer_result(3,index,phase,0)
        };max=value;
        stats.defaults+=1;
        if phase==0 {
            let damageable=d.scalar(2).is_some() && d.scalar(4).is_none() && d.scalar(3).is_some();
            let damaged=d.scalar(3).unwrap_or(0).max(0).min(d.scalar(2).unwrap_or(0))>0;
            phase=if max>1 && (!damageable || !damaged){1}else{2};
        }
    }
    if phase==1 {
        while visible_count(source,air)!=0 && index<limit {
            let target=&slots.entries[target_index(index,inventory_reverse)].stack;stats.slots+=1;
            if visible_count(target,air)!=0 && target.scalars.item==source.scalars.item {
                let Some((target_proto,target_defaults))=pristine(target,admitted.is_some()) else{
                    if admitted.is_none(){if let Some(roles)=super::components::roles(){return transfer_payload_step(slots,source,air,1,index,stats,inventory_reverse,roles);}}
                    return transfer_result(if admitted.is_some(){4}else{3},index,1,0)
                };
                if target_proto!=prototype || !Arc::ptr_eq(defaults,target_defaults) {return transfer_result(3,index,1,0);}
                let source_count=visible_count(source,air);let target_count=visible_count(target,air);
                let sum=target_count.wrapping_add(source_count);let cap=99.min(max);
                if sum<=cap {
                    // Aliases require these exact sequential stores, including source==target.
                    source.scalars.count.store(0,Ordering::Relaxed);target.scalars.count.store(sum,Ordering::Relaxed);
                    return transfer_result(1,index,1,0);
                } else if target_count<cap {
                    source.scalars.count.store(source_count.wrapping_sub(cap.wrapping_sub(target_count)),Ordering::Relaxed);
                    target.scalars.count.store(cap,Ordering::Relaxed);return transfer_result(1,index,1,0);
                }
            }
            index+=1;
        }
        if visible_count(source,air)==0 {return transfer_result(0,index,1,0);}
        phase=2;index=0;
    }
    while index<limit {
        stats.slots+=1;
        if visible_count(&slots.entries[target_index(index,inventory_reverse)].stack,air)==0 {
            return transfer_result(2,index,phase,visible_count(source,air).min(99.min(max)));
        }
        index+=1;
    }
    transfer_result(0,index,phase,0)
}
pub fn transfer_insert(slots:&mut Slots,source:&Stack,index:usize,inserted:Entry,amount:i32)->Option<Entry> {
    use std::sync::atomic::Ordering;
    // Validate all internal addresses/indices BEFORE the original split shrink commit.
    slots.entries.get(index)?;
    // The caller staged the actual facade/root and copied stack before this original shrink point.
    // Source is known nonempty/pure; copied inserted count is the captured split amount.
    source.scalars.count.store(source.scalars.count.load(Ordering::Relaxed).max(0).wrapping_sub(amount),Ordering::Relaxed);
    Some(std::mem::replace(&mut slots.entries[index],inserted))
}

/// Pure managed predicate used by the complete inventory-add consumer. Foreign
/// equality/component casts return None BEFORE calling any Java behavior.
pub fn inventory_space(target:&Stack,source:&Stack,air:u64)->Option<bool> {
    if visible_count(target,air)==0{return Some(false);}
    if visible_count(source,air)==0 || target.scalars.item!=source.scalars.item{return Some(false);}
    if let Some(roles)=super::components::roles() {
        if !inventory_same(target,source,air)? {return Some(false);}
        if !super::components::stackable_with(target,roles)? {return Some(false);}
        let max=super::components::max_size_with(target,roles)?;
        return Some(visible_count(target,air)<99.min(max));
    }
    let (proto,defaults)=pristine(target,false)?;let (source_proto,source_defaults)=pristine(source,false)?;
    if proto!=source_proto || !Arc::ptr_eq(defaults,source_defaults){return None;}
    let defaults=defaults.lock().unwrap();let max=max_size(&defaults)?;
    if !defaults.scalar_valid(4){return None;}
    let damageable=defaults.scalar(2).is_some() && defaults.scalar(4).is_none() && defaults.scalar(3).is_some();
    let damaged=defaults.scalar(3).unwrap_or(0).max(0).min(defaults.scalar(2).unwrap_or(0))>0;
    Some(max>1 && (!damageable || !damaged) && visible_count(target,air)<99.min(max))
}
pub fn inventory_capacity(target:&Stack)->Option<i32> {
    if let Some(roles)=super::components::roles() {return Some(99.min(super::components::max_size_with(target,roles)?));}
    let (_,defaults)=pristine(target,false)?;let defaults=defaults.lock().unwrap();
    if !defaults.scalar_valid(1){return None;}Some(99.min(defaults.scalar(1).unwrap_or(1)))
}

/// Same live component structure without foreign equality. Different prototype
/// identities can still compare equal and therefore require the original effect.
pub fn inventory_same(left:&Stack,right:&Stack,air:u64)->Option<bool> {
    inventory_same_with_roles(left,right,air,super::components::roles())
}
fn inventory_same_with_roles(left:&Stack,right:&Stack,air:u64,roles:Option<&super::components::Roles>)->Option<bool> {
    let left_empty=visible_count(left,air)==0;let right_empty=visible_count(right,air)==0;
    if left_empty || right_empty {return Some(left_empty && right_empty);}
    if left.scalars.item!=right.scalars.item{return Some(false);}
    if let Some(roles)=roles {
        let a=left.components.lock().unwrap();
        let a_prototype=a.prototype;let a_defaults=a.defaults.clone();let a_patch=a.view_patch();drop(a);
        let b=right.components.lock().unwrap();
        let controlled=match (&a_defaults,&b.defaults) {
            (Some(a),Some(b))=>Arc::ptr_eq(a,b),
            (None,None)=>a_prototype==roles.empty_prototype,_=>false,
        };
        if controlled && a_prototype==b.prototype {if let Some(equal)=a_patch.pure_equal(&b.view_patch()){return Some(equal);}}
    }
    let (a,ad)=pristine(left,false)?;let (b,bd)=pristine(right,false)?;
    if a==b && Arc::ptr_eq(ad,bd){Some(true)}else{None}
}

pub enum InventorySpaceScan {Found(i32),Effect(i32),Done}
/// One pure main-inventory segment. Borrow canonical rows and their actual
/// default owner once; a foreign predicate returns BEFORE any Java effect.
pub fn inventory_space_scan(rows:&Slots,source:&Stack,air:u64,mut index:usize)->InventorySpaceScan {
    let managed=pristine(source,false);let mut held=None;
    while index<36 {
        let target=&rows.entries[index].stack;
        if visible_count(target,air)!=0 && visible_count(source,air)!=0 && target.scalars.item==source.scalars.item {
            let Some((source_proto,source_defaults))=managed else{return InventorySpaceScan::Effect(index as i32)};
            let Some((proto,defaults))=pristine(target,false) else{return InventorySpaceScan::Effect(index as i32)};
            if proto!=source_proto || !Arc::ptr_eq(defaults,source_defaults){return InventorySpaceScan::Effect(index as i32);}
            if held.is_none(){held=Some(source_defaults.lock().unwrap());}
            let defaults=held.as_ref().unwrap();let Some(max)=max_size(defaults) else{return InventorySpaceScan::Effect(index as i32)};
            if !defaults.scalar_valid(4){return InventorySpaceScan::Effect(index as i32);}
            let damageable=defaults.scalar(2).is_some() && defaults.scalar(4).is_none() && defaults.scalar(3).is_some();
            let damaged=defaults.scalar(3).unwrap_or(0).max(0).min(defaults.scalar(2).unwrap_or(0))>0;
            if max>1 && (!damageable || !damaged) && visible_count(target,air)<99.min(max){return InventorySpaceScan::Found(index as i32);}
        }
        index+=1;
    }
    InventorySpaceScan::Done
}
