//! Native allocations paired with owner-local opaque VM anchors.
//!
//! This module never stores a JVM address or a VM object. A table lease owns the
//! actual allocation; the corresponding Java resource owns its opaque anchors.
//! Publication compares allocation identity after fallible VM preparation.
use std::sync::{Arc, Mutex};

#[derive(Debug)]
pub struct Root<T>(Mutex<Arc<T>>);

impl<T> Root<T> {
    pub fn new(value: T) -> Self { Self(Mutex::new(Arc::new(value))) }
    pub fn lease(&self) -> Arc<T> { self.0.lock().unwrap().clone() }

    /// The caller prepares all VM resources before calling this operation.
    /// Failure leaves the current allocation, including reentrant work, intact.
    pub fn publish(&self, expected: &Arc<T>, prepared: Arc<T>) -> Result<(), Arc<T>> {
        let mut current = self.0.lock().unwrap();
        if !Arc::ptr_eq(&current, expected) { return Err(prepared); }
        *current = prepared;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn publication_does_not_replace_reentrant_work_and_leases_own_allocations() {
        let owner = Root::new(vec![1]);
        let escaped = owner.lease();
        let retired = Arc::downgrade(&escaped);
        owner.publish(&escaped, Arc::new(vec![2])).unwrap();
        assert!(owner.publish(&escaped, Arc::new(vec![3])).is_err());
        assert_eq!(*owner.lease(), [2]);
        drop(owner);
        assert_eq!(*escaped, [1]);
        drop(escaped);
        assert!(retired.upgrade().is_none());
    }
}
