# Rust DH source occlusion

The ordinary full-data update now culls directly from the retained Rust source.
Rust admits the table, selects interior centers, detaches immutable producer or
parent backing, and runs the complete pass in Frozen's x-major/z-minor order.
Java no longer clones 4,096 column references, constructs an input descriptor
grid, or repeats center selection for that route. It refreshes escaped
center readers only; unescaped columns remain native.

Implementation: [owned pass](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/occlusion_pass.rs),
[source owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/source.rs)
and [temporary reader projection](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhOcclusionPass.java).
The original culling policy is unchanged; see the
[full-data constraints](RUST-DH-FULL-DATA.md#constraints-when-changing-this-subsystem).

## Ownership and failure rules

- Direct merges publish new vectors into the Rust table and release their
  preparation without Java adoption. Java readers acquire independent primary
  leases on demand. Explicit close materializes unescaped native values for
  closed-source compatibility, using compact native presence/reader bitsets.
- The source holds primary column leases. Admission checks unique primary
  identities, supported rows, bounded lengths and every material ID before
  changing any point values. Missing and empty neighbors stay open.
- Distinct immutable leases may share producer storage. Every selected center
  detaches before the first removal, preserving independent producer readers
  and primary identity. Neighbors are read from their current primary storage
  after earlier center mutations.
- An entirely mutable source needs no detach allocation. Rust combines center
  selection and culling into one pass. Immutable sources retain the separate
  detachment phase and its failure prefix. Material IDs are admitted once before
  either path; packed height/bottom setters and removal preserve those IDs.
- Output contains a count and index/address/length triples, with capacity for
  4,096 centers. It transports private CPU extents, never point values or GPU
  handles. Thread-local output is bounded and retains no source references.
- Allocation failure returns the successfully detached prefix. Java refreshes
  those readers before throwing; no point values have changed. An admission
  decline leaves values and output untouched.
- Keep the existing DH caller exclusion through native execution and reader
  projection. Independent leases protect lifetime, not simultaneous mutation.
  Readers survive source closure; grouped allocation release remains GC-driven.
- Unsupported/custom Java lists, aliases and closed sources retain the existing
  descriptor/per-column compatibility routes. Source orchestration, persistence
  and sparse contributor-map transport remain migration work.

## Verification and CPU comparison

Image `4f211cbd` on upstream `437e2bc15` passes 43 focused DH Rust tests,
full Rust (2,597 passed, three ignored) and full Java (1,946 reported,
1,944 executed, two skipped, zero failures/errors across 350 suites).
All six Java workers load the exact native image. Source/native/class,
Frozen inventory and protected-prompt guards match. Controls cover both passes
of all 32 saved Frozen culling cases, all 48 saved merge cases, invalid IDs,
immutable detachment prefixes, stable escaped identities, real culling without
projection, narrower identity maps, hash collisions and retained readers after
source/producer/preparation closure.

Fresh six alternating pairs, five 1,000-call warmups and 1,000 measured calls
per JVM give:

| CPU workload | Current ms | Frozen ms | Current/Frozen |
|---|---:|---:|---:|
| Create/hash/close | 10.522 | 13.921 | 0.756 |
| Update/hash without culling | 57.871 | 71.079 | 0.814 |
| Update/hash with visual culling | 251.055 | 260.391 | 0.964 |

All three median gates pass with matching checksums. These CPU fixtures do not
establish gameplay/FPS, streaming, broad parity or sustained memory acceptance.

A source owns one 512-byte bitmap of escaped Java identities. Demand for an
empty reader registers without a point vector; retaining a native column
registers its reader lease. Structural replacement updates that fact.
Registration preserves primary versions/copy proofs. Merge and mutable culling
emit escaped extents only. Immutable culling retains its complete ordered
detach/cull sequence, then compacts output, including a failed detachment prefix.
The bitmap never selects terrain or disables culling: the unescaped-column
control proves real point removals with zero output rows.

## Historical rejected candidates

Primary-publication image `68610e3b` passes full Rust (2,596 passed,
three ignored) and Java (1,946 reported, two skipped), but culling still fails
at 261.962/259.774 ms (1.0084×). The continuation above adds native reader
registration and projection selection.

Rejected scratch-reuse image `37456f44` passes 43 focused and 2,597 full Rust
tests and 1,946 reported Java tests (two skips), but culling fails at
263.279/260.163 ms (1.012×). Its pool, recycling ABI and obsolete control are
removed; receipts remain. Its profile has 6,486 update samples: culling 2,549
leaf, preparation 982, commit policy 903, extent projection 117, source hash
344 and metadata/hash publication 296.

Historical failed culling candidates, all six alternating pairs:

| Candidate | Calls per measurement | Current/Frozen ms | Ratio |
|---|---:|---:|---:|
| Original descriptor route `dc997036` | 200 | 90.388/52.317 | 1.728 |
| First source owner `a4951acb` | 1,000 | 359.564/258.502 | 1.391 |
| Mutable selection/cull scan `3a1ebb82` | 1,000 | 343.327/258.474 | 1.328 |
| Identity-map preparation `5c3cf01f` | 1,000 | 322.687/259.785 | 1.242 |
| Removed indexed experiment `9cd5e4c9` | 1,000 | 339.178/259.810 | 1.305 |

The native-symbol profile of the mutable candidate contains 8,487 update
samples: commit 1,782 inclusive (1,342 policy), preparation 1,093,
culling 2,642 leaf, Java commit 465 and source hashing 444 leaf.
These observations motivated primary publication and lazy readers.
The failed indexed kernel and duplicate fixture were removed. Keep raw
receipts for rejected measurements; do not compare absolute 200-call and
1,000-call measurements as equivalent workloads.

## Run the checks

With JDK25 and consistent native profile overrides:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::level::chunk::dh_columns -- --test-threads=1
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative compileTestJava
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests 'net.minecraft.world.level.levelgen.NativeDh*Test'
python3 DevUtils/RunWiki.py check
```

`VerifyDhSource.py --mode cull` runs the identical compiled driver on Frozen and
Current with visual compression enabled. Its CPU fixture uses registered stone
and four points per column; it asserts that hidden points were actually removed.
Use fresh output directories, alternating pairs and five equal-count warmups.
Keep builds, tests, profiles, observers and input hashing outside timing windows.
Do not overlap Gradle builds in this checkout: a concurrent rebuild hit an
output-cache race. The failed log is retained; the final isolated compilation,
full Java suite and class guards pass.
This is supplemental CPU evidence, not gameplay/FPS or sustained memory proof.

Receipts: `build/native-dh-source-occlusion/`,
`build/native-dh-primary-publication/`, `build/native-dh-scoped-preparation/`,
`build/native-dh-reader-publication/`.
