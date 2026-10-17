use super::*;
use crate::content::block::{definitions, StateId};

#[test]
fn all_state_policies_match_untouched_frozen_observations() {
    let expected = include_bytes!("frozen-state-policy.bin");
    let registry = definitions::registry();
    assert_eq!(expected.len(), 31_809);
    assert_eq!(registry.policies.len(), expected.len());
    for (id, &value) in expected.iter().enumerate() {
        assert_eq!(registry.state_policy(StateId(id as u16)).unwrap().bits(), value, "state {id}");
    }
    assert!(registry.state_policy(StateId(31_809)).is_none());
    assert!(registry.state_policy(StateId(u16::MAX)).is_none());
}

#[test]
fn policy_buffer_is_a_bounded_immutable_process_lifetime_view() {
    let expected = include_bytes!("frozen-state-policy.bin");
    unsafe {
        let mut length = -1;
        let first = definitions::ffi::mattmc_block_definitions_buffer(15, &mut length);
        assert!(!first.is_null());
        assert_eq!(length as usize, expected.len());
        assert_eq!(std::slice::from_raw_parts(first.cast::<u8>(), length as usize), expected);
        assert_eq!(definitions::ffi::mattmc_block_definitions_buffer(15, &mut length), first);
        assert!(definitions::ffi::mattmc_block_definitions_buffer(16, &mut length).is_null());
        assert_eq!(length, 0);
        assert!(definitions::ffi::mattmc_block_definitions_buffer(15, std::ptr::null_mut()).is_null());
    }
}
