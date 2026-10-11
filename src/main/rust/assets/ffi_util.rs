//! Shared helpers for the asset C ABIs: string arguments, owned result
//! buffers Java reads in place and frees, and a little-endian writer.

use std::slice;

pub(crate) unsafe fn str_arg<'a>(ptr: *const u8, len: u64) -> Option<&'a str> {
    if ptr.is_null() {
        return if len == 0 { Some("") } else { None };
    }
    std::str::from_utf8(unsafe { slice::from_raw_parts(ptr, usize::try_from(len).ok()?) }).ok()
}

pub(crate) unsafe fn bytes_arg<'a>(ptr: *const u8, len: u64) -> Option<&'a [u8]> {
    if ptr.is_null() {
        return if len == 0 { Some(&[]) } else { None };
    }
    Some(unsafe { slice::from_raw_parts(ptr, usize::try_from(len).ok()?) })
}

/// Hands `bytes` to Java: writes the pointer and length; Java frees it with
/// [`mattmc_buffer_free`].
pub(crate) unsafe fn give(bytes: Vec<u8>, out_ptr: *mut *mut u8, out_len: *mut u64) {
    let boxed = bytes.into_boxed_slice();
    let length = boxed.len() as u64;
    let pointer = Box::into_raw(boxed) as *mut u8;
    unsafe {
        *out_ptr = pointer;
        *out_len = length;
    }
}

/// Copies `text` when it fits; returns its length either way.
pub(crate) unsafe fn write_text(text: &str, out: *mut u8, capacity: u64) -> i64 {
    if !out.is_null() && text.len() as u64 <= capacity {
        unsafe { std::ptr::copy_nonoverlapping(text.as_ptr(), out, text.len()) };
    }
    text.len() as i64
}

/// Frees a buffer handed out by [`give`].
///
/// # Safety
/// `ptr`/`len` must come from one `give` and be freed once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_buffer_free(ptr: *mut u8, len: u64) {
    if !ptr.is_null() {
        drop(unsafe { Box::from_raw(slice::from_raw_parts_mut(ptr, len as usize)) });
    }
}

#[derive(Default)]
pub(crate) struct Writer {
    pub(crate) bytes: Vec<u8>,
}

impl Writer {
    pub(crate) fn u16(&mut self, value: u16) {
        self.bytes.extend_from_slice(&value.to_le_bytes());
    }

    pub(crate) fn u32(&mut self, value: u32) {
        self.bytes.extend_from_slice(&value.to_le_bytes());
    }

    pub(crate) fn i32(&mut self, value: i32) {
        self.bytes.extend_from_slice(&value.to_le_bytes());
    }

    pub(crate) fn u64(&mut self, value: u64) {
        self.bytes.extend_from_slice(&value.to_le_bytes());
    }

    pub(crate) fn text(&mut self, value: &str) {
        self.u32(value.len() as u32);
        self.bytes.extend_from_slice(value.as_bytes());
    }
}

/// A little-endian reader over Java-built records.
pub(crate) struct Reader<'a> {
    bytes: &'a [u8],
    at: usize,
}

impl<'a> Reader<'a> {
    pub(crate) fn new(bytes: &'a [u8]) -> Reader<'a> {
        Reader { bytes, at: 0 }
    }

    pub(crate) fn done(&self) -> bool {
        self.at >= self.bytes.len()
    }

    pub(crate) fn u8(&mut self) -> Option<u8> {
        let value = *self.bytes.get(self.at)?;
        self.at += 1;
        Some(value)
    }

    pub(crate) fn u32(&mut self) -> Option<u32> {
        let slice = self.bytes.get(self.at..self.at + 4)?;
        self.at += 4;
        Some(u32::from_le_bytes(slice.try_into().ok()?))
    }

    /// A u32-length-prefixed byte field.
    pub(crate) fn bytes(&mut self) -> Option<&'a [u8]> {
        let length = self.u32()? as usize;
        let slice = self.bytes.get(self.at..self.at + length)?;
        self.at += length;
        Some(slice)
    }

    pub(crate) fn text(&mut self) -> Option<&'a str> {
        let length = self.u32()? as usize;
        let slice = self.bytes.get(self.at..self.at + length)?;
        self.at += length;
        std::str::from_utf8(slice).ok()
    }
}
