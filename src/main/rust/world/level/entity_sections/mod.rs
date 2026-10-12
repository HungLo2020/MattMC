//! Entity-section membership and spatial-index ownership.
//! Member arrays retain physical storage across callback-driven growth;
//! Java behavior bindings and class/equality callbacks remain transitional.
mod bindings;
mod ffi;
mod members;
mod section_map;
mod section_tree;
mod index_ffi;
mod residency_set;
mod residency;
mod residency_ffi;
