# Native sound definitions

> Current source ownership at
> [`87046367`](https://github.com/HungLo2020/MattMC/commit/87046367cdf0a4a427f10066a9010dd6d39fd422).
> Runtime results below are author-recorded milestone evidence, not independently
> reproduced by this documentation review. The overall performance target remains unmet.

## Ownership

[`content/sound/`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/rust/content/sound/mod.rs)
owns 2,011 ordered event definitions, 125 block sound profiles
and 23 note-block instruments. The instrument property domain shares the
native instrument names directly. Event IDs are dense and stable. Sound profiles
reference event IDs in break/step/place/hit/fall order; distinct profile names
retain distinct identities even when they select the same events. The two
integrated Alex's Caves profiles share this owner.

Sound metadata has process lifetime and no world or device references. Playback
sources, buffers and decoding remain in `audio/`. Java `SoundEngine` retains
sound resolution and scheduling; `SoundBufferLibrary` still loads pack resources,
keeps asset futures and runs generation/close callbacks when clearing its cache.
See the [audio boundary](../PROJECT-ARCHITECTURE.md#audio). These reloadable
resources are separate from immutable definitions; this slice does not complete
audio migration.

## Temporary Java views

[`NativeSoundDefinitions`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/java/net/minecraft/sounds/NativeSoundDefinitions.java)
reads bounded immutable CPU tables. Its event views
register once in native order, and `SoundEvents` fields resolve those canonical
objects. `SoundType` constructs one view per native profile before assigning
its public aliases. `NoteBlockInstrument` keeps Java enum bindings while its
names, event references and category come from Rust.

Raw metadata loading must not construct Java sound-profile or instrument
objects. Keeping those phases separate avoids recursive static initialization.
Registry holders bind only when the registry freezes. Retain canonical event
objects separately for constructors that run before freeze; do not dereference
unbound holders or change registry freeze semantics. Missing aliases, duplicate
registration or changed ordering fail startup.
Unregistered `SoundEvent` and `SoundType` constructors remain available for
existing data/codec paths; they do not alter native registered definitions.

## Editing and verification

Edit event order in `events.rs`, sound-profile declarations in `types.rs` and
instrument declarations in `instruments.rs`. Preserve existing event IDs and
profile identity; append new definitions deliberately. Keep content independent
of audio resources and render materials.

Run native content tests, `NativeSoundDefinitionsTest`, the block/state/meshing
checks and the current v8 Frozen observer described in [block definitions](BLOCK-DEFINITIONS.md).
Version 8 retains the sound/offset coverage introduced by v7.
The observer checks every event's identity/range, every profile's float bits and
event references, and every instrument's name and behavior flags. Follow these
with real-client verification before publishing a runtime change.

The following sound/offset results are preserved from the
[implementation author’s milestone 1h record](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/PROGRESS.md#milestone-1h--sound-content-and-block-offsets-published-059c95621-performance-target-unmet).
This review inspected source, not the local receipt files, and ran no native,
Java, client or performance tests. The results predate the family milestone.

The native evaluator matches every extracted Frozen definition. The standalone
content/core suite passes 24 tests; the wider content/world suite passes 167.
Eleven Java production classes, four new regression tests and the v7 observer
compile. The integrated release build, 32 Java checks and 24 native content/core
tests pass. Five integrated v7 pairs match all seven semantic digests against
Frozen, including every sound definition and block binding. Full client verification passes its seven lifecycle and two visual comparisons. Evidence: `build/block-material-migration-draft/`
and `build/block-materials-master-verification-20261008/results.json`.
No startup or FPS improvement is established for this slice.

Full workflow: `validation/native-block-materials-master-20261008/`. Java
1,708 and Rust 2,363 tests pass (two Java skips, three Rust ignores). All 16
paired performance runs are clean; vanilla/DH average FPS and p99, plus
shaders+DH p99, remain below the target in that milestone. The later
[family performance record](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/SUMMARY.md)
retains **Performance FAIL**; it is a different run. Neither establishes
complete audio/gameplay parity or an isolated sound-migration speedup.
