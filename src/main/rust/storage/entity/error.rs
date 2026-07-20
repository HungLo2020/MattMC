#[repr(i32)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum EntityErrorKind {
    InvalidArgument = 1,
    UnsupportedDataVersion = 2,
    MissingField = 3,
    WrongType = 4,
    InvalidPosition = 5,
    OutputTooSmall = 6,
    Overflow = 7,
    NbtEncodeFailed = 8,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct EntityError {
    pub kind: EntityErrorKind,
    pub message: String,
}

impl EntityError {
    pub fn new(kind: EntityErrorKind, message: impl Into<String>) -> Self {
        Self {
            kind,
            message: message.into(),
        }
    }
}

pub type EntityResult<T> = Result<T, EntityError>;
