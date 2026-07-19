#[repr(i32)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NbtErrorKind {
    InvalidArgument = 1,
    InvalidLength = 2,
    UnexpectedEof = 3,
    InvalidTagType = 4,
    InvalidRootType = 5,
    NegativeLength = 6,
    ExcessiveLength = 7,
    DepthLimit = 8,
    AllocationLimit = 9,
    TotalByteLimit = 10,
    InvalidModifiedUtf8 = 11,
    ModifiedUtf8TooLong = 12,
    MissingListElementType = 13,
    TrailingData = 14,
    Overflow = 15,
    OutputTooSmall = 16,
    EndTagHasPayload = 17,
    UnsupportedCompression = 18,
    CompressionError = 19,
    CompressedSizeLimit = 20,
    DecompressedSizeLimit = 21,
    TrailingCompressedData = 22,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct NbtError {
    pub kind: NbtErrorKind,
    pub offset: usize,
}

impl NbtError {
    pub fn new(kind: NbtErrorKind, offset: usize) -> Self {
        Self { kind, offset }
    }
}

pub type NbtResult<T> = Result<T, NbtError>;
