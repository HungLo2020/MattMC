use super::*;

#[test]
fn bindings_retain_live_primary_owners_across_growth_and_release() {
    let blocks = Arc::new(live::Owner::load(0, 0, &[0], &[], 31809, 15).unwrap());
    let counts = Arc::new(counters::Owner::new(0));
    let weak_blocks = Arc::downgrade(&blocks);
    let weak_counts = Arc::downgrade(&counts);
    let pairs = [Arc::as_ptr(&blocks) as usize, Arc::as_ptr(&counts) as usize];
    let chunk = unsafe { mattmc_chunk_inputs_create(pairs.as_ptr(), 1, -64) };
    assert!(!chunk.is_null());
    drop(blocks); drop(counts);
    let sections = Sections::Retained(unsafe { &*chunk });
    for i in 0..300 { sections.block(0).write(i, (i + 1) as u32).unwrap(); }
    sections.counter(0).unwrap().set([300, -1, 17]);
    sections.block(0).with_state_reader(|r| {
        assert_eq!(r.get(299), 300); assert_eq!(r.get(4095), 0);
    });
    assert_eq!(sections.counter(0).unwrap().values(), [300, -1, 17]);
    assert!(weak_blocks.upgrade().is_some()); assert!(weak_counts.upgrade().is_some());
    unsafe { mattmc_chunk_inputs_release(chunk) };
    assert!(weak_blocks.upgrade().is_none()); assert!(weak_counts.upgrade().is_none());
}

#[test]
fn shared_blocks_keep_section_local_counters_and_invalid_inputs_do_not_retain() {
    let blocks = Arc::new(live::Owner::load(0, 0, &[0], &[], 31809, 15).unwrap());
    let a = Arc::new(counters::Owner::new(0)); let b = Arc::new(counters::Owner::new(4096));
    let p = Arc::as_ptr(&blocks) as usize;
    let pairs = [p, Arc::as_ptr(&a) as usize, p, Arc::as_ptr(&b) as usize];
    assert!(unsafe { mattmc_chunk_inputs_create(pairs.as_ptr(), 2, 1) }.is_null());
    let invalid = [p, Arc::as_ptr(&a) as usize, p, 0];
    assert!(unsafe { mattmc_chunk_inputs_create(invalid.as_ptr(), 2, 0) }.is_null());
    assert_eq!(Arc::strong_count(&blocks), 1); assert_eq!(Arc::strong_count(&a), 1);
    let chunk = unsafe { mattmc_chunk_inputs_create(pairs.as_ptr(), 2, 0) };
    assert!(!chunk.is_null());
    let sections = Sections::Retained(unsafe { &*chunk });
    assert_eq!(sections.counter(0).unwrap().values()[0], 0);
    assert_eq!(sections.counter(1).unwrap().values()[0], 4096);
    assert!(std::ptr::eq(sections.block(0), sections.block(1)));
    unsafe { mattmc_chunk_inputs_release(chunk) };
    assert_eq!(Arc::strong_count(&blocks), 1); assert_eq!(Arc::strong_count(&a), 1);
}

#[test]
fn column_bindings_retain_mutable_biomes_without_pin_to_an_old_generation() {
    let blocks = Arc::new(live::Owner::load(0, 0, &[0], &[], 31809, 15).unwrap());
    let counts = Arc::new(counters::Owner::new(0));
    let biome = Arc::new(biomes::Owner::load(0, 0, &[0], &[], 16, 4).unwrap());
    let weak = Arc::downgrade(&biome);
    let pairs = [Arc::as_ptr(&blocks) as usize, Arc::as_ptr(&counts) as usize];
    let base = unsafe { mattmc_chunk_inputs_create(pairs.as_ptr(), 1, 0) };
    let owners = [Arc::as_ptr(&biome) as usize];
    let columns = unsafe { mattmc_chunk_inputs_with_biomes(base, owners.as_ptr()) };
    assert!(!columns.is_null());
    unsafe { mattmc_chunk_inputs_release(base) };
    drop(blocks); drop(counts); drop(biome);
    let sections = Sections::Retained(unsafe { &*columns });
    for i in 0..16 { sections.biome(0).unwrap().write(i, i as u32).unwrap(); }
    assert_eq!(sections.biome(0).unwrap().snapshot().1[15], 15);
    assert_eq!(sections.counter(0).unwrap().values()[0], 0);
    unsafe { mattmc_chunk_inputs_release(columns) };
    assert!(weak.upgrade().is_none());
}

#[test]
fn biome_binding_is_shared_by_column_inputs_and_releases_only_after_every_reader() {
    let blocks=Arc::new(live::Owner::load(0,0,&[0],&[],31809,15).unwrap());
    let counts=Arc::new(counters::Owner::new(0));
    let biome=Arc::new(biomes::Owner::load(0,0,&[0],&[],16,4).unwrap());
    let weak=Arc::downgrade(&biome);
    let pairs=[Arc::as_ptr(&blocks) as usize,Arc::as_ptr(&counts) as usize];
    let owners=[Arc::as_ptr(&biome) as usize];
    unsafe {
        let base=mattmc_chunk_inputs_create(pairs.as_ptr(),1,0);
        let input=mattmc_chunk_biome_inputs_create(owners.as_ptr(),1);
        let columns=mattmc_chunk_inputs_with_biome_inputs(base,input);
        assert!(!columns.is_null());
        assert_eq!(Arc::as_ptr((&*columns).biomes.as_ref().unwrap()),input);
        let empty=mattmc_chunk_biome_inputs_create(owners.as_ptr(),0);assert!(!empty.is_null());
        assert!(mattmc_chunk_inputs_with_biome_inputs(base,empty).is_null());
        mattmc_chunk_biome_inputs_release(empty);
        mattmc_chunk_inputs_release(base);mattmc_chunk_biome_inputs_release(input);
        drop(blocks);drop(counts);drop(biome);
        let source=Sections::Retained(&*columns);
        for row in 0..16 {source.biome(0).unwrap().write(row,row as u32).unwrap();}
        assert_eq!(source.biome(0).unwrap().snapshot().1[15],15);
        assert!(weak.upgrade().is_some());mattmc_chunk_inputs_release(columns);assert!(weak.upgrade().is_none());
    }
}
