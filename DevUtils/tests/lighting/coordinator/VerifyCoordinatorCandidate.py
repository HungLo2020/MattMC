#!/usr/bin/env python3
"""Run a changed candidate against sealed, unchanged Frozen/pre observations.

The reference's exact observer source is compiled against the current classes.
This reuses observations, not a claim that an old run executed new code.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'threaded'))
from VerifyThreadedLighting import ROOT,FROZEN,FROZEN_HEAD,images,sha,source_identity


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference',type=Path,required=True)
    parser.add_argument('--current-classpath-file',type=Path,required=True)
    parser.add_argument('--native-build-manifest',type=Path,required=True)
    parser.add_argument('--java-build-manifest',type=Path,required=True)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--kind',choices=('collections','engine','threaded'),required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    reference=args.reference.resolve();old=json.loads((reference/'result.json').read_text())
    assert old['guards_pass'] and old['frozen']==old['control'], 'Unsealed or differing reference sides'
    assert old['frozen_head']==FROZEN_HEAD
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=FROZEN,text=True).strip()==FROZEN_HEAD
    assert not subprocess.check_output(['git','status','--porcelain'],cwd=FROZEN)
    assert all(sha(p)==v for p,v in old['class_images'].items()), 'Reference compiled input changed'
    native=json.loads(args.native_build_manifest.read_text());java=json.loads(args.java_build_manifest.read_text())
    assert native['passed'] and native['source_guard'] and java['exit']==0
    assert all(sha(ROOT/p)==v for p,v in native['source_images'].items()), 'Candidate native/source pair changed'
    assert all(sha(ROOT/p)==v for p,v in java['sources'].items()), 'Candidate Java/source pair changed'
    library=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
    assert sha(library)==native['native_sha256']==sha(ROOT/'build/rust/target/release/libmattmc_rust.so')
    name={'collections':'VerifyCoordinatorCollections','engine':'VerifyLightCoordinator','threaded':'VerifyThreadedLighting'}[args.kind]
    repo_source=(HERE if args.kind!='threaded' else HERE.parent/'threaded')/(name+'.java')
    source=reference/'driver-source'/(name+'.java')
    if not source.is_file():source=repo_source
    source_key=str(repo_source.relative_to(ROOT));assert sha(source)==old['driver_sha256'][source_key], 'Observer differs from sealed reference'
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=False);classes=out/'driver-classes';classes.mkdir()
    archive=out/(name+'.java');archive.write_bytes(source.read_bytes())
    cp=args.current_classpath_file.read_text().strip()
    # These are unchanged driver helpers only, never old game-class overlays.
    helper=reference/'driver-current';assert helper.is_dir()
    assert all('/net/minecraft/server/level/' in str(p) or '/net/minecraft/world/level/lighting/' in str(p) for p in helper.rglob('*.class'))
    classpath=str(classes)+os.pathsep+str(helper)+os.pathsep+cp
    env=os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS'):env.pop(key,None)
    result={'passed':False,'guards_pass':False,'kind':args.kind,'audit':old.get('audit'),'scope':'Candidate execution using exact sealed observer/reference observations; no baseline regeneration or cost/server claim','candidate_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'source_before':source_identity(),'reference_result_sha256':sha(reference/'result.json'),'reference':str(reference),'frozen_head':FROZEN_HEAD,'control_ref':old['control_ref'],'expected':old['frozen'],'observer_sha256':sha(source),'native_sha256':sha(library),'native_build_manifest_sha256':sha(args.native_build_manifest),'java_build_manifest_sha256':sha(args.java_build_manifest),'commands':{}}
    def run(command,log):
        result['commands'][log]=command
        with (out/log).open('w') as f:code=subprocess.call(command,cwd=ROOT,env=env,stdout=f,stderr=subprocess.STDOUT)
        assert code==0,(log,code)
    try:
        run([str(args.java_home/'bin/javac'),'-g','-nowarn','-cp',classpath,'-d',str(classes),str(archive)],'compile.log')
        before=images({'current':classpath});result['class_images']=before
        package='net.minecraft.server.level.' if args.kind=='threaded' else 'net.minecraft.world.level.lighting.'
        trace=out/'trace.txt';command=[str(args.java_home/'bin/java'),'-Xms512m','-Xmx2g','--enable-native-access=ALL-UNNAMED','-DthreadedLight.frozen=false','-Dmattmc.rust.natives.dir='+str(library.parent),'-cp',classpath,package+name,str(trace)]
        if args.kind=='collections' and old.get('audit')!='collections':command.append(old['audit'])
        run(command,'run.log')
        rows=len(trace.read_text().splitlines());digest=sha(trace)
        expected=old['frozen'];expected_digest=expected.get('sha256',expected.get('trace_sha256'));expected_rows=expected.get('rows',expected.get('events'))
        result.update(current={'rows':rows,'sha256':digest},passed=digest==expected_digest and rows==expected_rows)
        if args.kind=='engine':
            log=(out/'run.log').read_text();result['normal_native_composition']='NATIVE_COMPOSITION mode=normal bindings=2 independent-work/context=true owners-after=0' in log
            result['native_policy_routes']=[line for line in log.splitlines() if 'NATIVE_POLICY ' in line];result['passed'] &= result['normal_native_composition'] and len(result['native_policy_routes'])>=3
        if not result['passed']:
            baseline=reference/'frozen-trace.txt'
            if baseline.is_file():result['first_differences']=[{'line':i+1,'frozen':a,'current':b} for i,(a,b) in enumerate(zip(baseline.read_text().splitlines(),trace.read_text().splitlines())) if a!=b][:25]
    finally:
        result['guards_pass']=(source_identity()==result['source_before'] and sha(library)==result['native_sha256'] and all(sha(p)==v for p,v in result.get('class_images',{}).items()) and sha(reference/'result.json')==result['reference_result_sha256'] and sha(source)==result['observer_sha256'])
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:result.get(k) for k in ('passed','guards_pass','current','first_differences')}))
    assert result['passed'] and result['guards_pass'],str(out/'result.json')


if __name__=='__main__':main()
