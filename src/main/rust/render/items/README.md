# Item-layer preparation

`mod.rs` owns immutable right/left authored CPU poses and preserves the original
JOML/PoseStack evaluation. `ffi.rs` transfers nine authored scalars and creates
one independently released owner. `world_pose.rs` applies authored world
transforms and composes prepared hand poses, preserving independent normals
and JOML evaluation order. `tests.rs` covers centering, mirroring,
normal-scale conventions and rejected nonfinite results.

GUI, world and hand decoders read pinned CPU owners and copy resolved poses
into owned requests;
this module has no GAL/backend dependency. Java compatibility consumers may read
scoped CPU projections. See the [development guide](https://github.com/HungLo2020/MattMC/blob/master/docs/development/rendering/RUST-ITEM-LAYERS.md)
for lifetime, admission, test commands and verification limits.
