#!/usr/bin/env python3
"""Bounded native requested-heap and retained-facade control; never latency evidence.

Reuses S27's reviewed Linux/glibc scoped observer unchanged. Source archive:
93226e64570d9e69f0c943a1ab92304aa5a18aba822dd590d3f1a56a583786cc,
discussion888 worker S0027/0120. The FFM helper only changes its Java package/name.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[5]
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from VerifyItemState import class_seal,sha

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();out=args.output.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir()
    folder=Path(__file__).resolve().parent;native=ROOT/'build/rust/native/mattmc_rust-linux-x64.so'
    sources=[folder/'ItemNativeAllocation.java',folder/'ItemResourceReference.java',folder.parent/'StockChestTransferTimingReference.java']
    observer=folder/'scoped_alloc.c';before=class_seal();image=sha(native);classes=out/'observer';classes.mkdir()
    inputs={str(p.relative_to(ROOT)):sha(p) for p in [*sources,observer,Path(__file__).resolve()]}
    for p in [*sources,observer]:shutil.copy2(p,out/p.name)
    result={'kind':__doc__,'head':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'compiled_classes':before,'native_sha256':image,'sources':inputs,'passed':False}
    def save():(out/'receipt.json').write_text(json.dumps(result,indent=2))
    save()
    try:
        library=out/'libitem_alloc.so';compile=['gcc','-shared','-fPIC','-O2','-fno-builtin','-Wall','-Wextra','-Werror','-o',str(library),str(observer),'-ldl','-pthread']
        result['compiler_command']=compile
        with(out/'gcc.log').open('w')as log:subprocess.run(compile,stdout=log,stderr=subprocess.STDOUT,check=True)
        result['observer_native_sha256']=sha(library)
        cp=(ROOT/'build/item-state-current-classpath.txt').read_text().strip();jdk=args.java_home.resolve()/'bin'
        with(out/'javac.log').open('w')as log:subprocess.run([str(jdk/'javac'),'-cp',cp,'-d',str(classes),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True)
        command=[str(jdk/'java'),'-Xmx1g','--enable-native-access=ALL-UNNAMED','-Dfallen.alloc.library='+str(library),'-Dmattmc.rust.natives.dir='+str(native.parent),'-cp',str(classes)+os.pathsep+cp,'ItemResourceReference',str(out)]
        result['command']=command;environment=os.environ.copy();environment['LD_PRELOAD']=str(library)+(os.pathsep+environment['LD_PRELOAD'] if environment.get('LD_PRELOAD') else '')
        result['ld_preload']=environment['LD_PRELOAD'];mappings=set();peak=0;start=time.monotonic();save()
        with(out/'observer.log').open('w')as log:
            process=subprocess.Popen(command,cwd=out,env=environment,stdout=log,stderr=subprocess.STDOUT)
            try:
                while process.poll() is None:
                    try:
                        mappings.update(line for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines() if 'mattmc_rust' in line or str(library) in line)
                        peak=max(peak,next(int(line.split()[1])*1024 for line in Path(f'/proc/{process.pid}/status').read_text().splitlines() if line.startswith('VmRSS:')))
                    except(FileNotFoundError,ProcessLookupError,StopIteration):pass
                    if time.monotonic()-start>180:raise TimeoutError('bounded item resource observation')
                    time.sleep(.1)
            finally:
                if process.poll() is None:process.terminate();process.wait(timeout=15)
        result.update(exit=process.returncode,native_mappings=sorted(mappings),instrumented_process_peak_rss=peak,seconds=time.monotonic()-start)
        assert process.returncode==0,process.returncode
        assert any(str(native) in line for line in mappings) and any(str(library) in line for line in mappings)
        assert sha(native)==image and class_seal()==before
        assert inputs=={str(p.relative_to(ROOT)):sha(p) for p in [*sources,observer,Path(__file__).resolve()]}
        complete=json.loads((out/'complete.json').read_text());assert complete['passed']
        operations=json.loads((out/'operations.json').read_text());released=json.loads((out/'retirement-released.json').read_text())
        rust=[s['rust'] for s in operations];assert len(rust)==32
        summary={'requested_rust_bytes_median':statistics.median(x[1] for x in rust),'requested_rust_bytes_min':min(x[1] for x in rust),'requested_rust_bytes_max':max(x[1] for x in rust),'incremental_rust_live_peak_median':statistics.median(x[5] for x in rust),'incremental_rust_live_peak_max':max(x[5] for x in rust),'held_rust_bytes':complete['heldRustRequestedBytes'],'released_rust_bytes':[s['native']['rust'][4] for s in released],'released_other_native_bytes':[s['native']['otherNative'][4] for s in released]}
        assert all(x==0 for x in summary['released_rust_bytes'])
        result.update(passed=True,summary=summary,limits='Actual operation-thread requested libc heap with tagged cross-thread frees. Excludes Java heap, direct mmap/code cache, untagged calls/threads, allocator usable size and process-native RSS. Other-caller residuals remain unattributed; GC observations are forced diagnostic windows, not natural latency.')
    except Exception as failure:
        result['failure']=f'{type(failure).__name__}: {failure}';print(result['failure'],file=sys.stderr)
    finally:save()
    print(json.dumps({k:result[k] for k in ['passed','summary','failure'] if k in result},indent=2));return 0 if result['passed'] else 1

if __name__=='__main__':sys.exit(main())
