# DH quad owner

Build-local CPU policy for ordinary DH geometry. `mod.rs` owns typed lists,
classification, stable sorting and greedy merging. `packets.rs` owns immutable
layer plans, ordered callback events, shading and final byte encoding;
`ffi.rs` exposes scoped CPU leases and caller-owned copies. GPU ownership stays
in VulkanicGAL.

See the [developer guide](../../../../../docs/development/rendering/RUST-DH-QUADS.md)
for lifetimes, compatibility and Frozen replay commands.
See [packet ownership](../../../../../docs/development/rendering/RUST-DH-PACKETS.md)
for callback and transport contracts.
