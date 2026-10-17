#!/usr/bin/env python3
"""Verify the Rust bulk long-array wire codec against pinned original Java."""
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

ROOT=Path(__file__).resolve().parents[3]
REFERENCE='8b9173b399a629578a7bf0168e4d3ea32b10e8a6'
PACKAGE='net.minecraft.network.'
FRIENDLY='src/main/java/net/minecraft/network/FriendlyByteBuf.java'
TEST_BASE=ROOT/'src/test/java/net/minecraft/network'
CASES=['heap_read_128','heap_write_128','direct_read_128','direct_write_128',
       'pooled_heap_read_256','pooled_heap_write_256','pooled_direct_read_342','pooled_direct_write_342',
       'heap_slice_read_456','heap_slice_write_456','pooled_direct_slice_read_1024','pooled_direct_slice_write_1024',
       'heap_read_8193','heap_write_8193','direct_read_65536','direct_write_65536',
       'heap_prefixed_read_512','direct_prefixed_write_512']


def run(command,path):
    result=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    path.write_text(result.stdout)
    if result.returncode:raise RuntimeError('Command failed; see '+str(path))
    return result.stdout


def audit(out):
    original=subprocess.check_output(['git','show',REFERENCE+':'+FRIENDLY],cwd=ROOT,text=True)
    current=(ROOT/FRIENDLY).read_text()
    for hook in ['\t\tif (ls != null && ls.length >= NativeLongArray.MIN_VALUES && NativeLongArray.write(byteBuf, ls)) return;\n',
                 '\t\tif (ls != null && ls.length >= NativeLongArray.MIN_VALUES && NativeLongArray.read(byteBuf, ls)) return ls;\n']:
        if current.count(hook)!=1:raise RuntimeError('Missing/duplicate production hook')
        current=current.replace(hook,'')
    if current!=original:raise RuntimeError('FriendlyByteBuf changed beyond two native dispatch hooks')
    methods=[]
    for name in ['writeLongArray','writeFixedSizeLongArray','readLongArray','readFixedSizeLongArray']:
        match=re.search(r'\tpublic static (?:void|long\[\]) '+name+r'\(ByteBuf byteBuf,?[^\n]*',original)
        a=match.start();b=original.index('{',a)+1;depth=1
        while depth:depth+=(original[b]=='{')-(original[b]=='}');b+=1
        methods.append(original[a:b])
    expected='package net.minecraft.network;\n\nimport io.netty.buffer.ByteBuf;\nimport io.netty.handler.codec.DecoderException;\n\n/** Literal original fixed/prefixed long-array methods; source-pinned by verification. */\nfinal class JavaLongArray {\n'+'\n\n'.join(methods)+'\n}\n'
    if (TEST_BASE/'JavaLongArray.java').read_text()!=expected:raise RuntimeError('Original Java oracle changed')
    (out/'OriginalFriendlyByteBuf.java').write_text(original)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',default='build/long-array-migration/acceptance')
    parser.add_argument('--forks',type=int,default=3)
    parser.add_argument('--cpu',type=int,default=2)
    parser.add_argument('--background-cpus',default='3,4')
    parser.add_argument('--case',default='all')
    parser.add_argument('--skip-build',action='store_true')
    parser.add_argument('--parity-only',action='store_true')
    parser.add_argument('--pilot',action='store_true')
    args=parser.parse_args()
    cases=CASES if args.case=='all' else args.case.split(',')
    if len(set(cases))!=len(cases) or any(c not in CASES for c in cases):parser.error('Unknown/duplicate case')
    if not args.pilot and not args.parity_only and args.forks<3:parser.error('At least three independent comparisons required')
    background=[int(c) for c in args.background_cpus.split(',')]
    if len(background)<2 or args.cpu in background or len(set(background))!=len(background):parser.error('Separate worker CPUs required')
    if not set([args.cpu,*background]).issubset(os.sched_getaffinity(0)):parser.error('CPU unavailable')
    out=(ROOT/args.output).resolve()
    if not out.is_relative_to(ROOT/'build'):parser.error('Output must be under build/')
    out.mkdir(parents=True,exist_ok=True);audit(out)
    cp,init=out/'classpath.txt',out/'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; gradle.rootProject.tasks.register('writeLongArrayClasspath') { dependsOn 'testClasses'; doLast { new File("+json.dumps(str(cp))+").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command=['./gradlew','-I',str(init),'-PmattmcRustProfile=release','writeLongArrayClasspath','-x','testRustNative','--console=plain']
    if args.skip_build:command+=['-x','buildRustNative']
    run(command,out/'build.log')
    run(['./gradlew','-I',str(init),'test','-x','buildRustNative','-x','testRustNative','--tests',PACKAGE+'NativeLongArrayTest','--console=plain'],out/'parity.log')
    xml=(ROOT/('build/test-results/test/TEST-'+PACKAGE+'NativeLongArrayTest.xml')).read_text();(out/'parity.xml').write_text(xml)
    result=ET.fromstring(xml)
    if int(result.attrib['failures']) or int(result.attrib['errors']):raise RuntimeError('Parity failed')
    run(['rustc','--edition=2021','--test','src/test/rust/network.rs','-o',str(out/'network-tests')],out/'rust-build.log')
    rust=run([str(out/'network-tests'),'--test-threads=1'],out/'rust-tests.log')
    library=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
    flags=['-Xbatch','-Xms512m','-Xmx3g','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','--enable-native-access=ALL-UNNAMED',
           '-XX:ActiveProcessorCount='+str(1+len(background)),'-Dmattmc.longArray.cpu='+str(args.cpu),'-Dmattmc.longArray.backgroundCpus='+args.background_cpus]
    base=['taskset','-c',','.join(str(c) for c in [args.cpu,*background]),'java',*flags,
          '-Dmattmc.rust.natives.dir='+str(library.parent),'-cp',cp.read_text().strip(),PACKAGE+'NativeLongArrayVerification']
    files=[ROOT/FRIENDLY,ROOT/'src/main/java/net/minecraft/network/NativeLongArray.java',ROOT/'src/main/java/net/minecraft/network/RegistryFriendlyByteBuf.java',
           ROOT/'src/main/java/net/minecraft/network/VarInt.java',ROOT/'src/main/java/net/minecraft/util/NativeLibraryLoader.java',ROOT/'src/test/rust/network.rs',
           ROOT/'src/main/rust/Cargo.toml',ROOT/'src/main/rust/Cargo.lock',Path(__file__).resolve()]
    files+=list((ROOT/'src/main/rust/network').rglob('*.rs'))+list(TEST_BASE.glob('*LongArray*.java'))
    report={'reference':REFERENCE,'scope':'Actual FriendlyByteBuf fixed/prefixed bulk-long-array callers, including exact buffer type/size/lifetime checks, segment construction and slicing, all critical FFM transitions, conversion, chunking, cursor updates and equal checksum consumption. Fixtures/library setup outside timing equally. Normal palette word counts use actual 4096-cell SimpleBitStorage payloads. No result or input cache. No renderer/game pipeline tests.',
            'pilot':args.pilot,'cases':cases,'java_tests':int(result.attrib['tests']),'parity':re.findall(r'LONG_ARRAY_\w+[^\n<]*',xml),'rust_tests':rust,
            'cpu':args.cpu,'background_cpus':background,'jvm':flags,'java_version':subprocess.check_output(['java','--version'],text=True),
            'rust_version':subprocess.check_output(['rustc','--version'],text=True),'hardware':json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
            'native_sha256':hashlib.sha256(library.read_bytes()).hexdigest(),'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},'pairs':[],'performance':{}}
    def save():(out/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    save()
    if args.parity_only:print(out/'results.json');return
    for fork in range(args.forks):
        pair={};report['pairs'].append(pair)
        for name in cases:
            row=pair.setdefault(name,{})
            for mode in (['java','native'] if fork%2==0 else ['native','java']):
                print(f'Comparison {fork+1}: {name} {mode}',flush=True)
                text=run(base+[mode,name]+(['quick'] if args.pilot else []),out/f'{fork}-{name}-{mode}.log')
                matches=re.findall(r'LONG_ARRAY_BENCH case=(\w+) warmup_ns=(\d+) natural_warmup_gc=(\d+) forced_pre_warm_gc=2 repeats=\d+ ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
                if len(matches)!=1 or matches[0][0]!=name:raise RuntimeError('Incomplete measurement')
                _,warmup,gc,samples,jit,checksum=matches[0]
                if not args.pilot and int(warmup)<15_000_000_000:raise RuntimeError('Insufficient warmup')
                if any(json.loads(jit)):raise RuntimeError('Compilation during measurement')
                if 'checksum' in row and row['checksum']!=int(checksum):raise RuntimeError('Different output/workload')
                row['checksum']=int(checksum);row[mode]=json.loads(samples);row[mode+'_natural_warmup_gc']=int(gc);save()
    rng=random.Random(1977)
    for name in cases:
        pairs=[(p[name]['java'],p[name]['native']) for p in report['pairs']]
        ratios=[statistics.median(n)/statistics.median(j) for j,n in pairs]
        bootstrap=sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j)))
                         for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name]={'java_ns':statistics.median(statistics.median(j) for j,n in pairs),
            'native_ns':statistics.median(statistics.median(n) for j,n in pairs),'ratios':ratios,
            'ratio_ci95':[bootstrap[250],bootstrap[9749]],'passes':max(ratios)<=.95 and bootstrap[9749]<=.95}
    for name,digest in report['sources'].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest:raise RuntimeError('Measured source changed: '+name)
    if hashlib.sha256(library.read_bytes()).hexdigest()!=report['native_sha256']:raise RuntimeError('Measured library changed')
    for name in cases:
        if len({p[name]['checksum'] for p in report['pairs']})!=1:raise RuntimeError('Cross-JVM output mismatch')
    report['integrity']={'source_hashes_match':True,'native_hash_matches':True,'cross_fork_checksums_match':True}
    save();print(json.dumps(report['performance'],indent=2))
    if not args.pilot and not all(v['passes'] for v in report['performance'].values()):raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__=='__main__':main()
