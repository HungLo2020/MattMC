#!/usr/bin/env python3
"""Self-tests for the hand-played session recording summary."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import Recording


FRAME_HEADER = ("frame,frame_id,submission_id,execute_start_ns,acquire_start_ns,acquire_end_ns,"
                "submit_start_ns,submit_end_ns,present_start_ns,present_end_ns,worldMeshInstanceCount,"
                "profile.vulkanPresentMode,profile.vulkanSupportedPresentModes,profile.vulkanSwapchainImageCount,"
                "profile.vulkanConfiguredFramesInFlight,profile.vulkanPresentWaitNanos,profile.gpuFrameTotalNanos\n")


def write_recording(directory: Path) -> None:
    rows = []
    t = 0
    for frame in range(120):
        # Every 40th frame stalls 150 ms in present wait; others take 10 ms.
        stall = frame % 40 == 39
        interval = 150_000_000 if stall else 10_000_000
        t += interval
        wait = 140_000_000 if stall else 1_000_000
        rows.append(f"{frame},{frame},{frame},{t - 9_000_000},{t - 8_000_000},{t - 7_900_000},{t - 7_000_000},"
                    f"{t - 6_000_000},{t - 5_000_000},{t},50,2,{(1 << 2) | (1 << 1)},3,2,{wait},4000000\n")
    (directory / "frames.csv").write_text(FRAME_HEADER + "".join(rows), encoding="utf-8")
    ticks = ["tick,start_ns,interval_ns,duration_ns,phases\n"]
    for tick in range(100):
        slow = tick == 50
        ticks.append(f"{tick},{tick * 10_000_000},{80_000_000 if slow else 10_000_000},9000000,"
                     f"0:{70_000_000 if slow else 2_000_000};1:3000000\n")
    (directory / "ticks.csv").write_text("".join(ticks), encoding="utf-8")
    (directory / "phases.csv").write_text("id,name\n0,game.level-render\n1,rust-gal.frame.submit\n", encoding="utf-8")
    seconds = ["t_ns,gc_count,gc_ms,heap_used_mb,heap_committed_mb,process_cpu_pct,system_cpu_pct,jit_ms,threads,"
               "dropped_ticks,dropped_frames,fps_counter,vsync,max_fps,render_distance,graphics_mode,window_w,"
               "window_h,fullscreen,screen,in_world\n"]
    for second in range(3):
        seconds.append(f"{second * 1_000_000_000},{second},{second * 5},800,2048,12.5,30.0,100,60,0,0,"
                       f"90,true,120,12,FANCY,1920,1080,false,none,true\n")
    (directory / "seconds.csv").write_text("".join(seconds), encoding="utf-8")
    (directory / "console.log").write_text(
        "noise\nMattMC Vulkan device selection: [NVIDIA GeForce RTX 2070 type=discrete graphics_present=true "
        "score=3 SELECTED] [llvmpipe type=cpu graphics_present=true score=1]\n", encoding="utf-8")


class SummaryTest(unittest.TestCase):
    def test_summary_reports_device_settings_present_mode_and_stalls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            write_recording(directory)
            text = Recording.summarize(directory)
            self.assertTrue((directory / "summary.md").is_file())
            self.assertIn("RTX 2070 type=discrete graphics_present=true score=3 SELECTED", text)
            self.assertIn("vsync=true max_fps=120 render_distance=12", text)
            self.assertIn("FIFO (vsync): 120", text)
            self.assertIn("supported: MAILBOX, FIFO", text)
            self.assertIn("over 100 ms: 3", text)
            self.assertIn("Vulkan present wait", text)
            self.assertIn("interval   150.0 ms: Vulkan present wait 140.0", text)
            # The slow loop iteration is attributed to its dominant phase.
            slow = text.split("slowest 1% of iterations")[1]
            self.assertIn("70.000  game.level-render", slow)

    def test_missing_files_still_produce_a_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            text = Recording.summarize(Path(temp))
            self.assertIn("no frames.csv rows", text)


if __name__ == "__main__":
    unittest.main()
