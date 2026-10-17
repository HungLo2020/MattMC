#!/usr/bin/env python3
"""Literal original height-blending grids: exact parity and caller-inclusive speed."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = '7af3a1594956f41ed57c3bf67d11ce006e61530f'
BASE = 'src/main/java/net/minecraft/world/level/levelgen/'
TEST = 'src/test/java/net/minecraft/world/level/levelgen/blending/'
PACKAGE = 'net.minecraft.world.level.levelgen.blending.'
CASES = ['seam', 'minimum', 'sparse', 'border', 'mixed', 'large', 'wide', 'far']


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def original(name):
    return subprocess.check_output(['git', 'show', REFERENCE + ':' + BASE + name], cwd=ROOT, text=True)


def close_brace(source, brace):
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return end


def remove_method(source, signature):
    start = source.index(signature)
    # Remove the new Javadoc with the public batch method, if present.
    if signature.startswith('\tpublic void fillBlendingOutputs('):
        start = source.rindex('\t/** Fill the chunk', 0, start)
    return source[:start] + source[close_brace(source, source.index('{', start)):]


def compact(source):
    return re.sub(r'\s+', '', source)


def audit(out):
    source = original('blending/Blender.java')
    if (ROOT / TEST / 'JavaBlender.java').read_text() != re.sub(r'\bBlender\b', 'JavaBlender', source):
        raise RuntimeError('Literal original Blender oracle changed')
    current = (ROOT / BASE / 'blending/Blender.java').read_text()
    for signature in ['\tpublic void fillBlendingOutputs(', '\tdouble heightForNativeGrid(']:
        current = remove_method(current, signature)
    if compact(current) != compact(source): raise RuntimeError('Scalar blending changed')
    data = (ROOT / BASE / 'blending/BlendingData.java').read_text()
    data = remove_method(data, '\tboolean hasNativeHeightLayout(')
    if compact(data) != compact(original('blending/BlendingData.java')):
        raise RuntimeError('Blending data changed beyond pure layout query')
    chunk = original('NoiseChunk.java')
    start = chunk.index('\t\t\tfor (int l = 0; l <= this.noiseSizeXZ; l++) {')
    end = chunk.index('\n\t\t} else {', start)
    body = chunk[start:end]
    replacement = '\t\t\tblender.fillBlendingOutputs(this.firstNoiseX, this.firstNoiseZ, this.noiseSizeXZ + 1,\n\t\t\t\tthis.blendAlpha.values, this.blendOffset.values);'
    expected = chunk[:start] + replacement + chunk[end:]
    if (ROOT / BASE / 'NoiseChunk.java').read_text() != expected:
        raise RuntimeError('NoiseChunk changed beyond exact grid dispatch')
    transformed = body.replace('this.noiseSizeXZ', 'noiseSizeXZ').replace('this.firstNoiseX', 'firstNoiseX').replace('this.firstNoiseZ', 'firstNoiseZ').replace('this.blendAlpha.values', 'alpha').replace('this.blendOffset.values', 'offset').replace('this.blendAlpha.sizeXZ', '(noiseSizeXZ + 1)').replace('this.blendOffset.sizeXZ', '(noiseSizeXZ + 1)').replace('Blender.BlendingOutput', 'JavaBlender.BlendingOutput')
    expected = 'package net.minecraft.world.level.levelgen.blending;\n\nimport net.minecraft.core.QuartPos;\n\n/** Literal original NoiseChunk initialization loop, with field names as arguments. */\nfinal class JavaBlendingGrid {\n    static void fill(JavaBlender blender, int firstNoiseX, int firstNoiseZ, int noiseSizeXZ, double[] alpha, double[] offset) {\n' + transformed + '\n    }\n}\n'
    if (ROOT / TEST / 'JavaBlendingGrid.java').read_text() != expected:
        raise RuntimeError('Original grid loop changed')
    for name in ['Blender.java', 'BlendingData.java']:
        (out / ('Original' + name)).write_text(original('blending/' + name))
    (out / 'OriginalNoiseChunk.java').write_text(chunk)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/blending-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=2)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    parser.add_argument('--case', choices=['all', *CASES], default='all')
    args = parser.parse_args()
    if not args.parity_only and args.forks < 3: parser.error('At least three independent JVM pairs required')
    if args.cpu not in os.sched_getaffinity(0): parser.error('CPU unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'): parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    audit(out)
    cpfile, init = out / 'classpath.txt', out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeBlendingClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeBlendingClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build: command += ['-x', 'buildRustNative']
    print('Auditing originals and building release', flush=True)
    run(command, out / 'build.log')
    run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativeBlendingTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativeBlendingTest.xml')
    node = ET.fromstring(xml.read_text())
    if int(node.attrib['failures']) or int(node.attrib['errors']): raise RuntimeError('Java parity failed')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/worldgen.rs', '-o', str(out / 'world-tests')], out / 'rust-build.log')
    rust_text = run([str(out / 'world-tests'), 'levelgen::blending'], out / 'rust-tests.log')
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *flags, '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativeBlendingVerification']
    files = list((ROOT / 'src/main/rust/world/level/levelgen/blending').glob('*.rs')) + list((ROOT / BASE / 'blending').glob('*.java')) + list((ROOT / TEST).glob('*.java'))
    files += [ROOT / BASE / 'NoiseChunk.java', ROOT / 'src/main/java/net/minecraft/util/Mth.java', ROOT / 'src/main/java/net/minecraft/util/NativeLibraryLoader.java', ROOT / 'src/main/rust/world/level/levelgen/mod.rs', ROOT / 'src/main/rust/Cargo.toml', ROOT / 'src/main/rust/Cargo.lock', Path(__file__).resolve()]
    report = {'reference': REFERENCE, 'scope': 'Complete height-blending grid initialization: eligibility, original current direct lookups, exact fastutil sample order, fresh input collection, all ordinary FFM copies/call, caller array writes and consumption. Same original scalar Blender and literal NoiseChunk grid loop as baseline. No result/sample cache or hand-written SIMD. Fixture/library/initial scratch setup excluded equally. Compatibility paths outside native timing claims.',
        'fixtures': 'Eight independently seeded current height maps per case, original Blender.of chunk-ring traversal. seam is a chunk-aligned west old-terrain boundary with only the original EAST/NORTH_EAST samples present (60 heights); other layouts stress denser saved height maps. Finite full and missing heights, positive/negative quart queries, direct/missing mixtures, 25 or 81 points, three to roughly one hundred old chunks. far tests the original all-samples-outside cutoff, not a cached result.',
        'java_tests': int(node.attrib['tests']), 'parity': re.findall(r'BLENDING_\w+_PARITY[^\n<]*', xml.read_text()), 'rust_tests': rust_text,
        'case': args.case, 'jvm': flags, 'cpu': args.cpu,
        'java_version': subprocess.check_output(['java', '--version'], text=True), 'rust_version': subprocess.check_output(['rustc', '--version'], text=True),
        'hardware': json.loads(subprocess.check_output(['lscpu', '-J'], text=True)),
        'native_sha256': hashlib.sha256(native.read_bytes()).hexdigest(),
        'sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}, 'pairs': [], 'performance': {}}
    def save(): (out / 'results.json').write_text(json.dumps(report, indent=2))
    save()
    if args.parity_only: print(out / 'results.json'); return
    cases = CASES if args.case == 'all' else [args.case]
    for fork in range(args.forks):
        pair = {}; report['pairs'].append(pair)
        for name in cases:
            row = pair.setdefault(name, {})
            for mode in (['java', 'native'] if fork % 2 == 0 else ['native', 'java']):
                print(f'Pair {fork+1}: {name} {mode}', flush=True)
                text = run(base + [mode, name], out / f'{fork}-{name}-{mode}.log')
                matches = re.findall(r'BLENDING_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)', text)
                if len(matches) != 1 or matches[0][0] != name: raise RuntimeError('Incomplete measurements')
                _, samples, jit, checksum = matches[0]
                if any(json.loads(jit)): raise RuntimeError('Compilation during measurement')
                row[mode] = json.loads(samples)
                if 'checksum' in row and row['checksum'] != int(checksum): raise RuntimeError('Unequal outputs')
                row['checksum'] = int(checksum)
                save()
    rng = random.Random(1977)
    for name in cases:
        pairs = [(p[name]['java'], p[name]['native']) for p in report['pairs']]
        ratios = [statistics.median(n) / statistics.median(j) for j,n in pairs]
        boots = sorted(statistics.median(statistics.median(rng.choices(n, k=len(n))) / statistics.median(rng.choices(j, k=len(j))) for j,n in rng.choices(pairs, k=len(pairs))) for _ in range(10000))
        report['performance'][name] = {'java_ns': statistics.median(statistics.median(j) for j,n in pairs),
            'native_ns': statistics.median(statistics.median(n) for j,n in pairs), 'ratios': ratios, 'ratio_ci95': [boots[250], boots[9749]], 'passes': max(ratios) <= .95 and boots[9749] <= .95}
    for path, expected in report['sources'].items():
        if hashlib.sha256((ROOT / path).read_bytes()).hexdigest() != expected: raise RuntimeError('Measured source changed: ' + path)
    if hashlib.sha256(native.read_bytes()).hexdigest() != report['native_sha256']: raise RuntimeError('Native library changed')
    for name in cases:
        if len({p[name]['checksum'] for p in report['pairs']}) != 1: raise RuntimeError('Cross-fork checksum changed')
    report['integrity'] = {'source_hashes_match': True, 'native_hash_matches': True, 'cross_fork_checksums_match': True}
    save(); print(json.dumps(report['performance'], indent=2))
    if not all(p['passes'] for p in report['performance'].values()): raise SystemExit('5% performance gate FAILED')
    print(out / 'results.json')


if __name__ == '__main__': main()
