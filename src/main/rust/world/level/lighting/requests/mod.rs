//! Actual threaded initialization/light/unload policy. The owner retains the
//! existing level allocation; each invocation owns its live cursor and child
//! continuation. No native borrow survives a VM effect.
use super::{
    propagation,
    task_batch::{FrameToken, LevelToken},
};
use std::{
    ffi::c_void,
    panic::{catch_unwind, AssertUnwindSafe},
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc,
    },
};

static OWNERS: AtomicU64 = AtomicU64::new(0);
static FRAMES: AtomicU64 = AtomicU64::new(0);
struct LevelLease(*const LevelToken);
impl Drop for LevelLease {
    fn drop(&mut self) {
        unsafe {
            ((*self.0).release)(self.0);
        }
    }
}
struct Child(*mut FrameToken);
impl Drop for Child {
    fn drop(&mut self) {
        unsafe {
            ((*self.0).release)(self.0);
        }
    }
}
pub(super) struct Request {
    level: LevelLease,
    kind: i32,
    key: i64,
    value: bool,
}
impl Drop for Request {
    fn drop(&mut self) {
        OWNERS.fetch_sub(1, Ordering::Relaxed);
    }
}
#[derive(Clone, Copy)]
enum Stage {
    Array,
    Count,
    Air,
    SectionY,
    InitStatus,
    InitNext,
    EnableInit,
    RetainDone,
    Sources,
    Verbose,
    Log,
    Correct,
    RetainUnload,
    EnableUnload,
    MinLight,
    CheckLight,
    ClearBlock,
    ClearSky,
    NextLight,
    MinSection,
    CheckSection,
    EmptyStatus,
    NextSection,
    Done,
}
struct Frame {
    request: Arc<Request>,
    stage: Stage,
    after_child: Stage,
    child: Option<Child>,
    index: i32,
    section: i32,
    import_applied: bool,
    action: i32,
    reply: [i64; 7],
}
impl Drop for Frame {
    fn drop(&mut self) {
        FRAMES.fetch_sub(1, Ordering::Relaxed);
    }
}
impl Frame {
    fn section_key(&self, y: i32) -> i64 {
        propagation::section_pos(self.request.key as i32, y, (self.request.key >> 32) as i32)
    }
    fn effect(&mut self, action: i32, value: i64) -> i32 {
        self.reply.fill(0);
        self.reply[0] = value;
        action
    }
    unsafe fn begin_child(
        &mut self,
        kind: i32,
        key: i64,
        value: bool,
        after: Stage,
    ) -> Option<i32> {
        let level = self.request.level.0;
        let token = ((*level).start)(level, kind, key, value);
        self.after_child = after;
        if token.is_null() {
            self.stage = after;
            return None;
        }
        self.child = Some(Child(token));
        let action = ((*token).read)(token, self.reply.as_mut_ptr());
        if action == 0 {
            self.child = None;
            self.stage = after;
            None
        } else {
            Some(action)
        }
    }
    unsafe fn retain_false(&self) -> i32 {
        let level = self.request.level.0;
        ((*level).operation)(level, 0, self.section_key(0), 0, std::ptr::null())
    }
    unsafe fn import_effect(&mut self, channel: i32, next: Stage) -> Option<i32> {
        let level = self.request.level.0;
        let target = ((*level).channel)(level, channel);
        if target < 0 {
            self.stage = next;
            return None;
        }
        self.import_applied = false;
        self.effect(44, self.section_key(self.index));
        self.reply[6] = i64::from(target);
        Some(44)
    }
    unsafe fn drive(&mut self) -> i32 {
        loop {
            match self.stage {
                Stage::Array => return self.effect(32, 0),
                Stage::Count => return self.effect(33, 0),
                Stage::Air => return self.effect(34, i64::from(self.index)),
                Stage::SectionY => return self.effect(35, i64::from(self.index)),
                Stage::InitStatus => {
                    if let Some(a) =
                        self.begin_child(0, self.section_key(self.section), false, Stage::InitNext)
                    {
                        return a;
                    }
                }
                Stage::InitNext => {
                    self.index = self.index.wrapping_add(1);
                    self.stage = Stage::Count;
                }
                Stage::EnableInit => {
                    if let Some(a) =
                        self.begin_child(1, self.request.key, self.request.value, Stage::RetainDone)
                    {
                        return a;
                    }
                }
                Stage::RetainDone => {
                    let s = self.retain_false();
                    if s != 0 {
                        return s;
                    }
                    self.stage = Stage::Done;
                }
                Stage::Sources => {
                    if self.request.value {
                        self.stage = Stage::Verbose;
                    } else if let Some(a) =
                        self.begin_child(2, self.request.key, false, Stage::Verbose)
                    {
                        return a;
                    }
                }
                Stage::Verbose => return self.effect(37, 0),
                Stage::Log => return self.effect(38, 0),
                Stage::Correct => return self.effect(39, 1),
                Stage::RetainUnload => {
                    let s = self.retain_false();
                    if s != 0 {
                        return s;
                    }
                    self.stage = Stage::EnableUnload;
                }
                Stage::EnableUnload => {
                    if let Some(a) = self.begin_child(1, self.request.key, false, Stage::MinLight) {
                        return a;
                    }
                }
                Stage::MinLight => return self.effect(40, 0),
                Stage::CheckLight => return self.effect(41, 0),
                Stage::ClearBlock => {
                    if let Some(a) = self.import_effect(1, Stage::ClearSky) {
                        return a;
                    }
                }
                Stage::ClearSky => {
                    if let Some(a) = self.import_effect(0, Stage::NextLight) {
                        return a;
                    }
                }
                Stage::NextLight => {
                    self.index = self.index.wrapping_add(1);
                    self.stage = Stage::CheckLight;
                }
                Stage::MinSection => return self.effect(42, 0),
                Stage::CheckSection => return self.effect(43, 0),
                Stage::EmptyStatus => {
                    if let Some(a) =
                        self.begin_child(0, self.section_key(self.index), true, Stage::NextSection)
                    {
                        return a;
                    }
                }
                Stage::NextSection => {
                    self.index = self.index.wrapping_add(1);
                    self.stage = Stage::CheckSection;
                }
                Stage::Done => {
                    self.reply.fill(0);
                    return 0;
                }
            }
        }
    }
    unsafe fn step(&mut self, scalar: i64, entry: *const c_void) -> i32 {
        if self.action <= 0 {
            return self.action;
        }
        if let Some(child) = self.child.as_ref() {
            let token = child.0;
            let a = ((*token).step)(token, scalar, entry, self.reply.as_mut_ptr());
            if a != 0 {
                return a;
            }
            self.child = None;
            self.stage = self.after_child;
        } else {
            self.stage = match self.stage {
                Stage::Array => Stage::Count,
                Stage::Count => {
                    if self.index < scalar as i32 {
                        Stage::Air
                    } else {
                        Stage::Done
                    }
                }
                Stage::Air => {
                    if scalar != 0 {
                        Stage::InitNext
                    } else {
                        Stage::SectionY
                    }
                }
                Stage::SectionY => {
                    self.section = scalar as i32;
                    Stage::InitStatus
                }
                Stage::Verbose => {
                    if scalar != 0 {
                        Stage::Log
                    } else {
                        Stage::Done
                    }
                }
                Stage::Log | Stage::Correct => Stage::Done,
                Stage::MinLight => {
                    self.index = scalar as i32;
                    Stage::CheckLight
                }
                Stage::CheckLight => {
                    if self.index < scalar as i32 {
                        Stage::ClearBlock
                    } else {
                        Stage::MinSection
                    }
                }
                Stage::ClearBlock => {
                    if !self.import_applied {
                        return -27;
                    }
                    Stage::ClearSky
                }
                Stage::ClearSky => {
                    if !self.import_applied {
                        return -27;
                    }
                    Stage::NextLight
                }
                Stage::MinSection => {
                    self.index = scalar as i32;
                    Stage::CheckSection
                }
                Stage::CheckSection => {
                    if self.index <= scalar as i32 {
                        Stage::EmptyStatus
                    } else {
                        Stage::Done
                    }
                }
                _ => return -27,
            };
        }
        self.drive()
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_create(
    level: *const LevelToken,
    kind: i32,
    key: i64,
    value: i32,
) -> *const Request {
    if level.is_null() || !(0..=2).contains(&kind) {
        return std::ptr::null();
    }
    ((*level).retain)(level);
    let owner = Arc::new(Request {
        level: LevelLease(level),
        kind,
        key,
        value: value != 0,
    });
    OWNERS.fetch_add(1, Ordering::Relaxed);
    Arc::into_raw(owner)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_release(owner: *const Request) {
    if !owner.is_null() {
        drop(Arc::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_begin(owner: *const Request, post: i32) -> u64 {
    if owner.is_null() {
        return 0;
    }
    let stage = match ((*owner).kind, post != 0) {
        (0, false) => Stage::Array,
        (0, true) => Stage::EnableInit,
        (1, false) => Stage::Sources,
        (1, true) => Stage::Correct,
        (2, false) => Stage::RetainUnload,
        _ => return 0,
    };
    Arc::increment_strong_count(owner);
    let mut frame = Box::new(Frame {
        request: Arc::from_raw(owner),
        stage,
        after_child: Stage::Done,
        child: None,
        index: 0,
        section: 0,
        import_applied: false,
        action: 0,
        reply: [0; 7],
    });
    FRAMES.fetch_add(1, Ordering::Relaxed);
    frame.action = catch_unwind(AssertUnwindSafe(|| frame.drive())).unwrap_or(-99);
    Box::into_raw(frame) as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_read(id: u64, output: *mut i64) -> i32 {
    if id == 0 || output.is_null() {
        return -27;
    }
    let frame = &*(id as *const Frame);
    std::ptr::copy_nonoverlapping(frame.reply.as_ptr(), output, 7);
    frame.action
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_step(
    id: u64,
    scalar: i64,
    entry: *const c_void,
    output: *mut i64,
) -> i32 {
    if id == 0 || output.is_null() {
        return -27;
    }
    let frame = &mut *(id as *mut Frame);
    frame.action = catch_unwind(AssertUnwindSafe(|| frame.step(scalar, entry))).unwrap_or(-99);
    mattmc_light_request_read(id, output)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_bind_source(id:u64,source:*const super::source_owner::Source)->i32 {
    if id==0 || source.is_null() { return -27; }
    catch_unwind(AssertUnwindSafe(|| {
        let frame=&mut *(id as *mut Frame);
        if !matches!(frame.action,8|17) { return -27; }
        frame.child.as_ref().map_or(-27,|child|((*child.0).bind_source)(child.0,source))
    })).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_import(id: u64) -> i32 {
    if id == 0 {
        return -27;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let frame = &mut *(id as *mut Frame);
        let channel = match frame.stage {
            Stage::ClearBlock => 1,
            Stage::ClearSky => 0,
            _ => return -27,
        };
        if frame.action != 44 || frame.import_applied {
            return -27;
        }
        let level = frame.request.level.0;
        let status = ((*level).operation)(
            level,
            1,
            frame.section_key(frame.index),
            channel,
            std::ptr::null(),
        );
        if status == 0 {
            frame.import_applied = true;
        }
        status
    }))
    .unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_resource(id: u64) -> *const c_void {
    if id == 0 {
        return std::ptr::null();
    }
    let frame = &*(id as *const Frame);
    frame
        .child
        .as_ref()
        .map_or(std::ptr::null(), |child| ((*child.0).resource)(child.0))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_request_end(id: u64) {
    if id != 0 {
        drop(Box::from_raw(id as *mut Frame));
    }
}
#[no_mangle]
pub extern "C" fn mattmc_light_request_live(which: i32) -> u64 {
    if which == 0 {
        OWNERS.load(Ordering::Relaxed)
    } else {
        FRAMES.load(Ordering::Relaxed)
    }
}
