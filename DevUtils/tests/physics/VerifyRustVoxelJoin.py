#!/usr/bin/env python3
"""Pinned original voxel Boolean join callers, parity, and full boundary-inclusive performance acceptance."""
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
PACKAGE = 'net.minecraft.world.phys.shapes.'
JOIN_DISPATCH = '\t\tif ((long)bitSetDiscreteVoxelShape.xSize * bitSetDiscreteVoxelShape.ySize * bitSetDiscreteVoxelShape.zSize >= NativeVoxelJoin.MIN_CELLS) {\n\t\t\tvar nativeJoin = NativeVoxelJoin.join(discreteVoxelShape, discreteVoxelShape2, indexMerger, indexMerger2, indexMerger3, booleanOp, bitSetDiscreteVoxelShape);\n\t\t\tif (nativeJoin != null) return nativeJoin;\n\t\t}\n'
FIELDS = ['final BitSet storage','int xMin','int yMin','int zMin','int xMax','int yMax','int zMax']
NONOVERLAP = '\tboolean nativeCompatible() { return NativeVoxelJoin.trustedCoordinates(this.lower) && NativeVoxelJoin.trustedCoordinates(this.upper); }\n\n'


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/voxel-join-migration/acceptance')
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
    folder='src/main/java/net/minecraft/world/phys/shapes/'
    def original_file(name):
        return subprocess.check_output(['git','show',REFERENCE+':'+folder+name],cwd=ROOT,text=True)
    original=original_file('BitSetDiscreteVoxelShape.java')
    source=ROOT/(folder+'BitSetDiscreteVoxelShape.java')
    expected=original
    for field in FIELDS: expected=expected.replace('private '+field,field,1)
    at=expected.index('\t\tBitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(indexMerger.size() - 1')
    at=expected.index('\n',at)+1
    expected=expected[:at]+JOIN_DISPATCH+expected[at:]
    from VerifyRustVoxelBoxes import strip_boxes, strip_box_list
    from VerifyRustVoxelRotation import strip_rotation
    if strip_boxes(source.read_text())!=expected:raise RuntimeError('Grid differs beyond exact native dispatch/internal field visibility')
    a=original.index('\tstatic BitSetDiscreteVoxelShape join(');b=original.index('\n\tprotected static void forAllBoxes',a)
    if (ROOT/'src/test/java/net/minecraft/world/phys/shapes/JavaVoxelJoin.java').read_text().count(original[a:b])!=1:
        raise RuntimeError('Original complete grid join differs from Git')
    outer=original_file('Shapes.java');a=outer.index('\tpublic static VoxelShape joinUnoptimized(');b=outer.index('\n\tpublic static boolean joinIsNotEmpty',a)
    body=outer[a:b].replace('BitSetDiscreteVoxelShape.join(', 'JavaVoxelJoin.join(')
    if (ROOT/'src/test/java/net/minecraft/world/phys/shapes/JavaShapeJoin.java').read_text().count(body)!=1:
        raise RuntimeError('Original public caller differs from Git')
    for name in ['Shapes.java','BooleanOp.java','DiscreteVoxelShape.java','VoxelShape.java','ArrayVoxelShape.java',
                 'CubeVoxelShape.java','IndexMerger.java','IdenticalMerger.java','IndirectMerger.java','DiscreteCubeMerger.java','CubePointRange.java']:
        if (strip_box_list((ROOT/(folder+name)).read_text()) if name == 'VoxelShape.java' else strip_rotation((ROOT/(folder+name)).read_text()) if name=='DiscreteVoxelShape.java' else (ROOT/(folder+name)).read_text())!=original_file(name):raise RuntimeError('Original shape/coordinate semantics changed: '+name)
    non=original_file('NonOverlappingMerger.java')
    expected=non.replace('\t@Override\n\tpublic int size()',NONOVERLAP+'\t@Override\n\tpublic int size()',1)
    if (ROOT/(folder+'NonOverlappingMerger.java')).read_text()!=expected:raise RuntimeError('Nonoverlap changed beyond exact compatibility predicate')
    (out/'OriginalBitSetDiscreteVoxelShape.java').write_text(original)
    (out/'OriginalShapes.java').write_text(outer)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; gradle.rootProject.tasks.register('writeVoxelJoinClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeVoxelJoinClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    run(['./gradlew', '-I', str(init), 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativeVoxelJoinTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativeVoxelJoinTest.xml')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():
        raise RuntimeError('Parity failed')
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/physics.rs', '-o', str(out / 'worldgen-tests')], out / 'rust-build.log')
    run([str(out / 'worldgen-tests'), 'phys::shapes'], out / 'rust-tests.log')
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativeVoxelJoinVerification']
    files=list((ROOT/'src/main/rust/world/phys').rglob('*.rs'))
    files += list((ROOT/'src/main/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files += list((ROOT/'src/test/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files += [ROOT/'src/main/rust/world/mod.rs', ROOT/'src/test/rust/physics.rs',
              ROOT/'src/main/java/net/minecraft/util/NativeLibraryLoader.java',Path(__file__).resolve()]
    report = {'reference': subprocess.check_output(['git','rev-parse',REFERENCE],cwd=ROOT,text=True).strip(),
              'scope': 'Complete Shapes.joinUnoptimized public caller: BooleanOp checks, empty/reference shortcuts, original Java coordinate/epsilon merges and allocations, native eligibility, two BitSet.toLongArray snapshots and copies, numeric merger materialization/copy, one ordinary FFM call, output/bounds copies, original-capacity BitSet/result shape construction and consuming every occupied word/coordinate bit/bounds. No cache; initial fixture/library/thread scratch setup excluded. Small/custom/unsupported joins remain original and are not credited as speedup.',
              'fixture': 'Eight seeded pairs per synthetic workload, five merger families, grid sizes8 and16, OR/AND/XOR/ONLY_FIRST, full/random/sparse/shell occupancy. Two workloads use32 geometrically distinct actual registered collision shapes joined to a complex shell probe, selected from the original input registry and verified to dispatch native in every case. This is a shape join operation, not whole-game collision throughput or a claim that every ordinary block join qualifies.',
              'jvm': jvm, 'cpu': args.cpu,
              'java_version': subprocess.check_output(['java','--version'],text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
              'native_sha256': hashlib.sha256((native/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
              'sources': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
              'parity': re.findall(r'VOXEL_\w*PARITY[^\n<]*',xml.read_text()), 'pairs': [], 'performance': {}}
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
            rows = re.findall(r'VOXEL_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
            if len(rows)!=24:
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
