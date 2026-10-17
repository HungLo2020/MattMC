#!/usr/bin/env python3
"""Run a test or verification command with its memory protected, and report swap pressure.

On a shared Linux desktop other applications compete for RAM with test runs.
This runs COMMAND in a systemd user scope inside ``mattmctest.slice`` with
``MemoryLow``, so under pressure the kernel reclaims other applications before
the test's own pages (they stay usable; they are only swapped first). It then
reports the swap-in rate and memory stall time observed while COMMAND ran, so
a timing taken while the machine was swapping is visible instead of silent.

Protection needs a one-time root setup that lets ``MemoryLow`` reach the user
session; see docs/development/tooling/TESTING.md ("Memory-protected runs").
Without it the command still runs and the report says it was unprotected.

    python3 DevUtils/tests/tooling/RunProtected.py -- python3 DevUtils/PerfAudit/Gameplay.py ...
    python3 DevUtils/tests/tooling/RunProtected.py --fail-on-swap -- ./gradlew test

Exit status is COMMAND's, or 3 with --fail-on-swap when the run swapped.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

SLICE = "mattmctest.slice"  # no dash: systemd reads dashes as slice nesting
UNIT_DIR = Path.home() / ".config/systemd/user"
CGROUP_ROOT = Path("/sys/fs/cgroup")


def page_kb() -> int:
    return os.sysconf("SC_PAGE_SIZE") // 1024


def counters() -> dict:
    """Swap-in/out pages since boot and total memory stall (some, full) in microseconds."""
    vmstat = dict(line.split() for line in Path("/proc/vmstat").read_text().splitlines())
    stall = {}
    for line in Path("/proc/pressure/memory").read_text().splitlines():
        kind, *fields = line.split()
        stall[kind] = int(dict(field.split("=") for field in fields)["total"])
    return {"time": time.monotonic(), "pswpin": int(vmstat["pswpin"]), "pswpout": int(vmstat["pswpout"]),
            "stall_some_us": stall["some"], "stall_full_us": stall["full"]}


def user_manager_cgroup() -> Path | None:
    for line in Path("/proc/self/cgroup").read_text().splitlines():
        path = line.split("::", 1)[-1]
        marker = f"user@{os.getuid()}.service"
        if marker in path:
            return CGROUP_ROOT / path.lstrip("/").split(marker)[0] / marker
    return None


def session_protection() -> int:
    """The smallest memory.low on the user manager and its ancestors (bytes); 0 means none."""
    manager = user_manager_cgroup()
    if manager is None:
        return 0
    lows = []
    for directory in [manager, *manager.parents]:
        if directory == CGROUP_ROOT or not directory.is_relative_to(CGROUP_ROOT):
            break
        low = (directory / "memory.low").read_text().strip() if (directory / "memory.low").exists() else "0"
        lows.append(2**63 if low == "max" else int(low))
    return min(lows) if lows else 0


def ensure_slice(memory_low: str) -> None:
    unit = UNIT_DIR / SLICE
    text = ("[Unit]\nDescription=MattMC tests and verification runs (memory protected)\n\n"
            f"[Slice]\nMemoryLow={memory_low}\n")
    if not unit.exists() or unit.read_text() != text:
        UNIT_DIR.mkdir(parents=True, exist_ok=True)
        unit.write_text(text)
        subprocess.run(["systemctl", "--user", "daemon-reload"], check=True)


def report(before: dict, after: dict, protected: bool, threshold: float) -> dict:
    seconds = max(after["time"] - before["time"], 1e-6)
    swap_in_mb = (after["pswpin"] - before["pswpin"]) * page_kb() / 1024
    swap_out_mb = (after["pswpout"] - before["pswpout"]) * page_kb() / 1024
    full_pct = 100 * (after["stall_full_us"] - before["stall_full_us"]) / (seconds * 1e6)
    some_pct = 100 * (after["stall_some_us"] - before["stall_some_us"]) / (seconds * 1e6)
    result = {"seconds": round(seconds, 1), "protected": protected,
              "swap_in_mb": round(swap_in_mb, 1), "swap_out_mb": round(swap_out_mb, 1),
              "swap_in_mb_per_s": round(swap_in_mb / seconds, 2),
              "memory_stall_some_pct": round(some_pct, 2), "memory_stall_full_pct": round(full_pct, 2)}
    result["swapped"] = result["swap_in_mb_per_s"] > threshold or full_pct > 1.0
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--memory-low", default="8G", help="MemoryLow for the test slice and scope (default 8G)")
    parser.add_argument("--swap-threshold", type=float, default=5.0,
                        help="swap-in MB/s above which the run counts as swapping (default 5)")
    parser.add_argument("--fail-on-swap", action="store_true", help="exit 3 when the run swapped")
    parser.add_argument("--report", type=Path, help="also write the pressure report as JSON here")
    parser.add_argument("command", nargs=argparse.REMAINDER, help="the command, after --")
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("give the command after --")
    protected = shutil.which("systemd-run") is not None and session_protection() > 0
    if protected:
        ensure_slice(args.memory_low)
        command = ["systemd-run", "--user", "--scope", "--quiet", f"--slice={SLICE}",
                   "-p", f"MemoryLow={args.memory_low}", "--", *command]
    else:
        print("RunProtected: memory protection unavailable (see TESTING.md); running unprotected", file=sys.stderr)
    before = counters()
    code = subprocess.run(command).returncode
    result = report(before, counters(), protected, args.swap_threshold)
    result["exit"] = code
    print("RunProtected " + json.dumps(result), file=sys.stderr)
    if args.report:
        args.report.write_text(json.dumps(result, indent=2) + "\n")
    if result["swapped"]:
        print(f"RunProtected: WARNING the machine swapped during this run ({result['swap_in_mb_per_s']} MB/s in, "
              f"{result['memory_stall_full_pct']}% full memory stall); treat its timings as unreliable", file=sys.stderr)
        if args.fail_on_swap and code == 0:
            return 3
    return code


if __name__ == "__main__":
    sys.exit(main())
