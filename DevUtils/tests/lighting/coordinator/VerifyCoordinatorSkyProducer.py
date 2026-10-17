#!/usr/bin/env python3
"""Capture supported sky-source contracts, then compare a changed actual pair.

References preserve existing Frozen/pre images from a sealed receipt. Candidate
execution compiles the exact archived observers and additionally requires native
whole-producer admission for supported stock layers, and decline for escaped ones.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'threaded'))
from VerifyThreadedLighting import ROOT, FROZEN, FROZEN_HEAD, images, sha, source_identity

MODES = ('stock', 'default-write', 'default-skip', 'aliases', 'escaped',
         'skip', 'throw-neighbor', 'copy-mutation', 'custom-storage-copy')
SOURCES = ('VerifyLightCoordinator.java', 'VerifyCoordinatorSkySources.java')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--baseline-receipt', type=Path)
    parser.add_argument('--frozen-native-dir', type=Path)
    parser.add_argument('--control-native-dir', type=Path)
    parser.add_argument('--reference', type=Path)
    parser.add_argument('--current-classpath-file', type=Path)
    parser.add_argument('--native-build-manifest', type=Path)
    parser.add_argument('--java-build-manifest', type=Path)
    args = parser.parse_args()
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    result = {'kind': 'sky-producer', 'passed': False, 'guards_pass': False, 'runs': {}, 'commands': {},
              'observer_sha256': {}, 'class_images': {}, 'native_images': {},
              'source_before': source_identity(), 'frozen_head': FROZEN_HEAD,
              'scope': 'Actual source producer/default/alias/callback contracts; no timing/server claim'}
    classpaths = {}
    native_dirs = {}
    if args.reference:
        reference = args.reference.resolve()
        old = json.loads((reference / 'result.json').read_text())
        assert old['passed'] and old['guards_pass'] and set(old['runs']) == {'frozen', 'control'}
        assert all(sha(p) == h for p, h in old['class_images'].items())
        assert all(sha(p) == h for p, h in old['native_images'].items())
        assert args.current_classpath_file and args.native_build_manifest and args.java_build_manifest
        native = json.loads(args.native_build_manifest.read_text())
        java = json.loads(args.java_build_manifest.read_text())
        assert native['passed'] and native['source_guard'] and java['exit'] == 0
        assert all(sha(ROOT / p) == h for p, h in native['source_images'].items())
        assert all(sha(ROOT / p) == h for p, h in java['sources'].items())
        classpaths['current'] = args.current_classpath_file.read_text().strip()
        native_dirs['current'] = ROOT / 'build/rust/native'
        assert sha(native_dirs['current'] / 'mattmc_rust-linux-x64.so') == native['native_sha256']
        result['native_sha256'] = native['native_sha256']
        result.update(reference=str(reference), reference_result_sha256=sha(reference / 'result.json'),
                      expected=old['runs']['frozen'], prior=old['runs']['control'])
        source_dir = reference / 'observer-source'
    else:
        assert args.baseline_receipt and args.frozen_native_dir and args.control_native_dir
        old = json.loads(args.baseline_receipt.read_text())
        assert old['guards_pass'] and old['frozen_head'] == FROZEN_HEAD
        assert all(sha(p) == h for p, h in old['class_images'].items())
        for side in ('frozen', 'control'):
            command = old['commands']['compile-' + side + '.log']
            classpaths[side] = command[command.index('-cp') + 1]
        native_dirs = {'frozen': args.frozen_native_dir.resolve(), 'control': args.control_native_dir.resolve()}
        assert sha(native_dirs['frozen'] / 'mattmc_rust-linux-x64.so') == old['native_sha256']
        assert sha(native_dirs['control'] / 'mattmc_rust-linux-x64.so') == old['control_native_sha256']
        result.update(baseline_receipt=str(args.baseline_receipt.resolve()),
                      baseline_receipt_sha256=sha(args.baseline_receipt), control_ref=old['control_ref'],
                      note='Known inherited custom-reader/copy differences retained, not accepted behavior')
        source_dir = HERE
    archive = out / 'observer-source'
    archive.mkdir()
    for name in SOURCES:
        source = source_dir / name
        if args.reference:
            assert sha(source) == old['observer_sha256'][name]
        (archive / name).write_bytes(source.read_bytes())
        result['observer_sha256'][name] = sha(source)
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)

    def run(command, log):
        result['commands'][log] = command
        with (out / log).open('w') as stream:
            code = subprocess.call(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
        assert code == 0, (log, code)

    try:
        for side, cp in classpaths.items():
            classes = out / (side + '-classes')
            classes.mkdir()
            run([str(args.java_home / 'bin/javac'), '-g', '-nowarn', '-cp', cp, '-d', str(classes),
                 *map(str, (archive / name for name in SOURCES))], side + '-compile.log')
            cp = str(classes) + os.pathsep + cp
            result['class_images'].update(images({side: cp}))
            library = native_dirs[side] / 'mattmc_rust-linux-x64.so'
            result['native_images'][str(library)] = sha(library)
            traces = out / side
            run([str(args.java_home / 'bin/java'), '-Xms512m', '-Xmx2g', '--enable-native-access=ALL-UNNAMED',
                 '-Dcoordinator.expectSkyProducer=' + str(side == 'current').lower(),
                 '-Dmattmc.rust.natives.dir=' + str(native_dirs[side]), '-cp', cp,
                 'net.minecraft.world.level.lighting.VerifyCoordinatorSkySources', str(traces), 'all'], side + '-run.log')
            result['runs'][side] = {mode: {'rows': len((traces / (mode + '.txt')).read_text().splitlines()),
                'sha256': sha(traces / (mode + '.txt'))} for mode in MODES}
            (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        result['passed'] = (result['runs']['current'] == result['expected']) if args.reference else True
    finally:
        result['guards_pass'] = (source_identity() == result['source_before']
            and all(sha(p) == h for p, h in result['class_images'].items())
            and all(sha(p) == h for p, h in result['native_images'].items())
            and all(sha(archive / name) == h for name, h in result['observer_sha256'].items()))
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'passed': result['passed'], 'guards_pass': result['guards_pass'], 'runs': result['runs']}))
    assert result['passed'] and result['guards_pass'], str(out / 'result.json')


if __name__ == '__main__':
    main()
