# Item-layer preparation

`mod.rs` owns immutable right/left authored CPU poses and preserves the original
JOML/PoseStack evaluation. `ffi.rs` transfers nine authored scalars and creates
one independently released owner. `tests.rs` covers centering, mirroring,
normal-scale conventions and rejected nonfinite results.

The GUI bridge reads a pinned owner and copies its matrix into the owned request;
this module has no GAL/backend dependency. Java compatibility consumers may read
scoped CPU projections. See the [development guide](https://github.com/HungLo2020/MattMC/blob/master/docs/development/rendering/RUST-ITEM-LAYERS.md)
for lifetime, admission, test commands and verification limits.
