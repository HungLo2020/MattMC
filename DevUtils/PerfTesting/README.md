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

Common options:

- `--artifact-dir PATH`
- `--timeout-seconds N`
- `--rust-profile release`
- `--jvm-arg VALUE`
- `--gradle-arg VALUE`
- `--dry-run`
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

`chunk-meshing-hotpath` defaults to `--benchmark-force-mode clean-test`. That runs:

```text
gradlew cleanTest test --tests net.sodium.client.perf.ChunkMeshingHotPathBenchmarkTest --no-build-cache ...
```

This forces the selected benchmark test to execute without globally rerunning compile, native build, and unrelated Gradle tasks. `--no-build-cache` prevents a cached `test` result from skipping the benchmark while still allowing normal up-to-date checks for compile tasks. Use `--benchmark-force-mode none` only when you intentionally allow Gradle to skip an up-to-date benchmark test, and use `--benchmark-force-mode rerun-tasks` only to reproduce the older heavy Bash behavior.

For cross-repository timing, prefer multiple forks:

```powershell
python .\DevUtils\PerfTesting\rust_migration_harness.py --target both --workload chunk-meshing-hotpath --forks 3
```

When both targets are selected, the harness alternates execution order by fork by default. Odd forks run current then frozen; even forks run frozen then current. This reduces bias from first-run JVM, OS cache, and CPU-state effects while keeping each target's benchmark output separate. The root artifact directory receives:

- `combined_manifest.json`: commands, metadata, per-target status, and links to aggregate files.
- `aggregate_summary.json`: machine-readable row classifications, medians, ratios, fallback contamination, and work-equivalence checks.
- `aggregate_report.md`: compact human-readable comparison table.

Artifacts are written under:

```text
build/perf/rust-migration-harness/<timestamp>/
```

Each target gets its own subdirectory containing:

- `metadata.json`
- `stdout.log` and `stderr.log` for executed workloads
- workload output JSON when the workload produces one

The root artifact directory contains `combined_manifest.json`.

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
