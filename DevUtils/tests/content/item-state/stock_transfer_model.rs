#[path = "../../../../src/main/rust/content/item/runtime/menu.rs"] mod menu;
#[path = "../../../../src/main/rust/content/item/runtime/components.rs"] mod components;
#[path = "../../../../src/main/rust/content/item/runtime/jvm_roots.rs"] mod jvm_roots;
#[path = "../../../../src/main/rust/content/item/runtime/container.rs"] mod container;
#[path = "../../../../src/main/rust/content/item/runtime/inventory.rs"] mod inventory;
// Real native transfer owner tests; no ported implementation in the oracle.
#[path = "../../../../src/main/rust/content/item/runtime/patch.rs"] mod patch;
#[path = "../../../../src/main/rust/content/item/runtime/stack.rs"] mod stack;
#[path = "../../../../src/main/rust/content/item/runtime/slots.rs"] mod slots;
#[path = "../../../../src/main/rust/content/item/runtime/ffi.rs"] mod ffi;
use std::sync::{Arc,Mutex};use std::sync::atomic::Ordering;
use patch::{Overlay,defaults::{Store,Row}};use stack::Stack;use slots::{Slots,Entry};
// Inject real allocator failures only on the selected test thread. Production
// code is imported unchanged; successful reallocations move so stale published
// pointers cannot be hidden by an in-place system realloc.
mod allocation_failure {
    use std::alloc::{GlobalAlloc,Layout,System};
    use std::cell::Cell;
    #[derive(Clone,Copy)]
    struct Plan { remaining:usize,calls:usize,moved_from:usize }
    thread_local! { static PLAN:Cell<Plan>=const {Cell::new(Plan {remaining:0,calls:0,moved_from:0})}; }
    pub struct Allocator;
    fn fail()->bool {
        PLAN.try_with(|slot| {
            let mut plan=slot.get();if plan.remaining==0{return false;}
            plan.calls+=1;plan.remaining-=1;let fail=plan.remaining==0;slot.set(plan);fail
        }).unwrap_or(false)
    }
    unsafe impl GlobalAlloc for Allocator {
        unsafe fn alloc(&self,layout:Layout)->*mut u8 {if fail(){std::ptr::null_mut()}else{System.alloc(layout)}}
        unsafe fn alloc_zeroed(&self,layout:Layout)->*mut u8 {if fail(){std::ptr::null_mut()}else{System.alloc_zeroed(layout)}}
        unsafe fn dealloc(&self,ptr:*mut u8,layout:Layout){System.dealloc(ptr,layout)}
        unsafe fn realloc(&self,ptr:*mut u8,layout:Layout,size:usize)->*mut u8 {
            let move_it=PLAN.try_with(|slot|slot.get().remaining!=0).unwrap_or(false);
            if fail(){return std::ptr::null_mut();}
            if !move_it{return System.realloc(ptr,layout,size);}
            let next=System.alloc(Layout::from_size_align_unchecked(size,layout.align()));
            if !next.is_null(){
                std::ptr::copy_nonoverlapping(ptr,next,layout.size().min(size));System.dealloc(ptr,layout);
                let _=PLAN.try_with(|slot|{let mut plan=slot.get();plan.moved_from=ptr as usize;slot.set(plan);});
            }
            next
        }
    }
    pub fn begin(nth:usize){PLAN.with(|slot|slot.set(Plan {remaining:nth,calls:0,moved_from:0}));}
    pub fn finish()->(usize,usize){PLAN.with(|slot|{let plan=slot.replace(Plan {remaining:0,calls:0,moved_from:0});(plan.calls,plan.moved_from)})}
}
#[global_allocator]
static TEST_ALLOCATOR:allocation_failure::Allocator=allocation_failure::Allocator;
#[test]
fn failed_second_native_reservation_cannot_leave_a_published_view_pointing_at_a_freed_entry_buffer() {
    let patch=patch::Patch::default();
    for key in 1..=4 {patch.put(patch::Key(key),patch::Value::Present(patch::Leaf(key)));}
    let view=patch.view_address() as *const std::sync::atomic::AtomicUsize;
    allocation_failure::begin(2);
    let result=patch.put_payload(patch::Key(5),patch::Value::Present(patch::Leaf(5)),Some(components::Payload::Integer(7)));
    let (calls,freed)=allocation_failure::finish();
    assert_eq!(calls,2);assert!(result.is_err());assert_eq!(patch.len(),4);assert_eq!(patch.get(patch::Key(5)),None);
    let published=unsafe {(*view).load(Ordering::Acquire)};
    if freed!=0 {assert_ne!(published,freed,"failed reservation exposed a freed canonical entry buffer");}
    assert_eq!(unsafe {(*view.add(1)).load(Ordering::Acquire)},4);
    for key in 1..=4 {assert_eq!(patch.get(patch::Key(key)),Some(patch::Value::Present(patch::Leaf(key))));}
}
#[test]
fn failed_native_payload_reservations_release_staged_actual_enchantment_leases_and_preserve_members() {
    for nth in [1,2] {
        let patch=patch::Patch::default();
        for key in 1..=4 {patch.put(patch::Key(key),patch::Value::Present(patch::Leaf(key)));}
        let value=Arc::new(components::Enchantments::new(32));let weak=Arc::downgrade(&value);
        allocation_failure::begin(nth);
        let result=patch.put_payload(patch::Key(5),patch::Value::Present(patch::Leaf(5)),Some(components::Payload::Enchantments(value)));
        let (calls,freed)=allocation_failure::finish();
        assert_eq!(calls,nth);assert!(result.is_err());assert!(weak.upgrade().is_none());
        assert_eq!(patch.len(),4);assert_eq!(patch.get(patch::Key(5)),None);
        let view=patch.view_address() as *const std::sync::atomic::AtomicUsize;
        if freed!=0 {assert_ne!(unsafe{(*view).load(Ordering::Acquire)},freed);}
        for key in 1..=4 {assert_eq!(patch.get(patch::Key(key)),Some(patch::Value::Present(patch::Leaf(key))));}
    }
}
#[test]
fn actual_component_payloads_follow_shared_backing_then_detach_at_the_existing_cow_point() {
    use components::Payload;
    let source = patch::Patch::default();
    source.put_payload(patch::Key(1), patch::Value::Present(patch::Leaf(1)), Some(Payload::Integer(-9))).unwrap();
    let shared = source.clone(); let detached = source.detached();
    source.put_payload(patch::Key(1), patch::Value::Present(patch::Leaf(2)), Some(Payload::Integer(255))).unwrap();
    assert!(matches!(shared.get_payload(patch::Key(1)), Some((patch::Value::Present(patch::Leaf(2)), Some(Payload::Integer(255))))));
    assert!(matches!(detached.get_payload(patch::Key(1)), Some((patch::Value::Present(patch::Leaf(1)), Some(Payload::Integer(-9))))));
    detached.put(patch::Key(1), patch::Value::Removed);
    assert!(matches!(detached.get_payload(patch::Key(1)), Some((patch::Value::Removed, None))));
    assert!(matches!(source.get_payload(patch::Key(1)), Some((_, Some(Payload::Integer(255))))));
}
#[test]
fn enchantment_payload_retains_the_actual_allocation_through_copy_and_retires_at_last_native_member() {
    use components::{Payload,Enchantments};
    let enchantments = Arc::new(Enchantments::new(32)); let weak = Arc::downgrade(&enchantments);
    let patch = patch::Patch::default();
    patch.put_payload(patch::Key(1), patch::Value::Present(patch::Leaf(1)), Some(Payload::Enchantments(enchantments.clone()))).unwrap();
    let detached = patch.detached();
    enchantments.insert_at(32, 0, 2).unwrap();
    match detached.get_payload(patch::Key(1)) {
        Some((_, Some(Payload::Enchantments(actual)))) => { assert!(Arc::ptr_eq(&actual, &enchantments)); assert_eq!(actual.size(), 1); },
        _ => panic!("lost actual enchantment payload"),
    }
    drop(enchantments); patch.remove(patch::Key(1)); drop(patch);
    assert!(weak.upgrade().is_some());
    detached.clear();
    assert!(weak.upgrade().is_none());
}
#[test]
fn staged_native_component_cow_keeps_payload_and_source_version_until_matching_vm_adoption() {
    use components::Payload;
    let source = patch::Patch::default();
    source.put_payload(patch::Key(1), patch::Value::Present(patch::Leaf(1)), Some(Payload::Integer(12))).unwrap();
    let mut overlay = Overlay::from_sanitized(3, source.clone());
    let original = overlay.patch_version(); let prepared = overlay.stage_change().unwrap();
    assert!(matches!(prepared.get_payload(patch::Key(1)), Some((_, Some(Payload::Integer(12))))));
    // Simulate failed VM-root preparation by dropping the unpublished backing.
    drop(prepared); assert_eq!(overlay.patch_version(), original);
    assert!(matches!(overlay.view_patch().get_payload(patch::Key(1)), Some((_, Some(Payload::Integer(12))))));
    let prepared = overlay.stage_change().unwrap(); assert!(overlay.commit_change(original, prepared));
    assert_ne!(overlay.patch_version(), original);
    assert!(matches!(overlay.view_patch().get_payload(patch::Key(1)), Some((_, Some(Payload::Integer(12))))));
}
fn payload_roles() -> components::Roles {
    components::Roles { values: std::array::from_fn(|index|components::Role { key: patch::Key(101 + index as u64), hash: 101 + index as u32 }),
        empty_prototype: 999, empty_enchantments: Arc::new(components::Enchantments::new(32)) }
}
#[test]
fn typed_patch_equality_preserves_opaque_left_dispatch_and_never_skips_an_unknown_pair() {
    use components::Payload;
    let left=patch::Patch::default();let right=patch::Patch::default();
    left.put_payload(patch::Key(1),patch::Value::Present(patch::Leaf(1)),Some(Payload::Integer(7))).unwrap();
    right.put_payload(patch::Key(1),patch::Value::Present(patch::Leaf(2)),Some(Payload::Integer(7))).unwrap();
    assert_eq!(left.pure_equal(&right),Some(true));
    right.put(patch::Key(1),patch::Value::Present(patch::Leaf(3)));
    assert_eq!(left.pure_equal(&right),Some(false));assert_eq!(right.pure_equal(&left),None);
    left.put(patch::Key(1),patch::Value::Removed);
    assert_eq!(left.pure_equal(&right),Some(false));assert_eq!(right.pure_equal(&left),None);
    left.put_payload(patch::Key(2),patch::Value::Present(patch::Leaf(4)),Some(Payload::Integer(1))).unwrap();
    right.put_payload(patch::Key(2),patch::Value::Present(patch::Leaf(5)),Some(Payload::Integer(2))).unwrap();
    assert_eq!(right.pure_equal(&left),None);
}
#[test]
fn typed_patch_equality_uses_actual_enchantment_identity_or_empty_contents_before_declining_holder_callbacks() {
    use components::{Payload,Enchantments};
    let left=patch::Patch::default();let right=patch::Patch::default();
    let first=Arc::new(Enchantments::new(32));let second=Arc::new(Enchantments::new(32));
    left.put_payload(patch::Key(1),patch::Value::Present(patch::Leaf(1)),Some(Payload::Enchantments(first.clone()))).unwrap();
    right.put_payload(patch::Key(1),patch::Value::Present(patch::Leaf(2)),Some(Payload::Enchantments(second.clone()))).unwrap();
    assert_eq!(left.pure_equal(&right),Some(true));first.insert_at(32,0,3).unwrap();
    assert_eq!(left.pure_equal(&right),Some(false));second.insert_at(32,0,3).unwrap();
    assert_eq!(left.pure_equal(&right),None);
    right.put_payload(patch::Key(1),patch::Value::Present(patch::Leaf(3)),Some(Payload::Enchantments(first))).unwrap();
    assert_eq!(left.pure_equal(&right),Some(true));
}
fn payload_override(stack: &Stack, role: components::Role, leaf: u64, payload: Option<components::Payload>) {
    let mut owner = stack.components.lock().unwrap(); owner.prepare_change();
    owner.set_after_compare_payload(role.key, Some(patch::Leaf(leaf)), None, false, payload).unwrap();
}
#[test]
fn actual_typed_menu_continuation_merges_then_returns_the_original_empty_insert_amount() {
    let roles=payload_roles();let source=Stack::from_overlay(7,11,Overlay::new(roles.empty_prototype));
    payload_override(&source,roles.values[0],1,Some(components::Payload::Integer(64)));
    let target=Arc::new(Stack::from_overlay(7,60,source.components.lock().unwrap().copy()));
    let d=defaults();let mut rows=Slots::new(27,empty(&d));rows.set(0,Entry{facade:2,stack:target.clone()});
    let mut stats=slots::TransferStats::default();
    let merged=slots::transfer_payload_step(&rows,&source,99,0,0,&mut stats,false,&roles);
    assert_eq!(merged&7,1);assert_eq!((merged>>3)&63,0);
    assert_eq!(source.scalars.count.load(Ordering::Relaxed),7);assert_eq!(target.scalars.count.load(Ordering::Relaxed),64);
    let insert=slots::transfer_payload_step(&rows,&source,99,1,1,&mut stats,false,&roles);
    assert_eq!(insert&7,2);assert_eq!((insert>>3)&63,1);assert_eq!(insert>>32,7);
}
#[test]
fn native_component_consumers_read_actual_typed_prototype_and_live_shared_enchantment_payloads() {
    use components::Payload;
    let roles = payload_roles(); let enchantments = Arc::new(components::Enchantments::new(32));
    let mut defaults = Store::new(6);
    for (index, payload) in [Some(Payload::Integer(64)),Some(Payload::Integer(100)),Some(Payload::Integer(5)),Some(Payload::Null),Some(Payload::Enchantments(enchantments.clone())),Some(Payload::Null)].into_iter().enumerate() {
        let role = roles.values[index];
        defaults.put_payload(Row { key:role.key.0, leaf:index as i64+1, hash:role.hash, kind:index as u32+1, scalar:0, reserved:0 },payload).unwrap();
    }
    let mut owner = Overlay::new(17); owner.defaults = Some(Arc::new(slots::BoundaryOwner::new(defaults)));
    let stack = Stack::from_overlay(7, 4, owner);
    assert_eq!(components::max_size_with(&stack,&roles),Some(64));
    assert_eq!(components::damaged_with(&stack,&roles),Some(true));
    assert_eq!(components::stackable_with(&stack,&roles),Some(false));
    payload_override(&stack,roles.values[2],20,Some(Payload::Integer(0)));
    assert_eq!(components::damaged_with(&stack,&roles),Some(false));
    assert_eq!(components::crafting_with(&stack,&roles),Some(true));
    enchantments.insert_at(32,0,0).unwrap(); // A zero-level member still means enchanted.
    assert_eq!(components::enchanted_with(&stack,&roles),Some(true));
    assert_eq!(components::crafting_with(&stack,&roles),Some(false));
    enchantments.clear();
    assert_eq!(components::enchanted_with(&stack,&roles),Some(false));
    payload_override(&stack,roles.values[5],21,None); // Opaque non-null custom name is presence, not a cached flag.
    assert_eq!(components::crafting_with(&stack,&roles),Some(false));
}
#[test]
fn native_scalar_reads_preserve_negative_clamp_and_defer_wrong_type_to_original_vm_error() {
    use components::Payload;
    let roles=payload_roles();let stack=Stack::from_overlay(7,1,Overlay::new(roles.empty_prototype));
    assert_eq!(components::max_size_with(&stack,&roles),Some(1));
    payload_override(&stack,roles.values[0],1,Some(Payload::Integer(64)));
    payload_override(&stack,roles.values[1],2,Some(Payload::Integer(-5)));
    payload_override(&stack,roles.values[2],3,Some(Payload::Integer(7)));
    assert_eq!(components::damaged_with(&stack,&roles),Some(false));
    assert_eq!(components::stackable_with(&stack,&roles),Some(true));
    payload_override(&stack,roles.values[1],4,None);
    assert_eq!(components::damaged_with(&stack,&roles),None);
    payload_override(&stack,roles.values[3],5,None);
    assert_eq!(components::damaged_with(&stack,&roles),Some(false)); // Unbreakable short-circuits before the bad cast.
}
#[test]
fn native_component_reader_declines_foreign_prototype_before_effect_but_uses_explicit_patch_short_circuits() {
    use components::Payload;
    let roles=payload_roles();let stack=Stack::from_overlay(7,1,Overlay::new(123));
    assert_eq!(components::damaged_with(&stack,&roles),None);
    assert_eq!(components::enchanted_with(&stack,&roles),None);
    {let mut owner=stack.components.lock().unwrap();owner.prepare_change();owner.remove_after_lookup(roles.values[1].key,Some(patch::Leaf(5)));}
    assert_eq!(components::damaged_with(&stack,&roles),Some(false));
    let value=Arc::new(components::Enchantments::new(32));
    payload_override(&stack,roles.values[4],10,Some(Payload::Enchantments(value.clone())));
    assert_eq!(components::enchanted_with(&stack,&roles),Some(false));
    value.insert_at(32,0,3).unwrap();
    assert_eq!(components::enchanted_with(&stack,&roles),Some(true));
}
#[test]
fn prototype_payload_retains_actual_enchantment_allocation_then_releases_removed_and_cleared_members() {
    use components::Payload;
    let roles=payload_roles();let role=roles.values[4];let value=Arc::new(components::Enchantments::new(32));let weak=Arc::downgrade(&value);
    let mut defaults=Store::new(2);
    defaults.put_payload(Row {key:role.key.0,leaf:1,hash:role.hash,kind:5,scalar:0,reserved:0},Some(Payload::Enchantments(value.clone()))).unwrap();
    drop(value);
    match defaults.get_payload(role.key.0,role.hash) {Some((_,Some(Payload::Enchantments(value))))=>assert!(Arc::ptr_eq(&value,&weak.upgrade().unwrap())),_=>panic!("missing actual prototype payload")};
    defaults.remove(role.key.0,role.hash);assert!(weak.upgrade().is_none());
}
fn defaults()->Arc<slots::BoundaryOwner<Store>> {let mut d=Store::new(1);d.put(Row{key:1,leaf:1,hash:1,kind:1,scalar:64,reserved:0});Arc::new(slots::BoundaryOwner::new(d))}
fn stack(item:u64,count:i32,d:&Arc<slots::BoundaryOwner<Store>>)->Arc<Stack>{let mut o=Overlay::new(item);o.defaults=Some(d.clone());Arc::new(Stack::new(item,count,Arc::new(Mutex::new(o))))}
fn empty(d:&Arc<slots::BoundaryOwner<Store>>)->Entry{Entry{facade:1,stack:stack(0,0,d)}}
#[test] fn transfer_contracts_alias_stores_and_next_index_are_exact(){
 let d=defaults();let source=stack(7,11,&d);let mut slots=Slots::new(27,empty(&d));
 slots.set(0,Entry{facade:2,stack:source.clone()});slots.set(1,Entry{facade:2,stack:source.clone()});
 let result=slots::transfer_step(&slots,&source,99,0,0);assert_eq!(result&7,1);assert_eq!((result>>3)&63,0);assert_eq!(source.scalars.count.load(Ordering::Relaxed),22);
 let result=slots::transfer_step(&slots,&source,99,1,1);assert_eq!(result&7,1);assert_eq!(source.scalars.count.load(Ordering::Relaxed),44);
 let result=slots::transfer_step(&slots,&source,99,1,2);assert_eq!(result&7,2);assert_eq!((result>>3)&63,2);assert_eq!(result>>32,44);
 let inserted=stack(7,44,&d);slots::transfer_insert(&mut slots,&source,2,Entry{facade:3,stack:inserted},44).unwrap();assert_eq!(source.scalars.count.load(Ordering::Relaxed),0);
}
#[test] fn transfer_contracts_callback_patch_loss_continues_without_replay(){
 let d=defaults();let source=stack(7,11,&d);let target=stack(7,60,&d);let mut slots=Slots::new(27,empty(&d));slots.set(0,Entry{facade:2,stack:target});
 let result=slots::transfer_step(&slots,&source,99,0,0);assert_eq!(result&7,1);assert_eq!(source.scalars.count.load(Ordering::Relaxed),7);
 {let mut o=source.components.lock().unwrap();o.prepare_change();o.set_after_compare(patch::Key(88),Some(patch::Leaf(77)),None,false);}
 let result=slots::transfer_step(&slots,&source,99,1,1);assert_eq!(result&7,3);assert_eq!((result>>3)&63,1);assert_eq!((result>>9)&3,1);assert_eq!(source.scalars.count.load(Ordering::Relaxed),7);
}
#[test] fn transfer_contracts_default_membership_is_consumed_live(){
 let d=defaults();let source=stack(7,11,&d);let slots=Slots::new(27,empty(&d));
 d.lock().unwrap().remove(1,1);let result=slots::transfer_step(&slots,&source,99,0,0);assert_eq!(result&7,2);assert_eq!(result>>32,1);
}

#[test] fn transfer_contracts_null_item_and_singleton_identity_remain_distinct() {
 let d=defaults();let source=stack(7,11,&d);let malformed=stack(0,5,&d);
 let mut singleton=Stack::new(0,27,Arc::new(Mutex::new(Overlay::new(0))));singleton.empty_identity=true;
 let mut slots=Slots::new(27,Entry{facade:1,stack:Arc::new(singleton)});slots.set(0,Entry{facade:2,stack:malformed});
 let result=slots::transfer_step(&slots,&source,99,0,0);assert_eq!(result&7,2);assert_eq!((result>>3)&63,1);assert_eq!(result>>32,11);
}

#[test] fn transfer_contracts_empty_prepare_remains_admitted_but_current_patch_view_mutation_invalidates() {
 let d=defaults();let source=stack(7,11,&d);assert!(source.admission.valid());
 {let mut o=source.components.lock().unwrap();o.prepare_change();}
 assert!(source.admission.valid(),"empty load/apply preparation must remain eligible");
 let view=source.components.lock().unwrap().view_patch();view.put(patch::Key(99),patch::Value::Present(patch::Leaf(77)));
 assert!(!source.admission.valid(),"current view must invalidate even after backing replacement");
}
#[test] fn transfer_contracts_other_owner_alias_write_invalidates_shared_backing_without_retaining_retired_rows() {
 let d=defaults();let source=stack(7,11,&d);let copied={source.components.lock().unwrap().copy()};
 let child=Arc::new(Stack::new(7,11,Arc::new(Mutex::new(copied))));
 assert!(source.admission.valid());assert!(child.admission.valid());
 source.components.lock().unwrap().view_patch().put(patch::Key(88),patch::Value::Present(patch::Leaf(66)));
 assert!(!source.admission.valid());assert!(!child.admission.valid(),"backing marker covers writes through other owners");
}
#[test] fn transfer_contracts_external_sanitized_patch_alias_invalidates_before_reentry() {
 let d=defaults();let input=patch::Patch::default();let mut o=Overlay::from_sanitized(7,input.clone());o.defaults=Some(d);
 let source=Stack::new(7,11,Arc::new(Mutex::new(o)));assert!(source.admission.valid());
 input.put(patch::Key(99),patch::Value::Present(patch::Leaf(55)));assert!(!source.admission.valid());
}

#[test] fn transfer_contracts_admission_invariant_survives_twenty_thousand_cow_and_escaped_alias_steps() {
 let d=defaults();let mut owners:Vec<Arc<Stack>>=(0..8).map(|_|stack(7,11,&d)).collect();let mut escaped=Vec::<patch::Patch>::new();let mut state=0x139a731cu64;
 for step in 0..20_000 {
  state=state.wrapping_mul(6364136223846793005).wrapping_add(1);let index=((state>>32) as usize)&7;
  match (state>>16)%8 {
   0=>owners[index].components.lock().unwrap().prepare_change(),
   1=>{let mut o=owners[index].components.lock().unwrap();o.prepare_change();o.set_after_compare(patch::Key(state&15),Some(patch::Leaf((state&255)+1)),None,false);},
   2=>{let view=owners[index].components.lock().unwrap().view_patch();view.put(patch::Key(state&15),patch::Value::Removed);},
   3=>{let view=owners[index].components.lock().unwrap().view_patch();if escaped.len()==16{escaped.remove(0);}escaped.push(view);},
   4=>{let copy=owners[index].components.lock().unwrap().copy();owners[(index+1)&7]=Arc::new(Stack::new(7,11,Arc::new(Mutex::new(copy))));},
   5=>owners[index].components.lock().unwrap().clear(),
   6=>{if !escaped.is_empty(){let alias=&escaped[(state as usize)%escaped.len()];alias.put(patch::Key(state&15),patch::Value::Present(patch::Leaf((state&255)+1)));}},
   _=>{owners[index]=stack(7,11,&d);}
  }
  for owner in &owners {if owner.admission.valid(){let actual=owner.components.lock().unwrap();assert_eq!(actual.patch_len(),0,"false-positive admission at step {step}");assert_eq!(actual.prototype,owner.admission.prototype);assert!(Arc::ptr_eq(actual.defaults.as_ref().unwrap(),owner.admission.defaults.as_ref().unwrap()));}}
 }
}

#[test] fn transfer_contracts_conservative_lineage_invalidation_keeps_actual_empty_stack_native() {
 let d=defaults();let source=stack(7,11,&d);
 {let copy=source.components.lock().unwrap().copy();let sibling=Stack::new(7,11,Arc::new(Mutex::new(copy)));let mut o=sibling.components.lock().unwrap();o.prepare_change();o.set_after_compare(patch::Key(9),Some(patch::Leaf(5)),None,false);}
 assert!(!source.admission.valid());assert_eq!(source.components.lock().unwrap().patch_len(),0);
 let slots=Slots::new(27,empty(&d));let result=slots::transfer_step(&slots,&source,99,0,0);assert_eq!(result&7,2,"canonical empty state must still enter native consumer");
}

fn bounded(slots:&slots::BoundaryOwner<Slots>,source:&Stack,d:&slots::BoundaryOwner<Store>,token:i64)->i64 {
 let slots_guard=slots.lock().unwrap();let defaults_guard=d.lock().unwrap();
 let result=unsafe{ffi::mattmc_item_transfer_bounded(slots.address() as i64,source.address() as i64,d.address() as i64,99,token)};
 drop(defaults_guard);drop(slots_guard);result
}
#[test] fn bounded_transfer_actual_layout_and_live_mutation_between_effects() {
 let d=defaults();let source=stack(7,11,&d);let target=stack(7,60,&d);
 let mut rows=Slots::new(27,empty(&d));rows.set(0,Entry{facade:2,stack:target.clone()});
 let rows=slots::BoundaryOwner::new(rows);
 assert_eq!(source.address(),Arc::as_ptr(&source) as usize,"leased scalar address is actual repr(C) stack");
 assert_eq!(std::mem::offset_of!(Stack,scalars),0);
 let first=bounded(&rows,&source,&d,0);assert_eq!(first&7,1);assert_eq!(source.scalars.count.load(Ordering::Relaxed),7);
 // This stands at the exact Java effect point: both real canonical owners can be mutated.
 d.lock().unwrap().put(Row{key:1,leaf:2,hash:1,kind:1,scalar:5,reserved:0});
 rows.lock().unwrap().set(1,empty(&d));
 let second=bounded(&rows,&source,&d,first);assert_eq!(second&7,2);assert_eq!((second>>3)&63,1);assert_eq!(second>>32,5);assert_ne!(second&2048,0);
 assert_eq!(target.scalars.count.load(Ordering::Relaxed),64,"previous merge cannot be replayed");
}
#[test] fn bounded_transfer_declines_invalid_patch_without_reading_mutex_or_changing_count() {
 let d=defaults();let source=stack(7,11,&d);let rows=slots::BoundaryOwner::new(Slots::new(27,empty(&d)));
 let mut component_guard=source.components.lock().unwrap();component_guard.prepare_change();component_guard.set_after_compare(patch::Key(99),Some(patch::Leaf(77)),None,false);
 let result=bounded(&rows,&source,&d,0);assert_eq!(result&7,4);assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);
 // Holding the actual component mutex here proves bounded rejection never acquires it.
 drop(component_guard);
}
#[test] fn bounded_transfer_declines_wrong_or_large_default_owner_without_effects() {
 let d=defaults();let other=defaults();let source=stack(7,11,&d);let rows=slots::BoundaryOwner::new(Slots::new(27,empty(&d)));
 assert_eq!(bounded(&rows,&source,&other,0)&7,4);
 for key in 2..200 {d.lock().unwrap().put(Row{key,leaf:key as i64,hash:key as u32,kind:0,scalar:0,reserved:0});}
 assert_eq!(bounded(&rows,&source,&d,0)&7,4);assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);
}
#[test] fn boundary_owner_excludes_actual_native_writer_until_admitted_borrow_ends() {
 let owner=Arc::new(slots::BoundaryOwner::new(7));let guard=owner.lock().unwrap();
 let (started,ready)=std::sync::mpsc::channel();let (done,finished)=std::sync::mpsc::channel();let other=owner.clone();
 let writer=std::thread::spawn(move||{started.send(()).unwrap();*other.lock().unwrap()=11;done.send(()).unwrap();});
 ready.recv().unwrap();assert!(finished.try_recv().is_err());assert_eq!(unsafe{*owner.admitted()},7);
 drop(guard);finished.recv_timeout(std::time::Duration::from_secs(5)).unwrap();writer.join().unwrap();assert_eq!(*owner.lock().unwrap(),11);
}

#[test] fn embedded_parent_lease_reclaims_exact_root_after_last_alias_without_ancestry_chain() {
 let root=Arc::new(Stack::from_overlay(7,11,Overlay::new(17)));let address=root.address();let weak=Arc::downgrade(&root);
 let first=Arc::new(Stack::with_components(7,22,stack::Components::from_stack(root.clone())));
 let second=Arc::new(Stack::with_components(7,33,stack::Components::from_stack(first.clone())));
 let stack::Components::Parent(owner)=&second.components else{panic!("must retain actual containing allocation")};
 assert!(Arc::ptr_eq(owner,&root));assert_eq!(owner.address(),address);drop(root);drop(first);
 assert_eq!(weak.upgrade().unwrap().scalars.count.load(Ordering::Relaxed),11);
 assert_eq!(second.components.lock().unwrap().prototype,17);drop(second);assert!(weak.upgrade().is_none());
}

#[test] fn inventory_owner_retains_actual_rows_and_scalar_layout_with_masks_and_wrapping() {
 let d=defaults();let mut singleton=Stack::new(0,27,Arc::new(Mutex::new(Overlay::new(0))));singleton.empty_identity=true;
 let inv=inventory::Inventory::new(Entry{facade:1,stack:Arc::new(singleton)});
 assert_eq!(std::mem::size_of::<inventory::Scalars>(),8);assert_eq!(std::mem::offset_of!(inventory::Scalars,changed),4);
 assert_eq!(std::mem::offset_of!(stack::Scalars,pop_time),12);assert_eq!(std::mem::size_of::<stack::Scalars>(),16);
 assert!(inv.main_empty(99));assert_eq!(inv.free_slot(99),0);
 let item=stack(7,11,&d);item.scalars.pop_time.store(i32::MIN,Ordering::Relaxed);
 {let mut rows=inv.slots.lock().unwrap();rows.set(0,Entry{facade:2,stack:item.clone()});rows.set(1,Entry{facade:2,stack:item.clone()});}
 assert_eq!(inv.free_slot(99),2);assert!(!inv.main_empty(99));assert_eq!(item.scalars.pop_time.load(Ordering::Relaxed),i32::MIN);
 inv.scalars.selected.store(3,Ordering::Relaxed);assert!(!inv.swap_selected(40));assert_eq!(inv.scalars.selected.load(Ordering::Relaxed),3);
 assert!(inv.swap_selected(0));assert_eq!(inv.slots.lock().unwrap().get(3).unwrap().facade,2);
 assert_eq!(inv.slots.lock().unwrap().get(1).unwrap().stack.scalars.count.load(Ordering::Relaxed),11);
 inv.scalars.changed.store(i32::MAX,Ordering::Relaxed);inv.scalars.changed.fetch_add(1,Ordering::Relaxed);assert_eq!(inv.scalars.changed.load(Ordering::Relaxed),i32::MIN);
}
#[test] fn inventory_actual_retained_allocation_outlives_original_handle() {
 let overlay=ffi::mattmc_item_overlay_create(17);let empty=ffi::mattmc_item_stack_create(0,0,overlay,1);let inv=ffi::mattmc_item_inventory_create(empty);
 let retained=ffi::mattmc_item_inventory_retain(inv);let address=ffi::mattmc_item_inventory_address(inv);
 assert!(address>0);assert_eq!(ffi::mattmc_item_inventory_address(retained),address);assert_ne!(retained,inv);
 assert_eq!(ffi::mattmc_item_owner_release(inv),1);assert_eq!(ffi::mattmc_item_inventory_address(inv),-2);
 let scalars=unsafe{&*(address as *const inventory::Scalars)};scalars.selected.store(8,Ordering::Relaxed);assert_eq!(scalars.selected.load(Ordering::Relaxed),8);
 assert_eq!(ffi::mattmc_item_inventory_query(retained,99,0),0);
 assert_eq!(ffi::mattmc_item_owner_release(retained),1);assert_eq!(ffi::mattmc_item_owner_release(empty),1);assert_eq!(ffi::mattmc_item_owner_release(overlay),1);
}

#[test] fn inventory_reverse_consumes_main_rows_in_menu_order_and_wraps_each_change() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);
 {let mut rows=inv.slots.lock().unwrap();for i in 0..36{rows.set(i,Entry{facade:100+i as i64,stack:stack(8,64,&d)});}for i in [8,7,35]{rows.set(i,Entry{facade:200+i as i64,stack:stack(7,60,&d)});}}
 inv.scalars.changed.store(i32::MAX-1,Ordering::Relaxed);let result=inv.transfer_step(&source,99,0);
 assert_eq!(result&7,0);assert_ne!(result&2048,0);assert_eq!(source.scalars.count.load(Ordering::Relaxed),0);
 let rows=inv.slots.lock().unwrap();for (i,n) in [(8,64),(7,64),(35,63)]{assert_eq!(rows.get(i).unwrap().stack.scalars.count.load(Ordering::Relaxed),n);}
 assert_eq!(inv.scalars.changed.load(Ordering::Relaxed),i32::MIN+1);
}
#[test] fn inventory_reverse_alias_and_foreign_continuation_preserve_committed_prefix() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);
 inv.slots.lock().unwrap().set(8,Entry{facade:2,stack:source.clone()});let result=inv.transfer_step(&source,99,0);
 assert_eq!(result&7,2);assert_eq!((result>>3)&63,1);assert_eq!(result>>32,22);assert_eq!(source.scalars.count.load(Ordering::Relaxed),22);assert_eq!(inv.scalars.changed.load(Ordering::Relaxed),1);
 let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);let foreign=stack(7,60,&d);
 {let mut c=foreign.components.lock().unwrap();c.prepare_change();c.set_after_compare(patch::Key(88),Some(patch::Leaf(44)),None,false);}
 {let mut rows=inv.slots.lock().unwrap();rows.set(8,Entry{facade:3,stack:stack(7,60,&d)});rows.set(7,Entry{facade:4,stack:foreign});}
 let result=inv.transfer_step(&source,99,0);assert_eq!(result&7,3);assert_eq!((result>>3)&63,1);assert_eq!((result>>9)&3,1);assert_ne!(result&2048,0);
 assert_eq!(source.scalars.count.load(Ordering::Relaxed),7);assert_eq!(inv.scalars.changed.load(Ordering::Relaxed),1);
}

#[test] fn inventory_add_alias_and_last_iteration_result_preserve_original_sequence() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);inv.slots.lock().unwrap().set(0,Entry{facade:2,stack:source.clone()});
 let mut effects=Vec::new();assert_eq!(inv.add_run(&source,0,99,|action,index|{effects.push((action,index));Ok(0)}),Ok(true));
 assert_eq!(effects,vec![(1,0)]);assert_eq!(source.scalars.count.load(Ordering::Relaxed),0);assert_eq!(source.scalars.pop_time.load(Ordering::Relaxed),5);
 let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);
 {let mut rows=inv.slots.lock().unwrap();for i in 0..36{rows.set(i,Entry{facade:100+i as i64,stack:stack(8,64,&d)});}rows.set(0,Entry{facade:2,stack:stack(7,60,&d)});}
 let mut effects=Vec::new();assert_eq!(inv.add_run(&source,-1,99,|action,index|{effects.push((action,index));Ok(0)}),Ok(false));
 assert_eq!(effects,vec![(1,0),(2,40),(5,0)]);assert_eq!(source.scalars.count.load(Ordering::Relaxed),7);
 assert_eq!(inv.slots.lock().unwrap().get(0).unwrap().stack.scalars.count.load(Ordering::Relaxed),64);assert_eq!(inv.scalars.changed.load(Ordering::Relaxed),0);
}
#[test] fn inventory_add_effect_reentry_reads_live_selection_and_bad_damage_prefix() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);
 {let mut c=source.components.lock().unwrap();c.prepare_change();c.set_after_compare(patch::Key(88),Some(patch::Leaf(44)),None,false);}
 {let mut rows=inv.slots.lock().unwrap();rows.set(0,Entry{facade:2,stack:stack(7,60,&d)});rows.set(1,Entry{facade:3,stack:stack(8,64,&d)});}
 let mut effects=Vec::new();assert_eq!(inv.add_run(&source,-1,99,|action,index|{effects.push((action,index));if action==2{assert_eq!(index,0);inv.scalars.selected.store(1,Ordering::Relaxed);assert!(inv.slots.lock().unwrap().get(0).is_some());Ok(1)}else{Ok(0)}}),Ok(false));
 assert_eq!(effects,vec![(1,0),(2,0),(5,0)]);assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);assert_eq!(inv.scalars.selected.load(Ordering::Relaxed),1);
 let inv=inventory::Inventory::new(empty(&d));let source=stack(7,1,&d);
 assert_eq!(inv.add_run(&source,40,99,|action,index|{if action==1{Ok(1)}else{assert_eq!((action,index),(4,40));source.scalars.count.store(0,Ordering::Relaxed);Err(())}}),Err(()));
 assert_eq!(source.scalars.count.load(Ordering::Relaxed),0);
}

#[test] fn inventory_scan_suitable_reads_live_selection_and_has_no_guard_across_effect() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));
 {let mut rows=inv.slots.lock().unwrap();for i in 0..9{rows.set(i,Entry{facade:100+i as i64,stack:stack(8,64,&d)});}}
 inv.scalars.selected.store(8,Ordering::Relaxed);let mut seen=Vec::new();
 let found=inv.scan_run(None,0,99,|action,index|{seen.push((action,index));{let _rows=inv.slots.lock().unwrap();}if index==8{inv.scalars.selected.store(0,Ordering::Relaxed);Ok(1)}else{Ok(0)}}).unwrap();
 assert_eq!(found,1);assert_eq!(seen,[(6,8),(6,1)]);
}
#[test] fn inventory_scan_matching_resumes_with_live_rows_and_keeps_null_effect_prefix() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);let foreign=stack(7,11,&d);
 {let mut overlay=foreign.components.lock().unwrap();overlay.prepare_change();overlay.set_after_compare(patch::Key(99),Some(patch::Leaf(22)),None,false);}
 inv.slots.lock().unwrap().set(3,Entry{facade:3,stack:foreign.clone()});let mut seen=Vec::new();
 let found=inv.scan_run(Some(&source),1,99,|action,index|{seen.push((action,index));assert!(foreign.components.try_lock().is_ok());inv.slots.lock().unwrap().set(5,Entry{facade:5,stack:source.clone()});Ok(0)}).unwrap();
 assert_eq!(found,5);assert_eq!(seen,[(7,3)]);
 let mut seen=Vec::new();assert!(inv.scan_run(None,1,99,|action,index|{seen.push((action,index));Err(())}).is_err());assert_eq!(seen,[(7,3)]);
}
#[test] fn inventory_scan_add_pick_keeps_selection_and_displacement_before_final_error() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));
 {let mut rows=inv.slots.lock().unwrap();for i in 0..9{rows.set(i,Entry{facade:100+i as i64,stack:stack(8,64,&d)});}}
 inv.scalars.selected.store(8,Ordering::Relaxed);let mut seen=Vec::new();
 let result=inv.scan_run(None,4,99,|action,index|{seen.push((action,index));match action {
  6=>Ok(0),
  9=>{let mut rows=inv.slots.lock().unwrap();let selected=inv.scalars.selected.load(Ordering::Relaxed) as usize;let old=rows.get(selected).unwrap().clone();rows.set(index as usize,old);Ok(0)},
  10=>Err(()),_=>panic!("unexpected effect")
 }});
 assert!(result.is_err());assert_eq!(seen,[(6,8),(9,9),(10,8)]);assert_eq!(inv.scalars.selected.load(Ordering::Relaxed),8);
 let rows=inv.slots.lock().unwrap();assert_eq!(rows.get(8).unwrap().facade,108);assert_eq!(rows.get(9).unwrap().facade,108);
}

#[test] fn inventory_visitor_captures_facade_then_rereads_alias_counts_and_later_membership() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);
 {let mut rows=inv.slots.lock().unwrap();rows.set(0,Entry{facade:7,stack:source.clone()});rows.set(2,Entry{facade:7,stack:source.clone()});}
 let mut seen=Vec::new();inv.visit_run(None,0,36,99,|action,index,facade|{
  seen.push((action,index,facade));assert!(source.components.try_lock().is_ok());
  if index==0{source.scalars.count.store(0,Ordering::Relaxed);inv.slots.lock().unwrap().set(5,Entry{facade:9,stack:stack(7,7,&d)});}Ok(0)
 }).unwrap();assert_eq!(seen,[(20,0,7),(20,5,9)]);
}
#[test] fn inventory_visitor_contains_limit_is_captured_but_replacement_limit_is_live() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let mut seen=Vec::new();let mut changed_size=43;
 inv.visit_run(None,4,43,99,|action,index,facade|{seen.push((action,index,facade));if index==0{changed_size=44;inv.slots.lock().unwrap().set(5,Entry{facade:9,stack:stack(7,7,&d)});}Ok(0)}).unwrap();
 assert_eq!(changed_size,44);assert_eq!(seen.len(),43);assert_eq!(seen[5],(24,5,9));assert_eq!(seen[42],(24,42,0));
 let mut size=36;let mut writes=Vec::new();let mut conditions=Vec::new();
 inv.visit_run(None,7,0,99,|action,index,_|{if action==27{conditions.push(index);Ok(size)}else{writes.push(index);if index==0{size=37;}Ok(0)}}).unwrap();
 assert_eq!(writes,(0..37).collect::<Vec<_>>());assert_eq!(conditions,(0..38).collect::<Vec<_>>());
}
#[test] fn inventory_visitor_input_failure_keeps_original_progress_and_never_replays_effects() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let mut seen=Vec::new();let mut cursor=0;
 let result=inv.visit_run(None,8,0,99,|action,_,_|{seen.push(action);if action==29{return Ok(1);}if cursor==2{return Err(());}inv.slots.lock().unwrap().set(cursor+3,Entry{facade:10+cursor as i64,stack:stack(7,11,&d)});cursor+=1;Ok(0)});
 assert!(result.is_err());assert_eq!(seen,[29,30,29,30,29,30]);assert_eq!(cursor,2);assert_eq!(inv.slots.lock().unwrap().get(3).unwrap().facade,10);assert_eq!(inv.slots.lock().unwrap().get(4).unwrap().facade,11);
}
#[test] fn inventory_visitor_same_components_consumes_native_rows_and_null_waits_for_original_effect() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);inv.slots.lock().unwrap().set(3,Entry{facade:7,stack:source.clone()});
 assert!(inv.visit_run(Some(&source),6,43,99,|_,_,_|panic!("pure matching must finish inside native owner")).unwrap());
 let mut seen=Vec::new();assert!(inv.visit_run(None,6,43,99,|action,index,facade|{seen.push((action,index,facade));Err(())}).is_err());assert_eq!(seen,[(26,3,7)]);
}

#[test] fn inventory_bulk_clear_changes_only_canonical_membership_and_preserves_retained_stacks() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);source.scalars.pop_time.store(5,Ordering::Relaxed);
 inv.scalars.selected.store(8,Ordering::Relaxed);inv.scalars.changed.store(i32::MAX,Ordering::Relaxed);
 {let mut rows=inv.slots.lock().unwrap();for i in 0..36{rows.set(i,Entry{facade:7,stack:source.clone()});}rows.fill(empty(&d));}
 assert!(inv.main_empty(99));assert_eq!(inv.first_facade(7),-1);assert_eq!(inv.first_facade(1),0);
 assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);assert_eq!(source.scalars.pop_time.load(Ordering::Relaxed),5);
 assert_eq!(inv.scalars.selected.load(Ordering::Relaxed),8);assert_eq!(inv.scalars.changed.load(Ordering::Relaxed),i32::MAX);
 let weak=Arc::downgrade(&source);drop(source);assert!(weak.upgrade().is_none());
}
#[test] fn inventory_identity_search_uses_facade_identity_even_when_actual_stack_allocation_aliases() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);
 {let mut rows=inv.slots.lock().unwrap();rows.set(2,Entry{facade:7,stack:source.clone()});rows.set(3,Entry{facade:8,stack:source.clone()});}
 assert_eq!(inv.first_facade(7),2);assert_eq!(inv.first_facade(8),3);assert_eq!(inv.first_facade(0),-1);
}

#[test] fn inventory_borrowed_scan_drops_both_actual_gates_before_effect_and_rechecks_mutated_defaults() {
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let source=stack(7,11,&d);let foreign=stack(7,60,&d);
 {let mut overlay=foreign.components.lock().unwrap();overlay.prepare_change();overlay.set_after_compare(patch::Key(99),Some(patch::Leaf(11)),None,false);}
 {let mut rows=inv.slots.lock().unwrap();rows.set(0,Entry{facade:5,stack:stack(8,64,&d)});rows.set(1,Entry{facade:6,stack:foreign});rows.set(2,Entry{facade:7,stack:stack(7,60,&d)});rows.set(3,Entry{facade:8,stack:stack(7,11,&d)});}
 let mut seen=Vec::new();let found=inv.remaining_slot(Some(&source),99,&mut |action,index|{
  seen.push((action,index));{let _rows=inv.slots.lock().unwrap();}
  if index==1{d.lock().unwrap().put(Row{key:1,leaf:2,hash:1,kind:1,scalar:32,reserved:0});}Ok(0)
 }).unwrap();
 assert_eq!(found,3,"old64 cached across equality would wrongly admit target2");assert_eq!(seen,[(2,40),(2,1)]);
}

#[test] fn equipment_canonical_values_distinguish_absent_null_and_empty_and_keep_enum_keys_on_clear() {
 use container::{Equipment,EquipmentValue as V};let d=defaults();let mut eq=Equipment::new(8);assert_eq!(eq.get(5).unwrap().token(),-1);
 eq.set(5,V::Null);eq.set(1,V::Stack(empty(&d)));assert_eq!(eq.size(),2);assert_eq!(eq.get(5).unwrap().token(),0);assert_eq!(eq.next(0),1);assert_eq!(eq.next(2),5);
 eq.clear_values(empty(&d));assert_eq!(eq.size(),2);assert_eq!(eq.get(5).unwrap().token(),1);assert_eq!(eq.next(6),-1);eq.clear_keys();assert_eq!(eq.size(),0);assert_eq!(eq.next(0),-1);
}
#[test] fn equipment_retains_shared_actual_stacks_until_last_native_membership_retires() {
 use container::{Equipment,EquipmentValue as V};let d=defaults();let mut eq=Equipment::new(8);let source=stack(7,11,&d);let weak=Arc::downgrade(&source);
 eq.set(1,V::Stack(Entry{facade:7,stack:source.clone()}));eq.set(5,V::Stack(Entry{facade:7,stack:source.clone()}));drop(source);
 assert!(eq.references(7));let old=eq.set(1,V::Absent).unwrap();drop(old);assert!(eq.references(7));assert_eq!(weak.upgrade().unwrap().scalars.count.load(Ordering::Relaxed),11);
 eq.clear_keys();assert!(!eq.references(7));assert!(weak.upgrade().is_none());
}
#[test] fn equipment_actual_ffi_null_and_retired_binding_results_match_canonical_state() {
 unsafe {
 let overlay=ffi::mattmc_item_overlay_create(7);let source=ffi::mattmc_item_stack_create(7,11,overlay,0);let eq=ffi::mattmc_item_equipment_create(8);let mut retired=-1;
 assert_eq!(ffi::mattmc_item_equipment_op(eq,0,5,0,&mut retired),-1);assert_eq!(ffi::mattmc_item_equipment_op(eq,1,5,0,&mut retired),-1);
 assert_eq!(ffi::mattmc_item_equipment_op(eq,0,5,0,&mut retired),0);assert_eq!(ffi::mattmc_item_equipment_op(eq,3,5,0,&mut retired),1);
 assert_eq!(ffi::mattmc_item_equipment_op(eq,1,5,source,&mut retired),0);assert_eq!(ffi::mattmc_item_equipment_op(eq,1,1,source,&mut retired),-1);
 assert_eq!(ffi::mattmc_item_equipment_op(eq,2,5,0,&mut retired),source);assert_eq!(retired,0);
 assert_eq!(ffi::mattmc_item_equipment_op(eq,2,1,0,&mut retired),source);assert_eq!(retired,source);
 assert_eq!(ffi::mattmc_item_owner_release(eq),1);assert_eq!(ffi::mattmc_item_owner_release(source),1);assert_eq!(ffi::mattmc_item_owner_release(overlay),1);
 }
}

#[test] fn stock_slot_binding_reads_actual_owner_index_and_live_aliases_without_a_second_store(){
 let d=defaults();let inv=Arc::new(inventory::Inventory::new(empty(&d)));
 let link=menu::SlotLink::new(inv.clone(),7).unwrap();assert_eq!(link.index,7);
 assert!(menu::SlotLink::new(inv.clone(),36).is_none());assert!(menu::SlotLink::new(inv.clone(),-1).is_none());
 let item=stack(7,11,&d);inv.slots.lock().unwrap().set(7,Entry{facade:72,stack:item.clone()});
 {let _gate=inv.slots.lock().unwrap();unsafe{assert_eq!(link.read_admitted(inv.slots.address(),99,false),72);assert_eq!(link.read_admitted(inv.slots.address(),99,true),1);assert_eq!(link.read_admitted(0,99,false),-1);}}
 item.scalars.count.store(-7,Ordering::Relaxed);
 {let _gate=inv.slots.lock().unwrap();unsafe{assert_eq!(link.read_admitted(inv.slots.address(),99,true),0);}}
 inv.slots.lock().unwrap().set(7,Entry{facade:73,stack:stack(8,4,&d)});
 let retained=Arc::new(link);let view=retained.clone();drop(retained);drop(inv);
 {let menu::SlotContainer::Inventory(owner)=&view.container else{panic!("wrong source")};let _gate=owner.slots.lock().unwrap();unsafe{assert_eq!(view.read_admitted(owner.slots.address(),99,false),73);}}
 assert_eq!(view.index,7);assert_eq!(view.address(),Arc::as_ptr(&view) as usize);
}

#[test] fn container_slot_binding_follows_actual_replacements_and_releases_old_rows(){
 let d=defaults();let first=Arc::new(slots::BoundaryOwner::new(Slots::new(27,empty(&d))));let weak=Arc::downgrade(&first);
 let value=stack(7,11,&d);first.lock().unwrap().set(0,Entry{facade:91,stack:value.clone()});
 let cell=Arc::new(slots::BoundaryOwner::new(container::Container::new(10,Some(container::Rows::Separate(first.clone())))));
 let link=menu::SlotLink::for_container(cell.clone(),0).unwrap();assert_eq!(link.read(99,false),91);assert_eq!(link.read(99,true),1);
 let other=Arc::new(inventory::Inventory::new(empty(&d)));other.slots.lock().unwrap().set(0,Entry{facade:92,stack:stack(8,4,&d)});
 cell.lock().unwrap().replace(11,Some(container::Rows::Inventory(other.clone())));assert_eq!(link.read(99,false),92);
 assert_eq!(first.lock().unwrap().get(0).unwrap().facade,91);drop(first);assert!(weak.upgrade().is_none());
 cell.lock().unwrap().replace(12,None);assert_eq!(link.read(99,false),-1);assert_eq!(cell.lock().unwrap().token(),12);
 cell.lock().unwrap().replace(0,None);assert_eq!(link.read(99,true),-1);
 cell.lock().unwrap().replace(11,Some(container::Rows::Inventory(other)));assert_eq!(link.read(99,false),92);
 assert!(cell.lock().is_ok());assert_eq!(value.scalars.count.load(Ordering::Relaxed),11);
}

#[test] fn menu_roster_retains_actual_endpoints_and_native_membership_decides_retirement(){
 let d=defaults();let inv=Arc::new(inventory::Inventory::new(empty(&d)));let link=Arc::new(menu::SlotLink::new(inv.clone(),7).unwrap());let weak=Arc::downgrade(&link);
 let mut menu=menu::Menu::new(Some(empty(&d)));assert_eq!(menu.header.size.load(Ordering::Relaxed),0);
 assert_eq!(std::mem::offset_of!(menu::Header,size),8);assert_eq!(std::mem::offset_of!(menu::Header,carried),16);assert_eq!(std::mem::size_of::<menu::Header>(),24);
 assert!(menu.insert(0,menu::MenuSlot{facade:91,link:Some(link.clone())}));assert!(menu.insert(1,menu::MenuSlot{facade:91,link:Some(link.clone())}));drop(link);
 assert_eq!(menu.get(0).unwrap().link.as_ref().unwrap().index,7);assert_eq!(menu.remove(0).unwrap().facade,91);assert!(menu.references(91));assert!(weak.upgrade().is_some());
 assert_eq!(menu.set(0,menu::MenuSlot{facade:92,link:None}).unwrap().facade,91);assert!(!menu.references(91));assert!(weak.upgrade().is_none());assert_eq!(menu.header.size.load(Ordering::Relaxed),1);
 assert!(!menu.insert(3,menu::MenuSlot{facade:93,link:None}));assert!(menu.remove(4).is_none());
 let stack=stack(7,11,&d);menu.set_carried(Some(Entry{facade:94,stack:stack.clone()}));stack.scalars.count.store(-7,Ordering::Relaxed);
 assert_eq!(menu.carried().unwrap().stack.scalars.count.load(Ordering::Relaxed),-7);assert_eq!(menu.header.carried.load(Ordering::Relaxed),94);
 menu.set_carried(None);assert!(menu.carried().is_none());assert_eq!(menu.header.carried.load(Ordering::Relaxed),0);
}

#[test] fn stock_admission_consumes_actual_menu_links_and_original_length_contracts(){
 let d=defaults();let inv=Arc::new(inventory::Inventory::new(empty(&d)));
 let chest=Arc::new(slots::BoundaryOwner::new(container::Container::new(10,Some(container::Rows::Separate(Arc::new(slots::BoundaryOwner::new(Slots::new(27,empty(&d)))))))));
 let mut owner=menu::Menu::new(None);
 for index in 0..63 {
  let link=if index<27{menu::SlotLink::for_container(chest.clone(),index as i32).unwrap()}else{menu::SlotLink::new(inv.clone(),if index<54{index as i32-18}else{index as i32-54}).unwrap()};
  assert!(owner.insert(index,menu::MenuSlot{facade:index as i64+100,link:Some(Arc::new(link))}));
 }
 assert!(owner.matches_stock(&chest,Some(&inv),true));assert!(owner.matches_stock(&chest,None,false));
 let wrong=Arc::new(inventory::Inventory::new(empty(&d)));assert!(!owner.matches_stock(&chest,Some(&wrong),true));
 owner.insert(63,menu::MenuSlot{facade:999,link:None});assert!(!owner.matches_stock(&chest,Some(&inv),true));assert!(owner.matches_stock(&chest,None,false));owner.remove(63);
 let old=owner.set(3,menu::MenuSlot{facade:777,link:None}).unwrap();assert!(!owner.matches_stock(&chest,None,false));assert!(!owner.matches_stock(&chest,Some(&inv),true));owner.set(3,old);
 let old=owner.set(27,menu::MenuSlot{facade:778,link:Some(Arc::new(menu::SlotLink::new(inv.clone(),0).unwrap()))}).unwrap();assert!(!owner.matches_stock(&chest,Some(&inv),true));assert!(owner.matches_stock(&chest,None,false));owner.set(27,old);
 assert!(owner.matches_stock(&chest,Some(&inv),true));assert!(chest.lock().is_ok());assert!(inv.slots.lock().is_ok());
}

struct ActionEffects {
 owner:Arc<slots::BoundaryOwner<menu::Menu>>,source:Arc<Stack>,snapshot:Arc<Stack>,events:Vec<&'static str>,fail_craft:bool,change_snapshot:bool,
}
impl ActionEffects {fn event(&mut self,value:&'static str){let _gate=self.owner.lock().unwrap();self.events.push(value);}}
impl menu::QuickMoveEffects for ActionEffects {
 fn select(&mut self,_:i32)->Result<menu::Selected,()>{self.event("select");Ok(menu::Selected{absent:false,link:None})}
 fn has_item(&mut self)->Result<bool,()>{self.event("has");Ok(true)}
 fn capture_copy(&mut self)->Result<(Arc<Stack>,Arc<Stack>),()>{self.event("copy");Ok((self.source.clone(),self.snapshot.clone()))}
 fn equipment(&mut self)->Result<(),()>{self.event("equipment");Ok(())}
 fn equipment_route(&mut self)->Result<(i32,i32),()>{self.event("route");Ok((0,0))}
 fn target_has_item(&mut self,_:i32)->Result<bool,()>{self.event("target");Ok(false)}
 fn transfer(&mut self,start:i32,end:i32,reverse:bool)->Result<bool,()>{self.event("transfer");assert_eq!((start,end,reverse),(9,45,true));self.source.scalars.count.store(66,Ordering::Relaxed);Ok(true)}
 fn quick_craft(&mut self)->Result<(),()>{self.event("craft");if self.fail_craft{return Err(());}if self.change_snapshot{self.snapshot.scalars.count.store(66,Ordering::Relaxed);}Ok(())}
 fn cleanup(&mut self,empty:bool,inventory:bool)->Result<(),()>{self.event("cleanup");assert!(!empty);assert!(inventory);Ok(())}
 fn take(&mut self)->Result<(),()>{self.event("take");Ok(())}
 fn drop_leftover(&mut self)->Result<(),()>{self.event("drop");Ok(())}
 fn finish(&mut self,success:bool)->Result<(),()>{self.event(if success{"return"}else{"empty"});Ok(())}
 fn initial(&mut self,_:i32)->Result<bool,()>{self.event("initial");Ok(true)}
 fn initial_item(&mut self)->Result<Arc<Stack>,()>{self.event("initial-get");self.source.scalars.count.store(0,Ordering::Relaxed);Ok(self.source.clone())}
}
fn action_effects()->ActionEffects {let d=defaults();ActionEffects{owner:Arc::new(slots::BoundaryOwner::new(menu::Menu::new(None))),source:stack(7,130,&d),snapshot:stack(7,130,&d),events:Vec::new(),fail_craft:false,change_snapshot:false}}
#[test] fn menu_result_action_releases_guards_before_effects_and_preserves_partial_take_drop_order(){
 let mut effects=action_effects();let owner=effects.owner.clone();let snapshot=menu::quick_move(&owner,2,0,0,99,&mut effects).unwrap().unwrap();
 assert!(Arc::ptr_eq(&snapshot,&effects.snapshot));assert_eq!(effects.source.scalars.count.load(Ordering::Relaxed),66);
 assert_eq!(effects.events,vec!["select","has","copy","equipment","transfer","craft","cleanup","take","drop","return"]);
}
#[test] fn menu_result_action_reads_live_snapshot_after_quickcraft_and_keeps_throw_prefix(){
 let mut effects=action_effects();let owner=effects.owner.clone();effects.change_snapshot=true;assert!(menu::quick_move(&owner,2,0,0,99,&mut effects).unwrap().is_none());
 assert_eq!(effects.events,vec!["select","has","copy","equipment","transfer","craft","cleanup","empty"]);
 let mut effects=action_effects();let owner=effects.owner.clone();effects.fail_craft=true;assert!(menu::quick_move(&owner,2,0,0,99,&mut effects).is_err());assert_eq!(effects.source.scalars.count.load(Ordering::Relaxed),66);
 assert_eq!(effects.events,vec!["select","has","copy","equipment","transfer","craft"]);
}
#[test] fn repeated_menu_action_rechecks_captured_initial_item_after_take_drop_before_next_source(){
 let mut effects=action_effects();let owner=effects.owner.clone();menu::repeat_quick_move(&owner,2,0,0,99,&mut effects).unwrap();
 assert_eq!(effects.events,vec!["initial","select","has","copy","equipment","transfer","craft","cleanup","take","drop","return","initial-get"]);
}

fn ffi_stack_for_run(item:i64,count:i64,defaults:i64,handles:&mut Vec<i64>)->i64 {
 let overlay=ffi::mattmc_item_overlay_create(1);assert_eq!(ffi::mattmc_item_overlay_defaults(overlay,defaults),0);
 let value=ffi::mattmc_item_stack_create(item,count,overlay,0);assert!(value>0);handles.extend([overlay,value]);value
}
unsafe extern "C" fn unexpected_inventory_run_effect(_:i64,_:i64,_:i64,_:i64,_:i64)->i64 {panic!("pure main merges must remain native");}
fn ffi_count(handle:i64)->i32 {unsafe{(*(ffi::mattmc_item_stack_address(handle) as *const stack::Scalars)).count.load(Ordering::Relaxed)}}
#[test] fn complete_transfer_run_uses_actual_inventory_reverse_order_and_change_counter(){
 let defaults=ffi::mattmc_item_defaults_create(1);ffi::mattmc_item_defaults_put(defaults,1,1,(1i64<<32)|1,64);let mut handles=vec![defaults];
 let empty=ffi_stack_for_run(0,0,defaults,&mut handles);let inv=ffi::mattmc_item_inventory_create(empty);handles.push(inv);
 let mut targets=Vec::new();for index in 0..36{let target=ffi_stack_for_run(7,60,defaults,&mut handles);assert!(ffi::mattmc_item_slots_set(inv,index,target)>0);targets.push(target);}
 let source=ffi_stack_for_run(7,100,defaults,&mut handles);
 assert_eq!(unsafe{ffi::mattmc_item_transfer_run(inv,source,99,Some(unexpected_inventory_run_effect))},1);assert_eq!(ffi_count(source),0);
 let address=ffi::mattmc_item_inventory_address(inv);assert_eq!(unsafe{(*(address as *const inventory::Scalars)).changed.load(Ordering::Relaxed)},25);
 for (index,target) in targets.iter().enumerate(){assert_eq!(ffi_count(*target),if index<=8||index>=20{64}else{60});}
 for handle in handles{assert_eq!(ffi::mattmc_item_owner_release(handle),1);}
}
thread_local!{static TRANSFER_RUN_EFFECT:std::cell::Cell<(i64,i64,usize)>=const{std::cell::Cell::new((0,0,0))};}
unsafe extern "C" fn change_live_default_during_run(action:i64,index:i64,_:i64,_:i64,_:i64)->i64 {
 TRANSFER_RUN_EFFECT.with(|state|{let (slots,defaults,calls)=state.get();assert_eq!(action,1);assert_eq!(index,0);assert!(ffi::mattmc_item_slots_get(slots,0)>0);
  assert_eq!(ffi::mattmc_item_defaults_put(defaults,1,1,(1i64<<32)|1,32),1);state.set((slots,defaults,calls+1));});2
}
#[test] fn complete_transfer_run_releases_actual_rows_and_defaults_before_reentrant_effect(){
 let defaults=ffi::mattmc_item_defaults_create(1);ffi::mattmc_item_defaults_put(defaults,1,1,(1i64<<32)|1,64);let mut handles=vec![defaults];
 let empty=ffi_stack_for_run(0,0,defaults,&mut handles);let slots=ffi::mattmc_item_slots_create(27,empty);handles.push(slots);
 for index in 0..27{let target=ffi_stack_for_run(7,60,defaults,&mut handles);ffi::mattmc_item_slots_set(slots,index,target);}
 let source=ffi_stack_for_run(7,11,defaults,&mut handles);TRANSFER_RUN_EFFECT.with(|state|state.set((slots,defaults,0)));
 assert_eq!(unsafe{ffi::mattmc_item_transfer_run(slots,source,99,Some(change_live_default_during_run))},1);assert_eq!(ffi_count(source),7);
 assert_eq!(ffi_count(ffi::mattmc_item_slots_get(slots,0)),64);assert_eq!(ffi_count(ffi::mattmc_item_slots_get(slots,1)),60);TRANSFER_RUN_EFFECT.with(|state|assert_eq!(state.get().2,1));
 for handle in handles{assert_eq!(ffi::mattmc_item_owner_release(handle),1);}
}

#[test] fn inventory_queries_linked_actual_equipment_after_live_routing_effect_without_held_guards(){
 let d=defaults();let inv=Arc::new(inventory::Inventory::new(empty(&d)));let eq=Arc::new(slots::BoundaryOwner::new(container::Equipment::new(8)));assert!(inv.equipment.set(eq.clone()).is_ok());
 let source=stack(7,11,&d);let target=stack(7,60,&d);eq.lock().unwrap().set(2,container::EquipmentValue::Stack(Entry{facade:72,stack:target.clone()}));
 let mut queries=Vec::new();let mut effect=|action,index|{queries.push((action,index));assert_eq!((action,index),(11,40));let _main=inv.slots.lock().unwrap();let _equipment=eq.lock().unwrap();Ok(3)};
 assert_eq!(inv.remaining_slot(Some(&source),99,&mut effect),Ok(40));assert_eq!(queries,vec![(11,40)]);
 let mut effect=|action,index|{assert_eq!((action,index),(11,40));Ok(4)};assert_eq!(inv.remaining_slot(Some(&source),99,&mut effect),Ok(-1));
 let mut effect=|action,index|{assert_eq!((action,index),(11,40));target.scalars.count.store(64,Ordering::Relaxed);Ok(3)};assert_eq!(inv.remaining_slot(Some(&source),99,&mut effect),Ok(-1));
 assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);
}
#[test] fn linked_equipment_present_null_retains_original_effect_and_actual_link_releases_with_inventory(){
 let d=defaults();let inv=inventory::Inventory::new(empty(&d));let eq=Arc::new(slots::BoundaryOwner::new(container::Equipment::new(8)));let weak=Arc::downgrade(&eq);assert!(inv.equipment.set(eq.clone()).is_ok());
 eq.lock().unwrap().set(2,container::EquipmentValue::Null);let source=stack(7,11,&d);let mut events=Vec::new();
 assert_eq!(inv.remaining_slot(Some(&source),99,&mut |action,index|{events.push((action,index));match action{11=>Ok(3),2=>Err(()),_=>panic!("unexpected effect")}}),Err(()));
 assert_eq!(events,vec![(11,40),(2,40)]);eq.lock().unwrap().set(2,container::EquipmentValue::Stack(empty(&d)));
 assert_eq!(inv.remaining_slot(Some(&source),99,&mut |action,index|{assert_eq!((action,index),(11,40));Ok(3)}),Ok(-1));
 drop(eq);assert!(weak.upgrade().is_some());drop(inv);assert!(weak.upgrade().is_none());
 let foreign=inventory::Inventory::new(empty(&d));assert_eq!(foreign.remaining_slot(Some(&source),99,&mut |action,index|{assert_eq!((action,index),(2,40));Ok(1)}),Ok(40));
}

#[test] fn staged_component_root_failure_keeps_actual_shared_backing_until_matching_commit(){
 let mut owner=Overlay::new(1);owner.prepare_change();owner.set_after_compare(patch::Key(1),Some(patch::Leaf(11)),None,false);
 let escaped=owner.as_patch().unwrap();let version=owner.patch_version();let candidate=owner.stage_change().unwrap();assert_ne!(candidate.version(),version);drop(candidate);
 assert_eq!(owner.patch_version(),version);escaped.put(patch::Key(2),patch::Value::Present(patch::Leaf(22)));assert_eq!(owner.get_override(patch::Key(2)),Some(patch::Value::Present(patch::Leaf(22))));
 let candidate=owner.stage_change().unwrap();assert!(!owner.commit_change(version+999,candidate.clone()));assert_eq!(owner.patch_version(),version);assert!(owner.commit_change(version,candidate));assert!(!owner.needs_change());
 escaped.remove(patch::Key(1));assert_eq!(owner.get_override(patch::Key(1)),Some(patch::Value::Present(patch::Leaf(11))));
 let current=owner.as_patch().unwrap();owner.set_after_compare(patch::Key(3),Some(patch::Leaf(33)),None,false);assert_eq!(current.get(patch::Key(3)),Some(patch::Value::Present(patch::Leaf(33))));
}

#[test] fn actual_slot_publication_retires_only_last_facade_alias_and_rejects_stale_selection(){
 let defaults=ffi::mattmc_item_defaults_create(1);let mut handles=vec![defaults];
 let empty=ffi_stack_for_run(0,0,defaults,&mut handles);let source=ffi_stack_for_run(7,11,defaults,&mut handles);let rows=ffi::mattmc_item_slots_create(2,empty);handles.push(rows);
 assert_eq!(ffi::mattmc_item_slots_publish(rows,0,source,source),-2);assert_eq!(ffi::mattmc_item_slots_get(rows,0),empty);
 assert_eq!(ffi::mattmc_item_slots_publish(rows,0,source,empty),0);assert_eq!(ffi::mattmc_item_slots_publish(rows,1,source,empty),1);
 assert_eq!(ffi::mattmc_item_slots_publish(rows,0,empty,source),0);assert_eq!(ffi::mattmc_item_slots_get(rows,1),source);
 assert_eq!(ffi::mattmc_item_slots_publish(rows,1,empty,source),1);assert_eq!(ffi_count(source),11);
 for handle in handles{assert_eq!(ffi::mattmc_item_owner_release(handle),1);}
}
#[test] fn actual_insert_publication_checks_before_shrink_and_preserves_other_source_alias(){
 let defaults=ffi::mattmc_item_defaults_create(1);let mut handles=vec![defaults];
 let empty=ffi_stack_for_run(0,0,defaults,&mut handles);let source=ffi_stack_for_run(7,11,defaults,&mut handles);let inserted=ffi_stack_for_run(7,5,defaults,&mut handles);let rows=ffi::mattmc_item_slots_create(2,empty);handles.push(rows);
 ffi::mattmc_item_slots_set(rows,0,source);ffi::mattmc_item_slots_set(rows,1,source);
 assert_eq!(ffi::mattmc_item_transfer_publish(rows,source,0,inserted,5,empty),-2);assert_eq!(ffi_count(source),11);assert_eq!(ffi::mattmc_item_slots_get(rows,0),source);
 assert_eq!(ffi::mattmc_item_transfer_publish(rows,source,0,inserted,5,source),0);assert_eq!(ffi_count(source),6);assert_eq!(ffi::mattmc_item_slots_get(rows,0),inserted);assert_eq!(ffi::mattmc_item_slots_get(rows,1),source);
 assert_eq!(ffi::mattmc_item_slots_publish(rows,1,empty,source),1);
 for handle in handles{assert_eq!(ffi::mattmc_item_owner_release(handle),1);}
}
#[test] fn empty_menu_source_consumes_live_actual_endpoint_and_requires_current_chest_admission(){
 let d=defaults();let inv=Arc::new(inventory::Inventory::new(empty(&d)));let mut owner=menu::Menu::new(None);
 let link=Arc::new(menu::SlotLink::new(inv.clone(),7).unwrap());owner.insert(0,menu::MenuSlot{facade:81,link:Some(link)});assert!(owner.empty_source(0,99,None));
 let source=stack(7,11,&d);inv.slots.lock().unwrap().set(7,Entry{facade:71,stack:source.clone()});assert!(!owner.empty_source(0,99,None));source.scalars.count.store(-7,Ordering::Relaxed);assert!(owner.empty_source(0,99,None));
 assert!(!owner.empty_source(-1,99,None));assert!(!owner.empty_source(9,99,None));owner.insert(1,menu::MenuSlot{facade:82,link:None});assert!(!owner.empty_source(1,99,None));
 let rows=Arc::new(slots::BoundaryOwner::new(Slots::new(27,empty(&d))));let chest=Arc::new(slots::BoundaryOwner::new(container::Container::new(10,Some(container::Rows::Separate(rows.clone())))));
 let other=Arc::new(slots::BoundaryOwner::new(container::Container::new(11,None)));
 owner.set(0,menu::MenuSlot{facade:83,link:Some(Arc::new(menu::SlotLink::for_container(chest.clone(),0).unwrap()))});
 assert!(!owner.empty_source(0,99,None));assert!(!owner.empty_source(0,99,Some(&other)));assert!(owner.empty_source(0,99,Some(&chest)));
 chest.lock().unwrap().replace(12,None);assert!(!owner.empty_source(0,99,Some(&chest)));chest.lock().unwrap().replace(10,Some(container::Rows::Separate(rows)));
 assert!(owner.empty_source(0,99,Some(&chest)));let _menu_gate=slots::BoundaryOwner::new(owner);let _inventory_gate=inv.slots.lock().unwrap();let _chest_gate=chest.lock().unwrap();
}

struct UnifiedMenuFixture {menu:i64,link:i64,source:i64,snapshot:i64,destination:i64,events:Vec<i64>,fail:bool,prepared:bool,empty:bool}
thread_local!{static UNIFIED_MENU_FIXTURE:std::cell::RefCell<Option<UnifiedMenuFixture>>=const{std::cell::RefCell::new(None)};}
unsafe extern "C" fn unified_menu_effect(operation:i64,first:i64,second:i64,third:i64,out:i64)->i64 {
 UNIFIED_MENU_FIXTURE.with(|state|{let mut state=state.borrow_mut();let f=state.as_mut().unwrap();f.events.push(operation);let out=out as *mut i64;
  match operation {
   1=>{assert_eq!(first,27);assert_eq!(ffi::mattmc_item_menu_get(f.menu,27),127);if f.prepared{if f.empty{4}else{out.write(ffi::mattmc_item_stack_address(f.source));out.add(1).write(ffi::mattmc_item_stack_address(f.snapshot));3}}else{out.write(ffi::mattmc_item_slot_address(f.link));1}},
   3=>{out.write(ffi::mattmc_item_stack_address(f.source));out.add(1).write(ffi::mattmc_item_stack_address(f.snapshot));1},
   7=>{assert_eq!((first,second,third),(0,27,0));out.write(ffi::mattmc_item_slots_owner_address(f.destination));out.add(1).write(0);2},
   15=>{assert_eq!((first,second),(1,0));assert_eq!(((third as u64>>1)&3,third>>32,third&1),(1,0,0));assert_eq!(ffi_count(f.source),7);assert_eq!(ffi_count(ffi::mattmc_item_slots_get(f.destination,0)),64);if f.fail{i64::MIN}else{2}},
   9=>{assert_eq!((first,second),(0,0));1},12=>{assert_eq!(first,i64::from(!f.empty));1},_=>panic!("Unexpected unified operation {operation}")
  }
 })
}
#[test] fn actual_menu_abi_runs_destination_consumer_without_a_second_native_entry_and_keeps_failure_prefix(){
 for (fail,prepared,empty_source) in [(false,false,false),(true,false,false),(false,true,false),(true,true,false),(false,false,true),(false,true,true)]{
  let defaults=ffi::mattmc_item_defaults_create(1);ffi::mattmc_item_defaults_put(defaults,1,1,(1i64<<32)|1,64);let mut handles=vec![defaults];
  let empty=ffi_stack_for_run(0,0,defaults,&mut handles);let source=ffi_stack_for_run(7,if empty_source{0}else{11},defaults,&mut handles);let snapshot=ffi_stack_for_run(7,if empty_source{0}else{11},defaults,&mut handles);
  let inv=ffi::mattmc_item_inventory_create(empty);let link=ffi::mattmc_item_slot_create(inv,9);let destination=ffi::mattmc_item_slots_create(27,empty);let menu=ffi::mattmc_item_menu_create(empty,1);handles.extend([inv,link,destination,menu]);ffi::mattmc_item_slots_set(inv,9,source);
  unsafe{(*(ffi::mattmc_item_menu_rows_address(menu) as *const std::sync::atomic::AtomicI32)).store(3,Ordering::Relaxed);}
  for index in 0..63{let mut retired=0;assert_eq!(unsafe{ffi::mattmc_item_menu_edit(menu,1,index,100+index,if index==27{link}else{0},&mut retired)},0);}
  for index in 0..27{let target=ffi_stack_for_run(7,if index==0{60}else{64},defaults,&mut handles);ffi::mattmc_item_slots_set(destination,index,target);}
  UNIFIED_MENU_FIXTURE.with(|state|*state.borrow_mut()=Some(UnifiedMenuFixture{menu,link,source,snapshot,destination,events:Vec::new(),fail,prepared,empty:empty_source}));
  let result=unsafe{ffi::mattmc_item_menu_action(menu,27,99,0,Some(unified_menu_effect))};assert_eq!(result,if empty_source{0}else if fail{-2}else{1});assert_eq!(ffi_count(source),if empty_source{0}else{7});assert_eq!(ffi_count(snapshot),if empty_source{0}else{11});
  UNIFIED_MENU_FIXTURE.with(|state|{let f=state.borrow_mut().take().unwrap();assert_eq!(f.events,if empty_source{vec![1,12]}else{match(fail,prepared){(true,false)=>vec![1,3,7,15],(false,false)=>vec![1,3,7,15,9,12],(true,true)=>vec![1,7,15],(false,true)=>vec![1,7,15,9,12]}});});
  for handle in handles{assert_eq!(ffi::mattmc_item_owner_release(handle),1);}
 }
}

#[test] fn bounded_empty_source_uses_actual_leases_and_falls_back_on_every_contended_gate(){
 let d=defaults();let inv=Arc::new(inventory::Inventory::new(empty(&d)));let link=Arc::new(menu::SlotLink::new(inv.clone(),7).unwrap());
 let mut roster=menu::Menu::new(None);roster.insert(0,menu::MenuSlot{facade:81,link:Some(link.clone())});let owner=Arc::new(slots::BoundaryOwner::new(roster));let retained=owner.clone();let address=owner.address() as i64;drop(owner);drop(link);
 let check=|chest:i64|unsafe{ffi::mattmc_item_menu_empty_source_bounded(address,0,99,chest)};
 assert_eq!(check(0),1);{let _gate=retained.lock().unwrap();assert_eq!(check(0),0);}assert_eq!(check(0),1);
 {let _gate=inv.slots.lock().unwrap();assert_eq!(check(0),0);}assert_eq!(check(0),1);
 let rows=Arc::new(slots::BoundaryOwner::new(Slots::new(27,empty(&d))));let chest=Arc::new(slots::BoundaryOwner::new(container::Container::new(10,Some(container::Rows::Separate(rows.clone())))));
 retained.lock().unwrap().set(0,menu::MenuSlot{facade:82,link:Some(Arc::new(menu::SlotLink::for_container(chest.clone(),0).unwrap()))});
 assert_eq!(check(0),0);assert_eq!(check(inv.slots.address() as i64),0);let chest_address=chest.address() as i64;assert_eq!(check(chest_address),1);
 {let _gate=chest.lock().unwrap();assert_eq!(check(chest_address),0);}assert_eq!(check(chest_address),1);
 {let _gate=rows.lock().unwrap();assert_eq!(check(chest_address),0);}assert_eq!(check(chest_address),1);
 chest.lock().unwrap().replace(11,Some(container::Rows::Inventory(inv.clone())));assert_eq!(check(chest_address),1);
 {let _gate=inv.slots.lock().unwrap();assert_eq!(check(chest_address),0);}assert_eq!(check(chest_address),1);
 chest.lock().unwrap().replace(12,None);assert_eq!(check(chest_address),0);
 assert_eq!(unsafe{ffi::mattmc_item_menu_empty_source_bounded(address,-1,99,0)},0);assert_eq!(unsafe{ffi::mattmc_item_menu_empty_source_bounded(address,9,99,0)},0);assert_eq!(unsafe{ffi::mattmc_item_menu_empty_source_bounded(0,0,99,0)},0);
}

#[test] fn bounded_facade_reads_pin_actual_owners_and_reject_contention_without_mutation(){
 let d=defaults();let inv=Arc::new(inventory::Inventory::new(empty(&d)));let mut roster=menu::Menu::new(None);
 let link=Arc::new(menu::SlotLink::new(inv.clone(),7).unwrap());roster.insert(0,menu::MenuSlot{facade:81,link:Some(link.clone())});
 let original=Arc::new(slots::BoundaryOwner::new(roster));let owner=original.clone();let address=original.address() as i64;drop(original);
 assert_eq!(unsafe{ffi::mattmc_item_menu_facade_bounded(address,0)},81);{let _gate=owner.lock().unwrap();assert_eq!(unsafe{ffi::mattmc_item_menu_facade_bounded(address,0)},-2);}
 assert_eq!(unsafe{ffi::mattmc_item_menu_facade_bounded(address,-1)},-2);assert_eq!(unsafe{ffi::mattmc_item_menu_facade_bounded(address,1)},-2);
 let rows_address=inv.slots.address() as i64;let link_address=link.address() as i64;let source=stack(7,11,&d);inv.slots.lock().unwrap().set(7,Entry{facade:72,stack:source.clone()});
 assert_eq!(unsafe{ffi::mattmc_item_slots_facade_bounded(rows_address,7)},72);assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(link_address,99,0)},72);assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(link_address,99,1)},1);
 {let _gate=inv.slots.lock().unwrap();assert_eq!(unsafe{ffi::mattmc_item_slots_facade_bounded(rows_address,7)},-2);assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(link_address,99,0)},-1);}
 assert_eq!(unsafe{ffi::mattmc_item_slots_facade_bounded(rows_address,36)},-2);
 let rows=Arc::new(slots::BoundaryOwner::new(Slots::new(27,empty(&d))));let cell=Arc::new(slots::BoundaryOwner::new(container::Container::new(10,Some(container::Rows::Separate(rows.clone())))));
 let link=Arc::new(menu::SlotLink::for_container(cell.clone(),0).unwrap());let address=link.address() as i64;
 assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(address,99,0)},1);assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(address,99,1)},0);
 {let _gate=cell.lock().unwrap();assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(address,99,0)},-1);}{let _gate=rows.lock().unwrap();assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(address,99,0)},-1);}
 cell.lock().unwrap().replace(11,Some(container::Rows::Inventory(inv.clone())));inv.slots.lock().unwrap().set(0,Entry{facade:73,stack:source});assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(address,99,0)},73);
 cell.lock().unwrap().replace(12,None);assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(address,99,0)},-1);assert_eq!(unsafe{ffi::mattmc_item_slot_read_bounded(0,99,0)},-1);
}
