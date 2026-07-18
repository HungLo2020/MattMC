# Rust Migration Test Harness

MattMC uses two repositories for Java-to-Rust migration validation:

- `current`: this active MattMC checkout.
- `frozen`: the frozen Java performance checkout, resolved through `DevUtils/Common/platform/directory/directories.json` as `java_perf_repo` unless `MATTMC_JAVA_PERF_REPO` or `--frozen-repo` is set.

The frozen repository is allowed to change only in test infrastructure: `DevUtils`, benchmark launchers, report generation, metadata, and isolated fixtures. Do not change production meshing, rendering, world storage, NBT, save behavior, or benchmarked algorithms in the frozen repo.

## Requirements

Windows:

- Python 3.11+ available as `python`.
- Git available on `PATH`.
- A JDK matching the project build.
- `gradlew.bat` in both repositories.

Linux:

- Python 3.11+ available as `python3` or `python`.
- Git available on `PATH`.
- A JDK matching the project build.
- `gradlew` in both repositories.
- Existing Bash scripts remain supported and may be used as ergonomic wrappers.

## Directory Resolution

Logical paths live in:

```text
DevUtils/Common/platform/directory/directories.json
```

Resolve the frozen Java checkout with:

```powershell
python .\DevUtils\Common\platform\directory\directory_helper.py java_perf_repo
```

The helper normalizes the platform, expands `~` and environment variables, and returns an absolute path for the current OS.

## Harness Contract

Use:

```powershell
python .\DevUtils\PerfTesting\rust_migration_harness.py --target current --workload metadata
python .\DevUtils\PerfTesting\rust_migration_harness.py --target frozen --workload chunk-meshing-hotpath --quads 1 --warmup 0 --iterations 1 --warmup-ms 0 --measure-ms 1
python .\DevUtils\PerfTesting\rust_migration_harness.py --target both --workload metadata
```

Targets:

- `current`
- `frozen`
- `both`

Workloads:

- `metadata`: no child Gradle process; writes repository/platform metadata.
- `gradle-test`: runs the repository test task.
- `chunk-meshing-hotpath`: runs `net.sodium.client.perf.ChunkMeshingHotPathBenchmarkTest` with portable `JAVA_TOOL_OPTIONS`.
- `real-chunk-meshing-replay`: launches the existing development client replay runner and times the production section mesher against deterministic in-game fixture sections.

Common options:

- `--artifact-dir PATH`
- `--timeout-seconds N`
- `--rust-profile release`
- `--jvm-arg VALUE`
- `--gradle-arg VALUE`
- `--dry-run`
- `--rebuild-current-native`
- `--native-rebuild-timeout-seconds N`
- `--diagnostic`
- `--benchmark-force-mode clean-test|none|rerun-tasks`

Benchmark options:

- `--quads N`
- `--warmup N`
- `--iterations N`
- `--warmup-ms N`
- `--measure-ms N`
- `--fork-index N`
- `--forks N`
- `--alternate-order` / `--no-alternate-order`

Real replay options:

- `--real-warmup N`
- `--real-measure N`
- `--real-warmup-seconds N`
- `--real-measure-seconds N`
- `--real-rebuilds-per-sample N`
- `--real-fixture NAME`

`chunk-meshing-hotpath` defaults to `--benchmark-force-mode clean-test`. That runs:

```text
gradlew cleanTest test --tests net.sodium.client.perf.ChunkMeshingHotPathBenchmarkTest --no-build-cache ...
```

This forces the selected benchmark test to execute without globally rerunning compile, native build, and unrelated Gradle tasks. `--no-build-cache` prevents a cached `test` result from skipping the benchmark while still allowing normal up-to-date checks for compile tasks. Use `--benchmark-force-mode none` only when you intentionally allow Gradle to skip an up-to-date benchmark test, and use `--benchmark-force-mode rerun-tasks` only to reproduce the older heavy Bash behavior.

For cross-repository timing, prefer multiple forks:

```powershell
python .\DevUtils\PerfTesting\rust_migration_harness.py --target both --workload chunk-meshing-hotpath --forks 3
python .\DevUtils\PerfTesting\rust_migration_harness.py --target both --workload real-chunk-meshing-replay --forks 3 --real-warmup 8 --real-measure 25
```

When both targets are selected, the harness alternates execution order by fork by default. Odd forks run current then frozen; even forks run frozen then current. This reduces bias from first-run JVM, OS cache, and CPU-state effects while keeping each target's benchmark output separate. The root artifact directory receives:

- `combined_manifest.json`: commands, metadata, per-target status, and links to aggregate files.
- `aggregate_summary.json`: machine-readable row classifications, medians, ratios, fallback contamination, and work-equivalence checks.
- `aggregate_report.md`: compact human-readable comparison table.
- `diagnostic_summary.json`: per-fork current/frozen work signatures, raw checksums, stage timings, native profile counters, and explicit equivalence limitations.

Diagnostic benchmark output includes `semantic_fingerprint` for clean full-section replay rows. The raw vertex hash is computed from the actual packed vertex bytes produced by each implementation. The raw index hash is derived from the shared generated quad-index pattern used by these rows. Ordered, canonical, normalized, translucent, and per-pass semantic hashes are encoded from deterministic replay fixture quad records using SHA-256 and explicit little-endian field encoding. The canonical sort key is:

```text
render_pass|block_position|source_type|face|sprite_identity|material_flags|exact_vertex_bits
```

Use `--rebuild-current-native` before an authoritative run when native identity matters. The harness runs `buildRustNative --rerun-tasks` once before measured benchmark forks, writes `native-rebuild/native_rebuild.json`, records the produced native library SHA-256/size/timestamp in `combined_manifest.json`, and then reuses that artifact for the benchmark.

Use `--diagnostic` for a separate validation pass. It enables the benchmark diagnostic property and Rust native substage profile environment variables for the current repo:

```text
MATTMC_PROFILE_STATIC_MODEL_SUBSTAGES=true
MATTMC_PROFILE_FLUID_SUBSTAGES=true
MATTMC_PROFILE_SCAN_SUBSTAGES=true
MATTMC_PROFILE_STAGING_SUBSTAGES=true
```

Do not use diagnostic-mode timing as headline performance data; run a clean non-diagnostic pass for the authoritative comparison.

Artifacts are written under:

```text
build/perf/rust-migration-harness/<timestamp>/
```

Each target gets its own subdirectory containing:

- `metadata.json`
- `stdout.log` and `stderr.log` for executed workloads
- workload output JSON when the workload produces one

The root artifact directory contains `combined_manifest.json`.

## Real Replay Corpus Helper

The current live replay runner can also be launched from the normal capture wrapper:

```powershell
python .\DevUtils\RunDevCapture.py --backend opengl --shaders off --capture-meshing-corpus --meshing-corpus-warmup 2 --meshing-corpus-measure 3
```

That produces the existing `RealChunkMeshingReplayRunner` JSON in the capture artifact directory. Convert and validate that replay output with:

```powershell
python .\DevUtils\PerfTesting\meshing_corpus.py from-real-replay --input .\build\path\real_meshing_replay.json --output .\build\path\real_meshing_corpus.mmcm --summary .\build\path\real_meshing_corpus.summary.json
python .\DevUtils\PerfTesting\meshing_corpus.py validate --input .\build\path\real_meshing_corpus.mmcm
```

Milestone corpus helpers are first-class commands:

```powershell
python .\DevUtils\PerfTesting\meshing_corpus.py capture --output .\build\meshing-corpus\milestone2.mmcm --summary .\build\meshing-corpus\milestone2_summary.json
python .\DevUtils\PerfTesting\meshing_corpus.py inspect --input .\build\meshing-corpus\milestone2.mmcm --category fluid-heavy
python .\DevUtils\PerfTesting\meshing_corpus.py replay-current --input .\build\meshing-corpus\milestone2.mmcm --output .\build\meshing-corpus\current_replay.json
python .\DevUtils\PerfTesting\meshing_corpus.py replay-frozen --repo C:\repos\MattMC_JavaPerfTesting\MattMC --input .\build\meshing-corpus\milestone2.mmcm --output .\build\meshing-corpus\frozen_replay.json
python .\DevUtils\PerfTesting\meshing_corpus.py compare --corpus .\build\meshing-corpus\milestone2.mmcm --current .\build\meshing-corpus\current_replay.json --frozen .\build\meshing-corpus\frozen_replay.json --output .\build\meshing-corpus\compare.json
```

Use `--fixture NAME` for a single fixture or omit it to process the current corpus command's full fixture set. Use `inspect --category NAME` and `--replayable-only` to filter corpus metadata before replay. Replay commands pass corpus paths, output paths, warmup/measurement counts, and fixture filters as Gradle properties so Gradle can track the `.mmcm`, replay adapter, model bundle, semantic configuration, and current native library identity instead of relying on `cleanTest`.

Headless corpus replay outputs report cold first-use timing separately from warm median timing. The timed warm path prepares corpus/model input once, warms the selected adapter, resets per-section output for each invocation, and hashes outside the measured region. Semantic comparison artifacts include raw vertex/index hashes, ordered/canonical/normalized semantic hashes, translucent metadata hash, pass counts, and a bounded mismatch summary. The current semantic hashes are intentionally conservative: byte-identical fixtures are proven strongly, while future milestones should replace byte-derived semantic hashes with fully decoded renderer-independent vertex records.

## Future World Storage/NBT Workloads

The harness is ready to accept future workload adapters for deterministic file fixtures without changing the process/metadata contract. Useful future fixture types include:

- `.mca` region files
- raw compressed chunk payloads
- `.nbt` files
- generated deterministic NBT structures
- malformed or corrupt fixtures
- temporary output directories for write-parity checks

Do not implement world-storage or NBT migration behavior inside this harness. Add only workload adapters that call dedicated tests or benchmarks.

## Tests

Run harness self-tests:

```powershell
python .\DevUtils\PerfTesting\test_rust_migration_harness.py
```

These tests use temporary fake repositories and do not depend on machine-specific paths.

## Linux Status

The Python harness uses `pathlib`, list-based subprocess calls, POSIX process groups, and the existing `gradlew` wrapper on Linux. Existing Bash entry points are preserved. Linux runtime execution still needs to be verified on the Linux desktop after these Windows-side changes land.
