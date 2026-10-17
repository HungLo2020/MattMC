#!/usr/bin/env python3
"""Short actual MattMC region transport screen; original Minecraft has no NativeDecorationRegion API."""
import argparse,json,os,statistics,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'noise_composite'))
import VerifyComposite as portable
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--pre-runtime',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--smoke',action='store_true');a=parser.parse_args()
    p=portable.probe;r=p.ROOT;pre=a.pre_runtime.resolve();out=a.output.resolve();assert out.is_relative_to(r/'build');out.mkdir(parents=True,exist_ok=False);ref=p.verify_pre(pre);before=p.end.inputs(r)
    sources=[Path(__file__).with_name('SkyConsumerScreen.java'),r/'src/test/java/net/minecraft/world/level/chunk/NativeDecorationSkyTransportTest.java'];hashes={str(f):p.full.sha(f)for f in [*sources,Path(__file__)]};jars=[f for f in(r/'build/chunk-generation/s25-test-classpath.txt').read_text().strip().split(os.pathsep)if Path(f).is_file()];rows={}
    for side,runtime,native in [('pre',(pre/'classpath.txt').read_text().strip(),pre/'native'),('current',(r/'build/chunk-generation/s28-current-main-classpath.txt').read_text().strip(),r/'build/rust/native')]:
        folder=out/side;classes=folder/'classes';classes.mkdir(parents=True);cp=os.pathsep.join([str(classes),runtime,*jars])
        with(folder/'compile.log').open('w')as log:subprocess.run([str(p.full.JDK/'javac'),'-cp',cp,'-d',str(classes),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True)
        cmd=['taskset','-c','4',str(p.full.JDK/'java'),'-Xms1g','-Xmx4g','-Xbatch','-Dnet.bytebuddy.experimental=true','--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(native),'-cp',cp,'net.minecraft.world.level.chunk.SkyConsumerScreen']
        if a.smoke:cmd+=['--smoke']
        (folder/'command.json').write_text(json.dumps(cmd,indent=2));row=portable.launch(cmd,folder/'screen.log');lines=(folder/'screen.log').read_text().splitlines();row['samples']=[json.loads(line.split('SKY_CONSUMER_SCREEN ',1)[1])for line in lines if'SKY_CONSUMER_SCREEN 'in line];row['retirement']=[line for line in lines if'SKY_CONSUMER_RETIREMENT 'in line];assert p.full.sha(native/'mattmc_rust-linux-x64.so')in row['native_mappings'].values();rows[side]=row;print(side,'actual region screen complete',flush=True)
    summary=[]
    for kind in range(2):
        selected={side:[v for v in row['samples']if v['kind']==kind and v['accepted']]for side,row in rows.items()};assert all(len(v)==(1 if a.smoke else 4)for v in selected.values());sums={v['sum']for samples in selected.values()for v in samples};assert len(sums)==1;summary.append({'kind':kind,'sum':sums.pop(),'medians':{side:{'ns_per_call':statistics.median(v['elapsed_ns']/v['count']for v in samples),'min_ns_per_call':min(v['elapsed_ns']/v['count']for v in samples),'max_ns_per_call':max(v['elapsed_ns']/v['count']for v in samples),'java_bytes_per_call':statistics.median(v['java_bytes']/v['count']for v in samples)}for side,samples in selected.items()}})
    assert before==p.end.inputs(r);p.verify_pre(pre);assert hashes=={str(f):p.full.sha(f)for f in [*sources,Path(__file__)]};(out/'result.json').write_text(json.dumps({'passed':True,'before':before,'after':p.end.inputs(r),'pre_head':ref['source_head'],'sources':hashes,'rows':rows,'summary':summary,'smoke_only':a.smoke,'scope':'Actual pre58 vs Current MattMC NativeDecorationRegion copy/projection and escaped snapshot/restore, same real native region/chunk/source setup outside timer. Scoped actual native source Store probes/VMfacade+anchor retire and independent snapshots survive; pre58 Java-only Source has no native Store. No analogous NativeDecorationRegion in originalFrozen, no final performance/FPS/nativeallocator claim.'},indent=2)+'\n')
if __name__=='__main__':main()
