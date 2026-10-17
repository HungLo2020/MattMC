use super::*;

#[test]
fn layout_growth_snapshot_and_capacity() {
    assert_eq!(std::mem::size_of::<Particle>(), 72);
    assert_eq!(std::mem::offset_of!(Buffer, count), 12);
    let mut source=Buffer::new(); assert!(source.grow(1));
    source.slots[0]=Particle { size: -0.5, center: [1.,2.,3.], ..EMPTY }; source.count=1;
    unsafe {
        let snapshot=mattmc_particle_buffer_snapshot(&source);
        assert!(!snapshot.is_null());
        assert!(source.grow(LIMIT)); source.slots[0].center[0]=99.;
        assert_eq!((&*snapshot).active().unwrap()[0].center[0],1.);
        assert!(!source.grow(LIMIT+1));
        mattmc_particle_buffer_release(snapshot);
    }
}

#[test]
fn layer_validation_and_order_encoding_are_transactional() {
    let mut source=Buffer::new(); assert!(source.grow(2)); source.count=2;
    source.slots[0].size=-1.; source.slots[0].uv_bounds=[1.,0.,0.,1.];
    let mut frame=Buffer::new();
    unsafe {
        assert_eq!(mattmc_particle_buffer_append_layer(&mut frame,&source,17,4,2),2);
        source.slots[1].rotation=[0.;4];
        assert_eq!(mattmc_particle_buffer_append_layer(&mut frame,&source,17,4,2),-2);
        assert_eq!(frame.count,2);
        let mut output=[EMPTY;2]; let prefix=[0,1,1,2];
        assert_eq!(mattmc_particle_buffer_encode(&frame,prefix.as_ptr(),4,output.as_mut_ptr()),1);
        assert_eq!(output[0].material_index,2); assert_eq!(output[0].size,-1.);
        assert_eq!(output[0].texture_id,17); assert_eq!(output[0].surface_kind,4);
        assert_eq!(output[0].uv_bounds,[1.,0.,0.,1.]);
        assert_eq!(frame.slots[0].material_index,2);
        frame.slots[1].material_index=1; let before=output;
        assert_eq!(mattmc_particle_buffer_encode(&frame,prefix.as_ptr(),4,output.as_mut_ptr()),0);
        assert_eq!(output,before);
    }
}
