#!/usr/bin/env python3
"""Verify geode numeric fields and original placement behavior, without rendering."""
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
REFERENCE = '7af3a1594956f41ed57c3bf67d11ce006e61530f'
PACKAGE = 'net.minecraft.world.level.levelgen.feature.'
CASES = ['amethyst_crack', 'amethyst_no_crack', 'ore_crack', 'ore_no_crack', 'coral_no_crack', 'small_1', 'large_20']


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def compact(text):
    return re.sub(r'\s+', '', text)


def audit(out):
    path = 'src/main/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java'
    original = subprocess.check_output(['git', 'show', REFERENCE + ':' + path], cwd=ROOT).decode()
    tests = ROOT / 'src/test/java/net/minecraft/world/level/levelgen/feature'
    if (tests / 'JavaGeodeFeature.java').read_text() != re.sub(r'\bGeodeFeature\b', 'JavaGeodeFeature', original):
        raise RuntimeError('Java placement oracle differs from original beyond class renaming')
    start = original.index('double r = normalNoise.getValue')
    end = original.index('if (!(s < h))', start)
    numeric = original[start:end]
    fields = (tests / 'JavaGeodeFields.java').read_text()
    expected = numeric.replace('geodeConfiguration.noiseMultiplier', 'multiplier').replace('geodeCrackSettings.crackPointOffset', 'crackOffset')
    if compact(fields[fields.index('double r ='):fields.index('consumer.accept(blockPos3,s,t);')]) != compact(expected):
        raise RuntimeError('Streaming numeric oracle differs from original loops')
    # Restore just the batch hook/fallback to the original numeric loop. The
    # rest of place(), including every random/world/provider call, must match.
    production = (ROOT / path).read_text()
    hook = production.index('try (var nativeFields =')
    loop = production.index('for (BlockPos blockPos3', hook)
    restored = production[:hook] + production[loop:]
    hook = restored.index('double s;', hook)
    decision = restored.index('if (!(s < h))', hook)
    restored = restored[:hook] + numeric + restored[decision:]
    marker = restored.index('List<BlockState> list4')
    closing = restored.rfind('}', 0, marker)
    restored = restored[:closing] + restored[closing + 1:]
    if compact(restored) != compact(original):
        raise RuntimeError('Production placement changed outside the numeric batch hook')
    (out / 'OriginalGeodeFeature.java').write_text(original)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/geode-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=2)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    args = parser.parse_args()
    if not args.parity_only and args.forks < 3:
        parser.error('At least three independent process pairs are required')
    if args.cpu not in os.sched_getaffinity(0):
        parser.error('CPU unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    audit(out)
    cpfile, init = out / 'classpath.txt', out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeGeodeClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeGeodeClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    print('Building and checking original-source contracts', flush=True)
    run(command, out / 'build.log')
    run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativeGeodeGeometryTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativeGeodeGeometryTest.xml')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():
        raise RuntimeError('Parity failed')
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/worldgen.rs', '-o', str(out / 'worldgen-tests')], out / 'rust-build.log')
    run([str(out / 'worldgen-tests'), 'feature::geode'], out / 'rust-tests.log')
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativeGeodeGeometryVerification']
    sources = list((ROOT / 'src/main/rust/world/level/levelgen/feature/geode').glob('*.rs'))
    sources += list((ROOT / 'src/test/java/net/minecraft/world/level/levelgen/feature').glob('*Geode*.java'))
    sources += list((ROOT / 'src/main/rust/world/level/levelgen/synth').rglob('*.rs'))
    sources += [ROOT / 'src/main/rust/Cargo.toml', ROOT / 'src/main/rust/Cargo.lock', ROOT / 'src/main/rust/world/level/levelgen/feature/mod.rs', ROOT / 'src/test/rust/worldgen.rs']
    sources += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen/synth' / name for name in ['NormalNoise.java', 'NativeNoise.java', 'NativeNoiseState.java']]
    sources += [ROOT / 'src/test/java/net/minecraft/world/level/levelgen/feature' / name for name in ['OreTestWorld.java', 'UnsupportedOreWorld.java']]
    sources += [ROOT / 'src/main/java/net/minecraft/core/Vec3i.java', ROOT / 'src/main/java/net/minecraft/core/BlockPos.java', ROOT / 'src/main/java/net/minecraft/util/Mth.java']
    sources += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java', ROOT / 'src/main/java/net/minecraft/world/level/levelgen/feature/NativeGeodeGeometry.java', Path(__file__).resolve()]
    joml = [Path(p) for p in cpfile.read_text().strip().split(os.pathsep) if Path(p).name.startswith('joml-') and p.endswith('.jar')]
    if len(joml) != 1:
        raise RuntimeError('Expected exactly one JOML dependency')
    bytecode = run(['javap', '-classpath', str(joml[0]), '-c', 'org.joml.Math'], out / 'joml-math-bytecode.txt')
    invsqrt = bytecode.split('public static double invsqrt(double);', 1)[1].split('public static', 1)[0]
    if not re.search(r'dconst_1.*dload_0.*java/lang/Math.sqrt:\(D\)D.*ddiv.*dreturn', invsqrt, re.S):
        raise RuntimeError('Review the double inverse-square-root implementation')
    report = {
        'reference': REFERENCE,
        'scope': 'Original streaming geode numeric fields versus complete Rust batch caller including eligibility, input packing, state validation, FFM, native-to-heap copy and ordered consumption. Existing Rust NormalNoise is used on both sides, as it was before this slice. Placement decisions, RNG and world operations remain Java.',
        'fixture': '32 seeded grids per case, equal weights on both sides; all 15 registered configs fall into three numeric families. Cracked and uncracked versions are separately measured for the two crack-capable families. Additional cases measure the smallest supported cube (512 cells, one body point, no cracks) and largest cube (64000 cells, 20 body points, three cracks). Each case runs in its own fresh JVM per mode and pair.',
        'jvm': jvm, 'cpu': args.cpu,
        'java_version': subprocess.check_output(['java', '--version'], text=True),
        'joml_sha256': hashlib.sha256(joml[0].read_bytes()).hexdigest(),
        'rust_version': subprocess.check_output(['rustc', '--version'], text=True),
        'hardware': json.loads(subprocess.check_output(['lscpu', '-J'], text=True)),
        'native_sha256': hashlib.sha256((native / 'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
        'sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
        'registered_configs': {},
        'parity': re.findall(r'GEODE_\w+_PARITY[^\n<]*', xml.read_text()),
        'pairs': [], 'performance': {},
    }
    for p in sorted((ROOT / 'src/main/resources/data/minecraft/worldgen/configured_feature').rglob('*.json')):
        obj = json.loads(p.read_text())
        if obj.get('type') == 'minecraft:geode':
            c = obj['config']
            report['registered_configs'][str(p.relative_to(ROOT))] = { 'sha256': hashlib.sha256(p.read_bytes()).hexdigest(),
                'numeric': {k: c.get(k) for k in ['min_gen_offset','max_gen_offset','distribution_points','outer_wall_distance','point_offset','noise_multiplier','crack']} }
    def save():
        (out / 'results.json').write_text(json.dumps(report, indent=2))
    save()
    if args.parity_only:
        print(out / 'results.json'); return
    for fork in range(args.forks):
        pair = {}; report['pairs'].append(pair)
        for name in CASES:
            row = pair.setdefault(name, {})
            for mode in (['java', 'native'] if fork % 2 == 0 else ['native', 'java']):
                print(f'Pair {fork + 1}: {name} {mode}', flush=True)
                text = run(base + [mode, name], out / f'{fork}-{name}-{mode}.log')
                matches = re.findall(r'GEODE_BENCH case=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)', text)
                if len(matches) != 1 or matches[0][0] != name:
                    raise RuntimeError('Incomplete measurements')
                _, ns, jit, checksum = matches[0]
                if any(json.loads(jit)):
                    raise RuntimeError('Compilation during measurements')
                if 'checksum' in row and row['checksum'] != int(checksum):
                    raise RuntimeError('Unequal workload outputs')
                row[mode] = json.loads(ns); row['checksum'] = int(checksum)
                save()
    rng = random.Random(29104)
    for name in CASES:
        pairs = [(p[name]['java'], p[name]['native']) for p in report['pairs']]
        ratios = [statistics.median(n) / statistics.median(j) for j, n in pairs]
        boots = sorted(statistics.median(statistics.median(rng.choices(n, k=len(n))) / statistics.median(rng.choices(j, k=len(j))) for j, n in rng.choices(pairs, k=len(pairs))) for _ in range(10000))
        report['performance'][name] = {
            'java_ns': statistics.median(statistics.median(j) for j, n in pairs),
            'native_ns': statistics.median(statistics.median(n) for j, n in pairs),
            'ratios': ratios, 'ratio_ci95': [boots[250], boots[9749]],
            'passes': max(ratios) <= .95 and boots[9749] <= .95,
        }
    save()
    print(json.dumps(report['performance'], indent=2))
    if not all(v['passes'] for v in report['performance'].values()):
        raise SystemExit('5% performance gate FAILED')
    print(out / 'results.json')


if __name__ == '__main__':
    main()
