#!/usr/bin/env python3
"""Bounded matched early-save/unload-save comparison against immutable Frozen.

Compiles one portable observer jar against Frozen's actual API and uses those
same bytes for both runtimes. Production sources, flags and native owners stay
unchanged; no baseline is regenerated or silently advanced.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[4]
DIAGNOSTICS = ROOT / 'DevUtils/tests/worldgen/region/diagnostics'
NAMES = ['RegionSaveAgent.java', 'RegionSaveBoot.java', 'RegionLightTrace.java', 'RegionSaveOrder.java', 'RegionSaveOrderBoot.java']


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def portable_sources(folder):
    folder.mkdir()
    sources = []
    transforms = {}
    for name in NAMES:
        source = DIAGNOSTICS / name; text = source.read_text(); original = text
        if name == 'RegionSaveAgent.java':
            for line in ['            if (Boolean.getBoolean("region.save.native")) row.put("nativeSequence", RegionNativeTrace.marker(order));\n', '            if (Boolean.getBoolean("region.save.native")) RegionNativeTrace.flush();\n']:
                assert line in text; text = text.replace(line, '', 1)
            old = 'var encoded = (SerializableChunkData.Encoded)value;'
            assert old in text
            text = text.replace(old, 'var encoded = value;\n            byte[] tape = (byte[])value.getClass().getMethod("tape").invoke(value);\n            Object failure = value.getClass().getMethod("failure").invoke(value);', 1)
            text = text.replace('encoded.failure()', 'failure').replace('encoded.tape()', 'tape')
            text = text.replace('net.minecraft.nbt.NativeNbtRegionAccess.readTape(', 'readTape(')
            point = '    public static void encoded(Object value, Object source) {'
            assert point in text
            text = text.replace(point, '    private static CompoundTag readTape(byte[] tape) throws Exception {\n        return (CompoundTag)Class.forName("net.minecraft.nbt.NativeNbtRegionAccess").getMethod("readTape", byte[].class).invoke(null, (Object)tape);\n    }\n' + point, 1)
        if name == 'RegionLightTrace.java':
            old = 'layer.defaultValueForNativeLight()'; assert old in text
            text = text.replace(old, 'layer.get(0, 0, 0)', 1)
        target = folder / name; target.write_text(text); sources.append(target)
        transforms[name] = {'template': str(source), 'original_sha256': hashlib.sha256(original.encode()).hexdigest(), 'portable_sha256': sha(target), 'changed': original != text}
    helper = ROOT / 'DevUtils/tests/worldgen/full_generation/FullGenAudit.java'
    target = folder / helper.name; shutil.copy2(helper, target); sources.append(target)
    return sources, transforms


def compare_saved_bytes(folder, runs):
    """Compare every sampled saved field, including bytes that remain present."""
    saved, inputs = {}, {}
    for side, run in runs.items():
        trace = Path(run.get('evidence_directory', str(folder / side))) / 'save-trace.jsonl'
        inputs[str(trace)] = sha(trace)
        rows = {}
        for event in map(json.loads, trace.read_text().splitlines()):
            if event['event'] == 'snapshot' and event['actualSave']:
                assert event['chunk'] not in rows, ('Multiple actual target saves', side, event['chunk'])
                rows[event['chunk']] = {key: {name: value.get(name) for name in ['present', 'length', 'sha256']} for key, value in event['sections'].items()}
        assert len(rows) == 8
        saved[side] = rows
    modes = {}
    for mode in ['early', 'unload']:
        reference, candidate = saved['frozen-' + mode], saved['current-' + mode]
        differences = {key: {'frozen': reference.get(key), 'current': candidate.get(key)} for key in reference.keys() | candidate.keys() if reference.get(key) != candidate.get(key)}
        modes[mode] = {'all_sampled_saved_bytes_match_frozen': not differences, 'differences': differences}
    return {'modes': modes, 'input_images': inputs}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--frozen-pin', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--template', type=Path, required=True)
    parser.add_argument('--reuse-frozen', type=Path, help='Reuse guarded completed Frozen runs and their exact observer bytecode')
    args = parser.parse_args()
    out = args.output.resolve(); assert out.is_relative_to(ROOT / 'build'); out.mkdir(parents=True, exist_ok=False)
    frozen_pin = args.frozen_pin.resolve(); candidate = args.candidate.resolve(); template = args.template.resolve()
    frozen = json.loads((frozen_pin / 'manifest.json').read_text()); current = json.loads((candidate / 'result.json').read_text())
    assert frozen['frozen_head'] == '7a4d181717dc0c7da9086f1a17687dfd522ce417' and current['passed']
    frozen_cp = (frozen_pin / 'frozen-classpath.txt').read_text().strip(); current_cp = (candidate / 'classpath.txt').read_text().strip()
    frozen_classes = Path(frozen_cp.split(os.pathsep)[0]); frozen_root = frozen_classes.parents[3]
    assert subprocess.check_output(['git', '-C', str(frozen_root), 'rev-parse', 'HEAD'], text=True).strip() == frozen['frozen_head']
    assert not subprocess.check_output(['git', '-C', str(frozen_root), 'status', '--porcelain'], text=True)
    images = {str(frozen_classes / path): digest for path, digest in frozen['frozen_classes'].items()}
    images.update(frozen['frozen_resource_images'])
    images.update({str(candidate / 'classes' / path): digest for path, digest in current['classes'].items()})
    main_pin = json.loads((ROOT / 'build/decoration-region/s0031-s32-inputs-1/manifest.json').read_text()); images.update(main_pin['resource_images'])
    for entry in set(frozen_cp.split(os.pathsep) + current_cp.split(os.pathsep)):
        p = Path(entry)
        if p.is_file(): images[str(p)] = sha(p)
    sources, transforms = portable_sources(out / 'observer-source')
    for p in [*sources, Path(__file__).resolve(), *[DIAGNOSTICS / name for name in NAMES], ROOT / 'DevUtils/tests/worldgen/full_generation/FullGenAudit.java', frozen_pin / 'manifest.json', candidate / 'result.json']:
        images[str(p)] = sha(p)
    native = candidate / 'native/mattmc_rust-linux-x64.so'; images[str(native)] = sha(native)
    assert sha(native) == current['native_sha256']
    assert b'mattmc_region_trace_' not in subprocess.check_output(['nm', '-D', '--defined-only', str(native)])
    assert all(sha(path) == digest for path, digest in images.items())
    result = {'scope': __doc__, 'frozen_source': frozen['frozen_head'], 'candidate_source': current['base'], 'input_images': images, 'portable_adaptations': transforms, 'runs': {}, 'completed': False, 'acceptance': False}
    reused = None
    if args.reuse_frozen:
        previous = args.reuse_frozen.resolve()
        reused = json.loads((previous / 'result.json').read_text())
        assert reused['completed'] and reused['guards_pass'] and reused['frozen_source'] == frozen['frozen_head']
        assert reused['portable_adaptations'] == transforms
        for mode in ['early', 'unload']:
            key = 'frozen-' + mode
            row = reused['runs'][key]
            assert row['exit'] == 0 and row['controller']['preconditions'] and not row['observer_errors'] and not row['mapped_native']
            assert len(row['buffer']) == 1 and row['buffer'][0]['dropped'] == 0
            for path in (previous / key).rglob('*'):
                if path.is_file(): images[str(path)] = sha(path)
            result['runs'][key] = {**row, 'evidence_directory': str(previous / key)}
        for path in [previous / 'result.json', previous / 'agent.jar', *list((previous / 'classes').rglob('*.class'))]:
            images[str(path)] = sha(path)
        for path, digest in reused['input_images'].items():
            if str(frozen_root) in path or path in [str(frozen_pin / 'manifest.json')]:
                assert sha(path) == digest
        result['frozen_reuse'] = {'source_result': str(previous / 'result.json'), 'impact': 'Frozen classes, resources, dependencies, fixture runs and observer source/bytecode guarded; only Current production image changes.'}
    def save(): (out / 'result.json').write_text(json.dumps(result, indent=2))
    save()
    original = json.loads((template / 'current-fresh/command.json').read_text()); java = Path(original[3])
    try:
        classes = out / 'classes'; classes.mkdir()
        agent = out / 'agent.jar'
        if reused:
            shutil.copytree(previous / 'classes', classes, dirs_exist_ok=True)
            shutil.copy2(previous / 'agent.jar', agent)
        else:
            with (out / 'compile.log').open('w') as log:
                subprocess.run([str(java.with_name('javac')), '-g', '-cp', frozen_cp, '-d', str(classes), *map(str, sources)], check=True, stdout=log, stderr=subprocess.STDOUT)
            manifest = out / 'MANIFEST.MF'; manifest.write_text('Premain-Class: fallen.RegionSaveOrderBoot\n\n')
            subprocess.run([str(java.with_name('jar')), 'cfm', str(agent), str(manifest), '-C', str(classes), '.'], check=True, capture_output=True)
        result['same_observer_class_images'] = {str(p.relative_to(classes)): sha(p) for p in classes.rglob('*.class')}; save()
        if reused: assert result['same_observer_class_images'] == reused['same_observer_class_images']
        images[str(agent)] = sha(agent)
        for side, classpath in [('frozen', frozen_cp), ('current', current_cp)]:
            if side == 'frozen' and reused: continue
            for mode in ['early', 'unload']:
                key = side + '-' + mode; run = out / key; run.mkdir(); server = run / 'server'; server.mkdir()
                for name in ['eula.txt', 'server.properties']: shutil.copy2(template / 'current-fresh/server' / name, server / name)
                command = [a for a in original if not a.startswith(('-javaagent:', '-Dmattmc.rust.natives.dir='))]
                command = [('-Daudit.output=' + str(run)) if a.startswith('-Daudit.output=') else a for a in command]
                index = command.index('-cp'); command[index + 1] = str(agent) + os.pathsep + classpath
                extra = ['-javaagent:' + str(agent), '-Dregion.save.live=true', '-Dregion.save.order=true', '-Dsave.order.mode=' + mode]
                if side == 'current': extra.append('-Dmattmc.rust.natives.dir=' + str(native.parent))
                command[index:index] = extra; (run / 'command.json').write_text(json.dumps(command, indent=2))
                mapped = set(); start = time.monotonic(); env = {k: v for k, v in os.environ.items() if k not in ['MATTMC_REGION_LIGHT_TRACE', 'MATTMC_RUST_NATIVE_DIR']}
                with (run / 'server.log').open('w') as log:
                    process = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT, env=env)
                    try:
                        while process.poll() is None:
                            try:
                                for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                    p = line.split()[-1]
                                    if 'mattmc_rust' in p and Path(p).is_file(): mapped.add(p)
                            except (FileNotFoundError, ProcessLookupError): pass
                            if time.monotonic() - start > 420: raise TimeoutError(key)
                            time.sleep(.5)
                    finally:
                        if process.poll() is None: process.terminate(); process.wait(timeout=20)
                trace = [json.loads(line) for line in (run / 'save-trace.jsonl').read_text().splitlines()]
                control = json.loads((run / 'save-order-controller.json').read_text()); summaries = [e for e in trace if e['event'] == 'buffer-summary']; errors = [e for e in trace if e['event'] == 'observer-error']
                result['runs'][key] = {'exit': process.returncode, 'controller': control, 'buffer': summaries, 'observer_errors': errors, 'mapped_native': {p: sha(p) for p in mapped}, 'first_live_loss': [e for e in trace if e['event'] == 'first-live-loss']}; save()
                assert process.returncode == 0 and not errors and len(summaries) == 1 and summaries[0]['dropped'] == 0
                assert mapped == ({str(native)} if side == 'current' else set())
                if not control['preconditions']:
                    result['stopped'] = {'run': key, 'unsupported_fixture_boundary': control.get('failure')}; save(); print(result['stopped'], flush=True); return
                print(key, 'preconditions true; first_live_loss', bool(result['runs'][key]['first_live_loss']), flush=True)
        result['saved_reference_bytes'] = compare_saved_bytes(out, result['runs'])
        assert all(row['all_sampled_saved_bytes_match_frozen'] for row in result['saved_reference_bytes']['modes'].values())
        result['completed'] = True
    finally:
        result['guards_pass'] = all(Path(path).exists() and sha(path) == digest for path, digest in images.items()); save()
    assert result['completed'] and result['guards_pass']


if __name__ == '__main__': main()
