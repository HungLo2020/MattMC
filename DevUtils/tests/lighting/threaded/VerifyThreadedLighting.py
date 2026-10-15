#!/usr/bin/env python3
"""Three separately compiled observers of actual Frozen/control/current engines.

Build release classes first. This runs neither Gradle nor a copied coordinator.
The actual dedicated-server lifecycle is a separate gate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'
FROZEN_HEAD = '7a4d181717dc0c7da9086f1a17687dfd522ce417'
HERE = Path(__file__).resolve().parent
SOURCE = 'src/main/java/net/minecraft/server/level/ThreadedLevelLightEngine.java'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_identity():
    names = subprocess.check_output(['git', 'ls-files', '--cached', '--others',
        '--exclude-standard', '-z', '--', 'src'], cwd=ROOT).split(b'\0')
    digest = hashlib.sha256()
    for name in sorted(set(n for n in names if n)):
        path = ROOT / os.fsdecode(name)
        if path.is_file():
            digest.update(name + b'\0' + path.read_bytes())
    return digest.hexdigest()


def images(classpaths):
    return {str(p): sha(p) for cp in classpaths.values() for part in cp.split(os.pathsep)
            if Path(part).is_dir() for p in Path(part).rglob('*.class')}


def arguments(description):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--frozen-classpath-file', type=Path, required=True)
    parser.add_argument('--current-classpath-file', type=Path, required=True)
    parser.add_argument('--control-ref', required=True)
    parser.add_argument('--control-classpath-file', type=Path)
    parser.add_argument('--control-native-dir', type=Path)
    parser.add_argument('--control-image-manifest', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    return parser


class Campaign:
    def __init__(self, args):
        self.args = args
        self.output = args.output.resolve()
        self.output.mkdir(parents=True, exist_ok=False)
        self.jdk = args.java_home.resolve() / 'bin'
        self.native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
        self.control_native_dir = (args.control_native_dir or self.native.parent).resolve()
        self.result = {'passed': False, 'guards_pass': False, 'commands': {}}
        self.env = os.environ.copy()
        for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
            self.env.pop(key, None)
        assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
        assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
        assert sha(self.native) == sha(ROOT / 'build/rust/target/release/libmattmc_rust.so'), 'release image differs'
        base = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
        self.result.update(frozen_head=FROZEN_HEAD, control_ref=base,
            candidate_head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            source_sha256=source_identity(), native_sha256=sha(self.native),
            control_native_sha256=sha(self.control_native_dir / self.native.name),
            driver_sha256={str(p.relative_to(ROOT)): sha(p) for p in HERE.iterdir() if p.suffix in ('.java', '.py')})
        self.classpaths = {'frozen': args.frozen_classpath_file.read_text().strip(),
                           'current': args.current_classpath_file.read_text().strip()}
        control = self.output / 'control-classes'
        control.mkdir()
        source = self.output / 'control-source/ThreadedLevelLightEngine.java'
        source.parent.mkdir()
        source.write_bytes(subprocess.check_output(['git', 'show', base + ':' + SOURCE], cwd=ROOT))
        self.result['control_source_sha256'] = sha(source)
        if args.control_classpath_file:
            assert args.control_native_dir and args.control_image_manifest, 'Preserved control needs its native image and class manifest'
            manifest = json.loads(args.control_image_manifest.read_text())
            assert manifest['base'] == base and manifest['native_sha256'] == self.result['control_native_sha256']
            self.classpaths['control'] = args.control_classpath_file.read_text().strip()
            parts = [Path(p) for p in self.classpaths['control'].split(os.pathsep) if Path(p).is_dir()]
            for relative, digest in manifest['expected_base_class_image'].items():
                selected = next((p / relative for p in parts if (p / relative).is_file()), None)
                assert selected is not None and sha(selected) == digest, 'Preserved control class changed: ' + relative
            self.result['preserved_control'] = {'manifest_sha256': sha(args.control_image_manifest),
                'classpath_file_sha256': sha(args.control_classpath_file), 'classes_verified': len(manifest['expected_base_class_image']),
                'native_dir': str(self.control_native_dir), 'base': base}
        else:
            assert not args.control_native_dir and not args.control_image_manifest, 'Control image arguments must be provided together'
            self.run([str(self.jdk / 'javac'), '-nowarn', '-cp', self.classpaths['current'],
                      '-d', str(control), str(source)], 'compile-control.log')
            self.classpaths['control'] = str(control) + os.pathsep + self.classpaths['current']
        self.drivers = {}
        for side in ('frozen', 'control', 'current'):
            target = self.output / ('driver-' + side)
            target.mkdir()
            self.run([str(self.jdk / 'javac'), '-nowarn', '-cp', self.classpaths[side], '-d', str(target),
                      str(HERE / 'VerifyThreadedLighting.java'), str(HERE / 'BenchmarkThreadedLighting.java')],
                     'compile-' + side + '.log')
            self.drivers[side] = target
        self.class_images = images({**self.classpaths, **{'observer-' + side: str(path) for side, path in self.drivers.items()}})
        self.result['class_images'] = self.class_images

    def run(self, command, name):
        self.result['commands'][name] = command
        with (self.output / name).open('w') as log:
            code = subprocess.call(command, cwd=ROOT, env=self.env, stdout=log, stderr=subprocess.STDOUT)
        if code:
            self.save()
            raise RuntimeError(f'{name}: exit{code}; see {self.output / name}')
        return (self.output / name).read_text()

    def java(self, side, main, args, timing=False, cpu=None):
        command = ([] if cpu is None else ['taskset', '-c', str(cpu)]) + [str(self.jdk / 'java'), '-Xms512m', '-Xmx2g']
        if timing:
            command += ['-Xbatch', '-XX:-TieredCompilation']
        command += ['--enable-native-access=ALL-UNNAMED', '-DthreadedLight.frozen=' + str(side == 'frozen').lower(),
                    '-Dmattmc.rust.natives.dir=' + str(self.native_dir(side)),
                    '-cp', str(self.drivers[side]) + os.pathsep + self.classpaths[side],
                    'net.minecraft.server.level.' + main, *map(str, args)]
        return command

    def native_dir(self, side):
        return self.control_native_dir if side == 'control' else self.native.parent

    def save(self):
        (self.output / 'result.json').write_text(json.dumps(self.result, indent=2) + '\n')

    def seal(self):
        self.result['guards_pass'] = (source_identity() == self.result['source_sha256']
            and sha(self.native) == self.result['native_sha256']
            and sha(self.control_native_dir / self.native.name) == self.result['control_native_sha256']
            and all(sha(Path(p)) == digest for p, digest in self.class_images.items())
            and all(sha(ROOT / p) == digest for p, digest in self.result['driver_sha256'].items())
            and subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
            and not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN))
        self.save()


def main():
    args = arguments(__doc__).parse_args()
    campaign = Campaign(args)
    campaign.result['scope'] = 'Actual threaded-light engine/dispatcher; controlled executor/world; no server/client/FPS claim'
    try:
        for side in ('frozen', 'control', 'current'):
            trace = campaign.output / (side + '-trace.txt')
            campaign.run(campaign.java(side, 'VerifyThreadedLighting', [trace]), side + '.log')
            campaign.result[side] = {'trace_sha256': sha(trace), 'events': len(trace.read_text().splitlines())}
        rows = [campaign.result[s] for s in ('frozen', 'control', 'current')]
        campaign.result['passed'] = rows[0] == rows[1] == rows[2] and rows[0]['events'] > 40000
        if not campaign.result['passed']:
            a = (campaign.output / 'frozen-trace.txt').read_text().splitlines()
            b = (campaign.output / 'current-trace.txt').read_text().splitlines()
            campaign.result['first_differences'] = [{'line': i + 1, 'frozen': x, 'current': y}
                for i, (x, y) in enumerate(zip(a, b)) if x != y][:20]
    finally:
        campaign.seal()
    assert campaign.result['passed'] and campaign.result['guards_pass'], str(campaign.output / 'result.json')
    print('PASS', campaign.result['current'])


if __name__ == '__main__':
    main()
