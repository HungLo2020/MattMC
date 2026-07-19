use super::error::{NbtError, NbtErrorKind, NbtResult};

#[derive(Clone, Copy, Debug)]
pub struct NbtLimits {
    pub max_depth: u32,
    pub max_collection_len: u32,
    pub max_alloc_bytes: u64,
    pub max_total_bytes: u64,
}

impl NbtLimits {
    pub const DEFAULT_MAX_DEPTH: u32 = 512;
    pub const DEFAULT_MAX_COLLECTION_LEN: u32 = 16 * 1024 * 1024;
    pub const DEFAULT_MAX_ALLOC_BYTES: u64 = 256 * 1024 * 1024;
    pub const DEFAULT_MAX_TOTAL_BYTES: u64 = 256 * 1024 * 1024;

    pub fn defaults() -> Self {
        Self {
            max_depth: Self::DEFAULT_MAX_DEPTH,
            max_collection_len: Self::DEFAULT_MAX_COLLECTION_LEN,
            max_alloc_bytes: Self::DEFAULT_MAX_ALLOC_BYTES,
            max_total_bytes: Self::DEFAULT_MAX_TOTAL_BYTES,
        }
    }

    pub fn from_ffi(
        max_depth: u32,
        max_collection_len: u32,
        max_alloc_bytes: u64,
        max_total_bytes: u64,
    ) -> Self {
        Self {
            max_depth: if max_depth == 0 {
                Self::DEFAULT_MAX_DEPTH
            } else {
                max_depth
            },
            max_collection_len: if max_collection_len == 0 {
                Self::DEFAULT_MAX_COLLECTION_LEN
            } else {
                max_collection_len
            },
            max_alloc_bytes: if max_alloc_bytes == 0 {
                Self::DEFAULT_MAX_ALLOC_BYTES
            } else {
                max_alloc_bytes
            },
            max_total_bytes: if max_total_bytes == 0 {
                Self::DEFAULT_MAX_TOTAL_BYTES
            } else {
                max_total_bytes
            },
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub struct LimitTracker {
    limits: NbtLimits,
    allocated: u64,
    touched: u64,
}

impl LimitTracker {
    pub fn new(limits: NbtLimits) -> Self {
        Self {
            limits,
            allocated: 0,
            touched: 0,
        }
    }

    pub fn enter_depth(&self, depth: u32, offset: usize) -> NbtResult<()> {
        if depth > self.limits.max_depth {
            Err(NbtError::new(NbtErrorKind::DepthLimit, offset))
        } else {
            Ok(())
        }
    }

    pub fn collection_len(
        &mut self,
        len: i32,
        element_size: u64,
        offset: usize,
    ) -> NbtResult<usize> {
        if len < 0 {
            return Err(NbtError::new(NbtErrorKind::NegativeLength, offset));
        }
        let len = len as u32;
        if len > self.limits.max_collection_len {
            return Err(NbtError::new(NbtErrorKind::ExcessiveLength, offset));
        }
        let bytes = (len as u64)
            .checked_mul(element_size)
            .ok_or_else(|| NbtError::new(NbtErrorKind::Overflow, offset))?;
        self.allocate(bytes, offset)?;
        Ok(len as usize)
    }

    pub fn allocate(&mut self, bytes: u64, offset: usize) -> NbtResult<()> {
        self.allocated = self
            .allocated
            .checked_add(bytes)
            .ok_or_else(|| NbtError::new(NbtErrorKind::Overflow, offset))?;
        if self.allocated > self.limits.max_alloc_bytes {
            return Err(NbtError::new(NbtErrorKind::AllocationLimit, offset));
        }
        Ok(())
    }

    pub fn touch(&mut self, bytes: usize, offset: usize) -> NbtResult<()> {
        self.touched = self
            .touched
            .checked_add(bytes as u64)
            .ok_or_else(|| NbtError::new(NbtErrorKind::Overflow, offset))?;
        if self.touched > self.limits.max_total_bytes {
            return Err(NbtError::new(NbtErrorKind::TotalByteLimit, offset));
        }
        Ok(())
    }
}
