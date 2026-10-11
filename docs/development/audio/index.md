# Audio development

Client audio is moving into Rust ([issue #773](https://github.com/HungLo2020/MattMC/issues/773)).
Rust owns `sounds.json` resolution, the asset cache, the playback engine and
the thread that owns the OpenAL device, device recovery, the vanilla
tickables' per-tick rules, music, the local player's ambience, subtitles and
the volume preview. Java keeps pack IO, the `SoundInstance` objects gameplay
code creates, the entity facts their rules sample, the situation music follows,
and drawing the toast and subtitle overlay.

- [Sound engine](SOUND-ENGINE.md): ownership split, threading, tick order,
  constraints, troubleshooting and verification scope.
- [Client sounds](CLIENT-SOUNDS.md): native world-event and controller policy,
  Java context/UI inputs, shared-RNG limits and focused fixture coverage.

## Where the code lives

| Area | Rust | Java view |
| --- | --- | --- |
| Event registry and resolution | [`audio/policy/events.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/events.rs) | [`SoundManager`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/SoundManager.java), [`WeighedSoundEvents`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/minecraft/client/sounds/WeighedSoundEvents.java) |
| Asset cache and preload | [`audio/policy/assets.rs`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/assets.rs) | pack reads in [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/SoundEngine.java) |
| Playback policy | [`audio/policy/engine.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/engine.rs) | [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/SoundEngine.java) |
| Device thread and reopening | [`audio/policy/thread.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/thread.rs) | none |
| Tickable rules | [`audio/policy/rules.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/rules.rs) | [`RuledSoundInstance`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/resources/sounds/RuledSoundInstance.java) samplers |
| Music, ambience, subtitles, preview | [`audio/policy/client.rs`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/client.rs) and siblings | `MusicManager`, `LocalPlayerAmbience`, `SubtitleOverlay` |
| FFI | [`audio/policy/ffi/`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/rust/audio/policy/ffi/mod.rs) | [`NativeSoundPolicy`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/NativeSoundPolicy.java), [`NativeSoundClient`](https://github.com/HungLo2020/MattMC/blob/e6bc0e29835e431327cc8d7007ffce5fe53ce1b4/src/main/java/net/minecraft/client/sounds/NativeSoundClient.java) |
| OpenAL device, sources, decoding | [`audio/`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/mod.rs) backend | `NativeAudio` / `Library` compatibility and validation APIs |

Built-in sound event identities and block sound profiles are a separate,
immutable registry; see [sound definitions](../game-model/SOUND-DEFINITIONS.md).

## Tests

```sh
# Rust policy unit tests (registry, asset cache, engine, rules, client sounds)
(cd src/main/rust && ALSOFT_DRIVERS=null cargo test --locked --profile suite --lib audio::policy)

# Java policy FFI, ambience parity and null-device engine tests against the
# matching built release library. Run the native checks above first; this
# skips the full native task.
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.client.sounds.*'
```

See [testing setup](../tooling/TESTING.md) for the required JDK/native build.
The broader `net.blaze3d.audio.*` Java tests exercise backend behavior beyond
these policy controls; ordinary `test` also depends on `testRustNative`
unless explicitly excluded. The Java test task sets `ALSOFT_DRIVERS=null`, so
engine tests open OpenAL's silent null device. The four synthetic-registry
cases inspect state/record behavior without supplying Ogg bytes; the
4,000-tick ambience fixture keeps the engine closed. See the
[client test breakdown](CLIENT-SOUNDS.md#tests). These definitions were
inspected, not executed by this documentation review, and do not establish
audible output, physical-device recovery or long-session behavior. The bundled JSON test assumes files exist and
samples one draw sequence per event; see the
[test boundary](SOUND-ENGINE.md#verification-scope).

A real-device client check should include sound playback, music and its toast,
subtitles, category controls, the volume preview, pause/resume, ambience
(underwater, biome loops) and device change/reload behavior, with the F3
`Sounds:` line and logs as diagnostics rather than complete acceptance.
