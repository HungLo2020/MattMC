#!/usr/bin/env python3
"""Self-tests for the cross-repository graphics audit harness."""

from __future__ import annotations

import json
import os
import shlex
import subprocess
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))

import graphics_harness as harness
import artifact_retention
import capture_runner


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


def write_outline_probe_image(path: Path, *, visible: bool) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (8, 8, 8))
    if visible:
        for x in range(548, 733):
            for y in (280, 456):
                image.putpixel((x, y), (80, 217, 199))
        for y in range(280, 457):
            for x in (548, 732):
                image.putpixel((x, y), (80, 217, 199))
    image.save(path)


def write_world_border_probe_image(path: Path, *, visible: bool) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (18, 36, 72))
    if visible:
        for x in range(180, 1100):
            for y in range(260, 320):
                image.putpixel((x, y), (84, 220, 86))
    image.save(path)


def write_world_crack_probe_image(path: Path, *, variant: str) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (120, 167, 255))
    left, top, right, bottom = int(1280 * 0.46), int(720 * 0.47), int(1280 * 0.57), int(720 * 0.67)
    if variant == "vanilla":
        for x in range(left + 14, right - 14):
            for y in range(top + 12, bottom - 12):
                image.putpixel((x, y), (235, 235, 235))
        for x in range(left + 30, right - 30, 3):
            for y in range(top + 20, bottom - 20, 5):
                image.putpixel((x, y), (42, 48, 55))
    elif variant == "pack-a":
        for x in range(left + 10, right - 10):
            for y in range(top + 10, bottom - 10):
                image.putpixel((x, y), (238, 37, 67))
    elif variant == "pack-b":
        for x in range(left + 10, right - 10):
            for y in range(top + 10, bottom - 10):
                image.putpixel((x, y), (35, 212, 98))
    image.save(path)


def write_block_marker_probe_image(path: Path, *, texture_id: int = harness.WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (36, 48, 60))
    left, top, right, bottom = 560, 300, 640, 380
    if texture_id == harness.WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER:
        for x in range(left, right):
            for y in range(top, bottom):
                if abs((x - left) - (y - top)) <= 5:
                    image.putpixel((x, y), (225, 32, 38))
                elif x in range(left, left + 8) or x in range(right - 8, right):
                    image.putpixel((x, y), (225, 32, 38))
                elif y in range(top, top + 8) or y in range(bottom - 8, bottom):
                    image.putpixel((x, y), (225, 32, 38))
                else:
                    image.putpixel((x, y), (245, 245, 245))
    else:
        for x in range(left, right):
            for y in range(top, bottom):
                image.putpixel((x, y), (245, 235, 88) if (x + y) % 9 else (250, 250, 250))
    image.save(path)


def write_terrain_particle_probe_image(path: Path, *, texture_id: int = harness.WORLD_MATERIAL_TEXTURE_STONE) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (36, 48, 60))
    left, top, right, bottom = 560, 300, 640, 380
    color = {
        harness.WORLD_MATERIAL_TEXTURE_STONE: (116, 116, 116),
        harness.WORLD_MATERIAL_TEXTURE_DIRT: (128, 82, 45),
        harness.WORLD_MATERIAL_TEXTURE_OAK_LEAVES: (74, 128, 52),
        harness.WORLD_MATERIAL_TEXTURE_DEEPSLATE: (70, 73, 80),
        harness.WORLD_MATERIAL_TEXTURE_WHITE_WOOL: (220, 220, 205),
    }.get(texture_id, (116, 116, 116))
    for x in range(left, right):
        for y in range(top, bottom):
            if 8 <= x - left <= 72 and 8 <= y - top <= 72:
                image.putpixel((x, y), color)
    image.save(path)


def write_block_display_probe_image(path: Path, *, scenario: str = "stone") -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (36, 48, 60))
    left, top, right, bottom = 520, 250, 760, 490
    if scenario in {"oak-leaves", "cutout", "tinted"}:
        color = (78, 142, 58)
    elif scenario in {"asymmetric", "furnace"}:
        color = (118, 118, 118)
    elif scenario in {"non-full-cube", "stairs"}:
        color = (136, 82, 38)
    else:
        color = (124, 124, 124)
    for x in range(left, right):
        for y in range(top, bottom):
            if scenario in {"oak-leaves", "cutout", "tinted"} and ((x - left) // 16 + (y - top) // 16) % 3 == 0:
                continue
            image.putpixel((x, y), color)
    image.save(path)


def write_falling_block_probe_image(path: Path, *, scenario: str = "sand") -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (36, 48, 60))
    left, top, right, bottom = 520, 80, 700, 260
    if scenario == "gravel":
        color = (118, 118, 118)
    elif scenario in {"concrete-powder", "concrete_powder"}:
        color = (198, 198, 190)
    else:
        color = (196, 174, 104)
    for x in range(left, right):
        for y in range(top, bottom):
            image.putpixel((x, y), color)
    image.save(path)


def write_rust_shell_scene_image(path: Path) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (120, 167, 255))
    for x in range(160, 1120):
        for y in range(180, 250):
            image.putpixel((x, y), (80, 190, 255))
    for x in range(500, 780):
        for y in range(260, 470):
            image.putpixel((x, y), (36, 28, 24))
    for x in range(470, 810):
        image.putpixel((x, 230), (80, 220, 86))
        image.putpixel((x, 510), (80, 220, 86))
    for y in range(230, 511):
        image.putpixel((470, y), (80, 220, 86))
        image.putpixel((810, y), (80, 220, 86))
    for x in range(560, 720):
        for y in range(20, 70):
            image.putpixel((x, y), (240, 240, 240))
    image.save(path)


def write_retention_manifest(path: Path, *, success: bool, profile: str = "smoke") -> None:
    path.mkdir(parents=True, exist_ok=True)
    (path / harness.MANIFEST_NAME).write_text(
        json.dumps(
            {
                "success": success,
                "runtime_profile": {"name": profile},
            }
        )
        + "\n",
        encoding="utf-8",
    )


def write_frame_benchmark(
    capture_dir: Path,
    samples: list[int],
    rust_gal_line: str | None = None,
    terrain_particle_real: dict[str, object] | None = None,
    block_display: dict[str, object] | None = None,
    block_display_work_count: int = 0,
) -> None:
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
                "terrainParticleRealGameplay": terrain_particle_real or {"enabled": False},
                "blockDisplayScenario": block_display
                or {
                    "enabled": False,
                    "scenario": "",
                    "workload": "single",
                    "routeControl": "rust",
                    "status": "inactive",
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
                    **({"block-display": block_display_work_count} if block_display_work_count else {}),
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

    def test_block_marker_summary_uses_positive_frame_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[6]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "block_marker_barrier.png"
            texture_id = harness.WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER
            material_id = harness.WORLD_MATERIAL_BLOCK_MARKER_CUTOUT
            write_block_marker_probe_image(screenshot, texture_id=texture_id)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["rustGalWorldMaterialMarkerScenario"] = "barrier"
            doc["rustGalWorldMaterialMarkers"] = [
                {
                    "route": "rust-vulkan-whole-frame",
                    "textureId": texture_id,
                    "center": {"x": 1.5, "y": 80.5, "z": 2.5},
                    "quadSize": 0.5,
                    "colorArgb": 0xFFFFFFFF,
                    "viewport": {"width": 1280, "height": 720},
                    "projected": True,
                    "screenBounds": {"left": 560.0, "top": 300.0, "right": 640.0, "bottom": 380.0},
                }
            ]
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "\n".join(
                    [
                        "-Dmattmc.dev.rustGalWorldMaterial.blockMarkerScenario=barrier",
                        f"Rust VulkanicGAL BlockMarker semantic request route=rust-vulkan-whole-frame texture_id={texture_id} material_id={material_id} result=queued",
                        f"gal.frame.target.begin backend=vulkan frame=1 material_marker_barrier_quads=1 material_marker_light_quads=0 material_marker_light_level_mask=0 material_marker_last_light_level=-1 material_marker_last_texture_id={texture_id}",
                        "rust_gal_world_material_quads_executed=1",
                        "rust_gal_world_material_batches_executed=1",
                        "rust_gal_world_material_draws_executed=1",
                        "gal.frame.target.begin backend=vulkan frame=2 material_marker_barrier_quads=0 material_marker_light_quads=0 material_marker_light_level_mask=0 material_marker_last_light_level=-1 material_marker_last_texture_id=0",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
            )
            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertTrue(artifact["validation"]["complete"])
            self.assertEqual(counters["world_material_marker_barrier_quads"], 1)
            self.assertEqual(counters["world_material_marker_last_texture_id"], texture_id)
            self.assertEqual("present", counters["world_material_marker_pixel_evidence"]["status"])
            self.assertEqual([texture_id], counters["world_material_marker_pixel_evidence"]["validated_texture_ids"])

    def test_terrain_particle_summary_uses_projected_crop_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[6]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "terrain_particle_stone.png"
            texture_id = harness.WORLD_MATERIAL_TEXTURE_STONE
            material_id = harness.WORLD_MATERIAL_OPAQUE_TEXTURED
            write_terrain_particle_probe_image(screenshot, texture_id=texture_id)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["rustGalWorldTerrainParticleScenario"] = "stone"
            doc["rustGalWorldTerrainParticles"] = [
                {
                    "frameIndex": doc["captures"][0].get("renderedFrameIndex", 1),
                    "route": "rust-vulkan-whole-frame",
                    "textureId": texture_id,
                    "spriteId": "minecraft:block/stone",
                    "center": {"x": 1.5, "y": 80.5, "z": 2.5},
                    "quadSize": 0.15,
                    "colorArgb": 0xFFFFFFFF,
                    "packedLight": 0xF000F0,
                    "materialMode": 1,
                    "uv": {"u0": 0.0, "u1": 0.25, "v0": 0.0, "v1": 0.25},
                    "viewport": {"width": 1280, "height": 720},
                    "projected": True,
                    "screenBounds": {"left": 560.0, "top": 300.0, "right": 640.0, "bottom": 380.0},
                }
            ]
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "\n".join(
                    [
                        "-Dmattmc.dev.rustGalWorldMaterial.terrainParticleScenario=stone",
                        f"Rust VulkanicGAL TerrainParticle semantic request route=rust-vulkan-whole-frame texture_id={texture_id} material_id={material_id} mode=1 sprite=minecraft:block/stone result=queued",
                        f"gal.frame.target.begin backend=vulkan frame=1 material_terrain_particle_quads=1 material_terrain_particle_texture_mask=2 material_marker_barrier_quads=0 material_marker_light_quads=0 material_marker_light_level_mask=0 material_marker_last_light_level=-1 material_marker_last_texture_id={texture_id}",
                        "rust_gal_world_material_quads_executed=1",
                        "rust_gal_world_material_batches_executed=1",
                        "rust_gal_world_material_draws_executed=1",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
            )
            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertTrue(artifact["validation"]["complete"])
            self.assertEqual(1, counters["world_material_terrain_particle_quads"])
            self.assertEqual("present", counters["world_material_terrain_particle_pixel_evidence"]["status"])
            self.assertEqual([texture_id], counters["world_material_terrain_particle_pixel_evidence"]["validated_texture_ids"])

    def test_oak_leaf_terrain_particle_requires_cutout_material_mode(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[6]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "terrain_particle_oak_leaves.png"
            texture_id = harness.WORLD_MATERIAL_TEXTURE_OAK_LEAVES
            material_id = harness.WORLD_MATERIAL_OPAQUE_TEXTURED
            write_terrain_particle_probe_image(screenshot, texture_id=texture_id)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["rustGalWorldTerrainParticleScenario"] = "oak-leaves"
            doc["rustGalWorldTerrainParticles"] = [
                {
                    "frameIndex": doc["captures"][0].get("renderedFrameIndex", 1),
                    "route": "rust-vulkan-whole-frame",
                    "textureId": texture_id,
                    "spriteId": "minecraft:block/oak_leaves",
                    "center": {"x": 1.5, "y": 80.5, "z": 2.5},
                    "quadSize": 0.15,
                    "colorArgb": 0xFFFFFFFF,
                    "packedLight": 0xF000F0,
                    "materialMode": 1,
                    "uv": {"u0": 0.0, "u1": 0.25, "v0": 0.0, "v1": 0.25},
                    "viewport": {"width": 1280, "height": 720},
                    "projected": True,
                    "screenBounds": {"left": 560.0, "top": 300.0, "right": 640.0, "bottom": 380.0},
                }
            ]
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "\n".join(
                    [
                        "-Dmattmc.dev.rustGalWorldMaterial.terrainParticleScenario=oak-leaves",
                        f"Rust VulkanicGAL TerrainParticle semantic request route=rust-vulkan-whole-frame texture_id={texture_id} material_id={material_id} mode=1 sprite=minecraft:block/oak_leaves result=queued",
                        f"gal.frame.target.begin backend=vulkan frame=1 material_terrain_particle_quads=1 material_terrain_particle_texture_mask=8 material_marker_barrier_quads=0 material_marker_light_quads=0 material_marker_light_level_mask=0 material_marker_last_light_level=-1 material_marker_last_texture_id={texture_id}",
                        "rust_gal_world_material_quads_executed=1",
                        "rust_gal_world_material_batches_executed=1",
                        "rust_gal_world_material_draws_executed=1",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
            )
            evidence = artifact["metrics"]["rust_gal_slice"]["world_material_terrain_particle_pixel_evidence"]
            self.assertEqual("absent", evidence["status"])
            self.assertEqual([], evidence["validated_texture_ids"])
            self.assertFalse(evidence["crops"][0]["material_mode_matches"])

    def test_real_gameplay_terrain_particle_gate_uses_route_and_material_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000, 15_500_000],
                rust_gal_line=(
                    "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                    "rust_gal_world_material_quads_executed=42 "
                    "rust_gal_world_material_batches_executed=3 "
                    "rust_gal_world_material_draws_executed=3 "
                    "ffi_call_count=9 ffi_bytes=512"
                ),
                terrain_particle_real={
                    "enabled": True,
                    "routeControl": "rust",
                    "status": "continue",
                    "target": "1, 80, 2",
                    "blockType": "minecraft:stone",
                    "setupCount": 2,
                    "driveCalls": 20,
                    "startCalls": 1,
                    "continueCalls": 19,
                    "breakingEffects": 19,
                    "effectsPerFrame": 5,
                    "materialCount": 5,
                    "expectedMaterialMask": 0b11111,
                    "materialMask": 0b11111,
                    "routeCounts": {"rust": 42},
                    "routeNanos": {"rust": 1000},
                },
            )
            artifact = harness.normalize_capture_artifact(
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
            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertEqual([], artifact["validation"]["messages"])
            self.assertEqual(42, counters["world_material_terrain_particle_quads"])
            self.assertTrue(counters["world_material_terrain_particle_real_gameplay"])
            self.assertEqual("rust", counters["world_material_terrain_particle_control"])

    def test_real_gameplay_terrain_particle_gate_rejects_missing_production_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000, 15_500_000],
                rust_gal_line="Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame ffi_call_count=3 ffi_bytes=128",
                terrain_particle_real={
                    "enabled": True,
                    "routeControl": "rust",
                    "status": "continue",
                    "target": "1, 80, 2",
                    "blockType": "minecraft:stone",
                    "setupCount": 1,
                    "driveCalls": 20,
                    "startCalls": 1,
                    "continueCalls": 19,
                    "breakingEffects": 19,
                    "effectsPerFrame": 5,
                    "materialCount": 5,
                    "expectedMaterialMask": 0b11111,
                    "materialMask": 0b11111,
                    "routeCounts": {},
                    "routeNanos": {},
                },
            )
            artifact = harness.normalize_capture_artifact(
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
            messages = "\n".join(artifact["validation"]["messages"])
            self.assertIn("no particle extraction route samples", messages)
            self.assertIn("did not produce Rust material work", messages)

    def test_real_gameplay_terrain_particle_java_vulkan_uses_compatibility_route(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-java-vulkan-shaders-off")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000, 15_500_000],
                rust_gal_line="Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame rust_gal_world_material_quads_executed=0",
                terrain_particle_real={
                    "enabled": True,
                    "routeControl": "rust",
                    "status": "continue",
                    "target": "1, 80, 2",
                    "blockType": "minecraft:stone",
                    "setupCount": 1,
                    "driveCalls": 20,
                    "startCalls": 1,
                    "continueCalls": 19,
                    "breakingEffects": 19,
                    "effectsPerFrame": 5,
                    "materialCount": 5,
                    "expectedMaterialMask": 0b11111,
                    "materialMask": 0b11111,
                    "routeCounts": {"java-compat": 42},
                    "routeNanos": {"java-compat": 1000},
                },
            )
            artifact = harness.normalize_capture_artifact(
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
            self.assertEqual([], artifact["validation"]["messages"])
            self.assertEqual("java-vulkan", artifact["implementation_attribution"])

    def test_block_display_mesh_gate_uses_mesh_workload_counters(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000, 15_500_000],
                rust_gal_line=(
                    "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                    "rust_gal_world_mesh_instances_executed=1 "
                    "rust_gal_world_mesh_batches_executed=1 "
                    "rust_gal_world_mesh_draws_executed=1 "
                    "rust_gal_world_mesh_cache_hits=2 "
                    "rust_gal_world_mesh_cache_misses=1 "
                    "rust_gal_ffi_world_mesh_asset_update_calls=1 "
                    "rust_gal_ffi_world_mesh_asset_update_bytes=4096 "
                    "ffi_call_count=9 ffi_bytes=512"
                ),
            )
            frame_path = next(capture.glob("graphics_frame_benchmark_*.json"))
            frame_doc = json.loads(frame_path.read_text(encoding="utf-8"))
            frame_doc["blockDisplayScenario"] = {
                "enabled": True,
                "scenario": "stone",
                "status": "spawned",
                "block": "minecraft:stone",
                "position": {"x": 1.5, "y": 80.0, "z": 2.5},
            }
            frame_path.write_text(json.dumps(frame_doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
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

            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertEqual([], artifact["validation"]["messages"])
            self.assertTrue(artifact["validation"]["complete"])
            self.assertEqual("stone", counters["world_mesh_block_display_scenario"])
            self.assertEqual(1, counters["world_mesh_instances_executed"])
            self.assertEqual(1, counters["world_mesh_batches_executed"])
            self.assertEqual(1, counters["world_mesh_draws_executed"])
            self.assertEqual(2, counters["world_mesh_cache_hits"])
            self.assertEqual(1, counters["ffi_operations"]["world_mesh_asset_update"]["calls"])

    def test_block_display_mesh_gate_rejects_missing_rust_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000, 15_500_000],
                rust_gal_line="Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame ffi_call_count=3 ffi_bytes=128",
            )
            frame_path = next(capture.glob("graphics_frame_benchmark_*.json"))
            frame_doc = json.loads(frame_path.read_text(encoding="utf-8"))
            frame_doc["blockDisplayScenario"] = {
                "enabled": True,
                "scenario": "stone",
                "status": "spawned",
            }
            frame_path.write_text(json.dumps(frame_doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
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

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any("BlockDisplay scenario requested but no non-zero" in message for message in artifact["validation"]["messages"])
            )

    def test_block_display_performance_gate_requires_multi_mesh_rust_workload(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000, 15_500_000],
                rust_gal_line=(
                    "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                    "rust_gal_world_mesh_instances_executed=30 "
                    "rust_gal_world_mesh_batches_executed=5 "
                    "rust_gal_world_mesh_draws_executed=5 "
                    "rust_gal_world_mesh_cache_hits=60 "
                    "rust_gal_world_mesh_cache_misses=5 "
                    "ffi_call_count=4 ffi_bytes=2048"
                ),
                block_display={
                    "enabled": True,
                    "scenario": "stone",
                    "workload": "performance",
                    "routeControl": "rust",
                    "status": "spawned",
                    "block": "minecraft:stone",
                    "position": "1,2,3",
                    "entityCount": 30,
                    "distinctBlockCount": 5,
                    "workloadFingerprint": "performance|stone|furnace|leaves|stairs|wool",
                },
                block_display_work_count=90,
            )
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone\n"
                "-Dmattmc.dev.rustGalWorldMesh.blockDisplayWorkload=performance\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
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

            self.assertTrue(artifact["validation"]["complete"], artifact["validation"]["messages"])
            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertEqual("performance", counters["world_mesh_block_display_workload"])
            self.assertEqual("rust", counters["world_mesh_block_display_control"])
            self.assertEqual(30, counters["world_mesh_block_display_metrics"]["entityCount"])
            self.assertEqual(5, counters["world_mesh_block_display_metrics"]["distinctBlockCount"])

    def test_block_display_disabled_and_legacy_controls_expect_no_rust_mesh_work(self) -> None:
        for control, property_line in (
            ("disabled", "-Dmattmc.dev.rustGalWorldBlockDisplay.disabled=true\n"),
            ("legacy", "-Dmattmc.dev.rustGalWorldBlockDisplay.legacyControl=true\n"),
        ):
            with self.subTest(control=control):
                with tempfile.TemporaryDirectory() as temp_dir:
                    temp = Path(temp_dir)
                    mode = harness.MATRIX_MODES[0]
                    target = fake_repo(temp, mode.target)
                    capture = temp / "capture"
                    write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
                    write_frame_benchmark(
                        capture,
                        [16_000_000, 17_000_000, 15_500_000],
                        rust_gal_line="Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame ffi_call_count=3 ffi_bytes=128",
                        block_display={
                            "enabled": True,
                            "scenario": "stone",
                            "workload": "performance",
                            "routeControl": control,
                            "status": "spawned",
                            "block": "minecraft:stone",
                            "position": "1,2,3",
                            "entityCount": 30,
                            "distinctBlockCount": 5,
                            "workloadFingerprint": "performance|stone|furnace|leaves|stairs|wool",
                        },
                        block_display_work_count=90,
                    )
                    (capture / "runClient_20260101_000000.log").write_text(
                        "-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone\n"
                        "-Dmattmc.dev.rustGalWorldMesh.blockDisplayWorkload=performance\n"
                        + property_line,
                        encoding="utf-8",
                    )

                    artifact = harness.normalize_capture_artifact(
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

                    self.assertTrue(artifact["validation"]["complete"], artifact["validation"]["messages"])
                    counters = artifact["metrics"]["rust_gal_slice"]
                    self.assertEqual(control, counters["world_mesh_block_display_control"])
                    self.assertEqual(0, counters["world_mesh_instances_executed"] or 0)

    def test_block_display_capture_requires_projected_crop_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "block_display_stone.png"
            write_block_display_probe_image(screenshot, scenario="stone")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "initialPosition": {"x": 1.0, "y": 80.0, "z": 2.0},
                "window": {"width": 1280, "height": 720},
                "rustGalWorldBlockDisplayScenario": "stone",
                "captures": [
                    {
                        "poseName": "initial",
                        "requestedYaw": 0.0,
                        "requestedPitch": 10.0,
                        "renderedFrameIndex": 8,
                        "screenshot": str(screenshot),
                    }
                ],
                "rustGalWorldBlockDisplays": [
                    {
                        "frameIndex": 8,
                        "route": "rust-opengl",
                        "blockId": "minecraft:stone",
                        "meshKey": "42",
                        "meshGeneration": 1,
                        "vertexLayoutVersion": 1,
                        "indexType": 1,
                        "vertexCount": 24,
                        "indexBytes": 72,
                        "sectionCount": 6,
                        "textureIds": "568297839",
                        "materialMode": 1,
                        "viewport": {"width": 1280, "height": 720},
                        "projected": True,
                        "screenBounds": {"left": 520.0, "top": 250.0, "right": 760.0, "bottom": 490.0},
                    }
                ],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone\n"
                "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "rust_gal_world_mesh_instances_executed=1 "
                "rust_gal_world_mesh_batches_executed=1 "
                "rust_gal_world_mesh_draws_executed=1 "
                "rust_gal_world_mesh_cache_hits=2 "
                "rust_gal_world_mesh_cache_misses=1 "
                "rust_gal_ffi_world_mesh_asset_update_calls=1 "
                "rust_gal_ffi_world_mesh_asset_update_bytes=4096 "
                "ffi_call_count=9 ffi_bytes=512\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
            )

            evidence = artifact["metrics"]["rust_gal_slice"]["world_mesh_block_display_pixel_evidence"]
            self.assertTrue(artifact["validation"]["complete"], artifact["validation"]["messages"])
            self.assertEqual("present", evidence["status"])
            self.assertEqual("passed", evidence["texture_status"])
            self.assertEqual("passed", evidence["material_status"])
            self.assertEqual("passed", evidence["orientation_status"])

    def test_block_display_capture_rejects_counter_only_success(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "rustGalWorldBlockDisplayScenario": "stone",
                "captures": [
                    {
                        "poseName": "initial",
                        "requestedYaw": 0.0,
                        "requestedPitch": 10.0,
                        "renderedFrameIndex": 8,
                    }
                ],
                "rustGalWorldBlockDisplays": [],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone\n"
                "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "rust_gal_world_mesh_instances_executed=1 "
                "rust_gal_world_mesh_batches_executed=1 "
                "rust_gal_world_mesh_draws_executed=1 "
                "ffi_call_count=9 ffi_bytes=512\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
            )

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any("projected mesh crop evidence" in message for message in artifact["validation"]["messages"])
            )

    def test_falling_block_capture_accepts_deterministic_diagnostics_without_frame_benchmark(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "falling_block_sand.png"
            write_falling_block_probe_image(screenshot, scenario="sand")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "rustGalWorldFallingBlockScenario": "sand",
                "captures": [{"poseName": "initial", "renderedFrameIndex": 8, "screenshot": str(screenshot)}],
                "rustGalWorldFallingBlocks": [
                    {
                        "frameIndex": 8,
                        "route": "rust-opengl",
                        "blockId": "minecraft:sand",
                        "meshKey": 42,
                        "meshGeneration": 2,
                        "vertexLayoutVersion": 1,
                        "indexType": 1,
                        "vertexCount": 24,
                        "indexBytes": 72,
                        "sectionCount": 6,
                        "textureIds": "2269692870",
                        "materialMode": 1,
                        "viewport": {"width": 1280, "height": 720},
                        "projected": True,
                        "screenBounds": {"left": 520.0, "top": 80.0, "right": 700.0, "bottom": 260.0},
                    }
                ],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n"
                "Rust OpenGL VulkanicGAL GUI frame executed producer=minecraft.entity.falling-block "
                "rust_gal_world_mesh_instances_executed=1 "
                "rust_gal_world_mesh_batches_executed=1 "
                "rust_gal_world_mesh_draws_executed=1 "
                "ffi_call_count=9 ffi_bytes=512\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
            )

            self.assertTrue(artifact["validation"]["complete"], artifact["validation"]["messages"])
            counters = artifact["metrics"]["rust_gal_slice"]
            evidence = counters["world_mesh_falling_block_pixel_evidence"]
            self.assertEqual("sand", counters["world_mesh_falling_block_scenario"])
            self.assertEqual(1, counters["world_mesh_instances_executed"])
            self.assertEqual("present", evidence["status"])
            self.assertEqual("passed", evidence["texture_status"])
            self.assertEqual("passed", evidence["material_status"])
            self.assertEqual("passed", evidence["orientation_status"])

    def test_falling_block_crop_accepts_resource_pack_texture_signature(self) -> None:
        from PIL import Image

        image = Image.new("RGB", (32, 32), (238, 37, 67))
        for x in range(32):
            image.putpixel((x, 0), (255, 255, 255))
        for y in range(32):
            image.putpixel((0, y), (0, 70, 255))

        stats = harness.falling_block_crop_stats(image, "sand")

        self.assertEqual("pack-a", stats["texture_signature"])
        self.assertGreaterEqual(stats["matching_pixels"], 96)
        self.assertTrue(stats["coverage_ok"])

    def test_falling_block_crop_reports_missing_texture_fallback_signature(self) -> None:
        from PIL import Image

        image = Image.new("RGB", (32, 32), (255, 0, 255))
        for x in range(16):
            for y in range(16):
                image.putpixel((x, y), (0, 0, 0))
        for x in range(16, 32):
            for y in range(16, 32):
                image.putpixel((x, y), (0, 0, 0))

        stats = harness.falling_block_crop_stats(image, "sand")

        self.assertEqual("missingno", stats["texture_signature"])
        self.assertGreaterEqual(stats["missingno_pixels"], 96)
        self.assertTrue(stats["coverage_ok"])

    def test_falling_block_capture_rejects_counter_only_success(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "rustGalWorldFallingBlockScenario": "sand",
                "captures": [{"poseName": "initial", "renderedFrameIndex": 8}],
                "rustGalWorldFallingBlocks": [
                    {
                        "frameIndex": 8,
                        "route": "rust-opengl",
                        "blockId": "minecraft:sand",
                        "meshKey": 42,
                        "meshGeneration": 2,
                        "vertexLayoutVersion": 1,
                        "indexType": 1,
                        "vertexCount": 24,
                        "indexBytes": 72,
                        "sectionCount": 6,
                        "textureIds": "2269692870",
                        "materialMode": 1,
                        "viewport": {"width": 1280, "height": 720},
                        "projected": True,
                        "screenBounds": {"left": 520.0, "top": 80.0, "right": 700.0, "bottom": 260.0},
                    }
                ],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n"
                "Rust OpenGL VulkanicGAL GUI frame executed producer=minecraft.entity.falling-block "
                "rust_gal_world_mesh_instances_executed=1 "
                "rust_gal_world_mesh_batches_executed=1 "
                "rust_gal_world_mesh_draws_executed=1 "
                "ffi_call_count=9 ffi_bytes=512\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
            )

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any("FallingBlock projected crop evidence" in message for message in artifact["validation"]["messages"])
            )

    def test_falling_block_disabled_control_accepts_route_decision_without_rust_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "rustGalWorldFallingBlockScenario": "sand",
                "captures": [{"poseName": "initial", "renderedFrameIndex": 8}],
                "rustGalWorldFallingBlocks": [],
                "rustGalWorldFallingBlockRouteDecisions": [
                    {
                        "frameIndex": 8,
                        "route": "disabled",
                        "blockId": "minecraft:sand",
                        "rustSelected": False,
                        "rustQueued": False,
                        "javaDrawn": False,
                    }
                ],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n"
                "-Dmattmc.dev.rustGalWorldFallingBlock.disabled=true\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
            )

            self.assertTrue(artifact["validation"]["complete"], artifact["validation"]["messages"])
            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertEqual(0, counters["world_mesh_instances_executed"])
            self.assertEqual({"deterministic:disabled": 1}, counters["world_mesh_falling_block_route_counts"])

    def test_falling_block_disabled_control_requires_route_decision(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "rustGalWorldFallingBlockScenario": "sand",
                "captures": [{"poseName": "initial", "renderedFrameIndex": 8}],
                "rustGalWorldFallingBlocks": [],
                "rustGalWorldFallingBlockRouteDecisions": [],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n"
                "-Dmattmc.dev.rustGalWorldFallingBlock.disabled=true\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
            )

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any("FallingBlock disabled control did not prove production route traversal" in message for message in artifact["validation"]["messages"])
            )

    def test_falling_block_legacy_control_accepts_java_route_without_rust_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "rustGalWorldFallingBlockScenario": "sand",
                "captures": [{"poseName": "initial", "renderedFrameIndex": 8}],
                "rustGalWorldFallingBlocks": [],
                "rustGalWorldFallingBlockRouteDecisions": [
                    {
                        "frameIndex": 8,
                        "route": "java-legacy",
                        "blockId": "minecraft:sand",
                        "rustSelected": False,
                        "rustQueued": False,
                        "javaDrawn": True,
                    }
                ],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n"
                "-Dmattmc.dev.rustGalWorldFallingBlock.legacyControl=true\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
            )

            self.assertTrue(artifact["validation"]["complete"], artifact["validation"]["messages"])
            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertEqual(0, counters["world_mesh_instances_executed"])
            self.assertEqual({"deterministic:java-legacy": 1}, counters["world_mesh_falling_block_route_counts"])

    def test_falling_block_world_scenario_does_not_fail_subsystem_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_subsystem_benchmark(capture)
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                mode,
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="subsystem",
            )

            self.assertTrue(artifact["validation"]["complete"], artifact["validation"]["messages"])
            self.assertFalse(
                any("FallingBlock" in message for message in artifact["validation"]["messages"]),
                artifact["validation"]["messages"],
            )

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
            self.assertIn("meaningful Vulkan workload", " ".join(artifact["validation"]["messages"]))

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

    def test_tracy_collection_does_not_let_java_protocol_failure_mask_rust_trace(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            rust_capture = temp / "rust.tracy"
            rust_capture.write_bytes(b"rust")
            java_capture = temp / "java.tracy"
            java_capture.write_bytes(b"java")
            java_failed = {
                "status": "failed",
                "capture_path": str(java_capture),
                "size_bytes": java_capture.stat().st_size,
                "zone_count": 0,
                "role_detection": {"rust": False, "java": False},
                "failure": "incompatible protocol version",
                "attach": {"port": 8086, "listener": {"process": "java"}},
            }
            rust_summary = {
                "status": "complete",
                "capture_path": str(rust_capture),
                "size_bytes": rust_capture.stat().st_size,
                "zone_count": 2,
                "zones": {"opengl.backend.submit": {}},
                "major_zones": {"opengl.backend.submit": {}},
                "role_detection": {"rust": True, "java": False},
                "attach": {"port": 8087, "listener": {"process": "java"}},
            }
            accepted = json.loads(
                harness.write_tracy_collection_summary(
                    temp,
                    [java_failed, rust_summary],
                    require_rust_zones=True,
                    require_java_zones=False,
                    failure="first listener used incompatible protocol version",
                ).read_text(encoding="utf-8")
            )
            self.assertEqual("complete", accepted["status"])
            self.assertEqual(str(rust_capture), accepted["role_detection"]["selected_rust_capture"])
            self.assertIn("first listener", accepted["diagnosis"]["non_blocking_tool_failure"])

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

    def test_renderdoc_trigger_only_failure_reports_reached_api(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir)
            log = capture / "runClient_20260101_000000.log"
            log.write_text(
                "\n".join(
                    [
                        "RenderDoc API initialized pathTemplate=/tmp/capture",
                        "Triggered RenderDoc capture for next deterministic frame (initial#7)",
                    ]
                ),
                encoding="utf-8",
            )
            diagnosis = harness.diagnose_renderdoc_capture_failure(capture, capture / "missing.rdc")
            self.assertTrue(diagnosis["renderdoc_api_initialized"])
            self.assertTrue(diagnosis["frame_capture_triggered"])
            self.assertIn("no .rdc was persisted", diagnosis["likely_cause"])

    def test_renderdoc_end_result_parser_accepts_backend_and_window_fields(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir)
            log = capture / "runClient_20260101_000000.log"
            log.write_text(
                "RenderDoc API initialized pathTemplate=/tmp/capture\n"
                "Started RenderDoc frame capture (rust-vulkan#1) backend=vulkan windowPointer=123\n"
                "Ended RenderDoc frame capture (rust-vulkan#1) backend=vulkan windowPointer=123 result=0\n",
                encoding="utf-8",
            )
            diagnosis = harness.diagnose_renderdoc_capture_failure(capture, capture / "missing.rdc")
            self.assertTrue(diagnosis["frame_capture_started"])
            self.assertTrue(diagnosis["frame_capture_ended"])
            self.assertEqual(diagnosis["end_results"][0]["result"], "0")

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

    def test_renderdoc_game_capture_is_wrapped_inside_capture_runner(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            renderdoccmd = root / "renderdoccmd"
            renderdoccmd.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            renderdoccmd.chmod(0o755)
            original = harness.local_renderdoccmd_path
            try:
                harness.local_renderdoccmd_path = lambda: str(renderdoccmd)  # type: ignore[assignment]
                args = Namespace(
                    profile="smoke",
                    validation="off",
                    client_args="",
                    jvm_arg=[],
                    rust_gal_gui_control="rust",
                    armor_value=None,
                    player_health=None,
                    player_max_health=None,
                    player_absorption=None,
                    player_food_level=None,
                    player_food_saturation=None,
                    player_food_hunger_effect=False,
                    player_food_jitter=False,
                    player_air_supply=None,
                    player_max_air_supply=None,
                    player_underwater=False,
                    player_air_pop=False,
                    mount_present=False,
                    mount_health=None,
                    mount_max_health=None,
                    mount_health_rows=None,
                    player_heart_variant=None,
                    player_heart_flash=False,
                    player_heart_hardcore=False,
                    player_heart_regeneration=False,
                    game_mode=None,
                world_outline_scenario="full-cube",
                world_outline_style="normal",
                world_outline_depth_policy="test-write",
                world_outline_depth_probe=True,
                world_material_marker_scenario="light-15",
                world_mesh_block_display_scenario="stone",
                gui_resource_pack_scenario="",
                world="Origin",
                max_secs=1,
                    dump_secs=1,
                    client_rss_limit_mb=128,
                    diagnostic=True,
                    warmup_frames=0,
                    measure_frames=1,
                    settle_frames=0,
                    max_settle_frames=1,
                    subsystem_iterations=1,
                    tracy_capture=False,
                    tracy_duration_seconds=1,
                    tracy_max_size_mb=8,
                    renderdoc_capture=True,
                    renderdoc_frame=8,
                )
                command, env = harness.build_capture_command(target, harness.MATRIX_MODES[6], root / "capture", "correctness", args, "capture")
                self.assertNotEqual(Path(command[0]).name, "renderdoccmd")
                self.assertEqual(env["MATTMC_RENDERDOC_CMD"], str(renderdoccmd))
                self.assertEqual(env["MATTMC_RENDERDOC_CAPTURE"], "true")
                self.assertIn("mattmc.dev.renderdocCapture=true", env["JAVA_TOOL_OPTIONS"])
                self.assertIn("mattmc.dev.rustGalWorldMaterial.blockMarkerScenario=light-15", env["JAVA_TOOL_OPTIONS"])
                self.assertIn("mattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone", env["JAVA_TOOL_OPTIONS"])
                _, gameplay_env = harness.build_capture_command(
                    target,
                    harness.MATRIX_MODES[0],
                    root / "gameplay",
                    "correctness",
                    args,
                    "gameplay",
                )
                self.assertIn(
                    "mattmc.dev.rustGalWorldMesh.blockDisplayScenario=stone",
                    gameplay_env["JAVA_TOOL_OPTIONS"],
                )
            finally:
                harness.local_renderdoccmd_path = original  # type: ignore[assignment]

    def test_capture_runner_receives_profile_shutdown_budget(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            args = Namespace(
                profile="standard",
                validation="off",
                client_args="",
                jvm_arg=[],
                rust_gal_gui_control="rust",
                gui_resource_pack_scenario="",
                world="Origin",
                max_secs=120,
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
                startup_timeout_seconds=None,
                readiness_timeout_seconds=None,
                warmup_timeout_seconds=None,
                measurement_timeout_seconds=None,
                shutdown_timeout_seconds=37,
                cleanup_timeout_seconds=None,
            )
            _, env = harness.build_capture_command(target, harness.MATRIX_MODES[0], root / "capture", "correctness", args, "capture")
            self.assertEqual("37", env["MATTMC_DETERMINISTIC_SHUTDOWN_GRACE_SECS"])

    def test_shell_capture_runner_receives_deterministic_capture_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "frozen")
            (target.root / "DevUtils" / "Common" / "capture_runner.py").unlink()
            shell_runner = target.root / "DevUtils" / "Common" / "capture_runner.sh"
            shell_runner.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            shell_runner.chmod(0o755)
            args = Namespace(
                profile="standard",
                validation="off",
                client_args="",
                jvm_arg=[],
                rust_gal_gui_control="rust",
                gui_resource_pack_scenario="",
                world="Origin",
                max_secs=120,
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
                startup_timeout_seconds=None,
                readiness_timeout_seconds=None,
                warmup_timeout_seconds=None,
                measurement_timeout_seconds=None,
                shutdown_timeout_seconds=37,
                cleanup_timeout_seconds=None,
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            self.assertEqual("true", env["MATTMC_GRAPHICS_CORRECTNESS_CAPTURE"])
            self.assertIn("deterministic_camera_capture_", env["MATTMC_DETERMINISTIC_METADATA"])
            self.assertIn("deterministic_camera_capture_", env["MATTMC_DETERMINISTIC_SCREENSHOT_DIR"])
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture=true", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.metadata=", java_options)

    def test_capture_runner_wraps_actual_gradle_command_for_renderdoc(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            renderdoccmd = root / "renderdoccmd"
            renderdoccmd.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            renderdoccmd.chmod(0o755)
            runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
            runner.env = {
                "MATTMC_RENDERDOC_CAPTURE": "true",
                "MATTMC_RENDERDOC_CMD": str(renderdoccmd),
                "MATTMC_RENDERDOC_CAPTURE_TEMPLATE": str(root / "outline-capture"),
            }
            runner.root = root
            runner.artifact_dir = root
            runner.run_id = "test"
            meta_lines: list[str] = []
            runner.append_meta = meta_lines.append  # type: ignore[method-assign]
            wrapped = runner.renderdoc_wrapped_command(["./gradlew", "runClient"])
            self.assertEqual(wrapped[:2], [str(renderdoccmd), "capture"])
            self.assertEqual(wrapped[-3:], ["./gradlew", "--no-daemon", "runClient"])
            self.assertTrue(any("renderdoc_forced_gradle_no_daemon=true" in line for line in meta_lines))
            self.assertTrue(any("renderdoc_wrapped_actual_game_command=" in line for line in meta_lines))

    def test_renderdoc_workload_proof_checks_outline_and_image_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir)
            (capture / "latest_20260101_000000.log").write_text(
                "\n".join(
                    [
                        "gal.frame.acquire backend=vulkan correlation=9 frame=7 image=2 window=42",
                        "gal.frame.target.begin backend=vulkan frame=7 image=2 view=0x000000000000abcd extent=1280x720 layout=0 load=1 clear=0.080,0.310,0.740,1.000",
                        "gal.frame.target.present-ready backend=vulkan frame=7 image=2",
                        "gal.frame.present backend=vulkan correlation=9 frame=7 image=2 submission=11 status=Presented window=42",
                        "minecraft.world.block-outline.pass",
                        "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed rust_gal_world_primitive_batches_executed=1 rust_gal_world_line_segments_executed=12 rust_gal_world_line_vertices_executed=72 rust_gal_world_primitive_draws_executed=1 rust_gal_world_crack_quads_executed=6 rust_gal_world_crack_batches_executed=1 rust_gal_world_crack_draws_executed=1 rust_gal_world_background_clears_executed=1 rust_gal_world_background_diagnostic_fallbacks=0 rust_gal_world_background_sky_type=1 rust_gal_world_background_color_argb=ff78a7ff rust_gal_world_depth_attachment_creates=1 rust_gal_world_depth_attachment_reuses=0",
                    ]
                ),
                encoding="utf-8",
            )
            proof = harness.renderdoc_workload_proof(capture)
            self.assertTrue(proof["non_zero_outline_workload"])
            self.assertTrue(proof["non_zero_crack_workload"])
            self.assertTrue(proof["acquired_rendered_presented_image_identity_matches"])
            self.assertTrue(proof["depth_attachment_evidence"])
            self.assertTrue(proof["outline_marker_evidence"])
            self.assertTrue(proof["blue_diagnostic_shell_clear_expected"])
            self.assertTrue(proof["non_zero_background_workload"])
            self.assertTrue(proof["semantic_world_background_clear_expected"])
            self.assertEqual(proof["background"]["color_argb"], "ff78a7ff")

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
        performance = harness.parse_args(["gameplay", "--profile", "performance", "--dry-run"])
        self.assertEqual(performance.timeout_seconds, 300)
        self.assertGreater(performance.warmup_frames, standard.warmup_frames)
        self.assertGreater(performance.measure_frames, standard.measure_frames)
        extended = harness.parse_args(["gameplay", "--profile", "extended", "--dry-run"])
        self.assertEqual(extended.timeout_seconds, 300)
        self.assertLess(harness.child_process_timeout_seconds(smoke), harness.per_mode_timeout_seconds(smoke))
        self.assertLessEqual(
            harness.child_process_timeout_seconds(smoke) + smoke.cleanup_timeout_seconds + 1,
            60,
        )
        with self.assertRaises(SystemExit):
            harness.parse_args(["gameplay", "--profile", "extended", "--timeout-seconds", "301"])

    def test_backend_tool_profile_policy_rejects_slow_smoke_rows(self) -> None:
        java_vulkan = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-java-vulkan-shaders-on")
        rust_vulkan = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
        frozen_shaders = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
        self.assertIn("profile-not-supported", harness.profile_not_supported_reason("smoke", java_vulkan, "gameplay") or "")
        self.assertIn("profile-not-supported", harness.profile_not_supported_reason("smoke", java_vulkan, "subsystem") or "")
        self.assertIn("profile-not-supported", harness.profile_not_supported_reason("smoke", rust_vulkan, "capture") or "")
        self.assertIn("profile-not-supported", harness.profile_not_supported_reason("smoke", frozen_shaders, "subsystem") or "")
        self.assertIsNone(harness.profile_not_supported_reason("standard", java_vulkan, "gameplay"))

    def test_latest_subsystem_status_reads_terminal_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            capture = Path(temp)
            self.assertEqual((None, None), harness.latest_subsystem_status(capture))
            write_subsystem_benchmark(capture)
            status, path = harness.latest_subsystem_status(capture)
            self.assertEqual("complete", status)
            self.assertIsNotNone(path)

    def test_migration_gate_gameplay_defaults_do_not_wait_for_long_settle(self) -> None:
        args = harness.parse_args(["gameplay", "--profile", "standard", "--world-profile", "migration-gate", "--dry-run"])
        self.assertEqual(0, args.settle_frames)
        self.assertLessEqual(args.max_settle_frames, 120)

        explicit = harness.parse_args([
            "gameplay",
            "--profile",
            "standard",
            "--world-profile",
            "migration-gate",
            "--settle-frames",
            "12",
            "--max-settle-frames",
            "240",
            "--dry-run",
        ])
        self.assertEqual(12, explicit.settle_frames)
        self.assertEqual(240, explicit.max_settle_frames)

    def test_java_vulkan_defaults_use_bounded_frame_counts(self) -> None:
        args = harness.parse_args(["gameplay", "--profile", "standard", "--dry-run"])
        java_vulkan = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-java-vulkan-shaders-off")
        opengl = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-opengl-shaders-off")
        self.assertEqual(20, harness.mode_frame_count(args.warmup_frames, java_vulkan, args, "--warmup-frames"))
        self.assertEqual(60, harness.mode_frame_count(args.measure_frames, java_vulkan, args, "--measure-frames"))
        self.assertEqual(args.measure_frames, harness.mode_frame_count(args.measure_frames, opengl, args, "--measure-frames"))

        explicit = harness.parse_args(["gameplay", "--profile", "standard", "--warmup-frames", "77", "--measure-frames", "88", "--dry-run"])
        self.assertEqual(77, harness.mode_frame_count(explicit.warmup_frames, java_vulkan, explicit, "--warmup-frames"))
        self.assertEqual(88, harness.mode_frame_count(explicit.measure_frames, java_vulkan, explicit, "--measure-frames"))

    def test_readiness_state_uses_incomplete_frame_runtime_state(self) -> None:
        state = harness.readiness_state_from_text(
            "gameplay",
            "warmup",
            {
                "status": "warming_or_settling",
                "worldEntered": True,
                "runtimeState": {"screen": "none", "overlay": "none"},
                "lastReadinessBlocker": "auto-dismissed screen=LevelLoadingScreen",
            },
            None,
            "Loaded [0] waiting chunk wrappers\n",
            {"world_profile": "migration-gate"},
        )
        self.assertTrue(state["player_alive_and_controllable"])
        self.assertEqual("none", state["last_screen"])
        self.assertEqual("none", state["last_overlay"])

    def test_artifact_retention_safe_root_boundaries(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            root = temp / "artifacts"
            artifact_retention.ensure_marker(root)
            inside = root / "run"
            inside.mkdir()
            outside = temp / "source-world"
            outside.mkdir()
            artifact_retention.remove_path(root, inside)
            self.assertFalse(inside.exists())
            with self.assertRaises(RuntimeError):
                artifact_retention.remove_path(root, outside)
            with self.assertRaises(RuntimeError):
                artifact_retention.remove_path(temp / "unmarked", temp / "unmarked" / "run")

    def test_artifact_retention_ordering_success_failure_and_preserve(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            runs = []
            for index in range(4):
                run = root / f"success-{index}"
                write_retention_manifest(run, success=True)
                os.utime(run, (1000 + index, 1000 + index))
                runs.append(run)
            failed_old = root / "failed-old"
            failed_new = root / "failed-new"
            write_retention_manifest(failed_old, success=False)
            write_retention_manifest(failed_new, success=False)
            os.utime(failed_old, (900, 900))
            os.utime(failed_new, (1100, 1100))
            preserved = root / "preserved"
            write_retention_manifest(preserved, success=True)
            (preserved / ".preserve").write_text("keep\n", encoding="utf-8")
            policy = artifact_retention.policy_for("standard", root, keep_success=2, keep_failed=1, global_limit_mb=0)
            result = artifact_retention.cleanup(policy)
            removed = {Path(path).name for path in result["removed"]}
            self.assertEqual({"success-0", "success-1", "failed-old"}, removed)
            self.assertTrue((root / "success-2").exists())
            self.assertTrue((root / "success-3").exists())
            self.assertTrue(failed_new.exists())
            self.assertTrue(preserved.exists())

    def test_preserve_current_run_marker_does_not_disable_temp_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            current = root / "matrix" / "20260101-000000"
            current.mkdir(parents=True)
            (current / ".preserve").write_text("evidence\n", encoding="utf-8")
            copied = current / "mode" / "capture" / "run-01" / "capture" / "game_dir_20260101_000000"
            copied.mkdir(parents=True)
            (copied / "copy.bin").write_bytes(b"x")
            write_retention_manifest(current, success=True)
            policy = artifact_retention.policy_for("smoke", root, keep_success=0, keep_failed=0, global_limit_mb=0)
            result = artifact_retention.cleanup(policy, after_run=current)
            self.assertTrue(current.exists())
            self.assertFalse(copied.exists())
            self.assertIn(str(copied), result["removed_game_dirs"])

    def test_artifact_retention_preflight_free_space_rejection(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            policy = artifact_retention.policy_for("smoke", root, reserve_mb=10**9)
            with self.assertRaises(RuntimeError):
                artifact_retention.preflight_disk_budget(policy, estimated_bytes=1)

    def test_artifact_retention_live_quota_failure_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            run = root / "capture-run"
            run.mkdir()
            (run / "large.bin").write_bytes(b"x" * 2048)
            policy = artifact_retention.RetentionPolicy(
                "smoke",
                root.resolve(),
                global_limit_bytes=0,
                run_limit_bytes=1024,
                reserve_bytes=0,
                keep_success=1,
                keep_failed=1,
                heavy_keep=0,
            )
            with self.assertRaises(RuntimeError):
                artifact_retention.run_size_check(policy, run)
            self.assertTrue((run / "quota_failure_manifest.json").is_file())

    def test_artifact_retention_live_quota_excludes_copied_game_dirs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            run = root / "capture-run"
            copied_game_dir = run / "capture" / "game_dir_20260101_000000"
            copied_game_dir.mkdir(parents=True)
            (copied_game_dir / "huge-copy.bin").write_bytes(b"x" * 2048)
            (run / "graphics_audit_artifact.json").write_text("{}\n", encoding="utf-8")
            policy = artifact_retention.RetentionPolicy(
                "smoke",
                root.resolve(),
                global_limit_bytes=0,
                run_limit_bytes=1024,
                reserve_bytes=0,
                keep_success=1,
                keep_failed=1,
                heavy_keep=0,
            )
            artifact_retention.run_size_check(policy, run)
            self.assertFalse((run / "quota_failure_manifest.json").exists())

    def test_artifact_retention_removes_copied_game_dirs_only_inside_marked_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            root = temp / "artifacts"
            artifact_retention.ensure_marker(root)
            copied = root / "capture" / "run-01" / "game_dir_20260101_000000" / "saves" / "Origin"
            copied.mkdir(parents=True)
            (copied / "level.dat").write_text("copy\n", encoding="utf-8")
            source_world = temp / "run" / "saves" / "Origin"
            source_world.mkdir(parents=True)
            (source_world / "level.dat").write_text("source\n", encoding="utf-8")
            removed = artifact_retention.remove_copied_game_dirs(root)
            self.assertEqual(len(removed), 1)
            self.assertFalse((root / "capture" / "run-01" / "game_dir_20260101_000000").exists())
            self.assertTrue(source_world.exists())

    def test_artifact_retention_removes_generated_tmp_for_capture_run(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            capture = root / "capture" / "run-01" / "capture"
            capture.mkdir(parents=True)
            (capture / "meta_20260101_000000.txt").write_text("run_id=20260101_000000\n", encoding="utf-8")
            stale_tmp = root / ".tmp" / "20260101_000000" / "game_dir_20260101_000000"
            stale_tmp.mkdir(parents=True)
            unrelated_tmp = root / ".tmp" / "20260101_000001" / "game_dir_20260101_000001"
            unrelated_tmp.mkdir(parents=True)
            removed = artifact_retention.remove_generated_temp_dirs_for_capture(root, capture)
            self.assertEqual([root / ".tmp" / "20260101_000000"], removed)
            self.assertFalse((root / ".tmp" / "20260101_000000").exists())
            self.assertTrue(unrelated_tmp.exists())

    def test_artifact_retention_removes_only_stale_unowned_generated_tmp_dirs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            stale = root / ".tmp" / "20260101_000000" / "game_dir_20260101_000000"
            fresh = root / ".tmp" / "20260101_000001" / "game_dir_20260101_000001"
            live = root / ".tmp" / "20260101_000002" / "game_dir_20260101_000002"
            unrelated = root / ".tmp" / "not-a-run-id"
            for path in (stale, fresh, live, unrelated):
                path.mkdir(parents=True)
            old_time = time_value = 1000
            os.utime(stale.parents[0], (old_time, old_time))
            os.utime(live.parents[0], (old_time, old_time))
            original = artifact_retention._live_process_references
            try:
                artifact_retention._live_process_references = lambda path, run_id: run_id == "20260101_000002"
                removed = artifact_retention.remove_stale_generated_temp_dirs(root, older_than_seconds=1)
            finally:
                artifact_retention._live_process_references = original
            self.assertIn(root / ".tmp" / "20260101_000000", removed)
            self.assertFalse((root / ".tmp" / "20260101_000000").exists())
            self.assertTrue((root / ".tmp" / "20260101_000001").exists())
            self.assertTrue((root / ".tmp" / "20260101_000002").exists())
            self.assertTrue(unrelated.exists())

    def test_live_capture_phase_reports_matrix_milestones(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir) / "capture"
            capture.mkdir()
            self.assertEqual(("startup", "waiting for capture metadata"), harness.live_capture_phase(capture, "capture"))
            meta = capture / "meta_20260101_000000.txt"
            meta.write_text("gradle_pid=123\n", encoding="utf-8")
            self.assertEqual("process-launched", harness.live_capture_phase(capture, "capture")[0])
            meta.write_text("gradle_pid=123\nclient_pid=456\n", encoding="utf-8")
            self.assertEqual("readiness", harness.live_capture_phase(capture, "capture")[0])
            meta.write_text("gradle_pid=123\nclient_pid=456\ndeterministic_capture_ack=ack.json\n", encoding="utf-8")
            self.assertEqual("measurement/capture", harness.live_capture_phase(capture, "capture")[0])
            meta.write_text("gradle_pid=123\ndeterministic_capture_complete_elapsed=9\n", encoding="utf-8")
            self.assertEqual("shutdown", harness.live_capture_phase(capture, "capture")[0])

    def test_artifact_retention_renderdoc_tracy_heavy_runs_are_strict(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            heavy_old = root / "heavy-old"
            heavy_new = root / "heavy-new"
            write_retention_manifest(heavy_old, success=True)
            write_retention_manifest(heavy_new, success=True)
            (heavy_old / "capture.rdc").write_bytes(b"rdc")
            (heavy_new / "capture.tracy").write_bytes(b"tracy")
            os.utime(heavy_old, (1000, 1000))
            os.utime(heavy_new, (1100, 1100))
            policy = artifact_retention.policy_for("diagnostic", root, keep_success=10, global_limit_mb=0)
            result = artifact_retention.cleanup(policy)
            removed = {Path(path).name for path in result["removed"]}
            self.assertIn("heavy-old", removed)
            self.assertTrue(heavy_new.exists())

    def test_artifact_retention_compresses_large_sidecars_but_not_manifests(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            run = root / "run"
            write_retention_manifest(run, success=True)
            sidecar = run / "runClient.log"
            sidecar.write_text("x" * 2048, encoding="utf-8")
            manifest = run / harness.MANIFEST_NAME

            compressed = artifact_retention.compress_large_text_artifacts(root, run, threshold_bytes=1024)

            self.assertEqual([sidecar.with_name("runClient.log.gz")], compressed)
            self.assertFalse(sidecar.exists())
            self.assertTrue(sidecar.with_name("runClient.log.gz").is_file())
            self.assertTrue(manifest.is_file())

    def test_artifact_retention_concurrent_preserved_runs_are_not_deleted(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            active = root / "active-run"
            old = root / "old-run"
            write_retention_manifest(active, success=False)
            write_retention_manifest(old, success=False)
            (active / ".preserve").write_text("in-progress\n", encoding="utf-8")
            os.utime(active, (1000, 1000))
            os.utime(old, (900, 900))
            policy = artifact_retention.policy_for("smoke", root, keep_failed=0, global_limit_mb=0)
            artifact_retention.cleanup(policy)
            self.assertTrue(active.exists())
            self.assertFalse(old.exists())

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

    def test_direct_trigger_and_proc_memory_metrics_are_normalized(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            (capture / "runClient_20260101_000000.log").write_text(
                "direct_trigger_diagnostics summary integrations=7 catchup_integrations=3 "
                "average_delay_frames=2.5 max_delay_frames=5 average_catchup_movement=1.25 "
                "max_catchup_movement=8.0 native_process_calls=9 native_integrate_calls=7 "
                "native_catchup_integrations=3 native_angle_path_integrations=2 "
                "native_distance_path_integrations=5 native_invalid_angle_input_fallbacks=1\n",
                encoding="utf-8",
            )
            (capture / "process_snapshot_20260101_000000.txt").write_text(
                "===== /proc/123/smaps_rollup memory fields =====\n"
                "Rss:             9000 kB\n"
                "Pss:             8000 kB\n"
                "Private_Dirty:   7000 kB\n"
                "Swap:             256 kB\n",
                encoding="utf-8",
            )
            artifact = harness.normalize_capture_artifact(
                target, harness.MATRIX_MODES[0], capture, "correctness", False, [], 0, False
            )
            direct = artifact["metrics"]["native_direct_triggers"]
            memory = artifact["metrics"]["rss_and_native_memory"]
            self.assertEqual(3, direct["catchup_integrations"])
            self.assertEqual(1, direct["native_invalid_angle_input_fallbacks"])
            self.assertEqual(9000, memory["last_proc_rss_kb"])
            self.assertEqual(256, memory["last_proc_swap_kb"])

    def test_capture_normalization_tails_large_client_logs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(capture, [16_000_000, 17_000_000, 15_500_000])
            log = capture / "runClient_20260101_000000.log"
            log.write_text(
                ("renderdoc wrapper verbose line without counters\n" * 80_000)
                + "rust_gal_world_mesh_instances_executed=7\n"
                + "rust_gal_world_mesh_batches_executed=2\n"
                + "rust_gal_world_mesh_draws_executed=2\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target, harness.MATRIX_MODES[0], capture, "correctness", True, [], 0, False
            )

            tailed = harness.file_text(log)
            counters = artifact["metrics"]["rust_gal_slice"]
            self.assertIn("graphics-audit log truncated", tailed)
            self.assertEqual(7, counters["world_mesh_instances_executed"])
            self.assertEqual(2, counters["world_mesh_batches_executed"])
            self.assertEqual(2, counters["world_mesh_draws_executed"])

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

    def test_shell_capture_command_preserves_quickplay_world_names_with_spaces(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "frozen")
            (target.root / "DevUtils" / "Common" / "capture_runner.py").unlink()
            (target.root / "DevUtils" / "Common" / "capture_runner.sh").write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            args = Namespace(
                profile="smoke",
                validation="off",
                client_args="--quickPlaySingleplayer=OldWorld --width 640 --height 360",
                world="Origin Prime City 3",
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
            self.assertIn("--quickPlaySingleplayer='Origin Prime City 3'", joined)
            self.assertNotIn("OldWorld", joined)
            self.assertNotIn("Prime City 3 --quickPlaySingleplayer", joined)

    def test_capture_command_supplies_deterministic_capture_handshake(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "frozen")
            args = Namespace(
                profile="smoke",
                validation="off",
                client_args="",
                jvm_arg=[],
                world="Origin",
                world_profile="migration-gate",
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
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("--deterministic-camera-capture", command)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=", java_options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.metadata=", java_options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.screenshotDir=", java_options)

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

    def test_capture_runner_rust_vulkan_uses_vulkan_backend_and_whole_frame_property(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game_dir = root / "game"
            game_dir.mkdir()
            (game_dir / "options.txt").write_text("graphics_backend=opengl\n", encoding="utf-8")
            config = capture_runner.CaptureConfig(
                backend="rust-vulkan",
                shaders="off",
                max_secs=1,
                dump_secs=1,
                client_rss_limit_mb=128,
                screenshot_interval_secs=0,
                screenshot_max_count=0,
                screenshot_start_delay_secs=0,
                validation_mode="off",
                shader_input_parity="off",
                shader_input_parity_max_logs=0,
                lightmap_info_parity_max_logs=0,
                skip_tests=True,
                client_args="",
                deterministic_camera_capture=False,
                deterministic_static_camera_capture=False,
                deterministic_pose_tolerance=0.001,
                audio_validation=False,
                capture_meshing_corpus=False,
                meshing_corpus_output="",
                meshing_corpus_fixture="all",
                meshing_corpus_warmup=0,
                meshing_corpus_measure=0,
                artifact_dir=str(root / "artifacts"),
                platform_name="linux",
                world="Origin",
                game_dir=str(game_dir),
                jvm_args=[],
                gui_resource_pack_scenario="vanilla",
                region_validation=False,
                region_validation_copy_world=False,
                poi_validation=False,
            )
            runner = capture_runner.CaptureRunner(config)
            runner.configure_backend_and_validation()
            runner.configure_java_tool_options()
            self.assertIn("graphics_backend=vulkan", (game_dir / "options.txt").read_text(encoding="utf-8"))
            self.assertIn("-Dmattmc.dev.rustGalVulkanWholeFrame=true", runner.env["JAVA_TOOL_OPTIONS"])

    def test_rust_vulkan_shell_correctness_capture_is_static_single_pose(self) -> None:
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
                player_health=None,
                player_max_health=None,
                player_absorption=None,
                player_food_level=None,
                player_food_saturation=None,
                player_food_hunger_effect=False,
                player_food_jitter=False,
                player_air_supply=None,
                player_max_air_supply=None,
                player_underwater=False,
                player_air_pop=False,
                mount_present=False,
                mount_health=None,
                mount_max_health=None,
                mount_health_rows=None,
                player_heart_variant=None,
                player_heart_flash=False,
                player_heart_hardcore=False,
                player_heart_regeneration=False,
                game_mode=None,
                world="Origin",
                world_profile="migration-gate",
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
                world_outline_scenario="full-cube",
                world_outline_style="normal",
                world_outline_depth_policy="test-write",
                world_outline_depth_probe=True,
                world_outline_real_target=False,
                world_outline_aim_real_target=False,
                block_outline_pick_diagnostics=False,
                world_border_scenario="near",
                world_border_scroll_phase="0.25",
                world_background_scenario="overworld-day",
                gui_resource_pack_scenario="vanilla",
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0", java_options)

    def test_world_outline_controls_are_forwarded_as_java_properties(self) -> None:
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
                player_health=None,
                player_max_health=None,
                player_absorption=None,
                player_food_level=None,
                player_food_saturation=None,
                player_food_hunger_effect=False,
                player_food_jitter=False,
                player_air_supply=None,
                player_max_air_supply=None,
                player_underwater=False,
                player_air_pop=False,
                mount_present=False,
                mount_health=None,
                mount_max_health=None,
                mount_health_rows=None,
                player_heart_variant=None,
                player_heart_flash=False,
                player_heart_hardcore=False,
                player_heart_regeneration=False,
                game_mode=None,
                world_outline_scenario="full-cube",
                world_outline_style="high-contrast",
                world_outline_depth_policy="test-write",
                world_outline_depth_probe=True,
                world_outline_real_target=True,
                world_outline_aim_real_target=True,
                world_outline_pause_parity=True,
                world_outline_legacy_control=True,
                block_outline_pick_diagnostics=True,
                world_border_scenario="near",
                world_border_scroll_phase="0.25",
                gui_resource_pack_scenario="",
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
            _, env = harness.build_capture_command(target, harness.MATRIX_MODES[6], root / "capture", "correctness", args, "capture")
            parsed = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.blockOutlineDiagnostics=true", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.scenario=full-cube", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.style=high-contrast", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.depthPolicy=test-write", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.depthProbe=true", parsed)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.blockOutlineTarget=true", parsed)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.blockOutlineAimTarget=true", parsed)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.blockOutlinePauseParity=true", parsed)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=3", parsed)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0", parsed)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.blockOutlineHighContrast=true", parsed)
            self.assertIn("-Dmattmc.dev.blockOutlinePickDiagnostics=true", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.legacyControl=true", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldBorder.scenario=near", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldBorder.scrollPhase=0.25", parsed)

            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.scenario=full-cube", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.style=high-contrast", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.depthPolicy=test-write", parsed)
            self.assertIn("-Dmattmc.dev.rustGalWorldOutline.depthProbe=true", parsed)

    def test_java_tool_options_preserve_world_names_with_spaces(self) -> None:
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
                player_health=None,
                player_max_health=None,
                player_absorption=None,
                player_heart_variant=None,
                player_heart_flash=False,
                player_heart_hardcore=False,
                player_heart_regeneration=False,
                game_mode=None,
                world="Origin Prime City 3",
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
            parsed = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("--world", command)
            self.assertEqual("Origin Prime City 3", command[command.index("--world") + 1])
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.world=Origin Prime City 3", parsed)
            self.assertNotIn("Prime", parsed)

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
            self.assertIn("-Dmattmc.dev.rustGalGui.legacyControl=true", env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.armorValue=19", env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.gameMode=survival", env["JAVA_TOOL_OPTIONS"])

    def test_piston_gameplay_freezes_real_moving_pistons_and_forwards_positive_control_delay(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = harness.parse_args([
                "gameplay",
                "--profile",
                "performance",
                "--mode",
                "current-opengl-shaders-off",
                "--world-mesh-piston-scenario",
                "normal-extending",
                "--world-mesh-piston-count",
                "8",
                "--positive-control-delay-ms",
                "3",
            ])
            _, env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "gameplay", "gameplay", args, "gameplay"
            )
            options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.freezePistonProgress=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.gcBeforeMeasurement=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.positiveControlDelayNanos=3000000", options)
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.pistonScenario=normal-extending", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.measureFrames=900", options)
            self.assertEqual(env["SCREENSHOT_MAX_COUNT"], "0")

    def test_rust_gal_gui_global_controls_do_not_only_toggle_armor(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            base = dict(
                profile="smoke",
                validation="off",
                client_args="",
                jvm_arg=[],
                armor_value=None,
                player_health=None,
                player_max_health=None,
                player_absorption=None,
                player_heart_variant=None,
                player_heart_flash=False,
                player_heart_hardcore=False,
                player_heart_regeneration=False,
                game_mode=None,
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
            disabled = Namespace(**base, rust_gal_gui_control="disabled")
            _, disabled_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-disabled", "correctness", disabled, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.disabled=true", disabled_env["JAVA_TOOL_OPTIONS"])
            self.assertNotIn("-Dmattmc.dev.rustGalGui.armor.disabled=true", disabled_env["JAVA_TOOL_OPTIONS"])

            legacy = Namespace(**base, rust_gal_gui_control="legacy")
            _, legacy_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-legacy", "correctness", legacy, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.legacyControl=true", legacy_env["JAVA_TOOL_OPTIONS"])
            self.assertNotIn("-Dmattmc.dev.rustGalGui.armor.legacyControl=true", legacy_env["JAVA_TOOL_OPTIONS"])

            hunger_disabled = Namespace(**base, rust_gal_gui_control="hunger-disabled")
            _, hunger_disabled_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-hunger-disabled", "correctness", hunger_disabled, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.hunger.disabled=true", hunger_disabled_env["JAVA_TOOL_OPTIONS"])

            hunger_legacy = Namespace(**base, rust_gal_gui_control="hunger-legacy")
            _, hunger_legacy_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-hunger-legacy", "correctness", hunger_legacy, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.hunger.legacyControl=true", hunger_legacy_env["JAVA_TOOL_OPTIONS"])

            air_disabled = Namespace(**base, rust_gal_gui_control="air-disabled")
            _, air_disabled_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-air-disabled", "correctness", air_disabled, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.air.disabled=true", air_disabled_env["JAVA_TOOL_OPTIONS"])

            air_legacy = Namespace(**base, rust_gal_gui_control="air-legacy")
            _, air_legacy_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-air-legacy", "correctness", air_legacy, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.air.legacyControl=true", air_legacy_env["JAVA_TOOL_OPTIONS"])

            mount_disabled = Namespace(**base, rust_gal_gui_control="mount-health-disabled")
            _, mount_disabled_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-mount-disabled", "correctness", mount_disabled, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.mountHealth.disabled=true", mount_disabled_env["JAVA_TOOL_OPTIONS"])

            mount_legacy = Namespace(**base, rust_gal_gui_control="mount-health-legacy")
            _, mount_legacy_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-mount-legacy", "correctness", mount_legacy, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalGui.mountHealth.legacyControl=true", mount_legacy_env["JAVA_TOOL_OPTIONS"])

    def test_world_profiles_select_world_and_deterministic_readiness_policy(self) -> None:
        migration = harness.parse_args(["capture", "--profile", "smoke", "--world-profile", "migration-gate"])
        self.assertEqual("Origin", migration.world)
        self.assertTrue(harness.world_profile_dict(migration)["migration_gate_blocking"])

        stress = harness.parse_args(["capture", "--profile", "smoke", "--world-profile", "stress-diagnostic"])
        self.assertEqual("Origin Prime City 3", stress.world)
        self.assertFalse(harness.world_profile_dict(stress)["migration_gate_blocking"])

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            command, env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture", "correctness", stress, "capture"
            )
            self.assertIn("--world", command)
            self.assertEqual("Origin Prime City 3", command[command.index("--world") + 1])
            self.assertEqual("stress-diagnostic", env["MATTMC_GRAPHICS_WORLD_PROFILE"])
            self.assertEqual("false", env["MATTMC_GRAPHICS_MIGRATION_GATE_BLOCKING"])
            options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.directTriggerDiagnostics=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=sodium-terrain,distant-horizons", options)

    def test_readiness_state_classifies_affected_world_dh_generation_timeout(self) -> None:
        text = "\n".join(
            [
                "HungLo joined the game",
                "Loaded [0] waiting chunk wrappers.",
                "screen=LevelLoadingScreen overlay=LoadingOverlay",
                '"DH-World Gen Thread[1]" #1 prio=5 runnable',
            ]
        )
        state = harness.readiness_state_from_text("capture", "startup", None, None, text, {"world_profile": "stress-diagnostic"})
        self.assertTrue(state["world_entered"])
        self.assertTrue(state["required_chunks_loaded"])
        self.assertEqual("dh-generation", state["timeout_classification"])
        self.assertFalse(state["player_alive_and_controllable"])
        self.assertEqual("generating", state["dh"]["state"])

    def test_gameplay_complete_status_overrides_stale_startup_screen_logs(self) -> None:
        text = "\n".join(
            [
                "HungLo joined the game",
                "Loaded [0] waiting chunk wrappers.",
                "screen=LevelLoadingScreen overlay=LoadingOverlay",
            ]
        )
        frame_doc = {
            "status": "complete",
            "worldEntered": True,
            "runtimeState": {"screen": "none", "overlay": "none", "loadedChunks": 473},
        }

        state = harness.readiness_state_from_text("gameplay", None, frame_doc, None, text, {"world_profile": "migration-gate"})

        self.assertTrue(state["world_entered"])
        self.assertTrue(state["player_alive_and_controllable"])
        self.assertEqual("none", state["last_screen"])
        self.assertEqual("none", state["last_overlay"])

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
                player_food_level=19,
                player_food_saturation=0.0,
                player_food_hunger_effect=True,
                player_food_jitter=True,
                player_air_supply=42,
                player_max_air_supply=300,
                player_underwater=True,
                player_air_pop=True,
                mount_present=True,
                mount_health=7.0,
                mount_max_health=40.0,
                mount_health_rows=2,
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
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerFoodLevel=19", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerFoodLevel=19", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerFoodSaturation=0.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerFoodSaturation=0.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerFoodHungerEffect=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerFoodJitter=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerAirSupply=42", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerAirSupply=42", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerMaxAirSupply=300", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerMaxAirSupply=300", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerUnderwater=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerUnderwater=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.playerAirPop=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerAirPop=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.mountPresent=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.mountPresent=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.mountHealth=7.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.mountHealth=7.0", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.mountMaxHealth=40.0", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.mountMaxHealth=40.0", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.mountHealthRows=2", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.mountHealthRows=2", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHeartVariant=poisoned", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHealthFlash=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHealthHardcore=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.playerHealthRegeneration=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.gameMode=survival", options)

    def test_capture_passes_gui_resource_pack_scenario(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = Namespace(
                profile="smoke",
                validation="off",
                client_args="",
                jvm_arg=[],
                rust_gal_gui_control="rust",
                armor_value=20,
                player_health=20.0,
                player_max_health=20.0,
                player_absorption=4.0,
                player_heart_variant=None,
                player_heart_flash=False,
                player_heart_hardcore=False,
                player_heart_regeneration=False,
                game_mode="survival",
                gui_resource_pack_scenario="priority-a-b",
                world="Origin",
                max_secs=1,
                dump_secs=1,
                client_rss_limit_mb=128,
                diagnostic=True,
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
            command, _ = harness.build_capture_command(target, harness.MATRIX_MODES[0], root / "capture", "correctness", args, "capture")

            self.assertIn("--gui-resource-pack-scenario", command)
            self.assertIn("priority-a-b", command)

    def test_generated_gui_resource_pack_priority_order_is_encoded_in_options(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            game_dir = Path(temp)
            (game_dir / "resourcepacks").mkdir()
            options = game_dir / "options.txt"
            options.write_text("resourcePacks:[]\nincompatibleResourcePacks:[]\n", encoding="utf-8")
            specs = capture_runner.gui_resource_pack_specs("priority-a-b")
            selected = []
            for spec in specs:
                capture_runner.write_gui_resource_pack(game_dir / "resourcepacks" / str(spec["name"]), spec)
                selected.append(f"file/{spec['name']}")
            capture_runner.upsert_option(options, "resourcePacks", json.dumps(selected, separators=(",", ":")))

            text = options.read_text(encoding="utf-8")
            self.assertIn('resourcePacks:["file/mattmc-rust-gui-pack-a","file/mattmc-rust-gui-pack-b"]', text)
            self.assertTrue((game_dir / "resourcepacks" / "mattmc-rust-gui-pack-b" / "assets/minecraft/textures/gui/sprites/hud/armor_full.png").is_file())
            self.assertTrue((game_dir / "resourcepacks" / "mattmc-rust-gui-pack-b" / "assets/minecraft/textures/misc/forcefield.png").is_file())
            self.assertTrue((game_dir / "resourcepacks" / "mattmc-rust-gui-pack-b" / "assets/minecraft/textures/block/stone.png").is_file())
            self.assertTrue((game_dir / "resourcepacks" / "mattmc-rust-gui-pack-b" / "assets/minecraft/textures/block/oak_leaves.png").is_file())

    def test_rust_opengl_attribution_is_not_confused_by_vulkanicgal_name(self) -> None:
        mode = harness.ModeSpec("current-opengl-shaders-on", "current", "opengl", "on", "java-opengl", False)
        logs = "Rust OpenGL VulkanicGAL GUI frame executed rust_gal_frames_executed=1 rust_gal_ffi_submit_calls=1 ffi_call_count=3"

        self.assertEqual("mixed-java-opengl-rust-opengl", harness.detect_attribution(mode, {}, logs))
        self.assertEqual(
            "mixed-java-opengl-rust-opengl",
            harness.detect_attribution(mode, {"render_implementation": "rust-vulkan"}, logs),
        )
        self.assertEqual(
            {"frame.base": "java-opengl", "gui.migrated": "rust-opengl"},
            harness.implementation_attribution_families(mode, "mixed-java-opengl-rust-opengl", logs),
        )

    def test_zero_work_rust_gal_metrics_do_not_relabel_java_vulkan(self) -> None:
        mode = harness.ModeSpec("current-java-vulkan-shaders-off", "current", "vulkan", "off", "java-vulkan", True)
        logs = (
            "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
            "rust_gal_frames_executed=0 rust_gal_batches_executed=0 "
            "rust_gal_ffi_frame_acquire_calls=0 rust_gal_ffi_submit_calls=0 ffi_call_count=0"
        )

        self.assertEqual("java-vulkan", harness.detect_attribution(mode, {}, logs))
        self.assertEqual(
            {"frame.base": "java-vulkan"},
            harness.implementation_attribution_families(mode, "java-vulkan", logs),
        )

    def test_rust_vulkan_attribution_still_detects_specific_vulkan_backend(self) -> None:
        mode = harness.ModeSpec("current-rust-vulkan-shaders-off", "current", "rust-vulkan", "off", "rust-vulkan", True)
        logs = "Rust Vulkan backend submitted frame"

        self.assertEqual("rust-vulkan", harness.detect_attribution(mode, {}, logs))

    def test_rust_vulkanic_gal_whole_frame_metrics_do_not_look_like_opengl(self) -> None:
        mode = harness.ModeSpec("current-rust-vulkan-shaders-off", "current", "rust-vulkan", "off", "rust-vulkan", True)
        logs = "Rust VulkanicGAL GUI frame executed producer=gui.frame frame=9 submission=10"

        self.assertEqual("rust-vulkan", harness.detect_attribution(mode, {}, logs))
        self.assertEqual(
            {"frame.base": "rust-vulkan"},
            harness.implementation_attribution_families(mode, "rust-vulkan", logs),
        )

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
                "rust_gal_ffi_asset_update_calls=1 "
                "rust_gal_ffi_context_create_bytes=40 rust_gal_ffi_capability_bytes=24 "
                "rust_gal_ffi_frame_configure_bytes=64 rust_gal_ffi_frame_acquire_bytes=48 "
                "rust_gal_ffi_frame_present_bytes=40 rust_gal_ffi_resource_batch_bytes=3000 "
                "rust_gal_ffi_submit_bytes=720 rust_gal_ffi_completion_query_bytes=32 rust_gal_ffi_retire_bytes=128 "
                "rust_gal_ffi_asset_update_bytes=2048 "
                "ffi_call_count=11 ffi_bytes=4096\n"
                "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "stratum=gui.frame frame_batch_count=1 frame=4 submission=5 rust_gal_cache_hits=8 "
                "rust_gal_cache_misses=1 rust_gal_queue_depth=0 rust_gal_batches_executed=2 "
                "rust_gal_world_primitive_batches_executed=1 rust_gal_world_line_segments_executed=12 "
                "rust_gal_world_line_vertices_executed=72 rust_gal_world_primitive_draws_executed=1 "
                "rust_gal_world_crack_quads_executed=6 rust_gal_world_crack_batches_executed=1 "
                "rust_gal_world_crack_draws_executed=1 "
                "rust_gal_world_border_quads_executed=2 rust_gal_world_border_batches_executed=1 "
                "rust_gal_world_border_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_world_depth_attachment_reuses=7 "
                "rust_gal_world_depth_attachment_retires=2 "
                "rust_gal_world_outline_cache_hits=3 rust_gal_world_outline_cache_misses=1 "
                "rust_gal_world_crack_cache_hits=4 rust_gal_world_crack_cache_misses=1 "
                "rust_gal_world_border_cache_hits=5 rust_gal_world_border_cache_misses=1 "
                "rust_gal_world_border_asset_generation=4 rust_gal_world_border_uploaded_asset_generation=4 "
                "rust_gal_world_border_asset_payload_count=1 rust_gal_world_border_asset_payload_bytes=512 "
                "rust_gal_world_border_asset_update_failures=0 "
                "rust_gal_world_border_asset_source_pack=file/mattmc-rust-gui-pack-b "
                "rust_gal_world_border_asset_sha256=abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd "
                "rust_gal_world_border_asset_fallback=false "
                "rust_gal_frame_target_generations=8 rust_gal_frame_target_identity_changes=7 "
                "rust_gal_last_frame_target_generation=9 rust_gal_last_frame_target_identity=2 "
                "rust_gal_batches_cancelled=0 rust_gal_completion_polls=0 rust_gal_completion_timeouts=0 "
                "rust_gal_ffi_context_create_calls=1 rust_gal_ffi_capability_calls=1 "
                "rust_gal_ffi_frame_configure_calls=1 rust_gal_ffi_frame_acquire_calls=2 "
                "rust_gal_ffi_frame_present_calls=2 rust_gal_ffi_resource_batch_calls=5 "
                "rust_gal_ffi_submit_calls=3 rust_gal_ffi_completion_query_calls=0 rust_gal_ffi_retire_calls=1 "
                "rust_gal_ffi_asset_update_calls=2 rust_gal_ffi_world_border_asset_update_calls=1 "
                "rust_gal_ffi_context_create_bytes=40 rust_gal_ffi_capability_bytes=24 "
                "rust_gal_ffi_frame_configure_bytes=64 rust_gal_ffi_frame_acquire_bytes=96 "
                "rust_gal_ffi_frame_present_bytes=80 rust_gal_ffi_resource_batch_bytes=3200 "
                "rust_gal_ffi_submit_bytes=1080 rust_gal_ffi_completion_query_bytes=0 rust_gal_ffi_retire_bytes=64 "
                "rust_gal_ffi_asset_update_bytes=4096 rust_gal_ffi_world_border_asset_update_bytes=576 "
                "rust_gal_enqueue_nanos=4000 rust_gal_resource_lookup_nanos=6000 "
                "rust_gal_resource_create_nanos=1000 rust_gal_abi_packing_nanos=8000 "
                "rust_gal_frame_acquire_nanos=10000 rust_gal_submit_nanos=12000 "
                "rust_gal_frame_present_nanos=14000 rust_gal_retire_nanos=16000 "
                "rust_gal_completion_query_nanos=18000 rust_gal_execute_nanos=20000 "
                "rust_gal_command_lists=3 rust_gal_command_ops=12 rust_gal_backend_submissions=3 "
                "rust_gal_backend_waits=0 rust_gal_gl_calls=17 rust_gal_gl_flushes=0 rust_gal_gl_finishes=0 "
                "rust_gal_gl_fences_inserted=3 rust_gal_gl_fences_polled=3 rust_gal_gl_fences_waited=0 "
                "rust_gal_gl_fences_deleted=1 "
                "ffi_call_count=18 ffi_bytes=8192\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI asset resolved sprite=ARMOR_FULL "
                "sprite_id=6 path=minecraft:textures/gui/sprites/hud/armor_full.png "
	                "source_pack=file/mattmc-rust-gui-pack-b bytes=345 sha256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n"
	                "[MattMC graphics audit] Rust VulkanicGAL GUI asset missing sprite=HEART_NORMAL_FULL "
	                "sprite_id=11 path=minecraft:textures/gui/sprites/hud/heart/full.png fallback=vanilla\n"
	                "[MattMC graphics audit] Rust VulkanicGAL GUI asset update accepted generation=3 payloads=15 payload_bytes=8192 uploaded_generation=3\n"
	                "[MattMC graphics audit] Rust VulkanicGAL world-border asset resolved generation=4 texture_id=1 "
	                "path=minecraft:textures/misc/forcefield.png source_pack=file/mattmc-rust-gui-pack-b "
	                "payloads=1 payload_bytes=512 fallback=false "
	                "sha256=abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd\n"
	                "[MattMC graphics audit] Rust VulkanicGAL world-border asset update accepted generation=4 "
	                "texture_id=1 payloads=1 payload_bytes=512 source_pack=file/mattmc-rust-gui-pack-b "
	                "fallback=false uploaded_generation=4\n",
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
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_primitive_batches_executed"])
            self.assertEqual(12, artifact["metrics"]["rust_gal_slice"]["world_line_segments_executed"])
            self.assertEqual(72, artifact["metrics"]["rust_gal_slice"]["world_line_vertices_executed"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_primitive_draws_executed"])
            self.assertEqual(6, artifact["metrics"]["rust_gal_slice"]["world_crack_quads_executed"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_crack_batches_executed"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_crack_draws_executed"])
            self.assertEqual(2, artifact["metrics"]["rust_gal_slice"]["world_border_quads_executed"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_border_batches_executed"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_border_draws_executed"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_depth_attachment_creates"])
            self.assertEqual(7, artifact["metrics"]["rust_gal_slice"]["world_depth_attachment_reuses"])
            self.assertEqual(2, artifact["metrics"]["rust_gal_slice"]["world_depth_attachment_retires"])
            self.assertEqual(3, artifact["metrics"]["rust_gal_slice"]["world_outline_cache_hits"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_outline_cache_misses"])
            self.assertEqual(4, artifact["metrics"]["rust_gal_slice"]["world_crack_cache_hits"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_crack_cache_misses"])
            self.assertEqual(5, artifact["metrics"]["rust_gal_slice"]["world_border_cache_hits"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_border_cache_misses"])
            self.assertEqual(4, artifact["metrics"]["rust_gal_slice"]["world_border_asset_generation"])
            self.assertEqual(4, artifact["metrics"]["rust_gal_slice"]["world_border_uploaded_asset_generation"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["world_border_asset_payload_count"])
            self.assertEqual(512, artifact["metrics"]["rust_gal_slice"]["world_border_asset_payload_bytes"])
            self.assertEqual("file/mattmc-rust-gui-pack-b", artifact["metrics"]["rust_gal_slice"]["world_border_asset_source_pack"])
            self.assertEqual("false", artifact["metrics"]["rust_gal_slice"]["world_border_asset_fallback"])
            self.assertEqual(8, artifact["metrics"]["rust_gal_slice"]["frame_target_generations"])
            self.assertEqual(7, artifact["metrics"]["rust_gal_slice"]["frame_target_identity_changes"])
            self.assertEqual(9, artifact["metrics"]["rust_gal_slice"]["last_frame_target_generation"])
            self.assertEqual(2, artifact["metrics"]["rust_gal_slice"]["last_frame_target_identity"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["batches_cancelled"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["completion_polls"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["completion_timeouts"])
            self.assertEqual(5, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["resource_batch"]["calls"])
            self.assertEqual(3200, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["resource_batch"]["bytes"])
            self.assertEqual(2, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["asset_update"]["calls"])
            self.assertEqual(4096, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["asset_update"]["bytes"])
            self.assertEqual(1, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["world_border_asset_update"]["calls"])
            self.assertEqual(576, artifact["metrics"]["rust_gal_slice"]["ffi_operations"]["world_border_asset_update"]["bytes"])
            self.assertEqual(9, artifact["metrics"]["rust_gal_slice"]["ffi_calls_per_executed_batch"])
            self.assertEqual(4096, artifact["metrics"]["rust_gal_slice"]["ffi_bytes_per_executed_batch"])
            self.assertEqual(8000, artifact["metrics"]["rust_gal_slice"]["timing_totals_nanos"]["abi_packing_nanos"])
            self.assertEqual(10000, artifact["metrics"]["rust_gal_slice"]["timing_per_executed_batch_nanos"]["execute_nanos"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_flushes"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_finishes"])
            self.assertEqual(3, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_fences_inserted"])
            self.assertEqual(0, artifact["metrics"]["rust_gal_slice"]["backend_sync"]["gl_fences_waited"])
            self.assertEqual("file/mattmc-rust-gui-pack-b", artifact["metrics"]["rust_gal_slice"]["asset_resolutions"][0]["source_pack"])
            self.assertEqual("ARMOR_FULL", artifact["metrics"]["rust_gal_slice"]["asset_resolutions"][0]["sprite"])
            self.assertEqual("HEART_NORMAL_FULL", artifact["metrics"]["rust_gal_slice"]["asset_missing_fallbacks"][0]["sprite"])
            self.assertEqual(
                "file/mattmc-rust-gui-pack-b",
                artifact["metrics"]["rust_gal_slice"]["world_border_asset_resolutions"][0]["source_pack"],
            )
            self.assertEqual(18, artifact["metrics"]["ffi"]["call_count"])
            self.assertEqual(8192, artifact["metrics"]["ffi"]["bytes"])

    def test_requested_world_outline_capture_rejects_zero_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "full-cube"
            doc["rustGalWorldOutlineStyle"] = "normal"
            doc["rustGalWorldOutlineDepthPolicy"] = "test-write"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_primitive_batches_executed=0 "
                "rust_gal_world_line_segments_executed=0 rust_gal_world_line_vertices_executed=0 "
                "rust_gal_world_primitive_draws_executed=0 rust_gal_world_depth_attachment_creates=0 "
                "rust_gal_world_depth_attachment_reuses=0 rust_gal_backend_submissions=1 ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[6], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertEqual("full-cube", artifact["metrics"]["rust_gal_slice"]["world_outline_scenario"])
            self.assertTrue(any("world-outline target requested" in message for message in artifact["validation"]["messages"]))

    def test_requested_world_border_capture_rejects_zero_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldBorder.scenario=near\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_border_quads_executed=0 "
                "rust_gal_world_border_batches_executed=0 rust_gal_world_border_draws_executed=0 "
                "rust_gal_world_depth_attachment_creates=0 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[6], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertEqual("near", artifact["metrics"]["rust_gal_slice"]["world_border_scenario"])
            self.assertTrue(any("world-border scenario requested" in message for message in artifact["validation"]["messages"]))

    def test_requested_world_border_capture_requires_visible_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            screenshot = capture / "world_border_missing.png"
            write_world_border_probe_image(screenshot, visible=False)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["realSurvivalCrackHitType"] = "BLOCK"
            doc["realSurvivalCrackTarget"] = "1, 64, 1"
            doc["realSurvivalCrackDirection"] = "NORTH"
            doc["realSurvivalCrackStatus"] = "continue"
            doc["realSurvivalCrackSetupBlock"] = True
            doc["realSurvivalCrackSetupTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackLastRenderedTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastRenderedBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackStartCalls"] = 1
            doc["realSurvivalCrackContinueCalls"] = 12
            doc["realSurvivalCrackValidBlockHitCount"] = 13
            doc["realSurvivalCrackRenderedStateCount"] = 4
            doc["realSurvivalCrackMinRenderedStage"] = 1
            doc["realSurvivalCrackMaxRenderedStage"] = 4
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldBorder.scenario=near\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_border_quads_executed=1 "
                "rust_gal_world_border_batches_executed=1 rust_gal_world_border_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[6], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_border_pixel_evidence"]
            self.assertEqual("absent", evidence["status"])
            self.assertTrue(any("world-border scenario did not produce visible" in message for message in artifact["validation"]["messages"]))

    def test_hidden_world_border_uses_zero_work_not_sky_color_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            screenshot = capture / "sky_only.png"
            from PIL import Image

            Image.new("RGB", (1280, 720), (120, 167, 255)).save(screenshot)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["realSurvivalCrackHitType"] = "BLOCK"
            doc["realSurvivalCrackTarget"] = "1, 64, 1"
            doc["realSurvivalCrackDirection"] = "NORTH"
            doc["realSurvivalCrackStatus"] = "continue"
            doc["realSurvivalCrackSetupBlock"] = True
            doc["realSurvivalCrackSetupTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackLastRenderedTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastRenderedBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackStartCalls"] = 1
            doc["realSurvivalCrackContinueCalls"] = 12
            doc["realSurvivalCrackValidBlockHitCount"] = 13
            doc["realSurvivalCrackRenderedStateCount"] = 4
            doc["realSurvivalCrackMinRenderedStage"] = 1
            doc["realSurvivalCrackMaxRenderedStage"] = 4
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldBorder.scenario=hidden "
                "-Dmattmc.dev.rustGalWorldBackground.scenario=overworld-day\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_border_quads_executed=0 "
                "rust_gal_world_border_batches_executed=0 rust_gal_world_border_draws_executed=0 "
                "rust_gal_world_background_clears_executed=1 rust_gal_world_background_color_argb=ff78a7ff "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[6], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertTrue(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_border_pixel_evidence"]
            self.assertEqual("unexpected_present", evidence["status"])
            self.assertFalse(any("hidden/far world-border scenario produced visible" in message for message in artifact["validation"]["messages"]))

    def test_requested_world_border_capture_accepts_visible_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            screenshot = capture / "world_border_visible.png"
            write_world_border_probe_image(screenshot, visible=True)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["realSurvivalCrackHitType"] = "BLOCK"
            doc["realSurvivalCrackTarget"] = "1, 64, 1"
            doc["realSurvivalCrackDirection"] = "NORTH"
            doc["realSurvivalCrackStatus"] = "continue"
            doc["realSurvivalCrackSetupBlock"] = True
            doc["realSurvivalCrackSetupTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackLastRenderedTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastRenderedBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackStartCalls"] = 1
            doc["realSurvivalCrackContinueCalls"] = 12
            doc["realSurvivalCrackValidBlockHitCount"] = 13
            doc["realSurvivalCrackRenderedStateCount"] = 4
            doc["realSurvivalCrackMinRenderedStage"] = 1
            doc["realSurvivalCrackMaxRenderedStage"] = 4
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldBorder.scenario=near\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_border_quads_executed=1 "
                "rust_gal_world_border_batches_executed=1 rust_gal_world_border_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[6], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertTrue(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_border_pixel_evidence"]
            self.assertEqual("present", evidence["status"])
            self.assertGreaterEqual(evidence["matching_pixels"], evidence["threshold"])
            self.assertTrue(Path(str(evidence["crop_path"])).is_file())

    def test_requested_world_crack_capture_requires_visible_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "world_crack_missing.png"
            write_world_crack_probe_image(screenshot, variant="missing")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldCrack.scenario=full-cube "
                "-Dmattmc.dev.rustGalWorldCrack.stage=4\n"
                "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_crack_quads_executed=6 "
                "rust_gal_world_crack_batches_executed=1 rust_gal_world_crack_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_crack_pixel_evidence"]
            self.assertEqual("absent", evidence["status"])
            self.assertTrue(any("crack scenario did not produce visible crack pixels" in message for message in artifact["validation"]["messages"]))

    def test_requested_world_crack_capture_accepts_vanilla_and_pack_pixels(self) -> None:
        for variant, signature in (("vanilla", "vanilla-like"), ("pack-b", "pack-colored")):
            with self.subTest(variant=variant), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                target = fake_repo(root, "current")
                capture = root / "capture"
                write_capture(capture, backend="opengl")
                screenshot = capture / f"world_crack_{variant}.png"
                write_world_crack_probe_image(screenshot, variant=variant)
                deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
                doc = json.loads(deterministic.read_text(encoding="utf-8"))
                doc["captures"][0]["screenshot"] = str(screenshot)
                deterministic.write_text(json.dumps(doc), encoding="utf-8")
                (capture / "runClient_20260101_000000.log").write_text(
                    "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldCrack.scenario=full-cube "
                    "-Dmattmc.dev.rustGalWorldCrack.stage=4\n"
                    "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                    "frame=4 submission=5 rust_gal_world_crack_quads_executed=6 "
                    "rust_gal_world_crack_batches_executed=1 rust_gal_world_crack_draws_executed=1 "
                    "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                    "ffi_call_count=3 ffi_bytes=256\n",
                    encoding="utf-8",
                )

                artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

                self.assertTrue(artifact["validation"]["complete"])
                evidence = artifact["metrics"]["rust_gal_slice"]["world_crack_pixel_evidence"]
                self.assertEqual("present", evidence["status"])
                self.assertEqual(signature, evidence["texture_signature"])

    def test_real_survival_world_crack_gate_requires_destroy_progress_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "world_crack_real_survival.png"
            write_world_crack_probe_image(screenshot, variant="vanilla")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["realSurvivalCrackHitType"] = "BLOCK"
            doc["realSurvivalCrackTarget"] = "1, 64, 1"
            doc["realSurvivalCrackDirection"] = "NORTH"
            doc["realSurvivalCrackStatus"] = "continue"
            doc["realSurvivalCrackSetupBlock"] = True
            doc["realSurvivalCrackSetupTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastValidBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackLastRenderedTarget"] = "1, 64, 1"
            doc["realSurvivalCrackLastRenderedBlockType"] = "minecraft:stone"
            doc["realSurvivalCrackStartCalls"] = 1
            doc["realSurvivalCrackContinueCalls"] = 12
            doc["realSurvivalCrackValidBlockHitCount"] = 13
            doc["realSurvivalCrackRenderedStateCount"] = 9
            doc["realSurvivalCrackMinRenderedStage"] = 1
            doc["realSurvivalCrackMaxRenderedStage"] = 9
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldCrack.requireRealSurvivalCapture=true\n"
                "[MattMC graphics audit] Rust VulkanicGAL block-breaking crack request "
                "route=rust-opengl real_destroy_progress=true states=1 quads=6 "
                "first=block_1_64_1_stage_4_type_minecraft:stone result=queued\n"
                "[MattMC graphics-audit] block-crack framebuffer stage=after-draw route=rust-opengl "
                "pos=1,64,1 stageIndex=1 faceCount=6 crop=10,20+32x32 drawFb=1 readFb=1 program=7 "
                "viewport=0,0,854,480 depthTest=true blend=true scissor=false changedFromBefore=12 "
                "maxDeltaFromBefore=64 sumDeltaFromBefore=1024 changedFromAfterDraw=0 "
                "maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 darkenedFootprintPixels=6 "
                "brightenedFootprintPixels=0 avgRgba=0,0,0,255 minRgb=0,0,0 maxRgb=255,255,255\n"
                "[MattMC graphics-audit] block-crack framebuffer stage=after-draw route=rust-opengl "
                "pos=1,64,1 stageIndex=9 faceCount=6 crop=10,20+32x32 drawFb=1 readFb=1 program=7 "
                "viewport=0,0,854,480 depthTest=true blend=true scissor=false changedFromBefore=14 "
                "maxDeltaFromBefore=70 sumDeltaFromBefore=1200 changedFromAfterDraw=0 "
                "maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 darkenedFootprintPixels=8 "
                "brightenedFootprintPixels=0 avgRgba=0,0,0,255 minRgb=0,0,0 maxRgb=255,255,255\n"
                "[MattMC graphics-audit] block-crack framebuffer stage=after-draw route=rust-opengl "
                "pos=1,64,1 stageIndex=5 faceCount=6 crop=10,20+32x32 drawFb=1 readFb=1 program=7 "
                "viewport=0,0,854,480 depthTest=true blend=true scissor=false changedFromBefore=13 "
                "maxDeltaFromBefore=68 sumDeltaFromBefore=1100 changedFromAfterDraw=0 "
                "maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 darkenedFootprintPixels=7 "
                "brightenedFootprintPixels=0 avgRgba=0,0,0,255 minRgb=0,0,0 maxRgb=255,255,255\n"
                "[MattMC graphics-audit] block-crack framebuffer stage=after-iris-final route=rust-opengl "
                "pos=1,64,1 stageIndex=9 faceCount=6 crop=10,20+32x32 drawFb=1 readFb=1 program=7 "
                "viewport=0,0,854,480 depthTest=true blend=true scissor=false changedFromBefore=14 "
                "maxDeltaFromBefore=70 sumDeltaFromBefore=1200 changedFromAfterDraw=0 "
                "maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 darkenedFootprintPixels=8 "
                "brightenedFootprintPixels=0 avgRgba=0,0,0,255 minRgb=0,0,0 maxRgb=255,255,255\n"
                "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_crack_quads_executed=6 "
                "rust_gal_world_crack_batches_executed=1 rust_gal_world_crack_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertTrue(artifact["validation"]["complete"])
            slice_metrics = artifact["metrics"]["rust_gal_slice"]
            self.assertTrue(slice_metrics["world_crack_real_survival_required"])
            self.assertEqual(1, slice_metrics["world_crack_real_destroy_progress_states"])
            self.assertEqual(6, slice_metrics["world_crack_real_semantic_quads"])
            self.assertEqual("minecraft:stone", slice_metrics["world_crack_real_last_rendered_block_type"])

    def test_real_survival_world_crack_gate_rejects_forced_scenario_only(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "world_crack_forced_only.png"
            write_world_crack_probe_image(screenshot, variant="vanilla")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldCrack.requireRealSurvivalCapture=true "
                "-Dmattmc.dev.rustGalWorldCrack.scenario=full-cube -Dmattmc.dev.rustGalWorldCrack.stage=4\n"
                "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_crack_quads_executed=6 "
                "rust_gal_world_crack_batches_executed=1 rust_gal_world_crack_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(any("cannot be satisfied by a forced crack scenario" in message for message in artifact["validation"]["messages"]))

    def test_real_survival_world_crack_gate_rejects_stale_air_destroy_progress(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "world_crack_stale_air.png"
            write_world_crack_probe_image(screenshot, variant="vanilla")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            doc["realSurvivalCrackHitType"] = "MISS"
            doc["realSurvivalCrackStatus"] = "no-block-hit"
            doc["realSurvivalCrackLastRenderedBlockType"] = "minecraft:air"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldCrack.requireRealSurvivalCapture=true\n"
                "[MattMC graphics audit] Rust VulkanicGAL block-breaking crack request "
                "route=rust-opengl real_destroy_progress=true states=1 quads=6 "
                "first=block_1_64_1_stage_4_type_minecraft:air result=queued\n"
                "[MattMC graphics audit] Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_crack_quads_executed=6 "
                "rust_gal_world_crack_batches_executed=1 rust_gal_world_crack_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(any("stale destroy-progress for air block" in message for message in artifact["validation"]["messages"]))

    def test_rust_vulkan_shell_scene_evidence_writes_named_crops(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            screenshot = capture / "rust_shell_scene.png"
            write_rust_shell_scene_image(screenshot)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldBackground.scenario=overworld-day\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_background_clears_executed=1 "
                "rust_gal_world_background_color_argb=ff78a7ff rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target,
                harness.MATRIX_MODES[6],
                capture,
                "capture",
                True,
                [],
                0,
                False,
                tool_kind="capture",
            )

            evidence = artifact["metrics"]["rust_gal_slice"]["rust_vulkan_shell_scene_evidence"]
            self.assertEqual("complete", evidence["status"])
            self.assertEqual([120, 167, 255], list(evidence["expected_background_rgb"]))
            regions = evidence["regions"]
            self.assertIn("background_color_field", regions)
            self.assertIn("fixed_gui_sprite_group", regions)
            self.assertTrue(Path(str(regions["background_color_field"]["crop_path"])).is_file())
            self.assertGreater(regions["fixed_gui_sprite_group"]["bright_pixels"], 0)

    def test_hidden_world_border_capture_rejects_visible_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            screenshot = capture / "world_border_visible.png"
            write_world_border_probe_image(screenshot, visible=True)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["captures"][0]["screenshot"] = str(screenshot)
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.rustGalWorldBorder.scenario=hidden\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_border_quads_executed=0 "
                "rust_gal_world_border_batches_executed=0 rust_gal_world_border_draws_executed=0 "
                "rust_gal_world_depth_attachment_creates=1 rust_gal_backend_submissions=1 "
                "ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[6], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_border_pixel_evidence"]
            self.assertEqual("unexpected_present", evidence["status"])
            self.assertTrue(any("hidden/far world-border scenario produced visible" in message for message in artifact["validation"]["messages"]))

    def test_current_opengl_world_outline_capture_uses_rust_route_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "full-cube"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline extract route=rust-opengl target=true diagnostic=full-cube pos=1, 2, 3 highContrast=false shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=rust-opengl retained=false translucentPass=false pos=1, 2, 3 highContrast=false\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_primitive_batches_executed=1 "
                "rust_gal_world_line_segments_executed=12 rust_gal_world_line_vertices_executed=72 "
                "rust_gal_world_primitive_draws_executed=1 rust_gal_world_depth_attachment_creates=1 "
                "rust_gal_backend_submissions=1 ffi_call_count=3 ffi_bytes=512\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(any("world-outline scenario requested" in message for message in artifact["validation"]["messages"]))
            self.assertFalse(any("deterministic Java block-outline scenario requested" in message for message in artifact["validation"]["messages"]))
            self.assertEqual(12, artifact["metrics"]["rust_gal_slice"]["world_line_segments_executed"])

    def test_frozen_opengl_world_outline_capture_uses_java_route_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "frozen")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "full-cube"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline extract route=java-opengl target=true diagnostic=full-cube pos=1, 2, 3 highContrast=false shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=java-opengl retained=true translucentPass=false pos=1, 2, 3 highContrast=false\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[8], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(any("world-outline scenario requested" in message for message in artifact["validation"]["messages"]))
            self.assertFalse(any("deterministic Java block-outline scenario requested" in message for message in artifact["validation"]["messages"]))

    def test_current_opengl_world_outline_legacy_control_uses_java_route_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "full-cube"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldOutline.legacyControl=true\n"
                "[MattMC graphics-audit] block-outline extract route=java-opengl target=true diagnostic=full-cube pos=1, 2, 3 highContrast=false shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=java-opengl retained=true translucentPass=false pos=1, 2, 3 highContrast=false\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertTrue(artifact["validation"]["complete"])
            self.assertFalse(any("world-outline scenario requested" in message for message in artifact["validation"]["messages"]))
            self.assertFalse(any("deterministic Java block-outline target requested" in message for message in artifact["validation"]["messages"]))

    def test_world_outline_pause_parity_requires_play_pause_unpause_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["blockOutlinePauseParity"] = True
            doc["blockOutlineRealTargetAimed"] = True
            doc["poseSequence"] = ["initial"]
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline pick type=BLOCK blockPos=1, 2, 3 face=north distance=2.0 blockRange=5.0 entityRange=5.0 shouldRender=true highContrast=false hideGui=false screen=none overlay=none\n"
                "[MattMC graphics-audit] block-outline extract route=rust-opengl target=true pos=1, 2, 3 highContrast=false shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=rust-opengl retained=false translucentPass=false pos=1, 2, 3 highContrast=false\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-draw route=rust-opengl "
                "pos=1,2,3 highContrast=false translucentPass=false crop=10,20,64x64:projected-outline "
                "drawFb=1 readFb=1 program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "edgeSamples=visibleTotal=16,visibleChanged=8,hiddenTotal=12,hiddenChanged=0 "
                "avgRgba=1,1,1,255 outlinePixels=cyan=0,black=0,normalDark=8,greenBlue=0\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-iris-final route=rust-opengl "
                "pos=1,2,3 highContrast=false translucentPass=false crop=10,20,64x64:projected-outline "
                "drawFb=1 readFb=1 program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "edgeSamples=visibleTotal=16,visibleChanged=8,hiddenTotal=12,hiddenChanged=0 "
                "avgRgba=1,1,1,255 outlinePixels=cyan=0,black=0,normalDark=8,greenBlue=0\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_primitive_batches_executed=1 "
                "rust_gal_world_line_segments_executed=12 rust_gal_world_line_vertices_executed=72 "
                "rust_gal_world_primitive_draws_executed=1 rust_gal_world_depth_attachment_creates=1 "
                "rust_gal_backend_submissions=1 ffi_call_count=3 ffi_bytes=512\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(any("pause parity requested" in message for message in artifact["validation"]["messages"]))

    def test_deterministic_validator_accepts_block_outline_pause_parity_poses(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            screenshot_dir = root / "screens"
            screenshot_dir.mkdir()
            captures = []
            for index, pose_name in enumerate(("playing", "paused", "unpaused"), start=1):
                screenshot = screenshot_dir / f"{index:02d}_{pose_name}.png"
                screenshot.write_bytes(b"not-a-real-image")
                captures.append(
                    {
                        "index": index,
                        "poseName": pose_name,
                        "screenshot": str(screenshot),
                        "backend": "opengl",
                        "shaderEnabled": False,
                        "shaderPack": "unset",
                        "gitCommit": "abc123",
                        "window": {"width": 1280, "height": 720},
                        "dimension": "minecraft:overworld",
                        "position": {"x": 1.0, "y": 80.0, "z": 2.0},
                        "requestedYaw": 12.0,
                        "requestedPitch": 5.0,
                        "observedYaw": 12.0,
                        "observedPitch": 5.0,
                        "renderedFrameIndex": index * 8,
                    }
                )
            metadata = root / "deterministic.json"
            metadata.write_text(
                json.dumps(
                    {
                        "status": "complete",
                        "backend": "opengl",
                        "shaderEnabled": False,
                        "shaderPack": "unset",
                        "gitCommit": "abc123",
                        "dimension": "minecraft:overworld",
                        "yawDelta": 0.0,
                        "initialPose": {"name": "initial", "yaw": 12.0, "pitch": 5.0},
                        "initialPosition": {"x": 1.0, "y": 80.0, "z": 2.0},
                        "window": {"width": 1280, "height": 720},
                        "poseSequence": ["playing", "paused", "unpaused"],
                        "captures": captures,
                    }
                ),
                encoding="utf-8",
            )

            capture_runner.validate_deterministic_metadata(metadata, screenshot_dir, 0.001)

    def test_java_vulkan_world_outline_capture_uses_java_route_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "full-cube"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline extract route=java-vulkan target=true diagnostic=full-cube pos=1, 2, 3 highContrast=false shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=java-vulkan retained=true translucentPass=false pos=1, 2, 3 highContrast=false\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[2], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(any("world-outline scenario requested" in message for message in artifact["validation"]["messages"]))
            self.assertFalse(any("deterministic Java block-outline scenario requested" in message for message in artifact["validation"]["messages"]))

    def test_java_world_outline_capture_rejects_missing_draw_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "full-cube"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline extract route=java-opengl target=true diagnostic=full-cube pos=1, 2, 3 highContrast=false shapeEmpty=false\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(any("world-outline target requested" in message for message in artifact["validation"]["messages"]))

    def test_real_target_high_contrast_outline_requires_visible_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "outline_probe_missing.png"
            write_outline_probe_image(screenshot, visible=False)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["blockOutlineRealTargetForced"] = True
            doc["rustGalWorldOutlineStyle"] = "high-contrast"
            doc["captures"][0]["screenshot"] = str(screenshot)
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline extract route=rust-opengl target=true pos=1, 2, 3 highContrast=true shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=rust-opengl retained=false translucentPass=false pos=1, 2, 3 highContrast=true\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-draw route=rust-opengl "
                "pos=1,2,3 highContrast=true translucentPass=false crop=10,20+64x64 drawFb=1 readFb=1 "
                "program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "avgRgba=0,255,255,255 outlinePixels=cyan=24,black=0,normalDark=0,greenBlue=24\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-iris-final route=rust-opengl "
                "pos=1,2,3 highContrast=true translucentPass=false crop=10,20+64x64 drawFb=1 readFb=1 "
                "program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "avgRgba=0,255,255,255 outlinePixels=cyan=24,black=0,normalDark=0,greenBlue=24\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_primitive_batches_executed=2 "
                "rust_gal_world_line_segments_executed=24 rust_gal_world_line_vertices_executed=144 "
                "rust_gal_world_primitive_draws_executed=2 rust_gal_world_depth_attachment_creates=1 "
                "rust_gal_backend_submissions=1 ffi_call_count=3 ffi_bytes=768\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_outline_pixel_evidence"]
            self.assertEqual("absent", evidence["status"])
            self.assertTrue(any("visible outline-colored pixels" in message for message in artifact["validation"]["messages"]))

    def test_real_target_high_contrast_outline_accepts_visible_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "outline_probe_visible.png"
            write_outline_probe_image(screenshot, visible=True)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["blockOutlineRealTargetForced"] = True
            doc["rustGalWorldOutlineStyle"] = "high-contrast"
            doc["captures"][0]["screenshot"] = str(screenshot)
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline extract route=rust-opengl target=true pos=1, 2, 3 highContrast=true shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=rust-opengl retained=false translucentPass=false pos=1, 2, 3 highContrast=true\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-draw route=rust-opengl "
                "pos=1,2,3 highContrast=true translucentPass=false crop=10,20+64x64 drawFb=1 readFb=1 "
                "program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "avgRgba=0,255,255,255 outlinePixels=cyan=24,black=0,normalDark=0,greenBlue=24\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-iris-final route=rust-opengl "
                "pos=1,2,3 highContrast=true translucentPass=false crop=10,20+64x64 drawFb=1 readFb=1 "
                "program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "avgRgba=0,255,255,255 outlinePixels=cyan=24,black=0,normalDark=0,greenBlue=24\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_primitive_batches_executed=2 "
                "rust_gal_world_line_segments_executed=24 rust_gal_world_line_vertices_executed=144 "
                "rust_gal_world_primitive_draws_executed=2 rust_gal_world_depth_attachment_creates=1 "
                "rust_gal_backend_submissions=1 ffi_call_count=3 ffi_bytes=768\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertTrue(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_outline_pixel_evidence"]
            self.assertEqual("present", evidence["status"])
            self.assertGreaterEqual(evidence["matching_pixels"], evidence["threshold"])

    def test_world_outline_framebuffer_requires_projected_hidden_edges_to_remain_unchanged(self) -> None:
        logs = (
            "[MattMC graphics-audit] block-outline framebuffer stage=after-draw route=rust-opengl "
            "pos=1,2,3 highContrast=false translucentPass=false crop=10,20,64x64:projected-outline "
            "drawFb=1 readFb=1 program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
            "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
            "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
            "edgeSamples=visibleTotal=16,visibleChanged=8,hiddenTotal=12,hiddenChanged=0 "
            "avgRgba=1,1,1,255 outlinePixels=cyan=0,black=0,normalDark=8,greenBlue=0\n"
            "[MattMC graphics-audit] block-outline framebuffer stage=after-iris-final route=rust-opengl "
            "pos=1,2,3 highContrast=false translucentPass=false crop=10,20,64x64:projected-outline "
            "drawFb=1 readFb=1 program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
            "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
            "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
            "edgeSamples=visibleTotal=16,visibleChanged=8,hiddenTotal=12,hiddenChanged=0 "
            "avgRgba=1,1,1,255 outlinePixels=cyan=0,black=0,normalDark=8,greenBlue=0\n"
        )
        evidence = harness.world_outline_framebuffer_evidence(logs)
        self.assertEqual("present_projected_edges", evidence["status"])
        self.assertEqual(8, evidence["visible_edge_changed_after_draw"])
        self.assertEqual(0, evidence["hidden_edge_changed_after_draw"])

        leaking = logs.replace("hiddenChanged=0", "hiddenChanged=3")
        self.assertEqual("hidden_edge_depth_failed", harness.world_outline_framebuffer_evidence(leaking)["status"])

    def test_aimed_real_target_high_contrast_outline_uses_pixel_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "outline_probe_visible.png"
            write_outline_probe_image(screenshot, visible=True)
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["blockOutlineRealTargetForced"] = False
            doc["blockOutlineRealTargetAimed"] = True
            doc["rustGalWorldOutlineStyle"] = ""
            doc["captures"][0]["screenshot"] = str(screenshot)
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.deterministicCameraCapture.blockOutlineHighContrast=true\n"
                "[MattMC graphics-audit] block-outline pick type=BLOCK blockPos=1, 2, 3 face=north distance=2.0 blockRange=5.0 entityRange=5.0 shouldRender=true highContrast=true hideGui=false screen=none overlay=none\n"
                "[MattMC graphics-audit] block-outline extract route=rust-opengl target=true pos=1, 2, 3 highContrast=true shapeEmpty=false\n"
                "[MattMC graphics-audit] block-outline draw route=rust-opengl retained=false translucentPass=false pos=1, 2, 3 highContrast=true\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-draw route=rust-opengl "
                "pos=1,2,3 highContrast=true translucentPass=false crop=10,20+64x64 drawFb=1 readFb=1 "
                "program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "avgRgba=0,255,255,255 outlinePixels=cyan=24,black=0,normalDark=0,greenBlue=24\n"
                "[MattMC graphics-audit] block-outline framebuffer stage=after-iris-final route=rust-opengl "
                "pos=1,2,3 highContrast=true translucentPass=false crop=10,20+64x64 drawFb=1 readFb=1 "
                "program=9 viewport=0,0,854,480 depthTest=true blend=true scissor=false "
                "changedFromBefore=24 maxDeltaFromBefore=255 sumDeltaFromBefore=8192 "
                "changedFromAfterDraw=0 maxDeltaFromAfterDraw=0 sumDeltaFromAfterDraw=0 "
                "avgRgba=0,255,255,255 outlinePixels=cyan=24,black=0,normalDark=0,greenBlue=24\n"
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_primitive_batches_executed=2 "
                "rust_gal_world_line_segments_executed=24 rust_gal_world_line_vertices_executed=144 "
                "rust_gal_world_primitive_draws_executed=2 rust_gal_world_depth_attachment_creates=1 "
                "rust_gal_backend_submissions=1 ffi_call_count=3 ffi_bytes=768\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertTrue(artifact["validation"]["complete"])
            metrics = artifact["metrics"]["rust_gal_slice"]
            self.assertTrue(metrics["world_outline_aim_real_target"])
            self.assertEqual("high-contrast", metrics["world_outline_style"])
            self.assertEqual("present", metrics["world_outline_pixel_evidence"]["status"])

    def test_saved_view_block_outline_requires_real_block_pick(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            (capture / "runClient_20260101_000000.log").write_text(
                "Picked up JAVA_TOOL_OPTIONS: -Dmattmc.dev.blockOutlineSavedViewTargetRequired=true\n"
                "[MattMC graphics-audit] block-outline pick type=MISS blockPos=353, 83, -58 face=down "
                "distance=5.0000 blockRange=5.0000 entityRange=5.0000 eye=(353.7000, 88.6040, -57.2364) "
                "yaw=0.0000 pitch=90.0000 shouldRender=true highContrast=false hideGui=false screen=none overlay=none\n"
                "[MattMC graphics-audit] block-outline extract route=java-opengl target=false\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any("saved-view block-outline target required" in message for message in artifact["validation"]["messages"])
            )

    def test_java_no_target_world_outline_capture_rejects_unexpected_draw(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "no-target"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics-audit] block-outline extract route=java-opengl target=false diagnostic=no-target\n"
                "[MattMC graphics-audit] block-outline draw route=java-opengl retained=true translucentPass=false pos=1, 2, 3 highContrast=false\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[0], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(any("unexpected java-opengl draw" in message for message in artifact["validation"]["messages"]))

    def test_requested_no_target_outline_capture_accepts_zero_segments(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="vulkan")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            doc = json.loads(deterministic.read_text(encoding="utf-8"))
            doc["rustGalWorldOutlineScenario"] = "no-target"
            deterministic.write_text(json.dumps(doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "[MattMC graphics audit] Rust VulkanicGAL GUI frame executed producer=gui.frame "
                "frame=4 submission=5 rust_gal_world_primitive_batches_executed=0 "
                "rust_gal_world_line_segments_executed=0 rust_gal_world_line_vertices_executed=0 "
                "rust_gal_world_primitive_draws_executed=0 rust_gal_world_depth_attachment_creates=0 "
                "rust_gal_backend_submissions=1 ffi_call_count=3 ffi_bytes=256\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(target, harness.MATRIX_MODES[6], capture, "capture", True, [], 0, False, tool_kind="capture")

            self.assertFalse(any("world-outline scenario requested" in message for message in artifact["validation"]["messages"]))
            self.assertEqual("no-target", artifact["metrics"]["rust_gal_slice"]["world_outline_scenario"])

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
                "rust_gal_ffi_asset_update_calls=1 "
                "rust_gal_ffi_context_create_bytes=40 rust_gal_ffi_capability_bytes=24 "
                "rust_gal_ffi_frame_configure_bytes=48 rust_gal_ffi_frame_acquire_bytes=640 "
                "rust_gal_ffi_frame_resize_bytes=0 rust_gal_ffi_frame_present_bytes=640 "
                "rust_gal_ffi_resource_batch_bytes=5192 rust_gal_ffi_submit_bytes=260000 "
                "rust_gal_ffi_completion_query_bytes=0 rust_gal_ffi_retire_bytes=0 "
                "rust_gal_ffi_asset_update_bytes=4096 "
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
