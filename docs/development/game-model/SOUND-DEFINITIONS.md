# Native sound definitions

> Implemented and verified for the recorded content/runtime scope. The overall
> performance target remains unmet.

## Ownership

`content/sound/` owns 2,011 ordered event definitions, 125 block sound profiles
and 23 note-block instruments. The instrument property domain shares the
native instrument names directly. Event IDs are dense and stable. Sound profiles
reference event IDs in break/step/place/hit/fall order; distinct profile names
retain distinct identities even when they select the same events. The two
integrated Alex's Caves profiles share this owner.

Sound metadata has process lifetime and no world or device references. Playback
sources, buffers, decoding and mixing remain in `audio/`; the remaining Java
sound scheduling policy has not migrated. Resource-pack sound assets keep
their existing lifecycle. This content slice does not claim complete audio
migration.

## Temporary Java views

`NativeSoundDefinitions` reads bounded immutable CPU tables. Its event views
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
checks and the v7 Frozen observer described in [block definitions](BLOCK-DEFINITIONS.md).
The observer checks every event's identity/range, every profile's float bits and
event references, and every instrument's name and behavior flags. Follow these
with real-client verification before publishing a runtime change.

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
shaders+DH p99, remain below the target. See the current figures in the root
`SUMMARY.md`; this evidence does not establish complete audio/gameplay parity.
