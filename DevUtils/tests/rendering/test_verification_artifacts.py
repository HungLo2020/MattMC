"""Finished verification retires copies while preserving inputs and live work."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "DevUtils/Common"))
import artifact_retention as retention
import capture_runner


class VerificationArtifactTest(unittest.TestCase):
    def test_nearest_marker_finds_file_directory_and_closest_parent(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            self.assertIsNone(retention.nearest_marked_root(base / "unknown/missing"))
            root = base / "owned"; retention.ensure_marker(root)
            nested = root / "capture/run-01"; nested.mkdir(parents=True)
            evidence = nested / "receipt.json"; evidence.write_text("{}")
            self.assertEqual(root, retention.nearest_marked_root(evidence))
            self.assertEqual(root, retention.nearest_marked_root(nested))
            retention.ensure_marker(nested)
            self.assertEqual(nested, retention.nearest_marked_root(evidence))

    @unittest.skipUnless(Path('/proc').is_dir(), 'Linux process ownership check')
    def test_capture_cleanup_removes_only_ended_owned_game_copies(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "owned"; retention.ensure_marker(root)
            evidence = root / "capture/run-01"; evidence.mkdir(parents=True)
            (evidence / "frame.png").write_bytes(b"retained evidence")
            runner = object.__new__(capture_runner.CaptureRunner)
            runner.artifact_dir = evidence
            runner.run_id = "20261008_120000"
            game = root / ".tmp" / runner.run_id / "game_dir_copy"; game.mkdir(parents=True)
            runner.isolated_game_dir = game
            runner.region_validation_game_dir = None
            messages = []; runner.append_meta = messages.append
            with patch.dict(os.environ, {"MATTMC_CAPTURE_PRESERVE_ISOLATED_GAME_DIR": "false"}), \
                    patch.object(retention, "_live_process_references", return_value=False):
                runner.cleanup_generated_game_dirs()
            self.assertFalse(game.exists())
            self.assertFalse(game.parent.exists())
            self.assertEqual(b"retained evidence", (evidence / "frame.png").read_bytes())
            self.assertIn("artifact_retention_game_dirs_removed=1", messages)

    @unittest.skipUnless(Path('/proc').is_dir(), 'Linux process ownership check')
    def test_capture_cleanup_waits_for_a_real_process_to_exit(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); retention.ensure_marker(root)
            game = root / 'game_dir_copy'; game.mkdir()
            runner = object.__new__(capture_runner.CaptureRunner)
            runner.artifact_dir = root; runner.run_id = '20261008_120000'
            runner.isolated_game_dir = game; runner.region_validation_game_dir = None
            runner.append_meta = lambda text: None
            process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'],
                                       cwd=game, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                with patch.dict(os.environ, {'MATTMC_CAPTURE_PRESERVE_ISOLATED_GAME_DIR': 'false'}):
                    runner.cleanup_generated_game_dirs()
                self.assertTrue(game.exists())
            finally:
                process.terminate(); process.wait(timeout=10)
            with patch.dict(os.environ, {'MATTMC_CAPTURE_PRESERVE_ISOLATED_GAME_DIR': 'false'}):
                runner.cleanup_generated_game_dirs()
            self.assertFalse(game.exists())

    def test_capture_cleanup_preserves_live_crashed_and_external_work(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = base / "owned"; retention.ensure_marker(root)
            evidence = root / "capture/run-01"; evidence.mkdir(parents=True)
            game = root / "game_dir_copy"; game.mkdir()
            runner = object.__new__(capture_runner.CaptureRunner)
            runner.artifact_dir = evidence; runner.run_id = "20261008_120000"
            runner.isolated_game_dir = game; runner.region_validation_game_dir = None
            messages = []; runner.append_meta = messages.append
            with patch.dict(os.environ, {"MATTMC_CAPTURE_PRESERVE_ISOLATED_GAME_DIR": "false"}), \
                    patch.object(retention, "_live_process_references", return_value=True):
                runner.cleanup_generated_game_dirs()
            self.assertTrue(game.exists())
            for name in ("hs_err_pid1.log", "core.1", "crash-reports/crash.txt"):
                crash = game / name; crash.parent.mkdir(exist_ok=True)
                crash.write_text("crash evidence")
                with patch.object(retention, "_live_process_references", return_value=False):
                    runner.cleanup_generated_game_dirs()
                self.assertEqual("crash evidence", crash.read_text())
                crash.unlink()
            external = base / "source"; external.mkdir()
            runner.isolated_game_dir = external
            with patch.object(retention, "_live_process_references", return_value=False):
                runner.cleanup_generated_game_dirs()
            self.assertTrue(external.exists())
            link = root / "game_dir_symlink"; link.symlink_to(game, target_is_directory=True)
            runner.isolated_game_dir = link
            with patch.object(retention, "_live_process_references", return_value=False):
                runner.cleanup_generated_game_dirs()
            self.assertTrue(game.exists())
            self.assertTrue(link.is_symlink())

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

    def test_parent_cleanup_retains_unfinished_flights_but_retires_completed_siblings(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "profiles"; retention.ensure_marker(root)
            completed = root / "completed"
            game = completed / ".tmp/game_dir_complete"; game.mkdir(parents=True)
            (completed / "flight.json").write_text(json.dumps({
                "schema": "terrain-flight-observation-v1",
                "status": "observation_complete_requires_position_review", "engine_exit_code": 0,
                "runtime": {"status": "bounded_timeout_reaped", "client_exit_code": 143}}))
            retained = []
            for name, receipt in {
                "starting": {"schema": "terrain-flight-observation-v1", "status": "starting"},
                "failed": {"schema": "terrain-flight-observation-v1", "status": "failed", "engine_exit_code": 1},
                "bad_exit": {"schema": "terrain-flight-observation-v1",
                             "status": "observation_complete_requires_position_review", "engine_exit_code": 1},
                "unknown": {"schema": "future-flight-schema", "status": "complete", "engine_exit_code": 0},
                "malformed": "{",
            }.items():
                flight = root / name
                copy = flight / "capture/game_dir_copy"; copy.mkdir(parents=True)
                (copy / "world.dat").write_text("preserved investigation")
                (flight / "flight.json").write_text(receipt if isinstance(receipt, str) else json.dumps(receipt))
                retained.append(copy)
            with patch.object(retention, "_live_process_references", return_value=False):
                result = retention.retire_completed_fixtures(root)
            self.assertEqual([str(game)], result["removed_workspaces"])
            self.assertEqual({str(p) for p in retained}, set(result["retained_workspaces"]))
            for copy in retained:
                self.assertEqual("preserved investigation", (copy / "world.dat").read_text())
            self.assertTrue((completed / "flight.json").exists())

    def test_nested_completed_flight_does_not_override_incomplete_parent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); retention.ensure_marker(root)
            (root / "flight.json").write_text(json.dumps({
                "schema": "terrain-flight-observation-v1", "status": "starting"}))
            flight = root / "nested"
            copy = flight / "game_dir_copy"; copy.mkdir(parents=True)
            (flight / "flight.json").write_text(json.dumps({
                "schema": "terrain-flight-observation-v1",
                "status": "observation_complete_requires_position_review", "engine_exit_code": 0}))
            with patch.object(retention, "_live_process_references", return_value=False):
                result = retention.retire_completed_fixtures(root)
            self.assertEqual([], result["removed_workspaces"])
            self.assertEqual([str(copy)], result["retained_workspaces"])

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

    def test_fixture_copy_remains_when_its_source_is_missing(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = base / "invocation"; retention.ensure_marker(root)
            run = self.fixture(root, base / "missing-source")
            with patch.object(retention, "_live_process_references", return_value=False):
                result = retention.retire_completed_fixtures(root)
            self.assertEqual([], result["removed_workspaces"])
            self.assertIn(str(run), result["retained_workspaces"])
            self.assertEqual("generated copy", (run / "copied-world.dat").read_text())

    def test_parent_retirement_preserves_missing_source_copy_until_source_returns(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            old = base / "old"; retention.ensure_marker(old)
            current = base / "current"; retention.ensure_marker(current)
            for index, invocation in enumerate((old, current)):
                summary = invocation / "summary.json"
                summary.write_text(json.dumps({'schema': 'test-invocation-v2', 'passed': True}))
                os.utime(summary, (1000 + index, 1000 + index))
            source = base / "missing-source"
            run = self.fixture(old, source)
            with patch.object(retention, "_live_process_references", return_value=False):
                fixture_result = retention.retire_completed_fixtures(old)
                self.assertIn(str(run), fixture_result["retained_workspaces"])
                self.assertEqual([], retention.retire_old_invocations(current, 'test-invocation-v2'))
                self.assertEqual("generated copy", (run / "copied-world.dat").read_text())
                source.mkdir()
                (source / "world.dat").write_text("restored reference")
                self.assertEqual([str(old)], retention.retire_old_invocations(current, 'test-invocation-v2'))
            self.assertFalse(old.exists())
            self.assertEqual("restored reference", (source / "world.dat").read_text())

    def test_parent_retirement_rechecks_source_after_previous_cleanup_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            old = base / "old"; retention.ensure_marker(old)
            current = base / "current"; retention.ensure_marker(current)
            for index, invocation in enumerate((old, current)):
                summary = invocation / "summary.json"
                summary.write_text(json.dumps({'schema': 'test-invocation-v2', 'passed': False,
                    'workspace_retention': {'retained_workspaces': []}}))
                os.utime(summary, (1000 + index, 1000 + index))
            source = base / "missing-source"
            run = self.fixture(old, source)
            with patch.object(retention, "_live_process_references", return_value=False):
                self.assertEqual([], retention.retire_old_invocations(current, 'test-invocation-v2'))
            self.assertEqual("generated copy", (run / "copied-world.dat").read_text())

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
