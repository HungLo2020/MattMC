use std::fmt;
use std::io;

pub type PackResult<T> = Result<T, PackError>;

#[repr(i32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PackErrorKind {
    InvalidArgument = 1,
    Io = 2,
    Zip = 3,
    InvalidPath = 4,
    InvalidHandle = 5,
    NotFound = 6,
}

#[derive(Debug)]
pub struct PackError {
    pub kind: PackErrorKind,
    pub message: String,
}

impl PackError {
    pub fn new(kind: PackErrorKind, message: impl Into<String>) -> Self {
        Self {
            kind,
            message: message.into(),
        }
    }

    pub fn invalid_argument(message: impl Into<String>) -> Self {
        Self::new(PackErrorKind::InvalidArgument, message)
    }

    pub fn invalid_path(message: impl Into<String>) -> Self {
        Self::new(PackErrorKind::InvalidPath, message)
    }

    pub fn invalid_handle() -> Self {
        Self::new(PackErrorKind::InvalidHandle, "invalid or stale pack handle")
    }

    pub fn not_found() -> Self {
        Self::new(PackErrorKind::NotFound, "resource not found")
    }
}

impl fmt::Display for PackError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{:?}: {}", self.kind, self.message)
    }
}

impl std::error::Error for PackError {}

impl From<io::Error> for PackError {
    fn from(value: io::Error) -> Self {
        Self::new(PackErrorKind::Io, value.to_string())
    }
}

impl From<zip::result::ZipError> for PackError {
    fn from(value: zip::result::ZipError) -> Self {
        Self::new(PackErrorKind::Zip, value.to_string())
    }
}
