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

`--frozen` resolves `java_perf_repo` from
`DevUtils/Common/platform/directory/directories.json`. Use
`--frozen-repo /path/to/Frozen` to launch another full clone. Frozen uses its own
`run/` directory for settings and saves; its default backend is OpenGL. Keep
that backend selected when using it as the performance reference.

Prepare a missing checkout with `python3 DevUtils/ProvisionFrozenBaseline.py`;
see [Frozen preparation](../rendering/RENDER-VERIFICATION.md#2-frozen-image-comparison).
The Frozen launcher runs `runClient -x test`, without `clean`. Frozen's old
`DevUtils/RunDev.sh` runs `clean runClient`, which triggers its test dependency:
its bundled Byte Buddy rejects Java 25 in Mockito tests and prevents launch.
Skipping those tests allows ordinary play; it does not verify that suite or
change Frozen's source. To launch directly inside Frozen:

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
