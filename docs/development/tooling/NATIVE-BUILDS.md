# Native builds and running clients

Build the native library from the repository root:

```sh
./gradlew buildRustNative -PmattmcRustProfile=release
```

Gradle stages the platform library under `build/rust/native/`. Java loads it
through `mattmc.rust.natives.dir`; Cargo outputs remain under
`build/rust/target/`. See
[the build task](https://github.com/HungLo2020/MattMC/blob/master/build.gradle)
for platform names and build inputs.

## Launch Current or Frozen

From the current repository root:

```sh
python3 DevUtils/RunDev.py          # Current, release native profile
python3 DevUtils/RunDev.py --frozen # Frozen Java/OpenGL checkout
```

[The launcher at `87046367`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/DevUtils/RunDev.py)
finds the Current root relative to its own script, then starts Gradle with the
selected checkout as its working directory. `--frozen` resolves `java_perf_repo`
through Current's `DevUtils/Common/platform/directory/directories.json` and the
detected platform or `--platform` override. Linux and Windows have configured paths; macOS currently
has none. `--frozen-repo /absolute/path/to/Frozen` selects an explicit checkout
without also requiring `--frozen`; relative explicit paths resolve from the
caller's working directory. Prefer an absolute path when launching elsewhere.

Frozen must be a separate directory with a `.git/` directory and a Gradle
wrapper; a linked worktree's `.git` file is rejected. The launcher does not
check branch, origin, cleanliness, exact reference commit, compiled outputs or
input readiness. Prepare a missing checkout with
`python3 DevUtils/ProvisionFrozenBaseline.py`; see the provisioner's distinct
[preparation and mutation behavior](../rendering/RENDER-VERIFICATION.md#2-frozen-image-comparison).
The launch still needs that checkout's build/JDK dependencies and a working
graphics session. On Linux, the launcher aborts on a detected NVIDIA
kernel/user-space driver mismatch before starting either client.

The Frozen command is `runClient -x test`, without `clean` or Current's native
release-profile flag. It inherits the caller's environment; it does not force
OpenGL or rewrite source files itself. Current's launch defaults
`MATTMC_RUST_VULKAN_GPU_TIMESTAMPS` to `true` only when unset; Frozen does not add
that default. Frozen's [Gradle task at `7a4d1817`](https://github.com/HungLo2020/MattMC/blob/7a4d181717dc0c7da9086f1a17687dfd522ce417/build.gradle#L622-L679)
builds/copies required outputs and defaults the game's working directory to its
own `run/` (`mattmcRunGameDir` can override it). Normal launch/gameplay can write
build caches, shader-pack copies, settings and saves. This is not a read-only
Frozen invocation or a check that its source identity stayed unchanged.

[The reviewed Frozen options at `7a4d1817`](https://github.com/HungLo2020/MattMC/blob/7a4d181717dc0c7da9086f1a17687dfd522ce417/src/main/java/net/minecraft/client/Options.java#L207-L215)
default to OpenGL, but persisted settings can select another backend; verify
OpenGL for the performance reference. Its older `DevUtils/RunDev.sh`
requests `clean runClient`; Frozen's `runClient` depends on `test` even without
`clean`. The implementation author reports Java 25/Byte Buddy mocking failures
blocking that route. Skipping `test` avoids that dependency; it neither verifies
the suite nor guarantees a successful launch. This documentation review
inspected source without launching a client. To launch directly inside Frozen:

```sh
./gradlew runClient -x test
```

## Preserve loaded libraries

The build task copies the Cargo output to a unique temporary file beside the
destination, then atomically replaces the destination. An existing client keeps
its original file mapping; subsequent clients see the complete new library.
Temporary staging files are removed on success or failure. If the filesystem
cannot perform an atomic replacement, or a platform locks the loaded library,
the build fails instead of falling back to overwriting its bytes.

Never manually copy over a library used by a running client. The old staging
task changed bytes in existing mappings; a concurrent Oct 4 profiling build
also coincided with SIGBUS in that library's mapped executable range. The
isolated regression fails before the staging fix and passes after it.

Atomic publication does not make concurrent performance experiments equivalent.
Serialize builds and benchmark sessions that share classes and native paths, or
give each session its own complete build and runtime directory. Record the
library actually loaded by each client. A changing shared path invalidates a
benchmark's binary-identity check even when the client retains its old mapping.
See [render verification](../rendering/RENDER-VERIFICATION.md) for capture rules.

## Check staging without Cargo or gameplay

On Linux or macOS:

```sh
python3 DevUtils/tests/tooling/VerifyNativeLibraryStaging.py
```

The driver executes the real Gradle staging task in an isolated temporary build
directory, replacing Cargo with deterministic payloads. It checks that existing
read-only mappings retain their bytes across larger and smaller replacements,
new readers receive the replacement, and no staging files leak. It does not
touch the normal native library, compile Rust, or launch a client. Logs and the
result are stored under `artifacts/native-library-staging/`; use
`--artifact-root PATH` to choose another ignored evidence directory.
