//! Log lines for the Java logger, through an upcall Java registers once
//! (`NativeLog`). Only Java threads call the native code that logs, so the
//! upcall runs on a thread the JVM knows: native worker threads (see
//! [`crate::core::parallel`]) defer their lines, and the Java thread that
//! started them logs them after the workers join. Without a sink (native
//! tests) lines go to stderr.

use std::cell::RefCell;
use std::sync::atomic::{AtomicPtr, Ordering};

use super::console;

/// Levels as `NativeLog` maps them (0 is info).
pub(crate) const WARN: i32 = 1;
pub(crate) const ERROR: i32 = 2;

type Sink = unsafe extern "C" fn(level: i32, message: *const u8, length: u64);

static SINK: AtomicPtr<()> = AtomicPtr::new(std::ptr::null_mut());

thread_local! {
    /// Lines a native worker thread holds for its Java caller.
    static DEFERRED: RefCell<Option<Vec<(i32, String)>>> = const { RefCell::new(None) };
}

/// Makes this (native worker) thread hold its log lines.
pub(crate) fn defer() {
    DEFERRED.with(|deferred| *deferred.borrow_mut() = Some(Vec::new()));
}

/// The lines this thread held, which it stops holding.
pub(crate) fn take_deferred() -> Vec<(i32, String)> {
    DEFERRED.with(|deferred| deferred.borrow_mut().take().unwrap_or_default())
}

pub(crate) fn log(level: i32, message: &str) {
    let held = DEFERRED.with(|deferred| {
        let mut deferred = deferred.borrow_mut();
        match deferred.as_mut() {
            Some(lines) => {
                lines.push((level, message.to_owned()));
                true
            }
            None => false,
        }
    });
    if held {
        return;
    }
    let sink = SINK.load(Ordering::Acquire);
    if sink.is_null() {
        console::stderr(format_args!("{message}"));
        return;
    }
    let sink: Sink = unsafe { std::mem::transmute::<*mut (), Sink>(sink) };
    unsafe { sink(level, message.as_ptr(), message.len() as u64) };
}

pub(crate) fn warn(message: &str) {
    log(WARN, message);
}

pub(crate) fn error(message: &str) {
    log(ERROR, message);
}

/// Registers Java's log upcall (null removes it).
///
/// # Safety
/// `sink` must be null or an upcall that stays valid for the process.
#[no_mangle]
pub unsafe extern "C" fn mattmc_java_log_register(sink: Option<Sink>) {
    let pointer = sink.map_or(std::ptr::null_mut(), |sink| sink as *mut ());
    SINK.store(pointer, Ordering::Release);
}
