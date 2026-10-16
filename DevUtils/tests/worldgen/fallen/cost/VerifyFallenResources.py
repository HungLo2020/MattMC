#!/usr/bin/env python3
"""Tagged native heap requests/peak and deferred owner retirement, never timing.

Linux/glibc diagnostic: LD_PRELOAD observes unmodified malloc/calloc/realloc,
aligned allocation and free pointers. Requested live heap bytes exclude Java
heap, mmap/code cache/RSS, and the observer's fixed bookkeeping. Per-operation
gates cover only the actual operation thread; later frees retain their tags.
"""
import argparse, hashlib, json, os, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[5]
JAVA=Path('/usr/lib/jvm/java-25-openjdk-amd64/bin')
def sha(path):
    with Path(path).open('rb')as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--sides',default='frozen,pre,current');p.add_argument('--current-classpath',type=Path);p.add_argument('--current-native',type=Path);p.add_argument('--frozen-observations',type=Path);p.add_argument('--reference',type=Path,default=ROOT/'build/fallen-tree/s0027-pre-3f1d-2');p.add_argument('--cases',default='.*');p.add_argument('--expected-cases',type=int,default=20);args=p.parse_args();out=args.output.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    sides=args.sides.split(',');assert set(sides)<=set(('frozen','pre','current'))
    pre=args.reference.resolve();cp={'frozen':(pre/'frozen-classpath.txt').read_text().strip(),'pre':(pre/'classpath.txt').read_text().strip(),'current':(ROOT/'build/fallen-tree/s0027-edge-verified-inputs-7/classpath.txt').read_text().strip()};native={'pre':pre/'native','current':ROOT/'build/fallen-tree/s0027-edge-native-build-7/native'}
    assert bool(args.current_classpath)==bool(args.current_native)
    if args.current_classpath:cp['current']=args.current_classpath.read_text().strip();native['current']=args.current_native.resolve()
    folder=Path(__file__).parent;source=[folder.parent/'FallenAudit.java',folder.parent/'FallenChainRisks.java',*[folder/s for s in ('FallenCost.java','FallenResourceAgent.java','FallenAllocation.java','FallenNativeCost.java')]];observer=folder/'fallen_alloc.c';library=out/'libfallen_alloc.so'
    original=ROOT/'build/fallen-tree/s0027-complete-cost-pilot-1/result.json';inputs={p:h for p,h in json.loads(original.read_text())['input_images'].items() if '/DevUtils/tests/worldgen/fallen/' not in p};inputs[str(original)]=sha(original);inputs.update({str(p):sha(p)for p in [*source,observer,Path(__file__).resolve(),pre/'manifest.json']})
    for side in sides:
        for entry in cp[side].split(os.pathsep):
            if Path(entry).is_dir():inputs.update({str(p):sha(p)for p in Path(entry).rglob('*.class')})
        if side!='frozen':inputs[str(native[side]/'mattmc_rust-linux-x64.so')]=sha(native[side]/'mattmc_rust-linux-x64.so')
    for entry in cp['current'].split(os.pathsep):
        if Path(entry).is_dir():inputs.update({str(p):sha(p)for p in Path(entry).rglob('*.class')})
    library_input=native['current']/'mattmc_rust-linux-x64.so';inputs[str(library_input)]=sha(library_input)
    result={'scope':__doc__,'input_images':inputs,'cases':args.cases,'expected_cases':args.expected_cases,'pre_commit':json.loads((pre/'manifest.json').read_text())['pre_commit'],'runs':{},'comparisons':{},'passed':False}
    def save():(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    save()
    try:
        compile=['gcc','-shared','-fPIC','-O2','-fno-builtin','-Wall','-Wextra','-Werror','-o',str(library),str(observer),'-ldl','-pthread'];(out/'compiler-command.json').write_text(json.dumps(compile))
        with(out/'gcc.log').open('w')as log:subprocess.run(compile,stdout=log,stderr=subprocess.STDOUT,check=True)
        result['observer_library_sha256']=sha(library);jars={}
        for side in sides:
            classes=out/(side+'-classes');classes.mkdir()
            with(out/(side+'-compile.log')).open('w')as log:subprocess.run([str(JAVA/'javac'),'-nowarn','-cp',cp[side],'-d',str(classes),*map(str,source)],stdout=log,stderr=subprocess.STDOUT,check=True)
            manifest=out/(side+'-MANIFEST.MF');manifest.write_text('Premain-Class: fallen.FallenResourceAgent\n\n');jar=out/(side+'-agent.jar');subprocess.run([str(JAVA/'jar'),'cfm',str(jar),str(manifest),'-C',str(classes),'.'],capture_output=True,check=True);jars[side]=jar
        data={}
        for side in sides:
            run=out/side;run.mkdir();server=run/'server';server.mkdir();(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\n')
            command=['taskset','-c','2-7',str(JAVA/'java'),'-Xms2G','-Xmx4G','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','-Dmax.bg.threads=1','--enable-native-access=ALL-UNNAMED',f'-Daudit.output={run}','-Dfallen.driver=fallen.FallenNativeCost',f'-Dfallen.cost.side={side}',f'-Dfallen.alloc.library={library}',f'-javaagent:{jars[side]}']
            command.extend([f'-Dfallen.resourceCases={args.cases}',f'-Dfallen.expectedResourceCases={args.expected_cases}'])
            if side!='frozen':command.append(f'-Dmattmc.rust.natives.dir={native[side]}')
            command+=['-cp',str(jars[side])+os.pathsep+cp[side],'net.minecraft.server.Main','--nogui','--port','0'];(run/'command.json').write_text(json.dumps(command,indent=2));mapped=set();start=time.monotonic();peak=0;print('START native resources',side,flush=True)
            with(run/'server.log').open('w')as log:
                process=subprocess.Popen(command,cwd=server,stdout=log,stderr=subprocess.STDOUT,env={**os.environ,'LD_PRELOAD':str(library)})
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                path=line.split()[-1]
                                if ('mattmc_rust' in path or 'libfallen_alloc' in path)and Path(path).is_file():mapped.add(path)
                            peak=max(peak,next(int(l.split()[1])*1024 for l in Path(f'/proc/{process.pid}/status').read_text().splitlines()if l.startswith('VmRSS:')))
                        except(FileNotFoundError,ProcessLookupError,StopIteration):pass
                        if time.monotonic()-start>600:raise TimeoutError(side)
                        time.sleep(.5)
                finally:
                    if process.poll() is None:process.kill();process.wait()
            result['runs'][side]={'exit':process.returncode,'instrumented_process_peak_rss':peak,'mapped':{p:sha(p)for p in mapped}};save();assert process.returncode==0 and(run/'complete.json').exists(),run/'server.log'
            assert str(library)in mapped;actual={p for p in mapped if 'mattmc_rust'in p};assert(not actual if side=='frozen'else actual and {sha(p)for p in actual}=={sha(native[side]/'mattmc_rust-linux-x64.so')})
            report=json.loads((run/'allocations.json').read_text());assert len(report['cases'])==args.expected_cases;data[side]={(r['variant'],r['mode']):r for r in report['cases']};result['runs'][side]['complete']=json.loads((run/'complete.json').read_text());save();print('DONE native resources',side,flush=True)
        if args.frozen_observations:
            baseline=args.frozen_observations.resolve();prior=json.loads((baseline.parent.parent/'result.json').read_text())
            for path in [*source,observer]:assert prior['input_images'][str(path.resolve()) if str(path.resolve())in prior['input_images']else str(path)]==sha(path)
            data['frozen']={(r['variant'],r['mode']):r for r in json.loads(baseline.read_text())['cases']};result['reused_frozen_observations_sha256']=sha(baseline)
        if 'frozen'in data:
            for side in data:
                result['comparisons'][side]={'work_identical_frozen':all(data[side][key][field]==data['frozen'][key][field]for key in data[side]for field in ('checksum','random','snapshot'))}
            if 'current'in data:assert result['comparisons']['current']['work_identical_frozen']
        result['passed']=True
    finally:
        result['guards_pass']=all(Path(p).exists()and sha(p)==h for p,h in inputs.items());result['passed']=result['passed']and result['guards_pass'];save()
    assert result['passed'];print('NATIVE RESOURCE OBSERVATION COMPLETE',flush=True)

if __name__=='__main__':main()
