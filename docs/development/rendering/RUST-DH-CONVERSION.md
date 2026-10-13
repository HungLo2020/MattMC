# Rust DH full-data conversion

> **Current state (2026-10-13):** conversion now runs one Rust call per source,
> without the per-column Java loop, heap-list fallbacks or class-identity
> admission described below; see [Rust-only DH render build](RUST-DH-RENDER-BUILD.md).
> The policy and caching rules here still apply. `VerifyDhConversionPolicy` was
> retired; the commands and results below are historical.

Rust owns the
top-down full-data conversion policy: cave culling, avoided-block tint/light
transfer, snow trimming, adjacent-color merging and render-point packing.
Java retains configuration, contextual color queries, source traversal and
semantic sidecar orchestration. Verification scope and remaining limits follow.

## Boundary and lifetime

- `render/dh_columns/conversion.rs` reads scoped native input columns and writes
  bounded native output windows directly. No storage borrow survives a Java
  callback. Callback exceptions return with the original identity and exact
  already-published prefix; nested calls restore the outer context.
- `NativeDhConversionScope` retains first-use intrinsic material facts in Rust
  for one source conversion. Admission requires the stock client world, source
  map, native input reader and ordinary policy sets. Replacing a policy set
  clears the facts. Existing source-mutation exclusion still applies.
- Supported shape/fluid facts come from the installed Rust registries.
  Custom/escaped lists and unsupported wrappers retain ordered getters, using
  the same Rust conversion algorithm.
- Rust also retains already-resolved, untinted stock colors. Texture resolution,
  positional tint and semantic queries remain contextual. Clearing a world's
  color cache advances a native atomic generation, invalidating retained colors.
  A reload during a callback declines caching its old result. Transferred tint
  cannot reuse another point's semantic variant provenance.
- Expanded columns use a lazily allocated, 4,096-point Rust scratch owner per
  admitted scope. Clear its active window before reuse and reduce directly into
  the final native source. Custom/oversized columns retain their list contracts.
  Color-only conversion does not allocate unused semantic companion arrays.
- Close the scope on success or failure. It releases temporary native storage;
  closed scopes cannot allocate replacement scratch. Stock-world calls reuse a
  cleared Java worker context; custom worlds keep distinct position objects,
  and nested calls allocate temporary contexts. No world references survive a
  completed call in the worker context.

This is CPU conversion ownership. VulkanicGAL retains sole ownership of GPU
resources, ordering and presentation.

## Verification commands

Use JDK25 and the release native library:

```sh
./gradlew -PmattmcRustProfile=release check -x testRustNative
CARGO_TARGET_DIR=build/rust/target cargo test --profile suite --manifest-path src/main/rust/Cargo.toml --lib render::dh_columns:: -- --test-threads=1
python3 DevUtils/tests/rendering/VerifyDhConversionPolicy.py --saved-manifest /path/to/saved-dto-manifest.json --output build/dh-conversion-check --resolved-world --full-source
python3 DevUtils/RunWiki.py check
```

For focused Java checks, select `NativeDhConversionScopeTest`,
`NativeDhRenderConversionTest`, `NativeDhConversionReaderTest` and
`FullDataToRenderDataTransformerSemanticMaterialTest` with Gradle `--tests`.

The driver compiles observers separately against Current and unchanged Frozen.
`--predecessor` accepts pinned `classes/`, `native/` and `pins.json` from the
preceding Current build. Stock/custom oracles each cover 128 columns and seven
failure positions: 896 cases per run, comparing complete points, semantic
sidecars and ordered callback traces. Frozen is the color-only correctness
baseline; predecessor comparisons additionally cover Current's semantic sidecars.

Saved geometry/light values are real; nine material bindings and colors are CPU
fixtures. `--resolved-world` uses actual stock reads of initialized untinted
color caches, excluding texture resolution, contextual tint and a running world.
Default column timing includes reflective dispatch and full checksum reads.
`--full-source --resolved-world` instead times the public production transformer,
including output allocation and reduction, retains its complete outputs, then
hashes every point and closes owners outside timing. Do not equate these scopes
or present either as gameplay/FPS evidence.

Timing uses six interleaved JVMs with a predecessor, four warmups and eight
samples per JVM. Source/classes/native/corpus/prompt hashes are checked around
runs. Java thread allocation excludes native memory and is not a retention bound.

## Recorded CPU results

All times below are milliseconds per 24-source batch, ordered
**Current / preceding Current / Frozen**. Receipts are under
`build/native-dh-conversion-policy/`. These are successive experiments, not
verification of the latest unmeasured source.

| Scope and revision | CPU ms | Java heap MB | Receipt directory |
| --- | --- | --- | --- |
| Initial callback-heavy column prototype | 174.70 / 38.61 / 33.12 | — | Initial exploratory logs |
| Proxy colors, direct retained input/output | 69.13 / 37.24 / 21.95 | 70.47 / 48.45 / 42.93 | `verified-direct/` |
| Proxy colors, callback-frame reuse | 66.53 / 39.85 / 22.00 | 63.39 / 48.45 / 42.93 | `verified-frame-reuse/` |
| Initialized stock colors, scoped material facts | 43.08 / 33.93 / 23.26 | 30.69 / 14.17 / 8.65 | `verified-materials-resolved-world/` |
| Initialized stock colors, context reuse | 41.56 / 34.15 / 23.14 | 12.60 / 14.17 / 8.65 | `verified-context-reuse/` |
| Initialized stock colors, Rust color retention | 27.31 / 34.92 / 22.99 | 12.60 / 14.17 / 8.65 | `verified-constant-colors-isolated/` |
| Public full-source transformer, Java scratch | 65.78 / 62.54 / 64.13 | 31.88 / 34.23 / 29.85 | `verified-full-source/` |
| Public full-source transformer, Rust scratch | 50.29 / 63.57 / 64.69 | 5.85 / 34.23 / 29.85 | `verified-native-scratch/` |

The Java-scratch and isolated-column checkpoints pass ten oracle runs and all
integrity guards but trail Frozen in their respective CPU scopes. Rust color retention improves the
column workload 21.8% against the preceding implementation. A prior timing in
`verified-constant-colors/` overlapped a wiki check and is explicitly exploratory.
The JFR under this directory identified callback-frame allocation and proxy
provider arrays; its 25 execution samples do not establish a ranked CPU profile.

Native `561b048e` moves expanded-column scratch into Rust and removes unused
semantic arrays. Its public full-source workload is 22.3% faster than Frozen
and 20.9% faster than preceding Current, with 80.4%/82.9% less Java allocation
respectively. Complete outputs, ten oracle runs and all integrity guards match.
Full suites pass: 2,736 Rust tests (three ignored) and 2,058 Java/parity tests
(two skipped). All seven real-client
lifecycle scenarios and three Frozen image pairs (vanilla, DH and Iris+DH)
pass with zero Vulkan validation errors. Thirteen originals and ten Current
frame/presenter identities were reviewed; the seven hazy lifecycle views prove
structural behavior only. The clear settled pairs show corresponding coastlines,
foliage, water and distant terrain, with cloud/water phase differences. They do
not establish transient or broad rendering parity, gameplay FPS or long memory.
Runtime receipts: `artifacts/graphics-captures/validation/native-dh-conversion-policy-20261012/`.
Native/source guards pass. The harness retired ten completed copied workspaces,
preserving originals and receipts. Upstream `2b979072d` changes test tiers/tooling,
not production code. Combined `check` passes all three Java tiers: 1,863
ordinary, 184 parity and 11 slow tests, with two skips. The initial new-test
failure is retained: it expected the wrong exception for reading a
closed scratch source, whose existing contract is zero length and index rejection.
The test now checks that contract and refusal to allocate from a closed scope.
Published gameplay measurements remain separately scoped in `SUMMARY.md`.
