#!/usr/bin/env python3
"""Verify final native store/graph release completes the actual Java unpin."""
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
    storage = ROOT / 'src/main/java/net/minecraft/world/level/NativeTicketStorage.java'
    generation = ROOT / 'src/main/java/net/minecraft/server/level/NativeChunkGeneration.java'
    fixture = ROOT / 'src/test/java/net/minecraft/server/level/NativeTicketStoreLifetimeContractTest.java'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    guarded = [storage, generation, fixture, native,
               ROOT / 'build/classes/java/main/net/minecraft/world/level/NativeTicketStorage.class',
               ROOT / 'build/classes/java/main/net/minecraft/server/level/NativeChunkGeneration.class']
    guards = {str(path): sha(path) for path in guarded}
    original_storage, original_generation = storage.read_text(), generation.read_text()
    start = original_storage.index('    private static void release(long handle) {')
    end = original_storage.index('\n    }', start) + len('\n    }')
    release = original_storage[start:end]
    assert release.count('finishNativeRelease();') == 1
    graph_adoption = 'adopt(created, 1, arena, RequestBindings.GRAPH_RELEASE, true)'
    assert original_generation.count(graph_adoption) == 1
    lost_graph = original_generation.replace(graph_adoption, 'adopt(created, 1, arena, RequestBindings.GRAPH_RELEASE, false)')
    list_start = original_storage.index('    private static final class ListRelease')
    list_end = original_storage.index('    /** Primitive resource keys', list_start)
    list_release = original_storage[list_start:list_end]
    assert list_release.count('finishNativeRelease();') == 1
    lost_list = original_storage[:list_start] + list_release.replace('finishNativeRelease();', '') + original_storage[list_end:]
    # Compute the all-owner variant from the complete source, not offsets shifted
    # by deleting the earlier map completion.
    lost_all = original_storage.replace('finishNativeRelease();', '')
    classpath = args.classpath_file.read_text().strip()
    cases = [('released', original_storage, original_generation, None),
             ('missing-list-owner-retirement', lost_list, original_generation, 'final retained-list resource left its counter retirement pending'),
             ('missing-final-owner-retirement', lost_all, lost_graph, 'final native source release left its counter retirement pending')]
    rows = []
    for label, storage_source, generation_source, witness in cases:
        case = output / label
        source = case / 'source'
        classes = case / 'classes'
        classes.mkdir(parents=True)
        sources = []
        for path, text in [(storage, storage_source), (generation, generation_source), (fixture, fixture.read_text())]:
            destination = source / path.relative_to(ROOT)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(text)
            sources.append(str(destination))
        compile_command = [str(args.java_home / 'bin/javac'), '-g', '-cp', classpath, '-d', str(classes), *sources]
        with (case / 'compile.log').open('w') as log:
            subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT, check=True)
        command = [str(args.java_home / 'bin/java'), '-Xmx512m', '--enable-native-access=ALL-UNNAMED',
                   '-Dnet.bytebuddy.experimental=true', '-Dmattmc.rust.natives.dir=' + str(native.parent),
                   '-cp', str(classes) + os.pathsep + classpath, 'GenerationContractRunner',
                   'net.minecraft.server.level.NativeTicketStoreLifetimeContractTest']
        # The runner is a maintained verification source, independent of test runtime output.
        with (case / 'runner-compile.log').open('w') as log:
            subprocess.run([str(args.java_home / 'bin/javac'), '-cp', classpath, '-d', str(classes),
                            str(ROOT / 'DevUtils/tests/worldgen/chunk_generation/GenerationContractRunner.java')],
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        (case / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        with (case / 'run.log').open('w') as log:
            process = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=90)
        text = (case / 'run.log').read_text()
        killed = witness is not None and process.returncode != 0 and witness in text
        if witness:
            assert killed, text[-4000:]
        else:
            assert process.returncode == 0 and '3 tests successful' in text, text[-4000:]
        rows.append({'case': label, 'exit': process.returncode, 'intended_failure': killed})
        print(label, 'PASS' if not witness else 'intended mutation detected', flush=True)
    assert all(sha(Path(path)) == expected for path, expected in guards.items()), 'Production inputs changed'
    (output / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards,
                                                    'production_unchanged': True, 'rows': rows}, indent=2) + '\n')


if __name__ == '__main__':
    main()
