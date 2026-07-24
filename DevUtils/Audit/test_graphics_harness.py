#!/usr/bin/env python3
"""Self-tests for the cross-repository graphics audit harness."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))

import graphics_harness as harness


def fake_repo(root: Path, name: str) -> harness.RepoTarget:
    repo = root / name
    repo.mkdir(parents=True, exist_ok=True)
    (repo / ".git").mkdir(exist_ok=True)
    script_dir = repo / "DevUtils" / "Common"
    script_dir.mkdir(parents=True, exist_ok=True)
    (script_dir / "capture_runner.py").write_text("raise SystemExit(0)\n", encoding="utf-8")
    (repo / "gradlew").write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
    return harness.RepoTarget(name, repo, "current" if name == "current" else "frozen-java-reference")


def write_capture(capture_dir: Path, *, backend: str = "opengl", shaders: str = "off", world: str = "Origin") -> None:
    capture_dir.mkdir(parents=True, exist_ok=True)
    (capture_dir / "meta_20260101_000000.txt").write_text(
        "\n".join(
            [
                f"backend={backend}",
                f"shaders={shaders}",
                f"world={world}",
                "world_save_state_hash=abc123",
                "world_save_state_file_count=4",
                "world_save_state_total_bytes=1024",
                "world_save_state_truncated=false",
                f"effective_enable_shaders={'true' if shaders == 'on' else 'false'}",
                "effective_shader_pack=unset",
                "validation_mode=off",
                "graphics_run_type=clean-performance",
                "validation_profile=off",
                "validation_fail_severity=warning",
                "renderdoc_capture=false",
                "tracy_capture=false",
                "shader_input_parity=off",
                "forced_window_width=1280",
                "forced_window_height=720",
                "forced_option_renderDistance=10",
                "forced_option_simulationDistance=12",
                "forced_option_guiScale=3",
                "forced_option_fullscreen=false",
                "forced_option_hideGui=false",
                "forced_option_maxFps=120",
                "forced_option_enableVsync=false",
                "memory_guard_triggered=false",
                "memory_guard_peak_rss_kb=12345",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    (capture_dir / "shader_summary_20260101_000000.txt").write_text(
        f"effective_enable_shaders={'true' if shaders == 'on' else 'false'}\neffective_shader_pack=unset\n",
        encoding="utf-8",
    )
    (capture_dir / "runClient_20260101_000000.log").write_text("Creating shared Sodium chunk pipeline\nFPS 10\n", encoding="utf-8")
    (capture_dir / "validation_events_20260101_000000.log").write_text("", encoding="utf-8")
    (capture_dir / "deterministic_camera_capture_20260101_000000.json").write_text(
        json.dumps(
            {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "initialPosition": {"x": 1.0, "y": 80.0, "z": 2.0},
                "window": {"width": 1280, "height": 720},
                "captures": [
                    {"poseName": "initial", "requestedYaw": 0.0, "requestedPitch": 10.0, "renderedFrameIndex": 8},
                    {"poseName": "right", "requestedYaw": 35.0, "requestedPitch": 10.0, "renderedFrameIndex": 16},
                ],
            }
        ),
        encoding="utf-8",
    )


def write_frame_benchmark(capture_dir: Path, samples: list[int]) -> None:
    (capture_dir / "graphics_frame_benchmark_20260101_000000.json").write_text(
        json.dumps(
            {
                "schema": "mattmc-graphics-frame-benchmark-v1",
                "status": "complete",
                "warmupFramesRequested": 2,
                "measureFramesRequested": len(samples),
                "framesSeenIncludingWarmup": len(samples) + 2,
                "measuredFrameCount": len(samples),
                "frameNanosSamples": samples,
                "worldEntered": True,
                "window": {"width": 1280, "height": 720},
                "cameraPath": {
                    "type": "settled-sine-yaw",
                    "yawDelta": 70.0,
                    "initialYaw": 0.0,
                    "initialPitch": 10.0,
                    "initialPosition": {"x": 1.0, "y": 80.0, "z": 2.0},
                },
                "runtimeState": {
                    "loadedChunks": 121,
                    "entityCount": 7,
                    "playerCount": 1,
                    "windowFocused": True,
                    "fullscreen": False,
                    "screen": "none",
                    "overlay": "none",
                    "hideGui": False,
                    "debugOverlayVisible": False,
                    "renderDistance": 10,
                    "effectiveRenderDistance": 10,
                    "simulationDistance": 12,
                    "entityDistanceScaling": 1.0,
                    "guiScale": 3,
                    "maxFps": 120,
                    "enableVsync": False,
                    "graphicsMode": "FANCY",
                },
                "validity": {
                    "wallClockCheckPassed": True,
                    "displayedFpsCheckRequired": False,
                    "displayedFpsCheckPassed": True,
                },
                "submittedWorkCounts": {
                    "sodium-terrain": len(samples) * 30,
                    "distant-horizons": len(samples) * 8,
                },
                "java": {
                    "gcCountDelta": 1,
                    "gcTimeMillisDelta": 2,
                    "usedMemoryBytesAtStart": 100,
                    "usedMemoryBytesAtEnd": 120,
                },
                "exclusivePhaseNanos": {
                    "game.rendering": {"count": 2, "total": 20_000_000, "worst": 12_000_000},
                    "iris.composite": {"count": 2, "total": 6_000_000, "worst": 4_000_000},
                },
                "nestedPhaseNanos": {
                    "game.rendering": {"count": 2, "total": 30_000_000, "worst": 18_000_000},
                    "iris.composite": {"count": 2, "total": 6_000_000, "worst": 4_000_000},
                },
            }
        ),
        encoding="utf-8",
    )


def write_subsystem_benchmark(capture_dir: Path, status: str = "complete") -> None:
    workloads = [
        "resources.buffers",
        "transfers.uploads",
        "pipelines.descriptors.resource-sets",
        "render-pass.execution",
        "terrain.multidraw",
        "gui.text",
        "entities",
        "dh-style.lod-batches",
    ]
    (capture_dir / "graphics_subsystem_benchmark_20260101_000000.json").write_text(
        json.dumps(
            {
                "schema": "mattmc-graphics-subsystem-benchmark-v1",
                "status": status,
                "backend": "opengl",
                "shaders": "off",
                "iterations": 2,
                "workloads": [
                    {
                        "name": name,
                        "status": "ok" if status == "complete" else "failed",
                        "totalNanos": 1000,
                        "perOperationNanos": 500.0,
                        "counts": {"draw": 1, "dispatch": 0, "pass": 1, "transfer": 0, "pipeline": 0, "resource": 0, "descriptor": 0, "apiCall": 0},
                    }
                    for name in workloads
                ],
            }
        ),
        encoding="utf-8",
    )


def artifact_for(temp: Path, mode: harness.ModeSpec, *, world: str = "Origin") -> dict[str, object]:
    target = fake_repo(temp, mode.target)
    capture = temp / f"capture-{mode.name}"
    write_capture(capture, backend=mode.backend, shaders=mode.shaders, world=world)
    return harness.normalize_capture_artifact(
        target,
        mode,
        capture,
        "correctness",
        True,
        ["fake"],
        0,
        False,
    )


def gameplay_artifact_for(temp: Path, mode: harness.ModeSpec, *, world: str = "Origin", samples: list[int] | None = None) -> dict[str, object]:
    target = fake_repo(temp, mode.target)
    capture = temp / f"gameplay-{mode.name}"
    write_capture(capture, backend=mode.backend, shaders=mode.shaders, world=world)
    write_frame_benchmark(capture, samples or [16_000_000, 17_000_000, 18_000_000])
    return harness.normalize_capture_artifact(
        target,
        mode,
        capture,
        "gameplay",
        True,
        ["fake"],
        0,
        False,
        tool_kind="gameplay",
    )


class GraphicsAuditHarnessTests(unittest.TestCase):
    def test_setup_project_mentions_required_graphics_tools(self) -> None:
        setup = Path(__file__).resolve().parents[1] / "SetupProject.sh"
        text = setup.read_text(encoding="utf-8")
        self.assertIn("ensure_vulkan_validation_tools", text)
        self.assertIn("ensure_renderdoc", text)
        self.assertIn("ensure_tracy", text)
        self.assertIn("VK_LAYER_KHRONOS_validation", text)

    def test_structured_vuid_parsing_deduplicates_counts(self) -> None:
        text = "\n".join(
            [
                "ERROR VALIDATION VUID-vkQueueSubmit-pSubmits-00074 MessageID=SubmitError object 0xabc GAL=queue.submit",
                "ERROR VALIDATION VUID-vkQueueSubmit-pSubmits-00074 MessageID=SubmitError object 0xdef GAL=queue.submit",
                "WARNING VALIDATION VUID-vkCmdDraw-None-08600 MessageID=DrawError frame=4 draw=12",
            ]
        )
        findings = harness.parse_validation_findings(text)
        by_vuid = {finding["vuid"]: finding for finding in findings}
        self.assertEqual(by_vuid["VUID-vkQueueSubmit-pSubmits-00074"]["count"], 2)
        self.assertEqual(by_vuid["VUID-vkCmdDraw-None-08600"]["draw"], 12)
        self.assertEqual(by_vuid["VUID-vkQueueSubmit-pSubmits-00074"]["semantic_operation"], "queue.submit")

    def test_instrumentation_fingerprint_mismatch_rejects_comparison(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            clean = gameplay_artifact_for(temp, mode)
            diagnostic = gameplay_artifact_for(temp, mode)
            diagnostic["benchmark_fingerprint"]["instrumentation"]["run_type"] = "tracy-capture"
            diagnostic["benchmark_fingerprint"]["instrumentation"]["tracy"]["enabled"] = True
            comparison = harness.compare_workloads(clean, diagnostic)
            self.assertFalse(comparison["comparable"])
            self.assertTrue(any(diff["path"].startswith("instrumentation") for diff in comparison["differences"]))

    def test_renderdoc_and_tracy_incomplete_sidecars_reject(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            target = fake_repo(temp, "current")
            capture = temp / "capture"
            write_capture(capture)
            meta = capture / "meta_20260101_000000.txt"
            meta.write_text(
                meta.read_text(encoding="utf-8")
                .replace("graphics_run_type=clean-performance", "graphics_run_type=renderdoc-capture")
                .replace("renderdoc_capture=false", "renderdoc_capture=true"),
                encoding="utf-8",
            )
            harness.write_renderdoc_summary(capture, "failed", capture / "missing.rdc", "missing capture")
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "correctness", True, ["fake"], 0, False)
            self.assertFalse(artifact["validation"]["complete"])
            self.assertFalse(artifact["validation"]["performance_publishable"])
            self.assertFalse(artifact["validation"]["renderdoc_complete"])

            meta.write_text(
                meta.read_text(encoding="utf-8")
                .replace("graphics_run_type=renderdoc-capture", "graphics_run_type=tracy-capture")
                .replace("renderdoc_capture=true", "renderdoc_capture=false")
                .replace("tracy_capture=false", "tracy_capture=true"),
                encoding="utf-8",
            )
            harness.write_tracy_summary(capture, "failed", capture / "missing.tracy", "missing capture")
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "correctness", True, ["fake"], 0, False)
            self.assertFalse(artifact["validation"]["tracy_complete"])

    def test_cli_profiles_and_hard_timeout_enforcement(self) -> None:
        smoke = harness.parse_args(["gameplay", "--dry-run"])
        self.assertEqual(smoke.profile, "smoke")
        self.assertLessEqual(smoke.timeout_seconds, 60)
        standard = harness.parse_args(["gameplay", "--profile", "standard", "--dry-run"])
        self.assertEqual(standard.timeout_seconds, 180)
        extended = harness.parse_args(["gameplay", "--profile", "extended", "--dry-run"])
        self.assertEqual(extended.timeout_seconds, 300)
        self.assertLess(harness.child_process_timeout_seconds(smoke), harness.per_mode_timeout_seconds(smoke))
        self.assertLessEqual(
            harness.child_process_timeout_seconds(smoke) + smoke.cleanup_timeout_seconds + 1,
            60,
        )
        with self.assertRaises(SystemExit):
            harness.parse_args(["gameplay", "--profile", "extended", "--timeout-seconds", "301"])

    def test_stuck_phase_classification(self) -> None:
        self.assertEqual(harness.timeout_phase_for_artifact("gameplay", True, None, None, None, None), "startup")
        self.assertEqual(
            harness.timeout_phase_for_artifact(
                "gameplay",
                True,
                None,
                {"status": "failed", "worldEntered": False, "measuredFrameCount": 0},
                None,
                None,
            ),
            "readiness",
        )
        self.assertEqual(
            harness.timeout_phase_for_artifact(
                "gameplay",
                True,
                None,
                {"status": "warming", "worldEntered": True, "measuredFrameCount": 0},
                None,
                None,
            ),
            "warmup",
        )
        self.assertEqual(
            harness.timeout_phase_for_artifact(
                "gameplay",
                True,
                None,
                {"status": "measuring", "worldEntered": True, "measuredFrameCount": 10, "measureFramesRequested": 60},
                None,
                None,
            ),
            "measurement",
        )
        self.assertEqual(
            harness.timeout_phase_for_artifact(
                "capture",
                True,
                None,
                None,
                None,
                {"status": "partial"},
            ),
            "measurement",
        )

    def test_supported_graphics_cli_surface_is_exact(self) -> None:
        devutils = Path(__file__).resolve().parents[1]
        perf_clis = {path.name for path in (devutils / "PerfAudit").glob("*.py") if not path.name.startswith("test_")}
        audit_clis = {path.name for path in (devutils / "Audit").glob("*.py") if not path.name.startswith("test_")}
        self.assertEqual(perf_clis, {"Gameplay.py", "Subsystem.py", "Matrix.py", "MeshingCorpus.py", "RustMigration.py", "StorageComparison.py"})
        self.assertEqual(audit_clis, {"Capture.py", "VulkanParity.py", "VulkanCoverage.py"})

    def test_deleted_graphics_directories_do_not_exist(self) -> None:
        devutils = Path(__file__).resolve().parents[1]
        for name in ("PerfTesting", "graphics", "VulkanParityAudit", "VulkanCoverageAudit"):
            self.assertFalse((devutils / name).exists(), name)

    def test_shared_graphics_functionality_comes_from_common(self) -> None:
        self.assertEqual(Path(harness.__file__).resolve().parent.name, "Common")

    def test_no_source_references_deleted_graphics_paths(self) -> None:
        repo = Path(__file__).resolve().parents[2]
        removed_dirs = ("PerfTesting", "graphics", "VulkanParityAudit", "VulkanCoverageAudit")
        retired_scripts = (
            "Run" + "Dev" + "Capture",
            "Run" + "Backend" + "Perf" + "Compare",
            "Run" + "Vulkan" + "Perf" + "Audit",
            "vulkan" + "_parity" + "_audit",
            "vulkan" + "_coverage" + "_audit",
        )
        forbidden = tuple(
            f"DevUtils{separator}{name}" for name in removed_dirs for separator in ("/", "\\")
        ) + retired_scripts
        skipped_dirs = {".git", ".gradle", "build", "run", "logs", "__pycache__", ".idea"}
        checked_suffixes = {".py", ".sh", ".ps1", ".md", ".gradle", ".properties", ".json", ".txt"}
        offenders: list[str] = []
        self_path = Path(__file__).resolve()
        for path in repo.rglob("*"):
            if not path.is_file():
                continue
            if path.resolve() == self_path:
                continue
            if any(part in skipped_dirs for part in path.parts):
                continue
            if path.suffix not in checked_suffixes and path.name not in {"gradlew", "settings.gradle", "build.gradle"}:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            for pattern in forbidden:
                if pattern in text:
                    offenders.append(f"{path.relative_to(repo)}:{pattern}")
        self.assertEqual(offenders, [])

    def test_internal_only_launcher_enforcement(self) -> None:
        repo = Path(__file__).resolve().parents[2]
        result = subprocess.run(
            [sys.executable, str(repo / "DevUtils" / "Common" / "capture_runner.py"), "--help"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        self.assertEqual(result.returncode, 2)
        self.assertIn("internal capture engine", result.stderr)

    def test_supported_smoke_clis_launch_in_dry_run(self) -> None:
        repo = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            frozen = temp_path / "frozen"
            frozen.mkdir()
            commands = (
                repo / "DevUtils" / "PerfAudit" / "Gameplay.py",
                repo / "DevUtils" / "PerfAudit" / "Subsystem.py",
                repo / "DevUtils" / "PerfAudit" / "Matrix.py",
                repo / "DevUtils" / "Audit" / "Capture.py",
            )
            for script in commands:
                artifact_dir = temp_path / script.stem
                result = subprocess.run(
                    [
                        sys.executable,
                        str(script),
                        "--profile",
                        "smoke",
                        "--mode",
                        "current-opengl-shaders-off",
                        "--artifact-dir",
                        str(artifact_dir),
                        "--frozen-repo",
                        str(frozen),
                        "--dry-run",
                    ],
                    cwd=repo,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    check=False,
                    timeout=20,
                )
                self.assertEqual(result.returncode, 0, f"{script}: {result.stderr}\n{result.stdout}")
                self.assertTrue((artifact_dir / harness.MANIFEST_NAME).is_file(), script)

    def test_legacy_artifact_without_runtime_profile_loads(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = artifact_for(root, harness.MATRIX_MODES[0])
            artifact.pop("runtime_profile", None)
            path = root / harness.ARTIFACT_NAME
            harness.write_artifact(path, artifact)
            loaded = harness.load_cross_repo_artifact(path)
            self.assertEqual(loaded["schema"], harness.SCHEMA)

    def test_fingerprint_stability(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            mode = harness.MATRIX_MODES[0]
            first = artifact_for(root, mode)
            second = artifact_for(root, mode)
            self.assertEqual(first["benchmark_fingerprint"]["hash"], second["benchmark_fingerprint"]["hash"])

    def test_workload_mismatch_rejection(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            left = artifact_for(root, harness.MATRIX_MODES[0], world="Origin")
            right = artifact_for(root, harness.MATRIX_MODES[1], world="OtherWorld")
            comparison = harness.compare_workloads(left, right)
            self.assertIn({"path": "world", "left": "Origin", "right": "OtherWorld"}, comparison["differences"])
            with self.assertRaises(ValueError):
                harness.reject_mismatched_workloads(left, right)

    def test_fingerprint_parity_accepts_materially_equivalent_runtime_counts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            left = gameplay_artifact_for(root, harness.MATRIX_MODES[0])
            right = gameplay_artifact_for(root, harness.MATRIX_MODES[4])
            signature = right["benchmark_fingerprint"]["workload_signature"]
            signature["runtime"]["loaded_chunks"] = 124
            signature["workload_families"]["terrain"]["calls_per_frame"] = 31.0
            comparison = harness.compare_workloads(left, right)
            self.assertTrue(comparison["comparable"], comparison)

    def test_fingerprint_rejects_material_workload_family_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            left = gameplay_artifact_for(root, harness.MATRIX_MODES[0])
            right = gameplay_artifact_for(root, harness.MATRIX_MODES[4])
            signature = right["benchmark_fingerprint"]["workload_signature"]
            signature["workload_families"]["terrain"]["present"] = False
            signature["workload_families"]["terrain"]["bucket"] = "zero"
            comparison = harness.compare_workloads(left, right)
            self.assertFalse(comparison["comparable"])
            self.assertTrue(any(diff["path"].startswith("workload_families.terrain") for diff in comparison["differences"]))

    def test_deterministic_camera_metadata_is_in_fingerprint(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = artifact_for(root, harness.MATRIX_MODES[0])
            camera = artifact["benchmark_fingerprint"]["workload_signature"]["camera"]
            self.assertEqual(camera["status"], "complete")
            self.assertEqual(camera["window"], {"width": 1280, "height": 720})
            self.assertEqual(len(camera["poses"]), 2)

    def test_cross_repository_artifact_loading(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = artifact_for(root, harness.MATRIX_MODES[4])
            path = root / "foreign" / harness.ARTIFACT_NAME
            harness.write_artifact(path, artifact)
            loaded = harness.load_cross_repo_artifact(path)
            self.assertEqual(loaded["repository"]["target"], "frozen")

    def test_current_frozen_repository_routing(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            current = fake_repo(root, "current")
            frozen = fake_repo(root, "frozen")
            args = Namespace(frozen_repo=str(frozen.root))
            original_repo_root = harness.repo_root
            try:
                harness.repo_root = lambda start=None: current.root  # type: ignore[assignment]
                targets = harness.select_targets(args)
            finally:
                harness.repo_root = original_repo_root  # type: ignore[assignment]
            self.assertEqual(targets["current"].root, current.root)
            self.assertEqual(targets["frozen"].root, frozen.root)

    def test_implementation_attribution(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            mode = harness.MATRIX_MODES[2]
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan", shaders="off")
            (capture / "runClient_20260101_000000.log").write_text("Rust Vulkan native queue ready\n", encoding="utf-8")
            artifact = harness.normalize_capture_artifact(target, mode, capture, "correctness", True, ["fake"], 0, False)
            self.assertEqual(artifact["implementation_attribution"], "rust-vulkan")

    def test_process_cleanup(self) -> None:
        process = subprocess.Popen(
            [sys.executable, "-c", "import time; time.sleep(30)"],
            start_new_session=(harness.platform_name() != "windows"),
        )
        try:
            harness.terminate_process_tree(process)
            process.wait(timeout=5)
            self.assertIsNotNone(process.returncode)
        finally:
            if process.poll() is None:
                process.kill()

    def test_repo_scoped_orphan_cleanup(self) -> None:
        if harness.platform_name() == "windows":
            self.skipTest("repo process cleanup is POSIX-only")
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            process = subprocess.Popen(
                [sys.executable, "-c", "import time; time.sleep(30)", str(root), "KnotClient"],
                start_new_session=True,
            )
            try:
                killed = harness.cleanup_repo_processes(root)
                process.wait(timeout=5)
                self.assertIn(process.pid, killed)
                self.assertIsNotNone(process.returncode)
            finally:
                if process.poll() is None:
                    process.kill()

    def test_run_mode_timeout_marks_artifact_and_cleans_process(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            (target.root / "DevUtils" / "Common" / "capture_runner.py").write_text(
                "import time\ntime.sleep(30)\n",
                encoding="utf-8",
            )
            args = Namespace(
                profile="smoke",
                workload_profile="correctness",
                validation="off",
                client_args="",
                world="Origin",
                max_secs=1,
                dump_secs=1,
                client_rss_limit_mb=128,
                diagnostic=False,
                dry_run=False,
                timeout_seconds=1,
                startup_timeout_seconds=1,
                readiness_timeout_seconds=1,
                warmup_timeout_seconds=1,
                measurement_timeout_seconds=1,
                shutdown_timeout_seconds=1,
                cleanup_timeout_seconds=1,
                warmup_frames=0,
                measure_frames=1,
                settle_frames=0,
                max_settle_frames=1,
                subsystem_iterations=1,
            )
            result = harness.run_mode(target, harness.MATRIX_MODES[0], root / "artifacts", args, "capture")
            self.assertFalse(result.success)
            self.assertTrue(result.timed_out)
            artifact = harness.load_cross_repo_artifact(Path(result.artifact_path))
            self.assertTrue(artifact["capture"]["timed_out"])

    def test_rss_guard_behavior(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            meta = capture / "meta_20260101_000000.txt"
            meta.write_text(meta.read_text(encoding="utf-8") + "memory_guard_triggered=true\n", encoding="utf-8")
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "correctness", False, [], 124, False)
            self.assertTrue(artifact["metrics"]["rss_and_native_memory"]["rss_guard_triggered"])
            self.assertTrue(artifact["validation"]["rss_guard_triggered"])

    def test_malformed_incomplete_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "bad.json"
            path.write_text("{not json", encoding="utf-8")
            with self.assertRaises(ValueError):
                harness.load_cross_repo_artifact(path)
            path.write_text(json.dumps({"schema": harness.SCHEMA}), encoding="utf-8")
            with self.assertRaises(ValueError):
                harness.load_cross_repo_artifact(path)

    def test_baseline_reuse_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = artifact_for(root, harness.MATRIX_MODES[0])
            fingerprint = artifact["benchmark_fingerprint"]
            self.assertTrue(harness.baseline_reusable(artifact, fingerprint))
            changed = json.loads(json.dumps(fingerprint))
            changed["workload_signature"]["world"] = "Different"
            self.assertFalse(harness.baseline_reusable(artifact, changed))

    def test_synthetic_frame_durations_drive_fps_and_percentiles(self) -> None:
        samples = [10_000_000, 20_000_000, 40_000_000, 100_000_000]
        self.assertEqual(harness.frame_nanos_to_millis(samples), [10.0, 20.0, 40.0, 100.0])
        fps = harness.fps_from_frame_nanos(samples)
        self.assertEqual(fps, [100.0, 50.0, 25.0, 10.0])
        summary = harness.summarize_distribution(harness.frame_nanos_to_millis(samples))
        self.assertEqual(summary["median"], 30.0)
        self.assertEqual(summary["p95"], 100.0)
        self.assertEqual(summary["worst"], 100.0)

    def test_log_fps_is_ignored_without_frame_samples(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = artifact_for(root, harness.MATRIX_MODES[0])
            self.assertEqual(artifact["metrics"]["fps"]["count"], 0)
            self.assertIsNone(artifact["metrics"]["fps"]["median"])
            self.assertIn("never parsed", artifact["metrics"]["source"]["fps_derivation"])

    def test_gameplay_without_frame_samples_is_incomplete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="gameplay")
            self.assertFalse(artifact["validation"]["complete"])
            self.assertFalse(artifact["validation"]["workload_entered"])
            self.assertIn("title-screen", " ".join(artifact["validation"]["messages"]))

    def test_gameplay_title_screen_frame_artifact_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(capture, [16_000_000, 17_000_000])
            frame_path = capture / "graphics_frame_benchmark_20260101_000000.json"
            frame_doc = json.loads(frame_path.read_text(encoding="utf-8"))
            frame_doc["worldEntered"] = False
            frame_path.write_text(json.dumps(frame_doc), encoding="utf-8")
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="gameplay")
            self.assertFalse(artifact["validation"]["complete"])
            self.assertFalse(artifact["validation"]["workload_entered"])

    def test_subsystem_requires_complete_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="subsystem")
            self.assertFalse(artifact["validation"]["complete"])
            self.assertFalse(artifact["validation"]["subsystem_complete"])
            write_subsystem_benchmark(capture)
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="subsystem")
            self.assertTrue(artifact["validation"]["complete"])
            self.assertTrue(artifact["validation"]["subsystem_complete"])

    def test_frame_benchmark_artifact_supplies_normalized_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(capture, [16_666_667, 33_333_333])
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="gameplay")
            self.assertEqual(artifact["metrics"]["frame_time_ms"]["count"], 2)
            self.assertAlmostEqual(artifact["metrics"]["fps"]["median"], 45.0, places=2)
            self.assertEqual(artifact["metrics"]["java_allocation_and_gc"]["gc_events"], 1)

    def test_smoke_displayed_fps_mismatch_does_not_fail_sampler_validity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(capture, [16_000_000, 17_000_000, 18_000_000])
            frame_path = capture / "graphics_frame_benchmark_20260101_000000.json"
            frame_doc = json.loads(frame_path.read_text(encoding="utf-8"))
            frame_doc["worldEntered"] = True
            frame_doc["validity"] = {
                "wallClockCheckPassed": True,
                "displayedFpsCheckRequired": False,
                "displayedFpsCheckPassed": False,
            }
            frame_path.write_text(json.dumps(frame_doc), encoding="utf-8")
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="gameplay")
            self.assertTrue(artifact["validation"]["frame_sampler_validity_passed"])

    def test_shell_capture_command_forces_quickplay_world(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "frozen")
            (target.root / "DevUtils" / "Common" / "capture_runner.py").unlink()
            (target.root / "DevUtils" / "Common" / "capture_runner.sh").write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            args = Namespace(
                profile="smoke",
                validation="off",
                client_args="",
                world="Origin",
                max_secs=1,
                dump_secs=1,
                client_rss_limit_mb=128,
                diagnostic=False,
                warmup_frames=0,
                measure_frames=1,
                settle_frames=0,
                max_settle_frames=1,
                subsystem_iterations=1,
            )
            command, _ = harness.build_capture_command(target, harness.MATRIX_MODES[4], root / "capture", "gameplay", args, "gameplay")
            joined = " ".join(command)
            self.assertIn("--quickPlaySingleplayer=Origin", joined)

    def test_warmup_exclusion_is_represented_by_measured_samples_only(self) -> None:
        samples_after_warmup = [25_000_000, 25_000_000, 50_000_000]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(capture, samples_after_warmup)
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="gameplay")
            self.assertEqual(artifact["metrics"]["frame_time_ms"]["count"], len(samples_after_warmup))
            self.assertEqual(artifact["metrics"]["frame_time_ms"]["total"], 100.0)

    def test_nested_and_exclusive_phase_timing_are_separate(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(capture, [16_000_000, 17_000_000])
            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="gameplay")
            exclusive = artifact["metrics"]["cpu_phase_timings"]["game.rendering"]
            nested = artifact["metrics"]["nested_cpu_phase_timings"]["game.rendering"]
            self.assertEqual(exclusive["total"], 20.0)
            self.assertEqual(nested["total"], 30.0)
            self.assertLess(exclusive["total"], nested["total"])
            self.assertEqual(artifact["metrics"]["phase_family_timings"]["game-client"]["total"], 20.0)
            self.assertEqual(artifact["metrics"]["nested_phase_family_timings"]["game-client"]["total"], 30.0)

    def test_repeated_run_variance_calculation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = []
            for index, median_ns in enumerate((16_000_000, 17_000_000, 18_000_000), 1):
                artifact = gameplay_artifact_for(root / str(index), harness.MATRIX_MODES[0], samples=[median_ns, median_ns, median_ns])
                path = root / f"artifact-{index}.json"
                harness.write_artifact(path, artifact)
                paths.append(path)
            report = harness.variance_report(paths, 0.20)
            self.assertTrue(report["passed"])
            self.assertLess(report["rows"][0]["relative_span"], 0.20)

    def test_instrumentation_overhead_calculation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            control = gameplay_artifact_for(root / "control", harness.MATRIX_MODES[0], samples=[10_000_000, 10_000_000])
            diagnostic = gameplay_artifact_for(root / "diagnostic", harness.MATRIX_MODES[0], samples=[11_000_000, 11_000_000])
            control_path = root / "control.json"
            diagnostic_path = root / "diagnostic.json"
            harness.write_artifact(control_path, control)
            harness.write_artifact(diagnostic_path, diagnostic)
            report = harness.instrumentation_overhead_report([control_path], [diagnostic_path], 0.15)
            self.assertTrue(report["passed"])
            self.assertAlmostEqual(report["rows"][0]["relative_overhead"], 0.10)

    def test_stable_workload_family_summaries_bucket_raw_counts(self) -> None:
        frame_doc = {
            "measuredFrameCount": 10,
            "submittedWorkCounts": {
                "sodium-terrain": 100,
                "DistantHorizons": 20,
                "pipeline-cache": 1,
            },
        }
        summary = harness.stable_workload_family_summary(frame_doc, "")
        self.assertEqual(summary["terrain"]["bucket"], "high")
        self.assertEqual(summary["distant-horizons"]["calls_per_frame"], 2.0)
        self.assertTrue(summary["pipelines-resources"]["present"])

    def test_incomplete_vulkan_run_diagnosis(self) -> None:
        diagnosis = harness.incomplete_run_diagnosis(
            "gameplay",
            "readiness",
            {
                "status": "failed",
                "worldEntered": False,
                "measuredFrameCount": 0,
                "measureFramesRequested": 300,
                "failureReason": "timed out waiting for gameplay entry",
                "lastReadinessBlocker": "screen=TitleScreen",
            },
            None,
            None,
            {"crash": 0, "gl_error": 0, "vuid": 0, "device_loss": 0, "rss_guard": 0, "orphan_process": 0},
            False,
            "java-vulkan",
        )
        self.assertEqual(diagnosis["root_cause"], "readiness-phase")
        self.assertIn("readiness=screen=TitleScreen", diagnosis["evidence"])


if __name__ == "__main__":
    unittest.main()
