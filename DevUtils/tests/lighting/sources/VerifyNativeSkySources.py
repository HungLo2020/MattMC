#!/usr/bin/env python3
"""Verify native-owned source effects, transport, escaped arrays and real pending lifetime."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import time

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent

def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def command(args, log):
    with log.open('w') as stream:
        subprocess.run(args, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/lighting/source-owner-verification')
    parser.add_argument('--skip-build', action='store_true', help='Use an already built matching release native library.')
    args = parser.parse_args()
    if not Path('/proc/self/maps').is_file():
        parser.error('This origin-verified driver requires Linux /proc; use the Gradle source tests elsewhere.')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be a new directory under build/.')
    out.mkdir(parents=True, exist_ok=False)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeSourceOwnerClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    build = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeSourceOwnerClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        build += ['-x', 'buildRustNative']
    command(build, out / 'build.log')
    cp = cpfile.read_text().strip()
    native = ROOT / 'build/rust/native'
    libraries = sorted(path for path in native.glob('mattmc_rust-*') if path.suffix in ['.so', '.dylib', '.dll'])
    if len(libraries) != 1:
        raise RuntimeError('Expected exactly one matching native library in build/rust/native/.')
    library = libraries[0].resolve()
    inputs = {str(path): digest(path) for path in HERE.rglob('*') if path.is_file()}
    for path in (ROOT / 'src/main/rust/world/level/lighting').rglob('*.rs'):
        inputs[str(path)] = digest(path)
    for name in ['src/main/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java',
                 'src/main/java/net/minecraft/world/level/lighting/NativeSkySourceOwner.java',
                 'src/main/java/net/minecraft/world/level/lighting/NativeLightPropagation.java',
                 'src/main/java/net/minecraft/world/level/chunk/NativeSkySourceSections.java']:
        inputs[str(ROOT / name)] = digest(ROOT / name)
    for entry in cp.split(os.pathsep):
        path = Path(entry)
        for item in path.rglob('*') if path.is_dir() else [path]:
            if item.is_file():
                inputs[str(item)] = digest(item)
    inputs[str(library)] = digest(library)
    classes = out / 'classes'
    classes.mkdir()
    fixture = ROOT / 'DevUtils/tests/lighting/coordinator/VerifyLightCoordinator.java'
    inputs[str(fixture)] = digest(fixture)
    sources = [*sorted(HERE.glob('*.java')), fixture]
    command(['javac', '--release', '25', '-proc:none', '-cp', cp, '-d', str(classes), *map(str, sources)], out / 'compile.log')
    inputs.update({str(path): digest(path) for path in classes.rglob('*.class')})
    owner_source = ROOT / 'src/main/java/net/minecraft/world/level/lighting/NativeSkySourceOwner.java'
    owner_text = owner_source.read_text()
    fallback = 'if(!bulkFill(chunk,first,second))run(null,chunk,0,0,0,first,second,true);'
    if owner_text.count(fallback) != 1:
        raise RuntimeError('Update the required-native-route control for the current source facade.')
    overlay = out / 'required-source/net/minecraft/world/level/lighting/NativeSkySourceOwner.java'
    overlay.parent.mkdir(parents=True)
    overlay.write_text(owner_text.replace(fallback, 'if(!bulkFill(chunk,first,second))throw new AssertionError("Native source bulk route declined");'))
    required = out / 'required-classes'
    required.mkdir()
    command(['javac', '--release', '25', '-proc:none', '-cp', cp, '-d', str(required), str(overlay)], out / 'required-compile.log')
    inputs[str(overlay)] = digest(overlay)
    inputs.update({str(path): digest(path) for path in required.rglob('*.class')})
    # Require the actual primary-owner/air consumer: exporting compatibility
    # words or falling back to the effect cursor must fail this same Frozen probe.
    resource_source = ROOT / 'src/main/java/net/minecraft/world/level/chunk/NativeSkySourceSections.java'
    resource_text = resource_source.read_text()
    signature = ' public static SectionData export('
    start = resource_text.index('{', resource_text.index(signature))
    end, depth = start + 1, 1
    while depth:
        depth += (resource_text[end] == '{') - (resource_text[end] == '}')
        end += 1
    direct_source = out / 'direct-required-source/net/minecraft/world/level/chunk/NativeSkySourceSections.java'
    direct_source.parent.mkdir(parents=True)
    direct_source.write_text(resource_text[:start + 1]
        + '\n  throw new AssertionError("Primary source reconstruction exported VM section words");\n '
        + resource_text[end - 1:])
    direct_required = out / 'direct-required-classes'
    direct_required.mkdir()
    command(['javac', '--release', '25', '-proc:none', '-cp', cp, '-d', str(direct_required),
             str(overlay), str(direct_source)], out / 'direct-required-compile.log')
    inputs[str(direct_source)] = digest(direct_source)
    inputs.update({str(path): digest(path) for path in direct_required.rglob('*.class')})
    release = 'try{RELEASE.invokeExact(actual);}catch(Throwable e){throw fail(e);}'
    if owner_text.count(release) != 1:
        raise RuntimeError('Update the actual-release leak control for the current source facade.')
    leak_source = out / 'leak-source/net/minecraft/world/level/lighting/NativeSkySourceOwner.java'
    leak_source.parent.mkdir(parents=True)
    leak_source.write_text(owner_text.replace(release, '{ /* Test only: omit the real native base release. */ }'))
    leak_classes = out / 'leak-classes'
    leak_classes.mkdir()
    command(['javac', '--release', '25', '-proc:none', '-cp', cp, '-d', str(leak_classes), str(leak_source)], out / 'leak-compile.log')
    inputs[str(leak_source)] = digest(leak_source)
    inputs.update({str(path): digest(path) for path in leak_classes.rglob('*.class')})
    origins = json.loads((HERE / 'references/origins.json').read_text())
    jobs = {**origins['cases'], 'bulk-required': {**origins['cases']['bulk'], 'reference': 'bulk'}, 'bulk-direct-required': {**origins['cases']['bulk'], 'reference': 'bulk'}, 'section-lease': {'main': 'VerifySkySourceSectionLease', 'rows': 2, 'prefix': 'SOURCE_SECTION_LEASE '}, **origins['extensions'], 'lifetime': {'main': 'VerifySkySourceNativeLifetime', 'rows': 2, 'prefix': 'SKY_SOURCE_LIFETIME '}, 'engine-consumer': {'main': origins['engine_consumer']['main'], 'rows': 9, 'prefix': ''}, 'release-leak-control': {'main': 'VerifySkySourceNativeLifetime', 'rows': 0, 'prefix': 'SKY_SOURCE_LIFETIME '}}
    result = {'scope': 'Focused correctness only; reference identities in origins.json. No timing/FPS/whole-lighting acceptance.',
              'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'inputs': inputs, 'native_sha256': digest(library), 'runs': {}, 'passed': False}
    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2))
    save()
    try:
        for mode, job in jobs.items():
            classlog = out / (mode + '-classes.log')
            cmd = ['java', '-Xms256m', '-Xmx3g', '--enable-native-access=ALL-UNNAMED',
                   '-Xlog:class+load=info:file=' + str(classlog), '-Dmattmc.rust.natives.dir=' + str(native),
                   '-cp', str(classes) + os.pathsep + cp, 'net.minecraft.world.level.lighting.' + job['main']]
            if mode in ['bulk-required', 'bulk-direct-required', 'section-lease', 'release-leak-control']:
                position = cmd.index('-cp') + 1
                cmd[position] = str(required if mode == 'bulk-required' else direct_required if mode in ['bulk-direct-required', 'section-lease'] else leak_classes) + os.pathsep + cmd[position]
            if mode == 'engine-consumer':
                cmd.insert(cmd.index('-cp'), '-Dcoordinator.expectSkyProducer=true')
                cmd += [str(out / 'consumer-dumps'), 'all']
            start = time.monotonic()
            mapped = set()
            with (out / (mode + '.log')).open('w') as stream:
                process = subprocess.Popen(cmd, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                    env={key: value for key, value in os.environ.items() if key not in ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MATTMC_RUST_NATIVE_DIR']})
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                name = line.split()[-1]
                                if 'mattmc_rust' in name:
                                    mapped.add(str(Path(name).resolve()))
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() - start > 180:
                            raise TimeoutError(mode)
                        time.sleep(.05)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait()
            text = (out / (mode + '.log')).read_text()
            lines = [] if mode == 'engine-consumer' else [line[line.index(job['prefix']):] for line in text.splitlines() if job['prefix'] in line]
            trace = ('\n'.join(lines) + '\n').encode()
            (out / (mode + '.trace')).write_bytes(trace)
            reference = HERE / 'references' / (job.get('reference', mode) + '.trace')
            if mode == 'engine-consumer':
                dumps = sorted((out / 'consumer-dumps').glob('*.txt'))
                reference_matches = len(dumps) == 9 and all(path.read_bytes() == (HERE / 'references/consumer' / path.name).read_bytes() for path in dumps)
                lines = [path.name for path in dumps]
            else:
                reference_matches = mode in ['lifetime', 'section-lease', 'release-leak-control'] or trace == reference.read_bytes()
            row = {'command': cmd, 'exit': process.returncode, 'seconds': time.monotonic() - start,
                   'rows': len(lines), 'trace_sha256': hashlib.sha256(trace).hexdigest(),
                   'reference_matches': reference_matches,
                   'actual_additive_api': mode != 'transport' or 'TRANSPORT_API additive=true' in text,
                   'mapped_native': sorted(mapped), 'native_origin': mapped == {str(library)},
                   'source_class_origin': 'net.minecraft.world.level.lighting.ChunkSkyLightSources source: file:' + str(ROOT / 'build/classes/java/main') + '/' in classlog.read_text()}
            row['native_owner_class_origin'] = 'net.minecraft.world.level.lighting.NativeSkySourceOwner source: file:' + str(required if mode == 'bulk-required' else direct_required if mode in ['bulk-direct-required', 'section-lease'] else leak_classes if mode == 'release-leak-control' else ROOT / 'build/classes/java/main') + '/' in classlog.read_text()
            row['required_direct_resource_origin'] = mode not in ['bulk-direct-required', 'section-lease'] or (
                'net.minecraft.world.level.chunk.NativeSkySourceSections source: file:'
                + str(direct_required) + '/' in classlog.read_text())
            row['expected_native_leak_rejection'] = mode == 'release-leak-control' and process.returncode != 0 and 'actual base handle did not retire while pending cursor remains' in text
            expected_exit = row['expected_native_leak_rejection'] if mode == 'release-leak-control' else process.returncode == 0
            row['passed'] = expected_exit and len(lines) == job['rows'] and row['reference_matches'] and row['native_origin'] and row['source_class_origin'] and row['actual_additive_api'] and row['native_owner_class_origin'] and row['required_direct_resource_origin']
            result['runs'][mode] = row
            save()
            print(mode, 'PASS' if row['passed'] else 'FAIL', flush=True)
            if not row['passed']:
                raise RuntimeError('Source contract failed: ' + mode)
        result['passed'] = len(result['runs']) == len(jobs) and all(row['passed'] for row in result['runs'].values())
    finally:
        result['guards_pass'] = all(digest(path) == expected for path, expected in inputs.items())
        result['passed'] = result['passed'] and result['guards_pass']
        save()
    if not result['passed']:
        raise SystemExit('Source contract or input guard failed.')
    print(out / 'result.json')

if __name__ == '__main__':
    main()
