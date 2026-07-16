use alto::{Alto, AltoError};

use super::errors::{AudioError, AudioResult};

/// Owns access to the statically linked OpenAL Soft entry points.
///
/// `alto::Alto` is internally reference-counted around immutable function
/// pointers. The backend serializes all access through a global mutex and
/// `alto` makes contexts current around each operation, so moving the loader
/// wrapper between threads does not introduce concurrent OpenAL access.
pub(crate) struct NativeAlto(pub(crate) Alto);

unsafe impl Send for NativeAlto {}

pub(crate) fn load_openal() -> AudioResult<NativeAlto> {
    let alto = Alto::load_default().map_err(AudioError::from_alto_load)?;
    Ok(NativeAlto(alto))
}

pub(crate) fn alto_call<T>(
    operation: &'static str,
    result: Result<T, AltoError>,
) -> AudioResult<T> {
    result.map_err(|error| AudioError::OpenAlCall(operation, error.to_string()))
}

pub(crate) fn cstring_to_string(value: std::ffi::CString) -> String {
    value.to_string_lossy().into_owned()
}
