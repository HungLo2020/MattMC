#!/usr/bin/env python3
"""Self-tests for the cross-platform Rust migration harness."""

from __future__ import annotations

import json
import stat
import subprocess
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path

import rust_migration_harness as harness


def write_fake_gradle(root: Path, *, sleep_seconds: int = 0, exit_code: int = 0) -> None:
    python = sys.executable
    helper = root / "fake_gradle.py"
    helper.write_text(
        "\n".join(
            [
                "import pathlib",
                "import sys",
                "import time",
                f"time.sleep({sleep_seconds})",
                "import os",
                "parts = list(sys.argv) + os.environ.get('JAVA_TOOL_OPTIONS', '').split()",
                "outputs = [arg for arg in parts if arg.startswith('-Dmattmc.perf.output=')]",
                "if outputs:",
                "    path = pathlib.Path(outputs[-1].split('=', 1)[1])",
                "    path.parent.mkdir(parents=True, exist_ok=True)",
                "    path.write_text('{\"status\":\"ok\",\"results\":[]}\\n', encoding='utf-8')",
                f"sys.exit({exit_code})",
                "",
            ]
        ),
        encoding="utf-8",
    )
    if harness.platform_name() == "windows":
        (root / "gradlew.bat").write_text(f'@echo off\n"{python}" "{helper}" %*\n', encoding="utf-8")
    gradlew = root / "gradlew"
    gradlew.write_text(f'#!/usr/bin/env sh\n"{python}" "{helper}" "$@"\n', encoding="utf-8")
    gradlew.chmod(gradlew.stat().st_mode | stat.S_IXUSR)


def fake_repo(parent: Path, name: str, *, sleep_seconds: int = 0, exit_code: int = 0) -> Path:
    root = parent / name
    root.mkdir(parents=True)
    (root / ".git").mkdir()
    write_fake_gradle(root, sleep_seconds=sleep_seconds, exit_code=exit_code)
    return root


def args(**overrides) -> Namespace:
    values = {
        "target": "current",
        "workload": "metadata",
        "frozen_repo": None,
        "artifact_dir": None,
        "timeout_seconds": 5,
        "native_rebuild_timeout_seconds": 5,
        "rust_profile": "release",
        "jvm_arg": [],
        "gradle_arg": [],
        "dry_run": False,
        "rebuild_current_native": False,
        "diagnostic": False,
        "quads": 1,
        "warmup": 0,
        "iterations": 1,
        "warmup_ms": 0,
        "measure_ms": 1,
        "fork_index": 0,
        "forks": 1,
        "alternate_order": True,
        "benchmark_force_mode": "clean-test",
    }
    values.update(overrides)
    return Namespace(**values)


class RustMigrationHarnessTests(unittest.TestCase):
    def test_directory_helper_resolves_windows_config_with_spaces(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            configured = temp_path / "Repo With Spaces"
            configured.mkdir()
            config = temp_path / "directories.json"
            config.write_text(
                json.dumps({"java_perf_repo": {"windows": str(configured), "linux": "/tmp/frozen", "macos": None}}),
                encoding="utf-8",
            )
            helper = Path(__file__).resolve().parents[1] / "Common" / "platform" / "directory" / "directory_helper.py"
            result = subprocess.run(
                [sys.executable, str(helper), "java_perf_repo", "--platform", "windows", "--config", str(config)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(Path(result.stdout.strip()), configured.resolve())

    def test_missing_repository_configuration_reports_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            config_dir = root / "DevUtils" / "Common" / "platform" / "directory"
            config_dir.mkdir(parents=True)
            (config_dir / "directory_helper.py").write_text("raise SystemExit('missing test config')\n", encoding="utf-8")
            with self.assertRaises(SystemExit):
                harness.find_frozen_repo(root)

    def test_chunk_meshing_command_uses_argument_list_and_output_path_with_spaces(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "Repo With Spaces")
            target = harness.RepoTarget("current", root, "current")
            output = Path(temp) / "Artifacts With Spaces" / "result.json"
            command, env = harness.build_command(
                target,
                "chunk-meshing-hotpath",
                args(jvm_arg=["-Dextra=true"]),
                output,
            )
            self.assertIn("-PmattmcRustProfile=release", command)
            self.assertIn("cleanTest", command)
            self.assertIn("--no-build-cache", command)
            self.assertNotIn("--rerun-tasks", command)
            self.assertIn(str(output), env["JAVA_TOOL_OPTIONS"])
            self.assertIsInstance(command, list)

    def test_diagnostic_mode_enables_current_native_profile_env(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            command, env = harness.build_command(
                harness.RepoTarget("current", root, "current"),
                "chunk-meshing-hotpath",
                args(workload="chunk-meshing-hotpath", diagnostic=True),
                Path(temp) / "result.json",
            )
            self.assertIn("test", command)
            self.assertIn("-Dmattmc.perf.diagnostic=true", env["JAVA_TOOL_OPTIONS"])
            self.assertEqual(env["MATTMC_PROFILE_SCAN_SUBSTAGES"], "true")
            self.assertEqual(env["MATTMC_PROFILE_FLUID_SUBSTAGES"], "true")

    def test_chunk_meshing_legacy_rerun_mode_is_explicit(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            command, _ = harness.build_command(
                harness.RepoTarget("current", root, "current"),
                "chunk-meshing-hotpath",
                args(benchmark_force_mode="rerun-tasks"),
                Path(temp) / "result.json",
            )
            self.assertNotIn("cleanTest", command)
            self.assertIn("--rerun-tasks", command)

    def test_chunk_meshing_none_force_mode_allows_up_to_date_skip(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            command, _ = harness.build_command(
                harness.RepoTarget("current", root, "current"),
                "chunk-meshing-hotpath",
                args(benchmark_force_mode="none"),
                Path(temp) / "result.json",
            )
            self.assertNotIn("cleanTest", command)
            self.assertNotIn("--rerun-tasks", command)

    def test_alternating_order_swaps_two_target_even_forks(self) -> None:
        current = harness.RepoTarget("current", Path("current"), "current")
        frozen = harness.RepoTarget("frozen", Path("frozen"), "frozen")
        self.assertEqual([t.name for t in harness.ordered_targets([current, frozen], 1, True)], ["current", "frozen"])
        self.assertEqual([t.name for t in harness.ordered_targets([current, frozen], 2, True)], ["frozen", "current"])
        self.assertEqual([t.name for t in harness.ordered_targets([current, frozen], 2, False)], ["current", "frozen"])

    def test_benchmark_aggregate_classifies_equivalent_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            artifact = Path(temp)
            for fork in (1, 2):
                for target, median in (("current", 2.0), ("frozen", 1.0)):
                    target_dir = artifact / f"fork-{fork:02d}" / target
                    target_dir.mkdir(parents=True)
                    (target_dir / "chunk-meshing-hotpath.json").write_text(
                        json.dumps(
                            {
                                "fork_index": fork,
                                "results": [
                                    {
                                        "name": "normal_surface_terrain",
                                        "median_ms": median,
                                        "accounting": {
                                            "output_quads_per_invocation": 10,
                                            "output_vertex_bytes_per_invocation": 40,
                                            "output_index_bytes_per_invocation": 20,
                                            "fallback_like_blocks_total": 0,
                                            "fallback_like_quads_total": 0,
                                        },
                                    }
                                ],
                            }
                        ),
                        encoding="utf-8",
                    )
            results = [
                harness.CommandResult(
                    target=target,
                    workload="chunk-meshing-hotpath",
                    success=True,
                    timed_out=False,
                    exit_code=0,
                    command=[],
                    artifact_dir=str(artifact / f"fork-{fork:02d}" / target),
                    metadata_path="",
                    stdout_path=None,
                    stderr_path=None,
                    started_at="",
                    ended_at="",
                    duration_seconds=0,
                )
                for fork in (1, 2)
                for target in ("current", "frozen")
            ]
            aggregate = harness.build_benchmark_aggregate(results)
            self.assertIsNotNone(aggregate)
            row = aggregate["rows"][0]
            self.assertEqual(row["classification"], "current-slower")
            self.assertEqual(row["current_over_frozen_ratio"]["median"], 2.0)

    def test_semantic_classifier_distinguishes_byte_and_encoding_differences(self) -> None:
        fingerprint = {
            "raw_vertex_hash": "a",
            "raw_index_hash": "b",
            "ordered_semantic_hash": "c",
            "canonical_semantic_hash": "d",
            "normalized_semantic_hash": "e",
            "translucent_metadata_hash": "f",
        }
        self.assertEqual(harness.classify_semantic(fingerprint, dict(fingerprint), False), "byte-identical")
        current = dict(fingerprint)
        current["raw_vertex_hash"] = "different"
        self.assertEqual(harness.classify_semantic(current, fingerprint, False), "semantic-identical-encoding-different")
        current["ordered_semantic_hash"] = "different"
        self.assertEqual(harness.classify_semantic(current, fingerprint, False), "semantic-identical-order-different")
        current["canonical_semantic_hash"] = "different"
        self.assertEqual(harness.classify_semantic(current, fingerprint, False), "semantic-mismatch")

    def test_metadata_generation_records_dirty_state_and_artifact_separation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            artifact = Path(temp) / "artifacts"
            result = harness.run_target(
                harness.RepoTarget("current", root, "current"),
                "metadata",
                artifact,
                args(workload="metadata"),
            )
            self.assertTrue(result.success)
            metadata = json.loads(Path(result.metadata_path).read_text(encoding="utf-8"))
            self.assertEqual(metadata["repository"]["target"], "current")
            self.assertEqual(Path(result.artifact_dir).parent, artifact)

    def test_timeout_kills_child_and_returns_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "slow", sleep_seconds=10)
            result = harness.run_target(
                harness.RepoTarget("slow", root, "current"),
                "gradle-test",
                Path(temp) / "artifacts",
                args(workload="gradle-test", timeout_seconds=1),
            )
            self.assertFalse(result.success)
            self.assertTrue(result.timed_out)

    def test_failed_child_command_propagates_nonzero_status(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "failing", exit_code=7)
            result = harness.run_target(
                harness.RepoTarget("failing", root, "current"),
                "gradle-test",
                Path(temp) / "artifacts",
                args(workload="gradle-test", timeout_seconds=5),
            )
            self.assertFalse(result.success)
            self.assertFalse(result.timed_out)
            self.assertEqual(result.exit_code, 7)

    def test_chunk_meshing_requires_benchmark_output_json(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            artifact = Path(temp) / "artifacts"
            result = harness.run_target(
                harness.RepoTarget("current", root, "current"),
                "chunk-meshing-hotpath",
                artifact,
                args(workload="chunk-meshing-hotpath", jvm_arg=["-Dignored=true"]),
            )
            self.assertTrue(result.success)
            self.assertTrue((artifact / "current" / "chunk-meshing-hotpath.json").is_file())

    def test_unsupported_workload_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            with self.assertRaises(SystemExit):
                harness.build_command(harness.RepoTarget("current", root, "current"), "not-a-workload", args(), Path(temp) / "x")


if __name__ == "__main__":
    unittest.main()
