#!/usr/bin/env python3
"""Pinned original ordered palette scan: caller parity and boundary-inclusive performance."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = '7af3a1594'
PACKAGE = 'net.minecraft.world.level.chunk.'
ENTRY = '\t\tif (this.getClass() == PalettedContainer.class && this.data.storage.getClass() == SimpleBitStorage.class) {\n\t\t\tvar ids = NativePaletteDistinct.scan(this.data.storage);\n\t\t\tif (ids != null) {\n\t\t\t\ttry (ids) {\n\t\t\t\t\tfor (int i = 0; i < ids.size; i++) consumer.accept(palette.valueFor(ids.entry(i)));\n\t\t\t\t}\n\t\t\t\treturn;\n\t\t\t}\n\t\t}\n'


def strip_distinct(source):
    if source.count(ENTRY) != 1: raise RuntimeError('Expected one exact distinct scan dispatch')
    return source.replace(ENTRY, '', 1)


def audit(out):
    folder = 'src/main/java/net/minecraft/world/level/chunk/'
    def original(name):
        return subprocess.check_output(['git', 'show', REFERENCE + ':' + folder + name], cwd=ROOT, text=True)
    source = original('PalettedContainer.java')
    if strip_distinct((ROOT / (folder + 'PalettedContainer.java')).read_text()) != source:
        raise RuntimeError('Container changed beyond exact distinct scan dispatch')
    a = source.index('\tpublic void getAll(Consumer<T> consumer)')
    b = source.index('\n\tpublic void read(', a)
    body = source[a:b].replace('public void getAll(Consumer<T> consumer)',
        'static <T> void getAll(PalettedContainer<T> source, Consumer<T> consumer)').replace(
        'this.data.palette()', 'source.dataForNativeScan().palette()').replace(
        'this.data.storage', 'source.dataForNativeScan().storage()')
    if (ROOT / 'src/test/java/net/minecraft/world/level/chunk/JavaPaletteDistinct.java').read_text().count(body) != 1:
        raise RuntimeError('Literal Java getAll oracle differs from Git')
    for name in ['Strategy.java', 'Configuration.java', 'HashMapPalette.java', 'LinearPalette.java',
                 'SingleValuePalette.java', 'GlobalPalette.java', 'ChunkGenerator.java', 'LevelChunkSection.java',
                 'NativePaletteHistogram.java', 'NativePalettePacking.java', 'NativePaletteResize.java', 'NativePaletteUnpacking.java']:
        if (ROOT / (folder + name)).read_text() != original(name): raise RuntimeError('Unchanged semantics differ: ' + name)
    for name in ['SimpleBitStorage.java', 'ZeroBitStorage.java']:
        path = 'src/main/java/net/minecraft/util/' + name
        if (ROOT / path).read_text() != subprocess.check_output(['git','show',REFERENCE+':'+path],cwd=ROOT,text=True):
            raise RuntimeError('Original storage changed: ' + name)
    (out / 'OriginalPalettedContainer.java').write_text(source)


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/palette-distinct-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=2)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    parser.add_argument('--case', default='all')
    args = parser.parse_args()
    if not args.parity_only and args.forks < 3: parser.error('At least three JVM pairs required')
    if args.cpu not in os.sched_getaffinity(0): parser.error('Unavailable CPU')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'): parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    audit(out)
    cpfile, init = out / 'classpath.txt', out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; gradle.rootProject.tasks.register('writeDistinctClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeDistinctClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build: command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    command = ['./gradlew', '-I', str(init), 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--console=plain']
    tests = ['NativePaletteDistinctTest', 'NativePaletteHistogramTest', 'NativePalettePackingTest',
             'NativePaletteResizeTest', 'NativePaletteUnpackingTest']
    for name in tests: command += ['--tests', PACKAGE + name]
    run(command, out / 'parity.log')
    counts = {}
    for name in tests:
        xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + name + '.xml')
        node = ET.fromstring(xml.read_text())
        if int(node.attrib['failures']) or int(node.attrib['errors']): raise RuntimeError('Parity failed: ' + name)
        counts[name] = int(node.attrib['tests'])
        (out / (name + '.xml')).write_bytes(xml.read_bytes())
    run(['rustc','--edition=2021','--test','src/test/rust/worldgen.rs','-o',str(out/'world-tests')],out/'rust-build.log')
    run([str(out/'world-tests'),'chunk::palette'],out/'rust-tests.log')
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch','-Xms512m','-Xmx3g','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','--enable-native-access=ALL-UNNAMED']
    base = ['taskset','-c',str(args.cpu),'java',*flags,'-Dmattmc.rust.natives.dir='+str(native.parent),
        '-cp',cpfile.read_text().strip(),PACKAGE+'NativePaletteDistinctVerification']
    files = list((ROOT/'src/main/rust/world/level/chunk/palette').rglob('*.rs'))
    files += list((ROOT/'src/main/java/net/minecraft/world/level/chunk').glob('*.java'))
    files += list((ROOT/'src/test/java/net/minecraft/world/level/chunk').glob('*.java'))
    files += [ROOT/'src/main/java/net/minecraft/util/SimpleBitStorage.java', ROOT/'src/main/java/net/minecraft/util/ZeroBitStorage.java',
        ROOT/'src/main/java/net/minecraft/util/NativeLibraryLoader.java', ROOT/'src/test/resources/worldgen/palette_distinct/biomes.json',
        ROOT/'src/test/resources/worldgen/heightmap/chunks.json', Path(__file__).resolve()]
    report = {'reference':subprocess.check_output(['git','rev-parse',REFERENCE],cwd=ROOT,text=True).strip(),
        'scope':'Complete production PalettedContainer.getAll with ordered value consumption: eligibility, fresh packed copy/ordinary FFM/ordered ID copy for 4096 entries or bounded critical heap access for exactly 64, captured palette valueFor lookups and callbacks. Same stable inputs and no result cache. Initial library/scratch/fixtures excluded equally. Zero-bit, custom and unsupported storage retain original Java; excluded from native gains.',
        'fixtures':'16 seeds per synthetic case: real 64-entry biome storage/strategy and 4096-entry block containers across widths/cardinalities, runs, cyclic and stale uniform inputs. Every mixed section in unchanged sixteen-chunk saved biome and block corpora. Saved biome objects are canonical names resolved by the ordinary biome palette strategy.',
        'cpu':args.cpu,'case':args.case,'jvm':flags,'java_version':subprocess.check_output(['java','--version'],text=True),
        'hardware':json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
        'native_sha256':hashlib.sha256(native.read_bytes()).hexdigest(),
        'sources':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
        'java_tests':counts,'pairs':[],'performance':{}}
    def save(): (out/'results.json').write_text(json.dumps(report,indent=2))
    save()
    if args.parity_only: print(out/'results.json'); return
    for fork in range(args.forks):
        pair={}
        for mode in (['java','native'] if fork%2==0 else ['native','java']):
            print(f'Pair {fork+1}: {mode} ({args.case})',flush=True)
            text=run(base+[mode,args.case],out/f'{fork}-{mode}.log')
            rows=re.findall(r'DISTINCT_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
            if len(rows)!=(27 if args.case=='all' else 1):raise RuntimeError('Incomplete measurements')
            for name,samples,jit,checksum in rows:
                if any(json.loads(jit)):raise RuntimeError('Compilation during measured rounds')
                row=pair.setdefault(name,{})
                row[mode]=json.loads(samples)
                if 'checksum' in row and row['checksum']!=int(checksum):raise RuntimeError('Unequal outputs')
                row['checksum']=int(checksum)
        report['pairs'].append(pair);save()
    rng=random.Random(1977)
    for name in report['pairs'][0]:
        pairs=[(p[name]['java'],p[name]['native']) for p in report['pairs']]
        ratios=[statistics.median(n)/statistics.median(j) for j,n in pairs]
        boots=sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name]={'java_ns':statistics.median(statistics.median(j) for j,n in pairs),
            'native_ns':statistics.median(statistics.median(n) for j,n in pairs),'ratios':ratios,
            'ratio_ci95':[boots[250],boots[9749]],'passes':max(ratios)<=.95 and boots[9749]<=.95}
    for path,expected in report['sources'].items():
        if hashlib.sha256((ROOT/path).read_bytes()).hexdigest()!=expected:raise RuntimeError('Measured source changed: '+path)
    if hashlib.sha256(native.read_bytes()).hexdigest()!=report['native_sha256']:raise RuntimeError('Native library changed')
    for name in report['pairs'][0]:
        if len({p[name]['checksum'] for p in report['pairs']})!=1:raise RuntimeError('Output changed between forks')
    report['integrity']={'source_hashes_match':True,'native_hash_matches':True,'cross_fork_checksums_match':True}
    save();print(json.dumps(report['performance'],indent=2))
    if not all(p['passes'] for p in report['performance'].values()):raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__ == '__main__': main()
