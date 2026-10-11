# Client sounds

Sounds that need no Java object are created, resolved and ticked in Rust
([`client.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/client.rs)).
Java reports the situation and draws the results. The bindings are
[`NativeSoundClient`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/NativeSoundClient.java).

| System | Rust | Java reports |
| --- | --- | --- |
| World sound events (`ClientLevel.playSound`) | Resolution with `LegacyRandomSource(seed)`, scheduling and playback | Event, category, volume, pitch, seed, position; camera-distance calculation and supplied delay ticks |
| Music ([`music.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/music.rs)) | Current track, song delay, fades, replacement, music random, toast state | `getSituationalMusic()` each tick, the frequency option; shows or hides the toast when asked |
| Ambience ([`ambient.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/ambient.rs)) | Underwater additions and loop, bubble column sounds, biome loops and cross-fades, additions, mood and Iris's constant mood | [`LocalPlayerAmbience`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/LocalPlayerAmbience.java): position, underwater, spectator, bubble column, biome settings on change, light levels |
| Subtitles ([`subtitles.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/subtitles.rs)) | Entries, refresh by text, hearing range, expiry, closest position | `SubtitleOverlay` toggles listening and draws each frame's list |
| Volume preview ([`preview.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/preview.rs)) | The per-category preview sound and its restart rule | The options slider's category and volume |

Gameplay code that keeps a `SoundInstance` (jukeboxes, UI clicks, entity
sounds, mod instances) still plays it through `SoundManager.play`. New
fire-and-forget world sounds should use `SoundManager.playEvent`.

## Shared level random

The ambient handlers and the bubble column's local sound draw from the
client level's random, which other client code also uses. `LocalPlayerAmbience`
passes raw 48-bit state to Rust and writes the advanced state back only for
`LegacyRandomSource` and `ThreadSafeLegacyRandomSource`. For another
`RandomSource`, it consumes one `nextLong()` to seed a fresh native sequence
and does not write that sequence back. That compatibility route does not
preserve arbitrary generators' draw-by-draw behavior; see the
[`exportRandom` / `importRandom` boundary](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/LocalPlayerAmbience.java#L144-L162). The mood needs world light
at a block drawn mid-tick, so one tick is two calls: `ambient_tick` returns
the block to sample, and `ambient_finish` takes its sky and block light.

## Lifetimes

- Each `LocalPlayer` gets an owner ID. When a new player first ticks or plays,
  Rust resets the ambient handlers, as vanilla builds new ones. The previous
  player's underwater sounds see it removed and stop, and its biome loops keep
  their fade, as vanilla's orphaned instances do. A player that never ticks (a
  map mod's camera) takes nothing over.
- Controllers hold their instances (music, biome loops) through `stopAll` and a
  device reopen. Their policy playback becomes inactive, but release follows
  controller-specific rules rather than a guaranteed next-tick deadline:
  biome loops are removed when their native rule reports stopped, and music
  can return early when there is no situational track. These retained
  controller references are separate from device voices.
- Rust-owned delayed sounds resolve when due, with the random they were
  queued with.

## Tests

The definitions inspected at this source provide three different kinds of
coverage; this documentation review did not execute them:

- [`client_tests.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/client_tests.rs)
  contains 19 Rust policy fixtures for rules, native event/delay ownership,
  controller retention, music, ambience, subtitles and preview. Its hardcoded
  event check establishes registry membership, not that audio resources decode
  or play correctly. Random-draw replay uses another instance of the native
  legacy implementation plus short Java fixtures; it does not establish
  equivalence to Java's process-wide fresh-seed stream.
- [`NativeAmbienceParityTest`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/test/java/net/minecraft/client/sounds/NativeAmbienceParityTest.java)
  compares 4,000 deterministic scenario ticks against embedded Java reference
  bodies with the sound engine closed. It checks the shared legacy RNG state,
  sampled light position and both mood values. It does not run a Frozen client,
  world traversal, audible ambience or device recovery.
- [`NativeSoundEngineTest`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/test/java/net/minecraft/client/sounds/NativeSoundEngineTest.java)
  has four Java/native fixtures for event/subtitle/delay/warning records,
  ruled-instance outcomes, music/toast state and category preview. It opens
  OpenAL's null device against a synthetic registry but does not deliver the
  named Ogg asset bytes or construct the Java `SoundEngine` wrapper. A `STARTED` result and policy records therefore do not
  establish decoded or audible playback.

For commands and the incoming Java test-task null-driver setting, use the
[audio test entry point](index.md#tests). Real-device reconnect, reload with
pending pack IO, stream recovery and long-session resource behavior remain
[separate acceptance work](SOUND-ENGINE.md#verification-scope).
