# Capture storage and recovery

Graphics captures accumulate under `artifacts/graphics-captures/`,
`logs/graphics-audit/`, and older `~/.cache/mattmc-*` test directories. Preserve
unique runtime evidence and fixture inputs when reclaiming space. Treat copied
worlds as saves, even when their parent directory is named `cache`.

## Bulk retirement: 2026-10-08

The [implementation author's cleanup record at `97e30922`](https://github.com/HungLo2020/MattMC/blob/97e3092269ed29854c8175a480a819fb1896c311/docs/development/rendering/ARTIFACT-STORAGE.md)
reports free space rising from about 8.8 GiB to 304 GiB and the capture tree
falling from 339 GiB to about 62 GiB. It records retirement of 1,081 inactive
generated workspaces, 413 obsolete videos/raw readbacks, unused Cargo incremental
caches, and 8,135 inactive Gradle daemon logs. The record also attributes
42.26 GiB of recovery to replacing 2,669 large historical measurement/log/readback
files with hash-verified `.gz` archives.

The author reports preserving current verification reports and frames, source
worlds, original shader packs, retained reference assets and unresolved crash
evidence. The ignored receipt directory
`artifacts/graphics-captures/goal5/disk-cleanup-20261008/` is reported to contain
deleted paths, preserved fixture manifests, archive hashes and before/after
checks, with protected save/settings and shader-pack hashes matching. This
documentation review did not inspect that unbundled receipt or the source
filesystem, reproduce the cleanup, or independently establish those figures.

A follow-up reclaimed another 45.54 GiB, leaving about 348 GiB free. It removed
41 unused build-output/cache directories and, after stopping the idle Gradle
daemon, its regenerable compiler analysis cache. The active checkout's main
build and staged native library remain available; retired build trees require
recompilation. Another 1,237 historical logs, measurement files and raw
readbacks became hash-verified `.gz` archives, saving 13.96 GiB. Nineteen exact
duplicate archives now share storage through hardlinks. Recent validation,
feature comparison baselines, source fixtures and crash evidence were excluded
from this additional compression. All 28 protected source/save/settings/pack
hashes matched, and cleanup left the worktree status unchanged before this
documentation update. Follow-up receipts are in `bulk-followup/` below the
receipt directory named below.

A final sweep reclaimed another 10.90 GiB: 2,065 byte-identical historical
artifact files now share storage, five inactive generated fixtures outside the
main capture tree were retired, and unused Cargo profiles/incremental data were
removed. The current release library and debug test outputs remain; rebuilding
retired profiles recreates their caches. Free space measured 352.49 GiB after
the recent builds and verification runs. Receipts in `final-dedup/` record
duplicate hashes, original timestamps, removed paths and protected-file checks.
Historical hardlinked artifacts are immutable: copy a file to a separate inode
before editing it. Unique evidence and pinned investigations were retained.

Retired videos/readbacks cannot be restored from this receipt; old generated
workspaces require regeneration from their recorded inputs.

After a paired verification invocation finishes, retire its generated fixture
copies once its input sources and fixture manifests are retained. Keep recent
acceptance evidence and representative unresolved failures; prune superseded
captures regularly. Capture quotas exclude `.canonical-fixtures`, and
`--artifact-preserve-current-run` protects the current capture's extracted
evidence while ordinary cleanup still removes managed temporary game directories;
it does not bypass the verification-driver retirement below. Neither gives a bound on
total workspace storage. Never retire a fixture between Current and Frozen
rows or while a client/build still uses it.

## Verification-driver retention

The [retirement helpers at `97e30922`](https://github.com/HungLo2020/MattMC/blob/97e3092269ed29854c8175a480a819fb1896c311/DevUtils/Common/artifact_retention.py#L410-L497)
perform two separate destructive operations. `RunValidation.py` retires workspace
copies while finishing its summary; `RunFeatureParity.py` does so after each
scenario's pair and again at the end. Both prune older completed invocations
after writing their summary. Failed comparisons are eligible too. A driver
exception before these calls can leave copies and no final retention receipt.

For workspace retirement, only paths inside a marked invocation are candidates:

- `.canonical-fixtures/<fixture>/run` requires a
  `mattmc-cross-repo-fixture-v2` manifest with a matching resolved `run_root`
  and an existing `source_run` directory that is neither the run itself, its
  ancestor nor its descendant. The manifest beside `run` remains after deletion.
- Directories named `game_dir_*` or `region_validation_game_*` qualify by name;
  they do not require the canonical-fixture manifest.
- Unknown canonical fixtures and symlinked fixture/run candidates remain.
  Cleanup also retains candidates when `/proc` is absent or a readable process
  command/working directory references them. This is best-effort process
  discovery: unreadable process records are not proof of inactivity, and open
  files alone are not checked. Stop affected clients and builds before cleanup.
- Files matching `hs_err_pid*.log`, `core*` or `crash-reports/*` at the candidate
  workspace root retain that workspace and create a root `.keep` pin. This is
  not a recursive search for every form or location of crash evidence.

**Keep source saves outside retirement-managed outputs and verify them before
running either driver.** The canonical-fixture check validates path relationships,
plus source-directory existence, not input hashes or recoverability. The drivers check source
existence before creating a fresh output, and the capture harness requires a
source world. The retirement helper does not compare recorded input hashes or independently
protect sources placed inside disposable output trees.
Isolated synthetic fixtures confirm those helper limits; they do not reproduce
data loss in a normal fresh-driver run. A manifest by itself is not a backup.

Workspace retirement ignores capture preservation and root `.keep` pins;
those pins protect the **full invocation** in the next operation. Among sibling
invocations with the exact driver schema (`mattmc-validation-v2` or
`mattmc-feature-parity-v2`), a generated-root marker and a Boolean `passed`,
full-invocation retirement keeps the newest successful and failed summary by
`summary.json` modification time. It also keeps the current invocation, root
`.keep` pins, symlinked invocations and detected live work.
`RunFeatureParity.py --compare <label>` protects that label during this call;
add a root `.keep` if it must survive later runs that do not name it. A `.preserve`
marker alone does not pin an invocation against this operation. Older/unknown,
missing or unreadable summaries are excluded and need deliberate review.
Remove obsolete `.keep` pins only after their investigation or published
acceptance purpose is resolved.

`summary.json` records the final workspace pass in `workspace_retention` and
deleted old runs in `retired_invocations`; the feature driver's earlier
per-scenario workspace passes are not accumulated in that final receipt.
Retirement deletes files, without an archive or undo path. Keep the input sources
and pin evidence needed to reproduce a comparison. The earlier documentation
review independently ran four temporary-fixture tests with mocked process
checks. Current verification passes nine retention tests, including a real
child-process lifetime check, within 37 passing harness regressions. These
bounded checks do not establish safe cleanup of every live filesystem state.

The capture runner also retires its isolated game directories after shutdown.
Its ownership-marker search now reaches parent directories; the former early
return silently skipped this cleanup. Live process references, symlinks,
missing `/proc` process visibility and crash-containing copies remain. Canonical
fixture retirement also requires that its recorded source directory still
exists. Check these paths with:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p test_verification_artifacts.py
```


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
