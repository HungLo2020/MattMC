#!/usr/bin/env python3
"""Short matched request-lifecycle resource screen after a successful request oracle.

The oracle supplies pinned Frozen/pre/Current classpaths and native origins. All
inputs and compiled observer bytes remain guarded; raw samples are preserved.
This intermediate screen does not grant final subsystem performance acceptance.
"""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import time
from VerifyLightRequests import ROOT, HERE, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--oracle', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--cpu', type=int, default=min(os.sched_getaffinity(0)))
    parser.add_argument('--steps', type=int, default=4)
    parser.add_argument('--samples', type=int, default=12)
    args = parser.parse_args()
    assert args.steps > 0 and args.samples >= 8
    oracle_path = args.oracle.resolve() / 'result.json'
    oracle = json.loads(oracle_path.read_text())
    assert oracle['passed'] and oracle['guards_pass'] and all(oracle['traces_match'].values())
    assert set(oracle['traces_match']) == {'ThreadedRequestContracts', 'ThreadedRequestFailures', 'ThreadedRequestLifetimes', 'ThreadedUnloadCustody'}
    inputs = dict(oracle['inputs'])
    inputs[str(oracle_path)] = sha(oracle_path)
    for path in [Path(__file__).resolve(), HERE / 'VerifyLightRequests.py', HERE / 'BenchmarkLightRequests.java']:
        inputs[str(path)] = sha(path)
    assert all(sha(path) == digest for path, digest in inputs.items()), 'Changed oracle input'
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    classes = out / 'classes'
    classes.mkdir()
    frozen_command = oracle['runs']['ThreadedRequestContracts-frozen']['command']
    frozen_cp = frozen_command[frozen_command.index('-cp') + 1]
    compile_command = [str(args.java_home / 'bin/javac'), '-g', '-cp', frozen_cp, '-d', str(classes),
                       str(HERE / 'VerifyThreadedLighting.java'), str(HERE / 'BenchmarkLightRequests.java')]
    with (out / 'compile.log').open('w') as log:
        subprocess.run(compile_command, check=True, stdout=log, stderr=subprocess.STDOUT)
    for path in classes.rglob('*.class'):
        if path.name.startswith('VerifyThreadedLighting'):
            assert sha(path) == sha(args.oracle.resolve() / 'classes' / path.relative_to(classes)), 'Helper bytes differ from oracle'
        inputs[str(path)] = sha(path)
    result = {'passed': False, 'scope': 'Short intermediate request lifecycle/resource screen; no final 10%/25% acceptance claim',
              'oracle': str(oracle_path), 'oracle_sha256': sha(oracle_path), 'profile': oracle['profile'],
              'cpu': args.cpu, 'steps': args.steps, 'samples': args.samples, 'inputs': inputs, 'runs': {}}
    env = {key: value for key, value in os.environ.items()
           if key not in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MATTMC_RUST_NATIVE_DIR')}
    sample_pattern = re.compile(r'(?:^|\[STDOUT\]: )SAMPLE (\d+) (\d+) (\d+) (\d+) (\d+) ([a-f0-9]{64}) (\d+) (\d+)$')
    try:
        for side in ('frozen', 'pre', 'current'):
            original = oracle['runs']['ThreadedRequestContracts-' + side]['command']
            cp = original[original.index('-cp') + 1]
            properties = [arg for arg in original if arg.startswith('-D')]
            # Pre has no request exports; Current's existing physical counters are enabled.
            properties = [arg for arg in properties if not arg.startswith('-DthreadedLight.nativeRequests=')]
            properties.append('-DthreadedLight.nativeRequests=' + str(side == 'current').lower())
            command = ['taskset', '-c', str(args.cpu), str(args.java_home / 'bin/java'), '-Xms512m', '-Xmx2g',
                       '-Xbatch', '-XX:-TieredCompilation', '--enable-native-access=ALL-UNNAMED', *properties,
                       '-cp', str(classes) + os.pathsep + cp, 'net.minecraft.server.level.BenchmarkLightRequests',
                       str(args.steps), str(args.samples)]
            mapped = set()
            start = time.monotonic()
            with (out / (side + '.log')).open('w') as log:
                process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, env=env, cwd=ROOT)
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                name = line.split()[-1]
                                if 'mattmc_rust' in name:
                                    mapped.add(name)
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() - start > 240:
                            raise TimeoutError(side)
                        time.sleep(.05)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait()
            log = (out / (side + '.log')).read_text()
            samples = []
            for line in log.splitlines():
                match = sample_pattern.search(line)
                if match:
                    index, ns, allocated, jit, gc, state, dispatch, light = match.groups()
                    samples.append({'index': int(index), 'ns': int(ns), 'java_bytes': int(allocated),
                                    'jit_ms': int(jit), 'gc_count': int(gc), 'state': state,
                                    'dispatch': int(dispatch), 'light': int(light)})
            quiet = [row for row in samples if row['jit_ms'] == 0 and row['gc_count'] == 0]
            expected_mapping = set(oracle['runs']['ThreadedRequestContracts-' + side]['mapped_native'])
            run = {'command': command, 'exit': process.returncode, 'seconds': time.monotonic() - start,
                   'mapped_native': {path: sha(path) for path in mapped}, 'native_mapping_matches': mapped == expected_mapping,
                   'samples': samples, 'quiet_samples': len(quiet),
                   'resource_rows': [line for line in log.splitlines() if 'RESOURCE SAMPLE ' in line],
                   'release_rows': [line for line in log.splitlines() if 'RELEASE request_owner_delta=' in line]}
            result['runs'][side] = run
            assert process.returncode == 0 and len(samples) == args.samples and len(quiet) >= 8, 'Failed/noisy ' + side
            assert run['native_mapping_matches'] and run['release_rows'], 'Missing origin/release witness'
            run['swap_kib'] = [int(value) for row in run['resource_rows']
                               for value in re.findall(r'VmSwap:\s+(\d+)\s+kB', row)]
            assert len(run['swap_kib']) == args.samples and not any(run['swap_kib']), 'Missing resource/swap-free witness'
            run['median_ns_per_cycle'] = statistics.median(row['ns'] for row in quiet) / args.steps
            run['median_java_bytes_per_cycle'] = statistics.median(row['java_bytes'] for row in quiet) / args.steps
            run['work'] = sorted(set((row['state'], row['dispatch'], row['light']) for row in samples))
            (out / 'result.json').write_text(json.dumps(result, indent=2))
        runs = result['runs']
        result['matched_work'] = runs['frozen']['work'] == runs['pre']['work'] == runs['current']['work']
        result['current_over_frozen'] = runs['current']['median_ns_per_cycle'] / runs['frozen']['median_ns_per_cycle']
        result['current_over_pre'] = runs['current']['median_ns_per_cycle'] / runs['pre']['median_ns_per_cycle']
        result['passed'] = result['matched_work']
    finally:
        result['guards_pass'] = all(sha(path) == digest for path, digest in inputs.items())
        result['passed'] = result['passed'] and result['guards_pass']
        (out / 'result.json').write_text(json.dumps(result, indent=2))
    print({key: value for key, value in result.items() if key not in ('inputs', 'runs')})
    assert result['passed'], 'Unmatched work or changed inputs; no timing pass'


if __name__ == '__main__':
    main()
