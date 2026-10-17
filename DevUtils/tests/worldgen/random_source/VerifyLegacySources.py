#!/usr/bin/env python3
"""Original Legacy source public effects, sequence and committed-prefix contracts."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'noise_composite'))
import VerifyComposite as portable


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-runtime', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sides', default='frozen,pre,current')
    args = parser.parse_args()
    p = portable.probe
    root = p.ROOT
    pre = args.pre_runtime.resolve()
    out = args.output.resolve()
    sides = args.sides.split(',')
    assert len(set(sides)) == len(sides) and set(sides) <= {'frozen', 'pre', 'current'}
    assert out.is_relative_to(root / 'build')
    out.mkdir(parents=True, exist_ok=False)
    reference = p.verify_pre(pre)
    before = p.end.inputs(root)
    frozen = p.end.inputs(p.FROZEN)
    assert frozen['head'] == p.PIN
    tests = root / 'src/test/java/net/minecraft/world/level/levelgen'
    sources = [tests / (name + '.java') for name in ['LegacySourceContractTest', 'LegacyRawStateContractTest', 'LegacyGaussianAliasContractTest', 'LegacyLeafDispatchContractTest']]
    runner = root / 'DevUtils/tests/worldgen/chunk_generation/GenerationContractRunner.java'
    hashes = {str(source): p.full.sha(source) for source in [*sources, runner, Path(__file__)]}
    for source in [*sources, runner, Path(__file__)] :
        saved = out / 'sources' / source.relative_to(root)
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, saved)
    jars = [part for part in (root / 'build/chunk-generation/s25-test-classpath.txt').read_text().strip().split(os.pathsep) if Path(part).is_file()]
    runtimes = {
        'frozen': (root / 'build/chunk-generation/s28-frozen-main-classpath.txt', root / 'build/chunk-generation/s25-pre522-runtime/native'),
        'pre': (pre / 'classpath.txt', pre / 'native'),
        'current': (root / 'build/chunk-generation/s28-current-main-classpath.txt', root / 'build/rust/native'),
    }
    rows = {}
    for side in sides:
        cp, native = runtimes[side]
        folder = out / side
        classes = folder / 'classes'
        classes.mkdir(parents=True)
        selected = [source for source in sources if source.name != 'LegacyRawStateContractTest.java'] if side == 'frozen' else sources
        classpath = os.pathsep.join([str(classes), cp.read_text().strip(), *jars])
        with (folder / 'compile.log').open('w') as log:
            subprocess.run([str(p.full.JDK / 'javac'), '-cp', classpath, '-d', str(classes), *map(str, selected), str(runner)], stdout=log, stderr=subprocess.STDOUT, check=True)
        command = ['taskset', '-c', '4-7', str(p.full.JDK / 'java'), '-Xmx1g', '-Xbatch', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native), '-Xlog:class+load=info:file=' + str(folder / 'class-load.log'), '-cp', classpath, 'GenerationContractRunner', *['net.minecraft.world.level.levelgen.' + source.stem for source in selected]]
        (folder / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        row = portable.launch(command, folder / 'test.log')
        log = (folder / 'test.log').read_text()
        counts = {key: int(re.search(r'\[\s*(\d+) tests ' + key + r'\s*\]', log).group(1)) for key in ['found', 'successful', 'failed', 'skipped', 'aborted']}
        expected = 15 if side == 'frozen' else 18
        assert counts == {'found': expected, 'successful': expected, 'failed': 0, 'skipped': 0, 'aborted': 0}, counts
        if side != 'frozen':
            assert p.full.sha(native / 'mattmc_rust-linux-x64.so') in row['native_mappings'].values()
        row['tests'] = counts
        rows[side] = row
        (out / 'rows.json').write_text(json.dumps(rows, indent=2) + '\n')
        print(side, counts, 'PASS', flush=True)
    assert before == p.end.inputs(root)
    assert frozen == p.end.inputs(p.FROZEN)
    p.verify_pre(pre)
    assert hashes == {str(source): p.full.sha(source) for source in [*sources, runner, Path(__file__)]}
    (out / 'result.json').write_text(json.dumps({'passed': True, 'before': before, 'after': p.end.inputs(root), 'frozen': frozen, 'pre_head': reference['source_head'], 'sources': hashes, 'sides': sides, 'rows': rows, 'scope': 'Original Legacy public sequence/JDK differential/shiftmask/draw order/failure/reentry/constructor/private Gaussian reset/public target aliases/real leaf subclass callbacks. Three raw bridge cases absent Frozen and NOT RUN there. Baseline first; no performance or whole-RNG completion claim.'}, indent=2) + '\n')


if __name__ == '__main__':
    main()
