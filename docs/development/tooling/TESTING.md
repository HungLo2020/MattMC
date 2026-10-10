# Running tests quickly

Pick the narrowest command that covers your change. Full-suite runs are for
milestones and the [validation driver](../rendering/RENDER-VERIFICATION.md#one-command-validation).

| Goal | Command |
| --- | --- |
| One Rust test while editing | `(cd src/main/rust && cargo test --locked --lib <name>)` |
| Whole Rust suite | `(cd src/main/rust && cargo test --locked --profile suite --lib -- --test-threads=4)` |
| One Java class | `./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeHeightmapTest'` |
| Ordinary Java suite | `./gradlew -PmattmcRustProfile=release test -x testRustNative` |
| Large parity workloads | `./gradlew -PmattmcRustProfile=release parityTest -x testRustNative` |
| Everything | `./gradlew -PmattmcRustProfile=release check` |

## Rust profiles

- `dev` keeps this crate at `opt-level = 0` for fast incremental compiles.
  Third-party crates (including shaderc's C++) are optimized once.
- `suite` inherits `dev`, keeping debug assertions and overflow checks, but
  compiles this crate at `opt-level = 1`. CPU-heavy shader-pack and
  world-render tests run 5–6× faster. It builds into `target/suite`, so
  switching between `dev` and `suite` never invalidates either cache.
  `testRustNative` and the validation driver use it.

See [Cargo.toml](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/Cargo.toml).

## Java test tasks

- `test` skips classes tagged `@Tag("parity")` and runs in parallel JVMs.
- `parityTest` runs only the tagged classes, also in parallel JVMs.
- `check` runs both.
- An explicit `--tests` filter on `test` also selects tagged classes, so
  per-slice commands in subsystem docs keep working.
- `-PmattmcTestForks=N` sets the fork count (default: up to 3, one per four
  cores). Each fork is a 2 GB JVM; lower it on a machine short of memory.

Tag a class `parity` when it runs a large Frozen-vs-Rust workload (roughly
10 s or more). Keep fast parity checks untagged so ordinary runs still cover
each migrated system.

`testRustNative`, which every Java test task depends on, writes
`build/rust/testRustNative.stamp` after a passing run. Gradle skips it while
the crate, `src/main/resources` and `DevUtils/tests/rendering/fixtures` are
unchanged. Force a rerun with `--rerun-tasks` (for example after a GPU driver
update).

## Typical timings

Measured on October 9, 2026 on a 12-core laptop (matt-alienwarem15r3), warm caches:

| Step | Before | After |
| --- | --- | --- |
| `./gradlew test` (Java, release native) | 23m50s, one JVM | 1m08s (`test`) + 2m52s (`parityTest`) |
| Rust suite, four threads | 311s run (dev) | 180s run (`suite`) |
| `testRustNative`, unchanged inputs | ~5½ min, always reran | 2s, up to date |
| `RunWiki.py check` | 31s | 10s |

First builds of a new profile are one-time costs: about 6 minutes to optimize
dependencies and 7–9 minutes for each `suite` target directory. A full
`suite` rebuild of this crate takes about 3m20s, against 1m40s for `dev`.

## Writing fast parity tests

- Spend the time on cases, not on comparing them. Compare primitive arrays
  or `ByteBuffer`s, and build descriptive strings only when a check fails.
- Keep generated case sizes bounded where extra size adds no new code path.
  For example, [the biome search test](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/net/minecraft/world/level/levelgen/NativeBiomeSearchTest.java)
  caps closest searches at 64 rings.
- The Frozen Java reference is usually the slow side. Profile with
  `jcmd <pid> Thread.print` samples or JFR before cutting coverage.

## Troubleshooting

- *No tests found for given includes*: check the class name; tagged classes
  are still found through `--tests`.
- A failure that appears only with several forks usually means the test
  depends on state another class set up in the same JVM. Make the class set
  up its own fixtures; run with `-PmattmcTestForks=1` to confirm.
