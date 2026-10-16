//! Actual private slot binding. Java keeps public UI identity/layout and effects.
use std::sync::Arc;
use super::{inventory::Inventory,container::Container,slots::BoundaryOwner};
pub enum SlotContainer {Inventory(Arc<Inventory>),Container(Arc<BoundaryOwner<Container>>)}
#[repr(C)]
pub struct SlotLink {pub index:i32,pub container:SlotContainer}
impl SlotLink {
    pub fn new(inventory:Arc<Inventory>,index:i32)->Option<Self>{(0..36).contains(&index).then_some(Self{index,container:SlotContainer::Inventory(inventory)})}
    pub fn for_container(container:Arc<BoundaryOwner<Container>>,index:i32)->Option<Self>{(0..27).contains(&index).then_some(Self{index,container:SlotContainer::Container(container)})}
    pub fn address(&self)->usize{self as *const Self as usize}
    /// Caller pins this allocation and holds its exact canonical inventory gate.
    /// No allocation, locking, registry lookup or callback is permitted here.
    pub unsafe fn read_admitted(&self,owner:usize,air:u64,presence:bool)->i64 {
        let SlotContainer::Inventory(inventory)=&self.container else{return -1};
        if inventory.slots.address()!=owner{return -1;}
        let rows=inventory.slots.admitted();let Some(entry)=rows.get(self.index as usize) else{return -1};
        if presence{i64::from(super::slots::visible_count(&entry.stack,air)!=0)}else{entry.facade}
    }
    /// Regular operation: both guards end before the Java caller resolves a
    /// facade or executes a virtual/loot/equipment/world callback.
    pub fn read(&self,air:u64,presence:bool)->i64 {
        if let SlotContainer::Inventory(inventory)=&self.container {
            let rows=inventory.slots.lock().unwrap();let Some(entry)=rows.get(self.index as usize) else{return -1};
            return if presence{i64::from(super::slots::visible_count(&entry.stack,air)!=0)}else{entry.facade};
        }
        let SlotContainer::Container(container)=&self.container else{return -1};
        let current=container.lock().unwrap();let Some(rows)=&current.slots else{return -1};
        let rows=rows.lock().unwrap();let Some(entry)=rows.get(self.index as usize) else{return -1};
        if presence{i64::from(super::slots::visible_count(&entry.stack,air)!=0)}else{entry.facade}
    }
}

/// Actual menu roster and carried stack. Facade tokens resolve Java identities;
/// links and entries retain the actual source/container/stack allocations.
#[derive(Clone)]
pub struct MenuSlot {pub facade:i64,pub link:Option<Arc<SlotLink>>}
#[repr(C)]
pub struct Header {
    pub version:std::sync::atomic::AtomicI64,
    pub size:std::sync::atomic::AtomicI32,
    reserved:i32,
    pub carried:std::sync::atomic::AtomicI64,
}
#[repr(C)]
pub struct Menu {pub header:Header,rows:Vec<MenuSlot>,carried:Option<super::slots::Entry>,pub chest_rows:std::sync::atomic::AtomicI32,pub kind:i32}
impl Menu {
    pub fn new(carried:Option<super::slots::Entry>)->Self {
        let token=carried.as_ref().map_or(0,|entry|entry.facade);
        Self{header:Header{version:std::sync::atomic::AtomicI64::new(0),size:std::sync::atomic::AtomicI32::new(0),reserved:0,carried:std::sync::atomic::AtomicI64::new(token)},rows:Vec::new(),carried,chest_rows:std::sync::atomic::AtomicI32::new(0),kind:1}
    }
    pub fn address(&self)->usize{&self.header as *const Header as usize}
    pub fn get(&self,index:usize)->Option<&MenuSlot>{self.rows.get(index)}
    pub fn references(&self,facade:i64)->bool{self.rows.iter().any(|slot|slot.facade==facade)}
    pub fn set(&mut self,index:usize,slot:MenuSlot)->Option<MenuSlot>{self.rows.get_mut(index).map(|old|std::mem::replace(old,slot))}
    pub fn insert(&mut self,index:usize,slot:MenuSlot)->bool{
        if index>self.rows.len()||self.rows.len()>=i32::MAX as usize{return false;}
        self.rows.insert(index,slot);self.header.size.store(self.rows.len() as i32,std::sync::atomic::Ordering::Release);true
    }
    pub fn remove(&mut self,index:usize)->Option<MenuSlot>{
        if index>=self.rows.len(){return None;}
        let old=self.rows.remove(index);self.header.size.store(self.rows.len() as i32,std::sync::atomic::Ordering::Release);Some(old)
    }
    pub fn set_carried(&mut self,entry:Option<super::slots::Entry>){
        let token=entry.as_ref().map_or(0,|entry|entry.facade);self.carried=entry;self.header.carried.store(token,std::sync::atomic::Ordering::Release);
    }
    pub fn carried(&self)->Option<&super::slots::Entry>{self.carried.as_ref()}
    /// Empty exact-source guard consumes actual endpoints, without constructing
    /// an effect frame or resolving a Java facade. Unknown/foreign sources stay
    /// on the original effect path. Caller supplies only a currently loot-clear
    /// exact chest; no Java loot state is cached here.
    pub fn empty_source(&self,index:i32,air:u64,clear_chest:Option<&Arc<BoundaryOwner<Container>>>)->bool {
        if index<0{return false;}
        let Some(slot)=self.rows.get(index as usize) else{return false};let Some(link)=&slot.link else{return false};
        match &link.container {
            SlotContainer::Inventory(inventory)=>{let rows=inventory.slots.lock().unwrap();rows.get(link.index as usize).is_some_and(|entry|super::slots::visible_count(&entry.stack,air)==0)},
            SlotContainer::Container(container)=>{
                if !clear_chest.is_some_and(|expected|Arc::ptr_eq(expected,container)){return false;}
                let current=container.lock().unwrap();let Some(rows)=&current.slots else{return false};let rows=rows.lock().unwrap();
                rows.get(link.index as usize).is_some_and(|entry|super::slots::visible_count(&entry.stack,air)==0)
            }
        }
    }
    pub fn matches_stock(&self,chest:&Arc<BoundaryOwner<Container>>,inventory:Option<&Arc<Inventory>>,reverse:bool)->bool {
        if (reverse&&self.rows.len()!=63)||(!reverse&&self.rows.len()<27){return false;}
        for index in 0..if reverse{63}else{27} {
            let Some(link)=&self.rows[index].link else{return false};
            if index<27 {
                let SlotContainer::Container(actual)=&link.container else{return false};
                if !Arc::ptr_eq(actual,chest)||link.index!=index as i32{return false;}
            }else{
                let (SlotContainer::Inventory(actual),Some(inventory))=(&link.container,inventory) else{return false};
                let expected=if index<54{index-18}else{index-54};
                if !Arc::ptr_eq(actual,inventory)||link.index!=expected as i32{return false;}
            }
        }
        true
    }

    /// Borrow only actual retained endpoints. Every exclusion attempt is bounded;
    /// unknown/contended sources resume original Java behavior. No allocation,
    /// handle lookup, blocking lock, Arc clone or foreign callback occurs here.
    pub fn try_empty_source(&self,index:i32,air:u64,clear_chest:usize)->bool {
        if index<0{return false;}
        let Some(link)=self.rows.get(index as usize).and_then(|slot|slot.link.as_ref()) else{return false};
        match &link.container {
            SlotContainer::Inventory(inventory)=>{
                let Some(rows)=inventory.slots.try_lock() else{return false};
                rows.get(link.index as usize).is_some_and(|entry|super::slots::visible_count(&entry.stack,air)==0)
            }
            SlotContainer::Container(container)=>{
                if clear_chest!=container.address(){return false;}
                let Some(current)=container.try_lock() else{return false};let Some(rows)=&current.slots else{return false};
                match rows {
                    super::container::Rows::Separate(owner)=>{let Some(rows)=owner.try_lock() else{return false};rows.get(link.index as usize).is_some_and(|entry|super::slots::visible_count(&entry.stack,air)==0)}
                    super::container::Rows::Inventory(owner)=>{let Some(rows)=owner.slots.try_lock() else{return false};rows.get(link.index as usize).is_some_and(|entry|super::slots::visible_count(&entry.stack,air)==0)}
                }
            }
        }
    }

}

/// Synchronous original effects. Every method runs with all native guards ended.
/// The host pins captured Java facades before returning their actual native Arcs.
pub struct Selected {pub absent:bool,pub link:Option<Arc<SlotLink>>}
pub trait QuickMoveEffects {
    fn select(&mut self,index:i32)->Result<Selected,()>;
    fn has_item(&mut self)->Result<bool,()>;
    fn capture_copy(&mut self)->Result<(Arc<super::stack::Stack>,Arc<super::stack::Stack>),()>;
    fn equipment(&mut self)->Result<(),()>;
    fn equipment_route(&mut self)->Result<(i32,i32),()>;
    fn target_has_item(&mut self,index:i32)->Result<bool,()>;
    fn transfer(&mut self,start:i32,end:i32,reverse:bool)->Result<bool,()>;
    fn quick_craft(&mut self)->Result<(),()>;
    fn cleanup(&mut self,empty:bool,inventory:bool)->Result<(),()>;
    fn take(&mut self)->Result<(),()>;
    fn drop_leftover(&mut self)->Result<(),()>;
    fn finish(&mut self,success:bool)->Result<(),()>;
    fn initial(&mut self,index:i32)->Result<bool,()>;
    fn initial_item(&mut self)->Result<Arc<super::stack::Stack>,()>;
}
fn item_of(stack:&super::stack::Stack,air:u64)->u64 {
    if super::slots::visible_count(stack,air)==0{air}else{stack.scalars.item}
}
/// Complete direct action, retaining actual owners and the original effect prefix.
/// kind1=chest,kind2=inventory. Chest cutoff is the original immutable row count.
pub fn quick_move<E:QuickMoveEffects>(menu:&BoundaryOwner<Menu>,kind:i32,chest_rows:i32,index:i32,air:u64,effects:&mut E)->Result<Option<Arc<super::stack::Stack>>,()> {
    let selected=effects.select(index)?;
    if kind==1&&selected.absent{effects.finish(false)?;return Ok(None);}
    let native=selected.link.as_ref().map_or(-1,|link|link.read(air,true));
    let has=if native<0{effects.has_item()?}else{native!=0};
    if !has{effects.finish(false)?;return Ok(None);}
    let (source,snapshot)=effects.capture_copy()?;
    if kind==2{effects.equipment()?;}
    let (start,end,reverse)=if kind==1 {
        let cutoff=chest_rows.wrapping_mul(9);
        if index<cutoff{let end=menu.lock().unwrap().header.size.load(std::sync::atomic::Ordering::Relaxed);(cutoff,end,true)}else{(0,cutoff,false)}
    }else if index==0{(9,45,true)}else if (1..9).contains(&index){(9,45,false)}else{
        let (equipment,position)=effects.equipment_route()?;
        if equipment==1&&!effects.target_has_item(8i32.wrapping_sub(position))?{let start=8i32.wrapping_sub(position);(start,start.wrapping_add(1),false)}
        else if equipment==2&&!effects.target_has_item(45)?{(45,46,false)}
        else if (9..36).contains(&index){(36,45,false)}
        else if (36..45).contains(&index){(9,36,false)}else{(9,45,false)}
    };
    if !effects.transfer(start,end,reverse)?{effects.finish(false)?;return Ok(None);}
    if kind==2&&index==0{effects.quick_craft()?;}
    effects.cleanup(super::slots::visible_count(&source,air)==0,kind==2)?;
    if kind==2&&super::slots::visible_count(&source,air)==super::slots::visible_count(&snapshot,air){effects.finish(false)?;return Ok(None);}
    if kind==2{effects.take()?;if index==0{effects.drop_leftover()?;}}
    effects.finish(true)?;Ok(Some(snapshot))
}
/// Initial slot stays captured in the host while every direct call selects the
/// then-current roster. Native guards never cross source/slot/player callbacks.
pub fn repeat_quick_move<E:QuickMoveEffects>(menu:&BoundaryOwner<Menu>,kind:i32,chest_rows:i32,index:i32,air:u64,effects:&mut E)->Result<(),()> {
    if !effects.initial(index)?{return Ok(());}
    loop {
        let Some(snapshot)=quick_move(menu,kind,chest_rows,index,air,effects)? else{return Ok(())};
        if super::slots::visible_count(&snapshot,air)==0{return Ok(());}
        let against=effects.initial_item()?;
        if item_of(&against,air)!=item_of(&snapshot,air){return Ok(());}
    }
}

impl SlotLink {
    /// Actual retained link and nested owners only; one gate attempt per level.
    pub fn try_read(&self,air:u64,presence:bool)->i64 {
        fn value(entry:Option<&super::slots::Entry>,air:u64,presence:bool)->i64 {
            entry.map_or(-1,|entry|if presence{i64::from(super::slots::visible_count(&entry.stack,air)!=0)}else{entry.facade})
        }
        match &self.container {
            SlotContainer::Inventory(owner)=>{let Some(rows)=owner.slots.try_lock() else{return -1};value(rows.get(self.index as usize),air,presence)}
            SlotContainer::Container(owner)=>{
                let Some(current)=owner.try_lock() else{return -1};let Some(rows)=&current.slots else{return -1};
                match rows {
                    super::container::Rows::Separate(owner)=>{let Some(rows)=owner.try_lock() else{return -1};value(rows.get(self.index as usize),air,presence)}
                    super::container::Rows::Inventory(owner)=>{let Some(rows)=owner.slots.try_lock() else{return -1};value(rows.get(self.index as usize),air,presence)}
                }
            }
        }
    }
}
