# Capture storage and recovery

Graphics captures accumulate under `artifacts/graphics-captures/`,
`logs/graphics-audit/`, and older `~/.cache/mattmc-*` test directories. Preserve
unique runtime evidence and fixture inputs when reclaiming space. Treat copied
worlds as saves, even when their parent directory is named `cache`.

## Bulk retirement: 2026-10-08

Free space rose from about 8.8 GiB to 304 GiB. The capture tree fell from
339 GiB to about 62 GiB. This cleanup retired 1,081 inactive generated
workspaces, 413 obsolete videos/raw readbacks, unused Cargo incremental
caches, and 8,135 inactive Gradle daemon logs. It also replaced 2,669 large
historical measurement/log/readback files with hash-verified `.gz` archives,
recovering 42.26 GiB through compression alone.

Current verification reports and frames, source worlds, original shader packs,
retained reference assets, and unresolved crash evidence remain available.
The ignored receipt directory `artifacts/graphics-captures/goal5/disk-cleanup-20261008/`
records deleted paths, preserved fixture manifests, archive hashes, and
before/after checks. Protected save/settings and shader-pack hashes matched.
Retired videos/readbacks cannot be restored from this receipt; old generated
workspaces require regeneration from their recorded inputs.

After a paired verification invocation finishes, retire its generated fixture
copies once its input sources and fixture manifests are retained. Keep recent
acceptance evidence and representative unresolved failures; prune superseded
captures regularly. Capture quotas exclude `.canonical-fixtures`, and
`--artifact-preserve-current-run` bypasses retention cleanup, so neither bounds
total workspace storage. Never retire a fixture between Current and Frozen
rows or while a client/build still uses it.

## Verification-driver retention

`RunValidation.py` and `RunFeatureParity.py` now retire completed generated
fixture/game copies after their comparisons, keeping fixture manifests and
capture evidence. This occurs even when capture evidence was preserved. Known
fixtures require the v2 manifest and distinct retained source path; unknown
fixtures, symlinks, live workspaces and crash-containing workspaces stay.
Automatic workspace retirement requires `/proc` process visibility.

For newly marked invocations from these drivers, automatic retention keeps
the latest successful run and latest failed run. A root `.keep` file pins an
investigation or current published acceptance record; crash-containing copies
pin their invocation automatically. `RunFeatureParity.py --compare` protects
that baseline during the comparison. Remove obsolete pins when their purpose
is resolved. Older/unknown driver summaries are excluded from this automatic
retirement and need deliberate cleanup. Retired full invocations are deleted,
not archived; capture readers cannot replay their removed evidence.

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
