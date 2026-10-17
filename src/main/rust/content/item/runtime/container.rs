//! Canonical stock container state. Java enum keys/facades are identity anchors;
//! absence, present-null and a retained actual stack are distinct native values.
use super::slots::Entry;

#[derive(Clone)]
pub enum EquipmentValue {Absent,Null,Stack(Entry)}
impl EquipmentValue {
    pub fn token(&self)->i64 {match self{Self::Absent=>-1,Self::Null=>0,Self::Stack(entry)=>entry.facade}}
}
pub struct Equipment {entries:Vec<EquipmentValue>}
impl Equipment {
    pub fn new(size:usize)->Self {Self{entries:vec![EquipmentValue::Absent;size]}}
    pub fn get(&self,index:usize)->Option<&EquipmentValue>{self.entries.get(index)}
    pub fn set(&mut self,index:usize,value:EquipmentValue)->Option<EquipmentValue>{self.entries.get_mut(index).map(|entry|std::mem::replace(entry,value))}
    pub fn size(&self)->usize{self.entries.iter().filter(|entry|!matches!(entry,EquipmentValue::Absent)).count()}
    pub fn next(&self,start:usize)->i64{(start..self.entries.len()).find(|&index|!matches!(self.entries[index],EquipmentValue::Absent)).map_or(-1,|index|index as i64)}
    pub fn references(&self,token:i64)->bool{token>0&&self.entries.iter().any(|entry|entry.token()==token)}
    pub fn clear_keys(&mut self){self.entries.fill(EquipmentValue::Absent);}
    pub fn clear_values(&mut self,empty:Entry){for entry in &mut self.entries{if !matches!(entry,EquipmentValue::Absent){*entry=EquipmentValue::Stack(empty.clone());}}}
}

use super::slots::{BoundaryOwner,Slots};
use std::sync::{Arc,atomic::{AtomicI64,Ordering}};
/// The current backing is selected here. Token identifies only its Java facade;
/// the retained actual native rows are the storage consumed by native callers.
#[derive(Clone)]
pub enum Rows {Separate(Arc<BoundaryOwner<Slots>>),Inventory(Arc<super::inventory::Inventory>)}
impl std::ops::Deref for Rows {
    type Target=BoundaryOwner<Slots>;
    fn deref(&self)->&Self::Target{match self{Self::Separate(owner)=>owner.as_ref(),Self::Inventory(owner)=>&owner.slots}}
}
#[repr(C)]
pub struct Container {token:AtomicI64,pub slots:Option<Rows>}
impl Container {
    pub fn new(token:i64,slots:Option<Rows>)->Self{Self{token:AtomicI64::new(token),slots}}
    pub fn address(&self)->usize{&self.token as *const AtomicI64 as usize}
    pub fn token(&self)->i64{self.token.load(Ordering::Relaxed)}
    pub fn replace(&mut self,token:i64,slots:Option<Rows>){self.slots=slots;self.token.store(token,Ordering::Release);}
}
