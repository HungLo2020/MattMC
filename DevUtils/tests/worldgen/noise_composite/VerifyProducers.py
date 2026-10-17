#!/usr/bin/env python3
"""Compare real public noise producers against fixed complete pre and original Frozen."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import VerifyComposite as portable


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-runtime',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();probe=portable.probe;root=probe.ROOT;pre=args.pre_runtime.resolve();out=args.output.resolve()
    if not out.is_relative_to(root/'build'):raise ValueError('output must be below build/')
    out.mkdir(parents=True,exist_ok=False);reference=probe.verify_pre(pre);before=probe.end.inputs(root);frozen=probe.end.inputs(probe.FROZEN)
    if frozen['head']!=probe.PIN:raise ValueError('original Frozen pin required')
    names=['BlendedConstructorContractTest','SimplexCompositeConstructorContractTest','CheckedNoiseContractTest','CompositeConstructorContractTest','NormalConstructorContractTest','NoiseConstructorContractTest','PerlinBinaryFrequencyContractTest']
    sources=[root/'src/test/java/net/minecraft/world/level/levelgen/synth'/(name+'.java')for name in names]+[Path(__file__).with_name('CompositeConstructorProbe.java'),root/'DevUtils/tests/worldgen/noise_normal/NormalMutableTailProbe.java',root/'DevUtils/tests/worldgen/chunk_generation/GenerationContractRunner.java']
    hashes={str(p.relative_to(root)):probe.full.sha(p)for p in [*sources,Path(__file__),Path(portable.__file__)]}
    jars=[x for x in(root/'build/chunk-generation/s25-test-classpath.txt').read_text().strip().split(os.pathsep)if Path(x).is_file()]
    sides={'frozen':((root/'build/chunk-generation/s28-frozen-main-classpath.txt').read_text().strip(),root/'build/chunk-generation/s25-pre522-runtime/native'),'pre':((pre/'classpath.txt').read_text().strip(),pre/'native'),'current':((root/'build/chunk-generation/s28-current-main-classpath.txt').read_text().strip(),root/'build/rust/native')};rows=[]
    for side,(runtime,native)in sides.items():
        folder=out/side;classes=folder/'classes';classes.mkdir(parents=True);cp=os.pathsep.join([str(classes),runtime,*jars])
        with(folder/'compile.log').open('w')as log:subprocess.run([str(probe.full.JDK/'javac'),'-cp',cp,'-d',str(classes),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True)
        jobs=[('contracts',['GenerationContractRunner',*['net.minecraft.world.level.levelgen.synth.'+name for name in names]]),('corpus',['net.minecraft.world.level.levelgen.synth.CompositeConstructorProbe']),('tail',['net.minecraft.world.level.levelgen.synth.NormalMutableTailProbe'])]
        for name,tail in jobs:
            cmd=['taskset','-c','4-7',str(probe.full.JDK/'java'),'-Xms512m','-Xmx2g','-Xbatch','-Dnet.bytebuddy.experimental=true','--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(native),'-Xlog:class+load=info:file='+str(folder/(name+'-class-load.log')),'-cp',cp,*tail]
            (folder/(name+'-command.json')).write_text(json.dumps(cmd,indent=2)+'\n');row=portable.launch(cmd,folder/(name+'.log'));text=(folder/(name+'.log')).read_text()
            if side!='frozen'and probe.full.sha(native/'mattmc_rust-linux-x64.so')not in row['native_mappings'].values():raise RuntimeError('actual native mapping missing')
            if name=='contracts':
                import re
                counts={k:int(re.search(r'\[\s*(\d+) tests '+k+r'\s*\]',text).group(1))for k in ['found','successful','failed','skipped','aborted']}
                if counts!={'found':46,'successful':46,'failed':0,'skipped':0,'aborted':0}:raise RuntimeError(counts)
                row['tests']=counts
            elif name=='corpus':row['corpus']=next(line.split('COMPOSITE_CONSTRUCTOR_CORPUS ',1)[1]for line in text.splitlines()if'COMPOSITE_CONSTRUCTOR_CORPUS 'in line)
            else:
                value=next(json.loads(line.split('NORMAL_MUTABLE_TAIL ',1)[1])for line in text.splitlines()if'NORMAL_MUTABLE_TAIL 'in line)
                if(value['before_bits'],value['after_bits'],value['size'])!=(4599426361975877692,4599037845078277601,3):raise RuntimeError(value)
                row['tail']=value
            row.update(side=side,job=name);rows.append(row);(out/'rows.json').write_text(json.dumps(rows,indent=2)+'\n');print(side,name,'PASS',row.get('corpus',''),flush=True)
    if len({r['corpus']for r in rows if r['job']=='corpus'})!=1:raise RuntimeError('public output/RNG corpus differs')
    assert before==probe.end.inputs(root);assert frozen==probe.end.inputs(probe.FROZEN);probe.verify_pre(pre)
    assert hashes=={str(p.relative_to(root)):probe.full.sha(p)for p in [*sources,Path(__file__),Path(portable.__file__)]}
    (out/'result.json').write_text(json.dumps({'passed':True,'before':before,'after':probe.end.inputs(root),'frozen':frozen,'pre_head':reference['source_head'],'sources':hashes,'rows':rows,'scope':'actual46public producer/checkedThrowable contracts+ordinary50368corpus+fixedNormalmutabletail, all3completecoherent runtimes; no performance orwholeWGcompletionclaim'},indent=2)+'\n')


if __name__=='__main__':main()
