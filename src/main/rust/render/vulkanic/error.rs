//! Errors: `GalError` with its domain and the `StatusCode` reported over the
//! C ABI.

use std::fmt;

/// Result codes shared with Java through the bridge. `Ok` is 0 and every
/// failure is negative; the values are part of the C ABI.
#[repr(i32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StatusCode {
    /// Success.
    Ok = 0,
    /// A request or descriptor is malformed or violates a GAL rule.
    InvalidArgument = -1,
    /// A required pointer was null (bridge only).
    NullPointer = -2,
    /// A length or count exceeds its bound or overflows.
    LengthOverflow = -3,
    /// A pointer or offset is misaligned.
    Alignment = -4,
    /// A wire enum value is not one the GAL knows.
    UnknownEnum = -5,
    /// A handle is of a different kind than the operation needs.
    WrongHandleType = -6,
    /// A handle is null, destroyed or from an older generation.
    StaleHandle = -7,
    /// A resource was destroyed twice.
    DoubleDestroy = -8,
    /// A resource cannot be destroyed while others depend on it.
    DependencyViolation = -9,
    /// A resource is still used by an incomplete submission.
    InFlight = -10,
    /// The backend failed to perform an operation.
    BackendFailure = -11,
    /// A handle slot has used all its generations.
    GenerationExhausted = -12,
    /// The backend lacks a capability the request needs.
    UnsupportedFeature = -13,
}

/// Which part of the GAL reported an error; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ErrorDomain {
    /// Not specific to one area.
    General = 1,
    /// Handle lookup and lifetime.
    Handle = 2,
    /// Resource creation, validation and capabilities.
    Resource = 3,
    /// Command recording and validation.
    Command = 4,
    /// Submission and hazard analysis.
    Submission = 5,
    /// The Java bridge.
    Ffi = 6,
    /// The backend implementation.
    Backend = 7,
}

/// A GAL failure: where it came from, its status code and a message.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GalError {
    /// The area that reported the error.
    pub domain: ErrorDomain,
    /// The status code reported to callers.
    pub code: StatusCode,
    /// A human-readable description for logs.
    pub message: String,
}

/// The result type of every fallible GAL operation.
pub type GalResult<T> = Result<T, GalError>;

impl GalError {
    /// An error with an explicit domain and code.
    pub fn new(domain: ErrorDomain, code: StatusCode, message: impl Into<String>) -> Self {
        Self {
            domain,
            code,
            message: message.into(),
        }
    }

    /// A general `InvalidArgument` error.
    pub fn invalid_argument(message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::General, StatusCode::InvalidArgument, message)
    }

    /// A handle-domain error.
    pub fn handle(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Handle, code, message)
    }

    pub(in crate::render::vulkanic) fn resource(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Resource, code, message)
    }

    /// A command-domain error.
    pub fn command(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Command, code, message)
    }

    /// A submission-domain error.
    pub fn submission(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Submission, code, message)
    }

    /// A bridge-domain error.
    pub fn ffi(code: StatusCode, message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Ffi, code, message)
    }

    /// A backend `BackendFailure` error.
    pub fn backend(message: impl Into<String>) -> Self {
        Self::new(ErrorDomain::Backend, StatusCode::BackendFailure, message)
    }

    /// An `UnsupportedFeature` error for a missing backend capability.
    pub fn unsupported_feature(message: impl Into<String>) -> Self {
        Self::new(
            ErrorDomain::Resource,
            StatusCode::UnsupportedFeature,
            message,
        )
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
