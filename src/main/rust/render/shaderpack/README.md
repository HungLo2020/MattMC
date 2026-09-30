# shaderpack

Iris/OptiFine shader-pack compatibility: pack parsing, preprocessing, program
contracts, uniforms, the pass graph and the pack runtime that the world
renderer executes.

Will receive: `vulkanic/shader_pack/`.

Rules: builds on the shared renderer and the GAL's public API; never on
backend internals.
