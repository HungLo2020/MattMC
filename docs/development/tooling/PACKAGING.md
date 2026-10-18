# Packaged install: layout, updates, sync and backup

How the released game folder is laid out and what each packaged script may
touch. The scripts live in [`packaging/`](https://github.com/HungLo2020/MattMC/tree/master/packaging);
`clientDist`/`clientDistZip` in `build.gradle` assemble the release
`MattMC-Client-<version>-<os>-<arch>.zip`.

## Layout

| Path | Owner | Contents |
| --- | --- | --- |
| `MattMC/lib/`, `MattMC/natives/` | Release | Game and dependency jars, Rust native library |
| `MattMC/*.sh`, `*.bat`, `*.ps1` | Release | Client launcher, updater, sync and backup scripts |
| `MattMC/run/` | Player | Client game directory: saves, options, configs, resource and shader packs |
| `MattMC/run/server/` | Player | Dedicated server working directory and its launchers (`run-server.*`) |
| `MattMC/run/jdk/<platform>/` | Release | Bundled JDK, used by the client and server launchers |
| `MattMC/run/shaderpacks/<bundled>.zip` | Release | Bundled shader pack; other packs there belong to the player |

`run/server/run-server.*` run inside `run/server/`, use `../jdk/<platform>`
and the install root's `lib/` and `natives/`.

## Rules

- **Install and update** (`update-mattmc.sh`/`.ps1`, Basalt's MattMC install):
  - Replace `lib/`, `natives/` and `run/jdk/` wholesale. Files a release drops are
    removed; launchers put every `lib/*.jar` on the classpath.
  - Merge the rest of the release's `run/` over the existing one: the bundled
    shader pack and server launchers/README are overwritten, all other `run/`
    data is kept.
  - Overwrite the root scripts. The updater replaces itself last.
- **Sync Up/Down** (`SyncGameData.sh`/`.ps1`, also run by Basalt): mirror only
  `MattMC/run/` with `<sync root>/MattMC/run/`, deleting destination files the
  source lacks. `run/jdk/` is excluded at both ends (never copied, never
  deleted). Nothing outside `run/` is read or written at either end. The sync
  root defaults to `/mnt/storage/Storage/Sync` (`Z:\Storage\Sync` on Windows)
  and can be overridden with `MATTMC_SYNC_ROOT`. A missing mount is not
  detected: Sync Up then writes the local path, Sync Down fails.
- **Backup** (`backup.sh`): zips the whole install folder to the storage share.

There is no automatic migration of older layouts: a pre-2026-10-17 install's
`MattMC/server/` must be moved into `MattMC/run/server/` by hand.

Basalt invokes `SyncGameData.sh` (writing `up`/`down` to its prompt),
`update-mattmc.sh` and `backup.sh` from the install directory; keep those
names and the prompt stable.

## Verify

```sh
python3 DevUtils/tests/tooling/VerifyPackagingScripts.py
```

Runs the Bash updater (with `--archive` on a synthetic release ZIP), Sync
Up/Down against a temporary remote that still holds an old whole-install mirror,
and the server launcher with a stub JDK. All work happens in temporary
directories. The PowerShell scripts have no automated check; review them by
hand against the rules above. To check the real layout, build
`./gradlew clientDist` and inspect `build/client-dist/MattMC/`.
