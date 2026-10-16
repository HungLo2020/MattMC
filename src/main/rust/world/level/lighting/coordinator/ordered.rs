//! Lighting's fastutil-compatible primitive tables and detached live cursors.
//! Values are bytes, membership tags or CPU identity slots; no Java objects.
//! The node set uses its original 0.5 load factor and trim(512); other tables
//! use 0.75. Table order is observable through callbacks and failure prefixes.
//! Adapted from fastutil 8.5.12 Long2ObjectOpenHashMap (Apache-2.0),
//! Copyright (C) 2002-2023 Sebastiano Vigna.
//! https://www.apache.org/licenses/LICENSE-2.0
use super::super::maps::Entry;
use std::sync::{Arc, Mutex, atomic::{AtomicI64, AtomicU32, Ordering}};

/// Retained actual array generations. Interior atomic elements permit safe
/// ownership across detached callbacks; policy mutations still hold State.
pub(super) type KeyGeneration = Vec<AtomicI64>;
enum Values {
    Slots(Vec<AtomicU32>),
    // The actual object array owns its typed entries per position. Captured
    // generations retain these roots directly, without an auxiliary slot map.
    Entries(Mutex<Vec<Option<Arc<Entry>>>>),
}
pub(super) struct ValueGeneration { data:Values,len:usize,bytes:usize }
impl ValueGeneration {
    fn new(len:usize,empty:u32,typed:bool)->Result<Self,Fault>{
        if typed{
            let mut rows=Vec::new();rows.try_reserve_exact(len).map_err(|_|Fault::Allocation)?;rows.resize_with(len,||None);
            let bytes=rows.capacity()*std::mem::size_of::<Option<Arc<Entry>>>();
            Ok(Self{data:Values::Entries(Mutex::new(rows)),len,bytes})
        }else{
            let mut rows=Vec::new();rows.try_reserve_exact(len).map_err(|_|Fault::Allocation)?;rows.resize_with(len,||AtomicU32::new(empty));
            let bytes=rows.capacity()*std::mem::size_of::<u32>();Ok(Self{data:Values::Slots(rows),len,bytes})
        }
    }
    fn len(&self)->usize{self.len}
    fn is_typed(&self)->bool{matches!(&self.data,Values::Entries(_))}
    pub(super) fn get(&self,at:usize)->Option<u32>{
        match &self.data{
            Values::Slots(rows)=>rows.get(at).map(|v|v.load(Ordering::Relaxed)),
            Values::Entries(rows)=>rows.lock().unwrap().get(at).map(|v|v.as_ref().map_or(0,|entry|entry.id)),
        }
    }
    pub(super) fn entry_at(&self,at:usize)->Option<Arc<Entry>>{
        match &self.data{Values::Slots(_)=>None,Values::Entries(rows)=>rows.lock().unwrap().get(at).and_then(Clone::clone)}
    }
    fn set(&self,index:i32,value:u32,entry:Option<Arc<Entry>>)->Result<u32,Fault>{
        if index<0||index as usize>=self.len{return Err(Fault::ArrayIndex(index,self.len));}
        match &self.data{
            Values::Slots(rows)=>Ok(rows[index as usize].swap(value,Ordering::Relaxed)),
            Values::Entries(rows)=>{
                let entry=match entry{Some(entry) if entry.id==value=>Some(entry),None if value==0=>None,_=>return Err(Fault::IllegalState)};
                let mut rows=rows.lock().unwrap();let previous=rows[index as usize].as_ref().map_or(0,|entry|entry.id);rows[index as usize]=entry;Ok(previous)
            }
        }
    }
    fn move_at(&self,to:usize,from:usize)->Result<(),Fault>{
        if to>=self.len{return Err(Fault::ArrayIndex(to as i32,self.len));}
        if from>=self.len{return Err(Fault::ArrayIndex(from as i32,self.len));}
        match &self.data{
            Values::Slots(rows)=>{let value=rows[from].load(Ordering::Relaxed);rows[to].store(value,Ordering::Relaxed);},
            Values::Entries(rows)=>{let mut rows=rows.lock().unwrap();let value=rows[from].clone();rows[to]=value;},
        }Ok(())
    }
    fn fill(&self,value:u32){match &self.data{
        Values::Slots(rows)=>for target in rows{target.store(value,Ordering::Relaxed);},
        Values::Entries(rows)=>{debug_assert_eq!(value,0);for entry in rows.lock().unwrap().iter_mut(){*entry=None;}},
    }}
}
#[derive(Debug, PartialEq, Eq)]
pub(super) enum Fault {
    Allocation,
    NullPointer,
    ArrayIndex(i32, usize),
    IndexOutOfBounds(i32, usize),
    NoSuchElement,
    IllegalState,
}


/// A `null` value slot.
pub(super) const NONE: u32 = u32::MAX;
const DEFAULT_INITIAL_SIZE: usize = 16;

fn mix(key: i64) -> usize {
    let h = (key as u64).wrapping_mul(0x9e37_79b9_7f4a_7c15);
    let h = h ^ (h >> 32);
    (h ^ (h >> 16)) as u32 as i32 as usize
}

/// `HashCommon.arraySize(expected, 0.75f)`.
fn array_size(expected: usize, load: f32) -> usize {
    ((expected as f32 / load) as f64).ceil().max(2.0).min((1u64 << 30) as f64) as usize
}

fn max_fill(n: usize, load: f32) -> i32 {
    ((n as f64 * load as f64).ceil() as usize).min(n - 1) as i32
}

fn table(n: usize, empty: u32, typed: bool) -> Result<(Arc<KeyGeneration>, Arc<ValueGeneration>), Fault> {
    let mut keys = Vec::new();
    keys.try_reserve_exact(n + 1).map_err(|_| Fault::Allocation)?;
    keys.resize_with(n + 1, || AtomicI64::new(0));
    Ok((Arc::new(keys), Arc::new(ValueGeneration::new(n+1,empty,typed)?)))
}

pub(super) struct Table {
    keys: Arc<KeyGeneration>,
    values: Arc<ValueGeneration>,
    n: usize,
    min_n: usize,
    max_fill: i32,
    size: i32,
    load: f32,
    zero: bool,
    primitive_values: bool,
}

impl Table {
    /// `new Long2ObjectOpenHashMap<>(expected)`.
    pub(super) fn with_expected(expected: usize, load: f32) -> Result<Self, Fault> {
        let n = array_size(expected, load).next_power_of_two();
        let (keys, values) = table(n, NONE, false)?;
        Ok(Self { keys, values, n, min_n: n, max_fill: max_fill(n, load), size: 0, load, zero: false, primitive_values: false })
    }

    pub(super) fn primitive_values(&mut self) { self.primitive_values = true; self.values.fill(0); }
    pub(super) fn typed_values(&mut self)->Result<(),Fault>{assert_eq!(Arc::strong_count(&self.values),1,"unpublished value array");self.values=Arc::new(ValueGeneration::new(self.values.len(),0,true)?);Ok(())}
    pub(super) fn captured_keys(&self)->Arc<KeyGeneration>{Arc::clone(&self.keys)}
    pub(super) fn captured_values(&self)->Arc<ValueGeneration>{Arc::clone(&self.values)}
    pub(super) fn entry_at(&self,index:i32)->Option<Arc<Entry>>{self.values.entry_at(index as usize)}
    fn key(&self,at:usize)->i64{self.keys[at].load(Ordering::Relaxed)}
    fn value(&self,at:usize)->u32{self.values.get(at).unwrap()}
    fn empty_value(&self)->u32{if self.primitive_values || self.values.is_typed(){0}else{NONE}}
    pub(super) fn set_value_at(&self,index:i32,value:u32,entry:Option<Arc<Entry>>)->Result<u32,Fault>{self.values.set(index,value,entry)}
    pub(super) fn has_zero(&self)->bool{self.zero}

    pub(super) fn len(&self) -> i32 {
        self.size
    }

    /// Owned array capacity, excluding this struct and allocator bookkeeping.
    pub(super) fn allocation_bytes(&self) -> usize {
        self.keys.capacity() * std::mem::size_of::<i64>()
            + self.values.bytes
    }

    fn mask(&self) -> usize {
        self.n - 1
    }

    pub(super) fn find(&self, key: i64) -> Result<usize, usize> {
        if key == 0 {
            return if self.zero { Ok(self.n) } else { Err(self.n) };
        }
        let mut pos = mix(key) & self.mask();
        loop {
            match self.key(pos) {
                0 => return Err(pos),
                k if k == key => return Ok(pos),
                _ => pos = (pos + 1) & self.mask(),
            }
        }
    }

    pub(super) fn get(&self, key: i64) -> u32 {
        match self.find(key) {
            Ok(pos) => self.value(pos),
            Err(_) => NONE,
        }
    }

    /// `computeIfAbsent(key, k -> new list)`: the existing value (even null) or
    /// `create()` inserted. Returns the value and whether it was created.
    pub(super) fn compute_if_absent(&mut self, key: i64, create: impl FnOnce() -> Result<u32, Fault>) -> Result<(u32, bool), Fault> {
        match self.find(key) {
            Ok(pos) => Ok((self.value(pos), false)),
            Err(pos) => {
                let value = create()?;
                if pos == self.n {
                    self.zero = true;
                }
                self.keys[pos].store(key, Ordering::Relaxed);
                self.values.set(pos as i32, value, None)?;
                let old = self.size;
                self.size = self.size.wrapping_add(1);
                if old >= self.max_fill {
                    self.rehash(array_size(self.size.wrapping_add(1).max(0) as usize, self.load).next_power_of_two())?;
                }
                Ok((value, true))
            }
        }
    }

    /// `put(key, value)`: replaces in place, or inserts (growing as fastutil
    /// does); returns the previous value or `NONE`.
    pub(super) fn put(&mut self, key: i64, value: u32) -> Result<u32, Fault> { self.put_value(key,value,None) }
    pub(super) fn put_entry(&mut self,key:i64,entry:Option<Arc<Entry>>)->Result<u32,Fault>{
        let value=entry.as_ref().map_or(0,|entry|entry.id);self.put_value(key,value,entry)
    }
    fn put_value(&mut self, key:i64,value:u32,entry:Option<Arc<Entry>>)->Result<u32,Fault>{
        match self.find(key) {
            Ok(pos) => self.values.set(pos as i32, value, entry),
            Err(pos) => {
                if pos == self.n {
                    self.zero = true;
                }
                self.keys[pos].store(key, Ordering::Relaxed);
                self.values.set(pos as i32, value, entry)?;
                let old = self.size;
                self.size = self.size.wrapping_add(1);
                if old >= self.max_fill {
                    self.rehash(array_size(self.size.wrapping_add(1).max(0) as usize, self.load).next_power_of_two())?;
                }
                Ok(NONE)
            }
        }
    }
    pub(super) fn insertion_position(&self,key:i64)->i32{
        match self.find(key){Ok(at)=>at as i32,Err(at)=>-(at as i32)-1}
    }
    /// Callback methods commit to their original position without refinding.
    pub(super) fn insert_at(&mut self,index:i32,key:i64,value:u32,entry:Option<Arc<Entry>>)->Result<(),Fault>{
        if index==self.n as i32 {self.zero=true;}
        self.keys.get(index as usize).ok_or(Fault::ArrayIndex(index,self.keys.len()))?.store(key,Ordering::Relaxed);
        self.values.set(index,value,entry)?;
        let old=self.size;self.size=self.size.wrapping_add(1);
        if old>=self.max_fill {self.rehash(array_size(self.size.wrapping_add(1).max(0) as usize,self.load).next_power_of_two())?;}
        Ok(())
    }
    pub(super) fn remove_at(&mut self,index:i32,zero:bool)->Result<u32,Fault>{
        let at=if zero{self.n as i32}else{index};let previous=self.value_at(at)?;
        if !self.primitive_values {self.values.set(at,self.empty_value(),None)?;}
        if zero {self.zero=false;}
        self.size=self.size.wrapping_sub(1);
        if !zero {self.shift(at as usize,None)?;}
        if self.n>self.min_n && self.size<self.max_fill/4 && self.n>DEFAULT_INITIAL_SIZE {self.rehash(self.n/2)?;}
        Ok(previous)
    }

    /// The table length (`key.length`), for an out-of-bounds report.
    pub(super) fn table_len(&self) -> usize {
        self.keys.len()
    }

    /// `remove(key)`: the removed value, or `NONE`.
    pub(super) fn remove(&mut self, key: i64) -> Result<u32, Fault> {
        let Ok(pos) = self.find(key) else { return Ok(NONE) };
        self.remove_at(pos as i32,key==0)
    }

    /// `clear()`; returns the values it dropped.
    pub(super) fn clear(&mut self) -> Vec<u32> {
        if self.size == 0 {
            return Vec::new();
        }
        let dropped = self.ordered().into_iter().map(|(_, v)| v).collect();
        self.clear_membership();
        dropped
    }

    /// Clear a primitive map/set whose values need no retirement. Retain the
    /// original table capacity without allocating a discarded value vector.
    pub(super) fn clear_membership(&mut self) {
        if self.size == 0 { return; }
        self.size = 0;
        self.zero = false;
        for key in self.keys.iter() { key.store(0, Ordering::Relaxed); }
        if !self.primitive_values { self.values.fill(self.empty_value()); }
    }

    fn shift(&mut self, mut pos: usize, mut wrapped: Option<&mut Option<Vec<i64>>>) -> Result<(), Fault> {
        let mask = self.mask();
        loop {
            let last = pos;
            pos = (last + 1) & mask;
            let current;
            loop {
                let key = self.key(pos);
                if key == 0 {
                    let length = self.keys.len();
                    self.keys.get(last).ok_or(Fault::ArrayIndex(last as i32, length))?.store(0, Ordering::Relaxed);
                    if !self.primitive_values { self.values.set(last as i32, self.empty_value(), None)?; }
                    return Ok(());
                }
                let slot = mix(key) & mask;
                if if last <= pos { last >= slot || slot > pos } else { last >= slot && slot > pos } {
                    current = key;
                    break;
                }
                pos = (pos + 1) & mask;
            }
            if pos < last {
                if let Some(w) = wrapped.as_deref_mut() {
                    w.get_or_insert_with(|| Vec::with_capacity(2)).push(self.key(pos));
                }
            }
            let length = self.keys.len();
            self.keys.get(last).ok_or(Fault::ArrayIndex(last as i32, length))?.store(current, Ordering::Relaxed);
            self.values.move_at(last,pos)?;
        }
    }

    fn rehash(&mut self, new_n: usize) -> Result<(), Fault> {
        let (keys, values) = table(new_n, self.empty_value(), self.values.is_typed())?;
        let mask = new_n - 1;
        let mut i = self.n as i32;
        let real = self.size.wrapping_sub(i32::from(self.zero)).max(0);
        for _ in 0..real {
            loop {
                i = i.wrapping_sub(1);
                if self.key_at(i)? != 0 {
                    break;
                }
            }
            let key=self.key_at(i)?;let value=self.value_at(i)?;
            let mut pos = mix(key) & mask;
            while keys[pos].load(Ordering::Relaxed) != 0 {
                pos = (pos + 1) & mask;
            }
            keys[pos].store(key, Ordering::Relaxed);
            values.set(pos as i32, value, self.entry_at(i))?;
        }
        values.set(new_n as i32, self.value(self.n), self.entry_at(self.n as i32))?;
        self.keys = keys;
        self.values = values;
        self.n = new_n;
        self.max_fill = max_fill(new_n, self.load);
        Ok(())
    }

    pub(super) fn trim(&mut self, expected: usize) -> bool {
        let n = array_size(expected, self.load).next_power_of_two();
        if n >= self.n || self.size > max_fill(n, self.load) { return true; }
        self.rehash(n).is_ok()
    }

    /// Iteration order of every map view: the zero key, then descending slots.
    pub(super) fn ordered(&self) -> Vec<(i64, u32)> {
        let mut out = Vec::with_capacity(self.size.max(0) as usize);
        if self.zero {
            out.push((0, self.value(self.n)));
        }
        for pos in (0..self.n).rev() {
            if self.key(pos) != 0 {
                out.push((self.key(pos), self.value(pos)));
            }
        }
        out
    }

    pub(super) fn cursor(&self) -> Cursor {
        Cursor { mark: Mark { pos: self.n as i32, last: -1, left: self.size, zero: self.zero }, wrapped: None }
    }

    /// A fast-iterator entry's positional `getValue()`.
    pub(super) fn value_at(&self, index: i32) -> Result<u32, Fault> {
        self.values.get(index as usize).ok_or(Fault::ArrayIndex(index, self.values.len()))
    }

    pub(super) fn key_at(&self, index: i32) -> Result<i64, Fault> {
        self.keys.get(index as usize).map(|key|key.load(Ordering::Relaxed)).ok_or(Fault::ArrayIndex(index, self.keys.len()))
    }
}

/// `MapIterator`'s position: everything but its wrapped-key list.
#[derive(Clone, Copy)]
pub(super) struct Mark {
    pos: i32,
    last: i32,
    left: i32,
    zero: bool,
}

/// `MapIterator`, detached from the map so callbacks can mutate it between
/// steps exactly as they could the Java table.
pub(super) struct Cursor {
    mark: Mark,
    /// Created on the first wrapped shift; reading it before is Java's NPE.
    /// Only removal through the cursor appends to it.
    wrapped: Option<Vec<i64>>,
}

impl Mark {
    /// The iterator's `pos` (the slot it read last).
    pub(super) fn position(&self) -> i32 {
        self.pos
    }

    /// The iterator's `last`: the slot of the entry last returned, -1 if none.
    pub(super) fn last(&self) -> i32 {
        self.last
    }

    /// The iterator's `c`: entries left, counted down as each `nextEntry` begins.
    pub(super) fn left(&self) -> i32 {
        self.left
    }

    pub(super) fn has_next(&self) -> bool {
        self.left != 0
    }

    fn wrapped_key(&self, wrapped: &Option<Vec<i64>>) -> Result<i64, Fault> {
        let wrapped = wrapped.as_ref().ok_or(Fault::NullPointer)?;
        wrapped.get(self.pos.wrapping_neg().wrapping_sub(1) as usize).copied().ok_or(Fault::IndexOutOfBounds(self.pos.wrapping_neg().wrapping_sub(1), wrapped.len()))
    }

    /// `nextEntry()` over `wrapped`: the entry's table index.
    pub(super) fn next_entry(&mut self, wrapped: &Option<Vec<i64>>, map: &Table) -> Result<i32, Fault> {
        if self.left == 0 {
            return Err(Fault::NoSuchElement);
        }
        self.left = self.left.wrapping_sub(1);
        if self.zero {
            self.zero = false;
            self.last = map.n as i32;
            return Ok(self.last);
        }
        loop {
            self.pos = self.pos.wrapping_sub(1);
            if self.pos < 0 {
                self.last = i32::MIN;
                let key = self.wrapped_key(wrapped)?;
                let mut p = mix(key) & map.mask();
                while key != map.key(p) {
                    p = (p + 1) & map.mask();
                }
                return Ok(p as i32);
            }
            if map.key_at(self.pos)? != 0 {
                self.last = self.pos;
                return Ok(self.pos);
            }
        }
    }
}

impl Cursor {
    pub(super) fn remove_set(&mut self,map:&mut Table)->Result<(),Fault>{
        if self.mark.last==-1{return Err(Fault::IllegalState);}
        if self.mark.last==map.n as i32 {map.zero=false;}
        else if self.mark.pos>=0 {map.shift(self.mark.last as usize,Some(&mut self.wrapped))?;}
        else {let key=self.mark.wrapped_key(&self.wrapped)?;self.mark.last=-1;map.remove(key)?;return Ok(());}
        map.size=map.size.wrapping_sub(1);self.mark.last=-1;Ok(())
    }
    pub(super) fn next_key(&mut self,map:&Table)->Result<i64,Fault>{
        if !self.has_next(){return Err(Fault::NoSuchElement);}
        self.mark.left=self.mark.left.wrapping_sub(1);
        self.select_bulk(map,&map.keys,true).map(|(_,key)|key)
    }
    fn select_bulk(&mut self,map:&Table,keys:&KeyGeneration,set:bool)->Result<(i32,i64),Fault>{
        if self.mark.zero {
            self.mark.zero=false;self.mark.last=map.n as i32;
            let key=if set {keys.get(map.n).ok_or(Fault::ArrayIndex(map.n as i32,keys.len()))?.load(Ordering::Relaxed)}else{0};
            return Ok((self.mark.last,key));
        }
        loop {
            self.mark.pos=self.mark.pos.wrapping_sub(1);
            if self.mark.pos<0 {
                self.mark.last=i32::MIN;
                let key=self.mark.wrapped_key(&self.wrapped)?;
                if set{return Ok((i32::MIN,key));}
                let mut at=mix(key)&map.mask();
                while keys.get(at).ok_or(Fault::ArrayIndex(at as i32,keys.len()))?.load(Ordering::Relaxed)!=key {at=(at+1)&map.mask();}
                return Ok((at as i32,0));
            }
            let key=keys.get(self.mark.pos as usize).ok_or(Fault::ArrayIndex(self.mark.pos,keys.len()))?.load(Ordering::Relaxed);
            if key!=0 {self.mark.last=self.mark.pos;return Ok((self.mark.pos,if set{key}else{0}));}
        }
    }
    pub(super) fn mark(&self) -> Mark {
        self.mark
    }

    pub(super) fn reset(&mut self, mark: Mark) {
        self.mark = mark;
    }

    pub(super) fn wrapped(&self) -> &Option<Vec<i64>> {
        &self.wrapped
    }

    pub(super) fn has_next(&self) -> bool {
        self.mark.has_next()
    }

    /// `nextEntry()`: the entry's table index.
    pub(super) fn next_entry(&mut self, map: &Table) -> Result<i32, Fault> {
        self.mark.next_entry(&self.wrapped, map)
    }

    /// `remove()`: the removed value.
    pub(super) fn remove(&mut self, map: &mut Table) -> Result<u32, Fault> {
        if self.mark.last == -1 {
            return Err(Fault::IllegalState);
        }
        let value;
        if self.mark.last == map.n as i32 {
            map.zero = false;
            value = map.value(map.n);
            if !map.primitive_values { map.values.set(map.n as i32, map.empty_value(), None)?; }
        } else if self.mark.pos >= 0 {
            let last = self.mark.last as usize;
            value = map.values.get(last).ok_or(Fault::ArrayIndex(last as i32, map.values.len()))?;
            map.shift(last, Some(&mut self.wrapped))?;
        } else {
            let key = self.mark.wrapped_key(&self.wrapped)?;
            self.mark.last = -1;
            return map.remove(key);
        }
        map.size = map.size.wrapping_sub(1);
        self.mark.last = -1;
        Ok(value)
    }
}

/// One bulk invocation, independent of any nested invocation on the same
/// cursor. Set iterators capture before the zero callback; map iterators after.
/// Raw map views scan current arrays with their original captured range.
pub(super) struct Bulk {
    mode:i32, // 0 set iterator, 1 map iterator, 2 set forEach, 3 map-view forEach
    phase:u8,
    pos:i32,
    keys:Option<Arc<KeyGeneration>>,
}
impl Bulk {
    pub(super) fn new(mode:i32,table:&Table)->Result<Self,Fault>{
        if !(0..4).contains(&mode){return Err(Fault::IllegalState);}
        Ok(Self{mode,phase:0,pos:0,keys:if mode==0{Some(table.captured_keys())}else{None}})
    }
    pub(super) fn next(&mut self,cursor:&mut Cursor,table:&Table)->Result<Option<(i32,i64)>,Fault>{
        if self.phase==0 {
            self.phase=1;
            if self.mode<2 && cursor.mark.zero {
                return cursor.select_bulk(table,self.keys.as_deref().unwrap_or(&table.keys),self.mode==0).map(Some);
            }
            if self.mode>=2 && table.zero {
                return Ok(Some((table.n as i32,if self.mode==2{table.key(table.n)}else{0})));
            }
        }
        if self.phase==1 {
            if self.mode==1 || self.mode==2 {self.keys=Some(table.captured_keys());}
            self.pos=table.n as i32;self.phase=2;
        }
        if self.mode<2 {
            if !cursor.has_next(){return Ok(None);}
            return cursor.select_bulk(table,self.keys.as_ref().unwrap(),self.mode==0).map(Some);
        }
        while self.pos>0 {
            self.pos-=1;
            let key=if self.mode==2 {
                let keys=self.keys.as_ref().unwrap();
                keys.get(self.pos as usize).ok_or(Fault::ArrayIndex(self.pos,keys.len()))?.load(Ordering::Relaxed)
            }else{table.key_at(self.pos)?};
            if key!=0{return Ok(Some((self.pos,if self.mode==2{key}else{0})));}
        }
        Ok(None)
    }
    pub(super) fn commit(&self,cursor:&mut Cursor){
        if self.mode<2 {cursor.mark.left=cursor.mark.left.wrapping_sub(1);}
    }
}

#[derive(Clone,Copy,Debug,PartialEq,Eq)]
pub(super) struct Range {pub(super) pos:i32,max:i32,c:i32,zero:bool,split:bool}
impl Range {
    pub(super) fn new(table:&Table)->Self{Self{pos:0,max:table.n as i32,c:0,zero:table.zero,split:false}}
    pub(super) fn proposal(&self)->Option<Self>{
        if self.pos>=self.max-1{return None;}
        let half=(self.max-self.pos)>>1;if half<=1{return None;}
        Some(Self{pos:self.pos,max:self.pos+half,c:0,zero:self.zero,split:true})
    }
    pub(super) fn commit_split(&mut self,child:&Self){self.pos=child.max;self.zero=false;self.split=true;}
    pub(super) fn info(&self,table:&Table,kind:i32)->(i64,i64){
        let remaining=i64::from(table.size.wrapping_sub(self.c));
        let estimate=if !self.split{remaining}else{
            let real=table.size.wrapping_sub(i32::from(table.zero));
            let slots=(f64::from(real)/table.n as f64*f64::from(self.max.wrapping_sub(self.pos))) as i64;
            remaining.min(slots+i64::from(self.zero))
        };
        let flags=match kind{0|2=>257,1=>1,3=>256,_=>0}+if self.split{0}else{64};
        (estimate,flags)
    }
    pub(super) fn skip(&mut self,table:&Table,mut count:i64)->Result<i64,Fault>{
        if count<0{return Err(Fault::IllegalState);}if count==0{return Ok(0);}
        let mut skipped=0;
        if self.zero{self.zero=false;skipped+=1;count-=1;}
        let keys=table.captured_keys();
        while self.pos<self.max && count>0{
            let at=self.pos;self.pos=self.pos.wrapping_add(1);
            if keys.get(at as usize).ok_or(Fault::ArrayIndex(at,keys.len()))?.load(Ordering::Relaxed)!=0{skipped+=1;count-=1;}
        }
        // Fastutil 8.5.12 leaves c unchanged on this path.
        Ok(skipped)
    }
}
pub(super) struct RangeBulk {
    all:bool,set:bool,keys:Option<Arc<KeyGeneration>>,done:bool,pending_zero:bool,
}
impl RangeBulk {
    pub(super) fn new(table:&Table,all:bool,set:bool)->Self{
        Self{all,set,keys:if all&&set{Some(table.captured_keys())}else{None},done:false,pending_zero:false}
    }
    pub(super) fn next(&mut self,range:&mut Range,table:&Table)->Result<Option<(i32,i64)>,Fault>{
        if self.done{return Ok(None);}
        if range.zero{
            range.zero=false;self.pending_zero=true;
            if !self.all || !self.set{range.c=range.c.wrapping_add(1);}
            if !self.all{self.done=true;}
            let at=table.n as i32;
            let key=if self.set{
                let keys=self.keys.get_or_insert_with(||table.captured_keys());
                keys.get(at as usize).ok_or(Fault::ArrayIndex(at,keys.len()))?.load(Ordering::Relaxed)
            }else{0};
            return Ok(Some((at,key)));
        }
        let keys=self.keys.get_or_insert_with(||table.captured_keys());
        while range.pos<range.max{
            let at=range.pos;
            let key=keys.get(at as usize).ok_or(Fault::ArrayIndex(at,keys.len()))?.load(Ordering::Relaxed);
            if key!=0{
                self.pending_zero=false;
                if !self.all{range.c=range.c.wrapping_add(1);range.pos=range.pos.wrapping_add(1);self.done=true;}
                return Ok(Some((at,if self.set{key}else{0})));
            }
            range.pos=range.pos.wrapping_add(1);
        }
        self.done=true;Ok(None)
    }
    pub(super) fn commit(&self,range:&mut Range){
        if !self.all{return;}
        if self.pending_zero{if self.set{range.c=range.c.wrapping_add(1);}}
        else{range.c=range.c.wrapping_add(1);range.pos=range.pos.wrapping_add(1);}
    }
}
