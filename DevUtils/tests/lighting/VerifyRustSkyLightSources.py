#!/usr/bin/env python3
"""Pinned Java parity and complete skylight-source reconstruction performance acceptance."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = '7a4d181717dc0c7da9086f1a17687dfd522ce417'
PACKAGE = 'net.minecraft.world.level.lighting.'


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True, env={**os.environ, 'CARGO_TARGET_DIR': str(ROOT / 'build/rust/target')})
    return log.read_text()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/skylight-sources-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=2)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    args = parser.parse_args()
    if not args.parity_only and args.forks < 3:
        parser.error('At least three independent process pairs required')
    if args.cpu not in os.sched_getaffinity(0):
        parser.error('CPU unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    original = subprocess.check_output(['git', 'show', REFERENCE + ':src/main/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java'], cwd=ROOT, text=True)
    oracle = (ROOT / 'src/test/java/net/minecraft/world/level/lighting/JavaSkyLightSources.java').read_text()
    marker = '    /** Frozen7a4 algorithm, independently owned test payload;'
    start = oracle.index(marker)
    restored = oracle[:start] + oracle[oracle.rfind('}'):]
    restored = restored.replace('final class JavaSkyLightSources extends ChunkSkyLightSources {', 'public class ChunkSkyLightSources {')
    restored = restored.replace('JavaSkyLightSources(LevelHeightAccessor levelHeightAccessor) {\n        super(LevelHeightAccessor.create(0,16)); // Unused facade owner; oracle below keeps the exact frozen Java algorithm.', 'public ChunkSkyLightSources(LevelHeightAccessor levelHeightAccessor) {')
    if restored != original:
        raise RuntimeError('Independent Java source oracle differs from pinned Frozen implementation')
    (out / 'OriginalChunkSkyLightSources.java').write_text(original)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeSkyLightClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeSkyLightClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativeSkyLightSourcesTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativeSkyLightSourcesTest.xml')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():
        raise RuntimeError('Parity failed')
    cargo = ['cargo', 'test', '--manifest-path', 'src/main/rust/Cargo.toml', '--locked', '--profile', 'suite', '--lib']
    run(cargo + ['world::level::lighting::skylight_sources::', '--', '--test-threads=1'], out / 'rust-kernel-tests.log')
    run(cargo + ['world::level::lighting::source_owner::', '--', '--test-threads=1'], out / 'rust-owner-tests.log')
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativeSkyLightSourcesVerification']
    files = list((ROOT / 'src/main/rust/world/level/lighting/skylight_sources').glob('*.rs'))
    files += list((ROOT / 'src/main/rust/world/level/lighting/source_owner').glob('*.rs'))
    files += list((ROOT / 'src/test/java/net/minecraft/world/level/lighting').glob('*SkyLightSources*.java'))
    files += [ROOT / p for p in ['src/main/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java', 'src/test/java/net/minecraft/world/level/chunk/NativeSkyLightSources.java', 'src/main/java/net/minecraft/world/level/lighting/NativeSkySourceOwner.java', 'src/main/java/net/minecraft/world/level/chunk/NativeSkySourceSections.java', 'src/main/java/net/minecraft/world/level/lighting/NativeLightPropagation.java', 'src/main/java/net/minecraft/world/level/chunk/PalettedContainer.java', 'src/test/resources/worldgen/heightmap/chunks.json']]
    files += [ROOT / 'src/main/rust/world/level/lighting/mod.rs', ROOT / 'src/main/rust/world/level/mod.rs', ROOT / 'src/main/java/net/minecraft/world/level/chunk/NativeHeightmap.java', Path(__file__).resolve()]
    report = {'reference': subprocess.check_output(['git','rev-parse',REFERENCE],cwd=ROOT,text=True).strip(),
              'scope': 'Complete fillFrom() caller: eligibility, local palette mapping, packed input transfer, ordinary FFM calls, output publication and consumption. Immutable registry/occlusion-table bootstrap and fixture loading excluded equally; no source-result cache.',
              'fixture': '16 real chunk instances per case, identical packed inputs. Saved FULL chunks: four dimensions, two seeds, two chunks per seed/dimension. Additional solid/water/leaves/glass/empty/shapes/global/island fixtures. No RNG during scanning.',
              'jvm': jvm, 'cpu': args.cpu,
              'java_version': subprocess.check_output(['java','--version'],text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu','-J'],text=True)),
              'native_sha256': hashlib.sha256((native/'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
              'sources': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
              'parity': re.findall(r'SKYLIGHT_\w*PARITY[^\n<]*',xml.read_text()), 'pairs': [], 'performance': {}}
    def save():
        (out/'results.json').write_text(json.dumps(report,indent=2))
    save()
    if args.parity_only:
        print(out/'results.json')
        return
    for fork in range(args.forks):
        pair = {}
        for mode in (['java','native'] if fork%2==0 else ['native','java']):
            print(f'Pair {fork+1}: {mode}',flush=True)
            text = run(base+[mode,'all'],out/f'{fork}-{mode}.log')
            rows = re.findall(r'SKYLIGHT_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)',text)
            if len(rows)!=12:
                raise RuntimeError('Incomplete measurements')
            for name, samples, jit, checksum in rows:
                if any(json.loads(jit)):
                    raise RuntimeError('Compilation during measurement')
                row = pair.setdefault(name,{})
                row[mode] = json.loads(samples)
                if 'checksum' in row and row['checksum'] != int(checksum):
                    raise RuntimeError('Unequal workload outputs')
                row['checksum'] = int(checksum)
        report['pairs'].append(pair)
        save()
    rng = random.Random(1977)
    for name in report['pairs'][0]:
        pairs = [(p[name]['java'],p[name]['native']) for p in report['pairs']]
        ratios = [statistics.median(n)/statistics.median(j) for j,n in pairs]
        boots = sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name] = {'java_ns': statistics.median(statistics.median(j) for j,n in pairs),
                                      'native_ns': statistics.median(statistics.median(n) for j,n in pairs),
                                      'ratios': ratios, 'ratio_ci95': [boots[250],boots[9749]],
                                      'passes': max(ratios)<=.95 and boots[9749]<=.95}
    save()
    print(json.dumps(report['performance'],indent=2))
    if not all(v['passes'] for v in report['performance'].values()):
        raise SystemExit('5% performance gate FAILED')
    print(out/'results.json')


if __name__ == '__main__':
    main()
