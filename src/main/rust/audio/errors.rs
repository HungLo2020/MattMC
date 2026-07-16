use std::fmt;

use alto::AltoError;

pub const OK: i32 = 0;
pub const ERR_INVALID_HANDLE: i32 = -1;
pub const ERR_INVALID_ARGUMENT: i32 = -2;
pub const ERR_OPENAL_LOAD: i32 = -3;
pub const ERR_OPENAL_CALL: i32 = -4;
pub const ERR_POOL_EXHAUSTED: i32 = -6;
pub const ERR_UNSUPPORTED_FORMAT: i32 = -7;
pub const ERR_WRONG_THREAD: i32 = -8;

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum AudioError {
    InvalidHandle,
    InvalidArgument,
    OpenAlLoad(String),
    OpenAlCall(&'static str, String),
    PoolExhausted,
    UnsupportedFormat,
    WrongThread,
}

impl AudioError {
    pub(crate) fn status(&self) -> i32 {
        match self {
            AudioError::InvalidHandle => ERR_INVALID_HANDLE,
            AudioError::InvalidArgument => ERR_INVALID_ARGUMENT,
            AudioError::OpenAlLoad(_) => ERR_OPENAL_LOAD,
            AudioError::OpenAlCall(_, _) => ERR_OPENAL_CALL,
            AudioError::PoolExhausted => ERR_POOL_EXHAUSTED,
            AudioError::UnsupportedFormat => ERR_UNSUPPORTED_FORMAT,
            AudioError::WrongThread => ERR_WRONG_THREAD,
        }
    }

    pub(crate) fn from_alto_load(error: AltoError) -> Self {
        match error {
            AltoError::Io(error) => AudioError::OpenAlLoad(error.to_string()),
            other => AudioError::OpenAlLoad(other.to_string()),
        }
    }
}

impl fmt::Display for AudioError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AudioError::InvalidHandle => write!(f, "invalid native audio handle"),
            AudioError::InvalidArgument => write!(f, "invalid native audio argument"),
            AudioError::OpenAlLoad(message) => write!(f, "failed to load OpenAL: {message}"),
            AudioError::OpenAlCall(operation, message) => {
                write!(f, "OpenAL call failed during {operation}: {message}")
            }
            AudioError::PoolExhausted => write!(f, "native audio channel pool exhausted"),
            AudioError::UnsupportedFormat => write!(f, "unsupported audio format"),
            AudioError::WrongThread => write!(f, "native audio handle used from the wrong thread"),
        }
    }
}

pub(crate) type AudioResult<T> = Result<T, AudioError>;
