//! Authoritative retained DH column references. No point mirror or Java owner.
//! Point access/replacement keeps DH's existing caller exclusion requirement.
use super::{contributors::Snapshot, mutable::{Column, Lease}};
use std::sync::Arc;

enum Entry { Missing, Empty, Native(Lease) }
/// A source's retained four-column semantic footprints, one optional cell each.
pub(super) type Footprints = Option<Box<[Option<Arc<Snapshot>>]>>;
/// Closed compatibility values retain CPU leases and footprints, without a
/// live source table, metadata, caches or eagerly constructed Java readers.
pub(crate) struct Closed { columns: Option<Box<[Entry]>>, footprints: Footprints }
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_close_projection(source: *mut Source) -> *mut Closed {
    let Some(source)=source.as_mut() else{return std::ptr::null_mut();};
    let columns=source.columns.as_ref().map(|columns|columns.iter().map(|entry|match entry {
        Entry::Missing=>Entry::Missing,Entry::Empty=>Entry::Empty,
        Entry::Native(lease)=>Entry::Native(Lease::retain(lease.identity() as *mut Column)),
    }).collect());
    Box::into_raw(Box::new(Closed { columns, footprints: source.footprints.clone() }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_closed_kind(closed: *const Closed, index: i32) -> i32 {
    let Some(closed)=closed.as_ref() else{return -1;};if !(0..4096).contains(&index){return -1;}
    match closed.columns.as_ref().map(|c|&c[index as usize]) {
        Some(Entry::Missing)=>0,None|Some(Entry::Empty)=>3,Some(Entry::Native(_))=>2,
    }
}
/// A closed source's footprint at `index`, retained for the caller, or null.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_closed_footprint(closed: *const Closed, index: i32) -> *const Snapshot {
    let Some(closed)=closed.as_ref() else{return std::ptr::null();};if !(0..4096).contains(&index){return std::ptr::null();}
    footprint_lease(&closed.footprints, index as usize)
}
fn footprint_lease(footprints: &Footprints, index: usize) -> *const Snapshot {
    footprints.as_ref().and_then(|cells| cells[index].clone()).map_or(std::ptr::null(), Arc::into_raw)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_closed_column(closed: *const Closed, index: i32) -> *mut Column {
    let Some(closed)=closed.as_ref() else{return std::ptr::null_mut();};if !(0..4096).contains(&index){return std::ptr::null_mut();}
    match closed.columns.as_ref().map(|c|&c[index as usize]) {
        Some(Entry::Native(lease))=>Lease::retain(lease.identity() as *mut Column).into_raw(),_=>std::ptr::null_mut(),
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_closed_release(closed: *mut Closed) {if !closed.is_null(){drop(Box::from_raw(closed));}}
pub(crate) struct Source {
    pub(super) metadata: std::sync::Arc<super::metadata::Metadata>,
    columns: Option<Box<[Entry]>>, identities: Option<Vec<usize>>, version: u64,
    identity: u64, remap: Vec<i32>, remap_revision: u64, remap_cached: bool,
    disjoint: Option<[u64; 3]>,
    readers: [u64; 64],
    footprints: Footprints, footprint_count: usize,
}
impl Source {
    pub(super) fn footprint(&self, index: usize) -> Option<&Arc<Snapshot>> {
        self.footprints.as_ref().and_then(|cells| cells[index].as_ref())
    }
    pub(super) fn footprint_count(&self) -> usize { self.footprint_count }
    /// Install or remove one cell's footprint, keeping the count and the Java
    /// presence plane (metadata plane 3) in step.
    pub(super) fn set_footprint(&mut self, index: usize, value: Option<Arc<Snapshot>>) {
        if value.is_none() && self.footprints.is_none() { return; }
        let cells = self.footprints.get_or_insert_with(|| (0..4096).map(|_| None).collect());
        let (old, new) = (cells[index].is_some(), value.is_some());
        cells[index] = value;
        self.footprint_count = self.footprint_count + usize::from(new) - usize::from(old);
        unsafe { self.metadata.plane_mut(3)[index] = i8::from(new); }
    }
    /// Clear-before-resize is observable on allocation failure. Immutable spans
    /// detach without copying points that the persistence decoder will discard.
    pub(super) unsafe fn codec_reset(&mut self, index: usize, count: usize) -> bool {
        let mut owner = self.owner(index);
        if owner.is_null() || !(*owner).is_owned() {
            if count == 0 && self.column(index).is_some_and(|p| p.is_empty()) { return true; }
            self.adopt(index, Box::new(Column::new(Vec::new())));
            owner = self.owner(index);
        }
        let points = Column::points_mut(owner);
        points.clear();
        if points.try_reserve(count).is_err() { return false; }
        points.resize(count, 0);
        true
    }
    pub(super) unsafe fn codec_set(&mut self, index: usize, point: usize, value: u64) {
        Column::points_mut(self.owner(index))[point] = value;
    }
    pub(super) unsafe fn codec_len(&self,index:usize)->i64 {
        let owner=self.owner(index);if owner.is_null(){0}else{(*owner).logical_len()}
    }
    pub(super) unsafe fn codec_negative(&mut self,index:usize,count:i32) {
        if self.owner(index).is_null(){self.adopt(index,Box::new(Column::new(Vec::new())));}
        (*self.owner(index)).negative_size(count);
    }
    #[inline] pub(super) fn has_reader(&self,index:usize)->bool {self.readers[index/64]&(1u64<<(index%64))!=0}
    fn mark_reader(&mut self,index:usize,present:bool) {
        let bit=1u64<<(index%64);if present{self.readers[index/64]|=bit;}else{self.readers[index/64]&=!bit;}
    }
    /// Replace a column with new points (an empty one needs no vector) and its
    /// known list hash. The old column's Java reader, if any, keeps its own
    /// lease; Java drops that binding, so the reader mark is cleared.
    pub(super) fn replace(&mut self, index: usize, points: Vec<u64>, hash: i32) {
        if points.is_empty() {
            self.columns.get_or_insert_with(||(0..4096).map(|_|Entry::Empty).collect())[index] = Entry::Empty;
            self.identities = None;
            self.version = self.version.checked_add(1).unwrap_or_else(|| std::process::abort());
        } else {
            let column = Box::new(Column::new(points));
            unsafe { Column::publish_hash(&*column as *const Column as *mut Column, hash); }
            self.adopt(index, column);
        }
        self.mark_reader(index, false);
    }
    /// Transfer a newly committed vector directly into the authoritative table.
    /// No Java reader or second point allocation is required for publication.
    pub(super) fn adopt(&mut self, index: usize, column: Box<Column>) {
        self.columns.get_or_insert_with(||(0..4096).map(|_|Entry::Empty).collect())[index] = Entry::Native(Lease::adopt(column));
        self.identities = None;
        self.version = self.version.checked_add(1).unwrap_or_else(|| std::process::abort());
    }
    #[inline(always)]
    pub(super) unsafe fn bounded_column_hash(&self,index:usize,work:&mut usize)->Option<i32> {
        match self.columns.as_ref().map(|columns| &columns[index]) {
            Some(Entry::Missing)=>Some(0), None|Some(Entry::Empty)=>Some(1),
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
            Entry::Native(lease) => if (*(lease.identity() as *const Column)).invalid_size(){None}else{Some(lease.points())},
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
        identity: super::mutable::identity(), remap: Vec::new(), remap_revision: 0, remap_cached: false, disjoint:None,readers:[0;64],
        footprints: None, footprint_count: 0 }))
}
/// Empty/null facts or a retained primary column. Java copies every other list
/// into a native column before binding it, so no Java-held binding exists.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_set(source: *mut Source, index: i32, kind: i32, column: *mut Column) -> i32 {
    if source.is_null() || !(0..4096).contains(&index) { return 0; }
    let entry = match kind {
        0 if column.is_null() => Entry::Missing,
        3 if column.is_null() => Entry::Empty,
        2 if !column.is_null() && column as usize % std::mem::align_of::<Column>() == 0 => Entry::Native(Lease::retain(column)),
        _ => return 0,
    };
    (*source).columns.get_or_insert_with(||(0..4096).map(|_|Entry::Empty).collect())[index as usize] = entry;
    (*source).mark_reader(index as usize,kind!=0);
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
    match source.columns.as_ref().map(|columns| &columns[index as usize]) {Some(Entry::Missing)=>0,None|Some(Entry::Empty)=>3,Some(Entry::Native(_))=>2}
}
/// Independent CPU lease for a demand-created reader. The caller adopts or
/// releases this reference; source closure cannot invalidate the reader.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_retain_column(source: *mut Source, index: i32) -> *mut Column {
    let Some(source)=source.as_mut() else{return std::ptr::null_mut();};
    if !(0..4096).contains(&index){return std::ptr::null_mut();}
    let owner=source.owner(index as usize);
    if !owner.is_null(){source.mark_reader(index as usize,true);}
    if owner.is_null(){owner}else{Lease::retain(owner).into_raw()}
}
/// Register an escaped present-empty Java identity without creating a vector.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_reader(source:*mut Source,index:i32)->i32 {
    let Some(source)=source.as_mut() else{return 0;};
    if !(0..4096).contains(&index)||!source.present(index as usize){return 0;}
    source.mark_reader(index as usize,true);1
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
/// Close projects native values once and retains the original null/empty facts.
/// Two compact bitsets avoid a Java downcall for every unused empty slot.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_reader_masks(source:*const Source,output:*mut u64)->i32 {
    let Some(source)=source.as_ref() else{return 0;};
    if output.is_null()||output as usize%8!=0{return 0;}
    let output=std::slice::from_raw_parts_mut(output,128);output.fill(0);
    if let Some(columns)=&source.columns {
        for (i,column) in columns.iter().enumerate() {
            let plane=match column {Entry::Missing=>0,Entry::Native(_)=>64,_=>continue};
            output[plane+i/64]|=1u64<<(i%64);
        }
    }
    1
}
/// Verify the immutable producer hash without constructing empty Java lists.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_original(source:*const Source,producer:*const super::Columns,
    pos:u64,generation:*const i8,compression:*const i8)->i32 {
    let (Some(source),Some(producer_ref))=(source.as_ref(),producer.as_ref()) else{return -1;};
    if generation.is_null()||compression.is_null(){return -1;}
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

/// Replace one cell's footprint with a capture of four spans (count -1 is a
/// null column), each clipped to `limit` points. Count 0 for every span with
/// null pointers is valid. Returns 1, or 0 for malformed input.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_footprint_set(source: *mut Source, index: i32,
    spans: *const usize, limit: i32) -> i32 {
    let Some(source)=source.as_mut() else{return 0;};
    if !(0..4096).contains(&index) || spans.is_null() || spans as usize % 8 != 0 || !(0..=4096).contains(&limit) { return 0; }
    let raw = std::slice::from_raw_parts(spans, 8);
    let mut columns = [None; 4];
    for i in 0..4 {
        let (pointer, count) = (raw[i * 2], raw[i * 2 + 1] as isize);
        if count == -1 { continue; }
        if count < 0 || count != 0 && (pointer == 0 || pointer % 8 != 0) { return 0; }
        columns[i] = Some(if count == 0 { &[][..] } else { std::slice::from_raw_parts(pointer as *const u64, count as usize) });
    }
    let Some(snapshot) = super::contributors::capture(columns, None, limit as usize) else { return 0; };
    source.set_footprint(index as usize, Some(Arc::new(snapshot))); 1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_footprint_clear(source: *mut Source, index: i32) -> i32 {
    let Some(source)=source.as_mut() else{return 0;};
    if !(0..4096).contains(&index) { return 0; }
    source.set_footprint(index as usize, None); 1
}
/// The footprint at `index`, retained for the caller (released through
/// `mattmc_dh_contributors_release`), or null when the cell has none.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_footprint(source: *const Source, index: i32) -> *const Snapshot {
    let Some(source)=source.as_ref() else{return std::ptr::null();};
    if !(0..4096).contains(&index) { return std::ptr::null(); }
    footprint_lease(&source.footprints, index as usize)
}
/// Decode a whole decompressed V4 contributor sidecar into this source's
/// footprints, in saved order. `result` receives `[status, value]` as
/// `mattmc_dh_contributors_decode` reports it; cells before a stop are applied.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_footprints_decode(source: *mut Source, bytes: *const u8, len: i32, result: *mut i64) -> i32 {
    let Some(source)=source.as_mut() else{return 0;};
    if len < 0 || len > 0 && bytes.is_null() || result.is_null() || result as usize % 8 != 0 { return 0; }
    let input = if len == 0 { &[][..] } else { std::slice::from_raw_parts(bytes, len as usize) };
    let result = std::slice::from_raw_parts_mut(result, 2);
    let mut points = Vec::new();
    let Ok((cells, status, value)) = super::contributors::parse(input, &mut points) else {
        result.copy_from_slice(&[3, 0]); return 1;
    };
    for (cell, columns) in cells {
        let spans = columns.map(|c| c.map(|(start, n)| &points[start..start + n]));
        let Some(snapshot) = super::contributors::capture(spans, None, 4096) else { result.copy_from_slice(&[3, 0]); return 1; };
        source.set_footprint(cell, Some(Arc::new(snapshot)));
    }
    result.copy_from_slice(&[status as i64, value as i64]); 1
}
/// The complete base-data hash (`FullDataSourceV2.generateHashCode`), with no
/// point budget. Tagged: bit 32 set on success; 0 when a column has an
/// invalid (negative) logical size, whose Java hash cannot complete.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_hash_full(source: *const Source, pos: u64) -> u64 {
    let Some(source) = source.as_ref() else { return 0; };
    full_hash(&source.columns, &source.metadata, pos)
}
unsafe fn full_hash(columns: &Option<Box<[Entry]>>, metadata: &super::metadata::Metadata, pos: u64) -> u64 {
    fn fold(hash:i32,value:i32)->i32{hash.wrapping_mul(31).wrapping_add(value)}
    let (generation, compression) = (metadata.plane(0), metadata.plane(1));
    let (mut data, mut steps, mut modes) = (1i32, 1i32, 1i32);
    for i in 0..4096 {
        let column = match columns.as_ref().map(|columns| &columns[i]) {
            Some(Entry::Missing) => 0, None | Some(Entry::Empty) => 1,
            Some(Entry::Native(lease)) => {
                let column = &*(lease.identity() as *const Column);
                if column.invalid_size() { return 0; }
                column.hash()
            }
        };
        data = fold(data, column); steps = fold(steps, generation[i] as i32); modes = fold(modes, compression[i] as i32);
    }
    let hash = fold((pos ^ pos >> 32) as i32, data).wrapping_mul(17).wrapping_add(steps).wrapping_mul(43).wrapping_add(modes);
    (1u64 << 32) | hash as u32 as u64
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
            assert_eq!(mattmc_dh_source_set(b, 0, 1, std::ptr::null_mut()), 0, "no Java-held binding kind exists");
            assert_eq!(mattmc_dh_source_set(b, 0, 9, std::ptr::null_mut()), 0);
            assert_eq!((*b).column(0).unwrap()[0], 7, "a rejected binding changes nothing");
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
