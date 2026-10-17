//! Rust-owned live light generations and leased CPU reads. No rendering dependencies.
use std::sync::atomic::{AtomicU8, AtomicU32, AtomicU64, AtomicI32, Ordering};
use std::sync::{Arc, Mutex};

pub(crate) const BYTES: usize = 2048;
pub(crate) mod ffi;

struct Generation {
    default: i32,
    bytes: Option<Box<[AtomicU8; BYTES]>>,
}

impl Generation {
    fn lazy(default: i32) -> Self {
        Self {
            default,
            bytes: None,
        }
    }

    fn allocated(default: i32, values: [u8; BYTES]) -> Self {
        Self {
            default,
            bytes: Some(Box::new(values.map(AtomicU8::new))),
        }
    }

    fn snapshot_bytes(&self) -> Option<[u8; BYTES]> {
        self.bytes
            .as_ref()
            .map(|bytes| std::array::from_fn(|i| bytes[i].load(Ordering::Relaxed)))
    }
}

/// A leased CPU projection. Metadata is immutable; allocated cells reflect
/// mutations of this generation. A fill detaches the owner, preserving old views.
pub struct View {
    generation: Arc<Generation>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct InvalidByteIndex(pub i32);

impl View {
    pub fn default_value(&self) -> i32 {
        self.generation.default
    }

    pub fn allocated(&self) -> bool {
        self.generation.bytes.is_some()
    }
    pub(crate) fn is_empty(&self)->bool{
        // DataLayer.isEmpty tests definite lazy homogeneity, not byte content.
        self.generation.bytes.is_none() && self.generation.default==0
    }

    pub fn get(&self, index: i32) -> Result<i32, InvalidByteIndex> {
        let Some(bytes) = &self.generation.bytes else {
            // Original lazy layers do not inspect the index.
            return Ok(self.generation.default);
        };
        let byte_index = index >> 1;
        let value = usize::try_from(byte_index)
            .ok()
            .and_then(|i| bytes.get(i))
            .ok_or(InvalidByteIndex(byte_index))?
            .load(Ordering::Relaxed);
        Ok(i32::from((value >> ((index & 1) * 4)) & 15))
    }

    pub(crate) fn copy_bytes_into(&self, output: &mut [u8; BYTES]) -> bool {
        let Some(bytes) = &self.generation.bytes else {
            return false;
        };
        for (target, source) in output.iter_mut().zip(bytes.iter()) {
            *target = source.load(Ordering::Relaxed);
        }
        true
    }

    pub fn bytes_address(&self) -> Option<*const AtomicU8> {
        self.generation.bytes.as_ref().map(|bytes| bytes.as_ptr())
    }
}

/// Metadata belongs to the actual primary owner. It does not lease a generation
/// or expose unpinned byte addresses. A Java reader acquires a View only when it
/// needs that generation; the identity names the allocation that View retains.
#[repr(C)]
pub(crate) struct Header {
    default: AtomicI32,
    allocated: AtomicU32,
    generation: AtomicU64,
    valid: AtomicU32,
}

pub struct Layer {
    header: Header,
    generation: Mutex<Arc<Generation>>,
}

impl Layer {
    /// Mutable Java array escape ends authority; old generation leases stay valid.
    pub fn invalidate(&self) {
        let mut generation = self.generation.lock().unwrap();
        self.header.valid.store(0, Ordering::Release);
        // The escaped Java array is now authoritative. Keep the primary
        // identity, but do not retain a second native payload through it.
        // Explicit old Views independently retain their original allocation.
        if generation.bytes.is_some() {
            *generation = Arc::new(Generation::lazy(generation.default));
            self.publish_generation(&generation);
        }
    }
    pub fn is_valid(&self) -> bool {
        self.header.valid.load(Ordering::Acquire) != 0
    }

    fn from_generation(generation: Generation) -> Self {
        let generation = Arc::new(generation);
        let header = Header {
            default: AtomicI32::new(generation.default),
            allocated: AtomicU32::new(u32::from(generation.bytes.is_some())),
            generation: AtomicU64::new(Arc::as_ptr(&generation) as u64),
            valid: AtomicU32::new(1),
        };
        Self { header, generation: Mutex::new(generation) }
    }

    fn publish_generation(&self, generation: &Arc<Generation>) {
        self.header.default.store(generation.default, Ordering::Relaxed);
        self.header.allocated.store(u32::from(generation.bytes.is_some()), Ordering::Relaxed);
        self.header.generation.store(Arc::as_ptr(generation) as u64, Ordering::Release);
    }

    pub fn new(default: i32) -> Self {
        Self::from_generation(Generation::lazy(default))
    }

    pub fn from_bytes(values: [u8; BYTES]) -> Self {
        Self::from_generation(Generation::allocated(0, values))
    }

    /// Propagation publication keeps this layer's raw default, exactly as
    /// writing into its materialized byte array would. Map COW remains external.
    pub fn install_bytes(&self, values: [u8; BYTES]) -> bool {
        let mut generation = self.generation.lock().unwrap();
        if let Some(bytes) = &generation.bytes {
            // Match writes into an existing DataLayer array: retained views of
            // this generation see the update. Map copy-on-write is external.
            for (cell, value) in bytes.iter().zip(values) {
                cell.store(value, Ordering::Relaxed);
            }
            false
        } else {
            *generation = Arc::new(Generation::allocated(generation.default, values));
            self.publish_generation(&generation);
            true
        }
    }

    pub fn view(&self) -> View {
        View {
            generation: Arc::clone(&self.generation.lock().unwrap()),
        }
    }

    pub fn copy(&self) -> Self {
        let generation = self.generation.lock().unwrap();
        let copy = match generation.snapshot_bytes() {
            // The original allocated copy invokes the byte-array constructor,
            // whose raw default is zero even if its source retained another one.
            Some(bytes) => Generation::allocated(0, bytes),
            None => Generation::lazy(generation.default),
        };
        Self::from_generation(copy)
    }

    pub fn repeat_first(&self) -> Self {
        let view = self.view();
        let mut bytes = [0; BYTES];
        if !view.copy_bytes_into(&mut bytes) {
            return Self::new(view.default_value());
        }
        for row in 1..16 {
            let (before, after) = bytes.split_at_mut(row * 128);
            after[..128].copy_from_slice(&before[..128]);
        }
        Self::from_bytes(bytes)
    }

    pub fn fill(&self, default: i32) {
        let mut generation = self.generation.lock().unwrap();
        *generation = Arc::new(Generation::lazy(default));
        self.publish_generation(&generation);
        // A fill replaces the escaped array at the same stable light identity.
        self.header.valid.store(1, Ordering::Release);
    }

    fn initialize(generation: &mut Arc<Generation>) -> bool {
        if generation.bytes.is_some() {
            return false;
        }
        let default = generation.default;
        // Preserve the original byte cast/OR for raw values outside 0..15.
        let packed = (default as u8) | (default.wrapping_shl(4) as u8);
        *generation = Arc::new(Generation::allocated(default, [packed; BYTES]));
        true
    }

    pub fn materialize(&self) -> bool {
        let mut generation = self.generation.lock().unwrap();
        let changed = Self::initialize(&mut generation);
        if changed { self.publish_generation(&generation); }
        changed
    }

    /// The public byte-array escape needs its bytes, not an intermediate Java
    /// generation lease. Keep initialization and export in one native read.
    pub(crate) fn export_materialized(&self, output: &mut [u8; BYTES]) {
        let mut generation = self.generation.lock().unwrap();
        if Self::initialize(&mut generation) { self.publish_generation(&generation); }
        for (target, source) in output.iter_mut().zip(generation.bytes.as_ref().unwrap().iter()) {
            *target = source.load(Ordering::Relaxed);
        }
    }

    /// Caller exclusion follows the existing light-storage transaction. Atomic
    /// cells also keep retained CPU reads valid; they do not add map publication.
    pub fn set(&self, index: i32, value: i32) -> Result<bool, InvalidByteIndex> {
        let mut generation = self.generation.lock().unwrap();
        let changed_generation = Self::initialize(&mut generation);
        if changed_generation { self.publish_generation(&generation); }
        let byte_index = index >> 1;
        let cell = usize::try_from(byte_index)
            .ok()
            .and_then(|i| generation.bytes.as_ref().unwrap().get(i))
            .ok_or(InvalidByteIndex(byte_index))?;
        let shift = (index & 1) * 4;
        let previous = cell.load(Ordering::Relaxed);
        cell.store(
            (previous & !(15 << shift)) | (((value & 15) << shift) as u8),
            Ordering::Relaxed,
        );
        Ok(changed_generation)
    }
}

#[cfg(test)]
mod tests;
