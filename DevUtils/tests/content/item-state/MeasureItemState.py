#!/usr/bin/env python3
"""One fresh JVM per side: early diagnostic hot-access and complete stock-chest cost.

Five samples in one JVM are not independent trials. Reports compiler/GC activity,
boot time separately and full output checksums. Never emits an acceptance pass.
Run only after build/tests stop; keep the output directory even on failure.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import time
import zipfile
from VerifyItemState import ROOT, FROZEN, class_seal, sha, prepend_pre_overlays


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--pre', type=Path, required=True)
    parser.add_argument('--pre-overlay', type=Path, action='append', default=[], help='immutable original upstream-family overlay; repeat as needed')
    parser.add_argument('--complete-pre', action='store_true', help='--pre is a complete compiled image: skip the automatic historical menu/inventory/r5 overlays')
    parser.add_argument('--origin-guard', action='store_true', help='after timing, rerun each side once untimed with class-load logging and record class/native origins')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--timeout', type=float, default=240)
    parser.add_argument('--workload', choices=['stock', 'generic-debt', 'stock-transfer', 'inventory', 'atomic-lower-bound', 'owner-access', 'broadcast', 'equality', 'player-inventory'], default='stock')
    parser.add_argument('--owner-shape', choices=['full', 'small'], default='full', help='32 or 1 active overrides; applies to owner-access only')
    args = parser.parse_args()
    out = args.output.resolve(); pre = args.pre.resolve()
    if not out.is_relative_to(ROOT / 'build') or out.exists():
        parser.error('--output must be a new directory under build/')
    if args.complete_pre and args.pre_overlay:
        parser.error('--complete-pre takes no --pre-overlay')
    manifest = json.loads((pre / 'manifest.json').read_text())
    assert sha(pre / 'classes.jar') == manifest['jar_sha256']
    with zipfile.ZipFile(pre / 'classes.jar') as jar:
        for name, digest in manifest['files'].items():
            assert (sha(pre / name) if name.startswith('native/') else hashlib.sha256(jar.read(name)).hexdigest()) == digest
    native = {'frozen': FROZEN / 'build/rust/native', 'pre': pre / 'native', 'current': ROOT / 'build/rust/native'}
    libraries = {side: {p.name: {'sha256': sha(p), 'inode': p.stat().st_ino} for p in directory.glob('*mattmc*')} for side, directory in native.items()}
    # Timing a dev-profile (opt-level 0) library is meaningless: a Gradle run without
    # -PmattmcRustProfile=release rebuilds build/rust/native in that profile.
    def section_names(path):
        # ELF64 little-endian section header names (a string search would match literals in the data).
        import struct
        data = path.read_bytes()
        if data[:4] != b'\x7fELF' or data[4] != 2: return set()
        shoff, = struct.unpack_from('<Q', data, 0x28); shentsize, shnum, shstrndx = struct.unpack_from('<HHH', data, 0x3A)
        strtab_offset, = struct.unpack_from('<Q', data, shoff + shstrndx * shentsize + 0x18)
        names = set()
        for index in range(shnum):
            name, = struct.unpack_from('<I', data, shoff + index * shentsize)
            end = data.index(b'\0', strtab_offset + name); names.add(data[strtab_offset + name:end].decode())
        return names
    for side, directory in native.items():
        for library in directory.glob('*mattmc*'):
            if '.debug_info' in section_names(library):
                parser.error(f'{side} native {library} is a debug build; rebuild with ./gradlew -PmattmcRustProfile=release classes')
    cp = {'frozen': (ROOT / 'build/item-state-frozen-classpath.txt').read_text().strip(),
          'pre': (pre / 'classpath.txt').read_text().strip(),
          'current': (ROOT / 'build/item-state-current-classpath.txt').read_text().strip()}
    menu_pre = ROOT / 'build/item-state-pre-stock-menu'
    menu_manifest = None
    if not args.complete_pre and menu_pre.exists():
        menu_manifest = json.loads((menu_pre / 'manifest.json').read_text())
        assert sha(menu_pre / 'classes.jar') == menu_manifest['jar_sha256']
        cp['pre'] = str(menu_pre / 'classes.jar') + ':' + cp['pre']
    inventory_pre = ROOT / 'build/item-state-pre-inventory'
    inventory_manifest = None
    if not args.complete_pre and inventory_pre.exists():
        inventory_manifest = json.loads((inventory_pre / 'manifest.json').read_text())
        assert sha(inventory_pre / 'classes.jar') == inventory_manifest['jar_sha256']
        with zipfile.ZipFile(inventory_pre / 'classes.jar') as jar:
            for name, digest in inventory_manifest['files'].items():
                assert hashlib.sha256(jar.read(name)).hexdigest() == digest
        cp['pre'] = str(inventory_pre / 'classes.jar') + ':' + cp['pre']
    r5_pre = ROOT / 'build/item-state-pre-r5'
    r5_manifest = None
    if not args.complete_pre and r5_pre.exists():
        r5_manifest = json.loads((r5_pre / 'manifest.json').read_text())
        assert sha(r5_pre / 'classes.jar') == r5_manifest['jar_sha256']
        with zipfile.ZipFile(r5_pre / 'classes.jar') as jar:
            for name, digest in r5_manifest['files'].items():
                assert hashlib.sha256(jar.read(name)).hexdigest() == digest
        cp['pre'] = str(r5_pre / 'classes.jar') + ':' + cp['pre']
    cp['pre'], extra_pre_manifests = prepend_pre_overlays(cp['pre'], args.pre_overlay)
    inventory = args.workload == 'inventory'
    mocked = inventory or args.workload == 'player-inventory'
    if mocked:
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
        extra = []
        for family in ['org.mockito/mockito-core', 'net.bytebuddy/byte-buddy', 'net.bytebuddy/byte-buddy-agent', 'org.objenesis/objenesis']:
            files = sorted((cache / family).glob('*/*/*.jar'))
            assert len(files) == 1, (family, files)
            extra.append(str(files[0]))
        cp = {side: value + ':' + ':'.join(extra) for side, value in cp.items()}
    transfer = args.workload in ('stock-transfer', 'inventory')
    atomic = args.workload == 'atomic-lower-bound'
    owner = args.workload == 'owner-access'
    isolated = atomic or owner
    source = Path(__file__).with_name('BroadcastTimingReference.java' if args.workload == 'broadcast' else 'EqualityTimingReference.java' if args.workload == 'equality' else 'PlayerInventoryTimingReference.java' if args.workload == 'player-inventory' else 'OwnerAccessTimingReference.java' if owner else 'AtomicAccessTimingReference.java' if atomic else 'InventoryTimingReference.java' if inventory else 'StockChestTransferTimingReference.java' if transfer else 'ItemStateGenericDebtReference.java' if args.workload == 'generic-debt' else 'ItemStateTimingReference.java')
    adapter = Path(__file__).with_name('OwnerAccessTimingCurrent.java' if owner else 'AtomicAccessTimingCurrent.java') if isolated else None
    dependencies = [Path(__file__).with_name('StockChestTransferTimingReference.java')] if inventory or args.workload in ('broadcast', 'equality', 'player-inventory') else []
    before = class_seal()
    out.mkdir(); classes = out / 'observer'; classes.mkdir(); (out / source.name).write_bytes(source.read_bytes())
    receipt = {'kind': 'EARLY DIAGNOSTIC ONLY; one JVM per side, five dependent samples',
               'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, cwd=ROOT).strip(),
               'status': subprocess.check_output(['git', 'status', '--porcelain'], text=True, cwd=ROOT),
               'reference_sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, cwd=FROZEN).strip(),
               'pre_manifest': manifest, 'complete_pre': args.complete_pre, 'origin_guard_requested': args.origin_guard, 'menu_pre_manifest': menu_manifest, 'inventory_pre_manifest': inventory_manifest, 'r5_pre_manifest': r5_manifest, 'extra_pre_manifests': extra_pre_manifests, 'source_sha256': sha(source), 'driver_sha256': sha(Path(__file__)),
               'compiled_classes': before, 'native_files': libraries, 'runs': [], 'summary': {}, 'complete': False,
               'limitations': 'No direct retained serialization admission proof; no process-level repetitions, pressure gating or acceptance claim. Stock-transfer times complete quickMoveStack, including facade/native adoption; reset and complete output consumption are outside timing. World is null; Java thread allocation excludes Rust allocations.',
               'workload': args.workload, 'owner_shape': args.owner_shape if owner else None, 'adapter_sha256': sha(adapter) if adapter else None,
               'dependency_sources': {file.name: sha(file) for file in dependencies},
               'kernel_exclusions': 'native default store, ItemStack and output' if owner else 'key lookup, prototype, COW, ordered insert/remove, callbacks, ItemStack and output' if atomic else None}
    if inventory: receipt['limitations'] += ' Connected inventory uses identical stub-only mocked external Player on every side; six original forward cases run unchanged first, then19reverse/add/pick/repeated caller shapes. Reset, serialized chest/inventory verification and consumption remain outside timing; all snapshot/insert facades are inside complete calls.'
    if args.workload == 'generic-debt':
        original = Path(__file__).with_name('ItemStateTimingReference.java')
        original_text = original.read_text(); companion_text = source.read_text()
        assert original_text.split('    public static void main(', 1)[0] == companion_text.split('    public static void main(', 1)[0].replace('ItemStateGenericDebtReference', 'ItemStateTimingReference')
        receipt['original_generic_source_sha256'] = sha(original)
        receipt['admission_change'] = 'Companion recognizes the inherited Stack interface; original timed methods and native admission assertion retained. Original failing probe remains unchanged.'
    def save(): (out / 'receipt.json').write_text(json.dumps(receipt, indent=2))
    save()
    for file in dependencies: (out / file.name).write_bytes(file.read_bytes())
    subprocess.run([str(args.java_home / 'bin/javac'), '-cp', cp['frozen'], '-d', str(classes), str(source), *map(str, dependencies)], check=True)
    if adapter:
        (out / adapter.name).write_bytes(adapter.read_bytes())
        subprocess.run([str(args.java_home / 'bin/javac'), '-cp', str(classes) + ':' + cp['current'], '-d', str(classes), str(adapter)], check=True)
    variants = [('pre', 'pre', 'java_map'), ('frozen', 'frozen', 'java_map'), ('current_control', 'current', 'java_map'), ('current_kernel', 'current', 'atomic_lower_bound')] if isolated else [('pre', 'pre', 'false'), ('frozen', 'frozen', 'false'), ('current', 'current', 'true')]
    if owner: variants[-1] = ('current_owner', 'current', 'dynamic_owner')
    main_class = 'net.minecraft.world.item.' + source.stem if isolated else source.stem
    for side, base_side, mode in variants:
        command = [str(args.java_home / 'bin/java'), '-Xmx1g', '--enable-native-access=ALL-UNNAMED',
                   '-Dmattmc.rust.natives.dir=' + str(native[base_side]), '-cp', str(classes) + ':' + cp[base_side],
                   main_class, str(out / (side + '.csv')), mode]
        if mocked: command.insert(1, '-Dnet.bytebuddy.experimental=true')
        if owner: command.append(args.owner_shape)
        mapped = []; begin = time.monotonic(); print('START', side, flush=True)
        with (out / (side + '.log')).open('w') as log:
            process = subprocess.Popen(command, cwd=out, stdout=log, stderr=subprocess.STDOUT)
            try:
                while process.poll() is None:
                    if not mapped:
                        try: mapped = [line for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines() if 'mattmc_rust' in line]
                        except FileNotFoundError: pass
                    if time.monotonic() - begin > args.timeout: raise TimeoutError(side)
                    time.sleep(.1)
            finally:
                if process.poll() is None:
                    process.terminate()
                    try: process.wait(timeout=5)
                    except subprocess.TimeoutExpired: process.kill(); process.wait()
        assert process.returncode == 0, (side, process.returncode)
        assert bool(mapped) == bool(libraries[base_side])
        for line in mapped:
            fields = line.split(maxsplit=5); file = Path(fields[5]); expected = native[base_side] / file.name
            assert file.resolve() == expected.resolve() and int(fields[4]) == libraries[base_side][file.name]['inode']
        for name, identity in libraries[base_side].items():
            file = native[base_side] / name; assert sha(file) == identity['sha256'] and file.stat().st_ino == identity['inode']
        lines = (out / (side + '.csv')).read_text().splitlines(); rows = list(csv.DictReader(line for line in lines if not line.startswith('#')))
        result = {'side': side, 'command': command, 'elapsed_seconds': time.monotonic() - begin, 'admission_and_boot': lines[0],
                  'native_mappings': mapped, 'output_sha256': sha(out / (side + '.csv')), 'rows': rows,
                  'owner_contracts': [line for line in lines if line.startswith('# contract ')], 'save_checks': [line for line in lines if line.startswith('# save ')]}
        receipt['runs'].append(result); save(); print('FINISH', side, flush=True)
    if owner:
        expected = receipt['runs'][0]['owner_contracts']
        assert expected
        assert all(run['owner_contracts'] == expected for run in receipt['runs']), [(run['side'], run['owner_contracts']) for run in receipt['runs']]
    if transfer:
        expected_saves = receipt['runs'][0]['save_checks']
        assert expected_saves and all(run['save_checks'] == expected_saves for run in receipt['runs'])
    runs = {run['side']: run['rows'] for run in receipt['runs']}
    for workload in dict.fromkeys(row['workload'] for row in runs['pre']):
        summary = {}; checksums = {}
        for side, rows in runs.items():
            selected = [row for row in rows if row['workload'] == workload]
            checksums[side] = [row['checksum'] for row in selected]
            quiet = [int(row['elapsed_ns']) / int(row['operations']) for row in selected if row['compiler_ms'] == '0' and row['gc_count'] == '0']
            summary[side] = {'all_sample_ns_per_operation': [int(row['elapsed_ns']) / int(row['operations']) for row in selected],
                             'quiet_sample_count': len(quiet), 'quiet_median_ns_per_operation': statistics.median(quiet) if quiet else None}
            if owner or transfer:
                summary[side]['allocated_bytes_per_operation'] = [int(row['allocated_bytes']) / int(row['operations']) for row in selected]
        assert all(value == checksums['pre'] for value in checksums.values()), (workload, checksums)
        if owner:
            for field in ['prototype_calls', 'equals_calls']:
                expected = [row[field] for row in runs['pre'] if row['workload'] == workload]
                for side, rows in runs.items():
                    actual = [row[field] for row in rows if row['workload'] == workload]
                    assert actual == expected, (workload, side, field, expected, actual)
        receipt['summary'][workload] = summary
    assert class_seal() == before and sha(source) == receipt['source_sha256']
    assert {file.name: sha(file) for file in dependencies} == receipt['dependency_sources']
    if adapter: assert sha(adapter) == receipt['adapter_sha256']
    if args.origin_guard:
        receipt['origin_guard'] = origin_guard(receipt['runs'], out, native, cp, libraries)
        assert class_seal() == before
    receipt['complete'] = True; save(); print(json.dumps(receipt['summary'], indent=2), flush=True)


GUARDED = ['net.minecraft.world.item.ItemStack', 'net.minecraft.world.item.NativeItemState', 'net.minecraft.core.component.PatchedDataComponentMap',
           'net.minecraft.world.entity.player.Inventory', 'net.minecraft.world.inventory.AbstractContainerMenu', 'net.minecraft.world.inventory.ChestMenu',
           'net.minecraft.world.inventory.Slot']


def origin_guard(runs, out, native, cp, libraries):
    """Untimed separate processes with each timed side's exact command plus class/library logging."""
    guard = {}
    for run in runs:
        side = run['side']; base_side = 'current' if side.startswith('current') else side
        log = out / (side + '-origin.log'); csv_out = out / (side + '-origin.csv')
        command = list(run['command']); command.insert(1, f'-Xlog:class+load=info,library=info:file={log}:uptime,tags')
        command[command.index(str(out / (side + '.csv')))] = str(csv_out)
        subprocess.run(command, cwd=out, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT, check=True, timeout=600)
        origins = {}; game = {}; loaded = set()
        for line in log.read_text().splitlines():
            if 'source:' in line and '[class,load' in line:
                name = line.split(']', 2)[-1].split()[0]; origin = line.split('source: ', 1)[1].strip()
                if name in GUARDED: origins[name] = origin
                if (name.startswith('net.minecraft.') or name.startswith('com.mojang.')) and origin.startswith(('file:', 'jar:')): game[origin] = game.get(origin, 0) + 1
            if 'mattmc_rust' in line and '[library' in line:
                loaded.update(part for part in line.split() if 'mattmc_rust' in part)
        entries = cp[base_side].split(':')
        expected = native[base_side].resolve()
        required = set(GUARDED) - ({'net.minecraft.world.item.NativeItemState'} if base_side == 'frozen' else set())
        assert required <= set(origins), (side, sorted(required - set(origins)))
        assert bool(loaded) == bool(libraries[base_side]), (side, loaded)
        assert all(Path(part.strip('"\',')).resolve().parent == expected for part in loaded), (side, loaded)
        if base_side == 'pre' and entries[0].endswith('classes.jar'):
            live = str(ROOT / 'build/classes/java/main')
            assert not any(live in origin for origin in game), (side, {o: n for o, n in game.items() if live in o})
        guard[side] = {'command': command, 'guarded_class_origins': origins, 'game_class_origin_counts': game,
                       'loaded_native': sorted(loaded), 'log_sha256': sha(log), 'output_sha256': sha(csv_out)}
    return guard

if __name__ == '__main__': main()
