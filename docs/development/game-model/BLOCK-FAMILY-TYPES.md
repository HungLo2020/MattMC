# Block-family configuration

> Native ownership; runtime results below are author-recorded evidence from
> [`87046367`](https://github.com/HungLo2020/MattMC/commit/87046367cdf0a4a427f10066a9010dd6d39fd422).
> The overall performance target remains unmet.

## Ownership

[`content/block/family/`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/rust/content/block/family/mod.rs)
declares 17 block-set types and 12 wood types. These
contain shared interaction flags, pressure-plate sensitivity, canonical sound
references and sign/gate sound profiles. Rust's ordered block catalog binds
141 blocks to typed family parameters: doors, trapdoors, buttons, pressure
plates, weighted plates, gates, and four sign attachment variants.

The `Family` enum admits only the parameters used by each family. Button
press duration and weighted-plate capacity are bounded integers. Native
block-set and wood identities refer to the shared [sound owner](SOUND-DEFINITIONS.md);
they have no world, audio device or rendering resources. Existing integrated
content keeps its actual references, including pewen doors using cherry and
pewen gates/signs using oak.

This slice owns configuration. Neighbor updates, scheduling, entity queries,
world changes and sound playback remain with their current consumers. It does
not claim that door/button/plate/sign gameplay has fully migrated.

## Temporary Java construction

`BlockSetType` and `WoodType` become canonical views of the native tables,
retaining their codec and public alias identities. Raw metadata loading stays
separate from constructing those views to avoid recursive initialization.
Native event objects remain accessible before registry holders bind at freeze.

[`NativeBlockFamilies`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/java/net/minecraft/world/level/block/state/properties/NativeBlockFamilies.java)
validates bounded immutable tables and references. Registered factories pass
admitted `Properties` to native-only constructor
overloads. These select the exact family and parameters through immutable CPU
views. Missing admission or a mismatched family fails before allocating the
block's intrusive registry holder. Explicit legacy constructors and arbitrary
unregistered record values remain available for codec/data paths. They are not
a fallback for missing or mismatched registered family bindings.

Per-position/tick work does not make an FFM call to fetch configuration. Rust
consumers read typed definitions directly; current Java behavior reads the
constructor's existing final fields. The bridge is a finite process-lifetime
projection, with no unbounded runtime cache.

## Editing and verification

Edit `family/sets.rs`, `family/woods.rs` and explicit catalog bindings. Preserve
ordered identities and references. Keep profile names and Java aliases aligned
until the Java views are removed. New behavior families should receive their
own typed parameters, not unrelated nullable fields.

Run the native content tests, `NativeBlockFamiliesTest`, existing state/codec
checks, and the current v10 Frozen observer in [block definitions](BLOCK-DEFINITIONS.md).
It retains v8's definitions, public aliases, codec round trips and every registered
family binding. Follow with realistic client/lifecycle/parity/performance
verification.

The results below are preserved from the
[author’s milestone 1i record](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/PROGRESS.md#milestone-1i--block-family-configuration-verified-performance-target-unmet).
This documentation review inspected source only: it did not rerun Java/native
tests, client or performance checks, inspect the local receipts, or independently
review the images.

The standalone draft matches all Frozen definitions/bindings;
27 standalone content/core tests pass. The integrated release build, 26 native
content tests and 35 Java ownership/codec/meshing/save checks also pass. Five
integrated v8 Frozen pairs match all eight digests, with sources/native unchanged
and Frozen intact. Receipt: `build/block-families-master-verification-20261008/results.json`.
Full `validation/native-block-families-master-20261008/` verification passes
1,711 Java and 2,366 Rust tests (two skips/three ignores), all seven lifecycle
cases and both reviewed static coast images. RGB differences are
0.304/0.542/0.642 and 3.664/4.182/3.826, with the DH subset passing and VUID0.
All 16 paired 6,000-frame performance runs are clean; vanilla/DH average FPS
and p99 remain below Frozen, while both shader modes pass this run. The
vanilla+DH median gap is 28.9% and needs investigation. The pinned
[`SUMMARY.md`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/SUMMARY.md)
records **Performance FAIL**; no isolated attribution
to family configuration is established. Root `SUMMARY.md` retains repeats and
tails. Sources/native hashes match the observer, Frozen is unchanged and
25 generated fixture copies retired. Evidence: `build/block-family-migration-draft/runtime-integrity.json`.
This scope does not establish full family gameplay or Rust-only application parity.
