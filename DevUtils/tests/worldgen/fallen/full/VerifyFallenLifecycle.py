#!/usr/bin/env python3
"""Actual stock fallen calls, FEATURES rollback/retry and a missed-rollback kill."""
import argparse, json, os, subprocess
from pathlib import Path
import VerifyFallenFull as fallen

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--current-classpath',type=Path);p.add_argument('--current-native',type=Path);args=p.parse_args();args.inputs=args.inputs.resolve();out=args.output.resolve();assert out.is_relative_to(fallen.ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    source=json.loads((args.inputs/'result.json').read_text());assert source['passed'] and source['guards_pass']
    original=json.loads((args.inputs/'current-fresh/command.json').read_text());cp=original[original.index('-cp')+1];native=next(s.split('=',1)[1]for s in original if s.startswith('-Dmattmc.rust.natives.dir='))
    sources=[Path(__file__).parents[1]/'FallenAudit.java',Path(__file__).with_name('FallenLifecycle.java'),Path(__file__).with_name('FallenLifecycleAgent.java')]
    images={**source['class_images'],**source['native_images'],**{str(s):fallen.sha(s)for s in sources}}
    assert bool(args.current_classpath)==bool(args.current_native)
    if args.current_classpath:
        cp=args.current_classpath.read_text().strip();native=str(args.current_native.resolve())
        images.update({str(p):fallen.sha(p)for entry in cp.split(os.pathsep)if Path(entry).is_dir()for p in Path(entry).rglob('*.class')});library=Path(native)/'mattmc_rust-linux-x64.so';images[str(library)]=fallen.sha(library)
    resource=json.loads((fallen.ROOT/'build/fallen-tree/s0027-pre-3f1d-2/manifest.json').read_text());images.update(resource['resource_images']);images.update(resource['frozen_resource_images']);assert all(fallen.sha(k)==v for k,v in images.items())
    classes=out/'classes';classes.mkdir();jar=out/'agent.jar';manifest=out/'MANIFEST.MF';manifest.write_text('Premain-Class: fallen.FallenLifecycleAgent\n\n')
    result={'scope':'Real five-variant failed-write transaction/rollback/retry; exact native and original-Java physical/RNG prefixes, no save durability or timing claim','runs':{},'passed':False,'input_images':images}
    def save():(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    save()
    try:
        with(out/'compile.log').open('w')as log:subprocess.run([str(fallen.JAVA/'javac'),'-nowarn','-cp',cp,'-d',str(classes),*map(str,sources)],check=True,stdout=log,stderr=subprocess.STDOUT)
        subprocess.run([str(fallen.JAVA/'jar'),'cfm',str(jar),str(manifest),'-C',str(classes),'.'],check=True,capture_output=True)
        for mode in ('native','java','no-rollback'):
            run=out/mode;run.mkdir();server=run/'server';server.mkdir();(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\n')
            command=['taskset','-c','2-7',str(fallen.JAVA/'java'),'-Xms1G','-Xmx4G','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','-Dmax.bg.threads=1','--enable-native-access=ALL-UNNAMED',f'-Daudit.output={run}',f'-Dmattmc.rust.natives.dir={native}',f'-javaagent:{jar}']
            if mode=='java':command+=['-Dmattmc.worldgen.javaDecoration=true','-Dmattmc.worldgen.decorationTest=javafail:-1']
            if mode=='no-rollback':command+=['-Dmattmc.worldgen.decorationRollback=false']
            command+=['-cp',str(jar)+os.pathsep+cp,'net.minecraft.server.Main','--nogui','--port','0'];(run/'command.json').write_text(json.dumps(command,indent=2))
            with(run/'server.log').open('w')as log:r=subprocess.run(command,cwd=server,stdout=log,stderr=subprocess.STDOUT,timeout=300)
            row={'exit':r.returncode,'complete':(run/'complete.json').exists(),'failure':(run/'failed.txt').read_text()if(run/'failed.txt').exists()else None};result['runs'][mode]=row;save();print(mode,row,flush=True)
            if mode!='no-rollback':assert row['exit']==0 and row['complete'],run/'server.log'
            else:assert not row['complete'] and 'Partial family publication' in row['failure']
        a=json.loads((out/'native/lifecycle.json').read_text());b=json.loads((out/'java/lifecycle.json').read_text());assert len(a)==len(b)==10
        for x,y in zip(a,b):assert all(x[k]==y[k]for k in ('variant','writes','prefixRandom','retryRandom','retrySnapshot','rollbackExact','deferredAfterRetry')),(x['variant'],x['writes'])
        result['passed']=True
    finally:
        result['guards_pass']=all(Path(k).exists() and fallen.sha(k)==v for k,v in images.items());result['passed']=result['passed'] and result['guards_pass'];save()
    assert result['passed']

if __name__=='__main__':main()
