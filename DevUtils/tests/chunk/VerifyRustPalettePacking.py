#!/usr/bin/env python3
"""Pinned Java parity and complete block-state palette packing performance acceptance."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = 'fffe4a073'
PACKAGE = 'net.minecraft.world.level.chunk.'


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/palette-packing-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=2)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    args = parser.parse_args()
    if not args.parity_only and args.forks < 3:
        parser.error('At least three independent process pairs required')
    if args.cpu not in os.sched_getaffinity(0):
        parser.error('CPU unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    original = subprocess.check_output(['git','show',REFERENCE+':src/main/java/net/minecraft/world/level/chunk/PalettedContainer.java'],cwd=ROOT,text=True)
    source = ROOT/'src/main/java/net/minecraft/world/level/chunk/PalettedContainer.java'
    entry = '\t\t\tif (this.getClass() == PalettedContainer.class) {\n\t\t\t\tvar packed = NativePalettePacking.pack(this.data.storage, this.data.palette, this.strategy, strategy);\n\t\t\t\tif (packed != null) return packed;\n\t\t\t}\n'
    a=original.index('public PalettedContainerRO.PackedData<T> pack(')
    pos=original.index('\t\t\tBitStorage bitStorage',a)
    from VerifyRustPaletteHistogram import ENTRY as histogram_entry
    from VerifyRustPaletteResize import strip_resize
    from VerifyRustPaletteUnpacking import strip_unpack
    if strip_resize(strip_unpack(source.read_text())).replace(histogram_entry, "", 1)!=original[:pos]+entry+original[pos:]:
        raise RuntimeError('Original source/helpers differ outside the native dispatch insertion')
    a=original.index('\tpublic PalettedContainerRO.PackedData<T> pack(')
    b=original.index('\n\t@Override\n\tpublic int getSerializedSize',a)
    body=original[a:b].replace('public PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy)','static <T> PalettedContainerRO.PackedData<T> pack(PalettedContainer<T> source, Strategy<T> strategy)',1).replace('this.acquire()','source.acquire()').replace('this.release()','source.release()').replace('this.data.storage','source.dataForNativeScan().storage()').replace('this.data.palette','source.dataForNativeScan().palette()')
    oracle=ROOT/'src/test/java/net/minecraft/world/level/chunk/JavaPalettePacking.java'
    prefix='package net.minecraft.world.level.chunk;\n\nimport java.util.Arrays;\nimport java.util.Optional;\nimport java.util.stream.LongStream;\nimport net.minecraft.util.BitStorage;\nimport net.minecraft.util.SimpleBitStorage;\n\n/** Pinned original pack and reencode bodies; receiver accessors inline to their original reads. */\nfinal class JavaPalettePacking {\n'
    if oracle.read_text()!=prefix+body+'\n}\n':raise RuntimeError('Java packing oracle differs from Git')
    (out/'OriginalPalettedContainer.java').write_text(original)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writePaletteClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writePaletteClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativePalettePackingTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativePalettePackingTest.xml')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():
        raise RuntimeError('Parity failed')
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/worldgen.rs', '-o', str(out / 'worldgen-tests')], out / 'rust-build.log')
    run([str(out / 'worldgen-tests'), 'chunk::palette'], out / 'rust-tests.log')
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativePalettePackingVerification']
    files = list((ROOT / 'src/main/rust/world/level/chunk/palette').glob('*.rs'))
    files += list((ROOT / 'src/test/java/net/minecraft/world/level/chunk').glob('*PalettePacking*.java'))
    files += [ROOT/p for p in ['src/main/java/net/minecraft/world/level/chunk/PalettedContainer.java','src/main/java/net/minecraft/world/level/chunk/NativePalettePacking.java','src/main/java/net/minecraft/world/level/chunk/Strategy.java','src/main/rust/world/level/chunk/mod.rs','src/main/rust/world/level/mod.rs','src/test/resources/worldgen/heightmap/chunks.json']]
    files += [Path(__file__).resolve()]
    report = {'reference': subprocess.check_output(['git','rev-parse',REFERENCE],cwd=ROOT,text=True).strip(),
              'scope': 'Complete block-state pack() caller: locking, eligibility, identity labels, transfers, ordinary downcalls, palette/list and packed-word/stream construction and consumption. Immutable global identity-label bootstrap and fixture loading excluded equally; no packed-result cache.',
              'fixture': '16 synthetic containers per cardinality/stale case; all saved sections from four FULL chunks per dimension (two seeds). Equal packed inputs; no RNG during packing.',
              'jvm': jvm, 'cpu': args.cpu,
              'java_version': subprocess.check_output(['java','--version'],text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
              'native_sha256': hashlib.sha256((native/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
              'sources': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
              'parity': re.findall(r'PALETTE_\w*PARITY[^\n<]*',xml.read_text()), 'pairs': [], 'performance': {}}
    def save():
        (out/'results.json').write_text(json.dumps(report,indent=2))
    save()
    if args.parity_only:
        print(out/'results.json')
        return
    for fork in range(args.forks):
        pair = {}
        for mode in (['java','native'] if fork%2==0 else ['native','java']):
            print(f'Pair {fork+1}: {mode}',flush=True)
            text = run(base+[mode,'all'],out/f'{fork}-{mode}.log')
            rows = re.findall(r'PALETTE_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
            if len(rows)!=21:
                raise RuntimeError('Incomplete measurements')
            for name, samples, jit, checksum in rows:
                if any(json.loads(jit)):
                    raise RuntimeError('Compilation during measurement')
                row = pair.setdefault(name,{})
                row[mode] = json.loads(samples)
                if 'checksum' in row and row['checksum'] != int(checksum):
                    raise RuntimeError('Unequal workload outputs')
                row['checksum'] = int(checksum)
        report['pairs'].append(pair)
        save()
    rng = random.Random(1977)
    for name in report['pairs'][0]:
        pairs = [(p[name]['java'],p[name]['native']) for p in report['pairs']]
        ratios = [statistics.median(n)/statistics.median(j) for j,n in pairs]
        boots = sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name] = {'java_ns': statistics.median(statistics.median(j) for j,n in pairs),
                                      'native_ns': statistics.median(statistics.median(n) for j,n in pairs),
                                      'ratios': ratios, 'ratio_ci95': [boots[250],boots[9749]],
                                      'passes': max(ratios)<=.95 and boots[9749]<=.95}
    save()
    print(json.dumps(report['performance'],indent=2))
    if not all(v['passes'] for v in report['performance'].values()):
        raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__ == '__main__':
    main()
