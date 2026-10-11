# Client sounds

Sounds that need no Java object are created, resolved and ticked in Rust
([`client.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/client.rs)).
Java reports the situation and draws the results. The bindings are
[`NativeSoundClient`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/NativeSoundClient.java).

| System | Rust | Java reports |
| --- | --- | --- |
| World sound events (`ClientLevel.playSound`) | Resolution with `LegacyRandomSource(seed)`, distance delay, playback | Event, category, volume, pitch, seed, position, delay ticks |
| Music ([`music.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/music.rs)) | Current track, song delay, fades, replacement, music random, toast state | `getSituationalMusic()` each tick, the frequency option; shows or hides the toast when asked |
| Ambience ([`ambient.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/ambient.rs)) | Underwater additions and loop, bubble column sounds, biome loops and cross-fades, additions, mood and Iris's constant mood | [`LocalPlayerAmbience`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/LocalPlayerAmbience.java): position, underwater, spectator, bubble column, biome settings on change, light levels |
| Subtitles ([`subtitles.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/subtitles.rs)) | Entries, refresh by text, hearing range, expiry, closest position | `SubtitleOverlay` toggles listening and draws each frame's list |
| Volume preview ([`preview.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/preview.rs)) | The per-category preview sound and its restart rule | The options slider's category and volume |

Gameplay code that keeps a `SoundInstance` (jukeboxes, UI clicks, entity
sounds, mod instances) still plays it through `SoundManager.play`. New
fire-and-forget world sounds should use `SoundManager.playEvent`.

## Shared level random

The ambient handlers and the bubble column's local sound draw from the
client level's random, which other client code also uses. `LocalPlayerAmbience`
passes the source's raw 48-bit state to Rust and writes the advanced state
back (`LegacyRandomSource.rawSeed` / `setRawSeed`). The mood needs world light
at a block drawn mid-tick, so one tick is two calls: `ambient_tick` returns
the block to sample, and `ambient_finish` takes its sky and block light.

## Lifetimes

- Each `LocalPlayer` gets an owner ID. When a new player first ticks or plays,
  Rust resets the ambient handlers, as vanilla builds new ones. The previous
  player's underwater sounds see it removed and stop, and its biome loops keep
  their fade, as vanilla's orphaned instances do. A player that never ticks (a
  map mod's camera) takes nothing over.
- Controllers hold their instances (music, biome loops) through `stopAll` and a
  device reopen. Those instances then report inactive, and the controller
  drops them on its next tick, as Java's objects did.
- Rust-owned delayed sounds resolve when due, with the random they were
  queued with.

## Tests

- `audio/policy/client_tests.rs`: each rule, subtitles, native plays, music,
  ambience and the preview, with draws replayed on an independent source. It
  also checks Java `LegacyRandomSource` fixtures and that every hardcoded event
  exists in the bundled `sounds.json`.
- `NativeAmbienceParityTest`: 4,000 random ticks of the Rust handlers against
  the vanilla Java handler bodies on the same level random.
- `NativeSoundEngineTest`: the bindings end to end on OpenAL's null device
  (the Java test task sets `ALSOFT_DRIVERS=null`).
