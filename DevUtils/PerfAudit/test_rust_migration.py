#!/usr/bin/env python3
"""Self-tests for the cross-platform Rust migration harness."""

from __future__ import annotations

import json
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
from argparse import Namespace
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))

import RustMigration as harness
import MeshingCorpus
import capture_runner


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
                "outputs = [arg for arg in parts if arg.startswith('-Dmattmc.perf.output=') or arg.startswith('-Dmattmc.realMeshingReplay.output=') or arg.startswith('-PmattmcMeshingCorpusReplayOutput=')]",
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
        "real_warmup": 0,
        "real_measure": 1,
        "real_warmup_seconds": 0.0,
        "real_measure_seconds": 0.0,
        "real_rebuilds_per_sample": 1,
        "real_fixture": "",
    }
    values.update(overrides)
    return Namespace(**values)


class RustMigrationHarnessTests(unittest.TestCase):
    def test_run_client_does_not_run_the_full_test_suite(self) -> None:
        root = Path(__file__).resolve().parents[2]
        build_script = (root / "build.gradle").read_text(encoding="utf-8")
        self.assertNotIn(
            "tasks.named('runClient') {\n    dependsOn test",
            build_script,
            "runClient must remain launchable without running the full test suite",
        )
        self.assertNotIn(
            "tasks.named('compileJava') {\n    finalizedBy test",
            build_script,
            "compileJava must not force tests during a client launch",
        )

    def test_native_library_name_matches_gradle_platform_key(self) -> None:
        with mock.patch.object(harness.platform, "system", return_value="Linux"), \
             mock.patch.object(harness.platform, "machine", return_value="x86_64"):
            self.assertEqual(harness.native_library_name(), "mattmc_rust-linux-x64.so")
        with mock.patch.object(harness.platform, "system", return_value="Darwin"), \
             mock.patch.object(harness.platform, "machine", return_value="arm64"):
            self.assertEqual(harness.native_library_name(), "mattmc_rust-mac-aarch64.dylib")

    def test_capture_runner_quarantines_root_and_legacy_dot_capture_paths(self) -> None:
        root = Path("/tmp/mattmc-capture-test").resolve()
        default = capture_runner.CaptureRunner.resolve_artifact_dir(root, None)
        self.assertEqual(default, root / "artifacts" / "graphics-captures" / "auto-capture")
        self.assertEqual(
            capture_runner.CaptureRunner.resolve_artifact_dir(root, str(root)),
            root / "artifacts" / "graphics-captures" / "configured",
        )
        self.assertEqual(
            capture_runner.CaptureRunner.resolve_artifact_dir(root, str(root / ".capture")),
            root / "artifacts" / "graphics-captures" / "configured",
        )
        nested = capture_runner.CaptureRunner.resolve_artifact_dir(root, "captures/session")
        self.assertEqual(nested, root / "artifacts/graphics-captures/session")
        matrix = capture_runner.CaptureRunner.resolve_artifact_dir(
            root, str(root / "logs" / "graphics-audit" / "rust-vulkan" / "run-01")
        )
        self.assertEqual(
            matrix,
            root / "logs" / "graphics-audit" / "rust-vulkan" / "run-01",
            "the graphics matrix must retain its run-local managed artifact directory",
        )

    def test_graphics_retention_compresses_jsonl_diagnostics(self) -> None:
        import artifact_retention

        self.assertIn(
            ".jsonl",
            artifact_retention.COMPRESSIBLE_SUFFIXES,
            "large line-delimited diagnostics must participate in bounded artifact retention",
        )

    def test_world_copy_ignores_only_stale_dh_snapshots(self) -> None:
        self.assertTrue(
            capture_runner.CaptureRunner.ignored_world_copy_name(
                "/tmp/world/data", "DistantHorizons.sqlite.backup_before_fix"
            )
        )
        self.assertFalse(
            capture_runner.CaptureRunner.ignored_world_copy_name(
                "/tmp/world/data", "DistantHorizons.sqlite"
            )
        )
        self.assertFalse(
            capture_runner.CaptureRunner.ignored_world_copy_name(
                "/tmp/world/region", "DistantHorizons.sqlite.backup_before_fix"
            )
        )
        with mock.patch.dict("os.environ", {"MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE": "true"}):
            self.assertTrue(
                capture_runner.CaptureRunner.ignored_world_copy_name(
                    "/tmp/world/data", "DistantHorizons.sqlite"
                )
            )

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

    def test_configured_frozen_repo_path_exists_and_is_used(self) -> None:
        root = Path(__file__).resolve().parents[2]
        frozen = harness.resolve_named_directory(root, "java_perf_repo")
        self.assertEqual(frozen, Path("/home/matt/Documents/Repos/MattMC_JavaPerfTesting/MattMC"))
        self.assertEqual(harness.find_frozen_repo(root), frozen)
        self.assertTrue(frozen.is_dir(), frozen)
        self.assertTrue((frozen / ".git").is_dir(), frozen)
        self.assertTrue((frozen / "gradlew").is_file() or (frozen / "gradlew.bat").is_file(), frozen)

    def test_current_and_frozen_checkouts_must_be_distinct(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            with mock.patch.object(harness, "repo_root", return_value=root):
                with self.assertRaises(SystemExit) as failure:
                    harness.select_targets(args(target="both", frozen_repo=str(root)))
            self.assertIn("separate checkout", str(failure.exception))

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
            # Harness edits in either checkout are expected during parity work;
            # record them as metadata instead of treating them as a precondition
            # failure.  This mirrors the real Frozen checkout workflow.
            (root / "harness-change.txt").write_text("expected dirty checkout\n", encoding="utf-8")
            artifact = Path(temp) / "artifacts"
            with mock.patch.object(
                harness,
                "dirty_status",
                return_value={"dirty": True, "status_short": ["?? harness-change.txt"]},
            ):
                result = harness.run_target(
                    harness.RepoTarget("current", root, "current"),
                    "metadata",
                    artifact,
                    args(workload="metadata"),
                )
            self.assertTrue(result.success)
            metadata = json.loads(Path(result.metadata_path).read_text(encoding="utf-8"))
            self.assertEqual(metadata["repository"]["target"], "current")
            self.assertTrue(metadata["repository"]["dirty"])
            self.assertTrue(any("harness-change.txt" in entry for entry in metadata["repository"]["status_short"]))
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

    def test_real_chunk_meshing_replay_command_uses_live_runner_properties(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            target = harness.RepoTarget("current", root, "current")
            output = Path(temp) / "real-replay.json"
            command, env = harness.build_command(
                target,
                "real-chunk-meshing-replay",
                args(workload="real-chunk-meshing-replay", real_fixture="normal_surface_terrain"),
                output,
            )
            self.assertIn("runClient", command)
            self.assertIn(
                f"-PmattmcRunGameDir={(output.parent / 'run').resolve()}",
                command,
            )
            self.assertIn("-PmattmcRustProfile=release", command)
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.realMeshingReplay=true", java_options)
            self.assertIn(f"-Dmattmc.realMeshingReplay.output={output}", java_options)
            self.assertIn("-Dmattmc.realMeshingReplay.fixture=normal_surface_terrain", java_options)

    def test_replay_options_pin_backend_per_target(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            (root / "run").mkdir()
            (root / "run" / "options.txt").write_text("ao:true\ngraphics_backend=opengl\n", encoding="utf-8")
            current_dir = Path(temp) / "artifacts" / "current"
            harness.seed_replay_options(harness.RepoTarget("current", root, "current"), current_dir)
            self.assertIn("graphics_backend=vulkan", (current_dir / "run" / "options.txt").read_text())
            frozen_dir = Path(temp) / "artifacts" / "frozen"
            harness.seed_replay_options(harness.RepoTarget("frozen", root, "frozen"), frozen_dir)
            self.assertIn("graphics_backend=opengl", (frozen_dir / "run" / "options.txt").read_text())

    def test_real_chunk_meshing_replay_requires_output_json(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            artifact = Path(temp) / "artifacts"
            result = harness.run_target(
                harness.RepoTarget("current", root, "current"),
                "real-chunk-meshing-replay",
                artifact,
                args(workload="real-chunk-meshing-replay"),
            )
            self.assertTrue(result.success)
            self.assertTrue((artifact / "current" / "real-chunk-meshing-replay.json").is_file())

    def test_meshing_corpus_schema_v2_wraps_replayable_raw_input(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            replay = root / "real-replay.json"
            corpus = root / "ordinary.mmcm"
            replay.write_text(
                json.dumps(
                    {
                        "status": "ok",
                        "runner": "real-production-chunk-meshing",
                        "fixtures": [
                            {
                                "name": "ordinary_terrain_m1",
                                "summary": {
                                    "total_vertices": 4,
                                    "fallback_blocks": 0,
                                    "fallback_quads": 0,
                                    "canonical_hash": "abc",
                                },
                                "corpus_input": {
                                    "schema": "mattmc-real-meshing-input-v2",
                                    "fixture": "ordinary_terrain_m1",
                                    "unsupported": [],
                                    "padded_compact_grid": [],
                                    "active_blocks": [],
                                    "model_bundle": [],
                                },
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            summary = MeshingCorpus.write_corpus_from_real_replay(replay, corpus)
            self.assertEqual(summary["schema_version"], 2)
            self.assertEqual(summary["sections"][0]["classification"], "replayable-raw-input-v2")
            payload = summary["sections"][0]["payload"]
            self.assertEqual(payload["payload"]["capture_kind"], "production-real-replay-raw-input-v2")

    def test_meshing_corpus_filters_sections_by_category(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            replay = root / "real-replay.json"
            corpus = root / "fixtures.mmcm"
            replay.write_text(
                json.dumps(
                    {
                        "status": "ok",
                        "fixtures": [
                            {
                                "name": "ordinary_terrain_m1",
                                "summary": {"total_vertices": 4, "fallback_blocks": 0, "fallback_quads": 0},
                                "corpus_input": {"schema": "mattmc-real-meshing-input-v2", "fixture": "ordinary_terrain_m1", "unsupported": []},
                            },
                            {
                                "name": "fluid_heavy",
                                "summary": {"total_vertices": 8, "fallback_blocks": 0, "fallback_quads": 0},
                                "corpus_input": {"schema": "mattmc-real-meshing-input-v2", "fixture": "fluid_heavy", "unsupported": []},
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            summary = MeshingCorpus.write_corpus_from_real_replay(replay, corpus)
            filtered = MeshingCorpus.filtered_summary(summary, category="fluid-heavy")
            self.assertEqual(filtered["section_count"], 1)
            self.assertEqual(filtered["sections"][0]["name"], "fluid_heavy")

    def test_meshing_corpus_compare_reports_byte_identity_and_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            current = root / "current.json"
            frozen = root / "frozen.json"
            current.write_text(
                json.dumps(
                    {
                        "status": "ok",
                        "fixtures": [
                            {
                                "fixture": "ordinary_terrain_m1",
                                "raw_vertex_hash": "same",
                                "raw_index_hash": "same-index",
                                "solid_quads": 1,
                            },
                            {
                                "fixture": "fluid_heavy",
                                "raw_vertex_hash": "rust",
                                "raw_index_hash": "same-index",
                                "solid_quads": 2,
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            frozen.write_text(
                json.dumps(
                    {
                        "status": "ok",
                        "fixtures": [
                            {
                                "fixture": "ordinary_terrain_m1",
                                "raw_vertex_hash": "same",
                                "raw_index_hash": "same-index",
                                "solid_quads": 1,
                            },
                            {
                                "fixture": "fluid_heavy",
                                "raw_vertex_hash": "java",
                                "raw_index_hash": "same-index",
                                "solid_quads": 2,
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            comparison = MeshingCorpus.compare_replay_outputs(current, frozen)
            rows = {row["fixture"]: row for row in comparison["rows"]}
            self.assertEqual(rows["ordinary_terrain_m1"]["comparison"], "byte-identical")
            self.assertEqual(rows["fluid_heavy"]["comparison"], "semantic-mismatch")
            self.assertEqual(comparison["failure_count"], 1)

    def test_unsupported_workload_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = fake_repo(Path(temp), "current")
            with self.assertRaises(SystemExit):
                harness.build_command(harness.RepoTarget("current", root, "current"), "not-a-workload", args(), Path(temp) / "x")


if __name__ == "__main__":
    unittest.main()
