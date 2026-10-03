# World physics

Collision geometry and block physics are independent of renderer backends.

- [Rust voxel Boolean joins](RUST-VOXEL-JOIN.md): complex-grid occupancy joining,
  exact bounds/coordinate compatibility and focused acceptance checks.
- [Rust merged voxel boxes](RUST-VOXEL-BOXES.md): ordered collision-box extraction,
  callback compatibility and complete caller verification.
- [Rust voxel rotation](RUST-VOXEL-ROTATION.md): packed rotations/reflections,
  original coordinates and complete public caller checks.
- [Rust closest collision point](RUST-VOXEL-CLOSEST-POINT.md): ordered nearest-point
  queries, exact compatibility and complete boundary-inclusive verification.
- [Rust ray/shape intersection](RUST-VOXEL-RAYCAST.md): ordered outside-ray
  intersections, exact face/point parity and complete caller verification.
