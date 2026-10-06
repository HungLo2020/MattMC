# Capture storage and recovery

Graphics captures accumulate under `artifacts/graphics-captures/`,
`logs/graphics-audit/`, and older `~/.cache/mattmc-*` test directories. Preserve
unique runtime evidence and fixture inputs when reclaiming space. Treat copied
worlds as saves, even when their parent directory is named `cache`.

## Lossless historical archives

The 2026-10-04 cleanup replaced large historical capture JSON, raw data and logs,
inactive Gradle daemon logs, and historical DH database backup files with
adjacent `.gz` archives. It preserved live world databases, region files,
screenshots, recordings, JFR/RenderDoc captures, and native crash evidence.
Current Goal 5 capture data was excluded from capture-data compression; historical
DH backup files inside those fixtures were eligible.

An archive contains the exact original bytes. Before removing each original,
the cleanup decompressed its archive and verified the original SHA-256. The
ignored receipt directory `artifacts/graphics-captures/goal5/disk-cleanup-20261004/`
contains the file inventory, original hashes, retained asset reference, and
before/after storage and worktree checks.

Exact duplicate compressed DH backups share storage through hardlinks, after
verification of original hashes and the retained archive's decompressed bytes. Their paths and
contents remain available independently. Treat these archives as immutable;
restore a separate uncompressed file before changing a recovered database.

Tools that expect an original `.json`, `.raw`, or `.log` path need that file
restored first. For example:

```sh
gzip -dk -- path/to/graphics_audit_artifact.json.gz
```

`-k` retains the archive; `gzip` refuses to overwrite an existing destination
unless explicitly forced. Restore individual inputs as needed instead of
expanding the entire archive collection. Use `gzip -dc -- FILE.gz` to inspect
text without restoring it. Restore an archived DH backup before using the
existing world recovery procedure; do not replace a live database during a run.

## Reclaiming reproducible data

- Remove archived test `assets/` directories only after verifying every file
  against a retained reference inventory. Preserve distinct asset sets. A replay
  that requires a retired asset directory must copy the retained reference back
  to that path first; the cleanup receipt records both paths.
- Duplicate Cargo target trees and incremental compilation caches are
  reproducible. Their removal requires recompilation for subsequent builds.
  Keep the staged native library and the build target used by an ongoing A/B
  comparison so native identity remains verifiable.
- Preserve active daemon logs and their registries, dependency downloads,
  source changes, fixture manifests, and unique crash evidence. Avoid broad
  `git clean` commands and indiscriminate deletion of directories named `run`,
  `saves`, or `cache`.

Perform cleanup while no test client or affected build is running. Never edit
the Frozen repository as part of capture cleanup. Record actual filesystem
free-space changes separately from logical file sizes, which can differ because
of hardlinks, directory overhead, or concurrent disk activity.
