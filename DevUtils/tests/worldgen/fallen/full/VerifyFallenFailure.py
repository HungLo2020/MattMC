#!/usr/bin/env python3
"""Existing failure/rollback audit with sealed classpath/native inputs."""
import argparse, json, os, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
import VerifyGenerationFailure as failure
import VerifyFallenFull as fallen

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--writes',type=int,default=20000);p.add_argument('--at-chunk',type=int,default=20);p.add_argument('--radius',type=int,default=2);args=p.parse_args();assert args.at_chunk>=0,'Rollback capture requires an explicit chunk'
    args.inputs=args.inputs.resolve();out=args.output.resolve();assert out.is_relative_to(fallen.ROOT/'build')
    receipt=json.loads((args.inputs/'result.json').read_text());assert receipt['passed'] and receipt['guards_pass']
    images={**receipt['sources'],**receipt['class_images'],**receipt['native_images']}
    resource=json.loads((fallen.ROOT/'build/fallen-tree/s0027-pre-3f1d-2/manifest.json').read_text());images.update(resource['resource_images']);images.update(resource['frozen_resource_images'])
    assert all(Path(k).exists() and fallen.sha(k)==v for k,v in images.items())
    command=json.loads((args.inputs/'current-fresh/command.json').read_text());cp=command[command.index('-cp')+1];jar=args.inputs/'current-agent.jar'
    native=next(x.split('=',1)[1] for x in command if x.startswith('-Dmattmc.rust.natives.dir='));failure.full.NATIVE=Path(native)/'mattmc_rust-linux-x64.so';failure.full.JDK=fallen.JAVA;failure.full.prepare=lambda *unused:(cp,jar)
    sys.argv=['VerifyGenerationFailure.py','--output',str(out),'--radius',str(args.radius),'--writes',str(args.writes),'--at-chunk',str(args.at_chunk),'--skip-build','--timeout','600']
    try:failure.main()
    finally:
        if out.exists():(out/'input-guards.json').write_text(json.dumps({'images':images,'guards_pass':all(Path(k).exists() and fallen.sha(k)==v for k,v in images.items())},indent=2)+'\n')

if __name__=='__main__':main()
