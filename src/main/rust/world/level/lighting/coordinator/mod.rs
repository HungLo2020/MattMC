//! Per-engine lighting policy. Continuations contain indices/typed owners,
//! never a borrowed Java object, map guard or native lock across an effect.
pub(super) mod ffi;
mod level;
mod ordered;
#[cfg(test)]
mod tests;

use super::{layers::Layer, maps::{Entry, ffi::Owner as LightMap}, propagation::{self, work::{Queues, CoordinatorProjection as EngineProjection,LevelFollowers}}};
use ordered::{Cursor, Fault, Table, NONE};
use std::{collections::HashMap, sync::Arc};

const NODES: usize = 0;
const STATES: usize = 1;
const ENABLED: usize = 2;
const CHANGED: usize = 3;
const AFFECTED: usize = 4;
const QUEUED: usize = 5;
const RETAINED: usize = 6;
const REMOVING: usize = 7;

fn sx(key: i64) -> i32 { (key >> 42) as i32 }
fn sy(key: i64) -> i32 { (key << 44 >> 44) as i32 }
fn sz(key: i64) -> i32 { (key << 22 >> 42) as i32 }
fn column(key: i64) -> i64 { key & !0xfffff }
fn offset(key: i64, x: i32, y: i32, z: i32) -> i64 {
    propagation::section_pos(sx(key).wrapping_add(x), sy(key).wrapping_add(y), sz(key).wrapping_add(z))
}
fn section(pos: i64) -> i64 {
    propagation::section_pos(propagation::block_x(pos) >> 4, propagation::block_y(pos) >> 4, propagation::block_z(pos) >> 4)
}
fn index(pos: i64) -> i32 {
    (propagation::block_y(pos) & 15) << 8 | (propagation::block_z(pos) & 15) << 4 | propagation::block_x(pos) & 15
}
fn id(entry: &Option<Arc<Entry>>) -> u32 { entry.as_ref().map_or(0, |entry| entry.id) }
fn same(a: &Option<Arc<Entry>>, b: &Option<Arc<Entry>>) -> bool {
    match (a, b) { (None, None) => true, (Some(a), Some(b)) => a.identity == b.identity, _ => false }
}

#[derive(Debug)]
enum Error { Table(Fault), Neighbours, MissingOwner, Unsupported }
impl From<Fault> for Error { fn from(value: Fault) -> Self { Self::Table(value) } }

/// Each returned effect is completed by Java before the next step. A throwing
/// effect abandons only its continuation, retaining the exact committed prefix.
enum Effect {
    Done(i64),
    Repeat(u32),
    Copy(u32),
    Read(u32, i32),
    Write(u32, i32, i32),
    Publish(Arc<LightMap>),
    Notify(i64),
    BlockState(i64),
    BlockEmission,
    BlockEmpty,
    SkySource(i32, i32, i32),
    Propagate,
    ClearChunkCache,
    InstallResult(u32, usize),
    CustomNode(i64),
    CollectionCall(i32,i64,u32),
    HighestSource(i32,i32),
    LayerEmpty(u32),
    LayerFill(u32,i32),
    BlockSources(i32,i32),
    SkySources(i32,i32),
}

struct CollectionEdit {
    which:usize,kind:i32,key:i64,pos:i32,input:u32,
    input_entry:Option<Arc<Entry>>,held_old:Option<Arc<Entry>>,
}
struct ValueSearch {
    which:usize,wanted:u32,phase:u8,pos:i32,
    keys:Arc<ordered::KeyGeneration>,values:Arc<ordered::ValueGeneration>,
}

enum Op {
    Enable(i64,bool),
    EnableHighest(i64),
    EnableWalk(i64,i32,i32),
    EnableTarget,
    EnableFill(Arc<Entry>),
    EnableFilled(Arc<Entry>),
    Sources(i64),
    SeedSky(i64,i32,i32,Box<[i32]>),
    ValuesStart(usize,u32),
    ValuesSelect(ValueSearch),
    ValuesCompared(ValueSearch),
    CollectionStart(CollectionEdit),
    CollectionApply(CollectionEdit),
    Status(i64, bool),
    PutState(i64, u8),
    Neighbours(i64, usize, i32),
    InstallCreated(i64),
    Initialized(i64),
    Reconcile,
    RemoveWalk(Cursor),
    RemovedWalk(Cursor),
    QueuedWalk(Cursor),
    Publish,
    Published,
    NotifyWalk(Cursor),
    WriteStored(i64, i32),
    WriteTarget(i64, i32),
    Affected(i64),
    Writable(i64),
    Copied(i64, bool),
    ReturnEntry,
    ReadStored(i64),
    RememberRead,
    Run,
    NodeWalk(Cursor),
    NodesDone,
    AfterPropagation,
    Node(i64),
    BlockChecked(i64),
    BlockCustomEmission(i64),
    BlockLevel(i64, i32, Option<bool>),
    BlockEnqueue(i64, i32, Option<bool>),
    BlockCustomEmpty(i64, i32),
    BlockDecrease(i64, i32),
    SkySourceReady(i64),
    SkyNeighbours(i64, i32, i32, usize, i32),
    SkyRemove(i64, i32, i32, Option<i32>),
    SkyRemoveRead(i64, i32, i32, i32),
    SkyRemoveWritten(i64, i32, i32, i32),
    SkyAdd(i64, i32, i32, i32, Option<i32>),
    SkyAddRead(i64, i32, i32, i32, i32),
    SkyAddWritten(i64, i32, i32, i32, i32),
    SkyTarget(i64, i32),
    SkyTargetRead(i64),
    ResultWalk(u64, usize, usize, usize),
    AcquireResultTarget(i64),
    InstallResult(u64, usize),
    AffectSection(i64),
}

#[derive(Clone, Copy)]
enum SourceQuery { Column(i32, i32), Highest }

struct Frame {
    ops: Vec<Op>,
    scalar: i64,
    entry: Option<Arc<Entry>>,
    result: i64,
    sky_floor: i32,
    engine: u64,
    source_query: Option<SourceQuery>,
    source_input: Option<super::source_owner::Source>,
    block_query: bool,
    block_input: Option<(Arc<crate::world::level::chunk::live::Owner>,usize)>,
    // The original checkNode VM frame keeps its custom state through stored
    // layer callbacks, then releases it before later nodes/publication effects.
    vm_block_state: bool,
}
impl Frame {
    fn new(op: Op, engine: u64) -> Self { Self { ops: vec![op], scalar: 0, entry: None, result: 0, sky_floor: 0, engine, source_query: None, source_input: None, block_query:false, block_input:None, vm_block_state:false } }
    fn await_source(&mut self, effect: Effect) -> Effect {
        self.source_query = match effect {
            Effect::SkySource(x,z,_) => Some(SourceQuery::Column(x & 15,z & 15)),
            Effect::HighestSource(..) => Some(SourceQuery::Highest),
            _ => unreachable!(),
        };
        effect
    }
}

struct Engine {
    nodes: Table,
    queues: Arc<Queues>,
    kind: i32,
    projection: Arc<EngineProjection>,
    levels:Arc<LevelFollowers>,
    retired: bool,
    frames: usize,
}

struct State {
    sky: bool,
    tables: [Table; 8],
    queued_default: Option<Arc<Entry>>,
    state_default: u8,
    inconsistent: bool,
    updating: Arc<LightMap>,
    // Actual retired entries qualified by prior publication while their column is live.
    // This is save custody, not propagation membership or a Java state mirror.
    save_columns: HashMap<i64, HashMap<i64, Arc<Entry>>>,
    retiring_save: HashMap<i64, Arc<Entry>>,
    save_count: usize,
    save_bytes: usize,
    save_cleanup_needed: bool,
    engines: HashMap<u64, Engine>,
    next_engine: u64,
    // Materialization updates are handed to Java before any effect. These pins
    // survive until the next step, so their reusable identity slots cannot ABA.
    refreshed: Vec<Arc<Entry>>,
    frames: Vec<Option<Frame>>,
    free_frames: Vec<usize>,
    phase_counts: [u64; 4],
    sky_seeds:u32,
}

impl State {
    fn collection_default(&self,which:usize)->u32{if which==STATES{u32::from(self.state_default)}else{id(&self.queued_default)}}
    fn new(sky: bool, updating: Arc<LightMap>) -> Result<Self, Error> {
        let mut tables = Vec::with_capacity(8);
        // Index zero is only a placeholder; each engine has its own node table.
        for i in 0..8 { tables.push(Table::with_expected(if i == NODES { 0 } else { 16 }, 0.75)?); }
        tables[STATES].primitive_values();
        tables[QUEUED].typed_values()?;
        Ok(Self { sky, tables: tables.try_into().ok().unwrap(), queued_default: None, state_default: 0,
            inconsistent: false, updating, save_columns: HashMap::new(), retiring_save: HashMap::new(), save_count: 0, save_bytes: 0, save_cleanup_needed: false, engines: HashMap::new(), next_engine: 1, refreshed: Vec::new(), frames: Vec::new(), free_frames: Vec::new(), phase_counts: [0; 4],sky_seeds:0 })
    }
    fn attach(&mut self, queues: Arc<Queues>, kind: i32) -> Result<(u64, Arc<EngineProjection>), Error> {
        let id = self.next_engine; let next = id.checked_add(1).ok_or(Error::Table(Fault::Allocation))?;
        let projection = Arc::new(EngineProjection::default());
        let nodes = Table::with_expected(512, 0.5)?;
        let levels=queues.bind_projection(&projection);
        self.engines.insert(id, Engine { nodes, queues, kind, projection: Arc::clone(&projection), levels, retired: false, frames: 0 });
        self.next_engine = next; Ok((id, projection))
    }
    fn detach(&mut self, id: u64) {
        if let Some(engine) = self.engines.get_mut(&id) {
            engine.retired = true;
            if engine.frames == 0 { self.engines.remove(&id); }
        }
    }
    fn table(&self, engine: u64, which: usize) -> Result<&Table, Error> {
        if which == NODES { Ok(&self.engines.get(&engine).ok_or(Error::MissingOwner)?.nodes) }
        else { self.tables.get(which).ok_or(Error::MissingOwner) }
    }
    fn table_mut(&mut self, engine: u64, which: usize) -> Result<&mut Table, Error> {
        if which == ENABLED { self.save_cleanup_needed = true; }
        if which == NODES { Ok(&mut self.engines.get_mut(&engine).ok_or(Error::MissingOwner)?.nodes) }
        else { self.tables.get_mut(which).ok_or(Error::MissingOwner) }
    }
    fn refresh_save_bytes(&mut self) {
        self.save_bytes = self.retiring_save.capacity() * std::mem::size_of::<(i64, Arc<Entry>)>()
            + self.save_columns.capacity() * std::mem::size_of::<(i64, HashMap<i64, Arc<Entry>>)>()
            + self.save_columns.values().map(|layers| layers.capacity() * std::mem::size_of::<(i64, Arc<Entry>)>()).sum::<usize>();
    }
    fn retire_save_column(&mut self, key: i64) {
        if let Some(layers) = self.save_columns.remove(&key) {
            self.save_count -= layers.len();
        }
        self.retiring_save.retain(|section, _| column(*section) != key);
        self.refresh_save_bytes();
    }
    fn cleanup_save_columns(&mut self) {
        if !self.save_cleanup_needed { return; }
        self.save_cleanup_needed = false;
        let enabled = &self.tables[ENABLED];
        let mut removed = 0;
        self.save_columns.retain(|key, layers| {
            if enabled.find(*key).is_ok() { true } else { removed += layers.len(); false }
        });
        self.save_count -= removed;
        self.retiring_save.retain(|key, _| enabled.find(column(*key)).is_ok());
        self.refresh_save_bytes();
    }
    /// Keep the actual updating entry before removal. It may contain COW
    /// writes which the prior visible snapshot has never observed. Admission
    /// to committed save custody still requires that prior publication.
    fn prepare_retiring_save_layer(&mut self, key: i64) -> Result<(), Error> {
        if self.enabled(key) {
            if let Some(entry) = self.updating.raw(key) {
                self.retiring_save.try_reserve(1).map_err(|_| Error::Table(Fault::Allocation))?;
                self.retiring_save.insert(key, entry);
                self.refresh_save_bytes();
            }
        }
        Ok(())
    }
    fn saved_layer(&self, key: i64) -> Option<Arc<Entry>> {
        if !self.enabled(key) { return None; }
        self.save_columns.get(&column(key)).and_then(|layers| layers.get(&key)).cloned()
    }
    /// Prepare every fallible allocation before making a new visible map
    /// available to Java. Ordinary reads continue using that actual map;
    /// serialization alone may recover its retired, last-published identity.
    fn prepare_save_publication(&mut self, previous: &LightMap, next: &LightMap) -> Result<(), Error> {
        self.prepare_save_publication_with(previous, next, || Ok(()))
    }
    fn prepare_save_publication_with(&mut self, previous: &LightMap, next: &LightMap,
        before_commit: impl FnOnce() -> Result<(), Error>) -> Result<(), Error> {
        if !self.updating.shares_registry(previous) || !self.updating.shares_registry(next) { return Err(Error::MissingOwner); }
        let result = (|| {
            let mut pending = Vec::new();
            pending.try_reserve(self.retiring_save.len()).map_err(|_| Error::Table(Fault::Allocation))?;
            // The actual retirement producer is authoritative. Prior visible
            // membership alone cannot revive a layer retired while disabled.
            // This also covers a failed CHANGED insertion after physical removal.
            for (&key, entry) in &self.retiring_save {
                if self.enabled(key) && next.raw(key).is_none() && previous.raw(key).is_some() {
                    pending.push((column(key), key, Arc::clone(entry)));
                }
            }
            if pending.is_empty() { return Ok(()); }
            pending.sort_unstable_by_key(|(column, key, _)| (*column, *key));
            let columns = 1 + pending.windows(2).filter(|pair| pair[0].0 != pair[1].0).count();
            self.save_columns.try_reserve(columns).map_err(|_| Error::Table(Fault::Allocation))?;
            let mut first = 0;
            while first < pending.len() {
                let column = pending[first].0;
                let mut end = first + 1;
                while end < pending.len() && pending[end].0 == column { end += 1; }
                self.save_columns.entry(column).or_default().try_reserve(end - first).map_err(|_| Error::Table(Fault::Allocation))?;
                first = end;
            }
            before_commit()?;
            // Native-only commit, with no remaining allocation or VM effect.
            for (column, key, entry) in pending {
                if self.save_columns.get_mut(&column).unwrap().insert(key, entry).is_none() { self.save_count += 1; }
            }
            Ok(())
        })();
        if result.is_ok() { self.retiring_save.clear(); }
        self.refresh_save_bytes();
        result
    }
    fn contains(&self, table: usize, key: i64) -> bool { self.tables[table].find(key).is_ok() }
    fn add(&mut self, table: usize, key: i64) -> Result<bool, Error> { Ok(self.tables[table].put(key, 1)? == NONE) }
    fn state(&self, key: i64) -> u8 { let value = self.tables[STATES].get(key); if value == NONE { self.state_default } else { value as u8 } }
    fn enabled(&self, section: i64) -> bool { self.contains(ENABLED, column(section)) }
    fn queued_entry(&self, key: i64) -> Option<Arc<Entry>> {
        match self.tables[QUEUED].find(key){Ok(at)=>self.tables[QUEUED].entry_at(at as i32),Err(_)=>self.queued_default.clone()}
    }
    fn put_queued(&mut self, key: i64, entry: Option<Arc<Entry>>) -> Result<u32, Error> {
        let previous = self.tables[QUEUED].put_entry(key, entry)?;
        Ok(if previous == NONE { id(&self.queued_default) } else { previous })
    }
    fn remove_queued(&mut self, key: i64) -> Result<Option<Arc<Entry>>, Error> {
        let previous=self.queued_entry(key);
        self.tables[QUEUED].remove(key)?;
        Ok(previous)
    }
    fn new_layer(&self, layer: Layer) -> Result<Arc<Entry>, Error> {
        self.updating.native_entry(Arc::new(layer)).ok_or(Error::Table(Fault::Allocation))
    }
    fn affect_section(&mut self, key: i64) -> Result<(), Error> {
        for z in -1..=1 { for x in -1..=1 { for y in -1..=1 { self.add(AFFECTED, offset(key, x, y, z))?; } } }
        Ok(())
    }
    fn affect_block(&mut self, pos: i64) -> Result<(), Error> {
        let (x, y, z) = (propagation::block_x(pos), propagation::block_y(pos), propagation::block_z(pos));
        for x in (x - 1) >> 4..=(x + 1) >> 4 { for y in (y - 1) >> 4..=(y + 1) >> 4 { for z in (z - 1) >> 4..=(z + 1) >> 4 {
            self.add(AFFECTED, propagation::section_pos(x, y, z))?;
        } } }
        Ok(())
    }
    fn added(&self, key: i64) {
        if !self.sky { return; }
        let y = sy(key);
        if self.updating.lowest() > y { self.updating.set_lowest(y); }
        if self.updating.top(column(key)) < y.wrapping_add(1) { self.updating.set_top(column(key), Some(y.wrapping_add(1))); }
    }
    fn removed(&self, key: i64) {
        if !self.sky { return; }
        let mut y = sy(key);
        if self.updating.top(column(key)) == y.wrapping_add(1) {
            let mut next = key;
            while self.updating.get(next).is_none() && y >= self.updating.lowest() {
                y = y.wrapping_sub(1); next = offset(next, 0, -1, 0);
            }
            if self.updating.get(next).is_some() { self.updating.set_top(column(key), Some(y.wrapping_add(1))); }
            else { self.updating.set_top(column(key), None); }
        }
    }
    fn enqueue(&self, engine: u64, side: usize, pos: i64, entry: u64) -> Result<(), Error> {
        let owner = &self.engines.get(&engine).ok_or(Error::MissingOwner)?.queues;
        let mut queues = owner.data.lock().unwrap();
        queues[side].extend([pos, entry as i64]); owner.publish(&queues); Ok(())
    }
    fn begin(&mut self, op: Op, engine: u64) -> Result<u64, Error> {
        self.retain_engine(engine)?;
        Ok(self.insert_frame(op,engine))
    }
    fn begin_retained(&mut self,op:Op,engine:u64)->Result<u64,Error>{
        if engine!=0{self.engines.get_mut(&engine).ok_or(Error::MissingOwner)?.frames+=1;}
        Ok(self.insert_frame(op,engine))
    }
    fn insert_frame(&mut self,op:Op,engine:u64)->u64{
        let frame = Frame::new(op, engine);
        if let Some(i) = self.free_frames.pop() { self.frames[i] = Some(frame); i as u64 + 1 }
        else { self.frames.push(Some(frame)); self.frames.len() as u64 }
    }
    fn retain_engine(&mut self, engine: u64) -> Result<(), Error> {
        if engine != 0 {
            let row = self.engines.get_mut(&engine).ok_or(Error::MissingOwner)?;
            if row.retired { return Err(Error::MissingOwner); }
            row.frames += 1;
        }
        Ok(())
    }
    fn release_engine(&mut self, engine: u64) {
        if engine != 0 {
            let row = self.engines.get_mut(&engine).expect("live continuation retains its engine row");
            row.frames -= 1;
            if row.retired && row.frames == 0 { self.engines.remove(&engine); }
        }
    }
    fn end(&mut self, id: u64) -> Result<(), Error> {
        let i = id.checked_sub(1).ok_or(Error::MissingOwner)? as usize;
        let frame = self.frames.get_mut(i).ok_or(Error::MissingOwner)?.take().ok_or(Error::MissingOwner)?;
        self.release_engine(frame.engine);
        self.free_frames.push(i); self.refreshed.clear(); Ok(())
    }
    fn step(&mut self, id: u64, scalar: i64, entry: Option<Arc<Entry>>) -> Result<Effect, Error> {
        let i = id.checked_sub(1).ok_or(Error::MissingOwner)? as usize;
        let mut frame = self.frames.get_mut(i).ok_or(Error::MissingOwner)?.take().ok_or(Error::MissingOwner)?;
        // Consume the actual primary Source only at this query's last use.
        // The owned input drops before advancing into any later VM effect.
        let query = frame.source_query.take();
        let scalar = if let Some(source) = frame.source_input.take() {
            match query {
                Some(SourceQuery::Column(x,z)) => source.get(x | z << 4),
                Some(SourceQuery::Highest) => source.highest(),
                None => Err(super::source_owner::Error::Scan),
            }.map(i64::from).map_err(|_| Error::Unsupported)
        } else { Ok(scalar) };
        frame.block_query=false;
        let scalar=if let Some((owner,index))=frame.block_input.take(){
            owner.world_value(index).map(i64::from).ok_or(Error::Unsupported)
        }else{scalar};
        frame.entry = entry; self.refreshed.clear();
        let result = match scalar {
            Ok(value) => { frame.scalar = value; self.advance(&mut frame) },
            Err(error) => Err(error),
        };
        self.frames[i] = Some(frame); result
    }
    fn bind_source(&mut self, id: u64, source: &super::source_owner::Source) -> Result<(), Error> {
        let frame = id.checked_sub(1).and_then(|id| self.frames.get_mut(id as usize))
            .and_then(Option::as_mut).ok_or(Error::MissingOwner)?;
        if frame.source_query.is_none() || frame.source_input.is_some() { return Err(Error::MissingOwner); }
        frame.source_input = Some(source.clone());
        Ok(())
    }
    unsafe fn bind_block(&mut self,id:u64,owner:*const crate::world::level::chunk::live::Owner,index:usize)->i32 {
        let Some(frame)=id.checked_sub(1).and_then(|id|self.frames.get_mut(id as usize)).and_then(Option::as_mut)else{return -27;};
        if !frame.block_query || frame.block_input.is_some() || owner.is_null() || index>=4096{return -27;}
        let supported=(*owner).world_value(index).and_then(|state|propagation::installed_tables()
            .and_then(|tables|tables.state_types.get(state as usize).and_then(|&kind|tables.types.get(kind as usize)))).is_some();
        if !supported{return 1;}
        Arc::increment_strong_count(owner);
        frame.block_input=Some((Arc::from_raw(owner),index));
        0
    }
    /// Complete stock sky-source producer/COW/queue consumer. Admission has
    /// no observable mutation; every accepted section has no Java leaf effect.
    fn seed_sky(&mut self,engine:u64,chunk:i64,top:i32,bottom:i32,columns:&[i32])->Result<bool,Error>{
        let Some(binding)=self.engines.get(&engine) else{return Err(Error::MissingOwner);};
        if !self.sky || binding.kind!=1 || columns.len()!=propagation::seed::COLUMNS{return Ok(false);}
        let span=i64::from(top)-i64::from(bottom);
        if top==i32::MIN || span<0 || span>4096{return Ok(false);}
        let queues=Arc::clone(&binding.queues);let updating=Arc::clone(&self.updating);
        let x=chunk as i32;let z=(chunk>>32) as i32;
        updating.reconcile(|map|->Result<bool,Error>{
            if !map.native_sky_compatible((bottom..top).map(|y|propagation::section_pos(x,y,z))){return Ok(false);}
            let mut pending=queues.data.lock().unwrap();
            // A conservative bound declines before any section/cache/COW
            // mutation; the original loop then owns any capacity failure prefix.
            if pending[1].len() as u64+(span as u64)*8192>i32::MAX as u64{return Ok(false);}
            let mut y=top.wrapping_sub(1);
            while y>=bottom {
                let key=propagation::section_pos(x,y,z);
                if let Some(source)=map.get(key){
                    let entry=if self.add(CHANGED,key)? {
                        let copy=self.new_layer(source.layer.as_ref().unwrap().copy())?;
                        map.set(key,Some(Arc::clone(&copy)));map.clear_cache();copy
                    }else{source};
                    let layer=entry.layer.as_ref().unwrap();let view=layer.view();let mut bytes=[0u8;2048];
                    if !view.copy_bytes_into(&mut bytes){bytes.fill((view.default_value()*17) as u8);}
                    let (wrote,below)=propagation::seed::sky_section(&mut bytes,columns,y.wrapping_shl(4),x.wrapping_shl(4),z.wrapping_shl(4),&mut |pos,value|pending[1].extend([pos,value as i64]));
                    // Publish this section before another fallible COW step;
                    // an error retains the already committed queue prefix.
                    queues.publish(&pending);
                    if wrote && layer.install_bytes(bytes){self.refreshed.push(entry);}
                    if !below{break;}
                }
                y=y.wrapping_sub(1);
            }
            queues.publish(&pending);self.sky_seeds=self.sky_seeds.saturating_add(1);Ok(true)
        })
    }
    fn advance(&mut self, f: &mut Frame) -> Result<Effect, Error> {
        while let Some(op) = f.ops.pop() {
            match op {
                Op::Enable(chunk,enabled)=>{
                    let key=propagation::section_pos(chunk as i32,0,(chunk>>32) as i32);
                    if enabled{self.tables[ENABLED].put(key,1)?;}else{self.tables[ENABLED].remove(key)?;self.retire_save_column(key);}
                    let kind=self.engines.get(&f.engine).ok_or(Error::MissingOwner)?.kind;
                    if enabled&&kind==1{
                        f.ops.push(Op::EnableHighest(chunk));return Ok(f.await_source(Effect::HighestSource(chunk as i32,(chunk>>32) as i32)));
                    }
                }
                Op::EnableHighest(chunk)=>{
                    let source=(f.scalar as i32).wrapping_sub(1);
                    let bottom=self.updating.lowest().max((source>>4).wrapping_add(1));
                    let key=propagation::section_pos(chunk as i32,0,(chunk>>32) as i32);
                    f.ops.push(Op::EnableWalk(chunk,self.updating.top(key).wrapping_sub(1),bottom));
                }
                Op::EnableWalk(chunk,y,bottom)=>{
                    if y>=bottom{
                        f.ops.push(Op::EnableWalk(chunk,y.wrapping_sub(1),bottom));
                        f.ops.push(Op::EnableTarget);
                        f.ops.push(Op::Writable(propagation::section_pos(chunk as i32,y,(chunk>>32) as i32)));
                    }
                }
                Op::EnableTarget=>{
                    if let Some(target)=f.entry.take(){
                        if let Some(layer)=target.layer.as_ref().filter(|layer|layer.is_valid()){
                            if layer.view().is_empty(){layer.fill(15);self.refreshed.push(target);}
                        }else{
                            let slot=target.id;f.ops.push(Op::EnableFill(target));return Ok(Effect::LayerEmpty(slot));
                        }
                    }
                }
                Op::EnableFill(target)=>{
                    if f.scalar!=0{
                        if let Some(layer)=target.layer.as_ref().filter(|layer|layer.is_valid()){
                            layer.fill(15);self.refreshed.push(target);
                        }else{let slot=target.id;f.ops.push(Op::EnableFilled(target));return Ok(Effect::LayerFill(slot,15));}
                    }
                }
                Op::EnableFilled(target)=>{drop(target);}
                Op::Sources(chunk)=>{
                    self.tables[ENABLED].put(propagation::section_pos(chunk as i32,0,(chunk>>32) as i32),1)?;
                    let kind=self.engines.get(&f.engine).ok_or(Error::MissingOwner)?.kind;
                    return match kind{
                        0=>Ok(Effect::BlockSources(chunk as i32,(chunk>>32) as i32)),
                        1=>Ok(Effect::SkySources(chunk as i32,(chunk>>32) as i32)),
                        _=>Err(Error::Unsupported),
                    };
                }
                Op::SeedSky(chunk,top,bottom,columns)=>{f.result=i64::from(self.seed_sky(f.engine,chunk,top,bottom,&columns)?);}

                Op::ValuesStart(which,wanted)=>{
                    f.ops.push(Op::ValuesSelect(ValueSearch{which,wanted,phase:0,pos:0,
                        keys:self.tables[which].captured_keys(),values:self.tables[which].captured_values()}));
                }
                Op::ValuesSelect(mut search)=>{
                    let mut selected=None;
                    if search.phase==0 {
                        search.phase=1;
                        if self.tables[search.which].has_zero(){selected=Some(self.tables[search.which].table_len()-1);}
                    }
                    if selected.is_none() {
                        if search.phase==1 {search.pos=(self.tables[search.which].table_len()-1) as i32;search.phase=2;}
                        while search.pos>0 {
                            search.pos-=1;
                            let at=search.pos as usize;
                            let key=search.keys.get(at).ok_or(Fault::ArrayIndex(search.pos,search.keys.len()))?.load(std::sync::atomic::Ordering::Relaxed);
                            if key!=0 {selected=Some(at);break;}
                        }
                    }
                    let Some(at)=selected else {f.result=0;continue;};
                    let value=search.values.get(at).ok_or(Fault::ArrayIndex(at as i32,search.keys.len()))?;
                    if search.which==STATES {
                        if value==search.wanted {f.result=1;continue;}
                        f.ops.push(Op::ValuesSelect(search));continue;
                    }
                    f.ops.push(Op::ValuesCompared(search));return Ok(Effect::CollectionCall(0,0,value));
                }
                Op::ValuesCompared(search)=>{
                    if f.scalar!=0 {f.result=1;}else{f.ops.push(Op::ValuesSelect(search));}
                }
                Op::CollectionStart(mut edit)=>{
                    edit.pos=self.tables[edit.which].insertion_position(edit.key);
                    let previous=if edit.pos>=0 {self.tables[edit.which].value_at(edit.pos)?}else{NONE};
                    if matches!(edit.kind,0|1|5) && edit.pos>=0 {f.result=i64::from(previous);continue;}
                    if edit.kind==2 && (edit.pos<0 || (edit.which==QUEUED && previous==0)) {
                        f.result=i64::from(self.collection_default(edit.which));continue;
                    }
                    if edit.kind>=6 && edit.pos<0 {f.result=0;continue;}
                    if edit.which==STATES && edit.kind>=6 {
                        if previous!=edit.input {f.result=0;continue;}
                        if edit.kind==7 {self.tables[STATES].remove_at(edit.pos,edit.key==0)?;}
                        f.result=1;continue;
                    }
                    if edit.kind==4 && (edit.pos<0 || (edit.which==QUEUED && previous==0)) {
                        if edit.pos<0 {self.tables[edit.which].insert_at(-edit.pos-1,edit.key,edit.input,edit.input_entry)?;}
                        else{self.tables[edit.which].set_value_at(edit.pos,edit.input,edit.input_entry)?;}
                        f.result=i64::from(edit.input);continue;
                    }
                    if edit.which==QUEUED && previous!=NONE {edit.held_old=self.tables[QUEUED].entry_at(edit.pos);}
                    let leaf=if edit.kind==5{1}else{0};let key=edit.key;
                    f.ops.push(Op::CollectionApply(edit));
                    return Ok(Effect::CollectionCall(leaf,key,previous));
                }
                Op::CollectionApply(mut edit)=>{
                    if edit.kind==5 {
                        if f.scalar==0 {f.result=i64::from(self.collection_default(edit.which));continue;}
                        edit.kind=0;let key=edit.key;f.ops.push(Op::CollectionApply(edit));
                        return Ok(Effect::CollectionCall(2,key,NONE));
                    }
                    if edit.kind>=6 {
                        if f.scalar==0 {f.result=0;continue;}
                        if edit.kind==7 {self.tables[edit.which].remove_at(edit.pos,edit.key==0)?;}
                        if edit.kind==8 {self.tables[edit.which].set_value_at(edit.pos,edit.input,edit.input_entry)?;}
                        f.result=1;continue;
                    }
                    let value=if edit.which==STATES {f.scalar as u32}else{id(&f.entry)};
                    let null=if edit.which==STATES {value==NONE}else{value==0};
                    // Object computeIfAbsent inserts a null result. The
                    // nullable byte overload and remapping methods do not.
                    let remove_null=null && !(edit.which==QUEUED && edit.kind==0);
                    if remove_null {
                        if edit.pos>=0 {self.tables[edit.which].remove_at(edit.pos,edit.key==0)?;}
                        f.result=i64::from(self.collection_default(edit.which));continue;
                    }
                    let entry=if edit.which==QUEUED{f.entry.take()}else{None};
                    if edit.pos<0 {self.tables[edit.which].insert_at(-edit.pos-1,edit.key,value,entry)?;}
                    else{self.tables[edit.which].set_value_at(edit.pos,value,entry)?;}
                    f.result=i64::from(value);
                }
                Op::AffectSection(key) => { self.affect_section(key)?; }
                Op::Status(key, empty) => {
                    let previous = self.state(key);
                    let next = if empty { previous & !32 } else { previous | 32 };
                    if previous != next {
                        f.ops.push(Op::Neighbours(key, 0, if empty { -1 } else { 1 }));
                        f.ops.push(Op::PutState(key, next));
                    }
                }
                Op::Neighbours(key, mut i, delta) => {
                    if i == 13 { i += 1; }
                    if i < 27 {
                        let next = offset(key, (i / 9) as i32 - 1, (i / 3 % 3) as i32 - 1, (i % 3) as i32 - 1);
                        let previous = self.state(next); let count = i32::from(previous & 31) + delta;
                        if !(0..=26).contains(&count) { return Err(Error::Neighbours); }
                        f.ops.push(Op::Neighbours(key, i + 1, delta));
                        f.ops.push(Op::PutState(next, previous & !31 | count as u8));
                    }
                }
                Op::PutState(key, value) => {
                    self.phase_counts[1] += 1;
                    if value == 0 {
                        let previous = self.tables[STATES].remove(key)?;
                        if (if previous == NONE { self.state_default } else { previous as u8 }) != 0 {
                            self.add(REMOVING, key)?; self.inconsistent = true;
                        }
                    } else {
                        let previous = self.tables[STATES].put(key, u32::from(value))?;
                        if (if previous == NONE { self.state_default } else { previous as u8 }) == 0 && self.tables[REMOVING].remove(key)? == NONE {
                            if let Some(entry) = self.queued_entry(key) {
                                self.updating.set(key, Some(entry)); f.ops.push(Op::Initialized(key));
                            } else if self.sky && self.updating.top(column(key)) != self.updating.lowest() && sy(key) < self.updating.top(column(key)) {
                                let mut above = offset(key, 0, 1, 0);
                                let entry = loop { if let Some(entry) = self.updating.get(above) { break entry; } above = offset(above, 0, 1, 0); };
                                if let Some(layer) = entry.layer.as_ref().filter(|layer| layer.is_valid()) {
                                    self.updating.set(key, Some(self.new_layer(layer.repeat_first())?)); f.ops.push(Op::Initialized(key));
                                } else {
                                    f.ops.push(Op::InstallCreated(key)); return Ok(Effect::Repeat(entry.id));
                                }
                            } else {
                                let value = if self.sky && self.enabled(key) { 15 } else { 0 };
                                self.updating.set(key, Some(self.new_layer(Layer::new(value))?)); f.ops.push(Op::Initialized(key));
                            }
                        }
                    }
                }
                Op::InstallCreated(key) => { self.updating.set(key, f.entry.take()); f.ops.push(Op::Initialized(key)); }
                Op::Initialized(key) => {
                    self.add(CHANGED, key)?; self.added(key); self.affect_section(key)?; self.inconsistent = true;
                }
                Op::Reconcile => {
                    if self.inconsistent {
                        self.inconsistent = false; self.phase_counts[2] += 1;
                        f.ops.push(Op::RemoveWalk(self.tables[REMOVING].cursor()));
                    }
                }
                Op::RemoveWalk(mut cursor) => {
                    if cursor.has_next() {
                        let key = cursor.next_key(&self.tables[REMOVING])?;
                        let queued = self.remove_queued(key)?;
                        self.prepare_retiring_save_layer(key)?;
                        let removed = self.updating.remove(key);
                        if self.contains(RETAINED, column(key)) {
                            if let Some(entry) = queued.or(removed) { self.put_queued(key, Some(entry))?; }
                        }
                        f.ops.push(Op::RemoveWalk(cursor));
                    } else {
                        self.updating.clear_cache(false); f.ops.push(Op::RemovedWalk(self.tables[REMOVING].cursor()));
                    }
                }
                Op::RemovedWalk(mut cursor) => {
                    if cursor.has_next() {
                        let key = cursor.next_key(&self.tables[REMOVING])?;
                        self.removed(key); self.add(CHANGED, key)?; f.ops.push(Op::RemovedWalk(cursor));
                    } else {
                        self.tables[REMOVING].clear_membership(); f.ops.push(Op::QueuedWalk(self.tables[QUEUED].cursor()));
                    }
                }
                Op::QueuedWalk(mut cursor) => {
                    // There is no Java effect in this original loop. Consume
                    // the live queued table and actual map in one native scope;
                    // retain each original cache-read/mutation/error point.
                    let updating=Arc::clone(&self.updating);
                    updating.reconcile(|map|->Result<(),Error>{
                        while cursor.has_next() {
                            let at=cursor.next_entry(&self.tables[QUEUED])?;
                            let key=self.tables[QUEUED].key_at(at)?;
                            let current=map.get(key);
                            if current.is_some() {
                                let queued=self.queued_entry(key);
                                if !same(&current,&queued) {map.set(key,queued);self.add(CHANGED,key)?;}
                                cursor.remove(&mut self.tables[QUEUED])?;
                            }
                        }
                        map.clear_cache();Ok(())
                    })?;
                }
                Op::Publish => {
                    if self.tables[CHANGED].len() != 0 {
                        let snapshot = self.updating.snapshot(); snapshot.clear_cache(true);
                        f.ops.push(Op::Published); return Ok(Effect::Publish(snapshot));
                    }
                    f.ops.push(Op::NotifyWalk(self.tables[AFFECTED].cursor()));
                }
                Op::Published => {
                    self.phase_counts[3] += 1; self.tables[CHANGED].clear_membership();
                    f.ops.push(Op::NotifyWalk(self.tables[AFFECTED].cursor()));
                }
                Op::NotifyWalk(mut cursor) => {
                    if cursor.has_next() {
                        let key = cursor.next_key(&self.tables[AFFECTED])?;
                        f.ops.push(Op::NotifyWalk(cursor)); return Ok(Effect::Notify(key));
                    }
                    self.tables[AFFECTED].clear_membership();
                }
                Op::WriteStored(pos, value) => {
                    f.ops.push(Op::WriteTarget(pos, value));
                    let key = section(pos);
                    if self.add(CHANGED, key)? {
                        let source = self.updating.raw(key);
                        if let Some(layer) = source.as_ref().and_then(|e| e.layer.as_ref()).filter(|layer| layer.is_valid()) {
                            self.updating.set(key, Some(self.new_layer(layer.copy())?)); self.updating.clear_cache(false);
                        } else { f.ops.push(Op::Copied(key, true)); return Ok(Effect::Copy(id(&source))); }
                    }
                }
                Op::WriteTarget(pos, value) => {
                    let target = self.updating.get(section(pos));
                    if let Some(layer) = target.as_ref().and_then(|e| e.layer.as_ref()).filter(|layer| layer.is_valid()) {
                        if layer.set(index(pos), value).map_err(|e| Error::Table(Fault::ArrayIndex(e.0, 2048)))? { self.refreshed.push(target.unwrap()); }
                    } else { f.ops.push(Op::Affected(pos)); return Ok(Effect::Write(id(&target), index(pos), value)); }
                    self.affect_block(pos)?;
                }
                Op::Affected(pos) => { self.affect_block(pos)?; }
                Op::Writable(key) => {
                    let source = self.updating.get(key);
                    if source.is_none() { f.result = 0;f.entry=None; continue; }
                    if self.add(CHANGED, key)? {
                        if let Some(layer) = source.as_ref().and_then(|e| e.layer.as_ref()).filter(|layer| layer.is_valid()) {
                            let copy = self.new_layer(layer.copy())?; f.result = i64::from(copy.id);
                            f.entry=Some(Arc::clone(&copy));self.updating.set(key, Some(copy)); self.updating.clear_cache(false);
                        } else { f.ops.push(Op::Copied(key, false)); return Ok(Effect::Copy(id(&source))); }
                    } else { f.result = i64::from(id(&source));f.entry=source; }
                }
                Op::Copied(key, follow) => {
                    f.result = i64::from(id(&f.entry)); self.updating.set(key, f.entry.clone()); self.updating.clear_cache(false);
                    if follow { /* the pending write uses the current map */ }
                }
                Op::ReturnEntry => { f.result = i64::from(id(&f.entry)); }
                Op::ReadStored(pos) => {
                    let source = self.updating.get(section(pos));
                    if let Some(layer) = source.as_ref().and_then(|e| e.layer.as_ref()).filter(|layer| layer.is_valid()) {
                        f.scalar = i64::from(layer.view().get(index(pos)).map_err(|e| Error::Table(Fault::ArrayIndex(e.0, 2048)))?);
                    } else { return Ok(Effect::Read(id(&source), index(pos))); }
                }
                Op::RememberRead => { f.result = f.scalar; }
                Op::Run => { f.ops.push(Op::NodeWalk(self.table(f.engine, NODES)?.cursor())); }
                Op::NodeWalk(mut cursor) => {
                    if cursor.has_next() {
                        let nodes = self.table(f.engine, NODES)?;
                        let pos = cursor.next_key(nodes)?;
                        f.ops.push(Op::NodeWalk(cursor)); f.ops.push(Op::Node(pos));
                    } else { f.ops.push(Op::NodesDone); }
                }
                Op::NodesDone => {
                    let nodes = self.table_mut(f.engine, NODES)?; nodes.clear_membership(); nodes.trim(512);
                    f.ops.push(Op::AfterPropagation); return Ok(Effect::Propagate);
                }
                Op::AfterPropagation => {
                    f.result = f.scalar; f.ops.push(Op::Publish); f.ops.push(Op::Reconcile);
                    return Ok(Effect::ClearChunkCache);
                }
                Op::Node(pos) => {
                    self.phase_counts[0] += 1;
                    let kind = self.engines.get(&f.engine).ok_or(Error::MissingOwner)?.kind;
                    if kind == 2 { return Ok(Effect::CustomNode(pos)); }
                    if kind == 1 {
                        f.ops.push(Op::SkySourceReady(pos));
                        if self.enabled(section(pos)) { return Ok(f.await_source(Effect::SkySource(propagation::block_x(pos), propagation::block_z(pos), i32::MAX))); }
                        f.scalar = i64::from(i32::MAX);
                    } else if self.updating.get(section(pos)).is_some() {
                        f.ops.push(Op::BlockChecked(pos)); f.block_query=true; return Ok(Effect::BlockState(pos));
                    }
                }
                Op::BlockChecked(pos) => {
                    let properties = propagation::installed_tables().and_then(|t| t.state_types.get(f.scalar as usize).and_then(|&kind| t.types.get(kind as usize)));
                    if let Some(properties) = properties {
                        let emission = if self.enabled(section(pos)) { i32::from(properties.emission) } else { 0 };
                        f.ops.push(Op::BlockLevel(pos, emission, Some(properties.empty))); f.ops.push(Op::ReadStored(pos));
                    } else { f.vm_block_state=true; f.ops.push(Op::BlockCustomEmission(pos)); return Ok(Effect::BlockEmission); }
                }
                Op::BlockCustomEmission(pos) => {
                    let emission = if f.scalar > 0 && self.enabled(section(pos)) { f.scalar as i32 } else { 0 };
                    f.ops.push(Op::BlockLevel(pos, emission, None)); f.ops.push(Op::ReadStored(pos));
                }
                Op::BlockLevel(pos, emission, empty) => {
                    if emission < f.scalar as i32 {
                        f.ops.push(Op::BlockEnqueue(pos, emission, empty));
                        f.ops.push(Op::BlockDecrease(pos, f.scalar as i32));
                        f.ops.push(Op::WriteStored(pos, 0));
                    } else {
                        self.enqueue(f.engine, 0, pos, 1009)?; f.ops.push(Op::BlockEnqueue(pos, emission, empty));
                    }
                }
                Op::BlockDecrease(pos, previous) => { self.enqueue(f.engine, 0, pos, 1008 | previous as u64 & 15)?; }
                Op::BlockEnqueue(pos, emission, empty) => {
                    if emission > 0 {
                        if let Some(empty) = empty { self.enqueue(f.engine, 1, pos, 1008 | emission as u64 & 15 | 2048 | if empty { 1024 } else { 0 })?; }
                        else { f.ops.push(Op::BlockCustomEmpty(pos, emission)); return Ok(Effect::BlockEmpty); }
                    }
                    f.vm_block_state=false;
                }
                Op::BlockCustomEmpty(pos, emission) => { self.enqueue(f.engine, 1, pos, 1008 | emission as u64 & 15 | 2048 | if f.scalar != 0 { 1024 } else { 0 })?; f.vm_block_state=false; }
                Op::SkySourceReady(pos) => {
                    let lowest = f.scalar as i32;
                    f.ops.push(Op::SkyTarget(pos, lowest));
                    if lowest != i32::MAX {
                        let bottom = self.updating.lowest().wrapping_shl(4);
                        f.ops.push(Op::SkyNeighbours(pos, lowest, bottom, 0, i32::MIN));
                        if lowest > bottom { f.ops.push(Op::SkyRemove(pos, lowest, lowest.wrapping_sub(1) >> 4, None)); }
                    }
                }
                Op::SkyRemove(pos, lowest, section_y, at_y) => {
                    let key = propagation::section_pos(propagation::block_x(pos) >> 4, section_y, propagation::block_z(pos) >> 4);
                    if at_y.is_none() && section_y < self.updating.lowest() { continue; }
                    if at_y.is_some() || self.updating.get(key).is_some() {
                        let bottom = section_y.wrapping_shl(4);
                        let y = at_y.unwrap_or(bottom.wrapping_add(15).min(lowest.wrapping_sub(1)));
                        if y >= bottom {
                            let block = propagation::block_pos(propagation::block_x(pos), y, propagation::block_z(pos));
                            f.ops.push(Op::SkyRemoveRead(pos, lowest, section_y, y)); f.ops.push(Op::ReadStored(block));
                        } else { f.ops.push(Op::SkyRemove(pos, lowest, section_y.wrapping_sub(1), None)); }
                    } else { f.ops.push(Op::SkyRemove(pos, lowest, section_y.wrapping_sub(1), None)); }
                }
                Op::SkyRemoveRead(pos, lowest, section_y, y) => {
                    if f.scalar == 15 {
                        f.ops.push(Op::SkyRemoveWritten(pos, lowest, section_y, y));
                        f.ops.push(Op::WriteStored(propagation::block_pos(propagation::block_x(pos), y, propagation::block_z(pos)), 0));
                    }
                }
                Op::SkyRemoveWritten(pos, lowest, section_y, y) => {
                    let block = propagation::block_pos(propagation::block_x(pos), y, propagation::block_z(pos));
                    self.enqueue(f.engine, 0, block, if y == lowest.wrapping_sub(1) { 1023 } else { 991 })?;
                    f.ops.push(Op::SkyRemove(pos, lowest, section_y, Some(y.wrapping_sub(1))));
                }
                Op::SkyNeighbours(pos, lowest, bottom, at, maximum) => {
                    let maximum = if at == 0 { maximum } else { maximum.max(f.scalar as i32) };
                    if at < 4 {
                        let (x, z) = (propagation::block_x(pos), propagation::block_z(pos));
                        let (x, z) = match at { 0 => (x.wrapping_sub(1), z), 1 => (x.wrapping_add(1), z), 2 => (x, z.wrapping_sub(1)), _ => (x, z.wrapping_add(1)) };
                        f.ops.push(Op::SkyNeighbours(pos, lowest, bottom, at + 1, maximum));
                        return Ok(f.await_source(Effect::SkySource(x, z, i32::MIN)));
                    }
                    f.ops.push(Op::SkyAdd(pos, lowest, maximum, lowest.max(bottom) >> 4, None));
                    // Capture p=max(source,bottom) once, as the Java method does.
                    f.sky_floor = lowest.max(bottom);
                }
                Op::SkyAdd(pos, lowest, maximum, section_y, at_y) => {
                    let key = propagation::section_pos(propagation::block_x(pos) >> 4, section_y, propagation::block_z(pos) >> 4);
                    let top = self.updating.top(column(key));
                    if at_y.is_none() && (top == self.updating.lowest() || section_y >= top) { continue; }
                    if at_y.is_some() || self.updating.get(key).is_some() {
                        let bottom = section_y.wrapping_shl(4);
                        let y = at_y.unwrap_or(bottom.max(f.sky_floor));
                        if y <= bottom.wrapping_add(15) {
                            let block = propagation::block_pos(propagation::block_x(pos), y, propagation::block_z(pos));
                            f.ops.push(Op::SkyAddRead(pos, lowest, maximum, section_y, y)); f.ops.push(Op::ReadStored(block));
                        } else { f.ops.push(Op::SkyAdd(pos, lowest, maximum, section_y.wrapping_add(1), None)); }
                    } else { f.ops.push(Op::SkyAdd(pos, lowest, maximum, section_y.wrapping_add(1), None)); }
                }
                Op::SkyAddRead(pos, lowest, maximum, section_y, y) => {
                    if f.scalar != 15 {
                        f.ops.push(Op::SkyAddWritten(pos, lowest, maximum, section_y, y));
                        f.ops.push(Op::WriteStored(propagation::block_pos(propagation::block_x(pos), y, propagation::block_z(pos)), 15));
                    }
                }
                Op::SkyAddWritten(pos, lowest, maximum, section_y, y) => {
                    if y < maximum || y == lowest {
                        self.enqueue(f.engine, 1, propagation::block_pos(propagation::block_x(pos), y, propagation::block_z(pos)), 991)?;
                    }
                    f.ops.push(Op::SkyAdd(pos, lowest, maximum, section_y, Some(y.wrapping_add(1))));
                }
                Op::SkyTarget(pos, lowest) => {
                    if self.updating.get(section(pos)).is_some() {
                        if propagation::block_y(pos) >= lowest { self.enqueue(f.engine, 0, pos, 991)?; self.enqueue(f.engine, 1, pos, 991)?; }
                        else { f.ops.push(Op::SkyTargetRead(pos)); f.ops.push(Op::ReadStored(pos)); }
                    }
                }
                Op::SkyTargetRead(pos) => {
                    if f.scalar > 0 { f.ops.push(Op::BlockDecrease(pos, f.scalar as i32)); f.ops.push(Op::WriteStored(pos, 0)); }
                    else { self.enqueue(f.engine, 0, pos, 1009)?; }
                }
                Op::ResultWalk(handle, at, written, affected) => {
                    if at == 0 && unsafe { propagation::ffi::result_shape(handle) } != Some((written, affected)) { return Err(Error::Unsupported); }
                    if at < written {
                        let (key, _) = unsafe { propagation::ffi::result_layer(handle, at) }.ok_or(Error::Unsupported)?;
                        f.ops.push(Op::ResultWalk(handle, at + 1, written, affected));
                        f.ops.push(Op::InstallResult(handle, at)); f.ops.push(Op::AcquireResultTarget(key));
                    } else {
                        for i in 0..affected {
                            let key = unsafe { propagation::ffi::result_affected(handle, i) }.ok_or(Error::Unsupported)?;
                            self.add(AFFECTED, key)?;
                        }
                    }
                }
                Op::AcquireResultTarget(key) => {
                    if self.add(CHANGED, key)? {
                        let source = self.updating.raw(key);
                        if let Some(layer) = source.as_ref().and_then(|e| e.layer.as_ref()).filter(|layer| layer.is_valid()) {
                            let copy = self.new_layer(layer.copy())?;
                            self.updating.set(key, Some(Arc::clone(&copy))); self.updating.clear_cache(false); f.entry = Some(copy);
                        } else { f.ops.push(Op::Copied(key, true)); return Ok(Effect::Copy(id(&source))); }
                    } else { f.entry = self.updating.get(key); }
                }
                Op::InstallResult(handle, at) => {
                    if let Some(layer) = f.entry.as_ref().and_then(|e| e.layer.as_ref()).filter(|layer| layer.is_valid()) {
                        let (_, bytes) = unsafe { propagation::ffi::result_layer(handle, at) }.ok_or(Error::Unsupported)?;
                        if layer.install_bytes(bytes) { self.refreshed.push(f.entry.take().unwrap()); }
                    } else { return Ok(Effect::InstallResult(id(&f.entry), at)); }
                }
            }
        }
        Ok(Effect::Done(f.result))
    }
}
