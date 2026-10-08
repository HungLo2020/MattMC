# Native block state definitions

> Current implementation: Rust declares the names, order, state domains and
> defaults of all 1,235 registered blocks. Focused correctness results below
> are author-recorded milestone evidence.
> Java still supplies behavior factories, shapes, predicates
> and world-dependent gameplay. This is not complete block migration.

## Owners and construction

[`content/block/definitions`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/block/definitions)
contains the ordered catalog and 131 shared property/default sets. Definitions
also select immutable [physical settings](BLOCK-PHYSICS.md) and
[intrinsic state rules](BLOCK-INTRINSICS.md). Their state templates
use typed [native properties](PROPERTY-DEFINITIONS.md) and serialized default
values; Rust derives all 31,809 contiguous state IDs. Keep existing catalog
order and append new entries to preserve IDs.

The registry shares 63 immutable transition graphs, keyed by ordered domain
sizes. This pool is bounded by the declaration table, not an unbounded cache
of runtime inputs. Graph targets are local indices: blocks sharing a graph
retain distinct global state ranges and Java state objects.

`Blocks.register` resolves a native definition and checks registration order.
`Block` projects that definition through `NativeBlockDefinitions`; registered
blocks bypass Java property selection and default-state expressions. Missing
native definitions fail construction. Java class/enum objects remain temporary
bindings for behavior and codecs.

Unregistered codec/test objects retain their existing constructor path while
those APIs migrate. Their graph arithmetic is still Rust-owned, but Java may
select their domains/defaults. This compatibility path does not admit a missing
definition for a registered block.

The Java views borrow process-lifetime CPU buffers and never free the shared
graphs. Dynamically constructed graphs still use automatic arenas; see
[state graph ownership](STATE-GRAPHS.md). Neither path owns rendering resources.

The [block registry](RUST-BLOCK-REGISTRY.md) now imports only remaining state
facts and face data. Its format 7 packet contains block/state/face counts,
offset bounds, per-state face IDs/flags, blocked-light values and offset types.
Emitted light and fluid associations now derive from native intrinsic rules.
Names, property schemas, defaults and value indices no longer
make a native-to-Java-to-native trip. Air and can-occlude flags derive directly
from native physical definitions; incoming packets must not supply them.

## Working on this slice

- Change names/order in `catalog.rs`, and domains/defaults in `templates.rs`.
  Templates must use unique property names in sorted order and valid values.
- Keep Java aliases/factories as compatibility bindings until their behavior
  families move. Do not add a second Java declaration for registered state data.
- Validate state IDs, defaults, transitions, codecs and physical facts against
  Frozen before accepting a declaration change. A changed template can affect
  many blocks and all later global state IDs. Java live-view agreement alone
  cannot independently verify a definition now shared by both sides.

```sh
CARGO_TARGET_DIR="$PWD/build/rust/target-tests" cargo test \
  --manifest-path src/main/rust/Cargo.toml --locked --lib content::
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.world.level.block.NativeBlockRegistryTest' \
  --tests 'net.minecraft.world.level.block.state.NativeStateGraphTest' \
  --tests 'net.minecraft.world.level.block.state.properties.NativePropertyDefinitionsTest'
python3 DevUtils/tests/content/VerifyStateGraphs.py \
  --java-home /path/to/jdk-25 --output build/block-definitions-verification
```

Version 4 observer receipts (used for the state-definition milestone below) retain graph/fluid/property digests and add every
block state's flags, lighting, fluid association, two sampled offsets and six
light-occlusion face box lists. They do not prove all contextual collision or
gameplay behavior; use real world/client checks as well. Version 5 additionally
checks every block’s physical settings and constructor caches; its follow-up
verification is recorded in [physical settings](BLOCK-PHYSICS.md). Version 6
also checks map colors and copied color/emission functions; see
[intrinsic state rules](BLOCK-INTRINSICS.md) for current verification status.
Read the [observer scope and integrity limits](STATE-GRAPHS.md#verification)
before interpreting a passing receipt or its JVM main-thread allocation figures.

The following author-recorded results were not rerun and their local receipts
were not independently inspected for this documentation review. They predate
the physical-settings follow-up. The pre-integration control at published
`df6c6dc77` matched Frozen in three pairs (`build/block-definitions-control-verification-20261008/results.json`).
The native declaration draft also matched every ID/name/range/domain/default,
and its standalone content tests and Java compilation passed. The integrated release build, 17 native content tests and 22 Java
registry/graph/codec/save/meshing tests also pass. Five integrated Frozen pairs
match all four digests, including the block facts
(`build/block-definitions-master-verification-20261008/results.json`). Bootstrap
medians were 2.274/2.227 s, allocation 958.36/1068.40 MB; this does not establish
an isolated startup speedup.

Full `validation/native-block-definitions-master-20261008/` verification passed
1,699 Java tests (2 skipped), 2,356 Rust tests (3 ignored), all seven lifecycle
scenarios and both visually reviewed coast pairs. Vanilla RGB differences were
0.346/0.585/0.697; Iris+DH 3.648/4.165/3.785, with the DH subset passing and no
Vulkan validation errors. These static views do not prove broad movement parity.

Performance remains below the full target. The accepted comparison uses that
workflow's three non-vanilla modes and
`validation/native-block-definitions-vanilla-clean-v2-20261008/` for vanilla.
The original vanilla set was conservatively excluded because isolated draft
compilation overlapped invocation startup; its first replacement failed the
health check after Frozen logged a shutdown `ClosedChannelException`. Frozen
was not changed. All 16 accepted 6,000-frame runs were clean; all four p99
comparisons failed. See root `SUMMARY.md` for both repeats and tail values.
Sources/native hashes still match the Frozen observer, and 33 generated fixture
copies were retired. Performance gaps guide further ownership migration;
these checks do not establish full application acceptance.
