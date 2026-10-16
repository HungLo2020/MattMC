//! Temporary CPU identity pins for legacy Java objects. Map policy and typed
//! layer ownership stay native; Java only holds the corresponding object slots.
use std::collections::{HashMap, VecDeque};
use super::super::layers::Layer;
use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, Weak};

#[repr(C)]
pub struct Metadata { pub retired: AtomicU32 }
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Phase { Free, Reserved, Active, Pending, AwaitingAck }
struct Slot { phase: Phase, native_layer: Option<(Arc<Layer>,u64)>, entry_identity: u64 }
struct Slots {
    // The slot record owns its complete lifecycle and optional actual layer.
    // Index zero is never a CPU binding. Free IDs retain original LIFO reuse;
    // retirement remains FIFO and cannot make an ID reusable before Java ack.
    records: Vec<Slot>,
    free: Vec<u32>,
    pending: VecDeque<u32>,
    entries: HashMap<u64, (u32, Weak<super::Entry>)>,
}
impl Slots {
    fn reserve(&mut self)->Option<u32>{
        if let Some(id)=self.free.pop(){
            let slot=&mut self.records[id as usize];assert_eq!(slot.phase,Phase::Free);
            slot.phase=Phase::Reserved;Some(id)
        }else{
            let id=u32::try_from(self.records.len()).ok()?;
            self.records.try_reserve(1).ok()?;
            self.records.push(Slot{phase:Phase::Reserved,native_layer:None,entry_identity:0});Some(id)
        }
    }
    fn transition(&mut self,id:u32,from:Phase,to:Phase){
        let slot=&mut self.records[id as usize];assert_eq!(slot.phase,from);slot.phase=to;
    }
}
pub struct Registry {
    pub metadata: Metadata,
    slots: Mutex<Slots>,
    identities: AtomicU64,
}
impl Registry {
    pub fn new()->Arc<Self>{
        Arc::new(Self{metadata:Metadata{retired:AtomicU32::new(0)},
            slots:Mutex::new(Slots{records:vec![Slot{phase:Phase::Free,native_layer:None,entry_identity:0}],free:Vec::new(),pending:VecDeque::new(),entries:HashMap::new()}),
            identities:AtomicU64::new(1)})
    }
    pub fn reserve(self:&Arc<Self>)->Option<Reservation>{
        let slot=self.slots.lock().unwrap().reserve()?;
        Some(Reservation{registry:Arc::clone(self),slot,installed:false})
    }
    pub(super) fn native_layer(self:&Arc<Self>,layer:Arc<Layer>)->Option<Arc<super::Entry>>{
        let identity=self.identity()?;let pin=self.reserve()?.install();
        self.slots.lock().unwrap().records[pin.slot as usize].native_layer=Some((Arc::clone(&layer),identity));
        let entry=Arc::new(super::Entry{id:pin.slot,identity,layer:Some(layer),_pin:Some(pin)});
        self.bind_entry(&entry);Some(entry)
    }
    pub(super) fn bind_entry(&self, entry:&Arc<super::Entry>) {
        let mut slots=self.slots.lock().unwrap();
        slots.records[entry.id as usize].entry_identity=entry.identity;
        slots.entries.insert(entry.identity,(entry.id,Arc::downgrade(entry)));
    }
    pub(super) fn existing_entry(&self, identity:u64, layer:*const Layer)->Option<Arc<super::Entry>> {
        // Drop rejected upgrades outside the registry guard: the last actual
        // Entry can retire its pin here, which takes that same guard.
        let entry={self.slots.lock().unwrap().entries.get(&identity)?.1.upgrade()?};
        let matches=match entry.layer.as_ref() {
            Some(current) if !layer.is_null()=>Arc::as_ptr(current)==layer,
            Some(current)=>!current.is_valid(),
            None=>layer.is_null(),
        };
        matches.then_some(entry)
    }
    pub(super) fn owns_entry(&self,entry:&super::Entry)->bool {
        entry._pin.as_ref().is_some_and(|pin|std::ptr::eq(self,Arc::as_ptr(&pin.registry)))
    }
    fn identities(&self,count:u64)->Option<u64>{
        self.identities.fetch_update(Ordering::Relaxed,Ordering::Relaxed,|value|value.checked_add(count)).ok()
    }
    pub(super) fn identity(&self)->Option<u64>{self.identities(1)}
    pub(super) fn layer_for_slot(&self,slot:u32)->Option<(Arc<Layer>,u64)>{
        self.slots.lock().unwrap().records.get(slot as usize)?.native_layer.clone()
    }
    fn retire(&self,slot:u32){
        let mut state=self.slots.lock().unwrap();state.transition(slot,Phase::Active,Phase::Pending);
        let identity=state.records[slot as usize].entry_identity;
        if state.entries.get(&identity).is_some_and(|entry|entry.0==slot) {state.entries.remove(&identity);}
        state.pending.push_back(slot);self.metadata.retired.fetch_add(1,Ordering::Release);
    }
    pub fn take_retired_into(&self,output:&mut[u32])->usize{
        let mut state=self.slots.lock().unwrap();let count=output.len().min(state.pending.len());
        for target in &mut output[..count]{let slot=state.pending.pop_front().unwrap();state.transition(slot,Phase::Pending,Phase::AwaitingAck);*target=slot;}
        count
    }
    #[cfg(test)]
    fn take_retired(&self,limit:usize)->Vec<u32>{let mut output=vec![0;limit];let count=self.take_retired_into(&mut output);output.truncate(count);output}
    /// Validate the entire batch before clearing native roots or enabling reuse.
    /// Java must clear every corresponding heap binding before this call.
    pub fn acknowledge(&self,slots:&[u32])->bool{
        if slots.len()>64{return false;}
        let mut sorted=[0u32;64];sorted[..slots.len()].copy_from_slice(slots);sorted[..slots.len()].sort_unstable();
        let mut state=self.slots.lock().unwrap();
        if sorted[..slots.len()].windows(2).any(|pair|pair[0]==pair[1])
            ||slots.iter().any(|&slot|state.records.get(slot as usize).is_none_or(|record|record.phase!=Phase::AwaitingAck)){return false;}
        for &slot in slots{
            state.transition(slot,Phase::AwaitingAck,Phase::Free);
            state.records[slot as usize].native_layer=None;state.records[slot as usize].entry_identity=0;state.free.push(slot);
        }
        self.metadata.retired.fetch_sub(slots.len() as u32,Ordering::Release);true
    }
}
/// Immutable CPU binding descriptors belong to the same native reservation.
/// A proposed identity is used only if Java has no active binding for that object;
/// unused opaque identities are never reused and contain no Java-domain state.
#[repr(C)]
#[derive(Clone,Copy,Debug,PartialEq,Eq)]
pub struct ReservationDescriptor { pub slot:u32, padding:u32, pub identity:u64 }
#[repr(C)]
pub struct ReservationBatchMetadata { pub count:u64, pub slots:u64 }
pub struct ReservationBatch {
    registry:Arc<Registry>,
    slots:Box<[ReservationDescriptor]>,
    cells:Mutex<Vec<Option<Reservation>>>,
    pub metadata:ReservationBatchMetadata,
}
impl ReservationBatch {
    pub fn new(registry:&Arc<Registry>,limit:usize)->Option<Arc<Self>>{
        if limit==0||limit>64{return None;}
        let mut cells=Vec::new();cells.try_reserve_exact(limit).ok()?;
        let mut ids=Vec::new();ids.try_reserve_exact(limit).ok()?;
        {
            let mut state=registry.slots.lock().unwrap();
            for _ in 0..limit{
                let Some(slot)=state.reserve() else{break;};
                ids.push(ReservationDescriptor{slot,padding:0,identity:0});
                cells.push(Some(Reservation{registry:Arc::clone(registry),slot,installed:false}));
            }
        }
        if cells.is_empty(){return None;}
        // Checked all-or-nothing range: exhaustion cancels every reserved cell.
        let first=registry.identities(cells.len() as u64)?;
        for (offset,id) in ids.iter_mut().enumerate(){id.identity=first+offset as u64;}
        let slots=ids.into_boxed_slice();let metadata=ReservationBatchMetadata{count:slots.len() as u64,slots:slots.as_ptr() as u64};
        Some(Arc::new(Self{registry:Arc::clone(registry),slots,cells:Mutex::new(cells),metadata}))
    }
    pub fn belongs_to(&self,registry:&Arc<Registry>)->bool{Arc::ptr_eq(&self.registry,registry)}
    pub fn take(&self,index:usize)->Option<Reservation>{self.cells.lock().unwrap().get_mut(index)?.take()}
    pub fn cancel(&self,index:usize)->bool{let value=self.take(index);let present=value.is_some();drop(value);present}
    pub fn len(&self)->usize{self.slots.len()}
}
pub struct Reservation { pub registry:Arc<Registry>,pub slot:u32,installed:bool }
impl Reservation {
    pub fn install(mut self)->Pin{
        self.registry.slots.lock().unwrap().transition(self.slot,Phase::Reserved,Phase::Active);
        self.installed=true;Pin{registry:Arc::clone(&self.registry),slot:self.slot}
    }
}
impl Drop for Reservation {
    fn drop(&mut self){if !self.installed{let mut state=self.registry.slots.lock().unwrap();state.transition(self.slot,Phase::Reserved,Phase::Free);state.free.push(self.slot);}}
}
pub struct Pin { registry:Arc<Registry>,pub slot:u32 }
impl Drop for Pin {fn drop(&mut self){self.registry.retire(self.slot);}}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn actual_entry_is_reused_by_object_identity_without_owning_it_or_equating_payloads() {
        let registry=Registry::new();let layer=Arc::new(Layer::new(3));
        let first=registry.native_layer(Arc::clone(&layer)).unwrap();
        let identity=first.identity;let slot=first.id;let weak=Arc::downgrade(&first);
        let retained=registry.existing_entry(identity,Arc::as_ptr(&layer)).unwrap();
        assert!(Arc::ptr_eq(&first,&retained));
        assert!(registry.existing_entry(identity+100,Arc::as_ptr(&layer)).is_none());
        assert!(registry.existing_entry(identity,std::ptr::null()).is_none());
        layer.invalidate();assert!(Arc::ptr_eq(&first,&registry.existing_entry(identity,std::ptr::null()).unwrap()));
        layer.fill(9);assert!(registry.existing_entry(identity,std::ptr::null()).is_none());
        assert!(Arc::ptr_eq(&first,&registry.existing_entry(identity,Arc::as_ptr(&layer)).unwrap()));
        drop(first);assert!(weak.upgrade().is_some());drop(retained);
        assert!(weak.upgrade().is_none(),"registry lookup cannot form an owner cycle");
        assert!(registry.existing_entry(identity,Arc::as_ptr(&layer)).is_none());
        assert_eq!(registry.take_retired(64),[slot]);assert!(registry.acknowledge(&[slot]));
    }
    #[test]
    fn opaque_promotion_keeps_old_entry_roots_and_old_retirement_cannot_remove_new_identity() {
        let registry=Registry::new();let identity=registry.identity().unwrap();
        let pin=registry.reserve().unwrap().install();
        let old=Arc::new(super::super::Entry{id:pin.slot,identity,layer:None,_pin:Some(pin)});
        registry.bind_entry(&old);assert!(Arc::ptr_eq(&old,&registry.existing_entry(identity,std::ptr::null()).unwrap()));
        let layer=Arc::new(Layer::new(8));
        assert!(registry.existing_entry(identity,Arc::as_ptr(&layer)).is_none());
        let pin=registry.reserve().unwrap().install();
        let new=Arc::new(super::super::Entry{id:pin.slot,identity,layer:Some(Arc::clone(&layer)),_pin:Some(pin)});
        registry.bind_entry(&new);assert!(registry.existing_entry(identity,std::ptr::null()).is_none());
        drop(old);assert!(Arc::ptr_eq(&new,&registry.existing_entry(identity,Arc::as_ptr(&layer)).unwrap()));
        drop(new);assert!(registry.slots.lock().unwrap().entries.is_empty());
    }
    #[test]
    fn canceled_handoff_reuses_slot_without_retiring_a_java_object() {
        let registry = Registry::new();
        let reservation = registry.reserve().unwrap();
        let id = reservation.slot;
        drop(reservation);
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 0);
        assert_eq!(registry.reserve().unwrap().slot, id);
    }
    #[test]
    fn copies_and_cache_pins_retire_only_after_the_last_reference() {
        let registry = Registry::new();
        let pin = Arc::new(registry.reserve().unwrap().install());
        let id = pin.slot;
        let cache = Arc::clone(&pin);
        let snapshot = Arc::clone(&pin);
        drop(pin);
        drop(snapshot);
        assert!(registry.take_retired(64).is_empty());
        drop(cache);
        assert_eq!(registry.take_retired(64), [id]);
        // The object remains pinned on the Java side until this batch is acked.
        let other = registry.reserve().unwrap();
        assert_ne!(other.slot, id);
        assert!(registry.acknowledge(&[id]));
        assert_eq!(registry.reserve().unwrap().slot, id);
    }
    #[test]
    fn invalid_acknowledgement_is_atomic_and_drain_is_bounded() {
        let registry = Registry::new();
        let pins: Vec<_> = (0..130)
            .map(|_| registry.reserve().unwrap().install())
            .collect();
        drop(pins);
        let first = registry.take_retired(64);
        assert_eq!(first.len(), 64);
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 130);
        assert!(!registry.acknowledge(&[first[0], first[0]]));
        assert!(!registry.acknowledge(&[first[0], u32::MAX]));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 130);
        assert!(registry.acknowledge(&first));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 66);
        let second = registry.take_retired(64);
        assert!(registry.acknowledge(&second));
        let last = registry.take_retired(64);
        assert_eq!(last.len(), 2);
        assert!(registry.acknowledge(&last));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 0);
    }
    #[test]
    fn repeated_retirement_keeps_all_slot_tables_bounded() {
        let registry = Registry::new();
        for _ in 0..100_000 {
            drop(registry.reserve().unwrap().install());
            let retired = registry.take_retired(64);
            assert_eq!(retired.len(), 1);
            assert!(registry.acknowledge(&retired));
        }
        let state = registry.slots.lock().unwrap();
        assert_eq!(state.records.len(), 2);
        assert_eq!(state.free, [1]);
        assert!(
            state.records.iter().all(|record|record.phase==Phase::Free && record.native_layer.is_none())
                && state.pending.is_empty()
        );
    }
}

#[cfg(test)]
mod reservation_batch_tests {
    use super::*;
    #[test]
    fn cells_are_claimed_once_cancelled_once_and_header_keeps_actual_ids() {
        let registry=Registry::new();let batch=ReservationBatch::new(&registry,4).unwrap();
        let descriptors=batch.slots.to_vec();let ids:Vec<_>=descriptors.iter().map(|d|d.slot).collect();assert_eq!(batch.metadata.count,4);assert_eq!(batch.metadata.slots,batch.slots.as_ptr() as u64);
        let reservation=batch.take(0).unwrap();assert_eq!(reservation.slot,ids[0]);let pin=reservation.install();
        assert!(batch.take(0).is_none());assert!(!batch.cancel(0));assert!(batch.cancel(1));assert!(!batch.cancel(1));assert!(batch.take(4).is_none());
        assert_eq!(batch.slots.as_ref(),descriptors.as_slice());drop(batch);
        let held:Vec<_>=(0..3).map(|_|registry.reserve().unwrap()).collect();
        let available:std::collections::HashSet<_>=held.iter().map(|r|r.slot).collect();
        assert_eq!(available,ids[1..].iter().copied().collect());
        drop(pin);let mut retired=[0];assert_eq!(registry.take_retired_into(&mut retired),1);assert_eq!(retired[0],ids[0]);assert!(registry.acknowledge(&retired));
    }
    #[test]
    fn unused_cells_retain_registry_then_cancel_without_installing_cpu_roots() {
        let registry=Registry::new();let weak=Arc::downgrade(&registry);let batch=ReservationBatch::new(&registry,64).unwrap();
        assert!(ReservationBatch::new(&registry,0).is_none());assert!(ReservationBatch::new(&registry,65).is_none());
        assert_eq!(registry.slots.lock().unwrap().records.iter().filter(|s|s.phase==Phase::Reserved).count(),64);assert!(registry.slots.lock().unwrap().records.iter().all(|s|s.phase!=Phase::Active));
        drop(registry);assert!(weak.upgrade().is_some());drop(batch);assert!(weak.upgrade().is_none());
    }
}

#[cfg(test)]
mod slot_lifecycle_tests {
    use super::*;
    #[test]
    fn reservation_identities_are_disjoint_and_exhaustion_rolls_back_all_slots(){
        let registry=Registry::new();let individual=registry.identity().unwrap();
        let first=ReservationBatch::new(&registry,4).unwrap();let second=ReservationBatch::new(&registry,4).unwrap();
        let mut identities=std::collections::HashSet::from([individual,registry.identity().unwrap()]);
        for batch in [&first,&second]{for descriptor in batch.slots.iter(){assert_ne!(descriptor.identity,0);assert!(identities.insert(descriptor.identity));}}
        registry.identities.store(u64::MAX-1,Ordering::Relaxed);
        assert!(ReservationBatch::new(&registry,4).is_none());
        assert_eq!(registry.identities.load(Ordering::Relaxed),u64::MAX-1);
        assert_eq!(registry.slots.lock().unwrap().records.iter().filter(|r|r.phase==Phase::Reserved).count(),8);
        assert_eq!(registry.identity(),Some(u64::MAX-1));assert_eq!(registry.identity(),None);
        drop(first);drop(second);
        assert!(registry.slots.lock().unwrap().records.iter().all(|r|r.phase==Phase::Free));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire),0);
    }
    #[test]
    fn actual_layer_root_survives_retirement_until_the_matching_ack(){
        let registry=Registry::new();let layer=Arc::new(Layer::new(7));let weak=Arc::downgrade(&layer);
        let entry=registry.native_layer(layer).unwrap();let id=entry.id;drop(entry);
        assert!(weak.upgrade().is_some());let retired=registry.take_retired(64);assert_eq!(retired,[id]);
        assert!(registry.layer_for_slot(id).is_some());assert!(!registry.acknowledge(&[id,id]));assert!(weak.upgrade().is_some());
        assert!(registry.acknowledge(&retired));assert!(weak.upgrade().is_none());assert!(registry.layer_for_slot(id).is_none());
        assert!(!registry.acknowledge(&retired));assert_eq!(registry.reserve().unwrap().slot,id);
    }
    #[test]
    fn simultaneous_native_cell_claims_or_cancels_have_one_winner(){
        let registry=Registry::new();let batch=ReservationBatch::new(&registry,1).unwrap();
        let barrier=Arc::new(std::sync::Barrier::new(8));
        let workers:Vec<_>=(0..8).map(|i|{let batch=Arc::clone(&batch);let barrier=Arc::clone(&barrier);
            std::thread::spawn(move||{barrier.wait();if i%2==0{batch.cancel(0)}else{batch.take(0).is_some()}})}).collect();
        assert_eq!(workers.into_iter().map(|worker|usize::from(worker.join().unwrap())).sum::<usize>(),1);
        assert!(registry.slots.lock().unwrap().records.iter().all(|r|r.phase==Phase::Free));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire),0);
    }
}
