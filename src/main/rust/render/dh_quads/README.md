# DH quad owner

Build-local CPU policy for ordinary DH geometry. `mod.rs` owns typed lists,
classification, stable sorting and greedy merging; `ffi.rs` exposes exclusive
CPU leases and immutable row copies. GPU ownership stays in VulkanicGAL.

See the [developer guide](../../../../../docs/development/rendering/RUST-DH-QUADS.md)
for lifetimes, compatibility and Frozen replay commands.
