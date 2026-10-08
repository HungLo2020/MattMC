"""Finished verification retires copies while preserving inputs and live work."""
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "DevUtils/Common"))
import artifact_retention as retention


class VerificationArtifactTest(unittest.TestCase):
    def fixture(self, root, source):
        fixture = root / ".canonical-fixtures/example"
        run = fixture / "run"
        run.mkdir(parents=True)
        (run / "copied-world.dat").write_text("generated copy")
        (fixture / "fixture_manifest.json").write_text(json.dumps({
            "schema": "mattmc-cross-repo-fixture-v2", "run_root": str(run), "source_run": str(source)}))
        return run

    def test_completed_copies_retire_but_source_manifest_and_images_remain(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary); source = base / "source"; source.mkdir()
            (source / "world.dat").write_text("source world")
            root = base / "invocation"; retention.ensure_marker(root)
            run = self.fixture(root, source)
            (root / "accepted.png").write_bytes(b"evidence")
            game = root / "capture/game_dir_20261008_100000"; game.mkdir(parents=True)
            with patch.object(retention, "_live_process_references", return_value=False):
                result = retention.retire_completed_fixtures(root)
            self.assertEqual({str(run), str(game)}, set(result["removed_workspaces"]))
            self.assertTrue((run.parent / "fixture_manifest.json").exists())
            self.assertEqual("source world", (source / "world.dat").read_text())
            self.assertEqual(b"evidence", (root / "accepted.png").read_bytes())

    def test_live_work_and_crash_evidence_remain(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary); source = base / "source"; source.mkdir()
            root = base / "invocation"; retention.ensure_marker(root)
            run = self.fixture(root, source)
            with patch.object(retention, "_live_process_references", return_value=True):
                self.assertEqual([], retention.retire_completed_fixtures(root)["removed_workspaces"])
            (run / "hs_err_pid1.log").write_text("native crash")
            with patch.object(retention, "_live_process_references", return_value=False):
                self.assertEqual([], retention.retire_completed_fixtures(root)["removed_workspaces"])
            self.assertTrue((root / ".keep").exists())
            self.assertEqual("native crash", (run / "hs_err_pid1.log").read_text())

    def test_unknown_or_external_fixtures_are_never_removed(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary); source = base / "source"; source.mkdir()
            root = base / "invocation"; retention.ensure_marker(root)
            run = self.fixture(root, source)
            (run.parent / "fixture_manifest.json").write_text("{}")
            with patch.object(retention, "_live_process_references", return_value=False):
                self.assertEqual([], retention.retire_completed_fixtures(root)["removed_workspaces"])
            external = base / "external"; self.fixture(external, source)
            linked = base / "linked"; retention.ensure_marker(linked)
            (linked / ".canonical-fixtures").symlink_to(external / ".canonical-fixtures", target_is_directory=True)
            self.assertEqual([], retention.retire_completed_fixtures(linked)["removed_workspaces"])
            self.assertTrue((external / ".canonical-fixtures/example/run/copied-world.dat").exists())

    def test_old_invocations_keep_latest_success_failure_baseline_and_pins(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary); paths = {}
            for index, (name, passed) in enumerate((('old-pass', True), ('baseline', True), ('pinned', True),
                                                   ('old-fail', False), ('new-fail', False), ('current', True))):
                path = base / name; retention.ensure_marker(path); paths[name] = path
                summary = path / "summary.json"
                summary.write_text(json.dumps({'schema': 'test-invocation-v2', 'passed': passed}))
                os.utime(summary, (1000 + index, 1000 + index))
            (paths['pinned'] / '.keep').write_text('investigation')
            unknown = base / 'unknown'; retention.ensure_marker(unknown)
            (unknown / 'summary.json').write_text('{"passed": true}')
            with patch.object(retention, "_live_process_references", return_value=False):
                removed = retention.retire_old_invocations(paths['current'], 'test-invocation-v2',
                                                           protected_labels=['baseline'])
            self.assertEqual({str(paths['old-pass']), str(paths['old-fail'])}, set(removed))
            for name in ('baseline', 'pinned', 'new-fail', 'current'):
                self.assertTrue(paths[name].exists())
            self.assertTrue(unknown.exists())


if __name__ == '__main__':
    unittest.main()
