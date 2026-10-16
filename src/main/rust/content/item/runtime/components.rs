//! Actual component payloads. Opaque keys are resolved only by synchronous VM
//! effects; holder equality and hash behavior are never replaced with registry IDs.
use std::sync::{Arc, Mutex, OnceLock};
use std::sync::atomic::{AtomicU64, Ordering};
use super::jvm_roots::Root;

/// Actual retained leaf values. Java keeps only their original public object
/// identities; native consumers read these values from the owning backing.
#[derive(Clone, Debug)]
pub enum Payload { Null, Integer(i32), Enchantments(Arc<Enchantments>) }

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Role { pub key: super::patch::Key, pub hash: u32 }
#[derive(Debug)]
pub struct Roles { pub values: [Role; 6], pub empty_prototype: u64, pub empty_enchantments: Arc<Enchantments> }
static ROLES: OnceLock<Roles> = OnceLock::new();
pub fn roles() -> Option<&'static Roles> { ROLES.get() }
pub fn install_roles(roles: Roles) -> bool {
    if let Some(current) = ROLES.get() {
        return current.values == roles.values && current.empty_prototype == roles.empty_prototype
            && Arc::ptr_eq(&current.empty_enchantments, &roles.empty_enchantments);
    }
    ROLES.set(roles).is_ok()
}
enum Effective { Unknown, Missing, Opaque, Value(Payload) }
fn effective(owner: &super::patch::Overlay, roles: &Roles, index: usize) -> Effective {
    use super::patch::Value;
    let role = roles.values[index];
    if let Some((value, payload)) = owner.get_override_payload(role.key) {
        return match (value, payload) {
            (Value::Removed, _) | (_, Some(Payload::Null)) => Effective::Missing,
            (_, Some(payload)) => Effective::Value(payload), _ => Effective::Opaque,
        };
    }
    let Some(defaults) = &owner.defaults else {
        return if owner.prototype == roles.empty_prototype { Effective::Missing } else { Effective::Unknown };
    };
    match defaults.lock().unwrap().get_payload(role.key.0, role.hash) {
        None | Some((_, Some(Payload::Null))) => Effective::Missing,
        Some((_, Some(payload))) => Effective::Value(payload), _ => Effective::Opaque,
    }
}
fn present(value: &Effective) -> Option<bool> {
    match value { Effective::Unknown => None, Effective::Missing => Some(false), _ => Some(true) }
}
fn integer(value: Effective, missing: i32) -> Option<i32> {
    match value { Effective::Missing => Some(missing), Effective::Value(Payload::Integer(value)) => Some(value), _ => None }
}
fn damageable(owner: &super::patch::Overlay, roles: &Roles) -> Option<bool> {
    if !present(&effective(owner, roles, 1))? { return Some(false); }
    if present(&effective(owner, roles, 3))? { return Some(false); }
    present(&effective(owner, roles, 2))
}
fn damage_value(owner: &super::patch::Overlay, roles: &Roles) -> Option<i32> {
    // Mth.clamp is min(max(value, 0), maxDamage), including a negative upper bound.
    let damage = integer(effective(owner, roles, 2), 0)?;
    let max = integer(effective(owner, roles, 1), 0)?;
    Some(damage.max(0).min(max))
}
pub fn damaged_with(stack: &super::stack::Stack, roles: &Roles) -> Option<bool> {
    let owner = stack.components.lock().unwrap();
    if !damageable(&owner, roles)? { return Some(false); }
    Some(damage_value(&owner, roles)? > 0)
}
pub fn enchanted_with(stack: &super::stack::Stack, roles: &Roles) -> Option<bool> {
    let owner = stack.components.lock().unwrap();
    match effective(&owner, roles, 4) {
        Effective::Missing => Some(roles.empty_enchantments.size() != 0),
        Effective::Value(Payload::Enchantments(value)) => Some(value.size() != 0), _ => None,
    }
}
pub fn max_size_with(stack: &super::stack::Stack, roles: &Roles) -> Option<i32> {
    let owner = stack.components.lock().unwrap(); integer(effective(&owner, roles, 0), 1)
}
pub fn stackable_with(stack: &super::stack::Stack, roles: &Roles) -> Option<bool> {
    let owner = stack.components.lock().unwrap();
    if integer(effective(&owner, roles, 0), 1)? <= 1 { return Some(false); }
    if !damageable(&owner, roles)? { return Some(true); }
    Some(damage_value(&owner, roles)? <= 0)
}
pub fn crafting_with(stack: &super::stack::Stack, roles: &Roles) -> Option<bool> {
    if damaged_with(stack, roles)? { return Some(false); }
    if enchanted_with(stack, roles)? { return Some(false); }
    let owner = stack.components.lock().unwrap();
    Some(!present(&effective(&owner, roles, 5))?)
}
pub fn damaged(stack: &super::stack::Stack) -> Option<bool> { damaged_with(stack, roles()?) }
pub fn enchanted(stack: &super::stack::Stack) -> Option<bool> { enchanted_with(stack, roles()?) }
pub fn max_size(stack: &super::stack::Stack) -> Option<i32> { max_size_with(stack, roles()?) }
pub fn stackable(stack: &super::stack::Stack) -> Option<bool> { stackable_with(stack, roles()?) }
pub fn crafting(stack: &super::stack::Stack) -> Option<bool> { crafting_with(stack, roles()?) }

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct EnchantmentRow { pub key: u64, pub level: i32 }

/// Key/value arrays are one retained allocation. A removed entry's value stays
/// readable by escaped live entries, just as the original fastutil map does.
#[derive(Debug)]
pub struct EnchantmentTable { rows: Mutex<Vec<EnchantmentRow>>, revision: AtomicU64 }
impl EnchantmentTable {
    pub fn new(n: usize) -> Self { Self { rows: Mutex::new(vec![EnchantmentRow::default(); n + 1]), revision: AtomicU64::new(0) } }
    pub fn len(&self) -> usize { self.rows.lock().unwrap().len() }
    pub fn revision(&self) -> u64 { self.revision.load(Ordering::Acquire) }
    pub fn row(&self, index: usize) -> Option<EnchantmentRow> { self.rows.lock().unwrap().get(index).copied() }
    pub fn contains_token(&self, token: u64) -> bool { self.rows.lock().unwrap().iter().any(|row| row.key == token) }
    pub fn clear_keys(&self) {
        let mut rows = self.rows.lock().unwrap(); for row in rows.iter_mut() { row.key = 0; }
        self.revision.fetch_add(1, Ordering::Release);
    }
    pub fn write(&self, index: usize, row: EnchantmentRow) -> bool {
        let mut rows = self.rows.lock().unwrap();
        if let Some(target) = rows.get_mut(index) { *target = row; self.revision.fetch_add(1, Ordering::Release); true } else { false }
    }
    pub fn set_level(&self, index: usize, level: i32) -> Option<i32> {
        let mut rows = self.rows.lock().unwrap();
        let old = std::mem::replace(&mut rows.get_mut(index)?.level, level);
        self.revision.fetch_add(1, Ordering::Release);
        Some(old)
    }
    pub fn set_key(&self, index: usize, key: u64) -> Option<u64> {
        let mut rows = self.rows.lock().unwrap();
        let old = std::mem::replace(&mut rows.get_mut(index)?.key, key);
        self.revision.fetch_add(1, Ordering::Release);
        Some(old)
    }
}

#[derive(Debug)]
struct Shape { n: usize, min_n: usize, size: i32, contains_null: bool, default_level: i32, revision: u64 }
#[derive(Debug)]
pub struct Enchantments { pub table: Root<EnchantmentTable>, shape: Mutex<Shape> }
struct Stamp { table: Arc<EnchantmentTable>, rows: u64, shape: u64 }

/// Implementations may reenter this very owner. Every native lock must be gone
/// before either effect; an error stops at that exact observable position.
pub trait HolderEffects {
    type Error;
    fn capture_table(&mut self, _table: &Arc<EnchantmentTable>) -> Result<(), Self::Error> { Ok(()) }
    fn hash(&mut self, key: u64, table: &Arc<EnchantmentTable>) -> Result<i32, Self::Error>;
    fn equals(&mut self, query: u64, stored: u64, table: &Arc<EnchantmentTable>) -> Result<bool, Self::Error>;
}

/// Fallible VM preparation precedes native publication. The completion effect
/// performs only an already-prepared resource assignment and cannot call users.
pub trait RootEffects: HolderEffects {
    fn bind(&mut self, query: u64, table: &Arc<EnchantmentTable>) -> Result<u64, Self::Error>;
    fn prepare_table(&mut self, table: &Arc<EnchantmentTable>, source: &Arc<EnchantmentTable>) -> Result<(), Self::Error>;
    fn anchor(&mut self, key: u64, table: &Arc<EnchantmentTable>, source: &Arc<EnchantmentTable>) -> Result<(), Self::Error>;
    fn published(&mut self, table: &Arc<EnchantmentTable>);
    fn retired(&mut self, key: u64, table: &Arc<EnchantmentTable>);
}

pub trait CursorEffects: RootEffects {
    fn pin_wrapped(&mut self, key: u64, source: &Arc<EnchantmentTable>) -> Result<(), Self::Error>;
    fn retire_wrapped(&mut self, key: u64, retire: bool);
    fn accept(&mut self, index: usize) -> Result<(), Self::Error>;
}

#[derive(Debug, PartialEq, Eq)]
pub enum LookupError<E> { Effect(E), Bounds(usize, usize), StalePublication }

impl Enchantments {
    pub fn new(n: usize) -> Self {
        assert!(n.is_power_of_two() && n >= 2);
        Self { table: Root::new(EnchantmentTable::new(n)), shape: Mutex::new(Shape { n, min_n: n, size: 0, contains_null: false, default_level: 0, revision: 0 }) }
    }
    pub fn size(&self) -> i32 { self.shape.lock().unwrap().size }
    pub fn mask(&self) -> usize { self.shape.lock().unwrap().n - 1 }
    pub fn capacity(&self) -> usize { self.shape.lock().unwrap().n }
    pub fn contains_null(&self) -> bool { self.shape.lock().unwrap().contains_null }
    pub fn default_level(&self) -> i32 { self.shape.lock().unwrap().default_level }
    pub fn set_default_level(&self, level: i32) {
        let mut shape = self.shape.lock().unwrap(); shape.default_level = level; shape.revision = shape.revision.wrapping_add(1);
    }
    fn stamp(&self) -> Stamp {
        let table = self.table.lease();
        Stamp { rows: table.revision(), table, shape: self.shape.lock().unwrap().revision }
    }
    fn matches(&self, stamp: &Stamp) -> bool {
        Arc::ptr_eq(&stamp.table, &self.table.lease()) && stamp.rows == stamp.table.revision()
            && stamp.shape == self.shape.lock().unwrap().revision
    }

    /// Captures the key array BEFORE hashCode, but reads the mask and value
    /// array after user effects. In particular, equals(query, query) is not elided.
    pub fn find<E: HolderEffects>(&self, query: u64, effects: &mut E) -> Result<Result<usize, usize>, LookupError<E::Error>> {
        if query == 0 {
            let shape = self.shape.lock().unwrap();
            return Ok(if shape.contains_null { Ok(shape.n) } else { Err(shape.n) });
        }
        let keys = self.table.lease();
        effects.capture_table(&keys).map_err(LookupError::Effect)?;
        let hash = effects.hash(query, &keys).map_err(LookupError::Effect)?;
        let mut pos = mix(hash) & self.mask();
        loop {
            let stored = keys.row(pos).ok_or_else(|| LookupError::Bounds(pos, keys.len()))?.key;
            if stored == 0 { return Ok(Err(pos)); }
            if effects.equals(query, stored, &keys).map_err(LookupError::Effect)? { return Ok(Ok(pos)); }
            pos = (pos + 1) & self.mask();
        }
    }
    pub fn get<E: HolderEffects>(&self, query: u64, missing: Option<i32>, effects: &mut E) -> Result<i32, LookupError<E::Error>> {
        match self.find(query, effects)? {
            Ok(pos) => { let table = self.table.lease(); table.row(pos).map(|r| r.level).ok_or_else(|| LookupError::Bounds(pos, table.len())) },
            Err(_) => Ok(missing.unwrap_or_else(|| self.default_level())),
        }
    }

    pub fn put<E: RootEffects>(&self, query: u64, level: i32, effects: &mut E) -> Result<i32, LookupError<E::Error>> {
        match self.find(query, effects)? {
            Ok(pos) => { let table = self.table.lease(); table.set_level(pos, level).ok_or_else(|| LookupError::Bounds(pos, table.len())) },
            Err(pos) => {
                let selected = self.stamp();
                let bound = effects.bind(query, &selected.table).map_err(LookupError::Effect)?;
                if !self.matches(&selected) { return Err(LookupError::StalePublication); }
                let old_size = self.insert_at(pos, bound, level).ok_or_else(|| LookupError::Bounds(pos, self.table.lease().len()))?;
                let n = self.capacity();
                if old_size >= max_fill(n) as i32 {
                    let next_n = capacity(self.size().wrapping_add(1) as usize);
                    self.rehash_owned(next_n, effects)?;
                }
                Ok(self.default_level())
            }
        }
    }

    pub fn contains_value(&self, level: i32) -> bool {
        let table = self.table.lease(); let n = self.capacity();
        if self.contains_null() && table.row(n).is_some_and(|r| r.level == level) { return true; }
        (0..n).rev().any(|index| table.row(index).is_some_and(|r| r.key != 0 && r.level == level))
    }
    pub fn entry_level<E>(&self, index: usize, replacement: Option<i32>) -> Result<i32, LookupError<E>> {
        let table = self.table.lease();
        match replacement { Some(level) => table.set_level(index, level), None => table.row(index).map(|r| r.level) }
            .ok_or_else(|| LookupError::Bounds(index, table.len()))
    }
    pub fn clear(&self) -> bool {
        let mut shape = self.shape.lock().unwrap();
        if shape.size == 0 { return false; }
        shape.size = 0; shape.contains_null = false; shape.revision = shape.revision.wrapping_add(1);
        self.table.lease().clear_keys(); true
    }
    pub fn try_capacity<E: RootEffects>(&self, expected: i64, effects: &mut E) -> Result<(), LookupError<E::Error>> {
        let needed = (((expected as f32 / 0.75f32) as f64).ceil() as i64).max(2) as u64;
        let n = needed.next_power_of_two().clamp(2, 1 << 30) as usize;
        if n > self.capacity() { self.rehash_owned(n, effects)?; } Ok(())
    }
    pub fn merge_max<E: RootEffects>(&self, query: u64, level: i32, effects: &mut E) -> Result<i32, LookupError<E::Error>> {
        // Integer::max is the concrete primitive merge used by Mutable.upgrade.
        // Preserve find's single hash/equality phase and its live value read.
        match self.find(query, effects)? {
            Ok(index) => {
                let old = self.entry_level(index, None)?;
                let value = old.max(level);
                self.entry_level(index, Some(value))?; Ok(value)
            }
            Err(index) => {
                let selected = self.stamp();
                let bound = effects.bind(query, &selected.table).map_err(LookupError::Effect)?;
                if !self.matches(&selected) { return Err(LookupError::StalePublication); }
                let old_size = self.insert_at(index, bound, level).ok_or_else(|| LookupError::Bounds(index, selected.table.len()))?;
                if old_size >= max_fill(self.capacity()) as i32 { self.rehash_owned(capacity(self.size().wrapping_add(1) as usize), effects)?; }
                Ok(level)
            }
        }
    }
    pub fn visit_desc<E: CursorEffects>(&self, effects: &mut E) -> Result<(), LookupError<E::Error>> {
        if self.contains_null() { effects.accept(self.capacity()).map_err(LookupError::Effect)?; }
        let mut index = self.capacity();
        while index != 0 {
            index -= 1; let table = self.table.lease();
            if table.row(index).ok_or_else(|| LookupError::Bounds(index, table.len()))?.key != 0 {
                effects.accept(index).map_err(LookupError::Effect)?;
            }
        }
        Ok(())
    }
    pub fn map_hash<E: HolderEffects>(&self, effects: &mut E) -> Result<i32, LookupError<E::Error>> {
        let mut count = self.size().wrapping_sub(i32::from(self.contains_null()));
        let mut index = 0; let mut hash = 0i32;
        while count != 0 {
            count = count.wrapping_sub(1);
            loop {
                let table = self.table.lease();
                if table.row(index).ok_or_else(|| LookupError::Bounds(index, table.len()))?.key != 0 { break; }
                index += 1;
            }
            let table = self.table.lease();
            let key = table.row(index).ok_or_else(|| LookupError::Bounds(index, table.len()))?.key;
            let key_hash = effects.hash(key, &table).map_err(LookupError::Effect)?;
            hash = hash.wrapping_add(key_hash ^ self.entry_level(index, None)?);
            index += 1;
        }
        if self.contains_null() { hash = hash.wrapping_add(self.entry_level(self.capacity(), None)?); }
        Ok(hash)
    }

    pub fn rehash_owned<E: RootEffects>(&self, n: usize, effects: &mut E) -> Result<(), LookupError<E::Error>> {
        let old = self.table.lease();
        let next = Arc::new(EnchantmentTable::new(n));
        let selected = self.stamp();
        effects.prepare_table(&next, &old).map_err(LookupError::Effect)?;
        if !self.matches(&selected) { return Err(LookupError::StalePublication); }
        self.fill_rehash(&old, &next, n, effects, |effects, key| effects.anchor(key, &next, &old))?;
        // User hash callbacks have finished. Original rehash replaces whichever
        // table is current at this point, even if those callbacks rehashed it.
        let expected = self.table.lease();
        if !self.publish_rehash(&expected, next.clone(), n) { return Err(LookupError::StalePublication); }
        effects.published(&next);
        Ok(())
    }

    /// Called only after VM key binding succeeds, at the original insert point.
    /// The returned size is the pre-increment size used by the rehash condition.
    pub fn insert_at(&self, index: usize, key: u64, level: i32) -> Option<i32> {
        let mut shape = self.shape.lock().unwrap();
        if !self.table.lease().write(index, EnchantmentRow { key, level }) { return None; }
        if index == shape.n { shape.contains_null = true; }
        let previous = shape.size;
        shape.size = shape.size.wrapping_add(1);
        shape.revision = shape.revision.wrapping_add(1);
        Some(previous)
    }

    fn retire<E: RootEffects>(&self, key: u64, table: &Arc<EnchantmentTable>, effects: &mut E) {
        if key != 0 && !table.contains_token(key) { effects.retired(key, table); }
    }

    fn shift<E: RootEffects>(&self, pos: usize, effects: &mut E) -> Result<(), LookupError<E::Error>> {
        self.shift_wrapped(pos, effects, |_, _, _, _| Ok(()))
    }

    fn shift_wrapped<E, F>(&self, mut pos: usize, effects: &mut E, mut wrap: F) -> Result<(), LookupError<E::Error>>
    where E: RootEffects, F: FnMut(&mut E, usize, usize, &Arc<EnchantmentTable>) -> Result<(), LookupError<E::Error>> {
        let keys = self.table.lease();
        effects.capture_table(&keys).map_err(LookupError::Effect)?;
        loop {
            let last = pos;
            pos = (pos + 1) & self.mask();
            let current;
            loop {
                let key = keys.row(pos).ok_or_else(|| LookupError::Bounds(pos, keys.len()))?.key;
                if key == 0 {
                    let old = keys.set_key(last, 0).ok_or_else(|| LookupError::Bounds(last, keys.len()))?;
                    self.retire(old, &keys, effects);
                    return Ok(());
                }
                let hash = effects.hash(key, &keys).map_err(LookupError::Effect)?;
                let slot = mix(hash) & self.mask();
                if if last <= pos { last >= slot || slot > pos } else { last >= slot && slot > pos } {
                    current = key;
                    break;
                }
                pos = (pos + 1) & self.mask();
            }
            // A hash callback may have removed current. Its typed effect keeps
            // the actual holder alive through this original captured-key write.
            let revision = keys.revision();
            let selected = self.stamp();
            let values = selected.table.clone();
            effects.anchor(current, &keys, &keys).map_err(LookupError::Effect)?;
            if revision != keys.revision() || !self.matches(&selected) {
                return Err(LookupError::StalePublication);
            }
            wrap(effects, pos, last, &keys)?;
            if revision != keys.revision() || !self.matches(&selected) { return Err(LookupError::StalePublication); }
            let old = keys.set_key(last, current).ok_or_else(|| LookupError::Bounds(last, keys.len()))?;
            let level = values.row(pos).ok_or_else(|| LookupError::Bounds(pos, values.len()))?.level;
            values.set_level(last, level).ok_or_else(|| LookupError::Bounds(last, values.len()))?;
            self.retire(old, &keys, effects);
        }
    }

    pub fn remove<E: RootEffects>(&self, query: u64, effects: &mut E) -> Result<i32, LookupError<E::Error>> {
        let pos = match self.find(query, effects)? { Ok(pos) => pos, Err(_) => return Ok(self.default_level()) };
        self.remove_at(pos, effects)
    }
    pub fn remove_at<E: RootEffects>(&self, pos: usize, effects: &mut E) -> Result<i32, LookupError<E::Error>> {
        let current = self.table.lease();
        let old = current.row(pos).ok_or_else(|| LookupError::Bounds(pos, current.len()))?.level;
        let null = { let mut shape = self.shape.lock().unwrap();
            let null = pos == shape.n;
            if null { shape.contains_null = false; }
            shape.size = shape.size.wrapping_sub(1);
            shape.revision = shape.revision.wrapping_add(1);
            null
        };
        if null { current.set_key(pos, 0).ok_or_else(|| LookupError::Bounds(pos, current.len()))?; }
        else { self.shift(pos, effects)?; }
        let shrink = { let shape = self.shape.lock().unwrap();
            (shape.n > shape.min_n && shape.n > 16 && shape.size < (max_fill(shape.n) / 4) as i32).then_some(shape.n / 2)
        };
        if let Some(n) = shrink { self.rehash_owned(n, effects)?; }
        Ok(old)
    }

    /// Rehash uses captured old arrays, rereads a key after its hash callback,
    /// and reads the null value at the current n after all callbacks.
    /// The VM must anchor each copied key before the unpublished write, then
    /// finish the replacement table's root resource before final publication.
    pub fn rehash<E, F>(&self, n: usize, effects: &mut E, mut anchor: F) -> Result<Arc<EnchantmentTable>, LookupError<E::Error>>
    where E: HolderEffects, F: FnMut(&Arc<EnchantmentTable>, u64) -> Result<(), E::Error> {
        let old = self.table.lease();
        let next = Arc::new(EnchantmentTable::new(n));
        self.fill_rehash(&old, &next, n, effects, |_, key| anchor(&next, key))?;
        Ok(next)
    }

    fn fill_rehash<E, F>(&self, old: &Arc<EnchantmentTable>, next: &Arc<EnchantmentTable>, n: usize, effects: &mut E, mut anchor: F) -> Result<(), LookupError<E::Error>>
    where E: HolderEffects, F: FnMut(&mut E, u64) -> Result<(), E::Error> {
        let (mut i, mut remaining) = { let shape = self.shape.lock().unwrap(); (shape.n, shape.size.wrapping_sub(i32::from(shape.contains_null))) };
        while remaining != 0 {
            remaining = remaining.wrapping_sub(1);
            loop {
                i = i.checked_sub(1).ok_or_else(|| LookupError::Bounds(usize::MAX, old.len()))?;
                if old.row(i).ok_or_else(|| LookupError::Bounds(i, old.len()))?.key != 0 { break; }
            }
            let hash_key = old.row(i).unwrap().key;
            let hash = effects.hash(hash_key, &old).map_err(LookupError::Effect)?;
            let mut pos = mix(hash) & (n - 1);
            while next.row(pos).unwrap().key != 0 { pos = (pos + 1) & (n - 1); }
            let row = old.row(i).ok_or_else(|| LookupError::Bounds(i, old.len()))?;
            let selected = self.stamp();
            let source_revision = old.revision();
            let next_revision = next.revision();
            anchor(effects, row.key).map_err(LookupError::Effect)?;
            if !self.matches(&selected) || old.revision() != source_revision || next.revision() != next_revision {
                return Err(LookupError::StalePublication);
            }
            next.write(pos, row);
        }
        let current_n = self.shape.lock().unwrap().n;
        let null = old.row(current_n).ok_or_else(|| LookupError::Bounds(current_n, old.len()))?;
        next.set_level(n, null.level);
        Ok(())
    }

    pub fn publish_rehash(&self, expected: &Arc<EnchantmentTable>, prepared: Arc<EnchantmentTable>, n: usize) -> bool {
        // No VM effect between the matching VM-resource adoption and this swap.
        let mut shape = self.shape.lock().unwrap();
        if self.table.publish(expected, prepared).is_err() { return false; }
        shape.n = n;
        shape.revision = shape.revision.wrapping_add(1);
        true
    }
}

#[derive(Debug)]
struct CursorState { pos: i32, last: Option<usize>, remaining: i32, null_first: bool, wrapped: Option<Vec<u64>>, revision: u64 }

/// Actual cursor state, retaining its allocation owner independently of the
/// original map handle. Java keeps the corresponding facade/holder anchors.
#[derive(Debug)]
pub struct EnchantmentCursor { pub owner: Arc<Enchantments>, state: Mutex<CursorState>, pub failure: Mutex<[i64; 3]> }

#[derive(Debug, PartialEq, Eq)]
pub enum CursorError<E> { Lookup(LookupError<E>), NoSuchElement, IllegalState, WrappedNull, WrappedBounds(usize, usize) }
impl<E> From<LookupError<E>> for CursorError<E> { fn from(value: LookupError<E>) -> Self { Self::Lookup(value) } }

impl EnchantmentCursor {
    pub fn new(owner: Arc<Enchantments>) -> Self {
        let shape = owner.shape.lock().unwrap();
        let state = CursorState { pos: shape.n as i32, last: None, remaining: shape.size, null_first: shape.contains_null, wrapped: None, revision: 0 };
        drop(shape);
        Self { owner, state: Mutex::new(state), failure: Mutex::new([0; 3]) }
    }
    pub fn has_next(&self) -> bool { self.state.lock().unwrap().remaining != 0 }
    pub fn contains_wrapped(&self, key: u64) -> bool {
        self.state.lock().unwrap().wrapped.as_ref().is_some_and(|keys| keys.contains(&key))
    }
    pub fn skip<E: HolderEffects>(&self, count: i32, effects: &mut E) -> Result<i32, CursorError<E::Error>> {
        let mut remaining = count;
        loop {
            let previous = remaining; remaining = remaining.wrapping_sub(1);
            if previous == 0 || !self.has_next() { break; }
            self.next(effects)?;
        }
        Ok(count.wrapping_sub(remaining).wrapping_sub(1))
    }

    fn wrapped(&self, index: usize) -> Result<u64, CursorError<()>> {
        let state = self.state.lock().unwrap();
        let wrapped = state.wrapped.as_ref().ok_or(CursorError::WrappedNull)?;
        wrapped.get(index).copied().ok_or(CursorError::WrappedBounds(index, wrapped.len()))
    }
    fn scan<E: HolderEffects>(&self, keys: &Arc<EnchantmentTable>, effects: &mut E) -> Result<usize, CursorError<E::Error>> {
        loop {
            let pos = { let mut state = self.state.lock().unwrap(); state.pos = state.pos.wrapping_sub(1); state.revision = state.revision.wrapping_add(1); state.pos };
            if pos >= 0 {
                if keys.row(pos as usize).ok_or_else(|| LookupError::Bounds(pos as usize, keys.len()))?.key != 0 {
                    self.state.lock().unwrap().last = Some(pos as usize);
                    return Ok(pos as usize);
                }
                continue;
            }
            self.state.lock().unwrap().last = Some(usize::MAX);
            let index = pos.wrapping_neg().wrapping_sub(1) as usize;
            let key = self.wrapped(index).map_err(|error| match error {
                CursorError::WrappedNull => CursorError::WrappedNull,
                CursorError::WrappedBounds(i, n) => CursorError::WrappedBounds(i, n), _ => unreachable!(),
            })?;
            let hash = effects.hash(key, keys).map_err(|e| CursorError::Lookup(LookupError::Effect(e)))?;
            let mut bucket = mix(hash) & self.owner.mask();
            // Wrapped lookup does not stop on an empty bucket.
            loop {
                let stored = keys.row(bucket).ok_or_else(|| LookupError::Bounds(bucket, keys.len()))?.key;
                if effects.equals(key, stored, keys).map_err(|e| CursorError::Lookup(LookupError::Effect(e)))? { return Ok(bucket); }
                bucket = (bucket + 1) & self.owner.mask();
            }
        }
    }
    pub fn next<E: HolderEffects>(&self, effects: &mut E) -> Result<usize, CursorError<E::Error>> {
        let null = { let mut state = self.state.lock().unwrap();
            if state.remaining == 0 { return Err(CursorError::NoSuchElement); }
            state.remaining = state.remaining.wrapping_sub(1); state.revision = state.revision.wrapping_add(1);
            let null = state.null_first; state.null_first = false; null
        };
        if null {
            let index = self.owner.capacity(); self.state.lock().unwrap().last = Some(index); return Ok(index);
        }
        let keys = self.owner.table.lease();
        effects.capture_table(&keys).map_err(|e| CursorError::Lookup(LookupError::Effect(e)))?;
        self.scan(&keys, effects)
    }

    pub fn remove<E: CursorEffects>(&self, effects: &mut E) -> Result<(), CursorError<E::Error>> {
        let (last, pos) = { let state = self.state.lock().unwrap(); (state.last.ok_or(CursorError::IllegalState)?, state.pos) };
        if last == self.owner.capacity() {
            self.owner.shape.lock().unwrap().contains_null = false;
            let table = self.owner.table.lease();
            table.set_key(self.owner.capacity(), 0).ok_or_else(|| LookupError::Bounds(self.owner.capacity(), table.len()))?;
        } else if pos >= 0 {
            self.owner.shift_wrapped(last, effects, |effects, from, to, table| {
                if from < to {
                    let key = table.row(from).ok_or_else(|| LookupError::Bounds(from, table.len()))?.key;
                    let revision = self.state.lock().unwrap().revision;
                    effects.pin_wrapped(key, table).map_err(LookupError::Effect)?;
                    let mut state = self.state.lock().unwrap();
                    if state.revision != revision { return Err(LookupError::StalePublication); }
                    state.wrapped.get_or_insert_with(|| Vec::with_capacity(2)).push(key);
                    state.revision = state.revision.wrapping_add(1);
                }
                Ok(())
            })?;
        } else {
            let index = pos.wrapping_neg().wrapping_sub(1) as usize;
            let key = self.wrapped(index).map_err(|error| match error {
                CursorError::WrappedNull => CursorError::WrappedNull,
                CursorError::WrappedBounds(i,n) => CursorError::WrappedBounds(i,n), _ => unreachable!(),
            })?;
            let unused = { let mut state = self.state.lock().unwrap();
                state.wrapped.as_mut().unwrap()[index] = 0;
                state.revision = state.revision.wrapping_add(1);
                !state.wrapped.as_ref().unwrap().contains(&key)
            };
            // The effect frame pins the selected wrapped holder through remove's
            // hash/equality callbacks, including after its list root is retired.
            effects.retire_wrapped(key, unused);
            self.owner.remove(key, effects)?;
            { let mut state = self.state.lock().unwrap(); state.last = None; state.revision = state.revision.wrapping_add(1); }
            return Ok(());
        }
        { let mut shape = self.owner.shape.lock().unwrap(); shape.size = shape.size.wrapping_sub(1); shape.revision = shape.revision.wrapping_add(1); }
        let mut state = self.state.lock().unwrap(); state.last = None; state.revision = state.revision.wrapping_add(1);
        Ok(())
    }

    pub fn for_each<E: CursorEffects>(&self, effects: &mut E) -> Result<(), CursorError<E::Error>> {
        let null = { let mut state = self.state.lock().unwrap(); let null = state.null_first; state.null_first = false;
            if null { state.revision = state.revision.wrapping_add(1); } null };
        if null {
            let index = self.owner.capacity(); self.state.lock().unwrap().last = Some(index);
            effects.accept(index).map_err(|e| CursorError::Lookup(LookupError::Effect(e)))?;
            let mut state = self.state.lock().unwrap(); state.remaining = state.remaining.wrapping_sub(1); state.revision = state.revision.wrapping_add(1);
        }
        let keys = self.owner.table.lease();
        effects.capture_table(&keys).map_err(|e| CursorError::Lookup(LookupError::Effect(e)))?;
        while self.has_next() {
            let index = self.scan(&keys, effects)?;
            effects.accept(index).map_err(|e| CursorError::Lookup(LookupError::Effect(e)))?;
            let mut state = self.state.lock().unwrap(); state.remaining = state.remaining.wrapping_sub(1); state.revision = state.revision.wrapping_add(1);
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
struct SplitState { pos: i32, max: i32, consumed: i32, null_first: bool, split: bool, revision: u64, pending: Option<(usize, u64, i32)> }
#[derive(Debug)]
pub struct EnchantmentSplit { pub owner: Arc<Enchantments>, state: Mutex<SplitState> }
#[derive(Debug, PartialEq, Eq)]
pub enum SplitSkipError { Negative, Bounds(usize, usize) }
impl EnchantmentSplit {
    pub fn new(owner: Arc<Enchantments>) -> Self {
        let max = owner.capacity() as i32; let null_first = owner.contains_null();
        Self { owner, state: Mutex::new(SplitState { pos: 0, max, consumed: 0, null_first, split: false, revision: 0, pending: None }) }
    }
    pub fn characteristics(&self) -> i64 { if self.state.lock().unwrap().split { 1 } else { 65 } }
    pub fn can_split(&self) -> bool { let state = self.state.lock().unwrap(); state.pos < state.max.wrapping_sub(1) && state.max.wrapping_sub(state.pos) >> 1 > 1 }
    pub fn estimate(&self) -> i64 {
        let state = self.state.lock().unwrap();
        let remaining = self.owner.size().wrapping_sub(state.consumed) as i64;
        if !state.split { return remaining; }
        let real = self.owner.size().wrapping_sub(i32::from(self.owner.contains_null()));
        let estimate = ((real as f64 / self.owner.capacity() as f64) * state.max.wrapping_sub(state.pos) as f64) as i64;
        remaining.min(estimate.wrapping_add(i64::from(state.null_first)))
    }
    /// Actual unpublished child allocation. Its Java facade/cleanup must be
    /// prepared before commit changes the parent range or null-key obligation.
    pub fn prepare_split(&self) -> Option<Self> {
        let state = self.state.lock().unwrap();
        if state.pos >= state.max.wrapping_sub(1) { return None; }
        let length = state.max.wrapping_sub(state.pos) >> 1;
        if length <= 1 { return None; }
        let end = state.pos.wrapping_add(length);
        Some(Self { owner: self.owner.clone(), state: Mutex::new(SplitState {
            pos: state.pos, max: end, consumed: 0, null_first: state.null_first,
            split: true, revision: 0, pending: Some((self as *const Self as usize, state.revision, end)),
        }) })
    }
    pub fn commit_split(&self, prepared: &Self) -> bool {
        if !Arc::ptr_eq(&self.owner, &prepared.owner) || std::ptr::eq(self, prepared) { return false; }
        if prepared.state.lock().unwrap().pending.is_none_or(|(source, _, _)| source != self as *const Self as usize) { return false; }
        let mut parent = self.state.lock().unwrap(); let mut child = prepared.state.lock().unwrap();
        let Some((source, revision, end)) = child.pending else { return false; };
        if source != self as *const Self as usize || parent.revision != revision || child.revision != 0 || child.pos != parent.pos || child.max != end { return false; }
        parent.pos = end; parent.null_first = false; parent.split = true; parent.revision = parent.revision.wrapping_add(1);
        child.pending = None;
        true
    }
    pub fn advance<E: CursorEffects>(&self, effects: &mut E) -> Result<bool, LookupError<E::Error>> {
        let null = { let mut state = self.state.lock().unwrap(); let null = state.null_first;
            if null { state.null_first = false; state.consumed = state.consumed.wrapping_add(1); state.revision = state.revision.wrapping_add(1); } null
        };
        if null { effects.accept(self.owner.capacity()).map_err(LookupError::Effect)?; return Ok(true); }
        let keys = self.owner.table.lease(); effects.capture_table(&keys).map_err(LookupError::Effect)?;
        loop {
            let index = { let state = self.state.lock().unwrap(); if state.pos >= state.max { return Ok(false); } state.pos as usize };
            let present = keys.row(index).ok_or_else(|| LookupError::Bounds(index, keys.len()))?.key != 0;
            { let mut state = self.state.lock().unwrap(); state.pos = state.pos.wrapping_add(1);
                if present { state.consumed = state.consumed.wrapping_add(1); } state.revision = state.revision.wrapping_add(1);
            }
            if present { effects.accept(index).map_err(LookupError::Effect)?; return Ok(true); }
        }
    }
    pub fn for_each<E: CursorEffects>(&self, effects: &mut E) -> Result<(), LookupError<E::Error>> {
        let null = { let mut state = self.state.lock().unwrap(); let null = state.null_first;
            if null { state.null_first = false; state.consumed = state.consumed.wrapping_add(1); state.revision = state.revision.wrapping_add(1); } null
        };
        if null { effects.accept(self.owner.capacity()).map_err(LookupError::Effect)?; }
        let keys = self.owner.table.lease(); effects.capture_table(&keys).map_err(LookupError::Effect)?;
        loop {
            let index = { let state = self.state.lock().unwrap(); if state.pos >= state.max { return Ok(()); } state.pos as usize };
            if keys.row(index).ok_or_else(|| LookupError::Bounds(index, keys.len()))?.key != 0 {
                effects.accept(index).map_err(LookupError::Effect)?;
                let mut state = self.state.lock().unwrap(); state.consumed = state.consumed.wrapping_add(1);
            }
            let mut state = self.state.lock().unwrap(); state.pos = state.pos.wrapping_add(1); state.revision = state.revision.wrapping_add(1);
        }
    }
    pub fn skip(&self, mut count: i64) -> Result<i64, SplitSkipError> {
        if count < 0 { return Err(SplitSkipError::Negative); } if count == 0 { return Ok(0); }
        let mut skipped = 0i64;
        { let mut state = self.state.lock().unwrap(); if state.null_first {
            state.null_first = false; state.revision = state.revision.wrapping_add(1); skipped += 1; count -= 1;
        } }
        let keys = self.owner.table.lease();
        loop {
            let index = { let mut state = self.state.lock().unwrap(); if state.pos >= state.max || count <= 0 { return Ok(skipped); }
                let index = state.pos as usize; state.pos = state.pos.wrapping_add(1); state.revision = state.revision.wrapping_add(1); index
            };
            if keys.row(index).ok_or_else(|| SplitSkipError::Bounds(index, keys.len()))?.key != 0 { skipped += 1; count -= 1; }
        }
    }
}

fn mix(hash: i32) -> usize { let h = (hash as u32).wrapping_mul(0x9e37_79b9); (h ^ (h >> 16)) as usize }
fn max_fill(n: usize) -> usize { (n * 3).div_ceil(4).min(n - 1) }
fn capacity(expected: usize) -> usize { (expected * 4).div_ceil(3).next_power_of_two().max(2) }

#[cfg(test)]
mod tests {
    use super::*;
    struct Effects<'a> {
        owner: &'a Enchantments,
        events: Vec<(u8, u64, u64)>,
        hash_action: Option<Box<dyn FnOnce(&Enchantments) + 'a>>,
        equal_action: Option<Box<dyn FnOnce(&Enchantments) + 'a>>,
        prepare_action: Option<Box<dyn FnOnce(&Enchantments) + 'a>>,
        anchor_action: Option<Box<dyn FnOnce(&Enchantments) + 'a>>,
        accept_action: Option<Box<dyn FnMut(usize) -> Result<(), &'static str> + 'a>>,
        hash_code: i32,
        failure: bool,
    }
    impl<'a> Effects<'a> {
        fn new(owner: &'a Enchantments) -> Self { Self { owner, events: vec![], hash_action: None, equal_action: None, prepare_action: None, anchor_action: None, accept_action: None, hash_code: 5, failure: false } }
    }
    impl HolderEffects for Effects<'_> {
        type Error = &'static str;
        fn hash(&mut self, key: u64, _: &Arc<EnchantmentTable>) -> Result<i32, Self::Error> {
            self.events.push((0, key, 0));
            if let Some(action) = self.hash_action.take() { action(self.owner); }
            if self.failure { Err("same effect failure") } else { Ok(self.hash_code) }
        }
        fn equals(&mut self, query: u64, stored: u64, _: &Arc<EnchantmentTable>) -> Result<bool, Self::Error> {
            self.events.push((1, query, stored));
            if let Some(action) = self.equal_action.take() { action(self.owner); }
            Ok(query == stored)
        }
    }
    impl RootEffects for Effects<'_> {
        fn bind(&mut self, query: u64, _: &Arc<EnchantmentTable>) -> Result<u64, Self::Error> {
            self.events.push((2, query, 0));
            if let Some(action) = self.equal_action.take() { action(self.owner); }
            if self.failure { Err("same effect failure") } else { Ok(query) }
        }
        fn prepare_table(&mut self, _: &Arc<EnchantmentTable>, _: &Arc<EnchantmentTable>) -> Result<(), Self::Error> {
            self.events.push((3, 0, 0));
            if let Some(action) = self.prepare_action.take() { action(self.owner); }
            Ok(())
        }
        fn anchor(&mut self, key: u64, _: &Arc<EnchantmentTable>, _: &Arc<EnchantmentTable>) -> Result<(), Self::Error> {
            self.events.push((4, key, 0));
            if let Some(action) = self.anchor_action.take() { action(self.owner); }
            Ok(())
        }
        fn published(&mut self, _: &Arc<EnchantmentTable>) { self.events.push((5, 0, 0)); }
        fn retired(&mut self, key: u64, _: &Arc<EnchantmentTable>) { self.events.push((6, key, 0)); }
    }
    impl CursorEffects for Effects<'_> {
        fn pin_wrapped(&mut self, key: u64, _: &Arc<EnchantmentTable>) -> Result<(), Self::Error> { self.events.push((7, key, 0)); Ok(()) }
        fn retire_wrapped(&mut self, key: u64, retire: bool) { self.events.push((8, key, u64::from(retire))); }
        fn accept(&mut self, index: usize) -> Result<(), Self::Error> {
            self.events.push((9, index as u64, 0));
            if let Some(action) = self.accept_action.as_mut() { action(index)?; }
            Ok(())
        }
    }
    fn seeded() -> (Enchantments, usize) {
        let owner = Enchantments::new(32);
        let pos = mix(5) & 31;
        owner.insert_at(pos, 1, 2).unwrap();
        (owner, pos)
    }
    #[test]
    fn holder_query_calls_identity_equality_and_reads_reentrant_current_value() {
        let (owner, pos) = seeded();
        let mut effects = Effects::new(&owner);
        effects.equal_action = Some(Box::new(move |owner| { owner.table.lease().set_level(pos, 7); }));
        assert_eq!(owner.get(1, None, &mut effects), Ok(7));
        assert_eq!(effects.events, [(0, 1, 0), (1, 1, 1)]);
    }
    #[test]
    fn lookup_keeps_old_key_allocation_after_hash_replaces_current_table() {
        let (owner, pos) = seeded();
        let mut effects = Effects::new(&owner);
        effects.hash_action = Some(Box::new(move |owner| {
            let old = owner.table.lease();
            let next = Arc::new(EnchantmentTable::new(64));
            next.write(pos, EnchantmentRow { key: 99, level: 11 });
            assert!(owner.publish_rehash(&old, next, 64));
        }));
        // mix(5) has the same lower six and five bits. The old table still
        // contains key 1; the current value array at that index contains 11.
        assert_eq!(mix(5) & 31, mix(5) & 63);
        assert_eq!(owner.get(1, None, &mut effects), Ok(11));
        assert_eq!(effects.events, [(0, 1, 0), (1, 1, 1)]);
    }
    #[test]
    fn null_and_default_return_do_not_invoke_holder_effects() {
        let owner = Enchantments::new(32);
        owner.set_default_level(17);
        let mut effects = Effects::new(&owner);
        assert_eq!(owner.get(0, None, &mut effects), Ok(17));
        assert_eq!(owner.get(0, Some(0), &mut effects), Ok(0));
        owner.insert_at(32, 0, -9).unwrap();
        assert_eq!(owner.get(0, None, &mut effects), Ok(-9));
        assert_eq!(owner.size(), 1);
        assert!(effects.events.is_empty());
    }
    #[test]
    fn failed_rehash_anchor_keeps_existing_native_allocation_and_inserted_prefix() {
        let (owner, pos) = seeded();
        let old = owner.table.lease();
        let mut effects = Effects::new(&owner);
        let result = owner.rehash(64, &mut effects, |_, _| Err("root allocation failed"));
        assert!(matches!(result, Err(LookupError::Effect("root allocation failed"))));
        assert!(Arc::ptr_eq(&old, &owner.table.lease()));
        assert_eq!(old.row(pos).unwrap().level, 2);
        assert_eq!(owner.size(), 1);
    }
    #[test]
    fn rehash_copies_values_after_hash_effect_and_actual_leases_survive_retirement() {
        let (owner, pos) = seeded();
        let old = owner.table.lease();
        let weak = Arc::downgrade(&old);
        let mut effects = Effects::new(&owner);
        effects.hash_action = Some(Box::new(move |owner| { owner.table.lease().set_level(pos, 13); }));
        let mut rooted = vec![];
        let next = owner.rehash(64, &mut effects, |_, key| { rooted.push(key); Ok(()) }).unwrap();
        assert_eq!(next.row(mix(5) & 63).unwrap().level, 13);
        assert_eq!(rooted, [1]);
        assert!(owner.publish_rehash(&old, next, 64));
        drop(effects);
        drop(owner);
        assert_eq!(old.row(pos).unwrap().level, 13);
        drop(old);
        assert!(weak.upgrade().is_none());
    }
    #[test]
    fn hash_failure_stops_before_equality_without_changing_state() {
        let (owner, pos) = seeded();
        let mut effects = Effects::new(&owner);
        effects.failure = true;
        assert_eq!(owner.get(1, None, &mut effects), Err(LookupError::Effect("same effect failure")));
        assert_eq!(effects.events, [(0, 1, 0)]);
        assert_eq!(owner.table.lease().row(pos).unwrap().level, 2);
    }
    #[test]
    fn in_place_reentry_during_vm_binding_rejects_stale_insert_without_losing_nested_write() {
        let owner = Enchantments::new(32);
        let pos = mix(5) & 31;
        let mut effects = Effects::new(&owner);
        effects.equal_action = Some(Box::new(move |owner| { owner.insert_at(pos, 9, 12).unwrap(); }));
        assert_eq!(owner.put(1, 3, &mut effects), Err(LookupError::StalePublication));
        assert_eq!(owner.size(), 1);
        assert_eq!(owner.table.lease().row(pos), Some(EnchantmentRow { key: 9, level: 12 }));
    }
    #[test]
    fn put_binds_before_membership_and_prepares_actual_rehash_before_publication() {
        let owner = Enchantments::new(2);
        let mut effects = Effects::new(&owner);
        assert_eq!(owner.put(1, 1, &mut effects), Ok(0));
        assert_eq!(owner.put(2, 2, &mut effects), Ok(0));
        assert_eq!(owner.capacity(), 4);
        assert_eq!(owner.size(), 2);
        assert_eq!(owner.get(1, None, &mut effects), Ok(1));
        assert_eq!(owner.get(2, None, &mut effects), Ok(2));
        let prepare = effects.events.iter().position(|e| e.0 == 3).unwrap();
        let published = effects.events.iter().position(|e| e.0 == 5).unwrap();
        assert!(effects.events[prepare + 1..published].iter().any(|e| e.0 == 4));
    }
    #[test]
    fn remove_shifts_collisions_retires_last_key_and_keeps_the_original_stale_value() {
        let (owner, pos) = seeded();
        let retained = owner.table.lease();
        let mut effects = Effects::new(&owner);
        assert_eq!(owner.remove(1, &mut effects), Ok(2));
        assert_eq!(retained.row(pos), Some(EnchantmentRow { key: 0, level: 2 }));
        assert_eq!(owner.size(), 0);
        assert!(effects.events.contains(&(6, 1, 0)));
        for key in 1..=96 { owner.put(key, key as i32, &mut effects).unwrap(); }
        for key in 1..=96 { assert_eq!(owner.remove(key, &mut effects), Ok(key as i32)); }
        assert_eq!(owner.size(), 0);
        assert_eq!(owner.capacity(), 32);
    }
    #[test]
    fn removal_hash_failure_preserves_decremented_size_and_unshifted_key_prefix() {
        let (owner, pos) = seeded();
        owner.insert_at((pos + 1) & 31, 2, 3).unwrap();
        let mut effects = Effects::new(&owner);
        // Equality succeeds during find; only the following displaced-key hash
        // fails, after removeEntry's observable size decrement.
        effects.equal_action = Some(Box::new(|_| {}));
        struct FailShift<'a>(Effects<'a>);
        impl HolderEffects for FailShift<'_> {
            type Error = &'static str;
            fn hash(&mut self, key: u64, table: &Arc<EnchantmentTable>) -> Result<i32, Self::Error> {
                if key == 2 { Err("displaced holder hash") } else { self.0.hash(key, table) }
            }
            fn equals(&mut self, query: u64, stored: u64, table: &Arc<EnchantmentTable>) -> Result<bool, Self::Error> { self.0.equals(query, stored, table) }
        }
        impl RootEffects for FailShift<'_> {
            fn bind(&mut self, key: u64, table: &Arc<EnchantmentTable>) -> Result<u64, Self::Error> { self.0.bind(key, table) }
            fn prepare_table(&mut self, table: &Arc<EnchantmentTable>, source: &Arc<EnchantmentTable>) -> Result<(), Self::Error> { self.0.prepare_table(table, source) }
            fn anchor(&mut self, key: u64, table: &Arc<EnchantmentTable>, source: &Arc<EnchantmentTable>) -> Result<(), Self::Error> { self.0.anchor(key, table, source) }
            fn published(&mut self, table: &Arc<EnchantmentTable>) { self.0.published(table); }
            fn retired(&mut self, key: u64, table: &Arc<EnchantmentTable>) { self.0.retired(key, table); }
        }
        let mut effects = FailShift(effects);
        assert_eq!(owner.remove(1, &mut effects), Err(LookupError::Effect("displaced holder hash")));
        assert_eq!(owner.size(), 1);
        assert_eq!(owner.table.lease().row(pos).unwrap().key, 1);
        assert_eq!(owner.table.lease().row((pos + 1) & 31).unwrap().key, 2);
    }
    #[test]
    fn private_vm_table_preparation_preserves_nested_shape_change_and_rejects_stale_publication() {
        let (owner, _) = seeded();
        let old = owner.table.lease();
        let mut effects = Effects::new(&owner);
        effects.prepare_action = Some(Box::new(|owner| owner.set_default_level(17)));
        assert_eq!(owner.rehash_owned(64, &mut effects), Err(LookupError::StalePublication));
        assert!(Arc::ptr_eq(&old, &owner.table.lease()));
        assert_eq!(owner.default_level(), 17);
        assert!(!effects.events.iter().any(|e| e.0 == 5));
    }
    #[test]
    fn private_vm_anchor_preparation_preserves_nested_value_change_and_rejects_stale_publication() {
        let (owner, pos) = seeded();
        let old = owner.table.lease();
        let mut effects = Effects::new(&owner);
        effects.anchor_action = Some(Box::new(move |owner| { owner.table.lease().set_level(pos, 9); }));
        assert_eq!(owner.rehash_owned(64, &mut effects), Err(LookupError::StalePublication));
        assert!(Arc::ptr_eq(&old, &owner.table.lease()));
        assert_eq!(old.row(pos).unwrap().level, 9);
    }
    #[test]
    fn genuine_holder_hash_reentry_is_consumed_without_a_stale_vm_preparation_rejection() {
        let (owner, pos) = seeded();
        let mut effects = Effects::new(&owner);
        effects.hash_action = Some(Box::new(move |owner| { owner.set_default_level(17); owner.table.lease().set_level(pos, 9); }));
        assert_eq!(owner.rehash_owned(64, &mut effects), Ok(()));
        assert_eq!(owner.capacity(), 64);
        assert_eq!(owner.default_level(), 17);
        assert_eq!(owner.table.lease().row(mix(5) & 63).unwrap().level, 9);
    }
    #[test]
    fn native_cursor_wrap_removal_keeps_original_order_hash_effects_and_last_membership() {
        let owner = Arc::new(Enchantments::new(32));
        let mut effects = Effects::new(&owner);
        effects.hash_code = (0..1000).find(|&hash| mix(hash) & 31 == 31).unwrap();
        for key in 1..=3 { owner.put(key, key as i32, &mut effects).unwrap(); }
        effects.events.clear();
        let cursor = EnchantmentCursor::new(owner.clone());
        let mut seen = vec![];
        while cursor.has_next() {
            let index = cursor.next(&mut effects).unwrap();
            seen.push(owner.table.lease().row(index).unwrap().key);
            cursor.remove(&mut effects).unwrap();
        }
        assert_eq!(seen, [1, 3, 2]);
        assert_eq!(owner.size(), 0);
        let user: Vec<_> = effects.events.iter().copied().filter(|e| e.0 <= 1).collect();
        assert_eq!(user, [(0,2,0),(0,3,0),(0,2,0),(1,2,2),(0,2,0),(1,2,2)]);
        assert_eq!(cursor.remove(&mut effects), Err(CursorError::IllegalState));
    }
    #[test]
    fn native_cursor_consumer_error_leaves_original_counter_and_resume_failure_prefix() {
        let owner = Arc::new(Enchantments::new(32));
        let mut effects = Effects::new(&owner);
        owner.put(1, 1, &mut effects).unwrap(); owner.put(2, 2, &mut effects).unwrap();
        let cursor = EnchantmentCursor::new(owner.clone());
        effects.accept_action = Some(Box::new(|_| Err("original consumer")));
        assert_eq!(cursor.for_each(&mut effects), Err(CursorError::Lookup(LookupError::Effect("original consumer"))));
        assert!(cursor.has_next());
        let next = cursor.next(&mut effects).unwrap();
        assert_eq!(owner.table.lease().row(next).unwrap().key, 1);
        assert_eq!(cursor.next(&mut effects), Err(CursorError::WrappedNull));
        assert!(!cursor.has_next());
    }
    #[test]
    fn native_cursor_consumer_reentry_uses_the_same_actual_cursor_before_outer_decrement() {
        let owner = Arc::new(Enchantments::new(32));
        let mut effects = Effects::new(&owner);
        owner.put(1, 1, &mut effects).unwrap(); owner.put(2, 2, &mut effects).unwrap();
        drop(effects);
        let cursor = EnchantmentCursor::new(owner.clone());
        let nested = std::cell::RefCell::new(vec![]);
        let mut effects = Effects::new(&owner);
        effects.accept_action = Some(Box::new(|_| {
            if cursor.has_next() {
                let mut inner = Effects::new(&owner);
                let index = cursor.next(&mut inner).unwrap();
                nested.borrow_mut().push(owner.table.lease().row(index).unwrap().key);
            }
            Ok(())
        }));
        cursor.for_each(&mut effects).unwrap();
        assert_eq!(*nested.borrow(), [1]);
        assert!(!cursor.has_next());
    }
    #[test]
    fn split_foreach_consumer_error_repeats_current_entry_while_advance_commits_before_effect() {
        let owner = Arc::new(Enchantments::new(32));
        let mut effects = Effects::new(&owner);
        owner.put(1, 1, &mut effects).unwrap(); owner.put(2, 2, &mut effects).unwrap();
        let split = EnchantmentSplit::new(owner.clone());
        effects.accept_action = Some(Box::new(|_| Err("original split consumer")));
        assert_eq!(split.for_each(&mut effects), Err(LookupError::Effect("original split consumer")));
        assert_eq!(split.estimate(), 2);
        assert_eq!(split.advance(&mut effects), Err(LookupError::Effect("original split consumer")));
        assert_eq!(split.estimate(), 1);
        let accepted: Vec<_> = effects.events.iter().filter(|e| e.0 == 9).map(|e| e.1).collect();
        assert_eq!(accepted, [mix(5) as u64 & 31, mix(5) as u64 & 31]);
    }
    #[test]
    fn split_child_is_prepared_without_parent_publication_and_owns_its_independent_range() {
        let owner = Arc::new(Enchantments::new(32));
        let mut effects = Effects::new(&owner);
        owner.put(1, 1, &mut effects).unwrap(); owner.put(2, 2, &mut effects).unwrap();
        let parent = EnchantmentSplit::new(owner.clone());
        let child = parent.prepare_split().unwrap();
        assert_eq!(parent.estimate(), 2); assert_eq!(parent.characteristics(), 65);
        assert!(parent.commit_split(&child));
        assert_eq!(parent.estimate(), 1); assert_eq!(child.estimate(), 1);
        assert_eq!(parent.characteristics(), 1); assert_eq!(child.characteristics(), 1);
        child.for_each(&mut effects).unwrap();
        parent.for_each(&mut effects).unwrap();
        let accepted: Vec<_> = effects.events.iter().filter(|e| e.0 == 9).map(|e| e.1).collect();
        assert_eq!(accepted, [mix(5) as u64 & 31, (mix(5) as u64 & 31) + 1]);
        assert_eq!(parent.estimate(), 0); assert_eq!(child.estimate(), 0);
    }
    #[test]
    fn stale_or_wrong_source_split_adoption_does_not_replace_parent_progress() {
        let owner = Arc::new(Enchantments::new(32));
        let mut effects = Effects::new(&owner); owner.put(1, 1, &mut effects).unwrap();
        let parent = EnchantmentSplit::new(owner.clone());
        let other = EnchantmentSplit::new(owner.clone());
        let child = parent.prepare_split().unwrap();
        assert!(!other.commit_split(&child));
        parent.advance(&mut effects).unwrap();
        assert!(!parent.commit_split(&child));
        assert_eq!(parent.estimate(), 0); assert_eq!(parent.characteristics(), 65);
        assert_eq!(other.estimate(), 1); assert_eq!(other.characteristics(), 65);
    }
}
