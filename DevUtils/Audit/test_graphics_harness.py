#!/usr/bin/env python3
"""Self-tests for the cross-repository graphics audit harness."""

from __future__ import annotations

import json
import gzip
import os
import re
import shlex
import subprocess
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest import mock

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
    return harness.RepoTarget(name, repo, "current-under-test" if name == "current" else "frozen-baseline")


def fake_repo_path(root: Path, name: str) -> Path:
    return fake_repo(root, name).root


def isolated_capture_config(
    root: Path,
    *,
    world: str = "Origin",
    second_world: str = "",
    scenario: str = "world-different-reload",
) -> capture_runner.CaptureConfig:
    return capture_runner.CaptureConfig(
        backend="rust-vulkan",
        shaders="on",
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
        deterministic_camera_capture=True,
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
        world=world,
        game_dir="",
        jvm_args=[],
        gui_resource_pack_scenario="",
        world_static_terrain_scenario=scenario,
        world_static_terrain_second_world=second_world,
        world_static_terrain_fault="",
        world_static_terrain_resource_pack_scenario="",
        world_static_terrain_water_animation_capture=False,
        region_validation=False,
        region_validation_copy_world=False,
        poi_validation=False,
    )


def write_capture(
    capture_dir: Path,
    *,
    backend: str = "opengl",
    shaders: str = "off",
    world: str = "Origin",
    parity_yaw: float = 105.0,
) -> None:
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
                "parity_fixture_schema=mattmc-cross-repo-fixture-v1",
                "parity_fixture_id=Origin-real-world-vanilla-mattmc-cross-repo-fixture-v1",
                "parity_fixture_manifest=/tmp/fixture_manifest.json",
                "parity_fixture_source_save_hash=abc123",
                "parity_camera_x=150.5",
                "parity_camera_y=100.0",
                "parity_camera_z=530.5",
                f"parity_camera_yaw={parity_yaw}",
                "parity_camera_pitch=10.0",
                "parity_camera_pose_sequence=single-static-v1",
                f"effective_enable_shaders={'true' if shaders == 'on' else 'false'}",
                "effective_shader_pack=ComplementaryHungLoIfied.zip",
                "shader_pack_sha256=" + ("a" * 64),
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
        f"effective_enable_shaders={'true' if shaders == 'on' else 'false'}\neffective_shader_pack=ComplementaryHungLoIfied.zip\n"
        + ("a" * 64)
        + "  ComplementaryHungLoIfied.zip\n",
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
    image = Image.new("RGB", (1280, 720), (36, 48, 120))
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
    image = Image.new("RGB", (1280, 720), (36, 48, 120))
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
    image = Image.new("RGB", (1280, 720), (36, 48, 120))
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


def write_zombie_model_probe_image(path: Path) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (80, 130, 195))
    # The fixture's mirrored crop covers this head/body stack.
    for x in range(540, 700):
        for y in range(370, 400):
            image.putpixel((x, y), (72, 104, 54))
        for y in range(400, 430):
            image.putpixel((x, y), (42, 128, 128))
        for y in range(430, 470):
            image.putpixel((x, y), (70, 60, 140))
    image.save(path)


def write_primed_tnt_probe_image(path: Path) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (36, 48, 120))
    left, top, right, bottom = 520, 250, 760, 490
    for x in range(left, right):
        for y in range(top, bottom):
            image.putpixel((x, y), (182, 48, 40) if (x + y) % 11 else (54, 50, 48))
    image.save(path)


def write_experience_orb_probe_image(path: Path, *, visible: bool) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (120, 167, 223))
    if visible:
        for center_x, center_y in ((630, 350), (646, 360), (660, 370)):
            for x in range(center_x - 10, center_x + 11):
                for y in range(center_y - 10, center_y + 11):
                    if (x - center_x) ** 2 + (y - center_y) ** 2 <= 100:
                        image.putpixel((x, y), (86, 183, 150))
    image.save(path)


def write_desktop_sized_block_display_probe_image(path: Path, *, scenario: str = "stone") -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (2560, 720), (36, 48, 120))
    left, top, right, bottom = 520, 250, 760, 490
    color = (78, 142, 58) if scenario in {"oak-leaves", "cutout", "tinted"} else (124, 124, 124)
    for x in range(left, right):
        for y in range(top, bottom):
            if scenario in {"oak-leaves", "cutout", "tinted"} and ((x - left) // 16 + (y - top) // 16) % 3 == 0:
                continue
            image.putpixel((x, y), color)
    image.save(path)


def write_falling_block_probe_image(path: Path, *, scenario: str = "sand") -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1280, 720), (36, 48, 120))
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


def write_desktop_sized_falling_block_probe_image(path: Path, *, scenario: str = "sand") -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (2560, 720), (36, 48, 120))
    if scenario == "gravel":
        color = (118, 118, 118)
    elif scenario in {"concrete-powder", "concrete_powder"}:
        color = (198, 198, 190)
    else:
        color = (196, 174, 104)
    for x in range(520, 700):
        for y in range(80, 260):
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


def artifact_for(temp: Path, mode: harness.ModeSpec, *, world: str = "Origin", parity_yaw: float = 105.0) -> dict[str, object]:
    target = fake_repo(temp, mode.target)
    capture = temp / f"capture-{mode.name}"
    write_capture(capture, backend=mode.backend, shaders=mode.shaders, world=world, parity_yaw=parity_yaw)
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
    def test_frozen_cow_observation_requires_actual_matching_emission_each_capture(self):
        doc = {"backend": "opengl", "captures": [
            {"renderedFrameIndex": frame, "frozenModelProducer": {
                "frameIndex": frame, "stage": "model-render-to-buffer-returned", "fixtureEntityId": 7,
                "entityType": "minecraft:cow", "textureId": "minecraft:textures/entity/cow/temperate_cow.png",
                "position": [146.695, 100.676, 529.481]}}
            for frame in (10, 20, 30)]}
        self.assertTrue(harness.frozen_cow_model_emission_evidence(doc)["passed"])
        self.assertFalse(harness.frozen_cow_model_emission_evidence(doc)["pixel_parity_proven"])
        for field, value in (("frameIndex", 1), ("fixtureEntityId", -1),
                             ("frameIndex", float("nan")), ("frameIndex", 20.5),
                             ("fixtureEntityId", float("inf")), ("fixtureEntityId", 8),
                             ("position", [0, float("nan"), 0]),
                             ("textureId", "unrelated"), ("stage", "fixture_spawned")):
            broken = json.loads(json.dumps(doc))
            broken["captures"][1]["frozenModelProducer"][field] = value
            self.assertFalse(harness.frozen_cow_model_emission_evidence(broken)["passed"])
        broken = json.loads(json.dumps(doc))
        del broken["captures"][1]["frozenModelProducer"]
        self.assertFalse(harness.frozen_cow_model_emission_evidence(broken)["passed"])
        doc["backend"] = "vulkan"
        self.assertFalse(harness.frozen_cow_model_emission_evidence(doc)["passed"])

    def test_frozen_cow_observation_only_satisfies_producer_gate(self):
        baseline = {"benchmark_fingerprint": {"workload_signature": {
            "parity_config": {"fixture": {"scenario": "cow"}}}},
            "metrics": {"rust_gal_slice": {"frozen_cow_model_emission_evidence": {
                "passed": True, "status": "emission_observed", "pixel_parity_proven": False}}}}
        failures = harness.parity_evidence_failures(baseline, {})
        self.assertFalse(any("model_producer_evidence_missing" in item for item in failures))
        self.assertIn("baseline_camera_evidence_missing", failures)
        self.assertIn("baseline_world_save_hash_missing", failures)
        baseline["metrics"]["rust_gal_slice"]["frozen_cow_model_emission_evidence"]["passed"] = False
        self.assertTrue(any("model_producer_evidence_missing" in item
                            for item in harness.parity_evidence_failures(baseline, {})))

    def test_vanilla_dh_isolation_disables_independent_fade_without_partial_writes(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "config_after_test" / "DistantHorizons.toml"
            path.parent.mkdir()
            original = 'enableRendering = true\nvanillaFadeMode = "DOUBLE_PASS"\nlodOnlyMode = true\nother = 42\n'
            path.write_text(original)
            harness.apply_canonical_vanilla_dh_isolation(path)
            self.assertEqual('enableRendering = false\nvanillaFadeMode = "NONE"\nlodOnlyMode = false\nother = 42\n', path.read_text())
            self.assertEqual({"status": "recorded", "enableRendering": "false", "vanillaFadeMode": "NONE", "lodOnlyMode": "false"},
                             harness.dh_composition_settings(Path(temp)))
            for invalid in (original.replace('lodOnlyMode = true\n', ''), original + 'vanillaFadeMode = "NONE"\n'):
                path.write_text(invalid)
                with self.assertRaises(ValueError):
                    harness.apply_canonical_vanilla_dh_isolation(path)
                self.assertEqual(invalid, path.read_text())
                self.assertEqual("invalid", harness.dh_composition_settings(Path(temp))["status"])

    def test_cross_repository_comparison_rejects_dh_fade_mismatch(self):
        settings = {"status": "recorded", "enableRendering": "false",
                    "vanillaFadeMode": "NONE", "lodOnlyMode": "false"}
        baseline = {"benchmark_fingerprint": {"workload_signature": {
            "dh_composition": settings}}}
        current = json.loads(json.dumps(baseline))
        self.assertTrue(harness.compare_workloads(baseline, current, cross_repository=True)["comparable"])
        for field, value in (("enableRendering", "true"),
                             ("vanillaFadeMode", "DOUBLE_PASS"), ("lodOnlyMode", "true")):
            with self.subTest(field=field):
                different = json.loads(json.dumps(current))
                different["benchmark_fingerprint"]["workload_signature"]["dh_composition"][field] = value
                result = harness.compare_workloads(baseline, different, cross_repository=True)
                self.assertFalse(result["comparable"])
                self.assertTrue(result["differences"])

    def test_canonical_graphics_mode_preserves_other_options_and_rejects_duplicates(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "options.txt"
            original = "graphicsMode:1\nfov:70\ngamma:0.5\nrenderDistance:4\n"
            path.write_text(original, encoding="utf-8")
            harness.apply_canonical_graphics_mode(path, "fabulous")
            self.assertEqual(path.read_text(encoding="utf-8"), original.replace("graphicsMode:1", "graphicsMode:2"))
            duplicate = original + "graphicsMode:0\n"
            path.write_text(duplicate, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "exactly one"):
                harness.apply_canonical_graphics_mode(path, "fancy")
            self.assertEqual(path.read_text(encoding="utf-8"), duplicate)

    def test_shadow_execution_requires_matching_producer_and_submission_for_every_capture(self):
        doc = {"captures": [{"renderedFrameIndex": 102}], "rustGalWorldEntityShadows": {
            "scenario": "cow",
            "semanticReceipts": [{"frameIndex": 101, "route": "rust-vulkan-whole-frame", "shadowSubmits": 1, "quads": 9}],
            "executionReceipts": [{"deterministicFrameIndex": 101, "route": "rust-vulkan-whole-frame", "gameplayFrameId": 50, "submissionId": 60, "quads": 9}],
        }}
        result = harness.deterministic_entity_shadow_execution_evidence(doc)
        self.assertTrue(result["passed"])
        self.assertFalse(result["pixel_parity_proven"])
        for field, value in (("quads", 8), ("submissionId", 0), ("gameplayFrameId", 0),
                             ("route", "java-legacy"), ("deterministicFrameIndex", 98)):
            with self.subTest(field=field):
                broken = json.loads(json.dumps(doc))
                broken["rustGalWorldEntityShadows"]["executionReceipts"][0][field] = value
                self.assertFalse(harness.deterministic_entity_shadow_execution_evidence(broken)["passed"])
        doc["captures"].append({"renderedFrameIndex": 110})
        self.assertFalse(harness.deterministic_entity_shadow_execution_evidence(doc)["passed"])
        self.assertFalse(harness.deterministic_entity_shadow_execution_evidence({})["passed"])

    def test_capture_with_benchmark_uses_the_retained_capture_camera_for_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture)
            write_frame_benchmark(capture, [16_000_000, 17_000_000])
            artifact = harness.normalize_capture_artifact(
                target,
                harness.MATRIX_MODES[4],
                capture,
                "correctness",
                True,
                ["fake"],
                0,
                False,
                tool_kind="capture",
                require_frame_benchmark=True,
            )
            camera = artifact["benchmark_fingerprint"]["workload_signature"]["camera"]
            self.assertEqual("complete", camera["status"])
            self.assertEqual("initial", camera["poses"][0]["pose"])

    def test_shadow_receiver_crops_require_correlated_finite_projected_support(self):
        receipt = {"frameIndex": 101, "route": "rust-vulkan-whole-frame", "shadowSubmits": 1, "quads": 9,
                   "receiverBoundsOrigin": "top-left", "receiverBounds": [10.2, 20.4, 30.6, 40.8]}
        doc = {"captures": [{"renderedFrameIndex": 102, "window": {"width": 1280, "height": 720}}],
               "rustGalWorldEntityShadows": {"scenario": "cow", "semanticReceipts": [receipt],
               "executionReceipts": [{"deterministicFrameIndex": 101, "route": "rust-vulkan-whole-frame",
                                      "gameplayFrameId": 50, "submissionId": 60, "quads": 9}]}}
        result = harness.deterministic_entity_shadow_receiver_crops(doc)
        self.assertTrue(result["passed"])
        self.assertEqual([10, 20, 31, 41], result["crops"][0]["crop_box"])
        self.assertFalse(result["pixel_parity_proven"])
        # Isolate the pixel comparator here; fixture mismatch rejection has
        # independent tests. Real paired screenshots are the acceptance evidence.
        from PIL import Image
        with tempfile.TemporaryDirectory() as temp, mock.patch.object(
            harness, "deterministic_visual_fixture_equivalence", return_value={"status": "passed"}
        ):
            left = Path(temp) / "reference.png"
            right = Path(temp) / "candidate.png"
            Image.new("RGB", (1280, 720), "white").save(left)
            Image.new("RGB", (1280, 720), "white").save(right)
            doc["captures"][0]["screenshot"] = str(right)
            baseline = json.loads(json.dumps(doc))
            baseline["captures"][0]["screenshot"] = str(left)
            self.assertTrue(harness.compare_entity_shadow_receiver_crops(baseline, doc)["passed"])
            Image.new("RGB", (1280, 720), "black").save(right)
            self.assertFalse(harness.compare_entity_shadow_receiver_crops(baseline, doc)["passed"])
        for bounds in (None, [1, 2], [30, 20, 10, 40], [-1, 0, 20, 40],
                       [0, 0, 1300, 40], [0, 0, float("nan"), 40]):
            broken = json.loads(json.dumps(doc))
            broken["rustGalWorldEntityShadows"]["semanticReceipts"][0]["receiverBounds"] = bounds
            self.assertFalse(harness.deterministic_entity_shadow_receiver_crops(broken)["passed"])
        receipt["receiverBoundsOrigin"] = "bottom-left"
        self.assertFalse(harness.deterministic_entity_shadow_receiver_crops(doc)["passed"])

    def test_visual_fixture_equivalence_requires_matching_fixed_pose_and_time(self) -> None:
        def manifest() -> dict[str, object]:
            return {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "initialPosition": {"x": 150.5, "y": 100.0, "z": 530.5},
                "captures": [{
                    "poseName": "initial",
                    "dimension": "minecraft:overworld",
                    "shaderEnabled": "false",
                    "gameTime": 6000,
                    "requestedYaw": 105.0,
                    "requestedPitch": 10.0,
                    "observedYaw": 105.0,
                    "observedPitch": 10.0,
                    "position": {"x": 150.5, "y": 100.0, "z": 530.5},
                    "window": {"width": 1280, "height": 720},
                }],
            }

        baseline = manifest()
        current = manifest()
        self.assertEqual(
            "passed",
            harness.deterministic_visual_fixture_equivalence(baseline, current)["status"],
        )
        current["captures"][0]["observedYaw"] = 104.0
        evidence = harness.deterministic_visual_fixture_equivalence(baseline, current)
        self.assertEqual("failed", evidence["status"])
        self.assertIn("observedYaw", evidence["mismatches"])
        current = manifest()
        current["shadowReceiverFixture"] = "red-glass-v1"
        self.assertIn("shadowReceiverFixture", harness.deterministic_visual_fixture_equivalence(baseline, current)["mismatches"])
        self.assertNotEqual(harness.deterministic_camera_signature(baseline), harness.deterministic_camera_signature(current))
        current = manifest()
        fingerprint = "0,1,1.5,0,0,0,0.5,1,1,1,1,1,1"
        baseline["captures"][0]["lightmapSemanticFingerprint"] = fingerprint
        current["captures"][0]["lightmapSemanticFingerprint"] = fingerprint
        self.assertEqual("passed", harness.deterministic_visual_fixture_equivalence(baseline, current)["status"])
        for invalid in (None, "nan," + fingerprint, fingerprint.replace("1.5", "nan"),
                        fingerprint.replace("1.5", "inf"), fingerprint.replace("1.5", "1.53")):
            current["captures"][0]["lightmapSemanticFingerprint"] = invalid
            self.assertEqual("failed", harness.deterministic_visual_fixture_equivalence(baseline, current)["status"])

    def test_capture_window_metrics_excludes_teardown_values(self) -> None:
        log = """
rust_gal_world_background_sky_type=4 rust_gal_world_background_color_argb=ff101820
rust_gal_world_background_sky_type=2 rust_gal_world_background_color_argb=ff330808
Deterministic camera capture complete metadata=/tmp/capture.json
rust_gal_world_background_sky_type=1 rust_gal_world_background_color_argb=ff78a7ff
"""
        window = harness.capture_window_metrics_log(log)
        self.assertEqual(2, harness.last_number(window, r"rust_gal_world_background_sky_type[=: ]+(\d+)"))
        self.assertEqual("ff330808", harness.last_text(window, r"rust_gal_world_background_color_argb[=: ]+([0-9a-fA-F]+)"))

    def test_repo_local_artifact_dir_is_quarantined_under_ignored_capture_root(self) -> None:
        repo = Path(__file__).resolve().parents[2]
        resolved = harness.normalize_configured_artifact_dir(repo, repo / "unsafe-captures")
        self.assertEqual(artifact_retention.default_artifact_base(repo) / "unsafe-captures", resolved)
        explicit_ignored = repo / "artifacts" / "graphics-captures" / "explicit"
        self.assertEqual(explicit_ignored, harness.normalize_configured_artifact_dir(repo, explicit_ignored))

        with tempfile.TemporaryDirectory() as temp:
            external = Path(temp) / "captures"
            self.assertEqual(external.resolve(), harness.normalize_configured_artifact_dir(repo, external))

    def test_static_terrain_appearance_color_receipts_are_exposed_as_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            capture = root / "capture"
            capture.mkdir()
            sidecar = capture / "static_terrain_parity_diagnostics.jsonl"
            sidecar.write_text(
                json.dumps({
                    "schema": "mattmc-static-terrain-appearance-rust-copy-v2",
                    "stage": "java-to-rust-copy",
                    "targetBlockColorSamples": [{"colorR": 12, "colorG": 200, "colorB": 34}],
                }) + "\n",
                encoding="utf-8",
            )
            receipts = harness.read_static_terrain_appearance_color_receipts(
                root / "graphics_audit_artifact.json"
            )
            self.assertEqual(1, len(receipts))
            self.assertEqual("java-to-rust-copy", receipts[0]["stage"])
            self.assertEqual(200, receipts[0]["targetBlockColorSamples"][0]["colorG"])

    def test_world_text_scenario_requires_real_name_tag_semantic_and_execution_receipts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            log_path = capture / "runClient_20260101_000000.log"
            log_path.write_text(
                log_path.read_text(encoding="utf-8")
                + "-Dmattmc.dev.rustGalWorldText.scenario=block-display\n",
                encoding="utf-8",
            )
            missing = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(missing["validation"]["complete"])
            self.assertTrue(
                any("real vanilla text producer" in message for message in missing["validation"]["messages"]),
                missing["validation"]["messages"],
            )
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalWorldText"] = {
                "scenario": "block-display", "semanticFrame": 33, "visibleEntityStates": 2,
                "nameTagCallbacks": 1, "textCallbacks": 0, "normalSubmits": 1,
                "seeThroughSubmits": 1, "emittedQuads": 27, "emittedImages": 1,
                "fullySupported": True, "consumedQuads": 27,
            }
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any("real vanilla text producer" in message for message in admitted["validation"]["messages"]),
                admitted["validation"]["messages"],
            )
            metrics = admitted["metrics"]["rust_gal_slice"]
            self.assertEqual("block-display", metrics["world_text_scenario"])
            self.assertEqual(27, metrics["world_text_metrics"]["consumedQuads"])

            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            source_receipt = contract_dir / "selected-source-execution-frame-44.json"
            source_receipt.write_text(
                json.dumps({"frame_id": 44, "submission_id": 55, "world_generation": 2, "route": "rust-native-selected-source"}),
                encoding="utf-8",
            )
            missing_source_writer = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any("selected-source world-text frame did not execute" in message
                    for message in missing_source_writer["validation"]["messages"]),
                missing_source_writer["validation"]["messages"],
            )
            source_receipt.write_text(
                json.dumps({
                    "frame_id": 44, "submission_id": 55, "world_generation": 2, "route": "rust-native-selected-source",
                    "world_text_execution": {"quads": 27, "draws": 2, "clip_xy_visible_quads": 27},
                }),
                encoding="utf-8",
            )
            source_writer_admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any("selected-source world-text frame did not execute" in message
                    for message in source_writer_admitted["validation"]["messages"]),
                source_writer_admitted["validation"]["messages"],
            )

    def test_name_tag_support_entity_does_not_open_model_migration_gate(self) -> None:
        """The cow used to host a name tag is support state, not a model workload."""
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            log_path = capture / "runClient_20260101_000000.log"
            log_path.write_text(
                log_path.read_text(encoding="utf-8")
                + "-Dmattmc.dev.rustGalWorldText.scenario=name-tag\n",
                encoding="utf-8",
            )
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalWorldModelMeshScenario"] = "cow"
            document["rustGalWorldText"] = {
                "scenario": "name-tag", "semanticFrame": 33, "visibleEntityStates": 2,
                "nameTagCallbacks": 1, "textCallbacks": 0, "normalSubmits": 1,
                "seeThroughSubmits": 1, "emittedQuads": 27, "emittedImages": 1,
                "fullySupported": True, "consumedQuads": 27,
            }
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            artifact = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any("deterministic model capture" in message for message in artifact["validation"]["messages"]),
                artifact["validation"]["messages"],
            )

    def test_text_display_scenario_requires_real_ordinary_text_callback(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            log_path = capture / "runClient_20260101_000000.log"
            log_path.write_text(
                log_path.read_text(encoding="utf-8")
                + "-Dmattmc.dev.rustGalWorldText.scenario=text-display\n",
                encoding="utf-8",
            )
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalWorldText"] = {
                "scenario": "text-display", "semanticFrame": 33, "visibleEntityStates": 2,
                "nameTagCallbacks": 0, "textCallbacks": 0, "normalSubmits": 0,
                "seeThroughSubmits": 1, "emittedQuads": 27, "emittedImages": 1,
                "fullySupported": True, "consumedQuads": 27,
            }
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            missing = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any("ordinary text callbacks" in message for message in missing["validation"]["messages"]),
                missing["validation"]["messages"],
            )
            document["rustGalWorldText"]["textCallbacks"] = 1
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any("ordinary text callbacks" in message for message in admitted["validation"]["messages"]),
                admitted["validation"]["messages"],
            )
            self.assertFalse(
                any("normal submits" in message for message in admitted["validation"]["messages"]),
                admitted["validation"]["messages"],
            )

    def test_capture_runner_bounds_diagnostic_command_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            with mock.patch.object(
                capture_runner.subprocess,
                "run",
                side_effect=subprocess.TimeoutExpired(["/bin/echo", "diagnostic"], 8),
            ):
                output = capture_runner.command_text(
                    ["/bin/echo", "diagnostic"],
                    cwd=Path(temp),
                    timeout_secs=8,
                )
        self.assertIn("command timed out after 8s", output)
        self.assertIn("/bin/echo diagnostic", output)

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

    def test_selected_source_block_marker_requires_local_material_semantics(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "block_marker_source.png"
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
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            receipt_path = contract_dir / "selected-source-execution-frame-42.json"
            receipt = {
                "frame_id": 42,
                "submission_id": 9,
                "world_generation": 2,
                "route": "rust-native-selected-source",
                "source_material_coverage": {"batches": 1, "quads": 1, "draws": 1, "vertices": 6},
                "source_material_semantics": {
                    "atlas_quads": 0,
                    "local_quads": 0,
                    "unsupported_quads": 0,
                    "records": [],
                },
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")

            missing = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(missing["validation"]["complete"])
            self.assertTrue(
                any(
                    "did not retain a local Rust-owned material texture semantic receipt" in message
                    for message in missing["validation"]["messages"]
                ),
                missing["validation"]["messages"],
            )

            receipt["source_material_semantics"]["local_quads"] = 1
            receipt["source_material_semantics"]["records"] = [
                {"material_id": material_id, "texture_id": texture_id, "uv_space": 0, "quads": 1}
            ]
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "did not retain a local Rust-owned material texture semantic receipt" in message
                    for message in admitted["validation"]["messages"]
                ),
                admitted["validation"]["messages"],
            )

    def test_selected_source_cloud_requires_matching_writer_execution_receipt(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            deterministic_doc = json.loads(deterministic.read_text(encoding="utf-8"))
            deterministic_doc["rustGalWorldClouds"] = {
                "scenario": "fast",
                "traversalReceipts": [{"route": "rust_vulkan_whole_frame", "cells": 4, "radius": 2}],
                "semanticReceipts": [{"quads": 4, "sourceProgram": 3}],
                "executionReceipts": [
                    {"route": "rust_vulkan_whole_frame", "quads": 4, "sourceProgram": 3}
                ],
            }
            deterministic.write_text(json.dumps(deterministic_doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalClouds.scenario=fast\n", encoding="utf-8"
            )
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            receipt_path = contract_dir / "selected-source-execution-frame-42.json"
            receipt = {
                "frame_id": 42,
                "submission_id": 9,
                "world_generation": 2,
                "route": "rust-native-selected-source",
                "source_material_semantics": {"cloud_quads": 4},
                "source_material_execution": {
                    "clouds": {"batches": 1, "quads": 0, "draws": 0, "vertices": 0}
                },
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")

            missing = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any(
                    "selected-source cloud execution did not retain matching Rust-owned gbuffers_clouds"
                    in message
                    for message in missing["validation"]["messages"]
                ),
                missing["validation"]["messages"],
            )

            receipt["source_material_execution"]["clouds"] = {
                "batches": 1,
                "quads": 4,
                "draws": 1,
                "vertices": 24,
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "selected-source cloud execution did not retain matching Rust-owned gbuffers_clouds"
                    in message
                    for message in admitted["validation"]["messages"]
                ),
                admitted["validation"]["messages"],
            )

            receipt["source_material_execution"]["clouds"] = {
                "batches": 0,
                "quads": 0,
                "draws": 0,
                "vertices": 0,
                "suppressed_quads": 4,
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            ambiguous = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any(
                    "selected-source cloud execution did not retain matching Rust-owned gbuffers_clouds"
                    in message
                    for message in ambiguous["validation"]["messages"]
                ),
                ambiguous["validation"]["messages"],
            )

            receipt["source_material_execution"]["clouds"] = {
                "batches": 0,
                "quads": 0,
                "draws": 0,
                "vertices": 0,
                "suppressed_quads": 4,
                "face_disposition": "suppressed",
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            suppressed = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any(
                    "selected-source cloud execution did not retain matching Rust-owned gbuffers_clouds"
                    in message
                    for message in suppressed["validation"]["messages"]
                ),
                suppressed["validation"]["messages"],
            )

            receipt["source_material_execution"]["clouds"]["fullscreen_cloud_stages"] = 1
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            suppressed = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "selected-source cloud execution did not retain matching Rust-owned gbuffers_clouds"
                    in message
                    for message in suppressed["validation"]["messages"]
                ),
                suppressed["validation"]["messages"],
            )
            self.assertTrue(
                any(
                    "explicitly suppresses vanilla cloud faces" in message
                    for message in suppressed["validation"]["messages"]
                ),
                suppressed["validation"]["messages"],
            )

            receipt_path.unlink()
            distant_receipt_path = contract_dir / "selected-source-execution-distant-horizons-frame-42.json"
            distant_receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            distant = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "selected-source cloud execution did not retain matching Rust-owned gbuffers_clouds"
                    in message
                    for message in distant["validation"]["messages"]
                ),
                distant["validation"]["messages"],
            )

    def test_frozen_cloud_baseline_does_not_require_rust_receipts(self) -> None:
        """Frozen OpenGL remains a control even when the Rust cloud fixture is requested."""
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalWorldClouds"] = {"scenario": "bounded"}
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalClouds.scenario=bounded\n", encoding="utf-8"
            )
            artifact = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertNotIn(
                "deterministic Rust cloud scenario requires the Rust Vulkan whole-frame route",
                artifact["validation"]["messages"],
            )

    def test_frozen_cloud_baseline_retains_its_cpu_mesh_receipt(self) -> None:
        """Frozen remains observational while exposing the exact compared mesh inputs."""
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalWorldClouds"] = {"scenario": "bounded"}
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            receipt_dir = capture / "deterministic_camera_capture_20260101_000000"
            receipt_dir.mkdir()
            (receipt_dir / "frozen-cloud-mesh-last.json").write_text(
                json.dumps({
                    "quad_count": 469,
                    "relative_camera_pos": "INSIDE_CLOUDS",
                    "cell_x": 12,
                    "cell_z": 44,
                    "cell_offset_x": 6.5,
                    "cell_offset_z": 6.46,
                }),
                encoding="utf-8",
            )
            artifact = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            receipt = artifact["metrics"]["rust_gal_slice"]["world_cloud_metrics"]["frozenMeshReceipt"]
            self.assertEqual(469, receipt["quad_count"])
            self.assertEqual("INSIDE_CLOUDS", receipt["relative_camera_pos"])

    def test_selected_source_weather_requires_matching_writer_execution_receipt(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            deterministic_doc = json.loads(deterministic.read_text(encoding="utf-8"))
            deterministic_doc["rustGalWorldWeather"] = {
                "scenario": "rain",
                "setupComplete": True,
                "setupStage": "setup-complete",
                "traversalReceipts": [
                    {"route": "rust_vulkan_whole_frame", "rainColumns": 4, "intensity": 1.0}
                ],
                "semanticReceipts": [{"rainColumns": 4, "quads": 4, "intensity": 1.0}],
                "executionReceipts": [{"route": "rust_vulkan_whole_frame", "quads": 4}],
            }
            deterministic.write_text(json.dumps(deterministic_doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWeather.scenario=rain\n", encoding="utf-8"
            )
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            receipt_path = contract_dir / "selected-source-execution-frame-42.json"
            receipt = {
                "frame_id": 42,
                "submission_id": 9,
                "world_generation": 2,
                "route": "rust-native-selected-source",
                "source_material_semantics": {"weather_quads": 4},
                "source_material_execution": {
                    "weather": {"batches": 1, "quads": 0, "draws": 0, "vertices": 0}
                },
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")

            missing = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any(
                    "selected-source weather execution did not retain matching Rust-owned gbuffers_weather"
                    in message
                    for message in missing["validation"]["messages"]
                ),
                missing["validation"]["messages"],
            )

            receipt["source_material_execution"]["weather"] = {
                "batches": 1,
                "quads": 4,
                "draws": 1,
                "vertices": 24,
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "selected-source weather execution did not retain matching Rust-owned gbuffers_weather"
                    in message
                    for message in admitted["validation"]["messages"]
                ),
                admitted["validation"]["messages"],
            )

    def test_selected_source_terrain_particles_require_atlas_semantics_and_textured_execution(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "terrain_particle_source.png"
            texture_id = harness.WORLD_MATERIAL_TEXTURE_STONE
            write_terrain_particle_probe_image(screenshot, texture_id=texture_id)
            deterministic_path = capture / "deterministic_camera_capture_20260101_000000.json"
            deterministic = json.loads(deterministic_path.read_text(encoding="utf-8"))
            deterministic["captures"][0]["screenshot"] = str(screenshot)
            deterministic["rustGalWorldTerrainParticleScenario"] = "stone"
            deterministic["rustGalWorldTerrainParticles"] = [
                {
                    "route": "rust-vulkan-whole-frame",
                    "textureId": texture_id,
                    "center": {"x": 1.5, "y": 80.5, "z": 2.5},
                    "quadSize": 0.5,
                    "colorArgb": 0xFFFFFFFF,
                    "materialMode": 1,
                    "viewport": {"width": 1280, "height": 720},
                    "projected": True,
                    "screenBounds": {"left": 560.0, "top": 300.0, "right": 640.0, "bottom": 380.0},
                }
            ]
            deterministic_path.write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMaterial.terrainParticleScenario=stone\n"
                "material_terrain_particle_quads=1 material_terrain_particle_texture_mask=2\n"
                "rust_gal_world_material_quads_executed=1\n"
                "rust_gal_world_material_batches_executed=1\n"
                "rust_gal_world_material_draws_executed=1\n",
                encoding="utf-8",
            )
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            receipt_path = contract_dir / "selected-source-execution-frame-42.json"
            receipt = {
                "frame_id": 42,
                "submission_id": 9,
                "world_generation": 2,
                "route": "rust-native-selected-source",
                "source_material_semantics": {"atlas_quads": 1, "records": []},
                "source_material_execution": {
                    "textured": {"batches": 1, "quads": 0, "draws": 0, "vertices": 0}
                },
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")

            missing = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any(
                    "selected-source TerrainParticle execution did not retain matching atlas-semantic"
                    in message
                    for message in missing["validation"]["messages"]
                ),
                missing["validation"]["messages"],
            )

            receipt["source_material_semantics"]["records"] = [
                {"texture_id": texture_id, "uv_space": 1, "quads": 1}
            ]
            receipt["source_material_execution"]["textured"] = {
                "batches": 1,
                "quads": 1,
                "draws": 1,
                "vertices": 6,
            }
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "selected-source TerrainParticle execution did not retain matching atlas-semantic"
                    in message
                    for message in admitted["validation"]["messages"]
                ),
                admitted["validation"]["messages"],
            )

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

    def test_migration_matrix_excludes_current_java_vulkan(self) -> None:
        names = {mode.name for mode in harness.MATRIX_MODES}
        self.assertNotIn("current-java-vulkan-shaders-off", names)
        self.assertNotIn("current-java-vulkan-shaders-on", names)
        with self.assertRaises(SystemExit):
            harness.parse_args(["gameplay", "--mode", "current-java-vulkan-shaders-off", "--dry-run"])

    def test_terrain_particle_capture_waits_for_real_terrain_before_pairing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-material-terrain-particle-scenario",
                    "stone",
                ]
            )
            _command, env = harness.build_capture_command(
                target, mode, root / "capture", "correctness", args, "capture"
            )
            # Terrain particles are a real-world visual pair, not a particle
            # crop-only synthetic scene. Their async near-world sections need a
            # bounded pose warm-up, but global queue drainage is not a valid
            # requirement because unrelated sections may continue arriving.
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFrames=0",
                env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=180", env["JAVA_TOOL_OPTIONS"])

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
            screenshots = []
            for frame in range(5):
                screenshot = capture / f"falling_block_sand_{frame}.png"
                write_falling_block_probe_image(screenshot, scenario="sand")
                screenshots.append(screenshot)
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldFallingBlockScenario": "sand",
                "rustGalWorldFallingBlockSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:sand",
                    "spawnMethod": "FallingBlockEntity.fall",
                    "entityCount": 1,
                    "fallHeight": 64,
                },
                "rustGalWorldFallingBlockExtractionProbe": {
                    "seen": 4,
                    "shouldRender": 4,
                    "compiledSection": 4,
                    "extracted": 4,
                },
                "captures": [
                    {
                        "poseName": f"falling-{frame:02d}",
                        "renderedFrameIndex": 8 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "internal-main-render-target",
                    }
                    for frame in range(5)
                ],
                "rustGalWorldFallingBlocks": [
                    {
                        "frameIndex": 8 + frame,
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
                        "screenBounds": {"left": 500.0, "top": 60.0, "right": 720.0, "bottom": 280.0},
                    }
                    for frame in range(5)
                ],
                "rustGalWorldFallingBlockRouteDecisions": [
                    {
                        "frameIndex": 8 + frame,
                        "route": "rust-opengl",
                        "blockId": "minecraft:sand",
                        "rustSelected": True,
                        "rustQueued": True,
                        "javaDrawn": False,
                    }
                    for frame in range(4)
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
            self.assertEqual("passed", evidence["game_window_status"])
            self.assertEqual("passed", evidence["producer_traversal_status"])
            self.assertEqual("passed", evidence["setup_status"])
            self.assertEqual("passed", evidence["capture_method_status"])
            self.assertEqual("passed", evidence["frame_sequence_status"])
            self.assertEqual(5, evidence["frames_validated"])

    def test_falling_block_capture_accepts_correlated_rust_vulkan_final_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            screenshots = []
            for frame in range(4):
                screenshot = temp / f"falling_block_final_output_{frame}.png"
                write_falling_block_probe_image(screenshot, scenario="sand")
                screenshots.append(screenshot)
            document = {
                "window": {"width": 1280, "height": 720},
                "rustGalWorldFallingBlockSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:sand",
                    "spawnMethod": "FallingBlockEntity.fall",
                    "entityCount": 1,
                },
                "rustGalWorldFallingBlockExtractionProbe": {
                    "seen": 4,
                    "shouldRender": 4,
                    "extracted": 4,
                },
                "captures": [
                    {
                        "poseName": f"falling-{frame:02d}",
                        "renderedFrameIndex": 8 + frame,
                        "screenshot": str(screenshots[frame]),
                        "backend": "vulkan",
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "rust-vulkan-final-output",
                        "targetWindow": "rust-vulkan-final-output",
                    }
                    for frame in range(4)
                ],
                "rustGalWorldFallingBlocks": [
                    {
                        "frameIndex": 8 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "blockId": "minecraft:sand",
                        "meshKey": 42,
                        "meshGeneration": 2,
                        "indexType": 1,
                        "vertexCount": 24,
                        "indexBytes": 72,
                        "sectionCount": 6,
                        "textureIds": "2269692870",
                        "materialMode": 1,
                        "viewport": {"width": 1280, "height": 720},
                        "projected": True,
                        "screenBounds": {"left": 500.0, "top": 60.0, "right": 720.0, "bottom": 280.0},
                    }
                    for frame in range(4)
                ],
            }

            evidence = harness.deterministic_world_mesh_falling_block_pixel_evidence(document, "sand")

            self.assertEqual("present", evidence["status"])
            self.assertEqual("passed", evidence["capture_method_status"])
            self.assertEqual("passed", evidence["game_window_status"])

    def test_primed_tnt_capture_requires_matching_provenance_and_route(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            screenshots = []
            for frame in range(4):
                screenshot = temp / f"primed_tnt_{frame}.png"
                write_primed_tnt_probe_image(screenshot)
                screenshots.append(screenshot)
            document = {
                "status": "complete",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldPrimedTntScenario": "ordinary",
                "rustGalWorldPrimedTntSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:tnt",
                    "entityCount": 1,
                },
                "captures": [
                    {
                        "poseName": f"primed-tnt-{frame:02d}",
                        "renderedFrameIndex": 20 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "internal-main-render-target",
                    }
                    for frame in range(4)
                ],
                "rustGalWorldMovingBlockRouteDecisions": [
                    {
                        "frameIndex": 20 + frame,
                        "provenance": "primed-tnt",
                        "route": "rust-opengl",
                        "blockId": "minecraft:tnt",
                        "rustSelected": True,
                        "rustQueued": True,
                        "javaDrawn": False,
                    }
                    for frame in range(4)
                ],
                "rustGalWorldMovingBlocks": [
                    {
                        "frameIndex": 20 + frame,
                        "route": "rust-opengl",
                        "provenance": "primed-tnt",
                        "blockId": "minecraft:tnt",
                        "meshKey": 99,
                        "meshGeneration": 3,
                        "indexType": 1,
                        "vertexCount": 24,
                        "indexBytes": 72,
                        "sectionCount": 6,
                        "textureIds": "123",
                        "materialMode": 1,
                        "projected": True,
                        "screenBounds": {"left": 500.0, "top": 60.0, "right": 720.0, "bottom": 280.0},
                    }
                    for frame in range(4)
                ],
            }
            evidence = harness.deterministic_world_mesh_primed_tnt_pixel_evidence(document, "ordinary")
            self.assertEqual("present", evidence["status"])
            self.assertEqual("passed", evidence["route_status"])
            self.assertEqual(4, evidence["frames_validated"])

            document["rustGalWorldMovingBlockRouteDecisions"] = []
            missing_route = harness.deterministic_world_mesh_primed_tnt_pixel_evidence(document, "ordinary")
            self.assertEqual("missing_rust_route", missing_route["status"])

    def test_experience_orb_capture_requires_real_route_receipt_and_projected_frames(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            screenshots = []
            for frame in range(5):
                screenshot = temp / f"experience_orb_{frame}.png"
                write_experience_orb_probe_image(screenshot, visible=True)
                screenshots.append(screenshot)
            document = {
                "status": "complete",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldExperienceOrbSetup": {
                    "status": "spawned",
                    "entityCount": 3,
                },
                "captures": [
                    {
                        "poseName": f"experience-orb-{frame:02d}",
                        "renderedFrameIndex": 20 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "rust-vulkan-final-output",
                        "targetWindow": "rust-vulkan-final-output",
                    }
                    for frame in range(5)
                ],
                "rustGalWorldExperienceOrbRouteDecisions": [
                    {
                        "frameIndex": 20 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "rustSelected": True,
                        "rustQueued": True,
                        "javaDrawn": False,
                    }
                    for frame in range(5)
                ],
                "rustGalWorldExperienceOrbs": [
                    {
                        "frameIndex": 20 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "projected": True,
                        "colorArgb": 0x80FFFFFF,
                        "packedLight": 0x00F000F0,
                        "screenBounds": {"left": 520.0, "top": 250.0, "right": 760.0, "bottom": 490.0},
                    }
                    for frame in range(5)
                ],
                "rustGalWorldExperienceOrbExecution": [
                    {
                        "deterministicFrameIndex": 20 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "gameplayFrameId": 120 + frame,
                        "submissionId": 220 + frame,
                        "quads": 3,
                    }
                    for frame in range(5)
                ],
            }
            evidence = harness.deterministic_world_experience_orb_capture_evidence(document, "ordinary")
            self.assertEqual("visible_final_frames", evidence["status"])
            self.assertEqual(5, evidence["matched_frames"])

            for screenshot in screenshots:
                write_experience_orb_probe_image(screenshot, visible=False)
            missing_pixels = harness.deterministic_world_experience_orb_capture_evidence(document, "ordinary")
            self.assertEqual("missing_visible_orb_footprint", missing_pixels["status"])
            for screenshot in screenshots:
                write_experience_orb_probe_image(screenshot, visible=True)

            document["rustGalWorldExperienceOrbExecution"] = []
            missing_execution = harness.deterministic_world_experience_orb_capture_evidence(document, "ordinary")
            self.assertEqual("missing_execution_receipt", missing_execution["status"])

    def test_falling_block_capture_rejects_desktop_screenshot(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshots = []
            for frame in range(4):
                screenshot = capture / f"desktop_falling_block_sand_{frame}.png"
                write_desktop_sized_falling_block_probe_image(screenshot, scenario="sand")
                screenshots.append(screenshot)
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldFallingBlockScenario": "sand",
                "rustGalWorldFallingBlockSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:sand",
                    "spawnMethod": "FallingBlockEntity.fall",
                    "entityCount": 1,
                    "fallHeight": 64,
                },
                "rustGalWorldFallingBlockExtractionProbe": {
                    "seen": 4,
                    "shouldRender": 4,
                    "compiledSection": 4,
                    "extracted": 4,
                },
                "captures": [
                    {
                        "poseName": f"falling-{frame:02d}",
                        "renderedFrameIndex": 8 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "internal-main-render-target",
                    }
                    for frame in range(4)
                ],
                "rustGalWorldFallingBlocks": [
                    {
                        "frameIndex": 8 + frame,
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
                    for frame in range(4)
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
            evidence = artifact["metrics"]["rust_gal_slice"]["world_mesh_falling_block_pixel_evidence"]
            self.assertEqual("not_game_window", evidence["status"])
            self.assertEqual("failed", evidence["game_window_status"])
            self.assertEqual(
                {
                    "captured_width": 2560,
                    "captured_height": 720,
                    "expected_width": 1280,
                    "expected_height": 720,
                    "status": "failed",
                    "target_window": "",
                    "target_window_status": "not_recorded",
                },
                evidence["game_window"],
            )

    def test_arrow_capture_requires_frame_correlated_route_and_execution(self) -> None:
        from PIL import Image

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            screenshots: list[Path] = []
            for frame in range(4):
                screenshot = root / f"arrow_{frame}.png"
                image = Image.new("RGB", (1280, 720), (31, 52, 77))
                for offset in range(96):
                    image.putpixel((560 + offset, 400 + offset // 4), (228, 220, 196))
                image.save(screenshot)
                screenshots.append(screenshot)

            document = {
                "status": "complete",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldArrowScenario": "ordinary",
                "rustGalWorldArrowSetup": {
                    "status": "spawned",
                    "texture": "minecraft:textures/entity/projectiles/arrow.png",
                    "entityCount": 1,
                },
                "captures": [
                    {
                        "poseName": f"arrow-{frame:02d}",
                        "renderedFrameIndex": 40 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "external-window-request",
                        "targetWindow": "Minecraft",
                    }
                    for frame in range(4)
                ],
                "rustGalWorldArrows": [
                    {
                        "frameIndex": 40 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "textureId": "minecraft:textures/entity/projectiles/arrow.png",
                        "meshKey": 1001,
                        "meshGeneration": 3,
                        "vertexCount": 32,
                        "indexBytes": 72,
                        "sectionCount": 2,
                        "packedLight": 15728880,
                        "projected": True,
                        "screenBounds": {"left": 555.0, "top": 270.0, "right": 680.0, "bottom": 330.0},
                    }
                    for frame in range(4)
                ],
                "rustGalWorldArrowRouteDecisions": [
                    {
                        "frameIndex": 40 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "textureId": "minecraft:textures/entity/projectiles/arrow.png",
                        "rustSelected": True,
                        "rustQueued": True,
                        "javaDrawn": False,
                    }
                    for frame in range(4)
                ],
                "rustGalWorldMovingMeshExecution": [
                    {
                        "deterministicFrameIndex": 40 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "provenance": "arrow",
                        "gameplayFrameId": 900 + frame,
                        "submissionId": 700 + frame,
                        "instances": 1,
                    }
                    for frame in range(4)
                ],
            }

            evidence = harness.deterministic_world_mesh_arrow_capture_evidence(document, "ordinary")
            self.assertEqual("structural_present", evidence["status"])
            self.assertEqual(4, evidence["matched_frames"])
            self.assertTrue(all(crop["submission_id"] > 0 for crop in evidence["crops"]))
            with Image.open(evidence["crops"][0]["crop_path"]) as crop:
                # The semantic bounds are bottom-left coordinates. A top-left
                # crop would miss this deliberately asymmetric Arrow stripe.
                self.assertIn((228, 220, 196), crop.convert("RGB").getdata())

            document["captures"][0].update(
                {
                    "captureMethod": "rust-vulkan-final-output",
                    "targetWindow": "rust-vulkan-final-output",
                }
            )
            backend_final = harness.deterministic_world_mesh_arrow_capture_evidence(document, "ordinary")
            self.assertEqual("structural_present", backend_final["status"])
            self.assertEqual("rust-vulkan-final-output", backend_final["crops"][0]["capture_method"])

            document["rustGalWorldMovingMeshExecution"] = document["rustGalWorldMovingMeshExecution"][:1]
            missing = harness.deterministic_world_mesh_arrow_capture_evidence(document, "ordinary")
            self.assertEqual("missing_frame_correlated_execution", missing["status"])

    def test_arrow_capture_hidden_state_requires_zero_mesh_metadata(self) -> None:
        evidence = harness.deterministic_world_mesh_arrow_capture_evidence(
            {"rustGalWorldArrows": [], "rustGalWorldArrowRouteDecisions": []},
            "hidden",
        )
        self.assertEqual("absent_expected", evidence["status"])

    def test_model_capture_requires_real_chest_route_and_execution(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            screenshots = [temp / f"model-{frame}.png" for frame in range(4)]
            for screenshot in screenshots:
                write_block_display_probe_image(screenshot, scenario="stone")
            document = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldModelMeshScenario": "chest",
                "rustGalWorldModelMeshSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:chest",
                    "clientBlockEntityPresent": True,
                },
                "captures": [
                    {
                        "renderedFrameIndex": 80 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "external-window-request",
                        "targetWindow": "Minecraft",
                    }
                    for frame in range(4)
                ],
                "rustGalWorldModelMeshes": [
                    {
                        "frameIndex": 80 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "textureId": "minecraft:entity/chest/normal",
                        "meshKey": 2001,
                        "meshGeneration": 4,
                        "sectionCount": 3,
                        "projected": True,
                        "screenBounds": {"left": 555.0, "top": 270.0, "right": 680.0, "bottom": 330.0},
                    }
                    for frame in range(4)
                ],
                "rustGalWorldModelMeshRouteDecisions": [
                    {
                        "frameIndex": 80 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "textureId": "minecraft:entity/chest/normal",
                        "rustSelected": True,
                        "rustQueued": True,
                        "javaDrawn": False,
                    }
                    for frame in range(4)
                ],
                "rustGalWorldMovingMeshExecution": [
                    {
                        "deterministicFrameIndex": 80 + frame,
                        "route": "rust-vulkan-whole-frame",
                        "provenance": "model",
                        "gameplayFrameId": 1000 + frame,
                        "submissionId": 800 + frame,
                        "instances": 1,
                    }
                    for frame in range(4)
                ],
            }
            evidence = harness.deterministic_world_mesh_model_capture_evidence(document, "chest")
            self.assertEqual("structural_present", evidence["status"])
            self.assertEqual(4, evidence["matched_frames"])
            # A final capture can race the producer's last diagnostic record.
            # It is valid only as trailing lag after the complete required
            # correlated sequence; a gap in the sequence must still fail.
            trailing = json.loads(json.dumps(document))
            trailing_screenshot = temp / "model-trailing.png"
            write_block_display_probe_image(trailing_screenshot, scenario="stone")
            trailing["captures"].append(
                {
                    "renderedFrameIndex": 90,
                    "screenshot": str(trailing_screenshot),
                    "window": {"width": 1280, "height": 720},
                    "captureMethod": "external-window-request",
                    "targetWindow": "Minecraft",
                }
            )
            trailing_evidence = harness.deterministic_world_mesh_model_capture_evidence(trailing, "chest")
            self.assertEqual("structural_present", trailing_evidence["status"])
            self.assertEqual(4, trailing_evidence["matched_frames"])
            self.assertEqual("passed_bounded_visible_frames", trailing_evidence["frame_sequence_status"])
            conduit_document = dict(document)
            conduit_document["rustGalWorldModelMeshScenario"] = "conduit"
            conduit_document["rustGalWorldModelMeshSetup"] = {
                "status": "spawned",
                "blockId": "minecraft:conduit",
                "clientBlockEntityPresent": True,
            }
            conduit_document["rustGalWorldModelMeshes"] = [
                {**mesh, "textureId": "minecraft:textures/atlas/blocks.png"}
                for mesh in document["rustGalWorldModelMeshes"]
            ]
            conduit_document["rustGalWorldModelMeshRouteDecisions"] = [
                {**decision, "textureId": "minecraft:entity/conduit/base"}
                for decision in document["rustGalWorldModelMeshRouteDecisions"]
            ]
            conduit_document["rustGalWorldMovingMeshExecution"] = [
                {**receipt, "provenance": "model-part"}
                for receipt in document["rustGalWorldMovingMeshExecution"]
            ]
            conduit = harness.deterministic_world_mesh_model_capture_evidence(conduit_document, "conduit")
            self.assertEqual("structural_present", conduit["status"])
            self.assertEqual("passed", conduit["setup_status"])
            self.assertEqual("passed", conduit["execution_status"])
            crystal_document = dict(document)
            crystal_document["rustGalWorldModelMeshScenario"] = "end-crystal"
            crystal_document["rustGalWorldModelMeshSetup"] = {
                "status": "spawned",
                "blockId": "minecraft:end_crystal",
                "serverEntityPresent": True,
                "clientEntityPresent": True,
                "serverEntityId": 91,
                "clientEntityId": 91,
            }
            crystal_document["rustGalWorldModelMeshes"] = [
                {**mesh, "textureId": "minecraft:textures/entity/end_crystal/end_crystal.png",
                 "semanticModelIdentity": "minecraft:end_crystal", "entityId": 91}
                for mesh in document["rustGalWorldModelMeshes"]
            ]
            crystal_document["rustGalWorldModelMeshRouteDecisions"] = [
                {**decision, "textureId": "minecraft:textures/entity/end_crystal/end_crystal.png", "entityId": 91}
                for decision in document["rustGalWorldModelMeshRouteDecisions"]
            ]
            crystal = harness.deterministic_world_mesh_model_capture_evidence(crystal_document, "end-crystal")
            self.assertEqual("structural_present", crystal["status"])
            self.assertEqual("passed", crystal["setup_status"])
            cow_document = dict(document)
            cow_document["rustGalWorldModelMeshScenario"] = "cow"
            cow_document["rustGalWorldModelMeshSetup"] = {
                "status": "spawned",
                "blockId": "minecraft:cow",
                "serverEntityPresent": True,
                "clientEntityPresent": True,
                "serverEntityId": 41,
                "clientEntityId": 41,
            }
            cow_document["rustGalWorldModelMeshes"] = [
                {
                    **mesh,
                    "textureId": "minecraft:textures/entity/cow/temperate_cow.png",
                    "semanticModelIdentity": "minecraft:cow",
                    "entityId": 41,
                }
                for mesh in document["rustGalWorldModelMeshes"]
            ]
            cow_document["rustGalWorldModelMeshRouteDecisions"] = [
                {**decision, "textureId": "minecraft:textures/entity/cow/temperate_cow.png", "entityId": 41}
                for decision in document["rustGalWorldModelMeshRouteDecisions"]
            ]
            cow = harness.deterministic_world_mesh_model_capture_evidence(cow_document, "cow")
            self.assertEqual("structural_present", cow["status"])
            self.assertEqual("passed", cow["setup_status"])
            chicken_document = dict(cow_document)
            chicken_document["rustGalWorldModelMeshScenario"] = "chicken"
            chicken_document["rustGalWorldModelMeshSetup"] = {
                **cow_document["rustGalWorldModelMeshSetup"],
                "blockId": "minecraft:chicken",
            }
            chicken_document["rustGalWorldModelMeshes"] = [
                {
                    **mesh,
                    "textureId": "minecraft:textures/entity/chicken/temperate_chicken.png",
                    "semanticModelIdentity": "minecraft:chicken",
                }
                for mesh in cow_document["rustGalWorldModelMeshes"]
            ]
            chicken_document["rustGalWorldModelMeshRouteDecisions"] = [
                {**decision, "textureId": "minecraft:textures/entity/chicken/temperate_chicken.png"}
                for decision in cow_document["rustGalWorldModelMeshRouteDecisions"]
            ]
            chicken = harness.deterministic_world_mesh_model_capture_evidence(chicken_document, "chicken")
            self.assertEqual("structural_present", chicken["status"])
            self.assertEqual("passed", chicken["setup_status"])
            pig_document = dict(cow_document)
            pig_document["rustGalWorldModelMeshScenario"] = "pig"
            pig_document["rustGalWorldModelMeshSetup"] = {
                **cow_document["rustGalWorldModelMeshSetup"],
                "blockId": "minecraft:pig",
            }
            pig_document["rustGalWorldModelMeshes"] = [
				{
					**mesh,
					"textureId": "minecraft:textures/entity/pig/temperate_pig.png",
					"semanticModelIdentity": "minecraft:pig",
				}
                for mesh in cow_document["rustGalWorldModelMeshes"]
            ]
            pig_document["rustGalWorldModelMeshRouteDecisions"] = [
				{**decision, "textureId": "minecraft:textures/entity/pig/temperate_pig.png"}
                for decision in cow_document["rustGalWorldModelMeshRouteDecisions"]
            ]
            pig = harness.deterministic_world_mesh_model_capture_evidence(pig_document, "pig")
            self.assertEqual("structural_present", pig["status"])
            self.assertEqual("passed", pig["setup_status"])
            rabbit_document = dict(cow_document)
            rabbit_document["rustGalWorldModelMeshScenario"] = "rabbit"
            rabbit_document["rustGalWorldModelMeshSetup"] = {
                **cow_document["rustGalWorldModelMeshSetup"],
                "blockId": "minecraft:rabbit",
            }
            rabbit_document["rustGalWorldModelMeshes"] = [
                {
                    **mesh,
                    "textureId": "minecraft:textures/entity/rabbit/brown.png",
                    "semanticModelIdentity": "minecraft:rabbit",
                }
                for mesh in cow_document["rustGalWorldModelMeshes"]
            ]
            rabbit_document["rustGalWorldModelMeshRouteDecisions"] = [
                {**decision, "textureId": "minecraft:textures/entity/rabbit/brown.png"}
                for decision in cow_document["rustGalWorldModelMeshRouteDecisions"]
            ]
            rabbit = harness.deterministic_world_mesh_model_capture_evidence(rabbit_document, "rabbit")
            self.assertEqual("structural_present", rabbit["status"])
            self.assertEqual("passed", rabbit["setup_status"])
            zombie_document = dict(cow_document)
            zombie_document["rustGalWorldModelMeshScenario"] = "zombie"
            zombie_document["rustGalWorldModelMeshSetup"] = {
                **cow_document["rustGalWorldModelMeshSetup"],
                "blockId": "minecraft:zombie",
            }
            zombie_document["rustGalWorldModelMeshes"] = [
                {
                    **mesh,
                    "textureId": "minecraft:textures/entity/zombie/zombie.png",
                    "semanticModelIdentity": "minecraft:zombie",
                }
                for mesh in cow_document["rustGalWorldModelMeshes"]
            ]
            zombie_document["rustGalWorldModelMeshRouteDecisions"] = [
                {**decision, "textureId": "minecraft:textures/entity/zombie/zombie.png"}
                for decision in cow_document["rustGalWorldModelMeshRouteDecisions"]
            ]
            zombie_screenshots = [temp / f"zombie-{frame}.png" for frame in range(4)]
            for screenshot in zombie_screenshots:
                write_zombie_model_probe_image(screenshot)
            zombie_document["captures"] = [
                {**capture, "screenshot": str(zombie_screenshots[index])}
                for index, capture in enumerate(zombie_document["captures"])
            ]
            zombie = harness.deterministic_world_mesh_model_capture_evidence(zombie_document, "zombie")
            self.assertEqual("structural_present", zombie["status"])
            self.assertEqual("passed", zombie["setup_status"])
            selected_source_zombie = dict(zombie_document)
            selected_source_zombie["captures"] = [
                {
                    **zombie_document["captures"][0],
                    "captureMethod": "rust-vulkan-final-output",
                    "targetWindow": "rust-vulkan-final-output",
                }
            ]
            selected_source_zombie_evidence = harness.deterministic_world_mesh_model_capture_evidence(
                selected_source_zombie, "zombie"
            )
            self.assertEqual("structural_present", selected_source_zombie_evidence["status"])
            self.assertEqual(1, selected_source_zombie_evidence["required_capture_frames"])
            self.assertEqual(
                "selected_source_entity_final_output",
                selected_source_zombie_evidence["capture_contract"],
            )
            document["captures"][0].update(
                {
                    "captureMethod": "rust-vulkan-final-output",
                    "targetWindow": "rust-vulkan-final-output",
                }
            )
            backend_final = harness.deterministic_world_mesh_model_capture_evidence(document, "chest")
            self.assertEqual("structural_present", backend_final["status"])
            self.assertEqual("rust-vulkan-final-output", backend_final["crops"][0]["capture_method"])
            document["rustGalWorldMovingMeshExecution"] = []
            missing = harness.deterministic_world_mesh_model_capture_evidence(document, "chest")
            self.assertEqual("missing_execution_receipt", missing["status"])
            document["rustGalWorldModelMeshRouteDecisions"] = [
                {
                    "frameIndex": 80,
                    "route": "java-legacy",
                    "textureId": "minecraft:entity/chest/normal",
                    "rustSelected": False,
                    "rustQueued": False,
                    "javaDrawn": True,
                }
            ]
            control = harness.deterministic_world_mesh_model_capture_evidence(document, "chest", "java-compatibility")
            self.assertEqual("control_traversal_present", control["status"])

    def test_piston_capture_rejects_desktop_screenshot(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshot = capture / "desktop_piston.png"
            write_desktop_sized_block_display_probe_image(screenshot, scenario="stone")
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldPistonScenario": "normal-extending",
                "captures": [
                    {
                        "poseName": "initial",
                        "renderedFrameIndex": 8,
                        "screenshot": str(screenshot),
                        "window": {"width": 1280, "height": 720},
                    }
                ],
                "rustGalWorldMovingBlockRouteDecisions": [
                    {
                        "frameIndex": 8,
                        "provenance": "piston",
                        "route": "rust-opengl",
                        "blockId": "minecraft:stone",
                        "rustSelected": True,
                        "rustQueued": True,
                        "javaDrawn": False,
                    }
                ],
                "rustGalWorldMovingBlocks": [
                    {
                        "frameIndex": 8,
                        "route": "rust-opengl",
                        "provenance": "piston",
                        "blockId": "minecraft:stone",
                        "meshKey": 77,
                        "meshGeneration": 3,
                        "vertexLayoutVersion": 1,
                        "indexType": 1,
                        "vertexCount": 24,
                        "indexBytes": 72,
                        "sectionCount": 6,
                        "textureIds": "2269692870",
                        "materialMode": 1,
                        "viewport": {"width": 1280, "height": 720},
                        "projected": True,
                        "screenBounds": {"left": 520.0, "top": 250.0, "right": 760.0, "bottom": 490.0},
                    }
                ],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.pistonScenario=normal-extending\n"
                "Rust OpenGL VulkanicGAL GUI frame executed producer=minecraft.block-entity.piston "
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
            evidence = artifact["metrics"]["rust_gal_slice"]["world_mesh_piston_pixel_evidence"]
            self.assertEqual("not_game_window", evidence["status"])
            self.assertEqual("failed", evidence["game_window_status"])

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

    def test_distant_horizons_palette_final_frame_rejects_swapped_material_color(self) -> None:
        from PIL import Image

        def projected_target(x: int, y: int, sprites: list[str]) -> dict[str, object]:
            def ndc(pixel_x: int, pixel_y: int) -> list[float]:
                return [pixel_x / 255.0 * 2.0 - 1.0, 1.0 - pixel_y / 255.0 * 2.0, 0.0]

            return {
                "matched": True,
                "tileRepeatRequired": True,
                "expectedSprites": sprites,
                "projection": {
                    "status": "ok",
                    "insideClip": True,
                    "ndc": [x / 255.0 * 2.0 - 1.0, 1.0 - y / 255.0 * 2.0],
                },
                "matches": [{
                    "sprite": sprites[0],
                    "tileSpan": [4.0, 1.0],
                    "atlasRect": [0.0, 0.0, 0.25, 0.25],
                    "projection": {
                        "status": "ok",
                        "insideClip": True,
                        "ndcVertices": [
                            ndc(x - 20, y - 20),
                            ndc(x + 20, y - 20),
                            ndc(x + 20, y + 20),
                            ndc(x - 20, y + 20),
                        ],
                    },
                }],
            }

        with tempfile.TemporaryDirectory() as temp_dir:
            screenshot = Path(temp_dir) / "palette.png"
            image = Image.new("RGB", (256, 256), (0, 0, 0))
            palette = [
                (48, 48, (42, 98, 190), ["minecraft:block/lapis_block"]),
                (192, 48, (116, 45, 62), ["minecraft:block/redstone_ore"]),
                (48, 192, (168, 101, 39), ["minecraft:block/yellow_terracotta"]),
                # The selected source path can emit an equal-channel cyan
                # diamond swatch after shader-pack material processing.
                (192, 192, (54, 190, 190), ["minecraft:block/diamond_block"]),
            ]
            for x, y, color, _sprites in palette:
                for pixel_x in range(x - 20, x + 21):
                    for pixel_y in range(y - 20, y + 21):
                        image.putpixel((pixel_x, pixel_y), color)
            image.save(screenshot)
            plan = {
                "paletteTargetCoverage": [
                    projected_target(x, y, sprites) for x, y, _color, sprites in palette
                ]
            }
            document = {"captures": [{"screenshot": str(screenshot)}]}

            passing = harness.deterministic_distant_horizons_texture_palette_pixel_evidence(
                document, plan
            )
            self.assertEqual("present", passing["status"])
            self.assertEqual(4, passing["passed_targets"])
            self.assertEqual(4, passing["repeat_geometry_targets"])

            # The lapis target now has the redstone field. Semantic source
            # receipts can still be valid in this situation; the final-frame
            # gate must reject it rather than reporting a texture pass.
            for pixel_x in range(48 - 20, 48 + 21):
                for pixel_y in range(48 - 20, 48 + 21):
                    image.putpixel((pixel_x, pixel_y), (116, 45, 62))
            image.save(screenshot)
            swapped = harness.deterministic_distant_horizons_texture_palette_pixel_evidence(
                document, plan
            )
            self.assertEqual("palette_mismatch", swapped["status"])
            self.assertEqual(3, swapped["passed_targets"])

            plan["paletteTargetCoverage"][0]["matches"][0]["tileSpan"] = [1.0, 1.0]
            stretched = harness.deterministic_distant_horizons_texture_palette_pixel_evidence(
                document, plan
            )
            self.assertEqual("palette_mismatch", stretched["status"])
            self.assertEqual("missing_repeat_geometry", stretched["targets"][0]["status"])

    def test_distant_horizons_palette_uses_the_exact_quad_footprint(self) -> None:
        from PIL import Image

        def ndc(pixel_x: int, pixel_y: int) -> list[float]:
            return [pixel_x / 255.0 * 2.0 - 1.0, 1.0 - pixel_y / 255.0 * 2.0, 0.0]

        palette = [
            (48, 48, (42, 98, 190), "minecraft:block/lapis_block"),
            (192, 48, (116, 45, 62), "minecraft:block/redstone_ore"),
            (48, 192, (168, 101, 39), "minecraft:block/yellow_terracotta"),
            (192, 192, (54, 190, 170), "minecraft:block/diamond_block"),
        ]
        with tempfile.TemporaryDirectory() as temp_dir:
            screenshot = Path(temp_dir) / "tight-palette.png"
            # This background is deliberately redstone-like. A former 41px
            # square crop would classify three of these tiny valid quads as
            # texture swaps; the projected draw footprint must not.
            image = Image.new("RGB", (256, 256), (116, 45, 62))
            targets = []
            for x, y, color, sprite in palette:
                for pixel_x in range(x - 6, x + 7):
                    for pixel_y in range(y - 6, y + 7):
                        image.putpixel((pixel_x, pixel_y), color)
                targets.append({
                    "matched": True,
                    "tileRepeatRequired": False,
                    "expectedSprites": [sprite],
                    "matches": [{
                        "sprite": sprite,
                        "tileSpan": [1.0, 1.0],
                        "atlasRect": [0.0, 0.0, 0.25, 0.25],
                        "projection": {
                            "status": "ok",
                            "insideClip": True,
                            "ndcVertices": [
                                ndc(x - 6, y - 6), ndc(x + 6, y - 6),
                                ndc(x + 6, y + 6), ndc(x - 6, y + 6),
                            ],
                        },
                    }],
                })
            image.save(screenshot)
            evidence = harness.deterministic_distant_horizons_texture_palette_pixel_evidence(
                {"captures": [{"screenshot": str(screenshot)}]},
                {"paletteTargetCoverage": targets},
            )
            self.assertEqual("present", evidence["status"])
            self.assertEqual(4, evidence["passed_targets"])
            self.assertTrue(all(target["footprint_pixels"] >= 100 for target in evidence["targets"]))

            # A tiny correctly-coloured remnant is not final-frame texture
            # evidence for a reduced DH surface. This used to pass and masked
            # broad unresolved colour-only terrain.
            targets[0]["matches"][0]["projection"]["ndcVertices"] = [
                ndc(46, 46), ndc(50, 46), ndc(50, 50), ndc(46, 50),
            ]
            insufficient = harness.deterministic_distant_horizons_texture_palette_pixel_evidence(
                {"captures": [{"screenshot": str(screenshot)}]},
                {"paletteTargetCoverage": targets},
            )
            self.assertEqual("palette_mismatch", insufficient["status"])
            self.assertEqual("insufficient_texture_footprint", insufficient["targets"][0]["status"])

    def test_distant_horizons_source_palette_uses_its_declared_row_orientation(self) -> None:
        from PIL import Image

        def ndc(pixel_x: int, pixel_y: int) -> list[float]:
            # The raw Vulkan attachment has already been normalized to a
            # top-left PNG, while its source viewport keeps positive NDC Y
            # downward for this diagnostic contract.
            return [pixel_x / 255.0 * 2.0 - 1.0, pixel_y / 255.0 * 2.0 - 1.0, 0.0]

        with tempfile.TemporaryDirectory() as temp_dir:
            screenshot = Path(temp_dir) / "source-primary.png"
            image = Image.new("RGB", (256, 256), (0, 0, 0))
            for x in range(80, 177):
                for y in range(80, 177):
                    image.putpixel((x, y), (216, 104, 90))
            image.save(screenshot)
            plan = {
                "paletteTargetCoverage": [{
                    "matched": True,
                    "tileRepeatRequired": True,
                    "expectedSprites": ["minecraft:block/redstone_ore"],
                    "requiredSprites": ["minecraft:block/redstone_ore"],
                    "matches": [{
                        "sprite": "minecraft:block/redstone_ore",
                        "tileSpan": [8.0, 1.0],
                        "atlasRect": [0.0, 0.0, 0.25, 0.25],
                        "projection": {
                            "status": "ok",
                            "insideClip": True,
                            "ndcVertices": [
                                ndc(80, 80), ndc(176, 80), ndc(176, 176), ndc(80, 176),
                            ],
                        },
                    }],
                }]
            }
            # The shared four-target palette schema remains mandatory.
            plan["paletteTargetCoverage"] *= 4
            evidence = harness.deterministic_distant_horizons_texture_palette_pixel_evidence(
                {"captures": [{"screenshot": str(screenshot)}]},
                plan,
                ndc_y_positive_down=True,
                minimum_texture_footprint_pixels=12,
            )
            self.assertEqual("present", evidence["status"])
            self.assertEqual(4, evidence["passed_targets"])

    def test_distant_horizons_final_visibility_allows_pack_fog_but_requires_source_identity(self) -> None:
        source = {
            "targets": [
                {"target": [1, 2, 3], "mean_rgb": [0.9, 0.2, 0.1]},
                {"target": [4, 5, 6], "mean_rgb": [0.2, 0.8, 0.1]},
                {"target": [7, 8, 9], "mean_rgb": [0.8, 0.6, 0.1]},
                {"target": [10, 11, 12], "mean_rgb": [0.1, 0.7, 0.2]},
            ]
        }
        final = {
            "targets": [
                {"target": [1, 2, 3], "mean_rgb": [0.7, 0.75, 0.85]},
                {"target": [4, 5, 6], "mean_rgb": [0.7, 0.75, 0.85]},
                {"target": [7, 8, 9], "mean_rgb": [0.7, 0.75, 0.85]},
                {"target": [10, 11, 12], "mean_rgb": [0.7, 0.75, 0.85]},
            ]
        }
        evidence = harness.distant_horizons_source_to_final_color_transform_evidence(source, final)
        self.assertEqual("pack_transform_present", evidence["status"])
        self.assertEqual(4, evidence["transformed_targets"])

    def test_distant_horizons_final_visibility_rejects_an_uncontrasted_world_footprint(self) -> None:
        from PIL import Image

        def ndc(pixel_x: int, pixel_y: int) -> list[float]:
            return [pixel_x / 255.0 * 2.0 - 1.0, 1.0 - pixel_y / 255.0 * 2.0, 0.0]

        with tempfile.TemporaryDirectory() as temp_dir:
            screenshot = Path(temp_dir) / "fogged-empty.png"
            Image.new("RGB", (256, 256), (179, 191, 217)).save(screenshot)
            target = {
                "matched": True,
                "tileRepeatRequired": True,
                "expectedSprites": ["minecraft:block/redstone_ore"],
                "matches": [{
                    "sprite": "minecraft:block/redstone_ore",
                    "tileSpan": [4.0, 1.0],
                    "atlasRect": [0.0, 0.0, 0.25, 0.25],
                    "projection": {
                        "status": "ok",
                        "insideClip": True,
                        "ndcVertices": [
                            ndc(96, 96), ndc(160, 96), ndc(160, 160), ndc(96, 160),
                        ],
                    },
                }],
            }
            evidence = harness.deterministic_distant_horizons_texture_palette_pixel_evidence(
                {"captures": [{"screenshot": str(screenshot)}]},
                {"paletteTargetCoverage": [target] * 4},
                require_palette_color=False,
                minimum_passed_targets=2,
                minimum_world_contrast=0.02,
            )
            self.assertEqual("palette_mismatch", evidence["status"])
            self.assertTrue(
                all(entry["status"] == "insufficient_world_contrast" for entry in evidence["targets"]),
                evidence,
            )

    def test_static_terrain_palette_final_frame_rejects_swapped_material_color(self) -> None:
        from PIL import Image

        def probe(x: int, y: int, sprites: list[str]) -> dict[str, object]:
            return {
                "matched": True,
                "position": f"{x},{y},0",
                "allowedSprites": sprites,
                # The harness scales these semantic 1280x720 coordinates to
                # the retained image rather than trusting a desktop crop.
                "projection": {"screen": [x * 5, y * 720 / 256], "insideViewport": True},
            }

        with tempfile.TemporaryDirectory() as temp_dir:
            screenshot = Path(temp_dir) / "palette.png"
            image = Image.new("RGB", (256, 256), (0, 0, 0))
            palette = [
                (48, 48, (42, 98, 51), ["minecraft:block/grass_block_top"]),
                (192, 48, (116, 45, 62), ["minecraft:block/redstone_ore"]),
                (48, 192, (168, 101, 39), ["minecraft:block/yellow_terracotta"]),
                (192, 192, (54, 112, 48), ["minecraft:block/oak_leaves"]),
            ]
            for x, y, color, _sprites in palette:
                for pixel_x in range(x - 20, x + 21):
                    for pixel_y in range(y - 20, y + 21):
                        image.putpixel((pixel_x, pixel_y), color)
            image.save(screenshot)
            document = {"captures": [{"screenshot": str(screenshot)}]}
            receipt = {"probes": [probe(x, y, sprites) for x, y, _color, sprites in palette]}

            passing = harness.deterministic_static_terrain_texture_palette_pixel_evidence(
                document, receipt
            )
            self.assertEqual("present", passing["status"])
            self.assertEqual(4, passing["passed_targets"])

            # A correct atlas receipt alone must not excuse a final shader
            # sampling swap from grass to redstone.
            for pixel_x in range(48 - 20, 48 + 21):
                for pixel_y in range(48 - 20, 48 + 21):
                    image.putpixel((pixel_x, pixel_y), (116, 45, 62))
            image.save(screenshot)
            swapped = harness.deterministic_static_terrain_texture_palette_pixel_evidence(
                document, receipt
            )
            self.assertEqual("palette_mismatch", swapped["status"])
            self.assertEqual(3, swapped["passed_targets"])

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
                "window": {"width": 1280, "height": 720},
                "rustGalWorldFallingBlockScenario": "sand",
                "rustGalWorldFallingBlockSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:sand",
                    "spawnMethod": "FallingBlockEntity.fall",
                    "entityCount": 1,
                    "fallHeight": 64,
                },
                "rustGalWorldFallingBlockExtractionProbe": {
                    "seen": 4,
                    "shouldRender": 4,
                    "compiledSection": 4,
                    "extracted": 4,
                },
                "captures": [{"poseName": f"falling-{frame:02d}", "renderedFrameIndex": 8 + frame} for frame in range(4)],
                "rustGalWorldFallingBlocks": [
                    {
                        "frameIndex": 8 + frame,
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
                    for frame in range(4)
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

    def test_falling_block_capture_rejects_missing_real_producer_traversal(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshots = []
            for frame in range(4):
                screenshot = capture / f"falling_block_no_probe_{frame}.png"
                write_falling_block_probe_image(screenshot, scenario="sand")
                screenshots.append(screenshot)
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "window": {"width": 1280, "height": 720},
                "rustGalWorldFallingBlockScenario": "sand",
                "rustGalWorldFallingBlockSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:sand",
                    "spawnMethod": "FallingBlockEntity.fall",
                    "entityCount": 1,
                    "fallHeight": 64,
                },
                "rustGalWorldFallingBlockExtractionProbe": {
                    "seen": 0,
                    "shouldRender": 0,
                    "compiledSection": 0,
                    "extracted": 0,
                },
                "captures": [
                    {
                        "poseName": f"falling-{frame:02d}",
                        "renderedFrameIndex": 8 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "internal-main-render-target",
                    }
                    for frame in range(4)
                ],
                "rustGalWorldFallingBlocks": [],
            }
            next(capture.glob("deterministic_camera_capture_*.json")).write_text(json.dumps(deterministic), encoding="utf-8")
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
                tool_kind="capture",
            )

            self.assertFalse(artifact["validation"]["complete"])
            evidence = artifact["metrics"]["rust_gal_slice"]["world_mesh_falling_block_pixel_evidence"]
            self.assertEqual("missing_producer_traversal", evidence["status"])
            self.assertEqual("failed", evidence["producer_traversal_status"])

    def test_falling_block_shaders_on_requires_configured_shaderpack(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(spec for spec in harness.MATRIX_MODES if spec.name == "current-opengl-shaders-on")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            screenshots = []
            for frame in range(4):
                screenshot = capture / f"falling_block_wrong_shader_{frame}.png"
                write_falling_block_probe_image(screenshot, scenario="sand")
                screenshots.append(screenshot)
            deterministic = {
                "status": "complete",
                "dimension": "minecraft:overworld",
                "window": {"width": 1280, "height": 720},
                "shaderEnabled": "true",
                "shaderPack": "WrongShader.zip",
                "rustGalWorldFallingBlockScenario": "sand",
                "rustGalWorldFallingBlockSetup": {
                    "status": "spawned",
                    "blockId": "minecraft:sand",
                    "spawnMethod": "FallingBlockEntity.fall",
                    "entityCount": 1,
                    "fallHeight": 64,
                },
                "rustGalWorldFallingBlockExtractionProbe": {
                    "seen": 4,
                    "shouldRender": 4,
                    "compiledSection": 4,
                    "extracted": 4,
                },
                "captures": [
                    {
                        "poseName": f"falling-{frame:02d}",
                        "renderedFrameIndex": 8 + frame,
                        "screenshot": str(screenshots[frame]),
                        "window": {"width": 1280, "height": 720},
                        "captureMethod": "internal-main-render-target",
                    }
                    for frame in range(4)
                ],
                "rustGalWorldFallingBlocks": [
                    {
                        "frameIndex": 8 + frame,
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
                    for frame in range(4)
                ],
                "rustGalWorldFallingBlockRouteDecisions": [
                    {
                        "frameIndex": 8 + frame,
                        "route": "rust-opengl",
                        "blockId": "minecraft:sand",
                        "rustSelected": True,
                        "rustQueued": True,
                        "javaDrawn": False,
                    }
                    for frame in range(4)
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
                any("configured shader pack" in message for message in artifact["validation"]["messages"])
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
                next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off"),
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
            self.assertTrue(artifact["validation"]["vulkan_validation_clean"])
            self.assertIn("loader/configuration notices", artifact["validation"]["vulkan_validation_note"])

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
                next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off"),
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
                next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off"),
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
                next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off"),
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

    def test_renderdoc_diagnosis_reads_compressed_game_log(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir)
            log = capture / "runClient_20260101_000000.log.gz"
            with gzip.open(log, "wt", encoding="utf-8") as handle:
                handle.write(
                    "RenderDoc API initialized pathTemplate=/tmp/capture\n"
                    "Triggered RenderDoc capture for next deterministic frame (terrain#12)\n"
                    "Started RenderDoc frame capture (terrain#12) backend=vulkan windowPointer=123\n"
                    "Ended RenderDoc frame capture (terrain#12) backend=vulkan windowPointer=123 result=0\n"
                )
            diagnosis = harness.diagnose_renderdoc_capture_failure(capture, capture / "missing.rdc")
            self.assertTrue(diagnosis["renderdoc_api_initialized"])
            self.assertTrue(diagnosis["frame_capture_triggered"])
            self.assertTrue(diagnosis["frame_capture_started"])
            self.assertEqual(diagnosis["end_results"][0]["result"], "0")
            self.assertIn("EndFrameCapture returned 0", diagnosis["likely_cause"])

    def test_renderdoc_diagnosis_reports_preload_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir)
            (capture / "meta_preflight_20260101.txt").write_text(
                "renderdoc_preload_library=/does/not/exist/librenderdoc.so\n",
                encoding="utf-8",
            )
            diagnosis = harness.diagnose_renderdoc_capture_failure(capture, capture / "missing.rdc")
            self.assertFalse(diagnosis["renderdoc_stages"]["library_preloaded"])
            self.assertIn("preload library path is missing", diagnosis["likely_cause"])

    def test_renderdoc_diagnosis_reports_api_unavailable(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir)
            (capture / "runClient_20260101_000000.log").write_text(
                "RenderDoc API was not available from renderdoc result=0\n",
                encoding="utf-8",
            )
            diagnosis = harness.diagnose_renderdoc_capture_failure(capture, capture / "missing.rdc")
            self.assertTrue(diagnosis["renderdoc_api_unavailable"])
            self.assertIn("reported unavailable", diagnosis["likely_cause"])

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

    def test_renderdoc_summary_preserves_successful_capture_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir)
            capture_path = capture / "terrain_capture.rdc"
            capture_path.write_bytes(b"rdc")
            renderdoccmd = capture / "renderdoccmd"
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
                summary_path = harness.replay_renderdoc_summary(capture, capture_path)
                summary = json.loads(summary_path.read_text(encoding="utf-8"))
                self.assertEqual(summary["status"], "complete")
                self.assertEqual(summary["capture_path"], str(capture_path))
                self.assertEqual(summary["draw_count"], 1)
            finally:
                harness.local_renderdoccmd_path = original_cmd  # type: ignore[assignment]

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
                world_text_scenario="block-display",
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
                self.assertIn("mattmc.dev.rustGalWorldText.scenario=block-display", env["JAVA_TOOL_OPTIONS"])
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
                self.assertIn(
                    "mattmc.dev.rustGalWorldText.scenario=block-display",
                    gameplay_env["JAVA_TOOL_OPTIONS"],
                )
            finally:
                harness.local_renderdoccmd_path = original  # type: ignore[assignment]

    def test_rust_vulkan_gameplay_validation_enables_khronos_layer(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            args = harness.parse_args(
                [
                    "gameplay",
                    "--profile",
                    "standard",
                    "--mode",
                    "current-rust-vulkan-shaders-on",
                    "--validation",
                    "routine",
                    "--world-mesh-falling-block-scenario",
                    "sand",
                    "--dry-run",
                ]
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")

            command, env = harness.build_capture_command(
                target,
                mode,
                root / "gameplay",
                "gameplay",
                args,
                "gameplay",
            )

            self.assertIn("--validation", command)
            self.assertIn("standard", command)
            self.assertEqual("routine", env["MATTMC_GRAPHICS_VALIDATION_PROFILE"])
            self.assertEqual("VK_LAYER_KHRONOS_validation", env["VK_INSTANCE_LAYERS"])
            self.assertEqual("info", env["VK_LOADER_DEBUG"])
            self.assertIn("validate_sync=true", env["VK_LAYER_SETTINGS"])

    def test_selected_source_execution_is_explicit_and_rust_vulkan_only(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            rust_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--rust-selected-source-execution",
                    "--world-mesh-model-scenario",
                    "decorated-pot",
                ]
            )
            _command, env = harness.build_capture_command(
                target, rust_mode, root / "capture", "correctness", args, "capture"
            )
            self.assertEqual("1", env["MATTMC_RUST_SELECTED_SOURCE_EXECUTION"])
            self.assertEqual("true", env["MATTMC_GRAPHICS_AUDIT"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.requiredRustSourceExecutionDir="
                + str(root / "capture" / "terrain_pass_contract"),
                env["JAVA_TOOL_OPTIONS"],
            )
            self.assertNotIn("-Dmattmc.dev.rustGalWorldModelPart.v1=true", env["JAVA_TOOL_OPTIONS"])

            attachment_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--rust-full-gameplay-attachments",
                    "--world-mesh-falling-block-scenario",
                    "sand",
                ]
            )
            _attachment_command, attachment_env = harness.build_capture_command(
                target, rust_mode, root / "attachments", "correctness", attachment_args, "capture"
            )
            self.assertEqual("1", attachment_env["MATTMC_RUST_SELECTED_SOURCE_EXECUTION"])

            gameplay_args = harness.parse_args(
                [
                    "gameplay",
                    "--profile",
                    "smoke",
                    "--mode",
                    rust_mode.name,
                    "--rust-selected-source-execution",
                    "--world",
                    "Origin",
                    "--diagnostic",
                ]
            )
            _gameplay_command, gameplay_env = harness.build_capture_command(
                target, rust_mode, root / "gameplay", "gameplay", gameplay_args, "gameplay"
            )
            self.assertEqual(
                str(root / "gameplay" / "terrain_pass_contract"),
                gameplay_env["MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR"],
            )

            source_generic_model_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--rust-selected-source-execution",
                    "--world-mesh-model-scenario",
                    "chest",
                ]
            )
            _, source_generic_model_env = harness.build_capture_command(
                target, rust_mode, root / "generic-model", "correctness", source_generic_model_args, "capture"
            )
            self.assertNotIn("-Dmattmc.dev.rustGalWorldModelMesh.v1=true", source_generic_model_env["JAVA_TOOL_OPTIONS"])

            source_weather_cloud_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--rust-selected-source-execution",
                    "--world-weather-scenario",
                    "rain",
                    "--world-cloud-scenario",
                    "bounded",
                ]
            )
            _, source_weather_cloud_env = harness.build_capture_command(
                target, rust_mode, root / "weather-cloud", "correctness", source_weather_cloud_args, "capture"
            )
            self.assertNotIn("-Dmattmc.dev.rustGalWeather.v1=true", source_weather_cloud_env["JAVA_TOOL_OPTIONS"])
            self.assertNotIn("-Dmattmc.dev.rustGalClouds.v1=true", source_weather_cloud_env["JAVA_TOOL_OPTIONS"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.sourceEntityIsolation=true",
                source_weather_cloud_env["JAVA_TOOL_OPTIONS"],
            )

            source_dh_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--rust-selected-source-execution",
                    "--world-distant-horizons-opaque",
                ]
            )
            _, source_dh_env = harness.build_capture_command(
                target, rust_mode, root / "distant-horizons", "correctness", source_dh_args, "capture"
            )
            self.assertEqual("true", source_dh_env["MATTMC_CAPTURE_RESET_DH_DATABASE"])
            self.assertNotIn("-Dmattmc.dev.rustGalDistantHorizons.opaqueV1=true", source_dh_env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.rustGalDistantHorizons.semanticCapture=true", source_dh_env["JAVA_TOOL_OPTIONS"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.poseCount=1",
                source_dh_env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0",
                source_dh_env["JAVA_TOOL_OPTIONS"],
            )

            frozen_target = fake_repo(root / "frozen", "frozen")
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            frozen_dh_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    frozen_mode.name,
                    "--world-distant-horizons-opaque",
                ]
            )
            _command, frozen_dh_env = harness.build_capture_command(
                frozen_target, frozen_mode, root / "frozen-distant-horizons", "correctness", frozen_dh_args, "capture"
            )
            self.assertEqual("true", frozen_dh_env["MATTMC_CAPTURE_RESET_DH_DATABASE"])
            self.assertNotIn(
                "-Dmattmc.dev.rustGalDistantHorizons.semanticCapture=true",
                frozen_dh_env["JAVA_TOOL_OPTIONS"],
            )
            self.assertNotIn(
                "-Dmattmc.dev.graphicsFrameBenchmark.requireDistantHorizonsExecution=true",
                frozen_dh_env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=distant-horizons",
                frozen_dh_env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.poseCount=1",
                frozen_dh_env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0",
                frozen_dh_env["JAVA_TOOL_OPTIONS"],
            )

            isolated_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--world-mesh-model-scenario",
                    "decorated-pot",
                ]
            )
            _, isolated_env = harness.build_capture_command(
                target, rust_mode, root / "isolated", "correctness", isolated_args, "capture"
            )
            self.assertNotIn("-Dmattmc.dev.rustGalWorldModelPart.v1=true", isolated_env["JAVA_TOOL_OPTIONS"])
            self.assertNotIn("-Dmattmc.dev.rustGalWorldModelMesh.v1=true", isolated_env["JAVA_TOOL_OPTIONS"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.sourceEntityIsolation=true",
                isolated_env["JAVA_TOOL_OPTIONS"],
            )

            shaders_off_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            shaders_off_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    shaders_off_mode.name,
                    "--rust-selected-source-execution",
                    "--world-mesh-model-scenario",
                    "cow",
                ]
            )
            with self.assertRaisesRegex(ValueError, "requires shaders on"):
                harness.build_capture_command(
                    target, shaders_off_mode, root / "rust-vulkan-shaders-off", "correctness", shaders_off_args, "capture"
                )

            flame_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--world-mesh-model-scenario",
                    "cow",
                    "--world-entity-flame-scenario",
                    "cow",
                ]
            )
            _, flame_env = harness.build_capture_command(
                target, rust_mode, root / "entity-flame", "correctness", flame_args, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.modelScenario=cow", flame_env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.rustGalWorldEntityFlame.scenario=cow", flame_env["JAVA_TOOL_OPTIONS"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.sourceEntityIsolation=true",
                flame_env["JAVA_TOOL_OPTIONS"],
            )

            shadow_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--world-mesh-model-scenario",
                    "cow",
                    "--world-entity-shadow-scenario",
                    "cow",
                ]
            )
            _, shadow_env = harness.build_capture_command(
                target, rust_mode, root / "entity-shadow", "correctness", shadow_args, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.modelScenario=cow", shadow_env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.rustGalWorldEntityShadow.scenario=cow", shadow_env["JAVA_TOOL_OPTIONS"])

            leash_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--world-mesh-model-scenario",
                    "cow",
                    "--world-entity-leash-scenario",
                    "cow",
                ]
            )
            _, leash_env = harness.build_capture_command(
                target, rust_mode, root / "entity-leash", "correctness", leash_args, "capture"
            )
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.modelScenario=cow", leash_env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.rustGalWorldEntityLeash.scenario=cow", leash_env["JAVA_TOOL_OPTIONS"])

            invalid_flame_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--world-entity-flame-scenario",
                    "cow",
                ]
            )
            with self.assertRaisesRegex(ValueError, "requires --world-mesh-model-scenario cow"):
                harness.build_capture_command(
                    target, rust_mode, root / "invalid-entity-flame", "correctness", invalid_flame_args, "capture"
                )

            invalid_shadow_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--world-entity-shadow-scenario",
                    "cow",
                ]
            )
            with self.assertRaisesRegex(ValueError, "requires --world-mesh-model-scenario cow"):
                harness.build_capture_command(
                    target, rust_mode, root / "invalid-entity-shadow", "correctness", invalid_shadow_args, "capture"
                )

            invalid_leash_args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    rust_mode.name,
                    "--world-entity-leash-scenario",
                    "cow",
                ]
            )
            with self.assertRaisesRegex(ValueError, "requires --world-mesh-model-scenario cow"):
                harness.build_capture_command(
                    target, rust_mode, root / "invalid-entity-leash", "correctness", invalid_leash_args, "capture"
                )

    def test_static_capture_does_not_require_unrequested_selected_source_execution(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            for mode_name in ("current-rust-vulkan-shaders-off", "current-rust-vulkan-shaders-on"):
                mode = next(mode for mode in harness.MATRIX_MODES if mode.name == mode_name)
                args = harness.parse_args(
                    [
                        "capture",
                        "--profile",
                        "standard",
                        "--mode",
                        mode.name,
                        "--world-static-terrain-scenario",
                        "texture-palette",
                    ]
                )
                _command, env = harness.build_capture_command(
                    target, mode, root / mode_name, "correctness", args, "capture"
                )
                self.assertNotIn("MATTMC_RUST_SELECTED_SOURCE_EXECUTION", env)
                self.assertNotIn("requiredRustSourceExecutionDir", env.get("JAVA_TOOL_OPTIONS", ""))
                self.assertEqual("35184407740420", env.get("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_SECTION"))
                self.assertEqual("133,82,559", env.get("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_BLOCK"))
                self.assertIn(
                    "-Dmattmc.dev.staticTerrainParityDiagnostics.requireLightmapCapture=true",
                    env["JAVA_TOOL_OPTIONS"],
                )

    def test_translucent_terrain_capture_enables_only_the_bounded_rust_appearance_trace(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            for scenario in ("translucent-overlap", "translucent-water"):
                with self.subTest(scenario=scenario):
                    args = harness.parse_args(
                        [
                            "capture",
                            "--profile",
                            "standard",
                            "--mode",
                            mode.name,
                            "--world-static-terrain-scenario",
                            scenario,
                        ]
                    )
                    capture_dir = root / scenario
                    _command, env = harness.build_capture_command(
                        target, mode, capture_dir, "correctness", args, "capture"
                    )
                    trace_dir = env.get("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_DIR")
                    self.assertEqual(str(capture_dir / "static_terrain_appearance"), trace_dir)
                    self.assertEqual(trace_dir, env.get("MATTMC_STATIC_TERRAIN_BATCH_TRACE_DIR"))
                    self.assertNotIn("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_SECTION", env)
                    self.assertNotIn("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_BLOCK", env)

    def test_texture_palette_and_model_fixture_reject_overlapping_world_coordinates(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-static-terrain-scenario",
                    "texture-palette",
                    "--world-mesh-model-scenario",
                    "chest",
                ]
            )
            with self.assertRaisesRegex(ValueError, "fixtures use overlapping world coordinates"):
                harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")

    def test_selected_source_execution_does_not_leak_into_subsystem_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "matrix",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                ]
            )
            _command, env = harness.build_capture_command(
                target, mode, root / "subsystem", "correctness", args, "subsystem"
            )
            self.assertNotIn("MATTMC_RUST_SELECTED_SOURCE_EXECUTION", env)
            self.assertNotIn("MATTMC_GRAPHICS_AUDIT", env)

    def test_selected_source_execution_grace_requires_an_observed_native_receipt(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir) / "capture"
            self.assertFalse(harness.selected_source_execution_armed(capture))
            self.assertFalse(harness.selected_source_execution_observed(capture))
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir(parents=True)
            (contract_dir / "selected-source-admission-status.json").write_text(
                json.dumps({"source_execution_armed": False}), encoding="utf-8"
            )
            self.assertFalse(harness.selected_source_execution_armed(capture))
            (contract_dir / "selected-source-admission-status.json").write_text(
                json.dumps({"source_execution_armed": True}), encoding="utf-8"
            )
            self.assertTrue(harness.selected_source_execution_armed(capture))
            self.assertFalse(harness.selected_source_execution_observed(capture))
            (contract_dir / "selected-source-execution-distant-horizons-frame-42.json").write_text(
                json.dumps(
                    {
                        "frame_id": 42,
                        "submission_id": 9,
                        "route": "rust-native-selected-source",
                    }
                ),
                encoding="utf-8",
            )
            self.assertTrue(harness.selected_source_execution_observed(capture))

    def test_whole_frame_terrain_grace_accepts_benchmark_progress_when_logs_are_redirected(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            capture = Path(temp_dir) / "capture"
            capture.mkdir(parents=True)
            (capture / "graphics_frame_benchmark_1.json").write_text(
                json.dumps(
                    {
                        "status": "warming_or_settling",
                        "staticTerrainScenario": {
                            "acceptedBuildOutputs": 3,
                            "visibleLayerSubmissions": 12,
                            "registeredMeshes": 6,
                        },
                    }
                ),
                encoding="utf-8",
            )
            self.assertTrue(harness.rust_whole_frame_terrain_progress_observed(capture))

    def test_resource_pack_terrain_grace_is_bounded_and_only_for_rust_gameplay(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            args = harness.parse_args(
                [
                    "gameplay",
                    "--profile",
                    "standard",
                    "--mode",
                    "current-rust-vulkan-shaders-off",
                    "--gui-resource-pack-scenario",
                    "pack-a",
                ]
            )
            rust_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            self.assertEqual(
                harness.RESOURCE_PACK_TERRAIN_ADMISSION_GRACE_SECONDS,
                harness.terrain_admission_grace_seconds(args, rust_mode, "gameplay"),
            )
            self.assertEqual(
                harness.WHOLE_FRAME_TERRAIN_ADMISSION_GRACE_SECONDS,
                harness.terrain_admission_grace_seconds(args, frozen_mode, "gameplay"),
            )
            self.assertEqual(
                harness.WHOLE_FRAME_TERRAIN_ADMISSION_GRACE_SECONDS,
                harness.terrain_admission_grace_seconds(args, rust_mode, "capture"),
            )

    def test_gui_pack_capture_schedule_overrides_canonical_static_default(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--gui-resource-pack-scenario",
                    "priority-a-b",
                ]
            )
            args._canonical_fixture_run_source = root / "fixture"
            _command, env = harness.build_capture_command(
                fake_repo(root, "frozen"), mode, root / "capture", "correctness", args, "capture"
            )
            options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertEqual("-Dmattmc.dev.deterministicCameraCapture.poseCount=4", options[-2])
            self.assertEqual("-Dmattmc.dev.deterministicCameraCapture.yawDelta=35.0", options[-1])

    def test_selected_source_falling_block_requires_correlated_moving_mesh_execution(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(root, mode.target)
            capture = root / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic_path = next(capture.glob("deterministic_camera_capture_*.json"))
            deterministic = json.loads(deterministic_path.read_text(encoding="utf-8"))
            deterministic.update(
                {
                    "rustGalWorldFallingBlockScenario": "sand",
                    "rustGalWorldFallingBlockSetup": {
                        "status": "spawned",
                        "blockId": "minecraft:sand",
                        "spawnMethod": "FallingBlockEntity.fall",
                        "entityCount": 1,
                    },
                    "rustGalWorldFallingBlockExtractionProbe": {
                        "seen": 1,
                        "shouldRender": 1,
                        "compiledSection": 1,
                        "extracted": 1,
                    },
                    "rustGalWorldFallingBlocks": [
                        {
                            "frameIndex": 42,
                            "route": "rust-vulkan-whole-frame",
                            "blockId": "minecraft:sand",
                            "meshKey": 42,
                            "meshGeneration": 2,
                            "vertexLayoutVersion": 3,
                            "indexType": 1,
                            "vertexCount": 24,
                            "indexBytes": 72,
                            "sectionCount": 6,
                            "textureIds": "2269692870",
                            "materialMode": 1,
                            "viewport": {"width": 1280, "height": 720},
                            "projected": True,
                            "screenBounds": {"left": 500.0, "top": 60.0, "right": 720.0, "bottom": 280.0},
                        }
                    ],
                    "rustGalWorldFallingBlockRouteDecisions": [
                        {
                            "frameIndex": 42,
                            "route": "rust-vulkan-whole-frame",
                            "blockId": "minecraft:sand",
                            "rustSelected": True,
                            "rustQueued": True,
                            "javaDrawn": False,
                        }
                    ],
                }
            )
            deterministic_path.write_text(json.dumps(deterministic), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n"
                "rust_gal_world_mesh_instances_executed=1 "
                "rust_gal_world_mesh_batches_executed=1 "
                "rust_gal_world_mesh_draws_executed=1\n",
                encoding="utf-8",
            )
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            receipt = {
                "frame_id": 42,
                "submission_id": 9,
                "world_generation": 2,
                "route": "rust-native-selected-source",
                "source_mesh_instance_semantics": {"moving_mesh_instances": 0},
            }
            (contract_dir / "selected-source-execution-frame-42.json").write_text(
                json.dumps(receipt), encoding="utf-8"
            )

            missing = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any(
                    "selected-source FallingBlock execution did not retain a frame/submission-correlated"
                    in message
                    for message in missing["validation"]["messages"]
                ),
                missing["validation"]["messages"],
            )

            deterministic["rustGalWorldMovingMeshExecution"] = [
                {
                    "deterministicFrameIndex": 42,
                    "route": "rust-vulkan-whole-frame",
                    "provenance": "falling-block",
                    "gameplayFrameId": 42,
                    "submissionId": 9,
                    "instances": 1,
                }
            ]
            deterministic_path.write_text(json.dumps(deterministic), encoding="utf-8")
            receipt["source_mesh_instance_semantics"]["moving_mesh_instances"] = 1
            (contract_dir / "selected-source-execution-frame-42.json").write_text(
                json.dumps(receipt), encoding="utf-8"
            )
            admitted = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "selected-source FallingBlock execution did not retain a frame/submission-correlated"
                    in message
                    for message in admitted["validation"]["messages"]
                ),
                admitted["validation"]["messages"],
            )

    def test_selected_source_static_capture_bounds_only_the_vanilla_fixture_radius(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-static-terrain-scenario",
                    "texture-palette",
                    "--rust-selected-source-execution",
                ]
            )
            _command, env = harness.build_capture_command(
                target, mode, root / "capture", "correctness", args, "capture"
            )
            self.assertEqual("4", env["MATTMC_CAPTURE_RENDER_DISTANCE"])
            self.assertEqual("4", env["MATTMC_CAPTURE_SIMULATION_DISTANCE"])
            self.assertNotEqual("true", env.get("MATTMC_CAPTURE_DISABLE_DH_FOR_PERF", "false"))

    def test_selected_source_distant_horizons_palette_keeps_its_target_inside_the_client_radius(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-distant-horizons-opaque",
                    "--world-distant-horizons-texture-palette",
                    "--rust-selected-source-execution",
                ]
            )
            _command, env = harness.build_capture_command(
                target, mode, root / "capture", "correctness", args, "capture"
            )
            self.assertEqual("6", env["MATTMC_CAPTURE_RENDER_DISTANCE"])
            self.assertEqual("5", env["MATTMC_CAPTURE_SIMULATION_DISTANCE"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFrames=2",
                env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=distant-horizons",
                env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.sourceEntityIsolation=true",
                env["JAVA_TOOL_OPTIONS"],
            )
            self.assertNotIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=static-terrain",
                env["JAVA_TOOL_OPTIONS"],
            )

    def test_capture_runner_keeps_dh_palette_source_radius_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            config = isolated_capture_config(root, world="Origin")
            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_DH_RUST_OPAQUE_ONLY": "true",
                    "MATTMC_CAPTURE_DH_TEXTURE_PALETTE": "true",
                },
                clear=False,
            ):
                runner = capture_runner.CaptureRunner(config)
                runner.run_dir = root / "run"
                runner.options_file = runner.run_dir / "options.txt"
                runner.run_dir.mkdir(parents=True)
                dh_config_path = runner.run_dir / "config" / "DistantHorizons.toml"
                dh_config_path.parent.mkdir(parents=True)
                dh_config_path.write_text("lodChunkRenderDistanceRadius = 4\n", encoding="utf-8")
                runner.configure_backend_and_validation()
            dh_config = (runner.run_dir / "config" / "DistantHorizons.toml").read_text(encoding="utf-8")
            self.assertIn("lodChunkRenderDistanceRadius = 4", dh_config)

    def test_selected_source_distant_horizons_requires_extended_profile_for_capture_and_gameplay(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-distant-horizons-opaque",
                    "--world-distant-horizons-texture-palette",
                    "--rust-selected-source-execution",
                ]
            )
            reason = harness.profile_not_supported_reason("standard", mode, "capture", args)
            self.assertIn("requires at least extended", reason or "")
            self.assertIsNone(harness.profile_not_supported_reason("extended", mode, "capture", args))
            reason = harness.profile_not_supported_reason("standard", mode, "gameplay", args)
            self.assertIn("requires at least extended", reason or "")
            self.assertIsNone(harness.profile_not_supported_reason("extended", mode, "gameplay", args))

    def test_rust_vulkan_gameplay_requires_extended_profile_for_real_dh_startup(self) -> None:
        mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
        args = harness.parse_args(["gameplay", "--profile", "standard", "--mode", mode.name])
        reason = harness.profile_not_supported_reason("standard", mode, "gameplay", args)
        self.assertIsNone(reason)
        args = harness.parse_args(
            ["gameplay", "--profile", "standard", "--mode", mode.name, "--world-distant-horizons-opaque"]
        )
        reason = harness.profile_not_supported_reason("standard", mode, "gameplay", args)
        self.assertIn("requires at least extended", reason or "")
        self.assertIsNone(harness.profile_not_supported_reason("extended", mode, "gameplay", args))

    def test_requested_source_plan_rejects_normal_terrain_without_native_admission(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps(
                    {
                        "source_contract_discovered": True,
                        "selected_source_execution_requested": True,
                        "selected_source_execution_admitted": False,
                        "execution": "internal-terrain-fixture",
                    }
                ),
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
                tool_kind="capture",
            )

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any(
                    "selected-source execution was requested but Rust did not admit a complete source submission"
                    in message
                    for message in artifact["validation"]["messages"]
                )
            )

    def test_selected_source_texture_palette_rejects_black_source_overlay(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalStaticTerrainScenario"] = "texture-palette"
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            (contract_dir / "selected-source-execution-frame-42.json").write_text(
                json.dumps(
                    {
                        "frame_id": 42,
                        "submission_id": 9,
                        "world_generation": 2,
                        "route": "rust-native-selected-source",
                        "source_material_coverage": {
                            "batches": 1,
                            "quads": 1,
                            "draws": 1,
                            "vertices": 6,
                        },
                    }
                ),
                encoding="utf-8",
            )
            from PIL import Image

            overlay = contract_dir / "selected-source-overlay-frame-42.png"
            (contract_dir / "selected-source-overlay-frame-42.json").write_text(
                json.dumps(
                    {
                        "artifact_class": "rust_selected_source_overlay",
                        "frame_id": 42,
                        "submission_id": 9,
                    }
                ),
                encoding="utf-8",
            )
            Image.new("RGB", (128, 72), (0, 0, 0)).save(overlay)

            artifact = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any(
                    "selected-source texture-palette execution did not retain visible source-world output"
                    in message
                    for message in artifact["validation"]["messages"]
                ),
                artifact["validation"]["messages"],
            )
            evidence = artifact["capture"]["terrain_pass_contract"]["selected_source_overlay_evidence"]
            self.assertEqual("world_output_black", evidence["status"])
            source_execution = artifact["capture"]["terrain_pass_contract"][
                "selected_source_execution_doc"
            ]
            self.assertEqual(
                {"batches": 1, "quads": 1, "draws": 1, "vertices": 6},
                source_execution["source_material_coverage"],
            )

            Image.new("RGB", (128, 72), (48, 96, 32)).save(overlay)
            visible = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any(
                    "selected-source texture-palette execution did not retain visible source-world output"
                    in message
                    for message in visible["validation"]["messages"]
                ),
                visible["validation"]["messages"],
            )
            self.assertEqual(
                "world_output_present",
                visible["capture"]["terrain_pass_contract"]["selected_source_overlay_evidence"]["status"],
            )

    def test_selected_source_stage_capture_correlates_with_dh_only_submission(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            (contract_dir / "selected-source-execution-distant-horizons-frame-42.json").write_text(
                json.dumps(
                    {
                        "frame_id": 42,
                        "submission_id": 9,
                        "world_generation": 2,
                        "route": "rust-native-selected-source",
                        "lod_opaque_instances": 1,
                        "distant_horizons_opaque_program_ready": True,
                    }
                ),
                encoding="utf-8",
            )
            from PIL import Image

            stage = contract_dir / "selected-source-terrain-primary-frame-42.png"
            (contract_dir / "selected-source-terrain-primary-frame-42.json").write_text(
                json.dumps(
                    {
                        "artifact_class": "rust_selected_source_overlay",
                        "frame_id": 42,
                        "submission_id": 9,
                        "source_role": 'ShaderPackColor("primary")',
                    }
                ),
                encoding="utf-8",
            )
            Image.new("RGB", (128, 72), (48, 96, 32)).save(stage)

            artifact = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            evidence = artifact["capture"]["terrain_pass_contract"]["selected_source_overlay_evidence"]
            self.assertEqual("world_output_present", evidence["status"])
            self.assertEqual('ShaderPackColor("primary")', evidence["source_role"])

    def test_selected_source_distant_horizons_requires_its_own_execution_record(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalDistantHorizons.opaqueV1=true\n"
                "rust_gal_world_lod_selected_frames=1\n"
                "rust_gal_world_lod_instances_submitted=4\n"
                "rust_gal_world_lod_frames_executed=1\n",
                encoding="utf-8",
            )
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalDistantHorizonsRoute"] = {
                "decision": "selected",
                "reason": "visible-work",
                "opaqueSegments": 4,
                "transparentSegments": 0,
                "waterSegments": 0,
                "selected": True,
            }
            deterministic.write_text(json.dumps(document), encoding="utf-8")
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            (contract_dir / "terrain-pass-contract-generation-1.json").write_text(
                json.dumps({"selected_source_execution_requested": True}), encoding="utf-8"
            )
            (contract_dir / "selected-source-execution-frame-8.json").write_text(
                json.dumps(
                    {
                        "frame_id": 8,
                        "submission_id": 12,
                        "world_generation": 3,
                        "route": "rust-native-selected-source",
                    }
                ),
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
                tool_kind="capture",
            )

            self.assertFalse(artifact["validation"]["complete"])
            self.assertTrue(
                any(
                    "selected-source Distant Horizons row lacks a successful source submission record"
                    in message
                    for message in artifact["validation"]["messages"]
                )
            )

    def test_distant_horizons_texture_palette_requires_the_screenshot_frame_atlas_plan(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalDistantHorizons.opaqueV1=true\n"
                "-Dmattmc.dev.rustGalDistantHorizons.texturePalette=true\n"
                "rust_gal_world_lod_selected_frames=1\n"
                "rust_gal_world_lod_instances_submitted=4\n"
                "rust_gal_world_lod_frames_executed=1\n",
                encoding="utf-8",
            )
            deterministic = capture / "deterministic_camera_capture_20260101_000000.json"
            document = json.loads(deterministic.read_text(encoding="utf-8"))
            document["rustGalDistantHorizonsRoute"] = {
                "decision": "selected",
                "reason": "visible-work",
                "opaqueSegments": 4,
                "transparentSegments": 0,
                "waterSegments": 0,
                "selected": True,
            }
            deterministic.write_text(json.dumps(document), encoding="utf-8")

            receipt_dir = capture / "deterministic_camera_capture_20260101_000000"
            receipt_dir.mkdir()
            (receipt_dir / "capture_request_01_distant-horizons-texture-palette.ack.json").write_text(
                json.dumps(
                    {
                        # Final-output correlation belongs to the retained
                        # acknowledgement, not the summary capture entry.
                        "wholeFramePresentationCorrelation": {
                            "gameplayFrameId": 42,
                            "submissionId": 9,
                        },
                        "rustGalDistantHorizonsExecution": {
                            "worldFrame": 42,
                            "submission": 9,
                            "captureFrame": 8,
                            "instances": 4,
                            "opaqueInstances": 4,
                            "transparentInstances": 0,
                            "waterInstances": 0,
                            "semanticFrameEnabled": True,
                        },
                        "rustGalDistantHorizonsTextureProbeReceipt": {"matched": True, "status": "ok"},
                    }
                ),
                encoding="utf-8",
            )
            contract_dir = capture / "terrain_pass_contract"
            contract_dir.mkdir()
            plan = {
                "frameId": 42,
                "executedExactSegments": 4,
                "selectedSpriteCounts": {
                    "minecraft:block/lapis_block": 1,
                    "minecraft:block/redstone_ore": 1,
                    "minecraft:block/yellow_terracotta": 1,
                    "minecraft:block/diamond_block": 1,
                },
                "paletteTargetCoverage": [
                    {"position": [88, 80, 552], "matched": True},
                    {"position": [104, 80, 552], "matched": True},
                    {"position": [88, 80, 536], "matched": True},
                    {"position": [104, 80, 536], "matched": True},
                ],
            }
            (contract_dir / "world-lod-exact-atlas-plan-frame-42.json").write_text(
                json.dumps(plan), encoding="utf-8"
            )

            artifact = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any("screenshot-frame-correlated Rust exact-atlas receipt" in message for message in artifact["validation"]["messages"]),
                artifact["validation"]["messages"],
            )
            self.assertFalse(
                any("Distant Horizons screenshot lacks matching successful Rust material-route" in message
                    for message in artifact["validation"]["messages"]),
                artifact["validation"]["messages"],
            )

            (contract_dir / "world-lod-exact-atlas-plan-frame-42.json").unlink()
            (contract_dir / "world-lod-exact-atlas-plan-frame-41.json").write_text(
                json.dumps({**plan, "frameId": 41}), encoding="utf-8"
            )
            stale = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any("screenshot-frame-correlated Rust exact-atlas receipt" in message for message in stale["validation"]["messages"]),
                stale["validation"]["messages"],
            )

            (contract_dir / "world-lod-exact-atlas-plan-frame-41.json").unlink()
            (contract_dir / "world-lod-exact-atlas-plan-frame-42.json").write_text(
                json.dumps({
                    **plan,
                    "paletteTargetCoverage": [
                        {"position": [88, 80, 552], "matched": True},
                        {"position": [104, 80, 552], "matched": False},
                        {"position": [88, 80, 536], "matched": True},
                        {"position": [104, 80, 536], "matched": True},
                    ],
                }),
                encoding="utf-8",
            )
            missing_target = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any("unmatchedTargets" in message for message in missing_target["validation"]["messages"]),
                missing_target["validation"]["messages"],
            )

            # Complementary's real `dh_terrain` program consumes the compact
            # DH color/category stream rather than a Minecraft atlas sampler.
            # The source row must retain the same-frame semantic receipt, but
            # a reduced-color source contract is not itself a rejection.
            (contract_dir / "world-lod-selected-source-material-contract-frame-42.json").write_text(
                json.dumps({
                    "frame_id": 42,
                    "texture_identity_contract": "reduced-color-material-category",
                    "material_atlas_bound": False,
                    "opaque_draw_count": 4,
                    "opaque_index_count": 24,
                    "exact_atlas_draw_count": 0,
                    "exact_atlas_index_count": 0,
                }),
                encoding="utf-8",
            )
            (contract_dir / "world-lod-exact-atlas-plan-frame-42.json").unlink()
            reduced_contract = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            messages = reduced_contract["validation"]["messages"]
            self.assertFalse(
                any("screenshot-frame-correlated Rust exact-atlas receipt" in message for message in messages),
                messages,
            )
            self.assertFalse(
                any("reduced-color source contract lacks" in message for message in messages),
                messages,
            )

            (contract_dir / "world-lod-selected-source-material-contract-frame-42.json").write_text(
                json.dumps({
                    "frame_id": 42,
                    "texture_identity_contract": "reduced-color-material-category",
                    "material_atlas_bound": False,
                    "opaque_draw_count": 4,
                    "opaque_index_count": 24,
                    "exact_atlas_draw_count": 1,
                    "exact_atlas_index_count": 6,
                }),
                encoding="utf-8",
            )
            injected_atlas = harness.normalize_capture_artifact(
                target, mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertTrue(
                any("reduced-color source contract lacks" in message for message in injected_atlas["validation"]["messages"]),
                injected_atlas["validation"]["messages"],
            )

            # Vanilla whole-frame DH still proves the same real selected LOD
            # work and final palette footprint, but it has no selected-source
            # shader attachment to retain. Do not make the shaders-off route
            # fail solely because that shader-only artifact is absent.
            vanilla_mode = next(
                candidate for candidate in harness.MATRIX_MODES
                if candidate.name == "current-rust-vulkan-shaders-off"
            )
            vanilla = harness.normalize_capture_artifact(
                target, vanilla_mode, capture, "correctness", True, ["fake"], 0, False, tool_kind="capture"
            )
            self.assertFalse(
                any("exact source-stage material identity" in message for message in vanilla["validation"]["messages"]),
                vanilla["validation"]["messages"],
            )

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
                selected_hotbar_slot=1,
                hotbar_item_fixture="standard-3d",
            )
            _, env = harness.build_capture_command(target, harness.MATRIX_MODES[0], root / "capture", "correctness", args, "capture")
            self.assertEqual("37", env["MATTMC_DETERMINISTIC_SHUTDOWN_GRACE_SECS"])

    def test_rust_full_gameplay_attachment_diagnostic_is_explicit_and_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(candidate for candidate in harness.MATRIX_MODES if candidate.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "extended",
                    "--mode",
                    mode.name,
                    "--world",
                    "Origin",
                    "--rust-selected-source-execution",
                    "--world-distant-horizons-opaque",
                    "--rust-full-gameplay-attachments",
                    "--diagnostic",
                ]
            )
            _command, env = harness.build_capture_command(
                target, mode, root / "capture", "correctness", args, "capture"
            )
            self.assertEqual("0", env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY"])

    def test_static_terrain_second_world_is_forwarded_to_child_and_java(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "performance",
                    "--mode",
                    "current-rust-vulkan-shaders-on",
                    "--world",
                    "Origin",
                    "--world-static-terrain-scenario",
                    "world-different-reload",
                    "--world-static-terrain-second-world",
                    "SecondTerrainWorld",
                    "--dry-run",
                ]
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            command, env = harness.build_capture_command(
                target,
                mode,
                root / "capture",
                "correctness",
                args,
                "capture",
            )

            self.assertIn("--world-static-terrain-second-world", command)
            self.assertIn("SecondTerrainWorld", command)
            self.assertEqual("SecondTerrainWorld", env["MATTMC_CAPTURE_SECOND_WORLD"])
            self.assertIn(
                "-Dmattmc.dev.rustGalStaticTerrain.worldB=SecondTerrainWorld",
                env["JAVA_TOOL_OPTIONS"],
            )

    def test_static_terrain_translucent_second_world_alias_defaults_are_forwarded(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "performance",
                    "--mode",
                    "current-rust-vulkan-shaders-on",
                    "--world",
                    "Origin",
                    "--world-static-terrain-scenario",
                    "translucent-world-different-reload",
                    "--dry-run",
                ]
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            command, env = harness.build_capture_command(
                target,
                mode,
                root / "capture",
                "correctness",
                args,
                "capture",
            )

            self.assertIn("--world-static-terrain-second-world", command)
            self.assertIn("Origin-different", command)
            self.assertEqual("Origin-different", env["MATTMC_CAPTURE_SECOND_WORLD"])
            self.assertIn(
                "-Dmattmc.dev.rustGalStaticTerrain.worldB=Origin-different",
                env["JAVA_TOOL_OPTIONS"],
            )
            self.assertEqual("true", env["MATTMC_WORLD_STATIC_TERRAIN_NEEDS_SECOND_WORLD"])

    def test_capture_runner_copies_primary_and_secondary_static_terrain_worlds(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_run = root / "source-run"
            source_world = source_run / "saves" / "Origin"
            source_world.mkdir(parents=True)
            (source_run / "options.txt").write_text("enableShaders:false\n", encoding="utf-8")
            (source_world / "level.dat").write_bytes(b"primary world")
            (source_world / "region").mkdir()
            (source_world / "region" / "r.0.0.mca").write_bytes(b"terrain")
            config = isolated_capture_config(root, second_world="SecondTerrainWorld")
            runner = capture_runner.CaptureRunner(config)

            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_RUN_SOURCE": str(source_run),
                    "MATTMC_CAPTURE_WORLD_SOURCE": str(source_world),
                },
                clear=False,
            ):
                runner.prepare_isolated_game_dir()

            saves = runner.run_dir / "saves"
            self.assertTrue((saves / "Origin" / "level.dat").is_file())
            self.assertTrue((saves / "SecondTerrainWorld" / "level.dat").is_file())
            runner.write_initial_meta()
            meta = runner.meta_log.read_text(encoding="utf-8")
            self.assertIn(f"isolated_primary_world_source={source_world}", meta)
            self.assertIn("isolated_primary_world_name=Origin", meta)
            self.assertIn("isolated_primary_world_status=ok", meta)
            self.assertIn("isolated_secondary_world_name=SecondTerrainWorld", meta)
            self.assertIn("isolated_secondary_world_status=ok", meta)
            self.assertIn('isolated_saves_listing=["Origin","SecondTerrainWorld"]', meta)

    def test_capture_runner_hides_mod_owned_minimap_for_vanilla_visual_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_run = root / "source-run"
            source_world = source_run / "saves" / "Origin"
            source_world.mkdir(parents=True)
            (source_run / "options.txt").write_text("enableShaders:false\n", encoding="utf-8")
            (source_world / "level.dat").write_bytes(b"primary world")
            config_dir = source_run / "config"
            config_dir.mkdir()
            (config_dir / "voxelmap.properties").write_text(
                "Hide Minimap:false\nWelcome Message:true\n", encoding="utf-8"
            )
            runner = capture_runner.CaptureRunner(isolated_capture_config(root))

            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_RUN_SOURCE": str(source_run),
                    "MATTMC_CAPTURE_WORLD_SOURCE": str(source_world),
                },
                clear=False,
            ):
                runner.prepare_isolated_game_dir()
            runner.configure_backend_and_validation()

            copied = (runner.run_dir / "config" / "voxelmap.properties").read_text(encoding="utf-8")
            self.assertIn("Hide Minimap:true", copied)
            self.assertIn("Welcome Message:false", copied)

    def test_capture_runner_ids_are_unique_within_one_second(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            first = capture_runner.CaptureRunner(isolated_capture_config(root))
            second = capture_runner.CaptureRunner(isolated_capture_config(root))
            self.assertNotEqual(first.run_id, second.run_id)
            self.assertRegex(first.run_id, r"^\d{8}_\d{6}_\d{6}$")
            self.assertRegex(second.run_id, r"^\d{8}_\d{6}_\d{6}$")

    def test_capture_runner_copies_translucent_alias_secondary_world_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_run = root / "source-run"
            source_world = source_run / "saves" / "Origin"
            source_world.mkdir(parents=True)
            (source_run / "options.txt").write_text("enableShaders:false\n", encoding="utf-8")
            (source_world / "level.dat").write_bytes(b"primary world")
            (source_world / "region").mkdir()
            (source_world / "region" / "r.0.0.mca").write_bytes(b"terrain")
            config = isolated_capture_config(root, scenario="translucent-world-different-reload")
            runner = capture_runner.CaptureRunner(config)

            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_RUN_SOURCE": str(source_run),
                    "MATTMC_CAPTURE_WORLD_SOURCE": str(source_world),
                },
                clear=False,
            ):
                runner.prepare_isolated_game_dir()

            saves = runner.run_dir / "saves"
            self.assertTrue((saves / "Origin" / "level.dat").is_file())
            self.assertTrue((saves / "Origin-different" / "level.dat").is_file())
            runner.write_initial_meta()
            meta = runner.meta_log.read_text(encoding="utf-8")
            self.assertIn("isolated_secondary_world_name=Origin-different", meta)
            self.assertIn("isolated_secondary_world_status=ok", meta)
            self.assertIn('isolated_saves_listing=["Origin","Origin-different"]', meta)

    def test_capture_runner_rejects_missing_secondary_static_terrain_source(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_run = root / "source-run"
            source_world = source_run / "saves" / "Origin"
            source_world.mkdir(parents=True)
            (source_world / "level.dat").write_bytes(b"primary world")
            config = isolated_capture_config(root, second_world="SecondTerrainWorld")
            runner = capture_runner.CaptureRunner(config)

            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_RUN_SOURCE": str(source_run),
                    "MATTMC_CAPTURE_WORLD_SOURCE": str(source_world),
                    "MATTMC_CAPTURE_SECOND_WORLD_SOURCE": str(root / "missing-world"),
                },
                clear=False,
            ):
                with self.assertRaises(SystemExit) as raised:
                    runner.prepare_isolated_game_dir()
            self.assertIn("Cannot copy missing secondary benchmark world", str(raised.exception))

    def test_capture_runner_rejects_static_terrain_world_name_collision(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_run = root / "source-run"
            source_world = source_run / "saves" / "Origin"
            source_world.mkdir(parents=True)
            (source_world / "level.dat").write_bytes(b"primary world")
            config = isolated_capture_config(root, second_world="Origin")
            runner = capture_runner.CaptureRunner(config)

            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_RUN_SOURCE": str(source_run),
                    "MATTMC_CAPTURE_WORLD_SOURCE": str(source_world),
                },
                clear=False,
            ):
                with self.assertRaises(SystemExit) as raised:
                    runner.prepare_isolated_game_dir()
            self.assertIn("must differ from primary world", str(raised.exception))

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
                selected_hotbar_slot=1,
                hotbar_item_fixture="standard-3d",
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            self.assertEqual("true", env["MATTMC_GRAPHICS_CORRECTNESS_CAPTURE"])
            self.assertIn("deterministic_camera_capture_", env["MATTMC_DETERMINISTIC_METADATA"])
            self.assertIn("deterministic_camera_capture_", env["MATTMC_DETERMINISTIC_SCREENSHOT_DIR"])
            java_options = env["JAVA_TOOL_OPTIONS"]
            # The shell launcher receives the capture hook through JVM
            # properties; that Python-runner-only CLI flag is unsupported.
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture=true", shlex.split(java_options))
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.metadata=", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.selectedHotbarSlot=1", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.hotbarItemFixture=standard-3d", java_options)

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

    def test_capture_runner_redirects_root_renderdoc_path_into_ignored_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
            runner.env = {
                "MATTMC_RENDERDOC_CAPTURE": "true",
                "MATTMC_RENDERDOC_CMD": str(root / "renderdoccmd"),
                "MATTMC_RENDERDOC_CAPTURE_PATH": str(root / ".capture-root"),
            }
            runner.root = root
            runner.artifact_dir = root / "artifacts" / "graphics-captures"
            runner.run_id = "test"
            meta_lines: list[str] = []
            runner.append_meta = meta_lines.append  # type: ignore[method-assign]
            wrapped = runner.renderdoc_wrapped_command(["./gradlew", "runClient"])
            template = next(line for line in meta_lines if line.startswith("renderdoc_capture_template="))
            self.assertIn("artifacts/graphics-captures/.capture-root", template)
            self.assertNotIn(str(root) + "/.capture-root", template)
            self.assertEqual(str(root / "renderdoccmd"), wrapped[0])

    def test_capture_runner_redirects_root_renderdoc_template_into_ignored_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
            runner.env = {
                "MATTMC_RENDERDOC_CAPTURE": "true",
                "MATTMC_RENDERDOC_CMD": str(root / "renderdoccmd"),
                "MATTMC_RENDERDOC_CAPTURE_TEMPLATE": str(root / ".capture-template"),
            }
            runner.root = root
            runner.artifact_dir = root / "artifacts" / "graphics-captures"
            runner.run_id = "test"
            meta_lines: list[str] = []
            runner.append_meta = meta_lines.append  # type: ignore[method-assign]
            runner.renderdoc_wrapped_command(["./gradlew", "runClient"])
            template = next(line for line in meta_lines if line.startswith("renderdoc_capture_template="))
            self.assertIn("artifacts/graphics-captures/.capture-template", template)
            self.assertNotIn(str(root) + "/.capture-template", template)

    def test_capture_runner_redirects_external_renderdoc_template_into_ignored_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
            runner.env = {
                "MATTMC_RENDERDOC_CAPTURE": "true",
                "MATTMC_RENDERDOC_CMD": str(root / "renderdoccmd"),
                "MATTMC_RENDERDOC_CAPTURE_TEMPLATE": "/tmp/outside-mattmc.capture",
            }
            runner.root = root
            runner.artifact_dir = root / "artifacts" / "graphics-captures"
            runner.run_id = "test"
            meta_lines: list[str] = []
            runner.append_meta = meta_lines.append  # type: ignore[method-assign]
            runner.renderdoc_wrapped_command(["./gradlew", "runClient"])
            template = next(line for line in meta_lines if line.startswith("renderdoc_capture_template="))
            self.assertIn("artifacts/graphics-captures/outside-mattmc.capture", template)
            self.assertNotIn("/tmp/outside-mattmc.capture", template)

    def test_capture_runner_source_probe_forces_fresh_gradle_environment(self) -> None:
        runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
        runner.env = {
            "MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE": "shadow-coordinate",
            "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE": "distant-horizons-fog-inputs",
        }
        meta_lines: list[str] = []
        runner.append_meta = meta_lines.append  # type: ignore[method-assign]

        command = runner.fresh_environment_launch_command(["./gradlew", "runClient"])

        self.assertEqual(command, ["./gradlew", "--no-daemon", "runClient"])
        self.assertIn("native_diagnostic_forced_gradle_no_daemon=true", meta_lines)
        self.assertIn(
            "native_diagnostic_inputs=MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE,MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
            meta_lines,
        )

        runner.env = {
            "MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_SECTION": "4",
            "MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_MESH_KEY": "8f8b94fe68f34da8",
        }
        meta_lines.clear()
        self.assertEqual(
            runner.fresh_environment_launch_command(["./gradlew", "runClient"]),
            ["./gradlew", "--no-daemon", "runClient"],
        )
        self.assertIn("native_diagnostic_forced_gradle_no_daemon=true", meta_lines)
        self.assertIn(
            "native_diagnostic_inputs=MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_MESH_KEY,MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_SECTION",
            meta_lines,
        )

        runner.env = {}
        self.assertEqual(
            runner.fresh_environment_launch_command(["./gradlew", "runClient"]),
            ["./gradlew", "runClient"],
        )

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

    def test_cross_repository_comparison_ignores_backend_validation_instrumentation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = harness.MATRIX_MODES[0]
            frozen = gameplay_artifact_for(temp, mode)
            current = gameplay_artifact_for(temp, mode)
            current["benchmark_fingerprint"]["instrumentation"]["run_type"] = "vulkan-validation"
            current["benchmark_fingerprint"]["instrumentation"]["validation"] = {
                "mode": "standard",
                "vk_instance_layers": "VK_LAYER_KHRONOS_validation",
            }
            comparison = harness.compare_workloads(frozen, current, cross_repository=True)
            self.assertTrue(comparison["comparable"], comparison)

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
        rust_vulkan = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
        frozen_shaders = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
        self.assertIn("profile-not-supported", harness.profile_not_supported_reason("smoke", rust_vulkan, "capture") or "")
        self.assertIn("profile-not-supported", harness.profile_not_supported_reason("smoke", frozen_shaders, "subsystem") or "")

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

    def test_matrix_mode_frame_counts_preserve_requested_defaults(self) -> None:
        args = harness.parse_args(["gameplay", "--profile", "standard", "--dry-run"])
        opengl = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-opengl-shaders-off")
        self.assertEqual(args.measure_frames, harness.mode_frame_count(args.measure_frames, opengl, args, "--measure-frames"))

        explicit = harness.parse_args(["gameplay", "--profile", "standard", "--warmup-frames", "77", "--measure-frames", "88", "--dry-run"])
        self.assertEqual(77, harness.mode_frame_count(explicit.warmup_frames, opengl, explicit, "--warmup-frames"))
        self.assertEqual(88, harness.mode_frame_count(explicit.measure_frames, opengl, explicit, "--measure-frames"))

    def test_model_fixture_matrix_omits_inapplicable_timing_row(self) -> None:
        ordinary = harness.parse_args(["matrix", "--profile", "standard", "--dry-run"])
        self.assertEqual(("gameplay", "capture", "subsystem"), harness.matrix_tool_kinds(ordinary))

        model = harness.parse_args([
            "matrix", "--profile", "standard", "--world-mesh-model-scenario", "chest", "--dry-run",
        ])
        self.assertEqual(("capture", "subsystem"), harness.matrix_tool_kinds(model))

        selected = harness.parse_args([
            "matrix", "--profile", "standard", "--rust-selected-source-execution", "--dry-run",
        ])
        self.assertEqual(("capture", "subsystem"), harness.matrix_tool_kinds(selected))

    def test_canonical_fixture_model_scenario_is_recovered_for_baseline_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / ".canonical-fixtures" / "fixture" / "fixture_manifest.json"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(json.dumps({"model_scenario": "chest"}), encoding="utf-8")
            capture_dir = root / "frozen" / "capture"
            capture_dir.mkdir(parents=True)
            self.assertEqual("chest", harness.canonical_fixture_model_scenario(capture_dir))

    def test_disabled_ordinary_dh_lifecycle_logs_do_not_become_generating_state(self) -> None:
        state = harness.dh_state_from_text(
            "DistantHorizons Batch Chunk Generator initialized\nDH-World Gen Thread[0]",
            {
                "forced_dh_enableDistantGeneration": "false reason=ordinary-selected-source",
                "forced_dh_ordinary_enableRendering": "false",
            },
        )
        self.assertFalse(state["generating"])
        self.assertEqual("logged", state["state"])

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

    def test_artifact_retention_does_not_treat_invocation_reports_as_runs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            failed = root / "current-rust-vulkan-shaders-off"
            write_retention_manifest(failed, success=False)
            report = root / "paired_visual_static_terrain"
            report.mkdir()
            (report / "visual_parity_report.json").write_text("{}\n", encoding="utf-8")

            runs = artifact_retention.discover_runs(root)
            self.assertEqual([failed], [run.path for run in runs])
            artifact_retention.cleanup(
                artifact_retention.policy_for("standard", root, keep_failed=1, global_limit_mb=0)
            )
            self.assertTrue(failed.exists())
            self.assertTrue(report.exists())

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

    def test_parent_preserve_marker_protects_nested_capture_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            invocation = root / "capture" / "20260101-000000"
            nested = invocation / "current-rust-vulkan" / "capture" / "run-01"
            nested.mkdir(parents=True)
            write_retention_manifest(nested, success=False)
            (invocation / ".preserve").write_text("keep\n", encoding="utf-8")

            result = artifact_retention.cleanup(
                artifact_retention.policy_for("smoke", root, keep_failed=0, global_limit_mb=0)
            )

            self.assertEqual([], result["removed"])
            self.assertTrue(nested.exists())

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

    def test_artifact_retention_global_quota_excludes_canonical_fixture_workspace(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "artifacts"
            artifact_retention.ensure_marker(root)
            fixture = root / ".canonical-fixtures" / "fixture" / "run"
            fixture.mkdir(parents=True)
            (fixture / "copied-world.bin").write_bytes(b"x" * 2048)
            (root / "retained-artifact.json").write_text("{}\n", encoding="utf-8")
            policy = artifact_retention.RetentionPolicy(
                "smoke",
                root.resolve(),
                global_limit_bytes=1024,
                run_limit_bytes=0,
                reserve_bytes=0,
                keep_success=1,
                keep_failed=1,
                heavy_keep=0,
            )
            preflight = artifact_retention.preflight_disk_budget(policy, estimated_bytes=512)
            self.assertLess(preflight["artifact_root_usage_bytes"], 1024)

    def test_artifact_retention_keeps_static_terrain_parity_sidecars_readable(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            sidecar = Path(temp_dir) / "static_terrain_parity_diagnostics.jsonl"
            sidecar.write_text("{}\n", encoding="utf-8")
            self.assertFalse(artifact_retention._should_compress(sidecar, threshold_bytes=1))

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

    def test_artifact_retention_ignores_oversized_incomplete_json(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "in-progress.json"
            path.write_text("{\"success\":false," + ("x" * (8 * 1024 * 1024)) + "\n", encoding="utf-8")
            self.assertEqual({}, artifact_retention._read_json(path))

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
        # Generated capture bundles are intentionally outside the source
        # audit. They can preserve diagnostics from older layouts and are
        # retained only under the ignored artifacts tree.
        skipped_dirs = {".git", ".gradle", "build", "run", "logs", "artifacts", "__pycache__", ".idea"}
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

    def test_cross_repository_parity_report_rejects_mismatched_pair(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
            current_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            frozen = artifact_for(root / "frozen", frozen_mode, world="Origin")
            current = artifact_for(root / "current", current_mode, world="OtherWorld")
            frozen_path = root / "frozen.json"
            current_path = root / "current.json"
            harness.write_artifact(frozen_path, frozen)
            harness.write_artifact(current_path, current)
            report = harness.cross_repository_parity_report([frozen_path, current_path])
            self.assertEqual(report["pair_count"], 1)
            self.assertFalse(report["passed"])
            pair = report["pairs"][0]
            self.assertFalse(pair["comparable"])
            self.assertTrue(pair["comparison"]["differences"])

    def test_cross_repository_parity_report_does_not_cross_pair_tool_contracts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
            current_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            frozen = artifact_for(root / "frozen", frozen_mode)
            current = gameplay_artifact_for(root / "current", current_mode)
            frozen_path = root / "frozen.json"
            current_path = root / "current.json"
            harness.write_artifact(frozen_path, frozen)
            harness.write_artifact(current_path, current)
            report = harness.cross_repository_parity_report([frozen_path, current_path])
            self.assertEqual(report["pair_count"], 0)
            self.assertTrue(report["passed"], report)

    def test_cross_repository_parity_report_rejects_missing_pair_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
            current_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            frozen = artifact_for(root / "frozen", frozen_mode)
            current = artifact_for(root / "current", current_mode)
            for artifact in (frozen, current):
                signature = artifact["benchmark_fingerprint"]["workload_signature"]
                signature["camera"] = {"status": "unavailable", "poses": []}
                signature["world_save_state"] = {"hash": None}
                signature["shaderpack"] = {"name": "unset", "sha256": None}
            frozen_path = root / "frozen.json"
            current_path = root / "current.json"
            harness.write_artifact(frozen_path, frozen)
            harness.write_artifact(current_path, current)
            report = harness.cross_repository_parity_report([frozen_path, current_path])
            self.assertFalse(report["passed"])
            self.assertIn("baseline_camera_evidence_missing", report["pairs"][0]["evidence_failures"])
            self.assertIn("current_shaderpack_evidence_missing", report["pairs"][0]["evidence_failures"])

    def test_cross_repository_parity_report_rejects_camera_contract_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
            current_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            frozen = artifact_for(root / "frozen", frozen_mode, parity_yaw=150.0)
            current = artifact_for(root / "current", current_mode, parity_yaw=105.0)
            frozen_path = root / "frozen.json"
            current_path = root / "current.json"
            harness.write_artifact(frozen_path, frozen)
            harness.write_artifact(current_path, current)
            report = harness.cross_repository_parity_report([frozen_path, current_path])
            self.assertFalse(report["passed"])
            self.assertTrue(
                any(
                    diff["path"] == "parity_config.camera.yaw"
                    for diff in report["pairs"][0]["comparison"]["differences"]
                )
            )

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
            self.assertEqual(targets["current"].role, "current-under-test")
            self.assertEqual(targets["frozen"].role, "frozen-baseline")

    def test_explicit_repo_root_selects_current_under_test(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            harness_repo = fake_repo(root, "harness")
            selected_current = fake_repo(root, "selected-current")
            frozen = fake_repo(root, "frozen")
            args = Namespace(repo_root=selected_current.root, frozen_repo=str(frozen.root))
            original_repo_root = harness.repo_root
            try:
                harness.repo_root = lambda start=None: harness_repo.root  # type: ignore[assignment]
                targets = harness.select_targets(args)
            finally:
                harness.repo_root = original_repo_root  # type: ignore[assignment]
            self.assertEqual(targets["current"].root, selected_current.root)
            self.assertEqual(targets["frozen"].root, frozen.root)

    def test_capture_command_uses_target_repo_run_source(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            current = fake_repo(root, "current")
            frozen = fake_repo(root, "frozen")
            python_runner = frozen.root / "DevUtils" / "Common" / "capture_runner.py"
            python_runner.unlink()
            shell_runner = frozen.root / "DevUtils" / "Common" / "capture_runner.sh"
            shell_runner.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            shell_runner.chmod(0o755)
            args = harness.parse_args(
                [
                    "capture",
                    "--repo-root",
                    str(current.root),
                    "--frozen-repo",
                    str(frozen.root),
                    "--mode",
                    "frozen-opengl-shaders-off",
                    "--world",
                    "Origin",
                    "--dry-run",
                ]
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            command, env = harness.build_capture_command(
                frozen,
                mode,
                root / "capture",
                "correctness",
                args,
                "capture",
            )
            self.assertEqual(env["MATTMC_CAPTURE_RUN_SOURCE"], str(frozen.root / "run"))
            self.assertEqual(env["MATTMC_CAPTURE_WORLD_SOURCE"], str(frozen.root / "run" / "saves" / "Origin"))
            self.assertEqual(env["MATTMC_GRAPHICS_CORRECTNESS_CAPTURE"], "true")
            self.assertEqual(command[0], "bash")

    def test_canonical_fixture_materializes_from_current_run(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            current = fake_repo(root, "current")
            frozen = fake_repo(root, "frozen")
            source_world = current.root / "run" / "saves" / "Origin" / "region"
            source_world.mkdir(parents=True)
            (source_world / "r.0.0.mca").write_bytes(b"canonical-region")
            (current.root / "run" / "options.txt").parent.mkdir(parents=True, exist_ok=True)
            (current.root / "run" / "options.txt").write_text("renderDistance:10\ngraphicsMode:1\n", encoding="utf-8")
            (current.root / "run" / "config").mkdir(parents=True)
            (current.root / "run" / "config" / "iris.properties").write_text(
                "enableShaders=true\nshaderPack=ComplementaryHungLoIfied.zip\n",
                encoding="utf-8",
            )
            (current.root / "run" / "shaderpacks").mkdir(parents=True)
            (current.root / "run" / "shaderpacks" / harness.EXPECTED_SHADER_PACK).write_bytes(b"current-pack")
            (frozen.root / "run" / "shaderpacks").mkdir(parents=True)
            (frozen.root / "run" / "shaderpacks" / harness.EXPECTED_SHADER_PACK).write_bytes(b"frozen-pack")
            args = Namespace(
                world="Origin",
                world_profile="migration-gate",
                world_static_terrain_scenario="real-world",
                world_static_terrain_resource_pack_scenario="vanilla",
                world_distant_horizons_water=True,
                world_distant_horizons_opaque=False,
                world_distant_horizons_non_water=False,
                world_distant_horizons_texture_palette=False,
                graphics_mode="fabulous",
            )
            run_root = harness.materialize_canonical_fixture(
                args,
                {"current": current, "frozen": frozen},
                root / "artifacts",
            )
            manifest = json.loads(Path(args._canonical_fixture_manifest).read_text(encoding="utf-8"))
            self.assertEqual(run_root, Path(args._canonical_fixture_run_source))
            self.assertTrue((run_root / "saves" / "Origin" / "region" / "r.0.0.mca").is_file())
            self.assertEqual(manifest["source_save_hash"]["hash"], manifest["canonical_save_hash"]["hash"])
            self.assertEqual(manifest["camera"]["yaw"], harness.DEFAULT_PARITY_CAMERA["yaw"])
            self.assertEqual(
                (run_root / "options.txt").read_text(encoding="utf-8").splitlines(),
                ["renderDistance:10", "graphicsMode:2"],
            )
            self.assertEqual(manifest["graphics_mode"], "fabulous")
            self.assertEqual(
                b"frozen-pack",
                (run_root / "shaderpacks" / harness.EXPECTED_SHADER_PACK).read_bytes(),
            )
            fixture_function = run_root / "saves" / "Origin" / "datapacks" / "mattmc_dh_fixture" / "data" / "mattmc" / "function"
            self.assertEqual((fixture_function / "load.mcfunction").read_text(encoding="utf-8"), "schedule function mattmc:fixture 1t replace\n")
            self.assertIn("tellraw @a", (fixture_function / "fixture.mcfunction").read_text(encoding="utf-8"))
            self.assertIn("fill 92 82 536 95 82 539 minecraft:water", (fixture_function / "fixture.mcfunction").read_text(encoding="utf-8"))
            self.assertIn("unless block 77 81 537", (fixture_function / "tick.mcfunction").read_text(encoding="utf-8"))
            self.assertEqual(
                json.loads((run_root / "saves" / "Origin" / "datapacks" / "mattmc_dh_fixture" / "data" / "minecraft" / "tags" / "function" / "tick.json").read_text(encoding="utf-8")),
                {"values": ["mattmc:tick"]},
            )

    def test_current_only_terrain_capture_requests_canonical_fixture(self) -> None:
        args = Namespace(
            world_static_terrain_scenario="texture-palette",
            world_static_terrain_resource_pack_scenario="pack-a",
            world_static_terrain_water_animation_capture=True,
        )
        current_only = [
            mode for mode in harness.MATRIX_MODES
            if mode.name == "current-rust-vulkan-shaders-off"
        ]
        self.assertTrue(harness.canonical_fixture_requested(args, current_only))

        item_entity = Namespace(
            world_static_terrain_scenario="",
            world_static_terrain_resource_pack_scenario="",
            world_static_terrain_water_animation_capture=False,
            world_mesh_model_scenario="",
            world_mesh_falling_block_scenario="",
            world_mesh_arrow_scenario="",
            world_item_entity_scenario="ordinary",
            world_experience_orb_scenario="",
            world_beacon_beam_scenario="",
            world_entity_flame_scenario="",
            world_entity_shadow_scenario="",
            world_text_scenario="",
            world_distant_horizons_opaque=False,
            world_distant_horizons_non_water=False,
            world_distant_horizons_water=False,
            world_distant_horizons_texture_palette=False,
        )
        self.assertTrue(harness.canonical_fixture_requested(item_entity, current_only))

        dh_only = Namespace(
            world_static_terrain_scenario="",
            world_static_terrain_resource_pack_scenario="",
            world_static_terrain_water_animation_capture=False,
            world_distant_horizons_opaque=True,
            world_distant_horizons_non_water=False,
            world_distant_horizons_water=True,
            world_distant_horizons_texture_palette=True,
        )
        self.assertTrue(harness.canonical_fixture_requested(dh_only, current_only))

        weather = Namespace(
            world_static_terrain_scenario="",
            world_static_terrain_resource_pack_scenario="",
            world_static_terrain_water_animation_capture=False,
            world_weather_scenario="rain",
            world_cloud_scenario="",
        )
        self.assertTrue(harness.canonical_fixture_requested(weather, current_only))

        clouds = Namespace(
            world_static_terrain_scenario="",
            world_static_terrain_resource_pack_scenario="",
            world_static_terrain_water_animation_capture=False,
            world_weather_scenario="",
            world_cloud_scenario="bounded",
        )
        self.assertTrue(harness.canonical_fixture_requested(clouds, current_only))

        background = Namespace(
            world_static_terrain_scenario="",
            world_static_terrain_resource_pack_scenario="",
            world_static_terrain_water_animation_capture=False,
            world_weather_scenario="",
            world_cloud_scenario="",
            world_background_scenario="overworld-day",
        )
        self.assertTrue(harness.canonical_fixture_requested(background, current_only))

        ordinary = Namespace(
            world_static_terrain_scenario="",
            world_static_terrain_resource_pack_scenario="",
            world_static_terrain_water_animation_capture=False,
            world_distant_horizons_opaque=False,
            world_distant_horizons_non_water=False,
            world_distant_horizons_water=False,
            world_distant_horizons_texture_palette=False,
        )
        self.assertFalse(harness.canonical_fixture_requested(ordinary, current_only))

    def test_title_only_pair_does_not_clone_a_world_fixture(self) -> None:
        paired_title = Namespace(title_screen_capture=True)
        paired_modes = [
            mode for mode in harness.MATRIX_MODES
            if mode.name in {"frozen-opengl-shaders-off", "current-rust-vulkan-shaders-off"}
        ]
        self.assertFalse(
            harness.canonical_fixture_requested(paired_title, paired_modes),
            "a title presentation is validated by its acknowledged menu frame, not by a copied world/assets tree",
        )

    def test_model_capture_requests_shared_fixture_and_materializes_witness(self) -> None:
        args = Namespace(
            world="Origin",
            world_profile="migration-gate",
            world_static_terrain_scenario="",
            world_static_terrain_resource_pack_scenario="",
            world_mesh_model_scenario="shulker",
            world_distant_horizons_opaque=False,
            world_distant_horizons_non_water=False,
            world_distant_horizons_water=False,
            world_distant_horizons_texture_palette=False,
        )
        current_only = [mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on"]
        self.assertTrue(harness.canonical_fixture_requested(args, current_only))
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            current = fake_repo(root, "current")
            source_world = current.root / "run" / "saves" / "Origin" / "region"
            source_world.mkdir(parents=True)
            (source_world / "r.0.0.mca").write_bytes(b"model-region")
            (current.root / "run" / "options.txt").write_text("graphicsMode:1\n", encoding="utf-8")
            run_root = harness.materialize_canonical_fixture(args, {"current": current}, root / "artifacts")
            datapack = run_root / "saves" / "Origin" / "datapacks" / "mattmc_model_fixture"
            self.assertIn("setblock 146 99 529 minecraft:purple_shulker_box[facing=up]",
                          (datapack / "data" / "mattmc_model_fixture" / "function" / "model_fixture.mcfunction").read_text(encoding="utf-8"))
            self.assertEqual(
                json.loads((datapack / "data" / "minecraft" / "tags" / "function" / "load.json").read_text(encoding="utf-8")),
                {"values": ["mattmc_model_fixture:load"]},
            )
        parity = harness.normalized_parity_config(
            next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on"),
            {
                "world_mesh_model_scenario": "shulker",
                "forced_window_width": "1280",
                "forced_window_height": "720",
                "forced_option_renderDistance": "4",
                "forced_option_simulationDistance": "5",
                "forced_option_guiScale": "3",
                "parity_fixture_id": "fixture-shulker",
                "parity_fixture_source_save_hash": "hash",
                "parity_camera_yaw": "105",
                "parity_camera_pitch": "10",
            },
        )
        self.assertEqual(parity["fixture"]["scenario"], "shulker")

    def test_capture_command_uses_canonical_fixture_run_source_when_present(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            current = fake_repo(root, "current")
            args = harness.parse_args(
                [
                    "capture",
                    "--repo-root",
                    str(current.root),
                    "--mode",
                    "current-rust-vulkan-shaders-on",
                    "--world",
                    "Origin",
                    "--dry-run",
                ]
            )
            canonical_run = root / "canonical" / "run"
            setattr(args, "_canonical_fixture_run_source", str(canonical_run))
            setattr(args, "_canonical_fixture_id", "fixture-a")
            setattr(args, "_canonical_fixture_manifest", str(root / "fixture_manifest.json"))
            setattr(args, "_canonical_fixture_source_save_hash", "abc123")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            _command, env = harness.build_capture_command(
                current,
                mode,
                root / "capture",
                "correctness",
                args,
                "capture",
            )
            self.assertEqual(env["MATTMC_CAPTURE_RUN_SOURCE"], str(canonical_run))
            self.assertEqual(env["MATTMC_CAPTURE_WORLD_SOURCE"], str(canonical_run / "saves" / "Origin"))
            self.assertEqual(env["MATTMC_PARITY_CAMERA_YAW"], str(harness.DEFAULT_PARITY_CAMERA["yaw"]))

    def test_cloud_fixtures_use_a_cloud_layer_canonical_camera(self) -> None:
        for scenario in ("bounded", "fast"):
            with self.subTest(scenario=scenario):
                camera = harness.canonical_camera_options(Namespace(world_cloud_scenario=scenario))

                self.assertEqual(camera["x"], harness.DEFAULT_PARITY_CAMERA["x"])
                self.assertEqual(camera["y"], 192.0)
                self.assertEqual(camera["z"], harness.DEFAULT_PARITY_CAMERA["z"])
                self.assertEqual(camera["yaw"], harness.DEFAULT_PARITY_CAMERA["yaw"])
                self.assertEqual(camera["pitch"], 0.0)
                self.assertEqual(camera["pose_sequence"], "cloud-layer-static-v1")

    def test_ordinary_canonical_capture_explicitly_uses_its_static_camera_schedule(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            current = fake_repo(root, "current")
            frozen = fake_repo(root, "frozen")
            args = harness.parse_args(
                [
                    "capture",
                    "--repo-root",
                    str(current.root),
                    "--profile",
                    "standard",
                    "--world",
                    "Origin",
                    "--workload-profile",
                    "correctness",
                    "--dry-run",
                ]
            )
            canonical_run = root / "canonical" / "run"
            setattr(args, "_canonical_fixture_run_source", str(canonical_run))
            for target, mode_name in (
                (current, "current-rust-vulkan-shaders-off"),
                (frozen, "frozen-opengl-shaders-off"),
            ):
                mode = next(mode for mode in harness.MATRIX_MODES if mode.name == mode_name)
                _command, env = harness.build_capture_command(
                    target, mode, root / mode_name, "correctness", args, "capture"
                )
                options = shlex.split(env["JAVA_TOOL_OPTIONS"])
                self.assertEqual(
                    "-Dmattmc.dev.deterministicCameraCapture.poseCount=1", options[-2]
                )
                self.assertEqual(
                    "-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0", options[-1]
                )
                self.assertEqual("true", env["MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE"])
                self.assertIn("-Dmattmc.dev.rustGalDistantHorizons.disabled=true", options)
                self.assertIn("-Dmattmc.vulkan.deterministicTemporalParity=true", options)
                self.assertIn("-Dmattmc.vulkan.deterministicLightmapParity=true", options)

    def test_implementation_attribution(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
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
                tool="capture",
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

    def test_capture_frame_benchmark_keeps_both_capture_and_sampler_contracts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = harness.parse_args(
                [
                    "capture",
                    "--mode",
                    "current-rust-vulkan-shaders-off",
                    "--capture-frame-benchmark",
                    "--settle-frames",
                    "3",
                    "--warmup-frames",
                    "4",
                    "--measure-frames",
                    "5",
                    "--dry-run",
                ]
            )
            command, env = harness.build_capture_command(
                target,
                harness.MATRIX_MODES[4],
                root / "capture",
                "correctness",
                args,
                "capture",
            )
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("--deterministic-camera-capture", command)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark=true", java_options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.settleFrames=3", java_options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.warmupFrames=4", java_options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.measureFrames=5", java_options)
            self.assertTrue(harness.frame_benchmark_requested(args, "capture"))
            self.assertEqual(
                str(root / "capture" / next(part.split("=", 1)[1] for part in java_options.split() if part.startswith("-Dmattmc.dev.graphicsFrameBenchmark.status="))),
                env["MATTMC_GRAPHICS_FRAME_BENCHMARK_STATUS"],
            )
            self.assertIn("capture_runner.py", command[1])

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

    def test_rust_opengl_capture_uses_explicit_fixed_warmup_not_vulkan_readiness_families(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-opengl-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "gameplay",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "gameplay", args, "capture")
            java_options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=none", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=180", java_options)
            self.assertEqual("true", env["MATTMC_CAPTURE_KILL_AFTER_DETERMINISTIC"])

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

    def test_rust_vulkan_gameplay_measurement_honors_requested_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = Namespace(
                profile="standard",
                validation="standard",
                validation_fail_severity="warning",
                client_args="",
                jvm_arg=[],
                world="Origin",
                world_profile="migration-gate",
                max_secs=160,
                dump_secs=45,
                client_rss_limit_mb=12288,
                diagnostic=False,
                warmup_frames=120,
                measure_frames=300,
                settle_frames=0,
                max_settle_frames=120,
                readiness_timeout_seconds=15,
                shutdown_timeout_seconds=10,
                cleanup_timeout_seconds=10,
                subsystem_iterations=120,
                tracy_capture=False,
                tracy_duration_seconds=20,
                tracy_max_size_mb=256,
                renderdoc_capture=False,
                renderdoc_frame=8,
                gui_resource_pack_scenario="priority-a-b",
                world_static_terrain_resource_pack_scenario="priority-a-b",
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")

            gameplay_command, _ = harness.build_capture_command(target, mode, root / "gameplay", "gameplay", args, "gameplay")
            capture_command, _ = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")

            self.assertEqual("standard", gameplay_command[gameplay_command.index("--validation") + 1])
            self.assertEqual("standard", capture_command[capture_command.index("--validation") + 1])
            self.assertIn(
                "-Dmattmc.dev.graphicsFrameBenchmark.readinessTimeoutSeconds=30",
                harness.build_capture_command(target, mode, root / "pack-gameplay", "gameplay", args, "gameplay")[1]["JAVA_TOOL_OPTIONS"],
            )

            args.world_distant_horizons_opaque = True
            dh_env = harness.build_capture_command(
                target, mode, root / "dh-gameplay", "gameplay", args, "gameplay"
            )[1]
            self.assertIn(
                "-Dmattmc.dev.graphicsFrameBenchmark.readinessTimeoutSeconds=120",
                dh_env["JAVA_TOOL_OPTIONS"],
            )
            self.assertIn("-Dmattmc.dev.graphicsAuditSliceMetrics=true", dh_env["JAVA_TOOL_OPTIONS"])

            args.validation = "off"
            clean_command, clean_env = harness.build_capture_command(
                target, mode, root / "clean-gameplay", "gameplay", args, "gameplay"
            )
            self.assertEqual("off", clean_command[clean_command.index("--validation") + 1])
            self.assertNotIn("VK_INSTANCE_LAYERS", clean_env)

    def test_renderdoc_preflight_layer_does_not_enable_khronos_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = Namespace(
                validation="off",
                validation_fail_severity="warning",
                renderdoc_capture=True,
                renderdoc_frame=8,
                tracy_capture=False,
                tracy_duration_seconds=20,
                tracy_max_size_mb=256,
                world="Origin",
                world_profile="migration-gate",
            )
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            meta = harness.write_preflight_meta(
                root,
                mode,
                args,
                {"VK_INSTANCE_LAYERS": "VK_LAYER_RENDERDOC_Capture"},
            ).read_text(encoding="utf-8")

            self.assertIn("validation_mode=off", meta)
            self.assertIn("validation_enabled=false", meta)

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
                world_static_terrain_scenario="",
                world_static_terrain_second_world="",
                world_static_terrain_fault="",
                world_static_terrain_resource_pack_scenario="",
                world_static_terrain_water_animation_capture=False,
                region_validation=False,
                region_validation_copy_world=False,
                poi_validation=False,
            )
            runner = capture_runner.CaptureRunner(config)
            runner.configure_backend_and_validation()
            runner.configure_java_tool_options()
            options = (game_dir / "options.txt").read_text(encoding="utf-8")
            self.assertIn("graphics_backend=vulkan", options)
            self.assertIn('panoramaTheme:"aquatic"', options)
            self.assertIn("-Dmattmc.dev.rustGalVulkanWholeFrame=true", runner.env["JAVA_TOOL_OPTIONS"])

    def test_title_transition_does_not_complete_before_the_title_receipt(self) -> None:
        runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
        runner.config = type("Config", (), {
            "title_screen_capture": True,
            "title_screen_transition_capture": True,
            "screenshot_max_count": 8,
        })()
        runner.screenshot_count = 8
        runner.title_screen_receipt_observed = False
        self.assertFalse(runner.title_screen_timeline_complete())

        runner.title_screen_receipt_observed = True
        self.assertTrue(runner.title_screen_timeline_complete())

    def test_title_transition_rejects_nonclient_desktop_screenshots(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
            runner.config = type("Config", (), {
                "title_screen_transition_capture": True,
                "screenshot_max_count": 2,
            })()
            runner.screenshot_enabled = True
            runner.screenshot_count = 0
            runner.run_id = "test"
            runner.artifact_dir = Path(temp)
            runner.platform_name = "linux"
            runner.append_meta = lambda _value: None
            with mock.patch.object(capture_runner, "capture_screenshot", return_value=None) as capture:
                runner.capture_root_screenshot("tick", 1, 42)
            self.assertEqual(
                mock.call("linux", mock.ANY, 42, require_client_window=True),
                capture.call_args,
            )
            self.assertEqual(0, runner.screenshot_count)
            self.assertEqual([], list(Path(temp).glob("*.png")))

    def test_title_transition_timeline_never_claims_renderer_pixel_attribution(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
            runner.config = type("Config", (), {
                "title_screen_transition_capture": True,
                "screenshot_max_count": 2,
            })()
            runner.screenshot_enabled = True
            runner.screenshot_count = 0
            runner.run_id = "test"
            runner.artifact_dir = Path(temp)
            runner.platform_name = "linux"
            runner.append_meta = lambda _value: None
            def capture(_platform: str, destination: Path, _pid: int, **_kwargs: object) -> str:
                destination.write_bytes(b"png")
                return "0x123"
            with mock.patch.object(capture_runner, "capture_screenshot", side_effect=capture), mock.patch.object(
                capture_runner, "window_capture_provenance", return_value={"status": "verified"}
            ):
                runner.capture_root_screenshot("tick", 1, 42)
            sidecar = next(Path(temp).glob("*.provenance.json"))
            evidence = json.loads(sidecar.read_text(encoding="utf-8"))
            self.assertEqual("x11-identity-only", evidence["status"])
            self.assertEqual("unverified-without-renderer-frame-correlation", evidence["pixelAttribution"])

    def test_strict_linux_capture_requires_a_known_client_window(self) -> None:
        with tempfile.TemporaryDirectory() as temp, mock.patch.object(
            capture_runner, "find_linux_client_window_id", return_value=None
        ), mock.patch.object(capture_runner.subprocess, "run") as run:
            result = capture_runner.capture_screenshot(
                "linux", Path(temp) / "frame.png", None, require_client_window=True
            )
        self.assertIsNone(result)
        run.assert_not_called()

    def test_linux_window_capture_uses_direct_xwd_not_imagemagick_window_import(self) -> None:
        with tempfile.TemporaryDirectory() as temp, mock.patch.object(
            capture_runner.shutil, "which", side_effect=lambda name: f"/{name}"
        ), mock.patch.object(capture_runner.subprocess, "run") as run:
            run.return_value = type("Result", (), {"returncode": 0})()
            raw = Path(temp) / "frame.png.xwd"
            raw.write_bytes(b"xwd")
            screenshot = Path(temp) / "frame.png"
            screenshot.write_bytes(b"png")
            self.assertTrue(capture_runner.capture_linux_x11_window("0x123", screenshot))
        commands = [call.args[0] for call in run.call_args_list]
        self.assertEqual(["xwd", "-silent", "-id", "0x123", "-out", str(raw)], commands[0])
        self.assertEqual(["magick", str(raw), str(screenshot)], commands[1])
        self.assertFalse(raw.exists())

    def test_linux_window_capture_rejects_an_unmapped_client_window(self) -> None:
        with tempfile.TemporaryDirectory() as temp, mock.patch.object(
            capture_runner, "find_linux_client_window_id", return_value="0x123"
        ), mock.patch.object(capture_runner, "linux_x11_window_is_viewable", return_value=False), mock.patch.object(
            capture_runner.subprocess, "run"
        ) as run:
            result = capture_runner.capture_screenshot(
                "linux", Path(temp) / "frame.png", 42, require_client_window=True
            )
        self.assertIsNone(result)
        run.assert_not_called()

    def test_linux_transition_window_lookup_never_falls_back_when_client_pid_is_known(self) -> None:
        # An unrelated desktop window can contain a Minecraft project title.
        # Startup-transition evidence must be rejected unless X11 assigns it
        # to the launched client PID.
        with mock.patch.object(capture_runner.shutil, "which", return_value="xprop"), mock.patch.object(
            capture_runner,
            "command_text",
            side_effect=[
                "_NET_CLIENT_LIST_STACKING(WINDOW): window id # 0x1, 0x2",
                '_NET_WM_PID(CARDINAL) = 999\nWM_NAME(STRING) = "Minecraft source notes"',
                '_NET_WM_PID(CARDINAL) = 42\nWM_NAME(STRING) = "Minecraft"',
            ],
        ):
            self.assertEqual("0x1", capture_runner.find_linux_client_window_id(42))

        with mock.patch.object(capture_runner.shutil, "which", return_value="xprop"), mock.patch.object(
            capture_runner,
            "command_text",
            side_effect=[
                "_NET_CLIENT_LIST_STACKING(WINDOW): window id # 0x1",
                '_NET_WM_PID(CARDINAL) = 999\nWM_NAME(STRING) = "Minecraft source notes"',
            ],
        ):
            self.assertIsNone(capture_runner.find_linux_client_window_id(42))

    def test_window_capture_provenance_requires_the_exact_client_pid(self) -> None:
        with mock.patch.object(capture_runner.shutil, "which", return_value="xprop"), mock.patch.object(
            capture_runner, "command_text", return_value='_NET_WM_PID(CARDINAL) = 42\nWM_NAME(STRING) = "Minecraft"'
        ), mock.patch.object(capture_runner, "linux_x11_window_is_viewable", return_value=True
        ):
            evidence = capture_runner.window_capture_provenance("linux", "0x1", 42)
        self.assertEqual("verified", evidence["status"])
        self.assertEqual(42, evidence["observedWindowPid"])

        with mock.patch.object(capture_runner.shutil, "which", return_value="xprop"), mock.patch.object(
            capture_runner, "command_text", return_value='_NET_WM_PID(CARDINAL) = 99\nWM_NAME(STRING) = "other"'
        ), mock.patch.object(capture_runner, "linux_x11_window_is_viewable", return_value=True
        ):
            evidence = capture_runner.window_capture_provenance("linux", "0x1", 42)
        self.assertEqual("unverified", evidence["status"])

    def test_rust_title_presented_frame_rejects_root_desktop_capture(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            runner = capture_runner.CaptureRunner.__new__(capture_runner.CaptureRunner)
            runner.config = type("Config", (), {
                "title_screen_capture": True,
                "backend": "rust-vulkan",
            })()
            runner.title_presented_frame_dir = Path(temp)
            runner.platform_name = "linux"
            runner.append_meta = lambda _value: None
            request = runner.title_presented_frame_dir / "title_frame_capture.json"
            request.write_text(json.dumps({"screenshot": str(Path(temp) / "title.png")}), encoding="utf-8")
            with mock.patch.object(capture_runner, "capture_screenshot", return_value=None) as capture:
                self.assertFalse(runner.capture_rust_title_presented_frame(42))
            self.assertEqual(
                mock.call("linux", mock.ANY, 42, require_client_window=True),
                capture.call_args,
            )
            self.assertFalse((runner.title_presented_frame_dir / "title_frame_capture.ack.json").exists())

    def test_capture_runner_dh_semantic_route_keeps_default_renderer_mode(self) -> None:
        """Explicit DH semantic traversal must not self-disable through rendererMode."""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game_dir = root / "game"
            (game_dir / "config").mkdir(parents=True)
            (game_dir / "options.txt").write_text("graphics_backend=vulkan\n", encoding="utf-8")
            (game_dir / "config" / "DistantHorizons.toml").write_text("# fixture\n", encoding="utf-8")
            config = isolated_capture_config(root, scenario="real-world")
            config.game_dir = str(game_dir)
            runner = capture_runner.CaptureRunner(config)
            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_DH_RUST_OPAQUE_ONLY": "true",
                    "MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE": "false",
                },
                clear=False,
            ):
                runner.configure_backend_and_validation()
                runner.configure_java_tool_options()
            dh_file = game_dir / "config" / "DistantHorizons.toml"
            dh_text = dh_file.read_text(encoding="utf-8")
            self.assertIn("enableRendering = false", dh_text)
            self.assertNotIn("rendererMode", dh_text)

    def test_capture_runner_isolated_vanilla_route_disables_dh_rendering_for_both_repositories(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game_dir = root / "game"
            (game_dir / "config").mkdir(parents=True)
            (game_dir / "options.txt").write_text("graphics_backend=vulkan\n", encoding="utf-8")
            dh_file = game_dir / "config" / "DistantHorizons.toml"
            dh_file.write_text(
                "enableRendering = true\nenableDistantGeneration = true\n"
                "overrideVanillaGraphicsSettings = true\n"
                "enableVanillaFog = false\nenableDhFog = true\n",
                encoding="utf-8",
            )
            config = isolated_capture_config(root, scenario="")
            config.game_dir = str(game_dir)
            runner = capture_runner.CaptureRunner(config)
            with mock.patch.dict(
                os.environ,
                {
                    "MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE": "true",
                    "MATTMC_CAPTURE_DISABLE_DH_FOR_PERF": "false",
                },
                clear=False,
            ):
                runner.configure_backend_and_validation()
            dh_text = dh_file.read_text(encoding="utf-8")
            self.assertIn("enableRendering = false", dh_text)
            self.assertIn("enableDistantGeneration = false", dh_text)
            self.assertIn("overrideVanillaGraphicsSettings = false", dh_text)
            self.assertIn("enableVanillaFog = true", dh_text)
            self.assertIn("enableDhFog = false", dh_text)

    def test_capture_runner_preserves_moving_mesh_pose_sequence_with_static_terrain_diagnostics(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game_dir = root / "game"
            (game_dir / "config").mkdir(parents=True)
            (game_dir / "options.txt").write_text("graphics_backend=vulkan\n", encoding="utf-8")
            (game_dir / "config" / "iris.properties").write_text(
                "shaderPack=ComplementaryHungLoIfied\n", encoding="utf-8"
            )
            config = isolated_capture_config(root, scenario="real-world")
            config.game_dir = str(game_dir)
            config.deterministic_static_camera_capture = True
            config.client_args = "enableShaders=true"
            runner = capture_runner.CaptureRunner(config)
            runner.env["JAVA_TOOL_OPTIONS"] = " ".join(
                [
                    "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand",
                    "-Dmattmc.dev.deterministicCameraCapture.poseCount=5",
                    "-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2",
                ]
            )

            runner.configure_client_args()

            options = shlex.split(runner.env["JAVA_TOOL_OPTIONS"])
            self.assertEqual(1, options.count("-Dmattmc.dev.deterministicCameraCapture.poseCount=5"))
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", options)

    def test_capture_runner_preserves_model_part_pose_sequence_with_static_terrain_diagnostics(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game_dir = root / "game"
            (game_dir / "config").mkdir(parents=True)
            (game_dir / "options.txt").write_text("graphics_backend=vulkan\n", encoding="utf-8")
            (game_dir / "config" / "iris.properties").write_text(
                "shaderPack=ComplementaryHungLoIfied\n", encoding="utf-8"
            )
            config = isolated_capture_config(root, scenario="real-world")
            config.game_dir = str(game_dir)
            config.deterministic_static_camera_capture = True
            config.client_args = "enableShaders=true"
            runner = capture_runner.CaptureRunner(config)
            runner.env["JAVA_TOOL_OPTIONS"] = " ".join(
                [
                    "-Dmattmc.dev.rustGalWorldMesh.modelScenario=decorated-pot",
                    "-Dmattmc.dev.deterministicCameraCapture.poseCount=5",
                    "-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2",
                ]
            )

            runner.configure_client_args()

            options = shlex.split(runner.env["JAVA_TOOL_OPTIONS"])
            self.assertEqual(1, options.count("-Dmattmc.dev.deterministicCameraCapture.poseCount=5"))
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", options)

    def test_deterministic_capture_freezes_temporal_inputs_without_shader_trace(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game_dir = root / "game"
            game_dir.mkdir()
            (game_dir / "options.txt").write_text("graphics_backend=opengl\n", encoding="utf-8")
            config = capture_runner.CaptureConfig(
                backend="opengl",
                shaders="on",
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
                deterministic_camera_capture=True,
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
                world_static_terrain_scenario="",
                world_static_terrain_second_world="",
                world_static_terrain_fault="",
                world_static_terrain_resource_pack_scenario="",
                world_static_terrain_water_animation_capture=False,
                region_validation=False,
                region_validation_copy_world=False,
                poi_validation=False,
            )
            runner = capture_runner.CaptureRunner(config)
            runner.configure_java_tool_options()
            options = runner.env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.vulkan.deterministicTemporalParity=true", options)
            self.assertIn("-Dmattmc.vulkan.deterministicTemporalParity.worldTime=6000", options)
            self.assertNotIn("-Dmattmc.vulkan.traceShaderInputParity=true", options)

            config.shaders = "off"
            vanilla_runner = capture_runner.CaptureRunner(config)
            vanilla_runner.configure_java_tool_options()
            vanilla_options = vanilla_runner.env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.vulkan.deterministicTemporalParity=true", vanilla_options)

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
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            self.assertIn("--deterministic-camera-capture", command)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.fixedTime=6000", env["JAVA_TOOL_OPTIONS"])

    def test_static_translucent_overlap_capture_keeps_full_camera_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "gameplay",
                    "--world-static-terrain-scenario",
                    "translucent-overlap",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=7", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", java_options)
            self.assertGreater(
                java_options.rfind("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2"),
                java_options.rfind("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1"),
            )
            self.assertGreater(
                java_options.rfind("-Dmattmc.dev.deterministicCameraCapture.poseCount=7"),
                java_options.rfind("-Dmattmc.dev.deterministicCameraCapture.poseCount=1"),
            )

    def test_rust_vulkan_moving_mesh_capture_requests_real_gameplay_attachment_dump(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-mesh-falling-block-scenario",
                    "sand",
                    "--world-mesh-piston-scenario",
                    "normal-extending",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            self.assertTrue(env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR"].endswith("whole_frame_gameplay_attachments"))
            self.assertTrue(
                env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_REQUEST"].endswith(
                    "whole_frame_gameplay_attachments/requested-gameplay-frame.properties"
                )
            )
            self.assertEqual("1", env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY"])
            self.assertEqual("2", env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_MESH_INSTANCES"])
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.freezePistonProgress=true", java_options)
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand", java_options)
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.pistonScenario=normal-extending", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=12", java_options)
            self.assertNotIn(
                "-Dmattmc.dev.deterministicCameraCapture.poseCount=1",
                shlex.split(java_options),
            )

    def test_full_gameplay_attachment_flag_requests_complete_dump(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-mesh-falling-block-scenario",
                    "sand",
                    "--rust-full-gameplay-attachments",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            self.assertEqual("0", env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY"])

    def test_full_gameplay_attachments_keep_normal_rust_graph_unselected_and_correlated(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--rust-full-gameplay-attachments",
                ]
            )
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            self.assertEqual("0", env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY"])
            self.assertNotIn("MATTMC_RUST_SELECTED_SOURCE_EXECUTION", env)
            self.assertNotIn("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIAGNOSTIC_ONCE", env)
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.rustFinalOutputEveryPose=true",
                env["JAVA_TOOL_OPTIONS"],
            )

    def test_static_terrain_readiness_does_not_collapse_falling_mesh_capture_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-static-terrain-scenario",
                    "real-world",
                    "--world-mesh-falling-block-scenario",
                    "sand",
                    "--rust-selected-source-execution",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=5", java_options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", java_options)
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.rustFinalOutputEveryPose=true",
                java_options,
            )

    def test_static_terrain_readiness_does_not_collapse_arrow_mesh_capture_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-static-terrain-scenario",
                    "real-world",
                    "--world-mesh-arrow-scenario",
                    "ordinary",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=5", java_options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", java_options)

    def test_selected_source_capture_does_not_collapse_model_part_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-static-terrain-scenario",
                    "real-world",
                    "--world-mesh-model-scenario",
                    "decorated-pot",
                    "--rust-selected-source-execution",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=5", java_options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", java_options)

    def test_opengl_distant_horizons_capture_uses_internal_main_target(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-opengl-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "extended",
                    "--mode",
                    mode.name,
                    "--world-distant-horizons-opaque",
                ]
            )
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("--deterministic-camera-capture", command)
            self.assertEqual(
                1,
                java_options.count("-Dmattmc.dev.deterministicCameraCapture.internalScreenshots=true"),
            )

    def test_selected_source_falling_capture_preserves_multi_frame_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-mesh-falling-block-scenario",
                    "sand",
                    "--rust-selected-source-execution",
                ]
            )
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=5", options)
            self.assertNotIn(
                "-Dmattmc.dev.deterministicCameraCapture.poseCount=1",
                shlex.split(options),
            )
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.rustFinalOutputEveryPose=true",
                options,
            )
            self.assertIn("--deterministic-camera-capture", command)
            self.assertNotIn("--deterministic-static-camera-capture", command)
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.sourceEntityIsolation=true",
                shlex.split(options),
            )
            self.assertEqual("4", env["MATTMC_CAPTURE_RENDER_DISTANCE"])
            # Selected-source fixtures explicitly use a shared four-chunk
            # simulation window for both renderers.
            self.assertEqual("4", env["MATTMC_CAPTURE_SIMULATION_DISTANCE"])
            self.assertNotIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=static-terrain",
                shlex.split(options),
            )

    def test_selected_source_stage_diagnostic_keeps_the_real_falling_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-mesh-falling-block-scenario",
                    "sand",
                    "--rust-selected-source-execution",
                ]
            )
            with mock.patch.dict(
                os.environ,
                {"MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE": "shader-pack-stage:test:primary"},
                clear=False,
            ):
                _, env = harness.build_capture_command(
                    target, mode, root / "capture", "correctness", args, "capture"
            )
            options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=5", options)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", options)

    def test_raw_primed_tnt_jvm_scenario_uses_the_typed_capture_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--rust-selected-source-execution",
                    "--jvm-arg=-Dmattmc.dev.rustGalWorldMesh.primedTntScenario=ordinary",
                ]
            )
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            options = shlex.split(env["JAVA_TOOL_OPTIONS"])

            self.assertEqual("ordinary", args.world_mesh_primed_tnt_scenario)
            self.assertIn("--deterministic-camera-capture", command)
            self.assertNotIn("--deterministic-static-camera-capture", command)
            self.assertEqual(
                1,
                options.count("-Dmattmc.dev.rustGalWorldMesh.primedTntScenario=ordinary"),
            )
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=5", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.sourceEntityIsolation=true", options)

    def test_item_entity_scenario_is_exposed_as_a_typed_rust_capture_route(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args([
                "capture", "--profile", "standard", "--mode", mode.name,
                "--world-item-entity-scenario", "ordinary",
                "--world-item-entity-count", "2",
                "--world-item-entity-control", "rust",
            ])
            command, env = harness.build_capture_command(
                target, mode, root / "capture", "correctness", args, "capture"
            )
            options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertEqual("ordinary", args.world_item_entity_scenario)
            self.assertIn("-Dmattmc.dev.rustGalWorldItemEntity.scenario=ordinary", options)
            self.assertIn("-Dmattmc.dev.rustGalWorldItemEntity.count=2", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=5", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1", options)
            self.assertIn("--deterministic-camera-capture", command)

    def test_rust_vulkan_weather_capture_uses_copied_world_route_and_attachment_dump(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "correctness",
                    "--world-weather-scenario",
                    "rain",
                    "--rust-selected-source-execution",
                ]
            )
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("--deterministic-camera-capture", command)
            self.assertNotIn("-Dmattmc.dev.rustGalWeather.v1=true", java_options)
            self.assertIn("-Dmattmc.dev.rustGalWeather.scenario=rain", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.sourceEntityIsolation=true", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2", java_options)
            self.assertIn("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR", env)

    def test_rust_vulkan_cloud_capture_uses_semantic_route_without_java_cloud_flags(self) -> None:
        """Cloud fixtures must enter the Rust whole-frame semantic path directly."""
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "correctness",
                    "--world-cloud-scenario",
                    "bounded",
                ]
            )
            setattr(args, "_canonical_fixture_run_source", str(root / "canonical" / "run"))
            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("--deterministic-camera-capture", command)
            self.assertIn("-Dmattmc.dev.rustGalClouds.scenario=bounded", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.cloudRangeChunks=12", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.fixedCloudTime=0.0", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0", java_options)
            self.assertNotIn("-Dmattmc.dev.rustGalClouds.v1=true", java_options)
            self.assertNotIn("-Dmattmc.dev.rustGalClouds.legacyControl=true", java_options)
            self.assertIn("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR", env)

    def test_bounded_cloud_fixture_freezes_the_same_range_and_phase_for_frozen_baseline(self) -> None:
        """Cloud parity is invalid if only Rust gets the bounded radius or clock."""
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "frozen")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "correctness",
                    "--world-cloud-scenario",
                    "bounded",
                ]
            )
            setattr(args, "_canonical_fixture_run_source", str(root / "canonical" / "run"))
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            java_options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.rustGalClouds.scenario=bounded", java_options)
            self.assertIn("-Dmattmc.dev.rustGalClouds.radiusLimit=16", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.cloudRangeChunks=12", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.fixedCloudTime=0.0", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0", java_options)
            self.assertNotIn("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR", env)

    def test_background_capture_uses_one_static_pose_for_both_current_and_frozen(self) -> None:
        """Static sky probes must not create an asymmetric camera schedule."""
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--world-background-scenario",
                    "overworld-day",
                ]
            )
            for mode_name in ("current-rust-vulkan-shaders-off", "frozen-opengl-shaders-off"):
                mode = next(mode for mode in harness.MATRIX_MODES if mode.name == mode_name)
                target = fake_repo(root, mode.target)
                _command, env = harness.build_capture_command(
                    target, mode, root / mode_name, "correctness", args, "capture"
                )
                options = shlex.split(env["JAVA_TOOL_OPTIONS"])
                self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=1", options)
                self.assertIn("-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0", options)

    def test_ordinary_gameplay_disables_optional_dh_symmetrically(self) -> None:
        """Normal gameplay parity must not accidentally compare asynchronous DH work."""
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            args = harness.parse_args(
                ["gameplay", "--profile", "standard", "--world", "Origin"]
            )
            for mode_name in ("current-rust-vulkan-shaders-off", "frozen-opengl-shaders-off"):
                mode = next(mode for mode in harness.MATRIX_MODES if mode.name == mode_name)
                target = fake_repo(root, mode.target)
                _command, env = harness.build_capture_command(
                    target, mode, root / mode_name, "gameplay", args, "gameplay"
                )
                self.assertEqual("true", env["MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE"])
                self.assertIn(
                    "-Dmattmc.dev.rustGalDistantHorizons.disabled=true",
                    shlex.split(env["JAVA_TOOL_OPTIONS"]),
                )

    def test_ordinary_dh_lifecycle_callbacks_do_not_count_as_rendered_workload(self) -> None:
        frame_doc = {
            "measuredFrameCount": 2,
            "exclusivePhaseNanos": {
                "distant-horizons.lod-render": {"count": 2},
                "distant-horizons.opaque-fade": {"count": 4},
            },
        }
        summary = harness.stable_workload_family_summary(
            frame_doc,
            ordinary_dh_disabled=True,
        )
        self.assertEqual("zero", summary["dh-lod"]["bucket"])
        self.assertEqual("zero", summary["dh-fade"]["bucket"])

    def test_weather_pair_maps_frozen_baseline_to_legacy_route(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "frozen")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            args = harness.parse_args(
                ["capture", "--profile", "standard", "--mode", mode.name, "--world-weather-scenario", "rain"]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")
            options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.rustGalWeather.scenario=rain", options)
            self.assertIn("-Dmattmc.dev.rustGalWeather.legacyControl=true", options)
            self.assertNotIn("-Dmattmc.dev.rustGalWeather.disabled=true", options)

    def test_static_terrain_capture_execution_uses_the_captured_submission_without_a_lifecycle(self) -> None:
        valid, evidence = harness.static_terrain_capture_execution_evidence(
            {"required": True, "requestSubmission": 270, "requestInstances": 13}, "real-world"
        )
        self.assertTrue(valid, evidence)
        self.assertEqual("captured-submission", evidence)

    def test_static_terrain_coverage_comparison_excludes_uncovered_observer_candidates(self) -> None:
        records = {
            ("covered", "solid"): {"coveragePresent": 1, "primitiveCount": 6},
            ("candidate-only", "solid"): {"coveragePresent": 0, "primitiveCount": 6},
        }
        self.assertEqual(
            {("covered", "solid")},
            set(harness.static_terrain_covered_records(records)),
        )

    def test_static_terrain_coverage_does_not_compare_truncated_record_samples_as_sets(self) -> None:
        """A bounded receipt reports its limit, while aggregate parity stays strict."""
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            artifacts: list[Path] = []
            for name, stage, records, declared in (
                ("frozen", "java-opengl-draw-capture-ready-coverage", ["frozen-section"], 1),
                ("current", "rust-vulkan-enqueue-source-capture-ready-coverage", ["current-section"], 2),
            ):
                artifact = root / name / "run-01" / "graphics_audit_artifact.json"
                sidecar = artifact.parent / "capture" / "static_terrain_parity_diagnostics.jsonl"
                sidecar.parent.mkdir(parents=True)
                events = []
                for index, layer in enumerate(("solid", "cutout"), start=1):
                    events.append({
                        "schema": "mattmc-static-terrain-draw-coverage-v1",
                        "stage": stage,
                        "layer": layer,
                        "eventIndex": index,
                        "frameId": 9,
                        "aggregate": {"records": declared},
                        "records": [
                            {"sectionKey": key, "coveragePresent": 1, "primitiveCount": 6}
                            for key in records
                        ],
                    })
                sidecar.write_text("\n".join(json.dumps(event) for event in events) + "\n", encoding="utf-8")
                artifact.write_text("{}\n", encoding="utf-8")
                artifacts.append(artifact)
            report = harness.cross_repository_static_terrain_draw_coverage_report({
                "pairs": [{"baseline_artifact": str(artifacts[0]), "current_artifact": str(artifacts[1])}],
            })
            failures = report["pairs"][0]["failures"]
            self.assertIn("solid_coverage_aggregate_mismatch", failures)
            self.assertNotIn("solid_coverage_missing_sections", failures)
            self.assertNotIn("solid_coverage_extra_sections", failures)
            self.assertTrue(report["pairs"][0]["layers"]["solid"]["current_records_complete"] is False)
            self.assertFalse(report["pairs"][0]["layers"]["solid"]["record_sets_comparable"])

    def test_static_terrain_execution_promotes_capture_ready_receipt(self) -> None:
        events = [
            {
                "stage": "rust-vulkan-executed-coverage",
                "frameId": 99,
                "records": [{"sectionKey": "stale", "coveragePresent": 1}],
            },
            {
                "stage": "rust-vulkan-executed-ready-coverage",
                "frameId": 7,
                "records": [{"sectionKey": "executed", "coveragePresent": 1}],
            },
            {
                "stage": "rust-vulkan-executed-capture-ready-coverage",
                "frameId": 11,
                "records": [{"sectionKey": "captured", "coveragePresent": 1}],
            },
        ]
        selected = harness.latest_static_terrain_execution_events(events)
        self.assertEqual("rust-vulkan-executed-capture-ready-coverage", selected[0]["stage"])
        self.assertEqual(11, selected[0]["frameId"])

    def test_static_terrain_execution_prefers_capture_ready_receipt_over_warmup(self) -> None:
        events = [
            {
                "stage": "rust-vulkan-executed-ready-coverage",
                "frameId": 17,
                "deterministicRenderedFrameIndex": 97,
                "records": [{"sectionKey": "warmup", "coveragePresent": 1}],
            },
            {
                "stage": "rust-vulkan-executed-capture-ready-coverage",
                "eventIndex": 33,
                "frameId": 432,
                # The renderer's completion receipt is issued immediately
                # after the capture frame, so this counter can be one later.
                "deterministicRenderedFrameIndex": 433,
                "records": [{"sectionKey": "captured", "coveragePresent": 1}],
            },
        ]
        selected = harness.latest_static_terrain_execution_events(events, preferred_deterministic_frame=432)
        self.assertEqual("rust-vulkan-executed-capture-ready-coverage", selected[0]["stage"])
        self.assertEqual("captured", selected[0]["records"][0]["sectionKey"])

    def test_static_terrain_cross_repository_coverage_rejects_empty_receipts(self) -> None:
        """Two setup-only receipts must never be admitted as terrain parity."""
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            artifacts: list[Path] = []
            for name, stage in (
                ("frozen", "java-opengl-draw-coverage"),
                ("current", "rust-vulkan-enqueue-source-coverage"),
            ):
                artifact = root / name / "run-01" / "graphics_audit_artifact.json"
                sidecar = artifact.parent / "capture" / "static_terrain_parity_diagnostics.jsonl"
                sidecar.parent.mkdir(parents=True)
                sidecar.write_text(
                    "\n".join(
                        json.dumps({
                            "schema": "mattmc-static-terrain-draw-coverage-v1",
                            "stage": stage,
                            "layer": layer,
                            "eventIndex": index,
                            "frameId": 1,
                            "aggregate": {"records": 0},
                            "records": [],
                        })
                        for index, layer in enumerate(("solid", "cutout"), start=1)
                    ) + "\n",
                    encoding="utf-8",
                )
                artifact.write_text("{}\n", encoding="utf-8")
                artifacts.append(artifact)
            report = harness.cross_repository_static_terrain_draw_coverage_report({
                "pairs": [{"baseline_artifact": str(artifacts[0]), "current_artifact": str(artifacts[1])}],
            })
            self.assertFalse(report["passed"])
            failures = report["pairs"][0]["failures"]
            self.assertIn("solid_frozen_coverage_empty", failures)
            self.assertIn("solid_current_coverage_empty", failures)
            self.assertIn("cutout_frozen_coverage_empty", failures)
            self.assertIn("cutout_current_coverage_empty", failures)

    def test_static_terrain_lifecycle_capture_still_requires_post_setup_execution(self) -> None:
        invalid, evidence = harness.static_terrain_capture_execution_evidence(
            {"required": True, "requestSubmission": 270, "requestInstances": 13}, "texture-palette"
        )
        self.assertFalse(invalid)
        self.assertIn("post-setup Rust execution", evidence)

    def test_static_terrain_capture_requests_deterministic_capture_for_gameplay_profile(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "gameplay",
                    "--world-static-terrain-scenario",
                    "translucent-mixed-unsupported",
                ]
            )

            command, env = harness.build_capture_command(target, mode, root / "capture", "gameplay", args, "capture")

            self.assertIn("--deterministic-camera-capture", command)
            self.assertIn("--deterministic-static-camera-capture", command)
            self.assertIn("MATTMC_DETERMINISTIC_METADATA", env)
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.metadata=", env["JAVA_TOOL_OPTIONS"])
            self.assertNotIn("-Dmattmc.dev.deterministicCameraCapture.screenshotDir=", env["JAVA_TOOL_OPTIONS"])
            self.assertIn(
                "-Dmattmc.dev.rustGalStaticTerrain.scenario=translucent-mixed-unsupported",
                env["JAVA_TOOL_OPTIONS"],
            )

    def test_static_terrain_gameplay_uses_identical_fixed_workload_inputs_for_frozen_and_current(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            args = harness.parse_args(
                [
                    "gameplay",
                    "--profile",
                    "standard",
                    "--world-static-terrain-scenario",
                    "translucent-overlap",
                ]
            )
            frozen_target = fake_repo(root, "frozen")
            (frozen_target.root / "DevUtils" / "Common" / "capture_runner.py").unlink()
            (frozen_target.root / "DevUtils" / "Common" / "capture_runner.sh").write_text(
                "#!/usr/bin/env bash\nexit 0\n", encoding="utf-8"
            )
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            current_target = fake_repo(root, "current")
            current_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off")
            _, frozen_env = harness.build_capture_command(
                frozen_target, frozen_mode, root / "frozen-gameplay", "gameplay", args, "gameplay"
            )
            _, current_env = harness.build_capture_command(
                current_target, current_mode, root / "current-gameplay", "gameplay", args, "gameplay"
            )
            for env in (frozen_env, current_env):
                options = shlex.split(env["JAVA_TOOL_OPTIONS"])
                self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.yawDelta=0.0", options)
                self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.displayFpsCheckEnabled=false", options)
                self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.cameraPathType=fixed-static-terrain", options)
                self.assertEqual("260", env["MATTMC_CAPTURE_MAX_FPS"])
                self.assertEqual("4", env["MATTMC_CAPTURE_RENDER_DISTANCE"])
                self.assertEqual("5", env["MATTMC_CAPTURE_SIMULATION_DISTANCE"])

    def test_frozen_shell_settled_static_capture_waits_for_the_same_terrain_readiness(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "frozen")
            capture_runner = target.root / "DevUtils" / "Common" / "capture_runner.py"
            capture_runner.unlink()
            shell_entrypoint = target.root / "DevUtils" / "Common" / "capture_runner.sh"
            shell_entrypoint.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "settled-static",
                    "--world-static-terrain-scenario",
                    "real-world",
                ]
            )

            command, env = harness.build_capture_command(target, mode, root / "capture", "settled-static", args, "capture")

            self.assertEqual("bash", command[0])
            java_options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.staticTerrainParityDiagnostics=true", java_options)
            self.assertIn("-Dmattmc.dev.staticTerrainParityDiagnostics.waitForStable=true", java_options)
            self.assertIn("-Dmattmc.dev.staticTerrainParityDiagnostics.readyFrames=3", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.settledReadyMaxWaitFrames=300", java_options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1200", java_options)
            self.assertTrue(any(option.startswith("-Dmattmc.dev.staticTerrainParityDiagnostics.path=") for option in java_options))

    def test_frozen_model_fixture_waits_for_visible_terrain_before_capture(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "frozen")
            capture_runner = target.root / "DevUtils" / "Common" / "capture_runner.py"
            capture_runner.unlink()
            shell_entrypoint = target.root / "DevUtils" / "Common" / "capture_runner.sh"
            shell_entrypoint.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-mesh-model-scenario",
                    "chest",
                ]
            )

            command, env = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")

            self.assertEqual("bash", command[0])
            java_options = shlex.split(env["JAVA_TOOL_OPTIONS"])
            self.assertIn("-Dmattmc.dev.staticTerrainParityDiagnostics=true", java_options)
            self.assertIn("-Dmattmc.dev.staticTerrainParityDiagnostics.waitForStable=true", java_options)
            self.assertIn("-Dmattmc.dev.staticTerrainParityDiagnostics.readyFrames=3", java_options)

    def test_selected_source_capture_uses_one_settled_pose(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--workload-profile",
                    "gameplay",
                    "--world-distant-horizons-opaque",
                    "--rust-selected-source-execution",
                ]
            )

            command, env = harness.build_capture_command(target, mode, root / "capture", "gameplay", args, "capture")

            self.assertIn("--deterministic-camera-capture", command)
            self.assertIn("--deterministic-static-camera-capture", command)
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.framesPerPose=2",
                env["JAVA_TOOL_OPTIONS"],
            )

    def test_static_terrain_capture_rejects_non_rust_vulkan_routes_before_launch(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-opengl-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-static-terrain-scenario",
                    "texture-palette",
                ]
            )

            with self.assertRaisesRegex(ValueError, "requires current-rust-vulkan"):
                harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")

    def test_frozen_opengl_static_terrain_capture_is_allowed_for_parity_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "frozen")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
            args = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    mode.name,
                    "--world-static-terrain-scenario",
                    "translucent-water",
                ]
            )

            command, _ = harness.build_capture_command(target, mode, root / "capture", "correctness", args, "capture")

            self.assertIn("--world-static-terrain-scenario", command)
            self.assertIn("translucent-water", command)
            self.assertIn("--world-static-terrain-water-animation-capture", command)

    def test_rust_vulkan_gameplay_timing_does_not_request_attachment_readback(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "gameplay",
                    "--profile",
                    "performance",
                    "--mode",
                    mode.name,
                    "--world-mesh-falling-block-scenario",
                    "sand",
                    "--world-mesh-piston-scenario",
                    "normal-extending",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "gameplay", args, "gameplay")
            self.assertNotIn("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR", env)
            self.assertEqual("0", env["SCREENSHOT_MAX_COUNT"])

    def test_rust_vulkan_shader_graph_isolation_is_explicit_env_only(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = fake_repo(root, "current")
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            args = harness.parse_args(
                [
                    "gameplay",
                    "--profile",
                    "performance",
                    "--mode",
                    mode.name,
                    "--shader-graph-isolation",
                    "terrain-plus-shadow",
                ]
            )
            _, env = harness.build_capture_command(target, mode, root / "capture", "gameplay", args, "gameplay")
            self.assertEqual("terrain-plus-shadow", env["MATTMC_RUST_SHADER_GRAPH_ISOLATION"])
            self.assertNotIn("mattmc.dev.rustGalShaderGraphIsolation", env["JAVA_TOOL_OPTIONS"])

    def test_rust_vulkan_gameplay_accepts_benchmark_producer_evidence_without_attachments(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            target = fake_repo(temp, mode.target)
            capture = temp / "capture"
            write_capture(capture, backend=mode.backend, shaders=mode.shaders, world="Origin")
            write_frame_benchmark(
                capture,
                [16_000_000, 17_000_000, 15_500_000],
                rust_gal_line=(
                    "Rust VulkanicGAL GUI frame executed producer=gui.frame "
                    "rust_gal_world_mesh_instances_executed=3 "
                    "rust_gal_world_mesh_batches_executed=2 "
                    "rust_gal_world_mesh_draws_executed=2 "
                    "rust_gal_world_mesh_cache_hits=9 "
                    "rust_gal_world_mesh_cache_misses=2 "
                    "rust_gal_ffi_world_mesh_asset_update_calls=1 "
                    "rust_gal_ffi_world_mesh_asset_update_bytes=4096 "
                    "rust_gal_ffi_frame_acquire_calls=3 "
                    "rust_gal_ffi_frame_present_calls=3 "
                    "rust_gal_ffi_submit_calls=3 "
                    "ffi_call_count=9 ffi_bytes=2048"
                ),
                block_display={
                    "enabled": True,
                    "scenario": "oak-leaves",
                    "workload": "single",
                    "routeControl": "rust",
                    "status": "spawned",
                    "block": "minecraft:oak_leaves",
                    "position": "1,2,3",
                    "entityCount": 1,
                    "distinctBlockCount": 1,
                    "workloadFingerprint": "single|0:minecraft:oak_leaves",
                },
                block_display_work_count=3,
            )
            frame_path = next(capture.glob("graphics_frame_benchmark_*.json"))
            frame_doc = json.loads(frame_path.read_text(encoding="utf-8"))
            frame_doc["fallingBlockScenario"] = {
                "enabled": True,
                "scenario": "sand",
                "routeControl": "rust",
                "status": "spawned",
                "block": "minecraft:sand",
                "position": "1,2,3",
                "entityCount": 1,
                "routeCounts": {"rust-vulkan-whole-frame": 1},
                "movingRouteCounts": {"falling-block:rust-vulkan-whole-frame": 1},
                "workloadFingerprint": "sand|count=1|block=minecraft:sand",
            }
            frame_doc["pistonScenario"] = {
                "enabled": True,
                "scenario": "normal-extending",
                "routeControl": "rust",
                "status": "spawned",
                "block": "minecraft:stone",
                "position": "1,2,3",
                "entityCount": 1,
                "movingRouteCounts": {"piston:rust-vulkan-whole-frame": 1},
                "shellScan": {
                    "samples": 1,
                    "fallbackSamples": 0,
                    "totalNanos": 1000,
                    "maxNanos": 1000,
                    "chunksScanned": 0,
                    "blockEntitiesInspected": 0,
                    "pistonEntitiesFound": 1,
                    "pistonStatesExtracted": 1,
                },
                "workloadFingerprint": "normal-extending|count=1|block=minecraft:stone",
            }
            frame_doc["submittedWorkCounts"] = {
                "block-display": 3,
                "falling-block": 1,
                "piston": 1,
                "moving-block-route": 2,
            }
            frame_path.write_text(json.dumps(frame_doc), encoding="utf-8")
            (capture / "runClient_20260101_000000.log").write_text(
                "-Dmattmc.dev.rustGalVulkanWholeFrame=true\n"
                "-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario=oak-leaves\n"
                "-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario=sand\n"
                "-Dmattmc.dev.rustGalWorldMesh.pistonScenario=normal-extending\n",
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
            self.assertIsNone(
                artifact["capture"]["whole_frame_gameplay_attachments"]["manifest_doc"]
            )

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
                "--world-mesh-piston-direction",
                "east",
                "--world-mesh-piston-progress",
                "0.125",
                "--positive-control-delay-ms",
                "3",
            ])
            _, env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "gameplay", "gameplay", args, "gameplay"
            )
            options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.freezePistonProgress=true", options)
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.pistonDirection=east", options)
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.pistonProgress=0.125", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.gcBeforeMeasurement=true", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.positiveControlDelayNanos=3000000", options)
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.pistonScenario=normal-extending", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.measureFrames=900", options)
            self.assertEqual(env["SCREENSHOT_MAX_COUNT"], "0")

    def test_rust_vulkan_gameplay_default_window_is_bounded_but_explicit_window_is_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = harness.parse_args([
                "gameplay",
                "--profile",
                "standard",
                "--mode",
                "current-rust-vulkan-shaders-off",
            ])
            _, env = harness.build_capture_command(
                target, harness.MATRIX_MODES[4], root / "gameplay", "gameplay", args, "gameplay"
            )
            options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.warmupFrames=30", options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.measureFrames=60", options)

            explicit = harness.parse_args([
                "gameplay",
                "--profile",
                "standard",
                "--mode",
                "current-rust-vulkan-shaders-off",
                "--warmup-frames",
                "120",
                "--measure-frames",
                "300",
            ])
            _, explicit_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[4], root / "explicit", "gameplay", explicit, "gameplay"
            )
            explicit_options = explicit_env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.warmupFrames=120", explicit_options)
            self.assertIn("-Dmattmc.dev.graphicsFrameBenchmark.measureFrames=300", explicit_options)

    def test_moving_mesh_capture_uses_unfrozen_frame_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = harness.parse_args([
                "capture",
                "--profile",
                "standard",
                "--mode",
                "current-opengl-shaders-off",
                "--world-mesh-piston-scenario",
                "normal-extending",
                "--world-mesh-piston-direction",
                "east",
                "--world-mesh-piston-progress",
                "0.125",
            ])
            _, env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture", "capture", args, "capture"
            )
            options = env["JAVA_TOOL_OPTIONS"]
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.internalScreenshots=true", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.poseCount=7", options)
            self.assertIn("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1", options)
            self.assertIn("-Dmattmc.dev.rustGalWorldMesh.pistonDirection=east", options)
            self.assertNotIn("-Dmattmc.dev.rustGalWorldMesh.freezePistonProgress=true", options)
            self.assertNotIn("-Dmattmc.dev.rustGalWorldMesh.pistonProgress=0.125", options)

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

            dh = harness.parse_args(
                ["capture", "--profile", "standard", "--world", "Origin", "--world-distant-horizons-opaque"]
            )
            _, dh_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-dh", "correctness", dh, "capture"
            )
            dh_options = shlex.split(dh_env["JAVA_TOOL_OPTIONS"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=distant-horizons",
                dh_options,
            )

            dh_non_water = harness.parse_args(
                ["capture", "--profile", "standard", "--world", "Origin", "--world-distant-horizons-non-water"]
            )
            _, dh_non_water_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-dh-non-water", "correctness", dh_non_water, "capture"
            )
            self.assertEqual("true", dh_non_water_env["MATTMC_CAPTURE_DH_RUST_NON_WATER"])
            self.assertEqual("false", dh_non_water_env["MATTMC_CAPTURE_DH_RUST_OPAQUE_ONLY"])
            self.assertIn(
                "-Dmattmc.dev.rustGalDistantHorizons.requireTransparent=true",
                shlex.split(dh_non_water_env["JAVA_TOOL_OPTIONS"]),
            )

            dh_water = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--world",
                    "Origin",
                    "--world-distant-horizons-opaque",
                    "--world-distant-horizons-water",
                ]
            )
            _, dh_water_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture-dh-water", "correctness", dh_water, "capture"
            )
            self.assertEqual("false", dh_water_env["MATTMC_CAPTURE_DH_RUST_OPAQUE_ONLY"])
            self.assertIn(
                "-Dmattmc.dev.rustGalDistantHorizons.requireWater=true",
                shlex.split(dh_water_env["JAVA_TOOL_OPTIONS"]),
            )

            ordinary_source = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    "current-rust-vulkan-shaders-on",
                    "--world",
                    "Origin",
                    "--rust-selected-source-execution",
                ]
            )
            rust_vulkan = next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-on")
            _, ordinary_source_env = harness.build_capture_command(
                target, rust_vulkan, root / "capture-ordinary-source", "correctness", ordinary_source, "capture"
            )
            ordinary_source_options = shlex.split(ordinary_source_env["JAVA_TOOL_OPTIONS"])
            self.assertEqual("true", ordinary_source_env["MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE"])
            self.assertIn("-Dmattmc.dev.rustGalDistantHorizons.disabled=true", ordinary_source_options)
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=sodium-terrain",
                ordinary_source_options,
            )
            self.assertNotIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=distant-horizons",
                ordinary_source_options,
            )

            frozen_source = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
            frozen_target = fake_repo(root, "frozen")
            _, frozen_source_env = harness.build_capture_command(
                frozen_target,
                frozen_source,
                root / "capture-frozen-ordinary-source",
                "correctness",
                ordinary_source,
                "capture",
            )
            self.assertEqual("true", frozen_source_env["MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE"])
            self.assertEqual("4", frozen_source_env["MATTMC_CAPTURE_RENDER_DISTANCE"])
            self.assertEqual("4", frozen_source_env["MATTMC_CAPTURE_SIMULATION_DISTANCE"])

            resource_pack_source = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    "current-rust-vulkan-shaders-on",
                    "--world",
                    "Origin",
                    "--world-static-terrain-resource-pack-scenario",
                    "pack-a",
                ]
            )
            _, resource_pack_env = harness.build_capture_command(
                target, rust_vulkan, root / "capture-resource-pack", "correctness", resource_pack_source, "capture"
            )
            resource_pack_options = shlex.split(resource_pack_env["JAVA_TOOL_OPTIONS"])
            self.assertEqual("true", resource_pack_env["MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE"])
            self.assertIn("-Dmattmc.dev.rustGalDistantHorizons.disabled=true", resource_pack_options)
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=sodium-terrain",
                resource_pack_options,
            )
            resource_pack_gameplay = harness.parse_args(
                [
                    "gameplay",
                    "--profile",
                    "standard",
                    "--mode",
                    "current-rust-vulkan-shaders-off",
                    "--world",
                    "Origin",
                    "--gui-resource-pack-scenario",
                    "pack-a",
                ]
            )
            _, resource_pack_gameplay_env = harness.build_capture_command(
                target,
                rust_vulkan,
                root / "gameplay-resource-pack",
                "gameplay",
                resource_pack_gameplay,
                "gameplay",
            )
            self.assertEqual("true", resource_pack_gameplay_env["MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE"])
            frozen_resource_pack = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-on")
            _, frozen_resource_pack_env = harness.build_capture_command(
                target, frozen_resource_pack, root / "capture-frozen-resource-pack", "correctness", resource_pack_source, "capture"
            )
            frozen_resource_pack_options = shlex.split(frozen_resource_pack_env["JAVA_TOOL_OPTIONS"])
            self.assertEqual(str(harness.repo_root()), frozen_resource_pack_env["MATTMC_GUI_PACK_GENERATOR_ROOT"])
            self.assertEqual("pack-a", frozen_resource_pack_env["MATTMC_GUI_RESOURCE_PACK_SCENARIO"])
            self.assertNotIn("-Dmattmc.dev.rustGalStaticTerrain.scenario=real-world", frozen_resource_pack_options)

            static_vulkan = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    "current-rust-vulkan-shaders-off",
                    "--world",
                    "Origin",
                    "--world-static-terrain-scenario",
                    "real-world",
                ]
            )
            _, static_vulkan_env = harness.build_capture_command(
                target, rust_vulkan, root / "capture-static-vulkan", "settled-static", static_vulkan, "capture"
            )
            static_vulkan_options = shlex.split(static_vulkan_env["JAVA_TOOL_OPTIONS"])
            self.assertIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=sodium-terrain",
                static_vulkan_options,
            )
            self.assertNotIn(
                "-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=static-terrain",
                static_vulkan_options,
            )

            frozen_static = harness.parse_args(
                [
                    "capture",
                    "--profile",
                    "standard",
                    "--mode",
                    "frozen-opengl-shaders-off",
                    "--world",
                    "Origin",
                    "--world-static-terrain-scenario",
                    "translucent-water",
                ]
            )
            frozen = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            _, frozen_static_env = harness.build_capture_command(
                target, frozen, root / "capture-frozen-static", "correctness", frozen_static, "capture"
            )
            self.assertEqual("true", frozen_static_env["MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE"])

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

    def test_generated_terrain_identity_pack_keeps_each_palette_sprite_distinct(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            pack_dir = Path(temp) / "mattmc-rust-terrain-identity"
            spec = capture_runner.gui_resource_pack_specs("terrain-identity")[0]
            capture_runner.write_gui_resource_pack(pack_dir, spec)

            pixels = {}
            for resource_path in capture_runner.WORLD_STATIC_TERRAIN_IDENTITY_TEXTURES:
                payload = (pack_dir / resource_path).read_bytes()
                pixels[resource_path] = payload
            self.assertEqual(len(pixels), len(set(pixels.values())))

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

            artifact = harness.normalize_capture_artifact(
                target,
                next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off"),
                capture,
                "capture",
                True,
                [],
                0,
                False,
                tool_kind="capture",
            )

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

            artifact = harness.normalize_capture_artifact(
                target,
                next(mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off"),
                capture,
                "capture",
                True,
                [],
                0,
                False,
                tool_kind="capture",
            )

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

    def test_selected_source_gui_evidence_requires_the_correlated_rust_final_hud(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            screenshot = root / "source_final.png"
            write_rust_shell_scene_image(screenshot)
            from PIL import Image, ImageDraw

            with Image.open(screenshot) as source:
                hud = source.copy()
            draw = ImageDraw.Draw(hud)
            for slot in range(9):
                left = 365 + slot * 60
                draw.rectangle((left, 4, left + 54, 62), outline=(0, 0, 0), width=5)
                draw.rectangle((left + 7, 11, left + 47, 55), fill=(36, 48, 40))
            hud.save(screenshot)
            deterministic = {
                "captures": [
                    {
                        "renderedFrameIndex": 7,
                        "captureMethod": "rust-vulkan-final-output",
                        "targetWindow": "rust-vulkan-final-output",
                        "screenshot": str(screenshot),
                    }
                ]
            }
            shell_evidence = harness.deterministic_rust_vulkan_shell_scene_evidence(
                deterministic,
                None,
                None,
            )
            correlation = {
                "gameplay_frame_id": 211,
                "gal_submission_id": 249,
                "deterministic_rendered_frame_index": 7,
                "gui_sprites": 3,
            }
            evidence = harness.selected_source_gui_final_evidence(
                deterministic,
                correlation,
                True,
                shell_evidence,
            )
            self.assertEqual("visible", evidence["status"])
            self.assertGreaterEqual(evidence["dark_hud_pixels"], evidence["minimum_dark_hud_pixels"])

            correlation["deterministic_rendered_frame_index"] = 8
            uncorrelated = harness.selected_source_gui_final_evidence(
                deterministic,
                correlation,
                True,
                shell_evidence,
            )
            self.assertEqual("missing_correlated_rust_final_capture", uncorrelated["status"])

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

            artifact = harness.normalize_capture_artifact(
                target,
                next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off"),
                capture,
                "capture",
                True,
                [],
                0,
                False,
                tool_kind="capture",
            )

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
                write_world_border_probe_image(screenshot, visible=True)
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

    def test_capture_runner_accepts_falling_block_frame_sequence_names(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            screenshot_dir = root / "screens"
            screenshot_dir.mkdir()
            captures = []
            for index in range(5):
                pose_name = f"falling-{index:02d}"
                screenshot = screenshot_dir / f"{index + 1:02d}_{pose_name}.png"
                write_falling_block_probe_image(screenshot)
                captures.append(
                    {
                        "index": index + 1,
                        "poseName": pose_name,
                        "screenshot": str(screenshot),
                        "backend": "rust-vulkan",
                        "shaderEnabled": True,
                        "shaderPack": "ComplementaryHungLoIfied",
                        "gitCommit": "abc123",
                        "window": {"width": 1280, "height": 720},
                        "dimension": "minecraft:overworld",
                        "position": {"x": 1.0, "y": 80.0, "z": 2.0},
                        "requestedYaw": 12.0,
                        "requestedPitch": 5.0,
                        "observedYaw": 12.0,
                        "observedPitch": 5.0,
                        "renderedFrameIndex": (index + 1) * 8,
                    }
                )
            metadata = root / "deterministic.json"
            metadata.write_text(
                json.dumps(
                    {
                        "status": "complete",
                        "backend": "rust-vulkan",
                        "shaderEnabled": True,
                        "shaderPack": "ComplementaryHungLoIfied",
                        "gitCommit": "abc123",
                        "dimension": "minecraft:overworld",
                        "yawDelta": 0.0,
                        "initialPose": {"name": "initial", "yaw": 12.0, "pitch": 5.0},
                        "initialPosition": {"x": 1.0, "y": 80.0, "z": 2.0},
                        "window": {"width": 1280, "height": 720},
                        "poseSequence": [f"falling-{index:02d}" for index in range(5)],
                        "captures": captures,
                    }
                ),
                encoding="utf-8",
            )

            capture_runner.validate_deterministic_metadata(metadata, screenshot_dir, 0.001)

    def test_capture_runner_rejects_blank_deterministic_screenshot(self) -> None:
        from PIL import Image

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            screenshot_dir = root / "screens"
            screenshot_dir.mkdir()
            screenshot = screenshot_dir / "01_initial.png"
            Image.new("RGB", (1280, 720), (0, 0, 0)).save(screenshot)
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
                        "poseSequence": ["initial"],
                        "captures": [
                            {
                                "index": 1,
                                "poseName": "initial",
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
                                "renderedFrameIndex": 8,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(RuntimeError, "screenshot is blank"):
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

    def static_terrain_doc(self, **overrides: object) -> dict[str, object]:
        event: dict[str, object] = {
            "reason": "visible-submit",
            "gameplayFrameId": 7,
            "terrainExtractionFrameId": 3,
            "rustEnqueueFrameId": 5,
            "executionFrameId": 0,
            "executionSubmissionId": 0,
            "sectionPos": 123456789,
            "layer": "SOLID",
            "meshKey": 11,
            "contentHash": 101,
            "meshGeneration": 101,
            "visibleGeneration": 101,
            "vertexCount": 24,
            "bufferVertexCapacity": 24,
            "vertexStride": 40,
            "indexCount": 36,
            "maxIndex": 23,
            "indexType": 1,
            "sectionCount": 1,
            "sectionOrigin": {"x": 16, "y": 64, "z": 32},
            "transformTranslation": {"x": 1.0, "y": 2.0, "z": 3.0},
            "localBounds": {"minX": 0.0, "minY": 0.0, "minZ": 0.0, "maxX": 16.0, "maxY": 16.0, "maxZ": 16.0},
            "uvBounds": {"minU": 0.1, "minV": 0.2, "maxU": 0.8, "maxV": 0.9},
            "vertexPositionsFinite": True,
            "localBoundsValid": True,
            "uvBoundsValid": True,
            "indexRangeValid": True,
            "segmentLayoutValid": True,
            "sectionOriginValid": True,
            "indexOffsetAlignmentValid": True,
            "cameraBoundsFinite": True,
            "normalContractValid": True,
            "aoContractValid": True,
            "blockSkyLightContractValid": True,
            "topFaceShadeContractValid": True,
            "separateAoActive": True,
            "separateAoVertexCount": 24,
            "aoRange": {"min": 0.35, "max": 1.0},
            "normalSectionCounts": {"posY": 1, "negY": 1, "horizontal": 4},
        }
        event.update(overrides)
        return {"activeNativeVertexStride": 40, "expectedNativeVertexStride": 40, "recentEvents": [event]}

    def assert_static_terrain_failure(self, expected: str, **overrides: object) -> None:
        evidence = harness.static_terrain_geometry_evidence(self.static_terrain_doc(**overrides))
        self.assertEqual(evidence["status"], "fail")
        self.assertIn(expected, evidence["failures"])

    def static_terrain_translucent_doc(self, *events: dict[str, object]) -> dict[str, object]:
        base: dict[str, object] = {
            "gameplayFrameId": 7,
            "terrainExtractionFrameId": 3,
            "rustEnqueueFrameId": 5,
            "executionFrameId": 0,
            "executionSubmissionId": 0,
            "sectionPos": 123456789,
            "layer": "TRANSLUCENT",
            "meshKey": 11,
            "contentHash": 101,
            "meshGeneration": 101,
            "visibleGeneration": 101,
            "vertexCount": 24,
            "bufferVertexCapacity": 24,
            "vertexStride": 40,
            "indexCount": 36,
            "maxIndex": 23,
            "indexType": 2,
            "sectionCount": 1,
            "primitiveCount": 6,
            "sortGeneration": 101,
            "sortedIndexHash": 0x12345678,
            "indexUploadGeneration": 101,
            "translucentDrawOrder": 0,
            "sorterType": "net.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData$DynamicTopoSorter",
            "sourceSortedIndexHash": 0,
            "rustCopiedSortedIndexHash": 0,
            "sourceSortedIndexSampleHash": 0,
            "rustCopiedSortedIndexSampleHash": 0,
            "sortedIndexSample": "",
            "cameraPosition": {"x": 0.0, "y": 70.0, "z": 0.0},
            "sortOrigin": {"x": 24.0, "y": 72.0, "z": 40.0},
        }
        if not events:
            events = (
                {
                    "reason": (
                        "translucent-primitive-accounting"
                        ":sourcePrimitives=6:nonFluidPrimitives=4:waterPrimitives=2:unsupportedPrimitives=0"
                        ":retainedPrimitives=6:omittedPrimitives=0:executedPrimitives=6"
                        ":sourceIndices=36:retainedIndices=36:omittedIndices=0"
                        ":sourceHash=111:retainedHash=111:omittedHash=222"
                        ":rangeCount=2:materialSwitches=1:ranges=9001@0+12,9002@48+24"
                        ":sample=0/1/retained/9001/0,1/2/retained/9002/6"
                    )
                },
                {
                    "reason": "translucent-source-sort",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sourceSortedIndexHash": 0x12345678,
                    "sourceSortedIndexSampleHash": 0x44,
                    "sortedIndexSample": "0,1,2,2,3,0",
                },
                {
                    "reason": "translucent-rust-sort-copy",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "rustCopiedSortedIndexHash": 0x12345678,
                    "rustCopiedSortedIndexSampleHash": 0x44,
                    "sortedIndexSample": "0,1,2,2,3,0",
                },
                {"reason": "visible-submit", "sortGeneration": 102, "visibleGeneration": 102, "indexUploadGeneration": 102},
                {"reason": "executed-submit", "sortGeneration": 102, "visibleGeneration": 102, "indexUploadGeneration": 102, "executionFrameId": 8, "executionSubmissionId": 9},
            )
        merged_events = []
        for event in events:
            merged = dict(base)
            merged.update(event)
            reason = str(merged.get("reason") or "")
            if reason.startswith("translucent-primitive-accounting") and "waterStillPrimitives=" not in reason:
                detail = harness.parse_colon_key_values(reason)
                water = int(harness.parse_number(detail.get("waterPrimitives")) or 0)
                still = 1 if water > 0 else 0
                flow = max(0, water - still)
                reason += (
                    f":waterStillPrimitives={still}"
                    f":waterFlowPrimitives={flow}"
                    ":waterOverlayPrimitives=0"
                    f":waterTextureSwitches={1 if water > 1 else 0}"
                    ":waterAnimationHash=12345"
                    ":waterAnimationEntries=9002/minecraft~block/water_still/16x16/32/2/1/0/0@2|"
                    "9003/minecraft~block/water_flow/16x16/32/2/1/0/0@2|"
                    "9004/minecraft~block/water_overlay/16x16/1/1/0/0/"
                )
                merged["reason"] = reason
            merged_events.append(merged)
        return {"translucentEvents": merged_events}

    def test_static_terrain_geometry_truth_accepts_valid_event(self) -> None:
        evidence = harness.static_terrain_geometry_evidence(self.static_terrain_doc())
        self.assertEqual(evidence["status"], "pass")
        self.assertIsNone(evidence["failure"])
        self.assertEqual(evidence["checked_events"], 1)
        self.assertEqual(evidence["visible_submit_events"], 1)

    def test_static_terrain_geometry_truth_rejects_faults(self) -> None:
        cases = {
            "vertex_stride_invalid": {"vertexStride": 20},
            "vertex_count_exceeds_capacity": {"vertexCount": 25},
            "index_range_invalid": {"maxIndex": 24, "indexRangeValid": False},
            "index_type_invalid": {"indexType": 99},
            "index_alignment_invalid": {"indexOffsetAlignmentValid": False},
            "non_finite_vertex_position": {"vertexPositionsFinite": False},
            "section_origin_mismatch": {"sectionOriginValid": False},
            "stale_generation_visible": {"reason": "stale-or-unregistered-submit", "visibleGeneration": 202},
            "geometry_out_of_bounds": {
                "localBounds": {"minX": 0.0, "minY": 0.0, "minZ": 0.0, "maxX": 4096.0, "maxY": 16.0, "maxZ": 16.0},
                "localBoundsValid": False,
            },
            "terrain_lighting_normal_invalid": {"normalContractValid": False},
            "terrain_lighting_ao_invalid": {"aoContractValid": False},
            "terrain_lighting_block_sky_invalid": {"blockSkyLightContractValid": False},
            "terrain_lighting_top_shade_invalid": {"topFaceShadeContractValid": False},
            "terrain_lighting_ao_missing": {"separateAoVertexCount": 0},
        }
        for expected, overrides in cases.items():
            with self.subTest(expected=expected):
                self.assert_static_terrain_failure(expected, **overrides)

    def test_static_terrain_geometry_truth_prioritizes_old_stride_fault(self) -> None:
        evidence = harness.static_terrain_geometry_evidence(
            self.static_terrain_doc(vertexStride=20)
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertEqual(evidence["failure"], "vertex_stride_invalid")
        self.assertIn("vertex_stride_invalid", evidence["failures"])

    def test_static_terrain_geometry_truth_keeps_count_capacity_classification(self) -> None:
        evidence = harness.static_terrain_geometry_evidence(
            self.static_terrain_doc(vertexCount=25)
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertEqual(evidence["failure"], "vertex_count_exceeds_capacity")
        self.assertIn("vertex_count_exceeds_capacity", evidence["failures"])

    def test_static_terrain_geometry_truth_uses_deterministic_failure_priority(self) -> None:
        evidence = harness.static_terrain_geometry_evidence(
            self.static_terrain_doc(vertexStride=20, vertexCount=25)
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertEqual(evidence["failure"], "vertex_stride_invalid")
        self.assertIn("vertex_count_exceeds_capacity", evidence["failures"])

    def test_static_terrain_geometry_truth_missing_without_fault_stays_missing(self) -> None:
        evidence = harness.static_terrain_geometry_evidence(
            {"activeNativeVertexStride": 40, "expectedNativeVertexStride": 40, "recentEvents": []}
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertEqual(evidence["failure"], "geometry_truth_missing")

    def test_static_terrain_readiness_classifies_rendered_mesh_without_terrain_events(self) -> None:
        failure = harness.static_terrain_readiness_failure(
            expected=True,
            mesh_instances=28,
            accepted_builds=0,
            registered_meshes=0,
            visible_probes=0,
            visible_submissions=0,
            combined_logs="",
        )
        self.assertEqual("terrain-readiness-events-missing", failure)

    def test_static_terrain_readiness_preserves_submission_capacity_failure(self) -> None:
        failure = harness.static_terrain_readiness_failure(
            expected=True,
            mesh_instances=518,
            accepted_builds=0,
            registered_meshes=0,
            visible_probes=0,
            visible_submissions=0,
            combined_logs="world mesh instance count 518 exceeds maximum 512",
        )
        self.assertEqual("terrain-submission-capacity-rejected", failure)

    def test_static_terrain_readiness_does_not_replace_valid_events(self) -> None:
        failure = harness.static_terrain_readiness_failure(
            expected=True,
            mesh_instances=28,
            accepted_builds=1,
            registered_meshes=1,
            visible_probes=1,
            visible_submissions=1,
            combined_logs="",
        )
        self.assertIsNone(failure)

    def test_static_terrain_execution_reconciliation_ignores_empty_source_layer(self) -> None:
        records = {
            ("empty", "solid"): {"primitiveCount": 0},
            ("visible", "solid"): {"primitiveCount": 12},
        }
        self.assertEqual(
            harness.static_terrain_nonempty_records(records),
            {("visible", "solid"): {"primitiveCount": 12}},
        )

    def test_static_terrain_coverage_prefers_execution_game_time(self) -> None:
        events = [
            {
                "stage": "rust-vulkan-enqueue-source-coverage",
                "layer": "solid",
                "gameTime": 20,
                "frameId": 20,
                "eventIndex": 1,
                "aggregate": {"records": 2},
            },
            {
                "stage": "rust-vulkan-enqueue-source-coverage",
                "layer": "solid",
                "gameTime": 21,
                "frameId": 21,
                "eventIndex": 2,
                "aggregate": {"records": 3},
            },
            {
                "stage": "rust-vulkan-executed",
                "layer": "solid",
                "gameTime": 20,
                "frameId": 9,
                "eventIndex": 3,
                "records": [{"sectionKey": 1}],
            },
        ]
        self.assertEqual(20, harness.latest_static_terrain_execution_game_time(events))
        selected = harness.latest_static_terrain_coverage_event(
            events,
            "rust-vulkan-enqueue-source-coverage",
            "solid",
            20,
        )
        self.assertEqual(20, selected["gameTime"])

    def test_static_terrain_geometry_truth_rejects_duplicate_visible_section(self) -> None:
        doc = self.static_terrain_doc()
        doc["recentEvents"] = [dict(doc["recentEvents"][0]), dict(doc["recentEvents"][0])]
        evidence = harness.static_terrain_geometry_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("duplicate_section_visible", evidence["failures"])

    def test_static_terrain_geometry_truth_allows_repeated_visibility_across_frames(self) -> None:
        first = self.static_terrain_doc()["recentEvents"][0]
        second = dict(first)
        second["gameplayFrameId"] = int(first["gameplayFrameId"]) + 1
        second["rustEnqueueFrameId"] = int(first["rustEnqueueFrameId"]) + 1
        doc = {"activeNativeVertexStride": 40, "recentEvents": [first, second]}
        evidence = harness.static_terrain_geometry_evidence(doc)
        self.assertEqual(evidence["status"], "pass")
        self.assertNotIn("duplicate_section_visible", evidence["failures"])

    def test_static_terrain_geometry_truth_preserves_frame_zero_identity(self) -> None:
        first = self.static_terrain_doc(gameplayFrameId=0, rustEnqueueFrameId=1, frame=0)["recentEvents"][0]
        second = dict(first)
        second["frame"] = 1
        second["gameplayFrameId"] = 1
        second["rustEnqueueFrameId"] = 36
        doc = {"activeNativeVertexStride": 40, "recentEvents": [first, second]}
        evidence = harness.static_terrain_geometry_evidence(doc)
        self.assertEqual(evidence["status"], "pass")
        self.assertNotIn("duplicate_section_visible", evidence["failures"])

    def test_static_terrain_geometry_truth_rejects_same_frame_old_new_overlap(self) -> None:
        first = self.static_terrain_doc()["recentEvents"][0]
        second = dict(first)
        second["meshGeneration"] = 202
        second["visibleGeneration"] = 202
        doc = {"activeNativeVertexStride": 40, "recentEvents": [first, second]}
        evidence = harness.static_terrain_geometry_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("stale_generation_overlap", evidence["failures"])

    def test_static_terrain_geometry_truth_rejects_duplicate_execution_in_one_submission(self) -> None:
        base = self.static_terrain_doc(reason="executed-submit", executionFrameId=9, executionSubmissionId=10, rustEnqueueFrameId=0)["recentEvents"][0]
        doc = {"activeNativeVertexStride": 40, "recentEvents": [base, dict(base)]}
        evidence = harness.static_terrain_geometry_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("duplicate_section_execution", evidence["failures"])

    def test_static_terrain_geometry_truth_allows_delayed_execution_next_frame(self) -> None:
        visible = self.static_terrain_doc(gameplayFrameId=7, rustEnqueueFrameId=5)["recentEvents"][0]
        executed = dict(visible)
        executed["reason"] = "executed-submit"
        executed["executionFrameId"] = 8
        executed["executionSubmissionId"] = 11
        executed["rustEnqueueFrameId"] = 0
        doc = {"activeNativeVertexStride": 40, "recentEvents": [visible, executed]}
        evidence = harness.static_terrain_geometry_evidence(doc)
        self.assertEqual(evidence["status"], "pass")

    def test_static_terrain_geometry_truth_rejects_mesh_key_collision(self) -> None:
        doc = self.static_terrain_doc()
        second = dict(doc["recentEvents"][0])
        second["sectionPos"] = 987654321
        second["contentHash"] = 202
        doc["recentEvents"] = [doc["recentEvents"][0], second]
        evidence = harness.static_terrain_geometry_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("mesh_key_collision", evidence["failures"])

    def test_static_terrain_translucent_truth_accepts_valid_events(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(self.static_terrain_translucent_doc())
        self.assertEqual(evidence["status"], "pass")
        self.assertIsNone(evidence["failure"])
        self.assertEqual(evidence["visible_events"], 1)
        self.assertEqual(evidence["primitive_accounting_events"], 1)
        self.assertEqual(
            {
                "net.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData$DynamicTopoSorter": 5,
            },
            evidence["sorter_type_counts"],
        )

    def test_translucent_overlap_color_distance_uses_masked_dominant_occupancy(self) -> None:
        # Equal RGB intensity sums can hide a real pane-order change when the
        # world background occupies most of the crop. The pane mask's dominant
        # channel occupancy remains sensitive to that semantic difference.
        front = {
            "red_sum": 1000,
            "green_sum": 1000,
            "blue_sum": 1000,
            "dominant_red_pixels": 900,
            "dominant_green_pixels": 50,
            "dominant_blue_pixels": 50,
        }
        rear = {
            "red_sum": 1000,
            "green_sum": 1000,
            "blue_sum": 1000,
            "dominant_red_pixels": 50,
            "dominant_green_pixels": 50,
            "dominant_blue_pixels": 900,
        }
        front_vector = harness.color_ratio_vector(front)
        rear_vector = harness.color_ratio_vector(rear)
        self.assertGreater(sum(abs(a - b) for a, b in zip(front_vector, rear_vector)), 0.08)

    def static_terrain_water_animation_dense_doc(self, directory: Path, **overrides: object) -> dict[str, object]:
        from PIL import Image

        frames = []
        for index in range(6):
            screenshot = directory / f"water_animation_frame_{index:03d}.png"
            Image.new("RGB", (8, 8), (64 + index * 10, 96, 160)).save(screenshot)
            frames.append(
                {
                    "index": index,
                    "screenshot": str(screenshot),
                    "renderedFrameIndex": 100 + index,
                    "gameTime": 200 + index,
                    "animationHash": 12345,
                    "animationSummary": (
                        "9002/minecraft~block/water_still/16x16/4/2/1/0/0@1,1@1,2@1,3@1|"
                        "9003/minecraft~block/water_flow/16x16/4/2/1/0/0@1,1@1,2@1,3@1|"
                        "9004/minecraft~block/water_overlay/16x16/2/2/1/0/0@2,1@2"
                    ),
                    "animationState": (
                        f"still=tex:9002,loc:minecraft~block/water_still,generation:7,current:{index % 4},next:{(index + 1) % 4},elapsed:0,duration:1,fraction:0.000000,interpolation:0|"
                        f"flow=tex:9003,loc:minecraft~block/water_flow,generation:7,current:{index % 4},next:{(index + 1) % 4},elapsed:0,duration:1,fraction:0.000000,interpolation:0|"
                        f"overlay=tex:9004,loc:minecraft~block/water_overlay,generation:7,current:{(index // 2) % 2},next:{((index // 2) + 1) % 2},elapsed:{index % 2},duration:2,fraction:{(index % 2) / 2.0:.6f},interpolation:0"
                    ),
                    "visibleLayerSubmissions": 10 + index,
                    "currentFrameVisibleLayerSubmissions": 1,
                    "atlasGeneration": 7,
                }
            )
        dense: dict[str, object] = {
            "enabled": True,
            "requestedFrames": 6,
            "capturedFrames": 6,
            "complete": True,
            "frames": frames,
        }
        dense.update(overrides)
        return {"rustGalStaticTerrainWaterAnimationDenseCapture": dense}

    def test_static_terrain_water_animation_dense_accepts_valid_sequence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence = harness.static_terrain_water_animation_dense_evidence(
                self.static_terrain_water_animation_dense_doc(Path(temp))
            )
        self.assertEqual(evidence["status"], "pass")
        self.assertEqual(evidence["checked_frames"], 6)
        self.assertGreaterEqual(evidence["distinct_frame_keys"], 4)

    def test_static_terrain_water_animation_dense_rejects_missing_frame_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            doc = self.static_terrain_water_animation_dense_doc(Path(temp))
            Path(doc["rustGalStaticTerrainWaterAnimationDenseCapture"]["frames"][2]["screenshot"]).unlink()
            evidence = harness.static_terrain_water_animation_dense_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertEqual(evidence["failure"], "terrain_animation_frame_missing")

    def test_static_terrain_water_animation_dense_rejects_black_frames(self) -> None:
        from PIL import Image

        with tempfile.TemporaryDirectory() as temp:
            doc = self.static_terrain_water_animation_dense_doc(Path(temp))
            for frame in doc["rustGalStaticTerrainWaterAnimationDenseCapture"]["frames"]:
                Image.new("RGB", (8, 8), (0, 0, 0)).save(frame["screenshot"])
            evidence = harness.static_terrain_water_animation_dense_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertEqual(evidence["failure"], "terrain_animation_presented_frame_mismatch")

    def test_static_terrain_water_animation_dense_rejects_zero_work(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            doc = self.static_terrain_water_animation_dense_doc(Path(temp))
            for frame in doc["rustGalStaticTerrainWaterAnimationDenseCapture"]["frames"]:
                frame["visibleLayerSubmissions"] = 10
            evidence = harness.static_terrain_water_animation_dense_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertEqual(evidence["failure"], "terrain_animation_presented_frame_mismatch")

    def test_static_terrain_water_animation_dense_rejects_non_advancing_states(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            doc = self.static_terrain_water_animation_dense_doc(Path(temp))
            for frame in doc["rustGalStaticTerrainWaterAnimationDenseCapture"]["frames"]:
                frame["animationState"] = (
                    "still=tex:9002,loc:minecraft~block/water_still,generation:7,current:0,next:1,elapsed:0,duration:1,fraction:0.000000,interpolation:0|"
                    "flow=tex:9003,loc:minecraft~block/water_flow,generation:7,current:0,next:1,elapsed:0,duration:1,fraction:0.000000,interpolation:0|"
                    "overlay=tex:9004,loc:minecraft~block/water_overlay,generation:7,current:0,next:1,elapsed:0,duration:2,fraction:0.000000,interpolation:0"
                )
            evidence = harness.static_terrain_water_animation_dense_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_animation_frame_order_invalid", evidence["failures"])

    def test_static_terrain_translucent_truth_requires_declared_unsupported_fluid(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(),
            require_unsupported_fluid=True,
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_unsupported_metadata_missing", evidence["failures"])
        self.assertTrue(evidence["unsupported_fluid_required"])

    def test_static_terrain_translucent_truth_accepts_negative_generation_hash(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(
                {
                    "reason": (
                        "translucent-primitive-accounting"
                        ":sourcePrimitives=6:nonFluidPrimitives=4:waterPrimitives=2:unsupportedPrimitives=0"
                        ":retainedPrimitives=6:omittedPrimitives=0:executedPrimitives=6"
                        ":sourceIndices=36:retainedIndices=36:omittedIndices=0"
                        ":sourceHash=111:retainedHash=111:omittedHash=222"
                        ":rangeCount=2:sample=0/1/retained/9001/0"
                    ),
                    "meshGeneration": -3414063591750951357,
                },
                {
                    "reason": "translucent-source-sort",
                    "meshGeneration": -3414063591750951357,
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sourceSortedIndexHash": 0x12345678,
                    "sourceSortedIndexSampleHash": 0x44,
                    "sortedIndexSample": "0,1,2,2,3,0",
                },
                {
                    "reason": "translucent-rust-sort-copy",
                    "meshGeneration": -3414063591750951357,
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "rustCopiedSortedIndexHash": 0x12345678,
                    "rustCopiedSortedIndexSampleHash": 0x44,
                    "sortedIndexSample": "0,1,2,2,3,0",
                },
                {
                    "reason": "visible-submit",
                    "meshGeneration": -3414063591750951357,
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                },
                {
                    "reason": "executed-submit",
                    "meshGeneration": -3414063591750951357,
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "executionFrameId": 8,
                    "executionSubmissionId": 9,
                },
            )
        )
        self.assertEqual(evidence["status"], "pass")
        self.assertIsNone(evidence["failure"])

    def test_static_terrain_translucent_truth_accepts_repeated_submit_with_embedded_sort_identity(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(
                {
                    "reason": (
                        "translucent-primitive-accounting"
                        ":sourcePrimitives=6:nonFluidPrimitives=4:waterPrimitives=2:unsupportedPrimitives=0"
                        ":retainedPrimitives=6:omittedPrimitives=0:executedPrimitives=6"
                        ":sourceIndices=36:retainedIndices=36:omittedIndices=0"
                        ":sourceHash=111:retainedHash=111:omittedHash=222"
                        ":rangeCount=2:sample=0/1/retained/9001/0"
                    )
                },
                {"reason": "visible-submit", "sortGeneration": 102, "visibleGeneration": 102, "indexUploadGeneration": 102, "sortedIndexHash": 0x12345678},
                {
                    "reason": "executed-submit",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sortedIndexHash": 0x12345678,
                    "executionFrameId": 8,
                    "executionSubmissionId": 9,
                },
            )
        )
        self.assertEqual(evidence["status"], "pass")
        self.assertIsNone(evidence["failure"])

    def test_static_terrain_translucent_truth_rejects_bad_primitive_accounting(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(
                {
                    "reason": (
                        "translucent-primitive-accounting"
                        ":sourcePrimitives=3:nonFluidPrimitives=1:waterPrimitives=1:unsupportedPrimitives=1"
                        ":retainedPrimitives=3:omittedPrimitives=0:executedPrimitives=3"
                        ":sourceIndices=18:retainedIndices=18:omittedIndices=0"
                        ":sourceHash=111:retainedHash=111:omittedHash=222"
                        ":rangeCount=1:sample=0/1/retained/9001/0"
                    )
                },
                {
                    "reason": "visible-submit",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sortedIndexHash": 0x12345678,
                },
                {
                    "reason": "executed-submit",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sortedIndexHash": 0x12345678,
                    "executionFrameId": 8,
                    "executionSubmissionId": 9,
                },
            )
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_unsupported_primitive_executed", evidence["failures"])

    def test_static_terrain_translucent_truth_rejects_missing_sort(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc({"reason": "translucent-sort-missing"})
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_sort_missing", evidence["failures"])

    def test_static_terrain_translucent_truth_rejects_cross_world_fault_marker(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(
                {"reason": "translucent-fault-cross-world-sort:translucent-world-different-reload:block=1, 2, 3"}
            )
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_cross_world_sort", evidence["failures"])

    def test_static_terrain_translucent_crossing_requires_camera_sort_hash_change(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(
                {"reason": "mesh-registered"},
                {"reason": "visible-submit"},
                {"reason": "executed-submit", "executionFrameId": 8, "executionSubmissionId": 9},
            ),
            require_camera_sort=True,
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_sort_missing", evidence["failures"])

        accepted = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(
                {
                    "reason": (
                        "translucent-primitive-accounting"
                        ":sourcePrimitives=6:nonFluidPrimitives=4:waterPrimitives=2:unsupportedPrimitives=0"
                        ":retainedPrimitives=6:omittedPrimitives=0:executedPrimitives=6"
                        ":sourceIndices=36:retainedIndices=36:omittedIndices=0"
                        ":sourceHash=111:retainedHash=111:omittedHash=222"
                        ":rangeCount=2:sample=0/1/retained/9001/0"
                    )
                },
                {
                    "reason": "translucent-source-sort",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sortedIndexHash": 0x12345678,
                    "sourceSortedIndexHash": 0x12345678,
                    "sourceSortedIndexSampleHash": 0x44,
                    "sortedIndexSample": "0,1,2,2,3,0",
                },
                {
                    "reason": "translucent-rust-sort-copy",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sortedIndexHash": 0x12345678,
                    "rustCopiedSortedIndexHash": 0x12345678,
                    "rustCopiedSortedIndexSampleHash": 0x44,
                    "sortedIndexSample": "0,1,2,2,3,0",
                },
                {
                    "reason": "translucent-source-sort",
                    "sortGeneration": 103,
                    "visibleGeneration": 103,
                    "indexUploadGeneration": 103,
                    "sortedIndexHash": 0x87654321,
                    "sourceSortedIndexHash": 0x87654321,
                    "sourceSortedIndexSampleHash": 0x55,
                    "sortedIndexSample": "4,5,6,6,7,4",
                },
                {
                    "reason": "translucent-rust-sort-copy",
                    "sortGeneration": 103,
                    "visibleGeneration": 103,
                    "indexUploadGeneration": 103,
                    "sortedIndexHash": 0x87654321,
                    "rustCopiedSortedIndexHash": 0x87654321,
                    "rustCopiedSortedIndexSampleHash": 0x55,
                    "sortedIndexSample": "4,5,6,6,7,4",
                },
                {
                    "reason": "visible-submit",
                    "sortGeneration": 103,
                    "visibleGeneration": 103,
                    "indexUploadGeneration": 103,
                    "sortedIndexHash": 0x87654321,
                },
                {
                    "reason": "executed-submit",
                    "sortGeneration": 103,
                    "visibleGeneration": 103,
                    "indexUploadGeneration": 103,
                    "sortedIndexHash": 0x87654321,
                    "executionFrameId": 8,
                    "executionSubmissionId": 9,
                },
            ),
            require_camera_sort=True,
        )
        self.assertEqual(accepted["status"], "pass")

    def test_static_terrain_translucent_truth_rejects_payload_corruption(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc(
                {"reason": "mesh-registered"},
                {
                    "reason": "translucent-source-sort",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "sourceSortedIndexHash": 11,
                    "sourceSortedIndexSampleHash": 44,
                    "sortedIndexSample": "0,1,2,2,3,0",
                },
                {
                    "reason": "translucent-rust-sort-copy",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "rustCopiedSortedIndexHash": 22,
                    "rustCopiedSortedIndexSampleHash": 55,
                    "sortedIndexSample": "3,2,1,1,0,3",
                },
                {"reason": "visible-submit", "sortGeneration": 102, "visibleGeneration": 102, "indexUploadGeneration": 102},
                {
                    "reason": "executed-submit",
                    "sortGeneration": 102,
                    "visibleGeneration": 102,
                    "indexUploadGeneration": 102,
                    "executionFrameId": 8,
                    "executionSubmissionId": 9,
                },
            )
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_source_sort_mismatch", evidence["failures"])
        self.assertIn("terrain_translucent_sort_payload_corrupt", evidence["failures"])

    def test_static_terrain_translucent_truth_rejects_bad_index_type(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            self.static_terrain_translucent_doc({"reason": "visible-submit", "indexType": 1})
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_index_invalid", evidence["failures"])

    def test_static_terrain_translucent_truth_rejects_duplicate_draw_order(self) -> None:
        baseline = self.static_terrain_translucent_doc()
        first = dict(baseline["translucentEvents"][2])
        second = dict(first)
        second["sectionPos"] = 987654321
        second["meshKey"] = 22
        second["meshGeneration"] = 202
        second["visibleGeneration"] = 202
        second["sortGeneration"] = 202
        doc = {"translucentEvents": [baseline["translucentEvents"][0], baseline["translucentEvents"][1], first, second]}
        evidence = harness.static_terrain_translucent_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_section_order_invalid", evidence["failures"])

    def test_static_terrain_translucent_truth_rejects_old_new_overlap(self) -> None:
        doc = self.static_terrain_translucent_doc()
        stale_execute = dict(doc["translucentEvents"][-1])
        stale_execute["sortGeneration"] = 101
        stale_execute["sortedIndexHash"] = 0x99999999
        doc["translucentEvents"].append(stale_execute)
        evidence = harness.static_terrain_translucent_evidence(doc)
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_old_new_overlap", evidence["failures"])

    def test_static_terrain_translucent_truth_maps_fault_markers(self) -> None:
        cases = {
            "sort-reversed": "terrain_translucent_sort_reversed",
            "sort-stale": "terrain_translucent_sort_stale",
            "sort-missing": "terrain_translucent_sort_missing",
            "duplicate-primitive": "terrain_translucent_duplicate_primitive",
            "index-out-of-range": "terrain_translucent_index_invalid",
            "index-type-invalid": "terrain_translucent_index_invalid",
            "index-alignment-invalid": "terrain_translucent_index_invalid",
            "sort-section-mismatch": "terrain_translucent_sort_section_mismatch",
            "old-new-overlap": "terrain_translucent_old_new_overlap",
            "section-order-reversed": "terrain_translucent_section_order_invalid",
            "depth-write-enabled": "terrain_translucent_depth_policy_invalid",
            "opaque-blend": "terrain_translucent_blend_policy_invalid",
            "cross-world-sort": "terrain_translucent_cross_world_sort",
            "source-sort-mismatch": "terrain_translucent_source_sort_mismatch",
            "sort-payload-corrupt": "terrain_translucent_sort_payload_corrupt",
            "primitive-metadata-missing": "terrain_translucent_primitive_metadata_missing",
            "primitive-kind-unknown": "terrain_translucent_primitive_unknown",
            "primitive-range-overlap": "terrain_translucent_primitive_range_overlap",
            "primitive-range-out-of-bounds": "terrain_translucent_primitive_range_out_of_bounds",
            "primitive-classification-swapped": "terrain_translucent_primitive_classification_swapped",
            "supported-primitive-omitted": "terrain_translucent_supported_primitive_omitted",
            "unsupported-primitive-executed": "terrain_translucent_unsupported_primitive_executed",
            "source-index-unknown-primitive": "terrain_translucent_source_index_unknown_primitive",
            "retained-index-duplicated": "terrain_translucent_retained_index_duplicated",
            "filtered-order-changed": "terrain_translucent_filtered_order_changed",
            "ordered-range-material-mismatch": "terrain_translucent_ordered_range_material_mismatch",
            "ordered-range-overlap": "terrain_translucent_ordered_range_overlap",
            "execution-order-hash-mismatch": "terrain_translucent_execution_order_hash_mismatch",
            "cross-generation-metadata": "terrain_translucent_cross_generation_metadata",
            "cross-world-primitive-range-reuse": "terrain_translucent_cross_world_primitive_range_reuse",
            "water-still-flow-texture-swapped": "terrain_water_still_flow_texture_swapped",
            "water-overlay-identity-invalid": "terrain_water_overlay_identity_invalid",
            "water-animation-entry-mismatch": "terrain_animation_frame_order_invalid",
            "water-animation-invalid-frame": "terrain_animation_frame_order_invalid",
            "water-corner-height-invalid": "terrain_water_geometry_invalid",
            "water-depth-write-enabled": "terrain_water_depth_policy_invalid",
            "water-opaque-blend": "terrain_water_blend_policy_invalid",
            "water-glass-range-mismatch": "terrain_water_sort_range_invalid",
        }
        for fault, expected in cases.items():
            with self.subTest(fault=fault):
                evidence = harness.static_terrain_translucent_evidence(
                    self.static_terrain_translucent_doc({"reason": "translucent-fault-" + fault + ":diagnostic"})
                )
                self.assertEqual(evidence["status"], "fail")
                self.assertIn(expected, evidence["failures"])

    def test_static_terrain_translucent_truth_requires_water_identity_metadata(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(
            {
                "translucentEvents": [
                    {
                        **self.static_terrain_translucent_doc()["translucentEvents"][0],
                        "reason": (
                            "translucent-primitive-accounting"
                            ":sourcePrimitives=2:nonFluidPrimitives=0:waterPrimitives=2:unsupportedPrimitives=0"
                            ":retainedPrimitives=2:omittedPrimitives=0:executedPrimitives=2"
                            ":sourceIndices=12:retainedIndices=12:omittedIndices=0"
                            ":sourceHash=111:retainedHash=111:omittedHash=222"
                            ":rangeCount=1:sample=0/2/retained/9002/0"
                        ),
                    },
                    *self.static_terrain_translucent_doc()["translucentEvents"][1:],
                ]
            }
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_water_animation_entry_mismatch", evidence["failures"])

    def test_static_terrain_translucent_truth_records_water_identity_diagnostics(self) -> None:
        evidence = harness.static_terrain_translucent_evidence(self.static_terrain_translucent_doc())
        self.assertEqual(evidence["status"], "pass")
        diagnostics = evidence["primitive_accounting_diagnostics"]
        self.assertGreaterEqual(diagnostics[0]["waterStillPrimitives"], 1)
        self.assertGreaterEqual(diagnostics[0]["waterFlowPrimitives"], 1)
        self.assertGreater(diagnostics[0]["waterAnimationHash"], 0)
        self.assertIn("water_still", diagnostics[0]["waterAnimationEntries"])

    def test_static_terrain_translucent_camera_sequence_accepts_crossing(self) -> None:
        names = [
            "translucent-front",
            "translucent-lateral",
            "translucent-orbit-left",
            "translucent-cross-opposite",
            "translucent-above",
            "translucent-below",
            "translucent-return",
        ]
        positions = [
            (0.0, 70.0, 0.0),
            (4.0, 70.0, 0.0),
            (0.0, 70.0, 0.0),
            (10.0, 70.0, 0.0),
            (4.0, 75.0, 0.0),
            (4.0, 66.0, 0.0),
            (0.0, 70.0, 0.0),
        ]
        captures = [
            {"poseName": name, "position": {"x": x, "y": y, "z": z}}
            for name, (x, y, z) in zip(names, positions, strict=True)
        ]
        evidence = harness.static_terrain_translucent_camera_sequence_evidence(
            {"poseSequence": names, "captures": captures}
        )
        self.assertEqual(evidence["status"], "pass")
        self.assertGreaterEqual(evidence["horizontal_span"], 8.0)
        self.assertGreaterEqual(evidence["vertical_span"], 8.0)

    def test_static_terrain_translucent_camera_sequence_rejects_single_static_pose(self) -> None:
        evidence = harness.static_terrain_translucent_camera_sequence_evidence(
            {
                "poseSequence": ["initial"],
                "captures": [
                    {
                        "poseName": "initial",
                        "position": {"x": 0.0, "y": 70.0, "z": 0.0},
                    }
                ],
            }
        )
        self.assertEqual(evidence["status"], "fail")
        self.assertIn("terrain_translucent_camera_sequence_missing", evidence["failures"])
        self.assertIn("terrain_translucent_camera_sequence_no_crossing", evidence["failures"])

    def test_static_terrain_lifecycle_skips_base_translucent_fixture(self) -> None:
        evidence = harness.static_terrain_lifecycle_evidence({}, {}, "translucent-glass")
        self.assertEqual(evidence["status"], "skip")
        self.assertIsNone(evidence["failure"])

    def test_static_terrain_lifecycle_allows_translucent_prefixed_air_placement(self) -> None:
        diagnostics = {
            "lifecycleEvents": [
                {"reason": "lifecycle-edit-before:translucent-interior-edit:block=1, 2, 3:generation=0"},
                {"reason": "lifecycle-edit-applied:translucent-interior-edit:block=1, 2, 3:state=minecraft:blue_stained_glass"},
                {"reason": "lifecycle-edit-after:translucent-interior-edit:block=1, 2, 3:before=0:after=44:waitFrames=4"},
            ],
            "recentEvents": [],
        }
        lifecycle = {
            "stage": "replacement-visible",
            "setup": True,
            "afterRecorded": True,
            "beforeGeneration": 0,
            "afterGeneration": 44,
            "editBlock": "1, 2, 3",
        }
        evidence = harness.static_terrain_lifecycle_evidence(diagnostics, lifecycle, "translucent-interior-edit")
        self.assertEqual(evidence["status"], "pass")
        self.assertIsNone(evidence["failure"])

    def test_static_terrain_lifecycle_accepts_translucent_prefixed_section_reentry(self) -> None:
        diagnostics = {
            "lifecycleEvents": [
                {"reason": "lifecycle-edit-before:translucent-section-reentry:block=1, 2, 3:generation=0"},
                {"reason": "lifecycle-translucent-overlap-placed:translucent-section-reentry:block=1, 2, 3"},
                {"reason": "lifecycle-edit-applied:translucent-section-reentry:block=1, 2, 3:state=minecraft:blue_stained_glass"},
                {"reason": "lifecycle-section-removed:translucent-section-reentry:block=1, 2, 3"},
                {"reason": "lifecycle-section-reentry-requested:translucent-section-reentry:block=1, 2, 3"},
                {"reason": "lifecycle-edit-after:translucent-section-reentry:block=1, 2, 3:before=0:after=55:waitFrames=4"},
            ],
            "recentEvents": [],
        }
        lifecycle = {
            "stage": "replacement-visible",
            "setup": True,
            "afterRecorded": True,
            "beforeGeneration": 0,
            "afterGeneration": 55,
            "editBlock": "1, 2, 3",
        }
        evidence = harness.static_terrain_lifecycle_evidence(diagnostics, lifecycle, "translucent-section-reentry")
        self.assertEqual(evidence["status"], "pass")
        self.assertIsNone(evidence["failure"])

    def test_static_terrain_translucent_scenario_alias_policy(self) -> None:
        self.assertEqual("world-different-reload", harness.static_terrain_base_scenario("translucent-world-different-reload"))
        self.assertEqual("steady-state-performance", harness.static_terrain_base_scenario("translucent-moving-camera-performance"))
        self.assertTrue(harness.static_terrain_is_translucent_scenario("translucent-boundary-x-edit"))
        self.assertTrue(harness.static_terrain_requires_translucent_camera_sort("translucent-moving-camera-performance"))
        self.assertFalse(harness.static_terrain_requires_translucent_camera_sort("translucent-section-reentry"))

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

    def test_renderdoc_outline_workload_accepts_reused_depth_attachment(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            capture = Path(temp) / "capture"
            write_capture(capture, backend="vulkan")
            (capture / "runClient_20260101_000000.log").write_text(
                "rust_gal_world_primitive_batches_executed=1 "
                "rust_gal_world_line_segments_executed=12 "
                "rust_gal_world_line_vertices_executed=72 "
                "rust_gal_world_primitive_draws_executed=1 "
                "rust_gal_world_depth_attachment_creates=0 "
                "rust_gal_world_depth_attachment_reuses=3\n",
                encoding="utf-8",
            )
            proof = harness.renderdoc_workload_proof(capture)
            self.assertTrue(proof["non_zero_outline_workload"])
            self.assertTrue(proof["depth_attachment_evidence"])

    def test_large_log_distant_horizons_marker_is_scanned_beyond_tail(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "runClient.log"
            path.write_text("Distant Horizons client Initialized.\n" + ("x" * (harness.NORMALIZED_LOG_TAIL_BYTES + 2048)), encoding="utf-8")
            marker = re.compile(r"DistantHorizons|Distant Horizons", re.IGNORECASE)
            self.assertTrue(harness.file_contains_regex(path, marker))
            self.assertFalse(harness.dh_state_from_text(harness.file_text(path), {})["present_or_logged"])

    def test_matrix_aggregator_does_not_compare_different_workload_profiles(self) -> None:
        def artifact(profile: str, target: str) -> dict[str, object]:
            return {
                "schema": harness.SCHEMA,
                "tool": "gameplay",
                "mode": {"name": f"{target}-{profile}", "shaders": "on"},
                "repository": {"target": target},
                "benchmark_fingerprint": {
                    "workload_signature": {
                        "workload_profile": profile,
                        "dh": {"present_or_logged": True, "state": "logged"},
                    }
                },
                "implementation_attribution": target,
                "metrics": {"frame_time_ms": {}, "fps": {}},
                "validation": {},
            }

        with tempfile.TemporaryDirectory() as temp:
            paths = []
            for index, value in enumerate((artifact("capture", "current"), artifact("gameplay", "current"))):
                path = Path(temp) / f"{index}.json"
                path.write_text(json.dumps(value), encoding="utf-8")
                paths.append(path)
            self.assertEqual([], harness.aggregate_matrix(paths)["comparison_rejections"])

    def test_title_screen_capture_omits_quick_play_and_disables_world_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            args = harness.parse_args([
                "capture",
                "--profile", "smoke",
                "--mode", "current-rust-vulkan-shaders-off",
                "--title-screen-capture",
            ])
            command, env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "capture", "correctness", args, "capture"
            )
            self.assertNotIn("--quickPlaySingleplayer", " ".join(command))
            self.assertIn("--title-screen-capture", command)
            self.assertNotIn("--deterministic-camera-capture", command)
            self.assertNotIn("MATTMC_GRAPHICS_CORRECTNESS_CAPTURE", env)
            self.assertNotIn("MATTMC_DETERMINISTIC_METADATA", env)
            self.assertNotIn("MATTMC_DETERMINISTIC_SCREENSHOT_DIR", env)
            validation_index = command.index("--validation")
            self.assertEqual("off", command[validation_index + 1])
            self.assertEqual("true", env["MATTMC_TITLE_SCREEN_CAPTURE"])
            self.assertEqual("true", env["MATTMC_TITLE_SCREEN_REQUIRE_RECEIPT"])
            self.assertEqual("true", env["MATTMC_CAPTURE_TITLE_STATIC_FIXTURE"])
            self.assertNotIn("MATTMC_TITLE_SCREEN_CAPTURE_PANORAMA_SPIN", env)
            self.assertEqual("20", env["SCREENSHOT_INTERVAL_SECS"])
            self.assertEqual("20", env["SCREENSHOT_START_DELAY_SECS"])
            self.assertEqual("1", env["SCREENSHOT_MAX_COUNT"])
            timeline_args = harness.parse_args([
                "capture",
                "--profile", "smoke",
                "--mode", "current-rust-vulkan-shaders-off",
                "--title-screen-capture",
                "--title-screen-timeline-frames", "12",
                "--title-screen-timeline-interval-secs", "1",
            ])
            _timeline_command, timeline_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "timeline-capture", "correctness", timeline_args, "capture"
            )
            self.assertEqual("1", timeline_env["SCREENSHOT_INTERVAL_SECS"])
            self.assertEqual("1", timeline_env["SCREENSHOT_START_DELAY_SECS"])
            self.assertEqual("12", timeline_env["SCREENSHOT_MAX_COUNT"])
            transition_args = harness.parse_args([
                "capture",
                "--profile", "smoke",
                "--mode", "current-rust-vulkan-shaders-off",
                "--title-screen-capture",
                "--title-screen-transition-capture",
                "--title-screen-transition-frames", "9",
                "--title-screen-transition-interval-secs", "2",
            ])
            transition_command, transition_env = harness.build_capture_command(
                target, harness.MATRIX_MODES[0], root / "transition-capture", "correctness", transition_args, "capture"
            )
            self.assertIn("--title-screen-transition-capture", transition_command)
            self.assertEqual("true", transition_env["MATTMC_TITLE_SCREEN_TRANSITION_CAPTURE"])
            self.assertNotIn("MATTMC_TITLE_SCREEN_CAPTURE_PANORAMA_SPIN", transition_env)
            self.assertNotIn("MATTMC_CAPTURE_TITLE_STATIC_FIXTURE", transition_env)
            self.assertEqual("2", transition_env["SCREENSHOT_INTERVAL_SECS"])
            self.assertEqual("0", transition_env["SCREENSHOT_START_DELAY_SECS"])
            self.assertEqual("9", transition_env["SCREENSHOT_MAX_COUNT"])
            frozen_target = fake_repo(root, "frozen")
            (frozen_target.root / "DevUtils" / "Common" / "capture_runner.py").unlink()
            (frozen_target.root / "DevUtils" / "Common" / "capture_runner.sh").write_text(
                "#!/usr/bin/env sh\nexit 0\n", encoding="utf-8"
            )
            frozen_mode = next(mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off")
            frozen_command, _ = harness.build_capture_command(
                frozen_target, frozen_mode, root / "frozen-capture", "correctness", args, "capture"
            )
            self.assertNotIn("--quickPlaySingleplayer", " ".join(frozen_command))
            self.assertIn("--width 1280", " ".join(frozen_command))
            self.assertIn("--height 720", " ".join(frozen_command))

    def test_title_screen_normalization_does_not_apply_inherited_static_terrain_gates(self) -> None:
        """A menu frame may retain launcher properties from a world profile.

        Those properties are useful diagnostic context, but a title capture
        cannot produce terrain geometry or translucent-sort evidence.
        """
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="rust-vulkan")
            screenshot = capture / "title.png"
            screenshot.write_bytes(b"not-decoded-by-normalization")
            meta = capture / "meta_20260101_000000.txt"
            meta.write_text(
                meta.read_text(encoding="utf-8")
                + "title_screen_capture=true\n"
                + "title_screen_capture_completed=true\n"
                + f"screenshot_1={screenshot}\n"
                + "screenshot_1_target=client-window\n",
                encoding="utf-8",
            )
            log = capture / "runClient_20260101_000000.log"
            log.write_text(
                log.read_text(encoding="utf-8")
                + "-Dmattmc.dev.rustGalStaticTerrain.scenario=translucent-mixed\n",
                encoding="utf-8",
            )

            artifact = harness.normalize_capture_artifact(
                target, harness.MATRIX_MODES[0], capture, "correctness", True, ["fake"], 0, False
            )
            messages = "\n".join(artifact["validation"]["messages"])
            self.assertNotIn("static-terrain geometry truth gate failed", messages)
            self.assertNotIn("static-terrain translucent gate failed", messages)
            self.assertEqual("translucent-mixed", artifact["metrics"]["rust_gal_slice"]["world_static_terrain_scenario"])

    def test_rust_title_presenter_ack_completes_title_capture_without_desktop_target(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "current")
            capture = root / "capture"
            write_capture(capture, backend="rust-vulkan")
            screenshot = capture / "title_frame.png"
            acknowledgement = capture / "title_frame_capture.ack.json"
            screenshot.write_bytes(b"rust-presented-title")
            acknowledgement.write_text(json.dumps({
                "status": "captured", "screenshot": str(screenshot), "targetWindow": "0x1",
                "windowProvenance": {
                    "schema": "mattmc-window-capture-provenance-v1", "status": "verified",
                    "targetWindow": "0x1", "expectedClientPid": 42, "observedWindowPid": 42,
                },
            }), encoding="utf-8")
            meta = capture / "meta_20260101_000000.txt"
            meta.write_text(
                meta.read_text(encoding="utf-8")
                + "title_screen_capture=true\n"
                + "title_screen_capture_completed=true\n"
                + f"rust_title_presented_frame_screenshot={screenshot}\n"
                + f"rust_title_presented_frame_ack={acknowledgement}\n",
                encoding="utf-8",
            )
            rust_vulkan_mode = next(
                mode for mode in harness.MATRIX_MODES if mode.name == "current-rust-vulkan-shaders-off"
            )
            artifact = harness.normalize_capture_artifact(
                target, rust_vulkan_mode, capture, "correctness", True, ["fake"], 0, False
            )
            self.assertNotIn(
                "title-screen capture did not produce a bounded completion screenshot from the client window",
                artifact["validation"]["messages"],
            )

    def test_frozen_title_presenter_ack_completes_title_capture_without_desktop_target(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = fake_repo(root, "frozen")
            capture = root / "capture"
            write_capture(capture, backend="opengl")
            screenshot = capture / "title_frame.png"
            acknowledgement = capture / "title_frame_capture.ack.json"
            screenshot.write_bytes(b"frozen-presented-title")
            acknowledgement.write_text(
                json.dumps({"status": "captured", "screenshot": str(screenshot), "targetWindow": "0x1",
                    "windowProvenance": {
                        "schema": "mattmc-window-capture-provenance-v1", "status": "verified",
                        "targetWindow": "0x1", "expectedClientPid": 42, "observedWindowPid": 42,
                    }}),
                encoding="utf-8",
            )
            meta = capture / "meta_20260101_000000.txt"
            meta.write_text(
                meta.read_text(encoding="utf-8")
                + "title_screen_capture=true\n"
                + "title_screen_capture_completed=true\n"
                + f"frozen_title_presented_frame_ack={acknowledgement}\n",
                encoding="utf-8",
            )
            frozen_opengl_mode = next(
                mode for mode in harness.MATRIX_MODES if mode.name == "frozen-opengl-shaders-off"
            )
            artifact = harness.normalize_capture_artifact(
                target, frozen_opengl_mode, capture, "correctness", True, ["fake"], 0, False
            )
            self.assertNotIn(
                "title-screen capture did not produce a bounded completion screenshot from the client window",
                artifact["validation"]["messages"],
            )

    def test_cross_repo_visual_pair_rejects_mean_rgb_error_above_tolerance(self) -> None:
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is required for visual parity validation")
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifacts: list[Path] = []
            for name, color in (("frozen", (10, 20, 30)), ("current", (30, 20, 30))):
                run = root / name / "capture"
                shots = run / "shots"
                shots.mkdir(parents=True)
                image_path = shots / "01_initial.png"
                Image.new("RGB", (2, 2), color).save(image_path)
                capture = {
                    "captures": [{
                        "poseName": "initial",
                        "screenshot": str(image_path),
                        "dimension": "minecraft:overworld",
                        "shaderEnabled": False,
                        "gameTime": 1,
                        "requestedYaw": 0.0,
                        "requestedPitch": 0.0,
                        "observedYaw": 0.0,
                        "observedPitch": 0.0,
                        "position": {"x": 1.0, "y": 2.0, "z": 3.0},
                        "window": {"width": 2, "height": 2},
                    }]
                }
                (run / "deterministic_camera_capture_test.json").write_text(json.dumps(capture), encoding="utf-8")
                artifact = root / name / "graphics_audit_artifact.json"
                artifact.write_text("{}", encoding="utf-8")
                artifacts.append(artifact)
            report = harness.write_cross_repo_visual_pairs(
                root / "output",
                {"pairs": [{"baseline_artifact": str(artifacts[0]), "current_artifact": str(artifacts[1])}]},
                mean_rgb_abs_tolerance=6.0,
            )
            self.assertFalse(report["passed"])
            self.assertEqual("visual-mismatch", report["pairs"][0]["status"])
            self.assertEqual([20.0, 0.0, 0.0], report["pairs"][0]["diff"]["mean_rgb_abs"])

    def test_cross_repo_model_crop_pair_uses_rust_projected_bounds_on_frozen_frame(self) -> None:
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is required for visual parity validation")
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifacts: list[Path] = []
            for name in ("frozen", "current"):
                capture_root = root / name / "capture"
                shots = capture_root / "shots"
                shots.mkdir(parents=True)
                image_path = shots / "01_initial.png"
                image = Image.new("RGB", (4, 4), (1, 2, 3))
                for x in range(1, 3):
                    for y in range(1, 3):
                        image.putpixel((x, y), (40, 50, 60))
                image.save(image_path)
                capture = {"captures": [{
                    "poseName": "initial", "screenshot": str(image_path),
                    "dimension": "minecraft:overworld", "requestedYaw": 0.0,
                    "requestedPitch": 0.0, "observedYaw": 0.0, "observedPitch": 0.0,
                    "gameTime": 1,
                    "position": {"x": 1.0, "y": 2.0, "z": 3.0},
                    "window": {"width": 4, "height": 4},
                }]}
                (capture_root / "deterministic_camera_capture_test.json").write_text(json.dumps(capture), encoding="utf-8")
                artifact = root / name / "graphics_audit_artifact.json"
                artifact.write_text(json.dumps({
                    "metrics": {"rust_gal_slice": {"world_mesh_model_capture_evidence": {
                        "crops": ([{
                            "status": "present", "capture_index": 0,
                            "crop_box": [1, 1, 3, 3],
                            "crop_path": str(shots / "model_crop.png"),
                        }] if name == "current" else [])
                    }}}
                }), encoding="utf-8")
                if name == "current":
                    image.crop((1, 1, 3, 3)).save(shots / "model_crop.png")
                artifacts.append(artifact)
            report = harness.write_cross_repo_model_crop_pairs(
                root / "output",
                {"pairs": [{"baseline_artifact": str(artifacts[0]), "current_artifact": str(artifacts[1])}]},
                mean_rgb_abs_tolerance=0.0,
            )
            self.assertTrue(report["passed"], report)
            self.assertEqual("complete", report["pairs"][0]["status"])
            self.assertEqual([1, 1, 3, 3], report["pairs"][0]["crop_box"])

    def test_cross_repo_visual_pair_uses_acknowledged_title_presentation(self) -> None:
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is required for visual parity validation")
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifacts: list[Path] = []
            for name in ("frozen", "current"):
                shot = root / name / "title.png"
                shot.parent.mkdir(parents=True)
                Image.new("RGB", (2, 2), (10, 20, 30)).save(shot)
                artifact = root / name / "graphics_audit_artifact.json"
                artifact.write_text(json.dumps({"capture": {"title_screen": {
                    "requested": True,
                    "completed": True,
                    "semantic_receipt": {"panorama_spin": 7.25},
                    "presented_frame": {"screenshot": str(shot), "ack_status": "captured", "targetWindow": "0x1",
                        "windowProvenance": {"schema": "mattmc-window-capture-provenance-v1", "status": "verified", "targetWindow": "0x1", "expectedClientPid": 42, "observedWindowPid": 42}},
                }}}), encoding="utf-8")
                artifacts.append(artifact)
            report = harness.write_cross_repo_visual_pairs(
                root / "output",
                {"pairs": [{"baseline_artifact": str(artifacts[0]), "current_artifact": str(artifacts[1])}]},
                mean_rgb_abs_tolerance=0.0,
            )
            self.assertTrue(report["passed"])
            self.assertEqual("title-screen-presented-frame", report["pairs"][0]["visual_fixture"])

    def test_cross_repo_title_transition_is_observational_not_visual_acceptance(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifacts: list[Path] = []
            for name in ("frozen", "current"):
                artifact = root / name / "graphics_audit_artifact.json"
                artifact.parent.mkdir(parents=True)
                artifact.write_text(json.dumps({"capture": {"title_screen": {
                    "requested": True,
                    "transition_capture": True,
                    "completed": True,
                    "presented_frame": {"ack_status": "captured"},
                }}}), encoding="utf-8")
                artifacts.append(artifact)
            report = harness.write_cross_repo_visual_pairs(
                root / "output",
                {"pairs": [{"baseline_artifact": str(artifacts[0]), "current_artifact": str(artifacts[1])}]},
                mean_rgb_abs_tolerance=0.0,
            )
            self.assertTrue(report["passed"])
            self.assertEqual("observational-transition", report["pairs"][0]["status"])
            self.assertTrue(report["pairs"][0]["observation_only"])

    def test_cross_repo_title_visual_pair_rejects_missing_acknowledgement(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = root / "graphics_audit_artifact.json"
            artifact.write_text(json.dumps({"capture": {"title_screen": {
                "requested": True, "completed": True, "presented_frame": {"ack_status": "timed-out"},
            }}}), encoding="utf-8")
            evidence = harness.title_visual_fixture_equivalence(artifact, artifact)
            self.assertEqual("failed", evidence["status"])
            self.assertIn("baseline-title-presentation-not-acknowledged", evidence["mismatches"])

    def test_title_visual_pair_rejects_unverified_client_window(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            shot = root / "title.png"
            shot.write_bytes(b"title")
            artifact = root / "graphics_audit_artifact.json"
            artifact.write_text(json.dumps({"capture": {"title_screen": {
                "requested": True, "completed": True,
                "presented_frame": {"screenshot": str(shot), "ack_status": "captured", "targetWindow": "0x1"},
            }}}), encoding="utf-8")
            evidence = harness.title_visual_fixture_equivalence(artifact, artifact)
            self.assertEqual("failed", evidence["status"])
            self.assertIn("baseline-title-presentation-window-unverified", evidence["mismatches"])

    def test_cross_repo_title_visual_pair_rejects_mismatched_panorama_timing(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifacts: list[Path] = []
            for name, spin in (("frozen", 9.99), ("current", 10.03)):
                shot = root / name / "title.png"
                shot.parent.mkdir(parents=True)
                shot.write_bytes(b"title")
                artifact = root / name / "graphics_audit_artifact.json"
                artifact.write_text(json.dumps({"capture": {"title_screen": {
                    "requested": True,
                    "completed": True,
                    "presented_frame": {
                        "screenshot": str(shot), "ack_status": "captured", "panorama_spin": spin,
                    },
                }}}), encoding="utf-8")
                artifacts.append(artifact)
            evidence = harness.title_visual_fixture_equivalence(*artifacts)
            self.assertEqual("failed", evidence["status"])
            self.assertIn("panorama_spin", evidence["mismatches"])

    def test_title_semantic_receipt_exposes_panorama_timing(self) -> None:
        receipt = harness.title_semantic_receipt_from_log(
            "[MattMC graphics audit] title-screen semantic receipt fadeAlpha=1.0 splash=true fading=false panoramaSpin=7.25\n"
        )
        self.assertEqual(1.0, receipt["fade_alpha"])
        self.assertTrue(receipt["splash_present"])
        self.assertEqual(7.25, receipt["panorama_spin"])

    def test_frozen_capture_runner_rejects_desktop_root_for_baseline_evidence(self) -> None:
        runner = (
            Path(__file__).resolve().parents[3]
            / "MattMC_JavaPerfTesting"
            / "MattMC"
            / "DevUtils"
            / "Common"
            / "capture_runner.sh"
        )
        source = runner.read_text(encoding="utf-8")
        self.assertIn("screenshot_rejected_root_target", source)
        self.assertIn("deterministic_capture_rejected_root_target", source)
        self.assertNotIn('else\n        target_window="root"', source)
        self.assertNotIn('else\n        target_window="root"\n    fi\n\n    if import', source)


if __name__ == "__main__":
    unittest.main()
