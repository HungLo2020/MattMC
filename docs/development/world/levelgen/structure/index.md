# Structure generation

Supported structure placement checks and start planners run in Rust, including
jigsaw and the [other documented start families](RUST-STRUCTURE-PIECES.md#other-start-positions).
Java retains piece objects and unsupported planner fallbacks. Supported pieces
and template blocks place natively inside the decoration step loop.

- [Native structure pieces](RUST-STRUCTURE-PIECES.md): start planners, piece families, template
  tails, Java steps, placement checks and verification.
- [Native structure templates](RUST-STRUCTURE-TEMPLATES.md): processors, the
  write loop, block-entity replay, gates and structure-region verification.
