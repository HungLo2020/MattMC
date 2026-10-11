# Sound engine

The Rust policy in [`audio/policy/`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/mod.rs)
replaces vanilla `SoundEngineExecutor`, `ChannelAccess`, `SoundBufferLibrary`
plus `SoundEventRegistration`, its serializer and the `Weighted` interface.
`Sound` and `WeighedSoundEvents` remain Java views. [`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/minecraft/client/sounds/SoundEngine.java)
retains the gameplay-facing Java API and instance callbacks. This is a bounded
policy/asset migration; music selection, ambient managers, subtitles and pack
IO remain Java. Source reviewed at [`ed8dc571`](https://github.com/HungLo2020/MattMC/commit/ed8dc5714631e0e55d31dbd448789f21a47bedf6);
this documentation review inspected implementation/test definitions and ran no
Java/Rust, client, device or audible checks.

## Ownership

| Rust owns | Java still owns |
| --- | --- |
| Parsed `sounds.json` registry, weights and nested-event resolution | Reading `sounds.json` text and listing sound files during reload |
| Asset cache (loading, ready, failed) per reload generation, and preload | Reading `.ogg` bytes from packs on the IO pool when Rust asks |
| Voices, category volumes, released-instance age policy (normally 20 unpaused ticks; paused music bypasses the age gate), delays, manual loops, pause and resume | `SoundInstance` objects: `resolve`, `canPlaySound`, per-tick `tick()` |
| The "Sound engine" thread, which owns the OpenAL device | `SoundEventListener`s (subtitles), music and ambient managers |
| Device-change and disconnect detection | Triggering the reload when Rust reports a device change |

Java identifies each `SoundInstance` to Rust by a numeric ID. An ID lives
while Rust still references the instance: it is active, queued or delayed.
Rust reports released IDs at the end of each tick. Java removes active/ticking
membership and forgets IDs that are not requeued; manual loops retain their
instance object and ID for replay.
A policy `STARTED` result admits the voice before asynchronous asset/device
work completes; it does not confirm audible playback.
That minimum age concerns policy/instance retention: the audio thread can reap
a stopped OpenAL source on `deviceTick` before the policy forgets its instance.

## Threading

Device creation and queued voice/source operations run on the device thread.
The first queued command lazily starts a persistent Rust "Sound engine" thread.
Java calls update native policy under a mutex and queue the resulting device
operations. Ordinary play/update calls do not wait for device execution; this
is not a lock-free or universally nonblocking interface. Initialization,
stop-all and cleanup wait for acknowledgement. Emergency shutdown also uses
those blocking paths. Cleanup destroys the device but does not terminate the
thread's command loop; a later initialization can recreate its device.
Asset-generation eviction is invoked on the Java caller between stop-all and
device cleanup, so this is not a claim that every backend lifetime operation
has moved onto the worker. Preserve reload/eviction thread-context checks.

## Tick order

`SoundEngine.tick` uses the following order:

1. `tickBegin`: advances the policy clock only when unpaused and reports
   whether the device changed. If it did, Java reloads.
2. When not paused: Java plays queued tickables, ticks the ticking sounds and
   sends their volume, pitch and position in one batch.
3. `tickEnd`: Rust cleans up instances whose voices were reported released.
   When unpaused it requeues manual loops and collects due delayed sounds.
4. Java forgets released instances. When unpaused, it ticks due tickable
   instances before replaying the due sounds.
5. `deviceTick`: the audio thread reaps stopped sources and polls the device.

## Constraints

- **Caller-owned RNG.** Resolution must consume the caller's `RandomSource` exactly as
  vanilla does. `resolve` is a step protocol: Rust asks for a draw bound, and
  Java calls `nextInt(bound)` and retries with the draws so far. Do not move
  drawing into Rust unless the random source moves with it. The native
  resolver bounds nesting at 32; deeper/cyclic references can resolve empty
  rather than reproducing unbounded Java recursion. The Java bridge also caps
  retained draws at 64, returning empty before requesting a 65th. The focused draw tests
  are not universal resource-pack or RNG-parity proof.
- **Parsing compatibility.** Representative Gson/`GsonHelper` coercions are
  asserted in `audio/policy/tests.rs`; this is not an exhaustive parser oracle. Booleans accept strings. Ints truncate.
  An entry `name` without a namespace means `minecraft:`. A nested event
  weighs its target's total weight. A missing file drops its entry with a
  warning.
- **`Mth.clamp` semantics.** Volume clamping keeps NaN the way Java does.
- **Asset generations.** Each reload clears the cache and bumps the generation.
  Bytes delivered for an older generation are discarded.
- **Replaced voices.** Playing an instance that is already playing starts a new
  voice. The old one keeps playing unowned until it ends, as in vanilla.

## Dev audio validation

The opt-in `mattmc.dev.audioValidation` helper
[`DevAudioValidation.maybeRunIsolated`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/blaze3d/audio/DevAudioValidation.java)
runs direct backend checks through a temporary Java `Library`, on a separate
thread and device. It
normally joins that worker after it closes the validation device, before the
Rust engine initializes its device. Inspect the emitted validation status and
errors; launching this helper is not proof of audible output or complete
reload/disconnect/interrupted-shutdown behavior.

## Troubleshooting

- **`Error starting SoundSystem. Turning off sounds & music`.** Startup failed.
  Inspect the attached exception to distinguish isolated validation, native
  initialization, category setup and initial asset-request failures. Check the
  selected device or `ALSOFT_DRIVERS` when the cause is device initialization.
- **`Missing sound for event` or `File ... does not exist`.** The first comes
  from `SoundEngine.reload`; the second is a Rust registry warning logged on
  apply. Both keep vanilla's wording. Check the pack's `sounds.json` and file
  paths.
- **`WrongMethodTypeException` from `NativeSoundPolicy`.** A downcall's
  `invokeExact` result cast does not match its declared layout. Even an
  ignored result must be cast to the declared return type.
- **No sound after unplugging a device.** Rust sets `reload_needed`, and the next
  `tickBegin` returns true. Check that `SoundEngine.tick` still reloads on it.

## Verification scope

The [14 Rust policy tests](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/policy/tests.rs)
inspect registry parsing/resolution, cache generations and pure playback-policy
transitions. The bundled JSON fixture assumes each referenced file exists and
uses one `bound - 1` draw sequence per event. It checks neither every weighted
branch nor actual pack bytes, decoding or audible output. The
[three Java FFI tests](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/test/java/net/minecraft/client/sounds/NativeSoundPolicyTest.java)
check direct/nested caller-draw requests and installed metadata.

The removed executor tests previously covered an active ZIP read during shutdown
and idle-worker wake/restart. Their removal does not transfer those guarantees
to the new persistent thread. Verify interrupted initialization, reload with
pending IO, generation eviction, stream underrun/recovery versus actual EOF,
stop/destroy acknowledgement, device replacement and repeated
play/stop/reload against real devices. Retain resource counts, status files and
logs alongside listening checks; unit tests and Wiki CI do not establish audio
hardware, long-session lifetime or complete migration acceptance.

The [source ownership and acceptance review](https://github.com/HungLo2020/MattMC/issues/773#issuecomment-6104077162)
records this partial migration and the outstanding device, streaming and
shutdown checks. Issue #773 remains open.
