use super::*;
use std::{cell::Cell, io::{Read, Cursor}};
struct Chunk { pos: (i32,i32), min: i32, empty: Vec<Cell<bool>> }
struct Bank(Vec<Chunk>);
impl Source for Bank {
    fn position(&self, id: u64) -> (i32,i32) { self.0[id as usize - 1].pos }
    fn sections(&self, id: u64) -> usize { self.0[id as usize - 1].empty.len() }
    fn section_y(&self, id: u64, i: usize) -> i32 { self.0[id as usize - 1].min.wrapping_add(i as i32) }
    fn empty(&self, id: u64, i: usize) -> Result<bool, Fault> { Ok(self.0[id as usize - 1].empty[i].get()) }
}
struct Reader(Cursor<Vec<u8>>);
impl Reader {
    fn byte(&mut self) -> u8 { let mut a=[0];self.0.read_exact(&mut a).unwrap();a[0] }
    fn int(&mut self) -> i32 { let mut a=[0;4];self.0.read_exact(&mut a).unwrap();i32::from_be_bytes(a) }
    fn long(&mut self) -> u64 { let mut a=[0;8];self.0.read_exact(&mut a).unwrap();u64::from_be_bytes(a) }
    fn string(&mut self) -> String { let n=(usize::from(self.byte())<<8)|usize::from(self.byte());let mut a=vec![0;n];self.0.read_exact(&mut a).unwrap();String::from_utf8(a).unwrap() }
}
#[test]
fn all_actual_frozen_slot_empty_iteration_and_callback_prefixes_match() {
    let mut bytes=Vec::new();flate2::read::GzDecoder::new(&include_bytes!("../../../../../test/resources/world/chunk/frozen-client-residency.bin.gz")[..]).read_to_end(&mut bytes).unwrap();
    let mut r=Reader(Cursor::new(bytes));assert_eq!(r.int(),0x43525331);
    assert_eq!(r.string(),"b7885ea3f3e9bbe00b7088afc2fbc615eca9f397731b9d5b9b0171239c867658");
    assert_eq!(r.string(),"2080d2ab49335be229d6d37fa81ab9f2b7f5ddd5b23c3560c9c4740503f7a48a");
    assert!(r.string().contains("Actual Frozen"));
    let n=r.int();let mut bank=Bank(Vec::new());for id in 1..=n {
        assert_eq!(r.int(),id);let pos=(r.int(),r.int());let min=r.int();let count=r.int();
        bank.0.push(Chunk {pos,min,empty:(0..count).map(|_|Cell::new(r.byte()!=0)).collect()});
    }
    let mut world=Residency::new(3).unwrap();let steps=r.int();assert_eq!(steps,600);
    for step in 0..steps {
        let op=r.byte();let id=r.int() as u64+1;let x=r.int();let y=r.int();let z=r.int();let mask=r.long();
        let flag=r.byte()!=0;let fail=r.byte()!=0;let mut events=Vec::new();
        let callback=|event| { events.push(event);if fail {Err(Fault::Callback)}else {Ok(())} };
        let position=bank.position(id);let index=world.index(position.0,position.1);
        let result=match op {
            0=>world.replace(index,if flag{id}else{0},&bank,callback),
            1=>world.drop_chunk(index,id,&bank,callback),
            2=>{for (s,e) in bank.0[id as usize-1].empty.iter().enumerate(){e.set(mask&(1<<s)!=0);}world.refresh(id,&bank,callback)},
            3=>world.section_changed(x,y,z,flag,callback),
            4=>{world.center=(x,z);Ok(())},
            5=>{let radius=y.rem_euclid(5).max(2)+3;
                if radius==world.radius {Ok(())} else {match world.relocated(radius,&bank,callback) {Ok(next)=>{world=next;Ok(())},Err(e)=>Err(e)}}},
            _=>panic!("operation"),
        };
        let exception=r.string();assert_eq!(result,if exception.is_empty(){Ok(())}else{assert_eq!(exception,"callback");Err(Fault::Callback)},"step{step} exception");
        assert_eq!(world.count,r.int(),"step{step} count");let length=r.int();assert_eq!(length as usize,world.slots.len());
        for (s,&id) in world.slots.iter().enumerate(){assert_eq!(id,r.int() as u64,"step{step} slot{s}");}
        let empties=(0..r.int()).map(|_|r.long()).collect::<Vec<_>>();assert_eq!(world.empty.ordered(),empties,"step{step} exact fastutil iteration");
        let recorded=(0..r.int()).map(|_| match r.byte(){0=>Event::Unload(r.long()),1=>Event::NonEmpty(r.long()),_=>panic!("event")}).collect::<Vec<_>>();
        assert_eq!(events,recorded,"step{step} callbacks");assert_eq!(world.in_range(x,z),r.byte()!=0);assert_eq!(world.index(x,z),r.int() as usize);
    }
    assert_eq!(r.0.position() as usize,r.0.get_ref().len());
}
#[test]
fn reentrant_section_edits_are_sampled_after_each_callback_and_failed_cas_unloads() {
    let bank=Bank(vec![Chunk{pos:(0,0),min:0,empty:vec![Cell::new(true),Cell::new(true)]}]);
    let mut world=Residency::new(3).unwrap();world.replace(world.index(0,0),1,&bank,|_|Ok(())).unwrap();
    bank.0[0].empty[0].set(false);bank.0[0].empty[1].set(false);
    let mut events=Vec::new();world.refresh(1,&bank,|e|{events.push(e);bank.0[0].empty[1].set(true);Ok(())}).unwrap();
    assert_eq!(events,[Event::NonEmpty(0)]);assert_eq!(world.empty.ordered(),[1]);
    let other=world.index(1,0);world.drop_chunk(other,1,&bank,|e|{assert_eq!(e,Event::Unload(1));Ok(())}).unwrap();
    assert_eq!(world.count,1);assert_eq!(world.lookup(0,0,&bank),1);
}
#[test]
fn failed_unload_preserves_published_slot_without_new_count_or_empty_admission() {
    let bank=Bank(vec![Chunk{pos:(0,0),min:0,empty:vec![Cell::new(true)]},Chunk{pos:(7,0),min:-4,empty:vec![Cell::new(true)]}]);
    let mut world=Residency::new(3).unwrap();let i=world.index(0,0);world.replace(i,1,&bank,|_|Ok(())).unwrap();
    assert_eq!(world.replace(i,2,&bank,|_|Err(Fault::Callback)),Err(Fault::Callback));
    assert_eq!(world.slots[i],2);assert_eq!(world.count,0);assert!(world.empty.ordered().is_empty());
    world.center=(7,0);assert_eq!(world.lookup(7,0,&bank),2);
}
#[test]
fn long_set_growth_relocation_zero_and_shrink_preserve_membership() {
    let mut set=EmptySet::new();let values=(0..4000u64).map(|i|i.wrapping_mul(0x9e3779b97f4a7c15)).collect::<Vec<_>>();
    for &v in &values {assert!(set.add(v));assert!(!set.add(v));}assert!(set.keys.len()>32);
    for &v in values.iter().rev() {assert!(set.remove(v));assert!(!set.remove(v));}
    assert!(set.ordered().is_empty());assert_eq!(set.keys.len(),32);
}
#[test]
fn native_world_keeps_counter_owners_and_stops_at_each_external_callback() {
    use super::ffi::*;
    use crate::world::level::chunk::{inputs,live,counters};
    use std::sync::{Arc,atomic::Ordering};
    let blocks=Arc::new(live::Owner::load(0,0,&[0],&[],31809,15).unwrap());
    let a=Arc::new(counters::Owner::new(0));let b=Arc::new(counters::Owner::new(0));
    let wa=Arc::downgrade(&a);let wb=Arc::downgrade(&b);
    let pairs=[Arc::as_ptr(&blocks) as usize,Arc::as_ptr(&a) as usize,
        Arc::as_ptr(&blocks) as usize,Arc::as_ptr(&b) as usize];
    unsafe {
        let chunk=inputs::mattmc_chunk_inputs_create(pairs.as_ptr(),2,0);
        let world=mattmc_client_residency_create(3);
        let id=mattmc_client_residency_change(world,0,0,1,0) as u64;
        assert_eq!(mattmc_client_residency_change(world,2,0,0,0),0);
        let mut result=[0u64;2];
        assert_eq!(mattmc_client_residency_scan(world,chunk,0,id,0,0,0,2,0,1,result.as_mut_ptr()),0);
        let snapshot=mattmc_client_residency_snapshot(world,0);
        assert_eq!(mattmc_client_residency_snapshot_value(snapshot,1),2);
        a.set([1,0,0]);b.set([1,0,0]);
        assert_eq!(mattmc_client_residency_scan(world,chunk,0,id,0,0,0,2,0,2,result.as_mut_ptr()),1);
        assert_eq!(result,[0,1]);
        b.set([0,0,0]); // Represents callback mutation between the two native calls.
        assert_eq!(mattmc_client_residency_scan(world,chunk,0,id,0,0,0,2,1,2,result.as_mut_ptr()),0);
        assert_eq!(mattmc_client_residency_snapshot_value(snapshot,1),2); // Independent old reader.
        inputs::mattmc_chunk_inputs_release(chunk);drop(a);drop(b);drop(blocks);
        assert!(wa.upgrade().is_some());assert!(wb.upgrade().is_some());
        assert_eq!(mattmc_client_residency_change(world,1,0,0,0),0);
        assert!(wa.upgrade().is_none());assert!(wb.upgrade().is_none());
        let view=mattmc_client_residency_view(world) as *const std::sync::atomic::AtomicI32;
        assert_eq!((*view).load(Ordering::Acquire),0);
        mattmc_client_residency_release(world);
        assert_eq!(mattmc_client_residency_snapshot_value(snapshot,1),2);
        mattmc_client_residency_snapshot_release(snapshot);
    }
}
#[test]
fn detached_native_table_declines_further_empty_mutation_without_losing_ring_policy() {
    use super::ffi::*;
    unsafe {
        let world=mattmc_client_residency_create(3);
        assert_eq!(mattmc_client_residency_empty(world,0,1),1);
        assert_eq!(mattmc_client_residency_empty(world,7,1),1);
        let snapshot=mattmc_client_residency_snapshot(world,1);
        assert_eq!(mattmc_client_residency_snapshot_value(snapshot,1),2);
        assert_eq!(mattmc_client_residency_snapshot_value(snapshot,2),1);
        assert_eq!(mattmc_client_residency_empty(world,7,0),-1);
        assert!(mattmc_client_residency_snapshot(world,0).is_null());
        assert!(mattmc_client_residency_change(world,0,0,1,0)>0);
        assert_eq!(mattmc_client_residency_change(world,2,0,0,0),0);
        let id=mattmc_client_residency_identity(world,0);assert!(id>0);
        assert_eq!(mattmc_client_residency_change(world,3,0,i32::MIN,i32::MAX),0);
        assert_eq!(mattmc_client_residency_identity(world,0),id);
        mattmc_client_residency_snapshot_release(snapshot);mattmc_client_residency_release(world);
    }
}
