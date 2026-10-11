# Audio development

Client audio is moving into Rust ([issue #773](https://github.com/HungLo2020/MattMC/issues/773)).
At [`ed8dc571`](https://github.com/HungLo2020/MattMC/commit/ed8dc5714631e0e55d31dbd448789f21a47bedf6), Rust owns `sounds.json` resolution, the sound asset cache and the playback
engine, including the thread that owns the OpenAL device. Java keeps
`SoundInstance` objects, pack IO, music, ambient sounds, subtitles and the
gameplay calls that start sounds.

- [Sound engine](SOUND-ENGINE.md): ownership split, threading, the FFI
  protocol, constraints and troubleshooting.

## Where the code lives

| Area | Rust | Java view |
| --- | --- | --- |
| Event registry and resolution | [`audio/policy/events.rs`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/events.rs) | [`SoundManager`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/minecraft/client/sounds/SoundManager.java), [`WeighedSoundEvents`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/minecraft/client/sounds/WeighedSoundEvents.java) |
| Asset cache and preload | [`audio/policy/assets.rs`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/assets.rs) | pack reads in [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/minecraft/client/sounds/SoundEngine.java) |
| Playback policy | [`audio/policy/engine.rs`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/engine.rs) | [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/minecraft/client/sounds/SoundEngine.java) |
| Device thread | [`audio/policy/thread.rs`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/thread.rs) | none |
| FFI | [`audio/policy/ffi.rs`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/ffi.rs) | [`NativeSoundPolicy`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/minecraft/client/sounds/NativeSoundPolicy.java) |
| OpenAL device, sources, decoding | [`audio/`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/mod.rs) backend | `NativeAudio` / `Library` compatibility and validation APIs |

Built-in sound event identities and block sound profiles are a separate,
immutable registry; see [sound definitions](../game-model/SOUND-DEFINITIONS.md).

## Tests

```sh
# Rust policy unit tests (registry, asset cache, engine rules)
(cd src/main/rust && ALSOFT_DRIVERS=null cargo test --locked --profile suite --lib audio::policy)

# Focused Java policy FFI checks against the matching built release library
# Run the separate native checks above first; this skips the full native task.
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.client.sounds.NativeSoundPolicyTest'
```

See [testing setup](../tooling/TESTING.md) for the required JDK/native build.
The broader `net.blaze3d.audio.*` Java tests exercise backend behavior beyond
these three policy controls; ordinary `test` also depends on `testRustNative`
unless explicitly excluded. The commands above are verification instructions, not checks performed by this
documentation review. The bundled JSON test assumes files exist and samples
one draw sequence per event; see the [test boundary](SOUND-ENGINE.md#verification-scope).
`ALSOFT_DRIVERS=null` supports headless backend work and does not prove audible
output. A real-device client check should include sound playback, music,
subtitles, category controls, pause/resume and device/reload behavior, with the
F3 `Sounds:` line and logs as diagnostics rather than complete acceptance.
Java music/ambient policy, pack IO and instance callbacks remain migration work.
