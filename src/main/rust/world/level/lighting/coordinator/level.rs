//! Retained actual block/sky bindings consumed by direct and typed task producers.
use super::ffi::PropagationInput;
use super::super::propagation::work::LevelProjection;
use super::super::maps::ffi::Owner as LightMap;
use std::sync::atomic::Ordering;
use crate::world::level::lighting::task_batch::{LevelToken,FrameToken};
use std::{ffi::c_void, panic::{catch_unwind, AssertUnwindSafe}, sync::Arc};

#[repr(C)]
struct Level {
    token: LevelToken,
    block: Option<PropagationInput>,
    sky: Option<PropagationInput>,
    projection:Arc<LevelProjection>,
}

unsafe extern "C" fn retain(token: *const LevelToken) { Arc::increment_strong_count(token.cast::<Level>()); }
unsafe extern "C" fn release(token: *const LevelToken) { drop(Arc::from_raw(token.cast::<Level>())); }
unsafe extern "C" fn channel(token:*const LevelToken,channel:i32)->i32{
    let level=&*token.cast::<Level>();
    if channel==1{if level.block.is_some(){0}else{-1}}else if level.sky.is_some(){1}else{-1}
}
unsafe extern "C" fn operation(token:*const LevelToken,kind:i32,key:i64,value:i64,entry:*const c_void)->i32{
    catch_unwind(AssertUnwindSafe(||{
        let level=&*token.cast::<Level>();
        if kind==0{
            for engine in [&level.block,&level.sky].into_iter().flatten(){
                let status=engine.retain_data(key,value!=0);if status!=0{return status;}
            }
            0
        }else if kind==1{
            let target=if value==1{&level.block}else{&level.sky};
            target.as_ref().map_or(0,|target|target.import(key,entry.cast()))
        }else{-27}
    })).unwrap_or(-99)
}

#[repr(C)]
struct LevelFrame {
    token:FrameToken,
    owner:Arc<Level>,
    key:i64,
    value:bool,
    kind:i32,
    which:usize,
    child:u64,
    action:i32,
    reply:[i64;7],
    finishing:u8,
    total:i32,
    snapshot:Option<Arc<LightMap>>,
}
impl LevelFrame {
    unsafe fn drive(&mut self,mut scalar:i64,mut entry:*const c_void)->i32{
        loop{
            self.reply.fill(0);
            let input=match self.which{0=>self.owner.block.as_ref(),1=>self.owner.sky.as_ref(),_=>{self.reply[0]=i64::from(self.total);return 0;}};
            let Some(input)=input else{self.which+=1;continue;};
            self.reply[6]=self.which as i64;
            if self.finishing==1{
                input.end_status(self.child);self.child=0;self.finishing=0;
                if input.binding_pending(){self.finishing=2;return 16;}
                self.which+=1;scalar=0;entry=std::ptr::null();continue;
            }
            if self.finishing==3{self.finishing=0;self.which+=1;scalar=0;entry=std::ptr::null();continue;}
            if self.finishing==2{self.finishing=0;self.which+=1;scalar=0;entry=std::ptr::null();continue;}
            if self.child==0{
                if self.kind==3 && input.projection.idle_update.load(Ordering::Acquire)!=0 {
                    // Clearing the original VM chunk cache remains a genuine
                    // effect even when native policy/propagation is empty.
                    self.finishing=3;return 10;
                }
                match input.begin_level(self.kind,self.key,self.value){Ok(id)=>self.child=id,Err(status)=>return status}
            }
            let action=input.level_step(self.child,scalar,entry.cast(),&mut self.reply,&mut self.snapshot);
            if action!=0{return action;}
            if self.kind==3{self.total=self.total.wrapping_add(self.reply[0] as i32);}
            if self.reply[5]!=0{self.finishing=1;return 23;}
            input.end_status(self.child);self.child=0;
            if input.binding_pending(){self.finishing=2;return 16;}
            self.which+=1;
            scalar=0;entry=std::ptr::null();
        }
    }
}
impl Drop for LevelFrame {
    fn drop(&mut self){
        if self.child!=0{
            let input=if self.which==0{&self.owner.block}else{&self.owner.sky};
            if let Some(input)=input{input.end_status(self.child);}
        }
    }
}
unsafe extern "C" fn frame_read(token:*mut FrameToken,out:*mut i64)->i32{
    let frame=&*token.cast::<LevelFrame>();
    std::ptr::copy_nonoverlapping(frame.reply.as_ptr(),out,7);frame.action
}
unsafe extern "C" fn frame_step(token:*mut FrameToken,scalar:i64,entry:*const c_void,out:*mut i64)->i32{
    let frame=&mut *token.cast::<LevelFrame>();
    if frame.action>0{frame.action=catch_unwind(AssertUnwindSafe(||frame.drive(scalar,entry))).unwrap_or(-99);}
    frame_read(token,out)
}
unsafe extern "C" fn frame_source(token:*mut FrameToken,source:*const super::super::source_owner::Source)->i32 {
    if source.is_null() { return -27; }
    let frame=&mut *token.cast::<LevelFrame>();
    if !matches!(frame.action,8|17) || frame.child==0 { return -27; }
    let input=match frame.which {0=>frame.owner.block.as_ref(),1=>frame.owner.sky.as_ref(),_=>None};
    input.map_or(-27,|input|input.bind_source(frame.child,&*source))
}
unsafe extern "C" fn frame_block(token:*mut FrameToken,section:*const crate::world::level::chunk::live::Owner,index:i32)->i32 {
    if section.is_null(){return -27;}
    let frame=&mut *token.cast::<LevelFrame>();
    if frame.action!=7 || frame.child==0{return -27;}
    let input=match frame.which{0=>frame.owner.block.as_ref(),1=>frame.owner.sky.as_ref(),_=>None};
    input.map_or(-27,|input|input.bind_block(frame.child,section,index))
}
unsafe extern "C" fn frame_resource(token:*mut FrameToken)->*const c_void{
    let frame=&mut *token.cast::<LevelFrame>();
    if frame.action!=5{return std::ptr::null();}
    frame.snapshot.take().map_or(std::ptr::null(),|snapshot|Arc::into_raw(snapshot).cast())
}
unsafe extern "C" fn frame_release(token:*mut FrameToken){drop(Box::from_raw(token.cast::<LevelFrame>()));}
unsafe extern "C" fn start(token:*const LevelToken,kind:i32,key:i64,value:bool)->*mut FrameToken{
    Arc::increment_strong_count(token.cast::<Level>());
    let mut frame=LevelFrame{token:FrameToken{read:frame_read,step:frame_step,release:frame_release,resource:frame_resource,bind_source:frame_source,bind_block:frame_block},
        owner:Arc::from_raw(token.cast::<Level>()),key,value,kind,which:0,child:0,action:0,reply:[0;7],finishing:0,total:0,snapshot:None};
    frame.action=catch_unwind(AssertUnwindSafe(||frame.drive(0,std::ptr::null()))).unwrap_or(-99);
    if frame.action==0&&(kind!=3||frame.total==0){std::ptr::null_mut()}else{Box::into_raw(Box::new(frame)).cast()}
}

unsafe fn status(token:*const LevelToken,key:i64,empty:bool)->*mut FrameToken{start(token,0,key,empty)}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_command(token:*const LevelToken,kind:i32,key:i64,value:i32)->u64{
    if token.is_null(){return 0;}start(token,kind,key,value!=0) as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_status(token:*const LevelToken,key:i64,empty:i32)->u64{
    if token.is_null(){return 0;}status(token,key,empty!=0) as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_frame_read(frame:u64,out:*mut i64)->i32{
    if frame==0||out.is_null(){-27}else{frame_read(frame as *mut FrameToken,out)}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_frame_step(frame:u64,scalar:i64,entry:*const c_void,out:*mut i64)->i32{
    if frame==0||out.is_null(){-27}else{frame_step(frame as *mut FrameToken,scalar,entry,out)}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_frame_bind_source(frame:u64,
    source:*const super::super::source_owner::Source)->i32 {
    if frame==0 || source.is_null() { return -27; }
    catch_unwind(AssertUnwindSafe(||frame_source(frame as *mut FrameToken,source))).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_frame_bind_block(frame:u64,section:*const crate::world::level::chunk::live::Owner,index:i32)->i32 {
    if frame==0 || section.is_null(){return -27;}
    catch_unwind(AssertUnwindSafe(||frame_block(frame as *mut FrameToken,section,index))).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_frame_resource(frame:u64)->*const c_void{
    if frame==0{std::ptr::null()}else{frame_resource(frame as *mut FrameToken)}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_projection(token:*const LevelToken)->*const c_void{
    token.as_ref().map_or(std::ptr::null(),|_|Arc::as_ptr(&(*token.cast::<Level>()).projection).cast())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_frame_release(frame:u64){if frame!=0{frame_release(frame as *mut FrameToken);}}
unsafe extern "C" fn check(token: *const LevelToken, pos: i64) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let level = &*token.cast::<Level>();
        // The original LevelLightEngine checks block before sky. A later error
        // preserves the first engine's committed node insertion for retry.
        for engine in [&level.block, &level.sky].into_iter().flatten() {
            let status = engine.enqueue_node(pos);
            if status != 0 { return status; }
        }
        0
    })).unwrap_or(-99)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_create(block: *const c_void, sky: *const c_void) -> *const LevelToken {
    if block.is_null() && sky.is_null() { return std::ptr::null(); }
    let block = if block.is_null() { None } else {
        let Some(engine) = PropagationInput::retain(block) else { return std::ptr::null(); };
        Some(engine)
    };
    let sky = if sky.is_null() { None } else {
        let Some(engine) = PropagationInput::retain(sky) else { return std::ptr::null(); };
        Some(engine)
    };
    let projection=Arc::new(LevelProjection::default());
    if let Some(input)=&block{input.levels.bind_level(&input.projection,&projection,0);}
    if let Some(input)=&sky{input.levels.bind_level(&input.projection,&projection,1);}
    Arc::into_raw(Arc::new(Level { token: LevelToken { retain, release, check, operation, channel, start }, block, sky, projection })).cast()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_release(token: *const LevelToken) {
    if !token.is_null() { release(token); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_level_check(token: *const LevelToken, pos: i64) -> i32 {
    if token.is_null() { -27 } else { check(token, pos) }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::{ffi, NODES};
    use crate::world::level::lighting::{maps::ffi as maps, propagation::{self, work::Queues}};

    #[test]
    fn request_frame_keeps_actual_level_after_both_callers_release_then_drops_it() {
        use crate::world::level::lighting::requests::*;
        unsafe {
            // Each suspension is a genuine VM boundary in a production request.
            for (kind, post, expected) in [(0, 0, 32), (1, 1, 39), (2, 0, 40)] {
                let registry = maps::mattmc_light_map_registry_create();
                let map = Arc::from_raw(maps::mattmc_light_map_create(registry));
                maps::mattmc_light_map_registry_release(registry);
                let owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map), 0);
                let work = Arc::new(Queues::default());
                let binding = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&work), 0);
                let level = mattmc_light_level_create(binding.cast(), std::ptr::null());
                Arc::increment_strong_count(level.cast::<Level>());
                let observed = Arc::from_raw(level.cast::<Level>());
                let weak = Arc::downgrade(&observed);
                drop(observed);
                let request = mattmc_light_request_create(level, kind, 0, 0);
                let frame = mattmc_light_request_begin(request, post);
                let mut out = [0i64; 7];
                assert_eq!(mattmc_light_request_read(frame, out.as_mut_ptr()), expected);
                mattmc_light_request_release(request);
                mattmc_light_level_release(level);
                ffi::mattmc_light_coordinator_bind_release(binding);
                ffi::mattmc_light_coordinator_release(owner);
                assert!(weak.upgrade().is_some(), "suspended frame owns the actual level");
                // Dropping an invocation models a throwing VM effect, not success.
                mattmc_light_request_end(frame);
                assert!(weak.upgrade().is_none(), "no native owner survives abandoned invocation");
            }
        }
    }

    #[test]
    fn abandoned_request_releases_actual_block_sky_child_and_retries_committed_prefix() {
        use crate::world::level::lighting::requests::*;
        unsafe {
            let registry = maps::mattmc_light_map_registry_create();
            let block_map = Arc::from_raw(maps::mattmc_light_map_create(registry));
            let sky_map = Arc::from_raw(maps::mattmc_light_map_create(registry));
            let block_owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&block_map), 0);
            let sky_owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&sky_map), 1);
            let block_work = Arc::new(Queues::default());
            let sky_work = Arc::new(Queues::default());
            let block = ffi::mattmc_light_coordinator_bind(block_owner, Arc::as_ptr(&block_work), 0);
            let sky = ffi::mattmc_light_coordinator_bind(sky_owner, Arc::as_ptr(&sky_work), 1);
            let level = mattmc_light_level_create(block.cast(), sky.cast());
            assert!(status(level, propagation::section_pos(0, 2, 0), false).is_null());
            let mut slot = 0;
            let reservation = maps::mattmc_light_map_reserve(registry, &mut slot);
            let identity = maps::mattmc_light_map_identity_create(registry);
            let opaque = sky_map.entry(*Box::from_raw(reservation), None, identity).unwrap();
            sky_map.set(propagation::section_pos(0, 1, 0), Some(Arc::clone(&opaque)));
            sky_map.clear_cache(false);
            let request = mattmc_light_request_create(level, 0, 0, 0);
            let frame = mattmc_light_request_begin(request, 0);
            let mut out = [0i64; 7];
            assert_eq!(mattmc_light_request_read(frame, out.as_mut_ptr()), 32);
            for (reply, expected) in [(0, 33), (1, 34), (0, 35), (0, 16)] {
                assert_eq!(mattmc_light_request_step(frame, reply, std::ptr::null(), out.as_mut_ptr()), expected);
            }
            assert_eq!(out[6], 0, "block prefix completes before sky callback");
            let mut retired = [0u32; 64];
            let count = maps::mattmc_light_map_retired(registry, retired.as_mut_ptr(), 64);
            assert!(count > 0);
            assert_eq!(maps::mattmc_light_map_retired_ack(registry, retired.as_ptr(), count as u32), 0);
            assert_eq!(mattmc_light_request_step(frame, 0, std::ptr::null(), out.as_mut_ptr()), 1);
            assert_eq!(out[6], 1);
            assert_eq!(out[0], i64::from(opaque.id));
            Arc::increment_strong_count(level.cast::<Level>());
            let observed = Arc::from_raw(level.cast::<Level>());
            assert_eq!(Arc::strong_count(&observed), 4,
                "original caller, request, real child and observer each retain the level");
            drop(observed);
            mattmc_light_request_end(frame);
            // Retrying the original request starts its live section scan again,
            // while the already committed block/sky status prefix stays applied.
            let retry = mattmc_light_request_begin(request, 0);
            assert_eq!(mattmc_light_request_read(retry, out.as_mut_ptr()), 32);
            for (reply, expected) in [(0, 33), (1, 34), (0, 35), (0, 33), (1, 0)] {
                assert_eq!(mattmc_light_request_step(retry, reply, std::ptr::null(), out.as_mut_ptr()), expected);
            }
            mattmc_light_request_end(retry);
            Arc::increment_strong_count(level.cast::<Level>());
            let observed = Arc::from_raw(level.cast::<Level>());
            let weak = Arc::downgrade(&observed);
            drop(observed);
            mattmc_light_level_release(level);
            ffi::mattmc_light_coordinator_bind_release(block);
            ffi::mattmc_light_coordinator_bind_release(sky);
            ffi::mattmc_light_coordinator_release(block_owner);
            ffi::mattmc_light_coordinator_release(sky_owner);
            assert!(weak.upgrade().is_some(), "queued request alone retains both actual engines");
            mattmc_light_request_release(request);
            assert!(weak.upgrade().is_none());
            maps::mattmc_light_map_registry_release(registry);
        }
    }

    #[test]
    fn produced_snapshot_is_owned_until_claim_or_abandoned_vm_publication(){
        unsafe{
            let registry=maps::mattmc_light_map_registry_create();let map=Arc::from_raw(maps::mattmc_light_map_create(registry));maps::mattmc_light_map_registry_release(registry);
            let owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map),0);let work=Arc::new(Queues::default());let binding=ffi::mattmc_light_coordinator_bind(owner,Arc::as_ptr(&work),0);
            let level=mattmc_light_level_create(binding.cast(),std::ptr::null());
            for claimed in [false,true]{
                let mut out=[0i64;7];assert_eq!(ffi::mattmc_light_coordinator_collection(owner,binding,3,2,11,1,std::ptr::null(),out.as_mut_ptr()),0);
                let frame=start(level,3,0,false);assert_eq!(frame_read(frame,out.as_mut_ptr()),9);
                assert_eq!(frame_step(frame,0,std::ptr::null(),out.as_mut_ptr()),10);
                assert_eq!(frame_step(frame,0,std::ptr::null(),out.as_mut_ptr()),5);
                let actual=&*frame.cast::<LevelFrame>();let weak=Arc::downgrade(actual.snapshot.as_ref().unwrap());
                let exported=if claimed{let value=frame_resource(frame);assert!(!value.is_null());assert!(frame_resource(frame).is_null());Some(Arc::from_raw(value.cast::<LightMap>()))}else{None};
                frame_release(frame);
                assert_eq!(weak.upgrade().is_some(),claimed);
                drop(exported);assert!(weak.upgrade().is_none(),"snapshot ownership is released exactly once after either outcome");
            }
            mattmc_light_level_release(level);ffi::mattmc_light_coordinator_bind_release(binding);ffi::mattmc_light_coordinator_release(owner);
        }
    }
    #[test]
    fn level_projection_tracks_real_queue_and_publication_eligibility_without_owner_cycle(){
        unsafe{
            let registry=maps::mattmc_light_map_registry_create();let map=Arc::from_raw(maps::mattmc_light_map_create(registry));maps::mattmc_light_map_registry_release(registry);
            let owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map),0);let work=Arc::new(Queues::default());let binding=ffi::mattmc_light_coordinator_bind(owner,Arc::as_ptr(&work),0);
            let level=mattmc_light_level_create(binding.cast(),std::ptr::null());let actual=&*level.cast::<Level>();let weak=Arc::downgrade(&actual.projection);
            assert_eq!(actual.projection.flags.load(Ordering::Acquire),12);
            let mut output=[0i64;2];assert_eq!(ffi::mattmc_light_coordinator_collection(owner,binding,3,2,11,1,std::ptr::null(),output.as_mut_ptr()),0);
            assert_eq!(actual.projection.flags.load(Ordering::Acquire),8,"changed-only has no public pending work but cannot bypass publication");
            {let mut queues=work.data.lock().unwrap();queues[1].extend([99,100]);work.publish(&queues);}
            assert_eq!(actual.projection.flags.load(Ordering::Acquire),9);
            {let mut queues=work.data.lock().unwrap();queues[1].clear();work.publish(&queues);}
            assert_eq!(actual.projection.flags.load(Ordering::Acquire)&3,0);
            mattmc_light_level_release(level);assert!(weak.upgrade().is_none());
            ffi::mattmc_light_coordinator_bind_release(binding);ffi::mattmc_light_coordinator_release(owner);
        }
    }
    #[test]
    fn status_owns_both_engine_prefix_and_abandoned_repeat_releases_child_for_retry(){
        unsafe{
            let registry=maps::mattmc_light_map_registry_create();
            let block_map=Arc::from_raw(maps::mattmc_light_map_create(registry));
            let sky_map=Arc::from_raw(maps::mattmc_light_map_create(registry));
            let block_owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&block_map),0);
            let sky_owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&sky_map),1);
            Arc::increment_strong_count(block_owner);let observer=Arc::from_raw(block_owner);let weak_block=Arc::downgrade(&observer);drop(observer);
            Arc::increment_strong_count(sky_owner);let observer=Arc::from_raw(sky_owner);let weak_sky=Arc::downgrade(&observer);drop(observer);
            let block_work=Arc::new(Queues::default());let sky_work=Arc::new(Queues::default());
            let block=ffi::mattmc_light_coordinator_bind(block_owner,Arc::as_ptr(&block_work),0);
            let sky=ffi::mattmc_light_coordinator_bind(sky_owner,Arc::as_ptr(&sky_work),1);
            let level=mattmc_light_level_create(block.cast(),sky.cast());
            let high=super::super::propagation::section_pos(0,2,0);
            assert!(status(level,high,false).is_null());
            let mut slot=0;let reservation=maps::mattmc_light_map_reserve(registry,&mut slot);
            let identity=maps::mattmc_light_map_identity_create(registry);
            let opaque=sky_map.entry(*Box::from_raw(reservation),None,identity).unwrap();
            maps::mattmc_light_map_registry_release(registry);
            let above=super::super::propagation::section_pos(0,1,0);
            sky_map.set(above,Some(Arc::clone(&opaque)));sky_map.clear_cache(false);
            let key=super::super::propagation::section_pos(0,0,0);
            let frame=status(level,key,false);assert!(!frame.is_null());let mut output=[0i64;7];
            assert_eq!(frame_read(frame,output.as_mut_ptr()),16);assert_eq!(output[6],0,"block retirement precedes sky effects");
            let mut retired=[0u32;64];let count=maps::mattmc_light_map_retired(registry,retired.as_mut_ptr(),64);
            assert!(count>0);assert_eq!(maps::mattmc_light_map_retired_ack(registry,retired.as_ptr(),count as u32),0);
            assert_eq!(frame_step(frame,0,std::ptr::null(),output.as_mut_ptr()),1);assert_eq!(output[0],i64::from(opaque.id));assert_eq!(output[6],1);
            let mut out=[0i64;2];
            assert_eq!(ffi::mattmc_light_coordinator_collection(block_owner,std::ptr::null(),1,1,key,0,std::ptr::null(),out.as_mut_ptr()),0);
            assert_eq!(out[0],32,"block engine completed before sky callback");
            frame_release(frame);
            assert!(status(level,key,false).is_null(),"retry preserves original committed section-state prefix");
            ffi::mattmc_light_coordinator_bind_release(block);ffi::mattmc_light_coordinator_bind_release(sky);
            ffi::mattmc_light_coordinator_release(block_owner);ffi::mattmc_light_coordinator_release(sky_owner);
            mattmc_light_level_release(level);
            assert!(weak_block.upgrade().is_none());assert!(weak_sky.upgrade().is_none());
        }
    }
    #[test]
    fn actual_import_selects_channel_rejects_foreign_entry_and_retains_original_owner(){
        unsafe{
            let registry=maps::mattmc_light_map_registry_create();
            let block_map=Arc::from_raw(maps::mattmc_light_map_create(registry));
            let sky_map=Arc::from_raw(maps::mattmc_light_map_create(registry));
            maps::mattmc_light_map_registry_release(registry);
            let block_owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&block_map),0);
            let sky_owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&sky_map),1);
            let block_work=Arc::new(Queues::default());let sky_work=Arc::new(Queues::default());
            let block=ffi::mattmc_light_coordinator_bind(block_owner,Arc::as_ptr(&block_work),0);
            let sky=ffi::mattmc_light_coordinator_bind(sky_owner,Arc::as_ptr(&sky_work),1);
            let level=mattmc_light_level_create(block.cast(),sky.cast());
            let source=block_map.native_entry(Arc::new(super::super::super::layers::Layer::new(7))).unwrap();
            let weak=Arc::downgrade(&source);let entry=Arc::as_ptr(&source).cast();
            assert_eq!(operation(level,0,777,1,std::ptr::null()),0);
            let mut out=[0i64;2];
            for owner in [block_owner,sky_owner]{
                assert_eq!(ffi::mattmc_light_coordinator_collection(owner,std::ptr::null(),6,0,777,0,std::ptr::null(),out.as_mut_ptr()),0);
                assert_eq!(out[0],1);
            }
            assert_eq!(channel(level,1),0);assert_eq!(channel(level,-1),1);
            assert_eq!(operation(level,1,8,1,entry),0);
            assert_eq!(operation(level,1,9,-1,entry),0);
            for (owner,key) in [(block_owner,8),(sky_owner,9)]{
                assert_eq!(ffi::mattmc_light_coordinator_collection(owner,std::ptr::null(),5,1,key,0,std::ptr::null(),out.as_mut_ptr()),0);
                assert_eq!(out[0],i64::from(source.id));
            }
            let foreign_registry=maps::mattmc_light_map_registry_create();
            let foreign=Arc::from_raw(maps::mattmc_light_map_create(foreign_registry));
            maps::mattmc_light_map_registry_release(foreign_registry);
            let foreign_entry=foreign.native_entry(Arc::new(super::super::super::layers::Layer::new(5))).unwrap();
            assert_eq!(operation(level,1,88,1,Arc::as_ptr(&foreign_entry).cast()),-27);
            let block_only=mattmc_light_level_create(block.cast(),std::ptr::null());
            assert_eq!(channel(block_only,0),-1);assert_eq!(operation(block_only,1,99,0,entry),0);
            mattmc_light_level_release(block_only);
            drop(source);assert!(weak.upgrade().is_some());
            ffi::mattmc_light_coordinator_bind_release(block);ffi::mattmc_light_coordinator_bind_release(sky);
            ffi::mattmc_light_coordinator_release(block_owner);ffi::mattmc_light_coordinator_release(sky_owner);
            assert!(weak.upgrade().is_some());mattmc_light_level_release(level);assert!(weak.upgrade().is_none());
        }
    }

    #[test]
    fn actual_level_produces_both_node_tables_and_releases_detached_engines() {
        unsafe {
            let registry = maps::mattmc_light_map_registry_create();
            let map = maps::mattmc_light_map_create(registry);
            maps::mattmc_light_map_registry_release(registry);
            let owner = ffi::mattmc_light_coordinator_create(map, 0);
            maps::mattmc_light_map_release(map);
            Arc::increment_strong_count(owner);
            let observing = Arc::from_raw(owner);
            let weak_owner = Arc::downgrade(&observing);
            let block = Arc::new(Queues::default());
            let sky = Arc::new(Queues::default());
            let weak_block = Arc::downgrade(&block);
            let weak_sky = Arc::downgrade(&sky);
            let block_binding = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&block), 0);
            let sky_binding = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&sky), 1);
            let level = mattmc_light_level_create(block_binding.cast(), sky_binding.cast());
            assert!(!level.is_null());
            let mut out = [0i64; 2];
            for pos in [91, 91, -17] { assert_eq!(mattmc_light_level_check(level, pos), 0); }
            for binding in [block_binding, sky_binding] {
                assert_eq!(ffi::mattmc_light_coordinator_collection(owner, binding, NODES as i32, 4, 0, 0, std::ptr::null(), out.as_mut_ptr()), 0);
                assert_eq!(out[0], 2, "both real engines receive the same deduplicated positions");
                for pos in [91, -17] {
                    assert_eq!(ffi::mattmc_light_coordinator_collection(owner, binding, NODES as i32, 0, pos, 0, std::ptr::null(), out.as_mut_ptr()), 0);
                    assert_eq!(out[0], 1);
                }
            }
            ffi::mattmc_light_coordinator_bind_release(block_binding);
            ffi::mattmc_light_coordinator_bind_release(sky_binding);
            ffi::mattmc_light_coordinator_release(owner);
            drop(observing); drop(block); drop(sky);
            assert!(weak_owner.upgrade().is_some());
            assert!(weak_block.upgrade().is_some());
            assert!(weak_sky.upgrade().is_some());
            assert_eq!(mattmc_light_level_check(level, 123), 0, "level owns the actual detached engine inputs");
            mattmc_light_level_release(level);
            assert!(weak_owner.upgrade().is_none());
            assert!(weak_block.upgrade().is_none());
            assert!(weak_sky.upgrade().is_none());
            assert!(mattmc_light_level_create(std::ptr::null(), std::ptr::null()).is_null());
        }
    }
}
