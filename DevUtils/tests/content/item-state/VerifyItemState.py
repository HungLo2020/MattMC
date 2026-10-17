#!/usr/bin/env python3
"""Run unchanged item-state contracts against actual Frozen, sealed pre-change and Current.

Uses existing compiled classpaths; never rebuilds or changes the reference. Preserve
failed output directories. This is contract evidence, not a gameplay/performance gate.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / "MattMC_JavaPerfTesting/MattMC"
FAMILIES = ["world/item/enchantment/ItemEnchantments", "world/inventory/Slot", "world/Container", "world/entity/EntityEquipment", "world/inventory/InventoryMenu", "world/entity/player/Inventory", "world/item/Item", "world/item/NativeItems", "world/item/ItemStack", "world/item/NativeItemStacks",
            "world/item/NativeItemState", "world/item/NativeItemSlots", "world/item/NativeItemRoots", "world/inventory/ChestMenu", "world/inventory/AbstractContainerMenu", "core/component/DataComponentMap",
            "core/component/DataComponentPatch", "core/component/PatchedDataComponentMap", "resources/NativeData",
            "resources/NativeValues", "world/ItemStackWithSlot", "world/ContainerHelper", "world/level/storage/TagValueInput",
            "world/level/storage/TagValueOutput", "world/level/block/entity/ChestBlockEntity"]
SOURCES = {"component_payload": Path(__file__).with_name("ComponentPayloadConsumerReference.java"), "enchantment_payload": Path(__file__).with_name("EnchantmentPayloadReference.java"), "equipment_inventory": Path(__file__).with_name("EquipmentInventoryReference.java"), "menu_source": Path(__file__).with_name("MenuSourceReference.java"), "menu_state": Path(__file__).with_name("MenuStateReference.java"), "stock_container": Path(__file__).with_name("StockContainerBindingReference.java"), "inventory_menu": Path(__file__).with_name("InventoryMenuReference.java"), "equipment": Path(__file__).with_name("EquipmentStateReference.java"), "container_slot": Path(__file__).with_name("ContainerSlotReference.java"), "inventory_visit": Path(__file__).with_name("InventoryVisitReference.java"), "default_entry": Path(__file__).with_name("DefaultEntryReference.java"), "default_bootstrap": Path(__file__).with_name("DefaultBootstrapReference.java"), "inventory_scan": Path(__file__).with_name("InventoryScanReference.java"), "scalar_presence": Path(__file__).with_name("DefaultScalarPresenceReference.java"), "inventory_add": Path(__file__).with_name("InventoryAddReference.java"), "inventory_actor": Path(__file__).with_name("InventoryQuickMoveReference.java"), "inventory": Path(__file__).with_name("InventoryStateReference.java"), "default_skip": Path(__file__).with_name("DefaultIteratorSkipReference.java"), "copy_effects": Path(__file__).with_name("ItemStackCopyEffectsReference.java"), "stock_signed": Path(__file__).with_name("StockChestSignedReference.java"), "default_cursor": Path(__file__).with_name("DefaultCursorContractReference.java"), "stock_identities": Path(__file__).with_name("StockChestTransferIdentityReference.java"), "stock_effects": Path(__file__).with_name("StockChestTransferEffectsReference.java"), "stock_transfer": Path(__file__).with_name("StockChestTransferReference.java"), "contracts": Path(__file__).with_name("ItemStateContractReference.java"),
           "map_trace": Path(__file__).parent.parent / "ComponentMapTrace.java"}

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def class_seal() -> dict[str, str]:
    folder = ROOT / "build/classes/java/main/net/minecraft"
    result = {}
    for family in FAMILIES:
        path = folder / family
        files = [path.with_suffix(".class"), *path.parent.glob(path.name + "$*.class")]
        for file in files:
            if file.is_file():
                result[str(file.relative_to(ROOT))] = sha(file)
    return result

def prepend_pre_overlays(classpath: str, overlays: list[Path]) -> tuple[str, dict]:
    """Keep original non-item families when upstream changes shared classpaths."""
    manifests = {}
    for folder in overlays:
        folder = folder.resolve()
        manifest = json.loads((folder / "manifest.json").read_text())
        archive = folder / "classes.jar"
        assert sha(archive) == manifest["jar_sha256"], folder
        with zipfile.ZipFile(archive) as jar:
            for name, digest in manifest["files"].items():
                assert hashlib.sha256(jar.read(name)).hexdigest() == digest, (folder, name)
        for name, digest in manifest.get("sources", {}).items():
            assert sha(folder / "sources" / name) == digest, (folder, name)
        if "source_path" in manifest:
            assert sha(folder / Path(manifest["source_path"]).name) == manifest["source_sha256"], folder
        manifests[str(folder)] = manifest
        classpath = str(archive) + ":" + classpath
    return classpath, manifests

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--pre", type=Path, required=True, help="sealed Java-family/JNI fixture with manifest.json")
    parser.add_argument("--complete-pre", action="store_true", help="--pre is a complete compiled image: skip the automatic historical menu/inventory/r5 overlays")
    parser.add_argument("--pre-overlay", type=Path, action="append", default=[], help="immutable original upstream-family overlay; repeat as needed, last takes precedence")
    parser.add_argument("--frozen-classpath", type=Path, default=ROOT / "build/item-state-frozen-classpath.txt")
    parser.add_argument("--current-classpath", type=Path, default=ROOT / "build/item-state-current-classpath.txt")
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--observers", choices=list(SOURCES), nargs="+", default=list(SOURCES))
    parser.add_argument("--timeout", type=float, default=180)
    parser.add_argument("--reference-results", type=Path, help="reuse exact unchanged Frozen/pre rows from a passing receipt; candidate always reruns")
    args = parser.parse_args()
    if args.complete_pre and args.pre_overlay:
        parser.error("--complete-pre takes no --pre-overlay")
    if "stock_effects" in args.observers and "stock_transfer" not in args.observers:
        args.observers.append("stock_transfer")
    out = args.output.resolve()
    if not out.is_relative_to(ROOT / "build") or out.exists():
        parser.error("--output must be a new directory under build/")
    pre = args.pre.resolve()
    manifest = json.loads((pre / "manifest.json").read_text())
    assert sha(pre / "classes.jar") == manifest["jar_sha256"]
    with zipfile.ZipFile(pre / "classes.jar") as jar:
        for name, digest in manifest["files"].items():
            if name.startswith("native/"):
                assert sha(pre / name) == digest
            else:
                assert hashlib.sha256(jar.read(name)).hexdigest() == digest
    cp = {"frozen": args.frozen_classpath.read_text().strip(), "pre": (pre / "classpath.txt").read_text().strip(),
          "current": args.current_classpath.read_text().strip()}
    extra_libraries = []
    if any(name in args.observers for name in ("component_payload", "stock_effects", "copy_effects", "inventory_actor", "inventory_add", "inventory_scan", "inventory_visit", "equipment", "container_slot", "inventory_menu", "menu_source")):
        cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
        for family in ["org.mockito/mockito-core", "net.bytebuddy/byte-buddy", "net.bytebuddy/byte-buddy-agent", "org.objenesis/objenesis"]:
            files = sorted((cache / family).glob("*/*/*.jar"))
            assert len(files) == 1, (family, files)
            extra_libraries.append(str(files[0]))
        cp = {side: value + ":" + ":".join(extra_libraries) for side, value in cp.items()}
    menu_pre = ROOT / "build/item-state-pre-stock-menu"
    menu_manifest = None
    if not args.complete_pre and menu_pre.exists():
        menu_manifest = json.loads((menu_pre / "manifest.json").read_text())
        assert sha(menu_pre / "classes.jar") == menu_manifest["jar_sha256"]
        cp["pre"] = str(menu_pre / "classes.jar") + ":" + cp["pre"]
    inventory_pre = ROOT / "build/item-state-pre-inventory"
    inventory_manifest = None
    if not args.complete_pre and inventory_pre.exists():
        inventory_manifest = json.loads((inventory_pre / "manifest.json").read_text())
        assert sha(inventory_pre / "classes.jar") == inventory_manifest["jar_sha256"]
        with zipfile.ZipFile(inventory_pre / "classes.jar") as jar:
            for name, digest in inventory_manifest["files"].items():
                assert hashlib.sha256(jar.read(name)).hexdigest() == digest
        cp["pre"] = str(inventory_pre / "classes.jar") + ":" + cp["pre"]
    r5_pre = ROOT / "build/item-state-pre-r5"
    r5_manifest = None
    if not args.complete_pre and r5_pre.exists():
        r5_manifest = json.loads((r5_pre / "manifest.json").read_text())
        assert sha(r5_pre / "classes.jar") == r5_manifest["jar_sha256"]
        with zipfile.ZipFile(r5_pre / "classes.jar") as jar:
            for name, digest in r5_manifest["files"].items():
                assert hashlib.sha256(jar.read(name)).hexdigest() == digest
        cp["pre"] = str(r5_pre / "classes.jar") + ":" + cp["pre"]
    cp["pre"], extra_pre_manifests = prepend_pre_overlays(cp["pre"], args.pre_overlay)
    native = {"frozen": FROZEN / "build/rust/native", "pre": pre / "native", "current": ROOT / "build/rust/native"}
    libs = {side: {file.name: {"sha256": sha(file), "inode": file.stat().st_ino}
                   for file in directory.glob("*mattmc*")} for side, directory in native.items()}
    before = class_seal()
    out.mkdir(); classes = out / "observer"; classes.mkdir()
    source_hashes = {name: sha(SOURCES[name]) for name in args.observers}
    for name in args.observers:
        (out / SOURCES[name].name).write_bytes(SOURCES[name].read_bytes())
    receipt = {"current_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
               "current_status": subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True),
               "pre_base": manifest["base"], "pre_manifest": manifest, "pre_jar_sha256": sha(pre / "classes.jar"), "reference_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=FROZEN, text=True).strip(),
               "compiled_classes": before, "observer_sources": source_hashes, "native_files": libs,
               "complete_pre": args.complete_pre, "menu_pre_manifest": menu_manifest, "inventory_pre_manifest": inventory_manifest, "r5_pre_manifest": r5_manifest, "extra_pre_manifests": extra_pre_manifests, "driver_sha256": sha(Path(__file__)), "runs": [], "comparisons": {}, "passed": False}
    def save():
        (out / "receipt.json").write_text(json.dumps(receipt, indent=2))
    save()
    references = None
    reference_folder = None
    if args.reference_results:
        reference_folder = args.reference_results.resolve()
        references = json.loads((reference_folder / "receipt.json").read_text())
        assert references["passed"]
        assert references["reference_sha"] == receipt["reference_sha"]
        assert references["pre_manifest"] == manifest and references.get("menu_pre_manifest") == menu_manifest
        assert references.get("inventory_pre_manifest") == inventory_manifest, "supplemental inventory baseline changed"
        assert references.get("r5_pre_manifest") == r5_manifest, "supplemental r5 baseline changed"
        assert references.get("extra_pre_manifests", {}) == extra_pre_manifests, "upstream-family baseline overlays changed"
        for side in ("frozen", "pre"):
            assert references["native_files"][side] == libs[side]
        for tag in args.observers:
            assert references["observer_sources"][tag] == source_hashes[tag]
        receipt["reference_reuse"] = {"receipt": str(reference_folder / "receipt.json"), "sha256": sha(reference_folder / "receipt.json"),
            "impact": "Only assigned item-state families changed; original pre fixture and menu overlay hide those families, new runtime classes are absent from pre producer paths. Pinned native images/configuration/source observers unchanged. Source/contracts only; no performance evidence reused."}
        save()
    subprocess.run([str(args.java_home / "bin/javac"), "-cp", cp["frozen"], "-d", str(classes),
                    *[str(SOURCES[name]) for name in set(args.observers) | ({"stock_transfer"} if "stock_effects" in args.observers else set())]], check=True, cwd=ROOT)
    for tag in args.observers:
        for side in cp:
            dump = out / (tag + "-" + side + ".txt")
            command = [str(args.java_home / "bin/java"), "-Xmx1g", "-XX:-OmitStackTraceInFastThrow", "--enable-native-access=ALL-UNNAMED",
                       "-Dmattmc.rust.natives.dir=" + str(native[side]), "-cp", str(classes) + ":" + cp[side], SOURCES[tag].stem, str(dump)]
            if tag in ("component_payload", "stock_effects", "copy_effects", "inventory_actor", "inventory_add", "inventory_scan", "inventory_visit", "equipment", "container_slot", "inventory_menu", "menu_source"):
                command.insert(1, "-Dnet.bytebuddy.experimental=true")
            if tag == "stock_transfer" and side == "current":
                command.insert(1, "-Dmattmc.require.native.transfer=true")
            if references is not None and side in ("frozen", "pre"):
                original = next(run for run in references["runs"] if run["tag"] == tag and run["side"] == side)
                def normalized_command(cmd):
                    normalized = list(cmd); cp_index = normalized.index("-cp") + 1
                    normalized[cp_index] = "<observer>:" + normalized[cp_index].split(":", 1)[1]
                    normalized[-1] = "<dump>"
                    return normalized
                assert normalized_command(original["command"]) == normalized_command(command), (tag, side, "reference JVM/configuration changed")
                source_dump = reference_folder / dump.name
                assert sha(source_dump) == original["output_sha256"]
                dump.write_bytes(source_dump.read_bytes())
                old_log = reference_folder / (tag + "-" + side + ".log")
                if old_log.exists(): (out / old_log.name).write_bytes(old_log.read_bytes())
                reused = dict(original); reused["reused_from"] = str(reference_folder / "receipt.json"); reused["executed_this_run"] = False
                receipt["runs"].append(reused); save(); print("REUSE", tag, side, flush=True)
                continue
            print("START", tag, side, flush=True)
            mapped = []; begin = time.monotonic()
            with (out / (tag + "-" + side + ".log")).open("w") as log:
                process = subprocess.Popen(command, cwd=out, stdout=log, stderr=subprocess.STDOUT)
                try:
                    while process.poll() is None:
                        if not mapped:
                            try:
                                mapped = [line for line in Path(f"/proc/{process.pid}/maps").read_text().splitlines() if "mattmc_rust" in line]
                            except FileNotFoundError:
                                pass
                        if time.monotonic() - begin > args.timeout:
                            raise TimeoutError(f"{tag}/{side} exceeded {args.timeout}s")
                        time.sleep(.1)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            process.kill(); process.wait()
            assert process.returncode == 0, (tag, side, process.returncode)
            assert bool(mapped) == bool(libs[side]), (side, "unexpected native mappings")
            for line in mapped:
                fields = line.split(maxsplit=5); path = Path(fields[5]); expected = native[side] / path.name
                assert path.resolve() == expected.resolve() and int(fields[4]) == libs[side][path.name]["inode"], line
            for name, identity in libs[side].items():
                assert sha(native[side] / name) == identity["sha256"] and (native[side] / name).stat().st_ino == identity["inode"]
            receipt["runs"].append({"tag": tag, "side": side, "command": command, "native_mappings": mapped,
                                    "exit_code": 0, "elapsed_seconds": time.monotonic() - begin,
                                    "rows": len(dump.read_text().splitlines()), "output_sha256": sha(dump)})
            save(); print("FINISH", tag, side, flush=True)
        a, b, c = [(out / (tag + "-" + side + ".txt")).read_text().splitlines() for side in cp]
        assert len(a) == len(b) == len(c)
        comparison = {"rows": len(a), "frozen_pre_diff": [i for i, (x, y) in enumerate(zip(a, b)) if x != y],
                      "pre_current_diff": [i for i, (x, y) in enumerate(zip(b, c)) if x != y]}
        receipt["comparisons"][tag] = comparison; save(); print("COMPARE", tag, comparison, flush=True)
    assert class_seal() == before, "compiled classes changed during contracts"
    for tag, digest in source_hashes.items():
        assert sha(SOURCES[tag]) == digest
    receipt["passed"] = all(not row["frozen_pre_diff"] and not row["pre_current_diff"] for row in receipt["comparisons"].values())
    save()
    if not receipt["passed"]:
        raise SystemExit("Raw differences preserved in receipt; classify before claiming parity")

if __name__ == "__main__":
    main()
