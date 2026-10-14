#!/usr/bin/env python3
"""Run #796 acceptance through a real, disposable dedicated-server console.

Compile the intended server first (this driver never builds or modifies classes),
then supply the main runtime classpath, Java executable, and Rust native directory.
Example, from the checkout:

  python3 DevUtils/tests/server/VerifyComposterTransfers.py \
    --java /path/to/jdk/bin/java --classpath-file /path/to/main-classpath.txt \
    --native-dir build/rust/native --output /tmp/composter-acceptance

The output directory must not exist. It retains the disposable world, exact
commands, console logs, binary hashes, and machine-readable result. The server
binds only to loopback, with no RCON. No existing save or server configuration is
opened. --class-overlay prepends an original compiled class directory; combined
with --expect-original-loss it verifies the old defect and successful controls,
NOT candidate acceptance. Normal mode requires every preservation assertion.
Manual player harvesting is outside this console driver's scope.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shlex
import subprocess
import sys
import threading
import time
import uuid
import zipfile


REPO = Path(__file__).resolve().parents[3]
CLASS_NAMES = [
    "net/minecraft/world/level/block/ComposterBlock" + suffix + ".class"
    for suffix in ("", "$OutputContainer", "$InputContainer", "$EmptyContainer")
] + [
    "net/minecraft/world/level/block/entity/HopperBlockEntity.class",
    "net/minecraft/world/SimpleContainer.class",
]


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def binary_pins(classpath, native_dir):
    pins = {}
    for name in CLASS_NAMES:
        for entry in classpath.split(os.pathsep):
            path = Path(entry)
            if path.is_dir() and (path / name).is_file():
                pins[name] = {"source": str(path / name), "sha256": sha256(path / name)}
                break
            if path.is_file() and zipfile.is_zipfile(path):
                with zipfile.ZipFile(path) as archive:
                    if name in archive.namelist():
                        pins[name] = {"source": str(path) + "!/" + name,
                                      "sha256": hashlib.sha256(archive.read(name)).hexdigest()}
                        break
        else:
            raise RuntimeError("Missing compiled runtime class: " + name)
    natives = sorted(p for p in native_dir.iterdir() if p.is_file() and p.suffix in (".so", ".dll", ".dylib"))
    if not natives:
        raise RuntimeError("No Rust native library found in " + str(native_dir))
    pins["natives"] = {str(p): sha256(p) for p in natives}
    return pins


def xyz(pos):
    return " ".join(map(str, pos))


def offset(pos, dx=0, dy=0, dz=0):
    return pos[0] + dx, pos[1] + dy, pos[2] + dz


def items_snbt(items):
    return "[" + ",".join('{Slot:%db,id:"minecraft:%s",count:%d}' % (slot, item, count)
                           for slot, item, count in items) + "]"


def stones(count=1):
    return [(slot, "stone", count) for slot in range(5)]


class Server:
    def __init__(self, command, world_dir, output, timeout):
        self.command = command
        self.world_dir = world_dir
        self.output = output
        self.timeout = timeout
        self.process = None
        self.run = 0
        self.serial = 0
        self.token = "COMP796_" + uuid.uuid4().hex
        self.commands = (output / "commands.jsonl").open("w", encoding="utf-8")
        self.exits = []
        self.tick_steps = []
        self.startup_diagnostics = []

    def start(self):
        self.run += 1
        self.lines = queue.Queue()
        self.log = (self.output / f"console-{self.run}.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen(self.command, cwd=self.world_dir, stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, encoding="utf-8", errors="replace", bufsize=1)

        def read_output():
            for line in self.process.stdout:
                self.log.write(line)
                self.log.flush()
                self.lines.put(line.rstrip("\r\n"))
            self.lines.put(None)

        self.reader = threading.Thread(target=read_output, daemon=True)
        self.reader.start()
        startup = self.until(lambda line: '[Server thread/INFO]: Done (' in line, self.timeout)
        self.startup_diagnostics.append({"run": self.run, "lines": [
            line for line in startup if "/ERROR]" in line or "/WARN]" in line or line.startswith("WARNING:")
        ]})
        # Tick freezing does not persist. All restart fixtures are physically
        # powered before the previous save, so early startup ticks are harmless.
        self.command_batch(["tick freeze"])

    def until(self, predicate, timeout=None):
        deadline = time.monotonic() + (timeout or self.timeout)
        received = []
        while time.monotonic() < deadline:
            try:
                line = self.lines.get(timeout=max(0.01, deadline - time.monotonic()))
            except queue.Empty as exc:
                raise TimeoutError("Timed out waiting for dedicated-server output") from exc
            if line is None:
                raise RuntimeError(f"Server exited before expected response (exit={self.process.poll()})")
            received.append(line)
            if predicate(line):
                return received
        raise TimeoutError("Timed out waiting for dedicated-server response")

    @staticmethod
    def said(line, marker):
        # Only actual console broadcast output counts, never echoed commands or
        # partial marker substrings in a parser error.
        return re.search(r"\[Server thread/INFO\]: (?:\[Not Secure\] )?\[Server\] "
                         + re.escape(marker) + r"$", line) is not None

    def send(self, command):
        self.commands.write(json.dumps({"run": self.run, "time_ns": time.time_ns(),
                                        "command": command}) + "\n")
        self.commands.flush()
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()

    def marker(self, kind):
        self.serial += 1
        return f"{self.token}_{kind}_{self.serial}"

    def command_batch(self, commands):
        marker = self.marker("BARRIER")
        for command in commands:
            self.send(command)
        self.send("say " + marker)
        lines = self.until(lambda line: self.said(line, marker))
        # Parsing failures are never tolerated, even if a subsequent assertion
        # would happen to match the default world state.
        errors = ("Unknown or incomplete command", "Incorrect argument", "<--[HERE]",
                  "An unexpected error occurred", "That position is not loaded")
        if any(any(error in line for error in errors) for line in lines):
            raise RuntimeError("Console command failed: " + "\n".join(lines))
        return lines

    def condition(self, condition):
        marker = self.marker("ASSERT")
        lines = self.command_batch([f"execute {condition} run say {marker}"])
        return sum(self.said(line, marker) for line in lines) == 1

    def game_time(self):
        lines = self.command_batch(["time query gametime"])
        values = [int(m.group(1)) for line in lines
                  if (m := re.search(r"\[Server thread/INFO\]: The time is (\d+)$", line))]
        if len(values) != 1:
            raise RuntimeError("Missing or ambiguous game-time response: " + repr(lines))
        return values[0]

    def step(self, ticks=1):
        before = self.game_time()
        self.command_batch([f"tick step {ticks}t"])
        deadline = time.monotonic() + self.timeout
        while True:
            after = self.game_time()
            if after == before + ticks:
                self.tick_steps.append({"run": self.run, "before": before, "after": after, "ticks": ticks})
                return
            if after > before + ticks:
                raise AssertionError(f"Uncontrolled server ticking: {before} -> {after}, requested {ticks}")
            if time.monotonic() >= deadline:
                raise TimeoutError(f"Requested {ticks} ticks never completed: {before} -> {after}")
            time.sleep(0.02)

    def stop(self, save=True):
        if self.process is None:
            return
        errors = []
        if self.process.poll() is None:
            # Save failures must fail acceptance, but must never prevent the
            # owned disposable process from being stopped. Preserve every
            # failure while still attempting graceful and then forced cleanup.
            if save:
                try:
                    lines = self.command_batch(["save-all flush"])
                    if not any(line.endswith("Saved the game") for line in lines):
                        raise RuntimeError("save-all flush was not acknowledged")
                except Exception as exc:
                    errors.append(f"save failed: {type(exc).__name__}: {exc}")
            if self.process.poll() is None:
                try:
                    self.send("stop")
                except Exception as exc:
                    errors.append(f"stop command failed: {type(exc).__name__}: {exc}")
                try:
                    self.process.wait(timeout=self.timeout)
                except subprocess.TimeoutExpired:
                    errors.append("Dedicated server failed to stop within the timeout")
                    self.process.terminate()
                    try:
                        self.process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        errors.append("Dedicated server did not terminate; forced kill required")
                        self.process.kill()
                        self.process.wait(timeout=15)
        self.reader.join(timeout=10)
        if self.reader.is_alive():
            errors.append("Server output reader did not finish")
        self.log.close()
        self.process.stdin.close()
        self.process.stdout.close()
        code = self.process.returncode
        self.exits.append({"run": self.run, "exit": code, "cleanup_errors": errors.copy()})
        self.process = None
        if code:
            errors.append(f"Dedicated server exit status {code}")
        if errors:
            raise RuntimeError("; ".join(errors))


class Acceptance:
    def __init__(self, server, original):
        self.s = server
        self.original = original
        self.checks = []
        self.positions = {
            "blocked": (2, 64, 2), "empty": (6, 64, 2), "merge": (10, 64, 2),
            "maxed": (14, 64, 2), "powered": (2, 64, 6), "cooldown": (6, 64, 6),
            "output_frees": (10, 64, 6), "output_partial": (14, 64, 6),
            "maturation": (2, 64, 10), "valid_input": (6, 64, 10),
            "invalid_input": (10, 64, 10), "reload": (14, 64, 10),
        }

    def check(self, label, condition, expected=True):
        actual = self.s.condition(condition)
        self.checks.append({"label": label, "condition": condition, "expected": expected,
                            "actual": actual, "passed": actual == expected, "run": self.s.run})
        if actual != expected:
            raise AssertionError(label + ": " + condition)
        print("PASS " + label, flush=True)

    def block(self, label, pos, block):
        self.check(label, f"if block {xyz(pos)} minecraft:{block}")

    def level(self, name, value, label=None):
        self.block(label or f"{name}: composter level {value}", offset(self.positions[name], dy=1),
                   f"composter[level={value}]")

    def inventory(self, label, pos, items):
        # NBT list matching alone allows additional entries. Also prove the
        # exact list length, plus every slot's exact ID/count; no dropped or
        # duplicate items can satisfy these assertions.
        condition = f"unless data block {xyz(pos)} Items[{len(items)}]"
        if items:
            condition += f" if data block {xyz(pos)} {{Items:{items_snbt(items)}}}"
        self.check(label, condition)

    def fixture(self, name, items, level=8, cooldown=0, chest=False, powered=False):
        pos = self.positions[name]
        self.s.command_batch([
            f"setblock {xyz(offset(pos, dx=1))} minecraft:{'chest' if chest else 'stone'}",
            f"setblock {xyz(pos)} minecraft:hopper[facing=east]"
            + "{Items:" + items_snbt(items) + f",TransferCooldown:{cooldown}" + "}",
            f"setblock {xyz(offset(pos, dy=1))} minecraft:composter[level={level}]",
        ])
        if powered:
            self.power(name, True)
        self.block(name + ": hopper state", pos, f"hopper[facing=east,enabled={str(not powered).lower()}]")
        self.level(name, level, name + ": initial composter")
        self.inventory(name + ": initial inventory", pos, items)
        self.check(name + ": initial cooldown", f"if data block {xyz(pos)} {{TransferCooldown:{cooldown}}}")

    def power(self, name, powered):
        pos = self.positions[name]
        self.s.command_batch([f"setblock {xyz(offset(pos, dx=-1))} minecraft:{'redstone_block' if powered else 'air'}"])
        self.block(name + (": disabled" if powered else ": enabled"), pos,
                   f"hopper[enabled={str(not powered).lower()}]")

    def input_fixture(self, name, item):
        base = self.positions[name]
        top = offset(base, dy=2)
        self.s.command_batch([
            f"setblock {xyz(base)} minecraft:stone",
            f"setblock {xyz(offset(base, dy=1))} minecraft:composter[level=0]",
            f"setblock {xyz(top)} minecraft:hopper[facing=down]"
            + "{Items:" + items_snbt([(0, item, 1)]) + ",TransferCooldown:0}",
        ])
        self.level(name, 0)
        self.inventory(name + ": initial source", top, [(0, item, 1)])

    def run(self):
        s = self.s
        s.command_batch(["forceload add 0 0 15 15"])
        deadline = time.monotonic() + s.timeout
        while not s.condition("if loaded 2 64 2"):
            if time.monotonic() >= deadline:
                raise TimeoutError("Forced fixture chunk never loaded")
            time.sleep(0.1)
        self.fixture("blocked", stones())
        self.fixture("empty", [])
        self.fixture("merge", [(0, "bone_meal", 63)] + [(n, "stone", 64) for n in range(1, 5)])
        self.fixture("maxed", stones(64))
        self.fixture("powered", [], powered=True)
        self.fixture("cooldown", [], cooldown=4)
        self.fixture("output_frees", stones(), chest=True)
        self.fixture("output_partial", [(0, "stone", 2)] + stones()[1:], chest=True)
        self.fixture("maturation", [], level=7, powered=True)
        # Level 7 refuses an otherwise valid input and matures on its scheduled
        # tick. Its output is locked until the mature state is observed.
        mature_top = offset(self.positions["maturation"], dy=2)
        s.command_batch([f"setblock {xyz(mature_top)} minecraft:hopper[facing=down]"
                         + '{Items:[{Slot:0b,id:"minecraft:cake",count:1}],TransferCooldown:0}'])
        self.input_fixture("valid_input", "cake")
        self.input_fixture("invalid_input", "stone")
        initial_time = s.game_time()
        s.step()
        # This successful control demonstrates the actual server tick/container
        # path is active, independently of preservation assertions below.
        self.level("empty", 0)
        self.inventory("empty: successful ticker control", self.positions["empty"], [(0, "bone_meal", 1)])
        self.level("merge", 0)
        self.inventory("merge: exact 63 to 64", self.positions["merge"],
                       [(0, "bone_meal", 64)] + [(n, "stone", 64) for n in range(1, 5)])
        self.level("maxed", 8)
        self.inventory("maxed: all slots unchanged", self.positions["maxed"], stones(64))
        self.level("powered", 8)
        self.inventory("powered: no extraction", self.positions["powered"], [])
        self.level("cooldown", 8)
        self.check("cooldown: decremented to 3", f"if data block {xyz(self.positions['cooldown'])} {{TransferCooldown:3}}")
        self.inventory("cooldown: no premature extraction", self.positions["cooldown"], [])
        self.level("output_frees", 0)
        self.inventory("preceding output: freed slot receives exactly one bone meal", self.positions["output_frees"],
                       [(0, "bone_meal", 1)] + stones()[1:])
        self.inventory("preceding output: chest received stone", offset(self.positions["output_frees"], dx=1), [(0, "stone", 1)])
        self.inventory("preceding output: partial stack remains incompatible", self.positions["output_partial"], stones())
        self.inventory("preceding partial output: chest received stone", offset(self.positions["output_partial"], dx=1), [(0, "stone", 1)])
        self.level("maturation", 7)
        self.inventory("level 7 rejects valid hopper input", mature_top, [(0, "cake", 1)])
        self.level("valid_input", 1)
        self.inventory("valid input consumed once", offset(self.positions["valid_input"], dy=2), [])
        self.level("invalid_input", 0)
        self.inventory("invalid input retained", offset(self.positions["invalid_input"], dy=2), [(0, "stone", 1)])
        self.inventory("blocked: five incompatible partial stacks unchanged", self.positions["blocked"], stones())
        if self.original:
            self.level("blocked", 0, "ORIGINAL DEFECT: ready batch lost without delivery")
            self.level("output_partial", 0, "ORIGINAL DEFECT: partial preceding output still loses batch")
            return "original_defect_reproduced_controls_passed"
        self.level("blocked", 8, "blocked: failed extraction preserves ready batch")
        self.level("output_partial", 8, "partial preceding output preserves ready batch")
        # Isolate the two chest fixtures after their independently verified
        # first transfer, so later output attempts do not alter their evidence.
        self.power("output_frees", True)
        self.power("output_partial", True)
        for attempt in range(2, 5):
            s.step()
            self.level("blocked", 8, f"blocked: repeated attempt {attempt} preserves ready batch")
            self.inventory(f"blocked: repeated attempt {attempt} unchanged", self.positions["blocked"], stones())
            if attempt < 4:
                self.level("cooldown", 8, f"cooldown: tick {attempt} still pending")
                self.inventory(f"cooldown: tick {attempt} no output", self.positions["cooldown"], [])
        self.level("cooldown", 0, "cooldown: extraction at expiration")
        self.inventory("cooldown: exactly one output", self.positions["cooldown"], [(0, "bone_meal", 1)])
        s.command_batch([f"item replace block {xyz(self.positions['blocked'])} container.4 with minecraft:air"])
        s.step()
        self.level("blocked", 0, "recovery: preserved batch consumed after freeing slot")
        recovered = stones()[:4] + [(4, "bone_meal", 1)]
        self.inventory("recovery: exactly one bone meal", self.positions["blocked"], recovered)
        # The level-7 scheduled tick must not occur early, then must occur on
        # tick 20 while the output remains physically disabled.
        elapsed = s.game_time() - initial_time
        s.step(19 - elapsed)
        self.level("maturation", 7, "maturation: still level 7 at tick 19")
        self.inventory("maturation: no premature output", self.positions["maturation"], [])
        s.step()
        self.level("maturation", 8, "maturation: ready at tick 20")
        self.inventory("ready composter still rejects input", mature_top, [(0, "cake", 1)])
        # Lock the verified unchanged input before releasing output; otherwise
        # a valid new batch could begin during that same tick.
        s.command_batch([f"setblock {xyz(offset(mature_top, dx=-1))} minecraft:redstone_block"])
        self.block("maturation: input locked before harvesting", mature_top, "hopper[enabled=false]")
        self.power("maturation", False)
        self.power("powered", False)
        s.step()
        self.level("maturation", 0, "maturation: normal output after unlocking")
        self.inventory("maturation: one output", self.positions["maturation"], [(0, "bone_meal", 1)])
        self.level("powered", 0, "unpowered hopper resumes extraction")
        self.inventory("unpowered hopper: one output", self.positions["powered"], [(0, "bone_meal", 1)])
        s.step(24)
        self.level("blocked", 0, "repeated fresh lookups: no second batch")
        self.inventory("repeated fresh lookups: no duplicate output", self.positions["blocked"], recovered)
        self.inventory("empty success: no duplicate output", self.positions["empty"], [(0, "bone_meal", 1)])
        self.inventory("merge success: no duplicate output", self.positions["merge"],
                       [(0, "bone_meal", 64)] + [(n, "stone", 64) for n in range(1, 5)])
        self.reload_case()
        return "candidate_acceptance_passed"

    def reload_case(self):
        s = self.s
        name = "reload"
        pos = self.positions[name]
        self.fixture(name, stones())
        s.step(3)
        self.level(name, 8, "save/reload: batch preserved after blocked attempts")
        self.inventory("save/reload: unchanged before save", pos, stones())
        self.power(name, True)
        before_stop = s.game_time()
        s.command_batch([f"data get block {xyz(pos)}"])
        s.stop(save=True)
        s.start()
        # Freeze is not saved. Physical power is saved and makes startup safe;
        # verify the persisted state before enabling any extraction again.
        self.block("save/reload: persisted physical power", offset(pos, dx=-1), "redstone_block")
        self.block("save/reload: persisted disabled hopper", pos, "hopper[facing=east,enabled=false]")
        self.level(name, 8, "save/reload: persisted ready batch")
        self.inventory("save/reload: persisted exact contents", pos, stones())
        after_start = s.game_time()
        if after_start < before_stop:
            raise AssertionError("Reloaded game time predates the flushed save")
        s.command_batch([f"data get block {xyz(pos)}"])
        self.power(name, False)
        s.command_batch([f"data merge block {xyz(pos)} {{TransferCooldown:0}}"])
        for attempt in range(1, 4):
            s.step()
            self.level(name, 8, f"save/reload: blocked attempt {attempt} preserves batch")
            self.inventory(f"save/reload: blocked attempt {attempt} preserves inventory", pos, stones())
        s.command_batch([f"item replace block {xyz(pos)} container.4 with minecraft:air"])
        s.step()
        self.level(name, 0, "save/reload: recovery consumes persisted batch")
        recovered = stones()[:4] + [(4, "bone_meal", 1)]
        self.inventory("save/reload: recovery delivers exactly one", pos, recovered)
        s.step(24)
        self.level(name, 0, "save/reload: no duplicate batch")
        self.inventory("save/reload: no duplicate output", pos, recovered)
        # Persist the successful consumption too. A second fresh server must
        # neither resurrect the completed batch nor deliver a second item.
        self.power(name, True)
        s.stop(save=True)
        s.start()
        self.block("completed reload: persisted physical power", offset(pos, dx=-1), "redstone_block")
        self.block("completed reload: persisted disabled hopper", pos, "hopper[facing=east,enabled=false]")
        self.level(name, 0, "completed reload: consumed batch stays empty")
        self.inventory("completed reload: exactly one persisted output", pos, recovered)
        self.power(name, False)
        s.step(24)
        self.level(name, 0, "completed reload: no resurrected batch after ticks")
        self.inventory("completed reload: no duplicate after ticks", pos, recovered)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--classpath-file", type=Path, required=True)
    parser.add_argument("--native-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--class-overlay", type=Path)
    parser.add_argument("--expect-original-loss", action="store_true")
    parser.add_argument("--port", type=int, default=25583)
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535 or args.timeout <= 0:
        parser.error("port must be 1024..65535 and timeout must be positive")
    java = args.java.resolve(strict=True)
    native = args.native_dir.resolve(strict=True)
    entries = args.classpath_file.read_text().strip().split(os.pathsep)
    if not entries or any(not entry for entry in entries):
        parser.error("classpath file must contain nonempty entries")
    classpath = os.pathsep.join(str(Path(entry).resolve(strict=True)) for entry in entries)
    if args.class_overlay:
        classpath = str(args.class_overlay.resolve(strict=True)) + os.pathsep + classpath
    pins = binary_pins(classpath, native)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    world_dir = output / "server"
    world_dir.mkdir()
    properties = {
        "server-ip": "127.0.0.1", "server-port": args.port,
        "level-name": "world", "level-seed": 796, "level-type": "minecraft:flat",
        "generator-settings": json.dumps({"biome": "minecraft:plains", "layers": [
            {"block": "minecraft:bedrock", "height": 1},
            {"block": "minecraft:dirt", "height": 2},
            {"block": "minecraft:grass_block", "height": 1}]}),
        "generate-structures": "false", "view-distance": 2, "simulation-distance": 2,
        "pause-when-empty-seconds": 0, "enable-rcon": "false", "enable-query": "false",
        "motd": "Disposable Composter transfer acceptance",
    }
    (world_dir / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in properties.items()))
    command = [str(java), "-Xmx2G", "-Xms512M", "--enable-native-access=ALL-UNNAMED",
               "-Dmattmc.rust.natives.dir=" + str(native), "-cp", classpath,
               "net.minecraft.server.Main", "--nogui"]
    protocol = {
        "scope": "real dedicated-server console/tick and save/reload acceptance; no client/manual harvesting",
        "mode": "original_defect_probe" if args.expect_original_loss else "candidate_acceptance",
        "command": command, "shell_command": shlex.join(command), "working_directory": str(world_dir),
        "binary_pins": pins, "driver_sha256": sha256(Path(__file__).resolve()),
        "java_version": subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True).stderr,
        "git_head": subprocess.run(["git", "rev-parse", "HEAD"], cwd=REPO, capture_output=True, text=True, check=True).stdout.strip(),
        "initial_properties": properties,
    }
    (output / "protocol.json").write_text(json.dumps(protocol, indent=2) + "\n")
    server = Server(command, world_dir, output, args.timeout)
    acceptance = Acceptance(server, args.expect_original_loss)
    result = {"status": "failed", "mode": protocol["mode"]}
    try:
        server.start()
        result["status"] = acceptance.run()
        server.stop(save=True)
        if binary_pins(classpath, native) != pins:
            raise RuntimeError("Relevant compiled binaries changed during acceptance")
    except Exception as exc:
        result["status"] = "failed"
        result["error"] = f"{type(exc).__name__}: {exc}"
        print(result["error"], file=sys.stderr, flush=True)
    finally:
        try:
            server.stop(save=True)
        except Exception as exc:
            result["status"] = "failed"
            result["cleanup_error"] = f"{type(exc).__name__}: {exc}"
        server.commands.close()
        result.update(checks=acceptance.checks, tick_steps=server.tick_steps, server_exits=server.exits,
                      binary_pins=pins, startup_diagnostics=server.startup_diagnostics)
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({k: result[k] for k in ("status", "mode")}
                     | {"checks": len(acceptance.checks), "output": str(output)}, indent=2), flush=True)
    return 1 if result["status"] == "failed" else 0


if __name__ == "__main__":
    raise SystemExit(main())
