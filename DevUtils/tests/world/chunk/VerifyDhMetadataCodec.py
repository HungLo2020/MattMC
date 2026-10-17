#!/usr/bin/env python3
"""Compare saved DH metadata bytes and EOF prefixes with actual unchanged Frozen."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources(root):
    names = subprocess.check_output(['git', 'ls-files', '--cached', '--others',
        '--exclude-standard', '-z', '--', 'src', 'DevUtils', 'build.gradle'], cwd=root).split(b'\0')
    return {os.fsdecode(n): sha(root / os.fsdecode(n)) for n in names
            if n and (root / os.fsdecode(n)).is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--saved-corpus', type=Path, help='Read-only extracted full saved-DTO manifest')
    args = parser.parse_args()
    if not args.java_home:
        parser.error('JDK25 is required via --java-home or JAVA_HOME')
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    output.mkdir(parents=True, exist_ok=False)
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    prompt = ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md'
    pins = {'current': sources(ROOT), 'frozen': sources(FROZEN), 'native': sha(native), 'prompt': sha(prompt)}
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    assert pins['prompt'] == 'c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5'
    corpus_pins = {}
    if args.saved_corpus:
        manifest = args.saved_corpus.resolve()
        data = json.loads(manifest.read_text())
        corpus_pins[str(manifest)] = sha(manifest)
        database = Path(data['source'])
        assert sha(database) == data['source_sha256']
        corpus_pins[str(database)] = data['source_sha256']
        for row in data['rows']:
            for field in row['fields'].values():
                file = manifest.parent / field['file']
                assert sha(file) == field['sha256']
                corpus_pins[str(file)] = field['sha256']
    result = {'passed': False, 'guards_pass': False, 'pins': pins, 'saved_corpus_pins': corpus_pins,
        'scope': 'Actual generation/compression metadata reads and generation writer, all compression modes, '
                 'signed values, noncanonical lengths, EOF prefixes and real saved metadata. '
                 'Integrated Current uniformity sidecar uses separate tests. Supplemental to live gameplay; no timing claim.'}
    jdk = Path(args.java_home) / 'bin'
    env = os.environ.copy()
    env.update(JAVA_HOME=args.java_home, PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('dhCodecRuntimeClasspath') { "
                    "doLast { println('CODEC_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            code = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        if code:
            raise RuntimeError(f'{name} failed ({code})')
        return (output / name).read_text()

    images = {}
    try:
        for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
            text = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'dhCodecRuntimeClasspath'], side + '-classpath.log', repo)
            cp = next(line.removeprefix('CODEC_CP ') for line in text.splitlines() if line.startswith('CODEC_CP '))
            classes = [p for base in cp.split(os.pathsep) if Path(base).is_dir() for p in Path(base).rglob('*.class')]
            images[side] = {str(p): sha(p) for p in classes}
            target = output / side
            target.mkdir()
            run([str(jdk / 'javac'), '-cp', cp, '-d', str(target), str(ROOT / 'DevUtils/tests/world/chunk/VerifyDhMetadataCodec.java')], side + '-compile.log')
            command = [str(jdk / 'java'), '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders']
            if side == 'current':
                command += ['--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent)]
            command += ['-cp', str(target) + os.pathsep + cp, 'VerifyDhMetadataCodec',
                        str(output / (side + '.txt')), side]
            if args.saved_corpus:
                command += [str(args.saved_corpus.resolve())]
            run(command, side + '-run.log', repo)
        current = (output / 'current.txt').read_text().splitlines()
        frozen = (output / 'frozen.txt').read_text().splitlines()
        differences = [{'line': i + 1, 'current': a, 'frozen': b}
                       for i, (a, b) in enumerate(zip(current, frozen)) if a != b]
        result.update(line_counts={'current': len(current), 'frozen': len(frozen)}, differences=differences[:30],
                      class_images=images, frozen_sha256=sha(output / 'frozen.txt'))
        assert len(current) == len(frozen) and len(current) >= 510 and not differences, 'Metadata bytes or state differ from actual Frozen'
        result['passed'] = True
    finally:
        result['guards_pass'] = (sources(ROOT) == pins['current'] and sources(FROZEN) == pins['frozen']
            and sha(native) == pins['native'] and sha(prompt) == pins['prompt']
            and all(sha(Path(p)) == digest for side in images.values() for p, digest in side.items())
            and all(sha(Path(p)) == digest for p, digest in corpus_pins.items()))
        (output / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    assert result['passed'] and result['guards_pass']
    print(f'Actual Frozen DH metadata codec PASS: {len(current)} state rows; source/native/class guards PASS')


if __name__ == '__main__':
    main()
