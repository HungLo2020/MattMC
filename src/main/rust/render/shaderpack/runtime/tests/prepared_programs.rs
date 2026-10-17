use super::*;
use std::cell::RefCell;
use std::sync::Arc;

#[test]
fn prepared_program_epoch_replacement_preserves_in_use_snapshot_and_failed_retry() {
    // Source snapshots contain immutable CPU data only. An old frame may
    // retain its snapshot across candidate replacement without seeing new text.
    let memo = RefCell::new(None);
    let old = memoized_source_program(&memo, 10, || Ok(Some("old source".to_owned())))
        .unwrap()
        .unwrap();
    let repeated = memoized_source_program(&memo, 10, || {
        panic!("the same candidate epoch must reuse its prepared snapshot")
    })
    .unwrap()
    .unwrap();
    assert!(Arc::ptr_eq(&old, &repeated));

    let failed = memoized_source_program(&memo, 11, || {
        Err(GalError::unsupported_feature("incomplete replacement"))
    });
    assert!(failed.is_err());
    let replacement = memoized_source_program(&memo, 11, || Ok(Some("new source".to_owned())))
        .unwrap()
        .unwrap();
    assert_eq!(&**old, "old source");
    assert_eq!(&**replacement, "new source");
    assert!(!Arc::ptr_eq(&old, &replacement));

    // Unavailable candidates must never expose the retained earlier epoch.
    assert!(memoized_source_program(&memo, 12, || Ok(None)).unwrap().is_none());
    assert_eq!(&**replacement, "new source");
}
