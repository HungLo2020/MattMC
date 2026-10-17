#!/usr/bin/env python3
"""Compare actual leaf noise constructor contracts and public output across runtimes."""
import argparse
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'chunk_generation'))
import VerifyChunkGeneration as probe


def launch(command, log_path):
    mappings, peak_rss, swap = {}, 0, 0
    with log_path.open('w') as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 300
            while process.poll() is None:
                try:
                    for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                        if 'mattmc' in line and '.so' in line:
                            path = Path(line.split()[-1])
                            if path.is_file() and str(path) not in mappings:
                                mappings[str(path)] = probe.full.sha(path)
                    status = Path(f'/proc/{process.pid}/status').read_text().splitlines()
                    peak_rss = max([peak_rss] + [int(s.split()[1])*1024 for s in status if s.startswith('VmRSS:')])
                    swap = max([swap] + [int(s.split()[1])*1024 for s in status if s.startswith('VmSwap:')])
                except (FileNotFoundError, ProcessLookupError):
                    pass
                if time.monotonic() > deadline:
                    raise TimeoutError('noise constructor probe timed out')
                time.sleep(.05)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    if process.returncode:
        raise RuntimeError(f'probe exit {process.returncode}: {log_path}')
    return {'exit': process.returncode, 'native_mappings': mappings, 'sampled_peak_rss_bytes': peak_rss, 'max_swap_bytes': swap}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-runtime', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--screen', action='store_true', help='include the short public-constructor latency/work/resource screen')
    parser.add_argument('--screen-only', action='store_true', help='run only the constructor screen, retaining separate portable contract receipts')
    args = parser.parse_args()
    root = probe.ROOT
    out = args.output.resolve()
    if not out.is_relative_to(root / 'build'):
        raise ValueError('output must be below build/')
    out.mkdir(parents=True, exist_ok=False)
    pre = args.pre_runtime.resolve()
    reference = probe.verify_pre(pre)
    before = probe.end.inputs(root)
    frozen_before = probe.end.inputs(probe.FROZEN)
    if frozen_before['head'] != probe.PIN:
        raise ValueError('Frozen reference is not the original pinned baseline')
    test_cp = (root / 'build/chunk-generation/s25-test-classpath.txt').read_text().strip()
    jars = [v for v in test_cp.split(os.pathsep) if Path(v).is_file()]
    contract = root / 'src/test/java/net/minecraft/world/level/levelgen/synth/NoiseConstructorContractTest.java'
    sources = [contract, HERE / 'NoiseConstructorProbe.java', HERE / 'NoiseConstructorScreen.java', HERE.parent / 'chunk_generation/GenerationContractRunner.java']
    source_hashes = {str(p.relative_to(root)): probe.full.sha(p) for p in sources}
    sides = {
        'frozen': ((root / 'build/chunk-generation/s28-frozen-main-classpath.txt').read_text().strip(), root / 'build/chunk-generation/s25-pre522-runtime/native'),
        'pre': ((pre / 'classpath.txt').read_text().strip(), pre / 'native'),
        'current': ((root / 'build/chunk-generation/s28-current-main-classpath.txt').read_text().strip(), root / 'build/rust/native'),
    }
    rows = []
    for side, (runtime, native) in sides.items():
        folder = out / side
        classes = folder / 'classes'
        classes.mkdir(parents=True)
        cp = os.pathsep.join([str(classes), runtime, *jars])
        command = [str(probe.full.JDK / 'javac'), '-cp', cp, '-d', str(classes), *map(str, sources)]
        with (folder / 'compile.log').open('w') as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
        common = ['taskset', '-c', '4' if args.screen_only else '4-7', str(probe.full.JDK / 'java'), '-Xms512m', '-Xmx2g', '-Xbatch', '-Dnet.bytebuddy.experimental=true', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cp]
        jobs = [('contracts', ['GenerationContractRunner', 'net.minecraft.world.level.levelgen.synth.NoiseConstructorContractTest']), ('corpus', ['net.minecraft.world.level.levelgen.synth.NoiseConstructorProbe'])]
        if args.screen_only:
            jobs = []
        if args.screen or args.screen_only:
            jobs.append(('screen', ['net.minecraft.world.level.levelgen.synth.NoiseConstructorScreen']))
        for name, tail in jobs:
            command = common + tail
            (folder / (name + '-command.json')).write_text(json.dumps(command, indent=2) + '\n')
            result = launch(command, folder / (name + '.log'))
            text = (folder / (name + '.log')).read_text()
            if (side == 'current' or (side == 'pre' and name == 'corpus')) and probe.full.sha(native / 'mattmc_rust-linux-x64.so') not in result['native_mappings'].values():
                raise RuntimeError('actual native image was not observed')
            if name == 'corpus':
                result['corpus'] = next(line.split('NOISE_CONSTRUCTOR_CORPUS ', 1)[1] for line in text.splitlines() if 'NOISE_CONSTRUCTOR_CORPUS ' in line)
            if name == 'screen':
                result['samples'] = [json.loads(line.split('NOISE_SCREEN ', 1)[1]) for line in text.splitlines() if 'NOISE_SCREEN ' in line]
                result['retirement'] = [line for line in text.splitlines() if line.startswith('NOISE_RETIREMENT ')]
            result.update(side=side, job=name)
            rows.append(result)
            (out / 'rows.json').write_text(json.dumps(rows, indent=2) + '\n')
            print(side, name, 'PASS', result.get('corpus', ''), flush=True)
    hashes = [r['corpus'] for r in rows if r['job'] == 'corpus']
    if any(value != hashes[0] for value in hashes):
        raise RuntimeError('actual constructor metadata, sampler, derivative or next RNG word differ; retain all side receipts')
    if args.screen or args.screen_only:
        screen = {row['side']: row for row in rows if row['job'] == 'screen'}
        summary = []
        for kind in range(2):
            for family in range(2):
                selected = {side: [s for s in row['samples'] if s['accepted'] and s['kind'] == kind and s['family'] == family] for side, row in screen.items()}
                if any(len(samples) != 6 for samples in selected.values()):
                    raise RuntimeError('insufficient accepted screen samples')
                outputs = {s['sum'] for samples in selected.values() for s in samples}
                if len(outputs) != 1:
                    raise RuntimeError('screen output or next RNG word differs')
                medians = {side: {'ns_per_constructor': statistics.median(s['elapsed_ns']/s['count'] for s in samples), 'java_bytes_per_constructor': statistics.median(s['java_bytes']/s['count'] for s in samples)} for side, samples in selected.items()}
                summary.append({'kind': kind, 'family': family, 'sum': outputs.pop(), 'medians': medians, 'current_pre_ratio': medians['current']['ns_per_constructor']/medians['pre']['ns_per_constructor'], 'current_frozen_ratio': medians['current']['ns_per_constructor']/medians['frozen']['ns_per_constructor']})
        (out / 'screen-summary.json').write_text(json.dumps({'rows': summary, 'resources': {side: {key: row[key] for key in ['sampled_peak_rss_bytes','max_swap_bytes']} for side, row in screen.items()}, 'scope': 'intermediate public leaf-constructor screen with actual RNG effects; no native-allocator attribution or final subsystem/worldgen performance claim'}, indent=2) + '\n')
    after = probe.end.inputs(root)
    if before != after or frozen_before != probe.end.inputs(probe.FROZEN):
        raise RuntimeError('production runtime changed during portable verification')
    if source_hashes != {str(p.relative_to(root)): probe.full.sha(p) for p in sources}:
        raise RuntimeError('portable fixtures changed during verification')
    probe.verify_pre(pre)
    (out / 'result.json').write_text(json.dumps({'passed': True, 'before': before, 'after': after, 'frozen': frozen_before, 'pre_head': reference['source_head'], 'sources': source_hashes, 'rows': rows, 'scope': 'actual public constructor screen only; portable contracts/corpus require their separate receipt' if args.screen_only else 'actual leaf constructor callbacks/failure/reentry and independent public metadata/sampler/derivative/next-RNG corpus; no performance or complete-noise/worldgen acceptance'}, indent=2) + '\n')


if __name__ == '__main__':
    main()
