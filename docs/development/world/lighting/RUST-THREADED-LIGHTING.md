# Threaded lighting coordination

Rust owns the threaded server light engine's task membership, PRE/POST tags,
1,000-entry batch boundary, live pass cursors and scheduled admission. See
[`task_batch`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/task_batch)
and [`NativeLightTasks`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/NativeLightTasks.java).
The existing native [priority dispatcher](../chunk-loading/RUST-CHUNK-TASKS.md)
feeds this coordinator; the existing [propagation owners](RUST-LIGHT-WORK.md)
consume the resulting work. These are distinct stages, with no copied task list.

Java retains callable references, chunks, futures, executors and effectful light
methods. The [S0026 coordinator](RUST-LIGHT-COORDINATOR.md) moves stock node,
section, publication and propagation decisions into native owners.

**S0026/r4 development:** exact stock `checkBlock`, retention and queued imports enter native
task rows with retained actual block/sky engine bindings. PRE inserts into the
real node sets; POST retires the original engine/position capture roots. Custom
positions, derived engines and named-debug tasks retain callable dispatch.
Typed imports keep their original section/source captures and bind the current
primary entry only at PRE, under the selected storage's original VM monitor.
The connected typed check/import/retention/status/enable/source pair matches
portable behavior, callback/error/reentry and weak-lifetime references, plus the
full engine/threaded observers. Java ordinary/parity/slow tiers pass for the
guarded Java31/native `fc1e557a` candidate. The connected native level update consumer is verified separately on
Java33/native `7d49c5a0`: the invocation cursor holds the actual level through
PRE/update/POST, and genuine context/publication effects return to Java. The
old propagation runnable remains the fallback for ordinary callable owners.
All Java tiers and nine omission controls pass; the full Rust run retains its
57 known rendering-host failures. All six actual server fresh/reopen runs pass;
the adapted actual-consumer audit retains baseline equivalence and original
coverage thresholds. New cost acceptance remains pending; historical verification below does not certify performance.

## Contracts when editing

- A pass captures only its maximum count. It reads live membership while
  executing every PRE effect, propagation, then POST effects and removals.
  Appends and nested passes must remain visible at the original cursor positions.
- PRE or propagation failure retains the batch. POST failure retains the current
  entry and the unfinished suffix. A nested POST pass can remove the outer
  entry before its removal; the original outer `IndexOutOfBoundsException`
  remains observable. Do not detach a batch or remove entries before callbacks.
- Scheduled admission resets only after normal execution. Submission rejection
  or a throwing scheduled runnable leaves it true. Do not add a `finally` reset.
- Each native call ends before Java effects. There is no native queue borrow or
  lock across a callback. Compound changes and retired-row consumption retain
  the original consecutive-executor exclusion; this adds no general concurrent
  mutation contract. Pending and scheduled projections are atomic.
- Rust allocates reusable callable rows. Java clears only retired rows, before
  the next effect. A running callback has its own strong local reference even
  when a nested pass removes its row. Queued engine closures retain the owner;
  the automatic CPU arena releases it after it becomes unreachable.
  `ThreadedLevelLightEngine.close()` remains a no-op.
- Typed commands retain their original VM capture roots through POST, even
  though PRE needs no Java runnable. Holding the task owner after retirement
  must not keep the retired engine or position alive. The separate
  `VerifyCoordinatorTaskLifetimes.java` observer in
  `DevUtils/tests/lighting/coordinator/` checks these weak-reference boundaries
  through the actual public producer and original controlled transport.
- Import binding can fail. Keep its adoption after acquiring the original
  queued-section monitor at PRE; preparing it during transport changes error
  timing and loses the original pending-task retry. A missing sky engine skips
  this binding while retaining the original argument captures until POST.

## Verification

The capture/timing runners currently target Linux x64. Use JDK25 and a native
release build. Build before running the capture or timing
drivers; keep other builds, tests and servers outside timing windows.

```sh
./gradlew -PmattmcRustProfile=release classes testClasses -x testRustNative
./gradlew -PmattmcRustProfile=release parityTest -x testRustNative \
  --tests net.minecraft.server.level.NativeLightTasksTest
python3 DevUtils/tests/lighting/threaded/VerifyThreadedLighting.py \
  --java-home "$JAVA_HOME" --frozen-classpath-file /path/frozen-classpath.txt \
  --current-classpath-file /path/current-classpath.txt --control-ref <pre-change-commit> \
  --output build/threaded-lighting/oracle-new
python3 DevUtils/tests/lighting/threaded/BenchmarkThreadedLighting.py \
  --java-home "$JAVA_HOME" --frozen-classpath-file /path/frozen-classpath.txt \
  --current-classpath-file /path/current-classpath.txt --control-ref <pre-change-commit> \
  --output build/threaded-lighting/timing-new
python3 DevUtils/RunWiki.py check
```

The portable observer executes the actual threaded engine and chunk dispatcher
on unchanged Frozen, separately compiled pre-change sources and Current. Its
world/executors are controlled inputs. It compares complete block/sky layers,
publication sharing/immutability, notifications, pending work, futures and
failure/reentry traces. Baseline Frozen is pinned to `7a4d1817`; source,
native-image, driver and compiled-class seals must remain intact.
Initialization futures complete before the lighting stage is submitted. The
fixture then requires every column to be enabled and its known emitter to read
15. Queuing both stages together lets initialization POST disable a column after
lighting PRE enables it; matching outputs from that sequence cannot certify an
active lighting workload.

After integrating other source changes, an actual preserved pre-change image can
be supplied with `--control-classpath-file`, `--control-native-dir` and
`--control-image-manifest`. The manifest checks every original main class through
the supplied classpath and pins its native library. Preserve only classes that
would be overwritten, rather than copying a build tree. Without these options,
the driver compiles the original coordinator source over the current downstream
owners; keep that causal control distinct from a preserved full pre-change image.

`MutateThreadedLighting.py` accepts the same classpath/control arguments plus
`--oracle <completed-oracle-directory>`. It compiles temporary copies of this
standalone Rust owner and binds a temporary callable adapter to those exports.
The unchanged copy must reproduce the real release/Frozen trace before the six
semantic faults count. Production sources and the full release library remain
unchanged; an early crash or compilation failure is not a detected mutation.

`VerifyThreadedLightingServer.py` uses the same arguments and starts real
dedicated servers for initial load and save/reopen on all three sides. It
reuses the player-loading lifecycle audit and adds coordinator nonvacuity
counters without retaining engines. These agents are test-only observations,
not performance instrumentation or a network client.

The timing driver includes public producers, dispatch, callbacks and actual
propagation. Construction/first initialization are separate from steady state.
Fixture construction, engine construction within it, and first initialization
are separate cold measurements. Native capacity reports only the owner's
dynamic buffers; process RSS is recorded separately. The driver retains every
sample, marks measured compilation, and requires at least
eight quiet samples per JVM. Warmup includes the complete untimed observation
and resource reporting path. Check `result.json`, including the general
10% verdict; an attributable-boundary allowance requires separate evidence.
CPU fixtures do not establish client rendering, FPS or sustained gameplay
acceptance. Preserve the tested identities, exceptions and reconciliation
boundaries below when assessing this subsystem checkpoint.

The corrected active fixture on integrated base `646986043` with native image
`49621b32` matches Frozen and the preserved full pre-change image for 50,144
observations. Six deliberate semantic faults are detected after the unchanged
isolated owner reproduces the real release trace. The ordinary tier runs 2,197
tests: 2,191 pass and six skip; all 193 parity and 11 slow tests pass. The full
Rust run is **not green**: 2,830 pass, 57 fail and nine are ignored; its failure
names exactly match this host's previous rendering failures. Keep those limits
and the later reconciliation receipt distinct from these tested identities.

After integrating End changes at `522b1f8b`, the 37 lighting/task class images and
their source interfaces remain unchanged. The rebuilt native image `89d380ed`
matches the same 50,144 active observations; 29 focused lighting/generation tests
and seven coordinator tests pass. Fresh candidate server load/reopen checks pass
22/8 checks with actual native coordinator execution. Unchanged Frozen and
preserved pre-change server references retain their original identities. The
sealed timing, mutation and full-tier evidence above is reused under
[policy 210](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18957393)
and [decision 213](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18958334);
it is not relabeled as a full suite or timing run on `89d380ed`.

## Transitional costs recorded on October 15

These initial measurements use base `dc84c628f` and native image `047bcc14`.
They remain separate from corrected active-world acceptance and later integrated
images. All times include the stated fixture/controller boundary.

| Measured case | Frozen / pre-change / candidate | Decision |
|---|---|---|
| Empty polling cycle | 8.55–8.72 / 12.52–12.89 / 13.30–13.66 ns | [207](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18956933): paired 1.555–1.568× Frozen; 0.45–0.98 ns worse than pre |
| First engine construction | medians 6.005 / 54.352 / 56.801 ms | [207](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18956933): 1.625–3.458 ms worse than pre in paired runs |
| Disabled-column single edit/drain | medians 17.842 / 23.461 / 28.427 µs | [208](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18957104): paired 1.578–1.658× Frozen; 4.33–6.22 µs worse than pre |

These are bounded ownership exceptions, not performance-budget passes. No
downcall executes in the empty case; the whole gap is not FFM overhead. Keep
constructor stages separate from complete initialization. The initial fixture
overlapped initialization and lighting, leaving columns disabled; its timings
and the underwarmed 64-edit samples are retained as disabled/inconclusive evidence.
They do not establish active-world acceptance. The exit path is connected native
node/section/work/publication and world-creation ownership, requiring a separate
assignment and new measurements. No broader exception or successful exit is implied.

Corrected active campaigns measure the following candidate/Frozen ratios over
three alternating JVM rounds per side, including producers, actual dispatch,
callbacks and propagation. Work checksums and source/class/native seals match.
The pre-change side restores every original main class and uses native
`9a8f6def`, rather than a mixture of old coordinator and new component classes.

| Active inputs per cycle | Three candidate/Frozen time ratios | General 10% allowance |
|---|---|---|
| Empty | 1.546 / 1.532 / 1.583 | Fails |
| Single edit | 1.031 / 1.166 / 1.034 | One round fails |
| 64 edits | 0.680 / 0.678 / 0.675 | Passes |
| 1,000 | 0.656 / 0.695 / 0.695 | Passes |
| 2,501 | 0.683 / 0.716 / 0.711 | Passes |

[Decision 213](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18958334)
accepts the exact remaining active single-edit debt: medians Frozen/pre/current
45.137/47.269/46.747 µs, retaining the failed 1.166× Frozen and 1.114× pre round.
No separate boundary attribution supports calling that round a 25% budget pass.
It also extends the named empty medians to 8.700/12.634/13.495 ns and the first
engine-construction medians to 6.116/54.774/57.903 ms over 15 JVMs per side.
Every paired cold increment, from -3.823 to 8.463 ms, remains in the receipts.
These approved costs are not an aggregate performance pass or a claim that the
unassigned connected-ownership exit path has been demonstrated.

All measured JVMs report zero swap. Host swap-in increased five pages (about
20 KiB) during the remaining campaign; swap-out stayed unchanged. Preserve this
caveat alongside raw compiler, cold, Java allocation, native buffer-capacity and
process-RSS observations. These are controlled headless subsystem workloads,
not a rendered client, long-session memory or whole-game speed result.

## Troubleshooting

- A future finishes early: check POST ordering relative to actual propagation.
- An original failure disappears: check live cursors and the removal point.
- Pins grow after drain: inspect native retired rows before changing GC policy.
- An apparently empty engine stays scheduled after failure: compare the original
  rejection/exception prefix before attempting recovery.
