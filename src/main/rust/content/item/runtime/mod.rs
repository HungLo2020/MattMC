//! Retained item runtime state. The structural store uses stable identity tokens;
//! arbitrary Java payloads remain explicit leaves in their facade's reachability graph.
//! Callback phases must release every native borrow before entering Java.
pub mod patch;
pub mod ffi;

pub mod stack;
pub mod slots;

pub mod inventory;

pub mod container;

pub mod menu;

pub mod components;
pub mod jvm_roots;
