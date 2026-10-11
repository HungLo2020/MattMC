#!/usr/bin/env python3
"""Record actual untouched Frozen traversal, replay retained Rust topology."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
FROZEN=ROOT.parent/'MattMC_JavaPerfTesting/MattMC'
def sha(p):
 with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def sources(root):
 names=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z','--','src','DevUtils','build.gradle'],cwd=root).split(b'\0')
 return {os.fsdecode(n):sha(root/os.fsdecode(n)) for n in names if n and (root/os.fsdecode(n)).is_file()}
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME'));parser.add_argument('--output',type=Path,default=ROOT/'build/native-dh-visibility/replay');a=parser.parse_args();assert a.java_home,'JDK25 required'
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);jdk=Path(a.java_home)/'bin';native=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
 pins={'current':sources(ROOT),'frozen':sources(FROZEN),'native':sha(native),'prompt':sha(ROOT/'docs/STANDARD-COPILOT-PROMPTS.md'),'frozen_head':subprocess.check_output(['git','-C',str(FROZEN),'rev-parse','HEAD'],text=True).strip()}
 assert pins['frozen_head']=='7a4d181717dc0c7da9086f1a17687dfd522ce417';assert pins['prompt']=='c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5'
 cp={};init=out/'classpath.gradle'
 init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('visibilityPrintRuntimeClasspath') { doLast { println('VISIBILITY_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
 env=os.environ.copy();env['JAVA_HOME']=str(Path(a.java_home));env['PATH']=str(jdk)+os.pathsep+env.get('PATH','')
 for side,repo in [('current',ROOT),('frozen',FROZEN)]:
  command=[str(repo/'gradlew'),'-q','-I',str(init),'visibilityPrintRuntimeClasspath']
  data=subprocess.check_output(command,cwd=repo,env=env,text=True);cp[side]=next(line.removeprefix('VISIBILITY_CP ') for line in data.splitlines() if line.startswith('VISIBILITY_CP '))
  (out/(side+'-classpath.txt')).write_text(cp[side]+'\n')
 classes=out/'oracle';classes.mkdir(exist_ok=True)
 result={'pins':pins,'passed':False,'guards_pass':False,'scope':'Actual Frozen QuadTree/QuadTreeNodeIterator/DhFrustumBounds;1280 complete walks with incremental mutation/recenter/retained-old-node/deletion. Supplemental to real gameplay and performance.'};(out/'results.json').write_text(json.dumps(result,indent=2)+'\n')
 def run(command,name):
  with (out/name).open('w') as log:status=subprocess.call(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
  if status:raise RuntimeError(f'{name} exited{status}')
  return (out/name).read_text()
 try:
  run([str(jdk/'javac'),'-cp',cp['frozen'],'-d',str(classes),str(ROOT/'DevUtils/tests/rendering/VerifyDhVisibility.java')],'compile-frozen-driver.log')
  run([str(jdk/'javac'),'-cp',str(classes)+os.pathsep+cp['current'],'-d',str(classes),str(ROOT/'DevUtils/tests/rendering/VerifyNativeDhVisibility.java')],'compile-native-driver.log')
  result['driver_classes']={str(p.relative_to(classes)):sha(p) for p in classes.rglob('*.class')}
  fixture=out/'frozen-visibility.bin.gz'
  f=run([str(jdk/'java'),'-cp',str(classes)+os.pathsep+cp['frozen'],'VerifyDhVisibility',str(fixture)],'record-frozen.log');assert 'FROZEN_FRAMES 1280' in f
  c=run([str(jdk/'java'),'--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(native.parent),'-cp',str(classes)+os.pathsep+cp['current'],'VerifyNativeDhVisibility',str(fixture)],'replay-native.log');assert 'NATIVE_FRAMES 1280 PASS' in c
  image_checks={}
  for side,data,repo in [('current',c,ROOT),('frozen',f,FROZEN)]:
   checked={}
   for line in data.splitlines():
    if not line.startswith('CLASS '):continue
    _,name,digest=line.split();resource=name.replace('.','/')+'.class'
    path=next(Path(base)/resource for base in cp[side].split(os.pathsep) if (Path(base)/resource).is_file())
    assert path.is_relative_to(repo) and sha(path)==digest,(side,name,path)
    checked[str(path)]=digest
   assert len(checked)==5;image_checks[side]=checked
  result['class_images']=image_checks
  assert 'NATIVE_IMAGE '+pins['native']+' '+str(native) in c
  result['fixture_sha256']=sha(fixture);result['passed']=True
 finally:
  if 'class_images' in result: assert all(sha(Path(p))==h for images in result['class_images'].values() for p,h in images.items())
  result['guards_pass']=sources(ROOT)==pins['current'] and sources(FROZEN)==pins['frozen'] and sha(native)==pins['native'] and sha(ROOT/'docs/STANDARD-COPILOT-PROMPTS.md')==pins['prompt']
  if 'driver_classes' in result:result['guards_pass']=result['guards_pass'] and result['driver_classes']=={str(p.relative_to(classes)):sha(p) for p in classes.rglob('*.class')}
  (out/'results.json').write_text(json.dumps(result,indent=2)+'\n')
 assert result['passed'] and result['guards_pass'];print('Actual Frozen traversal replay1280 PASS;source/native/driver guards PASS')
if __name__=='__main__':main()
