#!/usr/bin/env python3
"""Real NOISE kernel factory capture, VM retirement, ore output and last release.

The negative controls interpose only the two admission/release ABI functions:
one admits an independent factory allocation, the other leaks the actual kernel.
Neither frees memory early or executes a dangling-pointer experiment.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'DevUtils/tests/worldgen/chunk_generation'))
import VerifyChunkGeneration as common


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-runtime', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--controls', action='store_true')
    args = parser.parse_args()
    pre = (ROOT / args.pre_runtime).resolve()
    common.verify_pre(pre)
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Use a fresh output directory under build/.')
    out.mkdir(parents=True, exist_ok=False)
    source = Path(__file__).with_name('FactoryKernelScene.java')
    before = common.end.inputs(ROOT)
    origins = {str(p): sha(p) for p in [Path(__file__), source]}
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    origins[str(library)] = sha(library)
    jdk = ROOT / 'libraries/jdk/linux-x64/bin'
    pre_cp = (pre / 'classpath.txt').read_text().strip()
    current_cp = (ROOT / 'build/chunk-generation/s28-current-main-classpath.txt').read_text().strip()
    classes = out / 'classes'
    classes.mkdir()
    compile_cmd = [str(jdk / 'javac'), '--release', '25', '-proc:none', '-cp', pre_cp,
                   '-d', str(classes), str(source)]
    (out / 'compile-command.json').write_text(json.dumps(compile_cmd, indent=2))
    with (out / 'compile.log').open('w') as log:
        subprocess.run(compile_cmd, stdout=log, stderr=subprocess.STDOUT, check=True)
    observers = {str(p): sha(p) for p in classes.rglob('*.class')}
    result = {'passed': False, 'before': before, 'sources': origins, 'observers': observers,
              'runs': {}, 'scope': 'Actual production kernel and canonical Weak, real VM owner GC; '
              'same once-precompiled observer; safe ABI capture/release controls, no UAF.'}

    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')

    def run(label, cp, native_dir, expected_library, legacy=False, expected_failure=None):
        classlog = out / (label + '-classes.log')
        cmd = [str(jdk / 'java'), '-Xms256m', '-Xmx2g', '--enable-native-access=ALL-UNNAMED',
               '-Xlog:class+load=info:file=' + str(classlog),
               '-Dmattmc.rust.natives.dir=' + str(native_dir), '-cp', str(classes) + os.pathsep + cp,
               'net.minecraft.world.level.levelgen.FactoryKernelScene']
        if legacy:
            cmd.append('--legacy-native')
        (out / (label + '-command.json')).write_text(json.dumps(cmd, indent=2))
        mapped = {}
        start = time.monotonic()
        with (out / (label + '.log')).open('w') as log:
            job = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT)
            try:
                while job.poll() is None:
                    try:
                        for line in Path(f'/proc/{job.pid}/maps').read_text().splitlines():
                            if 'mattmc_rust' in line and '.so' in line:
                                p = Path(line.split()[-1]).resolve()
                                mapped[str(p)] = sha(p)
                    except (FileNotFoundError, ProcessLookupError):
                        pass
                    if time.monotonic() - start > 90:
                        raise TimeoutError(label)
                    time.sleep(.03)
            finally:
                if job.poll() is None:
                    job.terminate()
                    job.wait()
        text = (out / (label + '.log')).read_text()
        rows = [line[line.index('FACTORY_KERNEL '):] for line in text.splitlines() if 'FACTORY_KERNEL ' in line]
        assert mapped.get(str(expected_library.resolve())) == sha(expected_library), (label, mapped)
        if native_dir != expected_library.parent:
            shim = native_dir / library.name
            assert mapped.get(str(shim.resolve())) == sha(shim)
        loaded = classlog.read_text()
        assert 'FactoryKernelScene source: file:' + str(classes) + '/' in loaded
        main = next(Path(p) for p in cp.split(os.pathsep) if Path(p).is_dir())
        assert 'NativePositionalFactory source: file:' + str(main) + '/' in loaded
        if expected_failure:
            assert job.returncode != 0 and expected_failure in text, (label, job.returncode, text[-2000:])
            assert not rows, 'Fault must fail before an accepted scene.'
        else:
            assert job.returncode == 0 and len(rows) == 4, (label, job.returncode, text[-2000:])
        result['runs'][label] = {'exit': job.returncode, 'mapped': mapped, 'rows': rows,
                                 'expected_rejection': expected_failure}
        save()
        return rows

    try:
        original = run('pre', pre_cp, pre / 'native', pre / 'native' / library.name, legacy=True)
        current = run('current', current_cp, library.parent, library)
        assert current == original, 'Actual ore outputs differ from immutable pre-change.'
        if args.controls:
            for name, failure in [('capture', 'expected=1 actual=0'),
                                  ('release', 'Actual kernel last release retained canonical factory')]:
                folder = out / name
                folder.mkdir()
                # Explicit dlopen of the exact guarded production image; ABI wrappers
                # delegate every real computation to it. Only capture/release is faulty.
                code = '''#include <stdint.h>
#include <dlfcn.h>
#include <stdlib.h>
static void *runtime;
static void *symbol(const char *name) {
  if (!runtime) runtime=dlopen(RUNTIME, RTLD_NOW|RTLD_GLOBAL);
  if (!runtime) abort();
  void *p=dlsym(runtime,name); if (!p) abort(); return p;
}
__attribute__((constructor)) static void load(void) { symbol("mattmc_positional_new"); }
uint64_t mattmc_noise_fill_create_owned(const int32_t *i,const double *d,uint64_t f) {
  uint64_t (*create)(const int32_t*,const double*,uint64_t)=symbol("mattmc_noise_fill_create_owned");
  if (CAPTURE && f) {
    int64_t (*seed)(uint64_t,int32_t)=symbol("mattmc_positional_seed");
    uint64_t (*make)(int32_t,int64_t,int64_t)=symbol("mattmc_positional_new");
    void (*drop)(uint64_t)=symbol("mattmc_positional_release");
    uint64_t independent=make(i[15],seed(f,0),seed(f,1));
    uint64_t kernel=create(i,d,independent); drop(independent); return kernel;
  }
  return create(i,d,f);
}
void mattmc_noise_fill_release(uint64_t f) {
  if (!CAPTURE) return; /* intentional safe leak in this short-lived process */
  void (*drop)(uint64_t)=symbol("mattmc_noise_fill_release"); drop(f);
}
'''
                c = folder / 'control.c'
                c.write_text(code)
                shim = folder / library.name
                # Link dependency supplies all remaining actual production symbols.
                command = ['cc', '-shared', '-fPIC', '-O2', '-DRUNTIME=' + json.dumps(str(library)),
                           '-DCAPTURE=' + ('1' if name == 'capture' else '0'), str(c),
                           '-Wl,--no-as-needed', str(library), '-ldl', '-o', str(shim)]
                with (folder / 'compile.log').open('w') as log:
                    subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
                result.setdefault('control_inputs', {})[name] = {'command': command, 'source': sha(c), 'library': sha(shim)}
                run(name, current_cp, folder, library, expected_failure=failure)
        result['passed'] = True
    finally:
        result['after'] = common.end.inputs(ROOT)
        result['guards_pass'] = before == result['after'] and all(sha(p) == v for p, v in {**origins, **observers}.items())
        common.verify_pre(pre)
        result['passed'] &= result['guards_pass']
        save()
    if not result['passed']:
        raise SystemExit(1)
    print('FACTORY_KERNEL_OWNERSHIP_PASS', flush=True)


if __name__ == '__main__':
    main()
