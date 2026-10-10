#!/usr/bin/env python3
"""Self-tests for the scripted look-around harness's game-directory setup."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import RunScriptedLook as harness


class ScriptedLookSetupTest(unittest.TestCase):
    def test_patch_options_overrides_and_appends(self) -> None:
        text = "enableVsync:true\nrenderDistance:12\nmaxFps:120\n"
        patched = harness.patch_options(text, {"enableVsync": "false", "pauseOnLostFocus": "false"})
        self.assertEqual(patched.splitlines(),
                         ["enableVsync:false", "renderDistance:12", "maxFps:120", "pauseOnLostFocus:false"])

    def test_prepare_copies_settings_and_world_without_touching_source(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "run"
            (source / "config").mkdir(parents=True)
            (source / "config" / "voxelmap.properties").write_text("Hide Minimap:false\n")
            (source / "options.txt").write_text("enableVsync:true\npauseOnLostFocus:true\n")
            world = source / "saves" / "New World"
            world.mkdir(parents=True)
            (world / "level.dat").write_bytes(b"x")
            game = Path(temp) / "out" / "game"
            harness.prepare_game_dir(source, "New World", game)
            self.assertTrue((game / "saves" / harness.WORLD_NAME / "level.dat").is_file())
            self.assertTrue((game / "config" / "voxelmap.properties").is_file())
            options = (game / "options.txt").read_text()
            self.assertIn("enableVsync:false", options)
            self.assertIn("pauseOnLostFocus:false", options)
            self.assertEqual((source / "options.txt").read_text(), "enableVsync:true\npauseOnLostFocus:true\n")

    def test_missing_world_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "run"
            source.mkdir()
            with self.assertRaises(SystemExit):
                harness.prepare_game_dir(source, "Nope", Path(temp) / "game")

    def test_client_command_passes_sweep_and_isolated_dirs(self) -> None:
        args = harness.parse_args(["--seconds", "30", "--jvm-arg=-Dx=1"])
        command = harness.client_command(args, Path("/tmp/g"), Path("/tmp/o"))
        self.assertIn("-PmattmcRunGameDir=/tmp/g", command)
        self.assertIn("-PmattmcRecordDir=/tmp/o", command)
        jvm = next(arg for arg in command if arg.startswith("-PmattmcRunJvmArgs="))
        self.assertIn("-Dmattmc.dev.scriptedCamera.seconds=30.0", jvm)
        self.assertIn("-Dx=1", jvm)


if __name__ == "__main__":
    unittest.main()
