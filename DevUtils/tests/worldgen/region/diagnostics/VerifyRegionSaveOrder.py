#!/usr/bin/env python3
"""One test-only controlled save-order regression on published419 and419+S31.

Production comparisons exclude native diagnostic hooks. An explicit, separately
identified diagnostic image can trace one already-reproduced unload case. Never
forces dirty/status bits, directly calls cleanup, or accepts missing preconditions.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[5]
NAMES = ['RegionSaveAgent.java', 'RegionSaveBoot.java', 'RegionLightTrace.java', 'RegionNativeTrace.java', 'RegionSaveOrder.java', 'RegionSaveOrderBoot.java']


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--published', type=Path, required=True)
    parser.add_argument('--failing', type=Path, required=True)
    parser.add_argument('--template', type=Path, required=True)
    parser.add_argument('--native-diagnostic', type=Path, help='Opt-in separate native buffer image; one established failing unload case only')
    parser.add_argument('--pre-s32', type=Path, help='Conditional exact parent41d441c8 image; requires BOTH earlier images reproduce')
    parser.add_argument('--prerequisite', type=Path, help='Original completed controlled-order comparison directory')
    args = parser.parse_args()
    out = args.output.resolve(); assert out.is_relative_to(ROOT / 'build'); out.mkdir(exist_ok=False)
    published = args.published.resolve(); failing = args.failing.resolve(); template = args.template.resolve()
    pub = json.loads((published / 'result.json').read_text()); fail = json.loads((failing / 'manifest.json').read_text())
    assert pub['passed'] and pub['base'] == fail['head'] == '4199640e33c54a1b8408b25f7bb695446dbbabae'
    original = json.loads((template / 'current-fresh/command.json').read_text())
    java = Path(original[3]); classpath = (failing / 'classpath.txt').read_text().strip()
    paths = [Path(__file__).resolve(), *[Path(__file__).with_name(name) for name in NAMES], ROOT / 'DevUtils/tests/worldgen/full_generation/FullGenAudit.java']
    images = {str(p): sha(p) for p in paths}
    images.update(fail['dependencies']); images.update(fail['resource_images'])
    for folder, manifest in [(failing, fail), (published, pub)]:
        images.update({str(folder / 'classes' / p): h for p, h in manifest['classes'].items()})
    fail_native = ROOT / 'build/decoration-region/s0031-s32-integrated-native-1/native/mattmc_rust-linux-x64.so'
    assert sha(fail_native) == 'b7502e37aa98ba9d59469c96364154b71f4b635f69697dd967f4b5ba5772d298'
    native_images = {'published': published / 'native/mattmc_rust-linux-x64.so', 'combined': fail_native}
    selected_images = [('published', published), ('combined', failing)]
    if args.pre_s32:
        assert args.prerequisite
        parent = args.pre_s32.resolve(); parent_manifest = json.loads((parent / 'result.json').read_text())
        prerequisite = args.prerequisite.resolve()
        proof = json.loads((prerequisite / 'save-order-assessment.json').read_text())
        assert proof['completed'] and proof['guards_pass'] and proof['published_and_combined_same_far_loss']
        assert parent_manifest['passed'] and parent_manifest['base'] == '41d441c8b1e0afb6241441e472baf59b3c659774'
        native_images['pre-s32'] = parent / 'native/mattmc_rust-linux-x64.so'
        selected_images = [('pre-s32', parent)]
        images.update({str(parent / 'classes' / p): h for p, h in parent_manifest['classes'].items()})
        for path in [parent / 'result.json', parent / 'native-source-images.json', parent / 'exact-published-source.json', prerequisite / 'result.json', prerequisite / 'save-order-assessment.json']:
            images[str(path)] = sha(path)
        # No changed Java controller/observer is permitted between the witness
        # and necessity runs, including unchanged production-snapshot hooks.
        for source in paths[1:]:
            assert sha(source) == sha(prerequisite / 'observer-source' / source.name), source
    diagnostic_native = None
    if args.native_diagnostic:
        assert args.prerequisite and not args.pre_s32
        diagnostic = args.native_diagnostic.resolve()
        selection = json.loads((diagnostic / 'source-selection.json').read_text())
        build = json.loads((diagnostic / 'native-build-result.json').read_text())
        proof = json.loads((args.prerequisite.resolve() / 'save-order-assessment.json').read_text())
        assert proof['completed'] and proof['guards_pass'] and proof['published_and_combined_same_far_loss']
        assert selection['production_base'] == fail['head'] and selection['production_native'] == sha(fail_native)
        assert build['passed']
        diagnostic_native = diagnostic / 'native/mattmc_rust-linux-x64.so'
        assert sha(diagnostic_native) == build['native_sha256']
        images[str(fail_native)] = sha(fail_native)
        native_images['combined'] = diagnostic_native
        selected_images = [('combined', failing)]
        for path in [diagnostic / name for name in ['source-selection.json', 'native-build-result.json', 'diagnostic-source-images.json', 'diagnostic.patch']]:
            images[str(path)] = sha(path)
        images.update(json.loads((diagnostic / 'diagnostic-source-images.json').read_text()))
    for path in native_images.values():
        images[str(path)] = sha(path)
        symbols = subprocess.check_output(['nm', '-D', '--defined-only', str(path)])
        assert (b'mattmc_region_trace_' in symbols) == (path == diagnostic_native), path
    assert images[str(native_images['published'])] == pub['native_sha256']
    for path in [published / 'result.json', published / 'native-source-images.json', published / 'exact-published-source.json', failing / 'manifest.json', template / 'current-fresh/command.json']:
        images[str(path)] = sha(path)
    assert all(sha(path) == digest for path, digest in images.items())
    result = {'scope': __doc__, 'production_base': parent_manifest['base'] if args.pre_s32 else pub['base'], 'published_reference_base': pub['base'], 'input_images': images, 'runs': {}, 'completed': False, 'acceptance': False}
    def save(): (out / 'result.json').write_text(json.dumps(result, indent=2))
    save()
    try:
        archive = out / 'observer-source'; archive.mkdir()
        for p in paths: shutil.copy2(p, archive / p.name)
        classes = out / 'classes'; classes.mkdir()
        with (out / 'compile.log').open('w') as log:
            subprocess.run([str(java.with_name('javac')), '-g', '-cp', classpath, '-d', str(classes), *map(str, paths[1:])], check=True, stdout=log, stderr=subprocess.STDOUT)
        manifest = out / 'MANIFEST.MF'; manifest.write_text('Premain-Class: fallen.RegionSaveOrderBoot\n\n')
        agent = out / 'agent.jar'; subprocess.run([str(java.with_name('jar')), 'cfm', str(agent), str(manifest), '-C', str(classes), '.'], check=True, capture_output=True)
        if args.pre_s32:
            original_classes = {str(p.relative_to(args.prerequisite.resolve() / 'classes')): sha(p) for p in (args.prerequisite.resolve() / 'classes').rglob('*.class')}
            new_classes = {str(p.relative_to(classes)): sha(p) for p in classes.rglob('*.class')}
            assert original_classes == new_classes, 'Controller/observer bytecode changed between witness and necessity test'
            result['controller_bytecode_identical_to_original_witness'] = True
            result['pre_s32_source_base'] = parent_manifest['base']
            save()
        for side, image in selected_images:
            for mode in (['unload'] if args.native_diagnostic else ['early', 'unload']):
                key = side + '-' + mode; run = out / key; run.mkdir(); server = run / 'server'; server.mkdir()
                for name in ['eula.txt', 'server.properties']: shutil.copy2(template / 'current-fresh/server' / name, server / name)
                command = [a for a in original if not a.startswith('-javaagent:')]
                command = [('-Daudit.output=' + str(run)) if a.startswith('-Daudit.output=') else ('-Dmattmc.rust.natives.dir=' + str(native_images[side].parent)) if a.startswith('-Dmattmc.rust.natives.dir=') else a for a in command]
                index = command.index('-cp'); command[index + 1] = str(agent) + os.pathsep + (image / 'classpath.txt').read_text().strip()
                options = ['-javaagent:' + str(agent), '-Dregion.save.live=true', '-Dregion.save.order=true', '-Dsave.order.mode=' + mode]
                if args.native_diagnostic: options.append('-Dregion.save.native=true')
                command[index:index] = options
                (run / 'command.json').write_text(json.dumps(command, indent=2)); mapped = set(); start = time.monotonic()
                env = {k: v for k, v in os.environ.items() if k != 'MATTMC_REGION_LIGHT_TRACE'}
                if args.native_diagnostic: env['MATTMC_REGION_LIGHT_TRACE'] = '1'
                with (run / 'server.log').open('w') as log:
                    process = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT, env=env)
                    try:
                        while process.poll() is None:
                            try:
                                for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                    path = line.split()[-1]
                                    if 'mattmc_rust' in path and Path(path).is_file(): mapped.add(path)
                            except (FileNotFoundError, ProcessLookupError): pass
                            if time.monotonic() - start > 420: raise TimeoutError(key)
                            time.sleep(.5)
                    finally:
                        if process.poll() is None: process.terminate(); process.wait(timeout=20)
                trace = [json.loads(line) for line in (run / 'save-trace.jsonl').read_text().splitlines()]
                controller = json.loads((run / 'save-order-controller.json').read_text())
                summary = [e for e in trace if e['event'] == 'buffer-summary']; errors = [e for e in trace if e['event'] == 'observer-error']
                result['runs'][key] = {'exit': process.returncode, 'controller': controller, 'mapped_native': {p: sha(p) for p in mapped}, 'observer_errors': errors, 'buffer': summary, 'first_live_loss': [e for e in trace if e['event'] == 'first-live-loss']}
                if args.native_diagnostic:
                    native_summary = json.loads((run / 'native-buffer-summary.json').read_text())
                    result['runs'][key]['native_buffer'] = native_summary
                    assert native_summary['dropped'] == 0
                save()
                assert mapped == {str(native_images[side])} and not errors and len(summary) == 1 and summary[0]['dropped'] == 0
                assert process.returncode == 0
                if not controller['preconditions']:
                    result['stopped'] = {'run': key, 'reason': 'Unsupported legitimate fixture boundary; no forced state or additional retry.', 'detail': controller.get('failure')}
                    save(); print(result['stopped'], flush=True); return
                print(key, 'controlled preconditions reached', 'first_live_loss', bool(result['runs'][key]['first_live_loss']), flush=True)
        result['completed'] = True
    finally:
        result['guards_pass'] = all(Path(path).exists() and sha(path) == digest for path, digest in images.items()); save()
    assert result['completed'] and result['guards_pass']


if __name__ == '__main__': main()
