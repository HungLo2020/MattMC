//! Test/dev resource-pack byte and index subsystem.
//!
//! Java keeps pack selection, fallback-stack policy, metadata parsing, reload
//! listeners, resource consumers, and `ResourceLocation` objects. This module
//! owns a persistent handle to one directory or ZIP pack, indexes resource
//! paths, and returns bulk namespace/resource tapes or complete resource bytes.

mod directory;
mod errors;
mod handles;
mod index;
mod resource_path;
mod tape;
mod zip;

pub mod ffi;

#[cfg(test)]
mod tests;

use std::path::Path;

use errors::PackResult;
use handles::PackHandleTable;
use index::{NativePack, PackCounters, PackOpenStats};

pub use errors::{PackError, PackErrorKind};

pub fn open_directory(path: &Path) -> PackResult<(u64, PackOpenStats)> {
    let (pack, stats) = directory::open(path)?;
    let handle = PackHandleTable::global().lock().unwrap().insert(pack);
    Ok((handle, stats))
}

pub fn open_zip(path: &Path, prefix: &str) -> PackResult<(u64, PackOpenStats)> {
    let (pack, stats) = zip::open(path, prefix)?;
    let handle = PackHandleTable::global().lock().unwrap().insert(pack);
    Ok((handle, stats))
}

pub fn close(handle: u64) -> PackResult<()> {
    let pack = PackHandleTable::global().lock().unwrap().remove(handle)?;
    drop(pack);
    Ok(())
}

pub fn with_pack<T>(
    handle: u64,
    op: impl FnOnce(&mut NativePack) -> PackResult<T>,
) -> PackResult<T> {
    let pack = PackHandleTable::global().lock().unwrap().get(handle)?;
    let mut guard = pack.lock().unwrap();
    op(&mut guard)
}

pub fn counters(handle: u64) -> PackResult<PackCounters> {
    with_pack(handle, |pack| Ok(pack.counters()))
}
