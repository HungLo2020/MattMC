#!/usr/bin/env python3
"""Execute six semantic faults through the same portable production observer.

Production sources/images are never edited. An isolated shared library compiles
the exact std-only task_batch owner; a test-only bridge copy binds its exports.
Its positive trace must match the complete real-release/Frozen oracle first.
Other dispatch/propagation methods continue using the real production library.
This is correctness mutation evidence, never acceptance timing.
"""
import json
from pathlib import Path
import re
import shutil
import subprocess
from VerifyThreadedLighting import Campaign, HERE, ROOT, SOURCE, arguments, sha, source_identity


def replace_once(source, old, new):
    assert source.count(old) == 1, ('Mutation source changed', old)
    return source.replace(old, new, 1)


def main():
    parser = arguments(__doc__)
    parser.add_argument('--oracle', type=Path, required=True)
    args = parser.parse_args()
    reference = json.loads((args.oracle / 'result.json').read_text())
    assert reference['passed'] and reference['guards_pass']
    assert reference['source_sha256'] == source_identity()
    expected = reference['current']['trace_sha256']
    assert sha(args.oracle / 'frozen-trace.txt') == expected
    campaign = Campaign(args)
    assert campaign.result['native_sha256'] == reference['native_sha256']
    campaign.result.update(scope=__doc__, mutations=[], reference_trace_sha256=expected)
    core_path = ROOT / 'src/main/rust/world/level/lighting/task_batch'
    core = (core_path / 'mod.rs').read_text()
    ffi = (core_path / 'ffi.rs').read_text()
    bridge = (ROOT / 'src/main/java/net/minecraft/server/level/NativeLightTasks.java').read_text()
    threaded = (ROOT / SOURCE).read_text()
    binding = 'return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tasks_" + suffix, descriptor);'
    bridge = replace_once(bridge, binding, 'return Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup('
        'System.getProperty("threadedLight.testOwnerLibrary"),Arena.global()).find("mattmc_light_tasks_" + suffix).orElseThrow(),descriptor);')
    try:
        for name in ('positive', 'early-post', 'captured-pre-snapshot', 'commit-failed-callback',
                     'wrong-cap', 'finally-reset', 'ignore-admission'):
            target = campaign.output / name
            target.mkdir()
            rust = core
            native_ffi = ffi
            java = bridge
            engine = threaded
            if name == 'early-post':
                rust = replace_once(rust, 'if !task.post {', 'if true { // deliberately execute POST before propagation')
            elif name == 'captured-pre-snapshot':
                rust = replace_once(rust, '#[derive(Clone, Copy)]\nstruct Cursor {', '#[derive(Clone)]\nstruct Cursor {\n    captured: Vec<Task>,')
                rust = replace_once(rust, 'limit: self.tasks.len().min(BATCH),', 'limit: self.tasks.len().min(BATCH),\n            captured: self.tasks.iter().copied().collect(),')
                rust = replace_once(rust, 'while c.position < self.tasks.len() && c.visited < c.limit {', 'while c.position < c.captured.len() && c.visited < c.limit {')
                rust = replace_once(rust, 'let task = self.tasks[c.position];', 'let task = c.captured[c.position];')
            elif name == 'commit-failed-callback':
                java = replace_once(java, '                    task.run();',
                    '                    try { task.run(); } catch (RuntimeException | Error failure) { change(2,cursor,0);retire();throw failure; }')
            elif name == 'wrong-cap':
                rust = replace_once(rust, 'const BATCH: usize = 1000;', 'const BATCH: usize = 999;')
            elif name == 'finally-reset':
                engine = replace_once(engine, '\t\t\t\tthis.runUpdate();\n\t\t\t\tthis.lightTasks.finishSchedule();',
                    '\t\t\t\ttry { this.runUpdate(); } finally { this.lightTasks.finishSchedule(); }')
            elif name == 'ignore-admission':
                pattern = r'&& o\.header\s*\.scheduled\s*\.compare_exchange\(0, 1, Ordering::SeqCst, Ordering::SeqCst\)\s*\.is_ok\(\)'
                native_ffi, count = re.subn(pattern, '&& { o.header.scheduled.store(1,Ordering::SeqCst); true }', native_ffi)
                assert count == 1
            source = target / 'owner-source'
            source.mkdir()
            (source / 'mod.rs').write_text(rust)
            (source / 'ffi.rs').write_text(native_ffi)
            shutil.copy2(core_path / 'tests.rs', source / 'tests.rs')
            library = target / 'libthreaded_light_owner.so'
            campaign.run(['rustc', '--edition', '2021', '--crate-name', 'threaded_light_owner',
                '--crate-type', 'cdylib', '-C', 'opt-level=3', '-C', 'panic=abort',
                str(source / 'mod.rs'), '-o', str(library)], name + '-rust-compile.log')
            java_source = target / 'java-source'
            java_source.mkdir()
            (java_source / 'NativeLightTasks.java').write_text(java)
            (java_source / 'ThreadedLevelLightEngine.java').write_text(engine)
            classes = target / 'classes'
            classes.mkdir()
            campaign.run([str(campaign.jdk / 'javac'), '-nowarn', '-cp', campaign.classpaths['current'],
                '-d', str(classes), str(java_source / 'NativeLightTasks.java'), str(java_source / 'ThreadedLevelLightEngine.java')],
                name + '-java-compile.log')
            seals = {str(p): sha(p) for folder in (source, java_source, classes) for p in folder.rglob('*') if p.is_file()}
            seals[str(library)] = sha(library)
            trace = target / 'trace.txt'
            command = campaign.java('current', 'VerifyThreadedLighting', [trace])
            cp = command.index('-cp') + 1
            command[cp] = str(classes) + ':' + command[cp]
            command.insert(cp - 1, '-DthreadedLight.testOwnerLibrary=' + str(library))
            campaign.run(command, name + '-run.log')
            text = trace.read_text().splitlines()
            assert len(text) > 40000, 'An early crash/termination is not a killed semantic control'
            assert all(sha(Path(p)) == digest for p, digest in seals.items()), 'Mutant image changed'
            actual = sha(trace)
            row = {'name': name, 'trace_sha256': actual, 'events': len(text), 'image_seals': seals,
                   'positive_equivalent': actual == expected, 'killed': actual != expected}
            if name != 'positive':
                baseline = (args.oracle / 'frozen-trace.txt').read_text().splitlines()
                row['first_differences'] = [{'line': i + 1, 'frozen': a, 'mutant': b}
                    for i, (a, b) in enumerate(zip(baseline, text)) if a != b][:8]
                assert row['killed'] and row['first_differences'], 'Semantic mutation survived'
            else:
                assert row['positive_equivalent'], 'Isolated-owner route differs from real production release'
            campaign.result['mutations'].append(row)
            campaign.save()
            print(name, 'equivalent' if name == 'positive' else 'KILLED', len(text), flush=True)
        campaign.result['passed'] = len(campaign.result['mutations']) == 7
    finally:
        campaign.seal()
    assert campaign.result['passed'] and campaign.result['guards_pass']


if __name__ == '__main__':
    main()
