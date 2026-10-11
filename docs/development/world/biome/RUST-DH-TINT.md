# Rust DH cached tint neighborhoods

The current implementation moves canonical DH block/biome tint-cache storage
and cached neighborhood sampling into
[`dh_columns/tint.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/tint.rs).
The normal tint getter reads the [retained Rust source table](../chunk/RUST-DH-SOURCE-TABLE.md)
and material map directly. It exports no point arrays and builds no second source.
Resolver callbacks and client registry/holder lookup remain Java. Custom wrappers,
nonempty Java columns, oversized spans and unsupported coordinates retain the
original callback route. This is a CPU migration, not complete color rendering
or server/client migration.

Source reviewed at [`85475b61`](https://github.com/HungLo2020/MattMC/commit/85475b61defddd2da5e0d0a5f310093145a577d3).
The execution counts and timings below, and the linked [hardware preflight](../../rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures),
are the author’s committed reports. This documentation review inspected implementation and test
assertions; it did not rerun Java/Rust, benchmarks or clients, or inspect the
untracked raw receipts.

## Constraints

- Preserve Frozen's x-first/z-second neighborhood order, first containing point,
  overlapping-column first match, transparent tint RGB arithmetic and opaque
  blended alpha. No-data neighborhoods return the center's cached tint.
- Preserve the mutable position at the first cache miss and after a complete
  neighborhood. Cached `Integer.MIN_VALUE` remains distinct from an absent entry
  for publication, but both yield the original invalid-color result to sampling.
- Native tint keys use exact UTF16 block/biome identities and retain the original
  wrapper hash and first inserted binding when a biome serializes again. Equal stock wrappers
  share colors across getter instances. Java binds CPU identity owners and stable
  primary atomic color cells. Its transport cache stores cell handles and native
  generation tags, never colors. Membership/key changes invalidate successful and
  missing projections; numeric updates publish directly into the same native cell.
  Custom wrappers retain their original map.
- Native locks never enclose resolver callbacks. Resolver-driven traversal keeps
  Java sequencing so callbacks may mutate sources or throw. Only the cached
  traversal admits to Rust; preserve callback behavior before extending admission.
- Caller exclusion pins the retained source, material map and tint bindings
  throughout the call. Rust plans the neighborhood and reads primary columns,
  including their current extent after growth. Normal stock sources need no
  Java point descriptors or second Java neighborhood plan.
  The bounded critical call allocates nothing and never waits for locks; limits
  are 4,096 point visits, 49 keys, 16 hash collisions per key and 128 UTF16 units
  per compared identity. Larger or contended inputs decline without output
  writes and retry through an ordinary native call. Neither invokes callbacks
  under a native lock. The older borrowed-descriptor adapter remains for
  compatibility inputs, with scratch pins cleared in `finally`.
  Stock radii 0..3 and section details 6..8 admit. Overflow/custom settings decline.
- Frozen's negative-coordinate relative-column formula is preserved, including
  truncating division after its negative offset. Do not replace it with a normal
  floor/modulo formula. Source containment uses the requested detail6 section.
- Shared native colors retain Frozen's process-lifetime cache semantics; they
  store numeric colors and UTF16 identities, never Java worlds or GPU handles.
  There is no world/reload key, so this is not a world-scoped eviction policy.
  World/reload resource behavior and realistic performance still need measurement.

## Verify

Use JDK25 and the same release configuration throughout:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::level::chunk::dh_columns::tint
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhTintTest
python3 DevUtils/RunWiki.py check
```

[`GenerateFrozenDhTintOracle.java`](https://github.com/HungLo2020/MattMC/blob/85475b61defddd2da5e0d0a5f310093145a577d3/DevUtils/tests/world/chunk/GenerateFrozenDhTintOracle.java)
records the actual untouched Frozen cached tint getter with seven compiled-class
pins. Its 288 synthetic sparse CPU neighborhoods cover negative sections,
boundaries, all stock radii, larger LODs, overlapping points, empty samples and
missing/invalid cache entries. Explicit cache contents are fixture inputs;
expected colors and position mutations come from the actual getter.
The initial attempt to seed through a resolver required a live client and failed;
its log/partial fixture are retained. The completed recorder seeds Frozen's cache
directly and does not claim resolver or gameplay verification.

The following results are historical, before direct source-table consumption;
current table verification is recorded in the linked source guide.
The author reports that release `007ebaef` passes the full Rust suite: 2,547 passed, three intentionally
ignored and zero failures. Five Java adapter controls match recorded Frozen
answers, mutable keys, concurrent color publication, callback mutation and
pin release after success/partial/oversized declines. Full Java verification
reports 1,923 tests, two skips and zero failures/errors across 345 suites
(1,921 executed); all six workers map the exact release image. The completed CPU matrix uses six alternating AB/BA pairs per
scenario, identical driver classes, matching checksums and five warmup rounds
at the measured call count in every JVM; builds/tests stay outside timing.

| Radius | Section detail | Calls | Current ms | Frozen ms | Current/Frozen |
| --- | --- | --- | --- | --- | --- |
| 0 | 6 | 500,000 | 2.92 | 3.02 | 0.968 |
| 1 | 6 | 500,000 | 81.55 | 83.22 | 0.980 |
| 2 | 6 | 100,000 | 31.42 | 44.44 | 0.707 |
| 3 | 6 | 50,000 | 27.10 | 42.09 | 0.644 |
| 3 | 7 | 50,000 | 26.68 | 41.36 | 0.645 |
| 3 | 8 | 500,000 | 3.04 | 3.36 | 0.906 |

These are median synthetic cached-tint elapsed times, not FPS or whole-game
acceptance. Detail 8 with stock radii takes the unsmoothed cached route; its row
does not measure blended-neighborhood work. Protocols, all runs and input guards are retained in
`build/native-dh-tint/matrix-int-hash-*` and `source-pins-final.json`.

Rejected prototypes and their logs remain under `build/native-dh-tint/`.
The first cached-neighborhood adapter regressed by 53%; an intermediate
no-blending path regressed by 3.86×. Atomic cell projections fixed that case,
but bounded blending still regressed by 24.3% at radius1. A JVM profile landed
mostly in pin cleanup; reducing that cleanup did not improve timings, so the
experiment was removed. JVM samples do not attribute Rust internals. The
current integer-key table mixes Frozen's existing wrapper hash directly;
collision ordering and UTF16 equality remain unchanged. Initial recorder,
driver ABI, old native-symbol, test compilation and parallel presenter-fixture
failures are retained; each corrected run has its own log. Presenter fixtures
now serialize their process-wide reservation and check cross-registry denial.

[`GenerateFrozenDhTintKeyOracle.java`](https://github.com/HungLo2020/MattMC/blob/85475b61defddd2da5e0d0a5f310093145a577d3/DevUtils/tests/world/chunk/GenerateFrozenDhTintKeyOracle.java)
adds 480 actual Frozen cache-key operations across 32 histories, including
equal wrappers, hash collisions and changed serialized identities.
Run the scoped CPU comparison with
[`VerifyDhTint.py`](https://github.com/HungLo2020/MattMC/blob/85475b61defddd2da5e0d0a5f310093145a577d3/DevUtils/tests/world/biome/VerifyDhTint.py),
passing the JDK executable, isolated Frozen/current classpath files, native
directory and a new output directory. Builds stay outside timing runs.
The script reports ratios but imposes no performance pass/fail threshold. It
hashes the native image and original compiled driver inputs, then rechecks the
native image before each launch; those guards do not hash every Frozen/current
classpath entry or independently establish its correspondence to source.
No runtime, FPS, broad parity or sustained-memory acceptance follows.

The [ownership review](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6103285146)
independently decoded the committed fixtures: 288 neighborhoods with seven
class pins and 8,296 point entries, plus 480 key operations across 32 histories,
both ending exactly at EOF. This is fixture inspection, not a test rerun. The
[evidence review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6103287294)
keeps synthetic timings and the author-reported driver probe separate from
MattMC runtime acceptance; both issues remain open.
