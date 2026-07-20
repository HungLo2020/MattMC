#[repr(i32)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PoiErrorKind {
    InvalidArgument = 1,
    UnsupportedDataVersion = 2,
    MissingField = 3,
    WrongType = 4,
    InvalidSectionKey = 5,
    InvalidPosition = 6,
    InvalidPoiType = 7,
    OutputTooSmall = 8,
    Overflow = 9,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PoiError {
    pub kind: PoiErrorKind,
    pub message: String,
}

impl PoiError {
    pub fn new(kind: PoiErrorKind, message: impl Into<String>) -> Self {
        Self {
            kind,
            message: message.into(),
        }
    }
}

pub type PoiResult<T> = Result<T, PoiError>;
