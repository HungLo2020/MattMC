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


def fake_repo_path(root: Path, name: str) -> Path:
    return fake_repo(root, name).root


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


def write_frame_benchmark(capture_dir: Path, samples: list[int], rust_gal_line: str | None = None) -> None:
    (capture_dir / "graphics_frame_benchmark_20260101_000000.json").write_text(
        json.dumps(
            {
                "schema": "mattmc-graphics-frame-benchmark-v1",
                "workloadCounterDefinitionVersion": "phase-family-v2",
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
                    "sodium.terrain.setup": {"count": len(samples), "total": 8_000_000, "worst": 4_000_000},
                    "sodium.terrain.draw": {"count": len(samples) * 2, "total": 10_000_000, "worst": 5_000_000},
                    "distant-horizons.lod-render": {"count": len(samples), "total": 4_000_000, "worst": 2_000_000},
                    "iris.composite-final": {"count": 2, "total": 6_000_000, "worst": 4_000_000},
                },
                "rustGalSliceMetricsLine": rust_gal_line,
                "nestedPhaseNanos": {
                    "game.rendering": {"count": 2, "total": 30_000_000, "worst": 18_000_000},
                    "sodium.terrain.setup": {"count": len(samples), "total": 8_000_000, "worst": 4_000_000},
                    "sodium.terrain.draw": {"count": len(samples) * 2, "total": 10_000_000, "worst": 5_000_000},
                    "distant-horizons.lod-render": {"count": len(samples), "total": 4_000_000, "worst": 2_000_000},
                    "iris.composite-final": {"count": 2, "total": 6_000_000, "worst": 4_000_000},
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


def make_validation_capture(capture_dir: Path, *, workload: bool = True, run_id: str = "20260101_000000", log_run_id: str | None = None) -> None:
    write_capture(capture_dir, backend="vulkan", shaders="off")
    meta = capture_dir / "meta_20260101_000000.txt"
    meta.write_text(
        meta.read_text(encoding="utf-8")
        .replace("validation_mode=off", "validation_mode=standard")
        .replace("graphics_run_type=clean-performance", "graphics_run_type=vulkan-validation")
        .replace("validation_profile=off", "validation_profile=routine")
        + "\n".join(
            [
                f"run_id={run_id}",
                "validation_enabled=true",
                "validation_layer_available=true",
                "validation_layer_manifest=/usr/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json",
                "vk_instance_layers=VK_LAYER_KHRONOS_validation",
                "vk_add_layer_path=/usr/share/vulkan/explicit_layer.d",
                "vk_loader_debug=error,warn,layer",
                "vk_layer_settings=validate_sync=true,validate_best_practices=true",
                "gradle_pid=11",
                "client_pid=12",
                "exit_code=0",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    for stale in capture_dir.glob("runClient_*.log"):
        stale.unlink()
    for stale in capture_dir.glob("validation_events_*.log"):
        stale.unlink()
    actual_log_id = log_run_id or run_id
    workload_text = (
        "Sodium Vulkan chunk render probe#1 pass=minecraft:pipeline/solid batchDraws=4 submittedDraws=4 submittedIndices=24\n"
        "Vulkan beginFramebufferRenderPass colorCount=1 depthPresent=true renderPass=0x1\n"
        "drawIndexed descriptor pipeline vkQueueSubmit\n"
        if workload
        else ""
    )
    (capture_dir / f"runClient_{actual_log_id}.log").write_text(
        "[Vulkan Loader] INFO | LAYER:   Insert instance layer \"VK_LAYER_KHRONOS_validation\" (libVkLayer_khronos_validation.so)\n"
        + workload_text,
        encoding="utf-8",
    )
    (capture_dir / f"validation_events_{actual_log_id}.log").write_text(
        "[Vulkan Loader] WARNING | LAYER: env var 'VK_INSTANCE_LAYERS' defined and adding layers \"VK_LAYER_KHRONOS_validation\"\n",
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
        self.assertIn("report_shader_compiler", text)
        self.assertIn("tracy-csvexport", text)

    def test_required_tool_fingerprint_uses_shaderc_not_glslang(self) -> None:
        fingerprint = harness.tool_fingerprint()
        self.assertIn("shader_compiler", fingerprint)
        self.assertEqual(fingerprint["shader_compiler"]["crate"], "shaderc")
        self.assertNotIn("glslangValidator", fingerprint)

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
        self.assertEqual(by_vuid["VUID-vkQueueSubmit-pSubmits-00074"]["first_occurrence"], 1)
        self.assertEqual(by_vuid["VUID-vkQueueSubmit-pSubmits-00074"]["last_occurrence"], 2)
        self.assertEqual(by_vuid["VUID-vkCmdDraw-None-08600"]["draw"], 12)
        self.assertEqual(by_vuid["VUID-vkQueueSubmit-pSubmits-00074"]["semantic_operation"], "queue.submit")
        self.assertEqual(len(by_vuid["VUID-vkQueueSubmit-pSubmits-00074"]["objects"]), 2)

    def test_old_and_new_validation_formats_parse_consistently_and_keep_full_text(self) -> None:
        long_tail = "pipeline-context=" + ("terrain/" * 120)
        text = "\n".join(
            [
                f"Validation Error: [ VUID-VkGraphicsPipelineCreateInfo-layout-07988 ] Object 0: handle = 0xabc, type = VK_OBJECT_TYPE_PIPELINE_LAYOUT, name = terrain-layout {long_tail}",
                "WARNING VALIDATION VUID-vkCmdDrawIndexed-None-08600 MessageID=DrawIndexed frame=3 pass=2 draw=9 operation=sodium.terrain.draw",
            ]
        )
        findings = harness.parse_validation_findings(text)
        by_vuid = {finding["vuid"]: finding for finding in findings}
        self.assertIn("VUID-VkGraphicsPipelineCreateInfo-layout-07988", by_vuid)
        self.assertIn("VUID-vkCmdDrawIndexed-None-08600", by_vuid)
        self.assertIn(long_tail, by_vuid["VUID-VkGraphicsPipelineCreateInfo-layout-07988"]["message"])
        self.assertEqual(by_vuid["VUID-VkGraphicsPipelineCreateInfo-layout-07988"]["category"], "concrete_api_vuid")
        self.assertEqual(by_vuid["VUID-vkCmdDrawIndexed-None-08600"]["semantic_operation"], "sodium.terrain.draw")

    def test_multiline_validation_context_parsing(self) -> None:
        text = "\n".join(
            [
                "Validation Error: [ VUID-vkCmdPipelineBarrier-srcStageMask-03937 ] MessageID = 0x1234",
                "\tObjects: 1",
                "\tObject 0: handle = 0xabc, type = VK_OBJECT_TYPE_COMMAND_BUFFER, name = terrain-main",
                "\tframe=9 pass=2 draw=40 operation=gal.pass.barrier",
            ]
        )
        finding = harness.parse_validation_findings(text)[0]
        self.assertEqual(finding["vuid"], "VUID-vkCmdPipelineBarrier-srcStageMask-03937")
        self.assertEqual(finding["message_id"], "0x1234")
        self.assertEqual(finding["objects"][0]["type"], "VK_OBJECT_TYPE_COMMAND_BUFFER")
        self.assertEqual(finding["objects"][0]["debug_name"], "terrain-main")
        self.assertIn("Object 0", finding["message"])

    def test_loader_only_validation_is_not_labeled_clean_but_records_workload_proof(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            capture = root / "capture"
            make_validation_capture(capture, workload=True)
            artifact = harness.normalize_capture_artifact(
                target,
                harness.MATRIX_MODES[2],
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
            )
            findings = artifact["metrics"]["validation_findings"]
            self.assertEqual(findings["concrete_vuid_count"], 0)
            self.assertEqual(findings["categories"]["loader_environment_notice"], 2)
            self.assertTrue(findings["proof"]["layer_loaded"])
            self.assertTrue(findings["proof"]["meaningful_vulkan_workload"])
            self.assertFalse(artifact["validation"]["vulkan_validation_clean"])

    def test_validation_proof_uses_subsystem_workload_counts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            capture = root / "capture"
            make_validation_capture(capture, workload=False)
            (capture / "graphics_subsystem_benchmark_20260101_000000.json").write_text(
                json.dumps(
                    {
                        "schema": "mattmc-graphics-subsystem-benchmark-v1",
                        "status": "complete",
                        "backend": "rust-vulkan",
                        "workloads": [
                            {
                                "name": "rust-bridge.indexed-textured-depth-blend-resource-sets-transfer-readback",
                                "status": "ok",
                                "counts": {
                                    "draw": 2,
                                    "dispatch": 0,
                                    "pass": 2,
                                    "transfer": 14,
                                    "pipeline": 1,
                                    "resource": 22,
                                    "descriptor": 1,
                                },
                                "lastSubmission": 2,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                harness.MATRIX_MODES[6],
                capture,
                "subsystem",
                True,
                ["fake"],
                0,
                False,
            )

            proof = artifact["metrics"]["validation_findings"]["proof"]
            self.assertTrue(proof["meaningful_vulkan_workload"])
            self.assertEqual(2, proof["workload_counts"]["draw"])
            self.assertEqual(14, proof["workload_counts"]["transfer"])
            self.assertEqual(2, proof["workload_counts"]["vulkan_submission"])
            self.assertEqual(22, proof["creation_counts"]["resource"])

    def test_zero_vuid_validation_without_workload_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            capture = root / "capture"
            make_validation_capture(capture, workload=False)
            artifact = harness.normalize_capture_artifact(
                target,
                harness.MATRIX_MODES[2],
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
            )
            self.assertFalse(artifact["metrics"]["validation_findings"]["proof"]["meaningful_vulkan_workload"])
            self.assertFalse(artifact["validation"]["complete"])
            self.assertIn("meaningful Java Vulkan workload", " ".join(artifact["validation"]["messages"]))

    def test_validation_artifact_rejects_logs_from_another_run(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            capture = root / "capture"
            make_validation_capture(capture, workload=True, run_id="20260101_000000", log_run_id="20260101_111111")
            artifact = harness.normalize_capture_artifact(
                target,
                harness.MATRIX_MODES[2],
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
            )
            proof = artifact["metrics"]["validation_findings"]["proof"]
            self.assertFalse(proof["artifact_log_association"]["all_named_logs_match_run"])
            self.assertFalse(artifact["validation"]["complete"])

    def test_tracy_summary_extraction_and_empty_rejection(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            capture = temp / "trace.tracy"
            capture.write_bytes(b"not-empty")
            exporter = temp / "tracy-csvexport"
            exporter.write_text(
                """#!/usr/bin/env python3
import sys
if "--messages" in sys.argv:
    print("MessageName,total_ns")
    print("ffi.shaderc.compile source=test bytes=32,100")
    print("gal.submission producer=java-subsystem backend=rust-vulkan iteration=2 id=17,100")
    print("gal.frame.acquire backend=vulkan correlation=41 frame=5 image=1 window=99,100")
    print("gal.frame.present backend=vulkan correlation=41 frame=5 submission=17 status=Presented window=99,100")
elif "--self" in sys.argv:
    print("name,src_file,src_line,total_ns,total_perc,counts,mean_ns,min_ns,max_ns,std_ns")
    print("java.frame.render-production,GraphicsFrameBenchmark.java,1,600,6,2,300,200,400,10")
    print("ffi.rust.shaderc.compile,NativeShadercCompiler.java,1,120,1.2,1,120,120,120,0")
else:
    print("name,src_file,src_line,total_ns,total_perc,counts,mean_ns,min_ns,max_ns,std_ns")
    print("java.frame.render-production,GraphicsFrameBenchmark.java,1,1000,10,2,500,400,600,20")
    print("ffi.rust.shaderc.compile,NativeShadercCompiler.java,1,150,1.5,1,150,150,150,0")
""",
                encoding="utf-8",
            )
            exporter.chmod(0o755)
            original = harness.local_tracy_csvexport_path
            try:
                harness.local_tracy_csvexport_path = lambda: str(exporter)  # type: ignore[assignment]
                summary_path = harness.extract_tracy_summary(temp, capture)
                summary = json.loads(summary_path.read_text(encoding="utf-8"))
                self.assertEqual(summary["status"], "complete")
                self.assertEqual(summary["zone_count"], 2)
                self.assertIn("java.frame.render-production", summary["major_zones"])
                self.assertEqual(summary["ffi"]["shaderc_message_count"], 1)
                self.assertEqual(summary["call_counts"]["submission_correlations"], 1)
                self.assertEqual(summary["call_counts"]["frame_correlations"], 2)
                self.assertEqual(summary["correlations"]["submissions"][0]["submission"], 17)
                self.assertEqual(summary["correlations"]["frames"][0]["correlation"], 41)
                self.assertEqual(summary["correlations"]["frames"][1]["submission"], 17)
                empty_path = temp / "empty.tracy"
                empty_path.write_bytes(b"")
                failed = json.loads(harness.extract_tracy_summary(temp, empty_path).read_text(encoding="utf-8"))
                self.assertEqual(failed["status"], "failed")
            finally:
                harness.local_tracy_csvexport_path = original  # type: ignore[assignment]

    def test_tracy_listener_discovery_parses_port_conflicts(self) -> None:
        output = (
            'LISTEN 0 4096 127.0.0.1:8086 0.0.0.0:* users:(("java",pid=111,fd=42))\n'
            'LISTEN 0 4096 127.0.0.1:8087 0.0.0.0:* users:(("java",pid=111,fd=43))\n'
            'LISTEN 0 4096 127.0.0.1:9000 0.0.0.0:* users:(("java",pid=111,fd=44))\n'
        )
        listeners = harness.parse_ss_tracy_listeners(output, 8086, 8110)
        self.assertEqual([listener.port for listener in listeners], [8086, 8087])
        self.assertEqual(listeners[0].process, "java")
        self.assertEqual(listeners[0].pid, 111)

    def test_tracy_role_detection_distinguishes_java_and_rust(self) -> None:
        rust_roles = harness.tracy_role_detection(
            {"opengl.backend.submit": {}, "opengl.lowering.draw-indexed": {}},
            ["MattMC Rust VulkanicGAL OpenGL Tracy client started", "gal.submission backend=opengl id=7"],
        )
        java_roles = harness.tracy_role_detection(
            {"java.frame.render-production": {}, "rust-gal.gui-frame.abi-packing": {}},
            ["gal.frame.deferred producer=gui.frame frame=2 submission=7 batches=3"],
        )
        self.assertTrue(rust_roles["rust"])
        self.assertFalse(rust_roles["java"])
        self.assertTrue(java_roles["java"])
        self.assertFalse(java_roles["rust"])

    def test_tracy_summary_rejects_java_only_when_rust_required(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            capture = temp / "trace.tracy"
            capture.write_bytes(b"not-empty")
            exporter = temp / "tracy-csvexport"
            exporter.write_text(
                """#!/usr/bin/env python3
import sys
if "--messages" in sys.argv:
    print("MessageName,total_ns")
    print("gal.frame.deferred producer=gui.frame frame=2 submission=7 batches=3,100")
elif "--self" in sys.argv:
    print("name,src_file,src_line,total_ns,total_perc,counts,mean_ns,min_ns,max_ns,std_ns")
    print("rust-gal.gui-frame.abi-packing,RustGalGuiRenderer.java,1,80,8,1,80,80,80,0")
else:
    print("name,src_file,src_line,total_ns,total_perc,counts,mean_ns,min_ns,max_ns,std_ns")
    print("rust-gal.gui-frame.abi-packing,RustGalGuiRenderer.java,1,100,10,1,100,100,100,0")
""",
                encoding="utf-8",
            )
            exporter.chmod(0o755)
            original = harness.local_tracy_csvexport_path
            try:
                harness.local_tracy_csvexport_path = lambda: str(exporter)  # type: ignore[assignment]
                summary_path = harness.extract_tracy_summary(temp, capture, require_rust_zones=True)
                summary = json.loads(summary_path.read_text(encoding="utf-8"))
                self.assertEqual(summary["status"], "failed")
                self.assertTrue(summary["role_detection"]["java"])
                self.assertFalse(summary["role_detection"]["rust"])
                self.assertIn("Rust VulkanicGAL", summary["failure"])
            finally:
                harness.local_tracy_csvexport_path = original  # type: ignore[assignment]

    def test_tracy_collection_rejects_zero_byte_and_accepts_rust_zone(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            rust_capture = temp / "rust.tracy"
            rust_capture.write_bytes(b"rust")
            empty_capture = temp / "empty.tracy"
            empty_capture.write_bytes(b"")
            rust_summary = {
                "status": "complete",
                "capture_path": str(rust_capture),
                "size_bytes": rust_capture.stat().st_size,
                "zone_count": 3,
                "zones": {"opengl.backend.submit": {}},
                "major_zones": {"opengl.backend.submit": {}},
                "role_detection": {"rust": True, "java": False},
            }
            empty_summary = {
                "status": "failed",
                "capture_path": str(empty_capture),
                "size_bytes": 0,
                "zone_count": 0,
                "role_detection": {"rust": False, "java": False},
            }
            accepted = json.loads(
                harness.write_tracy_collection_summary(
                    temp,
                    [empty_summary, rust_summary],
                    require_rust_zones=True,
                    require_java_zones=False,
                ).read_text(encoding="utf-8")
            )
            self.assertEqual(accepted["status"], "complete")
            self.assertTrue(accepted["role_detection"]["rust"])
            rejected = json.loads(
                harness.write_tracy_collection_summary(
                    temp,
                    [empty_summary],
                    require_rust_zones=True,
                    require_java_zones=False,
                ).read_text(encoding="utf-8")
            )
            self.assertEqual(rejected["status"], "failed")
            self.assertIn("Rust VulkanicGAL", rejected["failure"])

    def test_renderdoc_summary_replay_uses_renderdoccmd_not_qrenderdoc(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            capture = temp / "capture.rdc"
            capture.write_bytes(b"rdc")
            qrenderdoc = temp / "qrenderdoc"
            qrenderdoc.write_text("#!/usr/bin/env sh\nprintf 'qrenderdoc must not run\\n' >&2\nexit 99\n", encoding="utf-8")
            qrenderdoc.chmod(0o755)
            renderdoccmd = temp / "renderdoccmd"
            renderdoccmd.write_text(
                "#!/usr/bin/env sh\n"
                "if [ \"$1\" = vulkanlayer ]; then printf '%s\\n' 'RenderDoc layer correctly registered.'; exit 0; fi\n"
                "printf '%s\\n' 'EID 1 BeginPass'\n"
                "printf '%s\\n' 'EID 2 DrawIndexed'\n",
                encoding="utf-8",
            )
            renderdoccmd.chmod(0o755)
            original_cmd = harness.local_renderdoccmd_path
            try:
                harness.local_renderdoccmd_path = lambda: str(renderdoccmd)  # type: ignore[assignment]
                summary_path = harness.replay_renderdoc_summary(temp, capture)
                summary = json.loads(summary_path.read_text(encoding="utf-8"))
                self.assertEqual(summary["status"], "complete")
                self.assertTrue(summary["diagnosis"]["cli_only_replay"])
                self.assertTrue(summary["diagnosis"]["fallback_text_replay"])
                self.assertEqual(summary["draw_count"], 1)
            finally:
                harness.local_renderdoccmd_path = original_cmd  # type: ignore[assignment]

    def test_renderdoc_extractor_handles_drawcall_id_api_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            script = Path(temp_dir) / "extract.py"
            harness.write_renderdoc_python_extractor(script)
            text = script.read_text(encoding="utf-8")

            self.assertIn("def action_drawcall_id(action):", text)
            self.assertIn('"drawcallId", "drawcallID", "drawcall_id"', text)
            self.assertIn('"drawcall_id": action_drawcall_id(action)', text)
            self.assertIn("def action_name(action):", text)
            self.assertIn('"name": action_name(action)', text)

    def test_renderdoc_layer_failure_diagnosis(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            renderdoccmd = temp / "renderdoccmd"
            renderdoccmd.write_text(
                "#!/usr/bin/env sh\nprintf '%s\\n' 'Warning: Vulkan layer not correctly registered.'\n",
                encoding="utf-8",
            )
            renderdoccmd.chmod(0o755)
            original = harness.local_renderdoccmd_path
            try:
                harness.local_renderdoccmd_path = lambda: str(renderdoccmd)  # type: ignore[assignment]
                diagnosis = harness.renderdoc_vulkan_layer_diagnosis()
                self.assertFalse(diagnosis["registered"])
                self.assertIn("not correctly registered", diagnosis["explain"])
            finally:
                harness.local_renderdoccmd_path = original  # type: ignore[assignment]

    def test_renderdoc_cli_replay_does_not_require_qrenderdoc(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            capture = temp / "capture.rdc"
            capture.write_bytes(b"rdc")
            qrenderdoc = temp / "qrenderdoc"
            qrenderdoc.write_text("#!/usr/bin/env sh\nprintf 'qrenderdoc must not run\\n' >&2\nexit 3\n", encoding="utf-8")
            qrenderdoc.chmod(0o755)
            renderdoccmd = temp / "renderdoccmd"
            renderdoccmd.write_text(
                "#!/usr/bin/env sh\n"
                "if [ \"$1\" = vulkanlayer ]; then printf '%s\\n' 'RenderDoc layer correctly registered.'; exit 0; fi\n"
                "printf '%s\\n' 'Replaying capture locally.'\n",
                encoding="utf-8",
            )
            renderdoccmd.chmod(0o755)
            original_cmd = harness.local_renderdoccmd_path
            try:
                harness.local_renderdoccmd_path = lambda: str(renderdoccmd)  # type: ignore[assignment]
                summary_path = harness.replay_renderdoc_summary(temp, capture)
                summary = json.loads(summary_path.read_text(encoding="utf-8"))
                self.assertEqual(summary["status"], "complete")
                self.assertTrue(summary["diagnosis"]["cli_only_replay"])
                self.assertTrue(summary["diagnosis"]["fallback_text_replay"])
                self.assertNotIn("python_replay", summary["diagnosis"])
            finally:
                harness.local_renderdoccmd_path = original_cmd  # type: ignore[assignment]

    def test_rust_opengl_renderdoc_command_uses_concrete_test_binary(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            binary_dir = root / "src" / "main" / "rust" / "target" / "debug" / "deps"
            binary_dir.mkdir(parents=True)
            binary = binary_dir / "mattmc_rust-testhash"
            binary.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            binary.chmod(0o755)
            renderdoccmd = root / "renderdoccmd"
            renderdoccmd.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            renderdoccmd.chmod(0o755)
            original = harness.local_renderdoccmd_path
            try:
                harness.local_renderdoccmd_path = lambda: str(renderdoccmd)  # type: ignore[assignment]
                command, env = harness.build_rust_opengl_renderdoc_command(root, root / "capture")
                joined = " ".join(command)
                self.assertIn(str(binary), command)
                self.assertIn(harness.RUST_OPENGL_CONFORMANCE_TEST, command)
                self.assertIn("renderdoccmd capture", joined)
                self.assertEqual(env["MATTMC_RENDERDOC_CAPTURE"], "1")
                self.assertEqual(env["MATTMC_OPENGL_STRICT"], "1")
                self.assertEqual(env["MATTMC_GRAPHICS_RUN_TYPE"], "renderdoc-capture")
            finally:
                harness.local_renderdoccmd_path = original  # type: ignore[assignment]

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

    def test_gradle_knot_launchers_have_classpath_verifiers(self) -> None:
        build_gradle = (Path(__file__).resolve().parents[2] / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("verifyRunClientClasspath", build_gradle)
        self.assertIn("verifyRunRealChunkMeshingReplayClasspath", build_gradle)
        self.assertIn("verifyJavaExecMainClassOnClasspath", build_gradle)
        self.assertIn("task.classpath", build_gradle)
        self.assertIn("net.fabricmc.loader.impl.launch.knot.KnotClient", build_gradle)

    def test_supported_smoke_clis_launch_in_dry_run(self) -> None:
        repo = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            frozen = fake_repo_path(temp_path, "frozen")
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

    def test_configured_frozen_repo_path_exists_and_is_used(self) -> None:
        repo = Path(__file__).resolve().parents[2]
        frozen = harness.resolve_named_directory(repo, "java_perf_repo")
        self.assertEqual(frozen, Path("/home/matt/Documents/Repos/MattMC_JavaPerfTesting/MattMC"))
        self.assertEqual(harness.find_frozen_repo(repo), frozen)
        self.assertTrue(frozen.is_dir(), frozen)
        self.assertTrue((frozen / ".git").is_dir(), frozen)
        self.assertTrue((frozen / "gradlew").is_file() or (frozen / "gradlew.bat").is_file(), frozen)

    def test_dry_run_manifest_records_resolved_current_and_frozen_paths(self) -> None:
        repo = Path(__file__).resolve().parents[2]
        frozen = harness.find_frozen_repo(repo)
        with tempfile.TemporaryDirectory() as temp:
            artifact_dir = Path(temp) / "dry-run"
            result = subprocess.run(
                [
                    sys.executable,
                    str(repo / "DevUtils" / "Audit" / "Capture.py"),
                    "--profile",
                    "smoke",
                    "--mode",
                    "frozen-opengl-shaders-off",
                    "--artifact-dir",
                    str(artifact_dir),
                    "--dry-run",
                ],
                cwd=repo,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                timeout=20,
            )
            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            manifest = json.loads((artifact_dir / harness.MANIFEST_NAME).read_text(encoding="utf-8"))
            self.assertEqual(manifest["repository_resolution"]["current"], str(repo.resolve()))
            self.assertEqual(manifest["repository_resolution"]["frozen"], str(frozen))
            artifact_path = Path(manifest["results"][0]["artifact_path"])
            artifact = json.loads(artifact_path.read_text(encoding="utf-8"))
            self.assertEqual(artifact["repository_resolution"]["current"], str(repo.resolve()))
            self.assertEqual(artifact["repository_resolution"]["frozen"], str(frozen))

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
            left["benchmark_fingerprint"]["workload_signature"]["runtime"]["entity_count"] = 178
            signature = right["benchmark_fingerprint"]["workload_signature"]
            signature["runtime"]["loaded_chunks"] = 124
            signature["runtime"]["entity_count"] = 190
            signature["workload_families"]["sodium-terrain-draw"]["calls_per_frame"] = 2.1
            comparison = harness.compare_workloads(left, right)
            self.assertTrue(comparison["comparable"], comparison)

    def test_fingerprint_rejects_material_entity_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            left = gameplay_artifact_for(root, harness.MATRIX_MODES[0])
            right = gameplay_artifact_for(root, harness.MATRIX_MODES[4])
            signature = right["benchmark_fingerprint"]["workload_signature"]
            signature["runtime"]["entity_count"] = 240
            comparison = harness.compare_workloads(left, right)
            self.assertFalse(comparison["comparable"])
            self.assertTrue(any(diff["path"] == "runtime.entity_count" for diff in comparison["differences"]))

    def test_fingerprint_rejects_material_workload_family_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            left = gameplay_artifact_for(root, harness.MATRIX_MODES[0])
            right = gameplay_artifact_for(root, harness.MATRIX_MODES[4])
            signature = right["benchmark_fingerprint"]["workload_signature"]
            signature["workload_families"]["sodium-terrain-draw"]["present"] = False
            signature["workload_families"]["sodium-terrain-draw"]["bucket"] = "zero"
            comparison = harness.compare_workloads(left, right)
            self.assertFalse(comparison["comparable"])
            self.assertTrue(any(diff["path"].startswith("workload_families.sodium-terrain-draw") for diff in comparison["differences"]))

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
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            artifact = artifact_for(root, frozen_mode)
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
            self.assertIn("--artifact-dir " + str(root / "capture"), joined)
            self.assertIn("--shaders off", joined)

    def test_tracy_capture_uses_java_property_not_client_flag(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
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
                tracy_capture=True,
                tracy_duration_seconds=1,
                tracy_max_size_mb=8,
                renderdoc_capture=False,
                renderdoc_frame=8,
            )
            command, env = harness.build_capture_command(target, harness.MATRIX_MODES[0], root / "capture", "gameplay", args, "gameplay")
            self.assertNotIn("--tracy", command)
            self.assertIn("-Dmattmc.dev.tracyCapture=true", env["JAVA_TOOL_OPTIONS"])

    def test_explicit_jvm_args_are_java_tool_options_not_client_args(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = Namespace(
                profile="smoke",
                validation="off",
                client_args="",
                jvm_arg=["-Dmattmc.rustGal.guiCrosshair.enabled=true"],
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
                tracy_capture=False,
                tracy_duration_seconds=1,
                tracy_max_size_mb=8,
                renderdoc_capture=False,
                renderdoc_frame=8,
            )
            command, env = harness.build_capture_command(target, harness.MATRIX_MODES[0], root / "capture", "correctness", args, "capture")
            self.assertNotIn("mattmc.rustGal.guiCrosshair.enabled", " ".join(command))
            self.assertIn("-Dmattmc.rustGal.guiCrosshair.enabled=true", env["JAVA_TOOL_OPTIONS"])

    def test_gameplay_passes_armor_controls_without_per_frame_slice_logging(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = Namespace(
                profile="smoke",
                validation="off",
                client_args="",
                jvm_arg=[],
                rust_gal_gui_control="legacy",
                armor_value=19,
                game_mode="survival",
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
                tracy_capture=False,
                tracy_duration_seconds=1,
                tracy_max_size_mb=8,
                renderdoc_capture=False,
                renderdoc_frame=8,
            )
            command, env = harness.build_capture_command(target, harness.MATRIX_MODES[0], root / "capture", "gameplay", args, "gameplay")

            self.assertNotIn("mattmc.dev.graphicsAuditSliceMetrics", " ".join(command))
            self.assertNotIn("-Dmattmc.dev.graphicsAuditSliceMetrics=true", env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.rustGalGui.armor.legacyControl=true", env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.armorValue=19", env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.gameMode=survival", env["JAVA_TOOL_OPTIONS"])

    def test_capture_passes_player_heart_controls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = Namespace(
                profile="smoke",
                validation="off",
                client_args="",
                jvm_arg=[],
                rust_gal_gui_control="rust",
                armor_value=None,
                player_health=19.0,
                player_max_health=40.0,
                player_absorption=3.0,
                player_heart_variant="poisoned",
                player_heart_flash=True,
                player_heart_hardcore=True,
                player_heart_regeneration=True,
                game_mode="survival",
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
                tracy_capture=False,
                tracy_duration_seconds=1,
                tracy_max_size_mb=8,
                renderdoc_capture=False,
                renderdoc_frame=8,
            )
            command, env = harness.build_capture_command(target, harness.MATRIX_MODES[0], root / "capture", "correctness", args, "capture")
            options = env["JAVA_TOOL_OPTIONS"]

            self.assertNotIn("mattmc.dev.deterministicCameraCapture.playerHealth", " ".join(command))
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerHealth=19.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHealth=19.0", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerMaxHealth=40.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerMaxHealth=40.0", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerAbsorption=3.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerAbsorption=3.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHeartVariant=poisoned", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHealthFlash=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHealthHardcore=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHealthRegeneration=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.gameMode=survival", options)

    def test_rust_opengl_attribution_is_not_confused_by_vulkanicgal_name(self) -> None:
        mode = harness.ModeSpec("current-opengl-shaders-on", "current", "opengl", "on", "java-opengl", False)
        logs = "Rust VulkanicGAL bridge created borrowed OpenGL context"

        self.assertEqual("mixed-java-opengl-rust-opengl", harness.detect_attribution(mode, {}, logs))
        self.assertEqual(
            "mixed-java-opengl-rust-opengl",
            harness.detect_attribution(mode, {"render_implementation": "rust-vulkan"}, logs),
        )
        self.assertEqual(
            {"frame.base": "java-opengl", "gui.migrated": "rust-opengl"},
            harness.implementation_attribution_families(mode, "mixed-java-opengl-rust-opengl", logs),
        )

    def test_rust_vulkan_attribution_still_detects_specific_vulkan_backend(self) -> None:
        mode = harness.ModeSpec("current-rust-vulkan-shaders-off", "current", "rust-vulkan", "off", "rust-vulkan", True)
        logs = "Rust Vulkan backend submitted frame"

        self.assertEqual("rust-vulkan", harness.detect_attribution(mode, {}, logs))

    def test_rust_gal_slice_metrics_are_extracted_from_capture_logs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            (capture / "runClient_20260101_000000.log").write_text(
                "Creating shared Sodium chunk pipeline\n"
                "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI batch executed producer=minecraft.gui.crosshair "
                "stratum=gui.crosshair batch=2 frame=3 submission=4 rust_gal_cache_hits=7 "
                "rust_gal_cache_misses=1 rust_gal_queue_depth=0 rust_gal_batches_executed=1 "
                "rust_gal_batches_cancelled=0 rust_gal_completion_polls=1 rust_gal_completion_timeouts=0 "
                "rust_gal_ffi_context_create_calls=1 rust_gal_ffi_capability_calls=1 "
                "rust_gal_ffi_frame_configure_calls=1 rust_gal_ffi_frame_acquire_calls=1 "
                "rust_gal_ffi_frame_present_calls=1 rust_gal_ffi_resource_batch_calls=4 "
                "rust_gal_ffi_submit_calls=2 rust_gal_ffi_completion_query_calls=1 rust_gal_ffi_retire_calls=2 "
                "rust_gal_ffi_context_create_bytes=40 rust_gal_ffi_capability_bytes=24 "
                "rust_gal_ffi_frame_configure_bytes=64 rust_gal_ffi_frame_acquire_bytes=48 "
                "rust_gal_ffi_frame_present_bytes=40 rust_gal_ffi_resource_batch_bytes=3000 "
                "rust_gal_ffi_submit_bytes=720 rust_gal_ffi_completion_query_bytes=32 rust_gal_ffi_retire_bytes=128 "
                "ffi_call_count=11 ffi_bytes=4096\n"
                "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "stratum=gui.frame frame_batch_count=1 frame=4 submission=5 rust_gal_cache_hits=8 "
                "rust_gal_cache_misses=1 rust_gal_queue_depth=0 rust_gal_batches_executed=2 "
                "rust_gal_batches_cancelled=0 rust_gal_completion_polls=0 rust_gal_completion_timeouts=0 "
                "rust_gal_ffi_context_create_calls=1 rust_gal_ffi_capability_calls=1 "
                "rust_gal_ffi_frame_configure_calls=1 rust_gal_ffi_frame_acquire_calls=2 "
                "rust_gal_ffi_frame_present_calls=2 rust_gal_ffi_resource_batch_calls=5 "
                "rust_gal_ffi_submit_calls=3 rust_gal_ffi_completion_query_calls=0 rust_gal_ffi_retire_calls=1 "
                "rust_gal_ffi_context_create_bytes=40 rust_gal_ffi_capability_bytes=24 "
                "rust_gal_ffi_frame_configure_bytes=64 rust_gal_ffi_frame_acquire_bytes=96 "
                "rust_gal_ffi_frame_present_bytes=80 rust_gal_ffi_resource_batch_bytes=3200 "
                "rust_gal_ffi_submit_bytes=1080 rust_gal_ffi_completion_query_bytes=0 rust_gal_ffi_retire_bytes=64 "
                "rust_gal_enqueue_nanos=4000 rust_gal_resource_lookup_nanos=6000 "
                "rust_gal_resource_create_nanos=1000 rust_gal_abi_packing_nanos=8000 "
                "rust_gal_frame_acquire_nanos=10000 rust_gal_submit_nanos=12000 "
                "rust_gal_frame_present_nanos=14000 rust_gal_retire_nanos=16000 "
                "rust_gal_completion_query_nanos=18000 rust_gal_execute_nanos=20000 "
                "rust_gal_command_lists=3 rust_gal_command_ops=12 rust_gal_backend_submissions=3 "
                "rust_gal_backend_waits=0 rust_gal_gl_calls=17 rust_gal_gl_flushes=0 rust_gal_gl_finishes=0 "
                "rust_gal_gl_fences_inserted=3 rust_gal_gl_fences_polled=3 rust_gal_gl_fences_waited=0 "
                "rust_gal_gl_fences_deleted=1 "
                "ffi_call_count=18 ffi_bytes=8192\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertEqual("mixed-java-opengl-rust-opengl", artifact["implementation_attribution"])
            self.assertEqual("java-opengl", artifact["expected_base_backend"])
            self.assertEqual(
                {"frame.base": "java-opengl", "gui.migrated": "rust-opengl"},
                artifact["implementation_attribution_families"],
            )
            self.assertEqual(
                {"frame.base": "java-opengl", "gui.migrated": "rust-opengl"},
                artifact["benchmark_fingerprint"]["implementation"]["families"],
            )
            self.assertEqual("gui.frame", artifact["metrics"]["rust_gal_slice"]["producer"])
            self.assertEqual(8, artifact["metrics"]["rust_gal_slice"]["cache_hits"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["cache_misses"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["queue_depth"])
            self.assertEqual(2, artifact["metrics"]["rust_gal_slice"]["batches_executed"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["batches_cancelled"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["completion_polls"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["completion_timeouts"])
            self.assertEqual(5, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["resource_batch"]["calls"])
            self.assertEqual(3200, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["resource_batch"]["bytes"])
            self.assertEqual(9, artifact["metrics"]["rust_gal_slice"]["ffi_calls_per_executed_batch"])
            self.assertEqual(4096, artifact["metrics"]["rust_gal_slice"]["ffi_bytes_per_executed_batch"])
            self.assertEqual(8000, artifact["metrics"]["rust_gal_slice"]["timing_totals_nanos"]["abi_packing_nanos"])
            self.assertEqual(10000, artifact["metrics"]["rust_gal_slice"]["timing_per_executed_batch_nanos"]["execute_nanos"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_flushes"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_finishes"])
            self.assertEqual(3, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_fences_inserted"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_fences_waited"])
            self.assertEqual(18, artifact["metrics"]["ffi"]["call_count"])
            self.assertEqual(8192, artifact["metrics"]["ffi"]["bytes"])

    def test_rust_gal_slice_metrics_are_extracted_from_gameplay_status(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000],
                "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "stratum=gui.frame frame_batch_count=17 frame=20 submission=22 rust_gal_cache_hits=18 "
                "rust_gal_cache_misses=2 rust_gal_queue_depth=0 rust_gal_frames_executed=20 "
                "rust_gal_batches_executed=340 rust_gal_sprite_batches_executed=160 "
                "rust_gal_packed_sprites_executed=340 rust_gal_batches_cancelled=0 "
                "rust_gal_completion_polls=0 rust_gal_completion_timeouts=0 "
                "rust_gal_ffi_context_create_calls=1 rust_gal_ffi_capability_calls=1 "
                "rust_gal_ffi_frame_configure_calls=1 rust_gal_ffi_frame_acquire_calls=20 "
                "rust_gal_ffi_frame_resize_calls=0 rust_gal_ffi_frame_present_calls=20 "
                "rust_gal_ffi_resource_batch_calls=9 rust_gal_ffi_submit_calls=22 "
                "rust_gal_ffi_completion_query_calls=0 rust_gal_ffi_retire_calls=0 "
                "rust_gal_ffi_context_create_bytes=40 rust_gal_ffi_capability_bytes=24 "
                "rust_gal_ffi_frame_configure_bytes=48 rust_gal_ffi_frame_acquire_bytes=640 "
                "rust_gal_ffi_frame_resize_bytes=0 rust_gal_ffi_frame_present_bytes=640 "
                "rust_gal_ffi_resource_batch_bytes=5192 rust_gal_ffi_submit_bytes=260000 "
                "rust_gal_ffi_completion_query_bytes=0 rust_gal_ffi_retire_bytes=0 "
                "rust_gal_enqueue_nanos=100 rust_gal_resource_lookup_nanos=200 "
                "rust_gal_resource_create_nanos=300 rust_gal_abi_packing_nanos=400 "
                "rust_gal_frame_acquire_nanos=500 rust_gal_submit_nanos=600 "
                "rust_gal_frame_present_nanos=700 rust_gal_retire_nanos=0 "
                "rust_gal_completion_query_nanos=0 rust_gal_execute_nanos=800 "
                "rust_gal_command_lists=22 rust_gal_command_ops=1584 rust_gal_backend_submissions=22 "
                "rust_gal_backend_waits=0 rust_gal_gl_calls=1584 rust_gal_gl_flushes=0 "
                "rust_gal_gl_finishes=0 rust_gal_gl_fences_inserted=22 rust_gal_gl_fences_polled=22 "
                "rust_gal_gl_fences_waited=0 rust_gal_gl_fences_deleted=0 ffi_call_count=74 ffi_bytes=266584"
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "gameplay", True, [], 0, False, tool_kind="gameplay")

            self.assertEqual(20, artifact["metrics"]["rust_gal_slice"]["frames_executed"])
            self.assertEqual(340, artifact["metrics"]["rust_gal_slice"]["packed_sprites_executed"])
            self.assertEqual(3.7, artifact["metrics"]["rust_gal_slice"]["ffi_calls_per_frame"])
            self.assertEqual(13329.2, artifact["metrics"]["rust_gal_slice"]["ffi_bytes_per_frame"])

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

    def test_stable_workload_family_summaries_use_phase_counters_not_log_counts(self) -> None:
        frame_doc = {
            "measuredFrameCount": 10,
            "submittedWorkCounts": {
                "sodium-terrain": 100,
                "DistantHorizons": 20,
                "pipeline-cache": 1,
            },
            "runtimeState": {
                "entityCount": 7,
                "loadedChunks": 121,
                "hideGui": False,
            },
            "exclusivePhaseNanos": {
                "sodium.terrain.setup": {"count": 10, "total": 100, "worst": 10},
                "sodium.terrain.draw": {"count": 20, "total": 100, "worst": 10},
                "distant-horizons.lod-render": {"count": 5, "total": 100, "worst": 10},
            },
        }
        summary = harness.stable_workload_family_summary(frame_doc, "Sodium Sodium Sodium")
        self.assertEqual(summary["sodium-terrain-setup"]["calls_per_frame"], 1.0)
        self.assertEqual(summary["sodium-terrain-draw"]["calls_per_frame"], 2.0)
        self.assertEqual(summary["dh-lod"]["calls_per_frame"], 0.5)
        self.assertEqual(summary["entities"]["count"], 7)
        self.assertEqual(summary["gui"]["calls_per_frame"], 1.0)

    def test_backend_work_counters_are_diagnostic_only(self) -> None:
        frame_doc = {
            "measuredFrameCount": 10,
            "submittedWorkCounts": {
                "pipeline-cache": 1,
                "draw-indexed": 20,
                "transfer-upload": 3,
            },
        }
        summary = harness.backend_work_counter_summary(frame_doc)
        self.assertFalse(summary["families"]["draws"]["comparable"])
        self.assertEqual(summary["families"]["draws"]["calls_per_frame"], 2.0)
        self.assertEqual(summary["families"]["uploads"]["count"], 3)

    def test_graphics_diagnostic_hooks_are_not_publishable_performance(self) -> None:
        signature = harness.instrumentation_signature({"graphics_audit_enabled": "true"}, ["gradlew"])
        self.assertTrue(signature["diagnostics_enabled"])
        self.assertTrue(signature["diagnostic_hooks"])
        self.assertFalse(signature["performance_comparable"])

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
