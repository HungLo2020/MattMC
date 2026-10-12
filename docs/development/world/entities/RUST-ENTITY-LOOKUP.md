# Rust visible entity tracking

The client and server section managers both use `EntityLookup`. Its primary
ID map, UUID map, insertion order and binding retirement now live in
[`world/level/entity_lookup/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/entity_lookup).
Java keeps a behavior-object binding array and invokes existing callbacks.
It no longer builds or maintains ID/UUID maps or an ordered entity copy.
This is verified bounded tracking ownership, not complete world/entity or
renderer parity.

## Consumers and ownership

[`EntityLookup`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/EntityLookup.java)
serves `LevelEntityGetterAdapter`, ordinary renderer enumeration, tracking and
ID/UUID queries from both client and server. Quiet enumeration reads Rust's
retained CPU rows directly through the read-only
[`NativeEntityLookup`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/NativeEntityLookup.java)
view. It remaps that view only after table replacement, without per-frame
entity arrays or native reconstruction of Java maps.

Section keys, typed section membership, persistence's known-UUID set and
load/visibility orchestration remain Java. Those are subsequent migration
work. Tracking is also distinct from the
[geometry owner](RUST-ENTITY-GEOMETRY.md) and
[ticking membership](RUST-ENTITY-ORDER.md). None of these CPU views carries
native GPU handles; rendering resources remain owned by Rust/GAL.

## Compatibility boundaries

- UUID insertion precedes the virtual ID getter. A getter exception retains
  that UUID prefix; duplicate UUIDs log the original captured UUID and skip ID
  evaluation. Remove commits UUID removal before calling the ID getter.
- ID and UUID entries are independent. ID replacement preserves order, while
  older UUID entries can still name the previous object. Null UUID and all-zero
  UUID are distinct, and IDs retain the full signed 32-bit domain.
- Rust retains a pending binding during virtual ID evaluation. Nested removal,
  reinsertion or a throwing getter cannot invalidate the outer receiver. No
  native mutex spans a Java getter, cast, logger or consumer callback.
- Iterators are live and unmodifiable. Preserve Fastutil's physical hash slots,
  links, collision shifts and resize/shrink behavior across callback mutation;
  do not replace them with a frozen snapshot or a new fail-fast policy.
- Follow the existing world-thread contract. Read-only atomic CPU views do not
  authorize concurrent Java registry mutation. The automatic arena retains the
  owner for reachable wrappers and iterators; retired bindings are cleared
  immediately after their final native map/pending reference disappears.

## Verify and diagnose

Use Linux x64 and JDK25, with unchanged Frozen at
`../MattMC_JavaPerfTesting/MattMC`:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/entity/VerifyEntityLookup.py \
  --java-home /path/to/jdk25 --output build/entity-lookup-reference
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml world::level::entity_lookup \
  -- --test-threads=1
```

The observer compiles separately against actual Frozen and Current production
classes. It compares ordered entries, identity queries, getter/cast prefixes,
exceptions, duplicate UUIDs, nulls, collision shifts and resizing. It also
invokes actual client/server manager add, move, visibility, ticking and removal
flows, comparing callback prefixes and resulting identity/state records. Its bounded
cast callbacks also terminate otherwise cycling mutation prefixes. This
synthetic CPU replay supplements real gameplay; it cannot establish runtime
performance, visual parity or long-session memory behavior. The wrapper pins
source, compiled classes, native library, Frozen HEAD and the protected prompt.

Five native controls pass, including 10,000 tracking/removal cycles reusing
one binding and table contraction after growth. The initial Frozen corpus
differed at two iterator exception messages; a production no-message override
repairs that behavior without normalizing the verifier. The initial Java
manager fixtures required registry bootstrap, now added. Failures remain
recorded. Five Java integration controls exercise actual client/server
section-manager tracking and lifecycle callbacks. Retain failures under
`build/native-entity-lookup/`; native `9c9f085c` passes full Rust (2,685 tests, three ignored) and Java/parity
(2,010 executed, two skipped). The actual Frozen lookup/client/server replay
matches all 4,417 cases byte for byte with source/native/eight Current and six
Frozen compiled-class guards. Geometry/order/range replays also pass
11,489/4,143/645 cases. Seven live lifecycles and two settled world pairs pass with zero VUIDs; all
four originals were reviewed and nine runtime identities match. The additional
cow/shadow/leash comparison exposed a verifier assumption: retained models
have canonical per-part identities rather than one whole-model record. The
verifier now requires a complete, unique, frame-correlated part set and unions
its projected bounds; entity, texture, execution and pixel checks remain.
Three rejection/aggregation controls and all 344 existing reference tests pass.
A guarded diagnostic using the published lookup reproduced the original
observer failure, independently of the new registry.

Frozen's capture helper lacks Current's leash fixture flag. For equivalent
copied-world inputs, build the capture-helper-only adapter:

```sh
python3 DevUtils/tests/rendering/BuildLeashedCowFixture.py \
  --java-home /path/to/jdk25 --frozen-repo /path/to/Frozen \
  --output build/leashed-cow-fixture
```

Use its printed JAVA_HOME/PATH exports for both launches; an older launch JVM
rejects the JDK25-only agent before gameplay. Pass its printed JVM arguments to the existing parity harness with the cow
model, shadow and leash scenarios. The CodeSource guard excludes Current;
The adapter also supplies Current's integrated-server fixed player pose;
Frozen otherwise leaves the leash holder at the saved position and normal
physics snaps the leash. Frozen's gameplay, renderer and registry classes remain unchanged. Require
one Frozen helper-transform receipt and one attach-before-spawn receipt, and
none in Current. Large model diagnostics need a bounded artifact-finalization
budget: use `--cleanup-timeout-seconds 60`, retaining the normal game shutdown
and validation requirements. The first equivalent run passed model-crop,
whole-image and shadow checks but failed overall because the default 15-second
artifact budget killed log processing after game exit; retain that failure.
The equivalent-position run completes both games and finalization; all ten
original images were individually reviewed. Body/shadow/platform/terrain/HUD
agree, and both cows remain attached. Current's near-camera leash is visibly
thinner than Frozen; the guarded published-registry Current control retains
that thin geometry. This renderer discrepancy remains open: the equivalent
leash run passes whole-image/shadow checks but fails the strict model crop.
A separate identical-input cow body/shadow pair passes strict model-crop,
whole-image and shadow checks; all ten originals were individually reviewed.
No leash parity is claimed. A subsequent profile-backed text-copy cleanup
passes fresh Rust2,685/three ignored, Java/parity2,010 executed/two skipped
and actual Frozen lookup4,417. Seven lifecycle/two world/four GUI item pairs
pass with zero VUIDs; twelve originals are reviewed and thirteen Current
runtime identities match. All twenty fresh timing runs finish cleanly and
pass every FPS/p99 floor plus identity/input guards. The earlier vanilla
p995.1% miss remains retained; no isolated improvement or tail cause is proved.
GUI reload-fixture crosshair omission also remains separate from item parity.
See the [matrix and limits](../../rendering/GAMEPLAY-PERFORMANCE.md#local-visible-tracking-checkpoint).
