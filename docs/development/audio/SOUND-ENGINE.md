# Sound engine

The Rust policy in [`audio/policy/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/mod.rs)
replaces vanilla `SoundEngineExecutor`, `ChannelAccess`, `SoundBufferLibrary`
and the event/weight classes. [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/SoundEngine.java)
keeps its public API as a thin Java layer, so gameplay callers do not change.

## Ownership

| Rust owns | Java still owns |
| --- | --- |
| Parsed `sounds.json` registry, weights and nested-event resolution | Reading `sounds.json` text and listing sound files during reload |
| Asset cache (loading, ready, failed) per reload generation, and preload | Reading `.ogg` bytes from packs on the IO pool when Rust asks |
| Voices, category volumes, the 20-tick minimum source lifetime, delays, manual loops, pause and resume | `SoundInstance` objects: `resolve`, `canPlaySound`, per-tick `tick()` |
| The "Sound engine" thread, which owns the OpenAL device | `SoundEventListener`s (subtitles), music and ambient managers |
| Device-change and disconnect detection | Triggering the reload when Rust reports a device change |

Java identifies each `SoundInstance` to Rust by a numeric ID. An ID lives
while Rust still references the instance: it is active, queued or delayed.
Rust reports released IDs at the end of each tick, and Java forgets them.

## Threading

OpenAL calls must come from the thread that opened the device. Rust starts one
audio thread in `engine_init`. Every playback call from Java pushes ops onto
that thread's queue and returns without waiting, unlike vanilla's
`CompletableFuture.join()` on play. Only `init`, `stopAll` and `destroy`
block until the audio thread acknowledges them.

## Tick order

`SoundEngine.tick` follows vanilla's order:

1. `tickBegin`: reports whether the device changed. If it did, Java reloads.
2. When not paused: Java plays queued tickables, ticks the ticking sounds and
   sends their volume, pitch and position in one batch.
3. `tickEnd`: Rust ages voices and releases finished ones. It requeues manual
   loops and collects due delayed sounds.
4. Java forgets released instances and plays the due sounds.
5. `deviceTick`: the audio thread reaps stopped sources and polls the device.

## Constraints

- **RNG parity.** Resolution must consume the caller's `RandomSource` exactly as
  vanilla does. `resolve` is a step protocol: Rust asks for a draw bound, and
  Java calls `nextInt(bound)` and retries with the draws so far. Do not move
  drawing into Rust unless the random source moves with it.
- **Vanilla parsing quirks.** These come from Gson/`GsonHelper` and are
  covered in `audio/policy/tests.rs`. Booleans accept strings. Ints truncate.
  An entry `name` without a namespace means `minecraft:`. A nested event
  weighs its target's total weight. A missing file drops its entry with a
  warning.
- **`Mth.clamp` semantics.** Volume clamping keeps NaN the way Java does.
- **Asset generations.** Each reload clears the cache and bumps the generation.
  Bytes delivered for an older generation are discarded.
- **Replaced voices.** Playing an instance that is already playing starts a new
  voice. The old one keeps playing unowned until it ends, as in vanilla.

## Dev audio validation

[`DevAudioValidation.maybeRunIsolated`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/blaze3d/audio/DevAudioValidation.java)
runs the capture runner's OpenAL checks on a separate thread and device. It
then closes them, before the Rust engine opens its own device.

## Troubleshooting

- **`Error starting SoundSystem. Turning off sounds & music`.** `engine_init`
  failed. The message carries the Rust error. Check the selected device in
  Options or `ALSOFT_DRIVERS`.
- **`Missing sound for event` or `File ... does not exist`.** The first comes
  from `SoundEngine.reload`; the second is a Rust registry warning logged on
  apply. Both keep vanilla's wording. Check the pack's `sounds.json` and file
  paths.
- **`WrongMethodTypeException` from `NativeSoundPolicy`.** A downcall's
  `invokeExact` result cast does not match its declared layout. Even an
  ignored result must be cast to the declared return type.
- **No sound after unplugging a device.** Rust sets `reload_needed`, and the next
  `tickBegin` returns true. Check that `SoundEngine.tick` still reloads on it.
