#!/usr/bin/env python3
"""Complete public-call latency and Java allocation pilot; not final acceptance.

Run with other server/build jobs idle. Native allocation and deferred Cleaner
retirement remain separate gates. Frozen and pre-change inputs stay immutable.
"""
import argparse, hashlib, json, os, shutil, statistics, subprocess, time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[5]
JAVA=Path('/usr/lib/jvm/java-25-openjdk-amd64/bin')
def sha(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--current-classpath',type=Path,required=True)
    parser.add_argument('--current-native',type=Path,required=True)
    parser.add_argument('--reference',type=Path,default=ROOT/'build/fallen-tree/s0027-pre-3f1d-2')
    parser.add_argument('--warmups',type=int,default=256)
    parser.add_argument('--groups',type=int,default=12)
    parser.add_argument('--batch',type=int,default=32)
    parser.add_argument('--chains',action='store_true',help='Also measure complete JDK/Guava four-decorator stump/log chains on all four public routes')
    args=parser.parse_args();out=args.output.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    frozen=ROOT.parent/'MattMC_JavaPerfTesting/MattMC'
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=frozen,text=True).strip()=='7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git','status','--porcelain','--','src','build.gradle'],cwd=frozen)
    cp={side:(path.read_text().strip()) for side,path in {'frozen':args.reference/'frozen-classpath.txt','pre':args.reference/'classpath.txt','current':args.current_classpath}.items()}
    native={'pre':(args.reference/'native').resolve(),'current':args.current_native.resolve()}
    sources=[Path(__file__).parents[1]/'FallenAudit.java',Path(__file__).parents[1]/'FallenChainRisks.java',*sorted(Path(__file__).parent.glob('*.java'))]
    images={str(p):sha(p) for p in [*sources,Path(__file__).resolve()]}
    for value in cp.values():
        for entry in value.split(os.pathsep):
            p=Path(entry)
            if p.is_dir():images.update({str(f):sha(f) for f in p.rglob('*.class')})
            elif p.is_file():images[str(p)]=sha(p)
    for directory in native.values():p=directory/'mattmc_rust-linux-x64.so';images[str(p)]=sha(p)
    reference=json.loads((args.reference/'manifest.json').read_text())
    images.update(reference['resource_images']);images.update(reference['frozen_resource_images'])
    result={'scope':'Complete public-call latency / Java allocation pilot only; native allocation and deferred retirement unmeasured','runs':{},'comparisons':{},'input_images':images,'guards_pass':False,'work_parity':False,'hardware':subprocess.check_output(['lscpu'],text=True),'toolchain':subprocess.run([str(JAVA/'java'),'--version'],capture_output=True,text=True).stdout,'settings':vars(args)|{'output':str(out),'current_classpath':str(args.current_classpath),'current_native':str(args.current_native),'reference':str(args.reference)}}
    def save():(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    save();prepared={}
    try:
        for side in cp:
            target=out/(side+'-classes');target.mkdir()
            with(out/(side+'-javac.log')).open('w') as log:subprocess.run([str(JAVA/'javac'),'-nowarn','-cp',cp[side],'-d',str(target),*map(str,sources)],check=True,stdout=log,stderr=subprocess.STDOUT)
            manifest=out/(side+'-MANIFEST.MF');manifest.write_text('Premain-Class: fallen.FallenCostAgent\n\n')
            jar=out/(side+'-agent.jar');subprocess.run([str(JAVA/'jar'),'cfm',str(jar),str(manifest),'-C',str(target),'.'],check=True,capture_output=True);prepared[side]=jar
        data={}
        for side in cp:
            run=out/side;run.mkdir();server=run/'server';server.mkdir();(server/'eula.txt').write_text('eula=true\n')
            (server/'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\nsync-chunk-writes=false\n')
            command=['taskset','-c','2-7',str(JAVA/'java'),'-Xms2G','-Xmx4G','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','-Dmax.bg.threads=1','--enable-native-access=ALL-UNNAMED',f'-Daudit.output={run}',f'-Dfallen.cost.side={side}',f'-Dfallen.cost.warmups={args.warmups}',f'-Dfallen.cost.groups={args.groups}',f'-Dfallen.cost.batch={args.batch}',f'-javaagent:{prepared[side]}']
            command.append(f'-Dfallen.cost.chains={str(args.chains).lower()}')
            if side!='frozen':command.append(f'-Dmattmc.rust.natives.dir={native[side]}')
            command+=['-cp',str(prepared[side])+os.pathsep+cp[side],'net.minecraft.server.Main','--nogui','--port','0'];(run/'command.json').write_text(json.dumps(command,indent=2))
            print('START',side,flush=True);start=time.monotonic();mapped=set()
            with(run/'server.log').open('w') as log:
                process=subprocess.Popen(command,cwd=server,stdout=log,stderr=subprocess.STDOUT)
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                path=line.split()[-1]
                                if 'mattmc_rust' in path and Path(path).is_file():mapped.add(path)
                        except (FileNotFoundError,ProcessLookupError):pass
                        if time.monotonic()-start>1200:raise TimeoutError(side)
                        time.sleep(.5)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        try:process.wait(timeout=20)
                        except subprocess.TimeoutExpired:process.kill();process.wait()
            result['runs'][side]={'exit':process.returncode,'elapsed_seconds':time.monotonic()-start,'mapped_native':{p:sha(p) for p in sorted(mapped)}};save()
            assert process.returncode==0 and (run/'complete.json').exists(),run/'server.log'
            assert (not mapped if side=='frozen' else mapped and {sha(p) for p in mapped}=={sha(native[side]/'mattmc_rust-linux-x64.so')})
            rows=json.loads((run/'costs.json').read_text());assert len(rows)==(28 if args.chains else 20)
            data[side]={(r['variant'],r['mode']):r for r in rows};print('DONE',side,flush=True)
        for key,old in data['frozen'].items():
            current=data['current'][key];pre=data['pre'][key]
            same=all(old[k]==current[k] for k in ('checksum','random','snapshot'))
            def metric(row,name):
                samples=[r[name]/r['operations'] for r in row['samples'] if r['quiet']]
                return statistics.median(samples) if len(samples)>=3 else None
            f,c,p=(metric(row,'nanos') for row in (old,current,pre))
            result['comparisons']['/'.join(key)]={'work_identical_frozen':same,'pre_work_identical_frozen':all(old[k]==pre[k] for k in ('checksum','random','snapshot')),'frozen_ns':f,'current_ns':c,'pre_ns':p,'current_over_frozen':c/f if c and f else None,'current_over_pre':c/p if c and p else None,'java_bytes':{side:metric(data[side][key],'javaBytes') for side in data},'native_calls':current['nativeCalls'],'quiet_groups':{side:sum(r['quiet'] for r in data[side][key]['samples']) for side in data}}
            if key[1] in ('native','selector'):assert current['nativeCalls']==args.groups*args.batch
        result['work_parity']=all(r['work_identical_frozen'] for r in result['comparisons'].values());save();assert result['work_parity']
    finally:
        result['guards_pass']=all(Path(p).exists() and sha(p)==digest for p,digest in images.items());save()
    assert result['guards_pass'];print(json.dumps(result['comparisons'],indent=2),flush=True)

if __name__=='__main__':main()
