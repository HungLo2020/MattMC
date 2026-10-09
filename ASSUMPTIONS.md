# Migration assumptions
- Final runtime: one Rust library and one Rust executable with client/server modes; no JVM or Java fallback.
- Frozen Java OpenGL is the sole behavior, visual and performance reference; Frozen stays unchanged.
- Preserve existing worlds, content and integrated features; follow the recorded game-model decisions.
- Move complete subsystem ownership into Rust; bridges are temporary and new crates require approval.
- Publish tested milestones to master; retain compact evidence and prune generated copies routinely.
- Work on `master` in this MattMC checkout; do not use a separate migration checkout.
- Performance work moves more ownership into Rust; measured gaps guide continuing migration, while full acceptance still requires parity.
- Prioritize live world state and frame-input producers/consumers together, removing duplicate Java packing and Rust reconstruction ahead of further static catalogs.
