#!/usr/bin/env python3
"""Trace selected actual save snapshots, encoded tapes and region writes.

This is an instrumented diagnostic, never latency or replacement acceptance.
It adds no lighting barrier and keeps each fresh/reopen world separate.
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


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--inputs', type=Path, required=True, help='Sealed candidate Main manifest/classpath directory')
    parser.add_argument('--template', type=Path, required=True, help='Preserved exact dimension fresh/reopen commands')
    parser.add_argument('--live-light', action='store_true', help='Bounded live-layer identity/removal trace; stops observations at first same-engine save-read loss')
    parser.add_argument('--native-diag', type=Path, help='Separate diagnostic overlay/build with exact original source-selection receipt')
    parser.add_argument('--fresh-only', action='store_true', help='Only the writer discriminator, without a second reader process')
    args = parser.parse_args()
    out = args.output.resolve()
    assert out.is_relative_to(ROOT / 'build')
    out.mkdir(parents=True, exist_ok=False)
    template = args.template.resolve()
    seal = args.inputs.resolve()
    pin = json.loads((seal / 'manifest.json').read_text())
    prior = json.loads((template / 'result.json').read_text())
    assert prior['guards_pass'] and prior['stage'] == 'dimensions' and prior['reopen']
    classpath = (seal / 'classpath.txt').read_text().strip()
    images = {**pin['java_sources'], **pin['rust_sources'], **pin['resource_images'], **pin['dependencies']}
    images.update({str(seal / 'classes' / path): digest for path, digest in pin['classes'].items()})
    selected_sources = {}
    if args.native_diag:
        diagnostic = args.native_diag.resolve()
        selection = json.loads((diagnostic / 'source-selection.json').read_text())
        build = json.loads((diagnostic / 'native-build-result.json').read_text())
        assert selection['all_selected_match_original_b750_sources'] and build['passed']
        assert selection['production_base'] == pin['head']
        # Closed Main is unchanged. Select exact original source bytes for
        # intervening upstream Java edits, never relabel those old classes.
        source_archive = out / 'selected-java-sources'
        for path, digest in pin['java_sources'].items():
            if sha(path) == digest:
                continue
            relative = Path(path).relative_to(ROOT)
            data = subprocess.check_output(['git', 'show', pin['head'] + ':' + str(relative)], cwd=ROOT)
            assert hashlib.sha256(data).hexdigest() == digest, path
            selected = source_archive / relative
            selected.parent.mkdir(parents=True, exist_ok=True); selected.write_bytes(data)
            del images[path]; images[str(selected)] = digest
            selected_sources[path] = {'selected': str(selected), 'sha256': digest, 'production_base': pin['head']}
        # The original seal also guards embedded native resources and an
        # unused historical target tree. Preserve those unchanged guards.
        for path, digest in selection['selected_source_images'].items():
            assert pin['rust_sources'][path] == digest
            del images[path]
        images.update(json.loads((diagnostic / 'diagnostic-source-images.json').read_text()))
        for name in ('source-selection.json', 'native-build-result.json', 'diagnostic.patch', 'diagnostic-source-images.json'):
            images[str(diagnostic / name)] = sha(diagnostic / name)
        native = diagnostic / 'native/mattmc_rust-linux-x64.so'
        assert sha(native) == build['native_sha256'] and sha(native) != selection['production_native']
        images[str(native)] = sha(native)
    sources = [Path(__file__).resolve(), Path(__file__).with_name('RegionSaveAgent.java'), Path(__file__).with_name('RegionSaveBoot.java'), Path(__file__).with_name('RegionLightTrace.java'), Path(__file__).with_name('RegionNativeTrace.java')]
    commands = {side: json.loads((template / side / 'command.json').read_text()) for side in ('current-fresh', 'current-reopen')}
    for side, command in commands.items():
        assert command[command.index('-cp') + 1] == classpath
        images[str(template / side / 'command.json')] = sha(template / side / 'command.json')
        for argument in command:
            if argument.startswith('-javaagent:'):
                path = Path(argument.removeprefix('-javaagent:'))
                images[str(path)] = sha(path)
            elif argument.startswith('-Dmattmc.rust.natives.dir='):
                library = Path(argument.split('=', 1)[1]) / 'mattmc_rust-linux-x64.so'
                images[str(library)] = sha(library)
    images.update({str(p): sha(p) for p in [*sources, seal / 'manifest.json', template / 'result.json']})
    assert all(sha(path) == digest for path, digest in images.items())
    result = {'selected_old_java_source_provenance': selected_sources, 'diagnostic_native': str(args.native_diag) if args.native_diag else None, 'scope': __doc__, 'input_images': images, 'runs': {}, 'completed': False}

    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2))

    save()
    try:
        (out / 'observer-source').mkdir()
        for source in sources:
            shutil.copy2(source, out / 'observer-source' / source.name)
        classes = out / 'classes'
        classes.mkdir()
        java = Path(commands['current-fresh'][3])
        with (out / 'compile.log').open('w') as log:
            subprocess.run([str(java.with_name('javac')), '-g', '-cp', classpath, '-d', str(classes), *map(str, sources[1:])],
                check=True, stdout=log, stderr=subprocess.STDOUT)
        manifest = out / 'MANIFEST.MF'
        manifest.write_text('Premain-Class: fallen.RegionSaveBoot\n\n')
        agent = out / 'agent.jar'
        subprocess.run([str(java.with_name('jar')), 'cfm', str(agent), str(manifest), '-C', str(classes), '.'], check=True, capture_output=True)
        for side, original in commands.items():
            if args.fresh_only and side == 'current-reopen':
                continue
            run = out / side
            run.mkdir()
            server = run / 'server'
            if side == 'current-fresh':
                server.mkdir()
                for name in ('eula.txt', 'server.properties'):
                    shutil.copy2(template / side / 'server' / name, server / name)
            else:
                shutil.copytree(out / 'current-fresh/server', server)
            command = original.copy()
            if args.native_diag:
                command = [('-Dmattmc.rust.natives.dir=' + str(diagnostic / 'native')) if a.startswith('-Dmattmc.rust.natives.dir=') else a for a in command]
                command.insert(command.index('-cp'), '-Dregion.save.native=true')
            command = [f'-Daudit.output={run}' if a.startswith('-Daudit.output=') else a for a in command]
            command.insert(command.index('-cp'), '-javaagent:' + str(agent))
            if args.live_light:
                command.insert(command.index('-cp'), '-Dregion.save.live=true')
            command[command.index('-cp') + 1] = str(agent) + os.pathsep + classpath
            (run / 'command.json').write_text(json.dumps(command, indent=2))
            mapped = set()
            with (run / 'server.log').open('w') as log:
                started = time.monotonic()
                process = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT,
                    env={**os.environ, **({'MATTMC_REGION_LIGHT_TRACE': '1'} if args.native_diag else {})})
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                path = line.split()[-1]
                                if 'mattmc_rust' in path and Path(path).is_file():
                                    mapped.add(path)
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() - started > 900:
                            raise TimeoutError(side)
                        time.sleep(.5)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait(timeout=20)
            events = [json.loads(line) for line in (run / 'save-trace.jsonl').read_text().splitlines()]
            result['runs'][side] = {'exit': process.returncode, 'mapped_native': {p: sha(p) for p in mapped},
                'events': len(events), 'observer_errors': [e for e in events if e['event'] == 'observer-error'],
                'actual_save_snapshots': sum(e['event'] == 'snapshot' and e['actualSave'] for e in events),
                'encoded': sum(e['event'] == 'encoded' for e in events),
                'region_write_returns': sum(e['event'] == 'writeTape-return' for e in events)}
            summaries = [e for e in events if e['event'] == 'buffer-summary']
            assert len(summaries) == 1 and summaries[0]['dropped'] == 0, summaries
            result['runs'][side]['java_buffer'] = summaries[0]
            if args.native_diag:
                native_summary = json.loads((run / 'native-buffer-summary.json').read_text())
                result['runs'][side]['native_buffer'] = native_summary
                assert native_summary['dropped'] == 0, native_summary
                native_events = [json.loads(line) for line in (run / 'native-trace.jsonl').read_text().splitlines()]
                from collections import Counter
                result['runs'][side]['native_event_counts'] = dict(Counter(row[1] for row in native_events))
                result['runs'][side]['adoption_modes'] = dict(Counter(e['mode'] for e in events if e['event'] == 'native-adopted'))
                bindings = [e for e in events if e['event'] == 'native-binding']
                adoptions = [e for e in events if e['event'] == 'native-adopted']
                publications = {row[0]: row for row in native_events if row[1] == 8}
                unique_bindings = {(e['ownerIncarnation'], e['nativeEngine'], e['storageIdentity']) for e in bindings}
                result['runs'][side]['binding_observations'] = len(bindings)
                result['runs'][side]['unique_bindings'] = len(unique_bindings)
                assert len(unique_bindings) == 2 and adoptions and publications
                assert all(e['publication'] in publications and publications[e['publication']][2] == e['ownerIncarnation'] and publications[e['publication']][3] == e['nativeEngine'] for e in adoptions)
                assert mapped == {str(native)}, {'expected': str(native), 'actual': sorted(mapped)}
            result['runs'][side]['live_events'] = sum(e['event'].startswith('live-') for e in events)
            result['runs'][side]['first_live_loss'] = [e for e in events if e['event'] == 'first-live-loss']
            save()
            assert process.returncode == 0 and (run / 'complete.json').exists()
            assert mapped and all(images[p] == sha(p) for p in mapped)
            assert not result['runs'][side]['observer_errors']
            assert result['runs'][side]['actual_save_snapshots'] > 0 and result['runs'][side]['encoded'] > 0
            assert result['runs'][side]['region_write_returns'] > 0
            print(side, {k: v for k, v in result['runs'][side].items() if k != 'mapped_native'}, flush=True)
        # Every field remains compared. A diagnostic mismatch is retained, not
        # turned into a pass by successful instrumentation.
        import sys
        sys.path.insert(0, str(ROOT / 'DevUtils/tests/worldgen'))
        import VerifyFullGeneration as full
        if not args.fresh_only:
            result['fresh_reopen'] = full.compare(json.loads((out / 'current-fresh/regions.json').read_text()),
                json.loads((out / 'current-reopen/regions.json').read_text()))
        else:
            result['fresh_reopen'] = {'not_run': 'Bounded fresh-writer discriminator only; original failed fresh/reopen evidence remains unchanged.'}
        result['completed'] = True
    finally:
        result['guards_pass'] = all(Path(path).exists() and sha(path) == digest for path, digest in images.items())
        save()
    assert result['completed'] and result['guards_pass']


if __name__ == '__main__':
    main()
