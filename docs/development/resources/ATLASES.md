# Texture atlases

Rust builds every texture atlas
([`assets/textures/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/textures/mod.rs)):
atlas definitions and sprite sources, PNG decoding, animation metadata,
Sodium's sprite preprocessing, stitching and mip levels. Java keeps the
objects its consumers use (`SpriteContents`, `TextureAtlasSprite`,
`TextureAtlas`, `SpriteLoader.Preparations`) and fills them from the build.

## Flow

[`SpriteLoader.loadAndStitch`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/renderer/texture/SpriteLoader.java)
drives one build through
[`NativeAtlasBuild`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/renderer/texture/NativeAtlasBuild.java):

1. **Load** (`mattmc_atlas_load`): read `atlases/<id>.json` from every pack,
   run its sources in order, then decode and prepare each sprite on worker
   threads ([`sources.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/textures/sources.rs),
   [`sprite.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/textures/sprite.rs)).
2. **Metadata**: Java decodes the sidecar sections it asked for (GUI scaling,
   villager hats) with its own codecs and may drop sprites.
3. **Stitch** (`mattmc_atlas_stitch`): mip clamping and placement
   ([`stitcher.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/textures/stitcher.rs));
   a failure returns the record `StitcherException` needs.
4. **Mips and copy**: generate mip chains, then copy pixels into Java's
   `NativeImage`s and close the build.

The `filter` source's regexes run in Java through an upcall
(`mattmc_textures_register_pattern`), so pattern syntax is Java's.
`SpriteContents`' compatibility constructor runs Rust's preparation
(`mattmc_sprite_prepare`) for sprites built outside an atlas.

## Constraints

- **Java's results, bit for bit.** Sprite order, placements, UVs, pixels and
  mip levels must match what the Java pipeline produced. The supplier map
  follows Java `HashMap` iteration order
  ([`core/java_hash.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_hash.rs));
  stitching sorts by height, width, then name (path before namespace).
- **Sodium's arithmetic.** Transparent-pixel fill, the transparency scan
  (alpha ≤ 25 transparent, < 255 translucent) and the weighted mip average are
  Sodium's f32 operations; keep their order.
- **Strict JSON.** Atlas definitions use
  [`core/json.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/json.rs)
  `parse_document` (Gson strict: trailing text fails); `.mcmeta` animation
  sections keep Gson's number and key semantics.
- **Logs.** Warnings and errors keep Java's wording. Lines logged on worker
  threads are deferred and emitted by the calling thread after the workers
  join ([`core/parallel.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/parallel.rs)).
  Where Java logged an exception, the header line matches and the cause text
  follows on the next line.

## Working on it

```sh
(cd src/main/rust && cargo test --lib -- assets::textures core::json core::parallel)
python3 DevUtils/tests/textures/VerifyAtlases.py --output build/atlas-verification
```

The driver needs a Reference checkout (see [verification](VERIFICATION.md)).
On a record mismatch, `mismatch.diff` names the atlas and sprite; add a
fixture to `build_fixtures` for any new case before fixing it.

**Troubleshooting:** a missing `mattmc_atlas_*` symbol means a stale native
library (`./gradlew buildRustNative`). Different warnings with identical
records usually mean wording or ordering; the driver compares sorted
WARN/ERROR first lines.
