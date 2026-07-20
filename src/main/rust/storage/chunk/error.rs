#[repr(i32)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ChunkErrorKind {
    InvalidArgument = 1,
    MissingField = 2,
    WrongType = 3,
    UnsupportedDataVersion = 4,
    InvalidPosition = 5,
    InvalidPalette = 6,
    InvalidPackedData = 7,
    InvalidLightArray = 8,
    InvalidHeightmap = 9,
    InvalidString = 10,
    OutputTooSmall = 11,
    Overflow = 12,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ChunkError {
    pub kind: ChunkErrorKind,
    pub message: String,
}

impl ChunkError {
    pub fn new(kind: ChunkErrorKind, message: impl Into<String>) -> Self {
        Self {
            kind,
            message: message.into(),
        }
    }
}

pub type ChunkResult<T> = Result<T, ChunkError>;
