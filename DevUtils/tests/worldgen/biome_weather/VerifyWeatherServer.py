#!/usr/bin/env python3
"""Short real-server weather screen; intermediate evidence, not final acceptance."""
import argparse
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
import time

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'chunk_generation'))
import VerifyChunkGeneration as probe
sys.path.insert(0,str(HERE.parent/'biome_temperature'))
from VerifyTemperature import launch


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-runtime',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=probe.ROOT;out=args.output.resolve();pre=args.pre_runtime.resolve()
    if not out.is_relative_to(root/'build'):raise ValueError('output must be under build/')
    out.mkdir(parents=True,exist_ok=False);baseline=probe.verify_pre(pre)
    before=probe.end.inputs(root);frozen_before=probe.end.inputs(probe.FROZEN)
    if frozen_before['head']!=probe.PIN:raise ValueError('Frozen reference moved')
    sources=[HERE/'WeatherAgent.java',HERE/'WeatherAudit.java'];source_hashes={str(p.relative_to(root)):probe.full.sha(p) for p in sources}
    frozen=(root/'build/chunk-generation/s28-frozen-main-classpath.txt').read_text().strip()
    classes=out/'observer';classes.mkdir()
    with (out/'javac.log').open('w') as log:subprocess.run([str(probe.full.JDK/'javac'),'-cp',frozen,'-d',str(classes),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True)
    manifest=out/'MANIFEST.MF';manifest.write_text('Premain-Class: biomeweather.WeatherAgent\n\n');jar=out/'observer.jar'
    subprocess.run([str(probe.full.JDK/'jar'),'cfm',str(jar),str(manifest),'-C',str(classes),'.'],check=True)
    runtimes={'frozen':(frozen,root/'build/chunk-generation/s25-pre522-runtime/native'),'pre':((pre/'classpath.txt').read_text().strip(),pre/'native'),'current':((root/'build/chunk-generation/s28-current-main-classpath.txt').read_text().strip(),root/'build/rust/native')}
    rows=[]
    for side,(cp,native) in runtimes.items():
        folder=out/side;server=folder/'server';server.mkdir(parents=True);(server/'eula.txt').write_text('eula=true\n')
        settings=json.dumps({'layers':[{'height':1,'block':'minecraft:stone'}],'biome':'minecraft:snowy_plains','features':False,'lakes':False})
        (server/'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\nsync-chunk-writes=false\ngenerate-structures=false\nlevel-type=minecraft:flat\ngenerator-settings='+settings+'\n')
        command=['taskset','-c','4-7',str(probe.full.JDK/'java'),'-Xms1G','-Xmx4G','-Xbatch','--enable-native-access=ALL-UNNAMED','-Dmax.bg.threads=1','-Daudit.output='+str(folder),'-Dmattmc.rust.natives.dir='+str(native),'-javaagent:'+str(jar),'-cp',cp,'net.minecraft.server.Main','--nogui','--port','0','--universe',str(server)]
        (folder/'command.json').write_text(json.dumps(command,indent=2)+'\n')
        # The server itself uses an isolated world directory through --universe;
        # process working directory is isolated too, as in the other drivers.
        old=Path.cwd()
        try:os.chdir(server);process=launch(command,folder/'server.log')
        finally:os.chdir(old)
        if not (folder/'complete.json').is_file() or (folder/'failure.txt').exists():raise RuntimeError('actual weather scene failed: '+side)
        if side!='frozen' and probe.full.sha(native/'mattmc_rust-linux-x64.so') not in process['native_mappings'].values():raise RuntimeError('actual native image not observed')
        weather=json.loads((folder/'weather.json').read_text());scene=json.loads((folder/'scene.json').read_text())
        summary=[]
        for row in weather:
            quiet=[x for x in row['samples'] if x['jit_ms']==0 and x['gc_ms']==0]
            summary.append({'name':row['name'],'actual_public_calls':row['actual_public_calls'],'quiet_samples':len(quiet),'median_ns':statistics.median(x['elapsed_ns']/x['count'] for x in quiet),'median_cpu_ns':statistics.median(x['cpu_ns']/x['count'] for x in quiet),'median_java_bytes':statistics.median(x['java_bytes']/x['count'] for x in quiet),'sums':sorted({x['sum'] for x in row['samples']}),'parent_before':row['parent_before'],'parent_after':row['parent_after'],'temperature_before':row['temperature_before'],'temperature_after':row['temperature_after']})
        rows.append({'side':side,'process':process,'scene':scene,'workloads':summary});(out/'rows.json').write_text(json.dumps(rows,indent=2)+'\n');print(side,'actualweather PASS',[(v['name'],round(v['median_ns'],2)) for v in summary],flush=True)
    for row in rows[1:]:
        if row['scene']!=rows[0]['scene']:raise RuntimeError('actual world scene differs')
        for a,b in zip(rows[0]['workloads'],row['workloads']):
            if a['name']!=b['name'] or a['sums']!=b['sums']:raise RuntimeError('actual public outputs differ')
    if before!=probe.end.inputs(root) or frozen_before!=probe.end.inputs(probe.FROZEN):raise RuntimeError('runtime changed during weather screen')
    if source_hashes!={str(p.relative_to(root)):probe.full.sha(p) for p in sources}:raise RuntimeError('observer source changed')
    probe.verify_pre(pre)
    (out/'result.json').write_text(json.dumps({'passed':True,'before':before,'after':probe.end.inputs(root),'pre_head':baseline['source_head'],'frozen':frozen_before,'observer_sources':source_hashes,'observer_jar_sha256':probe.full.sha(jar),'rows':rows,'scope':'One paired trio of actual public weather queries on real ServerLevel block/light services; exact scene/output/work with6quiet samples each, intermediate diagnostic screen only; not final Frozen performance/gameplay acceptance'},indent=2)+'\n')


if __name__=='__main__':main()
