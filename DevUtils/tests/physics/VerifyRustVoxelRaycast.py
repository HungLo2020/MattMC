#!/usr/bin/env python3
"""Pinned ordered outside-ray intersection parity and full public caller timings."""
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
REFERENCE = '8b9173b399a629578a7bf0168e4d3ea32b10e8a6'
PACKAGE = 'net.minecraft.world.phys.shapes.'
HELPER = '\t@Nullable\n\tprivate BlockHitResult clipOutside(Vec3 start, Vec3 end, BlockPos position) {\n\t\tif (this.shape != null && (long)this.shape.xSize * this.shape.ySize * this.shape.zSize >= NativeVoxelRaycast.MIN_CELLS) {\n\t\t\tvar nativeHit = NativeVoxelRaycast.clip(this, start, end, position);\n\t\t\tif (nativeHit != null) return nativeHit.orElse(null);\n\t\t}\n\t\treturn AABB.clip(this.toAabbs(), start, end, position);\n\t}\n\n'

DISPATCH = '\t\t\t\tif (this.shape != null && (long)this.shape.xSize * this.shape.ySize * this.shape.zSize >= NativeVoxelRaycast.MIN_CELLS) {\n\t\t\t\t\tvar nativeHit = NativeVoxelRaycast.clipFromProbe(this, vec3, vec32, blockPos, vec34);\n\t\t\t\t\tif (nativeHit != null) return nativeHit.orElse(null);\n\t\t\t\t}\n'

def strip_raycast(source):
    if 'NativeVoxelRaycast' not in source:
        return source
    if source.count(HELPER) != 1 or source.count(': this.clipOutside(vec3, vec32, blockPos);') != 1:
        raise RuntimeError('Unexpected ray intersection production dispatch')
    if source.count(DISPATCH)!=1:raise RuntimeError('Unexpected outside-probe dispatch')
    return source.replace(DISPATCH,'',1).replace(HELPER, '', 1).replace(': this.clipOutside(vec3, vec32, blockPos);',
        ': AABB.clip(this.toAabbs(), vec3, vec32, blockPos);', 1)


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def audit(out):
    from VerifyRustVoxelClosestPoint import audit as closest_audit
    closest_audit(out)
    folder = 'src/main/java/net/minecraft/world/phys/shapes/'
    original = subprocess.check_output(['git','show',REFERENCE+':'+folder+'VoxelShape.java'],cwd=ROOT,text=True)
    a = original.index('\t@Nullable\n\tpublic BlockHitResult clip(')
    b = original.index('\n\tpublic Optional<Vec3> closestPointTo(',a)
    body = original[a:b].replace('public BlockHitResult clip(Vec3 vec3, Vec3 vec32, BlockPos blockPos)',
        'static BlockHitResult clip(VoxelShape source, Vec3 vec3, Vec3 vec32, BlockPos blockPos)').replace('this.','source.')
    oracle = (ROOT/'src/test/java/net/minecraft/world/phys/shapes/JavaVoxelRaycast.java').read_text()
    if oracle.count(body)!=1 or oracle.count(body.replace('static BlockHitResult clip(VoxelShape source',
        'static BlockHitResult vanilla(VoxelShape source').replace('source.toAabbs()','JavaVoxelBoxList.toAabbs(source)'))!=1:
        raise RuntimeError('Public Java ray oracle differs from Git')
    for p in ['src/main/java/net/minecraft/world/phys/AABB.java','src/main/java/net/minecraft/world/phys/BlockHitResult.java',
        'src/main/java/net/minecraft/core/BlockPos.java','src/main/java/net/minecraft/core/Direction.java','src/main/java/net/minecraft/core/Vec3i.java']:
        if (ROOT/p).read_bytes()!=subprocess.check_output(['git','show',REFERENCE+':'+p],cwd=ROOT):
            raise RuntimeError('Original AABB/point/position/direction semantics changed: '+p)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/voxel-raycast-migration/acceptance')
    parser.add_argument('--forks',type=int,default=3)
    parser.add_argument('--cpu',type=int,default=2)
    parser.add_argument('--background-cpus',default='3,4')
    parser.add_argument('--parity-only',action='store_true')
    parser.add_argument('--skip-build',action='store_true')
    parser.add_argument('--case',default='all')
    parser.add_argument('--order-offset',type=int,choices=range(3),default=0,
        help='Starting offset in the three balanced mode orders; does not change gates or workloads')
    args=parser.parse_args()
    if not args.parity_only and args.forks<3:parser.error('At least three independent JVM comparisons required')
    background=[int(c) for c in args.background_cpus.split(',')]
    if args.cpu in background or len(set(background))!=len(background) or len(background)<2:
        parser.error('Use at least two distinct background CPUs, separate from the benchmark CPU')
    if not set([args.cpu,*background]).issubset(os.sched_getaffinity(0)):parser.error('Unavailable CPU')
    out=(ROOT/args.output).resolve()
    if not out.is_relative_to(ROOT/'build'):parser.error('Output must be under build/')
    out.mkdir(parents=True,exist_ok=True);audit(out)
    cp=out/'classpath.txt';init=out/'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; gradle.rootProject.tasks.register('writeRaycastClasspath') { dependsOn 'testClasses'; doLast { new File("+json.dumps(str(cp))+").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command=['./gradlew','-I',str(init),'-PmattmcRustProfile=release','writeRaycastClasspath','-x','testRustNative','--console=plain']
    if args.skip_build:command+=['-x','buildRustNative']
    run(command,out/'build.log')
    tests=['NativeVoxelRaycastTest','NativeVoxelClosestPointTest','NativeVoxelBoxesTest','NativeVoxelJoinTest','NativeVoxelRotationTest']
    run(['./gradlew','-I',str(init),'test','-x','buildRustNative','-x','testRustNative','--console=plain',
        *[arg for name in tests for arg in ['--tests',PACKAGE+name]]],out/'parity.log')
    counts={};parity={}
    for name in tests:
        source=ROOT/('build/test-results/test/TEST-'+PACKAGE+name+'.xml')
        data=source.read_text();(out/(name+'.xml')).write_text(data);r=ET.fromstring(data)
        if int(r.attrib['failures']) or int(r.attrib['errors']):raise RuntimeError('Failed parity: '+name)
        counts[name]=int(r.attrib['tests']);parity[name]=re.findall(r'(?:RAY|CLOSEST|BOX|VOXEL|ROTATION)_\w*PARITY[^\n<]*',data)
    run(['rustc','--edition=2021','--test','src/test/rust/physics.rs','-o',str(out/'physics-tests')],out/'rust-build.log')
    rust=run([str(out/'physics-tests'),'phys::shapes','--test-threads=1'],out/'rust-tests.log')
    native=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
    flags=['-Xbatch','-Xms512m','-Xmx3g','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','--enable-native-access=ALL-UNNAMED']
    pool=','.join(str(c) for c in [args.cpu,*background])
    flags+=['-XX:ActiveProcessorCount='+str(1+len(background)), '-Dmattmc.raycast.cpu='+str(args.cpu), '-Dmattmc.raycast.backgroundCpus='+args.background_cpus]
    base=['taskset','-c',pool,'java',*flags,'-Dmattmc.rust.natives.dir='+str(native.parent),'-cp',cp.read_text().strip(),PACKAGE+'NativeVoxelRaycastVerification']
    files=list((ROOT/'src/main/rust/world/phys').rglob('*.rs'))
    files+=list((ROOT/'src/main/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files+=list((ROOT/'src/test/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files += [ROOT/'DevUtils/tests/physics'/name for name in ['VerifyRustVoxelClosestPoint.py',
        'VerifyRustVoxelBoxes.py','VerifyRustVoxelRotation.py']]
    files += [Path(__file__).resolve(),ROOT/'src/main/java/net/minecraft/world/phys/Vec3.java',ROOT/'src/main/java/net/minecraft/util/Mth.java',ROOT/'src/main/java/net/minecraft/util/NativeLibraryLoader.java',ROOT/'src/main/java/net/minecraft/world/phys/AABB.java',ROOT/'src/main/java/net/minecraft/world/phys/BlockHitResult.java',ROOT/'src/main/java/net/minecraft/core/BlockPos.java',ROOT/'src/main/java/net/minecraft/core/Direction.java',ROOT/'src/main/java/net/minecraft/core/Vec3i.java',ROOT/'src/test/rust/physics.rs']
    report={'reference':REFERENCE,'scope':'Complete public VoxelShape.clip call for eligible outside rays: original Java empty/short/probe arithmetic and inside decisions, native eligibility, exact outside-axis/occupancy proofs, fresh occupancy snapshot and original clone capacity trimming, current word/coordinate/ray input copies, ordinary FFM call, conservative whole-grid miss proof, ordered greedy traversal, AABB normalization and translation, strict ordered intersection, native output, Java Vec3/BlockHitResult construction and all raw result fields consumed. No result/input cache. Immediate inside hits remain unchanged Java behavior and are not claimed as migrated or measured gains. Pre-migration Java caller already has production Rust box extraction; vanilla also uses pinned original Java extraction. BOTH baselines must pass 5%. Fixture/library/initial scratch setup excluded equally.',
        'cpu':args.cpu,'background_cpus':background,'warmup_min_ns':15_000_000_000,'warmup_min_gc':2,'sample_target_ns':500_000_000,'case':args.case,'jvm':flags,'java_version':subprocess.check_output(['java','--version'],text=True),
        'hardware':json.loads(subprocess.check_output(['lscpu','-J'],text=True)),'java_tests':counts,'parity':parity,'rust_tests':rust,
        'native_sha256':hashlib.sha256(native.read_bytes()).hexdigest(),'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},'pairs':[],'performance':{}}
    def save():(out/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    save()
    if args.parity_only:print(out/'results.json');return
    orders=[['java','vanilla','native'],['native','java','vanilla'],['vanilla','native','java']]
    report['comparison_orders']=[orders[(fork+args.order_offset)%3] for fork in range(args.forks)]
    save()
    for fork in range(args.forks):
        pair={}
        for mode in orders[(fork+args.order_offset)%3]:
            print(f'Comparison {fork+1}: {mode} ({args.case})',flush=True)
            data=run(base+[mode,args.case],out/f'{fork}-{mode}.log')
            rows=re.findall(r'RAY_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',data)
            if len(rows)!=(23 if args.case=='all' else len(args.case.split(','))):raise RuntimeError('Incomplete benchmark')
            warmups=re.findall(r'RAY_BENCH name=\w+ warmup_ns=(\d+) warmup_gc=(\d+)',data)
            if len(warmups)!=len(rows) or any(int(ns)<15_000_000_000 or int(gc)<2 for ns,gc in warmups):
                raise RuntimeError('Incomplete JIT/heap warmup')
            for name,samples,jit,checksum in rows:
                if any(json.loads(jit)):raise RuntimeError('Compilation during measured rounds')
                row=pair.setdefault(name,{})
                row[mode]=json.loads(samples)
                if 'checksum' in row and row['checksum']!=int(checksum):raise RuntimeError('Different output checksums')
                row['checksum']=int(checksum)
        report['pairs'].append(pair);save()
    rng=random.Random(1977)
    for name in report['pairs'][0]:
        result={}
        for baseline in ['java','vanilla']:
            pairs=[(p[name][baseline],p[name]['native']) for p in report['pairs']]
            ratios=[statistics.median(n)/statistics.median(j) for j,n in pairs]
            boots=sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
            result[baseline]={'baseline_ns':statistics.median(statistics.median(j) for j,n in pairs),'native_ns':statistics.median(statistics.median(n) for j,n in pairs),'ratios':ratios,'ratio_ci95':[boots[250],boots[9749]],'passes':max(ratios)<=.95 and boots[9749]<=.95}
        report['performance'][name]=result
    for p in files:
        if hashlib.sha256(p.read_bytes()).hexdigest()!=report['sources'][str(p.relative_to(ROOT))]:raise RuntimeError('Measured source changed: '+str(p))
    if hashlib.sha256(native.read_bytes()).hexdigest()!=report['native_sha256']:raise RuntimeError('Native library changed')
    for name in report['pairs'][0]:
        if len({p[name]['checksum'] for p in report['pairs']})!=1:raise RuntimeError('Cross-JVM checksum mismatch')
    report['integrity']={'source_hashes_match':True,'native_hash_matches':True,'cross_fork_checksums_match':True}
    save();print(json.dumps(report['performance'],indent=2))
    if not all(b['passes'] for p in report['performance'].values() for b in p.values()):raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__=='__main__':main()
