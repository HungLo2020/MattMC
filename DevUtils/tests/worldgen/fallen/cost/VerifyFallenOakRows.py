#!/usr/bin/env python3
"""Only two noisy oak rows: matched original-Current/final-Current body replay.

Original Frozen/pre budget receipts stay immutable. This establishes integration
impact at increased warmup/sample count; no broader matrix or speedup claim.
"""
import argparse, hashlib, json, os, statistics, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[5]
def sha(p):
    with Path(p).open('rb')as f:return hashlib.file_digest(f,'sha256').hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--current-classpath',type=Path,required=True);p.add_argument('--current-native',type=Path,required=True);args=p.parse_args();out=args.output.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    cp={'original':(ROOT/'build/fallen-tree/s0027-edge-verified-inputs-7/classpath.txt').read_text().strip(),'current':args.current_classpath.read_text().strip()};native={'original':ROOT/'build/fallen-tree/s0027-edge-native-build-7/native','current':args.current_native.resolve()};jar=ROOT/'build/fallen-tree/s0027-oak-retry-observer-1/agent.jar';images={str(jar):sha(jar)}
    for side in cp:
        images[str(native[side]/'mattmc_rust-linux-x64.so')]=sha(native[side]/'mattmc_rust-linux-x64.so')
        for entry in cp[side].split(os.pathsep):
            path=Path(entry)
            if path.is_file():images[str(path)]=sha(path)
            elif path.is_dir():images.update({str(f):sha(f)for f in path.rglob('*')if f.is_file()})
    result={'scope':__doc__,'input_images':images,'runs':{},'passed':False};data={}
    def save():(out/'result.json').write_text(json.dumps(result,indent=2))
    save()
    try:
        for side in cp:
            run=out/side;run.mkdir();server=run/'server';server.mkdir();(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\n')
            command=['taskset','-c','2-7','/usr/lib/jvm/java-25-openjdk-amd64/bin/java','-Xms2G','-Xmx4G','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','-Dmax.bg.threads=1','--enable-native-access=ALL-UNNAMED',f'-Daudit.output={run}','-Dfallen.driver=fallen.FallenOakRetry','-Dfallen.cost.side=current','-Dfallen.cost.warmups=4096','-Dfallen.cost.groups=48','-Dfallen.cost.batch=128',f'-Dmattmc.rust.natives.dir={native[side]}',f'-javaagent:{jar}','-cp',str(jar)+os.pathsep+cp[side],'net.minecraft.server.Main','--nogui','--port','0'];(run/'command.json').write_text(json.dumps(command,indent=2));mapped=set();start=time.monotonic()
            with(run/'server.log').open('w')as log:
                process=subprocess.Popen(command,cwd=server,stdout=log,stderr=subprocess.STDOUT)
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                path=line.split()[-1]
                                if 'mattmc_rust'in path and Path(path).is_file():mapped.add(path)
                        except(FileNotFoundError,ProcessLookupError):pass
                        if time.monotonic()-start>900:raise TimeoutError(side)
                        time.sleep(.5)
                finally:
                    if process.poll() is None:process.kill();process.wait()
            assert process.returncode==0 and(run/'complete.json').exists();assert mapped and {sha(p)for p in mapped}=={sha(native[side]/'mattmc_rust-linux-x64.so')}
            data[side]=json.loads((run/'costs.json').read_text());result['runs'][side]={'exit':process.returncode,'mapped':{p:sha(p)for p in mapped}};save();print('Oak-only',side,'complete',flush=True)
        rows=[]
        for a,b in zip(data['original'],data['current']):
            assert all(a[k]==b[k]for k in('variant','mode','checksum','random','snapshot','nativeCalls'))
            row={'workload':b['variant']+'/'+b['mode'],'work_identical':True}
            for side,r in [('original',a),('current',b)]:
                quiet=[x for x in r['samples']if x['quiet']];assert len(quiet)>=3,(side,b['mode'],len(quiet));row[side]={'quiet_groups':len(quiet),'ns':statistics.median(x['nanos']/x['operations']for x in quiet),'java_bytes':statistics.median(x['javaBytes']/x['operations']for x in quiet)}
            rows.append(row)
        result.update(rows=rows,passed=True)
    finally:result['guards_pass']=all(Path(p).exists()and sha(p)==h for p,h in images.items());result['passed']=result['passed']and result['guards_pass'];save()
    print(json.dumps(result.get('rows'),indent=2));assert result['passed']
if __name__=='__main__':main()
