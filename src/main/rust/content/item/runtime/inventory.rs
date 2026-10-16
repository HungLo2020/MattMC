//! Actual player main-inventory state. Equipment and player/world effects stay
//! external; every slot/scalar address is borrowed under its real owner lease.
use super::slots::{BoundaryOwner,Entry,Slots,visible_count};
use std::sync::atomic::{AtomicI32,Ordering};

#[repr(C)]
pub struct Scalars {pub selected:AtomicI32,pub changed:AtomicI32}
#[repr(C)]
pub struct Inventory {pub scalars:Scalars,pub slots:BoundaryOwner<Slots>,pub equipment:std::sync::OnceLock<std::sync::Arc<BoundaryOwner<super::container::Equipment>>>}
impl Inventory {
    pub fn first_facade(&self,facade:i64)->i32 {
        self.slots.lock().unwrap().entries_facade_index(facade)
    }
    pub fn new(empty:Entry)->Self {Self{scalars:Scalars{selected:AtomicI32::new(0),changed:AtomicI32::new(0)},slots:BoundaryOwner::new(Slots::new(36,empty)),equipment:std::sync::OnceLock::new()}}
    pub fn address(&self)->usize {&self.scalars as *const Scalars as usize}
    pub fn free_slot(&self,air:u64)->i32 {
        let slots=self.slots.lock().unwrap();
        for index in 0..36 {if visible_count(&slots.get(index).unwrap().stack,air)==0{return index as i32;}}
        -1
    }
    pub fn main_empty(&self,air:u64)->bool {
        let slots=self.slots.lock().unwrap();(0..36).all(|index|visible_count(&slots.get(index).unwrap().stack,air)==0)
    }
    /// The Java caller already committed selection at its original point, then
    /// checked this main-only index. No new facade or GC binding is introduced.
    pub fn swap_selected(&self,index:usize)->bool {
        if index>=36{return false;}
        let selected=self.scalars.selected.load(Ordering::Relaxed);
        if !(0..9).contains(&selected){return false;}
        let mut slots=self.slots.lock().unwrap();
        let old=slots.get(selected as usize).unwrap().clone();let next=slots.get(index).unwrap().clone();
        slots.set(selected as usize,next);slots.set(index,old);true
    }
}

impl Inventory {
    /// All admitted merge effects target this owner's wrapping change counter.
    /// Empty insertion/foreign equality returns before Java effects or copies.
    pub fn transfer_step(&self,source:&super::stack::Stack,air:u64,previous:i64)->i64 {
        let bits=previous as u64;let mut phase=(bits>>9)&3;let mut index=((bits>>3)&63) as usize;
        let mut changed=bits&2048!=0;
        if bits&7==1 {index+=1;changed=true;}
        let rows=self.slots.lock().unwrap();let mut stats=super::slots::TransferStats::default();
        loop {
            let result=super::slots::transfer_step_inventory(&rows,source,air,phase,index,&mut stats);
            let bits=result as u64;let action=bits&7;
            if action==1 {
                self.scalars.changed.fetch_add(1,Ordering::Relaxed);changed=true;
                phase=(bits>>9)&3;index=((bits>>3)&63) as usize+1;continue;
            }
            return result | if changed {2048}else{0};
        }
    }
}

// Effects:1 damage query;2 original hasRemainingSpaceForItem at index;
// 3 original complete addResource(index);4 damaged copy/clear/place/pop;
// 5 player.hasInfiniteMaterials. No gate/borrow survives any effect.
impl Inventory {
    fn space(&self,index:i32,source:&super::stack::Stack,air:u64)->Option<bool> {
        if !(0..36).contains(&index){return None;}
        let rows=self.slots.lock().unwrap();
        super::slots::inventory_space(&rows.get(index as usize).unwrap().stack,source,air)
    }
    fn resource(&self,index:i32,source:&super::stack::Stack,air:u64)->Option<i32> {
        if !(0..36).contains(&index){return None;}
        let rows=self.slots.lock().unwrap();let target=&rows.get(index as usize).unwrap().stack;
        let amount=visible_count(source,air);
        if visible_count(&target,air)==0{return None;} // Original copied Java facade is required here.
        let cap=super::slots::inventory_capacity(&target)?;let count=visible_count(&target,air);
        let moved=amount.min(cap.wrapping_sub(count));
        if moved==0{return Some(amount);}
        let remaining=amount.wrapping_sub(moved);
        // Source may BE target: preserve grow -> pop -> outer source assignment.
        target.scalars.count.store(count.wrapping_add(moved),Ordering::Relaxed);
        target.scalars.pop_time.store(5,Ordering::Relaxed);Some(remaining)
    }
    pub fn add_run(&self,source:&super::stack::Stack,requested:i32,air:u64,mut effect:impl FnMut(i64,i32)->Result<i32,()>)->Result<bool,()> {
        let damaged=match super::components::damaged(source) { Some(value) => value, None => effect(1,0)?!=0 };
        if damaged {
            let index=if requested == -1 {self.free_slot(air)}else{requested};
            if index>=0 {effect(4,index)?;return Ok(true);}
            if effect(5,0)?!=0 {source.scalars.count.store(0,Ordering::Relaxed);return Ok(true);}
            return Ok(false);
        }
        let last;
        loop {
            let before=visible_count(source,air);
            let index=if requested == -1 {
                let found=self.remaining_slot(Some(source),air,&mut effect)?;
                if found == -1 {self.free_slot(air)}else{found}
            } else {requested};
            let remaining=if requested == -1 && index == -1 {visible_count(source,air)}
                else {match self.resource(index,source,air){Some(value)=>value,None=>effect(3,index)?}};
            source.scalars.count.store(remaining,Ordering::Relaxed);
            if visible_count(source,air)==0 || visible_count(source,air)>=before {last=before;break;}
        }
        if visible_count(source,air)==last && effect(5,0)?!=0 {
            source.scalars.count.store(0,Ordering::Relaxed);return Ok(true);
        }
        Ok(visible_count(source,air)<last)
    }
}

impl Inventory {
    pub fn remaining_slot(&self,source:Option<&super::stack::Stack>,air:u64,effect:&mut impl FnMut(i64,i32)->Result<i32,()>)->Result<i32,()> {
        let selected=self.scalars.selected.load(Ordering::Relaxed);
        let selected_match=match source.and_then(|value|self.space(selected,value,air)){Some(value)=>value,None=>effect(2,selected)?!=0};
        if selected_match {return Ok(self.scalars.selected.load(Ordering::Relaxed));}
        let equipment_match=if let Some(equipment)=self.equipment.get(){
            // Mutable public routing is queried at this original evaluation point.
            // No row/default/equipment guard crosses the Java query.
            let key=effect(11,40)?;
            if key==0{Some(false)}else{
                let rows=equipment.lock().unwrap();
                match rows.get(key.wrapping_sub(1) as usize){
                    Some(super::container::EquipmentValue::Absent)=>Some(false),
                    Some(super::container::EquipmentValue::Stack(entry))=>{
                        if visible_count(&entry.stack,air)==0{Some(false)}else{source.and_then(|source|super::slots::inventory_space(&entry.stack,source,air))}
                    },
                    _=>None, // Present-null and foreign predicates retain the original leaf.
                }
            }
        }else{None};
        let equipment_match=match equipment_match{Some(value)=>value,None=>effect(2,40)?!=0};
        if equipment_match{return Ok(40);}
        let mut index=0;
        while index<36 {
            let step=if let Some(source)=source {
                let rows=self.slots.lock().unwrap();super::slots::inventory_space_scan(&rows,source,air,index)
            }else{super::slots::InventorySpaceScan::Effect(index as i32)};
            match step {
                super::slots::InventorySpaceScan::Found(found)=>return Ok(found),
                super::slots::InventorySpaceScan::Done=>return Ok(-1),
                super::slots::InventorySpaceScan::Effect(next)=>{
                    let matched=match source.and_then(|source|self.space(next,source,air)) {Some(value)=>value,None=>effect(2,next)?!=0};
                    if matched{return Ok(next);}index=next as usize+1;
                }
            }
        }
        Ok(-1)
    }
    fn suitable(&self,air:u64,effect:&mut impl FnMut(i64,i32)->Result<i32,()>)->Result<i32,()> {
        for offset in 0..9 {
            let index=(self.scalars.selected.load(Ordering::Relaxed)+offset)%9;
            let empty={let rows=self.slots.lock().unwrap();visible_count(&rows.get(index as usize).unwrap().stack,air)==0};
            if empty {return Ok(index);}
        }
        for offset in 0..9 {
            let index=(self.scalars.selected.load(Ordering::Relaxed)+offset)%9;
            let target={self.slots.lock().unwrap().get(index as usize).unwrap().stack.clone()};
            let enchanted=match super::components::enchanted(&target) {Some(value)=>value,None=>effect(6,index)?!=0};
            if !enchanted {return Ok(index);}
        }
        Ok(self.scalars.selected.load(Ordering::Relaxed))
    }
    /// Actual slot reads stay in Rust; foreign component/enchantment behavior
    /// runs only after the current slot and component borrows have ended.
    pub fn scan_run(&self,source:Option<&super::stack::Stack>,mode:i32,air:u64,mut effect:impl FnMut(i64,i32)->Result<i32,()>)->Result<i32,()> {
        self.scan_run_effects(source, mode, air, |action,index| effect(action,index).map(i64::from))
    }
    pub fn scan_run_effects(&self,source:Option<&super::stack::Stack>,mode:i32,air:u64,mut effect:impl FnMut(i64,i32)->Result<i64,()>)->Result<i32,()> {
        match mode {
            0=>self.suitable(air,&mut |action,index| effect(action,index).and_then(|value|i32::try_from(value).map_err(|_|()))),
            2=>self.remaining_slot(source,air,&mut |action,index| effect(action,index).and_then(|value|i32::try_from(value).map_err(|_|()))),
            1|3=>{
                for index in 0..36 {
                    let target={self.slots.lock().unwrap().get(index).unwrap().stack.clone()};
                    if visible_count(&target,air)==0 {continue;}
                    let matches=if mode==1 {
                        match source.and_then(|value|super::slots::inventory_same(value,&target,air)) {Some(value)=>value,None=>effect(7,index as i32)?!=0}
                    }else if super::components::roles().is_some() {
                        // Original Item.builtInRegistryHolder executes first. Its VM frame retains
                        // that exact target even if the callback replaces the slot.
                        let captured=effect(12,index as i32)?;
                        let Some(target)=(unsafe{super::ffi::retain_vm_arc::<super::stack::Stack>(captured & !1)}) else{return Err(())};
                        if captured & 1 == 0 {false} else {
                            let usable=match super::components::crafting(&target){Some(value)=>value,None=>effect(13,index as i32)?!=0};
                            if !usable {false} else if let Some(source)=source {
                                if visible_count(source,air)==0 {true}
                                else {match super::slots::inventory_same(source,&target,air){Some(value)=>value,None=>effect(14,index as i32)?!=0}}
                            }else{effect(14,index as i32)?!=0}
                        }
                    }else{effect(8,index as i32)?!=0};
                    if matches{return Ok(index as i32);}
                }
                Ok(-1)
            },
            4=>{
                let selected=self.suitable(air,&mut |action,index| effect(action,index).and_then(|value|i32::try_from(value).map_err(|_|())))?;self.scalars.selected.store(selected,Ordering::Relaxed);
                let occupied={let rows=self.slots.lock().unwrap();visible_count(&rows.get(selected as usize).unwrap().stack,air)!=0};
                if occupied {let free=self.free_slot(air);if free != -1 {effect(9,free)?;}}
                effect(10,self.scalars.selected.load(Ordering::Relaxed))?;Ok(0)
            },
            _=>Err(())
        }
    }
}

impl Inventory {
    /// Complete live visitor; a Java effect receives the captured original
    /// facade token, while only actual Arc leases survive across that effect.
    pub fn visit_run(&self,source:Option<&super::stack::Stack>,mode:i32,limit:i32,air:u64,mut effect:impl FnMut(i64,i32,i64)->Result<i32,()>)->Result<bool,()> {
        if mode==7 {
            let mut index=0;
            while index<effect(27,index,0)? {effect(28,index,0)?;index=index.wrapping_add(1);}
            return Ok(false);
        }
        if mode==8 {
            while effect(29,0,0)?!=0 {effect(30,0,0)?;}
            return Ok(false);
        }
        if !(0..=6).contains(&mode){return Err(());}
        for index in 0..limit {
            let entry=if index<36 {Some(self.slots.lock().unwrap().get(index as usize).unwrap().clone())}else{None};
            if let Some(entry)=&entry {
                if matches!(mode,0|1|2|5|6) && visible_count(&entry.stack,air)==0 {continue;}
                if mode==6 {
                    if let Some(matches)=source.and_then(|source|super::slots::inventory_same(&entry.stack,source,air)) {
                        if matches{return Ok(true);}continue;
                    }
                }
            }
            let facade=entry.as_ref().map_or(0,|entry|entry.facade);
            let matched=effect(20+mode as i64,index,facade)?!=0;
            if mode>=4 && matched{return Ok(true);}
        }
        Ok(false)
    }
}
