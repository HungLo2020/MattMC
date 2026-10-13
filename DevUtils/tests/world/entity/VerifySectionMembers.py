#!/usr/bin/env python3
"""Compare production native section membership with actual untouched Frozen."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import statistics

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources(root):
    paths = subprocess.check_output(
        ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z',
         '--', 'src', 'DevUtils', 'build.gradle'], cwd=root).split(b'\0')
    return {os.fsdecode(p): sha(root / os.fsdecode(p))
            for p in paths if p and (root / os.fsdecode(p)).is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument("--benchmark", action="store_true")
    parser.add_argument("--java-scan-control-ref", default="49e26cd8a7c31b6500a20ab39fa2aad8eef14d04", help="Published Java scan source used by --benchmark; remaining classes/native image stay current")
    args = parser.parse_args()
    if not args.java_home:
        parser.error('--java-home or JAVA_HOME must identify JDK25')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    prompt = ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md'
    pins = {'current': sources(ROOT), 'frozen': sources(FROZEN),
            'native': sha(native), 'prompt': sha(prompt),
            'frozen_head': subprocess.check_output(
                ['git', '-C', str(FROZEN), 'rev-parse', 'HEAD'], text=True).strip()}
    assert pins['frozen_head'] == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    assert pins['prompt'] == 'c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5'
    result = {'pins': pins, 'passed': False, 'guards_pass': False,
              'scope': 'Actual Frozen root/typed membership, callback mutation, captured arrays, '
                       'live iterator/stream/spliterator behavior, and production EntitySection '
                       'bounds/cast/consumer queries; supplemental to gameplay.'}
    receipt = output / 'results.json'
    receipt.write_text(json.dumps(result, indent=2) + '\n')
    env = os.environ.copy()
    env.update(JAVA_HOME=str(Path(args.java_home)),
               PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; "
                    "p.tasks.register('sectionMembersRuntimeClasspath') { doLast { "
                    "println('MEMBERS_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env,
                                     stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}')
        return (output / name).read_text()

    try:
        counts = {}
        images = {}
        classpaths = {}
        for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
            data = run([str(repo / 'gradlew'), '-q', '-I', str(init),
                        'sectionMembersRuntimeClasspath'], side + '-classpath.log', repo)
            cp = next(line.removeprefix('MEMBERS_CP ') for line in data.splitlines()
                      if line.startswith('MEMBERS_CP '))
            classpaths[side] = cp
            target = output / 'oracle' / side
            target.mkdir(parents=True)
            run([str(jdk / 'javac'), '-cp', cp, '-d', str(target),
                 str(ROOT / 'DevUtils/tests/world/entity/VerifySectionMembers.java')],
                'compile-' + side + '.log')
            # HotSpot can drop messages from repeatedly thrown VM exceptions.
            # Compare full production failure messages on both sides, without
            # normalizing observer output or changing either implementation.
            # Class-key HashMap order otherwise depends on VM identity hashes,
            # an uncontrolled input between the two separate processes. Fix
            # identity hashes for this callback oracle only; benchmarks and
            # gameplay retain the normal VM policy. Frozen source is unchanged.
            command = [str(jdk / 'java'), '-XX:-OmitStackTraceInFastThrow',
                       '-XX:+UnlockExperimentalVMOptions', '-XX:hashCode=2']
            if side == 'current':
                command += ['--enable-native-access=ALL-UNNAMED',
                            '-Dmattmc.rust.natives.dir=' + str(native.parent)]
            command += ['-cp', str(target) + os.pathsep + cp, 'VerifySectionMembers',
                        str(output / (side + '-members.txt')), side]
            data = run(command, side + '-record.log')
            match = re.search(r'MEMBERS_CASES (\d+) bytes=(\d+) sha=([0-9a-f]{64})', data)
            assert match, f'{side}: no complete corpus receipt'
            counts[side] = int(match[1])
            assert sha(output / (side + '-members.txt')) == match[3]
            checked = {}
            for line in data.splitlines():
                line = line.split('[STDOUT]: ', 1)[-1]
                if not line.startswith('CLASS '):
                    continue
                _, name, digest = line.split()
                resource = name.replace('.', '/') + '.class'
                path = next(Path(base) / resource for base in cp.split(os.pathsep)
                            if (Path(base) / resource).is_file())
                assert path.is_relative_to(repo) and sha(path) == digest
                checked[str(path)] = digest
            assert len(checked) == (13 if side == 'current' else 6)
            images[side] = checked
            if side == 'current':
                assert 'NATIVE_IMAGE ' + pins['native'] + ' ' + str(native) in data
        assert counts['current'] == counts['frozen'] and counts['current'] >= 5500
        current = (output / 'current-members.txt').read_text().splitlines()
        frozen = (output / 'frozen-members.txt').read_text().splitlines()
        differences = [{'line': i + 1, 'current': a, 'frozen': b}
                       for i, (a, b) in enumerate(zip(current, frozen)) if a != b]
        result['differences'] = differences[:40]
        result['line_counts'] = {'current': len(current), 'frozen': len(frozen)}
        assert not differences and len(current) == len(frozen), 'Production behavior differs; inspect results.json'
        result.update(passed=True, cases=counts['current'], class_images=images,
                      fixture_sha256=sha(output / 'frozen-members.txt'),
                      driver_classes={str(p.relative_to(output / 'oracle')): sha(p)
                                      for p in (output / 'oracle').rglob('*.class')})
        if args.benchmark:
            # Compile the published Java scan into an isolated classpath overlay.
            # Everything else (including native library) is the candidate image;
            # this is a consumer control, not a historical whole-game binary.
            control=output / 'oracle' / 'java_scan_control'
            control.mkdir()
            control_sources=[]
            for name in ('net/minecraft/util/ClassInstanceMultiMap.java','net/minecraft/util/NativeClassInstanceStorage.java',
                         'net/minecraft/world/level/entity/EntitySection.java','net/minecraft/world/level/entity/EntityTypeTest.java'):
                target=output / 'control-source' / name
                target.parent.mkdir(parents=True,exist_ok=True)
                target.write_bytes(subprocess.check_output(['git','show',args.java_scan_control_ref+':src/main/java/'+name],cwd=ROOT))
                control_sources.append(str(target))
            classpaths['java_scan_control']=classpaths['current']
            run([str(jdk / 'javac'),'-cp',classpaths['current'],'-d',str(control)]+control_sources+
                [str(ROOT / 'DevUtils/tests/world/entity/VerifySectionMembers.java')], 'compile-control.log')
            result['java_scan_control']={'source_commit':subprocess.check_output(['git','rev-parse',args.java_scan_control_ref],cwd=ROOT,text=True).strip(),
                'sources':{p:sha(Path(p)) for p in control_sources},'scope':'Published Java scan/membership adapters, candidate native image and remaining classes.'}
            for key in ('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS'):
                env.pop(key,None)
            for side in ('frozen','current','java_scan_control'):
                target=output / 'oracle' / side
                run([str(jdk / 'javac'),'-cp',str(target)+os.pathsep+classpaths[side],'-d',str(target),
                     str(ROOT / 'DevUtils/tests/world/entity/BenchmarkSectionQueries.java')], 'compile-benchmark-'+side+'.log')
            samples={'current':[],'frozen':[],'java_scan_control':[]}
            for index,side in enumerate(['current','frozen','java_scan_control']*3):
                command=[str(jdk / 'java'),'-Xms1g','-Xmx1g','-XX:+UseG1GC']
                if side!='frozen':command+=['--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(native.parent)]
                command+=['-cp',str(output / 'oracle' / side)+os.pathsep+classpaths[side],'BenchmarkSectionQueries']
                data=run(command,f'benchmark-{index}-{side}.log')
                lines=[line.split('[STDOUT]: ',1)[-1] for line in data.splitlines()]
                rows=[list(map(int,line.split()[1:])) for line in lines if line.startswith('SECTION_TIMING ')]
                assert len(rows)==96
                samples[side].extend(rows)
            summaries={}
            for size,hits in {(r[0],r[1]) for r in samples['current']}:
                matching={side:[r for r in rows if r[:2]==[size,hits]] for side,rows in samples.items()}
                assert len({r[4] for rows in matching.values() for r in rows})==1
                summaries[f'{size}-members-{hits}-hits']={side:{'median_ms':statistics.median(r[2] for r in rows)/1e6,'median_java_bytes':statistics.median(r[3] for r in rows)} for side,rows in matching.items()}
            result['benchmark']={'scope':'32768 production section queries per sample; prebuilt entities. Nine alternating JVMs, five warmups/eight samples per workload. Excludes gameplay/GPU/native allocation.', 'samples':samples,'summaries':summaries}
    finally:
        result['guards_pass'] = (sources(ROOT) == pins['current']
                                and sources(FROZEN) == pins['frozen']
                                and sha(native) == pins['native']
                                and sha(prompt) == pins['prompt'])
        if 'class_images' in result:
            result['guards_pass'] &= all(sha(Path(p)) == digest
                                        for side in result['class_images'].values()
                                        for p, digest in side.items())
        receipt.write_text(json.dumps(result, indent=2) + '\n')
    assert result['passed'] and result['guards_pass']
    print(f"Actual Frozen section membership {result['cases']} PASS; source/native/class guards PASS")


if __name__ == '__main__':
    main()
