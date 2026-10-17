#!/usr/bin/env python3
"""Bounded real Gaussian/cache/source creation groups and physical owner retirement screen."""
import argparse
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'noise_composite'))
import VerifyComposite as portable


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-runtime',type=Path,required=True)
    parser.add_argument('--current-runtime',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--smoke',action='store_true',help='small functional check; not a performance result')
    parser.add_argument('--kind',type=int,choices=range(6),help='one row only: 0 XoroGaussian32+word, 1 LegacyGaussian32+word, 2 standaloneXoroGaussian32+word, 3 preparedcached+word, 4 createXoro+Gaussian3+word, 5 createLegacy+Gaussian3+word')
    args=parser.parse_args()
    probe=portable.probe;root=probe.ROOT;pre=args.pre_runtime.resolve();current=args.current_runtime.resolve();out=args.output.resolve()
    if not out.is_relative_to(root/'build'):raise ValueError('output must be below build/')
    out.mkdir(parents=True,exist_ok=False);reference=probe.verify_pre(pre);current_ref=probe.verify_pre(current);before=probe.end.inputs(root);frozen=probe.end.inputs(probe.FROZEN)
    if frozen['head']!=probe.PIN:raise ValueError('original Frozen pin required')
    source=Path(__file__).with_name('GaussianScreen.java');source_hash=probe.full.sha(source);driver_hash=probe.full.sha(Path(__file__))
    sides={'frozen':((root/'build/chunk-generation/s28-frozen-main-classpath.txt').read_text().strip(),root/'build/chunk-generation/s25-pre522-runtime/native'),'pre':((pre/'classpath.txt').read_text().strip(),pre/'native'),'current':((current/'classpath.txt').read_text().strip(),current/'native')};rows={}
    for side,(runtime,native) in sides.items():
        folder=out/side;classes=folder/'classes';classes.mkdir(parents=True);cp=str(classes)+os.pathsep+runtime
        with(folder/'compile.log').open('w')as log:subprocess.run([str(probe.full.JDK/'javac'),'-cp',cp,'-d',str(classes),str(source)],stdout=log,stderr=subprocess.STDOUT,check=True)
        cmd=['taskset','-c','4',str(probe.full.JDK/'java'),'-Xms1g','-Xmx4g','-Xbatch','--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(native),'-cp',cp,'net.minecraft.world.level.levelgen.GaussianScreen']
        if args.smoke:cmd.append('--smoke')
        if args.kind is not None:cmd.append('--kind='+str(args.kind))
        (folder/'command.json').write_text(json.dumps(cmd,indent=2)+'\n')
        row=portable.launch(cmd,folder/'screen.log');lines=(folder/'screen.log').read_text().splitlines()
        row['samples']=[json.loads(line.split('PRODUCER_SCREEN ',1)[1])for line in lines if 'PRODUCER_SCREEN ' in line]
        row['retirement']=[line.split('PRODUCER_RETIREMENT ',1)[1]for line in lines if 'PRODUCER_RETIREMENT ' in line]
        if side=='current' and probe.full.sha(native/'mattmc_rust-linux-x64.so')not in row['native_mappings'].values():raise RuntimeError('actual native mapping missing')
        rows[side]=row;(out/'rows.json').write_text(json.dumps(rows,indent=2)+'\n');print(side,'screen completed',flush=True)
    summary=[]
    for kind in (range(6) if args.kind is None else [args.kind]):
        selected={side:[s for s in row['samples']if s['kind']==kind and s['accepted']]for side,row in rows.items()}
        if any(len(s)!=(1 if args.smoke else 6) for s in selected.values()):raise RuntimeError('insufficient quiet samples')
        outputs={s['sum']for samples in selected.values()for s in samples}
        if len(outputs)!=1:raise RuntimeError('actual output/RNG work differs')
        medians={side:{'ns_per_call':statistics.median(s['elapsed_ns']/s['count']for s in samples),'min_ns_per_call':min(s['elapsed_ns']/s['count']for s in samples),'max_ns_per_call':max(s['elapsed_ns']/s['count']for s in samples),'java_bytes_per_call':statistics.median(s['java_bytes']/s['count']for s in samples)}for side,samples in selected.items()}
        summary.append({'kind':kind,'sum':outputs.pop(),'medians':medians,'current_pre_ratio':medians['current']['ns_per_call']/medians['pre']['ns_per_call'],'current_frozen_ratio':medians['current']['ns_per_call']/medians['frozen']['ns_per_call']})
    assert before==probe.end.inputs(root),'Current runtime changed';assert frozen==probe.end.inputs(probe.FROZEN),'Frozen changed';probe.verify_pre(pre);probe.verify_pre(current);assert source_hash==probe.full.sha(source)and driver_hash==probe.full.sha(Path(__file__)),'observer changed'
    (out/'screen-summary.json').write_text(json.dumps({'rows':summary,'resources':{side:{k:row[k]for k in ['sampled_peak_rss_bytes','max_swap_bytes']}for side,row in rows.items()},'scope':'intermediate actual Gaussian groups; ns per group (0:XoroGaussian32+word;1:LegacyGaussian32+word;2:standaloneXoroGaussian32+word;3:preparedcached+word;4:createXoro+Gaussian3+word;5:createLegacy+Gaussian3+word). Caller arrays prepared outside timer; rows4/5 include source construction, row2/Legacy include actual lazy native cache materialization. Real owner/frame creation+retirement and output identity; no finalbudget/nativeallocator/FPS claim'},indent=2)+'\n')
    (out/'result.json').write_text(json.dumps({'passed':True,'before':before,'after':probe.end.inputs(root),'frozen':frozen,'pre_head':reference['source_head'],'sealed_current_manifest_sha256':probe.full.sha(current/'manifest.json'),'sealed_current_native':current_ref['native'],'observer_sha256':source_hash,'driver_sha256':driver_hash,'rows':rows,'performance_budget_pass':False,'smoke_only':args.smoke,'selected_kind':args.kind},indent=2)+'\n')


if __name__=='__main__':main()
