#!/usr/bin/env python3
"""Run real save-custody contracts and an omitted-publication mutation in isolated class overlays."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    candidate = args.candidate.resolve(); out = args.output.resolve()
    assert out.is_relative_to(ROOT / 'build'); out.mkdir(parents=True, exist_ok=False)
    manifest = candidate / 'result.json'; pin = json.loads(manifest.read_text()); assert pin['passed']
    cp = (candidate / 'classpath.txt').read_text().strip()
    native = candidate / 'native/mattmc_rust-linux-x64.so'
    test = ROOT / 'src/test/java/net/minecraft/world/level/lighting/NativeLightSaveCustodyTest.java'
    production = ROOT / 'src/main/java/net/minecraft/world/level/lighting/LayerLightSectionStorage.java'
    retention = Path(__file__).with_name('VerifyLightSaveRetention.java')
    inputs = {str(candidate / 'classes' / path): digest for path, digest in pin['classes'].items()}
    inputs.update(pin['resource_images']); inputs.update(pin['dependencies'])
    for path in [native, manifest, test, production, retention, Path(__file__).resolve()]: inputs[str(path)] = sha(path)
    assert sha(native) == pin['native_sha256'] and all(sha(path) == digest for path, digest in inputs.items())
    result = {'base': pin['base'], 'native_sha256': sha(native), 'input_images': inputs, 'runs': {}, 'passed': False}
    launcher = out / 'SaveCustodyLauncher.java'
    launcher.write_text('''import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class SaveCustodyLauncher {
 public static void main(String[] args) {
  var listener=new SummaryGeneratingListener(); var launcher=LauncherFactory.create();
  launcher.registerTestExecutionListeners(listener);
  launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("net.minecraft.world.level.lighting.NativeLightSaveCustodyTest")).build());
  var summary=listener.getSummary(); summary.printTo(new java.io.PrintWriter(System.out,true));
  summary.printFailuresTo(new java.io.PrintWriter(System.out,true));
  System.out.println("CUSTODY_RESULT found="+summary.getTestsFoundCount()+" passed="+summary.getTestsSucceededCount()+" failed="+summary.getTestsFailedCount());
  if(summary.getTestsFoundCount()!=7||summary.getTestsSucceededCount()!=7)System.exit(1);
 }
}
''')
    env = {k: v for k, v in os.environ.items() if k not in ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MATTMC_RUST_NATIVE_DIR']}
    try:
        for mode in ['production', 'omit-publication']:
            folder = out / mode; folder.mkdir(); classes = folder / 'classes'; classes.mkdir()
            source = folder / production.name; text = production.read_text()
            if mode == 'omit-publication':
                old = 'this.coordinator.prepareSavePublication(this.visibleSectionData.nativeMap,map);'
                assert text.count(old) == 1
                text = text.replace(old, '{} // deliberately omit actual native save custody')
            source.write_text(text)
            compile_command = [str(args.java_home / 'bin/javac'), '-g', '-cp', cp, '-d', str(classes), str(source), str(test), str(launcher)]
            with (folder / 'compile.log').open('w') as log:
                subprocess.run(compile_command, check=True, stdout=log, stderr=subprocess.STDOUT, env=env)
            command = [str(args.java_home / 'bin/java'), '-Xms256m', '-Xmx2g', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'SaveCustodyLauncher']
            with (folder / 'run.log').open('w') as log:
                run = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, env=env, timeout=90)
            log = (folder / 'run.log').read_text()
            result['runs'][mode] = {'command': command, 'compile_command': compile_command, 'exit': run.returncode, 'summary': [line for line in log.splitlines() if 'CUSTODY_RESULT ' in line], 'overlay_images': {str(p.relative_to(classes)): sha(p) for p in classes.rglob('*.class')}}
            if mode == 'production': assert run.returncode == 0 and 'found=7 passed=7 failed=0' in log
            # The five selection/identity/COW contracts must fail. GC can
            # pass through other published roots; the disabled-column case
            # independently requires no save adoption.
            else: assert run.returncode == 1 and 'found=7 passed=2 failed=5' in log
            print(mode, result['runs'][mode]['summary'], flush=True)
        folder = out / 'retention'; folder.mkdir(); classes = folder / 'classes'; classes.mkdir()
        with (folder / 'compile.log').open('w') as log:
            subprocess.run([str(args.java_home / 'bin/javac'), '-g', '-nowarn', '-cp', cp, '-d', str(classes), str(retention)], check=True, stdout=log, stderr=subprocess.STDOUT, env=env)
        command = [str(args.java_home / 'bin/java'), '-Xms256m', '-Xmx2g', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'net.minecraft.world.level.lighting.VerifyLightSaveRetention', '512']
        with (folder / 'run.log').open('w') as log:
            run = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, env=env, timeout=120)
        result['retention'] = {'command': command, 'exit': run.returncode, 'rows': [line for line in (folder / 'run.log').read_text().splitlines() if 'RETENTION' in line], 'observer_sha256': sha(retention)}
        assert run.returncode == 0 and any('native_owner_delta=0' in row for row in result['retention']['rows'])
        print('retention', result['retention']['rows'], flush=True)
        result['passed'] = True
    finally:
        result['guards_pass'] = all(sha(path) == digest for path, digest in inputs.items())
        (out / 'result.json').write_text(json.dumps(result, indent=2))
    assert result['passed'] and result['guards_pass']


if __name__ == '__main__': main()
