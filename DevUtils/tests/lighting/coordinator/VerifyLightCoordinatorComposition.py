#!/usr/bin/env python3
"""Focused engine/storage composition proof; explicit prototype setup adapter."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('threaded_campaign', HERE.parent / 'threaded/VerifyThreadedLighting.py')
common = importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)


def main():
    parser = common.arguments(__doc__)
    parser.add_argument('--prototype-current', action='store_true', help='Redirect only the private focused Current fixture to the unadmitted native composition owner')
    parser.add_argument('--references-only', action='store_true', help='Establish the unchanged Frozen/pre contract without running a candidate')
    args = parser.parse_args()
    assert not (args.prototype_current and args.references_only)
    assert args.control_classpath_file and args.control_native_dir and args.control_image_manifest
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    jdk = args.java_home / 'bin'
    native = common.ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    controls = json.loads(args.control_image_manifest.read_text())
    assert subprocess.check_output(['git', 'rev-parse', args.control_ref], cwd=common.ROOT, text=True).strip() == controls['base']
    classpaths = {s: p.read_text().strip() for s, p in (
        ('frozen', args.frozen_classpath_file), ('control', args.control_classpath_file), ('current', args.current_classpath_file))}
    if args.references_only:
        del classpaths['current']
    control_dirs = [Path(p) for p in classpaths['control'].split(os.pathsep) if Path(p).is_dir()]
    for relative, digest in controls['expected_base_class_image'].items():
        selected = next((p / relative for p in control_dirs if (p / relative).is_file()), None)
        assert selected and common.sha(selected) == digest, relative
    assert common.sha(args.control_native_dir / native.name) == controls['native_sha256']
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=common.FROZEN, text=True).strip() == common.FROZEN_HEAD
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=common.FROZEN)
    if not args.references_only:
        assert common.sha(native) == common.sha(common.ROOT / 'build/rust/target/release/libmattmc_rust.so')
    source = HERE / 'VerifyLightCoordinator.java'
    archived = out / 'driver-source'
    archived.mkdir()
    for path in HERE.glob('*'):
        if path.suffix in ('.java', '.py'):
            (archived / path.name).write_bytes(path.read_bytes())
    result = {'scope': 'Focused composition only; no normal production admission/performance claim',
        'prototype_current_setup_adapter': args.prototype_current, 'source_sha256': common.source_identity(),
        'references_only': args.references_only,
        'native_sha256': None if args.references_only else common.sha(native), 'control_native_sha256': controls['native_sha256'],
        'base': controls['base'], 'frozen_head': common.FROZEN_HEAD,
        'driver_sha256': {str(p.relative_to(common.ROOT)): common.sha(p) for p in HERE.glob('*') if p.suffix in ('.py', '.java')},
        'commands': [], 'passed': False, 'guards_pass': False}
    env = os.environ.copy()
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)

    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2))

    def run(command, log):
        result['commands'].append(command)
        with (out / log).open('w') as stream:
            code = subprocess.call(command, cwd=common.ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
        save()
        assert code == 0, f'{log} exit{code}'

    images = {}
    try:
        drivers = {}
        for side, cp in classpaths.items():
            target = out / ('driver-' + side)
            target.mkdir()
            run([str(jdk / 'javac'), '-nowarn', '-cp', cp, '-d', str(target), str(source)], side + '-compile.log')
            drivers[side] = target
        images = common.images({**classpaths, **{'driver-' + s: str(p) for s, p in drivers.items()}})
        result['class_images'] = images
        for side, cp in classpaths.items():
            native_dir = args.control_native_dir if side == 'control' else native.parent
            trace = out / (side + '-trace.txt')
            command = [str(jdk / 'java'), '-Xms256m', '-Xmx1g', '--enable-native-access=ALL-UNNAMED',
                '-Dcoordinator.frozen=' + str(side == 'frozen').lower(),
                '-Dcoordinator.prototypeComposition=' + str(side == 'current' and args.prototype_current).lower(),
                '-Dmattmc.rust.natives.dir=' + str(native_dir), '-cp', str(drivers[side]) + os.pathsep + cp,
                'net.minecraft.world.level.lighting.VerifyLightCoordinator', str(trace), 'composition']
            run(command, side + '.log')
            result[side] = {'sha256': common.sha(trace), 'rows': len(trace.read_text().splitlines())}
        result['passed'] = all(result[s] == result['frozen'] for s in classpaths) and result['frozen']['rows'] >= 30
        if args.prototype_current:
            log=(out / 'current.log').read_text()
            result['native_composition_route'] = 'NATIVE_COMPOSITION' in log and 'bindings=2 independent-work/context=true owners-after=0' in log
            result['passed'] &= result['native_composition_route']
        if not result['passed'] and 'current' in classpaths:
            frozen = (out / 'frozen-trace.txt').read_text().splitlines()
            current = (out / 'current-trace.txt').read_text().splitlines()
            result['first_differences'] = [{'row': i+1, 'frozen': a, 'current': b} for i, (a,b) in enumerate(zip(frozen,current)) if a != b][:20]
    finally:
        result['guards_pass'] = (bool(images) and common.source_identity() == result['source_sha256']
            and (args.references_only or common.sha(native) == result['native_sha256'])
            and common.sha(args.control_native_dir / native.name) == result['control_native_sha256']
            and all(common.sha(Path(p)) == digest for p, digest in images.items())
            and all(common.sha(common.ROOT / p) == digest for p, digest in result['driver_sha256'].items())
            and subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=common.FROZEN, text=True).strip() == common.FROZEN_HEAD
            and not subprocess.check_output(['git', 'status', '--porcelain'], cwd=common.FROZEN))
        save()
    assert result['passed'] and result['guards_pass'], str(out / 'result.json')
    print('PASS reference composition' if args.references_only else 'PASS composition', json.dumps(result['frozen']))


if __name__ == '__main__':
    main()
