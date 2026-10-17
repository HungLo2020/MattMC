//! Original block propagation order over the actual pending queues. VM leaves
//! execute after queue guards have dropped; failures keep the consumed/write prefix.
use super::{block_pos,block_x,block_y,block_z,section_pos,work::Queues,DIRECTIONS,FROM_EMPTY_SHAPE,FROM_EMISSION,OFFSETS};
use crate::world::level::lighting::coordinator::ffi::PropagationInput;
use std::{sync::Arc,panic::{catch_unwind,AssertUnwindSafe}};

const READ:i32=0; const WRITE:i32=1; const STORED:i32=2; const STATE:i32=3;
const OPACITY:i32=4; const EMISSION:i32=5; const EMPTY:i32=6; const OCCLUDES:i32=7;
const CLEAR:i32=8; const AIR:i32=9; const MISSING:i32=10; const REFRESH:i32=11; const STORAGE_ERROR:i32=12;
const STATE_FALLBACK:i32=13;
trait Leaves { fn call(&mut self,action:i32,a:i64,b:i64,c:i64)->Result<i64,()>; }
#[derive(Clone,Copy)]
struct NativeState {id:u16,light:super::Type}
impl NativeState {
 fn from_id(tables:&super::Tables,id:i64)->Option<Self>{
  let id=u16::try_from(id).ok()?;let kind=tables.state_type(id);
  (kind!=super::UNSUPPORTED).then(||Self{id,light:tables.types[kind as usize]})
 }
}
/// Invocation-local admission slot. Only a synchronous original state callback
/// receives its address; the actual section Arc outlives that VM borrow.
struct StateCapture<'a>{tables:&'a super::Tables,input:Option<(Arc<crate::world::level::chunk::live::Owner>,usize)>}
impl StateCapture<'_>{
 fn capture(&mut self,owner:Arc<crate::world::level::chunk::live::Owner>,index:usize)->bool{
  if self.input.is_some()||index>=4096||owner.world_value(index).and_then(|id|NativeState::from_id(self.tables,id as i64)).is_none(){return false;}
  self.input=Some((owner,index));true
 }
 fn consume(&mut self)->Result<Option<NativeState>,()>{
  let Some((owner,index))=self.input.take()else{return Ok(None);};
  let state=owner.world_value(index).and_then(|id|NativeState::from_id(self.tables,id as i64));
  drop(owner);state.map(Some).ok_or(())
 }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_block_capture_state(slot:*mut std::ffi::c_void,owner:*const crate::world::level::chunk::live::Owner,index:i32)->i32{
 if slot.is_null()||owner.is_null()||!(0..4096).contains(&index){return -1;}
 catch_unwind(AssertUnwindSafe(||{
  Arc::increment_strong_count(owner);let owner=Arc::from_raw(owner);
  i32::from((*(slot as *mut StateCapture<'_>)).capture(owner,index as usize))
 })).unwrap_or(-99)
}
struct Pass<'a,L> {queues:Arc<Queues>,leaves:&'a mut L,input:Option<PropagationInput>,tables:Option<&'a super::Tables>,states:[Option<NativeState>;2]}
impl<L:Leaves> Pass<'_,L> {
 fn call(&mut self,action:i32,a:i64,b:i64)->Result<i32,()>{
  if let Some(tables)=self.tables{
   match action{
    STATE=>{
     let mut capture=StateCapture{tables,input:None};
     let id=self.leaves.call(STATE,a,b,(&mut capture as *mut StateCapture<'_>)as i64)?;
     self.states[b as usize]=match capture.consume(){
      Ok(state)=>state.or_else(||NativeState::from_id(tables,id)),
      // Materialization can invalidate the live primary owner after admission.
      // consume drops that Arc before the original selected chunk is read.
      Err(())=>NativeState::from_id(tables,self.leaves.call(STATE_FALLBACK,a,b,0)?),
     };
     return Ok(0);
    },
    OPACITY=>if let Some(state)=self.states[a as usize]{return Ok(state.light.opacity as i32);},
    EMPTY=>if let Some(state)=self.states[a as usize]{return Ok(i32::from(state.light.empty));},
    EMISSION=>if let Some(state)=self.states[b as usize]{
     if state.light.emission==0{return Ok(0);}
     if let Some(input)=self.input.as_ref(){return Ok(if input.light_enabled(section_pos(block_x(a)>>4,block_y(a)>>4,block_z(a)>>4)){state.light.emission as i32}else{0});}
    },
    OCCLUDES=>{
     if let(Some(source),Some(target))=(self.states[0],self.states[1]){
      return Ok(i32::from(tables.occludes(source.light.faces[a as usize],target.light.faces[a as usize^1])));
     }
     // A mixed shape needs the original registered VM facade alongside the
     // custom state. No lookup or custom state producer is repeated.
     return self.leaves.call(OCCLUDES,a,self.states[0].map_or(0,|state|state.id as i64+1),self.states[1].map_or(0,|state|state.id as i64+1)).map(|v|v as i32);
    },
    CLEAR=>if self.states[a as usize].take().is_some(){return Ok(0);},
    AIR=>{
     let registry=crate::content::block::installed().ok_or(())?;
     let id=registry.block(registry.air().ok_or(())?).default_state();
     self.states[0]=NativeState::from_id(tables,id.index()as i64);return Ok(0);
    },
    _=>{},
   }
  }

  if let Some(input)=self.input.as_ref(){
   let result=match action{
    READ=>input.stored_read(a),
    STORED=>Ok(input.stored_section(a).map(i32::from)),
    WRITE=>match input.stored_write(a,b as i32){
     Ok(Some((ids,pending)))=>{
      if !ids.is_empty()||pending{self.leaves.call(REFRESH,ids.as_ptr()as i64,ids.len()as i64,0)?;}
      return Ok(0);
     },Ok(None)=>Ok(None),Err(error)=>Err(error),
    },
    _=>Ok(None),
   };
   match result{
    Ok(Some(value))=>return Ok(value),Ok(None)=>{},
    Err((status,args))=>{self.leaves.call(STORAGE_ERROR,status as i64,args[0],args[1])?;return Err(());}
   }
  }
  self.leaves.call(action,a,b,0).map(|v|v as i32)
 }
 fn pop(&mut self,side:usize)->Result<Option<(i64,u64)>,()>{
  let mut queues=self.queues.data.lock().unwrap();
  let Some(position)=queues[side].pop_front()else{return Ok(None);};
  self.queues.publish(&queues);
  let entry=queues[side].pop_front();self.queues.publish(&queues);drop(queues);
  match entry{Some(entry)=>Ok(Some((position,entry as u64))),None=>{self.call(MISSING,0,0)?;Err(())}}
 }
 fn enqueue(&self,side:i32,position:i64,entry:u64)->Result<(),()>{if self.queues.enqueue(side,position,entry as i64)==0{Ok(())}else{Err(())}}
 fn neighbor(position:i64,direction:usize)->i64{let(x,y,z)=OFFSETS[direction];block_pos(block_x(position).wrapping_add(x),block_y(position).wrapping_add(y),block_z(position).wrapping_add(z))}
 fn stored(&mut self,position:i64)->Result<bool,()>{self.call(STORED,section_pos(block_x(position)>>4,block_y(position)>>4,block_z(position)>>4),0).map(|v|v!=0)}
 fn decrease(&mut self,position:i64,entry:u64)->Result<(),()>{
  let from=(entry&15)as i32;
  for direction in 0..6{
   if entry&(16<<direction)==0{continue;}
   let next=Self::neighbor(position,direction);
   if !self.stored(next)?{continue;}
   let stored=self.call(READ,next,0)?;
   if stored==0{continue;}
   if stored<=from.wrapping_sub(1){
    self.call(STATE,next,1)?;
    let emission=self.call(EMISSION,next,1)?;
    self.call(WRITE,next,0)?;
    if emission<stored{self.enqueue(0,next,(DIRECTIONS&!(16<<(direction^1)))|((stored as u64)&15))?;}
    if emission>0{let empty=self.call(EMPTY,1,0)?!=0;self.enqueue(1,next,DIRECTIONS|FROM_EMISSION|if empty{FROM_EMPTY_SHAPE}else{0}|((emission as u64)&15))?;}
    self.call(CLEAR,1,0)?;
   }else{self.enqueue(1,next,(16<<(direction^1))|((stored as u64)&15))?;}
  }
  Ok(())
 }
 fn increase(&mut self,position:i64,entry:u64,from:i32)->Result<(),()>{
  let mut source=false;
  for direction in 0..6{
   if entry&(16<<direction)==0{continue;}
   let next=Self::neighbor(position,direction);
   if !self.stored(next)?{continue;}
   let stored=self.call(READ,next,0)?;
   if from.wrapping_sub(1)<=stored{continue;}
   self.call(STATE,next,1)?;
   let target=from.wrapping_sub(self.call(OPACITY,1,0)?);
   if target>stored{
    if !source{if entry&FROM_EMPTY_SHAPE!=0{self.call(AIR,0,0)?;}else{self.call(STATE,position,0)?;}source=true;}
    if self.call(OCCLUDES,direction as i64,0)?==0{
     self.call(WRITE,next,target as i64)?;
     if target>1{let empty=self.call(EMPTY,1,0)?!=0;self.enqueue(1,next,(DIRECTIONS&!(16<<(direction^1)))|if empty{FROM_EMPTY_SHAPE}else{0}|((target as u64)&15))?;}
    }
   }
   self.call(CLEAR,1,0)?;
  }
  self.call(CLEAR,0,0)?;Ok(())
 }
 fn run(&mut self)->Result<i32,()>{
  let mut count=0i32;
  while let Some((position,entry))=self.pop(0)?{self.decrease(position,entry)?;count=count.wrapping_add(1);}
  while let Some((position,entry))=self.pop(1)?{
   let mut stored=self.call(READ,position,0)?;let from=(entry&15)as i32;
   if entry&FROM_EMISSION!=0&&stored<from{self.call(WRITE,position,from as i64)?;stored=from;}
   if stored==from{self.increase(position,entry,stored)?;}
   count=count.wrapping_add(1);
  }
  Ok(count)
 }
}
type Callback=unsafe extern "C" fn(i32,i64,i64,i64)->i64;
struct Vm(Callback);
impl Leaves for Vm{fn call(&mut self,action:i32,a:i64,b:i64,c:i64)->Result<i64,()>{let value=unsafe{(self.0)(action,a,b,c)};if value==i64::MIN{Err(())}else{Ok(value)}}}
/// No borrowed VM address, cursor registry or queue guard survives a leaf call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_block_run_effects(queues:*const Queues,callback:Option<Callback>,output:*mut i64)->i32{
 if queues.is_null()||output.is_null(){return -1;}
 let Some(callback)=callback else{return -1;};
 catch_unwind(AssertUnwindSafe(||{Arc::increment_strong_count(queues);let queues=Arc::from_raw(queues);let mut leaves=Vm(callback);let mut pass=Pass{queues,leaves:&mut leaves,input:None,tables:None,states:[None;2]};match pass.run(){Ok(count)=>{*output=count as i64;0},Err(())=>-2}})).unwrap_or(-99)
}

/// Retain the actual storage binding and its existing policy; VM effects run
/// only after every state/queue guard has ended.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_block_run_connected_effects(queues:*const Queues,binding:*const std::ffi::c_void,
 callback:Option<Callback>,output:*mut i64)->i32{
 if queues.is_null()||binding.is_null()||output.is_null(){return -1;}
 let Some(callback)=callback else{return -1;};
 catch_unwind(AssertUnwindSafe(||{
  let Some(input)=PropagationInput::retain(binding)else{return -1;};
  if !input.matches_block_queues(queues){return -1;}
  Arc::increment_strong_count(queues);let queues=Arc::from_raw(queues);let mut leaves=Vm(callback);
  let mut pass=Pass{queues,leaves:&mut leaves,input:Some(input),tables:super::installed_tables(),states:[None;2]};
  match pass.run(){Ok(count)=>{*output=count as i64;0},Err(())=>-2}
 })).unwrap_or(-99)
}

#[cfg(test)]
mod tests {
 use super::*;
 use crate::world::level::lighting::layers::Layer;
 use std::sync::Weak;
 struct ActualLeaves{queue:Weak<Queues>,layer:Layer,calls:Vec<(i32,i64,i64)>,fail_state:bool}
 fn index(pos:i64)->i32{(block_y(pos)&15)<<8|(block_z(pos)&15)<<4|(block_x(pos)&15)}
 impl Leaves for ActualLeaves{
  fn call(&mut self,action:i32,a:i64,b:i64,_:i64)->Result<i64,()>{
   assert_eq!(self.queue.strong_count(),1,"Only the real native pass owns the queue after base release");
   self.calls.push((action,a,b));
   match action{
    READ=>Ok(self.layer.view().get(index(a)).unwrap()as i64),
    WRITE=>{self.layer.set(index(a),b as i32).unwrap();Ok(0)},
    STORED=>Ok(1),
    STATE if self.fail_state=>{let queue=self.queue.upgrade().unwrap();assert_eq!(queue.enqueue(1,block_pos(12,8,8),17),0);Err(())},
    STATE|CLEAR|AIR=>Ok(0),OPACITY=>Ok(1),EMPTY=>Ok(1),OCCLUDES|EMISSION=>Ok(0),_=>panic!("Unexpected actual test leaf {action}")
   }
  }
 }
 #[test]
 fn actual_queue_and_layer_keep_consumed_write_prefix_and_callback_addition_without_replay(){
  let queue=Arc::new(Queues::default());let weak=Arc::downgrade(&queue);let position=block_pos(8,8,8);assert_eq!(queue.enqueue(1,position,(DIRECTIONS|FROM_EMISSION|FROM_EMPTY_SHAPE|15)as i64),0);
  let mut leaves=ActualLeaves{queue:weak.clone(),layer:Layer::new(0),calls:vec![],fail_state:true};
  let mut pass=Pass{queues:queue,leaves:&mut leaves,input:None,tables:None,states:[None;2]};assert_eq!(pass.run(),Err(()));
  assert_eq!(pass.leaves.layer.view().get(index(position)).unwrap(),15,"Actual emitted write stays committed after the custom state failure");
  assert_eq!(pass.queues.data.lock().unwrap()[1].iter().copied().collect::<Vec<_>>(),vec![block_pos(12,8,8),17],"Actual callback addition survives and consumed original is never replayed");
  assert_eq!(pass.leaves.calls.iter().filter(|(action,_,_)|*action==STATE).count(),1);
  drop(pass);assert_eq!(weak.strong_count(),0,"Actual queue retires at failed pass release");
 }
 #[test]
 fn actual_queue_survives_base_drop_through_each_leaf_and_retires_after_complete_consumer(){
  let queue=Arc::new(Queues::default());let weak=Arc::downgrade(&queue);let position=block_pos(8,8,8);assert_eq!(queue.enqueue(1,position,(DIRECTIONS|FROM_EMISSION|FROM_EMPTY_SHAPE|1)as i64),0);
  let mut leaves=ActualLeaves{queue:weak.clone(),layer:Layer::new(0),calls:vec![],fail_state:false};let mut pass=Pass{queues:queue,leaves:&mut leaves,input:None,tables:None,states:[None;2]};assert_eq!(pass.run(),Ok(1));assert_eq!(pass.leaves.layer.view().get(index(position)).unwrap(),1);assert!(pass.queues.data.lock().unwrap().iter().all(|queue|queue.is_empty()));drop(pass);assert_eq!(weak.strong_count(),0);
 }
 #[test]
 fn abandoned_actual_pass_drops_its_only_queue_owner_without_a_vm_leaf(){
  let queue=Arc::new(Queues::default());let weak=Arc::downgrade(&queue);assert_eq!(queue.enqueue(0,block_pos(8,8,8),15),0);let mut leaves=ActualLeaves{queue:weak.clone(),layer:Layer::new(0),calls:vec![],fail_state:false};let pass=Pass{queues:queue,leaves:&mut leaves,input:None,tables:None,states:[None;2]};assert_eq!(weak.strong_count(),1);drop(pass);assert_eq!(weak.strong_count(),0);assert!(leaves.calls.is_empty());
 }
}

/// Typed diagnostic weak handle; does not retain a queue or any VM reference.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_block_work_probe_new(queues:*const Queues)->u64{
 if queues.is_null()||queues as usize%std::mem::align_of::<Queues>()!=0{return 0;}
 Arc::increment_strong_count(queues);let owner=Arc::from_raw(queues);
 Box::into_raw(Box::new(Arc::downgrade(&owner)))as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_block_work_probe_owners(probe:u64)->u64{
 if probe==0{return 0;}(*(probe as *const std::sync::Weak<Queues>)).strong_count()as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_block_work_probe_release(probe:u64){
 if probe!=0{drop(Box::from_raw(probe as *mut std::sync::Weak<Queues>));}
}

#[cfg(test)]
mod primary_state_inputs {
 use super::*;
 use crate::world::level::chunk::live::Owner;
 fn tables()->super::super::Tables{
  let air=super::super::Type{opacity:1,emission:0,empty:true,faces:[0;6]};
  let solid=super::super::Type{opacity:15,emission:3,empty:false,faces:[1;6]};
  super::super::Tables{state_types:vec![0,1,super::super::UNSUPPORTED],types:vec![air,solid],faces:2,occludes:vec![0,0,0,1],air:0}
 }
 fn section()->Arc<Owner>{Arc::new(Owner::load(0,0,&[0],&[],3,15).unwrap())}
 #[test]
 fn real_primary_state_capture_reads_live_owner_after_base_drop_and_retires_before_property_consumer(){
  let tables=tables();let owner=section();let weak=Arc::downgrade(&owner);let mut capture=StateCapture{tables:&tables,input:None};
  assert!(capture.capture(owner.clone(),2184));owner.write(2184,1).unwrap();drop(owner);
  assert_eq!(weak.strong_count(),1);let state=capture.consume().unwrap().unwrap();assert_eq!(state.id,1,"Consume the actual newly written primary value, not the admission value or a fake scalar");
  assert_eq!(weak.strong_count(),0,"Primary owner ends before the later property consumer");assert_eq!(state.light.opacity,15);assert_eq!(state.light.emission,3);assert!(!state.light.empty);
 }
 #[test]
 fn abandoned_real_primary_state_capture_retires_without_a_property_or_vm_call(){
  let tables=tables();let owner=section();let weak=Arc::downgrade(&owner);let mut capture=StateCapture{tables:&tables,input:None};assert!(capture.capture(owner,17));assert_eq!(weak.strong_count(),1);drop(capture);assert_eq!(weak.strong_count(),0);
 }
 #[test]
 fn unsupported_or_invalid_real_primary_state_declines_before_retaining_or_consuming(){
  let tables=tables();for invalid in [false,true]{
   let owner=section();if invalid{owner.invalidate();}else{owner.write(12,2).unwrap();}
   let weak=Arc::downgrade(&owner);let mut capture=StateCapture{tables:&tables,input:None};assert!(!capture.capture(owner.clone(),12));assert!(capture.input.is_none());drop(owner);assert_eq!(weak.strong_count(),0);assert!(capture.consume().unwrap().is_none());
  }
 }
 #[test]
 fn late_invalid_or_unsupported_primary_retires_before_original_state_fallback_and_keeps_failure(){
  struct Effects{owner:Option<Arc<Owner>>,weak:std::sync::Weak<Owner>,invalid:bool,fail:bool,calls:Vec<i32>}
  impl Leaves for Effects{
   fn call(&mut self,action:i32,a:i64,b:i64,c:i64)->Result<i64,()>{
    assert_eq!((a,b),(12345,1));self.calls.push(action);
    match action{
     STATE=>{
      let owner=self.owner.take().unwrap();
      assert!(unsafe{(*(c as *mut StateCapture<'_>)).capture(owner.clone(),12)});
      if self.invalid{owner.invalidate();}else{owner.write(12,2).unwrap();}
      drop(owner);assert_eq!(self.weak.strong_count(),1);Ok(-1)
     },
     STATE_FALLBACK=>{
      assert_eq!(c,0);assert_eq!(self.weak.strong_count(),0,"No primary owner survives into the original VM fallback");
      if self.fail{Err(())}else{Ok(1)}
     },
     _=>panic!("Unexpected late-invalidation effect {action}")
    }
   }
  }
  let tables=tables();
  for invalid in [false,true]{for fail in [false,true]{
   let owner=section();let weak=Arc::downgrade(&owner);
   let mut leaves=Effects{owner:Some(owner),weak:weak.clone(),invalid,fail,calls:vec![]};
   let mut pass=Pass{queues:Arc::new(Queues::default()),leaves:&mut leaves,input:None,tables:Some(&tables),states:[None;2]};
   assert_eq!(pass.call(STATE,12345,1),if fail{Err(())}else{Ok(0)});
   assert_eq!(pass.leaves.calls,vec![STATE,STATE_FALLBACK],"Never replay the contextual state producer");
   assert_eq!(weak.strong_count(),0);
   if !fail{assert_eq!(pass.states[1].unwrap().id,1,"Use the fallback result rather than the admission value");}
  }}
 }
 #[test]
 fn native_property_consumer_keeps_original_mixed_shape_facades_and_custom_retirement(){
  struct Effects(Vec<(i32,i64,i64,i64)>);
  impl Leaves for Effects{fn call(&mut self,action:i32,a:i64,b:i64,c:i64)->Result<i64,()>{self.0.push((action,a,b,c));Ok(0)}}
  let tables=tables();let mut leaves=Effects(vec![]);let queue=Arc::new(Queues::default());let mut pass=Pass{queues:queue,leaves:&mut leaves,input:None,tables:Some(&tables),states:[NativeState::from_id(&tables,1),NativeState::from_id(&tables,0)]};
  assert_eq!(pass.call(OPACITY,1,0),Ok(1));assert_eq!(pass.call(EMPTY,1,0),Ok(1));assert_eq!(pass.call(EMISSION,0,1),Ok(0));assert_eq!(pass.call(OCCLUDES,2,0),Ok(0));assert!(pass.leaves.0.is_empty());
  pass.states[1]=None;assert_eq!(pass.call(OCCLUDES,3,0),Ok(0));assert_eq!(pass.leaves.0,vec![(OCCLUDES,3,2,0)],"Only the real registered source facade is reconstructed alongside the original custom target");
  pass.call(CLEAR,0,0).unwrap();pass.call(CLEAR,1,0).unwrap();assert_eq!(pass.leaves.0.last(),Some(&(CLEAR,1,0,0)),"Custom states still retire at their original VM boundary");assert_eq!(pass.leaves.0.len(),2);
 }
}
