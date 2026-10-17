#!/usr/bin/env python3
"""Run the existing cancellation audit on explicitly sealed candidate inputs."""
import argparse, json, os, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
import VerifyGenerationCancel as cancel
import VerifyFallenFull as fallen

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--radius',type=int,default=2);p.add_argument('--at-chunk',type=int,default=20);p.add_argument('--delay-ms',type=int,default=3000);args=p.parse_args()
    args.inputs=args.inputs.resolve()
    out=args.output.resolve();assert out.is_relative_to(fallen.ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    receipt=json.loads((args.inputs/'result.json').read_text());assert receipt['passed'] and receipt['guards_pass']
    images={**receipt['sources'],**receipt['class_images'],**receipt['native_images']}
    resources=json.loads((fallen.ROOT/'build/fallen-tree/s0027-pre-3f1d-2/manifest.json').read_text());images.update(resources['resource_images']);images.update(resources['frozen_resource_images'])
    assert all(Path(k).exists() and fallen.sha(k)==v for k,v in images.items())
    result={'input_images':images,'runs':{},'comparisons':{},'passed':False};dumps={}
    def save():(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    save()
    try:
        for side in ('frozen','current','current-java'):
            base='current' if side.startswith('current') else side
            selected_inputs=Path(receipt['runs'].get(base,{}).get('reused_from',args.inputs))
            original=json.loads((selected_inputs/(base+'-fresh/command.json')).read_text());cp=original[original.index('-cp')+1];jar=selected_inputs/(base+'-agent.jar')
            native=next((value.split('=',1)[1] for value in original if value.startswith('-Dmattmc.rust.natives.dir=')),None)
            if native:fallen.full.native_dir=lambda side,args:Path(native)
            runtime=argparse.Namespace(seed=42,radius=args.radius,rounds=1,warmups=0,dimensions='minecraft:overworld',timing=False,workers=1,centers='',biomes='',structures='',cpus='2-7',timeout=900,resume=False,heap='8G',remove_worlds=False,jvm_arg=[f'-Daudit.cancelAtChunk={args.at_chunk}',f'-Daudit.cancelDelayMs={args.delay_ms}'])
            try:fallen.full.launch(side,side,cp,jar,out,runtime,os.environ.copy())
            except (SystemExit,FileNotFoundError) as missing:
                # The original launch helper expects regular FULL output. A
                # cancelled run instead writes its own explicit completion.
                if isinstance(missing,FileNotFoundError):assert Path(missing.filename)==out/side/'regions.json'
                assert(out/side/'cancelled.json').exists() and (out/side/'cancelled.txt.gz').exists()
            process=json.loads((out/side/'process.json').read_text());assert process['exit_code']==0
            dumps[side]=cancel.load(out/side/'cancelled.txt.gz');result['runs'][side]={'process':process,'cancelled':json.loads((out/side/'cancelled.json').read_text())};save()
        for i,a in enumerate(dumps):
            for b in list(dumps)[i+1:]:result['comparisons'][a+'-'+b]=cancel.compare(dumps[a],dumps[b])
        result['passed']=all(not r['differ_below_features'] and r['same_status']>0 for r in result['comparisons'].values())
    finally:
        result['guards_pass']=all(Path(k).exists() and fallen.sha(k)==v for k,v in images.items());result['passed']=result['passed'] and result['guards_pass'];save()
    print(json.dumps(result['comparisons'],indent=2));assert result['passed']

if __name__=='__main__':main()
