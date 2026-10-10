#!/usr/bin/env python3
"""Hand-played session recordings for performance diagnosis.

`python3 DevUtils/RunDev.py --record` uses this module to sample the GPU and
system while you play, save the client console, and summarize afterwards.
Re-summarize an existing recording with:

    python3 DevUtils/PerfAudit/Recording.py summarize artifacts/recordings/<name>
"""

from __future__ import annotations

import argparse
import csv
import datetime as _dt
import os
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

PRESENT_MODES = {0: "IMMEDIATE", 1: "MAILBOX", 2: "FIFO (vsync)", 3: "FIFO_RELAXED"}
SUPPORTED_BITS = {0: "IMMEDIATE", 1: "MAILBOX", 2: "FIFO", 3: "FIFO_RELAXED"}
NS_PER_MS = 1_000_000.0


def new_recording_dir(root: Path, label: str | None) -> Path:
    stamp = _dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    name = f"{stamp}-{label}" if label else stamp
    directory = root / "artifacts" / "recordings" / name
    directory.mkdir(parents=True, exist_ok=False)
    return directory


# ----- samplers while the client runs -----

class Samplers:
    """Once-a-second GPU (nvidia-smi) and system/client-process CPU samples."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.stop_event = threading.Event()
        self.gpu: subprocess.Popen | None = None
        self.gpu_file = None
        self.cpu_thread = threading.Thread(target=self._cpu_loop, name="recording-cpu", daemon=True)

    def start(self) -> None:
        nvidia_smi = shutil.which("nvidia-smi")
        if nvidia_smi:
            self.gpu_file = open(self.directory / "gpu.csv", "w", encoding="utf-8")
            self.gpu = subprocess.Popen(
                [nvidia_smi,
                 "--query-gpu=timestamp,name,pstate,utilization.gpu,utilization.memory,clocks.gr,"
                 "clocks.mem,power.draw,temperature.gpu,memory.used",
                 "--format=csv,nounits", "-lms", "1000"],
                stdout=self.gpu_file, stderr=subprocess.DEVNULL)
        self.cpu_thread.start()

    def stop(self) -> None:
        self.stop_event.set()
        if self.gpu is not None:
            self.gpu.terminate()
            try:
                self.gpu.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.gpu.kill()
        if self.gpu_file is not None:
            self.gpu_file.close()
        self.cpu_thread.join(timeout=5)

    def _cpu_loop(self) -> None:
        stat = Path("/proc/stat")
        if not stat.is_file():
            return
        ticks_per_second = os.sysconf("SC_CLK_TCK")
        previous = _read_cpu_totals()
        previous_process: tuple[int, int] | None = None
        with open(self.directory / "cpu.csv", "w", encoding="utf-8") as out:
            out.write("epoch_s,system_busy_pct,iowait_pct,load1,client_pid,client_cpu_pct,client_rss_mb,client_threads\n")
            last = time.monotonic()
            while not self.stop_event.wait(1.0):
                now = time.monotonic()
                current = _read_cpu_totals()
                busy, iowait = _cpu_percentages(previous, current)
                previous = current
                pid = _client_pid()
                client_cpu = rss = threads = ""
                if pid is not None:
                    sample = _process_sample(pid)
                    if sample is not None:
                        jiffies, rss_kb, thread_count = sample
                        if previous_process is not None and previous_process[0] == pid:
                            elapsed = max(now - last, 1e-6)
                            client_cpu = f"{100.0 * (jiffies - previous_process[1]) / ticks_per_second / elapsed:.1f}"
                        previous_process = (pid, jiffies)
                        rss, threads = f"{rss_kb / 1024:.0f}", str(thread_count)
                last = now
                load1 = os.getloadavg()[0]
                out.write(f"{time.time():.1f},{busy:.1f},{iowait:.1f},{load1:.2f},{pid or ''},{client_cpu},{rss},{threads}\n")
                out.flush()


def _read_cpu_totals() -> list[int]:
    with open("/proc/stat", encoding="utf-8") as stat:
        return [int(value) for value in stat.readline().split()[1:]]


def _cpu_percentages(before: list[int], after: list[int]) -> tuple[float, float]:
    delta = [b - a for a, b in zip(before, after)]
    total = sum(delta) or 1
    idle = delta[3] + (delta[4] if len(delta) > 4 else 0)
    iowait = delta[4] if len(delta) > 4 else 0
    return 100.0 * (total - idle) / total, 100.0 * iowait / total


def _client_pid() -> int | None:
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        try:
            argv = (entry / "cmdline").read_bytes().split(b"\0")
        except OSError:
            continue
        # Match the client JVM itself, never a shell whose command line merely
        # mentions these strings.
        if (argv and Path(argv[0].decode(errors="replace")).name.startswith("java")
                and any(b"KnotClient" in arg for arg in argv)
                and any(b"mattmc.dev.recordDir" in arg for arg in argv)):
            return int(entry.name)
    return None


def _process_sample(pid: int) -> tuple[int, int, int] | None:
    try:
        fields = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
        status = Path(f"/proc/{pid}/status").read_text()
    except OSError:
        return None
    jiffies = int(fields[11]) + int(fields[12])
    rss = threads = 0
    for line in status.splitlines():
        if line.startswith("VmRSS:"):
            rss = int(line.split()[1])
        elif line.startswith("Threads:"):
            threads = int(line.split()[1])
    return jiffies, rss, threads


def run_with_console(command: list[str], cwd: Path, env: dict[str, str], log: Path) -> int:
    """Runs the client, showing its console live and saving a copy."""
    with open(log, "w", encoding="utf-8", errors="replace") as out:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, errors="replace", bufsize=1)
        assert process.stdout is not None
        try:
            for line in process.stdout:
                sys.stdout.write(line)
                out.write(line)
        except KeyboardInterrupt:
            process.terminate()
        return process.wait()


# ----- summary -----

def _percentile(values: list[float], fraction: float) -> float:
    if not values:
        return float("nan")
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(fraction * (len(ordered) - 1))))
    return ordered[index]


def _stats(values: list[float]) -> str:
    if not values:
        return "n/a"
    mean = sum(values) / len(values)
    return (f"mean {mean:7.2f}  p50 {_percentile(values, 0.5):7.2f}  p95 {_percentile(values, 0.95):7.2f}  "
            f"p99 {_percentile(values, 0.99):7.2f}  max {max(values):8.2f}")


def _read_csv(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        return []
    with open(path, encoding="utf-8", errors="replace", newline="") as handle:
        return list(csv.DictReader(handle))


def _num(row: dict[str, str], key: str, default: float = float("nan")) -> float:
    try:
        return float(row[key])
    except (KeyError, TypeError, ValueError):
        return default


def summarize(directory: Path) -> str:
    lines: list[str] = [f"# Session recording {directory.name}", ""]
    console = directory / "console.log"
    if console.is_file():
        selections = [line.strip() for line in console.read_text(errors="replace").splitlines()
                      if "MattMC Vulkan device selection" in line]
        lines.append("## Device")
        lines.extend(f"- {line}" for line in selections[-2:]) if selections else lines.append(
            "- no device-selection line in console.log")
        lines.append("")

    seconds = _read_csv(directory / "seconds.csv")
    in_world = [row for row in seconds if row.get("in_world") == "true"]
    if seconds:
        last = (in_world or seconds)[-1]
        lines += ["## Settings (last in-world sample)" if in_world else "## Settings (last sample; never in a world)",
                  f"- vsync={last.get('vsync')} max_fps={last.get('max_fps')} render_distance={last.get('render_distance')} "
                  f"graphics_mode={last.get('graphics_mode')} window={last.get('window_w')}x{last.get('window_h')} "
                  f"fullscreen={last.get('fullscreen')}", ""]
        vsync_values = sorted({row.get("vsync") for row in in_world})
        if len(vsync_values) > 1:
            lines += ["- vsync changed during the session; compare windows separately.", ""]

    frames = _read_csv(directory / "frames.csv")
    if frames:
        lines += _frame_section(frames)
    else:
        lines += ["## Frames", "- no frames.csv rows (the Rust whole-frame path did not record)", ""]

    lines += _tick_section(directory)

    if seconds:
        sampled = in_world or seconds
        gc_ms = [_num(row, "gc_ms") for row in seconds]
        per_second_gc = [b - a for a, b in zip(gc_ms, gc_ms[1:]) if b == b and a == a]
        cpu = [_num(row, "process_cpu_pct") for row in sampled if _num(row, "process_cpu_pct") >= 0]
        heap = [_num(row, "heap_used_mb") for row in sampled]
        dropped = max(_num(row, "dropped_frames", 0) + _num(row, "dropped_ticks", 0) for row in seconds)
        lines += ["## JVM (per second, in world)" if in_world else "## JVM (per second, whole session)",
                  f"- GC ms/s: {_stats(per_second_gc)}",
                  f"- client CPU % (all cores = 100): {_stats(cpu)}",
                  f"- heap used MB: {_stats(heap)}",
                  f"- recorder dropped rows: {dropped:.0f}", ""]

    gpu = _read_csv(directory / "gpu.csv")
    if gpu:
        key = next((k for k in gpu[0] if "utilization.gpu" in k), None)
        clock = next((k for k in gpu[0] if "clocks.current.graphics" in k or "clocks.gr" in k), None)
        utilization = [_num(row, key) for row in gpu] if key else []
        clocks = [_num(row, clock) for row in gpu] if clock else []
        pstates = sorted({row.get(next((k for k in row if "pstate" in k), ""), "").strip() for row in gpu})
        lines += ["## NVIDIA GPU (nvidia-smi, per second)",
                  f"- utilization %: {_stats([u for u in utilization if u == u])}",
                  f"- graphics clock MHz: {_stats([c for c in clocks if c == c])}",
                  f"- performance states seen: {', '.join(p for p in pstates if p)}", ""]

    cpu_rows = _read_csv(directory / "cpu.csv")
    if cpu_rows:
        client = [_num(row, "client_cpu_pct") for row in cpu_rows if row.get("client_cpu_pct")]
        lines += ["## System (per second)",
                  f"- system busy %: {_stats([_num(r, 'system_busy_pct') for r in cpu_rows])}",
                  f"- iowait %: {_stats([_num(r, 'iowait_pct') for r in cpu_rows])}",
                  f"- client process CPU % (one core = 100): {_stats(client)}", ""]

    text = "\n".join(lines) + "\n"
    (directory / "summary.md").write_text(text, encoding="utf-8")
    return text


FRAME_BREAKDOWN = [
    ("acquire (Java call)", None, ("acquire_start_ns", "acquire_end_ns")),
    ("submit (Java call)", None, ("submit_start_ns", "submit_end_ns")),
    ("present (Java call)", None, ("present_start_ns", "present_end_ns")),
    ("Rust world frontend", "profile.worldFrontendTotalNanos", None),
    ("Rust GUI frontend", "profile.guiFrontendNanos", None),
    ("GAL submit total", "profile.galSubmitTotalNanos", None),
    ("Vulkan queue submit", "profile.vulkanQueueSubmitNanos", None),
    ("Vulkan timeline wait", "profile.vulkanTimelineWaitNanos", None),
    ("Vulkan device wait idle", "profile.vulkanDeviceWaitIdleNanos", None),
    ("Vulkan acquire", "profile.vulkanAcquireNanos", None),
    ("Vulkan present", "profile.vulkanPresentNanos", None),
    ("Vulkan present wait", "profile.vulkanPresentWaitNanos", None),
    ("GPU frame (timestamps)", "profile.gpuFrameTotalNanos", None),
]


def _frame_section(frames: list[dict[str, str]]) -> list[str]:
    ends = [_num(row, "present_end_ns") for row in frames]
    intervals = [(b - a) / NS_PER_MS for a, b in zip(ends, ends[1:]) if b > a]
    duration = (ends[-1] - ends[0]) / 1e9 if len(ends) > 1 else 0.0
    lines = ["## Presented frames",
             f"- {len(frames)} frames over {duration:.1f} s = {len(frames) / duration if duration else 0:.1f} FPS average",
             f"- frame interval ms: {_stats(intervals)}",
             f"- intervals over 33 ms: {sum(i > 33 for i in intervals)}, over 100 ms: {sum(i > 100 for i in intervals)}"]
    modes = {}
    for row in frames:
        mode = int(_num(row, "profile.vulkanPresentMode", -1))
        modes[mode] = modes.get(mode, 0) + 1
    mode_text = ", ".join(f"{PRESENT_MODES.get(m, m)}: {n}" for m, n in sorted(modes.items()))
    last = frames[-1]
    supported = 0
    for row in frames:
        try:  # an exact 64-bit mask; float parsing would drop the low bits
            supported |= max(0, int(row.get("profile.vulkanSupportedPresentModes") or 0))
        except ValueError:
            pass
    names = [name for bit, name in SUPPORTED_BITS.items() if supported & (1 << bit)]
    if supported & (1 << 60):
        names.append("other")
    supported_text = ", ".join(names) or "unknown"
    lines += [f"- present modes used: {mode_text}; supported: {supported_text}; "
              f"swapchain images: {last.get('profile.vulkanSwapchainImageCount')}; "
              f"frames in flight: {last.get('profile.vulkanConfiguredFramesInFlight')}", "",
              "## Per-frame breakdown (ms)"]
    for label, column, pair in FRAME_BREAKDOWN:
        if column is not None:
            if column not in frames[0]:
                continue
            values = [_num(row, column) / NS_PER_MS for row in frames if _num(row, column) >= 0]
        else:
            start, end = pair
            values = [(_num(row, end) - _num(row, start)) / NS_PER_MS for row in frames]
        if any(v > 0 for v in values):
            lines.append(f"- {label:26s} {_stats(values)}")
    counts = [("draws", "profile.drawOps"), ("indexed draws", "profile.drawIndexedOps"),
              ("pipeline binds", "profile.pipelineBinds"), ("host write MB", "profile.hostWriteBytes"),
              ("world mesh instances", "worldMeshInstanceCount")]
    lines += ["", "## Per-frame work"]
    for label, column in counts:
        if column in frames[0]:
            scale = 1 / 1048576 if "Bytes" in column else 1
            lines.append(f"- {label:26s} {_stats([_num(row, column) * scale for row in frames])}")
    worst = sorted(range(1, len(frames)), key=lambda i: ends[i] - ends[i - 1], reverse=True)[:10]
    lines += ["", "## Ten slowest frame intervals"]
    for i in sorted(worst):
        row = frames[i]
        parts = []
        for label, column, _ in FRAME_BREAKDOWN:
            if column and column in row and _num(row, column) / NS_PER_MS >= 1:
                parts.append(f"{label} {_num(row, column) / NS_PER_MS:.1f}")
        lines.append(f"- t={ends[i] / 1e9:8.2f}s interval {(ends[i] - ends[i - 1]) / NS_PER_MS:7.1f} ms: "
                     + (", ".join(parts) or "no native stage over 1 ms (time spent in Java)"))
    lines.append("")
    return lines


def _tick_section(directory: Path) -> list[str]:
    ticks_path = directory / "ticks.csv"
    if not ticks_path.is_file():
        return []
    names = {row["id"]: row["name"] for row in _read_csv(directory / "phases.csv")}
    intervals: list[float] = []
    totals: dict[str, float] = {}
    rows: list[tuple[float, dict[str, int]]] = []
    with open(ticks_path, encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            interval = _num(row, "interval_ns")
            phases = {}
            for item in (row.get("phases") or "").split(";"):
                if ":" in item:
                    key, value = item.split(":", 1)
                    phases[key] = int(value)
                    totals[key] = totals.get(key, 0.0) + int(value)
            if interval > 0:
                intervals.append(interval / NS_PER_MS)
                rows.append((interval, phases))
    if not rows:
        return []
    count = len(rows)
    lines = ["## Client loop (Minecraft.runTick)",
             f"- {count} iterations, interval ms: {_stats(intervals)}", "",
             "### Phases by mean exclusive ms per iteration"]
    for key, total in sorted(totals.items(), key=lambda item: item[1], reverse=True)[:15]:
        lines.append(f"- {total / count / NS_PER_MS:7.3f}  {names.get(key, key)}")
    slow = sorted(rows, key=lambda item: item[0], reverse=True)[:max(1, count // 100)]
    slow_totals: dict[str, float] = {}
    for _, phases in slow:
        for key, value in phases.items():
            slow_totals[key] = slow_totals.get(key, 0.0) + value
    lines += ["", f"### Phases in the slowest 1% of iterations ({len(slow)}), mean exclusive ms"]
    for key, total in sorted(slow_totals.items(), key=lambda item: item[1], reverse=True)[:10]:
        lines.append(f"- {total / len(slow) / NS_PER_MS:7.3f}  {names.get(key, key)}")
    lines.append("")
    return lines


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    summary = sub.add_parser("summarize", help="write and print summary.md for a recording directory")
    summary.add_argument("directory", type=Path)
    args = parser.parse_args()
    print(summarize(args.directory.resolve()), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
