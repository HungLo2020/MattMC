use super::*;
use super::super::{biomes, counters, inputs, live};
use std::{io::Read, sync::Arc};
struct Reader(Vec<u8>, usize);
impl Reader {
    fn bytes(&mut self, count: usize) -> &[u8] { let start=self.1;self.1+=count;&self.0[start..self.1] }
    fn int(&mut self) -> i32 { i32::from_be_bytes(self.bytes(4).try_into().unwrap()) }
    fn text(&mut self) -> String { let length=u16::from_be_bytes(self.bytes(2).try_into().unwrap()) as usize;String::from_utf8(self.bytes(length).to_vec()).unwrap() }
}
#[test]
fn actual_frozen_change_hashes_cover_cached_fields_raw_edits_and_mutable_emitters() {
    let mut bytes=Vec::new();flate2::read::GzDecoder::new(include_bytes!("../../../../../../test/resources/world/chunk/frozen-dh-chunk-hash.bin.gz").as_slice()).read_to_end(&mut bytes).unwrap();
    let mut r=Reader(bytes,0);assert_eq!(r.int(),0x44484831);assert_eq!(r.int(),15);
    for _ in 0..15 { assert!(!r.text().is_empty());assert_eq!(r.text().len(),64); }
    assert_eq!(r.text(),"ee865336a6a8cc6d801dada6a0b54da98564a6ed6d216df3cefb4666f7fc187b");
    assert!(r.text().contains("Actual Frozen getBlockBiomeHashCode"));
    let mut biome_facts=Vec::new();for _ in 0..r.int() { let text=r.text();let hash=r.int();biome_facts.push((text,hash)); }
    let mut state_facts=Vec::new();for _ in 0..r.int() { let text=r.text();let hash=r.int();state_facts.push((text,hash)); }
    let domain=Domain::new(state_facts.len(),biome_facts.len()).unwrap();
    for (biome,table) in [(false,&state_facts),(true,&biome_facts)] {
        for (id,(text,expected)) in table.iter().enumerate() {
            assert!(domain.register(biome,id,&text.encode_utf16().collect::<Vec<_>>()));
            let facts=domain.0.read().unwrap();assert_eq!(if biome {facts.biomes[id]}else{facts.states[id]},Some(*expected));
        }
    }
    assert_eq!(r.int(),96);
    for _ in 0..96 {
        let name=r.text();let min=r.int();let height=r.int() as usize;let low=r.int();let high=r.int();let count=height/16;
        let empty:Vec<_>=(0..count).map(|_|r.bytes(1)[0]!=0).collect();
        let mut blocks=Vec::new();
        for _ in 0..count {
            let mut words=vec![0u64;1024];for i in 0..4096 {words[i/4]|=(r.int() as u64)<<((i%4)*15);}
            blocks.push(Arc::new(live::Owner::load(15,9,&[],&words,state_facts.len() as u32,15).unwrap()));
        }
        let mut biome_owners=Vec::new();
        for _ in 0..count {
            let mut words=vec![0u64;8];for i in 0..64 {words[i/9]|=(r.int() as u64)<<((i%9)*7);}
            biome_owners.push(Arc::new(biomes::Owner::load(7,4,&[],&words,biome_facts.len() as u32,7).unwrap()));
        }
        let mut heights=Heightmaps{min:low,max:high,solid:[0;256],blocking:[0;256]};
        for i in 0..256 {heights.solid[i]=r.int();heights.blocking[i]=r.int();}
        let positions:Vec<_>=(0..r.int()).map(|_|Position{x:r.int(),y:r.int(),z:r.int()}).collect();let expected=r.int();
        let counts:Vec<_>=empty.iter().map(|&e|Arc::new(counters::Owner::new(if e {0}else{4096}))).collect();
        let pairs:Vec<_>=blocks.iter().zip(&counts).flat_map(|(b,c)|[Arc::as_ptr(b) as usize,Arc::as_ptr(c) as usize]).collect();
        let biomes:Vec<_>=biome_owners.iter().map(|b|Arc::as_ptr(b) as usize).collect();
        unsafe {
            let base=inputs::mattmc_chunk_inputs_create(pairs.as_ptr(),count as i32,min);
            let chunk=inputs::mattmc_chunk_inputs_with_biomes(base,biomes.as_ptr());assert!(!chunk.is_null());inputs::mattmc_chunk_inputs_release(base);
            let sampled=ffi::mattmc_dh_hash_sample(&domain,chunk,&heights);assert_eq!(sampled>>32,1,"{name}");
            let result=ffi::mattmc_dh_hash_finish(&domain,chunk,std::ptr::null(),positions.as_ptr(),positions.len() as i32,sampled as i32);
            assert_eq!(result>>32,1,"{name}");assert_eq!(result as i32,expected,"{name}");
            inputs::mattmc_chunk_inputs_release(chunk);
        }
    }
    assert_eq!(r.1,r.0.len());
}
#[test]
fn utf16_identity_and_missing_facts_are_explicit() {
    let d=Domain::new(2,1).unwrap();assert!(d.register(false,0,&[]));
    assert_eq!(d.0.read().unwrap().states[0],Some(31));
    // Includes an unpaired surrogate; Rust must not normalize or decode it.
    assert!(d.register(false,1,&[0xd800,0x0061]));
    assert_eq!(d.0.read().unwrap().states[1],Some(0xd800i32*31+0x61+31));
    assert!(!d.register(true,1,&[]));assert!(Domain::new(65536,1).is_none());
    let blocks=live::Owner::load(0,0,&[1],&[],2,9).unwrap();let biome=biomes::Owner::load(0,0,&[0],&[],1,4).unwrap();let counts=counters::Owner::new(4096);
    let sections=[(&blocks,&biome)];let counts=[&counts];let input=Sections::Columns(&sections,Some(&counts));let h=Heightmaps{min:0,max:16,solid:[0;256],blocking:[0;256]};
    assert!(sample(input,0,&h,&d.0.read().unwrap()).is_none());assert!(d.register(true,0,&[]));
    assert!(sample(input,0,&h,&d.0.read().unwrap()).is_some());
}
#[test]
fn native_emitter_input_observes_growth_current_counts_and_retained_primary_lifetimes() {
    use crate::world::level::lighting::dh::sources::Sources;
    let domain=Domain::new(32,16).unwrap();for i in 0..32 {domain.register(false,i,&[i as u16]);}for i in 0..16 {domain.register(true,i,&[i as u16]);}
    let block=Arc::new(live::Owner::load(0,0,&[1],&[],32,9).unwrap());let count=Arc::new(counters::Owner::new(4096));
    let biome=Arc::new(biomes::Owner::load(0,0,&[0],&[],16,4).unwrap());let weak=Arc::downgrade(&block);
    let pairs=[Arc::as_ptr(&block) as usize,Arc::as_ptr(&count) as usize];let biome_p=[Arc::as_ptr(&biome) as usize];
    let heights=Heightmaps{min:-64,max:-48,solid:[-63;256],blocking:[-62;256]};
    let sources=Sources{min_y:-64,positions:Arc::from([0,256])};
    unsafe {
        let base=inputs::mattmc_chunk_inputs_create(pairs.as_ptr(),1,-64);let chunk=inputs::mattmc_chunk_inputs_with_biomes(base,biome_p.as_ptr());inputs::mattmc_chunk_inputs_release(base);
        let before=ffi::mattmc_dh_hash_sample(&domain,chunk,&heights);assert_eq!(before>>32,1);
        for i in 0..32 {block.write(i,i as u32).unwrap();}for i in 0..16 {biome.write(i,i as u32).unwrap();}
        let after=ffi::mattmc_dh_hash_sample(&domain,chunk,&heights);assert_ne!(before,after);
        let native=ffi::mattmc_dh_hash_finish(&domain,chunk,&sources,std::ptr::null(),0,after as i32);
        let positions=[Position{x:0,y:-64,z:0},Position{x:0,y:-63,z:0}];
        assert_eq!(native,ffi::mattmc_dh_hash_finish(&domain,chunk,std::ptr::null(),positions.as_ptr(),2,after as i32));
        count.set([0,0,0]);assert_ne!(native,ffi::mattmc_dh_hash_finish(&domain,chunk,&sources,std::ptr::null(),0,after as i32));
        let invalid=[Position{x:0,y:-65,z:0}];assert_eq!(ffi::mattmc_dh_hash_finish(&domain,chunk,std::ptr::null(),invalid.as_ptr(),1,0),0);
        let invalid=[Position{x:0,y:-46,z:0}];assert_eq!(ffi::mattmc_dh_hash_finish(&domain,chunk,std::ptr::null(),invalid.as_ptr(),1,0),0);
        drop(block);drop(biome);drop(count);assert!(weak.upgrade().is_some());inputs::mattmc_chunk_inputs_release(chunk);assert!(weak.upgrade().is_none());
    }
}
