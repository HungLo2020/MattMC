#!/usr/bin/env python3
"""Pinned closest-point query parity and complete caller performance, including FFM."""
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
DISPATCH = '\t\t\tif (this.shape != null && (long)this.shape.xSize * this.shape.ySize * this.shape.zSize >= NativeVoxelClosestPoint.MIN_CELLS) {\n\t\t\t\tvar nativePoint = NativeVoxelClosestPoint.find(this, vec3);\n\t\t\t\tif (nativePoint != null) return nativePoint;\n\t\t\t}\n'


def strip_closest_point(source):
    from VerifyRustVoxelRaycast import strip_raycast
    source = strip_raycast(source)
    if 'NativeVoxelClosestPoint' not in source:
        return source
    if source.count(DISPATCH) != 1:
        raise RuntimeError('Unexpected closest-point production dispatch')
    return source.replace(DISPATCH, '', 1)


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def audit(out):
    folder = 'src/main/java/net/minecraft/world/phys/shapes/'
    original = subprocess.check_output(['git', 'show', REFERENCE + ':' + folder + 'VoxelShape.java'], cwd=ROOT, text=True)
    if strip_closest_point((ROOT / (folder + 'VoxelShape.java')).read_text()) != original:
        raise RuntimeError('Public shape changed beyond exact dispatch')
    a = original.index('\tpublic Optional<Vec3> closestPointTo(')
    b = original.index('\n\tpublic VoxelShape getFaceShape(', a)
    body = original[a:b].replace('public Optional<Vec3> closestPointTo(Vec3 vec3)',
        'static Optional<Vec3> closestPointTo(VoxelShape source, Vec3 vec3)').replace('this.', 'source.')
    reference = (ROOT / 'src/test/java/net/minecraft/world/phys/shapes/JavaVoxelClosestPoint.java').read_text()
    if reference.count(body) != 1 or reference.count(body.replace('closestPointTo(VoxelShape source',
        'vanilla(VoxelShape source').replace('source.forAllBoxes(', 'JavaVoxelBoxList.forAllBoxes(source, ')) != 1:
        raise RuntimeError('Java reference caller differs from pinned original')
    unchanged = [folder + n for n in ['NativeVoxelBoxes.java', 'BitSetDiscreteVoxelShape.java', 'ArrayVoxelShape.java',
        'CubeVoxelShape.java', 'CubePointRange.java', 'OffsetDoubleList.java']]
    unchanged += ['src/main/java/net/minecraft/world/phys/Vec3.java','src/main/java/net/minecraft/util/Mth.java']
    for p in unchanged:
        if (ROOT / p).read_bytes() != subprocess.check_output(['git', 'show', REFERENCE + ':' + p], cwd=ROOT):
            raise RuntimeError('Existing geometry or numeric semantics changed: ' + p)
    for name in ['JavaVoxelBoxList.java','JavaVoxelBoxes.java','NativeVoxelBoxesTest.java','NativeVoxelJoinTest.java']:
        p='src/test/java/net/minecraft/world/phys/shapes/'+name
        if (ROOT/p).read_bytes()!=subprocess.check_output(['git','show',REFERENCE+':'+p],cwd=ROOT):
            raise RuntimeError('Existing Java box oracle/fixture changed: '+p)
    for name in ['extract.rs','mod.rs','isolated.rs','ffi.rs']:
        p='src/main/rust/world/phys/shapes/box_extract/'+name
        expected = subprocess.check_output(['git','show',REFERENCE+':'+p],cwd=ROOT,text=True)
        if name == 'mod.rs': expected = expected.replace('mod extract;', 'pub(super) mod extract;').replace('mod isolated;', 'mod isolated;\nmod cavity;')
        if name == 'isolated.rs':
            expected = expected.replace('pub(super) fn extract(words: &[u64], dims: [usize; 3], output: &mut [i32]) -> Option<usize> {',
                'pub(super) fn visit<F: FnMut([i32; 6])>(words: &[u64], dims: [usize; 3], emit: &mut F) -> Option<usize> {')
            expected = expected.replace('            output[count * 6..count * 6 + 6].copy_from_slice(&[','            emit([')
        if name == 'extract.rs':
            expected = expected.replace('start: usize, end: usize, output: &mut [i32], count: usize) {',
                'start: usize, end: usize) -> [i32; 6] {')
            expected = expected.replace('    output[count * 6..count * 6 + 6].copy_from_slice(&[\n        x as i32, y as i32, start as i32, x_end as i32, y_end as i32, end as i32,\n    ]);',
                '    [x as i32, y as i32, start as i32, x_end as i32, y_end as i32, end as i32]')
            a = expected.index('/// Same y/x/z traversal')
            expected = expected[:a] + """/// Ordered endpoints retain the existing public box ABI.
pub(super) fn extract(words: &mut [u64], dims: [usize; 3], output: &mut [i32]) -> usize {
    let mut at = 0;
    visit(words, dims, |b| {
        output[at..at + 6].copy_from_slice(&b);
        at += 6;
    })
}

/// Same y/x/z traversal, then Z run, X expansion, Y expansion as Java.
/// The consumer is a monomorphized Rust closure, never a foreign callback.
/// `words` is a private snapshot; the visitor consumes ordered endpoints.
#[inline]
pub(crate) fn visit<F: FnMut([i32; 6])>(words: &mut [u64], dims: [usize; 3], mut emit: F) -> usize {
""" + expected[expected.index('    let [nx, ny, nz] = dims;',a):]
            expected = expected.replace('super::isolated::extract(words, dims, output)', 'super::isolated::visit(words, dims, &mut emit)')
            expected = expected.replace('emit_run(words, dims, x, y, start, end, output, count);',
                'emit(emit_run(words, dims, x, y, start, end));')
        if name == 'extract.rs':
            expected = expected.replace('fn next(words:', 'pub(super) fn next(words:', 1)
            hook='    let [nx, ny, nz] = dims;\n    if let Some(count) = super::isolated::visit'
            expected = expected.replace(hook, '''    let [nx, ny, nz] = dims;
    if let Some(count) = super::cavity::visit(words, dims, &mut emit) {
        clear(words, 0, nx * ny * nz);
        return count;
    }
    if let Some(count) = super::isolated::visit''', 1)
        if (ROOT/p).read_text()!=expected:
            raise RuntimeError('Established Rust traversal changed beyond pinned visitor/cavity dispatch: '+p)
    (out / 'OriginalVoxelShape.java').write_text(original)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/voxel-closest-point-migration/acceptance')
    parser.add_argument('--forks',type=int,default=3)
    parser.add_argument('--cpu',type=int,default=2)
    parser.add_argument('--parity-only',action='store_true')
    parser.add_argument('--skip-build',action='store_true')
    parser.add_argument('--case',default='all')
    args=parser.parse_args()
    if not args.parity_only and args.forks<3:parser.error('At least three independent JVM comparisons required')
    if args.cpu not in os.sched_getaffinity(0):parser.error('Unavailable CPU')
    out=(ROOT/args.output).resolve()
    if not out.is_relative_to(ROOT/'build'):parser.error('Output must be under build/')
    out.mkdir(parents=True,exist_ok=True);audit(out)
    cp=out/'classpath.txt';init=out/'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; gradle.rootProject.tasks.register('writeClosestPointClasspath') { dependsOn 'testClasses'; doLast { new File("+json.dumps(str(cp))+").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command=['./gradlew','-I',str(init),'-PmattmcRustProfile=release','writeClosestPointClasspath','-x','testRustNative','--console=plain']
    if args.skip_build:command+=['-x','buildRustNative']
    run(command,out/'build.log')
    tests=['NativeVoxelClosestPointTest','NativeVoxelBoxesTest','NativeVoxelJoinTest','NativeVoxelRotationTest']
    run(['./gradlew','-I',str(init),'test','-x','buildRustNative','-x','testRustNative','--console=plain',
        *[arg for name in tests for arg in ['--tests',PACKAGE+name]]],out/'parity.log')
    counts={};parity={}
    for name in tests:
        source=ROOT/('build/test-results/test/TEST-'+PACKAGE+name+'.xml')
        data=source.read_text();(out/(name+'.xml')).write_text(data);r=ET.fromstring(data)
        if int(r.attrib['failures']) or int(r.attrib['errors']):raise RuntimeError('Failed parity: '+name)
        counts[name]=int(r.attrib['tests']);parity[name]=re.findall(r'(?:CLOSEST|BOX|VOXEL|ROTATION)_\w*PARITY[^\n<]*',data)
    run(['rustc','--edition=2021','--test','src/test/rust/physics.rs','-o',str(out/'physics-tests')],out/'rust-build.log')
    rust=run([str(out/'physics-tests'),'phys::shapes','--test-threads=1'],out/'rust-tests.log')
    native=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
    flags=['-Xbatch','-Xms512m','-Xmx3g','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','--enable-native-access=ALL-UNNAMED']
    base=['taskset','-c',str(args.cpu),'java',*flags,'-Dmattmc.rust.natives.dir='+str(native.parent),'-cp',cp.read_text().strip(),PACKAGE+'NativeVoxelClosestPointVerification']
    files=list((ROOT/'src/main/rust/world/phys').rglob('*.rs'))
    files+=list((ROOT/'src/main/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files+=list((ROOT/'src/test/java/net/minecraft/world/phys/shapes').glob('*.java'))
    files += [Path(__file__).resolve(),ROOT/'src/main/java/net/minecraft/world/phys/Vec3.java',ROOT/'src/main/java/net/minecraft/util/Mth.java',ROOT/'src/main/java/net/minecraft/util/NativeLibraryLoader.java']
    report={'reference':REFERENCE,'scope':'Complete closestPointTo query: eligibility, original clone, current words/coordinates copied, ordinary FFM call, greedy box traversal, ordered clamp/distance reduction, output transfer, Optional/Vec3 construction and raw output consumption. No result caching. Java comparator is pinned pre-migration public caller with its existing native box extraction; vanilla comparator additionally uses pinned original Java box extraction. Both baselines must pass 5%. Fixture/library/initial scratch setup excluded equally.',
        'cpu':args.cpu,'case':args.case,'jvm':flags,'java_version':subprocess.check_output(['java','--version'],text=True),
        'hardware':json.loads(subprocess.check_output(['lscpu','-J'],text=True)),'java_tests':counts,'parity':parity,'rust_tests':rust,
        'native_sha256':hashlib.sha256(native.read_bytes()).hexdigest(),'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},'pairs':[],'performance':{}}
    def save():(out/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    save()
    if args.parity_only:print(out/'results.json');return
    orders=[['java','vanilla','native'],['native','java','vanilla'],['vanilla','native','java']]
    for fork in range(args.forks):
        pair={}
        for mode in orders[fork%3]:
            print(f'Comparison {fork+1}: {mode} ({args.case})',flush=True)
            data=run(base+[mode,args.case],out/f'{fork}-{mode}.log')
            rows=re.findall(r'CLOSEST_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',data)
            if len(rows)!=(18 if args.case=='all' else 1):raise RuntimeError('Incomplete benchmark')
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
