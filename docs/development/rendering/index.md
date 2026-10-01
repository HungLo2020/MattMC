# Rendering

How the native (Rust) renderer is organized and how to change it safely. The
renderer lives in [`src/main/rust/render/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render);
each module there has a short README with its own file map.

- [Render Architecture](RENDER-ARCHITECTURE.md): the layers, what each may
  depend on, and where new code belongs.
- [VulkanicGAL](VULKANIC-GAL.md): working with the graphics abstraction layer:
  handles, validation, submission, hazards and common errors.
- [Java Bridge](JAVA-BRIDGE.md): the C ABI Java calls, and how to change it
  without breaking Java.
- [Render Verification](RENDER-VERIFICATION.md): tests, Frozen image
  comparisons, real-config sessions and A/B performance checks.
