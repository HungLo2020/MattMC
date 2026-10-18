#!/usr/bin/env python3
"""Exercise the packaged install scripts against throwaway installs.

Runs the real packaging/ scripts (Bash) on temporary directories only:
- update-mattmc.sh --archive: wholesale lib/natives/run/jdk replacement,
  merged run/ data, overwritten bundled files and deferred self-update.
- SyncGameData.sh: Sync Up/Down of run/ only, deletes, run/jdk exclusion,
  and nothing outside run/ touched at either end.
- run-server.sh: launched from run/server with the run/jdk, lib/ and natives/
  of the same install.

    python3 DevUtils/tests/tooling/VerifyPackagingScripts.py
"""

from __future__ import annotations

import os
import platform
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
PACKAGING = REPO / "packaging"


def write(path: Path, text: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def install_script(source_name: str, destination: Path) -> Path:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(PACKAGING / source_name, destination)
    destination.chmod(0o755)
    return destination


def tree(root: Path) -> set[str]:
    return {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()}


def linux_platform() -> str:
    machine = platform.machine().lower()
    return "linux-aarch64" if machine in {"aarch64", "arm64"} else "linux-x64"


@unittest.skipUnless(shutil.which("bash") and shutil.which("unzip"), "requires bash and unzip")
class UpdaterTest(unittest.TestCase):
    def test_update_replaces_application_files_and_preserves_run_data(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            install = temp_path / "MattMC"
            install_script("update-mattmc.sh", install / "update-mattmc.sh")
            write(install / "lib" / "dependency-1.0.jar", "old dependency")
            write(install / "lib" / "MattMC.jar", "old game")
            write(install / "natives" / "old.so", "old native")
            write(install / "run" / "jdk" / "linux-x64" / "bin" / "java", "old jdk")
            write(install / "run" / "jdk" / "linux-x64" / "removed-file", "dropped by release")
            write(install / "run" / "saves" / "World" / "level.dat", "save")
            write(install / "run" / "options.txt", "user options")
            write(install / "run" / "shaderpacks" / "Bundled.zip", "old bundled pack")
            write(install / "run" / "shaderpacks" / "Bundled.zip.txt", "pack settings")
            write(install / "run" / "shaderpacks" / "UserPack.zip", "user pack")
            write(install / "run" / "server" / "world" / "level.dat", "server world")
            write(install / "run" / "server" / "server.properties", "motd=mine")
            write(install / "run" / "server" / "run-server.sh", "old server launcher")
            write(install / "run-mattmc.sh", "old launcher")

            release = temp_path / "release"
            payload = release / "MattMC"
            write(payload / "lib" / "dependency-2.0.jar", "new dependency")
            write(payload / "lib" / "MattMC.jar", "new game")
            write(payload / "natives" / "new.so", "new native")
            write(payload / "run" / "jdk" / "linux-x64" / "bin" / "java", "new jdk")
            write(payload / "run" / "shaderpacks" / "Bundled.zip", "new bundled pack")
            (payload / "run" / "resourcepacks").mkdir(parents=True)
            write(payload / "run" / "server" / "run-server.sh", "new server launcher")
            write(payload / "run" / "server" / "README.md", "server readme")
            write(payload / "run-mattmc.sh", "new launcher")
            new_updater = (PACKAGING / "update-mattmc.sh").read_text(encoding="utf-8") + "\n# release marker\n"
            write(payload / "update-mattmc.sh", new_updater)
            archive = temp_path / "MattMC-Client-9.9.9-linux-x64.zip"
            with zipfile.ZipFile(archive, "w") as zip_file:
                for path in release.rglob("*"):
                    zip_file.write(path, path.relative_to(release).as_posix())

            result = subprocess.run(
                ["bash", str(install / "update-mattmc.sh"), "--archive", str(archive)],
                capture_output=True, text=True, check=False,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

            self.assertEqual(tree(install / "lib"), {"dependency-2.0.jar", "MattMC.jar"})
            self.assertEqual((install / "lib" / "MattMC.jar").read_text(), "new game")
            self.assertEqual(tree(install / "natives"), {"new.so"})
            self.assertEqual(tree(install / "run" / "jdk"), {"linux-x64/bin/java"})
            self.assertEqual((install / "run" / "jdk" / "linux-x64" / "bin" / "java").read_text(), "new jdk")
            self.assertEqual((install / "run" / "saves" / "World" / "level.dat").read_text(), "save")
            self.assertEqual((install / "run" / "options.txt").read_text(), "user options")
            self.assertEqual((install / "run" / "shaderpacks" / "Bundled.zip").read_text(), "new bundled pack")
            self.assertEqual((install / "run" / "shaderpacks" / "Bundled.zip.txt").read_text(), "pack settings")
            self.assertEqual((install / "run" / "shaderpacks" / "UserPack.zip").read_text(), "user pack")
            self.assertEqual((install / "run" / "server" / "world" / "level.dat").read_text(), "server world")
            self.assertEqual((install / "run" / "server" / "server.properties").read_text(), "motd=mine")
            self.assertEqual((install / "run" / "server" / "run-server.sh").read_text(), "new server launcher")
            self.assertEqual((install / "run" / "server" / "README.md").read_text(), "server readme")
            self.assertTrue((install / "run" / "resourcepacks").is_dir())
            self.assertEqual((install / "run-mattmc.sh").read_text(), "new launcher")
            self.assertEqual((install / "update-mattmc.sh").read_text(encoding="utf-8"), new_updater)


@unittest.skipUnless(shutil.which("bash") and shutil.which("rsync"), "requires bash and rsync")
class SyncTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.install = root / "local" / "MattMC"
        self.sync_root = root / "remote"
        self.remote = self.sync_root / "MattMC"
        install_script("SyncGameData.sh", self.install / "SyncGameData.sh")
        write(self.install / "lib" / "MattMC.jar", "local app")
        write(self.install / "run" / "saves" / "World" / "level.dat", "local save")
        write(self.install / "run" / "server" / "world" / "level.dat", "local server world")
        write(self.install / "run" / "jdk" / "linux-x64" / "bin" / "java", "local jdk")
        # The remote still holds the previous whole-install mirror.
        write(self.remote / "lib" / "MattMC.jar", "remote app")
        write(self.remote / "server" / "world" / "level.dat", "old remote server")
        write(self.remote / "run" / "saves" / "Old" / "level.dat", "remote-only save")
        write(self.remote / "run" / "jdk" / "linux-x64" / "bin" / "java", "remote jdk")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def sync(self, direction: str) -> None:
        env = dict(os.environ, MATTMC_SYNC_ROOT=str(self.sync_root))
        result = subprocess.run(
            ["bash", str(self.install / "SyncGameData.sh")], input=f"{direction}\n",
            capture_output=True, text=True, env=env, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_sync_up_mirrors_run_only_and_excludes_jdk(self) -> None:
        self.sync("up")
        remote_run = self.remote / "run"
        self.assertEqual((remote_run / "saves" / "World" / "level.dat").read_text(), "local save")
        self.assertEqual((remote_run / "server" / "world" / "level.dat").read_text(), "local server world")
        self.assertFalse((remote_run / "saves" / "Old").exists(), "remote-only data is deleted")
        self.assertEqual((remote_run / "jdk" / "linux-x64" / "bin" / "java").read_text(), "remote jdk")
        self.assertEqual((self.remote / "lib" / "MattMC.jar").read_text(), "remote app")
        self.assertEqual((self.remote / "server" / "world" / "level.dat").read_text(), "old remote server")
        self.assertFalse((self.remote / "SyncGameData.sh").exists(), "files outside run/ are not uploaded")

    def test_sync_down_mirrors_run_only_and_excludes_jdk(self) -> None:
        self.sync("down")
        local_run = self.install / "run"
        self.assertEqual((local_run / "saves" / "Old" / "level.dat").read_text(), "remote-only save")
        self.assertFalse((local_run / "saves" / "World").exists(), "local-only data is deleted")
        self.assertFalse((local_run / "server").exists(), "remote run/ has no server, so local run/server goes")
        self.assertEqual((local_run / "jdk" / "linux-x64" / "bin" / "java").read_text(), "local jdk")
        self.assertEqual((self.install / "lib" / "MattMC.jar").read_text(), "local app")
        self.assertFalse((self.install / "server").exists(), "remote files outside run/ are not downloaded")

    def test_sync_down_requires_remote_run(self) -> None:
        shutil.rmtree(self.remote / "run")
        env = dict(os.environ, MATTMC_SYNC_ROOT=str(self.sync_root))
        result = subprocess.run(
            ["bash", str(self.install / "SyncGameData.sh")], input="down\n",
            capture_output=True, text=True, env=env, check=False,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.install / "run" / "saves" / "World").exists())


@unittest.skipUnless(shutil.which("bash") and platform.system() == "Linux", "requires bash on Linux")
class ServerLauncherTest(unittest.TestCase):
    def test_server_launcher_uses_run_server_and_shared_install(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            install = Path(temp) / "MattMC"
            server_dir = install / "run" / "server"
            install_script("run-server.sh", server_dir / "run-server.sh")
            write(install / "lib" / "MattMC.jar", "jar")
            (install / "natives").mkdir()
            record = Path(temp) / "launch.txt"
            java = write(install / "run" / "jdk" / linux_platform() / "bin" / "java",
                         f'#!/bin/bash\n{{ pwd; printf "%s\\n" "$@"; }} > "{record}"\n')
            java.chmod(0o755)

            result = subprocess.run(["bash", str(server_dir / "run-server.sh")],
                                    capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            lines = record.read_text().splitlines()
            self.assertEqual(Path(lines[0]).resolve(), server_dir.resolve())
            self.assertIn(f"-Dmattmc.rust.natives.dir={install.resolve() / 'natives'}", lines)
            self.assertIn(str(install.resolve() / "lib" / "MattMC.jar"), lines)
            self.assertIn("net.minecraft.server.Main", lines)


if __name__ == "__main__":
    unittest.main(verbosity=2)
