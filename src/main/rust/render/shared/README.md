# shared

Helpers used by both the world and GUI renderers, above the GAL: texture
sampling metadata, sprite (atlas) interpolation, view layering, and the
standard/special item foil projections. `launch_configuration.rs` owns immutable
process render/audit flags and uses `core::environment`; Java receives one scalar
projection. GPU helpers depend on the public GAL; this module has no backend dependency.
