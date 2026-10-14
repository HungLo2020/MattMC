# Running tests quickly

Pick the narrowest command that covers your change. While iterating, run the
tests for what you changed. Run the full suites (`check`) for milestones,
cross-cutting changes and the
[validation driver](../rendering/RENDER-VERIFICATION.md#one-command-validation).
Profile and timing notes further down describe their recorded configurations.

| Goal | Command |
| --- | --- |
| Tests for the uncommitted change | `python3 DevUtils/tests/tooling/RunAffectedTests.py` |
| One Rust test or module while editing | `(cd src/main/rust && cargo test --locked --lib <name-or-module>)` |
| Whole Rust suite | `(cd src/main/rust && cargo test --locked --profile suite --lib -- --test-threads=4)` |
| One Java class | `./gradlew -PmattmcRustProfile=release test --tests '*NativeHeightmapTest'` |
| One Java package | `./gradlew -PmattmcRustProfile=release test --tests 'net.minecraft.world.level.chunk.*'` |
| Ordinary Java suite | `./gradlew -PmattmcRustProfile=release test -x testRustNative` |
| Large parity workloads | `./gradlew -PmattmcRustProfile=release parityTest -x testRustNative` |
| Exhaustive and corpus cases | `./gradlew -PmattmcRustProfile=release slowTest -x testRustNative` |
| All Java tiers and Rust checks | `./gradlew -PmattmcRustProfile=release check` |

Commands with `-x testRustNative` assume the intended Rust checks were completed
separately. A `--tests`-filtered Java run skips `testRustNative` on its own.
`check` runs all three Java tiers and their native check dependency. It does
not run the separate `performanceTest` task or the client lifecycle, Frozen
image and ordinary-gameplay protocols.

## Affected tests

[`RunAffectedTests.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/tooling/RunAffectedTests.py)
reads `git diff --name-only <base>` plus untracked files (default base `HEAD`)
and runs:

- the `cargo test` modules of the changed `.rs` files (`mod.rs` and `tests.rs`
  select their directory's module), in the `suite` profile with four threads.
- the Java packages of the changed classes, plus the packages that mirror a
  changed Rust module's path (Rust `world/level/chunk` selects
  `net.minecraft.world.level.chunk.*`), through a filtered `test`.

Changes to `lib.rs`, `Cargo.toml`, `Cargo.lock` or `build.rs` select the whole
Rust suite. Changes to Gradle build files select the whole ordinary Java suite.
Flags: `--dry-run` prints the selection, `--files PATH...` selects from given
paths instead of git, `--base origin/master` covers
unpushed commits, `--java-only`/`--rust-only` limit the run, and
`--include-slow` adds slow cases. It doesn't run callers in other packages, so
run `check` before publishing a change whose effects cross packages.

## Rust profiles

- `dev` keeps this crate at `opt-level = 0` for fast incremental compiles.
  Third-party crates (including shaderc's C++) are optimized once.
- `suite` inherits `dev`, keeping debug assertions and overflow checks, but
  compiles this crate at `opt-level = 1`. The author reports 5–6× faster
  CPU-heavy shader-pack and world-render tests in the measured workload.
  It uses the `suite` subdirectory of the selected Cargo target directory;
  switching profiles preserves their separate cached outputs.
  `testRustNative` and the validation driver use it.

See [Cargo.toml](https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/rust/Cargo.toml).

## Java test tasks

The tiers are selected by JUnit tag:

- `test` skips `@Tag("parity")` classes and `@Tag("slow")` cases, and runs in parallel JVMs.
- `parityTest` runs the parity-tagged classes without their slow cases.
- `slowTest` runs only slow-tagged cases. Most are single exhaustive or
  corpus-sized methods inside parity classes.
- `check` runs all three. The rendering validation driver runs `test` and
  `parityTest` when `--all-java-tests` is selected; run `slowTest` alongside it
  for the complete Java suite.
- A `--tests` filter applies to the task named just before it on the command
  line, so in `test parityTest --tests X` only `parityTest` is filtered and
  `test` runs everything it normally does. A filtered task also selects matching
  parity classes, so per-slice commands in subsystem docs keep working, but it
  still skips slow cases unless you pass `-PmattmcSlowTests=true`. The existing
  `*PerformanceTest`/`*Benchmark` class exclusions still apply to `test`.
- `-PmattmcTestForks=N` sets the fork count (default: up to 3, one per four
  cores). Each fork has a maximum 2 GB Java heap; lower the fork count on a
  machine short of memory.

Collect results from `build/test-results/test/`, `parityTest/` and `slowTest/`;
no single task is the full Java suite.

Tag a class `parity` when it runs a large Frozen-vs-Rust workload (roughly
10 s or more). Keep fast parity checks untagged so ordinary runs still cover
each migrated system. Tag a single method `slow` when it takes about 20 s or
more because it enumerates a state space or a large corpus. Keep at least one
faster test of the same behavior outside the slow tier.

`testRustNative`, which every unfiltered Java test task depends on, runs the
whole Rust suite serially (`--test-threads=1`, for the process-global
OpenAL/EGL/Vulkan tests) and writes
`build/rust/testRustNative.stamp` after a passing run. Gradle skips it while
the crate, `src/main/resources` and `DevUtils/tests/rendering/fixtures` are
unchanged and the recorded output remains up to date. Reusing the stamp is not
a fresh test run and does not verify changes outside the declared inputs.
Force a rerun with `--rerun-tasks` (for example after a GPU driver update).

## Typical timings

The implementation author reports these October 9, 2026 measurements on a
12-core laptop (matt-alienwarem15r3), with warm caches. The
[publication record](https://github.com/HungLo2020/MattMC/commit/f86206767dadde696adfed4e04c5ee97cd0d0885)
reports the combined-tree wiki and Rust suite passing (2,443 Rust passes),
while its Java runs were still in progress at publication. The earlier timings
below do not establish a complete Java pass for that combined tree. This review
did not rerun the suites or inspect original timing receipts.

The changes combine task splitting, parallel forks, compiler profiles and
fixture work reductions, including the biome search ring cap. Treat the
numbers as those measured configurations, not an isolated optimization or
renderer/gameplay speedup:

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

## Memory-protected runs

On a machine shared with other applications, timing runs swap and become
unreliable. [`RunProtected.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/tooling/RunProtected.py)
runs a command in a systemd user scope inside `mattmctest.slice` with
`MemoryLow` (default 8G): under pressure the kernel reclaims other
applications before the test. It reports the machine's swap-in rate and memory
stall during the run and warns when timings are unreliable:

```sh
python3 DevUtils/tests/tooling/RunProtected.py -- python3 DevUtils/PerfAudit/Gameplay.py ...
python3 DevUtils/tests/tooling/RunProtected.py --fail-on-swap --report build/pressure.json -- ./gradlew test
```

Protection is capped by every ancestor cgroup, so each machine needs a one-time
root setup (Linux with cgroup v2 and systemd). Only do this on a single-user
desktop or laptop; on a server it would favor your login session over system
services:

```sh
sudo systemctl set-property user.slice MemoryLow=10G
sudo systemctl set-property user-1000.slice MemoryLow=10G
sudo systemctl set-property user@1000.service MemoryLow=10G
```

Without it the tool runs unprotected and says so. A larger zstd zram also keeps
evicted pages in compressed RAM instead of disk swap, for example
`/etc/systemd/zram-generator.conf` with `[zram0]`, `zram-size = min(ram, 16384)`
and `compression-algorithm = zstd`, plus `vm.swappiness = 150` and
`vm.page-cluster = 0` in `/etc/sysctl.d/`; it takes effect after a reboot.


## Composter transfer regression

Run `./gradlew test --tests '*ComposterHopperTransferTest'` for the real Hopper
transaction with a controlled world boundary, rollback, manual harvesting,
input and maturation controls. The temporary ready-output container delays its
world reset until the Hopper explicitly commits a successful extraction; a
restored nonempty slot is not a commit.

For live server and persistence acceptance, compile the intended runtime first,
then run [VerifyComposterTransfers.py](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/server/VerifyComposterTransfers.py):

```sh
python3 DevUtils/tests/server/VerifyComposterTransfers.py \
  --java /path/to/jdk/bin/java --classpath-file /path/to/main-classpath.txt \
  --native-dir build/rust/native --output /new/disposable/output
```

The classpath file must contain the compiled main runtime classpath, including
its dependencies and resources, using the platform path separator. The driver
does not build classes. It starts the real dedicated server on loopback, checks
actual game-time advancement and inventory/block state, then saves and restarts
the disposable world. The output directory must be new; it retains commands,
logs, binary hashes and machine-readable results. No existing save is opened.
Use `--port` for a different free loopback port. Manual player harvesting is
covered by the Java fixture, not simulated by console commands.

`--class-overlay ORIGINAL_CLASSES --expect-original-loss` is only a baseline
probe for [#796](https://github.com/HungLo2020/MattMC/issues/796); its expected
loss result never establishes candidate acceptance. Normal mode must pass all
preservation and exact-once checks. Compare the recorded code/native hashes
and identical driver versions when reporting before/after results.

## Troubleshooting

- *No tests found for given includes*: check the class name and which task the
  `--tests` follows. Parity classes are found through `--tests`. A class whose
  only tests are slow-tagged needs `-PmattmcSlowTests=true` or `slowTest`.
- A failure that appears only with several forks usually means the test
  depends on state another class set up in the same JVM. Make the class set
  up its own fixtures; run with `-PmattmcTestForks=1` to confirm.
