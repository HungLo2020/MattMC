#!/usr/bin/env python3
"""Compare model reloads against the Java pipeline they replace.

Runs ModelReference in alternating fresh JVMs against a Reference checkout
(the last Java model pipeline, compiled), this checkout, and Frozen. Every
record must match Reference byte for byte, and so must the sorted WARN and
ERROR entries (with continuation lines, without stack frames); each timing
median must stay within the performance gate against Frozen (Current at
most 10% slower by default), and is reported against Reference.

The content is this checkout's resources, alone and with two generated
fixture packs of edge cases: model JSON errors and number forms, parent and
texture-reference cycles, element and face rotations, display transforms,
generated items over animated and partly transparent sprites, weighted and
multipart blockstates with conditions, overlaps and errors, a blockstate
split across packs, and item definitions over missing models. The trees'
main classes and native libraries must already be built.
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
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "DevUtils/tests/content"))
sys.path.insert(0, str(ROOT / "DevUtils/tests/textures"))
from VerifyStateGraphs import classpath, command, identity, sha  # noqa: E402
from VerifyAtlases import png, rgba, write  # noqa: E402

OBSERVER = Path(__file__).with_name("ModelReference.java")
ROW = re.compile(r"\bMODEL_REFERENCE sha256=([0-9a-f]{64}) models_ns=(\d+) reload_ns=(\d+)$", re.MULTILINE)
TIMINGS = ("models_ns", "reload_ns")

# -- fixtures -------------------------------------------------------------------


def cube(texture: str = "#all", **face) -> dict:
    return {side: {"texture": texture, **face} for side in ("down", "up", "north", "south", "west", "east")}


def build_fixtures(packs: Path) -> None:
    pack = packs / "edge"
    write(pack / "pack.mcmeta", '{"pack":{"description":"model edge cases","pack_format":69,"supported_formats":[15,999]}}')
    assets = pack / "assets/minecraft"
    models = assets / "models"
    blockstates = assets / "blockstates"
    items = assets / "items"
    tex = assets / "textures"

    def model(name: str, data) -> None:
        write(models / f"{name}.json", data if isinstance(data, str) else json.dumps(data))

    # Textures in the blocks atlas's own directories.
    write(tex / "block/fixture_checker.png", rgba(16, 16, lambda x, y: (200, 40, 40, 255 if (x // 4 + y // 4) % 2 else 0)))
    write(tex / "block/fixture_glass.png", rgba(16, 16, lambda x, y: (40, 90, 200, 120 if 0 < x < 15 and 0 < y < 15 else 255)))
    write(tex / "item/fixture_shape.png", rgba(16, 16, lambda x, y: (x * 16, y * 16, 90, 255 if (x - 8) ** 2 + (y - 8) ** 2 < 30 else 0)))
    write(tex / "item/fixture_holes.png", rgba(16, 16, lambda x, y: (10, 200, 10, 0 if (x * 7 + y * 3) % 5 == 0 else 255)))
    write(tex / "item/fixture_edge.png", rgba(16, 16, lambda x, y: (255, 255, 0, 255 if x in (0, 15) or y in (0, 15) else 0)))
    write(tex / "item/fixture_anim.png", rgba(16, 64, lambda x, y: (y * 4 % 256, 0, 0, 255 if (x + y // 16) % 3 else 0)))
    write(tex / "item/fixture_anim.png.mcmeta", '{"animation":{"frames":[0,2,{"index":3,"time":4},2],"frametime":2}}')
    write(tex / "item/fixture_wide.png", rgba(32, 16, lambda x, y: (x * 8, 0, y * 16, 255 if x > y else 0)))
    write(tex / "item/fixture_empty.png", rgba(16, 16, lambda x, y: (0, 0, 0, 0)))

    # Model files.
    model("block/fixture_cycle_a", {"parent": "block/fixture_cycle_b", "textures": {"all": "block/stone"}})
    model("block/fixture_cycle_b", {"parent": "minecraft:block/fixture_cycle_a"})
    model("block/fixture_missing_parent", {"parent": "block/fixture_nonexistent", "textures": {"all": "block/dirt"}})
    model("block/fixture_rotated", {
        "textures": {"all": "block/fixture_checker", "particle": "#all", "side": "#all"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 8, 16], "faces": cube(), "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 22.5, "rescale": True}},
            {"from": [2, 2, 2], "to": [14, 14, 14], "faces": cube("#side", tintindex=1), "rotation": {"origin": [8, 8, 8], "axis": "x", "angle": -45, "rescale": True}},
            {"from": [4, 0, 4], "to": [12, 16, 12], "faces": cube(cullface="up"), "rotation": {"origin": [0, 0, 0], "axis": "z", "angle": 45}},
            {"from": [1.5, 0.25, 3.125], "to": [7.75, 9.5, 15.0625], "shade": False, "light_emission": 12,
             "faces": {"north": {"texture": "#all", "uv": [16, 0, 0, 16], "rotation": 90},
                       "south": {"texture": "#all", "rotation": 180, "cullface": "south"},
                       "east": {"texture": "#all", "uv": [3, 4.5, 9, 13], "rotation": 270},
                       "up": {"texture": "#all", "uv": [0, 16, 16, 0]}}},
            {"from": [8, 8, 8], "to": [8, 16, 16], "faces": {"west": {"texture": "#all"}, "east": {"texture": "#all", "tintindex": 2.75}}},
            None,
        ]})
    model("block/fixture_rotation_bad", {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": cube(), "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 30}}],
                                         "textures": {"all": "block/stone"}})
    model("block/fixture_axis_bad", {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": cube(), "rotation": {"origin": [8, 8, 8], "axis": "w", "angle": 0}}]})
    model("block/fixture_bounds", {"elements": [{"from": [-17, 0, 0], "to": [16, 16, 16], "faces": cube()}]})
    model("block/fixture_no_faces", {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {}}]})
    model("block/fixture_bad_face", {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"front": {"texture": "#all"}}}]})
    model("block/fixture_face_rotation_bad", {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"up": {"texture": "#all", "rotation": 45}}}]})
    model("block/fixture_light_bad", {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 16, "faces": cube()}]})
    model("block/fixture_uv_bad", {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"up": {"texture": "#all", "uv": [0, 0, 16]}}}]})
    model("block/fixture_strings", {"textures": {"all": "block/fixture_glass"}, "ambientocclusion": "false",
                                    "elements": [{"from": ["0", "0", "0"], "to": ["16", "1e1", "16"], "shade": "true", "faces": cube(tintindex="3")}]})
    model("block/fixture_texture_refs", {"parent": "block/cube_all", "textures": {"all": "#middle", "middle": "#last", "last": "block/fixture_glass",
                                                                                  "loop_a": "#loop_b", "loop_b": "#loop_a", "particle": "#loop_a"}})
    model("block/fixture_texture_missing", {"parent": "block/cube", "textures": {"north": "#nowhere", "south": "block/fixture_glass", "up": "", "down": "Bad Name"}})
    model("block/fixture_texture_unprefixed", {"parent": "block/cube_all", "textures": {"all": "fixture_checker"}})
    model("block/fixture_ao", {"parent": "block/cube_all", "ambientocclusion": False, "gui_light": "front", "textures": {"all": "block/fixture_checker"},
                               "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 100, -100], "scale": [5, 5, 0.625]},
                                           "head": {"rotation": [0, 180, 0]},
                                           "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                                           "fixed": None,
                                           "bogus_slot": {"rotation": [1, 2, 3]}}})
    model("block/fixture_gui_light_bad", {"parent": "block/cube_all", "gui_light": "sideways"})
    model("block/fixture_display_bad", {"parent": "block/cube_all", "display": {"gui": {"translation": [0, 1]}}})
    model("block/fixture_broken", '{"parent": "block/cube_all", ')
    model("block/fixture_trailing", '{"parent": "block/cube_all", "textures": {"all": "block/fixture_glass"}} trailing text')
    model("block/fixture_duplicate", '{"parent": "block/cube_all", "textures": {"all": "block/stone"}, "textures": {"all": "block/fixture_checker"}}')
    model("block/fixture_null", "null")
    model("block/fixture_array", "[1, 2]")
    model("block/fixture_elements_override", {"parent": "block/fixture_rotated", "elements": [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": cube("#side")}]})
    model("block/fixture_null_after_missing", {"textures": {"all": "block/fixture_absent"}, "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"up": {"texture": "#all"}, "down": {"texture": "#nowhere"}}}, None]})
    model("block/fixture_null_face", {"textures": {"all": "block/stone"}, "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"up": None, "down": {"texture": "#all"}}}]})
    model("block/fixture_empty_face_texture", {"textures": {"all": "block/stone"}, "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"down": {"texture": "#all"}, "up": {"texture": ""}}}]})
    model("block/fixture_special_base", {"textures": {"particle": "block/stone", "side": "fixture:block/z_absent"}, "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"up": {"texture": "#nowhere"}, "down": {"texture": "#side"}}}]})
    model("block/fixture_namespaces", {"textures": {"a": "fixture:block/a_absent", "b": "minecraft:block/z_absent", "c": "zzz:block/b_absent",
                                                    "particle": "fixture:block/particle_absent"}, "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"up": {"texture": "#a"}, "down": {"texture": "#b"}, "north": {"texture": "#c"}}}]})
    model("block/fixture_generated_block", {"parent": "item/generated", "textures": {"layer0": "item/fixture_shape"}})
    model("item/fixture_layers", {"parent": "item/generated", "textures": {"layer0": "item/fixture_shape", "layer1": "item/fixture_holes",
                                                                         "layer2": "item/fixture_anim", "layer3": "item/fixture_wide", "layer4": "item/fixture_edge"}})
    model("item/fixture_layer_gap", {"parent": "minecraft:builtin/generated", "textures": {"layer0": "item/fixture_edge", "layer2": "item/fixture_shape"}})
    model("item/fixture_layer_missing", {"parent": "item/generated", "textures": {"layer0": "item/fixture_does_not_exist", "layer1": "item/fixture_empty"}})
    model("item/fixture_handheld", {"parent": "item/handheld", "textures": {"layer0": "item/fixture_anim"},
                                    "display": {"firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}}})
    model("item/fixture_generated_elements", {"parent": "item/generated", "textures": {"layer0": "item/fixture_shape"},
                                              "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": cube("#layer0")}]})

    def blockstate(name: str, data) -> None:
        write(blockstates / f"{name}.json", data if isinstance(data, str) else json.dumps(data))

    blockstate("stone", {"variants": {"": [
        {"model": "block/fixture_rotated", "weight": 3},
        {"model": "block/fixture_rotated", "x": 90, "y": 270, "uvlock": True},
        {"model": "block/fixture_texture_refs", "x": 180, "y": 90},
        {"model": "block/fixture_generated_block", "weight": 2},
        {"model": "block/fixture_strings", "y": 180, "uvlock": True}]}})
    blockstate("granite", {"variants": {"": {"model": "block/fixture_cycle_a"}}})
    blockstate("polished_granite", {"variants": {"": {"model": "block/fixture_missing_parent"}}})
    blockstate("diorite", {"variants": {"": [{"model": "block/stone", "weight": 0}]}})
    blockstate("polished_diorite", {"variants": {"": {"model": "block/stone", "x": 45}}})
    blockstate("andesite", '{"variants": {"": {"model": "block/fixture_ao"}}} trailing text')
    blockstate("polished_andesite", '{"variants": {"": ')
    blockstate("cobblestone", {"variants": {"": []}})
    blockstate("mossy_cobblestone", {})
    blockstate("dirt", {"variants": {"": {"model": "block/fixture_rotation_bad"}}, "multipart": [{"apply": {"model": "block/stone"}}]})
    blockstate("coarse_dirt", {"variants": {"": {"model": "block/fixture_bounds"}}})
    blockstate("podzol", {"variants": {"": [{"model": "block/fixture_axis_bad"}, {"model": "block/fixture_no_faces"}, {"model": "block/fixture_bad_face"}]}})
    blockstate("rooted_dirt", {"variants": {"": [{"model": "block/fixture_face_rotation_bad"}, {"model": "block/fixture_light_bad"},
                                                 {"model": "block/fixture_uv_bad"}, {"model": "block/fixture_gui_light_bad"}]}})
    blockstate("mud", {"variants": {"": [{"model": "block/fixture_broken"}, {"model": "block/fixture_trailing"}, {"model": "block/fixture_duplicate"},
                                         {"model": "block/fixture_null"}, {"model": "block/fixture_array"}, {"model": "block/fixture_display_bad"}]}})
    blockstate("clay", {"variants": {"": [{"model": "block/fixture_texture_missing"}, {"model": "block/fixture_texture_unprefixed"},
                                          {"model": "block/fixture_elements_override", "y": 90, "uvlock": True}]}})
    blockstate("gravel", {"variants": {"": [{"model": "block/fixture_null_after_missing"}, {"model": "block/fixture_null_face", "y": 90},
                                            {"model": "block/fixture_empty_face_texture"}]}})
    blockstate("furnace", {"variants": {
        "facing=north": {"model": "block/fixture_rotated"},
        "lit=true": {"model": "block/fixture_ao"},
        "facing=east,lit=false": {"model": "block/fixture_strings", "y": 90},
        "facing=south,lit=false": {"model": "block/stone", "y": 180, "x": 270, "uvlock": True},
        "facing=west,lit=false": {"model": "block/fixture_rotated", "y": 270},
        "facing=up": {"model": "block/stone"},
        "nonexistent=1": {"model": "block/stone"},
        "lit=maybe": {"model": "block/stone"}}})
    blockstate("blast_furnace", {"variants": {"facing=north,lit=true,facing=south": {"model": "block/stone"}, "lit=false": {"model": "block/fixture_ao"}}})
    blockstate("oak_fence", {"multipart": [
        {"apply": {"model": "block/oak_fence_post"}},
        {"when": {"north": "true"}, "apply": {"model": "block/oak_fence_side", "uvlock": True}},
        {"when": {"OR": [{"east": "true", "south": "false"}, {"west": "!false"}]}, "apply": [{"model": "block/fixture_rotated", "y": 90}, {"model": "block/stone"}]},
        {"when": {"AND": [{"north": "true|false"}, {"waterlogged": "true"}]}, "apply": {"model": "block/fixture_strings", "x": 90}},
        {"when": {"north": "!true|false"}, "apply": {"model": "block/stone"}},
        {"when": {"north": "true", "south": "true", "east": "false", "west": "false"}, "apply": {"model": "block/fixture_ao"}}]})
    blockstate("birch_fence", {"multipart": [{"apply": {"model": "block/birch_fence_post"}}, {"when": {"up": "true"}, "apply": {"model": "block/stone"}}]})
    blockstate("spruce_fence", {"multipart": [{"apply": {"model": "block/spruce_fence_post"}}, {"when": {"north": "sideways"}, "apply": {"model": "block/stone"}}]})
    blockstate("jungle_fence", {"multipart": [{"when": {"XOR": []}, "apply": {"model": "block/stone"}}]})
    blockstate("acacia_fence", {"multipart": [{"when": {"north": ""}, "apply": {"model": "block/stone"}}]})
    blockstate("dark_oak_fence", {"multipart": [{"when": {"OR": []}, "apply": {"model": "block/stone"}},
                                                {"when": {"AND": [], "north": "true"}, "apply": {"model": "block/stone"}}]})
    blockstate("redstone_wire", {"multipart": [
        {"when": {"power": "0|5|15"}, "apply": {"model": "block/redstone_dust_dot"}},
        {"when": {"power": "!3"}, "apply": {"model": "block/fixture_rotated", "y": 180}},
        {"when": {"east": "side|up", "north": "none"}, "apply": {"model": "block/redstone_dust_side0"}}]})
    blockstate("wheat", {"variants": {"age=0": {"model": "block/wheat_stage0"}, "age=1": {"model": "block/wheat_stage1"},
                                      "age=7": [{"model": "block/fixture_rotated"}, {"model": "block/wheat_stage7", "weight": 5}]}})
    blockstate("lever", {"variants": {"face=floor,facing=north,powered=false": {"model": "block/lever", "y": 180}}})
    write(assets.parent / "fixture/blockstates/unknown_block.json", json.dumps({"variants": {"": {"model": "block/stone"}}}))
    write(assets.parent / "fixture/models/block/orphan.json", json.dumps({"parent": "block/cube_all", "textures": {"all": "minecraft:block/stone"}}))

    def item(name: str, data) -> None:
        write(items / f"{name}.json", json.dumps(data))

    item("stick", {"model": {"type": "minecraft:model", "model": "minecraft:item/fixture_layers"}})
    item("apple", {"model": {"type": "minecraft:model", "model": "minecraft:item/fixture_layer_gap", "tints": [{"type": "minecraft:constant", "value": -1}]}})
    item("bread", {"model": {"type": "minecraft:model", "model": "minecraft:item/fixture_layer_missing"}})
    item("diamond", {"model": {"type": "minecraft:model", "model": "minecraft:item/fixture_handheld"}})
    item("emerald", {"model": {"type": "minecraft:model", "model": "minecraft:block/fixture_rotated"}})
    item("gold_ingot", {"model": {"type": "minecraft:model", "model": "minecraft:item/fixture_undefined"}})
    item("iron_ingot", {"model": {"type": "minecraft:model", "model": "minecraft:block/fixture_cycle_b"}})
    item("coal", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": "minecraft:block/fixture_ao"},
        {"type": "minecraft:model", "model": "minecraft:block/fixture_texture_refs"}]}})
    item("charcoal", {"model": {"type": "minecraft:model", "model": "minecraft:item/fixture_generated_elements"}})
    item("flint", {"model": {"type": "minecraft:model", "model": "minecraft:block/fixture_strings"}, "hand_animation_on_swap": False})
    item("feather", {"model": {"type": "minecraft:model", "model": "minecraft:block/fixture_broken"}})
    item("compass", {"model": {"type": "minecraft:special", "base": "minecraft:block/fixture_special_base",
                               "model": {"type": "minecraft:chest", "texture": "minecraft:normal"}}})
    item("clock", {"model": {"type": "minecraft:model", "model": "minecraft:block/fixture_namespaces"}})
    item("string", {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
                              "on_true": {"type": "minecraft:model", "model": "minecraft:block/fixture_texture_missing"},
                              "on_false": {"type": "minecraft:model", "model": "minecraft:block/fixture_generated_block"}}})

    # A later pack replacing some of the first's definitions and adding to one blockstate stack.
    second = packs / "edge2"
    write(second / "pack.mcmeta", '{"pack":{"description":"model edge cases, second pack","pack_format":69,"supported_formats":[15,999]}}')
    later = second / "assets/minecraft"
    write(later / "blockstates/furnace.json", json.dumps({"variants": {"facing=north": {"model": "block/stone"}}}))
    write(later / "blockstates/granite.json", '{"variants": ')
    write(later / "models/block/fixture_cycle_b.json", json.dumps({"parent": "block/cube_all", "textures": {"all": "block/fixture_glass"}}))
    write(later / "models/item/fixture_layer_gap.json", json.dumps({"parent": "item/generated", "textures": {"layer0": "item/fixture_holes"}}))


# -- logs -----------------------------------------------------------------------

LOG_HEAD = re.compile(r"^\[[^\]]*\] \[[^/\]]*/(\w+)\]: (.*)$")
STACK = re.compile(r"^\s+at |^\s*\.\.\. \d+ more|^Caused by|^[\w.$]+(Exception|Error)\b|^WARNING:")


def warnings(log: Path) -> list[str]:
    """WARN entries with their continuation lines (missing texture lists),
    without stack frames, and ERROR headers, sorted (loaders log in any
    order). An ERROR's cause is Java's Gson or DFU exception text with a
    stack trace, and Rust's own message line, so only the header compares."""
    entries, current = [], None
    for line in log.read_text(errors="replace").splitlines():
        head = LOG_HEAD.match(line)
        if head:
            if current:
                entries.append("\n".join(current))
            current = [f"{head.group(1)} {head.group(2)}"] if head.group(1) in ("WARN", "ERROR") else None
        elif current is not None and current[0].startswith("WARN") and not STACK.match(line):
            current.append(line)
    if current:
        entries.append("\n".join(current))
    # Java lists unresolved texture references in `Map.copyOf` order, which
    # differs per JVM.
    entries = [entry.split("\n")[0] + "\n" + "\n".join(sorted(entry.split("\n")[1:]))
               if entry.startswith("WARN Unresolved texture references") else entry for entry in entries]
    return sorted(entries)


# -- driver ---------------------------------------------------------------------


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, default=ROOT / "build/reference-head")
    parser.add_argument("--frozen-repo", type=Path, default=ROOT.parent / "MattMC_JavaPerfTesting/MattMC")
    parser.add_argument("--output", type=Path, default=Path("build/model-verification"))
    parser.add_argument("--java-home", type=Path, help="JDK 25 home; defaults to JAVA_HOME or java on PATH")
    parser.add_argument("--pairs", type=int, default=5, help="alternating fresh-JVM rounds (at least three)")
    parser.add_argument("--iterations", type=int, default=5, help="warm timing iterations per run")
    parser.add_argument("--max-ratio", type=float, default=1.10, help="largest allowed Current/Frozen timing median")
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
    report = {"schema": "mattmc-model-verification-v1", "passed": False,
              "scope": "ModelManager.reload over the checkout's resources, alone and with generated edge-case fixture packs, "
                       "atlases stitched but not uploaded: per block state the model class, particle, render-group partition and "
                       "collected parts for four seeds (AO, particle, every quad's vertices, tint, face, sprite, shade, light); "
                       "per item model id the baked item model tree and properties; the missing models; WARN entries with "
                       "continuation lines and ERROR headers (not ERROR causes: Java's are Gson/DFU exception texts). "
                       "Warm model reload time (blocks atlas precomputed) and atlas + model reload time. Does not cover "
                       "applying the reload, in-game rendering or texture upload.",
              "trees": {side: identity(tree) for side, tree in trees.items()}, "rounds": [], "max_ratio": args.max_ratio,
              "observer_sha256": sha(OBSERVER), "driver_sha256": sha(Path(__file__)),
              "jvm_args": ["-Xms1g", "-Xmx6g", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "--enable-native-access=ALL-UNNAMED"]}
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
        classes = {}
        for side in trees:
            classes[side] = out / f"observer-{side}"
            classes[side].mkdir()
            command([javac, "-nowarn", "-cp", cp[side], "-d", str(classes[side]), str(OBSERVER)], ROOT, out / f"observer-{side}.log", env)
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
                                  "-cp", str(classes[side]) + os.pathsep + cp[side], "ModelReference",
                                  str(content), str(content / "packs"), str(record), str(args.iterations)],
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
                if reference_warnings is None:
                    reference_warnings = logged
                    report["warning_entries"] = len(logged)
                elif logged != reference_warnings:
                    diff = list(difflib.unified_diff(reference_warnings, logged, "first warnings", f"{side} warnings", lineterm="", n=0))
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
    print(json.dumps({"timings": report.get("timings"), "record_lines": report.get("record_lines"),
                      "warning_entries": report.get("warning_entries")}, indent=2))
    print(result_path)


if __name__ == "__main__":
    main()
