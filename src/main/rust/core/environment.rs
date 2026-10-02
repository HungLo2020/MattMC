//! Process configuration, snapshotted at first access. Set options before launch.
use std::{
    collections::HashMap,
    ffi::{OsStr, OsString},
    sync::OnceLock,
};

fn snapshot() -> &'static HashMap<OsString, OsString> {
    static ENVIRONMENT: OnceLock<HashMap<OsString, OsString>> = OnceLock::new();
    ENVIRONMENT.get_or_init(|| std::env::vars_os().collect())
}

pub fn var_os(key: impl AsRef<OsStr>) -> Option<OsString> {
    #[cfg(test)]
    if let Some(value) = TEST_OVERRIDES.with(|values| values.borrow().get(key.as_ref()).cloned()) {
        return value;
    }
    snapshot().get(key.as_ref()).cloned()
}

pub fn var(key: impl AsRef<OsStr>) -> Result<String, std::env::VarError> {
    var_os(key)
        .ok_or(std::env::VarError::NotPresent)?
        .into_string()
        .map_err(std::env::VarError::NotUnicode)
}

// Test fixtures switch diagnostic modes in one process; production stays immutable.
#[cfg(test)]
thread_local! { static TEST_OVERRIDES: std::cell::RefCell<HashMap<OsString, Option<OsString>>> = std::cell::RefCell::new(HashMap::new()); }
#[cfg(test)]
pub fn set_var(key: impl AsRef<OsStr>, value: impl AsRef<OsStr>) {
    std::env::set_var(key.as_ref(), value.as_ref());
    TEST_OVERRIDES.with(|values| {
        values
            .borrow_mut()
            .insert(key.as_ref().to_owned(), Some(value.as_ref().to_owned()));
    });
}
#[cfg(test)]
pub fn remove_var(key: impl AsRef<OsStr>) {
    std::env::remove_var(key.as_ref());
    TEST_OVERRIDES.with(|values| {
        values.borrow_mut().insert(key.as_ref().to_owned(), None);
    });
}

/// Override one option on this test thread without changing process state.
#[cfg(test)]
pub fn scoped_override(key: &str, value: &str) -> impl Drop {
    struct Restore(OsString, Option<Option<OsString>>);
    impl Drop for Restore {
        fn drop(&mut self) {
            TEST_OVERRIDES.with(|values| {
                let mut values = values.borrow_mut();
                if let Some(previous) = self.1.take() {
                    values.insert(self.0.clone(), previous);
                } else {
                    values.remove(&self.0);
                }
            });
        }
    }
    let previous =
        TEST_OVERRIDES.with(|values| values.borrow_mut().insert(key.into(), Some(value.into())));
    Restore(key.into(), previous)
}
