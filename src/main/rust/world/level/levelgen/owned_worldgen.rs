//! Actual wrapper count, backing-source consumption and seed producer policy.
//! VM resources and virtual effects are supplied between native steps.
use super::{owned_legacy, owned_source, random};
use std::sync::{Arc, atomic::{AtomicI32,AtomicU64,Ordering}};
static LIVE:AtomicU64=AtomicU64::new(0);
static CREATED:AtomicU64=AtomicU64::new(0);
static RETIRED:AtomicU64=AtomicU64::new(0);
static FRAMES:AtomicU64=AtomicU64::new(0);
static FRAME_CREATED:AtomicU64=AtomicU64::new(0);
static FRAME_RETIRED:AtomicU64=AtomicU64::new(0);

pub(crate) struct Worldgen { count:AtomicI32 }
impl Worldgen {
    fn new()->Arc<Self>{LIVE.fetch_add(1,Ordering::Relaxed);CREATED.fetch_add(1,Ordering::Relaxed);Arc::new(Self{count:AtomicI32::new(0)})}
    pub(crate) fn count(&self)->i32{self.count.load(Ordering::Relaxed)}
    fn bump(&self){self.count.fetch_add(1,Ordering::Relaxed);}
    fn next(&self,target:&Target,bits:i32)->Result<i32,()>{self.bump();target.next(bits)}
}
impl Drop for Worldgen {fn drop(&mut self){LIVE.fetch_sub(1,Ordering::Relaxed);RETIRED.fetch_add(1,Ordering::Relaxed);}}
enum Target { Legacy(Arc<owned_legacy::Source>), Xoroshiro(Arc<owned_source::Source>) }
fn word(bits:i32,value:i64)->i32{((value as u64)>>((64_i32.wrapping_sub(bits)&63)as u32))as i32}
impl Target {
    fn next(&self,bits:i32)->Result<i32,()>{match self{Self::Legacy(source)=>source.next(bits),Self::Xoroshiro(source)=>Ok(source.with(|data|word(bits,data.next_long())))}}
}
/// A real native consumer owns count AND the exact backing data until last use.
pub(crate) struct Kernel { owner:Arc<Worldgen>, target:Target }
impl Kernel {
    pub(crate) fn next(&self,bits:i32)->Result<i32,()>{self.owner.next(&self.target,bits)}
    pub(crate) fn double(&self)->Result<f64,()>{Ok(random::legacy_double(self.next(26)?,self.next(27)?))}
    pub(crate) fn bounded(&self,bound:i32)->Result<i32,()>{random::legacy_bounded(bound,|bits|self.next(bits))}
}
fn seed_value(kind:i32,seed:i64,x:i32,z:i32,salt:i32)->i64 {
    if kind==3 {seed.wrapping_add(x as i64).wrapping_add(10000_i32.wrapping_mul(z)as i64)}
    else {(x as i64).wrapping_mul(341873128712).wrapping_add((z as i64).wrapping_mul(132897987541)).wrapping_add(seed).wrapping_add(salt as i64)}
}
enum Effect { Seed(i64), Long, Done(i64) }
#[repr(C)]
struct Seed {
    words:[u64;2],kind:i32,seed:i64,x:i32,z:i32,salt:i32,stage:u8,a:i64,result:i64,
}
impl Seed {
    fn new(kind:i32,seed:i64,x:i32,z:i32,salt:i32)->Self{
        FRAMES.fetch_add(1,Ordering::Relaxed);FRAME_CREATED.fetch_add(1,Ordering::Relaxed);
        Self{words:[0;2],kind,seed,x,z,salt,stage:0,a:0,result:0}
    }
    fn advance(&mut self,response:i64)->Effect{
        match self.stage {
            0=>{
                if self.kind==1||self.kind==2 {self.stage=1;Effect::Seed(self.seed)}
                else {
                    self.result=seed_value(self.kind,self.seed,self.x,self.z,self.salt);
                    self.stage=4;Effect::Seed(self.result)
                }
            }
            1=>{self.stage=2;Effect::Long}
            2=>{self.a=if self.kind==1 {response|1}else{response};self.stage=3;Effect::Long}
            3=>{
                self.result=if self.kind==1 {(self.x as i64).wrapping_mul(self.a).wrapping_add((self.z as i64).wrapping_mul(response|1))^self.seed}
                else {(self.x as i64).wrapping_mul(self.a)^(self.z as i64).wrapping_mul(response)^self.seed};
                self.stage=4;Effect::Seed(self.result)
            }
            _=>Effect::Done(self.result),
        }
    }
}
impl Drop for Seed{fn drop(&mut self){FRAMES.fetch_sub(1,Ordering::Relaxed);FRAME_RETIRED.fetch_add(1,Ordering::Relaxed);}}
pub(crate) mod ffi {
    use super::*;
    pub(crate) unsafe fn retain(id:u64)->Arc<Worldgen>{let ptr=std::ptr::with_exposed_provenance::<Worldgen>(id as usize);Arc::increment_strong_count(ptr);Arc::from_raw(ptr)}
    unsafe fn owner<'a>(id:u64)->&'a Worldgen{&*std::ptr::with_exposed_provenance::<Worldgen>(id as usize)}
    unsafe fn target(kind:i32,id:u64)->Option<Target>{if id==0{return None;}match kind{1=>Some(Target::Legacy(owned_legacy::ffi::retain(id))),2=>Some(Target::Xoroshiro(owned_source::ffi::retain(id))),_=>None}}
    pub(crate) unsafe fn kernel(id:u64,kind:i32,source:u64)->Option<Kernel>{Some(Kernel{owner:retain(id),target:target(kind,source)?})}
    #[no_mangle]pub extern "C" fn mattmc_worldgen_new()->u64{Arc::into_raw(Worldgen::new()).expose_provenance()as u64}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_release(id:u64){if id!=0{drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Worldgen>(id as usize)));}}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_count(id:u64)->i32{owner(id).count()}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_set_count(id:u64,value:i32){owner(id).count.store(value,Ordering::Relaxed);}
    /// Low32=result, high32=0 done/1 VM Legacy bits/2 VM word/3 collision.
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_next(id:u64,bits:i32,kind:i32,source:u64)->u64{
        let value=owner(id);value.bump();
        let Some(target)=target(kind,source)else{return(if kind==1||kind==3{1_u64}else{2_u64})<<32;};
        match target.next(bits){Ok(result)=>result as u32 as u64,Err(())=>3_u64<<32}
    }
    #[no_mangle]pub extern "C" fn mattmc_worldgen_word(bits:i32,value:i64)->i32{word(bits,value)}
    #[no_mangle]pub extern "C" fn mattmc_worldgen_seed_value(kind:i32,seed:i64,x:i32,z:i32,salt:i32)->i64{seed_value(kind,seed,x,z,salt)}
    #[no_mangle]pub extern "C" fn mattmc_worldgen_seed_new(kind:i32,seed:i64,x:i32,z:i32,salt:i32)->u64{
        if !(1..=4).contains(&kind){return 0;}Box::into_raw(Box::new(Seed::new(kind,seed,x,z,salt))).expose_provenance()as u64
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_seed_next(id:u64,response:i64)->i32{
        let seed=&mut*std::ptr::with_exposed_provenance_mut::<Seed>(id as usize);
        seed.words=match seed.advance(response){Effect::Seed(value)=>[1,value as u64],Effect::Long=>[2,0],Effect::Done(value)=>[0,value as u64]};i32::from(seed.words[0]!=0)
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_seed_release(id:u64){if id!=0{drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Seed>(id as usize)));}}
    #[no_mangle]pub extern "C" fn mattmc_worldgen_slime(seed:i64,x:i32,z:i32,salt:i64)->i64{
        seed.wrapping_add(x.wrapping_mul(x).wrapping_mul(4987142)as i64).wrapping_add(x.wrapping_mul(5947611)as i64)
            .wrapping_add((z.wrapping_mul(z)as i64).wrapping_mul(4392871)).wrapping_add(z.wrapping_mul(389711)as i64)^salt
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_kernel_new(id:u64,kind:i32,source:u64)->u64{kernel(id,kind,source).map_or(0,|value|Box::into_raw(Box::new(value)).expose_provenance()as u64)}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_kernel_next(id:u64,bits:i32)->u64{match(&*std::ptr::with_exposed_provenance::<Kernel>(id as usize)).next(bits){Ok(value)=>value as u32 as u64,Err(())=>3_u64<<32}}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_kernel_release(id:u64){if id!=0{drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Kernel>(id as usize)));}}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_weak_new(id:u64)->u64{Box::into_raw(Box::new(Arc::downgrade(&retain(id)))).expose_provenance()as u64}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_weak_count(id:u64)->u64{(&*std::ptr::with_exposed_provenance::<std::sync::Weak<Worldgen>>(id as usize)).strong_count()as u64}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_weak_release(id:u64){if id!=0{drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<std::sync::Weak<Worldgen>>(id as usize)));}}
    #[no_mangle]pub unsafe extern "C" fn mattmc_worldgen_statistics(out:*mut u64){for(i,value)in[&LIVE,&CREATED,&RETIRED,&FRAMES,&FRAME_CREATED,&FRAME_RETIRED].into_iter().enumerate(){out.add(i).write(value.load(Ordering::Relaxed));}}
}
#[cfg(test)]mod tests{
    use super::*;
    #[test]fn actual_kernel_owns_count_and_source_until_its_last_draw(){let source=owned_legacy::Source::raw_initial();source.reseed(42,true).unwrap();let source_weak=Arc::downgrade(&source);let owner=Worldgen::new();let weak=Arc::downgrade(&owner);let kernel=Kernel{owner,target:Target::Legacy(source)};assert_eq!(kernel.next(32),Ok(-1170105035));assert_eq!(kernel.owner.count(),1);drop(kernel);assert!(weak.upgrade().is_none());assert!(source_weak.upgrade().is_none());}
    #[test]fn vm_effect_count_is_committed_before_the_requested_word(){unsafe{let id=ffi::mattmc_worldgen_new();assert_eq!(ffi::mattmc_worldgen_next(id,32,4,0),2_u64<<32);assert_eq!(ffi::mattmc_worldgen_count(id),1);ffi::mattmc_worldgen_set_count(id,i32::MAX);assert_eq!(ffi::mattmc_worldgen_next(id,31,3,0),1_u64<<32);assert_eq!(ffi::mattmc_worldgen_count(id),i32::MIN);ffi::mattmc_worldgen_release(id);}}
    #[test]fn real_seed_producer_keeps_virtual_effect_order(){let mut seed=Seed::new(1,42,3,5,0);assert!(matches!(seed.advance(0),Effect::Seed(42)));assert!(matches!(seed.advance(0),Effect::Long));assert!(matches!(seed.advance(123),Effect::Long));assert!(matches!(seed.advance(456),Effect::Seed(value)if value==((3*123+5*457)^42)));assert!(matches!(seed.advance(0),Effect::Done(_)));}
    #[test]fn feature_seed_preserves_the_original_int_product_overflow(){let mut seed=Seed::new(3,i64::MAX,i32::MAX,i32::MAX,0);let expected=i64::MAX.wrapping_add(i32::MAX as i64).wrapping_sub(10000);assert!(matches!(seed.advance(0),Effect::Seed(value)if value==expected));}
    #[test]fn actual_ffi_base_and_kernel_release_both_owned_data_after_last_use(){unsafe{
        let id=ffi::mattmc_worldgen_new();let source=owned_legacy::ffi::mattmc_random_source_legacy_new();
        let watch=ffi::mattmc_worldgen_weak_new(id);let sw=Arc::downgrade(&owned_legacy::ffi::retain(source));
        let kernel=ffi::mattmc_worldgen_kernel_new(id,1,source);assert_ne!(kernel,0);
        ffi::mattmc_worldgen_release(id);owned_legacy::ffi::mattmc_random_source_legacy_release(source);
        assert_eq!(ffi::mattmc_worldgen_weak_count(watch),1);assert_eq!(sw.strong_count(),1);
        assert_eq!(ffi::mattmc_worldgen_kernel_next(kernel,32)>>32,0);assert_eq!(ffi::mattmc_worldgen_count(id),1);
        ffi::mattmc_worldgen_kernel_release(kernel);assert_eq!(ffi::mattmc_worldgen_weak_count(watch),0);assert_eq!(sw.strong_count(),0);ffi::mattmc_worldgen_weak_release(watch);
    }}
    #[test]fn real_word_extraction_keeps_java_unsigned_shift_and_i32_mask(){
        let value=0x923456789abcdef0_u64 as i64;
        for bits in [i32::MIN,-65,-33,-1,0,1,26,27,31,32,33,48,63,64,65,i32::MAX]{
            let expected=((value as u64).wrapping_shr((64_u32).wrapping_sub(bits as u32)))as i32;assert_eq!(word(bits,value),expected);
        }
    }

}
