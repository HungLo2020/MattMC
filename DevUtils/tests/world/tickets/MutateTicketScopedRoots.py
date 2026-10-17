#!/usr/bin/env python3
"""Require per-list VM roots, captured-consumer anchors, and scope-specific release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=ROOT / 'libraries/jdk/linux-x64')
    parser.add_argument('--classpath-file', type=Path, default=ROOT / 'build/chunk-generation/s25-test-classpath.txt')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    adapter = ROOT / 'src/main/java/net/minecraft/world/level/NativeTicketStorage.java'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    fixtures = [ROOT / 'src/test/java/net/minecraft/server/level' / (name + '.java') for name in
                ('TicketStorageCollectionContractTest', 'TicketStoreLiveBindingContractTest',
                 'TicketCrossSourceCycleContractTest', 'TicketScopedRootContractTest', 'LiveTicketConsumerContractTest', 'TicketDetachedCursorContractTest')]
    guards = {str(path): sha(path) for path in [adapter, native, *fixtures,
              ROOT / 'build/classes/java/main/net/minecraft/world/level/NativeTicketStorage.class']}
    original = adapter.read_text()
    publication = 'try { storage.tickets.put(cell, ticket); }'
    assert original.count(publication) == 1
    weak_only = original.replace(publication, 'try { java.lang.ref.Reference.reachabilityFence(ticket); }')
    release = 'if (roots != null) roots.release(sourceReleases[i*2+1]);'
    assert original.count(release) == 1
    global_unpin = original.replace(release, """if (roots instanceof ListRoots) {
        for (SourceReference candidate : SOURCES.values()) {
            SourceRoots other = candidate.get();
            if (other instanceof ListRoots) other.release(sourceReleases[i*2+1]);
        }
    } else if (roots != null) roots.release(sourceReleases[i*2+1]);""")
    capture = 'void captureList() { this.listRoots = NativeTicketStorage.this.rootsForList(this.state.get(J, 16)); }'
    assert original.count(capture) == 1
    missing_consumer_anchor = original.replace(capture, 'void captureList() {}')
    bulk_start = original.index('    static Object captureBatchList(')
    bulk_end = original.index('\n\t/** {@code getTickets(key)}', bulk_start)
    missing_bulk_anchor = original[:bulk_start] + '    static Object captureBatchList(long source, Object retained) { return null; }\n' + original[bulk_end:]

    cases = [('scoped', original, None),
             ('weak-only', weak_only, 'live native store lost its actual Ticket resource'),
             ('global-unpin', global_unpin, 'source-specific unpin erased another live source'),
             ('missing-consumer-vm-anchor', missing_consumer_anchor, 'consumer lost its captured list VM Ticket roots'),
             ('missing-bulk-vm-anchor', missing_bulk_anchor, 'consumer lost its captured list VM Ticket roots')]
    classpath = args.classpath_file.read_text().strip()
    rows = []
    for label, source, witness in cases:
        case = output / label
        classes = case / 'classes'
        classes.mkdir(parents=True)
        destination = case / 'source/net/minecraft/world/level/NativeTicketStorage.java'
        destination.parent.mkdir(parents=True)
        destination.write_text(source)
        command = [str(args.java_home / 'bin/javac'), '-g', '-cp', classpath, '-d', str(classes), str(destination),
                   *map(str, fixtures), str(ROOT / 'DevUtils/tests/worldgen/chunk_generation/GenerationContractRunner.java')]
        with (case / 'compile.log').open('w') as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
        command = [str(args.java_home / 'bin/java'), '-Xmx512m', '--enable-native-access=ALL-UNNAMED',
                   '-Dnet.bytebuddy.experimental=true', '-Dmattmc.rust.natives.dir=' + str(native.parent),
                   '-cp', str(classes) + os.pathsep + classpath, 'GenerationContractRunner',
                   *['net.minecraft.server.level.' + path.stem for path in fixtures]]
        (case / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        with (case / 'run.log').open('w') as log:
            process = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=150)
        text = (case / 'run.log').read_text()
        killed = witness is not None and process.returncode != 0 and witness in text
        if witness:
            assert killed, text[-5000:]
        else:
            assert process.returncode == 0 and '28 tests successful' in text, text[-5000:]
        rows.append({'case': label, 'exit': process.returncode, 'intended_failure': killed,
                     'adapter_sha256': sha(destination)})
        print(label, 'PASS' if witness is None else 'intended mutation detected', flush=True)
    assert all(sha(Path(path)) == expected for path, expected in guards.items()), 'Production inputs changed'
    (output / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards,
                                                    'production_unchanged': True, 'rows': rows}, indent=2) + '\n')


if __name__ == '__main__':
    main()
