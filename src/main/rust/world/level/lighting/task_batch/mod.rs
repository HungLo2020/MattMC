//! Threaded lighting's live task membership and PRE/propagation/POST policy.
//! Every operation ends before a Java effect; invocation cursors are independent.
use std::collections::VecDeque;
use std::ffi::c_void;
mod ffi;
#[cfg(test)]
mod tests;

const BATCH: usize = 1000;
/// Prefix of an actual native level allocation. Its operations retain/drop that
/// allocation and consume pure light policy; none may call Java or this queue.
#[repr(C)]
pub(crate) struct LevelToken {
    pub retain: unsafe extern "C" fn(*const LevelToken),
    pub release: unsafe extern "C" fn(*const LevelToken),
    pub check: unsafe extern "C" fn(*const LevelToken, i64) -> i32,
    pub operation: unsafe extern "C" fn(*const LevelToken, i32, i64, i64, *const c_void) -> i32,
    pub channel: unsafe extern "C" fn(*const LevelToken, i32) -> i32,
    pub start: unsafe extern "C" fn(*const LevelToken,i32,i64,bool)->*mut FrameToken,
}
/// Prefix of an actual native level continuation, created only on a VM effect
/// or original error. Pure status commands finish inside the native producer.
#[repr(C)]
pub(crate) struct FrameToken {
    pub read:unsafe extern "C" fn(*mut FrameToken,*mut i64)->i32,
    pub step:unsafe extern "C" fn(*mut FrameToken,i64,*const c_void,*mut i64)->i32,
    pub release:unsafe extern "C" fn(*mut FrameToken),
    pub resource:unsafe extern "C" fn(*mut FrameToken)->*const c_void,
}
struct FrameLease {owner:*mut FrameToken}
impl Drop for FrameLease {fn drop(&mut self){unsafe{((*self.owner).release)(self.owner);}}}
struct LevelLease { owner: *const LevelToken }
impl LevelLease {
    unsafe fn retain(owner: *const LevelToken) -> Option<Self> {
        let token = owner.as_ref()?;
        (token.retain)(owner);
        Some(Self { owner })
    }
    fn check(&self, pos: i64) -> Result<(), Error> {
        let status = unsafe { ((*self.owner).check)(self.owner, pos) };
        if status == 0 { Ok(()) } else { Err(Error::Lighting(status)) }
    }
}
impl Drop for LevelLease {
    fn drop(&mut self) { unsafe { ((*self.owner).release)(self.owner); } }
}
struct Check { level: LevelLease, pos: i64 }
enum Command {
    Check(Check),
    Retain { level:LevelLease, key:i64, value:bool },
    Import { level:LevelLease, channel:i32, key:i64 },
    Level { level:LevelLease, kind:i32, key:i64, value:bool },
}
struct Task {
    row: u64,
    post: bool,
    command: Option<Command>,
}
#[derive(Clone, Copy)]
enum Phase {
    Pre,
    Post,
}
struct Cursor {
    limit: usize,
    phase: Phase,
    position: usize,
    visited: usize,
    waiting: bool,
    frame:Option<FrameLease>,
    level:Option<LevelLease>,
}
#[derive(Debug, PartialEq)]
enum Error {
    Cursor,
    RemoveEmpty,
    Row,
    Lighting(i32),
}
#[derive(Debug, PartialEq)]
enum Action {
    Done,
    Propagate,
    Callable(u64),
    Import(u64, i32),
    Context(u64),
}
#[derive(Default)]
struct Queue {
    tasks: VecDeque<Task>,
    rows: u64,
    free_rows: Vec<u64>,
    cursors: Vec<Option<Cursor>>,
    free_cursors: Vec<usize>,
    retired: Vec<u64>,
}
impl Queue {
    fn next_row(&self) -> u64 {
        self.free_rows.last().copied().unwrap_or(self.rows + 1)
    }
    fn append(&mut self, row: u64, post: bool) -> Result<bool, Error> {
        self.append_task(row, post, None)
    }
    unsafe fn append_check(&mut self, row: u64, owner: *const LevelToken, pos: i64) -> Result<bool, Error> {
        let level = LevelLease::retain(owner).ok_or(Error::Row)?;
        self.append_task(row, false, Some(Command::Check(Check { level, pos })))
    }
    unsafe fn append_retain(&mut self,row:u64,owner:*const LevelToken,key:i64,value:bool)->Result<bool,Error>{
        let level=LevelLease::retain(owner).ok_or(Error::Row)?;
        self.append_task(row,false,Some(Command::Retain{level,key,value}))
    }
    unsafe fn append_import(&mut self,row:u64,owner:*const LevelToken,channel:i32,key:i64)->Result<bool,Error>{
        let level=LevelLease::retain(owner).ok_or(Error::Row)?;
        self.append_task(row,false,Some(Command::Import{level,channel,key}))
    }
    unsafe fn append_status(&mut self,row:u64,owner:*const LevelToken,key:i64,empty:bool)->Result<bool,Error>{
        self.append_level(row,owner,0,key,empty)
    }
    unsafe fn append_level(&mut self,row:u64,owner:*const LevelToken,kind:i32,key:i64,value:bool)->Result<bool,Error>{
        if !(0..=2).contains(&kind){return Err(Error::Row);}
        let level=LevelLease::retain(owner).ok_or(Error::Row)?;
        self.append_task(row,false,Some(Command::Level{level,kind,key,value}))
    }
    fn append_task(&mut self, row: u64, post: bool, command: Option<Command>) -> Result<bool, Error> {
        if row != self.next_row() {
            return Err(Error::Row);
        }
        if self.free_rows.pop().is_none() {
            self.rows += 1;
        }
        self.tasks.push_back(Task { row, post, command });
        Ok(self.tasks.len() >= BATCH)
    }
    fn begin(&mut self) -> u64 { self.begin_level(None) }
    fn begin_level(&mut self,level:Option<LevelLease>) -> u64 {
        let cursor = Cursor {
            limit: self.tasks.len().min(BATCH),
            phase: Phase::Pre,
            position: 0,
            visited: 0,
            waiting: false,
            frame:None,
            level,
        };
        if let Some(index) = self.free_cursors.pop() {
            self.cursors[index] = Some(cursor);
            index as u64 + 1
        } else {
            self.cursors.push(Some(cursor));
            self.cursors.len() as u64
        }
    }
    fn end(&mut self, id: u64) -> Result<(), Error> {
        let index = id.checked_sub(1).ok_or(Error::Cursor)? as usize;
        let cursor = self.cursors.get_mut(index).ok_or(Error::Cursor)?;
        if cursor.take().is_none() {
            return Err(Error::Cursor);
        }
        self.free_cursors.push(index);
        Ok(())
    }
    fn remove_front(&mut self) -> Result<(), Error> {
        // ObjectArrayList's post-pass iterator always removes index zero.
        // A nested pass may already have removed it: preserve that failure.
        let task = self.tasks.pop_front().ok_or(Error::RemoveEmpty)?;
        self.free_rows.push(task.row);
        self.retired.push(task.row);
        Ok(())
    }
    fn advance(&mut self, c: &mut Cursor) -> Result<Action, Error> {
        if c.frame.is_some(){return Err(Error::Cursor);}
        match c.phase {
            Phase::Pre => {
                if c.waiting {
                    c.visited += 1;
                    c.waiting = false;
                }
                while c.position < self.tasks.len() && c.visited < c.limit {
                    let task = &self.tasks[c.position];
                    c.position += 1;
                    if !task.post {
                        c.waiting = true;
                        if let Some(command) = &task.command {
                            match command {
                                Command::Check(check)=>check.level.check(check.pos)?,
                                Command::Retain{level,key,value}=>{
                                    let status=unsafe{((*level.owner).operation)(level.owner,0,*key,i64::from(*value),std::ptr::null())};
                                    if status!=0{return Err(Error::Lighting(status));}
                                }
                                Command::Import{level,channel,..}=>{
                                    let target=unsafe{((*level.owner).channel)(level.owner,*channel)};
                                    if target>=0{return Ok(Action::Import(task.row,target));}
                                }
                                Command::Level{level,kind,key,value}=>{
                                    let frame=unsafe{((*level.owner).start)(level.owner,*kind,*key,*value)};
                                    if !frame.is_null(){c.frame=Some(FrameLease{owner:frame});return Ok(Action::Context(task.row));}
                                }
                            }
                            c.visited += 1;
                            c.waiting = false;
                            continue;
                        }
                        return Ok(Action::Callable(task.row));
                    }
                    c.visited += 1;
                }
                // The original iterator.back(j) resets its cursor to zero here,
                // including after nested mutations. Do not snapshot its tasks.
                c.phase = Phase::Post;
                c.position = 0;
                c.visited = 0;
                c.waiting = false;
                if let Some(level)=&c.level {
                    let frame=unsafe{((*level.owner).start)(level.owner,3,0,false)};
                    if frame.is_null(){return self.advance(c);}
                    c.frame=Some(FrameLease{owner:frame});
                    Ok(Action::Context(0))
                }else{Ok(Action::Propagate)}
            }
            Phase::Post => {
                if c.waiting {
                    self.remove_front()?;
                    c.visited += 1;
                    c.waiting = false;
                }
                while !self.tasks.is_empty() && c.visited < c.limit {
                    let task = &self.tasks[0];
                    if task.post {
                        c.waiting = true;
                        return Ok(Action::Callable(task.row));
                    }
                    self.remove_front()?;
                    c.visited += 1;
                }
                Ok(Action::Done)
            }
        }
    }
    fn next(&mut self, id: u64) -> Result<Action, Error> {
        let index = id.checked_sub(1).ok_or(Error::Cursor)? as usize;
        let mut cursor = self
            .cursors
            .get_mut(index)
            .and_then(Option::take)
            .ok_or(Error::Cursor)?;
        let result = self.advance(&mut cursor);
        self.cursors[index] = Some(cursor);
        result
    }
    fn apply_import(&self,id:u64,entry:*const c_void)->Result<(),Error>{
        let cursor=self.cursors.get(id.checked_sub(1).ok_or(Error::Cursor)? as usize).and_then(Option::as_ref).ok_or(Error::Cursor)?;
        if !matches!(cursor.phase,Phase::Pre)||!cursor.waiting{return Err(Error::Cursor);}
        let task=self.tasks.get(cursor.position.checked_sub(1).ok_or(Error::Cursor)?).ok_or(Error::Cursor)?;
        let Some(Command::Import{level,channel,key})=&task.command else{return Err(Error::Cursor);};
        let status=unsafe{((*level.owner).operation)(level.owner,1,*key,i64::from(*channel),entry)};
        if status==0{Ok(())}else{Err(Error::Lighting(status))}
    }
    unsafe fn context(&mut self,id:u64,advance:bool,scalar:i64,entry:*const c_void,output:*mut i64)->i32{
        let Some(cursor)=id.checked_sub(1).and_then(|id|self.cursors.get_mut(id as usize)).and_then(Option::as_mut) else{return -27;};
        let Some(frame)=cursor.frame.as_ref() else{return -27;};
        let action=if advance{((*frame.owner).step)(frame.owner,scalar,entry,output)}else{((*frame.owner).read)(frame.owner,output)};
        if action==0{cursor.frame=None;}
        action
    }
    unsafe fn context_resource(&mut self,id:u64)->*const c_void{
        self.cursors.get_mut(id.wrapping_sub(1) as usize).and_then(Option::as_mut)
            .and_then(|cursor|cursor.frame.as_ref()).map_or(std::ptr::null(),|frame|((*frame.owner).resource)(frame.owner))
    }
    fn abort_context(&mut self,id:u64){
        if let Some(cursor)=id.checked_sub(1).and_then(|id|self.cursors.get_mut(id as usize)).and_then(Option::as_mut){cursor.frame=None;}
    }
    fn allocation_bytes(&self) -> usize {
        self.tasks.capacity() * std::mem::size_of::<Task>()
            + self.free_rows.capacity() * 8
            + self.cursors.capacity() * std::mem::size_of::<Option<Cursor>>()
            + self.free_cursors.capacity() * std::mem::size_of::<usize>()
            + self.retired.capacity() * 8
    }
}
