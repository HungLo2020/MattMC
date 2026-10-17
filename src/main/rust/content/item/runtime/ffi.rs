//! Synchronous structural operations only: every borrow ends before Java can
//! run a prototype/equality/codec callback. Handles own Arc references, not JVM roots.
use super::patch::{Entry, Key, Leaf, Overlay, Patch, Value};
use std::collections::HashMap;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::{Arc, Mutex, OnceLock};
use std::sync::atomic::{AtomicI64, Ordering};

const BAD: i64 = -2;
const PANIC: i64 = -3;
#[derive(Clone)]
enum Owner { EnchantmentSplit(Arc<super::components::EnchantmentSplit>), EnchantmentCursor(Arc<super::components::EnchantmentCursor>), Enchantments(Arc<super::components::Enchantments>), EnchantmentTable(Arc<super::components::EnchantmentTable>), Menu(Arc<super::slots::BoundaryOwner<super::menu::Menu>>), Container(Arc<super::slots::BoundaryOwner<super::container::Container>>), SlotLink(Arc<super::menu::SlotLink>), Equipment(Arc<super::slots::BoundaryOwner<super::container::Equipment>>), Inventory(Arc<super::inventory::Inventory>), Defaults(Arc<super::slots::BoundaryOwner<super::patch::defaults::Store>>), DefaultCursor(Arc<Mutex<DefaultCursorOwner>>), Patch(Patch), Overlay(Arc<Mutex<Overlay>>), Stack(Arc<super::stack::Stack>), Slots(Arc<super::slots::BoundaryOwner<super::slots::Slots>>), AtomicAccess(Arc<super::patch::AtomicAccess>), AccessPatch(super::patch::CanonicalPatch), AccessOverlay(Arc<Mutex<super::patch::CanonicalOverlay>>), Cell(Arc<super::patch::CanonicalCell>) }
fn owners() -> &'static Mutex<HashMap<i64, Owner>> {
    static OWNERS: OnceLock<Mutex<HashMap<i64, Owner>>> = OnceLock::new();
    OWNERS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn keep(owner: Owner) -> i64 {
    static NEXT: AtomicI64 = AtomicI64::new(1);
    let handle = NEXT.fetch_update(Ordering::Relaxed, Ordering::Relaxed, |id| id.checked_add(1))
        .expect("item owner handle space exhausted");
    owners().lock().unwrap().insert(handle, owner); handle
}
fn patch(handle: i64) -> Option<Patch> {
    let owner = owners().lock().unwrap().get(&handle).cloned();
    match owner { Some(Owner::Patch(patch)) => Some(patch), _ => None }
}
enum OverlayLease { Separate(Arc<Mutex<Overlay>>), Embedded(Arc<super::stack::Stack>) }
impl std::ops::Deref for OverlayLease {
    type Target=Mutex<Overlay>;
    fn deref(&self)->&Self::Target {match self {Self::Separate(owner)=>owner.as_ref(),Self::Embedded(owner)=>&owner.components}}
}
fn overlay(handle:i64)->Option<OverlayLease> {
    match owners().lock().unwrap().get(&handle).cloned() {
        Some(Owner::Overlay(owner))=>Some(OverlayLease::Separate(owner)),
        Some(Owner::Stack(owner))=>Some(OverlayLease::Embedded(owner)),_=>None,
    }
}
fn stack(handle: i64) -> Option<Arc<super::stack::Stack>> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::Stack(stack)) => Some(stack), _ => None }
}
use super::container::Rows as SlotsLease;
fn slots(handle:i64)->Option<SlotsLease> {
    match owners().lock().unwrap().get(&handle).cloned() {Some(Owner::Slots(owner))=>Some(SlotsLease::Separate(owner)),Some(Owner::Inventory(owner))=>Some(SlotsLease::Inventory(owner)),_=>None}
}
fn inventory(handle:i64)->Option<Arc<super::inventory::Inventory>> {
    match owners().lock().unwrap().get(&handle).cloned(){Some(Owner::Inventory(owner))=>Some(owner),_=>None}
}
fn atomic_access(handle: i64) -> Option<Arc<super::patch::AtomicAccess>> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::AtomicAccess(value)) => Some(value), _ => None }
}
struct DefaultCursorOwner { store: Arc<super::slots::BoundaryOwner<super::patch::defaults::Store>>, cursor: super::patch::defaults::Cursor }
fn defaults(handle:i64)->Option<Arc<super::slots::BoundaryOwner<super::patch::defaults::Store>>> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::Defaults(store))=>Some(store),_=>None }
}
fn default_cursor(handle:i64)->Option<Arc<Mutex<DefaultCursorOwner>>> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::DefaultCursor(cursor))=>Some(cursor),_=>None }
}
#[no_mangle] pub extern "C" fn mattmc_item_defaults_create(expected:i64)->i64 {
    guard(|| if (0..=1_000_000).contains(&expected) {keep(Owner::Defaults(Arc::new(super::slots::BoundaryOwner::new(super::patch::defaults::Store::new(expected as usize)))))}else{BAD})
}
#[no_mangle] pub extern "C" fn mattmc_item_defaults_address(handle:i64)->i64 {
    guard(|| defaults(handle).map(|s| &s.lock().unwrap().header as *const super::patch::defaults::Header as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_defaults_put(handle:i64,key:i64,leaf:i64,hash_kind:i64,scalar:i64)->i64 {
    guard(|| {let Some(s)=defaults(handle) else{return BAD};if leaf<=0{return BAD;}
        let result=s.lock().unwrap().put(super::patch::defaults::Row{key:key as u64,leaf,hash:hash_kind as u32,kind:((hash_kind as u64)>>32) as u32,scalar:scalar as i32,reserved:0}).map(|r|r.leaf).unwrap_or(0);result})
}
#[no_mangle] pub extern "C" fn mattmc_item_defaults_remove(handle:i64,key:i64,hash:i64)->i64 {
    guard(|| defaults(handle).map(|s|s.lock().unwrap().remove(key as u64,hash as u32).map(|r|r.leaf).unwrap_or(0)).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_defaults_clear(handle:i64)->i64 {
    guard(|| {let Some(s)=defaults(handle) else{return BAD};s.lock().unwrap().clear();0})
}
#[no_mangle] pub extern "C" fn mattmc_item_defaults_cursor(handle:i64)->i64 {
    guard(|| {let Some(store)=defaults(handle) else{return BAD};let cursor=super::patch::defaults::Cursor::new(&store.lock().unwrap());keep(Owner::DefaultCursor(Arc::new(Mutex::new(DefaultCursorOwner{store,cursor}))))})
}
/// 0=hasNext, 1=next physical position, 2=remove current; Java resolves retired leaf before releasing its root.
#[no_mangle] pub extern "C" fn mattmc_item_defaults_cursor_op(handle:i64,op:i64)->i64 {
    guard(|| {let Some(c)=default_cursor(handle) else{return BAD};let mut c=c.lock().unwrap();let store=c.store.clone();let mut store=store.lock().unwrap();
        match op {
            0=>i64::from(c.cursor.has_next(&store)),1=>c.cursor.next(&store).map(|i|i as i64).unwrap_or(-1),
            2=>c.cursor.remove(&mut store).map(|r|r.leaf).unwrap_or(-1),3=>c.cursor.failure as i64,
            4=>c.cursor.failure_index as i64,5=>c.cursor.wrapped_len() as i64,
            6=>c.cursor.next_foreach(&store).map(|i|i as i64).unwrap_or(-1),7=>c.cursor.position() as i64,
            8=>{c.cursor.foreach_complete(&store);0},_=>BAD}})
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_defaults(handle:i64,default_handle:i64)->i64 {
    guard(|| {let (Some(o),Some(d))=(overlay(handle),defaults(default_handle)) else{return BAD};o.lock().unwrap().defaults=Some(d);0})
}

fn guard(body: impl FnOnce() -> i64) -> i64 { catch_unwind(AssertUnwindSafe(body)).unwrap_or(PANIC) }

type HolderEffect = unsafe extern "C" fn(i64, i64, i64, i64) -> i64;
struct EnchantmentEffects { callback: HolderEffect }
impl super::components::HolderEffects for EnchantmentEffects {
    type Error = ();
    fn capture_table(&mut self, table: &Arc<super::components::EnchantmentTable>) -> Result<(), ()> {
        let result = unsafe { (self.callback)(10, Arc::as_ptr(table) as i64, 0, 0) };
        if result == i64::MIN { Err(()) } else { Ok(()) }
    }
    fn hash(&mut self, key: u64, table: &Arc<super::components::EnchantmentTable>) -> Result<i32, ()> {
        let value = unsafe { (self.callback)(0, key as i64, 0, Arc::as_ptr(table) as i64) };
        if value == i64::MIN { Err(()) } else { Ok(value as i32) }
    }
    fn equals(&mut self, query: u64, stored: u64, table: &Arc<super::components::EnchantmentTable>) -> Result<bool, ()> {
        let value = unsafe { (self.callback)(1, query as i64, stored as i64, Arc::as_ptr(table) as i64) };
        if value == i64::MIN { Err(()) } else { Ok(value != 0) }
    }
}
impl super::components::RootEffects for EnchantmentEffects {
    fn bind(&mut self, query: u64, table: &Arc<super::components::EnchantmentTable>) -> Result<u64, ()> {
        let result = unsafe { (self.callback)(2, query as i64, 0, Arc::as_ptr(table) as i64) };
        if result == i64::MIN { Err(()) } else { Ok(result as u64) }
    }
    fn prepare_table(&mut self, table: &Arc<super::components::EnchantmentTable>, source: &Arc<super::components::EnchantmentTable>) -> Result<(), ()> {
        let result = unsafe { (self.callback)(3, Arc::as_ptr(table) as i64, 0, Arc::as_ptr(source) as i64) };
        if result == i64::MIN { Err(()) } else { Ok(()) }
    }
    fn anchor(&mut self, key: u64, table: &Arc<super::components::EnchantmentTable>, source: &Arc<super::components::EnchantmentTable>) -> Result<(), ()> {
        let result = unsafe { (self.callback)(4, key as i64, Arc::as_ptr(table) as i64, Arc::as_ptr(source) as i64) };
        if result == i64::MIN { Err(()) } else { Ok(()) }
    }
    fn published(&mut self, table: &Arc<super::components::EnchantmentTable>) {
        unsafe { (self.callback)(5, Arc::as_ptr(table) as i64, 0, 0); }
    }
    fn retired(&mut self, key: u64, table: &Arc<super::components::EnchantmentTable>) {
        unsafe { (self.callback)(6, key as i64, 0, Arc::as_ptr(table) as i64); }
    }
}
impl super::components::CursorEffects for EnchantmentEffects {
    fn pin_wrapped(&mut self, key: u64, source: &Arc<super::components::EnchantmentTable>) -> Result<(), ()> {
        let result = unsafe { (self.callback)(7, key as i64, 0, Arc::as_ptr(source) as i64) };
        if result == i64::MIN { Err(()) } else { Ok(()) }
    }
    fn retire_wrapped(&mut self, key: u64, retire: bool) {
        unsafe { (self.callback)(8, key as i64, i64::from(retire), 0); }
    }
    fn accept(&mut self, index: usize) -> Result<(), ()> {
        let result = unsafe { (self.callback)(9, index as i64, 0, 0) };
        if result == i64::MIN { Err(()) } else { Ok(()) }
    }
}
fn enchantments(handle: i64) -> Option<Arc<super::components::Enchantments>> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::Enchantments(owner)) => Some(owner), _ => None }
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_owner_address(handle: i64) -> i64 {
    guard(|| enchantments(handle).map(|owner| Arc::as_ptr(&owner) as i64).unwrap_or(BAD))
}
/// Private tagged input: 0 opaque, signed integer << 2 | 1, actual native
/// Enchantments allocation address | 2, or 3 null. No JVM address is accepted.
unsafe fn component_payload(descriptor: i64) -> Result<Option<super::components::Payload>, ()> {
    use super::components::{Enchantments, Payload};
    match descriptor & 3 {
        0 if descriptor == 0 => Ok(None),
        1 => Ok(Some(Payload::Integer((descriptor >> 2) as i32))),
        2 => retain_vm_arc::<Enchantments>(descriptor & !3).map(|owner| Some(Payload::Enchantments(owner))).ok_or(()),
        3 if descriptor == 3 => Ok(Some(Payload::Null)), _ => Err(()),
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_component_roles(rows: *const i64, empty_enchantments: i64, empty_prototype: i64) -> i64 {
    guard(|| {
        if rows.is_null() || empty_prototype <= 0 { return BAD; }
        let Some(empty) = retain_vm_arc::<super::components::Enchantments>(empty_enchantments) else { return BAD; };
        let mut values = [super::components::Role { key: Key(0), hash: 0 }; 6];
        for (index, role) in values.iter_mut().enumerate() {
            let key = *rows.add(index * 2); if key <= 0 { return BAD; }
            *role = super::components::Role { key: Key(key as u64), hash: *rows.add(index * 2 + 1) as u32 };
        }
        i64::from(super::components::install_roles(super::components::Roles { values, empty_prototype: empty_prototype as u64, empty_enchantments: empty }))
    })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_defaults_put_payload(handle: i64, key: i64, leaf: i64, hash_kind: i64, descriptor: i64) -> i64 {
    guard(|| {
        if leaf <= 0 { return BAD; }
        let (Some(owner), Ok(payload)) = (defaults(handle), component_payload(descriptor)) else { return BAD; };
        let row = super::patch::defaults::Row { key: key as u64, leaf, hash: hash_kind as u32, kind: ((hash_kind as u64) >> 32) as u32, scalar: 0, reserved: 0 };
        let result = owner.lock().unwrap().put_payload(row, payload).map(|old| old.map(|row| row.leaf).unwrap_or(0));
        result.unwrap_or(BAD)
    })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_patch_put_payload(handle: i64, key: i64, value: i64, descriptor: i64) -> i64 {
    guard(|| {
        let (Some(owner), Some(value), Ok(payload)) = (patch(handle), decoded(value), component_payload(descriptor)) else { return BAD; };
        if value == Value::Removed && payload.is_some() { return BAD; }
        owner.put_payload(Key(key as u64), value, payload).map(encoded).unwrap_or(BAD)
    })
}
fn enchantment_split(handle: i64) -> Option<Arc<super::components::EnchantmentSplit>> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::EnchantmentSplit(split)) => Some(split), _ => None }
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_split_create(handle: i64) -> i64 {
    guard(|| enchantments(handle).map(|owner| keep(Owner::EnchantmentSplit(Arc::new(super::components::EnchantmentSplit::new(owner))))).unwrap_or(BAD))
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_split_prepare(handle: i64) -> i64 {
    guard(|| enchantment_split(handle).map(|parent| parent.prepare_split().map(|child| keep(Owner::EnchantmentSplit(Arc::new(child)))).unwrap_or(0)).unwrap_or(BAD))
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_split_commit(parent: i64, child: i64) -> i64 {
    guard(|| match (enchantment_split(parent), enchantment_split(child)) {
        (Some(parent), Some(child)) => i64::from(parent.commit_split(&child)), _ => BAD,
    })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_enchantments_split_op(handle: i64, operation: i64, argument: i64, callback: HolderEffect) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(split) = enchantment_split(handle) else { return i64::MIN + 2; };
        let mut effects = EnchantmentEffects { callback };
        let result = match operation {
            0 => Ok(split.estimate()), 1 => Ok(split.characteristics()), 2 => Ok(i64::from(split.can_split())),
            3 => split.advance(&mut effects).map(i64::from),
            4 => split.for_each(&mut effects).map(|()| 0),
            5 => match split.skip(argument) {
                Ok(value) => Ok(value),
                Err(super::components::SplitSkipError::Negative) => return i64::MIN + 20,
                Err(super::components::SplitSkipError::Bounds(index, len)) => Err(super::components::LookupError::Bounds(index, len)),
            }, _ => return i64::MIN + 2,
        };
        enchantment_result(result, callback)
    })).unwrap_or(i64::MIN + 3)
}
fn enchantment_result(result: Result<i64, super::components::LookupError<()>>, callback: HolderEffect) -> i64 {
    match result {
        Ok(value) => value,
        Err(super::components::LookupError::Effect(())) => i64::MIN,
        Err(super::components::LookupError::Bounds(index, len)) => { unsafe { callback(12, index as i64, len as i64, 0); } i64::MIN },
        Err(super::components::LookupError::StalePublication) => i64::MIN + 1,
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_enchantments_query(handle: i64, operation: i64, query: i64, argument: i64, callback: HolderEffect) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = enchantments(handle) else { return i64::MIN + 2; };
        let mut effects = EnchantmentEffects { callback };
        let result = match operation {
            0 => owner.find(query as u64, &mut effects).map(|found| i64::from(found.is_ok())),
            1 => Ok(i64::from(owner.contains_value(argument as i32))),
            2 => Ok(owner.capacity() as i64), 3 => Ok(i64::from(owner.contains_null())),
            4 => Ok(owner.default_level() as i64),
            5 => { owner.set_default_level(argument as i32); Ok(0) },
            6 => Ok(i64::from(owner.clear())),
            7 => owner.try_capacity(argument, &mut effects).map(|()| 0),
            8 => owner.merge_max(query as u64, argument as i32, &mut effects).map(|level| level as i64),
            9 => owner.map_hash(&mut effects).map(|hash| hash as i64),
            10 => owner.visit_desc(&mut effects).map(|()| 0),
            11 | 12 => match owner.find(query as u64, &mut effects) {
                Ok(Ok(index)) => match owner.entry_level::<()>(index, None) {
                    Ok(level) if level == argument as i32 => if operation == 12 { owner.remove_at(index, &mut effects).map(|_| 1) } else { Ok(1) },
                    Ok(_) => Ok(0), Err(error) => Err(error),
                }, Ok(Err(_)) => Ok(0), Err(error) => Err(error),
            },
            _ => return i64::MIN + 2,
        };
        enchantment_result(result, callback)
    })).unwrap_or(i64::MIN + 3)
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_table_entry(handle: i64, index: i64, level: i64, write: i64, callback: HolderEffect) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let table = match owners().lock().unwrap().get(&handle).cloned() {
            Some(Owner::EnchantmentTable(table)) => table, _ => return i64::MIN + 2,
        };
        let result = if write != 0 { table.set_level(index as usize, level as i32) } else { table.row(index as usize).map(|row| row.level) };
        enchantment_result(result.map(|level| level as i64).ok_or_else(|| super::components::LookupError::Bounds(index as usize, table.len())), callback)
    })).unwrap_or(i64::MIN + 3)
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_create(expected: i64) -> i64 {
    guard(|| {
        // HashCommon.arraySize(expected, 0.75f) permits capacities through 2^30.
        if !(0..=805_306_368).contains(&expected) { return BAD; }
        let n = (((expected as usize) * 4).div_ceil(3)).next_power_of_two().max(2);
        keep(Owner::Enchantments(Arc::new(super::components::Enchantments::new(n))))
    })
}
/// The caller has pre-registered the Java cleanup resource before acquiring this
/// real table lease. The owner is strongly reachable throughout the downcall.
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_table(handle: i64) -> i64 {
    guard(|| enchantments(handle).map(|owner| keep(Owner::EnchantmentTable(owner.table.lease()))).unwrap_or(BAD))
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_table_address(handle: i64) -> i64 {
    guard(|| match owners().lock().unwrap().get(&handle) {
        Some(Owner::EnchantmentTable(table)) => Arc::as_ptr(table) as i64, _ => BAD,
    })
}
/// Only an actual allocation lease supplied by the synchronous owner effect is
/// valid here. This is a native Arc address, never a Java object/heap address.
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_enchantments_table_retain(address: i64) -> i64 {
    guard(|| retain_vm_arc::<super::components::EnchantmentTable>(address)
        .map(|table| keep(Owner::EnchantmentTable(table))).unwrap_or(BAD))
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_table_read(handle: i64, index: i64, mode: i64) -> i64 {
    guard(|| {
        let selected = match owners().lock().unwrap().get(&handle).cloned() {
            Some(Owner::EnchantmentTable(table)) => table, _ => return BAD,
        };
        if mode == 2 { return selected.len() as i64; }
        if index < 0 { return i64::MIN; }
        match selected.row(index as usize) {
            Some(row) => if mode == 0 { row.key as i64 } else { row.level as i64 },
            None => i64::MIN,
        }
    })
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_cursor_create(handle: i64) -> i64 {
    guard(|| enchantments(handle).map(|owner| keep(Owner::EnchantmentCursor(Arc::new(super::components::EnchantmentCursor::new(owner))))).unwrap_or(BAD))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_enchantments_cursor_op(handle: i64, operation: i64, argument: i64, callback: HolderEffect) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let cursor = match owners().lock().unwrap().get(&handle).cloned() {
            Some(Owner::EnchantmentCursor(cursor)) => cursor, _ => return i64::MIN + 2,
        };
        if operation == 8 { return i64::from(cursor.contains_wrapped(argument as u64)); }
        if operation >= 5 { return cursor.failure.lock().unwrap().get((operation - 5) as usize).copied().unwrap_or(BAD); }
        let mut effects = EnchantmentEffects { callback };
        let result = match operation {
            0 => Ok(i64::from(cursor.has_next())),
            1 => cursor.next(&mut effects).map(|index| index as i64),
            2 => cursor.remove(&mut effects).map(|()| 0),
            3 => cursor.for_each(&mut effects).map(|()| 0),
            4 => cursor.skip(argument as i32, &mut effects).map(|count| count as i64),
            _ => return i64::MIN + 2,
        };
        use super::components::{CursorError, LookupError};
        let (result, failure) = match result {
            Ok(value) => (value, [0; 3]),
            Err(CursorError::NoSuchElement) => (i64::MIN + 10, [10, 0, 0]),
            Err(CursorError::IllegalState) => (i64::MIN + 11, [11, 0, 0]),
            Err(CursorError::WrappedNull) => (i64::MIN + 12, [12, 0, 0]),
            Err(CursorError::WrappedBounds(index, len)) => (i64::MIN + 13, [13, index as i64, len as i64]),
            Err(CursorError::Lookup(LookupError::Effect(()))) => (i64::MIN, [0; 3]),
            Err(CursorError::Lookup(LookupError::Bounds(index, len))) => (i64::MIN + 14, [14, index as i64, len as i64]),
            Err(CursorError::Lookup(LookupError::StalePublication)) => (i64::MIN + 1, [1, 0, 0]),
        };
        *cursor.failure.lock().unwrap() = failure;
        result
    })).unwrap_or(i64::MIN + 3)
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_table_contains(handle: i64, token: i64) -> i64 {
    guard(|| match owners().lock().unwrap().get(&handle).cloned() {
        Some(Owner::EnchantmentTable(table)) => i64::from(table.contains_token(token as u64)), _ => BAD,
    })
}
#[no_mangle]
pub extern "C" fn mattmc_item_enchantments_size(handle: i64) -> i64 {
    guard(|| enchantments(handle).map(|owner| owner.size() as i64).unwrap_or(BAD))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_enchantments_get(handle: i64, query: i64, missing: i64, use_missing: i64, callback: HolderEffect) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = enchantments(handle) else { return i64::MIN + 2; };
        let mut effects = EnchantmentEffects { callback };
        match owner.get(query as u64, (use_missing != 0).then_some(missing as i32), &mut effects) {
            Ok(level) => level as i64,
            Err(super::components::LookupError::Effect(())) => i64::MIN,
            Err(super::components::LookupError::Bounds(index, len)) => { (callback)(12, index as i64, len as i64, 0); i64::MIN },
            Err(super::components::LookupError::StalePublication) => i64::MIN + 1,
        }
    })).unwrap_or(i64::MIN + 3)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_enchantments_put(handle: i64, query: i64, level: i64, callback: HolderEffect) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = enchantments(handle) else { return i64::MIN + 2; };
        let mut effects = EnchantmentEffects { callback };
        match owner.put(query as u64, level as i32, &mut effects) {
            Ok(level) => level as i64,
            Err(super::components::LookupError::Effect(())) => i64::MIN,
            Err(super::components::LookupError::Bounds(index, len)) => { (callback)(12, index as i64, len as i64, 0); i64::MIN },
            Err(super::components::LookupError::StalePublication) => i64::MIN + 1,
        }
    })).unwrap_or(i64::MIN + 3)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_enchantments_remove(handle: i64, query: i64, _unused: i64, callback: HolderEffect) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(owner) = enchantments(handle) else { return i64::MIN + 2; };
        let mut effects = EnchantmentEffects { callback };
        match owner.remove(query as u64, &mut effects) {
            Ok(level) => level as i64,
            Err(super::components::LookupError::Effect(())) => i64::MIN,
            Err(super::components::LookupError::Bounds(index, len)) => { (callback)(12, index as i64, len as i64, 0); i64::MIN },
            Err(super::components::LookupError::StalePublication) => i64::MIN + 1,
        }
    })).unwrap_or(i64::MIN + 3)
}

fn encoded(value: Option<Value>) -> i64 {
    match value { None => -1, Some(Value::Removed) => 0, Some(Value::Present(leaf)) => leaf.0 as i64 }
}
fn decoded(value: i64) -> Option<Value> {
    match value { 0 => Some(Value::Removed), 1.. => Some(Value::Present(Leaf(value as u64))), _ => None }
}

#[no_mangle] pub extern "C" fn mattmc_item_patch_create() -> i64 { guard(|| keep(Owner::Patch(Patch::default()))) }
#[no_mangle] pub extern "C" fn mattmc_item_patch_share(handle: i64) -> i64 {
    guard(|| patch(handle).map(|p| keep(Owner::Patch(p))).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_snapshot(handle: i64) -> i64 {
    guard(|| patch(handle).map(|p| keep(Owner::Patch(p.detached()))).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_version(handle: i64) -> i64 {
    guard(|| patch(handle).map(|p| p.version() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_size(handle: i64) -> i64 {
    guard(|| patch(handle).map(|p| p.len() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_address(handle: i64) -> i64 {
    guard(|| patch(handle).map(|p| p.view_address() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_get(handle: i64, key: i64) -> i64 {
    guard(|| patch(handle).map(|p| encoded(p.get(Key(key as u64)))).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_put(handle: i64, key: i64, value: i64) -> i64 {
    guard(|| match (patch(handle), decoded(value)) {
        (Some(p), Some(value)) => encoded(p.put(Key(key as u64), value)), _ => BAD,
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_remove(handle: i64, key: i64) -> i64 {
    guard(|| patch(handle).map(|p| encoded(p.remove(Key(key as u64)))).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_patch_clear(handle: i64) -> i64 {
    guard(|| match patch(handle) { Some(p) => { p.clear(); 0 }, None => BAD })
}
/// Writes key and value for one live cursor entry. 0=end, 1=entry, negatives=error.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_patch_at(handle: i64, index: i64, out: *mut i64) -> i64 {
    guard(|| {
        let Some(p) = patch(handle) else { return BAD };
        if index < 0 || out.is_null() { return BAD; }
        match p.at(index as usize) {
            Some(entry) => { *out = entry.key.0 as i64; *out.add(1) = encoded(Some(entry.value)); 1 }, None => 0,
        }
    })
}

#[no_mangle] pub extern "C" fn mattmc_item_overlay_create(prototype: i64) -> i64 {
    guard(|| keep(Owner::Overlay(Arc::new(Mutex::new(Overlay::new(prototype as u64))))))
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_from_patch(prototype: i64, handle: i64) -> i64 {
    guard(|| patch(handle).map(|p| keep(Owner::Overlay(Arc::new(Mutex::new(Overlay::from_sanitized(prototype as u64, p)))))).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_version(handle: i64) -> i64 {
    guard(|| overlay(handle).map(|o| o.lock().unwrap().patch_version() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_get(handle: i64, key: i64) -> i64 {
    guard(|| overlay(handle).map(|o| encoded(o.lock().unwrap().get_override(Key(key as u64)))).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_size(handle: i64) -> i64 {
    guard(|| overlay(handle).map(|o| o.lock().unwrap().patch_len() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_prepare(handle: i64) -> i64 {
    guard(|| {
        let Some(o) = overlay(handle) else { return BAD }; let mut o = o.lock().unwrap();
        o.prepare_change(); o.patch_version() as i64
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_copy(handle: i64) -> i64 {
    guard(|| {
        let Some(o) = overlay(handle) else { return BAD };
        let copied = { o.lock().unwrap().copy() };
        keep(Owner::Overlay(Arc::new(Mutex::new(copied))))
    })
}
/// Iteration retains the selected backing but must not mark it copy-on-write.
#[no_mangle] pub extern "C" fn mattmc_item_overlay_view(handle: i64) -> i64 {
    guard(|| {
        let Some(o) = overlay(handle) else { return BAD };
        let view = { o.lock().unwrap().view_patch() };
        keep(Owner::Patch(view))
    })
}
/// 0=Java's singleton EMPTY; otherwise a new handle sharing the escaped backing.
#[no_mangle] pub extern "C" fn mattmc_item_overlay_as_patch(handle: i64) -> i64 {
    guard(|| {
        let Some(o) = overlay(handle) else { return BAD };
        let escaped = { o.lock().unwrap().as_patch() };
        escaped.map(|p| keep(Owner::Patch(p))).unwrap_or(0)
    })
}
/// Out: original visible return, previous override, next override, backing version.
/// Java resolves the old value before applying the root-reference changes.
unsafe fn result(out: *mut i64, visible: Option<Leaf>, old: Option<Value>, new: Option<Value>, version: u64) {
    *out = visible.map(|v| v.0 as i64).unwrap_or(0); *out.add(1) = encoded(old);
    *out.add(2) = encoded(new); *out.add(3) = version as i64;
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_overlay_set(handle: i64, key: i64, value: i64, prototype: i64, equal: i32, out: *mut i64) -> i64 {
    guard(|| {
        if value < 0 || prototype < 0 || out.is_null() { return BAD; }
        let Some(o) = overlay(handle) else { return BAD }; let mut o = o.lock().unwrap(); let key = Key(key as u64);
        let old = o.get_override(key); let proto = (prototype != 0).then_some(Leaf(prototype as u64));
        let visible = o.set_after_compare(key, (value != 0).then_some(Leaf(value as u64)), proto, equal != 0);
        result(out, visible, old, o.get_override(key), o.patch_version()); 0
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_overlay_set_payload(handle: i64, key: i64, value: i64, prototype: i64, equal: i32, descriptor: i64, out: *mut i64) -> i64 {
    guard(|| {
        if value < 0 || prototype < 0 || out.is_null() { return BAD; }
        let (Some(owner), Ok(payload)) = (overlay(handle), component_payload(descriptor)) else { return BAD; };
        if value == 0 && payload.is_some() { return BAD; }
        let mut owner = owner.lock().unwrap(); let key = Key(key as u64);
        let old = owner.get_override(key); let proto = (prototype != 0).then_some(Leaf(prototype as u64));
        let Ok(visible) = owner.set_after_compare_payload(key, (value != 0).then_some(Leaf(value as u64)), proto, equal != 0, payload) else { return BAD; };
        result(out, visible, old, owner.get_override(key), owner.patch_version()); 0
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_overlay_remove(handle: i64, key: i64, prototype: i64, out: *mut i64) -> i64 {
    guard(|| {
        if prototype < 0 || out.is_null() { return BAD; }
        let Some(o) = overlay(handle) else { return BAD }; let mut o = o.lock().unwrap(); let key = Key(key as u64);
        let old = o.get_override(key); let visible = o.remove_after_lookup(key, (prototype != 0).then_some(Leaf(prototype as u64)));
        result(out, visible, old, o.get_override(key), o.patch_version()); 0
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_overlay_apply(handle: i64, key: i64, value: i64, prototype_exists: i32, equal: i32, out: *mut i64) -> i64 {
    guard(|| {
        let Some(value) = decoded(value) else { return BAD };
        if out.is_null() { return BAD; }
        let Some(o) = overlay(handle) else { return BAD }; let mut o = o.lock().unwrap(); let key = Key(key as u64);
        let old = o.get_override(key); o.apply_after_compare(Entry { key, value }, prototype_exists != 0, equal != 0);
        result(out, None, old, o.get_override(key), o.patch_version()); 0
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_overlay_apply_payload(handle: i64, key: i64, value: i64, prototype_exists: i32, equal: i32, descriptor: i64, out: *mut i64) -> i64 {
    guard(|| {
        if out.is_null() { return BAD; }
        let (Some(owner), Some(value), Ok(payload)) = (overlay(handle), decoded(value), component_payload(descriptor)) else { return BAD; };
        if value == Value::Removed && payload.is_some() { return BAD; }
        let mut owner = owner.lock().unwrap(); let key = Key(key as u64); let old = owner.get_override(key);
        if owner.apply_after_compare_payload(Entry { key, value }, prototype_exists != 0, equal != 0, payload).is_err() { return BAD; }
        result(out, None, old, owner.get_override(key), owner.patch_version()); 0
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_clear(handle: i64) -> i64 {
    guard(|| {
        let Some(o) = overlay(handle) else { return BAD }; let mut o = o.lock().unwrap();
        o.clear(); o.patch_version() as i64
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_stack_create(item: i64, count: i64, components: i64, empty_identity: i64) -> i64 {
    guard(|| {
        let Some(components) = overlay(components) else { return BAD };
        let Ok(count) = i32::try_from(count) else { return BAD };
        if !(0..=1).contains(&empty_identity) || (empty_identity!=0 && item!=0) {return BAD;}
        let components=match components {OverlayLease::Separate(owner)=>super::stack::Components::Shared(owner),OverlayLease::Embedded(owner)=>super::stack::Components::from_stack(owner)};
        let mut stack=super::stack::Stack::with_components(item as u64,count,components);stack.empty_identity=empty_identity!=0;
        keep(Owner::Stack(Arc::new(stack)))
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_stack_address(handle: i64) -> i64 {
    guard(|| stack(handle).map(|s| s.address() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_slots_create(size: i64, default_stack: i64) -> i64 {
    guard(|| {
        let Some(stack) = stack(default_stack) else { return BAD };
        if !(0..=65536).contains(&size) { return BAD; }
        let empty = super::slots::Entry { facade: default_stack, stack };
        keep(Owner::Slots(Arc::new(super::slots::BoundaryOwner::new(super::slots::Slots::new(size as usize, empty)))))
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_slots_get(handle: i64, index: i64) -> i64 {
    guard(|| {
        let Some(slots) = slots(handle) else { return BAD };
        if index < 0 { return BAD; }
        let slots = slots.lock().unwrap(); slots.get(index as usize).map(|e| e.facade).unwrap_or(BAD)
    })
}
/// Registers the exact Arc in a pending row so Java can build its canonical
/// facade outside the row gate. The row itself is unchanged until bind. The
/// recipe's captured component roots must describe this copy's actual backing
/// (`version`); otherwise no facade may be built from them (-3).
#[no_mangle] pub extern "C" fn mattmc_item_slots_materialize(handle:i64,index:i64,key:i64,version:i64)->i64 {
    guard(||{let (Some(rows),Ok(index))=(slots(handle),usize::try_from(index)) else{return BAD};
        if !super::slots::is_pending(key){return BAD;}
        let stack={let rows=rows.lock().unwrap();match rows.get(index){Some(entry) if entry.facade==key=>entry.stack.clone(),_=>return BAD}};
        if stack.components.lock().unwrap().patch_version()!=version as u64{return -3;}
        keep(Owner::Stack(stack))})
}
/// A registered copy of pending contents for a broadcast comparison/packet,
/// leaving the row pending. Refused like materialize on a backing mismatch
/// (-3), and for empty contents (-4), whose original copy is EMPTY.
#[no_mangle] pub extern "C" fn mattmc_item_slots_pending_copy(handle:i64,index:i64,key:i64,version:i64)->i64 {
    guard(||{let (Some(rows),Ok(index))=(slots(handle),usize::try_from(index)) else{return BAD};
        if !super::slots::is_pending(key){return BAD;}
        let stack={let rows=rows.lock().unwrap();match rows.get(index){Some(entry) if entry.facade==key=>entry.stack.clone(),_=>return BAD}};
        if stack.components.lock().unwrap().patch_version()!=version as u64{return -3;}
        let count=stack.scalars.count.load(Ordering::Relaxed);if stack.empty_identity||count<=0{return -4;}
        super::slots::PENDING_BROADCAST_COPIES.fetch_add(1,Ordering::Relaxed);
        prepare_copy(&stack,count).0})
}
/// Binds the constructed facade only if the row still holds that key and Arc.
/// Returns 0 when the row changed meanwhile, otherwise 1, plus 2 when no other
/// row still carries the key (Java then retires its recipe).
#[no_mangle] pub extern "C" fn mattmc_item_slots_bind(handle:i64,index:i64,key:i64,facade:i64)->i64 {
    guard(||{let (Some(rows),Some(stack),Ok(index))=(slots(handle),stack(facade),usize::try_from(index)) else{return BAD};
        let mut rows=rows.lock().unwrap();
        if !rows.get(index).is_some_and(|entry|entry.facade==key&&Arc::ptr_eq(&entry.stack,&stack)){return 0;}
        rows.set(index,super::slots::Entry{facade,stack});super::slots::PENDING_MATERIALIZED.fetch_add(1,Ordering::Relaxed);
        1|(i64::from(rows.entries_facade_index(key)<0)<<1)})
}
#[no_mangle] pub extern "C" fn mattmc_item_slots_set(handle: i64, index: i64, facade: i64) -> i64 {
    guard(|| {
        let (Some(slots), Some(stack)) = (slots(handle), stack(facade)) else { return BAD };
        if index < 0 { return BAD; }
        let value = super::slots::Entry { facade, stack };
        let mut slots = slots.lock().unwrap(); slots.set(index as usize, value).map(|e| e.facade).unwrap_or(BAD)
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_atomic_create(length: i64) -> i64 {
    guard(|| {
        let Ok(length) = usize::try_from(length) else { return BAD };
        super::patch::AtomicAccess::new(length).map(|value| keep(Owner::AtomicAccess(Arc::new(value)))).unwrap_or(BAD)
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_atomic_address(handle: i64) -> i64 {
    guard(|| atomic_access(handle).map(|value| value.address() as i64).unwrap_or(BAD))
}
/// The caller passes an aligned cell from a live retained allocation. This
/// bounded operation has no allocation, locks, handle lookup or Java callback.
/// Java publishes its strong binding before this release; readers acquire-load
/// the same cell and validate the complete binding incarnation before use.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_atomic_swap(cell: *const std::sync::atomic::AtomicU64, value: i64) -> i64 {
    if cell.is_null() || value < 0 { return BAD; }
    (*cell).swap(value as u64, Ordering::AcqRel) as i64
}
#[no_mangle] pub extern "C" fn mattmc_item_owner_release(handle: i64) -> i64 {
    guard(|| i64::from(owners().lock().unwrap().remove(&handle).is_some()))
}
#[no_mangle] pub extern "C" fn mattmc_item_owner_live(handle: i64) -> i64 {
    guard(|| i64::from(owners().lock().unwrap().contains_key(&handle)))
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn atomic_access_lease_retains_fixed_allocation_and_swap_has_native_authority() {
        let handle = mattmc_item_atomic_create(2); let lease = atomic_access(handle).unwrap();
        let address = mattmc_item_atomic_address(handle); assert_eq!(address as usize, lease.address());
        let cell = address as *const std::sync::atomic::AtomicU64;
        unsafe {
            assert_eq!(mattmc_item_atomic_swap(cell, 0x100000001), 0);
            assert_eq!((*cell).load(Ordering::Acquire), 0x100000001);
            assert_eq!(mattmc_item_atomic_swap(cell, 1), 0x100000001);
        }
        assert_eq!(mattmc_item_owner_release(handle), 1); assert_eq!(mattmc_item_atomic_address(handle), BAD);
        unsafe { assert_eq!((*cell).load(Ordering::Acquire), 1); }
        drop(lease);
    }
    #[test] fn escaped_handle_survives_original_release_and_stale_handle_is_rejected() {
        let p = mattmc_item_patch_create(); assert_eq!(mattmc_item_patch_put(p, 99, 123), -1);
        let shared = mattmc_item_patch_share(p); let version = mattmc_item_patch_version(p);
        assert_eq!(mattmc_item_owner_release(p), 1); assert_eq!(mattmc_item_patch_get(p, 99), BAD);
        assert_eq!(mattmc_item_patch_version(shared), version); assert_eq!(mattmc_item_patch_get(shared, 99), 123);
        assert_eq!(mattmc_item_owner_release(shared), 1); assert_eq!(mattmc_item_owner_release(shared), 0);
    }
    #[test] fn ffi_callback_boundary_keeps_original_shared_write_and_reports_root_deltas() {
        let o = mattmc_item_overlay_create(7); mattmc_item_overlay_prepare(o); let mut out = [0i64; 4];
        unsafe { assert_eq!(mattmc_item_overlay_set(o, 1, 10, 0, 0, out.as_mut_ptr()), 0); }
        assert_eq!(&out[..3], &[0, -1, 10]);
        mattmc_item_overlay_prepare(o);
        let p = mattmc_item_overlay_as_patch(o); let copy = mattmc_item_overlay_copy(o);
        unsafe { assert_eq!(mattmc_item_overlay_set(o, 2, 20, 11, 0, out.as_mut_ptr()), 0); }
        assert_eq!(&out[..3], &[11, -1, 20]); assert_eq!(mattmc_item_patch_get(p, 2), 20); assert_eq!(mattmc_item_overlay_get(copy, 2), 20);
        let original_version = out[3]; assert_ne!(mattmc_item_overlay_prepare(o), original_version);
        unsafe { mattmc_item_overlay_remove(o, 2, 0, out.as_mut_ptr()); }
        assert_eq!(&out[..3], &[20, 20, -1]); assert_eq!(mattmc_item_patch_get(p, 2), 20);
        for h in [o, p, copy] { assert_eq!(mattmc_item_owner_release(h), 1); }
    }
    #[test] fn snapshot_and_live_cursor_observe_distinct_backings_and_later_append() {
        let p = mattmc_item_patch_create(); mattmc_item_patch_put(p, 1, 10); let snap = mattmc_item_patch_snapshot(p);
        mattmc_item_patch_put(p, 2, 0); let mut out = [0i64; 2];
        unsafe { assert_eq!(mattmc_item_patch_at(p, 1, out.as_mut_ptr()), 1); }
        assert_eq!(out, [2, 0]); assert_eq!(mattmc_item_patch_size(snap), 1);
        assert_ne!(mattmc_item_patch_version(p), mattmc_item_patch_version(snap));
        assert_eq!(mattmc_item_patch_put(p, 3, -1), BAD);
        for h in [p, snap] { mattmc_item_owner_release(h); }
    }
}

// Full dynamic-owner experiment; ordinary downcalls, never marked critical.
fn access_patch(handle: i64) -> Option<super::patch::CanonicalPatch> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::AccessPatch(p)) => Some(p), _ => None }
}
fn access_overlay(handle: i64) -> Option<Arc<Mutex<super::patch::CanonicalOverlay>>> {
    match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::AccessOverlay(p)) => Some(p), _ => None }
}
#[no_mangle] pub extern "C" fn mattmc_item_access_create(prototype: i64) -> i64 {
    guard(|| keep(Owner::AccessOverlay(Arc::new(Mutex::new(super::patch::CanonicalOverlay::new(prototype as u64))))))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_view(handle: i64) -> i64 {
    guard(|| access_overlay(handle).map(|p| { let patch = p.lock().unwrap().view(); keep(Owner::AccessPatch(patch)) }).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_escape(handle: i64) -> i64 {
    guard(|| access_overlay(handle).map(|p| { let patch = p.lock().unwrap().escape(); patch.map(|p| keep(Owner::AccessPatch(p))).unwrap_or(0) }).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_copy(handle: i64) -> i64 {
    guard(|| access_overlay(handle).map(|p| { let copy = p.lock().unwrap().copy(); keep(Owner::AccessOverlay(Arc::new(Mutex::new(copy)))) }).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_prepare(handle: i64) -> i64 {
    guard(|| access_overlay(handle).map(|p| p.lock().unwrap().prepare() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_version(handle: i64) -> i64 {
    guard(|| access_patch(handle).map(|p| p.version() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_address(handle: i64) -> i64 {
    guard(|| access_patch(handle).map(|p| p.address() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_put(handle: i64, key: i64, token: i64) -> i64 {
    guard(|| {
        if key <= 0 || token < 0 { return BAD; }
        access_overlay(handle).map(|p| p.lock().unwrap().put_prepared(Key(key as u64), token as u64) as i64).unwrap_or(BAD)
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_access_at(handle: i64, index: i64, out: *mut i64) -> i64 {
    guard(|| {
        if index < 0 || out.is_null() { return BAD; }
        let Some(p) = access_patch(handle) else { return BAD; };
        if let Some((key, value)) = p.at(index as usize) { *out = key.0 as i64; *out.add(1) = value as i64; 1 } else { 0 }
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_access_patch_put(handle: i64, key: i64, token: i64) -> i64 {
    guard(|| {
        if key <= 0 || token < 0 { return BAD; }
        access_patch(handle).map(|p| p.put(Key(key as u64), token as u64) as i64).unwrap_or(BAD)
    })
}
/// flags: equal=1, prototype present=2, remove operation=4.
#[no_mangle] pub extern "C" fn mattmc_item_access_finish(handle: i64, key: i64, token: i64, flags: i64) -> i64 {
    guard(|| {
        if key <= 0 || token < 0 { return BAD; }
        let next = if flags & 4 != 0 { if flags & 2 != 0 { 1 } else { 0 } }
            else if flags & 1 != 0 { 0 } else if token == 0 { 1 } else { token as u64 };
        access_overlay(handle).map(|p| p.lock().unwrap().put_prepared(Key(key as u64), next) as i64).unwrap_or(BAD)
    })
}
/// A retained owner lease pins the actual COW control allocation.
#[no_mangle] pub extern "C" fn mattmc_item_access_retain(handle: i64) -> i64 {
    guard(|| access_overlay(handle).map(|o| keep(Owner::AccessOverlay(o))).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_cow_address(handle: i64) -> i64 {
    guard(|| access_overlay(handle).map(|o| o.lock().unwrap().cow_address() as i64).unwrap_or(BAD))
}
/// Stage native construction without changing the original root or COW flag.
#[no_mangle] pub extern "C" fn mattmc_item_access_stage(handle: i64) -> i64 {
    guard(|| access_overlay(handle).map(|o| {
        let staged = o.lock().unwrap().stage(); staged.map(|p| keep(Owner::AccessPatch(p))).unwrap_or(0)
    }).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_access_commit(handle: i64, expected: i64, staged: i64) -> i64 {
    guard(|| {
        let (Some(owner), Some(staged)) = (access_overlay(handle), access_patch(staged)) else { return BAD; };
        let version = staged.version();
        if owner.lock().unwrap().commit(expected as u64, staged) { version as i64 } else { BAD }
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_access_cell_retain(handle: i64, address: i64) -> i64 {
    guard(|| {
        if address <= 0 { return BAD; }
        access_patch(handle).and_then(|p| p.retain_cell(address as usize)).map(|cell| keep(Owner::Cell(cell))).unwrap_or(BAD)
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_access_cell_address(handle: i64) -> i64 {
    guard(|| match owners().lock().unwrap().get(&handle).cloned() { Some(Owner::Cell(cell)) => cell.address() as i64, _ => BAD })
}
/// Unsafe caller contract: actual backing/cell lease stays alive and the SAME
/// root guard excludes alias writes, cell reuse AND snapshot sharing throughout.
/// 0=fallback before effects; 1=committed. No allocation/lock/lookup/upcall/loop.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_access_replace(
    address: i64, owner: i64, key: i64, incarnation: i64, expected: i64, next: i64, out: *mut i64,
) -> i64 {
    if address <= 0 || owner <= 0 || key <= 0 || incarnation <= 0 || expected <= 1 || next <= 1 || out.is_null() { return 0; }
    match super::patch::canonical_replace(address as *const super::patch::CanonicalCell, owner as u64, key as u64,
        incarnation as u64, expected as u64, next as u64) {
        Some(old) => { *out = old as i64; *out.add(1) = next; 1 }, None => 0,
    }
}

#[no_mangle] pub extern "C" fn mattmc_item_transfer_step(slots_handle:i64,source_handle:i64,air:i64,phase:i64,index:i64)->i64 {
    let result=catch_unwind(AssertUnwindSafe(|| {
        let (Some(slots),Some(source))=(slots(slots_handle),stack(source_handle)) else{return 7};
        if index<0 || phase<0 {return 7;}
        let slots=slots.lock().unwrap();super::slots::transfer_step(&slots,&source,air as u64,phase as u64,index as usize)
    }));result.unwrap_or(7)
}
#[no_mangle] pub extern "C" fn mattmc_item_transfer_insert(slots_handle:i64,source_handle:i64,index:i64,inserted_handle:i64,amount:i64)->i64 {
    guard(|| {
        let (Some(slots),Some(source),Some(inserted))=(slots(slots_handle),stack(source_handle),stack(inserted_handle)) else{return BAD};
        if index<0 || i32::try_from(amount).is_err(){return BAD;}
        let inserted=super::slots::Entry{facade:inserted_handle,stack:inserted};let mut slots=slots.lock().unwrap();
        super::slots::transfer_insert(&mut slots,&source,index as usize,inserted,amount as i32).map(|e|e.facade).unwrap_or(BAD)
    })
}
#[no_mangle] pub extern "C" fn mattmc_item_transfer_counter(which:i64)->i64 {
    match which {0=>super::slots::TRANSFER_STEPS.load(Ordering::Relaxed) as i64,1=>super::slots::TRANSFER_NATIVE_DEFAULTS.load(Ordering::Relaxed) as i64,2=>super::slots::TRANSFER_NATIVE_SLOTS.load(Ordering::Relaxed) as i64,
        3=>PREPARED_COPIES.load(Ordering::Relaxed) as i64,4=>PREPARED_RETIRED.load(Ordering::Relaxed) as i64,
        5=>super::slots::PENDING_PUBLISHED.load(Ordering::Relaxed) as i64,6=>super::slots::PENDING_MATERIALIZED.load(Ordering::Relaxed) as i64,7=>super::slots::PENDING_ROLLED_BACK.load(Ordering::Relaxed) as i64,8=>super::slots::PENDING_BROADCAST_COPIES.load(Ordering::Relaxed) as i64,
        9=>super::inventory::TICKS_NATIVE.load(Ordering::Relaxed) as i64,_=>BAD}
}

/// Copy actual scalar/overlay state as one owner. Java's scalar scope pins this
/// same allocation for both stack and independently escaped component facades.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_stack_copy(handle:i64,out:*mut i64)->i64 {
    guard(|| {
        if out.is_null(){return BAD;}
        let Some(source)=stack(handle)else{return BAD};
        let item=source.scalars.item;let count=source.scalars.count.load(Ordering::Relaxed);
        let copied={source.components.lock().unwrap().copy()};
        let mut copied=super::stack::Stack::from_overlay(item,count,copied);copied.empty_identity=source.empty_identity;
        let copied=Arc::new(copied);let address=copied.address();let owner=keep(Owner::Stack(copied));*out=address as i64;owner
    })
}

/// Shared complete consumer: actual leases remain in this native operation,
/// while every owner/row/default guard ends before an external effect.
fn transfer_run_owned<F:FnMut(i64,i64,i64,i64,i64)->i64>(slots:&SlotsLease,source:&super::stack::Stack,air:u64,effect:F)->i64 {
    transfer_run_steps(|phase,index,previous,stats|match slots {
        SlotsLease::Inventory(owner)=>owner.transfer_step(source,air,previous),
        SlotsLease::Separate(owner)=>{let rows=owner.lock().unwrap();super::slots::transfer_step_counted(&rows,source,air,phase,index,stats)}
    },effect)
}
/// The same consumer over a double chest's two halves (one 54-row forward
/// destination). Both guards end before each effect; first half locks first.
fn transfer_run_pair<F:FnMut(i64,i64,i64,i64,i64)->i64>(first:&super::slots::BoundaryOwner<super::slots::Slots>,second:&super::slots::BoundaryOwner<super::slots::Slots>,source:&super::stack::Stack,air:u64,effect:F)->i64 {
    transfer_run_steps(|phase,index,_,stats|{
        let a=first.lock().unwrap();let b=second.lock().unwrap();super::slots::transfer_step_pair(&a,&b,source,air,phase,index,stats)
    },effect)
}
fn transfer_run_steps<S:FnMut(u64,usize,i64,&mut super::slots::TransferStats)->i64,F:FnMut(i64,i64,i64,i64,i64)->i64>(mut step:S,mut effect:F)->i64 {
    let mut phase=0u64;let mut index=0usize;let mut previous=0i64;let mut changed=false;let mut stats=super::slots::TransferStats::default();
    loop {
        let result=step(phase,index,previous,&mut stats);
        let bits=result as u64;let action=bits&7;let selected=(bits>>3)&63;phase=(bits>>9)&3;let amount=(result>>32) as i32;
        changed|=bits&2048!=0;
        if action==0{return i64::from(changed);}if action==7{return BAD;}
        let returned=effect(action as i64,selected as i64,phase as i64,amount as i64,i64::from(changed));
        if action==1&&returned==2{changed=true;index=selected as usize+1;previous=result;continue;}
        return returned;
    }
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_transfer_run(slots_handle:i64,source_handle:i64,air:i64,callback:Option<unsafe extern "C" fn(i64,i64,i64,i64,i64)->i64>)->i64 {
    guard(||{let (Some(slots),Some(source),Some(callback))=(slots(slots_handle),stack(source_handle),callback) else{return BAD};
        transfer_run_owned(&slots,&source,air as u64,|a,b,c,d,e|callback(a,b,c,d,e))})
}

/// An escaped view retains the actual same allocation, independently of its private facade lease.
#[no_mangle] pub extern "C" fn mattmc_item_stack_retain(handle:i64)->i64 {
    guard(|| stack(handle).map(|value|keep(Owner::Stack(value))).unwrap_or(BAD))
}

#[cfg(test)] mod stack_lease_tests {
    use super::*;
    #[test] fn retained_scalar_lease_is_same_allocation_after_original_handle_release() {
        let overlay=mattmc_item_overlay_create(17);let original=mattmc_item_stack_create(7,27,overlay,0);
        let retained=mattmc_item_stack_retain(original);let address=mattmc_item_stack_address(original);
        assert_eq!(mattmc_item_stack_address(retained),address);assert_ne!(retained,original);
        assert_eq!(mattmc_item_owner_release(original),1);assert_eq!(mattmc_item_stack_address(original),BAD);
        assert_eq!(mattmc_item_stack_address(retained),address);
        assert_eq!(stack(retained).unwrap().scalars.count.load(Ordering::Relaxed),27);
        assert_eq!(mattmc_item_owner_release(retained),1);assert_eq!(mattmc_item_owner_release(overlay),1);
    }
}

/// Actual owner addresses only. Java pins the owning allocation lease, then
/// excludes the gate at offset zero before borrowing its contents.
#[no_mangle] pub extern "C" fn mattmc_item_defaults_owner_address(handle:i64)->i64 {
    guard(||defaults(handle).map(|owner|owner.address() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_slots_owner_address(handle:i64)->i64 {
    guard(||slots(handle).map(|owner|owner.address() as i64).unwrap_or(BAD))
}
fn transfer_resume(previous:i64)->(u64,usize,bool) {
    let bits=previous as u64;let action=bits&7;
    ((bits>>9)&3,((bits>>3)&63) as usize+usize::from(action==1),bits&2048!=0 || action==1)
}
/// Bounded critical body: actual retained Arc leases plus gates already held by
/// the caller, no acquisition, allocation, handle lookup, mutex or Java call.
/// Scalars are the first field of repr(C) Stack, so its leased scalar address
/// is also the actual Stack address. Nothing is borrowed beyond this call.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_transfer_bounded(slots_address:i64,source_address:i64,defaults_address:i64,air:i64,previous:i64)->i64 {
    if slots_address<=0 || source_address<=0 || defaults_address<=0 {return 7;}
    let slots=&*(slots_address as *const super::slots::BoundaryOwner<super::slots::Slots>);
    let source=&*(source_address as *const super::stack::Stack);
    let defaults=&*(defaults_address as *const super::slots::BoundaryOwner<super::patch::defaults::Store>);
    let (phase,index,changed)=transfer_resume(previous);
    let result=super::slots::transfer_step_admitted(slots.admitted(),source,defaults,air as u64,phase,index);
    result | if changed {2048}else{0}
}
/// Noncritical remaining native work when the bounded admission is unavailable.
/// It continues from the native token without replaying a committed effect.
#[no_mangle] pub extern "C" fn mattmc_item_transfer_continue(slots_handle:i64,source_handle:i64,air:i64,previous:i64)->i64 {
    let (phase,index,changed)=transfer_resume(previous);
    mattmc_item_transfer_step(slots_handle,source_handle,air,phase as i64,index as i64) | if changed {2048}else{0}
}

/// Promote the existing overlay lease in place at the original stack-construction
/// point. Its already-registered handle-only cleanup now releases this Stack;
/// no second facade lease or native registry handle is created.
#[no_mangle] pub extern "C" fn mattmc_item_stack_promote(handle:i64,item:i64,count:i64,singleton:i64)->i64 {
    guard(|| {
        let components=match owners().lock().unwrap().get(&handle).cloned() {Some(Owner::Overlay(owner))=>owner,_=>return BAD};
        let Ok(count)=i32::try_from(count) else{return BAD};
        if !(0..=1).contains(&singleton) || (singleton!=0 && item!=0) {return BAD;}
        let mut value=super::stack::Stack::new(item as u64,count,components.clone());value.empty_identity=singleton!=0;
        let mut value=Arc::new(value);let address=value.address() as i64;
        let mut table=owners().lock().unwrap();
        match table.get(&handle) {Some(Owner::Overlay(current)) if Arc::ptr_eq(current,&components)=>{},_=>return BAD}
        if Arc::strong_count(&components)==3 {
            // Exactly registry + local + unpublished Stack references. Resolve
            // admission before taking this registry guard, never lock a component
            // while holding it. Reuse the existing map slot and facade handle.
            drop(table.remove(&handle));drop(components);
            Arc::get_mut(&mut value).unwrap().inline_unique_components();
        }
        table.insert(handle,Owner::Stack(value));address
    })
}

#[cfg(test)] mod promotion_tests {
    use super::*;
    #[test] fn promotion_reuses_actual_component_lease_and_retained_scalar_arc() {
        let handle=mattmc_item_overlay_create(17);let components=overlay(handle).unwrap();
        let address=mattmc_item_stack_promote(handle,7,i32::MIN as i64,0);assert!(address>0);
        let value=stack(handle).unwrap();assert!(std::ptr::eq(&*components,&*value.components));
        assert_eq!(mattmc_item_stack_address(handle),address);assert_eq!(value.scalars.count.load(Ordering::Relaxed),i32::MIN);
        assert_eq!(mattmc_item_stack_promote(handle,9,11,0),BAD,"a second facade must never share one stack identity token");
        assert_eq!(value.scalars.item,7);assert_eq!(value.scalars.count.load(Ordering::Relaxed),i32::MIN);
        let retained=mattmc_item_stack_retain(handle);drop(value);drop(components);
        assert_eq!(mattmc_item_owner_release(handle),1);assert_eq!(mattmc_item_stack_address(handle),BAD);
        assert_eq!(mattmc_item_stack_address(retained),address);assert_eq!(mattmc_item_owner_release(retained),1);
    }
    #[test] fn rejected_promotion_preserves_existing_component_owner_for_retry() {
        let handle=mattmc_item_overlay_create(17);let original=overlay(handle).unwrap();
        assert_eq!(mattmc_item_stack_promote(handle,7,11,1),BAD);
        assert!(stack(handle).is_none());assert!(std::ptr::eq(&*original,&*overlay(handle).unwrap()));
        assert!(mattmc_item_stack_promote(handle,0,0,1)>0);assert!(stack(handle).unwrap().empty_identity);
        assert_eq!(mattmc_item_owner_release(handle),1);
    }
}

#[cfg(test)] mod embedded_component_tests {
    use super::*;
    #[test] fn unique_promotion_embeds_mutex_without_changing_escaped_patch_allocation_or_version() {
        let handle=mattmc_item_overlay_create(17);
        {let owner=overlay(handle).unwrap();let mut owner=owner.lock().unwrap();owner.prepare_change();owner.set_after_compare(Key(7),Some(Leaf(11)),None,false);}
        let view=mattmc_item_overlay_view(handle);let before=patch(view).unwrap();let version=before.version();let address=before.view_address();
        assert!(mattmc_item_stack_promote(handle,7,11,0)>0);
        let owner=stack(handle).unwrap();assert!(matches!(&owner.components,super::super::stack::Components::Inline(_)));
        assert_eq!(owner.components.lock().unwrap().patch_version(),version);assert_eq!(patch(view).unwrap().view_address(),address);
        before.put(Key(7),Value::Present(Leaf(22)));assert_eq!(owner.components.lock().unwrap().get_override(Key(7)),Some(Value::Present(Leaf(22))));
        assert_eq!(mattmc_item_owner_release(handle),1);drop(owner);
        assert_eq!(patch(view).unwrap().get(Key(7)),Some(Value::Present(Leaf(22))));assert_eq!(mattmc_item_owner_release(view),1);
    }
    #[test] fn retained_component_alias_preserves_actual_allocation_and_shared_mutation() {
        let handle=mattmc_item_overlay_create(17);let alias=overlay(handle).unwrap();let address=&*alias as *const Mutex<Overlay>;
        assert!(mattmc_item_stack_promote(handle,7,11,0)>0);let owner=stack(handle).unwrap();
        assert!(matches!(&owner.components,super::super::stack::Components::Shared(_)));assert_eq!(&*owner.components as *const Mutex<Overlay>,address);
        {let mut value=alias.lock().unwrap();value.prepare_change();value.set_after_compare(Key(7),Some(Leaf(22)),None,false);}
        assert_eq!(owner.components.lock().unwrap().get_override(Key(7)),Some(Value::Present(Leaf(22))));
        assert_eq!(mattmc_item_owner_release(handle),1);drop(owner);assert_eq!(alias.lock().unwrap().get_override(Key(7)),Some(Value::Present(Leaf(22))));
    }
    #[test] fn copied_stack_embeds_components_and_compatibility_aliases_flatten_to_actual_owner() {
        let handle=mattmc_item_overlay_create(17);assert!(mattmc_item_stack_promote(handle,7,11,0)>0);
        let mut address=0;let copied=unsafe{mattmc_item_stack_copy(handle,&mut address)};let value=stack(copied).unwrap();
        assert!(matches!(&value.components,super::super::stack::Components::Inline(_)));assert_eq!(value.address() as i64,address);
        let first=mattmc_item_stack_create(7,22,copied,0);let second=mattmc_item_stack_create(7,33,first,0);
        for alias in [first,second] {
            let alias=stack(alias).unwrap();let super::super::stack::Components::Parent(root)=&alias.components else{panic!("inline alias must retain actual containing allocation")};
            assert!(Arc::ptr_eq(root,&value));assert!(std::ptr::eq(&*alias.components,&*value.components));
        }
        assert_eq!(mattmc_item_owner_release(copied),1);assert_eq!(mattmc_item_owner_release(first),1);drop(value);
        assert_eq!(stack(second).unwrap().scalars.count.load(Ordering::Relaxed),33);
        assert_eq!(mattmc_item_owner_release(second),1);assert_eq!(mattmc_item_owner_release(handle),1);
    }
}

#[no_mangle] pub extern "C" fn mattmc_item_inventory_create(empty:i64)->i64 {
    guard(||{let Some(stack)=stack(empty) else{return BAD};keep(Owner::Inventory(Arc::new(super::inventory::Inventory::new(super::slots::Entry{facade:empty,stack}))))})
}
#[no_mangle] pub extern "C" fn mattmc_item_inventory_address(handle:i64)->i64 {
    guard(||inventory(handle).map(|owner|owner.address() as i64).unwrap_or(BAD))
}
#[no_mangle] pub extern "C" fn mattmc_item_inventory_query(handle:i64,air:i64,operation:i64)->i64 {
    guard(||{let Some(owner)=inventory(handle) else{return BAD};match operation{0=>owner.free_slot(air as u64) as i64,1=>i64::from(owner.main_empty(air as u64)),_=>BAD}})
}
#[no_mangle] pub extern "C" fn mattmc_item_inventory_swap(handle:i64,index:i64)->i64 {
    guard(||{let Some(owner)=inventory(handle) else{return BAD};if index<0{return BAD;}if owner.swap_selected(index as usize){1}else{BAD}})
}
#[no_mangle] pub extern "C" fn mattmc_item_inventory_retain(handle:i64)->i64 {
    guard(||inventory(handle).map(|owner|keep(Owner::Inventory(owner))).unwrap_or(BAD))
}

#[no_mangle] pub extern "C" fn mattmc_item_inventory_transfer(handle:i64,source:i64,air:i64,previous:i64)->i64 {
    guard(||{let (Some(owner),Some(source))=(inventory(handle),stack(source)) else{return 7};owner.transfer_step(&source,air as u64,previous)})
}

#[no_mangle] pub unsafe extern "C" fn mattmc_item_inventory_add(handle:i64,source:i64,index:i64,air:i64,effect:Option<unsafe extern "C" fn(i64,i64)->i64>)->i64 {
    guard(||{
        let (Some(owner),Some(source),Some(effect))=(inventory(handle),stack(source),effect) else{return BAD};
        let Ok(index)=i32::try_from(index) else{return BAD};
        match owner.add_run(&source,index,air as u64,|action,index| {
            let result=effect(action,index as i64);
            if result==i64::MIN {Err(())}else{i32::try_from(result).map_err(|_|())}
        }){Ok(value)=>i64::from(value),Err(())=>i64::MIN}
    })
}

#[no_mangle] pub unsafe extern "C" fn mattmc_item_inventory_scan(handle:i64,source_handle:i64,mode:i64,air:i64,effect:Option<unsafe extern "C" fn(i64,i64)->i64>)->i64 {
    guard(||{
        let (Some(owner),Some(effect))=(inventory(handle),effect) else{return BAD};
        if mode==5 {return owner.first_facade(source_handle) as i64;}
        let source=if source_handle==0 {None}else{let Some(value)=stack(source_handle) else{return BAD};Some(value)};
        let Ok(mode)=i32::try_from(mode) else{return BAD};
        match owner.scan_run(source.as_deref(),mode,air as u64,|action,index|{
            let result=effect(action,index as i64);if result==i64::MIN {Err(())}else{i32::try_from(result).map_err(|_|())}
        }) {Ok(value)=>value as i64,Err(())=>i64::MIN}
    })
}

#[no_mangle] pub extern "C" fn mattmc_item_slots_clear(handle:i64,empty:i64)->i64 {
    guard(||{
        let (Some(owner),Some(stack))=(slots(handle),stack(empty)) else{return BAD};
        owner.lock().unwrap().fill(super::slots::Entry{facade:empty,stack});1
    })
}

#[no_mangle] pub unsafe extern "C" fn mattmc_item_inventory_visit(handle:i64,source_handle:i64,mode:i64,limit:i64,air:i64,effect:Option<unsafe extern "C" fn(i64,i64,i64)->i64>)->i64 {
    guard(||{
        let (Some(owner),Some(effect))=(inventory(handle),effect) else{return BAD};
        let source=if source_handle==0 {None}else{let Some(value)=stack(source_handle) else{return BAD};Some(value)};
        let (Ok(mode),Ok(limit))=(i32::try_from(mode),i32::try_from(limit)) else{return BAD};
        match owner.visit_run(source.as_deref(),mode,limit,air as u64,|action,index,facade|{
            let result=effect(action,index as i64,facade);if result==i64::MIN {Err(())}else{i32::try_from(result).map_err(|_|())}
        }) {Ok(value)=>i64::from(value),Err(())=>i64::MIN}
    })
}

fn equipment(handle:i64)->Option<Arc<super::slots::BoundaryOwner<super::container::Equipment>>> {
    match owners().lock().unwrap().get(&handle).cloned(){Some(Owner::Equipment(owner))=>Some(owner),_=>None}
}
#[no_mangle] pub extern "C" fn mattmc_item_equipment_create(size:i64)->i64 {
    guard(||{if !(1..=64).contains(&size){return BAD;}keep(Owner::Equipment(Arc::new(super::slots::BoundaryOwner::new(super::container::Equipment::new(size as usize)))))})
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_equipment_op(handle:i64,operation:i64,index:i64,value:i64,out:*mut i64)->i64 {
    guard(||{
        use super::container::EquipmentValue as V;
        if out.is_null(){return BAD;}out.write(0);
        let Some(owner)=equipment(handle) else{return BAD};
        let next=if operation==1 {if value==0 {Some(V::Null)}else{let Some(stack)=stack(value) else{return BAD};Some(V::Stack(super::slots::Entry{facade:value,stack}))}}
            else if operation==6 {let Some(stack)=stack(value) else{return BAD};Some(V::Stack(super::slots::Entry{facade:value,stack}))}else{None};
        let mut rows=owner.lock().unwrap();
        match operation {
            0=>{if index<0{return BAD;}rows.get(index as usize).map_or(BAD,|entry|entry.token())},
            1|2=>{if index<0{return BAD;}let Some(old)=rows.set(index as usize,next.unwrap_or(V::Absent)) else{return BAD};let token=old.token();if token>0&&!rows.references(token){out.write(token);}token},
            3=>{if index<0{return 0;}rows.get(index as usize).map_or(0,|entry|i64::from(!matches!(entry,V::Absent)))},
            4=>rows.size() as i64,
            5=>{rows.clear_keys();0},
            6=>{let Some(V::Stack(empty))=next else{return BAD};rows.clear_values(empty);0},
            7=>{if index<0{return BAD;}rows.next(index as usize)},
            _=>BAD
        }
    })
}

fn slot_link(handle:i64)->Option<Arc<super::menu::SlotLink>> {
    match owners().lock().unwrap().get(&handle).cloned(){Some(Owner::SlotLink(owner))=>Some(owner),_=>None}
}
#[no_mangle] pub extern "C" fn mattmc_item_slot_create(inventory_handle:i64,index:i64)->i64 {
    guard(||{let Some(owner)=inventory(inventory_handle) else{return BAD};let Ok(index)=i32::try_from(index) else{return BAD};
        super::menu::SlotLink::new(owner,index).map_or(BAD,|link|keep(Owner::SlotLink(Arc::new(link))))})
}
#[no_mangle] pub extern "C" fn mattmc_item_slot_address(handle:i64)->i64 {guard(||slot_link(handle).map_or(BAD,|link|link.address() as i64))}
#[no_mangle] pub extern "C" fn mattmc_item_slot_retain(handle:i64)->i64 {guard(||slot_link(handle).map_or(BAD,|link|keep(Owner::SlotLink(link))))}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_slot_read(address:i64,owner:i64,air:i64,mode:i64)->i64 {
    if address<=0||owner<=0||!(0..=1).contains(&mode){return -1;}
    (&*(address as *const super::menu::SlotLink)).read_admitted(owner as usize,air as u64,mode==1)
}

fn container(handle:i64)->Option<Arc<super::slots::BoundaryOwner<super::container::Container>>> {
    match owners().lock().unwrap().get(&handle).cloned(){Some(Owner::Container(owner))=>Some(owner),_=>None}
}
#[no_mangle] pub extern "C" fn mattmc_item_container_create(token:i64,source:i64)->i64 {
    guard(||{if token<0||source<0{return BAD;}
        let rows=if source==0{None}else{let Some(rows)=slots(source) else{return BAD};Some(rows)};
        keep(Owner::Container(Arc::new(super::slots::BoundaryOwner::new(super::container::Container::new(token,rows)))))})
}
#[no_mangle] pub extern "C" fn mattmc_item_container_address(handle:i64)->i64 {
    guard(||{let Some(owner)=container(handle) else{return BAD};let address=owner.lock().unwrap().address() as i64;address})
}
#[no_mangle] pub extern "C" fn mattmc_item_container_replace(handle:i64,token:i64,source:i64)->i64 {
    guard(||{if token<0||source<0{return BAD;}let Some(owner)=container(handle) else{return BAD};
        let rows=if source==0{None}else{let Some(rows)=slots(source) else{return BAD};Some(rows)};
        owner.lock().unwrap().replace(token,rows);0})
}
#[no_mangle] pub extern "C" fn mattmc_item_slot_container_create(container_handle:i64,index:i64)->i64 {
    guard(||{let Some(owner)=container(container_handle) else{return BAD};let Ok(index)=i32::try_from(index) else{return BAD};
        super::menu::SlotLink::for_container(owner,index).map_or(BAD,|link|keep(Owner::SlotLink(Arc::new(link))))})
}
#[no_mangle] pub extern "C" fn mattmc_item_slot_read_regular(handle:i64,air:i64,mode:i64)->i64 {
    guard(||{if !(0..=2).contains(&mode){return BAD;}slot_link(handle).map_or(BAD,|link|if mode==2{link.digest()}else{link.read(air as u64,mode==1)})})
}

fn menu(handle:i64)->Option<Arc<super::slots::BoundaryOwner<super::menu::Menu>>> {
    match owners().lock().unwrap().get(&handle).cloned(){Some(Owner::Menu(owner))=>Some(owner),_=>None}
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_create(carried:i64,kind:i64)->i64 {
    guard(||{if !(1..=2).contains(&kind){return BAD;}let entry=if carried==0{None}else{let Some(stack)=stack(carried) else{return BAD};Some(super::slots::Entry{facade:carried,stack})};
        let mut value=super::menu::Menu::new(entry);value.kind=kind as i32;keep(Owner::Menu(Arc::new(super::slots::BoundaryOwner::new(value))))})
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_address(handle:i64)->i64 {
    guard(||{let Some(owner)=menu(handle) else{return BAD};let address=owner.lock().unwrap().address() as i64;address})
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_get(handle:i64,index:i64)->i64 {
    guard(||{let Some(owner)=menu(handle) else{return BAD};if index<0{return BAD;}
        let value=owner.lock().unwrap().get(index as usize).map_or(BAD,|slot|slot.facade);value})
}
/// 0=set,1=insert,2=remove. Resolve any retired Java facade BEFORE releasing its root.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_menu_edit(handle:i64,op:i64,index:i64,facade:i64,link:i64,retired:*mut i64)->i64 {
    guard(||{if retired.is_null()||index<0{return BAD;}retired.write(0);let Some(owner)=menu(handle) else{return BAD};
        let next=if op==0||op==1 {if facade<=0{return BAD;}let link=if link==0{None}else{let Some(link)=slot_link(link) else{return BAD};Some(link)};Some(super::menu::MenuSlot{facade,link})}else{None};
        let mut rows=owner.lock().unwrap();let old=match op{
            0=>rows.set(index as usize,next.unwrap()),
            1=>return if rows.insert(index as usize,next.unwrap()){0}else{BAD},
            2=>rows.remove(index as usize),_=>return BAD};
        let Some(old)=old else{return BAD};if !rows.references(old.facade){retired.write(old.facade);}old.facade})
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_carried(handle:i64,carried:i64)->i64 {
    guard(||{let Some(owner)=menu(handle) else{return BAD};let next=if carried==0{None}else{let Some(stack)=stack(carried) else{return BAD};Some(super::slots::Entry{facade:carried,stack})};
        owner.lock().unwrap().set_carried(next);0})
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_retain(handle:i64)->i64 {guard(||menu(handle).map_or(BAD,|owner|keep(Owner::Menu(owner))))}
/// `ItemStack.isSameItemSameComponents` for two non-empty stacks (scalar
/// addresses): 1 equal, 0 different, -1 when a foreign or unpaired component
/// value needs the original Java `equals` (slots.rs `inventory_same`).
#[no_mangle] pub unsafe extern "C" fn mattmc_item_stack_same_components(left:i64,right:i64,air:i64)->i64 {
    guard(||{let (Some(a),Some(b))=(menu_stack(left),menu_stack(right)) else{return BAD};
        match super::slots::inventory_same(&a,&b,air as u64){Some(true)=>1,Some(false)=>0,None=>-1}})
}
/// Bounded critical form of `mattmc_item_stack_same_components` over the
/// caller-fenced stacks (scalar addresses are the repr(C) Stack addresses): no
/// blocking lock, allocation, handle lookup or upcall. -1 sends the caller to
/// the regular entry; -2 means the original Java equals must decide.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_stack_same_bounded(left:i64,right:i64,air:i64)->i64 {
    if left<=0||right<=0{return -1;}
    let (a,b)=(&*(left as *const super::stack::Stack),&*(right as *const super::stack::Stack));
    match super::slots::inventory_same_bounded(a,b,air as u64){Ok(Some(true))=>1,Ok(Some(false))=>0,Ok(None)=>-2,Err(())=>-1}
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_double_match(handle:i64,first_handle:i64,second_handle:i64,inventory_handle:i64)->i64 {
    guard(||{let (Some(owner),Some(first),Some(second),Some(inventory))=(menu(handle),container(first_handle),container(second_handle),inventory(inventory_handle)) else{return BAD};
        let result=owner.lock().unwrap().matches_double(&first,&second,&inventory);i64::from(result)})
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_stock_match(handle:i64,chest_handle:i64,inventory_handle:i64,reverse:i64)->i64 {
    guard(||{let (Some(owner),Some(chest))=(menu(handle),container(chest_handle)) else{return BAD};if !(0..=1).contains(&reverse){return BAD;}
        let inventory=inventory(inventory_handle);let result=owner.lock().unwrap().matches_stock(&chest,inventory.as_ref(),reverse!=0);i64::from(result)})
}

// Private synchronous callback contract: Java effect fields strongly retain the
// actual owning facades until native acquisition finishes. The addresses below
// come only from those owners' verified allocation views, never public pointer
// constructors or generation tokens. Rust acquires its own Arc before another
// effect may clear/replace that Java anchor. Cleanup policy is unchanged.
pub(super) unsafe fn retain_vm_arc<T>(address:i64)->Option<Arc<T>> {
    if address<=0||(address as usize)&(std::mem::align_of::<T>()-1)!=0{return None;}
    let pointer=address as *const T;Arc::increment_strong_count(pointer);Some(Arc::from_raw(pointer))
}
fn menu_stack(address:i64)->Option<Arc<super::stack::Stack>>{unsafe{retain_vm_arc(address)}}
fn menu_link(address:i64)->Option<Arc<super::menu::SlotLink>>{unsafe{retain_vm_arc(address)}}
fn menu_destination(address:i64,kind:i64)->Option<SlotsLease>{unsafe{match kind {
    0=>retain_vm_arc(address).map(SlotsLease::Separate),
    1=>retain_vm_arc(address).map(SlotsLease::Inventory),_=>None,
}}}
type MenuCallback=unsafe extern "C" fn(i64,i64,i64,i64,i64)->i64;
/// A menu transfer's destination: one row owner, or a double chest's two
/// halves (kind 2 from op 7; op 19 supplies the second half's owner).
enum TransferDestination {Rows(SlotsLease),Pair(Arc<super::slots::BoundaryOwner<super::slots::Slots>>,Arc<super::slots::BoundaryOwner<super::slots::Slots>>)}
struct MenuEffects {callback:MenuCallback,source:Option<Arc<super::stack::Stack>>,prepared:Option<(Arc<super::stack::Stack>,Arc<super::stack::Stack>)>,known_has:Option<bool>,air:u64,pending:bool}
impl MenuEffects {
    fn call(&mut self,action:i64,a:i64,b:i64,c:i64)->Result<(i64,[i64;2]),()> {self.call_with(action,a,b,c,[0;2])}
    fn call_with(&mut self,action:i64,a:i64,b:i64,c:i64,mut out:[i64;2])->Result<(i64,[i64;2]),()> {
        let result=unsafe{(self.callback)(action,a,b,c,out.as_mut_ptr() as i64)};
        if result==i64::MIN{Err(())}else{Ok((result,out))}
    }
    /// An empty-destination insertion: the copy Java's original
    /// `copyForNativeTransferWithCount` made (components copied, count and pop
    /// time set) is prepared here and adopted by Java's insertion effects
    /// (op 16), which then publish it. An unadopted copy is retired here.
    fn insert_prepared(&mut self,source:&super::stack::Stack,index:i64,amount:i64)->i64 {
        let Ok(count)=i32::try_from(amount) else{return i64::MIN};
        let (handle,address,_)=prepare_copy(source,count);
        // The adoption flag is read even when Java threw: an adopted copy belongs
        // to its Java release, an unadopted one only to this operation.
        let mut out=[address as i64,0];
        let result=unsafe{(self.callback)(16,index,amount,handle,out.as_mut_ptr() as i64)};
        if out[1]==0{retire_unadopted(handle);}
        result
    }
    /// Publishes the copy straight into the destination row under a pending
    /// key, without a Java facade, registry handle or Cleaner. Op 18 then lets
    /// Java record the materialization recipe (adoption flag in out[1]) and run
    /// the original post-publication effects. If Java fails before recording,
    /// nothing ran in between, so the publication and source shrink are undone.
    fn publish_pending(&mut self,destination:&TransferDestination,source:&Arc<super::stack::Stack>,selected:i64,amount:i64,reverse:bool)->i64 {
        let (Ok(amount32),Ok(position))=(i32::try_from(amount),usize::try_from(selected)) else{return i64::MIN};
        // The owner and its row: a double chest's position selects the half.
        let (destination,index):(&super::slots::BoundaryOwner<super::slots::Slots>,usize)=match destination {
            TransferDestination::Rows(rows)=>(rows,super::slots::target_index(position,reverse&&matches!(rows,SlotsLease::Inventory(_)))),
            TransferDestination::Pair(first,second)=>{let split=first.lock().unwrap().len();if position<split{(first,position)}else{(second,position-split)}}
        };
        let copy=copy_stack(source,amount32);let key=super::slots::next_pending_key();
        let (old,retired)={
            let mut rows=destination.lock().unwrap();
            let Some(old)=super::slots::transfer_insert(&mut rows,source,index,super::slots::Entry{facade:key,stack:copy},amount32) else{return i64::MIN};
            let retired=rows.entries_facade_index(old.facade)<0;(old,retired)
        };
        super::slots::PENDING_PUBLISHED.fetch_add(1,Ordering::Relaxed);
        let mut out=[if retired{old.facade}else{0},0];
        let result=unsafe{(self.callback)(18,selected,amount,key,out.as_mut_ptr() as i64)};
        if out[1]==0 {
            let mut rows=destination.lock().unwrap();
            if rows.get(index).is_some_and(|entry|entry.facade==key){
                rows.set(index,old);source.scalars.count.fetch_add(amount32,Ordering::Relaxed);
                super::slots::PENDING_ROLLED_BACK.fetch_add(1,Ordering::Relaxed);
            }
            return i64::MIN;
        }
        result
    }
    /// A prepared copy adopted by Java through `op` (handle in the third argument).
    fn adopt_prepared(&mut self,op:i64,source:&super::stack::Stack,count:i32,a:i64,b:i64)->Result<Arc<super::stack::Stack>,()> {
        let (handle,address,copy)=prepare_copy(source,count);
        let mut out=[address as i64,0];
        let result=unsafe{(self.callback)(op,a,b,handle,out.as_mut_ptr() as i64)};
        if out[1]==0{retire_unadopted(handle);}
        if result==i64::MIN{Err(())}else{Ok(copy)}
    }
}
/// Production witnesses (`mattmc_item_transfer_counter` 3 and 4): copies a
/// native operation prepared for Java to adopt, and unadopted ones it retired.
static PREPARED_COPIES:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
static PREPARED_RETIRED:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
/// Retires a prepared copy Java never acquired (it threw first).
fn retire_unadopted(handle:i64){if owners().lock().unwrap().remove(&handle).is_some(){PREPARED_RETIRED.fetch_add(1,Ordering::Relaxed);}}
/// A registered copy of `source` with `count` and its pop time: the native
/// half of `ItemStack.copyOwnedStack` followed by `setCount`.
fn prepare_copy(source:&super::stack::Stack,count:i32)->(i64,usize,Arc<super::stack::Stack>) {
    let copied=copy_stack(source,count);let address=copied.address();PREPARED_COPIES.fetch_add(1,Ordering::Relaxed);(keep(Owner::Stack(copied.clone())),address,copied)
}
fn copy_stack(source:&super::stack::Stack,count:i32)->Arc<super::stack::Stack> {
    let copied={source.components.lock().unwrap().copy()};
    let mut copied=super::stack::Stack::from_overlay(source.scalars.item,count,copied);copied.empty_identity=source.empty_identity;
    copied.scalars.pop_time.store(source.scalars.pop_time.load(Ordering::Relaxed),Ordering::Relaxed);
    Arc::new(copied)
}
impl super::menu::QuickMoveEffects for MenuEffects {
    fn select(&mut self,index:i32)->Result<super::menu::Selected,()>{
        self.prepared=None;self.known_has=None;
        let (result,out)=self.call(1,index as i64,0,0)?;
        if result==3{let source=menu_stack(out[0]).ok_or(())?;let snapshot=menu_stack(out[1]).ok_or(())?;self.source=Some(source.clone());self.prepared=Some((source,snapshot));self.known_has=Some(true);return Ok(super::menu::Selected{absent:false,link:None});}
        if result==5{
            // A pure stock source: its snapshot is the copy prepared here and
            // adopted by op 17, in place of Java's nested copy.
            let source=menu_stack(out[0]).ok_or(())?;let count=source.scalars.count.load(Ordering::Relaxed);
            let snapshot=self.adopt_prepared(17,&source,count,0,0)?;
            self.source=Some(source.clone());self.prepared=Some((source,snapshot));self.known_has=Some(true);return Ok(super::menu::Selected{absent:false,link:None});
        }
        if result==4{self.known_has=Some(false);return Ok(super::menu::Selected{absent:false,link:None});}
        let link=if out[0]==0{None}else{Some(menu_link(out[0]).ok_or(())?)};Ok(super::menu::Selected{absent:result==0,link})
    }
    fn has_item(&mut self)->Result<bool,()>{if let Some(has)=self.known_has{return Ok(has);}Ok(self.call(2,0,0,0)?.0!=0)}
    fn capture_copy(&mut self)->Result<(Arc<super::stack::Stack>,Arc<super::stack::Stack>),()>{if let Some(prepared)=self.prepared.take(){return Ok(prepared);}let (_,out)=self.call(3,0,0,0)?;let source=menu_stack(out[0]).ok_or(())?;let snapshot=menu_stack(out[1]).ok_or(())?;self.source=Some(source.clone());Ok((source,snapshot))}
    fn equipment(&mut self)->Result<(),()>{self.call(4,0,0,0).map(|_|())}
    fn equipment_route(&mut self)->Result<(i32,i32),()>{let (_,out)=self.call(5,0,0,0)?;Ok((i32::try_from(out[0]).map_err(|_|())?,i32::try_from(out[1]).map_err(|_|())?))}
    fn target_has_item(&mut self,index:i32)->Result<bool,()>{Ok(self.call(6,index as i64,0,0)?.0!=0)}
    fn transfer(&mut self,start:i32,end:i32,reverse:bool)->Result<bool,()>{
        let (result,out)=self.call(7,start as i64,end as i64,i64::from(reverse))?;if result!=2&&result!=6{return Ok(result!=0);}
        self.pending=result==6;
        let destination=if out[1]==2 {
            let first:Arc<super::slots::BoundaryOwner<super::slots::Slots>>=unsafe{retain_vm_arc(out[0])}.ok_or(())?;
            let (_,second)=self.call(19,0,0,0)?;let second:Arc<super::slots::BoundaryOwner<super::slots::Slots>>=unsafe{retain_vm_arc(second[0])}.ok_or(())?;
            // The halves lock in order; one owner twice would never release its gate.
            if Arc::ptr_eq(&first,&second){return Err(());}
            TransferDestination::Pair(first,second)
        }else{TransferDestination::Rows(menu_destination(out[0],out[1]).ok_or(())?)};
        let source=self.source.clone().ok_or(())?;let air=self.air;
        let mut effect=|action:i64,index:i64,phase:i64,amount:i64,changed:i64|{
            if action==2{return if self.pending{self.publish_pending(&destination,&source,index,amount,reverse)}else{self.insert_prepared(&source,index,amount)};}
            let packed=(((amount as u32 as u64)<<32)|((phase as u64)<<1)|changed as u64) as i64;
            self.call(15,action,index,packed).map_or(i64::MIN,|result|result.0)
        };
        let result=match &destination {
            TransferDestination::Rows(rows)=>transfer_run_owned(rows,&source,air,&mut effect),
            TransferDestination::Pair(first,second)=>transfer_run_pair(first,second,&source,air,&mut effect),
        };if result<0{Err(())}else{Ok(result!=0)}
    }
    fn quick_craft(&mut self)->Result<(),()>{self.call(8,0,0,0).map(|_|())}
    fn cleanup(&mut self,empty:bool,inventory:bool)->Result<(),()>{self.call(9,i64::from(empty),i64::from(inventory),0).map(|_|())}
    fn take(&mut self)->Result<(),()>{self.call(10,0,0,0).map(|_|())}
    fn drop_leftover(&mut self)->Result<(),()>{self.call(11,0,0,0).map(|_|())}
    fn finish(&mut self,success:bool)->Result<(),()>{self.call(12,i64::from(success),0,0)?;self.source=None;Ok(())}
    fn initial(&mut self,index:i32)->Result<bool,()>{Ok(self.call(13,index as i64,0,0)?.0!=0)}
    fn initial_item(&mut self)->Result<Arc<super::stack::Stack>,()>{let (_,out)=self.call(14,0,0,0)?;menu_stack(out[0]).ok_or(())}
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_menu_action(handle:i64,index:i64,air:i64,repeated:i64,callback:Option<MenuCallback>)->i64 {
    guard(||{let (Some(owner),Some(callback),Ok(index))=(menu(handle),callback,i32::try_from(index)) else{return BAD};
        let (kind,rows)={let owner=owner.lock().unwrap();(owner.kind,owner.chest_rows.load(Ordering::Relaxed))};let mut effects=MenuEffects{callback,source:None,prepared:None,known_has:None,air:air as u64,pending:false};
        if repeated!=0 {return super::menu::repeat_quick_move(&owner,kind,rows,index,air as u64,&mut effects).map_or(BAD,|_|0);}
        super::menu::quick_move(&owner,kind,rows,index,air as u64,&mut effects).map_or(BAD,|result|i64::from(result.is_some()))})
}
#[no_mangle] pub extern "C" fn mattmc_item_pure_copy(item:i64)->i64 {
    guard(||{if item<=0{return BAD;}super::slots::mark_pure_copy(item as u64);0})
}
#[no_mangle] pub extern "C" fn mattmc_item_pending_key()->i64 {super::slots::next_pending_key()}
#[no_mangle] pub extern "C" fn mattmc_item_shulker_item(item:i64)->i64 {
    guard(||{if item<=0{return BAD;}super::slots::mark_shulker_item(item as u64);0})
}
/// Non-empty rows as a bitmask (slots::occupied_mask), or -2 for more than 63 rows.
#[no_mangle] pub extern "C" fn mattmc_item_slots_occupied(handle:i64,air:i64)->i64 {
    guard(||{let Some(rows)=slots(handle) else{return BAD};let rows=rows.lock().unwrap();super::slots::occupied_mask(&rows,air as u64).unwrap_or(-2)})
}
/// Occupancy bits of stock rows (slots::occupancy), or -2 for Java's original scan.
#[no_mangle] pub extern "C" fn mattmc_item_slots_occupancy(handle:i64,air:i64)->i64 {
    guard(||{let Some(rows)=slots(handle) else{return BAD};let rows=rows.lock().unwrap();super::slots::occupancy(&rows,air as u64).unwrap_or(-2)})
}
/// Comparator signal of two concatenated stock row sets (a double chest), or -2.
#[no_mangle] pub extern "C" fn mattmc_item_slots_signal_pair(first:i64,second:i64,air:i64)->i64 {
    guard(||{let (Some(first),Some(second))=(slots(first),slots(second)) else{return BAD};
        if std::ptr::eq(&*first as *const _,&*second as *const _){return BAD;}
        let (a,b)=(first.lock().unwrap(),second.lock().unwrap());super::slots::redstone_signal_parts(&[&*a,&*b],air as u64).map_or(-2,i64::from)})
}
/// Comparator signal of stock rows (slots::redstone_signal), or -2 for Java's original computation.
#[no_mangle] pub extern "C" fn mattmc_item_slots_signal(handle:i64,air:i64)->i64 {
    guard(||{let Some(rows)=slots(handle) else{return BAD};let rows=rows.lock().unwrap();super::slots::redstone_signal(&rows,air as u64).map_or(-2,i64::from)})
}
/// Stock hopper eject, planned without mutation over the exact native rows, in
/// the original order: isFullContainer(target), then per non-empty hopper slot
/// its one-item split into the first empty or mergeable target slot. Result:
/// -1 nothing moves (the original returns false), -2 not provably plain (Java
/// keeps the original path), else hopper slot | target slot << 8 | empty
/// placement << 16 | target was empty before (cooldown rule) << 17.
///
/// Suck (`suck` != 0, container above -> hopper): no isFullContainer check, and
/// only the first non-empty source slot may be attempted, because a failed
/// attempt has observable source effects (removeItem/setItem change marks) that
/// the original Java path performs (-2).
///
/// `source2`/`target2` (0 when absent) are a double chest's second half: the
/// halves are planned as one concatenated container, in the compound's order.
#[no_mangle] pub extern "C" fn mattmc_item_hopper_plan(source:i64,source2:i64,target:i64,target2:i64,air:i64,suck:i64)->i64 {
    guard(||{let (Some(source),Some(target))=(slots(source),slots(target)) else{return BAD};
        let half=|handle:i64|->Result<Option<SlotsLease>,()>{if handle==0{Ok(None)}else{slots(handle).map(Some).ok_or(())}};
        let (Ok(source2),Ok(target2))=(half(source2),half(target2)) else{return BAD};
        let air=air as u64;
        let all:Vec<&SlotsLease>=[Some(&source),source2.as_ref(),Some(&target),target2.as_ref()].into_iter().flatten().collect();
        for (i,a) in all.iter().enumerate(){for b in &all[i+1..]{if std::ptr::eq(&***a as *const _,&***b as *const _){return -2;}}}
        let snapshot=|rows:&SlotsLease|->Vec<Arc<super::stack::Stack>>{let rows=rows.lock().unwrap();(0..rows.len()).filter_map(|index|rows.get(index).map(|entry|entry.stack.clone())).collect()};
        let mut from=snapshot(&source);if let Some(second)=&source2{from.extend(snapshot(second));}
        let mut into=snapshot(&target);if let Some(second)=&target2{into.extend(snapshot(second));}
        let plain=|stack:&super::stack::Stack|->Option<(i64,i32,i32)>{let digest=super::slots::broadcast_digest(stack);if digest<=0{return None;}Some((digest>>21,super::slots::visible_count(stack,air),super::slots::plain_max(stack)?))};
        let mut full=true;let mut empty=true;
        for stack in &into {
            if super::slots::visible_count(stack,air)==0{full=false;continue;}empty=false;
            let Some((_,count,max))=plain(&**stack) else{return -2};if count<max{full=false;}
        }
        let (suck,refuse_shulkers)=(suck&1,suck&2!=0);
        if full&&suck==0{return -1;}
        for (index,stack) in from.iter().enumerate() {
            if super::slots::visible_count(stack,air)==0{continue;}
            if !super::slots::pure_copy(stack.scalars.item){return -2;}
            // A shulker box target refuses shulker box items through every face: no target slot accepts it.
            if refuse_shulkers&&super::slots::shulker_item(stack.scalars.item){if suck!=0{return -2;}continue;}
            let Some((identity,_,_))=plain(&**stack) else{return -2};
            for (slot,existing) in into.iter().enumerate() {
                if super::slots::visible_count(existing,air)==0{return index as i64|(slot as i64)<<8|1<<16|i64::from(empty)<<17;}
                let Some((other,count,max))=plain(&**existing) else{return -2};
                // Same item with another prototype object: Java's component-map equality decides.
                if other!=identity&&other>>21==identity>>21{return -2;}
                if other==identity&&count<max{return index as i64|(slot as i64)<<8|i64::from(empty)<<17;}
            }
            if suck!=0{return -2;}
        }
        -1})
}
/// Commits a hopper plan after rechecking the rows: the hopper slot shrinks by
/// one (split(1)), then the target either receives a one-item pending copy
/// under `key` (expected old token `expected`) or grows by one. 0 when the rows
/// no longer match; else 1, plus 2 when `expected` is no longer referenced.
#[no_mangle] pub extern "C" fn mattmc_item_hopper_commit(source:i64,target:i64,index:i64,slot:i64,key:i64,expected:i64,air:i64)->i64 {
    guard(||{let (Some(source),Some(target),Ok(index),Ok(slot))=(slots(source),slots(target),usize::try_from(index),usize::try_from(slot)) else{return BAD};
        if std::ptr::eq(&*source as *const _,&*target as *const _){return BAD;}let air=air as u64;
        let stack={let rows=source.lock().unwrap();match rows.get(index){Some(entry) if super::slots::visible_count(&entry.stack,air)>0=>entry.stack.clone(),_=>return 0}};
        let mut rows=target.lock().unwrap();let Some(current)=rows.get(slot).cloned() else{return BAD};
        if current.facade!=expected{return 0;}
        if key!=0 {
            if !super::slots::is_pending(key)||super::slots::visible_count(&current.stack,air)!=0{return 0;}
            let copy=copy_stack(&stack,1);rows.set(slot,super::slots::Entry{facade:key,stack:copy});
            stack.scalars.count.fetch_sub(1,Ordering::Relaxed);super::slots::PENDING_PUBLISHED.fetch_add(1,Ordering::Relaxed);
            return 1|(i64::from(rows.entries_facade_index(expected)<0)<<1);
        }
        // Merge: still the same unpatched item/prototype with room (canMergeItems).
        let (into,from)=(super::slots::broadcast_digest(&current.stack),super::slots::broadcast_digest(&stack));
        if into<=0||from<=0||into>>21!=from>>21{return 0;}
        let Some(max)=super::slots::plain_max(&current.stack) else{return 0};
        if super::slots::visible_count(&current.stack,air)>=max{return 0;}
        current.stack.scalars.count.fetch_add(1,Ordering::Relaxed);stack.scalars.count.fetch_sub(1,Ordering::Relaxed);1})
}
/// HopperBlockEntity.addItem(hopper, itemEntity) over the hopper's native rows,
/// copying (never mutating) the entity's stack `source`: in slot order, merges
/// of min(remaining, item max - count) into same unpatched stacks, then the whole
/// remainder, limited to min(99, item max) like setItem's limitSize, placed
/// as a pending copy under `key` in the first empty slot. A validation pass
/// precedes any mutation, so -2 (Java's original path) leaves the rows untouched.
/// Result: remaining | moves << 32 | hopper was empty << 40 | placed << 41;
/// out[0] receives the replaced token when no row references it any more.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_hopper_take(rows:i64,source:i64,key:i64,air:i64,out:*mut i64)->i64 {
    guard(||{let (Some(rows),Some(source))=(slots(rows),stack(source)) else{return BAD};
        if out.is_null()||!super::slots::is_pending(key){return BAD;}
        let air=air as u64;let from=super::slots::broadcast_digest(&source);
        if from<=0||!super::slots::pure_copy(source.scalars.item){return -2;}
        let Some(item_max)=super::slots::plain_max(&source) else{return -2};
        let wanted=super::slots::visible_count(&source,air);if wanted==0{return -2;}
        let mut rows=rows.lock().unwrap();
        let was_empty=(0..rows.len()).all(|index|rows.get(index).is_some_and(|entry|super::slots::visible_count(&entry.stack,air)==0));
        let (mut remaining,mut moves,mut placed,mut retired)=(wanted,0i64,false,0i64);
        for apply in [false,true] {
            remaining=wanted;moves=0;placed=false;
            for slot in 0..rows.len() {
                if remaining==0{break;}
                let entry=rows.get(slot).unwrap().clone();let count=super::slots::visible_count(&entry.stack,air);
                if count==0 {
                    if apply {
                        let copy=copy_stack(&source,remaining.min(99.min(item_max)));rows.set(slot,super::slots::Entry{facade:key,stack:copy});
                        if rows.entries_facade_index(entry.facade)<0{retired=entry.facade;}super::slots::PENDING_PUBLISHED.fetch_add(1,Ordering::Relaxed);
                    }
                    remaining=0;moves+=1;placed=true;break;
                }
                if entry.stack.scalars.item!=source.scalars.item{continue;}
                let into=super::slots::broadcast_digest(&entry.stack);
                if into<=0||into>>21!=from>>21{return -2;}
                let Some(max)=super::slots::plain_max(&entry.stack) else{return -2};
                if count>max{continue;}
                let moved=remaining.min(item_max-count);if moved<=0{continue;}
                if apply{entry.stack.scalars.count.store(count+moved,Ordering::Relaxed);}
                remaining-=moved;moves+=1;
            }
        }
        *out=retired;
        i64::from(remaining)|moves<<32|i64::from(was_empty)<<40|i64::from(placed)<<41})
}
#[no_mangle] pub extern "C" fn mattmc_item_inventory_tick_noop(item:i64)->i64 {
    guard(||{if item<=0{return BAD;}super::inventory::mark_tick_noop(item as u64);0})
}
/// One broadcast's snapshots for menu rows `from..count`, written to `out[from..count]`.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_menu_digests(handle:i64,out:i64,from:i64,count:i64)->i64 {
    guard(||{let (Some(owner),Ok(from),Ok(count))=(menu(handle),usize::try_from(from),usize::try_from(count)) else{return BAD};if out<=0||count>65536||from>count{return BAD;}
        let out=std::slice::from_raw_parts_mut(out as *mut i64,count);out[from..].fill(-1);owner.lock().unwrap().digests(from,out);0})
}
#[no_mangle] pub extern "C" fn mattmc_item_menu_rows_address(handle:i64)->i64 {
    guard(||{let Some(owner)=menu(handle) else{return BAD};let pointer=&owner.lock().unwrap().chest_rows as *const std::sync::atomic::AtomicI32 as i64;pointer})
}

/// Connect the actual immutable inventory reference to its exact equipment store.
/// Foreign Java equipment implementations remain original effects.
#[no_mangle] pub extern "C" fn mattmc_item_inventory_equipment(handle:i64,equipment_handle:i64)->i64 {
    guard(||{let (Some(owner),Some(equipment))=(inventory(handle),equipment(equipment_handle)) else{return BAD};
        match owner.equipment.set(equipment){Ok(())=>0,Err(value)=>if owner.equipment.get().is_some_and(|current|Arc::ptr_eq(current,&value)){0}else{BAD}}})
}

#[no_mangle] pub extern "C" fn mattmc_item_overlay_stage(handle:i64)->i64 {
    guard(||{let Some(owner)=overlay(handle) else{return BAD};let staged=owner.lock().unwrap().stage_change();staged.map_or(0,|patch|keep(Owner::Patch(patch)))})
}
#[no_mangle] pub extern "C" fn mattmc_item_overlay_commit(handle:i64,expected:i64,staged:i64)->i64 {
    guard(||{let (Some(owner),Some(staged))=(overlay(handle),patch(staged)) else{return BAD};
        let committed=owner.lock().unwrap().commit_change(expected as u64,staged);i64::from(committed)})
}

#[no_mangle] pub extern "C" fn mattmc_item_overlay_needs_copy(handle:i64)->i64 {
    guard(||{let Some(owner)=overlay(handle) else{return BAD};let needed=owner.lock().unwrap().needs_change();i64::from(needed)})
}

/// Publish membership and decide last-alias retirement under the actual row gate.
#[no_mangle] pub extern "C" fn mattmc_item_slots_publish(handle:i64,index:i64,facade:i64,expected:i64)->i64 {
    guard(||{let (Some(rows),Some(stack),Ok(index))=(slots(handle),stack(facade),usize::try_from(index)) else{return BAD};
        let mut rows=rows.lock().unwrap();if rows.get(index).map(|entry|entry.facade)!=Some(expected){return BAD;}
        rows.set(index,super::slots::Entry{facade,stack});i64::from(rows.entries_facade_index(expected)<0)})
}
/// The required facade is rooted before source shrink/membership publication.
#[no_mangle] pub extern "C" fn mattmc_item_transfer_publish(handle:i64,source_handle:i64,index:i64,facade:i64,amount:i64,expected:i64)->i64 {
    guard(||{let (Some(rows),Some(source),Some(stack),Ok(index),Ok(amount))=(slots(handle),stack(source_handle),stack(facade),usize::try_from(index),i32::try_from(amount)) else{return BAD};
        let mut rows=rows.lock().unwrap();if rows.get(index).map(|entry|entry.facade)!=Some(expected){return BAD;}
        let inserted=super::slots::Entry{facade,stack};if super::slots::transfer_insert(&mut rows,&source,index,inserted,amount).is_none(){return BAD;}
        i64::from(rows.entries_facade_index(expected)<0)})
}

#[no_mangle] pub extern "C" fn mattmc_item_menu_empty_source(handle:i64,index:i64,air:i64,chest_handle:i64)->i64 {
    guard(||{let (Some(owner),Ok(index))=(menu(handle),i32::try_from(index)) else{return BAD};let chest=container(chest_handle);
        let empty=owner.lock().unwrap().empty_source(index,air as u64,chest.as_ref());i64::from(empty)})
}

#[no_mangle] pub extern "C" fn mattmc_item_menu_owner_address(handle:i64)->i64 {guard(||menu(handle).map_or(BAD,|owner|owner.address() as i64))}
#[no_mangle] pub extern "C" fn mattmc_item_container_owner_address(handle:i64)->i64 {guard(||container(handle).map_or(BAD,|owner|owner.address() as i64))}
/// Private bounded entry. Caller fences the actual owning Menu lease and the
/// optional exact Container lease. Menu rows retain all nested endpoint Arcs.
/// A pointer alone, an unrelated gate, or a handle number is not a lease.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_menu_empty_source_bounded(address:i64,index:i64,air:i64,clear_chest:i64)->i64 {
    if address==0{return 0;}let Ok(index)=i32::try_from(index) else{return 0};
    let owner=&*(address as *const super::slots::BoundaryOwner<super::menu::Menu>);
    let Some(menu)=owner.try_lock() else{return 0};i64::from(menu.try_empty_source(index,air as u64,clear_chest as usize))
}

/// Private fixed-work facade read. The VM caller holds the matching binding-bag
/// monitor and fences the actual owner lease; failure resumes the regular read.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_menu_facade_bounded(address:i64,index:i64)->i64 {
    if address==0{return BAD;}let Ok(index)=usize::try_from(index) else{return BAD};
    let owner=&*(address as *const super::slots::BoundaryOwner<super::menu::Menu>);
    let Some(menu)=owner.try_lock() else{return BAD};menu.get(index).map_or(BAD,|slot|slot.facade)
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_slots_facade_bounded(address:i64,index:i64)->i64 {
    if address==0{return BAD;}let Ok(index)=usize::try_from(index) else{return BAD};
    let owner=&*(address as *const super::slots::BoundaryOwner<super::slots::Slots>);
    let Some(rows)=owner.try_lock() else{return BAD};rows.get(index).map_or(BAD,|entry|entry.facade)
}

/// Caller fences the actual SlotLink lease, which retains its nested owners.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_slot_read_bounded(address:i64,air:i64,mode:i64)->i64 {
    if address==0||mode==2{return -1;}let link=&*(address as *const super::menu::SlotLink);link.try_read(air as u64,mode!=0)
}

/// Source is borrowed from the actual fenced Java stack owner. This is a regular
/// allocating downcall, not a critical entry. The returned copy still follows
/// the original preregistered rollback/adoption/cleanup path.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_stack_copy_leased(address:i64,out:*mut i64)->i64 {
    guard(||{
        if address<=0||out.is_null(){return BAD;}
        let source=&*(address as *const super::stack::Stack);
        let item=source.scalars.item;let count=source.scalars.count.load(Ordering::Relaxed);
        let copied={source.components.lock().unwrap().copy()};
        let mut copied=super::stack::Stack::from_overlay(item,count,copied);copied.empty_identity=source.empty_identity;
        let copied=Arc::new(copied);let address=copied.address();let owner=keep(Owner::Stack(copied));out.write(address as i64);owner
    })
}
#[cfg(test)] mod vm_lease_tests {
    use super::*;
    #[test] fn callback_destination_retains_the_actual_outer_inventory_or_separate_rows(){
        let item=Arc::new(super::super::stack::Stack::from_overlay(0,0,Overlay::new(1)));
        let entry=super::super::slots::Entry{facade:1,stack:item};
        let inventory=Arc::new(super::super::inventory::Inventory::new(entry.clone()));let weak=Arc::downgrade(&inventory);
        assert_eq!(inventory.address(),Arc::as_ptr(&inventory) as usize);
        assert_ne!(inventory.address(),inventory.slots.address());
        let retained=menu_destination(inventory.address() as i64,1).unwrap();drop(inventory);assert!(weak.upgrade().is_some());
        assert_eq!(retained.lock().unwrap().get(0).unwrap().facade,1);drop(retained);assert!(weak.upgrade().is_none());
        let rows=Arc::new(super::super::slots::BoundaryOwner::new(super::super::slots::Slots::new(27,entry)));let weak=Arc::downgrade(&rows);
        let retained=menu_destination(rows.address() as i64,0).unwrap();drop(rows);assert!(weak.upgrade().is_some());
        assert_eq!(retained.lock().unwrap().get(26).unwrap().facade,1);drop(retained);assert!(weak.upgrade().is_none());
    }
    #[test] fn callback_link_retains_its_actual_endpoint_after_original_roots_drop(){
        let item=Arc::new(super::super::stack::Stack::from_overlay(7,11,Overlay::new(1)));
        let entry=super::super::slots::Entry{facade:7,stack:item};let inventory=Arc::new(super::super::inventory::Inventory::new(entry));let weak=Arc::downgrade(&inventory);
        let link=Arc::new(super::super::menu::SlotLink::new(inventory.clone(),7).unwrap());
        let retained=menu_link(link.address() as i64).unwrap();assert!(Arc::ptr_eq(&link,&retained));drop(link);drop(inventory);
        assert!(weak.upgrade().is_some());assert_eq!(retained.read(99,false),7);drop(retained);assert!(weak.upgrade().is_none());
    }
    #[test] fn callback_retains_the_same_actual_arc_before_vm_root_retirement(){
        let source=Arc::new(super::super::stack::Stack::from_overlay(7,11,Overlay::new(1)));let weak=Arc::downgrade(&source);
        assert_eq!(source.address(),Arc::as_ptr(&source) as usize);let retained=menu_stack(source.address() as i64).unwrap();
        assert!(Arc::ptr_eq(&source,&retained));drop(source);assert!(weak.upgrade().is_some());assert_eq!(retained.scalars.count.load(Ordering::Relaxed),11);drop(retained);assert!(weak.upgrade().is_none());
    }
    #[test] fn copied_owner_survives_original_actual_lease_and_preserves_cow(){
        let mut original=Overlay::new(1);original.prepare_change();original.set_after_compare(Key(8),Some(Leaf(9)),None,false);
        let source=Arc::new(super::super::stack::Stack::from_overlay(7,11,original));let mut address=0;
        let copy=unsafe{mattmc_item_stack_copy_leased(source.address() as i64,&mut address)};let copied=stack(copy).unwrap();assert_eq!(copied.address() as i64,address);
        source.components.lock().unwrap().prepare_change();source.components.lock().unwrap().set_after_compare(Key(8),Some(Leaf(10)),None,false);
        assert_eq!(copied.components.lock().unwrap().get_override(Key(8)),Some(Value::Present(Leaf(9))));drop(source);assert_eq!(copied.scalars.count.load(Ordering::Relaxed),11);assert_eq!(mattmc_item_owner_release(copy),1);
    }
}

/// Regular publication entry: Java holds the exact row binding-bag monitor and
/// fences its real owner, source and already-created inserted facade. Native
/// retains its own inserted Arc before publishing canonical membership.
#[no_mangle] pub unsafe extern "C" fn mattmc_item_slots_publish_leased(rows:i64,index:i64,inserted:i64,facade:i64,expected:i64)->i64 {
    guard(||{
        if rows<=0||facade<=0{return BAD;}let (Ok(index),Some(stack))=(usize::try_from(index),menu_stack(inserted)) else{return BAD};
        let owner=&*(rows as *const super::slots::BoundaryOwner<super::slots::Slots>);let mut rows=owner.lock().unwrap();
        if rows.get(index).map(|entry|entry.facade)!=Some(expected){return BAD;}
        rows.set(index,super::slots::Entry{facade,stack});i64::from(rows.entries_facade_index(expected)<0)
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_transfer_publish_leased(rows:i64,source:i64,index:i64,inserted:i64,facade:i64,amount:i64,expected:i64)->i64 {
    guard(||{
        if rows<=0||source<=0||facade<=0{return BAD;}
        let (Ok(index),Ok(amount),Some(stack))=(usize::try_from(index),i32::try_from(amount),menu_stack(inserted)) else{return BAD};
        let source=&*(source as *const super::stack::Stack);let owner=&*(rows as *const super::slots::BoundaryOwner<super::slots::Slots>);let mut rows=owner.lock().unwrap();
        if rows.get(index).map(|entry|entry.facade)!=Some(expected){return BAD;}
        if super::slots::transfer_insert(&mut rows,source,index,super::slots::Entry{facade,stack},amount).is_none(){return BAD;}
        i64::from(rows.entries_facade_index(expected)<0)
    })
}
/// Actual allocation leases enter once and remain owned across effects. Every
/// row/default/equipment guard still ends before a Java callback.
/// Inventory add/scan effects: `(action, index, handle, address, out)`. Only
/// the prepared-copy actions (33/34, with a nonzero handle) use the last three.
type InventoryEffect=unsafe extern "C" fn(i64,i64,i64,i64,i64)->i64;
#[no_mangle] pub unsafe extern "C" fn mattmc_item_inventory_add_leased(owner:i64,source:i64,index:i64,air:i64,effect:Option<InventoryEffect>)->i64 {
    inventory_add(owner,source,index,air,effect,false)
}
/// Inventory add whose Java caller accepts natively published pending contents
/// (actions 35/36 record the recipe; see `publish_pending_add`).
#[no_mangle] pub unsafe extern "C" fn mattmc_item_inventory_add_pending_leased(owner:i64,source:i64,index:i64,air:i64,effect:Option<InventoryEffect>)->i64 {
    inventory_add(owner,source,index,air,effect,true)
}
/// Empty main-slot resource (action 3: the original copyWithCount(0), setItem,
/// grow by one (an empty stack's max size), pop time 5) and damaged (action 4: copyAndClear,
/// set, pop time 5) copies published natively under a pending key. Java records
/// the recipe through 35/36; a failure before recording restores the row and
/// source. None keeps the prepared-copy path (S30) for anything not computable.
unsafe fn publish_pending_add(owner:&super::inventory::Inventory,source:&super::stack::Stack,action:i64,index:i32,air:u64,effect:InventoryEffect)->Option<Result<i32,()>> {
    if !(0..36).contains(&index){return None;}
    let held=source.scalars.count.load(Ordering::Relaxed);
    let (count,remaining)=if action==3 {
        // The original grows the zero-count copy by min(99, max stack) of an
        // *empty* stack, whose components read as empty: exactly one item.
        // The add loop then merges the rest into this row on its next pass.
        let wanted=super::slots::visible_count(source,air);
        let moved=wanted.min(1);if moved<=0{return None;}(moved,wanted-moved)
    } else {(held,0)};
    // An occupied destination (a damaged add at an explicit index) keeps the
    // prepared-copy path; decide that before copying the stack.
    let empty=|rows:&super::slots::Slots|rows.get(index as usize).is_some_and(|entry|super::slots::visible_count(&entry.stack,air)==0);
    if !empty(&owner.slots.lock().unwrap()){return None;}
    let copy=copy_stack(source,count);copy.scalars.pop_time.store(5,Ordering::Relaxed);
    let key=super::slots::next_pending_key();
    let (old,retired)={
        let mut rows=owner.slots.lock().unwrap();
        if !empty(&rows){return None;}
        let old=rows.set(index as usize,super::slots::Entry{facade:key,stack:copy})?;let retired=rows.entries_facade_index(old.facade)<0;(old,retired)
    };
    if action==4{source.scalars.count.store(0,Ordering::Relaxed);}
    super::slots::PENDING_PUBLISHED.fetch_add(1,Ordering::Relaxed);
    let mut out=[0i64,0];
    let result=effect(action+32,index as i64,key,if retired{old.facade}else{0},out.as_mut_ptr() as i64);
    if out[1]==0 {
        let mut rows=owner.slots.lock().unwrap();
        if rows.get(index as usize).is_some_and(|entry|entry.facade==key){rows.set(index as usize,old);super::slots::PENDING_ROLLED_BACK.fetch_add(1,Ordering::Relaxed);}
        if action==4{source.scalars.count.store(held,Ordering::Relaxed);}
        return Some(Err(()));
    }
    if result==i64::MIN{return Some(Err(()));}
    Some(Ok(if action==3{remaining}else{1}))
}
unsafe fn inventory_add(owner:i64,source:i64,index:i64,air:i64,effect:Option<InventoryEffect>,pending:bool)->i64 {
    guard(||{
        let (Some(owner),Some(source),Ok(index),Some(effect))=(retain_vm_arc::<super::inventory::Inventory>(owner),menu_stack(source),i32::try_from(index),effect) else{return BAD};
        match owner.add_run(&source,index,air as u64,|action,index|{
            if pending&&(action==3||action==4){if let Some(result)=publish_pending_add(&owner,&source,action,index,air as u64,effect){return result;}}
            let result=if action==3||action==4 {
                // Empty-slot resource (`copyWithCount(0)`) and damaged
                // (`copyAndClear`) copies are prepared here; Java adopts them
                // through actions 33/34 (scan owns 6/7) or keeps its original path.
                let count=if action==3{0}else{source.scalars.count.load(Ordering::Relaxed)};
                let (handle,address,_)=prepare_copy(&source,count);
                let mut out=[address as i64,0];
                let result=effect(action+30,index as i64,handle,address as i64,out.as_mut_ptr() as i64);
                if out[1]==0{retire_unadopted(handle);}result
            } else {effect(action,index as i64,0,0,0)};
            if result==i64::MIN{Err(())}else{i32::try_from(result).map_err(|_|())}
        }){Ok(value)=>i64::from(value),Err(())=>i64::MIN}
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_inventory_scan_leased(owner:i64,source:i64,mode:i64,air:i64,effect:Option<InventoryEffect>)->i64 {
    guard(||{
        let (Some(owner),Some(effect))=(retain_vm_arc::<super::inventory::Inventory>(owner),effect) else{return BAD};
        // Identity search deliberately consumes an opaque facade token, not an
        // allocation pointer: two facades may retain the same actual Arc.
        if mode==5{return owner.first_facade(source) as i64;}
        let source=if source==0{None}else{let Some(source)=menu_stack(source) else{return BAD};Some(source)};
        let Ok(mode)=i32::try_from(mode) else{return BAD};
        match owner.scan_run_effects(source.as_deref(),mode,air as u64,|action,index|{let result=effect(action,index as i64,0,0,0);if result==i64::MIN{Err(())}else if action==12{Ok(result)}else{i32::try_from(result).map(i64::from).map_err(|_|())}}){Ok(value)=>value as i64,Err(())=>i64::MIN}
    })
}
#[no_mangle] pub unsafe extern "C" fn mattmc_item_inventory_visit_leased(owner:i64,source:i64,mode:i64,limit:i64,air:i64,effect:Option<unsafe extern "C" fn(i64,i64,i64)->i64>)->i64 {
    guard(||{
        let (Some(owner),Some(effect),Ok(mode),Ok(limit))=(retain_vm_arc::<super::inventory::Inventory>(owner),effect,i32::try_from(mode),i32::try_from(limit)) else{return BAD};
        let source=if source==0{None}else{let Some(source)=menu_stack(source) else{return BAD};Some(source)};
        match owner.visit_run(source.as_deref(),mode,limit,air as u64,|action,index,facade|{let result=effect(action,index as i64,facade);if result==i64::MIN{Err(())}else{i32::try_from(result).map_err(|_|())}}){Ok(value)=>i64::from(value),Err(())=>i64::MIN}
    })
}

#[cfg(test)] mod operation_lease_tests {
    use super::*;
    use super::super::{slots::{BoundaryOwner,Entry,Slots},stack::Stack,inventory::Inventory,patch::defaults::{Store,Row}};
    fn defaults()->Arc<BoundaryOwner<Store>>{let mut store=Store::new(1);store.put(Row{key:1,leaf:1,hash:1,kind:1,scalar:64,reserved:0});Arc::new(BoundaryOwner::new(store))}
    fn value(count:i32,defaults:&Arc<BoundaryOwner<Store>>)->Arc<Stack>{let mut overlay=Overlay::new(7);overlay.defaults=Some(defaults.clone());Arc::new(Stack::from_overlay(7,count,overlay))}
    #[test] fn actual_publication_checks_before_shrink_and_retires_only_last_facade_token(){
        let defaults=defaults();let source=value(11,&defaults);let inserted=value(5,&defaults);let rows=Arc::new(BoundaryOwner::new(Slots::new(2,Entry{facade:7,stack:source.clone()})));
        unsafe{
            assert_eq!(mattmc_item_slots_publish_leased(rows.address() as i64,0,inserted.address() as i64,9,8),BAD);
            assert_eq!(mattmc_item_transfer_publish_leased(rows.address() as i64,source.address() as i64,0,inserted.address() as i64,9,5,8),BAD);
            assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);assert_eq!(rows.lock().unwrap().get(0).unwrap().facade,7);
            assert_eq!(mattmc_item_transfer_publish_leased(rows.address() as i64,source.address() as i64,0,inserted.address() as i64,9,5,7),0);
            assert_eq!(source.scalars.count.load(Ordering::Relaxed),6);assert_eq!(rows.lock().unwrap().get(1).unwrap().facade,7);
            assert_eq!(mattmc_item_slots_publish_leased(rows.address() as i64,1,inserted.address() as i64,9,7),1);
            assert_eq!(mattmc_item_slots_publish_leased(rows.address() as i64,1,inserted.address() as i64,9,9),0);
        }
    }
    unsafe extern "C" fn add_effect(action:i64,index:i64,_:i64,_:i64,_:i64)->i64 {match(action,index){(1,0)|(2,40)=>0,_=>i64::MIN}}
    #[test] fn inventory_add_consumes_actual_owner_and_source_leases_without_registry_entries(){
        let defaults=defaults();let empty=value(0,&defaults);let inventory=Arc::new(Inventory::new(Entry{facade:1,stack:empty}));
        for index in 0..36{inventory.slots.lock().unwrap().set(index,Entry{facade:100+index as i64,stack:value(60,&defaults)});}
        let source=value(11,&defaults);assert_eq!(unsafe{mattmc_item_inventory_add_leased(inventory.address() as i64,source.address() as i64,-1,99,Some(add_effect))},1);
        assert_eq!(source.scalars.count.load(Ordering::Relaxed),0);let rows=inventory.slots.lock().unwrap();
        assert_eq!(rows.get(0).unwrap().stack.scalars.count.load(Ordering::Relaxed),64);assert_eq!(rows.get(1).unwrap().stack.scalars.count.load(Ordering::Relaxed),64);assert_eq!(rows.get(2).unwrap().stack.scalars.count.load(Ordering::Relaxed),63);
    }
    unsafe extern "C" fn visit_effect(action:i64,index:i64,facade:i64)->i64 {assert_eq!(action,23);assert_eq!(facade,71+index);0}
    #[test] fn identity_scan_and_visitor_keep_facade_tokens_distinct_from_shared_allocations(){
        let defaults=defaults();let shared=value(11,&defaults);let inventory=Arc::new(Inventory::new(Entry{facade:1,stack:value(0,&defaults)}));
        {let mut rows=inventory.slots.lock().unwrap();rows.set(0,Entry{facade:71,stack:shared.clone()});rows.set(1,Entry{facade:72,stack:shared.clone()});}
        unsafe{
            assert_eq!(mattmc_item_inventory_scan_leased(inventory.address() as i64,71,5,99,Some(add_effect)),0);
            assert_eq!(mattmc_item_inventory_scan_leased(inventory.address() as i64,72,5,99,Some(add_effect)),1);
            assert_eq!(mattmc_item_inventory_scan_leased(inventory.address() as i64,73,5,99,Some(add_effect)),-1);
            assert_eq!(mattmc_item_inventory_visit_leased(inventory.address() as i64,shared.address() as i64,3,2,99,Some(visit_effect)),0);
        }
    }
    // Java throwing between preparation and adoption (the effect returns
    // i64::MIN without setting the adoption flag) leaves the copy to the native
    // operation, which retires it; one Java adopted before throwing stays with
    // its Java release. Covers the snapshot, stock insertion and inventory add.
    static THROWN:std::sync::Mutex<Vec<i64>>=std::sync::Mutex::new(Vec::new());
    unsafe extern "C" fn throw_before_adoption(_:i64,_:i64,_:i64,handle:i64,_:i64)->i64 {THROWN.lock().unwrap().push(handle);i64::MIN}
    unsafe extern "C" fn throw_after_adoption(_:i64,_:i64,_:i64,handle:i64,out:i64)->i64 {*(out as *mut i64).add(1)=1;THROWN.lock().unwrap().push(handle);i64::MIN}
    unsafe extern "C" fn add_throw_before_adoption(action:i64,index:i64,handle:i64,_:i64,_:i64)->i64 {match action{33|34=>{THROWN.lock().unwrap().push(handle);i64::MIN},_=>add_effect(action,index,0,0,0)}}
    fn last_thrown()->i64{*THROWN.lock().unwrap().last().unwrap()}
    #[test] fn java_throw_before_adoption_retires_the_prepared_copy_and_after_adoption_keeps_it(){
        let defaults=defaults();let source=value(11,&defaults);
        for (callback,adopted) in [(throw_before_adoption as MenuCallback,false),(throw_after_adoption as MenuCallback,true)] {
            let mut effects=MenuEffects{callback,source:None,prepared:None,known_has:None,air:0,pending:false};
            assert!(effects.adopt_prepared(17,&source,11,0,0).is_err());
            let snapshot=last_thrown();assert_eq!(owners().lock().unwrap().contains_key(&snapshot),adopted);
            assert_eq!(effects.insert_prepared(&source,3,11),i64::MIN);
            let inserted=last_thrown();assert_ne!(snapshot,inserted);assert_eq!(owners().lock().unwrap().contains_key(&inserted),adopted);
            // Stand-in for the Java release of an adopted copy.
            if adopted{owners().lock().unwrap().remove(&snapshot);owners().lock().unwrap().remove(&inserted);}
        }
        let inventory=Arc::new(Inventory::new(Entry{facade:1,stack:value(0,&defaults)}));
        let added=value(11,&defaults);let thrown=THROWN.lock().unwrap().len();
        assert_eq!(unsafe{mattmc_item_inventory_add_leased(inventory.address() as i64,added.address() as i64,-1,99,Some(add_throw_before_adoption))},i64::MIN);
        assert_eq!(THROWN.lock().unwrap().len(),thrown+1,"the add offered one prepared copy");
        let copy=last_thrown();assert_ne!(copy,0);assert!(!owners().lock().unwrap().contains_key(&copy));
        assert_eq!(added.scalars.count.load(Ordering::Relaxed),11,"nothing committed before the Java throw");
    }
    unsafe extern "C" fn record_then_throw(_:i64,_:i64,_:i64,_:i64,out:i64)->i64 {*(out as *mut i64).add(1)=1;i64::MIN}
    unsafe extern "C" fn throw_before_record(_:i64,_:i64,_:i64,_:i64,_:i64)->i64 {i64::MIN}
    // Op 18: a Java failure before the recipe is recorded undoes the native
    // publication and source shrink exactly; once recorded, the publication
    // stands (as an original commit before a failing effect would).
    #[test] fn pending_publication_rolls_back_only_before_java_records_its_recipe(){
        let defaults=defaults();let empty=value(0,&defaults);
        for (callback,recorded) in [(throw_before_record as MenuCallback,false),(record_then_throw as MenuCallback,true)] {
            let rows=Arc::new(BoundaryOwner::new(Slots::new(27,Entry{facade:1,stack:empty.clone()})));let destination=TransferDestination::Rows(SlotsLease::Separate(rows.clone()));
            let source=value(11,&defaults);let mut effects=MenuEffects{callback,source:None,prepared:None,known_has:None,air:0,pending:true};
            assert_eq!(effects.publish_pending(&destination,&source,4,11,false),i64::MIN);
            let entry=rows.lock().unwrap().get(4).unwrap().clone();
            if recorded {
                assert!(super::super::slots::is_pending(entry.facade));assert_eq!(entry.stack.scalars.count.load(Ordering::Relaxed),11);
                assert_eq!(source.scalars.count.load(Ordering::Relaxed),0);assert!(!Arc::ptr_eq(&entry.stack,&source));
            } else {
                assert_eq!(entry.facade,1);assert!(Arc::ptr_eq(&entry.stack,&empty));assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);
            }
        }
    }
    // Materialization registers the row's own Arc only for the matching key and
    // backing version; bind refuses a changed row, so a stale facade never binds.
    #[test] fn materialize_and_bind_require_the_exact_pending_row_and_backing(){
        let defaults=defaults();let empty=value(0,&defaults);
        let rows=Arc::new(BoundaryOwner::new(Slots::new(3,Entry{facade:1,stack:empty.clone()})));
        let handle=keep(Owner::Slots(rows.clone()));let key=super::super::slots::next_pending_key();let copy=value(5,&defaults);
        rows.lock().unwrap().set(0,Entry{facade:key,stack:copy.clone()});
        let version=copy.components.lock().unwrap().patch_version() as i64;
        assert_eq!(mattmc_item_slots_materialize(handle,0,key,version+1),-3);
        assert_eq!(mattmc_item_slots_materialize(handle,1,key,version),BAD);
        assert_eq!(mattmc_item_slots_materialize(handle,0,key-1,version),BAD);
        let facade=mattmc_item_slots_materialize(handle,0,key,version);assert!(facade>0);
        assert!(Arc::ptr_eq(&stack(facade).unwrap(),&copy),"the facade owns the row's own stack, not a copy");
        let other=keep(Owner::Stack(value(5,&defaults)));assert_eq!(mattmc_item_slots_bind(handle,0,key,other),0);
        rows.lock().unwrap().set(1,Entry{facade:key,stack:copy.clone()});
        assert_eq!(mattmc_item_slots_bind(handle,0,key,facade),1,"another row still carries the key");
        assert_eq!(rows.lock().unwrap().get(0).unwrap().facade,facade);assert_eq!(mattmc_item_slots_bind(handle,0,key,facade),0);
        assert_eq!(mattmc_item_slots_pending_copy(handle,1,key,version+1),-3);
        copy.scalars.count.store(0,Ordering::Relaxed);assert_eq!(mattmc_item_slots_pending_copy(handle,1,key,version),-4);
        copy.scalars.count.store(5,Ordering::Relaxed);let duplicate=mattmc_item_slots_pending_copy(handle,1,key,version);
        assert!(duplicate>0&&!Arc::ptr_eq(&stack(duplicate).unwrap(),&copy));assert!(super::super::slots::is_pending(rows.lock().unwrap().get(1).unwrap().facade));
        for owner in [facade,other,duplicate,handle]{owners().lock().unwrap().remove(&owner);}
    }
    static TICK_CALLS:std::sync::Mutex<Vec<(i64,i64)>>=std::sync::Mutex::new(Vec::new());
    unsafe extern "C" fn tick_effect(action:i64,index:i64,_:i64)->i64 {TICK_CALLS.lock().unwrap().push((action,index));0}
    // Mode 9 (Java vouches player.level() is a plain getter): an item whose
    // inventoryTick is Item's empty one ticks natively (pop time only, no call,
    // no facade); others keep the original ordered call. Mode 2 is unchanged.
    #[test] fn native_inventory_tick_skips_only_items_with_the_empty_java_tick(){
        let defaults=defaults();let inventory=Arc::new(Inventory::new(Entry{facade:1,stack:value(0,&defaults)}));
        let stack=|item:u64,pop:i32|{let mut overlay=Overlay::new(7);overlay.defaults=Some(defaults.clone());let stack=Arc::new(Stack::from_overlay(item,5,overlay));stack.scalars.pop_time.store(pop,Ordering::Relaxed);stack};
        let (plain,custom)=(stack(424242,3),stack(424243,3));
        {let mut rows=inventory.slots.lock().unwrap();rows.set(0,Entry{facade:2,stack:plain.clone()});rows.set(1,Entry{facade:3,stack:custom.clone()});}
        super::super::inventory::mark_tick_noop(424242);
        TICK_CALLS.lock().unwrap().clear();
        assert_eq!(unsafe{mattmc_item_inventory_visit_leased(inventory.address() as i64,0,9,36,99,Some(tick_effect))},0);
        assert_eq!(plain.scalars.pop_time.load(Ordering::Relaxed),2,"native pop-time countdown");
        assert_eq!(custom.scalars.pop_time.load(Ordering::Relaxed),3,"Java's own ItemStack.inventoryTick owns this one");
        assert_eq!(*TICK_CALLS.lock().unwrap(),vec![(22,1)]);
        TICK_CALLS.lock().unwrap().clear();
        assert_eq!(unsafe{mattmc_item_inventory_visit_leased(inventory.address() as i64,0,2,36,99,Some(tick_effect))},0);
        assert_eq!(*TICK_CALLS.lock().unwrap(),vec![(22,0),(22,1)],"mode 2 keeps every original call");assert_eq!(plain.scalars.pop_time.load(Ordering::Relaxed),2);
    }
    unsafe extern "C" fn add_throw_before_record(action:i64,index:i64,_:i64,_:i64,_:i64)->i64 {if action==35||action==36{i64::MIN}else{add_effect(action,index,0,0,0)}}
    unsafe extern "C" fn add_record_then_throw(action:i64,index:i64,_:i64,_:i64,out:i64)->i64 {if action==35||action==36{*(out as *mut i64).add(1)=1;i64::MIN}else{add_effect(action,index,0,0,0)}}
    // Pickup publication (35): a Java failure before the recipe is recorded
    // restores the empty row and the source count; once recorded the row keeps
    // the pending copy (count and pop time 5), as an original setItem before a
    // failing later step would.
    // A double chest's halves are one 54-row destination: the merge pass spans
    // both halves before any empty row is used, as the original moveItemStackTo(0,54).
    #[test] fn double_chest_transfer_merges_across_both_halves_before_any_empty_row(){
        let defaults=defaults();let empty=value(0,&defaults);
        let half=||Slots::new(27,Entry{facade:1,stack:empty.clone()});
        let (first,mut second)=(half(),half());
        let target=value(60,&defaults);second.set(3,Entry{facade:2,stack:target.clone()});
        let source=value(11,&defaults);let mut stats=super::super::slots::TransferStats::default();
        let step=super::super::slots::transfer_step_pair(&first,&second,&source,99,0,0,&mut stats);
        assert_eq!(step&7,1,"merge first");assert_eq!((step>>3)&63,30,"compound slot 30 is the second half's row 3");
        assert_eq!(target.scalars.count.load(Ordering::Relaxed),64);assert_eq!(source.scalars.count.load(Ordering::Relaxed),7);
        let step=super::super::slots::transfer_step_pair(&first,&second,&source,99,((step>>9)&3) as u64,31,&mut stats);
        assert_eq!(step&7,2,"then the first empty row");assert_eq!((step>>3)&63,0);assert_eq!(step>>32,7);
        assert_eq!(super::super::slots::transfer_step_pair(&first,&half(),&source,99,0,0,&mut stats)&7,2,"no merge target: first empty row");
    }
    // The bounded comparison decides copies and empties without locks held across,
    // and declines (None) under contention instead of waiting.
    #[test] fn bounded_component_comparison_declines_under_contention(){
        let defaults=defaults();let a=value(5,&defaults);let b=value(5,&defaults);let empty=value(0,&defaults);
        assert_eq!(super::super::slots::inventory_same_bounded(&empty,&value(0,&defaults),99),Ok(Some(true)));
        assert_eq!(super::super::slots::inventory_same_bounded(&empty,&a,99),Ok(Some(false)));
        let held=b.components.lock().unwrap();
        assert_eq!(super::super::slots::inventory_same_bounded(&a,&b,99),Err(()),"contended: decline, never wait");
        drop(held);
        // Without installed roles (process-global, set by Java at bootstrap) the bounded
        // form declines; whenever it decides, it must agree with the regular form.
        if let Ok(Some(decided))=super::super::slots::inventory_same_bounded(&a,&b,99){assert_eq!(Some(decided),super::super::slots::inventory_same(&a,&b,99));}
    }
    #[test] fn pending_pickup_rolls_back_only_before_java_records_its_recipe(){
        let defaults=defaults();
        for (callback,recorded) in [(add_throw_before_record as InventoryEffect,false),(add_record_then_throw as InventoryEffect,true)] {
            let empty=value(0,&defaults);let inventory=Arc::new(Inventory::new(Entry{facade:1,stack:empty.clone()}));
            let source=value(11,&defaults);
            assert_eq!(unsafe{mattmc_item_inventory_add_pending_leased(inventory.address() as i64,source.address() as i64,-1,99,Some(callback))},i64::MIN);
            let entry=inventory.slots.lock().unwrap().get(0).unwrap().clone();
            if recorded {
                // The original empty-slot pass grows the copy by one; the throw skips setCount.
                assert!(super::super::slots::is_pending(entry.facade));assert_eq!(entry.stack.scalars.count.load(Ordering::Relaxed),1);
                assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);
                assert_eq!(entry.stack.scalars.pop_time.load(Ordering::Relaxed),5);
            } else {
                assert_eq!(entry.facade,1);assert!(Arc::ptr_eq(&entry.stack,&empty));assert_eq!(source.scalars.count.load(Ordering::Relaxed),11);
            }
        }
    }
    // Hopper plan encoding over plain rows: empty placement / merge with the
    // target-was-empty bit, a full target (-1), suck's first-slot rule (-2) and
    // impure items (-2); commit applies the split and refuses changed rows.
    #[test] fn hopper_plan_and_commit_follow_the_original_slot_order(){
        let defaults=defaults();let empty=value(0,&defaults);
        let rows=|n:usize|Arc::new(BoundaryOwner::new(Slots::new(n,Entry{facade:1,stack:empty.clone()})));
        let (source,target)=(rows(5),rows(5));let (sh,th)=(keep(Owner::Slots(source.clone())),keep(Owner::Slots(target.clone())));
        let item=|count:i32,id:u64|{let mut overlay=Overlay::new(7);overlay.defaults=Some(defaults.clone());Arc::new(Stack::from_overlay(id,count,overlay))};
        super::super::slots::mark_pure_copy(515151);
        source.lock().unwrap().set(2,Entry{facade:2,stack:item(3,515151)});
        assert_eq!(mattmc_item_hopper_plan(sh,0,th,0,99,0),2|1<<16|1<<17,"slot 2 into empty target slot 0, target was empty");
        target.lock().unwrap().set(1,Entry{facade:3,stack:item(10,515151)});
        assert_eq!(mattmc_item_hopper_plan(sh,0,th,0,99,0),2|1<<16,"first target slot 0 is still empty");
        target.lock().unwrap().set(0,Entry{facade:4,stack:item(64,515151)});
        assert_eq!(mattmc_item_hopper_plan(sh,0,th,0,99,0),2|1<<8,"merge into slot 1");
        let key=super::super::slots::next_pending_key();
        assert_eq!(mattmc_item_hopper_commit(sh,th,2,1,key,3,99),0,"a merge plan commits without a key");
        assert_eq!(mattmc_item_hopper_commit(sh,th,2,1,0,3,99),1);
        assert_eq!(target.lock().unwrap().get(1).unwrap().stack.scalars.count.load(Ordering::Relaxed),11);
        assert_eq!(source.lock().unwrap().get(2).unwrap().stack.scalars.count.load(Ordering::Relaxed),2);
        for slot in 0..5{target.lock().unwrap().set(slot,Entry{facade:10+slot as i64,stack:item(64,515151)});}
        assert_eq!(mattmc_item_hopper_plan(sh,0,th,0,99,0),-1,"full target: eject moves nothing");
        assert_eq!(mattmc_item_hopper_plan(sh,0,th,0,99,1),-2,"suck: the first slot cannot move, the original path runs");
        source.lock().unwrap().set(2,Entry{facade:2,stack:item(3,515152)});
        target.lock().unwrap().set(4,Entry{facade:1,stack:empty.clone()});
        assert_eq!(mattmc_item_hopper_plan(sh,0,th,0,99,0),-2,"impure items keep the original path");
        for owner in [sh,th]{owners().lock().unwrap().remove(&owner);}
    }
    // Comparator signal and occupancy over plain rows, against values computed
    // with the original Java float formula by hand.
    #[test] fn row_signal_and_occupancy_match_the_original_formulas(){
        let defaults=defaults();let empty=value(0,&defaults);
        let rows=Slots::new(27,Entry{facade:1,stack:empty.clone()});
        assert_eq!(super::super::slots::redstone_signal(&rows,99),Some(0));
        assert_eq!(super::super::slots::occupancy(&rows,99),Some(1));
        let mut rows=rows;rows.set(0,Entry{facade:2,stack:value(1,&defaults)});
        // 1/64/27 > 0: floor(0.00081*14)=0, plus 1.
        assert_eq!(super::super::slots::redstone_signal(&rows,99),Some(1));
        for slot in 0..27{rows.set(slot,Entry{facade:3,stack:value(64,&defaults)});}
        assert_eq!(super::super::slots::redstone_signal(&rows,99),Some(15));
        assert_eq!(super::super::slots::occupancy(&rows,99),Some(2),"all at max: full, not empty");
        rows.set(5,Entry{facade:4,stack:value(63,&defaults)});
        let f=(26.0f32+63.0/64.0)/27.0;assert_eq!(super::super::slots::redstone_signal(&rows,99),Some((f*14.0).floor() as i32+1));
        assert_eq!(super::super::slots::occupancy(&rows,99),Some(0));
        rows.set(5,Entry{facade:5,stack:value(70,&defaults)});
        assert_eq!(super::super::slots::occupancy(&rows,99),Some(0),"over-max counts are not full");
    }
    // Hopper item-entity collection: in-order merges, then the remainder (clipped
    // to min(99, max)) placed pending; the entity's own stack is never mutated;
    // a fallback leaves every row untouched.
    #[test] fn hopper_take_merges_then_places_the_remainder_without_touching_the_entity_stack(){
        let defaults=defaults();let empty=value(0,&defaults);
        let rows=Arc::new(BoundaryOwner::new(Slots::new(5,Entry{facade:1,stack:empty.clone()})));let handle=keep(Owner::Slots(rows.clone()));
        let item=|count:i32|{let mut overlay=Overlay::new(7);overlay.defaults=Some(defaults.clone());Arc::new(Stack::from_overlay(616161,count,overlay))};
        super::super::slots::mark_pure_copy(616161);
        rows.lock().unwrap().set(0,Entry{facade:2,stack:item(60)});
        let source=item(20);let source_handle=keep(Owner::Stack(source.clone()));let key=super::super::slots::next_pending_key();
        let mut out=0i64;
        let result=unsafe{mattmc_item_hopper_take(handle,source_handle,key,99,&mut out)};
        assert_eq!(result&0xFFFFFFFF,0,"everything moved");assert_eq!((result>>32)&0xFF,2,"merge into slot 0 then placement in slot 1");
        assert_eq!((result>>40)&1,0,"hopper was not empty");assert_eq!((result>>41)&1,1);
        assert_eq!(source.scalars.count.load(Ordering::Relaxed),20,"the entity's stack is copied, not mutated");
        assert_eq!(rows.lock().unwrap().get(0).unwrap().stack.scalars.count.load(Ordering::Relaxed),64);
        let placed=rows.lock().unwrap().get(1).unwrap().clone();
        assert_eq!(placed.facade,key);assert_eq!(placed.stack.scalars.count.load(Ordering::Relaxed),16);
        let big=item(150);let big_handle=keep(Owner::Stack(big.clone()));let key2=super::super::slots::next_pending_key();
        let result=unsafe{mattmc_item_hopper_take(handle,big_handle,key2,99,&mut out)};
        assert_eq!(result&0xFFFFFFFF,0);assert_eq!(rows.lock().unwrap().get(2).unwrap().stack.scalars.count.load(Ordering::Relaxed),64,"limitSize clips the placed remainder");
        let other=Arc::new({let mut overlay=Overlay::new(8);overlay.defaults=Some(defaults.clone());Stack::from_overlay(616161,5,overlay)});
        rows.lock().unwrap().set(3,Entry{facade:3,stack:other});
        let before:Vec<i32>=(0..5).map(|i|rows.lock().unwrap().get(i).unwrap().stack.scalars.count.load(Ordering::Relaxed)).collect();
        let small=item(3);let small_handle=keep(Owner::Stack(small));
        assert_eq!(unsafe{mattmc_item_hopper_take(handle,small_handle,super::super::slots::next_pending_key(),99,&mut out)},-2,"same item, other prototype: Java decides");
        let after:Vec<i32>=(0..5).map(|i|rows.lock().unwrap().get(i).unwrap().stack.scalars.count.load(Ordering::Relaxed)).collect();
        assert_eq!(before,after,"the fallback mutated nothing");
        for owner in [handle,source_handle,big_handle,small_handle]{owners().lock().unwrap().remove(&owner);}
    }
}

