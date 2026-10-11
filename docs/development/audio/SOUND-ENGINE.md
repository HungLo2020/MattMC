# Sound engine

The Rust policy in [`audio/policy/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/mod.rs)
replaces vanilla `SoundEngineExecutor`, `ChannelAccess`, `SoundBufferLibrary`
plus `SoundEventRegistration`, its serializer, the `Weighted` interface and
`SoundEventListener`. `Sound` and `WeighedSoundEvents` remain Java views.
[`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/SoundEngine.java)
retains the gameplay-facing Java API and instance callbacks. The sounds Rust
plays on its own (music, ambience, world events, the preview) are covered in
[client sounds](CLIENT-SOUNDS.md). Pack IO and the `SoundInstance` objects
gameplay code creates remain Java.

## Ownership

| Rust owns | Java still owns |
| --- | --- |
| Parsed `sounds.json` registry, weights and nested-event resolution | Reading `sounds.json` text and listing sound files during reload |
| Asset cache (loading, ready, failed) per reload generation, and preload | Reading `.ogg` bytes from packs on the IO pool when Rust asks |
| Voices, category volumes, released-instance age policy (normally 20 unpaused ticks; paused music bypasses the age gate), delays, manual loops, pause and resume | `SoundInstance` objects gameplay code creates: `resolve`, `canPlaySound` |
| The vanilla tickables' `tick()` rules ([`rules.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/rules.rs)) | The entity facts those rules sample, and custom (mod) `tick()` bodies |
| The "Sound engine" thread, which owns the OpenAL device, and reopening it after a device change | The sound device option and directional audio setting |
| Subtitle entries and the once-per-event warnings | Drawing the subtitle overlay; logging Rust's queued lines |

Java identifies each `SoundInstance` to Rust by a numeric ID. An ID lives
while Rust still references the instance: it is active, queued or delayed.
Rust reports released IDs at the end of each tick. Java removes active/ticking
membership and forgets IDs that are not requeued; manual loops retain their
instance object and ID for replay. Rust-owned instances use IDs with bit 60
set and never reach Java.
A policy `STARTED` result admits the voice before asynchronous asset/device
work completes; it does not confirm audible playback.
That minimum age concerns policy/instance retention: the audio thread can reap
a stopped OpenAL source on `deviceTick` before the policy forgets its instance.

## Threading

Device creation and queued voice/source operations run on the device thread.
The first queued command lazily starts a persistent Rust "Sound engine" thread.
Java calls update native state under a mutex and queue the resulting device
operations. Ordinary play/update calls do not wait for device execution; this
is not a lock-free or universally nonblocking interface. Initialization,
stop-all, cleanup and a device reopen wait for acknowledgement. Emergency
shutdown also uses those blocking paths. Cleanup destroys the device but does
not terminate the thread's command loop; a later initialization can recreate
its device. Asset-generation eviction is invoked on the Java caller between
stop-all and device cleanup (including during a reopen), so this is not a
claim that every backend lifetime operation has moved onto the worker.
Preserve reload/eviction thread-context checks.

## Tick order

`SoundEngine.tick` uses the following order:

1. `tickBegin`: advances the policy clock only when unpaused. If the audio
   thread saw the output device change or disconnect, Rust closes and reopens
   it with the same settings and reports `TICK_REOPENED`; Java then forgets
   every instance, as vanilla's reload does (`TICK_STOPPED` if reopening failed).
2. When not paused: Java plays queued tickables and ticks the ticking sounds.
   A [`RuledSoundInstance`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/resources/sounds/RuledSoundInstance.java)
   only writes a sample (removed, riding, speed, ...). Any other tickable runs
   its own `tick()` and sends its volume, pitch and position. Everything goes in
   one batch, and Rust returns the ruled instances that stopped or (bees)
   switched.
3. `tickEnd`: Rust ticks its own tickables and cleans up instances whose
   voices were reported released. When unpaused it requeues manual loops,
   plays its due delayed sounds and collects Java's due ones.
4. Java forgets released instances. When unpaused, it ticks due tickable
   instances before replaying the due sounds.
5. `deviceTick`: the audio thread reaps stopped sources and polls the device.
6. Java logs the lines Rust queued (`W ` warn, `E ` error, `I ` info).

## Constraints

- **Caller-owned RNG.** A Java instance's resolution must consume its own
  `RandomSource` exactly as vanilla does. `resolve` is a step protocol: Rust
  asks for a draw bound, and Java calls `nextInt(bound)` and retries with the
  draws so far. Rust-owned sounds draw from a bit-exact port of
  `LegacyRandomSource` (`world/level/levelgen/random.rs`). The native
  resolver bounds nesting at 32; deeper/cyclic references can resolve empty
  rather than reproducing unbounded Java recursion. The Java bridge also caps
  retained draws at 64, returning empty before requesting a 65th. The focused draw tests
  are not universal resource-pack or RNG-parity proof.
- **Parsing compatibility.** Representative Gson/`GsonHelper` coercions are
  asserted in `audio/policy/tests.rs`; this is not an exhaustive parser oracle. Booleans accept strings. Ints truncate.
  An entry `name` without a namespace means `minecraft:`. A nested event
  weighs its target's total weight. A missing file drops its entry with a
  warning.
- **Java float order.** The rules, mood and fades keep Java's `float`
  arithmetic order and `Mth` semantics (`clamp` keeps NaN). Write tests that
  compare exact values, not tolerances.
- **Asset generations.** Each reload clears the cache and bumps the generation.
  Bytes delivered for an older generation are discarded.
- **Replaced voices.** Playing an instance that is already playing starts a new
  voice. The old one keeps playing unowned until it ends, as in vanilla.
- **`invokeExact` arguments.** Pass `MemorySegment` arguments through typed
  locals: an inline conditional reaches a polymorphic-signature call as
  `Object` and throws `WrongMethodTypeException`. Cast every result to its
  declared type, even when it is ignored.

## Dev audio validation

The opt-in `mattmc.dev.audioValidation` helper
[`DevAudioValidation.maybeRunIsolated`](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/java/net/blaze3d/audio/DevAudioValidation.java)
runs direct backend checks through a temporary Java `Library`, on a separate
thread and device. It
normally joins that worker after it closes the validation device, before the
Rust engine initializes its device. Inspect the emitted validation status and
errors; launching this helper is not proof of audible output or complete
reload/disconnect/interrupted-shutdown behavior. It and `NativeAudioTest` are
the only users of the Java `Library`/`Channel` bridge.

## Troubleshooting

- **`Error starting SoundSystem. Turning off sounds & music`.** Startup or a
  device reopen failed. At startup, inspect the attached exception to
  distinguish isolated validation, native initialization, category setup and
  initial asset-request failures; after a reopen the Rust error text follows
  the message. Check the selected device or `ALSOFT_DRIVERS` when the cause is
  device initialization.
- **`Missing sound for event` or `File ... does not exist`.** The first comes
  from `SoundEngine.reload`; the second is a Rust registry warning logged on
  apply. Both keep vanilla's wording. Check the pack's `sounds.json` and file
  paths.
- **`WrongMethodTypeException` from `NativeSoundPolicy` or `NativeSoundClient`.**
  See the `invokeExact` constraint above.
- **No sound after unplugging a device.** Rust sets `reload_needed` and the next
  `tickBegin` reopens the device. Check the `Sound engine reopened` or error
  line in the log and that `SoundEngine.tick` forgets its instances on
  `TICK_REOPENED`.
- **A vanilla tickable never changes volume.** It should extend
  `RuledTickableSoundInstance`, whose `tick()` does nothing: check that its
  rule kind matches `RuleKind::from_abi` and that it samples the flags its rule
  reads.

## Verification scope

The [Rust policy tests](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/tests.rs)
inspect registry parsing/resolution, cache generations and pure playback-policy
transitions. The bundled JSON fixture assumes each referenced file exists and
uses one `bound - 1` draw sequence per event. It checks neither every weighted
branch nor actual pack bytes, decoding or audible output.
[`client_tests.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/audio/policy/client_tests.rs)
compares each rule, subtitles, native plays, music, ambience and the preview
against values derived from the vanilla bodies, and Java `LegacyRandomSource`
fixtures. The Java tests in `net.minecraft.client.sounds` check caller-draw
requests and installed metadata (`NativeSoundPolicyTest`), the ambient
handlers against vanilla's bodies on one level random over random ticks
(`NativeAmbienceParityTest`), and the bindings end to end on OpenAL's null
device (`NativeSoundEngineTest`; the Java test task sets `ALSOFT_DRIVERS=null`).

The removed executor tests previously covered an active ZIP read during shutdown
and idle-worker wake/restart. Their removal does not transfer those guarantees
to the new persistent thread. Verify interrupted initialization, reload with
pending IO, generation eviction, stream underrun/recovery versus actual EOF,
stop/destroy acknowledgement, device replacement and reopening, and repeated
play/stop/reload against real devices. Retain resource counts, status files and
logs alongside listening checks; unit tests and Wiki CI do not establish audio
hardware, long-session lifetime or complete migration acceptance.

The [source ownership and acceptance review](https://github.com/HungLo2020/MattMC/issues/773#issuecomment-6104077162)
records the earlier partial migration and the outstanding device, streaming and
shutdown checks. Issue #773 remains open.
