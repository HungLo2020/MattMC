# Migration assumptions
- Final runtime: one Rust library and one Rust executable with client/server modes; no JVM or Java fallback.
- Frozen Java OpenGL is the sole behavior, visual and performance reference; Frozen stays unchanged.
- Preserve existing worlds, content and integrated features; follow the recorded game-model decisions.
- Move complete subsystem ownership into Rust; bridges are temporary and new crates require approval.
- Publish tested milestones to master; retain compact evidence and prune generated copies routinely.
