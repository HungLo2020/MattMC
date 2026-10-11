# Rust entity ticking membership

Both `ClientLevel` and `ServerLevel` use `EntityTickList`, whose ordered
membership and iteration snapshots now belong to Rust. Java retains references to behavior objects and calls their
existing tick callbacks. This does not migrate entity motion, AI, physics,
networking or the complete simulation loop.

## Ownership and iteration

The [Rust owner](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/entity_order)
stores entity keys, insertion order, free slots and generational binding tokens.
The [Java consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/NativeEntityOrder.java)
holds an object-binding array; it has no ordered map or duplicated membership.
Ordinary iteration reads retained immutable CPU pages without assembling a
per-tick entity array. Page views are remapped only after table replacement or
growth. Everything lives in the existing Rust library; no extra crate or GPU
resource is involved.

The first mutation during a tick creates a copy of the retained table. The
current callback traversal keeps the original snapshot, including removed or
replaced objects. Later mutations use the new active table. Java clears retired
object bindings after traversal, then acknowledges completion before Rust
reuses their slots. Transient objects added after the snapshot do not accumulate
in its retirement queue. Quiet ticks do not clone the table.

## Behavior to preserve

- Prepare mutation before virtual `Entity.getId()`, including getters that
  mutate the list or throw. Capture the physical map receiver before that getter
  in add/remove/contains. Contains does not prepare a copy. Preserve the two
  active/passive map identities across repeated getter-driven iteration epochs.
- Replacement preserves insertion position. Removal uses the argument's current
  ID. One physical object can remain under multiple keys after an ID change.
- A nested iteration throws Frozen's `UnsupportedOperationException`; callback
  or getter exceptions propagate unchanged and release iteration ownership.
- An empty list accepts a null consumer; a nonempty list fails when invoking it.
- Growth during callbacks must not invalidate the old pages or the current Java
  binding array. No native mutex remains held while invoking Java behavior.

Follow the existing world-thread contract. Native locking does not turn the
Java callback/binding API into a supported concurrent simulation interface.
Automatic Java scopes retain the native owner until the list becomes unreachable.
Ticking membership is distinct from render tracking and geometry lease lifetime.

## Verify

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/entity/VerifyEntityOrder.py \
  --java-home /path/to/jdk25 --output build/entity-order-reference
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml world::level::entity_order \
  -- --test-threads=1
```

The reference driver compiles against actual unchanged Frozen and Current
classes and compares complete ordered callback/member records. It checks source,
compiled class and loaded native image identities. Its corpus covers virtual
getter prefixes, replacement/removal, nested iteration, exception identity,
numeric ID edges and growth during traversal. This CPU replay is supplemental;
it does not prove realistic runtime performance or long-session memory behavior.
Keep failed receipts. The predecessor draft's 4,134-case Java control did not
verify the native production consumer.

Production `c487f375` passes five Rust controls and matches all 4,143 actual
Frozen cases byte for byte, with source/class/native guards. Fresh combined
suites pass 2,656 Rust tests (three ignored) and 2,003 executed Java/parity
cases (two skipped), with zero failures. Geometry/range comparisons also pass
11,057/645 cases. Seven live lifecycle cases and two settled world image
pairs pass with zero VUIDs; all four originals were reviewed and nine Current
artifacts identify this native image. All 20 timing runs are clean; only vanilla
p99 misses the performance floor. The [gameplay matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary)
records its separate scope and verdict.

Receipts: `build/native-entity-order/receiver-fixed-replay/`,
`receiver-fixed-{build,rust-tests}.log` and
`build/native-entity-order-receiver-fix/{cpu-results,runtime-original-review}.json`.

Rejected history: `a9745542` passed the initial 4,139 cases and seven lifecycle
rows, but the expanded corpus exposed incorrect physical receiver capture
before virtual getters. Its enclosing runtime run was stopped before image
completion or timing. Preserve
`build/native-entity-order/rejected-receiver-reference/` and
`interrupted-for-receiver-fix.json`; the earlier pass did not verify the added
behavior. The repair keeps both physical map identities across getter-driven
swaps. Preceding `a99b6f8d` timings do not verify this new native image.
