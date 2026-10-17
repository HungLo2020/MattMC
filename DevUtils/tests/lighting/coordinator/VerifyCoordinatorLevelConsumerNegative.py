#!/usr/bin/env python3
"""Restore only the old propagation caller in an isolated actual class image."""
import argparse,json,os,subprocess,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'threaded'))
from VerifyThreadedLighting import ROOT,sha,images,source_identity
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--positive-result',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
positive=json.loads(a.positive_result.read_text());assert positive['passed'] and positive['guards_pass'];name='VerifyCoordinatorNativeLevelConsumers';assert positive['runs'][name]['exit']==0
original=positive['commands'][name];cp=original[original.index('-cp')+1];relative=Path('net/minecraft/server/level/ThreadedLevelLightEngine.class');selected=next(Path(part)/relative for part in cp.split(os.pathsep) if (Path(part)/relative).is_file());native=ROOT/'build/rust/native/mattmc_rust-linux-x64.so';assert sha(native)==positive['native_sha256'];out=a.output.resolve();out.mkdir(parents=True,exist_ok=False);source=out/'MutateLevelConsumer.java';source.write_bytes((HERE/source.name).read_bytes());result={'scope':'Actual unchanged current-only admission observer; artifact-only restoration of previous Java caller; no production or baseline mutation','source_before':source_identity(),'native_sha256':sha(native),'positive_sha256':sha(a.positive_result),'original_class_sha256':sha(selected),'mutator_sha256':sha(source),'class_images':images({'positive':cp}),'commands':{}}
env=os.environ.copy()
for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
def run(command,name):
 result['commands'][name]=command
 with (out/name).open('w') as log:r=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,env=env)
 return r.returncode
try:
 assert run([str(a.java_home/'bin/javac'),'-cp',cp,'-d',str(out/'mutator'),str(source)],'compile.log')==0
 target=out/'overlay'/relative;assert run([str(a.java_home/'bin/java'),'-cp',str(out/'mutator')+':'+cp,'MutateLevelConsumer',str(selected),str(target)],'mutate.log')==0;result['mutated_class_sha256']=sha(target);assert sha(target)!=sha(selected)
 cmd=original.copy();cmd[cmd.index('-cp')+1]=str(out/'overlay')+':'+cp;code=run(cmd,'negative.log');log=(out/'negative.log').read_text();invalid=any(s in log for s in ['VerifyError','LinkageError','NoClassDefFoundError','WrongMethodTypeException','UnsatisfiedLinkError']);result.update(exit=code,invalid_runtime_failure=invalid,killed=code!=0 and not invalid and 'java.lang.AssertionError: Old Java level orchestration was invoked' in log)
finally:
 result['guards_pass']=source_identity()==result['source_before'] and sha(native)==result['native_sha256'] and sha(selected)==result['original_class_sha256'] and sha(source)==result['mutator_sha256'] and sha(a.positive_result)==result['positive_sha256'] and all(sha(p)==h for p,h in result['class_images'].items());result['passed']=result['guards_pass'] and result.get('killed',False);(out/'result.json').write_text(json.dumps(result,indent=2))
print({k:result.get(k) for k in ['passed','guards_pass','killed','exit','invalid_runtime_failure']});assert result['passed']
