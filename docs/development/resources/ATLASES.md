# Texture atlases

Rust builds the resource-reload texture atlases
([`assets/textures/`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/textures/mod.rs)):
atlas definitions and sprite sources, PNG decoding, animation metadata,
Sodium's sprite preprocessing, stitching and mip levels. Java keeps the
objects its consumers use (`SpriteContents`, `TextureAtlasSprite`,
`TextureAtlas`, `SpriteLoader.Preparations`) and fills them from the build.

Source-reviewed at
[`71762d60`](https://github.com/HungLo2020/MattMC/commit/71762d602195e0cc5ca29006a2bed1603b53eab8).
The [atlas verification record](VERIFICATION.md#atlases) is reported evidence;
it does not establish a fresh GPU or live-reload acceptance of this source.

## Flow

[`SpriteLoader.loadAndStitch`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/renderer/texture/SpriteLoader.java)
drives one build through
[`NativeAtlasBuild`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/renderer/texture/NativeAtlasBuild.java):

1. **Load** (`mattmc_atlas_load`): read `atlases/<id>.json` from every pack,
   run its sources in order, then decode and prepare each sprite on worker
   threads ([`sources.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/textures/sources.rs),
   [`sprite.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/textures/sprite.rs)).
2. **Metadata**: Java decodes the sidecar sections it asked for (GUI scaling,
   villager hats) with its own codecs and may drop sprites.
3. **Stitch** (`mattmc_atlas_stitch`): mip clamping and placement
   ([`stitcher.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/textures/stitcher.rs));
   a failure returns the record `StitcherException` needs.
4. **Mips and copy**: copy level zero into Java's `NativeImage`s before
   returning `Preparations`; when mips are requested, generate and copy them
   in the supplied executor, then close the build. With no mips, close it
   immediately. `Preparations.readyForUpload` marks completion of that work.

The `filter` source's regexes run in Java through an upcall
(`mattmc_textures_register_pattern`), so pattern syntax is Java's.
`SpriteContents`' compatibility constructor runs Rust's preparation
(`mattmc_sprite_prepare`) for sprites built outside an atlas.

## Admission, threads and ownership

- Use the [native resource-manager admission path](RESOURCE-MANAGER.md#native-atlas-and-model-admission).
  The supported source types are `minecraft:single`, `directory`, `filter`,
  `unstitch` and `paletted_permutations`; the
  [Rust decoder](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/textures/sources.rs#L102)
  rejects other type keys. A custom Java sprite-source implementation is not
  admitted through the removed `SpriteSources` registry.
- Source evaluation and both pack-filter and atlas-regex upcalls run on the
  calling Java preparation thread before native image workers start. Atlas
  regexes use `Pattern.matcher(...).find()` and a process-wide callback/cache;
  missing pattern fields match everything for that dimension. Extra metadata
  codecs run in Java before stitching. Preserve this callback boundary when
  moving work to threads.
- Each [native build](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/textures/ffi.rs#L13)
  owns its sprites and mip buffers behind a mutex. Java copies the results,
  frees returned record buffers, and releases the build once through `close`
  or its Cleaner. Keep image destinations alive through each copy and do not
  race close with native calls; the mutex is not a lifetime or reload barrier.
- [AtlasManager](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/resources/model/AtlasManager.java#L114)
  waits for all `readyForUpload` futures and the reload preparation barrier
  before applying on the apply executor. Java's retained
  [`TextureAtlas`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/renderer/texture/TextureAtlas.java#L79)
  publishes CPU sprite state and semantic resources; its `upload` method does
  not allocate a Java GPU atlas. Clearing it closes the old `SpriteContents`
  images. Retaining a sprite wrapper across reload does not keep those images
  open, and the temporary atlas-build handle is not a renderer generation.

## Constraints

- **Java's results, bit for bit.** Sprite order, placements, UVs, pixels and
  mip levels must match the supported Java reference behavior. The supplier map
  follows Java `HashMap` iteration order
  ([`core/java_hash.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/core/java_hash.rs));
  stitching sorts by height, width, then name (path before namespace).
- **Sodium's arithmetic.** Transparent-pixel fill, the transparency scan
  (alpha ≤ 25 transparent, < 255 translucent) and the weighted mip average are
  Sodium's f32 operations; keep their order.
- **Strict JSON.** Atlas definitions use
  [`core/json.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/core/json.rs)
  `parse_document` (Gson strict: trailing text fails); `.mcmeta` animation
  sections keep Gson's number and key semantics.
- **Logs.** Warnings and errors keep Java's wording. Lines logged on worker
  threads are deferred and emitted by the calling thread after the workers
  join ([`core/parallel.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/core/parallel.rs)).
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
