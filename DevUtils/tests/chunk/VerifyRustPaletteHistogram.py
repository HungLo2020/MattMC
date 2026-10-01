#!/usr/bin/env python3
"""Pinned Java parity and complete multi-entry palette histogram performance acceptance."""
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
ENTRY = '\t\t\tif (this.getClass() == PalettedContainer.class) {\n\t\t\t\tvar counts = NativePaletteHistogram.scan(this.data.storage);\n\t\t\t\tif (counts != null) {\n\t\t\t\t\ttry (counts) {\n\t\t\t\t\t\tfor (int i = 0; i < counts.size; i++) {\n\t\t\t\t\t\t\tlong entry = counts.entry(i);\n\t\t\t\t\t\t\tcountConsumer.accept(this.data.palette.valueFor((int)entry), (int)(entry >>> 32));\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t\treturn;\n\t\t\t\t}\n\t\t\t}\n'
FASTUTIL_SHA256 = "b5543ee08d062d551cf0a5c9bc0fb70588b0382079029ba48941fa9c9be8a5d4"



def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/palette-histogram-migration/acceptance')
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
    expected = original[:pos]+entry+original[pos:]
    count_start = expected.index('public void count(')
    insert = expected.index('\t\t\tInt2IntOpenHashMap int2IntOpenHashMap',count_start)
    expected = expected[:insert]+ENTRY+expected[insert:]
    if source.read_text()!=expected:
        raise RuntimeError('Container source differs outside the two known native dispatch insertions')
    a=original.index('\tpublic void count(')
    b=original.index('\n\t@FunctionalInterface',a)
    body=original[a:b].replace('public void count(PalettedContainer.CountConsumer<T> countConsumer)', 'static <T> void count(PalettedContainer<T> source, PalettedContainer.CountConsumer<T> countConsumer)',1).replace('this.data.palette','source.dataForNativeScan().palette()').replace('this.data.storage','source.dataForNativeScan().storage()')
    oracle=ROOT/'src/test/java/net/minecraft/world/level/chunk/JavaPaletteHistogram.java'
    prefix='package net.minecraft.world.level.chunk;\n\nimport it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;\n\n/** Original count body from fffe4a073, with inlining receiver accessor adaptation. */\nfinal class JavaPaletteHistogram {\n'
    if oracle.read_text()!=prefix+body+'\n}\n':raise RuntimeError('Java histogram oracle differs from Git')
    section=subprocess.check_output(['git','show',REFERENCE+':src/main/java/net/minecraft/world/level/chunk/LevelChunkSection.java'],cwd=ROOT,text=True)
    a=section.index('\tpublic void recalcBlockCounts()'); b=section.index('\n\tpublic PalettedContainer<BlockState> getStates()',a)
    recalc=section[a:b].replace('this.states.count(lv);','JavaPaletteHistogram.count(this.states, lv);')
    a=section.index('\tpublic BlockState setBlockState(int i, int j, int k, BlockState blockState)');b=section.index('\n\tpublic void recalcBlockCounts()',a)
    methods=section[a:b]
    section_oracle=ROOT/'src/test/java/net/minecraft/world/level/chunk/JavaSectionCounts.java'
    carrier=section_oracle.read_text()
    if not carrier.endswith(methods+'\n'+recalc+'\n}\n'):raise RuntimeError('Recount, mutation or public accessors differ from Git')
    if (ROOT/'src/main/java/net/minecraft/world/level/chunk/LevelChunkSection.java').read_text()!=section:raise RuntimeError('Section source differs from reference')
    (out/'OriginalPalettedContainer.java').write_text(original)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeHistogramClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeHistogramClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativePaletteHistogramTest', '--tests', PACKAGE + 'NativePalettePackingTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativePaletteHistogramTest.xml')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():
        raise RuntimeError('Parity failed')
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/worldgen.rs', '-o', str(out / 'worldgen-tests')], out / 'rust-build.log')
    run([str(out / 'worldgen-tests'), 'chunk::palette::histogram'], out / 'rust-tests.log')
    regression=ROOT/('build/test-results/test/TEST-'+PACKAGE+'NativePalettePackingTest.xml')
    (out/'packing-regression.xml').write_bytes(regression.read_bytes())
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativePaletteHistogramVerification']
    fastutil=[Path(p) for p in cpfile.read_text().strip().split(':') if 'fastutil' in p]
    if len(fastutil)!=1 or hashlib.sha256(fastutil[0].read_bytes()).hexdigest()!=FASTUTIL_SHA256:
        raise RuntimeError('Fastutil ordering reference changed; review kernel and rerun parity')
    files=list((ROOT/'src/main/rust/world/level/chunk/palette/histogram').glob('*.rs'))
    files += [ROOT/p for p in ['src/main/java/net/minecraft/world/level/chunk/PalettedContainer.java','src/main/java/net/minecraft/world/level/chunk/NativePaletteHistogram.java','src/main/java/net/minecraft/world/level/chunk/LevelChunkSection.java','src/main/java/net/minecraft/util/SimpleBitStorage.java','src/main/java/net/minecraft/util/ZeroBitStorage.java','src/main/rust/world/level/chunk/palette/mod.rs','src/test/java/net/minecraft/world/level/chunk/JavaPaletteHistogram.java','src/test/java/net/minecraft/world/level/chunk/JavaSectionCounts.java','src/test/java/net/minecraft/world/level/chunk/PalettePackingFixtures.java','src/test/java/net/minecraft/world/level/chunk/NativePaletteHistogramTest.java','src/test/java/net/minecraft/world/level/chunk/NativePaletteHistogramVerification.java','src/test/resources/worldgen/heightmap/chunks.json']]
    files += [Path(__file__).resolve()]
    report = {'reference': subprocess.check_output(['git','rev-parse',REFERENCE],cwd=ROOT,text=True).strip(),
              'scope': 'Complete multi-entry count() caller with ordered callback delivery; recalc cases additionally run original section BlockCounter and publish fields. Includes eligibility, input/output copies, ordinary downcall, and callback output consumption. Fixture loading and initial scratch allocation excluded equally. Single-entry shortcut excluded from timing and left unchanged; no result cache.',
              'fixture': '16 synthetic containers per cardinality/stale/collision case; all saved multi-entry palette sections from four FULL chunks per dimension (two seeds). Uniform and maximum-distinct stress cases included separately. Equal packed inputs; no RNG inside measurement.',
              'jvm': jvm, 'cpu': args.cpu, 'fastutil_sha256': FASTUTIL_SHA256,
              'java_version': subprocess.check_output(['java','--version'],text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
              'native_sha256': hashlib.sha256((native/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
              'sources': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
              'parity': re.findall(r'HISTOGRAM_\w*PARITY[^\n<]*',xml.read_text()), 'pairs': [], 'performance': {}}
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
            rows = re.findall(r'HISTOGRAM_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
            if len(rows)!=27:
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
    if any(hashlib.sha256(f.read_bytes()).hexdigest()!=report['sources'][str(f.relative_to(ROOT))] for f in files):
        raise RuntimeError('Measured source changed')
    if hashlib.sha256((native/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest()!=report['native_sha256']:
        raise RuntimeError('Native library changed')
    for name in report['pairs'][0]:
        if len({pair[name]['checksum'] for pair in report['pairs']})!=1:raise RuntimeError('Checksum changed between forks')
    report['integrity']={'source_hashes_match': True, 'native_hash_matches': True, 'cross_fork_checksums_match': True}
    save()
    print(json.dumps(report['performance'],indent=2))
    if not all(v['passes'] for v in report['performance'].values()):
        raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__ == '__main__':
    main()
