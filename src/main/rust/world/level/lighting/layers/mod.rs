//! Rust-owned live light generations and leased CPU reads. No rendering dependencies.
use std::sync::atomic::{AtomicBool, AtomicU8, Ordering};
use std::sync::{Arc, Mutex};

const BYTES: usize = 2048;
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

pub struct Layer {
    valid: AtomicBool,
    generation: Mutex<Arc<Generation>>,
}

impl Layer {
    /// Mutable Java array escape ends authority; old generation leases stay valid.
    pub fn invalidate(&self) {
        self.valid.store(false, Ordering::Release);
    }
    pub fn is_valid(&self) -> bool {
        self.valid.load(Ordering::Acquire)
    }

    pub fn new(default: i32) -> Self {
        Self {
            valid: AtomicBool::new(true),
            generation: Mutex::new(Arc::new(Generation::lazy(default))),
        }
    }

    pub fn from_bytes(values: [u8; BYTES]) -> Self {
        Self {
            valid: AtomicBool::new(true),
            generation: Mutex::new(Arc::new(Generation::allocated(0, values))),
        }
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
        Self {
            valid: AtomicBool::new(true),
            generation: Mutex::new(Arc::new(copy)),
        }
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
        *self.generation.lock().unwrap() = Arc::new(Generation::lazy(default));
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
        Self::initialize(&mut self.generation.lock().unwrap())
    }

    /// Caller exclusion follows the existing light-storage transaction. Atomic
    /// cells also keep retained CPU reads valid; they do not add map publication.
    pub fn set(&self, index: i32, value: i32) -> Result<bool, InvalidByteIndex> {
        let mut generation = self.generation.lock().unwrap();
        let changed_generation = Self::initialize(&mut generation);
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
