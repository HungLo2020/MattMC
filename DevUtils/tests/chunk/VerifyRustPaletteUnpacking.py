#!/usr/bin/env python3
"""Pinned original saved-data unpack callers, parity, and full boundary-inclusive performance acceptance."""
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
REFERENCE = 'b81c01943'
PACKAGE = 'net.minecraft.world.level.chunk.'
UNPACK_DISPATCH = '\t\t\t\t\t\tvar loaded = NativePaletteUnpacking.unpack(strategy, configuration, list, ls);\n\t\t\t\t\t\tif (loaded != null) return DataResult.success(loaded);\n'
CONSTRUCTOR = 'PalettedContainer(Strategy<T> strategy, Configuration configuration, BitStorage bitStorage, Palette<T> palette)'

def strip_unpack(source):
    if source.count(UNPACK_DISPATCH) != 1 or source.count('\t'+CONSTRUCTOR) != 1:
        raise RuntimeError('Missing/changed exact unpack dispatch or constructor')
    return source.replace(UNPACK_DISPATCH, '', 1).replace('\t'+CONSTRUCTOR, '\tprivate '+CONSTRUCTOR, 1)


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/palette-unpacking-migration/acceptance')
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
    from VerifyRustPaletteDistinct import strip_distinct
    from VerifyRustPaletteResize import strip_resize
    if strip_resize(strip_unpack(strip_distinct(source.read_text()))) != original:
        raise RuntimeError('Container differs beyond the exact resize/unpack dispatches and constructor visibility')
    oracle = (ROOT/'src/test/java/net/minecraft/world/level/chunk/JavaPaletteUnpacking.java').read_text()
    a=original.index('\tpublic static <T> DataResult<PalettedContainer<T>> unpack('); b=original.index('\n\t@Override',a)
    c=original.index('\tprivate static <T> int[] reencodeContents('); d=original.index('\n\t@Override',c)
    for body in (original[a:b],original[c:d]):
        if oracle.count(body)!=1:raise RuntimeError('Original complete Java unpack/reencode oracle differs from Git')
    for path in ('Strategy.java','Configuration.java','HashMapPalette.java','LinearPalette.java','SingleValuePalette.java','GlobalPalette.java'):
        relative='src/main/java/net/minecraft/world/level/chunk/'+path
        if (ROOT/relative).read_text()!=subprocess.check_output(['git','show',REFERENCE+':'+relative],cwd=ROOT,text=True):
            raise RuntimeError('Palette/strategy semantics changed: '+path)
    (out/'OriginalPalettedContainer.java').write_text(original)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeUnpackClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeUnpackClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativePaletteUnpackingTest', '--tests', PACKAGE + 'NativePaletteResizeTest', '--tests', PACKAGE + 'NativePaletteHistogramTest', '--tests', PACKAGE + 'NativePalettePackingTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativePaletteUnpackingTest.xml')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():
        raise RuntimeError('Parity failed')
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/worldgen.rs', '-o', str(out / 'worldgen-tests')], out / 'rust-build.log')
    run([str(out / 'worldgen-tests'), 'chunk::palette'], out / 'rust-tests.log')
    regression=ROOT/('build/test-results/test/TEST-'+PACKAGE+'NativePalettePackingTest.xml')
    (out/'packing-regression.xml').write_bytes(regression.read_bytes())
    (out/'resize-regression.xml').write_bytes((ROOT/('build/test-results/test/TEST-'+PACKAGE+'NativePaletteResizeTest.xml')).read_bytes())
    (out/'histogram-regression.xml').write_bytes((ROOT/('build/test-results/test/TEST-'+PACKAGE+'NativePaletteHistogramTest.xml')).read_bytes())
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativePaletteUnpackingVerification']
    files=list((ROOT/'src/main/rust/world/level/chunk/palette/unpack').glob('*.rs'))
    files += [ROOT/p for p in ['src/main/java/net/minecraft/world/level/chunk/PalettedContainer.java',
        'src/main/java/net/minecraft/world/level/chunk/NativePaletteUnpacking.java',
        'src/main/java/net/minecraft/util/SimpleBitStorage.java','src/main/java/net/minecraft/util/ZeroBitStorage.java',
        'src/main/rust/world/level/chunk/palette/mod.rs',
        'src/test/java/net/minecraft/world/level/chunk/JavaPaletteUnpacking.java',
        'src/test/java/net/minecraft/world/level/chunk/PalettePackingFixtures.java',
        'src/test/java/net/minecraft/world/level/chunk/NativePaletteUnpackingTest.java',
        'src/test/java/net/minecraft/world/level/chunk/NativePaletteUnpackingVerification.java',
        'src/test/resources/worldgen/heightmap/chunks.json']]
    files += [ROOT/('src/main/java/net/minecraft/world/level/chunk/'+p) for p in
        ['Strategy.java','Configuration.java','Palette.java','PaletteResize.java',
         'HashMapPalette.java','LinearPalette.java','SingleValuePalette.java','GlobalPalette.java']]
    files += [ROOT/'src/main/java/net/minecraft/util/CrudeIncrementalIntIdentityHashBiMap.java']
    files += [ROOT/'src/main/rust/world/level/chunk/palette/pack.rs', ROOT/'src/main/rust/world/level/chunk/palette/ffi.rs', ROOT/'src/test/java/net/minecraft/world/level/chunk/JavaPalettePacking.java']
    files += [Path(__file__).resolve()]
    report = {'reference': subprocess.check_output(['git','rev-parse',REFERENCE],cwd=ROOT,text=True).strip(),
              'scope': 'Complete PalettedContainer.unpack: fresh packed-data/stream creation, declared-bit checks, stream.toArray allocation, native eligibility/non-null checks, input copy, ordinary decode FFM call, used object/global-ID lookups, mapping copy, ordinary encode FFM call, output copy/storage/container/DataResult allocation and complete word consumption. Original includes its actual temporary HashMapPalette construction and int[4096] remap. Both use the same stable input list/words; no result cache. Initial fixture and scratch setup excluded.',
              'fixture': 'Eight seeded sections per synthetic workload; source widths9..16, 257..65536 entries, random/cycle/run/stale patterns, actual codec ListN list and three other standard list classes. Counts <=31809 contain distinct registered states, larger serialized lists contain identity aliases. Four workloads use saved sections edited with 768 distinct registry states and originally Java-packed. Unchanged corpus local sections retain original direct-load path and are parity coverage, not speedup evidence.',
              'jvm': jvm, 'cpu': args.cpu,
              'java_version': subprocess.check_output(['java','--version'],text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
              'native_sha256': hashlib.sha256((native/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
              'sources': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
              'parity': re.findall(r'UNPACK_\w*PARITY[^\n<]*',xml.read_text()), 'pairs': [], 'performance': {}}
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
            rows = re.findall(r'UNPACK_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
            if len(rows)!=26:
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
