//! Canonical positional factory data and real derived-source production.
use super::{owned_legacy, owned_source, random::{position_seed, Positional}};
use std::sync::{atomic::{AtomicU64, Ordering}, Arc};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);

pub(crate) struct Factory { data: Positional }
pub(crate) enum Source {
    Legacy(Arc<owned_legacy::Source>),
    Xoroshiro(Arc<owned_source::Source>),
}
impl Factory {
    pub(crate) fn new(kind:i32,a:i64,b:i64)->Option<Arc<Self>> {
        let data=Positional::from_abi(kind,a,b)?;
        LIVE.fetch_add(1,Ordering::Relaxed); CREATED.fetch_add(1,Ordering::Relaxed);
        Some(Arc::new(Self{data}))
    }
    pub(crate) fn seed(&self,index:i32)->i64 {
        match self.data {
            Positional::Legacy{seed}=>if index==0{seed}else{0},
            Positional::Xoroshiro{lo,hi}=>if index==0{lo}else{hi},
        }
    }
    pub(crate) fn kind(&self)->i32 {match self.data{Positional::Xoroshiro{..}=>1,Positional::Legacy{..}=>2}}
    pub(crate) fn at(&self,x:i32,y:i32,z:i32)->Source {
        let position=position_seed(x,y,z);
        match self.data {
            Positional::Legacy{seed}=>Source::Legacy(owned_legacy::Source::seeded(seed^position)),
            Positional::Xoroshiro{lo,hi}=>Source::Xoroshiro(owned_source::Source::from_pair(lo^position,hi)),
        }
    }
    pub(crate) fn from_seed(&self,seed:i64)->Source {
        match self.data {
            // Original Legacy fromSeed deliberately ignores the factory seed.
            Positional::Legacy{..}=>Source::Legacy(owned_legacy::Source::seeded(seed)),
            Positional::Xoroshiro{lo,hi}=>Source::Xoroshiro(owned_source::Source::from_pair(lo^seed,hi^seed)),
        }
    }
    pub(crate) fn from_hash(&self,chars:&[u16])->Source {
        match self.data {
            Positional::Legacy{seed}=>{
                let hash=chars.iter().fold(0_i32,|hash,&ch|hash.wrapping_mul(31).wrapping_add(ch as i32));
                Source::Legacy(owned_legacy::Source::seeded(seed^hash as i64))
            }
            Positional::Xoroshiro{lo,hi}=>{
                let digest=md5(&java_utf8(chars));
                let a=i64::from_be_bytes(digest[..8].try_into().unwrap());
                let b=i64::from_be_bytes(digest[8..].try_into().unwrap());
                Source::Xoroshiro(owned_source::Source::from_pair(lo^a,hi^b))
            }
        }
    }
}
impl Drop for Factory {fn drop(&mut self){LIVE.fetch_sub(1,Ordering::Relaxed);RETIRED.fetch_add(1,Ordering::Relaxed);}}
impl Source {
    fn into_raw(self)->u64 {match self{
        Self::Legacy(value)=>Arc::into_raw(value).expose_provenance()as u64,
        Self::Xoroshiro(value)=>Arc::into_raw(value).expose_provenance()as u64,
    }}
}
// String.getBytes(UTF_8) replaces each malformed UTF-16 unit with '?' rather
// than the Unicode replacement character. The VM supplies only actual units.
fn java_utf8(chars:&[u16])->Vec<u8> {
    let mut bytes=Vec::new();
    for ch in char::decode_utf16(chars.iter().copied()) {
        match ch {
            Ok(ch)=>{let mut buffer=[0;4];bytes.extend_from_slice(ch.encode_utf8(&mut buffer).as_bytes());}
            Err(_)=>bytes.push(b'?'),
        }
    }
    bytes
}
fn md5(input:&[u8])->[u8;16] {
    const S:[u32;64]=[
        7,12,17,22,7,12,17,22,7,12,17,22,7,12,17,22,
        5,9,14,20,5,9,14,20,5,9,14,20,5,9,14,20,
        4,11,16,23,4,11,16,23,4,11,16,23,4,11,16,23,
        6,10,15,21,6,10,15,21,6,10,15,21,6,10,15,21];
    const K:[u32;64]=[
        0xd76aa478,0xe8c7b756,0x242070db,0xc1bdceee,0xf57c0faf,0x4787c62a,0xa8304613,0xfd469501,
        0x698098d8,0x8b44f7af,0xffff5bb1,0x895cd7be,0x6b901122,0xfd987193,0xa679438e,0x49b40821,
        0xf61e2562,0xc040b340,0x265e5a51,0xe9b6c7aa,0xd62f105d,0x02441453,0xd8a1e681,0xe7d3fbc8,
        0x21e1cde6,0xc33707d6,0xf4d50d87,0x455a14ed,0xa9e3e905,0xfcefa3f8,0x676f02d9,0x8d2a4c8a,
        0xfffa3942,0x8771f681,0x6d9d6122,0xfde5380c,0xa4beea44,0x4bdecfa9,0xf6bb4b60,0xbebfbc70,
        0x289b7ec6,0xeaa127fa,0xd4ef3085,0x04881d05,0xd9d4d039,0xe6db99e5,0x1fa27cf8,0xc4ac5665,
        0xf4292244,0x432aff97,0xab9423a7,0xfc93a039,0x655b59c3,0x8f0ccc92,0xffeff47d,0x85845dd1,
        0x6fa87e4f,0xfe2ce6e0,0xa3014314,0x4e0811a1,0xf7537e82,0xbd3af235,0x2ad7d2bb,0xeb86d391];
    let mut bytes=input.to_vec();let bits=(input.len()as u64).wrapping_mul(8);
    bytes.push(0x80);while bytes.len()%64!=56{bytes.push(0);}bytes.extend_from_slice(&bits.to_le_bytes());
    let mut state=[0x67452301_u32,0xefcdab89,0x98badcfe,0x10325476];
    for block in bytes.chunks_exact(64) {
        let words:[u32;16]=std::array::from_fn(|i|u32::from_le_bytes(block[i*4..i*4+4].try_into().unwrap()));
        let [mut a,mut b,mut c,mut d]=state;
        for i in 0..64 {
            let (f,g)=match i {
                0..=15=>((b&c)|(!b&d),i),
                16..=31=>((d&b)|(!d&c),(5*i+1)%16),
                32..=47=>(b^c^d,(3*i+5)%16),
                _=>(c^(b|!d),(7*i)%16),
            };
            let next=b.wrapping_add(a.wrapping_add(f).wrapping_add(K[i]).wrapping_add(words[g]).rotate_left(S[i]));
            a=d;d=c;c=b;b=next;
        }
        state[0]=state[0].wrapping_add(a);state[1]=state[1].wrapping_add(b);
        state[2]=state[2].wrapping_add(c);state[3]=state[3].wrapping_add(d);
    }
    let mut result=[0;16];for(i,value)in state.into_iter().enumerate(){result[i*4..i*4+4].copy_from_slice(&value.to_le_bytes());}result
}
pub(crate) mod ffi {
    use super::*;
    pub(crate) unsafe fn retain(id:u64)->Arc<Factory> {
        let ptr=std::ptr::with_exposed_provenance::<Factory>(id as usize);
        Arc::increment_strong_count(ptr);Arc::from_raw(ptr)
    }
    unsafe fn factory<'a>(id:u64)->&'a Factory{&*std::ptr::with_exposed_provenance::<Factory>(id as usize)}
    #[no_mangle]pub extern "C" fn mattmc_positional_new(kind:i32,a:i64,b:i64)->u64 {
        Factory::new(kind,a,b).map_or(0,|value|Arc::into_raw(value).expose_provenance()as u64)
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_release(id:u64) {
        if id!=0{drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Factory>(id as usize)));}
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_source_release(kind:i32,id:u64) {
        if id==0{return;}
        match kind {
            1=>drop(Arc::from_raw(std::ptr::with_exposed_provenance::<owned_source::Source>(id as usize))),
            2=>drop(Arc::from_raw(std::ptr::with_exposed_provenance::<owned_legacy::Source>(id as usize))),
            _=>{},
        }
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_seed(id:u64,index:i32)->i64{factory(id).seed(index)}
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_at(id:u64,x:i32,y:i32,z:i32)->u64{factory(id).at(x,y,z).into_raw()}
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_from_seed(id:u64,seed:i64)->u64{factory(id).from_seed(seed).into_raw()}
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_from_hash(id:u64,chars:*const u16,len:u64)->u64 {
        if len>isize::MAX as u64/2||chars.is_null()&&len!=0{return 0;}
        let chars=if len==0{&[]}else{std::slice::from_raw_parts(chars,len as usize)};
        factory(id).from_hash(chars).into_raw()
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_weak_new(id:u64)->u64 {
        Box::into_raw(Box::new(Arc::downgrade(&retain(id)))).expose_provenance()as u64
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_weak_count(id:u64)->u64 {
        (&*std::ptr::with_exposed_provenance::<std::sync::Weak<Factory>>(id as usize)).strong_count()as u64
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_weak_release(id:u64) {
        if id!=0{drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<std::sync::Weak<Factory>>(id as usize)));}
    }
    #[no_mangle]pub unsafe extern "C" fn mattmc_positional_statistics(out:*mut u64){
        for(i,value)in[&LIVE,&CREATED,&RETIRED].into_iter().enumerate(){out.add(i).write(value.load(Ordering::Relaxed));}
    }
}
#[cfg(test)]mod tests {
    use super::*;
    #[test]fn md5_original_vectors_include_multiple_blocks() {
        for(text,expected)in[
            ("","d41d8cd98f00b204e9800998ecf8427e"),
            ("abc","900150983cd24fb0d6963f7d28e17f72"),
            ("message digest","f96b697d7cb7938d525a2f31aaf161d0"),
            ("abcdefghijklmnopqrstuvwxyz","c3fcd3d76192e4007dfb496cca67e13b"),
            ("12345678901234567890123456789012345678901234567890123456789012345678901234567890","57edf4a22be3c955ac49da2e2107b67a"),
        ]{assert_eq!(md5(text.as_bytes()).iter().map(|v|format!("{v:02x}")).collect::<String>(),expected);}
    }
    #[test]fn malformed_utf16_uses_original_java_question_mark_replacement(){
        assert_eq!(java_utf8(&[0xd800]),b"?");assert_eq!(java_utf8(&[0xdc00,0xd800]),b"??");
        assert_eq!(java_utf8(&[0x03bb,0xd83d,0xde00]),"λ😀".as_bytes());
    }
    #[test]fn legacy_from_seed_ignores_factory_seed_and_child_survives_factory_retirement(){
        let factory=Factory::new(2,12345,0).unwrap();let weak=Arc::downgrade(&factory);
        let Source::Legacy(child)=factory.from_seed(42)else{panic!()};drop(factory);assert!(weak.upgrade().is_none());
        let mut control=super::super::random::Legacy::new(42);
        for _ in 0..32{assert_eq!(child.next(32).unwrap(),control.next(32));}
    }
    #[test]fn actual_xoro_child_is_independent_of_factory_and_normalizes_only_public_zero_pair(){
        let factory=Factory::new(1,42,42).unwrap();let Source::Xoroshiro(child)=factory.from_seed(42)else{panic!()};
        drop(factory);let mut control=super::super::random::Xoroshiro::new(0,0);
        for _ in 0..32{assert_eq!(child.with(|value|value.next_long()),control.next_long());}
    }
    #[test]fn actual_native_factory_coordinate_source_uses_existing_position_overflow(){
        for kind in[1,2]{let factory=Factory::new(kind,i64::MIN,i64::MAX).unwrap();let mut control=factory.data.at(i32::MIN,i32::MAX,-1);
            match factory.at(i32::MIN,i32::MAX,-1){
                Source::Legacy(child)=>for _ in 0..32{assert_eq!(child.next(32).unwrap(),match &mut control{super::super::random::Random::Legacy(value)=>value.next(32),_=>panic!()});},
                Source::Xoroshiro(child)=>for _ in 0..32{assert_eq!(child.with(|value|value.next_long()),match &mut control{super::super::random::Random::Xoroshiro(value)=>value.next_long(),_=>panic!()});},
            }
        }
    }
}
