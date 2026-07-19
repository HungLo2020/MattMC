use std::fmt;
use std::io;

pub type RegionResult<T> = Result<T, RegionError>;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum RegionErrorKind {
    InvalidArgument = 1,
    PathEncoding = 2,
    Io = 3,
    TruncatedHeader = 4,
    OffsetInsideHeader = 5,
    ZeroSectorCount = 6,
    OutOfBoundsSector = 7,
    OverlappingSectors = 8,
    TruncatedChunkHeader = 9,
    ZeroDeclaredLength = 10,
    NegativeDeclaredLength = 11,
    PayloadLargerThanAllocation = 12,
    InvalidCompression = 13,
    CustomCompression = 14,
    MissingExternalFile = 15,
    TruncatedExternalFile = 16,
    InvalidExternalStub = 17,
    OutputTooSmall = 18,
    DecompressionError = 19,
    DecompressionSizeLimit = 20,
    Lz4InvalidHeader = 21,
    Lz4InvalidBlock = 22,
    Lz4ChecksumMismatch = 23,
    NbtParseError = 24,
}

#[derive(Debug)]
pub struct RegionError {
    pub kind: RegionErrorKind,
    pub offset: u64,
    pub message: String,
}

impl RegionError {
    pub fn new(kind: RegionErrorKind, offset: u64, message: impl Into<String>) -> Self {
        Self {
            kind,
            offset,
            message: message.into(),
        }
    }

    pub fn io(offset: u64, error: io::Error) -> Self {
        Self::new(RegionErrorKind::Io, offset, error.to_string())
    }
}

impl fmt::Display for RegionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{:?} at byte {}: {}",
            self.kind, self.offset, self.message
        )
    }
}

impl std::error::Error for RegionError {}
