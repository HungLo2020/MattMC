use std::fmt;

#[repr(i32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StatusCode {
    Ok = 0,
    InvalidArgument = -1,
    NullPointer = -2,
    LengthOverflow = -3,
    Alignment = -4,
    UnknownEnum = -5,
    WrongHandleType = -6,
    StaleHandle = -7,
    DoubleDestroy = -8,
    DependencyViolation = -9,
    InFlight = -10,
    BackendFailure = -11,
    GenerationExhausted = -12,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ErrorDomain {
    General = 1,
    Handle = 2,
    Resource = 3,
    Command = 4,
    Submission = 5,
    Ffi = 6,
    Backend = 7,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GalError {
    pub domain: ErrorDomain,
    pub code: StatusCode,
    pub message: String,
}

pub type GalResult<T> = Result<T, GalError>;

impl GalError {
    pub fn new(domain: ErrorDomain, code: StatusCode, message: impl Into<String>) -> Self {
        Self {
            domain,
            code,
            message: message.into(),
        }
    }

    pub fn invalid_argument(message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::General, StatusCode::InvalidArgument, message)
    }

    pub fn handle(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Handle, code, message)
    }

    pub fn resource(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Resource, code, message)
    }

    pub fn command(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Command, code, message)
    }

    pub fn submission(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Submission, code, message)
    }

    pub fn ffi(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Ffi, code, message)
    }

    pub fn backend(message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Backend, StatusCode::BackendFailure, message)
    }
}

impl fmt::Display for GalError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "{:?}/{:?}: {}",
            self.domain, self.code, self.message
        )
    }
}

impl std::error::Error for GalError {}
