#!/usr/bin/env python3
"""Pinned original merged box extraction: exact parity and complete toAabbs performance."""
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
REFERENCE = '3e85592c4'
PACKAGE = 'net.minecraft.world.phys.shapes.'
BOX_DISPATCH = '\t\tif (bl && (long)bitSetDiscreteVoxelShape.xSize * bitSetDiscreteVoxelShape.ySize * bitSetDiscreteVoxelShape.zSize >= NativeVoxelBoxes.MIN_CELLS\n\t\t\t&& NativeVoxelBoxes.emit(bitSetDiscreteVoxelShape, intLineConsumer)) return;\n'
LIST_DISPATCH = '\t\tif (this.shape != null && (long)this.shape.xSize * this.shape.ySize * this.shape.zSize >= NativeVoxelBoxes.MIN_CELLS) {\n\t\t\tvar nativeBoxes = NativeVoxelBoxes.toAabbs(this);\n\t\t\tif (nativeBoxes != null) return nativeBoxes;\n\t\t}\n'
HELPERS = ['boolean isZStripFull', 'boolean isXZRectangleFull', 'void clearZStrip']


def strip_boxes(source):
    if source.count(BOX_DISPATCH) != 1:
        raise RuntimeError('Expected exactly one known box dispatch')
    source = source.replace(BOX_DISPATCH, '', 1)
    for helper in HELPERS:
        old = '\t' + helper
        if source.count(old) != 1:
            raise RuntimeError('Unexpected box helper visibility: ' + helper)
        source = source.replace(old, '\tprivate ' + helper, 1)
    return source


def strip_box_list(source):
    if source.count(LIST_DISPATCH) != 1:
        raise RuntimeError('Expected exactly one known box-list dispatch')
    return source.replace(LIST_DISPATCH, '', 1)


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def audit(out):
    folder = 'src/main/java/net/minecraft/world/phys/shapes/'
    def original(name):
        return subprocess.check_output(['git', 'show', REFERENCE + ':' + folder + name], cwd=ROOT, text=True)
    source = original('BitSetDiscreteVoxelShape.java')
    if strip_boxes((ROOT / (folder + 'BitSetDiscreteVoxelShape.java')).read_text()) != source:
        raise RuntimeError('Grid changed beyond exact box dispatch/helper visibility')
    a = source.index('\tprotected static void forAllBoxes(')
    b = source.index('\n\tprivate boolean isZStripFull', a)
    if (ROOT / 'src/test/java/net/minecraft/world/phys/shapes/JavaVoxelBoxes.java').read_text().count(source[a:b]) != 1:
        raise RuntimeError('Original extraction loop differs from Git')
    outer = original('VoxelShape.java')
    a = outer.index('\tpublic void forAllBoxes(')
    b = outer.index('\n\tpublic double min(Direction.Axis axis, double', a)
    body = outer[a:b].replace('public void forAllBoxes(Shapes.DoubleLineConsumer doubleLineConsumer)',
                             'static void forAllBoxes(VoxelShape source, Shapes.DoubleLineConsumer doubleLineConsumer)')
    body = body.replace('public List<AABB> toAabbs()', 'static List<AABB> toAabbs(VoxelShape source)')
    body = body.replace('this.getCoords(', 'source.getCoords(')
    body = body.replace('this.shape\n\t\t\t.forAllBoxes(', 'JavaVoxelBoxes.forAllBoxes(source.shape,')
    body = body.replace('this.forAllBoxes(', 'forAllBoxes(source, ')
    if (ROOT / 'src/test/java/net/minecraft/world/phys/shapes/JavaVoxelBoxList.java').read_text().count(body) != 1:
        raise RuntimeError('Original complete public box caller differs from Git')
    from VerifyRustVoxelRotation import strip_rotation
    for name in ['VoxelShape.java','DiscreteVoxelShape.java','Shapes.java','ArrayVoxelShape.java','CubeVoxelShape.java',
                 'SubShape.java','NativeVoxelJoin.java','NonOverlappingMerger.java']:
        if (strip_box_list((ROOT / (folder + name)).read_text()) if name == 'VoxelShape.java' else strip_rotation((ROOT/(folder+name)).read_text()) if name=='DiscreteVoxelShape.java' else (ROOT / (folder + name)).read_text()) != original(name):
            raise RuntimeError('Unchanged coordinates/ownership/joins changed: ' + name)
    (out / 'OriginalBitSetDiscreteVoxelShape.java').write_text(source)
    (out / 'OriginalVoxelShape.java').write_text(outer)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/voxel-box-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=2)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    parser.add_argument('--case', default='all', help='One workload for independent single-workload JVM validation')
    args = parser.parse_args()
    if not args.parity_only and args.forks < 3:
        parser.error('At least three independent JVM pairs required')
    if args.cpu not in os.sched_getaffinity(0):
        parser.error('Unavailable CPU')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    audit(out)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; gradle.rootProject.tasks.register('writeVoxelBoxesClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeVoxelBoxesClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    command = ['./gradlew', '-I', str(init), 'test', '-x', 'buildRustNative', '-x', 'testRustNative',
               '--tests', PACKAGE + 'NativeVoxelBoxesTest', '--tests', PACKAGE + 'NativeVoxelJoinTest', '--console=plain']
    run(command, out / 'parity.log')
    tests = {}
    for name in ['NativeVoxelBoxesTest', 'NativeVoxelJoinTest']:
        xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + name + '.xml')
        text = xml.read_text()
        (out / (name + '.xml')).write_text(text)
        if 'failures="0"' not in text or 'errors="0"' not in text:
            raise RuntimeError('Parity failed: ' + name)
        tests[name] = re.findall(r'(?:BOX|VOXEL)_\w*PARITY[^\n<]*', text)
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/physics.rs', '-o', str(out / 'world-tests')], out / 'rust-build.log')
    run([str(out / 'world-tests'), 'phys::shapes'], out / 'rust-tests.log')
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch','-Xms512m','-Xmx3g','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','--enable-native-access=ALL-UNNAMED']
    base = ['taskset','-c',str(args.cpu),'java',*flags,'-Dmattmc.rust.natives.dir='+str(native.parent),
            '-cp',cpfile.read_text().strip(),PACKAGE+'NativeVoxelBoxesVerification']
    files = list((ROOT/'src/main/rust/world/phys').rglob('*.rs'))
    files += list((ROOT/'src/main/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files += list((ROOT/'src/test/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files += [Path(__file__).resolve(),ROOT/'src/main/rust/world/mod.rs',ROOT/'src/test/rust/physics.rs',
              ROOT/'src/main/java/net/minecraft/world/phys/AABB.java',ROOT/'src/main/java/net/minecraft/util/NativeLibraryLoader.java']
    report = {'reference': subprocess.check_output(['git','rev-parse',REFERENCE],cwd=ROOT,text=True).strip(),
              'scope': 'Complete public VoxelShape.toAabbs caller, initial original clone and bounds reads, eligibility, compressed-word snapshot/copy, ordinary FFM call, output endpoint copy, every Java coordinate lookup, list growth/AABB allocation (built-in lists avoid two intermediate callback layers; arbitrary consumers remain in Java), and consuming all six raw coordinate bits of every box. No result/input cache. Fixture/library/thread scratch initialization excluded equally; all results freshly computed. Merged complex grids only, not small/unmerged calls, shape optimize, ray clipping, whole-game physics or FPS.',
              'fixtures': 'Eight seeded inputs per synthetic pattern/size: empty/full/random/dense/sparse/checker/shell/slabs, 8^3/16^3; odd17x9x7 and8x1x64 rows. One workload uses all93 actual registered collision grids that qualify, including their real coordinates and geometry without alteration. Two further workloads are32 distinct grid/coordinate geometries derived by the unchanged production join of registered collision shapes with a complex shell probe; these are explicitly derived joins, not a claim that all ordinary registered shapes have large grids.',
              'jvm': flags, 'cpu': args.cpu, 'case': args.case,
              'java_version': subprocess.check_output(['java','--version'],text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
              'native_sha256': hashlib.sha256(native.read_bytes()).hexdigest(),
              'sources': {str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
              'parity': tests, 'pairs': [], 'performance': {}}
    def save():
        (out/'results.json').write_text(json.dumps(report,indent=2))
    save()
    if args.parity_only:
        print(out/'results.json'); return
    for fork in range(args.forks):
        pair = {}
        for mode in (['java','native'] if fork%2==0 else ['native','java']):
            print(f'Pair {fork+1}: {mode} ({args.case})',flush=True)
            text = run(base+[mode,args.case],out/f'{fork}-{mode}.log')
            rows = re.findall(r'BOX_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
            if len(rows) != (21 if args.case=='all' else 1): raise RuntimeError('Incomplete measurements')
            for name,samples,jit,checksum in rows:
                if any(json.loads(jit)): raise RuntimeError('Compilation during measured rounds')
                row = pair.setdefault(name,{})
                row[mode] = json.loads(samples)
                if 'checksum' in row and row['checksum'] != int(checksum): raise RuntimeError('Unequal output checksum')
                row['checksum'] = int(checksum)
        report['pairs'].append(pair); save()
    rng = random.Random(1977)
    for name in report['pairs'][0]:
        pairs=[(p[name]['java'],p[name]['native']) for p in report['pairs']]
        ratios=[statistics.median(n)/statistics.median(j) for j,n in pairs]
        boots=sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name]={'java_ns':statistics.median(statistics.median(j) for j,n in pairs),
            'native_ns':statistics.median(statistics.median(n) for j,n in pairs),'ratios':ratios,
            'ratio_ci95':[boots[250],boots[9749]],'passes':max(ratios)<=.95 and boots[9749]<=.95}
    if any(hashlib.sha256(f.read_bytes()).hexdigest()!=report['sources'][str(f.relative_to(ROOT))] for f in files):
        raise RuntimeError('Measured source changed')
    if hashlib.sha256(native.read_bytes()).hexdigest()!=report['native_sha256']: raise RuntimeError('Native library changed')
    for name in report['pairs'][0]:
        if len({p[name]['checksum'] for p in report['pairs']})!=1: raise RuntimeError('Checksum changed between JVM forks')
    report['integrity']={'source_hashes_match':True,'native_hash_matches':True,'cross_fork_checksums_match':True}
    save(); print(json.dumps(report['performance'],indent=2))
    if not all(p['passes'] for p in report['performance'].values()): raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__=='__main__':
    main()
