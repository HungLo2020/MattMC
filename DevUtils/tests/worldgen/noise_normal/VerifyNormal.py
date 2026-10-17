#!/usr/bin/env python3
"""Actual Normal/Perlin contracts, public corpus and explicit old-tail negative control."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'chunk_generation'))
import VerifyChunkGeneration as probe


def launch(command,log_path,allowed_exit):
    mappings={}
    with log_path.open('w')as log:
        process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
        try:
            deadline=time.monotonic()+300
            while process.poll()is None:
                try:
                    for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                        if'mattmc'in line and'.so'in line:
                            path=Path(line.split()[-1])
                            if path.is_file()and str(path)not in mappings:mappings[str(path)]=probe.full.sha(path)
                except(FileNotFoundError,ProcessLookupError):pass
                if time.monotonic()>deadline:raise TimeoutError('Normal probe timeout')
                time.sleep(.05)
        finally:
            if process.poll()is None:process.kill();process.wait()
    if process.returncode not in allowed_exit:raise RuntimeError(f'exit {process.returncode}: {log_path}')
    return{'exit':process.returncode,'native_mappings':mappings}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--pre-runtime',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();root=probe.ROOT;out=args.output.resolve();pre=args.pre_runtime.resolve()
    if not out.is_relative_to(root/'build'):raise ValueError('output must be below build/')
    out.mkdir(parents=True,exist_ok=False);reference=probe.verify_pre(pre);before=probe.end.inputs(root);frozen=probe.end.inputs(probe.FROZEN)
    if frozen['head']!=probe.PIN:raise ValueError('original Frozen pin required')
    test=root/'src/test/java/net/minecraft/world/level/levelgen/synth';sources=[test/'PerlinBinaryFrequencyContractTest.java',test/'NormalConstructorContractTest.java',test/'CompositeConstructorContractTest.java',test/'NoiseConstructorContractTest.java',HERE/'NormalMutableTailProbe.java',HERE.parent/'noise_composite/CompositeConstructorProbe.java',HERE.parent/'chunk_generation/GenerationContractRunner.java'];hashes={str(p.relative_to(root)):probe.full.sha(p)for p in sources}
    jars=[x for x in(root/'build/chunk-generation/s25-test-classpath.txt').read_text().strip().split(os.pathsep)if Path(x).is_file()];sides={'frozen':((root/'build/chunk-generation/s28-frozen-main-classpath.txt').read_text().strip(),root/'build/chunk-generation/s25-pre522-runtime/native'),'pre':((pre/'classpath.txt').read_text().strip(),pre/'native'),'current':((root/'build/chunk-generation/s28-current-main-classpath.txt').read_text().strip(),root/'build/rust/native')};rows=[]
    for side,(runtime,native)in sides.items():
        folder=out/side;classes=folder/'classes';classes.mkdir(parents=True);cp=os.pathsep.join([str(classes),runtime,*jars])
        with(folder/'compile.log').open('w')as log:subprocess.run([str(probe.full.JDK/'javac'),'-cp',cp,'-d',str(classes),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True)
        jobs=[('contracts',['GenerationContractRunner','net.minecraft.world.level.levelgen.synth.PerlinBinaryFrequencyContractTest','net.minecraft.world.level.levelgen.synth.NormalConstructorContractTest','net.minecraft.world.level.levelgen.synth.CompositeConstructorContractTest','net.minecraft.world.level.levelgen.synth.NoiseConstructorContractTest']),('corpus',['net.minecraft.world.level.levelgen.synth.CompositeConstructorProbe']),('tail',['net.minecraft.world.level.levelgen.synth.NormalMutableTailProbe'])]
        for name,tail in jobs:
            command=['taskset','-c','4-7',str(probe.full.JDK/'java'),'-Xms512m','-Xmx2g','-Xbatch','--enable-native-access=ALL-UNNAMED','-Dnet.bytebuddy.experimental=true','-Dmattmc.rust.natives.dir='+str(native),'-Xlog:class+load=info:file='+str(folder/(name+'-class-load.log')),'-cp',cp,*tail];(folder/(name+'-command.json')).write_text(json.dumps(command,indent=2)+'\n');known=side=='pre'and name=='contracts';row=launch(command,folder/(name+'.log'),{1}if known else{0});text=(folder/(name+'.log')).read_text()
            if side!='frozen'and probe.full.sha(native/'mattmc_rust-linux-x64.so')not in row['native_mappings'].values():raise RuntimeError('actual native mapping missing')
            if name=='contracts':
                totals={k:int(re.search(r'\[\s*(\d+) tests '+k+r'\s*\]',text).group(1))for k in ['found','successful','failed','skipped','aborted']};assert totals=={'found':29,'successful':28 if known else 29,'failed':1 if known else 0,'skipped':0,'aborted':0},totals
                assert'[         0 containers failed'in text
                if known:assert'actual second-child tail stayed stale'in text and'trailingAmplitudeChangeAfterAsymmetricConstructionUsesBothActualChildren'in text
                row['tests']=totals;row['known_preexisting_tail_failure']=known
            elif name=='corpus':row['corpus']=next(x.split('COMPOSITE_CONSTRUCTOR_CORPUS ',1)[1]for x in text.splitlines()if'COMPOSITE_CONSTRUCTOR_CORPUS 'in x)
            else:
                value=next(json.loads(x.split('NORMAL_MUTABLE_TAIL ',1)[1])for x in text.splitlines()if'NORMAL_MUTABLE_TAIL 'in x);assert value['before_bits']==4599426361975877692;assert value['after_bits']==(4599426361975877692 if side=='pre'else 4599037845078277601);assert value['size']==3;row['tail']=value
            row.update(side=side,job=name);rows.append(row);(out/'rows.json').write_text(json.dumps(rows,indent=2)+'\n');print(side,name,'EXPECTED_OLD_TAIL_FAILURE'if known else'PASS',row.get('corpus',''),flush=True)
    corpora=[r['corpus']for r in rows if r['job']=='corpus'];assert all(x==corpora[0]for x in corpora);assert before==probe.end.inputs(root);assert frozen==probe.end.inputs(probe.FROZEN);probe.verify_pre(pre);assert hashes=={str(p.relative_to(root)):probe.full.sha(p)for p in sources}
    (out/'result.json').write_text(json.dumps({'passed':True,'before':before,'after':probe.end.inputs(root),'frozen':frozen,'pre_head':reference['source_head'],'sources':hashes,'rows':rows,'scope':'Frozen/current29contractsPASS; pre28PASS+exact1knownstale-tailFAIL retained; ordinary50368corpus exactall3 and actualtailbefore/after matchFrozen only oncorrectedCurrent. No pre-parity or performance claim.'},indent=2)+'\n')


if __name__=='__main__':main()
