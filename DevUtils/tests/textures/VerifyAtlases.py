#!/usr/bin/env python3
"""Compare atlas builds against the Java pipeline they replace.

Runs AtlasReference in alternating fresh JVMs against a Reference checkout
(the last Java atlas pipeline, compiled), this checkout, and Frozen. Every
record must match Reference byte for byte; each timing median must stay
within the performance gate against Frozen (Current at most 10% slower by
default), and is reported against Reference.

The content is this checkout's resources, alone and with a generated
fixture pack of edge cases: odd PNG encodings, animation metadata errors,
unstitch and paletted sources, filters, broken definitions and GUI
metadata. The trees' main classes must already be built; no other tree's
sources change.
"""
from __future__ import annotations

import argparse
import difflib
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import struct
import sys
import time
import zlib

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "DevUtils/tests/content"))
from VerifyStateGraphs import classpath, command, identity, sha  # noqa: E402

OBSERVER = Path(__file__).with_name("AtlasReference.java")
ROW = re.compile(r"\bATLAS_REFERENCE sha256=([0-9a-f]{64}) atlases_ns=(\d+)$", re.MULTILINE)
TIMINGS = ("atlases_ns",)

# -- PNG fixtures ---------------------------------------------------------------


def chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


ADAM7 = ((0, 0, 8, 8), (4, 0, 8, 8), (0, 4, 4, 8), (2, 0, 4, 4), (0, 2, 2, 4), (1, 0, 2, 2), (0, 1, 1, 2))


def png(width: int, height: int, color: int, depth: int, samples, palette=None, trns=None, interlace=False) -> bytes:
    """`samples[y][x]` is a tuple of channel values at `depth` bits."""
    def row_bytes(values) -> bytes:
        if depth >= 8:
            fmt = ">H" if depth == 16 else ">B"
            return b"".join(struct.pack(fmt, value) for pixel in values for value in pixel)
        bits, out, filled = 0, bytearray(), 0
        for pixel in values:
            for value in pixel:
                bits = (bits << depth) | value
                filled += depth
                if filled == 8:
                    out.append(bits)
                    bits, filled = 0, 0
        if filled:
            out.append(bits << (8 - filled))
        return bytes(out)

    raw = bytearray()
    if interlace:
        for x0, y0, dx, dy in ADAM7:
            for y in range(y0, height, dy):
                values = [samples[y][x] for x in range(x0, width, dx)]
                if values:
                    raw += b"\x00" + row_bytes(values)
    else:
        for y in range(height):
            raw += b"\x00" + row_bytes(samples[y])
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, depth, color, 0, 0, int(interlace)))
    if palette:
        data += chunk(b"PLTE", bytes(value for entry in palette for value in entry))
    if trns is not None:
        data += chunk(b"tRNS", trns)
    return data + chunk(b"IDAT", zlib.compress(bytes(raw))) + chunk(b"IEND", b"")


def rgba(width: int, height: int, pixel) -> bytes:
    return png(width, height, 6, 8, [[pixel(x, y) for x in range(width)] for y in range(height)])


def write(path: Path, data: bytes | str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data.encode() if isinstance(data, str) else data)


def build_fixtures(packs: Path) -> None:
    pack = packs / "edge"
    write(pack / "pack.mcmeta", '{"pack":{"description":"atlas edge cases","pack_format":69,"supported_formats":[15,999]}}')
    assets = pack / "assets/minecraft"
    tex = assets / "textures/fixture"
    gradient = lambda x, y: (x * 16 % 256, y * 16 % 256, (x * y) % 256, 255)
    write(tex / "single_src.png", rgba(16, 16, gradient))
    write(tex / "dir/a.png", rgba(16, 16, gradient))
    write(tex / "dir/a_n.png", rgba(16, 16, lambda x, y: (128, 128, 255, 255)))
    write(tex / "dir/b_s.png", rgba(16, 16, lambda x, y: (1, 2, 3, 255)))
    write(tex / "dir/keep_s.png", rgba(16, 16, lambda x, y: (9, 9, 9, 255)))
    write(tex / "dir/anim.png", rgba(16, 64, lambda x, y: (y * 4 % 256, 0, 0, 255)))
    write(tex / "dir/anim.png.mcmeta", '{"animation":{"frames":[0,{"index":7,"time":2},2,{"index":1}],"frametime":3}}')
    write(tex / "dir/anim_wh.png", rgba(32, 16, lambda x, y: (0, x * 8 % 256, 0, 200)))
    write(tex / "dir/anim_wh.png.mcmeta", '{"animation":{"width":8,"height":16,"interpolate":true}}')
    write(tex / "dir/single_frame.png", rgba(16, 32, gradient))
    write(tex / "dir/single_frame.png.mcmeta", '{"animation":{"frames":[1]}}')
    write(tex / "dir/notmultiple.png", rgba(16, 16, gradient))
    write(tex / "dir/notmultiple.png.mcmeta", '{"animation":{"width":5}}')
    write(tex / "dir/badmeta.png", rgba(16, 16, gradient))
    write(tex / "dir/badmeta.png.mcmeta", '{"animation":')
    write(tex / "dir/nullanim.png", rgba(16, 16, gradient))
    write(tex / "dir/nullanim.png.mcmeta", '{"animation":null}')
    write(tex / "dir/badtime.png", rgba(16, 32, gradient))
    write(tex / "dir/badtime.png.mcmeta", '{"animation":{"frametime":0}}')
    write(tex / "dir/trailing.png", rgba(16, 32, gradient))
    write(tex / "dir/trailing.png.mcmeta", '{"animation":{"frametime":2}} trailing text')
    write(tex / "dir/badpng.png", b"\x89PNG\r\n\x1a\nnot really a png")
    write(tex / "dir/notpng.png", b"GIF89a")
    write(tex / "dir/gray16.png", png(16, 16, 0, 16, [[(x * 4096 + y,) for x in range(16)] for y in range(16)]))
    write(tex / "dir/gray1.png", png(16, 16, 0, 1, [[((x + y) % 2,) for x in range(16)] for y in range(16)]))
    write(tex / "dir/gray4_trns.png", png(16, 16, 0, 4, [[(x % 16,) for x in range(16)] for y in range(16)], trns=struct.pack(">H", 3)))
    write(tex / "dir/rgb16.png", png(16, 16, 2, 16, [[(x * 4000, y * 4000, 65535 - x * 100) for x in range(16)] for y in range(16)]))
    write(tex / "dir/rgb_trns.png", png(16, 16, 2, 8, [[(x % 2 * 255, 0, 0) for x in range(16)] for y in range(16)], trns=struct.pack(">HHH", 255, 0, 0)))
    write(tex / "dir/palette.png", png(16, 16, 3, 8, [[((x + y) % 4,) for x in range(16)] for y in range(16)],
                                     palette=[(255, 0, 0), (0, 255, 0), (0, 0, 255), (9, 9, 9)], trns=bytes([0, 128])))
    write(tex / "dir/palette2.png", png(16, 16, 3, 2, [[(x % 4,) for x in range(16)] for y in range(16)],
                                      palette=[(10, 20, 30), (40, 50, 60), (70, 80, 90), (1, 2, 3)]))
    write(tex / "dir/gray_alpha.png", png(16, 16, 4, 8, [[(x * 16, y * 16) for x in range(16)] for y in range(16)]))
    write(tex / "dir/rgba16.png", png(16, 16, 6, 16, [[(x * 4000, 1000, y * 4000, (x + y) * 2000) for x in range(16)] for y in range(16)]))
    write(tex / "dir/interlaced.png", png(16, 16, 6, 8, [[gradient(x, y) for x in range(16)] for y in range(16)], interlace=True))
    write(tex / "dir/bad_crc.png", rgba(16, 16, gradient)[:29] + b"\x00\x00\x00\x00" + rgba(16, 16, gradient)[33:])
    write(tex / "dir/transparent_all.png", rgba(16, 16, lambda x, y: (x * 16, y * 16, 0, 0)))
    write(tex / "dir/partial.png", rgba(16, 16, lambda x, y: (x * 16, y * 16, 128, (x * 37 + y * 11) % 256)))
    write(tex / "dir/cutout.png", rgba(16, 16, lambda x, y: (200, 100, 50, 255 if (x // 4 + y // 4) % 2 else 0)))
    write(tex / "dir/translucent.png", rgba(16, 16, lambda x, y: (20, 40, 60, 100)))
    write(tex / "dir/wide.png", rgba(48, 16, gradient))
    write(tex / "dir/nested/deep.png", rgba(16, 16, gradient))
    write(tex / "dir/not_png.txt", "ignored")
    write(tex / "small.png", rgba(4, 8, gradient))
    write(tex / "sheet.png", rgba(32, 32, lambda x, y: (x * 8, y * 8, 77, 255)))
    write(tex / "pal_tex.png", png(4, 4, 6, 8, [[(10, 20, 30, 255), (40, 50, 60, 128), (1, 1, 1, 255), (0, 0, 0, 0)] for _ in range(4)]))
    write(tex / "pal_key.png", png(3, 1, 6, 8, [[(10, 20, 30, 255), (40, 50, 60, 255), (7, 7, 7, 0)]]))
    write(tex / "pal_red.png", png(3, 1, 6, 8, [[(255, 0, 0, 255), (128, 0, 0, 64), (5, 5, 5, 255)]]))
    write(tex / "pal_bad_size.png", png(2, 1, 6, 8, [[(1, 2, 3, 255), (4, 5, 6, 255)]]))
    write(assets / "atlases/blocks.json", json.dumps({"sources": [
        {"type": "minecraft:single", "resource": "minecraft:fixture/single_src", "sprite": "minecraft:fixture/renamed"},
        {"type": "single", "resource": "minecraft:fixture/does_not_exist"},
        {"type": "minecraft:directory", "source": "fixture/dir", "prefix": "fixture/dir/"},
        {"type": "minecraft:single", "resource": "minecraft:fixture/small"},
        {"type": "minecraft:unstitch", "resource": "minecraft:fixture/sheet", "divisor_x": 2, "divisor_y": 2, "regions": [
            {"sprite": "minecraft:fixture/region_a", "x": 0, "y": 0, "width": 1, "height": 1},
            {"sprite": "minecraft:fixture/region_b", "x": 0.5, "y": 0.5, "width": 0.75, "height": 0.5},
            {"sprite": "minecraft:fixture/region_a", "x": 1, "y": 0, "width": 1, "height": 1}]},
        {"type": "minecraft:unstitch", "resource": "minecraft:fixture/missing_sheet", "regions": [
            {"sprite": "minecraft:fixture/never", "x": 0, "y": 0, "width": 1, "height": 1}]},
        {"type": "minecraft:paletted_permutations", "textures": ["minecraft:fixture/pal_tex", "minecraft:fixture/no_tex"],
         "palette_key": "minecraft:fixture/pal_key", "separator": "-",
         "permutations": {"red": "minecraft:fixture/pal_red", "bad": "minecraft:fixture/pal_bad_size", "absent": "minecraft:fixture/pal_absent"}},
        {"type": "minecraft:filter", "pattern": {"path": "^fixture/dir/b"}},
        {"type": "minecraft:filter", "pattern": {"namespace": "nothing_matches_this", "path": "single"}},
        {"type": "minecraft:single", "resource": "minecraft:fixture/single_src", "sprite": "minecraft:fixture/dir/a"},
    ]}))
    # An out-of-bounds unstitch region becomes a second missing sprite.
    write(assets / "atlases/beds.json", json.dumps({"sources": [
        {"type": "minecraft:unstitch", "resource": "minecraft:fixture/sheet", "regions": [
            {"sprite": "minecraft:fixture/outside", "x": 0.75, "y": 0, "width": 0.5, "height": 1}]}]}))
    write(assets / "atlases/signs.json", '{"sources": [')
    # StrictJsonParser rejects anything after the value.
    write(assets / "atlases/decorated_pot.json", '{"sources": [{"type": "minecraft:filter", "pattern": {"path": "pattern"}}]} trailing')
    write(assets / "atlases/paintings.json", json.dumps({"sources": [{"type": "minecraft:bogus"}]}))
    write(assets / "atlases/particles.json", json.dumps({"sources": [{"type": "minecraft:filter", "pattern": {"path": "(["}}]}))
    write(assets / "atlases/chests.json", json.dumps({"sources": [{"type": "minecraft:filter", "pattern": {"path": "normal"}}]}))
    write(assets / "atlases/gui.json", json.dumps({"sources": [{"type": "minecraft:directory", "source": "fixture/gui", "prefix": "fixture_gui/"}]}))
    write(tex / "gui/nine.png", rgba(16, 16, gradient))
    write(tex / "gui/nine.png.mcmeta", '{"gui":{"scaling":{"type":"nine_slice","width":16,"height":16,"border":4}}}')
    write(tex / "gui/tile.png", rgba(16, 16, gradient))
    write(tex / "gui/tile.png.mcmeta", '{"gui":{"scaling":{"type":"tile","width":8,"height":8}},"animation":{}}')
    write(tex / "gui/badgui.png", rgba(16, 16, gradient))
    write(tex / "gui/badgui.png.mcmeta", '{"gui":{"scaling":{"type":"nonsense"}}}')
    write(tex / "gui/othermeta.png", rgba(16, 16, gradient))
    write(tex / "gui/othermeta.png.mcmeta", '{"villager":{"hat":"full"}}')


def build_mip_fixtures(packs: Path) -> None:
    """A pack alone whose blocks atlas allows four mip levels."""
    pack = packs / "mipped"
    write(pack / "pack.mcmeta", '{"pack":{"description":"mip levels","pack_format":69,"supported_formats":[15,999]}}')
    assets = pack / "assets/minecraft"
    tex = assets / "textures/mip"
    write(tex / "opaque.png", rgba(16, 16, lambda x, y: (x * 16, y * 16, (x ^ y) * 16 % 256, 255)))
    write(tex / "cutout.png", rgba(32, 32, lambda x, y: (200, 30 + x * 6, 90, 255 if (x // 3 + y // 5) % 2 else 0)))
    write(tex / "partial.png", rgba(16, 32, lambda x, y: ((x * 13) % 256, (y * 7) % 256, 50, (x * 17 + y * 29) % 256)))
    write(tex / "uniform_alpha.png", rgba(16, 16, lambda x, y: (x * 16, 255 - y * 16, 128, 77)))
    write(tex / "transparent.png", rgba(16, 16, lambda x, y: (x * 16, y * 16, 3, 0)))
    write(tex / "wide.png", rgba(64, 16, lambda x, y: (x * 4, y * 16, 255 - x * 4, 255 if x % 7 else 30)))
    write(tex / "anim.png", rgba(16, 64, lambda x, y: (y * 4 % 256, x * 16, 9, 255 if y % 16 > 2 else 0)))
    write(tex / "anim.png.mcmeta", '{"animation":{"frametime":2,"interpolate":true,"frames":[3,1,{"index":2,"time":4},0]}}')
    write(assets / "atlases/blocks.json", json.dumps({"sources": [{"type": "minecraft:directory", "source": "mip", "prefix": "mip/"}]}))


LOG_LINE = re.compile(r"^\[[^\]]*\] \[[^/\]]*/(WARN|ERROR)\]: (.*)$", re.MULTILINE)


def warnings(log: Path) -> list[str]:
    """WARN and ERROR first lines, sorted (parallel loaders log in any order)."""
    return sorted(f"{level} {message}" for level, message in LOG_LINE.findall(log.read_text(errors="replace")))


# -- driver ---------------------------------------------------------------------


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, default=ROOT / "build/reference-head")
    parser.add_argument("--frozen-repo", type=Path, default=ROOT.parent / "MattMC_JavaPerfTesting/MattMC")
    parser.add_argument("--output", type=Path, default=Path("build/atlas-verification"))
    parser.add_argument("--java-home", type=Path, help="JDK 25 home; defaults to JAVA_HOME or java on PATH")
    parser.add_argument("--pairs", type=int, default=5, help="alternating fresh-JVM rounds (at least three)")
    parser.add_argument("--iterations", type=int, default=5, help="warm timing iterations per run")
    parser.add_argument("--max-ratio", type=float, default=1.10, help="largest allowed Current/Frozen timing median")
    parser.add_argument("--cpu-only", action="store_true", help="compare CPU atlas records without semantic-device snapshots, including against Frozen")
    args = parser.parse_args()
    if args.pairs < 3:
        parser.error("At least three rounds are required")
    trees = {"reference": args.reference.resolve(), "current": ROOT, "frozen": args.frozen_repo.resolve()}
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / "build") or out.exists():
        parser.error("--output must be a new directory below this checkout's build/")
    for side in ("reference", "frozen"):
        if not (trees[side] / "build/classes/java/main/net/minecraft/server/Bootstrap.class").is_file():
            parser.error(f"{side} must be a checkout with compiled main classes")
    home = args.java_home or (Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None)
    java = str(home / "bin/java") if home else shutil.which("java")
    javac = str(home / "bin/javac") if home else shutil.which("javac")
    env = {**os.environ, "CARGO_PROFILE_RELEASE_STRIP": "none", "CARGO_PROFILE_RELEASE_DEBUG": "line-tables-only"}
    out.mkdir(parents=True)
    report = {"schema": "mattmc-atlas-verification-v1", "passed": False,
              "scope": "Every known atlas built by SpriteLoader.loadAndStitch over the checkout's resources, alone and with "
                       "generated edge-case fixtures: sizes, mip levels, map order, sprite placements, UVs, frames, flags, "
                       "GUI metadata, per-level pixel hashes and uploaded first-frame snapshots; warm parallel load time of "
                       "all atlases. Does not cover in-game rendering or texture upload.",
              "trees": {side: identity(tree) for side, tree in trees.items()}, "rounds": [], "max_ratio": args.max_ratio,
              "observer_sha256": sha(OBSERVER), "driver_sha256": sha(Path(__file__)),
              "jvm_args": ["-Xms1g", "-Xmx6g", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "--enable-native-access=ALL-UNNAMED"]}
    report["cpu_only"] = args.cpu_only
    if args.cpu_only:
        report["scope"] = report["scope"].replace("and uploaded first-frame snapshots", "without uploaded first-frame snapshots")
    result_path = out / "results.json"

    def save() -> None:
        result_path.write_text(json.dumps(report, indent=2) + "\n")

    save()
    try:
        cp = {side: classpath(tree, out, side, env) for side, tree in trees.items()}
        content = out / "content"
        content.mkdir()
        (content / "resources").symlink_to(ROOT / "src/main/resources")
        build_fixtures(content / "packs")
        build_mip_fixtures(content / "mip-packs")
        classes = {}
        for side in trees:
            classes[side] = out / f"observer-{side}"
            classes[side].mkdir()
            command([javac, "-cp", cp[side], "-d", str(classes[side]), str(OBSERVER)], ROOT, out / f"observer-{side}.log", env)
        reference_record = None
        reference_warnings = None
        order = ("reference", "current", "frozen")
        for round_index in range(args.pairs):
            row = {}
            report["rounds"].append(row)
            sides = order if round_index % 2 == 0 else tuple(reversed(order))
            for side in sides:
                record = out / f"record-{round_index + 1}-{side}.txt"
                began = time.monotonic()
                output = command([java, *report["jvm_args"], "-Dmattmc.rust.natives.dir=" + str(trees[side] / "build/rust/native"),
                                  "-cp", str(classes[side]) + os.pathsep + cp[side], "AtlasReference",
                                  str(content), str(content / "packs"), str(content / "mip-packs"), str(record), str(args.iterations),
                                  *(["cpu-only"] if args.cpu_only else [])],
                                 out, out / f"round-{round_index + 1}-{side}.log", env)
                found = ROW.findall(output)
                if len(found) != 1:
                    raise RuntimeError(f"Missing/ambiguous observer receipt: round {round_index + 1} {side}")
                row[side] = {"sha256": found[0][0], **{key: int(value) for key, value in zip(TIMINGS, found[0][1:])},
                             "process_wall_s": time.monotonic() - began}
                save()
                if side == "frozen":
                    continue
                logged = warnings(out / f"round-{round_index + 1}-{side}.log")
                if side == "reference" and reference_warnings is None:
                    reference_warnings = logged
                elif side == "current" and reference_warnings is not None and logged != reference_warnings:
                    diff = list(difflib.unified_diff(reference_warnings, logged, "reference warnings", "current warnings", lineterm="", n=0))
                    (out / "warnings.diff").write_text("\n".join(diff) + "\n")
                    raise RuntimeError(f"Logged warnings differ ({len(diff)} diff lines); see {out / 'warnings.diff'}")
                text = record.read_text()
                if reference_record is None:
                    reference_record = (record, text)
                elif text != reference_record[1]:
                    diff = list(difflib.unified_diff(reference_record[1].splitlines(), text.splitlines(),
                                                     str(reference_record[0].name), str(record.name), lineterm="", n=1))
                    (out / "mismatch.diff").write_text("\n".join(diff) + "\n")
                    raise RuntimeError(f"Record mismatch ({len(diff)} diff lines); see {out / 'mismatch.diff'}")
        report["records_identical"] = True
        report["record_lines"] = len(reference_record[1].splitlines())
        report["timings"] = {}
        failures = []
        for metric in TIMINGS:
            medians = {side: statistics.median(row[side][metric] for row in report["rounds"]) for side in trees}
            ratio = medians["current"] / medians["frozen"]
            report["timings"][metric] = {**medians, "current_over_frozen": ratio,
                                         "current_over_reference": medians["current"] / medians["reference"],
                                         "round_ratios_frozen": [row["current"][metric] / row["frozen"][metric] for row in report["rounds"]]}
            if ratio > args.max_ratio:
                failures.append(f"{metric} {ratio:.3f}")
        if failures:
            raise RuntimeError("Performance gate failed: " + ", ".join(failures))
        report["passed"] = True
    except Exception as error:
        report["failure"] = str(error)
        raise
    finally:
        save()
    print(json.dumps({"timings": report.get("timings"), "record_lines": report.get("record_lines")}, indent=2))
    print(result_path)


if __name__ == "__main__":
    main()
