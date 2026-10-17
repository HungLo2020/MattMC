#!/usr/bin/env python3
"""Compare actual untouched Frozen client chunk lookups with retained Rust range pages."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
FROZEN=ROOT.parent/'MattMC_JavaPerfTesting/MattMC'
def sha(p):
 with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def sources(root):
 names=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z','--','src','DevUtils','build.gradle'],cwd=root).split(b'\0')
 return {os.fsdecode(n):sha(root/os.fsdecode(n)) for n in names if n and (root/os.fsdecode(n)).is_file()}
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME'));parser.add_argument('--output',type=Path,default=ROOT/'build/native-client-ranges/replay');a=parser.parse_args();assert a.java_home,'JDK25 required'
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);jdk=Path(a.java_home)/'bin';native=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
 pins={'current':sources(ROOT),'frozen':sources(FROZEN),'native':sha(native),'prompt':sha(ROOT/'docs/STANDARD-COPILOT-PROMPTS.md'),'frozen_head':subprocess.check_output(['git','-C',str(FROZEN),'rev-parse','HEAD'],text=True).strip()}
 assert pins['frozen_head']=='7a4d181717dc0c7da9086f1a17687dfd522ce417';assert pins['prompt']=='c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5'
 cp={};init=out/'classpath.gradle'
 init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('rangePrintRuntimeClasspath') { doLast { println('RANGE_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
 env=os.environ.copy();env['JAVA_HOME']=str(Path(a.java_home));env['PATH']=str(jdk)+os.pathsep+env.get('PATH','')
 for side,repo in [('current',ROOT),('frozen',FROZEN)]:
  command=[str(repo/'gradlew'),'-q','-I',str(init),'rangePrintRuntimeClasspath']
  data=subprocess.check_output(command,cwd=repo,env=env,text=True);cp[side]=next(line.removeprefix('RANGE_CP ') for line in data.splitlines() if line.startswith('RANGE_CP '))
  (out/(side+'-classpath.txt')).write_text(cp[side]+'\n')
 classes=out/'oracle';classes.mkdir(exist_ok=True)
 result={'pins':pins,'passed':False,'guards_pass':False,'scope':'Actual Frozen getChunk rectangular traversal;645 complete range/callback cases, stock dense/sparse/stale slots, replacements/drops, center/radius changes, nested readers and callback exceptions. Supplemental to real gameplay/performance.'};(out/'results.json').write_text(json.dumps(result,indent=2)+'\n')
 def run(command,name):
  with (out/name).open('w') as log:status=subprocess.call(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
  if status:raise RuntimeError(f'{name} exited{status}')
  return (out/name).read_text()
 try:
  for side in ['frozen','current']:
   target=classes/side;target.mkdir(exist_ok=True)
   run([str(jdk/'javac'),'-cp',cp[side],'-d',str(target),str(ROOT/'DevUtils/tests/world/chunk/VerifyClientChunkRanges.java')],'compile-'+side+'-driver.log')
  result['driver_classes']={str(p.relative_to(classes)):sha(p) for p in classes.rglob('*.class')}
  fixture=out/'frozen-ranges.bin.gz'
  f=run([str(jdk/'java'),'-cp',str(classes/'frozen')+os.pathsep+cp['frozen'],'VerifyClientChunkRanges','frozen',str(fixture)],'record-frozen.log');assert 'RANGE_CASES 645' in f
  c=run([str(jdk/'java'),'--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(native.parent),'-cp',str(classes/'current')+os.pathsep+cp['current'],'VerifyClientChunkRanges','current',str(fixture)],'replay-native.log');assert 'RANGE_CASES 645' in c
  image_checks={}
  for side,data,repo in [('current',c,ROOT),('frozen',f,FROZEN)]:
   checked={}
   for line in data.splitlines():
    if '[STDOUT]: ' in line:line=line.split('[STDOUT]: ',1)[1]
    if not line.startswith('CLASS '):continue
    _,name,digest=line.split();resource=name.replace('.','/')+'.class'
    path=next(Path(base)/resource for base in cp[side].split(os.pathsep) if (Path(base)/resource).is_file())
    assert path.is_relative_to(repo) and sha(path)==digest,(side,name,path)
    checked[str(path)]=digest
   assert len(checked)==4;image_checks[side]=checked
  result['class_images']=image_checks
  assert 'NATIVE_IMAGE '+pins['native']+' '+str(native) in c
  result['fixture_sha256']=sha(fixture);result['passed']=True
 finally:
  if 'class_images' in result: assert all(sha(Path(p))==h for images in result['class_images'].values() for p,h in images.items())
  result['guards_pass']=sources(ROOT)==pins['current'] and sources(FROZEN)==pins['frozen'] and sha(native)==pins['native'] and sha(ROOT/'docs/STANDARD-COPILOT-PROMPTS.md')==pins['prompt']
  if 'driver_classes' in result:result['guards_pass']=result['guards_pass'] and result['driver_classes']=={str(p.relative_to(classes)):sha(p) for p in classes.rglob('*.class')}
  (out/'results.json').write_text(json.dumps(result,indent=2)+'\n')
 assert result['passed'] and result['guards_pass'];print('Actual Frozen range replay645 PASS;source/native/driver guards PASS')
if __name__=='__main__':main()
