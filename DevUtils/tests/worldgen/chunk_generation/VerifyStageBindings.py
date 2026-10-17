#!/usr/bin/env python3
"""Actual task/callback binding contracts and intended pre-binding rejection.

Compile the identical portable fixture against complete immutable runtimes.
The Current task must consume the binding created by its actual admission;
compilation failure or a missing symbol is never an intended negative witness.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

import VerifyChunkGeneration as probe

HERE = Path(__file__).resolve().parent
ROOT = probe.ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True, help='Current test runtime classpath')
    parser.add_argument('--pre-runtime', type=Path, required=True)
    parser.add_argument('--frozen-classpath-file', type=Path, required=True)
    parser.add_argument('--frozen-native-dir', type=Path, required=True, help='native image paired with pinned Frozen classes')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to(ROOT / 'build'):
        raise ValueError('output must be below build/')
    out.mkdir(parents=True, exist_ok=False)
    pre = args.pre_runtime.resolve()
    reference = probe.verify_pre(pre)
    before = probe.end.inputs(ROOT)
    jdk = probe.full.JDK
    test_cp = args.classpath_file.read_text().strip()
    jars = [value for value in test_cp.split(os.pathsep) if Path(value).is_file()]
    fixture = ROOT / 'src/test/java/net/minecraft/server/level'
    sources = [fixture / 'GenerationChunkHolderContractTest.java', fixture / 'ChunkStageInvocationContractTest.java', fixture / 'ChunkStageBindingContractTest.java',
               HERE / 'GenerationContractRunner.java']
    source_hashes = {str(p.relative_to(ROOT)): probe.full.sha(p) for p in sources}
    runtimes = {
        'frozen': (args.frozen_classpath_file.read_text().strip(), args.frozen_native_dir.resolve()),
        'pre': ((pre / 'classpath.txt').read_text().strip(), pre / 'native'),
        'current': (test_cp, ROOT / 'build/rust/native'),
    }
    rows = []
    for side, (runtime, native) in runtimes.items():
        folder = out / side
        classes = folder / 'classes'
        classes.mkdir(parents=True)
        cp = os.pathsep.join([str(classes), runtime, *jars])
        with (folder / 'compile.log').open('w') as log:
            compiled = subprocess.run([str(jdk / 'javac'), '-cp', cp, '-d', str(classes), *map(str, sources)],
                                      stdout=log, stderr=subprocess.STDOUT)
        if compiled.returncode:
            raise RuntimeError('fixture compile failed: ' + str(folder / 'compile.log'))
        for marker in [False, True]:
            label = side + ('-marker' if marker else '')
            command = [str(jdk / 'java'), '--enable-native-access=ALL-UNNAMED', '-Dnet.bytebuddy.experimental=true',
                       '-Dmattmc.rust.natives.dir=' + str(native),
                       '-Dstage.bindingWitness=' + str(side == 'current' or marker).lower()]
            command += ['-cp', cp, 'GenerationContractRunner', 'net.minecraft.server.level.ChunkStageBindingContractTest']
            (out / (label + '-command.json')).write_text(json.dumps(command, indent=2) + '\n')
            mappings = {}
            with (out / (label + '.log')).open('w') as log:
                process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
                try:
                    deadline = time.monotonic() + 120
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                if 'mattmc' in line and '.so' in line:
                                    path = Path(line.split()[-1])
                                    if path.is_file() and str(path) not in mappings:
                                        mappings[str(path)] = probe.full.sha(path)
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() > deadline:
                            raise TimeoutError('stage fixture timed out: ' + label)
                        time.sleep(.1)
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.wait()
            text = (out / (label + '.log')).read_text()
            intended = marker and side != 'current'
            killed = process.returncode != 0 and 'actual task still has no native stage binding owner' in text
            if side != 'frozen' and probe.full.sha(native / 'mattmc_rust-linux-x64.so') not in mappings.values():
                raise RuntimeError('actual native runtime not observed: ' + label)
            if intended and not killed or not intended and process.returncode:
                raise RuntimeError('stage witness mismatch: ' + label)
            rows.append({'case': label, 'exit': process.returncode, 'intended_negative': intended,
                         'intended_witness_observed': killed, 'native_mappings': mappings})
            (out / 'rows.json').write_text(json.dumps(rows, indent=2) + '\n')
            print(label, 'intended missing binding owner failure' if intended else 'PASS', flush=True)
    after = probe.end.inputs(ROOT)
    if before != after or source_hashes != {str(p.relative_to(ROOT)): probe.full.sha(p) for p in sources}:
        raise RuntimeError('production image or fixture changed during stage witness')
    probe.verify_pre(pre)
    (out / 'result.json').write_text(json.dumps({'passed': True, 'before': before, 'after': after,
        'pre_head': reference['source_head'], 'sources': source_hashes, 'rows': rows,
        'scope': 'actual task admission, returned-future consumption and retained callback roots across cancellation/collection; physical native pending Arc custody is independently tested by NativeStageBindingOwnerTest and its omitted-pin mutation; not FULL/FPS evidence'}, indent=2) + '\n')


if __name__ == '__main__':
    main()
