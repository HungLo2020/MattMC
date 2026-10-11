# Audio development

Client audio is moving into Rust ([issue #773](https://github.com/HungLo2020/MattMC/issues/773)).
Today Rust owns `sounds.json` resolution, the sound asset cache and the playback
engine, including the thread that owns the OpenAL device. Java keeps
`SoundInstance` objects, pack IO, music, ambient sounds, subtitles and the
gameplay calls that start sounds.

- [Sound engine](SOUND-ENGINE.md): ownership split, threading, the FFI
  protocol, constraints and troubleshooting.

## Where the code lives

| Area | Rust | Java view |
| --- | --- | --- |
| Event registry and resolution | [`audio/policy/events.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/events.rs) | [`SoundManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/SoundManager.java), [`WeighedSoundEvents`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/WeighedSoundEvents.java) |
| Asset cache and preload | [`audio/policy/assets.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/assets.rs) | pack reads in [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/SoundEngine.java) |
| Playback policy | [`audio/policy/engine.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/engine.rs) | [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/SoundEngine.java) |
| Device thread | [`audio/policy/thread.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/thread.rs) | none |
| FFI | [`audio/policy/ffi.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/ffi.rs) | [`NativeSoundPolicy`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/NativeSoundPolicy.java) |
| OpenAL device, sources, decoding | [`audio/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/mod.rs) backend | none |

Built-in sound event identities and block sound profiles are a separate,
immutable registry; see [sound definitions](../game-model/SOUND-DEFINITIONS.md).

## Tests

```sh
# Rust policy unit tests (registry, asset cache, engine rules)
(cd src/main/rust && ALSOFT_DRIVERS=null cargo test --locked --profile suite --lib audio::policy)

# Java FFI tests against the built native library
./gradlew test --tests 'net.minecraft.client.sounds.*' --tests 'net.blaze3d.audio.*'
```

The Rust tests include a resolution pass over the bundled vanilla
`sounds.json`. Use a dev client run to check behavior end to end: sounds play,
music starts, the F3 `Sounds:` line moves, and `console.log` has no
`SoundEngine` or `Rust sound` errors.
