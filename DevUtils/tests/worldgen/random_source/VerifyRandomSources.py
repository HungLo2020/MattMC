#!/usr/bin/env python3
"""Verify actual random-source state, dispatch, raw import and native leaf consumption."""
from pathlib import Path
import sys,os,json,argparse,subprocess,re
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'noise_composite'))
import VerifyComposite as portable

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--pre-runtime',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();p=portable.probe;r=p.ROOT;pre=args.pre_runtime.resolve();out=args.output.resolve()
 assert out.is_relative_to(r/'build');out.mkdir(exist_ok=False,parents=True);reference=p.verify_pre(pre);before=p.end.inputs(r);frozen=p.end.inputs(p.FROZEN);assert frozen['head']==p.PIN
 tests=r/'src/test/java/net/minecraft/world/level/levelgen';names=['XoroshiroStateContractTest','XoroshiroLeafDispatchContractTest','XoroshiroRawStateContractTest','XoroshiroOperationsContractTest'];sources=[tests/(name+'.java')for name in names]+[HERE/'XoroshiroStateProbe.java',HERE/'LeafSourceProbe.java',HERE/'XoroshiroOperationsProbe.java',r/'DevUtils/tests/worldgen/chunk_generation/GenerationContractRunner.java'];hashes={str(s.relative_to(r)):p.full.sha(s)for s in [*sources,Path(__file__)]};jars=[x for x in(r/'build/chunk-generation/s25-test-classpath.txt').read_text().strip().split(os.pathsep)if Path(x).is_file()];rows=[]
 for side,cp,native in [('frozen',r/'build/chunk-generation/s28-frozen-main-classpath.txt',r/'build/chunk-generation/s25-pre522-runtime/native'),('pre',pre/'classpath.txt',pre/'native'),('current',r/'build/chunk-generation/s28-current-main-classpath.txt',r/'build/rust/native')]:
  d=out/side;classes=d/'classes';classes.mkdir(parents=True);classpath=os.pathsep.join([str(classes),cp.read_text().strip(),*jars]);selected=sources if side!='frozen'else[s for s in sources if s.name!='XoroshiroRawStateContractTest.java']
  with(d/'compile.log').open('w')as log:subprocess.run([str(p.full.JDK/'javac'),'-cp',classpath,'-d',str(classes),*map(str,selected)],stdout=log,stderr=subprocess.STDOUT,check=True)
  jobs=[('contracts',['GenerationContractRunner',*['net.minecraft.world.level.levelgen.'+n for n in(names if side!='frozen'else [n for n in names if n!='XoroshiroRawStateContractTest'])]]),('sequence',['net.minecraft.world.level.levelgen.XoroshiroStateProbe']),('leaf',['net.minecraft.world.level.levelgen.LeafSourceProbe',str(d/'leaf.bin')]),('operations',['net.minecraft.world.level.levelgen.XoroshiroOperationsProbe'])]
  for label,tail in jobs:
   cmd=['taskset','-c','4-7',str(p.full.JDK/'java'),'-Xmx1g','-Xbatch','--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(native),'-Xlog:class+load=info:file='+str(d/(label+'-class-load.log')),'-cp',classpath,*tail];(d/(label+'-command.json')).write_text(json.dumps(cmd,indent=2));row=portable.launch(cmd,d/(label+'.log'));text=(d/(label+'.log')).read_text()
   if side=='current'and p.full.sha(native/'mattmc_rust-linux-x64.so')not in row['native_mappings'].values():raise RuntimeError('actual candidate native mapping missing')
   if label=='contracts':
    counts={k:int(re.search(r'\[\s*(\d+) tests '+k+r'\s*\]',text).group(1))for k in ['found','successful','failed','skipped','aborted']};wanted=12 if side=='frozen'else 15;assert counts=={'found':wanted,'successful':wanted,'failed':0,'skipped':0,'aborted':0},counts;row['tests']=counts
   elif label=='sequence':
    value=next(s.split('RNG_STATE_CORPUS ',1)[1]for s in text.splitlines()if'RNG_STATE_CORPUS 'in s);assert value=='12324 1896341da9811de8efdac3c8694ac0e1d62450d462efb2f8921a2aa33b31c86d',value;row['sequence']=value
   elif label=='operations':
    value=next(s.split('RNG_OPERATION_CORPUS ',1)[1]for s in text.splitlines()if'RNG_OPERATION_CORPUS 'in s);assert value=='12480 cee2b9c3f58e9a34a2a563c6b254368c288d6326429a23c4638741907e9ab747',value;row['operations']=value
   else:
    value=p.full.sha(d/'leaf.bin');assert value=='e791746f95db278dc76d796b0dc187a71c12c1f2fea3db0681df5108524411c4',value;row['leaf_sha256']=value
   row.update(side=side,job=label);rows.append(row);(out/'rows.json').write_text(json.dumps(rows,indent=2));print(side,label,'PASS',flush=True)
 assert before==p.end.inputs(r);assert frozen==p.end.inputs(p.FROZEN);p.verify_pre(pre);assert hashes=={str(s.relative_to(r)):p.full.sha(s)for s in [*sources,Path(__file__)]};(out/'result.json').write_text(json.dumps({'passed':True,'before':before,'after':p.end.inputs(r),'frozen':frozen,'pre_head':reference['source_head'],'sources':hashes,'rows':rows,'scope':'actualoriginalFrozen12contracts/pre15/current15 plus12324sequence/statecodecobservations plus12480mixedsourceoperations andactual12leaf offset/permutation/successor/sourcecodec oracle each; rawbridgeabsentFrozen so3casesNOTRUNthere; no performanceclaim'},indent=2))

if __name__=='__main__':main()
