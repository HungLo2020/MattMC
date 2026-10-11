//! Log lines for the Java logger, through an upcall Java registers once
//! (`NativeLog`). Only Java threads call the native code that logs, so the
//! upcall runs on a thread the JVM knows. Without a sink (native tests) lines
//! go to stderr.

use std::sync::atomic::{AtomicPtr, Ordering};

use super::console;

/// Levels as `NativeLog` maps them (0 is info).
pub(crate) const WARN: i32 = 1;
pub(crate) const ERROR: i32 = 2;

type Sink = unsafe extern "C" fn(level: i32, message: *const u8, length: u64);

static SINK: AtomicPtr<()> = AtomicPtr::new(std::ptr::null_mut());

pub(crate) fn log(level: i32, message: &str) {
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
