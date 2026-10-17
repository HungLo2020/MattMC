#!/usr/bin/env python3
"""Current-only replay of a guarded cost receipt after upstream integration.

Reuses the original compiled observer and operation body, settings and workload.
Never reruns or replaces original Frozen/pre results. Raw noisy rows remain.
"""
import argparse, hashlib, json, os, statistics, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[5]
def sha(path):
    with Path(path).open('rb')as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--current-classpath',type=Path,required=True);p.add_argument('--current-native',type=Path,required=True);args=p.parse_args();out=args.output.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);reference=args.reference.resolve();prior=json.loads((reference/'result.json').read_text());assert prior.get('guards_pass')and prior.get('passed',prior.get('work_parity'))
    assert not os.environ.get('LD_PRELOAD'),'Allocation instrumentation must be absent from latency measurements'
    command=json.loads((reference/'current/command.json').read_text());cp=args.current_classpath.read_text().strip();native=args.current_native.resolve();oldcp=command[command.index('-cp')+1];agent=Path(next(s.split(':',1)[1]for s in command if s.startswith('-javaagent:')));assert agent.exists();command[command.index('-cp')+1]=str(agent)+os.pathsep+cp
    for i,value in enumerate(command):
        if value.startswith('-Daudit.output='):command[i]='-Daudit.output='+str(out)
        if value.startswith('-Dmattmc.rust.natives.dir='):command[i]='-Dmattmc.rust.natives.dir='+str(native)
    images={str(agent):sha(agent),str(reference/'current/costs.json'):sha(reference/'current/costs.json'),str(native/'mattmc_rust-linux-x64.so'):sha(native/'mattmc_rust-linux-x64.so')}
    for entry in cp.split(os.pathsep):
        path=Path(entry)
        if path.is_file():images[str(path)]=sha(path)
        elif path.is_dir():images.update({str(p):sha(p)for p in path.rglob('*')if p.is_file()})
    server=out/'server';server.mkdir();(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text((reference/'current/server/server.properties').read_text());(out/'command.json').write_text(json.dumps(command,indent=2));result={'scope':__doc__,'original_receipt_sha256':sha(reference/'result.json'),'input_images':images,'passed':False};mapped=set();start=time.monotonic()
    def save():(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    save()
    try:
        with(out/'server.log').open('w')as log:
            process=subprocess.Popen(command,cwd=server,stdout=log,stderr=subprocess.STDOUT)
            try:
                while process.poll() is None:
                    try:
                        for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                            path=line.split()[-1]
                            if 'mattmc_rust'in path and Path(path).is_file():mapped.add(path)
                    except(FileNotFoundError,ProcessLookupError):pass
                    if time.monotonic()-start>900:raise TimeoutError('Current cost impact')
                    time.sleep(.5)
            finally:
                if process.poll() is None:process.kill();process.wait()
        result.update(exit=process.returncode,mapped_native={p:sha(p)for p in mapped});save();assert process.returncode==0 and(out/'complete.json').exists();assert mapped and {sha(p)for p in mapped}=={sha(native/'mattmc_rust-linux-x64.so')}
        old=json.loads((reference/'current/costs.json').read_text());new=json.loads((out/'costs.json').read_text());assert len(old)==len(new);rows=[]
        for a,b in zip(old,new):
            assert all(a[k]==b[k]for k in('variant','mode','checksum','random','snapshot','nativeCalls')),(b['variant'],b['mode'])
            def med(r,metric):
                samples=[x[metric]/x['operations']for x in r['samples']if x['quiet']];return statistics.median(samples)if len(samples)>=3 else None
            before,after=med(a,'nanos'),med(b,'nanos');rows.append({'variant':b['variant'],'mode':b['mode'],'work_identical':True,'quiet_groups':sum(r['quiet']for r in b['samples']),'original_ns':before,'integrated_ns':after,'integrated_over_original':after/before if after and before else None,'original_java_bytes':med(a,'javaBytes'),'integrated_java_bytes':med(b,'javaBytes')})
        result.update(rows=rows,passed=True)
    finally:result['guards_pass']=all(Path(p).exists()and sha(p)==h for p,h in images.items());result['passed']=result['passed']and result['guards_pass'];save()
    print(json.dumps(result.get('rows'),indent=2));assert result['passed']

if __name__=='__main__':main()
