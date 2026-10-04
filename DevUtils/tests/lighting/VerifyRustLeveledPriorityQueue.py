#!/usr/bin/env python3
"""Verify the Rust-owned leveled priority queue against pinned original Java."""
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
REFERENCE='cfa7057b6fe2b8dfa84e93f21932be2602eff749'
PACKAGE='net.minecraft.world.level.lighting.'
TEST_BASE=ROOT/'src/test/java/net/minecraft/world/level/lighting'
CASES=['single_ticket','overlapping_tickets','ticket_churn','graph_single_ticket','graph_overlapping_tickets','graph_ticket_churn','graph_single_ticket_incremental','graph_overlapping_tickets_incremental','graph_ticket_churn_incremental']


def run(command,path):
    result=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    path.write_text(result.stdout)
    if result.returncode:raise RuntimeError('Command failed; see '+str(path))
    return result.stdout


def scoped_graph(original):
    # Wrap graph entry operations in exclusive native-access scopes.
    for signature in ['protected void removeFromQueue(long l)',
                      'protected void checkEdge(long l, long m, int i, boolean bl)',
                      'protected final int runUpdates(int i)']:
        start = original.index('\t' + signature + ' {')
        body_start = original.index('\n', start) + 1
        depth = 1
        end = body_start
        while depth:
            if original[end] == '{': depth += 1
            if original[end] == '}': depth -= 1
            end += 1
        close = original.rfind('\n', body_start, end) + 1
        body = original[body_start:close]
        indented = ''.join('\t' + line if line.strip() else line for line in body.splitlines(True))
        replacement = ('\t\tboolean acquired = this.priorityQueue.beginAccess();\n\t\ttry {\n' + indented
                       + '\t\t} finally {\n\t\t\tthis.priorityQueue.endAccess(acquired);\n\t\t}\n')
        original = original[:body_start] + replacement + original[close:]
    return original


def native_scheduling(original):
    original=original.replace('\t\t\t\tint k = this.calculatePriority(j, i);\n\t\t\t\tthis.priorityQueue.dequeue(l, k, this.levelCount);',
                              '\t\t\t\tthis.priorityQueue.cancelComputed(l, j, i);')
    original=original.replace('\tprivate int calculatePriority(int i, int j) {\n\t\treturn Math.min(Math.min(i, j), this.levelCount - 1);\n\t}\n\n','')
    original=original.replace('\t\t\tint o = this.calculatePriority(j, k);\n\t\t\tif (j != n) {\n\t\t\t\tint p = this.calculatePriority(j, n);\n\t\t\t\tif (o != p && !bl2) {\n\t\t\t\t\tthis.priorityQueue.dequeue(m, o, p);\n\t\t\t\t}\n\n\t\t\t\tthis.priorityQueue.enqueue(m, p);',
                              '\t\t\tif (j != n) {\n\t\t\t\tthis.priorityQueue.reschedule(m, j, bl2 ? NO_COMPUTED_LEVEL : k, n);')
    original=original.replace('this.priorityQueue.dequeue(m, o, this.levelCount);','this.priorityQueue.cancelComputed(m, j, k);')
    original=original.replace('this.priorityQueue.enqueue(l, this.calculatePriority(this.levelCount - 1, k));','this.priorityQueue.enqueueComputed(l, this.levelCount - 1, k);')
    original=original.replace('\t\t\t\tthis.computedLevels.put(m, (byte)n);',
        '\t\t\t\t// Queue work is immediate; avoid rewriting an already identical pending value.\n'
        '\t\t\t\t// Compare unsigned bytes to the clamped int: absence (255) never equals n.\n'
        '\t\t\t\tif (k != n || (this.computedLevels.get(m) & 255) != n) {\n'
        '\t\t\t\t\tthis.computedLevels.put(m, (byte)n);\n\t\t\t\t}')
    return original


def audit(out):
    lighting='src/main/java/net/minecraft/world/level/lighting/'
    def source(name):
        return subprocess.check_output(['git','show',REFERENCE+':'+name],cwd=ROOT,text=True)
    original=source(lighting+'LeveledPriorityQueue.java')
    if (TEST_BASE/'JavaLeveledPriorityQueue.java').read_text()!=original.replace('LeveledPriorityQueue','JavaLeveledPriorityQueue'):
        raise RuntimeError('Original queue oracle changed')
    original=source(lighting+'DynamicGraphMinFixedPoint.java')
    expected=original.replace('DynamicGraphMinFixedPoint','JavaDynamicGraphMinFixedPoint').replace('LeveledPriorityQueue','JavaLeveledPriorityQueue')
    if (TEST_BASE/'JavaDynamicGraphMinFixedPoint.java').read_text()!=expected:
        raise RuntimeError('Original graph oracle changed beyond renames')
    scoped = native_scheduling(scoped_graph(original))
    if (ROOT/(lighting+'DynamicGraphMinFixedPoint.java')).read_text()!=scoped:
        raise RuntimeError('Production graph changed beyond access scopes, native queue scheduling policy and unchanged-value write suppression')
    expected = scoped.replace('DynamicGraphMinFixedPoint','RecordingDynamicGraphMinFixedPoint').replace('LeveledPriorityQueue','QueueTranscript.RecordingQueue')
    if (TEST_BASE/'RecordingDynamicGraphMinFixedPoint.java').read_text()!=expected:
        raise RuntimeError('Recording graph differs from production algorithm/access scopes')
    original=source('src/main/java/net/minecraft/server/level/ChunkTracker.java')
    expected=original.replace('ChunkTracker','JavaChunkTracker').replace('DynamicGraphMinFixedPoint','JavaDynamicGraphMinFixedPoint').replace('package net.minecraft.server.level;','package net.minecraft.world.level.lighting;')
    if (TEST_BASE/'JavaChunkTracker.java').read_text()!=expected:
        raise RuntimeError('Original chunk graph oracle changed beyond relocation and renames')
    if (ROOT/'src/main/java/net/minecraft/server/level/ChunkTracker.java').read_text()!=original:
        raise RuntimeError('Production chunk tracker changed')
    expected=original.replace('ChunkTracker','RecordingChunkTracker').replace('DynamicGraphMinFixedPoint','RecordingDynamicGraphMinFixedPoint').replace('package net.minecraft.server.level;','package net.minecraft.world.level.lighting;')
    if (TEST_BASE/'RecordingChunkTracker.java').read_text()!=expected:
        raise RuntimeError('Recording chunk graph changed beyond relocation and renames')
    # Concrete overloads prevent recorder receiver profiles from contaminating
    # either timed path. Their workload bodies must be byte-for-byte equal.
    transcript=(TEST_BASE/'QueueTranscript.java').read_text()
    for method in ['settle', 'exercise']:
        bodies=[]
        for graph in ['NativeGraph', 'OriginalGraph', 'RecordingGraph']:
            start=transcript.index('    static void '+method+'('+graph+' graph')
            body=transcript.index('{',start); end=body+1; depth=1
            while depth:
                if transcript[end]=='{': depth+=1
                if transcript[end]=='}': depth-=1
                end+=1
            bodies.append(transcript[body:end])
        if len(set(bodies))!=1: raise RuntimeError('Different workload helper bodies')
    (out/'reference.txt').write_text(REFERENCE+'\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',default='build/lighting-priority-queue-migration/acceptance')
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
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false }; jvmArgs '-XX:-OmitStackTraceInFastThrow' }; gradle.rootProject.tasks.register('writeLightingQueueClasspath') { dependsOn 'testClasses'; doLast { new File("+json.dumps(str(cp))+").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command=['./gradlew','-I',str(init),'-PmattmcRustProfile=release','writeLightingQueueClasspath','-x','testRustNative','--console=plain']
    if args.skip_build:command+=['-x','buildRustNative']
    run(command,out/'build.log')
    run(['./gradlew','-I',str(init),'test','-x','buildRustNative','-x','testRustNative','--tests',PACKAGE+'NativeLeveledPriorityQueueTest','--console=plain'],out/'parity.log')
    xml=(ROOT/('build/test-results/test/TEST-'+PACKAGE+'NativeLeveledPriorityQueueTest.xml')).read_text();(out/'parity.xml').write_text(xml)
    result=ET.fromstring(xml)
    if int(result.attrib['failures']) or int(result.attrib['errors']):raise RuntimeError('Parity failed')
    run(['rustc','--edition=2021','--test','src/test/rust/lighting_priority_queue.rs','-o',str(out/'queue-tests')],out/'rust-build.log')
    rust=run([str(out/'queue-tests'),'--test-threads=1'],out/'rust-tests.log')
    library=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
    flags=['-Xbatch','-Xms512m','-Xmx3g','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','--enable-native-access=ALL-UNNAMED',
           '-XX:ActiveProcessorCount='+str(1+len(background)),'-Dmattmc.lightingQueue.cpu='+str(args.cpu),'-Dmattmc.lightingQueue.backgroundCpus='+args.background_cpus]
    base=['taskset','-c',','.join(str(c) for c in [args.cpu,*background]),'java',*flags,
          '-Dmattmc.rust.natives.dir='+str(library.parent),'-cp',cp.read_text().strip(),]
    files=[ROOT/'src/main/java/net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint.java', ROOT/'src/main/java/net/minecraft/server/level/ChunkTracker.java', ROOT/'src/main/rust/world/level/lighting/mod.rs',ROOT/'src/main/java/net/minecraft/world/level/lighting/LeveledPriorityQueue.java',
           Path(__file__).resolve(),ROOT/'src/test/rust/lighting_priority_queue.rs',
           *sorted((ROOT/'src/main/rust/world/level/lighting/priority_queue').glob('*.rs')),
           *[TEST_BASE/name for name in ['JavaLeveledPriorityQueue.java','JavaDynamicGraphMinFixedPoint.java','JavaChunkTracker.java','RecordingChunkTracker.java','RecordingDynamicGraphMinFixedPoint.java',
              'NativeLeveledPriorityQueueTest.java','NativeLeveledPriorityQueueVerification.java','NativeLeveledGraphVerification.java','QueueTranscript.java','LightingQueueAffinity.java']]]
    report={'reference':REFERENCE,'cpu_time_scope':'Current benchmark thread, including Java and Rust call execution; excludes scheduler delays and concurrent worker CPU. Wall time remains the primary elapsed-time measurement.', 'scope':'Native-owned queue membership, order, priority policy and rescheduling. Queue cases replay production intent calls plus actual access scopes, versus their original Java priority/queue sequences; capture and construction excluded equally. Graph cases time the real ChunkTracker update algorithm, callbacks, source changes and settling with each backend. All native calls, ownership leases, data movement, growth retries and output checksums included. No output cache, deferred operations, renderer or entire-game tests.',
            'pilot':args.pilot,'cases':cases,'java_tests':int(result.attrib['tests']),'parity':re.findall(r'LIGHT_QUEUE_\w+[^\n<]*',xml),'rust_tests':rust,
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
                benchmark='NativeLeveledGraphVerification' if name.startswith('graph_') else 'NativeLeveledPriorityQueueVerification'
                text=run(base+[PACKAGE+benchmark,mode,name]+(['quick'] if args.pilot else []),out/f'{fork}-{name}-{mode}.log')
                matches=re.findall(r'LIGHT_QUEUE_BENCH case=(\w+) warmup_ns=(\d+) repeats=\d+ ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
                fixtures=re.findall(r'LIGHT_QUEUE_FIXTURE case=(\w+) calls=(\d+) mix=(\[.*?\]) trace=([a-f0-9]{64}) native_operations=(\d+) java_operations=(\d+)',text)
                if len(matches)!=1 or matches[0][0]!=name or len(fixtures)!=1 or fixtures[0][0]!=name:raise RuntimeError('Incomplete measurement')
                _,warmup,samples,jit,checksum=matches[0]
                _,count,mix,trace,native_ops,java_ops=fixtures[0]
                metadata={'calls':int(count),'mix':json.loads(mix),'trace_sha256':trace,'native_logical_operations':int(native_ops),'original_java_queue_operations':int(java_ops)}
                if 'fixture' in row and row['fixture']!=metadata:raise RuntimeError('Different workload')
                row['fixture']=metadata
                if not args.pilot and int(warmup)<15_000_000_000:raise RuntimeError('Insufficient warmup')
                if any(json.loads(jit)):raise RuntimeError('Compilation during measurement')
                if 'checksum' in row and row['checksum']!=int(checksum):raise RuntimeError('Different output/workload')
                row['checksum']=int(checksum);row[mode]=json.loads(samples)
                cpu_samples=re.findall(r'cpu_ns=(\[.*?\])',text)
                if len(cpu_samples)!=1:raise RuntimeError('Missing CPU samples')
                row[mode+'_cpu']=json.loads(cpu_samples[0]);save()
    rng=random.Random(1977)
    def summarize(pairs):
        ratios=[statistics.median(n)/statistics.median(j) for j,n in pairs]
        bootstrap=sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j)))
                         for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        return {'java_ns':statistics.median(statistics.median(j) for j,n in pairs),
            'native_ns':statistics.median(statistics.median(n) for j,n in pairs),'ratios':ratios,
            'ratio_ci95':[bootstrap[250],bootstrap[9749]],'passes':max(ratios)<=.95 and bootstrap[9749]<=.95}
    for name in cases:
        report['performance'][name]=summarize([(p[name]['java'],p[name]['native']) for p in report['pairs']])
        report['performance'][name]['thread_cpu']=summarize([(p[name]['java_cpu'],p[name]['native_cpu']) for p in report['pairs']])
    for name,digest in report['sources'].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest:raise RuntimeError('Measured source changed: '+name)
    if hashlib.sha256(library.read_bytes()).hexdigest()!=report['native_sha256']:raise RuntimeError('Measured library changed')
    for name in cases:
        if len({p[name]['checksum'] for p in report['pairs']})!=1:raise RuntimeError('Cross-JVM output mismatch')
        if len({p[name]['fixture']['trace_sha256'] for p in report['pairs']})!=1:raise RuntimeError('Cross-JVM input transcript mismatch')
    report['integrity']={'source_hashes_match':True,'native_hash_matches':True,'cross_fork_checksums_match':True}
    save();print(json.dumps(report['performance'],indent=2))
    # Performance is reported honestly; 5% is a preference, not a correctness gate.
    print(out/'results.json')


if __name__=='__main__':main()
