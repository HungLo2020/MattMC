//! Authoritative retained DH column references. No point mirror or Java owner.
//! Point access/replacement keeps DH's existing caller exclusion requirement.
use super::mutable::{Column, Lease};

enum Entry { Missing, Empty, Unsupported, Native(Lease) }
pub(crate) struct Source {
    pub(super) metadata: std::sync::Arc<super::metadata::Metadata>,
    columns: Option<Box<[Entry]>>, identities: Option<Vec<usize>>, version: u64,
    identity: u64, remap: Vec<i32>, remap_revision: u64, remap_cached: bool,
    disjoint: Option<[u64; 3]>,
}
impl Source {
    #[inline(always)]
    pub(super) unsafe fn bounded_column_hash(&self,index:usize,work:&mut usize)->Option<i32> {
        match self.columns.as_ref().map(|columns| &columns[index]) {
            Some(Entry::Missing)=>Some(0), None|Some(Entry::Empty)=>Some(1),
            Some(Entry::Unsupported)=>None,
            Some(Entry::Native(lease))=> {
                let column=&*(lease.identity() as *const Column);
                let count=column.points().len();*work+=count;
                if count>4096||*work>65536 {None} else {Some(column.hash())}
            }
        }
    }
    /// One bounded pair proof, invalidated by either table's structural version.
    /// IDs never recycle; the cache retains no other source or native pointer.
    pub(super) fn disjoint_from(&mut self, source: &mut Self) -> bool {
        let proof=[self.version,source.identity,source.version];
        if self.disjoint==Some(proof) {return true;}
        self.disjoint=None;
        let identities=self.identities();
        if identities.windows(2).any(|p|p[0]==p[1]) {return false;}
        let mut a=0;
        for &id in source.identities() {
            while a<identities.len() && identities[a]<id {a+=1;}
            if identities.get(a)==Some(&id) {return false;}
        }
        self.disjoint=Some(proof);true
    }
    pub(super) fn version(&self) -> u64 { self.version }
    pub(super) fn mapping(&mut self, remap: &[i32]) -> [u64; 2] {
        // Retain at most 16KiB of remap proof per source. Larger maps still
        // copy normally, with a fresh stamp that cannot authorize reuse.
        if remap.len() > 4096 || !self.remap_cached || self.remap != remap {
            self.remap.clear(); self.remap_cached = remap.len() <= 4096;
            if self.remap_cached {
                self.remap.reserve_exact(remap.len()); self.remap.extend_from_slice(remap);
            }
            self.remap_revision = self.remap_revision.checked_add(1).unwrap_or_else(|| std::process::abort());
        }
        self.mapping_stamp()
    }
    pub(super) fn mapping_stamp(&self) -> [u64; 2] { [self.identity, self.remap_revision] }
    // Structural identity changes invalidate this bounded, source-owned proof.
    // Point edits/growth preserve primary identity and need no reconstruction.
    pub(super) fn identities(&mut self) -> &[usize] {
        if self.identities.is_none() {
            let mut ids: Vec<_> = (0..4096).filter_map(|i| self.identity(i)).collect();
            ids.sort_unstable(); self.identities = Some(ids);
        }
        self.identities.as_deref().unwrap()
    }
    #[inline] pub(super) fn owner(&self, index: usize) -> *mut Column {
        self.identity(index).map_or(std::ptr::null_mut(), |p| p as *mut Column)
    }
    #[inline] pub(super) unsafe fn column(&self, index: usize) -> Option<&[u64]> {
        if index>=4096{return None;}
        let Some(columns)=&self.columns else{return Some(&[]);};
        match columns.get(index)? {
            Entry::Missing | Entry::Empty => Some(&[]),
            Entry::Unsupported => None,
            Entry::Native(lease) => Some(lease.points()),
        }
    }
    #[inline] pub(super) fn present(&self, index: usize) -> bool { self.columns.as_ref().is_none_or(|columns| !matches!(columns[index], Entry::Missing)) }
    #[inline] pub(super) fn identity(&self, index: usize) -> Option<usize> {
        match self.columns.as_ref().map(|columns| &columns[index]) { Some(Entry::Native(lease)) => Some(lease.identity()), _ => None }
    }
}
#[no_mangle]
pub extern "C" fn mattmc_dh_source_create() -> *mut Source {
    Box::into_raw(Box::new(Source { metadata: std::sync::Arc::new(super::metadata::Metadata::new()), columns: None, identities: None, version: 0,
        identity: super::mutable::identity(), remap: Vec::new(), remap_revision: 0, remap_cached: false, disjoint:None }))
}
/// Empty/null facts, unsupported callback input, or a retained primary column.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_set(source: *mut Source, index: i32, kind: i32, column: *mut Column) -> i32 {
    if source.is_null() || !(0..4096).contains(&index) { return 0; }
    let entry = match kind {
        0 if column.is_null() => Entry::Missing,
        3 if column.is_null() => Entry::Empty,
        1 if column.is_null() => Entry::Unsupported,
        2 if !column.is_null() && column as usize % std::mem::align_of::<Column>() == 0 => Entry::Native(Lease::retain(column)),
        _ => return 0,
    };
    (*source).columns.get_or_insert_with(||(0..4096).map(|_|Entry::Empty).collect())[index as usize] = entry;
    (*source).identities = None; (*source).version = (*source).version.checked_add(1).unwrap_or_else(|| std::process::abort());
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_release(source: *mut Source) {
    if !source.is_null() { drop(Box::from_raw(source)); }
}
/// Bounded lazy Java projection query; the primary presence remains native.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_kind(source: *const Source,index: i32)->i32 {
    let Some(source)=source.as_ref() else {return -1;};
    if !(0..4096).contains(&index){return -1;}
    match source.columns.as_ref().map(|columns| &columns[index as usize]) {Some(Entry::Missing)=>0,None|Some(Entry::Empty)=>3,Some(Entry::Unsupported)=>1,Some(Entry::Native(_))=>2}
}
/// Closed Java readers retain only a compact presence snapshot, never points.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_missing(source:*const Source,output:*mut u64)->i32 {
    let Some(source)=source.as_ref() else{return 0;};
    if output.is_null() || output as usize%8!=0{return 0;}
    let output=std::slice::from_raw_parts_mut(output,64);output.fill(0);
    if source.columns.is_some(){for i in 0..4096 {if !source.present(i){output[i/64]|=1u64<<(i%64);}}}
    1
}
/// Verify the immutable producer hash without constructing empty Java lists.
/// Unsupported readers decline to the original Java validation sequence.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_original(source:*const Source,producer:*const super::Columns,
    pos:u64,generation:*const i8,compression:*const i8)->i32 {
    let (Some(source),Some(producer_ref))=(source.as_ref(),producer.as_ref()) else{return -1;};
    if generation.is_null()||compression.is_null(){return -1;}
    if source.columns.as_ref().is_some_and(|columns|columns.iter().any(|c|matches!(c,Entry::Unsupported))){return -1;}
    if pos!=producer_ref.pos{return 0;}
    let (ox,oz)=((producer_ref.chunk_x&3) as usize*16,(producer_ref.chunk_z&3) as usize*16);
    for x in 0..64 {for z in 0..64 {
        let i=x*64+z;let occupied=(ox..ox+16).contains(&x)&&(oz..oz+16).contains(&z);
        if *generation.add(i)!=if occupied{9}else{0}
            || *compression.add(i)!=if occupied{producer_ref.compression as i8}else{0}{return 0;}
        if occupied {
            let index=(x-ox)*16+z-oz;let first=producer_ref.offsets[index] as usize;
            let count=producer_ref.offsets[index+1] as usize-first;
            let owner=source.owner(i);
            if owner.is_null()||!(*owner).original_producer(producer,first,count){return 0;}
        } else if !source.present(i)||!source.column(i).unwrap().is_empty(){return 0;}
    }}
    1
}
/// Exact base-data hash over primary columns and borrowed semantic bytes.
/// This critical ABI has at most 65,536 point visits and no callbacks/allocation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_hash(source: *const Source, pos: u64,
    generation: *const i8, compression: *const i8, output: *mut i32) -> i32 {
    let Some(source) = source.as_ref() else { return 0; };
    if generation.is_null() || compression.is_null() || output.is_null() || output as usize % 4 != 0 { return 0; }
    fn fold(hash:i32,value:i32)->i32{hash.wrapping_mul(31).wrapping_add(value)}
    if source.columns.is_none() {
        const fn empty_data()->i32{let mut hash=1i32;let mut i=0;while i<4096{hash=hash.wrapping_mul(31).wrapping_add(1);i+=1;}hash}
        let (mut steps,mut modes)=(1i32,1i32);
        for i in 0..4096{steps=fold(steps,*generation.add(i) as i32);modes=fold(modes,*compression.add(i) as i32);}
        output.write(fold((pos^(pos>>32)) as i32,empty_data()).wrapping_mul(17).wrapping_add(steps).wrapping_mul(43).wrapping_add(modes));return 1;
    }
    let mut work = 0usize;
    let (mut data, mut steps, mut modes) = (1i32, 1i32, 1i32);
    for i in 0..4096 {
        let Some(column)=source.bounded_column_hash(i,&mut work) else {return 0;};
        data = fold(data, column); steps = fold(steps, *generation.add(i) as i32); modes = fold(modes, *compression.add(i) as i32);
    }
    output.write(fold((pos ^ pos >> 32) as i32, data).wrapping_mul(17).wrapping_add(steps).wrapping_mul(43).wrapping_add(modes));
    1
}

#[cfg(test)] mod tests {
    use super::*;
    use super::super::mutable::*;
    #[test]
    fn pair_proof_survives_point_growth_but_rejects_replacement_aliases_and_duplicate_recipients() {
        unsafe {
            let source=mattmc_dh_source_create();let target=mattmc_dh_source_create();
            let input=mattmc_dh_column_create([7].as_ptr(),1);let output=mattmc_dh_column_create([8].as_ptr(),1);
            assert_eq!(mattmc_dh_source_set(source,10,2,input),1);assert_eq!(mattmc_dh_source_set(target,20,2,output),1);
            assert!((*target).disjoint_from(&mut *source));let proof=(*target).disjoint;
            assert_eq!(mattmc_dh_column_add(input,1,9),1);
            assert!((*target).disjoint_from(&mut *source));assert_eq!((*target).disjoint,proof);
            assert_eq!(mattmc_dh_source_set(source,30,2,output),1);
            assert!(!(*target).disjoint_from(&mut *source));assert!((*target).disjoint.is_none());
            assert_eq!(mattmc_dh_source_set(source,30,0,std::ptr::null_mut()),1);
            assert!((*target).disjoint_from(&mut *source));
            assert_eq!(mattmc_dh_source_set(target,40,2,output),1);
            assert!(!(*target).disjoint_from(&mut *source));
            mattmc_dh_column_release(input);mattmc_dh_column_release(output);
            mattmc_dh_source_release(source);mattmc_dh_source_release(target);
        }
    }
    #[test]
    fn source_retains_primary_columns_across_aliases_growth_and_wrapper_release() {
        unsafe {
            let a = mattmc_dh_source_create(); let b = mattmc_dh_source_create();
            let column = mattmc_dh_column_create([7].as_ptr(), 1);
            assert_eq!(mattmc_dh_source_set(a, 0, 2, column), 1);
            assert_eq!(mattmc_dh_source_set(b, 0, 2, column), 1);
            for i in 1..1000 { assert_eq!(mattmc_dh_column_add(column, i, i as u64), 1); }
            mattmc_dh_column_release(column);
            assert_eq!((*a).column(0).unwrap().len(), 1000);
            assert_eq!((*b).column(0).unwrap()[999], 999);
            assert_eq!(mattmc_dh_source_set(a, 0, 0, std::ptr::null_mut()), 1);
            mattmc_dh_source_release(a);
            assert_eq!((*b).column(0).unwrap()[0], 7);
            assert_eq!(mattmc_dh_source_set(b, 0, 1, std::ptr::null_mut()), 1);
            assert!((*b).column(0).is_none());
            assert_eq!(mattmc_dh_source_set(b, 0, 9, std::ptr::null_mut()), 0);
            assert!((*b).column(0).is_none());
            mattmc_dh_source_release(b);
        }
    }
    #[test]
    fn cached_primary_identity_proof_invalidates_on_replacement_and_hash_declines_without_writes() {
        unsafe {
            let source=mattmc_dh_source_create(); let column=mattmc_dh_column_create([u64::MAX].as_ptr(),1);
            assert_eq!(mattmc_dh_source_set(source,0,2,column),1);
            assert_eq!((*source).identities(),[column as usize]);
            let cache=(*source).identities().as_ptr();
            assert_eq!(mattmc_dh_column_add(column,1,7),1);
            assert_eq!((*source).identities().as_ptr(),cache,"Point growth cannot invalidate a primary identity proof");
            assert_eq!(mattmc_dh_source_set(source,1,2,column),1);
            assert_eq!((*source).identities(),[column as usize,column as usize]);
            assert_eq!(mattmc_dh_source_set(source,0,0,std::ptr::null_mut()),1);
            assert_eq!((*source).identities(),[column as usize]);
            let bytes=vec![-128i8;4096]; let mut output=12345;
            assert_eq!(mattmc_dh_source_set(source,1,1,std::ptr::null_mut()),1);
            assert_eq!(mattmc_dh_source_hash(source,6,bytes.as_ptr(),bytes.as_ptr(),&mut output),0); assert_eq!(output,12345);
            let oversized=mattmc_dh_column_create(vec![0u64;4097].as_ptr(),4097);
            assert_eq!(mattmc_dh_source_set(source,1,2,oversized),1);
            assert_eq!(mattmc_dh_source_hash(source,6,bytes.as_ptr(),bytes.as_ptr(),&mut output),0); assert_eq!(output,12345);
            for i in 1..18 { assert_eq!(mattmc_dh_source_set(source,i,2,column),1); }
            let large=mattmc_dh_column_create(vec![0u64;4096].as_ptr(),4096);
            for i in 1..18 { assert_eq!(mattmc_dh_source_set(source,i,2,large),1); }
            assert_eq!(mattmc_dh_source_hash(source,6,bytes.as_ptr(),bytes.as_ptr(),&mut output),0); assert_eq!(output,12345);
            mattmc_dh_column_release(column); mattmc_dh_column_release(oversized); mattmc_dh_column_release(large); mattmc_dh_source_release(source);
        }
    }
    #[test]
    fn original_producer_admission_tracks_empty_spans_metadata_detachment_and_closed_presence() {
        use super::super::{Columns,ffi::mattmc_dh_columns_release};use std::sync::Arc;
        unsafe {
            let mut offsets=Box::new([1u32;257]);offsets[0]=0;
            let producer=Arc::new(Columns{pos:6,chunk_x:0,chunk_z:0,compression:128,points:vec![u64::MAX].into_boxed_slice(),offsets,materials:vec![].into_boxed_slice()});
            let expected=producer.source_hash();let weak=Arc::downgrade(&producer);let raw=Arc::into_raw(producer);
            let source=mattmc_dh_source_create();let mut generation=vec![0i8;4096];let mut compression=vec![0i8;4096];
            for x in 0..16 {for z in 0..16 {
                let i=x*64+z;let first=usize::from(x!=0||z!=0);let count=i32::from(x==0&&z==0);
                let column=mattmc_dh_column_shared(0,raw.cast(),first as i64,count);
                assert_eq!(mattmc_dh_source_set(source,i as i32,2,column),1);mattmc_dh_column_release(column);
                generation[i]=9;compression[i]=-128;
            }}
            assert_eq!(mattmc_dh_source_original(source,raw,6,generation.as_ptr(),compression.as_ptr()),1);
            let mut hash=0;assert_eq!(mattmc_dh_source_hash(source,6,generation.as_ptr(),compression.as_ptr(),&mut hash),1);assert_eq!(hash,expected);
            assert_eq!(mattmc_dh_source_kind(source,4095),3);assert_eq!(mattmc_dh_source_kind(source,1),2);
            compression[1]=127;assert_eq!(mattmc_dh_source_original(source,raw,6,generation.as_ptr(),compression.as_ptr()),0);compression[1]=-128;
            let empty=(*source).owner(1);assert_eq!(mattmc_dh_column_make_owned(empty),1);
            assert_eq!(mattmc_dh_source_original(source,raw,6,generation.as_ptr(),compression.as_ptr()),0,"Empty detachment invalidates producer ownership too");
            assert_eq!(mattmc_dh_source_set(source,4095,0,std::ptr::null_mut()),1);
            let mut missing=[0u64;64];assert_eq!(mattmc_dh_source_missing(source,missing.as_mut_ptr()),1);
            assert!(missing[..63].iter().all(|&v|v==0));assert_eq!(missing[63],1<<63);
            mattmc_dh_source_release(source);assert_eq!(weak.strong_count(),1,"Closing the table releases every primary producer lease");
            mattmc_dh_columns_release(raw as *mut Columns);assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn immutable_producer_lease_shares_points_then_detaches_and_releases_producer() {
        use super::super::{Columns, ffi::mattmc_dh_columns_release};
        use std::sync::Arc;
        unsafe {
            let producer = Arc::new(Columns { pos: 6, chunk_x: 0, chunk_z: 0, compression: 0,
                points: vec![10, 20, 30].into_boxed_slice(), offsets: Box::new([0; 257]),
                materials: vec![].into_boxed_slice() });
            let weak = Arc::downgrade(&producer);
            let expected = producer.points.as_ptr().add(1);
            let raw = Arc::into_raw(producer);
            let column = mattmc_dh_column_shared(0, raw.cast(), 1, 2);
            assert!(!column.is_null());
            let source = mattmc_dh_source_create();
            assert_eq!(mattmc_dh_source_set(source, 4, 2, column), 1);
            mattmc_dh_columns_release(raw as *mut Columns);
            assert!(weak.upgrade().is_some());
            assert_eq!((*source).column(4).unwrap().as_ptr(), expected);
            assert_eq!(mattmc_dh_column_make_owned(column), 1);
            assert!(weak.upgrade().is_none(), "detached columns cannot retain their producer");
            assert_eq!(mattmc_dh_column_set(column, 0, 99), 1);
            mattmc_dh_column_release(column);
            assert_eq!((*source).column(4).unwrap(), [99, 30]);
            mattmc_dh_source_release(source);
        }
    }
}
