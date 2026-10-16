#!/usr/bin/env python3
"""Require native selection plus heap publication tags to reject a reused pending binding.

Compiles an unchanged and mutated callable adapter into isolated class overlays.
Production sources/classes/library stay unchanged; compilation failure is not a kill.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

SOURCES = Path(__file__).resolve().parent
sys.path.insert(0, str(SOURCES.parent))
import VerifyFullGeneration as full
import VerifyEndFeatures as end


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True, help='compiled test runtime classpath')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = full.ROOT
    args.output = args.output.resolve()
    if not args.output.is_relative_to(root / 'build'): raise ValueError('output must be under build/')
    args.output.mkdir(parents=True, exist_ok=False)
    cp = args.classpath_file.read_text().strip()
    before = end.inputs(root)
    source = root / 'src/main/java/net/minecraft/server/level/NativeChunkGeneration.java'
    text = source.read_text()
    before_check = '(long)TAG.getAcquire(publicationTags, row) != token'
    after_check = '(long)TAG.getAcquire(publicationTags, row) == token'
    start = text.index('        @Nullable private Object readPayload(')
    stop = text.index('        /** Invalidate before clearing:', start)
    payload = text[start:stop]
    if payload.count(before_check) != 1 or payload.count(after_check) != 1:
        raise ValueError('two-slot publication checks changed; inspect the mutation')
    ready = 'TAG.setRelease(publicationTags, nextRow, word);'
    selected = 'int row = bindingRow(field, token);'
    if text.count(ready) != 1 or text.count(selected) != 1:
        raise ValueError('two-slot publication/selection boundary changed')
    # Hook only isolated copies: hold an old native selection, publish B, then
    # pause C after reusing the old Java location but before native commit.
    observed = text.replace(ready, ready + '\n                NativeGenerationSlotReadTest.afterReferencePublication(this, field, word);')
    observed = observed.replace(selected, 'NativeGenerationSlotReadTest.afterNativeSelection(this, field, token);\n                    ' + selected)
    start = observed.index('        @Nullable private Object readPayload(')
    stop = observed.index('        /** Invalidate before clearing:', start)
    mutant = observed[:start] + observed[start:stop].replace(before_check, 'false').replace(after_check, 'true') + observed[stop:]
    runner = args.output / 'runner'; runner.mkdir()
    subprocess.run([str(full.JDK / 'javac'), '-cp', cp, '-d', str(runner), str(SOURCES / 'GenerationContractRunner.java')], check=True)
    test = 'net.minecraft.server.level.NativeGenerationSlotReadTest'
    test_files = sorted((root / 'build/classes/java/test/net/minecraft/server/level').glob('NativeGenerationSlotReadTest*.class'))
    if not test_files: raise ValueError('compile the slot contract first')
    tests_before = {str(path): full.sha(path) for path in test_files}
    rows = []
    for label, body in [('unchanged', observed), ('missing-epoch', mutant)]:
        out = args.output / label; out.mkdir()
        copied = out / 'source/net/minecraft/server/level/NativeChunkGeneration.java'
        copied.parent.mkdir(parents=True); copied.write_text(body)
        classes = out / 'classes'; classes.mkdir()
        with (out / 'javac.log').open('w') as log:
            subprocess.run([str(full.JDK / 'javac'), '-cp', cp, '-d', str(classes), str(copied)],
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        command = [str(full.JDK / 'java'), '--enable-native-access=ALL-UNNAMED',
            # Same bundled-JDK25 Byte Buddy compatibility as the Gradle tests.
            '-Dnet.bytebuddy.experimental=true',
            '-Dgeneration.slotReadHook=true',
            '-Dmattmc.rust.natives.dir=' + str(root / 'build/rust/native'),
            '-cp', str(classes) + os.pathsep + str(runner) + os.pathsep + cp, 'GenerationContractRunner', test]
        (out / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        mappings = {}
        with (out / 'test.log').open('w') as log:
            proc = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 45
                while proc.poll() is None:
                    try:
                        for line in Path(f'/proc/{proc.pid}/maps').read_text().splitlines():
                            if 'mattmc' in line and '.so' in line:
                                path = line.split()[-1]
                                if path not in mappings and Path(path).is_file(): mappings[path] = full.sha(Path(path))
                    except (FileNotFoundError, ProcessLookupError): pass
                    if time.monotonic() > deadline: raise TimeoutError('slot contract timed out')
                    time.sleep(.1)
            finally:
                if proc.poll() is None: proc.kill(); proc.wait()
        log = (out / 'test.log').read_text()
        mapped = before['native_sha256'] in mappings.values()
        killed = proc.returncode != 0 and 'uncommitted binding exposed before native publication' in log
        rows.append({'case': label, 'exit': proc.returncode, 'source_sha256': full.sha(copied),
                     'mapped_expected_native': mapped, 'native_mappings': mappings, 'intended_failure': killed})
        if not mapped: raise RuntimeError('native route/image not observed: ' + label)
        if label == 'unchanged' and proc.returncode != 0: raise RuntimeError('unchanged isolated adapter did not reproduce the production positive')
        if label != 'unchanged' and not killed: raise RuntimeError('mutation was not detected by its intended witness: ' + label)
        print(label, 'pass' if proc.returncode == 0 else 'intended mutation detected', flush=True)
    after = end.inputs(root)
    tests_after = {str(path): full.sha(path) for path in test_files}
    if before != after or tests_before != tests_after: raise RuntimeError('production image or slot contract changed during mutation check')
    (args.output / 'result.json').write_text(json.dumps({'passed': True, 'before': before, 'after': after,
        'test_images': tests_before, 'rows': rows}, indent=2) + '\n')


if __name__ == '__main__': main()
