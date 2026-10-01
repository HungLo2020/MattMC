#!/usr/bin/env python3
"""Supplement uniform global-palette acceptance using the unchanged full-caller harness.
Run VerifyRustPaletteUnpacking.py first; artifacts go beside its accepted report.
"""
import hashlib,json,random,re,statistics,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'build/palette-unpacking-migration/acceptance'
def require(condition, message):
    if not condition:
        raise RuntimeError(message)
source=OUT/'UniformGlobalCheck.java'
source.write_text('''package net.minecraft.world.level.chunk;

/** Supplemental worst-shape caller check; reflection occurs outside the timed method. */
public final class UniformGlobalCheck {
    public static void main(String[] args) throws Exception {
        NativePaletteUnpackingTest.bootstrap();
        var constructor = NativePaletteUnpackingVerification.class.getDeclaredConstructor(boolean.class, String.class);
        var benchmark = NativePaletteUnpackingVerification.class.getDeclaredMethod("bench", String.class);
        constructor.setAccessible(true);
        benchmark.setAccessible(true);
        for (int count : new int[]{257, 512, 513, 1024}) {
            var name = "stale_" + count;
            var instance = constructor.newInstance(args[0].equals("native"), name);
            benchmark.invoke(instance, name);
        }
    }
}
''')
cp=(OUT/'classpath.txt').read_text().strip()
classes=OUT/'extra-classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-cp',cp,'-d',str(classes),str(source)],check=True)
main=json.loads((OUT/'results.json').read_text())
require(main.get('integrity') and all(v['passes'] for v in main['performance'].values()), 'Run full accepted unpack verification first')
base=['taskset','-c',str(main['cpu']),'java',*main['jvm'],
      '-Dmattmc.rust.natives.dir='+str(ROOT/'build/rust/native'),'-cp',str(classes)+':'+cp,
      'net.minecraft.world.level.chunk.UniformGlobalCheck']
report={'scope':'Supplemental uniform minimum/boundary global palettes; same unchanged complete-caller benchmark. Reflection constructs workloads and invokes bench before timed run, never per-call. No measured source modifications.',
        'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'driver_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'pairs':[], 'performance':{}}
for fork in range(3):
    pair={}
    for mode in (['java','native'] if fork%2==0 else ['native','java']):
        print('Uniform pair',fork+1,mode,flush=True)
        log=OUT/f'uniform-{fork}-{mode}.log'
        with log.open('w') as stream:subprocess.run(base+[mode],cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,check=True)
        rows=re.findall(r'UNPACK_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',log.read_text())
        require(len(rows)==4, 'Incomplete uniform measurements')
        for name,samples,jit,checksum in rows:
            require(not any(json.loads(jit)), 'JIT compilation during measured rounds');row=pair.setdefault(name,{})
            row[mode]=json.loads(samples)
            require('checksum' not in row or row['checksum']==int(checksum), 'Java/native outputs differ')
            row['checksum']=int(checksum)
    report['pairs'].append(pair)
    (OUT/'uniform-results.json').write_text(json.dumps(report,indent=2))
rng=random.Random(1977)
for name in report['pairs'][0]:
    pairs=[(p[name]['java'],p[name]['native']) for p in report['pairs']]
    ratios=[statistics.median(n)/statistics.median(j) for j,n in pairs]
    boots=sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
    report['performance'][name]={'java_ns':statistics.median(statistics.median(j) for j,n in pairs),
        'native_ns':statistics.median(statistics.median(n) for j,n in pairs), 'ratios':ratios,
        'ratio_ci95':[boots[250],boots[9749]],'passes':max(ratios)<=.95 and boots[9749]<=.95}
    require(len({p[name]['checksum'] for p in report['pairs']})==1, 'Cross-fork outputs differ')
require(all(hashlib.sha256((ROOT/f).read_bytes()).hexdigest()==h for f,h in main['sources'].items()), 'Measured source changed')
require(hashlib.sha256((ROOT/'build/rust/native/mattmc_rust-linux-x64.so').read_bytes()).hexdigest()==main['native_sha256'], 'Measured native library changed')
require(hashlib.sha256(source.read_bytes()).hexdigest()==report['source_sha256'], 'Supplemental Java source changed')
require(hashlib.sha256(Path(__file__).read_bytes()).hexdigest()==report['driver_sha256'], 'Supplemental driver changed')
report['integrity']={'source_hashes_match':True,'native_hash_matches':True,'cross_fork_checksums_match':True}
(OUT/'uniform-results.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report['performance'],indent=2))
require(all(v['passes'] for v in report['performance'].values()), 'Uniform 5% gate failed')
