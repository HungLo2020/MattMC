# shared

Rendering building blocks used by both the world and GUI renderers, above the
GAL and free of game content: materials, geometry pools and uploads, texture
atlases and sprite animation, text drawing, fullscreen passes, frame/pass
scheduling and GPU profiling scopes.

Will receive: generic pieces extracted from the world and GUI frontends
(for example `texture_sampling.rs`, `sprite_interpolation.rs`,
`buffer_upload_capture.rs`).
