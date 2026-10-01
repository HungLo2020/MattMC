#!/usr/bin/env python3
"""Run an isolated full-world surface comparison or timing fork.

Prepare the release build and original Java reference with VerifyRustSurface.py.
Use --reference for original Java; omit for production Rust. --hash records both
surface-stage and FULL hashes. --serial controls request order for parity only.
Without --hash/--serial, timings measure normal parallel chunk generation.
Every run uses a new world under build/, an ephemeral loopback port, and stops
its server. No user worlds or production configuration are touched.
"""
from pathlib import Path
import argparse,subprocess,json,time,os,hashlib,re
ROOT=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('label');p.add_argument('--seed',type=int,default=42);p.add_argument('--dimension',default='');p.add_argument('--radius',type=int,default=5);p.add_argument('--rounds',type=int,default=3);p.add_argument('--warmups',type=int,default=2);p.add_argument('--warm-radius',type=int,default=3);p.add_argument('--workers',type=int,default=3);p.add_argument('--cpus',default='2-5');p.add_argument('--reference',action='store_true');p.add_argument('--hash',action='store_true');p.add_argument('--serial',action='store_true');p.add_argument('--compare',help='Compare hashes with an earlier label in the output directory');p.add_argument('--output',default='build/rust-surface-world');p.add_argument('--classpath',default='build/rust-surface-verification/classpath.txt');p.add_argument("--native-dir", default="build/rust/native", help="Frozen native library directory for before/after comparisons");args=p.parse_args()
if not re.fullmatch(r'[A-Za-z0-9_-]+',args.label) or (args.compare and not re.fullmatch(r'[A-Za-z0-9_-]+',args.compare)):p.error('Use a simple directory name as label')
if min(args.radius,args.rounds,args.warmups,args.warm_radius)<0 or args.workers<1:p.error('Invalid workload size')
if args.compare and not args.hash:p.error('--compare requires --hash')
NATIVE=(ROOT/args.native_dir).resolve()
BASE=(ROOT/args.output).resolve()
if not BASE.is_relative_to(ROOT/'build'):p.error('Output must be under build/')
BASE.mkdir(parents=True,exist_ok=True)
cp_file=(ROOT/args.classpath).resolve()
if not cp_file.exists():p.error('Run VerifyRustSurface.py first to build and prepare the reference/classpath')
reference=cp_file.parent/'reference-classes'
classes=BASE/'agent-classes';classes.mkdir(exist_ok=True)
source=Path(__file__).resolve().with_name('surface_verification')
subprocess.run(['javac','-cp',cp_file.read_text().strip(),'-d',str(classes),*[str(f) for f in sorted(source.glob('*.java'))]],check=True)
manifest=BASE/'MANIFEST.MF';manifest.write_text('Premain-Class: surfaceverification.AuditAgent\n\n')
subprocess.run(['jar','cfm',str(BASE/'audit-agent.jar'),str(manifest),'-C',str(classes),'.'],check=True)
out=BASE/args.label
if out.exists():raise SystemExit('Use a fresh output directory')
out.mkdir();world=out/'server';world.mkdir()
(world/'server.properties').write_text(f'''level-seed={args.seed}
level-name=world
server-ip=127.0.0.1
server-port=0
online-mode=false
enforce-secure-profile=false
view-distance=2
simulation-distance=2
max-tick-time=-1
pause-when-empty-seconds=-1
sync-chunk-writes=false
generate-structures=true
spawn-protection=0
''')
cp=str(BASE/'audit-agent.jar')+':'+cp_file.read_text().strip()
if args.reference:cp=str(reference)+':'+cp
cmd=['taskset','-c',args.cpus,'java','-Xms1G','-Xmx8G','-XX:+UseZGC','-XX:+UseCompactObjectHeaders','-XX:+UnlockDiagnosticVMOptions','-XX:+DebugNonSafepoints','-XX:+PreserveFramePointer','--enable-native-access=ALL-UNNAMED',f'-Dmax.bg.threads={args.workers}',f'-Dmattmc.rust.natives.dir={NATIVE}',f'-Daudit.output={out}',f'-Daudit.serial={str(args.serial).lower()}',f'-Daudit.hash={str(args.hash).lower()}',f'-Daudit.radius={args.radius}',f'-Daudit.rounds={args.rounds}',f'-Daudit.warmRadius={args.warm_radius}',f'-Daudit.warmups={args.warmups}',f'-Daudit.dimension={args.dimension}',f'-javaagent:{BASE}/audit-agent.jar','-cp',cp,'net.minecraft.server.Main','--nogui','--port','0']
(out/'command.json').write_text(json.dumps({'args':vars(args),'command':cmd,'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'native_sha256':hashlib.sha256((NATIVE/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest()},indent=2))
start=time.time();peak=0
with (out/'server.log').open('w') as log:
 proc=subprocess.Popen(cmd,cwd=world,stdout=log,stderr=subprocess.STDOUT)
 (out/'pid').write_text(str(proc.pid))
 try:
  while proc.poll() is None:
   try:
    values=Path(f'/proc/{proc.pid}/status').read_text().splitlines()
    peak=max(peak,next(int(x.split()[1])*1024 for x in values if x.startswith('VmRSS:')))
   except (FileNotFoundError,StopIteration):pass
   if time.time()-start>1800:raise TimeoutError('30 minute run limit')
   time.sleep(1)
 finally:
  if proc.poll() is None:
   proc.terminate()
   try:proc.wait(timeout=30)
   except subprocess.TimeoutExpired:proc.kill();proc.wait()
(out/'process.json').write_text(json.dumps({'exit_code':proc.returncode,'elapsed_seconds':time.time()-start,'peak_rss_bytes':peak},indent=2))
print(args.label,'exit',proc.returncode,'seconds',round(time.time()-start,1),'peak_RSS_MiB',round(peak/1048576),flush=True)
if proc.returncode or not (out/'complete.json').exists():raise SystemExit('Run failed; inspect server.log')

if args.hash:
 rows=json.loads((out/'timings.json').read_text())
 measured=[r for r in rows if r['label'].startswith('measure')]
 if not measured:raise SystemExit('No measured chunks; check dimension/round count')
 for row in measured:
  stem=row['dimension'].split(':',1)[1]+'-'+row['label'].split('-')[1]
  for stage in ['surface','carvers']:
   if not json.loads((out/(stem+'-'+stage+'.json')).read_text()):
    raise SystemExit('Missing stage fingerprints: '+stem+'-'+stage)
if args.compare:
 reference_out=BASE/args.compare
 def full(path):
  return {(r['dimension'],r['label']):r['full_fingerprints'] for r in json.loads((path/'timings.json').read_text()) if 'full_fingerprints' in r}
 a,b=full(reference_out),full(out)
 if set(a)!=set(b):raise SystemExit('Different comparison workloads')
 report={'reference':args.compare,'full':{},'stages':{}}
 for key in a:
  aa,bb=a[key],b[key]
  different=[k for k in sorted(set(aa)|set(bb)) if aa.get(k)!=bb.get(k)]
  report['full']['/'.join(key)]={'chunks':len(bb),'different':different}
 for f in sorted(out.glob('*-surface.json'))+sorted(out.glob('*-carvers.json')):
  aa,bb=json.loads((reference_out/f.name).read_text()),json.loads(f.read_text())
  different=[k for k in sorted(set(aa)|set(bb)) if aa.get(k)!=bb.get(k)]
  report['stages'][f.name]={'chunks':len(bb),'different':different}
 report['identical']=all(not r['different'] for section in ['full','stages'] for r in report[section].values())
 (out/'comparison.json').write_text(json.dumps(report,indent=2))
 print('Exact full-world parity:',report['identical'],flush=True)
 if not report['identical']:raise SystemExit('World hashes differ; inspect comparison.json and repeat the Java control')
