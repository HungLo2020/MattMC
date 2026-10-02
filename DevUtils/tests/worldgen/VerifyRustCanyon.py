#!/usr/bin/env python3
"""Focused canyon candidate parity/performance, including the complete FFM caller."""
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
REFERENCE = '7af3a1594956f41ed57c3bf67d11ce006e61530f'
PACKAGE = 'net.minecraft.world.level.levelgen.carver.'
CASES = ['canyon','minimum','minimum_gaps','short_dense','short_gaps','tall','canyon_overlap']
BASE = 'src/main/java/net/minecraft/world/level/levelgen/carver/'
TESTS = 'src/test/java/net/minecraft/world/level/levelgen/carver/'


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def compact(text):
    return re.sub(r'\s+', '', text)


def original(name):
    return subprocess.check_output(['git','show',REFERENCE+':'+BASE+name+'.java'],cwd=ROOT).decode()


def close_brace(source, brace):
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return end


def oracle(name, source):
    if name == 'WorldCarver':
        source = re.sub(r'^\tpublic static final WorldCarver<.*\n','',source,flags=re.M)
        source = source.replace('\tprivate final MapCodec<ConfiguredWorldCarver<C>> configuredCodec;\n','')
        for signature in ['\tprivate static <C extends CarverConfiguration, F extends WorldCarver<C>> F register(', '\tpublic ConfiguredWorldCarver<C> configured(', '\tpublic MapCodec<ConfiguredWorldCarver<C>> configuredCodec(']:
            start = source.index(signature)
            source = source[:start] + source[close_brace(source,source.index('{',start)):]
        source = source.replace('\t\tthis.configuredCodec = codec.fieldOf("config").xmap(this::configured, ConfiguredWorldCarver::config);','')
    return re.sub(r'\b(WorldCarver|CaveWorldCarver|CanyonWorldCarver|NetherWorldCarver)\b',lambda m:'Java'+m.group(0),source)


def audit(out):
    for name in ['WorldCarver','CaveWorldCarver','CanyonWorldCarver','NetherWorldCarver']:
        source=original(name)
        if (ROOT / TESTS / ('Java'+name+'.java')).read_text() != oracle(name,source):
            raise RuntimeError('Original oracle changed: '+name)
        (out / ('Original'+name+'.java')).write_text(source)
    source=original('WorldCarver');current=(ROOT / BASE / 'WorldCarver.java').read_text()
    start=current.index('\t\t\tif (NativeCanyonGeometry.isCanyon')
    end=current.index('\t\t\tfor (int u = n;',start)
    restored=current[:start]+current[end:]
    a=restored.index('\tprivate boolean carveCanyonColumns(')
    restored=restored[:a]+restored[close_brace(restored,restored.index('{',a)):]
    if compact(restored)!=compact(source):raise RuntimeError('WorldCarver changed outside canyon batch hook')
    if (ROOT / BASE / 'CaveWorldCarver.java').read_text()!=original('CaveWorldCarver'):raise RuntimeError('Cave producer changed')
    canyon=original('CanyonWorldCarver');current=(ROOT / BASE / 'CanyonWorldCarver.java').read_text()
    old='(carvingContextx, dx, ex, fx, ix) -> this.shouldSkip(carvingContextx, fs, dx, ex, fx, ix)'
    current=current.replace('\t\tWorldCarver.CarveSkipChecker nativeChecker = NativeCanyonGeometry.canyon(fs, '+old+');\n','').replace('\t\t\t\t\tnativeChecker\n','\t\t\t\t\t'+old+'\n')
    if current!=canyon:raise RuntimeError('Canyon producer changed beyond checker metadata')
    if (ROOT / BASE / 'NetherWorldCarver.java').read_text()!=original('NetherWorldCarver'):raise RuntimeError('Nether producer changed')
    methods=[]
    for name in ['CaveWorldCarver','CanyonWorldCarver']:
        source=original(name);a=source.index('\tprivate '+('static ' if name.startswith('Cave') else '')+'boolean shouldSkip(')
        methods.append(source[a:source.rindex('\n}')].replace('private static boolean shouldSkip','static boolean cave').replace('private boolean shouldSkip','static boolean canyon'))
    expected='package net.minecraft.world.level.levelgen.carver;\n\n/** Literal original built-in predicates. */\nfinal class JavaCarverGeometry {\n'+'\n'.join(methods)+'\n}\n'
    if (ROOT / TESTS / 'JavaCarverGeometry.java').read_text()!=expected:raise RuntimeError('Numeric predicates changed')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',default='build/canyon-migration/acceptance')
    parser.add_argument('--forks',type=int,default=3)
    parser.add_argument('--cpu',type=int,default=2)
    parser.add_argument('--skip-build',action='store_true')
    parser.add_argument('--parity-only',action='store_true')
    args=parser.parse_args()
    if not args.parity_only and args.forks<3:parser.error('At least three independent pairs are required')
    if args.cpu not in os.sched_getaffinity(0):parser.error('CPU unavailable')
    out=(ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT/'build'):parser.error('Output must be under build/')
    out.mkdir(parents=True,exist_ok=True);audit(out)
    cpfile,init=out/'classpath.txt',out/'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeCarverClasspath') { dependsOn 'testClasses'; doLast { new File("+json.dumps(str(cpfile))+").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command=['./gradlew','-I',str(init),'-PmattmcRustProfile=release','writeCarverClasspath','-x','testRustNative','--console=plain']
    if args.skip_build:command+=['-x','buildRustNative']
    print('Building and auditing original source',flush=True);run(command,out/'build.log')
    run(['./gradlew','test','-x','buildRustNative','-x','testRustNative','--tests',PACKAGE+'NativeCanyonGeometryTest','--console=plain'],out/'parity.log')
    xml=ROOT/('build/test-results/test/TEST-'+PACKAGE+'NativeCanyonGeometryTest.xml');(out/'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():raise RuntimeError('Parity failed')
    run(['rustc','--edition=2021','--test','src/test/rust/worldgen.rs','-o',str(out/'worldgen-tests')],out/'rust-build.log')
    run([str(out/'worldgen-tests'),'carver::canyon'],out/'rust-tests.log')
    native=ROOT/'build/rust/native'
    jvm=['-Xbatch','-Xms512m','-Xmx3g','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','--enable-native-access=ALL-UNNAMED']
    base=['taskset','-c',str(args.cpu),'java',*jvm,'-Dmattmc.rust.natives.dir='+str(native),'-cp',cpfile.read_text().strip(),PACKAGE+'NativeCanyonGeometryVerification']
    files=list((ROOT/'src/main/rust/world/level/levelgen/carver').rglob('*.rs'))+list((ROOT/BASE).glob('*.java'))+list((ROOT/TESTS).glob('*.java'))
    files += [ROOT/'src/main/java/net/minecraft/world/level/chunk/CarvingMask.java',ROOT/'src/main/java/net/minecraft/world/level/levelgen/WorldGenerationContext.java',ROOT/'src/main/rust/world/level/levelgen/mod.rs',ROOT/'src/test/rust/worldgen.rs',ROOT/'src/main/rust/Cargo.toml',ROOT/'src/main/rust/Cargo.lock',Path(__file__).resolve()]
    report={'reference':REFERENCE,'scope':'Actual complete canyon carveEllipsoid caller with original bounds, live masks, ordered coordinate/column-state consumption, eligibility, inputs, width-factor copying/validation, native transitions and output copies. Terminal carveBlock is replaced equally by a checksum consumer; world writes and tunnel RNG are outside timing and covered by full producer parity.',
        'fixtures':'128 original seeded intersecting ellipsoids from the registered canyon config, including small Java compatibility paths. No geometry classification is precomputed. Native and original checkers are precreated caller-owned inputs; all FFM metadata/factor preparation remains timed. Additional minimum/tall cases and fully occupied mask cases. BitSet clear for empty-mask cases is timed on both sides; mask probes consume coordinates in both modes.',
        'jvm':jvm,'cpu':args.cpu,'java_version':subprocess.check_output(['java','--version'],text=True),'rust_version':subprocess.check_output(['rustc','--version'],text=True),'hardware':json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
        'native_sha256':hashlib.sha256((native/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
        'config_hashes':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'src/main/resources/data/minecraft/worldgen/configured_carver').glob('*.json')},'parity':re.findall(r'CARVER_\w+_PARITY[^\n<]*',xml.read_text()),'pairs':[],'performance':{}}
    def save():(out/'results.json').write_text(json.dumps(report,indent=2))
    save()
    if args.parity_only:print(out/'results.json');return
    for fork in range(args.forks):
        pair={};report['pairs'].append(pair)
        for name in CASES:
            row=pair.setdefault(name,{})
            for mode in (['java','native'] if fork%2==0 else ['native','java']):
                print(f'Pair {fork+1}: {name} {mode}',flush=True)
                text=run(base+[mode,name],out/f'{fork}-{name}-{mode}.log')
                matches=re.findall(r'CARVER_BENCH case=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+) probes=(\d+) carved=(\d+)',text)
                if len(matches)!=1 or matches[0][0]!=name:raise RuntimeError('Incomplete measurements')
                _,ns,jit,checksum,probes,carved=matches[0]
                if any(json.loads(jit)):raise RuntimeError('Compilation during measurements')
                stamp=[int(checksum),int(probes),int(carved)]
                if 'outputs' in row and row['outputs']!=stamp:raise RuntimeError('Unequal workloads')
                row['outputs']=stamp;row[mode]=json.loads(ns);save()
    rng=random.Random(41287)
    for name in CASES:
        pairs=[(p[name]['java'],p[name]['native']) for p in report['pairs']]
        ratios=[statistics.median(n)/statistics.median(j) for j,n in pairs]
        boots=sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name]={'java_ns':statistics.median(statistics.median(j) for j,n in pairs),'native_ns':statistics.median(statistics.median(n) for j,n in pairs),'ratios':ratios,'ratio_ci95':[boots[250],boots[9749]],'passes':max(ratios)<=.95 and boots[9749]<=.95}
    save();print(json.dumps(report['performance'],indent=2))
    if not all(v['passes'] for v in report['performance'].values()):raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__=='__main__':main()
